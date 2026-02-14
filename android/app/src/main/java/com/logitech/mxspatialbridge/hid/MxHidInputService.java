package com.logitech.mxspatialbridge.hid;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.logitech.mxspatialbridge.MxSpatialBridgeApp;
import com.logitech.mxspatialbridge.mapper.InputMapperEngine;
import com.logitech.mxspatialbridge.ui.ControlMappingActivity;

/**
 * MxHidInputService — persistent foreground service that intercepts
 * Bluetooth HID and USB-C OTG input events from MX peripherals.
 *
 * <h3>Why a foreground service?</h3>
 * Android restricts background app processing.  A foreground service with
 * type=connectedDevice keeps the input pipeline alive when the user is wearing
 * the glasses with eyes on AR content — not looking at the phone/glasses UI.
 *
 * <h3>Bluetooth HID path</h3>
 * When the MX Console pairs with the glasses over Bluetooth, Android routes
 * its HID events through the normal KeyEvent / MotionEvent dispatch chain.
 * The service registers as an {@link android.hardware.input.InputManager.InputDeviceListener}
 * to track device arrivals and dispatches events via {@link #dispatchInputEvent(InputEvent)}.
 *
 * <h3>USB-C OTG path</h3>
 * The manifest registers for {@code USB_DEVICE_ATTACHED}.  On attach, we
 * request permission and open a {@link android.hardware.usb.UsbDeviceConnection}
 * to read raw HID reports directly via interrupt transfers.
 */
public class MxHidInputService extends Service
        implements android.hardware.input.InputManager.InputDeviceListener {

    private static final int NOTIFICATION_ID = 1001;

    private InputMapperEngine mMapper;
    private android.hardware.input.InputManager mInputManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mMapper       = MxSpatialBridgeApp.getInstance().getMapperEngine();
        mInputManager = (android.hardware.input.InputManager) getSystemService(Context.INPUT_SERVICE);
        mInputManager.registerInputDeviceListener(this, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        // If service started by USB_DEVICE_ATTACHED intent, handle the device
        if (intent != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && HidEventParser.isMxDevice(device)) {
                requestUsbPermission(device);
            }
        }

        return START_STICKY;   // restart automatically if killed
    }

    // ── InputDeviceListener (Bluetooth HID) ──────────────────────────────────

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device != null) {
            logDevice("attached", device);
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        // Nothing to clean up; events just stop arriving
    }

    @Override
    public void onInputDeviceChanged(int deviceId) { }

    // ── Event dispatch entry point ─────────────────────────────────────────────
    //
    // The activity or view that receives focus on the glasses display calls
    // this method by overriding dispatchKeyEvent / dispatchGenericMotionEvent
    // and forwarding to the service.  See ArOverlayActivity for the wiring.

    /**
     * Entry point for all HID input events from MX peripherals.
     * Called from ArOverlayActivity (KeyEvent/MotionEvent overrides).
     */
    public void dispatchInputEvent(InputEvent event) {
        if (event instanceof KeyEvent) {
            handleKeyEvent((KeyEvent) event);
        } else if (event instanceof MotionEvent) {
            handleMotionEvent((MotionEvent) event);
        }
    }

    private void handleKeyEvent(KeyEvent event) {
        String eventId = HidEventParser.parseKeyEvent(event);
        if (eventId != null) {
            mMapper.dispatch(eventId, 0);
        }
    }

    private void handleMotionEvent(MotionEvent event) {
        int[] delta = new int[1];
        String eventId = HidEventParser.parseMotionEvent(event, delta);
        if (eventId != null) {
            mMapper.dispatch(eventId, delta[0]);
        }
    }

    // ── USB permission ────────────────────────────────────────────────────────

    private void requestUsbPermission(UsbDevice device) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager != null && !usbManager.hasPermission(device)) {
            Intent permIntent = new Intent("com.logitech.mxspatialbridge.USB_PERMISSION");
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, permIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            usbManager.requestPermission(device, pi);
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, ControlMappingActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MxSpatialBridgeApp.CHANNEL_ID)
                .setContentTitle("MX SpatialBridge")
                .setContentText("Listening for MX peripheral input")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mInputManager.unregisterInputDeviceListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;   // not a bound service
    }
}
