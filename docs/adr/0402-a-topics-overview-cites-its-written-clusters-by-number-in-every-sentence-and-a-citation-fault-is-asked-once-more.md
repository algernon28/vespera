# ADR-402 — A topic's overview cites its written clusters by number in every sentence, and a citation fault is asked once more

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in [#830](https://github.com/algernon28/vespera/issues/830), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §7 records it: that a topic's overview cites the topic's clusters by ordinal, checked in range as ADR-109 checks a cluster's citations, each citation leading to that cluster's page; that synthesis over synthesis is accepted for it; that an answer with a sentence citing nothing, or a citation out of range, is asked for once more, by a call that differs from the first, temperature 0 and a seed being sent and entering the run id; and that where the second fails too the topic's page carries a placeholder in the deliverable language, its table of clusters drawn as usual, and the fault is recorded, keyed by the topic. Under the operator's delegation, in #830 and ADR-396 §8: what the overview reads and the range its citations are checked against where some of the topic's clusters have no synthesis doc (§1, §2), what a sentence is (§2), the word budget (§3), the wording of both questions (§4), of the placeholders and their Italian (§5), when an overview is asked and what the step's completion asks of it (§6), and the shape of the rows it is recorded in (§7).
- **Amends**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md), for a topic's overview only: its *"no per-sentence requirement"* and its *"nothing is retried"*. An overview's every sentence carries a citation (§2), and a citation fault is asked once more, by another question (§4). A cluster's synthesis doc is checked as before. It carries a note at its head.
- **Amends**: [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), its repair pass, which *"re-attempts every cluster carrying a `cluster_fault` row"* by a byte-identical call: no repair pass asks a topic's overview again, and its second question differs from its first (§4, §6). It carries a note at its head.
- **Amends**: [ADR-319](0319-a-cluster-whose-answer-is-turned-down-the-last-two-times-it-is-asked-stands-without-writing-and-the-generation-step-finishes-with-it.md) §2, the generation step's completion: it also asks that every topic be settled (§6). It carries a note at its head.
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §4, whose table of Italian gains §5's seven rows. It carries a note at its head.
- **Settles**: ADR-396 §8's row for #830. ADR-396 carries a note at its head.
- **Keeps**: ADR-109's check for a cluster, and its *"not stripped"*: an answer is never repaired; [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s three checks of an answer and its imposed schema, checked client-side; [ADR-162](0162-writing-that-ends-with-the-answers-closing-brace-is-turned-down-as-malformed.md)'s brace; [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md)'s counting call; [ADR-159](0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md)'s square brackets and an example no number of which was not sent; [ADR-161](0161-the-instruction-follows-the-documents-and-names-a-length-the-reply-allowance-holds.md)'s instruction after what is read; ADR-321 §2, the question in English whatever the language, and §3, its last sentence naming the answer's fields, where the writing ends and the language; [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md) §5's rule on words; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md)'s *group*; ADR-111's five-in-a-row stop, for clusters; ADR-113's refusal of a corpus-wide synthesis doc.
- **Rests on**: the measurements below, on public text and on text written for the purpose, with a local model only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).

## Context

ADR-396 §7 gives each topic a short overview, written at 6b over its clusters' synthesis docs, and fixes what the operator decided of it: it cites the topic's clusters by ordinal, `[n]` with `1 ≤ n ≤` the topic's cluster count, checked as ADR-109 checks; a sentence citing nothing or a citation out of range turns it down; it is then asked once more, by a call that differs from the first, since at temperature 0 and a seed the same question draws the same answer; and where the second fails too its page carries a placeholder in the deliverable language and the fault is recorded, keyed by the topic. It leaves to #830 what the overview reads and the range a citation is checked against where some clusters have no synthesis doc, its word budget, the wording of the second call and of the placeholder and their Italian, and the fault's table, whose columns are #832's.

