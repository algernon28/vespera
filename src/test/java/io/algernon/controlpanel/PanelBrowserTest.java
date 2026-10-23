package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The browser the control panel's screens are tested through (ADR-297), driven against the panel's own server
 * (ADR-291) started with stub pages: a page of forms, a page that echoes the request it was sent, and a page that
 * answers a {@code POST} with a {@code 303}, as a screen that writes answers.
 *
 * <p>Every test opens the address the server printed, the root with no token (ADR-325 §2), and every form is sent
 * through the server's checks of {@code Host}, {@code Sec-Fetch-Site} and {@code Origin}: a request the browser sent
 * wrongly is refused before a stub page runs.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("568")
@Link(name = "ADR-297", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_TESTED_THROUGH_THE_PAGES_IT_SERVES, type = "adr")
class PanelBrowserTest {

    /** The page of forms, at the root, where the printed address lands. */
    private static final String FORMS = "/";

    /** The page that echoes the method, the origin and the fields it was sent. */
    private static final String ECHO = "/echo";

    /** The page that answers a {@code POST} with a {@code 303} to the echo, as a screen that writes answers. */
    private static final String STORE = "/store";

    /** A corpus root no code page of Windows' but UTF-8 holds whole, with a backslash and a space a form encodes. */
    private static final String CORPUS_ROOT = "D:\\archiwum łódź\\漢字";

