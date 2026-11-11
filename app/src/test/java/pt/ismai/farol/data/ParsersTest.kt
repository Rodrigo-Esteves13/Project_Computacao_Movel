package pt.ismai.farol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.Instant
import org.junit.Test
import pt.ismai.farol.data.remote.describeError
import pt.ismai.farol.data.remote.dto.DnsRecord
import pt.ismai.farol.data.remote.dto.DnsResponse
import pt.ismai.farol.data.remote.dto.RdapEntity
import pt.ismai.farol.data.remote.dto.RdapEvent
import pt.ismai.farol.data.repository.daysRemaining
import pt.ismai.farol.data.repository.daysSinceRegistration
import pt.ismai.farol.data.repository.firstIpv4
import pt.ismai.farol.data.repository.hostHierarchy
import pt.ismai.farol.data.repository.issuerName
import pt.ismai.farol.data.repository.mxServers
import pt.ismai.farol.data.repository.registrantName
import pt.ismai.farol.data.repository.txtWithPrefix
import pt.ismai.farol.data.repository.vcardValue

class ParsersTest {
    @Test fun firstIpv4_ignoresCnameAndInvalidIp() {
        val r = DnsResponse(0, listOf(
            DnsRecord("a.", 5, "b.example."),
            DnsRecord("b.", 1, "999.1.1.1"),
            DnsRecord("b.", 1, "140.82.121.4"),
        ))
        assertEquals("140.82.121.4", firstIpv4(r))
        assertNull(firstIpv4(DnsResponse(0, null)))
        assertNull(firstIpv4(null))
    }

    @Test fun txtWithPrefix_stripsQuotesAndJoinsPieces() {
        val r = DnsResponse(0, listOf(
            DnsRecord("x.", 16, "\"google-site-verification=abc\""),
            DnsRecord("x.", 16, "\"v=spf1 include:a.com \" \"~all\""),
        ))
        assertEquals("v=spf1 include:a.com ~all", txtWithPrefix(r, "v=spf1"))
        assertNull(txtWithPrefix(r, "v=DMARC1"))
    }

    @Test fun txtWithPrefix_ignoresCase() {
        val r = DnsResponse(0, listOf(DnsRecord("_dmarc.x.", 16, "\"v=dmarc1; p=reject\"")))
        assertEquals("v=dmarc1; p=reject", txtWithPrefix(r, "v=DMARC1"))
    }

    @Test fun hostHierarchy_climbsUpTo2LevelsKeepingTwoLabels() {
        assertEquals(listOf("www.github.com", "github.com"), hostHierarchy("www.github.com"))
        assertEquals(listOf("github.com"), hostHierarchy("github.com"))
        assertEquals(
            listOf("a.b.c.d.pt", "b.c.d.pt", "c.d.pt"),
            hostHierarchy("a.b.c.d.pt"),
        )
    }

    @Test fun mxServers_extractsNames() {
        val r = DnsResponse(0, listOf(
            DnsRecord("x.", 15, "10 ASPMX.L.GOOGLE.COM."),
            DnsRecord("x.", 15, "20 alt1.aspmx.l.google.com."),
            DnsRecord("x.", 15, "20 alt1.aspmx.l.google.com."),
        ))
        assertEquals(listOf("aspmx.l.google.com", "alt1.aspmx.l.google.com"), mxServers(r))
        assertTrue(mxServers(null).isEmpty())
    }

    @Test fun daysRemaining_negativeIfExpired() {
        val now = Instant.parse("2025-01-11T00:00:00Z")
        assertEquals(5L, daysRemaining(Instant.parse("2025-01-16T00:00:00Z"), now))
        assertEquals(-3L, daysRemaining(Instant.parse("2025-01-08T00:00:00Z"), now))
        assertNull(daysRemaining(null, now))
    }

    @Test fun issuerName_prefersOrganizationThenCn() {
        assertEquals("Google Trust Services", issuerName("CN=WR2,O=Google Trust Services,C=US"))
        assertEquals("R3", issuerName("CN=R3,C=US"))
        assertEquals("text", issuerName("text"))
        assertNull(issuerName(null))
    }

    @Test fun describeError_joinsWholeChain() {
        val error = javax.net.ssl.SSLHandshakeException("Chain validation failed")
            .apply { initCause(java.security.cert.CertificateExpiredException("NotAfter: 2020")) }
        val text = describeError(error)
        assertTrue(text.contains("SSLHandshakeException"))
        assertTrue(text.contains("Chain validation failed"))
        assertTrue(text.contains("CertificateExpiredException"))
        assertTrue(text.contains("NotAfter: 2020"))
    }

    @Test fun describeError_noMessageAndNoRepeats() {
        assertEquals("IOException", describeError(java.io.IOException()))
    }

    private val now = Instant.parse("2025-01-11T00:00:00Z")

    private fun event(action: String, date: String?) = RdapEvent(action, date)

    private fun json(s: String) = JsonParser.parseString(s).asJsonArray

    private fun entities(s: String) = Gson().fromJson(s, Array<RdapEntity>::class.java).toList()

    @Test fun daysSinceRegistration_countsWholeDays() {
        assertEquals(10L, daysSinceRegistration(listOf(event("registration", "2025-01-01T00:00:00Z")), now))
    }

    @Test fun daysSinceRegistration_acceptsOffsetAndPlainDate() {
        assertEquals(10L, daysSinceRegistration(listOf(event("registration", "2025-01-01T01:00:00+01:00")), now))
        assertEquals(10L, daysSinceRegistration(listOf(event("registration", "2025-01-01")), now))
    }

    @Test fun daysSinceRegistration_ignoresOtherEventsAndUsesOldest() {
        val events = listOf(
            event("last changed", "2000-01-01T00:00:00Z"),
            event("Registration", "2025-01-06T00:00:00Z"),
            event("registration", "2025-01-01T00:00:00Z"),
        )
        assertEquals(10L, daysSinceRegistration(events, now))
    }

    @Test fun daysSinceRegistration_noEventOrInvalidDate_null() {
        assertNull(daysSinceRegistration(null, now))
        assertNull(daysSinceRegistration(emptyList(), now))
        assertNull(daysSinceRegistration(listOf(event("registration", "garbage")), now))
        assertNull(daysSinceRegistration(listOf(event("registration", null)), now))
    }

    @Test fun daysSinceRegistration_futureDate_zero() {
        assertEquals(0L, daysSinceRegistration(listOf(event("registration", "2030-01-01T00:00:00Z")), now))
    }

    @Test fun registrantName_readsFnFromRegistrantEntity() {
        val list = entities(
            """[{"roles":["abuse"],"vcardArray":["vcard",[["fn",{},"text","Abuse Team"]]]},
                {"roles":["registrant"],"vcardArray":["vcard",[["version",{},"text","4.0"],["fn",{},"text","Google LLC"]]]}]""",
        )
        assertEquals("Google LLC", registrantName(list))
    }

    @Test fun registrantName_usesOrgAndNestedEntities() {
        val list = entities(
            """[{"roles":["administrative"],"entities":[
                {"roles":["registrant"],"vcardArray":["vcard",[["org",{},"text",["Org Lda","Dept"]]]]}]}]""",
        )
        assertEquals("Org Lda", registrantName(list))
        assertNull(registrantName(null))
    }

    @Test fun vcardValue_missingField_null() {
        assertNull(vcardValue(json("""["vcard",[["fn",{},"text","X"]]]"""), "org"))
    }
}
