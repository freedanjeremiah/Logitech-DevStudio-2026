namespace Loupedeck
{
    using System;
    using System.Collections.Generic;
    using System.Text.Json;
    using System.Text.Json.Serialization;

    /// <summary>
    /// A SpatialProfile defines how every MX peripheral event maps to an AR action.
    /// Profiles are JSON files stored in the plugin's data directory and synced to
    /// the glasses over WebSocket.
    ///
    /// The same JSON schema is used on both ends (desktop plugin and Android app)
    /// so the glasses can operate standalone once a profile has been received.
    /// </summary>
    public class SpatialProfile
    {
        [JsonPropertyName("name")]
        public String Name { get; set; } = "Default AR Profile";

        [JsonPropertyName("version")]
        public String Version { get; set; } = "1.0";

        [JsonPropertyName("scrollSensitivity")]
        public Double ScrollSensitivity { get; set; } = 1.0;

        [JsonPropertyName("zoomStep")]
        public Int32 ZoomStep { get; set; } = 5;

        [JsonPropertyName("defaultBrightness")]
        public Int32 DefaultBrightness { get; set; } = 70;

        /// <summary>
        /// Maps raw MX event IDs to AR action descriptors.
        ///
        /// Keys follow the pattern used by HidEventParser on Android:
        ///   "dial-cw"         — dial rotated clockwise (one detent)
        ///   "dial-ccw"        — dial rotated anti-clockwise
        ///   "dial-press"      — dial button pressed
        ///   "btn-N"           — keypad button N pressed (0-indexed)
        ///   "thumb-wheel-up"  — MX Master 4 thumb wheel scrolled up
        ///   "thumb-wheel-dn"  — thumb wheel scrolled down
        ///   "gesture-left"    — MX Master 4 gesture button swiped left
        ///   "gesture-right"   — gesture button swiped right
        /// </summary>
        [JsonPropertyName("mappings")]
        public Dictionary<String, ActionDescriptor> Mappings { get; set; } = new()
        {
            ["dial-cw"]         = new ActionDescriptor { Action = "scroll", Parameters = new() { ["delta"] = 1, ["axis"] = "vertical" } },
            ["dial-ccw"]        = new ActionDescriptor { Action = "scroll", Parameters = new() { ["delta"] = -1, ["axis"] = "vertical" } },
            ["dial-press"]      = new ActionDescriptor { Action = "select" },
            ["btn-0"]           = new ActionDescriptor { Action = "back" },
            ["btn-1"]           = new ActionDescriptor { Action = "switch-layer", Parameters = new() { ["target"] = "next" } },
            ["btn-2"]           = new ActionDescriptor { Action = "launch-app",   Parameters = new() { ["appId"] = "browser" } },
            ["btn-3"]           = new ActionDescriptor { Action = "launch-app",   Parameters = new() { ["appId"] = "maps" } },
            ["thumb-wheel-up"]  = new ActionDescriptor { Action = "workspace-next" },
            ["thumb-wheel-dn"]  = new ActionDescriptor { Action = "workspace-prev" },
            ["gesture-left"]    = new ActionDescriptor { Action = "scroll", Parameters = new() { ["delta"] = -3, ["axis"] = "horizontal" } },
            ["gesture-right"]   = new ActionDescriptor { Action = "scroll", Parameters = new() { ["delta"] =  3, ["axis"] = "horizontal" } },
        };

        // ── Serialisation helpers ─────────────────────────────────────────────

        private static readonly JsonSerializerOptions _jsonOpts = new()
        {
            WriteIndented = true,
            PropertyNameCaseInsensitive = true,
        };

        public String ToJson() => JsonSerializer.Serialize(this, _jsonOpts);

        public static SpatialProfile FromJson(String json)
            => JsonSerializer.Deserialize<SpatialProfile>(json, _jsonOpts)
               ?? new SpatialProfile();
    }

    /// <summary>
    /// Pairs an AR action name with optional parameters.
    /// e.g. { "action": "scroll", "parameters": { "delta": 1, "axis": "vertical" } }
    /// </summary>
    public class ActionDescriptor
    {
        [JsonPropertyName("action")]
        public String Action { get; set; } = "";

        [JsonPropertyName("parameters")]
        public Dictionary<String, Object>? Parameters { get; set; }
    }
}
