# ADR-120 — A profile value's type is its key's, and an unreadable value is a third state beside unset

- **Date**: 2026-09-15
- **Status**: accepted
- **Amends**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md), whose mechanism — a stray quoted null "fails to parse into a `Double` field and throws at load" — is replaced. Its purpose is kept and finally delivered: the format is not trusted, the deserializer is. What changes is that the bad value is refused *at its key*, as a named state, rather than by ending the invocation.
- **Rests on**: [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) (an unset key is a present key with nulls, and must stay distinguishable from a wrong one), [ADR-119](0119-profiles-constructor-overloads-are-deleted-and-the-convenience-they-bought-moves-to-a-test-fixture.md) (adding a key costs no constructor, and the record *is* the schema), [ADR-047](0047-the-pipeline-never-blocks.md) (an invocation ends having recorded what it learned), [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a shut gate is not an error), [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) (a run's identity has to agree with the step that reads it).

## Context

ADR-061 requires every profile value to be typed in the record it deserializes into — "`Double`, `Integer`, never `Object` or `String`" — so that the deserializer catches what a hand-edited file gets wrong. `ProfileValue.value` is a `String` for all seven keys, and the parse is deferred to whichever reader wants it. The requirement has never been met.

### Measured: five hand-rolled parses, three different behaviours

Counted in the tree at `d49e700`. Three of the seven keys are numeric; each is parsed at its point of use, and the points of use disagree:

| Reader | Key | A value no number can be read from |
| --- | --- | --- |
| `RelevanceFloor.stateFor` | `relevanceScoreFloor` | caught, reads as unset; the run scores, clusters, reports and removes nothing |
| `RelevanceScoreFloorValue.readFrom` | `relevanceScoreFloor` | caught, reads as null, deliberately matching the above (ADR-117) |
| `NextAction.isUnreadable` | `relevanceScoreFloor` | caught, produces a closing line quoting the text back at the operator |
| `RedundancyGate.floor` | `boilerplateDocumentFrequencyFloor` | **uncaught** — the exception ends the invocation mid-run |
| `ExtractionJobConfiguration.degenerateOutputConfidenceFloor` | `degenerateOutputConfidenceFloor` | **uncaught, at bean creation** — the application context never starts |

Issue #203 named the first two. The last two are the live defect: **the same typo in three numeric keys gives three different outcomes** — ignored with an explanation, killed mid-run, or never started. Two of the five also trim the text and three do not, so a value with a leading space is read by one key and fatal to another.

Nothing about which of these a key gets is recorded anywhere. It is a property of which reader was written first.

### What the tolerant reading is, and why it is not an oversight

`RelevanceFloor`'s catch carries its reasoning: the profile is a file a person edits by hand, ADR-047 says an invocation ends having recorded what it learned, and a typo in one key is not a reason to lose a whole scoring pass. `NextAction` then spends a whole branch telling the operator which value was ignored and what to write instead, on the ground that a value silently ignored is worse than one that was never set.

That is a considered position, and it is the opposite of ADR-061's. This record is where the two finally meet, and the tolerant one wins on the strength of ADR-047: the pipeline never blocks, and a mistyped threshold is exactly the sort of thing a person fixes between invocations.

### Measured: the sealed interface survives Jackson unchanged

ADR-119 established that Jackson 3 resolves a record's creator from its record components. A throwaway probe against this tree's own dependency set (`tools.jackson`, fail-on-unknown-properties enabled) checked the six things this decision rests on:

| Probed | Result |
| --- | --- |
| An ignore annotation on a **sealed interface's** default method | the derived question stays out of the written file |
| A record component declared as a concrete subtype | round-trips equal |
| A quoted null in a numeric key | reads as unreadable, carrying the text |
| A bare null in a numeric key | reads as unset, and the two are distinguishable |
| An unquoted number | still coerces into the string component and reads as a number |
| An unknown property under a subtype | still refused |

So the on-disk format does not change, and neither does the strictness that already works.

## Decision

### The key's type is part of the schema

`ProfileValue` becomes a sealed interface over two records, `NumericValue` and `TextValue`. Both carry the same three components in the same order — value, provenance, measurement — so **no file written by any prior version reads differently**, which is ADR-119's guarantee and is preserved here for the same reason.

`Profile` declares each component as the type its key actually has. Which keys are numeric stops being a convention each reader re-derives and becomes a fact the compiler holds, which is ADR-061's "the record *is* the schema" applied to the value as well as to the key set.

### A numeric key has three readings, and unreadable is one of them

`NumericValue` answers one question with a sealed three-state result:

- **Unset** — nobody has answered the key. ADR-062's state, unchanged.
- **Unreadable** — the operator wrote something no number can be read from. It carries the text, because the operator has to be told what it was.
- **Answered** — the number.

Unreadable is the state the tree has been expressing five different ways. Naming it is most of this decision: every reader now asks one question and gets one answer, and two readers of one key cannot disagree about it by construction rather than by a comment asking them not to.

The parse lives in `NumericValue` and nowhere else. It is computed on demand rather than stored, because a record has only its components and a fourth would be written into the file; one parse per call is not worth a schema change.

### Loading does not throw

This is the departure from ADR-061, stated plainly so it is not discovered later as a contradiction. A mistyped numeric value does not end the invocation: it reads as unreadable, the stage that wanted it behaves as though the key were unset, and the operator is told. ADR-061's null-vs-typo hazard is closed regardless — a quoted null can no longer be mistaken for an answer, which was the failure it named.

### An unreadable value is named in the closing line, for every numeric key

`NextAction` tells the operator about an unreadable relevance floor today. It now does so for any numeric key, on the rule it already states: a value the engine ignored is worth more to the operator than a value that was never set. Without this, the two keys that crash today would become silently ignored, which would be a regression dressed as a fix.

### Two behaviours change, and this is where they are recorded

- **`boilerplateDocumentFrequencyFloor`**: an unreadable value leaves stage 4's gate shut instead of ending the invocation. ADR-080 already says a shut gate is not an error.
- **`degenerateOutputConfidenceFloor`**: an unreadable value leaves the floor unset instead of preventing startup. Stage 2's tier-2 floor ships unset and the pipeline runs without it, so an unreadable one is the state the system is already built for.

Both are strictly less destructive than what they replace, and both were unrecorded accidents of a parse being called without a catch.

### The alternatives, and why each loses

**Uniform fail-at-load, as ADR-061 literally says.** Rejected. It would delete `RelevanceFloor`'s tolerant path and `NextAction`'s closing line, both of which carry recorded reasoning, and it makes a one-character typo in a key nothing has read yet end an invocation that ADR-047 says should end having recorded what it learned. It is also the harshest possible answer to a file the operator is *expected* to hand-edit between invocations.

**A generic `ProfileValue<T>`.** Rejected on measurement risk and on shape. It puts a type parameter where Jackson has to resolve it from a record component — the one thing the probe did not establish and did not need to — and the text keys would become a parameterisation that buys nothing, because text has no second state to distinguish.

**Leave the parse at each reader and merely make the five agree.** Rejected: that is the current design with a comment on it. Five agreeing parses are five things to keep agreeing, and the eighth key arrives with a sixth.

## Consequences

**A new numeric key gets the three readings for free**, and a key of a kind that is neither text nor a number is a new record implementing the sealed interface — visible, and refused by the compiler until it is written. The context-window key ADR-108 implies is the first to arrive under this rule.

**`RelevanceFloor` and `RelevanceScoreFloorValue` agree by construction**, which is what ADR-117 needs and currently gets from a comment in each asking the other to match.

**The trim is uniform.** Two of five readers trimmed; now one does, once.

**`ProfileFixture` can still express an invalid profile.** `NumericValue` accepts any string, exactly as `ProfileValue` did, so a test can still build the profile an operator actually mistyped — which #203 asked for and ADR-119 already protected.

**ADR-061's mechanism sentence is now wrong where it stands.** It is amended here rather than edited there, which is this project's append-only rule; a reader of ADR-061 arrives at this record through the amendment link.

## What this does not decide

**Whether an unreadable value should ever stop a run.** Every numeric key today is a threshold or a floor whose unset state is already legal. A future key whose absence genuinely cannot be tolerated would need its own answer, and this record does not pretend to have given it one.

**Whether provenance and measurement should be typed.** Both are free text by ADR-061 and ADR-053's rule, and nothing here touches that.

**Whether the context-window value is a profile key at all.** ADR-108 implies one and #196 owns it. This record only guarantees it costs no new parse.
