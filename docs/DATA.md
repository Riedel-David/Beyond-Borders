# Data and regenerating assets

The bundled assets are committed, so a normal build never needs any of this.
Sources and checksums are recorded in [ASSET_SOURCES.md](../ASSET_SOURCES.md).

## Regenerating the visa asset

There is
deliberately no in-app refresh: a new publication has to be re-reviewed against the
curated territory answers, which is a review rather than a download.

The source is the Visa-requirements dataset
(<https://riedel-david.github.io/Visa-requirements/>, CC BY-SA 4.0, derived from
Wikipedia). The build downloads the latest publication every time, so the
`generated_at` date and the per-passport Wikipedia revisions are rewritten into
`docs/visa-source-revisions.txt` by a full build, and nothing else needs updating by
hand for provenance.

```sh
mkdir -p tools/visa/cache
curl -sL -o tools/visa/cache/aa-travelwarning.json https://www.auswaertiges-amt.de/opendata/travelwarning
python3 tools/visa/build_visa.py                  # every passport (downloads the dataset first)
python3 tools/visa/build_visa.py --passport GB --source tools/visa/cache/visa-requirements
                                                    # just visa_gb.json, from the cached dataset
python3 tools/visa/build_visa.py --source ~/Visa-requirements/data   # offline, from a checkout
```

After rebuilding, refresh the recorded hashes as described in
[ASSET_SOURCES.md](../ASSET_SOURCES.md).

### The weekly refresh pull request

`.github/workflows/visa-refresh.yml` runs these same steps every Monday at 07:00 UTC (after
Visa-requirements republishes), regenerates `docs/visa-checksums.txt`, and, only when the
data differs from `main` beyond the `asOf` date and the per-passport `sourceRevision` (Wikipedia revision drift alone opens nothing; with no change an open data PR is closed), opens one `data/visa-YYYY-MM-DD` pull
request listing the changed rows. A failed build opens nothing. Merging it does not release
anything. The manual steps above remain the fallback (for example when the job is red or
the territory CORRECTIONS need a re-review); the workflow needs "Allow GitHub Actions to
create and approve pull requests" enabled under Settings > Actions > General.

It refuses to write unless every one of the 252 places resolves to exactly one answer
and every rule is still live — so the day the dataset starts covering a territory
itself, the build says the override is dead instead of preserving a stale opinion
forever. The dataset's `special` status (tourist card, invitation, partial bans such as
"Travel banned in most areas", ...)
is its own "Check requirements" category (a violet band on the map) at low confidence,
with the dataset's note (cut to about 280 characters at a sentence end, `NOTE_MAX_CHARS`) or,
when it has none, a generic "check the official requirements" note.

Schema 1.1.0 adds the status `travel_ban`: the traveller's *own* government forbids
travel there (these rows used to be `special`; `allowed_stay_days` is always null, the raw
text is in `allowed_stay_raw`). It maps to the same CHECK category at low confidence, with
the upstream note or, when it has none, `TRAVEL_BAN_FALLBACK_NOTE` (English only, like the
special fallback: the note is data in the asset, not a string resource). A combined cell
such as "eVisa / Travel banned" is `travel_ban` with the destination's own rule in
`alternative_statuses` (for example `["evisa"]`); unlike other combined cells, `status` is
**not** the least restrictive option there. The shipped note always leads with the ban
statement, then the upstream note. The build never reads the alternatives of a `travel_ban`
row, so it cannot become less restrictive than CHECK. The app never sees `alternative_statuses` at all; its
two-passport combine works on the shipped category, where CHECK loses to any definite visa
path of the other passport. The build accepts any 1.x `schema_version` (1.0.0 and 1.1.0
both build); `status.json` (a freshness file) is not read by the build.
Upstream files ESTA and Canada's eTA under `visa_free`;
`ETA_RULES` in `rules.py` turns those back into authorisations (the ESTA match skips the notes that say
who is *exempt*, `ESTA_NEGATIONS`; a note that mentions ESTA but matches neither that nor
`ESTA_REQUIRED` fails the build). The sovereign-gap exceptions are written to
`docs/visa-gap-allowlist.txt`, which the coverage test reads.

