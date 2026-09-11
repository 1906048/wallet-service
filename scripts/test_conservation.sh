#!/bin/bash

BASE_URL="${1:-http://localhost:8080}"
NUM_TRANSFERS=100
WALLET_A="${2:-1}"
WALLET_B="${3:-2}"
AMOUNT=10

echo "Testing conservation under contention..."
echo "Base URL: $BASE_URL"
echo "Running $NUM_TRANSFERS concurrent transfers between wallets $WALLET_A <-> $WALLET_B"
echo "Transfer amount: $AMOUNT paise each"

# Store results
results=$(mktemp)
trap "rm -f $results" EXIT

# Create mixed A->B and B->A transfers
for i in $(seq 1 $NUM_TRANSFERS); do
  from=$WALLET_A
  to=$WALLET_B

  # Alternate direction
  if [ $((i % 2)) -eq 0 ]; then
    from=$WALLET_B
    to=$WALLET_A
  fi

  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer test-user-transfer" \
    -H "Content-Type: application/json" \
    -d "{
      \"from\": $from,
      \"to\": $to,
      \"amount_paise\": $AMOUNT,
      \"idempotency_key\": \"transfer-$i-$(date +%s%N)\"
    }" >> $results 2>&1 &
done

wait

echo ""
echo "Checking final balances..."
balance_a=$(curl -s "$BASE_URL/wallets/$WALLET_A" \
  -H "Authorization: Bearer test-user-transfer" | grep -oP '"balance_paise":\K[0-9]+')

balance_b=$(curl -s "$BASE_URL/wallets/$WALLET_B" \
  -H "Authorization: Bearer test-user-transfer" | grep -oP '"balance_paise":\K[0-9]+')

successful_transfers=$(grep -c '"status":"SUCCESS"' $results)
failed_transfers=$(grep -c '"status":"DECLINED"' $results)
total_balance=$((balance_a + balance_b))

echo ""
echo "Results:"
echo "  Wallet $WALLET_A balance: $balance_a paise"
echo "  Wallet $WALLET_B balance: $balance_b paise"
echo "  Total balance: $total_balance paise"
echo "  Successful transfers: $successful_transfers"
echo "  Failed transfers: $failed_transfers"

# Check: no negative balances
if [ "$balance_a" -ge 0 ] && [ "$balance_b" -ge 0 ]; then
    echo "  ✅ PASS: No negative balances detected"
    exit 0
else
    echo "  ❌ FAIL: Negative balance detected!"
    echo "  Full response:"
    cat $results
    exit 1
fi

