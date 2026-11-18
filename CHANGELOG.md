# Changelog

## Unreleased

- The chosen app icon is used on the home screen, the recent-apps card, message and computer-inbox notification accents, and the computer inbox tab. Android's App info screen and the default-SMS chooser always show the original icon, because apps cannot change those.
- The swipe colors use the theme instead of fixed red and green, the Nord dark error color is readable, and palette swatches have an outline so dark ones show on a dark page.
- Settings has a Permissions page: every permission, why BlurtText needs it, whether it is on, an Allow button, and Open settings for when Android stops asking. Approximate-only location is called out.
- About has a Privacy statement: no ads, analytics, telemetry, tracking, or spyware, and the four times BlurtText goes online. See [PRIVACY.md](PRIVACY.md).
- Backups include picture messages and their photos, videos, and audio, in one ZIP file saved with the system "Save as" screen. Restore keeps read state, sent and failed status, and conversation dates, skips messages already on the phone, and still reads older JSON backups. See [docs/backup.md](docs/backup.md).
- Every theme keeps button, bubble, and link text readable in light and dark mode. Sheets, menus, and fields use the theme colors instead of a lavender default, settings pages follow the theme instead of black, and dark mode no longer flashes white on launch.
- With three-button navigation, the inbox footer and the conversation composer sit above the Back, Home, and Recents buttons instead of under them.
- When Location is off or only approximate location is allowed, the computer inbox says so and offers a button for the setting, instead of showing no network and no QR code.
- Fixed a crash on Android 12 and later when Wi-Fi dropped while the computer inbox was on, and a second crash when Android restarted the app in the background.
- The computer address is https://blurttext.local and the port. Computers look up .local names on the Wi-Fi, so the name now opens without a DNS entry. The old blurttext.lan name never reached the phone.

## 0.1.5

- About and the test-APK link open the renamed github.com/crome1394/blurttext-android project.
- A shared file that hides the message store or the contact store behind a user folder is refused.
- Release builds are reproducible, ready for F-Droid.

## 0.1.4

- An update keeps the computer inbox certificate. If that certificate cannot be opened, the inbox creates a new one and the browser accepts it once.
- A shared file from the message store or the contact store is refused.
- The sideload file is a signed release build.

## 0.1.3

- The name is BlurtText on the phone, in settings, in notifications, and in the browser inbox.
- A like, love, or other reaction is shown on the original message.
- Swipe to delete, or to mark a thread read or unread, once the row has crossed about a quarter of the screen. A read thread offers Mark as unread. An unread thread offers Mark as read.
- New message opens in a card over the screen behind it.
- The computer inbox groups conversations with the same Group conversations setting as the phone.
- The computer address is https://blurttext.lan and the port.
- The install id is app.crome1394.blurttext. Kotlin code stays in app.messages.
- About opens https://github.com/crome1394/BlurtText.
- Footer icons grow with the footer height and sit at the top of the bar.
- A group reply that arrives with only the sender is filed on that person's recent group thread.

## 0.1.2

- The launcher name is BlurtText.
- The conversation list no longer draws a line between threads.
- The line under a name is the latest message. A picture label is used only when that message has no text.
- A group message stays in one thread. A reply goes to everyone in the group, and a reply from one of them comes back to that same thread.

## 0.1.1

- GIF tiles in the computer inbox load through the phone, so the pictures show in the browser.
- A text or picture sent from the computer inbox is saved in the conversation, including a text to your own number.
- The inbox filter has a Contacts heading above people and numbers.
- The composer offers a SIM choice only when more than one SIM is active.
- A new install starts from the current phone layout: Ocean, cyan launcher icon, striped inbox, search at the bottom, left swipe Delete, right swipe Unread, footer height 68, and Archive hidden on the ribbon. Dynamic color is off. The SIM chip and the sent/received line on each message are off.
- About links to the GitHub project, shows the Apache License 2.0, and links to the test APK.

## 0.1.0

- First published source. SMS and MMS, the conversation list, and the computer inbox.
- SMS and MMS through the system telephony provider, with no carrier-specific APN or MMSC.
- `minSdk` 26. Newer-only calls (role manager, carrier MMS limits, animated GIFs, dynamic color, the photo picker) are skipped or replaced on older releases.
- Inbox, conversation, swipe actions, ribbon, footer filter, themes, multi-SIM, scheduled texts, and a LAN computer inbox.
