# HMT Vocale — Android voice assistant for Geely Coolray

Native Android app (Kotlin + XML) by **HMT Auto Solutions**, built for the Geely Coolray infotainment tablet (Android 9, API 28+). Landscape-first, black/red automotive design, French / English / Arabic (with right-to-left layout).

Package: `com.hmtautosolutions.hmttvocale` · minSdk 28 · targetSdk/compileSdk 34 · AGP 8.5.2 · Gradle 8.7 · JDK 17 · Kotlin 1.9.24

---

## 1. Download the APK (no Android Studio needed)

### From GitHub Actions (every push to `main`)
1. Open this repository on GitHub.
2. Click the **Actions** tab.
3. Click the latest **Android build** run with a green check mark.
4. Scroll down to **Artifacts** and click **HMT-Vocale-debug.apk** (you must be signed in to GitHub).
5. GitHub downloads a ZIP file. Extract it — inside is `HMT-Vocale-debug.apk`.

### From GitHub Releases (version tags)
When a tag like `v1.0.0` is pushed, the **Release APK** workflow attaches the APK to a release.
Open the **Releases** section on the right of the repository page, pick the version, and download `HMT-Vocale-v1.0.0-debug.apk` directly (no ZIP).

To create a release: on GitHub go to **Releases → Draft a new release → Choose a tag → type `v1.0.0` → Create new tag → Publish**.

## 2. Install on the Coolray tablet
1. Copy the APK to a USB stick (or download it on the tablet's browser).
2. Open it with the tablet's file manager.
3. If asked, allow **installation from unknown sources** for that file manager.
4. Open **HMT Vocale**, choose your language, tap **Allow microphone**.
5. Optional: enable hands-free "Geely" listening and start-at-boot.

> Some head-unit firmwares block installing APKs or hide the "unknown sources" option. That is a firmware restriction outside this app.

It is a **debug** build (signed with a debug key) for testing. A signed release build would use keystore credentials stored as GitHub Actions **secrets** — never commit keystores or passwords.

---

## 3. What works (implemented)

| Feature | Status |
|---|---|
| Setup: logo, welcome, language (FR/EN/AR), mic permission + explanation, optional AI config | Implemented |
| Main screen: animated circular waveform with idle / listening / processing / speaking / success / error states, recognized text, response, mic status, push-to-talk button | Implemented |
| Settings: language, voice on/off, speech rate, offline preference, wake word, background service, boot start, simulation mode, online AI, test mic, test voice, command list, vehicle capability table, diagnostics | Implemented, saved persistently |
| Arabic UI right-to-left | Implemented |
| Offline intent understanding (many phrasings per command, confidence score, parameters, clarification when unsure) | Implemented + unit tested |
| Open apps (YouTube, Spotify, Deezer, Anghami, Waze, Maps, Chrome, WhatsApp, Music, Settings) — clear message if not installed | Implemented |
| Navigation ("emmène-moi à Oran") via installed navigation app | Implemented |
| Media play / pause / next / previous (sent to the active player; reported as "sent", not "done") | Implemented |
| Volume up / down / mute / "volume 50" with read-back verification | Implemented |
| Identity, time, help answers, voice language change | Implemented |
| Unsafe requests (engine, brakes, airbag…) refused; opening windows requires spoken yes/no confirmation | Implemented |
| Text-to-speech replies in the chosen language, with detection of missing voice data | Implemented |
| Optional online AI (any OpenAI-compatible endpoint; key entered on the tablet, never in the APK/repo) for general questions only | Implemented |
| Foreground service + boot receiver for background listening (no window opened at boot) | Implemented |
| Vehicle commands via `VehicleCommandProvider`, with simulation mode clearly labelled | Interface + simulation |

## 4. Wake word "Geely" — honest status

- `WakeWordEngine` is a clean interface; the service and UI don't depend on a specific engine.
- The current engine (`SystemRecognizerWakeWordEngine`, **experimental**) runs the tablet's speech recognizer in a loop and genuinely checks each transcript for "Geely" (and recognizer spellings like "jili", "جيلي"). It does **not** record silently without detecting.
- Limitations: depending on the speech service installed, audio may be processed online; some recognizers play a beep each cycle; higher battery/CPU than a dedicated detector; accuracy with music/road noise is unknown until tested in the car.
- Android shows a **permanent notification** while background listening runs. This is required by Android and cannot be hidden.
- Push-to-talk always works as fallback.

**To get true low-power on-device wake word** (next step), one of:
1. **Picovoice Porcupine** — train a custom "Geely" keyword in the Picovoice Console (free tier for personal use, commercial license otherwise), add `ai.picovoice:porcupine-android`, enter the AccessKey in settings. Fully on-device.
2. **Vosk** (Apache 2.0) — on-device keyword spotting with a restricted grammar `["geely", "[unk]"]`; requires downloading a ~40–50 MB model per language on first run.
Either plugs into `WakeWordEngineFactory`.

## 5. Speech recognition — honest status
- Uses Android's `SpeechRecognizer` (normally Google's). French `fr-FR`, English `en-US`, Arabic `ar-DZ` (Algerian). Availability depends on what is installed on the tablet; errors such as missing language or no recognizer are shown clearly.
- "Prefer offline" is requested, but offline recognition only works if the offline language pack is installed — not guaranteed for Arabic.
- Some Algerian Darija phrases are included in the parser (e.g. "شكون دارك", "راني سخون", "سكر الشباك") but **have not been tested** with real speakers or recognizer output. Reliable Darija recognition is not claimed.

