# android-runtime consumer ProGuard rules
# kotlinx.serialization-generated companion objects are looked up reflectively
# by the runtime; keep them so R8 in the consumer app doesn't strip them.
-keepclassmembers class com.handy.runtime.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.handy.runtime.**$$serializer { *; }
