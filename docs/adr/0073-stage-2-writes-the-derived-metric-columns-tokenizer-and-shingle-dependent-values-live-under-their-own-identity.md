# ADR-073 — Stage 2 writes the derived-metric columns; tokenizer- and shingle-dependent values live under their own identity

- **Date**: 2026-09-03
- **Status**: accepted
- **Amends**: narrows ADR-058's "its module" to the modules a step's pass invokes; settles the column list ADR-070 deferred; places (does not answer) the signals ADR-064 deferred; keeps ADR-038's assignment while fixing where its code lives and how its parameters are keyed; adds one dependency under ADR-046

## Context

Three decisions each left an obligation pointing at this one, and none of them named a column.

- **ADR-019** says content census is "not a second traversal — extraction writes per-occurrence metrics as columns while the document is open." It is a mechanism with no list behind it. The record is reconstituted, so there is no surviving detail to recover: whatever the list is, it is decided now rather than remembered.
- **ADR-070** made the `ConfidenceScores` snapshot an *obligation* rather than an option — tier 2's quality floor cannot be calibrated later from data never stored, and re-converting hundreds of gigabytes to recover a number the first pass held is not a recovery plan — and left "the exact column list" here. It also obliged, in passing, that a `partial_success` document's errors are "recorded in stage 3's derived-metric columns regardless" of whether a verdict is written.
- **ADR-064** deferred the format-vs-topic mismatch-detection question to stage 5 and named the signals it is blocked on: **OCR-error rate, chunk count, language mix**. It did not say whether those are ADR-019's columns or something extra.
- **ADR-038** assigns shingles to the same pass ("shingles computed as a derived column during extraction"), while `docs/architecture.md` §1.3's module table assigns "Shingles, MinHash/LSH" to `similarity` and gives `extraction` "derived metrics." Both cannot be read literally at once.

### What a Docling response actually gives away for free

Read off `docling` on `main` (`docling/datamodel/base_models.py`, `docling/datamodel/service/responses.py`), continuing ADR-070's reading of the same files.

`ConvertDocumentResponse` carries `document` (the export), `status`, `errors[]`, `processing_time: float`, `timings: dict[str, ProfilingItem]`, and `confidence: Optional[ConfidenceScores]`. Three findings matter here:

1. **The response's confidence is the flat document-level aggregate.** The per-page `pages: dict[int, PageConfidenceScores]` lives on the in-process `ConfidenceReport`, not on the response. Per-page confidence is not available to a client over HTTP, so no amount of column design can persist it — and ADR-071 fixed one synchronous HTTP call as the invocation contract.
2. **Docling reports no OCR-error rate.** `ocr_score` is a *confidence* in the OCR, not a rate of errors in it. Nothing in `base_models.py` reports an error rate, and the two are not interchangeable: a confident OCR of a degraded scan is exactly the silence ADR-070 says `degenerate-output` exists to catch.
3. **Docling reports no language at all.** There is no language field anywhere in the response. Language mix is not a free signal; it is a new computation with a new dependency behind it.

Page count is free only because chunking already needs the `DoclingDocument` export — it is read off the document's own `pages`, not off a response field, and a `.txt`/`.docx` has none.

### Two structural problems the ticket did not name

**Chunk count is not a property of an occurrence.** ADR-044 keys the chunk cache on content hash + chunker + tokenizer identity precisely because the bake-off re-chunks per candidate model, and the tokenizer derives from an embedding model `docs/architecture.md` marks "not yet chosen" — which is why the stage-2 map excludes it. A single `chunk_count` column would assert one number for a value that has one per candidate.

**Stage 3 has no pass of its own.** The cascade table names stage 3 "Content census" and says its metrics are written during extraction. But ADR-048 makes a derived row owned by a *run*, and ADR-058 makes a run's implementation version the last commit touching its module. Which run id these rows carry, and therefore which implementation change forces them to be recomputed, was never stated.

## Decision

### The line between stage 2 and stage 3 is per-document versus corpus-wide

**Per-document metrics are stage 2's, written under stage 2's run id, in stage 2's single pass. There is no stage-3 run for them.** ADR-019's mechanism read literally: the document is open exactly once, and everything measurable from that one document is measured then.

