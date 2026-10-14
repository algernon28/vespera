# ADR-241 — The manifest asks a page's places together, a cluster's synthesis doc is read once, and a survivor's pictures are looked up by its listed key

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-240](0240-the-tree-asks-what-it-lists-of-each-survivor-a-page-of-survivors-at-a-time.md) §1, where a cluster's place and doc *"asked by its key"* stand (§1, §2 here), and its first item of *What this does not decide*, a survivor's content key asked by itself for its pictures, which this record decides (§3). [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §6, in `ArrangedSurvivors.placeOf` and `IndexPage.partition`, and step 5 of the order of the write; §7, in how the manifest asks for a cluster's places; §13, items 1 and 5; and its Consequences, *"It reads each cluster's doc twice by its key"* and *"a lookup by key for each cluster … of the manifest"*. [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §9, in what `SurvivorPictures.of` is handed (§3). Each carries a note at its head.
- **Keeps**: ADR-240 §2, the three reads of a page of survivors; ADR-223's one seed partition at a time, its three listings of every survivor, in occurrence order, its two reads named for what each is for, and what §9 says is held; ADR-104's and ADR-112's manifest, its rows, its columns and its order; ADR-133's numbering; ADR-149 §9's two passes, each asking a survivor's pictures once with no pixels kept between calls, and ADR-150 §4's format read before the conversion; [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) and [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md).
- **Rests on**: `pipeline.TheTreeReadsEachClustersDocOnceAndAPagesPlacesTogetherInvocationTest`; a throwaway probe outside the repository (Measured).

## Context

ADR-240 asks what the tree lists of each survivor, its path, its key and its score, a page of survivors at a time, and reads three things of ADR-223 as accepted: a cluster's places asked by its key for each row of the manifest (ADR-223 §13 item 1), a cluster's synthesis doc read twice, for its file and for its row of the index (§13 item 5, *"holding a partition's titles to save the second would be a collection for a lookup"*), and an exemplar's score and key asked as it is sent (§5). It leaves undecided a survivor's content key asked by itself where its pictures are read.

Each of the first two can be changed without the collection §13 item 5 refuses. The manifest is written a page of survivors at a time, so the places of a page's clusters can be asked for together. And the doc the cluster's file is written from is in hand when that cluster's row of the index can be written, if the row is written then and not after the partition's last file. The third needs no read at all: the listing that hands the pictures a survivor already holds its content key, which ADR-240 reads for the page.

## Measured

**The statements.** `TheTreeReadsEachClustersDocOnceAndAPagesPlacesTogetherInvocationTest` counts, through the connections the pool hands out, what a tree write over 30 survivors in three clusters of one partition asks between the first line of its first read and the last line of its last:

| Asked in the tree's span, 30 survivors, 3 clusters | Before ADR-240 | After ADR-240 | After this record |
| --- | ---: | ---: | ---: |
| a survivor's score | 90 | 3 | 3 |
| a survivor's path, or a seed's | 92 | 5 | 5 |
| a survivor's key, for its listing | 90 | 3 | 3 |
| a survivor's key, for its pictures | 60 | 60 | 0 |
| a cluster's places | 30 | 30 | 1 |
| a cluster's synthesis doc, and what its call sent | 12 | 12 | 6 |
| a page of survivors, a partition's clusters and its members | 4 | 4 | 4 |
| a survivor's detected format, and its cached conversion (§4) | 120 | 120 | 120 |
| **all** | **498** | **237** | **142** |

Of the 142, 120 are the pictures' own, two for each survivor in each of the two passes. The other 22 do not grow with the survivors of a page.

**The plan** of the one new statement, by the SQLite the pom bundles, 3.53.2, against the shipped `schema.sql` with no rows and a list of two: `SELECT cluster_ordinal, document_count, partition_order, cluster_order FROM cluster WHERE run_id = ? AND winning_seed_occurrence_id = ? AND cluster_ordinal IN (…)` is `SEARCH cluster USING INDEX sqlite_autoindex_cluster_1 (run_id=? AND winning_seed_occurrence_id=? AND cluster_ordinal=?)`. It goes through no temporary storage, and `EveryStatementThatSortsIsRecordedTest` passes unchanged.

**The time.** One throwaway probe, Java 26 and the bundled sqlite-jdbc 3.53.2.1, a `relevance_score` of 100,000 rows of one run, `journal_mode=WAL`, a Linux container's disk with the file cache warm, three rounds: each row read by its key, a statement prepared for each as `JdbcTemplate` prepares one, 948, 841 and 825 ms; the same rows read 1,000 to a statement, 134, 100 and 109 ms. About 8.4 µs a lookup against 1.1 µs a row; a `cluster` row by its key is a lookup of the same shape.

## Decision

### 1. The manifest asks for a page's places together

`ArrangedSurvivors.placeOf(ClusterSlot)` becomes `placesOf(Set<ClusterSlot>)`, answering a map that holds each cluster the arrangement records. `ManifestCsv.write` asks it once for each page of survivors, for the clusters the page names, and stops on a survivor whose cluster is not in the map with the exception it threw, where that survivor's row is reached. `Clusters.placeOf` becomes `Clusters.placesOf(RunId arrangement, Collection<ClusterSlot>)`: one statement for each winning seed among the clusters and each 1,000 of its ordinals, by the table's primary key. A page holds the places of at most the clusters its 1,000 survivors name. This amends ADR-223 §6's `placeOf`, §7's *"asking `placeOf` for its cluster's two places"* and §13 item 1, and ADR-240 §1's place asked by its key.

### 2. A cluster's synthesis doc is read once

`Deliverable` reads a cluster's doc once and hands it to the cluster's file and to its row of the index. `IndexPage.partition` becomes `IndexPage.partitionHeading(Appendable, ListedPartition)`, appended before the partition's first cluster file, and `IndexPage.clusterRow(Appendable, ListedPartition, int partitionCount, RecordedCluster, Optional<SynthesisDoc>)`, appended after each cluster's file. The index holds the same bytes in the same order: it is a `.part` file until it is moved, so writing its rows between the cluster files changes nothing a reader sees. Nothing is held for it beyond the doc the cluster's file was written from. This amends ADR-223 §6's step 5 and its `IndexPage.partition`, §13 item 5, whose reason stands and is met without a collection, and ADR-240 §1's doc asked by its key twice.

### 3. A survivor's pictures are looked up by the key its listing read

`SurvivorPictures.of` is handed the `ListedSurvivor` and not its `OccurrenceId`, and `GenerationTasklet` looks its pictures up by `ListedSurvivor.contentHash()`, the value ADR-149's and ADR-206's lookup is keyed on and ADR-240's read gives the page, where it asked the key again for each survivor in each pass. Both passes have the survivor in hand: the first pass's read hands `ListedSurvivor`s, as ADR-223 §6 has it, and the cluster file's members are. This amends ADR-149 §9's *"`List<ListedPicture> of(OccurrenceId occurrence)`"*, everything else of §9 standing, and decides the first item of ADR-240's *What this does not decide*.

### 4. What is still asked one survivor at a time

- **A survivor's detected format and its cached conversion**, in each of the two passes. ADR-149 §9 holds one survivor's pixels at a time and decodes them afresh on each call, and ADR-150 §4 reads the format first so that a survivor whose kind lists no pictures has its conversion left unread. Reading a page of conversions would hold a page's pixels. The format could be read for a page, but `SurvivorPictures` is asked one survivor at a time by the two passes, and this record does not change that.
- **6b's walk** reads each exemplar's score, key and opening chunk as it reaches it (ADR-223 §5), as ADR-240 leaves it.

### 5. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058, as [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) leaves it). This record touches `synthesis` (`ArrangedSurvivors`, `Clusters`, `Deliverable`, `EntryPictures`, `IndexPage`, `ManifestCsv`, `SurvivorPictures`) and `pipeline` (`GenerationTasklet`), which no stage's version names. `synthesis` is named by 6a and 6b, so on its own this change moves 6a and 6b. ADR-240's change and [ADR-237](0237-the-read-of-one-hashs-occurrences-names-its-run-in-its-text-and-is-prepared-once-for-the-containment-pass.md)'s move stages 2 to 6b as well. No DDL, no schema version, no cache key, no verdict kind; no page, no cell of the manifest, no cluster file, no picture and no line changes.

## Consequences

- **A tree write asks a few statements for each page of survivors and each cluster, and two for each survivor in each of its two passes over the pictures**: on the test's tree, 142 where ADR-240 left 237, and 498 before it.
- **The manifest holds the places of one page's clusters**, at most one for each of its 1,000 survivors, while it writes that page.
- **The index's rows are written as the cluster files are**, not after them; a write that fails partway leaves a `.part` index either way.

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.TheTreeReadsEachClustersDocOnceAndAPagesPlacesTogetherInvocationTest`, new, through an invocation with the pool's connections counted | that each cluster's synthesis doc is read once in the tree's span, 3 for 3 clusters; that no statement but the two the pictures are looked up by is asked as many times as the 30 survivors; and that all of them together are fewer than the survivors |

Moved with the change, none weakened: `synthesis.PartitionsOfAnArrangementTest`, from `Clusters.placeOf` to `placesOf`, the clusters of two seeds asked together, with the same claims; `synthesis.ListedArrangement`, whose source answers `placesOf` and whose index is written by `partitionHeading` and `clusterRow`; the three tests that build a source from it, `TreeIsTheSameWrittenAPartitionAtATimeTest`, `TreeThatFailsPartwayLeavesTheOldIndexAndManifestTest` and `TreeThatFailsWritingItsManifestLeavesTheOldIndexAndManifestTest`; and the picture sources of `DeliverablePicturesTest`, `DeliverableProgressTest`, `EntryPicturesTest` and `FurnitureIsDecidedOverTheWholeTreeTest`, handed a survivor where they were handed its occurrence. `TreeIsTheSameWrittenAPartitionAtATimeTest` still holds the tree byte for byte against its captured tree, and ADR-240's `TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` passes unchanged.

## What this does not decide

- Reading a page of survivors' detected formats together, which would leave the two passes with one statement for each survivor's conversion: `SurvivorPictures` would have to be asked for a page.
- 6b's walk's reads of each exemplar's score, key and opening chunk.
- What any of it costs on the operator's archive: the time above is one table's, on a warm cache.
