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

    def test_the_body_lists_the_withheld_german_entries(self):
        held = [{"code": "BG", "sha": "362e57fa8a507e6a239cdb51290255d23663e4ca58d6d45f3178177a55bb6c4a",
                 "sentence": "Meiden Sie `das` Gebiet."}]
        text = d.body(BASE, BASE, withheld=held)
        self.assertIn("German entries withheld this week", text)
        self.assertIn("BG [362e57fa8a50] Meiden Sie 'das' Gebiet.", text)
        self.assertNotIn("withheld", d.body(BASE, BASE, withheld=[]))
        self.assertNotIn("withheld", d.body(BASE, BASE, withheld="garbage"))

    def test_the_withheld_command_prints_lines_or_nothing(self):
        import io
        from contextlib import redirect_stdout
        with tempfile.TemporaryDirectory() as t:
            f = Path(t, "q.json")
            for content, expect in (('[{"code":"BG","sha":"abc","sentence":"S"}]', "BG [abc] S\n"), ("[]", "\n"), ("junk", d.WITHHELD_UNAVAILABLE + "\n")):
                f.write_text(content)
                buf = io.StringIO()
                with redirect_stdout(buf):
                    self.assertEqual(d.main(["withheld", str(f)]), 0)
                self.assertEqual(buf.getvalue(), expect)

    def test_the_withheld_list_is_capped_with_a_count_of_the_valid_rest(self):
        held = [{"code": "AB", "sha": "x", "sentence": "s"}] * 3 + ["junk"] + [{"code": "CD", "sha": "", "sentence": "t"}] * 2
        lines = d.withheld_lines(held, 2)
        self.assertEqual(lines, ["AB [x] s", "AB [x] s", "... and 3 more"])
        self.assertEqual(d.withheld_lines([{"code": "CD", "sha": "", "sentence": "t", "siblingPublished": True}]),
                         ["CD t (sibling entry published)"])

    def test_the_withheld_codes_are_sorted_unique_and_leave_siblings_out(self):
        held = [{"code": "BG", "sha": "a", "sentence": "s"}, {"code": "AR", "sha": "b", "sentence": "t"},
                {"code": "BG", "sha": "c", "sentence": "u"}, {"code": "PS", "sha": "d", "sentence": "v", "siblingPublished": True}]
        self.assertEqual(d.withheld_codes(held), ["AR", "BG"])
        self.assertEqual(d.withheld_codes("junk"), [])

    def test_an_unreadable_or_malformed_withheld_file_is_said_on_stderr(self):
        import io
        from contextlib import redirect_stderr
        with tempfile.TemporaryDirectory() as t:
            bad = Path(t, "q.json")
            bad.write_text("{}")
            for path in (bad, Path(t, "missing.json")):
                err = io.StringIO()
                with redirect_stderr(err):
                    self.assertEqual(d.load_withheld(path), d.WITHHELD_UNAVAILABLE)
                self.assertIn("warning:", err.getvalue())

    def test_an_unreadable_withheld_file_puts_a_visible_line_in_the_pr_body(self):
        text = d.body(BASE, BASE, withheld=d.load_withheld("/nonexistent/q.json"))
        self.assertIn("withheld list unavailable: check the job log", text)

    def test_the_body_opens_with_a_line_per_country_that_has_no_german_advice(self):
        held = [{"code": "BG", "sha": "a", "sentence": "s"}, {"code": "AR", "sha": "b", "sentence": "t"},
                {"code": "BG", "sha": "c", "sentence": "u"},
                {"code": "PS", "sha": "d", "sentence": "v", "siblingPublished": True},
                {"code": "TN", "sha": "e", "sentence": "w", "carriedForward": True, "previousStance": 3, "ageDays": 7}]
        text = d.body(BASE, BASE, withheld=held)
        first = text.splitlines()[:2]
        self.assertEqual(first, ["NO GERMAN ADVICE IS SHOWN for AR until the withheld sentence is reviewed.",
                                 "NO GERMAN ADVICE IS SHOWN for BG until the withheld sentence is reviewed."])
        self.assertNotIn("NO GERMAN ADVICE IS SHOWN for TN", text)
        self.assertNotIn("NO GERMAN ADVICE IS SHOWN for PS", text)

    def test_the_command_line_prints_true_false_and_rejects_a_bad_new_file(self):
        with tempfile.TemporaryDirectory() as t:
            old, new = Path(t, "old.json"), Path(t, "new.json")
            old.write_text(json.dumps(BASE))
            new.write_text(json.dumps(variant(generatedAt="later")))
            self.assertEqual(d.main(["changed", str(old), str(new)]), 0)
            new.write_text("not json")
            self.assertEqual(d.main(["changed", str(old), str(new)]), 2)


class CarriedForwardLineTest(unittest.TestCase):
    def test_a_carried_forward_entry_shows_last_weeks_stance(self):
        lines = d.withheld_lines([{"code": "BG", "sha": "ab" * 32, "sentence": "S.", "carriedForward": True,
                                   "previousStance": 3}])
        self.assertEqual(lines, ["BG [abababababab] S. \u2014 kept last week's German stance 3"])

    def test_a_carried_forward_entry_shows_its_age(self):
        lines = d.withheld_lines([{"code": "BG", "sha": "ab", "sentence": "S.", "carriedForward": True,
                                   "previousStance": 3, "ageDays": 21}])
        self.assertEqual(lines, ["BG [ab] S. \u2014 kept last week's German stance 3, 21 days old"])


if __name__ == "__main__":
    unittest.main()
