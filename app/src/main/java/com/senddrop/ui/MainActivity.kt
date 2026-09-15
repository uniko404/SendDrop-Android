package com.senddrop.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.senddrop.android.App;
import com.senddrop.android.Discovery;
import com.senddrop.android.Peer;
import com.senddrop.android.R;
import com.senddrop.android.Transfer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Discovery.OnPeerListChangedListener, Transfer.OnFileReceiveListener {

    private static final int REQUEST_CODE_PICK_FILE = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    private Discovery discovery;
    private Transfer transfer;
    private ListView peerListView;
    private ArrayAdapter<String> peerAdapter;
    private List<String> peerNames = new ArrayList<>();
    private List<Peer> peerObjects = new ArrayList<>();
    private TextView statusText;
    private TextView myIpText;
    private Button refreshBtn;
    private Button sendFileBtn;
    private Peer selectedPeer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        myIpText = findViewById(R.id.ipText);
        peerListView = findViewById(R.id.fileListView);
        refreshBtn = findViewById(R.id.refreshButton);
        sendFileBtn = findViewById(R.id.uploadButton);
        Button copyLinkBtn = findViewById(R.id.copyLinkButton);

        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, peerNames);
        peerListView.setAdapter(peerAdapter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSIONS);
            }
        }

        String deviceName = Build.MODEL;
        discovery = new Discovery(deviceName, this);
        discovery.start();

        transfer = new Transfer(App.FILES_DIR, this);
        transfer.startServer();

        myIpText.setText("Мой IP: " + getLocalIpAddress());

        refreshBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Поиск устройств...", Toast.LENGTH_SHORT).show();
        });

        sendFileBtn.setOnClickListener(v -> {
            if (selectedPeer == null) {
                Toast.makeText(this, "Сначала выберите устройство из списка", Toast.LENGTH_SHORT).show();
                return;
            }
            selectFileToSend();
        });

        copyLinkBtn.setOnClickListener(v -> {
            String ip = getLocalIpAddress();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("My IP", ip);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "IP скопирован: " + ip, Toast.LENGTH_SHORT).show();
        });

        peerListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < peerObjects.size()) {
                selectedPeer = peerObjects.get(position);
                statusText.setText("Выбрано: " + selectedPeer.name + " (" + selectedPeer.ip + ")");
                Toast.makeText(this, "Выбрано устройство: " + selectedPeer.name, Toast.LENGTH_SHORT).show();
            }
        });

        statusText.setText("Ожидание устройств...");
    }

    private void selectFileToSend() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && selectedPeer != null) {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    byte[] fileData = new byte[is.available()];
                    is.read(fileData);
                    String filename = getFileName(uri);
                    transfer.sendFile(selectedPeer.ip, filename, fileData);
                    Toast.makeText(this, "Отправка файла " + filename + " на " + selectedPeer.name, Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(this, "Ошибка чтения файла: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("IP", "Error: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    @Override
    public void onPeerAdded(Peer peer) {
        runOnUiThread(() -> {
            peerObjects.add(peer);
            peerNames.add(peer.name + " (" + peer.ip + ")");
            peerAdapter.notifyDataSetChanged();
            statusText.setText("Найдено устройств: " + peerObjects.size());
        });
    }

    @Override
    public void onPeerRemoved(Peer peer) {
        runOnUiThread(() -> {
            for (int i = 0; i < peerObjects.size(); i++) {
                if (peerObjects.get(i).ip.equals(peer.ip)) {
                    peerObjects.remove(i);
                    peerNames.remove(i);
                    peerAdapter.notifyDataSetChanged();
                    break;
                }
            }
            if (selectedPeer != null && selectedPeer.ip.equals(peer.ip)) {
                selectedPeer = null;
                statusText.setText("Устройство отключено");
            }
            statusText.setText("Найдено устройств: " + peerObjects.size());
        });
    }

    @Override
    public void onFileReceived(String filename, long size) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Файл получен: " + filename + " (" + size + " bytes)", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discovery != null) discovery.stop();
        if (transfer != null) transfer.stopServer();
    }
}
