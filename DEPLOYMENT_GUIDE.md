# Deployment Guide: Wallet Service on Free Tier

This guide walks through deploying the wallet service to free tier platforms.

## Prerequisites

1. **GitHub Account** (free) — for public repo
2. **Render Account** (free) — recommended for simplicity
3. **Git** installed locally

## Step 1: Prepare Local Repository

```bash
cd /Users/administrator/Downloads/P2PTransfer

# Initialize git if not done
git init
git add .
git commit -m "Initial wallet service commit"
```

## Step 2: Push to GitHub (Public)

Create a public GitHub repository, then:

```bash
git remote add origin https://github.com/YOUR-USERNAME/wallet-service.git
git branch -M main
git push -u origin main
```

Ensure the repo is **public** (anyone can view).

---

## Step 3: Deploy to Render.com (Recommended)

### 3.1 Create PostgreSQL Database

1. Go to https://render.com/dashboard
2. Click **+ New** → **PostgreSQL Database**
3. Fill in:
   - **Name:** `wallet-db`
   - **Database:** `wallet`
   - **User:** `wallet`
   - **Region:** Choose closest to you
4. Click **Create Database**
5. **Copy the connection internal URL** (you'll need it)

### 3.2 Deploy Web Service

1. Click **+ New** → **Web Service**
2. **Connect GitHub** → select your `wallet-service` repo
3. Fill in:
   - **Name:** `wallet-service`
   - **Environment:** `Docker`
   - **Root Directory:** `/` (default)
   - **Build Command:** `docker compose build` (or leave blank if using Dockerfile)
   - **Start Command:** `java -jar /app/app.jar`
4. Add **Environment Variables:**
   ```
   DB_HOST=<postgres-internal-url-host>
   DB_PORT=<postgres-internal-url-port>
   DB_NAME=wallet
   DB_USER=wallet
   DB_PASSWORD=<postgres-password>
   ENVIRONMENT=render
   ```
5. Click **Create Web Service**

Render will auto-build and deploy. Logs stream in the dashboard.

### 3.3 Verify Deployment

```bash
# Replace with your Render URL
BASE_URL="https://wallet-service-xxx.onrender.com"

curl -s "$BASE_URL/actuator/health"
```

Should return:
```json
{"status":"UP"}
```

---

## Step 4: Deploy to Railway.app (Alternative)

1. Go to https://railway.app
2. Click **New Project** → **Deploy from GitHub**
3. Select your `wallet-service` repo
4. Railway auto-detects Java/PostgreSQL
5. Add PostgreSQL plugin (if not auto-added):
   - Click **+ Add** → **PostgreSQL**
6. Set environment variables from PostgreSQL plugin:
   - Railway auto-injects `DATABASE_URL`, parse it for `DB_HOST`, `DB_PORT`, etc.
7. Deploy

Logs viewable in Railway dashboard.

---

## Step 5: Deploy to Fly.io (Alternative)

```bash
# Install flyctl: https://fly.io/docs/getting-started/installing-flyctl/
# Login
fly auth login

# From project root
fly launch --builder docker --name wallet-service

# Follow prompts (choose free tier, create PostgreSQL)
fly deploy

# View logs
fly logs
```

---

## Step 6: Test Live Deployment

```bash
LIVE_URL="https://wallet-service-xxx.onrender.com"  # or Railway/Fly URL

# Test 1: Concurrent wallet creation
bash scripts/test_concurrent_wallet_creation.sh "$LIVE_URL"

# Test 2: Idempotent transfer storm
bash scripts/test_idempotent_transfer.sh "$LIVE_URL" 1 2

# Test 3: Conservation under contention
bash scripts/test_conservation.sh "$LIVE_URL" 1 2
```

All should pass with ✅ PASS.

---

## Step 7: View Logs

### Render
- Dashboard → Web Service → **Logs** tab
- Logs stream live as JSON

### Railway
- Dashboard → Project → **Deployments** → **Logs**

### Fly.io
```bash
fly logs
```

Example log entry:
```json
{
  "timestamp": "2026-09-10T12:00:00.123Z",
  "level": "INFO",
  "service": "wallet-api",
  "correlationId": "req-abc-123",
  "message": "Transfer created: transferId=1, from=101, to=102, amount=500",
  "thread": "tomcat-1"
}
```

---

## Step 8: View Metrics

```bash
LIVE_URL="https://wallet-service-xxx.onrender.com"

curl -s "$LIVE_URL/actuator/prometheus" | grep wallet_transfer
```

Output:
```
# HELP wallet_transfer_created_total Total transfers created successfully
# TYPE wallet_transfer_created_total counter
wallet_transfer_created_total 42.0
wallet_transfer_declined_insufficient_funds_total 5.0
wallet_transfer_idempotent_replay_total 8.0
```

---

## Step 9: Troubleshooting

### App won't start ("migration error")

- Ensure PostgreSQL is ready before app starts.
- Check env vars are correct (copy-paste from platform).
- Render/Railway show detailed build logs; check for SQL syntax errors.

### Timeouts during tests

- Free tier may be slow (shared resources).
- Increase curl timeout: add `-m 30` to curl commands.
- Check if app is healthy: `curl $LIVE_URL/actuator/health`

### High latency or crashes

- Free tier has limited resources. Concurrency tests will be slower.
- Don't run all 3 tests simultaneously; run one at a time.

---

## Step 10: Submit Deliverables

Prepare submission:

1. **Live URL** (e.g., `https://wallet-service-xxx.onrender.com`)
2. **Public GitHub repo** link
3. **Logs screenshot** (curl `/actuator/health` and `/actuator/prometheus`)
4. **Burst script results** (output of all 3 tests passing)
5. **REASONING.md** (already created)

---

## Cost Verification

Confirm you're on free tier:

### Render
- **PostgreSQL:** 256 MB free → Plan: "Free"
- **Web Service:** 550 free hours/month → Plan: "Free"

### Railway
- Monitor: https://railway.app/account/billing
- Should show "$0 / month" or under $2 with free credits

### Fly.io
- Dashboard → Billing
- Shared-cpu-1x VM = free tier
- PostgreSQL = shared free tier

---

## Local Testing Before Deploy

Test locally first:

```bash
# In project root
docker compose up --build

# In another terminal
bash scripts/test_concurrent_wallet_creation.sh http://localhost:8080
bash scripts/test_idempotent_transfer.sh http://localhost:8080 1 2
bash scripts/test_conservation.sh http://localhost:8080 1 2

# All should pass locally, proves deployment will work
```

---

## Next: Continuous Integration (Optional)

Add GitHub Actions to auto-test on push:

Create `.github/workflows/test.yml`:

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: wallet
          POSTGRES_USER: wallet
          POSTGRES_PASSWORD: wallet
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: mvn clean test -Dspring.datasource.url=jdbc:postgresql://localhost:5432/wallet
```

Commit and push. GitHub will auto-test on each commit.

