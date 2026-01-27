namespace Loupedeck
{
    using System;
    using System.Collections.Generic;

    /// <summary>
    /// AR Switch Layer — cycles through AR workspace layers (home, apps, settings, comms).
    ///
    /// Uses profile action parameters so users can assign specific target layers
    /// to individual keypad buttons in Options+ or the Actions Ring.
    ///
    /// Action parameter format: "next" | "prev" | "0".."3" (direct layer index)
    /// </summary>
    public class ArSwitchLayerCommand : PluginDynamicCommand
    {
        private static readonly Dictionary<String, String> LayerLabels = new()
        {
            ["next"]    = "Layer →",
            ["prev"]    = "← Layer",
            ["0"]       = "Home",
            ["1"]       = "Apps",
            ["2"]       = "Settings",
            ["3"]       = "Comms",
        };

        public ArSwitchLayerCommand()
            : base(
                displayName:  "AR Switch Layer",
                description:  "Switch to a different AR workspace layer",
                groupName:    "AR Controls")
        {
            // Register all valid layer targets so Options+ shows them in the picker.
            this.AddParameter("next",  "Next layer",           "AR Controls");
            this.AddParameter("prev",  "Previous layer",       "AR Controls");
            this.AddParameter("0",     "Layer 0 — Home",       "AR Controls");
            this.AddParameter("1",     "Layer 1 — Apps",       "AR Controls");
            this.AddParameter("2",     "Layer 2 — Settings",   "AR Controls");
            this.AddParameter("3",     "Layer 3 — Comms",      "AR Controls");
        }

        protected override void RunCommand(String actionParameter)
        {
            var param = String.IsNullOrEmpty(actionParameter) ? "next" : actionParameter;

            MxSpatialBridgePlugin.Instance?.SendArAction(
                "switch-layer",
                new Dictionary<String, Object> { ["target"] = param });
        }

        public override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
            => LayerLabels.TryGetValue(actionParameter ?? "next", out var label) ? label : "AR Layer";

        protected override BitmapImage GetCommandImage(String actionParameter, PluginImageSize imageSize)
        {
            using var b = new BitmapBuilder(imageSize);
            b.FillRectangle(0, 0, b.Width, b.Height, new BitmapColor(24, 140, 80));   // green
            var label = LayerLabels.TryGetValue(actionParameter ?? "next", out var l) ? l : "Layer";
            b.DrawText(label, BitmapColor.White, fontSize: b.Width * 0.20f);
            return b.ToImage();
        }
    }
}
