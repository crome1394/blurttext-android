package app.messages.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun manifestSurvivesARoundTrip() {
        val manifest =
            BackupManifest(
                createdAt = 1_700_000_000_000,
                ownNumbers = listOf("+15550001111"),
                sms = listOf(BackupSms("+15551234567", "Hi \"there\"\nline two", 1_700_000_000_000, type = 2, read = false)),
                mms =
                    listOf(
                        BackupMms(
                            box = 1,
                            date = 1_700_000_000,
                            messageId = "abc",
                            addresses = listOf(BackupAddress("+15551234567", 137, 106)),
                            parts =
                                listOf(
                                    BackupPart(contentType = "text/plain", text = "caption", charset = 106),
                                    BackupPart(contentType = "image/jpeg", name = "p.jpg", file = "parts/1-2.jpg"),
                                ),
                        ),
                    ),
            )
        val decoded = json.decodeFromString<BackupManifest>(json.encodeToString(manifest))
        assertEquals(manifest, decoded)
    }

    @Test
    fun versionOneBackupsStillDecode() {
        val legacy = """{"version":1,"messages":[{"address":"5551234","body":"old","timestamp":5,"incoming":false,"type":"sms"}]}"""
        val decoded = json.decodeFromString<BackupFile>(legacy)
        assertEquals(1, decoded.messages.size)
        assertFalse(decoded.messages.first().incoming)
    }

    @Test
    fun aTextMatchesAcrossNumberFormats() {
        assertEquals(smsBackupKey("+1 (555) 123-4567", 10, "hi"), smsBackupKey("5551234567", 10, "hi"))
        assertNotEquals(smsBackupKey("5551234567", 10, "hi"), smsBackupKey("5551234567", 11, "hi"))
    }

    @Test
    fun aPictureMessageMatchesOnItsCarrierIdOrItsPeople() {
        assertEquals(mmsBackupKey(1, 100, "m1", listOf("a")), mmsBackupKey(1, 100, "m1", listOf("b")))
        assertEquals(
            mmsBackupKey(1, 100, "", listOf("+15551234567", "5550001111")),
            mmsBackupKey(1, 100, "", listOf("5550001111", "5551234567")),
        )
        assertNotEquals(mmsBackupKey(1, 100, "", listOf("5551234567")), mmsBackupKey(2, 100, "", listOf("5551234567")))
    }

    @Test
    fun zipFilesAreToldApartFromJson() {
        assertTrue(looksLikeZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertFalse(looksLikeZip("{\"version\":1}".toByteArray()))
        assertFalse(looksLikeZip(byteArrayOf(0x50, 0x4B)))
    }

    @Test
    fun summaryReadsNaturally() {
        assertEquals("Saved 1 text, 2 picture messages, 3 attachments", BackupSummary(1, 2, 3).describe("Saved"))
        assertEquals(
            "Restored 0 texts, 0 picture messages. 5 messages already here.",
            BackupSummary(skipped = 5).describe("Restored"),
        )
        assertEquals(
            "Restored 2 texts, 1 picture message. 1 attachment could not be read.",
            BackupSummary(texts = 2, pictureMessages = 1, damaged = 1).describe("Restored"),
        )
    }
}
