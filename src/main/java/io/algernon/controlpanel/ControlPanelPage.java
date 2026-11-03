package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one owner of the control panel's pages' skeleton, styling and escaping (ADR-302 §10; ADR-285 §4): every page is
 * written through {@link #render}, every text a page shows from outside it goes through {@link #escape}, and no page
 * holds a script but the control panel's own, by its address, or an attribute of an event (ADR-325 §4.1). The layout is
 * ADR-331 §3's: a sidebar, a top row, tiles and cards, written with no framework and nothing fetched. The words and
 * styling of a page are no decision's (ADR-302, What this does not decide) but for what ADR-331 §8 gives.
 */
final class ControlPanelPage {

    /** The path of the control panel's one script (ADR-325 §4.2). */
    static final String SCRIPT_PATH = "/control-panel.js";

    /**
     * The control panel's one script, the same at every start (ADR-325 §4.2): it reads the page's {@code data-start}
     * and {@code data-refresh-seconds}, polls {@code /alive} one second after the last poll settled, never on an
     * interval and never from inside another timer's callback, reloads the page where the server answers a mark that
     * is not the page's, and, where the page asks, once the server has answered the page's mark and the page's seconds
     * have passed. It writes nothing into the page.
     */
    static final String SCRIPT = """
            (function () {
              'use strict';
              var body = document.body;
              var start = body.getAttribute('data-start');
              var seconds = parseInt(body.getAttribute('data-refresh-seconds') || '0', 10);
              var loadedAt = Date.now();
              var tail = document.getElementById('tail');
              if (tail) {
                var kept = null;
                try { kept = window.sessionStorage.getItem('vespera-tail-scroll'); } catch (ignored) { kept = null; }
                var place = kept === null ? NaN : parseInt(kept, 10);
                tail.scrollTop = isNaN(place) ? tail.scrollHeight : place;
                tail.addEventListener('scroll', function () {
                  try {
                    if (tail.scrollTop + tail.clientHeight < tail.scrollHeight - 2) {
                      window.sessionStorage.setItem('vespera-tail-scroll', String(tail.scrollTop));
                    } else {
                      window.sessionStorage.removeItem('vespera-tail-scroll');
                    }
                  } catch (ignored) { }
                });
              }
              if (!start) { return; }
              function poll() {
                var reloading = false;
                window.fetch('/alive', {
                  credentials: 'omit',
                  cache: 'no-store',
                  redirect: 'error',
                  signal: AbortSignal.timeout(1000)
                })
                  .then(function (response) { return response.status === 200 ? response.text() : null; })
                  .then(function (answer) {
                    if (answer === null || !/^[A-Za-z0-9_-]{22}$/.test(answer)) { return; }
                    if (answer !== start || (seconds > 0 && Date.now() - loadedAt >= seconds * 1000)) {
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

    /** The image the jar defaults to, {@code application.yaml}'s {@code vespera.docling.image}; a contract test holds it. */
    static final String DOCLING_IMAGE_CPU = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The image {@code compose.gpu.yaml} runs the converter from; a contract test holds it. */
    static final String DOCLING_IMAGE_GPU = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The words an example begins with, so that none is read as a value a page holds (ADR-331 §8). */
    static final String EXAMPLE = "e.g. ";

    /** The words a placeholder begins with where it shows the value in use, in place of an example (ADR-334 §1, §2). */
    static final String IN_USE = "in use: ";

    /** The example of a reason, for every provenance (ADR-331 §8). */
    static final String REASON_EXAMPLE = "read off the report beside it: the cut I can live with";

    /** The hint of every provenance (ADR-331 §8). */
    private static final String REASON_HINT = "Required. Why you chose this value, in your words. It is written beside"
            + " it in profile.yaml.";

    private static final String STYLE = """
            :root { --bg: #f5f4f1; --surface: #ffffff; --surface2: #efedf3; --ink: #1e1b2e;
              --dim: #67637a; --rule: #dfdce6; --accent: #4c3fb1; --accent-ink: #fff; --accent-soft: #e9e6f9;
              --sun: #e0752c; --sun-ink: #fff; --ok: #17794f; --ok-soft: #dcf3e7; --warn: #9a5b00;
              --warn-soft: #fbeed4; --bad: #b3282d; --bad-soft: #fbe3e3; }
            @media (prefers-color-scheme: dark) { :root { --bg: #14121d; --surface: #1e1b2b; --surface2: #272338;
              --ink: #ece9f6; --dim: #a29db8; --rule: #352f49; --accent: #a99cff; --accent-ink: #17132b;
              --accent-soft: #2c2750; --sun: #f0914f; --sun-ink: #231003; --ok: #5fd6a0; --ok-soft: #173428;
              --warn: #f0b45a; --warn-soft: #3a2b12; --bad: #ff8c8c; --bad-soft: #3f1d20; } }
            :root { color-scheme: light dark; accent-color: var(--accent);
              --space-1: 2px; --space-2: 4px; --space-3: 8px; --space-4: 12px; --space-5: 16px; --space-6: 24px;
              --text-xs: 11px; --text-sm: 12px; --text-md: 13px; --text-lg: 15px; --text-xl: 18px;
              --control-height: 28px; --sidebar-width: 176px;
              --radius-sm: 4px; --radius: 6px; --radius-lg: 8px; --radius-pill: 999px; }
            * { box-sizing: border-box; }
            body { margin: 0; background: var(--bg); color: var(--ink); font: var(--text-md)/1.4 system-ui, "Segoe UI", sans-serif; }
            a { color: var(--accent); }
            #shell { display: grid; grid-template-columns: var(--sidebar-width) minmax(0, 1fr); min-height: 100vh; }
            #sidebar { position: sticky; top: 0; align-self: start; height: 100vh; padding: var(--space-5) var(--space-3);
              background: var(--surface); border-right: 1px solid var(--rule); }
            .brand { margin: 0 var(--space-3) var(--space-5); color: var(--sun); font-weight: 700; letter-spacing: .06em; }
            #sidebar nav { display: flex; flex-direction: column; gap: var(--space-1); }
            #sidebar nav a { padding: var(--space-2) var(--space-3); border-radius: var(--radius); color: var(--ink); text-decoration: none; }
            #sidebar nav a:hover { background: var(--surface2); }
            #sidebar nav a[aria-current=page] { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
            main { min-width: 0; padding: 0 var(--space-5) var(--space-6); overflow-wrap: anywhere; }
            main.two-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-4);
              grid-auto-flow: dense; align-items: start; align-content: start; }
            #top-row { position: sticky; top: 0; z-index: 2; display: flex; flex-wrap: wrap; align-items: center;
              justify-content: space-between; gap: var(--space-3); padding: var(--space-3) 0; background: var(--bg);
              border-bottom: 1px solid var(--rule); margin-bottom: var(--space-4); }
            form[name=launch] { display: contents; }
            main.two-columns #top-row, main.two-columns #tiles, main.two-columns .outcome { grid-column: 1 / -1; }
            main.two-columns #top-row { margin-bottom: 0; }
            [data-column=left] { grid-column: 1; }
            [data-column=right] { grid-column: 2; }
            #card-preflight { grid-row: span 3; }
            h1 { margin: 0; font-size: var(--text-xl); }
            h2 { margin: 0 0 var(--space-3); font-size: var(--text-lg); }
            .actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
            .card, .tile, fieldset { background: var(--surface); border: 1px solid var(--rule); border-radius: var(--radius-lg);
              padding: var(--space-4); min-width: 0; }
            fieldset { margin: 0 0 var(--space-4); }
            .card { margin-bottom: var(--space-4); }
            main.two-columns .card { margin-bottom: 0; }
            #tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-3); margin: var(--space-4) 0; }
            .tile { display: flex; flex-direction: column; padding: var(--space-3) var(--space-4); border-left: 4px solid var(--rule); }
            .tile-name { color: var(--dim); font-size: var(--text-xs); letter-spacing: .06em; text-transform: uppercase; }
            .tile-value { font-weight: 600; }
            [data-state=ok] { --state: var(--ok); --state-soft: var(--ok-soft); }
            [data-state=warn] { --state: var(--warn); --state-soft: var(--warn-soft); }
            [data-state=bad] { --state: var(--bad); --state-soft: var(--bad-soft); }
            [data-state=idle] { --state: var(--dim); --state-soft: var(--surface2); }
            .tile[data-state] { border-left-color: var(--state); }
            #child { display: inline-block; margin: 0 0 var(--space-4); padding: var(--space-1) var(--space-4); border-radius: var(--radius-pill);
              background: var(--state-soft); color: var(--state); font-weight: 600; }
            #preflight { margin: 0 0 var(--space-4); padding: 0; list-style: none; }
            #preflight li { margin: 0 0 var(--space-2); padding: var(--space-2) var(--space-3); border-radius: var(--radius);
              background: var(--state-soft); color: var(--state); font-weight: 600; }
            .field { margin: 0 0 var(--space-3); }
            label { display: block; margin: 0 0 var(--space-1); font-weight: 600; }
            .control { display: flex; gap: var(--space-2); }
            input[type=text], input[type=number], select, button { height: var(--control-height); padding: 0 var(--space-3);
              border: 1px solid var(--rule); border-radius: var(--radius); font: inherit; }
            input[type=text], input[type=number], select { width: 100%; min-width: 0; background: var(--surface); color: var(--ink); }
            input:focus-visible, select:focus-visible, button:focus-visible, a:focus-visible {
              outline: 2px solid var(--accent); outline-offset: 1px; }
            .hint { margin: var(--space-2) 0 0; color: var(--dim); font-size: var(--text-sm); }
            .status { margin: var(--space-1) 0 0; font-size: var(--text-sm); font-weight: 600; color: var(--dim); }
            .status[data-status=found] { color: var(--ok); }
            .status[data-status=will-be-created] { color: var(--warn); }
            .status[data-status=no-such-folder] { color: var(--bad); }
            button { padding: 0 var(--space-5); background: var(--surface2); color: var(--ink); cursor: pointer; }
            button[name=run] { border-color: var(--sun); background: var(--sun); color: var(--sun-ink); font-weight: 600; }
            button[name=save], button[name=save-then-run], button[name=save-then-label], button[name=use] {
              border-color: var(--accent); background: var(--accent); color: var(--accent-ink); }
            button:disabled { opacity: .5; cursor: not-allowed; }
            .browse { flex: none; }
            .outcome { margin: var(--space-4) 0; padding: var(--space-3) var(--space-4); border-radius: var(--radius); background: var(--warn-soft);
              color: var(--warn); font-weight: 600; }
            .warnings { margin: 0 0 var(--space-4); padding-left: var(--space-5); }
            .warnings li { color: var(--warn); }
            .profile { display: grid; grid-template-columns: minmax(12rem, 18rem) minmax(0, 1fr); gap: var(--space-4);
              align-items: start; }
            #profile-list { margin: 0; padding: 0; list-style: none; }
            #profile-list a { display: block; padding: var(--space-2) var(--space-3); border-radius: var(--radius); color: var(--ink);
              text-decoration: none; }
            #profile-list a:hover { background: var(--surface2); }
            #profile-list .current { display: block; color: var(--dim); font-size: var(--text-xs); }
            .sections > section { display: none; }
            .sections > section:target { display: block; }
            .sections:not(:has(> :target)) > section:first-child { display: block; }
            #folder-roots, #folder-list { margin: 0 0 var(--space-4); padding: 0; list-style: none; display: flex;
              flex-wrap: wrap; gap: var(--space-2); }
            #folder-roots a, #folder-list a, #folder-up, #folder-back { display: inline-block; padding: var(--space-2) var(--space-3);
              border: 1px solid var(--rule); border-radius: var(--radius); background: var(--surface2); text-decoration: none; }
            td, th { padding: var(--space-2) var(--space-3); border-bottom: 1px solid var(--rule); text-align: left; }
            pre { overflow-x: auto; padding: var(--space-3); border: 1px solid var(--rule); border-radius: var(--radius);
              background: var(--surface2); }
            #tail { height: 45vh; overflow-y: auto; }
            #progress-frame { display: block; width: 100%; height: 80vh; border: 1px solid var(--rule);
              border-radius: var(--radius); background: var(--surface); }
            #progress-view p { margin: var(--space-3) 0 0; }
            @media (max-width: 800px) {
              #shell { grid-template-columns: 1fr; }
              #sidebar { position: static; height: auto; border-right: 0; border-bottom: 1px solid var(--rule); }
              #sidebar nav { flex-direction: row; flex-wrap: wrap; }
              main.two-columns, .profile { grid-template-columns: 1fr; }
              [data-column] { grid-column: 1; }
              #card-preflight { grid-row: auto; }
              #tiles { grid-template-columns: 1fr; }
            }
            """;

    /** The pages the sidebar leads to, by path and name. */
    private static final List<String[]> NAVIGATION = List.of(new String[] {"/", "Launch"},
            new String[] {"/invocation", "Invocation"}, new String[] {"/progress", "Progress"},
            new String[] {"/next", "Next"},
            new String[] {"/labels", "Relevance"}, new String[] {"/code-sets", "Code sets"},
            new String[] {"/profile", "Profile"});

    private ControlPanelPage() {
    }

    /**
     * The whole page, carrying the start's mark the server gave the exchange, the control panel's one
     * script by its address, and, where it asks to be loaded again, the script's refresh and the browser's own inside
     * {@code <noscript>} (ADR-325 §4.1, §5); a sidebar of the six pages and a top row holding {@code title} (ADR-331 §3).
     *
     * @param exchange the request the page answers, which the server marked with the start's mark
     * @param refreshSeconds how often the page asks to be loaded again, none where it is not above zero
     * @param body the page's content, already escaped
     */
    static String render(HttpExchange exchange, String title, int refreshSeconds, String body) {
        return render(exchange, title, refreshSeconds, "", "", false, body);
    }

    /**
     * The whole page as {@link #render(HttpExchange, String, int, String)} writes it, with {@code before} written
     * before the top row, {@code actions} in it beside the title, and the content in two columns where {@code columns}
     * is set: the launch page's form wraps its top row so that Run, Label and Check stand in it (ADR-331 §3).
     */
    static String render(HttpExchange exchange, String title, int refreshSeconds, String before, String actions,
            boolean columns, String body) {
        String refresh = refreshSeconds > 0
                ? "<noscript><meta http-equiv=\"refresh\" content=\"" + refreshSeconds + "\"></noscript>\n" : "";
        String marks = " data-start=\"" + escape(attribute(exchange, ControlPanelServer.MARK_ATTRIBUTE)) + "\""
                + (refreshSeconds > 0 ? " data-refresh-seconds=\"" + refreshSeconds + "\"" : "");
        String here = exchange.getRequestURI().getPath();
        String navigation = NAVIGATION.stream()
                .map(entry -> "<a href=\"" + entry[0] + "\"" + (entry[0].equals(here) ? " aria-current=\"page\"" : "")
                        + ">" + entry[1] + "</a>")
                .collect(Collectors.joining("\n"));
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" + refresh
                + "<script src=\"" + SCRIPT_PATH + "\" defer></script>\n"
                + "<title>" + escape(title + " · Vespera") + "</title>\n<style>\n" + STYLE + "</style>\n</head>\n<body" + marks
                + ">\n<div id=\"shell\">\n<aside id=\"sidebar\">\n<div class=\"brand\">VESPERA</div>\n"
                + "<nav aria-label=\"Pages\">\n" + navigation + "\n</nav>\n</aside>\n<main"
                + (columns ? " class=\"two-columns\"" : "") + ">\n" + before + "<header id=\"top-row\"><h1>"
                + escape(title) + "</h1>" + (actions.isEmpty() ? "" : "<div class=\"actions\">" + actions + "</div>")
                + "</header>\n" + body + "\n</main>\n</div>\n</body>\n</html>\n";
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

    /**
     * A text control, its label, its hint and its example (ADR-331 §8), as the builder is told: named {@code name},
     * with the id {@code f-<name>} unless another is given.
     */
    static final class Field {
        private final String name;
        private final String label;
        private String id;
        private String value = "";
        private boolean required;
        private String type = "text";
        private String example = "";
        private String inUse = "";
        private String hintId = "";
        private String hint = "";
        private String list = "";
        private String browse = "";
        private String after = "";

        private Field(String name, String label) {
            this.name = name;
            this.label = label;
            this.id = "f-" + name;
        }

        static Field of(String name, String label) {
            return new Field(name, label);
        }

        Field id(String id) {
            this.id = id;
            return this;
        }

        Field value(String value) {
            this.value = value;
            return this;
        }

        Field required() {
            this.required = true;
            return this;
        }

        /** {@code number} for a number, drawn with any step and no least or most (ADR-331 §7). */
        Field type(String type) {
            this.type = type;
            return this;
        }

        /** The example shown in the empty control, after {@link #EXAMPLE}; none where it is empty. */
        Field example(String example) {
            this.example = example;
            return this;
        }

        /** The control is described by the element {@code hintId}; {@code hint} is written there, or not at all where it is empty and the element is another's. */
        Field hint(String hintId, String hint) {
            this.hintId = hintId;
            this.hint = hint;
            return this;
        }

        /** The value in use shown in the empty control, after {@link #IN_USE}, in place of an example (ADR-334 §1, §2). */
        Field inUse(String inUse) {
            this.inUse = inUse;
            return this;
        }

        /** The id of the {@code <datalist>} the control offers. */
        Field list(String list) {
            this.list = list;
            return this;
        }

        /** A Browse button beside the control, choosing a folder for the field {@code forName} (ADR-331 §5). */
        Field browse(String forName) {
            this.browse = forName;
            return this;
        }

        /** Markup written under the hint, the folder status's. */
        Field after(String after) {
            this.after = after;
            return this;
        }

        String html() {
            StringBuilder html = new StringBuilder("<div class=\"field\">\n<label for=\"").append(escape(id))
                    .append("\">").append(escape(label)).append("</label>\n<div class=\"control\"><input type=\"")
                    .append(type).append("\" id=\"").append(escape(id)).append("\" name=\"").append(escape(name))
                    .append("\" value=\"").append(escape(value)).append('"');
            if (type.equals("number")) {
                html.append(" step=\"any\"");
            }
            if (!inUse.isEmpty()) {
                html.append(" placeholder=\"").append(escape(IN_USE + inUse)).append('"');
            } else if (!example.isEmpty()) {
                html.append(" placeholder=\"").append(escape(EXAMPLE + example)).append('"');
            }
            if (!list.isEmpty()) {
                html.append(" list=\"").append(escape(list)).append('"');
            }
            if (!hintId.isEmpty()) {
                html.append(" aria-describedby=\"").append(escape(hintId)).append('"');
            }
            html.append(required ? " required>" : ">");
            if (!browse.isEmpty()) {
                html.append(browseButton(browse));
            }
            html.append("</div>\n");
            if (!hintId.isEmpty() && !hint.isEmpty()) {
                html.append("<p class=\"hint\" id=\"").append(escape(hintId)).append("\">").append(escape(hint))
                        .append("</p>\n");
            }
            return html.append(after).append("</div>\n").toString();
        }
    }

    /** A submit button named {@code browse-<forName>} that posts its form without asking its required fields (ADR-331 §5). */
    static String browseButton(String forName) {
        String name = "browse-" + forName;
        return "<button type=\"submit\" class=\"browse\" name=\"" + escape(name) + "\" value=\"" + escape(name)
                + "\" formnovalidate>Browse</button>";
    }

    /** A select named {@code name} offering {@code options}, {@code selected} chosen where it is among them. */
    static String choice(String name, String id, String label, List<String> options, String selected, String hintId,
            String hint) {
        StringBuilder html = new StringBuilder("<div class=\"field\">\n<label for=\"").append(escape(id)).append("\">")
                .append(escape(label)).append("</label>\n<div class=\"control\"><select id=\"").append(escape(id))
                .append("\" name=\"").append(escape(name)).append("\" aria-describedby=\"").append(escape(hintId))
                .append("\">");
        for (String option : options) {
            html.append("<option value=\"").append(escape(option)).append('"')
                    .append(option.equals(selected) ? " selected" : "").append('>')
                    .append(escape(option.isEmpty() ? "not set" : option)).append("</option>");
        }
        return html.append("</select></div>\n<p class=\"hint\" id=\"").append(escape(hintId)).append("\">")
                .append(escape(hint)).append("</p>\n</div>\n").toString();
    }

    /** The provenance control named {@code name}: required, with the reason's example and hint (ADR-331 §8). */
    static String provenance(String name, String id, String label, String value, String hintId) {
        return Field.of(name, label).id(id).value(value).required().example(REASON_EXAMPLE)
                .hint(hintId, REASON_HINT).html();
    }

    /** What a key's page says it is read from: its hint with a capital and a stop (ADR-331 §8). */
    static String hintOf(ProfileKey key) {
        String hint = key.hint();
        return hint.substring(0, 1).toUpperCase(Locale.ROOT) + hint.substring(1) + ".";
    }

    /** The two languages the deliverable is written in (ADR-331 §7). */
    private static final List<String> LANGUAGES = List.of("en", "it");

    /**
     * A valid floating-point number of HTML, which a {@code number} control keeps and any other it blanks (ADR-330 §3):
     * an optional minus, digits with an optional fraction or a fraction alone, and an optional exponent.
     */
    private static final Pattern A_NUMBER_OF_HTML =
            Pattern.compile("-?(\\d+(\\.\\d+)?|\\.\\d+)([eE][-+]?\\d+)?");

    /**
     * The control of {@code key} named {@code name}, the same on every page (ADR-331 §7): a select for the
     * deliverable's language, a number for a number with any step and no bounds, a text field offering the models or
     * {@code auto} where the key takes them, and a text field otherwise; each with its example and its hint
     * {@code hint-<key>}.
     *
     * @param browse the field a Browse button beside it chooses a folder for, none where it is empty
     * @param after markup written under the hint
     */
    static String keyControl(ProfileKey key, String name, String id, String label, String value, String browse,
            String after) {
        return keyControl(key, name, id, label, value, browse, after, false);
    }

    /**
     * As {@link #keyControl(ProfileKey, String, String, String, String, String, String)}, with the deliverable's language
     * offering a first choice of none, {@code not set}, where {@code withNone} is set: the profile page's, so that a save
     * of other values leaves an unset language unset (ADR-334 §7).
     */
    static String keyControl(ProfileKey key, String name, String id, String label, String value, String browse,
            String after, boolean withNone) {
        String hintId = "hint-" + key.key();
        if (key == ProfileKey.DELIVERABLE_LANGUAGE) {
            List<String> options = value.isEmpty() || LANGUAGES.contains(value) ? LANGUAGES
                    : Stream.concat(LANGUAGES.stream(), Stream.of(value)).toList();
            if (withNone) {
                options = Stream.concat(Stream.of(""), options.stream()).toList();
            }
            return choice(name, id, label, options, value, hintId, hintOf(key));
        }
        Field field = Field.of(name, label).id(id).value(value).example(key.example()).hint(hintId, hintOf(key));
        if (key.kind() == ProfileKey.Kind.NUMBER && (value.isEmpty() || A_NUMBER_OF_HTML.matcher(value).matches())) {
            field.type("number");
        }
        if (key == ProfileKey.EMBEDDING_MODEL || key == ProfileKey.GENERATION_MODEL) {
            field.list("models");
        }
        if (key == ProfileKey.ARRANGEMENT_APPROVED) {
            field.list("approvals");
        }
        if (!browse.isEmpty()) {
            field.browse(browse);
        }
        return field.after(after).html();
    }

    /** A {@code <datalist>} {@code id} offering {@code values} as they come. */
    static String datalist(String id, List<String> values) {
        StringBuilder html = new StringBuilder("<datalist id=\"").append(escape(id)).append("\">");
        for (String value : values) {
            html.append("<option value=\"").append(escape(value)).append("\">");
        }
        return html.append("</datalist>\n").toString();
    }

    /** The {@code <datalist>}s the keys of {@code keys} name: the models Ollama reported and {@code auto}. */
    static String listsFor(List<ProfileKey> keys, List<String> models) {
        StringBuilder html = new StringBuilder();
        if (keys.contains(ProfileKey.EMBEDDING_MODEL) || keys.contains(ProfileKey.GENERATION_MODEL)) {
            html.append(datalist("models", models));
        }
        if (keys.contains(ProfileKey.ARRANGEMENT_APPROVED)) {
            html.append(datalist("approvals", List.of("auto")));
        }
        return html.toString();
    }

    /** The two Docling images this build knows, each by its label (ADR-331 §7). */
    static String doclingImages() {
        return "<datalist id=\"docling-images\"><option label=\"GPU\" value=\"" + escape(DOCLING_IMAGE_GPU)
                + "\"><option label=\"CPU\" value=\"" + escape(DOCLING_IMAGE_CPU) + "\"></datalist>\n";
    }

    /** A submit button named {@code name}. */
    static String button(String name, String caption, boolean disabled) {
        return "<button type=\"submit\" name=\"" + escape(name) + "\" value=\"" + escape(name) + "\""
                + (disabled ? " disabled" : "") + ">" + escape(caption) + "</button>";
    }

    /** The state a sidecar check shows: green where it holds, amber where it does not, neither where nothing was checked. */
    static String stateOf(SidecarChecks.Result result) {
        return switch (result) {
            case MATCHES, SERVES_ALL -> "ok";
            case ANOTHER_IMAGE, NOT_ANSWERING, MISSING -> "warn";
            case NOT_CHECKED -> "idle";
        };
    }

    /** The state a sidecar check shows in the preflight: green where it holds, amber otherwise, nothing named included. */
    static String stateOfCheck(SidecarChecks.Result result) {
        return stateOf(result).equals("ok") ? "ok" : "warn";
    }

    /** The words of a sidecar check. */
    static String describe(SidecarChecks.Result result) {
        return switch (result) {
            case NOT_CHECKED -> "not checked";
            case MATCHES -> "runs the image named";
            case ANOTHER_IMAGE -> "runs another image";
            case NOT_ANSWERING -> "not answering";
            case SERVES_ALL -> "serves every model named";
            case MISSING -> "does not serve a model named";
        };
    }

    /** The state the invocation shows for how the child is: none, running, ended, failed-or-refused, defect, stopped. */
    static String stateOfInvocation(String how) {
        return switch (how) {
            case "ended" -> "ok";
            case "running", "stopped" -> "warn";
            case "failed-or-refused", "defect" -> "bad";
            default -> "idle";
        };
    }

    /**
     * The four tiles of ADR-331 §3, in order: the converter's check, the model service's, the free space of the working
     * directory's drive and how the child is.
     *
     * @param checks the sidecars' last answers
     * @param freeSpace the free space in words, or why there is none
     * @param how the child as {@code data-ending} has it
     * @param words the child in words
     */
    static String tiles(SidecarChecks.Checks checks, String freeSpace, String how, String words) {
        return "<div id=\"tiles\">\n"
                + tile("docling", stateOf(checks.docling().result()), "Docling", "docling-check",
                        checks.docling().result().attribute(), describe(checks.docling().result()))
                + tile("ollama", stateOf(checks.ollama().result()), "Ollama", "ollama-check",
                        checks.ollama().result().attribute(), describe(checks.ollama().result()))
                + "<div class=\"tile\" data-tile=\"free-space\" data-state=\"idle\"><span class=\"tile-name\">Free"
                + " space</span><span class=\"tile-value\">" + escape(freeSpace) + "</span></div>\n"
                + "<div class=\"tile\" data-tile=\"invocation\" data-state=\"" + stateOfInvocation(how)
                + "\" data-ending=\"" + escape(how) + "\"><span class=\"tile-name\">Invocation</span>"
                + "<span class=\"tile-value\">" + escape(how.equals("none") ? "none yet" : words)
                + "</span></div>\n</div>\n";
    }

    private static String tile(String tile, String state, String name, String checkId, String check, String words) {
        return "<div class=\"tile\" data-tile=\"" + tile + "\" data-state=\"" + state + "\""
                + "><span class=\"tile-name\">" + name + "</span><span class=\"tile-value\" id=\"" + checkId
                + "\" data-check=\"" + check + "\">" + escape(words) + "</span></div>\n";
    }

    /**
     * {@code cost-<key>}: the words of {@link #cost}, carrying {@code data-sample-drawn-again}, {@code true} or
     * {@code false}, on every page that shows a key's cost (ADR-330 §9).
     */
    static String costElement(ProfileKey key) {
        return "<p id=\"cost-" + escape(key.key()) + "\" data-sample-drawn-again=\"" + key.cost().sampleDrawnAgain()
                + "\">" + escape(cost(key)) + "</p>";
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
        if (cost.sampleDrawnAgain()) {
            words.append(" A sample of questions being answered is drawn again: other documents are asked about, and the"
                    + " label file is written over.");
        }
        return words.toString();
    }
}
