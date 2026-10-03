#!/usr/bin/env bash
# End-to-end check: CompletableFuture workflow, DeferredResult long-poll, webhook, failure modes, cache, bulk.
set -uo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
PASS=0; FAIL=0
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
check() { if [[ "$3" == "$2" ]]; then green "PASS  $1 ($3)"; PASS=$((PASS+1)); else red "FAIL  $1 (got $3, want $2)"; FAIL=$((FAIL+1)); fi; }
json()  { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"; }
order() { curl -s -X POST "$API/orders" -H 'Content-Type: application/json' \
          -d "{\"customerName\":\"Smoke\",\"email\":\"$1-$RANDOM@example.com\",\"planCode\":\"${2:-UNL-599}\",\"circle\":\"KA\"}" "${@:3}"; }

echo "Waiting for $BASE_URL/actuator/health ..."
for _ in $(seq 1 60); do curl -fs "$BASE_URL/actuator/health" >/dev/null 2>&1 && break; sleep 2; done

echo "== CompletableFuture workflow =="
ID=$(order anita | json "['id']")
check "order created (PROVISIONING)" PROVISIONING "$(curl -s "$API/orders/$ID" | json "['status']")"
check "long-poll returns ACTIVE" ACTIVE "$(curl -s "$API/orders/$ID/await?timeoutSeconds=15" | json "['status']")"
check "risky -> REJECTED" REJECTED "$(order risky | json "['status']")"
check "bureau down -> 503" 503 "$(order down UNL-599 -o /dev/null -w '%{http_code}')"
check "unknown plan -> 422" 422 "$(order anita XYZ-111 -o /dev/null -w '%{http_code}')"

echo "== DeferredResult long-poll + webhook =="
MID=$(order manual | json "['id']")
check "wait expires -> 202" 202 "$(curl -s -o /dev/null -w '%{http_code}' "$API/orders/$MID/await?timeoutSeconds=1")"
check "webhook applied" True "$(curl -s -X POST "$API/provisioning/callbacks" -H 'Content-Type: application/json' \
      -d "{\"orderId\":\"$MID\",\"success\":true,\"iccid\":\"8991000000000000001\"}" | json "['applied']")"
check "duplicate webhook ignored" False "$(curl -s -X POST "$API/provisioning/callbacks" -H 'Content-Type: application/json' \
      -d "{\"orderId\":\"$MID\",\"success\":false}" | json "['applied']")"
check "await after webhook -> ACTIVE" ACTIVE "$(curl -s "$API/orders/$MID/await?timeoutSeconds=1" | json "['status']")"

echo "== Cache and bulk =="
t1=$(curl -s -o /dev/null -w '%{time_total}' "$API/plans/FAM-999")
t2=$(curl -s -o /dev/null -w '%{time_total}' "$API/plans/FAM-999")
echo "  plan lookup: first ${t1}s, second ${t2}s (cached)"
check "bulk portability" 4 "$(curl -s -X POST "$API/portability/bulk-check" -H 'Content-Type: application/json' \
      -d '["9876543210","9876543211","9000000001","9123456780"]' | json "['results'].__len__()")"

echo; echo "Passed: $PASS  Failed: $FAIL"
[[ $FAIL -eq 0 ]]
