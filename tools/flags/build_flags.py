#!/usr/bin/env python3
"""Rebuild app/src/main/assets/flags/<code>.webp from lipis/flag-icons (MIT).

Usage: build_flags.py <work-dir> <out-dir> <places.json> <LICENSE-out>
  <work-dir> must be a new, empty directory OUTSIDE the repo; the tarball is
  downloaded and extracted there and treated as untrusted data (only SVG bytes
  are read, never executed). Needs `pip install resvg-py` and `cwebp` on PATH.
  Run with: python3 -I tools/flags/build_flags.py ...
"""
import hashlib, json, os, shutil, subprocess, sys, tarfile, urllib.request

TAG = "v7.6.0"
URL = f"https://api.github.com/repos/lipis/flag-icons/tarball/{TAG}"
WIDTH = 320


def main(work, out, places, lic_out):
    import resvg_py
    if os.path.exists(work) and os.listdir(work):
        sys.exit(f"{work} must be empty")
    os.makedirs(work, exist_ok=True)
    tgz = os.path.join(work, "fi.tgz")
    urllib.request.urlretrieve(URL, tgz)
    print("tarball sha256", hashlib.sha256(open(tgz, "rb").read()).hexdigest())
    svgs = {}
    with tarfile.open(tgz) as t:
        for m in t.getmembers():
            if not m.isfile():
                continue
            parts = m.name.split("/", 1)[-1]
            if parts.startswith("flags/4x3/") and parts.endswith(".svg"):
                svgs[os.path.basename(parts)[:-4]] = t.extractfile(m).read().decode()
            elif parts == "LICENSE":
                open(lic_out, "wb").write(t.extractfile(m).read())
    codes = sorted(p["code"].lower() for p in json.load(open(places)) if p.get("code"))
    os.makedirs(out, exist_ok=True)
    missing = []
    for c in codes:
        if c not in svgs:
            missing.append(c)
            continue
        png = os.path.join(work, c + ".png")
        open(png, "wb").write(bytes(resvg_py.svg_to_bytes(svg_string=svgs[c], width=WIDTH)))
        subprocess.run(["cwebp", "-quiet", "-q", "80", png, "-o", os.path.join(out, c + ".webp")], check=True)
    print(f"{len(codes) - len(missing)} flags written; no flag for: {missing}")


if __name__ == "__main__":
    main(*sys.argv[1:5])
