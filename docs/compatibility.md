# Compatibility

| | |
|---|---|
| Android | 8.0 or newer (API 26), target API 36 |
| Hardware | A phone with telephony. Wi-Fi-only tablets are not a target. |
| Role | Must be the default SMS app to write the system message store |
| Install id | `app.crome1394.blurttext` (Kotlin sources stay in `app.messages`) |
| Carrier | No carrier SDK. Sending uses `SmsManager` and the MMSC, size limits, and proxy from the phone's own carrier configuration. On Android 8, where that API is missing, MMS falls back to a 300 KB / 1024 px budget. |

Layouts are in density-independent pixels. Rows, settings, and the action ribbon scroll instead of assuming a Pixel-sized screen. A narrow phone keeps the footer icons and shortens the unread label. The footer and composer stay above the navigation bar with gestures or three-button navigation. Tested on a Pixel 9 (Android 17) and on Android 9 and 12 emulators set up like a Galaxy S10.

## Samsung and other phones that stop background apps

| Problem | What to do |
| --- | --- |
| The computer inbox stops after a Wi-Fi change | Android 12 and later refuse to start it from the background. It starts again when you open BlurtText. **Settings → Permissions → Background use** lets it start by itself. |
| The Wi-Fi name is hidden, so no network can be allowed | Allow **Precise** location (Approximate is not enough) and turn on Location. The computer inbox page shows a button for whichever is missing. |

## Features that need a newer Android

Older phones keep everything else.

| Feature | Needs |
| --- | --- |
| Animated GIFs in a thread | Android 9. Android 8 shows a still frame. |
| In-app default-SMS prompt | Android 10 uses the system role. Android 8 and 9 use the older default-SMS screen. |
| Dynamic wallpaper color | Android 12 |
| Exact-alarm permission screen | Android 12 |
| System photo picker | Android 13. Older versions use the document picker. |
| Local-network permission for the computer inbox | Android 17 |

## Permissions

**Settings → Permissions** shows each of these on the phone, whether it is on, an **Allow** button, and **Open settings** for when Android stops showing its prompt.

| Permission | Why |
| --- | --- |
| SMS, MMS, WAP push | Send, receive, and store texts as the default SMS app |
| Contacts | Show names instead of numbers, and save a number from a thread |
| Phone state, phone numbers | SIM choice, and this phone's number so it is left out of group replies |
| Camera | Take a picture to attach |
| Notifications | New-message alerts and the computer-inbox pairing prompt |
| Location, Nearby devices | Android hides the Wi-Fi name without them; the name is compared with your allowlist on the phone. Choose Precise: with Approximate, Android still hides it. Location is also read when you choose to share your location in a message. |
| Local network (Android 17+) | Lets a computer on your Wi-Fi open the inbox |
| Internet, foreground service, Wi-Fi multicast | The computer-inbox server and the `blurttext.local` name |
| Exact alarms | Scheduled texts |
| Background use (battery optimization off) | Optional. Lets the computer inbox start by itself after a Wi-Fi change. |

## App icon

The chosen icon shows on the home screen, the recent-apps card, notification accents, and the computer inbox tab. Android's App info screen and the default-SMS chooser always show the original cyan icon, because apps cannot change those.
