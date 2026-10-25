# Native session recovery regression

Use only an isolated emulator. Start `python3 qa/auth-recovery/server.py` on the
host (loopback18771). Build/install the normal debug app and separate
qa/device-presence instrumentation APK using its build.py.

Run `am instrument -w -e authRecovery true -e baseUrl http://10.0.2.2:18771
 dev.claude.assistant.qa.presence/dev.claude.assistant.qa.PresenceInstrumentation`.
This seeds dummy encrypted account credentials, an expired session, project p1,
and a paused dummy device pairing. It opens the real MainActivity and asserts
new persisted cookie, preserved default/pairing and no visible login error.
GET `/qa/requests` must show rejected-cookie -> Basic -> fresh-cookie for project
read. Reopen MainActivity after instrumentation exits, inspect current UI and
corrected device recovery hint. Never seed a production account or print secrets.
