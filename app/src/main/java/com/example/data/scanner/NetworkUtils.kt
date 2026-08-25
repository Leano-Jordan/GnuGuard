package com.example.data.scanner

import android.net.wifi.ScanResult
import com.example.data.model.DeviceType
import com.example.data.model.DiscoveredService
import com.example.data.model.SecurityLevel
import com.example.data.model.WifiSecurityType
import java.net.InetAddress
import kotlin.math.pow

object NetworkUtils {

    fun frequencyToChannel(freqMhz: Int): Int {
        return when {
            freqMhz == 2484 -> 14
            freqMhz in 2412..2472 -> (freqMhz - 2412) / 5 + 1
            freqMhz in 5170..5825 -> (freqMhz - 5170) / 5 + 34
            freqMhz in 5925..7125 -> (freqMhz - 5925) / 5 + 1
            else -> 0
        }
    }

    fun frequencyToBand(freqMhz: Int): String {
        return when {
            freqMhz in 2400..2495 -> "2.4 GHz"
            freqMhz in 5150..5895 -> "5 GHz"
            freqMhz in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }
    }

    fun parseSecurityType(capabilities: String): WifiSecurityType {
        val capUpper = capabilities.uppercase()
        return when {
            capUpper.contains("SAE") || capUpper.contains("WPA3") -> WifiSecurityType.WPA3_SAE
            capUpper.contains("WPA2-PSK") && capUpper.contains("WPA-PSK") -> WifiSecurityType.WPA_WPA2_MIXED
            capUpper.contains("WPA2") -> WifiSecurityType.WPA2_PSK
            capUpper.contains("WPA") -> WifiSecurityType.WPA_WPA2_MIXED
            capUpper.contains("EAP") -> WifiSecurityType.EAP
            capUpper.contains("OWE") -> WifiSecurityType.OWE
            capUpper.contains("WEP") -> WifiSecurityType.WEP
            !capUpper.contains("WPA") && !capUpper.contains("WEP") -> WifiSecurityType.OPEN
            else -> WifiSecurityType.UNKNOWN
        }
    }

    fun parseChannelWidth(channelWidth: Int): String {
        return when (channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
            else -> "20 MHz"
        }
    }

    fun parseWifiStandard(standard: Int, freqMhz: Int): String {
        // Standard constants from ScanResult (API 30+)
        return when (standard) {
            4 -> "Wi-Fi 4 (802.11n)"
            5 -> "Wi-Fi 5 (802.11ac)"
            6 -> if (freqMhz > 5900) "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
            7 -> "Wi-Fi 7 (802.11be)"
            1 -> "Legacy (802.11a)"
            2 -> "Legacy (802.11b)"
            3 -> "Legacy (802.11g)"
            else -> if (freqMhz > 5000) "Wi-Fi 5/6" else "Wi-Fi 4 (802.11n)"
        }
    }

    fun estimateDistance(freqMhz: Int, rssiDbm: Int): Double {
        // Free-Space Path Loss approximation formula:
        // FSPL = 20*log10(d) + 20*log10(f) - 27.55
        // Reference power at 1 meter ~ -40dBm
        val exp = (27.55 - (20 * kotlin.math.log10(freqMhz.toDouble())) + kotlin.math.abs(rssiDbm)) / 20.0
        val dist = 10.0.pow(exp)
        return kotlin.math.round(dist * 10) / 10.0
    }

    fun intToIp(ipInt: Int): String {
        return "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
    }

