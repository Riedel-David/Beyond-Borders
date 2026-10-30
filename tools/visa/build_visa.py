#!/usr/bin/env python3
"""Build BeyondBorders's bundled visa assets from the Visa-requirements dataset.

    python3 tools/visa/build_visa.py [--passport DE|GB] [--source DIR] [--advice FILE]

Without --source the latest published data is downloaded from
https://riedel-david.github.io/Visa-requirements/ into tools/visa/cache/ first.
That is not reproducible by itself, so the build records what it used: the
index's `generated_at` and each passport's `source_revision` go into the assets
(`asOf`, `dataset.sourceRevision`) and, on a full build, into
docs/visa-source-revisions.txt. --source DIR (a directory holding index.json and
passports/, e.g. `<checkout>/data` or a previous download in the cache) builds
offline from exactly that.

Output (committed, so the app builds without network access):
    app/src/main/assets/visa.json        --passport DE (the shipped default)
    app/src/main/assets/visa_gb.json     --passport GB   (and so on for every passport)

The curation rules live in rules.py. Read that first; it explains which places the
dataset leaves unanswered for a given passport and what we say about them instead.

The build refuses to write unless every place resolves, every rule is live, and
every entry in rules.EXPECTED comes out as promised. A visa answer that is
quietly missing is worse than a build that fails.
"""

from __future__ import annotations

import argparse
import http.client
import json
import re
import shutil
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import rules  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).parent / "cache"
DEFAULT_SOURCE = CACHE / "visa-requirements"
DEFAULT_ADVICE = CACHE / rules.AA_OPENDATA["file"]
ASSETS = REPO / "app" / "src" / "main" / "assets"
REGISTRY = ASSETS / "places.json"
REVISIONS_FILE = REPO / "docs" / "visa-source-revisions.txt"
GAP_FILE = REPO / "docs" / "visa-gap-allowlist.txt"


# ---------------------------------------------------------------------------
# Fetching and reading the dataset
# ---------------------------------------------------------------------------

def _get(url: str) -> bytes:
    """GET with retries. GitHub Pages answers 503 under a burst of requests.

    Anything transient (a reset socket, a short read, a timeout, a 5xx or 429) is
    retried; an HTTP 4xx means the URL or the publication is wrong and retrying
    cannot fix it, so it fails at once.
    """
    last: Exception | None = None
    for attempt in range(8):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "BeyondBorders-build"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            if 400 <= e.code < 500 and e.code != 429:
                raise SystemExit(f"could not download {url}: HTTP {e.code}")
            last = e
        except (OSError, http.client.HTTPException) as e:
            # URLError, TimeoutError, ConnectionError and socket errors are all
            # OSError; IncompleteRead is an HTTPException.
            last = e
        time.sleep(min(1 + 2 * attempt, 12))
    raise SystemExit(f"could not download {url}: {last}")


def check_index_files(index: dict) -> None:
    """Every index entry must be a dict naming exactly passports/<ISO2>.json.

    The index says which files to fetch and read; anything else could make the
    build touch a path outside the dataset.
    """
    for code, meta in index["passports"].items():
        if (not isinstance(meta, dict)
                or not re.fullmatch(r"[A-Z]{2}", code)
                or meta.get("file") != f"passports/{code}.json"):
            raise SystemExit(f"index.json: unexpected entry for {code!r}: {meta!r}")


def fetch(dest: Path) -> None:
    """Download the latest publication into `dest`, all or nothing.

    Written to a sibling directory and swapped in at the end, so a failure halfway
    never leaves an index from one publication next to passports from another.
    """
    base = rules.DATASET["pages"]
    tmp = dest.with_name(dest.name + ".part")
    shutil.rmtree(tmp, ignore_errors=True)
    (tmp / "passports").mkdir(parents=True)
    raw_index = _get(base + "index.json")
    index = json.loads(raw_index)
    (tmp / "index.json").write_bytes(raw_index)

    # The index names the files to fetch and write; only the exact shape
    # passports/<ISO2>.json is accepted, so a hostile or broken index cannot make the
    # build write outside the cache.
    check_index_files(index)

    def one(item: tuple[str, dict]) -> None:
        _, meta = item
        (tmp / meta["file"]).write_bytes(_get(base + meta["file"]))

    # Few workers: Pages rate-limits, and 200 small files are quick anyway.
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(one, sorted(index["passports"].items())))
    # Swap, never delete first: the old cache moves aside, the new one moves in, and
    # only then is the old one removed, so an interruption at any point leaves a
    # complete cache (the new one, or the old one under dest or its .old name).
    old = dest.with_name(dest.name + ".old")
    shutil.rmtree(old, ignore_errors=True)
    if dest.exists():
        dest.rename(old)
    tmp.rename(dest)
    shutil.rmtree(old, ignore_errors=True)
    print(f"downloaded {len(index['passports'])} passports, generated_at "
          f"{index['generated_at']} -> {dest.relative_to(REPO)}")


