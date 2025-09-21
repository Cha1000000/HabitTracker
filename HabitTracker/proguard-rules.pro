# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- HabitTracker Firestore DTO and enums keep rules ---
# Keep DTO used by Firestore to ensure field names remain
-keep class com.habittracker.data.remote.model.HabitInvitationRemote { *; }

# Keep enum names for status mapping
-keepclassmembers enum com.habittracker.domain.model.HabitInvitationStatus { *; }

# (Optional) Keep domain model constructors/fields if used by reflection elsewhere
#-keepclassmembers class com.habittracker.domain.model.HabitInvitation { *; }