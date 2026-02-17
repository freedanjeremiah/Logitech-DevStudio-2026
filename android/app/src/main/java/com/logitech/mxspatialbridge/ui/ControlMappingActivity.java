package com.logitech.mxspatialbridge.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.GsonBuilder;
import com.logitech.mxspatialbridge.MxSpatialBridgeApp;
import com.logitech.mxspatialbridge.R;
import com.logitech.mxspatialbridge.hid.MxHidInputService;
import com.logitech.mxspatialbridge.mapper.InputMapperEngine;
import com.logitech.mxspatialbridge.sync.ProfileSyncServer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

/**
 * ControlMappingActivity — the configuration UI shown on the glasses display
 * (or on a connected phone acting as a companion device).
 *
 * Shows:
 * - Current connection status (plugin connected / waiting)
 * - Active profile name and mapping summary
 * - Device IP and port for manual pairing
 * - Start/stop service controls
 */
public class ControlMappingActivity extends AppCompatActivity {

    private TextView mStatusText;
    private TextView mProfileText;
    private TextView mIpText;
    private Button   mStartStopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_mapping);

        mStatusText    = findViewById(R.id.tv_status);
        mProfileText   = findViewById(R.id.tv_profile);
        mIpText        = findViewById(R.id.tv_ip);
        mStartStopButton = findViewById(R.id.btn_start_stop);

        mStartStopButton.setOnClickListener(v -> toggleService());

        // Start the HID input service (if not already running)
        Intent hidIntent = new Intent(this, MxHidInputService.class);
        startForegroundService(hidIntent);

        refreshUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        // Profile info
        InputMapperEngine.SpatialProfile profile =
                MxSpatialBridgeApp.getInstance().getMapperEngine().getActiveProfile();

        mProfileText.setText(buildProfileSummary(profile));

        // IP address
        mIpText.setText("Connect at: " + getLocalIpAddress() + ":" + ProfileSyncServer.WS_PORT);

        // Status
        mStatusText.setText("WebSocket server running on :" + ProfileSyncServer.WS_PORT
                + "\nWaiting for MX SpatialBridge plugin…");
    }

    private String buildProfileSummary(InputMapperEngine.SpatialProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Profile: ").append(profile.name).append("\n\n");
        sb.append("Mappings:\n");
        for (Map.Entry<String, InputMapperEngine.ActionDescriptor> entry
                : profile.mappings.entrySet()) {
            sb.append("  ").append(entry.getKey())
              .append("  →  ").append(entry.getValue().action).append("\n");
        }
        sb.append("\nSensitivity: ").append(profile.scrollSensitivity)
          .append("  Brightness: ").append(profile.defaultBrightness).append("%");
        return sb.toString();
    }

    private void toggleService() {
        // Simple toggle — in a real app this would check service state
        Intent intent = new Intent(this, ProfileSyncServer.class);
        startForegroundService(intent);
        mStartStopButton.setText("Running");
    }

    private String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            return "unknown";
        }
        return "unknown";
    }
}
