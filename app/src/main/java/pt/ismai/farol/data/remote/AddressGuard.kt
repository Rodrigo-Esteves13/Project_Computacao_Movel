package pt.ismai.farol.data.remote

import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns

/** Thrown when a host resolves to an address that must not be requested from the phone. */
class BlockedAddressException(host: String) : IOException("$host resolves to a private or reserved address")

/**
 * Decides which addresses the app may contact. A public domain can point its DNS at the user's
 * own router or at the phone itself; requesting those would turn Farol into a scanner of the
 * local network, so private and reserved ranges are refused.
 */
object AddressGuard {

    fun isBlocked(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> blockedV4(address.address)
        is Inet6Address -> blockedV6(address)
        else -> true
    }

    /** True for a literal IP text that is private or reserved; false for anything else. */
    fun isBlockedLiteral(ip: String): Boolean {
        val looksLikeIp = ip.isNotBlank() &&
            ip.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' }
        if (!looksLikeIp) return false
        return runCatching { isBlocked(InetAddress.getByName(ip)) }.getOrDefault(true)
    }

    private fun blockedV4(b: ByteArray): Boolean {
        val a = b[0].toInt() and 0xFF
        val c = b[1].toInt() and 0xFF
        val d = b[2].toInt() and 0xFF
        return when {
            a == 0 || a == 10 || a == 127 -> true // this network, private, loopback
            a == 100 && c in 64..127 -> true // carrier-grade NAT
            a == 169 && c == 254 -> true // link-local
            a == 172 && c in 16..31 -> true // private
            a == 192 && c == 0 && d in 0..2 -> true // IETF protocol and documentation
            a == 192 && c == 168 -> true // private
            a == 198 && (c == 18 || c == 19) -> true // benchmarking
            a == 198 && c == 51 && d == 100 -> true // documentation
            a == 203 && c == 0 && d == 113 -> true // documentation
            a >= 224 -> true // multicast, reserved, broadcast
            else -> false
        }
    }

    private fun blockedV6(address: Inet6Address): Boolean {
        val b = address.address
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        val documentation = first == 0x20 && second == 0x01 &&
            b[2].toInt() == 0x0D && (b[3].toInt() and 0xFF) == 0xB8
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isMulticastAddress ||
            (first and 0xFE) == 0xFC || // unique local fc00::/7
            documentation
    }
}

/**
 * System DNS that refuses private answers. OkHttp connects to the addresses returned here, so the
 * check also covers every redirect hop and cannot be dodged by a DNS answer that changes between
 * a check and the connection.
 *
 * IPv4-mapped IPv6 answers (::ffff:a.b.c.d) are already returned by Java as plain IPv4 addresses.
 */
object GuardedDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any(AddressGuard::isBlocked)) throw BlockedAddressException(hostname)
        return addresses
    }
}
