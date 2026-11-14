#!/bin/sh
# Exercises the running compose demo (docker compose up --build -d) end to end:
# issue a certificate, validate all nine steps against the mock trust list, download the shared
# link, and confirm a tampered code is rejected. Exits non-zero on the first failed expectation.
set -eu
BASE="${BASE:-http://localhost:8082}"

fail() { echo "FAIL: $*" >&2; exit 1; }

printf 'waiting for %s ' "$BASE"
for _ in $(seq 1 60); do
  if curl -s -o /dev/null "$BASE/"; then echo "up"; break; fi
  printf '.'; sleep 2
done
curl -s -o /dev/null "$BASE/" || fail "app did not start"

HC1=$(curl -sf -X POST "$BASE/v2/vshcIssuance" -H 'Content-Type: application/json' \
  -d '{"jsonContent":"{\"resourceType\":\"Bundle\",\"id\":\"demo\"}"}') || fail "issuance request failed"
case "$HC1" in HC1:*) echo "issued a certificate (${#HC1} chars)";; *) fail "unexpected issuance output: $HC1";; esac

RESULT=$(curl -sf -X POST "$BASE/v2/vshcValidation" -H 'Content-Type: application/json' \
  -d "{\"qrCodeContent\":\"$HC1\"}") || fail "validation request failed"
OK=$(printf '%s' "$RESULT" | grep -o '"status":"SUCCESS"' | wc -l)
[ "$OK" -eq 9 ] || fail "expected 9 successful validation steps, got $OK: $RESULT"
echo "validation: 9/9 steps succeeded"

URL=$(printf '%s' "$RESULT" | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')
[ -n "$URL" ] || fail "no shared link in the validation result"
BODY=$(curl -sf "$URL") || fail "could not download $URL"
printf '%s' "$BODY" | grep -q '"id":"demo"' || fail "downloaded content is not the issued bundle: $BODY"
echo "shared link downloaded: $BODY"

BAD=$(curl -s -X POST "$BASE/v2/vshcValidation" -H 'Content-Type: application/json' \
  -d "{\"qrCodeContent\":\"$(printf '%s' "$HC1" | sed 's/.\{2\}$/XX/')\"}")
printf '%s' "$BAD" | grep -q '"status":"FAILED"' || fail "a tampered certificate was not rejected: $BAD"
echo "tampered certificate rejected"
echo "OK"
