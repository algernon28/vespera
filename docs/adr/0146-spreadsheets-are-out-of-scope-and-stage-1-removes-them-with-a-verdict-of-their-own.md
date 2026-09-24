# ADR-146 — Spreadsheets are out of scope, and stage 1 removes them with a verdict of their own

- **Date**: 2026-09-24
- **Status**: accepted
- **Amends**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md). The vocabulary gains a ninth value, `out-of-scope`, blocking, written by stage 1. ADR-057 anticipated exactly this route: a value is added in a pull request that carries an ADR.
- **Builds on**: [ADR-145](0145-a-tables-cells-are-extracted-text-read-once-in-docling-reading-order.md), which made spreadsheets survive stage 2 and so made their cost visible. It still governs every table inside a document that is in scope.
- **Builds on**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). Stage 1 recognises an OOXML workbook from the part inside it, the way it already recognised a Word document. An older `.xls` is still recognised by its name, within the compound-file class the bytes fixed.
- **Settles** [#278](https://github.com/algernon28/vespera/issues/278), except its question of who may change the list, which the operator deferred.

## Context

Since ADR-145, a spreadsheet's cells are text, so spreadsheets survive stage 2 and reach every later stage. That is what measured their cost. On the GesPOS corpus, 12 spreadsheets produced 738 of 2,216 chunks and most of stage 5c's 20 minutes, and none scored above the baseline's q3 (0.651). On the legacy corpus, 132 of its 158 files are `.xls`. Those are also most of what adding LibreOffice would buy.

A spreadsheet is data more often than prose. The SLA reports are about 1.4 million characters each of timings; `Merchants.xlsx` is a list. Some spreadsheets do carry content an operator might want, such as `Mappatura - GesPos.xlsx`, a mapping. Leaving them out by kind loses those as well, and the operator accepted that trade-off. They proposed leaving spreadsheets out now and making the list configurable later.

The full KT PERIN corpus holds 132 `.xls`, 114 `.xlsx` and one `.xlsb`. Every `.xlsx` carries `xl/workbook.xml` and the `.xlsb` carries `xl/workbook.bin`, so stage 1 can recognise all of them from their bytes. The corpus holds no `.ods`. It also holds 73 `.csv`, 2.6 MB between them with a median of 3 KB.

## Decision

**A spreadsheet is out of scope, whatever it holds.** It is removed by stage 1, which is the first stage that knows what a file is, and the cheapest place to stop it, before anything hashes, converts, shingles or embeds it.

**Stage 1 recognises two kinds of spreadsheet:**
- **A zip container holding `xl/workbook.xml` or `xl/workbook.bin`** is a new `DetectedFormat`, `SPREADSHEET`. Before this decision it read as `ZIP_CONTAINER`, an archive of another kind.
- **An OLE compound file named `.xls`** is the `LEGACY_SPREADSHEET` subtype stage 1 already records.

**It earns a ninth verdict, `out-of-scope`.** None of the eight existing verdicts is true of a spreadsheet:
- it is not `broken`;
- it did not fail extraction;
- it is not degenerate or redundant;
- it was never scored below a threshold.

A verdict that said any of those would be a false statement about the file. `out-of-scope` says what is true: this tool does not read this kind of file. Its reason reads *"a spreadsheet, and spreadsheets are out of scope"*.

**It is applied in stage 1's first pass, beside `broken`.**
- A broken spreadsheet earns `broken`, since damage is a statement about the file and comes first.
- An intact spreadsheet earns `out-of-scope`.
- Both are removed before the duplicate pass, so a spreadsheet is never hashed. Two identical workbooks are each out of scope in their own right.

**The list is a code default, in one place: `pipeline`'s `OutOfScope`.** A profile key for it is the later step the operator named. It would read the same question, so nothing here has to move when it comes.

**A comma-separated file stays in scope.** It is text, it is cheap, and the corpus's 73 of them are 2.6 MB in all. It is also the one tabular format stage 1 recognises only by name.

**The seed side is unchanged.** Seeds do not pass through stage 1. A spreadsheet an operator put in the seed folder is one they vouched for, and it is converted as before.

**Docling is sent a spreadsheet exactly as before.** `SPREADSHEET` maps to the same neutral name `ZIP_CONTAINER` did. The extractor identity is therefore unchanged and no cached conversion goes stale. That matters for a seed spreadsheet, and for a later decision to put spreadsheets back.

**The format-mix page states the count:** *"Files left out as out of scope, and not read any further: N"*. Spreadsheets also get a row of their own in its table.

## Consequences

Measured on 2026-09-24 against the conversions each corpus had already cached, from fresh ledgers:

| | GesPOS, ADR-145 | GesPOS, this decision | legacy, this decision |
| --- | --- | --- | --- |
| out-of-scope | — | 20 (12 `.xlsx`, 7 `.xls`, 1 `.xlsb`) | 132 `.xls` |
| scored | 78 | 65 | 23 |
| relevance median / q3 | 0.588 / 0.654 | 0.587 / 0.664 | 0.734 / 0.775 |
| chunks | 2,216 | 1,406 | 1,345 |
| shingles | 633,024 | 75,637 | 43,633 |
| stage 5c | 20 m 26 s | 3 m 24 s | 3 m 12 s |
| whole run | about 26 min | 3 m 34 s | 3 m 19 s |

**The cost ADR-145 added comes back out, and the rest of ADR-145 stays.** GesPOS scores 65 documents: the 68 of the pre-ADR-145 baseline, less `ERI-GESPOS-PROD-Audit-v2-dt-sg.xlsx`, the one spreadsheet that had survived it, and less two `.docx` that the tables now show to be near-duplicates of their neighbours (ADR-145, Consequences).

**It shrinks the LibreOffice question to the 19 `.doc` and 4 `.ppt`.** Their scores are unchanged by this decision. The `.doc` median is 0.736, 13 of 19 score at or above the GesPOS q3, and all 4 `.ppt` do. Converting the 132 `.xls` would now buy nothing, since they are out of scope before conversion.

**A spreadsheet an operator would have wanted is removed with the rest, and the removal is visible.** The verdict, its reason and the format-mix count all say so. What brings one back is the configurability this decision defers.

**A working directory scored under ADR-145 keeps its spreadsheet scores until stage 1 runs again.** Stage 1's module changes, so the next invocation mints a new stage-1 run. That run writes `out-of-scope`, and every later stage runs again downstream of it.

**The vocabulary is nine values.** `VerdictKindTest` pins that count, and the next addition needs an ADR of its own, as this one did.
