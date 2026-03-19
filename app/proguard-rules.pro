# Keep data classes for Gson serialization/deserialization
-keep class com.example.attendancewidgetlaudea.data.model.** { *; }
-keep class com.example.attendancewidgetlaudea.data.update.** { *; }

# Keep ALL classes/methods annotated with @JavascriptInterface (anonymous objects in WebView)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Keep WebViewAuthenticator and all its inner/anonymous classes
-keep class com.example.attendancewidgetlaudea.data.webview.WebViewAuthenticator { *; }
-keep class com.example.attendancewidgetlaudea.data.webview.WebViewAuthenticator$** { *; }

# General Android Compose rules (usually handled by R8, but good to be safe)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Gson TypeToken (needed for generic type deserialization like List<AbsentDay>)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Error Prone annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Liquid Glass library (AGSL shaders)
-keep class io.github.fletchmckee.liquid.** { *; }
