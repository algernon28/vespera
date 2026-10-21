# ADR-306 — The control panel reads a holder line of its own process as held by nobody, and serves the cluster pages' sizes as a sixteenth report

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Settles**: two rulings of the operator's on [#629](https://github.com/algernon28/vespera/issues/629), the build of ADR-302's screens: a holder line of `vespera.lock` naming the control panel's own process is held by nobody on its pages and in its start checks; and `cluster-page-sizes.html` is served as the sixteenth report and linked for `clusterPageByteCeiling`. §3, where the key's link stands, is the reading the records leave.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §2, whose holder line of `vespera.lock` is held as ADR-278 §4 reads it: one naming the control panel's own process is held by nobody (§1); §3, whose fifteen reports are sixteen, `cluster-page-sizes.html` last (§2); §8, whose table of `report-<key>` links gains `clusterPageByteCeiling`, and whose `/profile` form of a key gains `report-<key>` (§2, §3); §9, whose `/profile` row gains `report-<key>` (§3); §10, whose `REPORTS` holds sixteen names (§4); and its Tests, whose methods holding a held working directory fake another live process (Tests). It carries a note at its head.
- **Keeps**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §1: every write of the control panel's takes the working-directory lock, writes its holder line from byte 0, `command=control-panel write <file name>`, and leaves the line in the file when it releases the lock; and the control panel starts no invocation while it holds the lock.
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §4, as amended by ADR-285 §5: what makes a line held, its pid alive and that process started no later than `started` plus 1 second, and a line that does not parse held, shown whole. `HolderLine` and `HolderReading` keep that rule; §1 says which line the pages ask it of.
- **Keeps**: [ADR-279](0279-one-vespera-run-per-corpus-root-by-a-lock-on-a-file-named-for-its-real-path-outside-every-working-directory.md) and ADR-302 §2's reading of the corpus-root lock, which the control panel never takes: its holder line is read as before, whatever pid it names.
- **Keeps**: [ADR-303](0303-a-clusters-page-past-the-profiles-byte-ceiling-is-split-at-an-entrys-end-into-pages-and-every-drawing-writes-the-pages-sizes.md) §2 and §9: `clusterPageByteCeiling` is never a gate and never among `wants`, and enters no run; and §8: stage 6b writes `cluster-page-sizes.html` in the working directory at every drawing, numbers only, and `profile.yaml` holds no pointer to it.
- **Keeps**: ADR-302 §3's rules for every report: a fixed path, the file of that name in the working directory held, `sandbox allow-scripts`, a `404` of one line where the file is not there, and a link opening in a tab of its own.
- **Rests on**: a reading of `controlpanel.HolderLine`, `HolderReading`, `ControlPanelState`, `LaunchPages`, `NextPages`, `ValuePages`, `ControlPanelPages` and `WorkingDirectoryWrite`, of `deliverable.Deliverable` and `ClusterPageSizes`, and of the README's table of values. No archive, working directory or tree was opened for this record (ADR-196, ADR-283).

## Context

**The control panel's own line stays.** Every save on `/`, `/next`, `/labels`, `/code-sets` and `/profile` is one of ADR-295's writes. It takes the working-directory lock, writes `pid=<the control panel's pid> started=<time> command=control-panel write <file name>` into `vespera.lock`, and releases the lock with the line left in the file, as a holder's line is left (ADR-295 §1). `WorkingDirectoryWriteTest` holds the line there after the write.

**And it reads as held.** ADR-302 §2 reads `vespera.lock`'s holder line as ADR-278 §4 reads it: held while its pid is alive and started no later than `started` plus 1 second. The control panel's own process is alive while it serves, and started before any line it writes. So after any save the launch page shows the working directory held in `holder-working-directory` and disables Run and Label, and `run`, `label`, `label-auto`, `save-then-run` and `save-then-label` are refused with the line of a held `vespera.lock`, until the control panel is started again. `save-then-run` writes before it starts, so it is refused every time it is pressed.

**When the control panel holds the lock.** It holds the lock only inside one of its own writes. Its writes are made one at a time, each ending before the request that made it is answered, and it starts no invocation while it holds the lock (ADR-295 §1). No page is answered, and no start is checked, while it holds the lock.

**A report the list does not have.** ADR-303 §8 has stage 6b write `cluster-page-sizes.html` in the working directory at every drawing, its name `deliverable.ClusterPageSizes.FILE_NAME`, public as `Deliverable.CLUSTER_PAGE_SIZES_FILE_NAME`. The README's table of values names it as the report `clusterPageByteCeiling` is read off. ADR-302 §3 serves fifteen reports, and its Consequences say a report a later stage adds is not served until §3's list has it. `TheControlPanelServesEveryReportTheJarWritesTest` reads the report names of `pipeline` alone, and does not see it.

