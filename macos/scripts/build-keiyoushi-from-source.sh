#!/usr/bin/env bash
#
# build-keiyoushi-from-source.sh
# =================================
# Builds a keiyoushi extension from source as a JVM JAR for macOS.
#
# Uses git sparse-checkout to download just the extension directory
# from the keiyoushi/extensions-source repo (handles nested subdirectories
# that the GitHub Contents API cannot do reliably), then compiles against
# the source-api JARs and all required dependency JARs from the Gradle cache.
#
# This is the RECOMMENDED approach. Unlike APK→jadx→javac conversion,
# this compiles original Kotlin source producing clean JVM bytecode.
#
# Usage:
#   ./build-keiyoushi-from-source.sh --pkg allanime --lang en
#
# Options:
#   --pkg <name>    Extension directory name (e.g., allanime, nineanime)
#   --lang <code>   Language code (default: en)
#   --keep-temp     Keep temporary files for debugging
#   --help          Show this help
#
# Requirements:
#   - JDK 17+ with kotlinc (brew install kotlin)
#   - git, curl, python3
#   - Anikku source-api JARs (built by Gradle task rebuildSourceApiJars)
#
# Examples:
#   ./build-keiyoushi-from-source.sh --pkg allanime --lang en
#   ./build-keiyoushi-from-source.sh --pkg nineanime --lang en

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-source-build"
GIT_CLONE_DIR="${TEMP_DIR}/extensions-source"

KEIYOUSHI_SOURCE_URL="https://github.com/keiyoushi/extensions-source.git"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

cleanup() {
    if [ "${KEEP_TEMP:-false}" != "true" ]; then
        log "Cleaning up temporary files..."
        rm -rf "${TEMP_DIR}"
    else
        log "Keeping temporary files at: ${TEMP_DIR}"
    fi
}
trap cleanup EXIT

usage() {
    cat <<EOF
Usage: $(basename "$0") --pkg <name> [OPTIONS]

Build a keiyoushi extension from source as a JVM JAR.

Required:
  --pkg <name>     Extension directory name (e.g., allanime, nineanime)

Options:
  --lang <code>    Language code (default: en)
  --keep-temp      Keep temporary files for debugging
  --help           Show this help
EOF
    exit 0
}

# Parse arguments
PKG_NAME=""
LANG="en"
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pkg) PKG_NAME="$2"; shift 2 ;;
        --lang) LANG="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$PKG_NAME" ]; then
    err "Error: --pkg is required"
    usage
fi

EXT_PKG="eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}"
JAR_NAME="${EXT_PKG}.jar"

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Checking prerequisites..."

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew rebuildSourceApiJars"
    exit 1
fi

if ! command -v kotlinc &>/dev/null; then
    err "kotlinc not found. Install: brew install kotlin"
    exit 1
fi

KOTLINC_VERSION=$(kotlinc -version 2>&1 | head -1 || echo "unknown")
log "Found kotlinc: $(which kotlinc) (${KOTLINC_VERSION})"

# ---------------------------------------------------------------------------
# Step 1: Download extension source via git sparse-checkout
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  BUILD EXTENSION: ${PKG_NAME} (lang: ${LANG})"
log "═══════════════════════════════════════════════════════════════"
log ""

SRC_DIR="${TEMP_DIR}/src/${LANG}/${PKG_NAME}"
mkdir -p "${TEMP_DIR}"

log "Step 1: Downloading source for ${PKG_NAME} via git sparse-checkout..."
log ""

if [ -d "${GIT_CLONE_DIR}" ]; then
    log "Removing previous clone..."
    rm -rf "${GIT_CLONE_DIR}"
fi

# Use a shallow, sparse clone to download just the extension directory
# This handles all nested subdirectories (src/, res/, etc.) that the
# GitHub Contents API cannot handle recursively.
log "Cloning keiyoushi/extensions-source (sparse, depth 1)..."
log "  This downloads only the extension directory, not the full repo."
log ""

git clone --depth 1 --filter=blob:none --no-checkout \
    "${KEIYOUSHI_SOURCE_URL}" "${GIT_CLONE_DIR}" 2>&1 | tail -5

cd "${GIT_CLONE_DIR}"
git sparse-checkout set "src/${LANG}/${PKG_NAME}" 2>&1
git checkout 2>&1 | tail -5

SRC_DIR="${GIT_CLONE_DIR}/src/${LANG}/${PKG_NAME}"
SRC_COUNT=$(find "${SRC_DIR}" -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)

log "Downloaded ${SRC_COUNT} source files"
log "Source directory: ${SRC_DIR}"

if [ "$SRC_COUNT" -eq 0 ]; then
    err "No source files found for ${PKG_NAME} in language ${LANG}"
    err "Check available extensions at: https://github.com/keiyoushi/extensions-source/tree/main/src/${LANG}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Parse extension metadata
