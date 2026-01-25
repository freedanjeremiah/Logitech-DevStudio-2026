namespace Loupedeck
{
    using System;
    using System.Threading.Tasks;

    /// <summary>
    /// MX SpatialBridge — Precision spatial input for AR smart glasses.
    ///
    /// This universal plugin (HasNoApplication = true) registers with Logitech
    /// Options+ and exposes Commands and Adjustments for controlling Android-based
    /// AR waveguide glasses.  It discovers glasses on the local network via mDNS,
    /// syncs input-mapping profiles over WebSocket, and surfaces dedicated AR
    /// control bubbles inside the Actions Ring overlay.
    /// </summary>
    public class MxSpatialBridgePlugin : Plugin
    {
        // Singleton so Commands and Adjustments can reach the plugin instance.
        public static MxSpatialBridgePlugin? Instance { get; private set; }

        internal ProfileManager ProfileManager { get; private set; } = null!;
        internal GlassesDiscovery Discovery { get; private set; } = null!;
        internal ProfileSyncClient SyncClient { get; private set; } = null!;

        /// <summary>
        /// Universal plugin — no desktop application needs to be in the foreground.
        /// The "application" is the remote AR glasses device.
        /// </summary>
        public override Boolean HasNoApplication => true;

        public MxSpatialBridgePlugin()
        {
            Instance = this;
        }

        public override void Load()
        {
            ProfileManager = new ProfileManager(this);
            Discovery     = new GlassesDiscovery(this);
            SyncClient    = new ProfileSyncClient(this);

            ProfileManager.Load();
            _ = Discovery.StartAsync();

            this.OnPluginStatusChanged(
                PluginStatus.Warning,
                "Searching for AR glasses on local network…",
                "https://github.com/mx-spatialbridge/mx-spatialbridge#connection");
        }

        public override void Unload()
        {
            SyncClient?.Dispose();
            Discovery?.Dispose();
        }

        // ── Called by GlassesDiscovery when mDNS finds an _mxspatialbridge._tcp service ──

        internal void OnGlassesDiscovered(String address, Int32 port)
        {
            _ = SyncClient.ConnectAsync(address, port);
        }

        // ── Called by ProfileSyncClient on connection state changes ──

        internal void OnGlassesConnected()
        {
            this.OnPluginStatusChanged(PluginStatus.Ok, "AR glasses connected", "");

            // Push the active mapping profile immediately so the glasses are ready.
            _ = SyncClient.SyncProfileAsync(ProfileManager.ActiveProfile);

            // Notify every adjustment to refresh its displayed value.
            this.CommandImageChanged("ArConnectGlasses");
        }

        internal void OnGlassesDisconnected()
        {
            this.OnPluginStatusChanged(
                PluginStatus.Warning,
                "AR glasses disconnected — reconnecting…",
                "");

            _ = Discovery.StartAsync();
        }

        // ── Convenience: send an action to the glasses (used by commands) ──

        internal void SendArAction(String action, System.Collections.Generic.Dictionary<String, Object>? parameters = null)
        {
            if (!SyncClient.IsConnected)
            {
                this.OnPluginStatusChanged(
                    PluginStatus.Warning,
                    $"Cannot send '{action}' — glasses not connected",
                    "");
                return;
            }

            _ = SyncClient.SendActionAsync(action, parameters);
        }
    }
}
