# ADR-378 — Each deliverable language is a tree of its own under the one parent, its titles carrying the language's code, and the earlier tree is taken by the language its heading names

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator on 2026-11-12: each deliverable language is published as a tree of its own, under the one parent the operator names, in the one space, and the publish of one language never replaces another's; the root page is titled `Vespera_<code>`, the language's code as the profile names it, and stands directly under the parent; every other page's title carries the code in its place's suffix; a publish creates, updates, retitles and moves to the trash only the pages of its own language's tree; and the tree ADR-328 made is taken as the tree of the language it was drawn in, keeping its pages' ids, comments and links, and is not drawn a second time. The form of a tree's label and keys, how the language of an earlier tree is told, the titles checked, the stops, the cut, the lines, the page list's format and the run ids are decided under the operator's delegation of 2026-11-12.
- **Amends**: [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md) §1, whose titles gain the code (§1); §3, whose key is the code and the place, and whose `format` is `3` (§2, §4); §4, whose label is `vespera-<code>`, whose tree is one language's, and whose stop names the root page `Vespera_<code>` (§2, §3); §6, whose earlier tree is looked for in either earlier form and taken only by the language its root page's heading names (§5); and §7, whose suffixes end in the code (§6). Its §5, the removal to the trash, holds within each language's tree (§3).
- **Amends**: [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md) §2 and §3, as ADR-328 §1 left them, whose forms end ` · <code>)` and whose root page is `Vespera_<code>`; and §4.3, whose suffix, never cut, is ` (P · <code>)`, ` (P.C · <code>)` or ` (P.C page K · <code>)` (§1).
- **Amends**: [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md) §5, the root page's title, which carries the code, and the Italian form of ADR-310 §2, `TEXT (P.C pagina K · it)` (§1).
- **Amends**: [ADR-316](0316-the-page-list-is-pages-json-in-confluence-each-page-keyed-by-its-run-and-place-with-its-parent-title-body-files-sizes-and-digests.md) §2, whose `format` is `3` and which names its `language`; §3, whose key is §2's; and §4, whose label is `vespera-<code>` (§2, §4).
- **Amends**: [ADR-313](0313-a-clusters-confluence-page-carries-its-copies-as-files-and-a-copy-the-site-refuses-is-counted-and-listed-by-the-publish.md) §3, its `confluence-not-carried.csv`: a publish writes no such file, a file refused as too large being said on its line and counted in the closing line alone, whose clause names no file; and one an earlier version wrote is left as it stands (§10). [ADR-314](0314-the-confluence-rendering-writes-each-picture-its-cluster-page-shows-as-a-file-of-its-own-from-the-same-bytes-the-html-page-inlines.md)'s *"the same `confluence-not-carried.csv`"* with it: a picture's file refused is counted, and listed in no file (§10).
- **Amends**: [ADR-320](0320-a-publish-sends-each-page-whole-its-files-before-its-body-before-it-creates-the-next-and-says-how-far-it-has-got.md) §4: the totals and the closing line name the tree, a line says an earlier tree of another language is left, and a line may name the language's English name (§7).
- **Keeps**: ADR-328 §2, no run id on any page; §5 whole within a language's tree; §8, the HTML tree; the stops of §6 at the parent's read and listing. ADR-320 §1 to §3 and §5; [ADR-315](0315-the-root-page-carries-the-indexs-groups-and-counts-and-documents-csv-as-a-file-and-neither-the-corpus-root-nor-the-profiles-values.md) whole, its heading now also read by a publish (§5); [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) and [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) whole, one parent named for a publish; ADR-321 §1 to §4 and §6, the two languages, their codes and their fixed words; ADR-310 §4.1, §4.2 and the cut of §4.3; [ADR-309](0309-a-citation-on-a-confluence-page-is-an-anchor-link-naming-the-page-its-entry-is-on-and-where-it-cannot-link-it-is-its-number-as-plain-text.md), a link naming its page by title.

## Context

