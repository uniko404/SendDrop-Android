package com.senddrop.android;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Transfer {
    private static final String TAG = "SendDropTransfer";
    private static final int PORT = 8082;
    private ServerSocket serverSocket;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private OnFileReceiveListener listener;
    private String saveDir;

    public interface OnFileReceiveListener {
        void onFileReceived(String filename, long size);
    }

    public Transfer(String saveDir, OnFileReceiveListener listener) {
        this.saveDir = saveDir;
        this.listener = listener;
    }

    public void startServer() {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Transfer server started on port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        });
    }

    public void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedInputStream bis = new BufferedInputStream(socket.getInputStream())) {

            // Читаем заголовок: SEND_FILE|filename|size
            String header = reader.readLine();
            if (header == null || !header.startsWith("SEND_FILE|")) {
                return;
            }
            String[] parts = header.substring(10).split("\\|");
            if (parts.length != 2) {
                return;
            }
            String filename = parts[0];
            long size = Long.parseLong(parts[1]);

            // Читаем данные
            byte[] data = new byte[(int) size];
            int read = 0;
            while (read < size) {
                int n = bis.read(data, read, (int) size - read);
                if (n < 0) break;
                read += n;
            }
            if (read == size) {
                // Сохраняем файл
                File dir = new File(saveDir);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                }
                Log.d(TAG, "File saved: " + filename);
                if (listener != null) {
                    listener.onFileReceived(filename, size);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle client error: " + e.getMessage());
        }
    }

    public void sendFile(String ip, String filename, byte[] data) {
        executor.execute(() -> {
            try (Socket socket = new Socket(ip, PORT);
                 OutputStream os = socket.getOutputStream()) {
                // Заголовок
                String header = "SEND_FILE|" + filename + "|" + data.length + "\n";
                os.write(header.getBytes());
                os.write(data);
                os.flush();
                Log.d(TAG, "File sent: " + filename + " to " + ip);
            } catch (Exception e) {
                Log.e(TAG, "Send error: " + e.getMessage());
            }
        });
    }
}
