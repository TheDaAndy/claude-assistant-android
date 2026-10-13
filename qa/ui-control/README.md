# Rootless UI native regression

This fixture is a separate APK, never part of the Assistant APK. Use an isolated localhost Ankaï TEST_MODE server and the shared API35 emulator. It contains a plain field, a dummy password, a click counter, scrolling content and a FLAG_SECURE toggle.

Build with `ANDROID_HOME`, `ANKAI_QA_OUTPUT` (shared checkout .tools cache) and `ANKAI_QA_SIGNING_PASS` for the existing SDK debug identity: `python3 qa/ui-control/build.py`. Install the fixture APK and the Assistant APK. Enable Android's Ankaï accessibility service through its native consent dialog, explicitly grant UI control in the Assistant, then activate the visible device channel. No Shizuku server or root required.

Create a projectless active test-run fixture in the isolated database, with owner matching the paired test account and a hashed run token. Set `ANKAI_UI_TEST_BASE` (localhost HTTP only), `ANKAI_UI_TEST_RUN_TOKEN`, `ANKAI_UI_TEST_SESSION_ID`, `ANKAI_UI_TEST_RUN_ID`, `ANKAI_UI_TEST_DEVICE_ID` and `ANKAI_UI_TEST_OUTPUT`. Run `python3 qa/ui-control/run.py`. Reset the fixture by force-stopping it between runs so FLAG_SECURE, scrolling and the counter return to their initial state. Android14+ required for the secure-window rejection assertion.

Checks: real run authentication; UI-only grant without shell; native launch/snapshot; password redaction; tap counter; text entry; password write denial; wrong-foreground denial; JPEG capture; secure-window rejection; swipe and Home. Output includes a screenshot: reopen and inspect it, do not infer visual acceptance from test success.

Also check native Pause (including disabling auto-resume), Android permission revocation and optional cold emulator reboot recovery. Android UIAutomator may temporarily suppress ordinary accessibility services, so do not run hierarchy dumps during the command-only test; observe via the tested Run API instead. Physical-device Doze/OEM/first-unlock/force-stop behavior remains separate acceptance work. Run global Android teardown at the end.
