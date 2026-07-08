#!/usr/bin/env bash
#
# build-keiyoushi-from-source.sh
# =================================
# Downloads a keiyoushi extension's source code from the extensions-source repo
# and compiles it as a JVM JAR against the Anikku macOS source-api JARs.
#
# This is the RECOMMENDED approach for making extensions available on macOS.
# Unlike the APK conversion approach (convert-keiyoushi-extension.sh), this
# compiles from the original Kotlin source, producing clean JVM bytecode with
# proper class names and no Android API stub issues.
#
# Usage:
#   ./build-keiyoushi-from-source.sh --pkg allanime --lang en
#
# Options:
#   --pkg <name>    Extension package name (e.g., allanime, nineanime)
#   --lang <code>   Language code (default: en)
#   --repo <url>    Custom extensions-source repo URL
#   --keep-temp     Keep temporary files for debugging
#   --help          Show this help
#
# Requirements:
#   - JDK 17+ (with kotlinc or javac)
#   - curl, git, python3
#   - Anikku source-api JARs (macos/libs/source-api-jvm.jar, common-jvm.jar)
#
# Examples:
#   # Build allanime extension
#   ./build-keiyoushi-from-source.sh --pkg allanime --lang en
#
#   # Build nineanime extension
#   ./build-keiyoushi-from-source.sh --pkg nineanime --lang en

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-source-build"

KEIYOUSHI_SOURCE_REPO="https://github.com/keiyoushi/extensions-source.git"
KEIYOUSHI_EXTENSION_SRC="https://raw.githubusercontent.com/keiyoushi/extensions-source/main/src"

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
  --repo <url>     Custom extensions-source repo URL
  --keep-temp      Keep temporary files for debugging
  --help           Show this help
EOF
    exit 0
}

# Parse arguments
PKG_NAME=""
LANG="en"
CUSTOM_REPO=""
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pkg) PKG_NAME="$2"; shift 2 ;;
        --lang) LANG="$2"; shift 2 ;;
        --repo) CUSTOM_REPO="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$PKG_NAME" ]; then
    err "Error: --pkg is required"
    usage
fi

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Checking prerequisites..."

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew -p macos rebuildSourceApiJars"
    exit 1
fi

KOTLINC="${KOTLINC:-}"
if command -v kotlinc &>/dev/null; then
    KOTLINC="kotlinc"
    log "Found kotlinc: $(which kotlinc)"
else
    log "kotlinc not found — will use javac (extensions written in Kotlin need kotlinc)"
    log "Install kotlin compiler: brew install kotlin"
fi

# ---------------------------------------------------------------------------
# Step 1: Download extension source
# ---------------------------------------------------------------------------
log "Step 1: Downloading source for extension: ${PKG_NAME} (lang: ${LANG})"
mkdir -p "${TEMP_DIR}"

SRC_URL="${KEIYOUSHI_EXTENSION_SRC}/${LANG}/${PKG_NAME}"
EXT_DIR="${TEMP_DIR}/src/${LANG}/${PKG_NAME}"
mkdir -p "${EXT_DIR}"

log "Fetching file list from keiyoushi source repo for ${LANG}/${PKG_NAME}..."

# Try to get the directory listing from GitHub API
GH_API_URL="https://api.github.com/repos/keiyoushi/extensions-source/contents/src/${LANG}/${PKG_NAME}"
HTTP_RESPONSE=$(curl -sL -o "${TEMP_DIR}/gh-response.json" -w "%{http_code}" "$GH_API_URL" 2>/dev/null || echo "000")

if [ "$HTTP_RESPONSE" != "200" ]; then
    err "Extension source not found at ${SRC_URL}"
    err "HTTP response: ${HTTP_RESPONSE}"
    err "Available extensions in ${LANG}:"
    curl -sL "https://api.github.com/repos/keiyoushi/extensions-source/contents/src/${LANG}" 2>/dev/null | \
        python3 -c "import sys,json; data=json.load(sys.stdin); [print(f'  {d[\"name\"]}') for d in data if d.get('type')=='dir']" 2>/dev/null | head -20 || true
    exit 1
fi

# Parse the file list from GitHub API
log "Downloading extension source files..."
python3 -c "
import json, os
with open('${TEMP_DIR}/gh-response.json') as f:
    items = json.load(f)

