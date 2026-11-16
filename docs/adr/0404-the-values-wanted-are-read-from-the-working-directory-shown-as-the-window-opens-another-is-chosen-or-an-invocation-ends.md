# ADR-404 — The values wanted are read from the working directory shown, as the window opens, another is chosen or an invocation ends

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, on 2026-11-16: that the rows of the values the next invocation wants always belong to the working directory shown; that when the working directory changes, and when the window opens, the window reads that directory's own `next-invocation.properties` and draws its rows; that a directory with no such file, or with one that cannot be read, draws no row; and that the rows never carry another directory's keys (§1, §2). Under the operator's delegation: that no line names the invocation the rows came from (§4); how the arrangement offered and the last seed folder follow the rule (§3); where the reading stands among the steps a change of working directory takes (§2); that the window's own child ending, however it ended, and another process's hold of the directory shown ending are readings too (§5); and the tests.
- **Amends**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §4, whose control panel reads `next-invocation.properties` from the child's working directory *"only after the child it started ended with exit code 0, and only then"*: it reads the file of the working directory shown at the four moments of §1, the child's ending whatever its exit code among them (§5). The rest of §4 stands: the closing line is shown as how the invocation ended whatever the file holds, a file not in §1's shape is read as unreadable and offers nothing, and the loop it walks is unchanged. It carries a note at its head.
- **Amends**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §1, whose rows of `wanted` are kept as drawn and loaded again by `loadFiles()` from the file as it then stands: they are kept and loaded again after a Save that writes, as there, but a change of working directory draws them anew from the new directory's `next-invocation.properties` before `loadFiles()` loads them (§2). §3's sets of open keys, emptied as the working directory changes, stand. It carries a note at its head.
- **Keeps**: ADR-294 §1 to §3: the file, its four keys, written by `vespera run`, `vespera label` and `vespera label --auto` with the closing line and deleted by every invocation once it holds the working-directory lock, so that the file in a working directory is its last successful invocation's of those three, or none. [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §6: the last seed folder is offered for the operator to confirm, never stated (§3). [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1: `wanted` stands beneath the actions with a Save of its own, and the Profile section says *needs a value* while it holds a row. [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §4: a change of working directory under an edit of the Profile window not saved asks Save or Discard, a Save writing where the form was loaded from (§2). [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §4: the row chosen in the list is the working directory every part of the window reads, and the list cannot be changed while the window's own child runs (§5); §5: each row of the list shows its folder's last invocation and next value, read from its `report.json` (§4). [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: `NextInvocation` reads the file with `java.base` alone. [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8: the window watches a holder of the working directory it did not start (§5). [ADR-306](0306-the-control-panel-reads-a-holder-line-of-its-own-process-as-held-by-nobody-and-serves-the-cluster-page-sizes-as-a-sixteenth-report.md): a holder line naming the control panel's own process is held by nobody (§5).
- **Settles**: [#814](https://github.com/algernon28/vespera/issues/814).

## Context

**Where the rows are drawn.** `WantedValues.draw` draws a row for each key `wants` names, in its order, the line *The next invocation wants these values:* above them and a Save beneath; it keeps the file's `arrangement` to offer to the row of `arrangementApproved`. `Cockpit.readNext()` calls it with what `NextInvocation.read` gives for the working directory the window names, nothing where the file is not there, cannot be read, or is not in ADR-294 §1's shape. The clock's tick calls `readNext()` once for each ending of the window's own child, and only for one that ended with 0 (ADR-294 §4). Nothing else draws the rows: the window opened on a working directory whose last invocation wants a value shows none until a child of its own ends with 0.

**A change of working directory.** Choosing another row of the list (ADR-393 §4) closes the reports' tabs, empties the three sets of open keys (ADR-377 §3), settles an edit of the Profile window not saved (ADR-389 §4) and calls `loadFiles()`, which since ADR-377 §1 loads each row of `wanted` again from the `profile.yaml` it reads, the new directory's. The rows themselves, and the arrangement `WantedValues` keeps, are the earlier directory's. So the window can show, for one working directory, the keys another's last invocation wanted, filled with this one's values, offer the other's arrangement for this one's approval, and write them into this one's `profile.yaml` at a Save.

**The file.** `next-invocation.properties` holds four keys and no more: `invocation`, `wants`, `answer` and `arrangement`; it names no invocation, no date and no path (ADR-294 §1). An invocation deletes it as it takes the working-directory lock, `vespera publish` among them, and only `vespera run` and `vespera label` write it, just before their closing line (ADR-294 §1, §3). So the file changes only while an invocation holds that working directory's lock.

**Who else can hold the working directory.** One Vespera runs on a machine at a time, the control panel's own child let through (ADR-395); the window still watches a holder it did not start, as ADR-362 §8 has it, which is a process the machine lock does not reach, such as one on another machine writing through a shared folder. `ControlPanelState.holders` reads such a hold from the holder line, the window's own writes holding nothing (ADR-306), and Run, Record my answers and Publish are disabled while it lasts.

**The badge.** The Profile section's title says *needs a value* while `wanted` holds a row (ADR-344 §1). `WantedValues.draw` redraws the badges once it has drawn a row, and returns without doing so where it draws none; the next `loadFiles()` redraws them.

## Decision

### 1. The rows are the working directory shown's

**The rows of `wanted`, the line above them, their Save and the arrangement they offer are drawn from one reading of `next-invocation.properties` in the working directory shown, and from nothing else.** The working directory shown is the row chosen in the Folders section's list, the machine's folder where that row is the first (ADR-393 §4). The window reads the file:

1. as the window opens;
2. as the working directory shown changes;
3. as the window's own child ends, however it ended (§5);
4. as another process's hold of the working directory shown ends (§5).

**Each reading replaces every row.** Each key `wants` names is a row, in the file's order, as ADR-344 §1 draws it; a key it does not name is no row. Where the directory holds no file, where the file cannot be read or is not in ADR-294 §1's shape, or where `wants` is empty, no row, no line and no Save is drawn. No row is ever kept from an earlier reading, so no row carries a key another working directory's file named.

**A Save that writes is no reading.** It changes `profile.yaml` and never `next-invocation.properties`: the rows are kept and loaded again from `profile.yaml` as ADR-377 §1 has them.

**The Profile section's badge follows the rows after every reading**, a reading that draws no row taking the badge away.

### 2. A change of working directory, in order

1. the reports' tabs close, as today;
2. the three sets of open keys are emptied (ADR-377 §3);
3. an edit of the Profile window not saved is settled, a Save writing into the directory it was loaded from (ADR-389 §4);
4. the new directory's `next-invocation.properties` is read and the rows drawn from it, each loaded from the new directory's `profile.yaml` and each open, as ADR-377 §3 starts a row of `wanted` after a change;
5. `loadFiles()` reads the new directory's files and loads every row, those of `wanted` among them, from one reading of its `profile.yaml`, then draws the badges.

The reading comes after the settling, so that nothing of the new directory is drawn while the Profile window's form still holds the earlier one's edit; and before `loadFiles()`, so that the window loads every row of the new directory from one reading of its `profile.yaml` and draws its badges once, over rows of the new directory alone.

**A value typed into a row of `wanted` and not saved goes with its row**, as one typed into Seed folder's row goes when `loadFiles()` loads it from the new directory's file. Those rows carry a Save of their own and are not the Profile window's form, which alone ADR-389 §4 asks about.

### 3. The arrangement offered and the last seed folder

**The arrangement offered** to `wanted-arrangementApproved` is the `arrangement` of the file the rows were drawn from (ADR-294 §2, §4), and is replaced with them at every reading: a directory whose file names none offers none, and none ever offers the arrangement another directory's file named. `arrangement` is present exactly where `wants` holds `arrangementApproved` (ADR-294 §1), so the row and the arrangement it offers come and go together.

**The last seed folder** is offered as ADR-292 §6 has it: it is the user's own, kept beside the launch values and of no working directory, and is offered to `wanted-seedFolder` where the directory shown's file wants `seedFolder` and that directory's `profile.yaml` states none, as it is to Seed folder's row; offered, not stated, a Save writes it. It carries no key: the key is the row's, and the row is the directory's.

### 4. No line names the invocation the rows came from

**The window adds no line saying which invocation the rows came from**, and the line above them reads as it does.

- The file names no invocation (ADR-294 §1), so such a line would be read from another file, `report.json`, which every invocation holding the lock rewrites, a refused, failed or stopped one and a publish among them, none of which writes `next-invocation.properties`. After any of those, the line would name an invocation the rows did not come from.
- By §1 the rows are always the directory shown's, from its last `vespera run` or `vespera label` that ended on its closing line. The row chosen in the list names that directory, its last invocation and its next value (ADR-393 §5).

### 5. The endings that are readings

**The window's own child, however it ended.** As it ends, ended, failed or refused, stopped, or a defect of the control panel, the window reads the file of the working directory shown, and shows the closing line as how it ended as before. The file in a working directory is its last successful invocation's, or none (ADR-294 §3): an invocation that took the lock and then failed or was stopped deleted it, and the reading after it draws no row, where rows kept would show what a file the directory no longer holds wanted; one refused before the lock left the file as it was, and the reading draws the same rows again.

**No working directory changes under the window's own child.** The list and its buttons cannot be used while it runs (ADR-393 §4), so the directory read as it ends is the one it ran in, and nothing more is decided for that case.

**Another process's hold.** Where a holder line of the working directory shown, naming neither the control panel's own process nor its child, is seen held at one tick of the window's clock and is not held at a later tick while the same working directory is shown, the window reads the file at that later tick. The file changes only under the lock, deleted as a hold starts and written just before it ends, so the end of a hold is the moment after which it stands; a reading as a hold starts could come before the holder deletes it, and none is made. While the hold lasts the rows stay as last drawn.

### 6. What changes, by class

- **`Cockpit`**: reads the file and draws the rows as the window opens, before the first `loadFiles()`; on a change of working directory between `settleTheEditBeforeLoading()` and `loadFiles()` (§2); at each ending of its own child, with no condition on how it ended; and at the end of another process's hold of the working directory shown, tracking from tick to tick whether such a hold was seen and for which directory. `readNext()` keeps its reading: nothing where the file is not there, cannot be read or is unreadable.
- **`WantedValues.draw`**: redraws the badges after every reading, a reading that draws no row included (§1).
- **`NextInvocation`**: unchanged.

No class of the jar changes, and `io.algernon.vespera.ui` is in no module: no run id moves.

## Consequences

- A window opened on a working directory whose last invocation wants a value shows the rows, and the Profile section's badge, at once, with no command started.
- After a run or a label that failed or was stopped having taken the lock, and after a publish, the working directory shown holds no file and the window draws no row: the next act is told by the closing line alone.
- A value typed into a row of `wanted` and not saved is not kept across a change of working directory (§2).
- `ui.TheProfileWindowTest.theSectionAndTheWindowsTitleCarryTheBadge` writes the file once the window is open: written before, the window reads it as it opens and the section's title says *needs a value* before the child ends.

## Known limits

- **A hold the window never sees.** One that starts and ends between two ticks of the window's clock, a second apart, is read at none of §1's moments: the rows stand as last drawn until the next one. A file changed by hand is likewise read only at the next.

## Alternatives refused

- **Emptying the rows on a change of working directory and reading nothing until a child ends.** The operator chose that the window read the new directory's file.
- **Keeping the rows across a change, with a line naming the invocation they came from.** The rows never carry another directory's keys.
- **Reading the file at every tick of the clock.** It would draw the rows anew each second and drop what is typed in them; the file changes only under a hold, whose ending the window sees (§5).
- **Reading as another process's hold starts.** The reading could come before the holder deletes the file (§5).
- **A line naming the invocation from `report.json`.** §4.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheValuesWantedFollowTheWorkingDirectoryTest.theWindowOpensOnTheValuesItsWorkingDirectoryWants` | the window opened on a working directory whose file wants one key, no child started: one row, of that key (§1) |
| `ui.TheValuesWantedFollowTheWorkingDirectoryTest.aChangeOfWorkingDirectoryDrawsTheNewOnesValuesAndNoneOfTheEarliers` | from a first working directory wanting one key, choosing a second wanting another: that key's row alone, holding the value the second's `profile.yaml` states; a third with no file: no row, and the Profile section's title without its badge; a fourth whose file names a key under an `invocation` that is none of the three: no row; the first again: its own key's row alone (§1, §2) |
| `ui.TheValuesWantedFollowTheWorkingDirectoryTest.theArrangementOfferedIsTheWorkingDirectorysOwn` | two working directories each wanting `arrangementApproved` with an arrangement of its own: the second chosen, its row offers the second's arrangement (§3) |
| `ui.TheValuesWantedFollowTheWorkingDirectoryTest.theWindowsOwnChildEndingAnyWayReadsTheFile` | the window opened on no file, its child started, the file written and the child stopped: the row of the key the file wants (§5) |
| `ui.TheValuesWantedFollowTheWorkingDirectoryTest.theEndOfAnotherProcesssHoldReadsTheFile` | the window opened on no file, a holder line of another live process seen held, the file written and the process ended: the row of the key the file wants (§5) |
| `ui.TheProfileWindowTest.theSectionAndTheWindowsTitleCarryTheBadge` | changed: the file is written once the window is open (Consequences) |

## What the implementation owes

The four readings of §1, in §2's order on a change, and the badges redrawn after a reading that draws no row (§6), with each test above green; nothing else moves.

## What this does not decide

- **Whether a value typed into a row of `wanted` and not saved is asked about on a change of working directory**, as ADR-389 §4 asks about the Profile window's form (§2).
- **Whether a row of the list and the rows of `wanted` read one source.** A row's next value is read from `report.json` (ADR-393 §5), the rows from `next-invocation.properties`; after an invocation that writes the one and not the other they can differ.
- **The words of the line above the rows.**
