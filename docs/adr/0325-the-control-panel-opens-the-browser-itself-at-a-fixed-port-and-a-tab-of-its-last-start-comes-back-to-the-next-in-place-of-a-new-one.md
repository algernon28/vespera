# ADR-325 — The control panel opens the browser itself, at a fixed port, and a tab of its last start comes back to the next in place of a new one

> **Partly amended — see [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md).** Of §3: the fourth step's line is `opening the control panel; if nothing appears, go to http://127.0.0.1:<port>/ in your browser`. Of §7: `SystemBrowserOpener.open` starts what `windowCommand` chooses, a Chromium browser's `--app=<address>` where one is found and otherwise `command`'s, which stands, with the same rules for `CI`, for no display and for a failure. Of §8: "by the same commands" holds where no such browser is found. Of its Consequences: a window of its own opens where such a browser is found. The port, the checks, the mark, the one script and the wait of 5 seconds stand, a window running the script as a tab does. The rest of this record stands.

- **Date**: 2026-10-23
- **Status**: accepted on 2026-10-23.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names its own package, `java.base` and `jdk.httpserver` alone. The opener of §7 is `ProcessBuilder`, of `java.base`; `java.desktop` is not added (§7).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2's table of the system's commands for opening an address, its rule that nothing is opened where `CI` is set or, off Windows and macOS, where no display is, its refusals of `java.awt.Desktop` and of `cmd /c start`, and its refusal of a switch that turns opening off, each applied to the control panel's own opener (§7); and its "only `progress.html` is opened, and once", which stays the jar's and does not meet this record (§8).
- **Keeps**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §3's rules for a script, applied to the control panel's one script (§4.2).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §4's check of `Host`, first, and its refusal; §5's check of `Origin` on every `POST` and its three methods; §6, but for `/alive`; §7's four headers; and §8's rule that the server's classes write no file, log nothing, print nothing and start no process: the browser is opened by classes outside the server's (§7, §10).
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §12's refusal of a `GET` or `HEAD` sent `Sec-Fetch-Site: cross-site` or `same-site`, but under `/report/`, checked right after `Host` (§2).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): the pages are tested as a browser reads them, through `PanelBrowser` and `RawHttp`, and the start as the operator starts it; what a browser does with the script that no test can see is said in §9.
- **Keeps**: [ADR-307](0307-the-child-the-control-panel-started-ends-with-it-wherever-its-end-runs-the-jvms-shutdown-hooks.md): `Ctrl+C` still ends the child with the control panel, and a tab that comes back shows the invocation page with no child (§3).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): no dependency and no plugin is added.
- **Amends**: ADR-291 §1, whose port is 0 at every start: it is 24291, or the one `VESPERA_CONTROL_PANEL_PORT` names, and one the system picks where that one is taken or the variable names 0 (§1); §2, whose start draws a token and opens no browser: no token is drawn, the address printed is the root alone, and the control panel opens the browser (§2, §7); §3, the token's exchange for a cookie, which is struck: no cookie is set or read (§2); §4, whose second check, the cookie or the exchange, is struck, so that a request is let in past `Host` and ADR-302 §12's `Sec-Fetch-Site` alone (§2); §5, whose second line of defence, the cookie's `SameSite`, is gone, `Origin` holding a `POST` alone (§2); §6, which gains one path, `/alive` (§4.3); §8, whose server holds no token (§2); its Known limits, of which the token read from memory and the cookie sent to every port of `127.0.0.1` no longer apply, and which gain those below; its Consequences' "another local user cannot get past it without the token" and "A restarted control panel has a new port and a new token"; its alternatives refused "A fixed port, or one the operator configures" and "Opening the browser itself", reversed for the reasons below; its What this does not decide's "The port and the token are never remembered"; and its Tests, whose tests of the token, its exchange and the cookie are gone, the rest named again below where they hold §2 without a cookie. It carries a note at its head.
- **Amends**: ADR-297 §2, whose browser keeps the cookie the token is exchanged for, of which there is none, and its Keeps of ADR-291: a screen test passes the server's checks of `Host`, `Sec-Fetch-Site` and `Origin` (§2); and §7, whose `PanelBrowserTest` holds the landing on the root at the printed address, with no redirect, and the next page answered, with neither a token nor a cookie. It carries a note at its head.
- **Amends**: ADR-302 §1, whose pages carry no script of the control panel's own and whose invocation page refreshes by `<meta http-equiv="refresh">`: every page of the control panel's own carries one script, by its address, and the invocation page's refresh is the script's, the `<meta>` kept inside `<noscript>` for a browser running no script (§4, §5); its "No `GET` of the control panel changes anything", which gains one exception, a tab coming back (§4.4); §3, whose sandbox keeps a report from the control panel's cookie, of which there is none: it keeps a report's script from posting to it, its opaque origin refused by `Origin` (§2); §9, whose attributes a test reads gain `data-start` and `data-refresh-seconds` (§4.1, §5); §11, whose line holds the token and whose start writes it at once: the line is the root alone, and a start at the fixed port first waits for a tab, and writes another line where one comes back (§3); §12, whose cookie is struck, and whose refusal by `Sec-Fetch-Site` stands, checked after `Host` and before everything else (§2); its Keeps of ADR-291 §2, the token drawn at each start, which no longer holds; and its Tests' test that no page holds a `<script>` element, which holds one script by its address instead, and its test of the token's exchange for a `SameSite=Lax` cookie, which is gone. It carries a note at its head.
- **Settles**: that the control panel opens the browser itself, that a tab it opened is reused across its starts and not within one, and that it holds no token, every caller on the machine being one it answers, are the operator's; §1 to §11 are decided under the operator's delegation of 2026-10-23.

