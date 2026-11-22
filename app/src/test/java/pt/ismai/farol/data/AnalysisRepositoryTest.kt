package pt.ismai.farol.data

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pt.ismai.farol.data.remote.DnsApi
import pt.ismai.farol.data.remote.HttpChecker
import pt.ismai.farol.data.remote.InternetDbApi
import pt.ismai.farol.data.remote.IpifyApi
import pt.ismai.farol.data.remote.RdapApi
import pt.ismai.farol.data.remote.VirusTotalApi
import pt.ismai.farol.data.remote.dto.VirusTotalAttributes
import pt.ismai.farol.data.remote.dto.VirusTotalData
import pt.ismai.farol.data.remote.dto.VirusTotalDomain
import pt.ismai.farol.data.remote.dto.VirusTotalStats
import pt.ismai.farol.data.remote.dto.DnsRecord
import pt.ismai.farol.data.remote.dto.DnsResponse
import pt.ismai.farol.data.remote.dto.InternetDbResponse
import pt.ismai.farol.data.remote.dto.IpifyResponse
import pt.ismai.farol.data.remote.dto.RdapDomain
import pt.ismai.farol.data.remote.dto.RdapIp
import pt.ismai.farol.data.repository.AnalysisRepositoryImpl
import pt.ismai.farol.data.repository.Checkers
import pt.ismai.farol.data.repository.DataTexts
import pt.ismai.farol.data.repository.InMemoryAnalysisStore
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import pt.ismai.farol.domain.model.TargetType
import pt.ismai.farol.util.NetworkStatus
import retrofit2.Response

/** The repository with fake network sources: no Android, no internet. */
class AnalysisRepositoryTest {

    private object Texts : DataTexts {
        override val timeout = "timeout"
        override val error = "error"
        override val noRdap = "no rdap"
        override val noIp = "no ip"
        override val blockedAddress = "blocked"
        override val noHeaders = "no headers"
        override fun redirected(host: String) = "redirected to $host"
        override val noDns = "no dns"
        override fun mxWith(servers: String) = "mx $servers"
        override val mxNone = "no mx"
        override val noReputationKey = "no key"
        override val unknownToVirusTotal = "unknown to vt"
    }

    private class FakeNetwork(var online: Boolean = true) : NetworkStatus {
        override fun hasInternet() = online
        override fun connectionType() = "Wi-Fi"
    }

    /** Counts calls, so a test can prove that the cache avoided the network. */
    private class Fakes {
        var internetDbCalls = 0
        var dnsStatus = 0
        var dnsHttpCode = 200
        var dnssec = false
        var mtaSts = false
        var ipv6 = false
        var caa = false
        var internetDb: () -> Response<InternetDbResponse> = { Response.success(InternetDbResponse(ports = listOf(443))) }
        var rdapIp: () -> Response<RdapIp> = { Response.success(RdapIp(name = "EXAMPLE-NET", country = "PT")) }

