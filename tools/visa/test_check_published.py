import json
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from check_published import check  # noqa: E402

NOW = datetime(2026, 11, 15, tzinfo=timezone.utc)


def manifest(**over):
    m = {"manifestVersion": 1, "dataFormat": 1,
         "generatedAt": (NOW - timedelta(days=2)).isoformat(timespec="seconds"),
         "passports": {f"P{i}": {} for i in range(160)}}
    m.update(over)
    return json.dumps(m)


class CheckPublishedTest(unittest.TestCase):
    def test_a_fresh_complete_manifest_has_no_problems(self):
        self.assertEqual(check(manifest(), NOW), [])

    def test_a_manifest_older_than_ten_days_is_stale(self):
        old = (NOW - timedelta(days=11)).isoformat()
        self.assertEqual(len(check(manifest(generatedAt=old), NOW)), 1)

    def test_too_few_passports_is_a_problem(self):
        self.assertEqual(len(check(manifest(passports={"DE": {}}), NOW)), 1)

    def test_a_wrong_version_is_a_problem(self):
        self.assertEqual(len(check(manifest(dataFormat=2, manifestVersion=2), NOW)), 2)

    def test_non_json_is_a_problem(self):
        self.assertEqual(len(check("<html>", NOW)), 1)


if __name__ == "__main__":
    unittest.main()