    /**
     * The forms page: a form of each method to the echo, one to the store, two of one name, and one whose action leaves
     * the panel.
     */
    private static final String FORMS_PAGE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>Forms</title></head><body>
            <form name="launch" method="post" action="/echo">
              <input type="text" name="corpus" value="C:\\old">
              <input type="hidden" name="kind" value="run">
              <select name="mode"><option value="draft">Draft</option><option value="final" selected>Final</option></select>
              <textarea name="note"></textarea>
            </form>
            <form name="find" method="get" action="/echo"><input type="text" name="q" value=""></form>
            <form name="answer" method="post" action="/store"><input type="hidden" name="choice" value="keep"></form>
            <form name="twice" method="post" action="/echo"></form>
            <form name="twice" method="post" action="/echo"></form>
            <form name="elsewhere" method="post" action="http://192.0.2.1/x"><input name="corpus" value=""></form>
            <form name="buttons" method="post" action="/echo">
              <input type="hidden" name="kind" value="run">
              <button type="submit" name="run" value="go">Run</button>
              <button name="label">Label</button>
              <input type="submit" name="check" value="Check">
              <button type="submit" name="held" value="go" disabled>Held</button>
            </form>
            </body></html>
            """;

    /** Every request a stub page ran, as its method and path, in the order run. */
    private static final List<String> RAN = new CopyOnWriteArrayList<>();

    private static ControlPanelServer server;

    private static String origin;

    @BeforeAll
    static void startTheServer() throws IOException {
        server = ControlPanelServer.start(Map.of(
                FORMS, exchange -> answer(exchange, FORMS_PAGE),
                ECHO, PanelBrowserTest::echo,
                STORE, PanelBrowserTest::store));
        origin = "http://127.0.0.1:" + server.address().getPort();
    }

    @AfterAll
    static void closeTheServer() {
        server.close();
    }

    @BeforeEach
    void forgetWhatRan() {
        RAN.clear();
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("Opening the printed address lands on the page at the root, at that very address, and the next page is answered")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void openingThePrintedAddressLandsOnTheRootAndTheNextPageIsAnswered() {
        PanelBrowser browser = PanelBrowser.opening(server.firstAddress());
        Page landed = browser.page();
        Page next = browser.go(ECHO).page();

        claim("the address the panel printed is the root, and is answered with the page there, 200, at that very"
                        + " address, no redirect having moved it",
                () -> assertThat(List.of(landed.status(), landed.address().toString(),
                                server.firstAddress().toString()))
                        .containsExactly(200, origin + FORMS, origin + FORMS));
        claim("the page is read as a document, its title being the one the page wrote",
                () -> assertThat(landed.document().title()).isEqualTo("Forms"));
        claim("and the next page asked for is answered, 200, by its own page",
                () -> assertThat(List.of(next.status(), text(next, "method"))).containsExactly(200, "GET"));
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form posted is sent from the panel's own origin with every field a browser sends, in the page's order, in UTF-8")
    void aFormPostedSendsEveryFieldFromThePanelsOwnOrigin() {
        Page echoed = PanelBrowser.opening(server.firstAddress())
                .submit("launch", Map.of("corpus", CORPUS_ROOT, "mode", "draft"))
                .page();

        claim("the form is sent as a POST to its action, whose page answered it rather than the server refusing it",
                () -> assertThat(List.of(echoed.status(), text(echoed, "method"), echoed.address().getPath()))
                        .containsExactly(200, "POST", ECHO));
        claim("from the panel's own origin, as a browser names it on a form it posts",
                () -> assertThat(text(echoed, "origin")).isEqualTo(origin));
        claim("with the four fields in the order the page wrote their controls: the corpus root as given, whole in"
                        + " UTF-8, the hidden kind as the page set it, the option chosen in place of the one the page"
                        + " selected, and the empty note",
                () -> assertThat(fields(echoed))
                        .containsExactly("corpus=" + CORPUS_ROOT, "kind=run", "mode=draft", "note="));
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form of method get is sent as a GET whose query holds its fields")
    void aFormOfMethodGetIsSentAsAQuery() {
        Page echoed = PanelBrowser.opening(server.firstAddress())
                .submit("find", Map.of("q", CORPUS_ROOT))
                .page();

        claim("the form is sent as a GET to its action, with its one field in the query",
                () -> assertThat(List.of(text(echoed, "method"), echoed.address().getPath(), fields(echoed)))
                        .containsExactly("GET", ECHO, List.of("q=" + CORPUS_ROOT)));
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form answered with a 303 lands on the page it points to, asked for with a GET")
    void aFormAnsweredWithA303LandsOnThePageItPointsTo() {
        Page landed = PanelBrowser.opening(server.firstAddress())
                .submit("answer", Map.of())
                .page();

        claim("the POST ran the page it was sent to, then the page the 303 points to ran on a GET, in that order",
                () -> assertThat(RAN).containsExactly("GET " + FORMS, "POST " + STORE, "GET " + ECHO));
        claim("and the browser is on that page, 200",
                () -> assertThat(List.of(landed.status(), landed.address().getPath())).containsExactly(200, ECHO));
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form the page does not hold, two forms of one name, or a field its form does not hold, fails the test naming it")
    void aFormOrAFieldThePageDoesNotHoldFailsTheTest() {
        PanelBrowser browser = PanelBrowser.opening(server.firstAddress());

        claim("submitting a form by a name no form of the page carries fails, naming the form and the page",
                () -> assertThatThrownBy(() -> browser.submit("absent", Map.of()))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the page at / holds 0 forms named 'absent', where a test submits exactly one"));
        claim("submitting a form by a name two forms of the page carry fails, naming the form and the page",
                () -> assertThatThrownBy(() -> browser.submit("twice", Map.of()))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the page at / holds 2 forms named 'twice', where a test submits exactly one"));
        claim("setting a field the form holds no control for fails, naming the form and the field",
                () -> assertThatThrownBy(() -> browser.submit("find", Map.of("corpus", CORPUS_ROOT)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the form 'find' holds no control named 'corpus'"));
        claim("and none of the three sent a request: the forms page, opened first, is the only page that ran",
                () -> assertThat(RAN).containsExactly("GET " + FORMS));
    }

    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form whose action leaves the panel's origin is refused, as the panel's policy has a browser refuse it")
    void aFormWhoseActionLeavesThePanelIsRefused() {
        PanelBrowser browser = PanelBrowser.opening(server.firstAddress());

        claim("submitting the form sent to another address fails, naming that address and the panel's own",
                () -> assertThatThrownBy(() -> browser.submit("elsewhere", Map.of("corpus", CORPUS_ROOT)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the form 'elsewhere' is sent to http://192.0.2.1, which is not the panel's origin "
                                + origin + ", and a browser refuses it under form-action 'self'"));
        claim("and the browser stays on the page it was on",
                () -> assertThat(browser.page().address().toString()).isEqualTo(origin + FORMS));
    }

    /**
     * A browser sends, of a form's submit buttons, only the one pressed, and cannot press one that is disabled
     * (ADR-302 §9).
     */
    @Test
    @Story("The control panel's screens are tested as a browser uses them")
    @DisplayName("A form is sent with only the button pressed, with none when submitted, and a disabled or absent button cannot be pressed")
    @Issue("561")
    @Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
    void aFormIsSentWithOnlyTheButtonPressedAndADisabledOneCannotBePressed() {
        List<String> pressedRun = fields(PanelBrowser.opening(server.firstAddress())
                .press("buttons", "run", Map.of()).page());
        List<String> pressedLabel = fields(PanelBrowser.opening(server.firstAddress())
                .press("buttons", "label", Map.of()).page());
        List<String> pressedCheck = fields(PanelBrowser.opening(server.firstAddress())
                .press("buttons", "check", Map.of()).page());
        List<String> submitted = fields(PanelBrowser.opening(server.firstAddress())
                .submit("buttons", Map.of()).page());

        claim("pressing a button sends the form's hidden field and that button's name and value, and none of the"
                        + " other buttons: a button of type submit, one of no type, and a submit input",
                () -> assertThat(List.of(pressedRun, pressedLabel, pressedCheck)).containsExactly(
                        List.of("kind=run", "run=go"), List.of("kind=run", "label="), List.of("kind=run", "check=Check")));
        claim("submitting the form without pressing a button sends no button at all",
                () -> assertThat(submitted).containsExactly("kind=run"));

        RAN.clear();
        PanelBrowser browser = PanelBrowser.opening(server.firstAddress());
        claim("pressing a disabled button fails, naming it and its form",
                () -> assertThatThrownBy(() -> browser.press("buttons", "held", Map.of()))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the button 'held' of the form 'buttons' is disabled, and a browser cannot press it"));
        claim("pressing a button the form does not hold fails, naming it and its form",
                () -> assertThatThrownBy(() -> browser.press("buttons", "stop", Map.of()))
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("the form 'buttons' holds no button named 'stop'"));
        claim("and neither sent a request: the forms page, opened first, is the only page that ran",
                () -> assertThat(RAN).containsExactly("GET " + FORMS));
    }

    private static String text(Page page, String id) {
        Element element = page.document().getElementById(id);
        return element == null ? "" : element.text();
    }

    private static List<String> fields(Page page) {
        return page.document().select("#fields li").eachText();
    }

    /** Echoes the method, the origin and every field of the body or the query, decoded, in the order sent. */
    private static void echo(HttpExchange exchange) throws IOException {
        RAN.add(exchange.getRequestMethod() + " " + ECHO);
        String sent = exchange.getRequestMethod().equals("POST")
                ? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                : exchange.getRequestURI().getRawQuery();
        StringBuilder fields = new StringBuilder();
        if (sent != null && !sent.isEmpty()) {
            Arrays.stream(sent.split("&"))
                    .map(field -> URLDecoder.decode(field, StandardCharsets.UTF_8))
                    .forEach(field -> fields.append("<li>").append(escaped(field)).append("</li>"));
        }
        String sentFrom = exchange.getRequestHeaders().getFirst("Origin");
        answer(exchange, "<!doctype html><html><head><meta charset=\"utf-8\"><title>Echo</title></head><body>"
                + "<p id=\"method\">" + exchange.getRequestMethod() + "</p>"
                + "<p id=\"origin\">" + escaped(sentFrom == null ? "" : sentFrom) + "</p>"
                + "<ol id=\"fields\">" + fields + "</ol></body></html>");
    }

    /** Answers a {@code POST} with a {@code 303} to the echo, the way a screen that writes an answer does. */
    private static void store(HttpExchange exchange) throws IOException {
        RAN.add(exchange.getRequestMethod() + " " + STORE);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Location", ECHO);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static void answer(HttpExchange exchange, String html) throws IOException {
        if (exchange.getRequestURI().getPath().equals(FORMS)) {
            RAN.add(exchange.getRequestMethod() + " " + FORMS);
        }
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
