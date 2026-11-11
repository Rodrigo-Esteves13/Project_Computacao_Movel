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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val MAX_CAUSES = 10

data class HttpResult(
    val certValid: Boolean,
    val issuer: String?,
    val expiresAt: Instant?,
    val headers: Map<String, String>,
    val sslError: String?,
    /** final host after redirects (null if the request failed before any response) */
    val finalHost: String? = null,
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

/** A single HTTPS request to the site: yields the certificate (handshake) and headers. */
class HttpChecker(private val client: OkHttpClient) {

    /**
     * SSLException is a result (certValid = false). Timeouts and connection failures propagate
     * to the caller. Cancelling the coroutine cancels the request.
     */
    suspend fun check(domain: String): HttpResult {
        val request = Request.Builder().url("https://$domain").get().build()
        return try {
            client.newCall(request).await().use { resp -> toResult(resp) }
        } catch (e: SSLException) {
            HttpResult(false, null, null, emptyMap(), describeError(e))
        }
    }

    private fun toResult(resp: Response): HttpResult {
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
