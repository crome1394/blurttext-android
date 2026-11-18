package app.messages.data

/**
 * Pulls a one-time login or verification code out of a message.
 * A hit needs a nearby keyword and a standalone token, so a year, a price,
 * or a phone number in the same text is left alone.
 */
object VerificationCodes {
    private const val WINDOW = 60

    private val keywords =
        Regex(
            """(?i)(?<![A-Za-z0-9])(?:verification|verify|passcodes?|passwords?|codes?|otps?|pins?|log[\s-]?in|one[\s-]?time|2[\s-]?fa|two[\s-]?factor|security|auth)(?![A-Za-z0-9])""",
        )
    private val prefixed = Regex("""(?<![A-Za-z0-9])[A-Za-z]-(\d{4,8})(?!\d)""")
    private val hyphenated = Regex("""(?<![A-Za-z0-9])(\d{2,6})-(\d{2,6})(?!\d)""")
    private val plain = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
    private val mixed =
        Regex(
            """(?<![A-Za-z0-9])(?=[A-Za-z0-9]*\d)(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{6,8}(?![A-Za-z0-9])""",
        )
    private val dates = Regex("""\b(?:\d{1,4}[/-]\d{1,2}[/-]\d{1,4}|\d{4}-\d{2}-\d{2})\b""")
    private val monthYear =
        Regex(
            """(?i)\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{4}\b""",
        )
    private val localPhone = Regex("""^\d{3}-\d{4}$""")
    private val areaBefore = Regex("""(?:\+?\d[\d\s().-]{0,6}|\(\d{3}\)[\s.-]*)$""")
    private val yearFiller = Regex("""(?i)[\s:.#-]*(?:is|was)?[\s:.#-]*""")

