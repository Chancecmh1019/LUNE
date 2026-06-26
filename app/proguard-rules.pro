# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools/proguard/proguard-android.txt

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# kotlinx.datetime
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# kotlin-inject (DI)
-keep class me.tatarka.inject.** { *; }
-dontwarn me.tatarka.inject.**

# Keep AppComponent and generated implementations
-keep class com.luneapp.official.di.** { *; }

# Firebase / Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# DataStore
-keep class androidx.datastore.** { *; }

# Health KMP
-keep class com.viktormykhailiv.kmp.health.** { *; }
-dontwarn com.viktormykhailiv.kmp.health.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Navigation
-keepnames class androidx.navigation.** { *; }

# Suppress warnings for unused platforms in multiplatform libraries
-dontwarn org.slf4j.**
-dontwarn java.awt.**
-dontwarn javax.annotation.**
