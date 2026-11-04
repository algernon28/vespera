# ADR-338 — The collaborators of the deliverable a plugin reads are public, and three members beyond ADR-333's table are public with them

- **Date**: 2026-11-03
- **Status**: accepted on 2026-11-03.
- **Decided**: here. Each choice is one that building ADR-333 §1 and §2 needed, which ADR-333's Decided line leaves to its building; none is a ruling of the operator's, and none changes what the jar writes.
- **Amends**: [ADR-213](0213-each-rule-the-deliverable-is-written-by-has-one-class-and-the-cluster-key-the-citation-pattern-and-the-filename-stem-are-each-written-once.md) §1, whose heading has its collaborators package-private: of those still in `deliverable`, `ClusterPage`, `IndexPage` and `EntryPictures` are public for what a plugin reads of them, and `ManifestCsv` stays package-private (§1). Each still carries one rule, which is what §1 holds.
- **Amends**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md) §6, whose `deliverable.CopyNames` is package-private: it is public, for `keysOf` alone (§1).
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §1's listing of `Plugin`, which gains two constants (§3), and §2's table, whose rows for `ClusterPage.PageBreaks` and `FixedWords` gain a member each (§2, §4). Its rulings stand.
- **Keeps**: ADR-333 §14: no run id moves, since `StageModules` lists neither `deliverable` nor `confluence` for any stage.

## Context

ADR-333 §2's table lists what `deliverable` makes public for a plugin, and its building, step 1 of §13, made it public. That table makes four of ADR-213 §1's collaborators and ADR-280 §6's `CopyNames` public, and ADR-333 names no Amends line for either record. Moving the Confluence drawing into `confluence` also needed three members the table does not list. By `docs/adr/README.md`, a decision is reopened only by a later record, so this one states them.

## Decision

### 1. A collaborator a plugin reads is public, for what the plugin reads

`ClusterPage`, `IndexPage`, `EntryPictures`, `Copies`, `CopyNames`, `Html`, `ProseHtml` and `UnwrittenSentences` are public. Each makes public only the members ADR-333 §2's table lists for it, along with those that §2 to §4 here add. `ManifestCsv` stays package-private, because no plugin reads it. Making a collaborator public adds no copy of its rule: a plugin calls the rule, and the rule keeps one class.

### 2. `ClusterPage.PageBreaks.firstEntryOf` is public

The moved `ConfluencePage` splits a cluster's page at the same entries as the HTML page (ADR-303). It reads from the breaks the entry each page opens with, and does not work the split out again, so the split stays one rule. ADR-333 §2 lists only the count of pages, `pages()`. `PageBreaks`' constructor stays package-private, so only `deliverable` makes breaks.

### 3. `Plugin` carries `DRAWING_STATE_FILE_NAME` and `DRAWN`

`Plugin.DRAWING_STATE_FILE_NAME`, `drawing.txt`, and `Plugin.DRAWN`, `drawn`, are constants of the interface. Two classes use them: `Deliverable`, through `PluginDrawings`, writes the file (ADR-333 §11), and `VesperaCommand`'s publish reads it before it delivers. One spelling is shared by the writer and the reader. A publish asks only whether the file says `drawn`, so the other two words, `drawing` and `failed`, stay private to `PluginDrawings`.

### 4. `FixedWords.rootSentence` holds the root page's sentence, and the Confluence rendering derives from it

`FixedWords.rootSentence(language)` gives the root page's one sentence in each deliverable language (ADR-315 §2, ADR-321). `ConfluencePages.THE_ROOT_PAGES_SENTENCE` is `FixedWords.rootSentence(DeliverableLanguage.ENGLISH)` and no longer a literal of its own. The English sentence is written once, in the table every rendering draws from (ADR-333 §2's row for `FixedWords`).

## Tests

| Method | What it holds |
| --- | --- |
| `DeliverableRulesHaveOneHomeTest.eachCollaboratorIsInItsModuleAndOnlyThoseAPluginReadsArePublic` | each collaborator is a class of `deliverable`, and it is public exactly where a plugin reads it, `ManifestCsv` not (§1) |
| `confluence.TheConfluenceRenderingTest.aSplitClustersCitationNamesThePageItsEntryIsOn` | a cluster split by the ceiling: a citation names the Confluence page its entry is on, which `ConfluencePage` finds from the HTML page's breaks through `firstEntryOf`, as it finds each page's first and last entry (§2) |
| `confluence.TheConfluenceRenderingIsTheSameWrittenAPartitionAtATimeTest` | after a drawing that finished, `drawing.txt` says `drawn`, the value of `Plugin.DRAWN` (§3) |
| `pipeline.PublishRefusesARenderingWhoseDrawingDidNotFinishTest` | the publish delivers a rendering whose `drawing.txt` says `drawn`, or which has no `drawing.txt`, and delivers none that says `drawing` or `failed` (§3) |
| `confluence.TheConfluenceRenderingTest.theRootPageLinksEveryPageAndCarriesDocumentsCsv`, `confluence.TheConfluenceRenderingIsDrawnInItalianTest.theConfluenceRootAndPartitionPagesAreInItalian` | the root page's sentence in English, read from `ConfluencePages.THE_ROOT_PAGES_SENTENCE`, and in Italian, as it is on record (§4) |

## What this does not decide

- **Whether the seam is stable** toward a plugin outside the jar. ADR-333 leaves that undecided, and so does this record.
