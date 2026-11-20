# ADR-434 — The profile's `outputs` choose what stage 6b draws, and a retrieval plugin draws records and vectors into a JSONL file

- **Date**: 2026-11-20
- **Status**: accepted on 2026-11-20.
- **Decided**: by the operator, on [#890](https://github.com/algernon28/vespera/issues/890), on 2026-11-20: that what stage 6b draws is chosen, each plugin's rendering and the HTML tree alike, the HTML tree being one choosable output beside Confluence, the website and a vector store, so that an operator wanting only retrieval records pays for no page, picture or copy (§1); that a plugin which refers to the tree requires it, checked at the start (§3); that the choice is a profile key, `outputs`, defaulting to the tree, Confluence and the website, shown as a sidebar section (§2, §11); that a record-shaped seam sits beside `Drawing`, page plugins keeping `Drawing` and a store plugin reading records (§4); that a record is a cluster's synthesis doc, a topic overview or a member's passage, each with its topic, subtopic, cluster, citation ordinals, deliverable language and generation run (§5); that its stable id is its content key, the cluster's or the topic's key and an ordinal (§6); that it is embedded by the local `embedding` model, its vector's identity following ADR-228, and the vectors go with the records (§7); that member text is allowed, the destination being the operator's (ADR-305), and a plugin can leave it out (§4, §10); that only the contract is settled now, the first plugin writing one JSONL file of records and vectors under the deliverable for any store to import, so that nothing is transmitted (§8); that network stores come later, their credentials in the environment or the operating system's keyring and never in the profile (§10); and that this amends ADR-333 §4, and ADR-214 only as far as the vectors are carried out of SQLite, stages 4 and 5 keeping SQLite. What each section adds beneath that is what building it needs, and Decided here lists each such choice.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §4, *"Stage 6b draws every compiled-in plugin on every drawing"*: it draws the plugins `outputs` names (§1, §2); its refused alternative *Plugins enabled by the profile*, whose reason, that a key would move the generation run, does not hold of a key of drawing (§13); §1 and §2, `Plugin` gaining `drawsFromTheTree` and `records` and a second seam lying beside `Drawing` (§4); and §5, the HTML tree being no plugin still, but drawn only where `outputs` names it (§1, §9). Its §6 to §12 stand, a third plugin, `retrieval`, being a module, a bean, a folder and a destination as its Consequences have it (§8). It carries a note at its head.
- **Amends**: [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md) §2, *"Drawn always"*: the Confluence rendering is drawn where `outputs` names `confluence`, which it does unset (§2). Its §1, §3 and §4 stand. It carries a note at its head.
- **Amends**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md) §1 as ADR-308 and ADR-333 left it, and §2: stage 6b draws the outputs the profile names, the HTML tree among them unless it is left out, so the deliverable may hold no HTML tree, and beside the tree or alone it may hold the retrieval plugin's records file (§1, §8, §9). Its rule that beyond drawing nothing in this project renders, uploads or transmits the deliverable, but a publish as ADR-308 and ADR-333 have it, stands: the records file is drawn, and the retrieval plugin's delivery sends nothing (§8). It carries a note at its head.
- **Amends**: [ADR-214](0214-chroma-is-removed-and-vectors-live-in-sqlite-alone.md) §2, *"The `vector` table ADR-085 created is where every vector is, and nothing is projected from it anywhere"*: a synthesis doc's and a topic overview's vectors are kept in a table of `embedding`'s beside it, `writing_vector`, and a copy of the vectors a drawing hands the retrieval plugin is drawn into its records file (§7, §8). Every vector is still kept in SQLite alone, stages 4 and 5 read SQLite alone, and no vector database is added. It carries a note at its head.
- **Amends**: [ADR-435](0435-the-profile-may-list-the-topics-by-name-a-survivor-going-to-the-name-its-chunks-are-nearest-and-none-listed-infers-them.md) §9, *"The sidebar is ten sections"*: it is eleven, `outputs` after `code-sets` (§11). It carries a note at its head.
- **Keeps**: [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md): every vector a drawing hands is under the one embedder identity the scoring run names (§7). [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md): a vector is content under an instrument and keys no run, so `writing_vector` has no run id (§7). [ADR-305](0305-the-confluence-rendering-is-drawn-in-storage-format-for-cloud-and-data-center-alike-and-loading-it-into-cloud-is-the-operators-act-outside-what-binds-vespera.md): where the operator carries the deliverable afterwards is theirs (§10). [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md): the profile holds no credential (§10). [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) and [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md): a document is read only by the local models, and the records file lies under the tree's marker, so the guard reads it as a tree (§8, §9). ADR-333 §11: a plugin whose drawing fails stops alone, a plugin reading retrieval records too (§4). [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md): a table's SQL is its owner's, so the reads §12 adds lie in `extraction` and `embedding`. ADR-223 §9 and [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md): a drawing holds one topic's rows at most (§12). [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): no dependency is added (§8). [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): **Output** and **Retrieval record** are terms of ours, and a cluster renders as a group (§14).
- **Settles**: [#890](https://github.com/algernon28/vespera/issues/890).

## Context

**What stage 6b draws.** `Deliverable.writeTo` draws the HTML tree and, beside it, each compiled-in plugin's rendering, calling each plugin's `Drawing` at the places it goes through the topics (ADR-333 §2, ADR-415 §11). Every plugin draws on every drawing, and nothing turns one on or off (ADR-333 §4, ADR-312 §2). The HTML tree is no plugin: it is the core a plugin refers to, its copies checked against stage 2's key, and the Confluence rendering carries those copies as files and the website rendering lists them (ADR-333 §5, ADR-313, ADR-333 §3).

**A second end use.** The operator wants the same arrangement and writing in a vector store, for retrieval-augmented generation, in place of a wiki. Such a store wants records: a piece of text, its vector and the metadata that places it. `Drawing` is shaped for pages: page breaks, see-also lines, entry copies and a cluster folder.

**What is stored already.** A survivor's text is cut into chunks, kept in `chunk_cache` by content hash, chunker identity, chunking-rule identity and ordinal, and stage 5 stores a vector for every chunk in `vector` under the same key and the whole embedder identity (ADR-084, ADR-085). The scoring run names the embedding model's artefact, its digest and weight dtype, and reads the vectors of that one identity (ADR-228). A cluster's synthesis doc and a topic's overview are kept under the generation run (`cluster_synthesis_doc`, `topic_overview`), and have no vector.

**What stands in the way.** ADR-214 keeps every vector in the `vector` table and projects nothing from it. ADR-301 says nothing in the project transmits the deliverable beyond drawing it. ADR-333 refused plugins enabled by the profile because a key would move the generation run.

## Decision

### 1. Outputs, and the HTML tree among them

**What stage 6b draws is a set of outputs: the HTML tree, named `tree`, and each plugin's rendering, named by the plugin's name.** The HTML tree is still no plugin: its copies and their checking stay with it, and no plugin opens the archive (ADR-333 §5). It is drawn where the outputs name `tree`, and not otherwise (§9).

A plugin says whether it draws from the HTML tree, `Plugin.drawsFromTheTree()`. The Confluence plugin does, its copies being the tree's (ADR-313), and so does the website plugin, which lists the tree's copies and whose publish copies them from it (ADR-333 §3, §7). The retrieval plugin does not (§8).

### 2. The profile's `outputs`

**`outputs` is a key of the profile, the last component of `Profile`, after `confluenceRootTitle`, a `TextValue`.**

- **Read so**: its value split at `,`; each piece stripped of the blanks around it and lower-cased, `Locale.ROOT`; an empty piece skipped; a name written twice kept once, at its first place. The order written decides nothing: the plugins are drawn in the order of their names (ADR-333 §2).
- **Unset, blank, or no name once read**: `tree`, `confluence` and `website`, what is drawn without this record. `retrieval` is drawn only where it is named.
- **No gate, and in no run's identity** (§13). No `Measurement` pointer: no measurement of the corpus informs what the operator wants drawn.
- **Stated on the tree's index** with every other key `keysTheIndexStates` names (ADR-359 §7).
- **Read by `vespera run` alone**: a publish delivers what was drawn, and reads no `outputs`.

`deliverable.Outputs` holds the reading: `Outputs.read(String)`, `names()` in the order read, `DEFAULT` (`tree`, `confluence`, `website`), `drawsTheTree()`, `draws(Plugin)` and `refusal(List<Plugin>)` (§3). `pipeline` reads the key from the profile and hands `Outputs` and the plugins it names to `Deliverable.writeTo`; `deliverable` still reads no `Profile` (ADR-110).

### 3. The start refuses outputs no drawing can draw

**`vespera run` stops at its start, exit 1, where `outputs` names a name that is no output, or a plugin that draws from the HTML tree without `tree`**: after the refusal of `modelsRunOn` and before the corpus-root lock, as ADR-384 §2's is, so nothing is walked, no run is minted and no container is started; it comes after ADR-435 §3's refusal of the listed topics. `vespera label` and `vespera publish` are not stopped by it. A profile that cannot be read refuses nothing here and is left to the job's own refusal, as `ModelsAtTheStart` leaves it.

`Outputs.refusal(plugins)` gives an `Outputs.Refusal(String reason, String advice)` for the first of these two that applies, empty where neither does; `pipeline.OutputsAtTheStart` writes the line `vespera run stopped at its start: <reason>, so nothing was walked and no run was minted -- <advice>`, the shape of ADR-384 §2's.

| Case | Reason | Advice |
| --- | --- | --- |
| A name neither `tree` nor a plugin's | `outputs names <names>, which <is no output\|are no outputs> Vespera draws: it knows <known>` | `name outputs among those in profile.yaml, or leave it unset for tree, confluence and website` |
| A plugin that draws from the tree, and no `tree` | `outputs names <names> but not tree, and <it draws\|they draw> from the HTML tree` | `add tree to outputs in profile.yaml, or take <names> out of it` |

`<names>` are the names at fault, in the order read; `<known>` is `tree` then every plugin's name in name order. A list of names is written `a`, `a and b`, `a, b and c`.

### 4. A second seam beside `Drawing`: retrieval records

**Page plugins keep `Drawing`. A plugin that reads retrieval records opens `RetrievalRecords`, a seam of `deliverable` beside it.**

```java
package io.algernon.vespera.engine.deliverable;

public interface Plugin {
    // ADR-333 §1's members stand; Delivery and DrawingContext unchanged.
    /** Whether this plugin draws from the HTML tree, so that outputs naming it must name tree: true unless it says otherwise. */
    default boolean drawsFromTheTree() { return true; }
    /** Opens this plugin's drawing of the tree; never called on a plugin that draws from no tree. */
    Drawing drawing(DrawingContext context) throws IOException;
    /** Opens this plugin's reading of the retrieval records, empty where it reads none. */
    default Optional<RetrievalRecords> records(DrawingContext context) throws IOException { return Optional.empty(); }
}

public interface RetrievalRecords extends AutoCloseable {
    /** Whether member passages are handed to it; where it says no, none is handed, and none is composed for it alone. */
    boolean takesMemberPassages();
    /** One retrieval record, in §5's order. */
    void take(RetrievalRecord record) throws IOException;
    /** Every topic is gone through. */
    void finish() throws IOException;
    @Override void close() throws IOException;
}
```

- **Who is called.** `Deliverable.writeTo` opens `drawing` for each plugin the outputs name that draws from the tree, and `records` for each plugin the outputs name; a plugin may answer both. A plugin that draws from no tree writes `drawing` as it likes; `Deliverable` never calls it.
- **One way through the topics.** Retrieval records are composed as `Deliverable.writeTo` goes through the topics for the tree, a topic at a time, and handed to every plugin reading them, in the order of their names, each record to each before the next is composed. Where the tree is not drawn it goes through the topics all the same, writing no page (§9).
- **Composed only where read.** Where no plugin the outputs name reads retrieval records, none is composed, no vector is read and no writing is embedded. Where none of them takes member passages, no member passage is composed.
- **A failure stops that plugin alone** (ADR-333 §11): an `IOException` or `RuntimeException` out of `records`, `take`, `finish` or `takesMemberPassages`, or out of composing a record (§7), stops every plugin it was being composed for, `drawing.txt` saying `failed`, the tree and every other plugin going on, the closing line naming each with the type it threw. `Deliverable` writes `drawing.txt` for a plugin reading retrieval records as it does for one drawing pages: `drawing` as it opens, `drawn` once `finish` and `close` return.
- **What composes them** is handed by `pipeline`, as `ArrangedSurvivors` is: `RecordInputs`, an interface of `deliverable`, holding the embedder identity of §7, the vector of a writing, `vectorOf(String)`, and a member's passages, `passagesOf(ListedSurvivor, PassageConsumer)`, each handed as its ordinal, its text and its vector in ordinal order, a member at a time.

### 5. A retrieval record

**A retrieval record is one of three kinds, each one piece of text, its vector and its place.**

```java
public record RetrievalRecord(
        String id, Kind kind, String text, float[] vector, String embedderIdentity, Placement placement) {

    public enum Kind { SYNTHESIS_DOC, TOPIC_OVERVIEW, MEMBER_PASSAGE } // written synthesis-doc, topic-overview, member-passage

    public record Placement(
            String generationRun, DeliverableLanguage language,
            String topic, int topicOrder,
            Optional<String> subtopic, OptionalInt subtopicOrder,
            Optional<String> cluster, OptionalInt clusterNumber,
            List<Integer> citations,
            Optional<String> document, OptionalInt passage) {}
}
```

| Kind | `text` | `cluster`, `clusterNumber` | `citations` | `document`, `passage` |
| --- | --- | --- | --- | --- |
| A cluster's synthesis doc, one for each written cluster | its prose as `cluster_synthesis_doc` keeps it, citation markers and all (ADR-109) | its cluster title, and its number in its topic's one sequence (ADR-415) | the citation ordinals the prose cites, each once, ascending | empty |
| A topic's overview, one for each topic whose overview is written | its writing as `topic_overview` keeps it | empty | the cluster numbers it cites, each once, ascending (ADR-402) | empty |
| A member's passage, one for each chunk of each member of each cluster | the chunk's text as `chunk_cache` keeps it | the cluster's title, or its label where it is unwritten, and its number | the member's citation ordinal in its cluster's call where the call sent it, else none | the member's path, as `documents.csv`'s `path` column holds it unquoted, and the chunk's ordinal |

- **`topic`, `topicOrder`**: the topic's name and its place at the top level from 1; **`subtopic`, `subtopicOrder`**: its subtopic's name and place from 1, empty where the topic has none (ADR-406). **`generationRun`** is the whole id of the generation run drawn, and **`language`** the deliverable language.
- **The order** is the tree's: the topics in their order; within one, its overview, then each cluster in its topic's one sequence, its synthesis doc then its members' passages in membership order, each member's in ordinal order.
- **No record** of an unwritten cluster's synthesis doc, an overview turned down, or a topic no survivor is placed under. An unwritten cluster's members' passages are records all the same: they are survivors.
- **A member's passages are its chunks under the chunker and chunking-rule identities the scoring run read**, each with its stored vector (§7). A member with no chunk has no passage.

### 6. The stable id

**A retrieval record's id is its content key: the cluster's or the topic's key, and for a passage the member and the passage's ordinal.** It is the lowercase hexadecimal SHA-256 of the UTF-8 bytes of the key's parts joined by U+001F:

| Kind | Parts, in order |
| --- | --- |
| `synthesis-doc` | `synthesis-doc`, the topic's seed path, its chunk index, the subtopic ordinal or `-1` where there is none, the cluster ordinal |
| `topic-overview` | `topic-overview`, the topic's seed path, its chunk index |
| `member-passage` | `member-passage`, the topic's seed path, its chunk index, the subtopic ordinal or `-1`, the cluster ordinal, the member's content hash, the passage's ordinal |

Numbers are written in decimal, as the ledger keeps them (ADR-409 §3). `deliverable.RetrievalRecordId` holds the rule: `ofSynthesisDoc(ClusterSlot)`, `ofTopicOverview(TopicKey)` and `ofMemberPassage(ClusterSlot, String contentHash, int passage)`. **The id names neither the generation run nor the deliverable language**: the same place drawn by another generation run of the same arrangement, or in the other language, has the same id. Hashed, it names no seed's path where a store shows ids.

### 7. The vectors, and the identity they are under

**Every vector a drawing hands is under the scoring run's embedder identity (ADR-228): the embedding model's name, the digest and weight dtype the scoring run names, the dimension of its stored vectors, and no instruction (ADR-084).** `RecordInputs.embedderIdentity()` is that identity's composed value, and every record carries it.

- **A member's passage** is handed its vector as `vector` stores it under that identity, read by `embedding`. A member with fewer stored vectors than chunks stops the composing, as ADR-231 §2 stops scoring on such a seed: an `IllegalStateException` naming the occurrence and the counts.
- **A writing**, a synthesis doc's prose or an overview's, is embedded by the same embedding model, whole, with no instruction and `truncate: false`, a writing refused as too long split and averaged as ADR-144 has it of a chunk. Its vector is kept in **`writing_vector`**, a table of `embedding`'s: `writing_digest`, the lowercase hexadecimal SHA-256 of the writing's UTF-8 bytes, `embedder_identity` and `embedding`, a little-endian float32 BLOB at full dimension, keyed by the first two and by no run, as ADR-085 keys `vector`. Additive, so no module's schema version moves. A writing whose vector is kept under the identity is not embedded again, so a redrawing whose writings are all kept asks nothing of Ollama.
- **The artefact is asked once a drawing**, before the first writing it embeds: where Ollama reports another digest or weight dtype for the embedding model than the scoring run names, nothing is embedded and the composing stops with an `IllegalStateException`, each plugin reading retrieval records stopped alone (§4). Where Ollama does not serve the model, the same.

### 8. The first plugin: `retrieval`, one JSONL file under the deliverable

**`retrieval` is a plugin of its own module, `io.algernon.vespera.publish.retrieval`, declaring `ledger`, `deliverable` and `synthesis`, as each plugin does (ADR-333 §1).** Its name is `retrieval`, its folder `retrieval/` at the tree's root, its `listName()` `records.jsonl`. It draws from no tree, opens no `Drawing`, and takes member passages. A topic's folder begins with its order's digits (ADR-415) and the code sets lie under `code/`, so no folder the tree draws is so named (ADR-312 §1).

```
<deliverable root>/
  vespera-deliverable.txt
  retrieval/
    records.jsonl   drawing.txt
```

- **`records.jsonl`** is UTF-8 with no byte order mark, one JSON object a line in §5's order, each line ended by `\n`, the last too, and no other line. It is written a record at a time through `records.jsonl.part` and moved into place by `DrawingContext.moves()` at `finish`, so a drawing that stops leaves the file an earlier drawing put there whole.
- **A line**, its fields in this order: `format`, `1`; `id`; `kind`, as §5 writes it; `text`; `vector`, an array of numbers; `embedder`, the identity's value; `generationRun`; `language`, `en` or `it`; `topic`; `topicOrder`; `subtopic`; `subtopicOrder`; `cluster`; `clusterNumber`; `citations`, an array of numbers, `[]` where none; `document`; `passage`. An empty value is `null`. A string escapes `"`, `\` and U+0000 to U+001F, the last as `\b`, `\t`, `\n`, `\f`, `\r` or `\u00XX`, and writes every other character as its UTF-8 bytes. A component of a vector is written as `Float.toString` writes it, which reads back to the same float32; one that is not finite stops the plugin.
- **The file is whole for its generation run.** A drawing writes every record of its generation run again; nothing in this project changes or removes a record a store imported. A store holding an earlier file replaces each record by its id, and removes those the new file does not hold, by its own doing.
- **Its delivery sends nothing.** `vespera publish --to retrieval` finds the rendering as every destination's is found (ADR-333 §6, §11, ADR-393), writes nothing and opens no connection, and ends delivered, exit 0, with the line `vespera publish sends nothing to retrieval: its records are at <file>, a line each with its vector, for a store of yours to import.` So nothing is transmitted, and ADR-301's rule holds.
- **No dependency.** The lines are written with the JSON library the jar ships.

### 9. A deliverable with no HTML tree

**Where `outputs` does not name `tree`, the drawing writes the marker (ADR-276 §3) and each named plugin's folder with its `drawing.txt`, and nothing of the tree**: no `index.html`, no topic or cluster folder, no copy, no `documents.csv`, no `code/` or `code.csv`, and no report of the cluster pages' sizes, there being no page to size (ADR-303). No survivor is read from the archive. The deliverable root is resolved and refused as it is today (ADR-276, ADR-333 §7).

- **What an earlier drawing left is kept**, as nothing beneath a deliverable root is deleted (ADR-103, ADR-276 §3): an earlier drawing's tree, or a rendering of a plugin now left out, stands, and a publish to that plugin finds it as §6 of ADR-333 finds a rendering.
- **The closing line** of the generation step ends `-- the deliverable is at <folder>, with no HTML tree drawn` where it ended `-- the deliverable tree is at <tree>`.

### 10. Member text, and the stores to come

- **Member text may go into a retrieval record**: the records file lies under the deliverable, beside copies of the same documents where the tree is drawn, and where the operator carries it afterwards is theirs (ADR-305). A plugin leaves it out by answering no to `takesMemberPassages()`, and then no passage, its text or its vector, reaches it.
- **A plugin that sends to a store** is a later record's. It reads its credential from the environment or the operating system's keyring, never from the profile (ADR-359), and its transport is encrypted only, its access control the operator's (ADR-333 §8).

### 11. The control panel

**The sidebar gains a section `outputs`, titled Outputs, after `code-sets`**, so that the sidebar is eleven sections: `profile`, `preflight`, `folders`, `services`, `seed-folder`, `topics`, `labels`, `code-sets`, **`outputs`**, `command-options`, `reports`.

- **Four checkboxes**, in this order: `output-tree`, *HTML pages*; `output-confluence`, *Confluence*; `output-website`, *Website*; `output-retrieval`, *Retrieval records*; each ticked where the key, read as §2 reads it, names it, so the first three where it is unset. Unticking `output-tree` unticks and disables `output-confluence` and `output-website`; ticking it enables them, ticking neither.
- **`outputs-save`**, *Save*, writes the names ticked into `profile.yaml`, in that order, `, ` between them, through `ProfileRow.save` as `seed-folder-save` writes `seedFolder`, with the provenance ADR-343 fills in; its line goes in `outputs-outcome`.
- **The profile window's row** of `outputs` links to the section, as the rows of `seedFolder` and `codeSetFolders` link to theirs (ADR-389).
- **The closed lists.** `ChildCommand.DESTINATIONS` gains `retrieval`, its list `records.jsonl`, so that a publish offers it where it is drawn (ADR-333 §6). A list of the control panel's own names the outputs, `tree` then the destinations, and those drawn from the tree, `confluence` and `website`; a test holds both to the jar's (ADR-285 §3).

### 12. The reads, what sorts and what is held

- **`extraction`** answers a content hash's chunks under a chunker and a chunking-rule identity, in ordinal order, by `chunk_cache`'s primary key; **`embedding`** answers their vectors under an embedder identity in ordinal order, by `vector`'s primary key, and reads and writes `writing_vector` by its own. None sorts, so `EveryStatementThatSortsIsRecordedTest`'s counts are unchanged.
- **What is held**: one topic's rows, as ADR-223 §9 has it; one member's passages and vectors at a time, bounded by one document's chunk count (ADR-085); and one retrieval record being handed. The plugin holds none: each line is written as it is taken.

### 13. Which run ids move

- **The key moves no run.** `outputs` enters no run's identity, as `clusterPageByteCeiling` enters none (ADR-303 §9): which outputs are drawn is drawing, and the generation run's rows are the same whatever is drawn. The next invocation that reaches the tree draws what the key then names, under the generation run it arrives at, the recorded generation step drawing again from stored rows (ADR-333 §12). No `config_consumed` changes.
- **The build moves seven stages.** `extraction` gains §12's read of a member's chunks, and `embedding` the read of their vectors, `writing_vector` and the embedding of a writing, so every stage whose version names either moves (ADR-058, `StageModules`): extraction, content census, content redundancy, seed measurement, embedding scoring, the arrangement and generation, the arrangement approved again. Byte-level reduction does not. `deliverable`, `profile`, `pipeline`, `ui`, `confluence`, `website` and `retrieval` name no stage's version.

### 14. What the documents say

- **`CONTEXT.md`, with this record**: **Output** and **Retrieval record** are added, and **Plugin** says a plugin is chosen by `outputs` and that a third, `retrieval`, reads retrieval records.
- **Each with the step that makes it true**: `CONTEXT.md`'s **Publication-ready artifact**, **Deliverable root**, **Destination** and **Website rendering** say what §1, §8 and §9 say; `AGENTS.md`'s modules, twelve, and what stage 6b draws; `README.md`'s row of `outputs` in its table of values, its section on the control panel and its line of `vespera publish --to`, `check-claims` reading each; `docs/architecture.md` §1, its module table and its diagram; and `docs/running-stage-by-stage.md`'s stage 6b.

### 15. The order of the build

**First §2, §3 and the seam of §4 with the tree made optional (§9), no plugin reading retrieval records; then §7's reads and `writing_vector`, the composing of §5 and §6, and the retrieval plugin of §8; then the control panel of §11.** The second step is the one that moves run ids (§13).

## Decided here

1. **`tree` as the HTML tree's name in `outputs`** (§1, §2), a word of one syllable beside the plugins' names, which are lowercase letters a to z alone (ADR-333 §1).
2. **`Plugin.drawsFromTheTree()` defaulting to true** (§1, §4), so the two plugins that stand need no change to say what they already do.
3. **The reading of the key, and its default the three outputs drawn without this record** (§2), so that a profile written before it draws what it drew.
4. **The two refusals, their order and their words** (§3), on the shape of ADR-384 §2's line, a name unknown before a tree missing, since the second cannot be read until every name is known.
5. **`records` beside `drawing` on `Plugin`, and `takesMemberPassages` on the seam** (§4), so a plugin that leaves member text out is never handed it, rather than handed it and asked to drop it.
6. **One way through the topics for the tree and the records, each record to each plugin before the next is composed** (§4), so neither a topic's rows nor a record is held longer than ADR-223 §9 allows.
7. **The fields of §5, and the cluster's title, or its label, on a passage** (§5), so a passage found by a store can be placed without its cluster's synthesis doc.
8. **The id hashed from the key's parts, a passage keyed by its member's content hash and its ordinal** (§6): the content hash because a cluster holds many members and an occurrence id is per walk; hashed so that a seed's path is not shown where a store shows ids.
9. **`writing_vector`, a table of its own, keyed by the writing's digest** (§7), so a redrawing embeds nothing it embedded before, and `vector`'s key stays `chunk_cache`'s.
10. **The artefact asked once a drawing, a different one stopping the plugins reading retrieval records** (§7), so a records file never holds vectors of two identities.
11. **The plugin named `retrieval`, its file `records.jsonl`, a `format` on every line** (§8), so a line imported alone still says what it is.
12. **Its delivery sending nothing and naming the file** (§8), each plugin having a destination (ADR-333 §1), and this one's being the operator's own store.
13. **No tree drawn means no `documents.csv` and no report of page sizes** (§9), each being a file of the tree.
14. **The sidebar section's place, its ids and its words** (§11), on the shape of the Seed folder section.

## Alternatives refused

Refused by the operator, the reason beside each being this record's:

- **Only the renderings beside the tree chosen, the tree always drawn.** An operator wanting retrieval records alone would pay for every page, picture and copy.
- **A choice on `--to` alone, or a launch value of the control panel.** `--to` chooses where a publish delivers, after the drawing; and a launch value would be lost to a `vespera run` started from a shell.
- **`Drawing`'s calls widened for records.** Every page plugin would carry calls it ignores, and a store plugin calls it would not need.
- **The store's own embedding.** A document's text would be read by an embedding model this project does not run, against ADR-264, and the vectors would follow no identity of ours.
- **Synthesis docs alone, no member text.** The destination is the operator's (ADR-305), and a plugin can still leave member text out.
- **A network store's client now.** The contract is settled first, and the file serves any store.

Refused here:

- **`outputs` in the generation run's identity.** It would mint a generation run, and ask every cluster's writing again, for a change of what is drawn.
- **A writing's vector kept in `vector`, under a chunker identity of its own.** `vector`'s key is `chunk_cache`'s, and a key of a kind that cuts no chunk would be read by every reader of it as a chunk's.
- **Writings embedded again at every drawing, and kept nowhere.** Every `vespera run` reaching the tree draws it again (ADR-333 §12), so each would ask Ollama once a written cluster and once a topic, for vectors already made.
- **The id carrying the generation run or the language.** It would not be stable across drawings, and a store could not replace a record by it.
- **The records file at a stable folder of the working directory, refreshed by a publish, as the website is.** A second copy of a file that may be large, for a store that can import it where it is drawn.

## Consequences

- **An operator chooses what is drawn**, in the profile or the sidebar, and one wanting retrieval records alone draws no page, picture or copy, and reads no document from the archive at stage 6b.
- **The deliverable may hold no HTML tree**, and a `vespera run` naming a plugin that draws from it without it stops at its start.
- **A store is fed by importing one file**, every record with its vector under one embedder identity, and nothing is sent anywhere.
- **Stage 6b may ask Ollama's embedding model** where a writing has no vector kept, on the recorded path too.
- **Seven stages move with the second step**, the arrangement approved again and every cluster's writing asked again.
- **ADR-214, ADR-301, ADR-312, ADR-333 and ADR-435 carry a note at their heads.**

## Known limits

- **The records file can be large**: a component of a vector takes at most sixteen bytes with its comma, `Float.toString` writing at most fifteen characters for a float32, so a vector of 4,096 components takes up to 64 KiB of its line, and a corpus of many passages a file of many gigabytes. No room is checked before it is written, as none is for the website (ADR-333 Consequences).
- **Two languages give two files whose ids are the same** (§6): a store holding both keys each record by its id and its `language`.
- **A cluster's ordinal is its arrangement run's**: another arrangement may give a different cluster the same key, and so the same id. A store that replaces the whole file at each import, as §8 says, is not misled.
- **What an earlier drawing left is kept** (§9), so a publish to a plugin left out of `outputs` delivers that plugin's rendering of the last drawing that drew it, of the same generation run.
- **See-also links, pictures and copies are not in a retrieval record.**

## What this does not decide

- **Any store's client**, a plugin that sends, and how it updates or removes what an earlier generation run sent.
- **A choice for the operator of whether member text reaches the retrieval plugin**: the first plugin takes it, and a key for it is a later record's.
- **A retrieval record of a code set's files**, or of a topic with no survivor.
- **A vector database in place of SQLite for stages 4 and 5**, which ADR-214 refused and this record leaves refused.

## Tests

Built with the step of §15 that needs each. The first four classes, the table's first thirteen rows, come with this record.

| Method | What it holds |
| --- | --- |
| `deliverable.TheOutputsAreReadFromTheProfilesKeyTest.anUnsetOrBlankKeyDrawsTheTreeConfluenceAndTheWebsite` | unset, blank and a key of commas alone each read as the three, in that order (§2) |
| `…eachNameIsReadOnceStrippedAndInLowerCase` | `" Retrieval , TREE,,retrieval "` reads as `retrieval`, `tree`, and draws the tree (§2) |
| `…aNameThatIsNoOutputIsRefusedNamingWhatIsKnown` | the reason and the advice, whole, for one unknown name and for two (§3) |
| `…aPluginDrawnFromTheTreeWithoutTheTreeIsRefused` | the reason and the advice, whole, for one and for two such plugins; none for `retrieval` alone, nor for the three (§3) |
| `…anUnknownNameIsToldBeforeAMissingTree` | a key naming both faults gives the first reason alone (§3) |
| `deliverable.ARetrievalRecordIsKeyedByItsPlaceTest.eachKindsIdIsTheDigestOfItsKey` | each kind's id equals the SHA-256 of its parts joined by U+001F, an empty subtopic written `-1` (§6) |
| `…theSameKeyGivesTheSameIdAndAnotherKeyAnother` | the same slot twice, the same id; another subtopic, cluster, member or passage, another id (§6) |
| `pipeline.TheStartRefusesOutputsNoDrawingCanDrawTest.aProfileNamingTheWebsiteAloneStopsTheStartInOneLine` | a `profile.yaml` naming `website` alone: the whole line (§3) |
| `…aProfileLeavingTheKeyUnsetStopsNothing` | a `profile.yaml` with no `outputs`: no line (§3) |
| `retrieval.TheRecordsFileIsOneJsonLineARecordTest.eachRecordIsOneLineInTheOrderTaken` | an overview, a synthesis doc and a passage taken: three lines, whole, in that order, each ended by `\n` (§8) |
| `…aTextIsEscapedAndWrittenAsUtf8` | a quote, a backslash, a line break and an accented letter, whole (§8) |
| `…nothingStandsAtTheFileUntilTheReadingFinishes` | before `finish` no `records.jsonl`; after it, the file (§8) |
| `…thePluginDrawsFromNoTreeAndTakesMemberPassages` | its name, its list, `drawsFromTheTree` false, `takesMemberPassages` true (§8) |
| `pipeline.OutputsAtTheStartInvocationTest` | a `vespera run` whose `outputs` names `confluence` alone exits 1 with §3's line, walking nothing and minting no run (§3) |
| `deliverable.RetrievalRecordsAreHandedInTheTreesOrderTest` | two scripted plugins reading records over `ThreeTopics`: each record in §5's order, to each in name order before the next; an unwritten cluster's passages and no synthesis doc; a plugin not taking member passages handed none (§4, §5) |
| `deliverable.ADeliverableWithNoTreeHoldsNoPageTest` | outputs `retrieval` alone: the marker and `retrieval/` with `drawing.txt`, no `index.html`, no topic folder, no copy, no `documents.csv`, no `code/`; an earlier drawing's files kept (§9) |
| `deliverable.AFailedRetrievalReadingStopsItAloneTest` | composing that throws, and a plugin that throws at each call in turn: the tree byte for byte as with none, the other plugins whole, `drawing.txt` `failed` (§4) |
| `pipeline.TheRecordsCarryTheScoringRunsVectorsInvocationTest` | a drawing with `retrieval` named: each passage's vector the one `vector` holds under the scoring run's identity; each writing's kept in `writing_vector` and not embedded at the next drawing; an artefact other than the run's stops the plugin alone and names it on the closing line (§7) |
| `embedding.AWritingsVectorIsKeptByItsDigestTest` | a writing embedded once under an identity, read back by its digest; another identity, another row (§7) |
| `pipeline.PublishToRetrievalSendsNothingTest` | the whole closing line, exit 0, nothing written beneath the working directory (§8) |
| `ui.TheOutputsSectionTest` | the eleven sections in order; the four boxes ticked as the key reads; unticking HTML pages unticks and disables two; Save writing the names (§11) |
| `controlpanel` contract test of the closed lists | `DESTINATIONS` with `retrieval`, the outputs and those drawn from the tree equal to the jar's (§11) |

**What the build changes in tests that stand.** `ModuleBoundariesTest`: `RECORDED_MODULES` gains `retrieval`, `DECLARED_DEPENDENCIES` gains it with `ledger`, `deliverable` and `synthesis`. `PipelineHoldsOnlyTheRulesOnRecordTest`: `OutputsAtTheStart` and the class implementing `RecordInputs` on record as holding no rule. `EachTableIsNamedOnlyByItsOwnerTest`: `writing_vector` owned by `embedding`. The contract test of `ProfileKey` and the README's table: `outputs`, entering no run. `RunIdentityGoldenTest`: unchanged, no stage's modules changing.

## What the implementation owes

- **In `deliverable`**: `Outputs`, `RetrievalRecords`, `RetrievalRecord` with `Kind` and `Placement`, `RetrievalRecordId`, `RecordInputs`; `Plugin`'s two methods; `Deliverable.writeTo` taking the outputs and the inputs, drawing no tree where it is not named.
- **In `extraction`**: the read of a content hash's chunks under two identities. **In `embedding`**: the read of their vectors under an embedder identity, `writing_vector` in `schema.sql` with its owner's line, and the embedding of a writing kept by its digest.
- **In `profile`**: `outputs`, the last component. **In `pipeline`**: `OutputsAtTheStart`, the inputs over `extraction` and `embedding`, and the closing line of §9.
- **A module `retrieval`**, under `io.algernon.vespera.publish.retrieval`, with `RetrievalPlugin`.
- **In `ui`**: the section of §11, `ProfileKey.OUTPUTS` and its texts in `profile-texts.json`, and the closed lists.
