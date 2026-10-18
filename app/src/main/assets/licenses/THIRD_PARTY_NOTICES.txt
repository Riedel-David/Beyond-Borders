# Third-party notices

Beyond Borders itself is licensed under the GNU GPL v3 (see `LICENSE`). It bundles
or links the following third-party material, each under its own licence. Where a
data asset is generated, `ASSET_SOURCES.md` records how.

## Passport Index Data (MIT)

The `visa.json` and `visa_<iso2>.json` assets are derived from the "Passport Index
Data" repository, <https://github.com/imorte/passport-index-data> (file
`passport-index-tidy-iso2.csv`, as of 2026-02-17), which in turn says its data is
scraped from passportindex.org. The repository's `LICENSE` is reproduced here as
published:

```
MIT License

Copyright (c) 2019

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Caveats, stated plainly. The upstream `LICENSE` names no copyright holder (the line
reads "Copyright (c) 2019" with no name), so none is shown here. The MIT licence is
the repository's licence; it is not a statement about the underlying facts, which the
fork says it scraped from passportindex.org, and this project has not verified
whether that site's terms permit redistribution. The older dataset this fork
continues, `ilyankou/passport-index-dataset`, was published under CC BY-SA; the
fork's MIT licence is taken at face value, not independently confirmed to supersede
that. Visa answers are also hand-corrected and extended in `tools/visa/rules.py`.

## Natural Earth (public domain)

Map geometry (`places.geojson`, `places.json`, `places_labels.geojson`) derives from
Natural Earth, <https://www.naturalearthdata.com/>. Natural Earth is in the public
domain; no attribution is required, and it is given here as a courtesy.

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