    fun inferDeviceType(
        ip: String,
        hostname: String?,
        services: List<DiscoveredService>,
        openPorts: List<Int>,
        isGateway: Boolean
    ): DeviceType {
        if (isGateway) return DeviceType.ROUTER_GATEWAY

        val hostLower = hostname?.lowercase() ?: ""
        val serviceTypes = services.map { it.serviceType.lowercase() }
        val serviceNames = services.map { it.serviceName.lowercase() }

        // Check for Smart TV / Cast / Streaming
        if (serviceTypes.any { it.contains("googlecast") || it.contains("airplay") || it.contains("spotify") || it.contains("dial") } ||
            serviceNames.any { it.contains("chromecast") || it.contains("appletv") || it.contains("roku") || it.contains("firetv") || it.contains("tv") } ||
            openPorts.contains(8008) || openPorts.contains(8009) || hostLower.contains("tv") || hostLower.contains("chromecast")
        ) {
            return DeviceType.SMART_TV_CAST
        }

        // Check for Printer / IoT
        if (serviceTypes.any { it.contains("ipp") || it.contains("printer") || it.contains("pdl") } ||
            openPorts.contains(9100) || openPorts.contains(631) || hostLower.contains("printer") || hostLower.contains("epson") || hostLower.contains("hp")
        ) {
            return DeviceType.IOT_PRINTER
        }

        // Check for Smartphone / Tablet
        if (hostLower.contains("iphone") || hostLower.contains("android") || hostLower.contains("galaxy") ||
            hostLower.contains("pixel") || hostLower.contains("oneplus") || hostLower.contains("xiaomi") ||
            hostLower.contains("redmi") || hostLower.contains("huawei") || hostLower.contains("phone") ||
            serviceNames.any { it.contains("iphone") || it.contains("galaxy") || it.contains("pixel") }
        ) {
            return if (hostLower.contains("ipad") || hostLower.contains("tab")) DeviceType.TABLET else DeviceType.PHONE
        }

        // Check for Computer / Workstation / Laptop
        if (serviceTypes.any { it.contains("workstation") || it.contains("smb") || it.contains("ssh") } ||
            openPorts.contains(22) || openPorts.contains(445) || openPorts.contains(139) || openPorts.contains(3389) ||
            hostLower.contains("macbook") || hostLower.contains("desktop") || hostLower.contains("laptop") ||
            hostLower.contains("pc") || hostLower.contains("win") || hostLower.contains("linux") || hostLower.contains("thinkpad")
        ) {
            return DeviceType.COMPUTER
        }

        // Check common Router ports on first/last IPs
        if (openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080)) {
            val lastOctet = ip.split(".").lastOrNull()?.toIntOrNull() ?: -1
            if (lastOctet == 1 || lastOctet == 254) {
                return DeviceType.ROUTER_GATEWAY
            }
        }

        return DeviceType.UNKNOWN
    }

    fun inferVendor(hostname: String?, services: List<DiscoveredService>): String? {
        val text = (hostname ?: "") + " " + services.joinToString(" ") { "${it.serviceName} ${it.serviceType}" }
        val lower = text.lowercase()
        return when {
            lower.contains("apple") || lower.contains("iphone") || lower.contains("ipad") || lower.contains("macbook") || lower.contains("airplay") -> "Apple Inc."
            lower.contains("google") || lower.contains("pixel") || lower.contains("chromecast") || lower.contains("nest") -> "Google LLC"
            lower.contains("samsung") || lower.contains("galaxy") -> "Samsung Electronics"
            lower.contains("amazon") || lower.contains("echo") || lower.contains("firetv") || lower.contains("kindle") -> "Amazon"
            lower.contains("tp-link") || lower.contains("tplink") || lower.contains("deco") || lower.contains("kasa") -> "TP-Link"
            lower.contains("asus") || lower.contains("rt-") -> "ASUS"
            lower.contains("netgear") || lower.contains("nighthawk") || lower.contains("orbi") -> "NETGEAR"
            lower.contains("sony") || lower.contains("bravia") || lower.contains("playstation") -> "Sony"
            lower.contains("lg") || lower.contains("webos") -> "LG Electronics"
            lower.contains("microsoft") || lower.contains("xbox") || lower.contains("surface") -> "Microsoft"
            lower.contains("hp") || lower.contains("deskjet") || lower.contains("laserjet") -> "HP Inc."
            lower.contains("epson") -> "Epson"
            lower.contains("canon") -> "Canon"
            lower.contains("raspberry") || lower.contains("raspberrypi") -> "Raspberry Pi Foundation"
            lower.contains("synology") -> "Synology Inc."
            lower.contains("ubiquiti") || lower.contains("unifi") -> "Ubiquiti Networks"
            else -> null
        }
    }
}
