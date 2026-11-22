package pt.ismai.farol.data.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pt.ismai.farol.data.remote.AddressGuard
import pt.ismai.farol.data.remote.BlockedAddressException
import pt.ismai.farol.data.remote.DnsApi
import pt.ismai.farol.data.remote.HttpChecker
import pt.ismai.farol.data.remote.InternetDbApi
import pt.ismai.farol.data.remote.IpifyApi
import pt.ismai.farol.data.remote.RdapApi
import pt.ismai.farol.data.remote.VirusTotalApi
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.evaluators.AgeEvaluator
import pt.ismai.farol.domain.evaluators.DnsSignalsEvaluator
import pt.ismai.farol.domain.evaluators.CertificateEvaluator
import pt.ismai.farol.domain.evaluators.EmailEvaluator
import pt.ismai.farol.domain.evaluators.EmailExtras
import pt.ismai.farol.domain.evaluators.ExposureEvaluator
import pt.ismai.farol.domain.evaluators.HeadersEvaluator
import pt.ismai.farol.domain.evaluators.IpOwnerEvaluator
import pt.ismai.farol.domain.evaluators.ReputationEvaluator
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import retrofit2.Response

private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DNS_NXDOMAIN = 3
private const val DNS_OK = 0
private const val SPF_PREFIX = "v=spf1"
private const val DMARC_PREFIX = "v=DMARC1"
private const val DMARC_DNS_PREFIX = "_dmarc."
private const val MTA_STS_DNS_PREFIX = "_mta-sts."
private const val MTA_STS_PREFIX = "v=STSv1"
private const val TLS_RPT_DNS_PREFIX = "_smtp._tls."
private const val TLS_RPT_PREFIX = "v=TLSRPTv1"
private const val MAX_MX_SHOWN = 5

/** Response body; 429 -> FarolException; other HTTP errors -> null. */
internal fun <T> Response<T>.bodyOrNull(): T? {
    if (code() == HTTP_TOO_MANY_REQUESTS) throw FarolException(ErrorType.RATE_LIMITED)
    return if (isSuccessful) body() else null
}

