package com.example.data.scanner

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.model.CurrentNetworkInfo
import com.example.data.model.WifiNetwork
import com.example.data.model.WifiSecurityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled == true
    }

    @SuppressLint("MissingPermission")
    suspend fun getConnectedWifiInfo(): CurrentNetworkInfo = withContext(Dispatchers.IO) {
        try {
            val isWifi = isWifiConnected()
            var ssid: String? = null
            var bssid: String? = null
            var rssi = 0
            var freq = 0
            var linkSpeed = 0
            var ipStr: String? = null
            var gatewayStr: String? = null
            var subnetMask: String? = null

            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.ipAddress != 0) {
                ipStr = NetworkUtils.intToIp(dhcpInfo.ipAddress)
                gatewayStr = NetworkUtils.intToIp(dhcpInfo.gateway)
                val netmaskInt = dhcpInfo.netmask
                if (netmaskInt != 0) {
                    subnetMask = NetworkUtils.intToIp(netmaskInt)
                }
            }

            // Fallback for IP from NetworkInterface
            if (ipStr == null || ipStr == "0.0.0.0") {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (intf.name.contains("wlan") || intf.name.contains("eth") || intf.name.contains("wifi")) {
                        val addrs = intf.inetAddresses
                        while (addrs.hasMoreElements()) {
                            val addr = addrs.nextElement()
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                ipStr = addr.hostAddress
                                break
                            }
                        }
                    }
                }
            }

            val connectionInfo: WifiInfo? = wifiManager?.connectionInfo
            if (connectionInfo != null) {
                val rawSsid = connectionInfo.ssid
                ssid = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid != "0x") {
                    rawSsid.removePrefix("\"").removeSuffix("\"")
                } else {
                    null
                }
                bssid = connectionInfo.bssid
                rssi = connectionInfo.rssi
                freq = connectionInfo.frequency
                linkSpeed = connectionInfo.linkSpeed
            }

            CurrentNetworkInfo(
                isConnectedToWifi = isWifi,
                ssid = ssid,
                bssid = bssid,
                localIpAddress = ipStr,
                subnetMask = subnetMask,
                subnetCidr = null,
                gatewayIp = gatewayStr,
                linkSpeedMbps = linkSpeed.coerceAtLeast(0),
                frequencyMhz = freq.coerceAtLeast(0),
                rssiDbm = rssi
            )
        } catch (e: Exception) {
            CurrentNetworkInfo(isConnectedToWifi = isWifiConnected())
        }
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @SuppressLint("MissingPermission")
    suspend fun scanNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WifiNetwork>()
        try {
            val scanResults: List<ScanResult>? = wifiManager?.scanResults
            val currentInfo = getConnectedWifiInfo()

            if (!scanResults.isNullOrEmpty()) {
                for (sr in scanResults) {
                    val ssid = if (sr.SSID.isNullOrEmpty()) "<Hidden SSID>" else sr.SSID
                    val channel = NetworkUtils.frequencyToChannel(sr.frequency)
                    val band = NetworkUtils.frequencyToBand(sr.frequency)
                    val security = NetworkUtils.parseSecurityType(sr.capabilities)
                    val level = WifiManager.calculateSignalLevel(sr.level, 5)
                    val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        NetworkUtils.parseWifiStandard(sr.wifiStandard, sr.frequency)
                    } else {
                        if (sr.frequency > 5000) "Wi-Fi 5 (802.11ac)" else "Wi-Fi 4 (802.11n)"
                    }
                    val channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        NetworkUtils.parseChannelWidth(sr.channelWidth)
                    } else {
                        "20 MHz"
                    }
                    val isConnected = currentInfo.isConnectedToWifi &&
                            (currentInfo.bssid.equals(sr.BSSID, ignoreCase = true) ||
                             currentInfo.ssid.equals(sr.SSID, ignoreCase = true))

                    results.add(
                        WifiNetwork(
                            ssid = ssid,
                            bssid = sr.BSSID ?: "00:00:00:00:00:00",
                            rssi = sr.level,
                            signalLevel = level,
                            frequencyMhz = sr.frequency,
                            channel = channel,
                            band = band,
                            securityType = security,
                            capabilities = sr.capabilities ?: "",
                            standard = standard,
                            channelWidth = channelWidth,
                            isCurrentConnection = isConnected,
                            ipAddress = if (isConnected) currentInfo.localIpAddress else null,
                            linkSpeedMbps = if (isConnected) currentInfo.linkSpeedMbps else null,
                            estimatedDistanceMeters = NetworkUtils.estimateDistance(sr.frequency, sr.level),
                            timestamp = sr.timestamp
                        )
                    )
                }
            }

        } catch (e: Exception) {
            // Empty/unavailable scan results remain empty; never substitute synthetic networks.
        }

        // Sort: connected first, then highest signal (least negative RSSI)
        results.sortedWith(
            compareByDescending<WifiNetwork> { it.isCurrentConnection }
                .thenByDescending { it.rssi }
        )
    }

    @SuppressLint("MissingPermission")
    fun triggerHardwareScan(): Boolean {
        return try {
            wifiManager?.startScan() ?: false
        } catch (e: Exception) {
            false
        }
    }
}
