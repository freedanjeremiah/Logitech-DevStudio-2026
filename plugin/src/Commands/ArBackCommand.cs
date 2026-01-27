namespace Loupedeck
{
    using System;

    /// <summary>
    /// AR Back — navigate back in the AR UI hierarchy.
    ///
    /// Equivalent to the Android KEYCODE_BACK event, delivered to the glasses
    /// over the WebSocket action channel.
    /// </summary>
    public class ArBackCommand : PluginDynamicCommand
    {
        public ArBackCommand()
            : base(
                displayName:  "AR Back",
                description:  "Navigate back in the AR interface",
                groupName:    "AR Controls")
        {
        }

        protected override void RunCommand(String actionParameter)
        {
            MxSpatialBridgePlugin.Instance?.SendArAction("back");
        }

        protected override BitmapImage GetCommandImage(String actionParameter, PluginImageSize imageSize)
        {
            using var b = new BitmapBuilder(imageSize);
            b.FillRectangle(0, 0, b.Width, b.Height, new BitmapColor(48, 48, 56));
            b.DrawText("←", BitmapColor.White, fontSize: b.Width * 0.45f);
            return b.ToImage();
        }
    }
}
