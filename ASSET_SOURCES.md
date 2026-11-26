# Bundled asset provenance

The app is offline-first: every reference-data asset under `app/src/main/assets/`
is bundled at build time (the visa files and `advisory.json` can later be replaced by newer ones from the app's updaters, see docs/DATA.md; the bundled copy stays the fallback), so there is no source or checksum
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
| places_labels.geojson | c1185582b7fad64f36b91cf303c617e5adcec52b402b5194258ea1d678fc9c86 | 39798 | same |

## Visa requirements — `tools/visa/build_visa.py`

Built from the Visa-requirements dataset,
<https://github.com/Riedel-David/Visa-requirements>, published at
<https://riedel-david.github.io/Visa-requirements/> (`index.json` and
`passports/<ISO2>.json`). It is itself derived from Wikipedia's "Visa requirements for
... citizens" articles by Wikipedia contributors and is licensed **CC BY-SA 4.0**
(<https://creativecommons.org/licenses/by-sa/4.0/>); the `visa*.json` assets are an
adaptation of it and are shared under the same licence, see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). To cite: "Wikipedia contributors,
*Visa requirements for <nationality> citizens*, Wikipedia, The Free Encyclopedia,
revision <id>" — the article URL and revision of each passport are in its asset
(`dataset.sourceUrl`, `dataset.sourceRevision`), and the article's page history lists
the contributors.

The upstream repository's `LICENSE-DATA.md` sets the same terms (credit "Wikipedia contributors", link the
article and the licence, indicate changes, share adaptations under CC BY-SA 4.0); each asset records the
licence in `dataset.licence` / `dataset.licenceUrl`. `advisory.json` is not Wikipedia-derived and is not under this licence.

`build_visa.py` always downloads the latest publication into `tools/visa/cache/`
(gitignored), so a rebuild is not reproducible from this repository alone. What the
committed assets were built from is therefore recorded instead:

