#!/usr/bin/env bash
# End-to-end smoke test against a running stack, through nginx, like a real user.
# Usage: deploy/smoke-test.sh [base_url]
set -euo pipefail
BASE="${1:-http://localhost}"
PASS="Demo@1234"
fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "  ok  $*"; }
jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval('d'+sys.argv[1]))" "$1"; }

echo "Smoke testing $BASE"

for i in $(seq 1 60); do curl -fs --max-time 100 "$BASE/nginx-health" >/dev/null && break; sleep 2; done
curl -fs "$BASE/nginx-health" >/dev/null || fail "nginx not healthy"
ok "nginx healthy"

curl -fs "$BASE/" | grep -q '<div id="root">' || fail "React app not served"
curl -fs "$BASE/claims/123" | grep -q '<div id="root">' || fail "SPA fallback broken"
ok "React app served with SPA fallback"

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/prometheus")
[ "$code" = "404" ] || fail "actuator exposed publicly (got $code)"
ok "actuator hidden from the internet"

login() { curl -fs -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$PASS\"}" | jget "['token']"; }
ADMIN=$(login admin@claimflow.dev) || fail "admin login"
CUST=$(login customer@claimflow.dev) || fail "customer login"
ADJ=$(login adjuster@claimflow.dev) || fail "adjuster login"
ok "demo logins work"

auth() { curl -fs -H "Authorization: Bearer $1" "${@:2}"; }

total=$(auth "$ADMIN" "$BASE/api/claims?size=1" | jget "['totalElements']")
[ "$total" -ge 11 ] || fail "expected seeded claims, got $total"
ok "$total seeded claims visible to staff"

for i in $(seq 1 45); do
  pending=$(auth "$ADMIN" "$BASE/api/claims?status=AI_REVIEW&size=1" | jget "['totalElements']")
  [ "$pending" = "0" ] && break; sleep 2
done
[ "$pending" = "0" ] || fail "$pending claims still stuck in AI_REVIEW"
ok "worker analysed every seeded claim"

high=$(auth "$ADMIN" "$BASE/api/claims?minFraud=70&size=1" | jget "['totalElements']")
[ "$high" -ge 1 ] || fail "expected the kitchen-fire claim to be high risk"
ok "fraud scoring flagged $high high-risk claim(s)"

# customer files a claim end to end
POLICY=$(auth "$CUST" "$BASE/api/policies" | jget "[0]['id']")
INCIDENT=$(date -u +%Y-%m-%d)
CLAIM=$(auth "$CUST" -X POST "$BASE/api/claims" -H 'Content-Type: application/json' \
  -d "{\"policyId\":$POLICY,\"title\":\"Smoke test claim\",\"description\":\"Created by the CI smoke test to check the full pipeline.\",\"incidentDate\":\"$INCIDENT\",\"claimedAmount\":4200}" | jget "['claim']['id']")
printf 'Clinic bill\nConsultation Rs. 1,200\nTests Rs. 3,000\nGrand Total: Rs. 4,200\n' > /tmp/smoke-bill.txt
auth "$CUST" -X POST "$BASE/api/claims/$CLAIM/documents" -F "file=@/tmp/smoke-bill.txt;type=text/plain" >/dev/null || fail "upload"
auth "$CUST" -X POST "$BASE/api/claims/$CLAIM/submit" >/dev/null || fail "submit"
for i in $(seq 1 30); do
  st=$(auth "$ADJ" "$BASE/api/claims/$CLAIM" | jget "['claim']['status']")
  [ "$st" = "UNDER_REVIEW" ] && break; sleep 1
done
[ "$st" = "UNDER_REVIEW" ] || fail "claim did not reach UNDER_REVIEW (is $st)"
bill=$(auth "$ADJ" "$BASE/api/claims/$CLAIM" | jget "['aiExtracted']['billTotal']")
ok "new claim analysed, bill total read as $bill"

auth "$ADJ" -X POST "$BASE/api/claims/$CLAIM/actions" -H 'Content-Type: application/json' -d '{"action":"ASSIGN_TO_ME"}' >/dev/null
st=$(auth "$ADJ" -X POST "$BASE/api/claims/$CLAIM/actions" -H 'Content-Type: application/json' -d '{"action":"APPROVE","approvedAmount":4200}' | jget "['claim']['status']")
[ "$st" = "APPROVED" ] || fail "approve gave $st"
ok "adjuster approved it"

# load balancing: both replicas should answer (skip with EXPECT_REPLICAS=1, e.g. the Render free tier)
if [ "${EXPECT_REPLICAS:-2}" -ge 2 ]; then
seen=""
for i in $(seq 1 12); do
  s=$(curl -fs -D - -o /dev/null -H "Authorization: Bearer $ADMIN" "$BASE/api/analytics/instance" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-served-by"{print $2}')
  seen="$seen $s"
done
echo "$seen" | grep -q api-1 || fail "api-1 never served a request"
echo "$seen" | grep -q api-2 || fail "api-2 never served a request"
ok "nginx balanced requests across api-1 and api-2"
fi

# rate limiting on login
limited=0
for i in $(seq 1 25); do
  c=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"nobody@x.dev","password":"wrongpass"}')
  [ "$c" = "429" ] && limited=$((limited+1))
done
[ "$limited" -gt 0 ] || fail "login was never rate limited"
ok "login brute force rate limited ($limited/25 got 429)"

echo "All smoke tests passed."