**Stage 3 is the corpus-wide pass over what stage 2 stored** — ADR-038's document-frequency computation that identifies boilerplate, and the census report's distributions — and it mints its own run id when it runs, reading columns rather than documents. It remains a stage that writes no verdicts, exactly as stage 0 is.

This keeps `CONTEXT.md`'s **Stage** entry honest ("identified by the verdicts it writes… the two measurement stages write none") without inventing a second traversal ADR-019 forbids.

### `extraction_metric` — one `extraction`-owned table, keyed by occurrence and run

Following the shape `content_hash` and `superseded_by` already use (ADR-067, ADR-069): primary key `(occurrence_id, run_id)`, so a later run under a changed implementation recomputes rather than overwrites.

**Written for every occurrence the conversion returned a response for** — including one that earned `extraction-failed` on a document-scoped status, and including `partial_success`, which ADR-070 passes through with no verdict at all. A service-scope failure writes nothing here either, for ADR-070's reason: there is no measurement of a document that was never converted.

**The response's own report** — three columns, discharging ADR-070's "recorded regardless":

| Column | Type | Source |
|---|---|---|
| `status` | TEXT | Docling's `ConversionStatus`, verbatim |
| `error_count` | INTEGER | `len(errors)` |
| `error_categories` | TEXT | the distinct `FailureCategory` values in `errors[]`, sorted, comma-joined |

`status` is the column that makes `partial_success` visible: it earns no verdict, so without a row saying so, nothing in the database records that the conversion was incomplete. `error_categories` is a queryable projection, not the record — the full `errors[]` lives in the extraction cache's stored response.

**The confidence snapshot** — all eight, nullable throughout, discharging ADR-070's obligation exactly:

| Column | Type |
|---|---|
| `parse_score`, `layout_score`, `table_score`, `ocr_score`, `mean_score`, `low_score` | REAL, nullable |
| `mean_grade`, `low_grade` | TEXT, nullable (`QualityGrade` verbatim) |

**Null means "not measured", never "poor"** — ADR-070's rule, now with a storage representation that can hold the distinction. NaN, which Docling serialises as null, is stored as SQL NULL. **No per-page confidence rows**, because the response does not carry them.

**`processing_time_seconds`** (REAL) is stored; `timings` is not. One number that says what the conversion cost is a metric; a per-component profiling map is instrumentation, and the stage-2 throughput question is on the map's out-of-scope list anyway.

**Size** — tokenizer-independent only:

| Column | Type | Definition |
|---|---|---|
| `page_count` | INTEGER, nullable | the `DoclingDocument`'s own page count; null for an unpaginated format, which is the same "not measured" reading as a null score |
| `char_count` | INTEGER | characters of extracted text after whitespace normalisation |
| `alphanumeric_char_count` | INTEGER | of those, the alphanumeric ones |
| `word_count` | INTEGER | whitespace-separated runs after the same normalisation |

`alphanumeric_char_count = 0` **is tier 1's predicate**, verbatim from ADR-070 ("empty, or carries no alphanumeric content once whitespace is normalised"). The floor and the metric share one normalisation and one count, so a `degenerate-output` verdict is auditable from the row that produced it rather than being a judgement with no arithmetic behind it.

**Garbage counters — a proxy for the OCR-error rate Docling does not report:**

| Column | Type | Definition |
|---|---|---|
| `word_char_total` | INTEGER | sum of word lengths, so mean word length is derivable |
| `vowelless_word_count` | INTEGER | words containing no vowel |
| `single_char_word_count` | INTEGER | words of length one |

**Counters, never ratios.** A stored ratio hides its denominator and cannot be re-aggregated: stage 3's corpus-wide pass needs sums, and every ratio anyone wants is derivable from these plus `word_count` and `char_count`. This is the same reason ADR-055 stores cumulative counts on the walk row.

