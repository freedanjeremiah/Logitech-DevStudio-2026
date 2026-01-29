namespace Loupedeck
{
    using System;
    using System.Collections.Generic;

    /// <summary>
    /// AR Scroll — maps MX Dial (or MX Master 4 thumb wheel) rotation to
    /// vertical scroll in the currently focused AR panel.
    ///
    /// • Clockwise   (+ticks) → scroll down  (content moves up)
    /// • Anti-clock  (−ticks) → scroll up    (content moves down)
    ///
    /// The "reset" button (dial press-and-hold in SDK terms) snaps the AR
    /// interface back to the top of the current list/panel.
    ///
    /// Sensitivity is configurable per-profile (default: 1 tick = 1 scroll unit).
    /// </summary>
    public class ArScrollAdjustment : PluginDynamicAdjustment
    {
        // Accumulated scroll position per parameter so the dial ring display
        // can show a meaningful value, even though we stream each tick live.
        private readonly Dictionary<String, Int32> _position = new();

        public ArScrollAdjustment()
            : base(
                displayName:        "AR Scroll",
                description:        "Scroll the focused AR panel using the dial",
                groupName:          "AR Controls",
                hasReset:           true)          // adds a "press" / reset button
        {
            // "default" is the only parameter — single scroll context.
            // Extend here for multi-panel scroll if needed.
            this.AddParameter("default", "AR Scroll", "AR Controls");
        }

        protected override void ApplyAdjustment(String actionParameter, Int32 ticks)
        {
            var key = actionParameter ?? "default";
            _position.TryGetValue(key, out var pos);
            _position[key] = pos + ticks;

            // Stream every tick directly — AR needs sub-20 ms response.
            var plugin = MxSpatialBridgePlugin.Instance;
            if (plugin?.SyncClient.IsConnected == true)
            {
                _ = plugin.SyncClient.SendActionAsync(
                    "scroll",
                    new Dictionary<String, Object>
                    {
                        ["delta"]       = ticks,
                        ["axis"]        = "vertical",
                        ["sensitivity"] = plugin.ProfileManager.ActiveProfile.ScrollSensitivity,
                    });
            }

            this.AdjustmentValueChanged(key);
        }

        // Reset button: jump to top of current AR list/panel
        protected override void RunCommand(String actionParameter)
        {
            var key = actionParameter ?? "default";
            _position[key] = 0;

            MxSpatialBridgePlugin.Instance?.SendArAction(
                "scroll-reset",
                new Dictionary<String, Object> { ["axis"] = "vertical" });

            this.AdjustmentValueChanged(key);
        }

        protected override String GetAdjustmentValue(String actionParameter)
        {
            _position.TryGetValue(actionParameter ?? "default", out var pos);
            return pos.ToString();
        }

        protected override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
            => "Scroll ↑";
    }
}
