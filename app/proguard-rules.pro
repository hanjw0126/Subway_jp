# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class io.github.jpsubway.app.**$$serializer { *; }
-keepclassmembers class io.github.jpsubway.app.** { *** Companion; }
-keepclasseswithmembers class io.github.jpsubway.app.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
