#!/usr/bin/env bash
#
# build-keiyoushi-from-source.sh
# =================================
# Builds a keiyoushi extension from source as a JVM JAR for macOS.
#
# Uses git sparse-checkout to download the extension directory PLUS all
# shared library modules (lib-*) from the keiyoushi/extensions-source repo,
# then compiles everything against the source-api JARs and Gradle-cached
# dependency JARs.
#
# This avoids android.* references that plague dex2jar-converted APKs.
#
# Usage:
#   ./build-keiyoushi-from-source.sh --pkg nineanime --lang en
#
# Options:
#   --pkg <name>    Extension directory name (e.g., nineanime, allanime)
#   --lang <code>   Language code (default: en)
#   --keep-temp     Keep temporary files for debugging
#   --help          Show this help
#
# Requirements:
#   - JDK 17+ with kotlinc (brew install kotlin)
#   - git, curl, python3
#   - Anikku source-api JARs (built by Gradle task rebuildSourceApiJars)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-source-build"
GIT_CLONE_DIR="${TEMP_DIR}/extensions-source"

KEIYOUSHI_SOURCE_URL="https://github.com/keiyoushi/extensions-source.git"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

# Find JDK tools
JAVA_HOME="${JAVA_HOME:-/tmp/corretto17/amazon-corretto-17.jdk/Contents/Home}"
JAR_CMD=""
for c in "${JAVA_HOME}/bin/jar" "/opt/homebrew/opt/openjdk@17/bin/jar" "/opt/homebrew/opt/openjdk/bin/jar" $(which jar 2>/dev/null || true); do
    if [ -n "$c" ] && [ -f "$c" ] && [ -x "$c" ]; then
        JAR_CMD="$c"
        break
    fi
done

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
  --pkg <name>     Extension directory name (e.g., nineanime, allanime)

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

if [ -z "$JAR_CMD" ]; then
    err "jar command not found. Set JAVA_HOME or install JDK 17+."
    exit 1
fi

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

log "Found kotlinc: $(which kotlinc)"
log "jar: ${JAR_CMD}"

# ---------------------------------------------------------------------------
# Step 1: Download extension + shared lib source
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD: ${PKG_NAME} (lang: ${LANG})"
log "╚══════════════════════════════════════════════════════════════╝"
log ""

mkdir -p "${TEMP_DIR}"
if [ -d "${GIT_CLONE_DIR}" ]; then
    rm -rf "${GIT_CLONE_DIR}"
fi

log "Step 1: Downloading source via git sparse-checkout..."
git clone --depth 1 --filter=blob:none --no-checkout \
    "${KEIYOUSHI_SOURCE_URL}" "${GIT_CLONE_DIR}" 2>&1 | tail -2

cd "${GIT_CLONE_DIR}"

# Checkout extension directory and all shared libs
SHARED_LIBS=$(git ls-tree --name-only HEAD 2>/dev/null | grep '^lib-' || true)
SPARSE_PATTERNS="src/${LANG}/${PKG_NAME}"
for lib in $SHARED_LIBS; do
    SPARSE_PATTERNS="$SPARSE_PATTERNS $lib"
done
# Also get gradle version catalog
SPARSE_PATTERNS="$SPARSE_PATTERNS gradle"

git sparse-checkout set $SPARSE_PATTERNS 2>&1
git checkout 2>&1 | tail -2

SRC_DIR="${GIT_CLONE_DIR}/src/${LANG}/${PKG_NAME}"
SRC_COUNT=$(find "${SRC_DIR}" -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l)
SHARED_COUNT=$(ls -d "${GIT_CLONE_DIR}"/lib-*/ 2>/dev/null | wc -l)

log "Downloaded ${SRC_COUNT} source files, ${SHARED_COUNT} shared lib(s)"
for lib in "${GIT_CLONE_DIR}/lib-"*/; do
    [ -d "$lib" ] && log "  lib: $(basename "$lib") ($(find "$lib" -name '*.kt' | wc -l) files)"
done

if [ "$SRC_COUNT" -eq 0 ]; then
    err "No source files found for ${PKG_NAME} in language ${LANG}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Parse extension metadata (Python)
# ---------------------------------------------------------------------------
log ""
log "Step 2: Parsing extension metadata..."

BUILD_FILE="${SRC_DIR}/build.gradle.kts"

# Write Python metadata extractor to file (avoids bash escaping issues)
cat > "${TEMP_DIR}/extract_metadata.py" << 'PYEOF'
import re, os

build_file = os.environ['BUILD_FILE']
pkg_name = os.environ.get('PKG_NAME', 'unknown')
temp_dir = os.environ['TEMP_DIR']

with open(build_file) as f:
    content = f.read()

m = re.search(r'keiyoushi\s*\{([^}]+)\}', content, re.DOTALL)
block = m.group(1) if m else ''

def get_val(key):
    m2 = re.search(rf'{key}\s*=\s*"([^"]+)"', block)
    if m2: return m2.group(1)
    m2 = re.search(rf'{key}\s*=\s*(\S+)', block)
    if m2: return m2.group(1).strip()
    return ''

