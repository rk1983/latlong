# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers for readable stack traces in Play Console crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization rules
-keepattributes *Annotation*, InnerClasses, Signature

-keepclassmembers class * {
    *** Companion;
}

-keepclassmembers class * {
    *** $serializer;
}

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable class *;
}

-keep class *$$serializer {
    *;
}

-dontwarn kotlinx.serialization.**

# LatLong domain & data models
-keep class no.madival.latlong.data.** { *; }
-keepclassmembers class no.madival.latlong.data.** { *; }
