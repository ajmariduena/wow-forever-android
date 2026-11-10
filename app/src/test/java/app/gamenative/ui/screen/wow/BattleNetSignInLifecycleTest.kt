package app.gamenative.ui.screen.wow

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BattleNetSignInLifecycleTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun launchFileIsWrittenInClientFolderAndBothFlavorsAreRemoved() {
        val root = folder.newFolder()
        val login = BattleNetSignIn.Login("sample@example.invalid", "SamplePass!")
        BattleNetSignIn.writeLoginFile(root, login)
        val current = File(root, "${WowClientDownloader.FLAVOR_DIR}/login.txt")
        assertEquals("${login.email}\n${login.password}\n", current.readText())
        val legacy = File(root, "${WowClientDownloader.LEGACY_FLAVOR_DIR}/login.txt")
        legacy.parentFile!!.mkdirs()
        legacy.writeText("old credentials")
        BattleNetSignIn.removeLoginFile(root)
        assertFalse(current.exists())
        assertFalse(legacy.exists())
        BattleNetSignIn.removeLoginFile(root)
    }
    @Test fun cleanupTriggersOnHotfixThresholdOrTimeout() {
        assertFalse(BattleNetSignIn.shouldRemoveLoginFile(0, 0))
        assertFalse(BattleNetSignIn.shouldRemoveLoginFile(599_999, 50_000))
        assertTrue(BattleNetSignIn.shouldRemoveLoginFile(1000, 50_001))
        assertTrue(BattleNetSignIn.shouldRemoveLoginFile(600_000, 0))
    }
    @Test fun noSavedLoginMeansNoRetryAvailability() {
        val context = RuntimeEnvironment.getApplication()
        BattleNetSignIn.forget(context)
        assertFalse(BattleNetSignIn.hasSavedLogin(context))
        assertFalse(WoWRetryLogin.available(null, context))
    }
    @Test fun retryIsOnlyAvailableForWoWWithBothSavedFields() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("battle_net_login", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putString("email", "sample@example.invalid").apply()
        val wow = com.winlator.container.Container("wow_forever")
        assertFalse(WoWRetryLogin.available(wow, context))
        prefs.edit().putString("password", "encrypted-placeholder").apply()
        assertTrue(WoWRetryLogin.available(wow, context))
        assertFalse(WoWRetryLogin.available(com.winlator.container.Container("other_game"), context))
    }
    @Test fun buildingRetryNeverRecreatesPlaintextLoginFile() {
        val root = folder.newFolder()
        BattleNetSignIn.writeLoginFile(root, BattleNetSignIn.Login("sample@example.invalid", "SamplePass!"))
        BattleNetSignIn.removeLoginFile(root)
        assertNotNull(WoWRetrySequence.build("SamplePass!", true))
        assertFalse(File(root, "${WowClientDownloader.FLAVOR_DIR}/login.txt").exists())
    }
}
