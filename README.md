# NK APP — Native Android Application

**NK APP** is a native Android application built with **Kotlin** and **Jetpack Compose (Material 3)**. It integrates an in-app **Google Search browser with Brave-style content blocking** and a **Google Gemini AI Assistant** backed by a secure proxy, operating under a strict **Zero-History Privacy** architecture.

---

## Key Features

1. **Native Material 3 Homepage**
   - Clean, minimal design with the official NK APP logo.
   - One-tap access to **AI Assistant** and **Google Search**.
   - Full light and dark theme support.

2. **Google Gemini AI Integration**
   - Seamless conversational AI supporting **Hindi, English, and Hinglish**.
   - Handles coding, mathematics, writing, and general queries with formatted Markdown.
   - In-memory conversation session with a "New Chat" button to wipe active context.
   - Built with realistic timeouts, graceful quota handling, and streaming.
   - Credentials remain server-side on the backend proxy (`GEMINI_API_KEY`), never embedded in the APK.

3. **In-App Google Search Browser**
   - Full native browser view powered by Android WebView.
   - Dedicated search bar with support for Google Web, Images, Videos, News, and Maps shortcuts.
   - Navigation controls: Back, Forward, Reload, Home, and live loading progress bar.
   - Native error recovery screens with retry options.
   - Safe scheme handling (blocks unsafe protocols, safely opens mailto/tel/geo intents).

4. **Brave-Style Ad & Tracker Shield**
   - Real-time network interception blocking known ad networks, telemetry trackers, and analytics beacons (EasyList / EasyPrivacy patterns).
   - Injected cosmetic stylesheet hiding intrusive ad containers and sponsored placeholders.
   - Live shield counter badge displaying actual blocked requests.
   - Interactive Shields sheet showing blocked categories and individual blocked hostnames.

5. **Strict No-History Privacy**
   - Zero local database storage for chat messages or web visits.
   - Cookies, session storage, and web cache cleared on exit or when selecting "Clear Session".
   - No tracking SDKs, analytics libraries, or third-party ad networks.

---

## Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/
│   │   │   ├── blocker/              # Brave-style content filtering engine & rules
│   │   │   │   ├── ContentBlockerEngine.kt
│   │   │   │   └── FilterLists.kt
│   │   │   ├── data/                 # Gemini API service & network models
│   │   │   │   ├── GeminiRepository.kt
│   │   │   │   └── model/
│   │   │   │       ├── BlockedItem.kt
│   │   │   │       └── ChatMessage.kt
│   │   │   ├── ui/
│   │   │   │   ├── components/       # Shields sheet, Markdown renderer, Dialogs
│   │   │   │   │   ├── MarkdownText.kt
│   │   │   │   │   ├── PrivacyInfoDialog.kt
│   │   │   │   │   └── ShieldsBottomSheet.kt
│   │   │   │   ├── screens/          # Home, Chat, and Browser screens
│   │   │   │   │   ├── BrowserScreen.kt
│   │   │   │   │   ├── ChatScreen.kt
│   │   │   │   │   └── HomeScreen.kt
│   │   │   │   ├── theme/            # Material 3 Color, Type, and Theme
│   │   │   │   └── viewmodels/       # MVVM StateFlow ViewModels
│   │   │   │       ├── BrowserViewModel.kt
│   │   │   │       └── ChatViewModel.kt
│   │   │   └── MainActivity.kt       # Root Activity & navigation router
│   │   └── res/                      # Drawables, mipmap adaptive icons, strings
│   └── build.gradle.kts
├── server/                           # Secure Node.js Gemini backend proxy
│   ├── package.json
│   └── server.js
├── .env.example                      # Backend and client configuration
├── metadata.json
└── settings.gradle.kts
```

---

## Build & Run Instructions

### 1. Configure the Secure Backend

1. In `.env` (or server environment variables):
   ```env
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   GEMINI_MODEL=gemini-2.5-flash
   PORT=3000
   ```
2. Start the proxy server:
   ```bash
   cd server
   node server.js
   ```

### 2. Open in Android Studio

1. Launch **Android Studio** and select **Open**.
2. Choose this project root directory.
3. Allow Gradle to sync.

### 3. Build the APK

- **From Android Studio**: Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
- **From Command Line**:
  ```bash
  gradle assembleDebug
  ```
  The generated APK is located at: `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Install & Test

1. Connect your Android device via USB or start an emulator.
2. Install the APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
3. Test **AI Assistant** in English, Hindi, and Hinglish.
4. Test **Google Search**, verify the Shields counter increments when blocking ads and trackers, and confirm zero browsing history is saved upon restart.
