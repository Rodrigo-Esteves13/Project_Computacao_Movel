package pt.ismai.farol.data.repository

import android.content.Context
import pt.ismai.farol.R

/** "Unavailable" reasons shown to the user (strings_data.xml). */
class DataTexts(context: Context) {
    private val ctx = context.applicationContext
    val timeout: String get() = ctx.getString(R.string.data_reason_timeout)
    val error: String get() = ctx.getString(R.string.data_reason_error)
    val noDns: String get() = ctx.getString(R.string.data_reason_no_dns)
    fun mxWith(servers: String): String = ctx.getString(R.string.data_mx_with, servers)
    val mxNone: String get() = ctx.getString(R.string.data_mx_none)
}
