# ADR-328 — The Confluence rendering is one tree, updated in place by each publish, its pages keyed and titled by their place with no run id, and a page it no longer has moved to the trash

- **Date**: 2026-10-29
- **Status**: accepted on 2026-10-29.
- **Decided**: by the operator on 2026-10-29: no generation run's id, whole or as its marker, in any title or any text of the published pages; one tree, which each publish updates in place, the root page and every other page by its place; a page of the tree the new rendering no longer has removed; the place numbers kept in the titles; a title held by a page the tree does not own refused in one line. The key of each place, a group whose place changes, a rendering that grows or shrinks, how a page is removed and what the publish says of it, a refused removal, the tree an earlier publish made with its marker, the clashes left and the HTML tree are decided under the operator's delegation of 2026-10-29.
- **Supersedes**: [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md) §1, the run's marker in a title, §3's root page *"`Vespera` followed by the marker"*, §5's *"across runs, `MARKER` does"* and its Consequences *"Every run can be published into one space beside every earlier run"* and *"A run published again finds its own titles"*: no title carries a marker, and every run is published into the one tree (§1, §4).
- **Supersedes**: [ADR-316](0316-the-page-list-is-pages-json-in-confluence-each-page-keyed-by-its-run-and-place-with-its-parent-title-body-files-sizes-and-digests.md) §3, the key `<generation run>:<place>`, and its refused alternative *"A key with no run"*; §4's label `vespera-<marker>` and its *"A page of the same generation run whose key the list no longer holds is left as it stands and counted"*: a key is the place alone, every page is labelled `vespera`, and a page of the tree the list no longer holds is moved to the trash (§3, §4, §5).
- **Supersedes**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §8's *"Nothing is deleted. A page of an earlier publish whose key the page list no longer holds is left as it stands and counted in the closing line"*, and the same clause [ADR-320](0320-a-publish-sends-each-page-whole-its-files-before-its-body-before-it-creates-the-next-and-says-how-far-it-has-got.md) keeps: such a page is moved to the trash after every page of the list is sent (§5).
- **Amends**: ADR-310 §2 and §3, whose forms lose ` · MARKER`, and §4.3, whose suffix is ` (P.C)`, ` (P.C page K)` or ` (P)`; and [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md)'s Italian form of ADR-310 §2, which reads `TEXT (P.C pagina K)` (§1).
- **Amends**: [ADR-315](0315-the-root-page-carries-the-indexs-groups-and-counts-and-documents-csv-as-a-file-and-neither-the-corpus-root-nor-the-profiles-values.md) §2's first item, *"the generation run's id, whole, under the heading the HTML index gives it"*: the root page carries the heading's words alone and no run id (§2).
- **Amends**: ADR-316 §2, whose `format` is `2` and whose `key` and `parent` are §3's (§3).
- **Amends**: ADR-320 §3, a page found by its title taken only where its property holds that page's key in this tree's form; §4, which gains the lines of §5 and §6 and whose closing line's count of pages left becomes §5's; and §5, a stop during the removals (§4 to §6).
- **Keeps**: ADR-320 §1 and §2 whole, a page at a time, its files before its body, nothing created early; ADR-308 §1 to §7, §9 and §10, and §8 but for the clause above; ADR-316 §1 and §4 but for the label and the clause above; [ADR-313](0313-a-clusters-confluence-page-carries-its-copies-as-files-and-a-copy-the-site-refuses-is-counted-and-listed-by-the-publish.md) §3, a file refused as too large, whose row in `confluence-not-carried.csv` names its page by §3's key; [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) whole; ADR-310 §4.1, §4.2 and the cut of §4.3; [ADR-309](0309-a-citation-on-a-confluence-page-is-an-anchor-link-naming-the-page-its-entry-is-on-and-where-it-cannot-link-it-is-its-number-as-plain-text.md), a link names its page by title; [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md) and [ADR-314](0314-the-confluence-rendering-writes-each-picture-its-cluster-page-shows-as-a-file-of-its-own-from-the-same-bytes-the-html-page-inlines.md) whole.
- **Keeps**: the HTML tree whole, its index's heading naming the run, its folders and pages ([ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md)) (§8); ADR-274 §5 and §6, a change to `deliverable` alone moves no run id (§9).
- **Settles**: [#667](https://github.com/algernon28/vespera/issues/667).

## Context

ADR-310 ends every title with the generation run's marker, the first 12 hexadecimal characters of its id: `Vespera <marker>`, `<stem> (01 · <marker>)`, `<text> (10.1 · <marker>)`. The root page's body opens with the whole id under the heading `Deliverable` (ADR-315 §2). ADR-316 keys each page `<generation run>:<place>` in its content property `vespera` and labels it `vespera-<marker>`. The marker was there so that a later generation run's pages stand beside an earlier one's in the space, whose titles are unique (`docs/research/confluence-loading.md` §5, row 5): each generation run made a tree of its own under the parent, and a publish deleted nothing.

The operator wants neither the marker nor the id on the published pages, and one tree under the parent, which each publish brings to the rendering it sends.

What the code holds today. `ConfluenceTitles` draws the titles and `ConfluencePages` the keys, the root page's heading and `pages.json`, `format` 1. `Publisher` finds the run's pages by the label `vespera-<marker>` (v1 CQL `label = …`) and keys them by their property, looks for every page of the list not so found by its title (v2 `GET /pages?title=`), stops before any write where a page so titled holds another key or none (ADR-320 §3), and counts the pages of the run the list no longer holds. A place is padded to the width of its count (`IndexPage.pad`, `IndexPage.widthOf`): `1` to `9` with up to nine seed partitions, `01` to `10` with ten. A cluster's place is its order within its seed partition, which 6a sets by size and mean score (ADR-112), so another generation run can give a group another place.

Of the site's API, beside what the publish already sends: v2 `DELETE /wiki/api/v2/pages/{id}` moves a page to its space's trash, from which a space's admin restores it, and with `purge=true` on a page in the trash removes it for good, which needs the space's admin; v2 `GET /wiki/api/v2/folders/{id}/direct-children` and `GET /wiki/api/v2/pages/{id}/direct-children` list a folder's or a page's children, cursor-paged with `_links.next`; v1 `DELETE /wiki/rest/api/content/{id}/label?name=<label>` takes a label off a page. Atlassian's v2 reference and F3 (`docs/research/confluence-loading.md` §2.3) give these; none was measured on a site. A scoped token that deletes a page needs `delete:page` (§2.2 of the research), and a classic token the account's permission to delete pages in the space.

## Decision

### 1. The titles

```
Vespera
TEXT (P)
TEXT (P.C)
TEXT (P.C page K)
```

The root page is `Vespera`; a seed partition's page its seed's file stem followed by its place; a cluster's page its title, or its label, followed by its place, and page `K` of a split cluster, from 2, `page K`, `pagina K` in an Italian deliverable (ADR-321). `P` and `C` are padded as ADR-310 §2 pads them. ADR-310 §4 holds with this suffix: control characters made spaces, whitespace folded, the text cut at a code point's boundary with `…` so the whole is at most 255 UTF-16 code units, the suffix never cut, and an empty text leaving the suffix alone, `(P.C)`. No title carries ` · ` or a marker.

### 2. No run id on any page

The root page's body opens with the heading's words alone, `<h1>Deliverable</h1>`, `<h1>Base di conoscenza</h1>` in Italian, then its sentence, as before. No title, body, label, file name or property value a publish sends carries the generation run's id or its marker. `pages.json` still names the generation run and its marker, `generationRun` and `marker`: the publish checks the first against the ledger, and its lines on standard output name the second (ADR-320 §4). Neither file nor line is a page.

### 3. The key is the place, unpadded

A page's key is its place alone: `root` for the root page, `P` for a seed partition's page, `P.C` for a cluster's first page and `P.C.K` for page `K` of a split cluster, each number in decimal with no leading zero. A page's `parent` in the list is its parent's key, `null` for the root page. `pages.json`'s `format` is `2`.

So a seed partition whose count passes from nine to ten keeps its keys and changes its titles, `(1.2)` becoming `(01.02)`, and its pages are updated, not removed and made again. A publish refuses a page list of format 1 before it sends anything, as it refuses any other format, saying to run `vespera run` to draw the deliverable again; that drawing costs nothing, as §9 says.

### 4. The tree and a page's identity

- **Every page of the tree is labelled `vespera`**, and its property `vespera` holds its key and its body's digest, `{"key": "<key>", "bodySha256": "<hex>"}`, as ADR-316 §4 has it.
- **A page is the tree's** where it stands in the space, is labelled `vespera`, and its property holds a key of §3's form; or where it is titled as a page of the list and its property holds that page's key, the label's search not having found it (ADR-320 §3). Two pages holding one key: the one of the lower id, the earlier made, is the tree's, and the other is neither updated nor removed. A page labelled `vespera` whose property holds no such key is not the tree's.
- **The root page stands directly under the parent** the operator names (ADR-318). Where the tree's root page stands under another page or folder, the publish stops before any write, with the reason *"the tree it updates stands under &lt;that parent's id&gt;, not under the parent named. Name that parent, or move the page Vespera under the one named"*, in ADR-320 §5's stop line. A publish moves no page: any other page of the tree that was moved by hand is updated where it stands.
- **A page is its place.** Each page of the list is matched to the tree's page of its key and updated in place, its title and its body sent where either differs, its files by their name, size and digest, all as ADR-320 §1 has it. A group whose place changes from one generation run to the next is drawn on the page of its new place, and the page of its old place carries the group that has that place now, or, where none has it, is removed (§5). A page's id, its versions, its comments and who watches it go with its place, not with a group.
- **A rendering that grows** has pages of new keys, created at their turn as any page that does not stand (ADR-320 §1). **One that shrinks** leaves pages of keys the list no longer holds, which §5 removes.

### 5. A page the rendering no longer has is moved to the trash

- **Which**: every page of the tree whose key the list does not hold. The root page is never one: every list holds `root`.
- **When**: after the last page of the list has had its closing line, so the tree the list draws stands whole before anything is taken from it. Deepest first, `P.C.K` before `P.C` before `P`, and within one depth in the order of their numbers.
- **First, what stands under it**: v2 `GET /wiki/api/v2/pages/{id}/direct-children`, asked at the page's turn, so after every deeper page's removal. The page is moved to the trash only where that listing is empty at that moment. Where anything stands under it, it is left as it stands with all it holds, whatever that is: a page, a folder or anything else a person put there, a page of the tree a person moved under it, or a page of the tree whose own removal left it standing a moment before. Whether a child is the tree's does not enter into it. So a person's page under a group's page that the rendering no longer has keeps that page standing, and that page keeps its seed partition's page standing where the rendering no longer has that either. The listing answered `404` is a page gone already, and counts with them. Answered with anything else that is not `2xx`, it stops the publish there, as a read the site refuses does (ADR-320 §5): a page whose children could not be read is not removed.
- **Then the removal**: v2 `DELETE /wiki/api/v2/pages/{id}`, with no `purge`. The page goes to the space's trash, from which a space's admin can restore it; a publish never purges. An answer of `2xx` is the page moved to the trash; `404` is a page gone already, and counts with them; a `429` or a pacing `503` is waited out as any request is (ADR-308, `ConfluenceApi`).
- **A removal the site refuses**, with any other answer, `403` for an account that may not delete pages among them, is said on its line and counted on the closing line, and the page stands as it was. It stops nothing: the publish goes on to the next removal and ends as published, as it does after a file refused as too large (ADR-313 §3), and the next publish tries again. A request the site does not answer stops the publish, as any does (ADR-320 §5).

**What it says**, as rows of ADR-320 §4's table:

| When | Line |
| --- | --- |
| After the last page's closing line, where any page is to be removed | `<m> pages of the tree the rendering no longer has are moved to the trash`, or `1 page of the tree the rendering no longer has is moved to the trash` |
| After each removal | `removal <j> of <m>: <title>, moved to the trash`, `…, already gone`, `…, left as it stands: the site answered <HTTP status and message>` or `…, left as it stands: something stands under it, which its removal could take with it` |
| At the end, in the closing line, after the files' count and before the clauses of ADR-313 §3 | `; <r> pages the rendering no longer has were removed`, or `; 1 page the rendering no longer has was removed`, where any was moved to the trash or gone already; then `; <k> pages the rendering no longer has were left as they stand`, or `; 1 page the rendering no longer has was left as it stands`, where any was left |

`<title>` is the page's title as the site holds it. A page left because something stands under it is counted in the closing line's clause of the pages left, with a removal the site refused, and the next publish asks its children again. The closing line's *"pages of this run the page list no longer holds were left as they stand"* is gone. A stop during the removals says `It stopped at removal <j> of <m>, <title>.` where ADR-320 §5's line says the page it stopped at; the pages moved to the trash before it stay there, and the next publish removes the rest.

### 6. A tree an earlier publish made with its marker is taken as this tree

- **Where none of this tree's pages is keyed `root`**, the publish asks whether the parent is a folder or a page, by v2 `GET /wiki/api/v2/folders/{id}`: `2xx` is a folder and `404` a page. Any other answer, a `403` or a `500` among them, stops the publish before any write, in ADR-320 §5's stop line naming the site's answer: the parent is taken neither for a page nor for a folder. It then lists the parent's children, by v2 `GET /wiki/api/v2/folders/{id}/direct-children` for a folder or `GET /wiki/api/v2/pages/{id}/direct-children` for a page, and reads the property of each page among them. A page whose property's key is `<run>:root`, `<run>` hexadecimal, is the root page of a tree an earlier publish made under ADR-310 and ADR-316.
- **The listing answered with anything but `2xx`**, `404` among them, stops the publish before any write, in the same line. The parent was found a moment before, so no answer of its listing is an empty parent, and a publish that took it for one would make a second tree beside one it could not see.
- **None**: the publish makes the tree new. **One**: that tree is taken. Its root page is that page, whatever labels it carries. Its other pages are those labelled `vespera-<marker>`, the marker being the first 12 characters of `<run>`, whose property holds `<run>:<place>`; each is the tree's page of the key `<place>` names, each number read without its padding, so `<run>:01.10` is `1.10`. **Two or more**: the publish stops before any write, with the reason *"the parent holds &lt;n&gt; trees an earlier publish made, &lt;their titles&gt;, and one can be taken as this tree. Remove all but the one to keep"*.
- **Taking it**, after the checks before the first write, §7's among them, and before the first page: on each of its pages but the root page, the label `vespera` is added, the property set to the page's key in §3's form, keeping the digest it holds, and the label `vespera-<marker>` taken off; then, on the root page, the label `vespera` is added, the label `vespera-<marker>` taken off and, the last write of the taking, the property set to `root`. A label's `DELETE` answered `404` is a label gone already; any other answer but `2xx` stops the publish. Then the list is sent as any publish sends it: every page taken is retitled and sent its body, its title being another; its files are skipped where they stand at their size and digest, so its copies are not sent again; and the pages whose key the list does not hold are moved to the trash (§5).
- **It says so**, in one line before the first page: `the tree <its root page's title> an earlier publish made is taken as this tree: its <n> pages are retitled and updated in place`, `1 page is` for one.
- **A stop while it is taken** leaves the root page keyed `<run>:root`, since its property is the taking's last write: the next publish finds it again and takes it and the pages still keyed by `<run>`. A page labelled `vespera-<marker>` whose property holds a key of §3's form already is one the stopped taking had set and not yet finished: it is the tree's page of that key, found by its label `vespera`, and the label `vespera-<marker>` is taken off it, with the taking's other writes. So no page of the tree still carries the earlier label once a taking has gone to its end.
- **A tree with its marker that is not a child of the parent** is not looked for, and is left as it stands. Its titles carry its marker, so none is a title of this tree (§7).

**Why taken, and not left beside the new one.** "One tree, updated in place" is what the operator asked for, and the tree an earlier publish made under the parent is the tree a reader has. Taking it keeps every page's id, so a link or a bookmark to it, its comments and its watchers stay; it sends no copy again, where the rendering's files hold most of its bytes; and the folder ends holding one tree. Left alone, the folder would hold two trees after the next publish, the earlier one no longer sent anything, and the operator would trash its pages by hand.

### 7. The titles that can clash, and those that cannot

Two pages of one tree never share a title. A title's suffix is its last ` (`, since no suffix holds another `(`, and the suffix names its place and its kind: `(P)`, `(P.C)`, `(P.C page K)`; the root page's title, `Vespera`, ends in no `)`. Two titles alike end in one suffix, so are of one place, so of one key; a later rendering's title of a key and an earlier rendering's of another key differ the same way, whatever padding either has, so no page is retitled to a title another page of the tree still holds, and the order in which the pages are sent does not matter. In particular:

- **Two groups of one text** at different places differ by `P.C`; a group's text that is its seed partition's stem differs by `(P.C)` against `(P)`; the pages of a split group by `page K`; a cut text by its suffix, which is never cut.
- **A tree an earlier publish made with its marker** holds titles ending ` · <marker>)` or `Vespera <marker>`, none of which is this tree's.

What can clash is **a page of the space the tree does not own** whose title is one of the list's: an operator's page titled `Vespera`, or `<stem> (1)`, anywhere in the space, or a second tree, which a publish from another working directory into the same space would make. It is refused in one line before any write, as ADR-320 §3 refuses it: the publish stops, names the title, and leaves the page as it stands. So the space holds one such tree.

**Which titles are checked.** Before any write, a tree's taking included, the publish looks up by its title every page of the list whose title is not the title of the page that stands for its key: a page to be created; a page of the tree the list retitles, a group now drawn at a place, or a count that passes nine changing its padding; and every page of a tree being taken, whose titles all lose their marker, `Vespera <marker>` becoming `Vespera`. A page so titled that is not the page standing for that key stops the publish in ADR-320 §3's line, naming the title, and its line is the one a page to be created gets; for a page to be created, a page so titled whose property holds its key is still taken, as ADR-320 §3 takes it. A page whose title the list keeps is not looked up: it holds that title already. A page whose title the site holds equal to one of the list's though not character for character, as a site that compares titles regardless of case might, is not found by the check, and the site refuses the page when it is created, which stops the publish there (ADR-320 §3).

### 8. The HTML tree

Unchanged. The operator asked about the published pages; nothing in §1 to §7 needs the HTML tree to change. Its index's heading still names the generation run, and its folders, pages and copies are ADR-112's, ADR-274's and ADR-280's.

### 9. Run ids

The titles, keys, root page's heading and page list are drawn in `deliverable`, which reads no new value, so no run id moves (ADR-274 §5); the next invocation that reaches the tree draws `confluence/` again in format 2, asking the model nothing (ADR-274 §6, ADR-312). The publish is `confluence`, which no stage's version names (ADR-320 §7).

## Alternatives refused

- **The marker kept in the labels, or in the keys**: a label shows on the published page, and a key with the run in it makes every later generation run's pages new ones, which is the second tree the operator does not want.
- **A key from the group's text or its label**: a text is the model's and a label is not unique (ADR-310's Context); a page that followed its group from place to place would need both runs' groups matched, which no record defines.
- **Keys padded as the titles are**: a count that passes nine or ninety-nine would make every key of that level new, every page of it removed and made again, with its versions, comments and links.
- **Purging the page, or `DELETE` with `purge=true`**: it needs the space's admin, and a page removed that way cannot be brought back.
- **Removing before the pages are sent, or as each page's turn comes**: a stop would leave a tree with pages taken from it and the new ones not there yet.
- **Removing a page with what a person put under it**: what the tree does not own, the publish does not touch.
- **Removing a page whose children are all the tree's**: a page of the tree under it can be one that stays, or one left because a person's page stands under it, and a site that takes a page's children into the trash with it would take that person's page two levels down. An empty listing is the one answer that puts nothing at stake.
- **The parent taken for a page where the site does not say it is a folder, or its listing's `404` taken for an empty parent**: either sends the publish on with no earlier tree, which makes a second tree beside one it could not see.
- **A refused removal stopping the publish**: every page of the list is already whole by then; a stop would only hide the rest of the removals from the closing line.
- **The earlier tree left beside the new one**: §6.
- **Finding the earlier tree by the ledger's earlier generation runs and their markers**: the tree under the parent is what the publish updates, and it may have been made from a working directory that is not this one; its root page names its run in its own property.
- **A tree of its own for each parent in one space**: the titles carry no marker, so a second tree's `Vespera` meets the first's anyway (§7).

## Consequences

- **`ConfluenceTitles`** draws §1's forms and has no marker; **`ConfluencePages`** the keys of §3, the root page's heading of §2 and `format` 2. That is `deliverable`'s change.
- **`PageList`** reads format 2; **`Publisher`** labels `vespera`, finds and checks the tree as §4 has it, takes an earlier tree as §6 has it, removes as §5 has it and prints their lines; **`ConfluenceApi`** gains the two listings of children, the page's `DELETE` and the label's `DELETE`. That is `confluence`'s change.
- **An account that may not delete pages** in the space leaves each page the rendering no longer has standing, said on its line and counted; a scoped token needs `delete:page`.
- **The tree a reader opens keeps its address** from one generation run to the next, each page at its place; a group that moved is found on its new place's page, and a page's history mixes the groups that had its place.
- **`README.md`**'s description of the rendering and its *Publishing to Confluence* say what §1, §5 and §6 have.
- **`CONTEXT.md`**'s Publish entry says that a publish moves to the trash the pages the rendering no longer has.
- **ADR-308, ADR-310, ADR-315, ADR-316, ADR-320 and ADR-321 carry a banner** pointing here. Their bodies are unchanged but for ADR-308's row of `confluence.RepublishUpdatesInPlaceIT` in its Tests, which no longer says that test holds that nothing is deleted and names the test that holds what is.

## What this does not decide

- **A page the label's search has not caught up with**, made or labelled by a publish a moment before: it is found by its title where its title is one of the list's, and otherwise not, as before this record.
- **Whether a page in the trash still holds its title** against a new page of that title; the tree's titles never meet one another's (§7).
- **What the site does with the children of a page it is asked to delete**: the publish asks only for a page whose listing of children is empty at that moment (§5).
- **Whether a page's listing of children names a child in the trash**: where it does, a page whose children the publish moved to the trash is left as it stands, and said, rather than removed; nothing is taken that should stand.
- **A page copied by a person with its label and its property**, beyond §4's lower id.
- **Anything on Data Center**, as ADR-308 §9.

## Tests

`deliverable` holds the rendering's half and runs in the `test` phase; the publish's half is tagged `confluence` and runs under a local `verify` (ADR-320 §6), against `StubConfluence`, which gains a trash, into which a page's `DELETE` takes whatever stands under it, as the worst a site could do; the children's listings; a label's removal; a page's update refused where another page holds its title, as its creation is; and answers set by a test, to a folder's read, a listing, a label's removal and a label's search's order.

| Method | What it holds |
| --- | --- |
| `deliverable.TheConfluenceRenderingTest.everyPageIsTitledByItsTextAndItsPlace` | the root page is `Vespera`, the seed partition's and the groups' titles end with their place alone (§1) |
| `deliverable.TheConfluenceRenderingTest.aLongTitleIsCutInItsTextAndItsSuffixIsKeptWhole` | the cut, the folding and the empty text with the suffix of §1 (§1; ADR-310 §4) |
| `deliverable.TheConfluenceRenderingTest.noPageCarriesTheRunsIdOrItsMarkerAndTheHtmlIndexStillNamesTheRun` | no title, key, body or file name of the rendering holds the run's id or its marker, and the HTML index's heading still names the run (§2, §8) |
| `deliverable.TheConfluenceRenderingTest.aKeyIsItsPlaceUnpaddedAndATitleItsPlacePadded` | ten groups in one seed partition are keyed `1.1` to `1.10` and titled `(1.01)` to `(1.10)` (§3) |
| `deliverable.TheConfluenceRenderingTest.thePageListListsEveryPageParentsFirstAndEveryPathStands` | format 2, keys `root`, `1`, `1.1`, `1.2` (§3) |
| `deliverable.TheConfluenceRenderingTest.theRootPageLinksEveryPageAndCarriesDocumentsCsv` | the root page opens `<h1>Deliverable</h1>` (§2) |
| `deliverable.AnItalianTreeIsDrawnInItalianTest.thePageListTitlesTheLaterPagesWithPagina` | `(1.1 pagina K)`, and `Vespera` (§1) |
| `deliverable.AnItalianTreeIsDrawnInItalianTest.theConfluenceRootAndPartitionPagesAreInItalian` | the root page opens `<h1>Base di conoscenza</h1>` (§2) |
| `deliverable.ItalianWordingTest.aSplitClustersConfluenceTitleSaysPagina` | an Italian later page is titled `TEXT (P.C pagina K)`, its first page `TEXT (P.C)`, and an English later page still says `page`, none with a marker (§1) |
| `confluence.PublishSendsEachPageWholeIT.aPageIsFilledBeforeTheNextIsCreated` | each page labelled `vespera` alone and keyed by its place (§4) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aLaterRunsRenderingUpdatesTheSameTreeInPlace` | another generation run's rendering creates no page, keeps every page's id, and retitles the page of a place to the group now there (§4) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aPageTheRenderingNoLongerHasIsMovedToTheTrash` | after the last body, a `DELETE` with no `purge`, its lines and the closing line's clause (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aRemovalTheSiteRefusesIsSaidAndThePageStands` | a `403` leaves the page, says so, counts it, and the publish ends as published (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aPageHoldingAPageTheTreeDoesNotOwnIsLeftAsItStands` | no `DELETE` of a page holding a person's page, and its line, *something stands under it* (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aSeedPartitionWhoseClusterHoldsAPersonsPageIsLeftWithIt` | a seed partition's page and its group's page the rendering no longer has, a person's page under the group's: no `DELETE` of either, a line each, the group's first, and both counted left (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.severalPagesAreRemovedDeepestFirstAndInTheOrderOfTheirNumbers` | five removals, `1.1.2`, `1.2`, `2.9`, `2.10` then `2`, in that order, by their `DELETE`s, their lines and the trash (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aRemovalAnswered404IsSaidToBeGoneAlready` | a `DELETE` answered `404` is said `already gone` and counted removed (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aStopDuringTheRemovalsSaysWhichRemovalItStoppedAt` | a listing of children the site refuses stops the publish at that removal, `It stopped at removal 2 of 2, <title>.`, the page removed before it staying in the trash (§5) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.ofTwoPagesHoldingOneKeyTheLowerIdIsTheTreesAndTheOtherIsUntouched` | of two pages holding one key, the lower id is updated though the label's search finds it last, and the other is neither updated nor removed (§4) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aTreeAnEarlierPublishMadeWithItsMarkerIsTakenAndRetitled` | the earlier tree's pages keep their ids, are retitled, labelled `vespera` alone and keyed by place, their files not sent again, and the line says so (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.twoTreesWithTheirMarkerUnderTheParentStopThePublishBeforeAnyWrite` | the stop names both, and nothing is written (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aPageTitledVesperaTheTreeDoesNotOwnStopsThePublishBeforeAnyWrite` | the clash is refused in one line, and nothing is written (§7) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aRetitleToATitleAPageOutsideTheTreeHoldsStopsThePublishBeforeAnyWrite` | a later run retitling a page of the tree to a title a page outside it holds is refused in one line, and nothing is written (§7) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aPageTitledVesperaStopsATakingBeforeAnyWrite` | a page titled `Vespera` outside the tree stops a taking in one line before its first write, the earlier tree left as it was (§6, §7) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aTakingStoppedBeforeAnEarlierLabelWasTakenOffTakesItOffOnTheNextPublish` | a taking stopped by a label's removal the site refuses, on a seed partition's page or on the root page, ends on the next publish with every page labelled `vespera` alone and keyed by its place (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aParentThatIsAPageHasItsChildrenListedAsAPagesAndItsTreeTaken` | a parent that is a page is listed by `pages/{id}/direct-children`, and the earlier tree under it is taken (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aParentTheSiteWillNotSayIsAFolderStopsThePublishBeforeAnyWrite` | a folder's read answered `403` or `500` stops the publish, its children never listed as a page's, and nothing is written (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aParentsListingAnswered404StopsThePublishBeforeAnyWrite` | the parent's listing answered `404` stops the publish, and no tree is made (§6) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.theTreeStandingUnderAnotherParentStopsThePublishBeforeAnyWrite` | the stop names where the tree stands, and nothing is written (§4) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aPageListOfFormatOneIsRefusedBeforeAnythingIsSent` | the refusal says to draw again, and no request is sent (§3) |
