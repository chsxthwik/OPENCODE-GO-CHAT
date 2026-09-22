-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
