# ADR-223 — 5f's size report, 6a and 6b go through one seed partition at a time: no class of theirs holds every arranged occurrence, their pages and the manifest are written as their rows come, and what is still held says why

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10, on two kinds of answer, kept apart in §11. **The operator's six answers** reached the session that wrote this record through the coordinating session, as ADR-218's did. **Eight rulings are the coordinating session's own**, made as build-level calls and to be reported to the operator, who may overrule any of them: §8, the lines and counters the operator reads, is written so that one line can be overruled without reopening the rest. Built in `src/main` on 2026-10-10 by the commit that follows its owes list, `6865dcf`, on a tree that then took in ADR-222's change; the coordinating session reported the unit suite passing there under Java 26 (1,590 run, none failing, none skipped), ADR-222's guard among it. §13 was added the same day, after the build: it reads what the build did that this record had not said against the code, records five of those things and sends two back, and with the removals ADR-216 asks for and one label they are the list *Owed after the build*, which no commit has built yet.
- **Answers**: the item of [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md)'s *What this does not decide* that gave 5f's cluster sizes, 6a and 6b, rows 18 to 20 of its survey, to [#472](https://github.com/algernon28/vespera/issues/472) and a record of its own, by the operator's answer to its §12 question 3. And, for rows 12, 13 and 16 to 18 of ADR-218's table, the item of ADR-220's §15 that left them to this ticket.
- **Amends**: ADR-220 §9, whose list of what is still held loses rows 18 to 20 and gains what §9 here names. [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) §3 and its table: rows 13, 16, 17 and 18 no longer exist (§10). [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §6 and §7 and [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md) §3 and §4, for the timed lines of 6a and 6b and for `SynthesisStatement.WRITTEN` (§8). [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4 and §5, for five counters (§8). [ADR-190](0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md), for what `ClusterGeneration.write` is handed and how it learns a cluster is written (§5). [ADR-150](0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md) §5's sentence on what the first pass keeps for each picture, and [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §9's on the forms of `Deliverable.writeTo` (§6, §7). Each carries a banner at its head saying so, and is otherwise not edited. [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md) carries one too, for two of the notes at its head: the note on ADR-224's "one read and ten index builds", the read being row 18, which is gone, and the note on ADR-220's "5f's cluster sizes, 6a and 6b, held until #472". ADR-224 left both to this record.
- **Keeps**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) and [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), whole: every survivor listed, the manifest's columns and its order, the two rules of the order, the order rendered from the stored columns and never derived again, the padding to the count at each level. [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md), whole: a cluster file numbers its membership from the record. ADR-149 §1 and ADR-150 §3, whole: the furniture rule and its population, every picture of every survivor the tree lists. `CONTEXT.md`'s **Furniture picture**, which needs no change. [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) §5 and §10, whose shape this record gives 6a and 6b. [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s bound as ADR-211 reads it: this record makes no exception to it on disk, and names what stays on the heap (§9). [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md): `pipeline` gathers plain values and `synthesis` writes. [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2: the arrangement's page is written again where the arrangement was already recorded. [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) and [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md) §4: a cluster nothing could be sent for earns no row, and its page says why.
- **Rests on**: the answers and rulings of §11. ADR-220's survey and Measured. [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), for which run ids move (§12). Throwaway probes outside the repository, over synthetic values (Measured). No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Settles** [#472](https://github.com/algernon28/vespera/issues/472).

## Context

ADR-211 stopped six classes holding every survivor of a run, and ADR-220 surveyed every collection of `src/main` that grows with the corpus. Three sites of that survey were held for this record: 5f's cluster sizes, 6a and 6b. Each reads the run's whole cluster membership, or every cluster, into the heap, several times over, and builds each page it writes as one string.

#472's acceptance: no class in 5f, 6a or 6b holds a collection as large as the arranged occurrences or the clusters, or a record says why it must; and the pages and the CSV are byte for byte the same.

ADR-220's candidate was ADR-211 §5's shape: one seed partition at a time. It said the change reaches ADR-104, ADR-112, ADR-133 and the furniture rule. Read against the code, it reaches them as follows.

- **The arrangement's order (ADR-112) is local to a partition but for one sort.** The order of the partitions needs each partition's size and its seed's path, one row a seed. The order of a partition's clusters needs that partition's members and their scores, and nothing of any other partition.
- **A cluster file (ADR-104, ADR-133, ADR-135) is local to its cluster.** The citations, the anchors and the numbering from the record read one cluster's rows.
- **`index.md` (ADR-103, ADR-112) is a table a partition**, each needing that partition's cluster count for its padding and the partition count for the directory names.
- **`documents.csv` (ADR-104) is not in partition order.** Its rows are in occurrence order over the whole run, the order `DocumentClusters.forRun` reads in. A partition's members are not a run of that order.
- **The furniture rule (ADR-149 §1, ADR-150 §3) is over the whole tree.** Conditions (a), recurring bytes, and (c), a near-copy, are relations over every picture of every survivor the tree lists. Conditions (b) and (d) are local to one picture and to one occurrence.
- **Two things 6b holds are recorded nowhere.** Which clusters this invocation could send nothing for is known to the generation loop alone (ADR-121, #183), and the tree's page for such a cluster says which of two reasons (ADR-174 §4).

### The sites

As `main` stands at `5b0cc20`, which holds ADR-220's build.

| # | Site | What it holds | It grows with |
| --- | --- | --- | --- |
| 1 | `pipeline.ClusteringTasklet`'s `reported`, each `ClusterSizeReport.Partition`'s `sizes` (`:148`) | the size of every cluster of the run | the clusters |
| 2 | `pipeline.ArrangementTasklet`'s `membership`, from `embedding.DocumentClusters.forRun` (`:136`) | every membership row of the scoring run | the arranged occurrences |
| 3 | its `scores`, from `RelevanceScoring.scoresFor` (`:147`) | a score for every member | the arranged occurrences |
| 4 | its `documents`, a `ClusteredDocument` a member (`:167`, `:257`), and `synthesis.Arrangement.partitionsOf`'s lists of scores (`:168`) | every member again, twice | the arranged occurrences |
| 5 | its `arranged` and `leads` (`:170`, `:171`), and `Clusters.forRun` read back (`:200`) | every cluster, three times | the clusters |
| 6 | `reportOf`'s rows (`:214`) and `ArrangementReport.render`'s page | a row for every cluster, then `arrangement.html` whole | the clusters |
| 7 | `pipeline.GenerationTasklet`'s `membership`, `scores` and `byCluster` (`:251`, `:253`, `:257`) | every member, three times | the arranged occurrences |
| 8 | its `recordedClusters` (`:260`) | every cluster | the clusters |
| 9 | `synthesis.ClusterGeneration.write`'s `alreadyWritten` (`:63`), built from `SynthesisDocs.forRun` | a slot for every cluster written, and, while it is built, every synthesis doc with its prose and what its call sent | the clusters |
| 10 | `ClusterGeneration.write`'s `unsendable` (`:74`) | a slot for every cluster nothing could be sent for | the clusters, at worst |
| 11 | `GenerationTasklet.writeDeliverable`'s `written` (`:510`) | every synthesis doc with its prose and what its call sent | the clusters |
| 12 | its `hashes` and `survivorsFor`'s list (`:509`, `:664`) | a content key and a `ListedSurvivor` for every member | the arranged occurrences |
| 13 | `whyUnwritten` (`:534`) | every recorded fault, a slot for every cluster written, a reason for every cluster unwritten | the clusters |
| 14 | `synthesis.EntryPictures.among`'s `entries`, `recurrence` and `asked` (`:73` to `:75`) | a digest, a difference hash, an occurrence and a place for every picture, and a set of every listed occurrence | the pictures and the arranged occurrences |
| 15 | `synthesis.IndexPage.contents` (`:34`) | `index.md` whole, and a map of every synthesis doc | the clusters |
| 16 | `synthesis.Deliverable`'s `writtenByCluster`, `membersByCluster` and `byPartition` (`:214`, `:219`, `:226`), and its loop over every cluster for one total (`:232`) | every doc, every member and every cluster again | both |
| 17 | `synthesis.ManifestCsv.contents` (`:32`) | the order of every cluster, and `documents.csv` whole | both |

**Found by the survey and not in the ticket.** `LeadDocument.of` filters the whole list of the run's members once for each cluster, on both branches of 6a (`ArrangementTasklet:176`, `:224`), so 6a's labelling costs the clusters times the members (Measured).

## Measured

**Units.** A size of heap is at 10⁶ bytes to the MB.

**The probes.** Three throwaway programs outside the repository, run on 2026-10-10 with Java 26.0.2.1 on Windows 11, G1, `-Xmx6g`, compiled against the classes of `main` at `90b02e5`, the commit before ADR-220's build, which changed none of the classes the probes call, so they were not run again; and placed in the packages `synthesis` and `pipeline` so as to build the collections with the shipped records and the shipped methods (`Arrangement.partitionsOf` and `order`, `LeadDocument.of`, `IndexPage.contents`, `ManifestCsv.contents`, `ClusterSizeReport.render`, `ArrangementReport.render`, `EntryPictures.among`). Every value is synthetic: paths of 90 characters, content keys of 64, labels of 40, prose of 2,016, titles of 60, 20 partitions, clusters of three unless said. Nothing was read from a database. The heap held by a collection is the used heap after four collections with the collection alive, less the same before it was built. **One round each**, where ADR-220's figures are the range of two or three. These measure the relative cost of the shapes on one machine, and not what a stage takes on the operator's database.

**The plans.** Every statement §2 to §7 add was planned with `EXPLAIN QUERY PLAN` against the shipped `schema.sql`, its tables empty, by Python's SQLite 3.49.1, **which is not the SQLite the pom bundles**, 3.53.2. No statement was run over rows, and no temporary file was watched.

### What 5f, 6a and 6b hold today

Bytes for each arranged occurrence, at 100,000, 300,000 and 1,000,000:

| Collection (site) | Bytes an occurrence |
| --- | ---: |
| the membership, `List<DocumentCluster>` (2, 7) | 76 to 81 |
| the scores (3, 7) | 75 to 78 |
| 6a's `ClusteredDocument` list (4) | 36 to 38 |
| `Arrangement.partitionsOf` (4) | 28 to 31; 53 where every cluster is of one |
| 6b's content keys (12) | 145 to 150 |
| 6b's `ListedSurvivor` list (12) | 341 to 345 |
| `byCluster`, and `membersByCluster` (7, 16) | 55 to 58 each; 173 where every cluster is of one |
| `documents.csv` as one string (17) | 289 to 300 |

Bytes for each cluster, at 100,000 to 1,000,000 clusters:

| Collection (site) | Bytes a cluster |
| --- | ---: |
| 5f's sizes (1) | 5 to 6 |
| a recorded cluster (5, 8) | 180 to 184 |
| 6a's `leads` (5) | 117 |
| a row of 6a's page (6) | 407 to 410 |
| `arrangement.html` as one string (6) | 614 to 620, two bytes a character, the page carrying an em dash |
| a synthesis doc with its prose (9, 11) | 2,347 to 2,420 |
| `alreadyWritten`, and `writtenByCluster` (9, 16) | 93 each |
| `index.md` as one string (15) | 205 |

- **Everything of 6a and 6b alive at once**, which no one stage holds, the pictures left out: 219.9 MB at 100,000 occurrences, 651.1 MB at 300,000, 2,166.5 MB at 1,000,000 in clusters of three, and 4,458.4 MB at 1,000,000 in clusters of one.
- **`LeadDocument.of` over every cluster**: 0.8 s at 30,000 occurrences in 10,000 clusters and 10.5 s at 100,000 in 33,320. Not run larger.
- **The furniture rule's first pass, as it is**: 362 to 368 bytes a picture while it decides, over 3,000 and 60,000 pictures, one a survivor, each a 24-bit bitmap of 8 rows; 0.4 to 0.6 MB kept once it returns, none of those pictures being furniture. The pass took 69.1 s over the 60,000, which is the decoding and the two digests of each picture and not the comparison (below).

**Against ADR-220's 531 to 534 bytes an occurrence for four of 6b's collections.** The same four, the membership, the scores, the content keys and the `ListedSurvivor` list, came to 652.8 bytes here, and to 516.4 where each survivor shares one string for its seed's path. `survivorsFor` reads the seed's path from the ledger again for every survivor, so each carries its own: 136 bytes, a string of 90 characters. ADR-220's probe is not in the repository; its figure is what a probe that shared the string would give, and the difference is that one string.

### What answer 1 leaves held

Built from random digests and hashes, at 100,000 and 1,000,000 pictures, none recurring:

| What is kept for each distinct picture | Bytes a picture |
| --- | ---: |
| as it is today: digest, layer, hash, occurrence, place, and the count beside it | 362 to 368 |
| B. a list of (digest as text, hash bits, width, height) and a count for each digest | 196 to 198 |
| C. one map from the digest as text to (hash bits, width, height), and a set of the furniture | 190 to 191 |
| D. arrays: 32 bytes of digest, a long, two ints and a flag | 51 to 59 |

**The near-copy comparison**, ADR-150 §3(c)'s, the shipped loop copied and run over the shipped `DifferenceHash`, every picture distinct:

| Distinct pictures | Spread over 500 widths, 3 pixels apart | All of one width |
| ---: | ---: | ---: |
| 10,000 | 0.02 s, 99,556 pairs | 0.11 s, 49,995,000 pairs |
| 30,000 | 0.03 s | 0.99 s |
| 100,000 | 0.06 s | 9.73 s, 4,999,950,000 pairs |
| 300,000 | 0.29 s, 90,008,121 pairs | not run |

It compares every pair of pictures within 2 pixels of width, so it is quadratic in the pictures of one width.

### The plans

| Statement | Plan |
| --- | --- |
| how many members one partition has, `COUNT(*)` by run and winning seed (§3) | `document_cluster_by_winning_seed_occurrence_id`; no temp B-tree |
| one partition's members, with no `ORDER BY` (§3) | the same index; no temp B-tree. With `ORDER BY occurrence_id` it sorts |
| one partition's recorded clusters, with no `ORDER BY` (§3) | `cluster`'s primary key, `(run_id=? AND winning_seed_occurrence_id=?)`; no temp B-tree. With `ORDER BY cluster_order` it sorts |
| the partitions of an arrangement, `GROUP BY winning_seed_occurrence_id` over `cluster` (§4) | `cluster`'s primary key, `(run_id=?)`; no temp B-tree |
| one cluster's row, one synthesis doc, what one call sent in citation order, one cluster fault, each by its key (§5, §6) | the table's primary key; no temp B-tree |
| one score by its key | `relevance_score`'s primary key |
| a page of the run's members in occurrence order, `occurrence_id > ? AND +run_id = ? ORDER BY occurrence_id LIMIT 1000` (§7) | `document_cluster`'s primary key, `(occurrence_id>?)`; no temp B-tree. Written with `run_id = ?` it is planned by `document_cluster_by_run_id` and sorts the run's rows for every page |
| every partition's size in one `GROUP BY` over `document_cluster` | sorts; not taken |

## Decision

### 1. Per site

| Sites | Decision |
| --- | --- |
| 1 | five numbers a partition, the sizes let go (§2) |
| 2 to 6 | one partition at a time, the page written as its partitions come (§3) |
| 7, 8 | one partition's members at a time, the clusters read a partition at a time (§5) |
| 9 | asked by key, a cluster at a time (§5) |
| 10 | **held, and §9 says why** (§5) |
| 11, 13, 15, 16 | one partition at a time, a synthesis doc and a fault asked by key, the index written as its partitions come (§6) |
| 12, 17 | the tree lists one partition's survivors at a time; the manifest is a read of its own, a page at a time (§6, §7) |
| 14 | **held, less for each picture, and §9 says why** (§7) |

**This record adds no class file to `pipeline`, and `ArrangementTasklet` names no type it does not name today.** Every type it adds is `synthesis`'s; a rule that decides what a page or the manifest says stays in `synthesis`; and `pipeline`'s three tasklets and two report classes are edited where they stand, handing `synthesis` their reads as method references and lambdas (§6).

That is shaped to [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md)'s guard, which landed before this record was built: `PipelineHoldsOnlyTheRulesOnRecordTest` holds every class of `pipeline` on record, the classes that name `Deliverable` or `VerdictKind`, and, for the classes of the three stages whose versions it stops naming `pipeline` for, `ArrangementTasklet` among them, the exact set of capability types each names. So what 6a newly asks is asked through types `ArrangementTasklet` already names, in values of `ledger`'s types and the JDK's: `DocumentClusters.sizeOf` and `membersOf`, which answers the `DocumentCluster` it names today; `RelevanceScoring.winningSeeds`; `Arrangement.inOrder` over two maps and `Arrangement.order` over a `Partition`; `Clusters.seedsOf` and `ofPartition`, which answers the `RecordedCluster` it names today. It goes on naming each of the eighteen types on that record, `ClusterSlot` among them for the leads it keeps of the partition in hand, and names no `Clustering`, no `ArrangedPartition` and no `ListedPartition`. `GenerationTasklet` names `Deliverable` today and no other class of `pipeline` comes to name it.

### 2. 5f keeps five numbers a partition

`ClusteringTasklet` reads each partition's cluster sizes as now, `DocumentClusters.sizesFor`, once the partition is clustered, takes from them the five numbers the page shows, the members, the clusters, the largest, the middle one and the clusters of one, and lets the sizes go before the next partition. `ClusterSizeReport.Partition` is `(String seedPath, int documentCount, int clusterCount, int largest, int median, int singletons, Optional<RetainedEdgeSpread> spread)`, made by `Partition.of(String seedPath, List<Integer> sizes, Optional<RetainedEdgeSpread> spread)`. What 5f keeps for the page is one row a seed.

`cluster-sizes.html` is the same bytes: it has one row a partition, made of those numbers, today. The closing line's two sums are taken from the same rows.

`sizesFor`'s statement is not changed. It sorts one partition's rows, row 12 of ADR-218, and stays in that record's §3 (§10).

### 3. 6a arranges one partition at a time

**The partitions and their order.** `ArrangementTasklet` asks `RelevanceScoring.winningSeeds(RunId scoring)` for the winning seeds, the statement 5f issues through `Clustering.partitions`, and for each seed how many members its partition has under the scoring run, `DocumentClusters.sizeOf(RunId, OccurrenceId winningSeed)`, a count. A seed with none has no partition, as at 5f. It reads each remaining seed's path, and `synthesis.Arrangement` puts them in ADR-112's order, largest first and ties on the seed's path: `Arrangement.inOrder(Map<OccurrenceId, Integer> memberCounts, Map<OccurrenceId, String> seedPaths)` answers the seeds, a `List<OccurrenceId>`, in that order. No partition's members are read to order the partitions.

**Where no seed has a member the step is gated as now**, with the line it writes today, and no run is minted. That read and those counts are made on both branches, before the step knows which it is on.

**Then each partition, in that order, held until it is done and let go before the next.** The tasklet reads the partition's members, `DocumentClusters.membersOf(RunId, OccurrenceId winningSeed)`, which answers them in occurrence order, put in that order on the heap; reads their scores by key; and hands them to `Arrangement.partitionsOf` as now, which gathers that one partition's clusters. `Arrangement.order(Partition, int partitionOrder)` orders them by ADR-112's second rule. Each cluster is labelled from its lead and recorded, in order. Then the partition's rows are read back, `Clusters.ofPartition(RunId, OccurrenceId winningSeed)`, in `cluster_order`, and its part of the page is written from them.

- **Occurrence order within the partition is what keeps the bytes.** A cluster's mean score is a sum of doubles in the order its members come, and `LeadDocument.of` takes the first of equal scores. Both read the members in occurrence order today, because `forRun` sorts the run by it. One partition's members in occurrence order give each cluster the same members in the same order, so the same mean, the same lead, the same label and the same `cluster_order`.
- **The order is still rendered from the stored columns and never derived again** (ADR-112). `Clusters.ofPartition` puts one partition's rows in the order their stored `cluster_order` gives, on the heap, where `forRun` asked SQLite to. No rule of the order is applied a second time.
- **A member with no score stops the step**, as now, by `Arrangement`'s own check, on the partition it is in.
- **`LeadDocument` is handed one partition's members**, so what it filters for each cluster is the partition and not the run.

**`arrangement.html` is written as its partitions come**, and is the same bytes. `ArrangementReport.open(Appendable page, String approvalName, String corpusRoot)` writes everything before the first partition; `ArrangementReport.partition(Appendable page, Partition partition)` writes one partition's heading, its line of counts and its table, once that partition's rows are read back; `ArrangementReport.close(Appendable page, int partitionsWritten)` writes everything after the last, or, where none was written, the sentence the page carries today for an empty arrangement. The line of counts needs the partition's members and clusters before its rows, and both are in hand when the partition is read back. The page's head and tail are `ReportPage.head` and `ReportPage.tail`, which ADR-220 §6 added.

**It is written beside the target and moved into place** (§11, answer 5): to `arrangement.html.part` in the working directory, then moved over `arrangement.html` by one atomic move that replaces. A step that fails partway leaves the page of the arrangement before it, or none, and never the head of a page with an approval name on it and half its partitions. A `.part` file a failure leaves is written over by the next invocation.

**Where the arrangement was already recorded** (ADR-154 §2), the page is written from the rows recorded, in the same way: the seeds come from `Clusters.seedsOf(arrangement)` (§4), in the order stored, and each partition's members and scores are read for its clusters' leads, as now, then its recorded clusters, a partition at a time.

### 4. The partitions of an arrangement are read from its own rows

One statement over the arrangement's `cluster` rows, grouped by the winning seed, planned by the table's primary key with no temporary storage, its rows put in the order `partition_order` gives on the heap, one a seed. `synthesis.Clusters` answers it in two forms. `Clusters.seedsOf(RunId arrangement)` answers the winning seeds, a `List<OccurrenceId>`, for 6a's second branch, which may name no new type (§1). `Clusters.partitionsOf(RunId arrangement)` answers one `ArrangedPartition` a seed partition, `(OccurrenceId winningSeed, int partitionOrder, int clusterCount, int memberCount)`, for 6b, which needs the counts before it reads a partition. `Clusters.ofPartition` is §3's. `Clusters.forRun` goes: nothing calls it (ADR-216).

### 5. 6b generates a partition's clusters at a time, and asks by key what is written

**`ClusterGeneration.write` is handed the clusters as an `Iterable` and their number**, `write(RunId generation, long clusterCount, Iterable<RecordedCluster> clusters, ClusterExemplars exemplars, String modelName, int contextWindow, GenerationProgress progress)`. `GenerationTasklet` reads the arrangement's partitions once, `Clusters.partitionsOf(arrangement)`, and hands the walk an `Iterable` that reads one partition's clusters, `Clusters.ofPartition`, when the walk comes to the first of them, with `Clusters.countForRun(arrangement)`. It keeps the partitions, one row a seed, for the tree. The loop, the breaker, ADR-166 §4a's exemption, the repair pass's deletion and the completion rule are as ADR-190 has them. The order the clusters are gone through is the order they are gone through today.

**Whether a cluster is already written is asked by its key** (§11, answer 4): `SynthesisDocs.isWritten(RunId, OccurrenceId winningSeed, int clusterOrdinal)`, a lookup of one row, where the walk read every synthesis doc of the run, with its prose and what its call sent, to keep a set of slots. `SynthesisStatement.WRITTEN` and its two lines go (§8).

**A cluster's exemplars are drawn from its partition's members, held while that partition's clusters are gone through.** `GenerationTasklet`'s `ClusterExemplars` reads a partition's members, in occurrence order, when the walk first asks about a cluster of it that is not written, and lets them go when it is asked about a cluster of another. Each member's score is read by key in the loop that reads its opening chunk. A partition whose clusters are all written is not read. The exemplars of a cluster, their order and their scores are today's, so the question put is the same question.

**`unsendable` stays held** (§11, answer 2), and §9 says why.

### 6. 6b writes the tree a partition at a time

**Generation and the tree stay two passes** (§11, answer 2): the tree is written once the walk over the clusters has returned, as now, by every invocation that reaches it.

**`synthesis` asks for a partition's values as it comes to it.** `Deliverable.writeTo(Path workingDirectory, DeliverableProvenance provenance, ArrangedSurvivors source, SurvivorPictures pictures, DeliverableProgress progress)`. `ArrangedSurvivors` is a public interface of `synthesis`:

- `List<ListedPartition> partitions()`: one row a seed partition in stored order, `ListedPartition` being `(OccurrenceId winningSeed, String seedPath, int partitionOrder, int clusterCount)`. A `ListedPartition` refuses a seed path that is null or blank, with an `IllegalArgumentException`.
- `long survivorCount()`: how many survivors the arrangement arranges.
- `List<RecordedCluster> clustersOf(ListedPartition partition)`, in stored order, and `List<ListedSurvivor> survivorsOf(ListedPartition partition)`, in occurrence order.
- `Optional<SynthesisDoc> writtenOver(ClusterSlot cluster)`, the doc with what its call sent in citation order, and `Optional<Unwritten> whyUnwritten(ClusterSlot cluster)`, asked only of a cluster with no doc.
- `Optional<ArrangedCluster> placeOf(ClusterSlot cluster)`, a cluster's two places, for the manifest.
- `void eachPageOfSurvivorsForTheirPictures(Consumer<List<ListedSurvivor>> page)` and `void eachPageOfSurvivorsForTheManifest(Consumer<List<ListedSurvivor>> page)`, each every survivor once, in occurrence order (§7). They are two reads of the same rows, named for what each is for, because whoever answers them says a different line for each (§8, L11 and L12) and must not have to tell them apart by which came first (§13).

**`pipeline` implements it with no class of its own** (§11, ruling 6): `ArrangedSurvivors.reading(Supplier<List<ListedPartition>> partitions, LongSupplier survivorCount, Function<ListedPartition, List<RecordedCluster>> clustersOf, Function<ListedPartition, List<ListedSurvivor>> survivorsOf, Function<ClusterSlot, Optional<SynthesisDoc>> writtenOver, Function<ClusterSlot, Optional<Unwritten>> whyUnwritten, Function<ClusterSlot, Optional<ArrangedCluster>> placeOf, Consumer<Consumer<List<ListedSurvivor>>> eachPageOfSurvivorsForTheirPictures, Consumer<Consumer<List<ListedSurvivor>>> eachPageOfSurvivorsForTheManifest)` answers one whose methods call the functions it was given, and `GenerationTasklet` hands it method references and lambdas. The reads behind them are `synthesis`'s and `embedding`'s own: `SynthesisDocs.forCluster(RunId, OccurrenceId winningSeed, int clusterOrdinal)`, an `Optional<SynthesisDoc>`; `ClusterFaults.forCluster(RunId, OccurrenceId winningSeed, int clusterOrdinal)`, an `Optional<ClusterFault>`; `Clusters.ofPartition`, and `Clusters.placeOf(RunId, OccurrenceId winningSeed, int clusterOrdinal)`, an `Optional<ArrangedCluster>` read by the cluster's key; `DocumentClusters.membersOf` and `eachPage`.

`SynthesisDocs.forCluster` makes two statements, the doc and then what its call sent, the second begun once the first has given up its rows: outside a transaction a statement begun inside another's rows would wait for a connection, and where the pool holds one it would wait for ever.

The forms of `writeTo` that take lists go (ADR-216; §11, ruling 4), which amends ADR-149 §9's sentence that the five-parameter form stays. `src/test` keeps one fixture, `synthesis.ListedArrangement`, that answers `ArrangedSurvivors` from lists, so that every test of the tree hands it what it handed `writeTo`.

**The order of the write.**

1. The partitions are asked for. A partition's seed path arrives with it, read from the ledger for the seed, the value `survivorsFor` gives each survivor today. Today's stop for *"a partition no survivor names"* becomes `ListedPartition`'s refusal (§11, ruling 5), raised before any directory, page or total, as now.
2. The furniture rule's first pass (§7).
3. The three totals are announced: the partitions, the cluster files, which is the sum of the partitions' cluster counts, and the membership entries, which is `survivorCount()`. An entry carries a document exactly where a survivor of the cluster is listed, so the entries with a document are the survivors the arrangement arranges, and the loop over every cluster that counted them goes.
4. `index.md.part` is opened in the tree and the index's opening lines written, `IndexPage.open(Appendable index, DeliverableProvenance provenance)`.
5. Each partition, in stored order: its directory, its clusters and its survivors read; for each cluster its doc asked by key and, where it has none, why; the cluster file written by `ClusterPage.write` as now. The partition's table is appended to the index, `IndexPage.partition(Appendable index, ListedPartition partition, int partitionCount, List<RecordedCluster> clusters, Function<ClusterSlot, Optional<SynthesisDoc>> writtenOver)`. The partition is let go before the next.
6. `index.md.part` is moved over `index.md`. The index is the last of the pages to appear, as now.
7. The manifest (§7).

**The bytes are the same.**

- A partition's directory name needs the number of partitions and the seed's path, `IndexPage.partitionDirectoryName(ListedPartition partition, int partitionCount)`; a cluster's file name needs its partition's cluster count and its label. Each is in hand before the first file of the partition.
- A cluster file reads its own cluster's rows and nothing else. Its membership order needs its survivors in occurrence order, ties in score keeping the order they arrive in (`ClusterPage.numbered`), and `survivorsOf` gives one partition's in that order.
- A citation, its anchor and the numbering from the record (ADR-133) are local to the page. What one call sent is read for that cluster, in citation order, by `call_exemplar`'s primary key.
- Why a cluster is unwritten keeps today's order of reasons: what this invocation found (`unsendable`), then a recorded fault, then *not reached*.
- The index's rows are appended in the order they are composed in today.

**A page that fails partway.** A cluster file is written whole, as now. `index.md` and `documents.csv` are each written beside the target and moved (§11, answer 5), so a tree a failure interrupts holds the index and the manifest of the write before it, or none, beside cluster files of this one. A tree left with a `.part` file was not finished.

### 7. The manifest is a read of its own, and the furniture rule keeps less

**`documents.csv` is written a page of the run's members at a time, in occurrence order** (§11, answer 3). `DocumentClusters.eachPage(RunId, Consumer<List<DocumentCluster>> page)` hands over pages of up to 1,000 membership rows in occurrence order, each planned by `document_cluster`'s primary key, the run compared as a value, `+run_id = ?`, so that the index on the run is not chosen and nothing is sorted, as ADR-211 §8 compares the walk. For each row `pipeline` reads the occurrence's path, its content key and its score, and `ManifestCsv.write(Appendable csv, ArrangedSurvivors source, DeliverableProgress progress)` writes the header and then each row of the manifest's read, asking `placeOf` for its cluster's two places and telling `progress` of each row, to `documents.csv.part`, which is moved over `documents.csv` after the last. A seed's path is read once a seed.

- **The rows and their order are today's.** The header, each cell and the quoting are `ManifestCsv`'s, unchanged. A survivor with no score still shows `0.0`.
- **A survivor that names a cluster the arrangement does not carry still stops the write**, with the same exception, where its row is reached.
- **It cannot ride on §6's loop.** A partition's members are not a run of occurrence order, and the manifest is built to be loaded as it stands (ADR-104).

**The furniture rule's population stays every picture of every survivor the tree lists** (§11, answer 1; ADR-149 §1, ADR-150 §3). What changes is what the first pass keeps. `EntryPictures.among(ArrangedSurvivors source, SurvivorPictures pictures, DeliverableProgress progress)`:

- **Fed a page of survivors at a time**, from `eachPageOfSurvivorsForTheirPictures`, each survivor asked for its pictures once, the total announced being `survivorCount()`.
- **The set of every listed occurrence, `asked`, goes** (§11, ruling 3). It kept a survivor listed twice from being asked twice. No path hands one twice: a run's membership holds an occurrence once, `document_cluster`'s primary key being the occurrence and the run; each page begins after the last occurrence of the page before; and `ArrangedSurvivors` is the only source the first pass has. `EntryPicturesTest`'s claim that a survivor listed twice is asked once is removed with it, having no subject.
- **Condition (d) is decided as each survivor is read.** It compares the pictures of one occurrence with one another, so the places are compared while that occurrence's pictures are in hand, and no place is kept.
- **Condition (b)** marks the picture's digest as furniture when it is read.
- **Condition (a)**: a digest seen a second time is furniture.
- **Condition (c)** is decided once the last survivor is read, over the distinct pictures that have a difference hash, by the loop that stands.
- **Kept until then, for each distinct picture**: its digest, and its difference hash with the width and height it carries, or that it has none. One map from the digest to those three, and the set of furniture digests: form C of Measured, 190 to 191 bytes a distinct picture, against 362 to 368 a picture today. Kept afterwards: the furniture digests, as now.
- **The furniture decided is the furniture decided today.** (a), (b) and (d) are decided from the same values. (c) is over the first-seen picture of each digest, as now; equal digests are equal bytes, so equal hashes.

This amends ADR-150 §5's *"The first pass of ADR-149 §9 keeps, for each picture, the digest, the width and height, the difference hash and the place"*: the place is not kept past its occurrence, and what is kept is for each distinct picture.

**A survivor's content key is read once in each of the three reads that need it**, the first pass, the partition's listing and the manifest, where one map of every survivor's key served all three.

### 8. What the operator reads, line by line

**These are the coordinating session's ruling and not the operator's answer, but for the one line of answer 4** (§11, ruling 1). Each row below stands on its own: the operator can overrule one line, and the row is then amended by a later record without anything else here being reopened.

**Timed lines.** ADR-193 §1 puts a statement whose cost grows with a table in one of its forms in the change that adds it; a lookup of one row by its key and an exists check are not in its scope. `<P>` is a partition's place among `<M>`, counted from 1.

| # | Stage | Before | After |
| ---: | --- | --- | --- |
| L1 | 6a | `the cluster membership`, timed | struck. In its place `the seed partitions`, timed, in 5f's words, on both branches, first |
| L2 | 6a | — | `the members of partition <P> of <M>`, timed, once a partition, in 5f's words |
| L3 | 6a | `the recorded clusters`, timed, on either branch | struck. In its place `the recorded clusters of partition <P> of <M>`, timed, once a partition, after that partition's members |
| L4 | 6a, where the arrangement was already recorded | — | `the seed partitions of the arrangement`, timed, after L1 and before the first partition |
| L5 | 6b | `the cluster membership`, timed | struck. In its place `the members of partition <P> of <M>`, timed, once a partition the walk reads, when it first asks for a cluster's exemplars |
| L6 | 6b | `the recorded clusters`, timed | struck. In its place `the seed partitions of the arrangement`, timed, once, first, and `the recorded clusters of partition <P> of <M>`, timed, once a partition, when the walk comes to it |
| L7 | 6b | `the clusters already written`, timed, one call | struck, with `SynthesisStatement.WRITTEN`. **The operator's answer 4** |
| L8 | 6b | `the standing faults`, timed | as it is |
| L9 | 6b | `the clusters written`, timed | struck: a doc is asked by key. In the tree, `the recorded clusters of partition <P> of <M>` and then `the members of partition <P> of <M>`, timed, once a partition |
| L10 | 6b | `the faults recorded`, timed | struck: a fault is asked by key |
| L11 | 6b | — | `the arranged occurrences, for their pictures`, the first pass's read, before the tree's first partition, in the form ADR-211 §9 gave a read made a page at a time: the counted form's lines, its progress the rows read of `survivorCount()` |
| L12 | 6b | — | `the arranged occurrences, for the manifest`, the manifest's read, after the tree's last partition, in the same form |
| L13 | 5f | every line | as it is |

So a clean finish of 6b over one partition writes, in order: L6's two, L5, L8, L11, L9's two, L12. A walk that stops on five answers turned down writes the same less L8.

L11 and L12 are told through two constants `embedding.EmbeddingStatement` gained with the build, `ARRANGED_OCCURRENCES_FOR_PICTURES` and `ARRANGED_OCCURRENCES_FOR_THE_MANIFEST`, the statement being `embedding`'s, and their progress labels are `Stage 6b (generation, reading the arranged occurrences, for their pictures)` and `Stage 6b (generation, reading the arranged occurrences, for the manifest)`.

**Counters** (ADR-192 §4, §5):

| # | Before | After |
| ---: | --- | --- |
| C1 | `Stage 6a (arrangement, scores read)`, over the membership | as it is, its total the sum of the partitions' members, known once the partitions are sized |
| C2 | `Stage 6a (arrangement, members gathered)`, over the membership | as it is, the same total |
| C3 | `Stage 6a (arrangement, clusters)`, over the arranged clusters | one for each partition, `Stage 6a (arrangement, clusters, partition <P> of <M>)`, as 5f's are since ADR-211 §5: a partition's clusters are not known before its members are read |
| C4 | `Stage 6a (arrangement, page rows)`, over the recorded clusters | one for each partition, `Stage 6a (arrangement, page rows, partition <P> of <M>)` |
| C5 | `Stage 6a (arrangement, page partitions)` | as it is |
| C6 | `Stage 6b (generation, scores read)`, over the membership | struck: the loop is gone, and a score is read by key in loops that report, `cluster documents opened` and `survivors listed` |
| C7 | `Stage 6b (generation, clusters)`, `(…, partitions written)`, `(…, cluster files written)`, `(…, survivors listed)`, `(…, pictures listed)`, `(…, membership entries)` | as they are, each total read from a count or from the partitions' rows |
| C8 | — | `Stage 6b (generation, manifest rows)`, over `survivorCount()`, told by `DeliverableProgress.toWriteManifestRows(long rows)` and `manifestRowWritten()` |

**Nothing else the operator reads changes**: no page, no cell of the manifest, no cluster file, no picture file and no file name.

### 9. What is still held, and why

#472's acceptance asks that any class of 5f, 6a or 6b still holding a collection as large as the arranged occurrences or the clusters have a record saying why it must. After §2 to §7, **in the heap**:

- **One row a seed**, in each of the three stages. Not arranged occurrences and not clusters: the seed set, the operator's folder, as ADR-211 §10 says of 5c and 5d.
- **The largest partition, at 6a and at 6b**, as at 5f since ADR-211 §10. 6a holds one partition's members, their scores, their clusters and its part of the page; 6b's walk holds one partition's members; the tree holds one partition's clusters and survivors. A partition can be as large as the run's arranged occurrences where one seed wins nearly all. Why it must: a cluster's place needs every member's score (ADR-112), and a partition's clusters are numbered and padded to their count. The bound is the largest partition and no longer every partition at once.
- **`unsendable`, at 6b**: a slot for each cluster this invocation could send nothing for, at worst every cluster. Why it must: such a cluster earns no row in any table (ADR-121, #183), its page says which of two reasons (ADR-174 §4), and the tree is written after the walk, by the operator's answer. Writing each partition's pages as the walk reaches it would leave pages on disk for rows that roll back where the step throws. Its size was not measured as a map; a set of as many slots is 93 bytes a cluster (Measured).
- **The furniture rule's distinct pictures, at 6b**: 190 to 191 bytes for each distinct picture of the tree, until the last survivor is read (§7). Why it must: conditions (a) and (c) relate every picture of the tree to every other (ADR-149 §1, ADR-150 §3), and the operator kept that population. A table of `synthesis`'s in its place was refused (Alternatives refused).
- **One page of 1,000 membership rows**, in the manifest's read and in the first pass.

Nothing else in the heap of the three grows with the arranged occurrences or the clusters. **On disk, in SQLite's temporary files**, this record adds nothing that sorts (§10), and removes four reads that did.

### 10. This record against ADR-218: the statements that sort

**Nothing here is measured on disk.** No temporary file was watched for this record. What it says of a plan is Measured's, from an SQLite that is not the bundled one, and `EveryStatementThatSortsIsRecordedTest` plans every shipped statement with the bundled one.

| ADR-218's row | After this build |
| --- | --- |
| 12, `DocumentClusters.sizesFor`, one partition's sizes | unchanged, and still sorts. It stays in ADR-218 §3's class: its rows are one partition's members, 4.9 to 5.0 bytes each |
| 13, `DocumentClusters.forRun`, the run's membership in occurrence order | **gone.** §3's count and read of one partition, and §7's read a page at a time, are in its place, and none is planned through temporary storage |
| 16, `Clusters.forRun` | **gone.** §4's reads are in its place, none planned through temporary storage |
| 17, `SynthesisDocs.forRun`, the prose carried through the sort | **gone.** A doc is read by its key |
| 18, `ClusterFaults.forRun` | **gone**: `ClusterGeneration` counts since ADR-220 §7, and the tree asks by key |
| 10, 11, `RelevanceScoreCache`'s winning seeds and one partition's members | unchanged. Row 10's statement is now issued by 6a as well as by 5f, through `RelevanceScoring.winningSeeds`, which adds no statement. Both stay excepted by [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md), each with a measured size |

So this record takes rows 13, 16, 17 and 18 out of ADR-218 §3's class and leaves row 12 in it. With ADR-224, which landed beside it and takes out rows 15, 19 and 20, the class is rows 6, 7, 10, 11 and 12, every one with a measured size: row 18 was the last read recorded at the size of another's plan. The test holds one statement for `embedding.DocumentClusters` where it held two, and none for `synthesis.Clusters`, `synthesis.SynthesisDocs` and `synthesis.ClusterFaults`, where it held one each. **This record changes no other entry of that test**: the entries for `embedding.RelevanceDistribution` and `pipeline.InvocationAccount` went with ADR-224, and `embedding.RelevanceScoreCache`'s two stand. The map as both leave it holds `similarity.DocumentFrequency` 4, `similarity.ShingleHashIndex` 1, `similarity.RedundancyResolution` 3, `ledger.Verdicts` 1, `embedding.RelevanceScoreCache` 2, `embedding.DocumentClusters` 1 and `embedding.UnusableSeeds` 1.

**Three reads here are issued without `ORDER BY` and their rows put in order on the heap**: one partition's members, by occurrence; one partition's recorded clusters, by `cluster_order`; the partitions of an arrangement, by `partition_order`. Each is of rows the stage holds in any case, so the order costs no temporary file and no collection that was not there.

### 11. The operator's answers, and the coordinating session's rulings

**Six questions were put to the operator** on 2026-10-10 with the survey and the first measurements, and answered the same day through the coordinating session. Each answer is the recommendation.

1. **The furniture rule.** Its population could stay the whole tree, with less kept for each picture; or go to a table of `synthesis`'s; or become one partition, which changes which pictures are shown. **Answered: the whole tree, and less kept**: the digest, the hash and its size; (d) decided for each occurrence as it is read; `asked` dropped; this record says why it is still held (§7, §9).
2. **`unsendable`.** **Answered: generation and the tree stay two passes, and it stays held**, this record saying why (§5, §9).
3. **`documents.csv`.** **Answered: a read of its own in occurrence order, a page at a time by the primary key** (§7).
4. **The read of the clusters already written.** **Answered: asked by key for each cluster; the two timed lines go**, and ADR-193 and ADR-204 are amended (§5, §8).
5. **A page that fails partway.** **Answered: `arrangement.html`, `index.md` and `documents.csv` are written beside the target and moved into place** (§3, §6, §7).
6. **Which build ships it.** **Answered: the build that ships ADR-220's change and #353's** (§12).

**Seven points the answers did not settle were ruled by the coordinating session**, as build-level calls, on 2026-10-10. They are not the operator's, and are to be reported to the operator.

1. **The other lines the operator reads** go as §8 has them, in a section that names each before and after.
2. **The same build**: nothing enforces it, and Consequences says what an invocation between costs.
3. **`asked` goes, and the test's claim about a survivor listed twice with it**, this record saying why it has no subject (§7).
4. **The forms of `Deliverable.writeTo` that take lists go**, one fixture under `src/test` answering the new source from lists (§6).
5. **The stop for a partition with no seed path becomes `ListedPartition`'s refusal**, and its test still shows that nothing is written (§6).
6. **No class file is added to `pipeline`**: `ArrangedSurvivors.reading` takes the reads as functions (§1, §6).
7. **The names stand.** `ListedPartition`, `ArrangedSurvivors` and `ArrangedPartition` were read against `CONTEXT.md`'s entries for seed partition, survivor and arrangement, and carry no word those entries refuse. `SeedPartitionSize`, which the draft had, is gone by ruling 8.
8. **ADR-222's guard stays green as it is written**, a constraint relayed from #353's session once its pull request had passed its gate: no class file is added to `pipeline`, no class of `pipeline` comes to name `Deliverable`, and `ArrangementTasklet` names no capability type it does not name today (§1). Where the design could not meet it the instruction was to stop and not to plan an edit of the guard; it meets it.

### 12. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058), and a run's id is a hash of its upstream runs' too (ADR-048).

**This record touches three modules: `pipeline`, `synthesis` and `embedding`.** It touches nothing of `corpus`, `extraction`, `similarity`, `ledger` or `profile`.

- `pipeline`: `ClusteringTasklet`, `ClusterSizeReport`, `ArrangementTasklet`, `ArrangementReport`, `GenerationTasklet`.
- `synthesis`: `Arrangement`, `Clusters`, `SynthesisDocs`, `ClusterFaults`, `ClusterGeneration`, `SynthesisStatement`, `Deliverable`, `DeliverableProgress`, `IndexPage`, `ManifestCsv`, `EntryPictures`, and the new `ArrangedSurvivors`, `ArrangedPartition` and `ListedPartition`.
- `embedding`: `DocumentClusters`, `RelevanceScoring`.

**So every stage whose list names `pipeline`, `embedding` or `synthesis` is minted again, and so is every stage downstream of one that is.**

**Read against `StageModules` as ADR-222 left it**, the tree this record was built on: both runs of stage 5, seed measurement and embedding scoring, name `embedding` and `pipeline`; arrangement names `synthesis` and `embedding`; generation names all three. **Stages 5, 6a and 6b move.** Byte-level reduction names `corpus` alone, extraction names `extraction` and `similarity`, and content census and content redundancy name `similarity` and `extraction` since ADR-222 took `pipeline` from them: stages 1 to 4 are not moved by this record. Before ADR-222, stages 3 and 4 named `pipeline` and would have moved with it.

**What that costs, where this record's build is used on a working directory an earlier build had run.** Stage 5 scores again from the cached vectors and clusters again; 6a mints a new arrangement, which `arrangementApproved` must name; and 6b, under a new run, **asks for every synthesis doc again**, the most expensive calls there are.

**It ships in the build that ships ADR-220's change and ADR-222's** (§11, answer 6). ADR-220's moves stages 1 to 6b once (ADR-220 §11), so in that build this record adds no id to those that move and no work. ADR-221 and ADR-224 landed beside this record, each saying which ids its own change moves; ADR-224's edits to `embedding` and `pipeline` move stages this record moves already, and neither is part of the claim. The changes *Owed after the build* touch `pipeline` and `synthesis` again, and belong in the same build for the same reason.

**What does not move**: no DDL, so no schema version, and an existing working directory opens; no cache key; no `run.stage` and no `finished_step.step` value. This record does not edit `StageModules`.

### 13. What the build did that this record had not said

Read against the code of `6865dcf` on 2026-10-10, each either recorded here as the decision or sent back.

1. **`Clusters.placeOf(RunId, OccurrenceId winningSeed, int clusterOrdinal)`**, a lookup of one `cluster` row by its key, answers the source's `placeOf` for the manifest. **Recorded** (§6): §7 asked for a cluster's two places by its key and named no method of `Clusters` for it.
2. **Two constants of `EmbeddingStatement`, and two progress labels of the build's wording**, for L11 and L12. **Recorded** (§8), with the labels as built. ADR-193's and ADR-204's banners say the enum gained them.
3. **`GenerationTasklet` told the two reads of every survivor apart by counting the calls to one `eachPageOfSurvivors`**: the first was the pictures', the second the manifest's. **Sent back.** That is true only while `synthesis` makes exactly those two reads in that order, and nothing in either module says so: a write that skipped the first pass would have named the manifest's read as the pictures'. `ArrangedSurvivors` has two reads, named for what each is for (§6), and the count goes.
4. **`survivorCount()` at 6b is the sum of the arrangement's stored `document_count`**, and not a count of the membership. **Recorded, and held by a test.** It is the size 6a stated once (ADR-112), which the index and the disclosure already read, and `ArrangementGoesThroughTwoPartitionsInvocationTest` holds that 6a stores, for each partition, as many as the membership has under its seed. The two part only where a row is edited under the tool, and then the manifest's own stop for a survivor whose cluster is not carried, or a counter that ends short of its total, is what shows.
5. **6b reads a cluster's synthesis doc twice**, once for its cluster file and once for its row of the index. **Recorded** as a consequence. Both are lookups of one row by its key, of one cluster at a time, and holding a partition's titles to save the second would be a collection for a lookup.
6. **A `.part` file is left by a write that fails, and written over by the next.** **As this record has it** (§3, §6, Consequences): nothing deletes one, and nothing reads one.
7. **`SynthesisDocs.forCluster` makes its second statement once the first is done.** **Recorded** (§6), with the reason.

**Found by the analyst after the build, and sent back with 3.**

- **The two per-partition counter labels of 6a were built with `.formatted(place, …)`**, which `StageProgressLabelsAreConstantsTest` read as literal text: its scan passed any part that began with a quote. That was a hole in the test, and the test is closed: a part is a string literal only where it is one from its first quote to its last. `ArrangementTasklet` joins `place` and `of` to its literal text as `ClusteringTasklet` does, the two names being allowed in both files, so that the scan reads which names a label carries (§8, C3 and C4).
- **Six things nothing in `src/main` calls** since the build (ADR-216): `RecordedSynthesisDoc`, `RecordedClusterFault`, `ClusterSlot.of` of each, `synthesis.Partition.documentCount()`, and the two-argument `ManifestCsv.write`. No record requires any of them: §7 named the two-argument `write` before the build gave it a third, and is amended above. The tests that named them are moved off them, `WholeRun` and `ListedArrangement` keeping the pairing of a doc or a fault with its cluster in two records of `src/test`, `synthesis.ListedDoc` and `synthesis.ListedFault`.

## Alternatives refused

- **A table of `synthesis`'s for the pictures' digests and hashes**, so that the furniture rule holds nothing. DDL, a schema version, and rows written again on every write of the tree, for a rule whose reduced record is 190 bytes a distinct picture. Refused by the operator's answer 1.
- **Furniture decided over one partition.** It holds one partition's pictures, and it shows a logo that recurs in two partitions under both. It changes the deliverable, contradicts ADR-149 §1 and `CONTEXT.md`, and fails the acceptance. Refused by answer 1.
- **Form D for the pictures, arrays of primitives**, 51 to 59 bytes a picture. A hand-written table of 32-byte keys, for a third of form C. Not taken; it is there if a corpus's pictures ask for it.
- **The manifest in partition order**, written by §6's loop. Other bytes, and a file a consumer loads would change its order. Refused by answer 3.
- **A cluster's pages written as generation reaches it.** `unsendable` would then be let go a partition at a time, and pages would stand on disk for rows a throw rolls back. Refused by answer 2.
- **One partition's written slots at a time, in place of asking by key.** A line a partition for a set that a lookup of one row replaces. Refused by answer 4.
- **`ORDER BY` in the reads of one partition.** Each then sorts in temporary storage, a new statement for ADR-218's class, to order rows the stage holds.
- **Every partition's size in one grouped statement over `document_cluster`.** It sorts a row for each member of the run. A count a seed sorts nothing.
- **A cluster's members read by the cluster's key.** `document_cluster` has no index on the cluster, so each read goes through its partition's rows, and a partition's clusters would cost its members squared.
- **An anonymous class of `GenerationTasklet`'s implementing `ArrangedSurvivors`.** A class file in `pipeline`, where ruling 6 asked for none. Refused by ruling 6.

## Consequences

- **No class of 5f, 6a or 6b holds a collection as large as the arranged occurrences or the clusters, but what §9 names with the reason for each.**
- **The pages and the manifest are byte for byte the same**, and the tests hold it for each.
- **An invocation made on a build between `5b0cc20` and the one that carries this record pays the minting twice**: stages 3 to 6b run again under new ids when this record's build is first used, a new arrangement has to be approved, and 6b asks for every synthesis doc a second time. Nothing in the repository prevents it; it is avoided only by making no invocation in between (§11, ruling 2).
- **6a's labelling costs each partition's clusters times that partition's members**, where it cost the run's clusters times the run's members.
- **6b no longer reads every synthesis doc's prose three times**, to learn which clusters are written, to write the tree and to write the index.
- **It reads each cluster's doc twice by its key**, for the cluster file and for the index's row (§13).
- **More statements, each small.** A read a partition where there was one a run, and a lookup by key for each cluster and each survivor of the manifest. Their time was not measured.
- **A read of one partition goes through the rows of every scoring run that names its seed**, `document_cluster_by_winning_seed_occurrence_id` holding no run, as `sizesFor` does at 5f today.
- **The operator reads other lines at 6a and 6b** (§8), and more of them where there are many seeds: up to three timed pairs a partition at 6b.
- **A `.part` file can be left in the working directory or in a tree by an invocation that stops.** It is written over by the next.
- **Four of ADR-218's sorting reads are gone**, row 17's among them, which carried the prose of every synthesis doc through a sort.

## Tests

Written with this record, before `src/main`, each new class naming a type or a method this record adds; the last two rows of the table, and the row before them, were written after the build, against the built tasklets.

| Class | What it holds |
| --- | --- |
| `NoClassOfTheLastThreeStagesReadsAWholeRunTest` | that `forRun` is on none of `DocumentClusters`, `Clusters`, `SynthesisDocs` and `ClusterFaults`; that `SynthesisStatement` has no `WRITTEN`; that no form of `Deliverable.writeTo`, and no method of `Arrangement`, `IndexPage`, `ManifestCsv` or `EntryPictures`, takes a list of the run's clusters, survivors or members; and that `ClusterSizeReport.Partition` has no list among its components |
| `pipeline.ClusterSizesPageKeepsFiveNumbersAPartitionTest` | that `Partition.of` keeps the five numbers of its sizes, the middle one being the upper of two, and that the page's row shows them |
| `synthesis.SeedPartitionsAreOrderedFromTheirSizesTest` | that `Arrangement.inOrder` gives ADR-112's order from each seed's size and path, and that a partition ordered alone, at the place that order gives it, has the clusters, the places and the order the arrangement gave it when every partition was ordered at once, equal scores and clusters of one included |
| `pipeline.ArrangementPageIsWrittenAsItsPartitionsComeTest` | that the page written by `open`, `partition` and `close` is byte for byte the page `render` wrote at `5b0cc20`, for three partitions and for none |
| `synthesis.PartitionsOfAnArrangementTest` | that `Clusters.seedsOf` gives an arrangement's seeds in stored order, `partitionsOf` one row a partition in that order with its counts, and `ofPartition` one partition's clusters in stored order and no other's, another arrangement's rows left out of all three |
| `synthesis.AClusterIsAskedForByItsKeyTest` | that `SynthesisDocs.isWritten` and `forCluster`, and `ClusterFaults.forCluster`, answer of one cluster under one run, what a call sent coming back in citation order |
| `embedding.MembersAreReadAPartitionOrAPageAtATimeTest` | that `sizeOf` and `membersOf` answer of one partition under one run, in occurrence order, and that `eachPage` hands every member of the run once, in occurrence order, in pages of at most 1,000, another run's rows left out |
| `synthesis.TreeIsTheSameWrittenAPartitionAtATimeTest` | that `index.md`, `documents.csv` and every cluster file are byte for byte those `Deliverable.writeTo` wrote from lists at `5b0cc20`: three partitions, a cluster with a fault, one nothing could be sent for, one not reached, equal scores within a cluster, a call that sent other than the highest-scoring; and that a finished tree holds no `.part` file |
| `synthesis.ManifestIsWrittenAPageAtATimeTest` | that the manifest is the same bytes whether its survivors come in one page or in pages of two that cut across partitions |
| `synthesis.FurnitureIsDecidedOverTheWholeTreeTest` | that a picture whose bytes recur under two survivors of two partitions, handed in two pages, is furniture, and one that does not recur is not |
| `synthesis.TreeThatFailsPartwayLeavesTheOldIndexAndManifestTest` | that a write that fails at its second partition leaves `index.md` and `documents.csv` as the write before left them, and leaves neither where there was no write before |
| `pipeline.StageProgressLabelsAreConstantsTest` | as before, that every progress label is literal text and the names allowed in its file; and now that a part is literal text only where it is a whole string literal, so that text with a call on it is refused, and that `place` and `of` are allowed in `ArrangementTasklet` as in `ClusteringTasklet` (§13) |
| `pipeline.ArrangementGoesThroughTwoPartitionsInvocationTest` | through invocations over two partitions: that 6a says the reads of its first partition, members and then recorded clusters, before any read of its second; that each partition's stored sizes add up to the members under its seed; that the page is whole, in its place, and no `.part` file is left; and that an arrangement that fails at its second partition, on a member with no score, leaves `arrangement.html` character for character as it was |
| `pipeline.GenerationGoesThroughTwoPartitionsInvocationTest` | through invocations over two partitions of five clusters and two: that 6b's walk reads the second partition's clusters after the first partition's members, so when it comes to it; that a second invocation reads no member of a partition whose clusters are all written, and the members of the one with a cluster left; that a walk stopped by five answers turned down in the first partition reads nothing of the second until the tree; and the tree's reads over two partitions, in order |

A third claim of `synthesis.TreeIsTheSameWrittenAPartitionAtATimeTest`, added with §13: that the writer asks for every survivor once through the read named for the pictures and once through the read named for the manifest, in that order.

Three fixtures of `src/test` came with them. `synthesis.ThreePartitions` and `pipeline.ThreePartitionsOnThePage` hold the inputs of the byte-for-byte tests and name no type this record adds. `synthesis.TheTreeWrittenFromLists` and `pipeline.TheArrangementPageRenderedWhole` hold the bytes `Deliverable.writeTo` and `ArrangementReport.render` gave those inputs at `5b0cc20`, captured by a throwaway program and not written by hand, since the forms that gave them go with this record.

**Existing tests this record moves**, each edited with it:

- `EveryStatementThatSortsIsRecordedTest`: `embedding.DocumentClusters` 2 to 1; the entries for `synthesis.Clusters`, `synthesis.SynthesisDocs` and `synthesis.ClusterFaults` go. No other entry is touched.
- `synthesis.EntryPicturesTest`: its claim that a survivor listed twice is asked once is removed (§7).
- `synthesis.PartitionNoSurvivorNamesStopsTheWriterTest`: its stop is `ListedPartition`'s refusal, and it still shows no directory, no page and no total.
- `synthesis.SynthesisStatementProgressOrderTest`, `synthesis.SynthesisStatementThatThrowsTest`, `pipeline.ReportedStatementsTellAPagedReadByItsRegistrationTest`, `pipeline.GenerationReportsItsProgressInvocationTest`, `pipeline.StageFiveReportsItsProgressInvocationTest`: the lines and counters of §8.
- Every test that handed `Deliverable.writeTo`, `IndexPage.contents`, `ManifestCsv.contents`, `EntryPictures.among`, `ClusterGeneration.write`, `Arrangement.order`, `ArrangementReport.render` or `ClusterSizeReport.Partition` the run's lists, and every test that read a run's rows back through a `forRun`: the first through `synthesis.ListedArrangement`, the second through `WholeRun`, a fixture of `src/test` that reads a run's rows for a claim.

- After the build (§13): `synthesis.ClusterSlotTest`, `synthesis.ArrangementTest`, `WholeRun`, `synthesis.ListedArrangement` and every test that named `RecordedSynthesisDoc` or `RecordedClusterFault`, moved off what ADR-216 removes.

**What no test holds**: any size on the heap; any time; that a partition is let go before the next is read, beyond the order of the reads; that 6a's page is moved into place in one move and not copied, which a reader in the middle of it would have to be there to see; the order of 6b's reads against the answers it asks for, beyond what a walk that stops shows.

## What the commit that builds `src/main` owes

- §2 to §7, with the signatures named there, and the lines and counters of §8.
- `ArrangementTasklet` naming exactly the capability types ADR-222's guard has on record for it (§1), and no class file added to `pipeline`. The guard is not edited for this record.
- `ListedPartition`'s refusal in these words, the seed's id in the place of 2: `seed partition 2 has no seed path; a partition directory cannot be named without it`. And `Deliverable.writeTo` asking for the partitions before it makes the tree's directory or asks any survivor for its pictures, so that the refusal leaves nothing written and nothing reported.
- `DocumentClusters.forRun`, `Clusters.forRun`, `SynthesisDocs.forRun` with `whatEachCallSent`, `ClusterFaults.forRun`, `Arrangement.order(List<Partition>)`, `ArrangementReport.render`, `IndexPage.contents`, `partitions` and `seedPaths`, `ManifestCsv.contents` and the forms of `Deliverable.writeTo` that take the run's lists: removed, nothing calling them (ADR-216).
- Javadoc that says otherwise, where it stands: `ClusteringTasklet`'s "page tree", a word `CONTEXT.md` refuses, in the paragraph on the size distribution; `SynthesisDocs.whatEachCallSent`'s "read in one query rather than one per cluster"; `GenerationTasklet`'s account of `hashes` as the one cache of a tree write; `EntryPictures`' account of what the first pass keeps; `Deliverable.writeTo`'s parameters; `ClusterGeneration`'s account of its two reads.
- **Edit no test.** One the build finds it has to edit is a finding for the analyst.
- Verify with `./mvnw verify` under Java 26.

### Owed after the build

By a commit to `src/main` that edits no test (§13). Until it is made the test tree does not compile, three classes of it naming the two reads of `ArrangedSurvivors`, and `StageProgressLabelsAreConstantsTest` fails on 6a's two labels.

- **`synthesis.ArrangedSurvivors`**: `eachPageOfSurvivors` gives way to `eachPageOfSurvivorsForTheirPictures` and `eachPageOfSurvivorsForTheManifest`, and `reading` takes one function for each, the pictures' before the manifest's, after `placeOf`. `EntryPictures.among` reads through the first and `ManifestCsv.write` through the second.
- **`pipeline.GenerationTasklet`**: hands `reading` one lambda for each read, each naming its own `EmbeddingStatement`, and no longer counts the calls.
- **`pipeline.ArrangementTasklet`**: the labels of its two per-partition counters are `"Stage 6a (arrangement, clusters, partition " + place + " of " + of + ")"` and `"Stage 6a (arrangement, page rows, partition " + place + " of " + of + ")"`, `of` being a local name for the number of partitions where the first is built. The lines they write do not change.
- **Removed, nothing calling them** (ADR-216): `synthesis.RecordedSynthesisDoc`, `synthesis.RecordedClusterFault`, `ClusterSlot.of(RecordedSynthesisDoc)`, `ClusterSlot.of(RecordedClusterFault)`, `synthesis.Partition.documentCount()`, and `ManifestCsv.write(Appendable, ArrangedSurvivors)`, with any javadoc that names one of them.

## What this record does not measure

- Anything with the code of §2 to §7 as built: no peak of heap after the change and no time of any stage. Every figure under Measured is of the code before it. That the bytes are the same is held by the tests, over the arrangements they build, and was not looked at on any working directory.
- Any statement over rows, any temporary file, and any plan with the bundled SQLite.
- The time of a read of one partition where the working directory holds several scoring runs over one walk, and of the manifest's page where it holds membership rows of earlier walks.
- A real peak of heap: each figure is a collection's retained size, one round, and no figure includes what a write encodes or a builder copies.
- The furniture rule over real pictures: the 368 and the 190 bytes are of small synthetic bitmaps and random hashes, and the share of pictures that recur decides how many are distinct.
- `unsendable` as the map it is.
- Anything on `H:`, on a spinning disk or on Linux.

## What this does not decide

- **The near-copy comparison's cost where many pictures share a width**: 9.7 s at 100,000 of one width, and four times that for each doubling. Nothing here bounds it, and no corpus is known to reach it.
- **Rows 10 and 11 of ADR-218**, `RelevanceScoreCache`'s two reads at 5f, one of which 6a now issues too, and rows 15, 19 and 20: ADR-224 decides them, the first two staying excepted and the last three sorting nothing.
- **Whether `document_cluster` should carry an index on the run and the winning seed**, so that a read of one partition does not go through other runs' rows. DDL and a schema version, for a cost not measured.
- **Whether a tree left unfinished should be told apart by anything but its `.part` file.**
- **Whether the merges are made one build by anything but the operator** (§12).
- **What any of it costs on the operator's archive.**