# ---------------------------------------------------------------------------
log ""
log "Step 2: Parsing extension metadata..."

# Try to extract metadata from build.gradle.kts
BUILD_FILE="${SRC_DIR}/build.gradle.kts"
if [ -f "$BUILD_FILE" ]; then
    EXT_NAME=$(grep -oP 'name\s*=\s*"\K[^"]+' "$BUILD_FILE" 2>/dev/null | head -1 || python3 -c "import sys; p='${PKG_NAME}'; print(p[0].upper()+p[1:] if p else 'Unknown')")
    VERSION_NAME=$(grep -oP 'version\s*=\s*"\K[^"]+' "$BUILD_FILE" 2>/dev/null | head -1 || echo "1.0.0")
    VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$BUILD_FILE" 2>/dev/null || echo "100")
    LIB_VERSION=$(grep -oP 'libVersion\s*=\s*\K[\d.]+' "$BUILD_FILE" 2>/dev/null || echo "15.0")
    IS_NSFW=$(grep -oP 'contentWarning\s*=\s*ContentWarning\.\K\w+' "$BUILD_FILE" 2>/dev/null || echo "SAFE")
    NSFW_BOOL=false
    [ "$IS_NSFW" = "NSFW" ] || [ "$IS_NSFW" = "MIXED" ] && NSFW_BOOL=true
else
    EXT_NAME=$(python3 -c "import sys; p='${PKG_NAME}'; print(p[0].upper()+p[1:] if p else 'Unknown')")
    VERSION_NAME="1.0.0"
    VERSION_CODE="100"
    LIB_VERSION="15.0"
    NSFW_BOOL=false
fi

log "  Name: ${EXT_NAME}"
log "  Package: ${EXT_PKG}"
log "  Version: ${VERSION_NAME} (code: ${VERSION_CODE})"

# Find the main source class
MAIN_CLASS=$(find "${SRC_DIR}" -name "*.kt" -exec grep -l "HttpSource\|CatalogueSource\|Source" {} \; 2>/dev/null | head -1)
if [ -n "$MAIN_CLASS" ]; then
    MAIN_CLASS_NAME=$(basename "$MAIN_CLASS" .kt)
    FULL_CLASS_NAME="${EXT_PKG}.${MAIN_CLASS_NAME}"
else
    FULL_CLASS_NAME=$(python3 -c "import sys; p='${EXT_PKG}'; print(p+'.'+p.rsplit('.',1)[1][0].upper()+p.rsplit('.',1)[1][1:] if '.' in p else p)")
fi
log "  Source class: ${FULL_CLASS_NAME}"

# ---------------------------------------------------------------------------
# Step 3: Build classpath from Gradle cache
# ---------------------------------------------------------------------------
log ""
log "Step 3: Building classpath with all dependencies..."

# Start with source-api and common
CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"

# Find dependency JARs from Gradle cache
GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

find_jar() {
    local pattern="$1"
    local version="$2"
    local found
    found=$(find "${GRADLE_CACHE}" -path "*/${pattern}${version}*.jar" 2>/dev/null | grep -v sources | head -1)
    if [ -n "$found" ] && [ -f "$found" ]; then
        echo "$found"
    fi
}

add_to_classpath() {
    local jar="$1"
    if [ -n "$jar" ] && [ -f "$jar" ]; then
        CLASSPATH="${CLASSPATH}:${jar}"
    fi
}

log "  Kotlin stdlib..."
# Kotlin stdlib from Homebrew
KOTLIN_HOME="/opt/homebrew/Cellar/kotlin/2.4.0"
if [ -d "$KOTLIN_HOME" ]; then
    for jar in "${KOTLIN_HOME}/libexec/lib/"*.jar; do
        add_to_classpath "$jar"
    done
    log "    Found kotlin stdlib JARs in Homebrew"
fi

log "  Kotlinx-coroutines..."
add_to_classpath "$(find_jar kotlinx-coroutines-core-jvm 1.10.2)"
add_to_classpath "$(find_jar kotlinx-coroutines-core 1.10.2)"

log "  OkHttp..."
add_to_classpath "$(find_jar okhttp 4.12.0)"

log "  Okio..."
add_to_classpath "$(find_jar okio-jvm 3.15.0)"

log "  Jsoup..."
add_to_classpath "$(find_jar jsoup 1.21.2)"

log "  RxJava..."
add_to_classpath "$(find_jar rxjava 1.3.8)"

log "  Kotlinx-serialization..."
add_to_classpath "$(find_jar kotlinx-serialization-json-jvm 1.7.3)"
add_to_classpath "$(find_jar kotlinx-serialization-json 1.7.3)"

log "  Injekt (DI)..."
add_to_classpath "$(find_jar injekt-core 91edab2317)"
add_to_classpath "$(find_jar injekt 91edab2317)"
# Try alternate hash
if ! echo "$CLASSPATH" | grep -q "injekt"; then
    add_to_classpath "$(find ~/.gradle/caches -name 'injekt-*.jar' 2>/dev/null | grep -v sources | head -1)"
