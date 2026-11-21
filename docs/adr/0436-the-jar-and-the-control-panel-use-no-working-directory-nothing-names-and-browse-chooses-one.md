# ADR-436 — The jar and the control panel use no working directory nothing names, and Browse chooses one

> **Partly amended — see [ADR-439](0439-a-publish-may-name-an-earlier-run-by-its-invocation-and-the-control-panel-lists-the-earlier-deliverables.md).** Of §6's *"The window chooses a folder, never an invocation"* and the Keeps line's same words: Publish may be given an earlier invocation, chosen in the Working directory section's list of earlier deliverables, and passes it as `--invocation <id>`; Run and Label act on the latest invocation, as §6 has it. Of the Keeps line's ADR-047: `publish` gains the option `--invocation`. The rest of this record stands.

- **Date**: 2026-11-20
- **Status**: accepted on 2026-11-20.
- **Decided**: by the operator, in [#905](https://github.com/algernon28/vespera/issues/905): that nothing falls back to `.vespera` under the current directory, in the control panel or in the jar; that the shipped `application.yaml` names no working directory; that an operator's own `application.yaml` keeps its line, and the `application.yaml.new` written beside it says the `.vespera` folder is no longer used; that `vespera run`, `vespera label` and `vespera publish` with none named refuse in a clear line (§1); that the four sources of ADR-340 §6's table still name one, in its order, a system property or an environment variable before the YAML, beside the folder chosen in the window, and only its fifth row goes (§2); that the list's first row is shown only where a source names a folder, saying which source, and that a list holding nothing points to Browse (§3); that the ending panel says no working directory is chosen, as the window opens and again as Run, Label or Publish is pressed, with a *Choose a folder…* button that opens the picker, and that the starts are refused as the window's other refusals are (§4); that the picker is the existing Browse, the folder chosen added to the list file and chosen, the system's dialog alone making a new folder, Vespera making none, and an empty folder a working directory like any (§5); that Run, Label and Publish pass the row chosen, the sources' folder chosen only where no other row was chosen last (§6); that, at the first opening after this change, a `.vespera` folder of the current directory holding a ledger is added to the list file once, with a line saying so (§7); and that no run id moves (§9). Then, by the operator: that `--help` and `--version` are handed over before Spring, as `vespera serve` is, needing no working directory and opening no database (§1); that the window reads the working directory's line of the `application.yaml` beside the jar as the jar reads it, so that the two cannot disagree (§2); and that Run, Label and Publish act on the working directory's latest invocation, as before, choosing an earlier one being no part of this record (§6). Decided within those by this record: that the shipped line still reads `db-dir`, so `--db-dir=<path>` goes on naming the working directory (§1); that every other start of the jar's command line through Spring, and `vespera serve`, are refused alike (§1); that the window reads `application-local.yaml`'s line too, which Spring reads over `application.yaml`'s, and how it resolves a placeholder (§2); that each child is given the row chosen as the system property `vespera.working-dir` as well as `--db-dir`, so that no line of a file beside the jar stands over it (§6); the words, ids and order of the lines (§1, §3, §4, §7); that the row chosen last is the one the launch values name (§6); which row is chosen after Remove from list (§3); how *once* is kept (§7); and the seams (§8).
- **Amends**: [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md) §6: its table becomes §2's: rows 1 and 2 stand; rows 3 and 4, `db-dir`, are read only where a line of a file beside the jar names it, as Spring reads them; the line of `application-local.yaml`, then of `application.yaml`, beside the jar is read between; the fifth row, `.vespera` under the current directory with `data-source` `default`, is struck, and with it its *in use* sentence; where none names a folder, the window holds no folder of the sources (§2). Each child is given the folder it is passed as the system property `vespera.working-dir` too (§6). The resolution of a value that is not absolute, a blank value passed over, a value no path can hold and the eight variables withheld from the child stand. It carries a note at its head.
- **Amends**: [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §4: the first row is the sources' folder only where a source names one, and its words name the source (§3); the row chosen as the window opens is the launch value's, else the sources', else none, and none after Remove from list where no source names a folder (§3, §6); a list holding no row shows `working-directories-empty` (§3); Browse writes the list file as it adds a folder, a fourth time the file is written (§5); New… with no row chosen proposes its folder under the user's home folder (§5). Its rows after the first, the file, its form and its other writes stand. It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §1's *"§6 stands for the working directory and the child"*: the folder `WorkingDirectoryDefault` resolves is there only where a source names one, and a state given none of its sources holds no folder, not the one of its current directory (§2). It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §6: a sixth case of the starts' lines, `<Run> is not started: no working directory is chosen`, the first of them (§4). Its refusal of a working directory that is not there stands, and so does ADR-330 §7's Save of `profile.yaml`, the one thing of Vespera's that makes a working directory. It carries a note at its head.
- **Amends**: [ADR-417](0417-an-ending-panel-beneath-the-output-says-how-the-last-invocation-of-the-working-directory-ended-and-why.md) §2: before its four cases, where no working directory is chosen the panel says so (§4); §3's *"The panel words no advice"*: that case's line is a fixed sentence of the window's that says what to do (§4); §4: a fourth button, `ending-choose-folder`, *Choose a folder…*, shown in that case alone (§4). It carries a note at its head.
- **Amends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s *"Configured as a Spring Boot property, `vespera.working-dir`, defaulted in `application.yaml`"*: the shipped `application.yaml` defaults it to nothing, and a start that is given none is refused (§1). It carries a note at its head.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8's *"else `.vespera` under `user.dir`"*: `vespera serve` naming no working directory is refused in a line of its own (§1). It carries a note at its head.
- **Amends**: [ADR-341](0341-vespera-serve-tells-a-page-from-a-copy-by-the-websites-list-and-the-files-name-serves-working-directory-is-never-passed-and-its-refusals-answers-and-readings-are-as-built.md) §4's *"A blank `--db-dir=` is refused"* in the disagreement's line: read as unset, it names no working directory, and §1's line refuses it. It carries a note at its head.
- **Amends**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1's table, whose jar's default for `vespera.working-dir` is `${user.dir}/.vespera`: the jar has none (§1). It carries a note at its head.
- **Amends**: [ADR-397](0397-the-yaml-configuration-lies-beside-the-jar-and-the-jar-writes-its-shipped-copy-where-a-file-is-missing.md) §10's *"`application-local.yaml` stays outside §3's sources"*: for the working directory alone, the window reads `application-local.yaml`'s line beside the jar over `application.yaml`'s, as Spring does (§2). It carries a note at its head.
- **Keeps**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8's hand-over before Spring, which `--help` and `--version` now share (§1). [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §4: `vespera help` and `vespera version` are never passed by the window. [ADR-397](0397-the-yaml-configuration-lies-beside-the-jar-and-the-jar-writes-its-shipped-copy-where-a-file-is-missing.md) §3 and §5: a missing `application.yaml` is written from the shipped copy, and a file that differs is left as it is, its `.new` written beside it and named in one line. [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §1 to §3 and §5 to §7: the names, the warning, the rows' words after the source, New… and a publish's run. [ADR-417](0417-an-ending-panel-beneath-the-output-says-how-the-last-invocation-of-the-working-directory-ended-and-why.md) §1, §5 and §6: where the panel lies, its colours and ADR-383's warning. [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) and ADR-393 §7: which runs a stage reads, and that a publish delivers what the working directory's last `vespera run` arrived at; the window chooses a folder, never an invocation. [ADR-395](0395-one-vespera-runs-on-a-machine-at-a-time-and-the-control-panels-own-child-is-let-through.md): the machine lock is taken before any of §1's refusals. [ADR-047](0047-the-pipeline-never-blocks.md): no command and no option is added.
- **Settles**: [#905](https://github.com/algernon28/vespera/issues/905); choosing an earlier invocation is no part of it (What this does not decide).
- **Rests on**: Spring Boot 4.1's order of property sources, read: a system property, then the environment, then `application-local.yaml` and `application.yaml` beside the jar, the first over the second (ADR-397 §4); a placeholder `${name:default}` resolving to its default where `name` is not set, `${name:}` to the empty string, a default holding a placeholder of its own resolved in turn; `EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER`, `Ordered.HIGHEST_PRECEDENCE + 10`, reading the configuration files, and `LoggingApplicationListener.DEFAULT_ORDER`, `Ordered.HIGHEST_PRECEDENCE + 20`, setting up Logback, both on `ApplicationEnvironmentPreparedEvent`, read in the 4.1.1 jar.

## Context

**The jar.** The shipped `application.yaml` names the working directory `${db-dir:${user.dir}/.vespera}` (ADR-054, ADR-397). A command given no `--db-dir`, no `vespera.working-dir` and no `VESPERA_WORKING_DIR` opens `.vespera` under its current directory, and `WorkingDirectoryPreparer` makes it. `vespera serve` resolves the same line itself, `.vespera` under `user.dir` last (ADR-333 §8). `logback-spring.xml` writes the log under `${workingDir}/logs`, `workingDir` read from `vespera.working-dir`, and the datasource's URL is `jdbc:sqlite:${vespera.working-dir}/vespera.db`, so a working directory resolved to the empty string would put both at the root of a drive. `--help` and `--version` build the same application, and so open the working directory too.

**The control panel.** `WorkingDirectoryDefault` resolves ADR-340 §6's table: the system property `vespera.working-dir`, `VESPERA_WORKING_DIR` in its four spellings, the system property `db-dir`, `DB_DIR` in its four spellings, and else `.vespera` under the current directory. ADR-393 §4 makes that folder the list's first row, which cannot be removed, worded *(this machine's folder)*, and chosen wherever the launch value `vespera.working-dir` is empty. The control panel started from a folder with no such variable in its own environment shows `.vespera` there, a folder that is not there, and Run refuses it, `Run is not started: the working directory <folder> is no folder that is there` (ADR-331 §6); nothing in the row says what would name another. `.env` is read by the window for its fields, and never put in a process's environment. The window reads no line of a file beside the jar for the working directory: where the operator's `application.yaml` names a path, the jar opens that path whatever `--db-dir` the window passes, and `WorkingDirectoryOption` refuses the start as disagreeing.

**What is already there.** Browse, `working-directory-browse`, opens the system's folder dialog and adds the folder chosen as a row, chosen, without writing the list file (ADR-393 §4). New… proposes a folder that a Save of `profile.yaml` makes (ADR-393 §6, ADR-330 §7). The ending panel says how the last invocation of the working directory chosen ended (ADR-417). `EntryPoint` already says a refusal thrown before logging on standard error and exits 1 (ADR-397 §3).

## Decision

### 1. The jar names no working directory of its own

**The shipped `application.yaml` reads `working-dir: ${db-dir:}`.** `--db-dir=<path>`, the system property `db-dir` and `DB_DIR` go on naming the working directory through it, and nothing else does where they are not set. The comment above it says where a working directory is named, `--db-dir=<path>`, `VESPERA_WORKING_DIR`, the system property `vespera.working-dir` or a path written on this line, and holds the sentence, wrapped as the comment's lines wrap:

> When nothing names one, run, label, publish and serve refuse to start: the .vespera folder under the current directory, which earlier versions used, is no longer used.

**An operator's own `application.yaml` beside the jar is kept as it is** (ADR-397 §5): its line goes on naming `.vespera` under the current directory until the operator changes it. Its line differs from the shipped copy's, so ADR-397 §5's line names `vespera.working-dir`, and `application.yaml.new` beside it holds the shipped line and the sentence above.

**A start of the command line through Spring is refused where `vespera.working-dir` names no folder** once the configuration is read: not set, empty or white space alone, as `${db-dir:}` gives with nothing setting `db-dir`, and as a blank `--db-dir=` gives. That is every start but `vespera serve`'s, `--help`'s, `--version`'s and the window's: `vespera run`, `vespera label` and `vespera publish`, and the command line given no argument, as a start with no display has it, which builds the same application. It is refused after the configuration files are read and before Logback is set up, after the machine lock is taken (ADR-395) and before any other: no folder is made, no log file written, no lock but the machine's taken, no database opened and no report written. One line on standard error, exit 1:

```
Vespera is not started: no working directory is named. Name one with --db-dir=<path>, the environment variable VESPERA_WORKING_DIR, or vespera.working-dir in application.yaml beside the jar.
```

**`--help` and `--version` are handed over before Spring**, as `vespera serve` is (ADR-333 §8). A command line whose one argument, after a `--headless` first, is `--help`, `-h`, `--version` or `-V` is given to `pipeline.HelpAndVersion` by `EntryPoint`, on every path that runs the command line, before `VesperaApplication.main` and before the machine lock is asked: it prints on standard output what picocli prints for that option over `VesperaCommand`'s model, built with no application, and exits 0. No context is built, no listener runs, no configuration file is read, no working directory is named or opened, no database, log or lock is touched, and nothing is written. Any other arguments go to Spring as before.

**`vespera serve`** resolves, as before, `vespera.working-dir` then `db-dir` from its arguments, its system properties and its environment (ADR-333 §8, ADR-341 §4); where neither is set, or a blank `--db-dir=` is all it is given, it refuses, exit 1, before every other refusal of its own, making nothing:

```
vespera serve has nothing to serve: no working directory is named. Name one with --db-dir=<path>, the environment variable VESPERA_WORKING_DIR or the system property vespera.working-dir.
```

### 2. What names a working directory

**In the jar**, Spring's order, unchanged: the system property `vespera.working-dir`, then the environment's `VESPERA_WORKING_DIR` in its spellings, then `application.yaml`'s line, which as shipped reads `db-dir` from the command line, a system property or the environment. A system property or a variable naming the working directory comes before the file.

**In the window, the same order, so that the window and the jar cannot disagree.** Read once as it starts, from its own system properties, its environment, its current directory and the folder beside the jar (ADR-397 §1), the first that names a folder:

| Order | What is read | `source` | `name` |
|---|---|---|---|
| 1 | the system property `vespera.working-dir` | `system-property` | `vespera.working-dir` |
| 2 | `VESPERA_WORKINGDIR`, `VESPERA_WORKING_DIR`, `vespera_workingdir` or `vespera_working_dir`, the first set | `environment` | the variable read |
| 3 | the property `vespera.working-dir` of `application-local.yaml` beside the jar, as Spring flattens the file, where the file is there and sets it | below | below |
| 4 | the same of `application.yaml` beside the jar | below | below |

**A line of rows 3 and 4 is resolved as Spring resolves it.** `${db-dir}` and `${db-dir:<default>}` read `db-dir` as ADR-340 §6's rows 3 and 4 read it, the system property `db-dir`, then `DBDIR`, `DB_DIR`, `dbdir` or `db_dir`, the first set and not blank; `${user.dir}` is the current directory; any other name is read from the system properties, then from the environment by the name as written; a default is taken where its name is not set, and a placeholder in a default is resolved in turn. Where `db-dir` gives the value, `source` and `name` are `db-dir`'s, `system-property` and `db-dir` or `environment` and the variable; where the line's own text or its default gives it, `source` is `file` and `name` the file's name. A line that resolves to the empty string or white space, as the shipped `${db-dir:}` does with no `db-dir`, names no folder, and the next row is read; so does a file that is not there or does not set the key. A line holding a placeholder that resolves to nothing and has no default, or a file that cannot be read or does not load, names no folder; its *in use* line says so, and the next row is read. `db-dir` is read through a line alone: where neither file's line names it, `--db-dir`'s sources name nothing, as in the jar.

So an operator's own `application.yaml` still reading `${db-dir:${user.dir}/.vespera}` gives the window `.vespera` under its current directory, as it gives the jar, and a path written on the line gives that path.

`WorkingDirectoryDefault.of` gives the first row that names a folder, and nothing where none does: the window then holds no folder of the sources, whatever its current directory holds. **Beside them, the rows of the list** (§3): the folder chosen there is the working directory, whichever names it, and each start passes it so that no source stands over it (§6).

A value that no path can hold is as ADR-340 §6 has it: no folder is in use from it, its *in use* line says why, and it gives no row.

### 3. The list's first row, and the list with nothing in it

**The first row is the sources' folder, and only where a source names one.** It cannot be removed, as before. Its words are its name and, in place of *(this machine's folder)*, the source that named it, then ADR-393 §5's parts:

| Source | Words after the name |
|---|---|
| the system property `vespera.working-dir` | `(from the system property vespera.working-dir)` |
| an environment variable naming `vespera.working-dir` | `(from the environment variable <the variable read>)` |
| a line beside the jar, through the system property `db-dir` | `(from the system property db-dir)` |
| a line beside the jar, through an environment variable naming `db-dir` | `(from the environment variable <the variable read>)` |
| a line beside the jar, by its own text or its default | `(from <the file's name> beside the jar)` |

Where no source names a folder, the list's rows are the file's and the launch value's alone (ADR-393 §4).

**A list holding no row** shows its placeholder, `working-directories-empty`, a text reading, whole:

```
No working directory yet. Choose a folder with Browse, or name a new one with New….
```

**The row chosen as the window opens** is the one the launch value `vespera.working-dir` names, where it names a folder; else the sources' row, where there is one; else none (§6). **After Remove from list** takes off the row chosen, the sources' row is chosen where there is one, and none otherwise.

**With no row chosen**, every part of the window that reads the working directory reads none, as for a working directory no path can hold (ADR-331 §6): no status, preflight row, report, profile or holder line is read of a folder, and nothing is written for one.

### 4. The ending panel says no working directory is chosen

**While no row is chosen and no invocation runs, the ending panel says so**, in place of an ending: as the window opens, from the moment a row stops being chosen, and again as Run, Label or Publish is pressed and refused. It stands until a row is chosen; the panel then shows that row's last ending, or none, as ADR-417 §2 has it.

| Part | What it shows |
|---|---|
| `ending-when`, `ending-faults`, `ending-facts` | not shown |
| `ending-kind` | `No working directory`, in the error colour, the stripe too, both carrying `ending-bad` (ADR-417 §5) |
| `ending-line` | `No working directory is chosen. Choose one from the list, or press Choose a folder… to pick a folder.` |
| `ending-choose-folder` | a button, *Choose a folder…*, shown and laid out in this case alone, that does what Browse does (§5) |
| `ending-copy` | *Copy details*, as ADR-417 §4 has it: the kind and the line, a line each |
| `ending-open-profile`, `ending-open-report` | not shown |

**Run, Label and Publish are refused while no row is chosen**, in one line in `outcome`, starting nothing, storing no launch value and writing nothing, the first of the starts' refusals, before ADR-292 §5's `Run needs the corpus root` and ADR-331 §6's lines, `<Run>` as ADR-331 §6 names each start: `Run` for Run, `Label` for Save my labels and Auto Label, `Publish` for Publish:

```
<Run> is not started: no working directory is chosen
```

The panel's words are the window's, a fixed sentence: the one case where it says what to do, ADR-417 §3's rule standing for every ending of an invocation.

### 5. Browse is the picker

**Browse, `working-directory-browse`, and *Choose a folder…*, `ending-choose-folder`, open the same system folder dialog**, titled *Choose the working directory* as Browse's is, starting at the folder chosen where one is. **The folder chosen is added as the first row after the sources', chosen, and the list file is written at once**, as ADR-393 §4 writes it, a fourth time beside an invocation's start, a Save that made a folder and a removal. A folder already listed is chosen and moved to that place, listed once.

**Vespera makes no folder for it.** The system's dialog may make one with its own *New Folder*; Vespera itself makes a working directory only by a Save of `profile.yaml` (ADR-330 §7, ADR-393 §6). **An empty folder is a working directory like any**: a start passes it, and the jar writes into it.

**New…** with no row chosen proposes its folder under the user's home folder, `user.home`, where ADR-393 §6 takes the parent of the folder chosen.

### 6. Run, Label and Publish pass the row chosen

**Each start passes the row chosen as `--db-dir=<folder>`**, the sources' row among them, and its child inherits no variable naming the working directory (ADR-340 §6, ADR-345 §1). **It is given the same folder as the system property `vespera.working-dir`**, `-Dvespera.working-dir=<folder>`, absolute, after the two encoding flags and before `-jar`, so that a path on a line of a file beside the jar, which `--db-dir` does not stand over, does not stand over the row chosen either, and `WorkingDirectoryOption` finds the two agreeing.

**Run, Label and Publish act on the working directory's latest invocation, as before**: Run continues the runs its profile and build name (ADR-154), Label and Publish act on what the latest invocations left there, and a publish delivers what the last `vespera run` arrived at (ADR-393 §7). The window chooses a folder, never an invocation.

**The row chosen last is the one the launch values name.** The launch value `vespera.working-dir` is stored as each invocation starts (ADR-292 §2), the folder of the row chosen, or empty where the sources' row is chosen, as ADR-393 §4 has it. So the sources' row is chosen as the window opens only where the launch value is empty, or names no folder, and another row the launch value names is chosen though a source names a folder.

### 7. A `.vespera` folder of the current directory is listed once

**As the window opens, where `<ADR-292 §3's folder>/dot-vespera-looked-for` is not there**, the window looks at `.vespera` under its current directory. **Where that is a folder holding a file `vespera.db`**, it is added to the list file as ADR-393 §4 writes it, absolute and normalised, as the first row after the sources', listed once where it is listed already, and `outcome` reads, whole:

```
<folder> is added to the list of working directories: Vespera used it where nothing named a working directory, and no longer does so on its own
```

It is chosen where no other row is (§3, §6): where the launch value names no folder and no source names one.

**Then `dot-vespera-looked-for`**, an empty file, is written in that folder, whether or not a folder was added, only where the folder is the user's own (ADR-292 §3). From then on no window looks again: a folder removed from the list stays off it, and a `.vespera` that gains a ledger later is not added. Where the marker cannot be written, the window looks again at its next opening.

### 8. The seams

- **`WorkingDirectoryDefault.of(Path currentDirectory, Path besideTheJar, Map<String, String> systemProperties, Map<String, String> environment)`** returns `Optional<WorkingDirectoryDefault>`, §2's table, empty where no row names a folder; `source` is `system-property`, `environment` or `file`, and `DEFAULT` goes. The three-argument `of` is the same over a folder beside the jar holding the shipped copy alone, so a test names no file. `ControlPanel.main` gives the JVM's own and the folder beside the jar. **`ControlPanelState.useWorkingDirectoryDefault`** takes that `Optional`, and **`workingDirectoryDefault()`** returns it.
- **`ChildCommand`** puts `-Dvespera.working-dir=<folder>` on every child's command line, the folder `--db-dir` names (§6).
- **`pipeline.HelpAndVersion`**: `public static boolean asks(String... arguments)`, true for one argument alone among `--help`, `-h`, `--version` and `-V`; `public HelpAndVersion(PrintStream out)`; `public int call(String... arguments)`, printing on `out` and returning the exit code. `EntryPoint.start` asks it on the command line's arguments, `--headless` dropped, before the machine lock and the command line (§1).
- **`ControlPanelState.listTheDotVesperaFolderOnce(Path currentDirectory)`**: §7, given the current directory as `WorkingDirectoryDefault.of` is, `Optional<String>`, the line, empty where nothing was added; `ControlPanel.main` calls it with the JVM's own before the window opens, and the window shows the line in `outcome` as it opens.
- **`ControlPanelState.start`** refuses with §4's line where the working directory of the values it is to pass is blank.
- **`pipeline.WorkingDirectoryNamedCheck`**, an `ApplicationListener<ApplicationEnvironmentPreparedEvent>` and `Ordered`, its order after `EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER` and before `LoggingApplicationListener.DEFAULT_ORDER`, registered by `VesperaApplication.main`, throws **`pipeline.NoWorkingDirectoryNamedException`**, whose message is §1's line, where `vespera.working-dir` names no folder. `VesperaApplication.main` throws it on, as it does `BesideTheJarUnwritableException`, and **`EntryPoint`** says its message on standard error, once, and exits 1.
- **`ServeCommand`** gives §1's line of its own before the disagreement's.

### 9. No run id moves

`WorkingDirectoryNamedCheck`, `NoWorkingDirectoryNamedException`, `HelpAndVersion` and `ServeCommand` are in `pipeline`, which no stage's version names (ADR-222), and decide no verdict, cache key, cluster or text of the deliverable; `EntryPoint` and `VesperaApplication` are the root's; the rest is the control panel's and the shipped `application.yaml`. No stage moves.

## Alternatives rejected

- **Keeping `.vespera` under the current directory as the jar's default.** It opens a folder nobody named, wherever the command happens to be started, and the window's row of it reads as a choice the operator made.
- **Shipping the line empty, `working-dir:`.** `--db-dir=<path>` reaches the working directory only through the line's `db-dir`; an empty line would leave every start of the window's, and every operator's `--db-dir`, naming nothing.
- **Refusing in `WorkingDirectoryPreparer`.** It hears the environment after Logback is set up, which by then has opened a log under the root of a drive.
- **Refusing `run`, `label` and `publish` alone in the jar.** Every other start through Spring builds the same datasource and log, at a path nobody named.
- **Refusing `--help` and `--version` where no working directory is named.** Reading how to name one would need one named first.
- **The window reading ADR-340 §6's four rows and no file.** A path on the line beside the jar is the jar's working directory; the window would show and pass another, and the start be refused as disagreeing.
- **`--db-dir` alone on the child's command line.** It stands under a path on the line of a file beside the jar, so a row chosen other than that path would be refused.
- **Showing the sources' row worded *none set* in the error colour.** A row that names no folder cannot be chosen or passed; the ending panel says it once, with what to press.
- **A new panel for the case.** The ending panel stands beneath the output already and says why nothing ran; a second place would say the same.
- **Browse making the folder chosen, or New… beside it in the ending panel.** The system's dialog makes a folder on the operator's press; Vespera's one maker of a working directory stays the Save of `profile.yaml`.
- **The sources' folder always chosen as the window opens.** A working directory the operator chose and started stays chosen though the machine exports another.
- **Adding `.vespera` at every opening where it holds a ledger.** A folder the operator took off the list would come back.

## Consequences

- **A command given no working directory starts nothing**, and its line says what names one.
- **An operator's own `application.yaml` keeps `.vespera` in use for the jar, and the window shows it as the first row, `(from application.yaml beside the jar)`**, until its line is changed; the `.new` beside it shows the shipped line and says why.
- **`--help` and `--version` answer anywhere**, whatever names a working directory or none.
- **The window opens with no working directory chosen** where nothing names one and nothing was started before, and its ending panel says so until a row is chosen.
- **A working directory used at `.vespera` before is found once**, listed with a line, and chosen where nothing else is.
- **`CONTEXT.md`'s Working directory entry** no longer says *`.vespera` under the directory the command is started from when neither names one*, and its Control panel entry no longer says a working directory none of the sources names stands for the folder the jar would use; both say none is used.
- **The README's** paragraphs on the working directory, the Folders section and Browse, and `docs/running-stage-by-stage.md` where it names `.vespera`, say a working directory must be named and how.
- **Every integration test that builds the application** names a working directory, as every invocation test already does through `vespera.working-dir`.
- ADR-340, ADR-393, ADR-345, ADR-331, ADR-417, ADR-054, ADR-333, ADR-341, ADR-292 and ADR-397 carry a note at their heads pointing here.

## What this does not decide

- **Choosing an earlier invocation of the working directory chosen**, for Run, Label or Publish: a decision of its own. Until it is made, each acts on the latest, as §6 has it.
- **Handing the command line given no argument over before Spring** too, as a start with no display has it: it prints the usage through Spring, and so is refused where no working directory is named (§1).
- **A `spring.config.location` or `spring.config.additional-location` naming other files**, which the jar reads in place of, or beside, the two beside it (ADR-397 §4) and the window does not; the child's system property (§6) still stands over them.
- **A placeholder of another name than `db-dir` and `user.dir`** read from an environment variable under one of Spring's relaxed spellings, which the window reads by the name as written alone (§2).

## Tests

| Method | What it holds |
|---|---|
| `pipeline.NoWorkingDirectoryIsNamedByTheJarTest.theShippedLineNamesNoFolderWhereNothingSetsDbDir` | §1, §2: the shipped `application.yaml` read alone resolves `vespera.working-dir` to the empty string; with `db-dir` set to a folder, to that folder; with `vespera.working-dir` set too, to its own |
| `pipeline.NoWorkingDirectoryIsNamedByTheJarTest.theShippedCommentSaysTheDotVesperaFolderIsNoLongerUsed` | §1: the comment above `working-dir:` holds §1's sentence whole |
| `pipeline.NoWorkingDirectoryIsNamedByTheJarTest.aStartNamingNoFolderIsRefusedBeforeTheLogIsSetUp` | §1, §8: the check refuses a working directory not set, empty or white space with the exception whose message is §1's line, and lets a folder through making nothing; its order lies after the configuration files' and before Logback's |
| `pipeline.NoWorkingDirectoryIsNamedByTheJarTest.theApplicationRegistersTheCheck` | §8: `VesperaApplication.main` registers `WorkingDirectoryNamedCheck` |
| `EntryPointTest.aStartNamingNoWorkingDirectorySaysItsLineOnStandardErrorAndExitsOne` | §1, §8: the refusal thrown from the command line is said once on standard error, whole, the exit 1, nothing escaping |
| `pipeline.HelpAndVersionTest.helpAndVersionAloneAreAskedAndNothingElse` | §1, §8: `--help`, `-h`, `--version` and `-V` alone are asked; with another argument, `run`, `serve` or none, not |
| `pipeline.HelpAndVersionTest.eachPrintsWhatPicocliPrintsOverTheCommandsModelAndExitsZero` | §1: `--help`'s and `-V`'s output, whole, picocli's usage and version help over `VesperaCommand`'s model built with no application, exit 0 |
| `EntryPointTest.helpAndVersionAreAnsweredBeforeSpringAndTheMachineLock` | §1, §8: `--help`, `--headless --version` and `-h` exit 0 with neither the machine lock asked nor the command line run; `run` with a root still reaches the command line |
| `ui.WorkingDirectoryDefaultTest.aPathOnTheLineBesideTheJarIsReadAfterThePropertyAndTheVariableAndOverDbDir` | §2: a path on `application.yaml`'s line, with nothing set, is the folder, from the file; `VESPERA_WORKING_DIR` stands over it; `DB_DIR` does not; `application-local.yaml`'s path stands over `application.yaml`'s |
| `ui.WorkingDirectoryDefaultTest.aPlaceholderOnTheLineIsResolvedAsSpringResolvesIt` | §2: `${db-dir:${user.dir}/.vespera}` with nothing set gives `.vespera` of the current directory, from the file, and with `DB_DIR` set that folder, from the variable; `${db-dir:}` with nothing set gives none; a file setting no key gives none though `DB_DIR` is set |
| `ui.ChildCommandTest.everyChildIsGivenTheFolderItPassesAsTheSystemPropertyToo` | §6: run's, label's and publish's command lines each carry `-Dvespera.working-dir=<folder>` once, after the encoding flags and before `-jar`, the folder `--db-dir` names |
| `ui.NoWorkingDirectoryIsChosenTest.aFolderTheFileBesideTheJarNamesIsTheFirstRowWordedByThatFile` | §2, §3: a path on `application.yaml`'s line beside the jar is the first row, its words naming the file |
| `pipeline.ServeCommandTest.nothingNamingAWorkingDirectoryIsRefusedInOneLineAndNothingIsMade` | §1: nothing set, and a blank `--db-dir=`, each exit 1 with serve's line alone on standard error, nothing on standard output, and no `.vespera` under `user.dir` |
| `ui.WorkingDirectoryDefaultTest.nothingSetNamesNoFolderThoughTheCurrentDirectoryHoldsADotVespera` | §2: no source, or blank ones, give none, a `.vespera` folder with a ledger under the current directory notwithstanding |
| `ui.NoWorkingDirectoryIsChosenTest.withNothingNamedOrListedTheListPointsToBrowseAndTheEndingPanelSaysNoneIsChosen` | §3, §4: no row, the placeholder's id and words; the ending panel's kind, line, colour class and buttons |
| `ui.NoWorkingDirectoryIsChosenTest.eachStartIsRefusedInOneLineWhileNoneIsChosen` | §4: Run, Label, Auto Label and Publish refused at the state in their lines, no child and no launch values file; Run pressed in the window, its line in `outcome` and the ending panel still saying none is chosen |
| `ui.NoWorkingDirectoryIsChosenTest.chooseAFolderOpensBrowsesDialogAndTheFolderIsListedChosenAndKept` | §4, §5: the button opens the dialog with Browse's title; the folder is the one row, chosen, in the list file; the ending panel says no more that none is chosen |
| `ui.NoWorkingDirectoryIsChosenTest.browseListsTheFolderChosenFirstChoosesItAndWritesTheListFileAtOnce` | §5: a folder listed and the sources' row there, Browse puts the folder chosen after the sources' row, chosen, and the file holds it first, with no invocation started |
| `ui.NoWorkingDirectoryIsChosenTest.theFirstRowIsTheSourcesFolderOnlyWhereASourceNamesOneAndSaysWhichSource` | §2, §3: a variable naming the working directory gives the first row, its words naming the variable; with none, the first row is the file's |
| `ui.NoWorkingDirectoryIsChosenTest.theSourcesFolderIsChosenOnlyWhereTheLaunchValuesNameNoOtherRow` | §3, §6: the launch value naming a listed folder, that row is chosen though a source names another; empty, the sources' row |
| `ui.NoWorkingDirectoryIsChosenTest.aDotVesperaFolderHoldingALedgerIsListedOnceWithALineAndChosenWhereNothingElseIs` | §7: listed in the file, chosen, `outcome` its line whole, the marker written; removed and the window opened again, not listed again and no line |
| `ui.NoWorkingDirectoryIsChosenTest.aDotVesperaFolderHoldingNoLedgerIsNotListedAndNotLookedForAgain` | §7: no `vespera.db`, nothing listed and no line, the marker written; the ledger written later and the window opened again, nothing listed |

Amended:

- **`ui.WorkingDirectoryDefaultTest`**'s helper reads the folder resolved out of the `Optional` (§8).
- **`ui.TheWorkingDirectoryIsChosenFromAListTest.theFirstRowIsTheMachinesFolder`** reads the sources' folder out of the `Optional`; its state names the folder by a system property, as before, so its rows and choice stand.
- **`ui.AnsweringDialogs`** keeps each folder dialog's title and answers it with a folder the test sets, none where it sets none.
- **`ui.ChildCommandTest`**'s command line before the command carries `-Dvespera.working-dir=<the working directory>` after the two encoding flags (§6), so `runIsTheJarsRunCommandWithBothEncodingFlagsAndAbsolutePaths` and the tests that read the line by its place stand.

## What the implementation owes

Beside turning the tests above green: the shipped `application.yaml`'s line and comment (§1); `WorkingDirectoryNamedCheck` and `NoWorkingDirectoryNamedException`, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, `VesperaApplication.main`'s registration and rethrow, and `EntryPoint`'s line (§1, §8); `HelpAndVersion`, listed there as holding no rule, and `EntryPoint`'s hand-over to it (§1); `ServeCommand`'s reading and line, and its javadoc (§1); `WorkingDirectoryDefault`'s reading of the two files beside the jar, `ChildCommand`'s system property, `ControlPanel.main`'s call with the folder beside the jar, `ControlPanelState`, `WorkingDirectories`, `Cockpit`'s list, its placeholder and rows' words, Browse's write, New…'s parent, `EndingPanel`'s case and button, and the call in `ControlPanel.main` (§2 to §7); a working directory named by `VesperaApplicationIT`, `DoclingClientIT`, `OllamaClientIT` and `ClusterSynthesisIT`, which name none; `CONTEXT.md`'s two entries, the README and `docs/running-stage-by-stage.md` (Consequences); and every citation in the code of ADR-340 §6's fifth row or ADR-393 §4's *machine's folder* naming this record.
