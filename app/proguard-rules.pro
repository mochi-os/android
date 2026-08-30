# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class org.mochios.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Room opens a database by reflecting for the no-argument constructor of the
# <Database>_Impl class its compiler generated. Nothing calls that constructor
# from code, so R8 takes it away and the open fails with NoSuchMethodException.
# The app declares no database of its own, but WorkManager brings one, and it
# is built by an androidx.startup initializer - so the failure lands inside
# InitializationProvider before any of this app's code has run, and every
# launch dies. A database added here later needs the same rule.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Markwon's optional SVG and GIF media decoders reference androidsvg and
# android-gif-drawable, neither of which is a dependency. Nothing constructs
# those decoders, so the references are unreachable - but R8 refuses to run
# on a dangling reference it was not told to ignore.
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException
-dontwarn pl.droidsonroids.gif.GifDrawable
