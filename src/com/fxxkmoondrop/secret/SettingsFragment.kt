package com.fxxkmoondrop.secret

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/** Settings that remain meaningful for the standalone Space Travel 2 companion. */
class SettingsFragment : Fragment() {
    private lateinit var pal: ThemeUtil.Palette
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        Lang.refresh(requireContext())
        pal = ThemeUtil.Palette(requireContext())
        val sp = requireContext().getSharedPreferences("cfg", 0)
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(pal.surface)
            clipToPadding = false
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        val body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, ScrollView.LayoutParams(-1, -2))

        body.addView(TextView(requireContext()).apply {
            text = Lang.t("设置", "Settings")
            textSize = 30f
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setTextColor(pal.onSurface)
        })
        body.addView(TextView(requireContext()).apply {
            text = "Space Travel 2"
            textSize = 14f
            setTextColor(pal.onVariant)
        }, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp(18) })

        section(body, Lang.t("外观", "Appearance"))
        body.addView(rowCard(
            Lang.t("主题与颜色", "Theme and colors"),
            Lang.t("Material 3 Expressive、动态取色和深浅色模式",
                "Material 3 Expressive, dynamic color and light/dark mode")
        ) {
            startActivity(Intent(requireContext(), ThemeSettingsActivity::class.java))
        })

        section(body, Lang.t("通知", "Notifications"))
        body.addView(toggleCard(
            Lang.t("电量通知", "Battery notifications"),
            Lang.t("显示左右耳电量（耳机上报时）", "Show left and right earbud charge when available"),
            sp.getBoolean("feat_notif_battery", true)
        ) { enabled ->
            sp.edit().putBoolean("feat_notif_battery", enabled).apply()
            DeviceNotif.refreshBattery(requireContext())
        })
        body.addView(toggleCard(
            Lang.t("降噪控制通知", "Noise-control notification"),
            Lang.t("在通知中显示支持的降噪模式", "Show the supported noise-control modes in the notification"),
            sp.getBoolean("feat_notif_anc", true)
        ) { enabled ->
            sp.edit().putBoolean("feat_notif_anc", enabled).apply()
            DeviceNotif.refreshAnc(requireContext())
        })

        section(body, Lang.t("后台", "Background"))
        body.addView(toggleCard(
            Lang.t("后台监听", "Background monitoring"),
            Lang.t("耳机使用时自动重连并刷新电量", "Reconnect and refresh battery while the earbuds are in use"),
            KeepAlive.enabled(requireContext())
        ) { enabled ->
            KeepAlive.setEnabled(requireContext(), enabled)
        })

        section(body, Lang.t("语言", "Language"))
        val languageValue = TextView(requireContext()).apply {
            text = when (Lang.mode(requireContext())) {
                1 -> "中文"
                2 -> "English"
                else -> Lang.t("跟随系统", "System default")
            }
            textSize = 14f
            setTextColor(pal.onVariant)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val labels = arrayOf(Lang.t("跟随系统", "System default"), "中文", "English")
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(Lang.t("应用语言", "App language"))
                    .setItems(labels) { _, which ->
                        sp.edit().putInt("lang", which).apply()
                        Lang.refresh(requireContext())
                        activity?.recreate()
                    }.show()
            }
        }
        val languageCard = card().apply { addView(languageValue) }
        body.addView(languageCard)

        section(body, Lang.t("关于", "About"))
        body.addView(rowCard(
            "MoonEar",
            Lang.t("面向 Moondrop Space Travel 2 的独立蓝牙伴侣应用。",
                "A standalone Bluetooth companion for Moondrop Space Travel 2.")
        ) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/skray9403/FxxkMoondrop")))
            }
        })
        return scroll
    }

    private fun section(body: LinearLayout, title: String) {
        body.addView(TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(0, dp(20), 0, dp(10))
        })
    }

    private fun card() = MaterialCardView(requireContext()).apply {
        radius = dp(26).toFloat()
        cardBackgroundColor = pal.card
        strokeWidth = 0
        cardElevation = 0f
    }

    private fun toggleCard(title: String, summary: String, checked: Boolean,
                           changed: (Boolean) -> Unit): MaterialCardView {
        val card = card()
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }
        val text = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(requireContext()).apply {
            this.text = title
            textSize = 16f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        text.addView(TextView(requireContext()).apply {
            this.text = summary
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(MaterialSwitch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> changed(value) }
        })
        card.addView(row)
        return card
    }

    private fun rowCard(title: String, summary: String, action: () -> Unit): MaterialCardView {
        val card = card().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val text = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(requireContext()).apply {
            this.text = title
            textSize = 16f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        text.addView(TextView(requireContext()).apply {
            this.text = summary
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(requireContext()).apply {
            text = "›"
            textSize = 26f
            setTextColor(pal.onVariant)
        })
        card.addView(row)
        return card
    }
}
