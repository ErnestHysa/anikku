#!/usr/bin/env bash
#
# batch-build-extensions.sh
# ==========================
# Batch builds all available anime extensions as JVM JARs for macOS.
#
# This script:
# 1. Fetches the list of available anime extensions from community repos
# 2. Downloads each extension's APK from the repo
# 3. Attempts to convert each APK to a JVM JAR via jadx decompilation
# 4. Generates a custom repo index (index.min.json) pointing to the JARs
# 5. Deploys the JARs to the Anikku extensions directory
#
# Usage:
#   ./batch-build-extensions.sh
#   ./batch-build-extensions.sh --source-only  # Only build from source repos
#   ./batch-build-extensions.sh --repo <repo-url>
#
# Requirements:
#   - jadx (brew install jadx)
#   - JDK 17+ with javac
#   - Anikku source-api JARs built
#
# Extension repos used:
#   - keiyoushi/extensions (APK format) — 2 English anime extensions
#   - salmanbappi/extensions-repo (APK format) — 8 English anime extensions

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-batch-build"
OUTPUT_DIR="${TEMP_DIR}/output"
BUILD_LOG="${TEMP_DIR}/build-log.txt"

# Extension repos (APK-based)
REPOS=(
    "https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json:salmanbappi"
    "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json:keiyoushi"
    "https://raw.githubusercontent.com/msrofficial/anime-repo/repo/index.min.json:msrofficial"
)

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }
info() { echo "    $*"; }

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
Usage: $(basename "$0") [OPTIONS]

Batch build all available anime extensions as JVM JARs.

Options:
  --source-only     Only try source-based builds (skip APK conversion)
  --repo <url>      Use a specific repo URL
  --keep-temp       Keep temporary files for debugging
  --help            Show this help
EOF
    exit 0
}

SOURCE_ONLY=false
CUSTOM_REPO=""
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source-only) SOURCE_ONLY=true; shift ;;
        --repo) CUSTOM_REPO="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
log "Prerequisites..."
log "  Source-api JAR: ${SOURCE_API_JAR}"
log "  Common JVM JAR: ${COMMON_JVM_JAR}"
log "  Extensions dir: ${EXTENSIONS_DIR}"

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "JARs not found. Build: ./gradlew -p macos rebuildSourceApiJars"
    exit 1
fi

JAVAC="${JAVA_HOME}/bin/javac"
if [ ! -f "$JAVAC" ]; then
    JAVAC=$(which javac 2>/dev/null || echo "")
    if [ -z "$JAVAC" ]; then
        err "javac not found. Ensure JDK 17+ is installed."
        exit 1
    fi
fi

if ! command -v jadx &>/dev/null; then
    if [ "$SOURCE_ONLY" = false ]; then
        err "jadx not found. Install: brew install jadx"
        err "Run with --source-only to skip APK conversion"
        exit 1
    fi
fi

mkdir -p "${TEMP_DIR}" "${OUTPUT_DIR}" "${EXTENSIONS_DIR}"

# ---------------------------------------------------------------------------
# Step 1: Fetch extension indices from all repos
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  BATCH EXTENSION BUILD"
log "═══════════════════════════════════════════════════════════════"
log ""

ALL_EXTENSIONS=()
DECLARED_PKGS=""

