# ADR-303 — A cluster's page past the profile's byte ceiling is split at an entry's end into pages, and every drawing writes the pages' sizes

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Settles**: [#571](https://github.com/algernon28/vespera/issues/571), of map [#551](https://github.com/algernon28/vespera/issues/551). Three rulings are the operator's: the ceiling ships unset as a profile value, and unset means one page for each cluster, never split, the tree drawn as before; stage 6b writes a report of the pages' sizes at every drawing, numbers only, naming the key; and no number of bytes is offered as a default, an example or a suggestion anywhere. §1 to §13 are decided under the operator's delegation of 2026-10-19.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1.2 and §1.4, in these places and no others: a cluster's page may be several pages, `index.html` the first and `index-<k>.html` the others, each holding part of the membership list under the same heading, and the writing, its disclosure and the sentence of an unwritten cluster stand on the first alone (§4, §5).
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §0, whose cluster folder holds its page or pages and its copies; and §1.5, whose reserved `index-<digits>.html`, and ADR-286 §3's `index-<digits>.html.part`, are now written (§4). The reservation itself is unchanged.
- **Amends**: [ADR-286](0286-a-clusters-page-is-one-file-written-an-entry-at-a-time-through-its-part-with-its-pictures-and-a-png-or-jpeg-image-inlined.md) §2, whose one write of a page is also its measurement, and whose "this record writes one file per cluster" holds only where no ceiling splits it: a cluster past the ceiling is written a second time, as its pages (§3); and §4, whose files counted as left behind exclude every page this drawing wrote and include a page an earlier drawing wrote past the last one (§4).
- **Amends**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) and [ADR-135](0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md), in a citation's `href` alone: on a split cluster's first page, a citation of an entry on another page names that page before the anchor, `./index-<k>.html#document-<n>` (§6). The anchor, `id="document-<n>"`, stands, and so does every entry's link to its copy.
- **Amends**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), in the names inside a cluster folder: a cluster's further pages are `index-2.html` to `index-<N>.html` (§4). The order, its prefix and the folder's name stand, and the index links the first page as it did (§7).
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §5, narrowly: a key may enter no run, and a change of one costs no run, closes no approval and refuses no labels (§9). Every other key's earliest run and cost stand.
- **Amends**: [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §9, whose list of what is still held gains, for the drawing, one cluster's entry sizes while that cluster is planned, and three numbers for each cluster of the run for the report (§3, §8).
- **Keeps**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md), one synthesis doc per cluster from its sample, and [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md)'s numbering; [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §6, the ten pictures a document shows, and ADR-274 §2.5, the gibibyte kept free, each a bound of the tool that stays a bound (§10); ADR-274 §5, the tree keys no run, and §6, every invocation that reaches the tree draws it; [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md), every key on the index, which the new key reaches with no code of its own; [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md), the deliverable learns no `Profile`; [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md) and [ADR-282](0282-an-occurrence-with-fewer-words-than-the-word-floor-is-degenerate-output-whatever-its-detected-format-and-stage-3-writes-the-page-the-floor-is-read-off.md) §3, a value no reading counts is ignored and quoted back; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), a reader's words say *group*.
- **Rests on**: a reading of `deliverable.ClusterPage`, `ProseHtml`, `Html`, `Deliverable.writeTo`, `DeliverableProgress`, `CopyNames`, `ledger.ReplacingMove`, `profile.Profile` and `NumericValue`, `pipeline.DegenerateOutputWordFloor`, `NextAction`, `GenerationTasklet` and `StageModules`, and `controlpanel.ProfileKey` and `ProfileEdit`. No archive, working directory or tree was opened for this record (ADR-196, ADR-283), and no page of any corpus was measured.

## Context

**A cluster's page is one file.** ADR-286 writes it to `index.html.part` an entry at a time, its pictures and its PNG and JPEG images inlined as base64, a third larger than their bytes, and moves it over `index.html`. The writer counts the bytes it has handed on and tells them at each entry's end, through `ClusterPage.write`'s `LongConsumer entryEnds`, which `Deliverable.writeTo` hands a consumer that does nothing.