## 6. Geely Coolray vehicle control — honest status
No public, documented API from Geely for third-party apps to control climate or windows on the Coolray head unit was found. Standard Android 9 apps cannot access the vehicle CAN bus, and this project will **not** invent CAN frames, undocumented services, or bypass vehicle security.

| Function | Status |
|---|---|
| Air conditioning on/off | Not supported yet |
| Temperature | Not supported yet |
| Fan speed | Not supported yet |
| Windows | Not supported yet |

With **simulation mode ON** (default), vehicle commands go through the full flow and reply "Simulation only … no command was sent to the car". With it OFF, the assistant says the function isn't connected. It never reports a fake success.

Possible future paths (require hardware access and/or manufacturer cooperation):
- Check on the real tablet whether `android.car` / Car service (`CarHvacManager`, `CarPropertyManager`) exists and is accessible (`adb shell pm list packages`, `service list`).
- Look for a Geely/ECARX vehicle-service app exposing intents or bound services.
- Official manufacturer/dealer SDK or an authorised OBD/CAN gateway with documented commands.
Any of these becomes a new `VehicleCommandProvider` that must read back vehicle state before returning success.

## 7. Background & boot
- `BootReceiver` starts the foreground service at boot if setup is finished, background listening is ON and boot start is ON. No window opens; the assistant screen appears only after "Geely" or manual launch.
- `START_STICKY` asks Android to restart the service if it is killed. Some head-unit firmwares kill or block background apps / boot receivers — this must be tested on the real tablet.

## 8. Requires testing on the real Coolray
- Install permissions / unknown sources on the firmware
- Speech recognizer availability for FR/EN/AR and offline packs
- Wake-word accuracy with engine/road/music noise
- Boot auto-start and service survival
- Volume control (some head units use a fixed or vendor-managed volume)
- Media key routing to the car's media player
- Screen layout on the tablet's actual resolution
- Any vehicle integration

## 9. Project structure
```
app/src/main/java/com/hmtautosolutions/hmttvocale/
  ui/        MainActivity, SetupActivity, SettingsActivity, WaveformView
  nlu/       TextNormalizer, IntentParser, AppCatalog (command & app registry)
  commands/  CommandExecutor (real Android actions, honest outcomes)
  speech/    SpeechInput (SpeechRecognizer), Tts (TextToSpeech)
  wake/      WakeWordEngine interface + experimental engine
  service/   ListeningService (foreground, microphone), BootReceiver
  vehicle/   VehicleCommandProvider, Simulated/Unavailable providers
  ai/        OnlineAiProvider (optional, OpenAI-compatible)
```
Add an app: one line in `AppCatalog`. Add a phrase: add it to a rule in `IntentParser` and a test in `IntentParserTest`.

## 10. Build locally (optional)
Install JDK 17, Android SDK and Gradle 8.7, then `gradle testDebugUnitTest assembleDebug`. (No Gradle wrapper jar is committed; CI installs Gradle 8.7 directly. Run `gradle wrapper --gradle-version 8.7` locally to add one.)

## Offline voice (v1.1.0)

- « Geely » is detected **on the tablet** with the free [Vosk](https://alphacephei.com/vosk/) small models (Apache 2.0), bundled into the APK at build time by `scripts/fetch-models.sh` (models are not committed).
- French and English commands are recognized offline. Arabic commands still need the tablet's system speech recognizer (no small Arabic Vosk model exists); in Arabic mode the French model listens for « يا جيلي ».
- If the models are missing, the app falls back to the system recognizer loop.
- Not yet tested on a real Coolray: accuracy with road noise and music must be checked in the car.