        val dns = object : DnsApi {
            override suspend fun resolve(name: String, type: String, dnssecOk: Boolean?): Response<DnsResponse> {
                if (dnsHttpCode != 200) return Response.error(dnsHttpCode, "".toResponseBody())
                val answers = when {
                    type == DnsApi.TYPE_TXT && mtaSts && name.startsWith("_mta-sts.") ->
                        listOf(DnsRecord(name, 16, "\"v=STSv1; id=1\""))
                    type == DnsApi.TYPE_A -> listOf(DnsRecord(name, 1, "93.184.216.34"))
                    type == DnsApi.TYPE_AAAA && ipv6 -> listOf(DnsRecord(name, 28, "2606:2800::1"))
                    type == DnsApi.TYPE_CAA && caa -> listOf(DnsRecord(name, 257, "0 issue \"letsencrypt.org\""))
                    else -> null
                }
                return Response.success(DnsResponse(status = dnsStatus, answers = answers, authenticated = dnssec && dnssecOk == true))
            }
        }
        val internetDbApi = object : InternetDbApi {
            override suspend fun lookup(ip: String): Response<InternetDbResponse> {
                internetDbCalls++
                return internetDb()
            }
        }
        val ipify = object : IpifyApi {
            override suspend fun myIp(format: String) = Response.success(IpifyResponse("203.0.113.5"))
        }
        var virusTotalCalls = 0
        var virusTotalKeyUsed: String? = null
        var virusTotal: () -> Response<VirusTotalDomain> = {
            Response.success(VirusTotalDomain(VirusTotalData(VirusTotalAttributes(VirusTotalStats()))))
        }
        val virusTotalApi = object : VirusTotalApi {
            override suspend fun domain(apiKey: String, domain: String): Response<VirusTotalDomain> {
                virusTotalCalls++
                virusTotalKeyUsed = apiKey
                return virusTotal()
            }
        }
        val rdap = object : RdapApi {
            override suspend fun domain(domain: String) = Response.success(RdapDomain())
            override suspend fun ip(ip: String): Response<RdapIp> = rdapIp()
        }
    }

    private class Setup(
        val repo: AnalysisRepositoryImpl,
        val store: InMemoryAnalysisStore,
        val fakes: Fakes,
        val network: FakeNetwork,
        val clock: LongArray,
    )

    private fun setup(virusTotalKey: String = ""): Setup {
        val fakes = Fakes()
        val network = FakeNetwork()
        val clock = longArrayOf(1_000_000L)
        // The site answers 200 with no certificate details, which counts as a valid connection.
        val okHttp = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        val checkers = Checkers(
            fakes.dns, fakes.internetDbApi, fakes.ipify, fakes.rdap, HttpChecker(okHttp),
            fakes.virusTotalApi, virusTotalKey, Texts,
        )
        val store = InMemoryAnalysisStore()
        val repo = AnalysisRepositoryImpl(checkers, network, Texts, store) { clock[0] }
        return Setup(repo, store, fakes, network, clock)
    }

    private suspend fun failure(block: suspend () -> Unit): FarolException {
        try {
            block()
        } catch (e: FarolException) {
            return e
        }
        fail("expected a FarolException")
        throw IllegalStateException()
    }

    @Test fun ipAnalysis_emitsProgressThenSavesTheResult() = runTest {
        val s = setup()
        val emitted = s.repo.analyze("8.8.8.8").toList()
        val first = emitted.first()
        val last = emitted.last()
        assertEquals(listOf(CheckId.EXPOSURE, CheckId.IP_OWNER), first.pending)
        assertTrue(first.completed.isEmpty())
        assertTrue(last.finished)
        assertTrue(last.pending.isEmpty())
        assertEquals(TargetType.IP, last.type)
        assertNotNull(last.score)
        assertEquals(1, s.store.analyses.first().size)
        assertNotNull(last.analysisId)
    }

    @Test fun secondAnalysisWithinTenMinutes_comesFromTheCache() = runTest {
        val s = setup()
        s.repo.analyze("8.8.8.8").last()
        val callsAfterFirst = s.fakes.internetDbCalls
        s.clock[0] += 9 * 60 * 1000L
        val cached = s.repo.analyze("8.8.8.8").toList()
        assertEquals(1, cached.size)
        assertTrue(cached.single().fromCache)
        // The date of the original analysis travels with the cached data (shown as "Data from ...").
        assertEquals(s.store.analyses.first().single().date, cached.single().date)
        assertEquals(callsAfterFirst, s.fakes.internetDbCalls)
    }

    @Test fun cacheExpiresAfterTenMinutes_andForceSkipsIt() = runTest {
        val s = setup()
        s.repo.analyze("8.8.8.8").last()
        s.clock[0] += 11 * 60 * 1000L
        val fresh = s.repo.analyze("8.8.8.8").last()
        assertFalse(fresh.fromCache)
        assertEquals(null, fresh.date)
        assertFalse(s.repo.analyze("8.8.8.8", force = true).last().fromCache)
        assertEquals(3, s.store.analyses.first().size)
    }

