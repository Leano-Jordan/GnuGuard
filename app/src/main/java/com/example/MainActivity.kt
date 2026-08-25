package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.DeviceDetailDialog
import com.example.ui.screens.LanDiscoveryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.WifiDetailDialog
import com.example.ui.screens.WifiScannerScreen
import com.example.ui.theme.GnuGuardTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
            val isAppLocked by viewModel.isAppLocked.collectAsState()

            GnuGuardTheme(themeMode = themeMode) {
                if (!hasCompletedOnboarding) {
                    OnboardingScreen(
                        onComplete = { viewModel.completeOnboarding() }
                    )
                } else if (isAppLocked) {
                    BiometricLockScreen(
                        onUnlockRequested = { triggerBiometricUnlock() }
                    )
                } else {
                    MainAppScaffold(
                        viewModel = viewModel,
                        onRequestLockPrompt = {
                            viewModel.lockApp()
                            triggerBiometricUnlock()
                        }
                    )
                }
            }
        }
    }

    private fun triggerBiometricUnlock() {
        if (!viewModel.biometricAuthManager.canAuthenticate()) {
            // If hardware / PIN is not enrolled, unlock directly
            viewModel.unlockApp()
            return
        }

        viewModel.biometricAuthManager.promptBiometricAuth(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            onSuccess = {
                viewModel.unlockApp()
            },
            onError = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: MainViewModel,
    onRequestLockPrompt: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val currentNetworkInfo by viewModel.currentNetworkInfo.collectAsState()

    // Wi-Fi State
    val wifiNetworks by viewModel.wifiNetworks.collectAsState()
    val isWifiScanning by viewModel.isWifiScanning.collectAsState()
    val wifiFilter by viewModel.wifiFilter.collectAsState()
    val wifiSort by viewModel.wifiSort.collectAsState()
    val selectedWifi by viewModel.selectedWifi.collectAsState()

    // LAN State
    val lanDevices by viewModel.lanDevices.collectAsState()
    val isLanScanning by viewModel.isLanScanning.collectAsState()
    val lanProgress by viewModel.lanScanProgress.collectAsState()
    val lanScanningIp by viewModel.lanScanningIp.collectAsState()
    val deviceFilter by viewModel.deviceFilter.collectAsState()
    val selectedLanDevice by viewModel.selectedLanDevice.collectAsState()

    // Security & Preferences State
    val securityAudit by viewModel.securityAudit.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val autoScanInterval by viewModel.autoScanInterval.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> stringResource(R.string.title_wifi_scanner)
                            1 -> stringResource(R.string.title_network_discovery)
                            else -> stringResource(R.string.title_settings)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi Scanner") },
                    label = { Text("Wi-Fi") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_wifi")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    icon = { Icon(Icons.Default.Lan, contentDescription = "LAN Devices") },
                    label = { Text("LAN Devices") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_lan")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Security & Settings") },
                    label = { Text("Security") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_security")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> WifiScannerScreen(
                    currentNetworkInfo = currentNetworkInfo,
                    networks = wifiNetworks,
                    isScanning = isWifiScanning,
                    filter = wifiFilter,
                    sort = wifiSort,
                    onFilterChanged = { viewModel.setWifiFilter(it) },
                    onSortChanged = { viewModel.setWifiSort(it) },
                    onScanRequested = { viewModel.startWifiScan() },
                    onNetworkSelected = { viewModel.selectWifi(it) }
                )
                1 -> LanDiscoveryScreen(
                    currentNetworkInfo = currentNetworkInfo,
                    devices = lanDevices,
                    isScanning = isLanScanning,
                    progress = lanProgress,
                    scanningIp = lanScanningIp,
                    filter = deviceFilter,
                    onFilterChanged = { viewModel.setDeviceFilter(it) },
                    onStartScan = { viewModel.startLanScan() },
                    onCancelScan = { viewModel.cancelLanScan() },
                    onDeviceSelected = { viewModel.selectLanDevice(it) }
                )
                2 -> SecurityScreen(
                    securityAudit = securityAudit,
                    themeMode = themeMode,
                    isBiometricEnabled = isBiometricEnabled,
                    autoScanInterval = autoScanInterval,
                    onThemeModeChanged = { viewModel.setThemeMode(it) },
                    onBiometricToggle = { viewModel.setBiometricEnabled(it) },
                    onAutoScanIntervalChanged = { viewModel.setAutoScanInterval(it) },
                    onReplayOnboarding = { viewModel.resetOnboarding() },
                    onTestLock = onRequestLockPrompt
                )
            }

            // Detail Dialogs
            selectedWifi?.let { wifi ->
                WifiDetailDialog(
                    network = wifi,
                    onDismiss = { viewModel.selectWifi(null) }
                )
            }

            selectedLanDevice?.let { device ->
                DeviceDetailDialog(
                    device = device,
                    onDismiss = { viewModel.selectLanDevice(null) }
                )
            }
        }
    }
}
