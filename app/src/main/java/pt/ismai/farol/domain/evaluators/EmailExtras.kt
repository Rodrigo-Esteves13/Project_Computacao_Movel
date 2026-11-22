package pt.ismai.farol.domain.evaluators

/**
 * MTA-STS and TLS-RPT: records that ask other mail servers to deliver mail to this domain over TLS
 * and to report failures. Informative only: they never change the email points. A value is null when
 * the lookup failed, so nothing is claimed about it.
 */
object EmailExtras {
    fun note(mtaSts: Boolean?, tlsRpt: Boolean?): String? {
        val lines = listOfNotNull(
            mtaSts?.let { if (it) "MTA-STS: present (mail servers are asked to use TLS)." else "MTA-STS: not set." },
            tlsRpt?.let { if (it) "TLS-RPT: present (delivery failures are reported)." else "TLS-RPT: not set." },
        )
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
