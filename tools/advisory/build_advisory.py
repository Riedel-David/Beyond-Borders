"""Build app/src/main/assets/advisory.json from the US, UK and German government travel advisories.

Usage: python3 -I tools/advisory/build_advisory.py [--source DIR] [--out FILE] [--cache DIR] [--previous FILE]

Without --source the three feeds are downloaded (and the raw payloads saved under --cache, default
tools/advisory/cache/, gitignored, so a failed run can be examined and an offline run repeated):

    US  https://cadataapi.state.gov/api/TravelAdvisories                    one call, level 1-4
    UK  https://www.gov.uk/api/content/foreign-travel-advice[/<slug>]       index + one call per country
    DE  https://www.auswaertiges-amt.de/opendata/travelwarning              one call, warning flags
        https://www.auswaertiges-amt.de/opendata/travelwarning/<node id>    one call per country, page text

With --source DIR nothing is downloaded: DIR holds us.json, de.json, uk_index.json and uk/<slug>.json,
which is exactly what a cache directory looks like (plus de_detail/<node id>.json).

Each source's own rating is kept verbatim in `label`; `stance` (0-3) is only a common key for the
app's agreement count and row colour (docs/PLAN.md, "Country travel advisories"). No score is blended.

All or nothing, like tools/visa/build_visa.py: an unreachable feed, a feed with implausibly few
countries, or a country that cannot be mapped to an ISO code is an error that names it, and nothing
is written. A partial file would make the app show "no data" for a country that has data.
"""
import argparse
import datetime
import hashlib
import html
import json
import re
import shutil
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

DATA_FORMAT = 1
ROOT = Path(__file__).resolve().parents[2]
PLACES = ROOT / "app/src/main/assets/places.json"
DEFAULT_OUT = ROOT / "app/src/main/assets/advisory.json"
DEFAULT_CACHE = ROOT / "tools/advisory/cache"

US_URL = "https://cadataapi.state.gov/api/TravelAdvisories"
UK_INDEX_URL = "https://www.gov.uk/api/content/foreign-travel-advice"
DE_URL = "https://www.auswaertiges-amt.de/opendata/travelwarning"
# The AA feed has no page address, but each entry's key is the page's node id and
# /de/-/<node id> resolves to the country's own page (it matches the officialUrl in visa.json).
DE_LINK = "https://www.auswaertiges-amt.de/de/-/"

# Hosts an advisory `url` may point at. The app drops any entry whose host is not on its own copy of
# this list (ADVISORY_URL_HOSTS in Advisory.kt): the two must match.
URL_HOSTS = {"travel.state.gov", "www.gov.uk", "www.auswaertiges-amt.de"}

SOURCES = {"US": "US State Department", "UK": "UK FCDO", "DE": "Auswärtiges Amt"}

# Observed 2026-11-24: US 218 entries, UK 226 pages, DE 200 countries. Far fewer means a truncated
# or restructured feed, and a country count that low would silently hide advisories.
MIN_COUNTRIES = {"US": 150, "UK": 150, "DE": 150}

# Names a feed uses that places.json spells differently, and entries that cover several places.
# Matching is on normalised names (see norm), so only genuine differences are listed. Every target
# must be an ISO code in places.json; build() checks that.
US_ALIASES = {
    "macau": ["MO"],
    "gaza": ["PS"],
    "west bank": ["PS"],
    "bonaire": ["BQ"],
    "saba and sint eustatius": ["BQ"],
    "french saint martin": ["MF"],
    "democratic republic of the congo": ["CD"],
    "federated states of micronesia": ["FM"],
    "turkey": ["TR"],
    "mexico travel advisory": ["MX"],
    "burma": ["MM"],
    "kyrgyz republic": ["KG"],
    "gambia": ["GM"],
    "naoero": ["NR"],
    "republic of congo": ["CG"],
    "french west indies": ["GP", "MQ", "BL"],
}
UK_ALIASES = {
    "antarctica british antarctic territory": ["AQ"],
    "bahamas": ["BS"],
    "bonaire st eustatius saba": ["BQ"],
    "cape verde": ["CV"],
    "congo": ["CG"],
    "democratic republic of the congo": ["CD"],
    "federated states of micronesia": ["FM"],
    "myanmar burma": ["MM"],
    "st maarten": ["SX"],
    "st martin and st barthelemy": ["MF", "BL"],
    "turkey": ["TR"],
    "usa": ["US"],
    "cook islands tokelau and niue": ["CK", "TK", "NU"],
    "pitcairn island": ["PN"],
    "south georgia and the south sandwich islands": ["GS"],
    "st helena ascension and tristan da cunha": ["SH"],
    "st kitts and nevis": ["KN"],
    "st lucia": ["LC"],
    "st pierre miquelon": ["PM"],
    "st vincent and the grenadines": ["VC"],
}

UK_PARTS = {"avoid_all_travel_to_parts", "avoid_all_but_essential_travel_to_parts"}
UK_WHOLE = {"avoid_all_travel_to_whole_country", "avoid_all_but_essential_travel_to_whole_country"}
# FCDO's own phrases for each alert_status flag; the feed only carries the flag, not the sentence.
UK_PHRASE = {
    "avoid_all_travel_to_whole_country": "advises against all travel to the whole country",
    "avoid_all_but_essential_travel_to_whole_country": "advises against all but essential travel to the whole country",
    "avoid_all_travel_to_parts": "advises against all travel to parts of the country",
    "avoid_all_but_essential_travel_to_parts": "advises against all but essential travel to parts of the country",
}


class BuildError(Exception):
    pass


def norm(name: str) -> str:
    s = name.replace("’", "").replace("'", "")
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"[^a-z0-9]+", " ", s).strip()
    return s[4:] if s.startswith("the ") else s


def name_index(places: list) -> dict:
    index = {}
    for p in places:
        index.setdefault(norm(p["name"]), []).append(p["code"])
    return index


def resolve(name: str, index: dict, aliases: dict) -> list:
    key = norm(name)
    if key in aliases:
        return aliases[key]
    return index.get(key, [])