## Context

The **control panel** (ADR-285) serves its pages on `127.0.0.1` at a port the system picks at each start, answering only a browser holding that start's token (ADR-291), and prints one address for the operator to open (ADR-302 §11). It opens no browser, and a tab left open from one start is refused by the next, which has another port and another token.

Two things are wanted of it: that it opens the browser on its own address; and that a tab it opened at one start, still open when the operator ends it with `Ctrl+C` and starts it again, is reloaded by the next start, which then opens no other. A second tab within one start is not to be replaced by the first.

**What a tab coming back needs.** A browser keys a page's origin on its scheme, host and port. A tab can be reloaded by the next start only at the same port, and is let in only where that start takes what the tab holds. A token kept across starts for the tab to hold keeps out one kind of caller ADR-291 names, another local user, and is reached by that same user wherever they listen at the fixed port while the control panel is down, since a tab reloading by itself sends the server there what it holds. The control panel is used on the operator's own machine, and another local user is not a caller it is to keep out.

**What the token guards, and what guards the rest.** ADR-291 keeps out three kinds of caller. Another machine is kept out by `127.0.0.1` alone (ADR-291 §1), which holds no token. Another local user is kept out by the token alone. Another website open in the operator's browser is kept out by four checks, of which only the cookie is the token's:

| What another site's page does | What refuses it | Holds a token |
| --- | --- | --- |
| a name of its own rebound to `127.0.0.1`, to read the answers as its own | `Host` (ADR-291 §4) | no |
| a form posted to the control panel: a start, a stop, a write | `Origin` on every `POST` (ADR-291 §5) | no |
| a page of the control panel opened in a window of the operator's, its forms live for a click it tricks out of them | `Sec-Fetch-Site` on every `GET` and `HEAD` but a report's (ADR-302 §12) | no |
| a page or report framed, or read by its script | `frame-ancestors 'none'` (ADR-291 §7), the browser's same-origin policy, and `X-Content-Type-Options: nosniff` against a page loaded as a script or style | no |
| a page loaded as an image or a script, in a browser that sends no `Sec-Fetch-Site` | the cookie, which `SameSite=Lax` keeps from such a request, though it sends it on a window another site opens (ADR-302 §12) | yes |

Without the token, the first four rows stand as they are. The last request is answered without it, and its answer is no more readable than before, and changes nothing, no `GET` of the control panel changing anything (ADR-302 §1). A report under `/report/`, which no `Sec-Fetch-Site` refuses, is answered to a request another site starts: as a window, it is a page of the control panel's origin that site cannot read, and as an image, a script or a style, the browser hands that site nothing of an HTML answer sent with `nosniff`. A `POST` with no `Origin` is refused as before.

**What a browser does with a page's script**, from the specifications and the browsers' published rules, and not measured here:

- A request a page makes to its own origin carries `Sec-Fetch-Site: same-origin`; one another site's page makes carries `cross-site`, or `same-site` from another port of `127.0.0.1`.
- A hidden tab's timers wake at most once a second in Chromium and Firefox. Chromium's heavier throttling, to once a minute for a page hidden five minutes, applies to timers set from inside another timer's callback, chained five deep. A browser may also freeze or discard a hidden tab, which then runs no script.
- A `<meta http-equiv="refresh">` navigates whether or not the server answers; a refresh to a server that is gone leaves the browser's error page in the tab, which runs none of the page's script.

**What was measured.** On Windows 11, OpenJDK 26.0.2.1: an `HttpServer` bound to `127.0.0.1` at a fixed port, answering a client that kept its connection alive, ended by a forced end of its process and, in a second trial, by an orderly one, the client's connection still open each time: a new process bound the same address and port 84 ms and 78 ms after the first had ended. On that machine the system hands out ports from 49152 to 65535 to outgoing connections, and of the eighteen ranges it excludes from binding, all but port 5357 lie inside that range. IANA lists 24250 to 24320 as unassigned. Linux was not measured here; the tests of §9 start the control panel twice at one port on CI's Linux job.

