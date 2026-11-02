# ADR-332 — `vespera label --auto` is refused under another extractor identity than its run was converted under, and a placeholder in the OCR preset stops only a run

- **Date**: 2026-11-02
- **Status**: accepted on 2026-11-02.
- **Decided**: by the operator, that one rule covers another preset, a refused preset and any other change of the extractor identity, and that plain `vespera label` and `vespera publish` are unchanged (§1); that `DoclingClient` reads the OCR preset from Spring's `Environment`, and the limits that leaves (§2, Known limits); that `Presets` holds the lines and checks each bound first, the module refusing again, and that `Presets`' catches of a type's refusal go (§3); and the limit of a FAT mount on Linux. The words of §1's line, the order of §1's check and what the client holds under such a placeholder are decided under the same delegation.
- **Keeps**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md): a person's answer is never replaced and nothing is sent before the labeller says it may be used; its refusals stand ahead of §1's.
- **Keeps**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) and [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md): another preset or another image is another extractor.
- **Keeps**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md): a sidecar running another image than the one named gets no identity at all, ahead of §1's comparison (Known limits).
- **Keeps**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): no class of `pipeline` gains a rule that shapes a stage's output (§3, §4).
- **Keeps**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, by which a `vespera label` that has started has written its file, whatever then stops it, and which fails nothing where the file cannot be written; and §9's table of lines, word for word.
- **Amends**: ADR-330 §9, whose *"`vespera label` and `vespera publish`, which mint no run and read no preset, are not stopped by one"* stands of plain `vespera label` and of `vespera publish`, `--auto` being §1's; whose refusal gains a placeholder nothing resolves in `vespera.docling.ocr-preset` (§2); and whose *"Each is refused by the type of the module that uses it"* is said as §3 says it. And its Known limits, which gain this record's. It carries a note at its head.

## Context

**What `vespera label --auto` reads.** It mints no run and reads no preset, but it composes the extractor identity through the client: `AutoLabelling` asks for `ExtractorIdentity`, whose text holds what `DoclingClient.sentOptions()` names, `DocumentOpening` looks a conversion up in `extraction_cache` by that identity and the key stage 2 recorded, and a miss gives no opening. `OllamaRelevanceLabeller.answer` answers blank where it is given no opening. So under any other identity than the conversions were recorded under every question not yet answered came back blank, with a normal exit, a WARN for each document, and no floor set. The stage-2 run a label file's run reads is found by `AutoLabelling.extractionRunUpstreamOf`, and its `ExtractionConfigConsumed` holds the identity it was converted under as its first member.

**What a placeholder nothing resolves does.** `DoclingClient`'s constructor takes `vespera.docling.ocr-preset` by `@Value`, so such a value fails the context ahead of every command. Measured on the packaged jar with `-Dvespera.docling.ocr-preset=${NOTHING_NAMES_THIS}`, on OpenJDK 26.0.2.1: exit 1, 61 lines, 36 stack frames, `PlaceholderResolutionException`. `Environment.getProperty` throws the same exception, an `IllegalArgumentException`, for such a value, and `Presets` already turns it into the key's line.

**What standard error holds.** On every invocation that opens the database, the JDK writes four lines of its own there, each opening `WARNING: `, as `sqlite-jdbc` loads its library. The jar's own console logging goes to standard output.

**Where each bound lies.**

