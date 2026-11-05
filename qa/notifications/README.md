# Native inbox notifications (1.9.0 / build 22)

`./run-jvm-tests.sh` covers inbox parsing, cookie renewal, durable delivery receipts,
account isolation and same-origin links. Build using the shared Android SDK and
`./gradlew :app:assembleDebug --no-daemon`.

Native acceptance uses qa_api35 and the isolated local fixture:

1. Run `python3 qa/notifications/server.py`. It serves only loopback port 18764 and
   reads `/tmp/ankai-notification-fixture-state.json`. Never enter real credentials.
2. `adb reverse tcp:18764 tcp:18764`; link the debug app to
   `http://127.0.0.1:18764`, user `qa`, password `fixture`.
3. Enable **Ankaï-Benachrichtigungen auf diesem Handy**. Test denied and accepted
   Android notification permission. Device-control pairing is unnecessary.
4. Return Home. Write `{"notifications":[{"id":"qa-one","title":"Ankaï-Test",
   "body":"Dein Ergebnis ist da.","url":"/#personal","unread":true,"readAt":null}]}`
   to the state file. Within an active service poll (30 s) Android must display a
   real notification on channel `ankai_messages`, with title/body and a tap target.
5. Tap it: the native decision inbox opens. Leave the same row unread and restart
   the app/process; there must be no repeat alert or replacement of a dismissed item.
6. Add a second unread row and a third with `unread:false`/non-null `readAt`:
   only the second may appear. Mark the second read; its displayed item is removed.
7. Disable notifications locally: service and periodic job 8513 are removed,
   displayed inbox notifications disappear, new items stay silent.
8. Re-enable, run periodic job 8513 with `adb shell cmd jobscheduler run -f
   dev.claude.assistant 8513`, verify delivery. Check package-update/boot recovery
   while enabled, and that explicit disable remains disabled after recovery.
9. Inspect actual screenshots, resumed activity/process and `adb logcat -d -b crash`.
   Run `android-teardown` after every test/build session.

Delivery uses the authenticated existing `/api/notifications` endpoint. No Firebase,
Google services, external push server, new server deployment, remote-control token,
wake lock, or notification-listener permission is required. The user must opt in on
the phone. The foreground connection is visible and can be disabled in settings.
Android Doze/OEM limits, force-stop, no network and the server's latest-50-item
window limit delivery; the 15-minute system fallback is inexact. This is polling,
not a promise of instant push or lossless delivery during long offline periods.
Receipts are bounded to 4096 IDs per linked account and stored encrypted. Reading
or dismissing a native item does not mark the server inbox read.
