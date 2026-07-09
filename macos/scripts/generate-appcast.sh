#!/usr/bin/env bash
#
# generate-appcast.sh
# ===================
# Generates a Sparkle-compatible appcast.xml from a GitHub release.
#
# This script:
# 1. Takes a version number, DMG file path, and Ed25519 signing key
# 2. Signs the DMG with the Ed25519 key to produce a Sparkle signature
# 3. Generates or updates appcast.xml with the release entry
#
# Usage:
#   ./generate-appcast.sh --version 1.0.1 --dmg ./Anikku-1.0.1.dmg \
#       --signing-key ./ed25519-key.pem --appcast ./appcast.xml
#
# Requirements:
#   - openssl 3.x+ (for Ed25519 signing)
#   - Python 3 (for base64 encoding)
#
# The signing key must match the public key distributed with the app
# at macOS/src/main/resources/Sparkle/ed25519_pub.pem.
#
# For first-time setup, generate a key pair:
#   openssl genpkey -algorithm ed25519 -out ed25519-key.pem
#   openssl pkey -in ed25519-key.pem -pubout -out ed25519-pub.pem
#   cp ed25519-pub.pem macos/src/main/resources/Sparkle/ed25519_pub.pem

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { echo "[*] $*"; }
err() { echo "[!] $*" >&2; }

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Generate or update a Sparkle appcast.xml for a new release.

Required:
  --version <ver>    Version string (e.g., 1.0.1)
  --dmg <path>       Path to the DMG file to sign
  --signing-key <p>  Path to the Ed25519 private key PEM file

Options:
  --appcast <path>   Path to appcast.xml (default: updates appcast.xml in CWD)
  --repo-url <url>   GitHub repo URL (default: https://github.com/komikku-app/anikku)
  --help             Show this help

Examples:
  ./generate-appcast.sh --version 1.0.1 \\
      --dmg ./build/compose/binaries/main/dmg/Anikku-1.0.1.dmg \\
      --signing-key ./ed25519-key.pem \\
      --appcast ./appcast.xml
EOF
    exit 0
}

# Parse arguments
VERSION=""
DMG_PATH=""
SIGNING_KEY=""
APPCAST_PATH=""
REPO_URL="https://github.com/komikku-app/anikku"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version) VERSION="$2"; shift 2 ;;
        --dmg) DMG_PATH="$2"; shift 2 ;;
        --signing-key) SIGNING_KEY="$2"; shift 2 ;;
        --appcast) APPCAST_PATH="$2"; shift 2 ;;
        --repo-url) REPO_URL="$2"; shift 2 ;;
        --help|-h) usage ;;
        *) err "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$VERSION" ] || [ -z "$DMG_PATH" ] || [ -z "$SIGNING_KEY" ]; then
    err "Error: --version, --dmg, and --signing-key are required"
    usage
fi

if [ ! -f "$DMG_PATH" ]; then
    err "DMG file not found: $DMG_PATH"
    exit 1
fi

if [ ! -f "$SIGNING_KEY" ]; then
    err "Signing key not found: $SIGNING_KEY"
    exit 1
fi

APPCAST_PATH="${APPCAST_PATH:-appcast.xml}"

# ---------------------------------------------------------------------------
# Step 1: Sign the DMG with Ed25519
# ---------------------------------------------------------------------------
log "Signing DMG with Ed25519 private key..."
log "  DMG:       ${DMG_PATH}"
log "  Key:       ${SIGNING_KEY}"

DMG_SIZE=$(stat -f%z "$DMG_PATH" 2>/dev/null || stat -c%s "$DMG_PATH" 2>/dev/null || echo "0")
log "  DMG size:  ${DMG_SIZE} bytes"

# Generate Ed25519 signature (Sparkle 2+ format)
SIGNATURE=$(openssl pkeyutl \
    -sign \
    -inkey "$SIGNING_KEY" \
    -rawin \
    -in "$DMG_PATH" 2>/dev/null | base64)

if [ -z "$SIGNATURE" ]; then
    # Fallback: try older OpenSSL syntax
    SIGNATURE=$(openssl dgst -sign "$SIGNING_KEY" -keyform PEM -binary "$DMG_PATH" 2>/dev/null | base64)
