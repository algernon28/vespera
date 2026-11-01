# ADR-285 — The control panel is a second jar of this build, under a package of its own outside the nine modules, linking no class of the jar

> **Partly amended — see [ADR-288](0288-the-operator-starts-the-control-panel-with-java-jar-and-it-starts-the-jar-beside-its-own-with-the-java-running-it.md).** Of §2 and What this does not decide: the control-panel jar's manifest names `io.algernon.controlpanel.ControlPanel` as `Main-Class` and the Spring Boot jar's file name as `Vespera-Jar`, and the operator starts it with `java -jar`. The rest of this record stands.
>
> **Partly amended — see [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md).** Of §3: the files that are the control panel's whole contract with the jar also hold `next-invocation.properties`, which every invocation ending on the closing line writes in the working directory and every invocation holding the working-directory lock deletes as it starts; the control panel reads it after an exit code of 0 to learn what the next invocation wants. The rest of this record stands.

> **Partly amended — see [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md).** Of §3: the control panel also reads and writes three of the jar's file formats, `profile.yaml`, `relevance-labels.yaml` and `code-set-names.yaml`, in `java.base`, held to agree with the jar by contract tests.

> **Partly amended — see [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md).** Of §3: the files that are the control panel's contract with the jar also hold `effective-values.properties`, which `vespera run` and `vespera label` write in the working directory, saying what the jar would use where the profile names nothing; it is parsed in `java.base`, and no class of the jar is linked. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md): the nine modules stay nine, and the module rule is unchanged, since the control panel's code is outside `io.algernon.vespera` (§1).
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): the packaged jar is unchanged and the operator may still run it directly (§2).
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): one pom, and this record adds no dependency and no plugin the build does not already carry (§2, §4).
- **Reads**: [ADR-001](0001-tech-stack-is-a-fixed-constraint.md) as binding the control panel to Java alone (§4).
- **Applies**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md)'s rule within the control panel (§4).
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md)'s What this does not decide, on where the control panel's code lives and how it is built (§1, §2), and its Tests section, whose control-panel tests this record gives a home and lands where they need no screen (§5); and adds to its §4 what a lock file that holds no holder line is, and to its §5 how any other exit code is shown (§5). It carries a note at its head.
- **Read with**: [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md): the last line of output that is not blank, shown with how the child ended, is the command's closing line (§5).
- **Settles** [#562](https://github.com/algernon28/vespera/issues/562).

## Context

ADR-278 decides how the **control panel** starts, watches and stops the jar as its child, and leaves where its code lives and how it is built to this record. #554 settled that it is plain forms written by a server on the JDK's `com.sun.net.httpserver`, with no dependency and no Node.

The build is one Maven project. `src/main/java/io/algernon/vespera` holds nine packages, each a Spring Modulith module, and `ModuleBoundariesTest` names the nine. A stage's implementation version is the last commit touching its module's folder under `src/main/java/io/algernon/vespera` (ADR-058); nothing else names a run. The packaged jar is Spring Boot's, repackaged from the jar `maven-jar-plugin` builds from `target/classes`, and `maven-jar-plugin` is already managed by Spring Boot's parent. CI builds on Linux, through integration tests, and on Windows, on NTFS, through unit tests.

## Decision

### 1. Where the code lives

The control panel's code lives in this same Maven project, in `src/main/java` under the base package **`io.algernon.controlpanel`**, and its tests in `src/test/java/io/algernon/controlpanel`. It is outside `io.algernon.vespera`, so it is no Spring Modulith module: `ApplicationModules` does not see it, `ModuleBoundariesTest`'s nine stay nine, and ADR-040's rule is neither widened nor amended.

### 2. How it is built

One build, as today, on the existing CI, with no Node, no new dependency and no new plugin: `maven-jar-plugin`, already managed by Spring Boot's parent, gains two settings.

- The `default-jar` execution excludes `io/algernon/controlpanel/**`, so the Spring Boot jar, `target/vespera-<version>.jar`, carries no class of the control panel. It is otherwise unchanged.
- A second execution, bound to `package`, writes a plain jar with the classifier `control-panel`, `target/vespera-<version>-control-panel.jar`, holding only `io/algernon/controlpanel/**`. It nests no library and starts no Spring context.

Its `Main-Class` is set by the record that gives the control panel its first screen (#561, #567); this record sets none. How the operator starts it is #567's.

### 3. What it links

The control panel links no class of the jar, nothing under `io.algernon.vespera`: not `NextAction`, not `ProfileStore`, not the lock classes. Its whole contract with the jar is the process boundary and the files ADR-278 names: the command line, the exit code, the merged output, the holder lines of `vespera.lock` and of the corpus-root lock, and `progress.html`.

It names types of its own package and of two JDK modules only, `java.base` and `jdk.httpserver`. `java.sql` is not one of them, so ADR-278's "no connection to `vespera.db`" holds by construction. No class of the jar names the control panel either.

The holder line's format is parsed by the control panel itself. A contract test, whose class path holds both, holds that the control panel's parse reads what the jar's writers, `WorkingDirectoryLock` and `CorpusRootLock`, write.

A later decision that wants a class of the jar, or a file format of the jar's beyond these, in the control panel (#565, #566) amends this record.

### 4. Which records bind it

- **ADR-001** binds it as Java only: it starts no Spring Boot context and needs no Spring AI. It is compiled by the same compiler, at the same release, Java 26, as the jar.
- **ADR-046 and ADR-216** bind it as they bind the jar: one pom, and nothing ships that no decision requires.
- **ADR-130**'s rule, one owner of a page's skeleton, styling and escaping, binds the control panel's own pages within the control panel. It cannot link `ReportPage` (§3), so one class of its own owns them, written with its first page by the tickets of its screens.
- **ADR-158** is kept: the jar is unchanged, and the operator may still run it directly.
- **The module rule** in `AGENTS.md` is unchanged.

No run id moves: no file under `src/main/java/io/algernon/vespera` changes, and a stage's version is read from nothing else (ADR-058), the pom included.

### 5. What lands now

Of ADR-278's tests, those that need no screen land with this record, as code under `io.algernon.controlpanel`:

- **Held** (ADR-278 §4): a holder line is held while its pid is alive and that process's start instant is no later than `started` plus 1 second; a process giving no start instant counts as held; a dead pid, or a pid reused by a process started later, is not held. Reading a holder line takes no lock and starts no process. A lock file that is absent, empty, or whose first line is blank is held by nobody: the jar leaves the file between invocations. A first line that is not blank and does not parse as a holder line counts as held, and is shown whole: the control panel cannot rule out a live holder, and the contract test (§3) keeps its parse in step with the jar's writers.
- **The command line** (ADR-278 §1): only the two commands, `run <corpus root> --db-dir=<working directory>` and `label [--auto] --db-dir=<working directory>`, after `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar <jar>`, with `CI=1` in the child's environment, every path made absolute, and the working directory as the child's current directory. Nothing builds `--version` or `--help`.
- **Draining** (ADR-278 §3): standard error is merged into standard output, one thread reads the merged stream to its end as UTF-8, a child writing more than a pipe's buffer to standard error alone still ends, and the tail keeps the last **200** lines, in memory only.
- **Stop** (ADR-278 §6): the child's descendants are killed before the child, so a grandchild is gone when Stop returns. Only the child the control panel started can be stopped: nothing in the control panel kills a process it found by its pid. Its confirmation is one sentence:

  ```
  Stopping loses the work done since the running step's last commit, and the next vespera run continues from there.
  ```

- **How it ended** (ADR-278 §5): **Stopped**, when the control panel's own Stop ended it, whatever its exit code; exit 0 ended; exit 1 failed or refused; exit 2 a defect of the control panel; any other exit code of a child the control panel did not stop, such as one killed by another program, failed or refused, with its code. Every exit code is shown as one of the four. Each with the last line of output that is not blank, which is the command's closing line (ADR-281), and none for a child that printed none.

The types this asks for are `HolderLine`, `HolderProcess`, `HolderReading`, `ChildCommand`, `Child`, `HowItEnded` and `Ending`, with the members the tests below call. The tests that need a child process start a small test class of their own with the JVM running the tests, never the jar, and run on Linux and on Windows.

Two of ADR-278's tests wait for the screens: Run and Label disabled while a line is held (#561), and an orderly exit asking whether to leave the child running (#568).

## Alternatives refused

- **A tenth module under `io.algernon.vespera`.** It amends ADR-040, and a module that names no stage and owns no table fits no place in its rule.
- **A Maven multi-module build.** A root of packaging `pom` moves `src/` and every path the docs, `docs/check-claims.mjs`, CI and the agent guard name, and splits one pom into three.
- **A standalone Maven project in a subfolder.** A second build in CI, and the test conventions not shared.
- **One jar holding both.** The packaged jar would carry classes no decision of the jar requires (ADR-216), and a jar started for the control panel would carry Spring Boot's launcher.
- **The control panel linking the jar's lock classes to read a holder line.** It would bring `io.algernon.vespera` and Spring onto the control panel's class path for one line of text.

## Consequences

- The build writes two jars where it wrote one; the Spring Boot jar holds what it held.
- A change under `io.algernon.controlpanel` moves no run id.
- A class of the control panel that names a type of the jar, of Spring or of `java.sql` fails the build's unit tests.
- A change to the holder line's format fails the contract test until the control panel's parse follows it.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageAndOfTheJdksBaseAndHttpServerModules` | every type a compiled class of `io.algernon.controlpanel` names is its own package's or of `java.base` or `jdk.httpserver` (§3) |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest.noClassOfTheJarNamesTheControlPanel` | no compiled class of `io.algernon.vespera` names a type of `io.algernon.controlpanel` (§3) |
| `pipeline.TheControlPanelReadsTheHolderLinesTheJarWritesTest.theWorkingDirectoryLocksHolderLineIsReadAsHeldWhileItsHolderLives` | a second JVM holding a working directory: its `vespera.lock` line is read whole, with that JVM's pid, held while it lives and not once it is killed (§3) |
| `pipeline.TheControlPanelReadsTheHolderLinesTheJarWritesTest.theCorpusRootLocksHolderLineIsReadAsHeldWhileItsHolderLives` | the same of a corpus-root lock's line (§3) |
| `PackagedJarIT.theSpringBootJarCarriesNoClassOfTheControlPanel` | the Spring Boot jar holds `VesperaApplication` and nothing under `io/algernon/controlpanel/` (§2) |
| `PackagedJarIT.theControlPanelJarCarriesOnlyTheControlPanelsClasses` | the `control-panel` jar holds classes of `io/algernon/controlpanel/` and nothing else outside `META-INF/`, so no Spring and no nested library (§2) |
| `controlpanel.HolderLineTest.aLineIsReadIntoItsPidAndStartedAndKeptWhole` | a holder line's pid, its `started` as an instant, and its text whole |
| `controlpanel.HolderLineTest.aLineWhoseProcessIsAliveAndStartedNoLaterThanStartedPlusOneSecondIsHeld` | the rule's first case, at its bound |
| `controlpanel.HolderLineTest.aLineWhoseProcessStartedMoreThanOneSecondAfterStartedIsNotHeld` | a reused pid is not held |
| `controlpanel.HolderLineTest.aLineWhosePidIsDeadIsNotHeld` | a dead pid is not held, whatever its start |
| `controlpanel.HolderLineTest.aLineWhoseProcessGivesNoStartInstantIsHeld` | a missing start instant counts as held |
| `controlpanel.HolderLineTest.theRuleReadsTheProcessTheSystemNames` | the rule against real processes: this JVM held, this JVM's pid under a `started` before it began not held, an ended process's pid not held |
| `controlpanel.HolderLineTest.readingALockedHolderLineTakesNoLock` | a holder line is read while its file is locked as the jar locks it |
| `controlpanel.HolderLineTest.readingAHolderLineStartsNoProcessAndTakesNoLock` | `HolderLine`, `HolderProcess` and `HolderReading` name no `ProcessBuilder` and call no lock of a `FileChannel` |
| `controlpanel.HolderReadingTest.anAbsentEmptyOrBlankLockFileIsNotHeld` | a lock file absent, empty or blank is held by nobody and shows no line |
| `controlpanel.HolderReadingTest.aLineThatDoesNotParseIsHeldAndShownWhole` | a first line not blank and not a holder line counts as held, shown whole |
| `controlpanel.HolderReadingTest.aLineThatParsesIsShownWholeAndHeldByTheRule` | a holder line that parses is held by the rule, not by being there |
| `controlpanel.ChildCommandTest.runIsTheJarsRunCommandWithBothEncodingFlagsAndAbsolutePaths` | `run`'s command line, whole and in order |
| `controlpanel.ChildCommandTest.labelIsTheJarsLabelCommandWithAutoOnlyWhenAsked` | `label`'s command line, with and without `--auto` |
| `controlpanel.ChildCommandTest.theChildHasCiSetToOneAndTheWorkingDirectoryAsItsCurrentDirectory` | `CI=1` and the working directory, absolute, on the process builder |
| `controlpanel.ChildCommandTest.onlyRunAndLabelCanBeBuilt` | no public constructor, and `run` and `label` the only factories |
| `controlpanel.ChildCommandTest.noClassOfTheControlPanelSpellsVersionOrHelp` | no compiled class of the control panel holds the text `--version` or `--help` |
| `controlpanel.ChildTest.aChildWritingMoreThanAPipeBufferToStandardErrorAloneEnds` | the stream nobody drains stalls no child |
| `controlpanel.ChildTest.standardErrorIsMergedIntoOneStreamInTheOrderWritten` | the two streams merged, in the order the child wrote them |
| `controlpanel.ChildTest.theTailKeepsTheLastTwoHundredLines` | the tail's size and which lines it keeps |
| `controlpanel.ChildTest.theOutputIsReadAsUtf8` | a line holding `ł`, `漢` and `è` arrives whole |
| `controlpanel.ChildTest.drainingWritesNothingIntoTheChildsDirectory` | the tail is kept in memory, not beside the child |
| `controlpanel.ChildTest.stopLeavesNeitherTheChildNorItsGrandchild` | descendants first, so a grandchild is gone when Stop returns |
| `controlpanel.ChildTest.onlyTheChildItStartedCanBeStopped` | nothing builds a `Child` from a pid, and only `Child` destroys a process |
| `controlpanel.ChildTest.stopIsConfirmedByOneSentence` | §5's sentence, word for word |
| `controlpanel.ChildTest.aStoppedChildIsShownAsStoppedThoughItExitsAsAFailureDoes` | Stopped, recorded by the control panel, whatever the exit code |
| `controlpanel.ChildTest.eachExitCodeIsShownWithTheLastLineThatIsNotBlank` | exits 0, 1 and 2, each with its last line that is not blank |
| `controlpanel.HowItEndedTest.howItEndedFollowsTheExitCodeAndTheControlPanelsOwnStop` | ADR-278 §5's table, row by row |
| `controlpanel.HowItEndedTest.anyOtherExitCodeOfAChildNotStoppedIsFailedOrRefused` | the four ways it is shown, and every other code, the extremes of an int included, failed or refused, or stopped after the control panel's own Stop |
| `controlpanel.ChildTest.aChildKilledFromOutsideIsShownAsFailedOrRefusedWithItsCode` | a child killed by another program: failed or refused, with its code, not stopped, and no last line where it printed none |

## What this does not decide

- **The control panel's `Main-Class`, and how the operator starts it** (#561, #567).
- **Run and Label disabled while a line is held**, and **an orderly exit of the control panel** (ADR-278 §4 and §7): the screens' tickets (#561, #568).
- **The one class owning the control panel's pages** (§4): written with its first page.
