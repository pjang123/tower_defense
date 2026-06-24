package com.pauljang.towerdefense.velocity;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal Redis pub/sub subscriber implemented directly on RESP (no Jedis/Lettuce). A daemon thread
 * SUBSCRIBEs to one channel and hands each received payload to {@code onMessage}; it reconnects with
 * a fixed backoff if the connection drops.
 */
public final class RedisSubscriber {

    private final String host;
    private final int port;
    private final String password; // nullable
    private final String channel;
    private final Logger logger;
    private final Consumer<MatchRouteMessage> onMessage;

    private volatile boolean running;
    private volatile Socket socket;
    private Thread thread;

    public RedisSubscriber(String host, int port, String password, String channel,
                           Logger logger, Consumer<MatchRouteMessage> onMessage) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.channel = channel;
        this.logger = logger;
        this.onMessage = onMessage;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "td-router-redis");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
        if (thread != null) thread.interrupt();
    }

    private void run() {
        while (running) {
            try {
                connectAndListen();
            } catch (IOException e) {
                if (running) {
                    logger.warn("[td-router] Redis connection lost ({}); reconnecting in 5s", e.getMessage());
                    sleep(5000);
                }
            }
        }
    }

    private void connectAndListen() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000);
        this.socket = s;
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        if (password != null && !password.isEmpty()) {
            out.write(resp("AUTH", password));
            out.flush();
            readReply(in); // +OK or -ERR
        }
        out.write(resp("SUBSCRIBE", channel));
        out.flush();

        while (running) {
            handle(readReply(in));
        }
    }

    /** A pub/sub delivery arrives as the array ["message", <channel>, <payload>]. */
    private void handle(Object reply) {
        if (!(reply instanceof List<?> arr) || arr.size() < 3) return;
        if (!"message".equals(arr.get(0))) return; // ignore subscribe confirmations, etc.
        if (!(arr.get(2) instanceof String json)) return;
        try {
            onMessage.accept(MatchRouteMessage.fromJson(json));
        } catch (RuntimeException e) {
            logger.warn("[td-router] ignoring malformed route message: {}", e.getMessage());
        }
    }

    // ---- RESP decoding (subset: simple string, error, integer, bulk string, array) ----

    private static Object readReply(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix < 0) throw new IOException("connection closed");
        return switch (prefix) {
            case '+' -> readLine(in);
            case '-' -> throw new IOException("Redis error: " + readLine(in));
            case ':' -> Long.parseLong(readLine(in));
            case '$' -> readBulk(in);
            case '*' -> readArray(in);
            default -> readLine(in);
        };
    }

    private static Object readBulk(InputStream in) throws IOException {
        int len = Integer.parseInt(readLine(in));
        if (len < 0) return null;
        byte[] buf = in.readNBytes(len);
        if (buf.length != len) throw new IOException("short bulk read");
        if (in.read() != '\r' || in.read() != '\n') throw new IOException("bad bulk terminator");
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static List<Object> readArray(InputStream in) throws IOException {
        int count = Integer.parseInt(readLine(in));
        List<Object> list = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) list.add(readReply(in));
        return list;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\r') { in.read(); break; } // consume the trailing \n
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static byte[] resp(String... args) {
        StringBuilder sb = new StringBuilder().append('*').append(args.length).append("\r\n");
        for (String a : args) {
            int len = a.getBytes(StandardCharsets.UTF_8).length;
            sb.append('$').append(len).append("\r\n").append(a).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void closeQuietly() {
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
