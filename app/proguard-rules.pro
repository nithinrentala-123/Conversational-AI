# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities
-keep class com.niquewrld.conversationalai.Room.** { *; }

# Keep Model class for Gson
-keep class com.niquewrld.conversationalai.Model { *; }

# Keep Cronet
-keep class org.chromium.net.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
