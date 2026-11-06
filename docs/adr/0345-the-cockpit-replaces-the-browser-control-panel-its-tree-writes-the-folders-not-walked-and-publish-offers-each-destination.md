# ADR-345 — The cockpit replaces the browser control panel ADR-340 laid out, its tree writes the folders not walked beside a list of folders not relevant, its steps show the census, the relevance floor and the relevance report as stages 0, 5e and 5, and Publish offers each destination drawn

> **Partly amended — see [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md).** Of §1 and §4: a publish's child is also given `VESPERA_STARTED_BY=control-panel` and, where Confluence is ticked, the API token Publish asks for in a masked field, as `VESPERA_CONFLUENCE_TOKEN`; `ChildCommand` gains `withConfluenceToken`, and `ChildCommandTest.publishIsTheJarsPublishCommandAndAddsNoConfluenceValue` holds of a publish given no token. The rest stands.

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Decided**: by the operator, that the window of [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) takes the place of the browser control panel [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md) lays out, its home page, top bar, drawer and the words of its pages, while the walk's folders not walked stand (§1); that the Inclusions tab's tree writes the profile's `foldersNotWalked` (§2); that the steps chart shows the census as stage `0`, the relevance floor as `5e` and the relevance report as `5` (§3); and that the window keeps Publish's destinations, a box for each destination whose rendering is drawn, Confluence's ticked and the website's not, and `serve`'s `--db-dir` in the table of arguments as never passed, the window starting no `serve` (§4). Under the operator's delegation of the same date, Claude decided: that the folders not relevant stay in the window, as a list beneath the tree, with [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6's write and answers (§2); that nothing replaces [ADR-339](0339-an-argument-of-the-control-panels-table-names-the-form-its-control-stands-on-six-members-of-cluster-page-are-public-for-the-website-and-its-lists-stops-and-pages-are-as-recorded.md) §1's `form`, a control being found by its id (§4); that ADR-340 §6's working directory left empty and its child's environment stand in the window (§1); the ids; and the tests.
- **Supersedes**: ADR-340 §1 to §5 and §7, and of its §10 every name and every owed item of a page, a form, the script or the style: the top bar and its box Autolabel, the home page and its reload, the drawer and its sections, the next step's box, the form `folders-to-process` with its boxes Read and Code, and the words of the pages and their sections. Its §6, §8 and §9 stand as §1 says. It carries a note at its head.
- **Amends**: ADR-344 §1, whose `publish` stands where any destination's rendering is drawn, with `publish-to` beside it (§4), whose row `step-<name>` shows the stage §3 gives, and whose Inclusions tab is §2's; its Keeps of ADR-334 §6, which reads as §2 has it; its §3, whose working directory left empty is ADR-340 §6's folder (§1); its §4, whose `ChildCommand` gains `DESTINATIONS`, the publish of named destinations and `environmentFrom`, and whose `ARGUMENTS` hold `publish to` and `serve db-dir` (§4); and its Tests rows of the two tests renamed (Tests). It carries a note at its head.
- **Amends**: ADR-339 §1, whose `Argument` has no `form` and whose `publish to` is set by the window's `publish-to` (§4); and its Tests rows on the table, held now by the window's tests (Tests). Its §3's offer of Publish holds in the window (§4). It carries a note at its head.
- **Amends**: [ADR-341](0341-vespera-serve-tells-a-page-from-a-copy-by-the-websites-list-and-the-files-name-serves-working-directory-is-never-passed-and-its-refusals-answers-and-readings-are-as-built.md) §2, whose `serve db-dir` has "no form and no control": it has no control, the table having no form, and the entries never passed are three, `label <file>` being set by `label-file` since ADR-344 §4 (§4). It carries a note at its head.
- **Amends**: ADR-334 §6, whose list of folders not relevant stands beneath the Inclusions tab's tree, its adds and removes the window's (§2); its file, its one action and its answers are unchanged. It carries a note at its head.
- **Keeps**: ADR-340 §8 and §9: `foldersNotWalked`, the walk that counts a folder not walked and never enters it, what it records and how it is accounted for, and the run ids that moves. ADR-340 §6, for the working directory left empty and for the child's environment (§1). [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §2: a value is saved with its note filled in. [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1: the launch values stay seven, and a destination is none of them. [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, as [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) amended it: the window starts no `serve`. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the table, the destinations and the stages are text of the control panel's own, naming no class of the jar. [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §6: the window is tested on JavaFX's Headless platform, by ids.
- **Read with**: ADR-342, ADR-343 and ADR-344.
- **Settles**: [#700](https://github.com/algernon28/vespera/issues/700), with ADR-342, ADR-343 and ADR-344.

## Context

**Two faces were recorded for one control panel.** ADR-340 lays the browser control panel out as a home page under a top bar, with every value in a drawer, a tree of the corpus's folders in a form of its own and the words of each page; and it adds a profile key, `foldersNotWalked`, that the walk honours. ADR-342 removes the server and every page, and ADR-344 lays out the window that replaces them. Each also says what an empty working directory is: ADR-340 §6, the folder the jar would use on this machine, which Run passes; ADR-344 §3, a field no source names, which starts empty.

**Two lists answer "nothing I want here" differently.** A folder not walked (ADR-340 §8) is counted by the walk and never entered: nothing below it is read, converted or asked about, and since a walk given another list is another walk, every run after the change is new (ADR-340 §9). A folder not relevant (ADR-334 §6) is walked, read and judged like any other; the list only answers the relevance questions about the files below it, `false`, the rest `true`, and moves no run. ADR-344 §1's tree writes the second list.

**Three steps show no stage.** ADR-344 §1's row of a step shows the stage under whose runs the jar records the step's completion, as the progress page's tab of runs reads it (ADR-269 §5): none for the census, which mints no run, nor for the relevance floor and the relevance report, which record no completion (ADR-118). The records name them by number: the census is stage 0, the floor stage 5e, and the report is stage 5's, written after clustering, stage 5f.

**A form, where the window has none.** ADR-339 §1 gives each entry of `ChildCommand.ARGUMENTS` the HTML form its control stood on, so that a page's test could look for the control inside it; ADR-339 §3 offers Publish's destinations as checkboxes on the invocation page's form `publish`, and ADR-341 §2 adds `serve`'s `--db-dir` as an entry no control sets. The window has no forms: ADR-344 §4 names a control by its id, which is unique in the window's scene.

## Decision

### 1. The cockpit is the control panel; what of ADR-340 stands

**The window ADR-342 and ADR-344 describe is the control panel's one face.** ADR-340 §1 to §5 and §7 lay out pages ADR-342 §5 removes, and go with them: the top bar and Autolabel, the home page and its reload, the drawer and its sections, the next step's box, the form `folders-to-process` with its boxes Read and Code, and the words of the pages and their sections. A code set is named in the sidebar's Code sets section (ADR-344 §1), and not in the tree. The names and lines ADR-340 §7 gives each key, `ProfileKey`'s, are what the Profile tab shows as a key's descriptive name and description (ADR-344 §1), but for `foldersNotWalked`'s line, which names the Inclusions tab in place of the form that is gone: *Folders of the documents folder that Vespera never reads. Untick them in the Inclusions tab.*

**§8 and §9 stand whole**: the key, the walk, its two tables, its accounting and the runs it moves.

**§6 stands for the working directory and the child.** Where none of ADR-344 §3's sources names the working directory, its field starts empty, as ADR-344 §3 has it, and stands for the folder `WorkingDirectoryDefault` resolves by ADR-340 §6's table, read once as the window opens from its JVM's current directory, system properties and environment. That folder is the window's working directory: its files are the ones read and written, and Run, Label and Publish pass it as `--db-dir`, so ADR-344 §3's "the working directory always passed" holds of it, and ADR-334 §2's *in use* beside the field names it. Every child inherits the window's environment less the eight variables ADR-340 §6 names, with ADR-292 §4's added, so that the folder passed is the folder the child opens. A state given no default holds the one of the JVM's own current directory, with no property or variable, as ADR-340 §10 has the four-argument state hold it.

### 2. The Inclusions tab: the folders not walked, and beneath them the folders not relevant

**The tree writes `foldersNotWalked`.** It is ADR-344 §1's tree of the corpus root the field names: every folder ticked at first, an unticked one standing unticked with every folder below it, a folder with one below it unticked half-ticked, a folder's subfolders listed when it is first opened and never before, and no file listed. A folder is drawn unticked where its path from the corpus root is a piece of the profile's `foldersNotWalked`, compared as the walk compares it, element by element and without regard to case on Windows (ADR-340 §8.2). A value the walk cannot read (ADR-340 §8.1) is drawn as the walk reads it, every folder ticked, and `inclusions-outcome` says so.

**Save, `inclusions-save`, writes `profile.yaml`'s `foldersNotWalked` and nothing else**, as the Profile tab's Save writes a key, under ADR-295's lock, by ADR-330 §5's checks and with ADR-343 §2's note:

- **the value** is the highest unticked folder of each branch, spelt from the corpus root with `/` between its names, and each piece the profile lists that names no folder there, kept as stated; the pieces the profile already lists come first, in its order, and the others after them, in the tree's order; joined by the system's path separator, as `CorpusFoldersValue` reads them;
- **a value as the file states it is not written**; a changed one is written with `set in the control panel`, the tab showing no note;
- **with no folder unticked and none kept**, the key is taken out of the file, so every folder is read, as an unset key reads. A value the walk cannot read is read as no value (ADR-340 §8.1), so it has no piece to keep: a Save with nothing unticked takes it out, and one with folders unticked writes them in its place;
- **where no tree can be drawn**, the corpus root the field names being no folder that is there, every piece of a value the walk reads is kept as stated, so a Save writes the value as the file states it, which is not written.

**Reset, `inclusions-reset`**, puts the tree back to the profile as last saved, and writes nothing. The Profile tab shows `foldersNotWalked` read-only, as it shows `seedFolder` and `codeSetFolders`, with a link that opens the Inclusions tab. The tab's line above the tree says, in the implementation's words, that an unticked folder is never read, and that a change makes the next walk another, every run after it new.

**The folders not relevant are a list beneath the tree**, ADR-334 §6's list, whose file, one action and answers are unchanged:

| Part | Id | What it does |
| --- | --- | --- |
| The list | `not-relevant-list` | the folders `not-relevant-folders.yaml` lists, in its order, as the file holds them after each action; none where it is not there |
| The folder | `not-relevant-folder` | a folder typed, from the corpus root, `/` between its names |
| Its Browse | `not-relevant-browse` | the system's folder dialog at the corpus root, through ADR-342 §6's seam; the folder chosen put in `not-relevant-folder`, from the corpus root |
| Add | `not-relevant-add` | ADR-334 §6's save of the folder typed: the folder added to the list the file holds as read then, and every question of `relevance-labels.yaml` answered, `false` at or below a folder listed and `true` otherwise, all or none, in one hold of the lock |
| Remove | `not-relevant-remove` | ADR-334 §6's remove of the folder selected in `not-relevant-list`, the questions answered again; with none selected it writes nothing, whatever `not-relevant-folder` holds, a folder typed being Add's, and `not-relevant-outcome` says so |
| The outcome | `not-relevant-outcome` | the one line ADR-334 §6 says for the action, a refusal among them; a refused folder stays typed and nothing is written |

**Why the two live so.** The tree answers what the operator gave it to answer, which folders hold nothing to be read, and the profile is where the jar reads it. The list answers the relevance questions in bulk, at no cost to any run, about folders that are read. One box with both meanings would hide which was chosen and what it costs; a second box on each row would double the tree's states, when a folder not walked can hold no question. The list keeps ADR-334 §6 whole, and stands where the folders are chosen.

### 3. The stage beside each step

**`StopCosts.stageOf`**, which each row `step-<name>` shows, is the stage under whose runs the jar records the step's completion, as ADR-344 §1 has it, and for the three the jar records under none, the number the records give it:

| Step | Shown |
| --- | --- |
| `census` | `0` |
| `relevance-floor` | `5e` |
| `relevance-report` | `5` |

The other thirteen steps show the stage the jar names, unchanged. Each is text of the control panel's own (ADR-285 §3), and a test holds it to the jar's.

### 4. Publish's destinations, and the table of arguments

**Publish stands in the action row where any destination's rendering is drawn**, Confluence's by ADR-308 §6 and the website's by ADR-333 §6, each found by its list as ADR-339 §3 finds it, `pages.json` under `confluence/` and `files.json` under `website/`, at the top of the tree the output folder names, or of a run's folder under the working directory's `deliverable` where it names none (ADR-334 §1).

**`publish-to`**, a horizontal box of the action row standing after `publish`'s place, holds a checkbox `publish-to-<destination>` for each destination whose rendering is drawn, in `ChildCommand.DESTINATIONS`' order, `confluence` then `website`; it holds none where none is drawn, and it is a node of the window whatever is drawn. A box is drawn as it is added, Confluence's ticked and the website's not, and is kept nowhere, so the destinations are no launch value and are not remembered from one start of the window to the next.

**Pressed, Publish starts `publish --to <destinations>`**, the ticked boxes' destinations joined by commas in that order, before `--db-dir`; with none ticked it starts nothing and `outcome` says so in one line. `command-line-publish` shows the line it would start with the boxes as ticked, or the refusal's line where none is (ADR-344 §4).

**`ChildCommand`** gains, beside ADR-344 §4's:

```java
public static final List<String> DESTINATIONS;                                  // confluence, website: a closed list of the control panel's own
public static ChildCommand publish(Path java, Path jar, LaunchValues values, List<String> destinations);
                                       // IllegalArgumentException where destinations is empty or names one outside DESTINATIONS
public Map<String, String> environmentFrom(Map<String, String> inherited);   // inherited less ADR-340 §6's eight, environment() over it
```

`processBuilder()`'s environment is `environmentFrom` over the one the builder inherits. `publish(Path, Path, LaunchValues)` goes. A test holds `DESTINATIONS` to the names of the plugins compiled into the jar.

**The table of arguments has no `form`.** `ChildCommand.Argument` stays `(command, name, control, reason)`, as ADR-344 §4 has it: a window's id is unique in its scene, so it says where its control is, and the window's test finds each one by it. The table holds, beside ADR-344 §4's entries:

| `command` | `name` | `control` | `reason`, in the implementation's words |
| --- | --- | --- | --- |
| `publish` | `to` | `publish-to` | the boxes beside Publish, one for each destination drawn; never a launch value |
| `serve` | `db-dir` | | never passed: the window starts no `vespera serve`, a reader's server being a command the operator starts and ends |

**The entries never passed are three**: `vespera help`, `vespera version` and `serve db-dir`.

### 5. Run ids

No file under `src/main/java/io/algernon/vespera` changes for this record, so no run id moves. The runs ADR-340 §9 moves for `foldersNotWalked` stand as it records them.

## Consequences

- One face: the window is the control panel, and a decision about its layout is made once.
- An operator leaves a folder unread from the Inclusions tab, and the next walk is another walk; answering a folder's questions as not relevant stays beside it, at no cost to any run.
- Each step's row shows a stage, the census, the floor and the report among them.
- Publish delivers to the website from the window as it does to Confluence, the destinations ticked each time.

## Known limits

- **The steps' column mixes words and numbers**: thirteen rows show the stage the jar names, such as `embedding-scoring`, and three a number (§3).
- **A Save of the tree after a walk costs a walk**: every run after it is new (ADR-340 §9), which the tab says in words alone, before the Save.
- **An add or a remove of a folder not relevant answers every question of the label file again**, over the local model's answers and any typed in the file, as ADR-334 §6 has it.
- **A folder not walked is matched by its path**, so one moved or renamed in the archive is read again until the tree unticks it there (ADR-340's Known limits).
- **The working directory left empty is read from ADR-340 §6's sources alone**, whose Known limits stand: a folder named only in `application-local.yaml` is not seen.
- **A destination's box is ticked again for each start of the window**, being kept nowhere.

## Alternatives refused

- **One box per folder meaning both lists.** The operator could not tell a folder never read from one read and answered, nor see which costs a walk.
- **A second box on each folder's row.** Four states for a row where three can be had, a folder not walked holding no question to answer.
- **The folders not relevant left out of the window.** The operator kept the list (ADR-340's Decided), and the relevance questions would again be answered one by one.
- **The list in a sidebar section of its own.** An eighth section for the folders the tab beside it already shows.
- **`form` kept, every entry naming one form of the window.** A column that holds one value says nothing.
- **`publish-to` added and removed with Publish.** The table's control would not be a node of the window where nothing is drawn, and the test that finds each control could not tell a missing control from an undrawn one.
- **ADR-344 §3's empty working directory, with Run refused.** It would take back ADR-340 §6, whose working directory left empty is the folder the jar would use on this machine; the field still starts empty, as ADR-344 §3 has it.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheFoldersToIncludeTest.aFolderUntickedIsWrittenAloneThoughItsSubfoldersWereListed` | `A` unticked: `B` and `C` below it unticked, `D` ticked; `foldersNotWalked` written `A` with `set in the control panel`; no `not-relevant-folders.yaml` written (§2) |
| `controlpanel.TheFoldersToIncludeTest.untickingEverySubfolderLeavesTheFolderHalfTickedAndWritesOnlyThem` | the only subfolder, or every one, unticked: `A` half-ticked, those subfolders written and `A` not (§2) |
| `controlpanel.TheFoldersToIncludeTest.aListedSubfolderComesBackUnchanged` | `A/B` listed, with `A` holding `B` alone or `B` and `C`: drawn unticked under a half-ticked `A`, and `profile.yaml` byte for byte as it was after a Save (§2) |
| `controlpanel.TheFoldersToIncludeTest.aListedFolderNoLongerThereIsKept` | `Gone` and `A/Gone` listed, then `C` unticked: the three written, the two listed first in their order (§2) |
| `controlpanel.TheFoldersToIncludeTest.everyFolderTickedAgainTakesTheKeyOut` | `A` listed and ticked again: `foldersNotWalked` no longer in the file, `generationModel` still there (§2) |
| `controlpanel.TheFoldersToIncludeTest.anotherCorpusRootSetInTheFieldIsTheTreeDrawnAndSaved` | the field set to another corpus root: its folders drawn, none of the first's, and `B1/Deep` written (§2) |
| `controlpanel.TheFoldersToIncludeTest.onWindowsAFolderListedInAnotherCaseIsTheFolderItself` | on Windows, `notes` listed and `Notes` on disk: `Notes` drawn unticked, `profile.yaml` byte for byte as it was after a Save, and ticked again `foldersNotWalked` no longer in the file (§2, ADR-340 §8.2) |
| `controlpanel.TheFoldersToIncludeTest.aValueTheWalkCannotReadIsDrawnTickedAndASaveWithNothingUntickedTakesItOut` | `../outside` listed: every folder drawn ticked, one line in `inclusions-outcome`, and after a Save with nothing unticked `foldersNotWalked` no longer in the file, `generationModel` still there (§2) |
| `controlpanel.TheFoldersToIncludeTest.whereNoTreeCanBeDrawnASaveKeepsEveryFolderListedAsStated` | `A` and `B/C` listed, the corpus root no folder: no tree drawn, and `profile.yaml` byte for byte as it was after a Save (§2) |
| `controlpanel.TheFoldersNotRelevantTest.theListIsTheFoldersTheFileListsInItsOrder` | `B/x` then `A` listed: `not-relevant-list` the two in that order (§2) |
| `controlpanel.TheFoldersNotRelevantTest.aFolderAddedIsWrittenAndEveryQuestionIsAnsweredFromTheList` | `Old` typed and added: the file lists it, the question below it answered `false` and the other `true`, the list showing it, `profile.yaml` unwritten (§2, ADR-334 §6) |
| `controlpanel.TheFoldersNotRelevantTest.aFolderRemovedIsNoLongerListedAndItsQuestionsAreAnsweredAgain` | `Old` selected and removed: the file lists none, the question below it answered `true` (§2, ADR-334 §6) |
| `controlpanel.TheFoldersNotRelevantTest.aFolderTheListCannotHoldIsNotAddedAndNothingIsWritten` | `../Old` typed and added: one line in `not-relevant-outcome`, the folder still typed, neither file written (§2, ADR-334 §6) |
| `controlpanel.TheFoldersNotRelevantTest.removeWithNoFolderSelectedWritesNothingThoughAFolderIsTyped` | `Keep` listed, none selected and `Old` typed, Remove pressed: one line in `not-relevant-outcome`, `Old` still typed, the list `Keep` alone, neither file written (§2) |
| `pipeline.TheStopCostsNameTheJarsStepsTest.theStageShownBesideEachStepIsTheJarsOrTheNumberTheRecordsGiveIt` | for each step of the job, the stage the jar records it under, and `0`, `5e` and `5` for the census, the relevance floor and the relevance report, which it records under none (§3) |
| `controlpanel.PublishOffersEachDestinationDrawnTest.bothRenderingsDrawnOfferTwoBoxesConfluenceTickedAndTheWebsiteNot` | both lists in a run's folder: `publish-to-confluence` ticked then `publish-to-website` not, in `publish-to`; `command-line-publish` `--to confluence` (§4) |
| `controlpanel.PublishOffersEachDestinationDrawnTest.withTheWebsiteAloneDrawnItsBoxIsOfferedAndNothingStartsUntilItIsTicked` | the website's list alone: its box alone, unticked; Publish pressed starts no child and says one line; ticked, `command-line-publish` `--to website` (§4) |
| `controlpanel.PublishOffersEachDestinationDrawnTest.theClosedListIsTheJarsPluginNames` | `DESTINATIONS` the names of the jar's two plugins (§4) |
| `controlpanel.ChildCommandTest.publishIsTheJarsPublishCommandAndAddsNoConfluenceValue` | `publish --to` with the destinations joined in the order given; none, or one outside the list, refused; `DESTINATIONS` `confluence` then `website` (§4) |
| `controlpanel.ChildCommandTest.theChildInheritsNoVariableNamingTheWorkingDirectory` | Run's, Label's and Publish's environment over one holding the eight: it less the eight, with `CI=1` and the five; the builder's the same over the JVM's (§1, §4) |
| `controlpanel.TheCommandOptionsTest.eachOptionIsPassedOnlyToTheCommandsItIsForAfterTheirArguments` | publish given no option, after `--to` and `--db-dir` (§4, ADR-344 §4) |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.everyOptionAndPositionalArgumentOfTheCommandTreeIsInTheControlPanelsTableOnce` | the model read holds `publish to` and `serve db-dir`, and the table every argument once (§4) |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy` | `publish to` by `publish-to`; `vespera help`, `vespera version` and `serve db-dir` the only three never passed (§4) |
| `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` | `publish-to` among the controls found, on a first start with nothing drawn (§4) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.theFieldsWorkingDirectoryIsReadAndWrittenBeforeARunAndAfterIt` | the tree drawn from that working directory's `foldersNotWalked`, and its Save writing that profile (§2) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.anEmptyWorkingDirectoryFieldIsTheFolderTheJarWouldUseWhichRunPasses` | no source naming it and `DB_DIR` given the window: the field empty, the *in use* line beside it naming that folder (ADR-334 §2), the Profile tab showing that folder's profile, and `command-line-run` ending `--db-dir=<it>` (§1) |
| `controlpanel.WorkingDirectoryDefaultTest` | ADR-340 §6's table, which §1 keeps |

## What the implementation owes

Beside turning the tests above green, the change that builds this record: gives `ChildCommand` §4's members and rows, taking `publish(Path, Path, LaunchValues)` out; gives `ControlPanelState` a package-private `useWorkingDirectoryDefault(WorkingDirectoryDefault)`, as `useSources` gives it the four sources, `ControlPanel.open()` handing it the one its JVM resolves, and a `Plan` carrying the destinations ticked; draws `publish-to`, the Inclusions tab and `StopCosts.stageOf` as §2 to §4 have them, and keeps `inclusions-tree`, `inclusions-save`, `inclusions-reset` and `inclusions-outcome`; has `not-relevant-remove` with no folder selected write nothing, as §2's table has it; and keeps `WorkingDirectoryDefault`, whose table §1 keeps.

## What this does not decide

- **Whether the other thirteen steps show their stage's number**, as the records number stages, in place of the jar's name.
- **Whether a folder not walked may lie below a code set's folder outside the corpus root**, which ADR-340 leaves open.
- **The words of the tab's lines and of each reason**, beyond what §2 and §4 say they say.
