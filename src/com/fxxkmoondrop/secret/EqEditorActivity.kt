package com.fxxkmoondrop.secret

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import java.util.Locale

/**
 * Space Travel 2 PEQ page over its 9ECA BLE service.
 * Reads the actual firmware capability, PEQ config and every active PEQ point.
 * Draft changes are local only: COMMIT_PEQ action semantics are not documented as
 * verified for Space Travel 2, so device-side Apply deliberately remains locked.
 */
class EqEditorActivity : BaseActivity() {
    private lateinit var pal: ThemeUtil.Palette
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var graph: PeqGraph
    private lateinit var rows: LinearLayout
    private lateinit var pointTitle: TextView
    private lateinit var gainSlider: Slider
    private lateinit var gainValue: TextView
    private lateinit var filterButton: MaterialButton
    private lateinit var applyButton: MaterialButton
    private lateinit var reloadButton: MaterialButton

    private var client: BleSourceSwitchClient? = null
    private var readGeneration = 0
    private var selected = 0
    private var config: BleSourceProtocol.SrcPeqConfig? = null
    private var firmware: BleSourceProtocol.SrcFwInfo? = null
    private val original = ArrayList<PointDraft>()
    private val draft = ArrayList<PointDraft>()
    private var changingSlider = false

    private data class PointDraft(
        val index: Int,
        var frequencyHz: Int,
        var gainRaw: Int,
        var qRaw: Int,
        var filter: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.refresh(this)
        pal = ThemeUtil.Palette(this)
        val page = M3Ui.largeHeaderPageWithBack(this, pal,
            Lang.t("参数均衡器", "Parametric EQ"), Runnable { finish() })
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }

        status = TextView(this).apply {
            text = Lang.t("等待 9ECA BLE 服务…", "Waiting for 9ECA BLE service…")
            textSize = 13f
            setTextColor(pal.onVariant)
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }
        body.addView(status)

        graph = PeqGraph(this, pal)
        val graphCard = card()
        graphCard.addView(graph, LinearLayout.LayoutParams(-1, dp(238)))
        body.addView(graphCard)

