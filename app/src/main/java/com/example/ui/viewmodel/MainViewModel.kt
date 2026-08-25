package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuditFinding
import com.example.data.model.CurrentNetworkInfo
import com.example.data.model.DeviceType
import com.example.data.model.LanDevice
import com.example.data.model.NetworkSecurityAudit
import com.example.data.model.SecurityLevel
import com.example.data.model.WifiNetwork
import com.example.data.model.WifiSecurityType
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.BiometricAuthManager
import com.example.data.scanner.LanScanner
import com.example.data.scanner.WifiScanner
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WifiFilter {
    ALL,
    BAND_5GHZ,
    BAND_2GHZ,
    BAND_6GHZ,
    OPEN_UNSECURED,
    SECURED_ONLY
}

enum class WifiSort {
    SIGNAL_DESC,
    SSID_ASC,
    CHANNEL_ASC
}

enum class DeviceFilter {
    ALL,
    PHONES_TABLETS,
    COMPUTERS,
    MEDIA_CAST,
    ROUTERS_GATEWAYS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = AppPreferences(application)
    val biometricAuthManager = BiometricAuthManager(application)
    private val wifiScanner = WifiScanner(application)
    private val lanScanner = LanScanner(application)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
    val hasCompletedOnboarding: StateFlow<Boolean> = preferences.hasCompletedOnboarding
    val isBiometricEnabled: StateFlow<Boolean> = preferences.isBiometricEnabled
    val autoScanInterval: StateFlow<Int> = preferences.autoScanInterval

    // Authentication lock state
    private val _isAppLocked = MutableStateFlow(preferences.isBiometricEnabled.value)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Navigation Tab
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Wi-Fi State
    private val _rawWifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    private val _isWifiScanning = MutableStateFlow(false)
    val isWifiScanning: StateFlow<Boolean> = _isWifiScanning.asStateFlow()

    private val _wifiFilter = MutableStateFlow(WifiFilter.ALL)
    val wifiFilter: StateFlow<WifiFilter> = _wifiFilter.asStateFlow()

    private val _wifiSort = MutableStateFlow(WifiSort.SIGNAL_DESC)
    val wifiSort: StateFlow<WifiSort> = _wifiSort.asStateFlow()

    private val _selectedWifi = MutableStateFlow<WifiNetwork?>(null)
    val selectedWifi: StateFlow<WifiNetwork?> = _selectedWifi.asStateFlow()

    private val _currentNetworkInfo = MutableStateFlow(CurrentNetworkInfo())
    val currentNetworkInfo: StateFlow<CurrentNetworkInfo> = _currentNetworkInfo.asStateFlow()

