# ADR-358 — An invocation's report is one JSON file, drawn as static HTML through a Mustache template, and no command opens anything

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Decided**: by the operator, in [#721](https://github.com/algernon28/vespera/issues/721): that a display is present where a window can open, read from the system once at start, a GPU playing no part and `--headless` forcing none (§1); that the report is one machine-readable JSON file, the single source every view is drawn from, holding paths, counts, verdicts and reasons and never a document's text (§3); that the control panel's window is the only window, a run started from it showing its progress there and a run from the command line opening nothing (§2, §8); that without a display nothing graphical is tried, whatever `vespera.open-browser` says, and the report says so and why (§2); that the deliverable is untouched but for whether its index is opened (§2); that `report.html` is rendered from `report.json` with a template at write time, static, with no script and no fetch, the template shipped in the jar and replaceable by one `application-local.yaml` names (§5); what the report holds (§3); one current `report.json` per working directory, rewritten as the invocation goes, and a copy kept per invocation (§3, §4); that `report.html` replaces `progress.html` and the label page, the analysis reports staying and `report.json` linking them (§6); that the window shows the live report natively, from `report.json`, with no `WebView` (§8); that the command line keeps its console lines and its last line names `report.json` and `report.html` and says what was opened and why not (§7); that `report.json` carries a `format` number, with a schema in `docs/`, raised by a breaking change, a reader refusing a number it does not know (§3); that the template engine is Mustache through JMustache, Pebble the named step up, and FreeMarker, Thymeleaf and JTE set aside (§5, Alternatives refused); where the files lie (§3 to §5); that the analysis reports also write their numbers as JSON in the same work (§9); and that their HTML stays as their writers draw it now, each one's move to JSON and a template of its own a later ticket (§9). Under the operator's delegation of the same date, Claude decided the rest: how each system is asked, the fields and their names, the template's data model and how JMustache is held to it, what of the progress page's drawing the template keeps, the console lines' words, what the window shows and where, which records amend, the steps of the build and the tests.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §1, whose page is `report.html`, drawn from `report.json` (§3, §6), its rule that the page names no path giving way to §3's; §2, whose writes are `report.json`'s, `report.html` and the copy beside each, an invocation refused after it holds the working-directory lock writing one too (§3); §5's `ProgressPage` and `ReportPage.reloading` (§11); and §6, the README's sentence (Tests, What the implementation owes). §3's reading of memory alone, and §4's write that fails nothing, stand for the report. It carries a note at its head.
- **Amends**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §1, whose first line is `Report: <path>`, naming `report.html` (§7); §2, whose `PageOpener` and `SystemPageOpener` go, nothing being opened (§2); §3, whose label page is the report's `label` field and its drawing (§6); and §5, the README's sentence. It carries a note at its head.
- **Amends**: [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §1 and §2, whose tiles, timeline and charts `report.html` draws through the template (§6); §3, whose two pages of tables go (§6); §4, whose "no library and no template engine" and SVG written in Java no longer hold of `report.html` (§5); §5, whose liveness script goes and whose staleness the window shows (§6, §8); §6, the label page (§6); and §7, whose text alternatives are each chart's table (§6). It carries a note at its head.
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §7 and §8, whose drawings the shipped template makes from the geometry the jar computes (§5, §6); the rules that pick each chart's series, §2's counts held in memory, the tiles of §5 and the numbers of §6 stand as the jar's. It carries a note at its head.
- **Amends**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §1, whose four tabs are one page (§6); §3, whose script goes (§6); §4, whose hover is each point's `<title>` alone (§6); and §5, whose tab of runs is the report's `runs`, read at the same two moments (§3). It carries a note at its head.
- **Amends**: [ADR-322](0322-the-progress-pages-three-line-charts-stand-in-one-row-that-wraps-each-drawn-600-units-wide-and-legible-down-to-500-px.md) §1 to §7, which hold of the shipped template's drawing (§6). It carries a note at its head.
- **Amends**: [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §5, whose fold kept in the page's fragment goes with the script, so a fold does not outlive a reload (§6); and §6, whose control panel draws the steps from `report.json` (§8). §1 to §4, the legs, their bars, states and which is open, stand, written into the report by the jar. It carries a note at its head.
- **Amends**: [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md) §1, whose last write reloads by a plain refresh (§6); §3, whose script goes (§6); and §5, whose `vespera.open-browser` is no longer read, nothing being opened (§2). §2's sentence after the end stands. It carries a note at its head.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §7, whose index is handed to no opener: the closing line still names it, and nothing opens it (§2). The tree is untouched. It carries a note at its head.
- **Amends**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md), as [ADR-270](0270-every-log-vespera-writes-is-in-the-working-directorys-logs-folder-the-invocation-account-among-them.md) left it: the account is the report's copy per invocation (§4), its counts the report's `counts`, its steps, runs and failures the report's fields, and its allow list widened to §3's paths and the jar's own lines; §5's exception types, never their messages, and §3's counts and their exception to ADR-041 stand; Points settled 6, `vespera label` writing no account, no longer holds. It carries a note at its head.
- **Amends**: ADR-270 §2, whose `invocation-account-<start>.txt` is `logs/report-<invocation>.json`, written by every invocation that writes the report (§4). §1, `logs/vespera.log`, stands. It carries a note at its head.
- **Amends**: [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md) §2, whose closing line is the last line but one where the report line follows it (§7). §1 stands. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §5, whose line shown with how the child ended is the closing line the child's report holds (§8). It carries a note at its head.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §2, whose "Nothing asks the system whether it has a display before the toolkit is tried" no longer holds: §1's check is asked first, and the toolkit's failure stays the second answer (§2). It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose tabs gain the report's, first (§8); §2, whose `progress.json` and `ProgressFile` are `report.json` and `ReportFile` (§8); §4, whose `vespera.open-browser` is no longer among what the window does not offer, nothing reading it (§2); and §6, whose `Reports.ALL` holds the twelve analysis reports and none of the four progress pages (§8). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose closed list of `effective-values.properties` loses `vespera.open-browser`, and §8, whose row for it goes, nothing reading it (§2). It carries a note at its head.
- **Amends**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md)'s refusal of a template file, which no longer holds of `report.html` (§5). `ReportPage` stays the one module of the twelve analysis reports. It carries a note at its head.
- **Keeps**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) whole: the report lies in the working directory, open to an agent as the place it lies in, and holds no text of a document (§3), so it is no way into the archive. [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5 to §7, as [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) keeps them: the window learns the running step from the report as it learnt it from `progress.json`. [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) and [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) whole: the note, the tree, the stage beside each step and Publish's destinations are unchanged. [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1: the child still runs with `CI=1`, which the jar no longer reads. [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) and [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1: `next-invocation.properties` and `effective-values.properties` are written as before. [ADR-275](0275-every-div-of-every-page-vespera-writes-says-on-hover-what-it-shows-and-every-step-of-the-timeline-what-it-does.md): every `<div>` of the shipped template says on hover what it shows. [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md): the six counts are still taken by `InvocationAccount` alone. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): no stage's version names `pipeline`. The deliverable's tree, the Confluence rendering and the website rendering, as [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) and [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) draw them.
- **Rests on**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): the dependency of §5 is this record's.
- **Settles**: [#721](https://github.com/algernon28/vespera/issues/721).

## Context

**How an invocation is shown today.** `vespera run` rewrites four HTML pages in the working directory every `vespera.progress-interval`, `progress.html` and its three tabs of detail, with `progress.json` beside them for the control panel (ADR-265 to ADR-269, ADR-344 §2), each carrying one script that reloads it, keeps the timeline's folds in its address, marks it stale and draws a hover over each chart (ADR-269 §3, ADR-324 §5, ADR-327). `vespera label` writes `progress.html` as a page of its own, at its start and its end (ADR-266 §3). `SystemPageOpener` opens the progress page, and the generation step the deliverable's index, in the system's browser: `rundll32` on Windows, `open` on macOS, `xdg-open` elsewhere, nothing where `CI` is set or, off Windows and macOS, where neither `DISPLAY` nor `WAYLAND_DISPLAY` is set (ADR-266 §2, ADR-274 §7), and nothing where `vespera.open-browser` is false (ADR-327 §5). The control panel's child runs with `CI=1` (ADR-278 §1). Every `vespera run` that starts the job writes `logs/invocation-account-<start>.txt`, its steps, runs, counts, progress lines and failures by type, built from an allow list that names no path (ADR-198, ADR-270). The jar started with no argument tries the window, and only the toolkit's own failure tells it there is no display (ADR-342 §2).

**What else is written as text.** `effective-values.properties` (ADR-330), `next-invocation.properties` (ADR-294), `documents.csv`, `code.csv`, the website's `vespera-website.json` and `files.json`, and the Confluence rendering's `pages.json`, each for a reader of its own.

**The reports.** `Reports.ALL` names sixteen files (ADR-344 §6): the four progress pages, and twelve pages a step writes for the operator to read a value off, each through `ReportPage` (ADR-130): `format-mix.html` (`ByteLevelReductionTasklet`), `extraction-failures.html` (`ReviewListListener`), `extraction-survivors.html` (`SurvivorListListener`), `confidence-distribution.html`, `document-frequency.html` and `word-count-distribution.html` (`ContentCensusTasklet`), `seed-corpus-comparison.html` (`SeedCorpusComparisonTasklet`), `translations.html` (`TranslationTasklet`), `cluster-sizes.html` (`ClusteringTasklet`), `relevance-labelling.html` (`RelevanceLabellingReport`), `arrangement.html` (`ArrangementTasklet`), all of `pipeline`, and `cluster-page-sizes.html` (`ClusterPageSizes`, of `deliverable`). This record calls those twelve the **analysis reports**. No stage's version names `pipeline` or `deliverable` (`StageModules`).

**What was measured.** On Windows 11 with OpenJDK 26.0.2.1, a probe outside the repository called `user32`'s `GetProcessWindowStation` and `GetUserObjectInformationW` with `UOI_FLAGS` through `java.lang.foreign`, run with `--enable-native-access=ALL-UNNAMED` from the desktop the operator is logged on at: the window station answered `WSF_VISIBLE` set, `kernel32`'s `ProcessIdToSessionId` answered 1, and the probe took 25 ms from its first line, linking included. A process with no visible window station was not run: that a service's station lacks `WSF_VISIBLE` is the system's documented rule, the flag .NET's `Environment.UserInteractive` reads. Nothing was run on macOS or Linux. Spring Boot 4.1.1's dependency management names `com.samskivert:jmustache` at 1.16, its last release; JMustache resolves a name against a `Map` by its keys, and against any other object by a method, a getter or a field of that name, through its `DefaultCollector`, which extends `BasicCollector`, and escapes HTML in `{{name}}` unless told not to.

## Decision

### 1. A display is present where a window can open

**`DisplayPresence`, a public class of `pipeline`, answers once per process whether a display is present, and why**, from the system the process runs on, read by `os.name`:

| System | Present where | `display.reason` |
| --- | --- | --- |
| Windows | the process's window station, `GetProcessWindowStation`, carries `WSF_VISIBLE` in its `UOI_FLAGS`, read by `GetUserObjectInformationW` through `java.lang.foreign` | `visible-window-station` or `hidden-window-station` |
| macOS | CoreGraphics' `CGSessionCopyCurrentDictionary` answers a dictionary, which is released, rather than none, through `java.lang.foreign` | `window-server` or `no-window-server` |
| any other | `DISPLAY` or `WAYLAND_DISPLAY` is set and not blank | `display-variable` or `no-display-variable` |
| any, started with `--headless` first | never | `headless` |
| any, where the native call throws or a library is not found | never, the call's exception class named in the log once | `not-answered` |

**It is asked once.** `EntryPoint` asks it before anything else where the jar is started with no argument (§2), and `VesperaApplication`'s command line asks it once as it starts, the answer held for the invocation. `--headless` first is handed to the command line by `EntryPoint` as the JVM's system property `vespera.headless`, set by `EntryPoint` alone and named by no file the jar ships, so the command line's answer is `headless` without asking the system. **A GPU plays no part**: no graphics card, driver or OpenGL is asked of. **The operator cannot force a display present**; `--headless` is the one override, and it forces none.

**The seams.** The Windows and macOS calls are each one method of an interface `DisplayPresence` is given, so that a test answers them on any system; `DisplayPresence.of(String osName, Map<String, String> environment, boolean headless, NativeDisplay windows, NativeDisplay mac)` decides, and `DisplayPresence.ofThisProcess()` reads the system's own. Surefire's run is given `--enable-native-access=ALL-UNNAMED`, as the jar's manifest already gives it (ADR-342 §2).

### 2. What each start prints and opens

**No command of the jar opens anything**: no browser, no file, no window. `rundll32`, `open`, `xdg-open`, `java.awt.Desktop` and `HostServices` are named by no class of `io.algernon.vespera`. `PageOpener`, `SystemPageOpener` and `RecordingPageOpener` go, and `vespera.open-browser` is read by nothing: an `application-local.yaml`, an option or a variable that still sets it changes nothing and is told of nowhere. It leaves `application.yaml`, `effective-values.properties`' closed list (ADR-330 §1) and `ChildCommand`'s list of what the window does not offer (ADR-344 §4). The control panel's window is the only window, and only the jar started with no argument opens it.

| Start | Display present | No display |
| --- | --- | --- |
| no argument | the control panel's window opens; where the toolkit throws as it starts, ADR-342 §2's line with the toolkit's message, then the commands' usage, picocli's usage code | no JavaFX class is loaded and no toolkit is tried: one line on standard error, `the control panel cannot open a window here, so the jar answers as its command line: no display (<reason in words>)`, then the commands' usage, picocli's usage code |
| `--headless` first | as no display, whatever the system says: the command line, given every argument after it (ADR-342 §2) | the same |
| `run`, `label`, `label --auto`, `publish` | its console lines (§7); writes the report (§3, §4); opens nothing | the same lines and files; opens nothing |
| `serve`, `--help`, `--version`, an option or a command the command line refuses | as today; writes no report and opens nothing | the same |

A child of the window is a start of the command line, so it opens nothing either; the window shows its report (§8).

**The report says what was opened**: `opened.files` is empty in every report the jar writes, and `opened.because` is `no-display` where no display is present, and `commands-open-nothing` where one is.

### 3. The invocation report

**`report.json`, at the top of the working directory, is the invocation report**: one JSON file, the single source from which `report.html`, the window and any script or agent read how the invocation in hand, or the last to end, stands. It is written by `vespera run`, `vespera label` with or without `--auto`, and `vespera publish`, by every one of them that holds the working-directory lock (ADR-177), a refusal after that included. An invocation refused before it holds the lock, a working directory in use, not there or not a folder, writes none, so it never overwrites the report of the invocation that holds it; its line on standard error is all it says.

**When it is written**, whole, into `report.json.part` and moved over `report.json` by `ReplacingMove` (ADR-223 §3), and drawn into `report.html` the same way after each write (§5):

- `vespera run`: once as the job starts, every `vespera.progress-interval` while it runs from the writing thread ADR-265 §2 gives the page, and once as it ends, under ADR-265 §2's lock, so no write of the running report lands over the last;
- `vespera label`: as it starts and as it ends;
- `vespera publish`: as it starts, after each destination ends, and as it ends;
- a refusal after the lock: once, with the refusal.

**A write that fails fails nothing**, as ADR-265 §4 has it: caught where it is made, one warning, `the report could not be written: <exception class name>`, until a write succeeds again, and the next write on time.

**What it holds, format 1.** Every instant is ISO 8601 in UTC with `Z`; every duration is in whole seconds; every size in bytes; every count a JSON integer; a field with nothing to hold yet is `null`, never left out.

| Field | What it holds |
| --- | --- |
| `format` | `1`, the integer `InvocationReport.FORMAT` holds |
| `vespera` | the jar's version, as its manifest names it |
| `invocation` | `id`, the start in UTC as `yyyyMMdd'T'HHmmss'Z'` with `-2` and on for a second in the same second, which names the copy (§4); `command`, `run`, `label`, `label --auto` or `publish`; `started`, `ended`; `state`, `running` or `ended`; `outcome`, ADR-265 §1's `completed`, `failed` or `stopped` for `run`, ADR-266 §3's `recorded`, `refused` or `failed` for `label`, `delivered` or `stopped` for `publish`, `refused` for a refusal, `null` while running; `exitCode`, `null` while running; `writtenAt`; `interval`, the seconds between writes |
| `display` | `present`, and `reason` from §1's closed list |
| `opened` | `files`, the files opened, an empty list; `because`, `commands-open-nothing` or `no-display` (§2) |
| `places` | `workingDirectory`, absolute; `corpusRoot`, the root `run` walks, else `null`; `deliverableRoot`, the root the tree is drawn at where this invocation drew one, else `null` |
| `steps` | each step of the job, in the order the job runs them: `name` as `StepNames` holds it, `stage`, the stage under whose runs the job records the step's completion as ADR-269 §5's table gives it, and `0`, `5e` and `5` for the census, the relevance floor and the relevance report (ADR-345 §3), `leg` (ADR-324 §1), `state` in ADR-265 §1's five words, `read`, `written`, `skipped`, `started`, `ended`, `seconds`, and `progress`, the last progress line ADR-265 §3 admits, with its counter's `done` and `total` where it has one; for `label` and `publish`, an empty list |
| `legs` | the four legs, each `name`, `state` and `open` as ADR-324 §3 and §4 decide them |
| `removing` | the step removing occurrences now or last, its `entered`, `survivors`, `verdicts` by kind and `perMinute` by kind over the last ten minutes, as ADR-268 §2 and §3 count them in memory |
| `database` | `bytes`, `vespera.db` and `vespera.db-wal` added, read by a file system call |
| `charts` | ADR-344 §2's three charts, each `id`, `title`, `axis`, `seconds` and `lines` of `name` and `values`, picked by the jar's rules, at most 256 samples (ADR-267 §2) |
| `runs` | `read`, `none`, `start`, `end` or `failed`, and `rows`, ADR-269 §5's table: `walk`, `stage`, `ordinal`, `stepsFinished`, `stepsOf`, `state`, `thisInvocation` and `verdicts` by kind; read at the two moments ADR-269 §5 reads them |
| `arrivedAt` | the runs this invocation minted or continued, each `stage` and `run`, its id (ADR-198 §2) |
| `gates` | each step shown `stopped at a gate`, its `key`, the profile value whose absence shut it as `ShutGates` records it beside the step, and `reports`, the analysis reports that key is read off, as the README's table of values names them |
| `refusals` | each refusal line the invocation printed, whole, in the order printed |
| `faults` | each failed step, its `step`, `type` and at most three `causes`, fully qualified class names, never a message (ADR-198 §5) |
| `counts` | at the end of a `run` that started the job, ADR-198 §3's counts, scoped to the runs it arrived at: `occurrences`, `verdicts` by kind, `extractionFaults` by category, `clusterFaults` by kind, `clusters` and `synthesisDocs`, `null` before; a stored value outside its enumeration counted as `other` |
| `label` | for `label`, `answers`, ADR-266 §3's counts by its words in its order; else `null` |
| `publish` | for `publish`, each destination, its `destination`, `outcome` and closing `line`; else `null` |
| `closing` | the closing line (ADR-281 §2), whole, once printed; `null` before |
| `next` | what `next-invocation.properties` says, by its keys (ADR-294), from the same value the file is written from; `null` where the file says nothing |
| `reports` | the twelve analysis reports, each `name`, `html` and `json`, their absolute paths, `written`, whether the HTML is there, and `headline`, the `headline` its JSON holds (§9), `null` where it is not there, cannot be read or is of a format the jar does not know; read as the invocation starts and after each step ends |
| `files` | each file of the working directory's top and `logs` folder the invocation wrote or rewrote, and the deliverable root's `index.html` where it drew one: `role` from a closed list and `path`, absolute; no copy and no page of the tree is listed one by one |
| `template` | `named`, the path `vespera.report.template` names, else `null`; `used`, `jar` or `named` (§5) |

**What it never holds**: a document's text, title, opening or name read from its content, a cluster's label or title, a model's prose, a converter's message, a verdict's or a fault's stored reason or detail, and an exception's message. The paths it names are the folders the invocation was given, the files it wrote, and those the jar's own lines name: a refusal, a closing line or a publish's line, which may name a file of the operator's, a document's among them, as they do on the console. **It is built from typed values** as ADR-198 §2 built the account: an enumeration, a step name, a count, an instant, a `Path`, and a line of the jar's own printed whole; no method of `InvocationReport` takes a `Throwable`'s message or a document's field.

**The format and its schema.** The schema is `docs/report/report.schema.json`, JSON Schema 2020-12, using only `type`, `properties`, `required`, `items`, `enum`, `const`, `additionalProperties` and `description`, every field described; its `format` is a `const`. **A change that removes, renames or changes the meaning or type of a field raises `format`** by one, in the same change as the schema; a field added is no break, and a reader ignores a field it does not know. **A reader refuses a `format` it does not know**: the window (§8), and the jar where it reads a report of its own kind (§9). The record of each number is its schema's history in the repository.

### 4. The copy kept per invocation

**Each write of `report.json` writes the same text to `logs/report-<invocation.id>.json`**, the same way, so a process killed partway leaves its last state there, as ADR-198 §8 had the account leave its progress lines. The `logs` folder is made where it is not there (ADR-270 §2). A copy is never deleted or rewritten by a later invocation. It replaces `invocation-account-<start>.txt`, which no invocation writes from this record's build on; the accounts already written stay where they lie.

**What the account carried that the report does not**: every progress line in its order. The report keeps each step's last line, the samples behind the charts and the copy's last state; the whole run of lines stays in `logs/vespera.log`.

### 5. `report.html`, drawn from `report.json` through a template

**After each write of `report.json`, `ReportHtml` of `pipeline` draws `report.html` beside it**, through `.part` and `ReplacingMove`, from the text just written, read back, so the drawing is a function of `report.json` alone: the same `report.json` draws the same `report.html`. Nothing is served and nothing is opened.

**The engine is JMustache.** The pom gains, at compile scope, `com.samskivert:jmustache`, its version Spring Boot's dependency management gives, 1.16, under a comment naming this record. No `spring-boot-starter-mustache`. The compiler is built once per invocation:

- **its collector reads keys and nothing else**: a `Mustache.Collector` built on JMustache's `BasicCollector`, which resolves a name against a `Map`'s keys and walks a list, and never against an object by a method, a getter or a field, so `{{class}}`, `{{bytes}}` or any other name reaches no Java member;
- **its model is plain data**: `Map`, `List`, `String`, `Boolean`, `Long` and `Double` alone, made from the JSON read back and from §5's `drawn`; no lambda, no object of a class of the jar's;
- **it has no template loader**, so a partial, `{{> name}}`, does not compile;
- **`defaultValue("")`**, so a name the model does not hold draws nothing, and HTML escaping on, as JMustache ships it.

So a template reads the model and nothing else, and writes text: it cannot run code.

**The template's data model** is one map of two keys:

| Key | What it holds |
| --- | --- |
| `report` | `report.json`'s tree, as written, field for field |
| `drawn` | what a logic-free template cannot compute, derived from `report` alone by `ReportHtml`: `title`; `status`, ADR-269 §2's line and ADR-327 §2's sentence after the end; `writtenAt` and each step's times in the machine's zone; `refreshSeconds`, `invocation.interval` rounded up, at least 1; `tiles`, each `key`, `label`, `value`, `detail` and `hover` (ADR-268 §5, ADR-275); `timeline`, its `viewBox`, its axis's ticks, and each leg's and step's bar as `x` and `width` in ADR-324's 1,100 units, with its `title` and `hover`; `charts`, each chart's `viewBox`, `caption`, gridlines, each line's `points` as SVG's `x,y` pairs, `dash`, end label and latest value, every point's `title`, and its values as a `table` of `header` and `rows`; `steps`, `runs`, `gates`, `faults`, `refusals` and `reports` as rows of formatted cells; `files`, each `role` in words, `path` and `href`, its `file:` address; `display` and `opened`, each in words |

`drawn` is the template's alone and is not written into `report.json`.

**The shipped template** is the resource `io/algernon/vespera/pipeline/report.html.mustache` in the jar. It draws one self-contained page: one `<style>`, no `<script>`, no `fetch`, no stylesheet, font or picture outside it, no `{{{ }}}`, and a `Content-Security-Policy` of `default-src 'none'; style-src 'unsafe-inline'; img-src data:` in its head, so it opens from disk in any browser. Its links to the analysis reports are their bare file names, which lie beside it.

**The operator's template.** `vespera.report.template`, an application property named in `application-local.yaml` or the environment and unset as shipped, names a template file, absolute or against the current directory. It is read and compiled once, as the invocation starts. A file that cannot be read, or that does not compile, is one warning, `the report template <path> could not be used, so the jar's own draws report.html: <exception class name>`, and the jar's template draws every write of that invocation; a drawing that throws partway is the same warning and the same fall-back, for that write and the rest. `report.json`'s `template` says which drew it. It is no profile value and no preset: it enters no run (ADR-298), `effective-values.properties`, which holds no path, does not list it, and the window does not offer it, the child reading `application-local.yaml` as the command line does.

### 6. What replaces the progress page and the label page

**`report.html` is one page, the shipped template's**, replacing `progress.html`, `progress-steps.html`, `progress-samples.html`, `progress-runs.html` and `vespera label`'s `progress.html`, none of which is written from this record's build on, nor `progress.json`. A file of them an earlier version left stays where it lies, unread.

| Before | In `report.html`, as the shipped template draws it |
| --- | --- |
| the four tabs, each a page with a bar of tabs (ADR-269 §1) | one page of sections in this order: the status line; the tiles; the timeline of the four legs; the three charts; the steps; the gates and the next value; the refusals and the faults; the analysis reports, each with its headline numbers and its link; the runs; the files; the display and what was opened |
| the tiles, ADR-268 §5's, with ADR-275's hover | the same, from `drawn.tiles` |
| the timeline's legs as `<details>`, open where ADR-324 §4 opens them | the same, each leg's `open` from `report.legs` |
| a fold kept in the address by the script (ADR-324 §5) | none: a reload draws each leg as ADR-324 §4 opens it |
| the three line charts in one wrapping row, 600 units wide, never under 500 px (ADR-322) | the same, by the template's CSS and `drawn.charts` |
| the hover of each chart drawn by the script (ADR-269 §4) | each point's `<title>`, which a browser shows on hover without a script |
| `progress-samples.html` and `progress-steps.html` as the text alternatives (ADR-267 §7) | each chart's values as a table, and the steps as a table, each in a `<details>` beneath its drawing |
| `progress-runs.html` | the runs' section, from `report.runs` |
| the label page's tiles and table of answers (ADR-266 §3, ADR-267 §6) | the status line, the tiles of elapsed time and the database's size, and the table of answers, from `report.label`; no timeline and no chart |
| the reload by the script, and `<meta http-equiv="refresh">` inside `<noscript>` (ADR-324 §5, ADR-327 §1) | `<meta http-equiv="refresh" content="<refreshSeconds>">` outside any `<noscript>`, on every write, the last included, so an open tab shows the next invocation in this working directory, as ADR-327 §1 has it |
| the liveness mark and its staleness by the script (ADR-267 §5) | `Written at <time>` and the interval in the status line; the window marks a report stale (§8), and no page does |

The palette, its dark scheme and the contrast of ADR-268 §9 and ADR-267 §7 are the shipped template's stylesheet. A replaced template draws what it draws.

### 7. The console lines

**Every console line of the commands stays as it is, but for the two of the report.** As its first line, where ADR-266 §1 logs `Progress page: <path>`, each invocation that writes the report logs, through the command's own logger at INFO:

```
Report: <absolute path of report.html>
```

and, as its last line, once its last write of the report is made and after its closing line, one line through the same logger, which this record calls the **report line**:

| Where | The report line |
| --- | --- |
| a display present | `Report: <report.json>, drawn as <report.html>. Nothing was opened: a command opens no window, and the control panel's is the only one.` |
| no display | `Report: <report.json>, drawn as <report.html>. Nothing was opened: there is no display here (<reason in words>).` |
| the last write failed | `The report could not be written to <report.json>: <exception class name>. Nothing was opened: ` and the clause above that holds |

Each path is absolute and in the system's form, as ADR-266 §1 writes one. **The closing line is the last line but one**: the operator reads the value to set next on it, the report line after it. `ExtractionHealthCheckListener` and the failure lines of stage 1 and stage 4a stay the closing lines they are. `TaskExecutorJobLauncher`'s and Hikari's lines stay off the console at INFO (ADR-281 §1), so nothing follows the report line.

### 8. The control panel

**The window reads `report.json`, and no `progress.json`.** `ReportFile`, a class of the control panel built on its own `JsonReader`, replaces `ProgressFile`:

```java
public static final String FILE_NAME = "report.json";
public static final int FORMAT = 1;
public record Report(int format, Invocation invocation, List<Step> steps, List<Chart> charts, ...) {}
public static Report read(String json);   // IllegalArgumentException where it is not that JSON, or its format is not FORMAT
```

its records the fields §8 shows, by §3's names, and ADR-344 §2's `Step`, `Chart` and `Line` as they are. It reads the working directory's report every `invocation.interval` while its child runs, and once as it ends; a file not there, unreadable or not that JSON leaves the window as it was, and one of another `format` leaves it so too, `outcome` saying *the report is of format `<N>`, which this window does not read*.

**What it shows natively**, drawn by JavaFX's controls from `ReportFile.Report` and never by a `WebView`:

- the steps' bars and the three charts, as ADR-344 §1 and §2 draw them, from `steps` and `charts`;
- **a tab `live-report`, titled Report, first of the tabs pane** and not closable, holding the status line, the display and what was opened, the gates with their keys and the reports each is read off, the refusals, the faults by type, the closing line and the next value, each analysis report's headline numbers, and the files; a report's name opens that report's tab (ADR-344 §6), and the deliverable's index is handed to the system (ADR-344 §6's row for a file of a deliverable). `report.html`'s and `report.json`'s paths are shown as text, never opened;
- **stale**, beside the status line, where `writtenAt` is more than three `interval`s behind the window's clock while the report's `state` is `running`, as ADR-267 §5 had the script mark it.

**How the child ended** (ADR-278 §5) is shown with the `closing` of the report the child wrote, read once the child has ended, where its `invocation.started` is at or after the child's start; a child that wrote none, refused before the lock, is shown with the last line of output that is not blank, its refusal.

**`Reports.ALL` holds the twelve analysis reports**, in their order there, and none of the four progress pages: the window opens no `report.html` in a tab, the Report tab being its view, and `javafx-web` stays for the twelve (ADR-342 §1, ADR-344 §6).

### 9. The analysis reports write their numbers as JSON

**Each of the twelve writes `<name>.json` beside `<name>.html`**, its name the page's with `.json` for `.html`, by the class that writes the page, at the moment it writes it, through `.part` and `ReplacingMove`. It holds:

- `format`, the report's own, from 1, and `report`, its name;
- `headline`, the few numbers the page states first, named in its schema;
- every other number the page shows, under the names its schema gives: counts, shares, measured values, the bands of a distribution and the floor or value it suggests.

**It names no document.** A page that lists documents, the review list, the survivor list, the relevance labelling page and the arrangement page, writes the counts of its rows into its JSON and not the rows: no path, no file name, no title, no cluster label, no text and no reason of a document is in any of the twelve JSON files. The arrangement's clusters are told by their seed partition's and their own ordinals, with their sizes.

Each schema is `docs/report/<name>.schema.json`, by §3's rules, a `format` raised on a break. **Their HTML stays as their writers draw it** through `ReportPage` (ADR-130). Moving one report to a drawing from its JSON through a template of its own is a ticket of its own, one report at a time, and this record decides none of them.

### 10. What other records carry

Where a record names the progress page or the invocation account as a place a line or a count is carried, as [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md)'s progress lines, ADR-274 §8's counter and [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md) §5's line of megabytes pulled are, the report carries it, in its step's `progress`, from this record's build on. The line and the count do not change.

### 11. The modules, and run ids

**No run id moves.** The classes this record adds or changes are of `pipeline`, `ProgressPage` giving way to `InvocationReport` and `ReportHtml`, with `DisplayPresence`, `InvocationAccount`, `ShutGates`, `VesperaCommand`, `VesperaJobConfiguration`, `GenerationTasklet` and the analysis reports' writers; of `deliverable`, `ClusterPageSizes`; of the control panel; and `io.algernon.EntryPoint`. No stage's version names `pipeline` or `deliverable` (ADR-222, `StageModules`), and a report decides no verdict, cache key, cluster or text of the deliverable. **Nothing under `corpus`, `extraction`, `similarity`, `embedding` or `synthesis` changes**: a step of §12 that finds it needs to is outside this record. Each class added to `pipeline` is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule. `vespera.report.template` and `vespera.progress-interval` are part of no run's configuration.

### 12. The build, in steps that each land green

Each step leaves the build and every claim of the records it touches true on its own, in this order:

1. **The display check.** `DisplayPresence` (§1), and `EntryPoint` asking it before the toolkit, with §2's line where none is present and `vespera.headless` set for `--headless`. Nothing else changes.
2. **`report.json` and its copy**, written by `vespera run` and `vespera label` beside the files written today (§3, §4): `InvocationReport`, its schema, `ShutGates` given the key, `InvocationAccount` handing its counts to the report as well as to its file. `reports[].headline` is `null` for every report until step 5.
3. **`report.html`** drawn through JMustache from the shipped template, and `vespera.report.template` (§5, §6), beside the progress pages still written.
4. **The window reads `report.json`** (§8): `ReportFile` in place of `ProgressFile`, the Report tab and the stale mark; `Reports.ALL` unchanged until step 8.
5. **The analysis reports' JSON** (§9), one ticket a writing class: `ByteLevelReductionTasklet`, `ReviewListListener` with `SurvivorListListener`, `ContentCensusTasklet`'s three, `SeedCorpusComparisonTasklet`, `TranslationTasklet`, `ClusteringTasklet`, `RelevanceLabellingReport`, `ArrangementTasklet`, and `ClusterPageSizes`, each with its schema, `report.json` copying each headline as it lands.
6. **Nothing is opened** (§2): `PageOpener`, `SystemPageOpener` and `RecordingPageOpener` go, `vespera.open-browser` is read by nothing and leaves `application.yaml`, `EffectiveValuesFile` and `ChildCommand`'s list, the index is handed to no opener, and the report's `display` and `opened` say why.
7. **The console lines** (§7): `Report: <report.html>` first, the report line last, and the window showing how its child ended from the report's `closing` (§8), in one change, so the window never shows a report line as how a child ended.
8. **The progress pages, `progress.json` and the account retired** (§4, §6): `ProgressPage`'s pages and the label page, `progress.json` and `invocation-account-*.txt` no longer written, `Reports.ALL` the twelve, and the README, `AGENTS.md`, `CONTEXT.md` and `docs/architecture.md` saying so.
9. **`vespera publish` writes the report** (§3), with its `publish` field and its two lines.

## Consequences

- One file says how an invocation stands, for a person, a script, CI and an agent alike, and every view of it, the page, the window and the console's last line, is drawn from it.
- No command opens a browser or a window on any machine, so a run on a machine without a display, or over a remote shell, tries nothing graphical; the operator opens `report.html` from its path, or reads the window.
- A start of the jar with no argument on a machine with no display loads no JavaFX class and answers as the command line in the time the check takes.
- The operator who reads the last line for the value to set next reads the line before it.
- `report.html` reloads in an open tab as the progress page did, with no script, so a leg folded by hand opens again at each reload and a chart shows a point's value only by the point's own title.
- The account's place in `logs/` holds a JSON copy per invocation, `vespera label`'s and `vespera publish`'s among them, naming paths it did not name before and holding each step's last progress line where it held them all.
- The jar gains JMustache, and twelve JSON files lie beside the analysis reports.
- No run id moves.

## Known limits

- **The macOS and Linux answers were not run**: the macOS call and the variables are the systems' documented rules; on Linux a `DISPLAY` naming a server that does not answer is a display present, and the toolkit's failure is then the answer (§2).
- **A Windows process whose station is not visible was not run**; its answer is the system's documented flag.
- **A fold of the timeline does not outlive a reload** of `report.html`, nor does a hover's crosshair exist.
- **A replaced template is held only to reading the model**: what it draws, whether it carries a script, and whether it escapes what it writes with `{{{ }}}` are the operator's.
- **The report's staleness is shown by the window alone**; a browser shows the time written and the interval, and a reader judges.
- **The copy per invocation is rewritten at each write**, so `logs/` holds one file per invocation of a size the report's, about the samples' 256 rows at most.

## Alternatives refused

- **A display read from a GPU, a graphics driver or OpenGL.** A window needs a desktop to draw on, not an accelerator; a machine with a card and no one logged on has none.
- **The toolkit's own failure as the only answer** (ADR-342 §2). It loads JavaFX on a machine that cannot show it, and says nothing a command could write in its report.
- **An option forcing a display present.** The operator gave `--headless` alone, forcing none; a display forced where none is would try what cannot succeed.
- **`SESSIONNAME` on Windows.** It is set by the logon for the desktop it starts, and absent over a remote shell or a service, but it is a variable any parent may set or drop; the window station is the system's own answer.
- **Each command opening `report.html` where a display is present.** The operator made the window the only window.
- **XML, or several files.** The operator chose one JSON file, which Jackson writes and every reader parses.
- **`report.html` served, or drawing itself from `report.json` with a script.** A `file:` page cannot read another file in the browsers an operator uses (ADR-327's Context), and a server is a program the invocation would have to keep.
- **The template's model as `report.json` alone.** Mustache computes nothing, so a chart's coordinates and a time in the machine's zone would be beyond any template; `drawn` derives them from the report and nothing else.
- **JMustache's default collector.** It resolves a name by reflection on any object in the model, so a replaced template could reach a Java member; the keys alone are what a logic-free template needs.
- **FreeMarker, Thymeleaf and JTE.** Each lets a template reach code: FreeMarker's `?new`, Thymeleaf's expressions, JTE's templates compiled to classes. **Pebble** is the named step up if a template later needs logic, and is not taken now, its filters and functions being more than drawing from a model needs.
- **The progress page kept beside `report.html`.** Two drawings of one invocation drift, and the operator replaced it.
- **The invocation account kept beside the copy.** Two files per invocation for one hosted reader; the copy is the account's successor.
- **The progress lines' history in the report.** Rewritten every interval, the report would grow with the invocation; `vespera.log` keeps every line.
- **The analysis reports' JSON listing their documents.** The twelve JSON files are for their numbers; a page's rows by document stay the page's and the database's, and a list of paths and reasons in a file meant for scripts and agents would carry what a converter or a verdict wrote about a document.
- **`report.html` opened in a tab of the window's `WebView`.** The operator wanted the live report drawn natively, from the file every other view reads.
- **A new last line replacing the closing line.** The operator kept the console's lines; the closing line stays whole, before the report line.

## Tests

Owed by the build, step by step (§12):

| Method | What it holds |
| --- | --- |
| `pipeline.DisplayPresenceTest.onLinuxADisplayIsPresentWhereDisplayOrWaylandDisplayIsSetAndNotBlank` | each variable alone, both, neither, a blank one: present, present, present, none, none, with their reasons (§1) |
| `pipeline.DisplayPresenceTest.onWindowsADisplayIsPresentWhereTheWindowStationIsVisible` | the Windows seam answering visible and hidden: present and none, `visible-window-station` and `hidden-window-station` (§1) |
| `pipeline.DisplayPresenceTest.onMacOsADisplayIsPresentWhereTheWindowServerAnswers` | the macOS seam answering a dictionary and none (§1) |
| `pipeline.DisplayPresenceTest.headlessIsNoDisplayWhateverTheSystemSays` | `headless` with every system and seam answering present: none, `headless`, no seam asked (§1) |
| `pipeline.DisplayPresenceTest.aNativeCallThatThrowsIsNoDisplay` | a seam that throws: none, `not-answered` (§1) |
| `pipeline.DisplayPresenceTest.onWindowsThisMachinesWindowStationIsAsked` | on Windows alone, `ofThisProcess()` answers one of the two Windows reasons without throwing (§1) |
| `EntryPointTest.withNoDisplayTheWindowIsNotTriedAndTheJarAnswersAsItsCommandLine` | no argument and no display: no control panel class loaded, §2's line, then the command line with no argument (§2) |
| `EntryPointTest.headlessFirstSetsTheHeadlessProperty` | `--headless run <root>`: `vespera.headless` set and `run <root>` passed on (§1) |
| `pipeline.InvocationReportInvocationTest.aRunWritesTheReportAndItsCopyAsItGoes` | during a step, `report.json` and `logs/report-<id>.json` hold the same text, `state` `running`, the step `running` with its progress line, and a later write a later `writtenAt` (§3, §4) |
| `pipeline.InvocationReportInvocationTest.theLastWriteSaysHowTheInvocationEnded` | after a completed run: `ended`, `completed`, exit code 0, `counts` filled, `closing` the closing line, `arrivedAt` the runs (§3) |
| `pipeline.InvocationReportInvocationTest.theReportHoldsNoTextOfADocument` | a marker in every document's text, the converter's message, each cluster's title and a failing step's message occurs nowhere in the report or its copy (§3) |
| `pipeline.InvocationReportInvocationTest.aStepStoppedAtAGateNamesTheKeyItWants` | the boilerplate floor unset: stage 4a's step in `gates` with `boilerplateDocumentFrequencyFloor` and `document-frequency.html` (§3) |
| `pipeline.InvocationReportInvocationTest.aFailedStepIsAFaultByTypeAlone` | a step failing: its `faults` entry its type and causes, and no message (§3) |
| `pipeline.InvocationReportInvocationTest.aRefusalAfterTheLockIsWrittenWithItsLine` | a missing root: `outcome` `refused`, the refusal line in `refusals` and `closing` (§3) |
| `pipeline.InvocationReportInvocationTest.anInvocationRefusedBeforeTheLockLeavesTheReportAsItWas` | a working directory in use: `report.json` byte for byte as the holder left it (§3) |
| `pipeline.InvocationReportInvocationTest.theReportLinksEveryAnalysisReportAndCopiesItsHeadline` | after a run past stage 3: the twelve listed, those written `written` with their JSON's `headline`, the rest not (§3, §9) |
| `pipeline.InvocationReportTest.aReportThatCannotBeWrittenFailsNothingAndIsWarnedOfOnce` | a folder in the report's place: the run completes, one warning (§3) |
| `pipeline.InvocationReportTest.aSecondInvocationInTheSameSecondTakesASuffix` | two ids of one second: `-2` on the second copy (§4) |
| `pipeline.TheReportFollowsItsSchemaTest.everyFieldWrittenIsInTheSchemaAndEveryRequiredFieldIsWritten` | reports of a run, a label, a refusal and a publish against `docs/report/report.schema.json`, field by field (§3) |
| `pipeline.TheReportFollowsItsSchemaTest.theSchemasFormatIsTheJarsAndItUsesOnlyTheKeywordsAllowed` | the schema's `format` `const` is `InvocationReport.FORMAT`, and it names no keyword outside §3's eight (§3) |
| `pipeline.LabelWritesTheReportInvocationTest.theLabelCommandsReportCountsItsAnswers` | `vespera label` recording one new answer: `command` `label`, `outcome` `recorded`, `label.answers` 1, 0, 0, 0, `steps` empty, its copy in `logs` (§3, §4) |
| `pipeline.ReportHtmlTest.theDrawingIsAFunctionOfTheReportAlone` | one `report.json` drawn twice, and at two times of day: the same `report.html` (§5) |
| `pipeline.ReportHtmlTest.theShippedTemplateHoldsNoScriptAndReloadsAtTheInterval` | the shipped template's drawing: no `<script>`, no `fetch`, no `{{{`, its policy in the head, one refresh at the interval on a running and an ended report (§5, §6) |
| `pipeline.ReportHtmlTest.aTemplateReadsTheModelsKeysAndNoJavaMember` | a template naming `{{class}}`, `{{bytes}}` and `{{report.invocation.class}}` draws nothing for them; one naming a partial does not compile (§5) |
| `pipeline.ReportHtmlTest.aNamedTemplateDrawsTheReport` | `vespera.report.template` naming a file: `report.html` its drawing, `template.used` `named` (§5) |
| `pipeline.ReportHtmlTest.aNamedTemplateThatCannotBeUsedIsOneWarningAndTheJarsDraws` | a file not there, and one that does not compile: one warning each, the jar's drawing, `template.used` `jar` (§5) |
| `pipeline.ReportHtmlTest.everyDivOfTheShippedTemplateSaysOnHoverWhatItShows` | every `<div>` of the drawing carries a `title` of fixed words (§6, ADR-275) |
| `pipeline.ReportHtmlTest.everyTextOfTheReportIsEscaped` | a working directory whose name holds `<`, `&` and `"`: escaped wherever drawn (§5) |
| `pipeline.ReportHtmlTest.theLabelReportIsDrawnWithItsAnswersAndNoTimeline` | a label report: its table of answers, no timeline and no chart (§6) |
| `pipeline.TheControlPanelReadsTheReportTheJarWritesTest.theReportTheJarWritesIsReadWhole` | `InvocationReport` writes and `ReportFile` reads: the invocation, the steps in order with their states, the charts, the gates and the closing line (§8) |
| `controlpanel.ReportFileTest.aFormatTheWindowDoesNotKnowIsRefused` | `format` 2: `read` throws, and the window's `outcome` says the line (§8) |
| `controlpanel.TheLiveReportTest.theReportTabShowsTheInvocationNatively` | `live-report` first and not closable, holding the gates, the faults and the closing line, with no `WebView` in it (§8) |
| `controlpanel.TheLiveReportTest.aRunningReportNotRewrittenForThreeIntervalsIsShownStale` | a running report written four intervals ago: stale; an ended one: not (§8) |
| `controlpanel.TheLiveReportTest.howTheChildEndedIsTheClosingLineOfItsReport` | a child whose report holds a closing line and whose output ends on the report line: the closing line shown (§8) |
| `pipeline.EveryAnalysisReportWritesItsNumbersTest.eachWritesItsJsonBesideItsPageWithTheSameNumbers` | for each of the twelve, the numbers of its page equal its JSON's (§9) |
| `pipeline.EveryAnalysisReportWritesItsNumbersTest.noAnalysisReportsJsonNamesADocument` | a marker in every document's path, name and text occurs in none of the twelve JSON files (§9) |
| `pipeline.EveryAnalysisReportWritesItsNumbersTest.eachJsonFollowsItsSchema` | each JSON against `docs/report/<name>.schema.json` (§9) |
| `pipeline.NoCommandOpensAnythingTest.noClassOfTheJarNamesAWayToOpenAFile` | no compiled class of `io.algernon.vespera` names `rundll32`, `xdg-open`, `java.awt.Desktop` or `HostServices`, nor starts `open` (§2) |
| `pipeline.NoCommandOpensAnythingTest.theReportSaysWhyNothingWasOpened` | a run with a display present and with none: `opened` empty, `because` `commands-open-nothing` and `no-display` (§2) |
| `pipeline.TheReportLinesInvocationTest.theFirstLineNamesTheReportsDrawing` | the first line `run` logs is `Report: ` and `report.html`'s absolute path (§7) |
| `pipeline.TheReportLinesInvocationTest.theLastLineNamesBothFilesAndWhyNothingWasOpened` | the last line names `report.json` and `report.html`, and the clause of a display present and of none; the closing line before it (§7) |
| `pipeline.TheReportLinesInvocationTest.aLastWriteThatFailsIsSaidOnTheLastLine` | a folder in the report's place at the last write: the line's failure form (§7) |
| `pipeline.TheReportLineEndsTheOutputIT.eachCommandEndsOnItsClosingLineThenTheReportLine` | the packaged jar, `run` to a value to set, `run` failing at stage 2, `label` recording and a `run` refused for a missing root: the last line the report line, the line before it the closing line (§7); `pipeline.TheClosingLineIsTheLastLineIT` is renamed to it, its four methods merged (ADR-337 §3) |
| `CliExitIT.aJarThatCannotOpenTheWindowAnswersAsItsCommandLine` | the packaged jar with no argument and no display, or a glass platform JavaFX does not have: §2's line, the usage and picocli's usage code (§2) |
| `pipeline.NoInvocationWritesTheRetiredFilesInvocationTest.aRunAndALabelWriteNoProgressPageNoProgressJsonAndNoAccountText` | after a run and a label: none of the five progress files, no `progress.json`, no `invocation-account-*.txt` (§4, §6) |
| `pipeline.TheControlPanelListsEveryReportTheJarWritesTest.everyReportTheJarWritesIsListedByTheControlPanel` | `Reports.ALL` is the twelve analysis reports' file names, each once (§8) |
| `pipeline.PublishWritesTheReportInvocationTest.eachDestinationsOutcomeAndLineIsInTheReport` | a publish to both destinations, one stopping: `publish` two entries, `outcome` `stopped`, the report line last (§3, §7) |

The tests of what goes go with it, in step 6 or 8: `pipeline.SystemPageOpenerTest` and `pipeline.NoTestOpensABrowserTest` are removed; `pipeline.ProgressPageOpensInvocationTest`, `pipeline.BrowserNotOpenedWhenSetInvocationTest`, `pipeline.ProgressPageTabsTest`, `pipeline.ProgressPageInvocationTest`, `pipeline.LabelWritesTheProgressPageInvocationTest` and `pipeline.TheControlPanelReadsTheProgressTheJarWritesTest` are removed, each claim they hold that this record keeps held by a row above; `pipeline.InvocationAccountTest`'s claims of the file are removed and its claims of the counts held by `pipeline.InvocationReportInvocationTest`. Each record whose Tests section names one is corrected in place by the step that removes it.

## What the implementation owes

Beside turning the tests above green, step by step: the schemas in `docs/report/`; the pom's dependency and surefire's `--enable-native-access=ALL-UNNAMED`; the shipped template and its stylesheet; `ShutGates.record(String step, String key)`, each gate's call given its key; `ProgressPage.STEPS_OF_A_STAGE` moved to `InvocationReport`, `pipeline.TheStopCostsNameTheJarsStepsTest` reading it there; the README's table of files written beside the database, its sentences on the progress page, the browser, `vespera.open-browser`, the invocation account and the last line, and its section on the control panel; `AGENTS.md`'s list of integration tests, for the one renamed; `CONTEXT.md`'s entries for the progress page, the invocation account and the control panel, and the working directory's list of what it holds; `docs/architecture.md`'s paragraphs on the progress page and the control panel; and this record's number taken out of `docs/check-claims.mjs`'s `NOT_YET_BUILT` by the step that builds its last row.

## What this does not decide

- **Whether `next-invocation.properties` and `effective-values.properties` fold into the report.** Both stay the control panel's inputs (ADR-294, ADR-330); the report's `next` is written from the same value as the first.
- **Each analysis report drawn from its JSON through a template of its own** (§9).
- **A retention rule for the copies in `logs/`**, as ADR-198 left one for the accounts.
- **Whether `vespera serve` writes a report.** It holds no lock and opens no database (ADR-333 §8).
- **The words of the window's Report tab** beyond what §8 says it shows, and the shipped template's look beyond §6.
