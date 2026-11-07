# Integrated Wireless Debugging QA

The separate instrumentation APK is never shipped. Build with `ANKAI_QA_OUTPUT=<temporary folder> ANKAI_QA_SIGNING_PASS=<debug keystore password> python3 qa/wireless-debugging/build.py` after `assembleDebug`.

Install both APKs on the shared API 35 emulator. Enable Wi-Fi and Android's Wireless Debugging, accept the local network prompt, and open the pairing-code dialog. Run the fixture with `am instrument -w -e port <connection port> -e pairPort <pairing port> -e code <ephemeral code> dev.claude.assistant.qa.wireless/dev.claude.assistant.qa.WirelessInstrumentation`. Keep codes out of persisted logs. An existing identity can be tested without the pairing arguments. `-e mode ui` opens the actual internal settings screen for visual inspection.

The fixture exercises real local ADB TLS/SPAKE2 pairing, validates actual UID 2000, separates stdout/stderr and exit status, verifies the remote timeout and 2048-byte output cap, rejects a closed bridge, and checks capability loss on disconnect. JVM tests additionally cover fragmented frames, malformed/oversized frames and missing exit status. No server token is used.

API 35 x86_64 emulator: passed 2026-11-07 with Assistant 1.11.0/build24; no app crash-buffer entries. UI screenshot inspected. FP5/HTC hardware, OEM restart behavior and Android 11–14 remain unverified. Pairing/connecting are explicit local actions after process death; there is no automatic discovery or boot autostart.

Shell commands remain within Android ADB permissions. Closing a stream does not guarantee immediate termination of deliberately detached jobs. A remote timeout bounds normal foreground commands, and uncertain results are never retried.

Dependency references: https://github.com/MuntashirAkon/libadb-android/tree/3.1.1 (Apache-2.0 OR GPL-3.0); Conscrypt 2.5.3 (Apache-2.0), Bouncy Castle 1.81 (MIT). SPAKE2 is a transitive native dependency; see upstream licensing. Debug APK includes arm64-v8a and x86_64 (also armv7/x86).

## Notification pairing regression (1.11.1/build25)

`-e mode notification` starts the real foreground pairing service and waits up to three minutes. The separate test APK registers the test-only `ankai.qa.PAIR_REPLY` broadcast to forward an ephemeral `code` extra through the actual notification RemoteInput PendingIntent. It is never registered by the shipped app. Keep Settings' pairing dialog open and pass the code without a port to prove actual local mDNS discovery of both pairing and connection endpoints. The test verifies TLS pairing, UID2000 and a real shell command. Invalid replies must leave the notification usable; no pairing code is printed or persisted by production code. Inspect the expanded notification with its inline text field and the underlying Settings activity.

API35 x86_64: actual code-only notification reply, discovery, pairing, connection and shell passed. No split screen used. Invalid replies, cancellation and stale PendingIntent rejection passed in the separate `notification-cancel` regression. The complete code-only flow also passed using the actual SystemUI inline editor, Android keyboard and Send button while Settings stayed open; no test broadcast used in that run. Notification permission denial and blocked-channel guidance are implemented; vendor-specific behavior remains a hardware check.

## Autostart / existing-consent reconnect (1.11.2/build26)

After a real local pairing, `-e mode recovery-seed` persists the test device grant (QA-only localhost endpoint) and ends the process. In a new instrumentation process, `-e mode recovery` verifies there is no in-memory ADB connection, starts the same recovery component used by DeviceChannelService and proves real local TLS reconnection using the saved identity. It executes a shell command, then verifies paused and revoked grants prevent a new connection. No pairing code is supplied in either recovery run. Identity/consent may be staged in the emulator only. The recovery component runs inside the existing user-enabled foreground device channel, with bounded mDNS scans/backoff and no permanent wake lock. Existing sticky-service, boot/update receivers and persisted periodic jobs restore the channel; Android force-stop/disabled debugging/first-unlock/OEM restrictions still apply. Do not reboot the user's physical phones to test this without authorization.

Verified API35/x86_64 on 2026-11-07: fresh process with saved identity/grant automatically reconnected over real local TLS, UID2000 shell command succeeded; pause and revoked consent blocked reconnect. A preliminary attempt with Android Wireless Debugging disabled correctly remained disconnected; recovery succeeded after enabling it locally in emulator Settings. No real phone reboot was performed.
