#!/usr/bin/env bash
set -euo pipefail

# ==========================================================================
# GLB Model Generation Test Suite
#
# End-to-end tests for the full pipeline:
#   source GLB → validate → delight → validate → LEGO LDR → verify output
#
# Requires: blender (on PATH), python3, java, mvn
#
# Usage:
#   scripts/test_glb_pipeline.sh                    # test all models
#   scripts/test_glb_pipeline.sh labrador_dog.glb   # test one model
#   scripts/test_glb_pipeline.sh --quick             # skip delight, test LEGO only
# ==========================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS="$ROOT/scripts"
MODELS_DIR="$ROOT/models"
OUT_DIR="$ROOT/output/runs/glb_tests"
RESOLUTION=50

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
SKIP_COUNT=0

pass() { ((PASS_COUNT++)); echo -e "  ${GREEN}PASS${NC}  $1"; }
fail() { ((FAIL_COUNT++)); echo -e "  ${RED}FAIL${NC}  $1"; }
warn() { ((WARN_COUNT++)); echo -e "  ${YELLOW}WARN${NC}  $1"; }
skip() { ((SKIP_COUNT++)); echo -e "  ${CYAN}SKIP${NC}  $1"; }

usage() {
  cat <<USAGE
Usage: test_glb_pipeline.sh [model.glb | --quick | --all | --help]

Runs end-to-end GLB pipeline tests:
  1. Validate source GLB (catch artifact geometry)
  2. Delight via Blender (retinex mode + strip artifacts)
  3. Validate cleaned GLB (strict)
  4. Generate LEGO LDR (multiple color algorithms)
  5. Verify LDR output (non-empty, valid structure)

Options:
  model.glb     Test specific model only
  --quick       Skip Blender delight, test LEGO pipeline only
  --all         Test all .glb files in models/
  --help        Show this message

Output directory: output/runs/glb_tests/
USAGE
}

# Parse args
QUICK=false
SPECIFIC_MODEL=""

for arg in "$@"; do
  case "$arg" in
    --help|-h) usage; exit 0 ;;
    --quick) QUICK=true ;;
    --all) SPECIFIC_MODEL="" ;;
    *.glb) SPECIFIC_MODEL="$arg" ;;
    *) echo "Unknown argument: $arg"; usage; exit 1 ;;
  esac
done

# Check prerequisites
check_prereqs() {
  local ok=true
  echo -e "\n${CYAN}=== Prerequisites ===${NC}"

  if command -v blender &>/dev/null; then
    pass "Blender: $(blender --version 2>/dev/null | head -1)"
  elif $QUICK; then
    skip "Blender (--quick mode, not needed)"
  else
    fail "Blender not found on PATH"
    ok=false
  fi

  if command -v python3 &>/dev/null; then
    pass "Python3: $(python3 --version 2>&1)"
  else
    fail "python3 not found"
    ok=false
  fi

  if command -v java &>/dev/null; then
    pass "Java: $(java -version 2>&1 | head -1)"
  else
    fail "Java not found"
    ok=false
  fi

  if [[ -f "$SCRIPTS/validate_glb.py" ]]; then
    pass "validate_glb.py present"
  else
    fail "validate_glb.py missing"
    ok=false
  fi

  if [[ -f "$SCRIPTS/delight.py" ]]; then
    pass "delight.py present"
  else
    fail "delight.py missing"
    ok=false
  fi

  $ok || { echo -e "\n${RED}Prerequisites failed. Aborting.${NC}"; exit 1; }
}

# Build the project
build_project() {
  echo -e "\n${CYAN}=== Building project ===${NC}"
  if (cd "$ROOT" && mvn -q -DskipTests package 2>&1); then
    pass "Maven build succeeded"
  else
    fail "Maven build failed"
    exit 1
  fi
}

