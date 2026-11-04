# ADR-341 — `vespera serve` tells a page from a copy by the website's list and the file's name, `serve`'s working directory is a fourth argument never passed, and its refusals, answers and readings are as built

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: here. Each choice is one that building ADR-333's third step needed, which ADR-333's Decided line leaves to its building; none is a ruling of the operator's. §1 settles a gap between ADR-333 §8 and ADR-339 §3 by the reading that keeps ADR-339's list at its format and fields.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8: what `vespera-website.json` "does not list as a `page`, `asset` or `listing`" is read from that list and the file's name together (§1); its refusals gain a value of the port that is no port and a failure to listen that is not a port in use (§3); its reading of the three sources holds a blank value unset and finds the environment's names as Spring relaxes them (§4, §5); and its answers are as §6 and §7 have them.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §2, whose "the three entries with no control the three above" are four, `serve db-dir` the fourth, and its Tests row on the jar's side, which holds four (§2).
- **Amends**: [ADR-339](0339-an-argument-of-the-control-panels-table-names-the-form-its-control-stands-on-six-members-of-cluster-page-are-public-for-the-website-and-its-lists-stops-and-pages-are-as-recorded.md) §1, whose "The three entries with no control stay ADR-336's three" reads "stay ADR-336's three, and `serve db-dir`" (§2).
- **Keeps**: ADR-339 §3: `vespera-website.json` stays `{"format": 1, "files": [{"path", "sha256"}]}`, each entry those two fields and no other. ADR-333 §8's ruling: loopback alone, no login, no Spring context, no lock. [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1 as ADR-333 amended it: the control panel starts no `serve`. [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1: the launch values stay seven. ADR-333 §14: no run id moves, since `StageModules` lists no `pipeline`, `deliverable`, `confluence` or `website` for any stage, and nothing here touches `synthesis`, `extraction` or `embedding`; no profile key is added.

## Context

**Two records on one list.** ADR-333 §8 sandboxes every file that `vespera-website.json` "does not list as a `page`, `asset` or `listing`". ADR-339 §3, written with the second step, gives that file `{path, sha256}` and no `kind`: the kinds stay in the rendering's `files.json`, under the tree, which `vespera serve` does not read. A copy is listed in `vespera-website.json` as a page is, since a publish removes later only what it listed (ADR-333 §7). So the list alone cannot say which listed file is a page.

**A fourth argument of the jar.** ADR-333 §8 keeps `serve` an entry of `VesperaCommand`'s picocli tree, so that help lists it, and gives it `--db-dir` through `WorkingDirectoryOption`. ADR-336 §2's rule for a command added with arguments of its own puts that option in `ChildCommand.ARGUMENTS`, and ADR-278 §1 as ADR-333 amended it has the control panel start no `serve`. So the option is in the table with no control, where ADR-336 §2 and ADR-339 §1 say the entries with no control are three.

**What building the server settled.** ADR-333 §8 names three refusals, the checks on `Host` and `Sec-Fetch-Site`, the sandbox and the three sources, and leaves to the build what a value of the port that is no port does, what arguments `ServeCommand` cannot read do, how a blank value and the environment's spelling are read, what a folder asked without its slash and a method refused are answered, and when the list is read. By `docs/adr/README.md`, what a build settles beyond a record is stated by a later one.

## Decision

### 1. A page, an asset or the listing is a file the list names under a name the website draws

**A file of the website is one of its pages, its assets or its listing, and so answered with no `Content-Security-Policy: sandbox`, where `vespera-website.json` names its path and its name is one the website draws**: `index.html` or `index-<digits>.html`, in any folder, the digits one or more of `0` to `9`; or, at the folder's top alone, `search-index.js`, `website.js`, `website.css` or `documents.csv`. Every other file is sandboxed: each copy, a file listed under another name or an asset's name below the top, and whatever the operator put there, listed or not.

This is sound because a copy can never stand at a page's name. `CopyNames` reserves `index.html` and `index-<digits>.html` in every cluster folder, compared by its key, so a survivor of that name takes a suffix (ADR-280 §1.5, as ADR-286 amended it). The only folders that skip the reservation are a code set's (ADR-296), and ADR-333 §10 keeps code off the website. A copy lies in a cluster folder, never at the top, so the four names at the top are the website's own. A file of the operator's at a name the list names is replaced by a publish (ADR-333 §7), so that the name is the website's again.

