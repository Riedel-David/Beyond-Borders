# Features in depth

The design reasoning behind the map colours, the passport answers and the data
export. For the short version, see the [README](../README.md).

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
[`MapColorsTest`](../app/src/test/java/com/riedel/beyondborders/settings/MapColorsTest.kt)
computes each preset's WCAG contrast against unmarked land and open water in both
themes and fails below 3:1, and separately checks that no two presets sit within 40°
of each other on the colour wheel — because contrast says a fill is *visible*, not
that two fills are *distinguishable*. Adding a seventh colour breaks the build until
it earns its place.

## Where the passport gets you

Passport → **Where I can go** answers the other half of the question the map answers,
entirely offline, for any passport in a searchable picker over all ~195 Passport Index
passports. A German passport gets the full, hand-curated 252-destination answer; every
other passport gets a generic, programme-based answer wherever a rule applies, and an
honest grey gap where none does.

The interesting part is not the dataset — it is the ~53 places per passport that the
dataset does not cover. The Passport Index matrix is 199 sovereign states; Beyond
Borders has 252 places, because the whole point of this app is that Greenland is not
Denmark. Those are hand-written in [`tools/visa/rules.py`](../tools/visa/rules.py), one
entry at a time, and they are exactly the ones other apps get wrong:

- **Greenland** is outside the EU *and* outside Schengen — a Schengen visa is not valid
  there, and an ID card is not enough.
- **French Guiana** *is* the EU with full freedom of movement, but outside Schengen, so
  expect a passport check on a domestic flight from Paris. No 90-day limit, because
  freedom of movement does not have one.
- **American Samoa** does not accept an ESTA even though Guam does.

Every one of those carries a note written for a traveller and a **confidence** the app
displays — presented as uncertain rather than in the same authoritative voice as the
rest. A bundled dataset is a navigation aid, not an authority.


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
[`data/backup/BackupMerge.kt`](../app/src/main/java/com/riedel/beyondborders/data/backup/BackupMerge.kt)
as a pure function.

**Moving to a new phone needs no file.** Android's own Auto Backup is left on, so if
you have backup enabled the marks, the theme and colours, the passport and the widget
config come back when the app is installed on the new device. The app never uploads
anything itself — it asks for no network permission at all — that copy is made by the
system, to your own Google account, and Android's backup settings are where to switch
it off. The one thing deliberately left out is the per-install id the merge uses to
tell this device's writes from another's; restoring it would give two installs the
same id, so it is regenerated instead.

