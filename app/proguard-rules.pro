# Handy app ProGuard rules — release-only.
# Keep kotlinx.serialization metadata for core and runtime models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.handy.core.**$$serializer { *; }
-keepclassmembers class com.handy.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.handy.runtime.**$$serializer { *; }

# OkHttp / Okio — keep platform-reflected classes.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.**

# Production privacy audit:
# - Debug/verbose Timber calls are stripped from release.
# - @Sensitive field names are allowed to obfuscate in mapping output.
# - Data classes that carry @Sensitive values must override toString().
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
}
-keep @interface com.handy.core.privacy.Sensitive
-keepclassmembers,allowshrinking,allowoptimization,allowobfuscation class * {
    @com.handy.core.privacy.Sensitive <fields>;
}

# Compose + Hilt already have consumer rules; nothing extra needed here.