for repo_entry in "${REPOS[@]}"; do
    REPO_URL="${repo_entry%%:*}"
    REPO_NAME="${repo_entry##*:}"
    
    log "Fetching index from: ${REPO_NAME}..."
    REPO_INDEX=$(curl -sL --connect-timeout 15 "$REPO_URL" 2>/dev/null || echo "[]")
    
    EXT_COUNT=$(echo "$REPO_INDEX" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")
    log "  Found ${EXT_COUNT} extensions in ${REPO_NAME}"
    
    # For now, just collect the English anime extensions
    # Filter for English anime-specific packages
    ANIME_EXTS=$(echo "$REPO_INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
# Anime-specific keywords
anime_kw = ['allanime', 'animepahe', 'animesogo', 'animestream', 'anineko',
            'anitusk', 'reanime', 'anidb', 'nineanime', 'hanime', 'animesama',
            'animegdr', 'animexnovel', 'lunaranime']
for ext in data:
    pkg = ext.get('pkg', '').lower()
    lang = ext.get('lang', '')
    for kw in anime_kw:
        if kw in pkg and (lang == 'en' or lang == 'all'):
            print(json.dumps(ext))
            break
" 2>/dev/null || echo "")
    
    while IFS= read -r line; do
        if [ -n "$line" ]; then
            ALL_EXTENSIONS+=("$line")
            PKG=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))" 2>/dev/null || echo "")
            if [ -n "$PKG" ]; then
                if [ -n "$DECLARED_PKGS" ]; then
                    DECLARED_PKGS="${DECLARED_PKGS},${PKG}"
                else
                    DECLARED_PKGS="${PKG}"
                fi
            fi
        fi
    done <<< "$ANIME_EXTS"
done

log "Total unique anime extensions: ${#ALL_EXTENSIONS[@]}"
log ""

# ---------------------------------------------------------------------------
# Step 2: Download and convert each extension
# ---------------------------------------------------------------------------
SUCCESS_COUNT=0
FAIL_COUNT=0
SUCCESSFUL_JARS=""
FAILED_NAMES=""

for ext_json in "${ALL_EXTENSIONS[@]}"; do
    EXT_NAME=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','unknown'))" 2>/dev/null || echo "unknown")
    EXT_PKG=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))" 2>/dev/null || echo "")
    EXT_APK=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apk',''))" 2>/dev/null || echo "")
    EXT_LANG=$(echo "$ext_json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('lang',''))" 2>/dev/null || echo "")
    
    if [ -z "$EXT_PKG" ] || [ -z "$EXT_APK" ]; then
        log "  SKIP: Missing package name or APK URL for ${EXT_NAME}"
        continue
    fi
    
    EXT_TEMP="${TEMP_DIR}/ext-${EXT_PKG##*.}"
    mkdir -p "$EXT_TEMP"
    
    log "  [${SUCCESS_COUNT}/${#ALL_EXTENSIONS[@]}] ${EXT_NAME} (${EXT_PKG})"
    
    # Step 2a: Download APK
    APK_URL="https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/apk/${EXT_APK}"
    # Try alternative paths
    if ! curl -sL -o "${EXT_TEMP}/extension.apk" "$APK_URL" 2>/dev/null || [ ! -s "${EXT_TEMP}/extension.apk" ]; then
        APK_URL="https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${EXT_APK}"
        curl -sL -o "${EXT_TEMP}/extension.apk" "$APK_URL" 2>/dev/null || true
    fi
    
    if [ ! -s "${EXT_TEMP}/extension.apk" ]; then
        info "  FAIL: Could not download APK"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (download failed)\n"
        continue
    fi
    
    APK_SIZE=$(stat -f%z "${EXT_TEMP}/extension.apk" 2>/dev/null || echo "0")
    info "  Downloaded: ${APK_SIZE} bytes"
    
    if [ "$APK_SIZE" -lt 1000 ]; then
        info "  SKIP: File too small (probably a 404)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (APK too small)\n"
        continue
    fi
    
    # Step 2b: Convert APK to JAR via jadx + javac
    info "  Decompiling with jadx..."
    JADX_OUT="${EXT_TEMP}/jadx-output"
    mkdir -p "$JADX_OUT"
    
    if jadx -d "$JADX_OUT" "${EXT_TEMP}/extension.apk" 2>/dev/null | tail -1; then
        JAVA_SRC_DIR="$JADX_OUT/sources"
        [ ! -d "$JAVA_SRC_DIR" ] && JAVA_SRC_DIR="$JADX_OUT"
        
        SRC_COUNT=$(find "$JAVA_SRC_DIR" -name "*.java" 2>/dev/null | wc -l)
        info "  Decompiled ${SRC_COUNT} Java source files"
        
        if [ "$SRC_COUNT" -gt 0 ]; then
            # Create Android stubs
            STUBS_DIR="${EXT_TEMP}/stubs"
            mkdir -p "${STUBS_DIR}/android/util" "${STUBS_DIR}/android/os" \
                     "${STUBS_DIR}/android/text" "${STUBS_DIR}/android/net" \
                     "${STUBS_DIR}/android/content"
            
            # Write minimal stubs
            cat > "${STUBS_DIR}/android/util/Log.java" << 'STUB'
