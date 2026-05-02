#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

scan_targets=(deploy backend/scripts docs qa)
for optional_path in .staging .staging-18080 .staging-19080; do
  if [[ -e "$optional_path" ]]; then
    scan_targets+=("$optional_path")
  fi
done

echo "checking hardcoded default database passwords"
scan_output="$(
  rg -n \
    --glob '!qa/security/verify_security_checks.sh' \
    "(DB_PASSWORD=urban_agent|password[[:space:]]+''urban_agent''|login password[[:space:]]*'urban_agent')" \
    "${scan_targets[@]}" || true
)"
if [[ -n "$scan_output" ]]; then
  echo "$scan_output"
  echo "default database password references found"
  exit 1
fi

echo "checking acceptance security data set"
python3 qa/acceptance/validate_acceptance_set.py >/dev/null

echo "running backend security integration tests"
(
  cd backend
  mvn -q \
    -Dtest=SecurityIntegrationTest,KnowledgeSecurityIntegrationTest,CitationRequirementIntegrationTest,LegalReviewIntegrationTest,RuntimeGuardIntegrationTest,ObservabilityIntegrationTest \
    test
)

echo "security checks passed"
