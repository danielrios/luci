#!/usr/bin/env bash
# ==============================================================================
# lint-no-spring-httpx.sh — Constitutional guard: no hand-written httpx to Spring
#
# Scans agent/ (excluding agent/finance_api_client/) for httpx calls that
# target the Spring Finance API. Exits non-zero if any are found.
# (FR-002b, R-6, Gap-4, T054)
#
# To add a principled exception, annotate the line with:
#   # luci-allow-spring-httpx: <reason>
# ==============================================================================
set -euo pipefail

VIOLATIONS=0

# Patterns that indicate httpx usage targeting Spring
HTTPX_PATTERN='httpx\.(AsyncClient|Client|get|post|put|delete|patch|request)'
SPRING_TARGETS='(SPRING_BASE_URL|finance-api\.luci\.app|localhost:8080)'

# Search in agent/ but exclude the generated client
while IFS= read -r file; do
    # Skip the generated client directory
    if [[ "$file" == agent/finance_api_client/* ]]; then
        continue
    fi

    # Check each matching line for Spring-targeting patterns
    while IFS=: read -r lineno line; do
        # Skip lines with the allowlist comment
        if echo "$line" | grep -q '# luci-allow-spring-httpx:'; then
            continue
        fi

        # Check if this httpx call targets Spring
        if echo "$line" | grep -qE "$SPRING_TARGETS"; then
            echo "VIOLATION: $file:$lineno: hand-written httpx to Spring detected"
            echo "  $line"
            echo "  → Use the generated client from agent/finance_api_client/ instead."
            echo ""
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
    done < <(grep -nE "$HTTPX_PATTERN" "$file" 2>/dev/null || true)
done < <(find agent/ -name '*.py' -type f 2>/dev/null || true)

if [ "$VIOLATIONS" -gt 0 ]; then
    echo "✗ Found $VIOLATIONS hand-written httpx call(s) to Spring."
    echo "  Constitution §III (Contract-First) forbids hand-written httpx to Spring."
    echo "  Use the generated client or add '# luci-allow-spring-httpx: <reason>' for exceptions."
    exit 1
fi

echo "✓ No hand-written httpx calls to Spring found."
exit 0
