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
The single copy chosen for publication when several file occurrences share one content identity.
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

### Relevance

**Seed set**:
The operator-supplied folder of known-relevant documents. The sole carrier of domain knowledge in the system: it defines relevance, names the published taxonomy, and shapes the page tree.
_Avoid_: training set, examples, ground truth

**Hard negative**:
A document near the decision boundary — plausibly relevant, actually not. The only kind of negative that informs a threshold, and one that cannot be supplied in advance.
_Avoid_: counter-example, negative sample

**Winning seed**:
The one seed document that produced a file occurrence's relevance score. Stored, so that a seed's influence over the corpus is a question anyone can ask.
_Avoid_: best match, nearest seed

**Seed partition**:
All file occurrences sharing a winning seed. The unit within which grouping happens, and the top level of the published tree — so its size is also a statement about the seed that owns it.
_Avoid_: bucket, category, topic

### Measurement

**Census**:
A verdict-free measurement pass. It renders no verdicts, excludes nothing, and mutates nothing; it produces re-analyzable data and a draft profile with thresholds left unset.
_Avoid_: scan, audit, inventory

**Observe before enforce**:
The rule that no threshold is applied before it has been measured. Thresholds ship unset, each pointing at the measurement that should inform it.
_Avoid_: calibration, tuning

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
One step of the cascade, identified by the verdicts it writes. The two measurement stages write none, and the publication step is an adapter rather than a stage.
_Avoid_: step, phase, pass

**Run**:
One execution of one stage under one configuration. Minted when the configuration changes, continued when work resumes. Not a pass over the pipeline -- each stage has its own.
_Avoid_: job, pass, execution

**Invocation**:
One call of the command. It may advance several stages, and therefore span several runs.
_Avoid_: run, session, job

### Output

**Synthesis doc**:
A generated document that makes a set of survivors coherent by connecting them. Not a per-document summary — summarising each survivor separately reproduces the heap with an extra layer.
_Avoid_: summary, digest, abstract

**Publication-ready artifact**:
The pipeline's terminal state: page tree, surviving originals, generated text, and citations resolved to file occurrences. What an adapter renders, for any target.
_Avoid_: the wiki, the Confluence output, the export

### Cached artifacts

**Extraction cache**:
Stored extractor output, keyed so that an engine swap can never silently serve output produced by a different model.
_Avoid_: extraction results, parsed store

**Chunk cache**:
Stored chunk boundaries. Chunking is an artifact rather than a function here — determinism comes from persistence, because a calibrated threshold is meaningless against boundaries that move between runs.
_Avoid_: splits, segments
