#!/usr/bin/env python3
"""Build BeyondBorders's bundled geo assets from Natural Earth map units.

    python3 tools/geo/build_geo.py [--source PATH] [--eps-scale 1.0]

Outputs (all committed, so the app builds without network access):
    app/src/main/assets/places.geojson        polygons for the map
    app/src/main/assets/places_labels.geojson one Point per place, for the label SymbolLayer
    app/src/main/assets/places.json           registry: code, name, continent, bbox, centroid

The curation rules live in rules.py. Read that first; it explains why the raw
Natural Earth layer is wrong in both directions and what we do about it.
"""

from __future__ import annotations

import argparse
import heapq
import itertools
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import rules  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = Path(__file__).parent / "cache" / "ne_10m_admin_0_map_units.geojson"
ASSETS = REPO / "app" / "src" / "main" / "assets"

# The on-screen footprint (at 1x density) below which a country's label
# shouldn't draw. Baked into a zoom threshold here, at style-build time, so
# the SymbolLayer's visibility filter is a cheap zoom comparison rather than
# a runtime screen-space computation.
MIN_LABEL_FOOTPRINT_DP = 10.0

# Must track MapScreen.kt's maxZoomPreference. Without this cap, a tiny or
# narrow place (Liechtenstein, Vatican City, most microstates) can compute a
# min_label_zoom past the zoom the map can ever reach, so its label would
# never draw at all rather than just draw late.
MAX_LABEL_ZOOM = 9.0

# Estimating whether a label's own text will fit inside its country, so
# short countries with long names (or just short names that happen to be
# small, like Belgium and Switzerland) don't get their label appearing while
# the country is still narrower than its own rendered text and spilling into
# a neighbour. Must track PlacesMapStyle.kt's SymbolLayer(LAYER_LABELS):
# the two textSize stops and textMaxWidth(8f).
AVG_GLYPH_WIDTH_EM = 0.58  # Noto Sans Regular's rough average advance width
TEXT_SIZE_ZOOM_LO, TEXT_SIZE_SP_LO = 2.0, 8.0
TEXT_SIZE_ZOOM_HI, TEXT_SIZE_SP_HI = 8.0, 15.0
TEXT_MAX_WIDTH_EM = 8.0
LABEL_WIDTH_MARGIN = 0.75  # headroom so text doesn't hug the coastline

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

# How much of the pole of inaccessibility's own clearance the label point is
# allowed to give up while being pulled toward the mainland cluster's
# centroid (see visual_center). 0.7 lets compact shapes move most of the way
# to their middle; a crescent whose centroid is offshore barely moves at all,
# because leaving the shape costs clearance immediately.
LABEL_CLEARANCE_FLOOR = 0.7


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


EARTH_RADIUS_KM = 6371.0088
SQRT2 = math.sqrt(2.0)


def ring_area_km2(ring):
    """
    Approximate area of a lon/lat ring on the sphere, in square km.

    Square-degree area (see [ring_area]) is only good for comparing rings
    against each other; a degree of longitude shrinks towards the poles, so
    it would make Greenland look tiny and equatorial countries huge. This
    integrates dA = R^2 * cos(lat) dlat dlon along each edge instead, which is
    the standard closed-form area of a polygon with lon/lat straight edges
    and accurate enough for a "% of the world's land" headline.
    """
    area = 0.0
    n = len(ring)
    for i in range(n):
        lon1, lat1 = ring[i][0], ring[i][1]
        lon2, lat2 = ring[(i + 1) % n][0], ring[(i + 1) % n][1]
        area += math.radians(lon2 - lon1) * (
            2 + math.sin(math.radians(lat1)) + math.sin(math.radians(lat2))
        )
    return abs(area) * EARTH_RADIUS_KM * EARTH_RADIUS_KM / 2.0


MERCATOR_LAT_LIMIT = 85.05112878  # where Web Mercator's y blows up; MapLibre clips here too


