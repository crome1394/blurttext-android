package app.messages.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.messages.sms.addressKey
import app.messages.sms.cleanMmsAddress
import app.messages.sms.placeGroup
import app.messages.util.attempt
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.PduHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Restores a backup (see [BackupManifest]) into the system message store. Messages already on the
 * phone are skipped. Each attachment is streamed from the ZIP straight into the store.
 * The caller must be the default SMS app, because only that app may write the store.
 */
class BackupRestorer(
    private val context: Context,
    private val ownNumbers: () -> List<String>,
) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }
    private val touchedThreads = HashSet<Long>()

    fun restore(input: InputStream): BackupSummary {
        val buffered = BufferedInputStream(input)
        buffered.mark(ZIP_HEAD_BYTES)
        val head = ByteArray(ZIP_HEAD_BYTES)
        val read = buffered.read(head)
        buffered.reset()
        val summary = if (read == ZIP_HEAD_BYTES && looksLikeZip(head)) restoreZip(buffered) else restoreLegacy(buffered)
        resolver.redateThreads(touchedThreads)
        touchedThreads.clear()
        return summary
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun restoreZip(input: InputStream): BackupSummary =
        ZipInputStream(input).use { zip ->
            val first = zip.nextEntry
            if (first?.name != BACKUP_MANIFEST) error("This is not a BlurtText backup")
            val manifest = json.decodeFromStream<BackupManifest>(KeepOpenInput(zip))
            if (manifest.version > BACKUP_VERSION) error("This backup is from a newer BlurtText. Update the app first.")
            val texts = insertSms(manifest.sms)
            val own = (manifest.ownNumbers + ownNumbers()).distinctBy { addressKey(it) }
            val pending = HashMap<String, Pair<Long, BackupPart>>()
            val pictures = insertMms(manifest.mms, own, pending)
            val (attachments, damaged) = restoreAttachments(zip, pending)
            BackupSummary(
                texts = texts,
                pictureMessages = pictures,
                attachments = attachments,
                skipped = (manifest.sms.size - texts) + (manifest.mms.size - pictures),
                damaged = damaged,
            )
        }

    /** Returns attachments restored and attachments that could not be read. */
    private fun restoreAttachments(
        zip: ZipInputStream,
        pending: MutableMap<String, Pair<Long, BackupPart>>,
    ): Pair<Int, Int> {
        var restored = 0
        var damaged = 0
        // A damaged ZIP can fail between entries. Keep what was restored and count the rest.
        var entry = attempt { zip.nextEntry }.getOrNull()
        while (entry != null) {
            val target = pending.remove(entry.name)
            if (target != null) {
                if (restoreAttachment(zip, entry.name, target.first, target.second)) restored += 1 else damaged += 1
            }
            entry = attempt { zip.nextEntry }.getOrNull()
        }
        return restored to damaged + pending.size
    }

    private fun restoreAttachment(
        zip: ZipInputStream,
        name: String,
        mmsId: Long,
        part: BackupPart,
    ): Boolean {
        val uri = resolver.insertPart(mmsId, part) ?: return false
        return attempt {
            resolver.openOutputStream(uri)?.use { zip.copyTo(it) } ?: error("No place to write the attachment")
        }.onFailure { t ->
            Log.w(TAG, "Attachment $name was not restored", t)
            resolver.delete(uri, null, null)
        }.isSuccess
    }

    /** Version 1: a bare JSON file of texts. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun restoreLegacy(input: InputStream): BackupSummary {
        val legacy =
            attempt { json.decodeFromStream<BackupFile>(input) }
                .getOrElse { error("This is not a BlurtText backup") }
        val sms =
            legacy.messages.filter { it.type == "sms" }.map {
                BackupSms(
                    address = it.address,
                    body = it.body,
                    date = it.timestamp,
                    type = if (it.incoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
                )
            }
        val texts = insertSms(sms)
        return BackupSummary(texts = texts, skipped = sms.size - texts)
    }

    private fun insertSms(messages: List<BackupSms>): Int {
        val existing = HashSet<String>()
        val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
        resolver.query(Telephony.Sms.CONTENT_URI, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                existing += smsBackupKey(c.text(Telephony.Sms.ADDRESS), c.long(Telephony.Sms.DATE), c.text(Telephony.Sms.BODY))
            }
        }
        return messages.count { insertOneSms(it, existing) }
    }

    private fun insertOneSms(
        sms: BackupSms,
        existing: MutableSet<String>,
    ): Boolean {
        if (sms.address.isBlank() || !existing.add(smsBackupKey(sms.address, sms.date, sms.body))) return false
        val values =
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sms.address)
                put(Telephony.Sms.BODY, sms.body)
                put(Telephony.Sms.DATE, sms.date)
                put(Telephony.Sms.DATE_SENT, sms.dateSent)
                put(Telephony.Sms.TYPE, sms.type)
                put(Telephony.Sms.READ, if (sms.read) 1 else 0)
                put(Telephony.Sms.SEEN, if (sms.seen) 1 else 0)
                put(Telephony.Sms.STATUS, sms.status)
            }
        val uri = resolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return false
        resolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) touchedThreads += c.getLong(0)
        }
        return true
    }

    private fun insertMms(
        messages: List<BackupMms>,
        own: List<String>,
        pending: MutableMap<String, Pair<Long, BackupPart>>,
    ): Int {
        val existing = resolver.existingMmsKeys()
        return messages.count { mms ->
            val key = mmsBackupKey(mms.box, mms.date, mms.messageId, mms.addresses.map { it.address })
            existing.add(key) && insertOneMms(mms, own, pending)
        }
    }

    private fun insertOneMms(
        mms: BackupMms,
        own: List<String>,
        pending: MutableMap<String, Pair<Long, BackupPart>>,
    ): Boolean {
        val thread = threadFor(mms, own) ?: return false
        val uri = resolver.insert(Telephony.Mms.CONTENT_URI, mmsValues(mms, thread)) ?: return false
        val mmsId = uri.lastPathSegment?.toLongOrNull() ?: return false
        touchedThreads += thread
        for (address in mms.addresses) resolver.insertAddress(mmsId, address)
        for (part in mms.parts) {
            val file = part.file
            if (file == null) resolver.insertPart(mmsId, part) else pending[file] = mmsId to part
        }
        return true
    }

    /** The conversation a restored picture message belongs to: its people without this phone. */
    private fun threadFor(
        mms: BackupMms,
        own: List<String>,
    ): Long? {
        val people = participants(mms, own)
        if (people.isEmpty()) return null
        return attempt { Telephony.Threads.getOrCreateThreadId(context, HashSet(people)) }
            .onFailure { t -> Log.w(TAG, "No conversation for a restored picture message", t) }
            .getOrNull()
    }

    /** Lets the JSON decoder read one ZIP entry without closing the ZIP. */
    private class KeepOpenInput(
        input: InputStream,
    ) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private companion object {
        const val TAG = "BackupRestorer"
        const val ZIP_HEAD_BYTES = 4
    }
}

