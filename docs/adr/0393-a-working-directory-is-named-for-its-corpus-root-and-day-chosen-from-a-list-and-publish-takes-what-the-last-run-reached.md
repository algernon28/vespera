# ADR-393 — A working directory is named for its corpus root and day, chosen from a list, and publish takes what the last run reached

- **Date**: 2026-11-14
- **Status**: accepted on 2026-11-14.
- **Decided**: by the operator on 2026-11-14: that the readable name belongs to the working directory and is its folder's name, with no new term (§1); its form, `<corpus root's folder name>_<yyyyMMdd>`, the local date, `-2` and on for a second the same day, stored as the folder's name alone and renamed by renaming the folder (§2); that the corpus root proposes the name once, as the folder is added, and a working directory named for another root is warned of and not refused (§3); that the choice of what to resume is made in the control panel alone, from a list kept beside the launch values, each row read from that folder's report, the folders not chosen kept, each resumed under its own `profile.yaml` (§4 to §6); that a publish delivers what the working directory's last `vespera run` arrived at, and a finished rendering alone (§7). What a section adds beneath its ruling is what building it needs.
- **Amends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s *"never derived from the root"*: the control panel proposes a new working directory's folder name from the corpus root, once, as it adds one (§2); and its Consequences' *"No guard against a working directory pointed at the wrong root"*: the window warns, and does not refuse, where Run passes a working directory named for a corpus root of another name (§3). Every invocation still names its working directory. It carries a note at its head.
- **Amends**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §3, whose folder gains a third file, `working-directories`, the list of §4, which is no launch value; §1's seven launch values stand, the row chosen being `vespera.working-dir`'s. It carries a note at its head.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §6's *"the last generation run whose generation step is recorded finished"*: each destination's rendering is that of the generation run the working directory's last `vespera run` arrived at, and the last run minted only where no report of a `vespera run` is kept (§7). §11's check of `drawing.txt` stands. It carries a note at its head.
- **Amends**: [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md) §6, whose working directory left empty is the first row of §4's list, chosen where the launch value is empty; its table, its words and the eight variables withheld from the child stand. It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose Folders section's field `vespera.working-dir` gives way to §4's list; and §3, whose working directory starts from the launch values file as before, and whose list starts from §4's file. It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §1, whose field left empty stands for `WorkingDirectoryDefault`'s folder: that folder is the list's first row (§4). It carries a note at its head.
- **Keeps**: [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md): a working directory chosen is resumed as any is, under its own `profile.yaml`, a walk and a run continued or minted as those records have it; the operator chooses a folder, never a run.
- **Keeps**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3 and §4: the report gains no field and this record raises no `format`; `places.workingDirectory` names the folder, and so its name; the invocation's id, `-2` and on, is the rule §2 borrows.
- **Keeps**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §7: only a save of `profile.yaml` makes a working directory (§6). [ADR-047](0047-the-pipeline-never-blocks.md) and the README's commands: no command and no option is added. [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3: the marker names the working directory's path (Known limits). [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the window reads the reports with its own reader, linking no class of the jar. [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §4: Publish's boxes are offered as there.
- **Settles**: [#778](https://github.com/algernon28/vespera/issues/778).

## Context

**The operator asks for a readable name for each run of a corpus, and to choose which one to resume or publish.** In `CONTEXT.md` a run is one stage's, under one configuration, its id a hash; each stage has its own. An invocation has a dated id already, `yyyyMMdd'T'HHmmss'Z'` (ADR-362 §3). What the operator starts over one corpus and keeps, its database, its profile, its reports and its deliverable, is one working directory, and nothing names one but the path the operator gives it (ADR-054).

**Today the window has one field for the working directory** (ADR-344 §1), empty standing for the folder the machine names (ADR-340 §6), and nothing proposes a name for a new one. What to resume is whatever the folder named holds: its database read under its own `profile.yaml` (ADR-154).

**A publish takes the last generation run minted.** `VesperaCommand.Publish.lastDrawn` walks the working directory's runs from the last row of `run` back, and takes the first generation run whose step is finished and whose tree holds the plugin's list or `drawing.txt` (ADR-333 §6, §11). That is not always the run the working directory's last `vespera run` arrived at:

- **a value put back.** A floor loosened mints a run of the stages it reaches; the stricter value put back continues the earlier runs (ADR-156), minting none. The last `vespera run` arrived at the earlier generation run, and the publish takes the later one, of the value taken back;
- **two corpus roots.** A working directory given a second root walks it and mints its runs. Given the first root again, the walk and its runs are continued (ADR-115), and the publish takes the second root's rendering.

## Decision

### 1. The readable name is the working directory's folder name

**The name belongs to the working directory, and is its folder's name, the last element of its path. No term is added: the Working directory entry of `CONTEXT.md` gains that its name is its folder's name.** A run's id, a walk's and an invocation's are unchanged, and none moves.

The name is stored nowhere but in the folder's name: no table holds it, and the report gains no field, `places.workingDirectory` naming the folder (ADR-362 §3). Nothing locates a working directory by its name: every invocation names its working directory as ADR-054 has it, by `--db-dir` or `vespera.working-dir`, or the machine's default.

### 2. The name the window proposes

**`<the corpus root's folder name>_<yyyyMMdd>`, the date the local one of the window's clock; where that name is taken the same day, `-2`, then `-3` and on, as ADR-362 §3 numbers a second invocation in the same second.**

