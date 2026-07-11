package com.smarttv.remote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Finds the SmartTV Mac app on the LAN via NSD (Bonjour/mDNS).
 * Distinguishes "nothing advertising on this network" (onNothingFound after a
 * timeout) from a successful resolve (onFound) — the caller decides whether a
 * found TV still needs pairing.
 */
class BonjourDiscovery(context: Context) {

    interface Listener {
        fun onFound(host: String, port: Int, serviceName: String)
        fun onLost()
        /** Fired once if nothing is discovered within [SEARCH_TIMEOUT_MS]. */
        fun onNothingFound()
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: Listener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var found = false

    private val timeoutRunnable = Runnable {
        if (!found) listener?.onNothingFound()
    }

    fun start(listener: Listener) {
        stop()
        this.listener = listener
        found = false

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains(SERVICE_TYPE_BASE)) return
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    found = false
                    this@BonjourDiscovery.listener?.onLost()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
                mainHandler.post { this@BonjourDiscovery.listener?.onNothingFound() }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = discovery
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        mainHandler.postDelayed(timeoutRunnable, SEARCH_TIMEOUT_MS)
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve failed: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                mainHandler.post {
                    found = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    listener?.onFound(host, info.port, info.serviceName)
                }
            }
        })
    }

    fun stop() {
        mainHandler.removeCallbacks(timeoutRunnable)
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: IllegalArgumentException) {
                // Not currently discovering; fine.
            }
        }
        discoveryListener = null
    }

    companion object {
        private const val TAG = "BonjourDiscovery"
        const val SERVICE_TYPE = "_smarttv._tcp."
        private const val SERVICE_TYPE_BASE = "_smarttv._tcp"
        private const val SEARCH_TIMEOUT_MS = 10_000L
    }
}
