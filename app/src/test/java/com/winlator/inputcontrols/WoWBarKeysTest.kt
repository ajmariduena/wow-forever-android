package com.winlator.inputcontrols

import org.junit.Assert.*
import org.junit.Test

class WoWBarKeysTest {
    private val s = WoWBarKeys()
    private val lt = WoWBarKeys.Key.LT
    private val rt = WoWBarKeys.Key.RT
    @Test fun holdReleasesAfterChord() {
        s.down(lt, 1, 0); s.pressAbility(); s.releaseAbility(500); s.up(1, 600)
        assertNull(s.armed())
    }
    @Test fun longHoldDoesNotLatch() {
        s.down(lt, 1, 0); s.up(1, 350); assertNull(s.armed())
    }
    @Test fun tapOneShotWaitsForAbilityReleaseAndGap() {
        s.down(lt, 1, 0); s.up(1, 100); s.pressAbility(); s.tick(4000)
        assertEquals(lt, s.armed()); s.releaseAbility(4100); s.tick(4159)
        assertEquals(lt, s.armed()); s.tick(4160); assertNull(s.armed())
    }
    @Test fun expiry() {
        s.down(lt, 1, 0); s.up(1, 100); s.tick(3099); assertEquals(lt, s.armed())
        s.tick(3100); assertNull(s.armed())
    }
    @Test fun doubleTapLocksAndTapUnlocks() {
        s.down(lt, 1, 0); s.up(1, 100); s.down(lt, 2, 200); s.up(2, 250)
        assertTrue(s.locked()); s.pressAbility(); s.releaseAbility(300); s.tick(9000)
        assertEquals(lt, s.armed()); s.down(lt, 3, 9100); assertNull(s.armed())
    }
    @Test fun switchAndCancel() {
        s.down(lt, 1, 0); s.up(1, 100); s.down(rt, 2, 200); s.up(2, 250)
        assertEquals(rt, s.armed()); assertFalse(s.locked())
        s.down(rt, 3, 700); assertNull(s.armed())
    }
    @Test fun slideKeepsModifierThroughAbilityRelease() {
        s.down(lt, 1, 0); s.pressAbility(); s.up(1, 500)
        assertEquals(lt, s.armed()); s.releaseAbility(500); s.tick(559)
        assertEquals(lt, s.armed()); s.tick(560); assertNull(s.armed())
    }
    @Test fun resetClearsLockPointerAndDeadline() {
        s.down(WoWBarKeys.Key.LBRB, 1, 0); s.up(1, 100)
        s.down(WoWBarKeys.Key.LBRB, 2, 200); s.reset()
        assertNull(s.armed()); assertFalse(s.locked()); assertEquals(-1, s.pointer()); assertEquals(-1L, s.deadline())
    }
}
