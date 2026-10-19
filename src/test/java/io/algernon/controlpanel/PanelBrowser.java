package io.algernon.controlpanel;

import io.qameta.allure.Allure;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Connection.KeyVal;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;

/**
 * The browser the control panel's screens are tested through (ADR-297): the JDK's {@link HttpClient} with a cookie
 * store, and each page it lands on parsed by jsoup as a browser parses HTML.
 *
 * <p>It does what a browser does with a page of server-written forms and no script: it opens the address the panel
 * printed, keeps the cookie the token is exchanged for, follows a {@code 303} with a {@code GET}, and submits a form
 * by its name with the fields jsoup reads from it as a browser would send them, a {@code POST} carrying its page's
 * origin. A form whose action leaves the panel's origin is refused, as the panel's {@code form-action 'self'} has a
 * browser refuse it (ADR-291 §7). Each of these is one report step.
 */
final class PanelBrowser {

    /** How long a test waits for the panel's answer before it fails rather than hangs. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;

    private Page page;

    private PanelBrowser(HttpClient client) {
        this.client = client;
    }

    /** A page the browser landed on: its address after every redirect, its status, its headers and its document. */
    record Page(URI address, int status, Map<String, List<String>> headers, Document document) {

        /** Every value sent under {@code name}, compared without case, joined by a comma; empty where none was. */
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .collect(Collectors.joining(", "));
        }
    }

    /** A new browser, holding no cookie, that opens {@code firstAddress}. */
    static PanelBrowser opening(URI firstAddress) {
        PanelBrowser browser = new PanelBrowser(HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build());
        Allure.step("open the address the control panel printed when it started",
                () -> browser.land(HttpRequest.newBuilder(firstAddress).GET()));
        return browser;
    }

    /** The page the browser is on. */
    Page page() {
        return page;
    }

    /** Goes to {@code path} on the panel's own origin, as following a link to it does. */
    PanelBrowser go(String path) {
        Allure.step("go to " + path,
                () -> land(HttpRequest.newBuilder(page.address().resolve(path)).GET()));
        return this;
    }

    /**
     * Submits the page's one form named {@code formName}, each of {@code fields} set by its control's name; a control
     * not named in {@code fields} sends what the page gave it, as a browser sends it untouched.
     *
     * @throws AssertionError where the page holds no form or more than one by that name, the form no control named
     *                        as a field is, or the form's action leaves the panel's origin
     */
    PanelBrowser submit(String formName, Map<String, String> fields) {
        Allure.step("submit the form '" + formName + "'", () -> send(form(formName), fields));
        return this;
    }

    private FormElement form(String formName) {
        List<Element> named = page.document().select("form").stream()
                .filter(form -> form.attr("name").equals(formName))
                .toList();
        if (named.size() != 1) {
            throw new AssertionError("the page at " + page.address().getPath() + " holds " + named.size()
                    + " forms named '" + formName + "', where a test submits exactly one");
        }
        return (FormElement) named.getFirst();
    }

    private void send(FormElement form, Map<String, String> fields) throws IOException, InterruptedException {
        for (Map.Entry<String, String> field : fields.entrySet()) {
            Elements controls = form.getElementsByAttributeValue("name", field.getKey());
            if (controls.isEmpty()) {
                throw new AssertionError("the form '" + form.attr("name") + "' holds no control named '"
                        + field.getKey() + "'");
            }
            for (Element control : controls) {
                if (control.nameIs("select")) {
                    for (Element option : control.select("option")) {
                        option.attr("selected", option.val().equals(field.getValue()));
                    }
                } else if (control.nameIs("textarea")) {
                    control.text(field.getValue());
                } else {
                    control.val(field.getValue());
                }
            }
        }
        URI action = page.address().resolve(form.attr("action"));
        if (!origin(action).equals(origin(page.address()))) {
            throw new AssertionError("the form '" + form.attr("name") + "' is sent to " + origin(action)
                    + ", which is not the panel's origin " + origin(page.address())
                    + ", and a browser refuses it under form-action 'self'");
        }
        String body = form.formData().stream()
                .map(PanelBrowser::encoded)
                .collect(Collectors.joining("&"));
        if (form.attr("method").equalsIgnoreCase("post")) {
            land(HttpRequest.newBuilder(action)
                    .header("Origin", origin(page.address()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        } else {
            land(HttpRequest.newBuilder(URI.create(action.getScheme() + "://" + action.getRawAuthority()
                    + action.getRawPath() + (body.isEmpty() ? "" : "?" + body))).GET());
        }
    }

    private void land(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request.timeout(TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        page = new Page(response.uri(), response.statusCode(), response.headers().map(),
                Jsoup.parse(response.body(), response.uri().toString()));
    }

    private static String encoded(KeyVal field) {
        return URLEncoder.encode(field.key(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(field.value(), StandardCharsets.UTF_8);
    }

    private static String origin(URI address) {
        return address.getScheme() + "://" + address.getRawAuthority();
    }
}
