# HBX Short — Flutter Android App

A Flutter Android app that loads the HBX Short web app in a WebView and provides a
native **floating overlay bubble** that stays on-screen even when the app is minimized.

---

## Features

- Full WebView of `https://xenox-short-production.up.railway.app`
- JavaScript bridge (`window.XenoxAndroid`) so the web app controls the native bubble
- Floating draggable bubble that snaps to screen edges
- Bubble survives app minimization (foreground service)
- Tapping the bubble reopens the main app
- First-time overlay permission prompt (SYSTEM_ALERT_WINDOW)
- Hardware back button navigates back in WebView
- App name: **HBX Short** | Package: **com.hbx.short**

---

## Build Instructions

### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) ≥ 3.10
- Android Studio / Android SDK (API 34)
- Java 11+

### Steps

```bash
# 1. Clone / download this project
cd hbx_short

# 2. Install Flutter dependencies
flutter pub get

# 3. Connect an Android device or start an emulator

# 4. Run in debug mode
flutter run

# 5. Build release APK
flutter build apk --release
# APK will be at: build/app/outputs/flutter-apk/app-release.apk

# 6. Build App Bundle (for Play Store)
flutter build appbundle --release
```

### First Run
On first launch, when the user taps **"Start Bubble"** in the Float Sheet page,
the app will open the Android **"Display over other apps"** settings screen.
After granting permission, the user taps "Start Bubble" again and the bubble appears.

---

## Project Structure

```
hbx_short/
├── lib/
│   └── main.dart                          # Flutter app + WebView + JS bridge
└── android/
    └── app/src/main/
        ├── AndroidManifest.xml            # Permissions + service declaration
        └── kotlin/com/hbx/shortapp/
            ├── MainActivity.kt            # MethodChannel handler
            └── BubbleOverlayService.kt    # Floating bubble foreground service
```

## JavaScript Bridge

The web app communicates with Android via `window.XenoxAndroid`:

| Method | Description |
|--------|-------------|
| `hasOverlayPermission()` | Returns `true` if SYSTEM_ALERT_WINDOW is granted |
| `requestOverlayPermission()` | Opens Android overlay settings |
| `startBubble()` | Starts the floating bubble service |
| `stopBubble()` | Stops the bubble |
| `isBubbleRunning()` | Returns `true` if bubble is active |

These are already called by the existing web app's Float Sheet page.
