"""Host tests for diff_rows.py.

Run: python3 -m unittest tools/visa/test_diff_rows.py
"""
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))
import diff_rows  # noqa: E402


def doc(*rs):
    return {"requirements": [dict(code=c, category=cat, **({"days": d} if d else {})) for c, cat, d in rs]}


class DiffRows(unittest.TestCase):
    def test_a_changed_category_is_listed(self):
        old = diff_rows.rows(doc(("FR", "VISA_FREE", 90)))
        new = diff_rows.rows(doc(("FR", "E_VISA", 90)))
        self.assertEqual(diff_rows.diff_rows(old, new), [("FR", ("VISA_FREE", 90), ("E_VISA", 90))])

    def test_changed_days_are_listed(self):
        old = diff_rows.rows(doc(("FR", "VISA_FREE", 90)))
        new = diff_rows.rows(doc(("FR", "VISA_FREE", 30)))
        out = diff_rows.render([("DE", diff_rows.diff_rows(old, new))])
        self.assertIn("  FR: VISA_FREE 90d -> VISA_FREE 30d", out)

    def test_an_added_row_shows_none_as_old(self):
        out = diff_rows.render([("DE", diff_rows.diff_rows({}, diff_rows.rows(doc(("FR", "ETA", None)))))])
        self.assertIn("  FR: (none) -> ETA", out)

    def test_a_removed_row_shows_none_as_new(self):
        out = diff_rows.render([("DE", diff_rows.diff_rows(diff_rows.rows(doc(("FR", "ETA", None))), {}))])
        self.assertIn("  FR: ETA -> (none)", out)

    def test_a_new_file_counts_every_row_as_added(self):
        with mock.patch("subprocess.run") as run:
            run.return_value.returncode = 1  # git cat-file: path not in rev
            self.assertEqual(diff_rows.old_rows("HEAD", "x.json"), {})

    def test_a_failing_git_show_aborts_instead_of_looking_like_a_new_file(self):
        calls = [mock.Mock(returncode=0), mock.Mock(returncode=128, stderr="boom", stdout="")]
        with mock.patch("subprocess.run", side_effect=calls):
            with self.assertRaises(SystemExit):
                diff_rows.old_rows("HEAD", "x.json")

    def test_no_row_changes_says_so(self):
        self.assertEqual(diff_rows.render([]), ["No category or stay changes; only other fields differ."])

    def test_long_output_is_cut_on_whole_lines_with_a_count(self):
        diffs = [(f"C{i}", None, ("ETA", None)) for i in range(500)]
        out = diff_rows.render([("DE", diffs)], limit=50)
        self.assertTrue(any(l.endswith("more rows not shown") for l in out))
        self.assertTrue(all(l == "" or l.endswith(":") or l.startswith("  ") or "row" in l for l in out))


if __name__ == "__main__":
    unittest.main()
