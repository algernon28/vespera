# ADR-252 — Compiled code, fonts, certificates and detached signatures, databases and emails are out of scope, and a file with no signature earns no verdict

> **Partly amended — see [ADR-254](0254-a-signature-of-short-length-and-an-apple-font-are-out-of-scope-and-a-failure-at-the-end-of-stage-2-is-said-whatever-it-was.md).** Of §2: `FONT` rule 1 also reads `true` and `typ1` at offset 0 and rule 3 a web font of flavour `true`, and `CERTIFICATE` rule 2 also reads signed data whose outer length is written `81` and one byte.

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13.
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md) and [ADR-168](0168-videos-are-out-of-scope-and-stage-1-recognises-one-by-its-container-signature.md) did. Five kinds of file join `corpus`'s `OutOfScope`, under the same `out-of-scope` verdict and in the same place in stage 1's first pass. The vocabulary stays at nine values.
- **Amends**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md), its sentence *"No subtype is minted for `.msg`"*, and its list of the names that narrow plain text, which gains `.eml`. It carries a note at its head, and its own text is left as written. Its governing rule stands and is applied here.
- **Keeps**: [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). A file whose bytes match nothing still earns no verdict (§5).
- **Keeps**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md). Every new value is posted under the name its files were posted under before. The naming scheme stays at version 1 and the extractor identity does not change.

## Context

Stage 1 leaves out three kinds of file for what they are: a spreadsheet, a BMP image and a video. Every other intact file it does not recognise as a log or as text too large goes to stage 2, and stage 2 sends it to the converter. A file the converter does not read comes back refused, earns `extraction-failed`, and is listed by path on the review list.

Most of what stage 2 fails on is not a document. The measurements, on a corpus where 10,457 file occurrences entered stage 2:

| | File occurrences |
| --- | --- |
| earned `extraction-failed` | 3,924 |
| of those, refused by the converter's policy under the neutral name, `document.bin` | 3,740 |
| the converter's Markdown backend could not be initialised | 143 |
| an unexpected error on opening | 25 |
| other reasons | 16 |

The 3,740 refused, by the ending of the file's name:

| Endings | File occurrences |
| --- | --- |
| `.P3A`, `.P3L`, `.P3P`, `.P3S`, `.P3O`, `.P3K`, `.P3T`: package files of a payment terminal | 1,573 |
| `.DGN`, `.LGN`, `.PGN`, `.SGN`: signature and log files of the same terminals | 552 |
| compiled code and what a build leaves: `.dll`, `.jar`, `.aar`, `.out`, `.o`, `.LIB`, `.axf`, `.exe`, `.class`, `.h` and others | 412 |
| `.msg` | 280 |
| `.txt`, `.bak` | 50 |
| `.P7S`, `.crt` | 28 |
| the remainder, sampled and not counted out: `.mpp`, `.accdb`, `.ttf`, `.dat`, `.data`, `.pf`, `.cpl`, `.bfc`, `.CDR` among them | about 845 |

In all, 290 names ending `.msg` were on the review list: the 280 above, and 10 among the unexpected errors on opening.

Two things in the code explain the table.

- **The neutral name is what an unrecognised file travels under.** `DoclingClient.extensionFor` posts `UNRECOGNISED`, `ZIP_CONTAINER` and an `OLE_COMPOUND` file with no subtype as `document.bin`, so that the converter's own reading of the bytes decides (ADR-100). Where that reading finds nothing it converts, the answer is the policy refusal. An executable, an object file, a font, a binary certificate and an Access database are `UNRECOGNISED`; a Java archive is a `ZIP_CONTAINER`; an Outlook message and a project plan are `OLE_COMPOUND` with no subtype.
- **Text is recognised as UTF-8 only, and from the first 512 bytes only.** `BrokenCheck.decodesAsText` takes a prefix with no zero byte that decodes as UTF-8, or one opening with a UTF-16 or UTF-32 byte-order mark. Text in an older single-byte encoding is therefore `UNRECOGNISED` where a byte above ASCII falls in the prefix, and `PLAIN_TEXT`, posted as `document.md`, where the first one falls after it. That is the likeliest reading of the 50 text-named refusals and of the 143 Markdown-backend failures, and it is not measured (*What this does not decide*).

## Decision

### 1. The rule

**A file is out of scope for its kind where it is of a kind made for a machine to run, link, render, verify or query, which holds no prose and which the converter does not turn into text; or where it is an email (§4).**

