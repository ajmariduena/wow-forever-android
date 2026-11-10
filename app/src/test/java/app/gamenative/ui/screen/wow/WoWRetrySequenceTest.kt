package app.gamenative.ui.screen.wow

import android.view.KeyEvent
import app.gamenative.externaldisplay.GameTextKeyMapping
import org.junit.Assert.*
import org.junit.Test

class WoWRetrySequenceTest {
    @Test fun allPrintableAsciiCanBeMappedBeforeTyping() {
        for (char in ' '..'~') assertNotNull(GameTextKeyMapping.map(char))
    }
    @Test fun punctuationUsesCorrectPhysicalKeyAndShift() {
        val cases = mapOf(
            '@' to (KeyEvent.KEYCODE_2 to true),
            '.' to (KeyEvent.KEYCODE_PERIOD to false),
            '_' to (KeyEvent.KEYCODE_MINUS to true),
            '-' to (KeyEvent.KEYCODE_MINUS to false),
            '+' to (KeyEvent.KEYCODE_EQUALS to true),
            '!' to (KeyEvent.KEYCODE_1 to true),
            'A' to (KeyEvent.KEYCODE_A to true),
            'z' to (KeyEvent.KEYCODE_Z to false),
        )
        for ((char, expected) in cases) {
            val stroke = GameTextKeyMapping.map(char)!!
            assertEquals(expected.first, stroke.keyCode)
            assertEquals(expected.second, stroke.requiresShift)
        }
    }
    @Test fun unknownCharactersAreRejectedInsteadOfSilentlyDropped() {
        assertNull(GameTextKeyMapping.map('é'))
        assertNull(WoWRetrySequence.build("testé", true))
        assertNull(WoWRetrySequence.build("test\n", true))
        assertNull(WoWRetrySequence.build("test\t", true))
        assertNull(WoWRetrySequence.build("", true))
    }
    @Test fun noConfirmationMeansNoInputEvenWithSavedPassword() {
        assertNull(WoWRetrySequence.build("Test!", false))
    }
    @Test fun sequenceReplacesOnlySelectedFieldWithoutSubmittingOrChangingFocus() {
        val plan = WoWRetrySequence.build("Ab!", true)!!
        assertEquals(5, plan.size)
        assertEquals(KeyEvent.KEYCODE_A, plan[0].keyCode)
        assertTrue(plan[0].control)
        assertEquals(KeyEvent.KEYCODE_DEL, plan[1].keyCode)
        assertEquals(KeyEvent.KEYCODE_A, plan[2].keyCode)
        assertTrue(plan[2].shift)
        assertEquals(KeyEvent.KEYCODE_B, plan[3].keyCode)
        assertFalse(plan[3].shift)
        assertEquals(KeyEvent.KEYCODE_1, plan[4].keyCode)
        assertTrue(plan[4].shift)
        assertTrue(plan.none { it.keyCode == KeyEvent.KEYCODE_ENTER || it.keyCode == KeyEvent.KEYCODE_TAB })
        assertTrue(plan.drop(1).none { it.control })
    }
    @Test fun credentialObjectDoesNotExposeSecretsWhenFormatted() {
        val login = BattleNetSignIn.Login("sample@example.invalid", "SamplePass!")
        assertFalse(login.toString().contains(login.email))
        assertFalse(login.toString().contains(login.password))
    }
}