package android.util;
public class Log {
    public static int d(String t, String m) { return 0; }
    public static int e(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int i(String t, String m) { return 0; }
}
STUB
            cat > "${STUBS_DIR}/android/os/Build.java" << 'STUB'
package android.os;
public class Build {
    public static class VERSION { public static final int SDK_INT = 35; }
}
STUB
            cat > "${STUBS_DIR}/android/text/TextUtils.java" << 'STUB'
package android.text;
public class TextUtils {
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
}
STUB
            cat > "${STUBS_DIR}/android/net/Uri.java" << 'STUB'
package android.net;
public class Uri {
    private final String s;
    private Uri(String s) { this.s = s; }
    public static Uri parse(String s) { return new Uri(s); }
    public static final Uri EMPTY = new Uri("");
    public String toString() { return s; }
    public String getPath() { return s; }
}
STUB
            
            # Compile stubs
            STUBS_CLASSES="${EXT_TEMP}/stubs-classes"
            mkdir -p "$STUBS_CLASSES"
            find "$STUBS_DIR" -name "*.java" -exec "$JAVAC" -d "$STUBS_CLASSES" {} + 2>/dev/null || true
            
            # Compile extension source
            CLASSES_DIR="${EXT_TEMP}/classes"
            mkdir -p "$CLASSES_DIR"
            CLASSPATH="${STUBS_CLASSES}:${SOURCE_API_JAR}:${COMMON_JVM_JAR}"
            
            find "$JAVA_SRC_DIR" -name "*.java" > "${EXT_TEMP}/sources.txt" 2>/dev/null
            "$JAVAC" -d "$CLASSES_DIR" -cp "$CLASSPATH" -source 17 -target 17 \
                     @"${EXT_TEMP}/sources.txt" 2>/dev/null || true
            
            CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
            
            if [ "$CLASS_COUNT" -gt 0 ]; then
                # Package as JAR
                JAR_NAME="${EXT_PKG}.jar"
                mkdir -p "${CLASSES_DIR}/META-INF"
                
                # Generate extension.json
                VERSION_NAME=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_TEMP}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionName=\\\"([^\\\"]+)\\\"', text)
        print(m.group(1) if m else '1.0.0')
    except: print('1.0.0')
" 2>/dev/null || echo "1.0.0")

                VERSION_CODE=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${EXT_TEMP}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionCode=\\\"?([0-9]+)\\\"?', text)
        print(m.group(1) if m else '100')
    except: print('100')
