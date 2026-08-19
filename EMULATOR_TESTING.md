# Emulator testing

The debug APK can be built, installed, and smoke-tested on a headless Android
emulator with one command:

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./scripts/run-emulator-smoke-test.sh
```

The default AVD is `qa_api28`, which exercises the app's oldest readily
available Android image close to its `minSdk 26`. The script verifies that the
AVD exists, builds the APK, boots the emulator when needed, installs the APK,
grants microphone permission, launches `MainActivity`, confirms the resumed
activity and app process, and checks the crash buffer. An emulator started by
the script is stopped afterward; an already running emulator is left running.

Use another installed AVD or port when required:

```bash
ANKAI_AVD_NAME=qa_api36 ANKAI_EMULATOR_PORT=5558 \
  ./scripts/run-emulator-smoke-test.sh
```

This smoke test proves installation and startup. Audio capture, assistant-role
selection, overlays, background/process-kill behavior, network interruption,
audio focus, and subjective TTS quality still require targeted interaction or
real-device tests.