def _lat_to_merc_y(lat_deg: float) -> float:
    lat_deg = max(-MERCATOR_LAT_LIMIT, min(MERCATOR_LAT_LIMIT, lat_deg))
    return math.degrees(math.log(math.tan(math.pi / 4.0 + math.radians(lat_deg) / 2.0)))


def _merc_y_to_lat(y_deg: float) -> float:
    return math.degrees(2.0 * math.atan(math.exp(math.radians(y_deg))) - math.pi / 2.0)


def ring_edges(ring):
    return [(tuple(ring[i]), tuple(ring[i + 1])) for i in range(len(ring) - 1)]


def mainland_cluster(rings):
    """The biggest ring plus every ring transitively sharing at least one
    border edge with it.

    A place folded together by MERGE (Belgium's Flanders/Wallonia/Brussels,
    the UK's four home nations, Bosnia's two entities, ...) ships as several
    separate outer rings, one per source map unit. Only the ones connected
    to the biggest by a land border belong in the shape a label should sit
    in the middle of - an island shares no edge with anything, so Shetland,
    the Azores and Zanzibar can't drag the point offshore.
    """
    if len(rings) == 1:
        return rings
    edge_owners: dict[tuple, list] = {}
    for ridx, ring in enumerate(rings):
        seen_in_ring = set()
        for a, b in ring_edges(ring):
            key = (a, b) if a <= b else (b, a)
            if key in seen_in_ring:
                continue
            seen_in_ring.add(key)
            edge_owners.setdefault(key, []).append(ridx)
    neighbours: dict[int, set] = {i: set() for i in range(len(rings))}
    for owners in edge_owners.values():
        if len(owners) > 1:
            for i in owners:
                neighbours[i].update(o for o in owners if o != i)
    biggest = max(range(len(rings)), key=lambda i: ring_area(rings[i]))
    cluster = {biggest}
    frontier = [biggest]
    while frontier:
        cur = frontier.pop()
        for nb in neighbours[cur]:
            if nb not in cluster:
                cluster.add(nb)
                frontier.append(nb)
    return [rings[i] for i in sorted(cluster)]


def union_outline(rings):
    """Dissolve a cluster of rings that share border edges into the outline
    of their union - stdlib only, no shapely.

    simplify_shared_borders already guarantees a shared border is the
    identical polyline on both sides of the join, walked in opposite
    directions (each ring encloses its own area to the same hand, so a
    border walked by one owner runs backwards relative to the other). So
    every internal edge has its exact reverse somewhere else in the
    cluster; cancel those pairs and what is left is the outer boundary,
    which chains back into one or more closed loops. Falls back to the
    single biggest ring if there is nothing to cancel (a cluster of one).
    """
    if len(rings) == 1:
        return rings[0]
    edges = set()
    for ring in rings:
        for a, b in ring_edges(ring):
            if a != b:
                edges.add((a, b))
    boundary = {a: b for a, b in edges if (b, a) not in edges}
    if not boundary:
        return max(rings, key=ring_area)
    loops = []
    consumed = set()
    for start in boundary:
        if start in consumed:
            continue
        loop = [start]
        consumed.add(start)
        cur = boundary[start]
        while cur != start and cur not in consumed:
            loop.append(cur)
            consumed.add(cur)
            nxt = boundary.get(cur)
            if nxt is None:
                break
            cur = nxt
        loop.append(loop[0])
        loops.append(loop)
    best = max(loops, key=ring_area)
    return [list(p) for p in best]


def area_centroid_merc(ring):
    """Area-weighted centroid of a closed ring, in the same Web Mercator
    space visual_center searches in (see its docstring for why)."""
    cx = cy = signed_area2 = 0.0
    for i in range(len(ring) - 1):
        x0, y0 = ring[i][0], _lat_to_merc_y(ring[i][1])
        x1, y1 = ring[i + 1][0], _lat_to_merc_y(ring[i + 1][1])
        cross = x0 * y1 - x1 * y0
        signed_area2 += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    if abs(signed_area2) < 1e-12:
        x0, y0, x1, y1 = ring_bbox(ring)
        return (x0 + x1) / 2.0, _lat_to_merc_y((y0 + y1) / 2.0)
    area6 = signed_area2 * 3.0
    return cx / area6, cy / area6


