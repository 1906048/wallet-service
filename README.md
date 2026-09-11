# Wallet & P2P Transfer Service

A production-correct wallet service with peer-to-peer transfers, designed for strict concurrency correctness and deployment readiness.

## Quick Start

### Local Development (Docker Compose)

```bash
docker compose up --build
```

This will start:
- PostgreSQL 16 on port 5432
- Spring Boot app on port 8080

Access:
- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus`

### Maven Build (Local PostgreSQL Required)

```bash
mvn clean install
mvn spring-boot:run
```

Requires PostgreSQL 16+ running on localhost:5432 with database `wallet`, user `wallet`, password `wallet`.

---

## API Endpoints

All endpoints require bearer token authentication:

```http
Authorization: Bearer <user-id>
```

### Wallet Management

**Create or get wallet:**
```http
POST /wallets
Authorization: Bearer user-123
```

Response:
```json
{
  "id": 101,
  "balance_paise": 0
}
```

**Get wallet balance:**
```http
GET /wallets/{id}
Authorization: Bearer user-123
```

### Transfers

**Create transfer:**
```http
POST /transfers
Authorization: Bearer user-123
Content-Type: application/json

{
  "from": 101,
  "to": 102,
  "amount_paise": 500,
  "idempotency_key": "transfer-abc-123"
}
```

Response (success):
```json
{
  "transferId": 1,
  "status": "SUCCESS",
  "from": 101,
  "to": 102,
  "amount_paise": 500
}
```

Response (insufficient funds):
```json
{
  "transferId": 2,
  "status": "DECLINED",
  "from": 101,
  "to": 102,
  "amount_paise": 500
}
```

**Get transfer status:**
```http
GET /transfers/{id}
Authorization: Bearer user-123
```

---

## Concurrency & Correctness Guarantees

### 1. Conservation
The sum of all wallet balances is strictly preserved. Even under concurrent transfers, money is never created or destroyed.

**Implementation:** 
- Both wallets are locked with `PESSIMISTIC_WRITE` before any balance mutation
- Debit and credit happen in a single transaction
- If transaction fails, neither is committed

### 2. No Overdraft
A wallet balance never becomes negative. A transfer that would overdraw fails cleanly with status=DECLINED.

**Implementation:**
- Sender balance is checked after acquiring lock
- Amount must be positive (validated)
- Database CHECK constraint prevents negative balances

### 3. Exactly-Once Transfers
Re-sending the same `idempotency_key` applies the transfer once. Repeated requests return the original result.

**Implementation:**
- `UNIQUE(idempotency_key)` constraint prevents duplicates
- Transfer record stores original request fields
- Same key + different body = 409 Conflict
- Same key + same body = returns original result

### 4. Race-Free Wallet Creation
Two concurrent `POST /wallets` for the same user yield exactly one wallet.

**Implementation:**
- `UNIQUE(user_id)` enforced by PostgreSQL
- Concurrent inserts compete; one wins, others retry-on-exception logic
- Guaranteed one wallet per user

### 5. Deadlock Prevention
Concurrent A→B and B→A transfers never deadlock.

**Implementation:**
- Always lock wallets in ascending ID order: `min(from, to)` then `max(from, to)`
- Deterministic ordering eliminates circular waits
- Impossible to deadlock

---

## Observability

### Logs

Structured JSON logs with correlation ID per request:

```json
{
  "timestamp": "2026-09-10T12:00:00.123Z",
  "level": "INFO",
  "logger": "com.example.wallet.service.TransferService",
  "message": "Transfer created: transferId=1, from=101, to=102, amount=500, key=abc-123",
  "correlationId": "req-12345-abcde",
  "thread": "tomcat-1"
}
```

Available at:
- stdout (console output)
- `/tmp/wallet-service.log` (local file)
- Platform logs (Render/Railway/Fly.io dashboards)

### Metrics (Prometheus)

Exposed at `GET /actuator/prometheus`:

- `http_requests_total` — request count by status
- `http_request_duration_seconds` — request latency (including p99)
- `wallet_transfer_created_total` — successful transfers
- `wallet_transfer_declined_insufficient_funds_total` — declined transfers
- `wallet_transfer_idempotent_replay_total` — idempotent replays

Example:
```
# HELP wallet_transfer_created_total Total transfers created successfully
# TYPE wallet_transfer_created_total counter
wallet_transfer_created_total 42
```

---

## Testing Concurrency Correctness

Three test scripts validate invariants against a live URL:

### Test 1: Concurrent Get-or-Create

```bash
bash scripts/test_concurrent_wallet_creation.sh http://localhost:8080
```

Fires 20 concurrent `POST /wallets` for same user. Expects exactly 1 wallet ID.

### Test 2: Idempotent Retry Storm

```bash
bash scripts/test_idempotent_transfer.sh http://localhost:8080 1 2
```

Fires 20 concurrent transfers with same `idempotency_key`. Expects:
- Exactly 1 transfer ID in responses
- All responses identical

### Test 3: Conservation Under Contention

```bash
bash scripts/test_conservation.sh http://localhost:8080 1 2
```

Runs 100 concurrent bidirectional transfers between wallets 1 ↔ 2. Expects:
- No negative balances
- Total balance unchanged

---

## Deployment

### Free Tier Options

All platforms support free PostgreSQL + app deployment:

#### Render.com

1. Push repo to GitHub (public)
2. Create Render PostgreSQL (free tier)
3. Create Web Service → connect GitHub repo
4. Environment variables:
   - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
5. Deploy

Logs viewable in Render dashboard.

#### Railway.app

1. Push repo to GitHub (public)
2. Connect Railway → GitHub repo
3. Add PostgreSQL service (auto-provisioned)
4. Environment variables auto-injected
5. Deploy

Logs viewable in Railway UI.

#### Fly.io

```bash
fly launch --builder docker
fly deploy
fly logs
```

### Cost

✅ **Total: ₹0/month**

- Render: 1 free app tier (550 hr/month), 1 free Postgres (256MB)
- Railway: $5 free credits/month
- Fly.io: Free tier VM + shared PostgreSQL
- GitHub public repo: free

---

## Architecture

```
Clients (Bearer Token Auth)
        ↓
