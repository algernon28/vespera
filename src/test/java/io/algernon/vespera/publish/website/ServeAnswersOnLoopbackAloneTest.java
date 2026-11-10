package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.VESPERA_WEBSITE_JSON;
import static io.algernon.vespera.publish.website.WebsiteFixture.WEBSITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
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
    private static final int MOVED = 301;
    private static final int REFUSED = 403;
    private static final int NOT_FOUND = 404;
    private static final int NO_SUCH_METHOD = 405;

    /** A page of the operator's own, beside the website's, holding a script. */
    private static final String THE_OPERATORS_PAGE = "notes-of-mine.html";

    /** A folder of the operator's own, holding a file and no {@code index.html}. */
    private static final String THE_OPERATORS_FOLDER = "mine";

    private static final String THE_OPERATORS_FILE = "kept-by-me.txt";

    /** A file beside the website, in the working directory, which no request may reach. */
    private static final String OUTSIDE = "outside-the-website.txt";

    private static final String OUTSIDES_TEXT = "OUTSIDE-6d2f nothing of the website";

    /** A name no file of the website has, which no line printed while it is asked may carry. */
    private static final String A_MARKED_PATH = "MARKED-4b7e-asked-for-and-not-there.html";

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
    @DisplayName("No other address of this machine reaches the server at its port")
    void noOtherAddressOfThisMachineReachesIt() throws SocketException {
        List<InetAddress> others = NetworkInterface.networkInterfaces()
                .filter(face -> {
                    try {
                        return face.isUp() && !face.isLoopback();
                    } catch (SocketException unknown) {
                        return false;
                    }
                })
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
                .toList();
        assumeTrue(!others.isEmpty(), "this machine has no address but loopback to try");

        List<String> reached = new ArrayList<>();
        for (InetAddress other : others) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(other, port), 2_000);
                reached.add(other.getHostAddress());
            } catch (IOException refused) {
                // not reached, as it should be
            }
        }

        claim("a connection to each other address of this machine at the server's port is refused",
                () -> assertThat(reached).as("addresses that reached the server").isEmpty());
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
    @DisplayName("A request another website's page sends is refused, and one the website's own page or the reader sends is answered")
    void anotherSitesRequestIsRefused() {
        claim("Sec-Fetch-Site: cross-site is refused with 403, to GET and to HEAD",
                () -> {
                    assertThat(get("/", "Sec-Fetch-Site: cross-site").status()).isEqualTo(REFUSED);
                    assertThat(ask("HEAD", "/", ownHost(), "Sec-Fetch-Site: cross-site").status()).isEqualTo(REFUSED);
                });
        claim("so is same-site, another website of the same registrable domain",
                () -> assertThat(get("/", "Sec-Fetch-Site: same-site").status()).isEqualTo(REFUSED));
        claim("and same-origin, the website's own page, and none, the reader typing the address, are answered",
                () -> {
                    assertThat(get("/", "Sec-Fetch-Site: same-origin").status()).isEqualTo(OK);
                    assertThat(get("/", "Sec-Fetch-Site: none").status()).isEqualTo(OK);
                });
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("GET and HEAD are answered, and every other method is refused with 405, changing nothing")
    void onlyGetAndHeadAreAnswered() throws IOException {
        byte[] index = Files.readAllBytes(website.resolve("index.html"));
        Answer head = ask("HEAD", "/", ownHost());
        Map<String, Integer> others = new LinkedHashMap<>();
        Map<String, String> allowed = new LinkedHashMap<>();
        for (String method : List.of("POST", "PUT", "DELETE", "PATCH")) {
            Answer answer = ask(method, "/index.html", ownHost(), "Content-Length: 0");
            others.put(method, answer.status());
            allowed.put(method, answer.header("Allow"));
        }

        claim("HEAD is answered 200 with no body",
                () -> {
                    assertThat(head.status()).isEqualTo(OK);
                    assertThat(head.body()).isEmpty();
                });
        claim("POST, PUT, DELETE and PATCH are each refused with 405",
                () -> assertThat(others).containsExactly(
                        entry("POST", NO_SUCH_METHOD),
                        entry("PUT", NO_SUCH_METHOD),
                        entry("DELETE", NO_SUCH_METHOD),
                        entry("PATCH", NO_SUCH_METHOD)));
        claim("each refusal names the two methods answered in its Allow header (ADR-341 §6)",
                () -> assertThat(allowed).allSatisfy((method, allow) ->
                        assertThat(allow).as("the Allow header answering %s", method).isEqualTo("GET, HEAD")));
        claim("and the index they were sent to is as it was",
                () -> assertThat(Files.readAllBytes(website.resolve("index.html"))).isEqualTo(index));
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

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A link inside the website whose real path lies outside it is 404")
    void aLinkLeadingOutIsNotFound() throws IOException {
        Path link = website.resolve("leads-out.txt");
        try {
            Files.createSymbolicLink(link, workingDirectory.resolve(OUTSIDE));
        } catch (IOException | UnsupportedOperationException notHere) {
            assumeTrue(false, "this machine cannot make a symbolic link: " + notHere.getClass().getSimpleName());
        }
        try {
            Answer answer = get("/leads-out.txt");

            claim("the link is answered 404, and nothing of the file it leads to",
                    () -> {
                        assertThat(answer.status()).isEqualTo(NOT_FOUND);
                        assertThat(answer.text()).doesNotContain(OUTSIDES_TEXT);
                    });
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A folder answers its index.html, and a folder with none is 404 with no listing of what it holds")
    void aFolderAnswersItsIndexAndNeverAListing() throws IOException {
        byte[] doors = Files.readAllBytes(website.resolve(DOORS_FOLDER).resolve("index.html"));
        Answer doorsFolder = get("/" + encoded(DOORS_FOLDER) + "/");
        Answer theOperatorsFolder = get("/" + THE_OPERATORS_FOLDER + "/");

        claim("the doors' folder answers the doors' index.html",
                () -> {
                    assertThat(doorsFolder.status()).isEqualTo(OK);
                    assertThat(doorsFolder.body()).isEqualTo(doors);
                });
        claim("and the operator's folder, which holds no index.html, is 404 and names nothing it holds",
                () -> {
                    assertThat(theOperatorsFolder.status()).isEqualTo(NOT_FOUND);
                    assertThat(theOperatorsFolder.text()).doesNotContain(THE_OPERATORS_FILE);
                });
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A folder asked without its closing slash is moved to the slash form with 301, and answers nothing of its index")
    void aFolderAskedWithoutItsSlashIsMovedToIt() throws IOException {
        byte[] doors = Files.readAllBytes(website.resolve(DOORS_FOLDER).resolve("index.html"));
        String asked = "/" + encoded(DOORS_FOLDER);
        Answer moved = get(asked);

        claim("the doors' folder asked without its slash is answered 301, its Location the same path with the slash",
                () -> {
                    assertThat(moved.status()).isEqualTo(MOVED);
                    assertThat(moved.header("Location")).isEqualTo(asked + "/");
                });
        claim("and the answer is not the doors' index.html, which only the slash form answers",
                () -> {
                    assertThat(moved.body()).isNotEqualTo(doors);
                    assertThat(get(asked + "/").body()).isEqualTo(doors);
                });
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A file listed in vespera-website.json while the website is served leaves the sandbox, and one delisted is sandboxed again")
    void aFileListedOrDelistedWhileServedGainsOrLosesTheSandbox() throws IOException {
        Path folder = Files.createDirectories(base.resolve("listed-while-served"));
        Files.writeString(folder.resolve("index.html"), "<p>a page</p>");
        writeList(folder);
        try (WebsiteServer served = WebsiteServer.start(folder, 0)) {
            int at = served.address().getPort();
            Answer unlisted = getAt(at, "/index.html");
            writeList(folder, "index.html");
            Answer listedNow = getAt(at, "/index.html");
            writeList(folder);
            Answer delisted = getAt(at, "/index.html");

            claim("index.html, which vespera-website.json does not list, is answered with the sandbox",
                    () -> {
                        assertThat(unlisted.status()).isEqualTo(OK);
                        assertThat(unlisted.header("Content-Security-Policy")).isEqualTo("sandbox");
                    });
            claim("once the list names it, with the server still running, it is answered with no sandbox",
                    () -> {
                        assertThat(listedNow.status()).isEqualTo(OK);
                        assertThat(String.valueOf(listedNow.header("Content-Security-Policy")))
                                .doesNotContain("sandbox");
                    });
            claim("and once the list drops it again, it is answered with the sandbox again",
                    () -> {
                        assertThat(delisted.status()).isEqualTo(OK);
                        assertThat(delisted.header("Content-Security-Policy")).isEqualTo("sandbox");
                    });
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A file vespera-website.json lists leaves the sandbox only under a name the website draws: a page's anywhere, an asset's or the listing's at the top")
    void aListedFileLeavesTheSandboxOnlyUnderANameTheWebsiteDraws() throws IOException {
        Path folder = Files.createDirectories(base.resolve("listed-by-name"));
        List<String> theWebsites = List.of("index.html", "website.js", "website.css", "search-index.js",
                "documents.csv", "a/b/index.html", "a/b/index-2.html");
        List<String> stillSandboxed = List.of("notes.html", "a/b/report.html", "a/b/website.js", "a/documents.csv",
                "a/b/index-.html");
        for (String file : List.of(theWebsites, stillSandboxed).stream().flatMap(List::stream).toList()) {
            Path path = folder.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "listed");
        }
        writeList(folder, List.of(theWebsites, stillSandboxed).stream().flatMap(List::stream).toArray(String[]::new));
        try (WebsiteServer served = WebsiteServer.start(folder, 0)) {
            int at = served.address().getPort();
            Map<String, Answer> drawn = new LinkedHashMap<>();
            theWebsites.forEach(file -> drawn.put(file, getAt(at, "/" + file)));
            Map<String, Answer> notDrawn = new LinkedHashMap<>();
            stillSandboxed.forEach(file -> notDrawn.put(file, getAt(at, "/" + file)));

            claim("a page's name in any folder, and the script, the theme, the search index and the listing at the top,"
                            + " each listed, are answered with no sandbox",
                    () -> assertThat(drawn).allSatisfy((file, answer) -> {
                        assertThat(answer.status()).as("the answer to %s", file).isEqualTo(OK);
                        assertThat(String.valueOf(answer.header("Content-Security-Policy")))
                                .as("the policy of %s", file)
                                .doesNotContain("sandbox");
                    }));
            claim("and any other name, or an asset's or the listing's name below the top, is answered with the sandbox"
                            + " though the list names it as it names a page, since it names a copy so too",
                    () -> assertThat(notDrawn).allSatisfy((file, answer) -> {
                        assertThat(answer.status()).as("the answer to %s", file).isEqualTo(OK);
                        assertThat(answer.header("Content-Security-Policy")).as("the policy of %s", file)
                                .isEqualTo("sandbox");
                    }));
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("The website's marker, its own list and a file being written are 404, though each is there")
    void theWebsitesOwnFilesAreNotFound() {
        List<String> ownFiles = List.of(
                DeliverableRoot.MARKER_FILE_NAME,
                VESPERA_WEBSITE_JSON,
                "index.html.part",
                DOORS_FOLDER + "/index.html.part");
        Map<String, Integer> answers = new LinkedHashMap<>();
        ownFiles.forEach(file -> answers.put(file, get("/" + encoded(file)).status()));

        claim("each is there in the website, so a 404 below is not a file missing",
                () -> assertThat(ownFiles).allSatisfy(file -> assertThat(website.resolve(file)).isRegularFile()));
        claim("and each is answered 404",
                () -> assertThat(answers).allSatisfy((file, status) ->
                        assertThat(status).as("the answer to %s", file).isEqualTo(NOT_FOUND)));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A copy and the operator's files carry the sandbox, and the website's pages, assets and listing do not")
    void aCopyAndTheOperatorsFilesRunNoScript() {
        String page = firstOfKind("page");
        String asset = firstOfKind("asset");
        String listing = firstOfKind("listing");
        String copy = firstOfKind("copy");
        Map<String, Answer> theWebsites = new LinkedHashMap<>();
        for (String file : List.of(page, asset, listing)) {
            theWebsites.put(file, get("/" + encoded(file)));
        }
        Map<String, Answer> sandboxed = new LinkedHashMap<>();
        for (String file : List.of(copy, THE_OPERATORS_PAGE, THE_OPERATORS_FOLDER + "/" + THE_OPERATORS_FILE)) {
            sandboxed.put(file, get("/" + encoded(file)));
        }

        claim("the website holds a page, an asset, a listing and a copy, so each kind below is asked",
                () -> assertThat(List.of(page, asset, listing, copy)).doesNotContainNull());
        claim("a copy, the operator's own page and the operator's own file are each answered with"
                        + " Content-Security-Policy: sandbox",
                () -> assertThat(sandboxed).allSatisfy((file, answer) -> {
                    assertThat(answer.status()).as("the answer to %s", file).isEqualTo(OK);
                    assertThat(answer.header("Content-Security-Policy")).as("the policy of %s", file)
                            .isEqualTo("sandbox");
                }));
        claim("and a page, an asset and the listing of the website are answered with no sandbox, so their script runs",
                () -> assertThat(theWebsites).allSatisfy((file, answer) -> {
                    assertThat(answer.status()).as("the answer to %s", file).isEqualTo(OK);
                    assertThat(String.valueOf(answer.header("Content-Security-Policy")))
                            .as("the policy of %s", file)
                            .doesNotContain("sandbox");
                }));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("Every answer carries X-Content-Type-Options: nosniff, an answered file, a refusal and a 404 alike")
    void everyAnswerCarriesNosniff() {
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put("a page", get("/"));
        answers.put("a copy", get("/" + encoded(firstOfKind("copy"))));
        answers.put("HEAD of a page", ask("HEAD", "/", ownHost()));
        answers.put("a foreign host", ask("GET", "/", "example.com:" + port));
        answers.put("another site", get("/", "Sec-Fetch-Site: cross-site"));
        answers.put("a POST", ask("POST", "/", ownHost(), "Content-Length: 0"));
        answers.put("a path not there", get("/" + A_MARKED_PATH));
        answers.put("the marker", get("/" + DeliverableRoot.MARKER_FILE_NAME));

        claim("the answers asked are an answered file, a refusal of each kind and a 404, by their status codes",
                () -> assertThat(answers.values().stream().map(Answer::status).distinct().sorted().toList())
                        .containsExactly(OK, REFUSED, NOT_FOUND, NO_SUCH_METHOD));
        claim("and each carries X-Content-Type-Options: nosniff",
                () -> assertThat(answers).allSatisfy((what, answer) ->
                        assertThat(answer.header("X-Content-Type-Options")).as("the answer to %s", what)
                                .isEqualTo("nosniff")));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("No request is logged: nothing printed while the website is asked names the path asked for")
    void noRequestIsLogged() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(printed, true, StandardCharsets.UTF_8);
        String copy = firstOfKind("copy");
        int notThere;
        int answered;
        System.setOut(capture);
        System.setErr(capture);
        try {
            notThere = get("/" + A_MARKED_PATH).status();
            answered = get("/" + encoded(copy)).status();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String text = printed.toString(StandardCharsets.UTF_8);
        String copysName = copy.substring(copy.lastIndexOf('/') + 1);

        claim("both requests were answered, one not there and one a copy",
                () -> {
                    assertThat(notThere).isEqualTo(NOT_FOUND);
                    assertThat(answered).isEqualTo(OK);
                });
        claim("and nothing printed meanwhile names either path",
                () -> assertThat(text).doesNotContain(A_MARKED_PATH).doesNotContain(copysName)
                        .doesNotContain(encoded(copysName)));
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

    /** {@code GET target} sent to a server of the test's own at {@code 127.0.0.1:<at>}, addressed to that host. */
    private static Answer getAt(int at, String target) {
        return askAt(at, "GET", target, "127.0.0.1:" + at);
    }

    /**
     * {@code vespera-website.json} in {@code folder}, written whole, listing {@code paths} at a digest no check reads:
     * the server reads the paths alone.
     */
    private static void writeList(Path folder, String... paths) throws IOException {
        StringBuilder list = new StringBuilder("{\"format\":1,\"files\":[");
        for (int i = 0; i < paths.length; i++) {
            list.append(i == 0 ? "" : ",").append("{\"path\":\"").append(paths[i]).append("\",\"sha256\":\"")
                    .append("0".repeat(64)).append("\"}");
        }
        Files.writeString(folder.resolve(VESPERA_WEBSITE_JSON), list.append("]}\n"), StandardCharsets.UTF_8);
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

    /** The path in the website of the first file the rendering's list names of {@code kind}, or {@code null}. */
    private static String firstOfKind(String kind) {
        return listed.values().stream()
                .filter(file -> file.kind().equals(kind))
                .map(WebsiteFixture.Listed::path)
                .findFirst()
                .orElse(null);
    }
}
