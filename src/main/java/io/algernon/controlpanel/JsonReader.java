package io.algernon.controlpanel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reader of the JSON grammar whole, in {@code java.base} alone (ADR-302 §5; ADR-285 §3): an object is a
 * {@link Map} in the order written, an array a {@link List}, a string a {@link String}, a number a
 * {@link BigDecimal}, {@code true} and {@code false} a {@link Boolean}, and {@code null} a Java {@code null}. A name
 * given twice keeps its last value. Text that is not one JSON value, with white space alone around it, is refused
 * with an {@link IllegalArgumentException} naming the position.
 */
final class JsonReader {

    /** How deep arrays and objects may nest before the text is refused, so that nesting cannot exhaust the stack. */
    private static final int DEEPEST = 256;

    private final String text;
    private int at;

    private JsonReader(String text) {
        this.text = text;
    }

    /** The value {@code text} holds. */
    static Object read(String text) {
        JsonReader reader = new JsonReader(text);
        reader.skipWhiteSpace();
        Object value = reader.value(0);
        reader.skipWhiteSpace();
        if (reader.at != text.length()) {
            throw reader.refusal("text after the value");
        }
        return value;
    }

    private Object value(int depth) {
        if (at >= text.length()) {
            throw refusal("no value");
        }
        char next = text.charAt(at);
        return switch (next) {
            case '{' -> object(depth + 1);
            case '[' -> array(depth + 1);
            case '"' -> string();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> {
                if (next == '-' || (next >= '0' && next <= '9')) {
                    yield number();
                }
                throw refusal("no value");
            }
        };
    }

    private Map<String, Object> object(int depth) {
        if (depth > DEEPEST) {
            throw refusal("nesting deeper than " + DEEPEST);
        }
        at++;
        Map<String, Object> members = new LinkedHashMap<>();
        skipWhiteSpace();
        if (peek('}')) {
            at++;
            return members;
        }
        while (true) {
            skipWhiteSpace();
            if (!peek('"')) {
                throw refusal("a name was expected");
            }
            String name = string();
            skipWhiteSpace();
            expect(':');
            skipWhiteSpace();
            members.put(name, value(depth));
            skipWhiteSpace();
            if (peek(',')) {
                at++;
            } else {
                expect('}');
                return members;
            }
        }
    }

    private List<Object> array(int depth) {
        if (depth > DEEPEST) {
            throw refusal("nesting deeper than " + DEEPEST);
        }
        at++;
        List<Object> elements = new ArrayList<>();
        skipWhiteSpace();
        if (peek(']')) {
            at++;
            return elements;
        }
        while (true) {
            skipWhiteSpace();
            elements.add(value(depth));
            skipWhiteSpace();
            if (peek(',')) {
                at++;
            } else {
                expect(']');
                return elements;
            }
        }
    }

    private String string() {
        at++;
        StringBuilder value = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw refusal("a string is not closed");
            }
            char next = text.charAt(at++);
            if (next == '"') {
                return value.toString();
            }
            if (next < 0x20) {
                throw refusal("a control character in a string");
            }
            if (next != '\\') {
                value.append(next);
                continue;
            }
            if (at >= text.length()) {
                throw refusal("a string is not closed");
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> value.append(unit());
                default -> throw refusal("an escape that is not JSON's");
            }
        }
    }

    private char unit() {
        if (at + 4 > text.length()) {
            throw refusal("a \\u escape is cut short");
        }
        int code = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(at + i), 16);
            if (digit < 0) {
                throw refusal("a \\u escape without four hexadecimal digits");
            }
            code = code * 16 + digit;
        }
        at += 4;
        return (char) code;
    }

    private BigDecimal number() {
        int start = at;
        if (peek('-')) {
            at++;
        }
        if (peek('0')) {
            at++;
        } else if (digitAhead()) {
            digits();
        } else {
            throw refusal("a number without digits");
        }
        if (peek('.')) {
            at++;
            if (!digitAhead()) {
                throw refusal("a fraction without digits");
            }
            digits();
        }
        if (peek('e') || peek('E')) {
            at++;
            if (peek('+') || peek('-')) {
                at++;
            }
            if (!digitAhead()) {
                throw refusal("an exponent without digits");
            }
            digits();
        }
        return new BigDecimal(text.substring(start, at));
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw refusal("no value");
        }
        at += word.length();
        return value;
    }

    private void digits() {
        while (digitAhead()) {
            at++;
        }
    }

    private boolean digitAhead() {
        return at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9';
    }

    private boolean peek(char expected) {
        return at < text.length() && text.charAt(at) == expected;
    }

    private void expect(char expected) {
        if (!peek(expected)) {
            throw refusal("'" + expected + "' was expected");
        }
        at++;
    }

    private void skipWhiteSpace() {
        while (at < text.length()) {
            char next = text.charAt(at);
            if (next != ' ' && next != '\t' && next != '\n' && next != '\r') {
                return;
            }
            at++;
        }
    }

    private IllegalArgumentException refusal(String why) {
        return new IllegalArgumentException("not JSON at character " + at + ": " + why);
    }
}