**Where the key is set.** ADR-302 §8 shows `report-<key>` beside a key of the form `next` alone, and that form holds only the keys of `wants`. `clusterPageByteCeiling` is never among them (ADR-303 §2), so it is set only on `/profile`, whose form of a key shows no report. ADR-302 §8 says a key the README's table gains a report for gains it in the same change; it names no page but `/next` for it.

## Decision

### 1. A holder line of the control panel's own process is held by nobody

**On the control panel's pages and in its start checks, a holder line of `vespera.lock` whose pid is the control panel's own process is read as held by nobody.** That process holds the working-directory lock only inside one of its own writes, and a write ends before the request that made it is answered, so no page of that request can find it holding the lock. The server answers each request on a thread of its own, so every write is made under the one monitor every start check is made under: a start posted while a write holds the lock waits for the write to end, and no start check finds the control panel's own write holding it (ADR-295 §1).

- **On `/`**, `holder-working-directory` is empty for such a line, and it leaves `run` and `label` enabled and links no progress page. The control panel's own child running, or another line held, disables them as before.
- **In the start checks of ADR-302 §2**, of `run`, `label`, `label-auto`, `save-then-run` and `save-then-label`, such a line gives no refusal. The order of the checks, the control panel's child first, then `vespera.lock`, then the corpus-root lock, and their lines stand.
- **The pid alone decides.** A line naming the control panel's pid that the control panel did not write cannot be told apart from one it wrote, and is read the same way. Its `started` and `command` are not read for it.
- **Every other line is read by ADR-278 §4's rule**, unchanged: a line naming another live process started no later than `started` plus 1 second is held, a line naming a process that ended is not, and a first line that does not parse is held, shown whole.
- **The corpus-root lock is unchanged.** The control panel never takes it (ADR-295 §1), so its holder line is the jar's alone and is read as ADR-302 §2 reads it, whatever pid it names.
- **The line stays in `vespera.lock`.** Nothing removes or rewrites it after a write (ADR-295 §1); the jar's next lock truncates it as before.

### 2. The sixteenth report, `cluster-page-sizes.html`

**`/report/cluster-page-sizes.html` is served, answered with `cluster-page-sizes.html` of the working directory held**, the sixteenth of ADR-302 §3's reports and the last in its list, under every rule of §3 that every report is served under.

ADR-302 §8's table of `report-<key>` links gains one row, after `arrangementApproved`, in the order of the README's table:

| Key | `report-<key>` links |
| --- | --- |
| `clusterPageByteCeiling` | `/report/cluster-page-sizes.html` |

The report stays one no gate reads, and `profile.yaml` holds no pointer to it (ADR-303 §8).

### 3. `/profile` links each key's reports

**On `/profile`, the form of each key holds `report-<key>` where §8's table names a report for the key**, the same list as `/next` shows for it: a link to each report, in the table's order, each opening in a tab of its own. A key the table names no report for has no `report-<key>`.

This is the reading the records leave. ADR-302 §8 links a key's reports beside the control that sets it, so the report is read before the value is chosen, and names `/next` alone for it because every key it then linked could be wanted. `clusterPageByteCeiling` never is, so on `/next` its row would link nothing; `/profile` is where it is set. One table, shown beside a key wherever the key is set, adds no second rule for one key, and a key set on `/profile` when no run wants it is read off the same reports as when one does.

### 4. Where it is written

All in `io.algernon.controlpanel`, linking no class of the jar:

- **`ControlPanelPages.REPORTS`** holds sixteen file names, `cluster-page-sizes.html` last, and `pages` maps `/report/cluster-page-sizes.html` with them.
- **`ControlPanelState.write`** is the one way the pages write a file of the working directory, `synchronized` on the `ControlPanelState`, as `start` is.
- **`ControlPanelState.holders()`** reads a line of `vespera.lock` whose `pid` is `ProcessHandle.current().pid()` as held by nobody; its reading of the corpus-root lock is unchanged. `HolderLine.held()` and `HolderReading` are unchanged.
- **`NextPages.REPORTS_OF_A_KEY`** gains `clusterPageByteCeiling` → `cluster-page-sizes.html`.
- **`ValuePages`**' form of a key on `/profile` writes `report-<key>` from the same table, in the list `/next` writes.

### 5. No run id moves

No file under `src/main/java/io/algernon/vespera` changes, and no dependency or plugin is added. The control panel names no stage's version.

## Alternatives refused

