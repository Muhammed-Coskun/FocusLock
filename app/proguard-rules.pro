# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.personal.focuslock.** {
    *** Companion;
}
-keepclasseswithmembers class com.personal.focuslock.** {
    kotlinx.serialization.KSerializer serializer(...);
}
