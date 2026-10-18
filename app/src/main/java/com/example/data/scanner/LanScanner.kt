package com.example.data.scanner

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.data.model.CurrentNetworkInfo
import com.example.data.model.DeviceType
import com.example.data.model.DiscoveredService
import com.example.data.model.LanDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class LanScanner(private val context: Context) {

    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    // Common ports to probe active network devices
    private val probePorts = intArrayOf(80, 443, 8080, 5353, 22, 445, 8008, 9100, 62078)

    // mDNS service types to scan
    private val nsdServiceTypes = listOf(
        "_googlecast._tcp.",
        "_airplay._tcp.",
        "_http._tcp.",
        "_workstation._tcp.",
        "_ipp._tcp.",
        "_spotify-connect._tcp."
    )

    private val discoveredNsdServices = ConcurrentHashMap<String, MutableList<DiscoveredService>>()

    fun startNsdDiscovery() {
        if (nsdManager == null) return
        discoveredNsdServices.clear()

        for (serviceType in nsdServiceTypes) {
            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(regType: String) {}
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            try {
                                nsdManager.resolveService(
                                    serviceInfo,
                                    object : NsdManager.ResolveListener {
                                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                            val host = serviceInfo.host?.hostAddress ?: return
                                            val service = DiscoveredService(
                                                serviceName = serviceInfo.serviceName ?: "Unknown Service",
                                                serviceType = serviceInfo.serviceType ?: "",
                                                port = serviceInfo.port,
                                                host = host,
                                                attributes = serviceInfo.attributes?.mapValues {
                                                    String(it.value ?: byteArrayOf())
                                                } ?: emptyMap()
                                            )
                                            discoveredNsdServices.computeIfAbsent(host) { mutableListOf() }.add(service)
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                // Ignore resolve collisions
                            }
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                        override fun onDiscoveryStopped(serviceType: String) {}
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    }
                )
            } catch (e: Exception) {
                // Ignore service discovery registration errors
            }
        }
    }

    suspend fun scanSubnet(
        networkInfo: CurrentNetworkInfo,
        onProgress: (progress: Float, currentIp: String, foundDevices: List<LanDevice>) -> Unit
    ): List<LanDevice> = withContext(Dispatchers.IO) {
        val foundDevices = ConcurrentHashMap<String, LanDevice>()
        val arpTable = readArpTable()

        val localIp = networkInfo.localIpAddress ?: return@withContext emptyList()
        val gatewayIp = networkInfo.gatewayIp

        // Calculate Subnet prefix (e.g. 192.168.1)
        val ipParts = localIp.split(".")
        if (ipParts.size != 4 || ipParts.any { it.toIntOrNull()?.let { octet -> octet in 0..255 } != true }) {
            return@withContext emptyList()
        }
        val subnetPrefix = "${ipParts[0]}.${ipParts[1]}.${ipParts[2]}"
        val myLastOctet = ipParts[3].toInt()

        // Always register Self
        val selfDevice = LanDevice(
            id = localIp,
            ipAddress = localIp,
            macAddress = "Self (Device Protected)",
            hostName = "This Android Device",
            deviceType = DeviceType.PHONE,
            discoveredServices = emptyList(),
            responseTimeMs = 0L,
            isReachable = true,
            isGateway = false,
            isSelf = true,
            vendor = "Google / Android"
        )
        foundDevices[localIp] = selfDevice

        // The configured gateway address is known, but reachability and services are not.
        if (gatewayIp != null && gatewayIp.split(".").size == 4) {
            foundDevices[gatewayIp] = LanDevice(
                id = gatewayIp,
                ipAddress = gatewayIp,
                macAddress = arpTable[gatewayIp],
                hostName = resolveHost(gatewayIp),
                deviceType = DeviceType.ROUTER_GATEWAY,
                discoveredServices = emptyList(),
                openPorts = emptyList(),
                responseTimeMs = 0L,
                isReachable = false,
                isGateway = true,
                isSelf = false,
                vendor = null
            )
        }

        val gatewayLastOctet = gatewayIp?.split(".")?.lastOrNull()?.toIntOrNull()
        val ipsToScan = (1..254).filter { it != myLastOctet && it != gatewayLastOctet }
        val totalIps = ipsToScan.size

        // Batch scan in chunks of 24 to preserve battery and respect Android threading limits
        val chunkSize = 24
        val chunks = ipsToScan.chunked(chunkSize)

        var scannedCount = 0

        for (chunk in chunks) {
            coroutineScope {
                val deferreds = chunk.map { lastOctet ->
                    async(Dispatchers.IO) {
                        val targetIp = "$subnetPrefix.$lastOctet"
                        val device = probeIp(targetIp, arpTable[targetIp])
                        if (device != null) {
                            foundDevices[targetIp] = device
                        }
                    }
                }
                deferreds.awaitAll()
            }

            scannedCount += chunk.size
            val progress = scannedCount.toFloat() / totalIps.toFloat()
            val currentList = foundDevices.values.sortedWith(
                compareByDescending<LanDevice> { it.isSelf }
                    .thenByDescending { it.isGateway }
                    .thenBy { ipToLong(it.ipAddress) }
            )
            onProgress(progress, "$subnetPrefix.${chunk.lastOrNull() ?: 1}", currentList)
        }

        // Merge mDNS discovered services into device results
        for ((ip, services) in discoveredNsdServices) {
            val existing = foundDevices[ip]
            if (existing != null) {
                val updatedType = if (existing.deviceType == DeviceType.UNKNOWN) {
                    NetworkUtils.inferDeviceType(ip, existing.hostName, services, existing.openPorts, existing.isGateway)
                } else existing.deviceType

                val updatedVendor = existing.vendor ?: NetworkUtils.inferVendor(existing.hostName, services)

                foundDevices[ip] = existing.copy(
                    discoveredServices = services,
                    deviceType = updatedType,
                    vendor = updatedVendor
                )
            } else {
                val inferredType = NetworkUtils.inferDeviceType(ip, null, services, emptyList(), false)
                foundDevices[ip] = LanDevice(
                    id = ip,
                    ipAddress = ip,
                    macAddress = arpTable[ip] ?: "Privacy Protected",
                    hostName = services.firstOrNull()?.serviceName ?: "mDNS Device",
                    deviceType = inferredType,
                    discoveredServices = services,
                    responseTimeMs = 0L,
                    isReachable = true,
                    isGateway = false,
                    isSelf = false,
                    vendor = NetworkUtils.inferVendor(null, services)
                )
            }
        }

        foundDevices.values.sortedWith(
            compareByDescending<LanDevice> { it.isSelf }
                .thenByDescending { it.isGateway }
                .thenBy { ipToLong(it.ipAddress) }
        )
    }

    private fun probeIp(ip: String, mac: String?): LanDevice? {
        val startTime = System.currentTimeMillis()
        var reachable = false
        val openPorts = mutableListOf<Int>()

        try {
            val addr = InetAddress.getByName(ip)
            // Quick ICMP / ping check
            if (addr.isReachable(180)) {
                reachable = true
            }
        } catch (e: Exception) {
            // Proceed to port check
        }

        // Port probe if ICMP didn't reply or to detect service ports
        for (port in probePorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 120)
                    reachable = true
                    openPorts.add(port)
                }
            } catch (e: Exception) {
                // Port closed / unreachable
            }
        }

        if (!reachable && mac == null) {
            return null
        }

        val rtt = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        val hostname = resolveHost(ip)
        val services = discoveredNsdServices[ip] ?: emptyList()
        val deviceType = NetworkUtils.inferDeviceType(ip, hostname, services, openPorts, false)
        val vendor = NetworkUtils.inferVendor(hostname, services)

        return LanDevice(
            id = ip,
            ipAddress = ip,
            macAddress = mac ?: "Privacy Protected (Android 10+)",
            hostName = hostname,
            deviceType = deviceType,
            discoveredServices = services,
            openPorts = openPorts,
            responseTimeMs = if (reachable) rtt else 0L,
            isReachable = reachable,
            isGateway = false,
            isSelf = false,
            vendor = vendor
        )
    }

    private fun resolveHost(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
            val host = addr.canonicalHostName
            if (host != ip && !host.isNullOrBlank()) host else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readArpTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split("\\s+".toRegex()) ?: continue
                    if (tokens.size >= 4 && tokens[0] != "IP") {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00") {
                            map[ip] = mac.uppercase()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Restricted on newer Android versions for user privacy
        }
        return map
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        return (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
    }

}
