# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.squintboyadvance.**$$serializer { *; }
-keepclassmembers class com.example.squintboyadvance.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.squintboyadvance.** {
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