- **Deleting or emptying the holder line when a write ends.** ADR-295 §1 leaves the line, as the jar leaves its own, and `WorkingDirectoryWriteTest` holds it; a line rewritten after the lock is released could overwrite the holder line of an invocation that took the lock in between.
- **Reading the line by its `command=control-panel write`.** The line's text is not the control panel's to vouch for; the pid is what names the one process that cannot be holding the lock while it answers.
- **Telling a line the control panel wrote from one of its pid it did not.** Nothing in the file tells them apart, and the control panel keeps no record of its own lines.
- **Reading the corpus-root lock the same way.** The control panel never takes it, so a line of its pid there is none of its writes.
- **Changing `HolderLine.held()`.** The rule of ADR-278 §4 is a rule of the line, the same for every reader; which line the pages ask it of is the pages' choice.
- **Leaving `cluster-page-sizes.html` unserved.** The README sends the operator to it for a key the control panel sets, and the operator could not open it from there.
- **Linking the key's report on `/next` alone.** The key never enters `wants`, so the link would never be shown.
- **Linking `report-<key>` on `/profile` for `clusterPageByteCeiling` alone.** It would make a second rule, for one key, beside the table's.

## Consequences

- After a save on any page, the launch page shows the working directory held by nobody, Run and Label stay enabled, and `save-then-run` and `save-then-label` start their child.
- A `vespera run` started by hand while the control panel serves still finds `vespera.lock` free between its writes, and is refused by ADR-177 §1 while one is made (ADR-295 §1).
- The operator opens `cluster-page-sizes.html` from the launch page's `reports`, from `/invocation`, and beside `clusterPageByteCeiling` on `/profile`.
- Every key on `/profile` that a report informs carries its links there too.
- No run id moves (§5).

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.LaunchScreenTest.aSaveOfTheControlPanelsOwnLeavesTheWorkingDirectoryHeldByNobody` | after a run ends with 0 and `save` on `/next` writes into the working directory, `vespera.lock` holds a line naming the control panel's pid; `/` shows `holder-working-directory` empty and `run` and `label` enabled; `save-then-run` then lands on `/invocation` with a child started (§1) |
| `controlpanel.ControlPanelStateTest.noChildIsStartedWhileAWriteHoldsTheWorkingDirectoryLock` | a start asked for on a second thread while a write's change waits inside the working-directory lock waits on the `ControlPanelState`, unreturned, until the write ends, and only then starts the child (§1) |
| `controlpanel.ReportScreenTest.eachReportIsServedAsWrittenInASandboxAtItsOwnPath` | each of the sixteen, `cluster-page-sizes.html` among them, as ADR-302 §3 serves a report (§2) |
| `controlpanel.ProfileScreenTest.eachKeyIsLinkedToTheReportsItIsReadOffAndThePageSizesToTheirReport` | with no next act read, the form of every `ProfileKey` holds `report-<key>` exactly where the table names reports for it, those links in its order, each in a tab of its own; `report-clusterPageByteCeiling` holds one link, to `/report/cluster-page-sizes.html` (§2, §3) |
| `controlpanel.TheReportLinksAreTheReadmesTableTest.eachKeysReportLinksAreTheReportsTheReadmesTableNamesForIt` | ADR-302's owed test: §8's table, key by key, equals the README's table of values, each key's reports in the README's order, a key whose row names none having none (§2) |
| `pipeline.TheControlPanelServesEveryReportTheJarWritesTest.everyReportTheJarWritesIsListedAndServedByTheControlPanel` | the report file names of `pipeline`'s and `deliverable`'s classes, the tree's `index.html` not among them, are sixteen, and the control panel lists and serves each of them and no other (§2) |

**Holding a held working directory or corpus root as a line naming another live process**, the tests' JVM's parent, which started before it and is alive while it runs, so that the line is held by ADR-278 §4's rule:

- `controlpanel.LaunchScreenTest.runAndLabelAreDisabledWhileAnotherInvocationHoldsTheWorkingDirectory`;
- `controlpanel.LaunchScreenTest.runPressedOnceTheWorkingDirectoryIsHeldIsRefusedWithALineOfItsOwn`;
- `controlpanel.LaunchScreenTest.runAndLabelAreDisabledWhileAnotherInvocationHoldsTheCorpusRoot`, whose corpus-root lock §1 leaves as it was.

## What this does not decide

- **An invocation of the jar started by the control panel's own process**, which the control panel never is: its child has a pid of its own (ADR-278 §1).
- **Several control panels on one working directory.** Each reads another's line as ADR-278 §4 reads any other process's.
- **A report a later stage adds**, which is served once ADR-302 §3's list has it, as before.
- **The words and layout of `/profile`**, which no test holds.
