package com.fxxkmoondrop.secret

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView

/** Space Travel 2 only: verified ANC mapping, three gain levels and L/R battery. */
class OverviewFragment : Fragment() {
    private lateinit var pal: ThemeUtil.Palette
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var deviceName: TextView
    private lateinit var leftBattery: BatteryRingView
    private lateinit var caseBattery: BatteryRingView
    private lateinit var rightBattery: BatteryRingView
    private lateinit var ancGroup: MaterialButtonToggleGroup
    private lateinit var gainGroup: MaterialButtonToggleGroup
    private lateinit var eqButton: MaterialButton
    private lateinit var connectButton: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var rebuilding = false

    // UI 0=Off, 1=ANC, 2=Transparency, 3=Wind. Space Travel 2 profile mapping is tested.
    private val ancModes = listOf(1, 0, 3, 2)

    private val callback = object : GaiaBleClient.Callback {
        override fun onConnected(address: String) {
            handler.post {
                val ctx = context ?: return@post
                DeviceControlBridge.refreshProfile(ctx)
                GaiaBleClient.getInstance().fetchBatteryLevels()
                AncBridge.fetchAncMode()
                DeviceControlBridge.fetchGain()
                refreshUi()
            }
        }
        override fun onDisconnected(address: String) { handler.post { refreshUi() } }
        override fun onBatteryLevel(batteryId: Int, level: Int) { handler.post { refreshBattery() } }
        override fun onAncMode(mode: Int) { handler.post { refreshAncSelection() } }
        override fun onError(message: String) {
            handler.post {
                if (isAdded && ::status.isInitialized) {
                    status.text = message
                    status.setTextColor(pal.error)
                }
            }
        }
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        Lang.refresh(requireContext())
        pal = ThemeUtil.Palette(requireContext())

        val scroller = ScrollView(requireContext()).apply {
            clipToPadding = false
            setBackgroundColor(pal.surface)
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.surface)
        }
        scroller.addView(root, ScrollView.LayoutParams(-1, -2))

        deviceName = TextView(requireContext()).apply {
            text = "Space Travel 2"
            textSize = 30f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }
        root.addView(deviceName)
        status = TextView(requireContext()).apply {
            text = Lang.t("正在查找耳机…", "Looking for earbuds…")
            textSize = 14f
            setTextColor(pal.onVariant)
        }
        root.addView(status, columnParams(top = 4, bottom = 18))

