# Third-party notices

Beyond Borders itself is licensed under the GNU GPL v3 (see `LICENSE`). It bundles
or links the following third-party material, each under its own licence. Where a
data asset is generated, `ASSET_SOURCES.md` records how.

## Visa requirements (CC BY-SA 4.0)

The `visa.json` and `visa_<iso2>.json` assets are an adaptation of the
Visa-requirements dataset, <https://github.com/Riedel-David/Visa-requirements>
(published at <https://riedel-david.github.io/Visa-requirements/>), which is itself
derived from the English Wikipedia articles "Visa requirements for ... citizens" by
Wikipedia contributors, <https://en.wikipedia.org/>. Each article's URL and revision
is recorded per passport in its asset (`dataset.sourceUrl`, `dataset.sourceRevision`)
and in `docs/visa-source-revisions.txt`; the article page histories list the
contributors.

Licence: Creative Commons Attribution-ShareAlike 4.0 International,
<https://creativecommons.org/licenses/by-sa/4.0/>. Changes made here: only the
whole-country answer of each passport is kept; Wikipedia's wording is reduced to the
app's categories and day counts; some answers are hand-corrected or added in
`tools/visa/rules.py`; and the DE asset additionally carries links to the German
Foreign Office's travel advice. **Share-alike:** these `visa*.json` assets (not the
app's source code, which stays GPL-3.0) are distributed under CC BY-SA 4.0 as well, and
anyone reusing them must keep that licence and this attribution. No endorsement by
Wikipedia, its contributors or the dataset's author is implied.

## Natural Earth (public domain)

Map geometry (`places.geojson`, `places.json`, `places_labels.geojson`) derives from
Natural Earth, <https://www.naturalearthdata.com/>. Natural Earth is in the public
domain; no attribution is required, and it is given here as a courtesy.

## Country flags (public domain)

The flag images in `assets/flags/` are rendered from the SVGs of
hampusborgos/country-flags, <https://github.com/hampusborgos/country-flags>
(pinned to commit `c09927e63705529bbf59ca6684cd9b23225dddad`), whose sources are
Wikimedia Commons. The flags are in the public domain; no attribution is required,
and it is given here as a courtesy.

## Noto Sans (SIL Open Font License 1.1)

Map labels use glyph atlases generated from Noto Sans Regular,
Copyright 2022 The Noto Project Authors
(<https://github.com/notofonts/latin-greek-cyrillic>), licensed under the SIL Open
Font License, Version 1.1. The licence text ships next to the glyphs, in
`app/src/main/assets/glyphs/Noto Sans Regular/OFL.txt`.

## MapLibre Native (BSD-2-Clause)

Map rendering uses MapLibre Native for Android,
<https://github.com/maplibre/maplibre-native>. Its licence text is in
`app/src/main/assets/licenses/maplibre-gl-android-LICENSE.txt`.

## AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache-2.0)

The app is built on AndroidX libraries, Jetpack Compose and Material 3, Room, and
kotlinx.coroutines, all licensed under the Apache License 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>).

## Auswärtiges Amt travel-warning data

The visa build reads the German Federal Foreign Office's travel-warning open data
(<https://www.auswaertiges-amt.de/opendata/travelwarning>) only to learn each
country's content id, from which it generates the link to that country's official
travel-advice page. No advice text is copied into the app. The Foreign Office
publishes no formal licence for this feed; its interface page sets terms of use
(information taken over complete and current, clearly attributed to the Auswärtiges
Amt, no guarantee of accuracy, and a request-and-accept procedure for access). The
links in the app are attributed to the Auswärtiges Amt. This project has not
requested or recorded a separate agreement with them.

## Icons

The vector icons under `app/src/main/res/drawable/` are the project's own, except as
noted in `ASSET_SOURCES.md`.
