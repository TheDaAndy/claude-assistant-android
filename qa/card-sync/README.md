# Saved decision response regression

Use only the shared QA emulator with fixture credentials. Run `python3 qa/card-sync/server.py` on the host, build/install the app and the separate `qa/device-presence` instrumentation APK, then:

```
adb shell am instrument -w -e cardSync true -e baseUrl http://10.0.2.2:18765 dev.claude.assistant.qa.presence/dev.claude.assistant.qa.PresenceInstrumentation
```

The fixture delays dashboard GET by eight seconds after a reply. Instrumentation asserts that the local inbox drops the answered card within 2.5 seconds and saves `/sdcard/Download/card-sync-verified.png`. It never uses production credentials. Pair emulator use with `android-teardown`.
