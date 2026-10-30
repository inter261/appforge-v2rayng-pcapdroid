package com.v2ray.ang.pcap

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.preference.PreferenceManager
import com.emanuelef.remote_capture.CaptureService
import com.emanuelef.remote_capture.ExternalTun
import com.emanuelef.remote_capture.Log as PcapLog
import com.emanuelef.remote_capture.model.CaptureSettings
import com.emanuelef.remote_capture.model.Prefs

/**
 * Runs in the main process. The VPN daemon binds to it and sends the tun file descriptor:
 * the capture is then started on that tun by PCAPdroid's CaptureService.
 * When the daemon (or the whole process on the other side) goes away the binding drops.
 */
class PcapLinkService : Service() {
    private val handler = Handler(Looper.getMainLooper(), Handler.Callback { msg ->
        when (msg.what) {
            PcapLink.MSG_START -> handleStart(msg)
            PcapLink.MSG_STOP -> CaptureService.stopService()
        }
        true
    })
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handleStart(msg: Message) {
        PcapBridge.daemonReply = msg.replyTo
        PcapLog.i(TAG, "tun received from the VPN service, starting the capture")
        val data = msg.data
        val tunFd = BundleCompat.getParcelable(data, PcapLink.KEY_TUN_FD, ParcelFileDescriptor::class.java)
        if (tunFd == null) {
            Log.e(TAG, "MSG_START without tun fd")
            PcapBridge.onAnalysisEnded(this)
            return
        }

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val settings: CaptureSettings = PcapBridge.takePendingSettings() ?: CaptureSettings(this, prefs)

            settings.root_capture = false
            settings.input_pcap_path = null

            // All the TCP traffic goes to the local SOCKS5 inbound of the Xray core
            settings.socks5_enabled = true
            settings.socks5_proxy_address = "127.0.0.1"
            settings.socks5_proxy_port = data.getInt(PcapLink.KEY_SOCKS_PORT)
            settings.socks5_username = data.getString(PcapLink.KEY_SOCKS_USER) ?: ""
            settings.socks5_password = data.getString(PcapLink.KEY_SOCKS_PASS) ?: ""

            settings.ip_mode =
                if (data.getBoolean(PcapLink.KEY_IPV6)) Prefs.IpMode.BOTH else Prefs.IpMode.IPV4_ONLY

            // UDP is not proxied in this mode: force QUIC clients to fall back to TCP
            settings.block_quic_mode = Prefs.BlockQuicMode.ALWAYS

            // the fd cannot go in the Intent (startService refuses file descriptors): stage a dup
            ExternalTun.stageTunFd(tunFd.dup())

            val intent = Intent(this, CaptureService::class.java)
                .putExtra("settings", settings)
                .putExtra(ExternalTun.EXTRA_HAS_TUN, true)
                .putExtra(ExternalTun.EXTRA_VPN_IPV4, data.getString(PcapLink.KEY_IPV4))
                .putExtra(ExternalTun.EXTRA_VPN_DNS, data.getString(PcapLink.KEY_DNS))
                .putExtra(ExternalTun.EXTRA_MTU, data.getInt(PcapLink.KEY_MTU))

            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            ExternalTun.stageTunFd(null)
            Log.e(TAG, "Cannot start the capture: $e")
            PcapLog.e(TAG, "Cannot start the capture: $e")
            PcapBridge.onAnalysisEnded(this)
        } finally {
            // CaptureService owns the staged duplicate of the descriptor
            try {
                tunFd.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "PcapLinkService"
    }
}
