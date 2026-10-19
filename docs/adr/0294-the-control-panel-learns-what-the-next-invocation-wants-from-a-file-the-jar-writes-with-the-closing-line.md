# ADR-294 — The control panel learns what the next invocation wants from a file the jar writes with the closing line

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the files that are the control panel's whole contract with the jar gain `next-invocation.properties` (§1). It carries a note at its head. The rest of §3 stands: the control panel still links no class of the jar.
- **Keeps**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md): the closing line is still worded in one place, `NextAction`, and still names values and not stages; the file names the same values, as keys (§2).
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §5, [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md) and [ADR-289](0289-a-plain-tasklet-step-that-fails-with-an-exception-ends-the-output-on-one-closing-line-of-its-own.md): the control panel shows the last line of output that is not blank as how the invocation ended, whatever the file holds (§4).
- **Keeps**: [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2: the arrangement the file names is the one this invocation arrived at, the one the closing line names (§2).
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md): the jar run directly writes the file too and needs no control panel to be driven; the operator may ignore it.
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) and [ADR-001](0001-tech-stack-is-a-fixed-constraint.md): the file is `java.util.Properties`, read and written by `java.base` alone, and no dependency is added.
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): the file is deleted only by an invocation holding the working-directory lock (§3).
- **Reads**: [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), [ADR-282](0282-an-occurrence-with-fewer-words-than-the-word-floor-is-degenerate-output-whatever-its-detected-format-and-stage-3-writes-the-page-the-floor-is-read-off.md) §3 and [ADR-287](0287-a-log-and-a-configuration-file-are-told-by-their-bytes-whatever-their-detected-format-and-a-configuration-file-is-kept-with-its-code-set-or-is-out-of-scope.md) §7: a key the closing line names only when it is unreadable is in the file only then (§2, §4).
- **Settles** [#565](https://github.com/algernon28/vespera/issues/565).

## Context

The **control panel** starts one invocation at a time as its child and shows how it ended (ADR-278). Between invocations the operator writes profile values, answers relevance labels and approves an arrangement; which of these the next invocation wants is computed by the jar's `NextAction` at the end of each invocation, from the profile, the answers recorded against the seed set, whether `relevance-labels.yaml` exists and the arrangement this invocation arrived at, and printed as the **closing line**, the last line of output that is not blank (ADR-281).

The control panel links no class of the jar (ADR-285 §3), and its whole contract with the jar is the process boundary and the files ADR-278 names. It cannot call `NextAction`.

The closing line is English for a person. `NextAction` words it in one place so that two invocations print the same sentence, and the wording is free to change: the line has been reworded with every key it learned to name. A program parsing it breaks at a rewording with no error, reading the wrong key or none.

`NextAction` names, of the keys the next `vespera run` stops for, `seedFolder`, `boilerplateDocumentFrequencyFloor` and `embeddingModel`, together and in that order, and otherwise `relevanceScoreFloor` or `arrangementApproved`; and of the keys that are no stop, only the first, in a fixed order, that holds something nothing can be read from: `relevanceScoreFloor`, `degenerateOutputConfidenceFloor`, `degenerateOutputWordFloor`, `logTimestampShareFloor`, `outOfScopeNameEndings`, `codeNameEndings`, the two lists where they share an ending, `configurationSettingShareFloor`, `translationSimilarityFloor`, `preferredLanguages` and `extractionAttempt`. A key that is no stop and is unset is not named. `degenerateOutputWordFloor` (ADR-282) and `configurationSettingShareFloor` (ADR-287) ship unset and are such keys.

The README walks five invocations: `vespera run`; `vespera run` after `boilerplateDocumentFrequencyFloor` and `embeddingModel`, the seed folder named before the first; `vespera label` after the answers; `vespera run` after `relevanceScoreFloor`; `vespera run` after `arrangementApproved`. ADR-098's title says four.

## Decision

### 1. The file

Every invocation that ends on `NextAction`'s closing line also writes **`next-invocation.properties`** in the working directory, beside `vespera.db`, from the same computed state: the branch of `NextAction` that words the line also gives the file's contents, so the two cannot disagree. It is written before the line is printed. The three places a closing line is printed write it: `vespera run` once its job has ended with a status that is not unsuccessful, gated steps included, `vespera label` after recording answers, and `vespera label --auto` after the local model's answers are recorded.

The format is `java.util.Properties`, written in UTF-8, read by the control panel with `java.base` alone. A comment line the writer adds is no part of the contract. The file holds four keys at most:

| Key | Value |
| --- | --- |
| `invocation` | `run`, `label` or `none`: the command the next act ends on, or `none` where nothing is left to set |
| `wants` | the profile keys the closing line asks to be written, as the profile names them, separated by one space, in the order the line names them; empty where the next act writes no key |
| `answer` | `true` where the next act is answering the questions in `relevance-labels.yaml` and recording them with `vespera label`, `false` otherwise |
| `arrangement` | the short name of the arrangement to approve, as the closing line quotes it; present only where `wants` holds `arrangementApproved` |

`invocation`, `wants` and `answer` are always written. `answer` is `true` only with `invocation=label`, and `none` comes with empty `wants`.

The file holds no path, no text of a document and nothing read from one: only `invocation`'s three words, profile keys, `true` or `false`, and an arrangement's short name. It names no seed folder, corpus root or deliverable tree, though the closing line may.

### 2. What each closing line writes

| The closing line's next act | `invocation` | `wants` | `answer` | `arrangement` |
| --- | --- | --- | --- | --- |
| write the run values still unset or unreadable, of `seedFolder`, `boilerplateDocumentFrequencyFloor`, `embeddingModel` | `run` | those keys, in that order | `false` | — |
| write a score into an unreadable `relevanceScoreFloor` | `run` | `relevanceScoreFloor` | `false` | — |
| write a readable value into the one unreadable key it names, of `degenerateOutputConfidenceFloor`, `degenerateOutputWordFloor`, `logTimestampShareFloor`, `outOfScopeNameEndings`, `codeNameEndings`, `configurationSettingShareFloor`, `translationSimilarityFloor`, `preferredLanguages`, `extractionAttempt` | `run` | that key | `false` | — |
| remove from one of the two lists each ending both hold | `run` | `outOfScopeNameEndings codeNameEndings` | `false` | — |
| fix what the gated line above names, with no questions written, or with nothing arranged | `run` | empty | `false` | — |
| answer the questions and record them with `vespera label` | `label` | empty | `true` | — |
| read a number off the labelling report into `relevanceScoreFloor` | `run` | `relevanceScoreFloor` | `false` | — |
| write the arrangement this invocation arrived at into `arrangementApproved`, unset or naming another | `run` | `arrangementApproved` | `false` | that arrangement's short name |
| `vespera label`'s line once `relevanceScoreFloor` is set: run `vespera run` | `run` | empty | `false` | — |
| nothing is left to set, with or without a deliverable | `none` | empty | `false` | — |

The `arrangement` is the one this invocation arrived at (ADR-154 §2), never the value `arrangementApproved` already holds.

### 3. When it is deleted

An invocation deletes any `next-invocation.properties` in its working directory as it starts, once it holds the working-directory lock and before it opens the database file. A file that cannot be deleted fails the start, as a lock file that cannot be opened does. An invocation refused the lock deletes nothing, since the file is the holder's.

So an invocation that ends without a closing line, refused or failed, with a non-zero exit code (ADR-278 §5, ADR-289), leaves no file, and the file in the working directory is always the last successful invocation's, or none. A file that cannot be written at the end is logged at WARN before the closing line, and the invocation ends as it would have: the closing line stays last (ADR-281) and the control panel finds no file.

### 4. What the control panel reads, and the one loop it walks

The control panel reads `next-invocation.properties` from the child's working directory only after the child it started ended with exit code 0, and only then. It shows the closing line as how the invocation ended whatever the file holds, or with none (ADR-278 §5, ADR-281). It parses the file itself, as it parses a holder line; a contract test, whose class path holds both, holds that its reading is what the jar writes.

It walks no fixed list of invocations. It walks one loop: after a child ends with 0, read the file; where there is none, or `invocation=none`, there is no next act to offer; otherwise collect what `wants`, `answer` and `arrangement` name, and offer `invocation` as the next command. Every start is the operator's act (ADR-278), and the closing line says, in words, what a `run` with empty `wants` is waiting on.

On the shipped profile with the seed folder named first, the loop is the README's five: `run`; `run` wanting `boilerplateDocumentFrequencyFloor embeddingModel`; `label` with `answer=true`; `run` wanting `relevanceScoreFloor`; `run` wanting `arrangementApproved` with its `arrangement`; then `none`. `degenerateOutputWordFloor` and `configurationSettingShareFloor` add no step: they ship unset, and appear in `wants` only when they hold something nothing can be read from. A file present but not in §1's shape, an `invocation` that is not one of its three words, an `answer` that is not `true` or `false`, an `arrangement` without `arrangementApproved` in `wants` or the reverse, `answer=true` without `label`, or `none` with keys wanted, is read as unreadable, and the control panel offers nothing from it.

### 5. What the profile ticket takes from this

The ticket that writes profile values, label answers and the approval from the control panel (#566) reads this file and nothing else to know which. It owes a way to write each key `wants` names, with provenance as the operator states it; a way to answer the questions in `relevance-labels.yaml` where `answer=true`; and, where `arrangement` is present, a way to write it into `arrangementApproved`. How each is shown and written is that ticket's.

## Alternatives refused

- **The control panel calling `NextAction`.** ADR-285 §3 refuses every class of the jar in the control panel, and `NextAction` needs the profile store, the ledger and Spring to compute.
- **The control panel parsing the closing line.** The line is worded for a person in one place and is free to be reworded; a rewording would break the control panel with no error.
- **A fixed list of invocations in the control panel.** It would hold a second copy of the order `NextAction` already computes, and miss every branch for an unreadable key.
- **The file left between invocations and overwritten at the end.** A refused or failed invocation would leave the earlier one's file beside a non-zero exit, and the next act read from it would be stale.

## Consequences

- The working directory holds one more file the jar writes, listed in the README beside the others.
- A rewording of the closing line changes no file and no control panel; a new branch of `NextAction` gives its contents in the change that adds it.
- No run id moves: the change is to `pipeline`, whose classes no stage's version names (ADR-226), and to `io.algernon.controlpanel`, outside every module.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.NextActionNamesTheNextInvocationTest` | for each branch of §2, the contents `NextAction` gives beside the line, whole: `invocation`, `wants` in order, `answer` and `arrangement`, and every key in `wants` named by the line in the same order |
| `pipeline.NextInvocationFileTest` | the file's name and place, its keys and values written in `java.util.Properties` form, `arrangement` only where given, no path though the closing line holds one, a contents that breaks §1's rules refused, a file that cannot be written left as it is with no exception, and deleting where there is none |
| `pipeline.AnInvocationStartsWithNoNextInvocationFileTest` | taking the working-directory lock deletes an earlier file; a take refused the lock leaves the holder's (§3) |
| `pipeline.TheNextInvocationFileIsWrittenWithTheClosingLineTest` | `vespera run` and `vespera label` ending with exit 0 write the file their closing line agrees with; a refused `vespera run` and a refused `vespera label` write none |
| `pipeline.TheControlPanelReadsTheNextInvocationTheJarWritesTest` | each shape the jar writes is read by the control panel's `NextInvocation` as the same invocation, keys in the same order, answer and arrangement (§4) |
| `controlpanel.NextInvocationTest` | the control panel's reading of the file: each of the three invocations, `wants` in order, `answer`, `arrangement`, an empty `wants`, a comment and any key order ignored, no file read as none, and each shape §4 calls unreadable refused |
| `controlpanel.TheControlPanelLinksNoClassOfTheJarTest` | unchanged, and holding `NextInvocation` to its own package and `java.base` |

## What this does not decide

- **How the control panel shows the next act, and the screens that collect each key, the answers and the approval** (#561, #566).
- **The values the control panel passes when it starts the jar** (#564).
- **Whether `vespera label --auto` is offered where `answer=true`**: the file says answers are wanted, not who gives them.
- **The count in ADR-098's title**: four there, five in the README; this record walks whatever `NextAction` computes and moves neither.
- **Whether an agent may read the file**: ADR-261's list of what an agent may read in a working directory is unchanged.
