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

# Apache POI (Excel parsing for Exam Seat Finder)
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn com.microsoft.**
-dontwarn javax.xml.stream.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }

# OSGI framework (referenced by Log4j but not used on Android)
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**

# AWT classes (referenced by Apache POI/graphbuilder but not available on Android)
-dontwarn java.awt.**

# LiteRT-LM (Google on-device LLM inference)
-keep class com.google.ai.edge.litertlm.** { *; }
