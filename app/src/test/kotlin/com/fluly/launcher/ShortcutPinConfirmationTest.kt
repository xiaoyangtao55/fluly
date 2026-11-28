package com.fluly.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests the same one-shot consent gate used by the Activity's dialog callbacks. */
class ShortcutPinConfirmationTest {
    @Test
    fun openingConfirmationDoesNotInstall() {
        var installs = 0
        ShortcutPinConfirmation {
            installs++
            ShortcutInstallResult.ADDED
        }
        assertEquals(0, installs)
    }

    @Test
    fun explicitConfirmationInstallsOnlyOnce() {
        var installs = 0
        val confirmation = ShortcutPinConfirmation {
            installs++
            ShortcutInstallResult.ADDED
        }
        assertEquals(ShortcutInstallResult.ADDED, confirmation.confirm())
        assertNull(confirmation.confirm())
        assertEquals(1, installs)
    }

    @Test
    fun cancellationPreventsAcceptanceAndPersistenceEvenWithLatePositiveCallback() {
        var accepts = 0
        var writes = 0
        val confirmation = ShortcutPinConfirmation {
            accepts++
            writes++
            ShortcutInstallResult.ADDED
        }
        confirmation.cancel()
        confirmation.cancel()
        assertNull(confirmation.confirm())
        assertEquals(0, accepts)
        assertEquals(0, writes)
    }

    @Test
    fun replacementCancelsOldConsentWithoutApprovingNewRequest() {
        var oldInstalls = 0
        var newInstalls = 0
        val old = ShortcutPinConfirmation { oldInstalls++; ShortcutInstallResult.ADDED }
        old.cancel() // onNewIntent and onDestroy clear the outgoing gate.
        val replacement = ShortcutPinConfirmation { newInstalls++; ShortcutInstallResult.ADDED }
        assertNull(old.confirm())
        assertEquals(0, oldInstalls)
        assertEquals(0, newInstalls)
        assertEquals(ShortcutInstallResult.ADDED, replacement.confirm())
        assertEquals(1, newInstalls)
    }

    @Test
    fun failedConfirmationIsNotRetried() {
        var attempts = 0
        val confirmation = ShortcutPinConfirmation {
            attempts++
            error("Request expired")
        }
        assertEquals(ShortcutInstallResult.FAILED, confirmation.confirm())
        assertNull(confirmation.confirm())
        assertEquals(1, attempts)
    }
}
