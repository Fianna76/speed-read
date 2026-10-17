package io.github.fianna76.speedread.data.news

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Watches whether the device's default network is metered (mobile data, hotspots). */
class NetworkMonitor(context: Context) {

    private val connectivity = context.applicationContext.getSystemService<ConnectivityManager>()

    /** `true` while on a metered network; `false` on Wi-Fi/Ethernet or when offline. */
    val isMetered: Flow<Boolean> = callbackFlow {
        val manager = connectivity ?: run {
            trySend(false)
            awaitClose {}
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.isActiveNetworkMetered)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
