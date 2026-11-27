"""Manifest-signature helpers shared by check_published.py, sign_manifest.sh and the advisory site
builder. Deliberately tiny and import-light (standard library only) so any of them can load it
without pulling in the other make_site.py of tools/visa vs tools/advisory.
"""
import base64
import binascii
import re
import subprocess
import tempfile
from pathlib import Path

KEYS_FILE = Path(__file__).resolve().parents[2] / "app/src/main/java/com/riedel/beyondborders/data/visa/update/ManifestPublicKeys.kt"


def app_keys(kotlin: str) -> list:
    """The base64 public keys quoted in MANIFEST_PUBLIC_KEYS (ManifestPublicKeys.kt), the single
    source of truth; commented-out lines do not count."""
    m = re.search(r"MANIFEST_PUBLIC_KEYS[^=]*=\s*listOf\((.*?)\n\)", kotlin, re.S)
    if not m:
        return []
    body = "\n".join(l for l in m.group(1).splitlines() if not l.strip().startswith("//"))
    return re.findall(r'"([A-Za-z0-9+/=]+)"', body)


def openssl_verify(manifest: bytes, sig: bytes, key_b64: str) -> bool:
    """ECDSA P-256 / SHA-256 check through the openssl CLI (the standard library has none).
    False for anything that does not verify, including a key that is not valid base64 / DER."""
    with tempfile.TemporaryDirectory() as d:
        try:
            key_der = base64.b64decode(key_b64, validate=True)
        except (binascii.Error, ValueError):
            return False  # a malformed key is a failed check (reported as a problem), not a traceback
        (Path(d) / "key.der").write_bytes(key_der)
        (Path(d) / "m").write_bytes(manifest)
        (Path(d) / "s").write_bytes(sig)
        pem = subprocess.run(["openssl", "pkey", "-pubin", "-inform", "DER", "-in", f"{d}/key.der"],
                             capture_output=True)
        if pem.returncode != 0:
            return False
        (Path(d) / "key.pem").write_bytes(pem.stdout)
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", f"{d}/key.pem", "-signature", f"{d}/s", f"{d}/m"],
                           capture_output=True)
        return r.returncode == 0
