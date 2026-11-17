# ADR-407 — Topics are named at the head of the arrangement step by a naming model of their own, from their strongest passages

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §5, §6 and §9 record it: that the local model names each topic in the deliverable language, the Docling title of its strongest contributing seed standing in where the model fails; that the names are generated before the 6a gate and shown there read-only; that temperature 0 and a seed are sent and both enter the run id; and that a name is stored under its run and never generated again. By the operator, in [#831](https://github.com/algernon28/vespera/issues/831): that the level between a topic and its clusters is named by the local model under the same rules (§4, §7, §8). By the operator, in [#829](https://github.com/algernon28/vespera/issues/829): that the names come from a setting of their own, `vespera.naming.model`, and not from the profile's `generationModel` (§2); that a change of `deliverableLanguage` mints another arrangement, approved again (§3); that a serving engine giving no answer at all fails the step, which the next invocation resumes (§6); that the word budget is 1,000 words (§4); that the control panel shows `vespera.naming.model` read-only, as ADR-334 shows the label model in use (§10); and that until #832 stores the names, the arrangement step still asks for them and logs each at INFO, a line a topic with its key, its name and its origin, storing nothing, the gate's showing of them going to #832 with their store (§9). Under the operator's delegation, in #829: that the seed's filename stem follows its title, as in ADR-106's chain, and that an answer refused or empty falls back and stops nothing (§6, §7); the step and run the names are written under (§1), the naming model's default and its blank value (§2), the arrangement run's members (§3), the rest of what is sent (§4), the question and its options (§5), the checks (§6), what the strongest contributing seed is (§7), whether names are distinct among topics (§8), and where and in what words the control panel shows the naming model (§10).
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §1, *"It joins the generation run's identity"* and *"stops the invocation where the generation run is minted"*: `deliverableLanguage` joins the arrangement run's identity too, and a value that is no language stops the invocation where the arrangement run is minted (§3). The generation run keeps its member. It carries a note at its head.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md), its *"nothing else pulls"*: the arrangement step pulls the naming model as the generation step pulls the generation model (§1). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose closed list of `effective-values.properties` gains `vespera.naming.model` (§10). It carries a note at its head.
- **Amends**: ADR-396 §12, its *"The walk, stages 1 to 4 and seed measurement do not move"*: a child whose code touches `extraction`, `similarity` or `embedding` moves the runs whose versions name them (ADR-058), and this record's moves every run but byte-level reduction's (Consequences). It carries a note at its head.
- **Keeps**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §2, the label model's line of `effective-values.properties` and its field on the services card, and ADR-330 §12, a key that is not the profile's and enters a run's identity being shown and not edited (§10).
- **Keeps**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s fill, its word budget counted at a pessimistic ratio and its checks on every response (§4, §6); [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md)'s cluster label, derived at 6a with no model, and its refusal to rename at the gate; [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md)'s `generationModel`, a value of 6b's alone (§2); [ADR-159](0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md)'s thinking off; ADR-321 §2, the instruction in English whatever the language; [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md) §4 and §5, the order passages are compared in and the topic key; ADR-396 §4, the top level never ordered by a name.
- **Rests on**: the measurements below, on public text and a local model only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).
- **Part of**: [#829](https://github.com/algernon28/vespera/issues/829), whose names are stored, and shown at the gate, by #832.

## Context

ADR-396 has the local model name each topic in the deliverable language before the 6a gate, a name stored under its run and generated once, temperature 0 and a seed sent, the Docling title of the topic's strongest contributing seed standing in where the model fails. ADR-399 finds the topics at the head of the relevance-scoring step, under the embedding-scoring run, each a list of seed passages keyed by its first. #831 puts the level between a topic and its clusters under the same rules. ADR-396 leaves to #829 the call, what is sent, what the strongest contributing seed is and where the step runs, and to #832 where a name is stored; and it leaves open whether the deliverable language enters a run before generation, which ADR-321 puts in the generation run's identity alone.

Three facts of the code bear on it. The arrangement run is minted over the scoring run, and an approval names it (ADR-107): an arrangement run minted again under another identity is approved again, unless `arrangementApproved` holds `auto` (ADR-317). `generationModel` is a value of the profile the operator may first give at the gate, beside the approval (ADR-107, ADR-114). And the call that writes a cluster sends no temperature, so that a cluster turned down can be asked again with a chance of another answer (ADR-321's alternatives); a name, asked at temperature 0, has no such second chance.

### Measured

A probe outside the repository, on public text only, with no seed set and no archive read.

- **Text and topics.** ADR-399's probe: Reuters-21578 articles, one article a passage, its Qwen3-Embedding-0.6B vectors, and the topics found by the shipped method at k = 10 and a resolution of 1.0 over five of its seed sets: 20 seeds over 7 subjects (8 topics), 50 seeds over 14 (10 topics), one seed of 8 subjects beside 4 seeds (4 topics), 20 seeds of `crude` (4 topics) and 60 of `acq` (8 topics), 34 topics in all. A topic's *subject* is the Reuters category most of its passages carry.
- **Model.** Qwen3-4B (Q4_K_XL GGUF) through a local Ollama 0.40.16 on four shared CPU cores, standing in for the shipped `qwen3:8b`, of the same family, which could not be loaded beside the other processes in the probe's 16 GB. Every call as §5 has it: the passages strongest first, the one-field schema, thinking off, temperature 0, seed 1, `num_ctx` 4096, `num_predict` 64, `num_keep` -1.
- **Read by hand**: whether a name says the topic's subject, or the story of one of its passages.

**The budget.** The 8 topics of 20 seeds, named in English at three budgets:

| Budget | Passages sent | Names telling one passage's story | Median seconds a call | Longest question |
| --- | --- | --- | --- | --- |
| 150 words | 1 | 4 of 8, as *Soviet Cotton Crop Estimates* | 3.7 | 306 tokens |
| 400 words | 2 to 3 | 2 of 8, as *India's Sugar Purchases*, and 1 naming a region | 9.6 | 1,186 tokens |
| **1,000 words** | 6 to 9 | 0 of 8, as *Cotton Supply and Demand by Country* | 19.9 | 2,231 tokens |

At 1,000 words over all 34 topics, 3 to 13 passages were sent, every answer finished of its own accord and in the schema, every name was 3 to 8 words, and every name was on its topic's subject; one of the 4 topics of `crude` was named for one story, *Ecuador-Venezuela Oil Aid Agreement*. Of seven topics holding a second subject at a quarter of their passages or more, four were named for both, as *Housing and Unemployment Statistics*. Questions came to 1.9 to 2.3 tokens a word.

**The language.** Asked in Italian 28 times, the model answered in Italian each time; one answer of 16 at 400 words repeated a phrase until it ran out of room, and none of 12 at 1,000. One Italian name carried an English word, *Duty sui succhi d'arancia dal Brasile*.

**The same name twice.** Every topic of 20 seeds, of `crude` and of `acq`, 20 in all, asked at 1,000 words and asked again after the model was unloaded and loaded again: the same name 20 times of 20. Asked again at once at 400 words, the engine reusing the first question's cached evaluation, the name differed in 3 of 30, a word or two added or dropped, as *EC Sugar Export Rebates* and *EC Sugar Export Rebates and Interventions*.

**Names side by side.** At 1,000 words, two topics of the same set were given the same name once in 34: two of the 8 topics of `acq`, *Corporate Acquisitions and Takeovers*. No other set gave two topics one name, though the 20 seeds over 7 subjects hold three topics of `sugar`. Each of the 16 topics of 20 seeds and of `acq`, asked again with its own name given as taken, answered another name in 15 cases, as *Corporate Divestitures and Asset Sales* for the second of the two; once it gave the taken name again.

**Not measured**: the shipped 8B model, and any model on a GPU; seed passages of long documents of many sections; names for the level between a topic and its clusters, which needs survivors the probe did not place; operator seed sets, which no agent reads.

## Decision

### 1. The names are written at the head of the arrangement step, under the arrangement run

**The arrangement step (6a) names the topics, and the parts of the level between a topic and its clusters, first**, before it orders, labels and records anything, and under its own run. No step is added; the job keeps its fifteen. Once #832 stores them, a name recorded under the arrangement run is read back by every invocation that arrives at that run, and never asked for again under it; where it is stored is #832's, and with it whether the names a step wrote before it was stopped are kept or discarded with the rest of that step's work (ADR-116). Until then each name is logged and none is stored (§9).

- **Not under the scoring run**, where the topics are found (ADR-399 §6). A name in the deliverable language under that run would move it with the language, and re-score, re-floor and re-cluster the corpus for a change of words; and the level between a topic and its clusters is formed after the floor, by the clustering step.
- **Not a step of its own**: a run of its own, upstream of the arrangement, would move the arrangement with it all the same.

**The arrangement step pulls the naming model** where Ollama does not serve it, once its gates are open and before its run is resolved, as ADR-329 has the generation step pull the generation model, and under the same check and the same switch. A model it cannot pull stops the step in one line naming the arrangement step and quoting Ollama, as the generation step's does, before any topic is named and with no run minted.

### 2. The naming model is a value of configuration of its own, the shipped generation default unless a machine names another

**`vespera.naming.model`** names the model, read as `OllamaRelevanceLabeller` reads `vespera.label.model`: where configuration names it, that; else `spring.ai.ollama.chat.options.model`, the shipped generation default, `qwen3:8b`. It is not the profile's `generationModel`, and has no profile key.

`generationModel` is a value of 6b's (ADR-114), and the operator may give it with the approval. Read before the gate, it would move the arrangement whenever it is first given or changed there, so that the approval would name an arrangement the next invocation no longer arrives at, and the gate would ask again over names of another model. A model of the arrangement's own keeps the gate's input to what the arrangement is made from. Unset, it is the model the generation step writes under unless the profile names another, so one model is pulled and served.

A blank value is no model: the invocation stops where the arrangement run is minted, naming `vespera.naming.model`, by a `NoNamingModelNamedException`, as `GenerationModel` stops on a blank default by a `NoGenerationModelNamedException`. So does a blank `spring.ai.ollama.chat.options.model` where `vespera.naming.model` is not named.

### 3. The arrangement run's identity names the call and the language

The arrangement run's configuration gains eight members after `standingRemovals`, each always written:

| Member | What |
| --- | --- |
| `namingModel` | the model's name as §2 resolves it |
| `namingModelDigest` | the digest Ollama reports for the weights under that name (ADR-090, ADR-108) |
| `namingContextWindow` | `num_ctx`, 4096 |
| `namingReplyAllowance` | `num_predict`, 64 |
| `namingNumKeep` | `num_keep`, -1 |
| `namingTemperature` | `temperature`, 0.0 |
| `namingSeed` | `seed`, 1 |
| `deliverableLanguage` | the code as read, in lower case (ADR-321 §1) |

The first seven are the options ADR-159 counts as the options sent, and the model's identity. `deliverableLanguage` joins the arrangement run's identity because the names it records are written in it; the generation run keeps its own member, which ADR-321 §1 gave it. **A value of `deliverableLanguage` that is no language stops the invocation where the arrangement run is minted**, before any call, with ADR-321 §1's line: the arrangement is the first run that reads it.

The same clusters with their names in the other language are another arrangement run, approved again before 6b runs, unless `arrangementApproved` holds `auto`. `generationModel` and `generationContextWindow` do not enter it.

### 4. What is sent: the strongest passages, whole, under 1,000 words

**A topic's passages go strongest first**: by the cosine of each passage's vector to the topic's centroid, the mean of its passages' vectors each taken to unit length, the vectors being those ADR-399 §1 found the topic over; two of equal cosine in ADR-399 §4's order. A passage's text is its chunk's, as the embedding step cut it (ADR-399 §1).

**Whole passages are sent, in that order, until the next would pass a budget of 1,000 words**, counted as `chunk_cache.word_count` counts them. At 150 and 400 words the names told one passage's story for 4 and 2 of 8 topics, at 1,000 for none, at twice the time of 400 (Measured). The fill is ADR-108's: a passage longer than the budget on its own passed over, the fill ending at the first passage that does not fit what is left. A chunk is at most 512 words (`ChunkingRule.DEFAULT`), so the strongest passage is always sent; a topic none of whose passages fits, which only a longer chunking rule could make, takes its fallback name, turned down as `NOTHING_SENT` (§6).

**A part of the level between a topic and its clusters** sends its survivors' leading chunks (ADR-108's exemplars), strongest first by the cosine of the vector #831 forms it over to the part's centroid, two of equal cosine in the order of their paths compared as ADR-399 §4 compares a seed's, under the same budget.

There is no counting call (ADR-166): 1,000 words at ADR-108's 2.0 tokens a word and the question's own words come to about half the window, the longest question measured was 2,231 tokens, and a question the engine cuts is turned down by §6's check.

### 5. The question and its options

One call to `/api/chat` a name, the question in English whatever the language (ADR-321 §2):

> Below are passages from documents on one subject.
>
> Passage 1:
> *its text*
>
> *…each passage sent, numbered from 1*
>
> Name the subject these passages share, in a few words, as the heading of a section of a knowledge base would name it. Answer with one JSON object of one field: "name", the heading. Write it in English.

`English` is the deliverable language's English name, `Italian` for `it`, and the two questions differ in that word alone. Where a name is asked for again (§8), *"Other sections are already named "A", "B"; name this one differently."* stands before *"Answer with"*, the names taken quoted in the order they were given.

The call imposes the schema `{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}` through `format`, asks for no thinking (ADR-159), and sends `num_ctx` 4096, `num_predict` 64, `num_keep` -1, `temperature` 0 and `seed` 1.

### 6. Every answer is checked, and one turned down falls back

The checks run in this order, as ADR-108's and ADR-111's do; the first that fails turns the answer down, and the topic takes its fallback name (§7). Nothing stops ([ADR-047](0047-the-pipeline-never-blocks.md)).

| Turn-down | When |
| --- | --- |
| `NOTHING_SENT` | no passage fits the budget (§4), so no call is made |
| `QUESTION_CUT` | `prompt_eval_count` at or above the window less one token, or the engine refuses the question as longer than the window |
| `RAN_OUT_OF_ROOM` | `done_reason` is `length` |
| `NOT_THE_SHAPE_ASKED` | the answer is not one JSON object with a string `name` |
| `EMPTY` | the name is blank once the blanks around it are removed |
| `NOT_ONE_SHORT_LINE` | the name, its blanks removed, holds a line break or more than 12 words |
| `SAME_AS_AN_EARLIER_NAME` | §8 |

**A field beside `name` is no turn-down**: only `name` is read, and an object holding another field as well is taken as its `name` says.

**The blanks around a name are removed, and nothing else**: no quotation mark, full stop or case is changed, as no answer is repaired (ADR-109).

**A serving engine that gives no answer** — refused for anything but the question's length, not reached, or not answering in time — **is no answer to turn down.** The failure reaches the step, which fails as the generation step fails when its engine stops answering, and no name is recorded for that topic, so the next invocation asks again under the same run, as ADR-047 resumes any step. A fallback recorded for a name that was never answered would stand for that run for good (§1).

### 7. The fallback

**A topic's strongest contributing seed is the seed feeding it the most passages**, two feeding as many taken in ADR-399 §4's order. Its fallback name is, the first that says something:

1. the seed's Docling title, read by its content hash (`DocumentTitles`, ADR-106);
2. its filename stem (`FilenameStem`, ADR-213 §4);
3. `Topic n` in English, `Argomento n` in Italian, `n` being the topic's place among the topics in key order, counted from 1.

A title and a stem are the seed's own words and are not translated, as a cluster's label is not (ADR-321 §5). Of seeds of one content, the one that feeds is the first in ADR-399 §4's order (ADR-399 §1), and its path and title are the ones read.

**A part of the level between a topic and its clusters** falls back to the title, then the stem, of its strongest survivor, the first §4 sends, then to its place within its topic in #831's order, under #831's word for the level.

### 8. Topics side by side have different names, where a second asking makes it so

**The topics are named in key order**, and the parts of the level between in their order within their topic. Two names are the same where they are equal once case is folded (`Locale.ROOT`) and every run of blanks is one space. Siblings are the topics, and the parts of one topic's level.

- **A written name the same as an earlier sibling's is asked for once more**, the question naming every earlier sibling's name (§5). The answer is checked as the first was (§6).
- **A second answer the same as an earlier name** is turned down as `SAME_AS_AN_EARLIER_NAME`, and the topic takes its fallback.
- **A fallback the same as an earlier name stands.** It is the seed's own title, which ADR-106 already lets two clusters' labels share; the top level is ordered by its key, never by a name (ADR-396 §4).

A name thus depends on its own passages, and on its earlier siblings' names only where it would have been the same as one of them.

### 9. Until #832, each name is logged and none is stored

**Until #832 stores the names, the arrangement step asks for them as §1 to §8 have it and logs each at INFO**, as it logs its other lines, one line a topic in key order once the topics are named:

> Stage 6a (arrangement) topic seed.txt chunk 0 named "Sugar exports" (written)

`seed.txt chunk 0` being the topic's key, the path in the seed folder and the chunk index of its first passage (ADR-399 §5); the name as given, between quotation marks; and its origin: `written`, `asked again` where the second asking of §8 wrote it, or `fallback` (§7). These words are this record's, so that a test can match the line whole.

**Nothing is stored and nothing reads them**: neither the gate, the arrangement page, nor 6b. A step that does the arrangement's work again, resumed after a failure or stopped and started again, asks for every name again, at one call a topic and two where a name is asked again. What the gate shows of a name, and whether it says where the name came from, is #832's (What this does not decide).

### 10. The control panel shows the naming model, and does not set it

**`effective-values.properties` names `vespera.naming.model`**, resolved as §2 resolves it: `vespera.naming.model` where configuration names it, else `spring.ai.ollama.chat.options.model`, each with the source of the layer that named it, else `qwen3:8b` with the source `code`. It is written always, as ADR-334 §2 writes the label model. A naming model bound blank is left out, its source with it, as ADR-330 §1 leaves out any value bound blank: the file never says an empty value, and does not say the chat model in place of a blank naming model, which no invocation would name (§2). A model's name is no secret and no path.

**The Services section shows it read-only**, beneath the label model's field: the heading *Naming model*, and beneath it `in-use-vespera.naming.model`, one line of selectable text, saying the value the working directory's file names and where it came from, in ADR-377 §4's words for a source: *"In use: qwen3:8b, from Vespera's built-in value."*, *"…, from this computer's environment."*, and so for each. A source ADR-377 §4 gives no words for ends the line at the value. Where the file is absent, or names no naming model, the line says ADR-334 §2's *"The jar has not said yet what it would use; run an invocation to see it."* It is read from the working directory chosen, again whenever another is chosen and whenever an invocation the window started ends.

**It is no launch value.** The Services section holds no field for it, the launch values' file keeps nothing of it and the child's environment is given nothing of it: the naming model enters the arrangement run's identity (§3), and ADR-330 §12 has such a key shown and not edited, since a launch value is passed to whatever corpus is started next and would mint an arrangement, to be approved again, on any start after it was typed. A machine names another naming model in its own configuration, by hand, as ADR-330 §8 has a machine name a preset. The label model keeps its field (ADR-334 §2, ADR-344 §3).

### 11. What the implementation owes

- In `embedding`: `Topic.passagesStrongestFirst(Map<String, SeedVectors> bySeedPath)` and `Topic.strongestContributingSeed()` (§4, §7).
- In `synthesis`: `TopicNaming`, holding `NAMING_WINDOW` 4096, `NAME_REPLY_ALLOWANCE` 64, `NAMING_SEED` 1, `WORD_BUDGET` 1000 and `LONGEST_NAME_IN_WORDS` 12, with `nameSiblings(List<NameCall> inOrder, String modelName, DeliverableLanguage language)` returning a `GivenName(String name, NameOrigin origin, Optional<NameTurnDown> turnedDown)` for each, `NameOrigin` being `WRITTEN`, `WRITTEN_WHEN_ASKED_AGAIN` or `FALLBACK`, and `whatFits(List<String>)`, ADR-108's fill over words; `NameCall(List<String> passagesStrongestFirst, String fallback)`; `NameTurnDown`, §6's seven; and `TopicFallbackName.of(Optional<String> doclingTitle, String seedPath, int place, DeliverableLanguage language)`.
- In `pipeline`: the naming model's resolution (§2), its pull (§1), the eight members of the arrangement run's configuration (§3), the stop on a language moved to where the arrangement is minted, and the call at the head of `ArrangementTasklet`, the passages' texts read from the chunk cache, and §9's INFO line for each topic; `NoNamingModelNamedException` (§2), listed with `PipelineHoldsOnlyTheRulesOnRecordTest`'s classes holding no rule; and `EffectiveValuesFile` writing `vespera.naming.model` (§10).
- In the control panel, `io.algernon.vespera.ui`: the Services section's heading *Naming model* and its line `in-use-vespera.naming.model`, a part of the section of its own after the label model's field, read from `EffectiveValues` again whenever the working directory chosen changes and whenever the window's own child ends; no member of `LaunchValues`, no field and nothing in the child's environment (§10).

`StageModules.ARRANGEMENT` already names `synthesis`, `extraction` and `embedding`, so no module joins its version.

## Alternatives refused

- **`generationModel` as the naming model.** It would move the arrangement whenever the operator names or changes the writing model at the gate (§2).
- **Asking all the topics' names in one call**, so that the model sees them side by side. One topic's name would depend on every other's passages, a seed added anywhere could rename every topic, and one bad answer would cost every name.
- **Distinct names by a number appended**, *Sugar (2)*: ADR-106 refused it for labels, and it tells a reader nothing of what differs.
- **Names never compared.** Two of the 8 topics of one subject were given one name, and side by side at the top level the reader could not tell them apart; a second asking named one of the two otherwise in 15 of 16 trials (Measured), at the cost of one call where it is needed.
- **The strongest contributing seed as the highest summed similarity of its passages.** It weighs a seed's passages much as counting them does, and puts floating-point sums where a count decides, which ADR-399 §5 refused for the key.
- **A repair pass asking a turned-down name again.** At temperature 0 and a fixed seed the same question asked afresh drew the same answer 20 times of 20 (Measured), as ADR-396 §7 says of the overview; the overview's second call differs from its first, and a name's would have nothing to differ by but §8's.
- **The window and the budget of 6b**, `generationContextWindow` and its fill to the window. A name read from a handful of passages needs no more (Measured), the profile's window would enter the arrangement, and a call per topic over a whole window costs minutes a topic on a machine without a GPU.

## Consequences

- **The arrangement needs Ollama and the naming model**, pulled where it is not served. A corpus is no longer arranged with nothing served, which ADR-396's Consequences already said.
- **A change of `deliverableLanguage`, or of the naming model's name or weights, mints a new arrangement**, approved again unless `arrangementApproved` holds `auto`. A change of `generationModel` does not.
- **A name is the model's choice of the passages' subject**, and may name a part of a topic for the whole (Measured).
- **The same question does not always draw the same name.** Asked afresh it did, 20 times of 20; asked again while the engine reused the first question's cached evaluation, it differed by a word or two in 3 of 30 (Measured). A name is therefore to be read back under its run and never asked again once #832 stores it (§1); a new arrangement run may name a topic a little otherwise.
- **Until #832, the names reach neither the gate nor the deliverable.** The operator reads them in the log, a line a topic (§9), and the arrangement step pays for them each time it does its work: one call a topic, and one more for each name asked again.
- **The operator sees the naming model in use in the Services section, and changes it only in a machine's own configuration**, by hand (§10). A change there mints a new arrangement, as above.
- **ADR-321, ADR-329, ADR-330 and ADR-396 carry a note *"Partly amended — see ADR-407"*** at their heads.
- **`CONTEXT.md`** gains **Topic name**, which says it is not yet stored, and **Deliverable language** says the arrangement run's identity names it too.
- **Run ids**: a stage's version is the last commit touching the modules `StageModules` names for it (ADR-058), and the code that builds this record touches `extraction` (`HybridChunker`, whose read of the chunk cache §4 needs), `embedding` (`Topic`, §4 and §7) and `synthesis` (§5 to §8). So these runs move: **extraction**, **content census** and **content redundancy**, whose versions name `extraction`; **seed measurement** and **embedding scoring**, whose versions name `embedding` and `extraction`; and the **arrangement** and **generation**, whose versions name all three. **Byte-level reduction**, whose version names `corpus` alone, does not move, nor does the walk, which has no version. ADR-396 §12's *"stages 1 to 4 and seed measurement do not move"* does not hold of this child.
- **What the operator pays for it**: stages 2 to 5 are done again from their caches. No conversion, chunk or vector is asked for again, the extraction cache, the chunk cache and the vectors being keyed by their instruments and not by a run, but every verdict, shingle, score and cluster of stages 2 to 5 is written again under the new runs, and stage 4b builds `shingle_by_hash` again for the new extraction run (ADR-221). The new arrangement is approved again, unless `arrangementApproved` holds `auto`, and 6b writes every cluster again under its new generation run.

## Tests

| Class | What it holds |
| --- | --- |
| `embedding.TopicStrengthTest.passagesGoByTheirCosineToTheCentroidOfTheTopic` | six passages of three seeds go by cosine to the centroid of their unit vectors, not by a dot product nor in visiting order, two alike in chunk order (§4) |
| `embedding.TopicStrengthTest.theStrongestContributingSeedHasTheMostPassages` | the seed feeding three passages is taken over the seeds holding the strongest and the first (§7) |
| `embedding.TopicStrengthTest.twoSeedsWithAsManyPassagesAreTakenInSeedPathOrder` | of two seeds feeding two each, the upper-case path is taken, by code point (§7) |
| `synthesis.TopicNamingTest.sendsTheMeasuredOptions` | the call names the model, a window of 4096, 64 tokens, `num_keep` -1, temperature 0, seed 1, thinking off and the one-field schema; the constants are the record's (§3, §4, §5, §6) |
| `synthesis.TopicNamingTest.thePassagesComeFirstAndTheLanguageIsNamedLast` | the passages in the order handed over, the question ending *"Write it in Italian."* or *"…English."*, differing in that word alone (§5) |
| `synthesis.TopicNamingTest.wholePassagesFillTheWordBudget` | of 1,001, 700, 400 and 100 words, only the 700 are sent (§4) |
| `synthesis.TopicNamingTest.aNameThatPassesIsKept` | the blanks around a name are removed and its full stop kept (§6) |
| `synthesis.TopicNamingTest.anAnswerTurnedDownFallsBack` | a cut question, an answer out of room, one not in the schema, an empty name, a name of 13 words and one of two lines each fall back, said why, nothing thrown (§6) |
| `synthesis.TopicNamingTest.aTopicWithNoPassageThatFitsIsAskedNothing` | passages of 1,001 and 1,002 words send nothing, make no call and fall back as `NOTHING_SENT` (§4, §6) |
| `synthesis.TopicNamingTest.aQuestionRefusedAsTooLongFallsBack` | the engine's refusal on length is a cut question (§6) |
| `synthesis.TopicNamingTest.noAnswerAtAllIsNoTurnDown` | a connection refused reaches the caller unchanged (§6) |
| `synthesis.TopicNamingTest.aNameTakenByAnEarlierSiblingIsAskedForOnceMore` | a name the first's but for case and blanks is asked for again, the question naming both earlier names in order (§8) |
| `synthesis.TopicNamingTest.aSecondTakenNameFallsBackAndATakenFallbackStands` | a second taken name falls back, a taken fallback stands, three calls made (§8) |
| `synthesis.TopicNamingTest.aNameTakenByAnEarlierSiblingsFallbackIsAskedForOnceMore` | a written name the earlier sibling's fallback but for case and blanks is asked for again, the question naming the fallback as taken, three calls made (§8) |
| `synthesis.TopicNamingTest.aFieldBesideTheNameIsIgnored` | an object holding `name` and a second field is kept as its `name` says (§6) |
| `synthesis.TopicFallbackNameTest` | the seed's title untranslated, then its stem, then *Topic 3* or *Argomento 3* (§7) |
| `pipeline.TheTopicsAreNamedAtTheArrangementStepInvocationTest.eachTopicIsNamedAndItsNameLogged` | one seed of one passage: one naming call, under the machine's `vespera.naming.model`, and one INFO line, *Stage 6a (arrangement) topic seed.txt chunk 0 named "…" (written)* (§1, §2, §9) |
| `pipeline.TheTopicsAreNamedAtTheArrangementStepInvocationTest.thePassagesAreReadFromTheChunkCache` | the chunk cache's text changed after every chunk was cut and embedded: the question sends the cache's words and none of the converted seed's (§4) |
| `pipeline.TheTopicsAreNamedAtTheArrangementStepInvocationTest.aNamingModelNotServedIsPulledAtTheArrangementStep` | the naming model, not served, is pulled once, after every embedding call and before any naming call, and the topic named under it (§1) |
| `pipeline.TheTopicsAreNamedAtTheArrangementStepInvocationTest.aPullThatFailsStopsTheArrangementStep` | a pull Ollama refuses stops the invocation in one line naming the arrangement step and quoting Ollama, the scoring done, no naming call and no arrangement run (§1) |
| `pipeline.ABlankNamingModelStopsTheInvocationTest` | `vespera.naming.model` bound blank: the invocation ends non-zero naming the key, the scoring done, no naming call and no arrangement run (§2) |
| `pipeline.TheDeliverableLanguageInvocationTest.aLanguageItCannotBeDrawnInStopsWhereTheArrangementIsMinted` | `deliverableLanguage` of `fr`: ADR-321 §1's line, the scoring done, no naming call, no group asked about and no arrangement run (§3) |
| `pipeline.RunIdentityGoldenTest.arrangement` | the arrangement run's settings end with the naming model `qwen3:8b`, its digest, 4096, 64, -1, 0.0, 1 and `en` (§2, §3) |
| `pipeline.TheEffectiveValuesNameTheNamingModelTest` | the file read as the control panel reads it: nothing named, `qwen3:8b` from `code`; the chat model alone, it with its source; its own key, it over the chat model; bound blank, left out with its source, the chat model not said in its place (§10) |
| `ui.TheNamingModelIsShownAndNotSetTest.theServicesSectionSaysTheNamingModelInUse` | the file naming it from the environment: the line *In use: a-naming-model:4b, from this computer's environment.*, selectable and not editable, in the Services section after the label model's field, and no field holding the property (§10) |
| `ui.TheNamingModelIsShownAndNotSetTest.withNoFileTheLineSaysTheJarHasNotSaidYet` | no file in the working directory: the line says the jar has not said yet (§10) |
| `ui.TheNamingModelIsShownAndNotSetTest.anotherWorkingDirectoryChosenIsReadAgain` | the line follows the working directory chosen: its own file's model and source, then *the jar has not said yet* for one with no file (§10) |
| `ui.TheNamingModelIsShownAndNotSetTest.theWindowsOwnChildEndedIsReadAgain` | the file written while the window's child runs: once the child ends, the line says what it names (§10) |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `NoNamingModelNamedException` is on record as holding no rule (§2) |

## What this does not decide

- **Where a name is stored, with its origin and turn-down**, whether a stopped step's names are kept, and the test that a name is read back and never asked again under its run: #832.
- **What the gate shows of the names**: the arrangement page showing each topic's name, and each part's of the level between, read-only (ADR-396 §5), and, where the model did not write it, that the name is its seed's and which turn-down of §6 put it there, in the operator's English (ADR-321 §4), with a test of its own: #832, which reads them from where it stores them.
- **How the level between a topic and its clusters forms, its order and its word**: #831; this record names its parts once they exist.
- **Whether `vespera run` checks the naming model at its start**, as [ADR-375](0375-a-model-whose-check-cannot-be-completed-is-taken-as-local-and-a-cloud-model-is-refused-at-the-start-of-vespera-run.md) §2 checks the embedding and generation models it names, and how [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §1's `modelsRunOn`, said of every model Vespera sends a document's text to, holds of it.
- **The names under the shipped 8B model**, which the probe could not load (Measured).
