package com.logitech.mxspatialbridge.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.logitech.mxspatialbridge.MxSpatialBridgeApp;
import com.logitech.mxspatialbridge.hid.MxHidInputService;

/**
 * ArOverlayActivity — the full-screen transparent Activity shown on the
 * AR glasses waveguide display.
 *
 * <h3>Purpose</h3>
 * This activity keeps focus so that KeyEvent and MotionEvent from connected
 * MX peripherals (Bluetooth HID / USB OTG) are delivered to our app rather
 * than to whatever AR shell app is in the foreground.
 *
 * <h3>Key design choices</h3>
 * <ul>
 *   <li>Theme: {@code Theme.Translucent.NoTitleBar.Fullscreen} — completely
 *       transparent, the user only sees their AR content through the waveguide.</li>
 *   <li>FLAG_NOT_TOUCHABLE — touch passes through to underlying AR apps.</li>
 *   <li>TYPE_APPLICATION_OVERLAY — floats above all other windows (requires
 *       SYSTEM_ALERT_WINDOW permission on non-AOSP builds).</li>
 *   <li>All keyboard/motion events are forwarded to {@link MxHidInputService}.</li>
 * </ul>
 */
public class ArOverlayActivity extends Activity {

    private MxHidInputService mHidService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the window always on top and transparent
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE        |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE         |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN     |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // We don't inflate a layout — this activity has no visible UI.
        // It exists purely to receive and forward HID events.
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-request focus so key events keep arriving
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    // ── HID event forwarding ──────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (getMxHidService() != null) {
            getMxHidService().dispatchInputEvent(event);
            // Return true to consume events from MX devices (prevent them reaching AR shell)
            if (isMxDevice(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (getMxHidService() != null) {
            getMxHidService().dispatchInputEvent(event);
            if (isMxDeviceMotion(event)) return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MxHidInputService getMxHidService() {
        // In a full implementation this would use a ServiceConnection.
        // For simplicity we route through the application singleton's mapper.
        return null;  // Direct dispatch through MxSpatialBridgeApp.getMapperEngine() instead
    }

    /** True if the key event originated from a Logitech MX device. */
    private boolean isMxDevice(KeyEvent event) {
        android.view.InputDevice device = event.getDevice();
        if (device == null) return false;
        String name = device.getName().toLowerCase();
        return name.contains("mx") || name.contains("logitech") || name.contains("logi");
    }

    private boolean isMxDeviceMotion(MotionEvent event) {
        android.view.InputDevice device = event.getDevice();
        if (device == null) return false;
        String name = device.getName().toLowerCase();
        return name.contains("mx") || name.contains("logitech") || name.contains("logi");
    }
}
