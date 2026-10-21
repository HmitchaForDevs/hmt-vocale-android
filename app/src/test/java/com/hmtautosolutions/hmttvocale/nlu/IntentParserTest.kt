package com.hmtautosolutions.hmttvocale.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    private fun type(s: String) = IntentParser.parse(s).type

    @Test fun wakeWordIsDetected() {
        assertTrue(TextNormalizer.stripWakeWord("Geely, ouvre YouTube.").first)
        assertTrue(TextNormalizer.stripWakeWord("جيلي، افتح يوتيوب").first)
        assertFalse(TextNormalizer.stripWakeWord("ouvre YouTube").first)
    }

    @Test fun openYoutubeInThreeLanguages() {
        for (s in listOf("Geely, ouvre YouTube.", "Geely, open YouTube.", "جيلي، افتح يوتيوب")) {
            val i = IntentParser.parse(s)
            assertEquals(s, IntentType.OPEN_APP, i.type)
            assertEquals("youtube", i.params["app"])
        }
    }

    @Test fun climateOnVariants() {
        for (s in listOf("J'ai chaud.", "Mets la clim", "Allume la climatisation.",
            "Il fait trop chaud dans la voiture", "Geely, turn on the air conditioning.", "جيلي، شغّل المكيّف")) {
            assertEquals(s, IntentType.CLIMATE_ON, type(s))
        }
    }

    @Test fun climateOffBeatsOn() {
        assertEquals(IntentType.CLIMATE_OFF, type("éteins la clim"))
    }

    @Test fun windows() {
        assertEquals(IntentType.WINDOWS_OPEN, type("Geely, ouvre les vitres."))
        assertEquals(IntentType.WINDOWS_OPEN, type("Geely, open the windows."))
        assertEquals(IntentType.WINDOWS_OPEN, type("جيلي، افتح النوافذ"))
        assertEquals(IntentType.WINDOWS_CLOSE, type("ferme les vitres"))
    }

    @Test fun identity() {
        assertEquals(IntentType.IDENTITY, type("Geely, qui a créé cette application ?"))
        assertEquals(IntentType.IDENTITY, type("Geely, who created this application?"))
        assertEquals(IntentType.IDENTITY, type("جيلي، من أنشأ هذا التطبيق؟"))
    }

    @Test fun volume() {
        assertEquals(IntentType.VOLUME_UP, type("monte le son"))
        assertEquals(IntentType.VOLUME_DOWN, type("turn down the volume"))
        val set = IntentParser.parse("volume à 40")
        assertEquals(IntentType.VOLUME_SET, set.type)
        assertEquals("40", set.params["value"])
    }

    @Test fun temperatureValue() {
        val i = IntentParser.parse("mets 22 degrés")
        assertEquals(IntentType.TEMP_SET, i.type)
        assertEquals("22", i.params["value"])
    }

    @Test fun navigation() {
        val i = IntentParser.parse("emmène-moi à Oran")
        assertEquals(IntentType.NAVIGATE, i.type)
        assertEquals("oran", i.params["destination"])
    }

    @Test fun languageChange() {
        val i = IntentParser.parse("parle anglais")
        assertEquals(IntentType.CHANGE_LANGUAGE, i.type)
        assertEquals("en", i.params["lang"])
    }

    @Test fun unsafeIsRefused() {
        assertEquals(IntentType.UNSAFE, type("coupe le moteur"))
        assertEquals(IntentType.UNSAFE, type("disable the brakes"))
    }

    @Test fun gibberishIsUnknown() {
        assertEquals(IntentType.UNKNOWN, type("le chat de ma voisine"))
        assertEquals(IntentType.UNKNOWN, type("Geely"))
    }

    @Test fun confirmation() {
        assertTrue(IntentParser.isYes("oui"))
        assertTrue(IntentParser.isYes("نعم"))
        assertTrue(IntentParser.isNo("no"))
        assertFalse(IntentParser.isYes("non"))
    }
}
