package pt.ismai.farol.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.data.remote.AddressGuard

class AddressGuardTest {
    private fun blocked(ip: String) = AddressGuard.isBlockedLiteral(ip)

    @Test fun privateAndLoopbackV4_areBlocked() {
        listOf(
            "10.0.0.1", "10.255.255.255", "127.0.0.1", "172.16.0.1", "172.31.255.254",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255",
            "198.18.0.1", "192.0.2.10", "198.51.100.7", "203.0.113.9",
        ).forEach { assertTrue("$it should be blocked", blocked(it)) }
    }

    @Test fun publicV4_isAllowed() {
        listOf("8.8.8.8", "1.1.1.1", "94.61.84.23", "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1")
            .forEach { assertFalse("$it should be allowed", blocked(it)) }
    }

    @Test fun reservedV6_isBlocked() {
        listOf("::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "2001:db8::1", "::ffff:192.168.1.1")
            .forEach { assertTrue("$it should be blocked", blocked(it)) }
    }

    @Test fun publicV6_isAllowed() {
        assertFalse(blocked("2606:4700:4700::1111"))
        assertFalse(blocked("2001:4860:4860::8888"))
    }

    @Test fun textThatIsNotAnIp_isNotTreatedAsBlocked() {
        assertFalse(blocked(""))
        assertFalse(blocked("example.com"))
    }
}
