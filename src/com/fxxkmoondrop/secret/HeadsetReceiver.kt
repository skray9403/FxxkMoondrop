package com.fxxkmoondrop.secret

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

@Suppress("DEPRECATION")
class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Bluetooth event: $action")
        AncBridge.bind(context)

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val name = device?.name ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
        val address = device?.address ?: intent.getStringExtra(BluetoothDevice.EXTRA_ADDRESS)

        if (action == BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED) {
            val level = intent.getIntExtra(BluetoothDevice.EXTRA_BATTERY_LEVEL, -1)
            if (address != null && level in 0..100) {
                BatteryStore.set(address, level)
                DeviceNotif.refreshBattery(context)
                Log.d(TAG, "system battery update for $name: $level%")
            }
            return
        }

        val connected: Boolean
        val disconnected: Boolean
        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                connected = true
                disconnected = false
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                connected = false
                disconnected = true
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                connected = state == BluetoothProfile.STATE_CONNECTED
                disconnected = state == BluetoothProfile.STATE_DISCONNECTED
            }
            else -> return
        }

        Log.i(TAG, "device=$name address=$address connected=$connected disconnected=$disconnected")
        val isMoondrop = DeviceMatcher.isMoondrop(name)
        if (connected && isMoondrop) {
            wakeDetectService(context)
        } else if (disconnected) {
            val currentAddress = GaiaBleClient.getInstance().deviceAddress
            val isOurDevice = isMoondrop ||
                (!address.isNullOrBlank() && currentAddress.equals(address, ignoreCase = true))
            if (isOurDevice) HeadsetGate.clearConnectedMac(context)
        }
    }

    /** Rate-limit repeated ACL/A2DP/HFP broadcasts for the same connection event. */
    @Volatile private var lastWakeAt = 0L

    private fun wakeDetectService(context: Context) {
        if (!KeepAlive.enabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < 1000L) return
        lastWakeAt = now
        try {
            KeepAlive.wake(context)
            context.startService(
                Intent(context, HeadsetDetectService::class.java)
                    .setAction(HeadsetDetectService.ACTION_CONNECTION)
            )
            Log.d(TAG, "woke Bluetooth detection service")
        } catch (error: Throwable) {
            Log.w(TAG, "could not wake Bluetooth detection service", error)
        }
    }

    companion object {
        private const val TAG = "MoondropHeadset"
    }
}
