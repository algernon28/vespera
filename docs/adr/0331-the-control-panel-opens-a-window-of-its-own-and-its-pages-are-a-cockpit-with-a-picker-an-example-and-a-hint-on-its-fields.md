# ADR-331 — The control panel opens a window of its own, and its pages are a cockpit, with a picker, an example and a hint on its fields

> **Partly amended — see [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md).** Of §5: what a form held is let go where a save finds it no change, the file stating it already. Of §6 and its Consequences: a save of `profile.yaml` makes a working directory that is not there, on the operator's press; every start still refuses one, and the preflight stays `bad` until it exists. Of §7: Ollama is also asked at the address the jar last said, where no launch value names one. Of §8: the hint of `vespera.working-dir` says the first value saved makes it. The rest of this record stands.

- **Date**: 2026-10-31
- **Status**: accepted on 2026-10-31.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names its own package, `java.base` and `jdk.httpserver` alone, and starts the window by `ProcessBuilder`, `java.desktop` not added (§1). Its §4: one class owns the pages' skeleton, styling and escaping (§3).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2's rule that nothing is opened where `CI` is set or, off Windows and macOS, where no display is, and its refusals of `java.awt.Desktop`, of `cmd /c start` and of a switch that turns opening off; [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md): the jar's opening of its own progress page, which does not meet this record.
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §7's four headers and §8's rule that the server's classes write no file, log nothing, print nothing and start no process: the folder page is a page, outside the server's classes, and starts no process either (§5).
- **Keeps**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §1, §2, §4, §5, §6 and §7's `BrowserOpener` and `SystemBrowserOpener.command`, word for word: the port, the checks, the mark, the one script, the wait of 5 seconds for a tab. A window opened by §1 is a tab to that record: it runs the same script and comes back the same way.
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): the screens are tested through `PanelBrowser` and `RawHttp`, reading names, ids and attributes, with no JavaScript engine in the build (§4, §9).
- **Keeps**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md): every save is one of its writes, and each value keeps going through its checks (§5, §7), and §1's "The control panel creates no working directory" holds of a start too (§6). [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §2: the launch values' file is written only as an invocation starts (§5).
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md) and `CONTEXT.md`'s _Renders as_: a page's words are free of the _Avoid_ lists, the deliverable root is "Output folder" on a page, and a cluster a group (§8).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): no dependency and no plugin is added.
- **Amends**: [ADR-288](0288-the-operator-starts-the-control-panel-with-java-jar-and-it-starts-the-jar-beside-its-own-with-the-java-running-it.md) §3, whose "It reads neither `PATH` nor `JAVA_HOME`" stands of the child's `java`, which neither variable picks: `PATH` is read by `SystemBrowserOpener.windowCommand` alone, to find a browser, and `JAVA_HOME` by no class (§1); and its Tests, whose row of `ChildRuntimeTest.noClassOfTheControlPanelReadsPathOrJavaHome` is `neitherPathNorJavaHomePicksTheChildsJava`, as this record's Tests has it. Its reason, an older Java on `PATH`, is untouched by finding a browser. It carries a note at its head.
- **Amends**: ADR-291 §6, whose "No request path is ever mapped to a file" stands of files and gains what a query can name: `/folders`'s `path` names a folder whose subfolders' names alone are answered (§5); and its Known limits, which gain that any local caller can list folder names (Known limits). It carries a note at its head.
- **Amends**: ADR-295 §3, whose "leading and trailing white space removed" stands of every key but `seedFolder`, the one that names a folder, which loses its trailing white space alone (§6); and §7, whose refusals stand, in `CodeSetRenames`: a Save at `/code-sets` refuses before them a `new-folder` that climbs out of the corpus root (§5). It carries a note at its head.
- **Amends**: ADR-302 §1, where `GET /` keeps the sidecars' last answers in memory (§3, §7); §2, whose launch page is laid out by §3 here, whose post of `launch` also answers a Browse button (§5) and holds a folder as §6 has it, and whose starts gain the refusal of a folder the system cannot hold and of a working directory that is no folder (§6); §4, whose `seed-folder` form gains a Browse button and has what it holds kept for it (§5); §5, whose Ollama is asked wherever its address is named, and whose answers are kept for the other pages (§7); §6, whose words give GiB (§6); §8, whose controls of `/next` and `/profile` are §7's, and `/profile`'s forms a list and sections (§3); §9, whose table gains §9's; §11, whose line is §2's; and its What this does not decide, whose layout is decided here in what §3 names, its words and styling still no decision's but for §3's `<title>`. It carries a note at its head.
- **Amends**: ADR-325 §3, whose line is §2's; §7, whose opener starts a window where it finds a browser, and ADR-325 §7's command otherwise (§1); §8, whose "by the same commands" holds where no browser is found; and its Consequences' "the browser opens on it". It carries a note at its head.
- **Settles**: that the control panel opens a window of its own, that its pages take the layout of §3, that a field offers a picker wherever one can be written, an example and a hint, and that no script is added, a native dialog not carried and no slider drawn, are the operator's; the rest of §1 to §11 is decided under the operator's delegation of 2026-10-31.

## Context

The **control panel** (ADR-285) serves pages of server-written forms on `127.0.0.1` (ADR-302, ADR-325) and opens the system's browser on its root. Five things are wanted of it: that it opens by itself in something that looks like a program of its own; a friendlier look; a picker wherever one can be written, in place of a path or a value typed from memory; a real example and a plain hint on each field; and a printed line that does not read as an order to the operator to open a browser.

**What a Chromium browser does.** Chrome, Edge and Chromium take `--app=<address>` and open the address in a window with no address bar, by their published switches. Such a window is a page of the browser, with the browser's own engine, so it runs the control panel's one script and sends the headers its checks read as any tab does; ADR-325 §4 does not tell the two apart.

**What is measured of it.** On Windows 11, OpenJDK 26.0.2.1 and Microsoft Edge, with `CI` not set, the packaged control panel started `msedge.exe --app=http://127.0.0.1:<port>/`, and a window opened with no address bar. With that window left open, the control panel, ended and started again at the same port, wrote the waiting line of ADR-325 §3, then the line of a tab come back, and opened no second window. Chrome and Chromium, macOS and Linux, and where a report's link opens from such a window are not measured (Known limits).

**What can be tested.** The build has no JavaScript engine (ADR-297 §1), and none is to be added (ADR-046). A picker a script draws could be tested by nothing. ADR-325 §4.2's one script writes nothing into a page and is to go on so. What a browser draws from an HTML control, a `<datalist>`, a `<select>` or `type=number`, is the specification's, and a test holds what the page writes.

**What the fields want.** `ProfileEdit` checks each value as the jar reads it, whatever a browser let through (ADR-295 §4). None of the thresholds has a range anyone decided: `README.md` says "It never guesses a threshold", and a slider's bounds would be a guess at one.

**What a start needs of a working directory.** The working directory is the child's current directory (ADR-278 §1), and the control panel creates none (ADR-295 §1). Measured on Windows 11 and OpenJDK 26.0.2.1, `ProcessBuilder` given a current directory that is not there throws, `Cannot run program ... CreateProcess error=267`, so the jar, which would create it, is never reached. A deliverable root differs: the child is given it, and the jar creates it.

**The README's table of values** says, for each profile key, what its value is read from, and a test already holds its reports to the page's (ADR-302 §8). The jar's default Docling image is in `application.yaml`, and the GPU image in `compose.gpu.yaml` (ADR-147, ADR-170).

## Decision

### 1. The window

The start asks the opener once, as ADR-325 §3 has it. `SystemBrowserOpener.open` starts what one function chooses, by `ProcessBuilder`, does not wait, discards its output and carries ADR-325 §7's failure line:

```java
static Optional<List<String>> windowCommand(URI address, String osName, Map<String, String> environment, Predicate<Path> exists)
```

It is empty wherever `SystemBrowserOpener.command` is empty, a browser found or not, so `CI` and no display open nothing as ADR-266 §2 has it. Otherwise it gives the first browser `exists` accepts, in this order:

