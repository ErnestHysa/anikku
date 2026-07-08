#!/usr/bin/env bash
#
# convert-keiyoushi-extension.sh
# ==============================
# Converts a keiyoushi Android APK extension to a macOS-compatible JAR.
#
# This script:
# 1. Downloads a keiyoushi extension APK (or uses an existing one)
# 2. Extracts extension metadata from the APK
# 3. Decompiles DEX bytecode to Java source using jadx
# 4. Patches Android API references to JVM equivalents
# 5. Compiles the Java source with javac against source-api JARs
# 6. Packages everything into a JAR with META-INF/extension.json
# 7. Places the JAR in the Anikku extensions directory
#
# Usage:
#   ./convert-keiyoushi-extension.sh [--apk <path-to-apk>] [--pkg <pkg-name>] [--url <download-url>]
#
# Examples:
#   # Download and convert the first English extension from keiyoushi repo
#   ./convert-keiyoushi-extension.sh
#
#   # Convert an existing APK file
#   ./convert-keiyoushi-extension.sh --apk ~/Downloads/gogocdn.apk
#
#   # Convert with explicit package name and download URL
#   ./convert-keiyoushi-extension.sh --pkg gogocdn
#
# Requirements:
# - jadx (brew install jadx)
# - JDK 17+ (with javac)
# - curl, unzip, python3
# - The Anikku source-api JARs (macos/libs/source-api-jvm.jar, macos/libs/common-jvm.jar)
#
# Notes:
# - Decompiled code may contain Android API references that won't compile.
#   The script attempts basic patching but some extensions may fail to build.
# - Obfuscated extensions (most keiyoushi ones) produce decompiled code with
#   meaningless class names. The script still attempts to compile them.
# - For production use, extensions should be compiled from source as JVM bytecode
#   using the approach described in macos/docs/MIGRATION-GUIDE.md.

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EXTENSIONS_DIR="${HOME}/Library/Application Support/Anikku/extensions"
TEMP_DIR="/tmp/anikku-extension-convert"

KEIYOUSHI_INDEX="https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
KEIYOUSHI_APK_BASE="https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk"

SOURCE_API_JAR="${PROJECT_DIR}/libs/source-api-jvm.jar"
COMMON_JVM_JAR="${PROJECT_DIR}/libs/common-jvm.jar"

# Android API stubs — we create minimal stub interfaces so decompiled code can compile
# These replace android.* references with JVM-compatible no-ops

# ──────────────────────────────────────────────────────────────────────────────
# Functions
# ──────────────────────────────────────────────────────────────────────────────

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

cleanup() {
    log "Cleaning up temporary files..."
    rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Convert a keiyoushi APK extension to a macOS JAR extension.

Options:
  --apk <path>    Path to an existing APK file
  --pkg <name>    Package name of the extension to download (e.g., gogocdn)
  --lang <code>   Language filter for auto-download (default: en)
  --keep-temp     Keep temporary files for debugging
  --help          Show this help
EOF
    exit 0
}

# ──────────────────────────────────────────────────────────────────────────────
# Parse arguments
# ──────────────────────────────────────────────────────────────────────────────

APK_PATH=""
PKG_NAME=""
LANG_FILTER="en"
KEEP_TEMP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk) APK_PATH="$2"; shift 2 ;;
        --pkg) PKG_NAME="$2"; shift 2 ;;
        --lang) LANG_FILTER="$2"; shift 2 ;;
        --keep-temp) KEEP_TEMP=true; shift ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

# ──────────────────────────────────────────────────────────────────────────────
# Prerequisites check
# ──────────────────────────────────────────────────────────────────────────────

log "Checking prerequisites..."

if ! command -v jadx &>/dev/null; then
    err "jadx is required. Install with: brew install jadx"
    exit 1
fi

if ! command -v javac &>/dev/null && [ ! -f "${JAVA_HOME}/bin/javac" ]; then
    err "JDK 17+ with javac is required."
    err "Set JAVA_HOME to a JDK 17 installation."
    exit 1
fi

JAVAC="${JAVAC:-${JAVA_HOME}/bin/javac}"
JAVA="${JAVA:-${JAVA_HOME}/bin/java}"

if [ ! -f "$SOURCE_API_JAR" ] || [ ! -f "$COMMON_JVM_JAR" ]; then
    err "source-api JARs not found at ${PROJECT_DIR}/libs/"
    err "Build them first: cd ${PROJECT_DIR} && ./gradlew -p macos rebuildSourceApiJars"
    exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 1: Get the APK
# ──────────────────────────────────────────────────────────────────────────────

mkdir -p "${TEMP_DIR}"

