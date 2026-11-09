package pt.ismai.farol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.ismai.farol.domain.model.Target
import pt.ismai.farol.domain.model.TargetType

class TargetNormalizerTest {

    private fun domain(v: String) = Target(v, TargetType.DOMAIN)
    private fun ip(v: String) = Target(v, TargetType.IP)

    @Test
    fun fullUrlKeepsOnlyHostIncludingWww() {
        assertEquals(domain("www.github.com"), TargetNormalizer.parse("https://www.GitHub.com/explore"))
        assertEquals(domain("www.site.pt"), TargetNormalizer.parse("https://www.Site.pt/path?x=1"))
    }

    @Test
    fun removesSchemePortQueryFragmentAndSpaces() {
        assertEquals(domain("github.com"), TargetNormalizer.parse("  github.com  "))
        assertEquals(domain("github.com"), TargetNormalizer.parse("HTTP://github.com:8080/x"))
        assertEquals(domain("github.com"), TargetNormalizer.parse("github.com?q=1"))
        assertEquals(domain("github.com"), TargetNormalizer.parse("github.com#top"))
        assertEquals(domain("github.com"), TargetNormalizer.parse("github.com."))
    }

    @Test
    fun domainWithAccentsBecomesPunycode() {
        assertEquals(domain("xn--caf-dma.pt"), TargetNormalizer.parse("café.pt"))
        assertEquals(domain("xn--caf-dma.pt"), TargetNormalizer.parse("https://CAFÉ.pt/menu"))
    }

    @Test
    fun validIpv4() {
        assertEquals(ip("8.8.8.8"), TargetNormalizer.parse("8.8.8.8"))
        assertEquals(ip("0.0.0.0"), TargetNormalizer.parse("0.0.0.0"))
        assertEquals(ip("255.255.255.255"), TargetNormalizer.parse("255.255.255.255"))
        assertEquals(ip("1.1.1.1"), TargetNormalizer.parse("http://1.1.1.1:443/"))
    }

    @Test
    fun invalidIpv4ReturnsNull() {
        assertNull(TargetNormalizer.parse("1.2.3.256"))
        assertNull(TargetNormalizer.parse("256.1.1.1"))
        assertNull(TargetNormalizer.parse("1.2.3"))
        assertNull(TargetNormalizer.parse("1.2.3.4.5"))
        assertNull(TargetNormalizer.parse("01.2.3.4"))
    }

    @Test
    fun invalidInputsReturnNull() {
        assertNull(TargetNormalizer.parse(""))
        assertNull(TargetNormalizer.parse("   "))
        assertNull(TargetNormalizer.parse("https://"))
        assertNull(TargetNormalizer.parse("abc"))
        assertNull(TargetNormalizer.parse("localhost"))
        assertNull(TargetNormalizer.parse("git hub.com"))
        assertNull(TargetNormalizer.parse("site.c"))
        assertNull(TargetNormalizer.parse("site.123"))
        assertNull(TargetNormalizer.parse("-site.pt"))
        assertNull(TargetNormalizer.parse("site-.pt"))
        assertNull(TargetNormalizer.parse("a..pt"))
        assertNull(TargetNormalizer.parse("site_x.pt"))
    }

    @Test
    fun ipv6NotSupported() {
        assertNull(TargetNormalizer.parse("2001:4860:4860::8888"))
        assertNull(TargetNormalizer.parse("[::1]"))
    }

    @Test
    fun lengthLimits() {
        val label63 = "a".repeat(63)
        assertEquals(domain("$label63.pt"), TargetNormalizer.parse("$label63.pt"))
        assertNull(TargetNormalizer.parse("${"a".repeat(64)}.pt"))
        val tooLong = List(5) { "a".repeat(50) }.joinToString(".") + ".pt"
        assertNull(TargetNormalizer.parse(tooLong))
    }
}
