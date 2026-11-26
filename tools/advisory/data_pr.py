"""What changed between two advisory.json files, for the weekly data pull request.

  data_pr.py changed OLD NEW        prints "true" or "false"
  data_pr.py body OLD NEW [--limit N]   prints the changed countries, one line each

"Changed" means anything but the fields that move on every build: the top-level generatedAt and
each source's fetchedAt (build_advisory.keep_if_unchanged leaves both alone only when nothing else
moved, so a rebuild that found no advisory change still differs from main in those two). A source's
label counts. A missing or unreadable OLD (nothing on main yet) counts as changed; an unreadable NEW
is an error, because the build has already refused to write a bad file.

Standard library only; run with python3 -I.
"""
import argparse
import json
import sys
from pathlib import Path

VOLATILE_SOURCE_KEYS = ("fetchedAt",)
DEFAULT_LIMIT = 40
MAX_FIELD = 80


def load(path):
    """The parsed document, or None when the file is absent or not a JSON object."""
    try:
        doc = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return doc if isinstance(doc, dict) else None


def comparable(doc):
    """The document without generatedAt and the sources' fetchedAt."""
    out = {k: v for k, v in doc.items() if k != "generatedAt"}
    sources = out.get("sources")
    if isinstance(sources, dict):
        out["sources"] = {
            k: ({a: b for a, b in v.items() if a not in VOLATILE_SOURCE_KEYS} if isinstance(v, dict) else v)
            for k, v in sources.items()
        }
    return out


def changed(old, new) -> bool:
    return old is None or comparable(old) != comparable(new)


def short(text) -> str:
    s = str(text).replace("\n", " ").replace("`", "'")
    return s if len(s) <= MAX_FIELD else s[:MAX_FIELD - 3] + "..."


def describe(entry) -> str:
    return f"{entry.get('stance', '?')} {short(entry.get('label', ''))}"


def change_lines(old, new):
    """One line per changed (country, source), sorted by country then source, then source labels."""
    lines = []
    old_c = (old or {}).get("countries") or {}
    new_c = new.get("countries") or {}
    for code in sorted(set(old_c) | set(new_c)):
        o_row, n_row = old_c.get(code) or {}, new_c.get(code) or {}
        for src in sorted(set(o_row) | set(n_row)):
            o, n = o_row.get(src), n_row.get(src)
            if o == n:
                continue
            if o is None:
                lines.append(f"{code} {src}: added: {describe(n)}")
            elif n is None:
                lines.append(f"{code} {src}: removed (was {describe(o)})")
            elif (o.get("stance"), o.get("label")) != (n.get("stance"), n.get("label")):
                lines.append(f"{code} {src}: {describe(o)} -> {describe(n)}")
            else:
                keys = sorted(k for k in set(o) | set(n) if o.get(k) != n.get(k))
                lines.append(f"{code} {src}: same stance and label, changed {', '.join(keys)}")
    old_s = (old or {}).get("sources") or {}
    for src, v in sorted((new.get("sources") or {}).items()):
        o_label = (old_s.get(src) or {}).get("label") if isinstance(old_s.get(src), dict) else None
        n_label = v.get("label") if isinstance(v, dict) else None
        if o_label != n_label:
            lines.append(f"source {src}: label {short(o_label)} -> {short(n_label)}")
    return lines


def body(old, new, limit=DEFAULT_LIMIT) -> str:
    lines = change_lines(old, new)
    shown = lines[:limit]
    out = [f"{len(lines)} change(s), as stance and label (0 = no advisory, 3 = advised against):", ""]
    out += shown
    if len(lines) > limit:
        out.append(f"... and {len(lines) - limit} more")
    return "\n".join(out)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("command", choices=("changed", "body"))
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--limit", type=int, default=DEFAULT_LIMIT)
    args = ap.parse_args(argv)
    new = load(args.new)
    if new is None:
        print(f"{args.new} is missing or not a JSON object", file=sys.stderr)
        return 2
    old = load(args.old)
    if args.command == "changed":
        print("true" if changed(old, new) else "false")
    else:
        print(body(old, new, args.limit))
    return 0


if __name__ == "__main__":
    sys.exit(main())
