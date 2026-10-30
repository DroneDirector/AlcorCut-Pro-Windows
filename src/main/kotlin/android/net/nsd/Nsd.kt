package android.net.nsd

/** Network service discovery: on Windows the NAS is typed in (or opened from Explorer); nothing is searched. Own code. */
class NsdServiceInfo {
    var serviceName: String = ""
    var serviceType: String = ""
    var host: java.net.InetAddress? = null
    var port: Int = 0
}

class NsdManager {
    interface DiscoveryListener {
        fun onStartDiscoveryFailed(serviceType: String, errorCode: Int)
        fun onStopDiscoveryFailed(serviceType: String, errorCode: Int)
        fun onDiscoveryStarted(serviceType: String)
        fun onDiscoveryStopped(serviceType: String)
        fun onServiceFound(info: NsdServiceInfo)
        fun onServiceLost(info: NsdServiceInfo)
    }
    interface ResolveListener {
        fun onResolveFailed(info: NsdServiceInfo, errorCode: Int)
        fun onServiceResolved(info: NsdServiceInfo)
    }
    fun discoverServices(type: String, protocol: Int, l: DiscoveryListener) {}
    fun stopServiceDiscovery(l: DiscoveryListener) {}
    fun resolveService(info: NsdServiceInfo, l: ResolveListener) { l.onResolveFailed(info, 0) }
    companion object { const val PROTOCOL_DNS_SD = 1 }
}
