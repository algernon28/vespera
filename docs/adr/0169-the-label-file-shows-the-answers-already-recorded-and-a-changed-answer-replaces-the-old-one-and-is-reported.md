# ADR-169 — The label file shows the answers already recorded, and a changed answer replaces the old one and is reported

- **Date**: 2026-09-28
- **Status**: accepted
- **Amends**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) — its "a YAML label file, one entry per sampled document **with the answer left blank**" becomes: one entry per sampled document, carrying the answer already recorded for it where there is one and blank where there is none. The file also names the seed set it was generated under, beside the run and the embedder identity, and a file naming a seed set other than the profile's is refused (§4). Everything else in that paragraph stands: a file offered against a different sample is refused rather than partially matched.
- **Rests on**: [ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md) (an answer is keyed by `(path, seed_set)`, and whoever joins answers to scores resolves the path into the walk it is reading), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) (the label file is written and read on the profile's YAML machinery), and ADR-088's own exemption from [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) (a second copy of a person's answer is a duplicate, not an observation, so a re-run never writes a second row for it). ADR-088 argued that exemption for duplicates only. §2 extends it to a correction, which is not a duplicate.
- **Settles** [#354](https://github.com/algernon28/vespera/issues/354).

## Context

The relevance report step writes `relevance-labels.yaml` on every invocation that reaches it, and `RelevanceLabelFile.render` writes `relevant: null` into every entry. So an operator who answers the file, runs `vespera label`, and then runs `vespera run` again finds the file rewritten with every answer blank. Their answers are safe in `relevance_label` and the labelling page counts them, but the one file they work in says nobody has answered anything.

The answers are already in hand where the file is written. The same step reads every answer given against the profile's seed set and resolves each path into the current walk (`answersInThisWalk`) to band them for the labelling page. It passes them to the page and not to the file.

What `vespera label` does with an answer that differs from the recorded one is decided by accident today. `RelevanceLabels.record` is an upsert on `(path, seed_set)`, so the last answer ingested replaces the earlier one and nothing says so. A blank entry is skipped and counted as a question still blank, so it never deletes a recorded answer.

Once the file shows recorded answers, two things that could not happen before become ordinary. An operator re-ingests a file they did not edit, and every answer in it is one the ledger already holds. Or they change one of the answers the file showed them. The first today is refused as "the label file has no answers in it yet", which stops being true. The second is a correction, and it replaces an answer silently.

The README already promises the rule this decision states: "Your answers outlive the run that asked."

## Decision

### 1. The label file shows every answer already recorded

When the relevance report step writes the label file, **an entry whose document already has an answer for the profile's seed set carries it** as `relevant: true` or `relevant: false`. Every other entry stays `relevant: null`.

The answer is the one keyed by the entry's path and the profile's seed set (ADR-097). The step already reads exactly those answers, resolved into this walk, for the labelling page. The file takes them from the same read, so every answer the file shows is one the page counts. The converse does not hold. The page counts answers per band over every scored survivor, and the file lists only the documents this run's sample asks about, so the page may count answers to documents the file does not show.

A document whose path this walk does not hold has no entry anyway, since the sample is drawn from this walk. So ADR-097's renamed document is asked about afresh, with a blank answer. The page does not count the old answer either, because the same read drops it.

The entry's score, band and closest seed are this run's, as before. The answer is the only thing carried over from an earlier invocation.

### 2. `vespera label` compares each answer with the one recorded

Before recording an answer, ingestion reads the answer recorded for the same `(path, seed_set)`. Each answered entry is then exactly one of:

- **new**: nothing was recorded for it. It is recorded.
- **unchanged**: the recorded answer is the same. It is recorded again. The answer does not change, and the context beside it (the run, the score shown, the embedder identity) takes the newer values, as `RelevanceLabels.record` already does.
- **changed**: the recorded answer is the other one. **The file's answer replaces the recorded one.** The file is the operator's statement, and editing an answer is how they correct one. The old answer is not kept anywhere in the ledger. ADR-088 exempted `relevance_label` from ADR-077 for duplicates: a second copy of a person's answer is not a second observation, so no second row is written for it. A changed answer is not a duplicate, and ADR-088 did not argue for it. This decision is the first to extend the exemption to a correction, which replaces the row in place.

**A blank entry never retracts a recorded answer.** Blank means "not answered in this file". It is counted among the questions still blank, as it is today, and the recorded answer stays as it was. Retracting an answer is not something `vespera label` does. No way to do it is decided here.

### 3. What `vespera label` reports

The command reports what it recorded on one line on stdout, before the closing line (ADR-098) that follows it. That line keeps its opening word, `recorded`, and states the three counts. When anything was new or changed:

> recorded 2 answer(s) about the seed set at `<seed set>`: 1 new, 1 changed, 0 unchanged; changed: `<path>` was true, now false; 1 question(s) are still blank

Each changed answer is named in the same way, `<path> was <old>, now <new>`, separated by commas. A correction that goes by unremarked is how a wrong answer looks the same as a right one.

When nothing was new and nothing changed:

> recorded nothing new about the seed set at `<seed set>`: 2 answer(s) unchanged, none new

The clause about blank questions is added exactly as it is today whenever any entry is blank.

This line is not the closing line. The closing line is ADR-098's line naming the next value. It still comes last, after this one, and this decision does not change it.

**A file every entry of which is blank is still refused**, with the message it is refused with today. After §1 that happens only when no sampled document has an answer the file can show, or when the operator has blanked every answer the file showed them. A document can have an answer the file cannot show: one renamed since it was answered (§1), or one walked from a different corpus root, where every path differs (see the last consequence below). Its answer stays recorded, and its entry is blank. In both cases "no answers in it yet" is true of the file, and by §2 nothing recorded is lost.

### 4. The file names its seed set, and a file about another seed set is refused

The answers the file shows are about the seed set the profile named when the file was written. The profile can name a different seed folder by the time `vespera label` reads the file, and ingestion takes the seed set from the profile. Without a check, every answer the file showed would then be recorded against the new seed set, reported as new, and nothing about it would look wrong.

So **the file names the seed set it was generated under**, as `generatedUnderSeedSet`, the canonical seed set the answers are keyed by (ADR-097). It sits beside `generatedUnderRun` and `generatedUnderEmbedder`.

**`vespera label` refuses a file whose seed set is not the one the profile names**, and records nothing from it. The refusal names both seed sets:

> vespera label recorded nothing: the label file was generated under the seed set at `<file's seed set>`, but the profile now names the seed set at `<profile's seed set>`; answers about one seed set are not answers about another, so nothing in it was recorded

It exits as the run mismatch does. The operator can put the seed folder back, or run again to get a file about the seed set the profile now names.

**A file that names no seed set is read as before**, against the seed set the profile names. Only a file written before this decision lacks the stamp, and such a file was written blank, so every answer in it was typed by the operator. That is the situation this decision started from, and refusing it would stop an operator who upgrades part-way through a file from recording it. A file naming no run is still refused, as ADR-088 decided.

This is the argument ADR-088 makes for the run stamp, applied to the other half of a label's key. Before this decision the file carried only answers the operator typed into it. It now carries answers the ledger recorded about one seed set, which is why it must say which.

## Alternatives rejected

- **Keep the file blank and add a note at the top saying answers are recorded elsewhere.** The file would still contradict the ledger. The operator would be invited to answer every question twice, and a second answer that differs from the first would replace it with nothing said.
- **Refuse a file whose answer differs from the recorded one.** A correction would then need some way around the file, and the file is the only way an answer gets in. The operator's latest statement is the one to believe. Saying that it replaced something is enough.
- **Read a blank entry as a retraction.** Before this decision, a blank was the only thing the file could show. An operator working through part of a file would delete answers they gave in an earlier sitting by leaving questions they had not got to yet.
- **Record prefilled answers under whatever seed set the profile names at `label` time.** Any answer the file showed would move to the new seed set without anyone giving it. That is the failure §4 refuses.
- **Keep the replaced answer as history.** Only this decision's report line would read it, and ADR-088 keeps labels out of the ledger's append-only rule on purpose.

## Consequences

**A re-run no longer costs the operator their view of what they answered.** Every answer the file shows is one the page counts, because both come from one read. The page may also count answers to documents the current sample does not ask about. A re-score under a new model, ADR-088's headline case, shows the old answers beside the new scores for every document the new sample asks about again.

**A re-ingested, unedited file succeeds.** It exits 0, records nothing new, and says so.

**A changed answer is visible once, on the line `label` prints, and nowhere else.** Nothing in the ledger records that an answer was ever different. That is the price of replacing the row in place (§2), and it is accepted.

**Changing the seed folder between `run` and `label` is refused** (§4). Before this decision it recorded the typed answers under the new seed set.

**`pipeline` changes.** `RelevanceLabelFile`, the relevance report step, `LabelFileReader` and `LabelIngestion` all change, so on a jar built from this change stages 3 to 6b mint new runs. Take it at a corpus boundary.

**Not decided here: labels across a different corpus root.** A label is keyed by the path relative to the walk's root. So answers given over one folder do not match the same documents walked from its parent, where every path gains a prefix. That is a question about ADR-097, noticed while triaging #354, and left open.