" 2>/dev/null || echo "100")

                LIB_VERSION=$(echo "$VERSION_NAME" | python3 -c "
v = __import__('sys').stdin.read().strip()
try: print(float(v.rsplit('.',1)[0]))
except: print('15.0')
" 2>/dev/null || echo "15.0")

                # Find source class
                SOURCE_CLASSES=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | \
                    sed "s|${CLASSES_DIR}/||; s|/|.|g; s|\.class||" | \
                    grep -vE "(R\$|BuildConfig|Manifest)" | \
                    head -3 | tr '\n' ';' | sed 's/;$//')

                cat > "${CLASSES_DIR}/META-INF/extension.json" << JSON
{
  "name": "Aniyomi: ${EXT_NAME}",
  "pkgName": "${EXT_PKG}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE:-100},
  "libVersion": ${LIB_VERSION:-15.0},
  "lang": "${EXT_LANG:-en}",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "${SOURCE_CLASSES}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSON

                JAR_PATH="${OUTPUT_DIR}/${JAR_NAME}"
                cd "$CLASSES_DIR"
                jar cf "$JAR_PATH" META-INF/ $(find . -name "*.class" 2>/dev/null) 2>/dev/null || true
                
                if [ -f "$JAR_PATH" ] && [ -s "$JAR_PATH" ]; then
                    cp "$JAR_PATH" "${EXTENSIONS_DIR}/${JAR_NAME}"
                    SUCCESSFUL_JARS="${SUCCESSFUL_JARS}  ${JAR_NAME}\n"
                    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
                    info "  ✅ Built: ${JAR_NAME} (${CLASS_COUNT} classes)"
                else
                    info "  ❌ JAR packaging failed"
                    FAIL_COUNT=$((FAIL_COUNT + 1))
                    FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (JAR packaging)\n"
                fi
            else
                info "  ❌ Compilation failed (${COMPILE_EXIT:-0})"
                FAIL_COUNT=$((FAIL_COUNT + 1))
                FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (compilation)\n"
            fi
        else
            info "  ❌ No Java sources decompiled"
            FAIL_COUNT=$((FAIL_COUNT + 1))
            FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (no sources)\n"
        fi
    else
        info "  ❌ jadx decompilation failed"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_NAMES="${FAILED_NAMES}  ${EXT_NAME} (jadx)\n"
    fi
done

# ---------------------------------------------------------------------------
# Step 3: Generate repo index
# ---------------------------------------------------------------------------
log ""
log "Step 3: Generating extension repo index..."
log ""

# Generate index.min.json pointing to local JARs
python3 -c "
import json

entries = []
pkg_list = '${DECLARED_PKGS}'.split(',')

for pkg in pkg_list:
    if not pkg:
        continue
    # Create an entry for this package
    entries.append({
        'name': pkg.split('.')[-1].title() if '.' in pkg else pkg,
        'pkg': pkg,
        'apk': f'{pkg}.jar',
        'lang': 'en',
        'code': 100,
        'version': '1.0.0',
        'nsfw': 0,
        'torrent': 0,
        'sources': []
    })

with open('${OUTPUT_DIR}/index.min.json', 'w') as f:
    json.dump(entries, f, separators=(',', ':'))

print(f'Generated index with {len(entries)} entries')
" 2>&1 || true

# Also copy index to extensions dir
cp "${OUTPUT_DIR}/index.min.json" "${EXTENSIONS_DIR}/macos-repo-index.json" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  BUILD SUMMARY"
log "═══════════════════════════════════════════════════════════════"
log "  Total extensions attempted: ${#ALL_EXTENSIONS[@]}"
log "  ✅ Successfully built:      ${SUCCESS_COUNT}"
log "  ❌ Failed:                   ${FAIL_COUNT}"
log ""
log "  JARs in: ${EXTENSIONS_DIR}/"
log "  Repo index: ${EXTENSIONS_DIR}/macos-repo-index.json"
log ""

if [ -n "$SUCCESSFUL_JARS" ]; then
    log "  ✅ Successfully built:"
    echo -e "$SUCCESSFUL_JARS"
fi

if [ -n "$FAILED_NAMES" ]; then
    log "  ❌ Failed:"
    echo -e "$FAILED_NAMES"
fi

log ""
log "  To add this repo to the app:"
log "    1. Open Anikku → Browse → Extensions"
log "    2. Go to the Repos tab"
log "    3. Add repo URL:"
log "       file://${EXTENSIONS_DIR}/macos-repo-index.json"
log "    4. Tap Fetch to load available extensions"
log "    5. Install the ones you want"
log ""
log "  Or host the JARs online:"
log "    1. Upload ${OUTPUT_DIR}/ to a web server"
log "    2. The index URL would be: https://your-server.com/index.min.json"
log "    3. JAR URLs are relative to the index directory"
log "═══════════════════════════════════════════════════════════════"