Two more guards: whole states that upstream files under an area such as "and
territories" are read as the state (`WHOLE_SOVEREIGN_AREAS`), and the build fails if a
sovereign place has no dataset row for a passport unless it is a named gap
(`SOVEREIGN_GAP_*`); and a passport with fewer than `MIN_DATASET_ROWS` (150) dataset
answers is not shipped at all (Northern Cyprus, whose article gives one).

A fallback rule that no passport needs any more (an unused `GENERIC_RULE` place,
`IGNORED_DESTINATIONS`, `GAP_FILLS` or ETA rule) is only a `WARNING:` line, repeated in the
job summary of the scheduled refresh: it changes no output, so upstream catching up must not
turn the weekly job red. Stale `SOVEREIGN_GAP_*` entries still fail the build, because the
coverage test trusts them. A failed scheduled refresh opens (or comments on) a "Visa data
refresh failed" issue.

### Publication for the app's updater

The same workflow has a `publish` job that rebuilds the assets every Monday and deploys them to
GitHub Pages (Settings > Pages > Source = "GitHub Actions"): `manifest.json` plus one
`visa_<code>.json` per passport (the default passport's `visa.json` is published as `visa_de.json`),
assembled by `tools/visa/make_site.py`. It publishes whether or not a data PR is open or merged, so
installed apps are current without a release. It refuses to publish unless every file parses, names
its passport and has requirements, and at least 150 passports are present. The manifest's
`dataFormat` must be bumped together with any change to the shape of a visa file.

The app side lives in `app/src/main/java/com/riedel/beyondborders/data/visa/update/`; the manifest's
`dataFormat` is checked against `SUPPORTED_DATA_FORMAT` in `VisaUpdate.kt`. Pages must be set to
Source = GitHub Actions and the workflow run once (Actions tab, "Run workflow" on main) before a release,
or the app has nothing to fetch. The publish job rebuilds from upstream independently of the data PR, so
published data can be newer than what is on main until the PR merges. That is intentional: ship as
published, no app-side curation. `visa-freshness.yml` also checks the published `manifest.json` (reachable,
versions 1, generated within 10 days, at least 150 passports).

### Signing the manifest

The manifest comes from the same host as the data (GitHub Pages of this repo), so its sha256 values only
catch corruption. `manifest.sig` closes that: base64 of an ECDSA P-256 / SHA-256 signature (DER) over the exact
bytes of `manifest.json`, made in `visa-refresh.yml` by `tools/visa/sign_manifest.sh` with the private key in the
repository secret `MANIFEST_SIGNING_KEY`. The step fails (no skip) when the secret is missing or empty, and
verifies its own output before upload. `advisory-refresh.yml` carries `manifest.sig` together with
`manifest.json` when it redeploys the site; a manifest without its signature would make every new app reject
updates. The app (`ManifestSignature.kt`, `ManifestPublicKeys.kt`) checks the signature before it parses the
manifest. A missing or malformed signature counts as "manifest not recognised" and a signature that does
not verify as a failed check that is retried later (GitHub Pages caches each file separately for about ten
minutes, so right after a deploy a device can briefly get an old `manifest.json` with the new `manifest.sig`;
the user sees "offline"): either way nothing is downloaded or deleted, and bundled and stored data stay.
There is no unsigned fallback. An empty `MANIFEST_PUBLIC_KEYS` would reject everything.

One-time key setup (on your machine; the private key never goes into the repo):

```bash
openssl ecparam -name prime256v1 -genkey -noout -out manifest-key.pem
# public key as one base64 line (X.509 SubjectPublicKeyInfo, DER): this goes into ManifestPublicKeys.kt
openssl pkey -in manifest-key.pem -pubout -outform DER | base64 | tr -d '\n'; echo
gh secret set MANIFEST_SIGNING_KEY < manifest-key.pem
# store manifest-key.pem in the password manager, then:
rm -P manifest-key.pem   # or shred -u on Linux
```

The public key was generated on 2026-11-26 and is already in `ManifestPublicKeys.kt`; the private half is
the secret. An app that holds the key rejects an unsigned manifest, so the order matters (old apps ignore
`manifest.sig`, so steps 1 and 2 are safe for them):

1. Merge the PR that adds signing and the key.
2. Run "Visa data refresh" from the Actions tab, then "Advisory refresh". The first signs and publishes
   `manifest.sig`; the second must carry it along (`advisory-refresh.yml` refuses a run whose live
   manifest/signature pair does not verify).
3. Confirm the live signature verifies, by hand (`<public key base64>` is the string in `ManifestPublicKeys.kt`):

   ```bash
   curl -fsS https://riedel-david.github.io/Beyond-Borders/manifest.json -o m.json
   curl -fsS https://riedel-david.github.io/Beyond-Borders/manifest.sig | base64 -d > s.der
   echo '<public key base64>' | base64 -d | openssl pkey -pubin -inform DER -out pub.pem
   openssl dgst -sha256 -verify pub.pem -signature s.der m.json   # must print: Verified OK
   ```

4. Set the repository variable `MANIFEST_SIGNING_LIVE=true` (Settings > Secrets and variables > Actions >
   Variables), so `advisory-refresh.yml` refuses to carry a manifest that has lost its signature.
5. Only then cut any app release.

**Do not build a release from `main` until the live signature has verified (step 3).** The app holds the key,
so it rejects every unsigned or mismatched manifest and would never update visa data. From the moment the key
is listed, `visa-freshness.yml` also verifies the live signature against it.

Rotation: ship the new public key in an app release next to the old one, switch the secret once enough
installs have it, and drop the old key in a later release. `sign_manifest.sh` refuses a signing key that is not
in `MANIFEST_PUBLIC_KEYS`, so a wrong secret cannot publish an update that every
app rejects.

### Is a newer snapshot out?

`.github/workflows/visa-freshness.yml` runs every Monday, and on demand from the Actions
tab. It downloads the dataset's `index.json` and opens an issue (one per kind of problem, with a fixed title
per kind, none while one of that kind is open) only when a passport is added or removed upstream, the index
`schema_version` major changes, the index cannot be fetched or read, or the published
site's manifest is missing, older than 10 days, from another format version or has fewer than 150 passports. Wikipedia
revision drift (`source_revision` against `dataset.sourceRevision` in `visa*.json`) is
expected weekly and is only listed in the job summary. It changes nothing in the repo;
the weekly pull request above carries the data itself.

