# ADR-310 — A Confluence page's title is its text followed by its place and its generation run's marker, cut to 255 characters with the marker kept whole

> **Partly amended — see [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md).** Where the deliverable's language is Italian, §2's second form reads `TEXT (P.C pagina K · MARKER)`, and a cluster's title is written by the model in Italian. The rest of this record stands.

> **Partly superseded — see [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md).** No title carries the run's marker: the root page is `Vespera`, a seed partition's page `TEXT (P)`, a cluster's `TEXT (P.C)` and `TEXT (P.C page K)`, the place padded as §2 pads it; every generation run is published into one tree, updated in place, so §1, §5's uniqueness across runs and the first two Consequences no longer hold. §4 stands with that suffix.

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Decided**: by the operator, on [#613](https://github.com/algernon28/vespera/issues/613): the run's marker (§1), a cluster's page (§2), a seed partition's page and the root page (§3), the length cut and the characters (§4), and where any value lives (§6).
- **Rests on**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md), by which stage 6b draws the Confluence rendering beside the HTML tree, and [ADR-305](0305-the-confluence-rendering-is-drawn-in-storage-format-for-cloud-and-data-center-alike-and-loading-it-into-cloud-is-the-operators-act-outside-what-binds-vespera.md) §1, one rendering for both editions.
- **Keeps**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), whole: the arrangement's order is carried in its path, and a cluster is identified by its ordinal within its seed partition (`CONTEXT.md`, Cluster label).
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), whole: a title drawn here holds no word of ours, so says neither *cluster* nor *group*.
- **Keeps**: [ADR-303](0303-a-clusters-page-past-the-profiles-byte-ceiling-is-split-at-an-entrys-end-into-pages-and-every-drawing-writes-the-pages-sizes.md): a split cluster's pages are told apart by their number, its first page carrying no number (§2).
- **Keeps**: [ADR-309](0309-a-citation-on-a-confluence-page-is-an-anchor-link-naming-the-page-its-entry-is-on-and-where-it-cannot-link-it-is-its-number-as-plain-text.md) §1: the title a citation's `ri:content-title` carries is the one this record gives its entry's page.
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and §6: no value a stage reads is added, so no run id moves (§6).
- **Settles** [#613](https://github.com/algernon28/vespera/issues/613).

## Context

Every page of the Confluence rendering needs a title, and links between its pages name each other by title (`ri:page ri:content-title`, ADR-309 §1), so the titles are fixed before any body is drawn. The operator cannot create spaces: each generation run is published under one parent in an existing space the operator already holds, so a title must be unique across every generation run that space holds, not only within one run.

The texts a title could be drawn from are not unique by construction. A cluster's title is written by the model, or is its label where no synthesis doc was generated, and a label is never unique (`CONTEXT.md`, Cluster label). Two seed partitions can share a seed's file stem, and two generation runs draw the same texts over the same arrangement.

### What was measured

On one Confluence Cloud site, with synthetic pages only (`docs/research/confluence-loading.md` §5, row 5): a title of 255 characters is taken and one of 256 is refused (400); every character tried is taken, `: / \ | ? # % & < > " ' * ~ + [ ] { }`, `é` and `日` among them; a second page with a title already in the space is refused (400). A title link to a page resolves once the page exists, with or without `ri:space-key` (row 2). Data Center was not reached.

## Decision

### 1. The run's marker

A generation run's **marker** is the first 12 hexadecimal characters of its id, the 6b run's id (`DeliverableProvenance.runId`, a SHA-256 in hexadecimal, `RunId`). It is derived, never set. A generation run drawn again keeps its marker, so its pages keep their titles; a new generation run has a new marker, so its pages lie beside the earlier run's in the same space.

### 2. A cluster's page

```
TEXT (P.C · MARKER)
TEXT (P.C page K · MARKER)
```

`TEXT` is the cluster's title, or its label where no synthesis doc was generated. `P` is its seed partition's place and `C` the cluster's place within it, each zero-padded as the folder names of the HTML tree pad them (`IndexPage.partitionDirectoryName`, `IndexPage.clusterFolderName`). The first form is a cluster's only page, or the first page of a split cluster; the second is page `K` of a split cluster, `K` from 2, matching `index-<K>.html` (ADR-303).

So `Hydraulic seals (02.07 · 3f9a1c2e4b7d)` and `Hydraulic seals (02.07 page 2 · 3f9a1c2e4b7d)`. A label or title repeated across seed partitions is told apart by `P`, one repeated within a seed partition by `C`, and one repeated across runs by `MARKER`. The text is never what keeps two titles apart.

### 3. A seed partition's page and the root page

```
TEXT (P · MARKER)
Vespera MARKER
```

A seed partition's page has as `TEXT` its seed's file stem (`FilenameStem`), unslugged, and `P` padded as in §2. The root page, the index's counterpart and the parent every other page of the run lies under, is `Vespera` followed by the marker.

### 4. The length cut and the characters

1. **No character is replaced or escaped in the title** itself: every character measured was taken. Escaping where a title is written into a body, as an attribute value of `ri:content-title`, is the renderer's and is not a change to the title.
2. **Every control character becomes a space** (`Character.isISOControl`), runs of whitespace become one space, and `TEXT` is trimmed. A title is then never one whose text a site could fold differently on save from the text a link names. A `TEXT` that is empty after this leaves the title as the suffix alone, `(P.C · MARKER)`.
3. **A title is at most 255 characters**, counted in UTF-16 code units (Java's `String.length`), which is never fewer than the code points a site might count. **The suffix, from ` (` to `)`, is never cut.** Where the whole would be longer, `TEXT` is cut at a code point's boundary, trailing whitespace is dropped, and `…` is appended, so the cut text, `…` and the suffix are 255 together.

### 5. Uniqueness

Within one generation run, `P`, `C` and `K` give each page a distinct suffix; across runs, `MARKER` does. Two generation runs whose markers are the same would meet in one space, at a chance of about one in 2⁴⁸ per pair; Confluence refuses the second title with a 400 rather than overwrite, so the meeting is seen, not silent.

### 6. Where any value lives, and the run ids that move

**Nowhere, and none.** The marker is derived from the run's id, the cut from Confluence's own limit, and the form is fixed here. No profile key, Spring property or command-line option is added. The site, space and parent the rendering is published under are [#632](https://github.com/algernon28/vespera/issues/632)'s, and no title names them.

## Consequences

- **Every run can be published into one space** beside every earlier run, under the root page `Vespera MARKER`.
- **A run published again finds its own titles**, which is what lets a page be updated in place rather than refused.
- **A reader sees the cluster's own title first** in the page tree; the place and marker follow it.
- **The renderer computes each title before it draws any body**, since a citation (ADR-309 §1) and the page tree both name pages by title.
- **The HTML tree is unchanged**: its folder names and headings keep ADR-112, ADR-274 and ADR-280.

## What this does not decide

- Whether two titles that differ only by Unicode normalisation or by case are kept apart by a site; the suffix keeps every title this record draws apart by its ASCII alone.
- Where the site, space and parent are set (#632), and where the rendering lies in the tree (#612).
- Anything on Data Center, where nothing was measured.

## Tests

None. This record adds no code. §1 to §4 are to be held by the renderer's tests, which [#620](https://github.com/algernon28/vespera/issues/620) builds: each page's title has its form, a title of every kind is at most 255 code units with its suffix whole, control characters and whitespace runs are folded, and the titles one run draws are distinct.
