# ADR-322 — The deliverable is written and drawn in the language the profile names, and the question names the answer's two fields and where its writing ends

- **Date**: 2026-10-22
- **Status**: accepted on 2026-10-22.
- **Decided**: by the operator, who decided that what the deliverable shows its readers is in Italian: the fixed words Vespera writes on its pages, the title and the writing of each cluster, which the local model writes in Italian itself and nothing translates afterwards, and every page title Vespera or the model makes; and that the question states the shape of the answer it imposes. Whether the HTML tree follows the Confluence rendering, whether the language is a value or fixed, the Italian words, the language the instruction is written in and the wording of the shape are decided under delegation.
- **Amends**: [ADR-161](0161-the-instruction-follows-the-documents-and-names-a-length-the-reply-allowance-holds.md) §1: the instruction after the documents ends with one more sentence, naming the answer's two fields, where the writing ends, and the language (§3). Its two rules and its 400 words stand.
- **Amends**: [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md) §2 and [ADR-319](0319-a-cluster-whose-answer-is-turned-down-the-last-two-times-it-is-asked-stands-without-writing-and-the-generation-step-finishes-with-it.md) §5.1: each sentence they fix has an Italian rendering, given in §4, drawn where the deliverable's language is Italian. The English sentences stand word for word.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1.4, *"`<html lang="en">`"*: a page of the tree names the deliverable's language (§4).
- **Amends**: [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md) §2: in an Italian rendering, the second form reads `TEXT (P.C pagina K · MARKER)` (§5).
- **Amends**: [ADR-315](0315-the-root-page-carries-the-indexs-groups-and-counts-and-documents-csv-as-a-file-and-neither-the-corpus-root-nor-the-profiles-values.md) §2: in an Italian rendering, the root page's heading and its one sentence are §4's Italian.
- **Keeps**: [ADR-162](0162-writing-that-ends-with-the-answers-closing-brace-is-turned-down-as-malformed.md) whole: writing that ends with a closing brace belonging to no opening one is turned down, never stripped, with the same detail; [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md)'s refusal to repair an answer; [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s imposed schema and its client-side check; [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md) §1, the counting call being the same question; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), whose rendering *group* is *gruppo* in Italian; ADR-174 §5's three words, carried into Italian; [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md)'s criterion that a generation model handle Italian and English; [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md), whose `preferredLanguages` this record neither reads nor changes; ADR-274 §5, the drawing keying no run.
- **Rests on**: [`docs/research/ollama-generation-surface.md`](../research/ollama-generation-surface.md) §3, Ollama's documented advice to state the schema in the prompt as well as in `format`; ADR-319's measurement of the brace under the shipped wording; ADR-161's measurement of the instruction it placed after the documents.

## Context

### The language

Every word the deliverable carries that Vespera writes itself is English: the sentences ADR-174 and ADR-319 fix, the root page's sentence ADR-315 fixes, the membership heading, the disclosure ADR-133 asks for, the navigation ADR-303 adds, a copy's outcome (ADR-274 §2.6), the line on pictures not shown (ADR-149), the index's lines and tables, and ` page K` in a split cluster's Confluence title (ADR-310). Every page of the tree is `<html lang="en">` (ADR-274 §1.4).

The question names no language for the answer. The model writes in whichever language the documents draw it to: ADR-161 recorded Italian replies to the English question, and the archive's documents are in Italian and English together (ADR-033, ADR-114).

### The shape

The call imposes `ANSWER_SCHEMA` through `format`, an object of two required strings, `title` and `prose` (ADR-108). The question says *"Write one connected piece over a group of n document(s)"*, then the documents, then ADR-161's instruction; it never says that the answer is a JSON object, never asks for a title, and never says where the writing ends. Ollama enforces the schema as a constraint on decoding and documents no validation; its structured-outputs page advises passing the schema in the prompt as well, *"to ground the model's response"* (`ollama-generation-surface.md` §3).

Under the shipped wording and model, 8 of 54 first answers of one generation run were turned down for writing that ends with a closing brace belonging to no opening one, and one cluster drew it four times out of four (ADR-319). The model closes its object inside the string and then again for real (ADR-162). Since ADR-319 such a cluster stands without writing once it is turned down twice in a row.

## Decision

### 1. The deliverable's language is a profile value, English unless it names Italian

