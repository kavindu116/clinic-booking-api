# Clinic Appointment Booking API

A production-shaped REST backend for clinic appointment booking, built around a problem
that has a genuinely correct and incorrect answer: **two patients requesting the same
appointment slot in the same instant must not both succeed.**

[![CI](https://github.com/kavindu116/clinic-booking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/kavindu116/clinic-booking-api/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Tests](https://img.shields.io/badge/tests-44%20passing-brightgreen)

<!-- After deploying, replace the line below with:
> **Live API:** https://your-host/swagger-ui.html
-->
> **Swagger UI:** `/swagger-ui.html` · **OpenAPI spec:** `/v3/api-docs`

---

## Contents

- [The problem this solves](#the-problem-this-solves)
- [Architecture](#architecture)
- [Domain model](#domain-model)
- [Tech choices and why](#tech-choices-and-why)
- [Running locally](#running-locally)
- [API surface](#api-surface)
- [Testing](#testing)
- [Bugs worth remembering](#bugs-worth-remembering)
- [Deployment](#deployment)
- [What this is not](#what-this-is-not)

---

## The problem this solves

Most portfolio CRUD apps quietly break under concurrent load. This one is built around the
booking race, which has a real answer and a wrong one.

The naive implementation has a check-then-act race:

```
T1: is the slot free? -> yes
T2: is the slot free? -> yes   (T1 has not committed yet)
T1: INSERT booking
T2: INSERT booking
-> one slot, two patients
```

### Why `SELECT ... FOR UPDATE` is not the answer

The obvious fix is a pessimistic row lock. It does not work here, and the reason is worth
knowing: **`FOR UPDATE` locks rows that exist.** When the first booking for a slot is
created, no row exists yet — so there is nothing to lock, both transactions see an empty
result, and both insert.

PostgreSQL under `READ COMMITTED` does not take a lock on a non-existent row. MySQL's
InnoDB does, via gap locks under `REPEATABLE READ` — so this bug is database-specific,
which is exactly why the tests run against real PostgreSQL rather than H2.

### What this uses instead

**Layer 1 — a transaction-scoped advisory lock**, keyed on `(doctorId, slotStart)`:

```sql
SELECT pg_advisory_xact_lock(doctor_id, slot_key)
```

Advisory locks are not tied to rows. The application names a lock and PostgreSQL
serialises everyone who asks for the same name. The `_xact_` scope releases it
automatically on commit or rollback, so a forgotten unlock cannot strand a connection. The
lock is per-slot, not per-doctor, so bookings for different times still run in parallel —
there is a test for that too.

**Layer 2 — a partial unique index**, as the correctness backstop:

```sql
CREATE UNIQUE INDEX uq_active_booking_slot
    ON bookings (doctor_id, slot_start)
    WHERE status <> 'CANCELLED';
```

This holds even if the application has a bug, runs as several instances, or someone writes
SQL by hand. Cancelled bookings are excluded from the index, so a released slot becomes
bookable again without soft-delete gymnastics. When the index does fire,
`GlobalExceptionHandler` maps it to a clean `409 SLOT_ALREADY_BOOKED` rather than a 500.

Layer 1 gives a good user experience. Layer 2 guarantees correctness.

`BookingConcurrencyIT` proves it: ten threads, one slot, exactly one success and nine clean
409s — and it asserts that **no** thread reaches the database constraint, since that would
mean layer 1 had silently stopped working and layer 2 was covering for it.

### Slots are derived, not stored

A doctor's schedule is one row per weekly block — "Mondays, 09:00–13:00, 30-minute slots".
Bookable times are computed from that rule at request time and diffed against existing
bookings.

Materialising slots instead would mean roughly 1,250 rows per doctor per year, almost all
of them empty, and a schedule change would mean regenerating them.

The derivation lives in `SlotCalculator`, a pure function with no Spring context and no
database, so its edge cases — partial trailing slots, midnight wrap, timezone conversion,
split morning/afternoon blocks — are covered by unit tests that run in milliseconds.

### Notifications survive a broker outage

Sending the confirmation directly inside the booking transaction couples two systems that
fail independently:

```java
@Transactional
public Booking create(...) {
    Booking booking = repository.save(...);
    rabbitTemplate.convertAndSend("booking.confirmed", event);  // ← problem
    return booking;
}
```

If the broker is unreachable, the send throws, the transaction rolls back, and the booking
is gone — a patient cannot make an appointment because a message broker is having a bad
day. And if the send succeeds but the commit then fails, a confirmation goes out for a
booking that does not exist.

The **transactional outbox** removes the second system from the write path. The event is
written to an `outbox_events` row in the same transaction as the booking — same database,
so either both land or neither does. A scheduled poller reads pending rows and publishes
them afterwards, using `FOR UPDATE SKIP LOCKED` so that multiple app instances share the
work instead of queueing behind each other.

Delivery is **at-least-once**, not exactly-once: if a publish succeeds and the commit
marking it published then fails, the row goes out again. The consumer is idempotent on
message ID to absorb that. Exactly-once is not something distributed systems offer;
at-least-once plus idempotency is.

---

## Architecture

```
                      ┌──────────────┐
                      │  Swagger UI  │
                      └──────┬───────┘
                             │ HTTPS + Bearer JWT
                      ┌──────▼───────┐
                      │    Caddy     │  TLS, security headers,
                      │ reverse proxy│  actuator allow-list
                      └──────┬───────┘
              ┌──────────────▼─────────────────┐
              │       Spring Boot API          │
              │  CorrelationIdFilter           │
              │  LoginRateLimitFilter (Redis)  │
              │  JwtAuthFilter (stateless)     │
              │  Controller → Service → Repo   │
              │  GlobalExceptionHandler        │
              └───┬─────────┬──────────┬───────┘
                  │         │          │
          ┌───────▼──┐  ┌───▼───┐  ┌───▼──────┐
          │PostgreSQL│  │ Redis │  │ RabbitMQ │
          │ bookings │  │ cache │  │notify.q  │
          │ outbox   │  │ + rate│  │  + DLQ   │
          │ + locks  │  │  limit│  └───┬──────┘
          └──────────┘  └───────┘      │
                 ▲                ┌────▼────────┐
                 └────────────────│ Notification│
                   poller drains  │  consumer   │
                   the outbox     └─────────────┘
```

### Package layout

Organised **by feature**, not by layer. Changing how bookings work means opening one
folder, not three.

```
lk.kavindu.clinic
├── common/          BaseEntity, ApiError, ErrorCode, GlobalExceptionHandler
├── config/          SecurityConfig, CacheConfig, OpenApiConfig, DevDataSeeder
├── security/        JwtService, JwtAuthFilter, AppUserPrincipal, RestAuthEntryPoint
├── user/            User, RefreshToken, repositories, TokenCleanupJob
├── auth/            AuthController, AuthService, TokenRevocationService, dto/
├── doctor/          Doctor, Availability, SlotCalculator, DoctorSecurity, dto/
├── booking/         Booking, BookingService, SlotLock, dto/
├── outbox/          OutboxEvent, OutboxWriter, OutboxPublisher
├── notification/    NotificationMessaging, NotificationConsumer, BookingEventPayload
└── observability/   CorrelationIdFilter, BookingMetrics
```

`common/` and `security/` are the exceptions — they belong to no single feature.

---

## Domain model

| Table | Purpose |
|---|---|
| `users` | Identity and role (`PATIENT` / `DOCTOR` / `ADMIN`) |
| `refresh_tokens` | SHA-256 hashes only, rotating, revocable |
| `doctors` | Profile linked 1:1 to a user |
| `availability` | Weekly recurring rule — day, window, slot length |
| `bookings` | Reservations, optimistic `@Version`, partial unique index |
| `outbox_events` | Domain events awaiting publication, JSONB payload |

Two design notes worth calling out:

**Outbox payloads are snapshots, not references.** A doctor's name or fee may change after
a booking is made; the confirmation should say what was true when it was made. So the
payload stores values, not just IDs.

**Refresh tokens are stored hashed.** A database dump does not hand an attacker usable
sessions. Same reasoning as password hashing.

---

## Tech choices and why

| Layer | Choice | Reason |
|---|---|---|
| Language / framework | Java 21, Spring Boot 3.4 | Records, text blocks, pattern matching |
| Database | PostgreSQL 16 | Partial indexes, advisory locks, `TIMESTAMPTZ` |
| Migrations | Flyway | Versioned schema; `ddl-auto: validate` everywhere |
| Auth | Spring Security + jjwt | Stateless access tokens, DB-backed rotating refresh tokens |
| Cache / rate limit | Redis | Doctor lookups; atomic `INCR` for login throttling across instances |
| Async | RabbitMQ | Notification fan-out via transactional outbox, with a DLQ |
| Testing | JUnit 5, Testcontainers | Real PostgreSQL — H2 models neither partial indexes nor advisory locks |
| Docs | springdoc-openapi | Swagger UI is the front end |
| Ops | Actuator, Micrometer, Prometheus | Health probes, JVM and business metrics |
| Proxy | Caddy | Automatic TLS, no certbot and no cron job |

A few configuration decisions that are deliberate rather than default:

- **`ddl-auto: validate`**, never `update`. Flyway owns the schema; Hibernate's job is to
  refuse to start if the entities and the tables disagree.
- **`open-in-view: false`**. The default leaves the Hibernate session open through response
  rendering, which hides N+1 queries behind lazy loads in the view layer.
- **BCrypt strength 12** (~250 ms per hash). Deliberately slow, which is also why the login
  endpoint is rate limited — otherwise it is a denial-of-service amplifier.
- **The rate limiter fails open.** If Redis is unreachable, logins are allowed and a warning
  is logged. A defence in depth should not become a single point of failure.

---

## Running locally

### Prerequisites

- **JDK 21** ([Temurin](https://adoptium.net/))
- **Docker Desktop** — required for the infrastructure *and* for Testcontainers
- **Maven** 3.9+

### Start

```bash
git clone https://github.com/kavindu116/clinic-booking-api.git
cd clinic-booking-api

cp .env.example .env
openssl rand -base64 48        # paste into JWT_SECRET in .env

docker compose up -d           # postgres, redis, rabbitmq
docker compose ps              # wait for all three to be healthy

mvn spring-boot:run
```

Open **http://localhost:8080/swagger-ui.html**

Seeded development accounts (`SEED_ENABLED=true`, never enabled in production):

| Email | Password | Role |
|---|---|---|
| `admin@clinic.lk` | `Admin@123` | ADMIN |
| `dr.silva@clinic.lk` | `Doctor@123` | DOCTOR |
| `patient@clinic.lk` | `Patient@123` | PATIENT |

### A five-minute walkthrough

1. `POST /api/v1/auth/login` as the patient, copy `accessToken`, click **Authorize**
2. `GET /api/v1/doctors/1/slots?date=<a weekday>` — eight derived slots
3. `POST /api/v1/bookings` with a slot's `start` value → **201**
4. Repeat step 2 — that slot is now `available: false`
5. Repeat step 3 → **409 `SLOT_ALREADY_BOOKED`**
6. `PATCH /api/v1/bookings/{id}/cancel`, then step 2 again — available once more

Within two seconds of step 3 the application log shows the outbox row being published and
the notification being rendered.

### Watching the outbox handle an outage

```bash
docker compose stop rabbitmq
# make another booking — it still returns 201
# the log shows: Outbox publish failed (attempt 1), will retry
docker compose start rabbitmq
# within seconds: Outbox published: id=N type=CONFIRMED
```

The booking is never at the mercy of the broker.

### Everything in containers

```bash
docker compose --profile app up --build
```

### Tests

```bash
mvn verify
open target/site/jacoco/index.html
```

---

## API surface

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | Create a patient account |
| POST | `/api/v1/auth/login` | — | Access + refresh tokens |
| POST | `/api/v1/auth/refresh` | — | Rotate the refresh token |
| POST | `/api/v1/auth/logout` | Bearer | Revoke all refresh tokens |
| GET | `/api/v1/auth/me` | Bearer | Current user |

### Doctors and availability

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/doctors` | — | List active doctors, filter by specialization |
| GET | `/api/v1/doctors/specializations` | — | Distinct specializations offered |
| GET | `/api/v1/doctors/{id}` | — | Doctor profile |
| GET | `/api/v1/doctors/{id}/availability` | — | Weekly availability rules |
| GET | `/api/v1/doctors/{id}/slots?date=` | — | **Derived** bookable slots |
| POST | `/api/v1/doctors` | Admin | Create a doctor account |
| PUT | `/api/v1/doctors/{id}/availability` | Admin or self | Replace the weekly schedule |
| DELETE | `/api/v1/doctors/{id}` | Admin | Deactivate (soft delete) |

### Bookings

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/bookings` | Patient | Book a slot — concurrency-safe |
| GET | `/api/v1/bookings/me` | Patient | Own bookings, paginated |
| GET | `/api/v1/bookings/{id}` | Owner / doctor / admin | One booking |
| PATCH | `/api/v1/bookings/{id}/cancel` | Owner / doctor / admin | Cancel |
| PATCH | `/api/v1/bookings/{id}/reschedule` | Owner / doctor / admin | Move to another slot |
| GET | `/api/v1/bookings/doctors/{id}` | Doctor or admin | A doctor's schedule |

### Booking rules

Configurable under `app.clinic` — defaults in brackets.

- The slot must exist on the doctor's availability grid
- Not in the past; at least `min-advance-booking-minutes` [30] ahead
- At most `max-advance-booking-days` [60] ahead
- At most `max-upcoming-bookings-per-patient` [3] confirmed future bookings
- A patient cannot hold two appointments at the same time, even with different doctors
- Patients must cancel `cancellation-window-hours` [4] ahead; staff are exempt

### Error format

Every error is the same envelope, with a stable `code` clients can branch on without
parsing prose:

```json
{
  "timestamp": "2026-09-14T03:30:00Z",
  "status": 409,
  "code": "SLOT_ALREADY_BOOKED",
  "message": "This time slot has just been taken. Please choose another slot.",
  "path": "/api/v1/bookings"
}
```

Requesting someone else's booking returns **404, not 403**, so IDs cannot be used to probe
whether a booking exists. In a clinic that is a privacy question, not just a nicety.

---

## Testing

**44 tests**, split by what they need:

| Suite | Kind | Needs |
|---|---|---|
| `SlotCalculatorTest` | Unit — pure function | Nothing. Runs in milliseconds. |
| `AuthControllerIT` | Integration | Testcontainers PostgreSQL |
| `DoctorControllerIT` | Integration | Testcontainers PostgreSQL |
| `BookingRulesIT` | Integration | Testcontainers PostgreSQL |
| `BookingConcurrencyIT` | Integration — real threads | Testcontainers PostgreSQL |
| `OutboxIT` | Integration + mocked broker | Testcontainers PostgreSQL |

Surefire runs `*Test` (fast, no external dependencies); Failsafe runs `*IT`. `mvn test`
gives quick feedback, `mvn verify` runs everything.

**Why real PostgreSQL and not H2:** the partial unique index, `pg_advisory_xact_lock`,
`FOR UPDATE SKIP LOCKED` and `TIMESTAMPTZ` semantics are all things H2 either lacks or
models differently. A green suite on H2 would have proved nothing about the parts of this
system that actually matter.

The concurrency test deserves a note. It uses two `CountDownLatch` gates — one to release
all ten threads at the same instant, one for the main thread to wait on. Without the start
gate the first thread finishes before the second begins and there is no race to observe.

---

## Bugs worth remembering

Thirteen defects were found and fixed during the build. These five taught the most, and
none of them threw an exception:

**A wildcard route matcher made two protected endpoints public.** `/api/v1/auth/**` in
`SecurityConfig` matched `/auth/me` and `/auth/logout` as well as the three intended public
routes. The code compiled, a smoke test passed, and unauthenticated requests reached a
controller expecting a principal.

**Writing and then rejecting cannot happen in one transaction.** On refresh-token reuse the
service revoked every token the user had and then threw to reject the request — both inside
one `@Transactional` method. The exception rolled the revocation back. The log line
printed; the database disagreed. Fixed by moving the revocation into a separate bean with
`REQUIRES_NEW` propagation — it has to be a separate bean, since calling a method on the
same class bypasses the Spring proxy entirely.

**`LocalTime` arithmetic wraps past midnight, silently.** The slot loop ran
`while (!cursor.plus(slotLength).isAfter(blockEnd))`. For a block ending at 23:59, 23:30
plus thirty minutes is 00:00 — and `00:00.isAfter(23:59)` is false. So it emitted a slot
running past the end of its own block. Rewritten in integer minutes, where there is nothing
to wrap: not a guard clause, but a representation in which the bug cannot exist.

**`WHERE :param IS NULL OR LOWER(col) = LOWER(:param)` is a trap on PostgreSQL.** This is
the standard optional-filter pattern. When the parameter is null the driver sends an
untyped null, Postgres infers `bytea`, and `lower(bytea)` does not exist — a 500. The
catch is that it works fine when the filter *is* provided, so the filtered case tests
green and the endpoint breaks on its most common call. Replaced with two explicit queries.

**Forty-four passing tests, and the feature never ran.** The outbox publisher is
`@Scheduled`, but `@EnableScheduling` was missing from the main class. Every test passed,
because tests call `publishPending()` directly — which is the right way to test a scheduled
method, and also means they say nothing about whether anything ever calls it. Bookings
would have worked. Confirmations would have silently never gone out. Manual verification
after each feature is now part of the routine.

---

## Deployment

Full walkthrough in [`deploy/DEPLOYMENT.md`](deploy/DEPLOYMENT.md) — provisioning an Oracle
Cloud Always Free ARM instance, both layers of firewall, DNS, and TLS.

The production stack differs from development in ways worth naming:

- **Postgres, Redis and RabbitMQ publish no host ports.** They are reachable only from
  inside the Docker network. Only Caddy binds 80 and 443.
- **Seeding is off.** There is no admin account and no endpoint that creates one — a public
  "make me an admin" route is a serious hole. The first admin is promoted by hand in the
  database, once.
- **Actuator is allow-listed at the proxy.** `/actuator/health` and `/actuator/info` are
  public; everything else returns 404 rather than leaking configuration.
- **Caddy passes the real client IP** via `X-Forwarded-For`, and Spring is configured with
  `forward-headers-strategy: framework`. Without both, the rate limiter sees only the proxy's
  address and every user shares one bucket.
- **Containers have memory limits and log rotation.** An unbounded JSON log file fills a
  disk in weeks.

```bash
cd deploy
./deploy.sh          # pull, build, restart, wait for readiness
./deploy.sh logs     # follow the application
./deploy.sh status   # container health
./deploy.sh backup   # gzipped pg_dump, keeps the last seven
```

---

## What this is not

Knowing the limits of what you built is worth more than pretending there are none.

- **Single machine, no redundancy.** If the VM dies the API is down until it comes back.
- **Database on the same box.** Fine at this size; a real deployment uses managed Postgres
  with automated backups and point-in-time recovery.
- **Deploys have a few seconds of downtime.** Rolling deploys need at least two app
  instances behind the proxy.
- **Metrics are exported but nothing scrapes them.** Prometheus and Grafana are the obvious
  next step.
- **The consumer's idempotency cache is in-memory.** Correct for one instance, wrong the
  moment there are two — that needs Redis or a processed-messages table.
- **The outbox retries at a flat interval.** Exponential backoff with a `next_attempt_at`
  column would be kinder to a broker that is genuinely down, and would stop the log filling
  with connection attempts during an outage.
- **Notifications are logged, not emailed.** The pipeline is real end to end; only the final
  delivery step is a stub. Swapping in SMTP touches one method.

---

## License

MIT