`vespera-website.json` keeps ADR-339 §3's format and fields. `kind` is not added to it: the name says what `kind` would, the list would be at a second format for no reader but `vespera serve`, and the case in which they could differ cannot be drawn.

### 2. `serve db-dir` is a fourth argument never passed

**`ChildCommand.ARGUMENTS` holds `serve`'s `db-dir` with no form and no control**, its reason that the control panel starts no `vespera serve`, a reader's server being a command the operator starts and ends (ADR-333 §8, ADR-278 §1). The entries with no control are four: `vespera help`, `vespera version`, `label <file>` and `serve db-dir`. ADR-336 §2's "the three entries with no control the three above" and ADR-339 §1's "The three entries with no control stay ADR-336's three" read so. `--db-dir` stays a control of no form, so the launch form keeps its seven values (ADR-292 §1).

### 3. A value of the port that is no port is refused, and so is any other failure to listen

- **A value of `vespera.serve.port` that is no whole number from 1 to 65535, `0` among them, is refused**, exit 1, making nothing, after the working directory and its website are found: `vespera serve could not listen on port <value>: it is no port number from 1 to 65535. Name another in vespera.serve.port.`, `<value>` as read, its white space stripped. `0` is refused because the system would pick the port, which ADR-333's Decided 10 refuses.
- **A failure to listen that is not a port in use**, any `IOException` but a `BindException` out of starting the server, is refused, exit 1: `vespera serve could not listen on port <port>: <Type>. Name another in vespera.serve.port.`, `<Type>` the exception's simple name and never its message (ADR-274 §8).

### 4. Arguments it cannot read exit 2, and a blank value is unset

- **Arguments picocli cannot read into `serve`**, a `--db-dir` given no path among them, end the command with exit 2, picocli's line on standard error, before anything else is read. An argument `serve` does not declare, `--vespera.serve.port=<port>` among them, is let through to the reading of §5.
- **A blank value is unset**, in each of the three sources: an argument `--<name>=` with nothing or white space after it, a system property and an environment variable alike. The next source is read, then the default. This is where `ServeCommand` reads otherwise than Spring, which would take the empty text as the value; `vespera serve` would then serve the folder it was started in, or refuse a port of no number.

### 5. The environment's names are Spring's relaxed ones

**An environment variable is found under the property's name, with `.` as `_`, `-` as `_`, both, each as written and in upper case**, the first of those eight present winning: `VESPERA_WORKING_DIR`, `VESPERA_SERVE_PORT` and `DB_DIR` are the names Spring's `SystemEnvironmentPropertySource` finds too.

### 6. What it answers beyond ADR-333 §8

- **A folder asked without its closing slash** is answered `301`, `Location` the path as asked with `/` added, and none of its index, so that the page's relative links resolve beneath it.
- **A foreign `Host`**, or none, and a `Sec-Fetch-Site` of `cross-site` or `same-site`, are answered `403`, as the control panel answers them (ADR-291 §4, ADR-302 §12).
- **A method but `GET` and `HEAD`** is answered `405`, with `Allow: GET, HEAD`.
- **A failure while answering**, an `IOException` or `RuntimeException`, is answered `500` in one line of ours naming no path, unless the answer of the file asked had begun, its headers set, in which case the connection is closed with no answer of ours.
- **Every answer of ours**, a refusal, a 404 and a 500 alike, is one line of `text/plain`, naming no path, and carries `X-Content-Type-Options: nosniff`.

### 7. The list is read again whenever it changes

**`vespera-website.json` is read again, on the next request that asks whether a file is listed, whenever its modification time, to the millisecond, or its size has changed** since the server last read it. So a page a publish lists while the website is served leaves the sandbox, and a file it drops is sandboxed again, with no restart. An unreadable list lists nothing, so every file is sandboxed until it can be read.

## Known limits

- A list rewritten at the same size within the millisecond the server last read it at is not read again until it next changes, so a file that writing lists or drops keeps its earlier answer until then.
- A `500` and a failure to listen that is not a port in use are held by no test: neither can be brought about from a test without a file system or a network stack made to fail (Tests).
- §1 rests on ADR-280 §1.5's reservation and ADR-333 §10. A later record that put a copy at a website's top, or code on the website, would have to give the list a `kind`.
- §4's blank value is read otherwise than Spring reads it, so the shipped line `${db-dir:${user.dir}/.vespera}` and `vespera serve` part where a source holds a blank value: `vespera serve` takes the next.

## Alternatives refused

