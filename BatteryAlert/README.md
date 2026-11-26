# Battery Alert

A small native Android app (Kotlin) that plays an alert sound once when your battery drops to a level you choose.

## Features
- Threshold: 10, 15, 20, 25, 30 % or custom (1 to 99 %)
- Alert sound picker (system alarms, notifications, ringtones) with a Test button
- Sound duration: 5 or 10 seconds
- On/off toggle; runs as a foreground service so Android keeps it alive
- Plays once per discharge cycle; re-arms when the battery rises above the threshold
- Settings are saved and monitoring resumes after a reboot
- No internet permission, no ads, no login

## Build the APK with GitHub Actions
1. Create an empty repository on https://github.com/new (no README or .gitignore).
2. In this folder run:
   ```
   git init
   git add .
   git commit -m "Battery Alert"
   git branch -M main
   git remote add origin https://github.com/YOUR_USER/BatteryAlert.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab. The "Build Debug APK" workflow starts on every push (about 3 to 5 minutes).
4. Open the finished run and download **battery-alert-debug-apk** from **Artifacts** (a zip containing `app-debug.apk`).

You can also start a build manually from Actions > Build Debug APK > Run workflow.

## Install on the phone
1. Unzip the artifact and copy `app-debug.apk` to the phone (USB, Drive, email, or download it on the phone directly).
2. Tap the file. If prompted, allow **Install unknown apps** for the app you opened it from (Files, Chrome...).
3. Tap Install, then Open.

## First-time setup (important on OPPO / ColorOS)
1. Turn on **Monitor battery** and allow notifications.
2. Tap **Ignore battery optimization** and choose Allow.
3. Tap **App settings**, then Battery (or "App battery management") and enable **Allow background activity**. Under Settings > Apps > Auto-launch (or Startup manager) allow Battery Alert. Lock the app in the Recents screen if your phone offers it.

## Test it
- Tap **Test** to hear the selected sound and duration (tap again to stop).
- To test the real alert quickly, set the threshold higher than your current level while unplugged, switch monitoring on, and the alert fires immediately because the level is already at or below the threshold. Raise the threshold, re-toggle, or plug in and unplug to test again.
- Re-arm check: plug in the charger until the level is above the threshold, then unplug and let it drop below again.

## Local build
Open the folder in Android Studio (Koala or newer, JDK 17) and run, or with Gradle 8.7: `gradle assembleDebug`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## How it works
- `BatteryMonitorService` is a foreground service (type `specialUse`) that registers for `ACTION_BATTERY_CHANGED`, which Android only delivers to running components.
- `AlertPlayer` plays the sound on the alarm audio stream and stops it after the chosen duration.
- `AlertSettings` stores preferences, including the "armed" flag that enforces one alert per discharge cycle.
- `BootReceiver` restarts the service after reboot or app update.
