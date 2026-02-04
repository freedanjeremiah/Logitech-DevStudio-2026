namespace Loupedeck
{
    using System;
    using System.Collections.Generic;
    using System.IO;
    using System.Text.Json;

    /// <summary>
    /// Loads, saves, and switches SpatialProfiles.
    ///
    /// Profiles are stored as JSON files in the plugin data directory so they
    /// persist across Options+ restarts and survive plugin updates.
    ///
    /// The active profile is the one currently synced to the connected glasses.
    /// </summary>
    public class ProfileManager
    {
        private readonly MxSpatialBridgePlugin _plugin;
        private readonly String _profileDir;

        public SpatialProfile ActiveProfile { get; private set; } = new SpatialProfile();

        public List<SpatialProfile> AllProfiles { get; } = new();

        public ProfileManager(MxSpatialBridgePlugin plugin)
        {
            _plugin     = plugin;
            _profileDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "Logi", "LogiPluginService", "MxSpatialBridge", "Profiles");
        }

        public void Load()
        {
            Directory.CreateDirectory(_profileDir);

            // Seed default profile if directory is empty
            var defaultPath = Path.Combine(_profileDir, "default.json");
            if (!File.Exists(defaultPath))
                File.WriteAllText(defaultPath, new SpatialProfile().ToJson());

            AllProfiles.Clear();

            foreach (var file in Directory.EnumerateFiles(_profileDir, "*.json"))
            {
                try
                {
                    var json    = File.ReadAllText(file);
                    var profile = SpatialProfile.FromJson(json);
                    AllProfiles.Add(profile);
                }
                catch (Exception ex)
                {
                    // Malformed profile file — skip and log
                    Console.Error.WriteLine($"[MxSpatialBridge] Failed to load profile '{file}': {ex.Message}");
                }
            }

            ActiveProfile = AllProfiles.Count > 0 ? AllProfiles[0] : new SpatialProfile();
        }

        public void Save(SpatialProfile profile)
        {
            Directory.CreateDirectory(_profileDir);
            var filename = SanitiseFilename(profile.Name) + ".json";
            File.WriteAllText(Path.Combine(_profileDir, filename), profile.ToJson());
        }

        public void SetActive(String profileName)
        {
            var found = AllProfiles.Find(p =>
                String.Equals(p.Name, profileName, StringComparison.OrdinalIgnoreCase));

            if (found != null)
            {
                ActiveProfile = found;
                // Sync to glasses immediately if connected
                if (_plugin.SyncClient?.IsConnected == true)
                    _ = _plugin.SyncClient.SyncProfileAsync(ActiveProfile);
            }
        }

        private static String SanitiseFilename(String name)
        {
            foreach (var c in Path.GetInvalidFileNameChars())
                name = name.Replace(c, '_');
            return name.ToLowerInvariant().Replace(' ', '-');
        }
    }
}