def iso_date(ts: str) -> str:
    m = re.match(r"\d{4}-\d{2}-\d{2}", ts or "")
    if not m:
        raise BuildError(f"no ISO date in {ts!r}")
    return m.group(0)


def merge(out: dict, code: str, entry: dict) -> None:
    """Two feed entries for one ISO code (Gaza and the West Bank are both Palestine): keep the more
    cautious one, so a calm page cannot hide a warning one."""
    old = out.get(code)
    if old is None or entry["stance"] > old["stance"]:
        out[code] = entry


# --- US ---------------------------------------------------------------------------------------

def us_stance(level: int) -> int:
    return {1: 0, 2: 1, 3: 2, 4: 3}[level]


US_TAG_MAX_CHARS = 60
US_SINGLE_TAGS = {"crime", "terrorism", "unrest", "civil unrest", "kidnapping", "armed conflict", "health",
                  "health risks", "landmines", "natural disasters", "hostage taking"}


def us_tags(summary: str) -> list:
    """The risk indicators, from the 'due to ...' clause of the summary's first paragraph. Optional
    decoration: anything that does not parse cleanly is dropped rather than guessed at. Split on
    commas only ('crime and kidnapping' stays one tag: splitting on 'and' cut 'health care and ...'
    apart), with 'U.S.' protected from the full-stop cut, and 'and'/'or'/'the' dropped at the start of a
    piece ('..., and kidnapping', '..., or landmines')."""
    m = re.search(r"<p>(.*?)</p>", summary or "", re.S)
    if not m:
        return []
    text = html.unescape(re.sub(r"<[^>]+>", "", m.group(1))).replace("\xa0", " ").replace("\u202f", " ")
    text = re.sub(r"\bU\.S\.", "US", text)
    m = re.search(r"\bdue to\s+([^.]*)", text, re.I)
    if not m:
        return []
    tags = []
    for part in m.group(1).split(","):
        t = re.sub(r"\s+", " ", part.strip().lower())
        t = re.sub(r"^(?:(?:and|or|the)\s+)+", "", t)
        t = re.sub(r"^(?:(?:the\s+)?(?:risk|threat|potential)s?\s+(?:of|for)\s+|risks\s+to\s+)", "", t)
        # 'crime and kidnapping' is two indicators, but 'gay and lesbian individuals' is one phrase:
        # only a pair of indicators the feed uses on their own is split.
        pieces = re.split(r"\s+(?:and|or)\s+", t)
        if not (len(pieces) > 1 and all(x in US_SINGLE_TAGS for x in pieces)):
            pieces = [t]
        for x in pieces:
            if 0 < len(x) <= US_TAG_MAX_CHARS and x not in tags:
                tags.append(x)
    return tags


# --- regional advice as notes (US, UK) --------------------------------------------------------
# Both governments rate the country as a whole and then name the regions in prose ("Do Not Travel to
# the Valley of the Apurimac ..."). Those sentences are carried verbatim as `notes`, exactly like the
# German ones, so that a Level 2 or 3 row says where. The stance mapping is not touched.
_US_REGION = re.compile(r"^(?:Do not travel|Reconsider travel)\s+(?:to|in)\b", re.I)


def us_notes(summary: str, country: str) -> list:
    """Summary sentences that name a region to avoid. The first sentence is the country-wide headline
    and is skipped, as is anything that is the whole country ('... for any reason', 'to <country>
    due to ...'), which the label already says. A feed summary can be cut short (Mexico's is, so its
    'Do Not Travel to' list is not in it); what is not there is not invented."""
    cname = re.escape(country.strip())
    units = de_units(summary)[1:]
    notes = []
    for u in _redundant([u for u in units if _US_REGION.search(u.text) and not u.text.rstrip().endswith(":")]):
        if re.search(r"for any reason", u.text, re.I) \
                or re.match(r"^(?:Do not travel|Reconsider travel)\s+(?:to|in)\s+" + cname + r"\b\s*(?:due|[.,])", u.text, re.I):
            continue
        notes.append(trim_note(u.text))
    return notes[:DE_MAX_NOTES]


def uk_notes(page: dict) -> list:
    """The first sentence under each 'Areas where FCDO advises against ...' heading of the warnings
    part (and under 'FCDO advises against all travel to X'), which says where. Headings vary in
    spacing (non-breaking spaces), so they are compared with whitespace collapsed."""
    parts = (page.get("details") or {}).get("parts") or []
    body = parts[0].get("body") if parts and isinstance(parts[0], dict) else None
    if not isinstance(body, str):
        return []
    notes = []
    chunks = re.split(r"(?=<h2\b)", body)
    for chunk in chunks:
        m = re.match(r"<h2[^>]*>(.*?)</h2>", chunk, re.S)
        if not m:
            continue
        heading = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", m.group(1)))).strip()
        if not re.search(r"\bFCDO advises against\b", heading.replace("\u202f", " ")):
            continue
        for u in de_units(chunk[m.end():]):
            if re.search(r"\b(?:FCDO )?advises against\b", u.text) and not u.text.rstrip().endswith(":"):
                text = _split_sentences(u.text)[0]
                notes.append(trim_note(text))
                break
    seen, out = set(), []
    for n in notes:
        if _key(n) not in seen:
            seen.add(_key(n))
            out.append(n)
    return out[:DE_MAX_NOTES]


def parse_us(feed, index: dict) -> dict:
    if not isinstance(feed, list):
        raise BuildError("US feed is not a list")
    rows, unmapped = {}, []
    for e in feed:
        title = e.get("Title", "")
        name, _, label = title.rpartition(" - ")
        name = re.sub(r"\s+Travel Advisory$", "", name)
        m = re.match(r"Level ([1-4])\b", label)
        if not m:
            raise BuildError(f"US entry {title!r} has no 'Level 1-4' rating")
        codes = resolve(name, index, US_ALIASES)
        if not codes:
            unmapped.append(name)
            continue
        entry = {
            "stance": us_stance(int(m.group(1))),
            "label": label.strip(),
            "updated": iso_date(e.get("Updated") or e.get("Published")),
            "url": e["Link"],
        }
        tags = us_tags(e.get("Summary", ""))
        if tags:
            entry["tags"] = tags
        try:
            notes = us_notes(e.get("Summary", ""), name)
        except NoteTooLong as exc:
            raise BuildError(f"US {name}: {exc}") from exc
        if notes:
            entry["notes"] = notes
        for c in codes:
            rows.setdefault(c, []).append((re.sub(r"^The\s+", "", name), dict(entry)))
    if unmapped:
        raise BuildError("US entries with no ISO mapping: " + ", ".join(sorted(unmapped)))
    return {code: merge_us(found) for code, found in rows.items()}


