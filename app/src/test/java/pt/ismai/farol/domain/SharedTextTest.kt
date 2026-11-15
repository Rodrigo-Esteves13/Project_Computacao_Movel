package pt.ismai.farol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    @Test fun plainLink_givesTheHost() {
        assertEquals("example.com", SharedText.extractTarget("https://example.com/path?x=1"))
    }

    @Test fun linkInsideASentence_isFound() {
        assertEquals("example.com", SharedText.extractTarget("Look at this https://Example.com/a, it is great!"))
    }

    @Test fun linkWinsOverAnEarlierBareDomain() {
        assertEquals("real.org", SharedText.extractTarget("not.this see https://real.org now"))
    }

    @Test fun bareDomainAndIp_areAccepted() {
        assertEquals("github.com", SharedText.extractTarget("github.com"))
        assertEquals("8.8.8.8", SharedText.extractTarget("check 8.8.8.8 please"))
    }

    @Test fun parenthesesAndTrailingDotAreIgnored() {
        assertEquals("example.com", SharedText.extractTarget("(see https://example.com)."))
    }

    @Test fun textWithoutAHost_givesNull() {
        assertNull(SharedText.extractTarget("hello there, how are you?"))
        assertNull(SharedText.extractTarget("   "))
        assertNull(SharedText.extractTarget(null))
    }

    @Test fun unsafeSchemes_areNotAccepted() {
        assertNull(SharedText.extractTarget("javascript:alert(1)"))
    }
}
