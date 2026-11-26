#!/usr/bin/env bash
# Waits until this run's "github-pages" artifact is listed, then returns. deploy-pages lists the
# run's artifacts about a second after upload-pages-artifact finishes, and GitHub's listing
# sometimes has not registered the new one yet ("Found 0 artifact(s)"). Shared by
# advisory-refresh.yml and visa-refresh.yml. Needs GH_TOKEN, GITHUB_REPOSITORY, GITHUB_RUN_ID and
# `actions: read`. An API error counts as "not listed yet" and is retried. On timeout it warns and
# returns 0 so deploy-pages gets its own try and fails loudly. After a failed deploy, start a new
# run: "re-run failed jobs" uploads a second artifact and deploy-pages refuses duplicates.
set -u
timeout=120
deadline=$((SECONDS + timeout))
while :; do
  if n=$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID/artifacts?name=github-pages&per_page=100" \
        --jq '[.artifacts[] | select(.expired | not)] | length' 2>&1); then
    if [ "$n" -ge 1 ] 2>/dev/null; then echo "github-pages is listed ($n)."; exit 0; fi
  else
    echo "artifact listing failed, retrying: $n"
  fi
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "::warning::github-pages artifact still not listed after ${timeout}s; deploying anyway."
    exit 0
  fi
  sleep 5
done
