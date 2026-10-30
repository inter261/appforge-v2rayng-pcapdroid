package com.v2ray.ang.pcap

import android.content.Context
import android.content.Intent
import android.os.Message
import android.os.Messenger
import android.widget.Toast
import com.emanuelef.remote_capture.ExternalTun
import com.emanuelef.remote_capture.model.CaptureSettings
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

/**
 * Main-process side of the PCAPdroid integration. Registered as the ExternalTun provider, so the
 * PCAPdroid UI never creates its own VPN: "Start" asks the VPN daemon to hand over the tun,
 * "Stop" ends the capture and the daemon gives the tun back to hev-socks5-tunnel.
 */
object PcapBridge : ExternalTun.Provider {
    @Volatile
    private var pendingSettings: CaptureSettings? = null

    /** Messenger used to tell the daemon that the capture ended (set by PcapLinkService) */
    @Volatile
    var daemonReply: Messenger? = null

    fun install() {
        ExternalTun.setProvider(this)
    }

    override fun isAvailable(): Boolean = true

    override fun startAnalysis(ctx: Context, settings: CaptureSettings): Boolean {
        if (!MmkvManager.decodeSettingsBool(PcapLink.PREF_VPN_UP, false)) {
            toast(ctx, R.string.pcap_need_vpn)
            return false
        }
        if (!SettingsManager.isUsingHevTun()) {
            toast(ctx, R.string.pcap_need_hev)
            return false
        }

        pendingSettings = settings
        ctx.sendBroadcast(
            Intent(PcapLink.ACTION_SYNC)
                .setPackage(ctx.packageName)
                .putExtra(PcapLink.EXTRA_ENABLE, true)
        )
        return true
    }

    override fun onAnalysisEnded(ctx: Context) {
        val reply = daemonReply
        daemonReply = null
        try {
            reply?.send(Message.obtain(null, PcapLink.MSG_ENDED))
        } catch (_: Exception) {
            // the daemon is gone, nothing to hand back
        }
    }

    fun takePendingSettings(): CaptureSettings? {
        val s = pendingSettings
        pendingSettings = null
        return s
    }

    private fun toast(ctx: Context, resId: Int) {
        Toast.makeText(ctx.applicationContext, resId, Toast.LENGTH_LONG).show()
    }
}