- **The corpus root's folder name** is the last element of the path the corpus root's field names, as typed, links not followed. Where the field is empty, or names a path with no last element, a drive's or the file system's root, the name is the date alone, `<yyyyMMdd>`, numbered the same way.
- **The date** is `yyyyMMdd` of the instant New… is pressed, in the window's time zone, `ZoneId.systemDefault()`: a folder added at 00:30 in Rome on 14 November 2026 is `_20261114`, though it is 13 November in UTC.
- **Taken** is an entry of that name in the folder it is proposed in, a folder, a file or a link, by `Files.notExists(path, NOFOLLOW_LINKS)` being false; the first number from 2 whose name is free is taken.
- **Renaming.** The operator renames a working directory by renaming its folder, while no process holds its `vespera.lock` (ADR-278 §4). Nothing the jar reads at its next start names the folder's own path but a named deliverable root's marker (Known limits).

The class, `WorkingDirectoryName`, of the control panel, in `java.base` alone (ADR-285 §3):

```java
public final class WorkingDirectoryName {
    public static Path proposed(Path parent, Optional<Path> corpusRoot, Instant now, ZoneId zone);
                                  // parent.resolve(the name above), the first free from -2 where it is taken
    public static Optional<String> warningToRun(Path workingDirectory, Path corpusRoot);   // §3
}
```

### 3. A working directory named for another corpus root is warned of, never refused

**The corpus root proposes the folder's name once, as New… adds it (§6); every invocation still names its working directory, and the jar compares no name.** Where the folder Run passes has a name of §2's form, `<X>_<yyyyMMdd>` or `<X>_<yyyyMMdd>-<n>`, read as `^(.+)_([0-9]{8})(-[0-9]+)?$`, and `X` is not the corpus root's folder name, case ignored, Run adds one warning line to `outcome`, after `LaunchValues.warningsToRun()`'s, and starts:

```
the working directory <the folder's name> is named for a corpus root named <X>, and Run starts it on <the corpus root>
```

`<the corpus root>` as the field names it. A folder whose name is not of that form, the date alone among them, is warned of by nothing. The jar adds no line: started by hand, as ADR-054's Consequences have it, a working directory and a root are what the operator names.

### 4. The window lists the working directories this user has used

**An editable list, `working-directories`, takes the place of the Folders section's field `vespera.working-dir`, holding the working directories this user has used, in a file kept beside the launch values. The row chosen is the launch value `vespera.working-dir` (ADR-292 §1), and every part of the window reads it as it read the field: its status, its preflight row, its reports, its profile, its holder line.**

