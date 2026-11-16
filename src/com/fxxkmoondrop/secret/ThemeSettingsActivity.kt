package com.fxxkmoondrop.secret

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 主题设置（二级页）。
 *
 * Material 3 Expressive theme, color and display settings
 * 全部收在这一页，设置页只留一个入口 —— 避免外观分组被七八行下拉撑爆。
 */
class ThemeSettingsActivity : BaseActivity() {

    private lateinit var pal: ThemeUtil.Palette
    private lateinit var body: LinearLayout

    /** 首次构建走分层入场，之后的 rebirth 只做整体淡入。 */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = ThemeUtil.Palette(this)
        val page = M3Ui.largeHeaderPageWithBack(this, pal,
                Lang.t("主题设置", "Theme settings")) { finish() }
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        // 与设置页同一套版心：左右 12dp、上下 16/24dp。
        // 不设的话卡片会直接贴到屏幕边缘，浅色下看不出、深色下像被切掉一条。
        body.setPadding(dp(12), dp(16), dp(12), dp(24))
        rebuild()
        page.sv.addView(body, LinearLayout.LayoutParams(-1, -2))
        setContentView(page.container)
        M3Ui.fitSystemBars(this, pal)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun sp() = getSharedPreferences("cfg", MODE_PRIVATE)

    private fun rebuild() {
        body.removeAllViews()

        // ── 主题 ─────────────────────────────────────────────
        body.addView(M3Ui.sectionTitle(this, pal, Lang.t("主题", "Theme")))
        val modeRow = M3Ui.dropdownRow(this, pal,
                Lang.t("主题", "Theme"),
                "",
                arrayOf(Lang.t("跟随系统", "System"), Lang.t("浅色", "Light"), Lang.t("深色", "Dark")),
                ThemeUtil.themeMode(this)) { mi ->
            sp().edit().putInt("theme_mode", mi).commit()
            recreate()
        }

        // 强调色（种子色）：只在跟固定主色时才显示。
        // 始终建出来、用 visibility 控制，才能在开关切换时走 reveal 的淡入/淡出动画
        // （条件创建 = 只能"啪"地出现或消失，没有过渡）。
        val dynOn = sp().getBoolean("dynamic_color", true)
        val seedRow = buildSeedRow()
        seedRow.visibility = if (dynOn) View.GONE else View.VISIBLE

        body.addView(M3Ui.groupCard(this, pal, modeRow, seedRow), LinearLayout.LayoutParams(-1, -2))

        // ── 配色 ─────────────────────────────────────────────
        body.addView(M3Ui.sectionTitle(this, pal, Lang.t("配色", "Colors")))
        val dynRow = M3Ui.switchRow(this, pal,
                Lang.t("动态取色", "Dynamic color"),
                "",
                themeSwitch("dynamic_color", true, Lang.t("动态取色", "Dynamic color")) { on ->
                    // 立刻播强调色行的显隐动画；palette 重建延后到开关动画结束
                    M3Ui.reveal(seedRow, !on)
                })
        val amoledRow = M3Ui.switchRow(this, pal,
                Lang.t("AMOLED 纯黑", "AMOLED pure black"),
                "",
                themeSwitch("amoled", false, "AMOLED"))
        // 色彩规范：只换 M3 语义角色的 tone 表（颜色），不影响形状与动效。
        val stdRow = M3Ui.dropdownRow(this, pal,
                Lang.t("色彩规范", "Color spec"),
                "",
                ColorSpec.entries.map { Lang.t(it.zh, it.en) }.toTypedArray(),
                ColorSpec.current(this).ordinal) { i ->
            ColorSpec.set(this, ColorSpec.entries[i])
            recreate()
        }
        val variantRow = M3Ui.dropdownRow(this, pal,
                Lang.t("调色板风格", "Palette style"),
                "",
                M3SchemeVariant.entries.map { Lang.t(it.zh, it.en) }.toTypedArray(),
                M3SchemeVariant.current(this).ordinal) { i ->
            M3SchemeVariant.set(this, M3SchemeVariant.entries[i])
            recreate()
        }
        body.addView(M3Ui.groupCard(this, pal, dynRow, amoledRow, stdRow, variantRow),
                LinearLayout.LayoutParams(-1, -2))

        // ── 界面：导航方式 + 界面缩放 ─────────────────────────
        body.addView(M3Ui.sectionTitle(this, pal, Lang.t("界面", "Interface")))
        val storedScale = sp().getFloat("ui_scale", 1f)
        val scale = if (storedScale.isFinite()) storedScale.coerceIn(0.8f, 1.2f) else 1f