/** Each function performs one check and returns the domain Evaluator result. */
internal class Checkers(
    private val dns: DnsApi,
    private val internetDb: InternetDbApi,
    private val ipify: IpifyApi,
    private val rdap: RdapApi,
    private val http: HttpChecker,
    private val virusTotal: VirusTotalApi,
    /** Empty when the build has no key: the reputation check then reports itself unavailable. */
    private val virusTotalKey: String,
    private val texts: DataTexts,
    private val now: () -> Instant = Instant::now,
) {
    /** First IPv4 of the domain (null if none). NXDOMAIN -> DOMAIN_NOT_FOUND. */
    suspend fun resolveIpv4(domain: String): String? {
        val response = mapNetworkErrors { dns.resolve(domain, DnsApi.TYPE_A).bodyOrNull() }
            ?: throw FarolException(ErrorType.UNKNOWN)
        if (response.status == DNS_NXDOMAIN) throw FarolException(ErrorType.DOMAIN_NOT_FOUND)
        return firstIpv4(response)
    }

    /** The device's public IPv4 as seen from the internet. */
    suspend fun publicIp(): String {
        val ip = mapNetworkErrors { ipify.myIp().bodyOrNull()?.ip }
        return ip ?: throw FarolException(ErrorType.UNKNOWN)
    }

    /** A single HTTPS request -> [Certificate, Headers]. */
    suspend fun certificateAndHeaders(domain: String): List<Check> {
        val r = try {
            http.check(domain)
        } catch (e: BlockedAddressException) {
            return listOf(
                Checks.unavailable(CheckId.CERTIFICATE, texts.blockedAddress),
                Checks.unavailable(CheckId.HEADERS, texts.blockedAddress),
            )
        } catch (e: ConnectException) {
            return noHttps()
        } catch (e: UnknownHostException) {
            return noHttps()
        }
        val days = daysRemaining(r.expiresAt, now())
        val cert = CertificateEvaluator.evaluate(
            r.certValid, days, issuerName(r.issuer), r.sslError, r.downgradedToHttp, r.tlsVersion,
        )
        val headers = if (r.certValid) {
            HeadersEvaluator.evaluate(r.headers)
        } else {
            Checks.unavailable(CheckId.HEADERS, texts.noHeaders)
        }
        return listOf(withRedirectNote(cert, domain, r.finalHost), headers)
    }

    /** If the site redirected to another host, records it in the detail (the certificate belongs to that host). */
    private fun withRedirectNote(v: Check, requestedHost: String, finalHost: String?): Check =
        if (finalHost == null || finalHost.equals(requestedHost, ignoreCase = true)) {
            v
        } else {
            v.copy(detail = v.detail + "\n" + texts.redirected(finalHost))
        }

    private fun noHttps() = listOf(
        CertificateEvaluator.evaluate(false, null, null, null),
        Checks.unavailable(CheckId.HEADERS, texts.noHeaders),
    )

    /**
     * RDAP only knows the registrable domain: on 404 it goes up one label (up to 2 levels).
     * Final 404, 429 or no date -> UNAVAILABLE.
     */
    suspend fun age(domain: String): Check {
        val found = try {
            hostHierarchy(domain).firstNotNullOfOrNull { host ->
                rdap.domain(host).bodyOrNull()?.let { body ->
                    daysSinceRegistration(body.events, now())?.let { it to daysUntilExpiry(body.events, now()) }
                }
            }
        } catch (e: FarolException) {
            null
        }
        return if (found == null) {
            Checks.unavailable(CheckId.AGE, texts.noRdap)
        } else {
            AgeEvaluator.evaluate(found.first, found.second)
        }
    }

    /** Owner = RDAP registrant (vcard fn/org); if none, the network name. */
    suspend fun ipOwner(ip: String): Check {
        val rdapData = try {
            rdap.ip(ip).bodyOrNull()
        } catch (e: FarolException) {
            null
        }
        val name = registrantName(rdapData?.entities) ?: rdapData?.name
        return IpOwnerEvaluator.evaluate(name, rdapData?.country)
    }

    /**
     * SPF and DMARC (looked up on the host and its parent domains) + informational MX in the detail.
     * If any TXT query fails, the result is UNAVAILABLE (no penalty).
     */
    suspend fun email(domain: String): Check = try {
        coroutineScope {
            val hosts = hostHierarchy(domain)
            val spf = async { findTxt(hosts, SPF_PREFIX, "") }
            val dmarc = async { findTxt(hosts, DMARC_PREFIX, DMARC_DNS_PREFIX) }
            val mx = async { queryMx(domain) }
            val mtaSts = async { hasTxt(MTA_STS_DNS_PREFIX + domain, MTA_STS_PREFIX) }
            val tlsRpt = async { hasTxt(TLS_RPT_DNS_PREFIX + domain, TLS_RPT_PREFIX) }
            combineEmail(spf.await(), dmarc.await(), mx.await(), EmailExtras.note(mtaSts.await(), tlsRpt.await()))
        }
    } catch (e: FarolException) {
        Checks.unavailable(CheckId.EMAIL, texts.noDns)
    }

    private fun combineEmail(spf: TxtResult, dmarc: TxtResult, mx: List<String>?, extras: String?): Check {
        if (spf.value == null && spf.failed || dmarc.value == null && dmarc.failed) {
            return Checks.unavailable(CheckId.EMAIL, texts.noDns)
        }
        val evaluated = EmailEvaluator.evaluate(spf.value, dmarc.value)
        val base = if (extras == null) evaluated else evaluated.copy(detail = evaluated.detail + "\n" + extras)
        val note = when {
            mx == null -> return base
            mx.isEmpty() -> texts.mxNone
            else -> texts.mxWith(mx.take(MAX_MX_SHOWN).joinToString(", "))
        }
        return base.copy(detail = base.detail + "\n" + note)
    }

    /** Whether a TXT record with [prefix] exists at [name]; null if the lookup failed (nothing is claimed). */
    private suspend fun hasTxt(name: String, prefix: String): Boolean? = try {
        dns.resolve(name, DnsApi.TYPE_TXT).bodyOrNull()?.let { txtWithPrefix(it, prefix) != null }
    } catch (e: IOException) {
        null
    } catch (e: FarolException) {
        null
    }

    private class TxtResult(val value: String?, val failed: Boolean)

    /** First TXT with [prefix] at [namePrefix]+host, walking the hosts in order. */
    private suspend fun findTxt(hosts: List<String>, prefix: String, namePrefix: String): TxtResult {
        var failed = false
        for (host in hosts) {
            val response = dns.resolve(namePrefix + host, DnsApi.TYPE_TXT).bodyOrNull()
            if (response == null) {
                failed = true
                continue
            }
            txtWithPrefix(response, prefix)?.let { return TxtResult(it, false) }
        }
        return TxtResult(null, failed)
    }

    /** MX is informational only: any failure -> null (no note). */
    private suspend fun queryMx(domain: String): List<String>? = try {
        dns.resolve(domain, DnsApi.TYPE_MX).bodyOrNull()?.let { mxServers(it) }
    } catch (e: IOException) {
        null
    } catch (e: FarolException) {
        null
    }

    /** InternetDB: known ports and CVEs. 404 = no data (empty lists). */
    suspend fun portsAndCves(ip: String): Pair<List<Int>, List<String>> {
        val response = internetDb.lookup(ip)
        if (response.code() == HTTP_NOT_FOUND) return emptyList<Int>() to emptyList()
        val body = response.bodyOrNull() ?: throw FarolException(ErrorType.UNKNOWN)
        return body.ports.orEmpty() to body.vulns.orEmpty()
    }

    /** InternetDB errors (5xx, 403, 429) make only this check UNAVAILABLE. */
    suspend fun exposure(ip: String?): Check {
        if (ip == null) return Checks.unavailable(CheckId.EXPOSURE, texts.noIp)
        if (AddressGuard.isBlockedLiteral(ip)) return Checks.unavailable(CheckId.EXPOSURE, texts.blockedAddress)
        return try {
            val (ports, cves) = portsAndCves(ip)
            ExposureEvaluator.evaluate(ports, cves)
        } catch (e: FarolException) {
            Checks.unavailable(CheckId.EXPOSURE, texts.error)
        }
    }

    /**
     * What the VirusTotal engines say about the domain. No key, an unknown domain (404) or an
     * exhausted quota (429) make only this check UNAVAILABLE.
     */
    suspend fun reputation(domain: String): Check {
        if (virusTotalKey.isBlank()) return Checks.unavailable(CheckId.REPUTATION, texts.noReputationKey)
        return try {
            val response = virusTotal.domain(virusTotalKey, domain)
            if (response.code() == HTTP_NOT_FOUND) {
                return Checks.unavailable(CheckId.REPUTATION, texts.unknownToVirusTotal)
            }
            val stats = response.bodyOrNull()?.data?.attributes?.stats
                ?: return Checks.unavailable(CheckId.REPUTATION, texts.error)
            ReputationEvaluator.evaluate(stats.malicious, stats.suspicious)
        } catch (e: FarolException) {
            Checks.unavailable(CheckId.REPUTATION, texts.error)
        }
    }

    /**
     * DNSSEC (the resolver's "AD" flag), CAA records and IPv6 support, read through dns.google.
     * A signal that cannot be read stays unknown; a rate limit makes the whole check UNAVAILABLE.
     */
    suspend fun dnsSignals(domain: String): Check = try {
        coroutineScope {
            val signed = async { signedOrNull(domain) }
            val ipv6 = async { ipv6OrNull(domain) }
            val caa = async { caaOrNull(hostHierarchy(domain)) }
            DnsSignalsEvaluator.evaluate(signed.await(), caa.await(), ipv6.await())
        }
    } catch (e: FarolException) {
        Checks.unavailable(CheckId.DNS_SIGNALS, texts.noDns)
    }

    private suspend fun signedOrNull(domain: String): Boolean? = try {
        dns.resolve(domain, DnsApi.TYPE_A, dnssecOk = true).bodyOrNull()
            ?.takeIf { it.status == DNS_OK }?.authenticated
    } catch (e: IOException) {
        null
    }

    private suspend fun ipv6OrNull(domain: String): Boolean? = try {
        dns.resolve(domain, DnsApi.TYPE_AAAA).bodyOrNull()?.let { hasIpv6(it) }
    } catch (e: IOException) {
        null
    }

    /** CAA applies to the host and is inherited from its parents, so the first host that has one wins. */
    private suspend fun caaOrNull(hosts: List<String>): Boolean? = try {
        var failed = false
        var found = false
        for (host in hosts) {
            val response = dns.resolve(host, DnsApi.TYPE_CAA).bodyOrNull()
            if (response == null) {
                failed = true
            } else if (hasCaa(response)) {
                found = true
                break
            }
        }
        if (found) true else if (failed) null else false
    } catch (e: IOException) {
        null
    }

    /** Unreachable DNS is treated as having no internet; a timeout is a generic error. */
    private suspend fun <T> mapNetworkErrors(block: suspend () -> T): T = try {
        block()
    } catch (e: UnknownHostException) {
        throw FarolException(ErrorType.NO_INTERNET, e)
    } catch (e: ConnectException) {
        throw FarolException(ErrorType.NO_INTERNET, e)
    } catch (e: SocketTimeoutException) {
        throw FarolException(ErrorType.UNKNOWN, e)
    }
}
