# ADR-124 — Both fields of a parsed answer are required, and missing writing is a schema violation rather than an uncited one

- **Date**: 2026-09-19
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — its third check is where a schema is validated client-side, and that check now covers the parsed record's `prose` as well as the text it was read out of. Its four checks stand in the order it states them; nothing about what is sent, what the kinds are, or which check runs first moves.
- **Rests on**: [ADR-123](0123-an-answer-carrying-nothing-is-a-schema-violation-and-only-guaranteed-response-fields-are-dereferenced.md) (the response-level rule this extends one level down, and the record that named this hole as its one known exception), [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (failures under one kind are told apart by their detail, and uncited prose is a clause of its own), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a rejected answer faults its cluster and the run keeps going), [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) (the line between a call never made and a call that came back, and the first refusal of a fifth fault kind).
- **Charted as [#224](https://github.com/algernon28/vespera/issues/224)**, under the stage 6a/6b map [#175](https://github.com/algernon28/vespera/issues/175). Named as out of scope for [#222](https://github.com/algernon28/vespera/issues/222) by ADR-123, and confirmed by the architect review of [#223](https://github.com/algernon28/vespera/pull/223) as belonging to no existing ticket.

## Context

### The rule ADR-123 states has one hole under it, and this is it

ADR-123 settles that no check dereferences a response field nothing established is there, and closes every response-level case: a response carrying no generation, and a generation whose text is `null` or blank. It then names, rather than repairs, the one case that reaches the same failure through the **parsed record**.

`ANSWER_SCHEMA` names both `title` and `prose` in its `required` list, and `parseAnswer`'s own javadoc says that list is validated client-side, because Ollama pushes a schema down as a decoding constraint rather than checking conformance (ADR-108). For neither field is it true. An answer of `{"title":"x"}` parses: Jackson is content, since the key is absent rather than malformed, and reads it into an `Answer` whose `prose()` is `null`. `docFor` hands that straight to `checkCitations`, which does `CITATION.matcher(prose)`, and `Pattern.matcher(null)` throws.

### What that costs is the whole step's evidence, not the one cluster

The cost is ADR-123's and #216's, reached a third way. A `NullPointerException` is not a `ClusterFaultException`, so it escapes `GenerationTasklet` as a step failure; a step failure rolls back the step's transaction, taking with it every `cluster_fault` row written earlier in that step — the rows that exist to preserve exactly that evidence. One answer missing its `prose` therefore does not merely lose its own cluster: it destroys the record of every fault before it, which is the outcome ADR-111 exists to prevent.

### The state is reachable twice over, and the second way is already checked for

There are two ways for the writing to be missing, and today they do not land in the same place:

| What comes back | Today | Costs |
|---|---|---|
| `{"title":"x"}` | `prose()` is `null` → `Pattern.matcher(null)` throws | the step, and every fault row in it |
| `{"title":"x","prose":"   "}` | parses, reaches `checkCitations`, refused as `CITATION_NOT_IN_RANGE` with *no citation at all* | its own cluster |

Two answers that say the same thing — the model returned no writing — currently produce a destroyed step and a citation failure respectively, and which one an operator gets is decided by whether a serving engine omitted a key or emitted an empty string for it.

### Three tickets have now refused a fifth fault kind on the same ground

ADR-121 refused one for an unsendable cluster, #216 refuses one for a missing heading, and ADR-123 refused one for a response carrying nothing. `SCHEMA_VIOLATION` already names *what came back could not be read into the shape the call imposed*, and an answer missing a field that shape requires is squarely that. A fifth kind would change no behaviour and no remedy: the cluster keeps its hole, and a repair pass asks again, identically. This is the fourth refusal, and it is decided rather than inherited.

## Decision

### Both fields the schema requires are required of the parsed record, and a missing one is a `SCHEMA_VIOLATION`

An `Answer` is well formed when `title()` and `prose()` are each present and not blank. That is not a new requirement — it is `ANSWER_SCHEMA`'s own `required` list, read as the conformance check `parseAnswer`'s javadoc already claims to perform. `ClusterFaultKind` stays at four values.

**Absent and blank are one case, for `prose`.** ADR-123 folded a blank response text into an empty one at the response level, on the ground that both are the same thing to whoever reads the reason and only one wording says so. The same argument carries here and is adopted: `{"title":"x"}` and `{"title":"x","prose":"   "}` are one event — *the model returned no writing* — with one consequence, and they record one detail.

**That is not a general rule that guarded states share a detail**, and #216 is the counter-example rather than an inconsistency. Its two title cases are different events with different histories: an absent title raises a data-integrity exception against a `TEXT NOT NULL` column and costs the step, while a blank one is stored, renders as a hole forever, and is counted as written. An operator needs to know which of those happened. For `prose`, neither case leaves anything behind and neither is repairable differently, so telling them apart buys the operator nothing to act on. Distinctness of detail is owed where the cases are different events, not as a tax on every branch.

### The turn-down is at `parseAnswer`, not left to `checkCitations`

Missing writing is refused where the record is made, and never reaches the citation check.

**For the absent case there is no choice**: `checkCitations` cannot be reached at all without the dereference this record exists to remove. The decision is therefore only about the blank case, and it goes the same way for three reasons.

1. **One situation records one kind.** Leaving blank prose to `checkCitations` would have the two rows of the table above keep two different kinds for the same event, decided by a serving engine's serialisation. That is a distinction the operator cannot act on and did not cause.
2. **`CITATION_NOT_IN_RANGE` would say something untrue.** ADR-109's uncited clause is about prose that *reads as though it were written over named documents* with no thread back into the corpus — writing that rests on nothing. Blank prose is not writing that rests on nothing; it is the absence of writing. A row reading *no citation at all in the writing* tells the operator the model wrote something and failed to attribute it, when the model wrote nothing.
3. **It is where the evidence is complete.** ADR-108's order puts each check at the earliest point its own evidence is whole, which is what ADR-123 applied when it placed the response-level checks. The evidence that the writing is missing is complete the instant the record exists, one line after `readValue` and before any check reads a field of it.

### `title` is not settled here, and #216 keeps it whole

This record decides the **rule** for both fields: the required set is `title` and `prose`, presence is checked on the parsed record, and a missing one is a `SCHEMA_VIOLATION`. It does not build the `title` half.

#224 adds the `prose` guard; #216 adds the `title` guard, with the two distinct details its own criteria fix and the rest of its claims untouched — nothing of its scope moves here and nothing of it is now redundant. The scope line is stated once so it cannot drift: **#216 owns `title` entire, #224 owns `prose` entire.**

**Why not take both while the method is open.** #216 is an open ticket whose build is fully specified and whose remaining claims — that the cluster renders as a hole in the listing and is counted as one by the closing line — are about a state only the title case can produce. Implementing its guard from here would leave two tickets both claiming `title` and a record contradicting a live spec, which is worse than one method taking two commits. ADR-123 already fixed how that is done: whichever lands second **adds a branch to `parseAnswer` rather than rewriting it**, and that instruction is repeated here so the second implementer reads it wherever they look.

### What `parseAnswer` becomes

Its existing halves are untouched; one branch is added after them. The order within the method is: the response-level empty guard, `readValue` with its catch, then the record-level presence guard, then return.

- **The detail is `"the answer came back with no writing in it"`**, distinct from the three ADR-123 fixed. Under `SCHEMA_VIOLATION` an operator can now tell four things apart by the detail alone: a call that answered nothing, an answer with no text, an answer nothing could read, and an answer that read back fine and carried no writing. That is ADR-109's shape applied again, which is the third record to apply it.
- **It says nothing about the heading**, deliberately: until #216 lands, `title` may be `null` alongside it, and a detail reading *only a heading came back* would be a claim this record has not established.

### The rule, at the record level

ADR-123 states it for the response; this states it for the record, in the same terms:

> **No check downstream of `parseAnswer` dereferences a field of the parsed answer that nothing established is there.**

`parseAnswer` is the one place record-level presence is established, and the guarantee the rule rests on is that what it returns is an `Answer` whose required fields are non-null and non-blank by construction.

**That guarantee is stated as the target, and it arrives in two pieces.** This record decides it for both required fields; the branch that carries this record builds only the `prose` half, so the rule holds today for `prose` and holds for `title` when [#216](https://github.com/algernon28/vespera/issues/216) lands. Nothing here is true of `title` before then, and the section above says so in terms: until #216 lands, `title` may be `null` alongside it.

- **`prose`, today.** `checkCitations` may go on doing `CITATION.matcher(prose)` with no guard of its own **because `parseAnswer` established it and only because of that**, and `SynthesisDoc`'s prose argument in `docFor` is non-null on the same ground.
- **`title`, owed.** `SynthesisDoc`'s title argument is **not** covered yet. A `null` `title` still reaches `synthesis_doc.title`, which is `TEXT NOT NULL`, and still raises a data-integrity exception out of `GenerationTasklet` — costing the step and every `cluster_fault` row in it, which is the live defect #216 is open for. That is written down here rather than left implicit, because a record claiming a guarantee that does not hold is exactly what a later implementer reads and builds on. This bullet is spent when #216 lands, and #216's own record is what records it as spent: decisions here are append-only, so the rule holds whole by a later record amending this one, never by an eraser taken to it.

The rule has an edge, and it is stated rather than left implicit: a field added to `Answer` later either **joins the required set and is guarded here**, or is optional and every reader of it guards itself. There is no third position in which a reader assumes a field it did not check — and an unguarded required field is that third position being occupied by an outstanding ticket rather than by a decision.

## Consequences

- An answer parsing to a record whose `prose` is null, or blank, faults its cluster and the run continues. The `cluster_fault` rows written earlier in the step survive it, which is the whole of what this costs today.
- **The one exception ADR-123 named is closed.** That record's rule is about *dereferencing*, and the null `prose` handed to `Pattern.matcher` was the only known place a check dereferenced what nothing established was there. With this built, *no check dereferences what nothing established is there* is true of the response and of the record both. It is not a claim that every required field is now guaranteed present — `title` is not, and #216 is why.
- **A blank `prose` changes kind**, from `CITATION_NOT_IN_RANGE` to `SCHEMA_VIOLATION`. Nothing in the tree pins the old behaviour — the uncited-prose tests all script prose that is present and carries no markers — and no stored row changes shape, because a faulted cluster writes no `synthesis_doc` either way.
- **`ClusterFaultKind` still has four values**, for the fourth time and on the same ground.
- No `schema.sql` change and no `SynthesisSchema.VERSION` bump: the kinds and the row shape are untouched.
- **#216's scope is unambiguous afterwards**: it owns `title`, including both of its details and both of its reader-level claims. Its sentence about `ClusterFaultKind` staying at four is this record's too.
- **The tests pinning this are split across two levels, and deliberately.** What the fault is and what its detail reads are claimed against `docFor` directly, where that evidence lives; that the run carries on and that the fault rows written earlier in the step survive is claimed by an invocation test that writes an earlier fault row first, because a step-level guarantee cannot be read off a single call.
