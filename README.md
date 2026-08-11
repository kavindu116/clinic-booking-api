# Clinic Appointment Booking API

A production-shaped REST backend for clinic appointment booking, built to demonstrate
concurrency-safe reservations, stateless JWT auth, and a real deployment pipeline.

[![CI](https://github.com/kavindu116/clinic-booking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/kavindu116/clinic-booking-api/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

> **Live API:** _(coming in Week 4)_ · **Swagger UI:** `/swagger-ui.html`

---

## Why this project exists

Most portfolio CRUD apps quietly break under concurrent load. This one is built around a
problem that actually has a correct and an incorrect answer: **two patients requesting the
same appointment slot at the same instant must not both succeed.**

The naive implementation has a check-then-act race:

```
T1: is the slot free? -> yes
T2: is the slot free? -> yes   (T1 has not committed yet)
T1: INSERT booking
T2: INSERT booking
-> one slot, two patients
```

The fix here uses two independent layers:

1. **Pessimistic row lock** — `SELECT ... FOR UPDATE` on the doctor's bookings for that
   slot (`BookingRepository.lockActiveBySlot`). The second transaction blocks until the
   first commits, then sees the real state.
2. **Partial unique index** — a database-level guarantee that survives application bugs,
   multiple app instances, and manual SQL:

```sql
CREATE UNIQUE INDEX uq_active_booking_slot
    ON bookings (doctor_id, slot_start)
    WHERE status <> 'CANCELLED';
```

Cancelled bookings are excluded from the index, so a released slot becomes bookable again
without soft-delete gymnastics. When the index does fire, `GlobalExceptionHandler` maps it
to a clean `409 SLOT_ALREADY_BOOKED` rather than a 500.

This is verified by a concurrency test that fires N threads at one slot and asserts exactly
one success (Week 3).

---

## Tech stack

| Layer | Choice | Reason |
|---|---|---|
| Language / framework | Java 21, Spring Boot 3.4 | Records, pattern matching, virtual-thread ready |
| Database | PostgreSQL 16 | Partial indexes, `FOR UPDATE`, `TIMESTAMPTZ` |
| Migrations | Flyway | Versioned schema; `ddl-auto: validate` in every environment |
| Auth | Spring Security + JWT (jjwt) | Stateless access tokens, DB-backed rotating refresh tokens |
| Cache / rate limit | Redis | Doctor lookups, login throttling |
| Async | RabbitMQ | Notification fan-out via transactional outbox |
| Testing | JUnit 5, Testcontainers | Real Postgres in tests — H2 cannot model partial indexes or row locks |
| Docs | springdoc-openapi | Swagger UI is the front end |
| Ops | Actuator, Micrometer, Prometheus | Health probes and metrics |

---

## Architecture

```
                    ┌──────────────┐
                    │  Swagger UI  │
                    └──────┬───────┘
                           │ HTTPS + Bearer JWT
              ┌────────────▼─────────────┐
              │     Spring Boot API      │
              │  Controller → Service    │
              │        → Repository      │
              │  JwtAuthFilter (stateless)│
              │  GlobalExceptionHandler   │
              └───┬─────────┬──────────┬─┘
                  │         │          │
          ┌───────▼──┐  ┌───▼───┐  ┌───▼──────┐
          │PostgreSQL│  │ Redis │  │ RabbitMQ │
          │ bookings │  │ cache │  │notify.q  │
          │ + locks  │  │ + rate│  └───┬──────┘
          └──────────┘  └───────┘      │
                                  ┌────▼───────┐
                                  │Email worker│
                                  └────────────┘
```

### Domain model

| Table | Purpose |
|---|---|
| `users` | Identity + role (`PATIENT` / `DOCTOR` / `ADMIN`) |
| `refresh_tokens` | SHA-256 hashes only, rotating, revocable |
| `doctors` | Profile linked 1:1 to a user |
| `availability` | Weekly recurring rule (day, window, slot length) |
| `bookings` | Reservations, optimistic `@Version`, partial unique index |

Slots are **not** materialised. A doctor working Mon–Fri for a year would generate ~2,000
rows that mostly stay empty; instead, slots are derived from the availability rule at query
time and diffed against active bookings.

---

## Running locally

### Prerequisites

- **JDK 21** — [Temurin](https://adoptium.net/)
- **Docker Desktop** — needed for Postgres/Redis/RabbitMQ and for Testcontainers
  - Windows: enable WSL2 when the installer asks, then `wsl --install` if prompted
  - macOS: `brew install --cask docker`
  - Linux: `curl -fsSL https://get.docker.com | sh` then `sudo usermod -aG docker $USER`
  - Verify: `docker --version && docker compose version`
- **Maven** — or just use `./mvnw` once you generate the wrapper (`mvn wrapper:wrapper`)

### Start

```bash
git clone https://github.com/kavindu116/clinic-booking-api.git
cd clinic-booking-api

cp .env.example .env
# Generate a real JWT secret and paste it into .env
openssl rand -base64 48

# Infrastructure only — the app runs from your IDE
docker compose up -d

# Wait for health, then verify
docker compose ps

mvn spring-boot:run
```

Open **http://localhost:8080/swagger-ui.html**

Seeded dev accounts (`SEED_ENABLED=true`, never enabled in production):

| Email | Password | Role |
|---|---|---|
| `admin@clinic.lk` | `Admin@123` | ADMIN |
| `dr.silva@clinic.lk` | `Doctor@123` | DOCTOR |
| `patient@clinic.lk` | `Patient@123` | PATIENT |

### Everything in containers

```bash
docker compose --profile app up --build
```

### Tests

```bash
mvn verify          # spins up a throwaway Postgres via Testcontainers
open target/site/jacoco/index.html
```

---

## API surface

### Implemented

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | Create a patient account |
| POST | `/api/v1/auth/login` | — | Obtain access + refresh tokens |
| POST | `/api/v1/auth/refresh` | — | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Bearer | Revoke all refresh tokens |
| GET | `/api/v1/auth/me` | Bearer | Current user |
| GET | `/actuator/health` | — | Liveness / readiness |

### Planned

`/api/v1/doctors`, `/api/v1/doctors/{id}/availability`, `/api/v1/doctors/{id}/slots`,
`/api/v1/bookings` (create, cancel, reschedule, list), `/api/v1/admin/**`.

---

## Security notes

- Passwords hashed with BCrypt (strength 12).
- Access tokens live 15 minutes and are never persisted; refresh tokens live 7 days,
  are stored as SHA-256 hashes, and **rotate on every use**. Presenting a
  previously-rotated token is treated as theft and revokes the whole family.
- Login returns an identical response for "no such email" and "wrong password" so the
  endpoint cannot be used to enumerate registered addresses.
- Internal exception messages and stack traces never reach the client; every error is a
  stable `{ timestamp, status, code, message, path }` envelope.
- `spring.jpa.open-in-view: false` and `ddl-auto: validate` — no lazy-loading surprises,
  no accidental schema mutation.

---

## Roadmap

- [x] **Week 1** — Schema, migrations, JWT auth with rotation, error envelope, Docker Compose, CI
- [ ] **Week 2** — Doctor and availability management, slot derivation, booking create/cancel/reschedule, concurrency control
- [ ] **Week 3** — Outbox + RabbitMQ notifications, Redis caching and rate limiting, concurrency test suite, observability
- [ ] **Week 4** — Deploy to Oracle Cloud (ARM), TLS via Caddy, live Swagger, demo video

---

## License

MIT
