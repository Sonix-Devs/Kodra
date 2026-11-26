# Kodra architecture — end-to-end

```
Activity (MainActivity) — WebView —addWebMessageListener→ WorkbenchBridge
       ↕ bindService                ↕ Intents
KodraService (foreground) — ServerProcess —node→ REH-web (out/server-main.js)
       ↕ HOME/PREFIX/TMP/LD path   ↕ 127.0.0.1:0 + token file
BootstrapInstaller — $PREFIX/files/usr — node-pty / ripgrep / git / python
       ↕ assets/bootstrap.zip
FilePickerBridge — SAF → POSIX — /storage/emulated/0/Kodra
GithubAuth + DeviceAuthFlow — Keystore AES/GCM — git credential helper
```

All Android code in `kodra/android/app/src/main/java/dev/unalivin/kodra/`.
Product identity `dev.unalivin.kodra` (product.json), URL protocol `kodra://`.
No Microsoft gallery/telemetry/tunnel — verified by `kodra-reh.yml` origin checks.

See PORTING_NOTES.md Architecture decisions 1-9 for rationale.
