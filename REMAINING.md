# Remaining Work & Improvements

> A structured list of what could be done next to make this service production-ready.
> Items are grouped by priority and category.

---

## Priority 1 — Must-Have for Production

### 1. Concurrency / Race Condition Handling

**Problem:** Two simultaneous `/identify` requests with overlapping email/phone can both see "no match" and each create a new primary, producing duplicates.

**Fix:**

- Add a `SELECT ... FOR UPDATE` (pessimistic lock) on the cluster resolution query.
- Alternatively, add a unique composite index on `(email, phone_number)` with a partial filter on `deleted_at IS NULL` and handle constraint violations with retry logic.

**Files affected:** `ContactService.java`, `ContactRepository.java`

---

### 2. Input Validation — Email & Phone Format

**Problem:** Any non-blank string is accepted. `"asdf"` is treated as a valid email; `"x"` as a valid phone.

**Fix:**

- Add `@Email` annotation on `IdentifyRequest.email`.
- Add `@Pattern(regexp = "^\\+?[0-9\\-\\s]{6,20}$")` on `IdentifyRequest.phoneNumber`.
- Consider normalising phone to E.164 before storage.

**Files affected:** `IdentifyRequest.java`

---

### 3. Database Migrations (Flyway or Liquibase)

**Problem:** Schema is managed by `ddl-auto=update`, which is unsafe for production (it never drops columns, can't handle renames, and can't be audited).

**Fix:**

- Add Flyway dependency and create versioned SQL migration scripts under `src/main/resources/db/migration/`.
- Set `spring.jpa.hibernate.ddl-auto=validate` in production to catch schema drift.

**Files affected:** `pom.xml`, new `db/migration/*.sql` files, `application.properties`

---

### 4. CI/CD Pipeline

**Problem:** No automated build/test pipeline. Deployments are manual.

**Fix:**

- Add GitHub Actions workflow (`.github/workflows/ci.yml`) that:
  - Runs `mvn test` on every push/PR
  - Builds the JAR on the `main` branch
  - (Optional) deploys to Render via webhook on success

---

## Priority 2 — Should-Have

### 5. Docker Containerisation

**Problem:** The app runs directly on the host. No container image for consistent deployments.

**Fix:**

- Add a multi-stage `Dockerfile`:

  ```dockerfile
  FROM eclipse-temurin:21-jdk AS build
  COPY . .
  RUN ./mvnw clean package -DskipTests

  FROM eclipse-temurin:21-jre
  COPY --from=build target/bitespeed-0.0.1-SNAPSHOT.jar app.jar
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

- Add `docker-compose.yml` with PostgreSQL for local dev.

---

### 6. Soft-Delete Endpoint

**Problem:** The `deletedAt` column exists but is never set through any API endpoint.

**Fix:**

- Add `DELETE /contacts/{id}` that sets `deleted_at = NOW()`.
- Re-link any secondaries linked to the deleted contact to the cluster's surviving primary.
- Add corresponding tests.

**Files affected:** `ContactController.java`, `ContactService.java`, `ContactRepository.java`

---

### 7. Observability — Metrics & Health

**Problem:** No observability beyond the `/health` endpoint returning `"OK"`.

**Fix:**

- Add `spring-boot-starter-actuator` dependency.
- Expose `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`.
- Add Micrometer counters for: contacts created, merges performed, total requests.
- Wire up to a Prometheus + Grafana stack in production.

**Files affected:** `pom.xml`, `application.properties`

---

### 8. Structured Logging

**Problem:** The app uses `@Slf4j` but logs are unstructured plain text. Hard to parse in log aggregation tools.

**Fix:**

- Add `logstash-logback-encoder` for JSON-structured logs.
- Include correlation ID (request trace) in each log line.
- Add MDC context with email/phone (redacted) for debugging.

**Files affected:** `pom.xml`, `logback-spring.xml` (new)

---

### 9. API Rate Limiting

**Problem:** No throttling. A single client can flood the service.

**Fix:**

- Add `bucket4j-spring-boot-starter` or a gateway-level rate limiter (e.g. Nginx, Cloudflare).
- Limit to ~100 requests/minute per IP for the free tier.

---

### 10. Pagination on Cluster Queries

**Problem:** `findAllByPrimaryId` returns all contacts in one query. A cluster with thousands of entries would cause memory pressure.

**Fix:**

- Return paginated results with `Pageable`.
- Or stream results with `Stream<Contact>` and `@QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "50"))`.

**Files affected:** `ContactRepository.java`, `ContactService.java`

---

## Priority 3 — Nice-to-Have

### 11. Caching Layer (Redis)

Cache cluster lookups in Redis. Invalidate on any write to that cluster. Useful if read:write ratio is high.

### 12. Audit Log Table

Create a `contact_event` table recording every merge, link, and create event with before/after state. Useful for debugging and compliance.

### 13. OpenAPI / Swagger Documentation

Add `springdoc-openapi-starter-webmvc-ui` to auto-generate interactive API docs at `/swagger-ui.html`.

### 14. API Versioning

Prefix routes with `/api/v1/identify` to allow breaking changes in future versions without affecting existing clients.

### 15. Test Coverage Reporting

Integrate JaCoCo Maven plugin to generate code coverage reports. Set a minimum threshold (e.g. 80% line coverage) and fail the build if it drops below.

### 16. Profile-based Configuration

Separate `application-dev.properties`, `application-prod.properties`, and `application-test.properties` for environment-specific config instead of environment variable fallbacks.

### 17. Idempotency Key

Accept an `X-Idempotency-Key` header. If the same key is seen twice, return the cached response instead of processing again. Prevents duplicate contact creation from client retries.

---

## Summary

| Priority | Item | Effort |
|----------|------|--------|
| P1 | Concurrency / race conditions | Medium |
| P1 | Email & phone validation | Low |
| P1 | Database migrations (Flyway) | Medium |
| P1 | CI/CD pipeline | Low–Medium |
| P2 | Docker containerisation | Low |
| P2 | Soft-delete endpoint | Medium |
| P2 | Observability (Actuator + Prometheus) | Low |
| P2 | Structured logging | Low |
| P2 | API rate limiting | Medium |
| P2 | Pagination on large clusters | Low |
| P3 | Redis caching | Medium |
| P3 | Audit log table | Medium |
| P3 | OpenAPI / Swagger docs | Low |
| P3 | API versioning | Low |
| P3 | Test coverage reporting (JaCoCo) | Low |
| P3 | Profile-based config | Low |
| P3 | Idempotency key | Medium |
