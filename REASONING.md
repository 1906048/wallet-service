# Wallet & P2P Transfer Service — One-Page Design Reasoning

I designed this wallet service as a **single Spring Boot app backed by PostgreSQL** because the exercise rewards correctness, observability, and deployment readiness more than auth complexity or distributed architecture. I chose the architecture and data model first, then directed AI to implement the code and scripts from my decisions. I also rejected a few AI suggestions that would have made the solution heavier without improving the rubric score.

## Data Model

- `wallet(id, user_id UNIQUE, balance_paise, created_at)`
  - `balance_paise` is always integer paise, never decimal.
  - `UNIQUE(user_id)` guarantees one wallet per user.
  - `CHECK(balance_paise >= 0)` is a DB safety net.
- `transfer(id, from_wallet_id, to_wallet_id, amount_paise, idempotency_key UNIQUE, status, created_at)`
  - Stores every transfer attempt and its final status.
  - `UNIQUE(idempotency_key)` gives exactly-once behavior.

## Simplest-correct concurrency mechanism

I chose **PostgreSQL row locking with `SELECT ... FOR UPDATE`** and **deterministic lock ordering** (`min(from,to)` first, then `max(from,to)`). That is the simplest correct solution because it keeps conservation, no-overdraft, and deadlock prevention inside one ACID transaction.

Why I chose it:
- both wallets are locked before any mutation;
- the sender balance is checked while locked, so no race can overdraw it;
- fixed lock order prevents circular waits when two transfers touch the same pair in opposite directions.

I rejected a few AI suggestions here:
- **SERIALIZABLE isolation**: correct, but heavier than needed and can cause retries under contention.
- **Redis locks**: extra infrastructure and an extra failure mode with no benefit over DB locks.
- **Kafka/event sourcing**: useful for asynchronous systems, but unnecessary for an immediate money transfer API where the client needs a synchronous answer.

## Where idempotency lives

Idempotency is enforced in the **transfer table** with the `UNIQUE(idempotency_key)` constraint. My design keeps the idempotency record in the **same transaction** as the debit/credit update. So:

- same key + same body → return the original transfer result;
- same key + different body → `409 Conflict`;
- duplicate concurrent requests → one wins, the other re-reads and returns the same result.

## Consistency vs availability

For a wallet, I chose **consistency over availability**. I prefer a transfer to fail cleanly rather than succeed with uncertain state. That means I accept DB dependency and transactional latency, and I consciously give up fire-and-forget writes or eventual consistency. For money, that trade-off is correct.

## Authentication decision

The exercise asked for a **simple bearer token per user**, so I kept auth intentionally simple. The bearer token is treated as the caller identity for this prototype. I did **not** introduce JWT or a separate auth server because that would add security and operational complexity without improving the core evaluation criteria. I directed AI to wire the filter/controller code, but the decision to keep auth lightweight was mine.

## AI directed-vs-decided disclosure

- **I decided:** architecture, DB schema, lock ordering, idempotency strategy, and consistency-over-availability.
- **AI implemented:** Spring annotations, controller/service boilerplate, Docker/compose wiring, scripts, and logging/metrics code.
- **I rejected AI suggestions** that would have added Redis, Kafka, or JWT-based auth because they were not needed for this exercise.

## Free-tier cost

The entire solution is intended to run at **₹0/month** on free tiers:
- Render for app hosting;
- managed PostgreSQL free tier;
- GitHub public repo;
- Docker and local compose are free.

This is the design I would defend in review: simple, correct, observable, and practical for the exercise.

