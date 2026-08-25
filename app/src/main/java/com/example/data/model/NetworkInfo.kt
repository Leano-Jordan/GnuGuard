package com.example.data.model

data class CurrentNetworkInfo(
    val isConnectedToWifi: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val localIpAddress: String? = null,
    val subnetMask: String? = null,
    val subnetCidr: String? = null,
    val gatewayIp: String? = null,
    val dns1: String? = null,
    val dns2: String? = null,
    val linkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val rssiDbm: Int = 0,
    val interfaceName: String = "wlan0"
)

data class NetworkSecurityAudit(
    val score: Int = 100, // 0 - 100
    val grade: String = "A+",
    val findings: List<AuditFinding> = emptyList()
)

data class AuditFinding(
    val title: String,
    val description: String,
    val severity: SecurityLevel,
    val recommendation: String
)
