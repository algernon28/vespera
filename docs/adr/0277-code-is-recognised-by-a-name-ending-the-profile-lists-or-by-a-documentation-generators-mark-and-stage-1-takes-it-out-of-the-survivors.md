# ADR-277 — Code is recognised by a name ending the profile lists or by a documentation generator's mark, and stage 1 takes it out of the survivors under a verdict of its own

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Extends**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md). The vocabulary gains one value, `code`, and holds eleven, ten of them blocking (§5).
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as its precedent. A judgement about what a file is gets a verdict of its own, written by stage 1 in its first pass, so the file is never hashed and never read further. As ADR-146 said of a spreadsheet, saying code is out of scope, broken or irrelevant would be false.
- **Extends**: [ADR-256](0256-a-file-whose-name-ends-with-an-ending-the-profile-lists-is-out-of-scope-where-its-bytes-matched-no-signature.md). A second profile list of name endings, `codeNameEndings`, takes the parse, the three states and the reach of `outOfScopeNameEndings` (§2, §3). Where both lists hold an ending, ADR-256's list wins (§6).
- **Extends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md). Byte-level reduction records a fifth kind, so its tiles gain one and its chart of verdicts a fifth series, the most the palette holds (§7).
- **Answers**: [ADR-252](0252-compiled-code-fonts-certificates-databases-and-emails-are-out-of-scope.md), the fourth item of its *What this does not decide*. That item left to relevance whether text written to be compiled is documentation. It is not: it is code, and relevance never sees it.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). A name is read only where no signature named the file's kind, and an empty file is `broken` before any name is read, by its third limit (§3).
- **Keeps**: [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). The detected format is recorded as before, and no `DetectedSubtype` is added (§11).
- **Keeps**: [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) and ADR-256 §8. Seeds never pass stage 1, so nothing here reaches a seed (§10).
- **Rests on**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), for the key; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and ADR-171 §4, for stage 1's configuration consumed (§9); [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), by which every later stage follows stage 1's run (§12).
- **Rests on**: [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md) §7. The counting script's change is a draft the operator installs (§13).
- **Replaces**: the format-mix page's sentence above its timestamp table, as ADR-256 §7 quotes it (§7). That record is left as written.

## Context

`CONTEXT.md` defines **Code**: text written to be compiled or interpreted, or a page of reference a code documentation generator wrote from it. The archives hold both. Today neither is told apart.