| Keys | `Presets` | The module, for a caller that is not `Presets` |
| --- | --- | --- |
| `vespera.clustering.resolution`, `vespera.clustering.neighbours` | checks | `Clustering`, when called |
| `vespera.docling.ocr-preset` | asks `DoclingClient.isOnePresetsName` | the client refuses nothing, built or sending: that method is the rule |
| `vespera.docling.timeouts-in-a-row` | checks | `OccurrenceJudge`, when built |
| `vespera.redundancy.shingle-words` | checks, then builds | `ShingleParameters`, when built |
| `vespera.redundancy.minhash.permutations`, `.bands`, `.rows-per-band` | checks, then builds | `MinHashParameters`, when built |
| `vespera.redundancy.minhash.seed` | checks | none: a `long` holds every value that reaches it |
| `vespera.redundancy.near-duplicate-jaccard`, `vespera.redundancy.containment-index`, `vespera.redundancy.rare-shingle-sample`, `vespera.redundancy.rare-shingle-hits` | checks, then builds | `RedundancyThresholds`, when built |
| `vespera.relevance.top-similarities` | checks | `RelevanceScorer`, when called |
| `vespera.relevance.sample.bands`, `vespera.relevance.sample.per-band` | checks | `RelevanceDistribution.measure`, when called |

`Presets` holds three catches of `IllegalArgumentException` from a type it has just built, in its readings of the window, of MinHash's numbers and of the cuts. No value reaches any of them, each bound being checked first.

## Decision

### 1. `vespera label --auto` is refused under another extractor identity

**Where the extractor identity it composes now is not the one its label file's run was converted under, `vespera label --auto` is refused in one line and a non-zero exit, and nothing is asked.** No labeller is put a question, no answer is recorded, the label file is not rewritten, and no floor is set. The identity it was converted under is the `extractorIdentity` of the stage-2 run `extractionRunUpstreamOf` finds; the comparison is of the two texts, whole.

**One rule covers another preset, a refused preset and any other change of identity**, another image and another `/version` map among them, since each is the same miss in `extraction_cache`. A preset §9 of ADR-330 refuses composes an identity like any other, so `--auto` reads no preset for this.

The line, through `AutoLabelling.Outcome.refused` and what `VesperaCommand.Label` opens every such refusal with, on standard error:

`vespera label --auto recorded nothing: the label file's run was converted under the extractor identity <then>, and this invocation composes <now>, so no opening can be read under it. Nothing was asked. Name what the run was converted under, or run vespera under this one.`

**It stands in `AutoLabelling.run` where the identity is asked for now**, past the labeller's refusal and the refusals of the label file, of its run, of its seed set and of answers typed and not yet recorded, and ahead of the first question. Each of those says something the operator would meet next, and none needs the converter.

**Plain `vespera label` and `vespera publish` are unchanged, and still read no preset.** Neither reads a conversion.

### 2. A placeholder nothing resolves in the OCR preset is a line of ADR-330 §9

**`vespera run` under such a value in `vespera.docling.ocr-preset` ends non-zero in ADR-330 §9's one line, naming the key, with no stack trace, no run minted and ADR-330 §1's file not written; `vespera label` and `vespera publish` are not stopped by it.**

**`DoclingClient` no longer takes the value by `@Value`.** The constructor Spring calls takes the base address and the `Environment`, and reads the key from it as it is built, catching the `IllegalArgumentException` a placeholder nothing resolves throws, and holding that exception's sentence as `Presets` reads it, and nothing where the key is unnamed. `sentOptions()` names what the client holds, so `vespera label --auto` under such a value composes an identity no run was converted under and meets §1, and `vespera run` is stopped by `Presets` before any conversion is sent under it. `DoclingClient(String, String)` stays, with no `@Autowired`, for the tests' doubles of the client.

### 3. `Presets` checks first, and the module refuses again

ADR-330 §9's *"Each is refused by the type of the module that uses it, and the line is `pipeline`'s"* is said thus: **`Presets` holds the lines and checks each bound first, so that the line can name the key; the module's type or method refuses again when built or called, for a caller that is not `Presets`.** Neither is a rule of ADR-222: neither decides a verdict, a cache key, a cluster or text of the deliverable, each refusing a value no run can be made under.

