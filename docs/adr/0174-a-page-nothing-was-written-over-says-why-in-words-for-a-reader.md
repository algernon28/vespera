# ADR-174 — A page nothing was written over says why, in words for a reader

- **Date**: 2026-10-02
- **Status**: accepted
- **Amends**: [ADR-161](0161-the-instruction-follows-the-documents-and-names-a-length-the-reply-allowance-holds.md), which left open in "What this record does not decide" whether the deliverable says why a group got no writing. It now does.
- **Rests on**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), whose reader has the tree and no database; [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), whose fault rows and repair pass this reads; [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md), whose unsendable clusters earn no row; and [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), which keeps our names out of that reader's prose.
- **Settles** [#325](https://github.com/algernon28/vespera/issues/325).

## Context

When 6b turns a cluster's answer down, the cluster keeps its slot: its index cell reads `*nothing was written over this group*` (ADR-111), and its own page carries the same line above its full membership list. Why it went unwritten is recorded in the `cluster_fault` row under the 6b run, in the step's log line, and in the invocation's closing count. None of those is in the deliverable, and the deliverable's reader has no database (ADR-103). So that reader cannot tell a group whose answer was turned down from one nothing could be sent for, or from one the step never reached, and cannot tell whether running again would help. The largest group of the first end-to-end run (#317) was one of these, and its page never said why.

There are seven ways a cluster ends an invocation with no synthesis doc:

- four kinds of turned-down answer, each a `ClusterFaultKind` with a row (ADR-108, ADR-109, ADR-111); the engine counting a cluster too long for the window before any answer is asked for is recorded as the first of them (ADR-166 §4a);
- two ways ADR-121 finds nothing to send, which earn no row: no member could be read and sent, or every member that could be read was judged too long for the window;
- and the step stopping after five turned-down answers in a row (ADR-111) before it reached the cluster, which also earns no row.

## Decision

### 1. The page says why

The cluster's own page is the only place the reason can reach a reader of the tree, so it says it. `Deliverable` is told the reason per cluster as a plain value, `Map<ClusterSlot, Unwritten>`, gathered by 6b; `synthesis` still reads no ledger table of a stage for it (ADR-110).

### 2. In one fixed sentence per case, in a reader's words

Each case has one sentence, in italics on a line of its own, written under the heading where the writing would stand. No sentence carries a name the code gives a reason (ADR-122), and a fault row's `detail` is never shown, since it carries token counts and check internals. Each says *group*, never *cluster*:

| Case | Sentence |
|---|---|
| `PROMPT_EVALUATION_CEILING` | *Nothing was written over this group: its documents came to more than the writing model was given room to read at once.* |
| `ANSWER_RAN_OUT_OF_ROOM` | *Nothing was written over this group: the writing model's answer reached its length limit before it was finished, so it was not used.* |
| `SCHEMA_VIOLATION` | *Nothing was written over this group: the writing model's answer did not come back in the shape it was asked for, so it was not used.* |
| `CITATION_NOT_IN_RANGE` | *Nothing was written over this group: the writing model's answer cited documents it had not been given, or cited none, so it was not used.* |
| No member could be sent (ADR-121) | *Nothing was written over this group: none of its documents could be read and sent to the writing model.* |
| Every readable member too long (ADR-121) | *Nothing was written over this group: each of its documents that could be read was judged longer than the writing model was given room to read at once, so none was sent.* |
| Not reached (ADR-111) | *Nothing has been written over this group yet: writing stopped before it reached this group. Running the same command again carries the writing on.* |

Three of these differ from the wording #325 proposed, and the record's wording is the one that holds:

- **"was given room to read" rather than "can read".** The limit is the reading window the operator set in `generationContextWindow`, not a property of the model, and the same model given a larger window reads more.
- **"each of its documents that could be read was judged longer … so none was sent".** A member that could not be read at all was not judged long; and the judgement is the estimate ADR-121 makes before any call, which is a judgement, not a measurement.
- **"carries the writing on" rather than "continues from here".** "Here" is a place on a page the reader may reach from anywhere in the tree.

The mapping from a fault kind to its sentence is an exhaustive `switch` with no `default`, in `Unwritten.of`, so a fifth kind of fault stops the build until this record gives it a sentence.

### 3. On the group's own page only

The sentence replaces `*nothing was written over this group*` on the page. Nothing else on the page changes: the heading, the membership list and the pictures stay as they were. The index cell keeps `*nothing was written over this group*` and still carries no link (ADR-112), and `documents.csv` is unchanged. A caller that gives no reason for a cluster gets the plain line, so a tree written without reasons is the tree this class wrote before.

### 4. Which reason a page gives

6b gives the reason in this order:

1. **What this invocation found wins.** If this invocation could send nothing for the cluster (ADR-121), that is why it is unwritten now, even where a row an earlier invocation kept is still standing; a page naming that old answer's reason would have the reader expect a re-run to help, and for this cluster it does not.
2. **Otherwise, a standing fault row**, under its kind's sentence. A repair invocation that did not reach the cluster still meets the earlier invocation's row, and the page says why that answer was turned down.
3. **Otherwise, not reached.** A cluster with no synthesis doc, no finding this invocation and no row was never asked about: the step stopped after five turned-down answers before it.

A cluster with a synthesis doc gets no sentence: it has its writing.

### 5. Every sentence holds across a repair

No sentence says *permanent*, *never* or *always*. Each describes what happened in this run. Every invocation that reaches the writing writes the tree again (ADR-111), so a cluster a later invocation repairs simply carries its writing, and no sentence on any page is left behind as false.

## Consequences

- **A reader of the tree can tell the seven cases apart** without a database, and can tell from the sentence whether running again would help: it would for a stopped step and may for a turned-down answer, and it would not for a cluster nothing could be sent for unless the archive or the window changes.
- **The index and `documents.csv` read exactly as before**, so nothing that reads them changes, and every link still resolves without a database (ADR-103).
- **`Deliverable` gains a seven-argument `writeTo`**; the five- and six-argument forms pass an empty map and write the tree they wrote before.
- **The sentences live in two places on purpose**: in `Unwritten`, which writes them, and in the test fixture `UnwrittenPage`, copied from this record. A test holds the one to the other, so the code is held to the record and not the other way round.

## Tests

- **`UnwrittenPageWordingTest`**: seven sentences, none shared; none says *permanent*, *never* or *always*; none names a `ClusterFaultKind` or says *cluster*; each is one line of emphasis with nothing a renderer reads as a link; and `Unwritten`'s sentence for every case is the record's, word for word.
- **`DeliverableTest.saysWhyOnTheUnwrittenGroupsPageAndNowhereElse`**, over every `Unwritten`: the page carries the sentence and not the plain line; with no reason, the page keeps the plain line; `index.md` and `documents.csv` are byte for byte the same either way.
- **`GenerationFaultInvocationTest`**: for each of the four fault kinds, the page carries its sentence, shows no kind name and no `detail`, keeps its membership, and the index row and the manifest are unchanged; a cluster the engine counts too long carries the first sentence; one with room for none of its documents carries the window sentence with no row recorded; and one an earlier invocation kept a row for, but which this invocation could send nothing for, carries what this invocation found.
- **`GenerationBreakerInvocationTest.saysOnEveryPageWhyNothingWasWrittenWhenTheStepStops`**: when five turned-down answers stop the step, each of those five pages carries its kind's sentence, and the cluster after them carries the not-reached sentence.
