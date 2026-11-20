#!/usr/bin/env bash
# Post-deploy smoke test: the site, the SPA, the API and the auth rules respond as expected.
#   ./infra/aws/smoke-test.sh https://socialhub.example.com
set -uo pipefail
BASE="${1:?usage: smoke-test.sh <base-url>}"
fail=0

check() { # name, expected status, url, [curl args...]
  local name=$1 expected=$2 url=$3; shift 3
  local got
  for attempt in 1 2 3 4 5 6; do
    got=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10 "$@" "$url")
    [ "$got" = "$expected" ] && break
    sleep 10
  done
  if [ "$got" = "$expected" ]; then echo "PASS $name ($got)"; else echo "FAIL $name (expected $expected, got $got)"; fail=1; fi
}

check "nginx health"            200 "$BASE/healthz"
check "landing page"            200 "$BASE/"
check "React app"               200 "$BASE/app/"
check "SPA deep link fallback"  200 "$BASE/app/watch/1"
check "public video catalogue"  200 "$BASE/api/videos"
check "search API"              200 "$BASE/api/search/hashtags"
check "protected API needs JWT" 401 "$BASE/api/feed/home"
check "internal API is blocked" 404 "$BASE/internal/media/1"
check "login validates input"   400 "$BASE/api/auth/login" -X POST -H 'Content-Type: application/json' -d '{}'

exit $fail
