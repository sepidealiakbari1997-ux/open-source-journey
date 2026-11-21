# Order fulfillment experiment

**Status: Inventory stock slice implemented.** PostgreSQL, migrations, and transactional stock deduction are working, with three integration tests reported passing by the developer. The rest of this document describes the intended workflow; Order, Fulfillment, reservation decisions, and messaging are not implemented. The neighboring `otel-demo` remains the tracing baseline.

## Goal and learning approach

Practice data consistency, retries, idempotency, and tracing across three independent Spring Boot services. Each service owns its database; interactions use REST or RabbitMQ, never another service's tables.

Build one small slice at a time. Introduce failures deliberately and reason about recovery before adding mechanisms. The decisions below came from that discussion. Items explicitly marked open are not settled implementation choices.

## Business flow

```text
Customer → POST /orders → Order database: RECEIVED
                              │
                       background worker
                              │ REST reservation request
                              ▼
                      Inventory database
                      ├── REJECTED → REST result → Order: REJECTED
                      └── RESERVED + notification PENDING
                              │ publication worker
                              ▼
                           RabbitMQ
                              │ InventoryReserved
                              ▼
                      Fulfillment database
                      shipment: PENDING_DISPATCH
                      notification: PENDING
                              │ publication worker
                              ▼
                           RabbitMQ
                              │ ShipmentCreated
                              ▼
                        Order: PREPARING
                “Your order is being prepared”
```

The immediate customer response means **“Order received”**, not “stock reserved.” Fulfillment can be unavailable without preventing a reservation. Creating a shipment means preparation has begun, not that goods have been dispatched. No carrier integration or actual dispatch is in scope.

## Database ownership

| Service | Durable records | Responsibility |
|---|---|---|
| Order | Orders, items, reservation work/retry state | Acknowledge receipt after commit, request reservations, expose customer-facing status |
| Inventory | Stock and reservation decisions, including rejected decisions; publication state for successful reservations | Decide once per order ID, prevent overselling, eventually notify Fulfillment |
| Fulfillment | Shipments and publication state | Create one shipment per order, eventually notify Order |

Every service has its own database. Inventory now uses PostgreSQL 17; one local database server with separate databases and users is an option, not yet a confirmed deployment choice. Inventory uses Flyway migrations and Hibernate schema validation. Database setup for the other services remains to be implemented.

Business status and publication status are separate concepts. For example, a reservation can remain `RESERVED` while its notification changes from `PENDING` to `PUBLISHED`.

## Order → Inventory: REST with recoverable background work

### Accept the order

`POST /orders` saves the order and its items with status `RECEIVED` in a database transaction. Commit before responding to the customer. The saved record lets background work resume after a restart.

Proposed request:

```json
{
  "customerId": "customer-123",
  "items": [
    { "sku": "BOOK-JAVA", "quantity": 2 }
  ]
}
```

A proposed HTTP mapping is `202 Accepted`, a `Location` header for `/orders/{orderId}`, and:

```json
{
  "orderId": "<order-uuid>",
  "status": "RECEIVED"
}
```

`GET /orders/{orderId}` exposes the current locally stored outcome, or `404`. Validation rules, error bodies, and client retries of the original POST still need implementation decisions. In particular, worker idempotency by order ID does not prevent a repeated customer POST from creating a different order ID.

### Request a reservation

An Order background worker finds pending reservation work and calls Inventory over HTTP, using the same order ID and items on every retry. REST was chosen because the worker gets a direct reservation result without a separate reservation-result queue. The customer does not wait for this call.

Proposed `POST /reservations` body:

```json
{
  "orderId": "<order-uuid>",
  "items": [
    { "sku": "BOOK-JAVA", "quantity": 2 }
  ]
}
```

Inventory returns a committed `RESERVED` or `REJECTED` decision. A successful result includes a stable reservation ID; a rejection includes its reason, such as insufficient stock. Exact HTTP status codes and duplicate-response mapping remain open.

### Commit the decision atomically

