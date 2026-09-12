# Vespera

Vespera sifts multiformat bulk files to build a publication-ready knowledge base. It measures the corpus before judging it, removes what is broken, redundant or topically irrelevant, and synthesizes connective material over what survives.

The vocabulary below is the project's own. Decisions that established these terms are recorded in [`docs/adr/`](./docs/adr/README.md).

## Language

### Identity

**Entry**:
Anything a walk finds beneath a corpus root, before anything has been decided about it. The denominator a walk is accountable for: every entry is either recorded as a file occurrence, recorded as a walk anomaly, or — a readable directory only — descended into, which is what makes "excludes nothing" a claim anyone can check rather than assert.
_Avoid_: node, item, dirent

**File occurrence**:
A file found at a path during a walk. The unit everything else is recorded against.
_Avoid_: document, file

**Content identity**:
A distinct byte sequence. A relation over file occurrences, never a replacement for their identity.
_Avoid_: document, duplicate, hash

**Representative occurrence**:
The single copy left standing when several file occurrences share one content identity, the rest being recorded as duplicates of it.
_Avoid_: canonical copy, the original

### Curation

**Trash**:
The union of three separable conditions — mechanically broken, redundant, and topically irrelevant. Name the specific condition when only one is meant.
_Avoid_: junk, noise, garbage

**Ledger**:
The single record of file occurrence identity, verdicts and run identity — the vocabulary every stage records against. Not the database: each capability owns its own tables alongside it, and the ledger is only the part that says what exists and what was judged.
_Avoid_: the database, the store, the index

**Verdict**:
A recorded judgement against one file occurrence by one stage, carrying its reason. Verdicts accumulate; they never replace one another.
_Avoid_: status, state, flag

**Survivor**:
A file occurrence carrying no blocking verdict. A question the ledger answers, not a place documents are moved to.
_Avoid_: shortlist, whitelist, the keep pile

**Redundancy set**:
File occurrences whose text says the same thing, of which exactly one survives and the rest are redundant with it. Distinct from a content identity, whose members are byte-identical; these differ, and which one survives is a judgement rather than a tie-break.
_Avoid_: duplicate group, cluster (a cluster is an arrangement of relevant documents, not a set of interchangeable ones), near-dupe set

**Containment**:
The relation where one document's text appears near-whole inside another's, and the two are therefore not interchangeable: the container carries everything the contained document does, and more. Directional, always — naming it of a pair without saying which way round says nothing.
_Avoid_: overlap, subset, inclusion

### Relevance

**Seed set**:
The operator-supplied folder of known-relevant documents. The sole carrier of domain knowledge in the system: it defines relevance, names the top level of the arrangement, and shapes what sits beneath it.
_Avoid_: training set, examples, ground truth

**Unusable seed**:
A seed document extraction produced no text from. Recorded and reported, never removed and never a verdict: a seed is not a candidate at all, so nothing in the verdict vocabulary applies to it, and what it costs is a definition of relevance narrower than the operator intended.
_Avoid_: broken seed, failed seed, invalid seed

**Seed/corpus mismatch**:
A difference in *form* between the seed set and the survivors it will be scored against — language, length, born-digital against converted, OCR damage — large enough that the score measures format rather than topic. A measurement and never a judgement: it is reported before the corpus is embedded, and what to do about it is the operator's. It cannot see a mismatch of subject, which only the score distribution reveals.
_Avoid_: drift, skew, bias, incompatible seeds

**Hard negative**:
A document near the decision boundary — plausibly relevant, actually not. The only kind of negative that informs a threshold, and one that cannot be supplied in advance.
_Avoid_: counter-example, negative sample

**Relevance label**:
A person's recorded answer about one document: relevant to the seed set, or not. Not a verdict — it removes nothing and no stage writes it — and not a measurement, because no re-run can produce it a second time. It is a fact about the document rather than about the run that showed it, so it outlives the score that prompted it and the model that computed that score.
_Avoid_: annotation, ground truth, judgement, rating

**Winning seed**:
The one seed document that produced a file occurrence's relevance score. Stored, so that a seed's influence over the corpus is a question anyone can ask.
_Avoid_: best match, nearest seed

**Seed partition**:
All file occurrences sharing a winning seed. The unit within which grouping happens, and the top level of the arrangement — so its size is also a statement about the seed that owns it.
_Avoid_: bucket, category, topic

