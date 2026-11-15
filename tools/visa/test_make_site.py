"""Host tests for make_site.py.

Run: python3 -m unittest tools/visa/test_make_site.py
"""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import make_site  # noqa: E402


ROW = {"code": "FR", "category": "VISA_FREE", "source": "DATASET"}
DS = {"name": "n", "url": "u", "licence": "l"}


def write(d: Path, name: str, passport: str, reqs=(ROW,), **over):
    doc = {"passport": passport, "asOf": "2026-11-11", "dataset": DS, "requirements": list(reqs)}
    doc.update(over)
    (d / name).write_text(json.dumps(doc))


def full_set(d: Path, n=make_site.MIN_PASSPORTS):
    write(d, "visa.json", "DE")
    for i in range(n + 2):
        code = chr(65 + i // 26) + chr(65 + i % 26)
        if code not in ("DE", "FR"):
            write(d, f"visa_{code.lower()}.json", code)


class MakeSite(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.assets, self.out = self.tmp / "a", self.tmp / "o"
        self.assets.mkdir()

    def test_the_default_passport_is_published_under_its_own_code(self):
        full_set(self.assets)
        files = make_site.collect(self.assets)
        m = make_site.manifest(files, "t")
        self.assertEqual(m["passports"]["DE"]["file"], "visa_de.json")

    def test_the_manifest_hash_is_that_of_the_published_bytes(self):
        full_set(self.assets)
        files = make_site.collect(self.assets)
        raw = files["DE"][0]
        self.assertEqual(make_site.manifest(files, "t")["passports"]["DE"]["sha256"],
                         hashlib.sha256(raw).hexdigest())

    def test_a_file_naming_the_wrong_passport_is_refused(self):
        full_set(self.assets)
        write(self.assets, "visa_fr.json", "GB")
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_an_empty_requirements_list_is_refused(self):
        full_set(self.assets)
        write(self.assets, "visa_fr.json", "FR", reqs=())
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_a_file_without_a_dataset_block_is_refused(self):
        full_set(self.assets)
        write(self.assets, "visa_fr.json", "FR", dataset=None)
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_a_row_missing_its_source_is_refused(self):
        full_set(self.assets)
        write(self.assets, "visa_fr.json", "FR", reqs=({"code": "DE", "category": "VISA_FREE"},))
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_a_missing_as_of_is_refused(self):
        full_set(self.assets)
        write(self.assets, "visa_fr.json", "FR", asOf="")
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_a_non_object_file_is_refused(self):
        full_set(self.assets)
        (self.assets / "visa_fr.json").write_text("[]")
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_a_truncated_build_is_refused(self):
        full_set(self.assets, n=10)
        with self.assertRaises(ValueError):
            make_site.collect(self.assets)

    def test_invalid_json_is_refused(self):
        full_set(self.assets)
        (self.assets / "visa_fr.json").write_text("{")
        with self.assertRaises(json.JSONDecodeError):
            make_site.collect(self.assets)


if __name__ == "__main__":
    unittest.main()
