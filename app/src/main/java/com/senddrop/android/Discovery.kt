package com.senddrop.android

import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

class Discovery(private val deviceName: String?, private val listener: OnPeerListChangedListener?) {
    private val peers = ConcurrentHashMap<String?, Peer?>()
    private val localIp: String
    private var socket: DatagramSocket? = null
    private var running = false

    interface OnPeerListChangedListener {
        fun onPeerAdded(peer: Peer?)
        fun onPeerRemoved(peer: Peer?)
    }

    init {
        this.localIp = this.localIpAddress
    }

    fun start() {
        running = true
        startBroadcastThread()
        startListenThread()
    }

    fun stop() {
        running = false
        if (socket != null && !socket!!.isClosed()) {
            socket!!.close()
        }
    }

    fun getPeers(): MutableList<Peer?> {
        return ArrayList<Peer?>(peers.values)
    }

    private fun startBroadcastThread() {
        Thread(Runnable {
            while (running) {
                try {
                    DatagramSocket().use { ds ->
                        ds.setBroadcast(true)
                        val data: ByteArray = DISCOVERY_MSG.toByteArray()
                        val packet = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("255.255.255.255"),
                            PORT
                        )
                        ds.send(packet)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Broadcast error: " + e.message)
                }
                try {
                    Thread.sleep(3000)
                } catch (ignored: InterruptedException) {
                }
            }
        }).start()
    }

    private fun startListenThread() {
        Thread(Runnable {
            try {
                socket = DatagramSocket(PORT)
                socket!!.setBroadcast(true)
                val buffer = ByteArray(1024)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket!!.receive(packet)
                    val msg = String(packet.getData(), 0, packet.getLength())
                    val remoteAddr = packet.getAddress()
                    val remoteIp = remoteAddr.getHostAddress()

                    if (msg == DISCOVERY_MSG) {
                        // Кто-то ищет устройства – отвечаем
                        val response: String = RESPONSE_PREFIX + localIp + "|" + deviceName
                        val respData = response.toByteArray()
                        val reply = DatagramPacket(
                            respData,
                            respData.size,
                            remoteAddr,
                            packet.getPort()
                        )
                        socket!!.send(reply)
                    } else if (msg.startsWith(RESPONSE_PREFIX)) {
                        // Получили ответ от другого устройства
                        val parts = msg.substring(RESPONSE_PREFIX.length).split("\\|".toRegex())
                            .dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (parts.size >= 2) {
                            val ip = parts[0]
                            val name: String? = parts[1]
                            if (ip != localIp) {
                                addPeer(ip, name)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Listen error: " + e.message)
            }
        }).start()
    }

    private fun addPeer(ip: String, name: String?) {
        if (!peers.containsKey(ip)) {
            val peer = Peer(ip, name)
            peers.put(ip, peer)
            if (listener != null) {
                listener.onPeerAdded(peer)
            }
        }
    }

    fun removePeer(ip: String) {
        val removed: Peer? = peers.remove(ip)
        if (removed != null && listener != null) {
            listener.onPeerRemoved(removed)
        }
    }

    private val localIpAddress: String
        get() {
            try {
                val interfaces =
                    NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    val addresses =
                        iface.getInetAddresses()
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            return addr.getHostAddress()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "IP error: " + e.message)
            }
            return "127.0.0.1"
        }

    companion object {
        private const val TAG = "SendDropDiscovery"
        private const val PORT = 9999
        private const val DISCOVERY_MSG = "SENDDROP_DISCOVERY"
        private const val RESPONSE_PREFIX = "SENDDROP_RESPONSE|"
    }
}
