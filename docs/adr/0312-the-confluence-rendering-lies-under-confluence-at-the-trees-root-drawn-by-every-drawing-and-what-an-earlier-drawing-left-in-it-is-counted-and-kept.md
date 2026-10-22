# ADR-312 — The Confluence rendering lies under `confluence/` at the tree's root, drawn by every drawing, and what an earlier drawing left in it is counted and kept

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Rests on**: [ADR-301](0301-stage-6b-draws-a-confluence-storage-format-rendering-beside-the-html-tree-and-beyond-drawing-it-nothing-in-this-project-renders-uploads-or-transmits-the-deliverable.md), by which stage 6b draws the Confluence rendering beside the HTML tree, and [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md), by which a publish reads its page list.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1's tree, which gains `confluence/` (§1); and §2.7's bound of 69 characters on the working directory's path, which becomes 58 (§3).
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1's bound of 146 characters on a named root's path, which becomes 135 (§3); and §6's closing line, whose count of folders left behind counts those in `confluence/` (§4).
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §4, whose listing of what an earlier drawing left looks inside `confluence/` as [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §6.3 looks inside `code/` (§4).
- **Keeps**: ADR-274 §5 and §6: every invocation that reaches the tree draws it, and a change to `deliverable` alone moves no run id (§2, §6). [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3: nothing beneath a deliverable root is deleted (§5). [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md): nothing ships that no decision requires (§2).
- **Decided**: by the operator's ruling 4 of map [#604](https://github.com/algernon28/vespera/issues/604), of 2026-10-19, that the rendering is drawn always with no setting (§2); the rest under the operator's delegation of 2026-10-21.
- **Settles**: [#612](https://github.com/algernon28/vespera/issues/612).

## Context

The HTML tree's top level holds `index.html`, `documents.csv`, the marker `vespera-deliverable.txt`, a folder for each seed partition, and, where the run has a code set, `code/` and `code.csv` (ADR-274 §1, ADR-276 §1, ADR-296 §1). A seed partition's folder is named by `IndexPage.partitionDirectoryName`: its order, zero-padded, a `-`, and a slug of its seed's stem, so every such name begins with a digit. Code sets lie inside `code/`, never at the top (ADR-296 §3.1).

After drawing, `Deliverable.leftBehindBy` lists the top level and each seed partition's folder written, and counts each folder this drawing did not write as left behind, `documents/` apart (ADR-280 §4); inside `code/` it counts the folders directly there (ADR-296 §6.3).

ADR-274 §2.7 bounds the working directory's path at 69 characters so that every file of the tree is within Windows' 260; ADR-276 §1 bounds a named root's at 146. The rendering's files lie 11 characters deeper than the HTML tree's, `confluence/` being 11.

## Decision

### 1. Where it lies

The Confluence rendering lies under **`confluence/`, directly at the tree's root**, beside `index.html`. Inside it, its pages and the files they carry follow the HTML tree's shape: the root page and the page list directly in `confluence/`, and a folder for each seed partition and, within it, for each cluster, named as the HTML tree names them. What each page holds, how pictures and copies are written there and the page list's name and entries are [#615](https://github.com/algernon28/vespera/issues/615)'s, [#616](https://github.com/algernon28/vespera/issues/616)'s, [#617](https://github.com/algernon28/vespera/issues/617)'s and [#618](https://github.com/algernon28/vespera/issues/618)'s.

**No name is reserved for it**: no folder the tree draws can be called `confluence`. A seed partition's folder begins with a digit, a code set lies inside `code/`, and the top level's files are named by the code. A rule that changes how a seed partition's folder is named, or puts any folder of the operator's naming at the top, states how it keeps clear of `confluence/`.

### 2. Drawn always

Every invocation that reaches the tree draws the rendering beside the HTML tree, as it draws the HTML tree (ADR-274 §6). Nothing the operator sets turns it on or off: no profile key, which would move the generation run (ADR-274 §5); no Spring property, which the control panel could not set without amending ADR-292 §1 and ADR-285; no CLI option, which would grow the command line against ADR-047. Drawing it costs the disk its pages take, and a choice that saved that would ship a value no decision requires (ADR-216).

### 3. The path budget

A file of the rendering lies 11 characters deeper than its counterpart in the HTML tree. So **every file of the tree is within Windows' 260 characters while the working directory's path is at most 58**, and a named deliverable root's at most 135. Past those the JDK still writes the file, as ADR-274 §2.7 says; what may fail is another program that does not take long paths. A publish reads the files through the JDK.

### 4. What an earlier drawing left in it

- **`confluence/` is a folder every drawing writes**, so it is never itself counted as left behind.
- **Inside it, a folder this drawing did not write counts as left behind**, in ADR-280 §4's N, looked at as the tree's top level is: a seed partition's folder directly in `confluence/`, and a cluster's folder directly in a seed partition's folder this drawing wrote there. A file is not counted, and nothing deeper is looked at.
- The closing line's clause is ADR-280 §4's, its N counting these with the others.

### 5. What stays

Nothing in `confluence/` is deleted (ADR-276 §3). A page or a folder an earlier drawing left there stays where it is, and **a publish reads the page list alone**: what the page list does not list is not published, whatever lies beside it.

### 6. Run ids

The rule lives in `deliverable` and reads no value of the profile, so no run id moves; the next invocation redraws the tree, `confluence/` with it, at no cost (ADR-274 §5, §6).

## Alternatives refused

- **A tree of its own beside the HTML tree**, at `deliverable/<generation run>-confluence/` or under a second root. It would need a second marker, a second refusal at the drawing and, for a named root, a second value the operator sets, for no gain over a folder inside the tree.
- **The rendering's files beside the HTML tree's in each cluster's folder.** The two renderings would share names they do not share in meaning, and a publish could not take its files apart from the HTML tree's by place.
- **A setting that turns the rendering off**: refused by ruling 4 (§2).
- **Deleting what an earlier drawing left in `confluence/`**: ADR-276 §3 deletes nothing beneath a deliverable root; the page list is what a publish trusts.

## Consequences

- **`README.md`'s note on long paths** names 58 and 135; [#606](https://github.com/algernon28/vespera/issues/606) carries it with the rest.
- **`CONTEXT.md`'s Page list** says where it lies: directly in `confluence/`.

## Tests

Built by [#620](https://github.com/algernon28/vespera/issues/620), with the renderer.

| Method | What it holds |
| --- | --- |
| `…theConfluenceRenderingLiesUnderConfluenceAtTheRoot` | a drawing writes `confluence/` at the tree's root, with the root page and the page list directly in it (§1) |
| `…noSeedPartitionsFolderCanBeCalledConfluence` | a seed partition whose seed's stem is `confluence` is drawn at `<order>-confluence/`, beside `confluence/` (§1) |
| `…aFolderInConfluenceThisDrawingDidNotWriteIsCountedAsLeftBehind` | one folder in `confluence/` counted, `confluence/` itself not (§4) |
| `…nothingInConfluenceIsDeleted` | a folder an earlier drawing left in `confluence/` stands after a redrawing (§5) |

## What this does not decide

- **What each page holds and how its files are written**: #615, #616, #617.
- **The page list's name, entries and format**: #618.