For a new order ID, Inventory checks stock under database concurrency control and saves its decision. For acceptance, stock deduction, reservation insertion, and `notificationStatus=PENDING` belong to the **same transaction**. For insufficient stock, persist a rejected decision without deducting stock or scheduling a fulfillment notification. Commit before returning the business result.

Use a unique constraint on reservation `order_id`. Repeating the same order ID with matching items returns the saved decision without another stock deduction. Reusing it with different items must produce a conflict.

Stock concurrency and duplicate requests are distinct problems:

- A unique order ID prevents two committed reservation decisions for the same order.
- Stock protection prevents different orders from overselling the same product.
- Pessimistic locking holds the database stock lock through the check, update, and transaction completion.
- Optimistic locking detects conflicting updates; retries must use a fresh transaction and reread stock. The initial stock slice uses pessimistic locking; optimistic locking remains a possible comparison exercise.

If a duplicate insert or optimistic update fails, roll back the complete transaction, including any stock deduction. Resolve a concurrent duplicate by reading the committed decision in a fresh transaction; do not continue using a failed transaction.

**A committed rejection is final for that order ID**, even after replenishment. A crash before the decision commits leaves no durable decision, so a later attempt may reevaluate changed stock. The commit is the authoritative boundary.

### Save the result in Order

Order records `INVENTORY_RESERVED` or `REJECTED`. Stop reservation retries for a committed business rejection. A timeout, lost response, or failed Order database update leaves work eligible for retry with the same order ID.

This handles both uncertain cases:

1. Inventory commits, but the response never reaches Order.
2. Order receives success, but fails to commit its own update.

Inventory's durable idempotency makes the retry safe. Technical failures must not be persisted as final business rejections. Retry timing, backoff, worker coordination, and recovery of long-running pending work remain to be implemented.

## Inventory → Fulfillment: RabbitMQ

### Remember the obligation to publish

A successful reservation is saved with `notificationStatus=PENDING` in the stock transaction. A worker publishes `InventoryReserved`, then marks it `PUBLISHED` after broker confirmation and successful routing checks.

The database transaction does not include RabbitMQ. If publishing fails or the outcome is unknown, keep the notification pending. A crash after publication but before saving `PUBLISHED` can cause duplicate delivery; that is expected.

Use a stable notification/event ID and saved data sufficient to reconstruct the original notification across retries. The exact columns and event schema remain to be implemented. An example payload is:

```json
{
  "eventId": "<stable-event-uuid>",
  "eventType": "InventoryReserved",
  "orderId": "<order-uuid>",
  "reservationId": "<reservation-uuid>",
  "items": [
    { "sku": "BOOK-JAVA", "quantity": 2 }
  ]
}
```

`PUBLISHED` means handed to the messaging infrastructure, not processed by Fulfillment. Broker confirmation alone is insufficient: an exchange can accept a message with no matching queue. Declare durable topology, use persistent messages, publisher confirms, and mandatory publishing with returned-message handling. Do not mark an unroutable notification published. Queue type and recovery configuration still require selection.

Who declares the topology while consumers are unavailable remains an explicit implementation decision. Exchange, binding, and queue names are not yet fixed contracts.

### Consume and create a shipment

Fulfillment creates a shipment in a database transaction with:

- `shipmentId`
- `orderId`, protected by a unique constraint
- `reservationId`
- `status=PENDING_DISPATCH`
- `createdAt`
- Publication state for the `ShipmentCreated` notification

Commit the shipment and pending notification together, then acknowledge the incoming message. A proposed lookup endpoint is `GET /shipments?orderId=<order-id>`.

For a redelivered reservation, an existing matching shipment means the work is already complete: acknowledge without creating another shipment or another logical notification. A conflicting reservation for that order is not a harmless duplicate and must not silently be accepted. A failed insert requires rollback before checking the existing shipment.

If Fulfillment commits and crashes before acknowledging, redelivery is safe because the unique order ID is durable across restarts.

### Database outages versus individual bad messages

Do not acknowledge a delivery whose required database work has not committed.

