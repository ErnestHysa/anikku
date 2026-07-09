#!/usr/bin/env bash
#
# batch-build-keiyoushi-from-source.sh
# ======================================
# Batch-builds all yuzono/anime-extensions from source as JVM JARs.
#
# This is the RECOMMENDED approach for making extensions available on macOS.
# Unlike APK→dex2jar conversion, this compiles original Kotlin sources against
# source-api-jvm.jar producing clean JVM bytecode with zero Android references.
#
# Usage:
#   ./batch-build-keiyoushi-from-source.sh
#
# Options:
#   --lang <code>     Build only extensions for this language (default: en)
#   --limit <N>       Build at most N extensions (for testing)
#   --repo <url>      Git repo URL (default: https://github.com/yuzono/anime-extensions.git)
#   --keep-temp       Keep temporary files for debugging
#   --skip-index      Skip generating repo index
#   --help            Show this help
#
# Requirements:
#   - JDK 17+ with kotlinc (brew install kotlin)
#   - git, curl, python3
#   - Anikku source-api JARs (built by Gradle task rebuildSourceApiJars)
#
# Output:
#   - Extension JARs in ~/Library/Application Support/Anikku/extensions/
#   - Repo index in ~/Library/Application Support/Anikku/extensions/macos-source-repo-index.json
#   - Trust store in ~/Library/Application Support/Anikku/data/trust/trusted_extensions.json

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-batch-source-build"
GIT_CLONE_DIR="${TEMP_DIR}/extensions-source"
OUTPUT_DIR="${TEMP_DIR}/output"
BUILD_LOG="${TEMP_DIR}/build-log.txt"

# Default repo: yuzono/anime-extensions (actual anime extension sources)
REPO_URL="${REPO_URL:-https://github.com/yuzono/anime-extensions.git}"

LANG="en"
LIMIT=""
KEEP_TEMP=false
SKIP_INDEX=false

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Batch-build all yuzono/anime-extensions from source as JVM JARs.

Options:
  --lang <code>     Build only extensions for this language (default: en)
  --limit <N>       Build at most N extensions (for testing)
  --repo <url>      Git repo URL
  --keep-temp       Keep temporary files for debugging
  --skip-index      Skip generating repo index
  --help            Show this help
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --lang) LANG="$2"; shift 2 ;;
        --limit) LIMIT="$2"; shift 2 ;;
        --repo) REPO_URL="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --skip-index) SKIP_INDEX=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Prerequisites..."
log "  Extensions dir: ${EXTENSIONS_DIR}"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew rebuildSourceApiJars"
    exit 1
fi
log "  source-api: ${SOURCE_API_JAR}"
log "  common-jvm: ${COMMON_JVM_JAR}"

if ! command -v kotlinc &>/dev/null; then
    err "kotlinc not found. Install: brew install kotlin"
    exit 1
fi
log "  kotlinc: $(which kotlinc)"

# Auto-detect JAVA_HOME
JAVA_HOME=""
for candidate in \
    "${JAVA_HOME}" \
    "/opt/homebrew/opt/openjdk@17" \
    "/opt/homebrew/opt/openjdk@21" \
    "/opt/homebrew/opt/openjdk" \
    "/usr/local/opt/openjdk@17" \
    "/usr/local/opt/openjdk" \
    "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [ -n "$candidate" ] && [ -f "${candidate}/bin/jar" ]; then
        JAVA_HOME="$candidate"
        break
    fi
done
if [ -z "$JAVA_HOME" ]; then
    err "JDK 17+ not found. Install: brew install openjdk@17"
    exit 1
fi
JAR_CMD="${JAVA_HOME}/bin/jar"
log "  jar: ${JAR_CMD}"

# ---------------------------------------------------------------------------
# Step 1: Clone the extensions source repo
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BATCH SOURCE BUILD for language: ${LANG}"
log "╚══════════════════════════════════════════════════════════════╝"
log ""

mkdir -p "${TEMP_DIR}" "${OUTPUT_DIR}"

log "Step 1: Cloning extension sources from ${REPO_URL}..."
if [ -d "${GIT_CLONE_DIR}" ]; then
    log "  Updating existing clone..."
    cd "${GIT_CLONE_DIR}"
    git pull --depth 1 2>&1 | tail -2 || true