if [ -n "$APK_PATH" ]; then
    log "Using existing APK: ${APK_PATH}"
    if [ ! -f "$APK_PATH" ]; then
        err "APK file not found: ${APK_PATH}"
        exit 1
    fi
    cp "$APK_PATH" "${TEMP_DIR}/extension.apk"
else
    log "Fetching keiyoushi extension index..."
    INDEX=$(curl -sL "$KEIYOUSHI_INDEX")

    if [ -z "$INDEX" ]; then
        err "Failed to fetch keiyoushi index"
        exit 1
    fi

    log "Finding extension${PKG_NAME:+ matching '$PKG_NAME'}${LANG_FILTER:+ (lang: $LANG_FILTER)}..."

    EXT_JSON=$(echo "$INDEX" | python3 -c "
import sys, json
data = json.load(sys.stdin)
pkg_filter = '${PKG_NAME}'.lower()
lang_filter = '${LANG_FILTER}'
for ext in data:
    name = ext.get('pkg', '').lower()
    lang = ext.get('lang', '')
    if pkg_filter and pkg_filter not in name:
        continue
    if lang_filter and lang != lang_filter:
        continue
    if pkg_filter or lang_filter:
        print(json.dumps(ext))
        break
    if lang == 'en':
        print(json.dumps(ext))
        break
if not pkg_filter and not lang_filter:
    # First extension in the list
    if data:
        print(json.dumps(data[0]))
" 2>/dev/null)

    if [ -z "$EXT_JSON" ]; then
        err "No matching extension found in keiyoushi index"
        exit 1
    fi

    EXT_NAME=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','unknown'))")
    EXT_PKG=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pkg',''))")
    EXT_APK=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apk',''))")
    EXT_LANG=$(echo "$EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('lang',''))")

    log "Found: ${EXT_NAME} (${EXT_PKG}, lang: ${EXT_LANG})"

    APK_URL="${KEIYOUSHI_APK_BASE}/${EXT_APK}"
    log "Downloading from: ${APK_URL}"
    curl -sL --connect-timeout 30 -o "${TEMP_DIR}/extension.apk" "$APK_URL"
    FILE_SIZE=$(wc -c < "${TEMP_DIR}/extension.apk")
    log "Downloaded ${FILE_SIZE} bytes"

    if [ "$FILE_SIZE" -lt 1000 ]; then
        err "Downloaded file too small (${FILE_SIZE} bytes) — probably a 404"
        cat "${TEMP_DIR}/extension.apk"
        exit 1
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 2: Extract metadata from APK
# ──────────────────────────────────────────────────────────────────────────────

log "Extracting extension metadata from APK..."

# Extract AndroidManifest.xml and parse metadata
unzip -o "${TEMP_DIR}/extension.apk" AndroidManifest.xml -d "${TEMP_DIR}" 2>/dev/null || true

# Try to extract metadata values using aapt2 or fallback to Python parsing
# For now, use jadx to extract the extension.json-style metadata
# We read the keiyoushi index values since AndroidManifest.xml is binary

# Extract metadata from the keiyoushi index
if [ -z "${EXT_JSON:-}" ]; then
    # Try to extract metadata from the APK itself using jadx
    jadx -e "${TEMP_DIR}/extension.apk" 2>/dev/null | head -5 || true
fi

# Parse AndroidManifest binary XML to extract version info
# Use Python's androguard or just read the raw strings from the APK
log "Extracting version info from APK..."
strings "${TEMP_DIR}/extension.apk" | grep -E "^[0-9]+\\.[0-9]+(\\.[0-9]+)?$" | head -5

# ──────────────────────────────────────────────────────────────────────────────
# Step 3: Decompile DEX to Java source using jadx
# ──────────────────────────────────────────────────────────────────────────────

log "Decompiling APK with jadx..."
mkdir -p "${TEMP_DIR}/jadx-output"
jadx -d "${TEMP_DIR}/jadx-output" "${TEMP_DIR}/extension.apk" 2>&1 | tail -3

JAVA_SRC_DIR="${TEMP_DIR}/jadx-output/sources"
if [ ! -d "$JAVA_SRC_DIR" ]; then
    JAVA_SRC_DIR="${TEMP_DIR}/jadx-output"
fi

SRC_COUNT=$(find "$JAVA_SRC_DIR" -name "*.java" 2>/dev/null | wc -l)
log "Decompiled ${SRC_COUNT} Java source files"

if [ "$SRC_COUNT" -eq 0 ]; then
    err "No Java source files were decompiled"
    exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 4: Patch Android API references
# ──────────────────────────────────────────────────────────────────────────────

log "Patching Android API references..."

# Create Android stub classes
STUB_DIR="${TEMP_DIR}/android-stubs"
mkdir -p "${STUB_DIR}/android/util"
mkdir -p "${STUB_DIR}/android/content"
mkdir -p "${STUB_DIR}/android/net"
mkdir -p "${STUB_DIR}/android/os"
mkdir -p "${STUB_DIR}/android/text"
mkdir -p "${STUB_DIR}/android/graphics"
mkdir -p "${STUB_DIR}/java/io"

# Create android.util.Log stub
cat > "${STUB_DIR}/android/util/Log.java" << 'ANDROID_STUB'
package android.util;

public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static int v(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
    public static String getStackTraceString(Throwable tr) { return ""; }
}
ANDROID_STUB

# Create android.os.Build stub
cat > "${STUB_DIR}/android/os/Build.java" << 'ANDROID_STUB'
package android.os;

public class Build {
    public static final String VERSION_CODES = "";
    public static class VERSION {
        public static final String RELEASE = "15";
        public static final int SDK_INT = 35;
        public static final String CODENAME = "REL";
    }
    public static class VERSION_CODES {
        public static final int BASE = 1;
        public static final int JELLY_BEAN = 16;
        public static final int KITKAT = 19;
        public static final int LOLLIPOP = 21;
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 26;
        public static final int P = 28;
    }
    public static final String BRAND = "generic";
    public static final String MODEL = "Anikku-macOS";
    public static final String MANUFACTURER = "unknown";
    public static final String DEVICE = "generic";
}
ANDROID_STUB

# Create android.text.TextUtils stub
cat > "${STUB_DIR}/android/text/TextUtils.java" << 'ANDROID_STUB'
package android.text;

public class TextUtils {
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }
    public static boolean isBlank(CharSequence str) {
        return str == null || str.toString().trim().isEmpty();
    }
    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Object token : tokens) {
            if (sb.length() > 0) sb.append(delimiter);
            sb.append(token);
        }
        return sb.toString();
    }
}
ANDROID_STUB

