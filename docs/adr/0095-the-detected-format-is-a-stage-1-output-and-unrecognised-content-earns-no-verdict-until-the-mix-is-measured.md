# ADR-095 — The detected format is a stage-1 output under the run, and unrecognised content earns no verdict until the mix has been measured

- **Date**: 2026-09-09
- **Status**: accepted
- **Amends**: none (settles the two questions [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) opens: where its answer is kept, and what its last branch is worth)

## Context

[ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) makes stage 1 read the leading bytes of every occurrence and decide from them what the file is. That produces a fact nothing in the system has a place for, and a branch nothing in the system has a rule for.

**The fact has nowhere to live.** `file_occurrence` (`src/main/resources/schema.sql:44-52`) carries `path`, `size_bytes`, `last_modified` and `creation_time` — walk facts and nothing else. Stage 1 writes verdict rows plus `corpus`'s own `content_hash` and `superseded_by`. There is no format column anywhere, and stage 2 currently re-derives the format from the filename — which [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) has just ruled out as a way of deciding what a file is, admitting the name only to narrow within a class the bytes already fixed.

**The branch has no rule.** [Issue #87](https://github.com/algernon28/vespera/issues/87) raised the case directly: `Thumbs.db`, `desktop.ini`, `.DS_Store`, `~$report.docx` Office lock files, `.lnk` shortcuts, stray `.exe`. Each passes stage 1 today, reaches stage 2, costs a real out-of-process Docling conversion — the most expensive operation in the pipeline — and is then discarded as `extraction-failed` or `degenerate-output`. That is exactly the case [ADR-017](0017-the-cascade.md)'s cheapest-filter-first ordering exists to catch.

Under byte-determined dispatch this stops being a separate problem about junk lists and becomes one named branch: the content matched no signature and does not decode as text. **That branch is a great deal narrower than #87's list, and the drafting of any later floor depends on seeing exactly how narrow, so it is set out item by item here rather than gestured at.**

Of the eight things #87 names, the unrecognised branch catches **two and a half**:

| #87's example | Where ADR-094 actually puts it |
| --- | --- |
| `Thumbs.db` | **OLE compound** — a recognised signature class, not unrecognised |
| `~$report.docx` lock file | unrecognised (a short binary owner record) |
| `.lnk` shortcut | unrecognised (a shell link binary, `4C 00 00 00` at offset 0) |
| `.DS_Store` | unrecognised (a Mac buddy-allocator binary) |
| stray `.exe` | unrecognised (`MZ`) |
| `desktop.ini` | **plain text** — it decodes, so it passes |
| `.ini`, `.bak` holding text | **plain text** — same |
| stray `.zip` | **zip container** — a recognised class |

**Two consequences follow, and both bear directly on what a later floor could and could not do.**

First, `Thumbs.db` is not in the unrecognised branch at all: it is an OLE compound file, sharing that signature with every legacy `.doc`, `.xls` and `.ppt`. ADR-094 names that class and narrows it by extension, so the report can separate "legacy Word" from "OLE-shaped material we cannot name" — but a floor over *unrecognised* would never have reached `Thumbs.db` in the first place.

Second, and more consequential: **text-shaped junk is untouchable by any floor over this branch, however the branch is later treated.** `desktop.ini`, `.ini` and `.bak` files holding text decode cleanly and land in plain text with no subtype — which is exactly where a genuine `.txt` document lands. Nothing in ADR-094's rule distinguishes them, because nothing at the byte level does: they are text, and they are text in the same way a memo is. A floor over unrecognised leaves every one of them costing a Docling call, and the mix report cannot even size them, because they are inside a count that is mostly real documents. Anyone reading that report to decide about #87 has to know this, or they will read a small unrecognised count as "there is barely any junk" when what it means is "the junk that is left is the kind this instrument cannot see."

**What has never been measured** is how much of a real archive that branch holds, and what else is in it. #87 says so itself, and it is right: nobody has walked a real archive. `docs/adr/0006-census-measure-before-judging.md` is the posture the whole project takes, and [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md) says which direction an unmeasured guess is dangerous in — over-blocking loses archive, and loses it invisibly.

## Decision

### The detected format is a stage-1 output, keyed by occurrence and run — not a column on the occurrence

`corpus` gains a table of its own, the same shape as `content_hash` and for the same reason ([ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md)):

```sql
CREATE TABLE IF NOT EXISTS detected_format (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    format TEXT NOT NULL,
    subtype TEXT,
    PRIMARY KEY (occurrence_id, run_id)
);
```

**Two columns, because they are two strengths of claim.** `format` is what the bytes said and is never null. `subtype` is what the filename added within a class the bytes had already fixed, is null wherever ADR-094's narrowing does not apply or finds nothing, and is meaningful only relative to its `format` — `LEGACY_WORD` is a statement about an OLE compound file and about nothing else. Folding the two into one value would make every row of the mix report carry an unmarked mixture of a hard fact and a soft one, and would leave a reader no way to ask the hard question on its own. Keeping them apart makes ADR-094's governing rule structural rather than a caveat: nothing can narrow across a class boundary, because a subtype has no meaning without the format beside it.

**The walk/run split decides this, and it decides it against the occurrence column.** [ADR-048](0048-walk-and-run-identity.md): a walk owns file occurrence rows because they are filesystem observations; a run owns rows derived under a configuration. A detected format is not observed — it is *derived*, by an algorithm ADR-094 fixes and a later ADR may change. Add one signature to that list and the same bytes yield a different answer. As an occurrence column that change rewrites history in place, with no way to ask which rule produced which row; keyed by run it is a fresh row set under a new run id, which is [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)'s rule applied without amendment.

**The second reason is cost, and it is decisive on its own.** A walk fact has to be produced by the walk, and census is a stat-only pass — `size`, `last_modified`, `creation_time`, no open. Detection needs an open. Putting one into the walk would change the cost profile of the cheapest thing in the system, for a value stage 1 is about to compute anyway with a file handle already in hand.

**A row is written for every occurrence stage 1 examines, including the ones it verdicts `broken`.** A PDF with no `%%EOF` is still known to be a PDF, and a mix report that dropped the broken files would under-count precisely the formats that fail most.

**An occurrence stopped by the cross-format floor gets its own format value, `FLOOR_STOPPED`, and not `UNRECOGNISED`.** The two are different facts and the draft of this record conflated them. `UNRECOGNISED` means detection ran and the bytes matched nothing; `FLOOR_STOPPED` means the file was empty or would not open, so detection never ran and no byte was ever read. Merging them puts a number that is about damage inside the one count the whole deferral below rests on — the size of the unrecognised branch — and that count exists precisely to be read against a decision to block. An archive with many empty files would inflate it and argue for a floor that would not have touched them.

The row is still written, rather than omitted, so that the mix report's counts reconcile against the occurrences stage 1 examined. What changes is that `FLOOR_STOPPED` is reported on its own line and **excluded from the unrecognised branch's size**, and that the operator can see how much of stage 1's work never got as far as looking. The `broken` verdict these occurrences already carry says which of the two floor conditions it was, in its free-text reason, so the format value does not need to split further (ADR-057).

### Stage 2 is not changed here; it is a named follow-up

`DoclingClient.convert` posts a `FileSystemResource`, so the on-disk filename reaches `docling-serve` and selects its pipeline, and [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md) records that the choice is material — the paginated PDF/image pipeline returns confidence scores, the simple path returns nulls with `unspecified` grades, and tier 2 reads exactly those.

**Making stage 2 send the format the bytes say is a separate ticket, and this is the record saying so explicitly rather than leaving it implied.** It is stage-2 code, with its own test surface and its own ADR-070 interaction; folding it into stage 1's change makes one change two. Stage 2's run already names stage 1's run upstream ([ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md)), so the row is reachable when that ticket is taken.

What it owes, so the ticket can be written without re-deriving it:

1. **Read the detected format for the occurrence under stage 1's run, and derive the multipart part name from it instead of from the path** — so that a PDF reaches the paginated pipeline whatever it is called on disk, and a wordprocessing document reaches the simple one.
2. **Use the subtype where one is present, and the format alone where it is not.** A plain-text occurrence with an `HTML` or `MARKDOWN` subtype should reach Docling as that, because Docling parses their structure; one with no subtype travels as text. An OLE compound file with a `LEGACY_WORD` subtype names a format Docling supports only where LibreOffice is present in the sidecar image (ADR-011), which the ticket has to establish rather than assume.
3. **Weigh the weak signatures before the format starts steering conversion, `BM` first.** ADR-094 bounds `BM`'s risk correctly for stage 1 — an image is never `broken`, so a false reading loses nothing. That bound does not survive this ticket. Once the pipeline choice reads the detected format, a text file opening `BM` stops being mislabelled and starts being *misconverted*: sent to the paginated image pipeline, producing nothing usable, and blocked at ADR-070's tier-1 degeneracy floor. This ticket is therefore where a false `BM` first costs an occurrence, and it must decide what to do about it — ADR-094 names the BMP header's file-size field as the obvious hardening and records why it is not reliable enough to have been imposed at stage 1.
4. **Decide whether it needs the zip branch split further.** ADR-094 records that `xl/workbook.xml`, `ppt/presentation.xml` and ODF's `mimetype` entry separate spreadsheets, presentations and open-document files by bytes at the same cost as the `word/document.xml` lookup, and leaves the split unmade because nothing consumed it. This ticket is the first thing that might. If it takes it, the new values are formats, not subtypes, and no filename is involved.

Until that lands, a mislabelled file is still converted through the pipeline its name chooses, which is today's behaviour unchanged.

### Unrecognised content earns no verdict, and `broken` does not widen

**Stage 1 writes no verdict for the unrecognised branch, none for an OLE compound file whatever its subtype, and none for a zip container that is not a wordprocessing document. The verdict vocabulary stays at eight values; [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md) is untouched.**

Three arguments, in the order they bind.

**`broken` must not widen, because the widening is not reversible in the data.** `broken` means mechanically broken: unreadable, empty, truncated, malformed. A `.lnk` is none of those — it is intact, valid, and simply not a document. Making one kind carry both conditions leaves the ledger unable to tell a truncated PDF from a shortcut except by reading free text, and ADR-057 rejected exactly that shape of value when it ruled that no verdict blocks conditionally: "a verdict that sometimes blocks and sometimes doesn't is two kinds sharing one name." Two conditions sharing one name is the same defect wearing different clothes. Worse, it is retroactive: every `broken` row already written, and every one written by a re-run, becomes ambiguous.

**A ninth verdict is available but would be guessed.** ADR-057 provides the path — a new ADR amending it, editing the enum in a reviewed PR — so this is not a mechanical obstacle. It is an evidential one. ADR-042 rejected a *runtime* registry because drift toward over-publishing is asymmetric; the reviewed-PR path is not a licence to add a permanent, closed-vocabulary, archive-removing value on speculation about an archive nobody has walked.

**The coverage of the signature list is still the load-bearing risk, but it is a smaller one than it looks, and saying so honestly is what this argument turns on.**

The tempting version of this argument is that a legacy `.doc` is an unsignatured binary that falls into unrecognised beside `Thumbs.db`, so blocking the branch might cut an unknown fraction of a genuinely legacy archive. **That is false, and it is recorded here as false because it is the first thing anyone re-opening this decision will reach for.** A legacy `.doc` is not unsignatured: it is an OLE compound file with the eight-byte header [MS-CFB] §2.2 makes mandatory, and it shares that header with `.xls`, `.ppt`, `.msg` and `Thumbs.db`. The problem was never an opaque grab bag. It was one identifiable class holding both documents and junk — a smaller and far more tractable problem — and ADR-094 now names that class and narrows it, so legacy documents are not in the unrecognised branch at all.

What is genuinely left in the branch, with the OLE class and plain text both lifted out of it, is: shell links, Office lock files, `.DS_Store`, executables, BOM-less UTF-16 text (ADR-094's recorded blind spot, and real documents when it occurs), PDFs carrying preamble bytes before `%PDF-`, and every document format nobody has enumerated — `.chm`, `.wpd`, `.djvu`, a proprietary export from an application no one here has heard of. **That is a real residue of real documents, and it is unmeasured. It is no longer plausibly the bulk of an archive.**

So this third argument is the weakest of the three rather than the strongest, and it does not carry the decision on its own. What it still establishes is that the branch is defined by the *absence* of a match against a list this project enumerated by hand, which makes its contents a property of our enumeration rather than of the archive — and that is not a thing to block on before anyone has looked at what falls into it.

**The conclusion is therefore re-checked rather than assumed, because correcting that argument shifts the balance.** The first two are untouched by any of it: `broken` must not widen whatever the branch turns out to hold, because the widening is irreversible in already-written rows; and a ninth verdict is a permanent, closed-vocabulary, archive-removing value that ADR-057 makes reviewable precisely so that it is not minted on speculation. Either would defer the decision alone. And the shift runs *towards* an eventual floor, not away from it: a floor over the unrecognised branch would remove a narrower and less document-shaped set than the branch first appears to hold. But "a floor looks more defensible than it first did" is an argument for measuring and then deciding, not for skipping the measurement — and it is precisely the shape of reasoning ADR-006 exists to refuse. **The conclusion stands, on two arguments that were never at risk and a third that is real but no longer decisive.** What the correction does change is the report: it is what makes the OLE class separable in it, and what forces the two additions the next section records.

**A zip container that is not a wordprocessing document must not block at all**, and this is not a close call: `.xlsx`, `.pptx` and `.odt` are all zips, and `docling-serve` converts them.

### What replaces the block: stage 1 writes a format mix report

**Stage 1 writes a report of what its detection found, under its own run.** Four things, and each one is there because a question above needs it:

- **One count per detected format**, across the occurrences stage 1 examined — the hard, byte-derived number.
- **Within a format, one count per subtype**, with the unsubtyped remainder shown as its own line rather than folded away. This is where the correction pays: an operator sees `OLE_COMPOUND` split into legacy Word, Excel, PowerPoint and *unnamed*, so "how much of this archive is legacy Office" and "how much is OLE-shaped material we cannot name" are two numbers instead of one. The report must show that the subtype line is name-derived and the format line is not, because the two lines do not deserve equal trust.
- **`FLOOR_STOPPED` on its own line, outside the unrecognised branch's total**, for the reason given above: it counts occurrences detection never ran on.
- **For the unrecognised branch, a count per rendering of the leading four bytes**, so the operator can see *which* unknown thing dominates rather than only how much of it there is. This is the line a future floor would be drawn from, and it is also the line that says whether the enumeration missed a document format.

**The report has to state what it cannot see, in its own text.** Text-shaped junk — `desktop.ini`, `.ini`, `.bak` — is counted as plain text with no subtype, indistinguishable there from a genuine `.txt` document, and no floor over the unrecognised branch would ever reach it. A report that shows a small unrecognised count without saying this invites exactly the wrong conclusion, and the whole purpose of writing it is that the decision after it has a source rather than an impression.

That report is what turns "should intact non-documents be removed" from an argument into a decision with a source, and it is the same shape [ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md) established for tier 2's calibration and [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) for regenerating it.

**It belongs to stage 1, not to census, and #87's own suggestion is departed from here deliberately.** #87 proposes that stage 0 report the extension mix. An extension mix would be a report about what an archive's files are *called*, which is the thing ADR-094 established is not evidence of what they are; and the format mix that replaces it needs an open per file, which is the one thing census does not do. (The extension does survive, narrowly, as ADR-094's subtype — but only inside a class the bytes fixed, so even the subtype lines of this report are counts within a byte-determined total rather than counts of names.) Stage 1 already opens every occurrence it examines, so the report is free where it sits and would be a new cost anywhere else. The measure-before-judging posture is satisfied either way; what changes is which pass pays.

**The cost of deferring is stated rather than hidden.** An unrecognised occurrence still reaches stage 2 and still costs a Docling call. #87's complaint is not resolved by this decision — it is made *answerable*, by a report that says how large it actually is. What does improve immediately is unrelated to junk: a mislabelled file now gets the structural check its content deserves, so mislabelled corruption is caught and mislabelled valid documents stop being condemned.

### The seed set is unaffected, and that is on purpose

[ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) records an unusable seed rather than removing it — no verdict is ever written against a seed occurrence, because a seed is not a candidate for publication — and it explicitly deferred the format-floor question to ADR-068's map. This is that answer, and the answer is that no floor is added, so seed folders stay exactly as forgiving as they are: a `Thumbs.db` in a seed folder still costs one extraction call and is still recorded as an unusable seed.

**When a floor is eventually decided, it cannot simply be applied to the seed walk.** A verdict against a seed is the one thing ADR-083 rules out, so a floor would have to take a non-verdict form on that side. Recorded here so that the later decision starts from it rather than rediscovering it — and so that a seed-side benefit never becomes the justification for a corpus-side floor, which is the trap #87 warned about.

## Consequences

**`corpus`'s schema version bumps.** `detected_format` is a `corpus` table, so `CorpusSchema.VERSION` moves from 2 to 3 in the same commit that adds the statement to `schema.sql` ([ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)). An existing database refuses to start until `corpus`'s tables are dropped and census re-run, which is the recorded upgrade path and costs nothing irreplaceable.

**The detected formats and the subtypes are two closed enumerations, and neither is the verdict vocabulary.** The format names what the bytes were found to be; the subtype names what the extension added within one of them. Nothing blocks on either, and adding a value to either is an ordinary consequence of adding a signature or an extension — not the reviewed-vocabulary act ADR-057 governs. These must not be confused with the verdicts because all three are closed enums, in `corpus` and `ledger` respectively; only the verdicts remove archive.

**`corpus` gains an obligation it did not have: to keep the two enumerations apart in code as well as in the schema.** A single enum with `LEGACY_WORD` sitting beside `PDF` would let a name-derived value be read wherever a byte-derived one is expected, which is ADR-094's governing rule defeated by a type. They are separate types, and the subtype is optional at the type level rather than by convention.

**The junk cost #87 raised is unchanged for now, and visibly so.** Every unrecognised occurrence still reaches stage 2. The first real walk produces a number, and the decision that follows it has a source. If that number turns out to be small, the right outcome is no floor at all, and this record will have prevented a permanent vocabulary value bought with a guess.

**A future floor has a natural shape already waiting, and the correction gave it a second candidate.** It would be a stage-1 rule over `detected_format`, and it would need a name of its own in the verdict vocabulary rather than a widened `broken` — that argument is settled above and does not need re-running when the data arrives. What the data decides is *whether*, and over which formats, not *what it is called*.

The obvious candidate is the unrecognised branch. The less obvious one, which only exists because ADR-094 named the OLE class, is **an OLE compound file with no subtype** — the row `Thumbs.db` lands on, and a much better-aimed rule than "everything that matched nothing." It is also the one place a floor would rest partly on a filename's silence rather than on the bytes, which is a thing to weigh openly when the time comes rather than discover: a legacy `.doc` renamed `.dat` sits on that same row. Named here so the later decision starts from both candidates and from that objection.

**Nothing here specifies call sites.** The recorder's type, whether the report is HTML beside the confidence-distribution report or a log summary, and how the leading-bytes rendering is escaped are the hand-off spec's, the same deferral every prior slice made.
