#!/usr/bin/env bash
# The build's warnings and the withheld German entries, put where a person looks (job summary and an
# annotation). Shared by both jobs of advisory-refresh.yml so the filter lives once. Never fails.
#   show_build_notes.sh BUILD_LOG QUARANTINE_JSON
LOG="$1"
F="$2"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
HERE="$(cd "$(dirname "$0")" && pwd)"

# The withheld entries are reported below; echoed page text loses its backticks.
W="$(grep '^warning:' "$LOG" 2>/dev/null | grep -vE '^warning: (DE .* withheld|German entries withheld)' | tr -d '`' | cut -c1-400 || true)"
if [ -n "$W" ]; then
  { echo "### Advisory build warnings"; echo '```'; echo "$W"; echo '```'; } >> "$SUMMARY"
fi

[ -f "$F" ] || exit 0
LINES="$(python3 -I "$HERE/data_pr.py" withheld "$F")"
if [ -n "$LINES" ]; then
  NOTICE="$(python3 -I "$HERE/data_pr.py" withheld-notice "$F")"
  { echo "### German advisory entries withheld this week"; [ -n "$NOTICE" ] && { echo "$NOTICE" | sed 's/$/  /'; }; echo '```'; echo "$LINES"; echo '```'; } >> "$SUMMARY"
  CODES="$(python3 -I "$HERE/data_pr.py" withheld-codes "$F")"
  if [ -n "$CODES" ]; then
    echo "::warning::German advisory entries withheld this week: $CODES"
  fi
fi
exit 0