## Regenerating the geo assets

The three assets are committed, so a normal build never needs this. Run it only after
changing the curation rules or taking a new Natural Earth release.

```sh
mkdir -p tools/geo/cache
curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson

python3 tools/geo/build_geo.py
```

This writes `app/src/main/assets/places.geojson` (polygons, ~2.6 MB),
`places_labels.geojson` (label points, ~40 kB) and `places.json` (the registry, ~70 kB; besides names and areas it carries curated `landlocked` and `island` flags, which the achievement badges read). It fails loudly rather than shipping a
regression: the territory splits, the reverse merges and the continent buckets are
all asserted before anything is written.

The curation rules — what gets merged, dropped, renamed or given a private code,
and why — live in [`tools/geo/rules.py`](../tools/geo/rules.py). Read that file
before changing anything about the place list.


## Regenerating the travel advisories

`app/src/main/assets/advisory.json` holds the US State Department, UK FCDO and German Auswärtiges Amt
travel advisories side by side: per country and source a `stance` (0-3, a common key only), the source's `label`, an `updated` date and the `url` of its country page. The US label is the feed's own
wording; the UK and German labels are short summaries the build writes from the flags those feeds
publish. It is generated, never edited by hand.

The German feed's four flags only encode the formal *Reisewarnung* and *Teilreisewarnung*. The AA's
second tier, "Von Reisen nach X wird (dringend) abgeraten", appears only in each country page's text
(Georgia, Israel and North Korea carry it with every flag false), and the AA's terms forbid showing its
information incomplete or out of context, so the build also fetches every country's page
(`/opendata/travelwarning/<node id>`, cached in `tools/advisory/cache/de_detail/`). The DE entry maps:
`warning` is stance 3 "Reisewarnung"; `partialWarning` is stance 2 "Teilreisewarnung"; otherwise a
sentence about travelling ("Reise(n)", "Aufenthalte", "abgeraten") gives stance 2 "Keine formelle
Reisewarnung, aber Reiseabraten (siehe Hinweise)", and none gives stance 0 "Keine formelle
Reisewarnung". Text alone never reaches 3, so the headline count, "Sources advising against travel to the whole country", is the formal warning only.
Those sentences are the optional DE-only field `notes` (a list of at most 3 strings, each at most 1500
characters, verbatim from the page, de-duplicated). Absent `notes` means none; a parser must ignore
anything in it that is not a string. A note is never cut. A German note over
`DE_NOTE_MAX_CHARS` fails the build (naming the country: a human decides); a US or UK note over it is dropped with a warning naming source, country and length in the build log, and the build continues (the stance and label are unaffected). The limit is `DE_NOTE_MAX_CHARS` (1500, UTF-16 units; `MAX_ADVISORY_NOTE_CHARS` in the app is the same number), and the app drops one that is. It is an additive field, so `dataFormat` stays 1. The build refuses
(and writes nothing) if a page is missing for more than 10% of countries. A country with all flags false whose page is missing is
withheld (see the tripwire below), not an error.

