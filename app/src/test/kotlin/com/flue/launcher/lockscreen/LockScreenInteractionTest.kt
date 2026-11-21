package com.flue.launcher.lockscreen

import com.flue.launcher.watchface.BUILT_IN_WATCHFACE_ID
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.WatchFaceType
import org.junit.Assert.*
import org.junit.Test

class LockScreenInteractionTest {
    @Test fun `pause and stop caused by screen off preserve one launch token`() {
        val gate = LockScreenForegroundGate()
        gate.onResume()
        gate.onPause(isInteractive = false)
        gate.onStop(isInteractive = false)
        assertTrue(gate.consumeScreenOff())
        assertFalse(gate.consumeScreenOff())
    }

    @Test fun `opening another app revokes token even if launcher receiver remains registered`() {
        val gate = LockScreenForegroundGate()
        gate.onResume()
        gate.onPause(isInteractive = true)
        gate.onStop(isInteractive = false)
        assertFalse(gate.consumeScreenOff())
        gate.onResume()
        assertTrue(gate.consumeScreenOff())
    }

    @Test fun `never resumed launcher cannot launch a lock face`() {
        assertFalse(LockScreenForegroundGate().consumeScreenOff())
    }

    @Test fun `only a sufficiently long predominantly upward swipe exits`() {
        assertTrue(isLockScreenExitSwipe(10f, -60f, 48f))
        assertFalse(isLockScreenExitSwipe(0f, -47f, 48f))
        assertFalse(isLockScreenExitSwipe(0f, 60f, 48f))
        assertFalse(isLockScreenExitSwipe(70f, -60f, 48f))
    }

    @Test fun `renderers only run while resumed and screen interactive`() {
        assertTrue(isLockScreenFaceVisible(resumed = true, interactive = true))
        assertFalse(isLockScreenFaceVisible(resumed = false, interactive = true))
        assertFalse(isLockScreenFaceVisible(resumed = true, interactive = false))
        assertFalse(isLockScreenFaceVisible(resumed = false, interactive = false))
    }

    @Test fun `custom descriptors preserve native runtime type and source directory`() {
        val builtin = WatchFaceDescriptor(BUILT_IN_WATCHFACE_ID, WatchFaceType.BUILTIN, "Default")
        for (type in listOf(WatchFaceType.WATCHMAKER, WatchFaceType.JBWATCH)) {
            val imported = WatchFaceDescriptor("imported", type, "Custom", sourceDirPath = "/faces/custom")
            assertSame(imported, resolveLockScreenFace("imported", listOf(builtin, imported)))
            assertSame(builtin, resolveLockScreenFace("missing", listOf(builtin, imported)))
        }
    }
}
