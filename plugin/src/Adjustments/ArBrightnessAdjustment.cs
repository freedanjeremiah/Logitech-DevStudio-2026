namespace Loupedeck
{
    using System;
    using System.Collections.Generic;

    /// <summary>
    /// AR Brightness — adjusts the waveguide display brightness on the AR glasses.
    ///
    /// Brightness is tracked as 0–100 %. Each dial tick changes brightness by 2 %.
    /// Reset button returns to the profile default (typically 70 %).
    ///
    /// This is especially useful for quickly adapting to different lighting
    /// environments without reaching for the glasses themselves.
    /// </summary>
    public class ArBrightnessAdjustment : PluginDynamicAdjustment
    {
        private const Int32 DefaultBrightness = 70;
        private const Int32 TickStep          = 2;

        private readonly Dictionary<String, Int32> _brightness = new();

        public ArBrightnessAdjustment()
            : base(
                displayName:  "AR Brightness",
                description:  "Adjust AR glasses display brightness using the dial",
                groupName:    "AR Controls",
                hasReset:     true)
        {
            this.AddParameter("default", "AR Brightness", "AR Controls");
        }

        protected override void ApplyAdjustment(String actionParameter, Int32 ticks)
        {
            var key = actionParameter ?? "default";
            if (!_brightness.TryGetValue(key, out var current))
                current = DefaultBrightness;

            var next = Math.Clamp(current + ticks * TickStep, 0, 100);
            _brightness[key] = next;

            MxSpatialBridgePlugin.Instance?.SyncClient.SendActionAsync(
                "set-brightness",
                new Dictionary<String, Object> { ["percent"] = next });

            this.AdjustmentValueChanged(key);
        }

        // Reset → return to profile default brightness
        protected override void RunCommand(String actionParameter)
        {
            var key = actionParameter ?? "default";
            var defaultBrightness =
                MxSpatialBridgePlugin.Instance?.ProfileManager.ActiveProfile.DefaultBrightness
                ?? DefaultBrightness;

            _brightness[key] = defaultBrightness;

            MxSpatialBridgePlugin.Instance?.SendArAction(
                "set-brightness",
                new Dictionary<String, Object> { ["percent"] = defaultBrightness });

            this.AdjustmentValueChanged(key);
        }

        protected override String GetAdjustmentValue(String actionParameter)
        {
            _brightness.TryGetValue(actionParameter ?? "default", out var pct);
            return $"{(pct == 0 ? DefaultBrightness : pct)} %";
        }

        protected override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
            => "Default ☀";
    }
}
