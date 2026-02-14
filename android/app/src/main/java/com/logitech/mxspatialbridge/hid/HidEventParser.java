package com.logitech.mxspatialbridge.hid;

import android.hardware.usb.UsbDevice;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * HidEventParser translates raw Android HID events (KeyEvent / MotionEvent)
 * from the MX Creative Console Dialpad and MX Master 4 into canonical event
 * IDs understood by {@link com.logitech.mxspatialbridge.mapper.InputMapperEngine}.
 *
 * <h3>MX Creative Console HID layout (Bluetooth + USB-C OTG)</h3>
 * <pre>
 *   Dial rotation CW   → REL_DIAL positive delta  (HID usage 0x38)
 *   Dial rotation CCW  → REL_DIAL negative delta
 *   Dial press         → KEY_ENTER  (or vendor HID key 0x90)
 *   Keypad btn 0..N    → KEY_F13..F24 range on most firmware versions
 * </pre>
 *
 * <h3>MX Master 4 HID layout</h3>
 * <pre>
 *   Thumb wheel up     → REL_HWHEEL positive / KEY_SCROLLLEFT
 *   Thumb wheel down   → REL_HWHEEL negative / KEY_SCROLLRIGHT
 *   Gesture btn left   → BTN_SIDE + AXIS_X negative
 *   Gesture btn right  → BTN_SIDE + AXIS_X positive
 * </pre>
 *
 * Output event IDs match the keys defined in {@code SpatialProfile.mappings}.
 */
public final class HidEventParser {

    // ── Key codes used by MX Creative Console firmware ────────────────────────
    // Most firmware revisions report keypad buttons as F13..F24
    private static final int KEY_F13 = 183;
    private static final int KEY_F24 = 194;

    // MX Master 4 thumb-wheel axes
    private static final int AXIS_HWHEEL = MotionEvent.AXIS_HSCROLL;

    private HidEventParser() {}

    // ── KeyEvent → canonical event ID ─────────────────────────────────────────

    /**
     * @param event Android KeyEvent from the HID device
     * @return canonical event ID string, or {@code null} if not mapped
     */
    public static String parseKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return null;  // only handle press

        int keyCode = event.getKeyCode();

        // MX Dial press: ENTER or vendor key
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (isMxDialDevice(event.getDevice())) {
                return "dial-press";
            }
        }

        // Keypad buttons F13..F24 → btn-0..btn-11
        if (keyCode >= KEY_F13 && keyCode <= KEY_F24) {
            return "btn-" + (keyCode - KEY_F13);
        }

        // MX Master 4 gesture button: BTN_SIDE
        if (keyCode == KeyEvent.KEYCODE_BUTTON_SIDE) {
            return "gesture-click";
        }

        return null;
    }

    // ── MotionEvent → canonical event ID ─────────────────────────────────────

    /**
     * Parses continuous motion (dial rotation, wheel scroll).
     *
     * @param event  Android MotionEvent from the HID device
     * @param outDelta  single-element array; filled with the signed tick delta
     * @return canonical event ID string, or {@code null} if not mapped
     */
    public static String parseMotionEvent(MotionEvent event, int[] outDelta) {
        if (event.getSource() == InputDevice.SOURCE_CLASS_POINTER
                || event.getAction() != MotionEvent.ACTION_SCROLL) {
            return null;
        }

        float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float hScroll = event.getAxisValue(AXIS_HWHEEL);

        // MX Dial vertical scroll
        if (Math.abs(vScroll) > 0.001f) {
            outDelta[0] = (int) Math.signum(vScroll);  // +1 or -1 per detent
            // Dial: positive = clockwise (scroll down in list)
            return vScroll > 0 ? "dial-cw" : "dial-ccw";
        }

        // MX Master 4 thumb wheel (horizontal scroll axis)
        if (Math.abs(hScroll) > 0.001f) {
            outDelta[0] = (int) Math.signum(hScroll);
            return hScroll > 0 ? "thumb-wheel-up" : "thumb-wheel-dn";
        }

        // Gesture button swipe (delivered as AXIS_X on some firmware versions)
        float xAxis = event.getAxisValue(MotionEvent.AXIS_X);
        if (isMxMasterGestureDevice(event.getDevice()) && Math.abs(xAxis) > 5.0f) {
            outDelta[0] = (int) Math.signum(xAxis);
            return xAxis > 0 ? "gesture-right" : "gesture-left";
        }

        return null;
    }

    // ── Device identification ─────────────────────────────────────────────────

    /** Logitech vendor ID used across the MX product line */
    private static final int LOGITECH_VID = 0x046D;

    /** MX Creative Console (Dialpad) product ID */
    private static final int MX_CONSOLE_PID = 0xC2AB;

    /** MX Master 4 product ID */
    private static final int MX_MASTER4_PID = 0xC548;

    public static boolean isMxDevice(UsbDevice device) {
        return device.getVendorId() == LOGITECH_VID
                && (device.getProductId() == MX_CONSOLE_PID
                    || device.getProductId() == MX_MASTER4_PID);
    }

    /** Returns true if the InputDevice looks like an MX Dial */
    private static boolean isMxDialDevice(InputDevice device) {
        if (device == null) return false;
        String name = device.getName().toLowerCase();
        return name.contains("creative console") || name.contains("dialpad") || name.contains("mx dial");
    }

    /** Returns true if the InputDevice looks like the MX Master 4 */
    private static boolean isMxMasterGestureDevice(InputDevice device) {
        if (device == null) return false;
        String name = device.getName().toLowerCase();
        return name.contains("mx master") || name.contains("master 4");
    }
}
