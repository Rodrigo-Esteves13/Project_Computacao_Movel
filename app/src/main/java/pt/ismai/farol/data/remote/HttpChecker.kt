package pt.ismai.farol.data.remote

import java.io.IOException
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val MAX_CAUSES = 10
private const val MAX_REDIRECTS = 3
private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

data class HttpResult(
    val certValid: Boolean,
    val issuer: String?,
    val expiresAt: Instant?,
    val headers: Map<String, String>,
    val sslError: String?,
    /** final host after redirects (null if the request failed before any response) */
    val finalHost: String? = null,
    /** protocol of the handshake, for example "TLSv1.3" (null if unknown) */
    val tlsVersion: String? = null,
    /** true if the site redirected from HTTPS to a plain HTTP address */
    val downgradedToHttp: Boolean = false,
)

/**
 * Joins the class name and message of the WHOLE cause chain, so the evaluator recognizes
 * "CertificateExpiredException", "hostname", "trust anchor", etc. even when they sit in an inner cause.
 */
fun describeError(error: Throwable): String {
    val parts = LinkedHashSet<String>()
    var current: Throwable? = error
    var n = 0
    while (current != null && n < MAX_CAUSES) {
        parts += current.javaClass.simpleName
        current.message?.takeIf { it.isNotBlank() }?.let { parts += it }
        val cause = current.cause
        current = if (cause === current) null else cause
        n++
    }
    return parts.joinToString(" | ")
}

/**
 * An HTTPS request to the site: yields the certificate (handshake) and headers.
 *
 * Every connection goes through [GuardedDns], so a site that points at a private address is
 * refused with [BlockedAddressException]. Redirects are followed here, not by OkHttp: at most
 * [MAX_REDIRECTS] hops, HTTPS only, and each hop is resolved (and guarded) again.
 */
class HttpChecker(client: OkHttpClient) {
    private val client = client.newBuilder()
        .dns(GuardedDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * SSLException is a result (certValid = false). Timeouts and connection failures propagate
     * to the caller. Cancelling the coroutine cancels the request.
     */
    suspend fun check(domain: String): HttpResult {
        return try {
            val fetched = fetch("https://$domain".toHttpUrl())
            fetched.response.use { resp -> toResult(resp, fetched.downgraded) }
        } catch (e: SSLException) {
            HttpResult(false, null, null, emptyMap(), describeError(e))
        }
    }

    private class Fetched(val response: Response, val downgraded: Boolean)

    /**
     * Follows up to [MAX_REDIRECTS] HTTPS redirects. A redirect to plain HTTP is never followed:
     * the response that sent it is returned and the downgrade is reported.
     */
    private suspend fun fetch(start: HttpUrl): Fetched {
        var url = start
        var hops = 0
        while (true) {
            val response = client.newCall(Request.Builder().url(url).get().build()).await()
            val next = response.header("Location")?.let { response.request.url.resolve(it) }
            if (response.code !in REDIRECT_CODES || next == null) return Fetched(response, false)
            if (!next.isHttps) return Fetched(response, true)
            if (hops >= MAX_REDIRECTS) return Fetched(response, false)
            response.close()
            url = next
            hops++
        }
    }

    private fun toResult(resp: Response, downgraded: Boolean): HttpResult {
        val cert = resp.handshake?.peerCertificates?.firstOrNull() as? X509Certificate
        return HttpResult(
            certValid = true,
            issuer = cert?.issuerX500Principal?.name,
            expiresAt = cert?.notAfter?.toInstant(),
            headers = resp.headers.names().associate { name ->
                name.lowercase() to resp.headers.values(name).joinToString(", ")
            },
            sslError = null,
            finalHost = resp.request.url.host,
            tlsVersion = resp.handshake?.tlsVersion?.javaName,
            downgradedToHttp = downgraded,
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }
}