| `os.name` | Looked for, in order | Command |
| --- | --- | --- |
| starts with `Windows` | under `ProgramFiles(x86)`, then `ProgramFiles`: `Microsoft/Edge/Application/msedge.exe`, then `Google/Chrome/Application/chrome.exe`; then under `LOCALAPPDATA`: `Google/Chrome/Application/chrome.exe` | `<path> --app=<address>` |
| starts with `Mac` | `/Applications/Microsoft Edge.app`, then `/Applications/Google Chrome.app` | `open -na "Microsoft Edge"`, or `"Google Chrome"`, `--args --app=<address>` |
| any other | `chromium`, `chromium-browser`, `google-chrome`, then `microsoft-edge`, each in the folders of `PATH` in `PATH`'s order, split at `:` | `<path> --app=<address>` |

A variable absent or blank names no folder, and nothing is looked for under a relative path: a folder of `PATH` that does not begin with `/`, and a `ProgramFiles(x86)`, `ProgramFiles` or `LOCALAPPDATA` that does not begin with a drive letter, a colon and a separator, name no folder. A network share, `\\host\share`, begins with none of these, so it names no folder either, and no host is asked whether a browser lies on it. A `ProgramFiles(x86)`, `ProgramFiles` or `LOCALAPPDATA` holding a character `Path.of` refuses names no folder either: nothing is thrown out of `windowCommand`, the other two are read as ever, and with no browser found it gives what `command` gives, so that the default browser still opens. Their text is read, and not the running system's `Path.isAbsolute`, so that the choice is a function of what it is handed. On Windows the three names are matched ignoring case, on that branch alone: Windows holds one variable of a name whatever its case, and `System.getenv()` is a map that does not, which, measured on Windows 11 and OpenJDK 26.0.2.1 started from Git Bash, holds `PROGRAMFILES` and no `ProgramFiles`. Two spellings of one name are therefore not met, and this record chooses between none. `PATH` is matched exactly, as `command`'s names are. `PATH` is read here alone, to find a browser, and picks no `java` (ADR-288 §3). Where none is accepted, it gives what `command` gives. `command` itself stays as ADR-325 §7 has it, and `pipeline.TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest` goes on holding it to the jar's; `open` passes `System.getProperty("os.name")`, `System.getenv()` and `Files::exists`.

A browser that is found and cannot be started is ADR-325 §7's failure, one line on standard error, with no second try: the address is on the line of §2.

### 2. The line

The line of ADR-325 §3's fourth step is

```
opening the control panel; if nothing appears, go to http://127.0.0.1:<port>/ in your browser
```

printed before the opener is asked, as before, flushed, and the same whether a window, a tab or nothing is opened. The root's address stays on it for the operator and for what reads it. The other lines of ADR-325 §3 stand.

### 3. The layout

Every page is written through `ControlPanelPage`, which is its only owner (ADR-285 §4), with no framework, no dependency, and no font, style or picture fetched: its `<style>` names no `url(`, `@import` or `@font-face`, and a page holds no `link`, `img` or other element that fetches anything, its one script apart. Every `id`, `data-` attribute, form name and control name of ADR-302 §9 and ADR-325 is kept where its tests read it: what follows is added.

**Every page.** `#sidebar` holds a `nav` of the six pages in this order, `/` Launch, `/invocation` Invocation, `/next` Next, `/labels` Questions, `/code-sets` Code sets and `/profile` Profile, the page's own carrying `aria-current="page"`; it replaces the top navigation, and no page holds another `nav`. A page that is none of the six, `/stop` and `/folders`, marks none. `#top-row`, kept in view by `position: sticky`, holds the page's one `h1`, which reads as the sidebar names the page. The page's `<title>` is the `h1`'s words and ` · Vespera`, so that a window with no address bar and the taskbar name the program.

**The launch page.** The form `launch` wraps `#top-row`, so that `run`, `label` and `check` stand in it and are its buttons, with no `form` attribute, which ADR-297's browser does not read; it takes no box of its own, `display: contents`, so that the top row sticks to the page and its cards stand in the page's grid; a page with no such buttons writes `#top-row` alone. Then:

- `#tiles`, four elements in this order, each with `data-tile` and `data-state`, one of `ok`, `warn`, `bad` and `idle`:

  | `data-tile` | Holds | `data-state` |
  | --- | --- | --- |
  | `docling` | `#docling-check` | `matches` is `ok`; `another-image` and `not-answering` `warn`; `not-checked` `idle` |
  | `ollama` | `#ollama-check` | `serves-all` is `ok`; `missing` and `not-answering` `warn`; `not-checked` `idle` |
  | `free-space` | the GiB free on the working directory's drive (§6) | `idle`, the control panel computing no need (ADR-302 §6) |
  | `invocation` | how the child is, with `data-ending` as `#child` has it | `none` is `idle`; `running` and `stopped` `warn`; `ended` `ok`; `failed-or-refused` and `defect` `bad` |

- five cards, each with `id` and `data-column`, in this order: `card-folders` `left`, holding `vespera.corpus-root`, `vespera.working-dir` and `vespera.deliverable-root`; `card-services` `left`, holding the other four launch values; `card-seed-folder` `left`, holding the form `seed-folder`; `card-preflight` `right`, holding `#preflight`, `#warnings`, `#holder-working-directory` and `#holder-corpus-root`; and `card-reports` `right`, holding `#reports`. The two forms cannot nest, so `seed-folder` stands after `launch` closes and a card's column is the style's, read off `data-column`. `#free-space`, `#free-space-rules` and the progress link are placed by the implementation.

**The preflight.** `#preflight` holds five rows, each with `data-row` and `data-state`, computed from what the page already reads. It writes no line of its own into `#warnings`, whose lines stay as ADR-302 §2 has them, and it disables nothing: Run stays as ADR-302 §2 and §5 have it, its refusals its own.

| `data-row` | `bad` | `warn` | `ok` |
| --- | --- | --- | --- |
| `corpus-root` | `not-chosen` or `no-such-folder` (§6) | | `found` |
| `working-directory` | `not-chosen` or `no-such-folder` | | `found` |
| `docling` | | `another-image`, `not-answering`, `not-checked` | `matches` |
| `ollama` | | `missing`, `not-answering`, `not-checked` | `serves-all` |
| `locks` | a holder line held, or the control panel's own child running | | neither |

**The invocation page.** It holds the same `#tiles` and the output. `#child` keeps `data-ending` and is the status pill, with `data-state` as the `invocation` tile has it.

**The tiles beyond the launch page.** Only `/` asks the sidecars, as ADR-302 §5 has it: `ControlPanelState` keeps its last answers, which `/invocation` shows, and `not-checked` where `/` has asked nothing. The invocation page reloads every 2 seconds while the child runs, and each check can take 2 seconds where nobody answers.

**The profile page.** `#profile-list` links each key the page holds a form for, in `ProfileKey`'s order, to `#key-<key>`, a section holding that key's form, the key's current value shown in the link. Every form stands in the page, and the style shows the section `:target` names and the first where none is named, so `ProfileScreenTest`'s reads hold unchanged, with no script and no query. A save answers `303` to `/profile#key-<key>`, so the section saved stays open.

**A narrow window.** A rule of `max-width` sets each grid to one column; no element is wider than the window, a long path wrapping with `overflow-wrap: anywhere`.

**Light and dark.** Both schemes are set by `prefers-color-scheme` and name the same colours:

| Colour | Light | Dark | Colour | Light | Dark |
| --- | --- | --- | --- | --- | --- |
| `--bg` | `#f5f4f1` | `#14121d` | `--sun` | `#e0752c` | `#f0914f` |
| `--surface` | `#ffffff` | `#1e1b2b` | `--sun-ink` | `#fff` | `#231003` |
| `--surface2` | `#efedf3` | `#272338` | `--ok` | `#17794f` | `#5fd6a0` |
| `--ink` | `#1e1b2e` | `#ece9f6` | `--ok-soft` | `#dcf3e7` | `#173428` |
| `--dim` | `#67637a` | `#a29db8` | `--warn` | `#9a5b00` | `#f0b45a` |
| `--rule` | `#dfdce6` | `#352f49` | `--warn-soft` | `#fbeed4` | `#3a2b12` |
| `--accent` | `#4c3fb1` | `#a99cff` | `--bad` | `#b3282d` | `#ff8c8c` |
| `--accent-ink` | `#fff` | `#17132b` | `--bad-soft` | `#fbe3e3` | `#3f1d20` |
| `--accent-soft` | `#e9e6f9` | `#2c2750` | | | |