The UK and German `label` texts are English and German whatever the app's language, so each such entry
also carries the optional `labelKey`, which names the text and lets the app show its own translation:
UK `uk_none`, `uk_all_whole`, `uk_essential_whole`, `uk_all_parts`, `uk_essential_parts`,
`uk_all_parts_essential_other`; DE `de_none`, `de_advised_against`, `de_partial`, `de_warning`. US
entries (the feed's own wording) have none. The app falls back to `label` for a missing, blank or
unknown key, so the field is additive and `dataFormat` stays 1. A new key needs a string in every
`values*/strings_advisory.xml`. Adding the field changes every UK/DE entry, so the first run after it
counts as a change and moves `generatedAt` once (`keep_if_unchanged` compares whole entries).

When several US feed entries map to one ISO code (Gaza and the West Bank are both Palestine), the most cautious
supplies link, tags and notes and the label names every rating. If the ratings differ the stance is capped at 2
(parts of the country), so one part's Level 4 does not count the whole territory as advised against.

```sh
python3 tools/advisory/build_advisory.py                        # downloads all three feeds (~430 small calls)
python3 tools/advisory/build_advisory.py --source tools/advisory/cache   # offline, from the last download
python3 -m unittest tools/advisory/test_build_advisory.py tools/advisory/test_make_site.py
```

Every `url` must be on `https`-hosts `travel.state.gov`, `www.gov.uk` or `www.auswaertiges-amt.de` (`URL_HOSTS` in `build_advisory.py`, `ADVISORY_URL_HOSTS` in `Advisory.kt`; keep them equal): the build fails on any other host and the app drops such an entry. `--previous FILE` names the file "unchanged" is judged against (the weekly job passes the live published copy, so `generatedAt` only moves when an advisory does).

The build writes nothing unless every feed is reachable, each yields at least 150 countries, and every
feed entry maps to an ISO code. An unmapped name is an error that lists it: add it to `US_ALIASES` or
`UK_ALIASES` in `build_advisory.py` (the US feed's `Category` is FIPS, not ISO, and the UK feed has no
code at all, so both are matched by name against `places.json`).

`.github/workflows/advisory-refresh.yml` runs it on Mondays at 08:00 UTC (after visa-refresh) and publishes the result to GitHub Pages
(the `publish` job), and its `pull-request` job rebuilds independently and opens a data pull request on
`data/advisory-YYYY-MM-DD` when the bundled `advisory.json` differs from main in anything but
`generatedAt` and the sources' `fetchedAt` (`tools/advisory/data_pr.py`, which also writes the PR's list
of changed countries, capped at 40 lines). Only one such PR is open at a time, an older one is closed in
favour of the new one, and a stale one is closed when main already matches; merging it releases nothing.
The two jobs do not wait for each other, so a failed PR job does not stop the publication, and the other
way round; either failing opens the same issue. The PR job needs the same repo setting as the visa one
(Settings > Actions > General > "Allow GitHub Actions to create and approve pull requests"); its
branches start `data/advisory-`, the visa ones `data/visa-`, and each workflow closes only its own.
Pages deployments replace the whole site, so `tools/advisory/make_site.py` copies
the live visa files (verified against the live manifest) into the same deployment, and
`visa-refresh.yml` carries the live `advisory.json` the other way. A failed run publishes nothing and
opens an issue that quotes the build's error text (also in the run's step summary).