private fun mmsValues(
    mms: BackupMms,
    thread: Long,
): ContentValues {
    val media = mms.parts.any { it.file != null && !it.contentType.equals("application/smil", true) }
    val inbox = mms.box == Telephony.Mms.MESSAGE_BOX_INBOX
    return ContentValues().apply {
        put(Telephony.Mms.THREAD_ID, thread)
        put(Telephony.Mms.MESSAGE_BOX, mms.box)
        put(Telephony.Mms.DATE, mms.date)
        put(Telephony.Mms.DATE_SENT, mms.dateSent)
        put(Telephony.Mms.READ, if (mms.read) 1 else 0)
        put(Telephony.Mms.SEEN, if (mms.seen) 1 else 0)
        if (mms.messageId.isNotBlank()) put(Telephony.Mms.MESSAGE_ID, mms.messageId)
        if (mms.subject.isNotBlank()) put(Telephony.Mms.SUBJECT, mms.subject)
        put(Telephony.Mms.MESSAGE_TYPE, if (inbox) PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF else PduHeaders.MESSAGE_TYPE_SEND_REQ)
        put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
        put(Telephony.Mms.TEXT_ONLY, if (media) 0 else 1)
    }
}

private fun participants(
    mms: BackupMms,
    own: List<String>,
): List<String> {
    val to = mms.addresses.filter { it.type == PduHeaders.TO }.map { it.address }
    val cc = mms.addresses.filter { it.type == PduHeaders.CC }.map { it.address }
    val people =
        if (mms.box == Telephony.Mms.MESSAGE_BOX_INBOX) {
            val from =
                mms.addresses
                    .firstOrNull { it.type == PduHeaders.FROM }
                    ?.address
                    .orEmpty()
            placeGroup(from, to, cc, own).participants
        } else {
            // A sent message lists its recipients in To and Cc. Keep them all, even when this phone's number is unknown.
            val ownKeys = own.map { addressKey(it) }.toSet()
            (to + cc).filter { addressKey(it).isNotEmpty() && addressKey(it) !in ownKeys }.map { cleanMmsAddress(it) }
        }
    val fallback = mms.addresses.map { cleanMmsAddress(it.address) }.filter { addressKey(it).isNotEmpty() }
    return people.ifEmpty { fallback }.distinctBy { addressKey(it) }
}

