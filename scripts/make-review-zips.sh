#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/make-review-zips.sh
#   ./scripts/make-review-zips.sh dd40056
#
# If no SHA is passed, the current git short SHA is used.

COMMIT_SHA="${1:-$(git rev-parse --short HEAD)}"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Default output matches your Windows habit:
# /mnt/d/tmp/ITROBOC == D:\tmp\ITROBOC
OUT_DIR="${ITROBOC_ZIP_OUT:-/mnt/d/tmp/ITROBOC}"

if [[ ! -d /mnt/d ]]; then
    OUT_DIR="${ITROBOC_ZIP_OUT:-$HOME/tmp/ITROBOC}"
fi

CORE_SRC_DIR="$REPO_ROOT/core/src/main/kotlin/org/itroboc/core"
APP_SRC_DIR="$REPO_ROOT/app/src/main"

CORE_ZIP="$OUT_DIR/Core_${COMMIT_SHA}.zip"
APP_ZIP="$OUT_DIR/App_${COMMIT_SHA}.zip"

WORK_DIR="$(mktemp -d)"
CORE_TMP_DIR="$WORK_DIR/CoreZip"
APP_TMP_DIR="$WORK_DIR/AppZip"

cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$OUT_DIR"
mkdir -p "$CORE_TMP_DIR"
mkdir -p "$APP_TMP_DIR"

# --- Core zip ---
# Copy core source contents.
cp -a "$CORE_SRC_DIR"/. "$CORE_TMP_DIR"/

# Copy useful project context files into the core review zip root.
cp "$REPO_ROOT/README.md" "$CORE_TMP_DIR/README.md"

if [[ -f "$REPO_ROOT/docs/architecture.md" ]]; then
    cp "$REPO_ROOT/docs/architecture.md" "$CORE_TMP_DIR/architecture.md"
fi

if [[ -f "$REPO_ROOT/docs/product_context.md" ]]; then
    cp "$REPO_ROOT/docs/product_context.md" "$CORE_TMP_DIR/product_context.md"
fi

rm -f "$CORE_ZIP"
(
    cd "$CORE_TMP_DIR"
    zip -qr "$CORE_ZIP" .
)

echo "Created Core archive: $CORE_ZIP"

# --- App zip ---
# Preserve app/src/main as a 'main' folder in the zip, matching your PS script.
cp -a "$APP_SRC_DIR" "$APP_TMP_DIR/main"

rm -f "$APP_ZIP"
(
    cd "$APP_TMP_DIR"
    zip -qr "$APP_ZIP" .
)

echo "Created App archive: $APP_ZIP"
