package io.geoshift.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class VpnDetector(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    data class State(val active: Boolean, val network: Network?)

    fun currentState(): State {
        val network = connectivityManager.activeNetwork
        val caps = network?.let(connectivityManager::getNetworkCapabilities)
        return State(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true, network)
    }

    fun register(onChanged: (State) -> Unit): ConnectivityManager.NetworkCallback {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged(currentState())
            override fun onLost(network: Network) = onChanged(currentState())
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                onChanged(currentState())
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onChanged(currentState())
        return callback
    }

    fun unregister(callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
