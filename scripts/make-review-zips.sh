#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/make-review-zips.sh
#   ./scripts/make-review-zips.sh dd40056
#
# If no SHA is passed, the current git short SHA is used.

COMMIT_SHA="${1:-$(git rev-parse --short HEAD)}"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Default output matches Windows D:\tmp\ITROBOC
OUT_DIR="${ITROBOC_ZIP_OUT:-/mnt/d/tmp/ITROBOC}"

if [[ ! -d /mnt/d ]]; then
    OUT_DIR="${ITROBOC_ZIP_OUT:-$HOME/tmp/ITROBOC}"
fi

CORE_DIR="$REPO_ROOT/core"
VISION_DIR="$REPO_ROOT/vision"
APP_DIR="$REPO_ROOT/app"

CORE_ZIP="$OUT_DIR/Core_${COMMIT_SHA}.zip"
VISION_ZIP="$OUT_DIR/Vision_${COMMIT_SHA}.zip"
APP_ZIP="$OUT_DIR/App_${COMMIT_SHA}.zip"
PROJECT_ZIP="$OUT_DIR/Project_${COMMIT_SHA}.zip"

WORK_DIR="$(mktemp -d)"
cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$OUT_DIR"

zip_dir() {
    local src_dir="$1"
    local zip_path="$2"

    rm -f "$zip_path"
    (
        cd "$src_dir"
        zip -qr "$zip_path" . \
            -x "build/*" \
            -x ".gradle/*" \
            -x ".kotlin/*" \
            -x "*.zip"
    )
    echo "Created archive: $zip_path"
}

# --- Core zip ---
if [[ -d "$CORE_DIR" ]]; then
    CORE_TMP="$WORK_DIR/CoreZip"
    mkdir -p "$CORE_TMP"

    cp -a "$CORE_DIR/src" "$CORE_TMP/src"
    cp "$CORE_DIR/build.gradle.kts" "$CORE_TMP/build.gradle.kts"

    cp "$REPO_ROOT/README.md" "$CORE_TMP/README.md" 2>/dev/null || true

    if [[ -f "$REPO_ROOT/docs/architecture.md" ]]; then
        cp "$REPO_ROOT/docs/architecture.md" "$CORE_TMP/architecture.md"
    fi

    if [[ -f "$REPO_ROOT/docs/product_context.md" ]]; then
        cp "$REPO_ROOT/docs/product_context.md" "$CORE_TMP/product_context.md"
    fi

    zip_dir "$CORE_TMP" "$CORE_ZIP"
else
    echo "Skipped Core archive: $CORE_DIR not found"
fi

# --- Vision zip ---
if [[ -d "$VISION_DIR" ]]; then
    VISION_TMP="$WORK_DIR/VisionZip"
    mkdir -p "$VISION_TMP"

    cp -a "$VISION_DIR/src" "$VISION_TMP/src"
    cp "$VISION_DIR/build.gradle.kts" "$VISION_TMP/build.gradle.kts"

    cp "$REPO_ROOT/README.md" "$VISION_TMP/README.md" 2>/dev/null || true

    if [[ -f "$REPO_ROOT/docs/camera_and_decoding_design.md" ]]; then
        cp "$REPO_ROOT/docs/camera_and_decoding_design.md" "$VISION_TMP/camera_and_decoding_design.md"
    fi

    if [[ -f "$REPO_ROOT/docs/camera_and_decoding_tickets.md" ]]; then
        cp "$REPO_ROOT/docs/camera_and_decoding_tickets.md" "$VISION_TMP/camera_and_decoding_tickets.md"
    fi

    zip_dir "$VISION_TMP" "$VISION_ZIP"
else
    echo "Skipped Vision archive: $VISION_DIR not found"
fi

# --- App zip ---
if [[ -d "$APP_DIR" ]]; then
    APP_TMP="$WORK_DIR/AppZip"
    mkdir -p "$APP_TMP"

    cp -a "$APP_DIR/src" "$APP_TMP/src"
    cp "$APP_DIR/build.gradle.kts" "$APP_TMP/build.gradle.kts"

    zip_dir "$APP_TMP" "$APP_ZIP"
else
    echo "Skipped App archive: $APP_DIR not found"
fi

# --- Project/root zip ---
PROJECT_TMP="$WORK_DIR/ProjectZip"
mkdir -p "$PROJECT_TMP"

for f in \
    "settings.gradle.kts" \
    "build.gradle.kts" \
    "gradle.properties" \
    "README.md" \
    "AGENTS.md"
do
    if [[ -f "$REPO_ROOT/$f" ]]; then
        cp "$REPO_ROOT/$f" "$PROJECT_TMP/$f"
    fi
done

if [[ -d "$REPO_ROOT/docs" ]]; then
    cp -a "$REPO_ROOT/docs" "$PROJECT_TMP/docs"
fi

if [[ -d "$REPO_ROOT/md-files" ]]; then
    cp -a "$REPO_ROOT/md-files" "$PROJECT_TMP/md-files"
fi

zip_dir "$PROJECT_TMP" "$PROJECT_ZIP"