**Such a kind is recognised by its signature**: bytes at fixed places that a reader of the format must itself read, found in the 512 bytes stage 1 already reads, or, for a zip container, a part named in the central directory stage 1 already opens. Each is a `DetectedFormat` of its own, for the reason ADR-167 gave for `BMP`: the bytes alone decide it. The name decides none of them (ADR-094).

**A signature that opens with letters is tested only once the prefix has failed to decode as text**, as ADR-168 placed its two weakest rules. What decodes as text is text, whatever it opens with. Every signature below except the zip container's is tested there.

**Each rule is written so that where it is unsure the file stays what it is today.** A signature of two or four bytes removes a file here, where under `IMAGE` it removed none, so each carries a second structural condition, as ADR-167's did, except the four Mach-O magic numbers and `CA FE BA BE`, which no document format opens with and which are tested only where the prefix is not text. A file a rule misses is sent to the converter and is on the review list as before; a file a rule takes wrongly is removed with a reason that says what it was taken for.

### 2. The four kinds the bytes decide

All four are tested after the plain-text branch and before ADR-168's two rules for a QuickTime atom and a transport stream.

**`COMPILED_CODE`** — a program, a library, an object file, or an archive of them. Reason: *"compiled code, and compiled code is out of scope"*. Any one of:

1. **A Windows program or library** (PE: `.exe`, `.dll`, `.cpl`, `.sys`): `MZ` at offset 0, and the little-endian number at offset 60 names an offset at which the four bytes `50 45 00 00` lie wholly inside the prefix. `MZ` alone, or a PE header past the prefix, is not enough.
2. **An ELF file** (`.o`, `.so`, `.axf`, `.out`, a Linux program): `7F 45 4C 46` at offset 0, byte 4 is 1 or 2, byte 5 is 1 or 2, and byte 6 is 1.
3. **A Mach-O file**: `FE ED FA CE`, `FE ED FA CF`, `CE FA ED FE` or `CF FA ED FE` at offset 0.
4. **A Java class file or a Mach-O universal binary**: `CA FE BA BE` at offset 0. The two share the four bytes and both are compiled code, so they are not told apart.
5. **A static library** (`.a`, `.lib`): the eight bytes `!<arch>` and a line feed at offset 0.
6. **A Java or Android archive** (`.jar`, `.war`, `.aar`, `.apk`): a zip container whose central directory holds `META-INF/MANIFEST.MF` or `AndroidManifest.xml`, and holds neither `[Content_Types].xml` nor `mimetype`. Tested in the zip branch, after `word/document.xml` and the workbook parts, by lookups in the directory already read and with no entry inflated.

**How a Java archive is told from a document in the same container.** A Word file is found first by `word/document.xml` and a spreadsheet by its workbook part, whatever else they hold. Every other Office package (a presentation, a Visio drawing, an XPS file) lists its content types in `[Content_Types].xml`; an OpenDocument file and an EPUB book open with an entry named `mimetype`. A zip container holding either is never compiled code. A zip archive that holds none of the four names stays `ZIP_CONTAINER`, posted under the neutral name as before: what it holds may be documents, and stage 1 opens no archive.

**A zip archive of documents that holds a manifest is taken as compiled code, and that is accepted.** One made with the `jar` tool holds `META-INF/MANIFEST.MF`, and one rooted at an Android project holds `AndroidManifest.xml`, whatever else is in it; the directory says no more, and reading further means opening the archive. The converter refused such an archive before, so nothing that was read is lost: what it loses is its line on the review list, and its reason calls it compiled code.

**`FONT`**. Reason: *"a font, and fonts are out of scope"*. Any one of:

1. **A TrueType or OpenType font**: `00 01 00 00` or `OTTO` at offset 0; the big-endian count of tables at offset 4 is at least 1; and the three numbers after it, at offsets 6, 8 and 10, are the ones the header derives from that count: for a count *n* and the largest *e* with 2^*e* ≤ *n*, they are 16·2^*e*, *e* and 16·*n* − 16·2^*e*.
2. **A font collection**: `ttcf` at offset 0, then `00 01 00 00` or `00 02 00 00`.
3. **A web font**: `wOFF` or `wOF2` at offset 0, then `00 01 00 00` or `OTTO`.

**`CERTIFICATE`** — a certificate, or a signature that carries no content. Reason: *"a certificate or a detached signature, and certificates and detached signatures are out of scope"*. Any one of:

1. **An X.509 certificate in its binary encoding**: `30 82` at offset 0, `30 82` at offset 4, and `A0 03 02 01` at offset 8, the tag of its version.
2. **CMS signed data with no content**, a detached signature or a bundle of certificates (`.p7s`, `.p7b`): `30` at offset 0, followed by `82` and two bytes, `83` and three bytes, or `80`; then at once the eleven bytes `06 09 2A 86 48 86 F7 0D 01 07 02`; and the prefix holds an encapsulated content of type data with nothing in it, as `30 0B 06 09 2A 86 48 86 F7 0D 01 07 01`, or as `30 80 06 09 2A 86 48 86 F7 0D 01 07 01 00 00`.

**The empty content is looked for anywhere in the prefix, and the type at its fixed place is what makes that safe.** The thirteen bytes that open rule 2 at a fixed place, `30`, the length form and the type of signed data, are the second structural condition: no document format opens with them, so a file that passes is CMS signed data and nothing else. The empty content only tells a signature with no content from an envelope, and it is searched for, not placed, because the digest algorithms before it vary in number and length. An envelope is taken wrongly only where the first bytes of what it carries hold the same thirteen or fifteen bytes: by chance, which thirteen fixed bytes make negligible, or because what it carries is itself signed data with no content, which is not a document either.

**A signed envelope is not a detached signature.** A `.p7m` carries the document it signs, often a PDF, inside the same structure. Its encapsulated content is not empty, so rule 2 does not take it, and it stays `UNRECOGNISED`.

**`DATABASE`**. Reason: *"a database, and databases are out of scope"*. Any one of:

1. **A Microsoft Access database** (`.mdb`, `.accdb`): `00 01 00 00` at offset 0, then at offset 4 the sixteen bytes `Standard Jet DB` or `Standard ACE DB` and the zero byte that ends them.
2. **An SQLite database**: at offset 0 the sixteen bytes `SQLite format 3` and the zero byte that ends them.

The zero byte is part of each signature: it is what the format writes, and it is what keeps a note that opens with the same words text.

An Access database opens with the four bytes a TrueType font opens with. It is not taken for a font: the bytes after them, read as a font's count of tables and derived numbers, do not agree with one another.

### 3. Why each is not a document

- **Compiled code** is instructions for a processor or a virtual machine. The text in it is symbol names and message strings, and no converter reads it as a document.
- **A font** is outlines and tables for drawing letters.
- **A certificate or a detached signature** is a key, a name and a signature over some other file. The file it signs is elsewhere in the walk and is judged on its own.
- **A database** is rows for a program to query, which is the reason ADR-146 gave for a spreadsheet.

### 4. An email is out of scope, and is told by its name within what its bytes are

**An email is not among the documents this tool reads, whether it is an Outlook message or an internet message.** Its reason, the same for both: *"an email, and emails are out of scope"*.

**What that costs:** an email's text is never read, and neither is anything attached to it that is not also a file of its own in the walk.

**An Outlook message is an OLE compound file named `.msg`**, recorded as a new `DetectedSubtype`, `OUTLOOK_MESSAGE`. It shares its eight opening bytes with a `.doc`, a `.xls`, a `.ppt` and a project plan, and telling them apart by bytes means walking the compound file's directory, which ADR-068 and ADR-094 refused. So the name narrows within the class the bytes fixed, as it does for `.doc`, `.xls` and `.ppt`, and `OutOfScope` acts on the subtype, as ADR-146 does for `LEGACY_SPREADSHEET`. ADR-094 minted no subtype for `.msg` because nothing acted on one; now something does.

**An internet message is plain text named `.eml`**, recorded as a new `DetectedSubtype`, `RFC822_MESSAGE`. Without the subtype it is `PLAIN_TEXT` like any note, and is converted as prose. Its bytes are header lines and a body, and no anchored pattern separates them from a note that begins *"From:"*, so the name narrows, as it does for `.md`, `.csv` and `.adoc`. Where the bytes open as HTML the name is not consulted, as ADR-094 has it. Among the names the order of the tests decides nothing, because a name has one ending: `.eml` is tested after the others, and `.msg` before `.doc`, `.xls` and `.ppt`.

**What follows from a name deciding:**