# Create android.net.Uri stub
cat > "${STUB_DIR}/android/net/Uri.java" << 'ANDROID_STUB'
package android.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Uri {
    private final String uriString;

    private Uri(String uri) { this.uriString = uri; }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    public static Uri EMPTY = new Uri("");

    public String getScheme() {
        int idx = uriString.indexOf(':');
        return idx > 0 ? uriString.substring(0, idx) : "";
    }

    public String getHost() {
        try {
            java.net.URL url = new java.net.URL(uriString);
            return url.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    public String getPath() {
        try {
            java.net.URL url = new java.net.URL(uriString);
            return url.getPath();
        } catch (Exception e) {
            return "";
        }
    }

    public String getQuery() {
        try {
            java.net.URL url = new java.net.URL(uriString);
            return url.getQuery();
        } catch (Exception e) {
            return "";
        }
    }

    public String getQueryParameter(String key) {
        String query = getQuery();
        if (query == null || query.isEmpty()) return null;
        Pattern p = Pattern.compile(key + "=([^&]+)");
        Matcher m = p.matcher(query);
        return m.find() ? m.group(1) : null;
    }

    public static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (UnsupportedEncodingException e) { return s; }
    }

    public static String decode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); }
        catch (UnsupportedEncodingException e) { return s; }
    }

    public String toString() { return uriString; }

    public static class Builder {
        private StringBuilder sb = new StringBuilder();
        public Builder scheme(String scheme) { sb.append(scheme).append("://"); return this; }
        public Builder authority(String authority) { sb.append(authority); return this; }
        public Builder path(String path) { sb.append(path); return this; }
        public Builder appendQueryParameter(String key, String value) {
            sb.append(sb.indexOf("?") > 0 ? '&' : '?').append(key).append('=').append(value);
            return this;
        }
        public Builder fragment(String fragment) { sb.append('#').append(fragment); return this; }
        public Uri build() { return new Uri(sb.toString()); }
    }

    public static Builder buildUpon() { return new Builder(); }
}
ANDROID_STUB

# Create android.content.Context stub (minimal)
cat > "${STUB_DIR}/android/content/Context.java" << 'ANDROID_STUB'
package android.content;

public class Context {
    public static final int MODE_PRIVATE = 0;
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return new SharedPreferences();
    }
}
ANDROID_STUB

# Create android.content.SharedPreferences stub
cat > "${STUB_DIR}/android/content/SharedPreferences.java" << 'ANDROID_STUB'
package android.content;

import java.util.*;

public class SharedPreferences {
    public interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putFloat(String key, float value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }

