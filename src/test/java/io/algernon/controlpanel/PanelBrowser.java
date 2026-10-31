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
import java.util.Locale;
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
 * printed, keeps any cookie it is sent, of which the control panel sends none (ADR-325 §2), follows a {@code 303} with
 * a {@code GET}, and submits a form by its name with the fields jsoup reads from it as a browser would send them, a
 * {@code POST} carrying its page's origin. Of a form's submit buttons a browser sends only the one pressed (ADR-302
 * §9): {@link #press} sends that one, and {@link #submit} none. A form whose action leaves the panel's origin is
 * refused, as the panel's {@code form-action 'self'} has a browser refuse it (ADR-291 §7). Each of these is one report
 * step.
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
        Allure.step("submit the form '" + formName + "'", () -> send(form(formName), fields, null));
        return this;
    }

    /**
     * Submits the page's one form named {@code formName} as {@link #submit} does, pressing its submit button named
     * {@code button}: of the form's submit buttons only that one is sent, as a browser sends only the one pressed.
     *
     * @throws AssertionError where {@link #submit} would fail, or the form holds no submit button by that name, or
     *                        holds it disabled, which a browser cannot press
     */
    PanelBrowser press(String formName, String button, Map<String, String> fields) {
        Allure.step("press '" + button + "' of the form '" + formName + "'", () -> {
            FormElement form = form(formName);
            Element pressed = submitButtons(form).stream()
                    .filter(control -> control.attr("name").equals(button))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the form '" + formName + "' holds no button named '"
                            + button + "'"));
            if (pressed.hasAttr("disabled")) {
                throw new AssertionError("the button '" + button + "' of the form '" + formName
                        + "' is disabled, and a browser cannot press it");
            }
            send(form, fields, pressed);
        });
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

    /** The form's submit buttons: each {@code button} typed neither {@code button} nor {@code reset}, each submit input. */
    private static List<Element> submitButtons(FormElement form) {
        return form.elements().stream()
                .filter(control -> control.nameIs("button")
                        ? !List.of("button", "reset").contains(control.attr("type").toLowerCase(Locale.ROOT))
                        : control.nameIs("input") && control.attr("type").equalsIgnoreCase("submit"))
                .toList();
    }

    /** Sends the form with {@code fields} set, and of its submit buttons only {@code pressed}, none where it is null. */
    private void send(FormElement form, Map<String, String> fields, Element pressed)
            throws IOException, InterruptedException {
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
        List<Element> unsent = submitButtons(form).stream()
                .filter(control -> control != pressed && control.hasAttr("name"))
                .toList();
        List<String> names = unsent.stream().map(control -> control.attr("name")).toList();
        unsent.forEach(control -> control.removeAttr("name"));
        // jsoup reads no button element into a form's data, so the one pressed stands in its place as a hidden field
        Element standIn = pressed != null && pressed.nameIs("button")
                ? new Element("input").attr("type", "hidden")
                        .attr("name", pressed.attr("name")).attr("value", pressed.val())
                : null;
        if (standIn != null) {
            pressed.before(standIn);
        }
        // jsoup reads a textarea's value with its white space collapsed; a browser sends its lines, ended by CRLF
        Map<String, String> lines = form.select("textarea[name]").stream().collect(Collectors.toMap(
                textarea -> textarea.attr("name"),
                textarea -> textarea.wholeText().replace("\r\n", "\n").replace("\n", "\r\n")));
        String body;
        try {
            body = form.formData().stream()
                    .map(field -> lines.containsKey(field.key())
                            ? URLEncoder.encode(field.key(), StandardCharsets.UTF_8) + "="
                                    + URLEncoder.encode(lines.get(field.key()), StandardCharsets.UTF_8)
                            : encoded(field))
                    .collect(Collectors.joining("&"));
        } finally {
            if (standIn != null) {
                standIn.remove();
            }
            for (int i = 0; i < unsent.size(); i++) {
                unsent.get(i).attr("name", names.get(i));
            }
        }
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
