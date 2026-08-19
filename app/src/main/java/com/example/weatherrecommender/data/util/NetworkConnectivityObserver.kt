package com.example.weatherrecommender.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Android-specific implementation of [ConnectivityObserver] using [ConnectivityManager].
 * Emits continuous network state changes via Kotlin Flow.
 */
class NetworkConnectivityObserver(
    private val context: Context
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Emits the current network state, then distinct updates from [ConnectivityManager] callbacks. */
    override fun observe(): Flow<ConnectivityStatus> {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                /** Network is usable; treat as [ConnectivityStatus.Available]. */
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    launch { send(ConnectivityStatus.Available) }
                }

                /** The network is about to drop; emit [ConnectivityStatus.Losing]. */
                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    launch { send(ConnectivityStatus.Losing) }
                }

                /** The tracked network is gone; emit [ConnectivityStatus.Lost]. */
                override fun onLost(network: Network) {
                    super.onLost(network)
                    launch { send(ConnectivityStatus.Lost) }
                }

                /** No network satisfied the request; emit [ConnectivityStatus.Unavailable]. */
                override fun onUnavailable() {
                    super.onUnavailable()
                    launch { send(ConnectivityStatus.Unavailable) }
                }
            }
            
            // Check initial state
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (activeNetwork == null || capabilities == null) {
                launch { send(ConnectivityStatus.Unavailable) }
            } else {
                launch { send(ConnectivityStatus.Available) }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, callback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
    }
}
