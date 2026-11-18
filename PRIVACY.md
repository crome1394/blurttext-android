# Privacy

BlurtText has no ads, analytics, crash reporting, telemetry, or tracking, and no spyware. There is no account and no BlurtText server. The same statement is in the app under Settings → About → Privacy.

## What stays on the phone

Messages live in Android's own message store, the same place every SMS app uses. BlurtText's settings, trash list, and scheduled texts live in its private app storage. Android backup is off (`allowBackup="false"`), so none of it goes to a Google backup. A BlurtText backup file goes only where you save it.

## When BlurtText goes online

| What | Where it goes | When |
|---|---|---|
| Texts and picture messages | Your carrier | When you send, or one arrives |
| GIF search | KLIPY (`api.klipy.com`) | Only after you add your own KLIPY key, and only when you search |
| Computer inbox | Browsers on your Wi-Fi | Only on Wi-Fi networks you allow, after you approve the computer |
| Links | Your browser | Only links you tap |

Nothing else. The app has no analytics or advertising library: its dependencies are AndroidX, Jetpack Compose, kotlinx.serialization, libphonenumber, ZXing (QR codes), Ktor's certificate helper, and NanoHTTPD (the computer inbox server).

## Location

Android only reveals the Wi-Fi name to apps with precise location. BlurtText reads it to check that the computer inbox runs only on networks you allowed. It reads your position only when you tap Share location in a conversation, and then it puts a map link in that text. Coordinates are never stored or sent anywhere else.

## Permissions

Settings → Permissions lists every permission, why it is needed, and whether it is on. The same list is in [docs/compatibility.md](docs/compatibility.md).

## Checking it

The source is open. Every network call is in `external/lantext/app/src/main/java/app/lantext/sms/GifSearch.kt` (KLIPY), the carrier MMS code, and the computer inbox server in `external/lantext/app/src/main/java/app/lantext/net/`.
