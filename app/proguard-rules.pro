# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }

# Keep our app entities from being renamed/removed by R8
-keep class com.safebeauty.app.data.db.entities.** { *; }
-keep class com.safebeauty.app.data.db.dao.** { *; }
-keep class com.safebeauty.app.data.db.AppDatabase { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Crashlytics — keep file names and line numbers so deobfuscated crash
# reports remain readable, and preserve custom exception classes.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Firestore data models (no-arg constructors needed for deserialization)
-keep class com.safebeauty.app.data.firebase.** { *; }
-keep class com.safebeauty.app.data.model.** { *; }