- **The first row is the machine's folder**, ADR-340 §6's, resolved by `WorkingDirectoryDefault` as the window opens, its *in use* line as ADR-340 §6 words it. Chosen, the launch value is kept empty, as the field left empty was, and Run, Label and Publish pass the folder as ADR-345 §1 has them.
- **The rows after it are the file's**, in the order the file holds them (each window puts the folder it started last on top, §4), a folder named by the launch values file and not by the list added second. A folder named twice is listed once, compared as `ControlPanelState.namesTheSameFolder` compares.
- **New…**, `working-directory-new`, adds a row (§6); **Browse**, `working-directory-browse`, opens the system's folder dialog and adds the folder chosen as a row, and chooses it; **Remove from list**, `working-directory-remove`, takes the chosen row off the list, the first row excepted, and deletes nothing.
- **The folders not chosen are kept as they are**: nothing deletes, moves or archives a working directory.
- **While the window's own child runs the list cannot be changed**, as Run cannot be pressed: the child works in the folder it was started in.

**The file** is `<java.io.tmpdir>/vespera-control-panel-<user>/working-directories`, in ADR-292 §3's folder: UTF-8, one absolute, normalised path a line, each ending in `\n`, this window's folder started last on top, then the others in the file's order (below), the machine's folder not among them. It is written whole to a temporary file in its folder moved over it, as ADR-292 §2 writes the launch values, and only where the folder is the user's own (ADR-292 §3): as an invocation starts, the folder it starts in moved to the top; after a Save of `profile.yaml` that made a folder (§6); and as a row is removed. **Another window may write the same file**: at each write the window reads the file again, keeps every row another window wrote that this one has not removed, and writes this window's rows in its order, a folder just started on top, then those others in the file's order. A row New… added is written once its folder exists. A blank line, or one naming no absolute path, is passed over as the file is read. Where the file cannot be read, the list holds the machine's folder and the launch value's, and one line on the window's standard error says so:

```
the control panel could not read its list of working directories at <file>, and lists only the folders in use: <reason>
```

### 5. What a row shows

**Each row shows the folder's name, its corpus root, its last invocation's date and outcome, the furthest leg its last `vespera run` reached and the next value: read from that folder's `report.json` and the copies in its `logs`, without opening its database or taking its lock.** A row is read as the window opens, as it is added, and as the window's child ends.

`WorkingDirectoryRow`, of the control panel:

```java
public record WorkingDirectoryRow(String name, String corpusRoot, String lastInvocation, String legReached, String next) {
    public static WorkingDirectoryRow read(Path folder, ZoneId zone);   // never throws
}
```

| Part | Read from | Words |
| --- | --- | --- |
| `name` | the folder | its last element |
| `corpusRoot` | the last run's report (§7), `places.corpusRoot` | the path as the report holds it; `no run yet` where there is no such report |
| `lastInvocation` | `report.json`, `invocation` | `<command> on <yyyy-MM-dd>, <outcome>`, the date that of `started` in `zone`, `outcome` as the report words it and `not ended` where it is `null`; `nothing yet` where there is no `report.json`; `its report cannot be read` where it cannot be read or is of a format the window does not know |
| `legReached` | the last run's report, `legs` | `<name>, <state>` of the last leg, in the report's order, whose state is not `not started`; `none` where every leg is, or there is no such report |
| `next` | `report.json`, `next` | the keys `wants` holds, joined by `, `, where it holds any; else `answers to record` where `answer` is true; else `run` where `invocation` is `run`; else `nothing` |

**A row whose folder is held by another process** reads, in place of its last invocation, `in use by another process since <yyyy-MM-dd HH:mm>`, the holder line's `started` in `zone`, the line held as `ControlPanelState`'s holder of the working directory reads it, the control panel's own process holding nothing. Chosen, it is watched as ADR-362 §8 has the window watch an invocation it did not start, and Run is disabled as it is today. **A row whose folder is not there** reads its name and `not there` as its last invocation, the other parts as no report gives them.

### 6. New…

