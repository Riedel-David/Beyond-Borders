"""Assemble the GitHub Pages folder for advisory-refresh.yml: the new advisory.json plus the visa
files that are live right now.

Usage: python3 -I tools/advisory/make_site.py [--require-signature] <advisory.json> <base-url> <out-dir>
       python3 -I tools/advisory/make_site.py --check <advisory.json>
       python3 -I tools/advisory/make_site.py --check-heartbeat <advisory-heartbeat.json> [--max-age-days N]
       python3 -I tools/advisory/make_site.py --check-advisory-age <advisory.json> [--max-age-days N]

A Pages deployment replaces the whole site, so publishing advisory.json alone would delete
manifest.json and every visa_<code>.json, and the app's visa updater would find nothing. This copies
the live manifest and each file it lists (checked against the manifest's own sha256 and size, so a
half-served or tampered file is never re-published), then adds advisory.json beside them. The live
manifest.sig travels with manifest.json as a pair: the signature covers the manifest's exact bytes, so a
manifest carried without it would make every app that requires signatures reject updates. A site with
no manifest.sig yet (signing not rolled out) is carried without one unless --require-signature is
given, which the workflow sets once signing is live; then a missing signature refuses the run. The other
direction is handled in visa-refresh.yml, which carries the live advisory.json into its own site.

Every run also writes advisory-heartbeat.json ({"refreshedAt": ...}). advisory.json itself is stable
when no advisory changed, so it cannot say whether the weekly job is still running; the heartbeat can.
visa-refresh.yml carries it along like advisory.json, and visa-freshness.yml fails when it is old.

Refuses (exit 1, writes nothing) unless the out dir is empty, advisory.json is a well-formed
dataFormat 1 file, and every file the live manifest lists downloads and verifies.
"""
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

MANIFEST_FILE_RE = re.compile(r"visa_[a-z]{2}\.json")


SIGNATURE_FILE = "manifest.sig"
HEARTBEAT_FILE = "advisory-heartbeat.json"
# The job runs weekly. A single missed run is 7 days of silence plus the weekday offset of the
# check, so 10 days fails on the first missed week and tolerates a late one.
HEARTBEAT_MAX_AGE_DAYS = 10


class SiteError(Exception):
    pass


def heartbeat(now: datetime) -> bytes:
    return (json.dumps({"refreshedAt": now.strftime("%Y-%m-%dT%H:%M:%SZ")}) + "\n").encode()


