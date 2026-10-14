# ADR-235 — A path in the manifest that a spreadsheet would run as a formula is written behind a single quote

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) and [ADR-136](0136-the-angle-bracket-and-the-ampersand-are-backslash-escaped-and-the-heading-becomes-a-fourth-surrounding.md) §5, in one respect: a path in the manifest is written as the archive spells it, but for a leading single quote where its first character would start a formula.
- **Rests on**: `ManifestCsvTest.aPathASpreadsheetWouldRunIsWrittenBehindAQuote`.

## Context

`ManifestCsv.quoted` quoted a path only where it held a comma, a quote, a line feed or a carriage return. A file whose name begins with `=`, such as `=SUM(A1).txt`, was written as `=SUM(A1).txt`, and a spreadsheet opening `documents.csv` reads that cell as a formula and runs it. The paths are the archive's, and the archive is unknown, so its file names are not ours to trust.

## Decision

**A cell of either path column whose first character is `=`, `+`, `-` or `@`, or a tab or a carriage return, is written with a single quote before it**, and is then quoted under RFC 4180 as before where it needs to be (`ManifestCsv.cell`, which is `quoted(shownAsText(field))`). A spreadsheet shows the cell as text and runs nothing. A sign anywhere but first is left as it is. The other columns hold numbers, a content hash and nothing an archive names, and are not touched.

## Consequences

- A parser loading the manifest into a table gets the quote as the first character of such a path, and must take it off to find the file. That is the price of the quote; a path that begins with none of these characters, which is nearly every path, reads exactly as it did.
- **Which run ids move**: the change touches `synthesis`, so arrangement and generation move. The text of the deliverable changes only for a path that begins with one of the six characters.
- No DDL, no table, no index, no cache key.

## Tests

- `synthesis.ManifestCsvTest.aPathASpreadsheetWouldRunIsWrittenBehindAQuote`.
