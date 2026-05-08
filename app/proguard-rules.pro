# Keep data classes for Gson serialization/deserialization
-keep class com.justpass.app.data.model.** { *; }
-keep class com.justpass.app.data.update.** { *; }
# Private nested classes used by Gson reflection inside ViewModels
# (e.g. SyllabusViewModel$DeptWrapper). Without this, R8 minifies the
# field names and Gson silently returns null for the nested objects.
-keep class com.justpass.app.ui.viewmodel.**$* { *; }

# Keep ALL classes/methods annotated with @JavascriptInterface (anonymous objects in WebView)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Keep WebViewAuthenticator and all its inner/anonymous classes
-keep class com.justpass.app.data.webview.WebViewAuthenticator { *; }
-keep class com.justpass.app.data.webview.WebViewAuthenticator$** { *; }

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
-keep class org.apache.commons.compress.** { *; }
-keepclassmembers class org.apache.commons.compress.** {
    <init>(...);
}
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn com.microsoft.**
-dontwarn javax.xml.stream.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.etsi.** { *; }
-keep class org.w3.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class schemasMicrosoftComOfficeExcel.** { *; }
-keep class schemasMicrosoftComOfficeOffice.** { *; }
-keep class schemasMicrosoftComVml.** { *; }
# XmlBeans loads schema types via reflection (Class.newInstance) — keep no-arg ctors
-keepclassmembers class ** extends org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl {
    <init>();
}
-keepclassmembers class ** implements org.apache.xmlbeans.XmlObject {
    <init>();
}
-keepclassmembers class ** extends org.apache.xmlbeans.XmlObject {
    <init>();
}
-keep class ** extends org.apache.xmlbeans.impl.values.XmlObjectBase { *; }
-keep class ** implements org.apache.xmlbeans.impl.schema.TypeSystemHolder { *; }
-keep public class ** extends org.apache.poi.POIXMLDocumentPart

# OSGI framework (referenced by Log4j but not used on Android)
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**

# Log4j — Apache POI's logger; AbstractLogger reflectively instantiates
# DefaultFlowMessageFactory and other providers via Class.newInstance().
# R8 strips the no-arg ctor unless we keep it.
-keep class org.apache.logging.log4j.** { *; }
-keepclassmembers class org.apache.logging.log4j.** {
    <init>(...);
}

# AWT classes (referenced by Apache POI/graphbuilder but not available on Android)
-dontwarn java.awt.**

# LiteRT-LM (Google on-device LLM inference)
-keep class com.google.ai.edge.litertlm.** { *; }

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