def get_int(key):
    v = get_val(key)
    try: return str(int(v))
    except: return '100'

name = get_val('name') or pkg_name
vc = get_int('versionCode')
libv = get_val('libVersion') or '15.0'
nsfw = 'NSFW' in block.upper() or 'MIXED' in block.upper()

out_path = os.path.join(temp_dir, 'extension-metadata.txt')
with open(out_path, 'w') as out:
    out.write(f'EXT_NAME={name}\n')
    out.write(f'VERSION_CODE={vc}\n')
    out.write(f'LIB_VERSION={libv}\n')
    out.write(f'NSFW={str(nsfw).lower()}\n')
print('Metadata extracted successfully')
PYEOF

# Run the extractor
export BUILD_FILE="${SRC_DIR}/build.gradle.kts"
export PKG_NAME="${PKG_NAME}"
export TEMP_DIR="${TEMP_DIR}"
python3 "${TEMP_DIR}/extract_metadata.py" 2>&1

source "${TEMP_DIR}/extension-metadata.txt"

# Determine actual package name from source files
ACTUAL_PKG=$(grep -r '^package ' "${SRC_DIR}/src" 2>/dev/null | head -1 | sed 's/[[:space:]]*package //' | sed 's/[[:space:]]*$//' || echo "")
if [ -z "$ACTUAL_PKG" ]; then
    ACTUAL_PKG="eu.kanade.tachiyomi.extension.${LANG}.${PKG_NAME}"
fi
JAR_NAME="${ACTUAL_PKG}.jar"

# Find the main source class
MAIN_SOURCE=$(grep -rl 'CatalogueSource\|HttpSource\|Source::class' "${SRC_DIR}/src" 2>/dev/null | head -1 || echo "")
if [ -z "$MAIN_SOURCE" ]; then
    MAIN_SOURCE=$(find "${SRC_DIR}/src" -name "*.kt" -exec grep -l 'extends\|: .*Source' {} \; 2>/dev/null | head -1 || echo "")
fi
if [ -z "$MAIN_SOURCE" ]; then
    MAIN_SOURCE=$(find "${SRC_DIR}/src" -name "*.kt" -exec wc -l {} \; 2>/dev/null | sort -rn | head -1 | awk '{print $2}')
fi

if [ -n "$MAIN_SOURCE" ] && [ -f "$MAIN_SOURCE" ]; then
    CLASS_NAME=$(basename "$MAIN_SOURCE" .kt)
    FULL_CLASS_NAME="${ACTUAL_PKG}.${CLASS_NAME}"
else
    CLASS_NAME=$(python3 -c "p='${ACTUAL_PKG}'; seg=p.rsplit('.',1)[-1]; print(seg[0].upper()+seg[1:] if seg else 'Source')")
    FULL_CLASS_NAME="${ACTUAL_PKG}.${CLASS_NAME}"
fi

log "  Name: ${EXT_NAME:-$PKG_NAME}"
log "  Package: ${ACTUAL_PKG}"
log "  Source class: ${FULL_CLASS_NAME}"
log "  Version code: ${VERSION_CODE:-100}"

# ---------------------------------------------------------------------------
# Step 3: Build classpath from Gradle cache
# ---------------------------------------------------------------------------
log ""
log "Step 3: Building classpath..."

CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"
GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

add_to_cp() {
    local item="$1"
    if [ -z "$item" ]; then return; fi
    if [ ! -e "$item" ]; then return; fi
    if echo "$CLASSPATH" | tr ':' '\n' | grep -Fxq "$item"; then
        return
    fi
    CLASSPATH="${CLASSPATH}:${item}"
}

find_dep() {
    local group="$1"
    local artifact="$2"
    local found
    found=$(find "$GRADLE_CACHE" -path "*/${group}/${artifact}/*" -name "${artifact}*.jar" ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | sort -V | tail -1)
    if [ -n "$found" ] && [ -f "$found" ]; then
        add_to_cp "$found"
        return 0
    fi
    return 1
}

