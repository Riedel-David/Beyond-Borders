"""Assemble the GitHub Pages folder for advisory-refresh.yml: the new advisory.json plus the visa
files that are live right now.

Usage: python3 -I tools/advisory/make_site.py <advisory.json> <base-url> <out-dir>

A Pages deployment replaces the whole site, so publishing advisory.json alone would delete
manifest.json and every visa_<code>.json, and the app's visa updater would find nothing. This copies
the live manifest and each file it lists (checked against the manifest's own sha256 and size, so a
half-served or tampered file is never re-published), then adds advisory.json beside them. The other
direction is handled in visa-refresh.yml, which carries the live advisory.json into its own site.

Refuses (exit 1, writes nothing) unless the out dir is empty, advisory.json is a well-formed
dataFormat 1 file, and every file the live manifest lists downloads and verifies.
"""
import hashlib
import json
import re
import sys
import urllib.request
from pathlib import Path

MANIFEST_FILE_RE = re.compile(r"visa_[a-z]{2}\.json")


class SiteError(Exception):
    pass


def fetch(url: str) -> bytes:
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(url, timeout=40) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
    raise SiteError(f"cannot fetch {url}: {last}")


def check_advisory(raw: bytes) -> None:
    try:
        doc = json.loads(raw)
    except ValueError as e:
        raise SiteError(f"advisory.json does not parse: {e}")
    if not isinstance(doc, dict) or doc.get("dataFormat") != 1:
        raise SiteError("advisory.json is not dataFormat 1")
    if not isinstance(doc.get("countries"), dict) or len(doc["countries"]) < 150:
        raise SiteError("advisory.json has too few countries")


def build(advisory: bytes, base: str, get=fetch) -> dict:
    """Returns {file name: bytes} for the whole site."""
    check_advisory(advisory)
    base = base.rstrip("/") + "/"
    manifest_raw = get(base + "manifest.json")
    try:
        manifest = json.loads(manifest_raw)
        passports = manifest["passports"]
    except (ValueError, KeyError, TypeError) as e:
        raise SiteError(f"live manifest.json is unusable: {e!r}")
    if not passports:
        raise SiteError("live manifest.json lists no passports")
    files = {"manifest.json": manifest_raw}
    for code, meta in sorted(passports.items()):
        name = meta.get("file", "")
        if not MANIFEST_FILE_RE.fullmatch(name):
            raise SiteError(f"manifest lists an unexpected file name {name!r}")
        raw = get(base + name)
        if len(raw) != meta.get("bytes") or hashlib.sha256(raw).hexdigest() != meta.get("sha256"):
            raise SiteError(f"{name} does not match the live manifest's size and sha256")
        files[name] = raw
    files["advisory.json"] = advisory
    return files


def main(argv) -> int:
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    adv, base, out = Path(argv[0]), argv[1], Path(argv[2])
    try:
        if out.exists() and any(out.iterdir()):
            raise SiteError(f"{out} is not empty")
        files = build(adv.read_bytes(), base)
    except (SiteError, OSError) as e:
        print(f"site assembly FAILED, nothing written: {e}", file=sys.stderr)
        return 1
    out.mkdir(parents=True, exist_ok=True)
    for name, raw in files.items():
        (out / name).write_bytes(raw)
    print(f"assembled {len(files)} files into {out} ({len(files) - 2} visa files)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
