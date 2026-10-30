/*
 * This file is part of PCAPdroid (modified for V2Ray Me).
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.emanuelef.remote_capture;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.emanuelef.remote_capture.model.CaptureSettings;

/**
 * Integration point between PCAPdroid and the host application.
 *
 * In the stock PCAPdroid, {@link CaptureService} creates its own VPN interface. In V2Ray Me the
 * VPN is owned by the V2Ray service: PCAPdroid must not call VpnService.Builder#establish, it
 * receives (a duplicate of) the tun file descriptor instead and works on it exactly like it does
 * in VPN mode. All the TCP connections are forwarded to a local SOCKS5 proxy (the Xray core).
 */
public final class ExternalTun {
    /** Intent extra (CaptureService): ParcelFileDescriptor of the tun interface owned by the host */
    public static final String EXTRA_TUN_FD = "ext_tun_fd";
    /** Intent extra (CaptureService): IPv4 address assigned to the host tun interface */
    public static final String EXTRA_VPN_IPV4 = "ext_vpn_ipv4";
    /** Intent extra (CaptureService): IPv4 DNS server configured on the host tun interface */
    public static final String EXTRA_VPN_DNS = "ext_vpn_dns";
    /** Intent extra (CaptureService): MTU of the host tun interface */
    public static final String EXTRA_MTU = "ext_mtu";

    public interface Provider {
        /** @return true if the host owns the VPN, so PCAPdroid must not create its own */
        boolean isAvailable();

        /**
         * Asks the host to start routing its tun through PCAPdroid. The request is asynchronous:
         * the capture is started by the host via {@link CaptureService} with the EXTRA_* extras.
         *
         * @return false if the analysis cannot be started right now (e.g. the VPN is not running)
         */
        boolean startAnalysis(@NonNull Context ctx, @NonNull CaptureSettings settings);

        /**
         * Invoked when a capture running on the host tun terminates, for whatever reason (user
         * stop, error, aborted start). The host must give the tun back to its own packet engine.
         */
        void onAnalysisEnded(@NonNull Context ctx);
    }

    private static volatile Provider sProvider;

    private ExternalTun() {}

    public static void setProvider(@Nullable Provider provider) {
        sProvider = provider;
    }

    public static @Nullable Provider getProvider() {
        return sProvider;
    }

    /** @return true if a host app registered itself as the owner of the VPN */
    public static boolean isHostVpn() {
        Provider p = sProvider;
        return (p != null) && p.isAvailable();
    }
}