def visual_center(rings):
    """
    A point deep inside the union of [rings]: Mapbox's Polylabel algorithm
    over the dissolved outline, pulled toward the area centroid without
    giving up much clearance.

    [rings] is normally a mainland_cluster() result, not one arbitrary
    ring - see union_outline for how they are dissolved into a single
    boundary before the search below ever runs.

    A plain area centroid lands outside the coastline entirely for a
    crescent like Croatia or a coast-hugging arc like Norway - the "average"
    of a banana shape is off to one side of the banana. Polylabel instead
    finds the point of maximum distance from the boundary (the "pole of
    inaccessibility"): a priority queue of square cells covering the bbox,
    always exploring the cell whose *best possible* score (its centre's
    signed distance to the ring, plus its own radius) is highest, splitting
    it into four quadrants each time a cell is explored, until no
    unexplored cell could beat the best point found so far by more than
    [precision]. That is a precision-bounded result for a country of any
    size or shape, rather than a fixed grid resolution that is coarse for
    some countries and wasted effort for others - the failure mode an
    earlier, hand-rolled shrinking-grid version of this function had.
    Falls back to the bbox centre for degenerate rings.

    The search runs in Web Mercator space, not raw lon/lat, and that isn't
    cosmetic: the map itself renders in Web Mercator, where x is linear in
    longitude everywhere but y stretches by 1/cos(lat) away from the
    equator. An earlier version of this function instead shrank x by
    cos(lat) - the correction for *ground-distance* area/simplification
    math elsewhere in this file, wrongly reused here. That makes the search
    undervalue horizontal closeness to a border relative to vertical
    closeness at any latitude away from the equator, so it would happily
    push the point closer to one side's coastline than the other to buy a
    little more vertical room - invisible in an any-direction distance
    check, but exactly what put Switzerland's and Belgium's labels near one
    border on screen even though they scored fine on margin-to-nearest-edge.
    """
    ring = union_outline(rings)
    x0, y0, x1, y1 = ring_bbox(ring)
    width, height = x1 - x0, y1 - y0
    if width < 1e-9 or height < 1e-9 or len(ring) < 4:
        return (x0 + x1) / 2.0, (y0 + y1) / 2.0

    # Project onto the same Web Mercator the map renders in: x is lon
    # unchanged, y is the Mercator y for lat. Distances, the point-in-
    # polygon test and the search all run in this space; the winning point
    # is projected back to lat at the very end.
    merc_ring = [(px, _lat_to_merc_y(py)) for px, py in ring]
    mx0, mx1 = x0, x1
    my_vals = [p[1] for p in merc_ring]
    my0, my1 = min(my_vals), max(my_vals)
    mwidth, mheight = mx1 - mx0, my1 - my0
    edges = [(merc_ring[i][0], merc_ring[i][1], merc_ring[i + 1][0], merc_ring[i + 1][1])
              for i in range(len(merc_ring) - 1)]

    def inside(px, py):
        c = False
        n = len(merc_ring)
        j = n - 1
        for i in range(n):
            xi, yi = merc_ring[i]
            xj, yj = merc_ring[j]
            if (yi > py) != (yj > py):
                xint = xi + (xj - xi) * (py - yi) / (yj - yi)
                if px < xint:
                    c = not c
            j = i
        return c

    def dist_to_edges(px, py):
        best = float("inf")
        for ax, ay, bx, by in edges:
            dx, dy = bx - ax, by - ay
            length2 = dx * dx + dy * dy
            t = 0.0 if length2 < 1e-15 else max(
                0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2)
            )
            ex, ey = ax + t * dx, ay + t * dy
            d = (px - ex) ** 2 + (py - ey) ** 2
            if d < best:
                best = d
        return math.sqrt(best)

    def signed_dist(px, py):
        # Negative outside, not just excluded: without a sign, a cell
        # entirely outside the ring has nothing to distinguish "close to the
        # coastline" from "the middle of the ocean", so the queue can't tell
        # which outside cells are worth splitting to crawl inward.
        d = dist_to_edges(px, py)
        return d if inside(px, py) else -d

    class Cell:
        __slots__ = ("x", "y", "h", "d", "potential")

        def __init__(self, x, y, h):
            self.x, self.y, self.h = x, y, h
            self.d = signed_dist(x, y)
            self.potential = self.d + h * SQRT2

    precision = max(mwidth, mheight) / 200.0
    cell_size = min(mwidth, mheight) / 2.0
    counter = itertools.count()  # heap tie-breaker; Cell has no ordering of its own
    queue = []

    best = Cell((mx0 + mx1) / 2.0, (my0 + my1) / 2.0, 0.0)
    x = mx0
    while x < mx1:
        y = my0
        while y < my1:
            cell = Cell(x + cell_size / 2.0, y + cell_size / 2.0, cell_size / 2.0)
            if cell.d > best.d:
                best = cell
            heapq.heappush(queue, (-cell.potential, next(counter), cell))
            y += cell_size
        x += cell_size

    max_iterations = 10_000  # generous relative to the ~hundreds this needs; a safety cap, not a tuning knob
    for _ in range(max_iterations):
        if not queue or -queue[0][0] - best.d <= precision:
            break
        _, _, cell = heapq.heappop(queue)
        h = cell.h / 2.0
        for dx, dy in ((-h, -h), (h, -h), (-h, h), (h, h)):
            child = Cell(cell.x + dx, cell.y + dy, h)
            if child.d > best.d:
                best = child
            heapq.heappush(queue, (-child.potential, next(counter), child))

    # Pull toward the centroid: compact shapes (Belgium, Switzerland) should
    # read as "in the middle", not just "as far from every border as
    # possible", which for a lopsided shape is the fat end. Walk from the
    # pole toward the area centroid and keep the furthest point that stays
    # inside and keeps at least LABEL_CLEARANCE_FLOOR of the pole's own
    # clearance - a crescent whose centroid is offshore loses clearance
    # within the first few percent of the walk and stops almost immediately.
    cen_x, cen_y = area_centroid_merc(ring)

    def clearance_at(t):
        px = best.x + (cen_x - best.x) * t
        py = best.y + (cen_y - best.y) * t
        return dist_to_edges(px, py) if inside(px, py) else -1.0

    floor = best.d * LABEL_CLEARANCE_FLOOR
    lo, hi = 0.0, 1.0
    if clearance_at(hi) < floor:
        for _ in range(40):
            mid = (lo + hi) / 2.0
            if clearance_at(mid) >= floor:
                lo = mid
            else:
                hi = mid
        t = lo
    else:
        t = hi
    final_x = best.x + (cen_x - best.x) * t
    final_y = best.y + (cen_y - best.y) * t
    return final_x, _merc_y_to_lat(final_y)