**`Presets`' catches of a type's refusal go**, all three: with each bound checked first no value reaches them, and a line for what no value reaches is one no test can show. A type tightened alone then throws out of `Presets` as what it is, and `PresetsTest.theNearestValueToEachBoundIsAllowed` catches it, for what `Presets` builds or asks: `ShingleParameters`, `MinHashParameters`, `RedundancyThresholds` and `DoclingClient.isOnePresetsName` (Known limits).

**The OCR preset's bound is the one the module holds as a method alone**, as the table above has it: `isOnePresetsName` is the rule, `Presets` asks it, and the client refuses nothing, because under §2 it is built whatever the key holds.

### 4. Which run ids move

| Change | Folder | Moves |
| --- | --- | --- |
| §1, in `AutoLabelling`, and `Runs` reading a run's `config_consumed` | `pipeline`, `ledger` | none: no stage names either |
| §3, and `Presets`' and `StageRuns`' accessors of the window and of MinHash's numbers, named `shingleWindow()` and `minHash()` as `CONTEXT.md`'s Preset entry asks | `pipeline` | none |
| §2, in `DoclingClient` | `extraction` | the seven stages of ADR-330 §10, once: it lands in the one change that builds ADR-330's Part B, whose move it joins, and apart from that change would be a second. Its configuration text does not move, so the conversions, vectors and identities stay as ADR-330 §10 says |

### 5. What the implementation owes

**In `pipeline`**: §1 in `AutoLabelling`; `Presets.shingleWindow()` and `Presets.minHash()` in place of the accessors they rename, the same on `StageRuns` with its two fields, and their callers; `Presets`' three catches removed; the javadoc of `Presets` saying §3; and `DocumentOpening.NO_CONVERSION_ON_RECORD_FALLBACK`'s, whose *"under `vespera label --auto` it does when the Docling image was changed"* §1 ends. **In `ledger`**: a read in `Runs` of a run's configuration text, `run` being its table. **In `extraction`**: §2's constructor and its javadoc. The types `ShingleParameters` and `MinHashParameters`, which predate the entry, keep their names. No test names the read in `Runs`.

## Known limits

- `<value>` in §2's line is Spring's sentence, holding the value as written, and not the value alone.
- The same placeholder in another key taken by `@Value`, such as `vespera.docling.image`, still stops the context, as it did.
- `EffectiveValuesFile` fails whole with a WARN under such a value, as ADR-330 §1 allows of a file that cannot be written, so a `vespera label` under one leaves the file as the last invocation left it.
- "One line on standard error" is of the jar's own lines: the JDK's four stand beside it (Context).
- On Linux a FAT mount's `FileStore` answers `posix`, so a folder is made and then possibly refused, where ADR-330 §7 says it goes on.
- A sidecar running another image than the one named, or not answering, stops `vespera label --auto` where the identity is asked for, as ADR-179 has it, and §1's line is not said: there is then no identity to name.
- A module of the four `Presets` neither builds nor asks, `Clustering`, `OccurrenceJudge`, `RelevanceScorer` and `RelevanceDistribution`, tightened alone, is seen by no test of `Presets`: a value it then refuses still passes the start, and stops the stage that hands it down.
- That `vespera publish` goes on past a rendering found under a bad preset is shown by no test: past that, it reads the Confluence variables of the machine running the test and sends to the site they name. What is shown is that it reaches its own refusal of nothing drawn.

## Alternatives refused

- **Going on with a WARN for each document, as it did.** A normal exit over answers that are all blank says the local model looked at the sample and had nothing to say, and nothing tells the operator why no floor came of it.
- **Asking `Presets.refusal` at the start of `--auto`.** It sees a refused preset alone, and neither another preset nor another image, each of which misses as surely.
- **Catching the refusal where the context fails, in a failure analyser.** One line, and `vespera label` and `vespera publish` still stopped by a value they do not use, which ADR-330 §9 refuses.
- **The module's refusal as the only one, its message the line.** A module does not know the key configuration named its number by, and `Presets` cannot word a key's line from a refusal that does not say which it was.
- **Keeping `Presets`' catches for a bound a type may gain.** The line they gave named no key's value, and a type tightened alone is better seen as a failing test than heard of as a line.

