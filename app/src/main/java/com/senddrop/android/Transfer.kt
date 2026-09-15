package com.senddrop.android

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Transfer(private val saveDir: String?, private val listener: OnFileReceiveListener?) {
    private var serverSocket: ServerSocket? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    interface OnFileReceiveListener {
        fun onFileReceived(filename: String?, size: Long)
    }

    fun startServer() {
        executor.execute(Runnable {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Transfer server started on port " + PORT)
                while (true) {
                    val client = serverSocket!!.accept()
                    executor.execute(Runnable { handleClient(client) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: " + e.message)
            }
        })
    }

    fun stopServer() {
        try {
            if (serverSocket != null && !serverSocket!!.isClosed()) {
                serverSocket!!.close()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                BufferedInputStream(socket.getInputStream()).use { bis ->

                    // Читаем заголовок: SEND_FILE|filename|size
                    val header = reader.readLine()
                    if (header == null || !header.startsWith("SEND_FILE|")) {
                        return
                    }
                    val parts =
                        header.substring(10).split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (parts.size != 2) {
                        return
                    }
                    val filename = parts[0]
                    val size = parts[1].toLong()

                    // Читаем данные
                    val data = ByteArray(size.toInt())
                    var read = 0
                    while (read < size) {
                        val n = bis.read(data, read, size.toInt() - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read.toLong() == size) {
                        // Сохраняем файл
                        val dir = File(saveDir)
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, filename)
                        FileOutputStream(file).use { fos ->
                            fos.write(data)
                        }
                        Log.d(TAG, "File saved: " + filename)
                        if (listener != null) {
                            listener.onFileReceived(filename, size)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handle client error: " + e.message)
        }
    }

    fun sendFile(ip: String?, filename: String, data: ByteArray) {
        executor.execute(Runnable {
            try {
                Socket(ip, PORT).use { socket ->
                    socket.getOutputStream().use { os ->
                        // Заголовок
                        val header = "SEND_FILE|" + filename + "|" + data.size + "\n"
                        os.write(header.toByteArray())
                        os.write(data)
                        os.flush()
                        Log.d(TAG, "File sent: " + filename + " to " + ip)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send error: " + e.message)
            }
        })
    }

    companion object {
        private const val TAG = "SendDropTransfer"
        private const val PORT = 8082
    }
}