def horizontal_clearance_deg(ring, px, py):
    """
    Raw longitude degrees from (px, py) to the ring's boundary, cast left and
    cast right along latitude py - i.e. the room the label text actually has
    to grow into on each side of its own anchor point.

    Not the same as the anchor's overall distance-to-nearest-edge: Polylabel
    picks the point that maximises that (any-direction) distance, which is
    not required to sit at the horizontal midpoint of the country's bbox.
    Switzerland's true pole of inaccessibility, for instance, sits well
    towards the wide eastern half of the country, because the shape narrows
    hard towards Geneva in the west - so it has ~2.5x more room to its east
    than its west. A zoom threshold based on the *bbox's* width implicitly
    assumes the label has equal room both ways, which is only true for
    fairly round/symmetric shapes; for anything else it lets the label
    appear while its shorter side is still too tight, running the label text
    into whatever country is next door on that side.
    """
    left = right = float("inf")
    n = len(ring)
    for i in range(n - 1):
        x0, y0 = ring[i]
        x1, y1 = ring[i + 1]
        if (y0 - py) * (y1 - py) < 0:
            t = (py - y0) / (y1 - y0)
            xi = x0 + t * (x1 - x0)
            if xi > px:
                right = min(right, xi - px)
            elif xi < px:
                left = min(left, px - xi)
    return left, right


