#!/usr/bin/env python3
"""Rebuild app/src/main/assets/flags/<code>.webp from hampusborgos/country-flags.

Usage: build_flags.py <work-dir> <out-dir> <places.json>
  <work-dir> must be a new, empty directory OUTSIDE the repo; the tarball is
  downloaded and extracted there and treated as untrusted data (only SVG bytes
  are read, never executed). Needs `pip install resvg-py` and `cwebp` on PATH.
  Run with: python3 -I tools/flags/build_flags.py ...

Every flag is rendered at a fixed height; the width follows the SVG's own aspect
ratio, so Switzerland stays square and Qatar stays long. Alpha is kept (Nepal's
flag is not a rectangle).
"""
import hashlib, json, os, subprocess, sys, tarfile, urllib.request

COMMIT = "c09927e63705529bbf59ca6684cd9b23225dddad"
URL = f"https://api.github.com/repos/hampusborgos/country-flags/tarball/{COMMIT}"
TARBALL_SHA256 = "fc34d6635d21a63579268bb37eb5449c4971667cde308cfcfb44104cd961a285"
HEIGHT = 120


def main(work, out, places):
    import resvg_py
    if os.path.exists(work) and os.listdir(work):
        sys.exit(f"{work} must be empty")
    os.makedirs(work, exist_ok=True)
    tgz = os.path.join(work, "cf.tgz")
    urllib.request.urlretrieve(URL, tgz)
    got = hashlib.sha256(open(tgz, "rb").read()).hexdigest()
    if got != TARBALL_SHA256:
        sys.exit(f"tarball sha256 mismatch: expected {TARBALL_SHA256}, got {got}")
    svgs = {}
    with tarfile.open(tgz) as t:
        for m in t.getmembers():
            if not m.isfile():
                continue
            parts = m.name.split("/", 1)[-1]
            if parts.startswith("svg/") and parts.endswith(".svg"):
                svgs[os.path.basename(parts)[:-4].lower()] = t.extractfile(m).read().decode()
    codes = sorted(p["code"].lower() for p in json.load(open(places)) if p.get("code"))
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        if f.endswith(".webp"):
            os.remove(os.path.join(out, f))
    missing = []
    for c in codes:
        if c not in svgs:
            missing.append(c)
            continue
        png = os.path.join(work, c + ".png")
        open(png, "wb").write(bytes(resvg_py.svg_to_bytes(svg_string=svgs[c], height=HEIGHT)))
        subprocess.run(["cwebp", "-quiet", "-q", "85", "-alpha_q", "100", png, "-o", os.path.join(out, c + ".webp")], check=True)
    print(f"{len(codes) - len(missing)} flags written; no flag for: {missing}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
