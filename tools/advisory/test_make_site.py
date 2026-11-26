"""Host tests for tools/advisory/make_site.py.

Run: python3 -m unittest tools/advisory/test_make_site.py
"""
import base64
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import make_site as m  # noqa: E402

ADV = json.dumps({"dataFormat": 1, "countries": {f"C{i}": {} for i in range(160)}}).encode()
VISA = b'{"passport":"DE"}'


def live(files):
    return lambda url: files[url.rsplit("/", 1)[1]]


def NO_SIG(url):
    return None


def site(visa=VISA, **meta):
    entry = {"file": "visa_de.json", "sha256": hashlib.sha256(VISA).hexdigest(), "bytes": len(VISA)}
    entry.update(meta)
    return {"manifest.json": json.dumps({"passports": {"DE": entry}}).encode(), "visa_de.json": visa}


@unittest.skipUnless(shutil.which("openssl"), "openssl not installed")
class SignedPairTest(unittest.TestCase):
    """A throwaway P-256 key made here; the pair is verified with the same openssl path as the app check."""

    def sign(self, d, manifest):
        key = f"{d}/k.pem"
        run = lambda *a: subprocess.run(a, check=True, capture_output=True).stdout
        run("openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", key)
        Path(f"{d}/m").write_bytes(manifest)
        der = run("openssl", "dgst", "-sha256", "-sign", key, f"{d}/m")
        pub = base64.b64encode(run("openssl", "pkey", "-in", key, "-pubout", "-outform", "DER")).decode()
        return base64.b64encode(der), pub

    def test_a_matching_pair_is_carried_and_a_mismatched_pair_is_refused(self):
        files = site()
        with tempfile.TemporaryDirectory() as d:
            sig, pub = self.sign(d, files["manifest.json"])
            out = m.build(ADV, "https://x/y", live(files), get_optional=lambda u: sig, keys=[pub])
            self.assertEqual(out["manifest.sig"], sig)
            stale = dict(files, **{"manifest.json": files["manifest.json"] + b" "})
            with self.assertRaises(m.SiteError):
                m.build(ADV, "https://x/y", live(stale), get_optional=lambda u: sig, keys=[pub])
            with self.assertRaises(m.SiteError):
                m.build(ADV, "https://x/y", live(files), get_optional=lambda u: b"not base64 !!", keys=[pub])
            # no key listed: today's behaviour, any non-empty signature travels
            self.assertIn("manifest.sig", m.build(ADV, "https://x/y", live(stale), get_optional=lambda u: sig, keys=[]))


class MakeSiteTest(unittest.TestCase):
    def test_the_site_holds_the_live_visa_files_and_the_new_advisory_file(self):
        files = m.build(ADV, "https://x/y", live(site()), get_optional=NO_SIG)
        self.assertEqual(set(files), {"manifest.json", "visa_de.json", "advisory.json", "advisory-heartbeat.json"})
        self.assertEqual(files["advisory.json"], ADV)

    def test_a_live_signature_travels_with_the_manifest(self):
        files = m.build(ADV, "https://x/y", live(site()), get_optional=lambda u: b"c2ln\n", require_signature=True, keys=[])
        self.assertEqual(files["manifest.sig"], b"c2ln\n")

    def test_a_site_without_a_signature_is_carried_without_one_until_signing_is_live(self):
        files = m.build(ADV, "https://x/y", live(site()), get_optional=NO_SIG, keys=[])
        self.assertNotIn("manifest.sig", files)

    def test_a_missing_or_empty_signature_is_refused_once_signing_is_live(self):
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site()), get_optional=NO_SIG, require_signature=True)
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site()), get_optional=lambda u: b"  \n", keys=[])

    def test_a_visa_file_that_does_not_match_the_manifest_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site(visa=b'{"passport":"XX"}')), get_optional=NO_SIG)

    def test_an_unreachable_live_site_is_refused_rather_than_published_without_visa_files(self):
        def down(url):
            raise m.SiteError("offline")
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", down, get_optional=NO_SIG)

    def test_a_manifest_naming_a_path_outside_the_site_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site(file="../evil.json")), get_optional=NO_SIG)

    def test_a_malformed_advisory_file_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(b'{"dataFormat": 2, "countries": {}}', "https://x/y", live(site()), get_optional=NO_SIG)

    def test_every_site_carries_a_heartbeat_with_the_build_time(self):
        now = datetime(2026, 11, 25, 6, 30, 5, tzinfo=timezone.utc)
        files = m.build(ADV, "https://x/y", live(site()), now=now, get_optional=NO_SIG)
        self.assertEqual(json.loads(files["advisory-heartbeat.json"]), {"refreshedAt": "2026-11-25T06:30:05Z"})

    def test_a_heartbeat_older_than_the_limit_or_unreadable_is_refused(self):
        now = datetime(2026, 11, 25, tzinfo=timezone.utc)
        fresh = m.heartbeat(now - timedelta(days=9))
        stale = m.heartbeat(now - timedelta(days=11))
        self.assertEqual(m.check_heartbeat(fresh, now, 10), now - timedelta(days=9))
        for bad in (stale, b"{}", b"nope", m.heartbeat(now + timedelta(days=3))):
            with self.assertRaises(m.SiteError):
                m.check_heartbeat(bad, now, 10)
        m.check_heartbeat(stale, now)  # no limit given: only the shape is judged (the carry-over uses this)

    def test_advisory_age_is_refused_only_beyond_the_limit_or_when_unreadable(self):
        now = datetime(2026, 11, 25, tzinfo=timezone.utc)
        def adv(days):
            return json.dumps({"generatedAt": (now - timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")}).encode()
        self.assertEqual(m.check_advisory_age(adv(9), now, 10), now - timedelta(days=9))
        for bad in (adv(11), b"{}", b"nope"):
            with self.assertRaises(m.SiteError):
                m.check_advisory_age(bad, now, 10)

    def test_check_heartbeat_mode_exits_nonzero_for_a_stale_file(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "h.json"
            f.write_bytes(m.heartbeat(datetime.now(timezone.utc) - timedelta(days=30)))
            self.assertEqual(m.main(["--check-heartbeat", str(f)]), 1)
            f.write_bytes(m.heartbeat(datetime.now(timezone.utc)))
            self.assertEqual(m.main(["--check-heartbeat", str(f)]), 0)

    def test_check_mode_accepts_a_good_file_and_rejects_a_bad_one(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            good, bad = Path(d) / "g.json", Path(d) / "b.json"
            good.write_bytes(ADV)
            bad.write_bytes(b"<html>")
            self.assertEqual(m.main(["--check", str(good)]), 0)
            self.assertEqual(m.main(["--check", str(bad)]), 1)
            self.assertEqual(m.main(["--check", str(Path(d) / "none.json")]), 1)


if __name__ == "__main__":
    unittest.main()