def merge_us(found: list) -> dict:
    """One ISO code, several feed entries (Gaza and the West Bank are both Palestine). The most
    cautious one supplies stance, link, tags and notes, so a calm page cannot hide a warning one. If
    the ratings differ, the label names both, so 'Level 4' is not read as covering the whole territory,
    and the stance is capped at 2 (parts of the country): one part's warning must not count the
    whole country as advised against."""
    best = max(found, key=lambda f: f[1]["stance"])  # max keeps the first of equals
    entry = dict(best[1])
    if len({f[1]["label"] for f in found}) > 1:
        ordered = sorted(found, key=lambda f: -f[1]["stance"])
        entry["label"] = "; ".join(f"{n}: {e['label']}" for n, e in ordered)
        entry["stance"] = min(entry["stance"], 2)
    entry["updated"] = max(f[1]["updated"] for f in found)
    return entry


# --- UK ---------------------------------------------------------------------------------------

def uk_stance(flags: list) -> int:
    unknown = set(flags) - UK_PARTS - UK_WHOLE
    if unknown:
        raise BuildError(f"unknown FCDO alert_status {sorted(unknown)}")
    if set(flags) & UK_WHOLE:
        return 3
    if set(flags) & UK_PARTS:
        return 2
    return 0


# The UK and DE labels are text this pipeline writes, in English and German whatever the app locale.
# `labelKey` names which of them it is, so the app can show its own translation; `label` stays as the
# fallback for an app that does not know the key. US labels are the source's own and get no key.
UK_LABEL_KEYS = {
    "FCDO has no travel warning for this country": "uk_none",
    "FCDO " + UK_PHRASE["avoid_all_travel_to_whole_country"]: "uk_all_whole",
    "FCDO " + UK_PHRASE["avoid_all_but_essential_travel_to_whole_country"]: "uk_essential_whole",
    "FCDO " + UK_PHRASE["avoid_all_travel_to_parts"]: "uk_all_parts",
    "FCDO " + UK_PHRASE["avoid_all_but_essential_travel_to_parts"]: "uk_essential_parts",
    "FCDO advises against all travel to parts of the country, and all but essential travel to other parts":
        "uk_all_parts_essential_other",
}


def uk_label_key(label: str) -> str:
    return UK_LABEL_KEYS[label]  # a KeyError means uk_label gained a text with no key


def uk_label(flags: list) -> str:
    if not flags:
        return "FCDO has no travel warning for this country"
    # The whole-country flag outranks a parts flag in the same list, and "all travel" outranks "all
    # but essential"; the label states the strongest advice and leaves the rest to the linked page.
    order = ["avoid_all_travel_to_whole_country", "avoid_all_but_essential_travel_to_whole_country",
             "avoid_all_travel_to_parts", "avoid_all_but_essential_travel_to_parts"]
    present = [f for f in order if f in flags]
    if uk_stance(flags) == 2 and len(present) == 2:
        return ("FCDO advises against all travel to parts of the country, "
                "and all but essential travel to other parts")
    return "FCDO " + UK_PHRASE[present[0]]


def parse_uk(pages: dict, index: dict) -> dict:
    """pages: slug -> the page payload."""
    out, unmapped = {}, []
    for slug, page in sorted(pages.items()):
        d = page.get("details") or {}
        if "alert_status" not in d or not isinstance(d["alert_status"], list):
            raise BuildError(f"UK page {slug} has no alert_status list")
        name = (d.get("country") or {}).get("name") or page.get("title", slug)
        codes = resolve(name, index, UK_ALIASES)
        if not codes:
            unmapped.append(name)
            continue
        flags = d["alert_status"]
        entry = {
            "stance": uk_stance(flags),
            "label": uk_label(flags),
            "labelKey": uk_label_key(uk_label(flags)),
            "updated": iso_date(page.get("public_updated_at") or d.get("updated_at")),
            "url": "https://www.gov.uk/foreign-travel-advice/" + slug,
        }
        try:
            notes = uk_notes(page) if entry["stance"] else []
        except NoteTooLong as exc:
            raise BuildError(f"UK {slug}: {exc}") from exc
        if notes:
            entry["notes"] = notes
        for c in codes:
            merge(out, c, dict(entry))
    if unmapped:
        raise BuildError("UK countries with no ISO mapping: " + ", ".join(sorted(unmapped)))
    return out


# --- DE ---------------------------------------------------------------------------------------

# The AA's feed flags only the formal Reisewarnung / Teilreisewarnung. Its second tier, "Von Reisen
# nach X wird (dringend) abgeraten", exists only in the page text (Georgia, Israel and North Korea
# have all flags false and that sentence). The AA's terms forbid showing its information incomplete
# or in a distorting context, so a page that says "abgeraten" must never be labelled "no warning":
# the sentences are carried verbatim as `notes` and the row counts as stance 2.
DE_MAX_NOTES = 3
# The app's own limit per note: MAX_ADVISORY_NOTE_CHARS in Advisory.kt must stay equal to this. A longer
# note is a build error here and is dropped, never cut, by the app (the AA's terms forbid showing its
# text incomplete), so the two sides agree on what "too long" means.
DE_NOTE_MAX_CHARS = 1500
# More than this share of countries without a fetched detail page means the detail endpoint is
# broken, not that one country hiccuped.
DE_MAX_MISSING_SHARE = 0.10

DE_LABEL_NONE = "Keine formelle Reisewarnung"
DE_LABEL_ADVISED_AGAINST = "Keine formelle Reisewarnung, aber Reiseabraten (siehe Hinweise)"