- An OLE compound file named `.msg` that is not an Outlook message is out of scope by its name.
- A file named `.msg` or `.eml` whose bytes are something else is judged as what its bytes are: text named `.msg` is text, and a PDF named `.eml` is a PDF.
- An email saved under another name is not recognised: its words in a `.txt` are text and are converted.
- An OLE compound file with no subtype stays in scope. The container is not what is out of scope.

### 5. A file with no signature earns no verdict

**Binary content that matches no signature stays `UNRECOGNISED`, is sent to the converter and is on the review list, as ADR-095 decided.** No rule here reads *"binary, and of no known kind"* as a kind.

The two terminal families in the measurements, 2,125 of the 3,740, are of this sort as far as this record knows: no signature of theirs is known, so none is written. They are untouched by this record.

### 6. What the operator sees

| | Written by | Where it is seen |
| --- | --- | --- |
| `broken` | stage 1 | the verdict and its reason |
| `out-of-scope` | stage 1 | the verdict and its reason; the format-mix page's count of files left out, and its row for the kind; one count in the invocation account |
| `extraction-failed` | stage 2 | the verdict and its reason; the review list, by path |

**The format-mix page already counts what is out of scope by kind, and is extended.** Each of the four new formats gets a row: *"Compiled code"*, *"Fonts"*, *"Certificates and detached signatures"*, *"Databases"*. The two subtypes are named among the finer labels taken from filenames: *"Outlook messages"* and *"internet messages"*. Its sentence reads *"Spreadsheets, BMP images, videos, emails, compiled code, fonts, certificates and detached signatures, and databases are out of scope, whatever they hold."* All are counted in *"Files left out as out of scope, and not read any further"*. No new page is written.

**An internet message is counted in the *Text* row and among the files left out, and in neither of the page's two counts within them nor in its timestamp table.** *"Text files left out for their size"* counts a file only where a size rule gave its reason (ADR-171, ADR-178), and an email's reason is its kind. The timestamp table counts the text files read for timestamps, and a file left out for its kind is not read any further, as no other kind left out is; the table is what the log floor is drawn from, and no floor is ever asked of an email. The page's sentence above the table says so: *"Each text file not left out for its kind was read from its start and its end"*.

### 7. Everything is sent to the converter as before

`COMPILED_CODE`, `FONT`, `CERTIFICATE` and `DATABASE` are posted under the neutral name, which each was posted under as `UNRECOGNISED` or `ZIP_CONTAINER`. An `OUTLOOK_MESSAGE` is posted under the neutral name, as an OLE compound file with no subtype is. An `RFC822_MESSAGE` is posted as `document.md` and, over the converter's ceiling, converted in parts, as plain text with no subtype is. No posted name changes, the naming scheme stays at version 1, and `TextParts.RULE` is unchanged. That matters for a seed, which stage 1 never judges, and for a later decision to put a kind back.

### 8. No schema change

`detected_format.format` and `detected_format.subtype` are text with no list of values, so the new values need no column, no table and no schema version. `VerdictKind` is unchanged.

### 9. Run ids, and the ADR-216 check

The change is in `corpus` (`BrokenCheck`, `DetectedFormat`, `DetectedSubtype`, `OutOfScope`), for a reason of its own. It is also in `extraction`: `DoclingClient`'s three switches over the format and the subtype are exhaustive, and `TextParts` names the subtypes it cuts, so each gains the new values. `corpus`'s `BrokenOrOutOfScope` counts a text file as left out for its size only where `OutOfScope.sizeReason` gave the reason (§6). `pipeline`'s `FormatMixReport` changes too, in its rows, its labels, its sentence on what is out of scope and its sentence on which text files were read for timestamps, and no stage's version names `pipeline` (ADR-226).

**Every stage moves.** Stage 1 names `corpus`. Stages 2 to 6b each name `extraction`, and each also follows the run upstream of it.

**What that costs in kind.** Stage 1 checks every file occurrence again. Stage 2 asks the converter nothing about content it already holds an answer for: the extraction cache is keyed on the content hash and the extractor identity, the identity is the sidecar's image and versions and the options sent, and none of them changes. Stage 2 still measures, chunks and shingles each survivor again under its new run, and every later stage recomputes what it keys by run.

## Alternatives rejected