The words of a page and its styling, its `<title>` apart, stay no decision's (ADR-302, What this does not decide): a test holds that both schemes name the same colours, and none of these values. A colour's name is a name of ours, so none is "panel" alone (ADR-122, `CONTEXT.md`).

### 4. No new script

Every picker is a server-written page or a control a browser draws. No event attribute is written and no second script: `EveryPageHoldsOnlyTheControlPanelsScriptTest` stands, and ADR-325 §4.2's script writes nothing into a page. What is not one of these is CSS, the profile's sections among it.

### 5. The folder page

**Browse.** Beside each folder field stands a submit button, `browse-<for>`, with `formnovalidate`, since each of these forms but `launch` holds a required provenance a browser would otherwise stop it for. It posts its form to the form's own path. That path writes no file: it holds what was typed and answers `303` to `/folders?for=<for>&path=<what the field held>`. The launch values are held as `launch`'s post holds them with neither `run` nor `label` (ADR-302 §2). What any other form held, `ControlPanelState` keeps in memory by form until that form is saved, and its page writes it back.

**The closed list.** `for` is one of seven, and each names one field, and the page returned to:

| `for` | The form, its path | Its field | The page returned to |
| --- | --- | --- | --- |
| `vespera.corpus-root` | `launch`, `/launch` | `vespera.corpus-root` | `/` |
| `vespera.working-dir` | `launch`, `/launch` | `vespera.working-dir` | `/` |
| `vespera.deliverable-root` | `launch`, `/launch` | `vespera.deliverable-root` | `/` |
| `seedFolder` | `seed-folder`, `/seed-folder` | `seedFolder` | `/` |
| `next-seedFolder` | `next`, `/next` | `seedFolder` | `/next` |
| `profile-seedFolder` | `seedFolder`, `/profile` | `value` | `/profile#key-seedFolder` |
| `new-folder` | `code-sets`, `/code-sets` | `new-folder` | `/code-sets` |

A `GET` or a `POST` naming any other `for`, a launch value that is no folder among them, is answered 404, `text/plain`, with one line that repeats nothing of it, and changes nothing. A `GET` with no `for` answers `303` to `/`, as a path a form posts to does (ADR-302 §9).

**The page.** `/folders` is a path the server is started with, past ADR-325 §2's checks as every page is, written through `ControlPanelPage`. `#folder-listing` carries `data-listing` and `data-path` and says the state in words:

| `data-listing` | When | What else the page holds |
| --- | --- | --- |
| `listed` | the folder could be read | `#folder-list`, `#folder-up` where it has a parent, the form `use-folder` |
| `roots` | no `path`, or an empty one, for any field but `new-folder` | no list, no `use-folder` |
| `no-such-folder` | not a folder: nothing there, a file, or a path the system cannot hold | `#folder-up` where it has a parent, no `use-folder` |
| `cannot-be-listed` | a folder that cannot be listed | `#folder-up`, no `use-folder` |
| `outside-the-corpus-root` | for `new-folder` alone, below | no list, no `use-folder` |
| `no-corpus-root` | for `new-folder` alone, no corpus root named | no list, no `use-folder` |

On every page, `#folder-roots` links each of `FileSystems.getDefault().getRootDirectories()`, in its order, and `#folder-back` the page returned to. `data-path` is the folder absolute and normalized.

`#folder-list` holds a link per subfolder, its text the name alone, in name order without regard to case, its query carrying the folder whole, whatever its name holds. **No file's name or content is listed or read**: a directory stream's entries that are not folders are dropped, and nothing is opened.

**Use this folder.** The form `use-folder`, `POST` to `/folders`, holds hidden `for` and `path` and one button, `use`. It puts the value in the field of the closed list, in memory, writes no file, and answers `303` to the page returned to: the launch values are held, and the file of ADR-292 §2 is written only as an invocation starts; any other field is kept with its form, and is written by that form's Save. A save writes what it holds, and the form's held values are let go only once it is written: a save refused, by `ProfileEdit`, by `CodeSetRenames` or by a `new-folder` that climbs out (below), answers its one line in `outcome` and keeps them, so its page writes back what was chosen and typed.

**No `GET` writes**, as ADR-302 §1 has it: `/folders` changes no value and no file.

**A code set's folder** is relative to the corpus root and may not climb out of it. For `new-folder`, `path` is written as `code-set-names.yaml` wants it (ADR-295 §7), with `/` between its elements and none where it is the corpus root, which is where the page begins; `data-path` and `use-folder` hold it so; `#folder-roots` links the corpus root alone; and `#folder-up` stands only below the corpus root. A path that is absolute, holds a `..` element or a backslash, or whose real path, links followed, does not lie under the corpus root's, is `outside-the-corpus-root`, on a `GET`, and on a `POST` is refused with one line in `outcome` on `/code-sets`, the field unchanged.

**A folder typed can climb out too**, or be left in the field by Browse and Back, so Save holds the same line by its text. A `POST` of `save` to `/code-sets` whose `new-folder` is absolute, by a leading `/` or a drive letter and a colon, holds a `..` element or holds a backslash is refused with one line in `outcome`, and nothing is written, whether a corpus root is named or not. Its text alone is read, since a folder typed need not be there yet, and read after any white space before it, for this check alone, so that ` ../x` and ` C:\x` as pasted are refused too. The check stands in that path's post and not in `CodeSetRenames.render`, whose refusals ADR-295's contract test pairs with the jar's `CodeSetNames`, which §11 leaves unchanged. It reads `new-folder` alone: a saved row's folder may no longer be there, or may have been written by hand, so the page posts it back in `folder-<i>` as the file holds it, and Save writes what the post holds (Known limits). A Browse holds what the field held, whatever it holds (§6).

**Enter.** A form's first submit button is the one Enter presses, so a form whose Browse stands before its Save lands on the folder page, writing nothing (Known limits).

### 6. A folder field's status

Beside each folder field, `#status-<for>` carries `data-status`, read as the page is asked for:

| `data-status` | The field holds | For |
| --- | --- | --- |
| `not-chosen` | nothing | all |
| `found` | a folder that exists, the words giving its drive's free space as `<x.y> GiB` | all |
| `will-be-created` | nothing there | `vespera.deliverable-root`, which the jar creates |
| `no-such-folder` | nothing there | `vespera.corpus-root`, `vespera.working-dir`, the seed folder's three, `new-folder` |
| `no-such-folder` | anything that is not a folder, a file, or a path the system cannot hold | all |

`new-folder` is read under the corpus root, and `no-such-folder` where none is named or it climbs out. Free space is shown in GiB, to one decimal, `usable bytes / 2^30`, here and on `#free-space`, whose items keep `data-usable-bytes` (ADR-302 §6) and hold no count of bytes in their words.

**A folder's name is held whole.** Each of §5's seven fields keeps the white space before what it holds and loses only the white space after it: as its form is posted, as Browse carries it to the folder page, as its status reads it, as a start reads it and as a save writes it. A corpus root of ` x` is not `x`, another folder. A space before a name is part of a name every system can hold; Windows can hold no name ending in a space, and a paste commonly leaves one. So a path pasted with a space before it shows beside its field: ` C:\x` on Windows is a path the system cannot hold, below, and a ` x` that is not there is what the table has for nothing there, `no-such-folder`, or `will-be-created` in the one field the jar creates. `seedFolder`'s Save refuses it on every system, ADR-295 §4 reading the absolute path of a folder that exists. Every other field of a page loses white space at both ends, as before.