@dataclass
class Dataset:
    """index.json plus access to the per-passport files beside it."""

    root: Path
    index: dict

    @classmethod
    def open(cls, root: Path) -> "Dataset":
        index = json.loads((root / "index.json").read_text(encoding="utf-8"))
        major = int(str(index.get("schema_version", "0")).split(".")[0])
        if major != rules.DATASET["schema_major"]:
            raise SystemExit(
                f"dataset schema_version {index.get('schema_version')!r}; this "
                f"build understands major {rules.DATASET['schema_major']}"
            )
        if len(index["passports"]) < 150:
            raise SystemExit(
                f"only {len(index['passports'])} passports in index.json; the "
                f"dataset's shape has probably changed"
            )
        check_index_files(index)
        return cls(root, index)

    @property
    def generated_at(self) -> str:
        return self.index["generated_at"]

    def passports(self) -> list[str]:
        return sorted(self.index["passports"])

    def load(self, code: str) -> dict:
        meta = self.index["passports"][code]
        doc = json.loads((self.root / meta["file"]).read_text(encoding="utf-8"))
        if doc["passport"] != code or doc["source_revision"] != meta["source_revision"]:
            raise SystemExit(
                f"{meta['file']} does not match index.json (passport "
                f"{doc['passport']!r}, revision {doc['source_revision']!r} vs "
                f"{meta['source_revision']!r}) — the files come from two "
                f"publications; download again"
            )
        return doc


@dataclass
class Row:
    """One whole-country answer, already mapped onto our categories."""

    category: str
    days: int | None
    status: str          # upstream's status, verbatim, for `datasetSaid`
    special: bool
    note: str | None     # only kept for `special`; the app shows it verbatim


@dataclass
class Usage:
    """Which fallback rules fired across the whole run (see check_rules_live)."""

    generic: set = field(default_factory=set)
    ignored: set = field(default_factory=set)
    area: set = field(default_factory=set)
    eta_rules: set = field(default_factory=set)
    whole_sovereign: set = field(default_factory=set)
    gap_fills: set = field(default_factory=set)
    sov_gaps: set = field(default_factory=set)   # (passport, place) with no dataset row
    esta_unclassified: list = field(default_factory=list)


class TooThin(Exception):
    """A passport whose upstream article gives too few answers to ship."""


def esta_verdict(notes: str) -> str | None:
    """'required', 'exempt' or None (unrecognised) for a note that mentions ESTA.

    Read sentence by sentence, because one note can say both: Brunei's "ESTA ...
    required if arriving by air. ESTA is not required for Guam" is a requirement for
    the US itself. Any sentence that requires one wins; otherwise any exemption
    makes it exempt.
    """
    verdicts = set()
    for sentence in re.split(r"(?<=[.!?])\s+|\n", notes.lower()):
        if rules.ESTA_MARKER.lower() not in sentence:
            continue
        if re.search(rules.ESTA_NEGATIONS, sentence):
            verdicts.add("exempt")
        elif re.search(rules.ESTA_REQUIRED, sentence):
            verdicts.add("required")
        else:
            verdicts.add("unknown")
    if "required" in verdicts:
        return "required"
    # An ESTA sentence nobody classified, next to an exemption, still needs a human.
    return "exempt" if verdicts == {"exempt"} else None


def trim_note(text: str, limit: int) -> str:
    """Upstream's note on one line, cut to about `limit` characters.

    Some notes run to 1,900 characters (the Chinese rules for Indonesia, Mexico
    and Ukraine); the app shows the note in a row, so it ships the opening, cut at
    the last sentence end that fits, or failing that the last word, plus an
    ellipsis. The full text is not on the row; it is in the Wikipedia article that the
    visa screen's attribution line links to.
    """
    text = " ".join(text.split())
    if len(text) <= limit:
        return text
    head = text[:limit]
    ends = [m.end() for m in re.finditer(r"[.!?](?=\s)", head)]
    if ends and ends[-1] >= limit // 3:
        return head[:ends[-1]] + " …"
    # The limit may fall exactly on a word boundary: then the whole head is words.
    cut = limit if text[limit] == " " else head.rfind(" ")
    if cut <= 0:  # one unbroken word: cutting it is all there is
        return head + " …"
    return head[:cut].rstrip(",;:") + " …"