fi

if [ -z "$SIGNATURE" ]; then
    err "Failed to generate Ed25519 signature. Check your OpenSSL version (3.x required)."
    exit 1
fi

log "  Signature: ${SIGNATURE:0:32}..."

# ---------------------------------------------------------------------------
# Step 2: Generate the appcast item
# ---------------------------------------------------------------------------
log "Generating appcast item..."

# Current date in RFC 2822 format
PUB_DATE=$(date -R 2>/dev/null || date -u +"%a, %d %b %Y %H:%M:%S %z" 2>/dev/null || echo "Mon, 01 Jan 2026 00:00:00 +0000")

DOWNLOAD_URL="${REPO_URL}/releases/download/v${VERSION}/Anikku-${VERSION}.dmg"
RELEASE_NOTES_URL="${REPO_URL}/releases/tag/v${VERSION}"

# Build the new item XML
read -r -d '' NEW_ITEM << ITEMEOF || true
    <item>
      <title>Version ${VERSION}</title>
      <sparkle:version>${VERSION}</sparkle:version>
      <sparkle:shortVersionString>${VERSION}</sparkle:shortVersionString>
      <pubDate>${PUB_DATE}</pubDate>
      <enclosure
        url="${DOWNLOAD_URL}"
        sparkle:edSignature="${SIGNATURE}"
        length="${DMG_SIZE}"
        type="application/octet-stream" />
      <sparkle:releaseNotesLink>
        ${RELEASE_NOTES_URL}
      </sparkle:releaseNotesLink>
    </item>
ITEMEOF

# ---------------------------------------------------------------------------
# Step 3: Write appcast.xml
# ---------------------------------------------------------------------------
if [ -f "$APPCAST_PATH" ] && [ -s "$APPCAST_PATH" ]; then
    # Insert the new item after <channel> (before any existing items)
    log "Updating existing appcast: ${APPCAST_PATH}"
    python3 -c "
import sys
with open('${APPCAST_PATH}', 'r') as f:
    content = f.read()

new_item = '''${NEW_ITEM}'''

# Insert after <channel>
insert_pos = content.find('<channel>') + len('<channel>') + 1
content = content[:insert_pos] + '\n' + new_item + content[insert_pos:]
content = content.replace('<channel>\n\n', '<channel>\n')

with open('${APPCAST_PATH}', 'w') as f:
    f.write(content)

print('Updated appcast.xml with new entry')
print(f'  Version: ${VERSION}')
print(f'  Items: {content.count(\"<item>\")}')
"
else
    log "Creating new appcast: ${APPCAST_PATH}"
    cat > "$APPCAST_PATH" << XML
<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
  <channel>
    <title>Anikku macOS Appcast</title>
    <description>Most recent changes to Anikku macOS</description>
    <language>en</language>

${NEW_ITEM}
  </channel>
</rss>
XML
    log "Created new appcast.xml with initial entry"
fi

# ---------------------------------------------------------------------------
# Step 4: Verify
# ---------------------------------------------------------------------------
log ""
log "═══════════════════════════════════════════════════════════════"
log "  APPCAST GENERATION COMPLETE"
log "═══════════════════════════════════════════════════════════════"
log "  Appcast:       ${APPCAST_PATH}"
log "  Version:       ${VERSION}"
log "  DMG:           ${DMG_PATH}"
log "  DMG Size:      ${DMG_SIZE} bytes"
log "  Signature:     ${SIGNATURE:0:48}..."
log "  Download URL:  ${DOWNLOAD_URL}"
log ""
log "  Next steps:"
log "    1. Host appcast.xml at a public URL (e.g., GitHub Pages)"
log "    2. Set Info.plist SUFeedURL to that URL"
log "    3. Upload the DMG to GitHub Releases"
log ""
log "  To test locally:"
log "    python3 -m http.server 8080 --directory \$(dirname ${APPCAST_PATH})"
log "    Then set SUFeedURL to http://localhost:8080/\$(basename ${APPCAST_PATH})"
log "═══════════════════════════════════════════════════════════════"
