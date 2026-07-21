package com.senddrop.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.senddrop.android.App;
import com.senddrop.android.R;  // <-- ЭТОТ ИМПОРТ
import com.senddrop.server.HttpServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int REQUEST_CODE_PICK_FILE = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    
    private HttpServer server;
    private TextView statusText;
    private TextView ipText;
    private Button startButton;
    private Button uploadButton;
    private Button refreshButton;
    private Button copyLinkButton;
    private ImageView qrImage;
    private ListView fileListView;
    private ArrayAdapter<String> fileAdapter;
    private List<String> fileNames = new ArrayList<>();
    private List<File> fileObjects = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        server = new HttpServer();
        
        statusText = findViewById(R.id.statusText);
        ipText = findViewById(R.id.ipText);
        startButton = findViewById(R.id.startButton);
        uploadButton = findViewById(R.id.uploadButton);
        refreshButton = findViewById(R.id.refreshButton);
        copyLinkButton = findViewById(R.id.copyLinkButton);
        qrImage = findViewById(R.id.qrImage);
        fileListView = findViewById(R.id.fileListView);
        
        fileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileNames);
        fileListView.setAdapter(fileAdapter);
        
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            Toast.makeText(this, "Файл: " + fileObjects.get(position).getName(), Toast.LENGTH_SHORT).show();
        });
        
        fileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            deleteFile(position);
            return true;
        });
        
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
        
        startButton.setOnClickListener(v -> toggleServer());
        uploadButton.setOnClickListener(v -> selectFile());
        refreshButton.setOnClickListener(v -> refreshFileList());
        copyLinkButton.setOnClickListener(v -> copyLink());
        
        refreshFileList();
    }
    
    private void toggleServer() {
        if (server.isRunning()) {
            server.stopServer();
            startButton.setText("Запустить сервер");
            statusText.setText("🔴 Сервер остановлен");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            qrImage.setImageBitmap(null);
            copyLinkButton.setEnabled(false);
        } else {
            if (server.startServer()) {
                startButton.setText("Остановить сервер");
                statusText.setText("🟢 Сервер запущен: " + server.getUrl());
                statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                ipText.setText("IP: " + server.getUrl().replace("http://", "").split(":")[0]);
                generateQR(server.getUrl());
                copyLinkButton.setEnabled(true);
                Toast.makeText(this, "Сервер запущен на " + server.getUrl(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Не удалось запустить сервер", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void generateQR(String data) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            qrImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Log.e("QR", "Error generating QR: " + e.getMessage());
        }
    }
    
    private void selectFile() {
        if (!server.isRunning()) {
            Toast.makeText(this, "Сначала запустите сервер", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Выберите файл"), REQUEST_CODE_PICK_FILE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String filename = getFileName(uri);
                try {
                    File destFile = new File(App.FILES_DIR, filename);
                    if (!destFile.getParentFile().exists()) {
                        destFile.getParentFile().mkdirs();
                    }
                    try (InputStream is = getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    Toast.makeText(this, "Файл загружен: " + filename, Toast.LENGTH_SHORT).show();
                    refreshFileList();
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
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
    
    private void refreshFileList() {
        fileNames.clear();
        fileObjects.clear();
        List<File> files = server.getFileList();
        for (File f : files) {
            fileNames.add(f.getName() + " (" + formatSize(f.length()) + ")");
            fileObjects.add(f);
        }
        fileAdapter.notifyDataSetChanged();
    }
    
    private void deleteFile(int position) {
        File file = fileObjects.get(position);
        if (file != null && file.exists() && file.delete()) {
            Toast.makeText(this, "Файл удалён: " + file.getName(), Toast.LENGTH_SHORT).show();
            refreshFileList();
        } else {
            Toast.makeText(this, "Не удалось удалить файл", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyLink() {
        if (!server.isRunning()) {
            Toast.makeText(this, "Сервер не запущен", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = server.getUrl();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SendDrop Link", url);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Ссылка скопирована: " + url, Toast.LENGTH_SHORT).show();
    }
    
    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        if (size < 1073741824) return String.format("%.1f MB", size / 1048576.0);
        return String.format("%.1f GB", size / 1073741824.0);
    }
}