**These are Latin-script heuristics and are recorded as such.** "Contains no vowel" says nothing about Chinese and little about Arabic. That is precisely why they ship alongside the language column: the language is what tells a reader whether the counters mean anything for a given document. Neither `ocr_score` nor any combination of these counters *is* "the OCR-error rate" — the rate as a number with a threshold behind it stays unset, per **observe before enforce**, and this ADR stores the ingredients rather than declaring the recipe.

**Language — detected here, with a new dependency:**

| Column | Type |
|---|---|
| `primary_language` | TEXT, nullable (ISO 639-1, or null when detection declines) |
| `language_confidence` | REAL, nullable |

Detected over the whole extracted text, document level. **This is a new dependency, taken deliberately**, and ADR-046's rule is satisfied by this record being the recorded decision that requires it. The argument is ADR-070's own, applied to a second signal: language is derivable only from text, the text is open exactly once, and recovering a language label later means re-converting the archive.

**The library is Lingua (`com.github.pemistahl:lingua`).** Single-purpose, Apache-2.0, models bundled in the artifact so nothing is downloaded at runtime, and it reports ranked confidences rather than a bare guess — which is what makes `language_confidence` storable and a declined detection representable as null. Two costs are recorded rather than discovered later: the all-languages artifact is large (models are the bulk of it, and a language subset or its low-accuracy mode are the levers if that bites), and its latest release is 1.2.2 from 2022, so it is stable rather than actively developed. **Apache Tika's `tika-langdetect-optimaize` was weighed and rejected**: pulling `tika-core` into a design that has deliberately kept both Tika and POI out (ADR-010, ADR-068) is a far larger commitment than a single-purpose library, for a strictly weaker detector on short text.

**"Language mix" is not this column.** A mix is a distribution, and a distribution over one document is an aggregation over its chunks — which is stage 3's corpus-wide pass, over chunk-level detection, if stage 5 ever asks for it. Stage 2 stores the primary language and its confidence; the mix is derivable later without re-converting anything, which is the only property that had to be protected here.

### Chunk count is a query against the chunk cache, not a column

**No `chunk_count` column exists.** The count is `COUNT(*)` over the chunk cache for an occurrence under a given chunker + tokenizer identity (ADR-029, ADR-044) — already keyed that way, so the number is already stored, once per candidate, exactly as many times as there are honest answers.

ADR-064 named chunk count as a mismatch signal; what this decides is **where it lives**, and it flags the constraint that comes with it: **chunk count is comparable only within one chunker + tokenizer identity.** A stage-5 comparison of seed shape against corpus shape must hold that identity fixed or it is comparing tokenizers.

### Shingles: `similarity` owns the code and the table, `pipeline` composes the pass

ADR-038 and the module table are reconciled in `similarity`'s favour on ownership and ADR-038's favour on timing, because `docs/architecture.md` §1.3 has a rule that forbids the obvious middle: **a capability module may depend on `ledger` and nothing else horizontal.** `extraction` therefore cannot call into `similarity`, and shingling code moved into `extraction` would put it in the module the table says does not own it.

- **The shingling function and its table live in `similarity`.** The module table stands unchanged.
- **The pass is composed in `pipeline`**, the composition root — the same move already recorded for the chunker, which "gets tokenizer identity from `pipeline`." Stage 2's step hands the extracted text, once, to both `extraction`'s metric writer and `similarity`'s shingler. One document open, two modules writing, no horizontal dependency.
- **Rows are keyed `(occurrence_id, run_id, shingle_parameter_identity)`**, with the shingle stored as a 64-bit hash. Document frequency is a `GROUP BY` over the hash — ADR-038's boilerplate detection needs no other shape.
- **Granularity is a code default, not a profile key.** It is a mechanism parameter, the same call ADR-071 made for the timeout budget and the streak counts. Because the parameters are part of the row's key, changing them mints a new identity instead of needing a migration or invalidating what is already stored — which is what lets the map's parked "shingle granularity" item be revisited against real data without a schema change.
- **Whether MinHash signatures are computed in this pass is stage 4's question, not this one.** ADR-018 owns that; nothing here forecloses it.

### Stage 2's implementation version covers the modules its pass invokes

