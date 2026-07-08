package app.anikku.macos.platform.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacOSBiometricAuthTest {

    private val auth = MacOSBiometricAuth()

    @Test
    fun `pin is not initially set`() {
        assertFalse(auth.isPinSet)
    }

    @Test
    fun `set new pin`() {
        auth.setPin("1234")
        assertTrue(auth.isPinSet)
    }

    @Test
    fun `verify correct pin`() {
        auth.setPin("1234")
        assertTrue(auth.verifyPin("1234"))
    }

    @Test
    fun `verify incorrect pin`() {
        auth.setPin("1234")
        assertFalse(auth.verifyPin("5678"))
    }

    @Test
    fun `change pin with correct old pin`() {
        auth.setPin("1234")
        assertTrue(auth.changePin("1234", "5678"))
        assertTrue(auth.verifyPin("5678"))
        assertFalse(auth.verifyPin("1234"))
    }

    @Test
    fun `change pin fails with incorrect old pin`() {
        auth.setPin("1234")
        assertFalse(auth.changePin("wrong", "5678"))
        assertTrue(auth.verifyPin("1234"))
    }

    @Test
    fun `clear pin`() {
        auth.setPin("1234")
        assertTrue(auth.isPinSet)
        auth.clearPin()
        assertFalse(auth.isPinSet)
    }

    @Test
    fun `authenticate with pin when biometrics unavailable`() {
        auth.setPin("1234")
        val result = auth.authenticate(reason = "Test", pin = "1234")
        assertTrue(result)
    }

    @Test
    fun `authenticate fails with wrong pin`() {
        auth.setPin("1234")
        val result = auth.authenticate(reason = "Test", pin = "wrong")
        assertFalse(result)
    }

    @Test
    fun `verify pin is case sensitive`() {
        auth.setPin("AbCd")
        assertTrue(auth.verifyPin("AbCd"))
        assertFalse(auth.verifyPin("abcd"))
        assertFalse(auth.verifyPin("ABCD"))
    }

    @Test
    fun `verify empty pin`() {
        auth.setPin("")
        assertTrue(auth.verifyPin(""))
        assertFalse(auth.verifyPin("not_empty"))
    }

    @Test
    fun `biometric availability returns false on test JVM`() {
        // In a test environment without macOS LocalAuthentication, should return false
        assertFalse(auth.isBiometricAvailable)
    }
}
