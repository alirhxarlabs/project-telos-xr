package com.alixarlabs.telosxr.network

import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Manages network interface detection for WiFi and USB ethernet connections.
 */
object NetworkInterfaceManager {
    private const val TAG = "NetworkInterfaceManager"

    /**
     * Information about a detected network interface.
     */
    data class InterfaceInfo(
        val name: String,
        val displayName: String,
        val ipAddress: InetAddress,
        val isUSB: Boolean
    )

    /**
     * USB ethernet interface name patterns.
     * Common USB ethernet adapter interface names on Android:
     * - usb0, usb1, etc. - Standard USB ethernet
     * - rndis0, rndis1, etc. - RNDIS (Remote NDIS) USB ethernet
     * - eth0, eth1, etc. - Generic ethernet (may be USB)
     */
    private val USB_INTERFACE_PATTERNS = listOf(
        "usb",
        "rndis",
        "eth"
    )

    /**
     * WiFi interface name patterns.
     * Common WiFi interface names on Android:
     * - wlan0, wlan1, etc. - Standard WiFi
     */
    private val WIFI_INTERFACE_PATTERNS = listOf(
        "wlan"
    )

    /**
     * Detects a USB ethernet interface.
     *
     * @return InterfaceInfo if found, null otherwise
     */
    fun detectUSBInterface(): InterfaceInfo? {
        return detectInterface(isUSB = true)
    }

    /**
     * Detects a WiFi interface.
     *
     * @return InterfaceInfo if found, null otherwise
     */
    fun detectWiFiInterface(): InterfaceInfo? {
        return detectInterface(isUSB = false)
    }

    /**
     * Detects a network interface based on type.
     *
     * @param isUSB true to detect USB ethernet, false to detect WiFi
     * @return InterfaceInfo if found, null otherwise
     */
    private fun detectInterface(isUSB: Boolean): InterfaceInfo? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val patterns = if (isUSB) USB_INTERFACE_PATTERNS else WIFI_INTERFACE_PATTERNS
            val typeStr = if (isUSB) "USB" else "WiFi"

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val name = networkInterface.name

                // Check if interface name matches pattern
                val matchesPattern = patterns.any { pattern ->
                    name.startsWith(pattern, ignoreCase = true)
                }

                if (!matchesPattern) {
                    continue
                }

                // Check if interface is up
                if (!networkInterface.isUp) {
                    Log.d(TAG, "$typeStr interface $name is not up, skipping")
                    continue
                }

                // Get IPv4 address
                val ipAddress = getIPv4Address(networkInterface)
                if (ipAddress == null) {
                    Log.d(TAG, "$typeStr interface $name has no IPv4 address, skipping")
                    continue
                }

                // Found valid interface
                val info = InterfaceInfo(
                    name = name,
                    displayName = networkInterface.displayName,
                    ipAddress = ipAddress,
                    isUSB = isUSB
                )

                Log.i(TAG, "Detected $typeStr interface: $name (${info.displayName}) - IP: ${ipAddress.hostAddress}")
                return info
            }

            Log.w(TAG, "No $typeStr interface found")
            return null

        } catch (e: SocketException) {
            Log.e(TAG, "Failed to enumerate network interfaces", e)
            return null
        }
    }

    /**
     * Gets the first IPv4 address from a network interface.
     *
     * @param networkInterface the network interface
     * @return IPv4 address if found, null otherwise
     */
    private fun getIPv4Address(networkInterface: NetworkInterface): InetAddress? {
        val addresses = networkInterface.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            // Skip loopback addresses and non-IPv4 addresses
            if (!address.isLoopbackAddress && address is Inet4Address) {
                return address
            }
        }
        return null
    }

    /**
     * Lists all available network interfaces for debugging.
     */
    fun listAllInterfaces(): List<InterfaceInfo> {
        val result = mutableListOf<InterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val ipAddress = getIPv4Address(networkInterface)
                if (ipAddress != null && networkInterface.isUp) {
                    val isUSB = USB_INTERFACE_PATTERNS.any { pattern ->
                        networkInterface.name.startsWith(pattern, ignoreCase = true)
                    }
                    result.add(
                        InterfaceInfo(
                            name = networkInterface.name,
                            displayName = networkInterface.displayName,
                            ipAddress = ipAddress,
                            isUSB = isUSB
                        )
                    )
                    Log.d(TAG, "Interface: ${networkInterface.name} (${networkInterface.displayName}) - " +
                            "IP: ${ipAddress.hostAddress}, Up: ${networkInterface.isUp}, USB: $isUSB")
                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Failed to list network interfaces", e)
        }
        return result
    }
}
