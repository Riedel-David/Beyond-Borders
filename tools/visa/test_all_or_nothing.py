"""Build safety nets that the Kotlin suite cannot see.

Run: python3 -m unittest tools/visa/test_all_or_nothing.py
Needs the cached dataset (tools/visa/cache/visa-requirements, gitignored, made by
a build); the tests that need it are skipped without it.
"""
import contextlib
import hashlib
import io
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))
import build_visa  # noqa: E402

HAVE_CACHE = (build_visa.DEFAULT_SOURCE / "index.json").exists()


def snapshot(assets: Path, docs: Path) -> dict[str, str]:
    files = list(assets.glob("visa*.json")) + list(docs.glob("*.txt"))
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}


@unittest.skipUnless(HAVE_CACHE, "no cached dataset; run a full build once")
class FullBuild(unittest.TestCase):
    """Runs the real build against a throwaway copy of the assets and docs."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.assets = self.tmp / "assets"
        self.docs = self.tmp / "docs"
        self.assets.mkdir()
        self.docs.mkdir()
        for p in build_visa.ASSETS.glob("visa*.json"):
            shutil.copy(p, self.assets / p.name)
        shutil.copy(build_visa.REVISIONS_FILE, self.docs)
        self.patches = [
            mock.patch.object(build_visa, "REPO", self.tmp),
            mock.patch.object(build_visa, "ASSETS", self.assets),
            mock.patch.object(build_visa, "REVISIONS_FILE", self.docs / "rev.txt"),
            mock.patch.object(build_visa, "GAP_FILE", self.docs / "gap.txt"),
        ]
        for p in self.patches:
            p.start()
            self.addCleanup(p.stop)

    def run_main(self, *extra, **patches):
        argv = ["build_visa.py", "--source", str(build_visa.DEFAULT_SOURCE), *extra]
        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(sys, "argv", argv))
            stack.enter_context(mock.patch.object(build_visa, "fetch", lambda d: None))
            for name, value in patches.items():
                stack.enter_context(mock.patch.object(build_visa, name, value))
            with contextlib.redirect_stdout(io.StringIO()):
                return build_visa.main()

    def test_a_failed_check_writes_and_deletes_nothing(self):
        # A too-thin passport's stale asset is present, and a check fails afterwards.
        (self.assets / "visa_xn.json").write_text("{}")
        before = snapshot(self.assets, self.docs)
        with self.assertRaises(SystemExit):
            self.run_main(check_rules_live=lambda usage: ["forced failure"])
        self.assertEqual(before, snapshot(self.assets, self.docs))
        self.assertTrue((self.assets / "visa_xn.json").exists())

    def test_a_too_thin_passports_stale_asset_is_deleted_by_a_good_build(self):
        (self.assets / "visa_xn.json").write_text("{}")
        self.run_main()
        self.assertFalse((self.assets / "visa_xn.json").exists())
        self.assertTrue((self.assets / "visa.json").exists())
        self.assertTrue((self.docs / "gap.txt").exists())

    def test_a_shipped_passport_the_dataset_lost_is_a_failure(self):
        (self.assets / "visa_zz.json").write_text("{}")
        with self.assertRaises(SystemExit):
            self.run_main()


class SinglePassport(unittest.TestCase):
    def test_single_passport_build_refuses_to_download(self):
        argv = ["build_visa.py", "--passport", "GB"]
        fetched = []
        with mock.patch.object(sys, "argv", argv), \
                mock.patch.object(build_visa, "fetch", lambda d: fetched.append(d)):
            with self.assertRaises(SystemExit) as cm:
                build_visa.main()
        self.assertIn("--source", str(cm.exception))
        self.assertEqual([], fetched)


class IndexShape(unittest.TestCase):
    def test_entries_must_be_dicts_naming_their_own_file(self):
        build_visa.check_index_files({"passports": {"DE": {"file": "passports/DE.json"}}})
        for bad in ("passports/DE.json", {"file": "../x.json"}, {"file": "passports/FR.json"}):
            with self.assertRaises(SystemExit):
                build_visa.check_index_files({"passports": {"DE": bad}})


class Notes(unittest.TestCase):
    def test_short_notes_pass_through(self):
        self.assertEqual("a b", build_visa.trim_note(" a\n b ", 10))

    def test_cut_at_the_last_sentence_end(self):
        text = "First sentence here. Second sentence goes on and on without end"
        self.assertEqual("First sentence here. …", build_visa.trim_note(text, 40))

    def test_a_limit_on_a_word_boundary_keeps_the_word(self):
        self.assertEqual("one two three …", build_visa.trim_note("one two three four", 13))

    def test_an_unbroken_string_is_cut_rather_than_emptied(self):
        self.assertEqual("abcde …", build_visa.trim_note("abcdefghij", 5))


class Esta(unittest.TestCase):
    def rec(self, notes):
        return {"passport": "XX", "destination": "US", "status": "visa_free",
                "alternative_statuses": [], "allowed_stay_days": None, "notes": notes}

    def category(self, notes):
        usage = build_visa.Usage()
        return build_visa.row_from_record(self.rec(notes), usage).category, usage

    def test_required_exempt_and_unclassified(self):
        self.assertEqual("ETA", self.category("ESTA required.")[0])
        self.assertEqual("VISA_FREE", self.category("ESTA exempt. Do not need one.")[0])
        for exempt in ("ESTA not required", "No ESTA required",
                       "Not required to obtain an ESTA.", "Citizens do not need an ESTA."):
            cat, usage = self.category(exempt)
            self.assertEqual(("VISA_FREE", []), (cat, usage.esta_unclassified), exempt)
        for required in ("ESTA is valid for 2 years.", "On arrival; ESTA (valid 2 years) required",
                         "90 days. ESTA and passport with National ID number required."):
            self.assertEqual("ETA", self.category(required)[0], required)
        # One note, both statements: a requirement for the US, an exemption for Guam.
        self.assertEqual("ETA", self.category(
            "ESTA (valid for 1 years) required by air.\nESTA is not required for Guam.")[0])
        cat, usage = self.category("Travellers may be asked about ESTA at the gate.")
        self.assertEqual(1, len(usage.esta_unclassified))
        self.assertTrue(build_visa.check_esta(usage))


if __name__ == "__main__":
    unittest.main()
