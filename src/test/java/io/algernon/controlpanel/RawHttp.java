package io.algernon.controlpanel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One HTTP/1.1 request written by hand on a socket, and its response read back whole.
 *
 * <p>The control panel's server is held to the exact {@code Host}, {@code Origin} and {@code Cookie} a browser sends
 * (ADR-291 §4, §5), and to a request that carries no {@code Host} at all. {@code java.net.http.HttpClient} writes its
 * own {@code Host} and refuses to be given one, so the tests write the request themselves: the request line, exactly
 * the header lines a test gives, and {@code Connection: close}, so the response ends where the stream does.
 */
final class RawHttp {

    /** How long a test waits for the server's answer before it fails rather than hangs. */
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private static final List<String> METHODS_WITH_A_BODY = List.of("POST", "PUT", "PATCH", "DELETE");

    private static final byte[] END_OF_HEADERS = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private RawHttp() {
    }

    /**
     * A response: its status, its headers by name, each name's values in the order sent, the names compared as HTTP
     * compares them, without case, and its body read as UTF-8.
     */
    record Response(int status, Map<String, List<String>> headers, String body) {

        /** Every value sent under {@code name}, in the order sent; none where the header was not sent. */
        List<String> header(String name) {
            return headers.getOrDefault(name, List.of());
        }
    }

    /**
     * Writes {@code method target HTTP/1.1}, then each of {@code headerLines} as given, then {@code Connection: close}
     * and, for a method that may carry a body, {@code Content-Length: 0}, and reads the response to the end.
     */
    static Response send(InetSocketAddress server, String method, String target, List<String> headerLines)
            throws IOException {
        StringBuilder request = new StringBuilder(method + " " + target + " HTTP/1.1\r\n");
        for (String line : headerLines) {
            request.append(line).append("\r\n");
        }
        if (METHODS_WITH_A_BODY.contains(method)) {
            request.append("Content-Length: 0\r\n");
        }
        request.append("Connection: close\r\n\r\n");
        try (Socket socket = new Socket(server.getAddress(), server.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            return parse(in.readAllBytes());
        }
    }

    private static Response parse(byte[] bytes) throws IOException {
        int end = indexOf(bytes, END_OF_HEADERS);
        if (end < 0) {
            throw new IOException("the response ended before its headers did: "
                    + new String(bytes, StandardCharsets.ISO_8859_1));
        }
        List<String> lines = List.of(new String(bytes, 0, end, StandardCharsets.ISO_8859_1).split("\r\n"));
        int status = Integer.parseInt(lines.getFirst().split(" ")[1]);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String line : lines.subList(1, lines.size())) {
            int colon = line.indexOf(':');
            headers.computeIfAbsent(line.substring(0, colon).trim(), name -> new ArrayList<>())
                    .add(line.substring(colon + 1).trim());
        }
        byte[] body = Arrays.copyOfRange(bytes, end + END_OF_HEADERS.length, bytes.length);
        List<String> encoding = headers.getOrDefault("Transfer-Encoding", List.of());
        List<String> length = headers.getOrDefault("Content-Length", List.of());
        if (encoding.stream().anyMatch(value -> value.equalsIgnoreCase("chunked"))) {
            body = unchunked(body);
        } else if (!length.isEmpty()) {
            body = Arrays.copyOf(body, Math.min(body.length, Integer.parseInt(length.getFirst())));
        }
        return new Response(status, headers, new String(body, StandardCharsets.UTF_8));
    }

    private static byte[] unchunked(byte[] chunked) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int at = 0;
        while (at < chunked.length) {
            int lineEnd = indexOf(Arrays.copyOfRange(chunked, at, chunked.length), "\r\n".getBytes(StandardCharsets.ISO_8859_1));
            String sizeLine = new String(chunked, at, lineEnd, StandardCharsets.ISO_8859_1);
            int size = Integer.parseInt(sizeLine.split(";")[0].trim(), 16);
            at += lineEnd + 2;
            if (size == 0) {
                break;
            }
            body.write(chunked, at, size);
            at += size + 2;
        }
        return body.toByteArray();
    }

    private static int indexOf(byte[] bytes, byte[] sought) {
        for (int at = 0; at + sought.length <= bytes.length; at++) {
            if (Arrays.equals(bytes, at, at + sought.length, sought, 0, sought.length)) {
                return at;
            }
        }
        return -1;
    }
}
