# ADR-439 — A publish may name an earlier `vespera run` by its invocation, and the control panel lists the earlier deliverables to choose one from

- **Date**: 2026-11-21
- **Status**: accepted on 2026-11-21.
- **Decided**: under the operator's delegation, on [#923](https://github.com/algernon28/vespera/issues/923), on 2026-11-21: that what the operator chooses is a generation run that a kept `vespera run` reached, one row each, shown by the date of the invocation that last reached it and never by a run id, with its corpus root, its output folder and the destinations drawn there, and no count of topics or clusters (§6); that the jar is told by `vespera publish --invocation <id>`, the invocation's dated id, the generation run being the one that invocation's report names (§1); that its tree is looked for at the output folder that invocation's report names, not at the one named now (§3); that a publish naming an invocation is refused where its report is not kept, where it was no `vespera run`, where it did not reach the deliverable, where no tree is at the place it drew one, and where the tree there was drawn again for another generation run or working directory, the last two in lines of their own (§2, §3); that a publish of an earlier deliverable to Confluence or the website replaces what is published there, as any publish does, the jar asking nothing and the window asking once before it starts one (§4, §7); that the list stands in the Working directory section beneath the list of working directories, re-read as that list's rows are (§6); that nothing is chosen as it is read, so Publish keeps delivering what the last `vespera run` reached until a row is chosen, and that Publish's boxes offer what the chosen row's tree, or else the latest row's, has drawn (§7); that only a deliverable whose tree still stands, holding a destination's rendering, is listed, the one the last `vespera run` reached marked as the latest (§6); and the words and names of §1, §2, §6 and §7. Decided within those by this record: the form of an invocation's id the option takes, and its refusal's exit code (§1); the order of the refusals and that each is said once, before any destination (§2); where the tree is looked for when the report names no output folder (§3); that the latest row passes `--invocation` too and asks nothing (§7); that `--to` stays the last of the child's arguments before `--db-dir` (§7); and that the confirmation is asked before the API token (§7).
- **Amends**: [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §7, whose rendering is the one the last `vespera run` reached: so it is still, where no invocation is named, and a publish naming one delivers the rendering of the generation run that invocation reached (§1 to §5); its Keeps line's *"the operator chooses a folder, never a run"*: for a publish, the operator may choose a generation run, by the invocation that reached it (§6, §7); and its Known limits' *"The window may offer a destination the publish then refuses"*, which no longer holds where a `vespera run`'s report is kept (§7). It carries a note at its head.
- **Amends**: [ADR-436](0436-the-jar-and-the-control-panel-use-no-working-directory-nothing-names-and-browse-chooses-one.md) §6's *"The window chooses a folder, never an invocation"* and its Keeps line's *"the window chooses a folder, never an invocation"*: Publish may be given an earlier invocation chosen from §6's list; Run and Label still act on the working directory's latest invocation. Its Keeps line's [ADR-047](0047-the-pipeline-never-blocks.md) *"no command and no option is added"* held of that record; `publish` gains `--invocation` here. It carries a note at its head.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §6, whose `--to` is no longer `publish`'s one option beyond `--db-dir`, and whose *"Each finds its own rendering"* is, where `--invocation` is given, the rendering in the tree of the generation run that invocation reached (§1, §3, §4). It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §4, whose boxes are offered for each destination drawn in any run's tree: they are offered for each destination drawn in the chosen row's tree, or else in the latest row's, and as before only where no `vespera run`'s report is kept; and whose Publish starts `publish --invocation <id> --to <destinations>` where a row is chosen (§7). It carries a note at its head.
- **Amends**: [ADR-047](0047-the-pipeline-never-blocks.md) as ADR-333 amended it: `publish` takes two options beyond `--db-dir`, `--to` and `--invocation` (§1). A pipeline that never blocks stands: the jar asks nothing before an earlier deliverable is published (§4). It carries a note at its head.
- **Keeps**: [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md) §4 and §5 and ADR-378: one tree for each deliverable language, updated in place, a page the rendering no longer has moved to the trash, whichever generation run's rendering is published. [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §7 and §11: one website folder refreshed by digest, and a rendering whose `drawing.txt` does not say `drawn` refused. [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3 and [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md): a named root holds one generation run's tree, the unnamed root one folder for each, and nothing beneath a root is deleted. [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3 and §4: the report gains no field, and a copy in `logs` is never deleted or rewritten. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the window reads the copies and the trees' files with its own reader. [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §5: a working directory's row is unchanged. [ADR-429](0429-every-tree-holds-the-ledger-of-the-runs-it-was-drawn-from-in-one-sqlite-file-written-at-each-drawing.md) §5: nothing reads a tree ledger back. [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) and [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md): no run id is shown to the operator, and which runs a stage reads is unchanged.
- **Settles**: [#923](https://github.com/algernon28/vespera/issues/923).

## Context

**What a publish delivers today.** `VesperaCommand.Publish` reads, of the copies `logs/report-<id>.json`, the newest whose `invocation.command` is `run` (`LastRunInvocationReader`), takes the generation run its `arrivedAt` names, checks in the working store that its generation step is finished, and finds each destination's rendering in that run's tree at the deliverable root named now, `DeliverableRoot.treeOf` (ADR-393 §7, ADR-333 §6, §11). No option names another run. The control panel's Publish starts `publish --to <destinations>` (ADR-345 §4), its boxes offered for a destination drawn in any run's tree, and its Working directory section lists folders, each row a summary of its last invocation (ADR-393 §5).

**What stays on disk.** A copy in `logs` is never deleted or rewritten (ADR-362 §4), and it names, in `places.deliverableRoot`, the folder the invocation drew its tree in, where its generation step finished: the root named, or `<working directory>/deliverable` where none was (`InvocationReport.deliverableRootDrawn`). The unnamed root keeps one folder for each generation run, `deliverable/<generation run>/`, and nothing deletes one (ADR-103). A named root holds one generation run's tree, its marker `vespera-deliverable.txt` naming the working directory and the generation run; a new generation run needs a new, empty root (ADR-276 §3). A tree is drawn again in place whenever an invocation reaches its generation run (ADR-274 §6). A copy holds no count of topics or clusters, and nothing reads a tree ledger back (ADR-429 §5).

**What a publish replaces.** Confluence holds one tree for each deliverable language, updated in place, a page the rendering no longer has moved to the trash (ADR-328, ADR-378). The website is one folder, refreshed by digest, a file the previous publish listed and this one does not removed (ADR-333 §7). `retrieval` sends nothing (ADR-434 §8). The page list names its generation run, and the publish refuses one of another run or another format (`PageList.in`, ADR-378 §4).

## Decision

### 1. `vespera publish --invocation <id>`

**`--invocation <id>` names an invocation of this working directory by its id, `yyyyMMdd'T'HHmmss'Z'`, a second one of the same second `-2` and on (ADR-362 §3); the publish delivers, to each destination `--to` names, or to Confluence where it names none, the rendering of the generation run that invocation reached.** The id is read against `^[0-9]{8}T[0-9]{6}Z(-[0-9]+)?$`. One not of that form is refused before anything is read, exit 2, as a destination no plugin has is (ADR-333 §6):

```
vespera publish has no invocation <id>: an invocation is named by its id, as 20261114T083000Z or 20261114T083000Z-2.
```

**The invocation's report** is the copy `logs/report-<id>.json`, read as ADR-393 §7 reads the last run's: `format`, `invocation.command`, `arrivedAt` and `places.deliverableRoot`, the rest ignored; a copy that cannot be read, is not JSON or is of a `format` the reader does not know is no report. **The generation run** is the one its `arrivedAt` names under the stage `generation`. `PublishedInvocation`, of `pipeline`, reads it, beside `LastRunInvocationReader`.

### 2. The refusals before any destination

**Each is said once on standard error, whole, and the invocation exits 1 with no destination asked, no site and no folder touched**, kept in the report as every refusal after the working-directory lock is (`Publish.refuse`). In this order, after the destinations' names and §1's form are checked:

| Where | The line |
| --- | --- |
| no report of the invocation is kept, or the one kept cannot be read | `vespera publish sent nothing: this working directory keeps no report of the invocation <id> that can be read. Name another, or publish without --invocation.` |
| its `invocation.command` is not `run` | `vespera publish sent nothing: the invocation <id> was a vespera <command>, which draws no deliverable. Name another, or publish without --invocation.` |
| its `arrivedAt` names no generation run, or that run's generation step is not recorded finished in the working store | `vespera publish sent nothing: the invocation <id> did not reach the deliverable. Name another, or publish without --invocation.` |
| no tree is at the place of §3 | `vespera publish sent nothing: the tree the invocation <id> drew is no longer at <tree>. Name another, or publish without --invocation.` |
| the tree there is another generation run's or another working directory's (§3) | `vespera publish sent nothing: the tree at <tree> was drawn again for another generation run or working directory after the invocation <id>. Name another, or publish without --invocation.` |

`<command>` is the copy's command as it holds it; `<tree>` is the tree's path, absolute and normalised, as §3 composes it.

### 3. Where the tree is looked for, and when it stands

**The tree is looked for at the output folder the invocation's report names in `places.deliverableRoot`, read as `vespera.deliverable-root` is read** (ADR-276 §1, ADR-334 §1): `DeliverableRoot.of(<that value>).treeOf(<working directory>, <generation run>)`, the root itself where it names a root other than `<working directory>/deliverable`, and `<working directory>/deliverable/<generation run>` where it names that folder. Where the field is `null`, the tree is looked for where the root in use now places it, as a publish without `--invocation` looks. The root named now is not read otherwise: a named root holds one generation run's tree (ADR-276 §3), so the earlier run's tree lies where that invocation drew it.

- **At the unnamed root** the tree stands where its folder is a folder: it is named for its generation run (ADR-276 §3). Otherwise there is no tree there.
- **At a named root** the tree stands where its marker names this working directory, compared as ADR-276 §2 compares, and this generation run. Where the root is not there, is not a folder, or holds no marker naming both, there is no tree there; where its marker names another generation run or another working directory, the tree there was drawn again for another. `DeliverableRoot` reads the marker, ADR-276 §3 making it the one place of the marker's rule.

### 4. Each destination of an invocation named

**For each destination, in turn, as ADR-333 §6 has it, the rendering is `<tree>/<plugin's name>`.** Where it holds neither the plugin's list nor its `drawing.txt`, that destination is refused, and the next goes on:

```
vespera publish sent nothing to <name>: the invocation <id> drew no <name> rendering. Name another, or publish without --invocation.
```

and a bare publish, `vespera publish sent nothing: the invocation <id> drew no Confluence rendering. Name another, or publish without --invocation.` Where its `drawing.txt` does not say `drawn`, ADR-333 §11's refusal stands. Otherwise the plugin is handed the rendering and the generation run, as any publish hands them: the Confluence publish still refuses a page list of another format or another generation run (ADR-378 §4), still updates its language's one tree in place and moves to the trash a page that tree no longer has (ADR-328 §4, §5); the website's delivery refreshes the one folder and removes what the previous publish listed and this one does not (ADR-333 §7). **The jar asks nothing before it replaces a later rendering with an earlier one**: a publish is an act the operator starts, and the pipeline never blocks (ADR-047).

### 5. Without `--invocation`

**A publish naming no invocation is ADR-393 §7's, unchanged**: the generation run the last `vespera run` reached, at the root named now, and the last minted where no `vespera run`'s report is kept.

### 6. The window lists the earlier deliverables of the working directory chosen

**Beneath the list of working directories and its three buttons, in the Working directory section, the window shows `earlier-deliverables`, a list headed `earlier-deliverables-heading`, *Earlier deliverables*, of the deliverables the chosen working directory's kept `vespera run`s drew and that still stand, newest first, and a button `earlier-deliverables-clear`, *Clear the choice*.** It is read as the chosen row's parts are (ADR-393 §5): as the window opens, as another working directory is chosen, and as the window's child ends; it reads the copies in `logs` and, of each tree, its marker and whether each destination's list is there, and it opens no database, takes no lock and reads no other file of a tree. Its placeholder, `earlier-deliverables-empty`, reads *No earlier deliverable of this working directory still stands.*

`EarlierDeliverable`, of the control panel, in `java.base` alone (ADR-285 §3):

```java
public record EarlierDeliverable(String invocation, String started, String corpusRoot, Path tree,
        List<String> destinations, boolean latest) {
    public static List<EarlierDeliverable> of(Path workingDirectory, ZoneId zone);   // never throws
    public String words();
}
```

- **What is read.** Of the copies `logs/report-<id>.json`, read and ordered as ADR-393 §7 reads and orders them, those whose `invocation.command` is `run`, whose `arrivedAt` names a generation run, and whose `places.deliverableRoot` is not `null`.
- **One row for each generation run**, from the newest such copy that reached it: `invocation` its id, `started` its `invocation.started` as `yyyy-MM-dd HH:mm` in `zone`, `corpusRoot` its `places.corpusRoot` as it holds it, `tree` the tree §3 places, `destinations` the destinations of `ChildCommand.DESTINATIONS`, in its order, whose list lies in `<tree>/<destination>`.
- **Listed only where it stands**: its tree stands as §3 has it, the window comparing the marker's working directory as `ControlPanelState.namesTheSameFolder` compares, and `destinations` holds one at least. A row that does not stand is not listed.
- **`latest`** is true of the row whose generation run the newest copy whose command is `run` reached, and of no row where that copy reached none: the row a publish without `--invocation` delivers.
- **The rows are newest first**, by the order of the copies they were read from.
- **`words()`** is what the list shows of a row: `<started>`, ` (latest)` where `latest` is true, then ` — corpus root: <corpusRoot>; output folder: <tree>; drawn: <destinations joined by ", ">`. No run id is shown (ADR-098).

### 7. Choosing one, the boxes, and Publish

**Nothing is chosen as the list is read: Publish delivers what the last `vespera run` reached, as before (§5).** A row is chosen by selecting it, and the choice is cleared by *Clear the choice* and whenever the list is read again (§6).

- **The boxes** (ADR-345 §4) are offered for the chosen row's `destinations`; with none chosen, for the latest row's; with no row latest, for none, and Publish is not shown, as where nothing is drawn. Only where the working directory keeps no copy of a `vespera run`'s report are they offered as ADR-345 §4 has it, for a destination drawn in any run's tree, the publish then taking the last minted (ADR-393 §7). Confluence's is ticked as a box is added, and the others not, as before.
- **The command.** With a row chosen, Publish starts `ChildCommand.publish(java, jar, values, destinations, invocation)`, whose arguments are `publish --invocation <id> --to <destinations>`, `--to` staying the last of them, before `--db-dir`; the latest row chosen starts the same. `command-line-publish` shows it as it shows every other (ADR-344 §4). `ChildCommand` refuses an `invocation` not of §1's form with an `IllegalArgumentException`, `an invocation outside the form of an invocation's id was named`, as it refuses a destination outside its closed list. `ChildCommand.ARGUMENTS` gains `publish invocation`, its control `earlier-deliverables`, *The row chosen in Earlier deliverables, where one is* (ADR-336 §2).
- **The confirmation.** Where the row chosen is not the latest and Confluence or the website is ticked, Publish asks, before it asks for the API token and before anything starts, `Dialogs.confirm` with the title `Publish an earlier deliverable`, the answers `Publish` and `Cancel`, and the sentence `The deliverable of <started> replaces what is published to <where>, and what it does not hold is taken off there.`, `<where>` being `Confluence`, `the website`, or `Confluence and the website`, as ticked. Cancel starts nothing and asks nothing more. The latest row, no row chosen, and `retrieval` alone ask nothing.
- **Run and Label** act on the working directory's latest invocation, as ADR-436 §6 has it.

### 8. Run ids

**No run id moves.** `VesperaCommand.Publish` and `PublishedInvocation` lie in `pipeline`, which no stage's version names (ADR-222); `DeliverableRoot`'s reading of a marker lies in `deliverable`, which no stage's version names either (ADR-274 §5); the rest is the control panel's. No profile key is added, and the report gains no field.

## Alternatives rejected

- **A row for each `vespera run` invocation.** Two invocations reaching one generation run would show one deliverable twice, and an invocation that drew nothing would be a row with nothing to publish.
- **`--run <generation run's id>`.** A run's id is not shown to the operator (ADR-098, ADR-154); an invocation's id is dated, and is what the report and the window already show.
- **Topic and cluster counts on a row.** No copy holds them, and the window reading the working store or a tree ledger breaks ADR-393 §5 and ADR-429 §5; a field of the report for them adds what ADR-362 §3 keeps out.
- **The tree looked for at the root named now.** A named root holds one generation run's tree, so an earlier run's tree would be found nowhere once a new root is named, and the root named now would be refused as holding another run's.
- **A confirmation asked by the jar.** A publish started by hand is the operator's explicit act, and a command that waits for an answer blocks the pipeline (ADR-047). The window asks, because a row chosen by a click is easy to leave chosen.
- **A section of its own in the sidebar.** The list belongs to the working directory chosen, and changes with it; a twelfth section would stand apart from the row it reads.
- **Rows whose tree is gone, shown greyed.** They cannot be published, and the jar's refusals say why where an id is typed by hand.

## Consequences

- **An earlier deliverable can be published again**, from the window by choosing its row, or by hand with its invocation's id.
- **Publishing an earlier deliverable replaces the later one at Confluence and on the website**, its pages updated in place and those it does not hold moved to the trash, its files written and those it does not list removed; the window says so before it starts one.
- **A deliverable at a named root that a later run drew over cannot be published again**: a named root holds one tree (ADR-276 §3).
- **Publish's boxes offer what the publish delivers** where a `vespera run`'s report is kept.
- **`CONTEXT.md`'s Publish entry** says a publish may name an invocation; **the README's** publish paragraph and its control panel's Working directory section say what `--invocation` and the list do; `check-claims` reads `publish`'s options.
- ADR-393, ADR-436, ADR-333, ADR-345 and ADR-047 carry a note at their heads pointing here.

## Known limits

- **A tree drawn by an earlier build** may hold no rendering of a destination drawn since, or a page list of an earlier format: the first is not offered and §4 refuses it, the second is refused by the Confluence publish (ADR-378 §4).
- **A copy that names no output folder** is not listed by the window, though `--invocation` looks for its tree where the root in use now places it (§3).
- **The unnamed root's tree is taken as its folder names it**, its marker not read (§3).
- **Two windows over one working directory** may each publish a different deliverable; the working-directory lock keeps them one at a time, and the last one published stands.

## What this does not decide

- **Choosing an earlier invocation for Run or Label.**
- **A bound on the list's length.**
- **Removing an earlier deliverable from the window**: disk is the operator's to free (ADR-103).

## Tests

| Method | What it holds |
| --- | --- |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.theGenerationRunTheNamedInvocationReachedIsPublishedThoughALaterRunReachedAnother` | §1, §4: the run an earlier copy names is asked, the last run's not |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.theTreeIsLookedForAtTheOutputFolderTheInvocationDrewIn` | §3: a named root the earlier copy names is read, though another is named now |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.anIdNotOfAnInvocationsFormIsRefusedWithExitTwo` | §1: the line, whole, exit 2, nothing asked |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.anInvocationWhoseReportIsNotKeptIsRefused` | §2: no copy, and a copy that is not JSON, each the line whole, exit 1 |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.anInvocationThatWasNoRunIsRefused` | §2: a label's copy, the line whole |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.anInvocationThatDidNotReachTheDeliverableIsRefused` | §2: no generation run named, and one whose step is not finished, each the line whole |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.aTreeNoLongerWhereTheInvocationDrewItIsRefused` | §2, §3: the unnamed root's folder gone, and a named root emptied, each the line whole naming the tree |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.aTreeDrawnAgainForAnotherGenerationRunIsRefused` | §2, §3: a named root whose marker names another generation run, the line whole |
| `engine.pipeline.PublishNamesAnEarlierInvocationTest.aRenderingTheInvocationDidNotDrawIsRefusedForThatDestination` | §4: a bare publish of a tree with no `confluence/`, the line whole, exit 1 |
| `engine.pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.everyOptionAndPositionalArgumentOfTheCommandTreeIsInTheControlPanelsTableOnce`, standing | §7: `publish invocation` is in `ChildCommand.ARGUMENTS` once `--invocation` is in the model |
| `ui.EarlierDeliverablesAreListedFromTheRunsReportsTest.eachGenerationRunAKeptRunReachedIsOneRowNewestFirstAndTheLatestMarked` | §6: every part of each row, whole, one row for a generation run reached twice, a label's and a publish's copy passed over |
| `ui.EarlierDeliverablesAreListedFromTheRunsReportsTest.aDeliverableThatNoLongerStandsOrDrewNoRenderingIsNotListed` | §6: a copy naming no output folder, a folder gone, a named root drawn again and a tree with no list, none listed |
| `ui.EarlierDeliverablesAreListedFromTheRunsReportsTest.aRowsWordsAreItsDateItsRootItsFolderAndWhatIsDrawn` | §6: `words()`, whole, of the latest row and of another |
| `ui.EarlierDeliverablesAreListedFromTheRunsReportsTest.aFolderKeepingNoRunsReportListsNothing` | §6: no `logs`, and a label's copy alone, list nothing |
| `ui.PublishOfAnEarlierDeliverableNamesItsInvocationTest.theChildIsGivenTheInvocationBeforeItsDestinations` | §7: `publish --invocation <id> --to <names>` before `--db-dir`, the token still reaching a publish to Confluence |
| `ui.PublishOfAnEarlierDeliverableNamesItsInvocationTest.anInvocationNotOfAnIdsFormIsRefused` | §7: the exception's message, whole |
| `ui.PublishDeliversTheEarlierDeliverableChosenTest.theListShowsTheEarlierDeliverablesNoneChosenAndTheBoxesAreTheLatests` | §6, §7: the rows' words, nothing chosen, the boxes those of the latest row's tree and not of a tree no kept run reached |
| `ui.PublishDeliversTheEarlierDeliverableChosenTest.aRowChosenOffersItsBoxesNamesItsInvocationAndAsksBeforePublishing` | §7: the boxes, the command line shown, the confirmation whole, Cancel starting nothing; cleared, the line names no invocation |

## What the implementation owes

Beside turning the tests above green: `--invocation` and §1 to §4 in `VesperaCommand.Publish`, its help and javadoc; `PublishedInvocation`, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule; `DeliverableRoot`'s public reading of whether a tree stands at a root for a working directory and a generation run (§3); `EarlierDeliverable`; the list, its heading, placeholder and button in `Cockpit`'s Working directory section, its reading, the choice and its clearing, the boxes, the confirmation and `ControlPanelState`'s plan carrying the invocation chosen (§6, §7); `ChildCommand.publish`'s five-argument form and `ARGUMENTS`' row; `CONTEXT.md`'s Publish entry and the README (Consequences); and the notes at the heads of the five records amended, which this record's change carries.
