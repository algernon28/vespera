# ADR-364 — The output panel is copyable text of the whole output, from a file of its own, and every text of the window is copyable

- **Date**: 2026-11-07
- **Status**: accepted on 2026-11-07.
- **Decided**: by the operator, on [#768](https://github.com/algernon28/vespera/issues/768): the term **Output panel**; that the panel is a read-only text box whose any part is selected and copied with Ctrl+C or the context menu, with no button that copies it all (§1); that it shows the invocation's whole output, held in `logs/output-<invocation>.log`, written by a second appender with the console's pattern and level, its `<invocation>` ADR-362's, `vespera.log` unchanged and no dependency added (§2, §3); that the banner is logged and every line the jar writes goes through logback, the lines printed outside it today on a logger of their own, `io.algernon.vespera.operator`, as their words alone, a line of standard error at WARN on standard error, a test holding that no class but `EntryPoint`, for its lines before Spring starts, names `System.out` or `System.err` (§4, §5); that each invocation deletes the output files older than the 14 days `vespera.log` keeps (§6); that standard error stays merged into standard output; that the panel follows new lines only while its view is at the bottom (§1); that the window keeps reading the child's stream, a short tail inside `Child` kept for ADR-362 §8's fall-back alone, and shows the output file once there is one and the stream until then (§7); that every text of the window that is not a control can be selected and copied, a tab's title and a button's text staying as they are, and a tooltip's text outside the rule (§8). Claude decided the rest, as details of the build: how the appender learns the invocation and what it holds before it (§2, §3), how the window finds the file of an invocation it did not start (§7), the rule of the 14 days and who applies it (§6), the control a copyable text is and which texts are a control's (§8), the rule of following and its test (§1), and the tests.
- **Amends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md)'s "Two appenders": a third place, each invocation's output file, and the jar's own lines on a logger of their own, both added to `logback-spring.xml`, and the banner logged (§2, §4, §5). It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose `output` is the last 200 lines: it is the output panel, the invocation's whole output as text (§1, §7), and each text of the table is selectable (§8). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, whose id is handed to the output before the report's first write (§3); §4, whose `logs/` holds the output file of the same id beside the report's copy (§2); and §8, whose report the window accepts as the watched invocation's also names the output file the panel shows (§7). Its open question on the copies' retention stays open: the 14 days are the output files' alone (§6). It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md)'s refusal of *the output copied to a file*: the jar writes its own output to a file of the invocation's (§2); §3, whose control panel keeps, beside the tail, the lines read until that file is found, still writing nothing of what it reads and reading the one stream with one thread (§7); and Context's *Start-up*, whose JVM lines are in the stream and not in the file (Known limits). It carries a note at its head.
- **Amends**: [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md) §1: the two loggers write to the output file at WARN, as to the console (§2). It carries a note at its head.
- **Amends**: [ADR-270](0270-every-log-vespera-writes-is-in-the-working-directorys-logs-folder-the-invocation-account-among-them.md) §1: the `logs` folder holds the output files beside `vespera.log`, and `vespera.log` gains the banner and the jar's own lines (§2, §4, §5); and §6, whose README and `CONTEXT.md` lines name the output files (What the implementation owes). It carries a note at its head.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md)'s *The last line* and ADR-281 §2, as ADR-362 §7 amends it: the jar's own lines read as their words alone, as they do, so the closing line stays what it is. [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) and [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): each refusal stays on standard error, with its exit code. [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §4: the window reads no document; the output file is a log, read as `vespera.log` is.
- **Rests on**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): nothing is added to the pom. Logback is the jar's already (ADR-093), and the `TextArea` is `javafx-controls`'.
- **Settles**: [#768](https://github.com/algernon28/vespera/issues/768).

## Context

**The output panel today.** `Cockpit.output` is a `ListView<String>` beside the steps in a `SplitPane` (ADR-344 §1). Every tick of the window's clock, once a second, it is given `ControlPanelState.tail()`, the last `Child.TAIL_LINES`, 200, lines of the child's standard output with standard error merged in (`Child.start`, `redirectErrorStream(true)`), all of its lines replaced and the last scrolled to. A row of a `ListView` is selected as a row, and nothing in it is copied. `Child.waitForEnding` takes how a child ended, its last line that is not blank, from the same tail (ADR-278 §5).

**Logback today** (`src/main/resources/logback-spring.xml`). `CONSOLE`, on standard output in the pattern `%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n`; `FILE`, `logs/vespera.log` in the working directory, one file for every invocation, rolled at 100 MB, kept 14 days, capped at 2 GB; and `CONSOLE_WARN`, the console's pattern at WARN, which `com.zaxxer.hikari` and Spring Batch's `TaskExecutorJobLauncher` write to with `FILE`, with no additivity (ADR-281 §1). The root logs at INFO to `CONSOLE` and `FILE`. A test JVM reads `src/test/resources/logback-test.xml`, which shadows `logback-spring.xml` and has no file appender. `application.yaml` sets no `spring.main.banner-mode`, so Spring prints its banner to standard output, outside logback.

**Lines printed outside logback.** 32 lines of 5 classes name `System.out` or `System.err`: `VesperaCommand` 27 (20 of standard error: refusals and `label`'s and `publish`'s lines; 7 of standard output: the usage of bare `vespera`, `label`'s two lines on what it recorded and the next value, and `publish`'s lines, a destination's handed `System.out::println`), `MisshapenProfileRefusal` and `WorkingDirectoryInUseRefusal` one each, `ServeCommand` one, its public constructor handing both streams to the command, and `EntryPoint` two, the lines it prints before Spring where the window cannot open. `println` writes in `stdout.encoding` and `stderr.encoding`; logback's encoders name no charset and write UTF-8 (ADR-278's Context). The two refusals are printed by Spring's failure reporters, while the environment is prepared and after `LoggingApplicationListener` has configured logging: the four listeners `VesperaApplication` adds declare no order and so hear the event after it. Picocli writes its own refusal of an argument to its `getErr()`, standard error unless set. `vespera serve` is handed on before `SpringApplication` is built, and Spring never configures logging for it (ADR-333 §8).

**The invocation's id** is ADR-362 §3's `invocation.id`, the UTC start as `yyyyMMdd'T'HHmmss'Z'` with `-2` and on for a second in the same second, minted once the command runs and holds the working-directory lock; it names the report's copy, `logs/report-<id>.json` (ADR-362 §4). An invocation refused before the lock has none. That record is accepted and not yet built.

**The window's read-only texts** are `Label`s: the header's `where` and `child`, `outcome`, each step's stage, name, state and cost, the launch fields' headings, statuses, starting lines and hints, the preflight lines, the code sets listed, the command lines, a report not written yet, the profile rows' titles, keys, descriptions, values in use and costs, the profile's problems and the outcomes of each Save. A `Label` draws its text and selects none of it.

## Decision

### 1. The output panel

**The output panel**, `output`, is a `TextArea`, not editable, wrapping no line, in the style class `output`, where the `ListView` stood. The operator selects any part of its text with the mouse or the keyboard and copies it with Ctrl+C or with Copy in its context menu, the `TextArea`'s own, whose Cut, Paste, Delete, Undo and Redo are disabled since it is not editable, and whose Select All stays. Nothing else copies it: there is no button.

**What it holds** is §7's: the watched invocation's whole output, as text, a line to a line.

**It follows new lines only while its view is at the bottom.** Before each change of its text the window reads whether to follow: it follows where its view is at the bottom, its scroll at its end within one line's height, or it holds no text yet; and no text is selected. Following, the new text is added and the view is scrolled to its end. Not following, the new text is added and neither the view's scroll nor the selection moves. So a view the operator scrolled up stays where it is, and so does one in which text is selected, until the operator scrolls back to the bottom and nothing is selected; from the next change on it follows again.

### 2. The output file

**Each invocation that writes the invocation report (ADR-362 §3) writes `logs/output-<invocation.id>.log`** in its working directory, the id ADR-362 §3 mints and §4's copy is named by, beside it and beside `vespera.log`. The folder is made where it is not there, as ADR-362 §4 makes it.

**What it holds is what the invocation writes to its standard output and standard error through logback**, in the order logged, in UTF-8, a line flushed to the file as it is written, so a process killed by Stop leaves every line it logged before the kill:

- every line `CONSOLE` writes, in the console's pattern;
- every line `CONSOLE_WARN` writes, at WARN and above, in the console's pattern, so `com.zaxxer.hikari`'s and `TaskExecutorJobLauncher`'s lines below WARN are no more in it than on the console;
- every line of the jar's own (§4), as its words alone, whichever stream the console writes it to.

`vespera.log` is unchanged in its place, its rolling and its pattern, and keeps every line, the banner and the jar's own lines now among them (§4, §5, ADR-270). The output file is never rolled and has no cap: it is one invocation's, deleted by §6 alone.

**One sink, three appenders.** `InvocationOutput`, a class of `pipeline`, is the file's one sink, one to a logger context, held in it as an object under a name of its own, so that every appender of the context writes to the one file and a line is written whole before the next, whatever thread logs it. `InvocationOutputAppender`, a logback appender of `pipeline` with an encoder and the filters `logback-spring.xml` gives it, hands each event's bytes to that sink. `logback-spring.xml` declares three: `OUTPUT`, in the console's pattern, which the root writes to beside `CONSOLE` and `FILE`; `OUTPUT_WARN`, in the console's pattern with a `ThresholdFilter` at WARN, which the two loggers of ADR-281 §1 write to beside `CONSOLE_WARN` and `FILE`; and `OUTPUT_OPERATOR`, `%msg%n`, which §4's logger writes to. Each names `<folder>${workingDir}/logs</folder>`, the folder `FILE` writes in, and `UTF-8`.

**Before the invocation begins** the sink holds what it is handed, in memory, in order: the banner (§5), Spring's start-up lines and anything logged before the lock. At §3's begin it writes what it holds into the new file, first, and from then on writes each line as it comes. It holds 4 MiB at most; where more would be held it drops the oldest lines whole, and at the begin writes first one line of the jar's own, `<n> lines logged before this invocation began are not in this file; logs/vespera.log holds them`. An invocation that never begins, refused before the lock or by picocli, or a `ProfileShapeCheck` refusal, writes no output file: what was held is dropped as the context stops. `vespera serve` has no logback configuration of Spring's and writes none.

**A file that cannot be opened fails nothing**, as ADR-362 §3 has a report that cannot be written: one warning on `InvocationOutput`'s logger, `the output file could not be opened: <exception class name>`, what was held dropped, and every line after it dropped without another warning. The invocation goes on, its console and `vespera.log` as they are.

### 3. How the appender learns the invocation

**The class that mints `invocation.id`, ADR-362's `InvocationReport`, begins the output with it before the report's first write**: `InvocationOutput.begin(String invocation)`, reached through the logger context of the class's own logger, so that a report naming an id is never written before the output file of that id exists, or has been tried. `begin` is called once in an invocation; a second call is refused with an `IllegalStateException`. Nothing else names the id to logback: no system property, no MDC key and no property of the context, each of which a line logged before the id, or on another thread, would miss.

### 4. The jar's own lines go through logback

**Every line the jar prints outside logback today goes through logback, on the logger `io.algernon.vespera.operator`**, whose name `OperatorLines.LOGGER_NAME`, a class of `pipeline`, holds, with `additivity="false"`. A line printed on standard output today is logged at INFO; one printed on standard error, at WARN. **Each reads as it does today**, its words alone with a line's end, on the stream it was printed on:

- `OPERATOR`, a console appender on standard output, `%msg%n`, its encoder's charset `${stdout.encoding}`, taking INFO and below and denying WARN and above;
- `OPERATOR_WARN`, a console appender whose target is `System.err`, `%msg%n`, its encoder's charset `${stderr.encoding}`, with a `ThresholdFilter` at WARN;
- `FILE`, in its own pattern, as it takes every line (ADR-270);
- `OUTPUT_OPERATOR` (§2).

The charsets keep the bytes of a line on a console that is not UTF-8 what `println` wrote; the control panel's child is started with both encodings UTF-8 (ADR-278 §1), so its stream is UTF-8 throughout.

**Which lines.** `VesperaCommand`'s 27 and the two refusals' are logged through the operator logger at the same place and with the same words. `publish`'s destinations are handed the operator logger's `info` where they were handed `System.out::println`. The usage of bare `vespera` is logged as one INFO line holding `CommandLine.getUsageMessage()`, its own lines ends and all. Picocli's own refusals are written through it too: `VesperaCommand.commandLine()` sets the `CommandLine`'s `setOut` and `setErr` to `PrintWriter`s that log each line written to them, `OperatorLines.out()` at INFO and `OperatorLines.err()` at WARN.

**What stays outside logback is `EntryPoint`'s, before Spring starts**: its two lines where the window cannot open, and `vespera serve`, which `EntryPoint.main` now hands on itself, before `VesperaApplication.main`, giving `ServeCommand` `System.out` and `System.err` through its constructor that takes them, made public; `ServeCommand`'s constructor naming the two streams goes, and so does `VesperaApplication.main`'s hand-off of `serve`. A start with `serve` first that reaches Spring anyway is refused by picocli as today (ADR-333 §8).

**The test of it**: no class of the jar but `EntryPoint` reads the field `System.out` or `System.err`, and none calls `Throwable.printStackTrace()` with no argument, which prints on standard error.

**The test configuration routes the logger as the jar does**: `logback-test.xml` declares `OPERATOR` and `OPERATOR_WARN` as above and the logger writing to them, so that a test that reads a line on `System.out` or `System.err` reads its words as it did.

### 5. The banner is logged

`application.yaml` sets `spring.main.banner-mode: log`. Spring logs the banner at INFO on the logger of `VesperaApplication`, so it is in the stream in the console's pattern, in the output file (§2) and in `vespera.log`. The banner's text is Spring's own; no `banner.txt` is added.

### 6. Fourteen days

**At `begin`, before it opens its own file, `InvocationOutput` deletes each output file more than 14 days older than the invocation**: a regular file directly in `<working directory>/logs` whose name is `output-<id>.log`, `<id>` reading as §3's id with or without its suffix, and whose id's instant is more than 14 × 24 hours before the instant of the id being begun. A file of exactly 14 days is kept. The age is read from the name, never from the file's times, which a copy or a touch changes, as `vespera.log`'s rolled files are aged by the date in their names. Nothing else is deleted: a name that does not read as an output file's, a folder, the report's copies (ADR-362 §4 never deletes one), `vespera.log` and its rolled files, and anything outside `logs`. A file that cannot be deleted costs one warning on `InvocationOutput`'s logger, naming it and the exception's class name, and the next file is tried; the invocation goes on.

Only an invocation that begins deletes, in its own working directory, under the lock it holds; `serve`, a start refused before the lock and the control panel delete nothing.

### 7. What the window reads

**The window keeps reading its child's stream**, standard error merged in, with the one thread of ADR-278 §3. **`Child` keeps two things of it, in memory alone**:

- **the tail**, the last `TAIL_LINES`, 200, lines, for ADR-362 §8's fall-back on how a child ended alone: its last line that is not blank and does not start `Report: `; nothing shows the tail;
- **the held lines**, every line read from the start, in order, until the window lets them go, at most `HELD_LINES`, 10,000, the oldest dropped past it: `held()` gives a copy, and `letGo()` empties them and holds no line read after it.

**Which file is the watched invocation's.** The report ADR-362 §8 accepts as the watched invocation's, its `invocation.pid` the child's or, for an invocation the window did not start, the holder line's, and its `invocation.started` no earlier than the instant the window took before starting the child or than the holder line's `started` less a second, names the file: `<working directory>/logs/output-<invocation.id>.log`. The window finds the file of an invocation it did not start in the same way, and only so.

**What the panel shows.**

- While no accepted report names a file that is there, the panel shows the child's held lines, so a line written before Spring, or a refusal before the lock, is shown. For an invocation the window did not start it shows nothing.
- Once one is there, the panel's text is replaced, once, by the file's, and the child's held lines are let go. From then on, at each tick of the window's clock, the window reads the bytes the file has gained since its last read and adds the whole lines among them, decoded as UTF-8; a last line not yet ended waits for its end.
- A new child, or a foreign holder whose accepted report names another file, empties the panel; an invocation that has ended leaves its text in place until then.

### 8. Every text of the window that is not a control can be copied

**Every text the window shows outside a control is a `SelectableText`**, a class of the control panel: a `TextField` for a text drawn on one line and a `TextArea` for one that wraps, each not editable, not reached by the Tab key, in the style class `selectable-text`, which the stylesheet draws as the label it replaces, with no border and no background, its font, colour and padding the label's, and its width and height following its text as the label's did, so the window is laid out as before. Its text is selected and copied as §1's panel's is, with the same context menu. It keeps the id, the style classes and the wrapping the label had.

**Which texts.** The header's `where` and `child`; `outcome`; each step's stage, name, state and cost; every heading, status, starting line and hint of a launch field and of a preset; the converter's two image names; the preflight lines; the code sets listed; the command lines; a report not written yet; each profile row's title, key, description, value in use, cost, and the heading and hint of its provenance's field; the profile's problem and its lines; the Inclusions tab's and the folders not relevant's sentences; and every outcome line of a Save, an Add or a Repair. A text the window draws in a later build is held to the same rule, those of ADR-362's Report tab and native views among them: whichever of the two builds lands second applies it to the other's texts.

**What stays as it is**, the text of a control: a tab's title; a button's, a check box's, a radio button's and a link's text; the title of a sidebar section; a leg's summary, its name, bar and state, drawn in its `TitledPane`'s header, which folds it (ADR-361 §3); the rows of a list, a tree or a table, which the operator selects as rows; a chart's title, axes, ticks and legend, which the chart draws. **A tooltip's text is outside the rule**: it is help, shown while the pointer rests, and only visible text is covered. **A dialog's sentence is outside it too**: a dialog is a window of its own, which this record does not change.

**What "outside a control" is, as the test reads it.** From the window's root, the walk descends into every `Parent` that is neither a `Control` nor a `Chart`, and, of the controls, into a `ScrollPane`'s content, a `SplitPane`'s items, each `Tab`'s content, an `Accordion`'s panes and a `TitledPane`'s content, never its header; no other control is entered. A `Label` the walk reaches with a text that is not blank is a text the rule has missed.

### 9. The modules, and run ids

**No run id moves.** The classes added or changed are of `pipeline`, `InvocationOutput`, `InvocationOutputAppender`, `OperatorLines`, `VesperaCommand`, `MisshapenProfileRefusal`, `WorkingDirectoryInUseRefusal`, `ServeCommand` and, from ADR-362's build, `InvocationReport`; `io.algernon.vespera.VesperaApplication` and `io.algernon.EntryPoint`; of the control panel, `Child`, `ControlPanelState`, `Cockpit`, `ProfileRow`, `InclusionsTree` where it draws a text, `SelectableText` and the stylesheet; and `logback-spring.xml` and `application.yaml`. No stage's version names `pipeline` (ADR-222, `StageModules`), and none of these decides a verdict, a cache key, a cluster or a text of the deliverable. `spring.main.banner-mode` is part of no run's configuration. Each class added to `pipeline` is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule.

### 10. The build, in steps that each land green

1. **The jar's own lines** (§4): `OperatorLines`, the logger and its two console appenders in `logback-spring.xml` and `logback-test.xml`, `VesperaCommand`'s 27 lines and the two refusals' logged, picocli's writers, the usage, and `serve` handed on by `EntryPoint`. It needs nothing of ADR-362's.
2. **The banner** (§5): `spring.main.banner-mode: log`, with its comment. It needs nothing of ADR-362's.
3. **The output file** (§2, §3, §6): `InvocationOutput`, `InvocationOutputAppender` and the three appenders, begun by `InvocationReport`. It lands after ADR-362's step 2, which mints the id, and after step 1 of this list.
4. **The panel shows the output** (§1, §7): `output` a `TextArea` following the bottom, `Child`'s held lines, and the file found through the accepted report. It lands after step 3 and after ADR-362's step 4, by which the window accepts a report as the watched invocation's.
5. **Every text copyable** (§8): `SelectableText` in place of each `Label` §8 names. It needs nothing of ADR-362's and may land at any point; the step of the two builds that lands last applies §8 to the other's texts.

## Consequences

- **The operator copies any part of the output, or all of it with Select All**, from the start of the invocation, the banner and the lines before the lock included, where the window showed the last 200 lines and copied none.
- **The same lines are in three places**: the stream, the invocation's output file and `vespera.log`, which keeps them with dates and every library line at INFO. The output file is the one a person or a script reads for one invocation alone.
- **A refusal and the jar's other lines read as they did**, on the stream they were printed on, the closing line among them, now in the output file and in `vespera.log` as well.
- **`logs` grows by one output file per invocation that writes the report**, the size of its output; each is deleted by the first invocation that begins more than 14 days after it, in the same working directory.
- **A start of the jar now logs its banner**: in the stream its first line carries the console's time, level, thread and logger, and its other lines follow as Spring writes them.
- **Every read-only text of the window can be selected**, so the operator can copy a path, a refusal, a command line or a value in use, and the window looks as it did.

## Known limits

- **A `TextArea` holds its whole text and lays all of it out.** The output of a long run, a line per occurrence per stage at INFO (ADR-093), can reach hundreds of thousands of lines and tens of megabytes; how the panel behaves at that size was not measured. A virtualised text control would need a module the pom does not carry (Alternatives refused).
- **What the JVM or native code writes to the standard streams itself** is in the stream and in neither file, logback never seeing it.
- **Two threads' lines logged within one another's write** can stand in one order in the stream and in another in the file, since the console's appenders and the sink each take their own lock.
- **The replacement of the stream's lines by the file's** drops a selection the operator made in the first, and scrolls the panel to the end where it follows.
- **An invocation started by a jar built before step 3** writes no output file, so the panel shows nothing for it where the window did not start it, and the held lines where it did.
- **An output file is deleted only by a later invocation** that begins in the same working directory; a working directory no invocation begins in again keeps its files.
- **A line logged before the invocation began, past the 4 MiB held,** is in `vespera.log` and the stream alone.

## Alternatives refused

- **A `SiftingAppender` keyed by an MDC value.** The MDC is a thread's: a line logged on another thread, or before the id, would land in no file or in a default one.
- **The id set as a system property, or a property of the logger context, before Spring starts, and a plain `FileAppender` naming it.** The id is minted once the lock is held (ADR-362 §3), and an id minted earlier would name an invocation the lock refuses, whose report it never writes.
- **The window writing the stream to the file.** The window writes nothing of what it reads (ADR-278 §3), and an invocation it did not start would have no file.
- **The output files rolled, or aged by their last-modified time.** One file is one invocation's, and a copy or a touch changes a file's time where its name keeps its start.
- **The `ListView` kept, with a copy of the selected rows or a button that copies all.** The operator wanted any part selected, not rows, and no button.
- **`jfx.incubator.richtext`'s `RichTextArea`**, virtualised, for the panel. It is an incubating module the pom does not carry, and a dependency wants a record of its own (ADR-046).
- **A `Label` given a context menu with Copy.** It copies the whole text and selects none of it.
- **The 200-line tail kept as what the panel shows.** The operator wanted the whole output.
- **The jar's own lines in UTF-8 on every console.** Their bytes on a console that is not UTF-8 would change; their charsets are the streams' own.
- **The jar's own lines kept out of `vespera.log`.** It keeps every line an invocation logs (ADR-270, ADR-281's Alternatives refused).

## Tests

Owed by the build. The first column is the step of §10 that adds the test, and its green is that step's: no row lands before the code it holds.

| Step | Method | What it holds |
| --- | --- | --- |
| 1 | `OnlyTheEntryPointWritesToTheStandardStreamsTest.noClassButTheEntryPointNamesSystemOutOrSystemErr` | every class of `src/main` under `io.algernon`, imported by ArchUnit: none but `EntryPoint` reads `System.out` or `System.err`, none calls `Throwable.printStackTrace()` with no argument, and the import read `EntryPoint`'s reads, so that an import of nothing cannot pass (§4) |
| 1 | `CliExitIT.aRefusalIsPrintedOnStandardErrorAsItsWordsAlone` | the packaged jar started with `run`, naming no root and no `vespera.corpus-root`: standard error's last line is the refusal's words whole, with nothing before them on the line, and standard output holds none of them; exit 2 (§4) |
| 1 | `CliExitIT.theUsageIsPrintedOnStandardOutputAsItsWordsAlone` | `--headless` alone: standard output's last lines are picocli's usage of `vespera`, line for line as `CommandLine.getUsageMessage()` renders it for the command, with nothing before any of them; picocli's usage code (§4) |
| 1 | `CliExitIT.anArgumentPicocliRefusesIsSaidOnStandardErrorAsItsWordsAlone` | `run --bogus-option`: picocli's refusal and the usage on standard error, each line its words alone, none on standard output; exit 2 (§4) |
| 1 | `pipeline.OperatorLinesTest.whatIsWrittenToEitherWriterIsLoggedALineAtATime` | `OperatorLines.out()` and `OperatorLines.err()` given two lines and a third without its end, then flushed: three events on the operator logger, INFO and WARN as the writer, each line's words without its end (§4) |
| 2 | `LogsFolderIT.theBannerIsLoggedIntoVesperaLog` | `vespera label --db-dir=<path>`: `logs/vespera.log` holds the banner's `:: Spring Boot ::` line, logged on `io.algernon.vespera.VesperaApplication` (§5) |
| 3 | `pipeline.InvocationOutputTest.theLinesBeforeTheInvocationBeginsAreHeldAndWrittenFirst` | on a logger context of the test's own, the three appenders configured as §2 has them: three lines logged, then `begin`, then two: `logs/output-<id>.log` holds the five in order (§2, §3) |
| 3 | `pipeline.InvocationOutputTest.anInvocationThatNeverBeginsWritesNoFile` | lines logged and the context stopped with no `begin`: `logs` holds no file whose name starts `output-` (§2) |
| 3 | `pipeline.InvocationOutputTest.heldLinesPastTheBoundAreSaidInOneLine` | more than 4 MiB logged before `begin`: the file's first line names how many lines were dropped, and the lines after it are the newest held, each whole (§2) |
| 3 | `pipeline.InvocationOutputTest.eachLineIsInTheFileOnceItIsLogged` | after `begin`, each line logged is in the file, read by another stream, before the next is logged, with no flush or stop called (§2) |
| 3 | `pipeline.InvocationOutputTest.theLinesOfTheThreeAppendersStandInOneFileInTheOrderLogged` | the root's, the two loggers' and the operator logger's lines interleaved: one file, in that order, the first two in the console's pattern, the operator's its words alone, a line of the two loggers below WARN in none (§2, §4) |
| 3 | `pipeline.InvocationOutputTest.aSecondBeginIsRefused` | `begin` twice: `IllegalStateException`, one file (§3) |
| 3 | `pipeline.InvocationOutputTest.aFileThatCannotBeOpenedFailsNothingAndIsWarnedOfOnce` | a folder in the file's place: `begin` returns, one warning naming the exception's class, and the lines after it dropped with no other warning (§2) |
| 3 | `pipeline.InvocationOutputTest.outputFilesMoreThanFourteenDaysOlderThanTheInvocationAreDeleted` | output files whose ids are 14 days and one second, exactly 14 days, and one day before the id begun, and one of the same second with `-2`: the first deleted, the others kept; `output-notes.log`, `output-20260101T000000Z.txt`, a folder named as an output file, a `report-<id>.json` and a rolled `vespera.<date>.<n>.log` far older, kept (§6) |
| 3 | `pipeline.InvocationOutputTest.aFileThatCannotBeDeletedIsWarnedOfAndTheInvocationGoesOn` | an old output file held open by the test on Windows, or in a folder made read-only elsewhere: one warning naming it and the exception's class, the next old file deleted, and its own file opened (§6) |
| 3 | `pipeline.InvocationReportInvocationTest.theOutputIsBegunWithTheIdBeforeTheReportsFirstWrite` | a recording output given to the report: `begin` called once, with the `invocation.id` the first `report.json` names, before that file exists (§3) |
| 3 | `LogsFolderIT.anInvocationsOutputFileHoldsEveryLineOfItsStream` | the packaged jar, `vespera label --db-dir=<path>` with no label file, its standard error merged into standard output: `logs/output-<id>.log`, its id the one `report.json` names, holds the stream's lines, line for line and in order, the banner and the refusal among them, the refusal its words alone (§2, §4, §5) |
| 3 | `LogsFolderIT.aStartRefusedBeforeTheLockWritesNoOutputFile` | the working directory held by another process: the refusal on standard error, and `logs` holds no file whose name starts `output-` (§2) |
| 4 | `controlpanel.ChildTest.theLinesReadAreHeldUntilTheyAreLetGo` | a child writing 300 lines: `held()` holds the 300 in order; after `letGo()` it holds none, and none read after; `tail()` keeps its last 200 throughout (§7) |
| 4 | `controlpanel.ChildTest.theHeldLinesStopAtTenThousand` | a child writing 10,001 lines, never let go: the last 10,000 held, in order (§7) |
| 4 | `controlpanel.TheOutputPanelTest.itShowsTheStreamWhileNoOutputFileIsFound` | a child that prints lines and writes no report: `output` holds them, in order (§7) |
| 4 | `controlpanel.TheOutputPanelTest.onceTheChildsReportIsFoundItShowsItsOutputFileWhole` | a child writing a report of its own pid and an output file of 5,000 lines: `output` holds the file's text whole, more lines than the tail's 200, and the child's held lines are let go (§7) |
| 4 | `controlpanel.TheOutputPanelTest.itGrowsWithTheFileAWholeLineAtATime` | the file gaining two lines and a third without its end: the panel holds the two, and the third once its end is written (§7) |
| 4 | `controlpanel.TheOutputPanelTest.aForeignHoldersOutputFileIsFoundThroughItsReport` | a working directory held by a process the window did not start, a report of that pid started after the holder line's `started` less a second, and its output file: `output` holds the file (§7) |
| 4 | `controlpanel.TheOutputPanelTest.aReportOfAnotherProcessLeadsToNoFile` | a report naming another pid, and an output file of its id: `output` holds the child's lines and not the file's (§7) |
| 4 | `controlpanel.TheOutputPanelTest.thePanelIsTextThatIsSelectedAndNotEdited` | `output` is a `TextArea`, not editable; a range selected reads as that part of the lines; text typed or pasted changes nothing; its context menu's Copy and Select All are enabled and Cut, Paste and Delete disabled (§1) |
| 4 | `controlpanel.TheOutputPanelFollowsTheBottomTest.atTheBottomItFollowsEachNewLine` | more lines than the panel shows, then 50 more: after each tick the view's inner `ScrollPane` stands at its `vmax` and the last line is the file's last (§1) |
| 4 | `controlpanel.TheOutputPanelFollowsTheBottomTest.scrolledUpItStaysWhereItIsAndStillTakesTheNewLines` | the view scrolled to its top, then 50 lines: its `vvalue` and the `TextArea`'s `scrollTop` unchanged, and its text ending with the new lines (§1) |
| 4 | `controlpanel.TheOutputPanelFollowsTheBottomTest.withTextSelectedItStaysAndKeepsTheSelection` | at the bottom, a range selected, then 50 lines: the selection's range and text unchanged, and the view's scroll unchanged (§1) |
| 4 | `controlpanel.TheOutputPanelFollowsTheBottomTest.backAtTheBottomWithNothingSelectedItFollowsAgain` | scrolled up, then back to `vmax` with the selection cleared, then 50 lines: at `vmax` with the new last line (§1) |
| 5 | `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | the window drawn on a working directory holding a profile in the jar's form, one not in it, a code set renamed and one outside the corpus root, a report written and one not, and after a child that ended with a value wanted: §8's walk reaches no `Label` whose text is not blank; every text it reaches is a `SelectableText`, not editable; and it reaches `where`, `child`, `outcome`, a step's state, a profile row's title and a preflight line, so that a walk reaching nothing cannot pass (§8) |
| 5 | `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.aSelectableTextIsSelectedAndNotEdited` | `outcome` holding a line: a part selected reads as that part, Copy and Select All in its context menu enabled, and text typed or pasted changes nothing (§8) |
| 5 | `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.theTextsOfControlsStayAsTheyAre` | the tabs' titles, the buttons' texts, the sidebar's section titles and each leg's name and state are a `Tab`'s, a `Button`'s, a `TitledPane`'s and `Label`s of a `TitledPane`'s header, as before (§8) |
| 5 | `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.aSelectableTextIsLaidOutAsTheLabelItReplaces` | `where` and a hint that wraps, at the window's 1500 by 960: each as wide and as high as a `Label` of the same text, style classes and wrapping, laid out in the same place, within a pixel (§8) |

### Tests that change or go

Each existing class this record changes, at the step that does it. Each record whose Tests section names one is corrected in place by the step that changes it.

| Step | Class | What happens |
| --- | --- | --- |
| 1 | `PipelineHoldsOnlyTheRulesOnRecordTest` | lists `OperatorLines` as holding no rule, in the change that adds it |
| 1 | the 40 classes that read `System.out` or `System.err`, `pipeline.ClosingLineInvocationTest`, `pipeline.WorkingDirectoryInUseRefusalTest`, `pipeline.UnconfiguredRootTest`, `pipeline.PublishToEachDestinationInTurnInvocationTest` and `website.ServeAnswersOnLoopbackAloneTest` among them | unchanged: `logback-test.xml` routes the operator logger to the same streams with the words alone, so each reads the line it read. One that fails on a line's stream or its words is a line §4 changed, and is corrected in the step |
| 1 | `pipeline.ServeCommandTest`, `pipeline.ServeHoldsNoLockIT`, `EntryPointTest.theEntryPointIsNoJavaFxApplicationAndNamesBothItStarts` | unchanged: the first builds `ServeCommand` with streams of its own; the second launches the jar, whose `serve` `EntryPoint` now hands on; the third holds that `EntryPoint` names at least the two classes it starts, and it names `ServeCommand` besides |
| 3 | `PipelineHoldsOnlyTheRulesOnRecordTest` | lists `InvocationOutput` and `InvocationOutputAppender` as holding no rule, in the change that adds them |
| 4 | `controlpanel.ChildTest.theTailKeepsTheLastTwoHundredLines`, `controlpanel.ChildTest.drainingWritesNothingIntoTheChildsDirectory` | unchanged: the tail stays, for the fall-back alone, and nothing of the stream is written beside the child |
| 4 | `controlpanel.TheControlPanelsWindowTest.theStepsAreAChartOfABarEachBesideTheOutput` | unchanged: `output` stays a side of the split, found by its id |
| 4 | `controlpanel.PublishToConfluenceAsksForTheTokenTest` (97) | unchanged: it reads the child's tail for the token, which the tail still holds |
| 5 | `controlpanel.PublishOffersEachDestinationDrawnTest` (96, 149), `controlpanel.PublishToConfluenceAsksForTheTokenTest` (116, 118), `controlpanel.TheFoldersNotRelevantTest` (159, 203), `controlpanel.TheFoldersToIncludeTest` (309), `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest` (196, 198), `controlpanel.TheReportsOpenInTheWindowTest` (408) | read the text as a `TextInputControl` where they read a `Label`; the last only until ADR-362's step 8 removes it |
| 5 | `controlpanel.PublishToConfluenceAsksForTheTokenTest` (171) | its walk for the token reads each `TextInputControl`'s text as well as each `Labeled`'s, so that it still reads the outcome, now a `SelectableText` |
| 5 | `controlpanel.TheStepRowsAlignInColumnsTest` (151, 152, 210) | a row's stage, name, state and cost are `SelectableText`s: it reads them where it read `Label`s, and the columns it holds are unchanged |
| 5 | `controlpanel.TheStepsFoldIntoLegsTest` | unchanged: a leg's name and state stay `Label`s of its header |
| 5 | ADR-362's owed tests of the Report tab and of the native views, `TheAnalysisReportsOpenNativelyTest` among them | where the step of the two builds that lands second is this one, they read a text as a `TextInputControl` |

## What the implementation owes

Beside turning the tests above green, step by step:

- `logback-spring.xml`'s header comment, naming the output file and the operator logger beside the console and `vespera.log`;
- the stylesheet's `selectable-text`, and `output` drawn as text in the output's own style;
- `application.yaml`'s comment above `banner-mode`, citing §5;
- the README's table of files written beside the database gaining `logs/output-*.log`, one per invocation, its output whole, deleted after 14 days; and its section on the control panel saying that the output is copied by selecting it;
- `CONTEXT.md`'s Working directory entry naming the output files among what `logs` holds;
- this record's number taken out of `docs/check-claims.mjs`'s `NOT_YET_BUILT` by the step that builds its last row.

## What this does not decide

- **The input fields**: rethinking them is [#764](https://github.com/algernon28/vespera/issues/764)'s.
- **A retention rule for the report's copies in `logs/`**, which ADR-362 left open, and which §6 does not reach.
- **The words of a dialog**, a window of its own, and whether they are copied.
- **A size past which the panel stops taking the file**: the operator wanted the whole output, and the Known limits say what is not measured.
- **Whether the operator logger's lines take another pattern, or the console's lines lose their time**: both read as they do.
