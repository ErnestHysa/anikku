#!/usr/bin/env python3
"""
patch-all-source-issues.py

Universal patch script for yuzono/anime-extensions source compilation.
Fixes common issues that prevent JVM compilation of Android anime extensions.

Run BEFORE compiling extensions. Applies targeted in-place patches to
extension source files in the cloned git repo.

Usage:
    python3 patch-all-source-issues.py <extensions_source_dir>
"""

import os
import re
import sys


def log(msg):
    print(f"  [patch] {msg}")


def patch_file(fpath, old, new):
    """Replace old with new in file. Returns True if changed."""
    try:
        with open(fpath, "r") as fh:
            content = fh.read()
        if old in content:
            content = content.replace(old, new)
            with open(fpath, "w") as fh:
                fh.write(content)
            print(f"  [patch] Fixed: {os.path.basename(fpath)}")
            return True
        return False
    except Exception:
        return False


def regex_patch_file(fpath, pattern, replacement, description=""):
    """Apply regex replacement in file. Returns True if changed."""
    try:
        with open(fpath, "r") as fh:
            content = fh.read()
        new_content, count = re.subn(pattern, replacement, content)
        if count > 0:
            with open(fpath, "w") as fh:
                fh.write(new_content)
            if description:
                print(f"  [patch] {description}: {os.path.basename(fpath)} ({count} changes)")
            return True
        return False
    except Exception:
        return False


def patch_nullable_strings(ext_dir):
    """
    Fix #1: Nullable String? -> String mismatch.
    
    The MOST common failure (~12 extensions). Extensions pass nullable 
    `preferences.getString(key, default)` results to functions expecting 
    non-null String. The Android SharedPreferences.getString() returns 
    String? but our stub returns non-null "".

    Fix: add !! to getString() calls when used as arguments or in assignments
    to non-null String variables.
    """
    fixes = 0
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                
                # Only fix if the file actually has a nullable-related error pattern.
                # Look for getString() used as function argument or RHS of assignment
                # where the result is used in non-null String context.
                # Pattern: .getString("...", "...") used in val/var declaration
                new_content = re.sub(
                    r'(val\s+\w+\s*=\s*preferences\??\.getString\([^)]*\))\s*$',
                    r'\1!!',
                    new_content,
                    flags=re.MULTILINE
                )
                new_content = re.sub(
                    r'(var\s+\w+\s*=\s*preferences\??\.getString\([^)]*\))\s*$',
                    r'\1!!',
                    new_content,
                    flags=re.MULTILINE
                )
                
                if new_content != content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    fixes += 1
            except Exception:
                continue
    return fixes


def patch_kisskh(ext_dir):
    """Fix #2: KissKH BuildConfig constants."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "KissKH.kt":
                fpath = os.path.join(root, f)
                changed = False
                changed |= patch_file(fpath, 
                    '"${BuildConfig.KISSKH_API}$id&version=2.8.10"',
                    '"https://api.kisskh.co/api/$id&version=2.8.10"')
                changed |= patch_file(fpath,
                    '"${BuildConfig.KISSKH_SUB_API}$id&version=2.8.10"',
                    '"https://subs.kisskh.co/api/$id&version=2.8.10"')
                if changed:
                    print("  [patch] KissKH.kt — replaced BuildConfig with hardcoded URLs")


def patch_av1encodes(ext_dir):
    """Fix #3: AV1Encodes Uri.decode -> java.net.URLDecoder.decode."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "AV1Encodes.kt":
                fpath = os.path.join(root, f)
                changed = patch_file(fpath, "Uri.decode", "java.net.URLDecoder.decode")
                if changed:
                    print("  [patch] AV1Encodes.kt — Uri.decode -> java.net.URLDecoder.decode")


