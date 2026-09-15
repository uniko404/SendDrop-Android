package com.senddrop.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.senddrop.android.App
import com.senddrop.android.Discovery
import com.senddrop.android.Peer
import com.senddrop.android.R
import com.senddrop.android.Transfer
import java.io.IOException
import java.net.NetworkInterface

class MainActivity : AppCompatActivity(), Discovery.OnPeerListChangedListener, Transfer.OnFileReceiveListener {

    private var discovery: Discovery? = null
    private var transfer: Transfer? = null

    // Инициализируем UI без дурацких null
    private lateinit var peerListView: ListView
    private lateinit var peerAdapter: ArrayAdapter<String>
    private val peerNames = mutableListOf<String>()
    private val peerObjects = mutableListOf<Peer>()

    private lateinit var statusText: TextView
    private lateinit var myIpText: TextView
    private lateinit var refreshBtn: Button
    private lateinit var sendFileBtn: Button

    private var selectedPeer: Peer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        myIpText = findViewById(R.id.ipText)
        peerListView = findViewById(R.id.fileListView)
        refreshBtn = findViewById(R.id.refreshButton)
        sendFileBtn = findViewById(R.id.uploadButton)
        val copyLinkBtn = findViewById<Button>(R.id.copyLinkButton)

        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, peerNames)
        peerListView.adapter = peerAdapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    REQUEST_PERMISSIONS
                )
            }
        }

        discovery = Discovery(Build.MODEL, this).apply { start() }
        transfer = Transfer(App.FILES_DIR, this).apply { startServer() }

        val currentIp = localIpAddress
        myIpText.text = "Мой IP: $currentIp"

        // Нормальные Kotlin-лямбды
        refreshBtn.setOnClickListener {
            Toast.makeText(this, "Поиск устройств...", Toast.LENGTH_SHORT).show()
        }

        sendFileBtn.setOnClickListener {
            if (selectedPeer == null) {
                Toast.makeText(this, "Сначала выберите устройство из списка", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Теперь метка резолвится
            }
            selectFileToSend()
        }

        copyLinkBtn.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("My IP", currentIp)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP скопирован: $currentIp", Toast.LENGTH_SHORT).show()
        }

        peerListView.setOnItemClickListener { _, _, position, _ ->
            if (position < peerObjects.size) {
                val peer = peerObjects[position]
                selectedPeer = peer
                statusText.text = "Выбрано: ${peer.name} (${peer.ip})"
                Toast.makeText(this, "Выбрано устройство: ${peer.name}", Toast.LENGTH_SHORT).show()
            }
        }

        statusText.text = "Ожидание устройств..."
    }

    private fun selectFileToSend() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), REQUEST_CODE_PICK_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val peer = selectedPeer ?: return

            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val fileData = ByteArray(stream.available())
                    stream.read(fileData)

                    // Жестко гарантируем строку, никаких String?
                    val filename = getFileName(uri) ?: "unknown_file"
                    transfer?.sendFile(peer.ip, filename, fileData)

                    Toast.makeText(this, "Отправка файла $filename на ${peer.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        return result ?: uri.lastPathSegment
    }

    // Возвращаем строго String, а не String?
    private val localIpAddress: String
        get() {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (iface in interfaces) {
                    for (addr in iface.inetAddresses) {
                        if (!addr.isLoopbackAddress && !addr.hostAddress.isNullOrEmpty() && !addr.hostAddress!!.contains(':')) {
                            return addr.hostAddress!!
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IP", "Error: ${e.message}")
            }
            return "127.0.0.1"
        }

    // Сигнатуры исправлены на нуллабельные для совместимости с Java
    override fun onPeerAdded(peer: Peer?) {
        if (peer == null) return
        runOnUiThread {
            peerObjects.add(peer)
            peerNames.add("${peer.name} (${peer.ip})")
            peerAdapter.notifyDataSetChanged()
            statusText.text = "Найдено устройств: ${peerObjects.size}"
        }
    }

    override fun onPeerRemoved(peer: Peer?) {
        if (peer == null) return
        runOnUiThread {
            val iterator = peerObjects.iterator()
            var index = 0
            while (iterator.hasNext()) {
                val current = iterator.next()
                if (current.ip == peer.ip) {
                    iterator.remove()
                    if (index < peerNames.size) peerNames.removeAt(index)
                    peerAdapter.notifyDataSetChanged()
                    break
                }
                index++
            }

            if (selectedPeer?.ip == peer.ip) {
                selectedPeer = null
                statusText.text = "Устройство отключено"
            }
            statusText.text = "Найдено устройств: ${peerObjects.size}"
        }
    }

    override fun onFileReceived(filename: String?, size: Long) {
        runOnUiThread {
            val safeName = filename ?: "unknown"
            Toast.makeText(this, "Файл получен: $safeName ($size bytes)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discovery?.stop()
        transfer?.stopServer()
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1001
        private const val REQUEST_PERMISSIONS = 1002
    }
}