    public String getString(String key, String defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public boolean contains(String key) { return false; }
    public Map<String, ?> getAll() { return new HashMap<>(); }
    public Editor edit() { return null; }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    public interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences prefs, String key);
    }
}
ANDROID_STUB

# Create android.os.Bundle stub
cat > "${STUB_DIR}/android/os/Bundle.java" << 'ANDROID_STUB'
package android.os;

import java.util.*;

public class Bundle {
    private Map<String, Object> map = new HashMap<>();
    public Bundle() {}
    public void putString(String key, String value) { map.put(key, value); }
    public void putInt(String key, int value) { map.put(key, value); }
    public void putLong(String key, long value) { map.put(key, value); }
    public void putBoolean(String key, boolean value) { map.put(key, value); }
    public void putSerializable(String key, java.io.Serializable value) { map.put(key, value); }
    public String getString(String key) { return (String) map.get(key); }
    public String getString(String key, String defValue) { return (String) map.getOrDefault(key, defValue); }
    public int getInt(String key, int defValue) { return (int) map.getOrDefault(key, defValue); }
    public long getLong(String key, long defValue) { return (long) map.getOrDefault(key, defValue); }
    public boolean getBoolean(String key, boolean defValue) { return (boolean) map.getOrDefault(key, defValue); }
    public boolean containsKey(String key) { return map.containsKey(key); }
    public Set<String> keySet() { return map.keySet(); }
}
ANDROID_STUB

# Stub for other common Android references
mkdir -p "${STUB_DIR}/android/provider"
cat > "${STUB_DIR}/android/provider/Browser.java" << 'ANDROID_STUB'
package android.provider;
public class Browser {
    public static final String EXTRA_HEADERS = "extra_headers";
}
ANDROID_STUB

# Compile Android stubs
log "Compiling Android API stubs..."
STUB_CLASSES="${TEMP_DIR}/stubs"
mkdir -p "$STUB_CLASSES"
find "${STUB_DIR}" -name "*.java" -exec \
    "$JAVAC" -d "$STUB_CLASSES" {} + 2>&1 | tail -5
log "Android stubs compiled"

# ──────────────────────────────────────────────────────────────────────────────
# Step 5: Compile the decompiled Java source
# ──────────────────────────────────────────────────────────────────────────────

log "Compiling decompiled Java source..."
CLASSES_DIR="${TEMP_DIR}/classes"
mkdir -p "$CLASSES_DIR"

# Build classpath: stubs + source-api + common-jvm
CLASSPATH="${STUB_CLASSES}:${SOURCE_API_JAR}:${COMMON_JVM_JAR}"

# Find all Java source files (there may be many)
find "$JAVA_SRC_DIR" -name "*.java" > "${TEMP_DIR}/sources.txt"
SRC_COUNT=$(wc -l < "${TEMP_DIR}/sources.txt")
log "Found ${SRC_COUNT} Java source files to compile"

# Compile (may fail for obfuscated code — that's expected)
"$JAVAC" \
    -d "$CLASSES_DIR" \
    -cp "$CLASSPATH" \
    -source 17 -target 17 \
    @ "${TEMP_DIR}/sources.txt" 2>&1 | tail -20

CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
log "Compilation produced ${CLASS_COUNT} .class files"

if [ "$CLASS_COUNT" -eq 0 ]; then
    err "Compilation failed — no .class files produced"
    err "The decompiled extension source may be obfuscated or contain Android-specific code"
    err "that cannot be patched automatically."
    err ""
    err "Alternative: Build the extension from source as JVM bytecode."
    err "See macos/docs/MIGRATION-GUIDE.md for details."
    exit 1
fi

# ──────────────────────────────────────────────────────────────────────────────
# Step 6: Generate extension.json from APK metadata
# ──────────────────────────────────────────────────────────────────────────────

log "Generating extension metadata..."

# Extract metadata from the keiyoushi index (or from the APK)
NAME="${EXT_NAME:-}"
PKG="${EXT_PKG:-}"
APK_FILE="${EXT_APK:-}"
LANG="${EXT_LANG:-}"

# Try to extract version from the APK's AndroidManifest binary XML
# Use aapt2 if available, otherwise parse with python
if [ -z "${PKG:-}" ]; then
    # Fallback: extract from AndroidManifest.xml
    # Parse the APK using basic binary XML parsing
    PKG=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        # Binary XML - extract package name as ASCII string near the package attribute
        text = data.decode('latin-1')
        m = re.search(r'package=\"([^\"]+)\"', text)
        if m: print(m.group(1))
        else:
            m = re.search(r'([a-z]+\\.[a-z]+\\.[a-z]+[^\"]*)', text)
            if m: print(m.group(1))
    except: pass
