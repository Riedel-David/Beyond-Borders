# Privacy policy

Beyond Borders, an Android app by David Riedel (Riedel-David on GitHub), has no accounts, no analytics and no advertising, and
no travel data leaves your device, apart from Android's own system backup if you have that on. Its only network
use is a weekly check for newer public visa data and government travel advisories, on by default and switchable off
(see below). When newer data exists for a passport you have set, the app requests that passport's file by name, so
the request reveals that nationality to GitHub (the host), along with your IP address.

**Network use:** the app has the `INTERNET` permission for one purpose: to check `https://riedel-david.github.io/Beyond-Borders/` (GitHub Pages) about once a week, on an unmetered connection such as Wi-Fi, for newer visa-requirement files, and to download the files for the passports you have set. In the same check the app also downloads one small public file of travel advisories (the US State Department's, the UK Foreign, Commonwealth & Development Office's and the German Auswärtiges Amt's ratings for each country, compiled weekly by this project) from that same host; nothing about you, your places or your passports is sent with it. The request is a plain HTTPS GET with no account, no identifiers, no cookies and no query string; as with any web request, GitHub necessarily sees your IP address and the time. Each visa file is verified against a checksum before it is used, and the checksum list is verified against a digital signature (see "Visa data trust"); the advisory file has no checksum and is instead checked for format, date and plausibility (a file that looks truncated or older than what the app has is ignored). If anything fails the app keeps the data it shipped with. **Settings → Visa data** switches the weekly automatic check off, for both the visa files and the travel advisories. *Check now* is separate: it contacts GitHub Pages whenever you tap it, on any connection, even with the automatic check off (the weekly check runs on unmetered networks only). The app makes no other requests.

**Data collected:** none by me; the app has no server. The one thing a visa-data request discloses is the name of a passport file, which reveals a nationality you have set, to GitHub as the host. The travel-advisory request carries nothing about you. Nothing else is sent.

**Visa data trust:** updates are fetched over HTTPS only and each download is size-capped. Every visa file is checked against a SHA-256 listed in a manifest, and the manifest must carry a valid digital signature (ECDSA P-256) made with a private key that is not on the download host; the matching public key is built into the app. A manifest that is unsigned or whose signature does not verify is ignored, and the app keeps the data it already has. The travel-advisory file is not signed: it is only checked for format, date and plausibility.

**Data shared:** with no one beyond the passport-file request to GitHub described above. There are no third-party SDKs, advertisers or analytics providers. The only outside party involved is Google, through Android's optional Auto Backup (see below), and only if you have that switched on.

What you enter (places visited, visit dates, passport choice) is stored on your device only.
Achievements are worked out from your map each time and store nothing of their own,
apart from a list of which badges you have already seen, kept on the device. The app
language (English, German, Spanish, French, Portuguese, Ukrainian or Russian) is a per-app locale that Android itself keeps in its own
settings: the app stores no language setting, and choosing one involves no network request.
If Android's Auto Backup is enabled on your device, the app stages a snapshot of
your data so Android can include it in your device backup and offer to restore it
after a reinstall. That backup is handled by Google under Google's own terms; you
can turn it off in Android settings. Sharing a map image happens only when you
choose to, through the Android share sheet.

*Delete everything* in Settings clears your places, passports and display settings from
the phone and removes the staged snapshot. Google may keep an earlier backup until
Android next runs one; you can delete that in Android's backup settings.

Visa information and travel advisories are a bundled snapshot, optionally updated from GitHub Pages, and are not legal advice; check official
sources before travelling.

Last updated: 2026-11-27.

Contact: beyondborders.app@gmail.com, or open an issue on this repository, or use the private
[security advisory form](https://github.com/Riedel-David/Beyond-Borders/security/advisories/new)
for anything you would rather not post publicly.
