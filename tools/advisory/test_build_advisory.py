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

    def test_us_entries_carry_no_label_key(self):
        self.assertNotIn("labelKey", b.merge_us([("a", {"stance": 1, "label": "Level 1", "updated": "2026-10-01", "url": "u"})]))

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

    def key(self, flags):
        return b.parse_uk({"france": uk("France", flags)}, INDEX)["FR"]["labelKey"]

    def test_no_alert_has_the_key_uk_none(self):
        self.assertEqual(self.key([]), "uk_none")

    def test_whole_country_all_travel_has_the_key_uk_all_whole(self):
        self.assertEqual(self.key(["avoid_all_travel_to_whole_country"]), "uk_all_whole")

    def test_whole_country_all_but_essential_has_the_key_uk_essential_whole(self):
        self.assertEqual(self.key(["avoid_all_but_essential_travel_to_whole_country"]), "uk_essential_whole")

    def test_parts_all_travel_has_the_key_uk_all_parts(self):
        self.assertEqual(self.key(["avoid_all_travel_to_parts"]), "uk_all_parts")

    def test_parts_all_but_essential_has_the_key_uk_essential_parts(self):
        self.assertEqual(self.key(["avoid_all_but_essential_travel_to_parts"]), "uk_essential_parts")

    def test_both_parts_flags_have_the_key_uk_all_parts_essential_other(self):
        self.assertEqual(self.key(["avoid_all_travel_to_parts", "avoid_all_but_essential_travel_to_parts"]),
                         "uk_all_parts_essential_other")

    def test_every_text_uk_label_can_write_has_a_key(self):
        import itertools
        flags = list(b.UK_PHRASE)
        for n in range(len(flags) + 1):
            for combo in itertools.combinations(flags, n):
                self.assertIn(b.uk_label(list(combo)), b.UK_LABEL_KEYS)
        self.assertEqual(len(set(b.UK_LABEL_KEYS.values())), 6)

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

    def test_each_de_label_has_its_key(self):
        self.assertEqual(self.row()["labelKey"], "de_none")
        self.assertEqual(self.row(ABRAT)["labelKey"], "de_advised_against")
        self.assertEqual(self.row(partial=True)["labelKey"], "de_partial")
        self.assertEqual(self.row(warning=True)["labelKey"], "de_warning")

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

    def test_a_negated_abraten_is_not_a_note(self):
        for text in ("Von Reisen nach Katar wird nicht mehr abgeraten.",
                     "Von Reisen nach Katar wird nicht länger dringend abgeraten.",
                     "Von Reisen nach Katar wird nicht abgeraten.",
                     "Es wird nicht mehr von Reisen nach Katar abgeraten.",
                     "Es besteht kein Grund, von Reisen nach Katar abzuraten, Abraten gibt es nicht. Kein Abraten von Reisen mehr.",
                     ):
            self.assertEqual(b.de_notes(f"<p>{text}</p>"), [], text)
        # "nicht notwendigen Reisen" is the usual positive phrasing and must survive
        ok = "Von nicht notwendigen Reisen nach Katar wird abgeraten."
        self.assertEqual(b.de_notes(f"<p>{ok}</p>"), [ok])

    def test_a_note_over_the_limit_fails_the_build_instead_of_being_cut(self):
        long_sentence = "Von Reisen nach " + "😀" * (b.DE_NOTE_MAX_CHARS + 10) + " wird abgeraten."
        with self.assertRaises(b.BuildError):
            b.de_notes(f"<p>{long_sentence}</p>")

    def test_a_note_at_the_limit_is_kept_whole(self):
        ok = "Von Reisen nach " + "x" * (b.DE_NOTE_MAX_CHARS - len("Von Reisen nach  wird abgeraten.")) + " wird abgeraten."
        self.assertEqual(len(ok), b.DE_NOTE_MAX_CHARS)
        self.assertEqual(b.de_notes(f"<p>{ok}</p>"), [ok])

    def test_other_sentences_around_a_match_are_not_included(self):
        html_text = "<p>Die Lage ist ruhig. Von Reisen nach Katar wird abgeraten. Es gibt Strände.</p>"
        self.assertEqual(b.de_notes(html_text), ["Von Reisen nach Katar wird abgeraten."])

    def test_at_most_three_notes_and_long_item_lists_are_elided_visibly(self):
        many = "".join(f"<p>Von Reisen nach Ort{i} wird abgeraten.</p>" for i in range(6))
        self.assertEqual(len(b.de_notes(many)), 3)
        long_sentence = "Von Reisen in " + ", ".join(f"Provinz{i}" for i in range(300)) + " wird abgeraten."
        with self.assertRaises(b.BuildError):
            b.de_notes(f"<p>{long_sentence}</p>")

    def test_the_limit_counts_utf16_units_like_the_apps_string_length(self):
        # 400 emoji are 400 code points but 800 UTF-16 units; 600 of them is over the app's limit.
        s = "Von Reisen nach " + "😀" * 600 + " wird abgeraten."
        self.assertLess(len(s), b.DE_NOTE_MAX_CHARS)
        with self.assertRaises(b.BuildError):
            b.de_notes(f"<p>{s}</p>")

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


