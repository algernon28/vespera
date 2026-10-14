# ADR-254 — A file whose name ends with an ending the profile lists is out of scope, where its bytes matched no signature

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Extends**: [ADR-252](0252-compiled-code-fonts-certificates-databases-and-emails-are-out-of-scope.md) §5, and answers the first item of its *What this does not decide* with the list of name endings it named, read within text as well as within `UNRECOGNISED` (§3). A file whose bytes match no signature still earns no verdict, unless the operator lists the ending of its name.
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) did. One more reason joins `corpus`'s `OutOfScope`, under the same `out-of-scope` verdict and in the same first pass of stage 1. The vocabulary stays at nine values, and no `DetectedFormat` or `DetectedSubtype` is added.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). The name decides nothing about what a file is. It is read only within the two classes where no signature matched, and a file whose bytes name its kind is judged by them whatever its name (§3).
- **Keeps**: [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). The detected format is recorded as before, and a file left out by its name is counted under what its bytes were.
- **Rests on**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md). The profile gains a key, `outOfScopeNameEndings`, that ships unset, typed by a value type of its own with ADR-120's three states.
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and ADR-171 §4. Stage 1's configuration consumed records the list (§6).

## Context

ADR-252 left out compiled code, fonts, certificates, databases and emails by their signatures. It left two families of payment-terminal files as they were, because no signature of theirs is known: package files named `.P3A`, `.P3L`, `.P3P`, `.P3S`, `.P3O`, `.P3K` and `.P3T`, and signature and log files named `.DGN`, `.LGN`, `.PGN` and `.SGN`. They were 2,125 of the 3,740 refusals ADR-252 measured. With ADR-252's kinds left out, 3,219 file occurrences of that corpus earn `extraction-failed`.

The format-mix page groups the files stage 1 could not recognise by their first four bytes. On that corpus its largest groups open with ASCII digits:

| Begins with | As text | File occurrences |
| --- | --- | --- |
| `39 39 39 30` | `9990` | 4,753 |
| `39 39 39 32` | `9992` | 2,089 |
| `30 31 30 31` | `0101` | 2,004 |
| `38 35 34 31` | `8541` | among the large groups, not counted out |
| `38 32 32 37` | `8227` | among the large groups, not counted out |
| `31 2E 31 3D` | `1.1=` | 219 |

They read as fixed-width records in a single-byte encoding: digits and letters, with a byte above ASCII in the first 512 bytes that does not decode as UTF-8. `BrokenCheck` therefore finds them `UNRECOGNISED`, stage 2 sends them under the neutral name, and the converter refuses them. The page counts bytes and not names, so it cannot say which group is which family. Nothing at a fixed place in them is a signature a reader of the format must read, and ADR-252 refused to read *"binary, and of no known kind"* as a kind.

What they are is known from where they came from, not from their bytes. That is the case ADR-252 kept for the operator: a list of name endings, in the profile.

Two facts about the code shape the rule.

- **Text is recognised as UTF-8 only, from the prefix** (ADR-252, Context). A file of these families whose prefix is all ASCII is `PLAIN_TEXT` already, and a later decision to recognise single-byte encodings as text would move the rest there. A list read only within `UNRECOGNISED` would stop leaving them out on that day.
- **A profile value stage 1 reads is part of its run id only through its configuration consumed.** `ByteLevelReductionTasklet.ConfigConsumed` holds the log floor, and a run id is derived from the configuration consumed, the implementation version, the walk and the upstream runs (ADR-048, ADR-058). Stage 1's implementation version is `corpus`'s alone, and `profile` is not part of it. A value stage 1 acts on that its configuration consumed does not hold changes what stage 1 decides under a run id that does not change.

## Decision

### 1. The rule

**A file whose bytes matched no signature, and whose name ends with one of the endings the profile's `outOfScopeNameEndings` lists, is out of scope.** Stage 1 verdicts it `out-of-scope` in its first pass, beside the kinds `OutOfScope` names, so it is never hashed, never converted and never read further.

*Matched no signature* means the detected format is `UNRECOGNISED` or `PLAIN_TEXT` (§3).

### 2. The key

`outOfScopeNameEndings` holds one line of text under `value`, as every key of the profile does, with `provenance` beside it.

