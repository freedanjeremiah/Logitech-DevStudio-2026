namespace Loupedeck
{
    using System;

    /// <summary>
    /// AR Select — confirm the currently focused element in the AR interface.
    ///
    /// Equivalent to pressing Enter / A-button on a gamepad in the AR OS.
    /// Assign to any MX Creative Console keypad button or an Actions Ring bubble.
    /// </summary>
    public class ArSelectCommand : PluginDynamicCommand
    {
        public ArSelectCommand()
            : base(
                displayName:  "AR Select",
                description:  "Confirm / select the focused item in the AR interface",
                groupName:    "AR Controls")
        {
        }

        protected override void RunCommand(String actionParameter)
        {
            MxSpatialBridgePlugin.Instance?.SendArAction("select");
        }

        protected override BitmapImage GetCommandImage(String actionParameter, PluginImageSize imageSize)
        {
            using var b = new BitmapBuilder(imageSize);
            b.FillRectangle(0, 0, b.Width, b.Height, new BitmapColor(0, 112, 210));   // Logitech blue
            b.DrawText("✓", BitmapColor.White, fontSize: b.Width * 0.45f);
            return b.ToImage();
        }
    }
}
