# Layer 1 Integration Test Summary

## Artifacts added

- `src/test/java/com/authservice/auth/AuthControllerL1Test.java`
  - New Layer 1 integration test class covering auth registration, login, refresh cookie handling, authenticated `/me` access, logout revocation, and forgot-password reset token persistence.
- `.github/integration-tests/run-layer1-tests.sh`
  - Cross-platform shell runner that verifies Docker is running and executes `mvn verify -DincludeTags=Layer1`.
- `.github/integration-tests/run-layer1-tests.ps1`
  - PowerShell runner with the same behavior for Windows environments.
- `.github/integration-tests/integration-test-plan.md`
  - Plan describing the Layer 1 strategy, coverage gaps, dependencies, and expected scenarios.

## Coverage improvements

- Added explicit Layer 1 tagging and `L1Test` naming convention
- Covered the refresh-token cookie workflow via controller requests
- Verified bearer-token-protected endpoint access (`/api/v1/auth/me`)
- Validated logout revocation behavior for refresh token reuse
- Confirmed forgot-password request flow persists a reset token in the database

## Issues identified and resolved

- Existing test coverage lacked a Layer 1-specific suite and layer-specific tagging
- The new layer test is aligned with the project’s real HTTP controller paths and the `test` profile

## Test execution status

- Test runner commands are available, but actual execution could not be completed in this environment because the `mvn` command is not installed or available on the PATH.
- Once Maven is installed, run either:
  - `bash .github/integration-tests/run-layer1-tests.sh`
  - `powershell .github/integration-tests/run-layer1-tests.ps1`

## Next steps

- Install Maven in the local environment or add a Maven wrapper to the repository
- Execute the Layer 1 runner and confirm `✅ Layer 1 integration tests PASSED`
- If tests fail, inspect application logs and source behavior as described in the integration test plan