## Decision

### 1. The port

The server binds `127.0.0.1` alone, as ADR-291 §1 has it, at **24291**, unless the environment variable `VESPERA_CONTROL_PANEL_PORT`, in the control panel's own environment, names another:

| `VESPERA_CONTROL_PANEL_PORT` | The port |
| --- | --- |
| absent, or nothing but whitespace | 24291 |
| one to five ASCII digits naming 1 to 65535 | that port |
| one to five ASCII digits naming 0 | one the system picks, as ADR-291 §1 had it |
| anything else | none: one line on standard error, and exit 1 |

The value is read as it is set, with nothing trimmed: a digit string with a space before or after it names no port and is refused, and leading zeros are digits like any other, so `08080` names 8080 and `00` names 0. Six digits or more are refused whatever they name.

The variable is read after the jar beside the control panel's own is found (ADR-288 §4) and before the server is started. A value it refuses starts nothing and writes no address; the line, `<value>` the variable as set:

```
VESPERA_CONTROL_PANEL_PORT is <value>, and the control panel listens only at a port from 0 to 65535
```

**A port that is taken**, whatever refuses the bind, another program, another control panel or the system's exclusion, is left for one the system picks, with one line on standard error:

```
the port <port> is taken, so the control panel listens at a port the system picked, and no tab it opened before can come back to it
```

A start at a port the system picked, for either reason, waits for no tab (§3) and opens a new one.

**Why 24291.** It lies below 32768, where Linux starts handing out ports to outgoing connections, and below 49152, where Windows and macOS do, so the system never gives it to another program's outgoing connection; and in a range IANA leaves unassigned.

### 2. No token

The control panel draws no token, keeps none, and checks none. Of ADR-291:

- **§2's token is not drawn**, and the address the operator opens is the root alone:

  ```
  http://127.0.0.1:<port>/
  ```

- **§3's exchange is struck.** No response carries `Set-Cookie`, no request's `Cookie` is read, and a `token` parameter in a query is not read: a `GET` of a page's path with a query is answered as that path, by its page.
- **§4's second check is struck.** A request is let in by these checks, in this order, each refusing with ADR-291 §4's 403 and its one line, `open the address the control panel printed when it started`, and no page's handler run:
  1. `Host`, exactly as ADR-291 §4 has it;
  2. `Sec-Fetch-Site`, exactly as ADR-302 §12 has it: a `GET` or `HEAD`, to a path that does not begin `/report/`, sent `cross-site` or `same-site`, compared without case, is refused;
  3. for a path other than `/alive` (§4.3), the method, answered 405 as ADR-291 §5 has it, and the `Origin` of a `POST`, refused as there;
  4. the path, answered 404 where no page is at it (ADR-291 §6).
- **§5's second line of defence is gone with the cookie.** A `POST` is let in by its `Origin` alone, as every browser sends one on a `POST`, and a `POST` with none, or with `null`, is refused, as before.
- **§8 holds without a token**: the server's classes write no file, log nothing, print nothing and start no process.

No file is added to ADR-292's folder, and nothing it holds changes.

**What stays closed, and what opens.** Another machine is kept out by `127.0.0.1` alone. Another website in the operator's browser can neither post a form, nor read a page or a report, nor open a page of the control panel's own in a window, by the four rows of the Context that hold no token; these are the least that keeps it so, and none is removed. Another local user, and any program running on the machine, is answered as the operator is: it can read every page and report the control panel serves, text of documents included (ADR-261), post every form, start and stop a child, and write `profile.yaml`, `relevance-labels.yaml` and `code-set-names.yaml` through it. That is accepted: the control panel is used on the operator's own machine, by the operator.

### 3. The start

`ControlPanel.main`, after ADR-288 §4's line naming the jar it will start:

1. reads `VESPERA_CONTROL_PANEL_PORT` and refuses as §1 says;
2. binds the port, or one the system picks (§1), and starts the server;
3. at the fixed port, or the one the variable names, writes

   ```
   the control panel waits up to 5 seconds for a tab it opened before
   ```

   and waits up to **5 seconds** for a tab to come back (§4.4). Where one does, it writes

   ```
   a tab it opened before came back to http://127.0.0.1:<port>/, so the control panel opens no other
   ```

   and opens nothing. Where none does within the 5 seconds, it goes on as in 4;
