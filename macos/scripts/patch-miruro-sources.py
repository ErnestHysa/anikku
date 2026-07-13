#!/usr/bin/env python3
"""Patch miruro extension sources to remove problematic extractor deps."""

import os
import re
import sys

REMOVED_LIBS = ["m3u8server", "megacloudextractor", "omniembedextractor", "rapidcloudextractor"]


def patch_miruro_extractor(filepath):
    if not os.path.isfile(filepath):
        print("  Skipping: %s not found" % filepath)
        return False

    with open(filepath) as f:
        content = f.read()
    original = content

    # 1. Remove problematic imports
    for lib in REMOVED_LIBS:
        content = re.sub(
            r'^import aniyomi\.lib\.' + re.escape(lib) + r'\..*\n',
            '', content, flags=re.MULTILINE)

    # 2. Remove lazy vals (single-line and multi-line)
    content = re.sub(
        r'^    private val (embedExtractor|m3u8Integration) by lazy \{.*\}\n',
        r'    // [patched] \1 removed\n', content, flags=re.MULTILINE)
    content = re.sub(
        r'^    private val (megaCloudExtractor|rapidCloudExtractor) by lazy \{\n'
        r'        .*\n^    \}\n',
        r'    // [patched] \1 removed\n', content, flags=re.MULTILINE)

    # 3. Replace m3u8Integration usage block with fallback
    content = re.sub(
        r'        val proxied = m3u8Integration\.processVideoList\(videos\)\n'
        r'        Log\.d\(TAG, "parseStreamsFromResponse: built \${videos\.size} videos from \${sourcesDto\.streams\.size} streams, \${proxied\.size} proxied via m3u8server"\)\n'
        r'        return proxied\n',
        r'        Log.d(TAG, "parseStreamsFromResponse: built ${videos.size} videos (m3u8 proxying unavailable on JVM)")\n'
        r'        return videos\n',
        content, flags=re.MULTILINE)

    # 4. Comment out runCatching blocks for extractors (using .format() not f-strings to avoid brace issues)
    for ref in ["megaCloudExtractor", "embedExtractor"]:
        pat = (r'            }} -> runCatching \{\n'
               r'                ' + re.escape(ref) + r'\.getVideosFromUrl\(.*\n'
               r'(?:                    .*\n)*'
               r'                \)\n'
               r'            \}\.onFailure \{\n'
               r'(?:                .*\n)*'
               r'            \}\.getOrDefault\(.*\)\n')
        repl = (r'            }} -> \{\n'
                r'                // [patched] ' + ref + r' not available on JVM\n'
                r'                emptyList()\n'
                r'            }\n')
        content = re.sub(pat, repl, content, flags=re.MULTILINE)

    # rapidCloudExtractor single-line call
    pat = (r'            RAPID_CLOUD_HOSTS\.any \{\n'
           r'                lowerHost\.contains\(it\)\n'
           r'            \} -> runCatching \{\n'
           r'                rapidCloudExtractor\.getVideosFromUrl\(embedUrl, type = "Multi", name = qualityLabel\)\n'
           r'            \}\.onFailure \{\n'
           r'(?:                .*\n)*'
           r'            \}\.getOrDefault\(.*\)\n')
    repl = (r'            RAPID_CLOUD_HOSTS.any {\n'
            r'                lowerHost.contains(it)\n'
            r'            } -> {\n'
            r'                // [patched] rapidCloudExtractor not available on JVM\n'
            r'                emptyList()\n'
            r'            }\n')
    content = re.sub(pat, repl, content, flags=re.MULTILINE)

    # 5. Line-wide fallback: comment out lines with extractor refs
    for ref in ["megaCloudExtractor", "rapidCloudExtractor", "embedExtractor", "m3u8Integration"]:
        content = re.sub(
            r'^(.*' + re.escape(ref) + r'\b(?!.*\[patched\]).*)$',
            r'// [patched] \1', content, flags=re.MULTILINE)

    # 6. Handle orphaned named arguments after commented-out extractor calls.
    # Use line-by-line processing (not multi-line regex) for robustness.
    lines = content.split('\n')
    in_orphaned_block = False
    i = 0
    while i < len(lines):
        line = lines[i]
        # Detect start: a [patched] line with getVideosFromUrl( or extractVideos(
        if re.match(r'// \[patched\].*(getVideosFromUrl|extractVideos)\(', line):
            in_orphaned_block = True
            lines[i] = '// [patched] extractor call block removed'
            i += 1
            continue
        if in_orphaned_block:
            # Continue commenting out until we hit a line that doesn't match
            stripped = line.lstrip()
            if stripped.startswith(')') or re.match(r'[a-zA-Z]+ = .+,?', stripped):
                lines[i] = '// [patched] ' + line
                i += 1
                continue
            else:
                in_orphaned_block = False
        i += 1
    content = '\n'.join(lines)

    if content == original:
        print("  No changes needed for %s" % filepath)
        return False

    with open(filepath, "w") as f:
        f.write(content)

    changes = sum(1 for l in content.split('\n') if '[patched]' in l)
    print("  Patched: %s -- %d patch(es) applied" % (os.path.basename(filepath), changes))
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: patch-miruro-sources.py <extensions-source-dir>")
        sys.exit(1)

    miruro_dir = os.path.join(
        sys.argv[1], "src", "en", "miruro", "src",
        "eu", "kanade", "tachiyomi", "animeextension", "en", "miruro")
    if not os.path.isdir(miruro_dir):
        print("Miruro source dir not found: %s" % miruro_dir)
        sys.exit(1)

    patch_miruro_extractor(os.path.join(miruro_dir, "MiruroExtractor.kt"))


if __name__ == "__main__":
    main()