- **Text written to be compiled** is `PLAIN_TEXT` when its first 512 bytes decode as UTF-8, and `UNRECOGNISED` when a byte of an older single-byte encoding falls among them, as a comment in Italian written in cp1252 does (`BrokenCheck`, ADR-252's Context). Stage 2 converts the first and sends the second under the neutral name. Stages 4a and 4b sign what converts, stage 5 scores it, stage 6a clusters it, and the deliverable lists it among the documents.
- **Generated reference pages** are HTML, so they are `PLAIN_TEXT` with the `HTML` subtype, and go the same way.

What a page of generated documentation carries is in `docs/research/documentation-generator-markers.md`. It covers the 21 generators of Wikipedia's comparison and five more, and records the exact marks each writes, where in the page, and how a reference page is told from a guide. Its sections 1, 2 and 6 hold the strings §4 lists.

Four facts about the code shape the rule.

- **A blocking verdict written in stage 1's first pass is what keeps a file from every later stage.** Each later stage reads the survivors of the run upstream of it. A file stage 2 merely skipped would have no stage-2 key, and stages 5a to 6b throw on a survivor without one.
- **Stage 1's second pass hashes only survivors.** A file taken out in the first pass is never hashed, so it neither supersedes nor is superseded.
- **Stage 1's implementation version is `corpus`'s alone.** A profile value it acts on, and a list built into it, reach its run id only through its configuration consumed (ADR-256's Context).
- **The verdict vocabulary is closed.** A new kind is a value of `VerdictKind`, added by a change that carries a record (ADR-057).

## Decision

### 1. What code is

**A file is code where it is detected `PLAIN_TEXT` or `UNRECOGNISED`, its bytes read as text (§3), and either:**

1. **its name ends with an ending the profile's `codeNameEndings` lists** (§2); or
2. **it carries the mark of a documentation generator in §4's list**, and, for a generator that also renders guides, the markup of a reference page (§4).

Stage 1 verdicts it `code` in its first pass (§5, §6). A guide is not code, whatever generator rendered it.

### 2. The key, `codeNameEndings`

It takes ADR-256 §2 and §5 whole, with its own name.

- **Its form.** One line of text under `value`, with `provenance` beside it. Endings are separated by spaces, commas or both. Each begins with a dot, holds a character that is not a dot, and holds no `/`, `\`, `*` or `?`. It is matched at the end of the file's name, the last element of its path, in any case, folded with `Locale.ROOT`. The parse is `NameEndingsValue`'s, the type `outOfScopeNameEndings` already has.
- **Its three states.**
  - **Unset**, the key absent or blank. Nothing is code for its name. **It ships unset**, and census writes it unset into every profile it saves.
  - **Answered.** The endings, folded to lower case, each once, in sorted order.
  - **Unreadable.** Applied as unset. The closing line names it: *"Every run value is set, but codeNameEndings reads "…", which is not a list of name endings each beginning with a dot, so this run ignored it and kept nothing as code for the ending of its name. Next: write the endings into codeNameEndings in profile.yaml, each beginning with a dot, with no wildcard, and separated by spaces, and run again."* It comes after `outOfScopeNameEndings`'s line among the lines for unreadable values.
- **Its measurement.** After writing the format-mix page, stage 1 points the key's measurement at `format-mix.html`, as it does for `outOfScopeNameEndings`. The page's table of endings (§7) is the evidence beside the value.

### 3. Which files are read, and what reads as text

**Only a file detected `PLAIN_TEXT` or `UNRECOGNISED` is read for either rule.** That is ADR-256 §3's reach: in those two classes no signature named the file's kind. A PDF, an image, a Word file or any other class a signature named is judged by its bytes, whatever its name. A file the cross-format floor stopped, an empty file among them, is `broken` before either rule is read (ADR-094, third limit).

**A file reads as text where its first 64 KB and its last 64 KB hold no byte from `00` to `1F` but `09` (tab), `0A` (line feed), `0C` (form feed) and `0D` (carriage return).** A byte of nought is among those refused. A file of 128 KB or less is read whole. Between the two windows nothing is read.

- `7F` and every byte above it are not refused. Text in UTF-8, or in a single-byte encoding, reads as text.
- **What that costs.** Text in UTF-16 or UTF-32 holds bytes of nought, so it is never code. Text that carries an escape byte, a terminal colour code for one, is never code either.
- The test applies to both rules. A generator's mark in bytes that do not read as text is not looked for.

### 4. The generators' marks

**The list is built into `corpus`, as ADR-252's signatures are, and is changed by a change that carries a record.** Its version is `v1`, and a change to it is a new version (§9).

**How a string is matched.**

- **Where.** Each string is looked for in the file's first 64 KB and in its last 64 KB, never across the stretch between them.
- **Reading the bytes.** Each byte is read as one character (ISO 8859-1), so a mark written in ASCII is found whatever the page's encoding.
- **Normalising.** Letters are folded to lower case, ASCII only. Each run of spaces, tabs, line feeds, carriage returns and form feeds becomes one space, in the page and in the string alike, so a footer split across lines is found.
- **What is not normalised.** Quotes are matched as written. A raw tag is matched and never its escaped form: a page that quotes `&lt;!-- Generated by javadoc` carries no mark.
- **Order.** The generators are tried in the order of the tables below, and the first that matches is named.

**A generator that renders reference pages only: the mark alone.** Any one of the strings given is a mark. The window is where the research found it, for P the first 512 bytes, F the first 64 KB and L the last 64 KB. It is told for reading only, since both windows are always read.

| Named as | Mark (any one) | Found in |
| --- | --- | --- |
| Javadoc | `<!-- Generated by javadoc`, or `<meta name="generator" content="javadoc/`; and in neither case `content="javadoc/DocFileWriter"` or `content="javadoc/HelpWriter"` | P |
| pdoc | `<meta name="generator" content="pdoc` (pdoc 4 to 16, and pdoc3) | P |
| Haddock | `Produced by <a href="http://www.haskell.org/haddock/"` | L |
| HeaderDoc | `<meta name="generator" content="HeaderDoc"`, or `HTML documentation generated by <a href="http://www.opensource.apple.com/projects"` | P or F; L |
| ROBODoc | `<!-- Generated with ROBODoc Version`, or `>ROBODoc</a> V` | F; L |
| Imagix 4D | `created by Imagix 4D -->` | P |
| NDoc | `<body id="bodyID" class="dtBODY">` | P |
| mkdocstrings | `class="doc doc-object doc-` | F or L |
| phpDocumentor | `Documentation is powered by <a href="http://www.phpdoc.org/">phpDocumentor` (phpDocumentor 2) | L |
| pydoc | `<title>Python: `, together with one of `bgcolor="#7799ee"`, `class="heading-text decor"` or `<header class="heading">` | P |

**A generator that also renders guides: the mark and a page-type check.** Its page is code only where both are found.

| Named as | Mark (any one) | Reference page (any one) |
| --- | --- | --- |
| Doxygen | `<meta name="generator" content="Doxygen `; `<!-- HTML header for doxygen `; or an HTML comment that is `<!-- `, one to forty characters that are neither `<` nor `>`, ` Doxygen `, a digit, digits and dots, and ` -->`, which takes every language's *Generated by* | `class="memitem"`; `<table class="memberdecls"` |
| JSDoc | `<title>JSDoc: `; `Documentation generated by <a href="https://github.com/jsdoc` | `<title>JSDoc: Class: `, `<title>JSDoc: Module: `, `<title>JSDoc: Namespace: `, `<title>JSDoc: Mixin: `, `<title>JSDoc: External: `, `<title>JSDoc: Interface: `, `<title>JSDoc: Global`, `<title>JSDoc: Source: `; `class="container-overview"`; `<h4 class="name" id=` |
| YARD | `title="Yay! A Ruby Documentation Tool"`; `&mdash; Documentation by YARD ` | `<div class="box_info">`; `class="method_details` |
| RDoc | `Generated by <a href="https://ruby.github.io/rdoc/">RDoc`; `Generated by <a href="https://github.com/rdoc/rdoc">RDoc`; `Based on <a href="http://deveiate.org/projects/Darkfish-RDoc/">Darkfish`; `Generated with the <a href="http://deveiate.org/projects/Darkfish-Rdoc/">Darkfish`; `rdoc-style.css` | `<body id="top" role="document" class="class">`; `<body id="top" role="document" class="module">`; `class="class has-toc"`; `id="classHeader"` |
| Natural Docs | `<!-- Generated by Natural Docs, version `; `<html class="NDPage ND` | `NDPrototype` |
| Sandcastle | `<meta name="Microsoft.Help.Id" content="`; `<meta name="Help.Id" content="` | either meta whose content opens with `R:`, `N:`, `T:`, `M:`, `P:`, `F:`, `E:`, `Overload:`, `AllMembers.T:`, `Methods.T:`, `Properties.T:`, `Events.T:`, `Fields.T:` or `Operators.T:`; `<meta name="container"` |
| DocFX | `<meta name="generator" content="docfx `; `<meta property="docfx:navrel"`; `<meta name="docfx:navrel"`; `Generated by <strong>DocFX</strong>`; `Made with <a href="https://dotnet.github.io/docfx">docfx</a>` | `data-yaml-mime="ManagedReference"`; `data-yaml-mime="ApiPage"`; `<div class="inheritance">`; `_syntax">Syntax</h5>` |
| phpDocumentor | `<!-- template designed by Marco Von Ballmoos -->`; `<a href="http://www.phpdoc.org">` (phpDocumentor 1) | `<h2 class="class-name">`; `<div class="method-signature">` |
| phpDocumentor | `class="phpdocumentor-` (phpDocumentor 3) | `<article class="phpdocumentor-element -class`, `-interface`, `-trait` or `-enum`; `phpdocumentor-signature` |

The research found Doxygen's marks in P or F, JSDoc's title in P and its footer in L, YARD's title in P and its footer in L, RDoc's footer in F or L, Natural Docs' in F, Sandcastle's in F, DocFX's in P, F or L, phpDocumentor 1's in P or L and phpDocumentor 3's in F. A page-type check is looked for in the same two windows. On a large page its markup may lie in L only, and is found there.

**What is left out, and why.**

- **Sphinx.** Its footer and head are on every guide, and its reference markup also opens hand-written reference. It waits for a density floor that is measured.
- **MkDocs, Material for MkDocs and Zensical.** They render guides only, so their marks would make every guide code. Reference pages they render through mkdocstrings are code by mkdocstrings' mark.
- **pod2html and perldoc.** No test tells their reference from their guides.
- **Visual Expert.** Only its index page names it.
- **fpdoc and mkd.** fpdoc is matched by structure alone, and mkd writes no HTML.
- **CHM and PDF by their signatures.** Hand-written manuals share them.
- **JsDoc Toolkit 2**, JSDoc's predecessor, and every generator outside the comparison.

### 5. The verdict, `code`

**`VerdictKind` gains `CODE`, stored as `CODE` and written `code`, declared straight after `OUT_OF_SCOPE`. It blocks.** ADR-057's vocabulary goes to eleven values, ten of them blocking. ADR-146 and ADR-271 are the precedents.

**Code is not a survivor.** No stage after the first reads it (§8), and nothing it is copied as is decided here.

**The reason** says why, in one of two forms:

- For an ending: *"a file whose name ends with .java, an ending codeNameEndings in profile.yaml lists as code"*. The ending is spelt as ADR-256 §7 spells it: as the file's name spells it, the longer of two that match, and as the list holds it where folding changed the name's length.
- For a mark: *"a reference page generated by Doxygen"*. The generator is named as §4's tables name it.

### 6. Where it falls in stage 1's first pass

1. `broken`.
2. The kinds `OutOfScope.reasonFor` names. A listed `.eml` is left out as an email.
3. `outOfScopeNameEndings`.
4. **Code**: first its name ending, then a generator's mark. A file both rules reach is code for its ending.
5. For text not taken by any of these, the log rule and then the size rule (ADR-171, ADR-178).

**An ending both lists hold leaves its files out of scope.** Out of scope means left out, whatever the file holds. The format-mix page names the shared endings, and so does the closing line, where nothing earlier is wanted: *"Every run value is set, but outOfScopeNameEndings and codeNameEndings both list .inc, so this run left its files out of scope and kept none of them as code. Next: remove each from the list it does not belong in, in profile.yaml, and run again."* Several shared endings are named in the canonical order, separated by commas.

**Code is not read for timestamps.** It is counted neither among the logs, nor among the text files left out for their size, nor in the timestamp table.

### 7. What the operator sees

- **The verdict and its reason** (§5).
- **Stage 1's log line**: `[byte-level-reduction] checked <occurrence> for damage -> code: <reason>`.
- **The format-mix page.**
  - After the paragraph that counts what is out of scope, a paragraph counts code: *"Files kept as code, and not read any further: N. Of those, kept for the ending of their name: E, and kept for the mark of a documentation generator: M."*
  - A paragraph says what `codeNameEndings` holds. Where it is answered: *"codeNameEndings in profile.yaml lists these name endings: .c, .java."*, followed by what that does. Where it is unset or unreadable: *"No name endings are listed as code."*, followed by what listing them would do.
  - Where the two lists share an ending, a sentence names it: *"outOfScopeNameEndings and codeNameEndings both list .inc; a file whose name ends with it is out of scope, and not code."*
  - **A section headed *Kept as code*** tables the files kept as code, one row per listed ending that matched and one per generator named, each with its count.
  - **A section headed *Name endings of text files and files of no known kind*** counts every file detected `PLAIN_TEXT` or `UNRECOGNISED`, whatever became of it, by the ending of its name: from its last dot, where that dot is not the name's first character, folded to lower case. A name with no such dot is counted as *no ending*. It holds counts and endings, never a name or a path. The tally holds one entry for each distinct ending, as the table of leading bytes ADR-095 asked for holds one for each distinct four bytes.
  - That section ends with guidance: never list `.html .htm .xhtml .md .txt .tex .rst .adoc`, which are where the documents are; take care with `.sql .inc .in`, which hold documents as often as code.
  - The sentence above the timestamp table reads *"Each text file not left out for its kind or for the ending of its name, and not kept as code, was read from its start and its end"*, in place of ADR-256 §7's.
  - The table of what each file was found to be, and the table of leading bytes, count code under what its bytes were, as ADR-095 counts every file.
- **The progress page** (ADR-268).
  - Byte-level reduction records five kinds: broken, duplicate-of, superseded-by, out-of-scope and code. So its tiles end with one for code, keyed `code` and labelled *code (kept)*, with its count and its rate.
  - Its chart of verdicts draws code as the fifth series, the most the palette holds.
  - The page of samples and the tab of runs gain a column, `code`, after `out-of-scope`.
  - The survivors tile counts code among what stage 1 took out.
- **The invocation account**: `counts verdict kind=CODE count=N`, in the vocabulary's order.
- **The closing line**, for an unreadable value (§2) and for a shared ending (§6).

### 8. Stage 1 only

Code is taken out of the survivors by the first pass, so:

- **it is never hashed for content identity.** It neither supersedes nor is superseded, and two identical files kept as code are both code;
- **no later stage reads it.** It is never converted, measured, chunked, shingled, signed, compared, embedded, scored, translated, clustered or arranged;
- **it is on neither list stage 2 writes**, the survivor list or the review list.

**The discard before an unfinished stage 1 works again includes `CODE`**, beside `BROKEN`, `OUT_OF_SCOPE` and `SUPERSEDED_BY` (ADR-116), so a file redone keeps one verdict.

### 9. Stage 1's identity records both lists

`ConfigConsumed` gains two components after `outOfScopeNameEndings`:

- `codeNameEndings`, in the canonical form, as a JSON array, `[]` where the key is unset or unreadable;
- `generatorMarks`, the version of §4's list, `"v1"`.

Where the profile sets none of the keys stage 1 reads, its configuration consumed reads:

`{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null,"outOfScopeNameEndings":[],"codeNameEndings":[],"generatorMarks":"v1"}`

The same endings in another order, case or spacing, or with one repeated, name the same run. An ending added or removed names another.

### 10. Seeds are untouched

A seed never passes stage 1 (ADR-256 §8), and ADR-083 sets a seed no stricter bar than a survivor. A seed whose name ends with a listed ending, or that carries a generator's mark, is extracted as every seed is.

### 11. No schema change, and no subtype

The verdict is a row of `verdict` with a kind and a reason, as every verdict is. `detected_format` is unchanged, and no `DetectedSubtype` is minted: neither rule names a format, and ADR-095 and ADR-256 refused a subtype that claims one.

### 12. Run ids, and the ADR-216 check

- **`corpus`**: the two rules, the text test, the marks and their version, the first pass's order and its counts. That is `OutOfScope` or a class beside it, `BrokenOrOutOfScope` and `FormatMix`.
- **`ledger`**: `VerdictKind`, and the statement of `Verdicts.countsOfRun`, which sums each blocking kind.
- **`profile`**: `Profile` gains the key, typed `NameEndingsValue`.
- **`pipeline`**: `ByteLevelReductionTasklet` (its configuration consumed, its log line, its discard and the key's measurement), `FormatMixReport`, `NextAction`, `ProgressPage` and `InvocationAccount`. No stage's version names `pipeline` (ADR-222).

**Every stage moves, whatever the profile holds.** Stage 1 names `corpus`, and its configuration consumed gains two fields. Stages 2 to 6b each follow the run upstream of it (ADR-089).

**What that costs in kind.** Stage 1 checks every file occurrence again. Stage 2 asks the converter nothing about content it already holds an answer for: the extraction cache is keyed on the content hash and the extractor identity, and neither changes. Every later stage recomputes what it keys by run.

### 13. The counting script

`.claude/hooks/working-directory-counts.mjs` lists the verdict kinds twice, and its `SURVIVORS` statement counts survivors by the blocking ones. Both lists gain `"CODE"` after `"OUT_OF_SCOPE"`, and nothing else in the file changes.

**It lies in a closed `.claude` folder, so the change is a draft the operator installs** (ADR-215 §7). The draft is laid out as `.claude` is, at `%TEMP%\vespera-claude-drafts\adr-277\hooks\working-directory-counts.mjs`. Installing it changes the script's SHA-256 to 0be436a51227c3c29d52a2e71200d07b5bdf2ef71ac70f1943fb4267411265a5, which `COUNTING_SCRIPT_SHA256` in `.claude/hooks/private-paths-guard.mjs` is set to in the same change. Until both are installed, W02 of `src/test/hooks/working-directory-counts.test.mjs` fails once `VerdictKind` holds `CODE`, since it holds the script's lists to the code's.

## Alternatives rejected

- **Content alone.** No local classifier tells code from prose reliably, as the research on logs and configuration found (#555), and one would be a model to run on every text file.
- **Markers of each language**, such as `#include` or `public class`, **a local model, or a floor on the share of lines that look like code.** Each needs a calibration on archives nobody has measured, and each misfires on a document that quotes code.
- **The name alone, with no text test.** A binary file named `.c`, or a file of terminal records named `.dat`, would be kept as code. The test costs two reads already bounded.
- **A table owned by `corpus` and a second `NOT EXISTS` in every survivor read.** The counting script would count code among the survivors and say nothing. The ledger would hold a fact only `corpus` writes. And it is about twice the code of a verdict.
- **A subtype and `out-of-scope`.** Out of scope means left out whatever the file holds. Code is kept, apart from the documents.
- **Code as a survivor that stage 2 skips.** It would have no stage-2 key, and stages 5a to 6b throw on a survivor without one.
- **Every page of a generator, guides with them.** The operator's reference pages are code and their guides are documents. A page-type check is what tells them apart.
- **Sphinx's reference markup without a floor.** One directive on a guide would make it code.
- **The marks in the profile.** Each mark is a fact about a generator, not about an archive. It belongs with the signatures, and the operator's list is the endings.
- **Reading whole files.** The windows are what stage 1 already bounds its reads by (ADR-171), and a mark or a control byte between them is rare enough to leave.

## Consequences

- With endings listed, the operator's code and the generated reference pages leave the survivors in stage 1. They are counted on the format-mix page by ending and by generator. They are never converted, scored, clustered or listed by stage 2, and the counting script counts none of them among the survivors once its draft is installed.
- **A guide whose page carries a reference page's markup is taken as code.** So is a document named with a listed ending whose bytes read as text. The verdict and its reason say why. A file named with an ending the guidance warns of is the operator's to list or not.
- **Code in UTF-16 or UTF-32, or carrying an escape byte, is not code**, and goes on as text.
- **A reference page whose mark a custom template removed is not code.** Nor is one whose mark lies only between its two windows, one a generator outside §4 wrote, or a Doxygen listing of code, which carries no reference markup.
- **Code has no content hash**, so what the deliverable copies of it, and how it is checked, is the code-sets-in-the-deliverable ticket's to decide (#573).
- **Census writes `codeNameEndings`, unset, into every profile it saves**, and the deliverable's index states it as written.
- `CONTEXT.md` defines **Code**, and its entries for **Verdict**, **Out of scope**, **Survivor list**, **Arrangement** and **Publication-ready artifact** are widened.
- The README's table of profile values, `docs/architecture.md`'s stage table and the javadoc of each class §12 names name the key and the verdict (#577).
- The tests that list the blocking kinds in order list ten: the progress page's tests of its samples, its outcomes and its tab of runs.

## Tests

Every file is built in the test, a few bytes or a page of markup in the shape §4's research found each generator writing, and every profile is YAML written by the test. The key is written through `CodeNameEndingsInProfile` and the verdict read back by its stored name, so every test compiles before the key and the value exist.

| Class | What it holds |
| --- | --- |
| `ledger.VerdictKindTest.isExactlyTheRecordedVocabulary` | eleven values |
| `…codeTakesTheOccurrenceOutOfTheSurvivors` | `CODE` is a value, straight after `OUT_OF_SCOPE`, and blocks |
| `profile.CodeNameEndingsInTheProfileTest.aFreshProfileCarriesTheKeyUnset` | census writes `codeNameEndings` into a fresh profile, unset, and the profile states it as empty |
| `…theEndingsAreKeptAsWritten` | a value of mixed case, spaces and a comma loads, is stated as written, and is written back untouched |
| `…aYamlListUnderTheValueIsRefusedNamingTheKey` | a YAML list under `value` fails the load naming the key |
| `pipeline.NextActionTest.anUnreadableListOfCodeNameEndingsIsReported` | nine unreadable values each make the closing line name the key, say an ending begins with a dot and that nothing was kept as code, and quote the value back, in one line |
| `…aReadableListOfCodeNameEndingsIsNotReported` | seven readable values are not reported |
| `…anEndingBothListsHoldIsReported` | an ending in both lists is named with both keys in one line, which says its files were left out of scope; an ending in one list only is not named |
| `pipeline.CodeTaskletTest.keepsTextAndSingleByteTextWhoseNameEndsAsListedAsCode` | text named `.java`, two of them identical, single-byte text named `.c` (`UNRECOGNISED`) and text with a tab and a form feed named `.C` are code, with §5's reason; their formats are unchanged; nothing is hashed or superseded; the log line says `-> code:`; the configuration consumed records `[".c",".java"]` and `"v1"`; the page counts four kept as code, four for an ending, none for a mark, none out of scope, and lists the endings; the key's measurement points at the page |
| `…aListedEndingNeverMakesCodeOfBytesThatDoNotReadAsText` | a binary file named `.c`, a PDF named `.java`, text with a control byte in its last 64 KB, text with an escape byte in its first 64 KB, and single-byte text with a byte of nought carry no verdict; an empty file named `.java` is `broken`; text whose only control byte lies between the windows is code |
| `…codeComesAfterWhatIsOutOfScopeAndBeforeTheLogRule` | an email named with a listed ending is left out as an email; an ending both lists hold leaves its file out of scope; a timestamped file named `.py` is code and not a log; the out-of-scope list and the log rule still apply to the rest; the page counts four out of scope, one log and two code, one file in the band from 90% to 100%, carries §7's sentence above the table, and names the shared ending |
| `…eachGeneratorsMarkMakesItsReferencePagesCode` | with no profile, a reference page in each of the 21 shapes of §4's tables (pdoc and pdoc3 both, Doxygen in English and in Italian, phpDocumentor 1, 2 and 3) is code, its reason naming the generator; the configuration consumed records `[]` and `"v1"`; the page counts them all for a mark |
| `…aGuideAndAPageOfAGeneratorLeftOutStayDocuments` | a Doxygen page of prose, JSDoc's home page and a tutorial, YARD's and RDoc's file pages, a Natural Docs text page, a Sandcastle and a DocFX conceptual page, a phpDocumentor 1 tutorial and a phpDocumentor 3 guide, a Javadoc page written by `DocFileWriter`, a Sphinx reference page, a Material page, a pod2html page, an fpdoc page and a page that quotes a mark escaped carry no verdict |
| `…aMarkIsLookedForInTheFirstAndTheLast64KbOfTextOnly` | a Haddock footer in the last 64 KB of a 200,000-byte page makes it code; a Javadoc mark between the windows does not; a single-byte page (`UNRECOGNISED`) with a mark is code; a page with a mark and a byte of nought is not |
| `…anEndingIsReadBeforeAMark` | a page named with a listed ending and carrying a mark has the ending's reason and is counted for its ending |
| `…theFormatMixPageSaysWhatWasKeptAsCodeAndWhichEndingsTheTextHas` | the page counts code by the ending and by the generator under *Kept as code*, counts the endings of text and of files of no known kind (and *no ending*) but not of a picture, and carries the guidance |
| `…theRunIsNamedByTheEndingsKeptAsCodeAndNotByHowTheyAreWritten` | the same endings in another order, case and spacing name one run, one more names a second, and `.j*` is recorded as `[]` and keeps nothing as code |
| `…aStageOneLeftUnfinishedIsRedoneWithOneCodeVerdictEach` | stage 1 redone after its finished step is removed leaves one `CODE` verdict |
| `pipeline.ProgressPageShowsCodeTest.byteLevelReductionShowsCodeAsAKindOfItsOwn` | byte-level reduction's tiles end with `code`, labelled *code (kept)*, with its count and rate; the survivors tile takes code out; the chart draws code as series `s5`; the page of samples and the tab of runs head a `code` column after `out-of-scope`, and the run's row counts it |
| `pipeline.CodeInvocationTest.codeIsReadByStageOneAloneAndCountedWhereTheOperatorLooks` | through a whole invocation, two identical files kept as code carry one `CODE` verdict each, of byte-level reduction, and no row of `content_hash`, `extraction_metric`, `shingle`, `minhash_signature`, `relevance_score`, `translation_of` or `document_cluster`; the document is measured; neither list of stage 2 names them; the account says `counts verdict kind=CODE count=2`; a seed named `.java` is extracted and carries no verdict; the tab of runs counts two in the code column |
| `pipeline.RunIdentityGoldenTest.byteLevelReduction` | §9's configuration consumed, word for word |
| `pipeline.ProgressPageChartsTest`, `pipeline.ProgressPageOutcomesInvocationTest`, `pipeline.ProgressPageRunsInvocationTest` | the ten blocking kinds, `code` after `out-of-scope`, wherever they are listed in order |
| `src/test/hooks/working-directory-counts.test.mjs` W02 | the counting script's lists hold `CODE`, once §13's draft is installed |

## What this does not decide

- **Code sets** (#572): how code is grouped by its path, and what a generated tree's pictures, companion files and a tree present twice become. That includes the empty files a set lacks, since an empty file stays `broken`.
- **Code in the deliverable** (#573): what is copied, how a copy is checked when code has no content hash, and the **Copy** entry of `CONTEXT.md`.
- **Logs and configuration files** (#575). The log rule never reads code (§6).
- **Text with no recognisable text** (#576). Code is taken out before it.
- **Sphinx's reference pages**, until a density floor is measured, and **generators outside Wikipedia's comparison**.
- **A Doxygen listing of code** (`*_source.html`), which carries no reference markup.
- **Text in UTF-16 or UTF-32**, and **an HTML page that opens with a byte-order mark, an XML prolog or a comment.** The research's prerequisite for the HTML test is not taken: such a page is still `PLAIN_TEXT` by its name or its UTF-8 prefix, and is read for marks as any other.