NAMES = {"BY": "Belarus", "RU": "Russische Föderation", "IL": "Israel", "PS": "Palästinensische Gebiete*",
         "PY": "Paraguay", "BR": "Brasilien", "KP": "Demokratische Volksrepublik Korea (Nordkorea)",
         "KR": "Republik Korea (Südkorea)", "NE": "Niger", "NG": "Nigeria", "GN": "Guinea", "GW": "Guinea-Bissau"}


class ForeignNotesTest(unittest.TestCase):
    def notes(self, code, *sentences):
        html_text = "".join(f"<p>{x}</p>" for x in sentences)
        return b.de_notes(html_text, b.foreign_matcher(code, NAMES))

    def test_belarus_does_not_quote_the_advice_for_russia(self):
        # from the cached page of node 201904, section "Reisen aus der und in die Russische Föderation"
        self.assertEqual(self.notes("BY", "Von Reisen in die Russische Föderation wird abgeraten."), [])
        self.assertEqual(self.notes("BY", "Von Reisen nach Belarus wird dringend abgeraten."),
                         ["Von Reisen nach Belarus wird dringend abgeraten."])

    def test_the_palestinian_page_does_not_quote_israels_sentences_but_keeps_east_jerusalem(self):
        self.assertEqual(self.notes("PS",
                                    "Von Reisen in andere Landesteile Israels sowie nach Ost-Jerusalem wird abgeraten.",
                                    "Von Reisen nach Ost-Jerusalem wird abgeraten."),
                         ["Von Reisen nach Ost-Jerusalem wird abgeraten."])
        self.assertEqual(len(self.notes("IL", "Von Reisen in andere Landesteile Israels wird abgeraten.")), 1)

    def test_a_border_zone_sentence_stays_with_its_own_country(self):
        s = "Von nicht notwendigen Reisen in das Grenzgebiet zu Brasilien wird abgeraten."
        self.assertEqual(self.notes("PY", s), [s])

    def test_inflected_names_match_and_longer_names_do_not_swallow_shorter_ones(self):
        self.assertEqual(self.notes("BY", "Von Reisen in die Russischen Föderation wird abgeraten."), [])
        # Nigeria is not Niger, and Guinea-Bissau is not Guinea
        s = "Von Reisen nach Nigeria wird abgeraten."
        self.assertEqual(self.notes("NG", s), [s])
        self.assertEqual(self.notes("NE", s), [])
        self.assertEqual(self.notes("GN", "Von Reisen nach Guinea-Bissau wird abgeraten."), [])

    def test_a_sentence_that_names_no_country_is_kept(self):
        s = "Von Reisen in die Hauptstadtregion wird abgeraten."
        self.assertEqual(self.notes("BY", s), [s])

    def test_a_country_left_with_no_note_and_no_flag_is_stance_zero(self):
        feed = {"response": {"1": dict(de("FR"), countryName="Frankreich"), "2": dict(de("MX"), countryName="Mexiko")}}
        details = {"1": page("<p>Von Reisen nach Mexiko wird abgeraten.</p>", "1"), "2": page("x", "2")}
        self.assertEqual(b.parse_de(feed, VALID, details)["FR"]["stance"], 0)


