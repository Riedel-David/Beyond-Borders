"""Fetch one file from the live GitHub Pages site, telling "not published yet" from "broken".

Usage: python3 -I tools/advisory/fetch_live.py <url> <dest> [--probe <url>]

Exit 0: the file was fetched (HTTP 200) and written to <dest>.
Exit 3: the file is not live: HTTP 404, or HTTP 503 that outlasts the retries while the site's --probe
        URL (a file that certainly exists) answers 200. Nothing is written. Pages answers the first
        request for a path it has never served with 503 and the next with 404, so a new file shows up
        as a few 503s and then a 404; retrying is what turns that into a plain "absent".
Exit 1: anything else (other status, connection failure, a 503 on a site whose probe also fails). The
        message names the URL and the status. This is never read as "absent", because treating a real
        outage as "nothing published" would publish an older copy over a newer one.
"""
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

RETRY_STATUSES = {429, 500, 502, 503, 504}
ABSENT, OK, ERROR = 3, 0, 1


class LiveError(Exception):
    pass


def get(url: str):
    """(status, body); a connection failure is status 0."""
    req = urllib.request.Request(url, headers={"User-Agent": "BeyondBorders-advisory-build (+https://github.com/Riedel-David/Beyond-Borders)",
                                               "Cache-Control": "no-cache"})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, b""
    except Exception:  # noqa: BLE001 - reported as status 0
        return 0, b""


def fetch_live(url: str, probe: str = None, fetch=get, sleep=time.sleep, tries: int = 5):
    """Returns (ABSENT, None) or (OK, body); raises LiveError."""
    status = 0
    for attempt in range(tries):
        status, body = fetch(url)
        if status == 200:
            return OK, body
        if status == 404:
            return ABSENT, None
        if status not in RETRY_STATUSES and status != 0:
            raise LiveError(f"{url} answered HTTP {status}")
        if attempt < tries - 1:
            sleep(5 * (attempt + 1))
    if status == 503 and probe:
        probe_status, _ = fetch(probe)
        if probe_status == 200:
            return ABSENT, None
        raise LiveError(f"{url} answered HTTP 503 {tries} times and the site's {probe} answered {probe_status}")
    raise LiveError(f"{url} answered HTTP {status or 'nothing (connection failed)'} {tries} times")


def main(argv) -> int:
    probe = None
    if "--probe" in argv:
        i = argv.index("--probe")
        probe = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        code, body = fetch_live(argv[0], probe)
    except LiveError as e:
        print(f"live fetch FAILED: {e}", file=sys.stderr)
        return ERROR
    if code == OK:
        Path(argv[1]).write_bytes(body)
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