        addSection(Lang.t("电量", "Battery"))
        val batteryCard = MaterialCardView(requireContext()).apply {
            radius = dp(28).toFloat()
            cardBackgroundColor = pal.card
            strokeWidth = 0
            cardElevation = 0f
            setContentPadding(dp(8), dp(16), dp(8), dp(16))
        }
        val batteryRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        leftBattery = BatteryRingView(requireContext(), pal).apply {
            label = Lang.t("左耳", "Left")
            setLevel(-1)
        }
        caseBattery = BatteryRingView(requireContext(), pal).apply {
            label = Lang.t("充电盒", "Case")
            setUnavailable(Lang.t("未上报", "Not reported"))
        }
        rightBattery = BatteryRingView(requireContext(), pal).apply {
            label = Lang.t("右耳", "Right")
            setLevel(-1)
        }
        listOf(leftBattery, caseBattery, rightBattery).forEach {
            batteryRow.addView(it, LinearLayout.LayoutParams(0, dp(118), 1f))
        }
        batteryCard.addView(batteryRow)
        root.addView(batteryCard)

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val forget = MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("取消配对", "Forget")
            setOnClickListener {
                Toast.makeText(requireContext(),
                    Lang.t("请在系统蓝牙设置中选择“取消配对”。",
                        "Choose “Forget” in Android Bluetooth settings to unpair the earbuds."),
                    Toast.LENGTH_LONG).show()
                openBluetoothSettings()
            }
        }
        val disconnect = MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("断开", "Disconnect")
            setOnClickListener {
                GaiaBleClient.getInstance().shutdown()
                refreshUi()
            }
        }
        actionRow.addView(forget, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginEnd = dp(6) })
        actionRow.addView(disconnect, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginStart = dp(6) })
        root.addView(actionRow, columnParams(top = 14, bottom = 20))

        addSection(Lang.t("主动降噪", "Active noise control"))
        ancGroup = MaterialButtonToggleGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
            orientation = MaterialButtonToggleGroup.HORIZONTAL
        }
        ancModes.forEachIndexed { index, mode ->
            val label = when (mode) {
                1 -> Lang.t("降噪", "ANC")
                0 -> Lang.t("关闭", "Off")
                3 -> Lang.t("抗风噪", "Wind")
                else -> Lang.t("通透", "Transparency")
            }
            val button = MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                tag = mode
                text = label
                isCheckable = true
                textSize = 11f
                insetTop = 0
                insetBottom = 0
                minimumHeight = dp(54)
            }
            ancGroup.addView(button, LinearLayout.LayoutParams(0, dp(58), 1f).also {
                it.marginEnd = if (index == ancModes.lastIndex) 0 else dp(4)
            })
        }
        ancGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!rebuilding && isChecked) {
                val selected = group.findViewById<MaterialButton>(checkedId)?.tag as? Int
                    ?: return@addOnButtonCheckedListener
                if (GaiaBleClient.getInstance().isGaiaReady()) AncBridge.setAncMode(selected)
                else refreshAncSelection()
            }
        }
        root.addView(ancGroup)

        addSection(Lang.t("声音增益", "Sound gain"))
        gainGroup = MaterialButtonToggleGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        listOf(Lang.t("低", "Low"), Lang.t("中", "Medium"), Lang.t("高", "High"))
            .forEachIndexed { index, label ->
                val button = MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    tag = index
                    text = label
                    isCheckable = true
                    minimumHeight = dp(52)
                }
                gainGroup.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f).also {
                    it.marginEnd = if (index == 2) 0 else dp(6)
                })
            }
        gainGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!rebuilding && isChecked) {
                val level = group.findViewById<MaterialButton>(checkedId)?.tag as? Int
                    ?: return@addOnButtonCheckedListener
                if (GaiaBleClient.getInstance().isGaiaReady()) DeviceControlBridge.setGain(level)
                else refreshGainSelection()
            }
        }
        root.addView(gainGroup)

        addSection(Lang.t("更多控制", "More controls"))
        eqButton = MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("参数均衡器", "Parametric equalizer")
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            setIconResource(R.drawable.ic_tune)
            iconGravity = MaterialButton.ICON_GRAVITY_START
            setOnClickListener {
                if (isEnabled) startActivity(Intent(requireContext(), EqEditorActivity::class.java))
            }
        }
        root.addView(eqButton, LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(TextView(requireContext()).apply {
            text = Lang.t("仅当检测到耳机的 9ECA BLE 服务时显示可用。", 
                "Available when the earbud's 9ECA BLE service is detected.")
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(dp(2), dp(6), dp(2), 0)
        })

        connectButton = MaterialButton(requireContext()).apply {
            text = Lang.t("连接耳机", "Connect")
            setOnClickListener { connectToHeadset() }
        }
        root.addView(connectButton, columnParams(top = 20))
        refreshUi()
        return scroller
    }

    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        val gaia = GaiaBleClient.getInstance()
        gaia.setCallback(callback)
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
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun connectToHeadset() {
        val ctx = context ?: return
        val gaia = GaiaBleClient.getInstance()
        if (gaia.isGaiaReady()) {
            refreshUi()
            gaia.fetchBatteryLevels()
            AncBridge.fetchAncMode()
            DeviceControlBridge.fetchGain()
            return
        }
        status.text = Lang.t("正在连接 Space Travel 2…", "Connecting to Space Travel 2…")
        status.setTextColor(pal.onVariant)
        connectButton.isEnabled = false
        Thread {
            val mac = HeadsetGate.getConnectedMac(ctx)
            if (mac != null) gaia.connect(ctx.applicationContext, mac)
            handler.post {
                if (!isAdded) return@post
                connectButton.isEnabled = true
                refreshUi()
            }
        }.start()
    }

    private fun refreshUi() {
        if (!isAdded || !::status.isInitialized) return
        val gaia = GaiaBleClient.getInstance()
        val name = gaia.getConnectedDeviceName().orEmpty()
        val connected = gaia.isGaiaReady()
        val isSpaceTravel2 = name.contains("SPACE TRAVEL 2", true) ||
            name.contains("太空漫游2") || name.contains("SPACE TRAVEL II", true)
        deviceName.text = if (name.isNotBlank()) name else "Space Travel 2"
        status.text = when {
            !connected -> Lang.t("未连接", "Not connected")
            !isSpaceTravel2 -> Lang.t("当前连接的不是 Space Travel 2", "Connected device is not Space Travel 2")
            else -> Lang.t("已连接 · GAIA + 9ECA", "Connected · GAIA + 9ECA")
        }
        status.setTextColor(if (connected && isSpaceTravel2) pal.primary else pal.onVariant)
        val usable = connected && isSpaceTravel2
        ancGroup.isEnabled = usable
        for (i in 0 until ancGroup.childCount) ancGroup.getChildAt(i).isEnabled = usable
        gainGroup.isEnabled = usable
        for (i in 0 until gainGroup.childCount) gainGroup.getChildAt(i).isEnabled = usable
        eqButton.isEnabled = usable && gaia.hasSrcService()
        eqButton.alpha = if (eqButton.isEnabled) 1f else 0.55f
        connectButton.visibility = if (usable) View.GONE else View.VISIBLE
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
        // The documented Space Travel 2 GAIA telemetry provides L/R battery only.
        caseBattery.setUnavailable(Lang.t("未上报", "Not reported"))
    }

    private fun refreshAncSelection() {
        if (!::ancGroup.isInitialized) return
        val mode = AncBridge.getCurrentMode()
        rebuilding = true
        val button = (0 until ancGroup.childCount).map { ancGroup.getChildAt(it) as MaterialButton }
            .firstOrNull { (it.tag as? Int) == mode }
        if (button != null) ancGroup.check(button.id) else ancGroup.clearChecked()
        rebuilding = false
    }

    private fun refreshGainSelection() {
        if (!::gainGroup.isInitialized) return
        val level = DeviceControlBridge.getGainLevel()
        rebuilding = true
        val button = (0 until gainGroup.childCount).map { gainGroup.getChildAt(it) as MaterialButton }
            .firstOrNull { (it.tag as? Int) == level }
        if (button != null) gainGroup.check(button.id) else gainGroup.clearChecked()
        rebuilding = false
    }

    private fun addSection(title: String) {
        root.addView(TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(18), 0, dp(10))
        })
    }

    private fun columnParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(top); it.bottomMargin = dp(bottom) }

    private fun openBluetoothSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class BatteryRingView(context: android.content.Context, private val palette: ThemeUtil.Palette) : View(context) {
        var label: String = ""
            set(value) { field = value; invalidate() }
        private var level = -1
        private var unavailableText = "—"
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 7f * resources.displayMetrics.density
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
            level = value.coerceIn(-1, 100)
            unavailableText = "—"
            invalidate()
        }

        fun setUnavailable(text: String) {
            level = -1
            unavailableText = text
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = 43f * d
            val radius = 29f * d
            ring.color = palette.outline
            val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(bounds, -90f, 360f, false, ring)
            ring.color = if (level >= 0) palette.primary else palette.outline
            if (level >= 0) canvas.drawArc(bounds, -90f, 360f * level / 100f, false, ring)
            text.color = palette.onSurface
            text.textSize = 15f * d
            canvas.drawText(if (level >= 0) "$level%" else "—", cx, cy + 5f * d, text)
            small.color = palette.onVariant
            small.textSize = 11f * d
            canvas.drawText(if (unavailableText != "—") unavailableText else label,
                cx, 95f * d, small)
        }
    }
}
