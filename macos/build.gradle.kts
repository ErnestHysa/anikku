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
    // coil-gif is Android-only (AAR) — excluded
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

    // JNA - Java Native Access for macOS native API calls
    implementation(libs.jna.core)
    implementation(libs.jna.platform)
}

compose.desktop {
    application {
        mainClass = "app.anikku.macos.AnikkuAppKt"

        jvmArgs += listOf(
            "-Xmx2G",
            "-Dapple.awt.application.appearance=system",
            // Allow access to internal AWT APIs for JNA native window bridging
            "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            packageName = "Anikku"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "app.anikku.macos"
                // TODO Phase 10.1: Add iconFile.set(project.file("src/main/resources/icons/app.icns"))
                minimumSystemVersion = "12.0"
            }
        }
    }
}
