#!/usr/bin/env python3
"""List the visa rows that differ between a git revision and the working tree.

    python3 -I tools/visa/diff_rows.py [REV] [ASSETS_DIR]

REV defaults to HEAD, ASSETS_DIR to app/src/main/assets. For every passport file
that changed, prints one line per destination whose category or days differ
(also rows added or removed), then a count. Used by the weekly visa-refresh
workflow for its pull request body; read-only.
"""

import glob
import json
import os
import subprocess
import sys

rev = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
assets = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets"


def rows(doc):
    return {r["code"]: (r.get("category"), r.get("days")) for r in doc["requirements"]}


def show(cat_days):
    if cat_days is None:
        return "(none)"
    cat, days = cat_days
    return cat if days is None else f"{cat} {days}d"


total = 0
for path in sorted(glob.glob(os.path.join(assets, "visa*.json"))):
    rel = os.path.relpath(path)
    new = json.load(open(path, encoding="utf-8"))
    old_raw = subprocess.run(["git", "show", f"{rev}:{rel}"], capture_output=True, text=True)
    old = rows(json.loads(old_raw.stdout)) if old_raw.returncode == 0 else {}
    new_rows = rows(new)
    diffs = [(c, old.get(c), new_rows.get(c)) for c in sorted(set(old) | set(new_rows))
             if old.get(c) != new_rows.get(c)]
    if not diffs:
        continue
    print(f"\n{new['passport']}:")
    for code, o, n in diffs:
        print(f"  {code}: {show(o)} -> {show(n)}")
    total += len(diffs)
print(f"\n{total} changed row(s).")
