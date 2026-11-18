package app.messages.sms

import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.PduHeaders

/**
 * Reads an m-retrieve-conf PDU (OMA-TS-MMS-ENC) far enough to store its parts.
 * The platform download writes this PDU to a file. The telephony provider does
 * not parse that file, so the inbox has to.
 */
object MmsRetrieveParser {
    data class Part(
        val mime: String,
        val name: String,
        val bytes: ByteArray,
        val charset: Int = UTF_8,
    ) {
        fun text(): String = bytes.toString(charsetFor(charset))
    }

    data class Retrieved(
        val from: String,
        val dateSeconds: Long,
        val parts: List<Part>,
        val to: List<String> = emptyList(),
        val cc: List<String> = emptyList(),
    )

    fun parse(pdu: ByteArray?): Retrieved? {
        if (pdu == null || pdu.size < MIN_PDU_SIZE) return null
        return try {
            read(pdu)
        } catch (_: Exception) {
            null
        }
    }

    private fun read(pdu: ByteArray): Retrieved? {
        var i = 0
        var type = -1
        var from = ""
        var date = 0L
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        val limit = pdu.size
        while (i < limit) {
            val field = u8(pdu, i)
            if (field < Wsp.SHORT_INTEGER) return null
            i += 1
            when (field) {
                PduHeaders.MESSAGE_TYPE -> {
                    type = u8(pdu, i)
                    i += 1
                }

                PduHeaders.MMS_VERSION, PduHeaders.PRIORITY, PduHeaders.DELIVERY_REPORT, PduHeaders.READ_REPORT -> {
                    i += 1
                }

                PduHeaders.TRANSACTION_ID, PduHeaders.MESSAGE_ID -> {
                    i = readText(pdu, i, limit).next
                }

                PduHeaders.DATE -> {
                    val number = readLong(pdu, i, limit)
                    date = number.value
                    i = number.next
                }

                PduHeaders.FROM -> {
                    val parsed = readFrom(pdu, i, limit)
                    from = parsed.value
                    i = parsed.next
                }

                PduHeaders.TO, PduHeaders.CC -> {
                    val parsed = readEncoded(pdu, i, limit)
                    val address = cleanAddress(parsed.value)
                    if (address.isNotBlank() && !address.equals("insert-address-token", true)) {
                        if (field == PduHeaders.TO) to += address else cc += address
                    }
                    i = parsed.next
                }

                PduHeaders.CONTENT_TYPE -> {
                    if (type != PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF) return null
                    i = skipContentType(pdu, i, limit)
                    val parts = readParts(pdu, i, limit)
                    return Retrieved(cleanAddress(from), date, parts, to, cc)
                }

                else -> {
                    i = skipValue(pdu, i, limit)
                }
            }
        }
        return null
    }

    private fun readParts(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): List<Part> {
        val count = readUintvar(pdu, start, limit)
        var i = count.next
        val total = count.value.coerceIn(0L, MAX_PARTS).toInt()
        val out = ArrayList<Part>(total)
        repeat(total) {
            val headerLen = readUintvar(pdu, i, limit)
            val dataLen = readUintvar(pdu, headerLen.next, limit)
            val headerStart = dataLen.next
            val headerEnd = headerStart + headerLen.value
            val dataEnd = headerEnd + dataLen.value
            if (headerEnd > limit || dataEnd > limit) return out
            val part = readPart(pdu, headerStart, headerEnd.toInt(), dataEnd.toInt(), out.size)
            if (part != null) out += part
            i = dataEnd.toInt()
        }
        return out
    }

    private fun readPart(
        pdu: ByteArray,
        start: Int,
        headerEnd: Int,
        dataEnd: Int,
        index: Int,
    ): Part? {
        val typed = readContentType(pdu, start, headerEnd)
        var mime = typed.mime.lowercase()
        val charset = typed.charset
        var i = typed.next
        var name = ""
        while (i < headerEnd) {
            val field = u8(pdu, i)
            if (field >= Wsp.SHORT_INTEGER) {
                i += 1
                when (field) {
                    Wsp.PART_CONTENT_LOCATION, Wsp.PART_CONTENT_ID -> {
                        val text = readText(pdu, i, headerEnd)
                        val value =
                            text.value
                                .trim()
                                .trim('"')
                                .trim('<', '>')
                        if (name.isBlank() && value.isNotBlank()) name = value
                        i = text.next
                    }

                    else -> {
                        i = skipValue(pdu, i, headerEnd)
                    }
                }
            } else if (field < Wsp.TEXT_MIN) {
                i = skipLength(pdu, i, headerEnd)
            } else {
                i = readText(pdu, i, headerEnd).next
                if (i < headerEnd) i = skipValue(pdu, i, headerEnd)
            }
        }
        if (mime.isBlank() || mime == "application/smil") return null
        if (name.isBlank()) name = "part$index"
        val bytes = pdu.copyOfRange(headerEnd, dataEnd)
        if (mime == "text/plain" && bytes.isEmpty()) return null
        return Part(mime, name.substringAfterLast('/').ifBlank { name }, bytes, charset)
    }

    private data class Typed(
        val mime: String,
        val next: Int,
        val charset: Int,
    )

    private data class Text(
        val value: String,
        val next: Int,
    )

    private data class Number(
        val value: Long,
        val next: Int,
    )