## Consequences

- An operator who changes the OCR preset, the image or the converter after a scoring run, and asks the local model, is told in one line that nothing was asked and which two identities differ.
- A label file cannot be answered blank by a model for this reason, so a blank `--auto` leaves is one where no conversion was on record under the run's own identity, or the model gave no answer.
- A placeholder in the OCR preset costs the operator one line at the start of `vespera run`, and costs `vespera label` and `vespera publish` nothing but the WARN of §2's limit.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ALabelByTheLocalModelUnderAnotherExtractorIsRefusedInvocationTest.underAnotherIdentityItIsRefusedAndNothingIsAsked` | another preset, a refused preset and another image: non-zero, §1's line once, word for word, with the two identities in its order, no stack trace, the labeller asked nothing, no answer recorded, the label file byte for byte, no floor (§1) |
| `pipeline.ALabelByTheLocalModelUnderAnotherExtractorIsRefusedInvocationTest.underTheSameIdentityEveryQuestionIsAnswered` | every question put with its opening and recorded; passes before this is built (§1) |
| `pipeline.ALabelByTheLocalModelUnderAnotherExtractorIsRefusedInvocationTest.aPlainLabelUnderAnotherIdentityGoesOn` | plain `vespera label` under another identity records what was typed; passes before this is built (§1) |
| `pipeline.AnOcrPresetNothingResolvesIT.aRunStopsInOneLine` | on the packaged jar: non-zero, one line of the jar's on standard error opening with the key, no stack frame on either stream, no run minted, no file of ADR-330 §1 (§2) |
| `pipeline.AnOcrPresetNothingResolvesIT.aLabelIsNotStopped` | exit 0, recording the answers typed in a file a run at the shipped value wrote (§2) |
| `pipeline.AnOcrPresetNothingResolvesIT.aPublishIsNotStopped` | its own one line of nothing drawn, and no line of the value (§2) |
| `pipeline.ABadPresetStopsNeitherALabelNorAPublishInvocationTest.aLabelIsNotStoppedByIt` | built under a value `vespera run` refuses, and asked with answers typed in a file a run at the shipped value wrote: they are recorded, under the value a run is then refused for (§1, ADR-330 §9) |
| `pipeline.ABadPresetStopsNeitherALabelNorAPublishInvocationTest.aPublishIsNotStoppedByIt` | built under it, its own refusal of nothing drawn and no line of the value (§1, ADR-330 §9) |
| `pipeline.PresetsTest.theNearestValueToEachBoundIsAllowed` | what `Presets` builds or asks, tightened alone, is seen (§3) |
| `pipeline.PresetsTest.aValueNamingAPlaceholderNothingResolvesIsRefusedInOneLine` | `Presets` alone, on another key: the line (§2) |
| `pipeline.PresetsTest.whatShipsIsAllowed`, `pipeline.PresetsTest.whatShipsReadsAsWhatEveryEarlierRunWasMintedUnder` | `application.yaml` itself, read as Spring reads it, and not a copy of it (ADR-330 §9) |
| `similarity.TheNumbersOfAMinHashSignatureTest`, `similarity.TheWindowOfAShingleTest` | each type's own refusal, for a caller that is not `Presets` (§3) |

## What this does not decide

- **Whether `DoclingClient.convert` refuses a name `isOnePresetsName` refuses.** No path sends one: `vespera run` stops at its start, and nothing else converts.
- **Whether a sidecar running another image under `--auto` says one line of its own** (Known limits).
- **Whether the four modules `Presets` neither builds nor asks are compared with its bounds by a test** (Known limits).
- **Whether `vespera.docling.image` and the other keys taken by `@Value` read as §2 does.** No record has made them presets a label or a publish must outlive.
