# ADR-121 — A window with no room for documents is refused, and no call is ever made with none

- **Date**: 2026-09-16
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — its budget arithmetic is the thing that goes to zero and then below it, and that arithmetic now carries a lower bound and a refusal to call on an empty fill. Nothing else that record decided moves: one exemplar-first call per cluster, filled closest-to-the-seed first until the room runs out, is untouched.
- **Rests on**: [ADR-113](0113-no-corpus-wide-synthesis-doc-sits-above-the-seed-partitions.md) (every prose sentence in the deliverable traces to a document a human can open — the invariant a zero-document call breaks), [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (a citation is an ordinal into the exemplars one call sent), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a cluster that could not be written is the absence of a row, never a stopped run), [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md) (a profile key with a code default, where a written-but-wrong answer stops), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`pipeline` reads configuration and hands `synthesis` plain values), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (a step whose work is incomplete records no completion, and the next invocation carries on).
- **Found by the architect review of [#206](https://github.com/algernon28/vespera/pull/206)**, as the one of its six findings that no record answered. The others were test gaps and a contradiction with ADR-115; this one needed a decision.

## Context

### Measured: the budget reaches zero before the window looks small

ADR-108's room for documents, as implemented in `ClusterSynthesis.roomForDocumentsIn`, is the window less the reply allowance and the instruction reserve, converted at the pessimistic ratio and truncated to whole words:

```
room = (int) ((window − REPLY_ALLOWANCE − INSTRUCTION_RESERVE) / TOKENS_PER_WORD)
     = (int) ((window − 1024 − 256) / 2.0)
```

| `generationContextWindow` | Words of documents |
|---|---|
| 8192 (shipped) | 3456 |
| 2048 | 384 |
| 1300 | 10 |
| 1282 | 1 |
| **1281** | **0** |
| **1280** | **0** |
| 900 | −190 |

`GenerationContextWindow` refuses a value that is not a positive whole number and accepts every other one, so `900` and `1281` are both legal answers today. Under either, every document in the corpus is larger than the room, every cluster fills with nothing, and every call goes out carrying no documents at all.

### Two routes to the same call, and only one of them is about the window

**The window** produces it corpus-wide: at 1281 or below, no document of any size ever fits.

**The documents** produce it one cluster at a time, at any window. `whatFitsIn` passes over a document larger than the whole room — the case ADR-108 settled, because such a document can never be sent in any call and stopping on it would cost its cluster its writing entirely. A cluster whose documents are *all* that large therefore fills with nothing while its neighbours fill normally. Nothing about the operator's configuration is wrong in that case.

### What the call produces, and what it costs

The prompt reads *"Write one connected piece over a group of 0 document(s)"*, with nothing beneath the instruction to read. The model answers, because a model always answers. What comes back is stored as a `synthesis_doc` row carrying `documents_sent = 0`, and the deliverable renders it as prose over a cluster.

That prose traces to no file a reader can open. ADR-113 refused a corpus-wide synthesis doc precisely to keep *"every prose sentence in the deliverable traces to a document"* true of **the whole tree** rather than most of it, and said in terms that the value of the refusal is that there is no exception at the top. A zero-document call is the same hole arriving by the back door, one level down and without a decision behind it.

It also has no `k`. ADR-109 made a citation an ordinal into the exemplars one call sent, with the whole check being `1 ≤ n ≤ k`; at `k = 0` there is no marker the model could write that is in range, so the answer is either uncited prose or fabricated citation, whichever way it comes back.

And it is not free. One generation call is the most expensive thing this system does. A corpus of four hundred clusters under a mistyped window pays four hundred of them to produce four hundred pieces of writing about nothing.

### The option of leaving it to the response checks

[#183](https://github.com/algernon28/vespera/issues/183) will fault a cluster whose answer carries no citation, which would catch most of these after the fact. It is refused as the answer here on two grounds. It does not exist yet, so the behaviour would ship unguarded. And it is the wrong order even once it does: the call is paid for before the check runs, so the corpus is spent and every cluster faults. A call that cannot produce an acceptable answer should not be made.

## Decision

### The floor is read off the formula: a window is refused unless it leaves room for at least one word

`GenerationContextWindow` refuses any window for which `ClusterSynthesis.roomForDocumentsIn(window)` is not greater than zero.

**Not a restated constant, and the difference is not cosmetic.** The obvious reading of ADR-108's arithmetic is that the room runs out at `REPLY_ALLOWANCE + INSTRUCTION_RESERVE`, which is 1280 — and a floor written as `window <= 1280` **lets 1281 through**. In real arithmetic 1281 leaves half a word; truncated to whole words it leaves none, which is the very case this record exists to refuse. The sum of the two reserves is where the room reaches zero *before* the cast, and the code that decides what gets sent applies the cast.

A floor stated as a third constant would also drift. `TOKENS_PER_WORD` is a judgement about an unread corpus and either reserve may be revisited; each would silently move the point where the room runs out while the hardcoded 1280 stayed where it was. Reading the formula cannot drift, because it *is* the thing that decides.

**No minimum above zero.** A room of one word is useless in practice, and a floor of 2048 — enough for a few short documents — was the rival. It is refused because it is invented rather than derived: the document sizes it would be comfortable for belong to an archive nobody has read, ADR-108 contains no such number, and this project's rule for a number it cannot derive is that the number carries its reasoning in a named constant. A floor whose reasoning would read *"it felt roomy"* fails that rule. A floor that **is** derivable should be derived.

**1300 stays legal, with ten words of room, and that is correct.** A window that leaves room for one short document is a small window rather than an impossible one, and what it does with a cluster that does not fit it is already decided: the cluster sends what fits and says how much that was. Only the case where *nothing* can fit is refused.

### The refusal happens where the operator's value is read

In `GenerationContextWindow.size()`, beside the non-positive refusal and in the same shape: a stop naming the key and quoting what was written, with the shipped window offered as the way out.

**Not inside `synthesis`.** A refusal raised at call time stops the run partway through the corpus, after some clusters have been generated and paid for, over a condition that was knowable before the first call. It would also require `synthesis` to have an opinion about a configuration value, which ADR-110 does not allow it to read.

That is about *this* refusal — the operator's window, read once and knowable before anything is spent. A call whose fill came back empty is a different condition, knowable only where the fill is computed, and it **is** refused inside `synthesis`; the section below says so and says why the two sit in different places.

**It is a stop rather than a fallback**, for ADR-114's reason applied again: someone who wrote a number meant to change how much gets read, and quietly substituting the shipped window would run their archive under a window they did not choose and never mention it.

### `roomForDocumentsIn` and `nothingFitsIn` become public, and the empty call is refused in `synthesis` too

The formula lives in `synthesis` and the window refusal lives in `pipeline`, so reading the formula means widening `ClusterSynthesis.roomForDocumentsIn` from package-private to public. `ClusterSynthesis.nothingFitsIn` is widened in the same breath and for the same reason — it is what `GenerationTasklet` asks before it builds a call at all — and both are recorded here so nobody has to reconstruct why they moved. Naming only one of the two would leave the other looking like an accident.

It crosses nothing. `ClusterSynthesis` is already a public class publishing `CONTEXT_WINDOW` and `REPLY_ALLOWANCE`, and ADR-110 has `pipeline` reading configuration and handing `synthesis` plain values — a composition root naming a capability module is that rule working, not an exception to it. The dependency also points the safe way: `pipeline` learns what `synthesis` can accept, while `synthesis` learns nothing about the profile.

**And `docFor` refuses an empty fill itself, rather than trusting every caller to have asked first.** Where `whatFitsIn` comes back empty, no call is made: the method stops instead of returning, because there is no writing to return and a synthesis doc resting on no document is the very row this record exists to make unreachable.

**Why not the caller alone.** This record says flatly that no call is ever made with no documents. Enforced only in `GenerationTasklet`, that is a claim about today's single call site rather than a property of the system, and the record would be describing a convention that the next caller is free to break by forgetting. #185's repair pass is exactly such a caller and is being built next. Enforced in the module that owns the budget — the only place that can know the fill came out empty — it cannot be forgotten by anyone.

**The ordinary path never meets it.** `GenerationTasklet` goes on asking `nothingFitsIn` before it builds a call, and goes on taking the unsendable branch decided below, with its log line naming the cluster and the window and its count of clusters left unwritten. The refusal is the floor under a caller that forgot, not the mechanism a run passes through. A stop of this kind reaching an operator would mean a caller had skipped the question, which is a defect in that caller rather than a state an archive can be in.

### An empty fill never calls the model

Where the fill comes back empty — every exemplar larger than the room — no call is made and no row is written. That cluster takes the branch `GenerationTasklet` already has for a cluster it can offer no exemplars for: it is not counted as written, so the step is not recorded as finished, and the deliverable keeps the hole headed by the label 6a derived (ADR-106, ADR-111).

This is the same shape as the case beside it and deliberately not a new one. A cluster nothing could be sent for and a cluster nothing would fit are, to a reader of the deliverable, one situation: there is no writing over this group, and here is every document in it.

### No fifth fault kind

ADR-111's `cluster_fault` kinds are four things that can happen to **a call that came back** — the prompt-eval ceiling, the truncated answer, the schema violation, the citation failure. This is a call that was never made, and the table does not exist yet. Whether an unsendable cluster earns a kind of its own, and what its `detail` would carry, is [#183](https://github.com/algernon28/vespera/issues/183)'s to settle where that table is built. Nothing decided here is expressible only in a record that has not been written.

## Consequences

**A cluster whose every document exceeds the room keeps the step permanently unfinished.** Every invocation reaches it, writes nothing, and declines to record the step as complete; the next invocation does the same. This is accepted, and it is stated rather than mitigated because it is already exactly what an unsendable cluster does today. The permanent case — a hole that is known, recorded and finished with — needs the fault row, and is [#183](https://github.com/algernon28/vespera/issues/183) and [#184](https://github.com/algernon28/vespera/issues/184)'s.

**`documents_sent = 0` becomes unreachable.** A row carrying it is evidence of a defect rather than of a small window, which makes it worth asserting against.

**The smallest window this will accept is 1282**, and that number is nowhere in the code — it falls out of three constants and a cast, and moves when any of them does. That is the point of reading the formula rather than restating its result.

**The operator's mistyped window stops on the first invocation**, naming the key and the number, instead of costing a corpus of calls and producing a tree of prose about nothing. The stop arrives before the run row is inserted and before any call is made, so nothing has been spent when it does. It does not precede the arrangement gate, which resolves first — what it precedes is everything that costs anything.

**An unwritten cluster is what the next invocation picks up**, which is the skip half of [#185](https://github.com/algernon28/vespera/issues/185)'s repair pass — already required by ADR-115's exception for 6b, and buildable with no `cluster_fault` table. Only the fault half waits on that table, and nothing here adds to what #185 owes.

**ADR-113's invariant stays whole.** It was stated there as a property of the tree rather than of most of it, and this is the second place that property had to be defended rather than assumed.
