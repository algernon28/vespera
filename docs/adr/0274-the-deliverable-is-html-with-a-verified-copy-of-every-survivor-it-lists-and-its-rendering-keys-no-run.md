# ADR-274 — The deliverable is HTML with a verified copy of every survivor it lists, and its rendering keys no run

> **Partly amended — see [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md).** The tree is drawn at the deliverable root, which `vespera.deliverable-root` may name and which is `deliverable/<generation run>/` in the working directory where none is named, and every tree carries a marker, `vespera-deliverable.txt` (§1); the gibibyte of §2.5 is read on the drive the tree is written to; §2.7's bound of 69 characters is, for a named root, one of 146 on the root's path; §6's redrawing in place holds at a named root only for this working directory's tree of the same generation run; and §7's index is the root's. Everything else in this record stands.

> **Partly amended — see [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md).** `documents/` is no longer written: `<partition>/<cluster>/` holds the cluster's page, `index.html`, its pictures and a copy of each of its survivors (§1, §1.2), and the index's paragraph and the `copy` column say so (§1.4); a copy keeps its original's file name, made safe, cut to 100 code units and suffixed with its occurrence id only on a collision (§2.1), worked out from the cluster's members, so §2.3's and §2.4's "from the survivor alone" reads "from the survivor and its cluster's members, both fixed for the tree"; §2.5's survey after the last copy counts the copies past 259 characters; an entry links `./<copy name>` percent-encoded (§2.6); §2.7's bound of 69 characters holds for the index, the pages and the pictures and not for the copies; and §7's closing line names what an earlier drawing left behind. Everything else in this record stands.

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), in "Markdown, not HTML", the tree drawn under its Decision, `index.md`'s name, and "A re-run under a changed configuration writes a new tree beside the old one", which holds of a change to what the generation run is keyed by and not of a change to how the tree is drawn (§1, §6). One tree per generation run, in the working directory, with no new key, and "every link inside the deliverable resolves with no database, no ledger and no network" stand.
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md), in its Decision's first two sentences, "Why not copy", "What a link physically is", "6b does not check that the originals are still there", and its Consequences on links that die with the archive, on "Self-contained means without the ledger, not without the archive", and on an operator who must send the archive too (§2, §3). Every survivor listed, the manifest beside the prose, and the refusal to write the extracted text stand.
- **Amends**: [ADR-135](0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md): a membership entry links to its copy inside the tree, or names its path and why no copy stands, and never links into the archive (§2.6). The citation anchor stands.
- **Amends**: [ADR-134](0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md), [ADR-136](0136-the-angle-bracket-and-the-ampersand-are-backslash-escaped-and-the-heading-becomes-a-fourth-surrounding.md), [ADR-137](0137-a-destinations-ampersand-is-percent-encoded-and-the-destination-is-a-fifth-surrounding.md), [ADR-138](0138-a-bracket-is-escaped-in-every-surrounding-a-value-is-read-in-and-the-link-text-stops-being-a-rule-of-its-own.md) and [ADR-148](0148-a-backtick-is-escaped-in-every-surrounding-a-value-is-read-in-and-one-renderers-divergence-moves-no-rule.md): no Markdown is written, so their surroundings go and one HTML rule stands in their place (§1.3). [ADR-235](0235-a-path-in-the-manifest-a-spreadsheet-would-run-as-a-formula-is-written-behind-a-single-quote.md) and the manifest's RFC 4180 quoting stand.
- **Amends**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), in the cluster file's extension and in the slug, which is cut to 40 characters (§1.2). The order prefix and its padding stand.
- **Amends**: [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §3 and §5: a picture is shown by an `<img>` whose `alt` is its caption under §1.3's rule, its folder named from the page's stem as before. The furniture rule, the budget and the two passes stand.
- **Amends**: [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md) §4's "none opens an archive file" and §5's "Nothing after stage 2 notices": the generation step opens each survivor it lists to copy it, for reading only, and notices a survivor changed or gone since stage 2 (§2). Every other step still reads the recorded key and opens no archive file, and the manifest's `content_hash` is still that key.
- **Amends**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) §1 and [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md): the text of the deliverable is no longer among what shapes a stage's output for its version. Generation's version names `synthesis`, `extraction` and `embedding`, and not `profile`, which it named for the index alone (§5). This answers ADR-226 §7's reason for naming it, that a key set changed in `profile` alone would otherwise move no run id: the index is drawn again at the next invocation that reaches the tree (§6), so the change is seen with no run. With it, ADR-216 §7's "a javadoc in `ledger` or `profile` costs no run id" holds of `profile` again, and the Read-with lines ADR-216 and ADR-226 carry, that it holds of `ledger` alone, hold until this record is built and not after.
- **Amends**: [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) §2, in what may differ between two trees drawn under one run id: the key set as well as the values, since the index is drawn from the profile record at each drawing (§5, §6). Every key stated, each as written, stands.
- **Amends**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) in the module rule: a ninth module, `deliverable`, declares `ledger` and `synthesis`, the second declared exception beside `extraction`'s (§4).
- **Amends**: [ADR-213](0213-each-rule-the-deliverable-is-written-by-has-one-class-and-the-cluster-key-the-citation-pattern-and-the-filename-stem-are-each-written-once.md), in which module each rule's one class lies: `deliverable` (§4). The cluster key, the citation pattern and the filename stem stay written once, in `synthesis`.
- **Amends**: [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md), in where its sentences are written and their asterisks: `deliverable` writes them, in italics of HTML (§1.4). Their words stand.
- **Amends**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md): the pages of the tree are written by `deliverable`, which cannot name `pipeline`, with a skeleton of its own drawn to `ReportPage`'s stylesheet (§1.1). `ReportPage` stays the one writer of the pages beside the database.
- **Amends**: [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), in which invocations write the tree: also one whose generation step is already recorded finished (§6).
- **Amends**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2, in what is opened: the tree's `index.html` too, once, by an invocation that wrote it (§7).
- **Amends**: [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9 and [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §9, whose lists of what is still held gain 6b's outcomes of the copies that did not stand, by occurrence id, at worst every survivor listed (§2.3).
- **Keeps**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) and [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md), nothing carries the tree anywhere; [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md), nothing is written into the archive; [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) and [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md), what a citation is and how the membership is numbered; [ADR-151](0151-the-manifests-content-hash-is-every-survivors-sha-256-taken-at-6b-through-extractions-own-hash.md) §1; [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md), the account's allow-list; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) but for its §9's list, and [ADR-240](0240-the-tree-asks-what-it-lists-of-each-survivor-a-page-of-survivors-at-a-time.md), one seed partition and a page of survivors at a time; [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md), pages moved into place by `ReplacingMove`; [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) and [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md), no agent reads an operator's document (§8).
- **Read with**: [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md), whose translation step runs under the scoring run and adds no row to `StageModules`, and whose §10 has its change to `profile` move generation: from this record's build a change to `profile` moves no stage (§5, §9). [ADR-272](0272-a-module-whose-answer-is-the-boundary-of-a-shallow-clone-is-written-as-unknown.md), by which a module answered at a shallow clone's boundary is written `unknown`: `deliverable` gets its line like every module, and no stage reads it (§5). [ADR-273](0273-the-translation-step-reads-each-mean-vector-only-to-compare-it-and-the-profile-only-when-its-run-is-minted.md), whose §4 lists `profile` among the modules generation's version names, as it did when that record was built: from this record's build generation's list is `synthesis`, `extraction` and `embedding`, and ADR-273's change to `embedding` moves generation under either (§5, §9).
- **Rests on**: a reading of the seventeen classes of `synthesis` that write the tree and of who calls each, of `StageModules` and `.mvn/scripts/implementation-versions.groovy`, of `GenerationTasklet`'s three paths that write the tree and its one that writes none, of `NextAction`'s closing line, and of the pom, which carries no Markdown renderer. No archive, working directory or deliverable was opened for this record (ADR-196), so the copies' size on a real archive is not measured here (§2.5).

## Context

On 2026-10-17 the operator said: *"i want the deliverable in html, and the documents used copied in the working dir"*. Earlier the same day, of another change: *"i wouldn't add new flags for now, it makes things needlessly complicated. Just use defaults"*.

**What the tree is today.** `synthesis.Deliverable` writes `deliverable/<generation run>/index.md`, one `<partition>/<cluster>.md` a cluster with a folder of its pictures beside it, and `documents.csv`. A cluster file's membership entry links to the original in the archive by a path relative to the file (ADR-135), or names the path where no such path exists. Five Markdown surroundings, `MarkdownSurroundings`, escape what lands in a heading, a cell, an entry and a destination. Nothing of the archive is copied, and after ADR-206 nothing after stage 2 opens an archive file.

**What keys the generation run.** A stage's version is the last commit of each module `StageModules` lists for it (ADR-058); generation lists `synthesis`, `extraction`, `embedding` and `profile`, and `profile` only for the rule that states every key on the index (ADR-226). Every class that writes the tree lies in `synthesis`, so a change to how a page is drawn changes the generation run's id, and a new id has no `synthesis_doc` row: every cluster is asked about again. For the operator's arrangement of 92 clusters that is about 24 minutes of the generation model. The arrangement run names `synthesis` too, so the same change also mints an arrangement the operator must approve again.

**When the tree is written.** `GenerationTasklet` writes it on each of its three paths that end its work, stopped, left unfinished or finished, and on none where the step is already recorded finished under the run: that path logs one line and returns. So a tree drawn differently under an unchanged run is never seen.

**What the model writes.** The prompt asks for connected prose citing `[n]` and names no format. The answer's `prose` is stored as it came back and may carry Markdown or plain text. The pom carries no Markdown renderer, and the shipped classes parse none.

## Decision

### 1. The tree is HTML, and HTML only

```
deliverable/<generation run>/
  index.html                         the arrangement, whole, and what produced it
  documents.csv                      one row per survivor
  documents/<occurrence>-<stem>.<ext>  a verified copy of each survivor (§2)
  <partition>/<cluster>.html         one page per cluster
  <partition>/<cluster>/<picture>    the pictures that page shows (ADR-149)
```

No `.md` is written. `documents.csv` stays, being what a program loads. Nothing downstream of the tree reads Markdown: ADR-101 struck every target, and the operator asked for HTML.

#### 1.1 Every page stands alone

Each page is `<!DOCTYPE html>`, `<html lang="en">`, `<meta charset="UTF-8">`, a `<title>`, and one inline `<style>`; no `<script>`, no `<link>`, and no `src` or `href` that names a scheme or a host. Every link is relative and lands inside the tree. The stylesheet is `ReportPage`'s shared rules (font, margins, table and cell borders), written again in `deliverable` because a capability module cannot name `pipeline`.

#### 1.2 Names

The index is `index.html`. A partition's folder and a cluster's page are named as ADR-112 names them, the page ending `.html`, with each slug cut to its first 40 characters and any hyphen that then ends it dropped. The order prefix keeps two names apart however their slugs are cut.

#### 1.3 One escaping rule, and the writing's Markdown rendered from a subset

**Every value the tree did not write itself is escaped by one rule**: `&` as `&amp;`, `<` as `&lt;`, `>` as `&gt;`, `"` as `&quot;` and `'` as `&#39;`, wherever it lands, text or attribute. That covers a title, a label, a path, a seed path, a profile key and value, a reason, a caption and the writing. No value but the writing is folded onto one line or split: HTML ends no element at a line break.

**The writing's Markdown is converted to HTML by a renderer of `deliverable`'s own, `ProseHtml`, from a fixed subset, with no dependency** (ADR-046): the pom carries no Markdown renderer, and a subset of a few constructs needs none. Its output stands in one `<div class="writing">`.

**Escaping comes first, always.** The writing is escaped whole by the rule above before any markup is added, and every construct below is recognised on the escaped text, whose delimiters (`#`, `*`, `_`, `-`, digits and `.`, the backtick, `[` and `]`) the rule leaves as they are. So the only tags in the output are the ones the renderer writes — `p`, `br`, `h3`, `h4`, `h5`, `ul`, `ol`, `li`, `strong`, `em`, `code` and `a` — and the only attribute is a citation's `href="#document-n"`, made of digits the writing matched. No input produces any other tag or attribute.

**Blocks.** The writing is read in blocks separated by one or more blank lines, and each line of a block is stripped of the spaces around it. In a block, line by line:

- **A heading** is a line of one, two or three `#` and a space: `<h3>`, `<h4>` and `<h5>`, a level beneath the page's own `<h1>` and `<h2>`. Four or more `#`, or `#` with no space, is text.
- **A list item** is a line opening with `- ` or `* ` (`<ul>`) or with digits, a dot and a space (`<ol>`, counting from 1, the written numbers not kept). Consecutive items of one kind are one list; an item of the other kind starts a new one. Indentation is not read, so a nested list is shown flat, its items in order. A line that is none of these, following an item in the same block, continues that item after a `<br>`.
- **Any other line** is part of a paragraph, `<p>`, its lines joined by `<br>`: every line break the model wrote is kept.

Each element is on a line of its own in the page, and each list item too.

**Inline**, in headings, items and paragraphs alike:

- **Code**: text between a pair of backticks on one line is `<code>`, and nothing in it is read as emphasis.
- **Bold**: `**text**` on one line, opening before a character that is not a space and closing after one, is `<strong>`.
- **Italic**: `*text*` or `_text_` on one line, likewise, is `<em>`; a `_` with a letter or digit on its outer side is text, so `a_b_c` stays as written, and **`*` takes `_`'s rule**, so `2*3*4` stays as written too: a product written with stars is as likely in the writing as a name with underscores, and one rule for both delimiters is one to state. Bold is read before italic, so `**a *b* c**` is italic inside bold.
- **A delimiter with no partner on its line stays as written**: an unterminated `**`, a lone `*` or `_`, an odd backtick.
- **Citations**: `Citation.AS_WRITTEN` is matched everywhere, inside code, bold, italic, items and headings included, and each `[n]` becomes `<a href="#document-n">[n]</a>`.

**Everything else is shown as escaped text and never read**: a table, a link, an image, raw HTML, a block quote, a rule, a fenced code block, four `#` and more. `[x](javascript:…)` reads as those characters; `<script>` as `&lt;script&gt;`.

#### 1.4 The pages

- **The index** opens with what ADR-103 and ADR-186 put there: the run, the walk, the corpus root and every profile key with its value. Its paragraph says that `documents/` holds a copy of every document listed, checked against the digest taken when it was converted, and that the corpus root stated above it is where they were copied from. Each partition is an `<h2>` naming its seed path, over a table of its clusters, `Group`, `Documents`, `Written up as`, a cluster with writing linked to its page.
- **A cluster's page** is headed by its title, or its label where nothing was written (ADR-106); then the writing (§1.3) and ADR-133's disclosure where it was written from part of the cluster; or, where nothing was written, ADR-174's sentence for its reason in `<em>`, in the same words and without the asterisks that were Markdown. Then `The documents in this group` over an `<ol>` numbered as ADR-133 numbers it, each `<li id="document-n">`, carrying the entry of §2.6 and its pictures.
- **`documents.csv`** keeps its nine columns and gains two at the end, `copy`, the copy's path from the tree's root with `/` between its parts or nothing where no copy stands, and `copy_outcome`, one of the five words of §2.3.

### 2. Every survivor listed is copied, byte for byte, and checked

#### 2.1 Where a copy is put

A survivor's copy is `documents/<occurrence id>-<slug>.<extension>` under the tree, where the slug is ADR-112's slug of the stem of its file name, cut to 40 characters, and the extension is the last extension of its file name, lower-cased, kept where it is one to eight letters `a` to `z` and digits. With no slug the hyphen goes, and with no such extension the dot. So `reports/Q3 Report (final).PDF` of occurrence 42 is `documents/42-q3-report-final.pdf`.

**The name is collision-free by construction**: an occurrence id is unique within its walk, and a tree has one walk. It is the archive's depth, not a path of it, that the name leaves out: a copy's path is as long whatever folder the document lay in, it holds no character a path parser can refuse, and two names that differ only in case cannot meet. The root-relative path is the entry's text and the manifest's `path`, so nothing is lost to a reader.

#### 2.2 How a copy is made and checked

The original is the corpus root joined to the survivor's root-relative path, **opened for reading and nothing else**. Its bytes are streamed through SHA-256 into `<copy>.part` beside the copy, and when the stream ends **the digest is compared with the key stage 2 recorded for the survivor**, `ListedSurvivor.contentHash` (ADR-206), which is what its conversion, its scores and the writing about it rest on. Equal, the part is moved over the copy's name by `ReplacingMove` (ADR-242). Unequal, the part is deleted and nothing stands at the copy's name.

The copies are made a seed partition at a time, each partition's before any of its pages is written, so each page states what stands.

#### 2.3 What a copy can come to

| Outcome | `copy_outcome` | When | What the entry says |
| --- | --- | --- | --- |
| copied | `copied` | the digest equals the key, or a copy already stands at its name (§2.4) | a link to the copy |
| changed | `changed` | the bytes read do not hash to the key: the file was changed since stage 2 read it | *changed in the archive since it was read, so it was not copied* |
| gone | `gone` | the file, or a folder above it, is no longer there | *no longer in the archive, so it was not copied* |
| no room | `no-room` | §2.5 | *not copied: the drive had too little room left* |
| not copied | `not-copied` | any other failure to read the original or write the copy, a name the path parser refuses among them | *could not be copied* |

**None of them fails the step, and none throws.** A survivor whose copy did not stand is a fact about that survivor, recorded in its entry and its row, and the step goes on to the next. An exception out of the tree would roll back every fault row the invocation recorded (ADR-111). No row of the database records a copy: the tree is the record, and the next invocation tries every copy that does not stand again.

**Only an outcome other than `copied` is held**, by occurrence id, from the copy that met it to the end of the drawing, since the pages and the manifest written after it say it; a copy that stands is not held, the name it stands under following from the survivor alone, and a survivor held nowhere is `copied`. What is held is as large as the copies that did not stand, which can be every survivor listed, on a drive with too little room or whose free space cannot be read: a collection as large as a run's survivors, held for the reason ADR-220 asks to be given, that the manifest, written after every page, states each outcome.

**One entry is 62.6 to 67.3 bytes, measured as ADR-223 measured its 93 bytes a cluster.** What `Copies` holds is a `HashMap<Long, CopyOutcome>`, an occurrence id boxed for each entry and its outcome a reference to one of five constants. A throwaway probe outside the repository, on JDK 26 with its default flags (compressed references, no compact headers), took the used heap after four collections with the map alive, less the same before it was built, for 10,000, 100,000 and 1,000,000 entries, three rounds each: 62.6, 66.8 to 67.3 and 65.2 bytes an entry under the serial collector, and 62.6, 76.8 to 78.7 and 66.2 to 66.3 under G1, the default, whose figure at 100,000 is its regions' and not the map's. That is the 32-byte node and the 24-byte `Long`, 56 bytes, and 4 bytes for each bucket of a table 1.33 to 2.67 times as long as the entries: about 66 MB at a million survivors none of whose copies stood.

#### 2.4 After a stop, a copy that stands is kept

**A file at a copy's name was put there by §2.2's move and by nothing else**, so it is taken as copied, and neither it nor the original is read. A `.part` a stopped invocation left is written over by the next attempt. The tree is named for the generation run, which a change to how the tree is drawn does not move (§5), so a redrawn tree keeps every copy it already had.

#### 2.5 Room on the drive

Before each copy the original's size is read, and the copy is made only where the working directory's drive would still have **1 GiB** free after it; otherwise its outcome is `no-room`. The working directory holds the database, and the step that copies still writes its completion and the next invocation writes more: a full drive there would fail a step that had finished its work. The gibibyte is a bound of the tool, as ADR-149's ten pictures a document are, and not a judgement about any corpus.

**The size is read as the file system reports it**, by `Files.size`, without opening the original, and the free space as the working directory's file store reports it usable, asked again before each copy. **What cannot be read counts as none**: a free space that cannot be read is 0 bytes, so no copy is made and every outcome is `no-room`, since a copy the drive may not hold is the one thing this bound is for; and a size that cannot be read before a copy fails that copy, `gone` where the file is not there and `not-copied` otherwise.

**The size is not measured before this ships.** The invocation measures it: before the first copy it states how many survivors it is about to copy and how many bytes they hold, and how many bytes the drive has free; after the last, how many copies stand, by outcome, and how many bytes were copied. Counts only. The survivors about to copy are those with no copy at their name, and the bytes they hold are each one's `Files.size`, **a size that cannot be read counting as 0**: the survey is a count for the operator to read, and decides nothing, each survivor meeting its outcome at its own copy.

#### 2.6 The archive is never written, and a link never leaves the tree

The original is opened with `READ` alone, and nothing is created, moved, renamed or touched beneath the corpus root. A membership entry links to `../documents/<name>` with the root-relative path as its text where its copy stands, and otherwise names the path as text with §2.3's sentence in `<em>`, and links nowhere. No entry links into the archive: a link that leaves the tree breaks the moment the folder is carried elsewhere, which is the point of copying.

#### 2.7 Long Windows paths

Below the working directory the tree's longest file is a picture: `deliverable/` and a run id of 64 characters, a partition folder and a page's stem of at most 45 characters each with an order of four digits, and a picture's 16 hexadecimal characters and extension, 190 characters in all. A copy is at most 153 with its `.part`. So **every file of the tree is within Windows' 260 characters while the working directory's own path is at most 69**. Past that the JDK still writes it (ADR-103); what may fail is another program that does not take long paths, which this record does not decide for.

### 3. What a copy is not

A copy is not a second walk and decides nothing: no verdict, no row, no change to any run. A survivor changed or gone since stage 2 stays listed, written from and scored as stage 2 read it (ADR-206 §5); only its copy is missing, and its entry says why. The manifest's `content_hash` stays the recorded key, so a copy's own digest equals it wherever `copy_outcome` is `copied`.

### 4. A ninth module, `deliverable`, writes the tree

`io.algernon.vespera.deliverable` holds every rule the tree is written by, one class each as ADR-213 has it: the writer, the index, the cluster page, the manifest, the copies, the pictures with ADR-149's and ADR-150's furniture rule, ADR-133's numbering, ADR-174's sentences, ADR-226's 0.0 for a survivor with no score, and the values it is handed: `ArrangedSurvivors`, `ListedPartition`, `ListedSurvivor`, `ListedPicture`, `ListedPicturePlace`, `SurvivorPictures`, `DeliverableProvenance`, `NamedValue` and `DeliverableProgress`. They leave `synthesis`, and `MarkdownSurroundings` and `ArchiveLink` go. **`Unwritten` stays in `synthesis`**, an enumeration whose constants carry no value: the reasons a cluster goes unwritten and the ways of arriving at one are the generation step's, and the sentence each is shown by is `deliverable`'s, `UnwrittenSentences`, so `synthesis` holds no word of a page.

It declares **`@ApplicationModule(allowedDependencies = {"ledger", "synthesis"})`**: it renders `synthesis`'s records, `SynthesisDoc`, `RecordedCluster`, `ArrangedCluster`, `ClusterLabel`, `ClusterSlot` and `Unwritten`, and reads `Citation.AS_WRITTEN` and `FilenameStem.of`, which become public and stay written once where the check and the label read them. `synthesis` names nothing of `deliverable`. It is the second declared exception to the module rule, beside `extraction`'s naming of `corpus`'s two enumerations (ADR-100); `pipeline` handing it plain copies of each record instead would be a second statement of five types kept in step by nothing.

**It declares no logger.** It tells `pipeline` what it did through `DeliverableProgress`, whose every method takes only numbers and enumerations (§8), and `pipeline` writes the lines.

### 5. The tree keys no run

**No stage's version names `deliverable`**, and generation's names `synthesis`, `extraction` and `embedding`. A stage's version names the modules whose rules decide what its run records, and the generation run records the questions it put, the answers it believed and the faults it kept; the tree is drawn from those rows, and drawing it again changes none of them. `profile` leaves generation's list because the one rule it was named for, every key as written on the index (ADR-226), is a rule of how the tree is drawn.

So **a change to `deliverable` alone moves no run id**, and nor does one to `profile` alone, `ledger` or `pipeline`, which no stage names (ADR-222). Arrangement's list, `synthesis`, `extraction` and `embedding`, stands. A value of the profile a stage reads still reaches its run through `config_consumed`, as ADR-271's two keys reach the scoring run's: what leaves generation's version is the code of `profile`, not what the operator writes in it.

`.mvn/scripts/implementation-versions.groovy` writes a line for every package beneath `io.algernon.vespera`, so `deliverable` gets one, read with its parents as ADR-272 has it. No stage's version names it, so an `unknown` written for it refuses no mint.

### 6. Every invocation that reaches the tree draws it

The tree is written by every invocation in which the generation step has an approved arrangement this invocation arrived at (ADR-154), **including one whose step is already recorded finished under its run**: the rows are read as they are on the other paths, the copies made that do not stand, the pages written, and **no question is put to the model**. `ArrangementTasklet` already writes `arrangement.html` from its own already-recorded path (ADR-154 §2). So redrawing the tree never generates it again, and an invocation after a change to `deliverable` redraws the tree it already had, in place, under the same name.

### 7. What the operator is told

The closing line names the index, where it named the folder, and counts the survivors whose copy does not stand: *"The deliverable is at `<absolute path>/index.html`, with N groups left unwritten and M documents not copied."*, `group` and `document` in the singular for 1. M is stated when it is 0, as N is, and is the count of every outcome but `copied` in the tree this invocation wrote, handed from the generation step to the closing line in the job's execution context; where this invocation wrote no tree, the clause is left out. The generation step hands `index.html`'s URI to the `PageOpener` of ADR-266 **once, after the index is in place**, in every invocation that wrote the tree, **on each of the four paths that write it**: stopped, left unfinished, finished, and already recorded finished (§6); it opens nothing where `CI` is set or a Linux has no display, an opener that throws is one warning and fails nothing, and no setting turns it off. The progress page is opened when the job starts and the index when the tree is written, so an invocation that writes the tree opens two pages.

### 8. Nothing outside the tree names a document

- **The lines.** What `pipeline` writes of the copies is counts and outcomes: §2.5's two lines and a counter, `Stage 6b (generation, survivors copied)`. No line names a path, a file name or a document's text, and no exception's message is written, since an I/O exception's message names its path. `DeliverableProgress` takes only numbers and enumerations, so the module has nothing else to hand over.
- **The progress page and the account** carry the counter's line as they carry every progress line, and nothing else of the copies.
- **The copies are the operator's documents.** They lie in a working directory, and **no agent reads one**, as no agent reads the archive they came from (ADR-196). **The guard is not changed**: `deliverable/*/documents/` stays open as the working directory it lies in is (ADR-264 §2), and what keeps an agent from a copy is the written rule, from this record on.

### 9. The run ids that move

This change edits `synthesis`: fifteen classes leave it and two go, `Citation` and `FilenameStem` become public and `Unwritten`'s sentences go. **So arrangement and generation move once**: the arrangement is minted again and must be approved again under its new name, and every cluster is written again under a new generation run, with a tree of its own. Generation's list losing `profile` moves it in the same build. After it, a change to `deliverable` or to `profile` alone moves none.

ADR-271 and ADR-273 are built before this record, and moved the runs they name in their own builds. **So this build moves arrangement and generation on its own**, and no other stage. ADR-273 §4 names generation's version as `synthesis`, `extraction`, `embedding` and `profile`, which it was when ADR-273 was built; from this record's build it is `synthesis`, `extraction` and `embedding` (§5). ADR-273's change, to `embedding` and `pipeline`, would move generation under either list, so what §4 there says moved stands; what no longer holds is that a change to `profile` alone moves generation.

### 10. What the documents say

The implementer's, in the change that builds this:

- **`CONTEXT.md`**: *Archive*'s "the deliverable references the surviving originals where they are instead of copying them" reads that the deliverable holds a copy of each survivor it lists, read from the archive and never written there; *Publication-ready artifact*'s "It carries no copy of the surviving originals: those stay in the archive and are referenced from it" reads that it carries a verified copy of each; *Citation*'s "cluster file" reads "cluster's page".
- **`AGENTS.md`**: the deliverable is a directory of HTML with a copy of every survivor; nine modules, and `deliverable`'s declared exception; the copies under "The operator's documents are never read by an agent"; and the javadoc bullet frees `profile` again beside `ledger`, neither named by any stage's version, where it now says `profile` is named by generation's.
- **README**: "Where things live" names `index.html`, `documents/` and the cluster pages; the closing line and the page that opens.
- **`docs/architecture.md`**: §1's 6b and §2's module table.
- **`docs/running-stage-by-stage.md`**: stage 6b's tree is HTML with its copies, and a generation already finished draws it again.

## Decided here

1. **HTML only**, no Markdown beside it (§1). Nothing reads the `.md`, and two renderings of one tree are two to keep in step.
2. **The writing's Markdown is rendered from a subset of Vespera's own** (§1.3), escaped before any markup is added; whatever is outside the subset is shown as text. A model writing in Markdown is read as a reader expects, and nothing it writes can add markup the renderer did not.
3. **A collision-free flat name for each copy**, where the root-relative path could have been kept (§2.1): it bounds the path, and the path is still on the page and in the manifest.
4. **The recorded key verifies a copy** (§2.2), not a digest of the copy taken afterwards: the key is what everything the tree says rests on.
5. **A copy at its name is trusted on resume** (§2.4), where it could have been hashed again: only the verified move puts one there.
6. **The tree is the only record of a copy** (§2.3): no table, no DDL, and no schema version.
7. **A gibibyte left free** (§2.5), checked before each copy, rather than a total checked once: a copy that would not fit is passed over and a smaller one after it still made.
8. **A module of its own for the tree** (§4, §5), where its classes could have stayed in `synthesis` at the cost of a generation for every change to a page, or gone to `pipeline`, which holds no rule (ADR-226).
9. **The index opens by default** (§7), as the progress page does, with no setting.
10. **The closing line counts the copies that do not stand** (§7), beside the groups left unwritten, even at 0.
11. **The guard stays as it is for the copies** (§8).
12. **What cannot be read counts as none** (§2.5): a free space as 0 bytes, so no copy is made that the drive may not hold, and a size in the survey as 0, since the survey decides nothing.
13. **Only the copies that did not stand are held** (§2.3), by occurrence id, a copy that stands being told by its name.

## Consequences

- **The tree can be carried away and opened anywhere**, with no archive, database or network: every link lands inside it.
- **It costs the archive's survivors again in disk**, measured by the invocation and not before (§2.5), and a second tree under a new generation run costs it a second time. Nothing deletes a tree (ADR-103).
- **A copy is the archive's file as stage 2 read it, or no copy**: one changed or gone since says so on its page and in its row.
- **A page drawn differently is seen at the next invocation, with no question put** (§6), and rewrites the tree under the same name: a tree already handed over is not kept from a change to how it is drawn, only from a change to what was written.
- **The model's Markdown is rendered within the subset, and the rest of it shows as typed**: a table or a link the model writes reads as its characters.
- **An agent is kept from a copy by the written rule alone** (§8).
- **This build mints an arrangement to approve and a generation of every cluster** (§9).
- **Fifteen classes and their tests move to `deliverable`**, and the Markdown surroundings' tests go with them.

## Alternatives refused

- **Markdown beside the HTML.** Nothing reads it (Decided here 1).
- **A Markdown library.** A dependency for a subset of a few constructs, and one whose output would have to be checked against the safety rule rather than built on it (§1.3).
- **The writing shown as typed**, under `white-space: pre-wrap`. A model writing Markdown would be read as markup characters.
- **Rendering links, images, tables or raw HTML.** Each could take a reader out of the tree or carry markup the renderer did not write; none is needed to read connected prose.
- **Refusing the guard to `deliverable/*/documents/`** (§8).
- **Copies under their root-relative paths.** Long paths, names a path parser refuses and two names one case apart (§2.1).
- **A link into the archive where no copy stands.** It breaks when the tree is carried away, and for a changed or gone survivor it leads to the wrong file or to none.
- **Failing the step on a copy that does not stand.** A bad copy is that survivor's, not the run's (§2.3).
- **Hashing each copy again on resume** (Decided here 5), and **a table of copies** (Decided here 6).
- **The tree's classes in `pipeline`**, which would make generation name `pipeline` again (ADR-222) and put eight rules back where ADR-226 took them from; **in `synthesis`**, every change to a page costing a generation of every cluster.
- **Plain copies of `synthesis`'s records for `deliverable`**, keeping the module rule at one exception (§4).
- **Measuring the copies' size before building**, which an agent cannot do without opening the operator's ledger; the invocation says it (§2.5).

## What this does not decide

- **A construct beyond §1.3's subset**, which a later record adds to it.
- **A program opening a path past 260 characters** (§2.7).
- **Removing an earlier tree, or an earlier tree's `.md`.** Nothing deletes in the working directory (ADR-103).

## Tests

| Test | What it holds |
| --- | --- |
| `deliverable.TheTreeIsHtmlTest.writesAnHtmlIndexAndAnHtmlPageForEachClusterAndNoMarkdown` | §1: `index.html`, one `.html` page a cluster, `documents.csv`, and no `.md` anywhere |
| `deliverable.TheTreeIsHtmlTest.everyPageStandsAlone` | §1.1: a doctype, a character set, one inline stylesheet, no script, no stylesheet link and no scheme or host in any `src` or `href` |
| `deliverable.TheTreeIsHtmlTest.escapesEveryValueInTextAndInAttributes` | §1.3: a title, a label, a path, a seed path and a caption carrying `& < > " '` land escaped by the five rules, the caption in `alt` |
| `deliverable.TheTreeIsHtmlTest.rendersTheWritingOnItsPageWithEachCitationALink` | §1.3 on a page: the writing rendered in `<div class="writing">`, each `[n]` a link to `#document-n` and an entry of that id |
| `deliverable.TheWritingIsRenderedFromASubsetOfMarkdownTest` | §1.3: each construct of the subset as exact output — paragraphs and their line breaks, `#` to `###`, `- `, `* ` and `1. ` lists and a line continuing an item, a flattened nested list, `**bold**`, `*italic*`, `_italic_` and italic inside bold, inline code; a citation inside bold, italic, an item, a heading and code; an unterminated `**` and a lone `*` and `_` left as written, `a_b_c` untouched; `####`, a table, a link, an image, a quote and raw HTML as text; `<script>`, `"><img onerror=…>`, `[x](javascript:…)` inert; and over every input of the class, no tag outside the renderer's twelve and no attribute but a citation's `href` |
| `deliverable.TheTreeIsHtmlTest.saysWhyNothingWasWrittenInItalics` | §1.4: ADR-174's words in `<em>` with no asterisk, and the index row with no link |
| `deliverable.TheTreeIsHtmlTest.cutsEverySlugToFortyCharacters` | §1.2 and §2.1: a long label, seed and file name give names of 40 characters of slug after their prefix |
| `deliverable.EverySurvivorIsCopiedTest.copiesEverySurvivorByteForByteAndLinksToTheCopy` | §2.1, §2.2, §2.6: the copy's name, its bytes, the entry's link, the manifest's header and its two cells |
| `deliverable.EverySurvivorIsCopiedTest.aSurvivorChangedSinceItWasReadIsNotCopied` | §2.2, §2.3: no copy and no `.part` at its name, `changed`, no link, and the other survivor copied |
| `deliverable.EverySurvivorIsCopiedTest.aSurvivorGoneFromTheArchiveIsListedAsGone` | §2.3: `gone`, and the rest copied |
| `deliverable.EverySurvivorIsCopiedTest.aCopyThatStandsIsKeptWithoutReadingTheArchive` | §2.4: with the original deleted, a second write keeps the copy unchanged and says `copied` |
| `deliverable.EverySurvivorIsCopiedTest.aCopyThatWouldLeaveLessThanAGibibyteIsNotMade` | §2.5: of two survivors, the one leaving the drive under 1 GiB is `no-room` and the other copied, and the bound is 1 GiB |
| `deliverable.EverySurvivorIsCopiedTest.theArchiveIsLeftAsItWas` | §2.6: every file's bytes and time and the listing beneath the root are as before |
| `deliverable.EverySurvivorIsCopiedTest.namesEachCopyByItsOccurrenceAndAStemOfFortyCharactersAtMost` | §2.1: a name with no letter a slug keeps is its occurrence alone, a long one is cut to 40 characters of slug, one with no extension has none, and only the last extension is kept; the Cyrillic name is skipped where the file system will not create it |
| `deliverable.EverySurvivorIsCopiedTest.aSurvivorThatCannotBeReadIsNotCopied` | §2.3: with a folder at the survivor's path, `not-copied`, no copy and no `.part`, the entry's *could not be copied* and no link, and the other copied |
| `deliverable.EverySurvivorIsCopiedTest.aDriveWithNoFreeSpaceReadTakesNoCopy` | §2.5 and Decided here 12: with 0 bytes free, every outcome is `no-room` and nothing stands under `documents/` |
| `deliverable.EverySurvivorIsCopiedTest.aFreeSpaceThatCannotBeReadCountsAsNone` | §2.5 and Decided here 12: the shipped reading of the drive's free space, asked directly, is 0 for a working directory beneath a file |
| `deliverable.EverySurvivorIsCopiedTest.aSizeThatCannotBeReadCountsAsNoneInTheSurvey` | §2.5 and Decided here 12: the survey is made once and counts both survivors, the bytes of the one whose size could be read and 0 for the one gone, and the free bytes; each survivor meets its own outcome |
| `deliverable.EverySurvivorIsCopiedTest.aPartLeftByAStoppedWritingIsWrittenOver` | §2.4: a `.part` longer than the document, left at its copy's name, is written over: the copy holds the document's bytes alone and no `.part` is left |
| `deliverable.WhatTheTreeReportsNamesNoDocumentTest` | §8: every method of `DeliverableProgress` takes only primitives and enumerations |
| `pipeline.TheTreeKeysNoRunTest` | §5: generation's version names `synthesis`, `extraction` and `embedding`, arrangement's stands, and no stage names `deliverable` |
| `pipeline.RunIdentityGoldenTest.generation` | §5: generation's recorded version is `synthesis+extraction+embedding` |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | §4: the class `pipeline` names the tree by is `deliverable.Deliverable`, read among the capability types, and `GenerationTasklet` names the ten moved types as `deliverable`'s |
| `ModuleBoundariesTest.theDeliverableIsAModuleOfItsOwn` | §4: a module `deliverable` declaring `ledger` and `synthesis`, among nine recorded modules and two declared exceptions |
| `pipeline.TheTreeInHtmlInvocationTest.aCampaignLeavesAnHtmlTreeWithACopyOfEverySurvivor` | §1, §2: a whole campaign's tree, each copy equal to its file, the archive unchanged |
| `pipeline.TheTreeInHtmlInvocationTest.aFinishedGenerationIsDrawnAgainWithoutAQuestion` | §6 and §7: with the step recorded finished, the index is written again, a copy taken away is made again and says `copied`, no question is put, no run is minted, and the index is opened once with the page in place |
| `pipeline.TheTreeInHtmlInvocationTest.theClosingLineNamesTheIndexAndTheIndexIsOpenedOnce` | §7: the index by its whole path and `0 documents not copied`, opened once with the page in place |
| `pipeline.GenerationBreakerInvocationTest.aStoppedStepOpensTheTreesIndexOnce` | §7: with the step stopped by five answers turned down, the index is opened once with the page in place |
| `pipeline.GenerationBreakerInvocationTest.anUnfinishedStepOpensTheTreesIndexOnce` | §7: with the step left unfinished, eight groups standing with a fault, the index is opened once with the page in place |
| `pipeline.TheTreeInHtmlInvocationTest.anInvocationThatWritesTheTreeOpensTwoPages` | §7 and ADR-266 §2 as amended: exactly two pages are opened, the progress page and then the index |
| `pipeline.TheTreeInHtmlInvocationTest.theCopiesAreToldBeforeAndAfterInCountsOnly` | §2.5 and §8: two lines of the copies, every argument a number: before, the survivors about to copy, their bytes and the free bytes; after, the count of each outcome and the bytes copied; neither names a document, the archive or the tree |
| `pipeline.TheClosingLineCountsTheCopiesThatDoNotStandTest.leavesTheCopiesOutWhereThisInvocationWroteNoTree` | §7: with no count handed over, the line names the index and ends at the groups left unwritten, with nothing of copies |
| `pipeline.TheClosingLineCountsTheCopiesThatDoNotStandTest.countsTheDocumentsNotCopiedEvenAtNone` | §7: `0 documents`, `1 document` and `2 documents not copied` |
| `pipeline.TheTreeInHtmlInvocationTest.aSurvivorGoneBeforeTheTreeIsNamedNowhereOutsideIt` | §2.3, §7 and §8: `gone` in the manifest, the closing line's `1 document not copied`, and no line after it went and no progress page names it |

**Moved or rewritten with the classes** (§4): the tests of the tree in `synthesis` (`DeliverableTest`, `ClusterFileTest`, `ClusterPageTest`, `IndexPageTest`, `ManifestCsvTest`, `EntryPicturesTest`, `DeliverablePicturesTest`, `DeliverableProgressTest`, `FurnitureIsDecidedOverTheWholeTreeTest`, `ManifestIsWrittenAPageAtATimeTest`, `PartitionNoSurvivorNamesStopsTheWriterTest`, `SeedPartitionsAreOrderedFromTheirSizesTest`, `TreeIsTheSameWrittenAPartitionAtATimeTest`, the three `TreeThat…`/`TreeWhose…` tests, `ListedSurvivorTest`, `DifferenceHashTest`, and the fixtures `ListedArrangement` and `ThreePartitions`) move to `deliverable` and read HTML, beside two new fixtures, `ListedTree` and `UnwrittenSentencesOnRecord`. `pipeline.UnwrittenPageWordingTest` moves to `deliverable.UnwrittenPageWordingTest`, its `keepsTheShapeOfTheLineItReplaces`, a sentence wrapped in Markdown's asterisks, restated as `isOnePlainLineOfText`, the asterisks dropped (§1.4). `synthesis.ClusterSlotTest.everyValueOfOneClusterGivesOneKey` is `anArrangedClusterGivesItsKey`, and its half about a survivor moves to `ListedSurvivorTest.aSurvivorGivesItsClustersKey`, with `ListedSurvivor`. `DeliverableRulesHaveOneHomeTest.oneClassCarriesAMarkdownEscapingRule` is `oneClassEscapes` and `theOtherTwoSurroundingsHaveOneClassEach` is `theCsvsQuotingHasOneClass`, and its tests of the collaborators, the stem and the slot read both modules. `EveryPageIsMovedIntoPlaceByTheMoveThatTriesAgainTest`, `ModuleBoundariesTest`, `NoClassOfTheLastThreeStagesReadsAWholeRunTest` and `PipelineHoldsOnlyTheRulesOnRecordTest`, and in `pipeline` `DeliverableInvocationTest`, `DeliverablePicturesInvocationTest`, `GenerationFaultInvocationTest`, `RunIdentityGoldenTest`, `TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` and the fixture `UnwrittenPage`, follow the move, the names and the HTML. `synthesis.FilenameStemTest` and `synthesis.WhyAClusterIsUnwrittenTest` stay in `synthesis` unchanged, with `FilenameStem` and `Unwritten`; and the three `ClosingLine…` tests, `TranslationKeysInTheClosingLineTest` and `NextActionTest` are unchanged, the copies' clause of the closing line being held by the tests in the table above.

**Retired**, each holding a rule this record removes, and nothing put in its place but what the table above holds:

- **`synthesis.ArchiveLinkTest`**, deleted: a membership entry's relative link into the archive and its destination's percent-encoding (ADR-135, ADR-137), since no entry links into the archive (§2.6).
- **`synthesis.MarkdownSurroundingsTest`**, deleted: what each of the five Markdown surroundings escapes (ADR-134, ADR-136, ADR-137, ADR-138, ADR-148), which §1.3's one rule replaces.
- **`synthesis.TheTreeWrittenFromLists`**, the golden tree of Markdown files captured from `ThreePartitions`' lists before ADR-223 was built, deleted: `TreeIsTheSameWrittenAPartitionAtATimeTest` holds the HTML tree written a partition at a time byte for byte equal to the one written with every document in one page instead.
- **`EntryPicturesTest.indentsByTheWidthOfTheEntrysNumber`**, dropped: a picture indented under a two-digit Markdown list item so that it stays inside the entry, which an `<li>` holding its pictures needs no rule for.
- **`DeliverableRulesHaveOneHomeTest`**, four claims: that `ArchiveLink` alone writes `%26`, `%28` and `%29`; that `MarkdownSurroundings` is the one class writing a backslash before markup, which becomes that `Html` alone writes the five entities and no class of `synthesis` or `deliverable` writes such a backslash; a regex replacement writing a backslash before what it matched, among the forms counted as a Markdown escape; and `MarkdownSurroundings`, `ArchiveLink`, `Citation` and `FilenameStem` among the package-private collaborators, the first two being gone and the last two public in `synthesis`, which the test still finds there.
- **`DeliverablePicturesTest.indentsAPictureByTheWidthOfItsEntrysNumber`**, dropped, for the reason `EntryPicturesTest`'s is.
- **`ClusterFileTest.statesTheDocumentWithoutALinkWhereTheArchiveIsOnAnotherVolume`** and **`…WhereTheArchiveRootIsItselfRelative`**, dropped: a route from a page into the archive (ADR-135), which no entry is given (§2.6).
- **`ClusterFileTest`'s claim that no entry's anchor sits on a line of its own**, dropped from `listsEveryMemberInScoreOrder…`: a rule of Markdown's paragraphs that an `<li>` does not have (§1).

**Restated rather than retired**, each test kept and its claim turned to what this record decides:

- the backslash escapes of a pipe, a bracket, a backtick, `<` and `&`, "escaped once, not twice", "no live backtick" and "a backslash rather than an entity", which §1.3's one entity rule replaces (`DeliverableTest.keepsTheTableWhole…`, `keepsTheLinkWhole…`, `keepsTheCellWhole…`, both `keepsATagVisible…`, `keepsABracketedName…`, `keepsABacktickedName…`; `ClusterFileTest.keepsATitleThatIsItselfAnImage…`, `keepsAnAmpersandInAnEntry…`, `keepsABacktickInAnEntryAndAHeading…`; `DeliverablePicturesTest.escapesACaptionWrittenAsAltText`);
- a line break folded in a label, a title or a caption, which is now kept (§1.3; `ClusterPageTest`'s unwritten label);
- the percent-encoding of an entry's route, `%20`, `%22` and `%26`, which a copy's name makes unneeded (§2.1): `ClusterFileTest.linksEveryMembershipEntryToTheOriginalByAPathRelativeToThePage` is `…ToItsCopyByAPathRelativeToThePage`, `encodesTheAmpersandInTheRouteToADocumentWhoseNameSpellsAnEntity` is `routesADocumentWhoseNameSpellsAnEntityToItsCopy`, and `escapesCharactersThatWouldBreakTheLink` is `keepsANameWithASpaceOrAQuoteReadableAndFollowable`;
- an entry for which no path could be made from the corpus root, which now reads that the document could not be copied (§2.3);
- the byte-for-byte Markdown pages of `ClusterPageTest` and `DeliverablePicturesTest.THE_PAGE_WITH_NO_PICTURES`, restated as HTML (§1, §1.4), and `ClusterPageTest.rewritesEachCitationAsALinkToItsEntry`, which reads through `ProseHtml.of` (§1.3);
- that the tree never copies a document and never opens one to write it (ADR-206 §4), which §2 reverses: `DeliverableTest.writesTheWholeTreeWithoutReachingTheArchive` holds every row `gone` and no copy standing, `DeliverableInvocationTest.copiesNoOriginalIntoTheTree` is `copiesEverySurvivorIntoTheTreeAndLeavesTheArchiveAsItWas`, `writesAPageWhoseCitationLeadsToADocumentOfTheArchive` is `…ToACopyOfADocumentOfTheArchive`, and `fillsTheContentHashOfADocumentGoneBeforeTheTreeIsWritten` holds that a document is opened only to be copied.
