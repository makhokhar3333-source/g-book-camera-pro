package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class ConnectivityMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(checkSystemNetworkValidated())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshStatusAsync()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkSystemNetworkValidated()
            refreshStatusAsync()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated) {
                _isOnline.value = true
            } else {
                refreshStatusAsync()
            }
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        // Continuous live ping heartbeat every 4 seconds to guarantee instant red/green accuracy
        scope.launch {
            while (isActive) {
                val reachable = verifyInternetNow()
                _isOnline.value = reachable
                delay(4_000L)
            }
        }
    }

    private fun refreshStatusAsync() {
        scope.launch {
            _isOnline.value = verifyInternetNow()
        }
    }

    private fun checkSystemNetworkValidated(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun verifyInternetNow(): Boolean = withContext(Dispatchers.IO) {
        if (!hasBasicTransport()) {
            _isOnline.value = false
            return@withContext false
        }
        val httpReachable = runCatching {
            val conn = (URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "HEAD"
                useCaches = false
            }
            try {
                val code = conn.responseCode
                code in 200..399
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

        if (httpReachable) {
            _isOnline.value = true
            return@withContext true
        }

        val socketReachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 443), 1800)
                true
            }
        }.getOrDefault(false)

        val result = socketReachable || checkSystemNetworkValidated()
        _isOnline.value = result
        result
    }

    private fun hasBasicTransport(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun release() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
}