**Nothing bounds a page.** A cluster can hold nearly every survivor of its seed partition (ADR-211), and every one of its entries is listed (ADR-104), each with up to ten pictures (ADR-149 §6). ADR-280 §1.5 reserved `index-<digits>.html`, and ADR-286 §3 its `.part`, for further pages, and no copy stands at either name.

**What a page links to.** A citation `[n]` of the writing links `#document-n` on the same page (ADR-109, ADR-135); every entry links its copy beside the page (ADR-280 §5). The index links each cluster's `index.html`.

**What is not known.** How large any corpus's pages grow, and how large a page a browser opens comfortably, were measured by no record. So this record chooses no number.

## Decision

### 1. What is measured

**A page's size is the bytes of its file as written**: UTF-8, its pictures inlined as `data:` URIs (ADR-286 §1), its head and its tail included, which is its size on disk. **A cluster's one-page size** is the size of its page written whole, as ADR-286 writes it.

### 2. The key, `clusterPageByteCeiling`

- **`Profile` gains `NumericValue clusterPageByteCeiling`**, declared after `generationContextWindow`, beside the other value read at 6b. `Profile.keysAsWritten` lists it, so it is on the index by ADR-186 with no code of its own, and census writes it unset into a fresh profile.
- **It is a whole number of bytes from 1 to 9,223,372,036,854,775,807**, `Long.MAX_VALUE`, read exactly from the text as written, so `2.0` is `2` and `9223372036854775808` is no ceiling. **Unset, nothing is split.** Text no number is read from, `NaN`, an infinity, `0`, a negative number, a fraction and a number past `Long.MAX_VALUE` are **ignored**, as if unset, and the closing line says so (§2.1).
- **`pipeline.ClusterPageByteCeiling` reads it one way for everyone who asks**, in the shape of ADR-282 §3's `DegenerateOutputWordFloor`: `Unset`, `Ceiling(long bytes)`, `Ignored(String text)`, the text trimmed to be quoted back. The generation step hands `deliverable` what it reads as a value of `deliverable`'s own (§11), and the closing line reads it through the same class.
- **It is never a gate**: no invocation stops for it, set, unset or ignored, and it is never among `next-invocation.properties`' `wants` (ADR-294). It is in no stage's `config_consumed` (§13).

#### 2.1 The closing line names an ignored ceiling

