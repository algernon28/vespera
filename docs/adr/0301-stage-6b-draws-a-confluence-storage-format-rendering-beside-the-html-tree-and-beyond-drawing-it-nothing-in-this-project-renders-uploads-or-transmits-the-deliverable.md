# ADR-301 — Stage 6b draws a Confluence storage-format rendering beside the HTML tree, and beyond drawing it nothing in this project renders, uploads or transmits the deliverable

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md), its Decision's opening sentence, which reads as §1 here, and its Context's "rendering the result into a wiki", which stays struck as putting the result into a wiki and not as drawing files for one (§1). Seven stages, stage 7 struck, no `publication` module, no `vespera publish`, and every other section stand.
- **Amends**: [ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md), its section *"The artifact entry stops answering a question that is open"*: the **Publication-ready artifact** entry names the HTML tree and, beside it, a drawn Confluence storage-format rendering of it (§2). Its Decision stands: publication is an adjective and never a verb, and nothing in this project is ever said to publish anything.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), its Decided here 1, whose refusal of Markdown stands and whose reason "two renderings of one tree are two to keep in step" no longer bars a second rendering (§4). Its Keeps of ADR-101 and ADR-102, its §1 for the HTML tree, and its refused alternative *Markdown beside the HTML* stand.
- **Partly reopens**: [ADR-002](0002-confluence-is-the-publication-target.md), only as far as §3 says: Confluence is again a target a rendering is drawn for, and not a destination this project sends anything to. Everything else ADR-101 superseded of it stays superseded.
- **Partly reopens**: [ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md), only as far as §3 says: the rendering carries the survivors' copies as files on their cluster's page, as ruling 5 of [#604](https://github.com/algernon28/vespera/issues/604) fixes; how it carries them is [#617](https://github.com/algernon28/vespera/issues/617)'s to record.
- **Keeps**: [ADR-025](0025-stage-7-is-an-adapter-not-a-stage.md) as ADR-101 amended it: the pipeline terminates at a self-contained artifact, and rendering it into a destination of any kind is not this project's concern; drawing a second set of files at the terminus is not that (§1).
- **Decided**: under the operator's ruling 1 of map [#604](https://github.com/algernon28/vespera/issues/604), of 2026-10-19, that ADR-101's Decision and ADR-102's artifact entry are amended for a drawn Confluence rendering only, partly reopening ADR-002 and ADR-023, and that beyond drawing it Vespera renders, uploads or transmits nothing anywhere itself; and under the operator's delegation for how far each record is amended (§1 to §4).
- **Rests on**: [`docs/research/confluence-loading.md`](../research/confluence-loading.md) — the research of [#605](https://github.com/algernon28/vespera/issues/605): what storage format is and what the tree is not, §1 and §2.5.
- **Settles**: [#607](https://github.com/algernon28/vespera/issues/607).

## Context

ADR-101 ends the run at stage 6b and says that nothing in this project renders, uploads or transmits the deliverable anywhere. ADR-274 makes the tree HTML and HTML only, refusing Markdown beside it because nothing reads it and because two renderings of one tree are two to keep in step.

Map [#604](https://github.com/algernon28/vespera/issues/604) asks for the deliverable to be loadable into Confluence. Confluence takes a page's body in its storage format, well-formed XML based on XHTML, in which a link to another page is an `<ac:link>` element naming that page's title (`docs/research/confluence-loading.md` §2.5). The HTML tree is not in that form. Its cluster pages link to their copies by relative path and to their entries by fragment; a seed partition has no page of its own, only a heading on the index and a folder (ADR-274 §1.4, ADR-280 §0); its pictures are inlined as `data:` URIs (ADR-286). Something has to turn the tree into storage format before anything can load it.

That something can lie inside the project, drawn by stage 6b beside the HTML tree, or outside it, as a converter of the operator's own that reads the HTML tree (option D of #604). Sending the tree from the jar or the control panel is a separate question, refused by #604 and recorded by [#608](https://github.com/algernon28/vespera/issues/608).

The tree's layout is not fixed. ADR-280 moved the copies into each cluster's folder, ADR-286 inlined the pictures, ADR-296 put the code sets under `code/` ([#573](https://github.com/algernon28/vespera/issues/573)), and [#571](https://github.com/algernon28/vespera/issues/571) may split one cluster's page into several. Each such change is held by tests in this repository, and a converter outside it is held by none of them.

## Decision

**A Confluence storage-format rendering of the tree is drawn inside this project, by stage 6b, beside the HTML tree, as files for the operator to load. Beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere.** The reach is a drawn rendering and nothing more.

### 1. ADR-101's Decision, as it now reads

> **The pipeline's last stage is 6b. What it produces is the deliverable. Beside the HTML tree, stage 6b draws a Confluence storage-format rendering of the same tree, as files, for the operator to load; beyond drawing it, nothing in this project renders, uploads or transmits the deliverable anywhere.**

*Renders* keeps ADR-101's sense: rendering the deliverable into a destination, a wiki, a space or a site. Drawing files in the storage format a wiki reads is not that, and it is the only thing this record adds. Putting those files into a wiki is the operator's own doing, through a loader of their own, and stays outside this project; in that sense ADR-101's Context phrase "rendering the result into a wiki" stays struck.

The rest of ADR-101 stands: seven stages, stage 7 struck and not an adapter, no `publication` module, and no `vespera publish`. Stage 6b is still the last stage; the rendering is part of what it draws and is not a stage of its own.

### 2. ADR-102's artifact entry, as it now reads

The **Publication-ready artifact** names: **the arrangement and the synthesis docs written over it, self-contained and handed over as files — an HTML tree, and beside it a drawn Confluence storage-format rendering of it.** What ADR-274, ADR-284 and ADR-296 add to the entry, the copies and the code, stands.

*Publication* stays an adjective, the state the files are handed over in, and is never a verb with this project as its actor. Nothing in this project is ever said to publish anything. The rendering is drawn, and nothing here uploads it. The entry's _Avoid_ line stands: the artifact is not named after the wiki or after one of its renderings.

### 3. How far ADR-002 and ADR-023 reopen

- **ADR-002**: Confluence is again a target a rendering is drawn for, its storage format being what the rendering is written in, for the editions [#609](https://github.com/algernon28/vespera/issues/609) settles. It is not a destination this project sends anything to: that the sink is reached is the operator's own loader's doing. Everything else ADR-101 superseded of ADR-002 stays superseded.
- **ADR-023**: the rendering carries the survivors' copies as files on their cluster's page (ruling 5 of #604), so its argument that the result is self-contained and searchable, rather than links into a directory, returns for the rendering. How it carries them is #617's to record, not this record's.

Neither record's banner *"Superseded — see ADR-101"* is removed. Each gains a second banner, *"Partly reopened — see ADR-301"*, stating the reach above. Their verbatim summaries are untouched: a reconstituted record keeps its summary, and the banner is what tells a reader how much of it holds.

### 4. ADR-274

- **Its Keeps line stands**: under ADR-101 and ADR-102 nothing carries the tree anywhere. Drawing a second rendering beside it is not carrying it.
- **Its §1, "The tree is HTML, and HTML only", stands for the HTML tree**: no Markdown is written. The Confluence rendering is a separate set of files beside it, whose place and shape are [#612](https://github.com/algernon28/vespera/issues/612)'s, [#620](https://github.com/algernon28/vespera/issues/620)'s and the other tickets of #604's.
- **Its Decided here 1 is partly reopened.** Its refusal of Markdown stands: nothing reads Markdown. Its general reason, that two renderings of one tree are two to keep in step, no longer bars a second rendering. The cost is accepted for the Confluence rendering because something reads it, and it is answered by the two renderings carrying the same entries and the same citations, held by a parity test that #620 builds with the renderer.

ADR-274 gets a banner *"Partly amended — see ADR-301"*, and so do ADR-101 and ADR-102.

### 5. Run ids

This record changes no code, adds no setting and names no module, so no run id moves.

## Alternatives refused

- **Option D, a converter of the operator's own outside the repository**, turning the HTML tree into storage format. It re-derives the tree's layout rules outside the repository, breaks silently at each change of the layout, as #571's several pages and #573's code sets are, and nothing in this project can test it. A rendering drawn inside the project is held by its tests.
- **Reopening ADR-002 and ADR-023 whole, or removing their *Superseded* banners.** It would make Confluence a destination this project sends to again, and a reader would take a verbatim summary as current where only part of it holds.

## Consequences

- **The deliverable has two renderings of one tree**, and keeping them in step is a standing cost, paid by #620's parity test.
- **ADR-101, ADR-102 and ADR-274 carry a banner *"Partly amended — see ADR-301"***, and ADR-002 and ADR-023 a banner *"Partly reopened — see ADR-301"* beneath the one they carry.
- **ADR-025's banner reads as it did**: rendering the artifact anywhere is someone else's concern, in ADR-101's sense of rendering into a destination (§1). It is not amended.
- **`AGENTS.md`, `CONTEXT.md`, `README.md` and `docs/architecture.md`** each say the run ends at an HTML tree and nothing beside it; they carry the correction at their own altitude in [#606](https://github.com/algernon28/vespera/issues/606)'s change.

## Tests

This record changes no code, and no test holds it. The parity of the two renderings (§4) is held by the test #620 builds with the renderer.

## What this does not decide

- **That Vespera never sends the tree**, holds no credentials, opens no connection and adds no command: [#608](https://github.com/algernon28/vespera/issues/608).
- **Which edition is drawn for, and the storage format each takes**: [#609](https://github.com/algernon28/vespera/issues/609).
- **That the rendering is drawn always, with no setting**, and where it lies: [#612](https://github.com/algernon28/vespera/issues/612).
- **How the copies are carried as files on their cluster's page**: [#617](https://github.com/algernon28/vespera/issues/617).
- **What the root page holds**: [#616](https://github.com/algernon28/vespera/issues/616).
- **Which space the rendering is loaded into, and how**: [#613](https://github.com/algernon28/vespera/issues/613).
- **What the operator's loader is handed and may rely on**: [#618](https://github.com/algernon28/vespera/issues/618) and [#619](https://github.com/algernon28/vespera/issues/619).
- **The renderer itself, and its parity test**: [#620](https://github.com/algernon28/vespera/issues/620).
