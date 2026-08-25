package com.example.data.model

enum class DeviceType(val label: String) {
    PHONE("Smartphone"),
    TABLET("Tablet"),
    COMPUTER("Computer / Laptop"),
    ROUTER_GATEWAY("Router / Gateway"),
    SMART_TV_CAST("Smart TV / Cast"),
    IOT_PRINTER("Printer / IoT Device"),
    UNKNOWN("Network Device")
}

data class DiscoveredService(
    val serviceName: String,
    val serviceType: String,
    val port: Int,
    val host: String = "",
    val attributes: Map<String, String> = emptyMap()
)

data class LanDevice(
    val id: String, // IP or unique identifier
    val ipAddress: String,
    val macAddress: String? = null,
    val hostName: String? = null,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val discoveredServices: List<DiscoveredService> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val responseTimeMs: Long = 0L,
    val isReachable: Boolean = true,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val vendor: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)