DE_LABEL_KEYS = {
    DE_LABEL_NONE: "de_none",
    DE_LABEL_ADVISED_AGAINST: "de_advised_against",
    "Teilreisewarnung": "de_partial",
    "Reisewarnung": "de_warning",
}


def de_label(warning: bool, partial: bool, notes: list = ()) -> str:
    if warning:
        return "Reisewarnung"
    if partial:
        return "Teilreisewarnung"
    return DE_LABEL_ADVISED_AGAINST if notes else DE_LABEL_NONE


def de_stance(warning: bool, partial: bool, notes: list = ()) -> int:
    # 3 stays the formal Reisewarnung alone, so the "advise against travel" count is unchanged.
    return 3 if warning else 2 if (partial or notes) else 0


_ANY_TAG = re.compile(r"<[^>]+>")
# <li> and headings get a marker so that block structure survives tag stripping; every other block
# tag (p, br, ul, div ...) is only a line break.
_LINE_BREAK = re.compile(r"<(/?)(li|h[1-6])\b[^>]*>|</?(?:p|br|ul|ol|div|blockquote|tr|table|section)\b[^>]*>", re.I)
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+(?=[A-ZÄÖÜ„\"“(])")
# A full stop that does not end a sentence: abbreviations and ordinal dates ("am 12. November").
_ABBREV = re.compile(r"\b(?:o\.\s?g|z\.\s?B|u\.\s?a|d\.\s?h|u\.\s?U|i\.\s?d\.\s?R|bzw|ca|inkl|ggf|evtl|vgl|Nr)\.", re.I)
_ORDINAL_DATE = re.compile(r"(?<=\d)\.(?=\s+(?:Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\b)")
_DOT = "\x00"


def _split_sentences(text: str) -> list:
    text = _ABBREV.sub(lambda m: m.group(0)[:-1] + _DOT, text)
    text = _ORDINAL_DATE.sub(_DOT, text)
    return [s.replace(_DOT, ".").strip() for s in _SENTENCE_END.split(text) if s.strip()]


def _blocks(content: str) -> list:
    """(kind, text) per line of the page: 'li', 'h' (heading) or 'p'. Other tags vanish, entities are
    decoded, whitespace is collapsed; the words are untouched."""
    def mark(m):
        if m.group(2) and not m.group(1):
            return "\n\x01" if m.group(2).lower() == "li" else "\n\x02"
        return "\n"
    text = html.unescape(_ANY_TAG.sub("", _LINE_BREAK.sub(mark, content or ""))).replace("\xa0", " ")
    out = []
    for line in text.split("\n"):
        kind = "li" if line.startswith("\x01") else "h" if line.startswith("\x02") else "p"
        stripped = re.sub(r"\s+", " ", line.lstrip("\x01\x02")).strip()
        if kind == "p" and stripped[:1] in ("-", "•", "–"):
            kind = "li"  # a hyphen list typed inside one paragraph, lines split by <br/>
        stripped = stripped.strip(" -•–\t")
        if stripped:
            out.append((kind, stripped))
    return out


class Unit:
    """One sentence of a page: `text` is the whole sentence, judged, hashed and (if selected) shown."""

    def __init__(self, text: str, context: str = ""):
        self.text, self.context = text, context


def _join_items(items: list) -> str:
    out = ""
    for it in items:
        glue = not out or out[-1] in ",;:." or out.rsplit(" ", 1)[-1] in ("und", "sowie", "oder", "bzw.")
        out += (" " if glue else "; ") + it
    return out.strip()


def _list_unit(head: str, items: list, tail: str, context: str) -> Unit:
    """'Von Reisen in folgende Regionen wird abgeraten:' + its <li> items, or 'Von Reisen' + items +
    'wird abgeraten.' (Tunisia): one sentence the page only broke up for layout. The items are
    joined with '; ' unless they already end in punctuation. Over the length limit the sentence is
    not shortened: if it is selected as a note, trim_note fails the build (NoteTooLong) naming the
    source and country."""
    tail = (" " + tail) if tail else ""
    return Unit(head + " " + _join_items(items) + tail, context=context)


def de_units(content: str) -> list:
    """The page as sentences. A paragraph that stops without a full stop, or ends in a colon, goes on
    in the list items that follow it (and a lower-case paragraph after the list: Tunisia's
    'Von Reisen' / four <li> / 'wird abgeraten.'); headings end a sentence. A list that nothing
    continues is not joined: its items are sentences of their own."""
    units, state = [], {"head": None, "items": [], "tail": [], "ctx": ""}
    last_p = [""]

    def flush():
        head, items, tail = state["head"], state["items"], " ".join(state["tail"])
        if head is not None:
            if items and (head.endswith(":") or tail):
                units.append(_list_unit(head, items, tail, last_p[0]))
            else:
                units.append(Unit(head if not tail else head + " " + tail, context=last_p[0]))
                for it in items:
                    units.extend(Unit(s, context=last_p[0]) for s in _split_sentences(it))
        state.update(head=None, items=[], tail=[])

    for kind, text in _blocks(content):
        if kind == "h":
            flush()
        elif kind == "li":
            if state["head"] is not None and not state["tail"]:
                state["items"].append(text)
            else:
                flush()
                units.extend(Unit(s, context=last_p[0]) for s in _split_sentences(text))
        else:
            if state["head"] is not None and text[:1].islower():
                if state["items"]:
                    state["tail"].append(text)
                    if text[-1] in ".!?":
                        flush()
                else:
                    state["head"] += " " + text
                    if text[-1] in ".!?":
                        flush()
                continue
            flush()
            sents = _split_sentences(text)
            for s in sents[:-1]:
                units.append(Unit(s, context=last_p[0]))
                last_p[0] = s
            if sents:
                if sents[-1][-1] in ".!?":
                    units.append(Unit(sents[-1], context=last_p[0]))
                    last_p[0] = sents[-1]
                else:
                    state["head"] = sents[-1]
    flush()
    return units


def de_sentences(content: str) -> list:
    return [u.text for u in de_units(content)]


class NoteTooLong(BuildError):
    pass


def trim_note(sent: str) -> str:
    """A note is carried whole or not at all: the sources' terms forbid showing their text incomplete,
    so over the limit the build fails loudly (a human decides) instead of cutting."""
    units = len(sent.encode("utf-16-le")) // 2  # the app counts UTF-16 units (Kotlin String.length)
    if units > DE_NOTE_MAX_CHARS:
        raise NoteTooLong(f"note of {units} chars exceeds {DE_NOTE_MAX_CHARS}: {sent[:80]!r}...")
    return sent


# The second-tier advice is phrased "Von (nicht notwendigen) Reisen in/nach ... wird (dringend)
# abgeraten" or "Vor Reisen in ... wird gewarnt" (Israel), and a few pages give it as an imperative
# ("Meiden Sie möglichst Reisen in die Provinzen ...": Angola, Turkey, Uzbekistan, Turkmenistan).
# "abgeraten" alone is far too wide: the pages also say it of alcohol in public, night driving,
# drones, currency exchange and rental cars (observed 2026-11-25: 197 hits in 89 countries, most of
# them not about going to a place). So a note must also name a trip (Reise/Reisen, Einzelreisen,
# Individualreisen, Aufenthalte, Besuch). That drops sentence fragments ("wird abgeraten.") too.
# Einreise/Ausreise are not trips: they are about documents. "Reisen auf dem Landweg" is advice
# about the route into the neighbouring country (Dominican Republic -> Haiti).
_ADVICE = re.compile(r"abgeraten|abzuraten|\bgewarnt\b", re.I)
_TRIP = (r"\bReisen?\b|\b[A-ZÄÖÜ]\w*(?<!Ein)(?<!Aus)(?<!Weiter)(?<!Wieder)(?<!Rück)(?<!An)(?<!Durch)(?<!Zu)(?<!Heim)(?<!Mit)(?<!Ab)reisen\b")
_TRAVEL = re.compile(_TRIP + r"|\bAufenthalten?\b|\bBesuch(?:en)?\b")
# An imperative counts for Reisen anywhere, for Aufenthalte only at a border ("Vermeiden Sie
# Aufenthalte in der Sonne / an Stränden / in der Nähe von Wasserläufen" are not about a region).
_IMPERATIVE = re.compile(
    r"\b(?:Vermeiden|Meiden)\s+Sie\s+(?:(?!bei\b|auf\b|während\b|im\b|vor\b)\w+\s+){0,5}?"
    r"(?:(?:" + _TRIP + r")\s+(?:in|nach|durch|zu)\b"
    r"|Aufenthalte\b[^.;]*Grenz"
    r"|Fahrten\b[^.;]*?(?:in die (?:Provinz|Region)|Grenz))")
# The guidance sentences that only refer back to the advice ("Führen Sie unabdingbare Reisen in die
# Regionen, von denen abgeraten wird, nur mit ... durch") are not the advice.
_GUIDANCE = re.compile(r"^(?:Führen|Beachten|Informieren|Berücksichtigen|Erkundigen|Prüfen)\w*\s+Sie\b")
_ROUTE_ONLY = re.compile(r"\bauf dem Landweg\b")
_SEE_ALSO = re.compile(r"[,;]?\s*siehe\b[^.]*\.?$")
# Advice about a category of place, not a place ("Von Reisen in die unmittelbare Nähe militärischer
# Sperrgebiete", Vietnam, true of every country and no reason to mark this one).
_CATEGORY_ONLY = re.compile(r"\bmilitärische\w*\s+Sperrgebiet", re.I)
# "Von Reisen in diese Gebiete wird daher abgeraten" depends on the sentence before it; alone it
# says nothing, and the sentence before it is already a note.
_ORPHAN = re.compile(r"^(?:Diese|Dies|Daher|Deshalb|Darum)\b|\bbetroffenen?\s+Gebiete?\b|\bin diese[nm]?\s+(?:Gebiete|Regionen|Landesteile|Provinzen|Orte|Bereiche|Grenzgebiete)\b|\bwird\s+(?:daher|deshalb)\b", re.I)
_BACKREF = re.compile(r"\bo\.\s?g\.|\boben genannt")


# "nicht mehr abgeraten", "nicht länger dringend abgeraten", "kein Abraten mehr": a sentence that
# withdraws the advice must never become a note that asserts it.
_NEGATED = re.compile(
    r"\b(?:nicht|nie|niemals)\s+(?:(?:mehr|länger|weiter|dringend|ausdrücklich|generell|derzeit|grundsätzlich)\s+)*(?:abgeraten|abzuraten|gewarnt)"
    r"|\bnicht\s+(?:mehr|länger)\b[^.;]*(?:abgeraten|abzuraten|gewarnt)"
    r"|\bkein\w*\b[^.;]{0,60}\b(?:Abraten|abzuraten)",
    re.I,
)


def is_travel_advice(sent: str) -> bool:
    if sent.rstrip().endswith(":"):
        return False  # a bare stub ("Von Reisen in folgende Regionen wird abgeraten:") names nothing
    if _ROUTE_ONLY.search(sent) or _NEGATED.search(sent) or _CATEGORY_ONLY.search(sent):
        return False
    if _ORPHAN.search(sent) or _GUIDANCE.search(sent):
        return False
    if _IMPERATIVE.search(sent):
        return True
    return bool(_ADVICE.search(sent)) and bool(_TRAVEL.search(sent))


# A page may quote advice about another country ("Reisen aus der und in die Russische Föderation" on
# Belarus's page, Israel's sentences on the Palestinian territories'): that would be shown under the
# wrong country. A sentence that names another country and not the page's own is dropped, unless it is
# about a border zone ("Grenzgebiet zu Brasilien" is advice about Paraguay). The formal flag, which is
# per country, still shows, so dropping can only lose a note, never a warning.
_BORDER = re.compile(r"Grenz|grenz", )
_INFLECTION = r"(?:s|n|en|e|er|em|es)?"


def _country_pattern(name: str):
    """The names one feed entry goes by, as one regex that tolerates German inflection ('Israels',
    'Russischen Föderation'). 'Korea, Republik / Nordkorea' style annotations give alternatives."""
    base = name.replace("*", "")
    alts = [re.sub(r"\s*\([^)]*\)", "", base)] + re.findall(r"\(([^)]*)\)", base)
    alts = [a.strip() for part in alts for a in part.split("/") if a.strip()]
    parts = []
    for alt in alts:
        words = [re.escape(w) + _INFLECTION for w in alt.split()]
        parts.append(r"(?<![\w-])" + r"\s+".join(words) + r"(?![\w-])")
    return re.compile("|".join(parts)) if parts else None


def foreign_matcher(own: str, names: dict):
    """names: ISO code -> German country name from the feed. Returns sent -> True when the sentence
    belongs to another country's page."""
    own_re = _country_pattern(names.get(own, ""))
    others = [r for c, n in names.items() if c != own and (r := _country_pattern(n))]

    def is_foreign(sent: str) -> bool:
        if own_re and own_re.search(sent):
            return False
        if _BORDER.search(sent):
            return False
        return any(r.search(sent) for r in others)

    return is_foreign


def own_matcher(own: str, names: dict):
    own_re = _country_pattern(names.get(own, ""))
    return lambda sent: bool(own_re and own_re.search(sent))


def _key(sent: str) -> str:
    """What two sentences are compared by: the 'siehe Sicherheit' suffix the second copy often has
    is ignored, as are case and punctuation."""
    return re.sub(r"\W+", " ", _SEE_ALSO.sub("", sent)).strip().lower()


def _words(sent: str) -> set:
    return {w for w in _key(sent).split() if len(w) > 2}


def _redundant(units: list) -> list:
    """Drops a sentence whose words are all in a longer one kept before or after it (Morocco says
    'Westsahara sowie Grenzregion mit Algerien' and then each half again). Equal keys keep the first."""
    keep = []
    for i, u in enumerate(units):
        k, w = _key(u.text), _words(u.text)
        covered = False
        for j, v in enumerate(units):
            if i == j:
                continue
            kv, wv = _key(v.text), _words(v.text)
            if k == kv:
                covered = j < i
            elif k in kv or w < wv:
                covered = True
            if covered:
                break
        if not covered:
            keep.append(u)
    return keep


# "... und beachten Sie die Reise- und Sicherheitshinweise für die Demokratische Republik Kongo" (Angola)
# is a pointer to another page, not a statement about another country.
_POINTER = re.compile(r"[,;]?\s*(?:und\s+)?(?:beachten Sie|siehe)\b.*$", re.I)


def de_notes(content: str, is_foreign=None, is_own=None, full_warning: bool = False) -> list:
    """Up to three distinct sentences advising against travel, verbatim and in page order except that
    a sentence naming the page's own country or saying 'gewarnt' goes first. A sentence that points
    back ('in die o.g. Grenzgebiete') brings the sentence before it along, so the note can be read
    alone."""
    found = []
    for u in de_units(content):
        if not is_travel_advice(u.text) or (is_foreign and is_foreign(_POINTER.sub("", u.text))):
            continue
        if full_warning and not re.search(r"abgeraten|abzuraten", u.text, re.I):
            continue  # "Vor Reisen nach X wird gewarnt" is the Reisewarnung itself, which the label states
        found.append(u)
    notes = []
    for u in _redundant(found):
        text = u.text
        if _BACKREF.search(text) and u.context and u.context not in text \
                and len(u.context) + 1 + len(text) <= DE_NOTE_MAX_CHARS:
            text = u.context + " " + text
        specific = bool(is_own and is_own(u.text)) or bool(re.search(r"\bgewarnt\b", u.text))
        notes.append((0 if specific else 1, trim_note(text)))
    notes.sort(key=lambda n: n[0])  # stable: page order inside a tier
    return [t for _, t in notes[:DE_MAX_NOTES]]


# --- the tripwire -----------------------------------------------------------------------------
# A country with no formal flag and no extracted note is stance 0. If its page nevertheless has a
# sentence that looks like travel advice, the extractor has missed a phrasing (that is how Tunisia,
# Turkey and Angola slipped through), and a silent miss shows "no warning" for a country the AA
# advises against. So the build fails on such a page unless a human has looked at that exact
# sentence: tools/advisory/de_reviewed.json maps a country code to {sha256 of the sentence: reason}.
# A reworded sentence has a new hash and is reviewed again.
_WIRE_TRIGGER = re.compile(r"abgeraten|abzuraten|\bgewarnt\b|Lebensgefahr|Sperrzone|Sperrgebiet|\b(?:Vermeiden|Meiden)\s+Sie\b", re.I)
_WIRE_PLACE = re.compile(_TRIP + r"|Aufenthalt|Besuch|Gebiet|Region|Provinz|Grenz|Landesteil|Bundesstaat|Zone\b|Insel|Küste|Distrikt|Bezirk")
REVIEWED = Path(__file__).resolve().parent / "de_reviewed.json"


def sentence_sha(sent: str) -> str:
    return hashlib.sha256(sent.encode("utf-8")).hexdigest()


def load_reviewed(path: Path = REVIEWED) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise BuildError(f"cannot read {path}: {e}")
    if not isinstance(data, dict) or not all(isinstance(v, dict) for v in data.values()):
        raise BuildError(f"{path} must map a country code to an object of sentence hashes")
    return data


def de_wire_hits(content: str) -> list:
    """Sentences of a page that look like advice about travel to a place."""
    return [u.text for u in de_units(content) if _WIRE_TRIGGER.search(u.text) and _WIRE_PLACE.search(u.text)]


def detail_content(detail, key: str):
    """The page HTML of one detail payload, or None if the payload is not the expected shape."""
    resp = detail.get("response") if isinstance(detail, dict) else None
    entry = resp.get(str(key)) if isinstance(resp, dict) else None
    content = entry.get("content") if isinstance(entry, dict) else None
    return content if isinstance(content, str) and content.strip() else None


def warn(msg: str) -> None:
    print(f"warning: {msg}", file=sys.stderr)


def parse_de(feed, valid_codes: set, details: dict = None, reviewed: dict = None) -> dict:
    """details: node id -> detail payload. A country whose three flags are all false and whose page
    is missing cannot be told from one with Reiseabraten, so that is an error, never 'no warning'.
    reviewed: the tripwire's allowlist (see de_reviewed.json); None means nothing is reviewed."""
    resp = feed.get("response") if isinstance(feed, dict) else None
    if not isinstance(resp, dict):
        raise BuildError("DE feed has no 'response' object")
    details = details or {}
    reviewed = reviewed or {}
    out, bad, unverified, missing, tripped = {}, [], [], [], []
    total = 0
    names = {str(e.get("countryCode", "")).upper(): str(e.get("countryName", ""))
             for e in resp.values() if isinstance(e, dict)}
    for key, e in resp.items():
        if not isinstance(e, dict):
            continue  # lastModified, contentList
        code = str(e.get("countryCode", "")).upper()
        if code not in valid_codes:
            bad.append(f"{e.get('countryName', key)} ({code or 'no code'})")
            continue
        if not isinstance(e.get("warning"), bool) or not isinstance(e.get("partialWarning"), bool):
            raise BuildError(f"DE entry {code} has non-boolean warning flags")
        if not str(key).isdigit():
            raise BuildError(f"DE entry {code} has a non-numeric node id {key!r}")
        total += 1
        warning, partial = e["warning"], e["partialWarning"]
        if e.get("situationWarning") is True or e.get("situationPartWarning") is True:
            warn(f"DE {code} has a situation warning flag set; the app does not show it yet")
        content = detail_content(details.get(str(key)), key)
        notes = []
        if content is None:
            missing.append(code)
            if not warning and not partial:
                unverified.append(code)
        else:
            try:
                notes = de_notes(content, foreign_matcher(code, names), own_matcher(code, names), warning)
            except NoteTooLong as exc:
                raise BuildError(f"DE {code}: {exc}") from exc
            if not warning and not partial and not notes:
                is_foreign = foreign_matcher(code, names)
                for sent in de_wire_hits(content):
                    if is_foreign(_POINTER.sub("", sent)):
                        continue  # about another country, as de_notes judges it
                    if sentence_sha(sent) not in (reviewed.get(code) or {}):
                        tripped.append(f"{code} [{sentence_sha(sent)[:12]}] {sent[:200]}")
        updated = datetime.datetime.fromtimestamp(int(e["lastModified"]), datetime.timezone.utc)
        entry = {
            "stance": de_stance(warning, partial, notes),
            "label": de_label(warning, partial, notes),
            "labelKey": DE_LABEL_KEYS[de_label(warning, partial, notes)],
            "updated": updated.date().isoformat(),
            "url": DE_LINK + str(key),
        }
        if notes:
            entry["notes"] = notes
        merge(out, code, entry)
    if bad:
        raise BuildError("DE entries with no usable ISO code: " + ", ".join(sorted(bad)))
    if unverified:
        raise BuildError("DE page text missing for countries with no formal warning, so 'no warning' "
                         "cannot be verified: " + ", ".join(sorted(unverified)))
    if tripped:
        raise BuildError(
            f"{len(tripped)} German page sentence(s) look like travel advice on a country the build "
            "rates 'no warning'. Either teach de_notes the phrasing, or review the sentence and add "
            "its full sha256 and a reason to tools/advisory/de_reviewed.json:\n  " + "\n  ".join(tripped))
    if total and len(missing) > DE_MAX_MISSING_SHARE * total:
        raise BuildError(f"DE page text missing for {len(missing)} of {total} countries: " + ", ".join(sorted(missing)))
    return out


# --- assembly ---------------------------------------------------------------------------------

def assemble(per_source: dict, fetched_at: dict, generated_at: str) -> dict:
    for src, rows in per_source.items():
        if len(rows) < MIN_COUNTRIES[src]:
            raise BuildError(f"{src} yielded only {len(rows)} countries, expected at least {MIN_COUNTRIES[src]}")
    countries = {}
    for src in ("US", "UK", "DE"):
        for code, entry in per_source[src].items():
            countries.setdefault(code, {})[src] = entry
    return {
        "dataFormat": DATA_FORMAT,
        "generatedAt": generated_at,
        "sources": {s: {"label": SOURCES[s], "fetchedAt": fetched_at[s]} for s in ("US", "UK", "DE")},
        "countries": dict(sorted(countries.items())),
    }


def keep_if_unchanged(new: dict, old) -> dict:
    """'Data as of' must only move when an advisory does. If the countries and the source labels
    match the previous file, return the previous file whole: that keeps generatedAt and also
    fetchedAt, which changes on every run and would otherwise make every week look like a change
    (and every publication a new file)."""
    if isinstance(old, dict) and old.get("dataFormat") == new["dataFormat"] \
            and old.get("countries") == new["countries"] \
            and {k: v.get("label") for k, v in (old.get("sources") or {}).items()} \
            == {k: v["label"] for k, v in new["sources"].items()}:
        return old
    return new


DE_DRIFT_LIMIT = 4


def de_advised_count(doc) -> int:
    rows = (doc or {}).get("countries") if isinstance(doc, dict) else None
    if not isinstance(rows, dict):
        return 0
    return sum(1 for c in rows.values() if isinstance(c, dict) and (c.get("DE") or {}).get("stance", 0) >= 2)


def warn_on_drift(new: dict, old) -> None:
    """A page rewrite that makes the extractor miss (or over-match) shows up as many countries moving
    at once. Not an error: the AA does change its advice, and a real week can move a few."""
    if not isinstance(old, dict) or "countries" not in old:
        return
    was, now = de_advised_count(old), de_advised_count(new)
    if abs(now - was) > DE_DRIFT_LIMIT:
        warn(f"DE countries rated 'reconsider' or worse moved from {was} to {now} against the previous file; "
             "check that the page extraction still reads the AA's text")


def load_places() -> tuple:
    places = json.loads(PLACES.read_text(encoding="utf-8"))
    return name_index(places), {p["code"] for p in places}


def check_targets(valid: set) -> None:
    for table in (US_ALIASES, UK_ALIASES):
        for name, codes in table.items():
            for c in codes:
                if c not in valid:
                    raise BuildError(f"alias {name!r} targets {c}, which is not in places.json")


def check_urls(per_source: dict) -> None:
    bad = sorted({f"{src} {code}: {e['url']}" for src, rows in per_source.items() for code, e in rows.items()
                  if urllib.parse.urlparse(e["url"]).hostname not in URL_HOSTS})
    if bad:
        raise BuildError("advisory urls on a host that is not allowed: " + "; ".join(bad[:5]))


def now_iso() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def http_get(url: str, tries: int = 4) -> bytes:
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "BeyondBorders-advisory-build (+https://github.com/Riedel-David/Beyond-Borders)"})
            with urllib.request.urlopen(req, timeout=40) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - any failure is retried, then reported by URL
            last = e
            time.sleep(2 * (attempt + 1))
    raise BuildError(f"cannot fetch {url}: {last}")


