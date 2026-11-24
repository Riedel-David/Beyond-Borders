"""Build app/src/main/assets/advisory.json from the US, UK and German government travel advisories.

Usage: python3 -I tools/advisory/build_advisory.py [--source DIR] [--out FILE] [--cache DIR]

Without --source the three feeds are downloaded (and the raw payloads saved under --cache, default
tools/advisory/cache/, gitignored, so a failed run can be examined and an offline run repeated):

    US  https://cadataapi.state.gov/api/TravelAdvisories                    one call, level 1-4
    UK  https://www.gov.uk/api/content/foreign-travel-advice[/<slug>]       index + one call per country
    DE  https://www.auswaertiges-amt.de/opendata/travelwarning              one call, warning flags

With --source DIR nothing is downloaded: DIR holds us.json, de.json, uk_index.json and uk/<slug>.json,
which is exactly what a cache directory looks like.

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
# The AA feed carries no per-country page address and the real ones embed a node number, so every
# German row links to the country overview, from which each page is one tap away.
DE_LINK = "https://www.auswaertiges-amt.de/de/service/laender"

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

def de_label(warning: bool, partial: bool) -> str:
    if warning:
        return "Reisewarnung"
    if partial:
        return "Teilreisewarnung"
    return "Keine Reisewarnung"


def parse_de(feed, valid_codes: set) -> dict:
    resp = feed.get("response") if isinstance(feed, dict) else None
    if not isinstance(resp, dict):
        raise BuildError("DE feed has no 'response' object")
    out, bad = {}, []
    for key, e in resp.items():
        if not isinstance(e, dict):
            continue  # lastModified, contentList
        code = str(e.get("countryCode", "")).upper()
        if code not in valid_codes:
            bad.append(f"{e.get('countryName', key)} ({code or 'no code'})")
            continue
        if not isinstance(e.get("warning"), bool) or not isinstance(e.get("partialWarning"), bool):
            raise BuildError(f"DE entry {code} has non-boolean warning flags")
        warning, partial = e["warning"], e["partialWarning"]
        stance = 3 if warning else 2 if partial else 0
        updated = datetime.datetime.fromtimestamp(int(e["lastModified"]), datetime.timezone.utc)
        merge(out, code, {
            "stance": stance,
            "label": de_label(warning, partial),
            "updated": updated.date().isoformat(),
            "url": DE_LINK,
        })
    if bad:
        raise BuildError("DE entries with no usable ISO code: " + ", ".join(sorted(bad)))
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
    (cache / "de.json").write_bytes(http_get(DE_URL))
    index_raw = http_get(UK_INDEX_URL)
    (cache / "uk_index.json").write_bytes(index_raw)
    slugs = uk_slugs(json.loads(index_raw))

    def one(slug: str) -> None:
        (cache / "uk" / f"{slug}.json").write_bytes(http_get(f"{UK_INDEX_URL}/{slug}"))
        time.sleep(0.2)

    with ThreadPoolExecutor(max_workers=4) as ex:
        list(ex.map(one, slugs))  # re-raises the first failure


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
    per_source = {
        "US": parse_us(read_json(src / "us.json"), index),
        "UK": parse_uk(pages, index),
        "DE": parse_de(read_json(src / "de.json"), valid),
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
