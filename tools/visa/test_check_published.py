import json
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import base64
import shutil
import subprocess
import tempfile
from check_published import app_keys, check, check_signature, openssl_verify  # noqa: E402

NOW = datetime(2026, 11, 15, tzinfo=timezone.utc)


def manifest(**over):
    m = {"manifestVersion": 1, "dataFormat": 1,
         "generatedAt": (NOW - timedelta(days=2)).isoformat(timespec="seconds"),
         "passports": {f"P{i}": {} for i in range(160)}}
    m.update(over)
    return json.dumps(m)


class CheckPublishedTest(unittest.TestCase):
    def test_a_fresh_complete_manifest_has_no_problems(self):
        self.assertEqual(check(manifest(), NOW), [])

    def test_a_manifest_older_than_ten_days_is_stale(self):
        old = (NOW - timedelta(days=11)).isoformat()
        self.assertEqual(len(check(manifest(generatedAt=old), NOW)), 1)

    def test_too_few_passports_is_a_problem(self):
        self.assertEqual(len(check(manifest(passports={"DE": {}}), NOW)), 1)

    def test_a_wrong_version_is_a_problem(self):
        self.assertEqual(len(check(manifest(dataFormat=2, manifestVersion=2), NOW)), 2)

    def test_non_json_is_a_problem(self):
        self.assertEqual(len(check("<html>", NOW)), 1)


SAMPLE_KT = '''internal val MANIFEST_PUBLIC_KEYS: List<String> = listOf(
    // "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEcommented",
    "QUJD+/=",
    "REVG",
)'''


class SignatureTest(unittest.TestCase):
    def test_keys_are_read_from_the_kotlin_list_and_comments_are_ignored(self):
        self.assertEqual(app_keys(SAMPLE_KT), ["QUJD+/=", "REVG"])
        self.assertEqual(app_keys("val MANIFEST_PUBLIC_KEYS: List<String> = listOf(\n    // \"AAAA\",\n)"), [])

    def test_the_shipped_key_file_parses(self):
        from check_published import KEYS_FILE
        self.assertIsInstance(app_keys(KEYS_FILE.read_text(encoding="utf-8")), list)

    def test_a_missing_signature_is_a_problem(self):
        self.assertEqual(len(check_signature(b"m", None, ["k"])), 1)

    def test_a_malformed_or_empty_signature_is_a_problem(self):
        self.assertEqual(len(check_signature(b"m", "not base64 !!", ["k"])), 1)
        self.assertEqual(len(check_signature(b"m", "", ["k"])), 1)

    def test_with_no_key_configured_only_the_shape_is_checked(self):
        self.assertEqual(check_signature(b"m", base64.b64encode(b"sig").decode(), []), [])

    def test_a_signature_no_key_verifies_is_a_problem(self):
        sig = base64.b64encode(b"sig").decode()
        self.assertEqual(len(check_signature(b"m", sig, ["a", "b"], verify=lambda m, s, k: False)), 1)
        self.assertEqual(check_signature(b"m", sig, ["a", "b"], verify=lambda m, s, k: k == "b"), [])


@unittest.skipUnless(shutil.which("openssl"), "openssl not installed")
class OpensslRoundTripTest(unittest.TestCase):
    """The same commands visa-refresh.yml signs with, against a throwaway key made here."""

    def run_(self, *args, **kw):
        return subprocess.run(args, check=True, capture_output=True, **kw)

    def test_a_signature_made_like_the_workflow_verifies_and_a_changed_manifest_does_not(self):
        with tempfile.TemporaryDirectory() as d:
            key, manifest = f"{d}/k.pem", f"{d}/manifest.json"
            self.run_("openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", key)
            Path(manifest).write_bytes(b'{"manifestVersion": 1}\n')
            der = self.run_("openssl", "dgst", "-sha256", "-sign", key, manifest).stdout
            pub = base64.b64encode(self.run_("openssl", "pkey", "-in", key, "-pubout", "-outform", "DER").stdout).decode()
            sig = base64.b64encode(der).decode()
            self.assertEqual(check_signature(Path(manifest).read_bytes(), sig, [pub]), [])
            self.assertTrue(openssl_verify(b'{"manifestVersion": 1}\n', der, pub))
            self.assertFalse(openssl_verify(b'{"manifestVersion": 2}\n', der, pub))
            self.assertFalse(openssl_verify(b"x", der, "AAAA"))

    def test_a_malformed_key_is_a_reported_problem_not_a_traceback(self):
        sig = base64.b64encode(b"sig").decode()
        self.assertFalse(openssl_verify(b"m", b"sig", "not base64 !!"))
        self.assertEqual(len(check_signature(b"m", sig, ["not base64 !!", "AAA"])), 1)


if __name__ == "__main__":
    unittest.main()
