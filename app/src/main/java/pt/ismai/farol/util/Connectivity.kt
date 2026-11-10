package pt.ismai.farol.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Connection state of the device (complements UnknownHostException). */
class Connectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private fun capabilities(): NetworkCapabilities? =
        manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }

    fun hasInternet(): Boolean =
        capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** "Wi-Fi", "Mobile data", "Other" or "No connection" (shown to the user as is). */
    fun connectionType(): String {
        val c = capabilities() ?: return "No connection"
        return when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            else -> "Other"
        }
    }
}
