# ADR-292 — The control panel remembers its launch values in a file of the user's own under the temp directory, and passes five of them through the child's environment

> **Partly amended — see [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md).** Of What this does not decide: writing the confirmed seed folder into `profile.yaml` is decided there.

> **Partly amended — see [ADR-323](0323-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md).** Of §3: the folder of the user's own also holds `token`, the control panel's token, under the same rules, read and written only by a start at the fixed port. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md), two commands and no new option: the values beyond the corpus root and the working directory reach the child through its environment, not its command line (§4).
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md) and [ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md): the working directory is named by `--db-dir=` and the corpus root by `run`'s argument, as ADR-278 §1 passes them (§4).
- **Keeps**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md): the control panel sets `VESPERA_DELIVERABLE_ROOT` for each invocation; this record says only that the value is remembered and when it is passed at all (§4).
- **Keeps**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md): a step that would record conversions under an image the sidecar does not run stops, which is why an empty Docling image is warned of and not refused (§5).
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the file is read and written with `java.base` alone, and names the jar's properties as text, linking no class of the jar (§2, §7).
- **Keeps**: [ADR-288](0288-the-operator-starts-the-control-panel-with-java-jar-and-it-starts-the-jar-beside-its-own-with-the-java-running-it.md) §4: the jar started is the one beside the control panel's own, and is not a launch value (§1).
- **Amends**: ADR-288's What this does not decide, whose values passed to the child and whether the operator can name another jar are decided here (§1). It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, whose child's environment held `CI=1` alone: it holds `CI=1` and the launch values of §4 that are not empty; and its What this does not decide, whose values passed and remembered are decided here. It carries a note at its head.
- **Settles**: [#564](https://github.com/algernon28/vespera/issues/564). That the last values are kept in a file under the user's temp directory, and that the working directory, the corpus root and the values beside them are named on the control panel, are the operator's; §1 to §8 are decided under the operator's delegation of 2026-10-19.

## Context

The **control panel** starts the jar as its child with one of two command lines, `run <corpus root> --db-dir=<working directory>` and `label [--auto] --db-dir=<working directory>`, every path absolute, with `CI=1` in the child's environment (ADR-278 §1). Everything else the jar reads per machine is a Spring property: `application.yaml` ships a default or an empty key, and `application-local.yaml`, the environment or `--<property>=<value>` overrides it. The deliverable root is `vespera.deliverable-root`, which the control panel sets through the environment as `VESPERA_DELIVERABLE_ROOT` (ADR-276).

The properties the jar reads that vary from one machine to the next are these:

| Property | Read by | Default where the key is not set |
| --- | --- | --- |
| `vespera.corpus-root` | `run`, when its argument is absent (ADR-066) | empty, refused |
| `vespera.working-dir` | every invocation; `--db-dir` sets it | `${user.dir}/.vespera` |
| `vespera.deliverable-root` | stage 6b and the closing line (ADR-276) | empty: `<working directory>/deliverable/<generation run>/` |
| `vespera.docling.image` | stage 2's check of the sidecar (ADR-179) | the image `compose.yaml` runs, the CPU build |
| `vespera.docling.base-url` | `DoclingClient` | `http://localhost:5001` |
| `spring.ai.ollama.base-url` | `OllamaClient` and Spring AI's chat model | `http://localhost:11434` |
| `vespera.label.model` | `OllamaRelevanceLabeller`, `label --auto` | `spring.ai.ollama.chat.options.model`, `qwen3:8b` |

The rest are the datasource's URL and Hikari's pool, which are the jar's own and fixed by ADR-127 and ADR-180, and `vespera.progress-interval`, how often `progress.html` is rewritten (ADR-265).

ADR-278 measured, on Windows 11 with ANSI code page 1252, that a child JVM receives its arguments through the ANSI code page, `ł` and `漢` each arriving as `?`, while an environment variable holding them arrives whole.

Spring's relaxed binding reads a property from the environment variable named by upper-casing it and writing `_` for each `.` and `-`: `vespera.docling.base-url` from `VESPERA_DOCLING_BASE_URL`. The environment ranks above `application.yaml` and `application-local.yaml`, and below the command line.

The default of `vespera.docling.image` names a CPU image; on a machine whose sidecar runs another image, every step that converts stops before it records a conversion (ADR-179).

`java.io.tmpdir` is per user on Windows (`%LOCALAPPDATA%\Temp`) and on macOS, and is the shared `/tmp` on Linux.

## Decision

### 1. The launch values

A **launch value** is a value the control panel passes when it starts an invocation, remembered per user, and never a profile value. There are seven, each named by the jar's own property:

| Launch value | Key |
| --- | --- |
| the corpus root | `vespera.corpus-root` |
| the working directory | `vespera.working-dir` |
| the deliverable root | `vespera.deliverable-root` |
| the Docling image | `vespera.docling.image` |
| Docling's address | `vespera.docling.base-url` |
| Ollama's address | `spring.ai.ollama.base-url` |
| the label model | `vespera.label.model` |

Beside them the file remembers one value that is not passed to the child, the last seed folder, under the key `last-seed-folder` (§6).

None of the datasource's or Hikari's properties is a launch value, nor `vespera.progress-interval`, which shapes the output the control panel itself reads and is the jar's. Nor is the jar: it is the one beside the control panel's own (ADR-288 §4).

### 2. The file

The launch values are one `java.util.Properties` file, read and written in UTF-8 through a `Reader` and a `Writer`, so a character outside Latin-1 is written as itself and not as a `\u` escape. It holds the eight keys of §1 whose value is not blank, and nothing else: no token, no password, no text of any document. A key it does not know is ignored as it is read and is not written back. A blank value, whitespace or nothing, is the same as an absent key. Any other value is kept as it was typed.

The file is written when an invocation is started, with the values it was started with, and at no other time: not as a field is typed. It is written whole to a temporary file in its own folder, which is then moved over it with `ATOMIC_MOVE` and `REPLACE_EXISTING`, so a reader finds the old file or the new one and never part of either.

### 3. The folder of the user's own

The file is

```
<java.io.tmpdir>/vespera-control-panel-<user>/launch-values.properties
```

where `<user>` is `user.name` with every character outside `A`–`Z`, `a`–`z`, `0`–`9`, `.`, `_` and `-` written `_`, and `_` where `user.name` is empty.

Where the folder does not exist, the control panel creates it as it first writes the file: on a file system with POSIX permissions the folder `rwx------` and the file `rw-------`; on Windows with no change to the permissions `%LOCALAPPDATA%\Temp` gives.

The control panel neither reads nor writes in a folder that exists and is not the user's own: a path there that is not a folder, or, on a file system with POSIX permissions, a folder whose owner's name is not `user.name` or that its group or others may write in. It then does as it does where the file is missing (§5), and writes one line on its standard error, naming the folder:

```
the control panel neither reads nor keeps its launch values in <folder>, which is not a folder only this user can write in
```

It still starts. On Windows the owner is not compared, since a folder made by a process run as administrator is owned by the Administrators group and not by the user, and `%LOCALAPPDATA%\Temp` is the user's already.

Keeping other users out of the control panel as a whole is #563's; this section covers the file.

### 4. How each reaches the child

| Launch value | Reaches the child as |
| --- | --- |
| the corpus root | `run`'s argument, absolute (ADR-278 §1) |
| the working directory | `--db-dir=<working directory>`, absolute, and the child's current directory (ADR-278 §1) |
| the deliverable root | `VESPERA_DELIVERABLE_ROOT`, absolute |
| the Docling image | `VESPERA_DOCLING_IMAGE` |
| Docling's address | `VESPERA_DOCLING_BASE_URL` |
| Ollama's address | `SPRING_AI_OLLAMA_BASE_URL` |
| the label model | `VESPERA_LABEL_MODEL` |

The five variables are added to the child's environment beside `CI=1`, for `run` and for `label` alike. The command line stays the one of ADR-278 §1. A launch value that is empty is not passed at all, so the child reads what it would read started by hand: the operator's own environment, which the child inherits, `application-local.yaml`, or `application.yaml`'s default. The deliverable root is made absolute against the control panel's current directory, since the child's current directory is the working directory.

### 5. A missing file, and empty fields

Where the file is missing, as on a first start or after the system has cleared its temp directory, every field starts empty, and nothing is said. Where it cannot be read, or holds what `Properties` cannot load, or bytes that are not UTF-8, every field starts empty, and one line on the control panel's standard error says so, naming the file and the reason:

```
the control panel could not read its launch values at <file>, and every field starts empty: <reason>
```

Neither is fatal. Where the file cannot be written, one line says so and the invocation is started all the same:

```
the control panel could not keep its launch values at <file>: <reason>
```

Run is refused while the corpus root or the working directory is empty, and Label while the working directory is empty, each with one line:

| Empty | Refusal |
| --- | --- |
| the corpus root and the working directory | `Run needs the corpus root and the working directory` |
| the corpus root | `Run needs the corpus root` |
| the working directory | `Run needs the working directory` |
| the working directory, for Label | `Label needs the working directory` |

The jar's own working directory is `.vespera` under its current directory, which the control panel sets to the working directory, so an empty one has no default to fall back on; and `run` with no corpus root is refused by the jar (ADR-066).

Every other empty field starts the child on the jar's default (§4). An empty Docling image adds one warning line to Run, and does not refuse it:

```
no Docling image is named, so the jar expects the sidecar to run its own default image, and stops before converting anything where it runs another
```

A wrong default costs no work, since ADR-179's check stops the step before it records a conversion. Label gets no warning, since it converts nothing.

### 6. The last seed folder

The file remembers the last seed folder the operator confirmed, under `last-seed-folder`. When the operator names a working directory, the control panel offers it as that working directory's seed folder, for the operator to confirm or change. It is never passed to the child, by argument or environment. Writing a confirmed seed folder into `profile.yaml` is #566's.

### 7. The classes

`LaunchValues`, a record of `io.algernon.controlpanel`, held to ADR-285 §3:

```java
public record LaunchValues(String corpusRoot, String workingDirectory, String deliverableRoot, String doclingImage,
        String doclingBaseUrl, String ollamaBaseUrl, String labelModel, String lastSeedFolder) {

    // the compact constructor makes a null or blank component ""
    public static LaunchValues none();                                 // every component ""
    public static Path fileIn(Path tempDirectory, String userName);    // §3's path
    public static Optional<String> refusalOfFolder(Path folder, String userName);  // §3's line, or empty
    public static LaunchValues load(Path file, String userName, Consumer<String> said);
    public void store(Path file, String userName, Consumer<String> said);
    public Map<String, String> toChildEnvironment();                   // §4's five variables, the empty left out
    public Optional<String> refusalToRun();                            // §5
    public Optional<String> refusalToLabel();                          // §5
    public List<String> warningsToRun();                               // §5
}
```

`refusalOfFolder` is empty for a folder that does not exist. `load` and `store` take `user.name` and a consumer of lines, to which the control panel gives `System.err::println`; neither throws. `load` hands the consumer at most one line, §3's or §5's, and `store` at most one, §3's or §5's.

`ChildCommand` builds its two command lines from the launch values:

```java
public static ChildCommand run(Path java, Path jar, LaunchValues values);
public static ChildCommand label(Path java, Path jar, boolean auto, LaunchValues values);
```

each throwing `IllegalArgumentException` with `refusalToRun()`'s or `refusalToLabel()`'s line where there is one. Its `environment()` is `CI=1` and `values.toChildEnvironment()`.

Which screen calls `load` as the control panel starts, `store` as Run or Label starts, and shows the refusals and the warning, is the first screen's (#561).

### 8. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves. The jar reads every property named here already.

## Alternatives refused

- **The values on the child's command line as `--<property>=<value>`.** A path or a model's name holding a character outside the ANSI code page arrives as `?` on Windows (ADR-278's measurement), and the command line would grow past ADR-278 §1's two shapes.
- **The values in the working directory**, beside `profile.yaml`. The working directory is one of the values; it cannot say where it is, and the profile is the corpus's judgements, not the machine's.
- **The file in the user's home folder.** The operator settled the temp directory.
- **A fixed folder name `vespera-control-panel`.** On Linux `/tmp` is shared, and the first user to make the folder would own every other user's file.
- **Writing as a field is typed.** A value half typed would be remembered, and a value never started with would be offered as the last one.
- **Refusing Run on an empty Docling image.** The jar's default is right for every operator whose sidecar runs `compose.yaml`'s image, and a wrong one is refused by the jar before it costs a conversion.
- **`vespera.progress-interval` as a launch value.** It shapes the output the control panel reads, and is the jar's.

## Consequences

- The operator types each value once per machine; after a first start or a cleared temp directory every field is empty again, and Run is refused until the corpus root and the working directory are named.
- A launch value set on the control panel takes the place of the same property in the operator's environment and in `application-local.yaml`, for that invocation; an empty one leaves them in force.
- On Linux each user has a folder of their own under `/tmp`; a folder of that name another user made is not used, and the control panel remembers nothing until it is removed.
- The file holds paths and addresses of the operator's machine, readable by that user alone where POSIX permissions apply.
- A corpus root or working directory holding a character outside the ANSI code page still reaches the child on its command line, as ADR-278 §1 passes it.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.LaunchValuesTest.theFileIsInAFolderNamedForTheUserUnderTheTempDirectory` | `fileIn` is `<temp>/vespera-control-panel-<user>/launch-values.properties`, every character outside the safe set `_`, an empty name `_` (§3) |
| `controlpanel.LaunchValuesTest.aBlankValueIsTheSameAsNone` | a null, empty or whitespace component is `""`; another is kept as typed (§2) |
| `controlpanel.LaunchValuesTest.whatIsStoredIsReadBackWholeInUtf8` | a store then a load gives the same eight values, `ł` and `漢` among them, written as themselves in UTF-8 (§2) |
| `controlpanel.LaunchValuesTest.theFileHoldsTheJarsPropertyNamesForTheValuesNotBlankAndNothingElse` | the stored file, read by `Properties`, holds exactly the keys of §1 whose value is not blank, each with its value (§1, §2) |
| `controlpanel.LaunchValuesTest.storingReplacesTheFileWholeAndLeavesNothingBesideIt` | a second store replaces the first's values and leaves the folder holding the one file (§2) |
| `controlpanel.LaunchValuesTest.aKeyTheControlPanelDoesNotKnowIsIgnoredAndNotWrittenBack` | unknown keys, `vespera.progress-interval` and a datasource key among them, are not read and not stored again (§1, §2) |
| `controlpanel.LaunchValuesTest.aMissingFileStartsEveryFieldEmptyAndSaysNothing` | a missing file and a missing folder load as `none()`, with no line (§5) |
| `controlpanel.LaunchValuesTest.aFileThatCannotBeReadStartsEveryFieldEmptyWithOneLine` | bytes that are not UTF-8, a `\u` escape `Properties` refuses, and a folder in the file's place each load as `none()` with one line naming the file (§5) |
| `controlpanel.LaunchValuesTest.theFolderAndTheFileAreTheUsersAloneOnPosix` | where POSIX permissions apply, store creates the folder `rwx------` and the file `rw-------` (§3) |
| `controlpanel.LaunchValuesTest.aFolderOthersCanWriteInIsNeitherReadNorWritten` | where POSIX permissions apply, a folder `rwxrwxrwx` is refused by `refusalOfFolder`; load gives `none()` and store writes nothing, each with §3's line naming the folder (§3) |
| `controlpanel.LaunchValuesTest.aFolderAnotherUserOwnsIsNeitherReadNorWritten` | where POSIX permissions apply, an owner-only folder is accepted for the name of the user owning it and refused for another name; a file in the folder's place is refused, and a folder that does not exist is not (§3) |
| `controlpanel.LaunchValuesTest.whatCannotBeKeptIsSaidAndNotThrown` | a store where a folder that is not empty stands in the file's place says one line naming the file, and throws nothing (§5) |
| `controlpanel.LaunchValuesTest.fiveValuesReachTheChildsEnvironmentUnderTheJarsVariableNames` | `toChildEnvironment` maps each of the five to its variable, the deliverable root absolute, and leaves out every empty one, the corpus root, the working directory and the last seed folder (§4, §6) |
| `controlpanel.LaunchValuesTest.eachVariableIsReadByTheJarAsItsProperty` | Spring's relaxed binding of the environment resolves each of the five properties to its value (§4) |
| `controlpanel.LaunchValuesTest.runIsRefusedWithoutTheCorpusRootOrTheWorkingDirectory` | the three refusals of Run, and none with both named (§5) |
| `controlpanel.LaunchValuesTest.labelIsRefusedWithoutTheWorkingDirectory` | Label's refusal, and none with the working directory named, whatever the corpus root (§5) |
| `controlpanel.LaunchValuesTest.anEmptyDoclingImageIsWarnedOfAndNothingElse` | one warning with no Docling image, none with one, none for any other empty value (§5) |
| `controlpanel.ChildCommandTest.theChildGetsCiAndTheLaunchValuesInItsEnvironment` | `environment()` is `CI=1` and `toChildEnvironment()`, for `run` and `label`, and the process builder carries them; the command line is unchanged by them (§4, §7) |
| `controlpanel.ChildCommandTest.aRefusedCommandIsNotBuilt` | `run` and `label` throw with the refusal's line where there is one (§5, §7) |
| `controlpanel.ChildCommandTest.theLastSeedFolderReachesTheChildNowhere` | the last seed folder is in neither the command line nor the environment (§6) |

ChildCommandTest's other tests build their commands from launch values naming the corpus root and the working directory alone. The owner check on Windows is not made (§3), and no test makes a folder owned by another user, which needs a privilege the build does not have.

## What this does not decide

- **Where on the screen the fields, the refusals and the warning sit, and when `load` and `store` are called** (#561).
- **Writing the confirmed seed folder into `profile.yaml`** (#566).
- **Keeping other users out of the control panel** (#563).
- **A `vespera.working-dir` in the operator's environment or `application-local.yaml`**, which ranks above `--db-dir`'s fallback in `application.yaml` and so names another working directory than the one passed.
- **Checking a value as it is typed**: whether a folder exists, or an address answers.
