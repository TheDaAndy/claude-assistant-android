# Native availability regression (API35)

Use the shared disposable `qa_api35` emulator, never a physical phone or production server. App code contains no test hooks. The instrumentation is a separate APK and seeds only a localhost test pairing.

1. Build the Assistant and run its JVM tests. Start the emulator with the sanctioned Android skill runner, keeping its parent process alive for the test.
2. Start Ankaï's `scripts/test-device-presence-server.mjs` with a fresh private `QA_FIXTURE_DIR`. It binds localhost on an ephemeral port and stores its test-only pairing token in `fixture.json` mode0600. Never print that token or upload it as evidence. Forward that port with `adb reverse`.
3. Set `ANKAI_QA_OUTPUT` to a shared project cache and `ANKAI_QA_SIGNING_PASS` to the existing SDK debug identity; run `python3 qa/device-presence/build.py`. Install Assistant + separate test APK; grant POST_NOTIFICATIONS. Start the instrumentation with `deviceId`, `token`, `baseUrl` arguments from the private fixture. It holds the target alive for10minutes; teardown ends it earlier. It does not ship with the app.
4. Enable `dev.claude.assistant/dev.claude.assistant.remote.UiControlService` on the emulator. Instrumentation startup force-stops its target, so reset/re-enable the test accessibility setting afterward if Android lists it as crashed. Do not use UIAutomator hierarchy dumps during accessibility checks.
5. Install the separate `qa/ui-control` fixture; set `ANKAI_QA_FIXTURE` to the private JSON and `ANKAI_QA_OUTPUT` to a report directory, then `python3 qa/device-presence/run.py`.

Checks: awake/unlocked ready; actual injected click causes an approximate interaction event; age decays; screen-off report and UI denial; wake/unlock; temporary dummy emulator PIN lock reported and not ready; credential cleanup; accessibility revoke/re-enable changes readiness. The test always attempts to clear its dummy PIN. No human-presence claim follows from these checks. Capture/inspect the current native settings screen for the disclosure text. Crash buffer must be empty. Stop the QA server and run the global Android teardown; never leave emulator/build daemons or test credentials behind.

## Service recovery regression (1.6.1)

Use an isolated emulator and localhost device-channel fixture returning HTTP200
and `{"command":null}` for GET, `{}` for POST. Never seed a production pairing.
Run instrumentation with `-e recovery true` and the fixture credentials; it exits
after enabling the channel. Open MainActivity again after instrumentation exits
(instrumentation termination itself is not an Android low-memory kill).
Check the active foreground DeviceChannelService, kill only the app PID using
`run-as dev.claude.assistant kill -9 <pid>`, and wait for a different PID with
foreground service restored. Do not use force-stop, which intentionally prevents
Android sticky restart. Run instrumentation with `-e inspectRecovery true` to
assert enabled survived, explicitly pause and assert enabled is now false.
Also reopen MainActivity after pausing and verify it does not restart the channel.