- **Leaving out every binary file of no known kind.** It would take the two terminal families without a signature. It would also take every document in a format stage 1 does not know: text in an older single-byte encoding is `UNRECOGNISED` today (Context), and so are a CorelDRAW drawing and a signed envelope holding a PDF. Those would leave the review list, the one place that shows them by path, with a reason that says nothing of what they are.
- **Recognising a kind by the ending of its name.** ADR-094 does not let a name decide a format. The name narrows only where the bytes cannot say, which is §4's case and no other here.
- **Converting an Outlook message.** The converter reads one when it is posted under its own name, as `docs/research/docling-legacy-office.md` measured; under the neutral name it is refused. That reading drops the Cc line, loses a body held only as compressed RTF, reads no attachment, and its reading of the bytes takes some messages for another Office kind. Naming it would also change the extractor identity, and every conversion already cached would be made again.
- **Telling an internet message by its header lines.** A note or an exported log may open with *"From:"* or *"Subject:"*. An anchored pattern strict enough to exclude those is a parser of the format, and a loose one is the heuristic ADR-094 refused for HTML's other fifteen patterns.
- **One `DetectedFormat` for all four kinds.** The format-mix page would show one count where the operator needs to see which kind arrived in bulk.
- **Calling any of them `broken`.** None is damaged.
- **A profile key for the list.** It is the step ADR-146 deferred, and it stays deferred.

## Consequences

- Stage 2 no longer sends the converter a program, an object file, a Java archive, a font, a binary certificate, a detached signature, an Access or SQLite database or an email, and none of them is on the review list. They are counted by kind on the format-mix page.
- **More than half of the measured refusals remain.** The two terminal families and the other binary files with no signature are sent and refused as before, and stay on the review list.
- A file of one of these kinds that an operator wanted is removed with the rest. The verdict, its reason and the format-mix page say so.
- An email's text, and an attachment that is not also a file in the walk, is never read.
- A zip archive of documents that holds `META-INF/MANIFEST.MF` or `AndroidManifest.xml` is removed as compiled code and leaves the review list (§2).
- A `.jar` built with no manifest, a COFF object file, a certificate in its first version, a private key, a certificate written as letters between `BEGIN` and `END` lines, and a signed envelope are not recognised, and are treated as today.
- A seed of any of these kinds is converted as before. A seed folder may hold one: seeds are checked by `BrokenCheck` for their format and never pass through `OutOfScope`. The converter refuses it or, for an internet message, reads it as text, as it did before.
- `DocumentPicture.listedFor` is left as it is, and answers that the four new formats list pictures. Nothing reaches that answer: it is asked only of a survivor the tree lists, under stage 1's run, and a file of these formats in the corpus is never a survivor. A seed is not listed in the tree.
- `CONTEXT.md`'s entry for *Out of scope*, the stage table in `docs/architecture.md`, and the javadoc of `OutOfScope`, `DetectedFormat.ZIP_CONTAINER`, `DetectedSubtype`, `BrokenCheck.narrowedOleCompound`, `BrokenCheck.narrowedPlainText` and `BrokenCheck.checkZipContainer` name the new kinds.

## Tests

Every file is a few bytes built in the test.

