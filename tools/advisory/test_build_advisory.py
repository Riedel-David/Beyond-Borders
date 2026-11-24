"""Host tests for build_advisory.py. Fixtures are trimmed excerpts of the real feeds (2026-11-24).

Run: python3 -m unittest tools/advisory/test_build_advisory.py
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_advisory as b  # noqa: E402

PLACES = [
    {"code": "AF", "name": "Afghanistan"}, {"code": "FR", "name": "France"},
    {"code": "MX", "name": "Mexico"}, {"code": "PS", "name": "Palestine"},
    {"code": "CI", "name": "Côte d'Ivoire"}, {"code": "TR", "name": "Turkey"},
    {"code": "BS", "name": "The Bahamas"}, {"code": "CK", "name": "Cook Islands"},
    {"code": "TK", "name": "Tokelau"}, {"code": "NU", "name": "Niue"},
]
INDEX = b.name_index(PLACES)
VALID = {p["code"] for p in PLACES}


def us(title, cat="XX", updated="2026-10-02T19:00:00-04:00", summary="Exercise increased caution<p>due to <b>terrorism</b> and <b>unrest</b>.</p>"):
    return {"Title": title, "Link": "https://travel.state.gov/x", "Category": [cat], "Published": updated, "Updated": updated, "Summary": summary}


def uk(name, flags, updated="2026-11-01T10:00:00+00:00"):
    return {"public_updated_at": updated, "details": {"alert_status": flags, "country": {"name": name, "slug": name.lower()}}}


def de(code, warning=False, partial=False, last=1791881890):
    return {"lastModified": last, "countryCode": code, "countryName": code, "warning": warning, "partialWarning": partial}


class UsTest(unittest.TestCase):
    def stance(self, level_title):
        return b.parse_us([us("France - " + level_title)], INDEX)["FR"]["stance"]

    def test_levels_one_to_four_map_to_stances_zero_to_three(self):
        self.assertEqual(self.stance("Level 1: Exercise Normal Precautions"), 0)
        self.assertEqual(self.stance("Level 2: Exercise Increased Caution"), 1)
        self.assertEqual(self.stance("Level 3: Reconsider Travel"), 2)
        self.assertEqual(self.stance("Level 4: Do Not Travel"), 3)

    def test_label_is_the_sources_own_wording_and_date_is_the_update_date(self):
        e = b.parse_us([us("France - Level 2: Exercise Increased Caution")], INDEX)["FR"]
        self.assertEqual(e["label"], "Level 2: Exercise Increased Caution")
        self.assertEqual(e["updated"], "2026-10-02")
        self.assertEqual(e["tags"], ["terrorism", "unrest"])

    def test_a_title_without_a_level_is_an_error(self):
        with self.assertRaises(b.BuildError):
            b.parse_us([us("France - Exercise caution")], INDEX)

    def test_gaza_and_the_west_bank_share_palestine_and_the_stricter_one_wins(self):
        rows = b.parse_us([us("Gaza - Level 4: Do Not Travel"), us("The West Bank - Level 3: Reconsider Travel")], INDEX)
        self.assertEqual(rows["PS"]["stance"], 3)
        rows = b.parse_us([us("The West Bank - Level 3: Reconsider Travel"), us("Gaza - Level 4: Do Not Travel")], INDEX)
        self.assertEqual(rows["PS"]["stance"], 3)

    def test_the_title_prefix_and_odd_names_still_resolve(self):
        rows = b.parse_us([us("Mexico Travel Advisory - Level 2: Exercise Increased Caution"),
                           us("Côte d’Ivoire - Level 2: Exercise Increased Caution")], INDEX)
        self.assertEqual(set(rows), {"MX", "CI"})

    def test_an_unmappable_country_is_named_in_the_error(self):
        with self.assertRaises(b.BuildError) as cm:
            b.parse_us([us("Narnia - Level 1: Exercise Normal Precautions")], INDEX)
        self.assertIn("Narnia", str(cm.exception))


class UkTest(unittest.TestCase):
    def stance(self, flags):
        return b.parse_uk({"france": uk("France", flags)}, INDEX)["FR"]["stance"]

    def test_no_alert_is_stance_zero(self):
        self.assertEqual(self.stance([]), 0)

    def test_parts_of_the_country_is_stance_two_for_both_kinds_of_advice(self):
        self.assertEqual(self.stance(["avoid_all_travel_to_parts"]), 2)
        self.assertEqual(self.stance(["avoid_all_but_essential_travel_to_parts"]), 2)
        self.assertEqual(self.stance(["avoid_all_travel_to_parts", "avoid_all_but_essential_travel_to_parts"]), 2)

    def test_whole_country_is_stance_three_including_all_but_essential(self):
        self.assertEqual(self.stance(["avoid_all_travel_to_whole_country"]), 3)
        self.assertEqual(self.stance(["avoid_all_but_essential_travel_to_whole_country"]), 3)

    def test_whole_country_outranks_parts_when_both_are_present(self):
        self.assertEqual(self.stance(["avoid_all_travel_to_parts", "avoid_all_travel_to_whole_country"]), 3)

    def test_an_unknown_flag_is_an_error_rather_than_a_guess(self):
        with self.assertRaises(b.BuildError):
            self.stance(["avoid_everything"])

    def test_a_page_without_alert_status_is_an_error(self):
        with self.assertRaises(b.BuildError):
            b.parse_uk({"france": {"details": {"country": {"name": "France"}}, "public_updated_at": "2026-11-01T00:00:00Z"}}, INDEX)

    def test_url_and_date_come_from_the_slug_and_public_update(self):
        e = b.parse_uk({"france": uk("France", [])}, INDEX)["FR"]
        self.assertEqual(e["url"], "https://www.gov.uk/foreign-travel-advice/france")
        self.assertEqual(e["updated"], "2026-11-01")

    def test_one_page_for_several_territories_fans_out(self):
        rows = b.parse_uk({"x": uk("Cook Islands, Tokelau and Niue", [])}, INDEX)
        self.assertEqual(set(rows), {"CK", "TK", "NU"})

    def test_an_unmappable_country_is_named_in_the_error(self):
        with self.assertRaises(b.BuildError) as cm:
            b.parse_uk({"n": uk("Narnia", [])}, INDEX)
        self.assertIn("Narnia", str(cm.exception))


def page(html_text, key="1"):
    return {"response": {key: {"content": html_text}}}


ABRAT = "<p>Von Reisen nach Abchasien und Südossetien wird dringend abgeraten.</p>"


class DeTest(unittest.TestCase):
    def row(self, content="<p>Alles ruhig.</p>", **kw):
        feed = {"response": {"lastModified": 1, "contentList": ["1"], "1": de("FR", **kw)}}
        return b.parse_de(feed, VALID, {"1": page(content)})["FR"]

    def test_no_flag_and_no_abraten_is_stance_zero_and_says_formal(self):
        self.assertEqual(self.row()["stance"], 0)
        self.assertEqual(self.row()["label"], "Keine formelle Reisewarnung")
        self.assertNotIn("notes", self.row())

    def test_abraten_without_a_formal_flag_is_stance_two_with_its_own_label_and_notes(self):
        r = self.row(ABRAT)
        self.assertEqual(r["stance"], 2)
        self.assertEqual(r["label"], "Keine formelle Reisewarnung, aber Reiseabraten (siehe Hinweise)")
        self.assertEqual(r["notes"], ["Von Reisen nach Abchasien und Südossetien wird dringend abgeraten."])

    def test_partial_warning_is_stance_two_and_full_warning_stance_three(self):
        self.assertEqual(self.row(partial=True)["stance"], 2)
        self.assertEqual(self.row(partial=True)["label"], "Teilreisewarnung")
        self.assertEqual(self.row(warning=True)["stance"], 3)
        self.assertEqual(self.row(warning=True)["label"], "Reisewarnung")

    def test_a_formal_flag_keeps_its_label_even_when_the_text_says_abraten(self):
        r = self.row(ABRAT, partial=True)
        self.assertEqual((r["stance"], r["label"]), (2, "Teilreisewarnung"))
        self.assertEqual(len(r["notes"]), 1)
        # text alone never reaches 3: the "advise against travel" count stays the formal warning
        self.assertEqual(self.row(ABRAT)["stance"], 2)

    def test_a_full_warning_wins_over_a_partial_one(self):
        self.assertEqual(self.row(warning=True, partial=True)["stance"], 3)

    def test_the_url_is_the_country_page_ending_in_its_node_id(self):
        feed = {"response": {"lastModified": 1, "contentList": ["226268"], "226268": de("FR")}}
        url = b.parse_de(feed, VALID, {"226268": page("x", "226268")})["FR"]["url"]
        self.assertEqual(url, "https://www.auswaertiges-amt.de/de/-/226268")

    def test_a_non_numeric_node_id_is_an_error(self):
        feed = {"response": {"lastModified": 1, "abc": de("FR")}}
        with self.assertRaises(b.BuildError):
            b.parse_de(feed, VALID)

    def test_the_date_is_the_last_modified_epoch_in_utc(self):
        self.assertEqual(self.row()["updated"], "2026-10-13")

    def test_an_unknown_country_code_is_named_in_the_error(self):
        feed = {"response": {"1": de("ZZ")}}
        with self.assertRaises(b.BuildError) as cm:
            b.parse_de(feed, VALID)
        self.assertIn("ZZ", str(cm.exception))

    def test_a_feed_without_a_response_is_an_error(self):
        with self.assertRaises(b.BuildError):
            b.parse_de({"oops": 1}, VALID)

    def test_missing_page_text_for_a_country_with_no_flag_is_refused_never_no_warning(self):
        feed = {"response": {"lastModified": 1, "1": de("FR")}}
        with self.assertRaises(b.BuildError) as cm:
            b.parse_de(feed, VALID, {})
        self.assertIn("FR", str(cm.exception))

    def test_a_malformed_detail_counts_as_missing(self):
        feed = {"response": {"1": de("FR")}}
        for bad in ({"response": {}}, {"response": {"1": {"content": ""}}}, {"response": {"1": {"content": 5}}}, []):
            with self.assertRaises(b.BuildError):
                b.parse_de(feed, VALID, {"1": bad})

    def test_missing_page_text_for_a_flagged_country_is_tolerated_in_small_numbers(self):
        resp = {str(i): de("FR") for i in range(1, 21)}
        resp["1"] = de("FR", partial=True)
        details = {str(i): page("x", str(i)) for i in range(2, 21)}
        rows = b.parse_de({"response": resp}, VALID, details)
        self.assertEqual(rows["FR"]["stance"], 2)
        self.assertNotIn("notes", rows["FR"])

    def test_page_text_missing_for_many_flagged_countries_is_refused(self):
        resp = {str(i): de("FR", partial=True) for i in range(1, 11)}
        with self.assertRaises(b.BuildError):
            b.parse_de({"response": resp}, VALID, {})


class DeNotesTest(unittest.TestCase):
    def test_the_same_sentence_under_two_headings_is_kept_once(self):
        html_text = ("<h2>Aktuelles</h2><p>" + "Von Reisen nach Kuba wird dringend abgeraten." + "</p>"
                     "<h2>Sicherheit</h2><p>Von Reisen nach Kuba wird dringend abgeraten, siehe Sicherheit.</p>")
        self.assertEqual(b.de_notes(html_text), ["Von Reisen nach Kuba wird dringend abgeraten."])

    def test_tags_and_entities_are_removed_and_the_words_are_kept(self):
        html_text = ('<p>Von <strong>nicht notwendigen</strong> Reisen in das Gebiet um den '
                     '&bdquo;Gaza&#8209;Envelope&ldquo;&nbsp;wird <a href="x">abgeraten</a>.</p>')
        self.assertEqual(b.de_notes(html_text),
                         ["Von nicht notwendigen Reisen in das Gebiet um den „Gaza\u2011Envelope“ wird abgeraten."])

    def test_line_breaks_and_list_items_end_a_sentence(self):
        html_text = "<p>Erste Zeile ohne Punkt<br/>Von Reisen nach Katar wird abgeraten.</p><ul><li>Weiteres</li></ul>"
        self.assertEqual(b.de_notes(html_text), ["Von Reisen nach Katar wird abgeraten."])

    def test_abraten_about_something_other_than_travel_is_ignored(self):
        for text in ("Vom Alkoholkonsum in der Öffentlichkeit wird abgeraten.",
                     "Von nächtlichen Überlandfahrten wird abgeraten.",
                     "Von der Einreise mit einem Reisedokument ohne Chip wird abgeraten.",
                     "wird abgeraten.",
                     "Von Reisen auf dem Landweg nach Haiti wird dringend abgeraten.",
                     "Von Reisen wird nicht gewarnt."):
            self.assertEqual(b.de_notes(f"<p>{text}</p>"), [], text)

    def test_other_sentences_around_a_match_are_not_included(self):
        html_text = "<p>Die Lage ist ruhig. Von Reisen nach Katar wird abgeraten. Es gibt Strände.</p>"
        self.assertEqual(b.de_notes(html_text), ["Von Reisen nach Katar wird abgeraten."])

    def test_at_most_three_notes_and_long_ones_are_trimmed_at_a_word(self):
        many = "".join(f"<p>Von Reisen nach Ort{i} wird abgeraten.</p>" for i in range(6))
        self.assertEqual(len(b.de_notes(many)), 3)
        long_sentence = "Von Reisen in " + ", ".join(f"Provinz{i}" for i in range(200)) + " wird abgeraten."
        note = b.de_notes(f"<p>{long_sentence}</p>")[0]
        self.assertLessEqual(len(note), b.DE_NOTE_MAX_CHARS)
        self.assertTrue(note.endswith("…"))
        self.assertTrue(long_sentence.startswith(note[:-1]))

    def test_a_page_with_no_content_has_no_notes(self):
        self.assertEqual(b.de_notes(""), [])
        self.assertEqual(b.de_notes(None), [])


class AssembleTest(unittest.TestCase):
    def rows(self, n, prefix="A"):
        return {f"{prefix}{i}": {"stance": 0, "label": "l", "updated": "2026-01-01", "url": "u"} for i in range(n)}

    FETCHED = {"US": "t", "UK": "t", "DE": "t"}

    def test_a_country_missing_from_one_source_has_no_key_for_it(self):
        per = {"US": self.rows(151), "UK": self.rows(151), "DE": self.rows(151)}
        del per["DE"]["A5"]
        doc = b.assemble(per, self.FETCHED, "now")
        self.assertNotIn("DE", doc["countries"]["A5"])
        self.assertIn("US", doc["countries"]["A5"])
        self.assertEqual(doc["dataFormat"], 1)

    def test_a_feed_with_implausibly_few_countries_is_refused(self):
        per = {"US": self.rows(150), "UK": self.rows(10), "DE": self.rows(150)}
        with self.assertRaises(b.BuildError) as cm:
            b.assemble(per, self.FETCHED, "now")
        self.assertIn("UK", str(cm.exception))


class KeepIfUnchangedTest(unittest.TestCase):
    def doc(self, gen, fetched, stance=0):
        return {"dataFormat": 1, "generatedAt": gen,
                "sources": {"US": {"label": "US State Department", "fetchedAt": fetched}},
                "countries": {"FR": {"US": {"stance": stance}}}}

    def test_unchanged_countries_keep_the_previous_file_including_its_dates(self):
        old = self.doc("2026-11-18T06:30:00Z", "2026-11-18T06:29:00Z")
        new = self.doc("2026-11-25T06:30:00Z", "2026-11-25T06:29:00Z")
        self.assertIs(b.keep_if_unchanged(new, old), old)

    def test_a_changed_advisory_takes_the_new_file(self):
        old = self.doc("a", "a")
        new = self.doc("b", "b", stance=3)
        self.assertIs(b.keep_if_unchanged(new, old), new)

    def test_a_missing_or_malformed_previous_file_takes_the_new_file(self):
        new = self.doc("b", "b")
        self.assertIs(b.keep_if_unchanged(new, None), new)
        self.assertIs(b.keep_if_unchanged(new, {"countries": 1}), new)


class BuildTest(unittest.TestCase):
    def test_a_failed_build_writes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "advisory.json"
            src = Path(d) / "src"
            src.mkdir()
            (src / "us.json").write_text("[]")  # the other feeds are missing
            rc = b.main(["--source", str(src), "--out", str(out)])
            self.assertEqual(rc, 1)
            self.assertFalse(out.exists())

    def test_a_truncated_feed_set_writes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / "src"
            (src / "uk").mkdir(parents=True)
            (src / "us.json").write_text(json.dumps([us("France - Level 1: Exercise Normal Precautions")]))
            (src / "de.json").write_text(json.dumps({"response": {"1": de("FR")}}))
            (src / "de_detail").mkdir()
            (src / "de_detail" / "1.json").write_text(json.dumps(page("x")))
            (src / "uk_index.json").write_text(json.dumps({"links": {"children": [{"details": {"country": {"slug": "france"}}}]}}))
            (src / "uk" / "france.json").write_text(json.dumps(uk("France", [])))
            out = Path(d) / "advisory.json"
            self.assertEqual(b.main(["--source", str(src), "--out", str(out)]), 1)
            self.assertFalse(out.exists())

    def test_a_cache_without_detail_pages_writes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / "src"
            (src / "uk").mkdir(parents=True)
            (src / "us.json").write_text(json.dumps([us("France - Level 1: Exercise Normal Precautions")]))
            (src / "de.json").write_text(json.dumps({"response": {"1": de("FR")}}))
            (src / "uk_index.json").write_text(json.dumps({"links": {"children": [{"details": {"country": {"slug": "france"}}}]}}))
            (src / "uk" / "france.json").write_text(json.dumps(uk("France", [])))
            out = Path(d) / "advisory.json"
            self.assertEqual(b.main(["--source", str(src), "--out", str(out)]), 1)
            self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()
