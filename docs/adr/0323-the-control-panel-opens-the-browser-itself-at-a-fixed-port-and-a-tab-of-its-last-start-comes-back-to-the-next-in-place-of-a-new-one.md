# ADR-323 — The control panel opens the browser itself, at a fixed port, and a tab of its last start comes back to the next in place of a new one

- **Date**: 2026-10-23
- **Status**: accepted on 2026-10-23.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names its own package, `java.base` and `jdk.httpserver` alone. The proof of §4 is `javax.crypto.Mac` and the opener of §7 `ProcessBuilder`, both of `java.base`; `java.desktop` is not added (§7).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2's table of the system's commands for opening an address, its rule that nothing is opened where `CI` is set or, off Windows and macOS, where no display is, its refusals of `java.awt.Desktop` and of `cmd /c start`, and its refusal of a switch that turns opening off, each applied to the control panel's own opener (§7); and its "only `progress.html` is opened, and once", which stays the jar's and does not meet this record (§8).
- **Keeps**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §3's rules for a script, applied to the control panel's one script (§4.2).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §3, §5 and §7, and §4 for every path but `/alive`; and its §8, the server's classes write no file, log nothing, print nothing and start no process: the token's file is written, and the browser opened, by classes outside the server's (§2, §7, §10).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): the pages are tested as a browser reads them, through `PanelBrowser` and `RawHttp`, and the start as the operator starts it; what a browser does with the script that no test can see is said in §9.
- **Keeps**: [ADR-307](0307-the-child-the-control-panel-started-ends-with-it-wherever-its-end-runs-the-jvms-shutdown-hooks.md): `Ctrl+C` still ends the child with the control panel, and a tab that comes back shows the invocation page with no child (§3).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): no dependency and no plugin is added.
- **Amends**: ADR-291 §1, whose port is 0 at every start: it is 24291, or the one `VESPERA_CONTROL_PANEL_PORT` names, and one the system picks where that one is taken or the variable names 0 (§1); §2, whose token lives only in the server's memory and whose control panel opens no browser: the token is kept in a file of the user's own while a tab comes back holding it, and the control panel opens the browser (§2, §7); §4 and §6, which gain one path answered without the token, `/alive` (§4.3); §8, whose control panel writes no file holding the token: one file holds it (§2); its Known limits, which gain those below; its Consequences' "A restarted control panel has a new port and a new token"; its alternatives refused "A fixed port, or one the operator configures" and "Opening the browser itself", reversed for the reasons below; and its What this does not decide's "The port and the token are never remembered". It carries a note at its head.
- **Amends**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §3: the folder of the user's own holds a second file, `token`, under the same rules (§2). It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §1, whose pages carry no script of the control panel's own and whose invocation page refreshes by `<meta http-equiv="refresh">`: every page of the control panel's own carries one script, by its address, and the invocation page's refresh is the script's, the `<meta>` kept inside `<noscript>` for a browser running no script (§4, §5); its "No `GET` of the control panel changes anything", which gains one exception, a tab coming back (§4.4); §9, whose attributes a test reads gain `data-start`, `data-tab-key` and `data-refresh-seconds` (§4.1, §5); §11, whose start writes the address at once: it may first wait for a tab, and writes another line where one comes back (§3); §12's fifth reason, "the port and the token are drawn again at every start", which no longer holds and which the other four carry without it (§6); and its Tests' test that no page holds a `<script>` element, which holds one script by its address instead. It carries a note at its head.
- **Settles**: that the control panel opens the browser itself, and that a tab it opened is reused across its starts and not within one, are the operator's; §1 to §10 are decided under the operator's delegation of 2026-10-23.

## Context

The **control panel** (ADR-285) serves its pages on `127.0.0.1` at a port the system picks at each start, answering only a browser holding that start's token (ADR-291), and prints one address for the operator to open (ADR-302 §11). It opens no browser, and a tab left open from one start is refused by the next, which has another port and another token.

Two things are wanted of it: that it opens the browser on its own address; and that a tab it opened at one start, still open when the operator ends it with `Ctrl+C` and starts it again, is reloaded by the next start, which then opens no other. A second tab within one start is not to be replaced by the first.