4. at a port the system picked, at once, and otherwise once the wait has ended with no tab, writes ADR-302 §11's line with the root alone

   ```
   open http://127.0.0.1:<port>/ in your browser
   ```

   and then asks the opener to open that address (§7).

Every line is flushed as it is written. The waiting line names 5 seconds, the bound `main` hands in, whatever bound a test hands in (§10): it is the operator's line, and a test's shorter bound does not change what the operator reads. Then the control panel serves until its process ends, with ADR-307's shutdown hook as before. A tab that comes back after `Ctrl+C` shows the invocation page of a start whose child has not been started, the child of the last having ended with it (ADR-307).

**Every start at a port of its own waits.** No file says whether the last start left a tab open, so a start with no tab to come back to waits its 5 seconds before it opens one.

**The bound, against the poll.** A tab polls one second after its last poll settled (§4.2), each poll given up after a second, and a hidden tab's timer may wake a second late. So a tab that last polled just before the server came up polls again within 2 seconds, and that poll reaches the server over loopback in milliseconds and in at most a second: within 3 seconds of the server coming up. The bound of 5 seconds leaves 2 more for a loaded machine. They are spent in full only where no tab comes back.

### 4. How a tab comes back

#### 4.1 What a page carries

Every page of the control panel's own, written through `ControlPanelPage` (ADR-302 §10), which no report is, carries on its `<body>` `data-start`, the start's **mark**: 16 bytes of `SecureRandom` drawn as the server starts, as 22 characters of base64url without padding, the same on every page of one start and another at the next. It carries no other mark of the start; in its `<head>`, one script element, holding no text:

```html
<script src="/control-panel.js" defer></script>
```

The mark tells one start from the next, and lets no request in.

#### 4.2 The script

`/control-panel.js` is a path the server is started with, answered as every page is, past §2's checks: a `GET` or `HEAD` with `200` and `Content-Type: text/javascript; charset=utf-8`, a `POST` with `405` and `Allow: GET, HEAD`. Its text is one constant, the same at every start, under ADR-269 §3's rules: it holds no data of a start, and reads only the attributes of §4.1 and §5; it writes nothing into the page; it never sets `innerHTML`, `outerHTML` or `insertAdjacentHTML`, and never calls `document.write`, `eval` or `Function`.

It does this:

1. It reads `data-start` and, where the page carries it, `data-refresh-seconds`.
2. One second after the page has loaded, and one second after each poll has settled, never on an interval, it polls: it sends `GET /alive` to its own origin with `cache: 'no-store'` and `redirect: 'error'`, given up after one second. Scheduled from the poll's settling, a timer is never set from inside another timer's callback.
3. On a `200` whose body is a mark, 22 characters of base64url: where it is not the page's `data-start`, the server is a later start, and the script reloads the page; where it is, and the page carries `data-refresh-seconds`, it reloads the page once that many seconds have passed since the page loaded.
4. Any other outcome, a refusal, a network error, a poll given up or a body that is not a mark, changes nothing, and the next poll follows.

So a tab never goes to a server that has not answered a mark, and it reloads onto the next start at its own port without the operator.

#### 4.3 `/alive`

The server answers `/alive` itself, beside the pages it is started with:

- after §2's checks of `Host` and `Sec-Fetch-Site`, each refusing as there;
- a method other than `GET`, `HEAD` included, is answered **405**, with `Allow: GET`: the script sends nothing but a `GET`;
- otherwise **200**, `Content-Type: text/plain; charset=utf-8`, and as its body the start's mark, and no line break. Its query, where it has one, is not read.

ADR-291 §7's four headers are on every answer. Whoever asks `/alive` learns that the control panel runs, its port and its mark, which lets nothing in.

#### 4.4 A tab coming back

A tab has come back once the server lets in, after it started, a `GET` of `/alive`, or a request to a path that does not begin `/report/` let in past all of §2's checks, whatever its answer: a `GET` of a page or of `/control-panel.js`, a `POST` from the control panel's own origin, and a path no page is at, answered 404. A report's tab carries no script of the control panel's (ADR-302 §3), so a request under `/report/` is not one. A request another site starts is refused by `Sec-Fetch-Site` or `Origin` before it is counted, so it is not one either.

A tab's poll of `/alive` is the first request it sends to a new start, and the reload follows it; counting the poll ends the wait as soon as the tab is seen, and telling a page apart from the rest would add a rule and change no outcome.

It is the one effect a `GET` of the control panel has beyond showing a page (ADR-302 §1): it decides whether the start opens a new tab, and changes no page, no file, no value a page shows and no child.

### 5. The invocation page's refresh

