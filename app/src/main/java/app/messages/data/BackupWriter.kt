package app.messages.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.messages.util.attempt
import com.google.android.mms.pdu_alt.PduHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a backup ZIP (see [BackupManifest]) from the system message store. Attachments are
 * copied straight from the store into the ZIP, so a large backup never needs a second copy.
 */
class BackupWriter(
    private val context: Context,
    private val ownNumbers: () -> List<String>,
) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(out: OutputStream): BackupSummary {
        val files = mutableListOf<Pair<String, Long>>()
        val manifest =
            BackupManifest(
                createdAt = System.currentTimeMillis(),
                ownNumbers = ownNumbers(),
                sms = readSms(),
                mms = readMms(files),
            )
        var attachments = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_MANIFEST))
            json.encodeToStream(manifest, KeepOpen(zip))
            zip.closeEntry()
            for ((name, partId) in files) {
                if (copyAttachment(zip, name, partId)) attachments += 1
            }
        }
        return BackupSummary(manifest.sms.size, manifest.mms.size, attachments)
    }

    private fun copyAttachment(
        zip: ZipOutputStream,
        name: String,
        partId: Long,
    ): Boolean =
        attempt {
            val entry = storedEntry(name, partId) ?: return@attempt false
            resolver.openInputStream(partUri(partId))?.use { input ->
                zip.putNextEntry(entry)
                input.copyTo(zip)
                zip.closeEntry()
                true
            } ?: false
        }.getOrElse { t ->
            Log.w(TAG, "Attachment $partId was not readable", t)
            false
        }

    /**
     * Attachments are stored, not compressed. Photos and videos are already compressed, and a stored
     * entry's length is known up front, so one damaged attachment cannot hide the ones after it.
     */
    private fun storedEntry(
        name: String,
        partId: Long,
    ): ZipEntry? {
        val crc = CRC32()
        var size = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        resolver.openInputStream(partUri(partId))?.use { input ->
            var read = input.read(buffer)
            while (read >= 0) {
                crc.update(buffer, 0, read)
                size += read
                read = input.read(buffer)
            }
        } ?: return null
        return ZipEntry(name).apply {
            method = ZipEntry.STORED
            this.size = size
            compressedSize = size
            this.crc = crc.value
        }
    }

    private fun readSms(): List<BackupSms> {
        val out = mutableListOf<BackupSms>()
        resolver
            .query(Telephony.Sms.CONTENT_URI, SMS_COLUMNS, null, null, "${Telephony.Sms.DATE} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    val type = c.int(Telephony.Sms.TYPE)
                    if (type != Telephony.Sms.MESSAGE_TYPE_DRAFT) out += smsFrom(c, type)
                }
            }
        return out
    }

    private fun readMms(files: MutableList<Pair<String, Long>>): List<BackupMms> {
        val out = mutableListOf<BackupMms>()
        resolver
            .query(Telephony.Mms.CONTENT_URI, MMS_COLUMNS, null, null, "${Telephony.Mms.DATE} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    if (keepMms(c)) out += mmsFrom(c, files)
                }
            }
        return out
    }

    private fun mmsFrom(
        c: Cursor,
        files: MutableList<Pair<String, Long>>,
    ): BackupMms {
        val id = c.long(Telephony.Mms._ID)
        return BackupMms(
            box = c.int(Telephony.Mms.MESSAGE_BOX),
            date = c.long(Telephony.Mms.DATE),
            dateSent = c.long(Telephony.Mms.DATE_SENT),
            read = c.int(Telephony.Mms.READ) != 0,
            seen = c.int(Telephony.Mms.SEEN) != 0,
            messageId = c.text(Telephony.Mms.MESSAGE_ID),
            subject = c.text(Telephony.Mms.SUBJECT),
            addresses = readMmsAddresses(resolver, id),
            parts = readParts(id, files),
        )
    }

    private fun readParts(
        mmsId: Long,
        files: MutableList<Pair<String, Long>>,
    ): List<BackupPart> {
        val out = mutableListOf<BackupPart>()
        resolver.query(Uri.parse("content://mms/$mmsId/part"), PART_COLUMNS, null, null, null)?.use { c ->
            while (c.moveToNext()) out += partFrom(c, mmsId, files)
        }
        return out
    }

    private fun partFrom(
        c: Cursor,
        mmsId: Long,
        files: MutableList<Pair<String, Long>>,
    ): BackupPart {
        val partId = c.long(Telephony.Mms.Part._ID)
        val type = c.text(Telephony.Mms.Part.CONTENT_TYPE)
        val index = c.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
        val text = if (c.isNull(index)) null else c.getString(index)
        // Text normally sits in its column. Anything else, or text kept in a file, goes in the ZIP.
        val file = if (text == null) "$BACKUP_PARTS_DIR$mmsId-$partId.${extensionFor(type)}" else null
        if (file != null) files += file to partId
        return BackupPart(
            contentType = type,
            name = c.text(Telephony.Mms.Part.NAME),
            contentLocation = c.text(Telephony.Mms.Part.CONTENT_LOCATION),
            contentId = c.text(Telephony.Mms.Part.CONTENT_ID),
            charset = c.int(Telephony.Mms.Part.CHARSET),
            text = text,
            file = file,
        )
    }

    /** Lets the JSON encoder write into the ZIP entry without closing the ZIP. */
    private class KeepOpen(
        out: OutputStream,
    ) : FilterOutputStream(out) {
        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = out.write(b, off, len)

        override fun close() = flush()
    }

    private companion object {
        const val TAG = "BackupWriter"
        val SMS_COLUMNS =
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.DATE_SENT,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN,
                Telephony.Sms.STATUS,
            )
        val MMS_COLUMNS =
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.DATE,
                Telephony.Mms.DATE_SENT,
                Telephony.Mms.READ,
                Telephony.Mms.SEEN,
                Telephony.Mms.MESSAGE_ID,
                Telephony.Mms.SUBJECT,
                Telephony.Mms.MESSAGE_TYPE,
            )
        val PART_COLUMNS =
            arrayOf(
                Telephony.Mms.Part._ID,
                Telephony.Mms.Part.CONTENT_TYPE,
                Telephony.Mms.Part.NAME,
                Telephony.Mms.Part.CONTENT_LOCATION,
                Telephony.Mms.Part.CONTENT_ID,
                Telephony.Mms.Part.CHARSET,
                Telephony.Mms.Part.TEXT,
            )
    }
}

