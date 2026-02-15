package com.logitech.mxspatialbridge.mapper;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.util.Map;

/**
 * ArActionDispatcher translates logical AR action names into actual Android
 * system calls that affect the AR OS running on the glasses.
 *
 * <h3>Action → implementation mapping</h3>
 * <pre>
 *   select          → KeyEvent KEYCODE_DPAD_CENTER (or KEYCODE_ENTER)
 *   back            → KeyEvent KEYCODE_BACK
 *   switch-layer    → broadcast com.logitech.mxspatialbridge.SWITCH_LAYER
 *   launch-app      → startActivity via package name / app-id alias table
 *   scroll          → AccessibilityService scroll  (or AXIS_VSCROLL injection)
 *   zoom            → broadcast com.logitech.mxspatialbridge.ZOOM
 *   set-brightness  → Settings.System SCREEN_BRIGHTNESS
 *   workspace-next  → broadcast com.logitech.mxspatialbridge.WORKSPACE
 *   workspace-prev  → broadcast com.logitech.mxspatialbridge.WORKSPACE
 * </pre>
 *
 * All dispatches happen on the calling thread.  The HID service already runs
 * on a background thread, so this is safe.
 */
public class ArActionDispatcher {

    // Broadcasts consumed by the AR shell (waveguide display manager layer)
    public static final String ACTION_SWITCH_LAYER = "com.logitech.mxspatialbridge.SWITCH_LAYER";
    public static final String ACTION_ZOOM         = "com.logitech.mxspatialbridge.ZOOM";
    public static final String ACTION_WORKSPACE    = "com.logitech.mxspatialbridge.WORKSPACE";
    public static final String ACTION_BRIGHTNESS   = "com.logitech.mxspatialbridge.SET_BRIGHTNESS";

    // Known app-ID aliases → Android package names
    private static final Map<String, String> APP_ALIASES = Map.of(
            "browser",  "com.android.chrome",
            "maps",     "com.google.android.apps.maps",
            "camera",   "android.hardware.camera2",
            "gallery",  "com.android.gallery3d",
            "notes",    "com.google.android.keep"
    );

    private final Context mContext;

    public ArActionDispatcher(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Dispatch an AR action with optional parameters.
     *
     * @param action  canonical action name
     * @param params  optional key-value parameters (may be null or empty)
     */
    public void dispatch(String action, Map<String, Object> params) {
        if (action == null) return;

        switch (action) {
            case "select":
                injectKey(KeyEvent.KEYCODE_DPAD_CENTER);
                break;

            case "back":
                injectKey(KeyEvent.KEYCODE_BACK);
                break;

            case "scroll":
                handleScroll(params);
                break;

            case "scroll-reset":
                broadcastAction(ACTION_WORKSPACE, "direction", "scroll-reset");
                break;

            case "zoom":
                handleZoom(params);
                break;

            case "zoom-reset":
                broadcastWithInt(ACTION_ZOOM, "percent", 100);
                break;

            case "set-brightness":
                handleBrightness(params);
                break;

            case "switch-layer":
                String target = getStringParam(params, "target", "next");
                broadcastAction(ACTION_SWITCH_LAYER, "target", target);
                break;

            case "launch-app":
                launchApp(getStringParam(params, "appId", "browser"));
                break;

            case "workspace-next":
                broadcastAction(ACTION_WORKSPACE, "direction", "next");
                break;

            case "workspace-prev":
                broadcastAction(ACTION_WORKSPACE, "direction", "prev");
                break;

            default:
                android.util.Log.w("MxSpatialBridge", "Unknown AR action: " + action);
        }
    }

    // ── Specific handlers ─────────────────────────────────────────────────────

    private void handleScroll(Map<String, Object> params) {
        int delta  = getIntParam(params, "delta", 0);
        String axis = getStringParam(params, "axis", "vertical");

        // Translate scroll delta into repeated D-pad events
        // (most AR UIs respond to DPAD_UP/DOWN for list navigation)
        if ("vertical".equals(axis)) {
            int keyCode = delta > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
            int ticks   = Math.abs(delta);
            for (int i = 0; i < ticks; i++) injectKey(keyCode);
        } else {
            int keyCode = delta > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
            int ticks   = Math.abs(delta);
            for (int i = 0; i < ticks; i++) injectKey(keyCode);
        }
    }

    private void handleZoom(Map<String, Object> params) {
        int percent = getIntParam(params, "percent", 100);
        broadcastWithInt(ACTION_ZOOM, "percent", percent);
    }

    private void handleBrightness(Map<String, Object> params) {
        int percent = getIntParam(params, "percent", 70);
        // Convert 0-100% to 0-255 range used by Settings.System
        int brightness = (int) (percent / 100.0 * 255);
        try {
            android.provider.Settings.System.putInt(
                    mContext.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(0, Math.min(255, brightness)));
        } catch (SecurityException e) {
            // Requires WRITE_SETTINGS permission — send broadcast as fallback
            broadcastWithInt(ACTION_BRIGHTNESS, "percent", percent);
        }
    }

    private void launchApp(String appId) {
        // Resolve alias to package name
        String packageName = APP_ALIASES.getOrDefault(appId, appId);
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(launchIntent);
        } else {
            android.util.Log.w("MxSpatialBridge", "Cannot launch app: " + appId);
        }
    }

    // ── Key injection ─────────────────────────────────────────────────────────

    private void injectKey(int keyCode) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);

        // Dispatch through InputManager (requires INJECT_EVENTS — available on AOSP glasses builds)
        android.hardware.input.InputManager im =
                (android.hardware.input.InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
        if (im != null) {
            im.injectInputEvent(down, android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            im.injectInputEvent(up,   android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    private void broadcastAction(String intentAction, String key, String value) {
        Intent intent = new Intent(intentAction);
        intent.putExtra(key, value);
        intent.setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

    private void broadcastWithInt(String intentAction, String key, int value) {
        Intent intent = new Intent(intentAction);
        intent.putExtra(key, value);
        intent.setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

    // ── Parameter extraction helpers ──────────────────────────────────────────

    private static int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        if (params == null) return defaultVal;
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return defaultVal;
    }

    private static String getStringParam(Map<String, Object> params, String key, String defaultVal) {
        if (params == null) return defaultVal;
        Object v = params.get(key);
        return v != null ? v.toString() : defaultVal;
    }
}