    fun find(text: String): String? {
        if (text.isBlank()) return null
        val keys = keywords.findAll(text).map { it.range.first to it.range.last + 1 }.toList()
        if (keys.isEmpty()) return null
        val blocked = (dates.findAll(text) + monthYear.findAll(text)).map { it.range.first to it.range.last + 1 }.toList()
        val found = ArrayList<Hit>()

        fun add(
            code: String,
            start: Int,
            end: Int,
        ) {
            if (code.isEmpty()) return
            if (found.any { it.start < end && start < it.end }) return
            if (blocked.any { it.first < end && start < it.second }) return
            if (isMoney(text, start, end) || insideLink(text, start) || looksLikePhone(text, start, text.substring(start, end))) return
            if (!acceptable(text, code, start, keys)) return
            found.add(Hit(code, start, end))
        }
        for (match in prefixed.findAll(text)) {
            add(match.groupValues[1], match.range.first, match.range.last + 1)
        }
        for (match in hyphenated.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > 0 && text[start - 1] == '-') continue
            if (end < text.length && text[end] == '-') continue
            if (localPhone.matches(match.value)) continue
            val digits = match.groupValues[1] + match.groupValues[2]
            if (digits.length !in CODE_LENGTHS) continue
            add(digits, start, end)
        }
        for (match in plain.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            // A hyphen on either side means this run belongs to a phone number or a split code.
            if (start > 0 && text[start - 1] == '-') continue
            if (end < text.length && text[end] == '-') continue
            add(match.groupValues[1], start, end)
        }
        for (match in mixed.findAll(text)) {
            add(match.value, match.range.first, match.range.last + 1)
        }
        for (key in keys) {
            val near =
                found.mapNotNull { hit ->
                    val gap = gap(key.first, key.second, hit.start, hit.end)
                    if (gap > WINDOW) null else hit to gap
                }
            if (near.isEmpty()) continue
            return near
                .minWith(
                    compareBy({
                        it.second
                    }, { kotlin.math.abs(it.first.code.length - TYPICAL_CODE_LENGTH) }, { it.first.start }),
                ).first.code
        }
        return null
    }

    private data class Hit(
        val code: String,
        val start: Int,
        val end: Int,
    )

    private fun gap(
        aStart: Int,
        aEnd: Int,
        bStart: Int,
        bEnd: Int,
    ): Int =
        when {
            aEnd <= bStart -> bStart - aEnd
            bEnd <= aStart -> aStart - bEnd
            else -> 0
        }

    private fun acceptable(
        text: String,
        code: String,
        start: Int,
        keys: List<Pair<Int, Int>>,
    ): Boolean {
        if (isCalendarStamp(code)) return false
        if (code.length != YEAR_LENGTH) return true
        val year = code.toIntOrNull() ?: return true
        if (year !in PLAUSIBLE_YEARS) return true
        val key = keys.filter { it.second <= start }.minByOrNull { start - it.second } ?: return false
        val between = text.substring(key.second, start)
        return between.length <= YEAR_KEY_GAP && yearFiller.matches(between)
    }

    private fun isCalendarStamp(code: String): Boolean {
        if (code.length != DATE_STAMP_LENGTH || !code.all { it.isDigit() }) return false
        val year = code.substring(0, YEAR_LENGTH).toIntOrNull() ?: return false
        val month = code.substring(YEAR_LENGTH, MONTH_END).toIntOrNull() ?: return false
        val day = code.substring(MONTH_END, DATE_STAMP_LENGTH).toIntOrNull() ?: return false
        return year in PLAUSIBLE_YEARS && month in MONTHS && day in DAYS
    }

    private fun isMoney(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        if (start > 0 && text[start - 1] == ',') return true
        if (end < text.length && text[end] == ',') return true
        var i = start - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i >= 0 && text[i] in "$€£") return true
        if (i >= 2 && text.substring(i - 2, i + 1).equals("USD", ignoreCase = true)) return true
        if (end < text.length && text[end] == '.') {
            val cents =
                end + 2 < text.length && text[end + 1].isDigit() && text[end + 2].isDigit() &&
                    (end + CENTS_DIGITS + 1 >= text.length || !text[end + CENTS_DIGITS + 1].isDigit())
            if (cents) return true
        }
        return false
    }

    private fun insideLink(
        text: String,
        start: Int,
    ): Boolean {
        val from = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), start - 1).let { if (it < 0) 0 else it + 1 }
        val token = text.substring(from, start).lowercase()
        return token.startsWith("http://") || token.startsWith("https://") || token.startsWith("www.")
    }

    private fun looksLikePhone(
        text: String,
        start: Int,
        raw: String,
    ): Boolean {
        if (localPhone.matches(raw)) return true
        if (start > 0 && text[start - 1] == '+') return true
        val end = start + raw.length
        val digits = raw.count { it.isDigit() }
        // 800-555 inside 1-800-555-0199 is only a slice. A code that is the whole run is kept.
        if (phoneSpanDigits(text, start, end) > digits) return true
        if (digits < MIN_PHONE_DIGITS) return false
        val prefix = text.substring((start - AREA_LOOKBACK).coerceAtLeast(0), start)
        return areaBefore.containsMatchIn(prefix)
    }

    /** Digits joined to this token by spaces, dots, or hyphens, as in 1-800-555-0199. */
    private fun phoneSpanDigits(
        text: String,
        start: Int,
        end: Int,
    ): Int {
        val phone = "0123456789- ().".toSet()
        var a = start
        var b = end
        while (a > 0 && text[a - 1] in phone) a--
        while (b < text.length && text[b] in phone) b++
        return text.substring(a, b).count { it.isDigit() }
    }

    private const val TYPICAL_CODE_LENGTH = 6
    private const val YEAR_LENGTH = 4
    private const val MONTH_END = 6
    private const val DATE_STAMP_LENGTH = 8

    /** A year this close after a keyword ("code 2024") is filler, not a code. */
    private const val YEAR_KEY_GAP = 12
    private const val CENTS_DIGITS = 2
    private const val MIN_PHONE_DIGITS = 7

    /** How far back to look for an area code before a number. */
    private const val AREA_LOOKBACK = 12
    private val CODE_LENGTHS = 4..8
    private val PLAUSIBLE_YEARS = 1900..2099
    private val MONTHS = 1..12
    private val DAYS = 1..31
}