    @Test fun invalidInput_failsWithInvalidInput() = runTest {
        val e = failure { setup().repo.analyze("not a target").toList() }
        assertEquals(ErrorType.INVALID_INPUT, e.type)
    }

    @Test fun offline_failsWithNoInternet() = runTest {
        val s = setup()
        s.network.online = false
        assertEquals(ErrorType.NO_INTERNET, failure { s.repo.analyze("8.8.8.8").toList() }.type)
    }

    @Test fun missingDomain_failsWithDomainNotFound() = runTest {
        val s = setup()
        s.fakes.dnsStatus = 3
        assertEquals(ErrorType.DOMAIN_NOT_FOUND, failure { s.repo.analyze("nope.example").toList() }.type)
        assertTrue(s.store.analyses.first().isEmpty())
    }

    @Test fun rateLimitOnFirstLookup_failsWithRateLimited() = runTest {
        val s = setup()
        s.fakes.dnsHttpCode = 429
        assertEquals(ErrorType.RATE_LIMITED, failure { s.repo.analyze("example.com").toList() }.type)
    }

    @Test fun domainAnalysis_runsAllSevenChecks() = runTest {
        val s = setup()
        val last = s.repo.analyze("example.com").last()
        val ids = last.completed.map { it.id }
        assertEquals(
            listOf(
                CheckId.CERTIFICATE, CheckId.HEADERS, CheckId.AGE, CheckId.EMAIL, CheckId.EXPOSURE,
                CheckId.REPUTATION, CheckId.DNS_SIGNALS,
            ),
            ids,
        )
        assertEquals(CheckState.UNAVAILABLE, last.completed.first { it.id == CheckId.AGE }.state)
    }

    @Test fun dnsSignals_areReadAndNeverChangeTheScore() = runTest {
        val plain = setup()
        val withoutSignals = plain.repo.analyze("example.com").last()
        val s = setup()
        s.fakes.dnssec = true
        s.fakes.ipv6 = true
        s.fakes.caa = true
        val last = s.repo.analyze("example.com").last()
        val signals = last.completed.first { it.id == CheckId.DNS_SIGNALS }
        assertEquals("DNSSEC on, CAA set, IPv6 yes", signals.summary)
        assertEquals(CheckState.OK, signals.state)
        assertEquals(withoutSignals.score, last.score)
    }

    @Test fun dnsSignalsOff_areAWarningButStillNoScoreChange() = runTest {
        val last = setup().repo.analyze("example.com").last()
        val signals = last.completed.first { it.id == CheckId.DNS_SIGNALS }
        assertEquals("DNSSEC off, CAA not set, IPv6 no", signals.summary)
        assertEquals(CheckState.WARNING, signals.state)
        assertEquals(0, signals.weight)
    }

    @Test fun mailExtras_areInTheEmailDetail_andNeverChangeTheScore() = runTest {
        val plain = setup()
        val before = plain.repo.analyze("example.com").last()
        val s = setup()
        s.fakes.mtaSts = true
        val last = s.repo.analyze("example.com").last()
        val email = last.completed.first { it.id == CheckId.EMAIL }
        assertTrue(email.detail.contains("MTA-STS: present"))
        assertTrue(email.detail.contains("TLS-RPT: not set."))
        assertEquals(before.score, last.score)
    }

    @Test fun withoutAKey_virusTotalIsNeverCalled() = runTest {
        val s = setup(virusTotalKey = "")
        val last = s.repo.analyze("example.com").last()
        assertEquals(0, s.fakes.virusTotalCalls)
        assertEquals(CheckState.UNAVAILABLE, last.completed.first { it.id == CheckId.REPUTATION }.state)
    }

