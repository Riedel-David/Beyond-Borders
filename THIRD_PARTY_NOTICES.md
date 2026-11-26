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
Foreign Office's travel advice (see below). **Share-alike:** these `visa*.json` assets (not the
app's source code, which stays GPL-3.0) are distributed under CC BY-SA 4.0 as well, and
anyone reusing them must keep that licence and this attribution. The upstream dataset states the same terms in its `LICENSE-DATA.md` (CC BY-SA 4.0 for `data/`, MIT for its code) and every passport file it publishes carries `"license": "CC BY-SA 4.0"`; the assets here carry the licence name and URL in `dataset.licence` and `dataset.licenceUrl`, and the app's visa screen shows them. The advisory data below is not Wikipedia-derived and is not covered by this section. No endorsement by
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

## Inter (SIL Open Font License 1.1)

The app's text is set in Inter, bundled as `app/src/main/res/font/inter_regular.ttf`,
`inter_medium.ttf`, `inter_semibold.ttf` and `inter_bold.ttf`. Inter is
Copyright 2016 The Inter Project Authors (<https://github.com/rsms/inter>), licensed
under the SIL Open Font License, Version 1.1, available with a FAQ at
<https://openfontlicense.org>. The fonts' own metadata names version 4.001. The licence text ships in
`app/src/main/assets/licenses/Inter-OFL.txt`.

## MapLibre Native (BSD-2-Clause)

Map rendering uses MapLibre Native for Android,
<https://github.com/maplibre/maplibre-native>. Its licence text is in
`app/src/main/assets/licenses/maplibre-gl-android-LICENSE.txt`.

## AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache-2.0)

The app is built on AndroidX libraries, Jetpack Compose and Material 3, Room, and
kotlinx.coroutines, all licensed under the Apache License 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>).

## Travel advisories (`advisory.json`)

The `advisory.json` asset and its weekly updates carry, for each country, the current rating
of three governments, the date of that rating and a link to the country's page on the
government's own site. The build is `tools/advisory/build_advisory.py` (see `ASSET_SOURCES.md`).
The app shows these side by side and does not combine them; it is not affiliated with any of
the three governments, and the ratings are a snapshot that can be out of date.

**US Department of State** (<https://travel.state.gov/>). Travel advisories are works of the
US federal government and in the public domain. The US label is the feed's own wording.

**UK Foreign, Commonwealth & Development Office** (<https://www.gov.uk/foreign-travel-advice>).
Contains public sector information licensed under the Open Government Licence v3.0,
<https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/>. Changes made
here: the app's label is a short summary the build writes from the feed's alert-status flags
(for example "FCDO advises against all travel"), not FCDO text, and only the strongest advice
is stated; the linked FCDO page has the full advice.

**Auswärtiges Amt** (German Federal Foreign Office), *Reise- und Sicherheitshinweise*, from its
open-data interface (<https://www.auswaertiges-amt.de/opendata/travelwarning>). The app shows
the Auswärtiges Amt's warning status for each country (a travel warning, a partial warning or
none) as a short label the build writes from the feed's `warning` and `partialWarning` flags,
the date of the feed entry, a link to the Auswärtiges Amt's page for that country and, where
the page says so, up to three of its own sentences advising against travel ("… wird abgeraten"),
quoted word for word. The rest of the advice text is not copied: the complete Reise- und
Sicherheitshinweise are on the Auswärtiges Amt's website. The Auswärtiges Amt publishes no formal licence for this feed;
its interface page
(<https://www.auswaertiges-amt.de/de/service/opendata/open-data-schnittstelle-736118>) allows
the information to be taken over on conditions: it is taken over complete and kept current
through the interface, it is clearly recognisable as coming from the Auswärtiges Amt, and it is
not shown altered, incomplete, outdated or in a distorting context (the Auswärtiges Amt
reserves the right to withdraw permission otherwise, gives no guarantee of accuracy, and
asks users of the interface to contact it via <https://www.diplo.de/kontakt>, naming the
application and confirming acceptance of these terms). Beyond Borders is a free app, so the
extra rules for paid apps do not apply. The app refreshes the data weekly on an unmetered
network and shows the date of the data on screen. It takes over only the warning
status, those quoted sentences and a link to the rest, whereas the terms speak of information taken over complete, so
whether this use is accepted is for the Auswärtiges Amt to say. Confirmation with the
Auswärtiges Amt: pending (the request has not been sent yet).

## Icons

The vector icons under `app/src/main/res/drawable/` are the project's own, except as
noted in `ASSET_SOURCES.md`.
