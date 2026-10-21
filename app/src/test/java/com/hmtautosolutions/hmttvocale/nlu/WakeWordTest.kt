package com.hmtautosolutions.hmttvocale.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {
    @Test fun arabicYaGeely() {
        val (found, rest) = TextNormalizer.stripWakeWord("يا جيلي افتح النوافذ")
        assertTrue(found); assertEquals("افتح النوافذ", rest)
    }
    @Test fun heyGeely() = assertTrue(TextNormalizer.stripWakeWord("Hey Geely open YouTube").first)
    @Test fun frenchGeely() = assertTrue(TextNormalizer.stripWakeWord("Geely, ouvre les fenêtres").first)
}
