package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.WEBSITE;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code vespera serve}'s server, the {@code website} module's (ADR-333 §1, §8): it serves the website, {@code <working
 * directory>/website/}, bound to {@code 127.0.0.1} alone, with no login, answering {@code GET} and {@code HEAD} alone. A
 * request whose {@code Host} is not {@code 127.0.0.1:<port>} or {@code localhost:<port>} is refused, and so is one sent
 * {@code Sec-Fetch-Site: cross-site} or {@code same-site}, as the control panel refuses them (ADR-291 §4, ADR-302 §12),
 * since the folder holds the operator's documents and another website in the same browser must not read them. A path
 * is answered only where its real path lies beneath the folder's; a folder answers its {@code index.html}, or 404, and
 * never a listing; the marker, {@code vespera-website.json} and any {@code .part} are 404. Every answer carries {@code
 * X-Content-Type-Options: nosniff}, and every file that is not a page, an asset or a listing of the website, each copy and
 * whatever the operator put there, carries {@code Content-Security-Policy: sandbox}, so a document that is HTML runs no
 * script in the website's origin. No request is logged, since a request's path names a document (ADR-274 §8).
 *
 * <p>The website is the one {@link WebsiteFixture} draws and a publish to it refreshes, with what an operator may leave
 * beside it: a page of their own, a folder of their own with no {@code index.html}, a file being written, and a file
 * outside the folder that no request may reach. It is served once for the class, at a port the system picks. Each
 * request is written on a socket of its own, so that its {@code Host} is the test's to set, as no HTTP client of the
 * JDK lets it be, and its target is sent as written, {@code ..} and all.
 *
 * <p>The refusals' status codes are the control panel's: 403 for a foreign {@code Host} or another site, 405 for a
 * method but {@code GET} and {@code HEAD}; a folder asked without its closing slash is 301 to the slash form (ADR-341
 * §6). What is a page, an asset or the listing, and so carries no sandbox, is read from {@code vespera-website.json}
 * and the file's name together, the list read again whenever it changes while the website is served (ADR-341 §1, §7):
 * those two are asked of servers of their own, over folders written for each, so that the class's website is not
 * changed under its other tests.
 */
@Epic("Operation")
@Feature("The website")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-291", url = Adr.THE_CONTROL_PANEL_ANSWERS_ONLY_A_BROWSER_HOLDING_ITS_STARTS_TOKEN_ON_ITS_OWN_ADDRESS, type = "adr")
@Link(name = "ADR-341", url = Adr.SERVE_TELLS_A_PAGE_FROM_A_COPY_BY_THE_LIST_AND_THE_NAME, type = "adr")
class ServeAnswersOnLoopbackAloneTest {

    private static final int OK = 200;
    private static final int REFUSED = 403;
    private static final int NOT_FOUND = 404;
    /** A page of the operator's own, beside the website's, holding a script. */
    private static final String THE_OPERATORS_PAGE = "notes-of-mine.html";

    /** A folder of the operator's own, holding a file and no {@code index.html}. */
    private static final String THE_OPERATORS_FOLDER = "mine";

    private static final String THE_OPERATORS_FILE = "kept-by-me.txt";

    /** A file beside the website, in the working directory, which no request may reach. */
    private static final String OUTSIDE = "outside-the-website.txt";

    private static final String OUTSIDES_TEXT = "OUTSIDE-6d2f nothing of the website";

    @TempDir
    static Path base;

    private static Path website;

    private static Path workingDirectory;

    private static WebsiteServer server;

    private static int port;

    /** The rendering's list, by each file's path in the website: what the website holds by kind. */
    private static Map<String, WebsiteFixture.Listed> listed;

