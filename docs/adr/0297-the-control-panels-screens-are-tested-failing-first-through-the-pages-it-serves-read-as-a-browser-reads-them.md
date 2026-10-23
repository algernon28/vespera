# ADR-297 — The control panel's screens are tested failing-first through the pages it serves, read as a browser reads them

> **Partly amended — see [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md).** Of §2: a form is submitted by pressing one of its submit buttons by name, only that button is sent, `submit` sends none, and a button the form does not hold, or holds disabled, fails the test and sends nothing. The rest of this record stands.

> **Partly amended — see [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md).** Of §2 and its Keeps of ADR-291: no token is exchanged and no cookie set, so `PanelBrowser` holds none, and a screen test passes the server's checks of `Host`, `Sec-Fetch-Site` and `Origin`. Of §7: `PanelBrowserTest` holds the landing on the root at the printed address, with no redirect, and the next page answered. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel's jar names `java.base` and `jdk.httpserver` alone; the one dependency added here is at test scope and is in neither jar (§3).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md): every screen test reaches its page through the server's checks of `Host`, the cookie and `Origin`, as a browser does, and none goes around them (§2).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): the dependency is this record's (§3).
- **Keeps**: [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md): each browser action is one report step and each assertion one claim (§5).
- **Settles**: [#568](https://github.com/algernon28/vespera/issues/568). §1 to §7 are decided under the operator's delegation of 2026-10-19.

## Context

The control panel serves plain HTML forms it writes itself, on the JDK's `HttpServer`, with no script of its own; htmx would be vendored only if a screen later needs a part of a page replaced in place. Its server answers only a request addressed to `127.0.0.1:<port>` or `localhost:<port>`, holding the cookie its start's token is exchanged for by a `303`, and a `POST` only with its own `Origin`, under `Content-Security-Policy: frame-ancestors 'none'; form-action 'self'; base-uri 'none'` (ADR-291).

Every decision the panel's screens make is to have a failing-first test. CI runs the unit tests on Linux and on Windows NTFS, and the harness has to run in both and in a container with no display.

Four harnesses were weighed:

| Harness | What it drives | What it costs |
| --- | --- | --- |
| Playwright for Java | a real Chromium, Firefox or WebKit | a browser downloaded for each platform and cached on each CI job, and a driver process beside the JVM |
| HtmlUnit | a simulated browser in the JVM, with a JavaScript engine | a JavaScript engine, for pages that carry no script, and its own HTML, CSS and XPath parsers, each a jar at test scope |
| TestFX | a JavaFX scene | the panel has no JavaFX scene |
| The served HTML, parsed | the pages the server answers, through the JDK's `HttpClient` | one jar at test scope, jsoup, which brings nothing with it |

## Decision

### 1. A screen is tested through the pages the panel serves

A screen's test drives the control panel's own server, started with the screen's pages, through `PanelBrowser`, a class of the tests in `io.algernon.controlpanel`. `PanelBrowser` is the JDK's `HttpClient` with a cookie store, following redirects as a browser does, and jsoup parsing each page it lands on into a document.

No real browser is driven. A page with no script does nothing in a browser that its HTML and the server's answers do not say, so the HTML and the answers are what the test reads.

### 2. It does what a browser does with a form

`PanelBrowser`:

- opens the address the panel prints, and keeps the cookie the token is exchanged for;
- goes to a path of the panel's origin, as a link does;
- follows a `303` with a `GET`;
- submits a form by its `name`, each field given set by its control's `name`, every other control sent as the page wrote it, the fields in the order jsoup reads them, which is a browser's, encoded as UTF-8;
- sends a `POST` with `Origin` set to the page's origin and `Content-Type: application/x-www-form-urlencoded`, and a `GET` form's fields in the query;
- refuses a form whose action leaves the panel's origin, failing the test, as `form-action 'self'` has a browser refuse to send it.

A form the page does not hold, more than one form of the name, or a field the form has no control for fails the test with a line naming it, and sends nothing.

The server's checks are never bypassed: a test that wants a refusal writes its request with `RawHttp`, as ADR-291's tests do.

### 3. One dependency, at test scope

The pom carries `org.jsoup:jsoup` at test scope, version 1.23.2. jsoup's one compile dependency, re2j, is optional, and jspecify is provided, so it brings no other jar. Neither jar the build packages carries it, and the control panel's jar stays on `java.base` and `jdk.httpserver`.

### 4. Every screen test is a unit test

A screen test starts the server on a port the system picks and needs neither Docker, a browser, a display nor `verify`, so it is named `*Test` and runs under surefire on both CI jobs.

### 5. How a screen test is written

- A screen's test class is named for the screen, `<Screen>ScreenTest`, with `@Epic("Operation")` and `@Feature("The control panel")`, as ADR-291's tests are.
- A form and a control are found by `name`, and a page is gone to by its path; text a test reads is found by `id`. A test finds nothing by its position on the page, its CSS class or its wording, so a screen's layout and words can change without a test changing.
- Each action of `PanelBrowser` is one report step, named in plain words: opening the printed address, going to a path, submitting a form by its name. Each assertion is a `TestSteps.claim`, so a test reads in the report as the actions taken and the claims made, in order.
- A screen's test is written before the screen, against the path its record names, and fails there, `404` from a server that serves no page at that path, until the screen is built.

### 6. A part of a page replaced in place

If a screen vendors htmx (option B of #554's research), a request htmx makes is a request with the header `HX-Request: true` answered by a fragment of HTML. Its test sends that request through `PanelBrowser` and reads the fragment. Whether a test also drives the script in a browser is decided by the record that vendors htmx.

### 7. What `PanelBrowserTest` holds

`PanelBrowserTest` starts the server with stub pages and holds §2: the landing on the root without the token, the next page answered on the cookie, a form posted from the panel's origin with every field in the page's order, a corpus root in Polish and Chinese and a backslash whole, a `GET` form's query, a `303` followed by a `GET`, the three failures that send nothing, and the form whose action leaves the panel refused.

## Consequences

- No screen can be tested in a way a browser could not use it: a test can only open the printed address, follow, and submit forms the page holds.
- What a browser does that is not in the HTML or the answers is not tested: layout, focus, a form's validation attributes enforced before sending, and whether the CSS shows a control. A screen whose decision rests on one of these says so in its record.
- The browser's own handling of a cookie, a redirect or the policy header is the JDK's and jsoup's reading of the standards, not Chromium's or Firefox's.

## What this does not decide

- The screens, their paths and their words, which are #561's.
- Whether a screen vendors htmx, and whether its script is then driven in a browser (§6).
- Whether the panel's pages are checked for accessibility.
