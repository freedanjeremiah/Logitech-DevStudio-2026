namespace Loupedeck
{
    using System;
    using System.Collections.Generic;
    using System.Net.WebSockets;
    using System.Text;
    using System.Text.Json;
    using System.Threading;
    using System.Threading.Tasks;

    /// <summary>
    /// ProfileSyncClient manages the WebSocket connection from the desktop plugin
    /// to the AR glasses.
    ///
    /// Protocol (JSON over WebSocket):
    ///
    ///   Plugin → Glasses
    ///   ─────────────────────────────────────────────────────────────────────
    ///   { "type": "action",         "action": "select",   "params": {} }
    ///   { "type": "profile-update", "profile": { ...SpatialProfile... } }
    ///   { "type": "ping" }
    ///
    ///   Glasses → Plugin
    ///   ─────────────────────────────────────────────────────────────────────
    ///   { "type": "status",  "battery": 85,  "layer": 1,  "app": "browser" }
    ///   { "type": "pong" }
    ///   { "type": "ack",   "ref": "profile-update" }
    ///
    /// Connection is maintained with a 5-second heartbeat ping.
    /// On disconnect, GlassesDiscovery is restarted for automatic reconnection.
    /// </summary>
    public class ProfileSyncClient : IDisposable
    {
        private readonly MxSpatialBridgePlugin _plugin;
        private ClientWebSocket? _ws;
        private CancellationTokenSource? _cts;
        private Timer? _heartbeat;
        private Boolean _disposed;

        public Boolean IsConnected
            => _ws?.State == WebSocketState.Open;

        // Last known glasses status (for Actions Ring display)
        public Int32 GlassesBattery    { get; private set; }
        public Int32 GlassesLayer      { get; private set; }
        public String GlassesActiveApp { get; private set; } = "";

        private static readonly JsonSerializerOptions _json = new()
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        };

        public ProfileSyncClient(MxSpatialBridgePlugin plugin)
        {
            _plugin = plugin;
        }

        // ── Connection ────────────────────────────────────────────────────────

        public async Task ConnectAsync(String address, Int32 port)
        {
            if (IsConnected) return;

            try
            {
                _cts?.Cancel();
                _ws?.Dispose();

                _cts = new CancellationTokenSource();
                _ws  = new ClientWebSocket();
                _ws.Options.SetRequestHeader("X-Client", "MxSpatialBridgePlugin/1.0");

                var uri = new Uri($"ws://{address}:{port}/sync");
                await _ws.ConnectAsync(uri, _cts.Token);

                Console.WriteLine($"[MxSpatialBridge] WebSocket connected to {uri}");

                _plugin.OnGlassesConnected();

                // Start receive loop and heartbeat
                _ = ReceiveLoopAsync(_cts.Token);
                StartHeartbeat();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[MxSpatialBridge] WebSocket connect failed: {ex.Message}");
                _plugin.OnGlassesDisconnected();
            }
        }

        // ── Sending ───────────────────────────────────────────────────────────

        public Task SendActionAsync(String action, Dictionary<String, Object>? parameters = null)
        {
            var msg = new Dictionary<String, Object>
            {
                ["type"]   = "action",
                ["action"] = action,
            };
            if (parameters != null)
                msg["params"] = parameters;

            return SendJsonAsync(msg);
        }

        public Task SyncProfileAsync(SpatialProfile profile)
        {
            var msg = new Dictionary<String, Object>
            {
                ["type"]    = "profile-update",
                ["profile"] = JsonSerializer.SerializeToElement(profile, _json),
            };
            return SendJsonAsync(msg);
        }

        private async Task SendJsonAsync(Dictionary<String, Object> obj)
        {
            if (!IsConnected) return;

            try
            {
                var bytes  = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(obj, _json));
                var buffer = new ArraySegment<Byte>(bytes);
                await _ws!.SendAsync(buffer, WebSocketMessageType.Text, true, _cts!.Token);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[MxSpatialBridge] Send error: {ex.Message}");
                await HandleDisconnectAsync();
            }
        }

        // ── Receiving ─────────────────────────────────────────────────────────

        private async Task ReceiveLoopAsync(CancellationToken ct)
        {
            var buffer = new Byte[4096];

            while (!ct.IsCancellationRequested && IsConnected)
            {
                try
                {
                    var result = await _ws!.ReceiveAsync(new ArraySegment<Byte>(buffer), ct);

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        await HandleDisconnectAsync();
                        return;
                    }

                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var text = Encoding.UTF8.GetString(buffer, 0, result.Count);
                        HandleMessage(text);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"[MxSpatialBridge] Receive error: {ex.Message}");
                    await HandleDisconnectAsync();
                    return;
                }
            }
        }

        private void HandleMessage(String text)
        {
            try
            {
                using var doc  = JsonDocument.Parse(text);
                var root       = doc.RootElement;
                var type       = root.GetProperty("type").GetString();

                switch (type)
                {
                    case "status":
                        GlassesBattery    = root.TryGetProperty("battery", out var bat) ? bat.GetInt32() : GlassesBattery;
                        GlassesLayer      = root.TryGetProperty("layer",   out var lay) ? lay.GetInt32() : GlassesLayer;
                        GlassesActiveApp  = root.TryGetProperty("app",     out var app) ? app.GetString() ?? "" : GlassesActiveApp;
                        // Refresh the connect-button image with latest state
                        _plugin.CommandImageChanged("ArConnectGlasses");
                        break;

                    case "pong":
                        // Heartbeat acknowledged — connection alive
                        break;

                    case "ack":
                        Console.WriteLine($"[MxSpatialBridge] ACK: {root.GetProperty("ref").GetString()}");
                        break;
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[MxSpatialBridge] Message parse error: {ex.Message}");
            }
        }

        // ── Heartbeat ─────────────────────────────────────────────────────────

        private void StartHeartbeat()
        {
            _heartbeat?.Dispose();
            _heartbeat = new Timer(
                async _ => await SendJsonAsync(new Dictionary<String, Object> { ["type"] = "ping" }),
                null,
                TimeSpan.FromSeconds(5),
                TimeSpan.FromSeconds(5));
        }

        // ── Disconnect handling ───────────────────────────────────────────────

        private async Task HandleDisconnectAsync()
        {
            _heartbeat?.Dispose();
            _heartbeat = null;

            try { _ws?.Dispose(); } catch { }
            _ws = null;

            _plugin.OnGlassesDisconnected();
            await Task.CompletedTask;
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            _cts?.Cancel();
            _heartbeat?.Dispose();
            _ws?.Dispose();
        }
    }
}
