# Open items: going public

Already done: fixed release keystore wired into `release.yml` and
`app/build.gradle.kts`; v2/v3 signing comes automatically with `minSdk = 30`;
LICENSE (GPLv3), SECURITY, CONTRIBUTING, PRIVACY and ASSET_SOURCES exist.

## Done
README restructure, Play Protect step, feature-request template, bug-report typo,
CONTRIBUTING conventions, stale Play docs updated.

## Decided
- Release notes on GitHub only, no CHANGELOG.md.
- No CI workflow; release.yml runs the tests.
- Upload the existing release key to Play App Signing at the first upload (cannot be
  changed afterwards).

## To do
1. Screenshots are done (README has six). Greenland/Denmark and widget shots were
   deliberately skipped.
2. Enable private vulnerability reporting in the repo settings so the `SECURITY.md`
   advisory link works.
3. Play Console: 512x512 icon, 1024x500 feature graphic, short (80 char) and full
   description, Data safety form (no data collected), content rating, privacy policy
   URL (GitHub link to docs/PRIVACY.md).
4. GitHub repo description and topics.
5. Dry-run the release workflow on a tag: download the APK, install it on a phone.
6. Map labels: ~90 small places still only name themselves at zoom 4+; revisit
   LABEL_WIDTH_MARGIN (now 0.5) only if it bothers.