**What a tab coming back needs.** A browser keys a page's origin on its scheme, host and port, and sends a cookie of `127.0.0.1` to every port of it (ADR-291's Known limits). A tab can be reloaded by the next start only at the same port, and is let in only where that start takes the token its cookie holds. ADR-291 refused a fixed port because another local user can listen on it before the control panel starts and be the server the operator's browser reaches: were a tab to come back by itself, carrying its cookie, that server would receive the token without the operator doing anything.

**What a browser does with a page's script**, from the specifications and the browsers' published rules, and not measured here:

- `http://127.0.0.1:<port>` is a potentially trustworthy origin (W3C Secure Contexts), so a page served there is a secure context, and `crypto.subtle` is given to its script.
- `fetch` with `credentials: 'omit'` sends no cookie; a request a page makes to its own origin carries `Sec-Fetch-Site: same-origin`.
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

A start at a port the system picked, for either reason, neither reads nor writes the token's file (§2), waits for no tab (§3), and opens a new tab with a token of its own.

**Why 24291.** It lies below 32768, where Linux starts handing out ports to outgoing connections, and below 49152, where Windows and macOS do, so the system never gives it to another program's outgoing connection; and in a range IANA leaves unassigned.

### 2. The token, kept

ADR-291 §2's token, 32 bytes of `SecureRandom` written as 43 characters of base64url without padding, is kept in a file of the user's own, beside ADR-292's:

```
<java.io.tmpdir>/vespera-control-panel-<user>/token
```

`<user>` is made as ADR-292 §3 makes it. The folder is ADR-292 §3's, under its rules: created `rwx------` where POSIX permissions apply, neither read nor written where it exists and is not the user's own, its owner not compared on Windows. The file holds the token's 43 characters and nothing else, `rw-------` where POSIX permissions apply, written whole to a temporary file in the folder and moved over the old one with `ATOMIC_MOVE` and `REPLACE_EXISTING`.

- **It is read** as a start at the fixed port begins, and the token it holds is the one that start begins with. A file that is missing gives none, and nothing is said. A file that cannot be read, or holds anything but 43 characters of base64url, with at most one line break after them, gives none, with one line on standard error:

  ```
  the control panel could not read its token at <file>, and draws a new one: <reason>
  ```

  A folder that is not the user's own gives none, with:

  ```
  the control panel neither reads nor keeps its token in <folder>, which is not a folder only this user can write in
  ```

- **It is written** whenever a start at the fixed port draws a token (§3), and at no other time. Where it cannot be, one line says so, and the start goes on:

  ```
  the control panel could not keep its token at <file>: <reason>
  ```

  No line holds the token.

- **A token is kept only through a tab.** A start keeps the token it began with only where a tab came back holding it within §3's bound. Every other start at the fixed port draws a new token, which no tab holds, and keeps that one.

The token is still passed on no command line and never logged. It lies in the file, in the server's memory, in the browser's cookie, and in the one address a start that draws a token prints (ADR-302 §11).

### 3. The start

`ControlPanel.main`, after ADR-288 §4's line naming the jar it will start:

1. reads `VESPERA_CONTROL_PANEL_PORT` and refuses as §1 says;
2. binds the port, or one the system picks (§1);
3. at the fixed port, reads the token's file (§2);
4. where it read a token: starts the server with it, writes

   ```
   the control panel waits up to 5 seconds for a tab it opened before
   ```

   and waits up to **5 seconds** for a tab to come back (§4.4). Where one does, it writes

   ```
   a tab it opened before came back to http://127.0.0.1:<port>/, so the control panel opens no other
   ```

   and opens nothing. Where none does within the 5 seconds, it draws a new token, the old one refused from then on, and goes on as in 5;
5. otherwise, with a token it drew, keeps it (§2, at the fixed port only), writes ADR-302 §11's line

   ```
   open http://127.0.0.1:<port>/?token=<token> in your browser
   ```

   and then asks the opener to open that address (§7).

Every line is flushed as it is written. The waiting line names 5 seconds, the bound `main` hands in, whatever bound a test hands in (§10): it is the operator's line, and a test's shorter bound does not change what the operator reads. A start that read no token waits for nothing: no tab can hold a token nobody kept. Then the control panel serves until its process ends, with ADR-307's shutdown hook as before. A tab that comes back after `Ctrl+C` shows the invocation page of a start whose child has not been started, the child of the last having ended with it (ADR-307).