Four things shape the answer. A cluster's synthesis doc carries citations of its own, `[n]` pointing at documents (ADR-109), which read as the overview's citations if they are sent as they stand. A cluster may end a run with no synthesis doc: turned down (ADR-111, ADR-319), with nothing that could be sent (ADR-121), or not reached. A cluster turned down once is asked again by the next invocation and may be written then (ADR-111), so an overview written over the clusters written so far may be outdated by a later one. And ADR-109's check counts no sentence, so a sentence is something this record has to define before the check can read it.

### Measured

A probe outside the repository, on public text and on text written for it; no seed set, archive or deliverable was read.

**What a sentence is.** Three rules for where a sentence ends, run over the bodies of the 3,019 articles of Reuters-21578's test split (345,434 words) and over an Italian text of 316 words and 25 sentences written for the probe, dense in abbreviations (*S.p.A.*, *D.Lgs.*, *art. 5*, *Sig.*, *Dott.*, *L'ing.*, *p.es.*, *ecc.*, *3,5*, *12.03.2021*):

| Rule | Reuters: ends found | Italian: ends found, of 25 |
| --- | --- | --- |
| R0: `.`, `!`, `?` or `…`, then a blank or the end | 14,799 | 35 |
| R1: R0, and what follows starts with a capital letter, a digit, a quotation mark, `(` or `[` | 14,166 | 32 |
| R2: R1 without a digit, and not after a run of single letters each followed by a full stop, a single capital letter, or one of 25 short titles | 12,926 | 25 |

Of the 1,240 ends R1 finds and R2 does not on Reuters, 771 follow *U.S.*, 82 *U.K.*, and most of the rest an initial (*J.*, *E.F.*, *H.K.*). Of R2's ends, 488 (3.8%) follow a capitalised word of four letters or fewer; a sample of 30 of the 170 following *Inc.*, *Corp.*, *Co.*, *Ltd.*, *Rep.* or *Sen.* held 28 true ends and 2 false (*Sen. John*, which the titles then took in, and *Co. Plc*). On the Italian text R2 found the 25 sentences exactly.

**How long the read is.** Words and tokens of writing in the shape of a cluster's synthesis doc, two pieces in English and two in Italian written for the probe, and of public Reuters bodies, counted by a local Ollama serving Qwen3-Embedding-0.6B, whose tokenizer is the Qwen3 one the shipped generation model `qwen3:8b` uses:

| Text | Words | Tokens | Tokens a word |
| --- | --- | --- | --- |
| a cluster's writing, English | 321 | 368 | 1.15 |
| a cluster's writing, Italian | 331 | 581 | 1.76 |
| the bodies of 40 words or more among the test split's first 40 Reuters articles | 8,460 | 11,638 | 1.38 |
| the second question of §4 without its clusters | 168 | 210 | 1.25 |

At the default window of 8,192 tokens, less the answer's 1,024 and about 210 for the question, the room holds about 3,900 words of Italian writing and about 6,000 of English: some 11 and 17 clusters' writing at ADR-161's longest measured answer, 345 words. ADR-108's estimate of 2.0 tokens a word proposes 3,456 words, 8 clusters at the 400 words asked, and the engine's count then sends what fits (ADR-166). An overview of 150 words in Italian is about 265 tokens.

**Not measured**: any answer of a generation model. How often a first answer passes the check of §2, how often the second question of §4 turns a turned-down one into a believed one, and how the word budget of §3 moves either, are read off the rows of §7 the first generation runs write under this record.

## Decision

### 1. What the overview reads

**One question per topic, asked once more where §4 has it. It carries the topic's name and, under each cluster's number (§2), that cluster's title and its synthesis doc's writing, in the order of the numbers, for every cluster of the topic that has a synthesis doc.**

