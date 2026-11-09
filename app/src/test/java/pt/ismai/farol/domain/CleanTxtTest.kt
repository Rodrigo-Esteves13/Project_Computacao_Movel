package pt.ismai.farol.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanTxtTest {

    @Test
    fun joinsPiecesAndRemovesQuotes() {
        assertEquals("ab", "\"a\" \"b\"".cleanTxt())
    }

    @Test
    fun recordWithSingleQuotedPart() {
        assertEquals("v=spf1 include:_spf.google.com ~all", "\"v=spf1 include:_spf.google.com ~all\"".cleanTxt())
    }

    @Test
    fun longRecordSplitInThree() {
        assertEquals("v=spf1 ip4:1.2.3.4 -all", "\"v=spf1 \" \"ip4:1.2.3.4\" \" -all\"".cleanTxt())
    }

    @Test
    fun withoutQuotesStaysTheSame() {
        assertEquals("v=DMARC1; p=reject", "v=DMARC1; p=reject".cleanTxt())
        assertEquals("", "".cleanTxt())
    }
}
