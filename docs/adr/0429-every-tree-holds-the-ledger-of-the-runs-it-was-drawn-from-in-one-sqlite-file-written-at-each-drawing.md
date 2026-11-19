# ADR-429 — Every tree holds the ledger of the runs it was drawn from, in one SQLite file written at each drawing

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: by the operator, on [#900](https://github.com/algernon28/vespera/issues/900): that each tree carries its own ledger, the ledger being `CONTEXT.md`'s, the four records of [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §1 for the runs the tree was drawn from, with the rows its topics, clusters and writing were drawn from (§3); that it is a file of its own written as the tree is drawn, the working directory's database staying the store every invocation reads and writes (§1); that it holds the whole of those runs, their verdicts included, so the paths of the files they removed lie in it (§3); that it is not named `vespera.db` (§2); and its form, one file in SQLite's rollback journal written beside its place and moved into it, after the marker, and written again whole when the same run's tree is drawn again (§2). Under the operator's delegation on #900: that nothing in Vespera reads it back (§5); that every tree holds one, at a named root or not (§2); that no tree drawn before this record gains one but by a later drawing (§6); that one class writes it, naming the tables it holds, as an exception to ADR-209 §3 (§4); and that it is written without assuming the working store is a SQLite file (§4).
- **Amends**: ADR-209 §3, *"a table is named in SQL only by the module that owns it"*, with ADR-198's one exception: a second class, `deliverable.TreeLedger`, names the twenty-four tables the tree's ledger holds, in statements that read and nothing else (§4). It carries a note at its head.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1, the files a named root holds, which gain the tree's ledger; and §3, *"A named root holds its own tree and nothing else"*, where its own tree now includes its ledger, written after the marker and over itself at each drawing (§2). Its checks at the start and at the drawing are unchanged. It carries a note at its head.
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), the database and the profile in the working directory; [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) and [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), a run continued or minted and a value put back, all read from the working store; [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), every run's rows kept there and no `VACUUM`; [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) and [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md), the lock and the write-ahead log of the working store; [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), every link of the tree resolving with no database; ADR-274 §5, the tree keys no run; [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md), a tree closed to agents from its marker down; [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md), its script and its pin; [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md) §1, the top level's files named by the code; [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §12 and [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §7, a tree redrawn and a rendering published from the working store.
- **Settles**: [#900](https://github.com/algernon28/vespera/issues/900).

## Context

**What the working directory's database holds.** One `vespera.db` holds every walk and every run a working directory has had (ADR-115, ADR-156), every row of them kept (ADR-229), and three things keyed by no run at all: `extraction_cache` by content and extractor (ADR-070), `chunk_cache` and `vector` by chunk and instrument (ADR-085, ADR-214), and the relevance labels a person gave, by path and seed set (ADR-097). [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) says it plainly: *"A working directory holds what no run can make again."* A value put back finds its run's work recorded there (ADR-156 §2); each cost line the control panel shows for a profile row says what of it is kept ([ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4).

**When a generation run exists.** Every start opens the database before anything else (ADR-177 §1), and every stage before 6b, `vespera label` and the approval of the arrangement read and write it. A generation run's id, which names the tree's folder where no root is named, exists only once the arrangement is approved and 6b begins. So no store can lie in that folder before 6b, and a database there could only be written from the working store, or replace it at 6b and leave the next invocation with nothing to continue.

**The schema as it is.** `schema.sql` creates forty-nine tables: `ledger` owns ten, `corpus` six, `extraction` six, `similarity` six, `embedding` twelve and `synthesis` nine. The ledger's four records are `Walks` (`walk`), `Occurrences` (`file_occurrence`), `Runs` (`run`, `run_upstream`, `finished_step`) and `Verdicts` (`verdict`) (ADR-209 §1); `ledger` also owns `schema_version` and the three tables the folders of a walk are recorded in, `walk_folder`, `walk_folder_not_walked` and `walk_folder_not_entered` (ADR-334, ADR-340). Since ADR-409 the rows a tree is drawn from are kept in `topic`, `topic_passage`, `seed_feeding_no_topic`, `winning_topic`, `see_also` and `cluster_member` under the scoring run, `arranged_cluster`, `topic_name` and `subtopic_name` under the arrangement run, and `cluster_synthesis_doc`, `cluster_call_exemplar`, `standing_cluster_fault`, `topic_overview`, `topic_overview_source` and `topic_overview_fault` under the generation run; `synthesis_doc`, `cluster_fault` and the three others ADR-409 replaced are no longer created.

**What a drawing reads that is no row of those.** Each survivor's pictures are read, at every drawing, out of the converter's whole response in `extraction_cache` (`GenerationTasklet`'s pictures of a survivor, ADR-149 §9). A file that let a tree be drawn again without the working store would have to carry those responses, the extracted text of every document drawn.

**The name `vespera.db`.** It is how a working directory is known wherever it lies (ADR-196, `CONTEXT.md`'s **Working directory**); it is what the counting command of ADR-212 is admitted by, before any path of the command is read and so before a tree's marker is asked (ADR-215 §10); and a `--db-dir` naming a folder that holds one opens it as the working store, runs `schema.sql` in it and puts `vespera.lock` beside it.

**The guard.** The installed private-paths guard refuses a tree from its marker down (ADR-283 §2): its refusal names that record.

**The working store's engine.** [#899](https://github.com/algernon28/vespera/issues/899) asks whether the working store may later be another database, reached by a connection string; it is undecided, and SQLite stays the store.

## Decision

### 1. Every tree holds its tree ledger, and the working directory's database stays the store

**Every tree Vespera draws holds, at its top, its tree ledger: the ledger's rows of the walk and the runs the tree was drawn from, and the rows its topics, clusters and writing were drawn from, in one SQLite file.** The working directory's database stays where ADR-054 puts it and stays the one store: every invocation opens it, every stage reads and writes it, and a run is continued, minted or arrived at again from it, as before. The tree's ledger is written from it, as the tree is drawn, and is never written to by anything else.

### 2. The file

- **Its name is `vespera-run.db`**, `TreeLedger.FILE_NAME`, directly at the tree's top beside `index.html` and the marker. It is not `vespera.db`, so nothing that knows a working directory by that name takes a tree for one, ADR-212's counting command is not admitted over it, and a `--db-dir` naming a tree opens nothing of it.
- **One SQLite file in the rollback journal**, `PRAGMA journal_mode = DELETE`, closed when written, so that nothing lies beside it: no write-ahead log, no shared-memory file and no journal. It can be carried alone, and ADR-180 §4's local disk binds the working store and not it.
- **Written beside its place and moved into it.** It is written to `vespera-run.db.part` beside its place (`Deliverable.PART_SUFFIX`) and moved over any file of its name by ledger's `ReplacingMove` (ADR-242), so a reader of the tree never finds it half written.
- **After the marker.** It is written once `Deliverable.writeTo` has returned the tree, so the marker of this working directory and this generation run is already at the top (ADR-276 §3), on every path that draws the tree: the one that writes it, and the one that draws a recorded generation step's tree again (ADR-333 §12). A drawing refused at the drawing (ADR-276 §3) draws no tree and writes no tree ledger.
- **Written again whole** at every drawing of the same run's tree, over the one there: what it holds is what the working store holds at that drawing, by §3's rule, and nothing an earlier file held.
- **Every tree, at a named root or not.** A named root accepted at the start holds the marker of this working directory, so the file beside it changes no check; at the drawing it holds this run's tree, the file among it.
- **Its tables** are created by the statements `schema.sql` holds for them, so each has the columns the working store's has, and no index is built. Foreign keys are not checked as it is written; every reference its rows hold resolves within it.

### 3. What it holds

**Twenty-four tables, and in each the rows of the walk or the runs the tree was drawn from.** The runs the tree was drawn from are its generation run and every run reached from it through `run_upstream`, however many steps back: one run of each of the eight stages. Their walk is the one each of them is over.

| Tables | Owner | Rows |
| --- | --- | --- |
| `walk` | `ledger` | the walk's row |
| `walk_folder`, `walk_folder_not_walked`, `walk_folder_not_entered`, `file_occurrence` | `ledger` | the walk's rows, every occurrence of the walk included |
| `run` | `ledger` | the rows of the runs the tree was drawn from |
| `run_upstream`, `verdict` | `ledger` | the rows under those runs, every verdict included, whether it removes or not |
| `finished_step` | `ledger` | the rows under those runs, but the generation run's own |
| `topic`, `topic_passage`, `seed_feeding_no_topic`, `winning_topic`, `see_also`, `cluster_member` | `embedding` | the rows under those runs |
| `arranged_cluster`, `topic_name`, `subtopic_name`, `cluster_synthesis_doc`, `cluster_call_exemplar`, `standing_cluster_fault`, `topic_overview`, `topic_overview_source`, `topic_overview_fault` | `synthesis` | the rows under those runs |

**It never holds the generation run's own `finished_step` row.** The file is written inside the generation step, once the tree is drawn and before the step records itself finished, so the working store holds no such row at a first drawing and holds one when a finished run's tree is drawn again (ADR-333 §12). Leaving it out makes the file the same on every drawing of a run. Nothing is lost by it: a tree with its ledger at its top was drawn, and the generation step draws it.

**So the paths of the files those runs removed lie in it, with the verdict that removed each and its reason.** The operator accepts that they travel with the tree.

**It holds nothing else of the working store**: no row of another walk or of a run the tree was not drawn from; not `schema_version`; not `walk_anomaly`, nor the tables stages 1 to 4 and the seed measurement keep their measurements in; not `relevance_score`; not `extraction_cache`, `chunk_cache` or `vector`; not `relevance_label` or `relevance_label_provenance`. So it carries no text read out of a document: the paths, the judgements and the writing over the documents, and none of their words, which lie in the tree's copies alone. A tree's ledger is the record of what the tree was drawn from, and none of those is needed to read that record; the caches and the vectors are keyed by no run, and the labels are answers a person gave about documents, not a tree's.

### 4. How it is written

**One class, `deliverable.TreeLedger`, writes it.** It is built from the application's `JdbcTemplate`, the working store's, and writes the file through a connection of its own to the part file.

- **It reads rows, and never the working store's file.** Each of the twenty-four tables is read through the application's connection, by a statement that names the table and selects the rows of §3, with no `ORDER BY`, row after row through a row callback, each row written to the file as it comes, in transactions of a thousand rows; no class holds the rows of a table (ADR-211, ADR-220), and no statement sorts (ADR-218). It never opens, attaches, vacuums into, backs up or moves the working store's file, and names no statement only SQLite knows against it.
- **So it holds whatever engine the working store is.** It assumes only that the working store answers SQL through the application's connection. Should a later record, #899's, let the working store be another database, the tree's ledger is still one SQLite file written this way, and this record does not change.
- **It names the tables of three modules, which ADR-209 §3 forbids but for its exceptions, and it is the second.** `deliverable` owns no table. A tree's ledger holds `ledger`'s, `embedding`'s and `synthesis`'s tables, and a writer in each of those modules would move the runs of every stage whose version names it: `embedding` and `synthesis` name seed measurement to 6b, so 6b would write every cluster again with its model, and the operator approve the arrangement again. `deliverable` is named by no stage's version, so the class moves no run (§7). Its exception is bounded as ADR-198's is: it names those twenty-four tables and no other, each in a statement that reads, beginning `SELECT` or `WITH`, and holding no word that writes.
- **A failure to write it fails the drawing**, as a tree that cannot be written does (ADR-276, *What this does not decide*): the generation step ends failed, its recorded rows stand (ADR-111), and the next invocation draws the tree and writes the file again. ADR-276 §4's gibibyte is read before each copy, not before this file.

### 5. Nothing in Vespera reads it back

- **The working store is what everything reads.** A tree drawn again is drawn from the working store's rows (ADR-333 §12); a publish takes its run from the last `vespera run`'s report and checks it in the working store (ADR-393 §7); `vespera label`, the control panel and every stage read the working store alone. No class but `TreeLedger` names `vespera-run.db`.
- **So it does not carry what only a drawing needs**: the pictures in `extraction_cache` (Context) are not in it, and a tree cannot be drawn again from its ledger alone.
- **No publish carries it.** A publish delivers only what a plugin's list names, `pages.json` or `files.json` (ADR-312 §5, ADR-333 §7), and neither names it; the website lies in the working directory, never in a tree (ADR-333 §7). ADR-308 §1's *"not the database"* holds of it too.
- **No agent reads it.** It lies in a tree, which ADR-283 closes from the marker down, and the written rule of ADR-283 §1 reaches it. The guard does not change. ADR-212's counting command still counts the working store; its `folder=deliverable/<run id>` line counts the file's bytes with the tree's, and a tree at a named root is counted by no line, as before.

### 6. No tree drawn before gains one but by a drawing

A tree gains its ledger when it is drawn. A tree no later invocation draws, its generation run never arrived at again, holds none, and nothing writes one into it. A working directory needs nothing done to it: its database is the store as it was.

### 7. No run id moves

`TreeLedger` lies in `deliverable`, and its caller, `GenerationTasklet`, in `pipeline`. `StageModules` names `corpus` for stage 1, `extraction` and `similarity` for stages 2 to 4, `embedding` and `extraction` for both runs of stage 5, and `synthesis`, `extraction` and `embedding` for 6a and 6b: neither `deliverable` nor `pipeline`. No profile value and no preset is read, so no run's `config_consumed` moves. The tree keys no run (ADR-274 §5), and its ledger keys none.

### 8. What the documents say

- **`CONTEXT.md`** gains **Tree ledger**; **Ledger**, **Deliverable root** and **Publication-ready artifact** each name it in a sentence.
- **`AGENTS.md`** lists ADR-283 among the records the installed guard enforces, in place of *"once the operator installs its draft"*.
- **README**'s "Where things live" and **`docs/architecture.md`**'s 6b are the implementer's, in the change that writes the file: `docs/check-claims.mjs` reads every `FILE_NAME` constant against README, so README names `vespera-run.db` in the same change as the constant.

## Alternatives refused

- **The working directory's database moved into the tree at 6b, one per generation run.** Every stage before 6b, `vespera label` and the approval need a store before any generation run exists; the next invocation would find none to continue, or choose the folder written last, the recency ADR-099 and ADR-154 refused. It would walk, convert and embed everything again (ADR-229 §4), lose the labels a person gave and the vectors unless they were carried, and make false what the control panel's cost lines promise is kept (ADR-377 §4).
- **The whole database written into the tree at each drawing.** It keeps every promise and costs the file's size for each tree, 16.7 GB on a portable spinning disk (ADR-191); a whole file of a store in use needs SQLite's backup, or `VACUUM INTO`, which ADR-229 §3 refuses; and the tree, which is handed over, would carry the extracted text of every document, those removed and those of any other corpus root the working directory walked (ADR-393, Context).
- **6b's own rows kept only in the tree.** SQLite holds no foreign key across two files, ADR-209 §4 keeps one schema, the file would exist before the marker at a named root and a generation stopped partway would leave the root refused at the next start (ADR-276 §3), and deleting a tree would have the model write every cluster again.
- **Naming it `vespera.db`.** Context: a tree would read as a working directory, the counting command would open a file inside a tree, and a `--db-dir` naming a tree would write into it.
- **Carrying `extraction_cache` so that a tree can be drawn again from its ledger.** It would carry every survivor's whole extracted text, and nothing reads the file back.
- **The survivors alone, without the occurrences and verdicts of the files removed.** A ledger is what exists and what was judged; the operator chose the whole.
- **A writer in each owning module.** It keeps ADR-209 §3 whole and moves the runs of seed measurement to 6b: every cluster written again, and the approval given again.
- **Attaching the file to the working store and inserting from one into the other.** `ATTACH` is SQLite's, and the writer would depend on the working store being a SQLite file (§4).
- **Written before the first copy, with the marker.** A drawing stopped partway would leave a file describing a tree that is not there; written last, its presence says the tree it describes was drawn.

## Consequences

- **A tree says what it was drawn from, in a file anyone may open with SQLite**: every file the walk found, every judgement made on the way and why, and the topics, the clusters and the writing.
- **A tree is larger by that file**, which grows with the walk's occurrences and the runs' verdicts, and not with the archive's bytes.
- **Nothing an invocation, a publish, `vespera label` or the control panel does changes**, and no promise the control panel makes changes.
- **ADR-209 and ADR-276 carry a note at their heads.**

## What this does not decide

- **Whether the working store may be another database** (#899): the file is written as §4 says whatever it decides.
- **Whether anything reads a tree's ledger**, a control panel's view of a tree among them.
- **Whether a tree drawn before this record gains one without being drawn again.**
- **Whether the file is bounded in size, or its size said at the drawing.**

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheTreeLedgerInvocationTest.aDrawnTreeHoldsTheLedgerOfItsRunsAtItsTop` | §1 to §3: over a corpus whose arrangement is approved, with a verdict that removes nothing written under the first stage's run and a removal under a run of the walk no tree is drawn from, the invocation that draws the tree at a named root exits 0; the tree holds `vespera-run.db` at its top and nowhere else, nothing beside it, and no `vespera.db` or `vespera.lock`; the runs drawn from are one of each of the eight stages; the file is in the rollback journal, holds exactly the twenty-four tables, the walk alone, exactly those runs, and in each table the working store's rows by §3's rule, whole, `finished_step` without the generation run's own row, of which it holds none; the verdict under the first stage's run is in it and the removal is not; it holds clusters and the writing over them; `PRAGMA foreign_key_check` finds nothing. The test profile's working store is in memory, so the file can only have been written from rows (§4) |
| `pipeline.TheTreeLedgerInvocationTest.aTreeDrawnAgainHasItsLedgerWrittenAgainWhole` | §2: a table left in the file between two drawings of the same run's tree is gone after the second, which exits 0, its root accepted at the start and at the drawing; nothing is left beside the file; it holds the twenty-four tables, the same runs, and the walk's occurrences, whole; and no `finished_step` row of the generation run, though that run's step is now recorded finished in the working store (§3) |
| `pipeline.DeliverableInvocationTest.copiesEverySurvivorIntoTheTreeAndLeavesTheArchiveAsItWas` | §3: no file of the tree but the copies holds a document's text, each file's bytes searched for the text's UTF-8 bytes, so the tree's ledger is searched as a page is |
| `EachTableIsNamedOnlyByItsOwnerTest.noClassNamesATableAnotherModuleOwns` | §4: no shipped class names another module's table, but the invocation account's seven and `deliverable.TreeLedger`'s twenty-four |
| `EachTableIsNamedOnlyByItsOwnerTest.theClassThatWritesATreesLedgerOnlyReadsTheTablesItHolds` | §4: `deliverable.TreeLedger` ships; it names exactly the twenty-four tables of §3; each of its statements naming one begins `SELECT` or `WITH` and holds no word that writes; and no text of it attaches, detaches, vacuums, backs up or restores a database |

`PipelineHoldsOnlyTheRulesOnRecordTest` lists `deliverable.TreeLedger` among `GenerationTasklet`'s collaborators, as holding no rule.

**Not pinned, and why**: that no publish carries the file, since a publish carries only what a plugin's list names (§5), and the first test holds that no rendering's folder holds one; that nothing reads it back, which is an absence no test can see beyond `TreeLedger` alone naming it; that a failure to write it fails the drawing, which is a tree that cannot be written, as ADR-276 leaves it.

## What the implementation owes

- **`io.algernon.vespera.engine.deliverable.TreeLedger`**, public: `public static final String FILE_NAME = "vespera-run.db"`; `public TreeLedger(JdbcTemplate workingStore)`; `public Path writeInto(Path tree, RunId generationRun)`, which writes `<tree>/vespera-run.db` as §2 to §4 say and returns its path. Its table statements are read from `schema.sql` on the class path; the statements it runs against the working store are built from the table's name and §3's condition, and none of its texts holds a word of `ATTACH`, `DETACH`, `VACUUM`, `BACKUP` or `RESTORE`.
- **`GenerationTasklet`** building one from its `JdbcTemplate` and calling `writeInto(tree, generationRun)` once `Deliverable.writeTo` has returned, on both of its paths that draw the tree.
- **README**'s "Where things live" naming `deliverable/<run>/vespera-run.db`, and **`docs/architecture.md`**'s 6b naming the tree's ledger (§8).
- The notes at the heads of ADR-209 and ADR-276 are this record's, and stand.
- The tests above passing, and every other test as it is.