def download(cache: Path) -> None:
    """Fetches every feed into `cache`. Three calls plus one per UK country, four at a time with a
    pause between a worker's requests, which is gentle for ~230 small JSON files."""
    # Start from empty per-country directories: a page that fails to download must be missing, never
    # last week's copy read as this week's.
    for sub in ("uk", "de_detail"):
        shutil.rmtree(cache / sub, ignore_errors=True)
    (cache / "uk").mkdir(parents=True, exist_ok=True)
    (cache / "us.json").write_bytes(http_get(US_URL))
    de_raw = http_get(DE_URL)
    (cache / "de.json").write_bytes(de_raw)
    index_raw = http_get(UK_INDEX_URL)
    (cache / "uk_index.json").write_bytes(index_raw)
    slugs = uk_slugs(json.loads(index_raw))

    def one(slug: str) -> None:
        (cache / "uk" / f"{slug}.json").write_bytes(http_get(f"{UK_INDEX_URL}/{slug}"))
        time.sleep(0.2)

    with ThreadPoolExecutor(max_workers=4) as ex:
        list(ex.map(one, slugs))  # re-raises the first failure

    # The German page text, one call per country, two at a time: that is where the AA's "abgeraten"
    # lives. A country that fails after http_get's retries is left out; parse_de decides whether
    # that is tolerable (it never is for a country with no formal warning).
    (cache / "de_detail").mkdir(exist_ok=True)

    def one_de(key: str) -> None:
        try:
            (cache / "de_detail" / f"{key}.json").write_bytes(http_get(f"{DE_URL}/{key}"))
        except BuildError as e:
            print(f"warning: {e}", file=sys.stderr)
        time.sleep(0.3)

    with ThreadPoolExecutor(max_workers=2) as ex:
        list(ex.map(one_de, de_node_ids(json.loads(de_raw))))


