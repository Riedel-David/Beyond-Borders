# Data and regenerating assets

The bundled assets are committed, so a normal build never needs any of this.
Sources and checksums are recorded in [ASSET_SOURCES.md](../ASSET_SOURCES.md).

## Regenerating the visa asset

There is
deliberately no in-app refresh: a new publication has to be re-reviewed against the
curated territory answers, which is a review rather than a download.

The source is the Visa-requirements dataset
(<https://riedel-david.github.io/Visa-requirements/>, CC BY-SA 4.0, derived from
Wikipedia). The build downloads the latest publication every time, so the
`generated_at` date and the per-passport Wikipedia revisions are rewritten into
`docs/visa-source-revisions.txt` by a full build, and nothing else needs updating by
hand for provenance.

```sh
mkdir -p tools/visa/cache
curl -sL -o tools/visa/cache/aa-travelwarning.json https://www.auswaertiges-amt.de/opendata/travelwarning
python3 tools/visa/build_visa.py                  # every passport (downloads the dataset first)
python3 tools/visa/build_visa.py --passport GB --source tools/visa/cache/visa-requirements
                                                    # just visa_gb.json, from the cached dataset
python3 tools/visa/build_visa.py --source ~/Visa-requirements/data   # offline, from a checkout
```

After rebuilding, refresh the recorded hashes as described in
[ASSET_SOURCES.md](../ASSET_SOURCES.md).

It refuses to write unless every one of the 252 places resolves to exactly one answer
and every rule is still live — so the day the dataset starts covering a territory
itself, the build says the override is dead instead of preserving a stale opinion
forever. The dataset's `special` status (tourist card, invitation, "travel banned", ...)
is its own "Check requirements" category (a violet band on the map) at low confidence,
with the dataset's note (cut to about 280 characters at a sentence end, `NOTE_MAX_CHARS`) or,
when it has none, a generic "check the official requirements" note. Upstream files ESTA and Canada's eTA under `visa_free`;
`ETA_RULES` in `rules.py` turns those back into authorisations (the ESTA match skips the notes that say
who is *exempt*, `ESTA_NEGATIONS`; a note that mentions ESTA but matches neither that nor
`ESTA_REQUIRED` fails the build). The sovereign-gap exceptions are written to
`docs/visa-gap-allowlist.txt`, which the coverage test reads.

Two more guards: whole states that upstream files under an area such as "and
territories" are read as the state (`WHOLE_SOVEREIGN_AREAS`), and the build fails if a
sovereign place has no dataset row for a passport unless it is a named gap
(`SOVEREIGN_GAP_*`); and a passport with fewer than `MIN_DATASET_ROWS` (150) dataset
answers is not shipped at all (Northern Cyprus, whose article gives one).

### Is a newer snapshot out?

`.github/workflows/visa-freshness.yml` runs every Monday, and on demand from the Actions
tab. It downloads the dataset's `index.json` and opens an issue (one per kind of problem, with a fixed title
per kind, none while one of that kind is open) only when a passport is added or removed upstream, the index
`schema_version` major changes, or the index cannot be fetched or read. Wikipedia
revision drift (`source_revision` against `dataset.sourceRevision` in `visa*.json`) is
expected weekly and is only listed in the job summary. It changes nothing in the repo:
refreshing stays the manual review above.

## Regenerating the geo assets

The three assets are committed, so a normal build never needs this. Run it only after
changing the curation rules or taking a new Natural Earth release.

```sh
mkdir -p tools/geo/cache
curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson

python3 tools/geo/build_geo.py
```

This writes `app/src/main/assets/places.geojson` (polygons, ~2.6 MB),
`places_labels.geojson` (label points, ~40 kB) and `places.json` (the registry, ~60 kB). It fails loudly rather than shipping a
regression: the territory splits, the reverse merges and the continent buckets are
all asserted before anything is written.

The curation rules — what gets merged, dropped, renamed or given a private code,
and why — live in [`tools/geo/rules.py`](../tools/geo/rules.py). Read that file
before changing anything about the place list.