While the child the invocation page shows runs, the page carries `data-refresh-seconds="2"` on its `<body>`, and ADR-302 §1's `<meta http-equiv="refresh" content="2">` inside a `<noscript>` in its `<head>`. Once the child has ended, or where none was started, it carries neither. A browser running the script reloads the page only once the server has answered as the same start (§4.2), so a page whose start ends stays as it is, still polling, until the next start reloads it. A browser running no script refreshes by the `<meta>`, as before.

### 6. A second policy on every page of the control panel's own

Every page of §4.1 is answered with a second header beside ADR-291 §7's:

```
Content-Security-Policy: script-src 'self'; connect-src 'self'
```

so the one script runs from the control panel's own origin, no script written into a page runs, and the script connects to no other origin. A report keeps ADR-302 §3's two policies and no third, since its own script is written inside it. The script's answer and `/alive`'s carry ADR-291 §7's four headers alone.

### 7. Opening the browser

The start asks a **`BrowserOpener`**, `void open(URI address) throws IOException`, to open the address it printed, once, after printing it. The shipped one, `SystemBrowserOpener`, starts ADR-266 §2's command for the system, the address as one argument, does not wait for it, and discards its output:

| `os.name` | Command |
| --- | --- |
| starts with `Windows` | `rundll32 url.dll,FileProtocolHandler <address>` |
| starts with `Mac` | `open <address>` |
| any other | `xdg-open <address>` |

It opens nothing, and says nothing, where `CI` is set and not blank, and on a system other than Windows and macOS where neither `DISPLAY` nor `WAYLAND_DISPLAY` is set and not blank. The choice is one function, held without a display or a process:

```java
static Optional<List<String>> command(URI address, String osName, Map<String, String> environment)
```

empty where nothing is opened.

**Opening never stops the control panel.** Whatever `open` throws, an `IOException` or a `RuntimeException`, is caught and written as one line on standard error, the exception's class name alone, as `Class.getName` gives it, its package included, as ADR-266's warning writes it, and never its message; the control panel serves as before, the address already printed:

```
the control panel could not open the browser: <exception class name>
```

**There is no switch to not open**, as ADR-266 adds none: under `CI`, as the control panel's own child runs, and on Linux with no display, nothing is opened.

`java.awt.Desktop.browse` is not used: `java.desktop` is not one of ADR-285 §3's two modules, and ADR-266's commands already open an address on each system. `cmd /c start` is not used, for ADR-266's reason.

### 8. The jar's progress page

ADR-266 §2's opening is the jar's: an invocation opens its own `progress.html`, once. The control panel's child runs with `CI=1` (ADR-278 §1, ADR-292 §4), so under the control panel the jar opens nothing, and the control panel opens only its own address, by the same commands in a class of its own, since it links no class of the jar (ADR-285 §3). The progress page is reached from the control panel by its link, in a tab of its own, a report's (ADR-302 §3): that tab carries no mark of §4.1 and no script of the control panel's, so it neither comes back nor counts as a tab coming back (§4.4). The two openings do not meet.

### 9. What a test holds, and what it cannot

The tests reach the pages through `PanelBrowser` and `RawHttp`, on the server the screen tests start (ADR-297), and reach the start through the control panel as the operator starts it: `java -jar` on a jar of the control panel's classes beside the jar for the screen tests, with `CI=1`, `VESPERA_CONTROL_PANEL_PORT` and a temporary directory of the test's own. A test playing the tab polls `/alive` as §4.2 says and reloads its page.

No test runs the script: the build has no JavaScript engine, and none is added (ADR-297 §1, ADR-046). What a browser does with it, the `Sec-Fetch-Site` it sends, a hidden tab's timers, the `<noscript>`, rests on the specifications of the Context and is not measured. A test reads the script's text for §4.2's rules, and the server's answers for §4.3's.

### 10. The classes

In `io.algernon.controlpanel`, linking no class of the jar:

- **`ControlPanelServer`**: `start(Map<String, HttpHandler> pages)` keeps its contract but for the token, at a port the system picks, which the screen tests and `ControlPanelForTests` start through. Beside it, a start at a given port, refused with `BindException` where the port is taken; the mark of §4.1; the answer to `/alive` (§4.3); and a wait for a tab to come back, with the bound handed in and returning as soon as one does (§4.4). `firstAddress()` is the root alone (§2). It holds no token, no cookie's name and no key, and still writes no file and starts no process (ADR-291 §8).
- **`ControlPanelPage`**: the mark, the script element, the `<noscript>` and `data-refresh-seconds` (§4.1, §5), the second policy (§6), and the script's text as one constant.
- **`BrowserOpener`** and **`SystemBrowserOpener`** (§7).
- **`ControlPanel`**: §1's reading of the variable and the fallback, and §3's start, `begin(ControlPanelState state, String portNamed, Duration bound, BrowserOpener opener, Consumer<String> out, Consumer<String> err)`, with the bound, the opener and the lines of each stream handed in, so that a test gives a short bound, an opener that records, and consumers it reads; `main` gives 5 seconds, `SystemBrowserOpener`, `System.out` and `System.err`. `serve` keeps its contract, at a port the system picks, for `ControlPanelForTests`.