**Cluster**:
A group of documents within one seed partition that belong together by subject — the level below the partition in the arrangement. An arrangement of relevant documents, never a set of interchangeable ones (that is a redundancy set), and never a judgement: nothing is removed for the cluster it lands in. What a document contributes to it is what the document *is*, not the passage that earned its score.
_Avoid_: group, theme, cluster of duplicates

### Measurement

**Census**:
A verdict-free measurement pass. It renders no verdicts, excludes nothing, and mutates nothing; it produces re-analyzable data and a draft profile with thresholds left unset.
_Avoid_: scan, audit, inventory

**Observe before enforce**:
The rule that no threshold is applied before it has been measured. Thresholds ship unset, each pointing at the measurement that should inform it.
_Avoid_: calibration, tuning

**Derived metric**:
A per-occurrence measurement written while the document is open, never a second traversal over it (ADR-019). Per-document values belong to extraction's own pass; anything corpus-wide is a later pass over the stored values, not another pass over the archive (ADR-073). Stored as counts rather than ratios, so a denominator is never lost.
_Avoid_: statistic, feature, attribute, score

**Shingle**:
An overlapping fragment of a document's text, hashed, stored so that redundancy and boilerplate are questions over fragments rather than over whole documents (ADR-038, ADR-018). Computed in extraction's pass but owned by `similarity`; its granularity is part of its stored identity, because a granularity change is a different measurement rather than a correction of the same one (ADR-073).
_Avoid_: n-gram, token window, fingerprint

**Boilerplate**:
Text repeated across enough of the corpus that its recurrence is a fact about document production — a template, a letterhead, a standard disclaimer — rather than about content. Measured as a shingle's document frequency, corpus-wide; identifying it is a measurement, not a verdict, so stage 3 stays as verdict-free as census (ADR-038, ADR-074).
_Avoid_: template text, chrome, junk, filler

**Bake-off**:
The embedding-model selection mechanism specifically: candidates measured over the same sample and seeds, each re-chunked to its own tokenizer, judged by the relevance-threshold gate (ADR-034, ADR-044). Not a general word for comparing two models — there is exactly one bake-off in this design, and the extraction engine does not have one (ADR-072).
_Avoid_: shoot-out, A/B test, champion/challenger, evaluation

**Reference model**:
A deliberately larger model an unfavourable measurement is re-checked against before it is believed. A role, not a stage: the pipeline never invokes one, and confirmation happens between runs, compared by a person (ADR-072). The same hosted model may separately be a bake-off candidate; that is the competition, this is the confirmation.
_Avoid_: fallback, oracle, champion, second opinion

### Operation

**Profile**:
The per-corpus record of every judgement the engine cannot make for itself. Authored by a person, never guessed at; each value carries how it was arrived at.
_Avoid_: config, settings, parameters

**Gate**:
A value the pipeline requires and does not have. Not a pause — supply the value and no gate occurs; leave it unset and the run ends there, having recorded everything it learned.
_Avoid_: approval step, pause. Also "checkpoint" **for this concept**: a gate is not a point work resumes from, and the word now names one (see Checkpoint below).

**Walk**:
One observation of a filesystem, producing file occurrences. Carries the root it observed and whether it finished, because a partial walk that looks complete curates a fraction of the archive and reports success.
_Avoid_: scan, crawl, import

**Checkpoint**:
A point a walk may be continued from: the last directory whose whole subtree was recorded, stored with the counts the walk had reached by then. Not a gate and not a pause — nothing waits at a checkpoint, and a walk that never reaches another one simply repeats the entries since the last. ADR-055 named this, after the Gate entry above had already claimed the word.
_Avoid_: bookmark, savepoint, offset

**Walk anomaly**:
An entry a walk encountered and did not record as a file occurrence, carrying the reason. Not an error — most are not failures — and not a verdict: a verdict needs an occurrence to attach to, and an anomaly is exactly the case where none exists. Kinds are observations rather than policy, which is what makes the first walk of an archive a measurement.
_Avoid_: error, skip, exclusion, warning

**Stage**:
One step of the cascade, identified by the verdicts it writes. The two measurement stages write none. There are seven, ending at generation (6b), and nothing follows them.
_Avoid_: step, phase, pass

