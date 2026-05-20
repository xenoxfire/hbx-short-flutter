# HBX Short — Flutter Android App

## Project Overview

A Flutter Android application that loads the HBX Short web app (`https://xenox-short-production.up.railway.app`) in a WebView and provides a native **floating overlay bubble** (ChatHead-style) that persists even when the app is minimized.

### Features
- Full WebView of the HBX Short web app
- JavaScript bridge (`window.XenoxAndroid`) so the web app controls the native bubble
- Floating draggable bubble that snaps to screen edges
- Bubble survives app minimization (foreground service)
- Tapping the bubble reopens the main app
- First-time overlay permission prompt (SYSTEM_ALERT_WINDOW)

### Project Structure
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

## Important Note

**This is a Flutter Android app.** It cannot run in Replit's web preview pane. It must be built and run on an Android device or emulator.

## Build Instructions

### Prerequisites
- Flutter SDK ≥ 3.10
- Android Studio / Android SDK (API 34)
- Java 11+

### Build Commands
```bash
# Install dependencies
flutter pub get

# Run on connected device/emulator
flutter run

# Build release APK
flutter build apk --release
# Output: build/app/outputs/flutter-apk/app-release.apk

# Build App Bundle (for Play Store)
flutter build appbundle --release
```

## User Preferences
- Package: `com.hbx.short`
- App name: `HBX Short`
- Target SDK: 34
- Min SDK: determined by Flutter defaults