**`deliverableLanguage`** is a key of the profile. Its answer is `en` or `it`, ISO 639-1 as `preferredLanguages` writes a language, read in any case with the blanks around it ignored.

- **Unset, or blank, is English.** It is the third kind of key ADR-114 named, whose unset state means a default applies, and not a gate: no invocation stops for it.
- **Any other answer stops the invocation** where the generation run is minted, before any call, the way `generationContextWindow` stops on a window that is not one: somebody who wrote a language meant to change what the readers are handed, and drawing English instead would hand them a deliverable nobody chose. The refusal names the key, quotes the value as written, and names `en` and `it`, and that leaving the key empty means `en`.
- **It joins the generation run's identity**: `config_consumed` gains `deliverableLanguage`, the code as read, in lower case, after `numKeep`. The same arrangement written in the other language is another generation run, with a tree and a marker of its own (ADR-103, ADR-310 §1).
- **No `Measurement` pointer**: no pass over the corpus informs who reads the deliverable.

`synthesis` holds the two languages, their codes, the English name the question uses for each, and that English is the default, as it holds the default window (ADR-108): `DeliverableLanguage`, `ENGLISH` and `ITALIAN`. `pipeline` reads the key and hands `synthesis` and `deliverable` the language as a plain value, as it hands them the window (ADR-110); the class that reads it, `LanguageOfTheDeliverable`, holds no rule in ADR-222's sense. The control panel's table of keys gains the key, a text that enters the generation run, and saves only `en` or `it` in any case.

**Why a value of the profile and not fixed.** Who reads the deliverable is a fact about one corpus, and the profile is where a judgement about a corpus is kept with its provenance; shipped code carrying one operator's language would carry a judgement about one corpus into every other. **Why not application configuration.** It names nothing about where a thing is served, which is what application configuration holds (ADR-114). **Why English by default.** Unset, a deliverable is drawn in the language every record before this one fixed, so a corpus whose operator writes nothing is handed what it was handed before, but for §3's sentence. **Why two languages.** Every fixed word needs a rendering a record fixes; §4 gives Italian's. A third language is a record of its own, with its table.

### 2. The writing is written in it by the model, and the instruction stays in English

The question asks for the title and the writing in the deliverable's language, by its English name, as the last words it says (§3). Nothing translates an answer afterwards.

The instruction is English whatever the language:

- **The wording that holds is the measured one.** ADR-161's instruction passed 15 calls of 15 on the cluster that failed and 33 of 33 across the run's clusters, and ADR-159's citation sentence was measured with it. An Italian instruction is a new wording, unmeasured on everything those records measured; one sentence added to the measured wording changes the least.
- **One wording, not one for each language.** ADR-161 refused saying one thing twice in two wordings, which is two places to drift; a question in each language would be that, by design.
- **The question's language does not decide the answer's.** The documents are Italian and English together, and the model has answered an English question in Italian unasked (ADR-161). A sentence naming the language decides it; the language the instruction happens to be in does not.
- **The fields are named in English either way.** `title` and `prose` are the schema's keys, and an Italian instruction would still name them so.

### 3. The question names the answer's two fields, where its writing ends, and the language

After ADR-161's instruction, as the last thing the question says, one sentence more:

> Answer with one JSON object of two fields: "title", a heading of a few words for the group, then "prose", the connected piece, which ends with its last sentence. Write both in Italian.

`Italian` is the deliverable language's English name, `English` for `en`. The two questions differ in that word alone. The counting call carries the same question (ADR-166 §1).

- **It names the fields the call imposes, in the schema's order**: `title` first, as ADR-125 reads them. It asks for the heading, which until now the schema alone asked for.
- **It says where the writing ends, and forbids nothing.** *"which ends with its last sentence"* is the place the model was closing its object, said as where the writing stops. It does not forbid braces: a balanced pair is content (ADR-162), and a sentence forbidding braces would have the model drop `{PORT}` from a technical corpus.
- **It describes the schema rather than pasting it.** The schema's own text is a dozen whitespace-separated pieces that count as several times as many tokens, and it says the fields are strings, which `format` already holds; what the model is not told is that its writing is one field of an object and ends there, and the sentence tells it that.
- **ADR-162 is unchanged.** Writing that ends with a closing brace belonging to no opening one is still turned down as `SCHEMA_VIOLATION`, with ADR-162's detail, and never stripped. This sentence asks for fewer of them; it repairs none.

