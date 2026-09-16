# Beyond Borders

A personal Android app for tracking where I have been and where I can go without
a visa.

It exists because of one specific annoyance. Every travel tracker I tried selects
more than you ask for — tap Denmark and you get Greenland, tap France and you get
French Guiana. Beyond Borders is built on a dataset where those are separate places
and on a data model with no parent-child link at all, so a cascade is not a bug
that was fixed, it is a thing the code cannot express.

See [PLAN.md](PLAN.md) for the full reasoning, the decisions and the phase plan.

## Status

| | |
|---|---|
| Travel map | working — 252 places, tap to select, Been / Lived / Want, a visa lens, and a region-completion heatmap |
| Passport list | working — search, filters, per-continent stats |
| Passport visas | working — where any of ~195 passports goes, offline; DE gets a full 252-place curated answer |
| Home-screen widget | working — configurable per instance: headline metric and card background |
| Settings | working — theme, map colours, JSON export / import, emergency full data reset |
| First run | working — a welcome screen on first launch |

Everything works **offline**: the geometry is bundled in the APK and there is no
basemap, no tile server and no API key. A flight tracker was built through phase
3.6 and then removed — see [PLAN.md](PLAN.md) §1 for why.

Every scoped phase is done (version `0.7.5`). Nothing is currently planned; see
[STATUS.md](STATUS.md) for what that means and [PLAN.md](PLAN.md) §6 for the
ideas that aren't scheduled.

## Choosing the map colours

Settings → **Map colours** repaints Been, Lived and Want to. Six presets each, not a
colour picker: the reason this app has a hand-picked palette at all is that the four
map states have to stay tellable apart, and a free picker can land on three shades
of one hue just as easily as Material You can.

Two things follow from that, both deliberate:

- **Picking a colour another status holds swaps the two.** With six colours and
  three statuses, refusing the tap would leave you to work out unprompted that the
  *other* status has to move first. A swap always works and is its own undo, so the
  three colours are distinct by construction rather than by validation.
- **Each preset is a light/dark pair.** A single hex would look chosen-for-daylight
  the first evening you saw it.

The palette's claim to be readable is a test rather than a promise:
[`MapColorsTest`](app/src/test/java/com/riedel/beyondborders/settings/MapColorsTest.kt)
computes each preset's WCAG contrast against unmarked land and open water in both
themes and fails below 3:1, and separately checks that no two presets sit within 40°
of each other on the colour wheel — because contrast says a fill is *visible*, not
that two fills are *distinguishable*. Adding a seventh colour breaks the build until
it earns its place.

## Where the passport gets you

Passport → **Where I can go** answers the other half of the question the map answers,
entirely offline, for any passport in a searchable picker over all ~195 Passport Index
passports — pick one on first launch or switch it later in Settings. A German passport
gets the full, hand-curated 252-destination answer; every other passport gets a
generic, programme-based answer (EU membership, the UK's Common Travel Area, and six
reference-country groups) wherever a rule applies, and an honest gap where none does.
Both the map and the list paint that gap grey and say so — a place this passport's
dataset has no answer for is not the same thing as "visa-free" or "closed", and used to
be indistinguishable from either. The map has a matching **Visa** toggle that repaints
the same map by requirement instead of by your own history; tapping its legend expands
the dots into one labelled row per band.

The interesting part is not the dataset — it is the ~53 places per passport that the
dataset does not cover. The Passport Index matrix is 199 sovereign states; Beyond
Borders has 252 places, because the whole point of this app is that Greenland is not
Denmark. So those are hand-written in [`tools/visa/rules.py`](tools/visa/rules.py) as
twelve shared **programs** (EU freedom of movement, the UK's Common Travel Area, and
so on), one entry at a time, and they are exactly the ones other apps get wrong:

- **Greenland** is outside the EU *and* outside Schengen — a Schengen visa is not valid
  there, and an ID card is not enough.
- **French Guiana** *is* the EU with full freedom of movement, but outside Schengen, so
  expect a passport check on a domestic flight from Paris. No 90-day limit, because
  freedom of movement does not have one.
- **American Samoa** does not accept an ESTA even though Guam does.

Every one of those carries a note written for a traveller and a **confidence** the
app displays — a handful per passport are marked *uncertain*, and
the app says so on the row rather than presenting them all in the same authoritative
voice. Every destination links to the IATA Travel Centre; a German passport's rows
also link to their Auswärtiges Amt travel-advice page — that link-out is DE-only, since
there's no equivalent official feed wired up for anyone else. Every visa surface
carries *"data as of <date> for a [passport] passport — verify officially before
booking"*, whichever passport is currently chosen. A bundled dataset is a navigation
aid, not an authority.