- **The endings are separated by spaces, commas, or both.** The value, trimmed, is split at every run of spaces and commas, and a piece the split leaves empty, as before a leading comma, is dropped: `, .P3A` holds one ending, and `, ,` holds none (§5). The value stays one string, so the file keeps the shape ADR-061 gave every key; `keysAsWritten` and the deliverable's index state it as written (ADR-186), and census writes it back untouched (ADR-062). A YAML list under `value` does not load: `ProfileStore` refuses it with the line it gives any key that is not in the shape a key takes.
- **Each ending begins with a dot**, holds at least one character that is not a dot, and holds no `/`, `\`, `*` or `?`.
- **An ending matches the end of the file's name**, the last element of its path, in any case: `.P3A` matches `TERM01.P3A` and `term01.p3a`, and `.tar.gz` matches `dump.tar.gz` and not `dump.gz`. Case is folded with `Locale.ROOT`, on the ending and on the name alike.
- **There is no wildcard.** An ending holding `*` or `?` is unreadable (§5), so that an operator who writes `.P3*` is told rather than matching nothing. Seven endings are seven entries.

### 3. The name decides only where no signature did

ADR-094 lets a name narrow only within a class the bytes fixed, and never re-open or override one. In two classes no signature matched: `UNRECOGNISED`, where the bytes matched nothing, and `PLAIN_TEXT`, which is *"no signature matched and the prefix decodes as text"*. In both the bytes have said all they can. In every other class a signature has said what the file is.

**So the list is read for a file detected `UNRECOGNISED` or `PLAIN_TEXT`, and for no other.** A PDF, an image, a BMP image, a video, a Word file, a workbook, a zip container, compiled code, a font, a certificate, a database and an OLE compound file are judged as their bytes say, whatever their name ends with. A file the cross-format floor stopped is `broken` before the list is read, and a broken file of any class stays `broken`: ADR-094's third limit holds.

**Text is within the list's reach for two reasons.** A file of these families whose prefix is ASCII is text today, and the rest may be recognised as text by a later decision on encodings (Context): the operator's list names these files as not documents whatever they decode as. And a name already leaves text out: ADR-252 §4 does so for `.eml`.

**What that costs.** A text file whose name ends with a listed ending is left out whatever it says. A list holding `.txt` leaves out every text file named so. The list is the operator's, and the format-mix page says what it holds and how many files it left out (§7).

### 4. Where it falls in the first pass

1. `broken`, as before.
2. The kinds `OutOfScope.reasonFor` names. A listed `.eml` is still left out as an email.
3. The list, for `UNRECOGNISED` and `PLAIN_TEXT`.
4. For text not left out, the log rule and then the size rule (ADR-171, ADR-178).

A text file left out by its name is not read for timestamps. It is counted neither among the logs, nor among the text files left out for their size, nor in the timestamp table, as a file left out for its kind is not (ADR-252 §6).

### 5. Unset, answered and unreadable

ADR-120's three states, with the parse in the key's own value type, `NameEndingsValue`, as `NumericValue` holds the parse of a number:

- **Unset**, the key absent or its value blank: no ending is listed, and nothing is left out by its name. It ships unset.
- **Answered**: the endings, folded to lower case, each once, in sorted order. That is the form stage 1 matches by and records (§6).
- **Unreadable**: the value, split, holds no ending, or holds a piece that is not in §2's form, such as `.`, `..`, `P3A`, `.P3*`, `.P3?` or `dir/.P3A`. One such piece makes the whole value unreadable and none of it is applied: a list half applied is a list nobody wrote. It is treated as unset, and the closing line names the key and quotes back what is written, as it does for the log floor: *"Every run value is set, but outOfScopeNameEndings reads "…", which is not a list of name endings each beginning with a dot, so this run ignored it and left nothing out for the ending of its name. Next: write the endings into outOfScopeNameEndings in profile.yaml, each beginning with a dot, with no wildcard, and separated by spaces, and run again."*

### 6. Stage 1's identity records the list

`ConfigConsumed` gains a fourth component, `outOfScopeNameEndings`: the answered endings in their canonical form, as a JSON array, and `[]` where the key is unset or unreadable. Where the profile sets neither key, stage 1's configuration consumed reads:

`{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null,"outOfScopeNameEndings":[]}`

- **A list in another order, case or spacing, or with an ending repeated, names the same run.** `.SGN, .p3a` and `.P3A .sgn .sgn` both record `[".p3a",".sgn"]`.
- **Adding or removing an ending names another run**, and every later stage follows it, since each names the run upstream of it. Putting the earlier list back arrives at the earlier run, whose verdicts are still recorded (ADR-156, ADR-229).
- **Unset and unreadable both record `[]`**, as an unset and an unreadable log floor both record `null`. The field is written when empty, as the log floor's `null` is, rather than left out.

### 7. What the operator sees

- **The verdict reason**: *"a file whose name ends with .P3A, an ending outOfScopeNameEndings in profile.yaml lists as out of scope"*. The ending is spelt as the file's name spells it; where two listed endings match, the longer is named.
- **Stage 1's log line**: *"out of scope: "* and the reason, as for every out-of-scope file.
- **The format-mix page.**
  - Its count of files left out as out of scope includes these, and the sentence after it counts them apart: *"Of those, logs: L, text files left out for their size: S, and files left out for the ending of their name: N."*
  - A paragraph after it says what the list holds. Where it is answered: *"outOfScopeNameEndings in profile.yaml lists these name endings: .dgn, .p3a. A file of no known kind, or a text file, whose name ends with one of them, in any case, is out of scope whatever it holds. A file whose content says what it is, a PDF for one, is kept whatever its name."* The endings are the canonical ones, escaped as page text. Where it is unset or unreadable: *"No name endings are listed, so no file was left out for the ending of its name. Listing them in outOfScopeNameEndings in profile.yaml leaves out every file of no known kind, and every text file, whose name ends with one."*
  - The sentence above the timestamp table reads *"Each text file not left out for its kind or for the ending of its name was read from its start and its end"*.
  - The table of what each file was found to be, and the table of leading bytes under *"What could not be recognised"*, count a file left out by its name under what its bytes were, as ADR-095 counts every removed file. A group the list empties stays on the page, and the count of files left out for the ending of their name says how many went.
- **The profile**: after writing the page, stage 1 points the key's measurement at `format-mix.html`, as it does the log floor's. The page is the evidence beside the value; `provenance` holds the operator's reason.
- **The closing line**, where the value is unreadable (§5).

### 8. Seeds and the converter are untouched

Seeds never pass through `OutOfScope` (ADR-146, ADR-252), so the list leaves out no seed. Nothing sent to the converter changes: no detected format, subtype, posted name or naming version, and the extractor identity is the same.

### 9. No schema change

The verdict and its reason are written as every `out-of-scope` verdict is, and `detected_format` is unchanged.

### 10. Run ids, and the ADR-216 check

The change is in `corpus` (`OutOfScope`, `BrokenOrOutOfScope`, `FormatMix`) and in `profile` (`Profile`, `ProfileValue` and the new `NameEndingsValue`), each for a reason of its own. It is also in `pipeline` (`ByteLevelReductionTasklet`, `FormatMixReport`, `NextAction`), which no stage's version names (ADR-226).

**Every stage moves, whatever the profile holds.** Stage 1 names `corpus`, and its configuration consumed gains a field. Stages 2 to 6b each follow the run upstream of it, and generation's version also names `profile`.

**What that costs in kind.** Stage 1 checks every file occurrence again. Stage 2 asks the converter nothing about content it already holds an answer for: the extraction cache is keyed on the content hash and the extractor identity, and neither changes. Stage 2 still measures, chunks and shingles each survivor again under its new run, and every later stage recomputes what it keys by run.

## Alternatives rejected

- **The list within `UNRECOGNISED` only**, the form ADR-252 named. It misses a file of these families whose prefix is ASCII, and it would stop leaving out the rest on the day their encoding is recognised as text.
- **The list within every class.** A name would override bytes that named a kind, which ADR-094 forbids: a PDF named `.DGN` would be left out.
- **Wildcards or patterns.** `.P3*` saves six entries and costs a matcher, and what it reaches is harder to read on the page than a list. An entry holding a wildcard is unreadable, so that writing one is not silent.
- **A YAML list under `value`.** Every key holds one line of text, and `keysAsWritten`, the deliverable's index, census's merge and `docs/check-claims.mjs` all read it so. A list would be the profile's first value of another shape, and would say nothing a line cannot.
- **A subtype for a listed ending.** The list names no kind, so a subtype would claim one.
- **A rule on the bytes for binary content of no known kind.** ADR-252 refused it, for what it would take besides these files, and that stands.
- **Making `OutOfScope`'s kinds the operator's to change.** ADR-146 deferred that, and it stays deferred: the kinds stay code, and this list adds to them and takes none away.

## Consequences

- With the two terminal families' endings listed, their files of no known kind and their text files are left out in stage 1, are counted on the format-mix page, and are not on the review list. How many of the 2,125 that is cannot be read off the page, which does not tie bytes to names.
- A document of no known kind, or a text file, whose name ends with a listed ending is lost to the deliverable, and its verdict says why.
- A file named with a listed ending whose bytes name its kind is judged by them, so a listed family's file in a recognised format stays in scope.
- Census writes the key, unset, into every profile it saves, and the deliverable's index states it as written.
- `CONTEXT.md`'s entry for *Out of scope*, README's table of values, `docs/architecture.md`'s stage table, and the javadoc of `OutOfScope`, `BrokenOrOutOfScope`, `FormatMix` and `ByteLevelReductionTasklet.ConfigConsumed` name the key. `docs/check-claims.mjs` reads the new value type among the profile's value types, so that the key README names is found on the record.
- The existing callers of `BrokenOrOutOfScope.verdictSurvivors`, in `BrokenOrOutOfScopeTest` and `StageOneHoldsBoundedMemoryTest`, pass an empty list.

## Tests

Every file is a few bytes built in the test, and every profile is YAML written by the test.

| Class | What it holds |
| --- | --- |
| `profile.OutOfScopeNameEndingsInTheProfileTest.aFreshProfileCarriesTheKeyUnset` | census writes `outOfScopeNameEndings` into a fresh profile, unset, and the deliverable's index states it as empty |
| `…theEndingsAreKeptAsWritten` | a value of mixed case, spaces and commas loads, is stated as written, and is written back untouched |
| `…aYamlListUnderTheValueIsRefusedNamingTheKey` | a YAML list under `value` fails the load with the line naming the key and the shape a key takes |
| `pipeline.NextActionTest.anUnreadableListOfNameEndingsIsReported` | `.`, `...`, `P3A`, `.P3A P3L`, `.P3*`, `.P3?`, a path with either separator and `, ,` each make the closing line name the key, say an ending begins with a dot, and quote the value back, in one line |
| `pipeline.NextActionTest.aReadableListOfNameEndingsIsNotReported` | one ending, endings of mixed case, endings separated by a comma with or without a space, `.tar.gz`, and a value with spaces around it are not reported |
| `pipeline.OutOfScopeNameEndingsTaskletTest.leavesOutAFileOfNoKnownKindWhoseNameEndsAsListed` | listed endings in any case leave out five files of no known kind, each with §7's reason spelt as its name spells the ending, `.tar.gz` included; two identical ones are not hashed; an unlisted ending, a listed ending followed by another, and `.gz` against `.tar.gz` are kept; all eight stay `UNRECOGNISED` and are counted under *"Of no known kind"*; the page counts five left out for the ending of their name and none as logs or for their size, and lists the canonical endings; the configuration consumed records them; the key's measurement points at the page |
| `…aListedEndingNeverLeavesOutAFileWhoseBytesNameItsKind` | a PDF, a Word file, an OLE compound file and a PNG named with listed endings are kept as what their bytes are; two text files named with listed endings are left out by their name, one of them a log under the floor the profile sets, so neither is counted as a log nor in the timestamp table; the page's sentence above that table is §7's |
| `…withNoEndingsListedNothingIsLeftOutByName` | with no profile, a file of no known kind named `.P3A` carries no verdict, the configuration consumed records `[]`, and the page says no ending is listed and counts none |
| `…anUnreadableListLeavesNothingOutAndIsRecordedAsNone` | with `.P3*`, nothing is left out and the configuration consumed records `[]` |
| `…theRunIsNamedByTheEndingsAndNotByHowTheyAreWritten` | the same endings in another order, case and separation name one run, and an ending added names a second |
| `pipeline.RunIdentityGoldenTest.byteLevelReduction` | §6's configuration consumed, word for word |
| `pipeline.ByteLevelReductionTaskletTest.removesCompiledCodeFontsCertificatesDatabasesAndEmailsAsOutOfScope` | the page's sentence above the timestamp table is §7's |

## What this does not decide

- **Text in an older single-byte encoding**, ADR-252's second undecided item. The list reaches these files whichever of the two classes they are in.
- **Leaving out by name a file whose bytes named its kind.** §3 refuses it here; a case for it would be a record of its own.
- **Leaving out by folder or path**, or by anything in a name but its ending.
- **A page listing by path what the list left out.**
- **Who may change `OutOfScope`'s kinds** (ADR-146).