**The reserve stays 256 tokens.** The text after the last document's opening grows from 58 to 91 words in the one-document case, 182 tokens at `TOKENS_PER_WORD`, inside `INSTRUCTION_RESERVE`. With the opening, the question's own words come to 128, 256 tokens at the pessimistic ratio, the label and the seed path beyond it. Since ADR-166 the reserve is the estimate a proposal is first filled to and not a guard: the engine counts the question, and what is sent is what it counts inside the room. Raising the reserve would move the smallest window a profile may name, 1282 tokens, which the jar and the control panel both check, for a gain nothing measured.

### 4. The fixed words of both renderings are drawn in it; the operator's pages stay English

The HTML tree and the Confluence rendering are two drawings of one deliverable, carrying the same entries and citations (ADR-301 §4), and the tree is handed over as files and may be carried anywhere (ADR-274). So **every fixed word of every page of the tree is drawn in the deliverable's language: the cluster pages, the index, and every page of the Confluence rendering.** A page in two languages serves no reader.

**What stays English**: everything outside the tree, written for the operator: the reports beside the database, `cluster-page-sizes.html` among them (ADR-303 §8), the progress page, the closing line, the invocation account, the logs, the control panel, `profile.yaml` and the label file; and inside the tree what is a name of ours or a file's: the column names of `documents.csv` and `code.csv` and the words of their `copy_outcome` column (ADR-122), the profile's keys as the index lists them, file and folder names, `vespera-deliverable.txt`, `pages.json`'s keys, the anchors `document-n`, and a fault's `detail`, which no page shows.

**The Italian, fixed here.** Where a sentence has a number, `%d` stands for it.