# Test a single GLB model through the pipeline
test_model() {
  local glb_file="$1"
  local model_name
  model_name="$(basename "$glb_file" .glb)"
  local model_dir="$OUT_DIR/$model_name"
  mkdir -p "$model_dir"

  echo -e "\n${CYAN}========================================${NC}"
  echo -e "${CYAN}  Model: $model_name${NC}"
  echo -e "${CYAN}========================================${NC}"

  # --- Step 1: Validate source GLB ---
  echo -e "\n${CYAN}--- Step 1: Validate source GLB ---${NC}"
  local src_val_log="$model_dir/source_validation.log"
  if python3 "$SCRIPTS/validate_glb.py" "$glb_file" > "$src_val_log" 2>&1; then
    pass "Source GLB valid (no artifacts)"
  else
    # Source model having issues is expected — that's why we delight it
    warn "Source GLB has known issues (will be cleaned)"
  fi
  # Count specific issues (only count "  FAIL  " lines, not the summary)
  local src_fails
  src_fails=$(grep -c "^  FAIL" "$src_val_log" 2>/dev/null || true)
  if [[ "$src_fails" -gt 0 ]]; then
    echo "    Source issues detected: $src_fails (see $src_val_log)"
    grep "^  FAIL" "$src_val_log" | sed 's/^/    /'
  fi

  # --- Step 2: Delight via Blender ---
  local delit_glb="$model_dir/${model_name}_delit.glb"
  if $QUICK; then
    echo -e "\n${CYAN}--- Step 2: Delight (SKIPPED --quick) ---${NC}"
    skip "Blender delight (--quick mode)"
    # Use source GLB directly
    delit_glb="$glb_file"
  else
    echo -e "\n${CYAN}--- Step 2: Delight via Blender ---${NC}"
    local delight_log="$model_dir/delight.log"
    if blender -b -P "$SCRIPTS/delight.py" -- \
        "$glb_file" "$delit_glb" \
        --mode retinex --strip-artifacts \
        > "$delight_log" 2>&1; then
      pass "Blender delight completed"

      # Check what was stripped
      local stripped
      stripped=$(grep -c "object \|material \|removed " "$delight_log" 2>/dev/null || true)
      if [[ "$stripped" -gt 0 ]]; then
        echo "    Artifacts stripped: $stripped"
        grep "  object \|  material \|  mesh_" "$delight_log" | sed 's/^/    /'
      fi
    else
      fail "Blender delight failed (see $delight_log)"
      cat "$delight_log" | tail -20
      return
    fi

    # --- Step 3: Validate cleaned GLB (strict) ---
    echo -e "\n${CYAN}--- Step 3: Validate cleaned GLB ---${NC}"
    local clean_val_log="$model_dir/cleaned_validation.log"
    if python3 "$SCRIPTS/validate_glb.py" "$delit_glb" --strict > "$clean_val_log" 2>&1; then
      pass "Cleaned GLB passes strict validation"
    else
      fail "Cleaned GLB fails strict validation"
      grep "^  FAIL" "$clean_val_log" | sed 's/^/    /'
    fi

    # Verify artifacts were removed (compare fail counts)
    local clean_fails
    clean_fails=$(grep -c "^  FAIL" "$clean_val_log" 2>/dev/null || true)
    if [[ "$src_fails" -gt 0 && "$clean_fails" -lt "$src_fails" ]]; then
      pass "Artifact count reduced: $src_fails → $clean_fails"
    elif [[ "$src_fails" -gt 0 && "$clean_fails" -ge "$src_fails" ]]; then
      fail "Artifact count not reduced: $src_fails → $clean_fails"
    fi
  fi

  # --- Step 4: Generate LEGO LDR ---
  echo -e "\n${CYAN}--- Step 4: Generate LEGO LDR ---${NC}"

  local algorithms=("supersampled" "direct" "uvlab")

  for algo in "${algorithms[@]}"; do
    local ldr_file="$model_dir/${model_name}_${algo}.ldr"
    local ldr_log="$model_dir/${model_name}_${algo}.log"

    echo "  Algorithm: $algo"
    if (cd "$ROOT" && java -jar target/legomodel.jar \
        "$delit_glb" \
        "$RESOLUTION" \
        "$ldr_file" \
        ldraw \
        --color-mode=glb-color \
        --color-algorithm="$algo" \
        --color-fallback=15 \
        > "$ldr_log" 2>&1); then
      pass "LDR generated: $algo"

      # Verify output content
      if [[ -f "$ldr_file" ]]; then
        local line_count
        line_count=$(wc -l < "$ldr_file" | tr -d ' ')
        local brick_count
        brick_count=$(grep -c "^1 " "$ldr_file" 2>/dev/null || true)

        if [[ "$brick_count" -gt 0 ]]; then
          pass "$algo: $brick_count bricks, $line_count lines"
        else
          fail "$algo: LDR file has no brick lines"
        fi

        # Check color diversity
        local unique_colors
        unique_colors=$(grep "^1 " "$ldr_file" | awk '{print $2}' | sort -u | wc -l | tr -d ' ')
        if [[ "$unique_colors" -gt 1 ]]; then
          pass "$algo: $unique_colors unique colors (good diversity)"
        else
          warn "$algo: only $unique_colors unique color(s)"
        fi

        # Check for color code 16 (uncolored — should be zero if fallback works)
        local uncolored
        uncolored=$(grep "^1 16 " "$ldr_file" | wc -l | tr -d ' ')
        if [[ "$uncolored" -eq 0 ]]; then
          pass "$algo: no uncolored bricks"
        else
          warn "$algo: $uncolored uncolored bricks (color code 16)"
        fi
      else
        fail "$algo: output file not created"
      fi
    else
      fail "LDR generation failed: $algo (see $ldr_log)"
      tail -10 "$ldr_log" | sed 's/^/    /'
    fi
  done

  echo -e "\n  ${CYAN}Model outputs: $model_dir${NC}"
}

