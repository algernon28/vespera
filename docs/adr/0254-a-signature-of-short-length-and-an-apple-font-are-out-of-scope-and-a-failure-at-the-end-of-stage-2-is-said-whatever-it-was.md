# ADR-254 — A signature of short length and an Apple font are out of scope, and a failure at the end of stage 2 is said whatever it was

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Amends**: [ADR-252](0252-compiled-code-fonts-certificates-databases-and-emails-are-out-of-scope.md) §2, `FONT` rules 1 and 3, which gain the sfnt versions Apple's systems use, and `CERTIFICATE` rule 2, which gains the one-byte form of a length. It carries a note at its head.
- **Applies, and does not amend**: [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7 and [ADR-250](0250-stage-2-writes-the-survivor-list-of-the-files-it-converted-and-did-not-remove.md) §4, which say that the page written at the end of stage 2 is not made to fail the step, by one error line.

## Context

**A signature of fewer than 256 bytes.** ADR-252 recognises CMS signed data with no content when its outer sequence opens `30 82`, `30 83` or `30 80`. DER writes the length of a sequence of 128 to 255 bytes as `81` and one byte. A detached signature with few or no certificates opens `30 81 xx`, so it is recognised or not by its size: the same kind of file is left out when it is large and sent to the converter when it is small.

**A font made for Apple's systems.** An sfnt opens with a version, and ADR-252 knows two: `00 01 00 00` and `OTTO`. The version of an Apple TrueType font is `true`, and of an Apple PostScript-flavoured sfnt `typ1`. A web font wraps an sfnt and names its version at offset 4; the flavour `true` is as legal there as the others. Such a file is sent to the converter, which refuses it.

**An error that is not the database's.** ADR-175 §7 and ADR-250 §4 say that the page written at the end of the step is said to have failed in one error line and none is thrown. The two listeners that write a page there caught `IOException` and `DataAccessException` only. Any other failure — the lookup of the run, a row the page cannot show, a null — came out of `afterStep`, where Spring Batch logs it with a stack trace and skips the listeners registered after it, the circuit breaker among them.

## Decision

### 1. Signed data with no content is told in three encodings of its length

`30` at offset 0, then `81` and one byte, `82` and two bytes, `83` and three, or `80`; then at once the eleven bytes of the type of signed data, at offset 3, 4, 5 or 2. The rest of ADR-252 §2 `CERTIFICATE` rule 2 stands: the empty content is looked for in the prefix, and a signed envelope that carries its document is not taken.

### 2. An sfnt opening with `true` or `typ1` is a font

`FONT` rule 1 reads `00 01 00 00`, `OTTO`, `true` or `typ1` at offset 0, and the header must agree with itself as before. Rule 3 reads a web font whose flavour at offset 4 is `00 01 00 00`, `OTTO` or `true`. A collection is as it was. A text that opens with the word `true`, and bytes that open with the letters of a word, are no font: the three numbers after the count of tables must be the ones the count gives.

### 3. The two listeners say a failure of any kind

`ReviewListListener` and `SurvivorListListener` catch `RuntimeException` where they caught `DataAccessException`, in the count, in the write and in the lookup of the run. A failure is said in one error line that names what failed and the place of the page, and none is thrown. The lookup of the run, which sat outside the `try`, has its own line: *"the run of the files that … could not be found, and the list of them could not be written to …"*. The page before stays whole, as for a failure of the database.

### 4. Run ids

`BrokenCheck` is in `corpus`: stage 1 and every stage after it move, as ADR-252 §9 states. The two listeners are in `pipeline`, which no stage's version names (ADR-226). No schema, cache key or verdict changes: a file now recognised is left out under the same `out-of-scope` verdict and reason as one of its kind already was.

### 5. What is left as it is

- **A zip archive of documents that holds a manifest is compiled code, a file named `.eml` or `.msg` is an email, and `!<arch>` is a static library.** ADR-252 decides each and gives the cost it accepts. A lower-case `meta-inf/manifest.mf` is not looked for: the entry names a jar tool writes are exact.
- **A table count of 4096 or more.** The search range of an sfnt header is a 16-bit number and holds 16·2^*e* only up to *e* = 11; no font has more tables, and a file whose header cannot agree with itself is not taken for one.
- **The survivor list's count is taken by a read of its own.** The page states how many survivors it lists before the first row, and `ledger` and `extraction` may not be joined by one statement (ADR-209), so the count and the rows are two passes over the survivors, as ADR-250 §3 has them. Avoiding the second pass means holding the rows in a file or putting the count after them; each changes the page and is a decision of its own.

## Consequences

- A signature and an Apple font are left out by what they are, whatever their size or name.
- The end of stage 2 cannot fail a step, nor skip the listeners after it, for a reason of the page's.

## Tests

| Class | What it holds |
| --- | --- |
| `corpus.CompiledCodeFontCertificateAndDatabaseDetectionTest.aSignatureWhoseLengthIsOneByteIsRecognised` | signed data with no content whose outer length is written `81` and one byte is `CERTIFICATE`; an envelope of that form holding a PDF is not |
| `corpus.CompiledCodeFontCertificateAndDatabaseDetectionTest.anAppleFontIsAFont` | an sfnt opening `true` or `typ1`, and a web font of flavour `true`, are `FONT`; a header that disagrees, text opening with `true` and bytes opening with `typewriter` are not |
| `pipeline.SurvivorListListenerTest.aReadThatFailsForAnyReasonLeavesThePageBeforeAndIsNotThrown`, `…aRunThatCannotBeFoundIsSaidAndNotThrown` | an `IllegalStateException` in the count, and in the lookup of the run, is said in one error line and not thrown; the page before stands whole |
| `pipeline.ReviewListThatFailsPartwayLeavesThePreviousOneTest.anErrorOfAnyKindLeavesTheReviewListBeforeAndIsNotThrown` | the same for the review list |

## What this does not decide

- Whether the survivor list's heading states a count, or the count is read by a pass of its own (§5).
