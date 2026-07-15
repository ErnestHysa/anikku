import Sparkle
import Foundation

/// Sparkle 2 helper for Anikku macOS.
///
/// This file is compiled into a dynamic library (libSparkleHelper.dylib)
/// and loaded by the Kotlin app via JNA. It wraps Sparkle's
/// SPUStandardUpdaterController to provide C-compatible functions:
///
///   sparkle_init()             — Start Sparkle (call once at app launch)
///   sparkle_checkForUpdates()  — Show the Sparkle update dialog
///   sparkle_checkInBackground() — Silent background check
///   sparkle_feedURL()          — Return the configured feed URL
///
/// Compilation:
///   swiftc -emit-library -o libSparkleHelper.dylib \
///     -F /path/to/Sparkle.framework/.. \
///     -framework Sparkle \
///     SparkleHelper.swift
///
/// The resulting .dylib and Sparkle.framework must be bundled in
/// Anikku.app/Contents/Frameworks/.

// MARK: - Global updater controller

private var updaterController: SPUStandardUpdaterController? = nil

// MARK: - C-compatible exported functions

/// Initialize Sparkle with the given feed URL.
/// Call this ONCE at app startup, before any other Sparkle functions.
///
/// - Parameter feedURL: The appcast feed URL (e.g., "https://anikku.app/sparkle/appcast.xml").
///   If nil or empty, Sparkle reads SUFeedURL from the app's Info.plist.
@_cdecl("sparkle_init")
public func sparkle_init(feedURL: UnsafePointer<CChar>?) -> Bool {
    guard updaterController == nil else {
        print("[SparkleHelper] Already initialized — ignoring")
        return true
    }

    let urlString = feedURL.map { String(cString: $0) } ?? ""
    let url: URL? = urlString.isEmpty ? nil : URL(string: urlString)

    print("[SparkleHelper] Initializing Sparkle...")
    if let feedURL = url {
        print("[SparkleHelper] Feed URL: \(feedURL.absoluteString)")
    }

    // SPUStandardUpdaterController automatically:
    // - Checks for updates on a schedule (default: daily)
    // - Shows the "Check for Updates..." menu item
    // - Handles download, extraction, and installation
    updaterController = SPUStandardUpdaterController(
        startingUpdater: true,  // true = start update checks immediately
        updaterDelegate: nil,
        userDriverDelegate: nil
    )

    print("[SparkleHelper] Sparkle initialized successfully")
    return updaterController != nil
}

/// Show the standard Sparkle update window.
/// If an update is available, the user can download and install it.
@_cdecl("sparkle_checkForUpdates")
public func sparkle_checkForUpdates() {
    guard let controller = updaterController else {
        print("[SparkleHelper] Not initialized — call sparkle_init first")
        return
    }
    print("[SparkleHelper] Checking for updates (UI)...")
    controller.updater.checkForUpdates()
}

/// Perform a silent background check for updates.
/// If an update is found, Sparkle shows a notification (not a dialog).
@_cdecl("sparkle_checkInBackground")
public func sparkle_checkInBackground() {
    guard let controller = updaterController else {
        print("[SparkleHelper] Not initialized — call sparkle_init first")
        return
    }
    print("[SparkleHelper] Checking for updates (background)...")
    controller.updater.checkForUpdatesInBackground()
}

/// Return the configured Sparkle feed URL.
/// The caller (JNA) will copy the string immediately.
/// Returns nil if Sparkle is not initialized.
@_cdecl("sparkle_feedURL")
public func sparkle_feedURL() -> UnsafePointer<CChar>? {
    guard let controller = updaterController else {
        return nil
    }
    // Sparkle reads SUFeedURL from Info.plist, not UserDefaults.
    guard let urlString = controller.updater.feedURL?.absoluteString else { return nil }
    return UnsafePointer(strdup(urlString))
}