def patch_animekhor(ext_dir):
    """Fix #4: AnimeKhor VidHideExtractor 2-arg -> 1-arg constructor."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "AnimeKhor.kt":
                fpath = os.path.join(root, f)
                changed = patch_file(fpath,
                    "VidHideExtractor(client, headers)",
                    "VidHideExtractor(client)")
                if changed:
                    print("  [patch] AnimeKhor.kt — VidHideExtractor 2-arg -> 1-arg")


def patch_animenosub(ext_dir):
    """Fix #5: Animenosub getEpisodeName override, videoSortPref."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "Animenosub.kt":
                fpath = os.path.join(root, f)
                changed = False
                # getEpisodeName doesn't exist in JVM source-api
                changed |= patch_file(fpath,
                    'override fun getEpisodeName(episode: SEpisode): String? = episode.name',
                    '// getEpisodeName removed for JVM')
                changed |= patch_file(fpath, 'videoSortPref', 'videoSortPref!!')
                if changed:
                    print("  [patch] Animenosub.kt — fixed overrides & nullables")


def patch_kimoitv(ext_dir):
    """Fix #6: KimoiTV missing abstract member stubs."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "KimoiTV.kt":
                fpath = os.path.join(root, f)
                try:
                    with open(fpath, "r") as fh:
                        content = fh.read()
                    if "hosterListSelector" in content or "hosterFromElement" in content:
                        continue
                    # Insert stubs before the last class closing brace
                    stubs = """
    // Host stubs (required by ParsedAnimeHttpSource on JVM)
    override fun hosterListSelector() = "ul.videos > li"
    override fun hosterFromElement(element: Element) = throw Exception("Not implemented")
"""
                    last_brace = content.rfind("}")
                    if last_brace > 0:
                        content = content[:last_brace] + stubs + content[last_brace:]
                        with open(fpath, "w") as fh:
                            fh.write(content)
                        print("  [patch] KimoiTV.kt — added hoster stubs")
                except Exception:
                    pass


def patch_miruro(ext_dir):
    """Fix #7: Miruro media.opt, setEnabled, JSONObject.NULL."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            if f == "Miruro.kt":
                fpath = os.path.join(root, f)
                changed = False
                new_content = None
                try:
                    with open(fpath, "r") as fh:
                        content = fh.read()
                    new_content = content
                    # media.opt( -> media?.opt( (safe call on nullable)
                    new_content = re.sub(r'(?<![\?.])media\.opt\(', 'media?.opt(', new_content)
                    if new_content != content:
                        changed = True
                except Exception:
                    pass
                if changed and new_content:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print("  [patch] Miruro.kt — media.opt -> media?.opt")


def patch_cineby(ext_dir):
    """Fix #8: Cineby Android-only APIs (@RequiresApi, LruCache)."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                changed = False
                
                new_content = re.sub(r'@RequiresApi\([^)]*\)\s*\n', '', new_content)
                if "import android.annotation.RequiresApi" in new_content:
                    new_content = new_content.replace(
                        "import android.annotation.RequiresApi", 
                        "// Removed for JVM: import android.annotation.RequiresApi"
                    )
                    changed = True
                if "import android.util.LruCache" in new_content:
                    new_content = new_content.replace(
                        "import android.util.LruCache",
                        "// Replaced for JVM: android.util.LruCache"
                    )
                    changed = True
                
                if changed:
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — removed Android-only APIs")
            except Exception:
                continue


def patch_kickassanime(ext_dir):
    """Fix #9: Kickassanime safe call violations on intent.data."""
    for root, dirs, files in os.walk(ext_dir):
        for f in files:
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                new_content = content
                if "intent.data" in new_content and f.endswith("UrlActivity.kt"):
                    new_content = re.sub(r'(intent\.data)([^?!])', r'intent!!.data\2', new_content)
                    if new_content != content:
                        with open(fpath, "w") as fh:
                            fh.write(new_content)
                        print(f"  [patch] {f} — intent!!.data")
            except Exception:
                continue