| Where | English, as its record fixes it | Italian |
|---|---|---|
| A cluster page, ADR-174 §2: the question longer than the room | *Nothing was written over this group: its documents came to more than the writing model was given room to read at once.* | *Su questo gruppo non è stato scritto nulla: i suoi documenti superavano quanto il modello di scrittura aveva spazio per leggere in una volta.* |
| An answer that ran out of room | *Nothing was written over this group: the writing model's answer reached its length limit before it was finished, so it was not used.* | *Su questo gruppo non è stato scritto nulla: la risposta del modello di scrittura ha raggiunto il limite di lunghezza prima di essere completa, quindi non è stata usata.* |
| An answer not in the shape asked for | *Nothing was written over this group: the writing model's answer did not come back in the shape it was asked for, so it was not used.* | *Su questo gruppo non è stato scritto nulla: la risposta del modello di scrittura non è tornata nella forma richiesta, quindi non è stata usata.* |
| A citation out of range, or none | *Nothing was written over this group: the writing model's answer cited documents it had not been given, or cited none, so it was not used.* | *Su questo gruppo non è stato scritto nulla: la risposta del modello di scrittura citava documenti che non gli erano stati dati, o non ne citava nessuno, quindi non è stata usata.* |
| No member could be sent | *Nothing was written over this group: none of its documents could be read and sent to the writing model.* | *Su questo gruppo non è stato scritto nulla: nessuno dei suoi documenti ha potuto essere letto e inviato al modello di scrittura.* |
| Every readable member too long | *Nothing was written over this group: each of its documents that could be read was judged longer than the writing model was given room to read at once, so none was sent.* | *Su questo gruppo non è stato scritto nulla: ciascuno dei suoi documenti che si è potuto leggere è stato giudicato più lungo di quanto il modello di scrittura aveva spazio per leggere in una volta, quindi non ne è stato inviato nessuno.* |
| Not reached | *Nothing has been written over this group yet: writing stopped before it reached this group. Running the same command again carries the writing on.* | *Su questo gruppo non è ancora stato scritto nulla: la scrittura si è fermata prima di arrivare a questo gruppo. Eseguire di nuovo lo stesso comando fa proseguire la scrittura.* |
| ADR-319 §5.1, beneath the reason | *Its answer was turned down each time it was asked, so this group stands without writing; running the same command again asks nothing more about it.* | *La sua risposta è stata scartata ogni volta che è stata chiesta, quindi questo gruppo resta senza testo; eseguire di nuovo lo stesso comando non chiede altro su di esso.* |
| A cluster page with no reason given, and the index's and root page's cell | nothing was written over this group | su questo gruppo non è stato scritto nulla |
| The membership list's heading | The documents in this group | I documenti di questo gruppo |
| The disclosure, ADR-133 | Written from the first %d of the %d documents in this group. | Scritto a partire dai documenti numerati da 1 a %d, su un totale di %d in questo gruppo. |
| The writing's hover, ADR-275 | What the local model wrote over this group from the documents listed below it; each number in brackets is a link to the document it cites. | Ciò che il modello locale ha scritto su questo gruppo a partire dai documenti elencati qui sotto; ogni numero tra parentesi quadre è un collegamento al documento che cita. |
| A number whose document the cluster no longer holds | a document the writing was made from, which this group no longer holds | un documento da cui è stato scritto il testo, che questo gruppo non contiene più |
| The navigation, ADR-303 §5 | Page %d of %d: first, previous, next, last | Pagina %d di %d: prima, precedente, successiva, ultima |
| A copy's outcome, `changed` | changed in the archive since it was read, so it was not copied | modificato nell'archivio dopo essere stato letto, quindi non è stato copiato |
| `gone` | no longer in the archive, so it was not copied | non più presente nell'archivio, quindi non è stato copiato |
| `no-room` | not copied: the drive had too little room left | non copiato: sull'unità restava troppo poco spazio |
| `not-copied` | could not be copied | non è stato possibile copiarlo |
| Pictures not shown, one | %d more picture from this document is not shown. | %d altra immagine di questo documento non è mostrata. |
| Pictures not shown, more | %d more pictures from this document are not shown. | %d altre immagini di questo documento non sono mostrate. |
| The index's and root page's heading, and the index's title | Deliverable *run* | Base di conoscenza *run* |
| The index's three lines | Run: · Walk: · Archive root: | Esecuzione: · Esplorazione: · Radice dell'archivio: |
| The index's paragraph | The path column of documents.csv names each document beneath the archive root recorded above. Each group's folder holds its page and a copy of every document listed on it, checked against the digest taken when it was converted, and each link on a group's page leads to its copy; the archive root names where they were copied from. The seed_partition column is not a path beneath that root: it names a seed beneath the seed folder, which is a root of its own. The copy and copy_outcome columns say which documents were copied and, of any that were not, why. | La colonna path di documents.csv indica ogni documento a partire dalla radice dell'archivio riportata sopra. La cartella di ogni gruppo contiene la sua pagina e una copia di ogni documento che vi è elencato, verificata con l'impronta presa quando è stato convertito, e ogni collegamento della pagina di un gruppo porta alla sua copia; la radice dell'archivio indica da dove sono state copiate. La colonna seed_partition non è un percorso sotto quella radice: indica un documento campione sotto la cartella dei campioni, che è una radice a sé. Le colonne copy e copy_outcome dicono quali documenti sono stati copiati e, per quelli che non lo sono stati, perché. |
| The table of a seed partition's groups, on the index, the root page and the seed partition's page | Group · Documents · Written up as | Gruppo · Documenti · Titolo del testo |
| The index's code sets: heading | Code | Codice |
| Its sentence, ADR-296 §4 | Each code set is copied whole under code/, keeping its folders, and each of its files is checked against the size and modification time recorded when the archive was walked. | Ogni insieme di codice è copiato per intero sotto code/, con le sue cartelle, e ciascuno dei suoi file è verificato con la dimensione e la data di modifica registrate quando l'archivio è stato esplorato. |
| Its table | Code set · Folder in the archive · Files copied · Files not copied | Insieme di codice · Cartella nell'archivio · File copiati · File non copiati |
| A set at the corpus root | the archive's top | la cartella principale dell'archivio |
| The root page's sentence, ADR-315 §2 | Each group's page carries a checked copy of every document it lists. documents.csv lists every document of this run. | La pagina di ogni gruppo porta una copia verificata di ogni documento che elenca. documents.csv elenca ogni documento di questa esecuzione. |