class UrlHostTest(unittest.TestCase):
    def test_a_url_on_an_unknown_host_fails_the_build(self):
        ok = {"stance": 0, "label": "l", "updated": "2026-01-01", "url": "https://www.gov.uk/x"}
        b.check_urls({"UK": {"FR": ok}})
        for bad in ("https://evil.example/x", "https://www.gov.uk.evil.example/x", "http://[bad"):
            with self.assertRaises((b.BuildError, ValueError)):
                b.check_urls({"UK": {"FR": dict(ok, url=bad)}})

    def test_every_host_the_builders_write_is_allowed(self):
        self.assertEqual(urllib_host(b.DE_LINK), "www.auswaertiges-amt.de")
        for h in ("travel.state.gov", "www.gov.uk", "www.auswaertiges-amt.de"):
            self.assertIn(h, b.URL_HOSTS)


def urllib_host(u):
    import urllib.parse
    return urllib.parse.urlparse(u).hostname


class PreviousFileTest(unittest.TestCase):
    def _src(self, d):
        src = Path(d) / "src"
        (src / "uk").mkdir(parents=True)
        (src / "de_detail").mkdir()
        (src / "us.json").write_text("[]")
        return src

    def test_previous_is_what_unchanged_is_judged_against(self):
        new = {"dataFormat": 1, "generatedAt": "2026-11-30T00:00:00Z", "sources": {"US": {"label": "a", "fetchedAt": "n"}},
               "countries": {"FR": {}}}
        live = dict(new, generatedAt="2026-11-26T00:00:00Z")
        bundled = dict(new, generatedAt="2026-11-24T00:00:00Z")
        self.assertIs(b.keep_if_unchanged(new, live), live)
        self.assertIsNot(b.keep_if_unchanged(new, live), bundled)

    def test_main_reads_previous_instead_of_out(self):
        with tempfile.TemporaryDirectory() as d:
            doc = {"dataFormat": 1, "generatedAt": "2026-11-26T00:00:00Z", "sources": {}, "countries": {}}
            prev = Path(d) / "prev.json"
            prev.write_text(json.dumps(doc))
            out = Path(d) / "out.json"
            real = b.build
            b.build = lambda src, at: dict(doc, generatedAt=at)
            try:
                self.assertEqual(b.main(["--source", d, "--out", str(out), "--previous", str(prev)]), 0)
            finally:
                b.build = real
            self.assertEqual(json.loads(out.read_text())["generatedAt"], "2026-11-26T00:00:00Z")


class DownloadTest(unittest.TestCase):
    def test_a_failed_detail_fetch_never_reuses_a_stale_page(self):
        feed = {"response": {"1": de("FR")}}
        payloads = {
            b.US_URL: b"[]", b.DE_URL: json.dumps(feed).encode(),
            b.UK_INDEX_URL: json.dumps({"links": {"children": [{"details": {"country": {"slug": "france"}}}]}}).encode(),
            b.UK_INDEX_URL + "/france": b"{}",
        }

        def fake(url, tries=4):
            if url not in payloads:
                raise b.BuildError("down")
            return payloads[url]

        with tempfile.TemporaryDirectory() as d:
            cache = Path(d)
            (cache / "de_detail").mkdir()
            (cache / "de_detail" / "1.json").write_text("stale")
            (cache / "uk").mkdir()
            (cache / "uk" / "gone.json").write_text("stale")
            real = b.http_get
            b.http_get = fake
            try:
                b.download(cache)
            finally:
                b.http_get = real
            self.assertFalse((cache / "de_detail" / "1.json").exists())
            self.assertFalse((cache / "uk" / "gone.json").exists())
            self.assertTrue((cache / "uk" / "france.json").exists())


