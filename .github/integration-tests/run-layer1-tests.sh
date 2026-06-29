#!/bin/bash
set -euo pipefail

# --- Auto-generated Layer 1 integration test runner ---
# Project type: Maven Spring Boot
# Generated on: 2026-05-20

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker is not installed or not on PATH. Please install Docker and try again."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker is not running. Please start Docker and try again."
  exit 1
fi

cd "$(dirname "$0")/../.."

mvn verify -DincludeTags=Layer1
EXIT_CODE=$?

echo ""
echo "========================================"
if [ $EXIT_CODE -eq 0 ]; then
  echo "✅ Layer 1 integration tests PASSED"
else
  echo "❌ Layer 1 integration tests FAILED"
fi
echo "========================================"
exit $EXIT_CODE
