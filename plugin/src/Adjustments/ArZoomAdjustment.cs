namespace Loupedeck
{
    using System;
    using System.Collections.Generic;

    /// <summary>
    /// AR Zoom — maps MX Dial rotation to zoom level in the active AR application.
    ///
    /// • Clockwise   (+ticks) → zoom in
    /// • Anti-clock  (−ticks) → zoom out
    ///
    /// Zoom is tracked as a percentage (10 %..400 %).
    /// Reset button returns to 100 % (1:1 scale).
    /// </summary>
    public class ArZoomAdjustment : PluginDynamicAdjustment
    {
        private const Int32 MinZoom   = 10;
        private const Int32 MaxZoom   = 400;
        private const Int32 StepPct   = 5;    // each tick = 5 % zoom change

        private readonly Dictionary<String, Int32> _zoomPct = new();

        public ArZoomAdjustment()
            : base(
                displayName:  "AR Zoom",
                description:  "Zoom the active AR application using the dial",
                groupName:    "AR Controls",
                hasReset:     true)
        {
            this.AddParameter("default", "AR Zoom", "AR Controls");
        }

        protected override void ApplyAdjustment(String actionParameter, Int32 ticks)
        {
            var key = actionParameter ?? "default";
            _zoomPct.TryGetValue(key, out var current);
            if (current == 0) current = 100;   // initialise at 100 %

            var next = Math.Clamp(current + ticks * StepPct, MinZoom, MaxZoom);
            _zoomPct[key] = next;

            MxSpatialBridgePlugin.Instance?.SyncClient.SendActionAsync(
                "zoom",
                new Dictionary<String, Object>
                {
                    ["percent"] = next,
                    ["delta"]   = ticks * StepPct,
                });

            this.AdjustmentValueChanged(key);
        }

        protected override void RunCommand(String actionParameter)
        {
            var key = actionParameter ?? "default";
            _zoomPct[key] = 100;

            MxSpatialBridgePlugin.Instance?.SendArAction(
                "zoom-reset",
                new Dictionary<String, Object> { ["percent"] = 100 });

            this.AdjustmentValueChanged(key);
        }

        protected override String GetAdjustmentValue(String actionParameter)
        {
            _zoomPct.TryGetValue(actionParameter ?? "default", out var pct);
            return $"{(pct == 0 ? 100 : pct)} %";
        }

        protected override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
            => "1:1 Zoom";
    }
}
