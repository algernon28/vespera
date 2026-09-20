# ADR-133 — The exemplars one call sent are recorded, and a cluster file numbers its membership from that record

- **Date**: 2026-09-20
- **Status**: accepted
- **Amends**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) — its *"An in-range ordinal resolves to a surviving occurrence **by construction**, not by lookup"*, and the numbering rule that claim rests on: *"The cluster file's membership list is **numbered in relevance-score order** […] so the exemplars sent are exactly entries `1..k` of the list the reader is looking at."* Both are false wherever a member is dropped between the membership and the call, and two places drop members today. The correspondence is restored below by recording it rather than arguing it. **Nothing ADR-109 decided about the model-facing surface moves**: a citation is still an ordinal minted for one call, still rendered `[n]` inline and nowhere else, the check is still `1 ≤ n ≤ k` and nothing else, uncited prose still fails the cluster, and nothing is retried, regenerated or stripped.
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — **the disclosure sentence only.** Its *"Written from the 40 highest-scoring of 412 documents"* states something the same drop makes untrue, and it is replaced below by a sentence that is true under a drop. Everything else in that record stands: one exemplar-first call per cluster, the highest-scoring documents in score order filled until the budget runs out, an oversized document passed over, a cluster larger than the budget written anyway, and every response verified before its text is believed.
- **Amends**: [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) — **the table count only.** It says `synthesis` owns two tables; [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) made it three; it owns four. The rule that put them there is untouched, and it is the reason the fourth lands here rather than anywhere else: a derivation under a configuration lives in the capability that computed it ([ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md)), and which documents one call carried is exactly that.
- **Leaves [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md) and [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) whole**, deliberately and after checking. All three were candidates and none moves; the Decision says why.
- **Rests on**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) (nothing is opened, hashed or stat-ed at write time — the constraint that makes re-derivation impossible rather than merely expensive), [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) (the tree resolves its own links with no database), [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability owns its own tables), ADR-111 and [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) (a cluster's writing may have been produced by an earlier invocation of the same run), ADR-121 (the second of the two drops).
- **Found by the architect gate on [#187](https://github.com/algernon28/vespera/issues/187)**, which is the ticket that first wrote a cluster file, and so the first place the two numberings stood side by side in one artifact. Filed as [#236](https://github.com/algernon28/vespera/issues/236).

## Context

### The invariant, and where it stopped being true

ADR-109 does not check that a citation reaches the right document. It argues that it cannot reach the wrong one:

> An in-range ordinal resolves to a surviving occurrence **by construction**, not by lookup: Vespera built the mapping in the same call, from rows it had just read out of the ledger.

That argument needs one numbering to serve the prompt and the page. ADR-109 gets it by deriving the same order twice:

- **In the prompt**, `ClusterSynthesis` numbers from one the documents the call carried, in relevance-score order.
- **On the page**, `Deliverable` numbers from one every surviving document of the cluster, in relevance-score order.

Entries `1..k` of the second are the `k` highest-scoring documents of the cluster. The call carries the `k` highest-scoring documents of the cluster. So the two coincide — **provided nothing between the cluster's membership and the call's exemplar list removes a member.**

### Measured: two places remove members, and both are decided behaviour

**`GenerationTasklet.exemplarsOf` drops a member whose opening chunk this run cannot reach.** The chunk cache is keyed by content hash, so reaching it means hashing the file again, and a file deleted, renamed or locked since the walk hands nothing back. The same drop covers a document nothing was ever chunked from. Its own javadoc calls it what it is:

> a file the archive will no longer open is **the ordinary case rather than a broken one**: an archive is a live filesystem, and a document deleted, renamed or locked since the walk is a fact about that document.

**`ClusterSynthesis.whatFitsIn` drops a document larger than the whole room.** ADR-108 decided this and ADR-121's Context restates it. It is a `continue` and not a `break`, deliberately: such a document can never be sent under any fill order, so stopping on it would cost the whole cluster its writing.

Both are right. Neither is going to be removed. What neither record noticed is what they do to the numbering.

### What the drift actually looks like

A cluster of three surviving documents, scoring `0.93`, `0.77` and `0.41`. The middle one was renamed in the archive after the walk, so `exemplarsOf` cannot reach its opening chunk and leaves it out.

| Document | Score | Sent to the model as | Listed on the page as |
| --- | --- | --- | --- |
| `scaffold-audit-2024.pdf` | 0.93 | `[1]` | 1 |
| `method-statements.pdf` | 0.77 | dropped, unreachable | 2 |
| `toolbox-talks.docx` | 0.41 | `[2]` | 3 |

The model writes a sentence over `toolbox-talks.docx` and marks it `[2]`. Two documents were sent, so the check `1 ≤ 2 ≤ 2` passes. The deliverable rewrites the marker as a link to `#document-2`. Entry 2 of the list is `method-statements.pdf`, which the model never saw. **The link resolves, the reader lands on a document the sentence was not written from, and nothing in the artifact, the ledger or the logs says so.**

**Only a middle drop does this, which is why it is invisible.** The documents that are sent keep their score order among themselves — `whatFitsIn` sorts descending and takes a subsequence of that order — so the two numberings agree on every entry up to the first drop and disagree on every entry after it. Drop the lowest-scoring member and nothing moves. The failure is silent, it is data-dependent, and the data that produces it is an archive behaving normally.

**The same drop makes the disclosure sentence false.** The page says *"Written from the 2 highest-scoring of 3 documents"*. The two highest-scoring are the `0.93` and the `0.77`; the call carried the `0.93` and the `0.41`. The sentence names a set that includes a document the prose was not written from. That is not a rounding error in the wording: it is the same wrong claim as the citation, stated in words instead of a link.

### Measured: it cannot be put right at write time

Re-deriving the sent list where the tree is written is not expensive, it is impossible, and three independent things each make it so.

- **It needs the filesystem.** Which members were reachable is a fact about the archive at the moment the call was built. ADR-104 forbids opening, hashing or stat-ing anything at write time, for a reason this record has no interest in reopening: a terminal stage that checks the archive becomes a second, partial census, and a document that has moved has no verdict it could be recorded as.
- **The exemplars may never have existed in this process.** Under ADR-111 and ADR-115 a cluster's writing may have been produced by an earlier invocation of the same 6b run — the loop skips every cluster already carrying a `synthesis_doc` row. The tree is written by whichever invocation finishes the step, and for those clusters it computed nothing.
- **A count cannot be inverted.** `synthesis_doc.documents_sent` is a number. Nothing derives *which* documents from *how many*, whatever else is to hand.

### What this project does everywhere else

Record what happened; do not derive it again later. `cluster.document_count`, `partition_order` and `cluster_order` are stored rather than recomputed at render time, on ADR-112's reasoning that *"a number computed again at render time is a second place for the arrangement to be stated"*. `synthesis_doc` exists at all because ADR-110 judged that *"a stored answer is what makes it answerable later without a schema change"*. `cluster_fault` exists because ADR-111 judged that accounting for a cluster should cost a query rather than the most expensive call in the system, made again.

This defect is the general form of the thing all three guard against, and it is the first time the cost of the general form has been a wrong link rather than a recomputation.

## Decision

**The documents one call carried are written down, under the ordinal the model was given, and a cluster file numbers its membership list from that record rather than deriving it a second time.**

### `synthesis` gains a fourth table

```sql
CREATE TABLE IF NOT EXISTS call_exemplar (
    run_id TEXT NOT NULL REFERENCES run (id),
    winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    cluster_ordinal INTEGER NOT NULL,
    citation_ordinal INTEGER NOT NULL,
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    PRIMARY KEY (run_id, winning_seed_occurrence_id, cluster_ordinal, citation_ordinal),
    UNIQUE (run_id, winning_seed_occurrence_id, cluster_ordinal, occurrence_id)
);
```

**One row per document one call carried.** `citation_ordinal` is the number the model was given, counted from one; `occurrence_id` is the document it was given under that number. Together they *are* the mapping ADR-109 described and never wrote down.

**Keyed by the 6b run plus ADR-110's natural key, with the citation ordinal beneath it.** The same three columns `synthesis_doc` and `cluster_fault` carry, in the same vocabulary `document_cluster` uses, so no join needs translating and no surrogate id is minted. A second generation writes its own row set beside the first, exactly as the three tables above it do.

**The `UNIQUE` is load-bearing, not decoration.** A document appearing under two ordinals would put one document twice in the membership list and shift every entry after it, which is the defect this record exists to close, reintroduced from the other end. A call carries each document once; the schema says so.

**No score column, no chunk, no text.** The score is `embedding`'s and is already read for the manifest; the chunk is `extraction`'s, and it was sent rather than stored. What is owed here is the correspondence and nothing else, and every extra column is a second place for it to be restated.

### `documents_sent` is dropped from `synthesis_doc`

A count standing beside the list it counts is precisely the shape that produced this defect: two statements of one fact, kept in step by nothing. `documents_sent` becomes the number of `call_exemplar` rows under that key, and where the count is wanted in code it is the size of the recorded list.

This is not tidying. The whole of the decision is that the deliverable stops re-deriving what the call did, and leaving a count behind would let a future reader — or a future implementer — reach for the cheap number and rebuild the divergence one field at a time.

### The membership list is numbered from the record: sent first, in citation order, then the rest

A cluster file's list becomes **the documents the call carried, in the order their citation ordinals give them, followed by every other surviving document of the cluster, highest-scoring first.**

**This ordering is forced, not preferred.** Entries are numbered `1..M` down the page and anchored `#document-n`, and a citation `[n]` has to reach the n-th of them. If the documents the call carried were scattered through a globally score-ordered list, no sequential numbering of that list could agree with the ordinals the model was given. Sent-first is the only ordering under which one numbering serves the prompt, the check and the reader — which is ADR-109's own requirement, now met by a mechanism instead of by an argument.

**Where a drop never happened, nothing changes.** Sent-first *is* score order when the call carried the top `k`, which is the ordinary case. The new rule differs from the old one only on exactly the clusters where the old one was wrong.

**A cluster nothing was written over keeps the list it had**: every document, highest-scoring first. There is no call, no record and no citation, so there is nothing for the numbering to serve.

**A recorded exemplar the cluster no longer holds keeps its number and says so**, as *"a document the writing was made from, which this group no longer holds"*. It cannot arise from the ledger — `document_cluster`'s rows are written once per scoring run ([ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)) and the approval fixes which scoring run the writing and the membership are both read against — so what this covers is a database edited underneath the tool. It is **not** a stop, and that is chosen rather than inherited: a throw at write time escapes `Deliverable.writeTo` and rolls back every `cluster_fault` row the invocation had already recorded, which is the reason ADR-109's range check is not made a second time there. Dropping the entry instead is the one thing that is refused, because dropping it moves every document beneath it up one and points live citations at other documents — this defect, reintroduced by the writer that exists to close it.

### The disclosure says something a reader can check

ADR-108's

> *"Written from the 40 highest-scoring of 412 documents"*

becomes

> **"Written from the first 40 of the 412 documents in this group."**

It is true under a drop and true without one. *The first* points at the page's own numbering, which the reader is looking at and which the citations run over; *the highest-scoring* pointed at a property of the corpus the reader has no way to verify and that a drop makes false.

**The second number stays the one the arrangement recorded, and ADR-112 is untouched.** [#237](https://github.com/algernon28/vespera/issues/237) settled that this sentence and the index cell read the one `cluster.document_count` 6a stored rather than counting the list handed to the page, so that the size is stated once. Nothing here disturbs it: the list this record lays out holds exactly the cluster's own survivors, in a different order, so counting it would produce the same number by a second route — which is the thing that record refused.

The sentence still appears only where the call carried fewer documents than the cluster holds. A drop always makes the call carry fewer, so a drop is always disclosed; a cluster written from all of its documents still says nothing, because there is nothing to disclose.

### The rows are written on every call, and before the row that marks the cluster done

**Always, never only where a drop occurred.** Recording conditionally would make the absence of rows mean two things — no drop, or an older row set — and a reader of the ledger would have no way to tell which.

**Exemplar rows first, then the `synthesis_doc` row, and any prior exemplar rows under that key cleared first.** The doc row is what the generation loop reads to decide a cluster is already written (ADR-111, ADR-115), so it has to be the last thing that appears. An invocation that dies between the two leaves exemplar rows for a cluster the next invocation will write again, and clearing before inserting is what makes that self-healing instead of a duplicate-key stop.

### ADR-109's model-facing surface does not move, and that is the point

Nothing about the prompt, the notation, the check or the argument for an ordinal changes. A citation is still a number Vespera minted for one call; the space is still dense and small, so a fabricated one is still wrong by arithmetic; the check is still that the ordinal is in range, and nothing else.

One clause of the justification moves, and moves downward: an in-range ordinal now resolves **by record** rather than **by construction**. That is weaker prose and a stronger guarantee. "By construction" named an invariant no code asserted and two decided behaviours quietly falsified; "by record" names a row that either exists or does not.

### ADR-111, ADR-112 and ADR-121 were checked, and are untouched

- **ADR-111.** A faulted cluster writes no `synthesis_doc` row and so no `call_exemplar` rows either; the deliverable's hole has no writing, no citation and no numbering to serve. The repair pass writes both tables when the later answer is believed, which is what "record what the call sent" already means. The `cluster_fault` table, its four kinds, the streak breaker and the repair rule are all unaffected.
- **ADR-112.** The arrangement's order and its stored counts are what the tree is laid out by, and none of that moves: this reorders one list *inside* one cluster's page, which that record never spoke about, and the size the page discloses is still the stored one.
- **ADR-121.** A cluster no call could be made for earns no row in any of the four tables, which is that record's own decision restated with one more table in the list. Its observation that `documents_sent = 0` is unreachable carries over as an empty recorded list, which the same refusal makes unreachable for the same reason.

### The alternatives, and why each is refused

**Hand the model membership ordinals instead of per-call ones**, so `[n]` indexes the full cluster list, nothing can diverge and no table is needed; the check becomes set membership rather than a range. Refused on three counts. It changes what the model is shown, which is the most expensive surface in this slice to get wrong and the one ADR-109 reasoned hardest about — a sparse ordinal space over hundreds of members gives fabrication the room a dense `1..k` space denies it, and "wrong by inspection" becomes "wrong by lookup". It reinterprets prose already stored under a resumable run: a `[7]` written under per-call numbering and read back under membership numbering is a wrong link produced by the fix. And it does not remove the need for the record — an operator asking which documents a call was written from would still have nowhere to look.

**Build the call from unfiltered membership**, so nothing is ever dropped. Refused by ADR-108 and ADR-121, which decided both drops on their own merits, and nothing here disturbs either. A document nothing was chunked from has no text to send, and a document larger than the whole window cannot be sent at any budget.

**Amend ADR-109 to drop the "by construction" claim and admit the drift.** Refused, and stated rather than left unsaid. Fabrication-being-arithmetic is that record's whole argument, and it holds only because the ordinal reaches the document it was minted against. An ordinal that reaches a document nobody sent is not a weaker guarantee than an occurrence id, it is a worse one: the reader is shown a plausible source for a sentence that was not written from it, which is exactly the failure [ADR-026](0026-generated-content-verified-mechanically-and-by-human-review.md) and ADR-109 exist to refuse, arrived at by a different road. A record cannot document its way out of the one property it was written to provide.

**Remove the drops instead, by reading the opening chunk without touching the file.** The chunk cache is keyed by content hash and stage 1 does not hash every file — a file whose size is unique to it is never hashed at all ([ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md)) — so the hash is not universally available from the ledger, and "nothing was ever chunked from it" would survive even if it were. The drop can be narrowed; it cannot be abolished.

**Keep the count and add a delimited column of occurrence ids to `synthesis_doc`**, avoiding a table. Refused. SQLite has no array type, so it is a string this project would have to parse and keep ordered by convention; the ordinal is the thing being recorded, and a position in a delimiter-separated list is the weakest available way to record a number; and `walk_anomaly`, `unusable_seed` and `cluster_fault` all settled the house answer already — a repeating fact is a row.

**Keep the list in score order and place the citation anchors on whichever entries were sent**, so `[2]` links to `#document-2` sitting on the entry rendered as "3.". Refused. It puts two numberings on one page, visible side by side, which is the exact confusion ADR-109's one-numbering rule exists to prevent; a reader clicking `[2]` and landing on an entry headed "3." reads it as a broken link, and cannot tell a working one from a genuinely broken one. It also leaves the disclosure sentence with nothing true to say.

## Consequences

**`SynthesisSchema.VERSION` becomes 4**, and a stale database refuses ([ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)). Nothing else moves: `document_cluster` is still `embedding`'s, and the membership this reorders is read, not restated.

**`Exemplar` carries the occurrence it is.** The one place both the document and the ordinal are known is where the fill happens, so `ClusterSynthesis` hands back which documents it sent rather than how many, and `SynthesisDoc` carries that list. `documentsSent()` survives as its size, so every caller that only wanted the count is unchanged.

**A tree written over a 6b run from before this record cannot be renumbered.** Those runs have no exemplar rows, and nothing can recover which documents their calls carried. Such a tree would render as it did — every document, highest-scoring first, with the old disclosure — which is the behaviour this record calls wrong. Accepted without a migration: 6b has never been run outside tests, so the population of affected trees is empty, and writing a migration for rows that cannot be reconstructed would be theatre.

**An operator can now ask which documents a synthesis doc was written from, and get an answer.** That was not a goal, and it is the most useful thing here after the correctness. The question was previously unanswerable except by re-reading the archive and hoping it had not changed.

**The drops themselves are still silent in the artifact.** A reader sees that the writing was made from the first 40 of 412 and cannot tell whether the other 372 were too many for the window or unreachable on disk. Making the reason visible is a separate question about what a cluster file should disclose, and this record deliberately does not answer it: it fixes what the page *claims*, not what the page *omits*.

**`CONTEXT.md` needs no change, and that is worth recording.** Its **Citation** entry already reads *"an ordinal into the documents that call sent"* — the vocabulary stated the correspondence correctly the whole time. What drifted was the code and two sentences of ADR-108 and ADR-109. A glossary that was right while the records were wrong is an argument for reading it first.

**`exemplar` still means two things in this repository**, and this record does not fix it. [ADR-004](0004-relevance-defined-by-an-exemplar-seed-set.md)'s *exemplar seed set* is what the deliverable's prose calls a seed; ADR-108's *exemplar* is a document one call carried. The table is `call_exemplar` and not `exemplar` so that the schema says which of the two it holds. Collapsing the two is a vocabulary decision with its own blast radius, and it is not this one.

**This record was drafted twice and numbered three times.** A complete draft of it, numbered ADR-123, was written on 2026-09-18, left uncommitted in a worktree, and found while [#236](https://github.com/algernon28/vespera/issues/236) was being filed two days later; it is reproduced in that ticket's comments. The number moved from 123 to 130 to 133 as other work landed. What is above is that draft re-verified against `main` and changed in two places — the disclosure's second number, which #237 had settled in the meantime, and the missing-exemplar entry, which the draft made a run stop and which is a rendered entry here for the rollback reason stated above. The reproduced draft is not this decision; this file is.
