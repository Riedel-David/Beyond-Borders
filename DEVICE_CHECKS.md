# Device checks — the UI round (§6, §7, §8, §9)

## What the first pass on the phone found

- **Too much room above the "Stats" title.** Double insets: `BeyondBordersRoot.kt:122`
  already wraps the passport tab in `Box(...padding(padding))`, so the status bar is
  consumed before `PassportScreen` runs, and `StatsScreen`'s own `Scaffold` +
  `TopAppBar` then added it again. Fixed with `WindowInsets(0, 0, 0, 0)` on both.
  Pre-existing — the old `StatsDetailScreen` had the same `Scaffold` — so it was never
  introduced by this round, only exposed by it. **Re-check after the next install.**
- **No way to scroll the 252 rows by letter.** Not a bug: `FastScrollOverlay` is
  display-only, and §5 left the A–Z rail unbuilt on the guess that the pill would make
  it unnecessary. That guess was wrong. Rail now being built on
  `feat/passport-az-rail`: A–Z grouping only, tap plus drag-to-scrub, both lists.
  Lesson worth keeping: "the cheaper indicator may remove the need for the real
  control" is a guess, and this one cost a round trip to disprove.

The round is merged to `main` at `0.7.12` / versionCode 18, 225 host tests green. This
list is **not** worked through — it is the outstanding device pass, kept in the repo so
it survives a cleared session. Delete it once the boxes are ticked.

Nothing below is verified by the suite; the host JVM cannot see any of it. Items marked
**flag** are judgement calls an agent or I made without a screen, so they are the ones
most likely to be wrong. Two of the checks below have already been fixed once as a
result of a first pass — see the section immediately following.

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

## Second install — A–Z rail polish on fix/az-rail-polish

First pass found two defects (bare letters reading as stray text against the screen
edge, and no pill tracking the finger during a scrub). Both fixed on this branch.

- [ ] Stats title now sits a normal distance below the status bar, not a doubled one.
- [ ] A–Z rail: pick the A–Z chip on the travel list. Rail appears at the right edge
      on a pill-shaped container (surfaceContainerHigh, 28 dp wide, 8 dp vertical
      padding, 8 dp inset from right edge).
- [ ] Container reads correctly in both light and dark modes.
- [ ] Tap a letter — list jumps to it.
- [ ] Press and slide — list follows the finger continuously.
- [ ] During a scrub: a large pill to the **left** of the rail tracks the finger,
      vertically centred on it, showing the letter under it. The pill appears
      instantly when the finger lands (no fade-in) and names the correct letter.
- [ ] The top-right FastScrollOverlay pill and the scrub pill **never appear at once**.
      The top-right pill is suppressed during a scrub, only visible during flings.
- [ ] Top and bottom letters are still reachable after the container padding change
      (verify by scrubbing to A and Z — the letterAt arithmetic must account for the
      padding or the mapping skews).
- [ ] Drag past the top and past the bottom of the rail: no crash, pill clamps inside
      the available height.
- [ ] Switch to Continent or Region grouping — the rail is **gone**, not greyed out.
- [ ] The rail is invisible to TalkBack by design. Confirm the list is still fully
      navigable by section header with TalkBack on.
- [ ] **Overflow check**: 26 letters × 16 dp plus 16 dp padding is ~432 dp. On a small
      screen or in landscape, the rail may exceed the list's available height. Note
      whether this occurs and whether it is acceptable or needs adaptive sizing.

## §6 Stats screen — the Canvas, so this is the part the rule is about