" 2>/dev/null) || PKG="unknown.extension"
fi

# Extract versionName and versionCode
VERSION_NAME=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionName=\"([^\"]+)\"', text)
        if m: print(m.group(1))
        else: print('1.0.0')
    except: print('1.0.0')
" 2>/dev/null) || VERSION_NAME="1.0.0"

VERSION_CODE=$(python3 -c "
import zipfile, re
with zipfile.ZipFile('${TEMP_DIR}/extension.apk') as z:
    try:
        data = z.read('AndroidManifest.xml')
        text = data.decode('latin-1')
        m = re.search(r'versionCode=\"?([0-9]+)\"?', text)
        if m: print(m.group(1))
        else: print('100')
    except: print('100')
" 2>/dev/null) || VERSION_CODE="100"

# Determine lib version from versionName (part before last .)
LIB_VERSION=$(echo "$VERSION_NAME" | python3 -c "
import sys
v = sys.stdin.read().strip()
try:
    parts = v.rsplit('.', 1)
    if len(parts) > 1:
        print(float(parts[0]))
    else:
        print('15.0')
except:
    print('15.0')
" 2>/dev/null) || LIB_VERSION="15.0"

# Find the source class names from the decompiled code
SOURCE_CLASSES=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | \
    sed "s|${CLASSES_DIR}/||; s|/|.|g; s|\.class||" | \
    grep -vE "(R\$|R\$layout|R\$id|R\$string|R\$drawable|BuildConfig|Manifest)" | \
    head -5 | tr '\n' ';' | sed 's/;$//')

if [ -z "$SOURCE_CLASSES" ]; then
    err "No source classes found in compiled output"
    SOURCE_CLASSES="${PKG}.MainSource"
fi

log "Package: ${PKG}"
log "Version: ${VERSION_NAME} (code: ${VERSION_CODE})"
log "libVersion: ${LIB_VERSION}"
log "Source classes: ${SOURCE_CLASSES}"
log "Language: ${LANG:-en}"

# Write extension.json
cat > "${TEMP_DIR}/extension.json" << JSON
{
  "name": "Aniyomi: ${NAME:-$PKG}",
  "pkgName": "${PKG}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE:-100},
  "libVersion": ${LIB_VERSION:-15.0},
  "lang": "${LANG:-en}",
  "isNsfw": false,
  "isTorrent": false,
  "sourceClass": "${SOURCE_CLASSES}",
  "pkgFactory": null,
  "hasReadme": false,
  "hasChangelog": false
}
JSON

log "Generated extension.json:"
cat "${TEMP_DIR}/extension.json"

# ──────────────────────────────────────────────────────────────────────────────
# Step 7: Package as JAR
# ──────────────────────────────────────────────────────────────────────────────

log "Packaging extension JAR..."
JAR_NAME="${PKG}.jar"
JAR_PATH="${TEMP_DIR}/${JAR_NAME}"

cd "$CLASSES_DIR"
mkdir -p META-INF
cp "${TEMP_DIR}/extension.json" META-INF/

jar cf "${JAR_PATH}" META-INF/ $(find . -name "*.class" 2>/dev/null)

log "JAR created at: ${JAR_PATH}"
log "JAR contents:"
jar tf "${JAR_PATH}"

# ──────────────────────────────────────────────────────────────────────────────
# Step 8: Install to extensions directory
# ──────────────────────────────────────────────────────────────────────────────

log "Installing extension..."
mkdir -p "${EXTENSIONS_DIR}"
cp "${JAR_PATH}" "${EXTENSIONS_DIR}/${JAR_NAME}"
log "Installed to: ${EXTENSIONS_DIR}/${JAR_NAME}"

# ──────────────────────────────────────────────────────────────────────────────
# Summary
# ──────────────────────────────────────────────────────────────────────────────

log ""
log "═══════════════════════════════════════════════════════════════"
log "  Extension conversion complete!"
log "═══════════════════════════════════════════════════════════════"
log "  Name:     ${NAME:-unknown}"
log "  Package:  ${PKG}"
log "  JAR:      ${EXTENSIONS_DIR}/${JAR_NAME}"
log "  Classes:  ${CLASS_COUNT}"
log ""
log "  To test:"
log "    1. Launch the Anikku macOS app"
log "    2. Go to Browse tab"
log "    3. Find '${NAME:-$PKG}' in the source list"
log "    4. Click to browse anime"
log "    5. Select an anime to see episodes"
log "    6. Play an episode to test mpv streaming"
log ""
log "  Note: The extension may need to be trusted first."
log "═══════════════════════════════════════════════════════════════"
