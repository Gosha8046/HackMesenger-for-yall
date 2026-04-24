package com.hack.messenger

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import kotlin.concurrent.thread

object NetworkManager {

    const val DEFAULT_PORT = 55555

    interface Listener {
        fun onConnected(addr: String)
        fun onDisconnected(addr: String)
        fun onMessage(packet: JSONObject)
        fun onLog(msg: String)
        fun onError(msg: String)
    }

    var listener: Listener? = null

    private var serverSocket: ServerSocket? = null
    private val clients = mutableMapOf<String, Socket>()
    private var clientSocket: Socket? = null
    private var isServer = false

    // ── SERVER ──────────────────────────────────────────────────────
    fun startServer(port: Int = DEFAULT_PORT, key: String = "") {
        stopAll()
        isServer = true
        thread(isDaemon = true) {
            try {
                serverSocket = ServerSocket(port)
                listener?.onLog("// сервер запущен на порту $port //")
                while (!serverSocket!!.isClosed) {
                    val conn = serverSocket!!.accept()
                    val addr = conn.inetAddress.hostAddress ?: "?"
                    clients[addr] = conn
                    listener?.onConnected(addr)
                    listener?.onLog("// подключился: $addr //")
                    sendSystem("// $addr подключился //")
                    thread(isDaemon = true) { handleClient(conn, addr, key) }
                }
            } catch (e: Exception) {
                if (!serverSocket?.isClosed!!) listener?.onError("// ошибка сервера: $e //")
            }
        }
    }

    private fun handleClient(sock: Socket, addr: String, key: String) {
        try {
            val dis = DataInputStream(sock.getInputStream())
            while (!sock.isClosed) {
                val len = dis.readInt()
                if (len <= 0 || len > 10_000_000) break
                val bytes = ByteArray(len)
                dis.readFully(bytes)
                val payload = if (key.isNotEmpty()) xorCrypt(bytes, key) else bytes
                val pkt = JSONObject(String(payload, Charsets.UTF_8))
                // relay to others
                broadcast(pkt, exclude = addr)
                listener?.onMessage(pkt)
            }
        } catch (_: Exception) {}
        clients.remove(addr)
        listener?.onDisconnected(addr)
        sendSystem("// $addr отключился //")
        listener?.onLog("// отключился: $addr //")
    }

    fun broadcast(pkt: JSONObject, exclude: String? = null) {
        clients.forEach { (addr, sock) ->
            if (addr != exclude) sendTo(sock, pkt)
        }
    }

    // ── CLIENT ──────────────────────────────────────────────────────
    fun connectTo(host: String, port: Int = DEFAULT_PORT, key: String = "",
                  myName: String = "ANON", onDone: (Boolean) -> Unit) {
        stopAll()
        isServer = false
        thread(isDaemon = true) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 8000)
                clientSocket = sock
                listener?.onConnected(host)
                listener?.onLog("// подключён к $host:$port //")
                onDone(true)
                // announce
                sendPacket(JSONObject().apply {
                    put("type","system"); put("text","// $myName подключился //")
                    put("time", timeNow()); put("chat","general")
                })
                // read loop
                val dis = DataInputStream(sock.getInputStream())
                while (!sock.isClosed) {
                    val len = dis.readInt()
                    if (len <= 0 || len > 10_000_000) break
                    val bytes = ByteArray(len)
                    dis.readFully(bytes)
                    val payload = if (key.isNotEmpty()) xorCrypt(bytes, key) else bytes
                    val pkt = JSONObject(String(payload, Charsets.UTF_8))
                    listener?.onMessage(pkt)
                }
            } catch (e: Exception) {
                onDone(false)
                listener?.onError("// ошибка: $e //")
            }
            listener?.onDisconnected(host)
            clientSocket = null
        }
    }

    fun sendPacket(pkt: JSONObject, key: String = "") {
        if (isServer) {
            broadcast(pkt)
        } else {
            clientSocket?.let { sendTo(it, pkt, key) }
        }
    }

    private fun sendTo(sock: Socket, pkt: JSONObject, key: String = "") {
        try {
            val bytes = pkt.toString().toByteArray(Charsets.UTF_8)
            val payload = if (key.isNotEmpty()) xorCrypt(bytes, key) else bytes
            val dos = DataOutputStream(sock.getOutputStream())
            synchronized(sock) {
                dos.writeInt(payload.size)
                dos.write(payload)
                dos.flush()
            }
        } catch (_: Exception) {}
    }

    private fun sendSystem(text: String) {
        val pkt = JSONObject().apply {
            put("type","system"); put("text", text)
            put("time", timeNow()); put("chat","general")
        }
        listener?.onMessage(pkt)
    }

    fun stopAll() {
        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        clients.values.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        serverSocket = null
        clientSocket = null
    }

    fun isConnected() = clientSocket?.isConnected == true && !clientSocket!!.isClosed
    fun isServerRunning() = serverSocket != null && !serverSocket!!.isClosed
    fun connectedCount() = clients.size

    // ── CRYPTO ──────────────────────────────────────────────────────
    fun xorCrypt(data: ByteArray, key: String): ByteArray {
        val k = key.toByteArray()
        return ByteArray(data.size) { i -> (data[i].toInt() xor k[i % k.size].toInt()).toByte() }
    }

    fun timeNow(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun myIp(): String = try {
        val s = Socket(); s.connect(InetSocketAddress("8.8.8.8", 80), 2000)
        val ip = s.localAddress.hostAddress ?: "?"
        s.close(); ip
    } catch (_: Exception) { "127.0.0.1" }
}
