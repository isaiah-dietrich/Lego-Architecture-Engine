#!/usr/bin/env bash
# run_model.sh — Generate a LEGO model with an auto-incremented output filename.
#
# Output: <model_stem>_<resolution>_<N>.ldr  where N increments each run.
# The counter is per model+resolution combination, stored in output/.run_counts
#
# Usage:
#   scripts/run_model.sh <model_file> <resolution> [export_mode] [extra_flags...]
#
# Examples:
#   scripts/run_model.sh labrador_dog.glb 15
#   scripts/run_model.sh labrador_dog.glb 20 ldraw --color-mode=glb-color
#   scripts/run_model.sh labrador_dog.glb 15 ldraw --color-mode=glb-color --color-algorithm=supersampled

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELS_DIR="$ROOT/../models"
OUTPUT_DIR="$ROOT/output"
COUNTS_FILE="$OUTPUT_DIR/.run_counts"
JAR="$ROOT/target/legomodel.jar"

if [[ $# -lt 2 ]]; then
  echo "Usage: $(basename "$0") <model_file> <resolution> [export_mode] [extra_flags...]"
  echo "  model_file   filename in models/ directory (e.g. labrador_dog.glb)"
  echo "  resolution   integer >= 2"
  echo "  export_mode  brick | ldraw | voxel-surface | ... (default: ldraw)"
  exit 1
fi

MODEL_FILE="$1"
RESOLUTION="$2"
EXPORT_MODE="${3:-ldraw}"
shift 3 2>/dev/null || shift $#
EXTRA_FLAGS=("$@")

MODEL_PATH="$MODELS_DIR/$MODEL_FILE"
if [[ ! -f "$MODEL_PATH" ]]; then
  echo "Error: model not found: $MODEL_PATH"
  exit 1
fi

if [[ ! -f "$JAR" ]]; then
  echo "Error: jar not found at $JAR — run: cd legomodel && mvn -DskipTests package"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
touch "$COUNTS_FILE"

# Read and increment counter for this model+resolution key
MODEL_STEM="${MODEL_FILE%.*}"
COUNT_KEY="${MODEL_STEM}_${RESOLUTION}"
CURRENT=$(grep "^${COUNT_KEY}=" "$COUNTS_FILE" 2>/dev/null | cut -d= -f2 || echo "0")
NEXT=$((CURRENT + 1))

# Update counts file
if grep -q "^${COUNT_KEY}=" "$COUNTS_FILE" 2>/dev/null; then
  sed -i '' "s/^${COUNT_KEY}=.*/${COUNT_KEY}=${NEXT}/" "$COUNTS_FILE"
else
  echo "${COUNT_KEY}=${NEXT}" >> "$COUNTS_FILE"
fi

OUTPUT_FILE="$OUTPUT_DIR/${MODEL_STEM}_${RESOLUTION}_${NEXT}.ldr"

echo "Model:      $MODEL_FILE"
echo "Resolution: $RESOLUTION"
echo "Export:     $EXPORT_MODE"
echo "Run #:      $NEXT"
echo "Output:     $OUTPUT_FILE"
echo ""

java -jar "$JAR" \
  "$MODEL_PATH" \
  "$RESOLUTION" \
  "$OUTPUT_FILE" \
  "$EXPORT_MODE" \
  "${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"}"

echo ""
echo "Done: $OUTPUT_FILE"
