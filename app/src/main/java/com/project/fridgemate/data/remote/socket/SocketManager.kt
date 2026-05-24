package com.project.fridgemate.data.remote.socket

import android.util.Log
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.data.remote.ApiClient
import io.socket.client.IO
import io.socket.client.Socket

object SocketManager {

    private const val TAG = "SocketManager"

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var lastTokenUsed: String? = null

    @Synchronized
    fun get(): Socket {
        val currentToken = ApiClient.getTokenManager().accessToken
        val existing = socket

        if (existing != null && currentToken == lastTokenUsed) {
            return existing
        }

        existing?.let {
            it.disconnect()
            it.off()
        }

        val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
        val opts = IO.Options.builder()
            .setAuth(mapOf("token" to (currentToken ?: "")))
            .setReconnection(true)
            .setReconnectionDelay(1000)
            .setReconnectionDelayMax(5000)
            .setForceNew(true)
            .build()

        val newSocket = IO.socket(baseUrl, opts)
        newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.w(TAG, "Socket connect error: ${args.firstOrNull()}")
        }
        socket = newSocket
        lastTokenUsed = currentToken
        return newSocket
    }

    @Synchronized
    fun connect(): Socket {
        val s = get()
        if (!s.connected()) s.connect()
        return s
    }

    @Synchronized
    fun disconnect() {
        socket?.disconnect()
    }

    @Synchronized
    fun shutdown() {
        socket?.let {
            it.disconnect()
            it.off()
        }
        socket = null
        lastTokenUsed = null
    }
}
