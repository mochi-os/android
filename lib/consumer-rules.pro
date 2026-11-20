# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson models
-keep class org.mochios.android.model.** { *; }
# The parse guard reads each model's Kotlin metadata (NullGuard in ApiClient).
-keep class kotlin.Metadata { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
