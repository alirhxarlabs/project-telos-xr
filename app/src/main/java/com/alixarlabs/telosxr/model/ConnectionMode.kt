package com.alixarlabs.telosxr.model

/**
 * Connection mode for receiving FEC video stream.
 */
enum class ConnectionMode {
    /**
     * WiFi connection mode - receives stream over wireless network.
     * Default mode, uses standard WiFi interface.
     */
    WIFI,

    /**
     * USB tethered connection mode - receives stream over USB-C ethernet adapter.
     * Requires USB-C to Ethernet adapter connected to Android device.
     * Provides lower latency and more reliable connection than WiFi.
     */
    USB_TETHERED
}
