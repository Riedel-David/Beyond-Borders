# Security

Beyond Borders has the `INTERNET` permission, used only by the visa-data updater
(`data/visa/update/`): a plain HTTPS GET of static files from GitHub Pages, with
nothing about the user sent. The attack surface is small: local storage, the
backup/restore path, the file export (share) path and that updater fetch. The
fetch is HTTPS only (cleartext is disabled), size-capped, checked against a SHA-256
from the manifest and parsed before use, and the bundled data is the fallback if
anything fails.

To report a vulnerability, use GitHub's private
[security advisory form](https://github.com/Riedel-David/Beyond-Borders/security/advisories/new) rather than a public issue.
Expect an answer within a couple of weeks; this is a one-person project. Only the
latest release is supported. Release APKs on GitHub are signed with a fixed key and
published with a `.sha256` file. The Google Play build is signed by Google Play with a separate key that Google holds,
and Play checks it itself.