def label_zoom_for_width(name: str, available_width_deg: float) -> float:
    """
    The zoom at which the label's anchor has enough room on its tighter side
    for its own label text, given the SymbolLayer's zoom-interpolated font
    size. [available_width_deg] is twice the smaller of the anchor's actual
    left/right clearance (see horizontal_clearance_deg) - not the country's
    bbox width, which overstates the room available on an off-centre anchor's
    shorter side.

    A single word (most country names) can't wrap, so it needs its full
    width; a multi-word name wraps at TEXT_MAX_WIDTH_EM, so only its longest
    word has to fit. Binary search rather than a closed form because the
    font-size interpolation the label draws with is piecewise linear, and
    the footprint grows exponentially with zoom - solving that directly
    isn't worth it when the curve is monotonic and 60 bisections converge to
    far more precision than this estimate deserves anyway.
    """
    words = name.split()
    width_chars = max(len(w) for w in words) if len(words) > 1 else len(name)
    width_em = width_chars * AVG_GLYPH_WIDTH_EM
    if len(words) > 1:
        width_em = min(width_em, TEXT_MAX_WIDTH_EM)

    def font_sp(z):
        if z <= TEXT_SIZE_ZOOM_LO:
            return TEXT_SIZE_SP_LO
        if z >= TEXT_SIZE_ZOOM_HI:
            return TEXT_SIZE_SP_HI
        t = (z - TEXT_SIZE_ZOOM_LO) / (TEXT_SIZE_ZOOM_HI - TEXT_SIZE_ZOOM_LO)
        return TEXT_SIZE_SP_LO + t * (TEXT_SIZE_SP_HI - TEXT_SIZE_SP_LO)

    def fits(z):
        footprint_dp = available_width_deg * 512.0 * (2.0 ** z) / 360.0
        return footprint_dp >= width_em * font_sp(z) * LABEL_WIDTH_MARGIN

    lo, hi = 0.0, 25.0
    for _ in range(60):
        mid = (lo + hi) / 2.0
        if fits(mid):
            hi = mid
        else:
            lo = mid
    return hi


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


def quantize_open(pts, decimals):
    """Like quantize(), but for an open arc between two junctions - forcing
    first == last here would draw a false edge closing the arc into a loop."""
    out = []
    for x, y in ((round(p[0], decimals), round(p[1], decimals)) for p in pts):
        if not out or out[-1][0] != x or out[-1][1] != y:
            out.append([x, y])
    return out


def _run_points(pts, start, end):
    """The point run from start to end inclusive, walking forward and wrapping
    past the end of the ring if needed. start == end means the whole ring."""
    if start == end:
        return pts[start:] + pts[: start + 1]
    if start < end:
        return pts[start : end + 1]
    return pts[start:] + pts[: end + 1]


