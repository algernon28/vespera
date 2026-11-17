# ADR-411 — ADR-399, ADR-400 and ADR-401 move seed measurement, ADR-406's code is `TopicDivision` alone, and its floor is not shown in the control panel

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: by the operator, in [#850](https://github.com/algernon28/vespera/issues/850): that ADR-399, ADR-400 and ADR-401 each name every run their code moves, as [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) does; that ADR-406's two wording faults are put right; that the control panel's gap against [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §12 is recorded or opened as an issue of its own. By `docs/adr/README.md`'s rule that decisions are append-only: that a later record makes these corrections, the four records standing as written. Under the operator's delegation, in #850: the wording below, and that the gap is opened as [#855](https://github.com/algernon28/vespera/issues/855) and not built here (§3).
- **Amends**: [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md)'s Consequences, its *Run ids*, *"The walk, stages 1 to 4 and seed measurement do not move"*: seed measurement moves (§1); and its first Amends line, whose *"table of the members a run's configuration gains gains"* reads *"table of the members of a run's configuration gains"* (§4). It carries a note at its head.
- **Amends**: [ADR-400](0400-a-survivor-goes-to-the-topic-its-chunks-are-nearest-on-average-and-is-linked-from-one-15-per-cent-of-them-are-nearest.md)'s Consequences, its *Run ids*, the same sentence, and its Amends line, the same words (§1, §4). It carries a note at its head.
- **Amends**: [ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md)'s Consequences, its *Run ids*, the same sentence, and its Amends line, the same words (§1, §4). It carries a note at its head.
- **Amends**: [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md) §7, its *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* (§3); its Consequences, its *Run ids*, *"and taking the division back out of `Clustering`"* (§2); and its second Amends line, the same words as ADR-399's (§4). It carries a note at its head.
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)'s table, `StageModules` as it is; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) and [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version naming `pipeline`; [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §12 as ADR-407 amended it; ADR-330 §12, whose showing of a preset with no profile key stays owed (§3); and every other word of ADR-399, ADR-400, ADR-401 and ADR-406, the configuration each says its presets enter among them.
- **Settles**: [#850](https://github.com/algernon28/vespera/issues/850).

## Context

**A stage's version is the last commit touching the modules its row of ADR-058's table names**, and `StageModules` is that table: byte-level reduction names `corpus`; extraction, content census and content redundancy name `extraction` and `similarity`; seed measurement and embedding scoring name `embedding` and `extraction`; the arrangement and generation name `synthesis`, `extraction` and `embedding`. No stage names `pipeline` (ADR-222, ADR-226). The walk has no version.

**ADR-396 §12 says of the children that build it** that they move embedding scoring, the arrangement and generation, and that *"the walk, stages 1 to 4 and seed measurement do not move"*. ADR-407 amended that sentence: a child whose code touches `embedding` moves seed measurement as well, and each child says which runs its own code moves.

**ADR-399, ADR-400 and ADR-401 each repeat the sentence** in their *Run ids*, and each one's code touches `embedding`:

| Record | Its code in `embedding` | Its code elsewhere |
| --- | --- | --- |
| ADR-399 | `TopicInference`, `SeedPassage`, `SeedVectors`, `Topic`, `TopicKey` | `pipeline`: `Presets`, `EffectiveValuesFile`, `RelevanceScoringTasklet`, `StageRuns`; `application.yaml` |
| ADR-400 | `TopicPlacement`, `Placement`, `RelevanceScorer`, `RelevanceScoring` | `pipeline`: the same four classes; `application.yaml` |
| ADR-401 | `BreadthFloor`, `StandingTopics`, `Placement` | `pipeline`: the same four classes; `application.yaml` |
| ADR-406 | `TopicDivision` | `pipeline`: `Presets`, `EffectiveValuesFile`; `application.yaml` |

**ADR-406's *Run ids* already names the four runs**, but gives as its code *"`TopicDivision.java`, and taking the division back out of `Clustering`"*. `Clustering` is not touched by it: `Clustering.clusterAndRecord` clusters each seed partition as it did before [#831](https://github.com/algernon28/vespera/issues/831), as ADR-406 §7 itself says.

**ADR-406 §7 lists among what lands now** that *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"*. No class under `ui` shows `vespera.clustering.subtopic-floor`. The cockpit ([ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md), [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md)) reads `effective-values.properties`, through `EffectiveValues`, for the profile keys whose `ProfileKey.hasAValueBeneath()` holds, the two models, the generation window, the deliverable's language and the cluster resolution, and for the naming and label models in `Cockpit.modelInUse`; it holds no `machine-values`. So it shows none of ADR-330 §9's presets that have no profile key: not `vespera.clustering.neighbours` and the fourteen ADR-330 §12 lists after it, not ADR-399's `vespera.topics.neighbours` and `vespera.topics.resolution`, ADR-400's `vespera.topics.see-also-share` or ADR-401's `vespera.topics.breadth-floor`, and not ADR-406's floor. The gap is the cockpit's against ADR-330 §12, for every such preset, and not ADR-406's alone.

**ADR-399, ADR-400, ADR-401 and ADR-406 each write *"gains gains"*** in an Amends line naming ADR-330, where the noun phrase *"table of the members a run's configuration gains"* ends on the word its verb *"gains"* repeats.

## Decision

### 1. ADR-399, ADR-400 and ADR-401 move seed measurement too

**The code that builds each of ADR-399, ADR-400 and ADR-401 touches `embedding`, so it moves four runs**: **seed measurement** and **embedding scoring**, whose versions name `embedding` and `extraction`, and the **arrangement** and **generation**, whose versions name `synthesis`, `extraction` and `embedding` (ADR-058, `StageModules`). **Byte-level reduction**, **extraction**, **content census** and **content redundancy**, whose versions name no `embedding`, do not move, nor does the walk, which has no version. Its code in `pipeline` moves none, no stage's version naming `pipeline`.

Each record's *"The walk, stages 1 to 4 and seed measurement do not move"* reads *"The walk and stages 1 to 4 do not move"*, as ADR-407's note on ADR-396 §12 says of every child touching `embedding`. What each says of its configuration stands: at the shipped values no member is written, and ADR-399's and ADR-400's embedding-scoring configuration stays the text `RunIdentityGoldenTest` holds.

### 2. ADR-406's code touches `embedding` in `TopicDivision` alone

**ADR-406's *Run ids* reads: this record's code touches `embedding` in `TopicDivision` alone, `Clustering` unchanged (§7).** The four runs it names as moving with that code, and the four it names as not moving, stand.

### 3. The control panel does not show the subtopic floor, and the gap is decided on its own

**ADR-406 §7's *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* is struck from what lands.** No class shows the floor; it is in `application.yaml`, and on `EffectiveValuesFile`'s list in `effective-values.properties`, and nowhere in the control panel.

**What ADR-330 §12 owes stays owed**, for every preset with no profile key, the floor among them, and this record neither builds it nor amends §12. Whether the cockpit shows those presets read-only, and where, or a later record amends §12 to say it does not, is [#855](https://github.com/algernon28/vespera/issues/855)'s.

### 4. Errata

In the Amends line of ADR-399, ADR-400 and ADR-401 that names ADR-330, and in ADR-406's second Amends line, *"table of the members a run's configuration gains gains"* reads *"table of the members of a run's configuration gains"*, the noun phrase losing its last *"gains"* and the sentence keeping its verb. No meaning changes.

### 5. Where it lives, and run ids

**ADR-399, ADR-400, ADR-401 and ADR-406 each carry a note *"Partly amended — see ADR-411"*** at their heads, saying of which parts; their text is otherwise as written. **This record changes no code, so no run id moves with it.**

## Consequences

- **Seed measurement over the same seeds has a run id before the topics' code and another after it**, for work that comes out the same, as ADR-058 has of any commit touching a stage's modules. ADR-399, ADR-400 and ADR-401, read with their notes, now say so.
- **ADR-406 §7 claims no showing of the floor**: an operator learns its value from `application.yaml` or `effective-values.properties`, as of every preset with no profile key, until #855 is settled.
- **ADR-399, ADR-400, ADR-401 and ADR-406 carry a note *"Partly amended — see ADR-411"*** at their heads.
- **No run id moves** (§5).

## What this does not decide

- **Whether the cockpit shows the presets with no profile key, and where**, or ADR-330 §12 is amended to say it does not: [#855](https://github.com/algernon28/vespera/issues/855).
- **The index's lines for ADR-399, ADR-400 and ADR-401**, which name the embedding-scoring run as moving with their code; they stand, and this record's line names the rest.

## Tests

None of its own: it changes no code. `ADecisionRecordHoldsItsDecisionAndNoHistoryTest` and `AdrLinkTest` hold it as they hold every record.
