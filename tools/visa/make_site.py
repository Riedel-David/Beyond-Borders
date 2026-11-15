"""Assemble the folder that visa-refresh.yml publishes to GitHub Pages, for the app's updater.

Usage: python3 -I tools/visa/make_site.py <assets-dir> <out-dir> [--generated-at ISO]

Copies every visa*.json from the app assets under a uniform name (visa.json, the default
passport's file, is published as visa_<code>.json) and writes manifest.json beside them:

    {"manifestVersion": 1, "dataFormat": 1, "generatedAt": "...",
     "passports": {"DE": {"file": "visa_de.json", "sha256": "...", "bytes": 20123, "asOf": "..."}}}

dataFormat is the shape of one visa file as VisaRegistry.parse reads it. Bump it, here and in
the app, together with any change to that shape: an app that does not know the number ignores
the whole update instead of choking on it. manifestVersion is the shape of this manifest.

Refuses (exit 1, writes nothing) unless the out dir is empty and every file parses, names the
passport its file name says, and has the dataset block, ISO asOf and per-row code/category/source
that VisaRegistry.parse reads, so a broken build can never be published.
"""
import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

MANIFEST_VERSION = 1
DATA_FORMAT = 1
DEFAULT_PASSPORT = "DE"  # the passport whose file is visa.json (VisaRegistry.DEFAULT_PASSPORT)
MIN_PASSPORTS = 150  # build_visa.py ships 198; far fewer means a truncated build


def collect(assets: Path) -> dict:
    out = {}
    for path in sorted(assets.glob("visa*.json")):
        raw = path.read_bytes()
        m = re.fullmatch(r"visa(?:_([a-z]{2}))?\.json", path.name)
        if not m:
            raise ValueError(f"unexpected file name {path.name}")
        expected = m.group(1).upper() if m.group(1) else DEFAULT_PASSPORT
        doc = json.loads(raw)
        if not isinstance(doc, dict):
            raise ValueError(f"{path.name} is not a JSON object")
        if doc.get("passport") != expected:
            raise ValueError(f"{path.name} says passport {doc.get('passport')!r}, expected {expected}")
        if not doc.get("requirements"):
            raise ValueError(f"{path.name} has no requirements")
        # What VisaRegistry.parse reads unconditionally; a file lacking any of it would throw
        # in the app for that passport.
        ds = doc.get("dataset")
        if not isinstance(ds, dict) or not all(isinstance(ds.get(k), str) for k in ("name", "url", "licence")):
            raise ValueError(f"{path.name} has no usable dataset block")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(doc.get("asOf", ""))):
            raise ValueError(f"{path.name} has no ISO asOf date")
        for r in doc["requirements"]:
            if not (isinstance(r, dict) and all(isinstance(r.get(k), str) for k in ("code", "category", "source"))):
                raise ValueError(f"{path.name} has a requirement without code, category and source")
        if expected in out:
            raise ValueError(f"two files for passport {expected}")
        out[expected] = (raw, doc.get("asOf", ""))
    if len(out) < MIN_PASSPORTS:
        raise ValueError(f"only {len(out)} passports, expected at least {MIN_PASSPORTS}")
    return out


def manifest(files: dict, generated_at: str) -> dict:
    return {
        "manifestVersion": MANIFEST_VERSION,
        "dataFormat": DATA_FORMAT,
        "generatedAt": generated_at,
        "passports": {
            code: {
                "file": f"visa_{code.lower()}.json",
                "sha256": hashlib.sha256(raw).hexdigest(),
                "bytes": len(raw),
                "asOf": as_of,
            }
            for code, (raw, as_of) in sorted(files.items())
        },
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("assets", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--generated-at", default=datetime.now(timezone.utc).isoformat(timespec="seconds"))
    args = ap.parse_args()
    if args.out.exists() and any(args.out.iterdir()):
        print(f"refusing to publish: {args.out} is not empty", file=sys.stderr)
        return 1
    try:
        files = collect(args.assets)
    except (ValueError, json.JSONDecodeError) as e:
        print(f"refusing to publish: {e}", file=sys.stderr)
        return 1
    args.out.mkdir(parents=True, exist_ok=True)
    for code, (raw, _) in files.items():
        (args.out / f"visa_{code.lower()}.json").write_bytes(raw)
    (args.out / "manifest.json").write_text(
        json.dumps(manifest(files, args.generated_at), indent=1, sort_keys=True) + "\n")
    print(f"published {len(files)} passports")
    return 0


if __name__ == "__main__":
    sys.exit(main())
