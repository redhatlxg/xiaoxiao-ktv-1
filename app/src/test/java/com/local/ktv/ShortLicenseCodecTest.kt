package com.local.ktv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortLicenseCodecTest {
    @Test
    fun generatedCodeVerifiesOnlyForItsMachineAndCarriesTerm() {
        val code = ShortLicenseCodec.issue("A1:B2:C3:D4:E5:F6", 30)
        assertTrue(code.matches(Regex("([0-9A-F]{2}:){9}[0-9A-F]{2}")))
        assertEquals(30, ShortLicenseCodec.verify("A1B2C3D4E5F6", code)?.days)
        assertNull(ShortLicenseCodec.verify("001122334455", code))
    }

    @Test
    fun zeroDaysMeansPerpetualAndNonceMakesCodesUnique() {
        val first = ShortLicenseCodec.issue("A1B2C3D4E5F6", 0)
        val second = ShortLicenseCodec.issue("A1B2C3D4E5F6", 0)
        assertEquals(0, ShortLicenseCodec.verify("A1B2C3D4E5F6", first)?.days)
        assertNotNull(ShortLicenseCodec.verify("A1B2C3D4E5F6", second))
        assertTrue(first != second)
    }

    @Test
    fun editedShortCodeFailsAuthentication() {
        val code = ShortLicenseCodec.issue("A1B2C3D4E5F6", 90)
        val hex = code.replace(":", "")
        val changed = (if (hex[0] == '0') '1' else '0') + hex.substring(1)
        assertNull(ShortLicenseCodec.verify("A1B2C3D4E5F6", changed))
    }
}