The advisory file rides along on every visa publish too: `visa-refresh.yml` fetches the live
`advisory.json` and heartbeat (`tools/advisory/fetch_live.py`; Pages answers a first request for a missing
path with 503 then 404, which counts as absent). A live file that fails validation falls back to the bundled
copy with a `::warning::`; any other error fails the job, because the previous deployment keeps the live file
and silently publishing an older bundled copy over a newer one is worse.

Every advisory refresh also publishes `advisory-heartbeat.json` (the run time), even when the data is
unchanged. `visa-freshness.yml` opens an issue when it is more than 10 days old or missing while
`advisory.json` is live. German pages with no warning flag are scanned for travel-advice sentences
that extraction missed. A hit does not stop the build and needs no one. A sentence whose sha256 is in
`tools/advisory/de_reviewed.json` (per country code, with a reason) is known and ignored; any other hit
*quarantines* that feed entry (no rule clears a sentence, a reworded one has a new hash): its German
entry is left out (the app shows only the sources that are present, so nothing says "no warning"), every
other country and source builds, and the run stays green. If last week's file has a German stance of 1 or
more for that code, that entry is carried forward instead of dropped (`carriedForward` and
`previousStance` in the withheld record; stance 0, no entry or an unreadable previous file: dropped). The
previous file is `--previous`, else `--out`. Judging is per feed entry, then per code (Gaza and the West Bank
are both PS): if any entry of a code tripped, the whole code is withheld, unless another entry of it carries
a formal or partial warning flag (an extracted note alone is not enough), in which case the code publishes
with that sibling's stance (a formal warning is never dropped) and the tripped sentence is listed as
"sibling entry published". A calm sibling never leaves a plain "no warning" behind. The build logs
`warning: DE XX withheld, no German entry this week: [sha12] sentence` per sentence (`... entry withheld,
sibling entry published: ...` when another entry of the code still shows, listed but not counted) and a
closing `warning: German entries withheld this week (no DE entry for these countries): XX, YY`;
`--quarantine-out` writes the list (also when the limits below fail the build), both workflow jobs put it
in the job summary with a `::warning::` annotation on every run, and the data PR body lists "German
entries withheld this week" as `CODE [sha12] sentence`, with "kept last week's German stance N" for a
carried-forward one. Teach the extractor or add the hash and a reason later, at leisure. More than 10% of
the feed's entries, or more than 15 (`DE_MAX_MISSING_SHARE`, `DE_MAX_QUARANTINE`; countries withheld,
carried-forward ones included, against feed entries), is a changed feed or a broken extractor and fails
the build.

German notes are also not taken from a section about a territory of the country (Denmark's page keeps
its Greenland advice under a `Grönland` heading, and Denmark is not Greenland). The page's headings are
flat, so `TERRITORY_HEADINGS` in `build_advisory.py` lists them per country (DK, FR, GB, NL, US); a
sentence under such a heading is no note, but the tripwire still sees it, so on a country with no
formal flag a new one withholds that country's German entry until it is reviewed. Domestic sub-regions (Xinjiang, Oromia) are not territories.

The weekly data PR keeps the bundled asset current, so a fresh install without network starts from
current data once it is merged. Before a release, check that no `data/advisory-` PR is waiting; if the PR
job is broken, refresh by hand as the fallback: run the build (`--previous` the live file) and commit
`advisory.json`.
