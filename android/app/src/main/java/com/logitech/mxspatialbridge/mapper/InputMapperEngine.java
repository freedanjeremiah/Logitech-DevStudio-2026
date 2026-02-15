package com.logitech.mxspatialbridge.mapper;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * InputMapperEngine translates canonical HID event IDs (produced by
 * {@link com.logitech.mxspatialbridge.hid.HidEventParser}) into AR actions.
 *
 * <h3>Profile format (JSON)</h3>
 * <pre>
 * {
 *   "name": "Default AR Profile",
 *   "version": "1.0",
 *   "scrollSensitivity": 1.0,
 *   "zoomStep": 5,
 *   "defaultBrightness": 70,
 *   "mappings": {
 *     "dial-cw":  { "action": "scroll", "parameters": { "delta": 1, "axis": "vertical" } },
 *     "dial-ccw": { "action": "scroll", "parameters": { "delta": -1, "axis": "vertical" } },
 *     "btn-0":    { "action": "back" },
 *     ...
 *   }
 * }
 * </pre>
 *
 * Profiles can be hot-reloaded at runtime by calling {@link #loadProfile(SpatialProfile)};
 * no restart is required.  This is how WiFi sync works: the WebSocket server calls
 * {@code loadProfile} whenever the desktop plugin pushes a new mapping.
 */
public class InputMapperEngine {

    private final Context mContext;
    private final ArActionDispatcher mDispatcher;
    private final Gson mGson;

    private volatile SpatialProfile mActiveProfile = new SpatialProfile();

    // Raw sensitivity multiplier applied to dial ticks before dispatching scroll actions.
    private volatile double mSensitivity = 1.0;

    public InputMapperEngine(Context context) {
        mContext    = context.getApplicationContext();
        mDispatcher = new ArActionDispatcher(mContext);
        mGson       = new GsonBuilder().setPrettyPrinting().create();
    }

    // ── Profile loading ───────────────────────────────────────────────────────

    /**
     * Load the default profile bundled in assets.  Falls back to a hard-coded
     * default if the asset is missing.
     */
    public void loadDefaultProfile() {
        try (InputStream is = mContext.getAssets().open("default-spatial-profile.json");
             InputStreamReader reader = new InputStreamReader(is)) {
            SpatialProfile profile = mGson.fromJson(reader, SpatialProfile.class);
            loadProfile(profile);
        } catch (IOException e) {
            // Asset missing — use built-in defaults
            loadProfile(SpatialProfile.buildDefault());
        }
    }

    /**
     * Hot-reload a new profile.  Called by the WebSocket sync server when
     * the desktop plugin pushes a profile-update message.
     *
     * Thread-safe: profile is replaced atomically.
     */
    public void loadProfile(SpatialProfile profile) {
        if (profile == null) return;
        mActiveProfile = profile;
        mSensitivity   = profile.scrollSensitivity;

        // Persist to disk so the profile survives reboots
        saveProfileToDisk(profile);
    }

    public SpatialProfile getActiveProfile() {
        return mActiveProfile;
    }

    // ── Event dispatch ────────────────────────────────────────────────────────

    /**
     * Look up the mapping for {@code eventId} and dispatch the resulting
     * AR action, scaling {@code rawDelta} by the current sensitivity.
     *
     * @param eventId   canonical event ID (e.g. "dial-cw", "btn-0")
     * @param rawDelta  signed integer delta for rotary events; 0 for button presses
     */
    public void dispatch(String eventId, int rawDelta) {
        if (eventId == null) return;

        SpatialProfile profile = mActiveProfile;
        ActionDescriptor descriptor = profile.mappings.get(eventId);

        if (descriptor == null) return;   // unmapped event

        // Apply sensitivity scaling for scroll/zoom events
        int scaledDelta = (int) Math.round(rawDelta * mSensitivity);

        // Merge delta into parameters if the action uses one
        Map<String, Object> params = new HashMap<>();
        if (descriptor.parameters != null) {
            params.putAll(descriptor.parameters);
        }
        if (rawDelta != 0) {
            params.put("delta", scaledDelta);
        }

        mDispatcher.dispatch(descriptor.action, params);
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    private void saveProfileToDisk(SpatialProfile profile) {
        File profileDir = new File(mContext.getFilesDir(), "profiles");
        profileDir.mkdirs();
        File file = new File(profileDir, sanitiseName(profile.name) + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            mGson.toJson(profile, writer);
        } catch (IOException e) {
            android.util.Log.e("MxSpatialBridge", "Failed to save profile: " + e.getMessage());
        }
    }

    private static String sanitiseName(String name) {
        return name == null ? "profile" : name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    // ── Inner models ──────────────────────────────────────────────────────────

    /**
     * Java representation of the SpatialProfile JSON schema.
     * Shared with the desktop plugin (same field names).
     */
    public static class SpatialProfile {
        public String name              = "Default AR Profile";
        public String version           = "1.0";
        public double scrollSensitivity = 1.0;
        public int    zoomStep          = 5;
        public int    defaultBrightness = 70;

        public Map<String, ActionDescriptor> mappings = new HashMap<>();

        public static SpatialProfile buildDefault() {
            SpatialProfile p = new SpatialProfile();
            p.mappings.put("dial-cw",        new ActionDescriptor("scroll",       mapOf("delta", 1,  "axis", "vertical")));
            p.mappings.put("dial-ccw",       new ActionDescriptor("scroll",       mapOf("delta", -1, "axis", "vertical")));
            p.mappings.put("dial-press",     new ActionDescriptor("select",       null));
            p.mappings.put("btn-0",          new ActionDescriptor("back",         null));
            p.mappings.put("btn-1",          new ActionDescriptor("switch-layer", mapOf("target", "next")));
            p.mappings.put("btn-2",          new ActionDescriptor("launch-app",   mapOf("appId", "browser")));
            p.mappings.put("btn-3",          new ActionDescriptor("launch-app",   mapOf("appId", "maps")));
            p.mappings.put("thumb-wheel-up", new ActionDescriptor("workspace-next", null));
            p.mappings.put("thumb-wheel-dn", new ActionDescriptor("workspace-prev", null));
            p.mappings.put("gesture-left",   new ActionDescriptor("scroll",       mapOf("delta", -3, "axis", "horizontal")));
            p.mappings.put("gesture-right",  new ActionDescriptor("scroll",       mapOf("delta",  3, "axis", "horizontal")));
            return p;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> mapOf(Object... pairs) {
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i + 1 < pairs.length; i += 2)
                m.put((String) pairs[i], pairs[i + 1]);
            return m;
        }
    }

    public static class ActionDescriptor {
        public String action;
        public Map<String, Object> parameters;

        public ActionDescriptor() {}

        public ActionDescriptor(String action, Map<String, Object> parameters) {
            this.action     = action;
            this.parameters = parameters;
        }
    }
}
