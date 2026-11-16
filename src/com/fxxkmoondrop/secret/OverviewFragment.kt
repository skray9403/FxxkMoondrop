package com.fxxkmoondrop.secret

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView

/** Space Travel 2 overview, with a product-first layout and direct paired-device fallback. */
class OverviewFragment : Fragment() {
    private lateinit var pal: ThemeUtil.Palette
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var deviceName: TextView
    private lateinit var leftBattery: BatteryRingView
    private lateinit var rightBattery: BatteryRingView
    private lateinit var ancGrid: LinearLayout
    private lateinit var gainGroup: MaterialButtonToggleGroup
    private lateinit var eqCard: MaterialCardView
    private lateinit var connectButton: MaterialButton
    private lateinit var connectionCard: MaterialCardView
    private lateinit var batterySection: LinearLayout
    private lateinit var noiseSection: LinearLayout
    private lateinit var gainSection: LinearLayout
    private lateinit var equalizerSection: LinearLayout
    private lateinit var caseBattery: BatteryRingView
    private lateinit var actionRow: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var rebuilding = false
    private var connectionInProgress = false
    private var pairingRequired = false
    private var connectionError: String? = null
    private var connectionStage: String? = null
    private val ancTiles = mutableListOf<Pair<MaterialCardView, Int>>()
    private val ancTileLabels = mutableMapOf<MaterialCardView, Pair<TextView, TextView>>()
    private var connectionTimeout: Runnable? = null

