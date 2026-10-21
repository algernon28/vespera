# ADR-313 — A cluster's Confluence page carries its copies as files, and a copy the site refuses is counted and listed by the publish

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Rests on**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md) §3, which partly reopens ADR-023 and leaves how copies are carried to this record; [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md), by which a publish sends the rendering and its files; and [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md), by which it lies under `confluence/`.
- **Partly reopens**: [ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md), for the Confluence rendering: the published pages are self-contained and indexed by Confluence search, each copy a file on its cluster's page, not an object store reached by links (§1).
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §2, a copy is put in the tree only once its bytes hash to stage 2's key, and §5, no run id moves (§2, §6); [ADR-006](0006-census-measure-before-judging.md) and [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md), no threshold of ours ships set (§3).
- **Decided**: by the operator's ruling 5 of map [#604](https://github.com/algernon28/vespera/issues/604), of 2026-10-19, that copies are carried as files on their cluster's page, fixed in the record, and that a copy over the site's file cap is skipped and reported (§1, §3); the rest under the operator's delegation of 2026-10-21.
- **Settles**: [#617](https://github.com/algernon28/vespera/issues/617).

## Context

In the HTML tree a cluster's folder holds its page and a copy of each of its survivors, under the original's own name made safe (ADR-280 §0, §1), and `documents.csv`'s `copy` column names it. ADR-308 makes the publish the one thing that carries the rendering to Confluence, under one parent in the operator's space. A Cloud site's file cap is an admin's setting, 100 MB by default [unverified]; a measured Cloud site took every body and file sent up to 10 MiB (`docs/research/confluence-loading.md` §5). A file whose name already stands on a page is refused by a second `POST` and replaced through its update endpoint.

## Decision

### 1. Copies are files on their cluster's page

Each copy the HTML tree carries for a cluster is carried by the Confluence rendering as a file on that cluster's page, under the same name, and its entry on the page links it by that name (`ri:attachment`). The bytes are the HTML tree's copy, checked as ADR-274 §2 checks it; the rendering adds no second check. A survivor the HTML tree names with no copy, for the reason ADR-280 §5 gives, is named the same way on the Confluence page, with no file.

### 2. Fixed in the record

Carrying copies is fixed: no profile key, Spring property or CLI option turns it off, so no run id moves (ADR-274 §5) and the control panel gains no value (ADR-292 §1).

### 3. A copy the site refuses

- **The publish sends every copy** the page list lists and holds no cap of its own: Vespera ships no threshold for a file's size (ADR-006, ADR-061). The page list gives each copy's size, for the operator's reading and the publish's line.
- **A copy the site refuses as too large** is skipped: the page is published without it, and its entry still names the copy, as one with no copy is named (§1). Any other refusal of a file stops the publish, as a refused page does; [#633](https://github.com/algernon28/vespera/issues/633) builds both.
- **The publish reports what it skipped**, in two places:
  - its closing line counts the copies skipped and their bytes, with no names, *"; N copies were refused by the site as too large and are not on their pages"*, `copy was` for 1, and no clause where none was;
  - `confluence-not-carried.csv`, written into the working directory at every publish that skips one, with a row for each: the page's stable key, the copy's name and its size in bytes. A publish that skips none leaves no such file and removes none an earlier one wrote.
- A copy skipped by one publish is sent again by the next, as any file the page lacks is (ADR-308 §8).

### 4. Who sees a copy

Everyone who can see its page sees its copies, on Cloud as Confluence grants a page's files with the page. The pages lie under the parent the operator names (ADR-308 §3), so the parent's space and its restrictions decide who that is. Ruling 7's one space per generation run no longer holds (ADR-308, ADR-310).

### 5. Pictures

Whether a picture is written as a file of its own in the rendering is [#615](https://github.com/algernon28/vespera/issues/615)'s; a picture's file that a site refuses is reported as §3 reports a copy, once #615 writes one.

### 6. Run ids

The rendering's part is in `deliverable` and the publish's in `confluence`; neither reads a value of the profile, so no run id moves.

## Alternatives refused

- **Copies named by their path only, with no file**: the pages would not be self-contained, and Confluence search would not index the copies (ADR-023's reason, reopened by ADR-301).
- **A size cap of ours, skipping copies before sending**: the site's cap is an admin's and unknown to Vespera; a cap of ours would be a threshold set before it was measured (ADR-006).
- **Reporting each skipped copy on the console by name**: the closing line carries numbers alone, and a list is read from a file the operator opens.

## Consequences

- **The page list** carries each copy's name, its path in `confluence/` and its size (#618).
- **`README.md`'s publish section** names `confluence-not-carried.csv` (#621).

## Tests

| Method | What it holds |
| --- | --- |
| `…eachCopyIsAFileOnItsClustersPage` (#620) | each copy the HTML tree carries for a cluster is listed as a file of that cluster's page, under the same name, its entry linking it (§1) |
| `…aCopyTheSiteRefusesAsTooLargeIsSkippedCountedAndListed` (#633) | against a stub refusing one file as too large: the page is published without it, the closing line counts it, `confluence-not-carried.csv` lists it, and the rest are sent (§3) |
| `…anotherRefusalOfAFileStopsThePublish` (#633) | a refusal for another reason stops the publish (§3) |

## What this does not decide

- **Pictures' files**: #615.
- **The page list's format**: #618.
