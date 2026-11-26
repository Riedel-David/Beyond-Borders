"""Host tests for tools/advisory/data_pr.py.

Run: python3 -m unittest tools/advisory/test_data_pr.py
"""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import data_pr as d  # noqa: E402

BASE = {
    "dataFormat": 1,
    "generatedAt": "2026-11-18T06:30:00Z",
    "sources": {"US": {"label": "US State Department", "fetchedAt": "2026-11-18T06:00:00Z"}},
    "countries": {
        "AD": {"US": {"stance": 0, "label": "Level 1", "updated": "2026-05-20", "url": "https://x"}},
        "DK": {"US": {"stance": 0, "label": "Level 1"}, "DE": {"stance": 0, "label": "Keine"}},
    },
}


def variant(**edit):
    doc = copy.deepcopy(BASE)
    for k, v in edit.items():
        doc[k] = v
    return doc


class DataPrTest(unittest.TestCase):
    def test_generatedAt_and_fetchedAt_alone_are_no_change(self):
        new = variant(generatedAt="2026-11-25T06:30:00Z")
        new["sources"]["US"]["fetchedAt"] = "2026-11-25T06:00:00Z"
        self.assertFalse(d.changed(BASE, new))

    def test_a_changed_stance_is_a_change(self):
        new = variant()
        new["countries"]["AD"]["US"]["stance"] = 3
        self.assertTrue(d.changed(BASE, new))

    def test_a_changed_source_label_is_a_change(self):
        new = variant()
        new["sources"]["US"]["label"] = "State Dept"
        self.assertTrue(d.changed(BASE, new))

    def test_a_missing_previous_file_is_a_change(self):
        self.assertTrue(d.changed(None, BASE))

    def test_lines_show_old_and_new_stance_and_label(self):
        new = variant()
        new["countries"]["AD"]["US"].update(stance=3, label="Level 4")
        self.assertEqual(d.change_lines(BASE, new), ["AD US: 0 Level 1 -> 3 Level 4"])

    def test_added_and_removed_entries_are_listed(self):
        new = variant()
        del new["countries"]["DK"]["DE"]
        new["countries"]["ZZ"] = {"UK": {"stance": 1, "label": "Some"}}
        self.assertEqual(d.change_lines(BASE, new),
                         ["DK DE: removed (was 0 Keine)", "ZZ UK: added: 1 Some"])

    def test_a_change_outside_stance_and_label_names_the_field(self):
        new = variant()
        new["countries"]["AD"]["US"]["updated"] = "2026-06-01"
        self.assertEqual(d.change_lines(BASE, new), ["AD US: same stance and label, changed updated"])

    def test_the_body_is_capped_with_a_count_of_the_rest(self):
        new = variant(countries={f"C{i:02d}": {"US": {"stance": 1, "label": "x"}} for i in range(5)})
        text = d.body(variant(countries={}), new, limit=2)
        self.assertIn("5 change(s)", text)
        self.assertIn("... and 3 more", text)
        self.assertEqual(sum(1 for l in text.splitlines() if l.startswith("C0")), 2)

    def test_backticks_and_long_labels_cannot_break_the_pr_fence(self):
        new = variant()
        new["countries"]["AD"]["US"]["label"] = "`" * 3 + "y" * 200
        line = d.change_lines(BASE, new)[0]
        self.assertNotIn("`", line)
        self.assertLess(len(line), 200)

    def test_the_command_line_prints_true_false_and_rejects_a_bad_new_file(self):
        with tempfile.TemporaryDirectory() as t:
            old, new = Path(t, "old.json"), Path(t, "new.json")
            old.write_text(json.dumps(BASE))
            new.write_text(json.dumps(variant(generatedAt="later")))
            self.assertEqual(d.main(["changed", str(old), str(new)]), 0)
            new.write_text("not json")
            self.assertEqual(d.main(["changed", str(old), str(new)]), 2)


if __name__ == "__main__":
    unittest.main()