/** The From/To/Cc rows of one picture message. */
internal fun readMmsAddresses(
    resolver: ContentResolver,
    mmsId: Long,
): List<BackupAddress> {
    val out = mutableListOf<BackupAddress>()
    val columns = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE, Telephony.Mms.Addr.CHARSET)
    resolver.query(Uri.parse("content://mms/$mmsId/addr"), columns, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            out += BackupAddress(c.text(Telephony.Mms.Addr.ADDRESS), c.int(Telephony.Mms.Addr.TYPE), c.int(Telephony.Mms.Addr.CHARSET))
        }
    }
    return out
}

internal fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun smsFrom(
    c: Cursor,
    type: Int,
) = BackupSms(
    address = c.text(Telephony.Sms.ADDRESS),
    body = c.text(Telephony.Sms.BODY),
    date = c.long(Telephony.Sms.DATE),
    dateSent = c.long(Telephony.Sms.DATE_SENT),
    type = type,
    read = c.int(Telephony.Sms.READ) != 0,
    seen = c.int(Telephony.Sms.SEEN) != 0,
    status = c.int(Telephony.Sms.STATUS),
)

/** Drafts are not messages yet, and a notification row has nothing downloaded to keep. */
private fun keepMms(c: Cursor): Boolean =
    c.int(Telephony.Mms.MESSAGE_BOX) != Telephony.Mms.MESSAGE_BOX_DRAFTS &&
        c.int(Telephony.Mms.MESSAGE_TYPE) != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND

private fun partUri(partId: Long): Uri = Uri.parse("content://mms/part/$partId")
