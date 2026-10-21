package com.hmtautosolutions.hmttvocale.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transcripts taken from real runs of the bundled offline models on spoken samples. */
class OfflineWakeTest {
    @Test fun frenchGeelyIsHeard() = assertTrue(TextNormalizer.grammarHit("gili [unk]", "fr"))
    @Test fun englishHeyGeelyIsHeard() = assertTrue(TextNormalizer.grammarHit("[unk] geely [unk]", "en"))
    @Test fun normalSentenceDoesNotWake() = assertFalse(TextNormalizer.grammarHit("[unk]", "fr"))
    @Test fun wakeLateInSentenceDoesNotWake() = assertFalse(TextNormalizer.grammarHit("[unk] [unk] jimmy", "fr"))
    @Test fun commandAfterMisheardWake() = assertEquals("allume la climatisation", TextNormalizer.stripOfflineWake("jimmy allume la climatisation"))
    @Test fun commandAfterHeyWake() = assertEquals("ouvre les vitres", TextNormalizer.stripOfflineWake("et jimmy ouvre les vitres"))
    @Test fun englishCommand() = assertEquals("pause the music", TextNormalizer.stripOfflineWake("neely pause the music"))
    @Test fun wakeOnly() = assertEquals("", TextNormalizer.stripOfflineWake("jimmy"))
}