Where this invocation drew the tree and the ceiling is ignored, the closing line's sentence about the deliverable gains, after every clause it has and before its last full stop: *`; clusterPageByteCeiling reads "<text>", which is not a whole number of bytes of 1 or more, so it was ignored and no group's page was split`*. The line stays one line, and its file's contents are those of the line without the clause. Unset, or set and read, the line says nothing of it. Where no tree was drawn, nothing is said: the ceiling is read only by a drawing, and a value wanted before it would be a gate.

### 3. When a cluster becomes several pages

- **Only where the ceiling is set and the cluster's one-page size is greater than it.** At or under the ceiling, unset or ignored, the cluster has one page, byte for byte the page ADR-286 writes. **A cluster with no listed member, or with one entry, is never split**, whatever its size: it has no entry's end to split at.
- **Past the ceiling, it is written as N ≥ 2 pages.** **Every page is at most the ceiling, except a page holding one membership entry that alone takes it past**: on the first page, the heading, the writing, its disclosure and that one entry. Each page holds at least one entry.
- **Pages are filled greedily in membership order**, ADR-133's numbering unchanged: a page takes the next entry while it stays within the ceiling, and the first entry that would take it past begins the next page.
- **The split is planned from the one-page write.** That write, to `index.html.part` as ADR-286 has it, tells each entry's end through `entryEnds`, which gives every entry's exact size; it also gives the size of everything before the first entry and after the last. What depends on the plan is counted at an upper bound, so that no page comes out larger than planned: the page numbers in the navigation (§5) at `digits(entryCount + 1)` digits each, and every citation on the first page as if it carried the longest page name (§6).
- **The pages are then written**, a second write of that cluster alone, each entry's pictures asked of the source again as its entry is written (ADR-149 §9's second pass, once more for that cluster). **An entry is rendered the same on whichever page it lands.**
- **Progress counts each membership entry once and each cluster once**, on the first write; the second write tells nothing again.
- **While a cluster is planned, its entries' sizes are held**, one number an entry, and let go when its pages are written (§8's line of ADR-220 §9).

### 4. Names, and how the pages are put in place

- **The first page is `index.html`; page k, from 2, is `index-<k>.html`**, k in decimal with no leading zero. Both names are ADR-280 §1.5's, already reserved, and their parts ADR-286 §3's.
- **Each is written to its name with `.part` after it** and moved into place by `ReplacingMove`, as ADR-286 §3 moves a page. **Pages 2 to N are moved first, in page order, and the first page last**, so a reader who reaches `index.html` finds every page it links. A move that fails fails the drawing, as ADR-286 §3 has it.
- **Files left behind** (ADR-286 §4): the names this drawing gave are `index.html`, `index-2.html` to `index-<N>.html` where the cluster is split, and its copies. **A page an earlier drawing wrote past this drawing's last** (`index-7.html` where this drawing wrote five pages, or `index-2.html` where it wrote one) is counted among the files left behind, never named and never deleted.

### 5. What each page holds

- **An unsplit page is byte for byte ADR-286's**: no navigation and no word of pages.
- **On every page of a split cluster** the `<title>` and the `<h1>` are the cluster's title, or its label where nothing was written (ADR-106), the `<h1>` on every page. **A navigation line follows the `<h1>` and stands again after the list**, before the tail:

  `<p class="pages">Page <k> of <N>: <links></p>`

  where `<links>` are, in this order and joined by `, `, `<a href="./index.html">first</a>`, `<a href="./<previous>">previous</a>`, `<a href="./<next>">next</a>` and `<a href="./<last>">last</a>`, each page named as §4 names it. **A link is left out where it would point at the page itself or at no page**: the first page has no first or previous, the last no next or last.
- **The writing, its disclosure and the sentence saying why nothing was written stand on the first page alone.**
- **Each page's list is headed `The documents in this group`** and is `<ol start="<m>">`, m the number of its first entry, `start` left out on the first page, so the numbers run on across pages. **Every entry keeps `id="document-<n>"`**, n its number in the cluster, so each id is on one page of the cluster and unique across them.
- So a split page is, in order: `Html.head`, the `<h1>`, the navigation, on the first page the writing or the sentence, the heading, the `<ol>`, its entries, `</ol>`, the navigation, `Html.tail`.

### 6. Citations across pages

On a split cluster's first page, a citation `[n]` links `#document-<n>` where entry n is on the first page, as ADR-135 has it, and `./index-<k>.html#document-<n>` where entry n is on page k. A citation of a number no entry has links `#document-<n>`, as it does today. **`ProseHtml` takes the page each number is on**; the escaping comes first, as ADR-274 §1.3 has it, and the page's name is one §4 makes, of letters, digits, `-` and `.`. **ADR-286 §5's test of every link** reads such a link as a relative path to a file inside the tree whose fragment names an `id` on that file.

### 7. The index and the manifest

**The index's row for a cluster links its first page, `index.html`, exactly as it did**: one row for each cluster in the order approved (ADR-112), and no count of pages. **`documents.csv` is unchanged**: a copy stands in its cluster folder whichever page lists it.

### 8. The report, `cluster-page-sizes.html`

- **Written at every drawing, whatever the ceiling**, in the working directory beside the other reports, by `deliverable`, which is handed the working directory already, through `cluster-page-sizes.html.part` moved into place by `ReplacingMove`, after the index and the manifest are in place. A drawing that fails writes none. **A report that cannot be written fails nothing**: the tree is already in place, the report is the operator's evidence and not part of the tree, so where its folder cannot be written or its move is refused it is left out, its `.part` removed, as a listing that cannot be made is a courtesy left out (ADR-280 §4).
- **Numbers and fixed words only.** No cluster, seed partition, document or path is named, and no title or label is shown, so the page can be sent to anyone, as ADR-274 §8 has it of what is reported outside the tree. No line of the log names anything of it either.
- **What it holds**, as tables of `<th>` and `<td class="count">`, every number written as plain digits with no separator, so that one can be copied into `profile.yaml` as it is:
  1. **The key and how it reads**: `clusterPageByteCeiling` and its bytes, or `unset`, or `ignored`, the ignored text never quoted.
  2. **How many groups**, and the one-page sizes' **largest**, **95th percentile** and **median**, in bytes, each percentile by nearest rank: the value at rank ⌈p × n / 100⌉ of the n sizes in ascending order; with no cluster, the three read `none`.
  3. **How many groups' one-page size is larger than each of four marks**, 100,000, 1,000,000, 10,000,000 and 100,000,000 bytes, powers of ten chosen so that none reads as a value to set.
  4. **The ten largest one-page sizes**, largest first, ties in the order the clusters were drawn, each with its rank, its size, how many membership entries its list holds, and how many pages were written for it; fewer rows where there are fewer clusters.
  5. **Where the ceiling is set**, and only then: how many groups it split, how many pages were written in all, and how many pages are larger than the ceiling, counting every page written over it, the single page of a cluster of one entry among them.