Because one pass writes rows owned by two capability modules, **ADR-058's "the last commit touching its module" is read as "the last commit touching any module the stage's pass invokes"** — for stage 2, `extraction` and `similarity`'s shingler. No substance of ADR-058 changes: its purpose is that an implementation change forces recomputation, and a change to the shingler is an implementation change to stage 2's output. Reading it narrowly would let a shingler edit ship without minting a run, leaving stale rows under an unchanged run id. This is the same kind of narrowing ADR-070 applied to ADR-010's "can fail silently": the wording is made precise now that the mechanism behind it is known.

### Stage 2 measures whatever walk it is given

**Nothing in stage 2's step restricts it to the corpus walk.** It reads a walk's survivors, and ADR-064 already generalised the walk instrument to any root, a seed folder included.

This is recorded because **no column list makes the mismatch question answerable on its own.** A format-vs-topic mismatch is a comparison of seed shape against corpus shape; if seed documents are never extracted, no metric stored against corpus occurrences can express one. Stage 5 needs seed chunks embedded regardless (ADR-020 scores against seed *chunks*), so seed-side extraction is owed there anyway. **Whether stage 2's step is actually invoked over the seed walk is stage 5's decision, not this map's** — what is settled here is that the design does not stand in its way, and that the precondition is written down rather than discovered when someone tries to answer ADR-064's question and finds half the comparison missing.

### Nothing here bears a threshold

Every column is measurement. Stage 2's only profile key remains ADR-070's tier-2 confidence floor, shipped unset. No metric in this list gates, warns, or blocks — which is what makes stage 2 the measuring pass that a later threshold can be calibrated against.

### Vocabulary

`CONTEXT.md` gains **Derived metric** and **Shingle**. Both are load-bearing in ADR-019 and ADR-038 and neither was defined.

## Consequences

**ADR-064's three named signals resolve to three different places, and that is the substantive answer.** Chunk count is a query against the chunk cache, comparable only within one tokenizer identity. Language is one column plus one new dependency, with "mix" left as a derivable aggregation. OCR-error rate does not exist as a Docling signal at all, so what is stored is `ocr_score` plus dictionary-free counters, and the rate itself stays undefined until data defines it. **None of them is "exactly the columns ADR-019 already assigns"** — the honest answer to the ticket's framing is that one of the three is not a column, one needs a dependency, and one needs a definition nobody can write yet.

**The map's parked "shingle granularity" item now has a path back.** It was blocked on stage-3 OCR-error-rate data, and until now nothing in the design produced any. The counters above are that data — or rather, they are what a definition of it can be computed from, once a real run has produced them.

**`extraction` and `similarity` both gain a `schema.sql` and a `schema_version` row** (ADR-059), checked and refused independently. `similarity`'s arrives in stage 2's slice rather than stage 4's, which is earlier than the cascade order suggests and follows directly from ADR-038's timing.

**Shingle storage is the largest table in the database by a wide margin** — one row per distinct shingle per surviving occurrence per run. That is ADR-038's own consequence rather than a new one, but it is the first time anything has written it down, and it is a real number someone will meet on a first full run. It is also the reason the parameter identity is in the key: a granularity change that had to rewrite that table instead of adding to it would be close to unaffordable.

**A metric row exists where no verdict does.** `partial_success` and every `passed` occurrence get a row, and `status` is the only place an incomplete-but-usable conversion is recorded. Anyone asking "what did stage 2 actually see" reads this table, not the verdict table — the verdict table answers "what did stage 2 block."

**Two edits are owed to `docs/architecture.md`** as part of closing this ticket: §1.2's stage-3 row, to say that per-document metrics are written under stage 2's run and stage 3 is the corpus-wide pass, and §1.3's module table note, to record that `pipeline` composes the shingling call the same way it supplies tokenizer identity.

**One dependency is owed to the pom** (ADR-046), with its version pin and its language-subset/accuracy-mode configuration the hand-off spec's to write.

**Nothing here specifies types, SQL text, or call sites.** The exact `CREATE TABLE` statements, the record types, the whitespace-normalisation implementation, the vowel set, and where in stage 2's step each write happens are the stage-2 hand-off spec's — the same deferral ADR-068, ADR-069, ADR-070 and ADR-071 each made to a spec.
