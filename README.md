# AeroGlobe

A personal Android app for two things: tracking where I have been, and keeping an
eye on a flight I am about to take.

It exists because of one specific annoyance. Every travel tracker I tried selects
more than you ask for — tap Denmark and you get Greenland, tap France and you get
French Guiana. AeroGlobe is built on a dataset where those are separate places
and on a data model with no parent-child link at all, so a cascade is not a bug
that was fixed, it is a thing the code cannot express.

See [PLAN.md](PLAN.md) for the full reasoning, the decisions and the phase plan.

## Status

| | |
|---|---|
| Travel map | working — 252 places, tap to select, Been / Want, zoom to place |
| Passport list | working — search, filters, per-continent stats |
| Settings | working — theme, opening screen, JSON export / import |
| Flights | not started (phase 3) |

Everything that works, works **offline**: the geometry is bundled in the APK and
there is no basemap, no tile server and no API key.

Next up is the short list in [PLAN.md](PLAN.md) phase 1.5 — map card cleanup, a
*lived there* category, custom category colours and a real launcher icon — then
visas in phase 2.

## Build and run

Requires Android Studio (for its bundled JDK 21) and an emulator or device with
API 26+.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:testDebugUnitTest    # 52 tests, no device needed
./gradlew :app:installDebug         # build + install
```

The unit tests are the useful ones to run first: they read the shipped geo assets
directly and assert the territory splits, so they catch a broken dataset without
launching anything.

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
[`data/backup/BackupMerge.kt`](app/src/main/java/com/riedel/aeroglobe/data/backup/BackupMerge.kt)
as a pure function, and they are the same rules phase 5's optional sync will use.

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

## A note on OneDrive

The repo currently lives inside a OneDrive folder, which Gradle dislikes. Three
workarounds in `gradle.properties` deal with it: file-system watching off,
configuration cache off, and the project cache relocated outside the synced
folder. Each is commented in place, and all three can be deleted if the repo moves
to a local path.

OneDrive has also produced sync-conflict copies of source files
(`Foo-David's MacBook Air.kt`) after concurrent edits. If Kotlin suddenly reports
redeclarations, look for one of those before believing the error.

## Data and licences

- Boundaries: [Natural Earth](https://www.naturalearthdata.com/) `admin_0_map_units`,
  public domain.
- Map rendering: [MapLibre Native Android](https://github.com/maplibre/maplibre-native),
  BSD-2-Clause. Note it requires Vulkan.

Not for publication — this is a personal-use build.
