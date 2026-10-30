package com.v2ray.ang.pcap

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil

/**
 * Runs in the VPN daemon process. Decides who reads the tun interface:
 *  - normally hev-socks5-tunnel (tun -> SOCKS5 -> Xray)
 *  - when the analysis is ON, PCAPdroid in the main process (tun -> PCAPdroid -> SOCKS5 -> Xray)
 *
 * Only one reader at a time: hev is stopped before the tun is handed over, and it is started again
 * as soon as the capture ends (user stop, error, crash of the main process).
 */
class PcapTunController(
    private val service: Service,
    private val tunProvider: () -> ParcelFileDescriptor?,
    private val stopHev: () -> Unit,
    private val startHev: () -> Unit,
    private val isVpnRunning: () -> Boolean
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var active = false
        private set

    private var link: Messenger? = null
    private var bound = false
    private var stopRequested = false
    private var shuttingDown = false

    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), Handler.Callback { msg ->
        if (msg.what == PcapLink.MSG_ENDED) onEnded()
        true
    }))

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            link = Messenger(binder)
            if (stopRequested) onEnded() else sendStart()
        }

        // the main process died: its capture is gone, take the tun back
        override fun onServiceDisconnected(name: ComponentName?) = onEnded()
        override fun onBindingDied(name: ComponentName?) = onEnded()
        override fun onNullBinding(name: ComponentName?) = onEnded()
    }

    @Synchronized
    fun enable() {
        if (active || shuttingDown || !isVpnRunning() || !SettingsManager.isUsingHevTun()) {
            LogUtil.w(AppConfig.TAG, "PCAP: enable ignored (active=$active shuttingDown=$shuttingDown vpnRunning=${isVpnRunning()} hev=${SettingsManager.isUsingHevTun()})")
            return
        }
        if (tunProvider() == null) {
            LogUtil.w(AppConfig.TAG, "PCAP: enable ignored, no tun interface")
            return
        }

        LogUtil.i(AppConfig.TAG, "PCAP: handing the tun to PCAPdroid")
        stopHev()
        active = true
        stopRequested = false

        val ok = try {
            service.bindService(
                Intent(service, PcapLinkService::class.java), connection, Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            false
        }
        bound = ok
        if (!ok) onEnded()
    }

    @Synchronized
    fun disable() {
        if (!active) return
        stopRequested = true
        val l = link
        if (l == null) {
            // not connected yet: nothing started, just give the tun back
            onEnded()
            return
        }
        try {
            l.send(Message.obtain(null, PcapLink.MSG_STOP))
        } catch (e: Exception) {
            onEnded()
        }
    }

    /** The VPN is going down: stop the capture, do not restart hev. */
    @Synchronized
    fun shutdown() {
        shuttingDown = true
        if (!active) return
        active = false
        try {
            link?.send(Message.obtain(null, PcapLink.MSG_STOP))
        } catch (_: Exception) {
        }
        // let the STOP message be delivered before the binding is dropped
        mainHandler.postDelayed({ unbind() }, 1500)
    }

    private fun sendStart() {
        val l = link ?: return onEnded()
        val tun = tunProvider() ?: return onEnded()
        try {
            val dup = ParcelFileDescriptor.dup(tun.fileDescriptor)
            val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
            val bundle = Bundle().apply {
                putParcelable(PcapLink.KEY_TUN_FD, dup)
                putString(PcapLink.KEY_IPV4, vpnConfig.ipv4Client)
                putString(PcapLink.KEY_DNS, firstIpv4Dns())
                putInt(PcapLink.KEY_MTU, SettingsManager.getVpnMtu())
                putBoolean(PcapLink.KEY_IPV6, MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED))
                putInt(PcapLink.KEY_SOCKS_PORT, SettingsManager.getSocksPort())
                putString(PcapLink.KEY_SOCKS_USER, SettingsManager.getSocksUsername())
                putString(PcapLink.KEY_SOCKS_PASS, SettingsManager.getSocksPassword())
            }
            val msg = Message.obtain(null, PcapLink.MSG_START).apply {
                data = bundle
                replyTo = replyMessenger
            }
            l.send(msg)
            // the message holds its own duplicate of the descriptor
            try {
                dup.close()
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "PCAP: cannot hand over the tun: $e")
            onEnded()
        }
    }

    private fun firstIpv4Dns(): String? =
        SettingsManager.getVpnDnsServers().firstOrNull { IPV4.matches(it) }

    private fun unbind() {
        synchronized(this) {
            if (bound) {
                try {
                    service.unbindService(connection)
                } catch (_: Exception) {
                }
                bound = false
            }
            link = null
        }
    }

    @Synchronized
    private fun onEnded() {
        if (!active) return
        active = false
        stopRequested = false
        LogUtil.i(AppConfig.TAG, "PCAP: capture ended, tun returned")
        unbind()
        if (!shuttingDown && isVpnRunning()) startHev()
    }

    private companion object {
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    }
}
