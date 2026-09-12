# ADR-104 — The surviving originals stay where they are, and the deliverable references them

- **Date**: 2026-09-12
- **Status**: accepted
- **Answers**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) — the last of the questions it handed forward to the 6a/6b slice. With [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), that record's obligations are discharged.
- **Names**: [ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md) — already superseded by ADR-101. The question it asked now has an answer, and it is the opposite of the one it gave.

## Context

[ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md) put the surviving originals into a Confluence space as attachments, so the result was self-contained and searchable rather than a set of links into a directory nobody backs up. [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) superseded it and was explicit that the *reasoning* was discarded rather than deferred — it was an argument about Confluence's storage — while the underlying question survived with no answer at all.

[ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) settled what the deliverable is and where it lands, and fixed the test this record has to satisfy: **every link inside the deliverable resolves with no database, no ledger and no network.** It deliberately left what sits inside that boundary open.

Three options were live, and the third is the one that could have been taken quietly:

- **Copy the originals into the tree.** Self-contained in the strongest sense, at the cost of duplicating an archive measured in hundreds of gigabytes.
- **Reference them.** Free, and the links are only as durable as the archive's location.
- **Write the extracted text instead.** Available and cheap: `extraction_cache.response_json` already holds Docling's full response for every extracted document, keyed by content hash and extractor identity.

## Decision

**The surviving originals stay where they are. The deliverable references them and copies nothing.**

### Why not copy

**Nothing in this system moves a document.** `CONTEXT.md` says it of a **survivor** — an order over survivors, never a copy of them — and again of the **arrangement**: a document is arranged where it belongs, not moved there. Copying originals into the deliverable would be the first thing in the pipeline to break that rule, and it would break it at the largest scale in the system: on the terminal stage, over the whole survivor set, with disk-exhaustion and partial-copy failures that have no verdict they could be recorded as.

**The archive is kept, and is assumed static** ([ADR-016](0016-the-corpus-is-treated-as-static.md)): one archive, walked once, re-walked because code changed rather than because content did. The deliverable exists to make that archive navigable, not to become a second copy of it that starts drifting the moment it is written.

### Why not the extracted text either

This is refused explicitly rather than passed over, because it is the option that looks like a compromise. Writing the extracted text beside each cluster would manufacture **a third rendering of every document** — neither the original the operator owns nor the synthesis this stage exists to produce — and would duplicate what the ledger already holds. A deliverable carrying it invites a reader to read the wrong artifact, and invites a later stage to treat a copy as a source.

### Every survivor is listed, not only the cited ones

Each cluster file ends with its **membership**: every survivor arranged into that cluster, linked. Citations resolve to those same entries.

The arrangement is an order over *all* survivors. A cluster file listing the three documents a synthesis doc happened to mention, out of the forty the ledger arranged into it, would misstate the very thing it renders.

### A manifest beside the prose

**`documents.csv` at the root of the tree**, carrying occurrence id, root-relative path, content hash, winning seed, relevance score, seed partition and cluster. Every column is one the ledger already holds, so it costs a query.

It is what lets an operator build whatever comes after the hand-off — ADR-101 leaves them to make a wiki if they want one — without parsing Markdown or opening the database.

### What a link physically is

A file occurrence's path is root-relative already ([ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md)): the path beneath the corpus root, separators rewritten to `/`, with the canonicalised root held once on the walk. An entry whose name has no storable form never becomes an occurrence, so every survivor has a well-formed relative path to render.

- **In the prose**, a link is an absolute `file:` target, composed at write time from the recorded root and the occurrence's relative path.
- **In `index.md`**, the root is recorded once.
- **In `documents.csv`**, the paths stay root-relative.

### 6b does not check that the originals are still there

No stat, and no existence check at write time. A missing original at 6b has no verdict it could be recorded as — 6a and 6b remove nothing — and inventing one would make the terminal stage a second, partial census. ADR-016's assumption is exactly what is being leaned on, and the walk id in `index.md` is what says when the links were true.

## Consequences

**The links are not durable, and this record says so rather than implying otherwise.** They resolve on the machine that produced them, and every one of them dies if the archive moves. What the recorded root and the root-relative manifest buy is that re-pointing the whole tree is mechanical — one known root, one column of relative paths — instead of a deliverable that has quietly become a list of dead ends with nothing left to reconstruct it from.

**"Self-contained" means without the ledger, not without the archive.** A `file:` link satisfies ADR-103's test: the filesystem resolves it, and no part of this tool is needed to follow it. What it does not promise, and what ADR-103 never asked for, is that it resolves on somebody else's machine.

**No junction or symlink `originals/` inside the tree.** Refused for the reason ADR-103 refused a `latest` link: census is Windows-first, and this repository's symlink fixtures already abort by assumption where the privilege to create one is missing.

**The citation decision inherits a narrower question.** A citation no longer has to introduce a link of its own: every survivor is in its cluster's membership already, and `documents.csv` gives a mechanical existence check something to resolve against without opening the database.

**An operator who wants to hand the deliverable to someone else has to send the archive too.** That is a narrowing of what the tool produces, in the same direction ADR-101 already took deliberately.
