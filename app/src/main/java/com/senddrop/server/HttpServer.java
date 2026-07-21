package com.senddrop.server;

import android.util.Log;

import com.senddrop.android.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "SendDropServer";
    private static final int PORT = 8081;
    private boolean isRunning = false;
    
    public HttpServer() {
        super(PORT);
    }
    
    // Переименовали метод, чтобы не конфликтовать с родительским start()
    public boolean startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            isRunning = true;
            Log.d(TAG, "Server started on port " + PORT);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server: " + e.getMessage());
            return false;
        }
    }
    
    public void stopServer() {
        if (isRunning) {
            stop();
            isRunning = false;
            Log.d(TAG, "Server stopped");
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public String getUrl() {
        String ip = getLocalIp();
        return "http://" + ip + ":" + PORT;
    }
    
    private String getLocalIp() {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    public List<File> getFileList() {
        List<File> files = new ArrayList<>();
        File dir = new File(App.FILES_DIR);
        if (dir.exists()) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile()) {
                        files.add(f);
                    }
                }
            }
        }
        return files;
    }
    
    public boolean deleteFile(String filename) {
        File file = new File(App.FILES_DIR, filename);
        return file.exists() && file.delete();
    }
    
    public File getFile(String filename) {
        File file = new File(App.FILES_DIR, filename);
        return file.exists() ? file : null;
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();
        String method = session.getMethod().toString();
        
        Log.d(TAG, "Request: " + method + " " + uri);
        
        if ("/".equals(uri)) {
            String response = "<html><body>" +
                "<h1>📁 SendDrop Android</h1>" +
                "<p>Сервер работает! Используйте /upload для загрузки файлов</p>" +
                "<p>Текущие файлы: <a href='/files'>список</a></p>" +
                "</body></html>";
            return newFixedLengthResponse(response);
        }
        
        if ("/files".equals(uri)) {
            File dir = new File(App.FILES_DIR);
            if (!dir.exists()) dir.mkdirs();
            File[] files = dir.listFiles();
            StringBuilder json = new StringBuilder("[");
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (!files[i].isFile()) continue;
                    json.append("{");
                    json.append("\"name\":\"").append(files[i].getName()).append("\",");
                    json.append("\"size\":").append(files[i].length());
                    json.append("}");
                    if (i < files.length - 1) json.append(",");
                }
            }
            json.append("]");
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
        }
        
        if ("/upload".equals(uri) && "POST".equals(method)) {
            try {
                Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                String filename = files.get("file");
                if (filename == null) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file");
                }
                File dir = new File(App.FILES_DIR);
                if (!dir.exists()) dir.mkdirs();
                File tmpFile = new File(filename);
                if (tmpFile.exists()) {
                    File destFile = new File(App.FILES_DIR, tmpFile.getName());
                    try (FileInputStream fis = new FileInputStream(tmpFile);
                         FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    tmpFile.delete();
                    Log.d(TAG, "File saved: " + destFile.getName());
                    return newFixedLengthResponse("OK");
                } else {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file content");
                }
            } catch (Exception e) {
                Log.e(TAG, "Upload error: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }
        
        if ("/download".equals(uri)) {
            String filename = params.get("file");
            if (filename == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing file parameter");
            }
            try {
                filename = URLDecoder.decode(filename, "UTF-8");
                File file = new File(App.FILES_DIR, filename);
                if (!file.exists()) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                }
                return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", 
                    new FileInputStream(file), file.length());
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }
        
        if ("/delete".equals(uri)) {
            String filename = params.get("file");
            if (filename == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing file parameter");
            }
            try {
                filename = URLDecoder.decode(filename, "UTF-8");
                File file = new File(App.FILES_DIR, filename);
                if (file.exists() && file.delete()) {
                    return newFixedLengthResponse("OK");
                } else {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to delete");
                }
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }
        
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
    }
}
