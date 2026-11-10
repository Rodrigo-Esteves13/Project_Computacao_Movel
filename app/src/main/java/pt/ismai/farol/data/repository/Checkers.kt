package pt.ismai.farol.data.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pt.ismai.farol.data.remote.DnsApi
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.evaluators.EmailEvaluator
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import retrofit2.Response

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DNS_NXDOMAIN = 3
private const val SPF_PREFIX = "v=spf1"
private const val DMARC_PREFIX = "v=DMARC1"
private const val DMARC_DNS_PREFIX = "_dmarc."
private const val MAX_MX_SHOWN = 5

/** Response body; 429 -> FarolException; other HTTP errors -> null. */
internal fun <T> Response<T>.bodyOrNull(): T? {
    if (code() == HTTP_TOO_MANY_REQUESTS) throw FarolException(ErrorType.RATE_LIMITED)
    return if (isSuccessful) body() else null
}

/** Each function performs one check and returns the domain Evaluator result. */
internal class Checkers(
    private val dns: DnsApi,
    private val texts: DataTexts,
) {
    /** First IPv4 of the domain (null if none). NXDOMAIN -> DOMAIN_NOT_FOUND. */
    suspend fun resolveIpv4(domain: String): String? {
        val response = mapNetworkErrors { dns.resolve(domain, DnsApi.TYPE_A).bodyOrNull() }
            ?: throw FarolException(ErrorType.UNKNOWN)
        if (response.status == DNS_NXDOMAIN) throw FarolException(ErrorType.DOMAIN_NOT_FOUND)
        return firstIpv4(response)
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
            combineEmail(spf.await(), dmarc.await(), mx.await())
        }
    } catch (e: FarolException) {
        Checks.unavailable(CheckId.EMAIL, texts.noDns)
    }

    private fun combineEmail(spf: TxtResult, dmarc: TxtResult, mx: List<String>?): Check {
        if (spf.value == null && spf.failed || dmarc.value == null && dmarc.failed) {
            return Checks.unavailable(CheckId.EMAIL, texts.noDns)
        }
        val base = EmailEvaluator.evaluate(spf.value, dmarc.value)
        val note = when {
            mx == null -> return base
            mx.isEmpty() -> texts.mxNone
            else -> texts.mxWith(mx.take(MAX_MX_SHOWN).joinToString(", "))
        }
        return base.copy(detail = base.detail + "\n" + note)
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
