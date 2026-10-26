package android.net

/** Network state: a PC is treated as online and not on a metered connection. Own code. */
class Network
class NetworkCapabilities {
    fun hasCapability(c: Int) = true
    fun hasTransport(t: Int) = t == TRANSPORT_WIFI || t == TRANSPORT_ETHERNET
    companion object {
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
        const val NET_CAPABILITY_NOT_METERED = 11
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_ETHERNET = 3
    }
}
class ConnectivityManager {
    val activeNetwork: Network? = Network()
    fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = NetworkCapabilities()
    val isActiveNetworkMetered: Boolean get() = false
}
