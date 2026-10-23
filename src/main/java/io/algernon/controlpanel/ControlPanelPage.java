package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one owner of the control panel's pages' skeleton, styling and escaping (ADR-302 §10; ADR-285 §4): every page is
 * written through {@link #render}, every text a page shows from outside it goes through {@link #escape}, and no page
 * holds a script but the control panel's own, by its address, or an attribute of an event (ADR-325 §4.1). The words, layout and styling of a page are no decision's
 * (ADR-302, What this does not decide).
 */
final class ControlPanelPage {

    /** The path of the control panel's one script (ADR-325 §4.2). */
    static final String SCRIPT_PATH = "/control-panel.js";

    /**
     * The control panel's one script, the same at every start (ADR-325 §4.2): it reads the page's {@code data-start},
     * {@code data-tab-key} and {@code data-refresh-seconds}, polls {@code /alive} one second after the last poll
     * settled, never on an interval and never from inside another timer's callback, reloads the page where the server
     * answers as a later start with a proof the tab's key verifies, and, where the page asks, once the server has
     * answered as the same start and the page's seconds have passed. It writes nothing into the page.
     */
    static final String SCRIPT = """
            (function () {
              'use strict';
              var body = document.body;
              var start = body.getAttribute('data-start');
              var tabKey = body.getAttribute('data-tab-key');
              var seconds = parseInt(body.getAttribute('data-refresh-seconds') || '0', 10);
              var loadedAt = Date.now();
              var subtle = window.crypto && window.crypto.subtle;
              if (!subtle || !start || !tabKey) {
                if (seconds > 0) {
                  window.setTimeout(function () { window.location.reload(); }, seconds * 1000);
                }
                return;
              }
              var encoder = new TextEncoder();
              function bytesOf(text) {
                var plain = text.replace(/-/g, '+').replace(/_/g, '/');
                while (plain.length % 4 !== 0) { plain += '='; }
                var raw = window.atob(plain);
                var bytes = new Uint8Array(raw.length);
                for (var i = 0; i < raw.length; i++) { bytes[i] = raw.charCodeAt(i); }
                return bytes;
              }
              function nonceOf() {
                var bytes = new Uint8Array(16);
                window.crypto.getRandomValues(bytes);
                var text = '';
                for (var i = 0; i < bytes.length; i++) {
                  text += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16);
                }
                return text;
              }
              function proven(answer, nonce) {
                var parts = answer.split(' ');
                if (parts.length !== 2) { return Promise.resolve(null); }
                var signed = window.location.port + ' ' + parts[0] + ' ' + nonce;
                return subtle.importKey('raw', bytesOf(tabKey), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify'])
                  .then(function (key) {
                    return subtle.verify('HMAC', key, bytesOf(parts[1]), encoder.encode(signed));
                  })
                  .then(function (verified) { return verified ? parts[0] : null; });
              }
              function poll() {
                var nonce = nonceOf();
                var reloading = false;
                window.fetch('/alive?nonce=' + nonce, {
                  credentials: 'omit',
                  cache: 'no-store',
                  redirect: 'error',
                  signal: AbortSignal.timeout(1000)
                })
                  .then(function (response) { return response.status === 200 ? response.text() : null; })
                  .then(function (answer) { return answer === null ? null : proven(answer, nonce); })
                  .then(function (mark) {
                    if (mark === null) { return; }
                    if (mark !== start) {
                      reloading = true;
                      window.location.reload();
                    } else if (seconds > 0 && Date.now() - loadedAt >= seconds * 1000) {
                      reloading = true;
                      window.location.reload();
                    }
                  })
                  .catch(function () { })
                  .then(function () {
                    if (!reloading) { window.setTimeout(poll, 1000); }
                  });
              }
              window.setTimeout(poll, 1000);
            })();
            """;

    private static final String STYLE = """
            :root { color-scheme: light dark; --ink: #1d2330; --paper: #fbfbf8; --rule: #c9cdd6; --accent: #1f5fbf;
              --warning: #8a4b00; }
            @media (prefers-color-scheme: dark) { :root { --ink: #e6e8ee; --paper: #14171f; --rule: #3a4150;
              --accent: #7fb0ff; --warning: #f0b45a; } }
            body { background: var(--paper); color: var(--ink); font: 16px/1.5 system-ui, sans-serif; margin: 0 auto;
              max-width: 56rem; padding: 0 16px 48px; }
            nav a { margin-right: 1rem; }
            a { color: var(--accent); }
            fieldset, table { border: 1px solid var(--rule); }
            form { margin: 1rem 0; padding: .5rem 0; }
            label { display: block; margin: .5rem 0 .1rem; font-weight: 600; }
            input[type=text] { width: 100%; box-sizing: border-box; padding: .3rem; }
            button { padding: .35rem .9rem; margin-right: .5rem; }
            td, th { border-bottom: 1px solid var(--rule); padding: .25rem .5rem; text-align: left; }
            .outcome, .warnings li { color: var(--warning); }
            pre { overflow-x: auto; border: 1px solid var(--rule); padding: .5rem; }
            code { overflow-wrap: anywhere; }
            """;

    /** The pages the navigation leads to, by path and name. */
    private static final List<String[]> NAVIGATION = List.of(new String[] {"/", "Launch"},
            new String[] {"/invocation", "Invocation"}, new String[] {"/next", "Next"},
            new String[] {"/labels", "Questions"}, new String[] {"/code-sets", "Code sets"},
            new String[] {"/profile", "Profile"});

    private ControlPanelPage() {
    }

    /**
     * The whole page, carrying the start's mark and the tab's key the server gave the exchange, the control panel's one
     * script by its address, and, where it asks to be loaded again, the script's refresh and the browser's own inside
     * {@code <noscript>} (ADR-325 §4.1, §5).
     *
     * @param exchange the request the page answers, which the server marked with the start's mark and the tab's key
     * @param refreshSeconds how often the page asks to be loaded again, none where it is not above zero
     * @param body the page's content, already escaped
     */
    static String render(HttpExchange exchange, String title, int refreshSeconds, String body) {
        String refresh = refreshSeconds > 0
                ? "<noscript><meta http-equiv=\"refresh\" content=\"" + refreshSeconds + "\"></noscript>\n" : "";
        String marks = " data-start=\"" + escape(attribute(exchange, ControlPanelServer.MARK_ATTRIBUTE)) + "\""
                + " data-tab-key=\"" + escape(attribute(exchange, ControlPanelServer.TAB_KEY_ATTRIBUTE)) + "\""
                + (refreshSeconds > 0 ? " data-refresh-seconds=\"" + refreshSeconds + "\"" : "");
        String navigation = NAVIGATION.stream()
                .map(entry -> "<a href=\"" + entry[0] + "\">" + entry[1] + "</a>")
                .collect(Collectors.joining(" "));
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" + refresh
                + "<script src=\"" + SCRIPT_PATH + "\" defer></script>\n"
                + "<title>" + escape(title) + "</title>\n<style>\n" + STYLE + "</style>\n</head>\n<body" + marks
                + ">\n<nav>" + navigation + "</nav>\n<h1>" + escape(title) + "</h1>\n" + body
                + "\n</body>\n</html>\n";
    }

    private static String attribute(HttpExchange exchange, String name) {
        return exchange.getAttribute(name) instanceof String value ? value : "";
    }

    /** {@code text} as HTML text or as the value of an attribute in double quotes. */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char next = text.charAt(i);
            switch (next) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(next);
            }
        }
        return escaped.toString();
    }

    /** An {@code element} carrying {@code id}, holding {@code text} escaped; an empty one where the text is empty. */
    static String text(String element, String id, String text) {
        return "<" + element + " id=\"" + escape(id) + "\">" + escape(text) + "</" + element + ">";
    }

    /** The line a form's last post gave, once, as the element {@code outcome}; nothing where there is none. */
    static String outcome(String line) {
        return line == null ? "" : "<p id=\"outcome\" class=\"outcome\">" + escape(line) + "</p>";
    }

    /** A link to a report of ADR-302 §3, opening in a tab of its own. */
    static String reportLink(String file) {
        return "<a href=\"/report/" + escape(file) + "\" target=\"_blank\" rel=\"noopener\">" + escape(file) + "</a>";
    }

    /** A single text control and its label, named {@code name}. */
    static String field(String name, String label, String value, boolean required) {
        return "<label for=\"f-" + escape(name) + "\">" + escape(label) + "</label>\n<input type=\"text\" id=\"f-"
                + escape(name) + "\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\""
                + (required ? " required" : "") + ">\n";
    }

    /** A submit button named {@code name}. */
    static String button(String name, String caption, boolean disabled) {
        return "<button type=\"submit\" name=\"" + escape(name) + "\" value=\"" + escape(name) + "\""
                + (disabled ? " disabled" : "") + ">" + escape(caption) + "</button>";
    }

    /**
     * What a change of {@code key} costs, in words (ADR-295 §5): the runs the next invocation makes again, and
     * whether the approval is closed and the label file refused by it.
     */
    static String cost(ProfileKey key) {
        ProfileKey.Cost cost = key.cost();
        if (cost.runs().isEmpty()) {
            return "Saving " + key.key() + " makes no run again: it enters none.";
        }
        String runs = cost.runs().stream().map(ProfileKey.Run::stageName).collect(Collectors.joining(", "));
        StringBuilder words = new StringBuilder("Saving " + key.key() + " makes the next invocation mint again the runs of "
                + runs + ".");
        if (cost.approvalCloses()) {
            words.append(" An arrangement approved by name no longer matches the one the invocation arrives at, and is asked for"
                    + " again, unless arrangementApproved holds auto.");
        }
        if (cost.labelsRefused()) {
            words.append(" The label file names the old seed set, so vespera label refuses it.");
        }
        return words.toString();
    }
}