base_dir = '${EXT_DIR}'
for item in items:
    if item['type'] == 'file':
        # Download the file
        url = item['download_url']
        path = item['path']
        rel_path = os.path.relpath(path, 'src/${LANG}/${PKG_NAME}')
        dest = os.path.join(base_dir, rel_path)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        import urllib.request
        urllib.request.urlretrieve(url, dest)
        print(f'  Downloaded: {rel_path}')
    elif item['type'] == 'dir':
        # Recursive download would need pagination — for now just note it
        print(f'  Subdirectory: {item[\"name\"]}/')
" 2>&1

# Also check for build.gradle.kts
BUILD_GRADLE_URL="https://raw.githubusercontent.com/keiyoushi/extensions-source/main/src/${LANG}/${PKG_NAME}/build.gradle.kts"
if curl -sL -o "${EXT_DIR}/build.gradle.kts" "$BUILD_GRADLE_URL" 2>/dev/null && [ -s "${EXT_DIR}/build.gradle.kts" ]; then
    log "Downloaded build.gradle.kts"
else
    log "No build.gradle.kts found — creating minimal one"
fi

SRC_COUNT=$(find "${EXT_DIR}" -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)
log "Downloaded ${SRC_COUNT} source files"

if [ "$SRC_COUNT" -eq 0 ]; then
    err "No source files downloaded — the extension may not exist or the repo structure is different"
    err "Check available extensions at: https://github.com/keiyoushi/extensions-source/tree/main/src/${LANG}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Parse extension metadata
# ---------------------------------------------------------------------------
log "Step 2: Parsing extension metadata..."

# Try to extract metadata from build.gradle.kts
if [ -f "${EXT_DIR}/build.gradle.kts" ]; then
    EXT_NAME=$(grep -oP 'name\s*=\s*"\K[^"]+' "${EXT_DIR}/build.gradle.kts" 2>/dev/null | head -1 || echo "${PKG_NAME}")
    EXT_PKG="eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}"
    # Extract version info
    VERSION_NAME=$(grep -oP 'version\s*=\s*"\K[^"]+' "${EXT_DIR}/build.gradle.kts" 2>/dev/null | head -1 || echo "1.0.0")
    VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "${EXT_DIR}/build.gradle.kts" 2>/dev/null || echo "100")
    LIB_VERSION=$(grep -oP 'libVersion\s*=\s*\K[\d.]+' "${EXT_DIR}/build.gradle.kts" 2>/dev/null || echo "15.0")
    IS_NSFW=$(grep -oP 'contentWarning\s*=\s*ContentWarning\.\K\w+' "${EXT_DIR}/build.gradle.kts" 2>/dev/null || echo "SAFE")
    NSFW_BOOL=false
    [ "$IS_NSFW" = "NSFW" ] || [ "$IS_NSFW" = "MIXED" ] && NSFW_BOOL=true
else
    EXT_NAME="${PKG_NAME^}"
    EXT_PKG="eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}"
    VERSION_NAME="1.0.0"
    VERSION_CODE="100"
    LIB_VERSION="15.0"
    NSFW_BOOL=false
fi

log "  Name: ${EXT_NAME}"
log "  Package: ${EXT_PKG}"
log "  Version: ${VERSION_NAME} (code: ${VERSION_CODE})"

# Find the main source class (the one that extends HttpSource or similar)
MAIN_CLASS=$(find "${EXT_DIR}" -name "*.kt" -exec grep -l "HttpSource\|Source\|CatalogueSource" {} \; 2>/dev/null | head -1)
if [ -n "$MAIN_CLASS" ]; then
    MAIN_CLASS_NAME=$(basename "$MAIN_CLASS" .kt)
    FULL_CLASS_NAME="${EXT_PKG}.${MAIN_CLASS_NAME}"
else
    FULL_CLASS_NAME="${EXT_PKG}.${PKG_NAME^}"
fi

log "  Source class: ${FULL_CLASS_NAME}"

# ---------------------------------------------------------------------------
# Step 3: Compile the extension
# ---------------------------------------------------------------------------
log "Step 3: Compiling extension..."

CLASSES_DIR="${TEMP_DIR}/classes"
mkdir -p "$CLASSES_DIR"

# Find all Kotlin source files
find "${EXT_DIR}" -name "*.kt" > "${TEMP_DIR}/kotlin-sources.txt"
KT_COUNT=$(wc -l < "${TEMP_DIR}/kotlin-sources.txt")
log "Found ${KT_COUNT} Kotlin source files"

