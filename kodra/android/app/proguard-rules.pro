# Kodra keeps R8 minimal for debuggability; WebView bridge is not obfuscated.
-keep class dev.sonixdevs.kodra.** { *; }
-dontwarn android.webkit.**