else
    git clone --depth 1 --single-branch "${REPO_URL}" "${GIT_CLONE_DIR}" 2>&1 | tail -2
fi

cd "${GIT_CLONE_DIR}"
log "  Repo: $(git remote get-url origin 2>/dev/null || echo 'unknown')"
log "  Commit: $(git rev-parse HEAD 2>/dev/null || echo 'unknown')"

# ---------------------------------------------------------------------------
# Step 2: Find all extensions for the target language
# ---------------------------------------------------------------------------
log ""
log "Step 2: Finding extensions in src/${LANG}/..."

EXT_DIRS=()
while IFS= read -r dir; do
    [ -n "$dir" ] && EXT_DIRS+=("$dir")
done < <(find "src/${LANG}" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort)

TOTAL_COUNT=${#EXT_DIRS[@]}
log "  Found ${TOTAL_COUNT} extension(s) in src/${LANG}/"

if [ "$TOTAL_COUNT" -eq 0 ]; then
    err "No extensions found in src/${LANG}/!"
    err "Available languages: $(ls src/ 2>/dev/null | tr '\n' ' ')"
    exit 1
fi

# Apply limit
if [ -n "$LIMIT" ] && [ "$LIMIT" -gt 0 ] 2>/dev/null; then
    EXT_DIRS=("${EXT_DIRS[@]:0:$LIMIT}")
    log "  Limited to ${LIMIT} extension(s)"
fi

# ---------------------------------------------------------------------------
# Step 2b: Build classpath (shared across all compilations)
# ---------------------------------------------------------------------------
log ""
log "Step 2b: Building shared classpath..."

CLASSPATH="${SOURCE_API_JAR}:${COMMON_JVM_JAR}"
GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"

add_to_cp() {
    local item="$1"
    [ -z "$item" ] && return
    [ ! -e "$item" ] && return
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

# Add macos/libs/ JARs
for j in "${PROJECT_DIR}"/libs/*.jar; do
    [ -f "$j" ] && add_to_cp "$j"
done

# Add compiled macOS module classes (provides Android stubs: android.*, androidx.*)
# These are compiled by the Gradle build and found in the build output directory.
# They provide android.util.Base64, android.os.Handler, android.webkit.WebView, etc.
# which yuzono anime extensions reference.
MACOS_CLASSES_DIR="${PROJECT_DIR}/build/classes/kotlin/main"
if [ -d "$MACOS_CLASSES_DIR" ]; then
    add_to_cp "$MACOS_CLASSES_DIR"
    log "  Android stubs: ${MACOS_CLASSES_DIR} ✓"
else
    log "  WARNING: macOS build classes not found at ${MACOS_CLASSES_DIR}"
    log "  Run './gradlew :compileKotlin' first to build the module"
fi

# Kotlin stdlib from brew
KOTLIN_LIB=$(brew --prefix kotlin 2>/dev/null || echo "/opt/homebrew/opt/kotlin")
if [ -d "$KOTLIN_LIB/libexec/lib" ]; then
    for j in "$KOTLIN_LIB"/libexec/lib/*.jar; do
        add_to_cp "$j"
    done
fi

# Common extension deps
find_dep "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-json-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-core-jvm" || true
find_dep "org.jetbrains.kotlinx" "kotlinx-serialization-protobuf-jvm" || true
find_dep "com.squareup.okhttp3" "okhttp-jvm" || find_dep "com.squareup.okhttp3" "okhttp" || true
find_dep "com.squareup.okio" "okio-jvm" || true
find_dep "org.jsoup" "jsoup" || true
find_dep "io.reactivex" "rxjava" || true
find_dep "com.github.mihonapp" "injekt" || true
# Original injekt API (uy.kohesive.injekt) — needed by core/keiyoushi.utils
find_dep "uy.kohesive.injekt" "injekt-api" || true
find_dep "uy.kohesive.injekt" "injekt-core" || true
find_dep "com.fasterxml.jackson.core" "jackson-core" || true
find_dep "com.fasterxml.jackson.core" "jackson-databind" || true
find_dep "com.google.code.gson" "gson" || true
find_dep "org.jetbrains" "kotlin-reflect" || true

log "  Classpath: $(echo "$CLASSPATH" | tr ':' '\n' | wc -l) entries"

# ---------------------------------------------------------------------------
# Step 3: Compile each extension
# ---------------------------------------------------------------------------
log ""
log "Step 3: Building extensions..."
log ""

# ---------------------------------------------------------------------------
# Step 2c: Compile shared library modules (lib-*, common, core)
# ---------------------------------------------------------------------------
log ""
log "Step 2c: Compiling shared library modules..."

SHARED_LIBS_DIR="${TEMP_DIR}/shared-libs-classes"

for lib_dir in "${GIT_CLONE_DIR}"/lib-*/ "${GIT_CLONE_DIR}/common/" "${GIT_CLONE_DIR}/core/"; do
    [ ! -d "$lib_dir" ] && continue
    lib_name=$(basename "$lib_dir")
    lib_classes="${SHARED_LIBS_DIR}/${lib_name}"
    # Skip only if directory has actual class files (retry if cached empty/broken)
    if [ -d "$lib_classes" ]; then
        cached_classes=$(find "$lib_classes" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        [ "$cached_classes" -gt 0 ] && continue
        rm -rf "$lib_classes"
    fi

    find "$lib_dir" -name "*.kt" > "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || true
    src_count=$(wc -l < "${TEMP_DIR}/${lib_name}-sources.txt" 2>/dev/null || echo 0)
    [ "$src_count" -eq 0 ] && continue

    log "  Compiling: ${lib_name} (${src_count} files)..."
    mkdir -p "$lib_classes"

    set +e
    kotlinc -cp "${CLASSPATH}" -d "$lib_classes" -jvm-target 17 @"${TEMP_DIR}/${lib_name}-sources.txt" 2>"${TEMP_DIR}/${lib_name}-compile.log"
    local_exit=$?
    set -e

    class_count=$(find "$lib_classes" -name "*.class" 2>/dev/null | wc -l)

    if [ "$class_count" -gt 0 ]; then
        add_to_cp "$lib_classes"
        log "    -> ${class_count} classes ✓"
    elif [ "$local_exit" -ne 0 ]; then
        log "    -> SKIP: compilation failed"
    fi
done

# Compile the forked keiyoushi-utils module (pure JVM port from the Android core/ module)
KEIYOUSHI_UTILS_DIR="${PROJECT_DIR}/keiyoushi-utils/src/main/kotlin"
if [ -d "$KEIYOUSHI_UTILS_DIR" ]; then
    UTILS_NAME="keiyoushi-utils"
    UTILS_CLASSES="${SHARED_LIBS_DIR}/${UTILS_NAME}"
    # Only recompile if no cached classes
    if [ -d "$UTILS_CLASSES" ]; then
        cached_classes=$(find "$UTILS_CLASSES" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        [ "$cached_classes" -gt 0 ] && compiled_ok=true || { rm -rf "$UTILS_CLASSES"; compiled_ok=false; }
    else
        compiled_ok=false
    fi

    if [ "$compiled_ok" != true ]; then
        find "$KEIYOUSHI_UTILS_DIR" -name "*.kt" > "${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>/dev/null || true
        utils_src_count=$(wc -l < "${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>/dev/null || echo 0)
        if [ "$utils_src_count" -gt 0 ]; then
            log "  Compiling: ${UTILS_NAME} (${utils_src_count} files, pure JVM port)..."
            mkdir -p "$UTILS_CLASSES"
            set +e
            kotlinc -cp "${CLASSPATH}" -d "$UTILS_CLASSES" -jvm-target 17 @"${TEMP_DIR}/${UTILS_NAME}-sources.txt" 2>"${TEMP_DIR}/${UTILS_NAME}-compile.log"
            utils_exit=$?
            set -e
            utils_class_count=$(find "$UTILS_CLASSES" -name "*.class" 2>/dev/null | wc -l)
            if [ "$utils_class_count" -gt 0 ]; then
                add_to_cp "$UTILS_CLASSES"
                log "    -> ${utils_class_count} classes ✓"
            else
                log "    -> FAILED: $(cat "${TEMP_DIR}/${UTILS_NAME}-compile.log" 2>/dev/null | head -3)"
            fi
        fi
    else
        # Cached from previous run — add to classpath
        add_to_cp "$UTILS_CLASSES"
        cached_count=$(find "$UTILS_CLASSES" -name '*.class' 2>/dev/null | wc -l | tr -d ' ')
        log "  keiyoushi-utils already compiled (${cached_count} cached classes) ✓"
    fi
fi

SUCCESS_COUNT=0
FAIL_COUNT=0
SKIPPED_COUNT=0
SUCCESSFUL_PKGS=""
FAILED_NAMES=""
TRUST_DATA="${TEMP_DIR}/trust-data.json"
echo '[]' > "$TRUST_DATA"

for ext_dir in "${EXT_DIRS[@]}"; do
    EXT_NAME=$(basename "$ext_dir")
    echo ""

    SRC_FILES=$(find "$ext_dir" -name "*.kt" 2>/dev/null)
    SRC_COUNT=$(echo "$SRC_FILES" | wc -l | tr -d ' ')

    if [ "$SRC_COUNT" -eq 0 ]; then
        log "  [SKIP] ${EXT_NAME}: no .kt source files"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Determine package name
    PKG=$(grep -r '^package ' "$ext_dir/src" 2>/dev/null | head -1 | sed 's/[[:space:]]*package //' | sed 's/[[:space:]]*$//' || echo "")
    if [ -z "$PKG" ]; then
        PKG=$(grep -r '^package ' "$ext_dir"/*.kt 2>/dev/null | head -1 | sed 's/[[:space:]]*package //' | sed 's/[[:space:]]*$//' || echo "")
    fi
    if [ -z "$PKG" ]; then
        PKG="eu.kanade.tachiyomi.animeextension.${LANG}.${EXT_NAME}"
    fi
    JAR_NAME="${PKG}.jar"

    # Find main source class
    MAIN_FILE=$(echo "$SRC_FILES" | xargs grep -l 'AnimeHttpSource\|AnimeCatalogueSource\|CatalogueSource' 2>/dev/null | head -1 || echo "")
    if [ -z "$MAIN_FILE" ]; then
        MAIN_FILE=$(echo "$SRC_FILES" | xargs grep -l 'getVideoList\|getEpisodeList' 2>/dev/null | head -1 || echo "")
    fi
    if [ -z "$MAIN_FILE" ]; then
        MAIN_FILE=$(echo "$SRC_FILES" | sort -rn | head -1)
    fi
    CLASS_NAME=$(basename "$MAIN_FILE" .kt)
    FULL_CLASS_NAME="${PKG}.${CLASS_NAME}"

    # Check if extension uses AnimeHttpSource (anime) or HttpSource (manga)
    IS_ANIME=false
    if grep -q 'AnimeHttpSource\|AnimeCatalogueSource\|animesource\.model' "$MAIN_FILE" 2>/dev/null; then
        IS_ANIME=true
    fi

    if [ "$IS_ANIME" = false ]; then
        log "  [SKIP] ${EXT_NAME}: not an anime extension (no AnimeHttpSource reference)"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Check if extension depends on unavailable private libraries
    # aniyomi-lib (private, not available on JitPack) — some extensions use
    # DoodExtractor, StreamWishExtractor, etc. from this package.
    if grep -r 'import aniyomi\.lib\.' "$ext_dir" 2>/dev/null | grep -q .; then
        log "  [SKIP] ${EXT_NAME}: depends on private lib 'aniyomi.lib.*' (not available on JitPack)"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Get version code from build.gradle.kts
    VERSION_CODE="100"
    BUILD_FILE=""
    for candidate in "${ext_dir}/build.gradle.kts" "${ext_dir}/build.gradle"; do
        [ -f "$candidate" ] && BUILD_FILE="$candidate" && break
    done
    if [ -n "$BUILD_FILE" ]; then
        VCODE=$(grep -o 'versionCode\s*=\s*[0-9]*' "$BUILD_FILE" 2>/dev/null | grep -o '[0-9]*' | head -1 || echo "100")
        [ -n "$VCODE" ] && VERSION_CODE="$VCODE"
    fi

    # Build extension
    log "  [$((SUCCESS_COUNT+FAIL_COUNT+1))/$TOTAL_COUNT] ${EXT_NAME} (${PKG})"
    log "    Source files: ${SRC_COUNT}, class: ${FULL_CLASS_NAME}"

    EXT_CLASSES_DIR="${TEMP_DIR}/classes-${EXT_NAME}"
    rm -rf "$EXT_CLASSES_DIR"
    mkdir -p "$EXT_CLASSES_DIR"

    # Create META-INF/extension.json
    mkdir -p "${EXT_CLASSES_DIR}/META-INF"
    cat > "${EXT_CLASSES_DIR}/META-INF/extension.json" << JSONEOF
{
  "name": "Aniyomi: ${EXT_NAME}",
  "pkgName": "${PKG}",
  "versionName": "1.0.0",
  "versionCode": ${VERSION_CODE},
  "libVersion": 15.0,
  "lang": "${LANG}",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "${FULL_CLASS_NAME}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSONEOF

    # Write source file list
    echo "$SRC_FILES" > "${TEMP_DIR}/${EXT_NAME}-sources.txt"

    set +e
    kotlinc -cp "${CLASSPATH}" -d "${EXT_CLASSES_DIR}" -jvm-target 17 @"${TEMP_DIR}/${EXT_NAME}-sources.txt" 2>"${TEMP_DIR}/${EXT_NAME}-compile.log"
    COMPILE_EXIT=$?
    set -e

    CLASS_COUNT=$(find "$EXT_CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)

    if [ "$CLASS_COUNT" -gt 0 ]; then
        # Package JAR
        cd "$EXT_CLASSES_DIR"
        JAR_PATH="${OUTPUT_DIR}/${JAR_NAME}"
        "${JAR_CMD}" cf "${JAR_PATH}" META-INF/extension.json $(find . -name '*.class' 2>/dev/null)

        JAR_SIZE=$(stat -f%z "$JAR_PATH" 2>/dev/null || echo "0")
        log "    ✅ ${JAR_NAME} (${CLASS_COUNT} classes, ${JAR_SIZE} bytes)"

        # Install to extensions directory
        mkdir -p "${EXTENSIONS_DIR}"
        cp "$JAR_PATH" "${EXTENSIONS_DIR}/${JAR_NAME}"

        # Compute SHA-256 for trust store (use Python to build JSON safely)
        if command -v shasum >/dev/null 2>&1; then
            JAR_HASH=$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')
        elif command -v sha256sum >/dev/null 2>&1; then
            JAR_HASH=$(sha256sum "$JAR_PATH" | awk '{print $1}')
        else
            JAR_HASH=""
        fi
        if [ -n "$JAR_HASH" ]; then
            python3 -c "
import json
try:
    with open('${TRUST_DATA}') as f:
        data = json.load(f)
except:
    data = []
data.append({'pkgName': '${PKG}', 'versionCode': ${VERSION_CODE}, 'signatureHash': '${JAR_HASH}'})
with open('${TRUST_DATA}', 'w') as f:
    json.dump(data, f, separators=(',', ':'))
" 2>/dev/null || true
        fi

        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        SUCCESSFUL_PKGS="${SUCCESSFUL_PKGS}\n  ✅ ${JAR_NAME}"
    else
        log "    ❌ Compilation failed (exit=${COMPILE_EXIT})"
        sed 's/^/       /' "${TEMP_DIR}/${EXT_NAME}-compile.log" 2>/dev/null | head -5
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}\n  ❌ ${EXT_NAME}"
    fi
done

# ---------------------------------------------------------------------------
# Step 4: Generate repo index
# ---------------------------------------------------------------------------
if [ "$SKIP_INDEX" = false ] && [ "$SUCCESS_COUNT" -gt 0 ]; then
    log ""
    log "Step 4: Generating repo index..."

    # Generate index.json with relative paths to JARs
    python3 -c "
import os, json

output_dir = '${OUTPUT_DIR}'
extensions_dir = '${EXTENSIONS_DIR}'
entries = []

for fname in os.listdir(output_dir):
    if not fname.endswith('.jar'):
        continue
    jar_path = os.path.join(output_dir, fname)
    if not os.path.isfile(jar_path):
        continue

    # Try to read extension.json from the JAR
    import zipfile
    try:
        with zipfile.ZipFile(jar_path) as zf:
            if 'META-INF/extension.json' in zf.namelist():
                meta = json.loads(zf.read('META-INF/extension.json'))
                entry = {
                    'name': meta.get('name', fname),
                    'pkgName': meta.get('pkgName', fname.replace('.jar', '')),
                    'versionName': meta.get('versionName', '1.0.0'),
                    'versionCode': meta.get('versionCode', 100),
                    'libVersion': meta.get('libVersion', 15.0),
                    'lang': meta.get('lang', ''),
                    'isNsfw': meta.get('isNsfw', False),
                    'isTorrent': meta.get('isTorrent', False),
                    'apk': fname,
                    'sourceClass': meta.get('sourceClass', ''),
                }
                entries.append(entry)
    except Exception:
        pass

with open(os.path.join(output_dir, 'index.min.json'), 'w') as f:
    json.dump(entries, f, separators=(',', ':'))

print('Generated index with ' + str(len(entries)) + ' entries')
" 2>&1 || true

    # Copy index to extensions dir
    cp "${OUTPUT_DIR}/index.min.json" "${EXTENSIONS_DIR}/macos-source-repo-index.json" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Step 5: Write trust store (merge with existing if present)
# ---------------------------------------------------------------------------
TRUST_DIR="${HOME}/Library/Application Support/Anikku/data/trust"
mkdir -p "$TRUST_DIR" 2>/dev/null || true
TRUST_FILE="${TRUST_DIR}/trusted_extensions.json"
if [ -f "$TRUST_DATA" ]; then
    python3 -c "
import json, os

new_path = '${TRUST_DATA}'
old_path = '${TRUST_FILE}'

with open(new_path) as f:
    new_entries = json.load(f)

# Merge with existing trust store if present
existing = []
if os.path.exists(old_path):
    try:
        with open(old_path) as f:
            existing = json.load(f)
    except:
        pass

# Build index by (pkgName, versionCode) to avoid duplicates
seen = set()
merged = []
for entry in existing + new_entries:
    key = (entry.get('pkgName', ''), entry.get('versionCode', 0))
    if key not in seen:
        seen.add(key)
        merged.append(entry)

with open(old_path, 'w') as f:
    json.dump(merged, f, separators=(',', ':'))

print(str(len(merged)))
" 2>&1
    TRUSTED_COUNT=$(python3 -c "import json; print(len(json.load(open('${TRUST_FILE}'))))" 2>/dev/null || echo "0")
    log "  Trusted ${TRUSTED_COUNT} extension(s) → ${TRUST_FILE}"
else
    log "  No trust data to write"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "╔══════════════════════════════════════════════════════════════╗"
log "║  BUILD COMPLETE"
log "╚══════════════════════════════════════════════════════════════╝"
log "  Language:       ${LANG}"
log "  Total:          ${TOTAL_COUNT}"
log "  ✅ Built:       ${SUCCESS_COUNT}"
log "  ❌ Failed:       ${FAIL_COUNT}"
log "  ⏭️  Skipped:    ${SKIPPED_COUNT}"
log ""
log "  Extensions installed to:"
log "    ${EXTENSIONS_DIR}"
log ""
log "  Repo index:"
log "    ${EXTENSIONS_DIR}/macos-source-repo-index.json"
log ""
if [ -n "$FAILED_NAMES" ]; then
    log "  Failed:${FAILED_NAMES}"
fi
log ""
log "  To add local repo to the app:"
log "    Browse → Extensions → Repos tab"
log "    Add: file://${EXTENSIONS_DIR}/macos-source-repo-index.json"
log "    Fetch → Install desired extensions"
log ""

# Cleanup
if [ "$KEEP_TEMP" != "true" ]; then
    log "Cleaning up temporary files..."
    rm -rf "${TEMP_DIR}"
fi

exit $FAIL_COUNT
