package com.logitech.mxspatialbridge.sync;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

/**
 * NsdAdvertiser registers the MX SpatialBridge WebSocket service on the
 * local network using Android's built-in NSD (Network Service Discovery),
 * which is an mDNS/Bonjour implementation.
 *
 * The desktop plugin (C# / Makaretu.Dns) listens for:
 *   service type: _mxspatialbridge._tcp
 *   port:         58432
 *
 * This allows auto-discovery — the user never needs to enter an IP address.
 */
public class NsdAdvertiser {

    private static final String SERVICE_TYPE = "_mxspatialbridge._tcp.";
    private static final String SERVICE_NAME = "MX SpatialBridge Glasses";

    private final NsdManager  mNsdManager;
    private final int         mPort;
    private NsdManager.RegistrationListener mRegistrationListener;
    private boolean mRegistered = false;

    public NsdAdvertiser(Context context, int port) {
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        mPort       = port;
    }

    public void start() {
        if (mNsdManager == null) return;

        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceType(SERVICE_TYPE);
        info.setServiceName(SERVICE_NAME);
        info.setPort(mPort);

        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                android.util.Log.e("MxSpatialBridge", "mDNS registration failed: " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                android.util.Log.e("MxSpatialBridge", "mDNS unregistration failed: " + errorCode);
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                mRegistered = true;
                android.util.Log.i("MxSpatialBridge",
                        "mDNS registered: " + info.getServiceName() + " on port " + mPort);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
                mRegistered = false;
                android.util.Log.i("MxSpatialBridge", "mDNS unregistered");
            }
        };

        mNsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void stop() {
        if (mNsdManager != null && mRegistrationListener != null && mRegistered) {
            try {
                mNsdManager.unregisterService(mRegistrationListener);
            } catch (Exception ignored) {}
        }
    }
}
