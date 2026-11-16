package com.fxxkmoondrop.secret

import android.content.Context
import java.util.Locale

/**
 * English-only display strings for the standalone MoonEar companion.
 *
 * Bluetooth protocol values, device names and control mappings are not changed.
 * This object keeps the existing API so call sites stay small and consistent.
 */
object Lang {
    private const val LANG_EN = 2

    @Volatile
    private var zh: Boolean = false

    /** The standalone app is English-only. Ignore any legacy saved language value. */
    fun mode(ctx: Context): Int = LANG_EN

    fun isZh(ctx: Context): Boolean = false

    /** Refresh is retained for existing UI call sites; English is always selected. */
    fun refresh(ctx: Context) {
        zh = false
    }

    /** Return the English label. */
    fun t(zh: String, en: String): String = en

    fun t(ctx: Context, zh: String, en: String): String {
        refresh(ctx)
        return en
    }

    /** Format numeric status strings using the English format. */
    fun tf(zhFmt: String, enFmt: String, vararg args: Any): String =
        String.format(Locale.US, enFmt, *args)
}