- **What it holds in memory**: three numbers for each cluster of the run, its one-page size, its entries and its pages, bounded by the run's clusters and never by its survivors, recorded here against ADR-220 §9's rule.
- **Its rows, exactly.** A row stating one number under a label is `<tr><th><label></th><td class="count"><value></td></tr>`, and the labels are, in this order: `clusterPageByteCeiling`; `Groups`; `Largest, in bytes`; `95th percentile, in bytes`; `Median, in bytes`; `Larger than 100000 bytes`, `Larger than 1000000 bytes`, `Larger than 10000000 bytes` and `Larger than 100000000 bytes`; and, where the ceiling is set, `Groups split`, `Pages written` and `Pages larger than the ceiling`. The ten largest are a table headed `<tr><th>Rank</th><th>Bytes</th><th>Documents listed</th><th>Pages written</th></tr>`, each row four `<td class="count">` cells. Its `<title>` and `<h1>` are `Group page sizes`; a paragraph above the tables says what is measured and that `clusterPageByteCeiling` in `profile.yaml`, a whole number of bytes, splits every page larger than it at an entry's end, and offers no number.
- **It is no report a gate reads**, and no profile key points at it.

### 9. The control panel

`ProfileKey` gains `CLUSTER_PAGE_BYTE_CEILING("clusterPageByteCeiling", NUMBER)`, in the profile's order, **with no earliest run**: its cost lists no run, closes no approval and refuses no labels. ADR-295's check of a value refuses anything but a whole number of bytes from 1 to `Long.MAX_VALUE`, read exactly: `clusterPageByteCeiling is not saved: "<value>" is not a whole number of bytes from 1 to 9223372036854775807`. It is told nothing else.

### 10. Bounds stay bounds

ADR-149 §6's ten pictures a document shows and ADR-274 §2.5's gibibyte left free are bounds of the tool and stay in code. The ceiling is the operator's and is the one value of the page's size a profile holds.

### 11. Where it is written

- **`deliverable.ClusterPageCeiling`**, public, a sealed interface of three records, `Unset()`, `Bytes(long bytes)`, `bytes` from 1 or `IllegalArgumentException`, and `Ignored()`, with `String KEY = "clusterPageByteCeiling"`, the name the report states. A plain value, so `deliverable` learns no `Profile` (ADR-110).
- **`Deliverable.writeTo(Path workingDirectory, DeliverableRoot root, DeliverableProvenance provenance, ArrangedSurvivors source, ListedCodeSets code, SurvivorPictures pictures, ClusterPageCeiling ceiling, DeliverableProgress progress)`**, public, and the same with `ReplacingMove moves` last, package-private. Every existing form hands `Unset`. `Deliverable.CLUSTER_PAGE_SIZES_FILE_NAME` is `cluster-page-sizes.html`.
- **`ProseHtml.of(String markdown, IntFunction<String> pageOf)`**, package-private: `pageOf` answers, for a citation's number, `""` where its entry is on this page and the page's name, as `index-2.html`, where it is on another; `ProseHtml.of(String)` is `of(markdown, n -> "")`.
- **`DeliverableProgress.toDrawClusters(long clusters)` and `clusterDrawn()`** take the place of `toWriteClusterFiles` and `clusterFileWritten`, whose names say *cluster file*, which `CONTEXT.md`'s *Cluster page* refuses, and which count a cluster once whatever its pages (§3). `GenerationTasklet`'s line for them keeps its words. For the same name, the test class `deliverable.ClusterFileTest` is `deliverable.ClusterPageLinksAndWordingTest`, its tests unchanged.
- **`pipeline.ClusterPageByteCeiling`**, package-private, sealed: `KEY`, `of(NumericValue)`, `Unset`, `Ceiling(long bytes)`, `Ignored(String text)`, and `ClusterPageCeiling forTheTree()`, which hands `Ignored` without its text. `PipelineHoldsOnlyTheRulesOnRecordTest` lists it as holding no rule that shapes a stage's output: it reads a value the tree is drawn by, and since ADR-274 §5 the tree is no stage's output.
- **`GenerationTasklet`** hands `ClusterPageByteCeiling.of(profile.clusterPageByteCeiling()).forTheTree()` to `Deliverable.writeTo`, and **`NextAction`** words §2.1's clause from the profile it already reads.
- **`controlpanel.ProfileKey.earliestRun()`** answers `Optional<Run>`, empty for this key alone, and `cost()` answers `new Cost(List.of(), false, false)` for it.