def row_from_record(rec: dict, usage: Usage) -> Row:
    status = rec["status"]
    category = rules.REQUIREMENT_CATEGORY.get(status)
    if category is None:
        raise SystemExit(
            f"unknown status {status!r}. The dataset's vocabulary has grown; add "
            f"it to REQUIREMENT_CATEGORY in rules.py."
        )
    # Upstream files an authorisation you must obtain before flying under
    # `visa_free` in two shapes (see rules.ETA_RULES); both are ETA for us.
    if status == "visa_free":
        if "eta" in rec["alternative_statuses"]:
            category = "ETA"
            usage.eta_rules.add("alternative")
        elif (rec["destination"] in rules.ESTA_DESTINATIONS
              and rules.ESTA_MARKER in (rec["notes"] or "")):
            verdict = esta_verdict(rec["notes"] or "")
            if verdict == "required":
                category = "ETA"
                usage.eta_rules.add("esta:" + rec["destination"])
            elif verdict is None:
                # Neither a known requirement nor a known exemption: a reworded
                # note could be either, and guessing wrong ships a wrong answer.
                usage.esta_unclassified.append(
                    f"{rec['passport']}->{rec['destination']}: "
                    + " ".join((rec["notes"] or "").split())[:200])
    days = None
    if category == "VISA_FREE" and rec["allowed_stay_days"]:
        days = rec["allowed_stay_days"]
    note = None
    if status == rules.SPECIAL_STATUS:
        note = (trim_note(rec["notes"] or "", rules.NOTE_MAX_CHARS)
                or rules.SPECIAL_FALLBACK_NOTE)
    return Row(category, days, status, status == rules.SPECIAL_STATUS, note)


def load_row(doc: dict, places: set[str], usage: Usage) -> dict[str, Row]:
    """A passport's destination -> Row, from its `area == null` records.

    Destinations outside our registry must be named in IGNORED_DESTINATIONS; the
    AREA_IS_WHOLE_PLACE places are read from their one area record, and a state
    filed only as "<state> and territories" (rules.WHOLE_SOVEREIGN_AREAS) from that.
    """
    passport = doc["passport"]
    whole: dict[str, Row] = {}
    by_dest: dict[str, list[dict]] = {}
    for rec in doc["records"]:
        if rec["passport"] != passport:
            raise SystemExit(f"{passport}: record for passport {rec['passport']!r}")
        by_dest.setdefault(rec["destination"], []).append(rec)
    for dest, recs in by_dest.items():
        if dest not in places:
            if dest not in rules.IGNORED_DESTINATIONS:
                raise SystemExit(
                    f"{passport}: destination {dest!r} is not a place in the "
                    f"registry. Map it or add it to IGNORED_DESTINATIONS."
                )
            usage.ignored.add(dest)
            continue
        nulls = [r for r in recs if r["area"] is None]
        if len(nulls) > 1:
            raise SystemExit(f"{passport}: two whole-country records for {dest}")
        if nulls:
            whole[dest] = row_from_record(nulls[0], usage)
        elif (dest in rules.AREA_IS_WHOLE_PLACE and len(
                named := [r for r in recs
                          if r["area"] in rules.AREA_IS_WHOLE_PLACE[dest]]) == 1):
            whole[dest] = row_from_record(named[0], usage)
            usage.area.add(dest)
        else:
            # "Australia and territories": the whole state filed under an area.
            marked = [r for r in recs if r["area"] in rules.WHOLE_SOVEREIGN_AREAS]
            if len(marked) == 1:
                whole[dest] = row_from_record(marked[0], usage)
                usage.whole_sovereign.add(dest)
            elif len(marked) > 1:
                raise SystemExit(f"{passport}: two whole-state records for {dest}")
    if passport in places:
        if passport in whole:
            raise SystemExit(f"{passport} has a row for its own country")
        whole[passport] = Row("HOME", None, "home", False, None)
    return whole


def load_advice_links(advice: Path) -> dict[str, str]:
    """ISO alpha-2 -> Auswärtiges Amt page URL, from their open-data feed.

    The feed is keyed by content id, with the country code inside each entry, and
    it carries one non-country key (`contentList`) plus a top-level
    `lastModified` — hence the shape check rather than a blind walk.
    """
    payload = json.loads(advice.read_text(encoding="utf-8"))
    response = payload.get("response")
    if not isinstance(response, dict):
        raise SystemExit(f"{advice.name} has no 'response' object")
    links: dict[str, str] = {}
    for content_id, entry in response.items():
        if not isinstance(entry, dict) or "countryCode" not in entry:
            continue
        code = entry["countryCode"]
        if code in links:
            raise SystemExit(f"two Foreign Office pages claim {code} in {advice.name}")
        links[code] = rules.AA_OPENDATA["link"].format(content_id=content_id)
    if len(links) < 150:
        raise SystemExit(
            f"only {len(links)} countries in {advice.name}; the feed's shape has "
            f"probably changed"
        )
    return links


