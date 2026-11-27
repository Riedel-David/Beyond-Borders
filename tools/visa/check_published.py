"""Health check of the PUBLISHED visa site (the manifest the app's updater reads).

Usage: python3 -I tools/visa/check_published.py [manifest-url [signature-url]]
Prints one problem per line and always exits 0; visa-freshness.yml turns each line into an
issue. Nothing printed means the site looks healthy.
"""
import base64
import binascii
import json
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

# -I drops the script directory from sys.path, so add it back for the shared constants.
sys.path.insert(0, str(Path(__file__).parent))
from make_site import DATA_FORMAT, MANIFEST_VERSION, MIN_PASSPORTS  # noqa: E402
from manifest_sig import KEYS_FILE, app_keys, openssl_verify  # noqa: E402,F401  (re-exported for callers)

URL = "https://riedel-david.github.io/Beyond-Borders/manifest.json"
SIG_URL = "https://riedel-david.github.io/Beyond-Borders/manifest.sig"
MAX_AGE = timedelta(days=10)


def check(text: str, now: datetime) -> list:
    try:
        m = json.loads(text)
    except ValueError:
        return ["the manifest is not valid JSON"]
    if not isinstance(m, dict):
        return ["the manifest is not a JSON object"]
    problems = []
    for key, want in (("manifestVersion", MANIFEST_VERSION), ("dataFormat", DATA_FORMAT)):
        if m.get(key) != want:
            problems.append(f"{key} is {m.get(key)!r}, expected {want}")
    try:
        generated = datetime.fromisoformat(str(m.get("generatedAt")).replace("Z", "+00:00"))
        if generated.tzinfo is None:
            generated = generated.replace(tzinfo=timezone.utc)
        if now - generated > MAX_AGE:
            problems.append(f"generatedAt {m['generatedAt']} is older than {MAX_AGE.days} days")
    except ValueError:
        problems.append(f"generatedAt {m.get('generatedAt')!r} is not a date")
    passports = m.get("passports")
    count = len(passports) if isinstance(passports, dict) else 0
    if count < MIN_PASSPORTS:
        problems.append(f"only {count} passports, expected at least {MIN_PASSPORTS}")
    return problems


def check_signature(manifest: bytes, sig_text, keys: list, verify=openssl_verify) -> list:
    """Problems with the published manifest.sig. sig_text is None when it is not published. With no
    key configured in the app yet, only its shape can be checked (rollout step 2 in docs/DATA.md
    verifies it by hand with the public key)."""
    if sig_text is None:
        return ["manifest.sig is not published (the signed manifest the app requires is missing)"]
    try:
        sig = base64.b64decode(sig_text.strip(), validate=True)
    except (binascii.Error, ValueError):
        return ["manifest.sig is not valid base64"]
    if not sig:
        return ["manifest.sig is empty"]
    if not keys:
        return []
    if not any(verify(manifest, sig, k) for k in keys):
        return ["manifest.sig does not verify against manifest.json with any key the app trusts"]
    return []


def get(url: str):
    """Body bytes, or None for a 404; raises for anything else after three tries."""
    error = None
    for attempt in range(3):  # one blip must not open an issue
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            error = e
        except Exception as e:  # DNS, timeout
            error = e
        time.sleep(5 * attempt)
    raise error


def main(argv: list) -> int:
    url = argv[1] if len(argv) > 1 else URL
    sig_url = argv[2] if len(argv) > 2 else SIG_URL
    try:
        raw = get(url)
    except Exception as e:
        print(f"the manifest could not be fetched: {e}")
        return 0
    if raw is None:
        print("the manifest could not be fetched: HTTP 404")
        return 0
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        print("the manifest is not valid UTF-8")
        return 0
    for p in check(text, datetime.now(timezone.utc)):
        print(p)
    try:
        sig = get(sig_url)
    except Exception as e:
        print(f"manifest.sig could not be fetched: {e}")
        return 0
    keys = app_keys(KEYS_FILE.read_text(encoding="utf-8"))
    for p in check_signature(raw, None if sig is None else sig.decode("utf-8", "replace"), keys):
        print(p)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
