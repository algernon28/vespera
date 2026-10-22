# ADR-316 — The page list is `pages.json` in `confluence/`, each page keyed by its run and place, with its parent, title, body, files, sizes and digests

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Rests on**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §8, how a publish finds, skips and updates a page; [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md), the titles; [ADR-312](0312-the-confluence-rendering-lies-under-confluence-at-the-trees-root-drawn-by-every-drawing-and-what-an-earlier-drawing-left-in-it-is-counted-and-kept.md), where the rendering lies; [ADR-313](0313-a-clusters-confluence-page-carries-its-copies-as-files-and-a-copy-the-site-refuses-is-counted-and-listed-by-the-publish.md), [ADR-314](0314-the-confluence-rendering-writes-each-picture-its-cluster-page-shows-as-a-file-of-its-own-from-the-same-bytes-the-html-page-inlines.md) and [ADR-315](0315-the-root-page-carries-the-indexs-groups-and-counts-and-documents-csv-as-a-file-and-neither-the-corpus-root-nor-the-profiles-values.md), the files a page carries.
- **Keeps**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3, nothing is deleted; [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, no run id moves.
- **Decided**: under the operator's delegation of 2026-10-21.
- **Settles**: [#618](https://github.com/algernon28/vespera/issues/618).

## Context

ADR-308 makes the publish the one reader of the page list: it creates every page before any body, finds a page by its stable key in a content property, skips one whose local body's SHA-256 the property already holds, and replaces a file that stands through its update endpoint. A Cloud site normalises a body on save, answers 400 to a second `POST` of a file name, and keeps titles unique within a space (`docs/research/confluence-loading.md` §5). Every title carries its generation run's marker (ADR-310), so two generation runs never share a page. The jar reads and writes JSON through Jackson already.

## Decision

### 1. Where and in what form

The page list is **`confluence/pages.json`**, UTF-8 JSON, written through `pages.json.part` and moved into place after every page and picture's file of the rendering is written, so a page list that stands names only files that stand. It is rewritten whole at every drawing.

### 2. What it holds

```json
{
  "format": 1,
  "generationRun": "<the generation run's id>",
  "marker": "<ADR-310's marker>",
  "pages": [
    {
      "key": "<stable key, §3>",
      "parent": "<the parent's key, or null for the root page>",
      "title": "<ADR-310's title>",
      "body": "<path of the storage body, from confluence/>",
      "bodySha256": "<hex SHA-256 of the body's bytes>",
      "files": [
        { "name": "<the file's name on the page>", "path": "<its path, from confluence/>",
          "kind": "copy | picture | listing", "size": 0, "sha256": "<hex>" }
      ]
    }
  ]
}
```

- **Pages are listed parents first**: the root page, then each seed partition's page followed by its clusters' pages, in the index's order, each split cluster's pages in their order (ADR-303).
- **Paths are relative to `confluence/`, with `/` between parts.** The root page's body lies directly in `confluence/`, and every other page's in its seed partition's or its cluster's folder there (ADR-312 §1). A picture's file lies beside its page's body (ADR-314). A copy is not written twice: its path is the HTML tree's copy, `../<seed partition>/<cluster>/<copy>`, and `documents.csv` is `../documents.csv` (ADR-315 §3). A copy that does not stand is not listed.
- **`kind`** is `copy`, `picture` or `listing`, the last for `documents.csv`. **`size`** is the file's bytes as written, read by `Files.size`; **`sha256`** is the digest of those bytes. A copy's digest is stage 2's key, which its bytes were checked against (ADR-274 §2.2), so it is written without reading the copy again.
- Nothing in it names the corpus root, the working directory, a profile value or the operator's site.

### 3. The stable key

A page's key is `<generation run>:<place>`, the place being `root`, `<P>` for a seed partition's page, `<P>.<C>` for a cluster's page and `<P>.<C>.<K>` for page `K` of a split cluster from 2, with `P` and `C` the orders ADR-310 pads. The same generation run drawn again gives every page the same key, and a publish updates it in place; another generation run gives other keys and other titles, and its pages are new.

### 4. What a publish does with it

What ADR-308 §8 leaves to this record:

- **The content property** is named `vespera`, its value `{"key": "<key>", "bodySha256": "<hex>"}`, set on create and on every update of the body.
- **Every page is labelled** `vespera-<marker>`, so the operator can find a run's pages by label.
- **Order**: every page the list lists is created, empty, parents first, before any body is written; then each body is written; then each page's files are sent.
- **An update** sends the body with the page's version plus one. A `409` is answered by reading the page's version again and sending once more; a second `409` stops the publish.
- **A file** is skipped where one of its name stands on the page with the same size and its comment holds its `sha256`; otherwise it is sent new, or through the file's update endpoint where its name stands. The publish sets the file's comment to its `sha256`.
- **Every request** that sends a file carries `X-Atlassian-Token: no-check`.
- A page of the same generation run whose key the list no longer holds is left as it stands and counted on the closing line (ADR-308 §8).

### 5. Run ids

The page list is drawn by `deliverable` and read by `confluence`, and no value is added; no run id moves.

## Alternatives refused

- **A CSV**: a page's files are a list of their own, and a CSV would need a second file or a cell of joined values.
- **A key from the title**: a title holds a model's text, which a repair pass may change (ADR-310); the place does not change within a run.
- **A key with no run**: a second run's pages would update the first's in place, though their titles differ and both are meant to stand.
- **Copies written a second time under `confluence/`**: the bytes would lie twice for no reader that needs them there.

## Tests

| Method | What it holds |
| --- | --- |
| `…thePageListListsEveryPageParentsFirst` (#620) | the root page first, each seed partition's page before its clusters', in the index's order (§2) |
| `…everyPathInThePageListNamesAFileThatStands` (#620) | each body and file path resolves from `confluence/` to a file whose size and digest match (§1, §2) |
| `…thePageListNamesNoCorpusRootNoWorkingDirectoryAndNoProfileValue` (#620) | distinctive fixture strings appear nowhere in it (§2) |
| `…aRedrawingOfTheSameRunKeepsEveryKey` (#620) | two drawings of one generation run give equal keys (§3) |
| `…aPublishCreatesEveryPageBeforeAnyBody` (#633) | against a stub, every create precedes the first body update (§4) |
| `…aSecond409StopsThePublish` (#633) | one 409 is answered by a re-read and a resend; a second stops it (§4) |
