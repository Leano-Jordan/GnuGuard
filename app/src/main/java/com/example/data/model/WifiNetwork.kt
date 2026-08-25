package com.example.data.model

enum class WifiSecurityType(val label: String, val level: SecurityLevel) {
    WPA3_SAE("WPA3 (SAE)", SecurityLevel.SECURE),
    WPA2_PSK("WPA2 (PSK)", SecurityLevel.SECURE),
    WPA_WPA2_MIXED("WPA/WPA2", SecurityLevel.MODERATE),
    WEP("WEP (Insecure)", SecurityLevel.INSECURE),
    OPEN("Open / None", SecurityLevel.INSECURE),
    EAP("WPA-Enterprise", SecurityLevel.SECURE),
    OWE("OWE (Enhanced Open)", SecurityLevel.SECURE),
    UNKNOWN("Unknown", SecurityLevel.UNKNOWN)
}

enum class SecurityLevel {
    SECURE,
    MODERATE,
    INSECURE,
    UNKNOWN
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int, // dBm, e.g., -55
    val signalLevel: Int, // 0 to 4
    val frequencyMhz: Int,
    val channel: Int,
    val band: String, // "2.4 GHz", "5 GHz", "6 GHz"
    val securityType: WifiSecurityType,
    val capabilities: String,
    val standard: String, // Wi-Fi 6, Wi-Fi 5, etc.
    val channelWidth: String,
    val isCurrentConnection: Boolean = false,
    val ipAddress: String? = null,
    val linkSpeedMbps: Int? = null,
    val estimatedDistanceMeters: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
