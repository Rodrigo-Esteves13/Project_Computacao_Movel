package pt.ismai.farol.data.repository

import android.content.Context
import pt.ismai.farol.R

/** "Unavailable" reasons shown to the user. An interface, so tests do not need an Android Context. */
interface DataTexts {
    val timeout: String
    val error: String
    val noRdap: String
    val noIp: String
    val blockedAddress: String
    val noHeaders: String
    fun redirected(host: String): String
    val noDns: String
    fun mxWith(servers: String): String
    val mxNone: String
    val noReputationKey: String
    val unknownToVirusTotal: String
}

/** The real texts, from strings_data.xml. */
class AndroidDataTexts(context: Context) : DataTexts {
    private val ctx = context.applicationContext
    override val timeout: String get() = ctx.getString(R.string.data_reason_timeout)
    override val error: String get() = ctx.getString(R.string.data_reason_error)
    override val noRdap: String get() = ctx.getString(R.string.data_reason_no_rdap)
    override val noIp: String get() = ctx.getString(R.string.data_reason_no_ip)
    override val blockedAddress: String get() = ctx.getString(R.string.data_reason_blocked_address)
    override val noHeaders: String get() = ctx.getString(R.string.data_reason_no_headers)
    override fun redirected(host: String): String = ctx.getString(R.string.data_redirected, host)
    override val noDns: String get() = ctx.getString(R.string.data_reason_no_dns)
    override fun mxWith(servers: String): String = ctx.getString(R.string.data_mx_with, servers)
    override val mxNone: String get() = ctx.getString(R.string.data_mx_none)
    override val noReputationKey: String get() = ctx.getString(R.string.data_reason_no_reputation_key)
    override val unknownToVirusTotal: String get() = ctx.getString(R.string.data_reason_unknown_to_virustotal)
}
