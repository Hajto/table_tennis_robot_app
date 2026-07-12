package com.tablebot.ble

import android.Manifest
import android.os.Build

/**
 * Single source of truth for BLE runtime permissions.
 *
 * Android 12+ (S): BLUETOOTH_SCAN + BLUETOOTH_CONNECT. No location — the
 * manifest declares BLUETOOTH_SCAN with neverForLocation, which also lifts
 * the "Location Services must be on" gate on scan results.
 *
 * Android 8–11: the OS treats BLE scan results as location data. Scanning
 * needs ACCESS_FINE_LOCATION granted AND the system Location toggle on;
 * with the toggle off, startScan() silently delivers zero results.
 */
object BlePermissions {

    fun required(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    fun locationRequiredForScan(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.S
}
