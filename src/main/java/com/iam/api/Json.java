package com.iam.api;

import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader/writer so the API needs no extra libraries.
 * Writes maps, lists, strings, numbers, booleans, enums, dates and Java records.
 * Reads any JSON; objects become LinkedHashMap, arrays ArrayList, numbers Long or Double.
 */
public final class Json {

    private Json() { }

    // ------------------------------------------------------------------ writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append((long) d); else sb.append(d);
        } else if (v instanceof Enum<?> e) {
            quote(sb, e.name());
        } else if (v instanceof Temporal || v instanceof CharSequence) {
            quote(sb, v.toString());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else if (v.getClass().isRecord()) {
            sb.append('{');
            boolean first = true;
            for (RecordComponent rc : v.getClass().getRecordComponents()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, rc.getName());
                sb.append(':');
                try {
                    write(sb, rc.getAccessor().invoke(v));
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Cannot serialise " + v.getClass(), ex);
                }
            }
            sb.append('}');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch)); else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ reading

    /** Parses a JSON text whose top level is an object. Throws IllegalArgumentException when it is not valid. */
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) {
            throw p.error("Unexpected text after JSON value");
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON: " + msg + " at position " + i);
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            if (i >= s.length()) throw error("unexpected end");
            char ch = s.charAt(i);
            if (ch == '{') return object();
            if (ch == '[') return array();
            if (ch == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (ch == '-' || (ch >= '0' && ch <= '9')) return number();
            throw error("unexpected character '" + ch + "'");
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw error("object key must be a string");
                String key = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw error("expected ':'");
                i++;
                ws();
                m.put(key, value());
                ws();
                if (i >= s.length()) throw error("unterminated object");
                char ch = s.charAt(i++);
                if (ch == '}') return m;
                if (ch != ',') throw error("expected ',' or '}'");
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                ws();
                list.add(value());
                ws();
                if (i >= s.length()) throw error("unterminated array");
                char ch = s.charAt(i++);
                if (ch == ']') return list;
                if (ch != ',') throw error("expected ',' or ']'");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (true) {
                if (i >= s.length()) throw error("unterminated string");
                char ch = s.charAt(i++);
                if (ch == '"') return sb.toString();
                if (ch != '\\') { sb.append(ch); continue; }
                if (i >= s.length()) throw error("bad escape");
                char esc = s.charAt(i++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("bad \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("bad \\u escape");
                        }
                        i += 4;
                    }
                    default -> throw error("bad escape '\\" + esc + "'");
                }
            }
        }

        Object number() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                i = start;
                throw error("bad number '" + t + "'");
            }
        }
    }
}
