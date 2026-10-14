package ovh.litapp.neurhome3.data.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The rows in `application_log_entries` can carry a legacy space-separated timestamp or the
 * ISO form the logger writes today. [HomeAppContextEncoder.parseLocalTimestamp] must accept
 * both; anything with a UTC offset is rejected, which is what makes
 * `PairwiseHomeAppTrainingDataFactory` drop such rows.
 */
class HomeAppContextEncoderTimestampTest {
    @Test
    fun legacySpaceSeparatedFormParsesLikeTheIsoForm() {
        val expected = LocalDateTime.of(2025, 4, 2, 9, 30)

        assertEquals(expected, HomeAppContextEncoder.parseLocalTimestamp("2025-04-02 09:30:00"))
        assertEquals(expected, HomeAppContextEncoder.parseLocalTimestamp("2025-04-02T09:30:00"))
    }

    @Test
    fun fractionalSecondsArePreserved() {
        assertEquals(
            LocalDateTime.of(2025, 4, 2, 9, 30, 0, 123_000_000),
            HomeAppContextEncoder.parseLocalTimestamp("2025-04-02T09:30:00.123")
        )
    }

    @Test
    fun offsetFormIsRejectedInsteadOfBeingConverted() {
        assertThrows(DateTimeParseException::class.java) {
            HomeAppContextEncoder.parseLocalTimestamp("2025-04-02T09:30:00+02:00")
        }
        assertThrows(DateTimeParseException::class.java) {
            HomeAppContextEncoder.parseLocalTimestamp("2025-04-02T09:30:00Z")
        }
    }
}