# ==========================================================================
# Main
# ==========================================================================

echo -e "\n${CYAN}============================================${NC}"
echo -e "${CYAN}  GLB Model Generation Test Suite${NC}"
echo -e "${CYAN}  $(date)${NC}"
echo -e "${CYAN}============================================${NC}"

check_prereqs
build_project

mkdir -p "$OUT_DIR"

if [[ -n "$SPECIFIC_MODEL" ]]; then
  model_path="$MODELS_DIR/$SPECIFIC_MODEL"
  if [[ ! -f "$model_path" ]]; then
    echo -e "${RED}Model not found: $model_path${NC}"
    exit 1
  fi
  test_model "$model_path"
else
  # Test all GLB files
  glb_count=0
  for glb in "$MODELS_DIR"/*.glb; do
    [[ -f "$glb" ]] || continue
    test_model "$glb"
    ((glb_count++))
  done
  if [[ "$glb_count" -eq 0 ]]; then
    echo -e "${RED}No .glb files found in $MODELS_DIR${NC}"
    exit 1
  fi
fi

# ==========================================================================
# Summary
# ==========================================================================
echo -e "\n${CYAN}============================================${NC}"
echo -e "${CYAN}  TEST SUMMARY${NC}"
echo -e "${CYAN}============================================${NC}"
echo -e "  ${GREEN}PASSED:  $PASS_COUNT${NC}"
echo -e "  ${RED}FAILED:  $FAIL_COUNT${NC}"
echo -e "  ${YELLOW}WARNED:  $WARN_COUNT${NC}"
echo -e "  ${CYAN}SKIPPED: $SKIP_COUNT${NC}"
echo ""

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  echo -e "  ${RED}RESULT: FAILED${NC}"
  exit 1
else
  echo -e "  ${GREEN}RESULT: ALL PASSED${NC}"
  exit 0
fi
