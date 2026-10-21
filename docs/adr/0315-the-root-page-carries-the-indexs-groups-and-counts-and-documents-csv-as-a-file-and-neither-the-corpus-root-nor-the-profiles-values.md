# ADR-315 — The root page carries the index's groups and counts and `documents.csv` as a file, and neither the corpus root nor the profile's values

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Rests on**: [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md), by which the root page lies directly in `confluence/`, and [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md), by which it is titled `Vespera <marker>`.
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1.4, [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) and [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) for the HTML tree's index, which still states the corpus root and every profile key (§1). [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5: no run id moves (§5).
- **Decided**: by the operator's ruling 6 of map [#604](https://github.com/algernon28/vespera/issues/604), of 2026-10-19, that the root page carries neither the absolute corpus root nor the profile's values, fixed in the record (§1); the rest under the operator's delegation of 2026-10-21.
- **Settles**: [#616](https://github.com/algernon28/vespera/issues/616).

## Context

The HTML tree's index opens with the run, the walk, the corpus root and every profile key with its value (`IndexPage.open`, ADR-274 §1.4, ADR-186), then an `<h2>` for each seed partition over its groups. `documents.csv` has eleven columns, its paths beneath the corpus root and its `copy` column the copy's path in the tree. `vespera-deliverable.txt` names the working directory and the generation run (ADR-276 §3). What a publish sends lies on the operator's Cloud site, seen by whoever the parent's space lets see it (ADR-308, ADR-313 §4).

## Decision

### 1. What the root page leaves out

The root page carries **neither the corpus root**, absolute or in part, **nor any profile key or its value**. It carries no walk id and no path of the working directory. These stay on the HTML tree's index, which is unchanged.

### 2. What it carries

In this order:

- the generation run's id, whole, under the heading the HTML index gives it, so the site's pages can be matched with a working directory's runs;
- one sentence: *"Each group's page carries a checked copy of every document it lists. documents.csv lists every document of this run."*;
- for each seed partition, in the index's order, a link to the seed partition's page, and beneath it the index's table of its groups, each linking its cluster's page by ADR-310's title, with the counts the index shows;
- the index's lines on what was not drawn, in its words.

Links between pages are `ac:link` over `ri:page` by title, as ADR-309 names a page.

### 3. `documents.csv` is a file on the root page

`documents.csv` is carried on the root page as a file of its own, its bytes the HTML tree's file unchanged, and listed with its size and digest in the page list (ADR-316). Its paths are beneath the corpus root and never absolute, so it holds nothing §1 leaves out; its `copy` column names copies by their paths in the HTML tree, which the copies' names on their pages repeat.

### 4. What is never carried

The page list lists neither `vespera-deliverable.txt`, nor `code.csv` or anything under `code/`, nor anything an earlier drawing left (ADR-312 §5), and a publish sends only what the page list lists. How code sets appear in the Confluence rendering is not decided here; until a record decides it, none is carried.

### 5. Fixed in the record

Nothing in §1 to §4 is a value the operator sets, so no run id moves and the control panel gains no value.

## Alternatives refused

- **The root page as a copy of the index**: it would put the corpus root and the profile on the operator's site, against ruling 6.
- **`documents.csv` left off**: it is the one listing of every document of the run, and holds nothing ruling 6 keeps off.
- **The profile's values on a page of their own**: the same values, on the same site.

## Tests

Built by [#620](https://github.com/algernon28/vespera/issues/620).

| Method | What it holds |
| --- | --- |
| `…theRootPageNamesNeitherTheCorpusRootNorAProfileValue` | for a fixture whose corpus root and profile values are distinctive strings, the root page's body holds none of them (§1) |
| `…theRootPageLinksEverySeedPartitionsPageAndEveryClustersPage` | each link names a page the page list lists, by its title (§2) |
| `…documentsCsvIsAFileOfTheRootPage` | the page list lists it on the root page with the HTML tree's bytes' size and digest (§3) |
| `…thePageListListsNoMarkerNoCodeAndNothingLeftBehind` | none of them in the page list (§4) |
