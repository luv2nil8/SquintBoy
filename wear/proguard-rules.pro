# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.anaglych.squintboyadvance.**$$serializer { *; }
-keepclassmembers class com.anaglych.squintboyadvance.** {
    *** Companion;
}
-keepclasseswithmembers class com.anaglych.squintboyadvance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Wearable Data Layer
-keep class com.google.android.gms.wearable.** { *; }

# JNI — keep all native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose — keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