| Class | What it holds |
| --- | --- |
| `corpus.CompiledCodeFontCertificateAndDatabaseDetectionTest.aWindowsProgramIsCompiledCodeOnlyWithItsPeHeader` | a PE file named as a PDF is `COMPILED_CODE`, not broken, with no subtype; `MZ` with no PE header, one whose PE header lies past the prefix, one whose offset field is `FF FF FF FF`, one whose PE header begins at byte 509 and one shorter than 64 bytes are `UNRECOGNISED`; text opening *"MZ"* is text |
| `…anObjectFileAClassAndAStaticLibraryAreCompiledCode` | ELF in both widths and byte orders, the four Mach-O magic numbers, a Java class and a static library are `COMPILED_CODE`; ELF's letters over a class byte of 0 are `UNRECOGNISED`; an archive whose prefix is all text is text |
| `…aCodeArchiveIsCompiledCodeAndADocumentInAZipIsNot` | a zip holding `META-INF/MANIFEST.MF` or `AndroidManifest.xml` is `COMPILED_CODE`; a Word file and a workbook that also hold a manifest stay `WORDPROCESSING` and `SPREADSHEET`; a presentation, with or without a manifest, an OpenDocument file holding one, an EPUB book and a plain zip stay `ZIP_CONTAINER`; a zip of documents holding either manifest is `COMPILED_CODE`, which §2 accepts |
| `…aFontIsAFontOnlyWhereItsHeaderAgreesWithItself` | TrueType of 4 and of 19 tables, OpenType, a collection and both web fonts are `FONT`; a TrueType opening whose derived numbers are zero is `UNRECOGNISED`; minutes opening *"OTTOBRE"* and a note opening *"wOFF"* are text; `wOFF` wrapping neither kind, and minutes opening *"OTTOBRE"* in ISO 8859-1, are `UNRECOGNISED` |
| `…anAccessOrSqliteDatabaseIsADatabase` | Jet, ACE and SQLite files are `DATABASE`, the first although it opens as a TrueType font does; a note opening *"SQLite format 3"* is text |
| `…aCertificateAndADetachedSignatureAreRecognisedAndASignedEnvelopeIsNot` | a binary certificate, and signed data with no content in both encodings of its lengths, are `CERTIFICATE`; a signed envelope holding a PDF, in both encodings of its lengths, signed data naming the type data inside a set (`31 0B`), and a private key are `UNRECOGNISED`; a certificate written as letters is text |
| `…anEmailIsToldByItsNameWithinWhatItsBytesAre` | an OLE compound file named `.msg`, in either case, is `OLE_COMPOUND` with `OUTLOOK_MESSAGE`, and text named `.eml` is `PLAIN_TEXT` with `RFC822_MESSAGE`; each is left out with the email reason; an OLE compound file named `.doc` is `LEGACY_WORD` and kept; text named `.msg`, a web page, a binary file and a PDF named `.eml`, and an email's words named `.txt`, are what their bytes are and are kept |
| `…whatCarriesNoSignatureOfTheFourKindsIsNotLeftOut` | a PDF, an OLE compound file with no subtype, a project plan, a CorelDRAW drawing, a binary file with no signature and text in ISO 8859-1 keep the format they have today, and none is left out |
| `corpus.OutOfScopeTest.leavesOutCompiledCodeFontsCertificatesDatabasesAndEmails` | the five reasons, word for word |
| `corpus.OutOfScopeTest.keepsWhatCarriesNoSignatureOfAKindLeftOut` | `OLE_COMPOUND` with no subtype, `ZIP_CONTAINER` and `UNRECOGNISED` have no reason |
| `pipeline.ByteLevelReductionTaskletTest.removesCompiledCodeFontsCertificatesDatabasesAndEmailsAsOutOfScope` | in stage 1, two identical object files, a font, a certificate, a database, a `.msg` and an `.eml` each earn `OUT_OF_SCOPE` with their reason and none is hashed; a project plan, a note and a binary file with no signature carry no verdict; the page counts seven left out, none of them as a log or as text left out for its size, counts one text file of fewer than ten lines, the note and not the `.eml`, says *"Each text file not left out for its kind was read from its start and its end"*, gives each format its row and each subtype its label, and carries §6's sentence |
| `pipeline.ByteLevelReductionTaskletTest.removesVideosAsOutOfScope` | the page carries §6's sentence |
| `extraction.DoclingClientTest.postsTheKindsAdr252TellsApartUnderTheNamesTheyHadBefore` | the four formats and `OUTLOOK_MESSAGE` are posted as `document.bin`, `RFC822_MESSAGE` as `document.md` and cut in parts over the ceiling as text with no subtype is, and the options still say `naming=1` |

## What this does not decide

- **The two terminal families and any other binary file with no signature** (`.dat`, `.data`, `.bfc`, `.pf`). §5 leaves them as they are. Leaving them out needs either a signature, which the leading bytes the format-mix page lists under *"What could not be recognised"* would show if one exists, or a list of name endings that narrows within `UNRECOGNISED`, which is the operator's list and belongs in the profile.
- **Text in an older single-byte encoding.** Recognising it as text, and sending it so that the converter reads it, is a change to what is brought in, not to what is left out, and it changes what a conversion returns. The cause given in Context is read from the code and is not measured.
- **A Microsoft Project plan, a Visio drawing, a CorelDRAW drawing.** Each may be documentation. They stay in scope, are refused by the converter and are on the review list.
- **Source and header files.** They are text and are converted; whether they are documentation is for relevance to say.
- **A signed envelope.** Reading the document inside a `.p7m` is not decided.
- **A zip archive that holds no manifest.** It stays `ZIP_CONTAINER`; stage 1 opens no archive.
- **Audio**, which ADR-168 left undecided.
- **A page listing by path what was left out of scope**, and **counts by kind in the invocation account**. The account carries one count of `out-of-scope` verdicts and its allow-list is ADR-198's.
- **Who may change the list** (ADR-146's deferred question).
