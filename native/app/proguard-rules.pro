# RGBTv native — ProGuard / R8 rules (release, minifyEnabled).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn java.lang.invoke.**

# App models are parsed with org.json / JsonReader (no reflection), keep them anyway.
-keep class com.rgbtv.app.data.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Media3 / ExoPlayer (consumer rules ship with the AARs; keep entry points safe)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Material / AppCompat (resource shrinking keeps referenced items; silence the rest)
-dontwarn com.google.android.material.**
