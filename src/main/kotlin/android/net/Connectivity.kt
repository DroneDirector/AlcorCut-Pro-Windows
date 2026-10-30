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
class LinkAddress(val address: java.net.InetAddress)
class LinkProperties(val linkAddresses: List<LinkAddress>)

class ConnectivityManager {
    /** This PC's own addresses on its networks (for finding a NAS in the same subnet). */
    fun getLinkProperties(n: Network?): LinkProperties? = try {
        val list = java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { i -> i.inetAddresses.toList() }.map { LinkAddress(it) }
        LinkProperties(list)
    } catch (_: Throwable) { null }
    val activeNetwork: Network? = Network()
    fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = NetworkCapabilities()
    val isActiveNetworkMetered: Boolean get() = false
}