def fail(problems: list[str], hint: str) -> None:
    """Report every problem found in one pass, then stop."""
    if not problems:
        return
    for p in problems:
        print(f"  FAIL {p}")
    raise SystemExit(f"{len(problems)} problem(s) — {hint}")


def build(passport: str, ds: Dataset, advice: Path | None, usage: Usage) -> str:
    """The finished asset as JSON text; writes nothing."""
    places = json.loads(REGISTRY.read_text(encoding="utf-8"))
    codes = [p["code"] for p in places]
    names = {p["code"]: p["name"] for p in places}
    print(f"registry: {len(codes)} places from {REGISTRY.name}")

    doc = ds.load(passport)
    row = load_row(doc, set(codes), usage)
    print(f"dataset: {len(row)} places answered for the {passport} passport "
          f"(revision {doc['source_revision']})")
    answered = sum(1 for r in row.values() if r.category != "HOME")
    if answered < rules.MIN_DATASET_ROWS:
        raise TooThin(f"{passport}: only {answered} dataset answers (minimum "
                      f"{rules.MIN_DATASET_ROWS}); not shipping a passport that "
                      f"is mostly blank")
    # What the generic tier may borrow from: never a `special` row, whose category
    # is a stand-in and says nothing about the neighbouring territory.
    reference_row = {c: (r.category, r.days) for c, r in row.items() if not r.special}

    curated = passport in rules.PASSPORTS
    corrections = rules.CORRECTIONS.get(passport, {})
    overrides = rules.overrides_for(passport, reference_row)
    if curated:
        print(f"programs: {len(overrides)} places resolved for {passport} from "
              f"{sum(1 for p in rules.PROGRAMS.values() if passport in p['members'])} "
              f"programs (curated)")
    else:
        # A fallback, so it yields to a dataset row or a correction (see rules.py,
        # guiding rule 1). The curated tier gets no such leniency: a program entry
        # that shadows a row fails the hygiene check below.
        overrides = {c: e for c, e in overrides.items()
                     if c not in row and c not in corrections}
        for code, entry in rules.GAP_FILLS.get(passport, {}).items():
            if code not in row:
                overrides[code] = entry
                usage.gap_fills.add((passport, code))
        usage.generic.update(c for c in overrides
                             if (passport, c) not in usage.gap_fills)
        print(f"generic tier: {len(overrides)} places the dataset leaves open, "
              f"resolved for {passport}")

    # The Foreign Office link-out is DE-only: it comes from the Auswärtiges
    # Amt's own open data, and there is no equivalent feed wired up for any
    # other passport (the plan is only to link out for DE).
    if passport == "DE":
        advice_links = load_advice_links(advice)
        print(f"foreign office: {len(advice_links)} country pages from {advice.name}")
    else:
        advice_links = {}

    problems: list[str] = []

    # ---------------- rule hygiene, before any resolving ----------------
    # Checked first and reported together, so a stale rules.py surfaces every
    # problem in one run rather than one per edit.
    for table_name, table in (("OVERRIDES", overrides),
                              ("CORRECTIONS", corrections)):
        for code, entry in table.items():
            if code not in names:
                problems.append(
                    f"{table_name}[{code}] is not a place in the registry — "
                    f"either a typo or a place that has since been merged away"
                )
            if len(entry) != 5:
                problems.append(f"{table_name}[{code}] should be a 5-tuple, got {entry!r}")
                continue
            category, days, confidence, src, note = entry
            if category not in rules.CATEGORIES:
                problems.append(f"{table_name}[{code}] has unknown category {category!r}")
            if confidence not in rules.CONFIDENCE:
                problems.append(f"{table_name}[{code}] has unknown confidence {confidence!r}")
            if src not in rules.SOURCES:
                problems.append(f"{table_name}[{code}] cites unknown source {src!r}")
            if days is not None and days <= 0:
                problems.append(f"{table_name}[{code}] has nonsensical days {days!r}")
            if not note or not note.strip():
                problems.append(f"{table_name}[{code}] has no note; the app shows it")

    annotations = rules.ANNOTATIONS.get(passport, {})
    for code, (confidence, src, note) in annotations.items():
        if code not in names:
            problems.append(f"ANNOTATIONS[{passport}][{code}] is not a place")
        elif code not in row or row[code].special or code in corrections:
            problems.append(
                f"ANNOTATIONS[{passport}][{code}] needs a plain dataset row to "
                f"annotate (not missing, not special, not corrected)")
        if confidence not in rules.CONFIDENCE or src not in rules.SOURCES or not note:
            problems.append(f"ANNOTATIONS[{passport}][{code}] is malformed")

    # An override that shadows a dataset row is a correction and must live in the
    # other table, where the build can notice once upstream agrees.
    for code in overrides:
        if code in row:
            problems.append(
                f"OVERRIDES[{code}] shadows a dataset row ({row[code].status!r}). "
                f"Delete it if upstream is as good, else move it to CORRECTIONS "
                f"so it expires when upstream fixes it."
            )
    # And the reverse: a correction with nothing to correct.
    for code, entry in corrections.items():
        if code not in row:
            problems.append(
                f"CORRECTIONS[{passport}][{code}] has no dataset row to correct — "
                f"it belongs in OVERRIDES."
            )
        elif (row[code].category, row[code].days) == (entry[0], entry[1]):
            problems.append(
                f"CORRECTIONS[{passport}][{code}] now agrees with the dataset "
                f"({row[code].category}). Delete it; upstream has caught up."
            )

    # ---------------- the link-out table (DE only) ----------------
    # Every place must reach a Foreign Office page, whether its own or its
    # parent's, or be listed as deliberately having none. Silence here would mean
    # a destination screen with nowhere to send someone who needs the real answer.
    if passport == "DE":
        for code, target in rules.AA_PAGE.items():
            if code not in names:
                problems.append(f"AA_PAGE[{code}] is not a place in the registry")
            if target not in advice_links:
                problems.append(
                    f"AA_PAGE[{code}] points at {target}, which has no Foreign "
                    f"Office page in the feed"
                )
            if code in advice_links:
                problems.append(
                    f"AA_PAGE[{code}] is redundant — {code} has its own Foreign "
                    f"Office page now. Delete the entry."
                )
        for code in rules.AA_NONE:
            if code not in names:
                problems.append(f"AA_NONE has {code}, which is not a place")
            elif code in advice_links:
                problems.append(
                    f"AA_NONE has {code}, but the Foreign Office does have a page "
                    f"for it now. Delete the entry."
                )
        for code in codes:
            if code not in advice_links and code not in rules.AA_PAGE and code not in rules.AA_NONE:
                problems.append(
                    f"{code} {names[code]} has no Foreign Office link. Add it to "
                    f"AA_PAGE (whose page covers it) or AA_NONE in rules.py."
                )

    # ---------------- no sovereign state silently lost ----------------
    # Every sovereign place must come out of the dataset for every passport except
    # its own country and the named gaps. This is what catches a new way upstream
    # files a whole state (the "and territories" areas were missed once).
    for p in places:
        c = p["code"]
        if p["sovereign"] != p["name"] or c == passport or c in row:
            continue
        usage.sov_gaps.add((passport, c))
        if (c not in rules.SOVEREIGN_GAP_DESTINATIONS
                and (passport, c) not in rules.SOVEREIGN_GAP_PAIRS):
            problems.append(
                f"{passport}: sovereign place {c} {names[c]} has no usable dataset "
                f"row. Handle its area form in load_row/rules, or list it in "
                f"SOVEREIGN_GAP_PAIRS with the reason.")

    # Stop here rather than collecting further: the resolve step below trusts
    # what the tables say, and there is no sense reporting that Greenland came
    # out wrong when the reason is a typo already listed above.
    fail(problems, "fix the tables in tools/visa/rules.py")

    # ---------------- resolve ----------------
    entries = []
    from_dataset = 0
    from_override = 0
    corrected = 0
    specials = 0
    unresolved = []

    for code in codes:
        entry_override = corrections.get(code) or overrides.get(code)
        if code in row and code not in corrections:
            r = row[code]
            entry = {"code": code, "category": r.category, "source": "dataset"}
            if r.days is not None:
                entry["days"] = r.days
            if r.special:
                entry["confidence"] = "low"
                entry["note"] = r.note
                entry["sourceUrl"] = doc["source_url"]
                specials += 1
            elif code in annotations:
                confidence, src, note = annotations[code]
                entry["confidence"] = confidence
                entry["note"] = note
                entry["sourceUrl"] = rules.SOURCES[src][0]
            from_dataset += 1
        elif entry_override is not None:
            category, days, confidence, src, note = entry_override
            entry = {
                "code": code,
                "category": category,
                "source": "correction" if code in corrections else "curated",
                "confidence": confidence,
                "note": note,
                "sourceUrl": rules.SOURCES[src][0],
            }
            if days is not None:
                entry["days"] = days
            if code in corrections:
                said = row[code].status.replace("_", " ")
                if row[code].days is not None:
                    said += f", {row[code].days} days"
                entry["datasetSaid"] = said
                corrected += 1
            else:
                from_override += 1
        else:
            unresolved.append(code)
            continue

        # The link-out. `officialFor` names whose page it is, so the app can say
        # "Foreign Office: Denmark" on Greenland rather than implying the page is
        # about Greenland.
        official = advice_links.get(code) or advice_links.get(rules.AA_PAGE.get(code, ""))
        if official is not None:
            entry["officialUrl"] = official
            if code not in advice_links:
                entry["officialFor"] = rules.AA_PAGE[code]

        entries.append(entry)

    # Unresolved is only fatal for the curated tier. The generic tier's whole
    # point is that most of the registry's places have no applicable rule for most
    # passports — that is the honest answer, not a bug, so it is only
    # reported below for visibility.
    if unresolved and curated:
        for code in unresolved:
            problems.append(
                f"{code} {names[code]} has no dataset row and no override. "
                f"Add it to OVERRIDES in rules.py."
            )
    elif unresolved:
        print(f"generic tier: no answer for {len(unresolved)} places — "
              f"{' '.join(sorted(unresolved))}")

    # ---------------- the promise to users ----------------
    resolved = {e["code"]: e for e in entries}
    for code, (expected, why) in rules.EXPECTED.get(passport, {}).items():
        if expected is None:
            # This assertion is about an intended gap, not an answer.
            if code in resolved:
                problems.append(
                    f"{code} {names[code]} resolved to "
                    f"{resolved[code]['category']}, but is expected to have "
                    f"no generic answer ({why})"
                )
            continue
        if code not in resolved:
            # Either the code is not in the registry at all, or it is one of the
            # unresolved above — which is already reported, so say which.
            problems.append(
                f"EXPECTED names {code}, which "
                + ("did not resolve" if code in names else "is not a place")
            )
            continue
        got = resolved[code]["category"]
        if got != expected:
            problems.append(
                f"{code} {names[code]} came out {got}, expected {expected} ({why})"
            )

    fail(problems, "see tools/visa/rules.py")

    # ---------------- write ----------------
    # Sorted by code so a diff of this file is reviewable; the app sorts by
    # whatever the screen needs.
    entries.sort(key=lambda e: e["code"])
    asset = {
        "passport": passport,
        # The day the dataset was generated: upstream re-reads Wikipedia daily, so
        # this is "checked on", while sourceRevision pins which Wikipedia revision
        # of this passport's article the answers come from.
        "asOf": ds.generated_at[:10],
        "dataset": {
            "name": rules.DATASET["name"],
            "url": rules.DATASET["url"],
            "licence": rules.DATASET["licence"],
            "licenceUrl": rules.DATASET["licence_url"],
            "sourceUrl": doc["source_url"],
            "sourceRevision": doc["source_revision"],
        },
        "categories": rules.CATEGORIES,
        # The second link-out. One hub rather than per-place,
        # because the IATA Travel Centre is a form with no deep link.
        "iataUrl": rules.IATA_TRAVEL_CENTRE,
        "requirements": entries,
    }
    # Returned, not written: the caller writes every asset only after every
    # passport and every cross-passport check has passed.
    text = json.dumps(asset, separators=(",", ":"), ensure_ascii=False)

    # ---------------- report ----------------
    by_category = {c: 0 for c in rules.CATEGORIES}
    for e in entries:
        by_category[e["category"]] += 1
    low = [e["code"] for e in entries if e.get("confidence") == "low"]

    print(f"resolved {len(entries)} places: {from_dataset} from the dataset "
          f"({specials} special, shown as CHECK at low confidence), "
          f"{from_override} hand-curated, {corrected} corrected")
    for category in rules.CATEGORIES:
        n = by_category[category]
        if n:
            print(f"  {category:<16} {n:>3}")
    visa_free = by_category["VISA_FREE"] + by_category["HOME"]
    print(f"visa-free to {visa_free} of {len(entries)} places "
          f"({visa_free + by_category['ETA'] + by_category['VISA_ON_ARRIVAL']} "
          f"with no advance embassy visit)")
    print(f"low confidence, needs a human: {len(low)} — {' '.join(sorted(low))}")
    own = sum(1 for e in entries if "officialUrl" in e and "officialFor" not in e)
    parent = sum(1 for e in entries if "officialFor" in e)
    print(f"foreign office links: {own} own page, {parent} via the parent, "
          f"{len(entries) - own - parent} none")
    print(f"{asset_path(passport).relative_to(REPO)}  "
          f"{len(text.encode('utf-8')) / 1e3:.0f} kB")
    print(f"data as of {asset['asOf']}")
    print("all visa assertions passed")
    return text


