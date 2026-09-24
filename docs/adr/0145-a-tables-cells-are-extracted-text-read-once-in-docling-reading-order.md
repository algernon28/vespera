# ADR-145 — A table's cells are extracted text, read once, in Docling's reading order

- **Date**: 2026-09-24
- **Status**: accepted
- **Replaces**: the reading of "extracted text" recorded in `ExtractionOutputText`'s javadoc for [#50](https://github.com/algernon28/vespera/issues/50): *"Reads only `document.json_content.texts[].text` — table cell text, picture captions and layout are not part of 'extracted text' here."* No ADR carried that reading. It was one class's, and two other readers had adopted the same slice independently.
- **Keeps**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md)'s tier-1 floor exactly as it is. What changes is the text it measures.
- **Rests on**: a re-run of the GesPOS corpus on 2026-09-24 against the extraction cache of the 2026-09-23 baseline, so no document was converted again. The numbers are under §Consequences.
- **Settles** [#275](https://github.com/algernon28/vespera/issues/275).

## Context

Three classes read Docling's response, and all three read `texts[]` alone:

- `ExtractedText` feeds the metrics and the no-text floor;
- `ExtractionOutputText` feeds the shingler and the seed pass's usability check;
- `DoclingDocumentTexts` feeds the chunker and a document's title.

A spreadsheet converts to tables and nothing else, so its `texts[]` is empty and tier 1 removed it as `degenerate-output`. On the baseline that was 11 of 12 `.xlsx`, 2.4 million characters of cells among them, and on the legacy-only run it was all 129 `.xls` that converted. Every other document was compared, scored and summarised without its tables.

#275 counted 128,375 dropped table characters in the `.docx` files. That overstates the `.docx` loss. A Word table whose cells hold paragraphs lists those paragraphs in `texts[]` too, under a group the cell points at, and they were being read. Measured over the baseline's cache: 3,061 text items sit under a table, and the cell's own text carries 3,060 of them.

## Decision

**One reader, `extraction`'s `DoclingDocumentTexts`, is the only reading of extracted text.** `ExtractedText` builds on it, and `pipeline` reads it through `DoclingDocumentTexts.lines`. `ExtractionOutputText` is removed, so the slice cannot drift apart again between readers.

**It follows Docling's reading order.** It walks the `body` tree and then `furniture`, resolving each `$ref` into `texts`, `tables`, `groups` or `pictures`, and reads a group or a picture through to the text under it. Over the baseline's cache the walk reaches every text item of all 106 documents, and agrees with list order in 104. A response with no `body` is read from `texts[]` in list order, as before.

**A table is read as one item per row, under Docling's `table` label.**

- A row is the text of its cells, in column order, joined by `"; "`. The separator attaches to the word before it, so the metrics' word counts and garbage proxies gain no one-character words.
- Each cell is read once, in the row where it starts, so a spanning cell is not repeated. Blank cells are skipped.
- The table's own children are not read again, since they are the paragraphs its cells already carry.

**Every cell counts, with no cap and no special case for spreadsheets.** A cap would be a tuned number with nothing measured behind it yet (`CONTEXT.md`, *observe before enforce*). The measurement below is what a future cap would be read off.

**The chunker's identity moves to `docling-hybrid-chunker-v2`,** so no chunk cut from text items alone is reused, and no vector keyed by one is either. Stage 2's and stage 3's runs are new runs because their modules' implementation versions change (ADR-058).

## Consequences

Measured on the GesPOS corpus, against the 2026-09-23 baseline:

| | baseline | with tables |
| --- | --- | --- |
| `degenerate-output` verdicts | 11 | **0** |
| corpus documents scored | 68 | 78 |
| relevance min / q1 / median / q3 / max | 0.455 / 0.554 / 0.585 / 0.651 / 0.835 | 0.455 / 0.558 / 0.588 / 0.654 / 0.831 |
| shingles | 54,462 | 633,024 |
| chunks | 1,423 | 2,216 |
| stages 3–4 wall clock | 15 s | 5 m 23 s |
| stage 5c (embedding) wall clock | 3 m | 20 m 26 s |

**Every spreadsheet now survives stage 2 and is scored, and none of them crowds the top.** The 11 score between 0.511 and 0.651, around the baseline's median and at most at its q3. Mean-of-top-3 scoring does give a larger document more chances (ADR-020), but on this corpus the two largest, the SLA reports at about 1.4 million characters each, score 0.647 and 0.638.

**Documents already scored move little.** Across the 66 scored by both runs, the median change is +0.000, the range is −0.023 to +0.065, the winning seed changes for 16, and 8 of the top 10 stay in the top 10. The largest gains are specifications whose substance is tabular, such as `CreazioneTemplate_PAXStore` (0.602 → 0.667) and `EsitiTelegestione_CARD_ESITI_TLG_v2_1.pdf` (0.651 → 0.708).

**Stage 4 decides better, because it can now see the tables.**
- Among the five `WSDLGesPOS` versions, the survivor is now the newest, `1_14`, where it was `1_11`.
- `EsitiTelegestione_CARD_ESITI_TLG_v2_2.docx` is now recognised as a near-duplicate (0.833) of its own PDF, which its prose alone did not show.

**The cost is volume, and it grows with the spreadsheets in a corpus.** The two SLA reports alone produced 738 of the 2,216 chunks. Dense numeric chunks sit close to Ollama's 2,048-token input limit and take about 1.3 s each to embed, against 0.15 s for a median chunk. 807 calls of 2,236 took over a second, and together they account for 17 of the 20 minutes. Only 10 calls were refused and split (ADR-144). The same volume is what grows stage 3's shingle table twelvefold. If a larger corpus makes this cost matter, a cap per table or per document is the lever. It would be set against a measured distribution, as tier 2 is.

**`boilerplateDocumentFrequencyFloor` was calibrated on prose-only shingles,** and its provenance says so. It should be read again off the new distribution before the value is trusted.

**A database written before this change keeps its old verdicts.** A verdict outlives its run (ADR-139, Consequences), so a spreadsheet already marked `degenerate-output` stays removed under the new stage-2 run. Two ways out:
- start again in a fresh working directory, where the extraction cache is the only thing worth keeping and can be carried across;
- or delete the old stage-2 run's `degenerate-output` verdicts and re-run, the retune path the ledger already has.
