# HBX Short — Flutter Android App

A Flutter Android app that loads the HBX Short web app in a WebView with a native **floating overlay bubble**, Firebase push notifications, and full native clipboard/file-share support.

**Web App:** `https://xenox-short-production.up.railway.app`  
**Package:** `com.hbx.short`  
**Min SDK:** 23 (Android 6.0) | **Target SDK:** 34 (Android 14)

---

## ✅ Features

| Feature | Status |
|---------|--------|
| Full-screen WebView (no browser bar) | ✅ |
| Splash screen with app logo | ✅ |
| Hardware acceleration | ✅ |
| Smooth scrolling & native feel | ✅ |
| Offline cache / retry on error | ✅ |
| Floating draggable bubble (chat-head) | ✅ |
| Bubble uses actual app launcher icon | ✅ |
| Bubble size control from web app | ✅ |
| Bubble snaps to screen edges | ✅ |
| Firebase FCM push notifications | ✅ |
| Heads-up notifications (foreground) | ✅ |
| Notifications auto-delete after 24h | ✅ |
| Cut / Copy / Paste clipboard support | ✅ |
| File sharing (Share Sheet) | ✅ |
| Hardware back → WebView goBack | ✅ |
| SYSTEM_ALERT_WINDOW permission flow | ✅ |

---

## 🔥 Firebase FCM Setup (Required for Push Notifications)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a project (or use existing one)
3. Add an **Android app** with package name `com.hbx.short`
4. Download `google-services.json`
5. Replace `android/app/google-services.json` with your downloaded file
6. Get the **Server Key** from Firebase Console → Project Settings → Cloud Messaging
7. Use the server key in your admin panel to send pushes

> Without a real `google-services.json`, the app builds and runs fine — FCM push just won't work.

---

## 📱 JavaScript Bridge — `window.XenoxAndroid`

The web app communicates with Android via `window.XenoxAndroid`:

| Method | Description |
|--------|-------------|
| `hasOverlayPermission()` | Returns `true` if SYSTEM_ALERT_WINDOW is granted |
| `requestOverlayPermission()` | Opens Android overlay settings |
| `startBubble(sizeDp?)` | Starts floating bubble (optional size in dp) |
| `stopBubble()` | Stops the bubble |
| `isBubbleRunning()` | Returns `true` if bubble is active |
| `setBubbleSize(dp)` | Resize bubble while running |
| `showHeadsUpNotification(title, msg)` | Show heads-up notification |
| `shareText(text)` | Open Android Share Sheet |
| `copyToClipboard(text)` | Copy text to clipboard |
| `getFcmToken()` | Retrieve FCM device token (async → calls `window.onFcmToken(token)`) |

### Admin → Send notification example (Firebase REST)
```bash
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "DEVICE_FCM_TOKEN",
    "notification": {
      "title": "HBX Short",
      "body": "আপনার নতুন নোটিফিকেশন"
    }
  }'
```

> Notifications auto-delete from the tray after **24 hours** via AlarmManager.

---

## 🏗️ Build Instructions

### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) ≥ 3.10
- Android Studio / Android SDK API 34
- Java 17+

### Steps
```bash
# 1. Install Flutter dependencies
flutter pub get

# 2. Run on connected device
flutter run

# 3. Build release APK (split by ABI — smaller file size)
flutter build apk --release --split-per-abi

# APKs at: build/app/outputs/flutter-apk/
#   app-arm64-v8a-release.apk   ← most modern phones
#   app-armeabi-v7a-release.apk ← older phones
```

---

## 🚀 Automated CI/CD (GitHub Actions)

Every push to `main` automatically builds the APK.  
Tag a release with `v*` (e.g. `git tag v1.1.0 && git push --tags`) to create a GitHub Release with the APK attached.

---

## 📂 Project Structure

```
hbx_short/
├── assets/icon/app_icon.png              # Splash screen icon asset
├── lib/
│   └── main.dart                         # Splash + WebView + JS bridge
└── android/
    └── app/
        ├── google-services.json          # ← Replace with real Firebase file!
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            └── kotlin/com/hbx/shortapp/
                ├── MainActivity.kt                  # MethodChannel handler
                ├── BubbleOverlayService.kt          # Floating bubble service
                ├── HbxFirebaseMessagingService.kt   # FCM push notifications
                └── NotificationDeleteReceiver.kt    # 24h auto-delete
```
