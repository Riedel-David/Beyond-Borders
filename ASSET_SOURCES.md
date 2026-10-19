# Bundled asset provenance

The app is offline-first: every reference-data asset under `app/src/main/assets/`
is bundled at build time rather than downloaded, so there is no source or checksum
recorded anywhere a rebuild could check against. This file is that record. SHA-256
and byte size were computed directly from the files currently in the repo
(`shasum -a 256`); "source" is what the generator scripts in `tools/` say they were
built from, not a guess.

The `glyphs/` directory is covered in its own section below; the full third-party
licence texts are in `THIRD_PARTY_NOTICES.md`.

## Map geometry — `tools/geo/build_geo.py`

Built from Natural Earth's `ne_10m_admin_0_map_units` layer, downloaded per the
script's own instructions as:

```
curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson
```

Natural Earth data is public domain. The raw layer is then curated by
`tools/geo/rules.py` (merges, splits, and hand-written overrides — see that file
for why) before `build_geo.py` writes the three outputs below. The script
downloads from the `master` branch and the exact `natural-earth-vector` commit used
for the currently-committed assets is not recorded anywhere, so the source is
unpinned; only the upstream URL and the curation step are traceable.

| filename | sha256 | bytes | source |
|---|---|---|---|
| places.geojson | c315e8b08909f1e23dfda270f1f81dd8722905da73845e78a519417ab1ccf3ab | 2669172 | Natural Earth `ne_10m_admin_0_map_units`, curated by `tools/geo/rules.py`, built by `tools/geo/build_geo.py` |
| places.json | 86db954005e6a032623cccd46ec3490606890d5fbfa833f4022af0cf65098aaa | 61861 | same |
| places_labels.geojson | 3690485a7567e9807f2da30f16f0b320f0846b82be0e552fe344efb1ad51ea4b | 39801 | same |

## Visa requirements — `tools/visa/build_visa.py`

Built from the "Passport Index Data" fork at
`https://github.com/imorte/passport-index-data` (file
`passport-index-tidy-iso2.csv`, MIT licence), as of that fork's README date
2026-02-17 (recorded in `tools/visa/rules.py`, `DATASET["as_of"]`, deliberately not
the file's mtime since that changes on every clone). `visa.json` is the DE
(default) passport; each `visa_<iso2>.json` is the same build for that passport
code. The DE build additionally folds in the German Foreign Office's travel-warning
open data (`https://www.auswaertiges-amt.de/opendata/travelwarning`). On top of
both feeds, `tools/visa/rules.py` applies hand-written corrections and curated
entries (see that file for individual citations) — the exact commit of the
upstream CSV used for the currently-committed assets is not recorded, so, as with
the geo assets, only the upstream URL and the as-of date are traceable, not an
exact snapshot.

The SHA-256 of every `visa*.json` is recorded in
[`docs/visa-checksums.txt`](docs/visa-checksums.txt), regenerated with:

```sh
cd app/src/main/assets && LC_ALL=C shasum -a 256 visa*.json > ../../../../docs/visa-checksums.txt
```

Verify a checkout with `cd app/src/main/assets && shasum -a 256 -c ../../../../docs/visa-checksums.txt`.

## Map label glyphs — `glyphs/Noto Sans Regular/`

`0-255.pbf` and `256-511.pbf` are signed-distance-field glyph atlases (Mapbox/MapLibre
PBF format) for Noto Sans Regular, covering code points U+0000 to U+01FF. They are
bundled so the map's country-name `SymbolLayer` works with no glyph server. The
source TTF and the generator tool (fontnik, font-maker or similar) are not recorded
in the repo or its history, so the exact build is unknown; `OFL.txt` beside the files
gives the copyright as "The Noto Project Authors"
(<https://github.com/notofonts/latin-greek-cyrillic>). Licence: SIL Open Font License
1.1, which permits bundling and conversion; the licence text ships with the glyphs
and in `THIRD_PARTY_NOTICES.md`.

## Icons

`ic_settings` and `ic_tab_*` under `app/src/main/res/drawable/` are hand-drawn vector
paths, not copied from Material Icons or another library (the source comments say so).
The launcher and widget drawables are also project-made vectors.