def de_node_ids(feed) -> list:
    resp = feed.get("response") if isinstance(feed, dict) else None
    if not isinstance(resp, dict):
        raise BuildError("DE feed has no 'response' object")
    return [str(k) for k, v in resp.items() if isinstance(v, dict) and str(k).isdigit()]


def uk_slugs(index: dict) -> list:
    try:
        children = index["links"]["children"]
        slugs = [c["details"]["country"]["slug"] for c in children]
    except (KeyError, TypeError) as e:
        raise BuildError(f"FCDO index has an unexpected shape: {e!r}")
    if len(slugs) != len(set(slugs)):
        raise BuildError("FCDO index lists a country twice")
    return slugs


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise BuildError(f"cannot read {path}: {e}")


def build(src: Path, generated_at: str) -> dict:
    index, valid = load_places()
    check_targets(valid)
    for c in ("us.json", "de.json", "uk_index.json"):
        if not (src / c).is_file():
            raise BuildError(f"{src / c} is missing")
    slugs = uk_slugs(read_json(src / "uk_index.json"))
    pages = {s: read_json(src / "uk" / f"{s}.json") for s in slugs}
    de_feed = read_json(src / "de.json")
    details = {}
    for key in de_node_ids(de_feed):
        f = src / "de_detail" / f"{key}.json"
        if f.is_file():
            try:
                details[key] = json.loads(f.read_text(encoding="utf-8"))
            except ValueError:
                pass  # unreadable counts as missing, which parse_de judges
    per_source = {
        "US": parse_us(read_json(src / "us.json"), index),
        "UK": parse_uk(pages, index),
        "DE": parse_de(de_feed, valid, details, load_reviewed()),
    }
    check_urls(per_source)
    for rows in per_source.values():
        for code in rows:
            if code not in valid:
                raise BuildError(f"{code} is not in places.json")
    fetched = {
        s: datetime.datetime.fromtimestamp((src / f).stat().st_mtime, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        for s, f in (("US", "us.json"), ("UK", "uk_index.json"), ("DE", "de.json"))
    }
    return assemble(per_source, fetched, generated_at)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--source", type=Path, help="read cached feeds from DIR instead of downloading")
    ap.add_argument("--cache", type=Path, default=DEFAULT_CACHE, help="where downloads are saved")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--previous", type=Path, help="the file 'unchanged' is judged against (default: --out); "
                    "the weekly job passes the live published copy, which is newer than the bundled one")
    args = ap.parse_args(argv)
    try:
        src = args.source
        if src is None:
            download(args.cache)
            src = args.cache
        doc = build(src, now_iso())
        prev = args.previous or args.out
        if prev.is_file():
            try:
                old = json.loads(prev.read_text(encoding="utf-8"))
            except ValueError:
                old = None  # an unreadable previous file is simply replaced
            warn_on_drift(doc, old)
            doc = keep_if_unchanged(doc, old)
    except BuildError as e:
        print(f"advisory build FAILED, nothing written: {e}", file=sys.stderr)
        return 1
    args.out.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":"), sort_keys=False) + "\n", encoding="utf-8")
    counts = {s: sum(1 for c in doc["countries"].values() if s in c) for s in SOURCES}
    print(f"wrote {args.out}: {len(doc['countries'])} countries, per source {counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