fi

log ""

# Verify classpath is not empty
log "Classpath has $(echo "$CLASSPATH" | tr ':' '\n' | grep -c '.') entries"

# ---------------------------------------------------------------------------
# Step 4: Compile the extension
# ---------------------------------------------------------------------------
log ""
log "Step 4: Compiling extension with kotlinc..."

CLASSES_DIR="${TEMP_DIR}/classes"
mkdir -p "$CLASSES_DIR"

# Find all source files
find "${SRC_DIR}" -name "*.kt" > "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null
find "${SRC_DIR}" -name "*.java" > "${TEMP_DIR}/java-sources.txt" 2>/dev/null

KT_COUNT=$(wc -l < "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null || echo "0")
JAVA_COUNT=$(wc -l < "${TEMP_DIR}/java-sources.txt" 2>/dev/null || echo "0")
log "Sources: ${KT_COUNT} Kotlin files, ${JAVA_COUNT} Java files"

# Create META-INF manifest
MANIFEST_DIR="${CLASSES_DIR}/META-INF"
mkdir -p "$MANIFEST_DIR"

cat > "${MANIFEST_DIR}/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME}",
  "pkgName": "${EXT_PKG}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE},
  "libVersion": ${LIB_VERSION},
  "lang": "${LANG}",
  "isNsfw": ${NSFW_BOOL},
  "isTorrent": false,
  "sourceClass": "${FULL_CLASS_NAME}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

# Compile with kotlinc
log "Compiling..."
log "  kotlinc -cp <classpath> -d ${CLASSES_DIR} -jvm-target 17 ..."

COMPILE_START=$(date +%s)
set +e
kotlinc \
    -cp "${CLASSPATH}" \
    -d "${CLASSES_DIR}" \
    -jvm-target 17 \
    @"${TEMP_DIR}/kotlin-sources.txt" \
    2>&1
COMPILE_EXIT=$?
COMPILE_END=$(date +%s)
set -e

COMPILE_DURATION=$((COMPILE_END - COMPILE_START))
log "Compilation took ${COMPILE_DURATION}s, exit code: ${COMPILE_EXIT}"

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
log "Compilation produced ${CLASS_COUNT} .class files"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err ""
    err "Compilation failed — no .class files produced."
    err ""
    err "Common issues:"
    err "  1. Missing dependency JARs — check classpath entries above"
    err "  2. Extension uses keiyoushi shared modules (lib-cookieinterceptor, etc.)"
    err "     that are not in source-api — these need to be compiled separately"
    err "  3. Kotlin version mismatch (extension was built with a different Kotlin version)"
    err ""
    err "Keeping temp files for debugging at: ${TEMP_DIR}"
    KEEP_TEMP=true
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 5: Package as JAR
# ---------------------------------------------------------------------------
log ""
log "Step 5: Packaging extension JAR..."

JAR_PATH="${TEMP_DIR}/${JAR_NAME}"

cd "$CLASSES_DIR"
jar cf "${JAR_PATH}" META-INF/ $(find . -name "*.class" 2>/dev/null)

JAR_SIZE=$(stat -f%z "${JAR_PATH}" 2>/dev/null || echo "0")
log "JAR created: ${JAR_PATH}"
log "Size: ${JAR_SIZE} bytes"
log "Classes: ${CLASS_COUNT}"
log ""
log "JAR contents (first 20 entries):"
jar tf "${JAR_PATH}" | head -20

# ---------------------------------------------------------------------------
# Step 6: Install to extensions directory
# ---------------------------------------------------------------------------
log ""
log "Step 6: Installing extension..."

mkdir -p "${EXTENSIONS_DIR}"
cp "${JAR_PATH}" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "Installed to: ${EXTENSIONS_DIR}/${JAR_NAME}"

# Also remove any old version
if [ -f "${EXTENSIONS_DIR}/${JAR_NAME}.old" ]; then
    rm "${EXTENSIONS_DIR}/${JAR_NAME}.old"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  EXTENSION BUILD COMPLETE!"
log "═══════════════════════════════════════════════════════════════"
log "  Name:     ${EXT_NAME}"
log "  Package:  ${EXT_PKG}"
log "  Version:  ${VERSION_NAME} (${VERSION_CODE})"
log "  JAR:      ${EXTENSIONS_DIR}/${JAR_NAME}"
log "  Classes:  ${CLASS_COUNT}"
log "  Size:     ${JAR_SIZE} bytes"
log ""
log "  To verify:"
log "    1. Launch Anikku macOS app"
log "    2. Browse tab → Extensions → check Installed tab"
log "    3. If untrusted, go to Untrusted tab → Trust"
log "    4. Source should appear in Browse tab"
log "    5. Click to browse → search → watch"
log "═══════════════════════════════════════════════════════════════"
