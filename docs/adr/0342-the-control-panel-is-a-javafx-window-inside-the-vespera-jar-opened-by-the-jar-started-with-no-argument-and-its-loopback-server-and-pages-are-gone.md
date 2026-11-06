# ADR-342 — The control panel is a JavaFX window inside the Vespera jar, opened by the jar started with no argument, and its loopback server and pages are gone

> **Partly amended — see [ADR-353](0353-the-embedding-models-shipped-default-is-qwen3-embedding-8b-and-only-a-profile-naming-no-model-moves-its-scoring-run.md).** Of its Tests row for `TheLaunchFieldsSayWhatTheyHoldTest`: the answering model's example is held by its form, one name and not blank, and no test names the model.
>
> **Partly amended — see [ADR-360](0360-the-control-panel-drops-javafxs-class-path-line-from-the-javafx-logger-and-every-other-record-of-it-still-prints.md).** Of §2's paragraph on the manifest and of Consequences: the line `Unsupported JavaFX configuration` no longer stays on standard error, since the control panel's start drops it from the logger `javafx`, which JavaFX writes it on, before the toolkit starts; every other record of that logger still prints, and JavaFX stays on the class path. Of §3: one class of the control panel, `ClassPathLine`, names the JDK's `java.logging` too. Of its Tests row for `TheControlPanelLinksNoClassOfTheJarTest`'s first method: it holds every class of the control panel but `ClassPathLine`. The rest of this record stands.

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: by the operator, that a desktop window replaces the browser control panel rather than standing beside it, that it ships inside the Vespera jar and not as an application of its own, that a flag on the command line keeps a start that never opens the window, and that its layout is variant B of [#700](https://github.com/algernon28/vespera/issues/700), which [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) records. The rest, the dependencies and their version, how the window starts from the jar, which native libraries the jar carries, what a start does where no window can open, which records it supersedes and which it keeps, and how a window is tested, Claude recommended and the operator accepted as written.
- **Supersedes**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md), whose server, address and checks exist no more (§5). It carries a note at its head.
- **Supersedes**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): a window is tested as §6 says, and jsoup leaves the pom with the last page (§6). It carries a note at its head.
- **Supersedes**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) but for what §4 below keeps of it: its §5, the sidecars asked and their answers only read; its §6, free space; its §7, the table of what a stop costs at each step; and of its §8 the rule of what the next act asks for. Its pages, forms, paths, names and the report re-served in a sandbox go. It carries a note at its head.
- **Supersedes**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md), whose port, tab and browser opening go with the server (§5). It carries a note at its head.
- **Supersedes**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) but for its §6, a folder field's status, and its §8, an example and a hint on each field, which the window keeps (§4). It carries a note at its head.
- **Supersedes**: [ADR-335](0335-the-control-panel-shows-the-progress-page-on-a-page-of-its-own-and-its-pages-are-drawn-to-one-compact-scale.md): the window draws the progress page's charts itself (ADR-344 §2). It carries a note at its head.
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §2, whose second jar is not built; §3, whose JDK modules are `java.base` alone, with JavaFX's public packages and, for one file, Jackson's (§3); §4, whose ADR-130 rule binds one stylesheet; and its refusal of one jar holding both (§1). It carries a note at its head.
- **Amends**: [ADR-288](0288-the-operator-starts-the-control-panel-with-java-jar-and-it-starts-the-jar-beside-its-own-with-the-java-running-it.md) §1, whose operator starts the window by `java -jar` on the Vespera jar with no argument (§2), and §4, whose child is started from the jar the window runs from (§2). Its §3 stands. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §2, whose pages are the window's (ADR-344). The rest of it stands. It carries a note at its head.
- **Keeps**: ADR-278 §1, §3 to §8, [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md), [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md), [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) but for what [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) amends, [ADR-306](0306-the-control-panel-reads-a-holder-line-of-its-own-process-as-held-by-nobody-and-serves-the-cluster-page-sizes-as-a-sixteenth-report.md) §1 and §2, [ADR-307](0307-the-child-the-control-panel-started-ends-with-it-wherever-its-end-runs-the-jvms-shutdown-hooks.md), [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md), [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) and [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md), each for what it decides of the child, the locks, the refusals and the files, which §4 lists.
- **Keeps**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md): the ten modules stay ten; the control panel and the jar's entry point are outside `io.algernon.vespera` (§2, §3). [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): no installer, script or image is shipped. [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) and [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md): the window reads no document and serves nothing (§4).
- **Read with**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): the dependencies of §1 are this record's.
- **Settles**: [#700](https://github.com/algernon28/vespera/issues/700), with ADR-343 and ADR-344.

## Context

**What the control panel is today.** A plain jar of this build, `vespera-<version>-control-panel.jar`, holding `io.algernon.controlpanel` and nothing else (ADR-285 §2), started by `java -jar` (ADR-288). It serves forms on `127.0.0.1` at port 24291 through `com.sun.net.httpserver` (ADR-291, ADR-325) and opens them in a Chromium `--app` window or the default browser (ADR-331 §1). It starts the Vespera jar beside its own as its child, one at a time, reads the child's output to the end, stops only its own child, and reads and writes only the files ADR-278, ADR-292, ADR-294, ADR-295 and ADR-330 name. Of its 39 classes, 12 serve or draw pages; the other 27 hold the child, the locks, the launch values, the files and their checks.

**What the jar is.** One Maven project, Java 26, Spring Boot 4.1.1, repackaged by `spring-boot-maven-plugin` into a jar whose `Main-Class` is Spring Boot's `JarLauncher`, its own classes under `BOOT-INF/classes/` and each library a nested jar under `BOOT-INF/lib/`, loaded by Spring Boot's `LaunchedClassLoader`; nothing in it is a named module. A build of 2026-11-03 wrote it at 170,028,217 bytes. `VesperaApplication.main` prepares the working directory, takes its lock and checks the profile before any command is parsed, so a start of the jar that reaches it with no command still makes `.vespera` in its current directory and holds it. CI builds on Linux through `verify` and on Windows, on NTFS, through `test`; it publishes no jar.

### What was measured

**OpenJFX 26.0.2 on Maven Central** has `javafx-base`, `javafx-graphics` and `javafx-controls`, each an empty jar whose POM pulls a jar of the same name with a platform classifier, which OpenJFX's parent POM picks by an OS-activated profile: `win`, `linux`, `mac`, `mac-aarch64`, `linux-aarch64`. Their sizes in bytes:

| Classifier | `javafx-base` | `javafx-graphics` | `javafx-controls` | The three |
| --- | --- | --- | --- | --- |
| `win` | 749,188 | 6,161,094 | 2,605,681 | 9,515,963 |
| `linux` | 749,190 | 5,466,211 | 2,605,690 | 8,821,091 |
| `mac` | 749,191 | 5,632,421 | 2,605,709 | 8,987,321 |
| `mac-aarch64` | 749,190 | 5,581,767 | 2,605,718 | 8,936,675 |

Each classifier's jars carry the whole of JavaFX's classes beside that platform's native libraries: `glass.dll`, `prism_d3d.dll`, `prism_sw.dll`, `javafx_font.dll` and the C runtime's for `win`; `libglass.so`, `libglassgtk3.so`, `libprism_es2.so`, `libprism_sw.so` and the font libraries for `linux`. Both `javafx-graphics-26.0.2-win.jar` and `javafx-graphics-26.0.2-linux.jar` hold `com/sun/glass/ui/headless/`, JavaFX's own Headless platform. The charts, `javafx.scene.chart`, are in `javafx-controls`.

**A probe** outside the repository, on Windows 11 with OpenJDK 26.0.2.1: a project whose parent is `spring-boot-starter-parent` 4.1.1, depending on `javafx-controls` 26.0.2, repackaged by `spring-boot-maven-plugin` 4.1.1 with a main class that does not extend `javafx.application.Application`, whose `main` with no argument calls `Application.launch` on a second class that does, showing a stage of a button, a progress bar and a line chart of 50 points, firing the button from code and taking a snapshot of the scene.

- `java -jar probe.jar` showed the window 806 ms after `main` began, on Windows' own platform. The classes were loaded by `org.springframework.boot.loader.launch.LaunchedClassLoader`; JavaFX ran in an unnamed module; the button's press was seen and the snapshot was 800 by 600.
- JavaFX loaded its native libraries out of the nested `BOOT-INF/lib/javafx-graphics-26.0.2-win.jar` and kept them in `<user home>/.openjfx/cache/26.0.2+3/amd64/`.
- Every start wrote, on standard error, `WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @…'`, and, until the jar's manifest carried `Enable-Native-Access: ALL-UNNAMED`, four lines more beginning `WARNING: A restricted method in java.lang.System has been called`. With that entry the four were gone; the first stays.
- `-Dglass.platform=Headless -Dprism.order=sw` on the same jar showed the window in 638 ms with no window on the screen, the press and the snapshot the same.
- The main class's code source location was `jar:nested:/<path>/probe.jar/!BOOT-INF/classes/!/`; `java.class.path` was the jar's path alone.
- **In a surefire test** of the same project, `glass.platform` set to `Headless` and `prism.order` to `sw` as system properties before `Platform.startup`, the toolkit was up in 124 ms, its application `com.sun.glass.ui.headless.HeadlessApplication`; a stage of 800 by 600 was shown, a button found by its id and fired, and a snapshot of 800 by 600 taken. No display, no TestFX and no Monocle were on the class path.

The probe was not run on Linux.

**OpenJFX 26.0.2's `javafx-web`** is, like the three, an empty jar whose POM pulls the jar of its platform's classifier, and with it `javafx-media` and `jdk-jsobject`, each in the same way. A classifier's `jdk-jsobject` holds `netscape.javascript`'s two classes and nothing else. Their sizes in bytes:

| Classifier | `javafx-web` | `javafx-media` | `jdk-jsobject` | The three |
| --- | --- | --- | --- | --- |
| `win` | 36,073,597 | 1,189,003 | 1,675 | 37,264,275 |
| `linux` | 47,588,621 | 1,598,608 | 1,675 | 49,188,904 |
| `mac` | 39,810,204 | 1,618,939 | 1,675 | 41,430,818 |
| `mac-aarch64` | 38,815,045 | 1,541,079 | 1,675 | 40,357,799 |

`javafx-web-26.0.2-win.jar` holds WebKit as `jfxwebkit.dll`, 96,460,800 bytes unpacked; the `linux` jar holds it as `libjfxwebkit.so`, 124,666,640 bytes, whose ELF names as needed only `libdl.so.2`, `libpthread.so.0`, `libjvm.so`, `librt.so.1`, `libm.so.6`, `libc.so.6` and `ld-linux-x86-64.so.2`. `javafx-media`'s native libraries, GStreamer's among them, are loaded by a media player and not by a viewer of these reports: after the probe below, JavaFX's cache held `jfxwebkit.dll` beside the libraries of the three and none of `javafx-media`'s.

**The jar with them.** The six jars `javafx-web` brings on Windows, the three of the `win` classifier and the three empty ones of 301, 303 and 303 bytes, were nested in a copy of the packaged jar of 2026-11-04, 179,837,033 bytes, as the Boot plugin nests a library: stored, under `BOOT-INF/lib/`, and named in `BOOT-INF/classpath.idx`. That copy was 37,266,121 bytes larger than a copy made the same way without them, 20.7% of the packaged jar.

**A probe of a report's viewer**, outside the repository, on Windows 11 with OpenJDK 26.0.2.1 and the `win` jars of `javafx-controls` and `javafx-web` on a class path. The jar's own `ProgressPage` wrote the progress page's four tabs for a synthetic invocation with a step running, at an interval of 1 second, and a `WebView` of 1000 by 800 on a stage loaded `progress.html` by its `file:` address. On the Headless platform, `glass.platform` set to `Headless` and `prism.order` to `sw` as the tests set them:

- the toolkit was up in 161 to 173 ms, and the page loaded 849 to 969 ms after the `WebView` was made; JavaScript is on by default; the page held its 24 hit circles, and a snapshot of the view held 544 colours;
- the page's own script reloaded it 5 times in 5.2 seconds, at its interval;
- a JavaFX `MOUSE_MOVED` fired on the `WebView` over the middle of the first chart, scrolled into view, showed the page's tooltip, holding the sample's time and its series' value, and drew its crosshair;
- a press, a release and a click fired on the `WebView` over the bar's link to `progress-steps.html`, the same over a link to a text file outside the working directory, and the page's own `click()` on its link to `progress-samples.html`, each set the engine's location to the link's address as its load began; the load, cancelled from the location's listener, left the viewer on `progress.html`, its title and its document as they were;
- a script added to the page, catching each click and passing its address out through `alert`, kept the viewer in place as well.

On Windows' own platform, the stage placed off the screen, the same held, the toolkit up in 304 ms. The viewer was not run on Linux.

## Decision

### 1. The dependency

The pom gains two dependencies, at compile scope:

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>${javafx.version}</version>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-web</artifactId>
    <version>${javafx.version}</version>
</dependency>
```

with the property `javafx.version` at **26.0.2**, beside the pom's other versions. Spring Boot 4.1.1's parent manages no version of OpenJFX. `javafx-controls` brings `javafx-graphics` and `javafx-base`, and holds the window's controls and charts; `javafx-web` brings `javafx-media` and `jdk-jsobject`, and holds the `WebView` a report opens in inside the window (ADR-344 §6). Nothing else of OpenJFX is declared: no `javafx-fxml`, since the window is built in Java, and no `javafx-swing`; `javafx-media`, which no class of the control panel names, comes in only as `javafx-web`'s own. 26.0.2 is the OpenJFX release of the JDK's own major, the one measured above; a later 26.0.x is the same decision.

**The jar carries the native libraries of the platform it is built on**, the classifier OpenJFX's parent POM picks by the building machine's system. The pom names no classifier. A jar built on Windows opens the window on Windows; one built on Linux, on Linux. Every command of the jar runs on any platform whatever it carries, since no class of `io.algernon.vespera` names JavaFX and nothing loads it until the window opens. Carrying every classifier would add about 157.7 MB of classes the build machine does not use and of native libraries a jar built for one operator does not run (ADR-216); a jar for another platform is built there.

So the Spring Boot jar grows by the six jars of one classifier and their empty ones: where it is built on Windows, `javafx-controls`' three by 9,515,963 bytes, about 5.6% of the build of 2026-11-03, and `javafx-web`'s by 37,266,121 bytes more, 20.7% of the build of 2026-11-04, as measured above, so that JavaFX is about 46.8 MB of a jar of about 217 MB. WebKit is loaded only once a report is opened, and the first report opened on a machine writes `jfxwebkit.dll`, 96,460,800 bytes, beside JavaFX's other native libraries under `<user home>/.openjfx/cache/`.

**The build stays green on both CI jobs** because each resolves its own classifier: Linux's `verify` builds and tests against `linux`, Windows' `test` against `win`, and every window test, a report's viewer among them, runs on the Headless platform, which needs no display (§6).

### 2. How it starts

**The operator opens the window by starting the Vespera jar with no argument**:

```
java -jar target/vespera-<version>.jar
```

A start with any argument at all is the command line as before: `run`, `label`, `publish`, `--help`, `--version`, an option alone. So no command, option or usage line of the jar changes, and a double click on the jar, which starts it with no argument, opens the window.

**`--headless` as the first argument never opens the window.** The jar is then the command line, given every argument after the first: `--headless run <root>` is `run <root>`, and `--headless` alone is a start of the command line with no argument, which prints the commands' usage and exits with picocli's usage code, as a bare start of the jar did before this record. Only the first argument is read so: a `--headless` anywhere else is passed on as it stands, and the command line answers it as it answers any option it does not know. It is no option of picocli's model, so `ChildCommand.ARGUMENTS` (ADR-336 §2) does not hold it, and the window never passes it: each child it starts has a command, and never opens a window.

**The jar's `Start-Class` is `io.algernon.EntryPoint`**, set as `spring-boot-maven-plugin`'s `mainClass`, in the package above both: it is no class of `io.algernon.vespera`, so no module and no stage's version names it, and no class of the control panel names it. It does not extend `javafx.application.Application`, so the Java launcher's check for JavaFX's modules is never reached, and Spring Boot's `JarLauncher` stays the `Main-Class`. Its `main`:

- with no argument, calls `io.algernon.controlpanel.ControlPanel.open()`, the control panel's one entry, and does as its answer says (below);
- with `--headless` first, calls `io.algernon.vespera.VesperaApplication.main` with the arguments after it;
- with any other argument first, calls `VesperaApplication.main` with every argument;

and nothing of the control panel is loaded before `VesperaApplication.main` but where the window could not open. It is the one class that names both. It decides through two static methods a test calls: `EntryPoint.chosen(String[])`, answering `EntryPoint.Started.CONTROL_PANEL` for no argument and `EntryPoint.Started.COMMAND_LINE` otherwise, and `EntryPoint.passedOn(String[])`, the arguments `VesperaApplication.main` is given, the first `--headless` dropped.

**`ControlPanel.open()`** is the control panel's one entry. `ControlPanel` keeps no `main`. It answers a `ControlPanel.Opening`, a `record Opening(Result result, String line)` whose `Opening.Result` is `NO_JAR`, `NO_WINDOW` or `CLOSED`, and does, in this order:

1. **finds the child's runtime** (below). Where the class path names no jar, it answers `Opening.Result.NO_JAR` with the refusal line, and `EntryPoint` writes the line on standard error and exits 1, trying no window;
2. **registers the shutdown hook** that ends the child with the JVM (ADR-307 §1), `ControlPanel.endChildWithThisJvm(ControlPanelState)`;
3. **starts the toolkit and launches `ControlPanelWindow`**, the one class of the control panel that extends `Application`, whose root `ControlPanelWindow.drawn(ControlPanelState)` draws (ADR-344 §1). Where the toolkit throws as it starts, on a machine with no display, or from a jar built for another platform, it answers `Opening.Result.NO_WINDOW` with the message of what it threw as its line; otherwise it answers `Opening.Result.CLOSED`, with an empty line, once the window has closed, and `EntryPoint` exits 0.

**Where the window cannot open**, the jar answers as its command line rather than failing: `EntryPoint` writes one line on standard error,

```
the control panel cannot open a window here, so the jar answers as its command line: <reason>
```

and calls `VesperaApplication.main` with no argument, which prints the commands' usage and exits with picocli's usage code, as `--headless` alone does. Nothing asks the system whether it has a display before the toolkit is tried: the toolkit's own failure is the one answer, on every platform. Measured on the probe: with `glass.platform` naming a platform JavaFX does not have, `Platform.startup` threw a `NullPointerException` 208 ms after it was called, and the JVM went on.

**The Maven profiles.** `headless` runs the jar with `--headless` before `vespera.args`, so the profile given no `vespera.args` prints the usage and never opens the window. `gui` runs the jar with no argument, which opens the window.

**The manifest** gains `Enable-Native-Access: ALL-UNNAMED`, set as a `manifestEntries` entry of `maven-jar-plugin`'s `default-jar` execution, which the Boot plugin's repackage keeps. The line `Unsupported JavaFX configuration` stays on standard error at each start of the window: JavaFX runs on the class path, Spring Boot's launcher puts no library on the module path, and the line says so and nothing more.

**The child is the jar the window runs from** (amending ADR-288 §4): the path `java.class.path` holds, where it is one regular file; otherwise the refusal of step 1 above, exit 1:

```
the control panel starts the Vespera jar it runs from, and runs from no jar: <class path>
```

Its `java` stays ADR-288 §3's, `<java.home>/bin/java`. `ChildRuntime.find(String classPath, Path javaHome, String osName)` finds both, throwing `FileNotFoundException` with the line; the manifest entry `Vespera-Jar` goes.

### 3. What the control panel links

ADR-285 §1 stands: the control panel's code is `io.algernon.controlpanel`, outside the ten modules. ADR-285 §3 stands with other modules: a compiled class of the control panel names types of its own package, of the JDK's `java.base`, of JavaFX's public packages, those whose names begin `javafx.`, and of `tools.jackson`, for the one file ADR-344 §3 reads with it. It names nothing of `io.algernon.vespera`, of `io.algernon.EntryPoint`, of Spring, of `java.sql`, of `java.desktop`, of `jdk.httpserver`, of `netscape.javascript`, which `jdk-jsobject` brings, and no `com.sun.javafx`, `com.sun.glass` or `com.sun.webkit` type. No class of `io.algernon.vespera` names the control panel or JavaFX. ADR-285 §4's ADR-001 stands: the window starts no Spring context.

ADR-130's rule within the control panel binds one stylesheet, a resource of `io.algernon.controlpanel`, which owns every colour, size and space the window draws; no class sets a style of its own.

### 4. What carries over unchanged

The window is a new face on the same contract. These stand as their records say, the classes holding them unchanged but for how they are shown:

| What | Record | Classes |
| --- | --- | --- |
| The child: one at a time, its command lines, `CI=1`, its environment, draining, Stop of its own child alone, how it ended | ADR-278 §1, §3 to §6; ADR-292 §4 | `Child`, `ChildCommand`, `ChildRuntime`, `HowItEnded`, `Ending` |
| The child ends with the control panel's JVM | ADR-307 | `ControlPanel`'s shutdown hook |
| The locks read: holder lines, a held working directory or corpus root refusing a start, its own process's line held by nobody | ADR-278 §4, ADR-285 §5, ADR-306 §1 | `HolderLine`, `HolderProcess`, `HolderReading`, `CorpusRootLockFile` |
| The launch values: seven, their file, its folder of the user's own, its refusals and warning | ADR-292 | `LaunchValues`, `FolderOwnership` |
| What the next invocation wants | ADR-294 | `NextInvocation` |
| Each write holding the working-directory lock, one action at a time; each file written whole; the profile's checks | ADR-295 | `WorkingDirectoryWrite`, `ProfileEdit`, `ProfileKey`, `YamlLines`, `YamlScalars`, `LabelAnswers`, `CodeSetRenames` |
| What lies beneath the profile, a save that compares, a repair that loses nothing, a working directory made by a save | ADR-330 §1, §5 to §7 | `EffectiveValues`, `ProfileRepair` |
| The default output folder read as none named, the value in use beside an empty field, the folders not relevant, the profile as one form, code set folders outside the corpus root, the GPU image preselected | ADR-334 §1 to §7, §11 | `NotRelevantFolders`, `CodeSetFolderChecks` |
| The sidecars asked and only read; free space; what a stop costs at each step | ADR-302 §5 to §7 | `SidecarChecks`, `JsonReader`, and `StopCosts`, where the table moves from the pages (ADR-344 §8) |
| A folder field's status; an example and a hint on each field; each key's reports | ADR-331 §6, §8, ADR-306 §2, §3 | `ProfileKey`, and `FolderStatus`, `LaunchValues` and `Reports`, where the rules move from the pages (ADR-344 §8) |
| Every argument of the jar's commands accounted for | ADR-336 §2 | `ChildCommand.ARGUMENTS` |
| Run, Label and Let the local model answer disabled while the window's child runs or a holder line is held, `label --auto` started by Label's checks | ADR-302 §2, ADR-336 §1 | `ControlPanelState` |

**The private-paths rules stand.** The window reads no document: its viewer loads the jar's reports alone, and a link inside one to a document is handed to the system, which opens it (ADR-344 §6). It lists folder names only where the operator opens a folder (ADR-344 §1), and writes only the files ADR-295 and ADR-330 name; it serves nothing, so no other local caller reaches it. An agent may still start the jar, with no argument too: the guard sees the start and nothing the window then reads or writes, as ADR-264 §5 has it.

### 5. What goes

- **The server**: `ControlPanelServer`, `Exchanges`, port 24291 and `VESPERA_CONTROL_PANEL_PORT`, `/alive`, every path and header, ADR-291's checks.
- **The pages**: `ControlPanelPage`, `ControlPanelPages`, `LaunchPages`, `InvocationPages`, `NextPages`, `ValuePages`, `FolderPages`, `ReportPages`, and the held forms of `ControlPanelState`.
- **The browser**: `BrowserOpener`, `SystemBrowserOpener`, the Chromium window and the tab that comes back.
- **The second jar**: `maven-jar-plugin`'s `control-panel` execution and the `default-jar` execution's exclusion of `io/algernon/controlpanel/**`.

### 6. How a window is tested

**On JavaFX's Headless platform, in the test JVM, with no dependency at test scope.** The tests' `HeadlessToolkit` sets `glass.platform` to `Headless` and `prism.order` to `sw` and starts the toolkit once per JVM, since JavaFX starts once; a test runs its work on the toolkit's thread through it and waits for the answer. No TestFX and no Monocle: the platform is JavaFX's own since 26.0.2's jars hold it, and a robot is not needed to press a button a test can fire.

- **A window test builds the window's root from a `ControlPanelState`**, shows it on a stage, finds a control by its id, presses it by `fire()` and reads what the window then shows. It finds nothing by its position, its style class or its words, but where a record fixes the words, as ADR-344 §1 fixes the two label commands'.
- **Every window test is a unit test**, `*Test`, run by surefire on both CI jobs, needing neither a display, a browser nor `verify`.
- **A native dialog is not opened by a test.** The window asks for a folder or a file through a seam of its own that a test answers; that `DirectoryChooser` and `FileChooser` open is not held.
- **A report's viewer is JavaFX's own `WebView`, run on the Headless platform** as measured above. A test opens a report in it, presses a link inside the report by the page's own `click()` through `WebEngine.executeScript`, and reads where the viewer is from `WebEngine.getLocation()` and the page's title from its own `document.title`. What the window hands to the system to open goes through the same seam as a dialog, which the test answers, so no program of the system starts.
- **What a window test cannot hold**: how the window looks on Windows' Direct3D or Linux's OpenGL pipeline, focus, a native dialog, what the system opens for an address handed to it, and what WebKit draws of a report and a pointer's hover over it, which are measured above. A record whose decision rests on one says so.
- **jsoup leaves the pom** with the last test that reads a page; nothing else names it.

**The tests of the server, the pages and the second jar go, and the change that builds the window deletes them**, with the classes they read; they stand until then, since the classes they read still stand:

- every `*ScreenTest`, and the helpers they share: `PanelBrowser`, `PanelBrowserTest`, `ScreenServer`, `ScreenServerKillsWhatItsTestStartedTest`, `RawHttp`, `TabPaths`, `JarForScreenTests`, `StopCostsShown`;
- the server's and the start's: `ControlPanelServerTest`, `ControlPanelServerTabTest`, `ControlPanelStartTest`, `ControlPanelAsStarted`, `ControlPanelForTests`, `TheControlPanelsTabTest`, `TheControlPanelOpensItsTabOnceAcrossStartsTest`, `TheControlPanelOpensAWindowOfItsOwnTest`, `SystemBrowserOpenerTest`, `EveryPageHoldsOnlyTheControlPanelsScriptTest`;
- the pages' readings of rules the window keeps, each held now by a test that reads the rule without a page: `TheChildEndsWithTheControlPanelTest` by `TheChildEndsWithTheControlPanelsJvmTest`, `TheReportLinksAreTheReadmesTableTest` by `TheWindowFollowsTheReadmesTableOfValuesTest`, `pipeline.TheControlPanelServesEveryReportTheJarWritesTest` by `pipeline.TheControlPanelListsEveryReportTheJarWritesTest`, `pipeline.TheControlPanelsStopCostsNameTheJarsStepsTest` by `pipeline.TheStopCostsNameTheJarsStepsTest`;
- `pipeline.TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest`; and of `pipeline.TheControlPanelsChildIT` its two methods on the control-panel jar, which is not built, its refusal of a jar not beside it being now `ChildRuntimeTest`'s. Its first method, the jar started as the window starts its child printing UTF-8 on the merged stream, reads no control-panel jar and stands, the child of ADR-278 §1 and §3 being the window's as it was the pages'.

The rules §4 keeps that only a page's test held are held now without a page: ADR-307 by `TheChildEndsWithTheControlPanelsJvmTest`, ADR-330 §7, a working directory made owner-only by a save, by `WorkingDirectoryWriteTest.aSaveOfTheProfileMakesAMissingWorkingDirectoryOwnerOnly` (corrected in place, [#700](https://github.com/algernon28/vespera/issues/700)), ADR-302 §7 by `pipeline.TheStopCostsNameTheJarsStepsTest` and the window test's rows, the report list and each key's reports and hint by `pipeline.TheControlPanelListsEveryReportTheJarWritesTest` and `TheWindowFollowsTheReadmesTableOfValuesTest`, ADR-331 §6 by `FolderStatusTest`, ADR-331 §8 by `TheLaunchFieldsSayWhatTheyHoldTest`, ADR-334 §5 by `CodeSetFolderChecksTest`, §6 by `NotRelevantFoldersTest` and §11 by `StartingValuesTest`. The tests of the classes §4 lists stand.

### 7. Run ids

No file under `src/main/java/io/algernon/vespera` changes for this record, so no run id moves. `EntryPoint` is outside the ten modules, and a stage's version is read from nothing else (ADR-058), the pom included.

## Consequences

- One jar: the window and the commands ship together and cannot drift apart, and the operator starts one file.
- The jar grows by one platform's JavaFX, about 46.8 MB on Windows, WebKit about 36 MB of it, and a jar built on one platform opens the window there alone.
- No port is listened on, and no other program on the machine can reach the control panel.
- Each start of the window writes one line of JavaFX's on standard error, the first start on a machine writes JavaFX's native libraries under `<user home>/.openjfx/cache/`, and the first report opened there writes WebKit's beside them, about 96 MB on Windows.
- A start of the jar with no argument that opens the window makes no `.vespera` in its current directory; `--headless` alone, and a start where the window cannot open, still make one, as a bare start of the command line does.
- The private-paths guard, which sees an agent's start of the jar, sees no more than before: what the window does is held by the written rule alone (ADR-264 §5).

## Known limits

- **The Headless platform was measured on Windows alone, and a report's viewer on it too.** On Linux it loads `libprism_sw.so` and the font libraries, which need FreeType and Fontconfig on the runner, and the viewer loads `libjfxwebkit.so`, which by its ELF needs only the C runtime's libraries and the JVM's; the Linux job holds both, and a job lacking a library fails every window test there, or every test of a report's viewer, until the workflow installs it.
- **A jar copied to another platform runs every command and opens no window**: started with no argument, it answers as its command line, with §2's line.
- **A `--headless` after the first argument is not read as one**, and the command line refuses it as an option it does not know.
- **The window is drawn by JavaFX 26.0.2's controls and their default theme under the one stylesheet**; how it looks is held by no test (§6).

## Alternatives refused

- **The window beside the browser panel.** Two faces on one contract would each need every change, and the operator chose one.
- **A separate application, or the second jar kept, with JavaFX in it.** The second jar holds no library today (ADR-285 §2); with JavaFX it would be a second fat jar to keep beside the first, and the operator wants one file.
- **A subcommand, `vespera panel`.** picocli parses it after `VesperaApplication.main` has made and locked a working directory; the window is no invocation and takes no working directory until the operator names one.
- **`VesperaApplication.main` deciding.** A class of the jar would name the control panel, which ADR-285 §3 keeps from happening; `EntryPoint` stands outside both.
- **A `Main-Class` extending `Application`.** On the class path the Java launcher refuses one with `JavaFX runtime components are missing`; Spring Boot's launcher would also have to stop being the `Main-Class`.
- **Every classifier in the jar.** 157.7 MB of what no build for this operator runs, and duplicate classes on the class path (§1).
- **TestFX with Monocle.** Two test dependencies for what JavaFX's own Headless platform does, and Monocle's builds trail JavaFX's releases.
- **Testing the window's model alone.** The operator's decisions are about what the window shows and where; a model holds none of them.
- **No `javafx-web`, and a report opened in the default browser.** About 37 MB less on Windows, but a report would leave the window for another program, which the operator refused (ADR-344 §6).
- **A `WebView` of `progress.html` in place of the cockpit's three charts.** The page is laid out for a browser's window and wraps its charts at ADR-322's widths; the window draws them at its own size from `progress.json` (ADR-344 §2), and the page itself opens in a report's tab (ADR-344 §6).

## Tests

| Method | What it holds |
| --- | --- |
| `EntryPointTest.noArgumentOpensTheControlPanel` | no argument: the control panel (§2) |
| `EntryPointTest.anyArgumentIsTheCommandLine` | `run`, `label --auto`, `publish`, `--help`, `--version`, an option alone and `run <root> --headless`: the command line, given every argument as it stands (§2) |
| `EntryPointTest.headlessFirstNeverOpensTheWindowAndIsDroppedBeforeTheCommandLine` | `--headless` alone and before a command: the command line, given the arguments after it (§2) |
| `EntryPointTest.theEntryPointIsNoJavaFxApplicationAndNamesBothItStarts` | `EntryPoint` extends no `Application`, lies outside both packages, and names `ControlPanel` and `VesperaApplication` (§2) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson` | every type a compiled class of the control panel names is its own package's, `java.base`'s, of a `javafx.` package or of `tools.jackson` (§3) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.noClassOfTheJarNamesTheControlPanel` | no compiled class of `io.algernon.vespera` names the control panel (§3) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.noClassOfTheJarNamesJavaFx` | no compiled class of `io.algernon.vespera` names a `javafx.` type, so a command never loads it (§1) |
| `controlpanel.HeadlessToolkitTest.theToolkitRunsOnTheHeadlessPlatformWithNoDisplay` | the toolkit started by the tests runs JavaFX's Headless platform (§6) |
| `controlpanel.HeadlessToolkitTest.aStageIsShownAButtonFoundByItsIdPressedAndTheSceneDrawn` | a stage shown, a button found by id and fired, its effect read, a snapshot the scene's size (§6) |
| `PackagedJarIT.theJarCarriesTheControlPanelAndJavaFxAndStartsAtTheEntryPoint` | the Spring Boot jar holds the control panel's classes and `EntryPoint`, the six OpenJFX 26.0.2 jars of the classifier `os.name` and `os.arch` give the platform that built it, `javafx-base`, `javafx-controls`, `javafx-graphics`, `javafx-media`, `javafx-web` and `jdk-jsobject`, each once, `Start-Class` `io.algernon.EntryPoint`, `Main-Class` Spring Boot's launcher and `Enable-Native-Access: ALL-UNNAMED` (§1, §2) |
| `PackagedJarIT.noControlPanelJarIsBuiltBesideIt` | no `-control-panel.jar` in `target` (§5) |
| `CliExitIT.aJarThatCannotOpenTheWindowAnswersAsItsCommandLine` | the packaged jar, started in a JVM of its own with no argument and a glass platform JavaFX does not have: §2's line, the commands' usage and picocli's usage code (§2) |
| `controlpanel.ChildRuntimeTest.theJarIsTheOneTheClassPathNames` | the class path's one file, absolute, renamed or not (§2) |
| `controlpanel.ChildRuntimeTest.aClassPathThatNamesNoJarIsRefusedWithOneLine` | a folder, a file not there and two entries: §2's refusal, naming the class path whole (§2) |
| `controlpanel.ChildRuntimeTest.neitherPathNorJavaHomePicksTheChildsJava` | `ControlPanel` holds `java.home`, `os.name` and `java.class.path`; no class holds `JAVA_HOME` or `PATH`; `ChildRuntime` and `ChildCommand` read no environment (§2) |
| `controlpanel.TheChildEndsWithTheControlPanelsJvmTest.endingTheControlPanelsJvmInAnOrderlyWayEndsTheChildItStarted` | a state in a JVM of its own, the hook registered and a child started: `SIGTERM` to the JVM ends the child too; off Windows (§4, ADR-307 §1) |
| `controlpanel.TheChildEndsWithTheControlPanelsJvmTest.noChildIsStartedOnceTheControlPanelIsEnding` | after `endWithTheControlPanel`, Run is refused with ADR-307 §1's line (§4) |
| `controlpanel.WorkingDirectoryWriteTest.aSaveOfTheProfileMakesAMissingWorkingDirectoryOwnerOnly` | a save of the profile makes a missing working directory owner-only, with no page (§4, ADR-330 §7) |
| `controlpanel.FolderStatusTest.aFolderFieldSaysHowItsFolderStands` | not chosen, found with free space, will be created, no such folder (§4, ADR-331 §6) |
| `controlpanel.TheLaunchFieldsSayWhatTheyHoldTest.eachLaunchFieldHasItsExampleAndHint` | each launch value's example and hint, ADR-331 §8's table's (§4) |
| `controlpanel.TheWindowFollowsTheReadmesTableOfValuesTest.eachKeysHintIsItsRowsWords` | each key's hint is its README row's (§4, ADR-331 §8) |
| `controlpanel.TheWindowFollowsTheReadmesTableOfValuesTest.eachKeysReportsAreTheReportsItsRowNames` | each key's reports are its README row's, in order (§4, ADR-306 §3) |
| `pipeline.TheControlPanelListsEveryReportTheJarWritesTest.everyReportTheJarWritesIsListedByTheControlPanel` | `Reports.ALL` is the jar's report file names, each once (§4, ADR-306 §2) |
| `pipeline.TheStopCostsNameTheJarsStepsTest.theStopCostsNameEachStepOfTheJobInTheOrderItRunsThem` | `StopCosts.ROWS` names the job's steps in order, each with words (§4, ADR-302 §7) |
| `controlpanel.NotRelevantFoldersTest.theListWrittenIsReadBackAsItWas` | the list written whole, read back equal (§4, ADR-334 §6) |
| `controlpanel.NotRelevantFoldersTest.aFolderIsWrittenAsAnOccurrencesPathSpellsOne` | which folders may be written (§4, ADR-334 §6) |
| `controlpanel.NotRelevantFoldersTest.aQuestionLiesBelowAFolderByWholeElements` | a question below a folder by whole elements, another form refused (§4, ADR-334 §6) |
| `controlpanel.CodeSetFolderChecksTest.aFolderIsAddedOnlyWhereItIsThereAndApartFromTheOthers` | a folder outside the corpus root added only where whole, there and apart (§4, ADR-334 §5) |
| `controlpanel.CodeSetFolderChecksTest.theFoldersStatedAreReadInTheirOrder` | `codeSetFolders` read in order on the path separator (§4, ADR-334 §5) |

The packaged jar's three tests of the second jar and its manifest are replaced by its two rows above, and ADR-285's and ADR-288's Tests sections name those; ADR-288's name `ChildRuntimeTest`'s methods as they now are. `NotRelevantFoldersTest` and `CodeSetFolderChecksTest` read classes that stand, and pass now.

## What the implementation owes

Beside the tests above, which it turns green, the change that builds the window declares both dependencies of §1 in the pom, with the comment above them naming this record and, for `javafx-web`, ADR-344 §6, changes the `headless` and `gui` profiles of the pom as §2 has them, the `gui` profile keeping its name, and deletes the tests §6 lists. It changes the README's lines on building and running in one command, headless, and its section on starting the control panel: the window opened by the jar with no argument or by the `gui` profile, and `--headless`, or the `headless` profile, the way to run the jar without it.

## What this does not decide

- **What the window shows and where**: ADR-344.
- **The note saved as a value's provenance**: ADR-343.
- **A jar for another platform, an installer or a native image**: distribution stays out of scope (ADR-158).
- **Whether the window's controls are checked for accessibility.**