**Every Italian sentence keeps the rules its English keeps** (ADR-174 §5, ADR-122, ADR-319 §5.1): it says *gruppo*, never *cluster*, which Italian also borrows and which reads there as the same everyday heap; it says none of *permanente*, *mai* or *sempre*, since running again can make each false; it names no kind of fault; and each of the eight sentences of ADR-174 and ADR-319 is one plain line, with nothing markup could be made of.

**A page names its language**: `<html lang="it">` where the deliverable is Italian, `<html lang="en">` where it is English. `cluster-page-sizes.html` is the operator's and stays `en`.

**Where the plan of a split page counts its navigation at an upper bound** (ADR-303 §3), it counts the navigation in the deliverable's language, whose words are longer in Italian, so that no page comes out larger than its plan.

### 5. Page titles

- **A cluster's** is its title, which the model writes in the deliverable's language (§3), or its label where nothing was written, derived from its highest-scoring document's title and never translated (ADR-106). In an Italian rendering the second form of ADR-310 §2 reads `TEXT (P.C pagina K · MARKER)`.
- **A seed partition's** is its seed's file stem, a file's name, unchanged.
- **The root page's** is `Vespera MARKER`, a name, unchanged.
- **The HTML pages' `<title>`** is the cluster page's heading, and on the index `Base di conoscenza` and the run's id where the language is Italian.

### 6. The run ids that move

- **Generation (6b).** `synthesis` changes, and `StageModules.GENERATION` names it (ADR-058); and `config_consumed` gains `deliverableLanguage` (§1). A new generation run asks every cluster of the approved arrangement afresh, under the question of §3, in the deliverable's language.
- **Arrangement (6a).** `StageModules.ARRANGEMENT` names `synthesis` too, so the arrangement is minted again over the same scoring run, with the same clusters, and is approved again by its new id before 6b runs, unless `arrangementApproved` holds `auto` (ADR-107, ADR-317).
- **Nothing before 6a.** No other stage names `synthesis`. `deliverable`, `profile`, `pipeline` and the control panel are named by no stage (ADR-222, ADR-274 §5); the wording of §4 is drawing.

A rendering an earlier generation run published stays on the site; the new run's pages, under a new marker, lie beside it (ADR-310 §1, ADR-308).

## Alternatives rejected

- **Italian fixed in code.** It carries one corpus's readers into every corpus, and leaves an operator whose readers read English no way back but a change to the code.
- **A key of application configuration.** It holds where things are served and what a run is not a judgement about (ADR-114); who reads a corpus's deliverable is a judgement about the corpus.
- **The first of `preferredLanguages`.** That value decides which copy of a translation set survives (ADR-271); unset, it removes nothing, and nothing about it says English. Tying the readers' language to it would make choosing which copy to keep re-mint the writing, and leave a corpus with no translation sets no way to name its readers' language.
- **The HTML tree in English around Italian writing.** Every page would be in two languages, and the tree is handed over as the Confluence rendering is (§4).
- **The instruction in Italian.** Unmeasured where the English wording is measured, and one wording for each language (§2).
- **Translating the answer afterwards.** A second model call per cluster, or a second model, for writing the first could have written in the language asked; and a translation is not the writing the citations were checked in.
- **Pasting the schema's text into the question.** See §3: the sentence carries what the model lacks, in fewer tokens, and the schema's types are already imposed.
- **A temperature of 0**, Ollama's other advice on the same page. Sampled afresh, a turned-down cluster asked again can be believed (ADR-111, ADR-319); at 0 the repair pass would put the byte-identical question and draw the same answer, so a cluster turned down once would be turned down for good. It is also an option sent, and would join the identity for no measured gain.

## Consequences

- **A deliverable whose profile names `it`** has its writing, its titles and every fixed word of every page in Italian, and its Confluence rendering is published under Italian titles.
- **A deliverable whose profile names nothing** is drawn as before, its fixed words English; its question gains §3's sentence, naming English, so it is written afresh too, and its writing is asked for in English where it was asked in no language.
- **The brace's rate under §3's wording is not measured.** It is read off the invocation account's `counts cluster-fault kind=SCHEMA_VIOLATION` line of a generation run under this wording, against the 8 of 54 ADR-319 records, and is a measurement for a record of its own.
- **The two `synthesis` javadocs ADR-319 §4 left stale are corrected in the same change**, `GenerationOutcome.LeftUnfinished`'s and `ClusterGeneration`'s, since the change moves the generation run anyway (ADR-216 §7).
- **`Profile` gains its eighteenth key**, the fourth whose unset state means a default applies, after `generationModel`, `generationContextWindow` and `embeddingModel`.
- **`CONTEXT.md`** gains **Deliverable language**; **Cluster** renders as *gruppo* and **Cluster page** as *pagina* in an Italian deliverable.
- **`README.md`** gains the key in its table of values and in the paragraph on the values that already have answers, and says that the deliverable's pages are in the language it names.
- **`docs/running-stage-by-stage.md`**'s table of what runs again gains the key beside `generationModel`.

