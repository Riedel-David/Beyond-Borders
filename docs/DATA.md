# Data and regenerating assets

The bundled assets are committed, so a normal build never needs any of this.
Sources and checksums are recorded in [ASSET_SOURCES.md](../ASSET_SOURCES.md).

## Regenerating the visa asset

Committed like the geo assets, so a normal build never needs this. There is
deliberately no in-app refresh: a new matrix has to be re-reviewed against the
curated territory answers, which is a review rather than a download.

```sh
python3 tools/visa/build_visa.py                  # visa.json, for DE (the default)
python3 tools/visa/build_visa.py --passport GB     # visa_gb.json
```

It refuses to write unless every one of the 252 places resolves to exactly one answer
and every curated rule is still live — so the day the dataset starts covering a
territory itself, the build says the override is dead instead of preserving a stale
opinion forever.

## Regenerating the geo assets

Both assets are committed, so a normal build never needs this. Run it only after
changing the curation rules or taking a new Natural Earth release.

```sh
mkdir -p tools/geo/cache
curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson

python3 tools/geo/build_geo.py
```

This writes `app/src/main/assets/places.geojson` (polygons, ~2.6 MB) and
`places.json` (the registry, ~50 kB). It fails loudly rather than shipping a
regression: the territory splits, the reverse merges and the continent buckets are
all asserted before anything is written.

The curation rules — what gets merged, dropped, renamed or given a private code,
and why — live in [`tools/geo/rules.py`](../tools/geo/rules.py). Read that file
before changing anything about the place list.

