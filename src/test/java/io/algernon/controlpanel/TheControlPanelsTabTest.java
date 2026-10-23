package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.controlpanel.RawHttp.Response;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a page of the control panel carries for its tab to come back to a later start, and what the server answers the
 * tab with (ADR-323 §4 to §6): the start's mark and the tab's key on every page of its own, the one script, served to
 * the cookie alone and holding no data of a start, the second policy, the invocation page's refresh, and {@code
 * /alive}, answered without the cookie with a proof only a server holding the token can give.
 *
 * <p>One server, started with the screens' pages as {@code ControlPanel.main} starts it, at a port the system picks,
 * serves every test but the two that need a server of their own: a second start, and a child to watch run. The
 * pages of its own are asked for once, through {@link PanelBrowser}; the script and {@code /alive} are asked for on
 * {@link RawHttp}, so that each request carries exactly the cookie, {@code Host} and {@code Sec-Fetch-Site} it is
 * given, or none.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-323", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class TheControlPanelsTabTest {

    /** The policy every response of the control panel carries. */
    private static final String POLICY_OF_EVERY_RESPONSE = "frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    /** The second policy every page of the control panel's own carries. */
    private static final String POLICY_OF_ITS_OWN_PAGES = "script-src 'self'; connect-src 'self'";

    /** The four headers every response carries, with their values. */
    private static final Map<String, String> EVERY_RESPONSE = everyResponse();

    private static final String SCRIPT_TYPE = "text/javascript; charset=utf-8";

    private static final String PLAIN_TEXT = "text/plain; charset=utf-8";

    /** A start's mark: 22 characters of base64url, 16 random bytes with no padding. */
    private static final String MARK = "[A-Za-z0-9_-]{22}";

    /** How often the invocation page is reloaded while its child runs: every 2 seconds. */
    private static final String EVERY_TWO_SECONDS = "2";

    /** What the script never names: what parses a value as markup, what runs text as code, and a repeating timer. */
    private static final List<Pattern> NEVER_IN_THE_SCRIPT = List.of(Pattern.compile("\\binnerHTML\\b"),
            Pattern.compile("\\bouterHTML\\b"), Pattern.compile("\\binsertAdjacentHTML\\b"),
            Pattern.compile("\\bdocument\\.write"), Pattern.compile("\\beval\\s*\\("),
            Pattern.compile("\\bFunction\\s*\\("), Pattern.compile("\\bsetInterval\\b"));

    /** A fetch that sends no cookie. */
    private static final Pattern WITHOUT_THE_COOKIE = Pattern.compile("credentials\\s*:\\s*['\"]omit['\"]");

    /** A nonce of 31 lowercase hexadecimal characters, one short. */
    private static final String SHORT_NONCE = "0123456789abcdef0123456789abcde";

    /** A nonce of 32 hexadecimal characters in capitals. */
    private static final String NONCE_IN_CAPITALS = "0123456789ABCDEF0123456789ABCDEF";

    /** A nonce of 32 lowercase hexadecimal characters, as the script writes one. */
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    /** Four queries the server does not answer: no nonce, a short one, one in capitals, one beside another parameter. */
    private static final List<String> NOT_A_NONCE = List.of(TabProof.ALIVE, TabProof.aliveTarget(SHORT_NONCE),
            TabProof.aliveTarget(NONCE_IN_CAPITALS), TabProof.aliveTarget(NONCE) + "&token=x");

    @TempDir
    static Path folder;

    private static ScreenServer screens;

    /** Every page of the control panel's own, asked for once with the cookie. */
    private static final List<Page> PAGES = new ArrayList<>();

    @BeforeAll
    static void startTheServer() throws IOException {
        screens = ScreenServer.start(folder, valuesUnder(folder));
        PanelBrowser browser = screens.browser();
        for (String path : ScreenServer.pathsOfItsOwnPages(folder)) {
            PAGES.add(browser.go(path).page());
        }
    }

    @AfterAll
    static void closeTheServer() {
        screens.close();
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("Every page of the control panel's own carries the start's mark and the tab's key, derived from the start's token")
    void everyPageCarriesTheStartsMarkAndTheKeyDerivedFromTheToken() {
        List<String> marks = PAGES.stream().map(page -> page.document().body().attr("data-start")).distinct().toList();
        List<String> keys = PAGES.stream().map(page -> page.document().body().attr("data-tab-key")).distinct().toList();

        claim("every page of the control panel's own was answered with a page",
                () -> assertThat(PAGES).isNotEmpty().extracting(Page::status).containsOnly(200));
        claim("every page carries one mark of the start, the same on each, 22 characters of base64url",
                () -> assertThat(marks).satisfiesExactly(mark -> assertThat(mark).matches(MARK)));
        claim("and one tab's key, the same on each: the HMAC-SHA256 of the key's text, keyed with the start's token",
                () -> assertThat(keys).containsExactly(TabProof.tabKeyOf(screens.server().token())));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("The script is served as JavaScript to a request holding the cookie alone, the same at every start, holding no mark, key or token")
    void theScriptIsServedWithTheCookieAloneTheSameAtEveryStart(@TempDir Path another) throws IOException {
        Response script = script(screens, true);
        Response withoutTheCookie = script(screens, false);
        List<String> data = new ArrayList<>(dataOf(screens));
        Response atAnotherStart;
        try (ScreenServer second = ScreenServer.start(another, valuesUnder(another))) {
            atAnotherStart = script(second, true);
            data.addAll(dataOf(second));
        }
        List<String> held = data.stream().filter(value -> !value.isBlank() && script.body().contains(value)).toList();

        claim("asked for with the cookie, the script is answered 200, as JavaScript in UTF-8",
                () -> assertThat(List.of(script.status(), script.header("Content-Type")))
                        .containsExactly(200, List.of(SCRIPT_TYPE)));
        claim("a second start answers the same text, which is not empty",
                () -> assertThat(script.body()).isNotBlank().isEqualTo(atAnotherStart.body()));
        claim("holding neither start's mark, tab's key or token", () -> assertThat(held).isEmpty());
        claim("and asked for without the cookie, it is refused as every page is",
                () -> assertThat(withoutTheCookie.status()).isEqualTo(403));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("The script polls /alive without the cookie, and names nothing that parses a value as markup, runs text as code or repeats on an interval")
    void theScriptPollsAliveWithoutTheCookieAndParsesNoMarkup() throws IOException {
        String text = script(screens, true).body();
        List<String> named = NEVER_IN_THE_SCRIPT.stream().filter(never -> never.matcher(text).find())
                .map(Pattern::pattern).toList();

        claim("the script asks /alive", () -> assertThat(text).contains(TabProof.ALIVE));
        claim("with a fetch that sends no cookie, credentials: 'omit'",
                () -> assertThat(WITHOUT_THE_COOKIE.matcher(text).find()).isTrue());
        claim("and it names none of innerHTML, outerHTML, insertAdjacentHTML, document.write, eval, Function and"
                + " setInterval", () -> assertThat(named).isEmpty());
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("Every page of the control panel's own allows a script and a connection only from its own origin, beside the policy every response carries")
    void everyPageOfItsOwnAllowsScriptsAndConnectionsOnlyFromItsOrigin() {
        Map<String, List<String>> policies = new LinkedHashMap<>();
        for (Page page : PAGES) {
            policies.put(page.address().getPath(), page.headers().entrySet().stream()
                    .filter(header -> header.getKey().equalsIgnoreCase("Content-Security-Policy"))
                    .flatMap(header -> header.getValue().stream())
                    .toList());
        }

        claim("every page carries the policy every response carries, then its own, allowing a script and a"
                        + " connection only from the control panel's origin, and no third",
                () -> assertThat(policies).isNotEmpty().allSatisfy((path, values) -> assertThat(values).as(path)
                        .containsExactly(POLICY_OF_EVERY_RESPONSE, POLICY_OF_ITS_OWN_PAGES)));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("While its child runs, the invocation page asks the script to reload it every 2 seconds, with a refresh inside noscript for a browser running none, and once the child is stopped carries neither")
    void theInvocationPageRefreshesThroughTheScriptAndTheNoscriptOnlyWhileTheChildRuns(@TempDir Path own)
            throws IOException {
        Path workingDirectory = Files.createDirectories(own.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        Page running;
        Page stopped;
        try (ScreenServer server = ScreenServer.start(own, valuesUnder(own))) {
            PanelBrowser browser = server.browser().press("launch", "run", Map.of());
            running = browser.page();
            browser.press("stop", "stop", Map.of());
            stopped = browser.press("stop-confirmed", "stop", Map.of()).page();
        }

        claim("pressing Run lands on the invocation page with the child running, and Stop on it with the child stopped",
                () -> assertThat(List.of(ScreenServer.byId(running, "child").attr("data-ending"),
                                ScreenServer.byId(stopped, "child").attr("data-ending")))
                        .containsExactly("running", "stopped"));
        claim("while the child runs, the page asks the script to reload it every 2 seconds",
                () -> assertThat(running.document().body().attr("data-refresh-seconds")).isEqualTo(EVERY_TWO_SECONDS));
        claim("and holds one refresh of 2 seconds, inside a noscript in its head, which only a browser running no"
                        + " script reads",
                () -> assertThat(refreshesOf(running)).containsExactly("head > noscript: " + EVERY_TWO_SECONDS));
        claim("once the child is stopped, the page carries neither",
                () -> assertThat(List.of(stopped.document().body().hasAttr("data-refresh-seconds"),
                                refreshesOf(stopped)))
                        .containsExactly(false, List.of()));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("Asked for without the cookie, /alive answers the pages' mark and a proof of the port, the mark and the nonce under the tab's key")
    void theAliveAnswerProvesTheStartWithoutTheCookie() throws IOException {
        int port = screens.server().address().getPort();
        String mark = PAGES.getFirst().document().body().attr("data-start");
        String key = TabProof.tabKeyOf(screens.server().token());
        String firstNonce = TabProof.nonce();
        String secondNonce = TabProof.nonce();
        Response first = alive(TabProof.aliveTarget(firstNonce), "same-origin");
        Response second = alive(TabProof.aliveTarget(secondNonce), "same-origin");

        claim("asked for with a nonce and no cookie, /alive is answered 200, in plain text",
                () -> assertThat(List.of(first.status(), first.header("Content-Type")))
                        .containsExactly(200, List.of(PLAIN_TEXT)));
        claim("its body is the pages' mark, one space, and the HMAC-SHA256 of the port, the mark and the nonce under"
                        + " the tab's key",
                () -> assertThat(first.body()).isEqualTo(mark + " " + TabProof.proof(key, port, mark, firstNonce)));
        claim("another nonce is answered with its own proof, not the first's",
                () -> assertThat(second.body()).isEqualTo(mark + " " + TabProof.proof(key, port, mark, secondNonce))
                        .isNotEqualTo(first.body()));
        claim("and the answer carries the four headers every response carries, each once, with its value",
                () -> assertThat(EVERY_RESPONSE).allSatisfy((name, value) -> assertThat(first.header(name)).as(name)
                        .containsExactly(value)));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("/alive refuses a request of another site or for another host, names GET as its one method, and answers no nonce it cannot read")
    void anAliveRequestOfAnotherSiteHostOrMethodOrABadNonceIsNotAnswered() throws IOException {
        InetSocketAddress server = screens.server().address();
        String host = "Host: 127.0.0.1:" + server.getPort();
        String target = TabProof.aliveTarget(TabProof.nonce());
        Response crossSite = alive(target, "cross-site");
        Response anotherHost = RawHttp.send(server, "GET", target, List.of("Host: evil.example:" + server.getPort()));
        Response posted = RawHttp.send(server, "POST", target,
                List.of(host, "Origin: http://127.0.0.1:" + server.getPort()));
        List<Integer> unread = new ArrayList<>();
        for (String query : NOT_A_NONCE) {
            unread.add(alive(query, "same-origin").status());
        }

        claim("sent from another site, or to another host, it is refused with the line every refusal gives",
                () -> assertThat(List.of(List.of(crossSite.status(), crossSite.body()),
                                List.of(anotherHost.status(), anotherHost.body())))
                        .containsExactly(List.of(403, ControlPanelServer.REFUSAL),
                                List.of(403, ControlPanelServer.REFUSAL)));
        claim("a POST is answered 405, naming GET as the one method allowed",
                () -> assertThat(List.of(posted.status(), posted.header("Allow")))
                        .containsExactly(405, List.of("GET")));
        claim("and no nonce, a nonce one character short, one in capitals and one beside another parameter are each"
                        + " answered 400",
                () -> assertThat(unread).containsExactly(400, 400, 400, 400));
    }

    /** The script asked for on the server of {@code screens}, with the start's cookie or without it. */
    private static Response script(ScreenServer screens, boolean withTheCookie) throws IOException {
        InetSocketAddress server = screens.server().address();
        List<String> lines = new ArrayList<>(List.of("Host: 127.0.0.1:" + server.getPort(),
                "Sec-Fetch-Site: same-origin"));
        if (withTheCookie) {
            lines.add("Cookie: " + ControlPanelServer.COOKIE + "=" + screens.server().token());
        }
        return RawHttp.send(server, "GET", TabProof.SCRIPT, lines);
    }

    /** {@code target} asked of the class's server with no cookie, marked as sent from {@code site}. */
    private static Response alive(String target, String site) throws IOException {
        InetSocketAddress server = screens.server().address();
        return RawHttp.send(server, "GET", target, List.of("Host: 127.0.0.1:" + server.getPort(),
                "Sec-Fetch-Site: " + site));
    }

    /** The mark and the tab's key of the first page of {@code screens}, and its token. */
    private static List<String> dataOf(ScreenServer screens) {
        Element body = screens.browser().page().document().body();
        return List.of(body.attr("data-start"), body.attr("data-tab-key"), screens.server().token());
    }

    /** Each refresh of the page, as the elements it lies in and its seconds. */
    private static List<String> refreshesOf(Page page) {
        return page.document().select("meta").stream()
                .filter(meta -> meta.attr("http-equiv").toLowerCase(Locale.ROOT).equals("refresh"))
                .map(meta -> meta.parent().parent().tagName() + " > " + meta.parent().tagName() + ": "
                        + meta.attr("content"))
                .toList();
    }

    /** Launch values naming a corpus root and a working directory under {@code folder}, each made. */
    private static LaunchValues valuesUnder(Path folder) throws IOException {
        return ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work")));
    }

    private static Map<String, String> everyResponse() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Referrer-Policy", "same-origin");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Content-Security-Policy", POLICY_OF_EVERY_RESPONSE);
        return headers;
    }
}
