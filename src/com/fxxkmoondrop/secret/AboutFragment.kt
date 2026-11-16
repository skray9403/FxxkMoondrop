package com.fxxkmoondrop.secret

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

/** About page for the standalone MoonEar companion. */
class AboutFragment : Fragment() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        Lang.refresh(requireContext())
        val pal = ThemeUtil.Palette(requireContext())
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(pal.surface)
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        body.addView(TextView(requireContext()).apply {
            text = "MoonEar"
            textSize = 30f
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setTextColor(pal.onSurface)
        })
        body.addView(TextView(requireContext()).apply {
            text = Lang.t("Moondrop Space Travel 2 蓝牙伴侣", "A Bluetooth companion for Moondrop Space Travel 2")
            textSize = 14f
            setTextColor(pal.onVariant)
            setPadding(0, dp(4), 0, dp(18))
        })
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(26).toFloat()
            setCardBackgroundColor(pal.card)
            strokeWidth = 0
            cardElevation = 0f
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(TextView(requireContext()).apply {
            text = Lang.t(
                "此版本专注于 Space Travel 2：电量、四种降噪模式、三档声音增益与 9ECA 参数均衡器。",
                "This build focuses on Space Travel 2: battery, four noise-control modes, three gain levels and the 9ECA parametric equalizer."
            )
            textSize = 14f
            setTextColor(pal.onSurface)
        })
        body.addView(card)
        body.addView(TextView(requireContext()).apply {
            text = Lang.t("源代码与版本信息", "Source code and version information")
            textSize = 14f
            setTextColor(pal.onVariant)
            setPadding(0, dp(22), 0, dp(8))
        })
        val link = MaterialCardView(requireContext()).apply {
            radius = dp(22).toFloat()
            cardBackgroundColor = pal.card
            strokeWidth = 0
            cardElevation = 0f
            isClickable = true
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/skray9403/FxxkMoondrop"))) }
            }
        }
        val label = TextView(requireContext()).apply {
            text = "github.com/skray9403/FxxkMoondrop"
            textSize = 14f
            setTextColor(pal.primary)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        link.addView(label)
        body.addView(link)
        return scroll
    }
}
