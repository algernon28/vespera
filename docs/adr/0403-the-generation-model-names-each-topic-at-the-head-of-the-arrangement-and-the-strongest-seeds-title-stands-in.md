# ADR-403 — The generation model names each topic at the head of the arrangement, and the strongest seed's title stands in

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §5, §6 and §9 record it: that the local model names each topic in the deliverable language before the 6a gate, which shows the names read-only; that temperature 0 and a seed are sent and enter the run id; that a name is stored under its run and never generated again; and that the Docling title of the topic's strongest contributing seed stands in where the model fails. By the operator, in [#829](https://github.com/algernon28/vespera/issues/829): that `generationModel` names the topics, and the level between a topic and its clusters, with no model setting of its own (§1); that the names are generated at the head of 6a, after clustering, under the arrangement run, whose identity the model, temperature 0, the seed and `deliverableLanguage` enter, so that a change of language runs 6a again and never stage 5 (§1, §2); that the strongest contributing seed is the seed feeding the most passages to the topic, ties going to the seed that comes first in the topics' passage order, and that where it has no Docling title its filename stem stands in, as ADR-106's chain has it (§7); that names need not be unique among topics, a topic's identity being its key (§8); and that an engine that does not answer, or a refusal that is not about the question's length, fails the arrangement step, *never stopping* holding only of an answer that cannot be used (§6). Under the operator's delegation, in #829: what is sent and in what order, under what word budget (§3), the call (§4), the question (§5), what an answer must be to be used (§6), what counts as an answer that cannot be, and what stands in past the title and the stem (§6, §7), that the seed is a code constant and no preset (§4), and what [#832](https://github.com/algernon28/vespera/issues/832) stores (§9).
- **Amends**: [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md), its Consequences' *"Changing the generation model does not spend the arrangement approval"*: the model now enters the arrangement run's identity, so a change of model, or of the weights served under its name, mints a new arrangement run, whose approval is given again unless `arrangementApproved` holds `auto`; and its two stops, a blanked default and a model the runtime does not serve, are met where the arrangement run is minted, before generation's (§2). It carries a note at its head.
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §1: `deliverableLanguage` joins the arrangement run's identity as well as generation's, and a value that names no language stops the invocation where the arrangement run is minted (§2). It carries a note at its head.
- **Amends**: ADR-396, its *What this does not decide* on whether the deliverable language enters the identity of a run before generation: it enters the arrangement's, and no run before it (§2). And §8's row for #829 is settled here. It carries a note at its head.
- **Keeps**: [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md)'s fallback chain and its *"Labels are not unique"*, read for a topic's strongest seed and its name (§7, §8), and a cluster's label derived by 6a with no model call; [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s explicit `num_ctx`, its word-counted budget and its three checks of an answer, and its generator identity, the model's name and the options sent; [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md)'s digest in that identity and `pipeline` handing `synthesis` plain values; [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md)'s refusal to repair an answer; [ADR-159](0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md)'s thinking asked off; [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md) §3's `num_keep`; ADR-321 §2, the question in English whatever the language, the language named last; [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md)'s passages, its order and its key; [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md)'s pull at the step that needs the model; [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md): a seed's text is read by the local model alone.
- **Settles**: #829.

## Context

ADR-396 has the local model name each topic in the deliverable language before the 6a gate, temperature 0 and a seed sent, the name stored under its run and never generated again, the Docling title of the strongest contributing seed standing in where the model fails; and it leaves to #829 the call, the prompt, what the strongest contributing seed is and where a name is stored. ADR-399 finds the topics at the head of the relevance-scoring step, 5d, from the seed vectors already resident, each topic keyed by its first passage, a passage being one stored seed chunk. The topics are the deliverable's top level, so their names are what a reader of the tree and of the Confluence rendering meets first, and what the operator reads at the gate.

Until now no model ran before the gate (ADR-106), and the arrangement run's identity named the corpus root, the scoring run and the removals standing under it, and nothing of a model or a language. The generation run's names the model, its digest, the options sent and the language (ADR-108, ADR-110, ADR-166, ADR-321).

Four things shape what is left. A topic holds tens to hundreds of passages, from one seed or many, more than one call should read to find a few words. A name is a short thing the model may answer badly in ways a long writing does not: a sentence, two lines, a word count that is a paragraph, a preamble before it. The operator's documents are Italian and English together, and nothing here can tell which language a piece of text is in (ADR-106). And a name stored under a finished run is never asked again, so whatever stands in for an answer stands for the life of that run.