- **Each writing's own citations are taken out** before it is sent: every `[n]` ADR-109's notation reads, with the blanks before it. They point at documents of one cluster, and sent as they stand they would be read, and cited, as numbers of clusters. The answer is never edited (ADR-109's *"not stripped"*): what is taken out is the input's.
- **A cluster with no synthesis doc is not sent**, and its number is shown nowhere in the question. Its page still lists its documents, but the call read nothing of it, so a sentence citing it would cite what was never read.
- **What fits is found as a cluster's documents are** (ADR-108, ADR-166): proposed in number order at the word estimate, a cluster whose title and writing are more words than the room holds passed over and the fill stopping at the first cluster that does not fit what is left; then counted by the serving engine, the proposal whole and, where it is counted past the room, by halving, and what is sent is as many of the proposal, in number order, as the engine counts inside the room. A cluster whose writing the engine counts past the room on its own, as the first of a proposal, is passed over and a new proposal made from the clusters left, as a cluster's call passes over a document too large for it (`ClusterSynthesis`). The count is of the second question's wording (§4), the longer, and both calls send what it found, so the second never sends less than the first and the numbers the check reads are the same in both.
- **No written cluster, no call.** A topic none of whose clusters has a synthesis doc has nothing to read, and its page says so (§5).
- **A topic whose written clusters do not all fit** is still written over those that do, and its page discloses it (§5), as ADR-108 has a cluster too large for one call written over its closest documents and say so.
- **A topic is turned down for the room only where every written cluster was passed over**: nothing is left to send, no answer is asked for, and the fault is recorded as §7 says.

### 2. The numbers, the check, and what a sentence is

**A cluster's number is its place among the topic's clusters**, 1 to the topic's cluster count, in the order the arrangement lists them, through the level between a topic and its clusters where the topic has one ([#831](https://github.com/algernon28/vespera/issues/831)). It is the number a reader of the topic's page finds the cluster at, so one numbering serves the question, the check and the reader, as ADR-109 has it for a cluster; where clusters are unwritten the numbers sent have gaps.

**The check, in full: every sentence of the answer's writing carries at least one citation, and every citation is a number the call sent.** The second clause is ADR-109's, *"the ordinal is one Vespera minted"*: `1 ≤ n ≤` the cluster count holds of every number sent, and a number in that range the call did not send, an unwritten cluster's or one passed over for the room, is turned down as one past the count is. Both turn the answer down as `CITATION_NOT_IN_RANGE`; no kind is added.

**A sentence ends** at `.`, `!`, `?` or `…`, with any closing quotation marks or bracket after it, followed by a blank and then a capital letter, a quotation mark, `(` or `[`, or by the end of the writing (R2, Measured). It does not end there:

- after a run of single letters each followed by a full stop, or a single capital letter: *U.S.*, *e.g.*, *S.p.A.*, *J.*;
- after one of the titles *Mr*, *Mrs*, *Ms*, *Dr*, *St*, *Sr*, *Jr*, *Sen*, *Rep*, *Gov*, *Gen*, *No*, *Nr*, *Sig*, *Sigg*, *Dott*, *Prof*, *Ing*, *Avv*, *Art*, *Pag*, *Pp*, *Vol*, *Cap*, *Fig*, in any case, the part before an apostrophe set aside (*L'ing.*);
- before a digit or a lower-case letter: *art. 5*, *3.5*, *e.g. wheat*.

**A citation that opens a sentence belongs to the one before it**: *"… the harvest. [2] The …"* cites in the sentence about the harvest, as a model writing a citation after its full stop means it to. The last sentence is the text after the last end.

The rule errs towards fewer sentences: where it misses an end, two sentences are checked as one and one citation passes both; where it finds a false one, a sentence is turned down that a reader would have read as cited. On public newswire the second was 2 in a sample of 30 of the ends most like it (Measured).

### 3. The word budget, and what the call is sent with

**At most 150 words.** About six sentences, each carrying its citation, read above a table of clusters each with a page of its own. ADR-161 asks 400 of a cluster's writing, which connects tens of documents; an overview connects what those pieces already say, and every sentence it adds is one more the check of §2 can turn down. At the ratio ADR-108 prices a word at, 150 words are 300 tokens, under a third of the 1,024 the answer is allowed.

**The call is a cluster's call in all else but three things**: it sends `temperature` 0 and a seed, as the operator decided, the seed being 1, and its imposed schema is one object of one required string, `prose`. The window, the answer's allowance, `num_keep` and thinking left off are a cluster's call's (ADR-108, ADR-159, ADR-166), and the counting call is made as for a cluster. The seed is 1 rather than 0 so that no reading of nought as unset can drop it.

### 4. The two questions

**The first question**, in English whatever the deliverable language (ADR-321 §2), `N` being the clusters sent, `NAME` the topic's name, `EXAMPLE` ADR-159's example drawn from the numbers sent (the first, then the second and third, as `[1] or [2][3]`; with two, `[a] or [a][b]`; with one, `[a]`), and `LANGUAGE` the deliverable language's English name:

> Write a short overview of a topic from an archive, over what was written on N of its groups.
>
> The topic is called "NAME".
>
> What was written on each group follows below, under the number to cite it by.
>
> *each cluster sent, as `[n] TITLE`, a line break, then its writing, the clusters a blank line apart*
>
> That is all N group(s). Write the overview in at most 150 words: say what the groups cover together, how they relate and where they differ. Do not describe them one by one. End every sentence with the bracketed number of each group it draws on, written in square brackets exactly as the numbers appear above, such as EXAMPLE, and use no other citation of any kind.
>
> Answer with one JSON object of one field: "prose", the overview, which ends with its last sentence. Write it in LANGUAGE.

**The second question is the first with one paragraph more**, before the last, so that the sentence naming the answer's field stays the last thing it says (ADR-321 §3):

> An earlier answer to this was turned down because a sentence cited no group, or cited a number not shown above: every sentence must end with the bracketed number of at least one group shown above.

- **It is one wording, whatever the first answer was.** It names what failed by the check that failed, the only one it follows, and carries nothing of the first answer: not its text, not which sentence, not which number. So no model reads a model's output (ADR-026), the second question is fixed by the first, and it is as deterministic as the first.
- **It follows a citation fault alone.** An answer that ran out of room, came back out of shape or ended in a stray brace (ADR-108, ADR-162), and a question the engine counted past the room, are not asked again: the paragraph is about citations, and at temperature 0 the first question asked again draws the same answer. The topic's page carries the placeholder of its kind (§5).
- **It has no Italian.** The question is English whatever the language (ADR-321 §2); the answer is asked for in the deliverable language by the last sentence, as the first question asks it.

What follows the last cluster's writing is 90 words in the first question and 126 in the second, 252 tokens at ADR-108's ratio, inside the 256 `INSTRUCTION_RESERVE` keeps; the opening and the topic's name lie beyond it, as a cluster's label and seed path do, and since ADR-166 the engine's count, not the reserve, is what keeps a question inside the window.

### 5. What the topic's page says where no overview was written

**One sentence for each case, in the deliverable language**, in italics where the overview would be, the table of clusters drawn beneath as usual. Each holds ADR-174 §5's rule: it says *group* (*gruppo*) for a cluster and *topic* (*argomento*) for a topic, none of *permanent*, *never* or *always* (*permanente*, *mai*, *sempre*), names no kind of fault, and is one plain line. `%d` stands for a number.

| Case | English | Italian |
| --- | --- | --- |
| The second answer turned down by the citation check | No overview was written for this topic: each time it was asked, the writing model's answer left a sentence citing no group, or cited a group it had not been given, so it was not used. | Per questo argomento non è stata scritta alcuna panoramica: ogni volta che è stata chiesta, la risposta del modello di scrittura lasciava una frase senza citare alcun gruppo, o citava un gruppo che non gli era stato dato, quindi non è stata usata. |
| The answer ran out of room | No overview was written for this topic: the writing model's answer reached its length limit before it was finished, so it was not used. | Per questo argomento non è stata scritta alcuna panoramica: la risposta del modello di scrittura ha raggiunto il limite di lunghezza prima di essere completa, quindi non è stata usata. |
| The answer not in the shape asked for | No overview was written for this topic: the writing model's answer did not come back in the shape it was asked for, so it was not used. | Per questo argomento non è stata scritta alcuna panoramica: la risposta del modello di scrittura non è tornata nella forma richiesta, quindi non è stata usata. |
| No group's writing fits the room | No overview was written for this topic: what was written on its groups came to more than the writing model was given room to read at once. | Per questo argomento non è stata scritta alcuna panoramica: i testi dei suoi gruppi superavano quanto il modello di scrittura aveva spazio per leggere in una volta. |
| No cluster of the topic written | No overview was written for this topic: nothing was written over any of its groups. | Per questo argomento non è stata scritta alcuna panoramica: su nessuno dei suoi gruppi è stato scritto nulla. |
| Not reached | No overview has been written for this topic yet: writing stopped before it reached this topic. Running the same command again carries the writing on. | Per questo argomento non è ancora stata scritta una panoramica: la scrittura si è fermata prima di arrivare a questo argomento. Eseguire di nuovo lo stesso comando fa proseguire la scrittura. |
| An overview written over fewer clusters than the topic has, beneath it | Written from what was written on %d of the %d groups in this topic. | Scritto a partire dai testi di %d dei %d gruppi di questo argomento. |

The first row is the placeholder the operator asked for, *"Failed to generate an overview with proper citations"*, said in ADR-174's form. A citation in a written overview is rewritten into a link to the cited cluster's page, and no raw `[n]` reaches a page (ADR-109); how the link and the sentences are drawn on each rendering is [#833](https://github.com/algernon28/vespera/issues/833)'s.

### 6. When an overview is asked, and the step's completion

**The overviews are asked in the generation step, after its walk over the clusters, topic by topic in the topics' order (ADR-396 §4).** No step is added.

**A topic's overview is asked once each of its clusters is settled**: it has a synthesis doc; nothing could be sent for it (ADR-121); or it carries a repeated fault and the walk over the clusters leaves the step to be recorded finished under ADR-319 §2. A cluster turned down once may be written by the next invocation (ADR-111), and an overview asked before it would read without it; so a topic with such a cluster waits.

**An overview is asked once under its run, or twice where §4 has it asked again, and never after.** A written overview is kept; a turned-down one stands, its fault recorded, and no repair pass asks it again: at temperature 0 the same question draws the same answer, so a repair pass asking it would pay a call to draw the fault it has. An invocation that ends before reaching a topic leaves it with no row, and the next asks it, which is resumption, not repair. A later generation run, of another model, window or code, asks every overview afresh.

**An overview fault adds nothing to ADR-111's five-in-a-row stop**, and clears nothing of it: the overviews are asked after the clusters' walk, at most two answers each for each topic, a count bounded by the topics, which ADR-399 measured at 3 to 20.

**The generation step is recorded finished** when ADR-319 §2 holds of its clusters and every topic carries a written overview or a turned-down one, or has no written cluster. An overview fault never keeps the step unfinished.

### 7. Where it is recorded

`synthesis` owns three row sets for overviews, each keyed by the generation run and the topic's key (ADR-399 §5). Their tables and columns are [#832](https://github.com/algernon28/vespera/issues/832)'s; their shape is this:

- **an overview**: the writing, and whether it was believed at the first question or the second;
- **what it was written over**: one row for each number the call sent, naming the cluster at that number, so that the disclosure of §5 and each citation's link are read and not worked out again, as ADR-133's `call_exemplar` keeps a cluster's;
- **an overview fault**: its kind, one of `ClusterFaultKind`'s four, closed as ADR-111 closes them, with no fifth: a sentence citing nothing is `CITATION_NOT_IN_RANGE`, as uncited writing is for a cluster, and a topic every written cluster of which was passed over for the room (§1) is `PROMPT_EVALUATION_CEILING`, as a cluster none of whose documents fits is (ADR-166 §4); its detail; and how many answers were asked for: 0 where every written cluster was passed over and no answer was asked for, otherwise 1 or 2.
  - **The detail of a citation fault is the first fault in sentence order**: the sentences are read in order, and a sentence's citations in order, and the first sentence citing nothing or the first citation the call did not send, whichever is met first, is the one named, as *"sentence 3 of 6 cites no group"* or *"citation [4] against groups 1, 2, 5 sent"*.
  - **The detail of a topic every written cluster of which was passed over** carries what the serving engine said of the last question counted, its count or its refusal word for word, as `ClusterSynthesis` carries a cluster's: *"no cluster's writing fits the window by the serving engine's own count -- the last question counted: 7800"*; where every cluster's writing was larger than the room by the word estimate and no question was counted, the count's place reads *"no cluster's writing was ever counted"*.

A topic carries a row in the first or the third, never both; no row in either is a topic not reached, or one with no written cluster, which the clusters' rows tell apart. Like `cluster_fault`, the third is no verdict: nothing is removed for it (ADR-111). Unlike `cluster_fault`, no row of it is deleted on success, since nothing asks it again (§6).

### 8. Run ids

**Generation (6b) moves**: `synthesis` changes, which `StageModules.GENERATION` names, and the generator identity gains what the overview call sends, `temperature` 0 and `seed` 1 (ADR-108); the member names are #832's, with the rest of generation's identity for topics. **The arrangement (6a) moves** with it, `StageModules.ARRANGEMENT` naming `synthesis` (ADR-321 §6). Nothing before 6a moves for this record.

### 9. What the implementation owes

- In `synthesis`: `OverviewSource(int number, String title, String writing)`; `OverviewQuestion.first(String topicName, List<OverviewSource> sources, DeliverableLanguage language)` and `.second(...)` of the same arguments, §4's two questions, the citations of each writing taken out (§1); `OverviewQuestion.WORD_LIMIT`, 150, and `OverviewQuestion.SECOND_ASKING`, §4's paragraph; `OverviewSentences.of(String writing)`, the sentences of §2 in order; `OverviewCheck.faultIn(String writing, SortedSet<Integer> numbersSent)`, empty or the `ClusterFault` of §2 with §7's detail; `TopicOverviewSynthesis(ChatModel)` and its `overviewFor(OverviewCall call, String modelName, int contextWindow, DeliverableLanguage language)`, `OverviewCall(String topicName, List<OverviewSource> written)`, returning a `TopicOverviewOutcome` that is `Written(String writing, List<Integer> numbersSent, int asked)`, `TurnedDown(ClusterFault fault, int asked)` or `NothingToSend()`; `TopicOverviewSynthesis.TEMPERATURE` and `.SEED`; and `OverviewUnwritten`, the six cases of §5 but the disclosure, with `OverviewUnwritten.of(ClusterFaultKind)` for the four faults.
- In `deliverable`: `TopicOverviewWording.of(OverviewUnwritten why, DeliverableLanguage language)` and `TopicOverviewWording.disclosure(DeliverableLanguage language)`, §5's words.
- The walk of §6 in `synthesis`, the step's completion in `pipeline` beside `RepeatedFaults`, and the rows of §7 under #832's tables.

## Alternatives refused

- **Checking against `1 ≤ n ≤` the cluster count alone.** A number in range naming an unwritten cluster would pass, and its sentence would cite a cluster the call never read: the fabrication ADR-109 makes arithmetic would be in range.
- **Numbering the written clusters 1 to `k` among themselves.** It keeps ADR-109's dense range, but the overview's `[3]` would lead to the topic's fifth cluster where a reader of its table finds another at 3; one numbering for the question, the check and the reader is what ADR-109 bought, and a set of numbers sent checks as cheaply as a range.
- **Sending an unwritten cluster under its label.** A label is a document's title and a count (ADR-106); a sentence about it would be about one title, cited as though it were about the cluster.
- **Sending each writing with its citations.** Its `[n]` names a document of another call; read as a cluster's number it is in range as often as not, and a misattribution in range is the one fault the check cannot see.
- **Asking an overview before every cluster of its topic is settled**, over what is written so far. A later invocation writing one more cluster would leave the overview silent on it, under a run that asks nothing again.
- **A second question carrying the first answer, or which sentence failed.** A correction round over a model's own output is ADR-026's model checking a model, and two wordings for two failures are two places to drift; one fixed paragraph is neither.
- **The second question after every kind of fault.** The paragraph is about citations; after a schema fault at temperature 0 it is the only change, and nothing measured says it changes the shape.
- **A sentence rule without exceptions (R0 or R1).** Over newswire R1 cut after *U.S.* 771 times in 14,166; each cut leaves a fragment no citation reaches, and turns a cited overview down.
- **400 words, as for a cluster.** About sixteen sentences each to be cited, where the reader has a page for each cluster one link away.
- **Its own streak breaker.** The overviews of a run are tens of calls at most, after the clusters' walk; five in a row would stop a step whose clusters are written.

## Consequences

- **Every sentence of a topic's overview names a cluster the call read**, and that cluster's page names its documents, so ADR-113's invariant, every sentence of prose tracing to a document, holds of it one link further on.
- **An overview can be turned down by one uncited sentence**, which a cluster's writing cannot (ADR-109); the second question is what this costs at most, one call more for each topic so turned down.
- **A topic whose clusters are not all settled has no overview until they are**, and its page says writing has not reached it; a run left unfinished by a cluster nothing could be sent for writes no overview for a topic whose other clusters are repeated faults.
- **A topic's overview stands under its run once asked**, written or turned down; a turned-down one is asked again only by another generation run.
- **`ClusterFaultKind`'s four kinds name an overview's faults too**; its javadoc's *"a citation outside the range"* reads, for an overview, a sentence citing nothing as well.
- **ADR-109, ADR-111, ADR-319, ADR-321 and ADR-396 carry a note *"Partly amended — see ADR-402"*** at their heads.
- **`CONTEXT.md`** gains **Topic overview**.

## What this does not decide

- **The tables and columns of §7**, and the members of generation's identity: #832.
- **How the overview, its citations' links, its placeholder and its disclosure are drawn** on the HTML tree, the Confluence rendering and the website: #833.
- **Whether the invocation account or a report counts overviews written and turned down**: #834's, if any.
- **The rates §3 and §4 are chosen without**: how often a first answer passes, how often the second question rescues one, and whether 150 words moves either (Measured).
- **Whether the second question should follow a schema fault or an answer that ran out of room.** The operator decided it for the citation check; the others are placeholders at once (§4).

## Tests

| Class | What it holds |
| --- | --- |
| `synthesis.OverviewSentencesTest.endsASentenceAtAFullStopQuestionOrExclamationMarkBeforeACapital` | three sentences ended by `.`, `?` and `!` are three, in order (§2) |
| `…aCitationAfterTheFullStopBelongsToTheSentenceBefore` | `"… harvest. [2] The …"` puts `[2]` in the first sentence (§2) |
| `…doesNotEndAfterLettersEachFollowedByAFullStopAnInitialOrATitle` | *U.S.*, *S.p.A.*, *J.*, *Dott.* and *L'ing.* before a capital end no sentence (§2) |
| `…doesNotEndBeforeADigitOrALowerCaseLetter` | *art. 5*, *3.5* and *e.g. wheat* end no sentence (§2) |
| `synthesis.OverviewCheckTest.writingWhoseEverySentenceCitesAClusterSentPasses` | no fault (§2) |
| `…aSentenceCitingNothingIsTurnedDown` | `CITATION_NOT_IN_RANGE`, *"sentence 2 of 3 cites no group"* (§2, §7) |
| `…aNumberWithinTheTopicButNotSentIsTurnedDown` | groups 1, 2 and 5 sent, `[4]` cited: `CITATION_NOT_IN_RANGE`, *"citation [4] against groups 1, 2, 5 sent"* (§2, §7) |
| `…aNumberPastEveryClusterIsTurnedDown` | the same, for `[9]` (§2) |
| `…theFirstFaultInSentenceOrderIsTheOneNamed` | a first sentence citing nothing and a second citing `[9]` name the first; the two the other way round name `[9]` (§7) |
| `synthesis.OverviewQuestionTest.sendsEachWrittenClusterUnderItsOwnNumberWithItsCitationsTakenOut` | groups 1, 3 and 4 sent under those numbers, titled, their writing's `[n]` gone with the blank before it, no `[2]` anywhere (§1, §2) |
| `…theSecondQuestionIsTheFirstWithOneParagraphBeforeTheLast` | the second is the first with `SECOND_ASKING` and a blank line inserted before its last paragraph, word for word as §4 gives it (§4) |
| `…asksForAtMostTheWordLimitAndNamesTheLanguageLast` | `WORD_LIMIT` is 150, the question asks for at most 150 words, and ends naming `prose` and *Italian* (§3, §4) |
| `…theExampleShowsOnlyNumbersSent` | groups 2, 5 and 7 sent: `[2] or [5][7]`; groups 3 and 6: `[3] or [3][6]`; group 4: `[4]` (§4) |
| `…whatFollowsTheLastClusterStaysInsideTheReserve` | the second question's words after the last group's writing are at most 128, 256 tokens at 2.0 (§4) |
| `synthesis.TopicOverviewSynthesisTest.aCitationFaultIsAskedOnceMoreAndTheSecondAnswerBelieved` | an uncited first answer, then a cited one: written, asked twice, the second question carrying `SECOND_ASKING` (§4) |
| `…turnedDownTwiceIsTheSecondAnswersFaultAfterTwoAnswers` | two uncited answers: turned down with the second's fault, asked twice, and no third answer asked for (§4, §6) |
| `…anAnswerOutOfShapeIsNotAskedAgain` | an answer not in the shape asked for: turned down as `SCHEMA_VIOLATION`, asked once (§4) |
| `…aCitedFirstAnswerIsAskedOnce` | written, asked once (§4) |
| `…everyAnswerIsAskedAtTemperatureZeroWithTheSeedAndOneField` | each answering call sends temperature 0, seed 1, and a schema requiring `prose` alone (§3) |
| `…aTopicWithNoWrittenClusterAsksNothing` | nothing to send, and no call made (§1) |
| `synthesis.TopicOverviewRoomTest.aClusterTheEngineCountsPastTheRoomIsPassedOverAndTheRestSent` | clusters 1 to 3, the first counted past the room alone: written over 2 and 3, asked once, the question sending 2 and 3 (§1) |
| `…aClusterLongerThanTheRoomByTheEstimateIsPassedOverBeforeAnyCount` | the first cluster's title and writing over the 3,456 words the estimate has room for: written over 2 and 3, and no question counted or asked holds the first (§1) |
| `…noClusterFittingTheRoomTurnsTheTopicDownWithTheLastCount` | two clusters each counted past the room alone: turned down as `PROMPT_EVALUATION_CEILING`, asked 0 times, the detail giving the last count, the second's (§1, §7) |
| `…noClusterCountedTurnsTheTopicDownSayingSo` | two clusters each over the estimate's room: turned down as `PROMPT_EVALUATION_CEILING`, asked 0 times, the detail saying no cluster's writing was ever counted, and no call made (§1, §7) |
| `…aRunThatFitsInPartIsSentByBothQuestions` | three clusters past the room together and two inside it: both questions send 1 and 2, the overview written over 1 and 2 of 3, the first count being of the second question over all three (§1, §4, §5) |
| `deliverable.TopicOverviewWordingTest.theCodeSaysWhatTheRecordSays` | each of §5's sentences, English and Italian, word for word, the six cases being the record's and each fault kind reading as its case (§5) |
| `…everySentenceKeepsTheRulesOfAPageWithNothingWritten` | each says topic or *argomento* and never cluster, none of the six refused words as a word, and is one line with nothing markup could be made of (§5) |
| `TheVocabularyNamesTheTopicOverviewTest.topicOverviewIsATerm` | `CONTEXT.md` has a Topic overview entry whose `_Avoid_` line refuses *summary* (Consequences) |

The walk of §6, the completion and the rows of §7 are held by the tests the implementation adds with #832's tables.
