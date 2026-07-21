package com.senddrop.android;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Discovery {
    private static final String TAG = "SendDropDiscovery";
    private static final int PORT = 9999;
    private static final String DISCOVERY_MSG = "SENDDROP_DISCOVERY";
    private static final String RESPONSE_PREFIX = "SENDDROP_RESPONSE|";

    private ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    private OnPeerListChangedListener listener;
    private String localIp;
    private String deviceName;
    private DatagramSocket socket;
    private boolean running = false;

    public interface OnPeerListChangedListener {
        void onPeerAdded(Peer peer);
        void onPeerRemoved(Peer peer);
    }

    public Discovery(String deviceName, OnPeerListChangedListener listener) {
        this.deviceName = deviceName;
        this.listener = listener;
        this.localIp = getLocalIpAddress();
    }

    public void start() {
        running = true;
        startBroadcastThread();
        startListenThread();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public List<Peer> getPeers() {
        return new ArrayList<>(peers.values());
    }

    private void startBroadcastThread() {
        new Thread(() -> {
            while (running) {
                try (DatagramSocket ds = new DatagramSocket()) {
                    ds.setBroadcast(true);
                    byte[] data = DISCOVERY_MSG.getBytes();
                    DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            InetAddress.getByName("255.255.255.255"),
                            PORT
                    );
                    ds.send(packet);
                } catch (IOException e) {
                    Log.e(TAG, "Broadcast error: " + e.getMessage());
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void startListenThread() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    InetAddress remoteAddr = packet.getAddress();
                    String remoteIp = remoteAddr.getHostAddress();

                    if (msg.equals(DISCOVERY_MSG)) {
                        // Кто-то ищет устройства – отвечаем
                        String response = RESPONSE_PREFIX + localIp + "|" + deviceName;
                        byte[] respData = response.getBytes();
                        DatagramPacket reply = new DatagramPacket(
                                respData,
                                respData.length,
                                remoteAddr,
                                packet.getPort()
                        );
                        socket.send(reply);
                    } else if (msg.startsWith(RESPONSE_PREFIX)) {
                        // Получили ответ от другого устройства
                        String[] parts = msg.substring(RESPONSE_PREFIX.length()).split("\\|");
                        if (parts.length >= 2) {
                            String ip = parts[0];
                            String name = parts[1];
                            if (!ip.equals(localIp)) {
                                addPeer(ip, name);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Listen error: " + e.getMessage());
            }
        }).start();
    }

    private void addPeer(String ip, String name) {
        if (!peers.containsKey(ip)) {
            Peer peer = new Peer(ip, name);
            peers.put(ip, peer);
            if (listener != null) {
                listener.onPeerAdded(peer);
            }
        }
    }

    public void removePeer(String ip) {
        Peer removed = peers.remove(ip);
        if (removed != null && listener != null) {
            listener.onPeerRemoved(removed);
        }
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
            Log.e(TAG, "IP error: " + e.getMessage());
        }
        return "127.0.0.1";
    }
}
