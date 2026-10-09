# Ephy Remote Keyboard v2.0 — Bluetooth HID prototype

Android native source project for TECNO Camon 20 and Windows Bluetooth HID pairing. **This ZIP is source code, not an APK.** The APK must be built in GitHub Actions or Android Studio.

## Build with GitHub Actions
1. Create a new GitHub repository.
2. Upload the CONTENTS of this folder to the repository root, including `.github/workflows/android-build.yml` (uploading hidden folders may be easier with Git).
3. Open Actions → Build Ephy Bluetooth APK → Run workflow.
4. Download the `Ephy-Remote-Keyboard-Bluetooth-v2.0-APK` artifact after a successful build.
5. Extract artifact ZIP and install `app-debug.apk` on your phone (allow installation from that source).

## Use
1. Turn on Bluetooth on TECNO Camon 20 and Windows laptop.
2. Pair the devices through Android and Windows Bluetooth settings.
3. Launch Ephy Remote Keyboard, grant Nearby Devices permission, tap Enable Bluetooth keyboard.
4. If status says ready, tap Choose paired Windows laptop and select it.
5. Once Connected appears, click Notepad on Windows and tap the text field on Android to type.

## Limitations / compatibility
- Android Bluetooth HID Device profile must be exposed by the manufacturer's firmware. Some phones reject registration or connection.
- HID connection is not guaranteed on TECNO Camon 20; test on physical device.
- This first version supports basic US ASCII characters. Predictive text, IME replacements, pasted text, Unicode, complex cursor edits, and rapid input may not transmit correctly.
- No Windows receiver is needed when HID works.
- Debug APK is for testing, not Play Store release. No automated physical-device tests performed.
- Bluetooth connection ends if Android revokes the HID profile or closes the activity.