def check_heartbeat(raw: bytes, now: datetime, max_age_days: int = None) -> datetime:
    """Parses a heartbeat; with max_age_days also refuses one that is older (or from the future)."""
    try:
        at = datetime.strptime(json.loads(raw)["refreshedAt"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except (ValueError, KeyError, TypeError) as e:
        raise SiteError(f"heartbeat does not parse: {e!r}")
    if max_age_days is not None:
        if at > now + timedelta(hours=1):
            raise SiteError(f"heartbeat is dated in the future ({at:%Y-%m-%d})")
        if now - at > timedelta(days=max_age_days):
            raise SiteError(f"heartbeat is from {at:%Y-%m-%d}, {(now - at).days} days ago (limit {max_age_days})")
    return at


def check_advisory_age(raw: bytes, now: datetime, max_age_days: int) -> datetime:
    """The live advisory.json's generatedAt, refused when older than max_age_days. Used when no
    heartbeat exists yet: a bundled copy carried by visa-refresh is not a missed refresh until the
    first scheduled advisory-refresh run has had time to happen."""
    try:
        at = datetime.strptime(json.loads(raw)["generatedAt"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except (ValueError, KeyError, TypeError) as e:
        raise SiteError(f"advisory.json generatedAt does not parse: {e!r}")
    if now - at > timedelta(days=max_age_days):
        raise SiteError(f"advisory.json is from {at:%Y-%m-%d}, {(now - at).days} days ago (limit {max_age_days})")
    return at


def fetch(url: str) -> bytes:
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(url, timeout=40) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
    raise SiteError(f"cannot fetch {url}: {last}")


def fetch_optional(url: str):
    """The body, or None on HTTP 404 (not published); any other failure raises SiteError."""
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(url, timeout=40) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last = e
        except Exception as e:  # noqa: BLE001
            last = e
    raise SiteError(f"cannot fetch {url}: {last}")


def check_advisory(raw: bytes) -> None:
    try:
        doc = json.loads(raw)
    except ValueError as e:
        raise SiteError(f"advisory.json does not parse: {e}")
    if not isinstance(doc, dict) or doc.get("dataFormat") != 1:
        raise SiteError("advisory.json is not dataFormat 1")
    if not isinstance(doc.get("countries"), dict) or len(doc["countries"]) < 150:
        raise SiteError("advisory.json has too few countries")


def build(advisory: bytes, base: str, get=fetch, now: datetime = None, get_optional=fetch_optional,
          require_signature: bool = False) -> dict:
    """Returns {file name: bytes} for the whole site."""
    check_advisory(advisory)
    base = base.rstrip("/") + "/"
    manifest_raw = get(base + "manifest.json")
    try:
        manifest = json.loads(manifest_raw)
        passports = manifest["passports"]
    except (ValueError, KeyError, TypeError) as e:
        raise SiteError(f"live manifest.json is unusable: {e!r}")
    if not passports:
        raise SiteError("live manifest.json lists no passports")
    files = {"manifest.json": manifest_raw}
    signature = get_optional(base + SIGNATURE_FILE)
    if signature is None:
        if require_signature:
            raise SiteError("the live manifest.json has no manifest.sig, and signing is live: carrying the "
                            "manifest without it would make every signature-requiring app reject updates")
    elif not signature.strip():
        raise SiteError("the live manifest.sig is empty")
    else:
        files[SIGNATURE_FILE] = signature
    for code, meta in sorted(passports.items()):
        name = meta.get("file", "")
        if not MANIFEST_FILE_RE.fullmatch(name):
            raise SiteError(f"manifest lists an unexpected file name {name!r}")
        raw = get(base + name)
        if len(raw) != meta.get("bytes") or hashlib.sha256(raw).hexdigest() != meta.get("sha256"):
            raise SiteError(f"{name} does not match the live manifest's size and sha256")
        files[name] = raw
    files["advisory.json"] = advisory
    files[HEARTBEAT_FILE] = heartbeat(now or datetime.now(timezone.utc))
    return files


def main(argv) -> int:
    if len(argv) == 2 and argv[0] == "--check":
        # Used by visa-refresh.yml to vet an advisory.json it is about to carry into its own site.
        try:
            check_advisory(Path(argv[1]).read_bytes())
        except (SiteError, OSError) as e:
            print(f"{argv[1]} is not a usable advisory.json: {e}", file=sys.stderr)
            return 1
        return 0
    if argv and argv[0] in ("--check-heartbeat", "--check-advisory-age"):
        check = check_advisory_age if argv[0] == "--check-advisory-age" else check_heartbeat
        days = HEARTBEAT_MAX_AGE_DAYS
        if len(argv) == 4 and argv[2] == "--max-age-days":
            days = int(argv[3])
        elif len(argv) != 2:
            print(__doc__, file=sys.stderr)
            return 2
        try:
            at = check(Path(argv[1]).read_bytes(), datetime.now(timezone.utc), days)
        except (SiteError, OSError) as e:
            print(f"{argv[1]}: {e}", file=sys.stderr)
            return 1
        what = "advisory data generated" if argv[0] == "--check-advisory-age" else "advisory refresh last ran"
        print(f"{what} {at:%Y-%m-%d %H:%M} UTC")
        return 0
    require = "--require-signature" in argv
    argv = [a for a in argv if a != "--require-signature"]
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    adv, base, out = Path(argv[0]), argv[1], Path(argv[2])
    try:
        if out.exists() and any(out.iterdir()):
            raise SiteError(f"{out} is not empty")
        files = build(adv.read_bytes(), base, require_signature=require)
    except (SiteError, OSError) as e:
        print(f"site assembly FAILED, nothing written: {e}", file=sys.stderr)
        return 1
    out.mkdir(parents=True, exist_ok=True)
    for name, raw in files.items():
        (out / name).write_bytes(raw)
    print(f"assembled {len(files)} files into {out} ({len(files) - 3 - (SIGNATURE_FILE in files)} visa files)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
