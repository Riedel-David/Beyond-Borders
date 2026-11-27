#!/usr/bin/env bash
# Signs <site-dir>/manifest.json: writes <site-dir>/manifest.sig, the base64 of the DER ECDSA P-256 /
# SHA-256 signature over the manifest's exact bytes (what the app's verifyManifestSignature checks).
#
# Usage: MANIFEST_SIGNING_KEY="$(cat key.pem)" bash tools/visa/sign_manifest.sh <site-dir>
#
# Fails loudly, never skips: an empty or missing key, a key that is not P-256, a signature that does
# not verify against the key's own public half, or (once the app's MANIFEST_PUBLIC_KEYS lists a key)
# a signing key the app does not trust. The key is written to a private temp dir only for the
# duration of the run and is never printed.
set -euo pipefail

site="${1:?usage: sign_manifest.sh <site-dir>}"
[ -f "$site/manifest.json" ] || { echo "sign_manifest: $site/manifest.json does not exist" >&2; exit 1; }
if [ -z "$(printf '%s' "${MANIFEST_SIGNING_KEY:-}" | tr -d '[:space:]')" ]; then
  echo "sign_manifest: MANIFEST_SIGNING_KEY is missing or empty; refusing to publish an unsigned manifest" >&2
  exit 1
fi

umask 077
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sign.XXXXXX")"
trap 'rm -rf "$work"' EXIT
printf '%s\n' "$MANIFEST_SIGNING_KEY" > "$work/key.pem"

# Captured first: grep -q closing the pipe early would SIGPIPE openssl and fail under pipefail.
key_text="$(openssl pkey -in "$work/key.pem" -noout -text 2>/dev/null || true)"
grep -qE 'prime256v1|P-256' <<<"$key_text" \
  || { echo "sign_manifest: the key is not a readable ECDSA P-256 private key" >&2; exit 1; }

openssl dgst -sha256 -sign "$work/key.pem" -out "$work/sig.der" "$site/manifest.json"
openssl pkey -in "$work/key.pem" -pubout -out "$work/pub.pem"
openssl dgst -sha256 -verify "$work/pub.pem" -signature "$work/sig.der" "$site/manifest.json" >/dev/null \
  || { echo "sign_manifest: the fresh signature does not verify against the key's public half" >&2; exit 1; }

# The key must be one the shipped app trusts, once the app lists any .
pub_b64="$(openssl pkey -in "$work/key.pem" -pubout -outform DER | base64 | tr -d '\n')"
python3 -I - "$pub_b64" "$(dirname "$0")" <<'PY'
import sys
sys.path.insert(0, sys.argv[2])
from check_published import KEYS_FILE, app_keys
keys = app_keys(KEYS_FILE.read_text(encoding="utf-8"))
if keys and sys.argv[1] not in keys:
    sys.exit("sign_manifest: the signing key is not in MANIFEST_PUBLIC_KEYS; the app would reject every update")
print("app trusts %d key(s)" % len(keys) if keys else "app has no key configured yet (rollout step 3 pending)")
PY

base64 < "$work/sig.der" | tr -d '\n' > "$site/manifest.sig"
echo "signed $site/manifest.json -> manifest.sig ($(wc -c < "$site/manifest.sig") bytes)"
