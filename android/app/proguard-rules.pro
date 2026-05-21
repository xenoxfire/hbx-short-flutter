# Flutter
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.embedding.** { *; }

# Google Play Core (required by Flutter release builds)
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# HBX Short native classes
-keep class com.hbx.shortapp.** { *; }

# WebView JS interface — never obfuscate
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**