Spring Boot REST API
  ├─ WalletController
  ├─ TransferController
        ↓
Business Logic (Transactional)
  ├─ WalletService
  ├─ TransferService (pessimistic locking)
        ↓
JPA Repositories
        ↓
PostgreSQL 16 (row-level locks, ACID)
  ├─ wallet table (UNIQUE user_id, CHECK balance >= 0)
  ├─ transfer table (UNIQUE idempotency_key)
```

### Why This Design?

- **Simplest-correct concurrency:** Explicit row locking beats optimistic retries or eventual consistency
- **No SERIALIZABLE overhead:** Sorted-order locking prevents deadlocks without transaction aborts
- **No distributed infrastructure:** Redis/Kafka unnecessary for immediate transactional operations
- **Observable from day one:** JSON logs + Prometheus metrics built-in

---

## Configuration

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | wallet | Database name |
| `DB_USER` | wallet | Database user |
| `DB_PASSWORD` | wallet | Database password |
| `ENVIRONMENT` | local | Deployment environment (for logging) |

Example:
```bash
export DB_HOST=db.render.com
export DB_PORT=5432
export DB_NAME=wallet_prod
export DB_USER=postgres
export DB_PASSWORD=secret
java -jar target/wallet-service-1.0.0.jar
```

---

## Design Decisions

See `REASONING.md` for detailed reasoning on:

- Concurrency mechanism (why pessimistic locking + sorted ordering)
- Rejected alternatives (Redis, Kafka, event sourcing, SERIALIZABLE)
- Idempotency implementation (same transaction guarantee)
- Consistency vs. availability trade-off
- Authentication design (simple bearer token, not JWT)
- AI directed-vs-decided disclosure

---

## Next Steps for Deployment

1. **Build:** `docker compose build`
2. **Test locally:** `docker compose up`, then run burst scripts
3. **Push to GitHub:** Make repo public
4. **Deploy to free tier:** (Render/Railway/Fly.io)
5. **Verify:**
   - `curl -s https://YOUR-URL/actuator/health | jq`
   - Run burst scripts against live URL
   - Check logs in platform dashboard
6. **Submit:**
   - Live URL
   - GitHub repo link
   - Logs screenshot
   - Reasoning write-up (REASONING.md)

---

## License

MIT