private fun ContentResolver.insertAddress(
    mmsId: Long,
    address: BackupAddress,
) {
    val values =
        ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address.address)
            put(Telephony.Mms.Addr.TYPE, address.type)
            put(Telephony.Mms.Addr.CHARSET, if (address.charset != 0) address.charset else CharacterSets.UTF_8)
        }
    insert(Uri.parse("content://mms/$mmsId/addr"), values)
}

private fun ContentResolver.insertPart(
    mmsId: Long,
    part: BackupPart,
): Uri? {
    val values =
        ContentValues().apply {
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            if (part.name.isNotBlank()) put(Telephony.Mms.Part.NAME, part.name)
            if (part.contentLocation.isNotBlank()) put(Telephony.Mms.Part.CONTENT_LOCATION, part.contentLocation)
            if (part.contentId.isNotBlank()) put(Telephony.Mms.Part.CONTENT_ID, part.contentId)
            if (part.charset != 0) put(Telephony.Mms.Part.CHARSET, part.charset)
            part.text?.let { put(Telephony.Mms.Part.TEXT, it) }
        }
    return insert(Uri.parse("content://mms/$mmsId/part"), values)
}

private fun ContentResolver.existingMmsKeys(): HashSet<String> {
    val out = HashSet<String>()
    val columns = arrayOf(Telephony.Mms._ID, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_ID)
    query(Telephony.Mms.CONTENT_URI, columns, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val messageId = c.text(Telephony.Mms.MESSAGE_ID)
            val addresses =
                if (messageId.isBlank()) readMmsAddresses(this, c.long(Telephony.Mms._ID)).map { it.address } else emptyList()
            out += mmsBackupKey(c.int(Telephony.Mms.MESSAGE_BOX), c.long(Telephony.Mms.DATE), messageId, addresses)
        }
    }
    return out
}

/**
 * The provider stamps a conversation with the time a message is inserted, so restored
 * conversations would all show the restore time. Deleting a message makes it recompute
 * the date from the newest message left, so add and remove an empty draft in each one.
 */
private fun ContentResolver.redateThreads(threads: Set<Long>) {
    for (thread in threads) {
        attempt {
            val draft =
                ContentValues().apply {
                    put(Telephony.Sms.THREAD_ID, thread)
                    put(Telephony.Sms.BODY, "")
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_DRAFT)
                }
            insert(Telephony.Sms.Draft.CONTENT_URI, draft)?.let { delete(it, null, null) }
        }.onFailure { t -> Log.w("BackupRestorer", "Conversation $thread keeps the restore time", t) }
    }
}