**New… proposes §2's name and adds the folder as a row, chosen; the first Save of `profile.yaml` makes it (ADR-330 §7), and nothing else does.** Pressed, it shows one field holding `<parent>/<the name proposed>`, absolute, which the operator may edit, with Browse for the parent, an OK and a Cancel; the parent is the parent of the folder chosen as New… is pressed. OK adds the field's folder as the first row after the machine's and chooses it; its status reads *to be created*, and Run, Label and Publish are refused as they are for a folder not there (ADR-340 §6), until a Save of the Profile window makes it.

### 7. A publish takes the generation run the last `vespera run` arrived at

**Each destination's rendering is that of the generation run the working directory's last `vespera run` arrived at, as its report's `arrivedAt` names it; a finished one alone is delivered.**

- **The last run's report** is, of the copies `logs/report-<id>.json` (ADR-362 §4), the one of the greatest id whose `invocation.command` is `run`: ids are ordered by their sixteen characters `yyyyMMdd'T'HHmmss'Z'`, then by the number after their `-`, an id with none being the first of its second. A copy that cannot be read, is not JSON or is of a `format` the reader does not know is passed over. The reader reads `format`, `invocation.id`, `invocation.command` and `arrivedAt`, the window `places.corpusRoot` and `legs` besides, and ignores the rest. The jar and the window each have their own reader of it (ADR-285 §3).
- **The run** is the one `arrivedAt` names under the stage `generation`. Where it names none, or that run's generation step is not recorded finished, the destination is refused, its publisher not asked and no site or folder touched, with one line on standard error, and the invocation exits 1 as ADR-333 §6 and ADR-383 have it:

  ```
  vespera publish sent nothing to <name>: the last vespera run did not reach the deliverable. Run vespera run to the deliverable, and publish again.
  ```

  and a bare publish, as ADR-308 §5 words its own: `vespera publish sent nothing: the last vespera run did not reach the deliverable. Run vespera run to the deliverable, and publish again.`
- **Where that run's tree holds neither the plugin's list nor its `drawing.txt`**, ADR-333 §6's refusal stands, naming no rendering drawn; where `drawing.txt` does not say `drawn`, ADR-333 §11's.
- **Where no copy of a `vespera run`'s report is kept**, as in a working directory whose runs were all made before ADR-362's copies or whose `logs` is gone, the run is the last minted, as ADR-333 §6 has it.

**The window's choice of what to publish is the working directory chosen** (§4): Publish passes its folder as it does today.

### 8. Run ids

**No run id moves.** `VesperaCommand.Publish` and the reader §7 adds lie in `pipeline`, which no stage's version names (ADR-222); the rest is the control panel's.

## Alternatives rejected

- **A term of its own for the whole job over one corpus, every stage's runs from the census to the generation.** It is what ADR-154 §1 gives the working directory's database and present profile, and a name chosen to resume is ADR-154's rejected gate naming a run to continue from.
- **Naming the invocation, or a stage's run.** An invocation's id is dated already; a run's id and a stage's name are not shown to the operator (ADR-098, ADR-154).
- **The name in a column of the database, or a field of the report.** A second copy of a name the folder holds, which a rename would leave behind.
- **A time, `_HHmm`, to tell two of the same day apart.** `-2` is the rule the report's id already follows, and the name stays short.
- **`vespera run --resume <name>`.** The command line names its working directory already; a name would need a lookup from name to folder, which ADR-054 refused.
- **Archiving or deleting the working directories not chosen.** Each holds work the operator may come back to; disk is the operator's to free.
- **The jar refusing a working directory named for another root.** The operator may rename either, or mean it; a warning tells and stops nothing.
- **Keeping the last generation run minted for the publish.** It sends a rendering the last `vespera run` did not arrive at, as Context shows.

## Consequences

- **A new working directory reads as its corpus root and its day**, `Letters_20261114`, and the window lists every one this user has used, each with where it stands.
- **A publish delivers what the last `vespera run` arrived at**, so a value put back, or a second root walked into the same working directory, is published as the last run left it.
- **A publish after a `vespera run` that stopped at a gate before the generation is refused**, though an earlier rendering is drawn: it is not what the last run arrived at.
- **ADR-054, ADR-292, ADR-333, ADR-340, ADR-344 and ADR-345 carry a note at their heads pointing here.**

