# Bitespeed Identity Reconciliation Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-88%20passed-success)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot REST API that links customer identities across multiple purchases — even when different emails and phone numbers are used. Built for the [BiteSpeed Backend Task: Identity Reconciliation](https://bitespeed.co/).

**Live endpoint:** `https://YOUR-APP.onrender.com/identify`

---

## Table of Contents

- [Author](#author)
- [Problem Statement](#problem-statement)
- [Tech Stack](#tech-stack)
- [Architecture & Design](#architecture--design)
- [API Reference](#api-reference)
- [Reconciliation Logic](#reconciliation-logic)
- [Database Schema](#database-schema)
- [Running Locally](#running-locally)
- [Testing](#testing)
- [Example curl Commands](#example-curl-commands)
- [Deploying on Render.com](#deploying-on-rendercom)
- [Project Structure](#project-structure)
- [Scope for Improvement](#scope-for-improvement)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## Author

| | |
|---|---|
| **Name** | fl4nk3r |
| **GitHub** | [@fl4nk3r-h](https://github.com/fl4nk3r-h) |
| **Role** | Backend Intern — BiteSpeed |
| **Year** | 2026 |

---

## Problem Statement

FluxKart.com customers sometimes check out with different contact details (email / phone number) across orders. This makes it hard to identify repeat customers and offer a personalised experience.

**This service solves that problem** by maintaining a graph of linked contacts. When a new checkout event arrives with an email and/or phone number, the service:

1. **Finds** all existing contacts that share the email or phone.
2. **Links** them into a single identity cluster (one primary + N secondaries).
3. **Returns** a consolidated view of who the customer is.

Even if a customer uses completely different emails across orders, as long as any phone number or email overlaps with a previous order, the service will connect them.

---

## Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Java 21 (LTS) | Long-term support, modern features (records, pattern matching, virtual threads) |
| Framework | Spring Boot 4.0.3 | Industry standard for production Java microservices |
| ORM | Spring Data JPA + Hibernate | Declarative repository queries, automatic schema management |
| Database | PostgreSQL (prod) / H2 (test) | Robust relational DB for production; fast in-memory DB for tests |
| Build | Maven | Widely adopted, reproducible builds |
| Security | Spring Security | Stateless config with CSRF disabled for REST API |
| Validation | Jakarta Bean Validation | Declarative request validation with `@Valid` |
| Hosting | Render.com | Free tier, GitHub auto-deploy |

---

## Architecture & Design

The application follows a **layered architecture** that separates concerns cleanly:

```
HTTP Request
     │
     ▼
┌─────────────────┐
│   Controller    │  ← Thin HTTP layer: validation, routing, response shaping
│   (REST API)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    Service      │  ← All business logic: reconciliation, merging, dedup
│  (Business)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Repository    │  ← Data access: JPQL queries, Spring Data JPA
│    (Data)       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │  ← Persistent storage
└─────────────────┘
```

**Key design decisions:**

- **DTOs separate from entities** — `IdentifyRequest` / `IdentifyResponse` are decoupled from the `Contact` JPA entity.
- **Global exception handler** — `@RestControllerAdvice` catches validation and server errors, returning structured JSON.
- **Transactional service** — The `identify()` method runs in a single transaction, ensuring atomicity during merges.
- **Database indexes** — `email`, `phone_number`, and `linked_id` are indexed for fast lookups.
- **Soft delete support** — The `deletedAt` column is honoured by all queries.

---

## API Reference

### `POST /identify`

Accepts an email and/or phone number and returns the consolidated contact identity.

**Request body** (at least one field required):

```json
{
  "email": "mcfly@hillvalley.edu",
  "phoneNumber": "123456"
}
```

| Field | Type | Required |
|-------|------|----------|
| `email` | `string` or `null` | At least one of `email` or `phoneNumber` |
| `phoneNumber` | `string` or `null` | At least one of `email` or `phoneNumber` |

**Success Response** (`200 OK`):

```json
{
  "contact": {
    "primaryContatctId": 1,
    "emails": ["lorraine@hillvalley.edu", "mcfly@hillvalley.edu"],
    "phoneNumbers": ["123456"],
    "secondaryContactIds": [23]
  }
}
```

> **Note:** `primaryContatctId` preserves the typo from the BiteSpeed specification intentionally.

| Field | Description |
|-------|-------------|
| `primaryContatctId` | ID of the primary (root) contact |
| `emails` | All known emails; primary's email first |
| `phoneNumbers` | All known phones; primary's phone first |
| `secondaryContactIds` | IDs of all secondary contacts linked to the primary |

**Error Response** (`400 Bad Request`):

```json
{
  "timestamp": "2026-03-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "At least one of email or phoneNumber must be provided"
}
```

### `GET /health`

Liveness probe — returns `200 OK` with body `"OK"`.

---

## Reconciliation Logic

The service handles **4 cases** on every `/identify` call:

### Case 1 — No match found

No existing contact shares the email or phone. Creates a new **primary** contact. Returns it with an empty `secondaryContactIds` array.

### Case 2 — Partial match (new info on known contact)

The incoming email or phone matches an existing contact, but the request also carries a field not yet seen in that cluster. A new **secondary** contact is created and linked to the primary.

**Example:**

```
Existing:  { id: 1, email: "lorraine@hillvalley.edu", phone: "123456", primary }

Request:   { email: "mcfly@hillvalley.edu", phone: "123456" }

Result:    → Creates secondary { id: 23, email: "mcfly@hillvalley.edu", phone: "123456", linkedId: 1 }
           → Returns both emails, one phone, secondaryContactIds: [23]
```

### Case 3 — Exact match

All info is already known. No new rows created. Returns the consolidated cluster.

### Case 4 — Two separate primaries get connected (merge)

If the request's email matches cluster A and its phone matches cluster B, the two clusters must be **merged**:

1. The **older** primary (by `createdAt`) wins.
2. The newer primary is **demoted** to secondary (`linkedId` → winner's ID).
3. All secondaries previously linked to the loser are **re-linked** to the winner.

**Example:**

```
Existing:  { id: 11, email: "george@hillvalley.edu",   phone: "919191", primary }
           { id: 27, email: "biffsucks@hillvalley.edu", phone: "717171", primary }

Request:   { email: "george@hillvalley.edu", phone: "717171" }

Result:    → id=27 demoted to secondary, linkedId=11
           → All of id=27's former secondaries re-linked to id=11
           → Response: primaryContatctId=11, emails=[george, biffsucks], phones=[919191, 717171]
```

---

## Database Schema

```sql
CREATE TABLE contact (
    id              BIGSERIAL PRIMARY KEY,
    phone_number    VARCHAR(255),
    email           VARCHAR(255),
    linked_id       BIGINT,
    link_precedence VARCHAR(10) NOT NULL CHECK (link_precedence IN ('primary', 'secondary')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

CREATE INDEX idx_contact_email     ON contact(email);
CREATE INDEX idx_contact_phone     ON contact(phone_number);
CREATE INDEX idx_contact_linked_id ON contact(linked_id);
```

> Hibernate auto-creates this schema on first run via `spring.jpa.hibernate.ddl-auto=update`.

---

## Running Locally

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.8+**
- **PostgreSQL** running locally (or via Docker)

### 1. Clone the repo

```bash
git clone https://github.com/fl4nk3r-h/bitespeed.git
cd bitespeed
```

### 2. Set up the database

```sql
CREATE DATABASE bitespeed;
```

### 3. Configure environment variables

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/bitespeed
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=yourpassword
```

Or edit `src/main/resources/application.properties` directly for local dev.

### 4. Run the app

```bash
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080`.

---

## Testing

### Test Strategy

The project uses a **multi-layer testing approach** with **88 tests** across 8 test classes:

| Layer | Test Class | Type | Tests | What It Covers |
|-------|-----------|------|-------|----------------|
| Model | `ContactTest` | Unit | 18 | Builder, getters/setters, enum, constructors |
| DTO | `IdentifyRequestTest` | Unit | 13 | Validation logic, equality, edge cases |
| DTO | `IdentifyResponseTest` | Unit | 8 | Builder, payload structure, ordering |
| Repository | `ContactRepositoryTest` | Integration | 13 | JPQL queries, soft-delete filtering, cross-match |
| Service | `ContactServiceTest` | Unit (Mockito) | 10 | All 4 reconciliation cases, edge cases |
| Controller | `ContactControllerTest` | Web slice | 8 | HTTP layer, JSON shape, validation, status codes |
| Exception | `GlobalExceptionHandlerTest` | Web slice | 3 | 400/500 structured error responses |
| E2E | `ContactServiceIntegrationTest` | Full-stack | 14 | All cases end-to-end via HTTP + real H2 database |
| Smoke | `BitespeedApplicationTests` | Context load | 1 | Spring context wires without errors |

### Running Tests

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=ContactServiceIntegrationTest

# Run tests with verbose output
./mvnw test -Dsurefire.useFile=false
```

Tests use an **in-memory H2 database** — no external database needed. Configuration is in `src/test/resources/application.properties`.

---

## Example curl Commands

**New contact:**

```bash
curl -X POST http://localhost:8080/identify \
  -H "Content-Type: application/json" \
  -d '{"email":"lorraine@hillvalley.edu","phoneNumber":"123456"}'
```

**Trigger secondary creation:**

```bash
curl -X POST http://localhost:8080/identify \
  -H "Content-Type: application/json" \
  -d '{"email":"mcfly@hillvalley.edu","phoneNumber":"123456"}'
```

**Trigger primary merge (edge case):**

```bash
# Assumes two separate primaries already exist:
curl -X POST http://localhost:8080/identify \
  -H "Content-Type: application/json" \
  -d '{"email":"george@hillvalley.edu","phoneNumber":"717171"}'
```

**Email-only lookup:**

```bash
curl -X POST http://localhost:8080/identify \
  -H "Content-Type: application/json" \
  -d '{"email":"lorraine@hillvalley.edu"}'
```

**Health check:**

```bash
curl http://localhost:8080/health
```

---

## Deploying on Render.com

> Render no longer supports native Java runtimes — use the **Docker** environment.

1. Push repo to GitHub (ensure `.env` is gitignored).
2. Create a new **Web Service** on [Render](https://render.com/), connect the repo.
3. Set **Environment** to **Docker** (Render auto-detects the `Dockerfile`).
4. Add environment variables in the Render dashboard:
   - `DATABASE_URL` → your PostgreSQL JDBC URL (e.g. from Neon.tech)
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
5. Deploy — Render will build the multi-stage Docker image and start the container.
6. The app listens on `$PORT` (Render injects this automatically).

---

## Project Structure

```
src/
├── main/java/com/fluxkart/bitespeed/
│   ├── BitespeedApplication.java          # Spring Boot entry point
│   ├── config/
│   │   └── SecurityConfig.java            # Stateless security, CSRF disabled
│   ├── controller/
│   │   └── ContactController.java         # POST /identify, GET /health
│   ├── dto/
│   │   ├── request/
│   │   │   └── IdentifyRequest.java       # Input DTO with validation
│   │   └── response/
│   │       └── IdentifyResponse.java      # Output DTO matching spec format
│   ├── exception/
│   │   └── GlobalExceptionHandler.java    # 400/500 structured error responses
│   ├── model/
│   │   └── Contact.java                   # JPA entity with indexes
│   ├── repository/
│   │   └── ContactRepository.java         # JPQL queries for reconciliation
│   └── service/
│       └── ContactService.java            # Core business logic (4 cases)
│
├── main/resources/
│   └── application.properties             # DB config, JPA settings
│
└── test/java/com/fluxkart/bitespeed/
    ├── BitespeedApplicationTests.java     # Context load smoke test
    ├── controller/
    │   └── ContactControllerTest.java     # Web-layer tests (8 tests)
    ├── dto/
    │   ├── request/
    │   │   └── IdentifyRequestTest.java   # Validation tests (13 tests)
    │   └── response/
    │       └── IdentifyResponseTest.java  # Builder/payload tests (8 tests)
    ├── exception/
    │   └── GlobalExceptionHandlerTest.java # Error handling tests (3 tests)
    ├── model/
    │   └── ContactTest.java               # Entity tests (18 tests)
    ├── repository/
    │   └── ContactRepositoryTest.java     # Query tests (13 tests)
    └── service/
        ├── ContactServiceTest.java        # Unit tests with Mockito (10 tests)
        └── ContactServiceIntegrationTest.java # Full E2E tests (14 tests)
```

---

## Scope for Improvement

These are deliberate trade-offs made for the scope of this task. A production system would address them:

### 1. Concurrency & Race Conditions

Two simultaneous `/identify` requests with overlapping data could both create a primary, resulting in duplicates. **Fix:** database-level unique constraints + pessimistic locking or `SELECT FOR UPDATE`.

### 2. Soft-Delete Endpoint

The schema has `deletedAt` but no deletion endpoint. A `DELETE /contacts/{id}` with soft-delete semantics would complete the CRUD surface.

### 3. Pagination on Large Clusters

`findAllByPrimaryId` fetches all contacts in one query. For clusters with hundreds of entries, this should be paginated or streamed.

### 4. Email & Phone Validation

Any non-blank string is accepted. Adding `@Email` and regex for phone formats would prevent dirty data.

### 5. Audit Logging

Merge events should be written to an audit log table for debugging and compliance.

### 6. Caching

Frequent lookups for the same cluster could be cached (Redis) with invalidation on writes.

### 7. API Rate Limiting

No rate limiting is applied. A gateway-level limit per IP or API key would prevent abuse.

### 8. Metrics & Observability

Spring Boot Actuator + Micrometer + Prometheus would expose request latency, DB pool stats, and error rates.

---

## License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgements

- [BiteSpeed](https://bitespeed.co/) for the problem statement
- [Spring Boot](https://spring.io/projects/spring-boot) & [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Render.com](https://render.com/) for hosting
- [Neon.tech](https://neon.tech/) for free-tier PostgreSQL
