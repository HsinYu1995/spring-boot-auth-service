# System Design — Auth Service

## Overview

This project is a stateless authentication microservice built with Spring Boot 3.3, Java 21, and PostgreSQL. It provides JWT-based registration, login, token refresh, logout, and password reset flows. A companion React/Vite frontend communicates with it over REST.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Domain Model](#domain-model)
5. [API Reference](#api-reference)
6. [Security Design](#security-design)
7. [Database Schema](#database-schema)
8. [Service Layer](#service-layer)
9. [Rate Limiting](#rate-limiting)
10. [Configuration & Environments](#configuration--environments)
11. [Docker & Deployment](#docker--deployment)
12. [Testing](TESTING.md)
13. [Error Handling](#error-handling)

---

## Architecture

```
┌──────────────────────────────────────┐
│       Client (React / Vite)          │
│   http://localhost:5173              │
└───────────────┬──────────────────────┘
                │ HTTP / REST + HttpOnly Cookie
                ▼
┌──────────────────────────────────────┐
│       AuthController                 │
│       /api/v1/auth/*                 │
└───────────────┬──────────────────────┘
                │
┌───────────────▼──────────────────────┐
│     Spring Security Filter Chain     │
│  ┌─────────────────────────────────┐ │
│  │ JwtAuthFilter                   │ │
│  │ (Bearer token → SecurityContext)│ │
│  └─────────────────────────────────┘ │
│  CSRF off · Stateless session        │
│  CORS from app.cors.allowed-origins  │
└───────────────┬──────────────────────┘
                │
┌───────────────▼──────────────────────┐
│           Service Layer              │
│  AuthService · JwtService            │
│  RefreshTokenService · EmailService  │
└───────────────┬──────────────────────┘
                │
┌───────────────▼──────────────────────┐
│         Repository Layer             │
│  UserRepository                      │
│  RefreshTokenRepository              │
│  PasswordResetTokenRepository        │
└───────────────┬──────────────────────┘
                │
┌───────────────▼──────────────────────┐
│    PostgreSQL 16 (Flyway managed)    │
│  users · refresh_tokens              │
│  password_reset_tokens               │
└──────────────────────────────────────┘

External services
  └── SMTP (async password-reset email)
```

**Key design decisions:**

- **Stateless** — no server-side sessions; every request is authenticated via a signed JWT.
- **Token rotation** — each refresh call issues a new refresh token and invalidates the old one. Reuse detection revokes all tokens for the user.
- **HttpOnly cookies** carry the refresh token so JavaScript cannot read it; access tokens are short-lived (15 min) and returned in the JSON body.
- **Layered architecture** — Controller → Service → Repository. No business logic in controllers or entities.

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.0 |
| Security | Spring Security | 6.x |
| JWT | JJWT | 0.12.6 |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | 10.x |
| Build | Maven | 3.9.7 |
| Containerization | Docker + Docker Compose | — |
| API Docs | SpringDoc OpenAPI | 2.5.0 |
| Email | Spring Mail (SMTP) | — |
| Rate Limiting | Bucket4j (`com.bucket4j`) | 8.10.1 |
| Testing | JUnit 5, Mockito, Testcontainers | 1.20.6 |

---

## Project Structure

```
src/
└── main/
    └── java/com/authservice/
        ├── AuthServiceApplication.java     # Entry point
        ├── auth/
        │   ├── AuthController.java         # REST endpoints
        │   ├── AuthService.java            # Core auth logic
        │   ├── EmailService.java           # Async email
        │   └── dto/                        # Request / response records
        ├── token/
        │   ├── JwtService.java             # JWT sign / validate (RSA)
        │   ├── JwtProperties.java          # Config binding
        │   ├── RefreshToken.java           # Entity
        │   ├── RefreshTokenRepository.java
        │   ├── RefreshTokenService.java
        │   ├── PasswordResetToken.java     # Entity
        │   ├── PasswordResetTokenRepository.java
        │   └── TokenCleanupService.java    # Scheduled stale-token purge (3am daily)
        ├── user/
        │   ├── User.java                   # Entity + UserDetails
        │   ├── Role.java                   # Enum (ROLE_USER, ROLE_ADMIN)
        │   └── UserRepository.java
        ├── config/
        │   ├── SecurityConfig.java         # Filter chain + beans
        │   ├── JwtAuthFilter.java          # OncePerRequestFilter
        │   ├── RateLimitFilter.java        # Per-IP rate limiting (Bucket4j)
        │   ├── RateLimitConfig.java        # Servlet-level filter registration
        │   └── OpenApiConfig.java          # Swagger security scheme
        └── exception/
            ├── GlobalExceptionHandler.java
            ├── AuthException.java
            ├── TokenException.java
            ├── UserNotFoundException.java
            ├── UserAlreadyExistsException.java
            └── ErrorResponse.java

src/main/resources/
    ├── application.yml                     # Base config
    ├── application-dev.yml
    ├── application-prod.yml
    ├── db/migration/
    │   ├── V1__create_users_table.sql
    │   ├── V2__create_refresh_tokens_table.sql
    │   └── V3__create_password_reset_tokens_table.sql
    └── keys/
        ├── private.pem                     # RSA private key (dev only)
        └── public.pem                      # RSA public key (dev only)
```

---

## Domain Model

### User

| Column | Type | Constraints |
|---|---|---|
| id | UUID | PK, auto-generated |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL, BCrypt hashed |
| role | VARCHAR(50) | NOT NULL, DEFAULT `ROLE_USER` |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() |

Implements Spring Security's `UserDetails`. `getUsername()` returns the email address.

### RefreshToken

| Column | Type | Constraints |
|---|---|---|
| id | UUID | PK |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL — SHA-256 of raw token |
| user_id | UUID | FK → users.id, CASCADE DELETE |
| expires_at | TIMESTAMP | NOT NULL |
| revoked | BOOLEAN | DEFAULT false |
| created_at | TIMESTAMP | DEFAULT NOW() |

Raw token is never stored. Only the SHA-256 hash is persisted. Lifetime: 7 days.

### PasswordResetToken

| Column | Type | Constraints |
|---|---|---|
| id | UUID | PK |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL — SHA-256 |
| user_id | UUID | FK → users.id, CASCADE DELETE |
| expires_at | TIMESTAMP | NOT NULL (1 hour from creation) |
| used | BOOLEAN | DEFAULT false — single-use enforcement |
| created_at | TIMESTAMP | DEFAULT NOW() |

---

## API Reference

Base path: `/api/v1/auth`

Interactive docs available at `/swagger-ui.html` (dev) and `/api-docs`.

### Endpoints

| Method | Path | Auth | Success | Description |
|---|---|---|---|---|
| `POST` | `/register` | None | 201 | Create account; sets HttpOnly refresh cookie |
| `POST` | `/login` | None | 200 | Authenticate; sets HttpOnly refresh cookie |
| `POST` | `/refresh` | Cookie | 200 | Rotate refresh token; returns new access token |
| `POST` | `/logout` | Bearer JWT | 204 | Revoke all refresh tokens; clears cookie |
| `GET` | `/me` | Bearer JWT | 200 | Return current user profile |
| `POST` | `/forgot-password` | None | 202 | Send password-reset email |
| `POST` | `/reset-password` | None | 204 | Set new password using emailed token |

### DTOs

**RegisterRequest / LoginRequest**
```json
{ "email": "user@example.com", "password": "secret123" }
```

**AuthResponse** (access token in body)
```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**ForgotPasswordRequest**
```json
{ "email": "user@example.com" }
```

**ResetPasswordRequest**
```json
{ "token": "<raw-reset-token>", "newPassword": "newSecret123" }
```

### Refresh Cookie

```
Set-Cookie: refresh_token=<raw-UUID>; HttpOnly; SameSite=Strict;
            Path=/api/v1/auth; Max-Age=604800
```

In production the `Secure` attribute is added.

---

## Security Design

### JWT

- **Algorithm:** RS256 (RSA + SHA-256)
- **Keys:** PKCS8 private key / X.509 public key loaded from PEM files
- **Access token lifetime:** 15 minutes
- **Claims:** `sub` (email), `role`, `iat`, `exp`
- **Validation:** signature + expiration + subject match in `JwtAuthFilter`

The public key is exposed via `JwtService.getPublicKey()` to allow external services to verify tokens without sharing the private key.

### Refresh Token Rotation

```
Client → POST /refresh (cookie)
            ↓
  1. SHA-256 hash(rawToken) → look up in DB
  2. Check: not revoked, not expired
  3. If reused (revoked): revoke ALL tokens for user (breach assumed)
  4. Mark old token revoked
  5. Issue new raw token + store its hash
  6. Return new access token + set new cookie
```

### Password Reset Flow

```
POST /forgot-password
  → generate random UUID token
  → SHA-256 hash → store in DB (1h TTL, used=false)
  → async email with link: {baseUrl}/api/v1/auth/reset-password?token=<raw>

POST /reset-password
  → SHA-256 hash(rawToken) → find record
  → verify: not used, not expired
  → mark used=true
  → BCrypt new password → save user
  → revoke ALL refresh tokens for user
```

### Defence Layers

| Concern | Mechanism |
|---|---|
| Password storage | BCrypt (Spring Security default) |
| Token storage | SHA-256 hash only — raw token never persisted |
| XSS (refresh token) | HttpOnly cookie |
| CSRF | SameSite=Strict cookie + stateless JWT (no session) |
| Token replay | Rotation + reuse detection |
| Enumeration | `/forgot-password` always returns 202 regardless of email existence |
| Access control | All non-public endpoints require valid Bearer JWT |
| Primary keys | UUID v4 — non-sequential, non-guessable |
| Cascading deletes | Deleting a user removes all their tokens |

---

## Database Schema

Managed by Flyway. Migrations run automatically on startup.

```sql
-- V1
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);

-- V2
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- V3
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
```

`hibernate.ddl-auto` is set to `validate` in all environments — Flyway owns schema changes.

---

## Service Layer

### AuthService

Orchestrates all authentication flows. All mutating operations are `@Transactional`.

| Method | Description |
|---|---|
| `register(RegisterRequest)` | Validate uniqueness, BCrypt password, persist user, issue token pair |
| `login(LoginRequest)` | Delegate to `AuthenticationManager`, issue token pair |
| `refresh(rawToken)` | Delegate to `RefreshTokenService`, issue new access token |
| `logout(user)` | Revoke all refresh tokens via `RefreshTokenService` |
| `forgotPassword(request)` | Generate reset token, async email |
| `resetPassword(request)` | Validate reset token, update password, revoke sessions |

### JwtService

Stateless JWT operations using the RSA key pair loaded at startup.

| Method | Description |
|---|---|
| `generateAccessToken(User)` | Signs JWT with private key |
| `extractEmail(token)` | Parses subject claim |
| `isTokenValid(token, User)` | Checks signature, expiry, and subject |
| `getPublicKey()` | Exposes RSA public key for external verifiers |

### RefreshTokenService

Manages the refresh token lifecycle.

| Method | Description |
|---|---|
| `createRefreshToken(User)` | Generate UUID, hash it, persist with 7-day TTL |
| `validateAndRotate(rawToken)` | Reuse-detection + rotation |
| `revokeAllForUser(User)` | Bulk revocation (logout / breach response) |

### EmailService (`@Async`)

Sends password-reset emails without blocking the request thread. Uses `JavaMailSender` configured via SMTP properties.

### TokenCleanupService (`@Scheduled`)

Runs daily at 3am via a Spring scheduler. Issues two bulk-delete queries in a single transaction:

- `RefreshToken` — deletes rows where `expiresAt < now OR revoked = true`
- `PasswordResetToken` — deletes rows where `expiresAt < now OR used = true`

This prevents the token tables from growing unboundedly as users log in and rotate tokens over time.

---

## Rate Limiting

Implemented as a servlet-level `OncePerRequestFilter` (registered at `HIGHEST_PRECEDENCE`, before Spring Security). Uses **Bucket4j** token-bucket algorithm with per-IP, per-endpoint counters stored in a `ConcurrentHashMap`.

| Endpoint | Limit |
|---|---|
| `POST /login` | 10 requests / minute / IP |
| `POST /register` | 20 requests / minute / IP |
| `POST /forgot-password` | 5 requests / minute / IP |

Requests that exceed the limit receive:
- HTTP `429 Too Many Requests`
- `Retry-After` header (seconds until the bucket refills)
- JSON body: `{"status":429,"error":"Too Many Requests","message":"..."}`

Client IP is resolved from `X-Forwarded-For` when present (for reverse-proxied deployments), falling back to `remoteAddr`.

---

## Configuration & Environments

### Profiles

| Profile | Purpose |
|---|---|
| `dev` | Local development; verbose logging; CORS open to localhost ports |
| `prod` | Production; WARN-level logging; secrets from env vars |
| `test` | Integration tests; Testcontainers PostgreSQL via JDBC URL |

### Key Properties (`app.*`)

```yaml
app:
  jwt:
    private-key: classpath:keys/private.pem   # or ${JWT_PRIVATE_KEY_PATH}
    public-key:  classpath:keys/public.pem    # or ${JWT_PUBLIC_KEY_PATH}
    access-token-expiration:  900000          # 15 min (ms)
    refresh-token-expiration: 604800000       # 7 days (ms)
  cors:
    allowed-origins: http://localhost:5173,http://localhost:3000
  mail:
    from: noreply@example.com
  base-url: http://localhost:5173             # Used in reset-password link
```

### Connection Pool (HikariCP)

| Property | Dev | Prod |
|---|---|---|
| `maximum-pool-size` | 10 | 20 |
| `minimum-idle` | 2 | 5 |
| `connection-timeout` | 3000 ms | 3000 ms |
| `idle-timeout` | 300000 ms (5 min) | 600000 ms (10 min) |
| `max-lifetime` | — | 1800000 ms (30 min) |

Requests that cannot acquire a connection within `connection-timeout` fail fast with an exception rather than queuing indefinitely.

### Required Environment Variables (production)

| Variable | Description |
|---|---|
| `DB_URL` | JDBC connection string |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_PRIVATE_KEY_PATH` | Path to RSA private key PEM |
| `JWT_PUBLIC_KEY_PATH` | Path to RSA public key PEM |
| `CORS_ALLOWED_ORIGIN` | Single allowed CORS origin |
| `MAIL_HOST` | SMTP host |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `MAIL_FROM` | Sender address |
| `APP_BASE_URL` | Frontend base URL (for reset links) |

---

## Docker & Deployment

### Images

**Auth service** — multi-stage Dockerfile:
1. `maven:3.9.7-eclipse-temurin-21-alpine` — builds the fat JAR
2. `eclipse-temurin:21-jre-alpine` — minimal runtime; runs as non-root `appuser`

### Compose Files

| File | Purpose |
|---|---|
| `docker-compose.yml` | Production: auth-service + postgres |
| `docker-compose.dev.yml` | Development: auth-service + postgres + React frontend (hot reload) |
| `docker-compose.prod.yml` | Full production: adds nginx-fronted React build |

### Port Map

| Service | Host Port | Container Port |
|---|---|---|
| auth-service | 8080 | 8080 |
| postgres | 5433 | 5432 |
| frontend (dev) | 5173 | 5173 |
| frontend (prod) | 80 | 80 |

### RSA Keys (production)

JWT signing keys are mounted as Docker secrets at `/run/secrets/` and referenced via `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH`. They are never baked into the image.

---

## Testing

See [TESTING.md](TESTING.md) for how to run tests, suite descriptions, and test infrastructure details.

---

## Error Handling

All errors are returned as a consistent JSON envelope:

```json
{
  "timestamp": "2026-06-07T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Email already in use",
  "path": "/api/v1/auth/register"
}
```

| Exception | HTTP Status |
|---|---|
| `UserAlreadyExistsException` | 409 Conflict |
| `UserNotFoundException` | 404 Not Found |
| `AuthException` | 401 Unauthorized |
| `TokenException` | 401 Unauthorized |
| `ResponseStatusException` | status from exception (e.g. 401 for missing refresh cookie) |
| `MethodArgumentNotValidException` | 400 Bad Request |
| Unhandled `Exception` | 500 Internal Server Error |

Validation errors on `400` responses include a field-level `message` describing which constraint failed.
