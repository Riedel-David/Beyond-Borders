#!/usr/bin/env python3
"""Build BeyondBorders's bundled visa asset from the Passport Index matrix.

    python3 tools/visa/build_visa.py [--passport DE|GB] [--source PATH]

Output (committed, so the app builds without network access):
    app/src/main/assets/visa.json        --passport DE (the shipped default)
    app/src/main/assets/visa_gb.json     --passport GB

The curation rules live in rules.py. Read that first; it explains why 53 of our
252 places have no row in the dataset and what we say about them instead.

The build refuses to write unless every place resolves, every rule is live, and
every entry in rules.EXPECTED comes out as promised. A visa answer that is
quietly missing is worse than a build that fails.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import rules  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).parent / "cache"
DEFAULT_SOURCE = CACHE / rules.DATASET["file"]
DEFAULT_ADVICE = CACHE / rules.AA_OPENDATA["file"]
ASSETS = REPO / "app" / "src" / "main" / "assets"
REGISTRY = ASSETS / "places.json"


def load_row(source: Path, passport: str) -> dict[str, str]:
    """The one row of the matrix we resolve, as destination -> raw requirement."""
    with source.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        expected = {"Passport", "Destination", "Requirement"}
        if set(reader.fieldnames or []) != expected:
            raise SystemExit(
                f"unexpected columns {reader.fieldnames} in {source.name}; "
                f"expected {sorted(expected)}"
            )
        row = {
            r["Destination"]: r["Requirement"]
            for r in reader
            if r["Passport"] == passport
        }
    if not row:
        raise SystemExit(f"no rows for passport {passport!r} in {source.name}")
    return row


def load_passports(source: Path) -> list[str]:
    """Every passport code with at least one row in the matrix.

    This, not rules.PASSPORTS, is what a full build iterates — PASSPORTS is
    only the curated roster (today, just DE).
    """
    with source.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        codes = sorted({r["Passport"] for r in reader})
    if len(codes) < 150:
        raise SystemExit(
            f"only {len(codes)} passports in {source.name}; the dataset's "
            f"shape has probably changed"
        )
    return codes


def classify(raw: str) -> tuple[str, int | None]:
    """Turn one Requirement cell into (category, days).

    A bare integer means visa-free for that many days, which is the one case the
    dataset encodes by type rather than by name.
    """
    raw = raw.strip()
    if raw.lstrip("-").isdigit():
        n = int(raw)
        if n == -1:
            return "HOME", None
        if n <= 0:
            raise SystemExit(f"nonsensical day count {raw!r}")
        return "VISA_FREE", n
    category = rules.REQUIREMENT_CATEGORY.get(raw)
    if category is None:
        raise SystemExit(
            f"unknown requirement {raw!r}. The dataset's vocabulary has grown; "
            f"add it to REQUIREMENT_CATEGORY in rules.py."
        )
    return category, None


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


def build(passport: str, source: Path, advice: Path | None) -> int:
    places = json.loads(REGISTRY.read_text(encoding="utf-8"))
    codes = [p["code"] for p in places]
    names = {p["code"]: p["name"] for p in places}
    print(f"registry: {len(codes)} places from {REGISTRY.name}")

    row = load_row(source, passport)
    print(f"dataset: {len(row)} destinations for the {passport} passport")
    classified_row = {code: classify(raw) for code, raw in row.items()}

    curated = passport in rules.PASSPORTS
    overrides = rules.overrides_for(passport, classified_row)
    if curated:
        print(f"programs: {len(overrides)} places resolved for {passport} from "
              f"{sum(1 for p in rules.PROGRAMS.values() if passport in p['members'])} "
              f"programs (curated)")
    else:
        print(f"generic tier: {len(overrides)} of the 53 non-dataset places "
              f"resolved for {passport}")

    # The Foreign Office link-out is DE-only: it comes from the Auswärtiges
    # Amt's own open data, and there is no equivalent feed wired up for any
    # other passport (see PLAN.md §6 — the plan is only to link out for DE).
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
                              ("CORRECTIONS", rules.CORRECTIONS)):
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

    # An override that shadows a dataset row is a correction and must live in the
    # other table, where the build can notice once upstream agrees.
    for code in overrides:
        if code in row:
            problems.append(
                f"OVERRIDES[{code}] shadows a dataset row ({row[code]!r}). Move it "
                f"to CORRECTIONS so it expires when upstream fixes it."
            )
    # And the reverse: a correction with nothing to correct.
    for code, entry in rules.CORRECTIONS.items():
        if code not in row:
            problems.append(
                f"CORRECTIONS[{code}] has no dataset row to correct — it belongs "
                f"in OVERRIDES."
            )
        else:
            dataset_category, dataset_days = classify(row[code])
            if (dataset_category, dataset_days) == (entry[0], entry[1]):
                problems.append(
                    f"CORRECTIONS[{code}] now agrees with the dataset "
                    f"({dataset_category}). Delete it; upstream has caught up."
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

    # Stop here rather than collecting further: the resolve step below trusts
    # what the tables say, and there is no sense reporting that Greenland came
    # out wrong when the reason is a typo already listed above.
    fail(problems, "fix the tables in tools/visa/rules.py")

    # ---------------- resolve ----------------
    entries = []
    from_dataset = 0
    from_override = 0
    corrected = 0
    unresolved = []

    for code in codes:
        entry_override = rules.CORRECTIONS.get(code) or overrides.get(code)
        if code in row and code not in rules.CORRECTIONS:
            category, days = classify(row[code])
            entry = {"code": code, "category": category, "source": "dataset"}
            if days is not None:
                entry["days"] = days
            from_dataset += 1
        elif entry_override is not None:
            category, days, confidence, src, note = entry_override
            entry = {
                "code": code,
                "category": category,
                "source": "correction" if code in rules.CORRECTIONS else "curated",
                "confidence": confidence,
                "note": note,
                "sourceUrl": rules.SOURCES[src][0],
            }
            if days is not None:
                entry["days"] = days
            if code in rules.CORRECTIONS:
                entry["datasetSaid"] = row[code]
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
    # point is that most of the 53 places have no applicable rule for most
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

    # ---------------- the promise in PLAN.md ----------------
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

    fail(problems, "see tools/visa/rules.py and PLAN.md phase 2")

    # ---------------- write ----------------
    # Sorted by code so a diff of this file is reviewable; the app sorts by
    # whatever the screen needs.
    entries.sort(key=lambda e: e["code"])
    asset = {
        "passport": passport,
        "asOf": rules.DATASET["as_of"],
        "dataset": {
            "name": rules.DATASET["name"],
            "url": rules.DATASET["url"],
            "licence": rules.DATASET["licence"],
        },
        "categories": rules.CATEGORIES,
        # The second link-out PLAN.md asks for. One hub rather than per-place,
        # because the IATA Travel Centre is a form with no deep link.
        "iataUrl": rules.IATA_TRAVEL_CENTRE,
        "requirements": entries,
    }
    # DE keeps the historical bare filename — it is still the sole passport the
    # app loads until the Kotlin side of the switcher picks a nationality.
    out = ASSETS / ("visa.json" if passport == "DE" else f"visa_{passport.lower()}.json")
    out.write_text(
        json.dumps(asset, separators=(",", ":"), ensure_ascii=False),
        encoding="utf-8",
    )

    # ---------------- report ----------------
    by_category = {c: 0 for c in rules.CATEGORIES}
    for e in entries:
        by_category[e["category"]] += 1
    low = [e["code"] for e in entries if e.get("confidence") == "low"]

    print(f"resolved {len(entries)} places: {from_dataset} from the dataset, "
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
    print(f"{out.relative_to(REPO)}  {out.stat().st_size / 1e3:.0f} kB")
    print(f"data as of {rules.DATASET['as_of']}")
    print("all visa assertions passed")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--passport", default=None,
                    help="which passport to build (default: every passport "
                         "with a row in the dataset)")
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE,
                    help="the Passport Index tidy CSV")
    ap.add_argument("--advice", type=Path, default=DEFAULT_ADVICE,
                    help="the Auswärtiges Amt travelwarning JSON (DE only)")
    a = ap.parse_args()
    # The Foreign Office feed is only needed to build DE; a build that never
    # touches DE should not fail over a file it never reads.
    needs_advice = a.passport is None or a.passport == "DE"
    required = [a.source, a.advice] if needs_advice else [a.source]
    missing = [p for p in required if not p.exists()]
    if missing:
        raise SystemExit(
            "missing " + ", ".join(str(p) for p in missing) + "\n"
            "download with:\n"
            f"  mkdir -p {CACHE.relative_to(REPO)}\n"
            f"  curl -sL -o {DEFAULT_SOURCE.relative_to(REPO)} \\\n"
            f"    {rules.DATASET['url'].replace('github.com', 'raw.githubusercontent.com')}"
            f"/main/{rules.DATASET['file']}\n"
            + (
                f"  curl -sL -o {DEFAULT_ADVICE.relative_to(REPO)} \\\n"
                f"    {rules.AA_OPENDATA['url']}\n"
                if needs_advice else ""
            )
        )
    if a.passport is not None:
        return build(a.passport, a.source, a.advice if a.passport == "DE" else None)

    codes = load_passports(a.source)
    print(f"building {len(codes)} passports from {a.source.name}")
    for code in codes:
        build(code, a.source, a.advice if code == "DE" else None)
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