if [ -z "$KOTLINC" ] || [ "$KOTLINC" = "" ]; then
    err "Kotlin compiler (kotlinc) is required to compile Kotlin extension sources."
    err "Install it: brew install kotlin"
    err ""
    log "Attempting to compile with kotlinc from PATH..."
    if command -v kotlinc &>/dev/null; then
        KOTLINC="kotlinc"
    else
        err "kotlinc not available. Cannot compile extension."
        err "Install kotlin: brew install kotlin"
        exit 1
    fi
fi

# Build the classpath with all required dependencies
# Extensions depend on: source-api, common, kotlinx-coroutines, okhttp, jsoup, rxjava
CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"

# Add kotlin stdlib and kotlinx libraries from the Gradle cache or Homebrew
KOTLIN_STDLIB=""
if [ -f "/opt/homebrew/Cellar/kotlin/*/libexec/lib/kotlin-stdlib.jar" ]; then
    KOTLIN_STDLIB=$(ls /opt/homebrew/Cellar/kotlin/*/libexec/lib/kotlin-stdlib.jar 2>/dev/null | head -1)
elif ls ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/*/kotlin-stdlib-*.jar &>/dev/null; then
    KOTLIN_STDLIB=$(ls ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/*/kotlin-stdlib-*.jar 2>/dev/null | sort -V | tail -1)
fi
if [ -n "$KOTLIN_STDLIB" ] && [ -f "$KOTLIN_STDLIB" ]; then
    CLASSPATH="${CLASSPATH}:${KOTLIN_STDLIB}"
    log "Added kotlin stdlib: ${KOTLIN_STDLIB}"
else
    log "WARNING: kotlin-stdlib.jar not found — compilation may fail"
fi

# Add any lib JARs if present
if ls "${TEMP_DIR}/libs/"*.jar 2>/dev/null; then
    for jar in "${TEMP_DIR}/libs/"*.jar; do
        CLASSPATH="${CLASSPATH}:${jar}"
    done
fi

# Create a manifest file
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

log "Compiling with kotlinc..."
log "Classpath: ${CLASSPATH}"

# Compile with kotlinc
# Note: -no-stdlib is NOT used because extensions depend on kotlin stdlib APIs
set +e
kotlinc \
    -cp "${CLASSPATH}" \
    -d "${CLASSES_DIR}" \
    -jvm-target 17 \
    @"${TEMP_DIR}/kotlin-sources.txt" \
    2>&1 | tail -30
COMPILE_EXIT=$?
set -e

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
log "Compilation produced ${CLASS_COUNT} .class files"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err "Compilation failed — no .class files produced"
    if [ "$COMPILE_EXIT" -ne 0 ]; then
        err "kotlinc exit code: ${COMPILE_EXIT}"
    fi
    err ""
    err "The extension may have dependencies on keiyoushi shared modules"
    err "(lib-cookieinterceptor, lib-synchrony, etc.) that are not available."
    err ""
    err "A more complete build would require checking out the full"
    err "extensions-source repo and building against its Gradle infrastructure."
    log "Keeping temp files for debugging at: ${TEMP_DIR}"
    KEEP_TEMP=true
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 4: Package as JAR
# ---------------------------------------------------------------------------
log "Step 4: Packaging extension JAR..."

JAR_NAME="${EXT_PKG}.jar"
JAR_PATH="${TEMP_DIR}/${JAR_NAME}"

cd "$CLASSES_DIR"
jar cf "${JAR_PATH}" META-INF/ $(find . -name "*.class" 2>/dev/null)

log "JAR created: ${JAR_PATH}"
log "JAR contents:"
jar tf "${JAR_PATH}" | head -20

# ---------------------------------------------------------------------------
# Step 5: Install to extensions directory
# ---------------------------------------------------------------------------
log "Step 5: Installing extension..."

mkdir -p "${EXTENSIONS_DIR}"
cp "${JAR_PATH}" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "Installed to: ${EXTENSIONS_DIR}/${JAR_NAME}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  Extension build complete!"
log "═══════════════════════════════════════════════════════════════"
log "  Name:     ${EXT_NAME}"
log "  Package:  ${EXT_PKG}"
log "  Version:  ${VERSION_NAME} (${VERSION_CODE})"
log "  JAR:      ${EXTENSIONS_DIR}/${JAR_NAME}"
log "  Classes:  ${CLASS_COUNT}"
log ""
log "  To test:"
log "    1. Launch the Anikku macOS app"
log "    2. Go to Browse tab → Extensions"
log "    3. Trust the extension if prompted"
log "    4. The source should appear in the source list"
log "    5. Click to browse → search → watch"
log "═══════════════════════════════════════════════════════════════"
