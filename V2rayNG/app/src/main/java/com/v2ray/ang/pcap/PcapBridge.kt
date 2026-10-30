package com.v2ray.ang.pcap

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.Toast
import com.emanuelef.remote_capture.CaptureService
import com.emanuelef.remote_capture.ExternalTun
import com.emanuelef.remote_capture.Log
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
        val vpnUp = MmkvManager.decodeSettingsBool(PcapLink.PREF_VPN_UP, false)
        val hev = SettingsManager.isUsingHevTun()
        Log.i(TAG, "startAnalysis: vpnUp=$vpnUp hevTunnel=$hev")

        if (!vpnUp) {
            Log.w(TAG, "V2Ray VPN is not running: cannot attach")
            toast(ctx, R.string.pcap_need_vpn)
            return false
        }
        if (!hev) {
            Log.w(TAG, "HEV tunnel mode is disabled: cannot attach")
            toast(ctx, R.string.pcap_need_hev)
            return false
        }

        pendingSettings = settings
        ctx.sendBroadcast(
            Intent(PcapLink.ACTION_SYNC)
                .setPackage(ctx.packageName)
                .putExtra(PcapLink.EXTRA_ENABLE, true)
        )

        // The daemon answers asynchronously: if nothing started, tell why instead of waiting forever
        Handler(Looper.getMainLooper()).postDelayed({
            if (!CaptureService.isServiceActive()) {
                Log.w(TAG, "The VPN service did not start the capture within 6s")
                toast(ctx, R.string.pcap_not_started)
            }
        }, 6000)
        return true
    }

    override fun onAnalysisEnded(ctx: Context) {
        Log.i(TAG, "capture ended, giving the tun back to the VPN service")
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

    private const val TAG = "PcapBridge"

    private fun toast(ctx: Context, resId: Int) {
        Toast.makeText(ctx.applicationContext, resId, Toast.LENGTH_LONG).show()
    }
}
