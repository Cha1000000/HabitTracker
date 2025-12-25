# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name in stack traces
-renamesourcefileattribute SourceFile

# ============================================================================
# HabitTracker Firestore DTO and enums keep rules
# ============================================================================

# Keep all Firestore Remote DTOs (used by Firestore reflection)
-keep class com.habittracker.data.remote.model.** { *; }

# Keep all domain model enums (used for status mapping)
-keepclassmembers enum com.habittracker.domain.model.** { *; }

# Keep domain models used by Firestore (data classes with no-arg constructors)
-keepclassmembers class com.habittracker.domain.model.** {
    <init>();
    <init>(...);
}

# ============================================================================
# Firebase / Firestore
# ============================================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep Firestore document classes
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# ============================================================================
# Retrofit / OkHttp / Gson
# ============================================================================
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================================================
# Room Database
# ============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ============================================================================
# Hilt / Dagger
# ============================================================================
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ============================================================================
# Kotlin Serialization
# ============================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.habittracker.**$$serializer { *; }
-keepclassmembers class com.habittracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.habittracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Coroutines
# ============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================================
# Coil (Image loading)
# ============================================================================
-dontwarn coil.**
-keep class coil.** { *; }

# ============================================================================
# Google Auth (OAuth 2.0 for FCM)
# ============================================================================
-dontwarn com.google.auth.**
-keep class com.google.auth.** { *; }

# ============================================================================
# Compose
# ============================================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================================================================
# WorkManager
# ============================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ============================================================================
# Konfetti (Confetti animations)
# ============================================================================
-keep class nl.dionsegijn.konfetti.** { *; }