# Add project specific ProGuard rules here.

# Keep Gson model classes
-keep class com.evo.operator.model.** { *; }

# Keep enum values for Gson
-keepclassmembers enum com.evo.operator.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }
