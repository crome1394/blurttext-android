package app.messages.sms

/**
 * Wire markers shared by the MMS PDU readers. Values come from WAP-230-WSP
 * (section 8.4, header encoding) and OMA-TS-MMS-ENC (section 7). MMS header
 * field codes and charset numbers live in PduHeaders and CharacterSets.
 */
internal object Wsp {
    /** A byte at or above this is a short integer: a well-known value with the high bit set. */
    const val SHORT_INTEGER = 0x80

    /** Strips the short-integer flag. */
    const val SHORT_INTEGER_MASK = 0x7F

    /** A byte up to this is a short length that counts the bytes after it. */
    const val SHORT_LENGTH_MAX = 30

    /** Introduces a uintvar length. */
    const val LENGTH_QUOTE = 31

    /** The lowest byte that starts text. Anything below is a length. */
    const val TEXT_MIN = 32

    /** Optional prefix of a text value whose first byte would look like a short integer. */
    const val TEXT_QUOTE = 127

    /** Prefix of a quoted-string value. */
    const val QUOTED_STRING = 34

    /** A uintvar never needs more than five bytes for a 32-bit value. */
    const val UINTVAR_MAX_BYTES = 5

    /** Payload bits in each uintvar byte. */
    const val UINTVAR_BITS = 7

    /** Set on every uintvar byte except the last. */
    const val UINTVAR_CONTINUE = 0x80

    const val BYTE_BITS = 8
    const val BYTE_MASK = 0xFF

    /** Part header Content-Location (WSP field 0x0E with the short-integer flag). */
    const val PART_CONTENT_LOCATION = 0x8E

    /** Part header Content-ID (WSP field 0x40 with the short-integer flag). */
    const val PART_CONTENT_ID = 0xC0

    /** Well-known content-type parameter "charset". */
    const val PARAM_CHARSET = 0x01

    /** IANA MIB numbers that CharacterSets does not define. */
    const val MIB_UTF_16BE = 1013
    const val MIB_UTF_16LE = 1014
}