### 12. What the documents say

`CONTEXT.md` gains *Cluster page*, and *Cluster folder* holds the page or pages; `docs/architecture.md`'s 6b and the `deliverable` row; the README's table of values, the paragraph of optional values, *Where things live* and *Where the run ends*; `docs/running-stage-by-stage.md`'s 6b row and section.

### 13. No run id moves

**None moves, and setting or changing the key moves none.** Splitting a page is drawing, not generation: 6b still writes one synthesis doc for each cluster from its sample (ADR-108), and the clusters are 5f's, unchanged. The tree keys no run, and no stage's version names `deliverable` or `profile` (ADR-274 §5); the key is in no stage's `config_consumed`. Every invocation that reaches the tree draws it again (ADR-274 §6), so a new value shows at the next one that reaches it, with no question put to the model.

## Decided here

1. **The measure is the file's bytes as written, pictures inlined** (§1), under the operator's delegation: it is what a browser opens and a disk holds, and it is what ADR-286's count already gives.
2. **`clusterPageByteCeiling`, a whole number of bytes from 1, read exactly, after `generationContextWindow`** (§2), under the operator's delegation: a count of bytes is whole, a ceiling of 0 would split every page into single entries for no reader's gain, and reading the text exactly keeps `Long.MAX_VALUE` the largest ceiling and nothing past it.
3. **An ignored value named on the deliverable's sentence and nowhere wanted** (§2.1), under the operator's delegation: the operator ruled that the key is never a gate and never in `wants`, so it is said where the tree it would have split is named, in ADR-282 §3's words.
4. **Split only past the ceiling, and never a cluster with no listed member or with one entry** (§3), under the operator's delegation: at the ceiling the page fits, and a list of no entry or of one has no entry's end at which to split.
5. **Greedy fill, a single entry over the ceiling allowed alone** (§3), under the operator's delegation: an entry is never cut, so an entry larger than the ceiling has a page of its own and no other is pushed past it.
6. **Planned from the one-page write, the variable parts at an upper bound, and the cluster written twice** (§3), under the operator's delegation: the entries' sizes are known only once written, the page is held nowhere whole (ADR-286 §2), and an upper bound keeps every planned page within the ceiling.
7. **`index.html` and `index-<k>.html`, the first page moved last** (§4), under the operator's delegation: both names are reserved already, and a reader reaching the first page finds the rest.
8. **A navigation line above and below the list, links left out where they point at the page itself** (§5), under the operator's delegation: a reader at either end of a long list can move on, and no link leads nowhere.
9. **The writing on the first page only, the numbering run on with `start`** (§5), under the operator's delegation: the writing is one text, and each number stays the entry its citation names.
10. **A citation names the page its entry is on** (§6), under the operator's delegation: ADR-109's promise that a citation lands on its entry holds across pages.
11. **The index and the manifest unchanged** (§7), under the operator's delegation: the first page is the cluster's page as a reader already finds it.
12. **A report of numbers at every drawing, its marks powers of ten** (§8), under the operator's delegation, the report itself being the operator's ruling: the operator reads the sizes before choosing, and no mark or example is a value to copy.
13. **A key with no earliest run on the control panel** (§9), under the operator's delegation: a change of it mints nothing, so its cost is none.
14. **`toDrawClusters` and `clusterDrawn`** (§11), under the operator's delegation: a cluster is counted once whatever its pages, and *cluster file* is refused for its page.
15. **No run id moves** (§13), under the operator's delegation, following ADR-274 §5 and §6.

