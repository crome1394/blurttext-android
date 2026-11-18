package app.messages.data

import app.messages.sms.addressKey
import kotlinx.serialization.Serializable

/**
 * A BlurtText backup (version 2) is a ZIP file:
 *
 *  - `messages.json`, always the first entry: every text and picture message, see [BackupManifest].
 *  - `parts/<name>`: one entry per picture-message attachment, named by [BackupPart.file].
 *
 * The JSON comes first so an import can create the messages and then stream each
 * attachment straight into the message store without a temporary copy.
 * Version 1 backups were a bare JSON file of texts only ([BackupFile]) and still import.
 */
@Serializable
data class BackupManifest(
    val version: Int = BACKUP_VERSION,
    val app: String = "BlurtText",
    val createdAt: Long = 0,
    /** The exporting phone's own numbers, so a new phone can tell its own address apart in picture messages. */
    val ownNumbers: List<String> = emptyList(),
    val sms: List<BackupSms> = emptyList(),
    val mms: List<BackupMms> = emptyList(),
)

/** One row of the SMS store. [type] is Telephony.Sms.TYPE (inbox, sent, failed, queued). */
@Serializable
data class BackupSms(
    val address: String = "",
    val body: String = "",
    val date: Long = 0,
    val dateSent: Long = 0,
    val type: Int = 1,
    val read: Boolean = true,
    val seen: Boolean = true,
    val status: Int = -1,
)

/** One picture message. [date] is in seconds, as the MMS store keeps it. [box] is Telephony.Mms.MESSAGE_BOX. */
@Serializable
data class BackupMms(
    val box: Int = 1,
    val date: Long = 0,
    val dateSent: Long = 0,
    val read: Boolean = true,
    val seen: Boolean = true,
    val messageId: String = "",
    val subject: String = "",
    val addresses: List<BackupAddress> = emptyList(),
    val parts: List<BackupPart> = emptyList(),
)

/** [type] is the PDU address type: FROM, TO, CC, or BCC. */
@Serializable
data class BackupAddress(
    val address: String = "",
    val type: Int = 0,
    val charset: Int = 0,
)

/** A text part carries [text]. Any other part names its ZIP entry in [file]. */
@Serializable
data class BackupPart(
    val contentType: String = "",
    val name: String = "",
    val contentLocation: String = "",
    val contentId: String = "",
    val charset: Int = 0,
    val text: String? = null,
    val file: String? = null,
)

/** What an export wrote or an import added. */
data class BackupSummary(
    val texts: Int = 0,
    val pictureMessages: Int = 0,
    val attachments: Int = 0,
    val skipped: Int = 0,
    val damaged: Int = 0,
) {
    fun describe(verb: String): String {
        val pieces = mutableListOf(plural(texts, "text"), plural(pictureMessages, "picture message"))
        if (attachments > 0) pieces += plural(attachments, "attachment")
        val notes = mutableListOf<String>()
        if (skipped > 0) notes += "${plural(skipped, "message")} already here."
        if (damaged > 0) notes += "${plural(damaged, "attachment")} could not be read."
        val main = "$verb ${pieces.joinToString(", ")}"
        return if (notes.isEmpty()) main else "$main. ${notes.joinToString(" ")}"
    }
}

private fun plural(
    count: Int,
    noun: String,
): String = if (count == 1) "1 $noun" else "$count ${noun}s"

const val BACKUP_VERSION = 2
const val BACKUP_MANIFEST = "messages.json"
const val BACKUP_PARTS_DIR = "parts/"

/** A text already in the store matches on address, time, and body. */
fun smsBackupKey(
    address: String,
    date: Long,
    body: String,
): String = "${addressKey(address)}|$date|$body"

/** A picture message matches on box, time, and the carrier message id or, without one, its people. */
fun mmsBackupKey(
    box: Int,
    dateSeconds: Long,
    messageId: String,
    addresses: List<String>,
): String {
    val who = if (messageId.isNotBlank()) messageId else addresses.map { addressKey(it) }.sorted().joinToString(",")
    return "$box|$dateSeconds|$who"
}

/** True when [head] starts like a ZIP file ("PK\u0003\u0004"). */
fun looksLikeZip(head: ByteArray): Boolean = head.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { head[it] == ZIP_MAGIC[it] }

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Version 1 backup: a bare JSON file of texts. Still read by import. */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val messages: List<BackupMessage> = emptyList(),
)

@Serializable
data class BackupMessage(
    val address: String = "",
    val body: String = "",
    val timestamp: Long = 0,
    val incoming: Boolean = true,
    val type: String = "sms",
)
