package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameConfigTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun configWtf(root: File) = File(root, "${WowClientDownloader.FLAVOR_DIR}/WTF/Config.wtf")

    private fun portals(root: File) = configWtf(root).readLines().filter { it.startsWith("SET portal ") }

    private fun install(branch: String, config: String? = null): File {
        val root = tempFolder.newFolder()
        File(root, WowClientDownloader.BUILD_INFO).writeText(
            "Branch!STRING:0|Product!STRING:0\n$branch|${WowClientDownloader.TARGET_PRODUCT}\n",
        )
        config?.let { configWtf(root).apply { parentFile?.mkdirs() }.writeText(it) }
        return root
    }

    @Test
    fun noRegionPicked_newConfig_usesInstallRegion() {
        val root = install("eu")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    @Test
    fun noRegionPicked_keepsExistingPortal() {
        val root = install("us", "SET portal \"EU\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    @Test
    fun pickedRegion_replacesExistingPortal() {
        val root = install("us", "SET portal \"US\"\nSET gxApi \"D3D11\"\n")
        ensureGameConfig(root, "1920x1080", GameRegion.EU)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    @Test
    fun resolution_alwaysMatchesContainer() {
        val root = install("us", "SET gxFullscreenResolution \"1280x720\"\nSET gxWindowedResolution \"953x720\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(
            listOf("SET gxFullscreenResolution \"1920x1080\"", "SET gxWindowedResolution \"1920x1080\""),
            configWtf(root).readLines().filter { it.contains("Resolution ") },
        )
    }

    @Test fun newConfigIncludesCameraDefaults() {
        val root = install("us")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET GamePadCameraYawSpeed \"3\"", "SET GamePadCameraPitchSpeed \"2\""),
            configWtf(root).readLines().filter { it.startsWith("SET GamePadCamera") })
    }

    @Test fun existingConfigReceivesMissingCameraDefaultsOnlyOnce() {
        val root = install("us", "SET portal \"US\"\n")
        ensureGameConfig(root, "1920x1080", null)
        val once = configWtf(root).readText()
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(once, configWtf(root).readText())
        assertEquals(2, configWtf(root).readLines().count { it.startsWith("SET GamePadCamera") })
    }

    @Test fun userCameraValuesSurviveIncludingCaseAndWhitespace() {
        val saved = "  set\tgamepadcamerayawspeed\t\"1.7\""
        val root = install("us", "$saved\nSET GamePadCameraPitchSpeed \"0.8\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf(saved, "SET GamePadCameraPitchSpeed \"0.8\""),
            configWtf(root).readLines().filter { it.contains("gamepadcamera", ignoreCase = true) })
    }

    @Test fun partialCameraConfigKeepsYawAndAddsPitch() {
        val root = install("us", "SET GamePadCameraYawSpeed \"4\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET GamePadCameraYawSpeed \"4\"", "SET GamePadCameraPitchSpeed \"2\""),
            configWtf(root).readLines().filter { it.startsWith("SET GamePadCamera") })
    }
}