No class reads or writes a token's file.

A bound handed in is what the wait reads, so no clock is: a test hands in a short one where no tab comes back, and a long one where one does, to see the wait end as the tab comes back.

### 11. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves, and no dependency or plugin is added. The README's section on starting the control panel says that it opens the browser, at which port it listens and how another is named, that a tab it opened comes back to the next start, and that the control panel answers anyone on the machine. `docs/architecture.md`'s sentence on the control panel's server says the same of the port, the checks without a token, `/alive` and the opening of the browser.

## Known limits

- Another local user, and any program on the machine, can use the control panel as the operator does while it runs, read every page and report it serves, text of documents included, and start, stop and write through it (§2).
- A tab the browser has frozen or discarded, or whose timers it delays past the bound, does not come back in time: the start opens a new tab, and the old one, polling again once it wakes, reloads onto the same start, so two tabs show it.
- A browser closed between two starts, or a tab closed, leaves nothing to come back: the start waits its 5 seconds, then opens a new tab.
- Another program listening at 24291 before the control panel starts keeps it: the control panel listens at a port the system picks and opens a new tab (§1). A tab of the last start, polling 24291, reloads onto that program where it answers `/alive` with `200` and a body of 22 characters of base64url.
- A page of another site can tell, by whether `127.0.0.1:24291` answers at all, that the control panel runs; it reads nothing of any answer.
- In a browser that sends no `Sec-Fetch-Site`, a request another site's page makes to `/alive` counts as a tab coming back, and that start opens no tab; and a page of the control panel's own opens in a window another site opens, as ADR-302 §12 says of such a browser.

## Alternatives refused

- **A token kept across starts in a file of the user's own, with a proof, under a key derived from it, that the server answering a tab holds it.** It keeps out another local user, whom the control panel does not keep out (§2), and only while that user does not listen at 24291 with the control panel down; it costs a file and its folder's rules, a key, a proof the script checks with `crypto.subtle`, and a tab refused where a start draws another token.
- **A token drawn at every start, as ADR-291 had it.** No tab could come back to it.
- **Dropping `Host`, `Sec-Fetch-Site` and `Origin` with the token.** A website open in the operator's browser could post every form, starting a run, stopping a child and writing the profile, could read every page through a name of its own rebound to `127.0.0.1`, and could open a page of the control panel's own for a click it tricks out of the operator. The three checks keep that closed and hold no secret.
- **A file saying the last start left a tab open, so that a start with none waits for nothing.** A file and its folder's rules, to spare 5 seconds of a start whose tab was closed, and wrong wherever the tab was closed after the file was written.
- **The port the system picked, remembered and asked for again.** It lies in the range the system hands to outgoing connections, so it is the likelier to be taken at the next start; a port outside that range is never handed out.
- **Refusing to start where the port is taken.** The operator would have to name another before working at all; a port the system picks costs only the reuse of a tab.
- **A bound long enough for a tab Chromium wakes once a minute.** Every start with no tab open would wait a minute before opening one.
- **`<meta http-equiv="refresh">` kept on the invocation page.** At the end of a start the refresh lands on the browser's error page, where the script no longer runs, so the tab watching an invocation, the one most often open, would never come back.
- **A connection held open, by server-sent events or a WebSocket.** `HttpServer` has no WebSocket, a held response takes a thread per tab, and when it is reconnected is the browser's, not the script's.
- **`java.awt.Desktop.browse`, and `cmd /c start`.** As §7 says.
- **A switch to not open.** As ADR-266 refused one: `CI` and a machine with no display already open nothing.
- **Reusing a tab within one start**, for an address opened twice. The operator chose the reuse across starts.

## Consequences

- The operator starts the control panel and the browser opens on it; started again, the tab already open reloads, and no other opens.
- The address it prints holds no secret, and is good at that port for as long as a control panel listens there.
- The control panel listens at 24291 unless told otherwise, and a second one started beside it listens at a port the system picks, in a tab of its own.
- Each start at a port of its own with no tab open waits up to 5 seconds before it opens one.
- Every page of the control panel's own carries one script, and a browser running none still refreshes the invocation page.
- A tab of the control panel polls its server once a second.
- Another website in the operator's browser is kept out as before; another local user is not.
- `vespera.lock`, the launch values, the reports and the jar's progress page are as they were.

## Tests