## Consequences

- **With the key unset, nothing a reader sees changes**: every page is byte for byte ADR-286's, and `cluster-page-sizes.html` appears beside the other reports.
- **With the key set, a large cluster opens on its first page** and is read a page at a time, every citation still landing on its entry, every entry on its copy.
- **A cluster past the ceiling is written twice and its pictures asked for a third time**, so a drawing with a ceiling takes longer by about the time those clusters take to write.
- **Lowering the ceiling, or setting it back to unset, leaves earlier pages in a folder**, counted on the closing line and never deleted.
- **A page can still be larger than the ceiling** where one entry alone is, and the report counts such pages.
- **No run id moves** (§13).

## Alternatives refused

- **A default ceiling, or a value suggested on any page**: the operator ruled the key ships unset and that no number is offered, and no corpus's pages were measured.
- **Counting characters, entries or pictures instead of bytes**: a page's weight is its bytes, its pictures being most of them; a count of entries says nothing of an entry of ten pictures.
- **Cutting an entry across pages**, or moving its pictures to a page of their own: an entry is one document's, and its number has to name one place.
- **Measuring with a separate pass before writing**: the page is held nowhere whole, and the one-page write is already that measurement.
- **Repeating the writing on every page**: one text on several pages is several places to read it, and its citations already lead to the other pages.
- **A page count, or a link to each page, on the index**: the first page leads to the rest, and the index stays one row for each cluster.
- **Making the ceiling a gate, or a value the closing line wants**: the operator ruled it never one.
- **A key of the stage's run**: splitting is drawing, and ADR-274 §5 keeps drawing out of every run.
- **Deleting the pages an earlier drawing left past the last**: nothing beneath a deliverable root is deleted (ADR-276 §3).

## What this does not decide