## Known limits

- **A renamed working directory and a named deliverable root.** The root's marker names the working directory's old path (ADR-276 §3), so the next `vespera run` refuses that root as holding no tree of this working directory, until an empty one is named; the unnamed root under `deliverable/` is not affected. Older copies in `logs` name the old path in `places`, which no reader of §5 or §7 compares.
- **The window may offer a destination the publish then refuses**: ADR-345 §4 offers a destination drawn in any run's tree, and §7 refuses one the last `vespera run` did not arrive at.
- **The list lives in the temp directory**, as the launch values do: a system that clears it leaves the list holding the machine's folder alone, and the folders are where they were.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.ANewWorkingDirectoryIsNamedForItsCorpusRootAndDayTest.theNameIsTheCorpusRootsFolderNameAndTheLocalDate` | §2: the root's last element, `_` and the date in the zone given, a day ahead of UTC's |
| `ui.ANewWorkingDirectoryIsNamedForItsCorpusRootAndDayTest.aNameTakenTheSameDayTakesTheFirstFreeNumber` | §2: with the name and its `-2` taken, by a folder and a file, `-3` |
| `ui.ANewWorkingDirectoryIsNamedForItsCorpusRootAndDayTest.aCorpusRootWithNoFolderNameGivesTheDateAlone` | §2: no corpus root, or a file system's root, gives `<yyyyMMdd>` |
| `ui.AWorkingDirectoryNamedForAnotherCorpusRootIsWarnedOfTest.aWorkingDirectoryNamedForAnotherRootIsWarnedOf` | §3: the warning, whole, for a name with and without its number |
| `ui.AWorkingDirectoryNamedForAnotherCorpusRootIsWarnedOfTest.aWorkingDirectoryNamedForItsOwnRootOrForNoneIsNot` | §3: no warning for its own root in another case, the date alone or a name of no such form |
| `ui.WorkingDirectoriesTest.aRowAnotherWindowStartedIsKeptAndARowRemovedStaysOff` | §4: of two windows over one file, one starts a folder and the other, opened before, removes a row: the file holds the folder started and not the row removed, the remover's rows first, whole |
| `ui.EachWorkingDirectoryIsARowOfWhatItsReportSaysTest.aRowShowsItsNameRootLastInvocationLegAndNextValue` | §5: every part of a row, whole, the root and the leg from the last run's copy and the rest from `report.json` |
| `ui.EachWorkingDirectoryIsARowOfWhatItsReportSaysTest.aFolderNoInvocationWroteInSaysSo` | §5: the words of a folder with no report |
| `ui.EachWorkingDirectoryIsARowOfWhatItsReportSaysTest.aFolderAnotherProcessHoldsSaysSinceWhen` | §5: the holder's words in place of the last invocation |
| `pipeline.PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest.theRunPutBackIsPublishedThoughAnotherWasMintedAfterIt` | §7: the run the last `run` copy names is asked, not the last minted, a later publish's copy passed over |
| `pipeline.PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest.copiesOfOneSecondAreOrderedByTheirNumber` | §7: `-10` comes after `-2` |
| `pipeline.PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest.aLastRunThatReachedNoDeliverableSendsNothing` | §7: the refusal, whole, exit 1, the publisher not asked |
| `pipeline.PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest.withNoRunsReportKeptTheLastMintedIsPublished` | §7: no copy of a `run`'s report, the last minted, as before |

## What the implementation owes

Beside turning the tests above green: `WorkingDirectoryName` and `WorkingDirectoryRow` (§2, §3, §5); the list, its three buttons, New…'s field and the file of §4 and §6 in the window, Run's warning (§3); the reader of the last run's report in `pipeline`, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, and `VesperaCommand.Publish`'s choice of run (§7); the README's Folders, Where each field starts and publish paragraphs; the notes at the heads of the six records amended.

## What this does not decide

- **A bound on the list's length.**
- **Whether ADR-345 §4's boxes offer only what §7 would deliver** (Known limits).
- **Moving a working directory to another parent from the window**: it is done in the system, as a rename is.
