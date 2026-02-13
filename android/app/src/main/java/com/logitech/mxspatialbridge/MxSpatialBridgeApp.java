package com.logitech.mxspatialbridge;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import com.logitech.mxspatialbridge.mapper.InputMapperEngine;
import com.logitech.mxspatialbridge.sync.ProfileSyncServer;

/**
 * Application singleton for MX SpatialBridge.
 *
 * Holds the shared {@link InputMapperEngine} instance (so both the HID service
 * and the sync server can access it without passing Context everywhere) and
 * starts the two persistent foreground services on boot.
 */
public class MxSpatialBridgeApp extends Application {

    public static final String CHANNEL_ID = "mx_spatialbridge_service";

    private static MxSpatialBridgeApp sInstance;
    private InputMapperEngine mMapperEngine;

    public static MxSpatialBridgeApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        createNotificationChannel();

        // Initialise the engine first — services will reference it.
        mMapperEngine = new InputMapperEngine(this);
        mMapperEngine.loadDefaultProfile();

        // Start the WebSocket profile-sync server.
        Intent syncIntent = new Intent(this, ProfileSyncServer.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(syncIntent);
        } else {
            startService(syncIntent);
        }
    }

    public InputMapperEngine getMapperEngine() {
        return mMapperEngine;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MX SpatialBridge",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Persistent service for AR glasses input");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
