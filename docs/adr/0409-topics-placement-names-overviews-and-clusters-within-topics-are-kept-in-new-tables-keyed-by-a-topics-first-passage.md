# ADR-409 — Topics, placement, names, overviews and clusters within topics are kept in new tables keyed by a topic's first passage

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: under the operator's delegation, in map [#822](https://github.com/algernon28/vespera/issues/822) as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §8 records it, which gives [#832](https://github.com/algernon28/vespera/issues/832) the ledger's tables for topics, placement and names and the run each is written under; and of what [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md) §5, [ADR-400](0400-a-survivor-goes-to-the-topic-its-chunks-are-nearest-on-average-and-is-linked-from-one-15-per-cent-of-them-are-nearest.md) §5, [ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md) §4, [ADR-402](0402-a-topics-overview-cites-its-written-clusters-by-number-in-every-sentence-and-a-citation-fault-is-asked-once-more.md) §7 and §8, [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md) §5 and §7 and [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) §1 and *What this does not decide* each leave to #832: how a topic key is written in a row (§1); the tables, their columns, keys and owners (§2 to §4, §14); the run and step each is written under, and what a step doing its work again does with them (§5); that a name is kept across a stopped step and read back (§6); what the 6a gate shows of a name (§7); the wiring of the division into the clustering step and the identity member it brings (§8); the overview's walk and generation's members (§9); what the renderings draw until [#833](https://github.com/algernon28/vespera/issues/833) (§10); the schema version and a working directory an earlier build used (§11); the statements that sort (§12); what is held, one topic at a time (§13); and which runs move (§15).
- **Amends**: [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md), the key of 6a's cluster row, the run, the winning seed and the ordinal: a cluster row is keyed by the arrangement run, the topic key, the subtopic and the cluster ordinal (§3). ADR-396 left whether ADR-105 is amended to #832.
- **Amends**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) §3 and [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §10: rows 10 and 11 of ADR-218's table, 5f's winning seeds and the members of one partition, are gone with the reads that made them (§12).
- **Amends**: ADR-223 §2 to §7 and §9, and [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9 where it names them: 5f, 6a and 6b go through one topic at a time where they went through one seed partition at a time, and what each holds is restated for a topic (§13).
- **Amends**: [ADR-236](0236-a-seed-partitions-reads-seek-its-rows-under-one-scoring-run-by-an-index-on-the-seed-the-run-and-the-cluster.md): `schema.sql` no longer builds `document_cluster_by_winning_seed_run_and_cluster`, its table being no longer created; the reads of one topic seek by indexes led by the run and the topic (§12).
- **Amends**: [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md)'s count of the tables keyed by a run, twenty-eight since ADR-392, which becomes thirty-eight (§11).
- **Amends**: ADR-400 §6's line *"…, the placement held and not yet stored (#832)"*, ADR-406 §5's *"Until then no member is written at any value"*, and ADR-407 §9, the names logged and none stored: each held until this record, and each replaced by what §5 to §8 say.
- **Keeps**: ADR-049, [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) and [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §3: every table this record adds is new and no existing one changes shape, so no module's schema version moves (§11); [ADR-392](0392-vespera-finds-no-translation-sets-the-translation-step-its-verdict-its-report-and-its-two-profile-keys-go.md) §2's rule for a table `schema.sql` stops creating, kept unread in a database that holds it (§11); [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §3, a table named in SQL by its owner alone (§14); ADR-399 §4 and §5, the topic key and the order keys compare in (§1); ADR-020's score and winning seed, `relevance_score` unchanged; ADR-112's orders, ADR-396 §4's for the topics and ADR-406 §4's for the subtopics (§3); [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md) and [ADR-319](0319-a-cluster-whose-answer-is-turned-down-the-last-two-times-it-is-asked-stands-without-writing-and-the-generation-step-finishes-with-it.md), every rule of the cluster tables but their key (§3); [ADR-230](0230-the-clusters-under-a-scoring-run-are-of-its-survivors-as-they-stand-and-the-arrangements-identity-names-the-removals-standing.md), the clusters of the survivors as they stand (§8); [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md)'s memory contract (§13); [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md), an index on every reference (§12).
- **Settles**: [#832](https://github.com/algernon28/vespera/issues/832).

## Context

Five records of map #822 decide what topics are and how they are found, placed, divided, named and overviewed, and each leaves where its results are kept to #832. ADR-399 finds the topics at the head of the relevance-scoring step, under the embedding-scoring run, keyed by the seed path and chunk index of their first passage. ADR-401 drops a topic under the breadth floor and lists the seeds that then feed none. ADR-400 gives every scored document a winning topic and the topics it fits, read in the scoring pass. ADR-406 divides a crowded topic into subtopics and finds clusters within each, but is wired into no step: the clustering step still clusters seed partitions, and its `subtopicFloor` member is written at no value. ADR-407 names topics and subtopics at the head of the arrangement step, and logs each name at INFO, storing none. ADR-402 writes an overview per topic at 6b, in three row sets whose tables are #832's.

So the code finds the topics twice, once at 5d to place and once at 6a to name, from the same vectors; it places every survivor and keeps none of it; and five tables, `document_cluster`, `cluster`, `synthesis_doc`, `call_exemplar` and `cluster_fault`, are keyed by `winning_seed_occurrence_id`, a seed partition, which ADR-396 retired. Three things bear on how that is changed:

- **A working directory holds what no run can make again.** `vector` holds every chunk's embedding, which cost the embedding model a call each; `relevance_label` holds the relevance labels a person gave. Both are `embedding`'s. A re-keyed table is a change of shape, which `schema.sql`'s `CREATE TABLE IF NOT EXISTS` does not make on a database that has the table: the module's version must move (ADR-059), and the guard's refusal tells the operator to delete and recreate that module's tables, `vector` and `relevance_label` among them. And an index on a column such a table lacks fails `schema.sql` itself on that database, before the guard can say anything.
- **A topic key is a path and a number, not an occurrence.** No foreign key reaches it, and its two columns order as ADR-399 §4 compares keys: SQLite's `BINARY` order of UTF-8 text is code-point order.
- **`synthesis` may not name `embedding`'s types** (`AGENTS.md`, the module rule): a topic key crosses into it as values.

## Decision

### 1. A topic key in a row is two columns

**A topic is written in a row as its key, the seed path and the chunk index of its first passage** (ADR-399 §5): two columns, a `TEXT` and an `INTEGER`, named `seed_path` and `chunk_index` in the topic's own table and `topic_seed_path` and `topic_chunk_index` wherever another row names a topic. Never an occurrence id, which is per walk; never a name, which is a rendering; never an ordinal, which moves with the topics before it. The path is the one the seed walk stores (ADR-051), so a key compared in a statement, or in the order of a primary key, compares as ADR-399 §4 has it.

### 2. Stage 5 keeps the topics, their passages, the seeds feeding none and each document's place

Five tables, each `embedding`'s, each under the embedding-scoring run. The key is in bold.

| Table | Columns | What a row is |
| --- | --- | --- |
| `topic` | **`run_id`, `seed_path`, `chunk_index`**, `passage_count` | a topic that stands at the breadth floor (ADR-401 §1), and how many passages it holds |
| `topic_passage` | **`run_id`, `seed_path`, `chunk_index`**, `topic_seed_path`, `topic_chunk_index` | a passage of a topic that stands, and that topic: a passage lies in one topic (ADR-399 §2) |
| `seed_feeding_no_topic` | **`run_id`, `seed_path`** | a seed ADR-401 §4 lists |
| `winning_topic` | **`occurrence_id`, `run_id`**, `topic_seed_path`, `topic_chunk_index` | a scored document's winning topic, beside its `relevance_score` row, under the same key |
| `see_also` | **`occurrence_id`, `run_id`, `topic_seed_path`, `topic_chunk_index`** | a topic other than its winning one that the document fits (ADR-400 §2) |

- **A passage is keyed by its seed's path and its chunk index, never by an occurrence id alone**: an occurrence id is per walk, and a seed of an earlier seed's content feeds no passage (ADR-399 §1), so it has no row.
- **Only topics that stand are kept**, and only their passages. A dropped topic's passages have no row; the seeds left feeding none are `seed_feeding_no_topic`'s.
- **Every scored document has one `winning_topic` row** (ADR-400 §5), whether the relevance floor passes it or not, and no score at a topic is kept.
- **`relevance_score` is unchanged.** Its winning seed stays the score's provenance (ADR-020, ADR-396 §2); the winning topic lies in a table of its own under the same key, so that no table changes shape (§11).
- `topic_passage` reads a topic's passages in visiting order by `topic_passage_by_run_and_topic (run_id, topic_seed_path, topic_chunk_index, seed_path, chunk_index)`; `winning_topic` reads a topic's members in occurrence order, and counts them, by `winning_topic_by_run_topic_and_occurrence (run_id, topic_seed_path, topic_chunk_index, occurrence_id)`; `see_also` a topic's links by `see_also_by_run_topic_and_occurrence (run_id, topic_seed_path, topic_chunk_index, occurrence_id)`.

### 3. Clusters lie within topics, and the five cluster tables are keyed by topic, subtopic and ordinal

**Each of the five tables keyed by a winning seed is replaced by a table of another name keyed by the topic**, its columns and rules otherwise the old table's:

| Replaced | By | Owner, run | Key |
| --- | --- | --- | --- |
| `document_cluster` | `cluster_member` | `embedding`, the scoring run | **`occurrence_id`, `run_id`**; beside them `topic_seed_path`, `topic_chunk_index`, `subtopic_ordinal`, `cluster_ordinal` |
| `cluster` | `arranged_cluster` | `synthesis`, the arrangement run | **`run_id`, `topic_seed_path`, `topic_chunk_index`, `subtopic_ordinal`, `cluster_ordinal`**; then `label`, `document_count`, `topic_order`, `subtopic_order`, `cluster_order` |
| `synthesis_doc` | `cluster_synthesis_doc` | `synthesis`, the generation run | the same five; then `title`, `prose` |
| `call_exemplar` | `cluster_call_exemplar` | `synthesis`, the generation run | the same five and `citation_ordinal`; then `occurrence_id`, unique with the five |
| `cluster_fault` | `standing_cluster_fault` | `synthesis`, the generation run | the same five; then `kind`, `detail` |

- **`subtopic_ordinal` is the survivor's subtopic within its topic, numbered as `Communities` numbers it, or `-1` where the topic has no subtopics** (ADR-406 §2, §5), and `cluster_ordinal` is numbered within the subtopic where there is one, else within the topic. A `CHECK (subtopic_ordinal >= -1)` refuses any other negative. `-1` and not `NULL`: a key holding `NULL` is unique to SQLite whatever else it holds, and `cluster_call_exemplar`'s uniqueness is what keeps one document from being listed under two citations (ADR-133). A reader of the rows hands it on as an empty `OptionalInt`.
- **The three orders of `arranged_cluster`** are stored, as ADR-112 stores its two, because the operator approves a specific arrangement: `topic_order`, the topic's place at the top level counted from 1, by the survivors placed under it, most first, ties on the topic key (ADR-396 §4); `subtopic_order`, the subtopic's place within its topic counted from 1 by ADR-406 §4, and `0` where the topic has none; `cluster_order`, ADR-112's rule within the subtopic, or within the topic where it has none, as the arrangement step numbers it now.
- **A topic no survivor is placed under has no `arranged_cluster` row**, so no place at the top level and no folder (ADR-396 §2).
- **No column names a seed partition**: `partition_order` has no successor of that name, and no new column, type or line names one.
- `cluster_member` is read for one topic by `cluster_member_by_run_topic_subtopic_and_cluster (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal)`, its sizes counted in that order; `cluster_call_exemplar` keeps `cluster_call_exemplar_by_occurrence_id (occurrence_id)` for its reference (ADR-173). No synthesis table references `file_occurrence` through the topic, so the four indexes on `winning_seed_occurrence_id` have no successor.

### 4. Names and overviews are keyed by the topic

Five tables, each `synthesis`'s:

| Table | Run | Columns, the key in bold |
| --- | --- | --- |
| `topic_name` | the arrangement run | **`run_id`, `topic_seed_path`, `topic_chunk_index`**, `name`, `origin`, `turn_down` |
| `subtopic_name` | the arrangement run | **`run_id`, `topic_seed_path`, `topic_chunk_index`, `subtopic_ordinal`**, `name`, `origin`, `turn_down` |
| `topic_overview` | the generation run | **`run_id`, `topic_seed_path`, `topic_chunk_index`**, `writing`, `asked` |
| `topic_overview_source` | the generation run | **`run_id`, `topic_seed_path`, `topic_chunk_index`, `number`**, `subtopic_ordinal`, `cluster_ordinal` |
| `topic_overview_fault` | the generation run | **`run_id`, `topic_seed_path`, `topic_chunk_index`**, `kind`, `detail`, `asked` |

- **`origin` is `NameOrigin`'s name, `WRITTEN`, `WRITTEN_WHEN_ASKED_AGAIN` or `FALLBACK`, and `turn_down` is `NameTurnDown`'s, one of ADR-407 §6's seven, where the origin is `FALLBACK` and `NULL` otherwise**; a `CHECK` holds that a row has a turn-down exactly where it fell back. `subtopic_name.subtopic_ordinal` is at least 0.
- **`topic_overview.asked` is the question the writing was believed at, 1 or 2**; `topic_overview_fault.asked` how many answers were asked for, 0, 1 or 2; `kind` is `ClusterFaultKind`'s, with ADR-402 §7's detail. `topic_overview_source` holds one row for each number the call sent, naming the cluster at that number (ADR-402 §7). A topic has a row in `topic_overview` or in `topic_overview_fault`, never both.
- **Every topic that stands is named**, a topic no survivor is placed under among them: its name is what the report of topics with no document ([#834](https://github.com/algernon28/vespera/issues/834)) shows beside its seeds.

### 5. Which run and step writes each table, and what a step doing its work again does

| Tables | Written by | A step doing its work again under its run (ADR-116, ADR-118, ADR-230) |
| --- | --- | --- |
| `topic`, `topic_passage`, `seed_feeding_no_topic` | the relevance-scoring step, 5d, once the breadth floor is applied and before the first survivor is scored; nothing where the step stops for no topic standing (ADR-401 §1) | discarded with the step's other rows, `relevance_score`'s |
| `winning_topic`, `see_also` | 5d, each document's with its score | the same |
| `cluster_member` | the clustering step, 5f | discarded as `document_cluster` was, unfinished or decided again (ADR-230) |
| `topic_name`, `subtopic_name` | the arrangement step, 6a, at its head | **kept**, and read back (§6) |
| `arranged_cluster` | 6a | discarded as `cluster` was |
| `cluster_synthesis_doc`, `cluster_call_exemplar`, `standing_cluster_fault` | the generation step, 6b | as their predecessors: resumed, never discarded (ADR-111, ADR-133, ADR-319) |
| `topic_overview`, `topic_overview_source`, `topic_overview_fault` | 6b, after the walk over the clusters (ADR-402 §6) | kept: an overview is asked once under its run, or twice, and never again |

**An overview's rows are written in one transaction**, its sources with its writing, or its fault alone, so that no row of a topic is left without the others and none has to be cleared before a topic is asked: nothing deletes from the three tables.

**The arrangement step reads the topics 5d kept, and finds none again.** It builds each `Topic` from `topic` and `topic_passage` under the scoring run it arranges, in key order, and reads the seeds' vectors only to put a topic's passages strongest first (ADR-407 §4).

### 6. A name is kept under its run, read back, and never asked for again

**A name is recorded under the arrangement run as soon as it is given**, before the next sibling is asked, with its origin and turn-down. Topics are named first, in key order, then each topic's subtopics, topic by topic in key order and each topic's in ADR-406 §4's order; that is also ADR-407 §8's order of siblings.

**A name recorded under the run is read back and not asked for again**, by this invocation or any later one that arrives at the run: whether the step finished, failed after naming some, or does its other work again. A sibling with no recorded name is asked, the recorded names of its earlier siblings counting as taken (ADR-407 §8). So a serving engine that stops answering part-way (ADR-407 §6) costs the next invocation only the names it did not give, and every name the arrangement shows is the one first given under its run: the same question does not always draw the same name (ADR-407, Measured).

**Where the serving engine gives no answer, the step fails without throwing**, as ADR-111 has 6b's breaker fail, since everything a tasklet writes is inside its step's transaction and an exception would roll back the names given before it and the arrangement run with them: it records its closing line through `RecordedClosingLines`, sets the step's status to failed and returns, and no completion is recorded. The line is *"the arrangement step stopped: the naming model gave no answer for `<level>` `<key>` -- `<what the engine said>`; the names given before it are kept, and the next invocation asks only for the rest"*, `<level>` being `topic` or `subtopic <n> of topic`, and `<key>` the topic's key as ADR-407 §9's line writes it.

**ADR-407 §9's line stays for each name given**, and a subtopic's reads `Stage 6a (arrangement) subtopic <n> of topic <path> chunk <i> named "<name>" (<origin>)`, `<n>` its number within its topic. A name read back is not logged again.

**A subtopic is named as ADR-407 §4 and §7 have it**: its survivors' leading chunks, strongest first by the cosine of each survivor's mean vector to the subtopic's centroid, two of equal cosine in the order of their paths compared as ADR-399 §4 compares a seed's, under the 1,000-word budget; its fallback the title, then the filename stem, of its strongest survivor, then `Subtopic n` in English and `Sottoargomento n` in Italian, `n` its place among its topic's subtopics in ADR-406 §4's order, counted from 1. The centroid is taken in one pass over the subtopic's members, summing their mean vectors each taken to unit length, and each member's cosine in a second, so that no mean vector is held past its member (ADR-085). `TopicFallbackName.ofSubtopic(Optional<String> doclingTitle, String survivorPath, int place, DeliverableLanguage language)` gives the fallback.

### 7. The 6a gate shows every name read-only, and marks a fallback by its turn-down

`arrangement.html` goes through the topics in `topic_order`. **A topic's heading is its recorded name**, `<h2>`; under a topic with subtopics, each subtopic's heading is its recorded name, `<h3>`, in `subtopic_order`, with its line of counts and its table of clusters; a topic without subtopics has its line of counts and its table under its heading, as a seed partition had.

**Where a name fell back, a paragraph stands directly after its heading**, in English whatever the deliverable language (ADR-321 §4), `<level>` being `topic` or `subtopic` and `<why>` the turn-down's words below:

> The naming model's name for this `<level>` was not used: `<why>`. The name above stands in for it, taken from the seed feeding the topic the most passages, or from the topic's place where that seed gives none.

and, for a subtopic, the second sentence is *"The name above stands in for it, taken from the document most like the rest of the subtopic, or from the subtopic's place where that document gives none."*

| Turn-down | `<why>` |
| --- | --- |
| `NOTHING_SENT` | nothing of it fitted what the model is given to read, so the model was not asked |
| `QUESTION_CUT` | the question was longer than the model could read at once |
| `RAN_OUT_OF_ROOM` | the model's answer reached its length limit before it was finished |
| `NOT_THE_SHAPE_ASKED` | the model's answer did not come back in the shape it was asked for |
| `EMPTY` | the model's answer was empty |
| `NOT_ONE_SHORT_LINE` | the model's answer was not one short line |
| `SAME_AS_AN_EARLIER_NAME` | asked a second time, the model again gave another `<level>`'s name |

A name the model wrote, at the first asking or the second, carries no paragraph. **The page's opening says what the names are**, in place of *"nothing on it was written for you"*: *"The names of the topics, and of the subtopics a crowded topic is divided into, were written by the naming model from the documents' own words. They are shown here to read: they are not part of what you approve, and they cannot be changed here. Every group's name below was taken from a document you can open."* The page's other words that say *exemplar* say *topic*. Nothing on the page edits a name: the approval names the arrangement run, as before (ADR-107). `arrangement.json` follows the page, one section a topic and one a subtopic, each titled by its name.

### 8. The clustering step divides each topic

**The clustering step goes through the topics that have a placed survivor, in key order, and clusters each topic's placed survivors that stand under the scoring run** (ADR-396 §3), read from `winning_topic` in occurrence order and filtered by `Verdicts.survivingAmong`, as the members of a partition were. Each is divided by `TopicDivision.of` at clustering's neighbours and resolution and at the subtopic floor (ADR-406 §1, §2), and every member's subtopic and cluster are recorded in `cluster_member`. A seed partition is clustered nowhere.

**ADR-230's check is made topic by topic**: no placed member that survives lacks a `cluster_member` row, and no clustered document has stopped surviving.

**`subtopicFloor` enters the embedding-scoring run's identity** as ADR-406 §5 has it once wired: `StageRuns` resolves `vespera.clustering.subtopic-floor` where the scoring run is minted, appends `subtopicFloor`, a whole number, after `seeAlsoShare`, `@JsonInclude(NON_NULL)` and written only where it is not 300, and hands the value to the step. At 300 no member is written, and the configuration's text stays what `RunIdentityGoldenTest` holds.

**`cluster-sizes.html` has one row a topic** until #834 settles what it shows of topics: its five numbers taken over the topic's clusters, across its subtopics, and the spread of the graph over the whole topic; the row names the topic by its key, `<path> chunk <i>`.

**5d's closing line on placement** becomes `Stage 5d (relevance scoring) placed <n> survivor(s) under the <t> topic(s) that stand`.

### 9. Generation goes through topics, and writes each topic's overview

**The walk over the clusters goes topic by topic in `topic_order`, and within a topic in the order the arrangement lists its clusters**, subtopic by subtopic in `subtopic_order` and each subtopic's in `cluster_order` (ADR-406 §4); a cluster's exemplars are drawn from its topic's members. Every other rule of the walk is ADR-111's, ADR-190's and ADR-319's.

**The overviews are asked after the walk, topic by topic in `topic_order`**, a topic asked once each of its clusters is settled (ADR-402 §6), its name read from `topic_name`, its numbers being its clusters' places in the order just given, and its rows written as §5 has them. A topic with a row in either of `topic_overview` and `topic_overview_fault` is not asked again. **The generation step is recorded finished** where ADR-319 §2 holds of its clusters and every topic has a row in one of the two, or has no written cluster (ADR-402 §6).

**The generation run's configuration gains two members after `deliverableLanguage`, each always written**: `overviewTemperature`, `0.0`, and `overviewSeed`, `1` (ADR-396 §9, ADR-402 §3, §8), as the arrangement run writes its naming call's (ADR-407 §3).

### 10. What the renderings draw until #833

#833 decides how the three renderings draw a topic, a subtopic, an overview and a link. Until it does, the tree, the Confluence rendering and the website rendering draw two levels, as they do now:

- **A topic stands where a seed partition stood**: its folder carries ADR-112's order prefix, padded to the count of topics drawn, and is named from the topic's recorded name as a cluster's folder is named from its label, and no longer from a seed's filename stem (ADR-396 §4). Its index heading is its name.
- **A crowded topic's clusters are drawn in one sequence**, subtopic by subtopic, numbered and padded over the topic's clusters; no subtopic is drawn as a level.
- **`documents.csv` keeps its header** (ADR-112, ADR-122): `seed_partition` holds the topic's name and `partition_order` its `topic_order`, until #833 and #835 rename them.
- **No overview and no link is drawn.**

### 11. No schema version moves, and a working directory an earlier build used opens

**Every table of §2 to §4 is new, and no table that exists changes shape**, so no module's schema version moves (ADR-197 §3): `embedding` stays at 7 and `synthesis` at 4. `schema.sql` creates the fifteen tables and their five indexes, and **no longer creates `document_cluster`, `cluster`, `synthesis_doc`, `call_exemplar` or `cluster_fault`, nor their eight indexes**: `document_cluster_by_run_id`, `document_cluster_by_winning_seed_occurrence_id`, `document_cluster_by_winning_seed_run_and_cluster`, `cluster_by_winning_seed_occurrence_id`, `synthesis_doc_by_winning_seed_occurrence_id`, `call_exemplar_by_winning_seed_occurrence_id`, `call_exemplar_by_occurrence_id` and `cluster_fault_by_winning_seed_occurrence_id`.

**A database an earlier build wrote keeps those five tables, their rows and their indexes, and no statement names them**, as ADR-392 §2 has it of `translation_of`: nothing drops them, as nothing removes a run's rows (ADR-229). It opens with no step of the operator's: `schema.sql` adds the new tables beside them, the guard finds the versions it expects, and `vector`, `relevance_label` and every cache are read as before. Every run that reads the old tables moves (§15), so none of their rows is a run the new code arrives at.

**The tables keyed by a run are thirty-eight**: the twenty-eight less the five, and the fifteen.

### 12. The statements that sort, and the indexes

**No statement this record adds sorts.** Each read of §2 to §4 is of one run's rows of one topic, one subtopic or one cluster, planned by a primary key or by one of the five indexes of §2 and §3, whose columns are in the order the read wants its rows; where a read wants another order, its rows are read unordered and put in order on the heap, as ADR-223 §10 has three reads do. The topics of a run are read in key order by `topic`'s primary key, `run_id` first; the topics of an arrangement by grouping `arranged_cluster`'s rows on its primary key, put in `topic_order` on the heap, one row a topic.

**ADR-218's rows 10 and 11 are gone**: `RelevanceScoreCache`'s read of a run's winning seeds and of one seed's members, which 5f and 6a made through `Clustering.partitions`, `Clustering.membersOf` and `RelevanceScoring.winningSeeds`, go with those methods. `RelevanceScoreCache` then holds no statement that sorts, and ADR-218 §3's class is row 7 alone, the review list's read by path, with its measured size. `EveryStatementThatSortsIsRecordedTest` holds no entry for `embedding.RelevanceScoreCache`, and no new entry.

**The five new indexes are built on tables that hold no row when `schema.sql` first runs over them**, on a new database and on one an earlier build wrote alike, so none is a build over rows ADR-218 §2 excepts, and start-up announces none. `schema.sql`'s indexes are thirty: the thirty-three, less the eight of §11, and the five.

### 13. One topic at a time

**What ADR-223 bounds to one seed partition is bounded to one topic**:

- **5d** holds the resident seed vectors and the topics, as ADR-399 §6 and ADR-400 §5 have it: the seed set's size. It records the topics, their passages and the seeds feeding none from what it holds, and each document's place as it is scored.
- **5f** reads one topic's members at a time, a content hash each, and lets them go before the next (ADR-211 §5). It builds one graph over the topic and, where the topic is divided, one graph over one subtopic at a time, in ADR-087's blocks (ADR-406 §5); it keeps five numbers a topic for the size report (ADR-223 §2).
- **6a** reads the topics' member counts, one count a topic, and orders the topics from them; then it arranges, labels, records and draws one topic at a time, holding its members, their scores and its clusters, and lets them go before the next (ADR-223 §3). While it names a subtopic it holds one number a member (§6).
- **6b** holds one topic's members for its clusters' exemplars, asks a cluster's synthesis doc and fault by key, and writes the tree a topic at a time (ADR-223 §5, §6). An overview holds the writing of the topic's written clusters that fit the window, one topic at a time.

**What is still held, and why**, is ADR-223 §9's with *topic* for *seed partition*: one row a topic, bounded by the topics that stand, at most two hundred at the shipped floor (ADR-401); the largest topic, at 5f, 6a and 6b, which may hold every placed survivor where one topic stands; `unsendable`; the furniture rule's distinct pictures; one page of 1,000 rows. Nothing held grows with the corpus beyond what a partition's did.

### 14. Owners, and the account's counts

The line above each `CREATE TABLE` names its owner (ADR-209 §3): `embedding` owns `topic`, `topic_passage`, `seed_feeding_no_topic`, `winning_topic`, `see_also` and `cluster_member`; `synthesis` owns `topic_name`, `subtopic_name`, `arranged_cluster`, `cluster_synthesis_doc`, `cluster_call_exemplar`, `standing_cluster_fault`, `topic_overview`, `topic_overview_source` and `topic_overview_fault`. No other module names them in SQL: `pipeline` asks `embedding`'s and `synthesis`'s classes, and hands `synthesis` the topic key as values. **`InvocationAccount`'s exception keeps its six counts and its seven tables**, the three of `synthesis`'s being `arranged_cluster`, `standing_cluster_fault` and `cluster_synthesis_doc`.

### 15. Which run ids move, and why

A stage's version is the last commit touching the modules `StageModules` names for it (ADR-058), and a run's id folds in its upstream runs' (ADR-048). `StageModules` names `embedding` and `extraction` for seed measurement and embedding scoring, and `synthesis`, `extraction` and `embedding` for the arrangement and generation.

**The code that builds this record touches `embedding`, `synthesis`, `deliverable` and `pipeline`, and no class of `corpus`, `extraction`, `similarity`, `ledger` or `profile`.** So:

- **Seed measurement** moves, its version naming `embedding`, for work that comes out the same.
- **Embedding scoring** moves with `embedding`, and writes the topics and the placement it held before; where a machine's subtopic floor is not 300, its configuration names `subtopicFloor` and it is another run.
- **The arrangement** moves with `synthesis` and `embedding`, over a new scoring run, and is approved again unless `arrangementApproved` holds `auto`.
- **Generation** moves with them and with its two new members, and writes every cluster again under its new run.
- **The walk, byte-level reduction, extraction, content census and content redundancy do not move**: no module their versions name is touched.

## Alternatives refused

- **Re-keying the five tables in place, and placement as two columns of `relevance_score`.** Each is a change of shape, so `embedding` and `synthesis` move their versions (ADR-059), and a working directory written before is refused with the instruction to delete and recreate those modules' tables, `vector` and `relevance_label` among `embedding`'s: every chunk embedded again, and the labels a person gave, which no run can make again, lost. An index on a new column would fail `schema.sql` on that database before the guard spoke. New tables cost five unread tables in an old database.
- **A number of the run's own for a topic**, its rowid or its place among the topics, in the tables that name it. It is shorter than a path, but it is the ordinal ADR-399 §5 refused as a key, moving with every topic before it; and a row naming a topic by it would mean nothing without the scoring run's `topic` rows beside it.
- **`NULL` for a topic with no subtopics.** A primary key or `UNIQUE` holding a `NULL` is never violated in SQLite, so `cluster_call_exemplar` could list one document twice under one cluster (ADR-133).
- **Discarding the names with the rest of the step's work** (ADR-116). A stopped step would pay every call again, and the same question asked again drew another name in 3 of 30 (ADR-407, Measured): the names shown would depend on how often the step was stopped.
- **The top-level order in `topic`.** It is counted over the survivors placed and standing, which the relevance floor's step decides again on every invocation (ADR-118), and is what the operator approves at 6a: it is the arrangement's, as `partition_order` was.
- **Finding the topics again at 6a**, as ADR-407's build does until this record. Two passes over the same vectors must agree, and nothing held that they did; one recorded set is read by both.
- **Leaving the overviews' walk to #833.** Their rows need the names and the clusters' places this record stores, and generation's identity needs their two members before a run is minted; #833 draws what is recorded.

## Consequences

- **The topics, each document's place and the topics it fits, the subtopics, the names and the overviews are on record**, each under the run that decides it, and a name, once given, is the one every later reading of the run shows.
- **The 6a gate shows topic › subtopic › clusters**, each name read-only, a fallback saying why the model's name was not used; the arrangement approved is still named by its run alone.
- **A crowded topic is divided** from the first run this build makes, three levels deep, and the clustering step costs a seventh more on it (ADR-406).
- **A working directory an earlier build used opens as it is.** Its old cluster rows stay, unread; its vectors, labels and caches are read again; stages 1 to 4 are not done again; stage 5 scores and clusters again from the cached vectors, the arrangement is approved again, and 6b writes every cluster again.
- **The deliverable's folders are named from the topics' names** until #833 says otherwise, and `documents.csv` keeps its header with the topic in its two columns.
- **ADR-105, ADR-218, ADR-220, ADR-223, ADR-229, ADR-236, ADR-400, ADR-406 and ADR-407 are amended** as the head of this record says. ADR-406's Tests section is corrected in place for the test this record replaces (ADR-337 §5).
- **`CONTEXT.md`'s Topic name** no longer says a name is not yet stored.
- **What `AGENTS.md`, `README.md` and `docs/architecture.md` say of seed partitions, of the twenty-eight tables and of the excepted reads** is brought to this record by [#835](https://github.com/algernon28/vespera/issues/835).

## Tests

| Class | What it holds |
| --- | --- |
| `TheTopicLedgerTablesTest.stageFivesTablesKeepTheTopicsTheirPassagesAndEachDocumentsPlace` | `topic`, `topic_passage`, `seed_feeding_no_topic`, `winning_topic` and `see_also`: their columns, types and keys, owned by `embedding` (§1, §2) |
| `…theClusterTablesAreKeyedByTheTopicTheSubtopicAndTheOrdinal` | `cluster_member`, `arranged_cluster`, `cluster_synthesis_doc`, `cluster_call_exemplar` and `standing_cluster_fault`: their columns and keys, `cluster_call_exemplar`'s uniqueness, and their owners (§3) |
| `…theNamesAndTheOverviewsAreKeyedByTheTopic` | `topic_name`, `subtopic_name`, `topic_overview`, `topic_overview_source` and `topic_overview_fault`: their columns, keys and owner (§4) |
| `…theReadsOfOneTopicSeekByAnIndexLedByTheRunAndTheTopic` | the five indexes, their tables and columns in order (§2, §3, §12) |
| `…aSubtopicBelowNoneAndAFallbackWithNoTurnDownAreRefused` | a subtopic of -2, a fallback with no turn-down and a written name with one are refused (§3, §4) |
| `…aDatabaseAnEarlierBuildWroteOpensKeepingItsOldTablesUnread` | the five old tables with a row each, and the versions 7 and 4: `schema.sql` applies over them, keeps their rows and adds the fifteen tables, and creates none of the five on a new database (§11) |
| `EveryTableKeyedByARunIsOnRecordTest` | the thirty-eight tables and their owners; the ten no shipped statement deletes from (§5, §11) |
| `EachTableIsNamedOnlyByItsOwnerTest` | the account's seven tables, `synthesis`'s three renamed (§14) |
| `EveryStatementThatSortsIsRecordedTest` | no entry for `embedding.RelevanceScoreCache`, and the thirty indexes (§12) |
| `pipeline.TheSubtopicFloorNamesTheScoringRunInvocationTest.aFloorOtherThanTheShippedOneEntersTheScoringRun` | a machine naming a floor of 1 gets a scoring run whose configuration ends with `subtopicFloor` 1 (§8). It replaces `TheSubtopicFloorNamesNoRunYetInvocationTest`, deleted |
| `pipeline.ClusteringDividesEachTopicInvocationTest.aTopicAtTheFloorIsDividedAndItsClustersAreNumberedWithinEachSubtopic` | three survivors of one topic at a floor of three, at a resolution that parts them: subtopics 0, 1 and 2, each one cluster numbered 0, every row naming the topic's key (§3, §8) |
| `…aTopicUnderTheFloorHasNoSubtopic` | two survivors under that floor: every row's subtopic is -1 (§3, §8) |
| `pipeline.TheTopicsAndPlacementAreStoredInvocationTest.theTopicItsPassageAndTheSurvivorsWinningTopicAreStoredUnderTheScoringRun` | one seed of one passage: its topic of one passage, the passage, the one survivor's winning topic, no link and no seed feeding none, all under the one scoring run (§2, §5) |
| `…aTopicTheFloorDropsIsNotStoredAndItsSeedsAreListed` | four seeds on one subject and two on another at a floor of 0.6: one topic of four passages, the two other seeds listed in path order, and the survivor placed under the one that stands (§2) |
| `pipeline.TopicNamesAreStoredAndShownInvocationTest.eachTopicsNameIsStoredUnderTheArrangementRunWithItsOrigin` | the one topic's name, written, with no turn-down, under the one arrangement run (§4, §6) |
| `…theArrangementPageHeadsEachTopicWithItsStoredName` | `arrangement.html` heads the topic with the name recorded, and no fallback paragraph follows it (§7) |
| `…aFallbackNameIsStoredWithItsTurnDownAndMarkedOnThePage` | an empty answer: the seed's title recorded as a fallback turned down as empty, and the page's paragraph under it, whole (§4, §7) |
| `…aNameRecordedBeforeTheStepStoppedIsNotAskedForAgain` | two topics, the engine refusing the second's question: the invocation fails, the arrangement run and the first name recorded; the next arrives at the same run, asks the second alone, and both names stand (§6) |
| `pipeline.SubtopicsAreNamedAndShownInvocationTest.eachSubtopicIsNamedAfterTheTopicAndStoredInItsOrder` | a topic of three subtopics: four names asked, the topic's first, and the subtopics' recorded under their numbers in order (§6) |
| `…thePageShowsEachSubtopicUnderItsTopicInOrder` | the topic's heading, then the three subtopics' in order (§7) |
| `…aSubtopicsFallbackIsMarkedOnThePage` | empty answers: each subtopic's fallback recorded and its paragraph on the page, whole (§6, §7) |
| `synthesis.SubtopicFallbackNameTest.titleThenStemThenItsPlaceInTheDeliverableLanguage` | a survivor's title untranslated, then its stem, then `Subtopic 2` or `Sottoargomento 2` (§6) |
| `pipeline.TheTopicOverviewIsRecordedAtGenerationInvocationTest.eachTopicsOverviewIsRecordedWithTheClustersItWasWrittenOver` | through 6b: the one topic's overview, believed at the first asking, and its one source naming the topic's one cluster, under the generation run (§4, §9) |
| `pipeline.RunIdentityGoldenTest.generation` | the generation run's settings end with `overviewTemperature` 0.0 and `overviewSeed` 1 (§9) |
| `pipeline.RunIdentityGoldenTest.embeddingScoring` | at the shipped floor no `subtopicFloor` is written (§8) |
| `pipeline.PlacementIsReadInTheScoringPassInvocationTest` | 5d's closing line on placement, as §8 gives it |

The tests that read the five old tables by name read the new ones: `ClusteringInvocationTest`, `TheClusteringPresetsInvocationTest`, `ClustersAreOfTheSurvivorsAsTheyStandInvocationTest`, `RelevanceFloorInvocationTest`, `RepeatedInvocationTest`, `AProfileEditedWhileStageFiveRunsInvocationTest`, `APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest` and `ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest`.

## What the implementation owes

- **`schema.sql`**: §2 to §4's fifteen tables, each with its owner line, its comment and its `CHECK`s; §2's and §3's five indexes; and the five old tables and their eight indexes taken out (§11).
- **In `embedding`**: a class recording and reading `topic`, `topic_passage` and `seed_feeding_no_topic`, its read answering the `Topic`s that stand in key order; one recording and reading `winning_topic` and `see_also`, `RelevanceScoring.scoreAndRecord` recording a document's `Placement` beside its score and `discardForRun` discarding all five; `DocumentClusters` and `DocumentCluster` over `cluster_member`, the topic key and the subtopic in place of the winning seed; `Clustering`'s reads of a topic's members, and `clusterAndRecord` dividing through `TopicDivision` at the floor handed to it (§8); `Clustering.partitions`, `Clustering.membersOf`, `RelevanceScoring.winningSeeds` and `RelevanceScoreCache`'s two reads gone (§12).
- **In `synthesis`**: the topic key as a value of its own, since `synthesis` names no `embedding` type; `ClusterSlot`, `ArrangedCluster`, `RecordedCluster`, `ClusteredDocument` and the reads of `Clusters`, `SynthesisDocs`, `ClusterFaults` and `ClusterExemplars` keyed by it, the subtopic and the ordinal, over §3's tables; the types that say partition, `Partition`, `ArrangedPartition` and `ListedPartition`, renamed for the topic; `Arrangement`'s three orders (§3); the names' recording and reading (§6), `TopicFallbackName.ofSubtopic`; and the overviews' walk and rows (§9), with `RepeatedFaults`' completion in `pipeline`.
- **In `deliverable`**: the tree, the manifest and the two plugins' drawings read a topic where they read a seed partition, as §10 has it.
- **In `pipeline`**: `RelevanceScoringTasklet` recording the topics and the placement and saying §8's line; `ClusteringTasklet` going through topics (§8); `StageRuns`' `subtopicFloor` member and the generation run's two (§8, §9); `ArrangementTasklet` reading the topics, naming and recording as §6 has it, and drawing the page as §7 has it through `ArrangementReport`; `GenerationTasklet` going through topics and asking the overviews (§9); `InvocationAccount`'s three counts over the new tables (§14); `ClusterSizeReport`'s row a topic (§8). `PipelineHoldsOnlyTheRulesOnRecordTest`'s sets of the types each tasklet names follow the types the build gives them, and a class added to `pipeline` is put on its record.
- **`CONTEXT.md`**: Topic name's sentence on storage (Consequences).
- **The tests the re-keyed types reach**, adapted in their keys and fixtures with their claims unchanged: those of `embedding` on clustering and memberships (`ClusteringTest`, `DocumentClustersTest`, `ClusteringBeans`); of `synthesis` on the arrangement, the clusters, the docs and the faults (`ArrangementTest`, `ClusterFaultsTest`, `ClusterGenerationTest`, `ClusterSlotTest`, `LeadDocumentTest`, `PartitionsOfAnArrangementTest`, `SynthesisDocsTest`, `ListedDoc`, `ListedFault`); of `deliverable` and the two plugins that build an arrangement (`ListedArrangement`, `ListedTree`, `ThreePartitions`, `TwoClustersWithPictures`, `ScriptedPlugin`, `WebsiteFixture` and the tests over them); `WholeRun`; the generation invocation tests that write `cluster` and `document_cluster` rows by hand (`GenerationInvocationTest`, `GenerationFaultInvocationTest`, `GenerationBreakerInvocationTest`, `GenerationPromptRefusedInvocationTest`); `InvocationAccountTest`; `NoClassOfTheLastThreeStagesReadsAWholeRunTest`; and `PipelineHoldsOnlyTheRulesOnRecordTest`.

## What this does not decide

- **How the three renderings draw a topic, a subtopic, an overview and a "see also" link**, and a topic's folder name past §10: #833.
- **The reports**: seeds feeding no topic, topics with no document, cluster sizes per topic, and how ADR-045's alarm reads for a topic: #834.
- **Whether the invocation account or a report counts overviews written and turned down**: #834's, if any (ADR-402).
- **Removing the five old tables, and the rows of earlier runs, from a database**: a record of its own, as ADR-392 left it for `translation_of`.
- **The names in `documents.csv`'s header and in the documents**: #833 and #835.
</content>
</invoke>