    private fun skipContentType(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Int = readContentType(pdu, start, limit).next

    private fun readContentType(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Typed {
        val marker = u8(pdu, start)
        if (marker >= Wsp.SHORT_INTEGER) return Typed(mimeOf(marker and Wsp.SHORT_INTEGER_MASK), start + 1, UTF_8)
        if (marker <= Wsp.LENGTH_QUOTE) {
            val end = skipLength(pdu, start, limit)
            val contents = lengthContents(pdu, start)
            if (contents >= end) return Typed("application/octet-stream", end, UTF_8)
            val media = readMediaToken(pdu, contents, end)
            return Typed(media.mime, end, charsetParameter(pdu, media.next, end))
        }
        val text = readText(pdu, start, limit)
        return Typed(text.value.ifBlank { "application/octet-stream" }, text.next, UTF_8)
    }

    private data class Media(
        val mime: String,
        val next: Int,
    )

    private fun readMediaToken(
        pdu: ByteArray,
        start: Int,
        end: Int,
    ): Media {
        if (start >= end) return Media("application/octet-stream", start)
        val marker = u8(pdu, start)
        if (marker >= Wsp.SHORT_INTEGER) return Media(mimeOf(marker and Wsp.SHORT_INTEGER_MASK), start + 1)
        val text = readText(pdu, start, end)
        return Media(text.value.ifBlank { "application/octet-stream" }, text.next)
    }

    private fun charsetParameter(
        pdu: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        var i = start
        var charset = UTF_8
        while (i < end) {
            val marker = u8(pdu, i)
            if (marker >= Wsp.SHORT_INTEGER) {
                val value = readTypedValue(pdu, i + 1, end)
                if (marker and Wsp.SHORT_INTEGER_MASK == Wsp.PARAM_CHARSET) charset = value.mib ?: mibForName(value.text) ?: UTF_8
                i = value.next
            } else if (marker < Wsp.TEXT_MIN) {
                val next = skipLength(pdu, i, end)
                if (next <= i) break
                i = next
            } else {
                val name = readText(pdu, i, end)
                val value = readTypedValue(pdu, name.next, end)
                if (name.value.equals("charset", ignoreCase = true)) {
                    charset = value.mib ?: mibForName(value.text) ?: UTF_8
                }
                i = value.next
            }
        }
        return charset
    }

    private data class TypedValue(
        val mib: Int?,
        val text: String,
        val next: Int,
    )

    private fun readTypedValue(
        pdu: ByteArray,
        start: Int,
        end: Int,
    ): TypedValue {
        if (start >= end) return TypedValue(null, "", start)
        val marker = u8(pdu, start)
        if (marker >= Wsp.SHORT_INTEGER) return TypedValue(marker and Wsp.SHORT_INTEGER_MASK, "", start + 1)
        if (marker in 1..Wsp.SHORT_LENGTH_MAX && start + 1 + marker <= end) {
            var value = 0
            for (offset in 1..marker) value = (value shl Wsp.BYTE_BITS) or u8(pdu, start + offset)
            return TypedValue(value, "", start + 1 + marker)
        }
        val text = readText(pdu, start, end)
        return TypedValue(mibForName(text.value), text.value, text.next)
    }

    private fun readFrom(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Text {
        val end = skipLength(pdu, start, limit)
        val valueStart = lengthContents(pdu, start)
        if (valueStart >= end) return Text("", end)
        val token = u8(pdu, valueStart)
        val address =
            when (token) {
                PduHeaders.FROM_INSERT_ADDRESS_TOKEN -> ""
                PduHeaders.FROM_ADDRESS_PRESENT_TOKEN -> readEncoded(pdu, valueStart + 1, end).value
                else -> readEncoded(pdu, valueStart, end).value
            }
        return Text(address, end)
    }

    private fun readEncoded(
        pdu: ByteArray,
        start: Int,
        end: Int,
    ): Text {
        if (start >= end) return Text("", start)
        val marker = u8(pdu, start)
        if (marker <= Wsp.LENGTH_QUOTE) {
            val block = skipLength(pdu, start, end)
            val contents = lengthContents(pdu, start)
            val text = if (contents < block) readText(pdu, contents + 1, block) else Text("", block)
            return Text(text.value, block)
        }
        return readText(pdu, start, end)
    }

    private fun readText(
        pdu: ByteArray,
        start: Int,
        end: Int,
    ): Text {
        var i = start
        if (i < end && u8(pdu, i) == Wsp.TEXT_QUOTE) i += 1
        if (i < end && u8(pdu, i) == Wsp.QUOTED_STRING) i += 1
        val begin = i
        while (i < end && pdu[i].toInt() != 0) i += 1
        val value = pdu.copyOfRange(begin, i.coerceAtMost(end)).toString(Charsets.UTF_8)
        if (i < end && pdu[i].toInt() == 0) i += 1
        return Text(value, i.coerceAtMost(end))
    }

    private fun readLong(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Number {
        val len = u8(pdu, start)
        if (start + 1 + len > limit) error("mms length")
        var value = 0L
        for (offset in 1..len) value = (value shl Wsp.BYTE_BITS) or u8(pdu, start + offset).toLong()
        return Number(value, start + 1 + len)
    }

    private fun skipValue(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Int {
        if (start >= limit) return start
        val marker = u8(pdu, start)
        return when {
            marker >= Wsp.SHORT_INTEGER -> start + 1
            marker <= Wsp.LENGTH_QUOTE -> skipLength(pdu, start, limit)
            else -> readText(pdu, start, limit).next
        }
    }

    private fun skipLength(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Int {
        val marker = u8(pdu, start)
        val end =
            when {
                marker < Wsp.LENGTH_QUOTE -> {
                    start + 1 + marker
                }

                marker == Wsp.LENGTH_QUOTE -> {
                    val length = readUintvar(pdu, start + 1, limit)
                    val next = length.next.toLong() + length.value
                    if (next > Int.MAX_VALUE) error("mms length")
                    next.toInt()
                }

                else -> {
                    start
                }
            }
        if (end <= start || end > limit) error("mms length")
        return end
    }

    private fun lengthContents(
        pdu: ByteArray,
        start: Int,
    ): Int {
        val marker = u8(pdu, start)
        return if (marker == Wsp.LENGTH_QUOTE) readUintvar(pdu, start + 1, pdu.size).next else start + 1
    }

    private fun readUintvar(
        pdu: ByteArray,
        start: Int,
        limit: Int,
    ): Number {
        var value = 0L
        var i = start
        repeat(Wsp.UINTVAR_MAX_BYTES) {
            if (i >= limit) return Number(value, i)
            val byte = u8(pdu, i)
            i += 1
            value = (value shl Wsp.UINTVAR_BITS) or (byte and Wsp.SHORT_INTEGER_MASK).toLong()
            if (byte and Wsp.UINTVAR_CONTINUE == 0) return Number(value, i)
        }
        return Number(value, i)
    }

    /** Well-known content types (WAP-230-WSP table 40) that MMS uses. */
    private val wellKnownTypes =
        mapOf(
            0x02 to "text/html",
            0x03 to "text/plain",
            0x1C to "image/*",
            0x1D to "image/gif",
            0x1E to "image/jpeg",
            0x20 to "image/png",
            0x21 to "image/vnd.wap.wbmp",
            0x23 to "application/vnd.wap.multipart.mixed",
            0x33 to "application/vnd.wap.multipart.related",
            0x4F to "audio/*",
            0x50 to "video/*",
        )

    private fun mimeOf(index: Int): String = wellKnownTypes[index] ?: "application/octet-stream"

    private const val UTF_8 = CharacterSets.UTF_8
    private const val MIN_PDU_SIZE = 4
    private const val MAX_PARTS = 16L

    /** ISO-8859-n has MIB number n + 3 for n in 1..9. */
    private const val ISO_8859_MIB_OFFSET = 3

    private fun charsetFor(mib: Int): java.nio.charset.Charset =
        when (mib) {
            CharacterSets.US_ASCII -> {
                Charsets.US_ASCII
            }

            in CharacterSets.ISO_8859_1..CharacterSets.ISO_8859_9 -> {
                java.nio.charset.Charset
                    .forName("ISO-8859-${mib - ISO_8859_MIB_OFFSET}")
            }

            CharacterSets.SHIFT_JIS -> {
                java.nio.charset.Charset
                    .forName("Shift_JIS")
            }

            CharacterSets.UTF_8 -> {
                Charsets.UTF_8
            }

            CharacterSets.UCS2, Wsp.MIB_UTF_16BE -> {
                Charsets.UTF_16BE
            }

            Wsp.MIB_UTF_16LE -> {
                Charsets.UTF_16LE
            }

            CharacterSets.UTF_16 -> {
                Charsets.UTF_16
            }

            else -> {
                Charsets.UTF_8
            }
        }

    private fun mibForName(name: String): Int? {
        val clean = name.trim().lowercase().replace('_', '-')
        when (clean) {
            "utf-8" -> return CharacterSets.UTF_8
            "us-ascii", "ascii" -> return CharacterSets.US_ASCII
            "shift-jis" -> return CharacterSets.SHIFT_JIS
            "utf-16" -> return CharacterSets.UTF_16
            "utf-16be" -> return Wsp.MIB_UTF_16BE
            "utf-16le" -> return Wsp.MIB_UTF_16LE
            "ucs-2", "iso-10646-ucs-2" -> return CharacterSets.UCS2
            "latin-1", "latin1" -> return CharacterSets.ISO_8859_1
        }
        val iso = Regex("iso-8859-(\\d+)").matchEntire(clean) ?: return null
        val part = iso.groupValues[1].toIntOrNull() ?: return null
        return if (part in ISO_8859_PARTS) part + ISO_8859_MIB_OFFSET else null
    }

    private fun cleanAddress(raw: String): String = raw.substringBefore("/").trim().trim('<', '>')

    private fun u8(
        pdu: ByteArray,
        index: Int,
    ): Int {
        if (index < 0 || index >= pdu.size) error("mms pdu ended early")
        return pdu[index].toInt() and Wsp.BYTE_MASK
    }
}

/** ISO-8859 parts with a MIB number in CharacterSets. */
private val ISO_8859_PARTS = 1..9
