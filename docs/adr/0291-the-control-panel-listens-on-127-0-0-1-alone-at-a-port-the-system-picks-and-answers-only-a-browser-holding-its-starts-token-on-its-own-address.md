# ADR-291 — The control panel listens on 127.0.0.1 alone, at a port the system picks, and answers only a browser holding its start's token, on its own address

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the server is the JDK's `com.sun.net.httpserver.HttpServer`, and the control panel still names only its own package, `java.base` and `jdk.httpserver` (§1, §2).
- **Keeps**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md) and [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md): text of the operator's documents lies where it lay, in the archives, the seed set, a working directory and a deliverable tree, and the control panel adds no place to them, not the browser's cache (§7, §8).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md)'s refusal of `cmd /c start`: the control panel opens no browser (§2).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): no dependency and no plugin is added.
- **Settles** [#563](https://github.com/algernon28/vespera/issues/563).

## Context

ADR-285 builds the **control panel** as plain forms written by a server on the JDK's `com.sun.net.httpserver`, and ADR-288 gives it its entry point, which until its first screen (#561) prints one line and serves nothing. A server on the operator's machine is reachable by more than the operator. Three kinds of caller are to be kept out:

- **Another local user**, who can connect to any port listening on the machine's loopback addresses, and can read every process's command line.
- **Another machine**, which can connect to a port listening on any address that is not loopback. On Windows, listening on such an address raises a Windows Defender Firewall prompt.
- **Another website open in the operator's browser**, whose page can send requests to `127.0.0.1` from the operator's own browser: a form posted across sites (cross-site request forgery), and a name of its own resolved to `127.0.0.1` after the page has loaded (DNS rebinding), so that the browser reads the answers as that site's own. In both the request arrives from the operator's browser, on the loopback address.

Two of the control panel's future pages, the relevance labelling page and the arrangement page, carry text of documents (ADR-261), and a browser keeps the pages it is served in its cache, in its own profile, outside every working directory. `arrangement.html` links into the archive with `file:` links, which a browser does not follow from an `http:` page.

### What was measured

On Linux, OpenJDK 26.0.2.1, an `HttpServer` bound to `127.0.0.1` at port 0, with one handler on the context `/`, sent requests by a raw socket:

- A request with no `Host` header reaches the handler, `Host` read as absent; a request with `Host: evil.example:<port>` reaches it with that `Host`.
- `GET /../vespera.db` reaches the handler with the path `/../vespera.db`, and `GET /%2e%2e/` with the path `/../`: the server neither normalises nor refuses either.
- `PUT`, `DELETE`, `PATCH` and `OPTIONS` reach the handler as its method.
- A header the handler adds as `Content-Security-Policy` is sent as `Content-security-policy`: the server writes each header's name with its first letter upper case and the rest lower case, which HTTP reads as the same name.

- With no executor set, a handler runs on the server's one dispatching thread, and `stop(0)` called while a handler is still answering returns only once that handler does. With an executor set, `stop(0)` returns at once, and with `stop(2)` it waits up to the 2 seconds given for the exchange to finish.

So none of what follows is done by `HttpServer` itself: the control panel's server checks it before a page's handler runs.

## Decision

### 1. Where it listens

The server is the JDK's `com.sun.net.httpserver.HttpServer`, bound to the IPv4 literal `127.0.0.1` alone: never `0.0.0.0`, never `::`, never a name such as `localhost`, which resolves through the system. `[::1]` is not bound. The port is 0, so the system picks a free ephemeral port at each start: there is no fixed port to collide with another program, and none that another user could take before the control panel starts. Nothing remembers the port.

`close` stops the server at once, by `stop(0)`, though a page is still answering: pages' handlers run on an executor of the server's own, not on its dispatching thread, so a page still answering keeps neither `close` nor the port.

Listening on a loopback address raises no Windows Defender Firewall prompt: the prompt comes from listening on an address that is not loopback.

### 2. The token

Each start draws **32 bytes** from `java.security.SecureRandom` and writes them as base64url without padding, **43 characters**: the start's token. It lives only in the server's memory: it is written to no file, passed on no command line, since another local user can read command lines, and never logged.

The address the operator opens is

```
http://127.0.0.1:<port>/?token=<token>
```

and #561 prints it on the operator's console. The control panel opens no browser: `java.desktop`, whose `Desktop.browse` would, is not one of ADR-285 §3's modules, and ADR-266 refused `cmd /c start`.

### 3. The token is exchanged for a cookie

A `GET` of a registered path whose query carries the right `token` parameter is answered **303 See Other**, with `Location` the same path, without the query, and

```
Set-Cookie: vespera-control-panel=<token>; Path=/; HttpOnly; SameSite=Strict
```

The cookie carries no `Max-Age` and no `Expires`, so the browser drops it when it closes, and no `Secure`, since the server speaks plain `http` on loopback. `HttpOnly` keeps it from a page's scripts, `SameSite=Strict` from requests another site starts. The page itself is not served on the 303, so the token leaves the address bar at once.

The token is compared in constant time, by `MessageDigest.isEqual`, whether it arrives in the query or in the cookie. It stays valid for the whole start, so a second tab opened on the printed address is let in too.

### 4. Who is answered

Every request, before any page's handler runs, must carry:

1. **A `Host` header that is the control panel's own address**: exactly `127.0.0.1:<port>`, or `localhost:<port>`, `localhost` compared ASCII case-insensitively, `<port>` being the port of §1. Any other `Host`, one without the port, one of another port, `[::1]:<port>`, or none, is refused. A page of another site that rebinds its name to `127.0.0.1` sends its own name as `Host`, so it is refused.
2. **The cookie of §3 with the token's value, or the exchange of §3.** The cookie is found by its name among the others a browser sends to `127.0.0.1`.

`Host` is checked first: a request with the right cookie, or the right token, and a foreign `Host` is refused. A refused request is answered **403**, with `Content-Type: text/plain; charset=utf-8`, no `Set-Cookie`, and one line as its body:

```
open the address the control panel printed when it started
```

and no page's handler runs.

### 5. Writes

Every `POST` must also carry `Origin: http://127.0.0.1:<port>` or `Origin: http://localhost:<port>`. A `POST` with no `Origin`, with `Origin: null`, or with any other `Origin` is answered 403, as in §4, and its page's handler does not run: a form posted from another site carries that site's origin. `SameSite=Strict` is the second line of the same defence.

The methods served are `GET`, `HEAD` and `POST`. Any other method, from a request §4 lets in, is answered **405** with `Allow: GET, HEAD, POST`, and no page's handler runs.

### 6. What is served

The server answers only the pages it is started with, each by its exact path: `ControlPanelServer.start` takes a map from a path, such as `/`, to the handler of its page. Any other path, from a request §4 and §5 let in, is answered **404**, and no handler runs. No request path is ever mapped to a file, so nothing under a corpus root, a seed set or a deliverable tree, nor the working directory's `vespera.db`, is served, whatever `..` or percent-encoding the path holds.

`arrangement.html`'s `file:` links into the archive are neither rewritten nor intercepted here: how arrangement is shown is #561's.

### 7. Every response

Every response, refusals and redirects included, carries four headers, with these values:

| Header | Value | Why |
| --- | --- | --- |
| `Cache-Control` | `no-store` | the browser's cache keeps no page, so no document text, outside the working directory |
| `Referrer-Policy` | `no-referrer` | the token, while it is in the address, is sent in no `Referer` |
| `X-Content-Type-Options` | `nosniff` | a body is read only as the type it is sent as |
| `Content-Security-Policy` | `frame-ancestors 'none'; form-action 'self'; base-uri 'none'` | no other site frames a page, a form posts only to the control panel, and no page's base is moved |

A page may send a second `Content-Security-Policy` of its own (#561, and #554's strict one); a browser enforces both.

### 8. Document text and the token

The control panel holds text of documents only in memory, while it answers a request, and writes no file holding text of a document or the token. The server's classes write no file, log nothing, print nothing and start no process.

The control panel writes no file in a folder other local users can write, and checks the ownership of any folder it reuses: a shared, sticky temporary folder may be the parent of a folder only its owner can write, and is never the folder a file is written in. The one file it keeps is ADR-292's (#564, being decided): its launch values, at `<java.io.tmpdir>/vespera-control-panel-<user.name>/launch-values.properties`, the folder `0700` and the file `0600` on POSIX; a folder there that the current user does not own, or that its group or other users can write, is neither read nor written, and the values count as missing. That file holds neither the token nor text of a document.

### Known limits

- A user with administrator or root rights, or another process of the operator's own, can read the token from the control panel's memory or from the browser's profile.
- A browser sends a cookie of `127.0.0.1` to every port of `127.0.0.1`. A server another local user runs on another port there receives the control panel's cookie if the operator browses to it in the same browser while the control panel's start lasts.
- Traffic on loopback is not encrypted. Only an administrator can read it.

### What does not change

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves. `ControlPanel.main` stays as ADR-288 left it until #561 starts the server from it. No dependency and no plugin is added.

The types this asks for are `ControlPanelServer`, in `io.algernon.controlpanel`: a final class, `AutoCloseable`, started by `static ControlPanelServer start(Map<String, HttpHandler> pages) throws IOException`, with `InetSocketAddress address()`, the address it is bound to, `String token()`, `URI firstAddress()`, the address of §2, and `close()`, which stops it at once, a page still answering or not, and the constants `COOKIE`, `vespera-control-panel`, and `REFUSAL`, §4's line. The checks of §2, §3 and §8 that read the compiled classes read `ControlPanelServer` and its nested classes, so the token and the checks live there.

## Alternatives refused

- **A fixed port, or one the operator configures.** It can collide with another program, and another local user can listen on it before the control panel starts and be the one the operator's browser reaches. A configured one is a value to remember (#564).
- **Binding `localhost` by name.** The name resolves through the system, and may give `::1`, both addresses, or, on a machine whose resolution is changed, an address that is not loopback.
- **The token in every address in place of a cookie.** Every link and form carries it, so it lands in the browser's history and in any address the operator copies.
- **HTTPS with a self-signed certificate.** A warning for the operator to click through at each start, and a key to keep somewhere, against traffic only an administrator can read.
- **Opening the browser itself.** `java.desktop` is not one of ADR-285 §3's modules, and `cmd /c start` was refused by ADR-266.

## Consequences

- The operator opens one address the control panel prints, once per start, and every tab opened on it works until the browser closes or the control panel stops.
- Another machine cannot reach the control panel, another local user cannot get past it without the token, and another website in the operator's browser can neither read a page nor post a form.
- A page of the control panel is never in the browser's cache.
- A restarted control panel has a new port and a new token, so an old tab is refused with the one line of §4.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.ControlPanelServerTest.itListensOn127001AloneAtAPortTheSystemPicked` | bound to `127.0.0.1`, at a port that is not 0, and no other address of the machine, `::1` included, accepts a connection on it (§1) |
| `controlpanel.ControlPanelServerTest.eachStartDrawsATokenOf32RandomBytesWrittenAsBase64Url` | 43 characters of base64url that decode to 32 bytes, another at a second start, and the first address with it (§2) |
| `controlpanel.ControlPanelServerTest.theTokenIsDrawnFromSecureRandomAndComparedInConstantTime` | the server's classes call `SecureRandom` and `MessageDigest.isEqual` (§2, §3) |
| `controlpanel.ControlPanelServerTest.aRequestWithNeitherTheCookieNorTheTokenIsRefusedAndNoPageRuns` | 403, the line, plain text, no `Set-Cookie`, no handler run, with no cookie and with a cookie of another value (§4) |
| `controlpanel.ControlPanelServerTest.aWrongTokenIsRefusedAndSetsNoCookie` | 403, no `Set-Cookie`, no handler run (§3, §4) |
| `controlpanel.ControlPanelServerTest.theRightTokenIsExchangedForACookieTheBrowserDropsWhenItCloses` | 303 to the path without the query, the `Set-Cookie` whole, no handler run; then the cookie alone, or among others, gets the page, and the token is exchanged again (§3) |
| `controlpanel.ControlPanelServerTest.aRequestNotAddressedToTheControlPanelsOwnAddressIsRefusedEvenWithTheCookie` | a foreign `Host`, another port, no port, `[::1]` and no `Host`: 403 with the cookie or the token; `127.0.0.1:<port>` and `localhost:<port>` in either case: the page (§4) |
| `controlpanel.ControlPanelServerTest.aPostReachesItsPageOnlyFromTheControlPanelsOwnOrigin` | no `Origin`, `null`, another site's, another scheme's and another port's: 403, no handler run; both of the control panel's own: the handler runs (§5) |
| `controlpanel.ControlPanelServerTest.aMethodOtherThanGetHeadAndPostIsRefusedNamingTheThree` | `PUT`, `DELETE`, `PATCH` and `OPTIONS`: 405 with `Allow: GET, HEAD, POST`, no handler run; `HEAD`: 200 (§5) |
| `controlpanel.ControlPanelServerTest.aPathNoPageIsStartedWithIsNotFoundAndNoFileIsServed` | an unknown path, a page's path with a trailing slash, `/vespera.db`, `/../vespera.db`, `/%2e%2e/` and `/%2e%2e/vespera.db`: 404, no handler run (§6) |
| `controlpanel.ControlPanelServerTest.everyResponseCarriesTheFourHeadersThatKeepItOutOfTheCacheTheRefererAndAFrame` | a 403 of each check, a 303, a 200, a 404 and a 405 each carry §7's four headers, once each, with their values (§7) |
| `controlpanel.ControlPanelServerTest.theServerWritesNoFileLogsNothingPrintsNothingAndStartsNoProcess` | the server's classes name no type that writes a file, logs or starts a process, and read neither `System.out` nor `System.err` (§2, §8) |
| `controlpanel.ControlPanelServerTest.closeStopsTheServerAtOnce` | with a page's handler still answering a request, `close` returns within a second, and its port accepts no connection after (§1) |

## What this does not decide

- **Which pages exist, and their own `Content-Security-Policy` beyond §7's** (#561).
- **Starting the server from `ControlPanel.main`, and printing the address of §2** (#561).
- **How arrangement is shown, its `file:` links included** (#561).
- **Remembering values between starts, and the file they are kept in** (#564, ADR-292). The port and the token are never remembered.