def check_rules_live(usage: Usage) -> list[str]:
    """After a full build: every fallback rule fired for at least one passport.

    The per-passport shadow checks cannot cover the generic tier, whose entries are
    skipped wherever a passport has a row; this is the same guarantee over the
    whole run. A rule nobody needs any more is deleted, not kept as decoration.
    """
    problems = []
    g = rules.GENERIC_RULE
    defined = set(g["eu_membership"]["places"]) | set(g["uk_eta"]["places"])
    defined |= set(g["universal"]) | set(g["uninhabited"])
    for group in g["reference"]:
        defined |= set(group["places"])
    for code in sorted(defined - usage.generic):
        problems.append(f"GENERIC_RULE place {code} applied to no passport — every "
                        f"passport has a dataset row; delete the rule")
    for code in sorted(set(rules.IGNORED_DESTINATIONS) - usage.ignored):
        problems.append(f"IGNORED_DESTINATIONS[{code}] never occurs in the dataset; "
                        f"delete it")
    wanted = {"alternative"} | {"esta:" + c for c in rules.ESTA_DESTINATIONS}
    for rule in sorted(wanted - usage.eta_rules):
        problems.append(f"ETA rule {rule!r} (rules.ETA_RULES) matched no record; "
                        f"upstream has caught up, delete it")
    wanted_fills = {(p, c) for p, fills in rules.GAP_FILLS.items() for c in fills}
    for pair in sorted(wanted_fills - usage.gap_fills):
        problems.append(f"GAP_FILLS{pair} was never needed; upstream has the row now")
    for pair in sorted(set(rules.SOVEREIGN_GAP_PAIRS) - usage.sov_gaps):
        problems.append(f"SOVEREIGN_GAP_PAIRS{pair} is not a gap any more; delete it")
    gap_places = {c for _, c in usage.sov_gaps}
    for code in sorted(set(rules.SOVEREIGN_GAP_DESTINATIONS) - gap_places):
        problems.append(f"SOVEREIGN_GAP_DESTINATIONS[{code}] is answered for every "
                        f"passport now; delete it")
    for code in sorted(set(rules.AREA_IS_WHOLE_PLACE) - usage.area):
        problems.append(f"AREA_IS_WHOLE_PLACE[{code}] never fired; delete it")
    return problems