**Not measured**: any answer of a generation model to the question of §5. How often a first answer is usable, how long the names run, whether they come in the language asked, and whether 1,500 words name a topic better than fewer, are read off the rows of §9 the first arrangement runs under this record write, each topic's name carrying whether the model wrote it and, if not, why. What is decided here rests on the window and budget arithmetic below and on the records it keeps.

## Decision

### 1. Where it runs, and which model

**The topics are named at the head of the arrangement step, stage 6a, under the arrangement run**: after stage 5's clustering, before the arrangement's first row is written, one call for each topic that the breadth floor lets stand, in the order of their keys (ADR-399 §5). A topic under the floor is no topic (ADR-396 §1) and is not named; a standing topic no survivor is placed under is named, since the report that lists it (ADR-396 §2) reads its name.

**The model is the generation model**, resolved as ADR-114 resolves it, `generationModel` in the profile over `spring.ai.ollama.chat.options.model`, and handed to `synthesis` as a plain name. No key names a model for naming alone. The arrangement step makes it ready as the generation step does before its first call: asked whether Ollama serves it, and pulled where it does not and pulling is on (ADR-329, ADR-384), before anything is sent.

**The level between a topic and its clusters** ([#831](https://github.com/algernon28/vespera/issues/831)) is named by the same model, under the same options (§4) and the same rule for an answer (§6), at the same place and under the same run. What a part of that level is sent, the wording of its question and what stands in where its answer cannot be used are #831's.

### 2. The arrangement run's identity

**The arrangement's configuration gains eight members, after `standingRemovals`, in this order**: `generationModel`, the name resolved; `weightsDigest`, what Ollama reports serving under it (ADR-110); `contextWindow`, `replyAllowance` and `numKeep`, the window, the answer's allowance and how much of a too-long question is kept, as sent (§4); `temperature` and `seed`, as sent; and `deliverableLanguage`, the code as read, in lower case. Each is written whatever its value. They are the generator identity ADR-108 composes for 6b, the model and the options sent, for the call this step now makes; and the language the names are asked in.

- **A change of model, of the weights served under its name, or of the language mints a new arrangement run** over the same scoring run. No run before the arrangement moves for it: a change of language runs 6a again and never stage 5. Generation follows the new arrangement, as it follows any.
- **The approval names an arrangement run** (ADR-107), so a new one is approved again before 6b runs, unless `arrangementApproved` holds `auto`. This amends ADR-114, by which a change of model minted a new generation run alone and the approval stood.
- **ADR-114's two stops are met where the arrangement run is minted**: a blanked application default, and a model the runtime does not serve, whose digest cannot be read. Each stops the invocation before any naming call, as it stopped generation's mint, which meets them again.
- **A `deliverableLanguage` that names no language stops the invocation where the arrangement run is minted**, in ADR-321 §1's words, before any call: the arrangement is now the first run that reads it.
- **The members are written from the change that first sends the call.** The options are the options actually sent (ADR-108, ADR-159); a run minted before any naming call is made sends none.

### 3. What is sent

**The topic's passages, nearest the topic's centroid first, filled in that order until 1,500 words are reached.**

- **The centroid** is the mean of the unit vectors of the topic's passages, as placement takes it (the centroid ADR-396 §8 names as placement's candidate); a passage's nearness is the cosine of its unit vector to that mean. Two passages of equal nearness are in ADR-399 §4's order. A passage of another topic, or of no topic, does not enter the mean.
- **The order is taken where the topics are found**, at the head of 5d, from the vectors already resident (ADR-399 §6), and stored with the topic's passages (§9). It costs no embedding call and no second read of a vector, and it is a fact of the topic as found, the same for every arrangement over that scoring run whatever the model or the language.
- **A passage is sent as the embedding step cached it**: its chunk's text and its word count, from `chunk_cache` under the scoring run's chunker and rule (ADR-108's count, a query and not a pass over text). Passages are sent bare, a blank line apart, with no number, path or title: nothing in a name is cited, and a seed's filename in the question would be read as a name to copy.
- **The fill is ADR-108's**: a passage longer than 1,500 words on its own is passed over and the fill goes on; the first passage that does not fit what is left ends it. A topic none of whose passages fits sends nothing, makes no call, and its strongest seed stands in (§7).

**Why nearest the centroid.** A topic's name is what its passages have in common, and the passages nearest the mean of all of them are the ones that share most of it. The visiting order would send the passages of the seed whose path sorts first; the strongest seed's passages first would name a topic of several seeds after one of them.

**Why 1,500 words.** A name is a few words over what the passages share, and three to seven passages of the chunker's size carry that; each word more is prompt evaluation paid for every topic of every arrangement run, for a gain nothing measured. And it keeps the question inside the window without a counting call (§4): 1,500 words and the question's own 80 come, at 5.1 tokens a word, to the 8,128 tokens the window leaves beside the answer, so a question is cut only where its words average more than 2.5 times ADR-108's pessimistic 2.0 tokens a word.

### 4. The call

**One call to `/api/chat` for each topic, its options**:

| Option | Value | Why |
| --- | --- | --- |
| `num_ctx` | 8,192 | sent, as ADR-108 has every call send it, so that the host does not decide; the shipped generation window, so that one loaded model serves both stages where the profile leaves the window unset. A code constant, not `generationContextWindow`, which is 6b's and would carry a 6b value into 6a's identity |
| `num_predict` | 64 | a name of 16 words, the most §6 uses, is 32 tokens at ADR-108's 2.0 a word, and the object around it a few more |
| `num_keep` | −1 | a question too long for the window is counted at the window's last token, where §6 sees it (ADR-166 §3) |
| `temperature` | 0 | the operator's |
| `seed` | 1 | 1 rather than 0, so that no reading of nought as unset drops it, as the topic overview's call sends it |
| thinking | off | ADR-159 |
| `format` | one object of one required string, `name` | the answer's one field, read client-side (ADR-108) |

**The seed is a code constant, not a preset** (ADR-330 §8). Its one task is to be the same every time; no value is better than another, so a machine naming another one would re-name every topic for nothing, and there is nothing to measure it against. It enters the run's identity as a member all the same (§2).

**No counting call** (ADR-166 §1). A cluster's call counts its question because it fills the window to the edge; a topic's question is budgeted to a fifth of it (§3), and a question the engine still counts at the ceiling, or refuses as past the window, is an answer that cannot be used (§6). Counting would cost a call for each topic to save a stand-in in a case the budget makes rare.

### 5. The question

In English whatever the deliverable language (ADR-321 §2), `N` being the passages sent and `LANGUAGE` the deliverable language's English name:

> Name a topic of an archive from N passage(s) of the documents it was found in.
>
> The passages follow below, the most typical of the topic first, a blank line apart.
>
> *each passage sent, in the order of §3, the passages a blank line apart*
>
> That is all N passage(s). Name what they have in common as a heading of at most 8 words for the topic: no sentence, no quotation marks and no full stop.
>
> Answer with one JSON object of one field: "name", the topic's name, on one line. Write it in LANGUAGE.

The paragraphs are a blank line apart, and nothing follows the last. The instruction follows what is read (ADR-161), and the last sentence names the field and the language (ADR-321 §3). The two languages' questions differ in that word alone.

### 6. What an answer must be to be used

**The answer is used where it reads as an object whose `name` is a string that, the blanks around it set aside, is not blank, holds no line break, and has at most 16 words**, a word being a run of characters none of which is blank.

- **A blank character** is one for which Java's `Character.isWhitespace` or `Character.isSpaceChar` holds: the ASCII blanks and line breaks, and every Unicode space, the no-break spaces U+00A0, U+2007 and U+202F among them. *The blanks around it* are the blank characters before its first character that is not blank and after its last; a name *is blank* where every character of it is blank, an empty one included.
- **A line break** is any of LF U+000A, VT U+000B, FF U+000C, CR U+000D, NEL U+0085, LINE SEPARATOR U+2028 and PARAGRAPH SEPARATOR U+2029, Unicode's mandatory breaks. The check reads the name with the blanks around it set aside, so a break before or after it is set aside with them and only a break inside it turns the answer down.
- **The checks run in this order**: blank, then a line break, then the words.

The name is that string with the blanks around it set aside, and nothing else of it is changed: no quotation mark or full stop is taken off, no word is cut, nothing is translated (ADR-109). The question asks for 8 words; 16 are used, since a name a few words longer than asked is still a name of the topic, where a seed's title is a name of one document; past 16 it is a sentence or a paragraph.

**Nothing checks the language.** Nothing here can tell which language a text is in (ADR-106), and a name in the other language is still the topic's name. A name that says it cannot name the topic is not told apart from one that names it (ADR-026, no model checking a model); the rows of §9 keep every name the model wrote.

**An answer is not used, and the strongest seed stands in (§7), for one of seven reasons**, recorded with the name:

| Reason | When |
| --- | --- |
| nothing fits | no passage fits the budget, and no call is made (§3) |
| past the window | the engine counts the question at or past the window's last token, or refuses it as longer than the window (ADR-108, ADR-166 §3) |
| ran out of room | the answer stops for its length, `done_reason: "length"` (ADR-108) |
| out of shape | no answer, an answer that is not an object, or an object with no string `name` (ADR-108, ADR-123) |
| blank | the name is blank |
| not one line | the name holds a line break |
| too many words | the name has more than 16 words |

**A call the engine does not answer about this question is not an answer, and stops the step**, as the operator decided in #829. A refusal for any reason but the question's length, or an engine that does not answer, reaches the step as it does from a cluster's call (ADR-108), and the step ends failed; nothing of its own work is kept (ADR-116), and the next invocation names the topics again. A seed's title standing in for an outage would stand for the life of the run (§9), for a fault of no topic's.

### 7. What stands in

**The topic's strongest contributing seed is the seed feeding the most of its passages**, ties going to the seed whose passages come first in ADR-399 §4's order, which is the seed whose path comes first by code point, every passage of one seed lying together in that order. A seed of the content of an earlier seed feeds no passage (ADR-399 §1) and is never the strongest.

**What stands in is the first of three that is not blank**, blank as §6 has it, as ADR-106's chain has it for a cluster's lead document: that seed's Docling title, read from its cached conversion; its filename stem (`FilenameStem`); and its path relative to the seed folder, as the seed walk stores it. The third replaces ADR-106's ordinal, since a topic's place in the top level is decided after it is named (ADR-396 §4) and a path is never blank. What stands in is never translated, as a cluster's label is not (ADR-321 §5).

**A name stood in for never stops anything** (ADR-047). The invocation log says so in one line, naming the topic's key, the reason and what stood in.

### 8. Names need not be unique

Two topics may carry one name, and both are shown as they are, as ADR-106 keeps cluster labels. A topic is identified by its key (ADR-399 §5), never by its name: the top level's order, its ties and every row keyed by a topic read the key.

### 9. What #832 stores

The tables and columns are #832's, as ADR-399 left the key's; what they must hold is this.

- **Under the embedding-scoring run**, with each topic's passages: each passage's place in the order of §3.
- **Under the arrangement run, for each standing topic**, keyed by the run and the topic's key: its name; whether the model wrote it or what stood in, the seed's title, its filename stem or its path; and, where the model's answer was not used, which of §6's seven reasons. The level between a topic and its clusters is stored likewise, keyed by its part's key, which is #831's and #832's.

**A name written under a finished arrangement run is read, never asked again** (ADR-396 §6). A step that ends unfinished keeps none of its own work (ADR-116), and the next invocation names those topics again under the same run.

### 10. Run ids

`embedding` changes, for the order of §3, and `StageModules` names it for seed measurement, embedding scoring, the arrangement and generation, whose versions move; `synthesis` changes, which the arrangement's and generation's name; and the arrangement's configuration gains §2's members. The walk and stages 1 to 4 do not move. At ADR-396 §12's word the children of #822 move the embedding-scoring stage, the arrangement and generation in any case.

### 11. What the implementation owes

**Built now**, as no table is needed:

- In `embedding`: `Topic.strongestContributingSeed()`, the seed path of §7; and `TopicNamingOrder.of(Topic topic, Map<String, SeedVectors> bySeedPath)`, the topic's passages in the order of §3, whatever order the topic holds them in.
- In `synthesis`: `NamingPassage(String text, int wordCount)`; `StrongestSeed(String seedPath, Optional<String> doclingTitle)`; `TopicNameCall(List<NamingPassage> passages, StrongestSeed strongestSeed)`, the passages in §3's order; `TopicNameQuestion.of(List<NamingPassage> sent, DeliverableLanguage language)`, §5's question; `TopicNaming(ChatModel)` and its `nameFor(TopicNameCall call, String modelName, DeliverableLanguage language)`, returning `TopicName(String value, TopicNameSource source, Optional<NameFallbackReason> fallbackReason)`, `TopicNameSource` being `GENERATION_MODEL`, `SEED_TITLE`, `SEED_FILENAME_STEM` or `SEED_PATH` and `NameFallbackReason` §6's seven, `NOTHING_FITS`, `PAST_THE_WINDOW`, `RAN_OUT_OF_ROOM`, `OUT_OF_SHAPE`, `BLANK`, `NOT_ONE_LINE` and `TOO_MANY_WORDS`; and the constants `TopicNaming.TEMPERATURE`, `SEED`, `CONTEXT_WINDOW`, `REPLY_ALLOWANCE`, `WORD_BUDGET` and `WORDS_ACCEPTED`. `num_keep` is `ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD`.

**Built with #832's tables**, since 6a can read the topics only once they are stored: the order of §3 written at 5d; the readiness of the model and the calls at the head of `ArrangementTasklet`, a seed's Docling title read as `DocumentTitle` reads a cluster's; §7's line in the invocation log for each name stood in for, naming the topic's key, the reason and what stood in; the members of §2 in the arrangement's configuration, and §2's three stops where the arrangement run is minted, before any naming call: a blanked application default, a model the runtime does not serve, and a `deliverableLanguage` that names no language; and the rows of §9. That change also carries what the members change in the tests: `RunIdentityGoldenTest.arrangement`'s text; the classes whose approval names a generation model after the first invocation, which name it before; `TheDeliverableLanguageInvocationTest`, whose language changed after the approval mints a new arrangement; and every reader of the questions a scripted model was sent, which now holds the naming questions too. `docs/running-stage-by-stage.md`'s table of what runs again, and every place that says which run `generationModel` or `deliverableLanguage` enters, name the arrangement with it.

## Alternatives refused

- **A model setting for naming alone.** The operator's: one model writes what the reader of the tree reads, and a second would be a second model to pull and to name.
- **The passages in the visiting order.** The first seed by path would name every topic it feeds, by the alphabet of its filenames.
- **The strongest seed's passages first.** A topic of five seeds would be named after one.
- **As many passages as the window holds**, about 3,900 words at 8,192 tokens: two and a half times the prompt evaluation for every topic, for nothing measured.
- **The profile's `generationContextWindow` as the naming window.** A value of 6b's would enter 6a's identity, and widening the window for the clusters would arrange again and ask for approval again.
- **Counting the question first, as a cluster's is.** One call more per topic, to save a stand-in the budget leaves for passages of more than 5.1 tokens a word.
- **An answer in plain text, with no shape imposed.** A preamble such as *"Topic name:"*, or the model's quotation marks, would be taken as the name; the schema keeps the field apart, as ADR-108's does the title.
- **Mending the answer**: taking off quotation marks or a full stop, or cutting it to 8 words. A cut name is not the name the model wrote, and ADR-109 repairs no answer.
- **Asking again on an unusable answer**, as a topic's overview is. At temperature 0 the same question draws the same answer, a second wording is a second thing to measure, and a seed's title is a name a reader can use.
- **Falling back where the engine does not answer.** The stand-in would be kept under the run for a fault of the engine's (§6).
- **The ordinal as the chain's last link.** A topic's place in the top level is decided after it is named, and moves where another topic gains or loses survivors; its path is the seed's own and never blank.
- **The seed as a preset.** No value of it is better than another, and nothing could measure one.

## Consequences

- **The arrangement needs the generation model.** An arrangement is minted only where it is served or can be pulled, and a corpus cannot be arranged again without Ollama, which ADR-396 already gave up for the gate.
- **A change of `generationModel` or of `deliverableLanguage`, or the same model pulled again over other weights, arranges again** over the same scoring run, with the same topics and clusters, and asks for the arrangement to be approved again, unless `arrangementApproved` holds `auto`.
- **Every standing topic costs one call at 6a**, of at most about 3,200 tokens: ADR-399 measured 3 to 20 topics a seed set.
- **A topic is never without a name at the gate**: a seed's title, filename stem or path stands in, and the row says which and why, so that how often the model's names are used is a count anyone can take.
- **Two topics may share a name**, and the gate and the deliverable show both.
- **ADR-114, ADR-321 and ADR-396 carry a note *"Partly amended — see ADR-403"*** at their heads.
- **`CONTEXT.md`** gains **Topic name**.

## Tests

| Class | What it holds |
| --- | --- |
| `synthesis.TopicNameQuestionTest.isTheRecordsWordingWordForWord` | two passages, Italian: the question is §5's, word for word (§5) |
| `…namesTheLanguageLastAndDiffersInItAlone` | the English and the Italian questions differ in the language's name alone, which is the last word (§5) |
| `synthesis.TopicNamingTest.asksAtTemperatureZeroWithSeedOneUnderAShapeOfOneField` | the call names the model, asks for no thinking, and sends a window of 8,192, an allowance of 64, `num_keep` −1, temperature 0, seed 1 and a schema requiring `name` alone; the constants are those (§4) |
| `…sendsThePassagesInTheOrderGivenUntilTheBudgetRunsOut` | a passage of 1,600 words is passed over, two of 700 are sent, one of 200 ends the fill and one of 50 after it is not sent (§3) |
| `…sendsPassagesThatComeToExactlyTheBudget` | passages of 700, 700 and 100 words are all sent, and one of exactly 1,500 words is sent (§3) |
| `…aTopicNoPassageOfWhichFitsAsksNothing` | every passage over the budget: no call, and the seed's title stands in for nothing fitting (§3, §6) |
| `…keepsAUsableNameAsWrittenBarTheBlanksAroundIt` | a name with spaces, no-break spaces or line breaks around it is used without them, as the generation model's (§6) |
| `…usesSixteenWordsAndNotSeventeen` | 16 words are used; 17 are too many, and the seed's title stands in (§6) |
| `…anAnswerItCannotUseIsStoodInForByTheStrongestSeedsTitle` | a blank name, a name of no-break spaces alone, a name broken inside by each of the seven line breaks, an answer that is no object, an object with no `name`, a response carrying no answer, an answer stopped for its length, a question counted at the window's last token, and one refused as past the window in the runner's words and in Ollama's: each stood in for by the title, with its reason (§6, §7) |
| `…withNoTitleTheFilenameStemAndThenThePathStandIn` | no title, a blank one or one of no-break spaces alone: the filename stem; a stem that is blank: the seed's path (§7) |
| `…aRefusalNotAboutTheQuestionsLengthReachesTheCaller` | a model not found, and an engine that does not answer, are thrown to the caller, and nothing stands in (§6) |
| `embedding.TopicNamingOrderTest.sendsThePassagesNearestTheTopicsCentroidFirst` | four passages handed in reverse, one of them five times as long as the rest, and a passage of another topic far off: nearest the mean of the unit vectors first, the other topic's passage counting for nothing (§3) |
| `…aTieGoesToThePassageFirstInTheVisitingOrder` | two passages as near as each other: U+FF5E's seed before U+1F4C4's, by code point (§3) |
| `embedding.TopicStrongestSeedTest.theSeedFeedingTheMostPassagesIsTheStrongest` | the seed of three passages, not the seed of the key's passage (§7) |
| `…aTieGoesToTheSeedWhosePathComesFirstByCodePoint` | two seeds of two passages each: U+FF5E's before U+1F4C4's (§7) |
| `TheVocabularyNamesTheTopicNameTest.topicNameIsATerm` | `CONTEXT.md` has a Topic name entry whose `_Avoid_` line refuses *topic label* and *topic title* |

The calls at the head of the arrangement step, §7's line in the invocation log, the members of §2, §2's three stops at the arrangement run's mint and the rows of §9 are held by the tests the change that builds them adds and changes, with #832's tables (§11).

## What this does not decide

- **The tables and columns of §9**, and how a re-run discards them: #832.
- **How the gate, the reports and the three renderings show a name**, and whether they mark one a seed stood in for: [#833](https://github.com/algernon28/vespera/issues/833) and [#834](https://github.com/algernon28/vespera/issues/834).
- **What a part of the level between a topic and its clusters is sent**, the wording of its question and what stands in for it: #831.
- **The rates §5 and §6 are chosen without**: how often an answer is usable, in the language asked, and whether another budget names better (Context).
