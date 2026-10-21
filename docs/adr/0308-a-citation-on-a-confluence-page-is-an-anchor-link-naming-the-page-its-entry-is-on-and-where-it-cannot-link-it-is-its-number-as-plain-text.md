# ADR-308 — A citation on a Confluence page is an anchor link naming the page its entry is on, and where it cannot link it is its number as plain text

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Decided**: by the operator, on [#614](https://github.com/algernon28/vespera/issues/614): the form (§1), the fallback (§3) and how this record stands beside ADR-135 §6 (§4).
- **Rests on**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md), by which stage 6b draws the Confluence rendering in storage format beside the HTML tree, and [ADR-305](0305-the-confluence-rendering-is-drawn-in-storage-format-for-cloud-and-data-center-alike-and-loading-it-into-cloud-is-the-operators-act-outside-what-binds-vespera.md) §1, one rendering for both editions.
- **Keeps**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md), whole: a citation is an ordinal minted for one call, linking to its document's entry in the membership list.
- **Keeps**: [ADR-135](0135-a-membership-entry-links-relatively-or-does-not-link-at-all-and-the-citation-anchor-stands.md) §6 for the HTML tree, unchanged: `id="document-n"` on the entry and `#document-n` on the citation. No anchor is derived from a heading's text, in either rendering (§2).
- **Keeps**: [ADR-303](0303-a-clusters-page-past-the-profiles-byte-ceiling-is-split-at-an-entrys-end-into-pages-and-every-drawing-writes-the-pages-sizes.md) §6: on a split cluster's first page, a citation names the page its entry is on. The Confluence rendering follows it with the page's title where the HTML tree has the page's file name (§1).
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and §6: no value a stage reads is added, so no run id moves (§5).
- **Settles** [#614](https://github.com/algernon28/vespera/issues/614).

## Context

A citation `[n]` in a synthesis doc is rendered on a cluster's page as a link to entry n of the membership list (ADR-109). In the HTML tree it is `<a href="#document-n">[n]</a>`, the entry carrying `id="document-n"` (`ProseHtml`, `ClusterPage.ANCHOR_PREFIX`), and on a split cluster's first page `./index-<k>.html#document-n` where entry n is on page k (ADR-303 §6). The Confluence rendering needs the same hop in storage format.

### What was measured

On one Confluence Cloud site, with synthetic pages only (`docs/research/confluence-loading.md` §5, row 1):

| Sent | Stored | Rendered |
| --- | --- | --- |
| `<ac:link ac:anchor="document-1">` with `<ac:plain-text-link-body>` | as sent | `<a href="#document-1">[1]</a>` |
| the same with `<ri:page ri:content-title="…">` naming the page it is on | as sent | `<a href="#document-1">[1]</a>` |
| `<ac:structured-macro ac:name="anchor">` with parameter `document-1` | as sent, with a macro id added | `<span id="document-1">`, beside a second id prefixed with the page's title |
| a raw `<a href="#document-1">` | as sent | as sent |

A title link with no `ri:space-key` resolves to its page once that page exists (§5, row 2). Not measured: whether a click in a browser lands on the anchor, and an anchor link naming **another** page. Data Center was not reached.

### What was known before

CONFCLOUD-66773 is closed "Won't Do" [Cloud], and anchor links made through the API on pages of the newer editor are reported to reach the page but not the anchor [Cloud, unverified]. #604 names two fallbacks: anchors derived from headings, or citations as plain numbered text.

## Decision

### 1. The form

Each entry of a cluster's page in the Confluence rendering opens with an anchor macro named `document-n`:

```xml
<ac:structured-macro ac:name="anchor"><ac:parameter ac:name="">document-n</ac:parameter></ac:structured-macro>
```

Each citation `[n]` is an anchor link naming the page entry n is on, whether that is the page the citation is on or another page of the same split cluster:

```xml
<ac:link ac:anchor="document-n"><ri:page ri:content-title="TITLE" /><ac:plain-text-link-body><![CDATA[[n]]]></ac:plain-text-link-body></ac:link>
```

`TITLE` is the title [#613](https://github.com/algernon28/vespera/issues/613) gives that page, escaped as an attribute value. **`ri:page` is always given**, so one form serves a page that is whole and every page of a split one, and the link still reaches the right page where the anchor is not honoured. **`ri:space-key` is never given**: the link resolves in the space of the page holding it, and the rendering names no space. The prefix is `ClusterPage.ANCHOR_PREFIX`, `document-`, shared with the HTML tree.

A citation of a number no entry has is drawn as ADR-303 §6 has it in the HTML tree: its anchor link names the page the citation is on.

### 2. No anchor is derived from a heading

An anchor Confluence derives from a heading's text is refused, on ADR-109's ground as ADR-135 §6 states it: two viewers may derive it differently, and a citation would land elsewhere without a sign. The anchor Confluence adds beside `document-n`, prefixed with the page's title, is not named by anything drawn.

### 3. The fallback is the number as plain text

The fallback, named now so #620 is drawn knowing it, is `[n]` as plain text, with no link, in place of §1's `ac:link`; the entry's anchor macro stays. It is taken only if the form of §1 is found to reach neither the entry nor its page. Heading-derived anchors are not the fallback (§2).

The fallback is **not a value the operator sets**, and taking it is a later record's decision. The form of §1 fails safe: where an anchor is not honoured, the link still lands on the page that carries the entry, where `[n]` is read against the numbered list, so a page where the anchor is not honoured is not a reason to take it.

### 4. Beside ADR-135 §6

ADR-135 §6 states the HTML tree's form and stands. This record states the Confluence rendering's form. The two share the prefix and the entry each citation names; [#620](https://github.com/algernon28/vespera/issues/620)'s parity test holds that both renderings carry the same entries and the same citations (#604, Destination 3).

### 5. The run ids that move

**None.** This record adds no profile key, Spring property or command-line option, and changes no code a stage runs.

## Consequences

- **A citation reaches at least its entry's page** on every site that resolves a title link, and its entry where the anchor is honoured.
- **A split cluster's citations are drawn by one rule**: the page they name is the only thing that differs.
- **The renderer needs each page's title** before it draws a citation, so the titles #613 gives are fixed before any body is drawn.
- **Nothing in the rendering names the operator's space**, so the same files publish into any space.

## What this does not decide

- The titles of the pages (#613), and so the text `ri:content-title` carries.
- How the entry's anchor sits inside the entry's markup, beyond opening it: #620's.
- Whether a click lands on the anchor in a browser on Cloud, and anything on Data Center; neither is built on.

## Tests

None. This record adds no code. §1 to §3 are to be held by the renderer's tests, which #620 builds: each entry opens with its anchor macro, each citation is an `ac:link` naming its entry's page and no space, and no anchor is derived from a heading.
