# Kodra keeps R8 minimal for debuggability; WebView bridge is not obfuscated.
-keep class dev.unalivin.kodra.** { *; }
-dontwarn android.webkit.**
