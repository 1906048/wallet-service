#!/bin/bash

BASE_URL="${1:-http://localhost:8080}"
NUM_REQUESTS=20
FROM_WALLET="${2:-1}"
TO_WALLET="${3:-2}"
AMOUNT=500
IDEMPOTENCY_KEY="storm-test-$(date +%s%N)"

echo "Testing idempotent transfer retry storm..."
echo "Base URL: $BASE_URL"
echo "From wallet: $FROM_WALLET, To wallet: $TO_WALLET"
echo "Idempotency key: $IDEMPOTENCY_KEY"
echo "Running $NUM_REQUESTS concurrent requests..."

# Store results
results=$(mktemp)
trap "rm -f $results" EXIT

# Fire K concurrent requests with same idempotency key
seq 1 $NUM_REQUESTS | xargs -n1 -P$NUM_REQUESTS -I{} \
  curl -s -X POST "$BASE_URL/transfers" \
  -H "Authorization: Bearer test-user-alice" \
  -H "Content-Type: application/json" \
  -d "{
    \"from\": $FROM_WALLET,
    \"to\": $TO_WALLET,
    \"amount_paise\": $AMOUNT,
    \"idempotency_key\": \"$IDEMPOTENCY_KEY\"
  }" >> $results 2>&1

# Parse transfer IDs and statuses
transfer_ids=$(grep -oP '"transferId":\K[0-9]+' $results | sort -u)
unique_transfer_count=$(echo "$transfer_ids" | wc -w)
statuses=$(grep -oP '"status":"\K[^"]+' $results | sort -u)

echo ""
echo "Results:"
echo "  Total responses: $(grep -c transferId $results)"
echo "  Unique transfer IDs: $unique_transfer_count"
echo "  Transfer ID: $(echo "$transfer_ids" | tr '\n' ',')"
echo "  Unique statuses: $(echo "$statuses" | tr '\n' ',')"

if [ "$unique_transfer_count" -eq 1 ]; then
    echo "  ✅ PASS: Exactly one transfer ID for all requests"
    exit 0
else
    echo "  ❌ FAIL: Expected 1 transfer ID, got $unique_transfer_count"
    echo "  Full response:"
    cat $results
    exit 1
fi

