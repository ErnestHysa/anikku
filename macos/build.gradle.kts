import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.plugin)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.serialization.json)

    // Koin DI (replaces Injekt)
    implementation(libs.koin.core)

    // Logging (SLF4J + Logback)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)

    // SQLDelight (JDBC driver for desktop)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.sqldelight.coroutines)

    // Voyager navigation (desktop compatible)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.screenmodel)
    implementation(libs.voyager.tab.navigator)
    implementation(libs.voyager.transitions)

    // Material Kolor - dynamic color scheme generation
    implementation(libs.material.kolor)

    // Material Motion - shared axis transitions
    implementation(libs.material.motion)

    // Coil 3 - image loading (Compose Desktop compatible)
    implementation(platform("io.coil-kt.coil3:coil-bom:3.3.0"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Moko Resources - i18n string resources (KMP, JVM compatible)
    implementation(libs.moko.resources)

    // Markdown renderer - changelog/about screens
    implementation(libs.markdown.core)
    implementation(libs.markdown.coil)

    // Shared module dependencies (desktop-compatible)
    implementation(libs.rxjava)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okio)
    implementation(libs.jsoup)
    implementation(libs.disklrucache)
    implementation(libs.kotlinx.immutables)

    // Source API (JVM target) and core/common — replaces macOS stubs
    implementation(files("libs/source-api-jvm.jar"))
    implementation(files("libs/common-jvm.jar"))

    // Transitive deps from source-api/core/common
    implementation("com.github.mihonapp:injekt:91edab2317")
    implementation(kotlin("reflect"))
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")

    // NanoHTTPd — embedded HTTP server for local video streaming
    implementation(libs.nanohttpd)

    // JNA - Java Native Access for macOS native API calls
    implementation(libs.jna.core)
    implementation(libs.jna.platform)

    // JSON processing
    implementation(libs.org.json)

    // Jackson — required by many keiyoushi extensions for JSON parsing
    // Must match the version used by the extensions (2.15+ for ObjectNode.remove(EnumSet))
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.2")

    // Apache Commons — used by extensions (StringSubstitutor, encoding, utilities)
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("org.apache.commons:commons-lang3:3.17.0")

    // Aniyomi lib — custom extractors used by some yuzono extensions
    // (DoodExtractor, StreamWishExtractor, etc.) — NOT currently available on JitPack.
    // Extensions using aniyomi.lib.* will need this added when the artifact becomes public.
    // implementation("com.github.aniyomiorg:aniyomi-lib:bdc8184127")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.test {
    dependsOn("buildTestExtensionJar")
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// ---- Extension Build Tasks ------------------------------------------------

/**
 * Build a standalone test extension JAR that can be loaded by MacOSExtensionLoader.
 *
 * Usage:
 *   ./macos/gradlew -p macos buildTestExtensionJar
 *   cp macos/build/libs/test-extension-1.0.0.jar ~/Library/Application Support/Anikku/extensions/
 */
tasks.register<Jar>("buildTestExtensionJar") {
    dependsOn("compileKotlin")
    archiveBaseName.set("test-extension")
    archiveVersion.set("1.0.0")
    from("${layout.buildDirectory.get()}/classes/kotlin/main") {
        include("app/anikku/macos/testextension/**")
    }
    from("src/main/resources/test-extension") {
        into("META-INF")
    }
    manifest {
        attributes("Implementation-Title" to "TestExtension", "Implementation-Version" to "1.0.0")
    }
}

/**
 * Rebuild the source-api and core/common JVM JARs.
 * Run after any changes to source-api or core/common:
 *   ./macos/gradlew -p macos rebuildSourceApiJars
 */
tasks.register<Exec>("rebuildSourceApiJars") {
    workingDir = rootProject.projectDir.parentFile
    commandLine(
        "bash", "-c",
        "./gradlew :source-api:jvmJar :core:common:jvmJar --no-daemon -q && " +
        "cp \$(ls source-api/build/libs/source-api-jvm-*.jar | grep -v -- -sources | head -1) macos/libs/source-api-jvm.jar && " +
        "cp \$(ls core/common/build/libs/common-jvm-*.jar | grep -v -- -sources | head -1) macos/libs/common-jvm.jar && " +
        "echo '=== Copying extension runtime deps ===' && " +
        "bash macos/scripts/copy-extension-deps.sh",
    )
}

tasks.named("compileKotlin") {
    dependsOn("rebuildSourceApiJars")
}

/**
 * Download a keiyoushi extension APK for reference/testing.
 *
 * Usage:
 *   ./macos/gradlew -p macos downloadKeiyoushiExtension
 *   ./macos/gradlew -p macos downloadKeiyoushiExtension -PextName=gogoanime
 */
tasks.register<Exec>("downloadKeiyoushiExtension") {
    description = "Download a keiyoushi extension APK for reference"
    group = "verification"

    val extNameFilter = project.findProperty("extName") as? String ?: ""
    val extensionsDir = "${System.getProperty("user.home")}/Library/Application Support/Anikku/extensions"
    val D = "$"

    commandLine(
        "bash", "-c",
        """
set -euo pipefail
INDEX_URL="https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
OUT_DIR="$extensionsDir"
mkdir -p "${D}OUT_DIR"
PYTHON_SCRIPT='import sys, json
data = json.load(sys.stdin)
ext_name = "$extNameFilter".lower().strip()
for ext in data:
    name = ext.get("name", "").lower()
    lang = ext.get("lang", "")
    if ext_name:
        if ext_name in name:
            print(json.dumps(ext)); sys.exit(0)
    else:
        if lang == "en":
            print(json.dumps(ext)); sys.exit(0)
if data: print(json.dumps(data[0]))
else: print("ERROR: Empty index"); sys.exit(1)'
EXT_JSON=$(curl -sL "${D}INDEX_URL" | python3 -c "${D}PYTHON_SCRIPT")
APK_NAME=$(echo "${D}EXT_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['apk'])")
curl -sL "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/${D}APK_NAME" -o "${D}OUT_DIR/${D}APK_NAME"
echo "Downloaded $(wc -c < "${D}OUT_DIR/${D}APK_NAME") bytes — APK for reference (not JVM-compatible)"
""".trimIndent(),
    )
}

/**
 * Build a single yuzono/anime-extensions extension from source as a JVM JAR.
 *
 * Clones the yuzono/anime-extensions source repo, compiles the extension
 * against source-api JARs, generates META-INF/extension.json, and deploys
 * to the app's extensions directory.
 *
 * This is the RECOMMENDED approach for making extensions available on macOS.
 * Unlike APK→dex2jar conversion, this produces clean JVM bytecode with
 * zero Android references.
 *
 * Usage:
 *   ./macos/gradlew -p macos buildKeiyoushiExtension -PbuildKeiyoushiExtName=miruro
 *
 * Parameters:
 *   -PbuildKeiyoushiExtName=<name>  Extension dir name (required, e.g. miruro)
 *   -PbuildKeiyoushiExtLang=<code>  Language code (default: en)
 *
 * Examples:
 *   # Build miruro extension
 *   ./macos/gradlew -p macos buildKeiyoushiExtension -PbuildKeiyoushiExtName=miruro
 *
 *   # Build aniwave extension
 *   ./macos/gradlew -p macos buildKeiyoushiExtension -PbuildKeiyoushiExtName=aniwave
 *
 * Requirements:
 *   - kotlinc: brew install kotlin
 *   - source-api JARs: ./gradlew rebuildSourceApiJars
 */
val buildKeiyoushiExtName: String? by project
val buildKeiyoushiExtLang: String by project

tasks.register<Exec>("buildKeiyoushiExtension") {
    description = "Build a single yuzono anime extension from source as JVM JAR"
    group = "extension"

    doFirst {
        val extName = buildKeiyoushiExtName
            ?: throw GradleException("Usage: -PbuildKeiyoushiExtName=<name> (e.g., -PbuildKeiyoushiExtName=miruro)")
        val extLang = buildKeiyoushiExtLang.ifBlank { "en" }
        val scriptPath = "${project.projectDir}/scripts/build-keiyoushi-from-source.sh"

        logger.lifecycle("Building extension: $extName (lang: $extLang)")
        logger.lifecycle("Script: $scriptPath")
        logger.lifecycle("")
        logger.lifecycle("This pipeline compiles yuzono/anime-extensions sources from GitHub")
        logger.lifecycle("as JVM JARs, bypassing the Android APK format entirely.")
        logger.lifecycle("")
        logger.lifecycle("Requirements: kotlinc (brew install kotlin)")

        exec {
            commandLine(
                "bash", scriptPath,
                "--pkg", extName,
                "--lang", extLang,
            )
        }
    }
}

/**
 * Batch-build ALL yuzono/anime-extensions from source as JVM JARs.
 *
 * Clones the entire yuzono/anime-extensions source repo, compiles each
 * anime extension against source-api JARs, generates META-INF/extension.json
 * for each, builds a repo index, and auto-trusts all built extensions.
 *
 * This is the RECOMMENDED approach for bulk extension installation on macOS.
 *
 * Usage:
 *   ./macos/gradlew -p macos batchBuildKeiyoushiExtensions
 *
 * Options:
 *   -PbatchExtLang=<code>  Language filter (default: en)
 *   -PbatchExtLimit=<N>    Build at most N extensions (for testing)
 *
 * Examples:
 *   # Build all English extensions
 *   ./macos/gradlew -p macos batchBuildKeiyoushiExtensions
 *
 *   # Build first 5 for testing
 *   ./macos/gradlew -p macos batchBuildKeiyoushiExtensions -PbatchExtLimit=5
 *
 * Requirements:
 *   - kotlinc: brew install kotlin
 *   - source-api JARs built
 */
val batchExtLang: String? by project
val batchExtLimit: String? by project

tasks.register<Exec>("batchBuildKeiyoushiExtensions") {
    description = "Batch-build ALL yuzono anime extensions from source as JVM JARs"
    group = "extension"

    doFirst {
        val scriptPath = "${project.projectDir}/scripts/batch-build-keiyoushi-from-source.sh"
        val lang = (batchExtLang?.ifBlank { "en" }) ?: "en"

        logger.lifecycle("Batch-building extensions for language: $lang")
        logger.lifecycle("Script: $scriptPath")
        logger.lifecycle("")
        logger.lifecycle("This pipeline compiles yuzono/anime-extensions sources from GitHub")
        logger.lifecycle("as JVM JARs, bypassing the Android DEX/APK format entirely.")
        logger.lifecycle("Requirements: kotlinc (brew install kotlin)")
        logger.lifecycle("")

        val args = mutableListOf("bash", scriptPath, "--lang", lang)
        val limit = batchExtLimit
        if (!limit.isNullOrBlank()) {
            args.add("--limit")
            args.add(limit)
        }

        exec { commandLine(args) }
    }
}

// ---- Desktop Application Configuration ------------------------------------

compose.desktop {
    application {
        mainClass = "app.anikku.macos.AnikkuAppKt"

        jvmArgs += listOf(
            "-Xmx2G",
            "-Dapple.awt.application.appearance=system",
            "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "Anikku"
            packageVersion = "1.0.0"
            description = "A native macOS anime watching application"
            vendor = "Anikku"
            licenseFile.set(project.file("../LICENSE"))

            macOS {
                bundleID = "app.anikku.macos"
                iconFile.set(project.file("src/main/resources/icons/app.icns"))
                minimumSystemVersion = "12.0"
                entitlementsFile.set(project.file("src/main/resources/entitlements.plist"))
            }

            // Bundle libmpv.2.dylib into Contents/Resources/ so users don't need brew install mpv
            appResourcesRootDir.set(file("src/main/resources/dist"))
        }
    }
}
