package com.pauljang.towerdefense.velocity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proxy-side view of the match-route payload. Deliberately a small copy of the schema defined by
 * {@code com.pauljang.towerDefense.orchestration.MatchRouteMessage} in the Paper plugin — the two
 * run as separate artifacts in separate JVMs, so they share the wire format, not the class. This
 * copy only needs to <em>parse</em> ({@link #fromJson}); the lobby is the only producer.
 */
public record MatchRouteMessage(String matchId, String host, int port, List<UUID> players) {

    /** Must match the producer's channel. */
    public static final String CHANNEL = "td:match:route";

    public MatchRouteMessage {
        players = players == null ? List.of() : List.copyOf(players);
    }

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

    /** Minimal recursive-descent JSON reader — just enough to parse the producer's output. */
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