These hold §1 to §9 through the pages and the start as the operator meets them; none of them names a class of §10.

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.itListensAtThePortTheVariableNames` | started with `VESPERA_CONTROL_PANEL_PORT` naming a free port, the address it prints is the root at that port, with no query (§1, §2) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.withNoPortNamedItListensAt24291` | started with the variable absent, the address is the root at 24291; aborted where 24291 is taken on the machine (§1) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aPortTheVariableCannotNameIsRefusedWithOneLine` | `twenty-four`, `65536` and `-1`: exit 1, §1's line on standard error naming the value, and no address (§1) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aTakenPortIsLeftForOneTheSystemPicksAndWaitsForNoTab` | the named port held: §1's line, an address at another port, and no line of waiting (§1, §3) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aStartNoTabComesBackToWaitsTheBoundThenOpensTheRoot` | a start no tab polls: ADR-288 §4's line, the waiting line, then, at least 4 seconds later, the address of the root (§2, §3) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aTabOfTheLastStartComesBackAndNoOtherIsOpened` | a tab of a first start, polling as §4.2 says once the first is ended, finds another mark at the same port and reloads onto the second start's page, carrying its mark; the second writes the waiting line, then the line of a tab come back, and no address (§3, §4) |
| `controlpanel.TheControlPanelsTabTest.everyPageCarriesTheStartsMark` | every page of the control panel's own carries one `data-start` of 22 characters of base64url, the same on each (§4.1) |
| `controlpanel.TheControlPanelsTabTest.theScriptIsServedAsJavaScriptTheSameAtEveryStart` | `/control-panel.js`: 200, `text/javascript; charset=utf-8`, the same bytes from two starts, holding neither start's mark (§4.2) |
| `controlpanel.TheControlPanelsTabTest.theScriptPollsAliveAndParsesNoMarkup` | the script names `/alive`, and none of `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, `Function` and `setInterval` (§4.2) |
| `controlpanel.TheControlPanelsTabTest.everyPageOfItsOwnAllowsScriptsAndConnectionsOnlyFromItsOrigin` | every page of the control panel's own carries ADR-291 §7's policy and then §6's, and no third (§6) |
| `controlpanel.TheControlPanelsTabTest.theInvocationPageRefreshesThroughTheScriptAndTheNoscriptOnlyWhileTheChildRuns` | while the child runs, `data-refresh-seconds` 2 and the `<meta>` of 2 seconds inside `<noscript>` in the head; once it is stopped, neither (§5) |
| `controlpanel.TheControlPanelsTabTest.theAliveAnswerIsTheStartsMarkAlone` | `/alive`, with a query and without: 200, plain text, the mark of the pages and nothing else, and ADR-291 §7's four headers (§4.3) |
| `controlpanel.TheControlPanelsTabTest.anAliveRequestOfAnotherSiteHostOrMethodIsNotAnswered` | sent `cross-site` or `same-site`, or to another `Host`: 403 with ADR-291 §4's line; `POST` and `HEAD`: 405 with `Allow: GET` (§4.3) |
| `controlpanel.EveryPageHoldsOnlyTheControlPanelsScriptTest.everyPageHoldsOneScriptByItsAddressAndNoEventAttribute` | every page of the control panel's own, after an invocation ended and while one runs, holds one `<script>`, `src` `/control-panel.js`, with no text, and no attribute beginning `on`, though the text it shows holds markup (§4.1); it replaces ADR-302's test that no page holds a script |

These hold §2 on the server ADR-291's tests start, through `RawHttp`; each names `ControlPanelServer` as ADR-291's tests do:

| Method | What it holds |
| --- | --- |
| `controlpanel.ControlPanelServerTest.theAddressTheOperatorOpensIsTheRootWithNoQuery` | `firstAddress()` is `http://127.0.0.1:<port>/` (§2) |
| `controlpanel.ControlPanelServerTest.aRequestToItsOwnAddressGetsThePageWithNoCookieAndSetsNone` | with no `Cookie`, with a cookie of any name, and with a `token` parameter in the query: the page, 200, and no `Set-Cookie` (§2) |
| `controlpanel.ControlPanelServerTest.aRequestNotAddressedToTheControlPanelsOwnAddressIsRefused` | a foreign `Host`, another port, no port and `[::1]`: 403 with the line `open the address the control panel printed when it started`, word for word; no `Host`: 403; no handler run; `127.0.0.1:<port>` and `localhost:<port>` in either case: the page (§2) |
| `controlpanel.ControlPanelServerTest.aPostReachesItsPageOnlyFromTheControlPanelsOwnOrigin` | no `Origin`, `null`, another site's, another scheme's and another port's: 403, no handler run; both of the control panel's own: the handler runs (§2) |
| `controlpanel.ControlPanelServerTest.aMethodOtherThanGetHeadAndPostIsRefusedNamingTheThree` | `PUT`, `DELETE`, `PATCH` and `OPTIONS`: 405 with `Allow: GET, HEAD, POST`, no handler run; `HEAD`: 200 (§2) |
| `controlpanel.ControlPanelServerTest.aPageOpenedFromAnotherSiteIsRefusedButAReportIsNot` | a `GET` or `HEAD` sent `cross-site` or `same-site`, in any case, to a page: 403, no handler run; to a report's path: the report (§2) |
| `controlpanel.ControlPanelServerTest.aPageAskedForFromItsOwnPagesTheAddressBarOrWithNoMarkIsServed` | `same-origin`, `none` and no `Sec-Fetch-Site`: the page (§2) |
| `controlpanel.ControlPanelServerTest.aPathNoPageIsStartedWithIsNotFoundAndNoFileIsServed` | an unknown path and paths climbing out by dots: 404, no handler run (§2) |
| `controlpanel.ControlPanelServerTest.everyResponseCarriesTheFourHeadersAndNoneSetsACookie` | a 403 of each check, a 200, a 404 and a 405 each carry ADR-291 §7's four headers, once each, and none a `Set-Cookie` (§2) |
| `controlpanel.PanelBrowserTest.openingThePrintedAddressLandsOnTheRootAndTheNextPageIsAnswered` | the printed address lands on the root, 200, with no redirect, and the next page is answered (§2) |

These hold the classes of §10, which they name:

| Method | What it holds |
| --- | --- |
| `controlpanel.SystemBrowserOpenerTest.eachSystemIsAskedThroughItsOwnCommand` | Windows through `rundll32 url.dll,FileProtocolHandler`, macOS through `open`, Linux with a display through `xdg-open`, each with the address (§7) |
| `controlpanel.SystemBrowserOpenerTest.underCiOrWithNoDisplayNothingIsOpened` | `CI` set on every system, and Linux with neither display variable: empty (§7) |
| `controlpanel.SystemBrowserOpenerTest.theAddressIsHandedOverAsOneArgument` | three arguments on Windows and two elsewhere, the last the address whole (§7) |
| `controlpanel.ControlPanelStartTest.aTabThatComesBackWithinTheBoundLeavesTheOpenerUnasked` | in process, with a recording opener and a bound of 30 seconds: a tab's poll of `/alive` ends the wait at once, and the opener is never asked (§3, §4.4) |
| `controlpanel.ControlPanelStartTest.noTabWithinTheBoundAsksTheOpenerOnceForTheRoot` | with a short bound and no tab: the waiting line, one address printed, the root alone, and the opener asked once for it (§3) |
| `controlpanel.ControlPanelStartTest.anOpenerThatThrowsLeavesTheAddressPrintedAndTheControlPanelServing` | an opener throwing: §7's line naming the class, the address printed before it, and the server answering its page (§7) |
| `controlpanel.ControlPanelStartTest.aPortOfZeroWaitsForNoTab` | the variable `0`: a port the system picked, the address alone, no waiting line (§1, §3) |
| `controlpanel.ControlPanelServerTabTest.aReportOrARefusedRequestIsNoTabComingBackAndAPollOfAliveIs` | a request to a report's path leaves the wait running, as do `/alive` sent `cross-site`, `/alive` to a foreign `Host` and a `POST` with a foreign `Origin`, each refused; a `GET` of `/alive` ends it (§4.4) |
| `controlpanel.ControlPanelServerTabTest.aPageOrAPathNoPageIsAtIsATabComingBack` | a `GET` of a page's path ends the wait; so, on another server, does a path no page is at, answered 404 (§4.4) |
| `pipeline.TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest.bothChooseTheSameCommandForEverySystem` | for each system and environment, `SystemBrowserOpener.command` and the jar's `SystemPageOpener.command` choose the same command, the address apart (§7, §8) |

`pipeline.TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava` starts the packaged control panel with `CI=1`, `VESPERA_CONTROL_PANEL_PORT=0` and a temporary directory of its own, so that it opens no browser and listens at no port of the machine's operator, and reads the address as the root alone.

`ControlPanelServerTest.theServerWritesNoFileLogsNothingPrintsNothingAndStartsNoProcess` and `TheControlPanelLinksNoClassOfTheJarTest` keep holding §10's classes to ADR-291 §8 and ADR-285 §3 as they stand.

## What this does not decide

- **A second control panel started while one runs**: it listens at a port the system picks (§1), and is told nothing of the first.
- **Reusing a tab within one start**, in place of another the operator opens.
- **Closing a tab that did not come back.**
- **How Firefox and Safari time and freeze a hidden tab**, which nothing here measured.
