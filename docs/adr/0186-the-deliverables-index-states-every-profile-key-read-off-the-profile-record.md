# ADR-186 — The deliverable's index states every profile key, read off the profile record

- **Date**: 2026-10-04
- **Status**: accepted
- **Answers**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), what *"the profile values the run consumed"* in `index.md` covers, and how the list is kept from falling behind the profile.
- **Rests on**: [ADR-048](0048-walk-and-run-identity.md) (a run id folds in the configuration consumed and the upstream runs), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (a stage's implementation version is the last commit under its modules), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) (the record is the schema), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`synthesis` is handed plain values and never learns `Profile`), [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md) (an unreadable value is a third state), [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) and [ADR-185](0185-stage-2-asks-the-converter-again-under-a-run-of-its-own-when-extractionattempt-is-raised-and-nothing-is-discarded.md) (the two keys the index left out).
- **Settles** [#398](https://github.com/algernon28/vespera/issues/398).

## Context

ADR-103 says `index.md` opens with what produced the tree: the run id, the walk, the corpus root, "and the profile values the run consumed". `GenerationTasklet.profileValues` lists those values by hand, as eight `NamedValue` entries. The profile record has ten components. `logTimestampShareFloor` has been missing from the index since ADR-171 added it, and `extractionAttempt` since ADR-185 added it.

`DeliverableInvocationTest.EVERY_PROFILE_KEY` was a second hand-written list with the same eight keys. Its claim was that the index *contains* every key on that list. So the test passed with two keys missing: the code and the test fell behind in the same way, and nothing compared either of them with the record.

The ticket asks three things. What "consumed" covers. How the index shows `extractionAttempt`, whose value ADR-185 reads in three ways. And whether the list can be derived, so that the next key cannot be missed.

### What "the profile values the run consumed" has meant in practice

There are two possible readings. One is every key the profile record declares. The other is only the keys that some stage's recorded settings carry, which are the values that go into a run id.

The record and the shipped code already choose between them.

- **The shipped index has never used the narrower reading.** `arrangementApproved` has been on the index since #186. It is in no stage's recorded settings. It opens 6b's gate, and 6b's identity names the arrangement run it reads (`StageRuns`), not the text the operator wrote.
- **Every key is read by a stage on the run's upstream chain.** A 6b run's id folds in its upstream run, and that run's id folds in its own upstream run, back to stage 1 (ADR-048). So "what the run consumed" includes what every stage on that chain read. Today each key has a reader on that chain:

  | Key | Read by |
  |---|---|
  | `seedFolder` | census, the seed gate and seed measurement |
  | `logTimestampShareFloor` | stage 1's log rule (ADR-171) |
  | `degenerateOutputConfidenceFloor` | stage 2 |
  | `extractionAttempt` | stage 2's run identity (ADR-185) |
  | `boilerplateDocumentFrequencyFloor` | stage 4's gate |
  | `embeddingModel` | stage 5's gate and scoring run |
  | `relevanceScoreFloor` | stage 5's scoring run (ADR-117) |
  | `arrangementApproved` | 6b's gate |
  | `generationModel`, `generationContextWindow` | 6b |

- **The record holds only keys the code reads** (ADR-061: every key the current build's records define, added as the stage that needs it is built). A key that no stage read would be a defect in the record, not something for the index to leave out.
- **The method and the test already said "every key".** The javadoc on `profileValues` reads *"Every `Profile` key the run consumed"*. The test's constant was documented as *"Every key the profile carries"*. The list fell behind. The intent did not change.

The narrower reading would also lose the provenance ADR-103 wants. A reader holding only the tree could not tell whether the arrangement was approved, or which seed folder defined relevance.

### Where the list could be read from

| Source | What it gives | Why it is or is not the source |
|---|---|---|
| A hand-written list, in code | whatever was written | It is how this ticket happened. |
| Each stage's recorded settings (`run.config_consumed`) | the identity members of each stage on the chain | It leaves out `arrangementApproved`, which opens a gate and is a member of no stage's settings. By design it also leaves out `extractionAttempt` for the first attempt and for an ignored value (ADR-185 §1). The index would lose exactly the values a reader needs most. It also shows each value as read, not as written, so an unreadable floor would be shown as missing. |
| `Profile`'s record components | every key, in declaration order, named as `profile.yaml` names it | The record is the schema (ADR-061). A key cannot be added to the profile without being added here. |

## Decision

### 1. "The profile values the run consumed" is every key the `Profile` record declares

The index states one line for every component of `Profile`. It states them in the order the record declares them, and names each key as `profile.yaml` does. That is today's order with `logTimestampShareFloor` and `extractionAttempt` added last, in that order.

### 2. Each value is shown as the operator wrote it, `extractionAttempt` included

Every key follows the rule the index already uses: `- key: text`, where `text` is the value written in `profile.yaml`, and nothing follows the colon when the key is unset. For `extractionAttempt` that gives:

- **Unset**: `- extractionAttempt:` with nothing after it, like every other unset key. This is the first attempt.
- **`1`, `2`, `2.0`**: the text as written. `2` and `2.0` are the same attempt (ADR-185 §1), and the run id already says so. The index repeats the file, and does not normalise it.
- **An ignored value**, such as `1.5`, `0` or `two`: the text as written. The run read it as the first attempt.

**No reading is added beside a value.** An unreadable floor (ADR-120) has always been shown as written, and an ignored attempt is the same kind of value. To annotate one key, the pipeline would need its own reading of every key beside it, and that would be a second list, kept by hand, of the kind this record removes. The closing line already names an ignored attempt (ADR-185 §1). Whether the index should mark every unreadable or ignored value is not decided here. If it should, it should do it for every numeric key at once (below).

**One consequence is accepted.** Unset, `1` and an ignored value give the same stage-2 run id. So two trees written under one generation run id can show different text on the `extractionAttempt` line. ADR-103 claims the path is idempotent, and does not claim the bytes are. The same was already true of an unreadable floor and an unset one.

### 3. The list is derived from the record's components, in `pipeline`

`GenerationTasklet.profileValues` builds its `NamedValue`s from `Profile.class.getRecordComponents()`, in declaration order. The key is the component's name. The value is the accessor's `ProfileValue`, passed through the existing `textOf`. No key is named in the method.

- **It stays in `pipeline`.** `synthesis` still receives plain `NamedValue`s and never learns `Profile` (ADR-110). `profile` gains no method, so this decision moves no module but `pipeline`.
- **The component name is the YAML key.** `Profile` carries no Jackson renaming, so its components and the `profile.yaml` keys are one set of names. If a rename is ever wanted, it has to change both, and the tests in §4 then fail by name.
- **Every component is a `ProfileValue`.** That is already true (ADR-120). A component of another type would be a change to the record's design, and would fail loudly at the cast, not quietly on the page.

### 4. Tests compare with the record through one helper, and one count stays written out

The test tree gains `io.algernon.vespera.profile.ProfileKeys`, which reads the same components. `everyKey()` gives their names in order, and `valuesOf(Profile)` gives each key's value. Any test that claims something about "every key" gets the keys from it:

- `DeliverableInvocationTest` claims that the index's lines **are exactly** `everyKey()`, in order. This is an exact comparison, not a `contains`. A key added to the record and left off the index fails the test and is named.
- `ProfileStoreTest.everyAnswerLandsOnTheKeyThatCarriedIt` answers all ten keys, and claims through `valuesOf` that each one is answered. Its file had fallen to seven of ten while its javadoc said "every key". With the claim derived, a key missing from that file fails by name.
- `ClusteringInvocationTest` and `SeedCorpusComparisonInvocationTest` compare `profile.yaml`'s top-level keys with `everyKey()`, in place of their own copies. They already failed when a key was added. Now there are two fewer lists to update.

**Kept as written:**

- **`ProfileTest.EVERY_KEY`**, the parameter count of the one constructor (ADR-119). That claim is about the record's one way in. Derived from the components, it would compare the record with itself. It fails at once when a key is added, so it cannot fall behind silently.
- **The README's table of values**, which `docs/check-claims.mjs` compares with the record's components. That guard is in JavaScript and reads the source, and it already compares exactly. A Java helper cannot serve it, and it does not need one.
- **`NextAction`'s run-value list.** It is the values the closing line asks for in order, not every key, and it answers to a different decision.

## Consequences

- **The index gains two lines**, `logTimestampShareFloor` and `extractionAttempt`, and the next key the record gains is on the index with no change to `GenerationTasklet`.
- **Implementing this touches only `pipeline`**, so it moves the run ids of every stage whose implementation version names `pipeline` (ADR-058, `StageModules`): content census, content redundancy, seed measurement, embedding scoring, arrangement and generation. They each run again once, on the first build that ships it. The walk, stage 1 (byte-level reduction) and stage 2 (extraction) keep their ids, on one condition: the implementing commit touches nothing under `corpus`, `extraction` or `similarity`. This is what every `pipeline` commit costs. The arrangement is minted under a new id, so `arrangementApproved` no longer names it. The operator reads `arrangement.html` and approves the new arrangement once, as after any `pipeline` change.
- **A rename of a `Profile` component is now a rename on the page as well.** That is the intended result, since the page names keys as the file does.
- **One reflective read in production code.** It is a single call over a record's components, which the compiler already guarantees exist. A hand-written list in its place is the defect this record closes.

## Tests

- **`DeliverableInvocationTest.opensTheIndexWithWhatProducedTheTree`** gets every key from `ProfileKeys.everyKey()`. It claims that the index's key lines, the `- key:` lines in the provenance block other than `Run`, `Walk` and `Archive root`, are exactly those keys in record order. It claims that those lines, stripped, are exactly `- key:` followed by a space and the value `profile.yaml` holds for each key in `ProfileKeys.valuesOf`, or by nothing where the key is unset, so a value beside the wrong key fails too. It also claims that the unset `extractionAttempt` line has nothing after the colon. **Red until this is built**: the index lacks both keys.
- **`DeliverableInvocationTest.statesAnIgnoredAttemptAsWritten`**: with `extractionAttempt` written as `1.5`, the invocation completes and the index line reads `- extractionAttempt: 1.5`. **Red until this is built.**
- **`ProfileStoreTest.everyAnswerLandsOnTheKeyThatCarriedIt`**: the file answers all ten keys, and every key `ProfileKeys` reads off the record is answered. Green. It changes only the test file.
- **`ClusteringInvocationTest`, `SeedCorpusComparisonInvocationTest`**: their key lists come from `ProfileKeys.everyKey()`. Green.

## What this does not decide

- **Marking an unreadable or ignored value on the index.** If it is wanted, it covers every numeric key, and it needs one reading per key that the closing line and the index share.
- **Showing each value's provenance on the index.** `profile.yaml` carries one beside every value. The index has never stated provenance, and nothing here asks it to.