        val pointHeading = TextView(this).apply {
            text = Lang.t("均衡器参数", "Equalizer controls")
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(18), 0, dp(8))
        }
        body.addView(pointHeading)

        val editor = card()
        val editorBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
        }
        pointTitle = TextView(this).apply {
            textSize = 16f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, 0, 0, dp(8))
        }
        editorBody.addView(pointTitle)

        gainValue = TextView(this).apply {
            textSize = 14f
            setTextColor(pal.primary)
        }
        editorBody.addView(gainValue)
        gainSlider = Slider(this).apply {
            valueFrom = -32768f
            valueTo = 32767f
            value = 0f
            setLabelFormatter { v -> String.format(Locale.US, "%d raw", v.toInt()) }
            addOnChangeListener { _, value, fromUser ->
                if (!fromUser || changingSlider || selected !in draft.indices) return@addOnChangeListener
                draft[selected].gainRaw = value.toInt()
                renderDraft()
            }
        }
        editorBody.addView(gainSlider, LinearLayout.LayoutParams(-1, -2))

        filterButton = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("滤波器", "Filter")
            setOnClickListener { showFilterPicker() }
        }
        editorBody.addView(filterButton, LinearLayout.LayoutParams(-1, dp(48)))
        val editPoint = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("编辑频率与 Q 值", "Edit frequency and Q")
            setOnClickListener { showPointDialog() }
        }
        editorBody.addView(editPoint, LinearLayout.LayoutParams(-1, dp(48)).also {
            it.topMargin = dp(8)
        })
        editor.addView(editorBody)
        body.addView(editor)

        val pointsTitle = TextView(this).apply {
            text = Lang.t("频点列表", "PEQ points")
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(18), 0, dp(8))
        }
        body.addView(pointsTitle)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pointsCard = card()
        pointsCard.addView(rows)
        body.addView(pointsCard)

        val actionsTitle = TextView(this).apply {
            text = Lang.t("操作", "Actions")
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(18), 0, dp(8))
        }
        body.addView(actionsTitle)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        reloadButton = MaterialButton(this).apply {
            text = Lang.t("重新读取", "Reload")
            setOnClickListener { readPeq() }
        }
        val resetButton = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("重置草稿", "Reset draft")
            setOnClickListener {
                draft.clear()
                draft.addAll(original.map { it.copy() })
                selected = selected.coerceIn(0, (draft.size - 1).coerceAtLeast(0))
                renderDraft()
            }
        }
        applyButton = MaterialButton(this).apply {
            text = Lang.t("应用", "Apply")
            isEnabled = false
        }
        actionRow.addView(reloadButton, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginEnd = dp(4) })
        actionRow.addView(resetButton, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginStart = dp(4); it.marginEnd = dp(4) })
        actionRow.addView(applyButton, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginStart = dp(4) })
        body.addView(actionRow)

        val warning = TextView(this).apply {
            text = Lang.t(
                "安全保护：本页会读取耳机 PEQ，并允许本地预览修改。Space Travel 2 的 PEQ 提交动作尚未完成实机验证，因此不会向耳机写入。",
                "Safety lock: this page reads the earbud PEQ and previews local edits. Space Travel 2 PEQ commit behaviour has not been hardware-verified, so no writes are sent."
            )
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        body.addView(warning)

        page.sv.addView(body, LinearLayout.LayoutParams(-1, -2))
        setContentView(page.container)
        M3Ui.fitSystemBars(this, pal)
    }

    override fun onResume() {
        super.onResume()
        GaiaBleClient.getInstance().retainConnection(this)
        readPeq()
    }

    override fun onDestroy() {
        readGeneration++
        GaiaBleClient.getInstance().releaseConnection(this)
        super.onDestroy()
    }

    private fun readPeq() {
        val generation = ++readGeneration
        val gaia = GaiaBleClient.getInstance()
        client = gaia.getSrcClient()
        val src = client
        if (!gaia.isGaiaReady() || !gaia.hasSrcService() || src == null) {
            showStatus(Lang.t(
                "9ECA 尚未就绪。请先返回设备页连接 Space Travel 2。",
                "9ECA is not ready. Return to the device page and connect Space Travel 2."
            ), true)
            reloadButton.isEnabled = true
            return
        }
        reloadButton.isEnabled = false
        draft.clear()
        original.clear()
        graph.setPoints(emptyList())
        showStatus(Lang.t("正在读取固件能力…", "Reading firmware capabilities…"))
        src.readProtocolFirmwareInfo(object : BleSourceSwitchClient.SrcCallback<BleSourceProtocol.SrcFwInfo> {
            override fun onSuccess(value: BleSourceProtocol.SrcFwInfo) {
                if (generation != readGeneration) return
                firmware = value
                if (!value.supportsPeq32()) {
                    reloadButton.isEnabled = true
                    showStatus(Lang.t(
                        "当前固件未声明 PEQ32 支持，已停止读取。",
                        "This firmware does not advertise PEQ32 support. Reading was stopped."
                    ), true)
                    return
                }
                readConfig(generation, src)
            }
            override fun onError(message: String) {
                if (generation != readGeneration) return
                reloadButton.isEnabled = true
                showStatus(message, true)
            }
        })
    }

    private fun readConfig(generation: Int, src: BleSourceSwitchClient) {
        src.getPeqConfig(object : BleSourceSwitchClient.SrcCallback<BleSourceProtocol.SrcPeqConfig> {
            override fun onSuccess(value: BleSourceProtocol.SrcPeqConfig) {
                if (generation != readGeneration) return
                config = value
                if (value.pointCount !in 1..BleSourceProtocol.PEQ_POINT_COUNT) {
                    reloadButton.isEnabled = true
                    showStatus(Lang.t("耳机没有返回可用的 PEQ 点。", "The earbud returned no usable PEQ points."), true)
                    return
                }
                readPoint(generation, src, 0, value.pointCount)
            }
            override fun onError(message: String) {
                if (generation != readGeneration) return
                reloadButton.isEnabled = true
                showStatus(message, true)
            }
        })
    }

    private fun readPoint(generation: Int, src: BleSourceSwitchClient, index: Int, count: Int) {
        if (generation != readGeneration) return
        if (index >= count) {
            reloadButton.isEnabled = true
            original.clear()
            original.addAll(draft.map { it.copy() })
            selected = 0
            renderDraft()
            val cfg = config
            val fw = firmware
            val preamp = cfg?.preGainRaw?.div(100f) ?: 0f
            showStatus(
                Lang.t(
                    "已读取 " + draft.size + " 个点 · 预增益 " + String.format(Locale.US, "%+.2f dB", preamp) +
                        " · FW " + (fw?.major ?: 0) + "." + (fw?.minor ?: 0) + "." + (fw?.patch ?: 0),
                    "Read " + draft.size + " points · preamp " + String.format(Locale.US, "%+.2f dB", preamp) +
                        " · FW " + (fw?.major ?: 0) + "." + (fw?.minor ?: 0) + "." + (fw?.patch ?: 0)
                )
            )
            return
        }
        src.getPeqPoint(index, object : BleSourceSwitchClient.SrcCallback<BleSourceProtocol.SrcPeqPoint> {
            override fun onSuccess(value: BleSourceProtocol.SrcPeqPoint) {
                if (generation != readGeneration) return
                draft.add(PointDraft(value.pointIndex, value.frequencyHz, value.gainRaw, value.qRaw, value.filter))
                readPoint(generation, src, index + 1, count)
            }
            override fun onError(message: String) {
                if (generation != readGeneration) return
                reloadButton.isEnabled = true
                showStatus(Lang.t("已读取 " + draft.size + "/" + count + " 个点：" + message,
                    "Read " + draft.size + "/" + count + " points: " + message), true)
                renderDraft()
            }
        })
    }

    private fun renderDraft() {
        graph.setPoints(draft)
        rowsSafeRender()
        if (draft.isEmpty()) {
            pointTitle.text = Lang.t("尚无数据", "No data")
            gainValue.text = ""
            filterButton.isEnabled = false
            gainSlider.isEnabled = false
            return
        }
        selected = selected.coerceIn(0, draft.lastIndex)
        val p = draft[selected]
        pointTitle.text = Lang.t("第 " + (selected + 1) + " 段 · " + p.frequencyHz + " Hz",
            "Band " + (selected + 1) + " · " + p.frequencyHz + " Hz")
        gainValue.text = Lang.t("增益原始值：", "Gain raw: ") + p.gainRaw
        changingSlider = true
        gainSlider.value = p.gainRaw.toFloat().coerceIn(gainSlider.valueFrom, gainSlider.valueTo)
        changingSlider = false
        filterButton.text = filterName(p.filter)
        filterButton.isEnabled = true
        gainSlider.isEnabled = true
    }

    private fun rowsSafeRender() {
        rows.removeAllViews()
        draft.sortedBy { it.frequencyHz }.forEach { point ->
            val item = MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = (point.index + 1).toString() + "   " + point.frequencyHz + " Hz"
                isCheckable = false
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                setOnClickListener {
                    selected = point.index.coerceIn(0, (draft.size - 1).coerceAtLeast(0))
                    renderDraft()
                }
            }
            rows.addView(item, LinearLayout.LayoutParams(-1, dp(44)))
        }
    }

    private fun showFilterPicker() {
        val filters = arrayOf("Peaking", "Low shelf", "High shelf", "Low pass",
            "High pass", "Band pass", "Notch", "All pass")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(Lang.t("滤波器类型", "Filter type"))
            .setSingleChoiceItems(filters, draft.getOrNull(selected)?.filter ?: 0) { dialog, which ->
                draft.getOrNull(selected)?.filter = which
                dialog.dismiss()
                renderDraft()
            }.setNegativeButton(Lang.t("取消", "Cancel"), null)
            .show()
    }

    private fun showPointDialog() {
        val point = draft.getOrNull(selected) ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val freq = com.google.android.material.textfield.TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = Lang.t("频率", "Frequency")
            suffixText = "Hz"
        }
        val freqInput = com.google.android.material.textfield.TextInputEditText(freq.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(point.frequencyHz.toString())
        }
        freq.addView(freqInput)
        box.addView(freq)
        val q = com.google.android.material.textfield.TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = Lang.t("Q 原始值", "Q raw")
        }
        val qInput = com.google.android.material.textfield.TextInputEditText(q.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(point.qRaw.toString())
        }
        q.addView(qInput)
        box.addView(q, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(8) })
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(Lang.t("编辑第 " + (selected + 1) + " 段", "Edit band " + (selected + 1)))
            .setView(box)
            .setNegativeButton(Lang.t("取消", "Cancel"), null)
            .setPositiveButton(Lang.t("预览", "Preview")) { _, _ ->
                val newFrequency = freqInput.text?.toString()?.toIntOrNull()
                val newQ = qInput.text?.toString()?.toIntOrNull()
                if (newFrequency == null || newFrequency !in 20..20000 || newQ == null || newQ !in 1..65535) {
                    showStatus(Lang.t("频率或 Q 原始值超出范围。", "Frequency or Q raw value is out of range."), true)
                } else {
                    point.frequencyHz = newFrequency
                    point.qRaw = newQ
                    renderDraft()
                }
            }.show()
    }

    private fun filterName(id: Int): String = when (id) {
        BleSourceProtocol.FILTER_PEAKING -> "Peaking"
        BleSourceProtocol.FILTER_LOW_SHELF -> "Low shelf"
        BleSourceProtocol.FILTER_HIGH_SHELF -> "High shelf"
        BleSourceProtocol.FILTER_LOW_PASS -> "Low pass"
        BleSourceProtocol.FILTER_HIGH_PASS -> "High pass"
        BleSourceProtocol.FILTER_BAND_PASS -> "Band pass"
        BleSourceProtocol.FILTER_NOTCH -> "Notch"
        BleSourceProtocol.FILTER_ALL_PASS -> "All pass"
        else -> "Unknown (" + id + ")"
    }

    private fun showStatus(message: String, error: Boolean = false) {
        status.text = message
        status.setTextColor(if (error) pal.red else pal.onVariant)
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(28).toFloat()
        setCardBackgroundColor(pal.card)
        strokeWidth = 0
        cardElevation = 0f
        setContentPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class PeqGraph(context: Context, private val palette: ThemeUtil.Palette) : View(context) {
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.divider
            strokeWidth = resources.displayMetrics.density
        }
        private val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = 2.5f * resources.displayMetrics.density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
        private val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onVariant
            textSize = 10f * resources.displayMetrics.density
        }
        private var points: List<PointDraft> = emptyList()

        fun setPoints(value: List<PointDraft>) {
            points = value.toList()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val left = 38f * d
            val right = width - 12f * d
            val top = 18f * d
            val bottom = height - 30f * d
            if (right <= left || bottom <= top) return

            for (i in 0..4) {
                val y = top + (bottom - top) * i / 4f
                canvas.drawLine(left, y, right, y, grid)
            }
            val xTicks = listOf(20, 100, 1000, 10000, 20000)
            xTicks.forEach { hz ->
                val x = xFor(hz, left, right)
                canvas.drawLine(x, top, x, bottom, grid)
                val label = when (hz) {
                    1000 -> "1k"
                    10000 -> "10k"
                    else -> hz.toString()
                }
                canvas.drawText(label, x - 10f * d, height - 8f * d, labels)
            }
            canvas.drawText("Gain raw", 2f * d, top + 4f * d, labels)
            if (points.isEmpty()) return
            val low = minOf(0, points.minOf { it.gainRaw }).toFloat()
            val high = maxOf(0, points.maxOf { it.gainRaw }).toFloat()
            val margin = maxOf(100f, (high - low) * 0.15f)
            val minGain = low - margin
            val maxGain = high + margin
            val span = (maxGain - minGain).coerceAtLeast(1f)
            val sorted = points.sortedBy { it.frequencyHz }
            val path = Path()
            sorted.forEachIndexed { i, p ->
                val x = xFor(p.frequencyHz, left, right)
                val y = bottom - ((p.gainRaw - minGain) / span) * (bottom - top)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                canvas.drawCircle(x, y, 3f * d, dots)
            }
            canvas.drawPath(path, curve)
        }

        private fun xFor(hz: Int, left: Float, right: Float): Float {
            val safe = hz.coerceIn(20, 20000).toFloat()
            val t = (kotlin.math.ln(safe / 20f) / kotlin.math.ln(1000f)).coerceIn(0f, 1f)
            return left + t * (right - left)
        }
    }
}
