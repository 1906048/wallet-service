#!/bin/bash

BASE_URL="${1:-http://localhost:8080}"
NUM_REQUESTS=20

echo "Testing concurrent wallet creation for brand-new user..."
echo "Base URL: $BASE_URL"

# Store results
results=$(mktemp)
trap "rm -f $results" EXIT

# Fire N concurrent requests with unique user tokens
USER_ID="concurrent-test-$(date +%s%N)"
seq 1 $NUM_REQUESTS | xargs -n1 -P$NUM_REQUESTS -I{} \
  curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $USER_ID" \
  -H "Content-Type: application/json" >> $results 2>&1

# Parse wallet IDs
wallet_ids=$(grep -oP '"id":\K[0-9]+' $results | sort -u)
unique_wallet_count=$(echo "$wallet_ids" | wc -w)

echo ""
echo "Results:"
echo "  Total responses: $(grep -c id $results)"
echo "  Unique wallet IDs: $unique_wallet_count"
echo "  Wallet ID: $(echo "$wallet_ids" | tr '\n' ',')"

if [ "$unique_wallet_count" -eq 1 ]; then
    echo "  ✅ PASS: Exactly one wallet created"
    exit 0
else
    echo "  ❌ FAIL: Expected 1 wallet, got $unique_wallet_count"
    echo "  Full response:"
    cat $results
    exit 1
fi

