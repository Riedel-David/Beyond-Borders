#!/usr/bin/env python3
"""Build AeroGlobe's bundled geo assets from Natural Earth map units.

    python3 tools/geo/build_geo.py [--source PATH] [--eps-scale 1.0]

Outputs (both committed, so the app builds without network access):
    app/src/main/assets/places.geojson  polygons for the map, minimal properties
    app/src/main/assets/places.json     registry: code, name, continent, bbox, centroid

The curation rules live in rules.py. Read that first; it explains why the raw
Natural Earth layer is wrong in both directions and what we do about it.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import rules  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = Path(__file__).parent / "cache" / "ne_10m_admin_0_map_units.geojson"
ASSETS = REPO / "app" / "src" / "main" / "assets"

# Coordinate precision, chosen per feature from its bounding-box diagonal.
# 3 decimals is ~110 m, which is invisible on a Russia-sized polygon but would
# collapse Vatican City (0.006 deg across) into fewer than three distinct
# points. Small features therefore get more decimals; they are so few that the
# extra bytes do not matter.
def decimals_for(diag: float) -> int:
    if diag < 0.5:
        return 5
    if diag < 5.0:
        return 4
    return 3

# A ring smaller than this (in square degrees) is dropped, unless it is the
# largest ring of its place. Keeps single-island nations intact while discarding
# the thousands of unnamed rocks that dominate the raw file's size.
MIN_RING_AREA = 2.0e-4


# --------------------------------------------------------------------------
# geometry helpers
# --------------------------------------------------------------------------
def ring_bbox(ring):
    xs = [p[0] for p in ring]
    ys = [p[1] for p in ring]
    return min(xs), min(ys), max(xs), max(ys)


def ring_area(ring):
    """Absolute shoelace area in square degrees. Only used for comparisons."""
    a = 0.0
    for i in range(len(ring) - 1):
        x1, y1 = ring[i][0], ring[i][1]
        x2, y2 = ring[i + 1][0], ring[i + 1][1]
        a += x1 * y2 - x2 * y1
    return abs(a) / 2.0


def ring_centroid(ring):
    """Area-weighted centroid, falling back to the bbox centre for slivers."""
    a = cx = cy = 0.0
    for i in range(len(ring) - 1):
        x1, y1 = ring[i][0], ring[i][1]
        x2, y2 = ring[i + 1][0], ring[i + 1][1]
        cross = x1 * y2 - x2 * y1
        a += cross
        cx += (x1 + x2) * cross
        cy += (y1 + y2) * cross
    if abs(a) < 1e-12:
        x0, y0, x1, y1 = ring_bbox(ring)
        return (x0 + x1) / 2.0, (y0 + y1) / 2.0
    a *= 0.5
    return cx / (6.0 * a), cy / (6.0 * a)


def simplify(ring, eps):
    """Douglas-Peucker, iterative to avoid recursion limits on huge rings.

    Distances are measured in degree space with longitude scaled by cos(lat) so
    that high-latitude coastlines (Norway, Greenland, Canada) are not
    over-simplified in the x direction.
    """
    n = len(ring)
    if n <= 4:
        return ring
    lat0 = math.radians(sum(p[1] for p in ring) / n)
    kx = max(0.05, math.cos(lat0))

    keep = [False] * n
    keep[0] = keep[n - 1] = True
    stack = [(0, n - 1)]
    while stack:
        lo, hi = stack.pop()
        if hi <= lo + 1:
            continue
        ax, ay = ring[lo][0] * kx, ring[lo][1]
        bx, by = ring[hi][0] * kx, ring[hi][1]
        dx, dy = bx - ax, by - ay
        norm = dx * dx + dy * dy
        best_i, best_d = -1, -1.0
        for i in range(lo + 1, hi):
            px, py = ring[i][0] * kx, ring[i][1]
            if norm <= 0.0:
                d = math.hypot(px - ax, py - ay)
            else:
                t = ((px - ax) * dx + (py - ay) * dy) / norm
                t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
                d = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if d > best_d:
                best_i, best_d = i, d
        if best_d > eps:
            keep[best_i] = True
            stack.append((lo, best_i))
            stack.append((best_i, hi))

    return [ring[i] for i in range(n) if keep[i]]


def quantize(ring, decimals):
    """Round coordinates and drop points that collapse onto their predecessor."""
    out = []
    for x, y in ((round(p[0], decimals), round(p[1], decimals)) for p in ring):
        if not out or out[-1][0] != x or out[-1][1] != y:
            out.append([x, y])
    if out[0] != out[-1]:
        out.append([out[0][0], out[0][1]])
    return out


def polygons_of(geom):
    """Normalise Polygon / MultiPolygon into a list of polygons."""
    if geom["type"] == "Polygon":
        return [geom["coordinates"]]
    if geom["type"] == "MultiPolygon":
        return list(geom["coordinates"])
    raise ValueError(f"unexpected geometry type {geom['type']}")


# --------------------------------------------------------------------------
# build
# --------------------------------------------------------------------------
def build(source: Path, eps_scale: float) -> int:
    raw = json.loads(source.read_text())
    features = raw["features"]
    print(f"source: {len(features)} map units from {source.name}")

    # Pass 1: index by GU_A3 and apply DROP.
    units: dict[str, dict] = {}
    dropped = []
    for f in features:
        gu = f["properties"]["GU_A3"]
        if gu in rules.DROP:
            dropped.append(gu)
            continue
        if gu in units:
            raise SystemExit(f"duplicate GU_A3 in source: {gu}")
        units[gu] = f

    # Pass 2: fold merged units into their target's geometry. Targets are
    # validated up front so a stale rules table reports every problem at once.
    bad_targets = sorted({t for t in rules.MERGE.values() if t not in units})
    if bad_targets:
        for t in bad_targets:
            srcs = [g for g, tt in rules.MERGE.items() if tt == t]
            print(f"  FAIL merge target {t} does not exist (sources: {srcs})")
        raise SystemExit("fix MERGE targets in rules.py")

    merged = []
    for gu, target in rules.MERGE.items():
        if gu not in units:
            print(f"  warning: MERGE source {gu} not present in source data")
            continue
        units[target].setdefault("_extra", []).extend(polygons_of(units[gu]["geometry"]))
        merged.append(gu)
        del units[gu]

    # Pass 3: build places.
    places = []
    geo_features = []
    kept_rings = dropped_rings = pts_in = pts_out = 0

    for gu, f in units.items():
        p = f["properties"]
        code = rules.CODE_OVERRIDE.get(gu) or str(p.get("ISO_A2_EH", "-99"))
        if code == "-99" or len(code) != 2:
            raise SystemExit(
                f"no usable code for {gu} ({p.get('GEOUNIT')}). "
                f"Add it to CODE_OVERRIDE in rules.py."
            )
        name = rules.NAME_OVERRIDE.get(gu) or p.get("GEOUNIT") or p["NAME"]

        polys = polygons_of(f["geometry"]) + f.get("_extra", [])

        # Rank rings by area so the largest is always retained.
        scored = []
        for poly in polys:
            outer = poly[0]
            pts_in += sum(len(r) for r in poly)
            scored.append((ring_area(outer), poly))
        scored.sort(key=lambda s: s[0], reverse=True)

        out_polys = []
        for idx, (area, poly) in enumerate(scored):
            if idx > 0 and area < MIN_RING_AREA:
                dropped_rings += 1
                continue
            outer = poly[0]
            x0, y0, x1, y1 = ring_bbox(outer)
            diag = math.hypot(x1 - x0, y1 - y0)
            # Epsilon scales with the feature so Malta and Monaco keep their
            # shape while Russia's coastline is aggressively thinned.
            eps = min(0.05, max(1e-5, diag * 0.004)) * eps_scale
            dec = decimals_for(diag)

            new_rings = []
            for ri, ring in enumerate(poly):
                # Interior rings (lakes, enclaves) use the same epsilon; they are
                # dropped if they collapse below a valid ring.
                s = quantize(simplify(ring, eps), dec)
                if len(s) >= 4:
                    new_rings.append(s)
                elif ri == 0:
                    new_rings = []
                    break
            if new_rings:
                out_polys.append(new_rings)
                kept_rings += len(new_rings)
                pts_out += sum(len(r) for r in new_rings)

        if not out_polys:
            raise SystemExit(f"{code} {name} simplified to nothing - lower MIN_RING_AREA")

        # Camera target, not the full extent. The full extent is useless here:
        # the United States reaches the antimeridian via the Aleutians, so its
        # true bbox is the whole world. Start from the largest ring, then absorb
        # other substantial rings only while the result stays compact. That
        # keeps Scotland in the United Kingdom's frame while leaving Alaska and
        # Hawaii out of the United States' frame.
        rings = sorted((poly[0] for poly in out_polys), key=ring_area, reverse=True)
        biggest = rings[0]
        main_area = ring_area(biggest)
        bx0, by0, bx1, by1 = ring_bbox(biggest)
        for ring in rings[1:]:
            if ring_area(ring) < main_area * 0.02:
                continue
            rx0, ry0, rx1, ry1 = ring_bbox(ring)
            nx0, ny0 = min(bx0, rx0), min(by0, ry0)
            nx1, ny1 = max(bx1, rx1), max(by1, ry1)
            if nx1 - nx0 <= 60.0 and ny1 - ny0 <= 40.0:
                bx0, by0, bx1, by1 = nx0, ny0, nx1, ny1
        clon, clat = ring_centroid(biggest)

        geo_features.append({
            "type": "Feature",
            # `id` at feature level is what MapLibre feature-state keys on.
            "id": code,
            "properties": {"code": code},
            "geometry": (
                {"type": "Polygon", "coordinates": out_polys[0]}
                if len(out_polys) == 1
                else {"type": "MultiPolygon", "coordinates": out_polys}
            ),
        })
        places.append({
            "code": code,
            "name": name,
            "continent": rules.CONTINENT_OVERRIDE.get(code) or p.get("CONTINENT") or "Other",
            "region": p.get("SUBREGION") or p.get("REGION_UN") or "Other",
            "sovereign": p.get("SOVEREIGNT") or name,
            "uninhabited": code in rules.UNINHABITED,
            "lat": round(clat, 5),
            "lon": round(clon, 5),
            # 5 decimals: 3 would round Vatican City's bbox into a single point.
            "bbox": [round(bx0, 5), round(by0, 5), round(bx1, 5), round(by1, 5)],
        })

    places.sort(key=lambda pl: pl["name"])
    geo_features.sort(key=lambda gf: gf["properties"]["code"])

    # ---------------- validation ----------------
    codes = {pl["code"] for pl in places}
    if len(codes) != len(places):
        seen = set()
        dupes = {pl["code"] for pl in places if pl["code"] in seen or seen.add(pl["code"])}
        raise SystemExit(f"duplicate place codes: {sorted(dupes)}")

    problems = []
    for group in rules.SPLIT_ASSERTIONS:
        missing = [c for c in group if c not in codes]
        if missing:
            problems.append(f"split group {group[0]}: missing {missing}")
    for gu in rules.MERGE_ASSERTIONS:
        if gu in units:
            problems.append(f"{gu} should have been merged away but is still a place")
    for pl in places:
        if pl["continent"] not in rules.VALID_CONTINENTS:
            problems.append(
                f"{pl['code']} {pl['name']} has continent {pl['continent']!r}; "
                f"add it to CONTINENT_OVERRIDE in rules.py"
            )
    if problems:
        for pr in problems:
            print(f"  FAIL {pr}")
        raise SystemExit("territory assertions failed - see PLAN.md section 3")

    # ---------------- write ----------------
    ASSETS.mkdir(parents=True, exist_ok=True)
    gj = ASSETS / "places.geojson"
    reg = ASSETS / "places.json"
    gj.write_text(json.dumps(
        {"type": "FeatureCollection", "features": geo_features},
        separators=(",", ":"),
    ))
    reg.write_text(json.dumps(places, separators=(",", ":"), ensure_ascii=False))

    print(f"dropped {len(dropped)} overlay/reef units, merged {len(merged)} units away")
    print(f"places: {len(places)}  ({sum(1 for pl in places if pl['uninhabited'])} uninhabited)")
    print(f"rings: {kept_rings} kept, {dropped_rings} tiny rings dropped")
    print(f"points: {pts_in:,} -> {pts_out:,}  ({100 * pts_out / pts_in:.1f}%)")
    print(f"{gj.relative_to(REPO)}  {gj.stat().st_size / 1e6:.2f} MB")
    print(f"{reg.relative_to(REPO)}  {reg.stat().st_size / 1e3:.0f} kB")
    print("all territory assertions passed")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    ap.add_argument("--eps-scale", type=float, default=1.0,
                    help="scale all simplification epsilons; >1 is smaller and coarser")
    a = ap.parse_args()
    if not a.source.exists():
        raise SystemExit(
            f"missing {a.source}\n"
            "download it with:\n"
            "  curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \\\n"
            "    https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
            "master/geojson/ne_10m_admin_0_map_units.geojson"
        )
    return build(a.source, a.eps_scale)


if __name__ == "__main__":
    sys.exit(main())
