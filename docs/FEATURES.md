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
entirely offline, for any passport in a searchable picker over about 200
passports. A German passport gets the full 252-destination answer (the dataset's rows plus hand-written answers for the places it lacks); every
other passport gets a generic, programme-based answer wherever a rule applies, and an
honest grey gap where none does.

The interesting part is not the dataset — it is the places per passport that the
dataset does not cover. The dataset follows Wikipedia's per-nationality articles, which
cover most territories only patchily; Beyond Borders has 252 places, because the whole point of this app is that Greenland is not
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

**Moving to a new phone needs no file.** Android's Auto Backup carries your places, not
the app's database: the app writes a small JSON snapshot of the history to its own
storage whenever it goes to the background, and the system copies that file to your own
Google account. After a reinstall, Settings shows **Restore from Android's backup**; it
opens the same preview an imported file gets, and nothing is applied until you confirm.
Theme, colours, passports and widget configuration are deliberately left out and start
fresh, as does the per-install id the merge uses to tell this device's writes from
another's. The app never uploads anything itself and asks for no network permission; the
copy is made by the system, and Android's backup settings are where to switch it off.

**Delete everything** (Settings) clears your places, both passports and the display
settings, and removes the staged snapshot so there is nothing for a restore to offer.
Google may still hold an earlier backup until Android next runs one, so a reinstall
before that can bring the places back; Android's backup settings can delete that copy.

## The stats screen, share cards and widget

The passport card's headline number opens a stats screen: a bar per continent, the
least-covered continent and the next milestone, and a grid of achievement badges
(counts, continents, regions, named groupings, territories, tiny places). Badges are
derived from the current map, so unticking a place can take one away again; they ignore
the Denominator setting and store nothing of their own. Each badge group has an icon and
colour, and Regions and named groupings have their own headers ("Regions · 3 of 28") that
expand in place to show the locked ones; earned badges are always visible. The hero card at the top shows
the share of the world as a ring and a traveller rank (Newcomer up to Legend), which
counts every place visited, like the badges. Above the badge groups, "Closest to unlocking" shows the three nearest locked badges and "Recently unlocked" tags new ones; the first visit after an unlock also shows a one-time message. Which badges were already seen is kept on the device only. Tapping a badge opens its details with progress, and an unlocked badge can be shared as a card with its icon, description and your traveller rank; on a phone held upright the card takes the screen's shape (up to
9:21), so a story shows no black bars. **Share** renders the map as an image in
three styles: a caption bar, a corner badge, or a numbers-only stats card.

The home-screen widget shows one headline number (countries, percent of the world or
continents) and grows with its size: a corner logo and number when small, the wordmark
when narrow but tall, a progress bar at 3×2 with the three stat tiles in a column beside
it, and the tiles in a row beneath at 4×3. A box wide enough for tiles but too short for
the progress bar (a 3×2 on HyperOS, for one) gets a strip instead: the number with three
one-line tiles beside it. Tapping a countries widget opens the map; percent and
continents widgets open the stats screen.

## The selected place

Tapping a place on the map shows it in a banner: its flag as a thumbnail beside the
name, on a background tinted in the flag's own colours. The flags are bundled, so this
works offline; the two places without one (Northern Cyprus, Somaliland) get a plain
dark banner. The Share button steps aside while a place is selected.