A database outage affects the whole consumer. Pause consumption and resume after recovery rather than cycling every queued message through immediate retries. Already delivered messages must remain unacknowledged or be safely requeued; exact listener/container recovery and prefetch behavior remain to be configured.

A malformed or persistently failing individual message is a different case. Bounded delayed retries and dead-letter handling may be appropriate. The retry schedule, limits, and dead-letter topology are not chosen yet. If implementing retry republishing, do not acknowledge the original until the handoff is safe.

## Fulfillment → Order: ShipmentCreated

Fulfillment uses the same publication-status approach: a worker publishes the saved `ShipmentCreated` notification and marks it published after confirmation and routing checks. The pending obligation is committed with shipment creation, so a crash between database commit and publication does not erase the work.

The event identifies the order, reservation, shipment, and stable event ID. Order consumes it and saves `PREPARING`, shown to the customer as **“Your order is being prepared.”** Acknowledge after the database transaction commits. Redelivery must be harmless.

### Prevent backward status updates

Events and REST results can arrive in a different order. Fulfillment might complete before Order's reservation worker saves its HTTP result.

The successful business progression is:

```text
RECEIVED → INVENTORY_RESERVED → PREPARING
```

Allow `ShipmentCreated` to move either `RECEIVED` or `INVENTORY_RESERVED` to `PREPARING`; shipment creation implies a successful reservation. A duplicate event leaves `PREPARING` unchanged.

The reservation worker must update conditionally, for example:

```sql
UPDATE orders
SET status = 'INVENTORY_RESERVED'
WHERE order_id = :orderId
  AND status = 'RECEIVED';
```

Check the affected row count and current state. Do not perform an unconditional read-then-save that could overwrite a concurrent transition. Apply allowed transitions atomically; `REJECTED` is a final business outcome, and contradictions such as shipment creation for a rejected order require investigation.

A late reservation result must not move `PREPARING` backward. Completion/retry tracking for the worker must also recognize that an order already advanced to `PREPARING` no longer needs another reservation attempt.

## Why publication status is enough for this scope

Each successful reservation has one fulfillment notification, and each shipment has one creation notification. Persisting publication state with the business change can represent those obligations without a separate outbox table, provided the event identity and original data survive retries.

A separate transactional outbox becomes useful when one record generates several independent events, historical payloads must survive later updates, or a deletion must still be announced. For example, one `PENDING` flag on a shipment cannot represent both an unpublished `ShipmentCreated` and an unpublished `ShipmentDispatched` after its current state changes.

The key guarantee is atomic persistence of the business change and the obligation to publish. A separate table is not required for our current one-notification-per-record model. Neither design eliminates duplicate publication or automatically enforces event order. Durable consumer idempotency and allowed state transitions remain necessary.

## Independent builds and Docker orchestration

Planned layout; create files only as the implementation needs them:

```text
order-fulfillment/
├── README.md
├── compose.yaml
├── docker/
│   └── Dockerfile
├── order-service/         # independent Maven project
├── inventory-service/     # independent Maven project
└── fulfillment-service/   # independent Maven project
```

Keep orchestration and shared Docker build instructions at this experiment root. No parent Maven POM or shared DTO module is currently planned. Each service owns its build and application configuration.

Compose will coordinate the three applications, their databases, RabbitMQ, and Jaeger. The database deployment layout, image versions, credentials for local use, ports, health checks, and volumes remain to be selected. Use service DNS names between containers, not host `localhost` addresses. Account for host-port conflicts with the baseline Jaeger.

The intended eventual entry point is `docker compose up --build`; **the complete stack does not exist yet; the current Compose file starts only Inventory’s database**. The implemented runbook must document readiness, sample requests, status lookup, logs, failure injection, shutdown, and explicit reset behavior for database and broker state.

## Tracing goals

Use the Java agent with distinct service names and Jaeger as the backend. Select and verify compatible tool, library, and image versions during implementation.

Observe rather than assume:

- HTTP propagation from Order's worker to Inventory.
- Message context injection and extraction for both event paths.
- Producer/consumer span names, parents, and links emitted by the selected instrumentation.
- Retry attempts and processing after the original HTTP request has ended.

A database-backed worker does not automatically inherit the original request's current context. How to preserve or link causal trace context across saved work is an open observability decision. Business order/event IDs are separate from trace/span IDs.

## Practical implementation and failure exercises

1. Build Inventory's database-backed reservation slice: accept, reject, replay decisions, and prevent duplicate deductions under concurrent requests.
2. Add Order's durable receipt and background REST attempts. Lose responses and fail local result updates to verify recovery.
3. Add publication status and RabbitMQ. Stop the broker and interrupt publication before/after confirmation.
4. Add Fulfillment with durable shipment uniqueness. Crash after commit but before acknowledgement; pause during a database outage.
5. Add `ShipmentCreated` and Order's conditional transitions. Deliver duplicates and delay the reservation result to demonstrate no backward transition.
6. Finish the Compose runbook and document observed traces and recovery results.

The initial stock deduction portion of stage 1 is implemented and tested as recorded below; durable reservation decisions and stages 2–6 remain ahead. Work step by step with the developer reasoning about failures before implementing the next mechanism.

## 2026-11-20 — Implemented stock slice and verification

The current code contains:

- `inventory-service/src/main/java/com/pipita/inventory/stock/Stock.java`: SKU primary key and available quantity.
- `StockRepository.findBySkuForUpdate()`: a pessimistic write-lock query, used within the service transaction.
- `StockService.reserve()`: reject nonpositive quantities, missing stock, or insufficient stock; otherwise deduct. This is not yet an order-aware reservation or an idempotent operation.
- `V1__create_stock.sql`: stock table with `available_quantity >= 0`, which permits a valid deduction to zero.
- `V2__seed_stock.sql`: 10 books and 5 keyboards.
- `StockServiceTest`: real PostgreSQL tests under `@ActiveProfiles("test")`, without an outer test transaction masking service commits.

### Current local run instructions

Prerequisites: Java 17, Docker Compose, and a local `.env` beside `compose.yaml` defining `INVENTORY_DB_IMAGE_NAME=postgres:17`, `INVENTORY_DB_USER`, `INVENTORY_DB_PASSWORD`, and `INVENTORY_DB_NAME`. Do not use production credentials. The application imports that file as properties, so launch Maven from `inventory-service`. No credentials are recorded here.

From this experiment directory, start the database:

```bash
docker compose up inventory-db
```

The current configuration publishes `127.0.0.1:5432` and mounts `inventory_data` at `/var/lib/postgresql/data`, PostgreSQL 17's data location. Confirm readiness before starting Java.

In a second terminal, connect to the initialized database:

```bash
docker compose exec inventory-db sh -c 'psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -W'
```

For the current local database user `inventory`, create the test database once if it does not already exist:

```sql
CREATE DATABASE inventory_test OWNER inventory;
```

Exit with `\q`. From `inventory-service`, run:

```bash
./mvnw spring-boot:run
```

Or run only the stock integration tests:

```bash
./mvnw -Dtest=StockServiceTest test
```

`application-test.properties` overrides the URL to database `inventory_test`; username, password, and Hibernate validation are inherited. Flyway migrates that database independently. Tests use and clean up SKU `TEST-BOOK`; do not run concurrent copies of the suite against the same database.

### Results and limits

The developer supplied seed query output and reported:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

The tests verify committed deduction (10 → 7), insufficient-stock rejection without changing stock, and ten competing attempts to reserve two units each from eleven units. The last test asserts five successes, five rejections, and one remaining unit. Timed joins check worker termination before database cleanup. This coordinates attempts but does not guarantee overlapping database transactions or prove lock contention deterministically. Timeout failure can leave workers and test data for investigation.

Tests were run by the developer, not rerun by the assistant during documentation. No end-to-end three-service result is claimed. No telemetry experiment has yet been run against this Inventory service.

**Single next action:** Design a durable reservation record keyed by order ID, so repeat requests cannot deduct stock twice.
