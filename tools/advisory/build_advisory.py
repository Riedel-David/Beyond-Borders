"""Build app/src/main/assets/advisory.json from the US, UK and German government travel advisories.

Usage: python3 -I tools/advisory/build_advisory.py [--source DIR] [--out FILE] [--cache DIR]

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
import html
import json
import re
import sys
import time
import unicodedata
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


def us_tags(summary: str) -> list:
    """The risk indicators, from the 'due to ...' clause of the summary's first paragraph. Optional
    decoration: anything that does not parse cleanly is dropped rather than guessed at."""
    m = re.search(r"<p>(.*?)</p>", summary or "", re.S)
    if not m:
        return []
    text = html.unescape(re.sub(r"<[^>]+>", "", m.group(1))).replace("\xa0", " ")
    m = re.search(r"\bdue to\s+([^.]*)", text)
    if not m:
        return []
    parts = re.split(r",\s*(?:and\s+)?|\s+and\s+", m.group(1))
    tags = [re.sub(r"^the ", "", t.strip().lower()) for t in parts]
    return [t for t in tags if 0 < len(t) <= 30]


def parse_us(feed, index: dict) -> dict:
    if not isinstance(feed, list):
        raise BuildError("US feed is not a list")
    out, unmapped = {}, []
    for e in feed:
        title = e.get("Title", "")
        name, _, label = title.rpartition(" - ")
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
        for c in codes:
            merge(out, c, dict(entry))
    if unmapped:
        raise BuildError("US entries with no ISO mapping: " + ", ".join(sorted(unmapped)))
    return out


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
            "updated": iso_date(page.get("public_updated_at") or d.get("updated_at")),
            "url": "https://www.gov.uk/foreign-travel-advice/" + slug,
        }
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
DE_NOTE_WORD = "abgeraten"
DE_MAX_NOTES = 3
DE_NOTE_MAX_CHARS = 400
# Fewer than this share of countries without a fetched detail page means the detail endpoint is
# broken, not that one country hiccuped.
DE_MAX_MISSING_SHARE = 0.10

DE_LABEL_NONE = "Keine formelle Reisewarnung"
DE_LABEL_ADVISED_AGAINST = "Keine formelle Reisewarnung, aber Reiseabraten (siehe Hinweise)"


def de_label(warning: bool, partial: bool, notes: list = ()) -> str:
    if warning:
        return "Reisewarnung"
    if partial:
        return "Teilreisewarnung"
    return DE_LABEL_ADVISED_AGAINST if notes else DE_LABEL_NONE


def de_stance(warning: bool, partial: bool, notes: list = ()) -> int:
    # 3 stays the formal Reisewarnung alone, so the "advise against travel" count is unchanged.
    return 3 if warning else 2 if (partial or notes) else 0


_BLOCK_TAG = re.compile(r"</?(?:p|br|li|ul|ol|div|h[1-6]|blockquote|tr|table|section)\b[^>]*>", re.I)
_ANY_TAG = re.compile(r"<[^>]+>")
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+(?=[A-ZÄÖÜ„\"“(])")


def de_sentences(content: str) -> list:
    """The page text as sentences: block-level tags and line breaks end a sentence, other tags vanish,
    entities are decoded. Whitespace is collapsed; the words are untouched."""
    text = _ANY_TAG.sub("", _BLOCK_TAG.sub("\n", content or ""))
    text = html.unescape(text).replace("\xa0", " ")
    out = []
    for line in text.split("\n"):
        line = re.sub(r"\s+", " ", line).strip(" -•\t")
        for sent in _SENTENCE_END.split(line):
            sent = sent.strip()
            if sent:
                out.append(sent)
    return out


def trim_note(sent: str) -> str:
    if len(sent) <= DE_NOTE_MAX_CHARS:
        return sent
    cut = sent[:DE_NOTE_MAX_CHARS - 1].rsplit(" ", 1)[0].rstrip(",;:")
    return cut + "…"


# "abgeraten" alone is far too wide: the pages also say it of alcohol in public, night driving,
# drones, currency exchange and rental cars (observed 2026-11-25: 197 hits in 89 countries, most of
# them not about going to a place). The second-tier advice is always phrased "Von (nicht notwendigen)
# Reisen in/nach ... wird (dringend) abgeraten", so a note must also say Reise/Reisen (or Einzelreisen, Aufenthalte: Djibouti, Finland). That drops
# sentence fragments ("wird abgeraten.") too. "Reisen auf dem Landweg" is advice about the route
# into the neighbouring country (Dominican Republic -> Haiti), not about the country itself.
_TRAVEL = re.compile(r"\b(?:Einzelr|R)eisen?\b|\bAufenthalten?\b")
_ROUTE_ONLY = re.compile(r"\bauf dem Landweg\b")
_SEE_ALSO = re.compile(r"[,;]?\s*siehe\b[^.]*\.?$")


def is_travel_advice(sent: str) -> bool:
    return DE_NOTE_WORD in sent.lower() and bool(_TRAVEL.search(sent)) and not _ROUTE_ONLY.search(sent)


def de_notes(content: str) -> list:
    """Up to three distinct sentences advising against travel ('Reisen ... abgeraten'), verbatim and
    in page order. The same sentence appears under 'Aktuelles' and again under 'Sicherheit', the
    second time often with ', siehe Sicherheit' appended, so that suffix is ignored when comparing."""
    seen, notes = set(), []
    for sent in de_sentences(content):
        if not is_travel_advice(sent):
            continue
        key = _SEE_ALSO.sub("", sent).rstrip(".:; ")
        if key in seen:
            continue
        seen.add(key)
        notes.append(trim_note(sent))
        if len(notes) == DE_MAX_NOTES:
            break
    return notes


def detail_content(detail, key: str):
    """The page HTML of one detail payload, or None if the payload is not the expected shape."""
    resp = detail.get("response") if isinstance(detail, dict) else None
    entry = resp.get(str(key)) if isinstance(resp, dict) else None
    content = entry.get("content") if isinstance(entry, dict) else None
    return content if isinstance(content, str) and content.strip() else None


def parse_de(feed, valid_codes: set, details: dict = None) -> dict:
    """details: node id -> detail payload. A country whose three flags are all false and whose page
    is missing cannot be told from one with Reiseabraten, so that is an error, never 'no warning'."""
    resp = feed.get("response") if isinstance(feed, dict) else None
    if not isinstance(resp, dict):
        raise BuildError("DE feed has no 'response' object")
    details = details or {}
    out, bad, unverified, missing = {}, [], [], []
    total = 0
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
        content = detail_content(details.get(str(key)), key)
        notes = []
        if content is None:
            missing.append(code)
            if not warning and not partial:
                unverified.append(code)
        else:
            notes = de_notes(content)
        updated = datetime.datetime.fromtimestamp(int(e["lastModified"]), datetime.timezone.utc)
        entry = {
            "stance": de_stance(warning, partial, notes),
            "label": de_label(warning, partial, notes),
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


def load_places() -> tuple:
    places = json.loads(PLACES.read_text(encoding="utf-8"))
    return name_index(places), {p["code"] for p in places}


def check_targets(valid: set) -> None:
    for table in (US_ALIASES, UK_ALIASES):
        for name, codes in table.items():
            for c in codes:
                if c not in valid:
                    raise BuildError(f"alias {name!r} targets {c}, which is not in places.json")


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
        "DE": parse_de(de_feed, valid, details),
    }
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
    args = ap.parse_args(argv)
    try:
        src = args.source
        if src is None:
            download(args.cache)
            src = args.cache
        doc = build(src, now_iso())
        if args.out.is_file():
            try:
                doc = keep_if_unchanged(doc, json.loads(args.out.read_text(encoding="utf-8")))
            except ValueError:
                pass  # an unreadable previous file is simply replaced
    except BuildError as e:
        print(f"advisory build FAILED, nothing written: {e}", file=sys.stderr)
        return 1
    args.out.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":"), sort_keys=False) + "\n", encoding="utf-8")
    counts = {s: sum(1 for c in doc["countries"].values() if s in c) for s in SOURCES}
    print(f"wrote {args.out}: {len(doc['countries'])} countries, per source {counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