- **The number itself**, which is the operator's to read off `cluster-page-sizes.html` and set, or to leave unset.
- **How large a page a browser opens comfortably**, which no browser was measured for here.
- **The index growing past any size**: it holds one row for each cluster and is split by nothing here.
- **The control panel's screen for the key**, which ADR-295's table now carries for the screens.
- **The Confluence rendering's cluster pages** ([ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md)): whether one is split, and under which value, is map [#604](https://github.com/algernon28/vespera/issues/604)'s to record once [#611](https://github.com/algernon28/vespera/issues/611) has measured the request cap; `clusterPageByteCeiling` splits the HTML tree's pages alone.

## Tests

| Test | What it holds |
| --- | --- |
| `deliverable.AClusterPastTheCeilingIsSplitIntoPagesTest.anUnsetCeilingWritesThePageAsItWasWritten` | §3, §5: unset, the one page is byte for byte the page written before this record, and the folder holds one page |
| `…aCeilingAtTheOnePageSizeWritesOnePageAndOneByteLessSplitsIt` | §3: a ceiling equal to the one-page size writes one page byte for byte the unset one; one byte less splits it |
| `…pagesAreFilledGreedilyAndEachIsWithinTheCeiling` | §3: five entries of near 5,000 bytes, a ceiling of the fixed parts and two and a half entries, give pages of 2, 2 and 1 entries, each within the ceiling |
| `…anEntryLargerThanTheCeilingHasAPageOfItsOwn` | §3: the one page past the ceiling holds one entry; the others are within it |
| `…aClusterWithNoListedMemberOrOneEntryIsNeverSplit` | §3: a ceiling of 1 leaves each one page, byte for byte the unset one |
| `…everyEntryIsOnExactlyOnePageInOrderNumberedOnWithItsId` | §5: every `id="document-n"` once across the pages, in order, each page's `<ol start>` its first entry's number and none on the first |
| `…eachPageCarriesTheTitleTheNavigationAboveAndBelowTheListAndTheWritingOnlyOnTheFirst` | §5: each of three pages whole, built from the unsplit page: the title, both navigation lines, the heading, its entries, the writing and the unwritten sentence on the first page alone |
| `…theFurtherPagesAreMovedIntoPlaceBeforeTheFirst` | §4: `index-2.html`, `index-3.html`, then `index.html`, and no `.part` left |
| `…aPageAnEarlierDrawingWrotePastTheLastIsCountedAndKept` | §4: a planted `index-7.html` beside three pages counts 1; a redrawing unset counts the two earlier further pages; a redrawing split counts 0; none is deleted |
| `…eachEntryAndEachClusterIsToldOnceWhetherOrNotItIsSplit` | §3: the progress hears every entry once and every cluster once with a ceiling of 1 |
| `deliverable.ACitationLinksToThePageItsEntryIsOnTest` | §6: `ProseHtml.of` with the page of each number: `#document-1` on this page, `./index-2.html#document-3` on another; the one-argument form unchanged; and on a drawn first page, each citation to its entry's page |
| `deliverable.TheTreeIsHtmlTest.everyLinkOfASplitClusterLandsOnASiblingPageAndAnIdOnIt` | §6, ADR-286 §5: a tree split at a ceiling of 1, every link a fragment on its page, a file in the tree, or a sibling page with a fragment naming an `id` on it, and each cluster folder its pages and its copies |
| `deliverable.TheClusterPageSizesReportTest.theReportStatesTheSizesTheMarksAndTheTenLargestInNumbersAlone` | §8: unset; groups, largest, 95th percentile and median by nearest rank, the four marks and the ten largest, read against the pages on disk; no label, title, seed or path on it |
| `…aSetCeilingIsStatedWithWhatItSplit` | §8: the ceiling's bytes, groups split, pages written and pages over the ceiling, against the tree |
| `…anIgnoredCeilingIsStatedAsIgnoredAndNotQuoted` | §8: `ignored`, no table of what was split |
| `…aTreeWithNoClusterStatesNone` | §8: `0` groups, `none` for the three sizes, no row of the ten largest |
| `pipeline.ClusterPageByteCeilingTest` | §2: the key's name; unset; whole numbers from 1 to `Long.MAX_VALUE` read exactly; `0`, `-1`, `1.5`, `NaN`, infinities, text and `9223372036854775808` ignored with their text; `forTheTree` |
| `pipeline.TheClosingLineNamesAnIgnoredPageCeilingTest` | §2.1: the clause with the text quoted, before the last full stop, one line, nothing wanted; no clause unset, set, or with no tree |
| `profile.ClusterPageByteCeilingInTheProfileTest` | §2: after `generationContextWindow`; unset in a fresh profile and in `profile.yaml`; written back as written; on the index's keys |
| `pipeline.RunIdentityGoldenTest.theCeilingEntersNoRunAndTheDrawingStatesIt` | §13, §8: with the ceiling set, every run's text as pinned, no `config_consumed` naming the key, and the report in the working directory stating it |
| `controlpanel.ProfileKeyTest` | §9: the key in the profile's order with no earliest run, its cost no run, no approval closed, no labels refused |

**Restated**, each test kept and its claim turned to this record:

- `PipelineHoldsOnlyTheRulesOnRecordTest`: `ClusterPageByteCeiling` among the classes holding no rule; `GenerationTasklet`'s collaborators gain `deliverable.ClusterPageCeiling` and `profile.NumericValue`.
- `pipeline.TheJarReadsWhatTheControlPanelWritesTest` and `controlpanel.ProfileEditTest`: the key's accepted and refused samples, `9223372036854775807` accepted and `9223372036854775808` refused.
- `profile.ProfileTest`: seventeen keys; `profile.ProfileStoreTest`: the key answered among every key; `profile.ProfileFixture`: the key.
- `deliverable.DeliverableProgressTest`, `deliverable.PartitionNoSurvivorNamesStopsTheWriterTest` and `pipeline.GenerationReportsItsProgressInvocationTest`: `toDrawClusters` and `clusterDrawn`.
- `deliverable.ListedTree`: a drawing handed a ceiling and a progress.
- `deliverable.TheTreeIsHtmlTest.everySourceAndLinkStaysOnItsPageOrInTheTree`: its reading of every link is a helper both of its tests share, and a link to a file that carries a fragment has the fragment's `id` on that file.

**Not pinned, and why**: the upper bound the plan counts at (§3), which no fixture tells apart from the exact count but by a page's size, held by the ceiling; what is held while a cluster is planned (§3, §8), a bound on memory no test here measures; and the third asking of a split cluster's pictures, which the pages' bytes cannot tell from a second.