**A path the system cannot hold** is one `Path.of` refuses: on Windows one pasted with its quotes, as "Copy as path" gives it, and on every system one holding a NUL, as `not\0a-path` does, which is what the tests use. Every page goes on answering whichever field holds it, by Browse or by Check. Its status is `no-such-folder`, and its row of §3's preflight, where it has one, `bad`. A working directory so held is, to every page and every save that reads one, a working directory not named, and a report is not found (ADR-302 §3), and a corpus root so held is held by nobody. Nothing is written for it.

**Every start refuses it**, in one line, a fourth case of ADR-302 §2's table of lines, `<Run>` being the start's name, as in `Publish is not started: the corpus root is no path this system can hold`: `Run` for `run` and `save-then-run`, `Label` for `label`, `save-then-label` and `label --auto`, and `Publish` for `publish`.

| The value that cannot be held, the first in this order | Line |
| --- | --- |
| `vespera.corpus-root` | `<Run> is not started: the corpus root is no path this system can hold` |
| `vespera.working-dir` | `<Run> is not started: the working directory is no path this system can hold` |
| `vespera.deliverable-root` | `<Run> is not started: the output folder is no path this system can hold` |

The line stands after ADR-292 §5's and before the others of ADR-302 §2's, and answers `303` to the page that start's refusals land on, starting nothing and storing nothing: `/` for `run` and `label`, `/next` for `save-then-run` and `label --auto`, `/labels` for `save-then-label` and `/invocation` for `publish`. `Label` and `Publish` refuse a corpus root they do not read, since the file of launch values would keep it and the preflight is red on it.

**A working directory that is no folder** is refused by every start too, nothing there or a file in its place: no child can be started in it (Context), and nothing creates it. It is one line, a fifth case of ADR-302 §2's table of lines, `<Run>` as above and `<folder>` as the field holds it, a space before it included:

```
<Run> is not started: the working directory <folder> is no folder that is there
```

The line stands after those of the table above and before the others of ADR-302 §2's, and answers `303` to the page that start's refusals land on, starting nothing, storing nothing and creating nothing. A page meets it by `run` and `label`, and by `publish` where the rendering lies under a deliverable root. `label --auto`, `save-then-run` and `save-then-label` stand only on a page that read the working directory to offer them, so only a page shown before the folder went can press them. `label --auto` meets this line; `save-then-label` is refused before it by its write (ADR-295 §1), and `save-then-run` by `there is no value the next invocation wants to save`, the next act's file having gone with the folder.

### 7. The other pickers

Each is a control a browser draws, offering what the operator may still type past, and each key's control is the same on `/profile`, named `value`, and on `/next`, named by the key:

| Field | Control |
| --- | --- |
| `vespera.label.model`, `embeddingModel`, `generationModel` | a text field with `list="models"`, the `<datalist id="models">` holding the models Ollama reports, in its order; empty where nobody answers |
| `vespera.docling.image` | a text field with `list="docling-images"`, the `<datalist>` holding `<option label="CPU">`, the jar's default of `application.yaml`, then `<option label="GPU">`, `compose.gpu.yaml`'s |
| `deliverableLanguage` | a `<select>` of `en` and `it`, the value the profile holds selected, the first where it holds none |
| `arrangementApproved` | a text field with a `<datalist>` offering `auto`; `/next` keeps it filled with the arrangement (ADR-302 §8) |
| each key of kind `NUMBER` | `type=number`, `step=any`, with no `min`, no `max` and no slider |
| any other | a text field |
| `relevant-<i>` on `/labels` | its `<select>`, as ADR-302 §8 has it |

**Ollama is asked wherever its address is named.** `SidecarChecks.ollama` asks `/api/tags` and returns the names, in the answer's order, whether a model is named or not; `data-check` stays as ADR-302 §5's table has it, so `not-checked` where no model is named, and with models to offer. `/` alone asks. Every other page offers the names `/` was last told (§3), which are a suggestion, and `/` asks again as the operator returns to it.

A posted value goes through `ProfileEdit` as before: a browser's `type=number` keeps what is no number from being sent, and whatever is sent is ADR-295 §4's to refuse.

### 8. Examples and hints

Every field a page draws carries a placeholder with a real example, beginning `e.g. `, a `<select>` apart, and `aria-describedby` naming an element that holds a hint of one line for the operator in plain words, and a `label` or an `aria-label`. No `id` is written twice on a page. An example holds no folder the page was given and names no real corpus: it is no recommendation of a value, and a threshold's is one number a reader can see is an example.

| Field | Label | Example | Hint |
| --- | --- | --- | --- |
| `vespera.corpus-root` | Corpus root | `D:\archive\corpus` | The folder of documents Vespera reads. It is never changed. |
| `vespera.working-dir` | Working directory | `D:\vespera\work` | Where the database, the reports and `profile.yaml` live. Create it if it is not there yet. |
| `vespera.deliverable-root` | Output folder | `D:\vespera\deliverable` | Where the finished pages go. Leave it empty to keep them in the working directory. |
| `vespera.docling.image` | Docling image | the CPU image of §7 | The image the converter runs from. The two offered are the ones this build knows. |
| `vespera.docling.base-url` | Docling address | `http://localhost:5001` | Where the converter answers. |
| `spring.ai.ollama.base-url` | Ollama address | `http://localhost:11434` | Where the local models answer. |
| `vespera.label.model` | Label model | `qwen3:8b` | The local model that answers the questions when you ask it to. |
| `new-folder` | Folder, from the corpus root | `src/billing` | The folder of the set of code, from the corpus root. |
| `new-name`, `name-<i>` | Name | `Billing engine` | The name the deliverable gives the set. Clear it to remove the name. |
| `relevant-<i>` | relevant | | Whether this document belongs in what you are keeping. No answer leaves it for later. |
| `provenance`, `<key>.provenance` | Why this value? | `read off the report beside it: the cut I can live with` | Required. Why you chose this value, in your words. It is written beside it in `profile.yaml`. |

**Each profile key's hint** is the second cell of the README's table of values, its row's own, its code quotes taken out, in `#hint-<key>` on both pages; a capital and a stop are the page's. The README is not read at run time, the table being a constant of the control panel, and `FieldHintsScreenTest` holds it to the row, so a key added to `ProfileKey` fails until it has one. Its examples:

| Key | Example | Key | Example |
| --- | --- | --- | --- |
| `seedFolder` | `D:\archive\exemplars` | `clusterPageByteCeiling` | `10485760` |
| `degenerateOutputConfidenceFloor` | `0.5` | `logTimestampShareFloor` | `0.7` |
| `degenerateOutputWordFloor` | `20` | `extractionAttempt` | `2` |
| `boilerplateDocumentFrequencyFloor` | `0.4` | `codeNameEndings` | `.java .sql` |
| `embeddingModel` | `bge-m3:latest` | `outOfScopeNameEndings` | `.ok .tmp` |
| `relevanceScoreFloor` | `0.62` | `translationSimilarityFloor` | `0.85` |
| `arrangementApproved` | `auto` | `preferredLanguages` | `it, en` |
| `generationModel` | `qwen3:8b` | `configurationSettingShareFloor` | `0.8` |
| `generationContextWindow` | `32768` | `deliverableLanguage` | a select, none |

### 9. What a test holds, and what it cannot

A test reads names, ids and attributes, as ADR-302 §9 and ADR-297 §5 have it. It adds:

| Page | Forms, controls and buttons (`name`) | Texts and attributes (`id`) |
| --- | --- | --- |
| every page | | `sidebar`, `top-row`, and the words of its `<title>` |
| `/`, `/invocation` | | `tiles` (`data-tile`, `data-state`, and `data-ending` on `invocation`), and `#child`'s `data-state` |
| `/` | `browse-vespera.corpus-root`, `browse-vespera.working-dir`, `browse-vespera.deliverable-root`, `browse-seedFolder` | `card-folders`, `card-services`, `card-seed-folder`, `card-preflight`, `card-reports` (`data-column`), `preflight` (`data-row`, `data-state`), `status-<for>` (`data-status`), `models`, `docling-images` |
| `/next` | `browse-next-seedFolder` | `status-next-seedFolder`, `models`, `hint-<key>` |
| `/code-sets` | `browse-new-folder` | `status-new-folder` |
| `/profile` | `browse-profile-seedFolder` | `profile-list`, `key-<key>`, `status-profile-seedFolder`, `models`, `hint-<key>` |
| `/folders` | `use-folder`: `for`, `path`; button `use` | `folder-listing` (`data-listing`, `data-path`), `folder-roots`, `folder-up`, `folder-list`, `folder-back` |

