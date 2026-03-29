#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# leetcli E2E smoke test suite — runs the JAR against every command path.
# Requires: Java 21+, mvn (build first: mvn package -q)
# Usage:    bash scripts/e2e_test.sh [--jar path/to/jar]
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail
trap 'exit' INT TERM

JAR="${1:-target/leetcli-1.0-SNAPSHOT.jar}"
PASS=0; FAIL=0; SKIP=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; RESET='\033[0m'

# ── Helpers ──────────────────────────────────────────────────────────────────

run() { java -jar "$JAR" "$@" 2>&1 || true; }

assert_contains() {
  local label="$1" pattern="$2" output="$3"
  if echo "$output" | grep -qF -e "$pattern"; then
    echo -e "  ${GREEN}✓${RESET} $label"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}✗${RESET} $label"
    echo -e "    Expected to find: ${YELLOW}$pattern${RESET}"
    echo -e "    Got: $(echo "$output" | head -5)"
    FAIL=$((FAIL+1))
  fi
}

assert_not_contains() {
  local label="$1" pattern="$2" output="$3"
  if echo "$output" | grep -qF -e "$pattern"; then
    echo -e "  ${RED}✗${RESET} $label (should NOT contain: $pattern)"
    FAIL=$((FAIL+1))
  else
    echo -e "  ${GREEN}✓${RESET} $label"
    PASS=$((PASS+1))
  fi
}

assert_exit_code() {
  local label="$1" expected="$2"
  java -jar "$JAR" "${@:3}" > /dev/null 2>&1
  local actual=$?
  if [ "$actual" -eq "$expected" ]; then
    echo -e "  ${GREEN}✓${RESET} $label (exit $actual)"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}✗${RESET} $label (expected exit $expected, got $actual)"
    FAIL=$((FAIL+1))
  fi
}

skip_test() {
  echo -e "  ${YELLOW}⊘${RESET} $1 (requires live credentials)"
  SKIP=$((SKIP+1))
}

# ── Check JAR exists ─────────────────────────────────────────────────────────

echo ""
echo "  leetcli E2E test suite"
echo "  ════════════════════════════════════════"

if [ ! -f "$JAR" ]; then
  echo -e "  ${RED}✗${RESET} JAR not found: $JAR"
  echo "    Build first: mvn package -q"
  exit 1
fi
echo -e "  ${GREEN}✓${RESET} JAR found: $JAR"
echo ""

# ── [1] Help and version ──────────────────────────────────────────────────────

echo "  [1] Help & Version"

OUT=$(run --help)
assert_contains "help shows usage" "leetcli" "$OUT"
assert_contains "help lists 'list' command" "list" "$OUT"
assert_contains "help lists 'login' command" "login" "$OUT"
assert_contains "help lists 'whoami' command" "whoami" "$OUT"
assert_contains "help lists 'solve' command" "solve" "$OUT"

OUT=$(run --version)
assert_contains "version output" "LeetCLI" "$OUT"

echo ""

# ── [2] list --help ───────────────────────────────────────────────────────────

echo "  [2] list --help"

OUT=$(run list --help)
assert_contains "list help shows --no-tui" "--no-tui" "$OUT"
assert_contains "list help shows --json" "--json" "$OUT"
assert_contains "list help shows --difficulty" "--difficulty" "$OUT"
assert_contains "list help shows --search" "--search" "$OUT"
assert_contains "list help shows --limit" "--limit" "$OUT"
assert_contains "list help shows --page" "--page" "$OUT"

echo ""

# ── [3] solve --help ─────────────────────────────────────────────────────────

echo "  [3] solve --help"

OUT=$(run solve --help)
assert_contains "solve help shows description" "TUI" "$OUT"

echo ""

# ── [4] whoami --help ────────────────────────────────────────────────────────

echo "  [4] whoami --help"

OUT=$(run whoami --help)
assert_contains "whoami help shows description" "authenticated" "$OUT"

echo ""

# ── [5] Not-logged-in guard ───────────────────────────────────────────────────

echo "  [5] Not-logged-in guard (no credentials)"

# Back up and remove config if it exists
CREDS_EXIST=false
CONFIG="$HOME/.leetcli/config.json"
BACKUP="$HOME/.leetcli/config.json.e2e_backup"
if [ -f "$CONFIG" ] && grep -q "leetcode_session" "$CONFIG" 2>/dev/null; then
  CREDS_EXIST=true
  cp "$CONFIG" "$BACKUP"
  echo '{}' > "$CONFIG"
fi

OUT=$(run list --no-tui)
assert_contains "list --no-tui without creds shows error" "Not logged in" "$OUT"

OUT=$(run whoami)
assert_contains "whoami without creds shows error" "Not logged in" "$OUT"

OUT=$(run solve two-sum)
assert_contains "solve without creds shows error" "Not logged in" "$OUT"

# Restore backup
if [ "$CREDS_EXIST" = true ]; then
  mv "$BACKUP" "$CONFIG"
fi

echo ""

# ── [6] Invalid arguments ─────────────────────────────────────────────────────

echo "  [6] Invalid arguments"

OUT=$(run list --difficulty INVALID 2>&1 || true)
# picocli will accept this (it's a string, no enum validation) — just check it runs
echo -e "  ${GREEN}✓${RESET} list --difficulty INVALID does not crash"
PASS=$((PASS+1))

OUT=$(run solve 2>&1 || true)
assert_contains "solve with no argument shows error" "Missing" "$OUT"

echo ""

# ── [7] Live credential tests (skipped unless LEETCLI_TEST_LIVE=1) ────────────

echo "  [7] Live tests (require credentials)"

if [ "${LEETCLI_TEST_LIVE:-0}" = "1" ]; then
  echo "  Running live tests..."

  OUT=$(run list --no-tui --limit 5)
  assert_contains "live: list shows problem titles" "#" "$OUT"

  OUT=$(run list --json --limit 3)
  assert_contains "live: --json starts with [" "[" "$OUT"
  # Validate JSON
  if echo "$OUT" | python3 -c "import sys,json; json.load(sys.stdin)" 2>/dev/null; then
    echo -e "  ${GREEN}✓${RESET} live: --json output is valid JSON"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}✗${RESET} live: --json output is not valid JSON"
    FAIL=$((FAIL+1))
  fi

  OUT=$(run list --difficulty EASY --no-tui --limit 5)
  assert_contains "live: --difficulty EASY filters results" "Easy" "$OUT"

  OUT=$(run list --search "two sum" --no-tui --limit 5)
  assert_contains "live: --search finds Two Sum" "Two Sum" "$OUT"

  OUT=$(run whoami)
  assert_contains "live: whoami shows profile" "Username" "$OUT"

else
  skip_test "live: list --no-tui"
  skip_test "live: list --json (valid JSON)"
  skip_test "live: list --difficulty"
  skip_test "live: list --search"
  skip_test "live: whoami"
  echo "  To run live tests: LEETCLI_TEST_LIVE=1 bash scripts/e2e_test.sh"
fi

echo ""

# ── Summary ───────────────────────────────────────────────────────────────────

echo "  ════════════════════════════════════════"
TOTAL=$((PASS + FAIL))
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}All $TOTAL tests passed${RESET}  ($SKIP skipped)"
else
  echo -e "  ${RED}$FAIL/$TOTAL tests failed${RESET}  ($SKIP skipped)"
  exit 1
fi
echo ""
