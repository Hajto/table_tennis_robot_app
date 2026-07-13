package com.tablebot.ble

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class BlePermissionsTest {

    @Test
    fun `Android 12+ requests only the two bluetooth runtime permissions`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            BlePermissions.required(sdkInt = 31),
        )
    }

    @Test
    fun `pre-12 requests legacy bluetooth plus fine location`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            BlePermissions.required(sdkInt = 30),
        )
    }

    @Test
    fun `location services gate applies only below Android 12`() {
        assertTrue(BlePermissions.locationRequiredForScan(sdkInt = 26))
        assertTrue(BlePermissions.locationRequiredForScan(sdkInt = 30))
        assertFalse(BlePermissions.locationRequiredForScan(sdkInt = 31))
        assertFalse(BlePermissions.locationRequiredForScan(sdkInt = 34))
    }
}
