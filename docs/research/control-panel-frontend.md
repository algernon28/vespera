# Is a JS/TS front-end worth it for the control panel?

Research record for [#554](https://github.com/algernon28/vespera/issues/554), a child of map [#551](https://github.com/algernon28/vespera/issues/551). Gathered 2026-10-18. It records facts and one recommendation, and decides nothing. Each number is either **measured** (fetched and counted on 2026-10-18, with the method given) or **estimate** (marked as such).

## Recommendation

**No JS/TS front-end.** Build options A and B as plain server-written HTML forms on the JDK's `com.sun.net.httpserver` (G3-lite), hand-assembled the way `ReportPage` already assembles pages. The panel then adds no Maven dependency, no npm package and no Node to the build. It meets a strict CSP with no `'unsafe-*'` source, and needs about 1,100–1,700 lines of Java for A+B (estimate).

- **Vite and Angular.** Both put Node and 44–411 npm packages in the path of the shipped build, and both release weekly to monthly. They also add an estimated 60–110% more hand-written code (a JSON API plus a client that mirrors it), for one operator on loopback.
- **A desktop window.** Swing cannot draw the progress page, so the browser stays in the flow anyway. JavaFX WebView can draw it, but costs 36–48 MB of native jar per platform, and its JUnit 5 harness (TestFX 4.0.18) has had no release since 2024-02.
- **Keep one fallback.** If B's label form or rename list later needs updates in place, vendor **htmx 2.0.11**: one file of 52 KB (16.9 KB gzipped), no dependencies, and HtmlUnit tests against it. With `allowEval=false` and `includeIndicatorStyles=false` it keeps the strict CSP. It is not needed for A.
- **Testing.** Start with `java.net.http.HttpClient` + AssertJ under JUnit 5, wrapped in `Allure.step`, which adds no dependency. Add HtmlUnit 5.6.0 (pure Java, 14 Maven graph nodes, no browser download) only where a test must click.

## Fixed points read from this repo

| Fact | Where |
|---|---|
| Java 26, Spring Boot 4.1.1 | `pom.xml:30`, `pom.xml:8` |
| The pages are hand-assembled. ADR-046 forbids a template engine; one module may own the hand-assembly. | `docs/adr/0130-…md:4`, `:21` |
| `ReportPage` (283 lines) owns the skeleton, styles and escaping. It is package-private in `pipeline`. | `src/main/java/io/algernon/vespera/pipeline/ReportPage.java` |
| The progress page already reloads with a meta refresh and runs one fixed inline vanilla script, "the same text always" (so a CSP hash fits). | `ProgressPage.java:83-88`, `ReportPage.java:141-155` |
| The jar already opens the progress page in the OS browser (`xdg-open` / `start`). `java.awt.Desktop` is off because Spring Boot sets `java.awt.headless=true`. | `SystemPageOpener.java:17-42`, `docs/adr/0266-…md:16`, `:109` |
| The profile has 13 keys, and `keysAsWritten()` iterates them generically, which suits B's secondary panel. | `Profile.java:109-122`, `:152` |
| `NextAction` (672 lines) already computes what the next invocation wants. | `pipeline/NextAction.java` |
| **Node already runs at build time, for the test report only.** allure-maven 3.0.3 with generator 3.16.0 provisions a private Node and the npm `allure` package (27 direct deps) during `verify`. That ships nothing, and the NTFS job runs only `test`. | `pom.xml:60-62`, `:330-365`; [allure-maven README](https://github.com/allure-framework/allure-maven) |
| CI runs `verify` on ubuntu-latest and `test` on windows-latest. | `.github/workflows/build.yml:36`, `:67`, `:89`, `:114` |

## Comparison

**Lines of code** cover A+B hand-written code (estimates). They include about 350–500 lines of shared core that every option needs: starting the child `ProcessBuilder`, Stop through `Process.destroy`, the last line, the remembered values and the one-at-a-time guard. Lockfiles and generated files are excluded.

**Packages** are deps.dev resolved graphs (measured). They include optional native binaries for every platform, so a single-OS install is smaller.

**Bundle sizes** are measured on the published file, raw and gzip level 9. Bundle size hardly matters on loopback. It stands in for the volume of code that has to be audited.

| Option | Lines of code for A+B (estimate) | Build chain / Node at build | Bundle (measured) | Packages (measured) | Testability | CSP fit | Upgrade cadence (stable releases, 12 months to 2026-10-18) |
|---|---|---|---|---|---|---|---|
| **1. Plain forms on `com.sun.net.httpserver`** | 1,100–1,700 Java, 0 new JS | None. `jdk.httpserver` is a JDK module. | 0 KB new JS | 0 Maven, 0 npm | `HttpClient` + AssertJ (0 deps). HtmlUnit 5.6.0 for clicks (14 nodes, Java 17+). | **Best.** `default-src 'none'; style-src 'self'; script-src 'sha256-…'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'` | Moves with the JDK only |
| **2a. Vendored htmx** | 1,100–1,700 Java + ~0–50 JS | None. One file in resources, upgraded by hand. | 2.0.11: 52.2 KB / 16.9 KB gz. 4.0.0: 36.7 / 13.1. | 1 npm, 0 deps | HtmlUnit names htmx among its tested libraries | Good with `allowEval=false` (loses `hx-on`, event filters and `js:` values) and `includeIndicatorStyles=false` | 5. 4.0.0 shipped 2026-08-28 on `next`, and becomes `latest` "at some point in 2027". |
| **2b. Vendored Alpine** | 900–1,400 Java (JSON) + 300–600 HTML/JS | None | 3.17.4: 55.9 / 19.9. The CSP build is 72.2 / 23.9. | 3 nodes | HtmlUnit not verified with Alpine. Playwright works. | Default build needs `'unsafe-eval'`. The CSP build drops arrow functions, template literals and property assignment. | 21 |
| **2c. Vendored Preact + htm** | 900–1,400 Java + 600–1,000 JS | None | `htm/preact/standalone` 3.1.1: 13.2 / 5.3 | 1 + 1 | HtmlUnit not verified. Playwright works. | Expected good (no eval), not verified | Preact 18 (major 11 on 2026-09-30). **htm 0, last published 2022-10-11**, and its standalone bundles Preact 10. |
| **2d. Vendored Lit** | 900–1,400 Java + 600–1,000 JS | None. The single-file bundle comes from `lit/dist` on GitHub, not npm. | `lit-core.min.js`: 15.7 / 6.1. `lit-all`: 29.4 / 10.2. | 6 nodes (npm `lit`) | HtmlUnit not verified. Playwright works. | Expected good, not verified | 2 (3.3.3). No major since 3.0. |
| **3. Vite SPA** (React / Vue / Svelte, TS) through `frontend-maven-plugin` 2.0.2 | 900–1,400 Java (JSON) + 800–1,300 TS + ~100 config ≈ 1,800–2,800 | **Node required:** Vite 8.3.4 needs Node `^20.19 \|\| >=22.12`. The plugin downloads Node from nodejs.org/dist (or a mirror) and runs `npm install`, which breaks `./mvnw -o` and air-gapped builds without a mirror. It runs in `package` on both CI OSes. | React 19.3.1 as published: react 18.0 / 4.6, react-dom-client 625 / 110.5 (unminified; estimate ~60 KB gz after Vite). Vue runtime prod 113.3 / 42.5. Svelte estimate 10–25 KB gz. | Minimal set: React 44, Vue 60, Svelte 63. create-vite 9.2.1 templates: 90 / 93 / 94. | Playwright only in practice | Good: `script-src 'self'`. Small assets become `data:` URIs unless `assetsInlineLimit: 0`. A nonce is optional. | Vite 55 (a major yearly: 5, 6, 7, 8 between 2023-11 and 2026-03), React 29, Vue 22, Svelte 105, TS 3 (6.0 and 7.0 in 2026) |
| **4. Angular** 22.2.3 | 900–1,400 Java + 1,200–2,000 TS/HTML/CSS + ~150 config ≈ 2,300–3,500 | Node `^22.22.3 \|\| ^24.15 \|\| >=26`, by the same plugin route | Not measured. Estimate 50–80 KB gz. | 364 packages for the `ng new` deps + `@angular/build`; 411 with vitest + jsdom | Playwright, or vitest + jsdom (more Node) | Weak. It needs a per-request nonce for runtime styles, or `style-src 'unsafe-inline'`; `autoCsp` covers scripts only. | 102 releases. Majors every 6 months until v22, every 12 months from v22, each supported 24 months. |
| **5a. Swing + FlatLaf** 3.7.2 | 1,500–2,500 Java + core | None. FlatLaf is one 1.0 MB jar with no deps. | n/a | 1 Maven | **No maintained harness.** AssertJ-Swing's last release is 3.17.1 (2020-09). Test the model, not the UI. | n/a (no socket) | FlatLaf: a few releases a year |
| **5b. JavaFX** 27 + WebView | 1,300–2,200 Java + core | Platform-classified jars. `javafx-web-27` alone is 36.1 MB (win) and 47.6 MB (linux). Needs JDK 25+. | n/a | OpenJFX modules per OS | TestFX 4.0.18 (2024-02), "legacy support" only. Monocle's newest is 21.0.2, so headless Linux CI needs xvfb. | n/a. WebView can draw the progress page. | GA every ~6 months: 25, 26, 27 |

### Bound by the record, or not

Whether the record binds the panel is decided in "Where the control panel's code lives and how it is built". Each option is read both ways below.

| Option | If the jar's record binds the panel (ADR-001/214 fixed stack; ADR-046/216 pom; no template engine) | If it does not (a separate module or build) |
|---|---|---|
| 1. Plain forms | **Fits as is.** Java only, no pom line, hand-assembled HTML. `ReportPage` is reusable if the panel sits in `pipeline`; elsewhere it must be made public or copied. | Fits; nothing changes. |
| 2. Vendored script | No pom line, but a shipped third-party file needs its own record under ADR-216 ("nothing ships that no decision requires"). Pin the file's SHA-256 in that record. | One file to pin and checksum. |
| 3. Vite SPA | Needs a recorded decision for `frontend-maven-plugin` and a Node build step in the pom, and a TS client sits outside "Java, Spring Boot, Spring AI". | Node and npm move into a separate build, but the one maintainer still owns the lockfile and its weekly churn. |
| 4. Angular | Same as Vite, with the largest graph. | Same as Vite, with the largest graph. |
| 5a. Swing | Java, so within ADR-001. FlatLaf needs a record. Spring Boot's `headless=true` (ADR-266) means the panel must not run inside a Boot context. | Fits. |
| 5b. JavaFX | Within "Java", but OpenJFX is not in the JDK and needs a record plus per-OS artifacts. | Fits, at 36–48 MB per OS. |

## Notes behind the table

- **Security headers for every loopback option.** Options 1–4 need these; the desktop options need none of them.
  - Bind to `InetAddress.getLoopbackAddress()`.
  - Refuse any `Host` header other than `127.0.0.1:<port>` / `localhost:<port>`, which stops DNS rebinding.
  - Require a matching `Origin` plus a per-launch token on each POST, which stops cross-site requests from pages the operator has open.
  - Send `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` and the CSP above.
  - `HttpExchange.getResponseHeaders()` sets any header, so option 1 needs no library for this. These rules are an engineering recommendation, not a sourced fact.
- **`form-action` must be stated.** `default-src` does not fall back to it (MDN). Without it, a form may post anywhere.
- **Stop and Run never need the client to hold state.** A POST then a redirect (303) back to the form is enough for A. The progress page already refreshes itself (ADR-265). B's 60-label form is one `<form>` of 60 `<select>`/radio groups: a large page, but a single POST.
- **`jdk.httpserver` is documented for "simple usages like local testing, development, and debugging"**, not high load. A one-operator loopback panel is that case.
- **Playwright Java brings Node anyway.** Its `driver-bundle-1.63.0.jar` is 203.8 MB, and the docs describe pointing it at a pre-installed Node (`PLAYWRIGHT_NODEJS_PATH`). Browsers download separately to `%LOCALAPPDATA%\ms-playwright`, unless it is pointed at the installed Edge with `setChannel("msedge")`. Choosing a JS front-end that only Playwright can test therefore adds Node to the test path even when the build has none.

## Method

- **npm versions, engines and release counts:** `registry.npmjs.org` metadata (`npm view`, and `time` for stable `x.y.z` versions published between 2025-10-18 and 2026-10-18). Nothing was installed.
- **Dependency graphs:** `api.deps.dev/v3/systems/{npm,maven}/packages/…:dependencies`, with node counts for the latest version, and unions for multi-package sets.
- **Bundle sizes:** each published file was fetched into memory from the npm mirror at cdn.jsdelivr.net and gzipped with Node `zlib` at level 9. Nothing was written or executed. The Lit bundles come from `cdn.jsdelivr.net/gh/lit/dist@3`.
- **Maven versions and jar sizes:** `repo1.maven.org` `maven-metadata.xml` and directory listings.
- **Lines-of-code figures:** estimated from the screens in #554, scaled against `ReportPage` (283 lines) and `ProgressPage` (1,302 lines). Not built.

## Sources (accessed 2026-10-18)

- htmx docs (security, `allowEval`, `inlineStyleNonce`, `selfRequestsOnly`): https://htmx.org/docs/
- htmx home (size "~16k min.gz'd", htmx 4 release and `latest` tag in 2027): https://htmx.org/
- Alpine CSP build: https://alpinejs.dev/advanced/csp
- Lit, pre-built bundles: https://lit.dev/docs/getting-started/
- Preact no-build workflows: https://preactjs.com/guide/v11/no-build-workflows
- Vite CSP (`html.cspNonce`, `data:` inlining): https://vite.dev/guide/features
- Vite release policy: https://vite.dev/releases
- React versioning policy: https://react.dev/community/versioning-policy
- Angular release schedule: https://angular.dev/reference/releases
- Angular security, CSP (`ngCspNonce`, `autoCsp`): https://angular.dev/best-practices/security
- frontend-maven-plugin README: https://github.com/eirslett/frontend-maven-plugin
- frontend-maven-plugin versions: https://repo1.maven.org/maven2/com/github/eirslett/frontend-maven-plugin/maven-metadata.xml
- allure-maven README (Allure 3 provisions a private Node): https://github.com/allure-framework/allure-maven
- `jdk.httpserver` module (JDK 26): https://docs.oracle.com/en/java/javase/26/docs/api/jdk.httpserver/module-summary.html
- MDN, CSP `form-action`: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/form-action
- Playwright Java intro: https://playwright.dev/java/docs/intro
- Playwright Java browsers: https://playwright.dev/java/docs/browsers
- Playwright `driver-bundle` 1.63.0 listing: https://repo1.maven.org/maven2/com/microsoft/playwright/driver-bundle/1.63.0/
- HtmlUnit: https://www.htmlunit.org/
- TestFX: https://github.com/TestFX/TestFX
- AssertJ-Swing versions: https://repo1.maven.org/maven2/org/assertj/assertj-swing-junit/maven-metadata.xml
- FlatLaf: https://www.formdev.com/flatlaf/
- OpenJFX: https://openjfx.io/
- OpenJFX 27 release notes ("requires JDK 25 or later"): https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-27.md
- `javafx-web` 27 listing: https://repo1.maven.org/maven2/org/openjfx/javafx-web/27/
- npm registry metadata: https://registry.npmjs.org/
- deps.dev API: https://docs.deps.dev/api/v3/
