#!/usr/bin/env bash
# SnapLink language-agnostic smoke test. Works against ANY track (Java, Python, TypeScript).
# Usage:  scripts/smoke-test.sh [BASE_URL]        (default http://localhost:8080)
# Needs only bash + curl (Linux, macOS, WSL, Git Bash).
set -uo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
PASS=0
FAIL=0
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; [ -s "$TMP" ] && printf '       body: %s\n' "$(head -c 300 "$TMP")"; }

# request METHOD PATH [JSON_BODY] -> sets STATUS, HEADERS; body in $TMP
request() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -o "$TMP" -D - -X "$method" "$BASE_URL$path")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  HEADERS="$(curl "${args[@]}" | tr -d '\r')"
  STATUS="$(printf '%s\n' "$HEADERS" | awk 'NR==1{print $2}')"
}
header() { printf '%s\n' "$HEADERS" | awk -v h="$(echo "$1" | tr 'A-Z' 'a-z')" -F': ' 'tolower($1)==h{print $2}' | tail -1; }
json_str() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$TMP" | head -1; }
json_num() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" "$TMP" | head -1; }
json_bool() { sed -nE "s/.*\"$1\"[[:space:]]*:[[:space:]]*(true|false).*/\1/p" "$TMP" | head -1; }
expect() { if [ "$STATUS" = "$1" ]; then ok "$2 ($STATUS)"; else bad "$2 (expected $1, got ${STATUS:-no response})"; fi; }

echo "SnapLink smoke test against $BASE_URL"

request GET /health
expect 200 "GET /health"
grep -q '"database":[[:space:]]*"UP"' "$TMP" && ok "database is UP" || bad "database is UP"

TARGET="https://www.aicancode.org/projects?from=smoke"
request POST /api/v1/links "{\"url\":\"$TARGET\"}"
expect 201 "POST /api/v1/links (random code)"
CODE="$(json_str code)"
[ -n "$CODE" ] && ok "response has code=$CODE" || bad "response has a code"
[ "$(header Location)" = "/api/v1/links/$CODE" ] && ok "Location header" || bad "Location header was '$(header Location)'"

ALIAS="smoke-$(date +%s)-$RANDOM"
request POST /api/v1/links "{\"url\":\"https://www.aicancode.org\",\"alias\":\"$ALIAS\",\"expiresInDays\":7}"
expect 201 "POST /api/v1/links (custom alias)"
[ "$(json_bool custom)" = "true" ] && ok "custom=true" || bad "custom=true"
request POST /api/v1/links "{\"url\":\"https://example.com\",\"alias\":\"$ALIAS\"}"
expect 409 "duplicate alias is rejected"

request POST /api/v1/links '{"url":"ftp://not-allowed.example"}'
expect 400 "invalid URL is rejected"
case "$(header Content-Type)" in application/problem+json*) ok "errors use application/problem+json" ;; *) bad "errors use application/problem+json" ;; esac

request GET "/$CODE"
expect 302 "GET /$CODE redirects"
[ "$(header Location)" = "$TARGET" ] && ok "redirect Location is the long URL" || bad "redirect Location was '$(header Location)'"
request GET "/$CODE" >/dev/null

request GET "/api/v1/links/$CODE"
expect 200 "GET /api/v1/links/$CODE"
[ "$(json_num clickCount)" = "2" ] && ok "clickCount=2 after two redirects" || bad "clickCount=2 (got $(json_num clickCount))"

request GET "/api/v1/links/$CODE/stats?days=7"
expect 200 "GET stats"
[ "$(json_num totalClicks)" = "2" ] && ok "stats totalClicks=2" || bad "stats totalClicks=2"
[ "$(grep -o '"date"' "$TMP" | wc -l | tr -d ' ')" = "7" ] && ok "stats has 7 zero-filled days" || bad "stats has 7 days"

request GET "/api/v1/links/$CODE/insights"
expect 200 "GET insights"
PROVIDER="$(json_str provider)"
if [ "$PROVIDER" = "template" ] || [ "$PROVIDER" = "llm" ]; then ok "insights provider=$PROVIDER fallback=$(json_bool fallback)"; else bad "insights provider"; fi
if [ -n "${EXPECT_FALLBACK:-}" ]; then
  # EXPECT_FALLBACK=true: app runs with INSIGHTS_PROVIDER=llm and an unreachable LLM -> template fallback
  [ "$(json_bool fallback)" = "$EXPECT_FALLBACK" ] && ok "insights fallback=$EXPECT_FALLBACK as expected" || bad "insights fallback expected $EXPECT_FALLBACK"
fi
grep -q "\"summary\"" "$TMP" && ok "insights summary: $(json_str summary | head -c 110)" || bad "insights has a summary"

request GET /no-such-link-xyz
expect 404 "unknown code is 404"

request DELETE "/api/v1/links/$CODE"
expect 204 "DELETE link"
request GET "/$CODE"
expect 404 "deleted link no longer redirects"
request DELETE "/api/v1/links/$ALIAS" >/dev/null

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