**The bound, against the poll.** A tab polls one second after its last poll settled (§4.2), each poll given up after a second, and a hidden tab's timer may wake a second late. So a tab that last polled just before the server came up polls again within 2 seconds, its answer comes back over loopback in milliseconds and in at most a second, and the reload it then sends reaches the server at once: within 3 seconds of the server coming up. The bound of 5 seconds leaves 2 more for a loaded machine. They are spent only by a start that kept a token, and only in full where no tab comes back.

### 4. How a tab comes back

#### 4.1 What a page carries

Every page of the control panel's own, written through `ControlPanelPage` (ADR-302 §10), which no report is, carries on its `<body>`:

- `data-start`, the start's **mark**: 16 bytes of `SecureRandom` drawn as the server starts, as 22 characters of base64url without padding, the same on every page of one start and another at the next;
- `data-tab-key`, the **tab's key**: `HMAC-SHA256`, keyed with the token's 43 characters as ASCII bytes, of the ASCII text `vespera-control-panel-tab-key`, its 32 bytes as 43 characters of base64url without padding. It changes with the token.

and in its `<head>` one script element, holding no text:

```html
<script src="/control-panel.js" defer></script>
```

The key lets a page check that the server answering it holds the token. It is not the token, which no script reads (ADR-291 §3's `HttpOnly`), and the server lets no request in by it.

#### 4.2 The script

`/control-panel.js` is a path the server is started with, answered as every page is, past ADR-291's checks and the cookie: a `GET` or `HEAD` with `200` and `Content-Type: text/javascript; charset=utf-8`, a `POST` with `405` and `Allow: GET, HEAD`. Its text is one constant, the same at every start, under ADR-269 §3's rules: it holds no data of a start, and reads only the attributes of §4.1 and §5; it writes nothing into the page; it never sets `innerHTML`, `outerHTML` or `insertAdjacentHTML`, and never calls `document.write`, `eval` or `Function`.

It does this:

1. It reads `data-start`, `data-tab-key` and, where the page carries it, `data-refresh-seconds`. Where `crypto.subtle` is not there, it reloads the page every `data-refresh-seconds` seconds where the page carries it, and does nothing else.
2. One second after the page has loaded, and one second after each poll has settled, never on an interval, it polls: it draws 16 bytes from `crypto.getRandomValues`, writes them as 32 lowercase hexadecimal characters, the **nonce**, and sends `GET /alive?nonce=<nonce>` to its own origin with `credentials: 'omit'`, `cache: 'no-store'` and `redirect: 'error'`, given up after one second. Scheduled from the poll's settling, a timer is never set from inside another timer's callback.
3. On a `200` whose body is a mark, one space and a proof that verifies under the tab's key, with `crypto.subtle`, for `<port> <mark> <nonce>`, `<port>` being the port of the page's own address: where the mark is not the page's `data-start`, the server is a later start holding the token, and the script reloads the page; where it is, and the page carries `data-refresh-seconds`, it reloads the page once that many seconds have passed since the page loaded.
4. Any other outcome, a refusal, a network error, a poll given up or a proof that does not verify, changes nothing, and the next poll follows.

So a poll never carries the cookie, a server that does not hold the token cannot give a proof the tab's key verifies, and a tab never goes to a server that has not given one.

#### 4.3 `/alive`

The server answers `/alive` itself, beside the pages it is started with, to a request holding neither the cookie nor the token:

- after ADR-291 §4's check of `Host` and ADR-302 §12's of `Sec-Fetch-Site`, each refusing as there; the cookie is not checked on this path, and the token neither exchanged nor read;
- a method other than `GET`, `HEAD` included, is answered **405**, with `Allow: GET`: the script sends nothing but a `GET`, and an answer with no body proves nothing;
- a query other than exactly `nonce=` and 32 lowercase hexadecimal characters is answered **400**, with no body;
- otherwise **200**, `Content-Type: text/plain; charset=utf-8`, and as its body the start's mark, one space, and the **proof**: `HMAC-SHA256`, keyed with the tab's key's 32 bytes, of the ASCII text `<port> <mark> <nonce>`, `<port>` being the port the server is bound to in decimal, as 43 characters of base64url without padding, and no line break.

ADR-291 §7's four headers are on every answer. Another local user who asks `/alive` learns that the control panel runs, its port, its mark, and the proofs of nonces of their own choosing, from which the key is not found, `HMAC-SHA256` being a pseudorandom function. Nothing of it lets a request in.

The port inside the proof keeps a server listening at the fixed port from handing a tab's nonce to the control panel at a port the system picked and passing its proof back: that proof names the other port.

#### 4.4 A tab coming back

A tab has come back once the server lets in, after it started, a request holding the token, in its cookie or as ADR-291 §3's exchange, to a path that does not begin `/report/`. A request to `/alive` holds no token, and a report's tab carries no script of the control panel's (ADR-302 §3), so neither is one. Any other request let in counts, whatever its method and path: a `GET` of `/control-panel.js`, a `POST` from the control panel's own origin, and a path no page is at, answered 404. Each holds the token, so a browser holding the cookie is there, which is all a tab coming back has to show; telling a page apart from the rest would add a rule and change no outcome, since a browser asks for the script and posts a form only from a page it has loaded.

It is the one effect a `GET` of the control panel has beyond showing a page (ADR-302 §1): it decides whether the start opens a new tab, and changes no page, no file, no value a page shows and no child.

### 5. The invocation page's refresh

While the child the invocation page shows runs, the page carries `data-refresh-seconds="2"` on its `<body>`, and ADR-302 §1's `<meta http-equiv="refresh" content="2">` inside a `<noscript>` in its `<head>`. Once the child has ended, or where none was started, it carries neither. A browser running the script reloads the page only once the server has answered as the same start (§4.2), so a page whose start ends stays as it is, still polling, until the next start reloads it. A browser running no script refreshes by the `<meta>`, as before.

### 6. A second policy on every page of the control panel's own

Every page of §4.1 is answered with a second header beside ADR-291 §7's:

```
Content-Security-Policy: script-src 'self'; connect-src 'self'
```

so the one script runs from the control panel's own origin, no script written into a page runs, and the script connects to no other origin. A report keeps ADR-302 §3's two policies and no third, since its own script is written inside it. The script's answer and `/alive`'s carry ADR-291 §7's four headers alone.

ADR-302 §12's reasons for `SameSite=Lax` stand without its fifth: a page another site opens at the fixed port is refused by `Sec-Fetch-Site` as it was at a picked one, but for a report, which changes nothing and which that site cannot read.

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

ADR-266 §2's opening is the jar's: an invocation opens its own `progress.html`, once. The control panel's child runs with `CI=1` (ADR-278 §1, ADR-292 §4), so under the control panel the jar opens nothing, and the control panel opens only its own address, by the same commands in a class of its own, since it links no class of the jar (ADR-285 §3). The progress page is reached from the control panel by its link, in a tab of its own, a report's (ADR-302 §3): that tab carries none of §4.1's marks and no script of the control panel's, so it neither comes back nor counts as a tab coming back (§4.4). The two openings do not meet.

### 9. What a test holds, and what it cannot

The tests reach the pages through `PanelBrowser` and `RawHttp`, on the server the screen tests start (ADR-297), and reach the start through the control panel as the operator starts it: `java -jar` on a jar of the control panel's classes beside the jar for the screen tests, with `CI=1`, `VESPERA_CONTROL_PANEL_PORT` and a temporary directory of the test's own. A test playing the tab polls `/alive` as §4.2 says and reloads with its cookie.

No test runs the script: the build has no JavaScript engine, and none is added (ADR-297 §1, ADR-046). What a browser does with it, the secure context, `credentials: 'omit'`, a hidden tab's timers, the `<noscript>`, rests on the specifications of the Context and is not measured. A test reads the script's text for §4.2's rules, and the server's answers for §4.3's proof.

### 10. The classes

In `io.algernon.controlpanel`, linking no class of the jar:

- **`ControlPanelServer`**: `start(Map<String, HttpHandler> pages)` keeps its contract, a port the system picks and a token drawn and kept nowhere, which the screen tests and `ControlPanelForTests` start through. Beside it, a start at a given port, with a given token or a drawn one, refused with `BindException` where the port is taken; the mark and the tab's key of §4.1; the answer to `/alive` (§4.3); a wait for a tab to come back, with the bound handed in and returning as soon as one does (§4.4); and the drawing of a new token in place of the one it began with (§3). The token is still drawn, held and compared there (ADR-291 §2, §3), and the server still writes no file and starts no process (ADR-291 §8).
- **`ControlPanelPage`**: the marks, the script element, the `<noscript>` and `data-refresh-seconds` (§4.1, §5), the second policy (§6), and the script's text as one constant.
- **`ControlPanelToken`**: reads and writes §2's file, never throwing, its lines handed to a consumer, as `LaunchValues` does.
- **`BrowserOpener`** and **`SystemBrowserOpener`** (§7).
- **`ControlPanel`**: §1's reading of the variable and the fallback, and §3's start, with the bound, the opener and the lines of each stream handed in, so that a test gives a short bound, an opener that records, and consumers it reads; `main` gives 5 seconds, `SystemBrowserOpener`, `System.out` and `System.err`. `serve` keeps its contract, at a port the system picks, for `ControlPanelForTests`.

A bound handed in is what the wait reads, so no clock is: a test hands in a short one where no tab comes back, and a long one where one does, to see the wait end as the tab comes back.

### 11. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves, and no dependency or plugin is added. The README's section on starting the control panel says that it opens the browser, at which port it listens and how another is named, that a tab it opened comes back to the next start, and how long an address it prints is good for. `docs/architecture.md`'s sentence on the control panel's server says the same of the port, the kept token, `/alive` and the opening of the browser.

## Known limits

- A tab the browser has frozen or discarded, or whose timers it delays past the bound, does not come back in time: the start opens a new tab, and the old one, whose token is no longer the server's, stays as it was.
- A browser closed between two starts holds no cookie: the start waits its 5 seconds, then opens a new tab.
- A browser that gives no `crypto.subtle` to `http://127.0.0.1` reuses no tab.
- A tab whose proof verified just before a start drew a new token reloads onto ADR-291 §4's refusal.
- Another program or another user listening at 24291 before the control panel starts keeps it: the control panel listens at a port the system picks and opens a new tab (§1). A tab the operator reloads by hand while that program holds the port sends it the cookie, as ADR-291's limits say of any port of `127.0.0.1`; the token in it is the last start's, which the start that found the port taken does not take.
- A page of another site can tell, by whether `127.0.0.1:24291` answers at all, that the control panel runs; it reads nothing of any answer.
- A token is good across starts for as long as a tab comes back to each; one that leaves the machine, in an address copied out of the terminal, is good until a start draws another.

## Alternatives refused

- **The port the system picked, remembered and asked for again.** It lies in the range the system hands to outgoing connections, so it is the likelier to be taken at the next start; a port outside that range is never handed out.
- **Refusing to start where the port is taken.** The operator would have to name another before working at all; a port the system picks costs only the reuse of a tab.
- **The token kept, and the tab polling with its cookie.** A server another user started at the fixed port while the control panel was down would receive the token at the first poll.
- **A proof written into the page, the same at every poll.** Anyone may ask `/alive`, so a fixed answer can be learnt from the control panel and replayed by a server at its port.
- **A proof that does not name the port.** A server at the fixed port could pass a tab's nonce to the control panel at the port it fell back to and hand its proof on.
- **A cookie that outlives the browser, with `Max-Age`.** A tab across starts of the control panel is what is wanted, not across starts of the browser, and a longer cookie lengthens what a copied token is good for.
- **A new token at every start.** No tab could come back to it.
- **A bound long enough for a tab Chromium wakes once a minute.** Every start with no tab open would wait a minute before opening one.
- **`<meta http-equiv="refresh">` kept on the invocation page.** At the end of a start the refresh lands on the browser's error page, where the script no longer runs, so the tab watching an invocation, the one most often open, would never come back.
- **A connection held open, by server-sent events or a WebSocket.** `HttpServer` has no WebSocket, a held response takes a thread per tab, and when it is reconnected is the browser's, not the script's.
- **`java.awt.Desktop.browse`, and `cmd /c start`.** As §7 says.
- **A switch to not open.** As ADR-266 refused one: `CI` and a machine with no display already open nothing.
- **Reusing a tab within one start**, for an address opened twice. The operator chose the reuse across starts.

## Consequences

- The operator starts the control panel and the browser opens on it; started again, the tab already open reloads, and no other opens.
- The control panel listens at 24291 unless told otherwise, and a second one started beside it listens at a port the system picks, in a tab of its own.
- Each start with a token kept but no tab waits up to 5 seconds before it opens one.
- Every page of the control panel's own carries one script, and a browser running none still refreshes the invocation page.
- A tab of the control panel polls its server once a second.
- `vespera.lock`, the launch values, the reports and the jar's progress page are as they were.

## Tests

These hold §1 to §9 through the pages and the start as the operator meets them; none of them names a class of §10.

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.itListensAtThePortTheVariableNames` | started with `VESPERA_CONTROL_PANEL_PORT` naming a free port, the address it prints is at that port (§1) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.withNoPortNamedItListensAt24291` | started with the variable absent, the address is at 24291; aborted where 24291 is taken on the machine (§1) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aPortTheVariableCannotNameIsRefusedWithOneLine` | `twenty-four`, `65536` and `-1`: exit 1, §1's line on standard error naming the value, and no address (§1) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aTakenPortIsLeftForOneTheSystemPicksAndNoTokenIsKept` | the named port held: §1's line, an address at another port, no line of waiting, and no token's file (§1, §2) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.theFirstStartKeepsItsTokenAndWaitsForNoTab` | a first start writes ADR-288 §4's line and then the address, nothing between, and its file holds the address's token (§2, §3) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.theTokensFileIsTheUsersAloneOnPosix` | where POSIX permissions apply, the token's file is `rw-------` (§2) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.aTabOfTheLastStartComesBackAndNoOtherIsOpened` | a tab of a first start, polling as §4.2 says once the first is ended, verifies a second start's proof with its own key, finds another mark, and reloads with its cookie onto the second start's page; the second writes the waiting line, then the line of a tab come back, and no address (§2, §3, §4) |
| `controlpanel.TheControlPanelOpensItsTabOnceAcrossStartsTest.whereNoTabComesBackANewTokenIsDrawnAndItsAddressPrinted` | a second start no tab polls: the waiting line, then, at least 4 seconds later, an address with another token, which its file now holds, and the first start's cookie refused (§2, §3) |
| `controlpanel.TheControlPanelsTabTest.everyPageCarriesTheStartsMarkAndTheKeyDerivedFromTheToken` | every page of the control panel's own carries one `data-start` of 22 characters of base64url and the `data-tab-key` §4.1 derives from the server's token (§4.1) |
| `controlpanel.TheControlPanelsTabTest.theScriptIsServedWithTheCookieAloneTheSameAtEveryStart` | `/control-panel.js`: 200, `text/javascript; charset=utf-8`, the same bytes from two starts, none of either start's mark, key or token; 403 without the cookie (§4.2) |
| `controlpanel.TheControlPanelsTabTest.theScriptPollsAliveWithoutTheCookieAndParsesNoMarkup` | the script names `/alive` and `credentials: 'omit'`, and none of `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, `Function` and `setInterval` (§4.2) |
| `controlpanel.TheControlPanelsTabTest.everyPageOfItsOwnAllowsScriptsAndConnectionsOnlyFromItsOrigin` | every page of the control panel's own carries ADR-291 §7's policy and then §6's, and no third (§6) |
| `controlpanel.TheControlPanelsTabTest.theInvocationPageRefreshesThroughTheScriptAndTheNoscriptOnlyWhileTheChildRuns` | while the child runs, `data-refresh-seconds` 2 and the `<meta>` of 2 seconds inside `<noscript>` in the head; once it is stopped, neither (§5) |
| `controlpanel.TheControlPanelsTabTest.theAliveAnswerProvesTheStartWithoutTheCookie` | `/alive` with a nonce, no cookie: 200, plain text, the mark of the pages and the proof §4.3 computes for the server's port, another proof for another nonce, and ADR-291 §7's four headers (§4.3) |
| `controlpanel.TheControlPanelsTabTest.anAliveRequestOfAnotherSiteHostOrMethodOrABadNonceIsNotAnswered` | sent `cross-site` or to another `Host`: 403 with ADR-291 §4's line; `POST`: 405 with `Allow: GET`; a nonce absent, short, in capitals or beside another parameter: 400 (§4.3) |
| `controlpanel.EveryPageHoldsOnlyTheControlPanelsScriptTest.everyPageHoldsOneScriptByItsAddressAndNoEventAttribute` | every page of the control panel's own, after an invocation ended and while one runs, holds one `<script>`, `src` `/control-panel.js`, with no text, and no attribute beginning `on`, though the text it shows holds markup (§4.1); it replaces ADR-302's test that no page holds a script |

`pipeline.TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava` starts the packaged control panel with `CI=1`, `VESPERA_CONTROL_PANEL_PORT=0` and a temporary directory of its own, so that it opens no browser, listens at no port of the machine's operator and reads no token of theirs.

These hold the classes of §10, which they name:

| Method | What it holds |
| --- | --- |
| `controlpanel.SystemBrowserOpenerTest.eachSystemIsAskedThroughItsOwnCommand` | Windows through `rundll32 url.dll,FileProtocolHandler`, macOS through `open`, Linux with a display through `xdg-open`, each with the address (§7) |
| `controlpanel.SystemBrowserOpenerTest.underCiOrWithNoDisplayNothingIsOpened` | `CI` set on every system, and Linux with neither display variable: empty (§7) |
| `controlpanel.SystemBrowserOpenerTest.theAddressIsHandedOverAsOneArgument` | three arguments on Windows and two elsewhere, the last the address whole (§7) |
| `controlpanel.ControlPanelStartTest.aTabThatComesBackWithinTheBoundLeavesTheOpenerUnasked` | in process, with a recording opener and a bound of 30 seconds: a tab's request with the cookie ends the wait at once, and the opener is never asked (§3, §4.4) |
| `controlpanel.ControlPanelStartTest.noTabWithinTheBoundAsksTheOpenerOnceForTheNewAddress` | with a short bound and no tab: one address printed, the opener asked once for it, its token new and kept (§3) |
| `controlpanel.ControlPanelStartTest.anOpenerThatThrowsLeavesTheAddressPrintedAndTheControlPanelServing` | an opener throwing: §7's line naming the class, the address printed before it, and the server answering (§7) |
| `controlpanel.ControlPanelStartTest.aPortOfZeroReadsAndKeepsNoTokenAndWaitsForNoTab` | a kept token in the file and the variable `0`: a port the system picked, a token of its own, the file untouched, no waiting line (§1, §2) |
| `controlpanel.ControlPanelServerTabTest.aReportOrAnAliveRequestIsNoTabComingBack` | requests to `/alive` and to a report's path, with the cookie, leave the wait running; a page's path with it ends it (§4.4) |
| `controlpanel.ControlPanelTokenTest.aTokenFileThatCannotBeReadIsReplacedWithOneLine` | bytes that are not a token, and a folder in the file's place: no token, §2's line naming the file and not the token (§2) |
| `controlpanel.ControlPanelTokenTest.aFolderOthersCanWriteInIsNeitherReadNorWritten` | where POSIX permissions apply, a folder `rwxrwxrwx`: no token read, none written, §2's line naming the folder (§2) |
| `pipeline.TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest.bothChooseTheSameCommandForEverySystem` | for each system and environment, `SystemBrowserOpener.command` and the jar's `SystemPageOpener.command` choose the same command, the address apart (§7, §8) |

`ControlPanelServerTest.theServerWritesNoFileLogsNothingPrintsNothingAndStartsNoProcess` and `TheControlPanelLinksNoClassOfTheJarTest` keep holding §10's classes to ADR-291 §8 and ADR-285 §3 as they stand.

## What this does not decide

- **A second control panel started while one runs**: it listens at a port the system picks (§1), and is told nothing of the first.
- **Reusing a tab within one start**, in place of another the operator opens.
- **Closing a tab that did not come back.**
- **How Firefox and Safari time and freeze a hidden tab**, which nothing here measured.
