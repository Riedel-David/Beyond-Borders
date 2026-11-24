"""Host tests for tools/advisory/make_site.py.

Run: python3 -m unittest tools/advisory/test_make_site.py
"""
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import make_site as m  # noqa: E402

ADV = json.dumps({"dataFormat": 1, "countries": {f"C{i}": {} for i in range(160)}}).encode()
VISA = b'{"passport":"DE"}'


def live(files):
    return lambda url: files[url.rsplit("/", 1)[1]]


def site(visa=VISA, **meta):
    entry = {"file": "visa_de.json", "sha256": hashlib.sha256(VISA).hexdigest(), "bytes": len(VISA)}
    entry.update(meta)
    return {"manifest.json": json.dumps({"passports": {"DE": entry}}).encode(), "visa_de.json": visa}


class MakeSiteTest(unittest.TestCase):
    def test_the_site_holds_the_live_visa_files_and_the_new_advisory_file(self):
        files = m.build(ADV, "https://x/y", live(site()))
        self.assertEqual(set(files), {"manifest.json", "visa_de.json", "advisory.json"})
        self.assertEqual(files["advisory.json"], ADV)

    def test_a_visa_file_that_does_not_match_the_manifest_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site(visa=b'{"passport":"XX"}')))

    def test_an_unreachable_live_site_is_refused_rather_than_published_without_visa_files(self):
        def down(url):
            raise m.SiteError("offline")
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", down)

    def test_a_manifest_naming_a_path_outside_the_site_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(ADV, "https://x/y", live(site(file="../evil.json")))

    def test_a_malformed_advisory_file_is_refused(self):
        with self.assertRaises(m.SiteError):
            m.build(b'{"dataFormat": 2, "countries": {}}', "https://x/y", live(site()))

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
