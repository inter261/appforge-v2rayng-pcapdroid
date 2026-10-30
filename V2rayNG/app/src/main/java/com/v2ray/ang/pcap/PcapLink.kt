package com.v2ray.ang.pcap

import com.v2ray.ang.AppConfig

/**
 * Protocol between the VPN daemon process (":daemon", owns the tun and the Xray core) and the
 * main process (PCAPdroid UI and CaptureService).
 *
 *  main   -> daemon : broadcast ACTION_SYNC(enable)    start/stop routing the tun through PCAPdroid
 *  daemon -> main   : MSG_START (bundle with tun fd)   bound Messenger of PcapLinkService
 *  daemon -> main   : MSG_STOP                         stop the capture
 *  main   -> daemon : MSG_ENDED                        the capture is over, the tun is free again
 */
object PcapLink {
    const val MSG_START = 1
    const val MSG_STOP = 2
    const val MSG_ENDED = 3

    const val KEY_TUN_FD = "tun_fd"
    const val KEY_IPV4 = "ipv4"
    const val KEY_DNS = "dns"
    const val KEY_MTU = "mtu"
    const val KEY_IPV6 = "ipv6"
    const val KEY_SOCKS_PORT = "socks_port"
    const val KEY_SOCKS_USER = "socks_user"
    const val KEY_SOCKS_PASS = "socks_pass"

    const val ACTION_SYNC = AppConfig.ANG_PACKAGE + ".action.PCAP_SYNC"
    const val EXTRA_ENABLE = "enable"

    /** MMKV flag (multi-process) written by the daemon: the VPN is up and PCAPdroid can attach */
    const val PREF_VPN_UP = "pref_pcap_vpn_up"
}
