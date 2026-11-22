package pt.ismai.farol.domain.evaluators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailExtrasTest {
    @Test fun bothPresent() {
        val note = EmailExtras.note(mtaSts = true, tlsRpt = true)!!
        assertTrue(note.contains("MTA-STS: present"))
        assertTrue(note.contains("TLS-RPT: present"))
    }

    @Test fun missingOnesAreSaidSo() {
        val note = EmailExtras.note(false, false)!!
        assertEquals("MTA-STS: not set.\nTLS-RPT: not set.", note)
    }

    @Test fun unknownOnesAreLeftOut() {
        assertEquals("TLS-RPT: not set.", EmailExtras.note(null, false))
        assertNull(EmailExtras.note(null, null))
    }
}
