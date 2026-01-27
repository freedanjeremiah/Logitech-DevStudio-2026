namespace Loupedeck
{
    using System;
    using System.Collections.Generic;

    /// <summary>
    /// AR Launch App — opens a named app on the AR glasses.
    ///
    /// Users can assign a specific app identifier to each keypad button.
    /// The app ID is passed through to the glasses, which map it to an
    /// Android package name via the active spatial profile.
    ///
    /// Pre-configured apps (user can also type a custom package name via
    /// the "text" profile action):  browser, maps, camera, gallery, notes
    /// </summary>
    public class ArLaunchAppCommand : PluginDynamicCommand
    {
        private static readonly Dictionary<String, (String Label, BitmapColor Color)> KnownApps = new()
        {
            ["browser"]  = ("Browser",  new BitmapColor(26,  115, 232)),
            ["maps"]     = ("Maps",     new BitmapColor(52,  168,  83)),
            ["camera"]   = ("Camera",   new BitmapColor(234,  67,  53)),
            ["gallery"]  = ("Gallery",  new BitmapColor(251, 188,   5)),
            ["notes"]    = ("Notes",    new BitmapColor(130,  90, 200)),
        };

        public ArLaunchAppCommand()
            : base(
                displayName:  "AR Launch App",
                description:  "Open an application on the AR glasses",
                groupName:    "AR Controls")
        {
            foreach (var (id, meta) in KnownApps)
                this.AddParameter(id, $"Open {meta.Label} on glasses", "AR Controls");

            // Let users type any Android package name (com.example.myapp)
            this.MakeProfileAction("text;AR App ID (e.g. browser, or com.example.app):");
        }

        protected override void RunCommand(String actionParameter)
        {
            var appId = String.IsNullOrWhiteSpace(actionParameter) ? "browser" : actionParameter.Trim();

            MxSpatialBridgePlugin.Instance?.SendArAction(
                "launch-app",
                new Dictionary<String, Object> { ["appId"] = appId });
        }

        public override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
            => KnownApps.TryGetValue(actionParameter ?? "", out var m) ? m.Label : actionParameter ?? "App";

        protected override BitmapImage GetCommandImage(String actionParameter, PluginImageSize imageSize)
        {
            using var b = new BitmapBuilder(imageSize);
            var color = KnownApps.TryGetValue(actionParameter ?? "", out var m)
                ? m.Color
                : new BitmapColor(80, 80, 90);

            b.FillRectangle(0, 0, b.Width, b.Height, color);
            var label = KnownApps.TryGetValue(actionParameter ?? "", out var meta) ? meta.Label : "App";
            b.DrawText(label, BitmapColor.White, fontSize: b.Width * 0.20f);
            return b.ToImage();
        }
    }
}
