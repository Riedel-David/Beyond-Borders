"""Health check of the PUBLISHED visa site (the manifest the app's updater reads).

Usage: python3 -I tools/visa/check_published.py [url]
Prints one problem per line and always exits 0; visa-freshness.yml turns each line into an
issue. Nothing printed means the site looks healthy.
"""
import json
import sys
import urllib.request
from datetime import datetime, timedelta, timezone

URL = "https://riedel-david.github.io/Beyond-Borders/manifest.json"
MAX_AGE = timedelta(days=10)
MIN_PASSPORTS = 150


def check(text: str, now: datetime) -> list:
    try:
        m = json.loads(text)
    except ValueError:
        return ["the manifest is not valid JSON"]
    if not isinstance(m, dict):
        return ["the manifest is not a JSON object"]
    problems = []
    for key in ("manifestVersion", "dataFormat"):
        if m.get(key) != 1:
            problems.append(f"{key} is {m.get(key)!r}, expected 1")
    try:
        generated = datetime.fromisoformat(str(m.get("generatedAt")).replace("Z", "+00:00"))
        if generated.tzinfo is None:
            generated = generated.replace(tzinfo=timezone.utc)
        if now - generated > MAX_AGE:
            problems.append(f"generatedAt {m['generatedAt']} is older than {MAX_AGE.days} days")
    except ValueError:
        problems.append(f"generatedAt {m.get('generatedAt')!r} is not a date")
    passports = m.get("passports")
    count = len(passports) if isinstance(passports, dict) else 0
    if count < MIN_PASSPORTS:
        problems.append(f"only {count} passports, expected at least {MIN_PASSPORTS}")
    return problems


def main(argv: list) -> int:
    url = argv[1] if len(argv) > 1 else URL
    try:
        with urllib.request.urlopen(url, timeout=20) as r:
            text = r.read().decode("utf-8")
    except Exception as e:  # HTTP error, DNS, timeout, bad encoding
        print(f"the manifest could not be fetched: {e}")
        return 0
    for p in check(text, datetime.now(timezone.utc)):
        print(p)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