    // UI labels map to the hardware-verified Space Travel 2 values.
    private val ancModes = listOf(
        Triple(1, "Noise cancelling", "◉"),
        Triple(0, "Off", "○"),
        Triple(3, "Wind reduction", "≈"),
        Triple(2, "Transparency", "◎")
    )

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
            handler.post { refreshBattery() }
        }

        override fun onAncMode(mode: Int) {
            handler.post { refreshAncSelection() }
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
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Lang.refresh(requireContext())
        pal = ThemeUtil.Palette(requireContext())

        val scroller = ScrollView(requireContext()).apply {
            clipToPadding = false
            setBackgroundColor(pal.surface)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(dp(20), bars.top + dp(18), dp(20), bars.bottom + dp(132))
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(
                        dp(20),
                        insets.systemWindowInsetTop + dp(18),
                        dp(20),
                        insets.systemWindowInsetBottom + dp(132)
                    )
                }
                insets
            }
        }

        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.surface)
        }
        scroller.addView(root, ViewGroup.LayoutParams(-1, -2))

        buildHeader()
        buildProductCard()
        buildBatterySection()
        buildNoiseSection()
        buildGainSection()
        buildEqualizerSection()
        refreshUi()

        scroller.post { scroller.requestApplyInsets() }
        return scroller
    }

    private fun buildHeader() {
        deviceName = TextView(requireContext()).apply {
            text = "Space Travel 2"
            textSize = 30f
            letterSpacing = -0.025f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }
        root.addView(deviceName)

        val stateRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = TextView(requireContext()).apply {
            text = "●"
            textSize = 10f
            setTextColor(pal.outline)
        }
        status = TextView(requireContext()).apply {
            text = "Not connected"
            textSize = 14f
            setTextColor(pal.onVariant)
            setPadding(dp(7), 0, 0, 0)
        }
        stateRow.addView(dot)
        stateRow.addView(status)
        root.addView(stateRow, columnParams(bottom = 18))
    }

    private fun buildProductCard() {
        // Pixel Buds-style connection prompt: compact, calm, and only shown while disconnected.
        connectionCard = MaterialCardView(requireContext()).apply {
            radius = dp(24).toFloat()
            setCardBackgroundColor(pal.secondaryContainer)
            strokeWidth = 0
            cardElevation = 0f
            setContentPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(TextView(requireContext()).apply {
            text = "Connect your Space Travel 2"
            textSize = 17f
            setTextColor(pal.onSecondaryContainer)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        content.addView(TextView(requireContext()).apply {
            text = "Open the charging case and keep both earbuds nearby."
            textSize = 13f
            setTextColor(pal.onSecondaryContainer)
            setPadding(0, dp(4), 0, dp(12))
        })
        connectButton = MaterialButton(requireContext()).apply {
            text = "Connect"
            isAllCaps = false
            setOnClickListener {
                if (pairingRequired) openBluetoothSettings() else connectToHeadset()
            }
        }
        content.addView(connectButton, LinearLayout.LayoutParams(-1, dp(48)))
        connectionCard.addView(content)
        root.addView(connectionCard, columnParams(bottom = 16))
    }

        private fun buildBatterySection() {
        batterySection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        batterySection.addView(sectionHeading("Battery", "Left and right earbud charge"))
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(26).toFloat()
            setCardBackgroundColor(pal.card)
            strokeWidth = dp(1)
            strokeColor = pal.cardStroke
            cardElevation = 0f
            setContentPadding(dp(8), dp(14), dp(8), dp(10))
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        leftBattery = BatteryRingView(requireContext(), pal).apply {
            label = "Left"
            setLevel(-1)
        }
        caseBattery = BatteryRingView(requireContext(), pal).apply {
            label = "Case"
            setLevel(-1)
        }
        rightBattery = BatteryRingView(requireContext(), pal).apply {
            label = "Right"
            setLevel(-1)
        }
        row.addView(leftBattery, LinearLayout.LayoutParams(0, dp(122), 1f))
        row.addView(caseBattery, LinearLayout.LayoutParams(0, dp(122), 1f))
        row.addView(rightBattery, LinearLayout.LayoutParams(0, dp(122), 1f))
        card.addView(row)
        batterySection.addView(card)
        batterySection.addView(TextView(requireContext()).apply {
            text = "Case battery is not reported by the current earbud protocol."
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(dp(4), dp(8), dp(4), 0)
        })

        val actionsCard = MaterialCardView(requireContext()).apply {
            radius = dp(22).toFloat()
            setCardBackgroundColor(pal.secondaryContainer)
            strokeWidth = 0
            cardElevation = 0f
            setContentPadding(dp(4), dp(4), dp(4), dp(4))
        }
        actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val managePairing = MaterialButton(requireContext()).apply {
            text = "Bluetooth settings"
            isAllCaps = false
            setTextColor(pal.onSecondaryContainer)
            backgroundTintList = android.content.res.ColorStateList.valueOf(pal.secondaryContainer)
            elevation = 0f
            setOnClickListener { openBluetoothSettings() }
        }
        val disconnect = MaterialButton(requireContext()).apply {
            text = "Disconnect"
            isAllCaps = false
            setTextColor(pal.onSecondaryContainer)
            backgroundTintList = android.content.res.ColorStateList.valueOf(pal.secondaryContainer)
            elevation = 0f
            setOnClickListener {
                connectionInProgress = false
                connectionStage = null
                connectionError = null
                connectionTimeout?.let { handler.removeCallbacks(it) }
                GaiaBleClient.getInstance().shutdown()
                refreshUi()
            }
        }
        actionRow.addView(managePairing, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow.addView(disconnect, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionsCard.addView(actionRow)
        batterySection.addView(actionsCard, columnParams(top = 12, bottom = 8))
        root.addView(batterySection, columnParams(bottom = 12))
    }

        private fun buildNoiseSection() {
        noiseSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        noiseSection.addView(sectionHeading("Active noise control", "Choose how much of the outside world you hear"))
        ancGrid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        ancModes.forEachIndexed { index, item ->
            ancGrid.addView(createAncTile(item.first, item.second, item.third),
                LinearLayout.LayoutParams(0, dp(86), 1f).also {
                    if (index != 0) it.marginStart = dp(3)
                    if (index != ancModes.lastIndex) it.marginEnd = dp(3)
                })
        }
        noiseSection.addView(ancGrid)
        root.addView(noiseSection, columnParams(top = 8, bottom = 16))
    }

    private fun sectionHeading(title: String, subtitle: String): LinearLayout {
        val block = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        block.addView(TextView(requireContext()).apply {
            text = title
            textSize = 18f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        block.addView(TextView(requireContext()).apply {
            text = subtitle
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(3), 0, dp(10))
        })
        return block
    }

        private fun createAncTile(mode: Int, label: String, symbol: String): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(pal.card)
            strokeWidth = dp(1)
            strokeColor = pal.cardStroke
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            tag = mode
            setContentPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val glyph = TextView(requireContext()).apply {
            text = symbol
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(pal.onVariant)
        }
        val title = TextView(requireContext()).apply {
            text = label
            textSize = 10.5f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(4), 0, 0)
        }
        content.addView(glyph, LinearLayout.LayoutParams(-1, dp(28)))
        content.addView(title, LinearLayout.LayoutParams(-1, -2))
        card.addView(content)
        card.setOnClickListener {
            if (!GaiaBleClient.getInstance().isGaiaReady()) return@setOnClickListener
            AncBridge.setAncMode(mode)
            refreshAncSelection()
        }
        ancTileLabels[card] = glyph to title
        ancTiles.add(card to mode)
        return card
    }

        private fun buildGainSection() {
        gainSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        gainSection.addView(sectionHeading("Sound gain", "Set the output level"))
        gainGroup = MaterialButtonToggleGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        listOf("Low", "Medium", "High").forEachIndexed { index, label ->
            val button = MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                tag = index
                text = label
                textSize = 13f
                isAllCaps = false
                isCheckable = true
                minimumHeight = dp(54)
                insetTop = 0
                insetBottom = 0
            }
            gainGroup.addView(button, LinearLayout.LayoutParams(0, dp(58), 1f).also {
                if (index != 0) it.marginStart = dp(3)
                if (index != 2) it.marginEnd = dp(3)
            })
        }
        gainGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!rebuilding && isChecked) {
                val level = group.findViewById<MaterialButton>(checkedId)?.tag as? Int
                    ?: return@addOnButtonCheckedListener
                if (GaiaBleClient.getInstance().isGaiaReady()) {
                    DeviceControlBridge.setGain(level)
                } else {
                    refreshGainSelection()
                }
            }
        }
        gainSection.addView(gainGroup)
        root.addView(gainSection, columnParams(top = 4, bottom = 16))
    }

        private fun buildEqualizerSection() {
        equalizerSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        equalizerSection.addView(sectionHeading("More controls", "Fine-tune your listening experience"))
        eqCard = MaterialCardView(requireContext()).apply {
            radius = dp(22).toFloat()
            setCardBackgroundColor(pal.card)
            strokeWidth = dp(1)
            strokeColor = pal.cardStroke
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isEnabled) startActivity(Intent(requireContext(), EqEditorActivity::class.java))
            }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(16), dp(14))
        }
        val icon = TextView(requireContext()).apply {
            text = "≋"
            textSize = 28f
            setTextColor(pal.primary)
            gravity = Gravity.CENTER
        }
        val copy = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        copy.addView(TextView(requireContext()).apply {
            text = "Parametric equalizer"
            textSize = 15f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        copy.addView(TextView(requireContext()).apply {
            text = "9-band tuning"
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(3), 0, 0)
            tag = "eq_summary"
        })
        row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(38)))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(requireContext()).apply {
            text = "›"
            textSize = 28f
            setTextColor(pal.onVariant)
        })
        eqCard.addView(row)
        equalizerSection.addView(eqCard)
        root.addView(equalizerSection, columnParams(top = 2, bottom = 16))
    }

        private fun addSection(title: String, subtitle: String) {
        val block = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        block.addView(TextView(requireContext()).apply {
            text = title
            textSize = 18f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        block.addView(TextView(requireContext()).apply {
            text = subtitle
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(3), 0, dp(10))
        })
        root.addView(block, columnParams(top = 4, bottom = 4))
    }

    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        val gaia = GaiaBleClient.getInstance()
        gaia.setCallback(callback)
        gaia.setDcBridge(DeviceControlBridge)
        DeviceControlBridge.setStateListener { handler.post { refreshGainSelection() } }
        DeviceControlBridge.refreshProfile(requireContext())
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
            // The monitor service may have started GATT before this screen resumed.
            // Adopt that in-flight session instead of leaving the UI stuck at "Not connected".
            connectionInProgress = true
            pairingRequired = false
            connectionError = null
            connectionStage = "Connecting to the earbuds…"
            connectButton.isEnabled = false
            connectButton.text = "Connecting…"
            connectionTimeout?.let { handler.removeCallbacks(it) }
            connectionTimeout = Runnable {
                if (isAdded && connectionInProgress && !gaia.isGaiaReady()) {
                    connectionInProgress = false
                    connectionStage = null
                    connectionError = "The earbuds did not finish connecting. Open the case and try again."
                    refreshUi()
                }
            }
            handler.postDelayed(connectionTimeout!!, 40_000L)
            refreshUi()
            return
        }

        connectionInProgress = true
        pairingRequired = false
        connectionError = null
        connectionStage = "Finding Space Travel 2…"
        connectButton.isEnabled = false
        connectButton.text = "Connecting…"
        refreshUi()

        Thread {
            var address: String? = null
            var pairedDeviceFound = false
            try {
                // Prefer the active audio device, but do not make that the only discovery path.
                address = HeadsetGate.getConnectedMac(ctx)
                if (address == null) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    val bonded = adapter?.bondedDevices?.firstOrNull { device ->
                        DeviceMatcher.isMoondrop(runCatching { device.name }.getOrNull())
                    }
                    if (bonded != null) {
                        address = bonded.address
                        pairedDeviceFound = true
                    }
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
                    connectButton.isEnabled = true
                    connectButton.text = if (pairingRequired) "Pair device" else "Try again"
                    refreshUi()
                    if (target == null) {
                        Toast.makeText(requireContext(),
                            "Pair Space Travel 2 in Android Bluetooth settings first.",
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    connectionStage = "Connecting to Space Travel 2…"
                    connectionTimeout?.let { handler.removeCallbacks(it) }
                    connectionTimeout = Runnable {
                        if (isAdded && connectionInProgress && !gaia.isGaiaReady()) {
                            connectionInProgress = false
                            connectionStage = null
                            connectionError = "Connection timed out. Open the case and try again."
                            connectButton.isEnabled = true
                            connectButton.text = "Try again"
                            refreshUi()
                        }
                    }
                    handler.postDelayed(connectionTimeout!!, 40_000L)
                    connectButton.isEnabled = false
                    connectButton.text = "Connecting…"
                    status.text = "Connecting to Space Travel 2…"
                }
            }
        }.apply {
            name = "MoonEar-DeviceLookup"
            isDaemon = true
            start()
        }
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
        status.text = "Allow Bluetooth access to connect."
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
            connectToHeadset()
        } else {
            connectionError = "Bluetooth access is required to connect."
            refreshUi()
        }
    }

    private fun refreshUi() {
        if (!isAdded || !::status.isInitialized) return
        val gaia = GaiaBleClient.getInstance()
        val name = gaia.getConnectedDeviceName().orEmpty()
        val connected = gaia.isGaiaReady()
        val isSpaceTravel2 = name.isBlank() || DeviceMatcher.isMoondrop(name)

        deviceName.text = if (name.isNotBlank() && isSpaceTravel2) name else "Space Travel 2"
        status.text = when {
            connected && isSpaceTravel2 -> "Connected"
            connectionInProgress -> connectionStage ?: "Connecting to Space Travel 2…"
            connectionError != null -> connectionError
            else -> "Not connected"
        }
        status.setTextColor(
            when {
                connected && isSpaceTravel2 -> pal.green
                connectionError != null -> pal.red
                else -> pal.onVariant
            }
        )

        connectButton.visibility = if (connected && isSpaceTravel2) View.GONE else View.VISIBLE
        connectButton.isEnabled = !connectionInProgress
        if (!connectionInProgress && connectButton.visibility == View.VISIBLE) {
            connectButton.text = if (pairingRequired) "Pair device" else if (connectionError != null) "Try again" else "Connect"
        }
        val ready = connected && isSpaceTravel2
        connectionCard.visibility = if (ready) View.GONE else View.VISIBLE
        actionRow.visibility = if (ready) View.VISIBLE else View.GONE
        noiseSection.visibility = if (ready) View.VISIBLE else View.GONE
        gainSection.visibility = if (ready) View.VISIBLE else View.GONE
        equalizerSection.visibility = if (ready) View.VISIBLE else View.GONE

        val controlsEnabled = ready
        ancTiles.forEach { (card, _) ->
            card.isEnabled = controlsEnabled
            card.alpha = if (controlsEnabled) 1f else 0.55f
        }
        gainGroup.isEnabled = controlsEnabled
        for (i in 0 until gainGroup.childCount) gainGroup.getChildAt(i).isEnabled = controlsEnabled

        eqCard.isEnabled = controlsEnabled && gaia.hasSrcService()
        eqCard.alpha = if (eqCard.isEnabled) 1f else 0.58f
        val eqSummary = (eqCard.getChildAt(0) as? LinearLayout)
            ?.getChildAt(1) as? LinearLayout
        (eqSummary?.getChildAt(1) as? TextView)?.text =
            if (eqCard.isEnabled) "9ECA service detected" else "Available after 9ECA connection"

        refreshBattery()
        refreshAncSelection()
        refreshGainSelection()
    }

    private fun refreshBattery() {
        val gaia = GaiaBleClient.getInstance()
        val address = gaia.deviceAddress
        val connected = gaia.isGaiaReady()
        leftBattery.setLevel(if (connected) BatteryStore.getGaiaLeft(address) else -1)
        rightBattery.setLevel(if (connected) BatteryStore.getGaiaRight(address) else -1)
        caseBattery.setLevel(-1)
    }

    private fun refreshAncSelection() {
        if (!::ancGrid.isInitialized) return
        val selected = AncBridge.getCurrentMode()
        ancTiles.forEach { (card, mode) ->
            val active = mode == selected
            card.setCardBackgroundColor(if (active) pal.secondaryContainer else pal.card)
            card.strokeColor = if (active) pal.primary else pal.cardStroke
            val labels = ancTileLabels[card]
            labels?.first?.setTextColor(if (active) pal.onSecondaryContainer else pal.onVariant)
            labels?.second?.setTextColor(if (active) pal.onSecondaryContainer else pal.onSurface)
        }
    }

    private fun refreshGainSelection() {
        if (!::gainGroup.isInitialized) return
        val level = DeviceControlBridge.getGainLevel()
        rebuilding = true
        val button = (0 until gainGroup.childCount)
            .map { gainGroup.getChildAt(it) as MaterialButton }
            .firstOrNull { (it.tag as? Int) == level }
        if (button != null) gainGroup.check(button.id) else gainGroup.clearChecked()
        rebuilding = false
    }

    private fun openBluetoothSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    private fun columnParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).also {
            it.topMargin = dp(top)
            it.bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private class ProductIllustrationView(
        context: android.content.Context,
        private val palette: ThemeUtil.Palette
    ) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * resources.displayMetrics.density
        }
        private val d = resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height * 0.55f
            val caseW = minOf(width * 0.48f, 188f * d)
            val caseH = 70f * d
            val left = cx - caseW / 2f
            val top = cy - caseH / 2f + 18f * d

            // Soft shadow beneath the case.
            fill.color = 0x18000000
            canvas.drawOval(RectF(left + 14f * d, top + caseH - 2f * d,
                left + caseW - 14f * d, top + caseH + 13f * d), fill)

            fill.shader = LinearGradient(
                left, top, left + caseW, top + caseH,
                intArrayOf(0xFFFDFDFD.toInt(), 0xFFE3E2E4.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(left, top, left + caseW, top + caseH),
                25f * d, 25f * d, fill)
            fill.shader = null
            stroke.color = 0x80736F70.toInt()
            canvas.drawRoundRect(RectF(left, top, left + caseW, top + caseH),
                25f * d, 25f * d, stroke)

            // Lid seam and the tiny charging indicator.
            stroke.color = 0x66736F70
            canvas.drawLine(left + 12f * d, top + 25f * d,
                left + caseW - 12f * d, top + 25f * d, stroke)
            fill.color = palette.primary
            canvas.drawCircle(cx, top + 47f * d, 2.4f * d, fill)

            drawEarbud(canvas, cx - 47f * d, cy - 37f * d, -13f)
            drawEarbud(canvas, cx + 47f * d, cy - 37f * d, 13f)
        }

        private fun drawEarbud(canvas: Canvas, x: Float, y: Float, angle: Float) {
            canvas.save()
            canvas.rotate(angle, x, y)
            fill.shader = LinearGradient(
                x - 15f * d, y - 18f * d, x + 18f * d, y + 33f * d,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFDAD9DC.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(x - 7f * d, y + 2f * d,
                x + 7f * d, y + 39f * d), 6f * d, 6f * d, fill)
            canvas.drawCircle(x, y, 15f * d, fill)
            fill.shader = null
            stroke.color = 0x806D696A.toInt()
            canvas.drawCircle(x, y, 15f * d, stroke)
            fill.color = 0xFF494649.toInt()
            canvas.drawCircle(x, y, 5f * d, fill)
            canvas.restore()
        }
    }

    private class BatteryRingView(
        context: android.content.Context,
        private val palette: ThemeUtil.Palette
    ) : View(context) {
        var label: String = ""
            set(value) { field = value; invalidate() }

        private var level = -1
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.25f * resources.displayMetrics.density
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", 0)
        }

        fun setLevel(value: Int) {
            level = value.takeIf { it in 0..100 } ?: -1
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = 30f * d
            val radius = 26f * d

            // Soft circular product thumbnail, inspired by the Pixel Buds device-detail row.
            fill.color = palette.secondaryContainer
            canvas.drawCircle(cx, cy, radius, fill)
            outline.color = palette.cardStroke
            canvas.drawCircle(cx, cy, radius, outline)

            fill.color = palette.onSecondaryContainer
            if (label.equals("Case", ignoreCase = true)) {
                val body = RectF(cx - 17f * d, cy - 8f * d, cx + 17f * d, cy + 13f * d)
                canvas.drawRoundRect(body, 6f * d, 6f * d, fill)
                val lid = RectF(cx - 17f * d, cy - 12f * d, cx + 17f * d, cy - 4f * d)
                canvas.drawRoundRect(lid, 5f * d, 5f * d, fill)
                fill.color = palette.primary
                canvas.drawCircle(cx, cy + 5f * d, 1.8f * d, fill)
            } else {
                canvas.save()
                val tilt = if (label.equals("Left", true)) -10f else 10f
                canvas.rotate(tilt, cx, cy)
                canvas.drawRoundRect(
                    RectF(cx - 5f * d, cy - 2f * d, cx + 5f * d, cy + 20f * d),
                    4.5f * d, 4.5f * d, fill
                )
                canvas.drawCircle(cx, cy - 4f * d, 10f * d, fill)
                canvas.restore()
            }

            text.color = palette.onSurface
            text.textSize = 14f * d
            canvas.drawText(if (level >= 0) "$level%" else "—", cx, 76f * d, text)
            small.color = palette.onVariant
            small.textSize = 12f * d
            canvas.drawText(label, cx, 98f * d, small)
        }
    }

        companion object {
        private const val REQUEST_BLUETOOTH = 0x4D42
    }
}