    @Test fun withAKey_reputationCountsInTheScoreAndTheKeyIsSent() = runTest {
        val s = setup(virusTotalKey = "abc123")
        val last = s.repo.analyze("example.com").last()
        assertEquals("abc123", s.fakes.virusTotalKeyUsed)
        assertEquals(CheckState.OK, last.completed.first { it.id == CheckId.REPUTATION }.state)
    }

    @Test fun manyMaliciousDetections_failTheCheckAndCapTheScore() = runTest {
        val s = setup(virusTotalKey = "k")
        s.fakes.virusTotal = {
            Response.success(VirusTotalDomain(VirusTotalData(VirusTotalAttributes(VirusTotalStats(malicious = 7)))))
        }
        val last = s.repo.analyze("example.com").last()
        assertEquals(CheckState.FAIL, last.completed.first { it.id == CheckId.REPUTATION }.state)
        assertTrue(last.score!! <= 40)
    }

    @Test fun virusTotalUnknownDomainOrQuota_onlyMakesThatCheckUnavailable() = runTest {
        val s = setup(virusTotalKey = "k")
        s.fakes.virusTotal = { Response.error(404, "".toResponseBody()) }
        assertEquals(CheckState.UNAVAILABLE, s.repo.analyze("example.com").last().completed.first { it.id == CheckId.REPUTATION }.state)
        s.fakes.virusTotal = { Response.error(429, "".toResponseBody()) }
        val last = s.repo.analyze("example.com", force = true).last()
        assertEquals(CheckState.UNAVAILABLE, last.completed.first { it.id == CheckId.REPUTATION }.state)
        assertNotNull(last.score)
    }

    @Test fun analysisWhereNothingAnswered_isNotSaved() = runTest {
        val s = setup()
        s.fakes.internetDb = { Response.error(500, "".toResponseBody()) }
        s.fakes.rdapIp = { Response.error(500, "".toResponseBody()) }
        val last = s.repo.analyze("8.8.8.8").last()
        assertTrue(last.completed.all { it.state == CheckState.UNAVAILABLE })
        assertNull(last.score)
        assertTrue(s.store.analyses.first().isEmpty())
        assertNull(last.analysisId)
    }

    @Test fun ioFailureOnOneCheck_onlyMakesThatCheckUnavailable() = runTest {
        val s = setup()
        s.fakes.rdapIp = { throw IOException("boom") }
        val last = s.repo.analyze("8.8.8.8").last()
        assertEquals(CheckState.UNAVAILABLE, last.completed.first { it.id == CheckId.IP_OWNER }.state)
        assertNotEquals(CheckState.UNAVAILABLE, last.completed.first { it.id == CheckId.EXPOSURE }.state)
    }

    @Test fun favorites_toggleAndShowTheLatestScore() = runTest {
        val s = setup()
        assertFalse(s.repo.isFavorite("8.8.8.8").first())
        s.repo.toggleFavorite("8.8.8.8")
        assertTrue(s.repo.isFavorite("8.8.8.8").first())
        assertNull(s.repo.favorites().first().single().score)
        s.repo.analyze("8.8.8.8").last()
        assertNotNull(s.repo.favorites().first().single().score)
        s.repo.toggleFavorite("8.8.8.8")
        assertTrue(s.repo.favorites().first().isEmpty())
    }

    @Test fun recheckFavorites_forcesAFreshAnalysisOfEach() = runTest {
        val s = setup()
        s.repo.toggleFavorite("8.8.8.8")
        s.repo.analyze("8.8.8.8").last()
        val before = s.fakes.internetDbCalls
        assertEquals(listOf("8.8.8.8"), s.repo.recheckFavorites().toList())
        assertEquals(before + 1, s.fakes.internetDbCalls)
    }

    @Test fun history_isNewestFirst() = runTest {
        val s = setup()
        s.repo.analyze("8.8.8.8").last()
        s.clock[0] += 60_000
        s.repo.analyze("1.1.1.1").last()
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), s.repo.history().first().map { it.target })
    }

    private fun assertNotEquals(unexpected: Any?, actual: Any?) = assertFalse(unexpected == actual)
}