def check_esta(usage: Usage) -> list[str]:
    return [f"ESTA note is neither a known requirement nor a known exemption "
            f"(rules.ESTA_REQUIRED / ESTA_NEGATIONS): {u}"
            for u in usage.esta_unclassified]


def asset_path(passport: str) -> Path:
    # DE keeps the historical bare filename (visa.json).
    return ASSETS / ("visa.json" if passport == "DE" else f"visa_{passport.lower()}.json")


def write_gap_allowlist() -> None:
    """The sovereign-coverage exceptions, for VisaAssetCoverageTest to read.

    Generated so the host test and rules.py cannot drift apart: the test used to
    carry its own copy of the list.
    """
    lines = ["# Sovereign places a shipped passport may lack a row for. Written by",
             "# tools/visa/build_visa.py from rules.SOVEREIGN_GAP_*; read by VisaAssetCoverageTest."]
    lines += [f"destination {c}" for c in sorted(rules.SOVEREIGN_GAP_DESTINATIONS)]
    lines += [f"pair {p} {c}" for p, c in sorted(rules.SOVEREIGN_GAP_PAIRS)]
    GAP_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_revisions(ds: Dataset, built: list[str], thin: list[str]) -> None:
    lines = [
        "# Which publication of the Visa-requirements dataset the committed visa",
        "# assets were built from. Written by tools/visa/build_visa.py on a full build.",
        f"# index.json generated_at: {ds.generated_at}",
        f"# index.json schema_version: {ds.index.get('schema_version')}",
        "# passport  source_revision (Wikipedia revision of its article)",
    ]
    if thin:
        lines.insert(4, f"# not shipped (fewer than {rules.MIN_DATASET_ROWS} answers): "
                        + " ".join(thin))
    for code in built:
        lines.append(f"{code} {ds.index['passports'][code]['source_revision']}")
    REVISIONS_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--passport", default=None,
                    help="which passport to build (default: every passport "
                         "in the dataset)")
    ap.add_argument("--source", type=Path, default=None,
                    help="directory with the dataset's index.json and passports/ "
                         "(a checkout's data/ or a previous download in "
                         "tools/visa/cache/visa-requirements); default: download "
                         "the latest publication")
    ap.add_argument("--advice", type=Path, default=DEFAULT_ADVICE,
                    help="the Auswärtiges Amt travelwarning JSON (DE only)")
    a = ap.parse_args()
    # The Foreign Office feed is only needed to build DE; a build that never
    # touches DE should not fail over a file it never reads.
    needs_advice = a.passport is None or a.passport == "DE"
    if needs_advice and not a.advice.exists():
        raise SystemExit(
            f"missing {a.advice}\ndownload with:\n"
            f"  mkdir -p {CACHE.relative_to(REPO)}\n"
            f"  curl -sL -o {DEFAULT_ADVICE.relative_to(REPO)} \\\n"
            f"    {rules.AA_OPENDATA['url']}\n"
        )
    if a.source is None:
        if a.passport is not None:
            # The download replaces tools/visa/cache wholesale; doing that for one
            # passport would swap out the publication the committed assets were
            # built from and then refuse the build. Point at it explicitly.
            raise SystemExit(
                "--passport needs --source: it re-runs the rules against a dataset "
                "you already have and never downloads. Use\n"
                f"  --source {DEFAULT_SOURCE.relative_to(REPO)}\n"
                "or omit --passport for a full build.")
        fetch(DEFAULT_SOURCE)
        a.source = DEFAULT_SOURCE
    ds = Dataset.open(a.source)
    print(f"dataset generated_at {ds.generated_at}, {len(ds.passports())} passports")
    usage = Usage()

    if a.passport is not None:
        if a.passport not in ds.index["passports"]:
            raise SystemExit(f"no passport {a.passport!r} in the dataset")
        # A single asset must not move ahead of the rest: it would carry a newer
        # asOf/sourceRevision than its 199 siblings and docs/visa-source-revisions.txt.
        # So this is only for re-running the rules against the publication the asset
        # was already built from; a newer publication needs the full build.
        out = asset_path(a.passport)
        if not out.exists():
            raise SystemExit(f"{out.name} does not exist; build every passport "
                             f"(omit --passport) to add one")
        have = json.loads(out.read_text(encoding="utf-8"))
        want = (ds.generated_at[:10], ds.index["passports"][a.passport]["source_revision"])
        got = (have["asOf"], str(have["dataset"].get("sourceRevision")))
        if got != (want[0], str(want[1])):
            raise SystemExit(
                f"{out.name} was built from {got} but this dataset is {want}. A "
                f"single-passport build never bumps asOf/sourceRevision; run the "
                f"full build (omit --passport) instead.")
        try:
            text = build(a.passport, ds, a.advice if a.passport == "DE" else None, usage)
        except TooThin as e:
            raise SystemExit(str(e))
        fail(check_esta(usage), "see tools/visa/rules.py")
        out.write_text(text, encoding="utf-8")
        return 0

    codes = ds.passports()
    places = {p["code"] for p in json.loads(REGISTRY.read_text(encoding="utf-8"))}
    unknown = [c for c in codes if c not in places]
    if unknown:
        raise SystemExit(f"passports that are not places in the registry: {unknown}")
    print(f"building {len(codes)} passports")
    # All-or-nothing: every asset and every check is computed in memory first;
    # the tree is only touched once nothing can fail any more.
    built = []
    thin = []
    texts: dict[str, str] = {}
    for code in codes:
        try:
            texts[code] = build(code, ds, a.advice if code == "DE" else None, usage)
            built.append(code)
        except TooThin as e:
            thin.append(code)
            print(f"SKIPPED {e}")
        print()
    problems = check_rules_live(usage)
    shipped = {("DE" if p.name == "visa.json" else p.stem[5:].upper())
               for p in ASSETS.glob("visa*.json")}
    # Too-thin passports are deliberately not built; their stale asset is deleted
    # below, so only a shipped asset that is neither built nor thin is a problem.
    for stale in sorted(shipped - set(built) - set(thin)):
        problems.append(f"asset for {stale} is shipped but the dataset no longer "
                        f"has that passport; delete it")
    problems += check_esta(usage)
    fail(problems, "see tools/visa/rules.py")
    for code, text in texts.items():
        asset_path(code).write_text(text, encoding="utf-8")
    for code in thin:
        stale = asset_path(code)
        if stale.exists():
            stale.unlink()
            print(f"deleted {stale.name} (too thin to ship)")
    write_revisions(ds, built, thin)
    write_gap_allowlist()
    print(f"sovereign (passport, place) pairs with no dataset row: "
          f"{len(usage.sov_gaps)}")
    print(f"{REVISIONS_FILE.relative_to(REPO)} written; all rules live")
    return 0


if __name__ == "__main__":
    sys.exit(main())
