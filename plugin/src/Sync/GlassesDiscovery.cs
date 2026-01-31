namespace Loupedeck
{
    using System;
    using System.Threading;
    using System.Threading.Tasks;

    using Makaretu.Dns;

    /// <summary>
    /// GlassesDiscovery uses mDNS/Bonjour to find AR glasses running the
    /// MX SpatialBridge Android service on the local network.
    ///
    /// The glasses advertise themselves as:
    ///   _mxspatialbridge._tcp.local.  (port 58432)
    ///
    /// When a matching service is found the plugin is notified via
    /// MxSpatialBridgePlugin.OnGlassesDiscovered(address, port).
    /// </summary>
    public class GlassesDiscovery : IDisposable
    {
        public const String ServiceType = "_mxspatialbridge._tcp";
        public const Int32  DefaultPort = 58432;

        private readonly MxSpatialBridgePlugin _plugin;
        private ServiceDiscovery? _sd;
        private CancellationTokenSource? _cts;
        private Boolean _disposed;

        public GlassesDiscovery(MxSpatialBridgePlugin plugin)
        {
            _plugin = plugin;
        }

        /// <summary>
        /// Begin advertising our plugin on mDNS (so glasses can find us too)
        /// and simultaneously browse for glasses advertising their own service.
        /// </summary>
        public async Task StartAsync()
        {
            _cts?.Cancel();
            _cts = new CancellationTokenSource();

            try
            {
                _sd?.Dispose();
                _sd = new ServiceDiscovery();

                // ── Browse for glasses ────────────────────────────────────────
                _sd.ServiceDiscovered += OnServiceTypeFound;
                _sd.ServiceInstanceDiscovered += OnInstanceDiscovered;
                _sd.QueryServiceInstances(ServiceType);

                // ── Advertise our plugin so glasses can initiate connection ──
                var profile = new ServiceProfile(
                    instanceName: "MX SpatialBridge Plugin",
                    serviceType:  ServiceType,
                    port:         DefaultPort);
                profile.AddProperty("version", "1.0");
                _sd.Advertise(profile);

                Console.WriteLine("[MxSpatialBridge] mDNS discovery started");
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[MxSpatialBridge] mDNS discovery failed: {ex.Message}");
            }

            await Task.CompletedTask;
        }

        private void OnServiceTypeFound(Object? sender, DomainName serviceName)
        {
            // Triggered when the service type itself is announced — nothing to do here,
            // instance discovery is handled by OnInstanceDiscovered.
        }

        private void OnInstanceDiscovered(Object? sender, ServiceInstanceDiscoveryEventArgs e)
        {
            if (_disposed) return;

            // Only process our specific service type
            if (!e.ServiceInstanceName.ToString().Contains(ServiceType, StringComparison.OrdinalIgnoreCase))
                return;

            // Ignore our own advertisement
            if (e.ServiceInstanceName.ToString().Contains("Plugin", StringComparison.OrdinalIgnoreCase))
                return;

            // Extract the first A record address
            foreach (var record in e.Message.AdditionalRecords)
            {
                if (record is ARecord aRecord)
                {
                    var address = aRecord.Address.ToString();
                    var port    = DefaultPort;   // SRV record parsing could refine this

                    Console.WriteLine($"[MxSpatialBridge] Found AR glasses at {address}:{port}");
                    _plugin.OnGlassesDiscovered(address, port);
                    return;
                }
            }
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            _cts?.Cancel();
            _sd?.Dispose();
        }
    }
}
