# ADR-288 — The operator starts the control panel with `java -jar`, and it starts the jar beside its own with the Java running it

> **Partly amended — see [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md).** Of What this does not decide: the values passed to the child are the seven launch values, and the jar is not one of them. The rest of this record stands.

> **Partly amended — see [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md).** Of §4: once its runtime is found, `ControlPanel.main` writes its line, starts the server and writes the address to open, and serves until its process ends, in place of exiting 0. Of What this does not decide: the README's text arrives with the screens. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): no launcher script, installer or image is shipped, and the operator starts the control panel as they start the jar, by `java -jar` (§1); distribution stays out of scope.
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): the pom gains two manifest entries in an execution it already has, and no dependency and no plugin (§1).
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §2, whose control-panel jar set no `Main-Class`: it now sets one, and an entry naming the jar's file name (§1); and its What this does not decide, whose `Main-Class` and how the operator starts the control panel are decided here. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1 and its What this does not decide: the `java` of §1's two command lines, and `<jar>`, are decided here (§3, §4). It carries a note at its head.
- **Settles** [#567](https://github.com/algernon28/vespera/issues/567).

## Context

ADR-285 builds the control panel as a second, plain jar of this build, `target/vespera-<version>-control-panel.jar`, beside the Spring Boot jar `target/vespera-<version>.jar`, both compiled at release 26, and leaves its `Main-Class` and how the operator starts it open. ADR-278 §1 starts the jar as `java … -jar <jar>`, and leaves how that `java` and that jar are found open.

The operator already starts the jar as `java -jar target/vespera-0.0.1-SNAPSHOT.jar` with a Java 26 they found themselves (ADR-158): no script, wrapper or installer is shipped, and making one is distribution work, out of the scope of map #551.

### What was measured

On Linux, a jar holding one class compiled at release 26, with a `Main-Class` naming it, started by `java -jar` with OpenJDK 21.0.12.1, prints on standard error, before any code of the class runs, and exits 1:

```
Error: LinkageError occurred while loading main class p.Hello
	java.lang.UnsupportedClassVersionError: p/Hello has been compiled by a more recent version of the Java Runtime (class file version 70.0), this version of the Java Runtime only recognizes class file versions up to 65.0
```

The same jar started with OpenJDK 26.0.2.1 runs. Class file version 70.0 is Java 26's, and 65.0 Java 21's.

## Decision

### 1. How the operator starts it

The operator starts the control panel as

```
java -jar target/vespera-<version>-control-panel.jar
```

the same shape as the jar's own `java -jar`. The control-panel jar's manifest gains two entries, set as `manifestEntries` of the `archive` configuration of `maven-jar-plugin`'s `control-panel` execution (ADR-285 §2), and of no other execution:

```xml
<archive>
    <manifestEntries>
        <Main-Class>io.algernon.controlpanel.ControlPanel</Main-Class>
        <Vespera-Jar>${project.build.finalName}.jar</Vespera-Jar>
    </manifestEntries>
</archive>
```

`Main-Class` names `io.algernon.controlpanel.ControlPanel`, whose `main` is the control panel's entry point. `Vespera-Jar` names the Spring Boot jar's file name, `vespera-<version>.jar`, as the same build writes it (§4). The Spring Boot jar's manifest is unchanged. No plugin and no dependency is added.

### 2. The Java that runs it

The operator names a Java 26 `java`, as they do for the jar today; where the `java` on `PATH` is older, they give its full path. The control panel is compiled at release 26, as the jar is (ADR-285 §4), so an older `java` refuses it before any of its code runs, with the `UnsupportedClassVersionError` measured above, exit 1. Nothing prints a line of the control panel's own in its place.

### 3. The Java that runs the child

The control panel starts its child with the runtime running it: `<java.home>/bin/java`, where `<java.home>` is `System.getProperty("java.home")`, and `java.exe` in place of `java` where `os.name` starts with `Windows`. It reads neither `PATH` nor `JAVA_HOME`. A control panel that runs at all runs on Java 26 or later (§2), so its child gets one by construction, and the operator finds a Java 26 once.

### 4. The jar it starts

The control panel starts the jar beside its own: the folder holding the control panel's own jar, its code source location, joined with the manifest's `Vespera-Jar` name. The name is read from the manifest rather than derived from the control panel's own file name, so a renamed control-panel jar still finds the jar.

Where no regular file is there, or the control panel runs from a folder of classes and not from a jar, `ControlPanel.main` refuses: it writes one line on standard error and exits 1. The line is

```
the control panel looks for the Vespera jar beside its own jar, and found none at <path>
```

where `<path>` is the absolute path it looked at: the folder joined with the name, or, run from a folder of classes, that folder.

Otherwise, until the control panel has a first screen (#561), `ControlPanel.main` writes one line on standard output and exits 0:

```
the control panel will start <jar> with <java>
```

`<jar>` and `<java>` are the absolute paths of §4 and §3.

The choice is one class, `ChildRuntime`, a record of the two paths, `java()` and `jar()`, built by

```java
static ChildRuntime find(Path controlPanelLocation, String jarName, Path javaHome, String osName)
        throws FileNotFoundException
```

whose exception's message is the refusal line above, and which `ControlPanel.main` calls with its own code source location, its manifest's `Vespera-Jar` value (`null` where it runs from a folder, which has no manifest), `java.home` and `os.name`. `ChildRuntime` and `ControlPanel` are classes of `io.algernon.controlpanel`, held to ADR-285 §3 like every other.

### 5. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves. The README gains no line here: how to start the control panel arrives in it with the control panel's first screen (#561).

## Alternatives refused

- **A `.cmd` or `.sh` script.** ADR-158 ships no launcher. On Windows, `cmd` parses its command line again, so a `&`, `%` or `^` in a path, or a space, is read as its syntax (ADR-266's reason for refusing `cmd /c start`). And a script that finds a Java 26 has to search the registry, the usual folders and `JAVA_HOME`: that is distribution work, out of scope.
- **A double-click on the jar.** Windows runs the `javaw` of the `.jar` association, which may be older than 26, and shows no console, so the refusal of §2 or §4 is lost. It is neither promised nor prevented.
- **An IDE run configuration.** It is a developer's tool, the operator is not one, and none is committed.
- **A `jlink` or `jpackage` image.** Distribution, out of scope.
- **A class compiled at release 8 that checks the version and prints a friendlier line.** A second compiler execution for one message.
- **The child started with the `java` on `PATH` or of `JAVA_HOME`.** Either may be older than 26 where the control panel's own is not, and the operator would find a Java 26 twice.
- **The jar's name derived from the control panel's own file name, its classifier cut off.** A renamed control-panel jar would lose it.

## Consequences

- The operator starts the control panel by one `java -jar` on its jar, with the Java 26 they already start the jar with.
- An older `java` fails with the JDK's own message, exit 1, before any line of the control panel's.
- The control-panel jar is useful only beside the Spring Boot jar of the same build, under its own file name; moved alone, or beside a renamed jar, it refuses with one line naming where it looked.
- The child runs on the same runtime as the control panel.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.ChildRuntimeTest.theChildsJavaIsTheJavaOfTheRuntimeRunningTheControlPanel` | `java()` is `<java.home>/bin/java` on Linux and macOS, and `<java.home>/bin/java.exe` where `os.name` starts with `Windows` (§3) |
| `controlpanel.ChildRuntimeTest.theJarIsFoundBesideTheControlPanelsJarByTheNameItIsGiven` | `jar()` is the control panel's folder joined with the name given, whatever the control panel's own jar is called (§4) |
| `controlpanel.ChildRuntimeTest.noJarBesideTheControlPanelIsRefusedWithOneLineNamingWhereItLooked` | no file of that name, or a folder of that name: refused, the line naming the path looked at (§4) |
| `controlpanel.ChildRuntimeTest.aControlPanelRunFromAFolderOfClassesIsRefusedWithOneLineNamingTheFolder` | a location that is a folder: refused, the line naming the folder (§4) |
| `controlpanel.ChildRuntimeTest.noClassOfTheControlPanelReadsPathOrJavaHome` | the control panel's compiled texts hold `java.home` and `os.name`, and neither `PATH` nor `JAVA_HOME` (§3) |
| `PackagedJarIT.theControlPanelJarsManifestNamesItsMainClassAndTheJarBesideIt` | the control-panel jar's manifest: `Main-Class` `io.algernon.controlpanel.ControlPanel`, `Vespera-Jar` the Spring Boot jar's file name; the Spring Boot jar's `Main-Class` still Spring Boot's (§1) |
| `pipeline.TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava` | `java -jar` on the control-panel jar in `target`: exit 0, standard error empty, one line naming the jar beside it and the `java` of the runtime that started it (§1, §3, §4) |
| `pipeline.TheControlPanelsChildIT.theControlPanelWithNoJarBesideItRefusesWithOneLine` | a copy of the control-panel jar alone in a folder: exit 1, standard output empty, one line on standard error naming where it looked (§4) |

§2's refusal is the JDK's, measured above, and held by no test of this build: the build has no older JDK to start.

## What this does not decide

- **The control panel's server, its address and port, and whether a browser opens** (#561, #563).
- **The values passed to the child**: its working directory, the corpus root, and whether the operator can name another jar (#564).
- **The README's text for starting the control panel**: with its first screen (#561).
