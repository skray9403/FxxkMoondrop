package com.fxxkmoondrop.secret

import android.content.Context
import android.util.Log

/**
 * 降噪控制桥（alpha1.0 直连 GAIA 版）：
 * 通过 GaiaBleClient 直接发送 GAIA V3 ANC 命令，
 * 不再依赖 Moondrop 官方 App。
 */
class AncBridge {
    companion object {
        private const val TAG = "AncBridge"

        @JvmField
        val MODE_NAMES = AncProfileLib.ANC_MODE_NAMES_FULL

        
        /** 广播用 Context（由 MainActivity / HeadsetDetectService 绑定，取 applicationContext） */
        @Volatile
        private var sCtx: Context? = null

        @JvmStatic
        fun bind(ctx: Context?) {
            if (ctx != null) sCtx = ctx.applicationContext
        }

        /** 最新 ANC 模式缓存 */
        @Volatile
        private var currentMode = -1
        @Volatile
        private var currentModeVersion = 0 // 用于 UI 刷新

        /** 获取当前缓存的 ANC 模式（-1 表示未知） */
        @JvmStatic
        fun getCurrentMode(): Int = currentMode

        @JvmStatic
        fun getCurrentModeVersion(): Int = currentModeVersion

        /** 由 GaiaBleClient 回调调用，更新模式 */
        @JvmStatic
        fun notifyAncMode(mode: Int) {
            if (mode >= 0 && mode < MODE_NAMES.size) {
                currentMode = mode
                currentModeVersion++
                Log.d(TAG, "ANC mode updated: $mode (${MODE_NAMES[mode]})")
                // 第 5 项：ANC 通知高亮跟随模式。
                // 挂在这里而不是 GaiaBleClient.Callback.onAncMode —— 实测 GA2 的模式数据
                // 走 AncBridge 内部回调（fetchAncMode / setAncMode 的 callback），
                // 外层 callback 不一定被触发，挂外层会漏刷新。
                sCtx?.let { DeviceNotif.refreshAnc(it) }
                // 3.2.12: 磁贴也挂这里 —— 它与通知栏是同一份「当前档位」的两种呈现，
                // 挂在同一个点上才不会出现「通知栏已变、磁贴还是旧档位」
                AncTileService.refreshAll(sCtx)
            }
        }

        /** 设置 ANC 模式（UI：0=关闭 1=降噪 2=透传 3=抗风 4=自适应 5=直播）
         *  alpha2.14: UI 模式直传 GaiaBleClient，由能力探测结果选择协议路径
         *  （AudioCuration / ANC V2 / ANC V1），设备枚举映射在 GaiaCommands.ancDevFromUi */
        /** 最近一次下发（仅用于 200ms 重入去重） */
        private var lastSentMode = -1
        private var lastSentAt = 0L

        @JvmStatic
        fun setAncMode(mode: Int) {
            if (mode < 0 || mode > 5) return
            // 只挡「同一次事件的重入」：同档位 200ms 内重复调用直接丢弃。
            // ⚠️ 不要放大这个窗口 —— 连接后同步档位、重连对齐都走这里，
            // 窗口一大就会吞掉正常命令（上一版 1.2s+限流那次就是这么把耳机搞成「连不上」的）。
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (mode == lastSentMode && nowMs - lastSentAt < 200L) {
                Log.d(TAG, "setAncMode reentry ignored: " + mode)
                return
            }
            lastSentMode = mode
            lastSentAt = nowMs
            Log.d(TAG, "setAncMode ui=$mode (path 由能力探测自动选择)")
            // alpha1.21: 乐观更新——GA2 AudioCuration 设备不回 ACK，不能依赖回调刷新
            // Optimistic state update; a device response can replace it with the confirmed state.
            val prevMode = currentMode
            notifyAncMode(mode)
            GaiaBleClient.getInstance().setAncMode(mode, object : GaiaBleClient.AncControlCallback {
                override fun onAncModeResult(m: Int) {
                    notifyAncMode(m)
                }

                override fun onAncError(message: String) {
                    // alpha2.22: 发送被拒（能力未就绪/无ANC/不支持）时回滚乐观高亮，
                    // 让用户明确看到按钮没真正生效，避免"假高亮"误导
                    Log.w(TAG, "setAncMode error: $message -> rollback highlight")
                    notifyAncMode(prevMode)
                }
            })
        }

        /** 查询当前 ANC 模式 */
        @JvmStatic
        fun fetchAncMode() {
            GaiaBleClient.getInstance().fetchAncMode(object : GaiaBleClient.AncControlCallback {
                override fun onAncModeResult(mode: Int) {
                    notifyAncMode(mode)
                }

                override fun onAncError(message: String) {
                    Log.w(TAG, "fetchAncMode error: $message")
                }
            })
        }
    }
}
