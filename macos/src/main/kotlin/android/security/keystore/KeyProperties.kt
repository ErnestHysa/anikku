package android.security.keystore

/**
 * Stubs for android.security.keystore on macOS desktop.
 */
object KeyGenParameterSpec {
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2

    class Builder(keyStoreAlias: String, purposes: Int)
}

object KeyProperties {
    const val BLOCK_MODE_CBC = "CBC"
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_PKCS7 = "PKCS7"
    const val ENCRYPTION_PADDING_NONE = "None"
    const val KEY_ALGORITHM_AES = "AES"
    const val KEY_ALGORITHM_RSA = "RSA"
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val DIGEST_SHA256 = "SHA-256"
}