def simplify_shared_borders(ring_infos):
    """Simplify every ring, treating a border shared by two rings as one arc
    simplified once, instead of each side independently.

    Without this, a border between a large country and a small one gets two
    different simplifications - each ring's epsilon is scaled to its own
    bounding box - so the two lines drift apart and visibly cross instead of
    coinciding. Natural Earth's shared borders are bit-identical coordinates
    on both sides before simplification, which is what makes matching them
    back up by point sequence reliable.

    ring_infos: list of {"pts": open ring (no closing duplicate), "eps", "dec"}.
    Returns closed, simplified rings in the same order.
    """
    # Every undirected edge and which ring indices use it. A shared border
    # edge is used by exactly two rings; a coastline, by one.
    edge_users: dict[tuple, set] = {}
    for ridx, info in enumerate(ring_infos):
        pts = info["pts"]
        m = len(pts)
        for i in range(m):
            a, b = pts[i], pts[(i + 1) % m]
            if a == b:
                continue
            key = (a, b) if a <= b else (b, a)
            edge_users.setdefault(key, set()).add(ridx)

    # A point is a junction where a ring's edge ownership changes - where a
    # shared border starts or ends, or a third neighbour's edge begins.
    runs_by_ring = []
    for ridx, info in enumerate(ring_infos):
        pts = info["pts"]
        m = len(pts)

        def users_at(i, pts=pts, m=m):
            a, b = pts[i], pts[(i + 1) % m]
            key = (a, b) if a <= b else (b, a)
            return edge_users[key]

        junctions = [i for i in range(m) if users_at((i - 1) % m) != users_at(i)]
        if not junctions:
            junctions = [0]
        runs_by_ring.append(
            [
                (junctions[k], junctions[(k + 1) % len(junctions)])
                for k in range(len(junctions))
            ]
        )

    # Group runs into arcs by point sequence, forward or reversed - the same
    # border walked from either neighbour collapses onto one arc.
    arcs: dict[tuple, dict] = {}
    entries_by_ring = []
    for ridx, info in enumerate(ring_infos):
        pts = info["pts"]
        entries = []
        for start, end in runs_by_ring[ridx]:
            run = _run_points(pts, start, end)
            fwd, rev = tuple(run), tuple(reversed(run))
            key = fwd if fwd <= rev else rev
            direction = "f" if key == fwd else "r"
            arc = arcs.get(key)
            if arc is None:
                arcs[key] = {"pts": list(key), "eps": info["eps"], "dec": info["dec"]}
            else:
                arc["eps"] = min(arc["eps"], info["eps"])
                arc["dec"] = max(arc["dec"], info["dec"])
            entries.append((key, direction))
        entries_by_ring.append(entries)

    # Simplify each arc exactly once, at the finer of its owners' budgets, so
    # both sides of a border end up with identical points.
    simplified = {}
    for key, arc in arcs.items():
        simplified[key] = quantize_open(simplify(arc["pts"], arc["eps"]), arc["dec"])

    # Reassemble each ring from its arcs in order, dropping the duplicate
    # junction point between consecutive arcs and re-closing at the end.
    result = []
    for entries in entries_by_ring:
        assembled = []
        for key, direction in entries:
            pts = simplified[key]
            seq = pts if direction == "f" else list(reversed(pts))
            assembled.extend(seq[:-1])
        if not assembled:
            assembled = [simplified[entries[0][0]][0]]
        assembled.append(assembled[0])
        result.append(assembled)
    return result


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

    # Pass 1b: cut specific polygons out of a unit's own geometry into a
    # different unit's geometry. See MOVE_POLYGON in rules.py.
    for gu, moves in rules.MOVE_POLYGON.items():
        if gu not in units:
            raise SystemExit(f"MOVE_POLYGON source {gu} not present in source data")
        kept = []
        moved_any = [False] * len(moves)
        for poly in polygons_of(units[gu]["geometry"]):
            bx0, by0, bx1, by1 = ring_bbox(poly[0])
            for i, move in enumerate(moves):
                mx0, my0, mx1, my1 = move["bbox"]
                if mx0 <= bx0 and bx1 <= mx1 and my0 <= by0 and by1 <= my1:
                    target = move["target"]
                    if target not in units:
                        raise SystemExit(f"MOVE_POLYGON target {target} does not exist")
                    units[target].setdefault("_extra", []).append(poly)
                    moved_any[i] = True
                    break
            else:
                kept.append(poly)
        if not all(moved_any):
            raise SystemExit(f"MOVE_POLYGON: no polygon in {gu} matched {moves}")
        units[gu]["geometry"] = (
            {"type": "Polygon", "coordinates": kept[0]} if len(kept) == 1
            else {"type": "MultiPolygon", "coordinates": kept}
        )

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
        # Each merged-away unit carries its own POP_EST (Scotland's, not the
        # UK's), so the target's population has to add them up the same way
        # the geometry does, or a merged place would report just its anchor's
        # share of the population instead of the whole place.
        units[target]["_extra_pop"] = (
            units[target].get("_extra_pop", 0) + units[gu]["properties"].get("POP_EST", 0)
        )
        merged.append(gu)
        del units[gu]

    # Pass 3: build places.
    places = []
    geo_features = []
    label_features = []
    kept_rings = dropped_rings = pts_in = pts_out = 0

    # Pass 3a: decide which polygons/rings survive MIN_RING_AREA, and each
    # ring's simplification budget, without simplifying yet - that has to
    # happen once, globally, so a border shared by two units simplifies to the
    # same line on both sides (see simplify_shared_borders).
    unit_data = []
    ring_infos = []
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

        kept_polys = []  # list of {"eps", "dec", "ring_idx": [index into ring_infos, ...]}
        for idx, (area, poly) in enumerate(scored):
            if idx > 0 and area < MIN_RING_AREA:
                dropped_rings += 1
                continue
            outer = poly[0]
            x0, y0, x1, y1 = ring_bbox(outer)
            diag = math.hypot(x1 - x0, y1 - y0)
            # Epsilon scales with the feature so Malta and Monaco keep their
            # shape while Russia's coastline is aggressively thinned. Shared
            # borders reconcile this against the neighbour's budget below.
            eps = min(0.05, max(1e-5, diag * 0.004)) * eps_scale
            dec = decimals_for(diag)

            ring_idx = []
            for ring in poly:
                # Open: drop the closing duplicate, and any exact consecutive
                # duplicates in the source data, which would otherwise be a
                # zero-length "edge" with no entry in edge_users.
                pts = []
                for pt in ring[:-1]:
                    t = tuple(pt)
                    if not pts or pts[-1] != t:
                        pts.append(t)
                if len(pts) > 1 and pts[-1] == pts[0]:
                    pts.pop()
                ring_infos.append({"pts": pts, "eps": eps, "dec": dec})
                ring_idx.append(len(ring_infos) - 1)
            kept_polys.append({"ring_idx": ring_idx})

        pop_est = int(p.get("POP_EST", 0)) + int(f.get("_extra_pop", 0))
        unit_data.append((code, name, p, kept_polys, pop_est))

    # Pass 3b: simplify once, globally.
    simplified_rings = simplify_shared_borders(ring_infos)

    # Pass 3c: reassemble polygons from their simplified rings, drop whatever
    # collapsed below a valid ring, and build the place/feature records.
    for code, name, p, kept_polys, pop_est in unit_data:
        out_polys = []
        for poly in kept_polys:
            new_rings = []
            for ri, ring_idx in enumerate(poly["ring_idx"]):
                s = simplified_rings[ring_idx]
                # Interior rings (lakes, enclaves) are dropped individually if
                # they collapse below a valid ring; the outer ring collapsing
                # drops the whole polygon.
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

        # Land area, holes (lakes, enclaves) subtracted from their polygon's
        # outer ring so a country is not credited with the water inside it.
        area_km2 = 0.0
        for new_rings in out_polys:
            area_km2 += ring_area_km2(new_rings[0])
            for hole in new_rings[1:]:
                area_km2 -= ring_area_km2(hole)

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
        # The label point, its footprint and its clearance are all about the
        # mainland - the biggest ring plus everything land-connected to it
        # (see mainland_cluster) - not the camera-target bbox above, which
        # deliberately also covers Scotland-style attached-but-separate
        # rings. Using the dissolved cluster outline instead of the biggest
        # ring alone is the fix for merged places (Belgium, UK, Bosnia, ...)
        # whose biggest ring is only one piece of the whole place - see
        # PLAN.md "country labels off-centre in merged places".
        cluster = mainland_cluster(rings)
        outline = union_outline(cluster)
        if code in rules.LABEL_POINT_OVERRIDE:
            clon, clat = rules.LABEL_POINT_OVERRIDE[code]
        else:
            clon, clat = visual_center(cluster)

        # Web-Mercator tile math, ignoring latitude distortion (same
        # approximation the screen-space code it replaces already made). 512,
        # not 256: MapLibre's zoom 0 puts the world at 512 dp across (see
        # MapScreen.kt's WORLD camera comment), so pixels-per-degree at zoom z
        # is 512 * 2^z / 360. Solve for the zoom at which the cluster's own
        # bbox's longer side crosses the footprint threshold, so the label
        # only appears once the country reads as more than a sliver.
        ox0, oy0, ox1, oy1 = ring_bbox(outline)
        span_deg = max(ox1 - ox0, oy1 - oy0, 1e-9)
        footprint_zoom = max(0.0, math.log2(MIN_LABEL_FOOTPRINT_DP * 360.0 / (span_deg * 512.0)))
        # Beyond just "not a sliver": Belgium and Switzerland are big enough
        # to clear footprint_zoom easily, but at that zoom their own name is
        # wider than they are, spilling into a neighbour. The room the label
        # actually has is set by its tighter side, not the country's bbox
        # width - an off-centre anchor (see horizontal_clearance_deg) has much
        # less room on one side than the bbox alone would suggest.
        left, right = horizontal_clearance_deg(outline, clon, clat)
        available_width_deg = 2.0 * min(left, right) if left < float("inf") and right < float("inf") else span_deg
        text_zoom = label_zoom_for_width(name, available_width_deg)
        min_label_zoom = round(min(max(footprint_zoom, text_zoom), MAX_LABEL_ZOOM), 2)

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
        # A separate Point per place, at the same centroid places.json uses,
        # rather than reusing the polygon above for the label SymbolLayer:
        # MapLibre anchors a polygon symbol once per polygon *part*, so a
        # MultiPolygon country (Russia's islands, Canada's archipelago,
        # Antarctica's claims) would otherwise get one label per fragment
        # instead of one at the country's true visual centre.
        label_features.append({
            "type": "Feature",
            "id": code,
            "properties": {"code": code, "name": name, "minLabelZoom": min_label_zoom},
            "geometry": {"type": "Point", "coordinates": [round(clon, 5), round(clat, 5)]},
        })
        places.append({
            "code": code,
            "name": name,
            "continent": rules.CONTINENT_OVERRIDE.get(code) or p.get("CONTINENT") or "Other",
            "region": p.get("SUBREGION") or p.get("REGION_UN") or "Other",
            "sovereign": p.get("SOVEREIGNT") or name,
            "uninhabited": code in rules.UNINHABITED,
            "unMember": code in rules.UN_MEMBER,
            "lat": round(clat, 5),
            "lon": round(clon, 5),
            # 5 decimals: 3 would round Vatican City's bbox into a single point.
            "bbox": [round(bx0, 5), round(by0, 5), round(bx1, 5), round(by1, 5)],
            "areaKm2": round(area_km2),
            "popEst": pop_est,
        })

    places.sort(key=lambda pl: pl["name"])
    geo_features.sort(key=lambda gf: gf["properties"]["code"])
    label_features.sort(key=lambda gf: gf["properties"]["code"])

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
    unknown_un_member = rules.UN_MEMBER - codes
    if unknown_un_member:
        problems.append(f"UN_MEMBER has codes with no place: {sorted(unknown_un_member)}")
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
    gj_labels = ASSETS / "places_labels.geojson"
    reg = ASSETS / "places.json"
    gj.write_text(json.dumps(
        {"type": "FeatureCollection", "features": geo_features},
        separators=(",", ":"),
    ))
    gj_labels.write_text(json.dumps(
        {"type": "FeatureCollection", "features": label_features},
        separators=(",", ":"),
        ensure_ascii=False,
    ))
    reg.write_text(json.dumps(places, separators=(",", ":"), ensure_ascii=False))

    print(f"dropped {len(dropped)} overlay/reef units, merged {len(merged)} units away")
    print(f"places: {len(places)}  ({sum(1 for pl in places if pl['uninhabited'])} uninhabited, "
          f"{sum(1 for pl in places if pl['unMember'])} UN member/observer)")
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