- **`kind` added to `vespera-website.json`.** A second format of a list ADR-339 §3 has just settled, `TheWebsitesListsHoldTheirFormatAndFieldsTest` changed, and a field whose every value the file's name already holds.
- **`vespera serve` reading the rendering's `files.json`.** That lies under the tree of a generation run, which `vespera serve` would have to find in a database it does not open (ADR-333 §8).
- **The list alone, every listed file unsandboxed.** Every copy is listed, so a document that is HTML would run its script in the website's origin, which ADR-333 §8 refuses.
- **`serve`'s `--db-dir` left out of the table.** ADR-336 §2 holds the table equal to the tree, and the tree holds `serve` so that help lists it.
- **A port of `0` taken as the system's pick.** The address would change with every start, which ADR-333's Decided 10 refuses.

## Tests

| Method | What it holds |
| --- | --- |
| `website.ServeAnswersOnLoopbackAloneTest.aListedFileLeavesTheSandboxOnlyUnderANameTheWebsiteDraws` | every file listed: a page's name in any folder and the four names at the top unsandboxed; another name, `index-.html`, and an asset's or the listing's name below the top sandboxed (§1) |
| `website.ServeAnswersOnLoopbackAloneTest.aCopyAndTheOperatorsFilesRunNoScript` | on a published website, a copy, which the list names, sandboxed, and a page, an asset and the listing not (§1) |
| `deliverable.CopyNamesTest.aCopyYieldsToThePagesNamesAndTheirPartsAndNotToAPicturesName` | the reservation §1 rests on: a survivor at a page's name takes a suffix (ADR-280 §1.5) |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy` | `serve db-dir` in the model and in the table, and the four entries with no control, and no other (§2) |
| `pipeline.ServeCommandTest.aPortValueThatIsNoPortIsRefused` | `0`, `65536` and `abc`, from each of the three sources, refused, exit 1, the whole line, nothing on standard output, nothing made (§3) |
| `pipeline.ServeCommandTest.aPortInUseIsRefused` | ADR-333 §8's refusal of a port in use, beside §3's |
| `pipeline.ServeCommandTest.argumentsItCannotReadEndItWithExitTwo` | `--db-dir` given no path: exit 2, one line naming the option, though a website is there to serve (§4) |
| `pipeline.ServeCommandTest.aBlankValueIsUnsetInEverySource` | a blank argument gives way to a system property, a blank system property to the environment, blank in all three the default, for the port and for the working directory (§4) |
| `pipeline.ServeCommandTest.theWorkingDirectoryResolvesAsTheShippedLineDoes` | `DB_DIR` and `VESPERA_WORKING_DIR` in the environment found, each where Spring's reading finds it (§5) |
| `pipeline.ServeCommandTest.thePortResolvesFromTheThreeSourcesInSpringsOrder` | `VESPERA_SERVE_PORT` found, where Spring's reading finds it (§5) |
| `website.ServeAnswersOnLoopbackAloneTest.aFolderAskedWithoutItsSlashIsMovedToIt` | `301`, `Location` the path asked with `/`, and the body not the index the slash form answers (§6) |
| `website.ServeAnswersOnLoopbackAloneTest.onlyItsOwnHostIsAnswered` | `403` for a foreign `Host` and for none (§6) |
| `website.ServeAnswersOnLoopbackAloneTest.anotherSitesRequestIsRefused` | `403` for `cross-site` and `same-site` (§6) |
| `website.ServeAnswersOnLoopbackAloneTest.onlyGetAndHeadAreAnswered` | `405` and `Allow: GET, HEAD` for `POST`, `PUT`, `DELETE` and `PATCH` (§6) |
| `website.ServeAnswersOnLoopbackAloneTest.everyAnswerCarriesNosniff` | `nosniff` on an answered file, each refusal and a 404 (§6) |
| `website.ServeAnswersOnLoopbackAloneTest.aFileListedOrDelistedWhileServedGainsOrLosesTheSandbox` | one server running: `index.html` sandboxed unlisted, not once the list names it, sandboxed again once the list drops it (§7) |
| `pipeline.ServeCommandTest.aWorkingDirectoryThatIsNotThereIsRefusedAndNotMade` | ADR-333 §8's refusal of a working directory not there, the whole line, and the folder not made, among the unit tests, beside `ServeHoldsNoLockIT` on the packaged jar |

Held by no test: the `500` of §6 and the failure to listen of §3 that is not a port in use (Known limits).
