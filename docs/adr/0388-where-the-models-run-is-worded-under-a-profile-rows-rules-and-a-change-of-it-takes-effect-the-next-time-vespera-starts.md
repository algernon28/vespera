# ADR-388 — Where the models run is worded under a profile row's rules, and a change of it takes effect the next time Vespera starts

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Decided**: by Claude, under the operator's delegation of 2026-11-13 ("you decide"): `modelsRunOn`'s title, short description and tooltip (§1); its cost line (§2); the writing model's tooltip sentence ADR-384 made untrue (§3); and the site address's example in `confluenceBaseUrl`'s tooltip (§4).
- **Amends**: [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §7, whose texts are *Where the models run*, *…run on this machine…* and *…AI models that run on this machine…*: they are §1's; and whose cost line is that of a key a change of which redoes no work, ADR-377 §4's *…the next time you press Publish*: it is §2's. Its holder's tooltip, *Cloud is not supported yet.*, stands. It carries a note at its head.
- **Amends**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4: its cost-line table gains `modelsRunOn`'s row (§2); `generationModel`'s tooltip loses its sentence on a model ending in `cloud` (§3); and `confluenceBaseUrl`'s tooltip, as `profile-texts.json` holds it, gives `https://wiki.example.com` for §4's `https://your-company.atlassian.net` (§4). Its rules and its other texts stand. It carries a note at its head.
- **Keeps**: [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §1 to §6 and §8, and §7's control, holder and Save; [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4's word rule; [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md)'s ban on a Confluence Cloud domain in a shipped file outside the confluence module.

## Context

**ADR-384 §7 wrote `modelsRunOn`'s texts before ADR-377 §4's file held any.** Its title, *Where the models run*, and its short description and tooltip each say *run*, which ADR-377 §4's word rule forbids in every text of a row, a key's name aside; `TheProfileTextsTest.noTextNamesAStageAnInvocationAClusterOrAnEmbedding` fails on them.

**Its cost line is false.** ADR-384 §7 gives it the line of a key a change of which redoes no work, which ADR-377 §4 words for the five Confluence keys: *Changing it redoes no work; it takes effect the next time you press Publish.* `vespera publish` does not read the key (ADR-384 §3). It is read at the start of `vespera run` and of `vespera label --auto` alone, and *a value saved during an invocation is read at the start of the next one: no step reads the key* (ADR-384 §2). And the window never writes it: it is changed in `profile.yaml` alone (ADR-384 §7).

**A tooltip ADR-384 made untrue.** ADR-377 §4's `generationModel` tooltip says *Vespera does not use a model that sends text off this computer, such as one whose name ends in 'cloud'.* Since ADR-384 §1, no model is refused for its name or for what Ollama says of where it runs: a model whose tag ends in `cloud` is sent text where `modelsRunOn` is unset or `local`.

**The site address's example.** ADR-377 §4 gives `confluenceBaseUrl`'s tooltip the example `https://your-company.atlassian.net`. ADR-308 keeps a Confluence Cloud domain out of every shipped file outside the confluence module, which `NothingShippedReachesConfluenceTest` holds over `src/main`; `profile-texts.json` lies in `src/main/resources` beside the control panel, outside that module, so it holds `https://wiki.example.com`, which §4 does not record. No test or check reads `docs/adr` for the domain.

## Decision

### 1. Its texts

| Text | Words |
| --- | --- |
| Title | Where the AI models work |
| Short description | The AI models that read your documents work on this computer; using them in the cloud is not supported yet. |
| Tooltip | Says whether the AI models that read your documents work on this computer or in the cloud. Using them in the cloud is not supported yet, so this value cannot be changed here; profile.yaml holds it as a comment. Vespera takes every AI model it uses to work on this computer and does not check it, so name only models that do: one whose name ends in 'cloud' would be sent your documents' text. If profile.yaml is edited by hand to say cloud, or anything other than local, Vespera stops at the start of anything that would send your documents' text to an AI model, before reading a file or asking a model anything, and says why. |

*Work* in place of *run*, and *computer* as every other text of the tab says it. The tooltip says what ADR-384 makes true and the operator must know: that the value is not checked against the models, so a forwarded model is sent text (ADR-384 Consequences), and that `cloud`, or any other value, stops `vespera run` and `vespera label --auto` before the walk or the labeller (ADR-384 §2, §3). It names its key nowhere, and no row or report.

### 2. Its cost line

**Changing it in profile.yaml redoes no work; it takes effect the next time you start Vespera.**

It redoes no work: the key enters no run's identity (ADR-384 §1, §9). It takes effect at the next start, which reads it, and not at a Publish, which does not. *In profile.yaml* because the window does not write it. `ProfileRow.costOf(ProfileKey)` gives it to `modelsRunOn` alone; the Confluence keys keep theirs. No warning stands under it.

### 3. The writing model's tooltip

Its sentence *Vespera does not use a model that sends text off this computer, such as one whose name ends in 'cloud'.* becomes *Vespera does not check where the model works, which the "Where the AI models work" row says, so name one that works on this computer: one whose name ends in 'cloud' would be sent your documents' text.* Its other sentences stand.

### 4. The site address's example

`confluenceBaseUrl`'s tooltip, as `profile-texts.json` holds it, gives `https://wiki.example.com` where ADR-377 §4 gives `https://your-company.atlassian.net`, because ADR-308 keeps a Cloud site's domain out of every shipped file outside the confluence module. ADR-377 §4 keeps its words, as written when it was settled; the shipped text is this section's.

## Alternatives rejected

- **Keeping ADR-384's texts and admitting *run* for this key.** The rule holds every text of a row so that the window names no work by the project's words; one exception would be the first.
- **Keeping the Publish line.** It tells the operator a Publish applies a value no Publish reads.
- **A cost line naming `vespera run` and `vespera label --auto`.** The rule says *start Vespera*.
- **Editing ADR-384 §7 and ADR-377 §4 in place.** A settled record is reopened only by a later one.

## Consequences

- **`modelsRunOn`'s row reads *Where the AI models work***, its texts and cost line passing ADR-377 §4's rules.
- **The writing model's tooltip no longer says a cloud model is refused.**
- **ADR-384 and ADR-377 carry a note at their heads pointing here.**
- **No run id moves**: the texts are the control panel's, which no stage's version names.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.WhereTheAiModelsWorkReadsInPlainWordsTest.itsTitleShortDescriptionAndTooltipAreTheRecords` | §1: `ProfileKey.MODELS_RUN_ON`'s `title()`, `shortDescription()` and `tooltip()` are §1's, whole |
| `ui.WhereTheAiModelsWorkReadsInPlainWordsTest.aChangeRedoesNoWorkAndTakesEffectTheNextTimeVesperaIsStarted` | §2: `ProfileRow.costOf(MODELS_RUN_ON)` is §2's line, not `confluenceBaseUrl`'s, and `warningsOf` gives none |
| `ui.WhereTheAiModelsWorkReadsInPlainWordsTest.theWritingModelsTooltipSaysNothingChecksWhereAModelWorks` | §3: `generationModel`'s tooltip holds §3's new sentence and not the old |
| `ui.TheProfileTextsTest.noTextNamesAStageAnInvocationAClusterOrAnEmbedding` | §1, §2: unchanged; it fails on ADR-384 §7's texts and passes on these |

Amended:

- **`ui.TheCostLineTest.eachKeysCostLineIsTheLineOfItsEarliestRun`** gives `modelsRunOn` §2's line, the Confluence keys keeping theirs.

## What the implementation owes

Beside turning the tests above green: §1's three texts and §3's sentence in `profile-texts.json`; §2's line in `ProfileRow.costOf`; the notes at the heads of ADR-384 and ADR-377.