## What this does not decide

- **A third language.** It needs a code, an English name, and a table like §4's.
- **Whether §3's sentence lowers the brace's rate.** Unmeasured (Consequences).
- **ADR-319's rule over more than two answers.** It needs a column `synthesis` owns, so a change that moves the generation run, as this one does; it is not taken up here.
- **The operator's pages in Italian.** Reports, the progress page, the closing line and the control panel stay English (§4).
- **The arrangement gate's message naming the language**, as ADR-114 had it name the model. The language is on every page the run draws, and under `auto` the message is not read.

## Tests

| Test | What it holds |
|---|---|
| `synthesis.TheQuestionNamesTheAnswersFieldsAndLanguageTest.namesTheLanguageItWasGivenAndNoOther` | after the last document's opening, the question asks for both fields in Italian where the language is Italian, in English where it is English, and never names the other (§2, §3) |
| `…theTwoQuestionsDifferInTheLanguagesNameAlone` | the Italian question is the English one with the language's name changed, and nothing else (§2) |
| `…namesEachFieldTheCallImposesInTheSchemasOrder` | after the documents the question says JSON and quotes each field the imposed schema requires, `title` before `prose` (§3) |
| `…saysTheWritingEndsWithItsLastSentence` | after the documents the question says the writing ends with its last sentence, and says nothing of braces (§3) |
| `…whatFollowsTheDocumentsStaysInsideTheReserve` | the words after the last document's opening, at 2.0 tokens each, are at most 256 (§3) |
| `…aClosingBraceAfterTheLastSentenceIsStillTurnedDown` | Italian writing ending in ` }` is turned down as `SCHEMA_VIOLATION` with ADR-162's detail (§3, ADR-162 kept) |
| `synthesis.DeliverableLanguageTest` | `en` and `it` are read in any case with blanks ignored; anything else is no language; English is the default; each language's code and English name (§1) |
| `pipeline.LanguageOfTheDeliverableTest` | unset and blank are English, `it` and ` IT ` Italian, and `fr` stops naming the key, the value, `en` and `it` (§1) |
| `pipeline.TheDeliverableLanguageInvocationTest` | with `it`, every question names Italian, the generation run records `deliverableLanguage` `it`, and the cluster page and the index are drawn in Italian; with the key emptied again, a second generation run is minted, asking in English; with `fr`, the invocation stops and no generation run is minted (§1, §2, §4, §6) |
| `pipeline.RunIdentityGoldenTest.generation` | the generation run's settings end with `"deliverableLanguage":"en"` where the profile names none (§1) |
| `deliverable.ItalianWordingTest` | the code's Italian for each of ADR-174's seven cases and ADR-319's line is the record's, word for word; each keeps the rules of §4; the English is unchanged; the line on pictures not shown, the index's code sets and a split cluster's Confluence title in Italian (§4, §5) |
| `deliverable.AnItalianTreeIsDrawnInItalianTest` | an Italian tree, split by a ceiling and holding a written cluster, an unwritten one standing without writing, a disclosure, a number its cluster no longer holds and a copy gone from the archive: every HTML page is `lang="it"`, every fixed word on the cluster pages, the index, the root page, the seed partition's page and the cluster's Confluence pages is §4's Italian, the page list titles the later pages `pagina K`, and no English fixed word is on any page (§4, §5) |
| `deliverable.UnwrittenPageWordingTest` | unchanged in what it holds, the code asked for the English (§4) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest`, `controlpanel.ProfileEditTest` | the control panel saves `it` and `EN` and refuses `fr` and `italian` with one line, and the jar reads the first as an answer and stops on the second (§1) |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `LanguageOfTheDeliverable` holds no rule, and no stage names `pipeline` (§1) |
