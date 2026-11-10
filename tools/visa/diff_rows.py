#!/usr/bin/env python3
"""List the visa rows that differ between a git revision and the working tree.

    python3 -I tools/visa/diff_rows.py [REV] [ASSETS_DIR]

REV defaults to HEAD, ASSETS_DIR to app/src/main/assets. For every passport file
that changed, prints one line per destination whose category or days differ
(also rows added or removed), then a count. Output is capped at MAX_LINES whole
lines. Used by the weekly visa-refresh workflow for its pull request body;
read-only.
"""

import glob
import json
import os
import subprocess
import sys

MAX_LINES = 200


def rows(doc):
    return {r["code"]: (r.get("category"), r.get("days")) for r in doc["requirements"]}


def show(cat_days):
    if cat_days is None:
        return "(none)"
    cat, days = cat_days
    return cat if days is None else f"{cat} {days}d"


def diff_rows(old, new):
    """(code, old, new) for every destination whose (category, days) differ."""
    return [(c, old.get(c), new.get(c)) for c in sorted(set(old) | set(new))
            if old.get(c) != new.get(c)]


def old_rows(rev, rel):
    """Rows of `rel` at `rev`; {} only when the path genuinely does not exist there."""
    spec = f"{rev}:{rel}"
    if subprocess.run(["git", "cat-file", "-e", spec], capture_output=True).returncode != 0:
        return {}
    shown = subprocess.run(["git", "show", spec], capture_output=True, text=True)
    if shown.returncode != 0:
        sys.exit(f"git show {spec} failed: {shown.stderr.strip()}")
    return rows(json.loads(shown.stdout))


def render(sections, limit=MAX_LINES):
    """sections: [(passport, [(code, old, new)])] -> output lines, capped on whole lines."""
    lines = []
    total = 0
    for passport, diffs in sections:
        lines.append("")
        lines.append(f"{passport}:")
        for code, o, n in diffs:
            lines.append(f"  {code}: {show(o)} -> {show(n)}")
        total += len(diffs)
    if total == 0:
        return ["No category or stay changes; only other fields differ."]
    if len(lines) > limit:
        kept = lines[:limit]
        shown_rows = sum(1 for l in kept if l.startswith("  "))
        lines = kept + ["", f"{total - shown_rows} more rows not shown"]
    return lines + ["", f"{total} changed row(s)."]


def main(argv):
    rev = argv[1] if len(argv) > 1 else "HEAD"
    assets = argv[2] if len(argv) > 2 else "app/src/main/assets"
    sections = []
    for path in sorted(glob.glob(os.path.join(assets, "visa*.json"))):
        with open(path, encoding="utf-8") as f:
            new = json.load(f)
        diffs = diff_rows(old_rows(rev, os.path.relpath(path)), rows(new))
        if diffs:
            sections.append((new["passport"], diffs))
    print("\n".join(render(sections)))


if __name__ == "__main__":
    main(sys.argv)
