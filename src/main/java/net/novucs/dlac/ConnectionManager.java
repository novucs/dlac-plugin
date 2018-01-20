package net.novucs.dlac;

import com.google.gson.Gson;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ConnectionManager extends Thread {

    private static final Gson GSON = new Gson();
    private final BlockingQueue<Request> sendQueue = new LinkedBlockingQueue<>();
    private final AntiCheatPlugin plugin;
    private final AtomicReference<String> host = new AtomicReference<>();
    private final AtomicInteger port = new AtomicInteger(0);

    public AtomicReference<String> getHost() {
        return host;
    }

    public AtomicInteger getPort() {
        return port;
    }

    public ConnectionManager(AntiCheatPlugin plugin) {
        super("dlac-connection-manager");
        this.plugin = plugin;
    }

    public void send(Packet packet) {
        send(packet, (ignore) -> {
        });
    }

    public void send(Packet packet, Consumer<String> responseCallback) {
        Request request = new Request(packet, responseCallback);
        sendQueue.add(request);
    }

    @Override
    public void run() {
        Socket socket = null;

        while (!Thread.interrupted()) {
            try {
                while (!Thread.interrupted()) {
                    Request request = sendQueue.take();
                    socket = new Socket(host.get(), port.get());
                    socket.setSoTimeout(2000);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF(GSON.toJson(request.getPacket()));
                    out.flush();

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    String response = in.readUTF();
                    request.getResponseCallback().accept(response);
                    socket.close();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Unable to communicate with classification server!");
                plugin.getLogger().log(Level.SEVERE, "Retrying in 30 seconds...");
                plugin.getLogger().log(Level.SEVERE, "Stacktrace:", e);

                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                } catch (InterruptedException ignore) {
                    interrupt();
                }
            } catch (InterruptedException ignore) {
                interrupt();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
    }

    @Data
    private class Request {
        private final Packet packet;
        private final Consumer<String> responseCallback;
    }
}