**What no test holds.** No test renders a page: where a card falls, whether a window that is narrow scrolls sideways, what the colours look like and how a browser offers a `<datalist>` rest on the style's rules, which a test reads as text, and on the specifications. No test opens a browser, so what `--app=` does rests on the measurement in Context, which no test repeats, and whether a link opened from such a window opens in the browser's ordinary window is not measured (Known limits). `SystemBrowserOpener.open` starts a process and so is run by no test: a test reads its compiled calls, and the choice in `windowCommand`.

### 10. The classes

In `io.algernon.controlpanel`, linking no class of the jar, and none of the server's classes changed:

- **`SystemBrowserOpener`**: `windowCommand`, as §1 gives its signature, package-private and static. `open` calls it. `command` is unchanged. No other test names a new member of these classes: `ChildRuntimeTest` names `FolderPages` for its compiled calls, and every other behaviour is read through the pages.
- **`ControlPanel`**: the line of §2.
- **`ControlPanelPage`**: `render`'s `<title>`, `#sidebar` and `#top-row`, the style, the tiles, and the fields of §7 and §8.
- **`FolderPages`**, a class of its own beside `ValuePages`: `/folders`, a `GET` and a `POST`, among the pages `ControlPanelPages` gives the server.
- **`ControlPanelState`**: the held fields of a form (§5), the last answers of the sidecars (§3, §7), and a folder the system cannot hold read as §6 has it: `workingDirectory` and `holders` throw nothing for one, and `start` refuses it, and a working directory that is no folder, before it stores anything.
- **`SidecarChecks`**: the names Ollama answers, on `Check`, whose one-argument constructor stays.
- **`ProfileKey`**: each key's hint and example (§8).
- **`ProfileEdit`**: `seedFolder`'s value, checked and written losing its trailing white space alone (§6).
- **`LaunchPages`**, **`NextPages`**, **`ValuePages`**, **`InvocationPages`**: the layout of §3 and a form's Browse button (§5), a save at `/profile` answered to its section, the refusal of a `new-folder` at `/code-sets` (§5), and a folder field read as §6 has it.

### 11. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves, and no dependency or plugin is added. The README's section on starting the control panel says that it opens a window where it finds a Chromium browser and the default browser otherwise, gives the line of §2 and says what to do where nothing appears; `docs/architecture.md`'s sentence on the control panel's server says it opens a window by the same rule.

## Known limits

- Any local caller can list the names of the folders of every drive through `/folders`, on top of what ADR-325 §2 accepted of another local user and any program on the machine. It reads no file's name and no file. Another website in the operator's browser is kept out of it by the same checks as every page.
- On Windows a `path` of `/folders`, or a value typed in a folder field, that names a share, `\\host\share`, has `Files.isDirectory` contact that host. A local caller can so cause outbound connections, and hold a request on a share that does not answer. Not measured. A website is kept out by `Sec-Fetch-Site`.
- Save reads a `new-folder` by its text, so one that lies under a link leading out of the corpus root is written; the folder page and the field's status follow links, and say so (§5, §6).
- Save writes what a post holds in `folder-<i>`, which the page fills from the file, and reads `new-folder` alone by its text. A post made by hand from the control panel's own origin, `folder-1=../x`, so writes a folder that climbs out. No page offers this, and a website is kept out by `Origin`.
- A folder whose name ends in a space, which Linux can hold and Windows cannot, cannot be chosen: the folder page lists it, and the field reads what it holds without that space (§6), so that the name read is another folder's or no folder's.
- A value typed in another form of a page, and not posted, is lost by Browse, as it is by that page's Save: a browser sends one form.
- Enter in a field presses a form's first submit button. In `seed-folder`, `next`, the profile's seed folder and `code-sets` that is Browse, which writes nothing; in `launch` it stays Run, as it was.
- `deliverableLanguage`'s select has no empty value, so a language once saved is cleared only in the file.
- A browser found but too old to take `--app=` is not told from one that does; what it does with the switch is the browser's.
- What `--app=` does in Chrome and Chromium, and on macOS and Linux, rests on the browsers' published switches, a test holding only the command chosen. Not measured.
- A link opened from a window, a report's above all, opens where that browser opens one, its ordinary window as a rule. Not measured.
- On a system other than Windows and macOS, a browser the start started shares the terminal's foreground group where it was not already running, so `Ctrl+C` may end it with the control panel, and the next start waits its 5 seconds and opens a new window. Not measured.
- A window the operator closed leaves nothing to come back, as a tab closed does (ADR-325).

## Alternatives refused

- **A desktop toolkit, or `jpackage`.** A dependency or a second build beyond ADR-285 §3's two modules, and a second interface to test; a window cannot show the reports, which are HTML pages with a script (ADR-302).
- **A picker driven by script.** No test could run it, the build having no JavaScript engine, and ADR-325 §4.2's script writes nothing into a page.
- **The system's own folder dialog, started by the server.** A request of a page would start a process, which ADR-291 §8 keeps out of what answers a request; it needs a program of its own on each system, and the folder page serves every system.
- **Sliders, and `min` and `max` on a number.** A threshold's range is no decision anyone made, and a bound the page draws would be one more value nobody decided (Context).
- **A `min` from the checks of `ProfileEdit` on the whole-number keys.** A second copy of a rule `ProfileEdit` holds, for a value it refuses in one line anyway.
- **`/profile?key=` in place of `:target`.** A page holding one form would change what every read of `ProfileScreenTest` finds.
- **The sidecars asked from every page.** Two seconds where nobody answers, on a page that reloads every two seconds while the child runs.
- **A `form` attribute on the top row's buttons.** ADR-297's browser, as jsoup reads a form, sees only what stands inside it.
- **Reading the README at run time for the hints.** The jar of the control panel does not carry it.
- **Trying the system's own command after a browser that was found fails to start.** A branch no test can reach, for a case the line of §2 already answers.
- **The three Windows names read exactly, with a known limit.** A Chrome installed under `ProgramFiles` alone would get a tab where the control panel is started from Git Bash, against what §11 has the README say.
- **Holding a path the system cannot hold out of the launch values, as a post of `launch` arrives.** The field would lose what the operator pasted, and with it the only sign of why it was refused (ADR-302 §2 replaces the values by those posted).
- **The start creating the working directory.** ADR-295 §1 has the control panel create none. A ` x` pasted would be made under the control panel's current directory, and a slip in a path would become a new, empty working directory, on the strength of an amber row.
- **The refusal left to `ProcessBuilder`.** `Run is not started: Cannot run program ...` says nothing of what to do, and comes after the launch values are stored, so the file would keep a working directory no start can use.
- **Red on the preflight disabling Run.** ADR-302 §2 and §5 keep Run enabled on a warning, and its refusals are its own.
- **A folder field stripped at both ends, the name lost stated as a limit.** A corpus root chosen as ` x` then Run would read `x`, another folder, with nothing on the page to say so.
- **A folder field held as typed at both ends.** `Path.of` on Windows refuses a name ending in a space, so a folder that is there, pasted with one, would read as no path at all, and the name given up is one Windows cannot hold anyway.

## Consequences

- The operator starts the control panel and a window of its own opens, where a Chromium browser is installed, and a tab of the default browser otherwise; started again, the window already open reloads, and no other opens.
- The printed line tells the operator it is opening and asks nothing of them unless nothing appears.
- A folder is chosen without typing it, on every system, from a page of names; a folder typed still works, and its field says what the control panel found, and a code set's that climbs out of the corpus root is refused by Save.
- A working directory that is not there is made outside the control panel before its first command, the folder page making no folder; a slip in its path is refused and makes nothing.
- The tree of a machine's folders is visible, by name, to anything local that asks the control panel while it runs.
- A key added to `ProfileKey` needs a hint and a row of the README's table, and an example.
- A change of the jar's default Docling image, or of `compose.gpu.yaml`'s, changes what the launch page offers with it.
- Every other page's tiles and models are as of the launch page's last showing.

