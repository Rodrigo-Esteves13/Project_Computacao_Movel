package pt.ismai.farol.data

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ismai.farol.data.remote.HttpChecker

/** Redirect handling, with an interceptor that plays the site: no network. */
class HttpCheckerTest {
    private val requested = mutableListOf<String>()

    private fun checker(answer: (String) -> Pair<Int, String?>): HttpChecker {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            requested += url
            val (code, location) = answer(url)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("x").body("".toResponseBody())
                .apply { if (location != null) header("Location", location) }
                .build()
        }).build()
        return HttpChecker(client)
    }

    @Test fun plainAnswer_isReturnedWithoutRedirects() = runTest {
        val result = checker { 200 to null }.check("a.com")
        assertEquals(listOf("https://a.com/"), requested)
        assertEquals("a.com", result.finalHost)
        assertFalse(result.downgradedToHttp)
    }

    @Test fun httpsRedirect_isFollowed_andTheFinalHostIsReported() = runTest {
        val result = checker { url -> if (url.contains("//a.com")) 301 to "https://www.a.com/home" else 200 to null }
            .check("a.com")
        assertEquals(listOf("https://a.com/", "https://www.a.com/home"), requested)
        assertEquals("www.a.com", result.finalHost)
        assertFalse(result.downgradedToHttp)
    }

    @Test fun redirectToPlainHttp_isNotFollowedAndIsReported() = runTest {
        val result = checker { 302 to "http://a.com/insecure" }.check("a.com")
        assertEquals(listOf("https://a.com/"), requested)
        assertTrue(result.downgradedToHttp)
    }

    @Test fun redirectLoop_stopsAfterThreeHops() = runTest {
        val result = checker { 302 to "https://a.com/again" }.check("a.com")
        assertEquals(4, requested.size) // the first request plus three followed redirects
        assertFalse(result.downgradedToHttp)
    }

    @Test fun relativeRedirect_isResolvedAgainstTheCurrentUrl() = runTest {
        checker { url -> if (url.endsWith("a.com/")) 301 to "/login" else 200 to null }.check("a.com")
        assertEquals(listOf("https://a.com/", "https://a.com/login"), requested)
    }
}