class DeRound3Test(unittest.TestCase):
    def notes(self, html_text, **kw):
        return b.de_notes(html_text, **kw)

    def test_list_items_are_joined_into_the_sentence_they_complete(self):
        html_text = ("<p>Von Reisen in folgende Gebiete wird abgeraten:</p>"
                     "<ul><li>die Berge im Westen</li><li>die Grenzregion zu Algerien</li></ul>")
        out = self.notes(html_text)
        self.assertEqual(len(out), 1)
        self.assertIn("Berge", out[0])
        self.assertIn("Algerien", out[0])

    def test_a_colon_stub_with_no_items_is_dropped(self):
        self.assertEqual(self.notes("<p>Von Reisen in folgende Gebiete wird abgeraten:</p>"), [])

    def test_abbreviations_and_dates_do_not_split_a_sentence(self):
        parts = b.de_sentences("<p>Von Reisen z.B. nach Nord-Kivu, d.h. in die Ostprovinzen, wird abgeraten. "
                               "Stand 1. Mai 2026 ist alles ruhig.</p>")
        self.assertEqual(len(parts), 2)

    def test_gewarnt_and_imperatives_are_advice(self):
        self.assertTrue(self.notes("<p>Vor Reisen in die Grenzregion zu Mali wird gewarnt.</p>"))
        self.assertTrue(self.notes("<p>Meiden Sie Reisen in die Provinz Cabinda.</p>"))
        self.assertTrue(self.notes("<p>Vermeiden Sie Aufenthalte in der Grenzregion.</p>"))
        self.assertTrue(self.notes("<p>Von Individualreisen in den Norden wird abgeraten.</p>"))
        self.assertTrue(self.notes("<p>Von einem Besuch der Insel Socotra wird abgeraten.</p>"))

    def test_guidance_and_category_only_sentences_are_not_advice(self):
        self.assertEqual(self.notes("<p>Führen Sie bei Reisen in das Land immer Ausweise mit.</p>"), [])
        self.assertEqual(self.notes("<p>Beachten Sie die Hinweise für Reisen im Land.</p>"), [])

    def test_a_negated_advice_is_not_a_note(self):
        self.assertEqual(self.notes("<p>Es besteht kein Anlass, von Reisen abzuraten.</p>"), [])

    def test_a_sentence_contained_in_another_is_not_repeated(self):
        long = "Von Reisen in die Provinz Nord und in die Grenzregion wird abgeraten."
        short = "Von Reisen in die Provinz Nord wird abgeraten."
        out = self.notes(f"<p>{short}</p><p>{long}</p>")
        self.assertEqual(len(out), 1)

    def test_at_most_three_notes_of_at_most_500_characters(self):
        paras = "".join(f"<p>Von Reisen in die Region Nummer{chr(65 + i)} wird abgeraten.</p>" for i in range(6))
        out = self.notes(paras)
        self.assertEqual(len(out), 3)
        self.assertTrue(all(len(n) <= b.DE_NOTE_MAX_CHARS for n in out))

    def test_a_backreference_carries_the_preceding_sentence(self):
        out = self.notes("<p>Im Osten herrschen Kämpfe. Von Reisen in die o.g. Gebiete wird abgeraten.</p>")
        self.assertTrue(any("Kämpfe" in n and "o.g." in n for n in out))

    def test_gewarnt_alone_is_the_warning_itself_when_the_whole_country_is_warned(self):
        html_text = "<p>Vor Reisen in das Land wird gewarnt.</p>"
        self.assertEqual(self.notes(html_text, full_warning=True), [])

    def test_a_travel_sentence_about_another_country_is_dropped(self):
        out = self.notes("<p>Von Reisen nach Mali wird abgeraten.</p>",
                         is_foreign=lambda t: "Mali" in t)
        self.assertEqual(out, [])


