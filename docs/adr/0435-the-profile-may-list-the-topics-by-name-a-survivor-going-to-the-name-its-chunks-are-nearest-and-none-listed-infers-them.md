# ADR-435 — The profile may list the topics by name, a survivor going to the name its chunks are nearest, and none listed infers them

> **Partly amended — see [ADR-434](0434-the-profiles-outputs-choose-what-stage-6b-draws-and-a-retrieval-plugin-draws-records-and-vectors-into-a-jsonl-file.md).** Of §9: the sidebar is eleven sections, `outputs`, titled Outputs and holding a checkbox for each output stage 6b may draw, standing after `code-sets`. The rest of this record stands.

- **Date**: 2026-11-20
- **Status**: accepted on 2026-11-20.
- **Decided**: by the operator, on [#898](https://github.com/algernon28/vespera/issues/898): that `profile.yaml` gains a key, `topics`, a list of names, and that where it is empty or absent the topics are inferred as before (§1); that it is edited in a section of the sidebar of its own, Topics, beside Seed folder, with an entry box, Add and a Remove on each row, built as the code set folders' rows are, and saved into `profile.yaml`, so that editing there is editing the file (§9); that it holds names alone, no description, and that one topic is allowed (§1); that a blank or duplicate entry is refused in the window with a warning, *Pagamenti is already a topic*, Add staying disabled, two names being one where they match ignoring case, accents and the spaces around them (§2, §9); that a file holding a blank or duplicate entry refuses `vespera run` at its start, naming the entry, the reason shown in the ending panel and the profile's row drawn in the error colour (§3, §9); that each name is embedded alone, exactly as written, with no template (§5); that a survivor goes under the topic its chunks are nearest on average, and is linked from every other topic at least 15% of its chunks are nearest, ADR-400's rule unchanged (§5); that a survivor far from every topic still goes to its nearest, and `topics.html` counts the documents placed with low similarity (§5, §10); that relevance is untouched, the seeds alone scoring it, and the list changing placement and the top level and never the floor, the labels or a verdict (§6); that *topic* is the one term, with no distinction between topics listed and inferred (§13); that a listed name is shown as written, never named by a model and never translated (§7); that the subtopics of a crowded topic are still inferred and named by the model (§7); that a listed topic has no breadth floor, and one no document lands under is kept, has a page, and is listed among `topics.html`'s topics with no document (§4, §8); that a topic's identity is its name made plain, a reordering changing only the order and a renaming making a new topic whose earlier overview is not carried over (§2, §7, §11); that editing the list starts new runs from where the topics are found onward (§11); that `topics.html` for a list of names drops the seed passages' columns and the seeds feeding no topic, and shows per topic the documents placed under it, those linked from it and the low-similarity count (§10); and that 6b's overviews, the 6a gate showing the names read-only, the see-also links and determinism are unchanged (§7, §8, §11). By the operator, on #898, of what was left open: that a document is placed with low similarity where its score at its winning topic is under a cosine floor, a preset measured for the shipped embedder on public text alone and never on an operator's documents, and that each document's score at its winning topic is stored (§5, §14); and that the top level follows the list's order (§7). Under the operator's delegation on #898: which records are amended and how far (Amends, below), and which runs move (§11). The rest is this record's: the value's form and what a line of it is (§1); how a name is made plain (§2); the refusal's words and its place among the start's checks (§3); how a listed topic is keyed and stored (§4); where and when the names are embedded (§5); where and how the score is stored, the floor's key, where it is applied, which run it enters and how it is measured (§4, §5, §14); what the page of a topic no document lands under says (§8); the section's ids, words and Save (§9); the words of the pages and lines that name a listed topic (§10); the identity member (§11); and the tests.
- **Amends**: [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §1, *"How many there are is inferred from the passages, never set by the operator"*, and the breadth floor; §2, *"A topic no survivor is placed under has no folder"*; §4, the order of the top level; §6, *"The local model names each topic, in the deliverable language"*; §9, *"The same seed set, models and configuration give the same topics"*; and §10, the vocabulary: where the profile lists topics, they are the top level, in the list's order, under their names as written, each standing, one no document lands under having a page (§4, §7, §8, §13). It carries a note at its head.
- **Amends**: [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md) §5 and §6: where the profile lists topics, none is inferred, and a listed topic's key is its name made plain, not a first passage (§4). It carries a note at its head.
- **Amends**: [ADR-400](0400-a-survivor-goes-to-the-topic-its-chunks-are-nearest-on-average-and-is-linked-from-one-15-per-cent-of-them-are-nearest.md) §1 and §5: a listed topic's centroid is its name's vector, the cosines to it taken in the scoring pass beside those to the seed passages (§5); and, for listed topics, §5's *"No score at a topic need be stored"* and §1's *"no floor, label or report reads it"*: a document's score at its winning listed topic is stored, and `topics.html` counts those under the low-similarity floor (§5, §14). The rule, the share and the ties stand, and the score stays no relevance score. It carries a note at its head.
- **Amends**: [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md)'s count of the tables keyed by a run, as ADR-409 §11 left it: this record adds two, `listed_topic` and `winning_topic_score` (§4). It carries a note at its head.
- **Amends**: [ADR-428](0428-the-presets-with-no-profile-key-are-shown-read-only-in-command-options-a-list-for-each-run-they-enter.md) §2: once `vespera.topics.low-similarity-floor` ships, the embedding-scoring run's list shows it after the four of the topics (§14). It carries a note at its head.
- **Amends**: [ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md) §1, §4 and §5: no floor is applied to listed topics, every one stands, and no seed is listed as feeding none (§4). It carries a note at its head.
- **Amends**: [ADR-402](0402-a-topics-overview-cites-its-written-clusters-by-number-in-every-sentence-and-a-citation-fault-is-asked-once-more.md) §5: a topic no document is placed under has a sentence of its own where its overview would be (§8). It carries a note at its head.
- **Amends**: [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) §1, §6 to §9: a listed topic is not named by the naming model, has no fallback, and its name enters no sibling's comparison; its subtopics are named as before (§7). It carries a note at its head.
- **Amends**: [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) §1, §2, §3, §4, §6 and §8: a listed topic is written in a row as its name made plain and the chunk index `-1`; a new table, `listed_topic`, keeps each listed name as written and its place; `topic_order` is the list's place; `topic_name.origin` gains `LISTED`; and a line or a page that writes a topic's key as `<path> chunk <i>` writes a listed topic's name instead (§4, §7, §10). It carries a note at its head.
- **Amends**: [ADR-413](0413-save-my-labels-and-auto-label-are-a-labels-section-of-the-sidebar-and-the-window-calls-no-label-an-answer.md) §1, the sidebar of nine sections: it is ten, `topics` after `seed-folder` (§9). It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, as ADR-389 and ADR-413 left its sidebar: the Topics section stands after Seed folder (§9). It carries a note at its head.
- **Amends**: [ADR-415](0415-a-topic-is-a-page-carrying-its-overview-its-subtopics-are-pages-beneath-it-and-a-see-also-link-lands-on-a-groups-page.md) §1, §2, §4, §5 and §7: a listed topic no document is placed under is drawn, a page with no table, and the see-also lines naming it are drawn (§8). It carries a note at its head.
- **Amends**: [ADR-416](0416-a-topics-report-lists-the-topics-with-no-document-and-the-seeds-feeding-none-and-cluster-sizes-gain-a-row-a-subtopic.md) §1 to §4: `topics.html` is titled *Topics*, has a shape for listed topics, and its JSON is of format 2 (§10). It carries a note at its head.
- **Keeps**: [ADR-004](0004-relevance-defined-by-an-exemplar-seed-set.md) and [ADR-020](0020-relevance-scoring-function.md): the seeds alone define and score relevance, under the floor the operator set, `relevance-labels.yaml` and its key ([ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md)) unchanged (§6). [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md): every survivor placed (§5). [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md): one copy (§5). [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md): a crowded topic divided as before (§7). [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §5.1: a key's value is one string, a list written within it (§1). ADR-409 §11: no table changes shape, and no module's schema version moves (§4). [ADR-417](0417-an-ending-panel-beneath-the-output-says-how-the-last-invocation-of-the-working-directory-ended-and-why.md): the ending panel says why the last invocation ended, a refusal's line among the reasons (§3).
- **Settles**: [#898](https://github.com/algernon28/vespera/issues/898).

## Context

Since ADR-396 the top level of the arrangement is a set of topics inferred from the seed set's passages (ADR-399), dropped under a breadth floor (ADR-401), each survivor placed under the one whose centroid its chunks are nearest on average (ADR-400), named by a local model at the head of 6a (ADR-407), and stored keyed by its first passage (ADR-409). The operator cannot set them. Some operators already hold the top level they want, a list of subjects for the knowledge base, and want to give it instead.

`profile.yaml` holds one object a key, its value one string (ADR-061), a list being written within it, as `codeSetFolders` writes its folders (ADR-334 §5.1). The sidebar has nine sections (ADR-413 §1). The embedding-scoring run is the one 5d to 5f write under: the topics, the placement, the scores, the relevance verdicts and the clusters (ADR-394, ADR-409 §5). A topic is written in a row as two columns, `topic_seed_path` and `topic_chunk_index` (ADR-409 §1), with no `CHECK` on either.

**Not measured**: placement against a name's embedding, against the centroid of seed passages, has not been measured on any text; nor what share of the documents of a corpus lies far from every name of a list. The decision rests on the operator's choice, not on a measurement.

## Decision

### 1. The `topics` key

**`profile.yaml` gains `topics`**, after `foldersNotWalked` in `Profile`'s components and `ProfileKey`'s order, and before the six Confluence keys, of a new kind, *topics*: one string under `value`, as every key's (ADR-061), holding one name a line. A line ends at a line feed, a carriage return followed by a line feed, or a carriage return alone.

- **Unset, or blank once the blanks around it are removed, the key reads unset**, and the topics are inferred as ADR-396 to ADR-409 have it. Nothing of this record applies, and no run's identity changes (§11).
- **Otherwise the value, its blanks at its start and end removed, is split into lines**, each line one entry, in order. An entry's **name** is the line with the blanks around it removed; nothing else of it is changed.
- **One name is a list.** It gives one topic, every survivor placed under it, and its subtopics where it is crowded (ADR-406).
- **A name is no more than a name**: no description, no heading level, no nesting. A line holding `#` or `-` holds those characters in its name.

The reading has three states, as ADR-120 has every value's: **unset**; **refused**, carrying §3's line; **answered**, carrying the names in order. It is computed from the text in one place, `TopicsValue.reading()`.

### 2. One topic, one name made plain

**A name made plain** is the name with the blanks around it removed, decomposed (Unicode NFD), its combining marks removed, lower-cased under `Locale.ROOT`, and composed again (NFC). `  Qualità dell'Acqua ` is made plain as `qualita dell'acqua`. Blanks within a name are kept: `Conti correnti` and `Conti  correnti` are two names.

- **Two entries are the same topic where their names made plain are equal.** The second of them is a **duplicate**.
- **An entry blank once its blanks are removed** is a **blank entry**.
- **A topic's identity is its name made plain**, and nothing else: not its place in the list, which a reordering changes (§7), and not its name as written, which a change of case or accent changes while the topic stands.

### 3. A list holding a blank or a duplicate refuses `vespera run` at its start

**`vespera run` refuses a `topics` value holding a blank entry or a duplicate**, in one line on standard error, exit 1, after ADR-384's check of `modelsRunOn` and before the corpus root is locked: nothing is walked, no run is minted, no sidecar is started and `effective-values.properties` is not written. The first entry in the list's order that is blank or a duplicate is named, `<n>` and `<m>` counting the value's lines from 1 after its blanks at its start and end are removed:

> vespera run refused the topics in profile.yaml: line `<n>` is blank, and each line names one topic. Remove it.

> vespera run refused the topics in profile.yaml: line `<n>`, "`<entry>`", is the topic "`<earlier>`" of line `<m>` again, two names being one topic where they differ only in case, accents or the spaces around them. Remove one of them.

`<entry>` is the line as written, its blanks kept, and `<earlier>` the earlier entry's name. **The line is the invocation's closing line**, which the ending panel shows as it shows every refusal (ADR-417); the control panel draws the profile's row in the error colour (§9). `vespera label` and `vespera publish` read no topic and are not refused.

### 4. A listed topic, its key and its rows

**Where the key is answered, no topic is inferred**: 5d runs neither `TopicInference` nor `BreadthFloor`, and **every listed topic stands**, whatever lands under it. The breadth floor, the topics' neighbours and their resolution are read by nothing (ADR-399 §3, ADR-401 §2); no seed is listed as feeding no topic, and `seed_feeding_no_topic` has no row under the run.

**A listed topic's key is `TopicKey.listed(identity)`**: a `TopicKey` whose `SeedPassage` holds the name made plain as its path and `-1` as its chunk index, the constant `TopicKey.LISTED_CHUNK_INDEX`. In a row, `topic_seed_path` (`seed_path` in `topic`'s own row) holds the name made plain and `topic_chunk_index` (`chunk_index`) holds `-1`. A chunk index is never negative, so no key of a listed topic is a passage's; and under one run every topic is listed or none is. **Keys compare as ADR-399 §4 compares them**, the names made plain by code point, so a tie of ADR-400 §1 goes to the name made plain that comes first, never to the list's order.

| Table | Under a run with a list |
| --- | --- |
| `topic` | one row a listed topic, `passage_count` 0 |
| `topic_passage`, `seed_feeding_no_topic` | no row |
| **`listed_topic`**, new, `embedding`'s, the embedding-scoring run | **`run_id`, `topic_seed_path`, `topic_chunk_index`**, `name`, `place`: a row a listed topic, its name as §1 reads it, and its place in the list counted from 1, a `CHECK (place >= 1)` and a `UNIQUE (run_id, place)` |
| `winning_topic`, `see_also`, `cluster_member` | as ADR-409 has them, naming a listed topic by its key |
| **`winning_topic_score`**, new, `embedding`'s, the embedding-scoring run | **`occurrence_id`, `run_id`**, `score`: a scored document's score at its winning topic (§5), a `REAL`, beside its `winning_topic` row under the same key; its `occurrence_id` references `file_occurrence`, indexed as the primary key's first column (ADR-173) |

**`listed_topic` and `winning_topic_score` are new and no table changes shape**, so no module's schema version moves (ADR-409 §11): the tables keyed by a run gain two, ADR-409's thirty-eight becoming forty, and one more for each table another record adds. `listed_topic`'s rows are written by 5d with `topic`'s, before the first survivor is scored; `winning_topic_score`'s with each document's `winning_topic` row; both are discarded with the step's other rows (ADR-409 §5), and neither has a row under a run with no list. `listed_topic` is read by its primary key, a run's rows in `place` order being put in order on the heap, at most as many rows as the list has names; a topic's scores are read by `winning_topic_by_run_topic_and_occurrence`, each document's score by `winning_topic_score`'s primary key: no statement added sorts.

**The score is a table of its own and not a column of `winning_topic`**: a column added to a table that exists is a change of shape, which moves `embedding`'s schema version and refuses a database written before it, `vector` and `relevance_label` among what the operator would recreate (ADR-409's Alternatives refused).

### 5. Each name is embedded alone, and a survivor goes to the name its chunks are nearest on average

**At the head of 5d, after the resident seed vectors are read and before the first survivor is scored, each name is embedded alone**, its name as §1 reads it, sent as the embedding step sends a chunk, with no instruction, prefix or template, by the embedding model and artefact the scoring run was minted under (ADR-228), one call a name in the list's order. The vectors are held for the step, a name's vector each, and stored nowhere. A call the embedder does not answer fails the step, and nothing it wrote is kept (ADR-116): the next invocation does the step's work again. The step says, in place of the two lines on the topics it found and kept:

> Stage 5d (relevance scoring) took the `<t>` topic(s) the profile lists, each placed by its name

**Placement is ADR-400's, each listed topic's centroid being its name's vector.** A survivor's score at a listed topic is the mean, over its chunks, of each chunk's cosine to the name's vector; its winning topic is the topic of the highest score, a tie going to the key that comes first (§4); each chunk is nearest the topic of its highest cosine; and it fits each other topic at least `vespera.topics.see-also-share` of its chunks are nearest, 0.15 as shipped. ADR-400 §1's centroid of one vector is that vector, its unit vector's length 1, so the rule is ADR-400's unchanged.

- **The scoring pass takes, for each survivor chunk, its cosine to each name's vector**, beside its cosines to the seed passages, and hands each to the survivor's tally, which reads cosines alone, so that no survivor vector reaches it (ADR-400 §6). The cosines to the seed passages are taken for relevance alone, and place nothing.
- **Every scored document is placed**, as ADR-400 §5 has it: a document far from every name goes under the one it is nearest. How many were placed so is counted on `topics.html` (§10, §14).
- **Each scored document's score at its winning topic is stored** in `winning_topic_score`, the mean over its chunks of each chunk's cosine to the winning name's vector, the number the placement compared, taken as a `double` and written as a `REAL`. The scores at the other topics are not stored. It is a score that places, on the embedder's own scale, and no relevance score: no relevance floor, label or verdict reads it (§6).
- **The cost**: one embedding call a name, once per run; one cosine a survivor chunk and a name, a sum a chunk and a name held for one document at a time (ADR-085).

### 6. Relevance is untouched

**The seeds alone score relevance**, as ADR-020 has it: a survivor's score, its winning seed, the relevance floor and its verdicts, `relevance-labels.yaml` and its key, `closestSeed` and the seed-corpus comparison are what they are without a list. A seed folder is still required. The list changes placement, the top level and what lies under each topic, and no score, floor, label or verdict.

### 7. Names, the order and the subtopics

- **A listed topic's name is its name as §1 reads it**, on every page, in every line and in every rendering, in whatever `deliverableLanguage` the deliverable is written: never asked of the naming model, never translated, never falling back. **6a records it in `topic_name`** under the arrangement run with `origin` `LISTED`, a fourth value of `NameOrigin`, and `turn_down` `NULL`, so that every reader of a topic's name reads it there as before. It enters no comparison of ADR-407 §8, two listed names being distinct by §3.
- **The top level is in the list's order.** `topic_order` is a listed topic's `place` (§4), so the order prefix of its folder, its `P` and its place on the 6a page are its place in the list, padded to the width of the count of listed topics, and a topic no document lands under keeps its place (§8). ADR-396 §4's order by survivors placed holds where the topics are inferred.
- **The subtopics of a crowded listed topic are found as ADR-406 has it**, and named by the naming model as ADR-407 and ADR-409 §6 have it, their fallbacks included.
- **A renamed topic is a new topic.** Its key is another, and nothing of an earlier run is read for it: its overview is asked under its new name in the new generation run (§11). No row of one run is read by another (ADR-409); a topic whose name, clusters and models are unchanged is asked again in the new run as its first was, and is written by the same rules.

### 8. A topic no document lands under

**A listed topic no document is placed under stands, and is drawn.** It has no `arranged_cluster` row, so no heading on the 6a page (ADR-416's refusal of one stands), and is listed among `topics.html`'s topics with no document (§10). In the HTML tree, the Confluence rendering and the website rendering it has its folder and its page at its place (§7):

- **its page** carries its name as the `<h1>`; where its overview would be, in italics, the sentence of `OverviewUnwritten.NO_DOCUMENT_PLACED`: *No overview was written for this topic: no document was placed under it.*, in Italian *Per questo argomento non è stata scritta alcuna panoramica: nessun documento è stato collocato sotto di esso.*; no table; and its see-also list where it has one;
- **the index and the Confluence root page** head it with its name linking to its page, with no table beneath;
- **a see-also line naming it is drawn** on its page, as on any topic's (ADR-415 §5): it has a page for the line to stand on.

No overview is asked for it: it has no cluster (ADR-402 §6). `documents.csv` holds no row of it.

### 9. The control panel: a Topics section, and the row marked

**The sidebar is ten sections, in this order**: `profile`, `preflight`, `folders`, `services`, `seed-folder`, **`topics`**, `labels`, `code-sets`, `command-options`, `reports`. Folders is still the section open as the window opens. It stands after Seed folder because the topics, listed or not, are the seed set's partner in what the deliverable's top level is; it carries no badge.

**`topics`, titled Topics, is built as the Code sets section is (ADR-377 §8)**, and holds, top to bottom:

| Id | What |
| --- | --- |
| `topic-title` | `topics`' title in the style of a key's title, its tooltip the key's tooltip and the provenance the file states |
| `topic-list` | a row a name, in the list's order: the name as `SelectableText.line`, and **Remove** |
| `topic-name` | the entry box, prompting *A topic's name* |
| `topic-add` | **Add** |
| `topic-warning` | one line, beneath the box |
| `topics-save` | **Save** |
| `topic-outcome` | one line, beneath Save |

- **Add is disabled while the box is empty or blank, or holds a name the same topic as a listed one** (§2), and `topic-warning` says why: blank, *A topic needs a name.*; the same topic as a listed name, *`<listed>` is already a topic*, `<listed>` the listed name as shown, as *Pagamenti is already a topic*. An empty box says nothing. Otherwise Add is enabled and the warning empty; Add appends the name, its blanks around removed, and empties the box.
- **Save writes `topics` by ADR-377 §2, and nothing else**: the listed names joined by a line feed, the provenance as ADR-343 fills it in, refused where the file changed the value since the section was loaded (ADR-330 §5), its outcome in `ProfileEdit`'s words. No name listed writes the key empty, so that the topics are inferred again.
- **A value the jar would refuse** (§3) is drawn as stated, each blank entry and each duplicate a row of its own carrying the style class `refused`, drawn in the error colour (`-vespera-bad`), with §3's reason as its tooltip. Removing it and saving writes the list without it.
- **The Profile window's row of `topics`**, `profile-row-topics`, of the kind *topics*, links to the Topics section as `codeSetFolders`' row links to Code sets, and carries the style class `refused` while the file's value reads refused. Its texts in `profile-texts.json`, its hint, example and cost line, are written with its row by ADR-377's rules; its cost names the embedding-scoring run.
- **The window holds the rule of §2 in a copy of its own**, `TopicEntries`, since it links no class of the jar (ADR-285 §3); a test holds the two copies to the same answers.

### 10. Pages and lines that name a listed topic

**`topics.html` is titled *Topics*, whether or not the topics are listed**, its tab and link with it, since *Topics found in your seed documents* is untrue of a list. Where the topics are listed it has this shape, in English whatever the deliverable language (ADR-321 §4):

- the first paragraph, whole: *"The topics are the ones your profile lists, in its order, under the names you gave them. Every document that survived was placed under the one whose name its passages are nearest to on average. Each topic's row gives its name, how many documents were placed under it, how many documents placed under another topic are linked from it, and how many of the documents placed under it were placed with low similarity to every name."*; then one row a listed topic, in the list's order, its columns *Topic*, *Documents placed*, *Linked documents* and *Placed with low similarity*;
- *Topics no document was placed under*, then, where there are some, *"A topic your profile lists that no document was placed under is listed here. It still has its page in what is written, with no group on it."* and a table of one column, *Topic*, in the list's order; where there are none, ADR-416 §3's sentence;
- no section on seed documents feeding no topic, and no column of seed passages, their share, the seed documents or the first passage;
- *How to read this*, two paragraphs, the first two words of each in `<em>`: *"Linked documents are documents placed under another topic that also fit this one: each is copied once, under its own topic, and this topic's page carries a link to it rather than a second copy."* and *"Placed with low similarity counts the documents placed under the topic although they are far from every name: each document goes under the name it is nearest to, however far that is, so a large count says the list has no topic for them."*

**`topics.json` is of format 2** (ADR-362 §9), whose schema admits ADR-416 §3's shape and this one; the window reads `topics` at 2. In this shape its headline is `topics`, *Topics*, `topics-with-no-document`, *Topics with no document*, and `placed-with-low-similarity`, *Placed with low similarity*; its sections `topics`, whose columns are `topic` *Topic* `text`, `documents-placed`, `linked-documents` and `placed-with-low-similarity` *Placed with low similarity* `count`, `topics-with-no-document`, its one column `topic`, and `how-to-read`. A document counts as placed with low similarity where its stored score is under the low-similarity floor (§14). **The column *Placed with low similarity*, its headline figure and the last sentence of the first paragraph's list are written once the floor ships a value**: until then the table has its other three columns, the headline its other two figures, the first paragraph ends *"…how many documents placed under another topic are linked from it."* and the second paragraph of *How to read this* is not written; the schema admits both.

**Wherever a line or a page writes a topic as `<path> chunk <i>`, a listed topic is written as its name as listed, between quotation marks**: ADR-407 §9's and ADR-409 §6's lines, a subtopic's reading *subtopic `<n>` of topic "`<name>`"*, and `cluster-sizes`' *Topic* column, which holds the name without quotation marks. `cluster-sizes.html`'s first paragraph then ends *"Each topic's row names it as your profile lists it."* in place of its last sentence. No line is logged for a listed topic's name at 6a: it is not asked for.

**The 6a page and `arrangement.json`** say, where the topics are listed, as the first paragraph, whole: *"Every document that survived is here, placed under one topic and in the group it formed with the documents nearest it. Nothing has been removed, merged or renamed to produce this page. The topics are the ones your profile lists, in its order, under the names you gave them; the names of the subtopics a crowded topic is divided into were written by the naming model from the documents' own words, except where a paragraph under a name says otherwise. They are shown here to read: they are not part of what you approve, and they cannot be changed here. Every group's name below was taken from a document you can open."*; and as the first paragraph under *How to read this*: *"Topics are in the order your profile lists them, and a crowded topic's subtopics are in order of how much of the archive sits under them, largest first. Within each topic, or within each of its subtopics where it is divided, groups holding several documents come before groups holding one, and within those two tiers the groups whose documents scored highest for relevance come first."* `arrangement.json` stays of format 2.

### 11. The list enters the embedding-scoring run's identity, and which runs move when it is edited

**The embedding-scoring run's configuration gains `topics`**, appended after `subtopicFloor`: the names as §1 reads them, in the list's order, a JSON array of strings, `@JsonInclude(NON_NULL)` and `null` where the key reads unset. `StageRuns` reads it from the profile it mints the scoring run over, in the same load as the relevance floor, keeps it and hands it to 5d. **With no list nothing is written**, so every run's configuration text is what `RunIdentityGoldenTest` holds. A refused value never reaches a mint (§3).

**Editing the list mints a new embedding-scoring run**, and with it the runs downstream of it: adding, removing, renaming, respelling or reordering a name, and setting or clearing the key. The placement, written under the scoring run (ADR-409 §5), is why the scoring run moves and not only the arrangement. So:

- **Embedding scoring moves**: 5d writes the scores, the relevance verdicts and the placement under the new run, the scores and the verdicts the same as before (§6), from the stored vectors, the names alone sent to the embedder; 5e and 5f, under the same run, write the floor's verdicts and the clusters again. Its relevance labelling sample is drawn again: labels already saved are kept, being keyed by the document's path and the seed set (ADR-097), and labels in `relevance-labels.yaml` not yet saved are lost, which the row's cost line and warnings say (ADR-377 §4).
- **The arrangement moves** over the new scoring run, and is approved again unless `arrangementApproved` holds `auto`.
- **Generation moves**, and writes every cluster and every overview again under its new run.
- **The walk, byte-level reduction, extraction, content census, content redundancy and seed measurement do not move**: none reads the list.

A reordering alone moves the scoring run too, though it places no document otherwise (§4's ties are by the name made plain): the list enters one run's identity whole (Alternatives refused).

### 12. The modules, and run ids of the change that builds it

**The code that builds this record touches `profile` (`TopicsValue`, `Profile`), `embedding` (§4, §5), `synthesis` (`NameOrigin.LISTED`, `OverviewUnwritten.NO_DOCUMENT_PLACED`), `deliverable` and `publish`'s two drawings (§8), `pipeline` (§3, §5, §10, §11) and `ui` (§9)**, and no class of `corpus`, `extraction`, `similarity` or `ledger`. By `StageModules` (ADR-058, ADR-411): **seed measurement, embedding scoring, the arrangement and generation move**, the arrangement approved again unless `arrangementApproved` holds `auto`; **the walk, byte-level reduction, extraction, content census and content redundancy do not move**. `profile`, `deliverable`, `publish` and `ui` move no run by themselves (ADR-274 §5).

### 13. The vocabulary

**`CONTEXT.md`'s Topic entry** says a topic is listed by the operator in the profile, or, where none is listed, inferred from the seed set's passages; that a listed topic stands whatever lands under it, its identity its name made plain and its name as written; and that one no survivor is placed under has a page where listed, and none where inferred. **Winning topic** says a survivor's winning topic is the one its chunks are nearest on average, a listed topic being near by its name. **Topic name** says a listed topic's name is the operator's, never the model's. **Seed set** says the topics are inferred from its passages where the profile lists none. **Arrangement**'s `_Avoid_` line keeps rejecting *taxonomy* for a list of topics, the term being *topic* whether listed or inferred. No term is added: a listed topic is a topic.

### 14. The low-similarity floor

**A document is placed with low similarity where its score at its winning topic (§5) is under `vespera.topics.low-similarity-floor`**, a cosine on the scale of the shipped embedder, `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M` (ADR-353). It is a preset under ADR-330 §8, beside the topics' four, with no profile key; a value no run can be made under stops the invocation in one line, as ADR-330 §9 refuses every preset's: `vespera.topics.low-similarity-floor is <value>: a number from -1 to 1`.

- **It is read where `topics.html` is written**, at 6a, on both of its branches (ADR-416 §1), against the stored scores of the topic's placed survivors, those 5f grouped, as *Documents placed* counts them; no score is taken again.
- **It enters the embedding-scoring run's identity** as `lowSimilarityFloor`, a number appended after `topics`, `@JsonInclude(NON_NULL)` and written only where the value resolved is not the one that ships, as ADR-330 §9 writes every preset's, beside the topics' four presets; Command options shows it read-only in that run's list after them (ADR-428).
- **It is measured for the shipped embedder, on public text alone**, no operator's document read (ADR-264): Reuters-21578 articles of one category, as ADR-399 to ADR-401 drew them, one article a passage; lists of names written for some of its categories, one in English and one in Italian, each name sent with no instruction; documents of a listed category and documents of a category no list holds, of one chunk and of several, as ADR-400's corpus has them; each document's score as §5 takes it. **The floor shipped is the value that separates the documents of no listed category from those of a listed one at the highest balanced accuracy over both lists**, measured with the share of each kind it counts at that value and at its neighbours, and under a second embedder to show how far the value is the shipped embedder's alone.
- **Its value is stated, with that measurement, by the record that makes it**, and the preset ships with that record; nothing here gives it a value. The score is stored from this record on, so the floor's arrival moves no run but by its member, where a machine names another value.

## What the implementation owes

- **In `profile`**: `TopicsValue(String value, String provenance, Measurement measurement)`, a `ProfileValue`, with `reading()` giving `TopicsValue.Unset`, `TopicsValue.Refused(String line)` (§3's line whole) or `TopicsValue.Answered(List<String> names)`, and `static String identityOf(String name)` (§2); `Profile.topics` after `foldersNotWalked`.
- **In `embedding`**: `TopicKey.LISTED_CHUNK_INDEX`, `TopicKey.listed(String identity)` and `TopicKey.isListed()`; `ListedTopicPlacement.of(List<TopicKey> topics, double seeAlsoShare)`, refusing an empty list, with `forSurvivor(int chunks)` giving a tally that takes `add(int survivorChunk, TopicKey topic, double cosine)` and gives `placement()` and `winningScore()`, the winning topic's score (§5); `RelevanceScorer` taking each survivor chunk's cosine to each name's vector; `listed_topic` and `winning_topic_score` in `schema.sql`, their owner's line above each, their writes and reads.
- **In `synthesis`**: `NameOrigin.LISTED`; `OverviewUnwritten.NO_DOCUMENT_PLACED`.
- **In `deliverable` and `publish`**: §8's page, index heading and Confluence page, `TopicOverviewWording`'s two sentences.
- **In `pipeline`**: §3's check, `ListedTopicsAtTheStart`, beside `ModelsAtTheStart` in `VesperaCommand.Run`, and on `PipelineHoldsOnlyTheRulesOnRecordTest`'s record as holding no rule; the member of §11 in `StageRuns`; 5d's embedding of the names and its line (§5); 6a's `topic_name` rows and `topic_order` from `listed_topic` (§7); `TopicsAnalysisReport` at format 2 with §10's shape and title; `ClusterSizeReport`'s and the lines' naming of a listed topic (§10); the 6a page's two paragraphs.
- **In `ui`**: `ProfileKey.TOPICS` of `ProfileKey.Kind.TOPICS`, entering the embedding-scoring run; the Topics section (§9), `TopicEntries`; the `refused` style; `Reports.TITLES`' *Topics*; `AnalysisReportFile.FORMATS` reading `topics` at 2; `docs/report/topics.schema.json` at format 2.
- **Documents, written with the implementation, since the claims check reads them against the code**: `README.md`'s table of values gains `topics`' row, and its section on the control panel names the Topics section; `AGENTS.md`'s count of the tables keyed by a run gains this record's two; `docs/architecture.md`'s stage 5 and 6a say a listed topic is placed by its name.
- **Not owed before the floor's value is recorded** (§14): the preset `vespera.topics.low-similarity-floor`, its refusal, its member `lowSimilarityFloor`, its row in Command options, and `topics.html`'s column and headline figure. The score is stored from this record on.
- **The tests below that the change cannot yet compile against are this record's**; beside them, the implementation adds the invocation test of §5 over a scripted embedder whose vector follows the text sent (5d places a survivor under the listed name it is nearest, `listed_topic` holding each name and place and `winning_topic` the name made plain and `-1`), and the drawing test of §8, a listed topic no document lands under drawn at its place with its sentence, in both languages.

## Alternatives refused

- **A YAML sequence under `value`.** It reads naturally by hand, but makes one key's value no string, against ADR-061 and every reader of `keysAsWritten`; and the window's writer writes one scalar a value (ADR-295 §3a). One name a line keeps the file's shape and holds any name but one with a line break.
- **A separator such as `;` or `,`.** A name may hold either; a line break is the one character no name holds.
- **The list's order in the arrangement's identity and the names alone in the scoring run's**, so that a reordering moves only the arrangement. It saves a pass of 5d to 5f on a reordering, at the cost of one list split across two runs' identities that must agree, and of a place no row of the scoring run would hold for a topic no document lands under.
- **Keying a listed topic by its place.** A reordering would make every topic another, against the operator's identity by name.
- **A table of their own for listed topics' placement and clusters.** Every reader of a topic would read two tables; the two columns of ADR-409 §1 hold a name made plain as well as a path, and `-1` tells the two apart.
- **The score as a column of `winning_topic`, or of `relevance_score`.** Either changes a table's shape, moving `embedding`'s schema version and refusing every database written before, its vectors and labels with it (§4).
- **Storing whether a document is under the floor, and not its score.** A floor named again would need the corpus scored again to be counted; the score is one number a document and serves any floor.
- **Storing the score where no topic is listed.** No count reads it there, and an inferred topic's score is on a centroid's scale, which no floor of §14 is measured on.
- **Storing the names' vectors.** A name is one call; a vector cached apart from its run would need a key of its own for a text that is no chunk.
- **Inferring the topics and naming them from the list.** The operator chose the names as the topics themselves, each placing by its own embedding.

## Consequences

- **An operator can give the deliverable's top level** as a list of names, in the order they want, and take the inferred topics back by clearing it.
- **A name the documents do not match still stands**, with a page and no group, and is listed on `topics.html`; a document no name matches still lands under its nearest, and is counted.
- **A topic's name is only as good as its embedding**: a short or ambiguous name places by what the embedder makes of it, which nothing measured (Context).
- **A listed run stores one number more a scored document**, in `winning_topic_score`.
- **Any edit of the list costs a pass of 5d to 5f from the stored vectors, an approval unless `auto`, a new sample to label and a new generation**, a reordering included; no extraction, chunk or seed vector is asked for again.
- **ADR-229, ADR-344, ADR-396, ADR-399, ADR-400, ADR-401, ADR-402, ADR-407, ADR-409, ADR-413, ADR-415, ADR-416 and ADR-428 carry a note *"Partly amended — see ADR-435"*** at their heads.

## What this does not decide

- **The low-similarity floor's value**, which §14's measurement gives and the record making it states; until then the preset does not ship and the column is not written (§10).
- **How well a name places**, against the inferred topics, on any text: not measured.
- **The hint, example and cost line of `topics`' row**, ADR-377's to word.

## Tests

| Class | What it holds |
| --- | --- |
| `profile.TheTopicsKeyTest.anUnsetOrBlankValueReadsUnset` | a value of `null`, blanks, and line breaks alone each read unset (§1) |
| `…eachLineIsANameInOrderItsBlanksRemoved` | three lines, one with blanks around it and a line break at the end, read as the three names in order, blanks around removed (§1) |
| `…oneNameIsAList` | one line reads as one name (§1) |
| `…aNameIsMadePlainByCaseAccentsAndTheBlanksAroundIt` | `  Qualità dell'Acqua ` is made plain as `qualita dell'acqua`, and two names differing by inner blanks are two (§2) |
| `…aBlankEntryIsRefusedNamingItsLine` | a blank second line is refused in §3's line, whole (§2, §3) |
| `…aDuplicateIsRefusedNamingBothLines` | `Qualità` and ` qualita ` refused in §3's line, whole, naming line 2 and line 1 (§2, §3) |
| `…theKeyStandsAfterTheFoldersNotWalked` | `Profile`'s component `topics` follows `foldersNotWalked` and precedes `confluenceBaseUrl` (§1) |
| `embedding.ListedTopicPlacementTest.aListedTopicsKeyIsItsIdentityAndNoChunk` | `TopicKey.listed("pagamenti")` holds `pagamenti` and `-1`, is listed, and a passage's key is not (§4) |
| `…placesASurvivorUnderTheNameItsChunksAreNearestOnAverage` | three chunks nearer one name and one chunk on the other: placed under the first, linked from the second at one chunk in four (§5) |
| `…aSurvivorFarFromEveryNameGoesToItsNearest` | cosines of 0.01 and 0.02: placed under the second, linked from none (§5) |
| `…aTieGoesToTheNameMadePlainThatComesFirst` | two names handed over last first, each at a cosine of 0: placed under the one that comes first by code point (§4, §5) |
| `…linksFromANameOneChunkInSixIsNearestAndNotOneInSeven` | at 0.15, one chunk of six links, one of seven does not (§5) |
| `…refusesToPlaceUnderNoName` | an empty list is refused (§5) |
| `…theWinningScoreIsTheMeanCosineToTheWinningName` | the survivor of the first case scores 0.5, the mean of its four chunks' cosines to the winning name, the score `winning_topic_score` stores (§5) |
| `pipeline.TheListedTopicsEnterTheScoringRunInvocationTest.theListInItsOrderIsTheLastMember` | a profile listing *Pagamenti* then *Incassi*: the scoring run's configuration ends with `"topics":["Pagamenti","Incassi"]` (§11) |
| `…aReorderingIsAnotherScoringRun` | the same names in the other order: a second scoring run, the first kept (§11) |
| `pipeline.AListOfTopicsWithADuplicateRefusesTheRunInvocationTest.aDuplicateIsRefusedBeforeAnythingIsWalked` | the invocation exits 1, says §3's line alone on standard error, and nothing is walked (§3) |
| `…aBlankEntryIsRefusedBeforeAnythingIsWalked` | the same for a blank line (§3) |
| `deliverable.TopicOverviewWordingTest.theCodeSaysWhatTheRecordSays` | amended: seven cases, `NO_DOCUMENT_PLACED`'s sentence in English and in Italian, whole (§8) |
| `ui.TheTopicsSectionTest.theSectionListsTheNamesInOrder` | the section titled Topics, its rows the two names the file states, in order, each with Remove (§9) |
| `…aBlankOrDuplicateNameIsRefusedAndAddStaysDisabled` | blanks: Add disabled, *A topic needs a name.*; ` pagamenti `: Add disabled, *Pagamenti is already a topic*; *Fornitori*: Add enabled, no warning, and added at the end (§9) |
| `…theSaveWritesTheNamesOneALine` | *Incassi* removed and *Fornitori* added: `topics` saved as `Pagamenti` and `Fornitori` on two lines, said in the outcome (§9) |
| `…aDuplicateTheFileHoldsIsDrawnInTheErrorColour` | a file listing *Pagamenti* and *pagamenti*: two rows, the second alone `refused` (§9) |
| `ui.TheWindowAndTheJarMakeANamePlainAlikeTest.eachNameIsMadePlainAlike` | `TopicEntries.identityOf` and `TopicsValue.identityOf` agree on every name of a table of cases, accents, blanks and scripts (§2, §9) |
| `TheListedTopicTablesTest.theTwoTablesHaveTheirColumnsAndAreTheEmbeddingModules` | `listed_topic`'s and `winning_topic_score`'s columns, types and keys, in order, each `embedding`'s (§4) |
| `…aPlaceIsCountedFromOneAndHeldOnceARun` | a place of 0, and a second topic at a place taken under the run, refused; place 2 kept (§4) |
| `EveryTableKeyedByARunIsOnRecordTest.everyShippedDeleteIsOfOneRunsRows` | amended: `listed_topic` and `winning_topic_score` on record, `embedding`'s (§4) |
| `ui.TheProfileWindowTest.theProfileIsTheSidebarsFirstSectionAndOpensOneWindowOfItsOwn` | amended: ten sections, `topics` after `seed-folder` (§9) |
| `ui.ProfileKeyTest.aChangeCostsItsRunAndEveryLaterOne` | amended: `topics` costs embedding scoring and every later run, closes the approval and draws the sample again, fifteen keys drawing it (§9, §11) |
| `TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest.aTopicMayBeListedInTheProfile` | the Topic entry no longer says a topic is never set by the operator, and says it may be listed in the profile (§13) |

`RunIdentityGoldenTest` holds, unchanged, that with no list no member is written.
