# Spanish Learning & Practice Application

A cross-platform Spanish 1A practice and drilling application built with **Compose Multiplatform** (Kotlin Multiplatform). This application provides interactive vocabulary translation, verb conjugation drills, and speech recognition/synthesis tools designed to build grammar fluency and automaticity.

---

## Features

- **Interactive Practice Modes**: Comprehensive drills covering vocabulary translation (English $\leftrightarrow$ Spanish) and verb conjugations.
- **Audio & Speech Features**:
  - **Text-to-Speech (TTS)**: Spoken prompts using native system TTS or [Piper TTS](https://github.com/rhasspy/piper).
  - **Speech Recognition (STT)**: Voice response parsing via [FasterWhisper](https://github.com/SYSTRAN/faster-whisper).
- **Customizable Drill Settings**: Configure app speech/writing modes and target pronoun/verb drill modes.
- **Cross-Platform**: Runs on Linux, macOS, Windows, and Android.

---

## Prerequisites for Linux

Before building or running the application on Linux, ensure your environment meets the following requirements:

1. **Java Development Kit (JDK 17+)**:
   - Ensure JDK 17 or later is installed and configured in your environment.
   - Example (Ubuntu/Debian):
     ```bash
     sudo apt update
     sudo apt install openjdk-17-jdk
     ```
   - Confirm installation:
     ```bash
     java -version
     ```

2. **Audio Playback Utilities**:
   - Standard audio playback servers (PulseAudio, PipeWire, or ALSA) are used for TTS playback. Ensure one of `paplay`, `pw-play`, or `aplay` is installed on your system (usually preinstalled on desktop distributions).
   - Example (Ubuntu/Debian):
     ```bash
     sudo apt install pulseaudio-utils  # provides paplay
     # OR
     sudo apt install alsa-utils        # provides aplay
     ```

3. **Python 3 (Optional - for Speech Recognition / FasterWhisper)**:
   - If using the local Whisper STT features, ensure Python 3 is available:
     ```bash
     sudo apt install python3 python3-venv
     ```

---

## Building and Running on Linux

The project uses the Gradle wrapper (`./gradlew`) included in the root directory.

### 1. Run the Desktop App (Development Mode)

To compile and launch the Compose Desktop application directly:

```bash
./gradlew :desktopApp:run
```

### 2. Build Linux Packages and Distributables

To package the application for distribution on Linux:

- **Create a Standalone Application Bundle**:
  ```bash
  ./gradlew :desktopApp:createDistributable
  ```
  The generated output will be located under `desktopApp/build/compose/binaries/main/app/`.

- **Create a `.deb` Installer Package (Debian / Ubuntu)**:
  ```bash
  ./gradlew :desktopApp:packageDeb
  ```
  The `.deb` installer file will be generated in `desktopApp/build/compose/binaries/main/deb/`.

### 3. Build the Android Application (Optional)

To build the Android debug APK:

```bash
./gradlew :androidApp:assembleDebug
```
The APK will be generated at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

---

## Project Structure

- `desktopApp/`: Entry point and packaging configuration for Compose Desktop.
- `androidApp/`: Android application launcher and activity configuration.
- `shared/`: Shared Kotlin Multiplatform logic containing UI components, view models, database schemas (SQLDelight), and platform audio abstraction layers.
- `PRACTICE_MODES.md`: Detailed specifications for language drill modes and pedagogical design.

---

## License

Distributed under the Apache 2.0 License. See `LICENSE.txt` for details.