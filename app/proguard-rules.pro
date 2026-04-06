# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tablebot.**$$serializer { *; }
-keepclassmembers class com.tablebot.** { *** Companion; }
-keepclasseswithmembers class com.tablebot.** { kotlinx.serialization.KSerializer serializer(...); }
