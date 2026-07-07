package android.content.pm

/**
 * Stub for android.content.pm.PackageManager on macOS desktop.
 */
open class PackageManager {
    companion object {
        const val GET_META_DATA = 0x80
        const val GET_SIGNATURES = 0x40
        const val GET_SIGNING_CERTIFICATES = 0x8000000
    }

    open fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo()
    open fun hasSystemFeature(name: String): Boolean = true
}

class PackageInfo {
    var versionName: String = "1.0.0"
    var versionCode: Int = 1
}
