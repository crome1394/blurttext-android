# Backup and restore

Settings → Backup saves every text and picture message, with their photos, videos, and audio, in one ZIP file. Restore adds the messages from that file to any phone running BlurtText. Nothing leaves the phone unless you move the file yourself.

## Using it

| | |
|---|---|
| Back up | **Back up** opens the system "Save as" screen. Pick Downloads, an SD card, or a cloud folder. The file is written there directly, with no copy in the app. |
| Restore | **Restore** opens the system file picker. BlurtText must be the default SMS app, because only that app may write the message store. |
| Already there | A message already on the phone is skipped, so restoring twice, or onto a phone that has some of the messages, adds only what is missing. Nothing is replaced or deleted. |
| Damaged file | A damaged attachment is skipped and counted in the result. The other messages and attachments still restore. |
| Old backups | Version 1 backups (a JSON file of texts, from BlurtText 0.1.5 and older) still restore. |

## What is kept

| Texts | Picture messages |
|---|---|
| Address, body, time sent and received | Sender and every recipient (To, Cc) |
| Received, sent, failed, or queued | Inbox or sent, time, carrier message id, subject |
| Read and seen | Read and seen |
| | Every part: text, photo, GIF, video, audio, with its name and type |

Drafts and picture messages that were never downloaded are left out. The SIM a message used is not restored, because SIM ids differ between phones.

Conversations keep the date of their newest message. Android stamps a conversation with the time a message is inserted, so after restoring, BlurtText makes the provider recompute each date by adding and removing an empty draft.

## File format (version 2)

A ZIP file:

- `messages.json` is always the first entry. It holds every message (`BackupManifest` in `app/src/main/java/app/messages/data/BackupFormat.kt`) and the exporting phone's own numbers, so a new phone can tell its own address apart in group messages.
- `parts/<message>-<part>.<ext>` holds one attachment each, stored without compression. Photos and videos are already compressed, and a stored entry has a known length, so one damaged attachment cannot hide the ones after it.

Restore reads `messages.json`, creates the messages, then streams each attachment straight into the message store. It never needs a second copy of the backup on the phone.

## Tested

On an Android 12 emulator, with texts (read, unread, sent, emoji, quotes) and picture messages (JPEG, PNG, GIF): back up, wipe the message store, restore. The store matched the original row for row, and every attachment matched byte for byte. A second restore added nothing, a damaged attachment cost only itself, a version 1 file restored, and restoring without the default SMS role changed nothing.
