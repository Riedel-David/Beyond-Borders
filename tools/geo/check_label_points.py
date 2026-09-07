"""Verify every label point in places_labels.geojson lands inside its
place's mainland cluster in places.geojson, and report how centred it is.

Point-in-polygon against a place's single biggest ring is not enough: a
merged place (Belgium, the UK, Bosnia, ...) ships as several outer rings,
one per source map unit, and a point can be technically inside one of them
while sitting nowhere near the middle of the whole place - that is exactly
the bug this script is meant to catch, so it dissolves the mainland cluster
(the biggest ring plus everything land-connected to it, same definition as
build_geo.py, imported rather than re-implemented) before testing "inside"
or "centred".

It also runs a synthetic regression guard for mainland_cluster/
union_outline/visual_center themselves: two unit squares sharing an edge
must dissolve into a 2x1 rectangle whose pole sits near the rectangle's own
centre, not near either square's - the Belgium/Wallonia bug in miniature,
independent of any real country's geometry, so it can't pass by
coincidence the way a threshold on real, oddly-shaped countries could.

Run after tools/geo/build_geo.py, from the repo root:
    python3 tools/geo/check_label_points.py
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_geo as bg  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent.parent / "app/src/main/assets"

WORST_TO_SHOW = 10


def cluster_regression_guard() -> str | None:
    """None if mainland_cluster/union_outline/visual_center correctly treat
    two edge-sharing rings as one shape; otherwise a description of what
    went wrong."""
    left = [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0], [0.0, 0.0]]
    right = [[1.0, 0.0], [2.0, 0.0], [2.0, 1.0], [1.0, 1.0], [1.0, 0.0]]
    cluster = bg.mainland_cluster([left, right])
    if len(cluster) != 2:
        return f"expected the two squares to cluster together, got {len(cluster)} ring(s)"
    outline = bg.union_outline(cluster)
    x0, y0, x1, y1 = bg.ring_bbox(outline)
    if (x0, y0, x1, y1) != (0.0, 0.0, 2.0, 1.0):
        return f"expected the union to dissolve into a 2x1 rectangle, got bbox {(x0, y0, x1, y1)}"
    cx, _ = bg.visual_center(cluster)
    if abs(cx - 1.0) > 0.15:
        return f"expected the pole near x=1 (the rectangle's centre), got x={cx:.3f}"
    return None


def polygons_of(geometry):
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    return geometry["coordinates"]


def inside(ring, px, py):
    c = False
    n = len(ring)
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]
        xj, yj = ring[j]
        if (yi > py) != (yj > py):
            xint = xi + (xj - xi) * (py - yi) / (yj - yi)
            if px < xint:
                c = not c
        j = i
    return c


def dist_to_ring(ring, kx, px, py):
    best = float("inf")
    for i in range(len(ring) - 1):
        ax, ay = ring[i][0] * kx, ring[i][1]
        bx, by = ring[i + 1][0] * kx, ring[i + 1][1]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        t = 0.0 if length2 < 1e-15 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
        ex, ey = ax + t * dx, ay + t * dy
        d = (px - ex) ** 2 + (py - ey) ** 2
        if d < best:
            best = d
    return math.sqrt(best)


def main() -> int:
    guard_failure = cluster_regression_guard()
    if guard_failure:
        print(f"FAIL: mainland_cluster/union_outline/visual_center regression - {guard_failure}")
        return 1

    places = json.loads((ASSETS / "places.geojson").read_text())
    labels = json.loads((ASSETS / "places_labels.geojson").read_text())

    outlines = {}
    for f in places["features"]:
        code = f["properties"]["code"]
        rings = [poly[0] for poly in polygons_of(f["geometry"])]
        outlines[code] = bg.union_outline(bg.mainland_cluster(rings))

    outside = []
    margin_rows = []
    offset_rows = []
    for f in labels["features"]:
        code = f["properties"]["code"]
        name = f["properties"]["name"]
        px, py = f["geometry"]["coordinates"]
        outline = outlines.get(code)
        if outline is None:
            print(f"  no polygon for label {code} ({name})")
            continue
        x0, y0, x1, y1 = bg.ring_bbox(outline)
        kx = max(0.05, math.cos(math.radians((y0 + y1) / 2.0)))
        long_side = max((x1 - x0) * kx, y1 - y0)
        half_short_side = min((x1 - x0) * kx, y1 - y0) / 2.0

        if not inside(outline, px, py):
            outside.append((code, name))
            continue

        d = dist_to_ring(outline, kx, px * kx, py)
        margin_ratio = d / half_short_side if half_short_side > 1e-9 else 1.0
        margin_rows.append((margin_ratio, code, name))

        cen_x, cen_y = bg.area_centroid_merc(outline)
        cen_y = bg._merc_y_to_lat(cen_y)
        offset = math.hypot(px * kx - cen_x * kx, py - cen_y)
        offset_ratio = offset / long_side if long_side > 1e-9 else 1.0
        offset_rows.append((offset_ratio, code, name))

    margin_rows.sort()
    offset_rows.sort(reverse=True)

    ok = not outside
    if outside:
        print(f"FAIL: {len(outside)} label point(s) outside their mainland cluster:")
        for code, name in outside:
            print(f"  {code}  {name}")
    else:
        print(f"OK: 0/{len(margin_rows)} label points outside their mainland cluster")

    print(f"\nworst {WORST_TO_SHOW} margin ratios (distance-to-edge / half shorter bbox side):")
    for ratio, code, name in margin_rows[:WORST_TO_SHOW]:
        print(f"  {ratio:5.1%}  {code}  {name}")

    print(f"\nworst {WORST_TO_SHOW} centroid offsets (distance-to-cluster-centroid / longer bbox side):")
    for ratio, code, name in offset_rows[:WORST_TO_SHOW]:
        print(f"  {ratio:5.1%}  {code}  {name}")

    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