    @BeforeAll
    static void serveAPublishedWebsite() throws IOException {
        Path tree = WebsiteFixture.drawn(base.resolve("drawn"));
        workingDirectory = Files.createDirectories(base.resolve("work-dir"));
        Plugin.Delivery delivery = WebsiteFixture.published(tree, workingDirectory);
        if (!delivery.delivered()) {
            throw new IllegalStateException("the fixture's website was not published: " + delivery.closingLine());
        }
        website = workingDirectory.resolve(WEBSITE);
        listed = WebsiteFixture.listedIn(WebsiteFixture.renderingOf(tree));
        Files.writeString(website.resolve(THE_OPERATORS_PAGE), "<script>document.title = 'mine'</script>");
        Files.writeString(Files.createDirectories(website.resolve(THE_OPERATORS_FOLDER)).resolve(THE_OPERATORS_FILE),
                "the operator's own");
        Files.writeString(website.resolve("index.html.part"), "being written");
        Files.writeString(website.resolve(DOORS_FOLDER).resolve("index.html.part"), "being written");
        Files.writeString(workingDirectory.resolve(OUTSIDE), OUTSIDES_TEXT);
        server = WebsiteServer.start(website, 0);
        port = server.address().getPort();
    }

    @AfterAll
    static void stopServing() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("The server listens on 127.0.0.1 and nowhere else")
    void itListensOn127001Alone() {
        InetSocketAddress address = server.address();

        claim("it is bound to 127.0.0.1, the loopback address, and to no address of every interface",
                () -> {
                    assertThat(address.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
                    assertThat(address.getAddress().isAnyLocalAddress()).isFalse();
                });
        claim("and it answers there",
                () -> assertThat(get("/").status()).isEqualTo(OK));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A request is answered addressed to 127.0.0.1 or localhost at the server's port, and refused addressed to any other host")
    void onlyItsOwnHostIsAnswered() throws IOException {
        byte[] index = Files.readAllBytes(website.resolve("index.html"));
        Map<String, Answer> foreign = new LinkedHashMap<>();
        for (String host : List.of("example.com:" + port, "127.0.0.1", "localhost", "127.0.0.1:" + otherPort(),
                "localhost:" + otherPort(), "127.0.0.2:" + port, "evil.localhost:" + port)) {
            foreign.put(host, ask("GET", "/", host));
        }
        Answer noHost = ask("GET", "/", null);

        claim("addressed to 127.0.0.1 or to localhost at its port, the index is answered",
                () -> {
                    assertThat(ask("GET", "/", "127.0.0.1:" + port).body()).isEqualTo(index);
                    assertThat(ask("GET", "/", "localhost:" + port).body()).isEqualTo(index);
                });
        claim("addressed to another host, to either without the port or at another port, it is refused with 403 and"
                        + " nothing of the page",
                () -> assertThat(foreign).allSatisfy((host, answer) -> {
                    assertThat(answer.status()).as("the answer to Host %s", host).isEqualTo(REFUSED);
                    assertThat(answer.body()).as("the body answered to Host %s", host).isNotEqualTo(index);
                }));
        claim("and a request naming no host is refused too",
                () -> assertThat(noHost.status()).isEqualTo(REFUSED));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A path leading out of the website by .., spelt plainly or encoded, is 404 and answers nothing of the file it names")
    void aPathLeadingOutIsNotFound() {
        List<String> leadingOut = List.of(
                "/../" + OUTSIDE,
                "/%2e%2e/" + OUTSIDE,
                "/%2E%2E/" + OUTSIDE,
                "/" + DOORS_FOLDER + "/../../../" + OUTSIDE,
                "/..%2f" + OUTSIDE,
                "/..%5c" + OUTSIDE,
                "/%2e%2e%5c" + OUTSIDE);
        Map<String, Answer> answers = new LinkedHashMap<>();
        leadingOut.forEach(target -> answers.put(target, get(target)));

        claim("each is answered 404, and no answer carries the outside file's text",
                () -> assertThat(answers).allSatisfy((target, answer) -> {
                    assertThat(answer.status()).as("the answer to %s", target).isEqualTo(NOT_FOUND);
                    assertThat(answer.text()).as("the body answered to %s", target).doesNotContain(OUTSIDES_TEXT);
                }));
    }

    // ---------- helpers ----------

    /** One answer: its status, its headers by their lower-case names, and its body. */
    record Answer(int status, Map<String, List<String>> headers, byte[] body) {

        /** The header's values joined, or {@code null} where it is absent. */
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null ? null : String.join(", ", values);
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static String ownHost() {
        return "127.0.0.1:" + port;
    }

    /** A port that is not the server's. */
    private static int otherPort() {
        return port == 65535 ? 65534 : port + 1;
    }

    /** {@code GET target} addressed to the server's own host, with {@code headers}. */
    private static Answer get(String target, String... headers) {
        return ask("GET", target, ownHost(), headers);
    }

    /**
     * {@code method target}, sent as written on a connection of its own to {@code 127.0.0.1} at the server's port,
     * {@code host} as its {@code Host} or none where it is {@code null}, and read to the end.
     */
    private static Answer ask(String method, String target, String host, String... headers) {
        return askAt(port, method, target, host, headers);
    }

    /** {@code method target} as {@link #ask} sends it, to {@code 127.0.0.1} at port {@code at}. */
    private static Answer askAt(int at, String method, String target, String host, String... headers) {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), at)) {
            socket.setSoTimeout(10_000);
            StringBuilder request = new StringBuilder(method).append(' ').append(target).append(" HTTP/1.1\r\n");
            if (host != null) {
                request.append("Host: ").append(host).append("\r\n");
            }
            for (String header : headers) {
                request.append(header).append("\r\n");
            }
            request.append("Connection: close\r\n\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            try (InputStream in = socket.getInputStream()) {
                return parsed(in.readAllBytes(), method.equals("HEAD"));
            }
        } catch (IOException failed) {
            throw new java.io.UncheckedIOException(failed);
        }
    }

    private static Answer parsed(byte[] all, boolean head) {
        int end = indexOf(all, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        if (end < 0) {
            throw new IllegalStateException("no whole answer: " + new String(all, StandardCharsets.ISO_8859_1));
        }
        String[] lines = new String(all, 0, end, StandardCharsets.ISO_8859_1).split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String line : Arrays.asList(lines).subList(1, lines.length)) {
            int colon = line.indexOf(':');
            headers.computeIfAbsent(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), name -> new ArrayList<>())
                    .add(line.substring(colon + 1).strip());
        }
        byte[] body = head ? new byte[0] : Arrays.copyOfRange(all, end + 4, all.length);
        List<String> encoding = headers.get("transfer-encoding");
        if (!head && encoding != null && encoding.getFirst().equalsIgnoreCase("chunked")) {
            body = unchunked(body);
        }
        return new Answer(status, headers, body);
    }

    private static byte[] unchunked(byte[] chunked) {
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        int at = 0;
        byte[] lineEnd = "\r\n".getBytes(StandardCharsets.ISO_8859_1);
        while (at < chunked.length) {
            int sizeEnd = at + indexOf(Arrays.copyOfRange(chunked, at, chunked.length), lineEnd);
            String size = new String(chunked, at, sizeEnd - at, StandardCharsets.ISO_8859_1).split(";")[0].strip();
            int length = Integer.parseInt(size, 16);
            if (length == 0) {
                break;
            }
            whole.write(chunked, sizeEnd + 2, length);
            at = sizeEnd + 2 + length + 2;
        }
        return whole.toByteArray();
    }

    private static int indexOf(byte[] in, byte[] sought) {
        outer:
        for (int i = 0; i + sought.length <= in.length; i++) {
            for (int j = 0; j < sought.length; j++) {
                if (in[i + j] != sought[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** {@code path}, each name of it percent-encoded but for the characters a path needs none for. */
    private static String encoded(String path) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-._~/".indexOf(c) >= 0) {
                encoded.append(c);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xff));
            }
        }
        return encoded.toString();
    }

}
