package pt.ismai.farol.domain

import java.net.IDN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookalikeTest {
    private fun brandOf(domain: String) = Lookalike.detect(domain)?.takeIf { it.kind == LookalikeKind.SIMILAR_TO_BRAND }?.brand

    @Test fun letterSwapsAndDigitsAreCaught() {
        assertEquals("paypal", brandOf("paypa1.com"))
        assertEquals("google", brandOf("g00gle.com"))
        assertEquals("microsoft", brandOf("rnicrosoft.com"))
        assertEquals("amazon", brandOf("arnazon.com"))
    }

    @Test fun oneLetterTypoIsCaught() {
        assertEquals("facebook", brandOf("facebok.com"))
        assertEquals("netflix", brandOf("netfliix.net"))
    }

    @Test fun brandInsideAHyphenatedNameIsCaught() {
        assertEquals("paypal", brandOf("paypal-login.com"))
        assertEquals("santander", brandOf("secure-santander.pt"))
    }

    @Test fun realBrandNamesAreNotFlagged() {
        assertNull(Lookalike.detect("google.com"))
        assertNull(Lookalike.detect("paypal.me"))
        assertNull(Lookalike.detect("www.google.pt"))
    }

    @Test fun unrelatedDomainsAreNotFlagged() {
        assertNull(Lookalike.detect("example.com"))
        assertNull(Lookalike.detect("ismai.pt"))
        assertNull(Lookalike.detect("github.io"))
    }

    @Test fun shortNamesAreNotTypoMatched() {
        assertNull(Lookalike.detect("appl.com"))
    }

    @Test fun mixedAlphabetsAreCaught() {
        // "a" in Cyrillic inside a Latin word
        val domain = IDN.toASCII("pаypal.com")
        assertEquals(LookalikeKind.MIXED_ALPHABETS, Lookalike.detect(domain)?.kind)
    }

    @Test fun wholeNameInAnotherAlphabetIsReported() {
        val domain = IDN.toASCII("арре.com")
        assertEquals(LookalikeKind.OTHER_ALPHABET, Lookalike.detect(domain)?.kind)
    }

    @Test fun accentedLatinNamesAreOrdinary() {
        assertNull(Lookalike.detect(IDN.toASCII("café.pt")))
    }

    @Test fun editDistanceHelper() {
        assertTrue(Lookalike.oneEditApart("paypal", "paypa"))
        assertTrue(Lookalike.oneEditApart("paypal", "paypall"))
        assertTrue(Lookalike.oneEditApart("paypal", "paypol"))
        assertFalse(Lookalike.oneEditApart("paypal", "paypal"))
        assertFalse(Lookalike.oneEditApart("paypal", "pyapal"))
    }
}
