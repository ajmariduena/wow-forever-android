package com.winlator.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
class MachineIdentityTest {
    private lateinit var dir: File
    private val now = 1_794_000_000L

    @Before
    fun setUp() {
        dir = createTempDirectory(prefix = "machine-identity-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun load_generatesWindowsLikeIdentityAndPersistsIt() {
        val file = File(dir, MachineIdentity.FILE_NAME)

        val first = MachineIdentity.load(file, now)
        val second = MachineIdentity.load(file, now)

        assertTrue(file.isFile)
        assertTrue(first.machineGuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertTrue(first.computerName.matches(Regex("DESKTOP-[A-Z0-9]{7}")))
        assertTrue(first.volumeSerial.matches(Regex("[0-9a-f]{8}")))
        assertTrue(first.productId.matches(Regex("\\d{5}-\\d{5}-\\d{5}-AA\\d{3}")))
        assertTrue(first.installDate in 1 until now)
        assertEquals(first.machineGuid, second.machineGuid)
        assertEquals(first.computerName, second.computerName)
        assertEquals(first.volumeSerial, second.volumeSerial)
        assertEquals(first.productId, second.productId)
        assertEquals(first.installDate, second.installDate)
    }

    @Test
    fun load_differsBetweenInstalls() {
        val a = MachineIdentity.load(File(dir, "a.properties"), now)
        val b = MachineIdentity.load(File(dir, "b.properties"), now)

        assertNotEquals(a.machineGuid, b.machineGuid)
    }

    @Test
    fun load_replacesOnlyInvalidValues() {
        val file = File(dir, MachineIdentity.FILE_NAME)
        file.writeText(
            """
            machineGuid=not-a-guid
            computerName=DESKTOP-ABC1234
            volumeSerial=1a2b3c4d
            productId=00330-80000-00000-AA478
            installDate=1700000000
            """.trimIndent(),
        )

        val identity = MachineIdentity.load(file, now)

        assertNotEquals("not-a-guid", identity.machineGuid)
        assertEquals("DESKTOP-ABC1234", identity.computerName)
        assertEquals("1a2b3c4d", identity.volumeSerial)
        assertEquals("00330-80000-00000-AA478", identity.productId)
        assertEquals(1_700_000_000, identity.installDate)
        assertEquals(identity.machineGuid, MachineIdentity.load(file, now).machineGuid)
    }

    @Test
    fun applyTo_overwritesSharedPrefixValues() {
        val prefix = File(dir, ".wine").apply { File(this, "drive_c").mkdirs() }
        val systemReg = File(prefix, "system.reg")
        systemReg.writeText(
            """
            WINE REGISTRY Version 2
            ;; All keys relative to \\Machine

            [Software\\Microsoft\\Cryptography] 1700000000
            #time=1d9f0e0a0b0c0d0
            "MachineGuid"="87447f37-0000-0000-0000-000000000000"

            [Software\\Microsoft\\Windows NT\\CurrentVersion] 1700000000
            #time=1d9f0e0a0b0c0d0
            "CurrentBuild"="19045"
            "ProductId"="12345-oem-0000001-54321"

            """.trimIndent() + "\n",
        )
        val identity = MachineIdentity.load(File(dir, MachineIdentity.FILE_NAME), now)

        identity.applyTo(prefix)

        val reg = systemReg.readText()
        assertTrue(reg.contains("\"MachineGuid\"=\"${identity.machineGuid}\""))
        assertTrue(!reg.contains("87447f37"))
        assertTrue(reg.contains("\"ProductId\"=\"${identity.productId}\""))
        assertTrue(reg.contains("\"InstallDate\"=dword:%08x".format(identity.installDate)))
        assertTrue(reg.contains("\"CurrentBuild\"=\"19045\""))
        assertEquals("${identity.volumeSerial}\n", File(prefix, "drive_c/.windows-serial").readText())
    }
}
