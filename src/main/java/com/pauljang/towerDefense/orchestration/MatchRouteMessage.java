package com.pauljang.towerDefense.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Redis pub/sub payload that tells the Velocity proxy where to send a match's players once the
 * container is up. The lobby publishes it on {@link #CHANNEL}; the Velocity-side plugin subscribes,
 * registers {@code host:port} as a server, and transfers each listed player to it.
 *
 * <p>Wire format (JSON), e.g.:
 * <pre>{@code
 * {"type":"MATCH_READY","matchId":"8f3a-1","host":"10.0.0.5","port":49154,
 *  "players":["6b1c8e2a-...","9d2e4f10-..."]}
 * }</pre>
 */
public record MatchRouteMessage(String matchId, String host, int port, List<UUID> players) {

    /** Redis channel the lobby publishes to and Velocity subscribes to. */
    public static final String CHANNEL = "td:match:route";

    /** Discriminator so consumers can fan out on message kind on a shared channel. */
    public static final String TYPE = "MATCH_READY";

    public MatchRouteMessage {
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId required");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host required");
        players = players == null ? List.of() : List.copyOf(players);
    }

    public static MatchRouteMessage of(MatchInstance instance, List<UUID> players) {
        return new MatchRouteMessage(instance.matchId(), instance.host(), instance.port(), players);
    }

    /** Minimal hand-rolled JSON so the plugin needs no JSON dependency on either side. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"type\":\"").append(TYPE).append('"')
          .append(",\"matchId\":\"").append(escape(matchId)).append('"')
          .append(",\"host\":\"").append(escape(host)).append('"')
          .append(",\"port\":").append(port)
          .append(",\"players\":[");
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(players.get(i).toString())).append('"');
        }
        return sb.append("]}").toString();
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    /** Parses the {@link #toJson()} wire format back into a message. Throws on malformed input. */
    public static MatchRouteMessage fromJson(String json) {
        Object root = new JsonReader(json).parseValue();
        if (!(root instanceof Map<?, ?> obj)) {
            throw new IllegalArgumentException("expected a JSON object: " + json);
        }
        Object matchId = obj.get("matchId");
        Object host = obj.get("host");
        Object port = obj.get("port");
        Object players = obj.get("players");
        if (!(matchId instanceof String mId) || !(host instanceof String h) || !(port instanceof Number pNum)) {
            throw new IllegalArgumentException("missing/invalid matchId/host/port in: " + json);
        }
        List<UUID> roster = new ArrayList<>();
        if (players instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof String s && !s.isEmpty()) roster.add(UUID.fromString(s));
            }
        }
        return new MatchRouteMessage(mId, h, pNum.intValue(), roster);
    }

    /** Minimal recursive-descent JSON reader — just enough to parse {@link #toJson()} output. */
    private static final class JsonReader {
        private final String s;
        private int i;

        JsonReader(String s) { this.s = s; }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw err("unexpected end of input");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                char c = afterWs();
                if (c == '}') break;
                if (c != ',') throw err("expected ',' or '}'");
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                char c = afterWs();
                if (c == ']') break;
                if (c != ',') throw err("expected ',' or ']'");
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> b.append('"');
                        case '\\' -> b.append('\\');
                        case '/' -> b.append('/');
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> throw err("invalid escape \\" + e);
                    }
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }

        private Object parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.isEmpty()) throw err("invalid number");
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw err("invalid literal");
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw err("invalid literal");
        }

        private void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private char peek() { skipWs(); return i < s.length() ? s.charAt(i) : '\0'; }
        private char afterWs() { skipWs(); return next(); }
        private char next() { if (i >= s.length()) throw err("unexpected end of input"); return s.charAt(i++); }
        private void expect(char c) { char a = afterWs(); if (a != c) throw err("expected '" + c + "' but got '" + a + "'"); }
        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON parse error at " + i + ": " + msg);
        }
    }
}
