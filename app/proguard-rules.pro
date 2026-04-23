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

# Compose + Hilt already have consumer rules; nothing extra needed here.
