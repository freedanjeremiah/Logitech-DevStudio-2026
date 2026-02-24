package com.logitech.mxspatialbridge.sync;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.logitech.mxspatialbridge.MxSpatialBridgeApp;
import com.logitech.mxspatialbridge.mapper.InputMapperEngine;
import com.logitech.mxspatialbridge.ui.ControlMappingActivity;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * ProfileSyncServer — WebSocket server that listens on port 58432 for
 * incoming messages from the MX SpatialBridge desktop plugin.
 *
 * <h3>Supported message types (JSON)</h3>
 * <pre>
 *   { "type": "ping" }
 *   { "type": "action",         "action": "...", "params": {...} }
 *   { "type": "profile-update", "profile": {...SpatialProfile...} }
 * </pre>
 *
 * On receiving a {@code profile-update}:
 * 1. Deserialise the {@link InputMapperEngine.SpatialProfile}
 * 2. Call {@link InputMapperEngine#loadProfile} — zero-downtime hot-reload
 * 3. Reply with {@code { "type": "ack", "ref": "profile-update" }}
 *
 * On receiving an {@code action}: forward directly to
 * {@link InputMapperEngine#dispatch} (allows the desktop to drive AR
 * without waiting for a button press on the glasses).
 *
 * <h3>mDNS advertisement</h3>
 * {@link NsdAdvertiser} announces {@code _mxspatialbridge._tcp} on the
 * local network so the desktop plugin can discover glasses automatically.
 */
public class ProfileSyncServer extends Service {

    public static final int    WS_PORT        = 58432;
    private static final int   NOTIFICATION_ID = 1002;

    private GlassesWebSocketServer mWsServer;
    private NsdAdvertiser          mNsdAdvertiser;
    private final Gson             mGson = new GsonBuilder().create();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        // Start WebSocket server
        mWsServer = new GlassesWebSocketServer(WS_PORT);
        mWsServer.start();

        // Advertise on mDNS so the desktop plugin can auto-discover us
        mNsdAdvertiser = new NsdAdvertiser(this, WS_PORT);
        mNsdAdvertiser.start();

        android.util.Log.i("MxSpatialBridge", "ProfileSyncServer listening on :" + WS_PORT);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mWsServer != null) mWsServer.stop();
        } catch (InterruptedException ignored) {}
        if (mNsdAdvertiser != null) mNsdAdvertiser.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── WebSocket server ──────────────────────────────────────────────────────

    private class GlassesWebSocketServer extends WebSocketServer {

        GlassesWebSocketServer(int port) {
            super(new InetSocketAddress(port));
            setReuseAddr(true);
            setConnectionLostTimeout(30);   // detect dead connections within 30 s
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            android.util.Log.i("MxSpatialBridge", "Plugin connected: " + conn.getRemoteSocketAddress());

            // Immediately send current status so plugin knows glasses are alive (battery from device)
            InputMapperEngine.SpatialProfile profile =
                    MxSpatialBridgeApp.getInstance().getMapperEngine().getActiveProfile();
            int batteryPct = getBatteryLevel();
            Map<String, Object> statusMsg = new HashMap<>();
            statusMsg.put("type",    "status");
            statusMsg.put("battery", batteryPct);
            statusMsg.put("layer",   0);
            statusMsg.put("app",     "home");
            conn.send(mGson.toJson(statusMsg));
        }

        /** Read current battery level (0–100) from the device for status messages. */
        private int getBatteryLevel() {
            Intent batteryIntent = ProfileSyncServer.this.getApplicationContext().registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent == null) return 100;
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level < 0 || scale <= 0) return 100;
            return (level * 100) / scale;
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            android.util.Log.i("MxSpatialBridge", "Plugin disconnected: " + reason);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            handleMessage(conn, message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            android.util.Log.e("MxSpatialBridge", "WebSocket error: " + ex.getMessage());
        }

        @Override
        public void onStart() {
            android.util.Log.i("MxSpatialBridge", "WebSocket server started");
        }
    }

    // ── Message handling ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleMessage(WebSocket conn, String message) {
        try {
            Map<String, Object> msg = mGson.fromJson(message, Map.class);
            String type = (String) msg.get("type");
            if (type == null) return;

            switch (type) {
                case "ping":
                    // Reply pong immediately
                    Map<String, Object> pong = new HashMap<>();
                    pong.put("type", "pong");
                    conn.send(mGson.toJson(pong));
                    break;

                case "action":
                    // Forward action directly to the dispatcher
                    String action  = (String) msg.get("action");
                    Object paramsObj = msg.get("params");
                    Map<String, Object> params = paramsObj instanceof Map
                            ? (Map<String, Object>) paramsObj
                            : new HashMap<>();
                    MxSpatialBridgeApp.getInstance()
                            .getMapperEngine()
                            .dispatch(actionToEventId(action), 0);
                    break;

                case "profile-update":
                    // Deserialise and hot-reload profile
                    Object profileObj = msg.get("profile");
                    if (profileObj != null) {
                        String profileJson = mGson.toJson(profileObj);
                        InputMapperEngine.SpatialProfile profile =
                                mGson.fromJson(profileJson, InputMapperEngine.SpatialProfile.class);
                        MxSpatialBridgeApp.getInstance().getMapperEngine().loadProfile(profile);

                        // Acknowledge
                        Map<String, Object> ack = new HashMap<>();
                        ack.put("type", "ack");
                        ack.put("ref",  "profile-update");
                        conn.send(mGson.toJson(ack));

                        android.util.Log.i("MxSpatialBridge",
                                "Profile hot-reloaded: " + profile.name);
                    }
                    break;

                default:
                    android.util.Log.w("MxSpatialBridge", "Unknown message type: " + type);
            }
        } catch (Exception e) {
            android.util.Log.e("MxSpatialBridge", "Message handling error: " + e.getMessage());
        }
    }

    /**
     * Convert a direct action name (from the desktop plugin) into an event ID
     * that the mapper engine understands.  This lets the plugin drive the glasses
     * directly without needing a physical button press.
     */
    private static String actionToEventId(String action) {
        if (action == null) return "";
        // Direct actions map through a synthetic "direct-action" event ID
        // The dispatcher handles these action names directly, so pass them through.
        return "direct:" + action;
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, ControlMappingActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MxSpatialBridgeApp.CHANNEL_ID)
                .setContentTitle("MX SpatialBridge")
                .setContentText("Sync server active — ready for desktop connection")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
