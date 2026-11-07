# ADR-360 — The control panel drops JavaFX's class path line from the javafx logger, and every other record of it still prints

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Decided**: by the operator, that the line JavaFX writes on standard error at each start of the window is filtered out of its logger, that every other record of that logger still prints at any level, and that JavaFX stays on the class path of the one jar. The logger the filter stands on, the class that holds it, the step of the start that installs it and the module that class names are decided here, from the measurements below.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §2, its paragraph on the manifest, by which the line `Unsupported JavaFX configuration` stays on standard error at each start of the window, and the line of its Consequences that says so (§1 below); its §3, by which a class of the control panel names `java.base` alone of the JDK's modules (§3 below); and its Tests row for `TheControlPanelLinksNoClassOfTheJarTest`'s first method, which holds every class of the control panel but one (§6 below). It carries a note at its head.
- **Keeps**: ADR-342 §1 and the rest of §2: OpenJFX 26.0.2 on the class path inside the Spring Boot jar, Spring Boot's `JarLauncher` the `Main-Class`, `io.algernon.EntryPoint` extending no `javafx.application.Application`, and `Enable-Native-Access: ALL-UNNAMED`. [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): one jar, started by `java -jar`, with no script, installer or image beside it. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 as ADR-342 §3 leaves it, but for the one module of §3 below.
- **Settles**: [#723](https://github.com/algernon28/vespera/issues/723).

## Context

ADR-342 §2 runs OpenJFX from the class path inside the Spring Boot jar, on purpose: `EntryPoint` extends no `Application`, so the Java launcher's check for JavaFX's modules is never reached, and Spring Boot's launcher puts no library on the module path. ADR-342's probe measured that every start then writes `WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @…'` on standard error. A start of the jar's window, as [#723](https://github.com/algernon28/vespera/issues/723) shows it, writes it through `java.util.logging`'s console handler as two lines:

```
nov 06, 2026 6:28:33 PM com.sun.javafx.application.PlatformImpl startup
WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @4962191c'
```

### What was measured

**The source of OpenJFX 26.0.2**, from `javafx-graphics-26.0.2-sources.jar` and `javafx-base-26.0.2-sources.jar`. `PlatformImpl.startup(Runnable, boolean)`, the first time it is called in a JVM and before it starts the toolkit, builds the message `"Unsupported JavaFX configuration: " + "classes were loaded from '" + module + "'"`, with `, isAutomatic: …, isOpen: …` after it where the module has a descriptor. It does so whenever `PlatformImpl`'s module is not the named, explicit, closed module `javafx.graphics`, and writes the message at `WARNING` through `com.sun.javafx.util.Logging.getJavaFXLogger()`. That method answers `PlatformLogger.getLogger("javafx")`, which wraps `System.getLogger("javafx")`. The compiled `PlatformImpl` in `javafx-graphics-26.0.2-win.jar` holds the message as one string of its constant pool, `Unsupported JavaFX configuration: classes were loaded from '\u0001'`, which is the recipe for the concatenation. No other source file of `javafx-base`, `javafx-graphics`, `javafx-controls`, `javafx-web` or `javafx-media` 26.0.2 holds the words `Unsupported JavaFX configuration`.

**So the logger is `javafx`, and not the class the line names.** The first of the two lines, `com.sun.javafx.application.PlatformImpl startup`, is the record's source, a class and a method that the record infers from the stack and that `java.util.logging.SimpleFormatter` prints ahead of the message. It names no logger. `javafx` is OpenJFX's general logger: twelve other source files of those jars write through `Logging.getJavaFXLogger()`, `ImageStorage`, `SVGPath`, `AbstractPrimaryTimer` and the GTK and Mac applications among them. `javafx.css`, `javafx.scene.layout`, `javafx.scene.focus` and `javafx.scene.input` are loggers of their own beneath it.

**A probe** was run outside the repository, on Windows 11 with OpenJDK 26.0.2.1. The `win` jars of `javafx-base`, `javafx-graphics` and `javafx-controls` 26.0.2 were on a class path, with `glass.platform` set to `Headless` and `prism.order` to `sw`. A handler on the root logger printed each record's logger, level, source and message. The probe started the toolkit by `Platform.startup`, and then wrote a `WARNING` and an `ERROR` through `System.getLogger("javafx")`:

- **With nothing installed**, the line was on standard error. Its record was the one record of the start: logger `javafx`, level `WARNING`, source `com.sun.javafx.application.PlatformImpl.startup`. The toolkit was up in 168 ms. The two records written after it printed as `WARNING` and `SEVERE`.
- **With a filter on `Logger.getLogger("javafx")`** dropping a message that begins `Unsupported JavaFX configuration`, the logger held in a static field and `System.gc()` called five times before the start, there was no line and no record of it. The two records after it printed, and `javafx` still carried the filter.
- **With the same filter on `Logger.getLogger("com.sun.javafx.application.PlatformImpl")`**, the line printed, and its record was on `javafx`.
- **With the same filter on `Logger.getLogger("javafx")` but the logger held by nothing**, and `System.gc()` called five times, the line printed, and `javafx` carried no filter. The log manager holds a logger weakly, so the logger JavaFX was given was a new one.
- **With `glass.platform` set to `NoSuchPlatform`**, as `CliExitIT` sets it, the line printed before the toolkit threw its `NullPointerException`. With the filter on `javafx` and held, it did not.

The probe was not run in the packaged jar, where Spring Boot's `LaunchedClassLoader` loads JavaFX's classes, nor on Linux.

### Read, and not executed

- **`java.util.logging.Logger.log(LogRecord)`, Java 26.** A logger asks its filter about each record logged on that logger. It does so after the level check and before any handler, its own or its parents'. A record logged on a logger beneath it, `javafx.css` for one, is not put to that filter. `LogManager` reads a logger's level and handlers from `logging.properties`, and no filter.
- **`LogManager.demandLoggerFor`, which `System.getLogger` reaches through the JDK's default `LoggerFinder` where `java.logging` is present.** It gives a module that is not the JDK's own the logger that `Logger.getLogger` gives of the same name. In the packaged jar, JavaFX's classes are in the unnamed module of Spring Boot's loader, which is not the JDK's, so they are given the logger the control panel set the filter on, as on the class path of the probe.

## Decision

### 1. The control panel's start drops the one line

**Before the toolkit starts, the control panel's start sets a `java.util.logging.Filter` on the logger named `javafx`**, the logger `PlatformImpl` writes the line on. The filter looks at a record's message as it was logged, before any formatting. It drops a record whose message begins `Unsupported JavaFX configuration`, whatever its level, the words matched as they stand, case and spaces included. It passes every other record, one with no message among them. So every other record of `javafx` prints as before, at any level. So does every record of the loggers beneath it, which are not put to the filter.

The line is no longer on standard error at a start of the window. Nor is it at a start where the window cannot open (ADR-342 §2), since JavaFX writes it as its start begins, before the toolkit throws. The filter is set once in a JVM and stays for the JVM's life. A start where the window cannot open goes on into `VesperaApplication.main` with the filter still set. There it drops nothing, since JavaFX writes the line only as its toolkit starts, and no command starts one.

### 2. A class of the control panel's own holds the filter, and step 3 of the start installs it

**`io.algernon.controlpanel.ClassPathLine`** is a final class implementing `java.util.logging.Filter`, with a constructor of no argument:

- **`isLoggable(LogRecord)`** answers false for a record whose message begins as §1 says, and true for every other record.
- **`static void install()`** sets an instance of the class as the filter of `Logger.getLogger("javafx")`. Called again, it sets it again.
- **The class holds that logger in a static field**, so the logger is held for as long as the class is loaded. The log manager holds a logger weakly, and so keeps the one that carries the filter and does not give JavaFX a new one without it (Measured).

`install` answers nothing, so a class that calls it names no type of `java.logging`.

**`ControlPanel.open()` calls `ClassPathLine.install()` in its step 3** (ADR-342 §2), immediately before `Application.launch`, inside the attempt whose throw answers NO_WINDOW. `Application.launch` starts the toolkit. Steps 1 and 2 start no toolkit, and an opening that answers `Opening.Result.NO_JAR` installs nothing.

**The control panel holds it, and `io.algernon.EntryPoint` does not.** The line is the toolkit's, and only the control panel starts the toolkit: its step 3 is where JavaFX's start is, and the filter stands beside it. `EntryPoint` lies outside both packages, so it could hold the filter without §3 below. But ADR-342 §2 gives it one task, choosing between the window and the command line. With the filter it would also know a logger of OpenJFX's and the order of the window's start, and both are the control panel's.

### 3. One class of the control panel names `java.logging`

Amending ADR-342 §3. Of the JDK's modules, a compiled class of the control panel names `java.base`. `ClassPathLine` names `java.logging` as well, for `Filter`, `LogRecord` and `Logger`, and no other class of the control panel names it. `java.util.logging` is the JDK module `java.logging`, not part of `java.base`. Admitting it for one class keeps the rest of the control panel as ADR-342 §3 has it.

The rest of ADR-342 §3 stands. The control panel names nothing of `io.algernon.vespera`, of `io.algernon.EntryPoint`, of Spring, of `java.sql`, of `java.desktop`, of `jdk.httpserver` or of `netscape.javascript`. It names no `com.sun.javafx`, `com.sun.glass` or `com.sun.webkit` type. The filter knows the logger by its name and the line by its words, and names neither `PlatformImpl` nor `Logging`.

### 4. What stays

JavaFX stays on the class path, and no library moves to the module path. `JarLauncher` stays the `Main-Class`, `EntryPoint` extends no `Application`, and the jar stays one file started by `java -jar` (ADR-158). The pom does not change. No logger's level changes, and no handler is added or taken away.

### 5. Run ids

No file under `src/main/java/io/algernon/vespera` changes for this record, so no run id moves.

### 6. The tests

The tests read the JavaFX the build declares, 26.0.2. A change of `javafx.version` that changes the logger's name or the message's first words fails them in the same change.

- **The filter's answers are held on records the test builds**, at each of the seven levels `java.util.logging` names. The line as JavaFX writes it is dropped, and so is a message of the words alone. Six other messages pass: another message, the words not at the start, part of the words, the words in another case, the words after a space, and no message.
- **What `install` does is held in the test JVM**: the logger `Logger.getLogger("javafx")` answers carries a `ClassPathLine`, and a static field of `ClassPathLine` holds that very logger. The test puts the logger's earlier filter back after it.
- **The order of the start is held on `ControlPanel`'s compiled class.** `open` calls `ClassPathLine.install` once, and `Application.launch` once, on a later line.
- **The toolkit's start is held in a JVM of its own, once with the filter and once without**, since JavaFX starts once in a JVM and the test JVM's toolkit may have been started by any other test. That JVM's `java.util.logging` configuration is written by the test: the console handler on the root logger, the levels at `INFO`, and the format `%4$s: %5$s%n`. Its locale is set too, `user.language` `en` and `user.country` `US`, since `%4$s` prints the level's name in the JVM's locale, `WARNUNG` and `SCHWERWIEGEND` in German. A handler on the root logger writes each record that reaches it to standard output. That JVM calls `System.gc()` five times before the toolkit starts.
  - Without the filter, the start writes one record whose message begins `Unsupported JavaFX configuration`, on the logger `javafx` at `WARNING`, and its line is on standard error. This holds the logger's name and the message's first words against the JavaFX the test runs, and is the control that shows the start with the filter can fail.
  - With the filter, the start writes no record with those words and no line holding them. A `WARNING` and an `ERROR` written after the start through `System.getLogger("javafx")` reach the handler, as `WARNING` and `SEVERE`, and print on standard error.
- **The message's first words are held on the compiled class**: `PlatformImpl`'s constant pool, read with the JDK's `java.lang.classfile` from the JavaFX on the test class path, holds a string beginning `Unsupported JavaFX configuration: classes were loaded from '`.
- **The packaged jar is held by an integration test.** Started with no argument and a glass platform JavaFX does not have, it writes no line holding `Unsupported JavaFX configuration`, after writing ADR-342 §2's line saying the window cannot open.
- **The control panel's modules are held as §3 has them**: every class but `ClassPathLine` names only what ADR-342 §3 lets it name, and `ClassPathLine` names that and `java.logging`.

## Consequences

- A start of the window writes nothing of JavaFX's on standard error where it wrote the line, and a start where the window cannot open writes no such line either. Every other record of JavaFX's, at any level and on any of its loggers, still prints.
- The control panel now depends on two things OpenJFX does not promise: the name of the logger `PlatformImpl` writes on, and the words its message begins with. A release that changes either brings the line back, and the change of `javafx.version` that brings that release in fails the tests of §6.
- ADR-342's probe stays a measurement of the jar as it was before this record.
- The README's sentence on what a start of the window prints changes.

## Known limits

- **The configuration stays one OpenJFX does not support.** The filter hides what JavaFX says and changes nothing JavaFX does. A later release may refuse to start from the class path at all, as the Java launcher refuses a `Main-Class` extending `Application` there (ADR-342, Alternatives refused), and this record does not prepare for that.
- **The filter rests on the logger's name and the message's first words**, both internal to OpenJFX. §6's tests pin both against 26.0.2, as the pom declares it.
- **A message logged as a key into a resource bundle is matched as the key.** JavaFX logs the line with no bundle.
- **A handler that anything adds, to the root logger or to `javafx` itself, sees no record the filter dropped**, since a logger puts a record to its filter before any handler.
- **Neither the probe nor a unit test runs the packaged jar.** The integration test of §6 runs it on CI's Linux runner. The window's start is not run on Linux outside CI.

## Alternatives refused

- **Accepting the line and documenting it**, as ADR-342 §2 had it. The operator does not want a `WARNING` at every start.
- **JavaFX on the module path.** Beside the jar, with a script to start it, breaks one-file delivery and ADR-158. Unpacked by the jar and started again in a child JVM, it costs a second JVM at each start and files written to a temporary folder, and takes JavaFX out of `BOOT-INF/lib`.
- **`jlink` and `jpackage`.** They need a build for each system, CI for each target and an installer, and they reverse ADR-158.
- **Setting the `javafx` logger's level to `SEVERE` or `OFF`.** It would hide every other warning of JavaFX's general logger, not one line.
- **The filter on a logger named `com.sun.javafx.application.PlatformImpl`.** No record is logged there: that class is the line's source, not its logger (Measured).
- **The filter on the console handler or on the root logger.** Every record of every logger would be put to it, and Spring Boot replaces the root's handlers once a command runs.
- **The logger held by nothing.** The log manager lost it to a collection, and JavaFX was given a new one with no filter (Measured).
- **Also checking the record's source class, `com.sun.javafx.application.PlatformImpl`.** The record infers its source from the stack when asked, which is a third thing OpenJFX does not promise. No other source of OpenJFX 26.0.2 holds the first words, so they name this line alone.
- **`EntryPoint` installing the filter**: §2.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.ClassPathLineTest.aRecordWhoseMessageBeginsWithTheLinesOpeningWordsIsDroppedAtEveryLevel` | the line as JavaFX writes it, and the words alone, dropped at each of the seven levels (§1, §2) |
| `…everyOtherRecordPassesAtEveryLevel` | six other messages, no message among them, passed at each of the seven levels (§1) |
| `…installingPutsTheFilterOnTheLoggerNamedJavafxAndItsClassHoldsThatLogger` | `install` sets a `ClassPathLine` on `javafx`, and a static field of the class holds that logger (§2) |
| `…theControlPanelsStartInstallsTheFilterBeforeItLaunchesTheWindow` | `ControlPanel.open` calls `install` once, on a line before its one call of `Application.launch` (§2) |
| `controlpanel.TheToolkitsStartWritesNoClassPathLineTest.withoutTheFilterTheToolkitsStartWritesTheLineOnTheJavafxLoggerAtWarning` | in a JVM of its own, with no filter: one record of the words, on `javafx` at `WARNING`, and its line on standard error (§6) |
| `…withTheFilterTheToolkitsStartWritesNoLineAndJavaFxsOtherRecordsStillPrint` | in a JVM of its own, with the filter installed and five collections: no record and no line of the words, and a `WARNING` and an `ERROR` of `javafx` after the start reach the handler and print (§1, §2) |
| `…theLinesOpeningWordsAreAStringOfTheClassJavaFxStartsBy` | `PlatformImpl`'s constant pool holds the message's first words (§6) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson` | every class of the control panel but `ClassPathLine` names only its own package, `java.base`, `javafx.` packages and `tools.jackson` (§3) |
| `…onlyTheClassPathLineNamesTheJdksLoggingModule` | `ClassPathLine` is there, and names only those and `java.logging` (§3) |
| `CliExitIT.aJarThatCannotOpenTheWindowWritesNoLineOfJavaFxsAboutItsClassPath` | the packaged jar, started with no argument and a glass platform JavaFX does not have, writes ADR-342 §2's line and no line of the words (§1) |

## What the implementation owes

Beside the tests above, which it turns green:

1. **`io.algernon.controlpanel.ClassPathLine`**, as §2 has it: final, implementing `java.util.logging.Filter`, with a constructor of no argument the tests call, `isLoggable(LogRecord)`, `static void install()`, and a static field holding `Logger.getLogger("javafx")`. Its javadoc cites this record. It names nothing beyond `java.base` and `java.logging`, and holds no nested class.
2. **`ControlPanel.open()`** calls `ClassPathLine.install()` immediately before `Application.launch(ControlPanelWindow.class)`, inside the attempt whose throw answers NO_WINDOW. Its javadoc names the step.
3. **The README's sentence** on what each start of the window prints no longer names `WARNING: Unsupported JavaFX configuration`. It keeps what it says of `.openjfx/cache` and of the `.vespera` a start does not make.
4. Nothing in the pom, the build or CI changes.

## What this does not decide

- **What happens if OpenJFX refuses the class path in a later release**: the module path, `jlink` or another way of starting the window is a later record's.
- **Lines JavaFX writes on standard error outside `java.util.logging`**, such as the stack trace of the glass platform's own `ClassNotFoundException` where a platform JavaFX does not have is named: they stay as they are.
- **The console handler's format and level** in the jar: they are the JDK's defaults, as before.
