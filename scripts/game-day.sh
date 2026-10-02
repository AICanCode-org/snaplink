#!/usr/bin/env bash
# Game day (Phase 7): stop Redis while redirecting and measure the impact on one track.
# Usage:  scripts/game-day.sh [python|typescript|java]      (needs the stack from docker compose)
# Env:    N_DOWN=50 (redirects during the outage)  BASE_URL=http://localhost:8080  DC="docker compose"
set -u
TRACK=${1:-python}
N_DOWN=${N_DOWN:-50}
B=${BASE_URL:-http://localhost:8080}
read -r -a DC <<< "${DC:-docker compose}"
ts() { date -u '+%H:%M:%SZ'; }

burst() { # $1 = label, $2 = number of redirects
  local total=0 code t r statuses=""
  for _ in $(seq "$2"); do
    r=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' "$B/$CODE")
    code=${r% *}; t=${r#* }
    statuses="$statuses $code"
    total=$(awk -v a="$total" -v b="$t" 'BEGIN { print a + b }')
  done
  echo "$(ts) $1: $2 redirects, statuses:$(echo "$statuses" | tr ' ' '\n' | sed '/^$/d' | sort | uniq -c | tr -s ' \n' ' '), avg $(awk -v a="$total" -v n="$2" 'BEGIN { printf "%.2f", a / n * 1000 }') ms"
}
report() {
  echo "  health:  $(curl -s "$B/health")"
  echo "  metrics: $(curl -s "$B/metrics" | grep -E '^snaplink_(redirects|cache_hits|cache_misses)_total' | tr '\n' ' ')"
}

echo "$(ts) starting app-$TRACK"
"${DC[@]}" --profile "$TRACK" up -d --wait "app-$TRACK" >/dev/null 2>&1 || { echo "could not start app-$TRACK"; exit 1; }
CODE=$(curl -s -X POST "$B/api/v1/links" -H 'Content-Type: application/json' \
  -d '{"url":"https://www.aicancode.org/game-day"}' | sed -E 's/.*"code":"([^"]+)".*/\1/')
echo "$(ts) created code=$CODE"

burst "baseline (redis up)" 50; report
echo "$(ts) docker compose stop redis"; "${DC[@]}" stop redis >/dev/null 2>&1
burst "redis DOWN" "$N_DOWN"; report
echo "  recent cache warnings:"
"${DC[@]}" logs --no-color --since 2m "app-$TRACK" 2>/dev/null | grep -iE 'cache|redis' | tail -3 | cut -c1-240
echo "$(ts) docker compose start redis"; "${DC[@]}" start redis >/dev/null 2>&1; sleep 3
burst "redis back" 50; report
curl -s -o /dev/null -X DELETE "$B/api/v1/links/$CODE"
echo "$(ts) done. Write the numbers into docs/postmortem-<date>-redis-down.md"
