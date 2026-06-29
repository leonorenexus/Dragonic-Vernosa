<p align="center">
  <img src="assets/logo.png" width="140" alt="Dragonic Vernosa logo" />
</p>

# Dragonic Vernosa

Chatbot AI **offline** (Kotlin host + WebView UI) yang jalanin model GGUF lokal
lewat llama.cpp (compiled via NDK). Full local inference, no API, no cloud.

Brand: **Leonore Tech Team**

## Arsitektur
- **Kotlin** — host app, JNI bridge, file picker (SAF), download manager
- **WebView (HTML/CSS/TS→JS)** — UI chat, cyberpunk neon dark theme
- **llama.cpp (C++ via NDK)** — inference engine, di-fetch otomatis dari source
  resmi pas build (tag `b3600`, lihat `app/src/main/cpp/CMakeLists.txt`)

## Cara pakai model
1. **Import** file `.gguf` dari storage HP, atau
2. **Download** langsung dari link `.gguf` (misal resolve URL dari HuggingFace)

Disarankan model kecil 1-3B params (quantized Q4_K_M) biar generate-nya
ngebut di HP. Model disimpan di `filesDir/models/model.gguf` (app-private,
gak butuh permission storage tambahan).

## Build — 100% via GitHub Actions, no Android Studio
Workflow `.github/workflows/build.yml` jalanin urutan ini otomatis tiap push
ke `main`:
1. Compile TypeScript (`web-src/app.ts`) → `app/src/main/assets/web/app.js`
2. Setup JDK 17 + Android SDK + NDK 26 + CMake 3.22.1
3. `gradle assembleDebug` — ini juga yang compile llama.cpp via CMake
4. Upload `app-debug.apk` sebagai artifact, tinggal download dari tab Actions

Trigger manual: tab **Actions → Build Dragonic Vernosa APK → Run workflow**.

## Catatan penting
- **llama.cpp API cepat berubah.** Kalau build gagal di step `gradle
  assembleDebug` dengan error soal fungsi `llama_*` yang gak ketemu/beda
  signature, cek [release llama.cpp terbaru](https://github.com/ggerganov/llama.cpp/releases),
  update `GIT_TAG` di `CMakeLists.txt`, lalu sesuaikan `llama_jni.cpp` kalau
  ada perubahan nama fungsi.
- ABI default cuma `arm64-v8a` (HP modern). Mau dukung HP 32-bit lama,
  tambahin `"armeabi-v7a"` di `app/build.gradle.kts` (`ndk.abiFilters`).
- Font Orbitron/JetBrains Mono pakai `local()` fallback ke system font.
  Untuk bener-bener offline-first tanpa tergantung font HP, drop file
  `.woff2`-nya ke `app/src/main/assets/web/fonts/` lalu update `@font-face`
  di `style.css` pake `url(...)`.
- CPU-only inference (`n_gpu_layers = 0`) — paling stabil di semua device,
  walau gak secepat kalau pakai GPU delegate.
- APK hasil build ini **debug, belum di-sign buat rilis**. Buat publish ke
  luar, perlu tambahin signing config + keystore secret di Actions.

## Struktur folder
```
DragonicVernosa/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/leonoretech/dragonicvernosa/
│       │   ├── MainActivity.kt
│       │   ├── WebBridge.kt
│       │   ├── ModelManager.kt
│       │   └── LlamaEngine.kt
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   └── llama_jni.cpp
│       └── assets/web/
│           ├── index.html
│           └── style.css
├── web-src/
│   ├── app.ts
│   ├── package.json
│   └── tsconfig.json
└── .github/workflows/build.yml
```
