"""Host tests for tools/advisory/fetch_live.py.

Run: python3 -m unittest tools/advisory/test_fetch_live.py
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import fetch_live as f  # noqa: E402

URL, PROBE = "https://x/advisory.json", "https://x/manifest.json"


def script(answers):
    """A fetch that replays (status, body) per call and records the urls asked."""
    asked = []

    def fetch(url):
        asked.append(url)
        return answers[min(len(asked) - 1, len(answers) - 1)] if not isinstance(answers, dict) else answers[url]
    return fetch, asked


class FetchLiveTest(unittest.TestCase):
    def run_fetch(self, answers, **kw):
        fetch, asked = script(answers)
        return f.fetch_live(URL, PROBE, fetch=fetch, sleep=lambda s: None, **kw), asked

    def test_a_200_returns_the_body(self):
        (code, body), _ = self.run_fetch([(200, b"{}")])
        self.assertEqual((code, body), (f.OK, b"{}"))

    def test_the_first_request_503_then_404_is_absent_not_an_error(self):
        (code, body), asked = self.run_fetch([(503, b""), (503, b""), (404, b"")])
        self.assertEqual((code, body, len(asked)), (f.ABSENT, None, 3))

    def test_a_503_that_never_clears_is_absent_only_while_the_site_itself_is_up(self):
        fetch, _ = script({URL: (503, b""), PROBE: (200, b"{}")})
        self.assertEqual(f.fetch_live(URL, PROBE, fetch=fetch, sleep=lambda s: None, tries=3), (f.ABSENT, None))
        fetch, _ = script({URL: (503, b""), PROBE: (503, b"")})
        with self.assertRaises(f.LiveError):
            f.fetch_live(URL, PROBE, fetch=fetch, sleep=lambda s: None, tries=3)

    def test_other_statuses_and_dead_connections_are_errors(self):
        for status in (403, 410, 301):
            with self.assertRaises(f.LiveError, msg=status):
                self.run_fetch([(status, b"")])
        with self.assertRaises(f.LiveError):
            self.run_fetch([(0, b"")], tries=3)

    def test_a_transient_failure_followed_by_a_200_succeeds(self):
        (code, body), asked = self.run_fetch([(502, b""), (0, b""), (200, b"ok")])
        self.assertEqual((code, body, len(asked)), (f.OK, b"ok", 3))


if __name__ == "__main__":
    unittest.main()