def patch_missing_hosters(ext_path):
    """
    Generic fix: add hosterListSelector and hosterFromElement stubs.
    
    Many extensions extend ParsedAnimeHttpSource (via a multisrc theme like
    WcoTheme, AnikotoTheme, DooPlay, etc.) but only override getVideoList()
    directly, leaving hosterListSelector() and hosterFromElement() abstract.
    
    These methods are never called if getVideoList() is overridden, but the
    compiler requires them to be implemented since they're abstract in the
    ParsedAnimeHttpSource base class.
    """
    for root, dirs, files in os.walk(ext_path):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fpath = os.path.join(root, f)
            try:
                with open(fpath, "r") as fh:
                    content = fh.read()
                
                # Only patch files that have class declarations extending something
                if not re.search(r'class\s+\w+\s*:', content):
                    continue
                
                # Skip if already has hoster stubs
                if "hosterListSelector" in content or "hosterFromElement" in content:
                    continue
                
                # Check if this file extends ParsedAnimeHttpSource (directly or via
                # any multisrc theme). Use a regex check for multisrc imports to
                # catch ALL multisrc themes now and in the future without hardcoding.
                has_parsed_source = (
                    "ParsedAnimeHttpSource" in content or
                    re.search(r'import.*multisrc\.', content)
                )
                if not has_parsed_source:
                    continue
                
                # Insert stubs before the last class closing brace
                stubs = """
    // Host stubs (not used — video extraction via getVideoList override)
    override fun hosterListSelector() = "unused"
    override fun hosterFromElement(element: org.jsoup.nodes.Element) = eu.kanade.tachiyomi.animesource.model.Hoster("", "")
"""
                # Find the last } that's not inside a string or comment
                # Simple approach: find the last '}' at column 0 (class-level closing)
                lines = content.split('\n')
                last_brace_idx = -1
                for i in range(len(lines) - 1, -1, -1):
                    stripped = lines[i].strip()
                    if stripped == '}' or stripped.startswith('} //'):
                        last_brace_idx = i
                        break
                
                if last_brace_idx > 0:
                    lines.insert(last_brace_idx, stubs)
                    new_content = '\n'.join(lines)
                    with open(fpath, "w") as fh:
                        fh.write(new_content)
                    print(f"  [patch] {f} — added hoster stubs")
            except Exception:
                continue


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 patch-all-source-issues.py <extensions_source_dir>")
        sys.exit(1)
    
    ext_root = sys.argv[1]
    if not os.path.isdir(ext_root):
        print(f"Error: {ext_root} is not a directory")
        sys.exit(1)
    
    print("")
    print("  ╔══════════════════════════════════════════════════════╗")
    print("  ║  Patching extension sources for JVM compilation     ║")
    print("  ╚══════════════════════════════════════════════════════╝")
    print("")
    
    # Patch core files (DataLifeEngine, DooPlay, DopeFlix — same as Step 1b)
    core_dirs = [
        os.path.join(ext_root, "lib-multisrc", "datalifeengine", "src"),
        os.path.join(ext_root, "lib-multisrc", "dooplay", "src"),
        os.path.join(ext_root, "lib-multisrc", "dopeflix", "src"),
    ]
    for cd in core_dirs:
        if os.path.isdir(cd):
            patch_nullable_strings(cd)
    
    # Apply patches to all extension directories
    src_dir = os.path.join(ext_root, "src")
    ext_count = 0
    if os.path.isdir(src_dir):
        for lang in sorted(os.listdir(src_dir)):
            lang_dir = os.path.join(src_dir, lang)
            if not os.path.isdir(lang_dir):
                continue
            for ext_name in sorted(os.listdir(lang_dir)):
                ext_path = os.path.join(lang_dir, ext_name, "src")
                if not os.path.isdir(ext_path):
                    continue
                ext_count += 1
                
                patch_nullable_strings(ext_path)
                patch_missing_hosters(ext_path)
                patch_kisskh(ext_path)
                patch_av1encodes(ext_path)
                patch_animekhor(ext_path)
                patch_animenosub(ext_path)
                patch_kimoitv(ext_path)
                patch_miruro(ext_path)
                patch_cineby(ext_path)
                patch_kickassanime(ext_path)
    
    print(f"")
    print(f"  Processed {ext_count} extension directories")
    print(f"  ✅ Patching complete")
    print("")


if __name__ == "__main__":
    main()
