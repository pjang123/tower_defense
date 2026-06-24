package com.pauljang.towerDefense.orchestration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Tiny Redis {@code PUBLISH} client implemented directly on the RESP protocol over a socket, so the
 * plugin pulls in no Jedis/Lettuce dependency. One short-lived connection per publish — fine for the
 * low-frequency "match is ready" signal; swap in a pooled client if volume ever grows.
 */
public final class RedisPublisher {

    private final String host;
    private final int port;
    private final String password; // nullable
    private final int timeoutMillis;

    public RedisPublisher(String host, int port) {
        this(host, port, null, 3000);
    }

    public RedisPublisher(String host, int port, String password, int timeoutMillis) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.timeoutMillis = timeoutMillis;
    }

    /** Publishes {@code message} to {@code channel}; returns the number of subscribers that received it. */
    public long publish(String channel, String message) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            if (password != null && !password.isEmpty()) {
                out.write(resp("AUTH", password));
                out.flush();
                readReply(in); // +OK or -ERR
            }
            out.write(resp("PUBLISH", channel, message));
            out.flush();
            Object reply = readReply(in);
            return reply instanceof Long l ? l : 0L;
        }
    }

    /** Encodes a command as a RESP array of bulk strings. */
    private static byte[] resp(String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (String a : args) {
            int byteLen = a.getBytes(StandardCharsets.UTF_8).length;
            sb.append('$').append(byteLen).append("\r\n").append(a).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Reads one RESP reply — the subset needed here: +simple, -error, :integer. */
    private static Object readReply(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix < 0) throw new IOException("Redis closed the connection");
        String line = readLine(in);
        return switch (prefix) {
            case '+' -> line;
            case ':' -> Long.parseLong(line);
            case '-' -> throw new IOException("Redis error: " + line);
            default  -> line; // bulk/array unexpected for PUBLISH/AUTH; return raw
        };
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\r') {
                if (in.read() != '\n') throw new IOException("malformed RESP line terminator");
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }
}