**Run**:
One execution of one stage under one configuration. Minted when the configuration changes, continued when work resumes. Not a pass over the pipeline -- each stage has its own.
_Avoid_: job, pass, execution

**Upstream run**:
The run a stage names as its immediate predecessor — the run of the stage before it, over the same walk, whose record this stage read. Each run names one, and earlier ones are reached through it rather than listed alongside it. Where one walk holds two runs of the same stage, there is no upstream run until a person says which of them is meant.
_Avoid_: parent run, previous run, run chain, ancestry

**Invocation**:
One call of the command. It may advance several stages, and therefore span several runs.
_Avoid_: run, session, job

### Output

**Synthesis doc**:
A generated document that makes a set of survivors coherent by connecting them. Not a per-document summary — summarising each survivor separately reproduces the heap with an extra layer.
_Avoid_: summary, digest, abstract

**Arrangement**:
The shape stage 6a gives the survivors: seed partitions at the top, clusters beneath them. An order over survivors, never a copy of them — a document is arranged where it belongs, not moved there.
_Avoid_: page tree, hierarchy, taxonomy (a seed set names one; the arrangement is what that naming produces)

**Cluster label**:
What stage 6a calls a cluster: derived from the cluster's own members — the title of its highest-scoring document, and how many documents it holds. Derived rather than written, so it exists before anything is generated and can be checked against a document anyone can open. Never unique and never edited by hand: a cluster is identified by its ordinal within its seed partition, not by what it is called.
_Avoid_: cluster name (a cluster has two, and the word chosen says which), heading

**Cluster title**:
What stage 6b calls a cluster in the deliverable: written by the model that read the whole cluster, as the head of the synthesis doc it belongs to. A cluster whose synthesis doc could not be generated shows its label instead.
_Avoid_: cluster name, cluster label (the label is derived and 6a's; this is generated and 6b's)

**Citation**:
How a synthesis doc points at a document it was written from: an ordinal into the documents that call sent, rendered in the deliverable as a link to that document's entry in the cluster file's membership list. Minted by Vespera for one call rather than written by the model, so a fabricated one is out of range rather than merely wrong. What it points at is never an occurrence id: the reader follows it without a database.
_Avoid_: reference, source, footnote

**Cluster fault**:
A cluster stage 6b could not write a synthesis doc for: the call came back, and what came back did not survive checking. Recorded against the cluster, never against its documents — nothing is wrong with them, and nothing is removed. The deliverable keeps the hole, headed by the cluster's label.
_Avoid_: error, failure, skipped cluster, verdict (a verdict is about a document, and it removes one)

**Repair pass**:
A re-run of generation that finishes what an earlier one left behind: the clusters it never reached, and the clusters it could not write. It asks the same question again and is never shown what went wrong before, so nothing is corrected — only completed, and each pass is smaller than the one before it.
_Avoid_: retry, regeneration, repairing a synthesis doc (a synthesis doc is written or it is not; nothing edits one)

**Publication-ready artifact**:
The pipeline's terminal state, and the deliverable: the arrangement, the synthesis docs written over it, and a listing of the survivors it arranges — self-contained and handed over as files. It carries no copy of the surviving originals: those stay in the archive and are referenced from it. Nothing in this project renders it: publication is the state it is handed over in, never something this system does.
_Avoid_: the wiki, the Confluence output, the export

### Cached artifacts

**Instrument**:
Anything whose version changes what a cached artifact means: the extractor, the chunker, the tokenizer, the embedder. Each carries an **identity** that keys the rows it produced, so a changed instrument mints new rows rather than overwriting the old ones — which is what lets two of them coexist for comparison. An identity is composed of what the instrument's runtime reports about itself plus what we asked of it, never of where it is served: two deployments answering alike are one instrument, and moving a port is not a change. It refuses a blank value, because an instrument that cannot say what it is cannot be told apart from one that can.
_Avoid_: engine, tool, model (each names one instrument at most, and "model" is the embedder's name rather than the embedder)

**Extraction cache**:
Stored extractor output, keyed so that an engine swap can never silently serve output produced by a different model.
_Avoid_: extraction results, parsed store

**Chunk cache**:
Stored chunk boundaries. Chunking is an artifact rather than a function here — determinism comes from persistence, because a calibrated threshold is meaningless against boundaries that move between runs.
_Avoid_: splits, segments
