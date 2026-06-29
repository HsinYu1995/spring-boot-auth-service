# Security Policy

## Supported Versions

| Version       | Supported          |
|---------------|--------------------|
| Spring Boot 3.3.x | :white_check_mark: |
| Spring Boot < 3.3  | :x:                |

Only the latest release branch receives security patches. Update to the current version before reporting vulnerabilities.

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report vulnerabilities privately by emailing: **hsinyulai18@gmail.com**

Include in your report:
- A clear description of the vulnerability
- Steps to reproduce the issue
- Affected component(s) (e.g., JWT handling, password reset, CORS)
- Potential impact assessment
- Any suggested mitigations (optional)

You will receive an acknowledgement within **48 hours** and a status update within **7 days**. Critical vulnerabilities will be patched on a best-effort basis as quickly as possible.

Please do not disclose publicly until a fix has been released or 90 days have passed, whichever comes first.

---

## Security Architecture

### Authentication & Authorization

- **JWT-based stateless authentication** using [JJWT 0.12.6](https://github.com/jwtk/jjwt)
- Short-lived **access tokens** and rotating **refresh tokens**
- Refresh tokens stored in `HttpOnly`, `SameSite=Strict` cookies to prevent XSS and CSRF attacks
- Role-based access control (RBAC) with Spring Security — default role: `ROLE_USER`
- All sessions are stateless (`SessionCreationPolicy.STATELESS`)

### Password Security

- Passwords hashed with **BCrypt** (Spring Security default cost factor)
- Plain-text passwords are never stored or logged
- Password reset via time-limited, single-use email tokens

### Transport Security

- All production traffic should be served over **HTTPS/TLS**
- Strict CORS policy configured — only whitelisted origins permitted

### Database Security

- **PostgreSQL** with schema managed by **Flyway** migrations
- Users identified by **UUID primary keys** (non-sequential, non-guessable)
- Parameterized queries via Spring Data JPA — no raw SQL string interpolation

### Email

- Password reset emails sent asynchronously via Spring Mail
- Reset links are time-limited and single-use

### Observability

- Spring Actuator endpoints are exposed for health monitoring — restrict `/actuator` access to internal networks or authenticated roles in production

---

## Production Hardening Checklist

Before deploying to production, verify the following:

- [ ] RSA key pair (`JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH`) is generated fresh for production and stored in a secrets manager or Docker secret — never committed to source control
- [ ] Access token TTL is short (e.g., 15 minutes); refresh token TTL is reasonable (e.g., 7 days)
- [ ] HTTPS/TLS is enforced; HTTP redirects to HTTPS
- [ ] CORS `allowedOrigins` lists only known production domains
- [ ] `/actuator` endpoints are restricted (firewall or Spring Security configuration)
- [ ] Database credentials are rotated and not hard-coded
- [ ] Flyway migrations have been reviewed before each deployment
- [ ] Spring Boot and all dependencies are up-to-date (run `mvn versions:display-dependency-updates`)
- [ ] Application logs do not emit tokens, passwords, or PII

---

## Dependency Management

This project uses Maven. To check for known vulnerabilities in dependencies:

```bash
mvn dependency-check:check
```

Or audit using the [OWASP Dependency-Check plugin](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/).

Dependencies are pinned in [pom.xml](pom.xml). Keep Spring Boot and the JJWT library up-to-date as both receive frequent security patches.

---

## Known Limitations

- In-process rate limiting (`RateLimitFilter`) uses an in-memory `ConcurrentHashMap`. Limits are per-JVM instance — horizontal scaling requires an external rate-limit store (e.g., Redis + Bucket4j distributed backend) to share counters across nodes.
- Refresh token revocation is session-scoped. Implement a token revocation list (e.g., Redis blocklist) if immediate logout across all sessions is required.

---

## Security Advisories

| Date | CVE / Issue | Component | Resolution |
|------|-------------|-----------|------------|
| —    | —           | —         | —          |

This table will be updated as advisories are issued.
