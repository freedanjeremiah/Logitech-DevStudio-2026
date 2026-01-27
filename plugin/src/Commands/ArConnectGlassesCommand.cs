namespace Loupedeck
{
    using System;

    /// <summary>
    /// AR Connect — displays glasses connection status and lets users
    /// manually trigger a re-scan for AR glasses on the local network.
    ///
    /// This command also acts as the Actions Ring status indicator:
    /// the button image reflects whether glasses are connected or searching.
    /// </summary>
    public class ArConnectGlassesCommand : PluginDynamicCommand
    {
        public ArConnectGlassesCommand()
            : base(
                displayName:  "AR Connect",
                description:  "Show AR glasses connection status. Press to re-scan for glasses.",
                groupName:    "AR Controls")
        {
        }

        protected override void RunCommand(String actionParameter)
        {
            var plugin = MxSpatialBridgePlugin.Instance;
            if (plugin == null) return;

            if (plugin.SyncClient.IsConnected)
            {
                // Already connected — re-sync the active profile as a "health check"
                _ = plugin.SyncClient.SyncProfileAsync(plugin.ProfileManager.ActiveProfile);
            }
            else
            {
                // Start a fresh mDNS discovery scan
                _ = plugin.Discovery.StartAsync();
            }

            // Refresh our own button image
            this.CommandImageChanged(actionParameter);
        }

        public override String GetCommandDisplayName(String actionParameter, PluginImageSize imageSize)
        {
            var connected = MxSpatialBridgePlugin.Instance?.SyncClient.IsConnected ?? false;
            return connected ? "Glasses ●" : "Glasses ○";
        }

        protected override BitmapImage GetCommandImage(String actionParameter, PluginImageSize imageSize)
        {
            var connected = MxSpatialBridgePlugin.Instance?.SyncClient.IsConnected ?? false;

            using var b = new BitmapBuilder(imageSize);
            if (connected)
            {
                b.FillRectangle(0, 0, b.Width, b.Height, new BitmapColor(0, 180, 100));   // connected: green
                b.DrawText("●\nON", BitmapColor.White, fontSize: b.Width * 0.22f);
            }
            else
            {
                b.FillRectangle(0, 0, b.Width, b.Height, new BitmapColor(60, 60, 70));    // disconnected: grey
                b.DrawText("○\nSCAN", BitmapColor.White, fontSize: b.Width * 0.20f);
            }

            return b.ToImage();
        }
    }
}
