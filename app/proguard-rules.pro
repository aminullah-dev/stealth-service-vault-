# --- Gson / Retrofit DTO models -------------------------------------------
# Gson uses reflection on field names, so the DTOs must keep their members.
-keep class com.kabulsignal.news.data.remote.dto.** { <fields>; }
-keepclassmembers class com.kabulsignal.news.data.remote.dto.** { <fields>; }

# --- Retrofit / OkHttp ----------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# --- Gson ------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
