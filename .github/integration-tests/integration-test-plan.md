# Layer 1 Integration Test Plan

## Summary

This plan targets local Layer 1 integration coverage for the auth-service Spring Boot application. The focus is on the authentication controller and the end-to-end JWT refresh/logout workflow using the actual application stack and Testcontainers-managed PostgreSQL database.

## Current coverage

Existing tests in the project include:
- `JwtServiceTest` — unit coverage for JWT helper behavior
- `AuthControllerIntegrationTest` — earlier integration coverage for auth endpoints, but not tagged to Layer 1 and not aligned with the Layer 1 naming convention

## Coverage gaps

The current project lacks a clearly tagged Layer 1 integration suite and standardized runner artifacts. There is also a gap in coverage for:
- cookie-based refresh/revoke flows
- authenticated `/me` endpoint access
- logout revocation behavior
- password reset token persistence behavior

## Test strategy

### Layer 1 approach

- Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` with the `test` profile
- Execute tests through the web controller layer using `TestRestTemplate`
- Preserve the actual application behavior by using real persistence via Testcontainers-backed PostgreSQL
- Avoid mocks for application services and data sources
- Use tag `Layer1` and class suffix `L1Test` to isolate the layer

### Components under test

- `AuthController`
- `AuthService`
- `RefreshTokenService` / JWT refresh handling
- `SecurityConfig` / authenticated endpoint access
- `PasswordResetTokenRepository` persistence for forgot-password

## Dependencies and environment

- JDK 25
- Maven 3.9+ (Maven wrapper not present in repo)
- Docker running locally for Testcontainers
- No real Azure or cloud services required for Layer 1

## Expected scenarios

- `POST /api/v1/auth/register` returns `201 Created`, access token, refresh token, and refresh cookie
- `POST /api/v1/auth/login` returns `200 OK`, access token, refresh token, and refresh cookie
- `GET /api/v1/auth/me` returns authenticated user details when called with a bearer token
- `POST /api/v1/auth/refresh` returns a new access token when called with a refresh cookie
- `POST /api/v1/auth/logout` revokes refresh tokens for the user
- `POST /api/v1/auth/forgot-password` persists a reset token for the user

## Artifacts to generate

- `.github/integration-tests/integration-test-plan.md`
- `src/test/java/com/authservice/auth/AuthControllerL1Test.java`
- `.github/integration-tests/run-layer1-tests.sh`
- `.github/integration-tests/run-layer1-tests.ps1`
- `.github/integration-tests/integration-test-summary.md` (created after test execution)

## Validation criteria

- All Layer 1 tests execute via `mvn verify -DincludeTags=Layer1`
- Runner scripts print a clear pass/fail summary
- Tests are discoverable without extra `-Dtest` flags
- The suite uses a distinct `Layer1` tag and `L1Test` naming convention