- index.json `generated_at` (also each asset's `asOf`, as a date) and each passport's
  Wikipedia `source_revision`: both are written by the build into
  [`docs/visa-source-revisions.txt`](docs/visa-source-revisions.txt), which is the one
  place to read them. Each asset also carries its own `source_revision`.

`python3 tools/visa/build_visa.py --source DIR` builds offline from a directory holding
`index.json` and `passports/` (a checkout's `data/`, or the cached download). Only the
whole-country record (`area` null) of each passport is read (a state filed only as "<state> and
territories" is read as the state, see `WHOLE_SOVEREIGN_AREAS` in `tools/visa/rules.py`).
A passport whose article yields fewer than 150 answers (today only Northern Cyprus) is not
shipped and is named in the revisions file. `visa.json` is the DE (default) passport; each `visa_<iso2>.json` is the same build for
that passport code. The DE build additionally folds in the German Foreign Office's
travel-warning open data (`https://www.auswaertiges-amt.de/opendata/travelwarning`). On
top of both feeds, `tools/visa/rules.py` applies hand-written corrections and curated
entries (see that file for individual citations). The Foreign Office JSON is likewise
downloaded fresh and not pinned.

The SHA-256 of every `visa*.json` is recorded in
[`docs/visa-checksums.txt`](docs/visa-checksums.txt), regenerated with:

```sh
cd app/src/main/assets && LC_ALL=C shasum -a 256 visa*.json > ../../../../docs/visa-checksums.txt
```

Verify a checkout with `cd app/src/main/assets && shasum -a 256 -c ../../../../docs/visa-checksums.txt`.

## Travel advisories — `tools/advisory/build_advisory.py`

`advisory.json` is generated, never edited by hand: per country, the rating, date and country-page
link of three governments. Sources, fetched live by the script:

- US Department of State, `https://cadataapi.state.gov/api/TravelAdvisories` (public domain; label verbatim).
- UK FCDO, `https://www.gov.uk/api/content/foreign-travel-advice` plus one call per country
  (Open Government Licence v3.0; label is a summary the script writes from the alert-status flags).
- Auswärtiges Amt open data, `https://www.auswaertiges-amt.de/opendata/travelwarning` (bespoke terms;
  label is a summary written from the `warning` and `partialWarning` flags).

Rebuild with `python3 tools/advisory/build_advisory.py` (needs network; `--source tools/advisory/cache` for offline)
and commit the file; details and the tests are in [`docs/DATA.md`](docs/DATA.md). The published copy is rebuilt
every Monday by `.github/workflows/advisory-refresh.yml`. The file records its own `generatedAt` and each
source's `fetchedAt`, so it is not pinned to a checksum like the visa files. Licences and the Auswärtiges Amt
terms: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Country flags — `tools/flags/build_flags.py`

`flags/<iso2>.webp` (250 files, 418882 bytes in total) are the SVG flags of
[hampusborgos/country-flags](https://github.com/hampusborgos/country-flags) at commit
`c09927e63705529bbf59ca6684cd9b23225dddad`, fetched from
`https://api.github.com/repos/hampusborgos/country-flags/tarball/c09927e63705529bbf59ca6684cd9b23225dddad`
(tarball SHA-256 `fc34d6635d21a63579268bb37eb5449c4971667cde308cfcfb44104cd961a285`),
rasterised with resvg at a fixed height of 120 px (the width follows each flag's own
aspect ratio, so none is stretched to 4:3) and encoded with `cwebp -q 85`, alpha kept.
The code list is read from `places.json`; `xn` and `xs` have no upstream flag and the app
falls back to a plain dark banner.
Licence: upstream ships no LICENSE file; its README states the flags come from Wikimedia
Commons and are public domain. No licence text is bundled.

Checksum over all 250 files (every file matched its per-file hash when the per-file table was replaced, 2026-11-26):

```sh
cd app/src/main/assets/flags && LC_ALL=C shasum -a 256 *.webp | shasum -a 256
# 9fe1ab8fcd54da42353bd201202bf7ebe5c524f47df963d726ea0cbf81ccf107
```

For a single file, `shasum -a 256 flags/<iso2>.webp`; `git log -p` on this file has the old per-file table.

## Map label glyphs — `glyphs/Noto Sans Regular/`

`0-255.pbf`, `256-511.pbf`, `512-767.pbf`, `1024-1279.pbf` and `8192-8447.pbf` are
signed-distance-field glyph atlases (Mapbox/MapLibre PBF format) for Noto Sans Regular,
covering code points U+0000 to U+02FF (Latin and modifier letters such as U+02BC),
U+0400 to U+04FF (Cyrillic) and U+2000 to U+20FF (general punctuation: en and em dash,
U+2019 apostrophe). They are bundled so the map's country-name
`SymbolLayer` works with no glyph server. The last three were generated with
`node-fontnik` 0.7.7 from Noto Sans v2.015; the exact commands, font URL and hashes are in
`tools/glyphs/README.md`. The first two files predate that and their generator was never
recorded; the same pipeline reproduces their printable glyphs closely but not byte for
byte (see the README). `OFL.txt` beside the files
gives the copyright as "The Noto Project Authors"
(<https://github.com/notofonts/latin-greek-cyrillic>). Licence: SIL Open Font License
1.1, which permits bundling and conversion; the licence text ships with the glyphs
and in `THIRD_PARTY_NOTICES.md`.

## App font — `res/font/`

`inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf` and `inter_bold.ttf` are
Inter, Version 4.001 (`git-9221beed3`) per the fonts' name tables, Copyright 2016 The
Inter Project Authors (<https://github.com/rsms/inter>). They replace the platform
default (Roboto) and are loaded in `ui/theme/Type.kt`. The download URL and file hashes
were not recorded when they were added. Licence: SIL Open Font License 1.1, which
permits bundling; the notice is in `THIRD_PARTY_NOTICES.md` and the licence text ships as
`app/src/main/assets/licenses/Inter-OFL.txt`, downloaded 2026-11-04 from
<https://raw.githubusercontent.com/rsms/inter/master/LICENSE.txt> (sha256 `262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a`).

## Icons

`ic_settings` and `ic_tab_*` under `app/src/main/res/drawable/` are hand-drawn vector
paths, not copied from Material Icons or another library (the source comments say so).
The launcher and widget drawables are also project-made vectors.