## Tests

These hold §1 to §8 through the pages and the start as the operator meets them; none names a new member of §10 but `TheControlPanelOpensAWindowOfItsOwnTest`, which calls `windowCommand`, and `ChildRuntimeTest` names `FolderPages` for its compiled calls.

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.onWindowsTheFirstBrowserFoundIsOpenedAsAWindowInTheRecordsOrder` | the five candidates, each with those after it installed: the first is chosen, with `--app=<address>` as one argument (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.onMacOsAnInstalledBrowserIsOpenedAsAWindowThroughOpen` | both installed: `open -na "Microsoft Edge" --args --app=<address>`; Chrome alone: Chrome (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.onAnyOtherSystemTheFirstBrowserOnThePathIsOpenedAsAWindow` | the four names in order, by the path found at; a name in both folders from the one `PATH` names first; the name counting before the folder (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.whereNoBrowserIsFoundTheSystemsOwnCommandIsChosen` | nothing installed: `command`'s for each system; variables absent or blank: no folder looked in, every path existing (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.nothingIsLookedForUnderARelativeFolder` | every path existing: `.` and `bin` before `/usr/bin` in `PATH`, `/usr/bin/chromium`; relative and empty elements alone, `xdg-open`; a relative `ProgramFiles(x86)` before an absolute `ProgramFiles`, Edge under the second; all three relative, a drive with no separator among them, `rundll32`; a `ProgramFiles` of `\\host\share`, `rundll32` (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.onWindowsTheThreeVariablesAreReadWhateverTheirCase` | each of the three in another case names its folder, the order standing and a relative one still passed over; off Windows `Path` is not `PATH`, `xdg-open` (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.aVariableTheSystemCannotHoldAsAPathFindsNoBrowserThere` | every path existing: each of the three holding a NUL alone, and all three, `rundll32`; `ProgramFiles(x86)` so held, Edge under `ProgramFiles`; `ProgramFiles` so held, Chrome under `LOCALAPPDATA`; `LOCALAPPDATA` so held, Edge under `ProgramFiles(x86)` (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.underCiOrWithNoDisplayNothingIsOpenedThoughABrowserIsThere` | `CI` on every system, and Linux with neither display: empty, every path existing (§1) |
| `controlpanel.TheControlPanelOpensAWindowOfItsOwnTest.theOpenerStartsWhatTheFunctionChooses` | `open`'s compiled calls name `windowCommand` and not `command`, `System.getProperty`, `System.getenv()` with no name, and `Files.exists` (§1, §9) |
| `controlpanel.ControlPanelStartTest.noTabWithinTheBoundAsksTheOpenerOnceForTheRoot` | the line of §2 word for word, after the waiting line, the opener asked once for the root (§2) |
| `controlpanel.ControlPanelStartTest.aPortOfZeroWaitsForNoTab` | the line of §2 alone at a port the system picked (§2) |
| `controlpanel.TheControlPanelsLayoutScreenTest.everyPageHasTheSixPagesInASidebarWithItsOwnMarked` | on each of the six, the links in order, its own alone `aria-current`, no other `nav`; `/folders` marks none (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.everyPageHasATopRowHoldingItsTitleAndTheLaunchPageItsThreeButtons` | one `h1` named as the sidebar names the page; `run`, `label`, `check` in `#top-row` and in `launch`, and on no other page (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.everyPageIsTitledWithItsNameThenTheProgramsName` | on each of the six and on `/folders`, the `<title>` is the page's name and ` · Vespera` (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.noPageFetchesAFontAStyleOrAPicture` | no element that fetches, and no `url(`, `@import` or `@font-face` in the style (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.everyColourOfTheLightIsNamedAgainForTheDark` | the two blocks name the same colours, `--ok`, `--warn` and `--bad` among them (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.noColourOfTheStyleIsCalledPanelAlone` | no `--panel`, with a number or without one, named or used in the style (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theTopRowStaysAtTheTopAndANarrowWindowHasOneColumn` | `#top-row`'s `position: sticky`, and a `max-width` rule setting each selector the style makes a grid, `#tiles` among them, to `1fr` (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theLaunchPageHasFourTilesInOrder` | the four in order, each with a state, the two checks inside their tiles (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theTilesOfTheSidecarsShowTheirChecks` | each `data-check` paired with its tile's `data-state`, through stub sidecars (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theInvocationsTileAndStatusShowHowTheChildIs` | none, ended, failed, a defect, running and stopped, each on `#child` and on both pages' tiles with its state (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theInvocationPageShowsTheChecksTheLaunchPageLastMade` | before `/`: `not-checked`, nobody asked; after: what `/` was told, nobody asked again (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theLaunchPageHasTwoColumnsOfCards` | the five cards in order with their columns, and what each holds (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.thePreflightIsRedForAFolderRunNeeds` | the five rows in order; not chosen, not there and a file red, and found green, for each of the two folders; `run` enabled on each (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.thePreflightShowsTheChecksOfTheSidecars` | every row `ok` with every check holding; another image, a model not served and nothing named `warn` (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.thePreflightsLocksAreRedWhileRunCannotStart` | `ok`; `bad` with `vespera.lock` held; `bad` with the control panel's own child running (§3) |
| `controlpanel.TheControlPanelsLayoutScreenTest.theProfilePageListsEveryValueAndLinksToItsForm` | `#profile-list` in `ProfileKey`'s order, each to the section holding its form; `:target` in the style (§3, §4) |
| `controlpanel.TheControlPanelsLayoutScreenTest.savingAValueLandsOnThatValuesSection` | a save lands at `/profile#key-<key>` (§3) |
| `controlpanel.TheFolderPageScreenTest.eachFolderFieldHasABrowseButtonOfItsOwnThatSkipsTheRequiredProvenance` | each form's Browse buttons by name, one form of the profile's alone holding one; each a submit button with `formnovalidate` (§5) |
| `controlpanel.TheFolderPageScreenTest.browseOnTheLaunchFormHoldsWhatWasTypedAndLandsOnTheFolderPage` | every value typed held, the launch values' file unchanged, no child, landing on the folder typed (§5) |
| `controlpanel.TheFolderPageScreenTest.browseCarriesAFolderWhateverItsNameHolds` | an ampersand, a space, a percent, a plus, a hash and a quote carried whole by the `303` (§5) |
| `controlpanel.TheFolderPageScreenTest.browseWithTheFieldEmptyLandsOnTheRoots` | `roots`, each root linked to itself in the system's order, no list and no `use-folder` (§5) |
| `controlpanel.TheFolderPageScreenTest.theFolderPageListsSubfoldersByNameAndNoFile` | names in order without regard to case; a file's name and its text nowhere on the page (§5) |
| `controlpanel.TheFolderPageScreenTest.eachSubfolderLinkLandsInThatFolderWhateverItsNameHolds` | each link paired with the folder of its own name (§5) |
| `controlpanel.TheFolderPageScreenTest.upLeadsToTheParentAndARootHasNone` | Up, and none at a root (§5) |
| `controlpanel.TheFolderPageScreenTest.upBelowTheCorpusRootLeadsAStepTowardIt` | for `new-folder`, `tools/build` to `tools` to the corpus root, where it stops; `no-such-folder` at `tools/gone` and `gone`, each one's Up landing in the folder that would hold it (§5) |
| `controlpanel.TheFolderPageScreenTest.aSubfolderWhoseNameBeginsWithASpaceIsCarriedWhole` | beside the folder of that name without its space: listed, opened, chosen and given by its whole path as itself; aborted where the file system holds no such name (§5) |
| `controlpanel.TheFolderPageScreenTest.eachFieldLeadsBackToItsOwnPage` | `#folder-back` for each of the seven (§5) |
| `controlpanel.TheFolderPageScreenTest.useThisFolderPutsTheChoiceInTheLaunchFieldItWasPressedFor` | each of the three launch fields: that field alone holds the choice, a value typed is kept, the file unchanged, landing on `/` (§5) |
| `controlpanel.TheFolderPageScreenTest.chooseTheSeedFolderOnTheLaunchPageThenSaveIt` | the choice and the provenance typed held, no profile; Save writes both and lets go of them (§5) |
| `controlpanel.TheFolderPageScreenTest.chooseTheSeedFolderOnTheNextPage` | every value and provenance typed held, no profile, landing on `/next` (§5) |
| `controlpanel.TheFolderPageScreenTest.chooseTheSeedFolderOnTheProfilePage` | landing at its section, the value and provenance held, no profile; Save writes both and lets go of them (§5) |
| `controlpanel.TheFolderPageScreenTest.aRefusedSaveOfTheLaunchPagesSeedFolderKeepsWhatItHeld` | a folder and a reason held by Browse and Use this folder, a profile `ProfileEdit` refuses: one line in `outcome`, the file as it was, both still in their fields (§5) |
| `controlpanel.TheFolderPageScreenTest.aRefusedSaveOfTheNextPagesSeedFolderKeepsWhatItHeld` | the same on `/next` (§5) |
| `controlpanel.TheFolderPageScreenTest.aRefusedSaveOfTheProfilePagesSeedFolderKeepsWhatItHeld` | the same on `/profile` (§5) |
| `controlpanel.TheFolderPageScreenTest.aRefusedSaveOfACodeSetKeepsWhatItHeld` | a folder and a name held, refused by the write as a folder given twice, and before it as one that climbs out: one line each, the file as it was, both still in their fields (§5) |
| `controlpanel.TheFolderPageScreenTest.aCodeSetsFolderIsChosenFromTheCorpusRootDownAndSaved` | from the corpus root, `tools` then `tools/build` on every system, the name held, Save writing them, the row then empty (§5) |
| `controlpanel.TheFolderPageScreenTest.aCodeSetsFolderMayNotClimbOutOfTheCorpusRoot` | dots, dots after a name, an absolute path as the system writes it and with forward slashes, a leading `/`, a drive letter and a backslash: `outside-the-corpus-root`, nothing outside named (§5) |
| `controlpanel.TheFolderPageScreenTest.aPostClimbingOutOfTheCorpusRootChangesNoField` | the field empty, one line in `outcome` (§5) |
| `controlpanel.TheFolderPageScreenTest.aSaveOfANewFolderThatClimbsOutWritesNothing` | dots, dots after a name, a drive letter, a leading `/`, a backslash and an absolute path each way, and a value left in the field by Browse and Back: one line in `outcome`, the file as it was (§5) |
| `controlpanel.TheFolderPageScreenTest.withNoCorpusRootASaveOfANewFolderThatClimbsOutWritesNothing` | one line in `outcome`, no file written (§5) |
| `controlpanel.TheFolderPageScreenTest.aSaveRefusesOnlyTheNewRowsFolder` | saved rows whose folders are gone and lie above the corpus root kept; a new folder not there yet, two dots inside an element's name, written (§5) |
| `controlpanel.TheFolderPageScreenTest.aLinkInsideTheCorpusRootLeadingOutOfItIsNotFollowed` | a symbolic link leading out: `outside-the-corpus-root`; aborted where none can be created (§5) |
| `controlpanel.TheFolderPageScreenTest.withNoCorpusRootACodeSetsFolderPageListsNothing` | `no-corpus-root`, nothing listed (§5) |
| `controlpanel.TheFolderPageScreenTest.aFolderThatIsNotThereSaysSoAndOffersNothingToUse` | nothing there, a file, a path no system could hold: `no-such-folder`, in words, nothing to use (§5) |
| `controlpanel.TheFolderPageScreenTest.aFolderThatCannotBeReadSaysSoOnThePage` | reading taken away: `cannot-be-listed`, in words, nothing listed; aborted where this user can read it still (§5) |
| `controlpanel.TheFolderPageScreenTest.aFieldOffTheClosedListIsNotFoundAndChangesNothing` | five names by address, and one by post: 404, `text/plain`, one line by address, no name in the body, the image unchanged (§5) |
| `controlpanel.TheFolderPageScreenTest.theAddressWithNoFieldGoesToTheLaunchPage` | `/folders` and `/folders?path=` land on `/` (§5) |
| `controlpanel.TheFolderPageScreenTest.openingTheFolderPageWritesNothing` | opened for each of the seven: every file under the test's folder unchanged, and every field held unchanged, on `/`, `/profile` and `/code-sets` before a run and on those and `/next` after one, the next act's field empty after the sweep before the run (§5) |
| `controlpanel.TheFolderPageScreenTest.theFolderPagePassesTheServersChecks` | another site, another address: 403; a post from another origin: 403; another method: 405; its own: the listing (§5) |
| `controlpanel.TheFolderPageScreenTest.theFolderPageHoldsOnlyTheOneScriptThoughTheAddressHoldsMarkup` | one script, no event attribute, with markup in the query; a corpus root named, a code set's page `no-such-folder` with the raw path in `data-path`; a folder's name as text (§4, §5) |
| `controlpanel.FolderFieldStatusScreenTest.withNoFolderNamedEachFieldSaysNotChosen` | the launch page's four (§6) |
| `controlpanel.FolderFieldStatusScreenTest.foldersThatExistSayFoundWithTheFreeSpaceInGib` | `found`, the words holding GiB and no bytes (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aFolderThatIsNotThereWillBeCreatedOnlyWhereTheJarCreatesIt` | not there, each field's own; a file, `no-such-folder` for all (§6) |
| `controlpanel.FolderFieldStatusScreenTest.theNextActsSeedFolderSaysWhatItsFieldHolds` | `found`, then `no-such-folder` (§6) |
| `controlpanel.FolderFieldStatusScreenTest.theProfilesSeedFolderSaysWhatItsFieldHolds` | `not-chosen`, then `found` once chosen (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aCodeSetsNewFolderIsReadFromTheCorpusRoot` | `not-chosen`, then `found`, under the corpus root (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aCodeSetsNewFolderThatClimbsOutSaysNoSuchFolder` | dots, dots after a name and an absolute path each way, a folder there on the disk, left in the field by Browse and Back: `no-such-folder` (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aCodeSetsNewFolderSaysNoSuchFolderWithNoCorpusRootNamed` | `not-chosen` while empty, then `no-such-folder`, though the folder lies under the current directory (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aFolderTheSystemCannotHoldSaysNoSuchFolderAndEveryPageAnswers` | `not\0a-path` in each of the corpus root and the working directory, by Check and by Browse, the other field found: every page answers, a report not found, that field `no-such-folder` and its preflight row `bad`, the other's `found` and `ok` (§3, §6) |
| `controlpanel.FolderFieldStatusScreenTest.runAndLabelRefuseAFolderTheSystemCannotHold` | each of Run and Label with each of the three folders so held: back on `/` with §6's line word for word, no child, the launch values' file unchanged (§6) |
| `controlpanel.FolderFieldStatusScreenTest.publishRefusesACorpusRootTheSystemCannotHold` | a rendering drawn: back on `/invocation` with `Publish is not started: ...`, no child, the launch values' file unchanged (§6) |
| `controlpanel.FolderFieldStatusScreenTest.saveThenRunRefusesACorpusRootTheSystemCannotHold` | after a run: back on `/next` with `Run is not started: ...`, the first child's ending alone, the launch values' file unchanged (§6) |
| `controlpanel.FolderFieldStatusScreenTest.labelAutoRefusesACorpusRootTheSystemCannotHold` | the same, with `Label is not started: ...` (§6) |
| `controlpanel.FolderFieldStatusScreenTest.saveThenLabelRefusesACorpusRootTheSystemCannotHold` | back on `/labels` with `Label is not started: ...`, no child, the launch values' file unchanged (§6) |
| `controlpanel.FolderFieldStatusScreenTest.runAndLabelRefuseAWorkingDirectoryThatIsNoFolder` | each of Run and Label on a working directory not there, and on a file: back on `/` with §6's line word for word, no child, the launch values' file unchanged, nothing created (§6) |
| `controlpanel.FolderFieldStatusScreenTest.publishRefusesAWorkingDirectoryThatIsNoFolder` | a rendering under the deliverable root: back on `/invocation` with `Publish is not started: ...`, no child, the launch values' file unchanged, nothing created (§6) |
| `controlpanel.FolderFieldStatusScreenTest.theWorkingDirectorysHintAsksForItToBeCreatedFirst` | the hint tied to the field, word for word (§8) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.eachLaunchFolderKeepsASpaceBeforeItsNameThroughBrowseUseThisFolderAndRun` | each of the three holding a space before a folder of the current directory: Browse's address and `data-path` that folder's whole, not the one without the space; the field whole after Browse, Use this folder and Run; Run on the corpus root or the output folder started, the launch values' file holding it whole; Run on the working directory refused in §6's line, the file as it was; no folder of that name created (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aLaunchFolderLosesASpaceAfterItsNameAndAFieldNamingNoFolderLosesBoth` | spaces after a folder: listed, `found`, held without them; the Docling image and the label model typed lose spaces at both ends (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aValuePastedWithASpaceBeforeItSaysSoBesideEachLaunchFolder` | a name and a whole path after a space: the corpus root, the seed folder and the working directory `no-such-folder`, the output folder `will-be-created`, or `no-such-folder` where the system cannot hold it; the corpus root's row and the working directory's `bad` (§3, §6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.runRefusesACorpusRootPastedWithASpaceBeforeItsDriveWhereTheSystemCannotHoldIt` | §6's line word for word, the field as pasted, no child, the launch values' file unchanged; aborted where the system holds a path of that text (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.theSeedFolderKeepsASpaceBeforeItOnEachFormAndSaveRefusesIt` | on the launch page, `/next` and `/profile`: Browse's address whole, `no-such-folder`, the field whole after Browse and Use this folder; Save one line, no profile, the field as pasted (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.theSeedFolderLosesASpaceAfterItOnEachForm` | on each: listed, `found`, held without them, Save writing the folder without the spaces (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aCodeSetsNewFolderKeepsASpaceBeforeItThroughBrowseAndSave` | beside a folder of that name without it: Browse's address and `data-path` whole, `no-such-folder`; the field whole; Save writing it whole (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aCodeSetsNewFolderLosesASpaceAfterIt` | listed, `found`, held without them, Save writing the folder without the spaces (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aCodeSetsFolderWhoseNameBeginsWithASpaceIsSavedWhole` | beside the folder without its space: chosen, `found`, Browse landing in it, Save writing it whole; aborted where the file system holds no such name (§5, §6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aNewFolderThatClimbsOutWithASpaceBeforeItIsStillRefused` | dots, dots after a name, a drive letter and a leading `/`, each after a space: one line in `outcome`, no file written (§5) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aCodeSetsFolderIsReadUnderTheCorpusRootAsTheFieldHoldsIt` | a corpus root after a space: the page `no-such-folder`, nothing listed, the root linked whole; a folder under the one without the space `no-such-folder` (§6) |
| `controlpanel.FolderFieldsKeepALeadingSpaceScreenTest.aRenderingOfTheFolderWithoutTheSpaceIsNotOfferedToPublish` | a rendering in a folder: offered by that deliverable root and that working directory, and by neither after a space (§6) |
| `controlpanel.ProfileEditTest.theSeedFolderKeepsASpaceBeforeItAndLosesOneAfterIt` | a space before: refused in its one line with the space shown, nothing written; spaces after: the folder written without them (§6) |
| `controlpanel.FolderFieldStatusScreenTest.aSaveWithAWorkingDirectoryTheSystemCannotHoldIsAnsweredWithOneLine` | a save on `/` and on `/profile` lands with one line, no profile written (§6) |
| `controlpanel.FolderFieldStatusScreenTest.theFreeSpaceListShowsGibFromTheBytesItKeeps` | each item's words the GiB of its own attribute, no bytes; the tile the working directory's (§3, §6) |
| `controlpanel.NativePickersScreenTest.theLabelModelOffersTheModelsOllamaReports` | `list="models"`, the names in the answer's order, with no model named, `not-checked`, asked once (§7) |
| `controlpanel.NativePickersScreenTest.withNobodyAnsweringTheListOffersNothing` | an empty list, a text field (§7) |
| `controlpanel.NativePickersScreenTest.theEmbeddingAndGenerationModelsOfferTheSameList` | `/profile` and `/next` offer them, neither asking again (§7) |
| `controlpanel.NativePickersScreenTest.theDoclingImageOffersTheJarsDefaultAndTheGpuComposeFilesImage` | each label paired with the value read from `application.yaml` and `compose.gpu.yaml` (§7) |
| `controlpanel.NativePickersScreenTest.theDeliverablesLanguageIsASelectOfTheTwoLanguages` | `en`, `it`, a choice written, the value held selected on both pages (§7) |
| `controlpanel.NativePickersScreenTest.theApprovalOffersAuto` | `auto` on both pages, `/next` still filled with the arrangement (§7) |
| `controlpanel.NativePickersScreenTest.aNumberIsANumberFieldWithNoRangeAndNoSlider` | each key's type by its kind, `step=any`, no `min`, no `max`, on `/profile`; no `input[type=range]` on the six and `/folders` (§7) |
| `controlpanel.NativePickersScreenTest.aPostedValueStillGoesThroughTheProfilesOwnCheck` | what is no number refused, nothing written (§7) |
| `controlpanel.NativePickersScreenTest.eachValuesControlIsTheSameOnBothPages` | element, type, step, list and example, key by key (§7, §8) |
| `controlpanel.FieldHintsScreenTest.everyFieldOfEveryPageHasAnExampleAndAHint` | every field the pages draw: an example, a hint it is tied to, a label (§8) |
| `controlpanel.FieldHintsScreenTest.noExampleHoldsAFolderThePageWasGiven` | no example holds the test's own folder (§8) |
| `controlpanel.FieldHintsScreenTest.everyProfileKeyHasTheHintTheReadmesTableGivesIt` | each key paired with its own row's words, on both pages (§8) |
| `controlpanel.FieldHintsScreenTest.theDeliverableRootIsCalledTheOutputFolder` | the label, and a hint not calling it the deliverable root (§8) |
| `controlpanel.FieldHintsScreenTest.noPageGivesOneIdToTwoElements` | every id once on each page (§8) |

These change with this record, holding what they held: `controlpanel.ControlPanelAsStarted`, which reads the line of §2; `controlpanel.ControlPanelStartTest`'s two above; `pipeline.TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava`, which reads it too; `controlpanel.ScreenServer.buttonNames`, which leaves out a Browse button; `pipeline.TheJarReadsWhatTheControlPanelWritesTest.everyValueTheControlPanelSavesIsReadAsAnsweredAndEqual`, whose seed folder is saved with a space after it and no longer before it, `controlpanel.ProfileEditTest.theSeedFolderKeepsASpaceBeforeItAndLosesOneAfterIt` holding the refusal of one before it (§6); and `controlpanel.ChildRuntimeTest.neitherPathNorJavaHomePicksTheChildsJava`, which ADR-288's Tests names `noClassOfTheControlPanelReadsPathOrJavaHome`: `java.home` and `os.name` held, `JAVA_HOME` in no class, the word `PATH` in `SystemBrowserOpener` and in `FolderPages`'s query alone, and `ChildRuntime`, `ChildCommand` and `FolderPages` with no compiled call of `System.getenv`. `EveryPageHoldsOnlyTheControlPanelsScriptTest`, `ProfileScreenTest`, `SystemBrowserOpenerTest` and `pipeline.TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest` stand unchanged.

## What this does not decide

- **The words and styling of a page**, but for the `<title>` of §3, what §8 gives and `CONTEXT.md`'s _Renders as_.
- **Whether the pages are checked for accessibility**, beyond `aria-current`, a hint tied to its field and a label (ADR-302).
- **How Firefox and Safari draw a `<datalist>`**, and how a browser other than Chromium is opened as a window of its own.
- **A window's size**, which is the browser's.