The map's lens uses **five** bands rather than all eight categories, coloured
categorically — green for visa-free, blue for an ETA, orange for on arrival, red for
apply-first, grey for closed — rather than one hue at several intensities. That pairing
of green and red is exactly what an intensity-ordered scale would rule out, since hue
is the channel colour-vision deficiency takes away; the fix is pushing every pair,
green/red included, to opposite ends of the lightness range that still clears 3:1
against land, so the pair stays tellable apart under simulated protanopia,
deuteranopia and tritanopia even when hue collapses.
[`VisaRampTest`](app/src/test/java/com/riedel/beyondborders/ui/theme/VisaRampTest.kt)
asserts that pairwise, for every one of the ten band pairs, plus the same bar for the
grey used for the honest-gap places above. The exact answer — "e-Visa · 30 days" — is
text in the list, where an exact answer belongs; the map is for "roughly, where can
I go".

The map's other lens is a **region heatmap**: tap to cycle through off, official
continents, and informal named groupings, and every region repaints by how much of
it you've been to — none, some, most, or all of it. It uses a two-hue ramp rather
than one hue at four intensities, for the same colour-vision-deficiency reason as the
visa lens. Region and named-group sets aren't known at style-build time the way the
five visa bands are, so the map still only ever holds four heatmap layers — one per
completion band — and switching grouping or region is a `setFilter` call, not a style
rebuild.

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

## Build and run

Requires Android Studio (for its bundled JDK 21) and an emulator or device with
API 30+.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:testDebugUnitTest        # 167 tests, no device needed
./gradlew :app:connectedDebugAndroidTest # 9 tests, needs a device: DB migrations, deviceId storage
./gradlew :app:installDebug              # build + install
```

The unit tests are the useful ones to run first: they read the shipped geo and visa
assets directly and assert the territory splits, the visa coverage and the colour
contrast, so they catch a broken dataset without launching anything.

## Getting the data out

Settings → **Export** writes every place you have marked to a JSON file through the
system file picker. It is plain text, sorted by code, and small enough to read:

```json
{ "code": "GL", "name": "Greenland", "status": "visited",
  "updatedAt": 1786536430561, "deviceId": "019ff5dc-…" }
```

**Import merges, it does not restore.** Each entry is applied only if it is newer
than what is already on the phone, so importing an old backup cannot undo newer
trips, and importing the same file twice does nothing. Un-markings travel as
tombstones so they propagate too, and a place code this build does not know is
skipped and named rather than stored. The rules are in
[`data/backup/BackupMerge.kt`](app/src/main/java/com/riedel/beyondborders/data/backup/BackupMerge.kt)
as a pure function.

**Moving to a new phone needs no file.** Android's own Auto Backup is left on, so if
you have backup enabled the marks, the theme and colours, the passport and the widget
config come back when the app is installed on the new device. The app never uploads
anything itself — it asks for no network permission at all — that copy is made by the
system, to your own Google account, and Android's backup settings are where to switch
it off. The one thing deliberately left out is the per-install id the merge uses to
tell this device's writes from another's; restoring it would give two installs the
same id, so it is regenerated instead.

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
and why — live in [`tools/geo/rules.py`](tools/geo/rules.py). Read that file
before changing anything about the place list.

## A note on OneDrive — historical

The repo used to live inside a OneDrive folder and no longer does. That removed
three Gradle workarounds from `gradle.properties` (file-system watching off,
configuration cache off, project cache relocated) along with two failure modes worth
recognising if you ever see them again: sync-conflict forks named `Foo 2.kt` or
`Foo-David's MacBook Air.kt` that Kotlin compiles while `git status` stays clean, and
one tracked file silently truncated to 0 bytes (`notes.md`). Both were triggered by
two edits to the same file in quick succession.

`.gitignore` still carries `* 2.*` from that era. It is harmless and guards nothing
now.

## Data and licences

- Boundaries: [Natural Earth](https://www.naturalearthdata.com/) `admin_0_map_units`,
  public domain.
- Visa requirements: [Passport Index Data](https://github.com/imorte/passport-index-data),
  MIT. One passport row (DE or GB), plus ~53 hand-curated territory answers of our own.
- Travel-advice links: the German Federal Foreign Office
  ([Auswärtiges Amt](https://www.auswaertiges-amt.de/)) — links only, no content copied.
- Map rendering: [MapLibre Native Android](https://github.com/maplibre/maplibre-native),
  BSD-2-Clause. Note it requires Vulkan.

Not for publication — this is a personal-use build.