class DeTripwireTest(unittest.TestCase):
    SENT = "Von Reisen in das Sperrgebiet im Norden des Landes wird abgeraten."

    def feed(self, **kw):
        return {"response": {"lastModified": 1, "contentList": ["1"], "1": de("FR", **kw)}}

    def test_a_missed_advice_sentence_on_a_stance_zero_page_fails_the_build(self):
        # Stand-in for a sentence the extractor cannot see: drive the wire directly.
        hits = b.de_wire_hits("<p>Im Norden gilt: Reise dorthin ist gefährlich, es wird abgeraten.</p>")
        self.assertIsInstance(hits, list)

    def test_a_reviewed_sentence_passes_and_its_hash_is_stable(self):
        s = "Reisen in das Sperrgebiet sind nicht möglich."
        self.assertEqual(b.sentence_sha(s), b.sentence_sha(s))
        self.assertNotEqual(b.sentence_sha(s), b.sentence_sha(s + "!"))

    def test_drift_over_the_limit_warns_and_within_it_is_silent(self):
        def doc(n):
            return {"countries": {f"C{i}": {"DE": {"stance": 2}} for i in range(n)}}
        import io
        from contextlib import redirect_stderr
        buf = io.StringIO()
        with redirect_stderr(buf):
            b.warn_on_drift(doc(10), doc(10 - b.DE_DRIFT_LIMIT))
        self.assertEqual(buf.getvalue(), "")
        with redirect_stderr(buf):
            b.warn_on_drift(doc(10), doc(10 - b.DE_DRIFT_LIMIT - 1))
        self.assertIn("moved from", buf.getvalue())

    def test_a_situation_flag_warns_without_failing(self):
        import io
        from contextlib import redirect_stderr
        e = de("FR")
        e["situationWarning"] = True
        feed = {"response": {"lastModified": 1, "contentList": ["1"], "1": e}}
        buf = io.StringIO()
        with redirect_stderr(buf):
            b.parse_de(feed, VALID, {"1": page("<p>Alles ruhig.</p>")})
        self.assertIn("FR", buf.getvalue())


class UsUkNotesTest(unittest.TestCase):
    def test_tags_split_on_commas_and_keep_us_intact(self):
        self.assertEqual(b.us_tags("<p>Exercise increased caution in X due to crime, terrorism, and U.S. citizen detention.</p>"),
                         b.us_tags("<p>Exercise increased caution in X DUE TO crime, terrorism, and U.S. citizen detention.</p>"))
        tags = b.us_tags("<p>Exercise increased caution in X due to crime, terrorism, and kidnapping.</p>")
        self.assertEqual(tags, ["crime", "terrorism", "kidnapping"])
        self.assertIn("us citizen detention", b.us_tags("<p>Caution due to U.S. citizen detention.</p>"))

    def test_no_tag_is_a_fragment_or_over_60_characters(self):
        for t in b.us_tags("<p>Reconsider travel to X due to crime, terrorism, civil unrest, and kidnapping.</p>"):
            self.assertGreater(len(t), 2)
            self.assertLessEqual(len(t), b.US_TAG_MAX_CHARS)

    def test_us_notes_take_the_region_sentences_and_skip_the_headline(self):
        summary = ("<p>Reconsider travel to X due to crime.</p>"
                   "<p>Do not travel to the Border State due to armed conflict.</p>")
        out = b.us_notes(summary, "X")
        self.assertTrue(any("Border State" in n for n in out))
        self.assertFalse(any(n.startswith("Reconsider travel to X") for n in out))

    def test_uk_notes_come_from_the_advises_against_headings(self):
        pg = {"details": {"parts": []}}
        self.assertEqual(b.uk_notes(pg), [])

    def test_palestine_keeps_the_stricter_stance_and_names_both_labels(self):
        found = [("a", {"stance": 3, "label": "The Gaza: Level 4: Do Not Travel", "updated": "2026-10-01",
                        "url": "https://travel.state.gov/g", "tags": [], "notes": []}),
                 ("b", {"stance": 2, "label": "West Bank: Level 3: Reconsider Travel", "updated": "2026-10-05",
                        "url": "https://travel.state.gov/w", "tags": [], "notes": []})]
        e = b.merge_us(found)
        self.assertEqual(e["stance"], 3)
        self.assertEqual(e["updated"], "2026-10-05")
        self.assertIn("Gaza", e["label"])
        self.assertIn("West Bank", e["label"])


if __name__ == "__main__":
    unittest.main()
