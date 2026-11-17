package com.fxxkmoondrop.secret

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

/**
 * Compose host for the Space Travel 2 overview.
 *
 * Bluetooth discovery, GAIA transport, protocol state and device controls remain in
 * GaiaBleClient / DeviceControlBridge / AncBridge. This Fragment only adapts their
 * existing callbacks into immutable Compose UI state.
 */
class OverviewFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val screenState = mutableStateOf(MoonEarOverviewState())
    private lateinit var palette: ThemeUtil.Palette

    private var connectionInProgress = false
    private var pairingRequired = false
    private var connectionError: String? = null
    private var connectionStage: String? = null
    private var connectionTimeout: Runnable? = null

    private val callback = object : GaiaBleClient.Callback {
        override fun onConnected(address: String) {
            handler.post {
                connectionInProgress = false
                connectionError = null
                connectionStage = null
                connectionTimeout?.let { handler.removeCallbacks(it) }
                context?.let { DeviceControlBridge.refreshProfile(it) }
                GaiaBleClient.getInstance().setDcBridge(DeviceControlBridge)
                GaiaBleClient.getInstance().fetchBatteryLevels()
                AncBridge.fetchAncMode()
                DeviceControlBridge.fetchGain()
                refreshUi()
            }
        }

        override fun onDisconnected(address: String) {
            handler.post {
                connectionInProgress = false
                connectionStage = null
                refreshUi()
            }
        }

        override fun onBatteryLevel(batteryId: Int, level: Int) {
            handler.post { refreshUi() }
        }

        override fun onAncMode(mode: Int) {
            handler.post { refreshUi() }
        }

        override fun onError(message: String) {
            handler.post {
                connectionInProgress = false
                connectionStage = null
                connectionError = when {
                    message.contains("timeout", ignoreCase = true) ->
                        "Connection timed out. Open the case and keep both earbuds awake."
                    message.contains("service discovery", ignoreCase = true) ||
                        message.contains("GAIA", ignoreCase = true) ->
                        "Earbuds found, but their control service did not respond. Keep both buds awake and retry."
                    else -> "Connection failed: $message"
                }
                android.util.Log.e("MoonEar", "GAIA connection error: $message")
                connectionTimeout?.let { handler.removeCallbacks(it) }
                if (isAdded) {
                    Toast.makeText(requireContext(), connectionError, Toast.LENGTH_LONG).show()
                    refreshUi()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        Lang.refresh(requireContext())
        palette = ThemeUtil.Palette(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoonEarOverviewScreen(
                    state = screenState.value,
                    palette = palette,
                    onConnect = { connectToHeadset() },
                    onBluetoothSettings = { openBluetoothSettings() },
                    onDisconnect = { disconnectFromHeadset() },
                    onAncSelected = { mode ->
                        if (GaiaBleClient.getInstance().isGaiaReady()) {
                            AncBridge.setAncMode(mode)
                            refreshUi()
                        }
                    },
                    onGainSelected = { level ->
                        if (GaiaBleClient.getInstance().isGaiaReady()) {
                            DeviceControlBridge.setGain(level)
                            refreshUi()
                        }
                    },
                    onOpenEqualizer = {
                        if (GaiaBleClient.getInstance().hasSrcService()) {
                            startActivity(Intent(requireContext(), EqEditorActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val gaia = GaiaBleClient.getInstance()
        gaia.setCallback(callback)
        gaia.setDcBridge(DeviceControlBridge)
        DeviceControlBridge.setStateListener { handler.post { refreshUi() } }
        DeviceControlBridge.refreshProfile(requireContext())
        refreshUi()
        connectToHeadset()
        handler.postDelayed({ refreshUi() }, 500)
    }

    override fun onPause() {
        DeviceControlBridge.setStateListener(null)
        GaiaBleClient.instance?.releaseCallback(callback)
        super.onPause()
    }

    override fun onDestroyView() {
        connectionTimeout?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun connectToHeadset() {
        val ctx = context ?: return
        if (!ensureBluetoothPermissions()) return

        val gaia = GaiaBleClient.getInstance()
        if (gaia.isGaiaReady()) {
            connectionInProgress = false
            connectionError = null
            refreshUi()
            gaia.fetchBatteryLevels()
            AncBridge.fetchAncMode()
            DeviceControlBridge.fetchGain()
            return
        }
        if (connectionInProgress) return

        if (gaia.isConnecting()) {
            // The background monitor may own the active GATT attempt. Adopt it instead
            // of starting a competing connection or leaving the screen stale.
            connectionInProgress = true
            pairingRequired = false
            connectionError = null
            connectionStage = "Connecting to the earbuds…"
            scheduleConnectionTimeout(gaia)
            refreshUi()
            return
        }

        connectionInProgress = true
        pairingRequired = false
        connectionError = null
        connectionStage = "Finding Space Travel 2…"
        refreshUi()

        Thread {
            var address: String? = null
            try {
                address = HeadsetGate.getConnectedMac(ctx)
                if (address == null) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    val bonded = adapter?.bondedDevices?.firstOrNull { device ->
                        DeviceMatcher.isMoondrop(runCatching { device.name }.getOrNull())
                    }
                    if (bonded != null) address = bonded.address
                }
                if (address == null) {
                    address = ctx.getSharedPreferences("cfg", 0).getString("gaia_le_addr", null)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("MoonEar", "Bluetooth permission denied while finding Space Travel 2", e)
            } catch (e: Exception) {
                android.util.Log.w("MoonEar", "Bluetooth device lookup failed", e)
            }

            val target = address
            val started = target != null && gaia.connect(ctx.applicationContext, target)
            handler.post {
                if (!isAdded) return@post
                if (!started) {
                    connectionInProgress = false
                    pairingRequired = target == null
                    connectionStage = null
                    connectionError = if (target == null) {
                        "Space Travel 2 was not found. Pair it in Android Bluetooth settings, then retry."
                    } else {
                        "Android could not start the earbud connection. Check Bluetooth access and try again."
                    }
                    refreshUi()
                    if (target == null) {
                        Toast.makeText(
                            requireContext(),
                            "Pair Space Travel 2 in Android Bluetooth settings first.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    connectionStage = "Connecting to Space Travel 2…"
                    scheduleConnectionTimeout(gaia)
                    refreshUi()
                }
            }
        }.apply {
            name = "MoonEar-DeviceLookup"
            isDaemon = true
            start()
        }
    }

    private fun scheduleConnectionTimeout(gaia: GaiaBleClient) {
        connectionTimeout?.let { handler.removeCallbacks(it) }
        connectionTimeout = Runnable {
            if (isAdded && connectionInProgress && !gaia.isGaiaReady()) {
                connectionInProgress = false
                connectionStage = null
                connectionError = "Connection timed out. Open the case and try again."
                refreshUi()
            }
        }
        handler.postDelayed(connectionTimeout!!, 40_000L)
    }

    private fun ensureBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val missing = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).filter {
            requireContext().checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        requestPermissions(missing.toTypedArray(), REQUEST_BLUETOOTH)
        connectionError = "Allow Bluetooth access to connect."
        refreshUi()
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLUETOOTH) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            connectionError = null
            connectToHeadset()
        } else {
            connectionError = "Bluetooth access is required to connect."
            refreshUi()
        }
    }

    private fun disconnectFromHeadset() {
        connectionInProgress = false
        connectionStage = null
        connectionError = null
        connectionTimeout?.let { handler.removeCallbacks(it) }
        GaiaBleClient.getInstance().shutdown()
        refreshUi()
    }

    private fun refreshUi() {
        if (!isAdded) return
        val gaia = GaiaBleClient.getInstance()
        val name = gaia.getConnectedDeviceName().orEmpty()
        val linkReady = gaia.isGaiaReady()
        val supportedDevice = name.isBlank() || DeviceMatcher.isMoondrop(name)
        val connected = linkReady && supportedDevice
        val status = when {
            connected -> "Connected"
            connectionInProgress -> connectionStage ?: "Connecting to Space Travel 2…"
            connectionError != null -> connectionError!!
            else -> "Not connected"
        }
        val address = gaia.deviceAddress

        screenState.value = MoonEarOverviewState(
            deviceName = if (name.isNotBlank() && supportedDevice) name else "Space Travel 2",
            status = status,
            connected = connected,
            connecting = connectionInProgress,
            pairingRequired = pairingRequired,
            connectionError = connectionError,
            leftBattery = if (connected) BatteryStore.getGaiaLeft(address) else -1,
            rightBattery = if (connected) BatteryStore.getGaiaRight(address) else -1,
            ancMode = if (connected) AncBridge.getCurrentMode() else -1,
            gainLevel = if (connected) DeviceControlBridge.getGainLevel() else -1,
            equalizerAvailable = connected && gaia.hasSrcService()
        )
    }

    private fun openBluetoothSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    companion object {
        private const val REQUEST_BLUETOOTH = 0x4D42
    }
}
