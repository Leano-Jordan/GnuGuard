package com.example.data.scanner

import com.example.data.model.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun blankCapabilitiesAreUnknownNotOpen() {
        assertEquals(WifiSecurityType.UNKNOWN, NetworkUtils.parseSecurityType(""))
        assertEquals(WifiSecurityType.UNKNOWN, NetworkUtils.parseSecurityType("   "))
    }

    @Test
    fun enterpriseAndEnhancedOpenAreNotMisclassifiedAsOpen() {
        assertEquals(WifiSecurityType.EAP, NetworkUtils.parseSecurityType("[RSN-EAP-CCMP][ESS]"))
        assertEquals(WifiSecurityType.OWE, NetworkUtils.parseSecurityType("[OWE][ESS]"))
    }

    @Test
    fun explicitEssWithoutSecurityIsOpen() {
        assertEquals(WifiSecurityType.OPEN, NetworkUtils.parseSecurityType("[ESS]"))
    }

    @Test
    fun wpa2IsStillRecognized() {
        assertEquals(WifiSecurityType.WPA2_PSK, NetworkUtils.parseSecurityType("[WPA2-PSK-CCMP][ESS]"))
    }
}
