package com.ephydigital.remotekeyboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 101;
    private static final byte[] REPORT_DESCRIPTOR = new byte[] {
        0x05,0x01, 0x09,0x06, (byte)0xA1,0x01, 0x05,0x07, 0x19,(byte)0xE0, 0x29,(byte)0xE7,
        0x15,0x00, 0x25,0x01, 0x75,0x01, (byte)0x95,0x08, (byte)0x81,0x02,
        (byte)0x95,0x01, 0x75,0x08, (byte)0x81,0x01,
        (byte)0x95,0x06, 0x75,0x08, 0x15,0x00, 0x25,0x65, 0x05,0x07, 0x19,0x00, 0x29,0x65, (byte)0x81,0x00,
        (byte)0xC0
    };
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice target;
    private boolean registered = false;
    private boolean connected = false;
    private TextView status;
    private EditText typing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = command -> handler.post(command);
    private boolean suppress = false;

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            registered = isRegistered;
            show(isRegistered ? "Bluetooth keyboard ready. Select your laptop." : "HID registration failed or stopped. Firmware may not support HID Device.");
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
            connected = state == BluetoothProfile.STATE_CONNECTED;
            if (connected) target = device;
            show(connected ? "Connected to " + safeName(device) : "Disconnected. Choose your paired laptop.");
        }
    };
    private void show(String message) { runOnUiThread(() -> status.setText(message)); }
    private boolean permitted() { return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; }
    private String safeName(BluetoothDevice d) { if (d == null) return "device"; try { return permitted() ? d.getName() : "device"; } catch (SecurityException e) { return "device"; } }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status); typing = findViewById(R.id.typing);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        findViewById(R.id.enable).setOnClickListener(v -> startHid());
        findViewById(R.id.select).setOnClickListener(v -> chooseLaptop());
        findViewById(R.id.disconnect).setOnClickListener(v -> { if (hid != null && target != null && permitted()) hid.disconnect(target); });
        bindKey(R.id.enter, 40); bindKey(R.id.backspace, 42); bindKey(R.id.tab, 43);
        bindKey(R.id.left, 80); bindKey(R.id.right, 79); bindKey(R.id.up, 82); bindKey(R.id.down, 81);
        typing.addTextChangedListener(new TextWatcher() {
            private String previous = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppress) return;
                String current = s.toString();
                // Handle normal append and deletion. Mid-string IME edits are intentionally not transmitted.
                if (current.startsWith(previous)) {
                    String appended = current.substring(previous.length());
                    for (int i = 0; i < appended.length(); i++) sendChar(appended.charAt(i));
                } else if (previous.startsWith(current)) {
                    for (int i = current.length(); i < previous.length(); i++) sendKey(42, 0);
                } else {
                    show("For reliable typing, append at the end of the field. Clear the field to start over.");
                }
                previous = current;
                if (current.length() > 300) {
                    suppress = true; typing.setText(""); previous = ""; suppress = false;
                }
            }
            @Override public void afterTextChanged(Editable e) {}
        });
    }
    private void bindKey(int viewId, int keyCode) { findViewById(viewId).setOnClickListener(v -> sendKey(keyCode, 0)); }
    private void startHid() {
        if (adapter == null) { show("Bluetooth is not available on this phone."); return; }
        if (!permitted()) { if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT); return; }
        if (!adapter.isEnabled()) { show("Turn on Bluetooth in Android Settings, then try again."); return; }
        if (hid != null && registered) { show("Keyboard is already enabled. Choose your laptop."); return; }
        show("Requesting Bluetooth HID Device profile...");
        boolean accepted = adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (!(proxy instanceof BluetoothHidDevice)) { show("HID Device unavailable."); return; }
                hid = (BluetoothHidDevice) proxy;
                BluetoothHidDevice.SdpSettings sdp = new BluetoothHidDevice.SdpSettings("Ephy Remote Keyboard", "Android Bluetooth Keyboard", "Ephy Digital", BluetoothHidDevice.SUBCLASS1_KEYBOARD, REPORT_DESCRIPTOR);
                try { boolean ok = hid.registerApp(sdp, null, null, executor, callback); if (!ok) show("HID registration rejected by Android firmware."); }
                catch (SecurityException e) { show("Bluetooth permission denied."); }
            }
            @Override public void onServiceDisconnected(int profile) { hid = null; registered = false; connected = false; show("Bluetooth HID service disconnected."); }
        }, BluetoothProfile.HID_DEVICE);
        if (!accepted) show("HID Device profile not supported by this phone's firmware.");
    }
    private void chooseLaptop() {
        if (!permitted()) { startHid(); return; }
        if (hid == null || !registered) { show("Enable Bluetooth keyboard first."); return; }
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired == null || paired.isEmpty()) { show("Pair your Windows laptop in Android Bluetooth settings first."); return; }
        List<BluetoothDevice> devices = new ArrayList<>(paired);
        String[] labels = new String[devices.size()];
        for (int i=0; i<devices.size(); i++) labels[i] = safeName(devices.get(i)) + " (" + devices.get(i).getAddress() + ")";
        new AlertDialog.Builder(this).setTitle("Choose paired laptop").setItems(labels, (dialog, which) -> {
            target = devices.get(which);
            show("Connecting to " + safeName(target) + "...");
            try { if (!hid.connect(target)) show("Connection rejected. Verify Windows pairing and HID support."); }
            catch (SecurityException e) { show("Bluetooth permission denied."); }
        }).setNegativeButton("Cancel", null).show();
    }
    private void sendKey(int keyCode, int modifier) {
        if (!connected || hid == null || target == null || !permitted()) { show("Not connected. Enable HID and choose your laptop."); return; }
        byte[] press = new byte[]{(byte)modifier, 0, (byte)keyCode, 0,0,0,0,0};
        byte[] release = new byte[8];
        try {
            if (!hid.sendReport(target, 0, press)) { show("Failed to send key."); return; }
            handler.postDelayed(() -> { try { if (hid != null && target != null && connected && permitted()) hid.sendReport(target, 0, release); } catch (Exception ignored) {} }, 28);
        } catch (SecurityException e) { show("Bluetooth permission denied."); }
    }
    private void sendChar(char c) {
        if (c == '\n') { sendKey(40,0); return; }
        if (c == '\t') { sendKey(43,0); return; }
        if (c >= 'a' && c <= 'z') { sendKey(c-'a'+4,0); return; }
        if (c >= 'A' && c <= 'Z') { sendKey(c-'A'+4,2); return; }
        if (c >= '1' && c <= '9') { sendKey(c-'1'+30,0); return; }
        if (c == '0') { sendKey(39,0); return; }
        String plain = " -=[]\\;',./";
        int[] codes = {44,45,46,47,48,49,51,52,54,55,56};
        int idx = plain.indexOf(c);
        if (idx >= 0) { sendKey(codes[idx],0); return; }
        String shifted = "_+{}|:\"<>?!@#$%^&*()";
        int[] shiftCodes = {45,46,47,48,49,51,52,54,55,56,30,31,32,33,34,35,36,37,38,39};
        idx = shifted.indexOf(c);
        if (idx >= 0) { sendKey(shiftCodes[idx],2); return; }
        show("Unsupported character: " + c + " (v2.0 supports basic US keyboard characters).");
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startHid();
    }
    @Override protected void onDestroy() {
        if (hid != null && permitted()) { try { if (registered) hid.unregisterApp(); if (adapter != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
