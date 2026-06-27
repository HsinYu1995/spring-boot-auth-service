# Testing — Auth Service

## Coverage

**84.9% line coverage** (327 / 385 lines). Enforced by JaCoCo at the `verify` phase — the build fails if coverage drops below **80%**.

```
mvn verify   # runs all tests + coverage check
```

Excluded from the threshold (infrastructure / config with no testable logic):
`AuthServiceApplication`, `auth.dto.*`, `SecurityConfig`, `OpenApiConfig`

---

## Running Tests

Unit tests have **no external dependencies** — no Docker, no database.

| Command | Scope | Docker required |
|---|---|---|
| `mvn test` | All unit tests | No |
| `mvn verify` | Unit tests + coverage check | No |
| `mvn verify -DincludeTags=Layer1` | Layer 1 integration tests | Yes |
| `mvn verify` (full) | All tests including integration | Yes |

> **Note:** Integration tests (`AuthControllerL1Test`, `AuthControllerIntegrationTest`, `JwtServiceTest`) require Testcontainers with a Docker daemon that supports API version ≥ 1.40. Run unit tests with `mvn test` if Docker is unavailable.

**Unit tests only:**
```powershell
mvn test
```

**Layer 1 integration tests** (recommended for fast feedback with Docker):
```powershell
# PowerShell script
.\.github\integration-tests\run-layer1-tests.ps1

# Or directly
mvn verify -DincludeTags=Layer1
```

**All tests + coverage enforcement:**
```powershell
mvn verify
```

---

## Test Suites

### Unit Tests (no Docker, no database)

All unit tests use `@ExtendWith(MockitoExtension.class)` — plain JUnit 5 + Mockito, no Spring context.

**`AuthServiceTest`** — all `AuthService` branches.

Covers:
- `register`: success, duplicate email → `UserAlreadyExistsException`
- `login`: valid credentials, bad credentials → `AuthException`
- `refresh`: token rotation via `RefreshTokenService`
- `logout`: delegates to `RefreshTokenService.revokeAllForUser`
- `forgotPassword`: user exists (saves token + sends email), user not found (no-op)
- `resetPassword`: success, token not found, token already used, token expired

**`RefreshTokenServiceTest`** — token rotation and reuse detection.

Covers:
- `createRefreshToken`: persists hashed token, returns raw token
- `validateAndRotate`: happy path, not-found, revoked (reuse → revoke all), expired
- `revokeAllForUser`: delegates to repository

**`AuthControllerTest`** — all 8 endpoints via standalone MockMvc (no Spring Security filter chain).

Covers:
- `POST /register` → 201 with access token
- `POST /login` → 200 with access token
- `POST /refresh` → 200 with new token; no cookie → 401
- `POST /logout` → 204 (sets authenticated user via `SecurityContextHolder`)
- `GET /me` → 200 with user info
- `POST /forgot-password` → 202
- `POST /reset-password` → 204

**`JwtAuthFilterTest`** — `OncePerRequestFilter` logic.

Covers:
- No `Authorization` header → chain continues, no auth set
- Non-`Bearer` header → chain continues, no auth set
- Valid token → `SecurityContextHolder` authentication populated
- Invalid token (signature mismatch) → chain continues, no auth set
- Token extraction exception → chain continues gracefully
- Unknown user email → chain continues, no auth set

**`RateLimitFilterTest`** — per-IP token-bucket rate limiting.

Covers:
- Non-rate-limited path → passes through
- Rate-limited path under limit → passes through
- Rate-limited path over limit → 429 Too Many Requests
- `X-Forwarded-For` header used for IP resolution
- `/forgot-password` lower capacity (5/min) enforced

**`JwtServiceUnitTest`** — RSA JWT operations without Spring Boot context.

Covers:
- Token generation returns a non-blank JWT
- Email extracted from subject claim
- Valid token passes `isTokenValid`
- Token for wrong user fails `isTokenValid`
- Tampered token fails `isTokenValid`
- `getPublicKey()` returns the loaded RSA public key

**`EmailServiceTest`** — `@Async` email dispatch.

Covers:
- Correct `From`, `To`, `Subject`, and body (with token URL) sent via `JavaMailSender`
- SMTP failure is caught and logged — does not propagate to caller

**`GlobalExceptionHandlerTest`** — all six `@ExceptionHandler` methods.

Covers: `UserAlreadyExistsException` → 409, `UserNotFoundException` → 404, `AuthException` → 401, `TokenException` → 401, `MethodArgumentNotValidException` → 400, `Exception` → 500

**`TokenCleanupServiceTest`** — scheduled stale-token purge.

Covers: `purgeStaleTokens` delegates bulk-delete to both repositories.

**`TokenEntityTest`** — entity helper methods.

Covers: `RefreshToken.isExpired()` (past / future), default `revoked=false`; `PasswordResetToken.isExpired()` (past / future), default `used=false`

**`UserEntityTest`** — `UserDetails` contract.

Covers: `getAuthorities()`, `getUsername()`, account-status flags, default `role=ROLE_USER`

**`RefreshTokenServiceTest`** — covered above.

---

### Integration Tests — Layer 1 (Docker required)

**`AuthControllerL1Test`** (`@Tag("Layer1")`) — full Spring context + Testcontainers PostgreSQL.

Covers:
- `POST /register` returns 201 with access token and HttpOnly refresh cookie
- `POST /login` returns tokens and allows access to `GET /me`
- `POST /refresh` issues a new access token from the cookie
- `POST /logout` revokes the refresh token
- `POST /forgot-password` creates a password reset record

### Integration Tests — Full (Docker required)

**`AuthControllerIntegrationTest`** — end-to-end HTTP via `TestRestTemplate`, including error paths.

Covers:
- Registration returns 201 with tokens
- Duplicate email returns 409 Conflict
- Valid login returns tokens
- Invalid credentials return 401 Unauthorized
- Token refresh with a valid token returns new tokens
- Invalid email format returns 400 Bad Request

**`JwtServiceTest`** (`@SpringBootTest`) — JWT operations with the full application context.

Covers: same cases as `JwtServiceUnitTest` but exercised through the real Spring bean lifecycle.

---

## Coverage Enforcement (JaCoCo)

JaCoCo is configured in `pom.xml`:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  ...
  <limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.80</minimum>
  </limit>
```

After running `mvn verify`, the HTML report is available at:

```
target/site/jacoco/index.html
```

---

## Test Database

Integration tests use the Testcontainers JDBC URL (Docker required):

```
jdbc:tc:postgresql:16:///authdb
```

No local PostgreSQL installation is required. Flyway runs the same migrations as production, so the schema is always in sync.

**`application-test.yml`:**
```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///authdb
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
```

---

## Test Infrastructure

**`AbstractIntegrationTest`** — base class for integration tests.

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@ActiveProfiles("test")`
- Provides a `baseUrl()` helper that resolves the random port at runtime
