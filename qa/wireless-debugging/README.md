# Integrated Wireless Debugging QA

The separate instrumentation APK is never shipped. Build with `ANKAI_QA_OUTPUT=<temporary folder> ANKAI_QA_SIGNING_PASS=<debug keystore password> python3 qa/wireless-debugging/build.py` after `assembleDebug`.

Install both APKs on the shared API 35 emulator. Enable Wi-Fi and Android's Wireless Debugging, accept the local network prompt, and open the pairing-code dialog. Run the fixture with `am instrument -w -e port <connection port> -e pairPort <pairing port> -e code <ephemeral code> dev.claude.assistant.qa.wireless/dev.claude.assistant.qa.WirelessInstrumentation`. Keep codes out of persisted logs. An existing identity can be tested without the pairing arguments. `-e mode ui` opens the actual internal settings screen for visual inspection.

The fixture exercises real local ADB TLS/SPAKE2 pairing, validates actual UID 2000, separates stdout/stderr and exit status, verifies the remote timeout and 2048-byte output cap, rejects a closed bridge, and checks capability loss on disconnect. JVM tests additionally cover fragmented frames, malformed/oversized frames and missing exit status. No server token is used.

API 35 x86_64 emulator: passed 2026-11-07 with Assistant 1.11.0/build24; no app crash-buffer entries. UI screenshot inspected. FP5/HTC hardware, OEM restart behavior and Android 11–14 remain unverified. Pairing/connecting are explicit local actions after process death; there is no automatic discovery or boot autostart.

Shell commands remain within Android ADB permissions. Closing a stream does not guarantee immediate termination of deliberately detached jobs. A remote timeout bounds normal foreground commands, and uncertain results are never retried.

Dependency references: https://github.com/MuntashirAkon/libadb-android/tree/3.1.1 (Apache-2.0 OR GPL-3.0); Conscrypt 2.5.3 (Apache-2.0), Bouncy Castle 1.81 (MIT). SPAKE2 is a transitive native dependency; see upstream licensing. Debug APK includes arm64-v8a and x86_64 (also armv7/x86).
