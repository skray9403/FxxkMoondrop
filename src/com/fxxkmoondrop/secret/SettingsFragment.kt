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

/**
 * Minimal settings for the standalone MoonEar companion.
 *
 * Legacy module, injection, popup, simulation and diagnostic controls do not
 * belong in this app and are intentionally not exposed here.
 */
class SettingsFragment : Fragment() {
    private lateinit var pal: ThemeUtil.Palette
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        Lang.refresh(requireContext())
        pal = ThemeUtil.Palette(requireContext())
        val prefs = requireContext().getSharedPreferences("cfg", 0)

        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(pal.surface)
            clipToPadding = false
            setPadding(dp(20), dp(18), dp(20), dp(32))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        content.addView(TextView(requireContext()).apply {
            text = "Settings"
            textSize = 32f
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setTextColor(pal.onSurface)
        })
        content.addView(TextView(requireContext()).apply {
            text = "Make MoonEar work the way you prefer."
            textSize = 14f
            setTextColor(pal.onVariant)
        }, LinearLayout.LayoutParams(-1, -2).also {
            it.topMargin = dp(4)
            it.bottomMargin = dp(12)
        })

        section(content, "Appearance")
        content.addView(
            navigationCard(
                "Theme and colours",
                "Dynamic colour, light and dark appearance"
            ) {
                startActivity(Intent(requireContext(), ThemeSettingsActivity::class.java))
            }
        )

        section(content, "Notifications")
        content.addView(
            toggleCard(
                "Battery notifications",
                "Show left and right earbud battery levels when available",
                prefs.getBoolean("feat_notif_battery", true)
            ) { enabled ->
                prefs.edit().putBoolean("feat_notif_battery", enabled).apply()
                DeviceNotif.refreshBattery(requireContext())
            }
        )
        content.addView(
            toggleCard(
                "Noise-control notification",
                "Show ANC and transparency controls in the notification",
                prefs.getBoolean("feat_notif_anc", true)
            ) { enabled ->
                prefs.edit().putBoolean("feat_notif_anc", enabled).apply()
                DeviceNotif.refreshAnc(requireContext())
            }
        )

        section(content, "Background")
        content.addView(
            toggleCard(
                "Background monitoring",
                "Reconnect and refresh battery while the earbuds are in use",
                KeepAlive.enabled(requireContext())
            ) { enabled ->
                KeepAlive.setEnabled(requireContext(), enabled)
            }
        )

        section(content, "About")
        content.addView(
            navigationCard(
                "MoonEar",
                "A dedicated companion for Moondrop Space Travel 2"
            ) {
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/skray9403/FxxkMoondrop")
                        )
                    )
                }
            }
        )
        return scroll
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTextColor(pal.onVariant)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
            setPadding(dp(2), dp(22), dp(2), dp(10))
        })
    }

    private fun card() = MaterialCardView(requireContext()).apply {
        radius = dp(26).toFloat()
        setCardBackgroundColor(pal.card)
        strokeWidth = 0
        cardElevation = 0f
    }

    private fun toggleCard(
        title: String,
        summary: String,
        checked: Boolean,
        changed: (Boolean) -> Unit
    ): MaterialCardView {
        val card = card()
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(17), dp(14), dp(17))
        }
        val copy = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        copy.addView(TextView(requireContext()).apply {
            text = summary
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(MaterialSwitch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> changed(value) }
        })
        card.addView(row)
        return card
    }

    private fun navigationCard(
        title: String,
        summary: String,
        action: () -> Unit
    ): MaterialCardView {
        val card = card().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val copy = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(pal.onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        })
        copy.addView(TextView(requireContext()).apply {
            text = summary
            textSize = 12f
            setTextColor(pal.onVariant)
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(requireContext()).apply {
            text = "›"
            textSize = 26f
            setTextColor(pal.onVariant)
        })
        card.addView(row)
        return card
    }
}
