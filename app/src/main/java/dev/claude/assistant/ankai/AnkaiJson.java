package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sehr kleiner JSON-Parser fuer die Ankai-Antworten.
 *
 * Bewusst ohne org.json, damit die Netzwerkschicht auch auf einer normalen JVM
 * getestet werden kann und nicht vom Android-Framework abhaengt.
 */
public final class AnkaiJson {

    private final String text;
    private int pos;

    private AnkaiJson(String text) {
        this.text = text;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        AnkaiJson parser = new AnkaiJson(json == null ? "" : json);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw new IllegalArgumentException("Unerwarteter Inhalt nach dem JSON-Objekt");
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON-Objekt erwartet");
        }
        return (Map<String, Object>) value;
    }

    public static String string(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof String ? (String) value : null;
    }

    public static int intValue(Map<String, Object> object, String key, int fallback) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Double ? (int) Math.round((Double) value) : fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof List ? (List<Object>) value : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    public static String escape(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= text.length()) throw new IllegalArgumentException("Unerwartetes Ende");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': return readLiteral("true", Boolean.TRUE);
            case 'f': return readLiteral("false", Boolean.FALSE);
            case 'n': return readLiteral("null", null);
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') { pos++; return result; }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            result.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') return result;
            if (c != ',') throw new IllegalArgumentException("',' oder '}' erwartet");
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') { pos++; return result; }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return result;
            if (c != ',') throw new IllegalArgumentException("',' oder ']' erwartet");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            char escaped = next();
            switch (escaped) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) throw new IllegalArgumentException("Kaputte \\u-Sequenz");
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw new IllegalArgumentException("Unbekannte Escape-Sequenz \\" + escaped);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw new IllegalArgumentException("Zahl erwartet");
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungueltige Zahl", e);
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) throw new IllegalArgumentException(literal + " erwartet");
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= text.length()) throw new IllegalArgumentException("Unerwartetes Ende");
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        if (next() != expected) throw new IllegalArgumentException("'" + expected + "' erwartet");
    }
}