ADR-321 makes the deliverable language a value of the profile, `en` or `it`, which joins the generation run's identity: the same arrangement in the other language is another generation run. ADR-328 publishes every generation run's Confluence rendering into one tree under the parent the operator names: the root page `Vespera`, every other page titled `TEXT (P)`, `TEXT (P.C)` or `TEXT (P.C page K)`, keyed by its place alone in its content property `vespera`, and labelled `vespera`. A publish of an Italian rendering after an English one therefore finds the English tree as its own and updates it in place: each page of a place is retitled and sent the Italian body, and the English deliverable is gone from the site.

The operator writes deliverables in both languages and wants each published as a tree of its own under the same parent, `<parent>/Vespera_it/…` beside `<parent>/Vespera_en/…`.

Confluence refuses a second page of a title already held anywhere in the space, with `400` *"A page already exists with the same TITLE in this space"* (`docs/research/confluence-loading.md` §5, row 5, measured on the operator's site). The two languages' renderings of one arrangement share titles by construction: a seed partition's page is titled by its seed's file stem, which is a file's name and the same in either language (ADR-321 §5), and a cluster that nothing was written over is titled by its label, derived from its highest-scoring document's title and never translated (ADR-106). So without the language in the title, every seed partition's page of the second tree would meet the first's, and the second publish would stop at ADR-320 §3's title check.

What the code holds. `ConfluenceTitles.root()` and `ConfluenceTitles.partition` take no language; `ConfluenceTitles.cluster` takes it for `page`/`pagina` alone. `ConfluencePages` keys a page by its place and writes `pages.json` in format 2. `PageList` reads format 2 alone. `Publisher` labels every page `vespera`, finds the tree by that label and a key matching `root|P|P.C|P.C.K`, and, where none is keyed `root`, lists the parent's children for a root page keyed `<run>:root` of ADR-310's form, matching the run with `[0-9a-f]+`. The root page's body opens with the heading's words of its language, `<h1>Deliverable</h1>` or `<h1>Base di conoscenza</h1>` (ADR-315 §2, ADR-321 §4, ADR-328 §2), and the root page of an ADR-310 tree with those words followed by the run's id. A page's body is read by v2 `GET /wiki/api/v2/pages/{id}?body-format=storage`, which answers it as `body.storage.value`; Atlassian's v2 reference gives this, and it was not measured on a site.

## Decision

### 1. The titles

```
Vespera_<code>
TEXT (P · <code>)
TEXT (P.C · <code>)
TEXT (P.C page K · <code>)
```

`<code>` is the deliverable language's code, in lower case, as ADR-321 §1 has it: `en` or `it`. The root page is `Vespera`, an underscore and the code, with no space, never cut. A seed partition's page is its seed's file stem followed by its place and the code, a cluster's page its title, or its label, followed by its place and the code, and page `K` of a split cluster, from 2, `page K`, or `pagina K` in an Italian rendering. The code follows the place after ` · `, a middle dot (U+00B7) with a space on each side, as ADR-310 wrote its marker. `P` and `C` are padded as ADR-310 §2 pads them.

So `Vespera_it`, `Sicurezza (1 · it)`, `Porte tagliafuoco (1.1 · it)` and `Porte tagliafuoco (1.1 pagina 2 · it)`; and `Vespera_en`, `Fire Safety (1 · en)`, `Fire doors (1.1 page 2 · en)`.

ADR-310 §4 holds with this suffix: control characters made spaces, whitespace folded, the text cut at a code point's boundary with `…` so the whole is at most 255 UTF-16 code units, the suffix, from ` (` to `)` with the code inside it, never cut, and an empty text leaving the suffix alone, `(P.C · it)`.

### 2. A key and a label of each language

- **A page's key is the code, a colon and its place**: `it:root` for the root page, `it:P` for a seed partition's page, `it:P.C` for a cluster's first page and `it:P.C.K` for page `K` of a split cluster, each number in decimal with no leading zero, as ADR-328 §3 writes the place. It is the key in the page list, the `parent` of the page's children there, the key its property `vespera` holds, `{"key": "it:1.2", "bodySha256": "<hex>"}`.
- **Every page of a language's tree is labelled `vespera-<code>`**, `vespera-it` or `vespera-en`, and carries no other label a publish puts.
- **A page is the tree of language `L`'s** where it stands in the space, is labelled `vespera-L` and its property holds a key `L:<place>` of that form; or where it is titled as a page of the list and its property holds that page's key, the label's search not having found it (ADR-320 §3). Of two pages holding one key, the one of the lower id is the tree's, as ADR-328 §4 has it.

**Why the code in the key.** The property is what a page is matched by, and where a language's tree is not found the publish reads the properties of the parent's children to find an earlier tree (§5). With the code in the key, a property alone says whose page it is: `root` is the root page of ADR-328's tree, `<run>:root` of an ADR-310 tree, and `<code>:root` of a language's tree. With the place alone, `root` would be every language's root page and ADR-328's at once, and the publish would need each child's labels to tell them apart.

**Why a label of each language.** The label's search, by which a publish finds its tree, answers that language's pages and no other's; a reader finds one language's pages by its label; and the bare label `vespera` keeps meaning a page of ADR-328's tree, so a page still carrying it is one whose taking has not finished (§5).

**No code is a marker.** A code is two lower-case letters (ADR-321 §1); a marker is twelve hexadecimal characters and a run's id sixty-four (ADR-310 §1, ADR-316 §3). So `vespera-<code>` is ten characters where `vespera-<marker>` is twenty, and a key's part before its colon is two characters where an ADR-310 key's is sixty-four. The publish tells the forms apart by that length, not by the characters, since a code such as `de` or `fa` could be read as hexadecimal; an ADR-310 key's run is sixty-four hexadecimal characters exactly.

### 3. One tree for each language

- **Its root page stands directly under the parent** the publish is given (ADR-318, ADR-359). Where the tree's root page stands under another page or folder, the publish stops before any write, with the reason *"the tree Vespera_it it updates stands under &lt;that parent's id&gt;, not under the parent named. Name that parent, or move the page Vespera_it under the one named"*, in ADR-320 §5's stop line, the title being the tree's own.
- **A publish touches its own language's tree alone.** It creates, updates, retitles, labels and moves to the trash only pages of the tree of the rendering's language, and, where it takes one, of an earlier tree of that language (§5). It sends no write to a page of another language's tree: none of their keys is one its list holds, and none of their labels one it searches. ADR-328 §4's update in place, a page matched by its place, a tree that grows or shrinks, and §5's removal to the trash, deepest first and only where nothing stands under a page, each hold within one language's tree.
- **Two languages' trees may stand under one parent**, as the operator has them, or under two, where the operator names another parent for a publish in the other language: each publish checks only that its own tree stands under the parent it is given.

### 4. The page list

```json
{
  "format": 3,
  "generationRun": "<the generation run's id>",
  "marker": "<ADR-310's marker>",
  "language": "<the code>",
  "pages": [
    { "key": "<code>:root", "parent": null, "title": "Vespera_<code>", "…": "…" },
    { "key": "<code>:1", "parent": "<code>:root", "title": "<stem> (1 · <code>)", "…": "…" }
  ]
}
```

`format` is `3`, and `language`, after `marker`, is the code the rendering was drawn in; every page's key and parent are §2's, the rest of each entry as ADR-316 §2 has it. A publish refuses a page list of format 1 or 2 before it sends anything, as it refuses any other format, saying to run `vespera run` to draw the deliverable again (ADR-328 §3); and it refuses, in the same line, a list whose `language` is not a code ADR-321 names written in lower case as the drawing writes it, `EN` and ` en` among them, or a page whose key does not begin with that code and a colon. The profile's answer is read in any case (ADR-321 §1); the page list is the drawing's, which writes the code in lower case, so any other spelling is a list no drawing wrote.

### 5. A tree an earlier publish made is taken by the language it was drawn in

- **When**: where none of the tree's pages is keyed `L:root`, by its label or by its title. So before it looks for an earlier tree, the publish looks up the page titled `Vespera_L` in the space, by reads alone, where the label's search found no root page: one whose property holds `L:root` is the tree's root page, which lost its label, is labelled at its turn as ADR-320 §3 has it, and no earlier tree is looked for, taken or said to be left; one holding any other key, or none, stops the publish in ADR-320 §3's line before any write, as §6 has it. Only where neither search finds the root page does the publish ask whether the parent is a folder or a page and lists its children, as ADR-328 §6 has it, every answer that stops there stopping here, and reads the property of each page among them.
- **An earlier tree** is one whose root page is such a child: its property's key `root`, a tree of ADR-328's form, or `<run>:root` with `<run>` sixty-four hexadecimal characters, a tree of ADR-310's form. A child keyed `<code>:root` is a language's tree, of whatever code, and no earlier tree.
- **Its language** is read from its root page's body, by v2 `GET /wiki/api/v2/pages/{id}?body-format=storage`: the text of the body's first `h1` element, its markup taken out, its character references read and its whitespace folded to single spaces and trimmed. Where that text is a language's heading words of ADR-321 §4, `Deliverable` or `Base di conoscenza`, or begins with them followed by a space, as an ADR-310 tree's heading does with its run's id after them, the tree was drawn in that language. A read of the body answered with anything but `2xx` stops the publish before any write, in ADR-320 §5's stop line naming the site's answer.
- **Which is taken**, every earlier tree being read first:
  - **One whose heading names neither language** stops the publish before any write, with the reason *"the parent holds the tree &lt;its root page's title&gt; an earlier publish made, and its root page's heading is neither Deliverable nor Base di conoscenza, so the language it was drawn in cannot be told. Put the heading back, or move the page &lt;its title&gt; from under the parent named"*. It may be this language's tree.
  - **None drawn in `L`**: the publish makes the tree new. Each earlier tree of another language is left as it stands, its line said (§7), for a publish in its language to take.
  - **One drawn in `L`**: that tree is taken.
  - **Two or more drawn in `L`**: the publish stops before any write, with the reason *"the parent holds &lt;n&gt; trees an earlier publish made in &lt;the language's English name&gt;, &lt;their root pages' titles&gt;, and one can be taken as the tree Vespera_&lt;code&gt;. Remove all but the one to keep"*.
- **Its pages.** A tree of ADR-328's form: its root page, and the pages labelled `vespera` whose property holds a key of ADR-328 §3's form other than `root`, each the page of key `L:<that key>`. A tree of ADR-310's form: its root page, and the pages labelled `vespera-<marker>`, the marker being the first 12 characters of `<run>`, whose property holds `<run>:<place>`, each the page of key `L:<place>`, its numbers read without their padding, as ADR-328 §6 reads them.
- **Only pages that stand under its root page are its pages.** A page so labelled and keyed is the tree's where it stands under the root page at any depth: reading its `parentId`, then that page's, by v2 `GET /wiki/api/v2/pages/{id}`, reaches the root page. A read that reaches the parent named, a page answered `404` or anything that is not a page the site holds, a folder among them, ends the walk, and the page is not the tree's. A read answered with anything else that is not `2xx` stops the publish before any write, as any read does (ADR-320 §5). The label `vespera` is on every page of every ADR-328 tree in the space, and `vespera-<marker>` on every page of one generation run's ADR-310 trees, which a publish from two working directories could have made under two parents; a page of another such tree, or a copy of the tree's page a person made elsewhere with its label and its property, is not taken, and stands as it is.
- **Of two or more pages under the root page holding one key**, keyed alike or read alike once unpadded, as `<run>:01.10` and `<run>:1.10`, the one of the lower id, the earlier made, is taken, as ADR-328 §4 has it; each other is neither labelled, keyed, retitled, sent a body nor moved to the trash, and stands where it is with its earlier label and key. Where it stands under a page the rendering no longer has, that page is left as it stands for it, as ADR-328 §5 leaves a page with anything under it. The same holds of a page a stopped taking had set already (below): only one under the root page is the tree's, and of two of one key the lower id.
- **Taking it**, after the checks before the first write, §6's among them, and before the first page: on each of its pages but the root page, the label `vespera-L` is added, the property set to the page's key in §2's form, keeping the digest it holds, and the earlier label, `vespera` or `vespera-<marker>`, taken off; then, on the root page, the label `vespera-L` is added, the earlier label taken off and, the last write of the taking, the property set to `L:root`. A label's `DELETE` answered `404` is a label gone already; any other answer but `2xx` stops the publish. Then the list is sent as any publish sends it: every page taken is retitled and sent its body, its title being another; its files are skipped where they stand at their size and digest, so its copies are not sent again; and the pages of the tree whose key the list does not hold are moved to the trash (ADR-328 §5).
- **A stop while it is taken** leaves the root page keyed `root` or `<run>:root`, since its property is the taking's last write, and its heading as it was: the next publish in `L` finds it again and takes it, with the pages still keyed in the earlier form. A page carrying the earlier label whose property holds a key `L:<place>` already is one the stopped taking had set: it is the tree's page of that key, and the earlier label is taken off it with the taking's other writes.
- **An earlier tree is looked for only where the language's tree has no root page**, by its label or by its title, and only among the parent's children, its pages only under its root page. One that stands beside a language's tree, or under another parent, is left as it stands, every page of it; its titles carry no code, so none is a title of a language's tree (§6).

**Why its heading tells the language.** The language is in nothing of the tree but its generated content. The root page's heading is drawn in the deliverable language by every drawing since ADR-321, on the root page of every tree, and is sent by a publish alone. The titles tell it only where a cluster is split, by `pagina`. ADR-328's root page names no run (ADR-328 §2), so the ledger cannot be asked which generation run drew it, and the tree may have been sent from another working directory. And the language of the rendering being published says what this publish is, not what the tree was: the first publish in the other language would take the tree and send each page the other language's body, its comments then standing on a page in a language they were not written about.

### 6. The titles that can clash, and those that cannot

Two pages of one tree never share a title, as ADR-328 §7 has it: a title's suffix is its last ` (`, it names the page's place, its kind and the code, and two titles alike are of one key. **Two trees never share one**: every suffix of a language's tree ends ` · <its code>)` and its root page is `Vespera_<its code>`; ADR-328's tree's suffixes are `(P)`, `(P.C)` and `(P.C page K)`, with no ` · `, and its root page `Vespera`; an ADR-310 tree's suffixes end ` · <marker>)`, twelve characters where a code is two, and its root page is `Vespera <marker>`. So no publish retitles a page to a title a page of another tree holds, and a taking's retitles meet none.

What can clash is **a page of the space no tree owns** whose title is one of the list's, such as a page titled `Vespera_it` someone made. It is refused in one line before any write, as ADR-320 §3 and ADR-328 §7 refuse it: the publish stops, names the title, and leaves the page as it stands. **Which titles are checked** is ADR-328 §7's rule: every page of the list whose title is not that of the page standing for its key, among them every page of a tree being taken, whose titles all change.

### 7. What it says

As rows of ADR-320 §4's table:

| When | Line |
| --- | --- |
| Once the values, the page list and every file it names are checked, before any request | `vespera publish is sending the rendering of generation run <marker> to <host>, space <key>, tree Vespera_<code>: <n> pages and <f> files holding <b> bytes` |
| Before the first page, for each earlier tree of another language | `the tree <its root page's title> an earlier publish made was drawn in <the language's English name>, and is left as it stands for a publish in <that name> to take` |
| Before the first page, where an earlier tree is taken | `the tree <its root page's title> an earlier publish made is taken as the tree Vespera_<code>: its <n> pages are retitled and updated in place`, or `its 1 page is retitled and updated in place` |
| At the end | `vespera publish sent the rendering of generation run <marker> to <host>, space <key>, tree Vespera_<code>: ` and the rest of the closing line as ADR-320 §4 and ADR-328 §5 have it |

The English name is ADR-321 §1's, `English` or `Italian`. ADR-320 §4's *"A line names nothing but"* gains it, beside the titles and names it lists. The stop lines are §3's and §5's reasons in ADR-320 §5's line.

### 8. The HTML tree and the website rendering

Unchanged. Neither is a tree of the site; their titles, folders and pages are ADR-274's, ADR-280's, ADR-321's and ADR-333's.

### 9. Run ids

`ConfluenceTitles`, `ConfluencePages`, `PageList` and `Publisher` lie in `confluence` (ADR-333 §1, ADR-367), which no stage's version names (`StageModules`), and nothing reads a value it did not read before: the language is already the generation run's (ADR-321 §1). No run id moves. The next invocation that reaches the tree draws `confluence/` again, in format 3, asking the model nothing (ADR-274 §6, ADR-312, ADR-328 §9).

### 10. A file the site refuses as too large is said and counted, and listed in no file

Decided by the operator on 2026-11-12: a publish writes no `confluence-not-carried.csv`, in any language. What the site refused as too large is told on standard output alone: each such file's line, `file <j> of <F>: <name>, <size> bytes, refused by the site as too large` (ADR-320 §4), its page's closing line, `, <r> refused by the site as too large`, and the closing line's clauses counting the copies and the pictures' files refused and their bytes (ADR-313 §3, ADR-314). What becomes of the list an earlier version wrote is decided under the operator's delegation.

- **The closing line's clauses** end at the bytes: `; 1 copy was refused by the site as too large and is not on its page, <b> bytes`, or `; <n> copies were refused by the site as too large and are not on their pages, <b> bytes`, and the same for a picture's file and pictures' files, with no `, listed in confluence-not-carried.csv` after them.
- **No file is written in the working directory** for a file refused, by a publish that goes to its end or by one that stops; and a publish that went to its end cannot fail to write one, so ADR-313 §3's closing line's `; but confluence-not-carried.csv could not be written in …` is gone with it.
- **A `confluence-not-carried.csv` an earlier version wrote is left as it stands**: no publish reads, rewrites or removes it. A file in the working directory that no step of this invocation wrote is the operator's to remove; the operator deletes it once it is of no use. It names the files an earlier publish could not carry, and the next publish tries each again, as before.
- **The key of a refused file's page is in no line**: a line names a page by its title and a file by its name (ADR-320 §4), as it did.

## Alternatives refused

- **One tree for every language, as ADR-328 has it**: a publish in the other language retitles every page of the tree and sends it the other language's body, which is what the operator does not want.
- **The code in a title only where two titles would meet**: which titles meet is known on the site alone and changes from one generation run to the next; a stem is the same in both languages, so nearly every seed partition's page would carry it anyway; and a page whose title gained the code later would be retitled for no change of its own.
- **A folder or a page for each language between the parent and its root page**: the operator decided that the root page stands directly under the parent.
- **The place alone as the key, the language in the label alone**: §2, a root page's property would not tell one tree's from another's.
- **The language as a field of the property beside the key**: two values to keep alike on every page, where ADR-328 §4's lower id and ADR-320 §3's title check compare the key alone.
- **The label `vespera` kept on every language's pages**: the label's search would answer every language's pages for each publish to sort by key, and a page still labelled `vespera` would no longer say its taking is unfinished.
- **The earlier tree's language read from the rendering being published, from `pagina` in its titles or from the ledger**: §5.
- **An earlier tree whose heading names neither language taken, or passed over**: taken, it may be the other language's, every page then sent a body in a language it was not drawn in; passed over, a second tree of its language would stand beside it, which the operator decided against. A stop leaves it to the operator, and says how.
- **The code in upper case, or the language's name, in the titles**: the operator gave the code as the profile names it, in lower case.
- **An earlier tree's pages found by their label and key anywhere in the space**: the label `vespera` is every ADR-328 tree's, so a second such tree under another parent, which §5 leaves as it stands, would have its pages taken into this one, and two pages of one key would both become the page of one key, each later publish updating whichever its search found first.
- **An earlier tree's pages found by listing its root page's children downward** rather than by reading each labelled page's parents upward: either finds the same pages; the label's search is the one ADR-328 §6 already makes, and a walk upward reads only the labelled pages' parents, where a walk downward lists every page under the tree, a person's among them.
- **`confluence-not-carried.csv` kept, one for every language as ADR-313 §3 has it, or one for each language**: one for every language is written over by the other language's publish; the operator decided that the lines a publish prints are the record, and no file.
- **The list an earlier version wrote removed by the next publish**: no step wrote it in this invocation, and it may be the only record of what an earlier publish could not carry; the operator removes it.
- **The language's own root page that lost its label taken for no root page**: the publish would look for an earlier tree it does not need, and say an earlier tree of another language is left, or take one of its own language and stop on the title `Vespera_L` it holds already.

## Consequences

- **`ConfluenceTitles`** draws §1's forms, the root page's and a seed partition's from the language too; **`ConfluencePages`** keys each page by §2's form and writes `pages.json` in format 3 with its `language`; **`PageList`** reads format 3 alone and checks the language and the keys; **`Publisher`** labels each page `vespera-<code>`, finds its language's tree by that label and §2's keys, stops on its root page under another parent naming it, looks for an earlier tree of either form, reads its language from its heading, takes it or leaves it as §5 has it, and prints §7's lines; **`ConfluenceApi`** reads a page's body with `body-format=storage`. That is `confluence`'s change.
- **The tree `Vespera` an operator's earlier publishes made** is taken by the first publish in the language its heading names, keeping its pages' ids, versions, comments and watchers, its files not sent again; the first publish in the other language makes a tree of its own beside it, and says why the earlier tree is left.
- **`README.md`**'s description of the Confluence rendering and its *Publishing to Confluence* say what §1, §3, §5 and §10 have. Its table of the files written in the working directory loses `confluence-not-carried.csv` in the change that stops writing it, since `docs/check-claims.mjs` holds that table to the names the code's `FILE_NAME` constants hold.
- **`Publisher`** writes no list: its `NOT_CARRIED_FILE_NAME`, the writing and the closing line's `listed in` and `could not be written` go, and its javadoc and `package-info`'s say so. That is `confluence`'s change too.
- **`CONTEXT.md`**'s **Publish** entry says a publish updates one tree for each deliverable language.
- **ADR-310, ADR-313, ADR-314, ADR-316, ADR-320, ADR-321 and ADR-328 carry a banner** pointing here. Their bodies are unchanged.

## What this does not decide

- **A third language**: it needs a code, an English name and its heading's words, as ADR-321 says of every fixed word.
- **Links between two languages' trees**: there are none; a link names a page of its own tree by title (ADR-309).
- **Whether a site keeps a root page's `h1` as it was sent**: not measured. A site that rewrote its words would stop the publish at §5 rather than have a tree taken by the wrong language.
- **A page a person moved from one language's tree into another's**: it stays its own tree's, by its label and its key, and is updated where it stands (ADR-328 §4).
- **An earlier tree of a language that has a tree already**: it is not looked for, and is left as it stands (§5).
- **Anything on Data Center**, as ADR-308 §9.

## Tests

The rendering's half is in `confluence`, untagged, and runs in the `test` phase; the publish's half is tagged `confluence`, runs under a local `verify` (ADR-320 §6), against `StubConfluence`, which answers a page's read with `body-format=storage` with its body, and against `Rendering`, which draws a rendering of either language, its titles, keys and label §1's and §2's.

| Method | What it holds |
| --- | --- |
| `confluence.TheConfluenceRenderingTest.everyTitleCarriesTheLanguagesCodeAndTheRootPageIsVesperaAndTheCode` | an English rendering's titles, in the list's order, are `Vespera_en`, `Fire Safety (1 · en)`, `Fire doors & their inspection (1.1 · en)` and `Smoke dampers (1.2 · en)` (§1) |
| `confluence.TheConfluenceRenderingTest.aKeyIsTheCodeAndThePlaceAndThePageListOfFormatThreeNamesItsLanguage` | the page list is format 3 of language `en`, keyed `en:root`, `en:1`, `en:1.1`, `en:1.2` with their parents; a publish reads it, and refuses it rewritten as format 2, naming the language `fr` or `EN`, or with its root page keyed `root`, no code before it (§2, §4) |
| `confluence.TheConfluenceRenderingTest.aLongTitleIsCutInItsTextAndItsSuffixWithTheCodeIsKeptWhole` | the cut keeps ` (1.1 pagina 2 · it)` whole at 255 code units; whitespace and control characters fold; an empty text leaves `(1.1 · en)` (§1; ADR-310 §4) |
| `confluence.TheConfluenceRenderingIsDrawnInItalianTest.anItalianTreeIsVesperaItAndEveryPageCarriesIt` | an Italian rendering is `Vespera_it`, its pages keyed `it:` and titled with ` · it)`, a split cluster's later pages `(1.1 pagina K · it)` (§1, §2, §4) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.twoLanguagesAreTwoTreesUnderOneParentAndAPublishOfOneWritesToNoPageOfTheOther` | an English and an Italian publish make two trees under the parent, each labelled and keyed by its code; an English publish that removes a page trashes the English page alone, and writes to no Italian page; the totals and the closing line name the tree (§2, §3, §7) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.theTreeOfTheEarlierFormIsLeftByTheOtherLanguageAndTakenByTheLanguageItsHeadingNames` | ADR-328's tree headed *Base di conoscenza* is left by an English publish, which says so and makes its own tree, and taken by an Italian one: ids kept, retitled, labelled `vespera-it` alone, keyed `it:`, its files not sent again, and the line says so (§5, §7) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.anEarlierTreeWhoseHeadingNamesNeitherLanguageStopsThePublishBeforeAnyWrite` | the stop says the language cannot be told, and nothing is written (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.twoTreesWithTheirMarkerUnderTheParentStopThePublishBeforeAnyWrite` | two ADR-310 trees headed in English stop an English publish, naming both, with nothing written; an Italian publish under them makes its own tree and leaves both (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.theTreeOfItsLanguageUnderAnotherParentStopsThePublishNamingItsRootPage` | an English tree under another parent stops the English publish naming `Vespera_en`; an Italian publish to that parent makes its own tree there (§3) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aTreeOfTheMarkersFormHeadedInEnglishIsTakenAsVesperaEnItsPlacesUnpadded` | an ADR-310 tree whose root page opens `<h1>Deliverable <run></h1>`, keyed `<run>:01` and `<run>:01.01`, is taken by an English publish: ids kept, retitled, labelled `vespera-en` alone, keyed `en:1` and `en:1.1`, its files not sent again (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aTakingStoppedPartWayIsFinishedByTheNextPublishInItsLanguageTheRootPageKeyedLast` | a taking stopped by a label's removal the site refuses, on a seed partition's page and then on the root page, leaves the root page keyed `root` after each stop, and the third publish ends with every page labelled `vespera-it` alone, keyed `it:`, ids kept (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aRootPagesBodyTheSiteWillNotGiveStopsThePublishBeforeAnyWrite` | the read of an earlier root page's body answered `404`, then `500`, stops the publish naming the site's answer, with nothing written (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aHeadingInMarkupWithANoBreakSpaceIsReadAsItsLanguage` | a root page whose `h1` is `<strong>Base di&nbsp;conoscenza</strong>` is taken by an Italian publish (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.ofTwoEarlierPagesOfOneKeyUnderTheRootTheLowerIdIsTakenAndAPageOutsideTheTreeIsNot` | a page of the earlier label and key `1.1` under another parent, and a second one under the root page made after the tree's own, are neither written to nor changed; the tree's own, the lower id under the root, is taken as `it:1.1` (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.itsOwnRootPageThatLostItsLabelIsFoundByItsTitleAndNoEarlierTreeIsLookedFor` | `Vespera_it` keyed `it:root` with no label is found by its title: an earlier English tree under the parent is not said to be left, nothing is created, and the root page is labelled `vespera-it` again (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aFileTheSiteRefusesAsTooLargeIsSaidAndCountedAndListedInNoFile` | a copy refused as too large is said on its line and counted with its bytes in the closing line, which names no file; no file is written in the working directory; and a `confluence-not-carried.csv` an earlier version wrote stands after it byte for byte (§10) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.noLineOrFileThePublishWritesHoldsTheToken` | as ADR-308 has it, with no list of refused files among the files it reads (§10) |

The other methods of `PublishUpdatesOneTreeInPlaceIT` hold what ADR-328 has them hold, with §1's titles and §2's keys and label.
