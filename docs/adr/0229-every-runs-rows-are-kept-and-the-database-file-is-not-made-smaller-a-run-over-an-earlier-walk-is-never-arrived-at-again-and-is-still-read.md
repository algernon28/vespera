# ADR-229 — Every run's rows are kept and the database file is not made smaller; a run over an earlier walk is never arrived at again, and is still read

> **Partly amended — see [ADR-230](0230-the-clusters-under-a-scoring-run-are-of-its-survivors-as-they-stand-and-the-arrangements-identity-names-the-removals-standing.md).** Two sentences, and nothing this record decides. §1 calls the shipped deletes by a run *"a step discarding its own unfinished work under its own run (ADR-116)"*: two are of work that was finished, the floor's step withdrawing a finished invocation's removals on every invocation (ADR-118) and, as of that record's build, the clustering step forming its clusters again. Each is still a step discarding its own rows under its own run. And Consequences' *"A table that gains or loses a reference to `run` fails `EveryTableKeyedByARunIsOnRecordTest`"* holds only of a reference written `run_id TEXT NOT NULL REFERENCES run (id)`, as *What no test holds* says. Everything else in this record stands.

- **Date**: 2026-10-10
- **Status**: accepted.
- **Built**: nothing is to be built. This record changes nothing under `src/main`; its tests pass as written.
- **Amends**: [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md) in what it left to [#481](https://github.com/algernon28/vespera/issues/481). §7's *"Which runs can never be arrived at again, what removing their rows costs, and whether the file should be made smaller, are [#481](https://github.com/algernon28/vespera/issues/481)'s"*: §1 and §2 here say which runs, the ticket's body carries the cost of a delete and of `VACUUM` as #468's probes measured it, and §3 here says the file is not made smaller. §7's *"the only deletes are a step discarding its own unfinished work under its own run (ADR-116)"*: it holds of every shipped delete from a table keyed by a run but one, the delete of `relevance_label_provenance` by path and seed set (§4). Its *Alternatives refused*, in *"the reading of the code is that nothing arrives at them again; that, and the cost measured for a delete, are in #481"*: a test now holds it, with what §2 adds. Its Consequences' *"Nothing gives that back until #481 is decided"*: #481 is decided and nothing gives it back. And the first item of its *What this does not decide*, *"Whether an earlier run's rows are ever removed, by what, and whether the file is made smaller"*: they are not removed, by anything, and the file is not made smaller. Its decision that the rows stay, its measurements and its index are not touched.
- **Amends**: `AGENTS.md`, in *"whatever rows of earlier runs `shingle` keeps, which nothing removes ([#481](https://github.com/algernon28/vespera/issues/481))"*: the ticket is closed by this record, which the sentence now cites.
- **Keeps**: [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), whole: a verdict under a run not arrived at stays recorded, and a value put back arrives at its run again and finds its work done. [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md), by which a step discards its own unfinished work under its own run. [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), by which a walk that observed what the walk before it recorded is discarded. [ADR-047](0047-the-pipeline-never-blocks.md), which sizes the CLI to two commands. [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md) and [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md): no statement is added, so no exception is. [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), by which a table is named in SQL only by its owner.
- **Rests on**: the choices of the session that wrote this record, made on 2026-10-10. The coordinating session handed it [#481](https://github.com/algernon28/vespera/issues/481) with the instruction to decide and not to ask, and gave as its ground the operator's word of that day, which it quoted as *"tell the agents to make all decisions regarding their tickets"*. The author did not hear the operator: the quotation is the coordinating session's. The choices under *Who decided* are the author's, and are not the operator's own answers. The code as it stands at `066040de`; the size of a kept row as ADR-221 measured it, and the cost of a delete and of `VACUUM` as #468's probes measured them and #481's body records them, all cited and none measured again; two tests written for this record. No archive and no working directory was opened ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#481](https://github.com/algernon28/vespera/issues/481).

**An *earlier walk* here** is a finished walk of a corpus root that is not that root's latest finished walk. **A table *keyed by a run*** is one whose rows carry `run_id` as a reference to `run`: there are twenty-six (§4).

## Who decided

The session that wrote this record, on 2026-10-10, under the hand-over stated above:

1. Every run's rows are kept. Nothing removes them: no stage, and no command.
2. The database file is not made smaller.
3. The growth is stated, table by table, and a test holds the tables.
4. A run over an earlier walk is recorded as never arrived at again by `vespera run`, on a test, and as still read by `vespera label`, on a test. It is not recorded as removable.
5. What would reopen this is named (§5), and the one removal that would give back what an unchanged archive accumulates is left to the operator, because it contradicts ADR-156 (*What this does not decide*).

## Context

ADR-221 made stage 4b's index one run's, so the room and the time of its build no longer grow with the stage-2 runs a working directory has had. What still grows is the database file: each run of a stage writes its rows beside the earlier runs', and nothing removes any. ADR-221 measured that at 277.5 bytes for each `shingle` row of each stage-2 run kept and 278.8 for each `signature_band` row of each stage-4 run kept, and left to #481 whether the rows of runs that can never be arrived at again are removed.

#481 asked three things: which runs those are, established by a test; the choice among keeping, removing and an operator-run command; and a record.

## Established

### Which runs `vespera run` never arrives at again

**Read in the code.**

- Every run is minted through `RunMint.mint`, at eight sites: seven in `StageRuns` and one in `ByteLevelReductionTasklet`. `Runs.startRun` has no other caller in `src/main`.
- At each site the walk is `RunMint.finishedWalk`, which is `Walks.finishedWalkFor`: the finished walk of the corpus root with the highest id.
- The walk's id is one of the four things `RunId.of` hashes. So a run over one walk is never the run of another.
- `walk.id` is `INTEGER PRIMARY KEY AUTOINCREMENT`, so an id is not given twice and a later walk has a higher one.
- The one delete of a walk is `Walks.discardWalk`, called by `WalkRecorder.discardIfNothingNewWasSeen` on the walk it has just finished, where that walk recorded what the finished walk immediately before it recorded (ADR-115). The walk before it is then the latest, as it was before the traversal began. Nothing deletes a walk that a run names.
- An unfinished walk is continued before another is started (`Walks.unfinishedWalk`), so two walks of one root are not in progress together.

So once a later walk of a root is finished and kept, an earlier walk is never the latest again, and no run over it is minted or continued by `vespera run`.

**Run, in `ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest`.** The sequence by which the first walk might have come back is an archive changed and changed back. Over a corpus of two texts and a seed set of one, with every gate open but the arrangement's approval:

| What happened before the invocation | Finished walks of the root | Runs over the first walk | Runs over the latest walk |
| --- | ---: | ---: | --- |
| nothing: the first invocation | 1 | 7 | the same 7 |
| one text added | 2 | 7 | 7, none of the first walk's |
| that text taken out again | 3 | 7 | 7, none of the first walk's |
| nothing | 3 | 7 | the same 7 as the row above |

- **The third walk recorded the same paths and sizes as the first and is not the first again.** It was compared with the second, which held the added text, and kept.
- **No run was added over the first walk after it stopped being the latest**, and every stage did its work again under a run of its own, three times.
- **Every row under the first walk's runs was still there after the two later invocations**, counted table by table over the twenty-six: none added, none removed.
- **The fourth invocation, with nothing changed, arrived at the third walk's runs and minted none.** That is what a run over the latest walk has and a run over an earlier walk does not.

A run over the latest finished walk of its root can be arrived at again, by a value put back or an earlier build installed: ADR-156 §2, held by `UpstreamRunOverAReusedWalkTest` and by `AnEarlierRunsRowsStayInvocationTest`.

### A run over an earlier walk is still read

`vespera label` mints no run and walks nothing. It reads the run the label file in the working directory names, which is the scoring run that wrote the file (`LabelIngestion`, `AutoLabelling`). It asks only that the database holds that run; it does not ask whether the run's walk is the latest.

**Run, in the same class's second test.** One invocation scores the corpus and writes the label file. One text is added, and the next invocation stops at stage 4's gate: the root has a second finished walk, no scoring run over it, and the label file still names the first scoring run.

- **`vespera label --auto` then completes with exit code 0.** It puts each question with the opening of its text, read from the conversion the first walk's stage-2 run recorded a key for, and records every answer under the first scoring run.
- **With the `extraction_cache_key` rows of that stage-2 run deleted by the test**, the same command fails with exit code 1 and puts no question: `ExtractionCacheKeys.requireForOccurrence` throws, the key it requires not being on record.

So for that run the command reads `run`, `run_upstream`, the `file_occurrence` rows of the earlier walk, and `extraction_cache_key` under the stage-2 run upstream of it. And `relevance_label` and `relevance_label_provenance` rows name the scoring run an answer was given under for as long as the answer is kept, which is for good ([ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md)).

### What each run's stage keeps

Which stages' runs hold rows in which table, after the first invocation of the first test, is held by that test for fourteen of the twenty-six tables. The other twelve hold no row in its fixture.

| A run of | Is minted anew, besides by a new walk or a new run upstream, by | Tables its rows are in |
| --- | --- | --- |
| byte-level reduction (1) | a build that moves `corpus`; the log floor, and the two constants recorded beside it, the size ceiling of a text and the rule for its parts | `content_hash`, `detected_format`; read and not run: `superseded_by` |
| extraction (2) | a build that moves `extraction` or `similarity`; the extractor identity, `degenerateOutputConfidenceFloor`, `extractionAttempt` | `shingle`, `extraction_metric`, `extraction_cache_key`; read and not run: `extraction_fault` |
| content census (3) | a build that moves `similarity` or `extraction` | `shingle_document_frequency`, `shingle_corpus_size`, `confidence_distribution` |
| content redundancy (4) | the same builds; the boilerplate floor | held by `AnEarlierRunsRowsStayInvocationTest`: `minhash_signature`, `signature_band`; read and not run: `redundant_with` |
| seed measurement (5) | a build that moves `embedding` or `extraction`; the seed folder's path | `extraction_metric`, `extraction_cache_key`, `seed_corpus_comparison`; read and not run: `unusable_seed` |
| embedding scoring (5) | the same builds; the embedding model's name, `relevanceScoreFloor` | `relevance_score`, `document_cluster` |
| arrangement (6a) | a build that moves `synthesis`, `extraction` or `embedding` | `cluster` |
| generation (6b) | the same builds or one that moves `profile`; the approved arrangement, the generation model, its weights' digest, the context window | not reached by the test, read: `synthesis_doc`, `call_exemplar`, `cluster_fault` |

- **Every run also has a row of `run`, its rows of `finished_step`, and, from extraction on, a row of `run_upstream`.**
- **`verdict` holds rows under the run of each stage that writes one.** Which stages do was not traced for this record, and the fixture writes none.
- **A new run of a stage is a new run of every stage after it**, each naming the run upstream of it in what it records. So a new stage-2 run is a further copy of every table from the second row down, once the invocations reach those stages.
- **`relevance_label` and `relevance_label_provenance` are keyed by path and seed set, not by run.** They name a run and do not grow by a copy for each.
- **Three tables are keyed by content and an instrument's identity and not by a run**: `extraction_cache`, `chunk_cache` and `vector`. A new run adds nothing to them. **`file_occurrence` and `walk_anomaly` are keyed by a walk**: a changed archive keeps the earlier walk's rows too.
- *Read and not run* is from the statements in `src/main` and the comments of `schema.sql`. The fixture's two texts share every shingle and its seed is usable, so no verdict, no signature and no fault is written in it.

## Decision

### 1. Every run's rows are kept

No stage and no command removes a row of a table keyed by a run because another run exists. This holds of a run over the latest walk of its root, which can be arrived at again (ADR-156), and of a run over an earlier walk, which cannot (Established).

The deletes that ship stay as they are: a step discarding its own unfinished work under its own run (ADR-116), and the two §4 names.

### 2. A run over an earlier walk is never arrived at again, and that does not make its rows removable

**`vespera run` never mints or continues a run over an earlier walk.** That is the ticket's reading of `RunMint` and `Walks`, and it is so (Established).

**It is not the same as the run never being read.** `vespera label` reads the run the label file names, whichever walk it is over, and needs that run's row of `run`; `vespera label --auto` also reads that run's stage-2 keys, and fails where they are gone (Established). So the set a removal could take is not *the runs over an earlier walk*. It is those less the scoring run the label file names and every run upstream of it, at the least; and the `run` row of any scoring run a kept answer names cannot go while the answer stays.

This record does not go on to define that set, because §1 removes nothing.

### 3. The database file is not made smaller

Nothing issues `VACUUM`, and the database is not opened with `auto_vacuum`. The file grows and does not shrink. Pages freed by the deletes of §1's last paragraph are used again by later rows, as ADR-221 measured of an index dropped and built again.

### 4. The growth, stated

**The twenty-six tables keyed by a run, by owner**, as `schema.sql` has them:

| Owner | Tables |
| --- | --- |
| `ledger` | `run_upstream`, `finished_step`, `verdict` |
| `corpus` | `content_hash`, `superseded_by`, `detected_format` |
| `extraction` | `extraction_metric`, `extraction_cache_key`, `confidence_distribution`, `extraction_fault` |
| `similarity` | `shingle`, `shingle_document_frequency`, `shingle_corpus_size`, `minhash_signature`, `signature_band`, `redundant_with` |
| `embedding` | `unusable_seed`, `seed_corpus_comparison`, `relevance_score`, `document_cluster`, `relevance_label`, `relevance_label_provenance` |
| `synthesis` | `cluster`, `synthesis_doc`, `call_exemplar`, `cluster_fault` |

**Each run of a stage adds a copy of that stage's rows** in the tables *Established* gives for it, and the earlier copies stay.

**What a copy costs in the file, where it was measured** (ADR-221, *What a kept run costs in the file*, on synthetic ledgers on a solid-state disk):

- 277.5 bytes for each `shingle` row of each stage-2 run kept.
- 278.8 bytes for each `signature_band` row of each stage-4 run kept, sixteen rows for each signed file occurrence.
- Not measured: a row of any of the other twenty-four.

**What it costs in time**: stage 4b's build of `shingle_by_hash` reads every run's `shingle` rows to find its own, at 0.04 to 0.06 s for each million rows of other runs on the disk ADR-221 measured. No other cost in time is known.

**What the shipped deletes are.** Every shipped `DELETE` from one of the twenty-six begins `WHERE run_id = ?`, one run's rows, except the delete of `relevance_label_provenance`, which is by path and seed set and takes back who gave an answer when another answer replaces it. Within one run, some are narrower than the run: a cluster's fault and a cluster's exemplars, each for one cluster, the document frequencies below two, and verdicts of given kinds or against given file occurrences. No shipped statement deletes from `shingle`, `run_upstream`, `finished_step`, `relevance_label` or `synthesis_doc` at all. `EveryTableKeyedByARunIsOnRecordTest` holds this paragraph but for two things: which run each delete is handed, which is its caller's and was not traced for this record, and the deletes narrower than a run, which were read in the code and are held by no test of this record.

**A working directory that is new holds none of it.** It holds no `extraction_cache` either, so every file occurrence goes to the converter again. That is stated as what is so and not as advice.

### 5. What would reopen this

A later record, on either of:

- **The operator saying the file's size hurts**: the drive is short of room, or a statement is slow for the rows kept.
- **A count of what is kept.** How many stage-2 and stage-4 runs a working directory holds over each walk is not known to any agent. The pinned script of [ADR-212](0212-an-agent-may-read-a-working-directorys-aggregate-counts-through-one-pinned-script-and-nothing-else-in-it.md) prints counts by run for verdicts, finished steps, extraction faults, clusters, synthesis docs and cluster faults; it names neither `shingle` nor `signature_band`. A count of those by run would be a change to that script, which is the operator's to install.

### 6. Which run ids move

A stage's implementation version is the last commit touching `src/main/java/io/algernon/vespera/<module>` for a module `StageModules` names for it (ADR-058).

**This record changes no file under `src/main`**, checked with `git status` over the tree it was written in: what changed is under `docs/` and `src/test/`, and `AGENTS.md`. And it specifies no build. As `StageModules` stands since [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md):

| Stage | Modules it names | Moves with this record |
| --- | --- | --- |
| byte-level reduction (1) | `corpus` | no: no file of `corpus` changes |
| extraction (2) | `extraction`, `similarity` | no: no file of either changes |
| content census (3) | `similarity`, `extraction` | no, and the run upstream of it does not move |
| content redundancy (4) | `similarity`, `extraction` | no, the same way |
| seed measurement (5) | `embedding`, `extraction` | no, the same way |
| embedding scoring (5) | `embedding`, `extraction` | no, the same way |
| arrangement (6a) | `synthesis`, `extraction`, `embedding` | no, the same way |
| generation (6b) | `synthesis`, `extraction`, `embedding`, `profile` | no, the same way |

No stage names `ledger` or `pipeline`, and nothing of either changes besides.

## What is to be built

Nothing.

## Why

**On an archive that does not change, no removal that keeps ADR-156 gives anything back.** Every run there is over the latest walk and can be arrived at again. Those are the runs the ticket lists first: a build that moves a module, a profile value changed. A removal confined to the runs over an earlier walk frees nothing until the archive changes.

**The runs over an earlier walk are not simply removable either.** One of them is read by `vespera label` for as long as the label file names it, and with its keys gone `vespera label --auto` fails (Established). The removable set would need a definition this record has no call to write.

**A removal is a build in six modules, and that build adds a copy before it frees one.** A table is named in SQL only by its owner (ADR-209), and the twenty-six have six owners. Deletes in `similarity`, `extraction`, `embedding`, `synthesis` and `corpus` move every stage's run id, so every stage does its work again and writes one more copy of its rows over the latest walk, which the removal may not touch.

**What a removal costs is measured, and what it would save is not known.** #468's probes, as #481's body records them, on synthetic ledgers on a solid-state disk: 44 s to remove 4,500,000 `shingle` rows of 13,500,000 in transactions of 500,000 with `shingle_by_hash` absent, with a write-ahead log of about 200 MB, and 80 to 238 s in one statement with a log of 1.8 to 2.2 GB; 177 s for 6,400,000 `signature_band` rows; and the file no smaller afterwards. `VACUUM` took 7.4 s to bring 4.99 GB down to 1.17 GB, needing as much again in temporary files and in the write-ahead log, which is one more statement outside ADR-060's bound. None of it was measured on a spinning disk. Against that, no one has said the file's size hurts, and no agent may look.

**A wrong removal fails without a word, and a kept row costs only room.** With a run's `shingle` rows gone, a stage 4 over it signs nothing and ends with exit code 0 (`AnEarlierRunsRowsStayInvocationTest`, the second test). Since ADR-221 a kept row costs the file its size and stage 4b's build a read.

## Alternatives refused

- **Remove the rows of the runs over an earlier walk, in a stage.** The reasons above: it frees nothing on an unchanged archive, its set is not the one the ticket names, it is a build in six modules, and its benefit is unmeasured. Which stage, and in what transactions, were not worked out, the choice not being taken.
- **A command the operator runs.** The same deletes in the same six modules, and a third command where ADR-047 sizes the CLI to two. It would put the choice of when with the operator, which is its merit; it would still need the set of §2 defined and a way to keep a put-back from meeting missing rows.
- **Remove the rows of every run this invocation did not arrive at.** It is the only removal that would give back what an unchanged archive accumulates. It contradicts ADR-156 §2, by which a value put back arrives at a run and finds its work recorded. That contradiction is the operator's to choose, and this record does not (*What this does not decide*).
- **`VACUUM` alone, with no row removed.** It gives back only the pages already free, which later rows use anyway.
- **Opening the database with `auto_vacuum`.** SQLite takes the setting on a database with no table yet, or at a `VACUUM`. And with no run's rows removed it would give back only the pages a step's own redone work frees, which later rows use.

## Consequences

- **The database file grows by a copy of a stage's rows for each run of it, for as long as a working directory is used**, and by a copy of every later stage's with it. A build that moves `extraction` or `similarity` costs 277.5 bytes for each `shingle` row of the corpus, and what the other tables add, which is not measured.
- **Nothing in the ledger is ever removed for being old.** *"Nothing deletes the old run's rows"* in `AGENTS.md` stays true of every table, and a value put back still costs nothing.
- **`vespera label` goes on working over a label file written before the archive changed.**
- **No run id moves and no stage does its work again for this record.**
- **A table that gains or loses a reference to `run` fails `EveryTableKeyedByARunIsOnRecordTest`** until a record states what it keeps. So does a shipped delete from one of the twenty-six that does not begin with one run, a first delete from one of the five that have none, and any shipped text that names `VACUUM` or `auto_vacuum`.
- **#481 closes with the rows kept.** The operator may reverse that; §5 says on what.

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest`, new, two tests | by whole invocations: that an archive changed and changed back is a third walk, with the first's paths and sizes, and not the first again; that every stage's run over each walk is its own; that no run is added over the first walk and no row under its runs is added or removed, in any of the twenty-six tables; which stages' runs hold rows in fourteen of them; that an invocation with nothing changed arrives at the latest walk's runs. And that `vespera label --auto` reads the scoring run the label file names though its walk is an earlier one, recording its answers under that run, and fails with exit code 1, asking nothing, once that run's stage-2 `extraction_cache_key` rows are deleted by the test |
| `EveryTableKeyedByARunIsOnRecordTest`, new, three tests | §4: the twenty-six tables and their owners, read from `schema.sql`; that every shipped delete from one of them begins `WHERE run_id = ?` but the one by path and seed set, and that five of them have no shipped delete; and §3, that no shipped class's text, nor `schema.sql`, nor `application.yaml`, names `VACUUM` or `auto_vacuum` |

All five pass as written. No test is red by design, and no existing test was edited.

**What no test holds.**

- **That no sequence at all arrives at a run over an earlier walk.** The test drives one sequence, the one that looked able to; the rest is the reading of the code in *Established*.
- **Which run each shipped delete is handed, and which deletes are narrower than a run.** The guard reads the statement's text, and of it only how its condition begins.
- **A delete whose table is not named in the text that holds `DELETE FROM`.** The guard passes over it. None ships.
- **A reference to `run` not written `run_id TEXT NOT NULL REFERENCES run (id)`**, a nullable one among them: the guard does not count its table, so a table that gains such a reference does not fail it.
- **`vespera label` without `--auto` over a run on an earlier walk.** That it asks only for the run's row is read in `LabelIngestion`.
- **That the rows under the first walk's runs are the same rows.** *None added, none removed* is a count for each table.
- **Any size or time.** They are the probes' of #468.
- **The twelve tables the fixture leaves empty**, for which stage's run their rows are under.
- **A delete built at run time from parts none of which begins `DELETE FROM`**, or a `VACUUM` so built.

## Not known

- **How many runs' rows any real database holds, over how many walks, and whether the file's size hurts.** No agent reads a working directory, and the pinned counts do not include the two largest tables (§5).
- **What a row of any table but `shingle` and `signature_band` costs in the file.**
- **Anything on a spinning disk**: ADR-221's figures are of a solid-state disk.
- **Whether anything outside `vespera run` and `vespera label` reads a run by an id kept outside the database.** Two were looked at: the label file, above; and `arrangementApproved` in the profile, which `StageRuns.generation` asks about this invocation's own arrangement alone. The deliverable's folder is named for a run and is read with no database ([ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)).
- **The operator's own word.** It reached the author as the coordinating session's quotation.

## What this does not decide

- **Whether ADR-156 §2 should give way, so that the rows of a run not arrived at are removed and done again when a value is put back.** It is the removal that would bound the file on an unchanged archive. It changes what a put-back costs, from nothing to the stage's work again, and it is the operator's choice.
- **Whether a removal could take back a step's completion with its rows**, so that a run arrived at again does its work again where today it would find the work recorded and the rows gone. Not examined.
- **Whether the pinned counts script should count `shingle` and `signature_band` rows by run.**
- **Whether an invocation should say how large the database file is**, or warn at a size.
- **Whether stage 4b's build should stop reading the other runs' rows**, which would need the run's rows found without reading the table.
