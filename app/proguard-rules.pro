# Proguard rules for PrismDE
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