    // Filtered and Sorted Wi-Fi Networks
    val wifiNetworks: StateFlow<List<WifiNetwork>> = combine(
        _rawWifiNetworks,
        _wifiFilter,
        _wifiSort
    ) { networks, filter, sort ->
        val filtered = when (filter) {
            WifiFilter.ALL -> networks
            WifiFilter.BAND_5GHZ -> networks.filter { it.frequencyMhz in 5000..5899 }
            WifiFilter.BAND_2GHZ -> networks.filter { it.frequencyMhz in 2400..2499 }
            WifiFilter.BAND_6GHZ -> networks.filter { it.frequencyMhz >= 5900 }
            WifiFilter.OPEN_UNSECURED -> networks.filter { it.securityType == WifiSecurityType.OPEN || it.securityType == WifiSecurityType.WEP }
            WifiFilter.SECURED_ONLY -> networks.filter { it.securityType != WifiSecurityType.OPEN && it.securityType != WifiSecurityType.WEP }
        }

        when (sort) {
            WifiSort.SIGNAL_DESC -> filtered.sortedWith(
                compareByDescending<WifiNetwork> { it.isCurrentConnection }
                    .thenByDescending { it.rssi }
            )
            WifiSort.SSID_ASC -> filtered.sortedBy { it.ssid.lowercase() }
            WifiSort.CHANNEL_ASC -> filtered.sortedBy { it.channel }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // LAN Discovery State
    private val _rawLanDevices = MutableStateFlow<List<LanDevice>>(emptyList())
    private val _isLanScanning = MutableStateFlow(false)
    val isLanScanning: StateFlow<Boolean> = _isLanScanning.asStateFlow()

    private val _lanScanProgress = MutableStateFlow(0f)
    val lanScanProgress: StateFlow<Float> = _lanScanProgress.asStateFlow()

    private val _lanScanningIp = MutableStateFlow("")
    val lanScanningIp: StateFlow<String> = _lanScanningIp.asStateFlow()

    private val _deviceFilter = MutableStateFlow(DeviceFilter.ALL)
    val deviceFilter: StateFlow<DeviceFilter> = _deviceFilter.asStateFlow()

    private val _selectedLanDevice = MutableStateFlow<LanDevice?>(null)
    val selectedLanDevice: StateFlow<LanDevice?> = _selectedLanDevice.asStateFlow()

    val lanDevices: StateFlow<List<LanDevice>> = combine(
        _rawLanDevices,
        _deviceFilter
    ) { devices, filter ->
        when (filter) {
            DeviceFilter.ALL -> devices
            DeviceFilter.PHONES_TABLETS -> devices.filter { it.deviceType == DeviceType.PHONE || it.deviceType == DeviceType.TABLET }
            DeviceFilter.COMPUTERS -> devices.filter { it.deviceType == DeviceType.COMPUTER }
            DeviceFilter.MEDIA_CAST -> devices.filter { it.deviceType == DeviceType.SMART_TV_CAST }
            DeviceFilter.ROUTERS_GATEWAYS -> devices.filter { it.deviceType == DeviceType.ROUTER_GATEWAY }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Security Audit
    val securityAudit: StateFlow<NetworkSecurityAudit> = combine(
        _currentNetworkInfo,
        _rawWifiNetworks,
        _rawLanDevices
    ) { netInfo, wifis, devices ->
        calculateSecurityAudit(netInfo, wifis, devices)
    }.stateIn(viewModelScope, SharingStarted.Lazily, NetworkSecurityAudit())

    private var lanScanJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        refreshNetworkInfo()
        startWifiScan()
        lanScanner.startNsdDiscovery()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setWifiFilter(filter: WifiFilter) {
        _wifiFilter.value = filter
    }

    fun setWifiSort(sort: WifiSort) {
        _wifiSort.value = sort
    }

    fun selectWifi(network: WifiNetwork?) {
        _selectedWifi.value = network
    }

    fun setDeviceFilter(filter: DeviceFilter) {
        _deviceFilter.value = filter
    }

    fun selectLanDevice(device: LanDevice?) {
        _selectedLanDevice.value = device
    }

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            val info = wifiScanner.getConnectedWifiInfo()
            _currentNetworkInfo.value = info
        }
    }

    fun startWifiScan() {
        if (_isWifiScanning.value) return
        viewModelScope.launch {
            _isWifiScanning.value = true
            wifiScanner.triggerHardwareScan()
            val info = wifiScanner.getConnectedWifiInfo()
            _currentNetworkInfo.value = info
            val results = wifiScanner.scanNetworks()
            _rawWifiNetworks.value = results
            _isWifiScanning.value = false
        }
    }

    fun startLanScan() {
        if (_isLanScanning.value) return
        lanScanJob?.cancel()
        lanScanJob = viewModelScope.launch {
            _isLanScanning.value = true
            _lanScanProgress.value = 0f
            val netInfo = wifiScanner.getConnectedWifiInfo()
            _currentNetworkInfo.value = netInfo

            val devices = lanScanner.scanSubnet(netInfo) { progress, currentIp, partialDevices ->
                _lanScanProgress.value = progress
                _lanScanningIp.value = currentIp
                _rawLanDevices.value = partialDevices
            }

            _rawLanDevices.value = devices
            _lanScanProgress.value = 1f
            _isLanScanning.value = false
        }
    }

    fun cancelLanScan() {
        lanScanJob?.cancel()
        _isLanScanning.value = false
    }

    fun completeOnboarding() {
        preferences.setOnboardingCompleted(true)
    }

    fun resetOnboarding() {
        preferences.setOnboardingCompleted(false)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferences.setBiometricEnabled(enabled)
        _isAppLocked.value = enabled
    }

    fun setAutoScanInterval(intervalSeconds: Int) {
        preferences.setAutoScanInterval(intervalSeconds)
        startAutoRefreshJob(intervalSeconds)
    }

    private fun startAutoRefreshJob(intervalSeconds: Int) {
        autoRefreshJob?.cancel()
        if (intervalSeconds > 0) {
            autoRefreshJob = viewModelScope.launch {
                while (true) {
                    delay(intervalSeconds * 1000L)
                    if (!_isWifiScanning.value) {
                        startWifiScan()
                    }
                }
            }
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (preferences.isBiometricEnabled.value) {
            _isAppLocked.value = true
        }
    }

    private fun calculateSecurityAudit(
        netInfo: CurrentNetworkInfo,
        wifis: List<WifiNetwork>,
        devices: List<LanDevice>
    ): NetworkSecurityAudit {
        val findings = mutableListOf<AuditFinding>()
        var score = 100

        val currentWifi = wifis.find { it.isCurrentConnection }

        if (currentWifi != null) {
            when (currentWifi.securityType) {
                WifiSecurityType.OPEN -> {
                    score -= 40
                    findings.add(
                        AuditFinding(
                            title = "Unencrypted Open Wi-Fi Network",
                            description = "Your current Wi-Fi network does not use wireless encryption. Any nearby device can intercept unencrypted data packets.",
                            severity = SecurityLevel.INSECURE,
                            recommendation = "Use a VPN and configure WPA2/WPA3 password protection on the router."
                        )
                    )
                }
                WifiSecurityType.WEP -> {
                    score -= 35
                    findings.add(
                        AuditFinding(
                            title = "Deprecated WEP Protocol in Use",
                            description = "WEP cipher keys can be cracked within minutes using standard statistical packet analysis.",
                            severity = SecurityLevel.INSECURE,
                            recommendation = "Upgrade router security settings to WPA2-AES (CCMP) or WPA3-SAE."
                        )
                    )
                }
                WifiSecurityType.WPA_WPA2_MIXED -> {
                    score -= 10
                    findings.add(
                        AuditFinding(
                            title = "Mixed WPA/WPA2 Compatibility",
                            description = "Legacy TKIP ciphers may be allowed, which are vulnerable to collision attacks.",
                            severity = SecurityLevel.MODERATE,
                            recommendation = "Enforce WPA2-AES or WPA3-Personal only."
                        )
                    )
                }
                WifiSecurityType.WPA3_SAE -> {
                    findings.add(
                        AuditFinding(
                            title = "Robust WPA3 Encryption (SAE)",
                            description = "Protected by Simultaneous Authentication of Equals (SAE) with forward secrecy.",
                            severity = SecurityLevel.SECURE,
                            recommendation = "Your wireless handshake is encrypted with state-of-the-art protection."
                        )
                    )
                }
                WifiSecurityType.WPA2_PSK -> {
                    findings.add(
                        AuditFinding(
                            title = "Industry Standard WPA2-PSK",
                            description = "Standard AES-CCMP encryption is active.",
                            severity = SecurityLevel.SECURE,
                            recommendation = "Ensure a strong, non-dictionary passphrase (12+ characters)."
                        )
                    )
                }
                else -> {}
            }
        }

        // Check for Open Wi-Fi networks in vicinity
        val openNetworksCount = wifis.count { it.securityType == WifiSecurityType.OPEN }
        if (openNetworksCount > 0) {
            findings.add(
                AuditFinding(
                    title = "$openNetworksCount Open Nearby Wi-Fi Hotspots Detected",
                    description = "Open networks in the area can be impersonated by rogue evil-twin access points.",
                    severity = SecurityLevel.MODERATE,
                    recommendation = "Avoid connecting to unfamiliar unauthenticated hotspots."
                )
            )
        }

        // Check LAN devices for sensitive open ports
        val telnetOrSshDevices = devices.filter { it.openPorts.contains(23) || it.openPorts.contains(22) }
        if (telnetOrSshDevices.isNotEmpty()) {
            findings.add(
                AuditFinding(
                    title = "Remote Management Ports Exposed (${telnetOrSshDevices.size} devices)",
                    description = "Devices at IP ${telnetOrSshDevices.joinToString { it.ipAddress }} have SSH/Telnet ports open.",
                    severity = SecurityLevel.MODERATE,
                    recommendation = "Verify that SSH/Telnet ports are protected with strong keys and not exposed to the public Internet."
                )
            )
        }

        val clampedScore = score.coerceIn(10, 100)
        val grade = when {
            clampedScore >= 90 -> "A+"
            clampedScore >= 80 -> "A"
            clampedScore >= 70 -> "B"
            clampedScore >= 60 -> "C"
            else -> "F"
        }

        return NetworkSecurityAudit(
            score = clampedScore,
            grade = grade,
            findings = findings
        )
    }
}