Reach it by tapping the passport card (the caption that used to say "Tap for
continents…" is gone, a chevron replaces it).

- [x] Ring draws at all, and the percent sits centred *inside* it — not clipped by
      the stroke, not overlapping it. **Confirmed by eye in one state; edge cases
      (0%, 1–3%, 100%) and dark mode remain unverified.**
- [x] Ring metrics look right: 168 dp diameter, 14 dp stroke. Both are one-line
      constants at the top of `StatsScreen.kt` if you want them different. **flag** —
      I picked these blind. **Confirmed visually correct.**
- [x] Sweep starts at twelve o'clock and fills clockwise. **Confirmed.**
- [ ] **Low percentage** (a fresh-ish install, 1–3%): the `StrokeCap.Round` cap does
      not render as a stray blob or bulge larger than the arc it caps.
- [ ] **100%**: no seam, gap or overdraw where the sweep meets its own start.
- [ ] **0%**: no stray cap dot at twelve o'clock when nothing is marked.
- [ ] Track vs sweep still reads in **dark mode** (`surfaceVariant` under `primary`).
- [ ] "Least covered" and "Next up" render as label-above-value, not inline
      prefixes. "Next up" keeps the primary accent, "Least covered" is neutral.
- [ ] Fresh install with nothing marked: *neither* line appears (no lone label).
- [ ] "Show all groupings" — expand **and collapse** both read as smooth. Collapsing
      is the direction that read as a cut the last time this project animated a
      size change.
- [ ] Long-press a bar inside the newly-revealed Regions / Named groupings grids —
      the "which places are missing" dialog still opens there, not just in Continents.
- [ ] Chevron is vertically centred against the headline number, not the whole card.

### TalkBack on the stats card — this was a real regression, sent back once

- [ ] Swipe through the passport card: the continent count, the land-area/population
      coverage line and the nudge are each **still announced individually**. The first
      version replaced all of them with one sentence; confirm the fix took.
- [ ] The card announces the action ("View continents, regions and groupings").
- [ ] The ring announces **one** sentence, not the Canvas plus both texts separately.

## §8 Recovery screen — the one verified bug in the round

Hard to reach on purpose: it only appears when Room cannot open the database. If you
have no way to force it, say so and I will find one — leaving it unchecked is the
worst option, since it is the screen that appears in the app's worst moment.

- [ ] Headline clear of the status bar, buttons clear of the nav bar. This is the bug
      that was verified in code: no `Scaffold` under `enableEdgeToEdge()`.
- [ ] **flag** — content is top-aligned, not centred (the agent kept the original
      behaviour). Does a short recovery screen now sit awkwardly high?
- [ ] With nothing to save: "Save a copy" disabled **and** a line saying why.
- [ ] Tap Save: spinner appears, button does not change size as the label swaps.
- [ ] Double-tap Save fast: only one save starts.
- [ ] Force a failure if you can: the button comes back rather than staying dead.

## §7 Settings — the danger zone

- [x] The Danger zone card is distinguishable from the neutral sections in **both**
      light and dark. **flag** — it uses `surfaceContainerHigh` against the other
      cards' `surfaceContainerLow`, which is a subtle step and the agent's own call.
      **Confirmed in the mode checked; dark mode remains unverified.**
- [x] Error-coloured border on the card and on "Delete everything"; the ripple
      carries the warning too, not just the label text. **Confirmed.**
- [x] It reads as *dangerous*, not as *broken* or shouting. Settings is a screen you
      open for ordinary reasons. **Confirmed.**
- [ ] The confirmation flow behind the button is unchanged — it should still demand
      the name.

## §9 Settings — your travel data

Needs a real export file to import, so this one takes setup.

- [ ] Import preview shows three rows — Added / Updated / Skipped — instead of the
      old prose sentence.
- [ ] Under them: "e.g. Andorra, Denmark, and 2 more." The sample names were dropped
      in the first version and put back; confirm they are actually there.
- [ ] The rarer caveats (unknown codes, unreadable rows) still appear when they apply.
- [ ] One line of supporting text under Export/Import, not two paragraphs.
- [ ] About now carries the Auto Backup explanation, and still says all three things:
      the *system* makes the copy, the per-install id is excluded, and where to turn
      it off.

## §9 Map, visa, welcome

- [ ] Select **Sint Maarten** (or Bonaire, Curaçao — anything with a long sovereign):
      the card shows the sovereign across two lines instead of ellipsising it away.
      "Kingdom of the Netherlands" must be readable.
- [ ] Switch between a short selection (Chad) and a long one: the card height does
      **not** jump. That is what `minLines = 2` is for.
- [ ] Haptics: seven sites now buzz — the ⋮ pill, search, visa lens, regions, both
      branches of the legend/counter pill, and the share FAB. **flag** — is the ⋮
      expand one too many? It is arguably navigation, not an action.
- [ ] A visa entry with LOW confidence: warning glyph aligned with the *first* line
      of its text, and the caveat visibly outranks the "as of" line beneath it.
- [ ] **flag** — that gap comes from the warning growing to `labelMedium`, not the
      caption shrinking (`labelSmall` is the floor of the scale). If it reads as
      "warning too big" rather than "caption quieter", say so and I will use an
      explicit `fontSize` instead.
- [ ] Welcome screen CTA is a filled button, not tonal. Needs cleared data to see.

## Cross-cutting

- [ ] **The passport list fling.** Still unverified from the previous session, and
      `PassportScreen.kt` changed again in this round (chevron, card semantics). Fling
      the 252-row list hard and confirm it does not stall.
- [ ] A dark-mode pass over all four areas.
- [ ] The About card shows `0.7.12`.