# Kotlin stdlib
KOTLIN_LIB=$(brew --prefix kotlin 2>/dev/null || echo "/opt/homebrew/opt/kotlin")
if [ -d "$KOTLIN_LIB/libexec/lib" ]; then
    for j in "$KOTLIN_LIB"/libexec/lib/*.jar; do add_to_cp "$j"; done
    log "  Kotlin stdlib: $(ls "$KOTLIN_LIB"/libexec/lib/*.jar 2>/dev/null | wc -l) JARs"
else
    log "  WARNING: Kotlin lib dir not found at $KOTLIN_LIB"
fi

# Common dependencies
find_dep "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" && log "  coroutines ok"
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-json-jvm" && log "  serialization ok"
find_dep "com.squareup.okhttp3" "okhttp" && log "  okhttp ok"
find_dep "com.squareup.okio" "okio-jvm" && log "  okio ok"
find_dep "org.jsoup" "jsoup" && log "  jsoup ok"
find_dep "io.reactivex" "rxjava" && log "  rxjava ok"
find_dep "com.github.mihonapp" "injekt" && log "  injekt ok"
find_dep "org.jetbrains" "kotlin-reflect" 2>/dev/null && log "  kotlin-reflect ok" || true

log "Classpath: $(echo "$CLASSPATH" | tr ':' '\n' | wc -l) entries"

# ---------------------------------------------------------------------------
# Step 3b: Compile shared library modules (in dependency order)
# ---------------------------------------------------------------------------
log ""
SHARED_LIBS_DIR="${TEMP_DIR}/shared-libs-classes"

# Try compiling shared libs individually (they may have inter-dependencies)
for attempt in 1 2; do
    for lib_dir in "${GIT_CLONE_DIR}"/lib-*/; do
        [ ! -d "$lib_dir" ] && continue
        lib_name=$(basename "$lib_dir")
        lib_classes="${SHARED_LIBS_DIR}/${lib_name}"
        [ -d "$lib_classes" ] && continue  # already compiled

        find "$lib_dir" -name "*.kt" > "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || true
        src_count=$(wc -l < "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || echo 0)
        [ "$src_count" -eq 0 ] && continue

        log "Compiling shared lib: ${lib_name} (attempt ${attempt})..."
        mkdir -p "$lib_classes"

        compiler_output=$({ kotlinc -cp "${CLASSPATH}" -d "$lib_classes" -jvm-target 17 @"${TEMP_DIR}/${lib_name}-sources.txt" 2>&1; } || true)
        class_count=$(find "$lib_classes" -name "*.class" 2>/dev/null | wc -l)

        if [ "$class_count" -gt 0 ]; then
            add_to_cp "$lib_classes"
            log "  -> ${class_count} classes"
        elif [ "$attempt" -eq 2 ]; then
            log "  -> WARNING: no classes (may need deps not in classpath)"
        fi
    done
done

# ---------------------------------------------------------------------------
# Step 4: Compile the extension
# ---------------------------------------------------------------------------
log ""
log "Step 4: Compiling extension..."

CLASSES_DIR="${TEMP_DIR}/classes"
mkdir -p "$CLASSES_DIR"

find "${SRC_DIR}" -name "*.kt" > "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null
KT_COUNT=$(wc -l < "${TEMP_DIR}/kotlin-sources.txt" 2>/dev/null || echo 0)
log "Sources: ${KT_COUNT} Kotlin files"

# Create META-INF/extension.json
mkdir -p "${CLASSES_DIR}/META-INF"
cat > "${CLASSES_DIR}/META-INF/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME:-$PKG_NAME}",
  "pkgName": "${ACTUAL_PKG}",
  "versionName": "1.0.0",
  "versionCode": ${VERSION_CODE:-100},
  "libVersion": ${LIB_VERSION:-15.0},
  "lang": "${LANG}",
  "isNsfw": ${NSFW:-false},
  "isTorrent": false,
  "sourceClass": "${FULL_CLASS_NAME}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

set +e
kotlinc -cp "${CLASSPATH}" -d "${CLASSES_DIR}" -jvm-target 17 @"${TEMP_DIR}/kotlin-sources.txt" 2>"${TEMP_DIR}/compile-err.log"
COMPILE_EXIT=$?
set -e

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
log "Compile exit: ${COMPILE_EXIT}, classes: ${CLASS_COUNT}"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err "Compilation failed:"
    sed 's/^/  /' "${TEMP_DIR}/compile-err.log" 2>/dev/null | head -20
    KEEP_TEMP=true
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 5: Package as JAR
# ---------------------------------------------------------------------------
log ""
log "Step 5: Packaging JAR..."

JAR_PATH="${TEMP_DIR}/${JAR_NAME}"
cd "$CLASSES_DIR"
find . -name '*.class' > "${TEMP_DIR}/class-files.txt"

xargs "${JAR_CMD}" cf "${JAR_PATH}" META-INF/extension.json < "${TEMP_DIR}/class-files.txt" 2>/dev/null || \
    "${JAR_CMD}" cf "${JAR_PATH}" META-INF/extension.json $(find . -name '*.class' 2>/dev/null)

JAR_SIZE=$(stat -f%z "${JAR_PATH}" 2>/dev/null || echo "0")
log "JAR: ${JAR_PATH} (${JAR_SIZE} bytes, ${CLASS_COUNT} classes)"

# ---------------------------------------------------------------------------
# Step 6: Install
# ---------------------------------------------------------------------------
log ""
log "Step 6: Installing..."

mkdir -p "${EXTENSIONS_DIR}"
cp "${JAR_PATH}" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "Installed: ${EXTENSIONS_DIR}/${JAR_NAME}"

REPO_APK_DIR="${PROJECT_DIR}/scripts/output/apk"
if [ -d "$REPO_APK_DIR" ]; then
    cp "${JAR_PATH}" "${REPO_APK_DIR}/${JAR_NAME}"
    log "Copied to repo: ${REPO_APK_DIR}/${JAR_NAME}"
fi

# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD COMPLETE!"
log "║  ${EXT_NAME:-$PKG_NAME} (${ACTUAL_PKG})"
log "║  ${CLASS_COUNT} classes, ${JAR_SIZE} bytes"
log "║  Source class: ${FULL_CLASS_NAME}"
log "╚══════════════════════════════════════════════════════════════╝"
