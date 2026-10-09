# ADR-213 — Each rule the deliverable is written by has one class, and the cluster key, the citation pattern and the filename stem are each written once

- **Date**: 2026-10-09
- **Status**: accepted
- **Keeps**: [ADR-134](0134-a-line-break-in-a-value-is-folded-and-three-escaping-rules-stand-because-there-are-three-surroundings.md) as written, its reopen trigger included (§2); [ADR-137](0137-a-destinations-ampersand-is-percent-encoded-and-the-destination-is-a-fifth-surrounding.md) §4 (five surroundings, and what a surrounding answers to decides the form of its rule); [ADR-138](0138-a-bracket-is-escaped-in-every-surrounding-a-value-is-read-in-and-the-link-text-stops-being-a-rule-of-its-own.md) §5 and its table, whose Rule column names the methods as they stood when it was written (§2); [ADR-133](0133-the-exemplars-one-call-sent-are-recorded-and-a-cluster-file-numbers-its-membership-from-that-record.md) (membership numbering); [ADR-149](0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md) §3 and §5 (a picture's file is named from its own bytes; alt text is folded, then escaped by the membership entry's rule); [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md) (`ClusterSlot` as the key a caller outside `synthesis` names a cluster by); [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`synthesis` reads neither `profile` nor `pipeline`).
- **Rests on**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md) (a second *copy* of a rule is the defect, a second *rule* is not); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids a change to a module moves); the code at `4b99a03`, read and measured for this record; and the tests that ship with it, written before anything in `src/main` changed.
- **Settles** [#351](https://github.com/algernon28/vespera/issues/351), Wave 5 of the architecture simplification.

## Context

**Measured at `4b99a03`.** `synthesis/Deliverable.java` is 1,271 lines, 525 of them comment, and the largest class in the tree. It carries nine seams: the orchestration, the index page, the cluster page, membership numbering (ADR-133), citation links (ADR-109), pictures (ADR-149, ADR-150), link destinations (ADR-135, ADR-137), the CSV manifest (ADR-104), and the Markdown surroundings (ADR-134, ADR-136, ADR-138, ADR-148). Every rendering defect since #236 landed in this one file, and each seam's rule can be tested only through the whole tree.

Four things are written more than once in `synthesis`:

| What | Where, at `4b99a03` |
| --- | --- |
| The citation pattern `\[(\d+)\]` | `ClusterSynthesis.java:158` (the range check) and `Deliverable.java:110` (the link rewrite) |
| A path's filename stem | `ClusterLabel.filenameStemOf` (`:62`, the label's second tier) and `Deliverable.stemOf` (`:1128`, the partition directory and the picture directory) |
| A record of exactly (winning seed, cluster ordinal) | the public `ClusterSlot`, and two private `ClusterKey` records, in `Deliverable` (`:1265`) and in `SynthesisDocs` (`:133`). A third, in `pipeline`'s `GenerationTasklet` (`:768`), is outside `synthesis` |
| A picture's SHA-256 | taken in the first pass (`Deliverable.java:294`), then again in the second for the furniture check (`:882`) and again for the file name (`:854`): three digests for a picture that is shown, two for one left out or past the budget |

The issue's note that ADR-151 made 6b hash each survivor's file once, in `GenerationTasklet.hashOf`, does not bear on the last row. That hash was of a survivor's file, never of a picture, and `hashOf` no longer exists: ADR-206 replaced it with the key stage 2 records.

No test held ADR-134's reopen trigger. It was a sentence, checked by whoever read it.

## Decision

### 1. Six package-private collaborators, each carrying one rule, and `Deliverable` orchestrates

| Collaborator | The rule it carries | Records |
| --- | --- | --- |
| `MarkdownSurroundings` | an enum with one constant per Markdown text surrounding, each saying whether it folds and which characters it escapes, and the one fold, `onOneLine` | ADR-134, ADR-136, ADR-138, ADR-148 |
| `ArchiveLink` | a membership entry's destination, relative to its page or absent, percent-encoded | ADR-135, ADR-137 |
| `ManifestCsv` | `documents.csv`, and RFC 4180 quoting | ADR-104, ADR-112, ADR-136 §5 |
| `ClusterPage` | a cluster's page: heading, writing or the sentence in its place, the subset sentence, citation links, and the membership list numbered from what the call sent | ADR-109, ADR-133, ADR-174 |
| `IndexPage` | `index.md` | ADR-103, ADR-112, ADR-122 |
| `EntryPictures` | which pictures are furniture, and the pictures shown under one membership entry | ADR-149, ADR-150 |

`Deliverable` keeps its public surface, its four `writeTo` overloads and every constant a test reads (`DIRECTORY_NAME`, `INDEX_FILE_NAME`, `MANIFEST_FILE_NAME`, `NOTHING_WAS_WRITTEN_OVER_IT`, `THE_CLUSTER_NO_LONGER_HOLDS_IT`, `PICTURES_PER_DOCUMENT`), so `DeliverableTest` passes unedited. Each rule is moved out of `Deliverable`, not copied.

**The pictures collaborator is `EntryPictures`, not the plan's `DeliverablePictures`.** `DeliverablePicturesTest` already exists and holds the pictures through `Deliverable.writeTo`, end to end, in twenty cases. A class named `DeliverablePictures` would make that file read as its unit test. The unit test is `EntryPicturesTest`, and `DeliverablePicturesTest` stays where it is, unedited.

### 2. The table keeps ADR-134, ADR-137 §4 and ADR-138 §5 as written

`MarkdownSurroundings` is ADR-138 §5's table, its three Markdown rows made code:

| Constant | Folds | Escapes, in order |
| --- | --- | --- |
| `TABLE_CELL` | yes | `\` `<` `&` `[` `]` `\|` `` ` `` |
| `ATX_HEADING` | yes | `\` `<` `&` `[` `]` `` ` `` |
| `MEMBERSHIP_ENTRY` | no | `\` `<` `&` `[` `]` `` ` `` |

The two other rows of that table are `ArchiveLink` (the destination) and `ManifestCsv` (the CSV). They stay out of the enum because ADR-137 §4 says what a surrounding answers to decides the form of its rule: a resolver gets percent-encoding and a parser gets a doubled quote, not a backslash.

**`ATX_HEADING` and `MEMBERSHIP_ENTRY` stay two constants**, although they escape the same characters. ADR-138 §5 records that convergence as a result, not a merger, and they still differ: one folds and the other does not.

**A picture's alt text is not a fourth constant.** ADR-149 §5 says it is the membership entry's surrounding, folded first, so `EntryPictures` writes `MEMBERSHIP_ENTRY.escape(MarkdownSurroundings.onOneLine(caption))`. That is byte for byte what `Deliverable` writes today.

**The index's link text is `TABLE_CELL`**, as ADR-138 left it when it stopped being a rule of its own.

**ADR-134's reopen trigger stands, and now has a test.** It reads: *a second class under `synthesis` grows a Markdown escaping method.* From this record, the one class is `MarkdownSurroundings`. `Deliverable` keeps no escaping rule, so the move trips nothing. `ArchiveLink`'s percent-encoding and `ManifestCsv`'s quoting are each one rule for one grammar, and neither is a Markdown escape (ADR-137 §4). ADR-130's premise, two copies of one rule, holds nowhere.

ADR-138 §5's Rule column, and ADR-134's sentences naming `Deliverable` as the class the rules sit in, describe the code as it stood when they were written. Following ADR-138 §3's own precedent, they are not amended. This section is where a reader tracing them forward finds the new homes.

### 3. The one cluster key is `ClusterSlot`, by its present name and in its present home

`ClusterSlot(OccurrenceId winningSeed, int clusterOrdinal)` is public, and `pipeline` imports it (`ArrangementTasklet`, `GenerationTasklet`). Renaming it would edit `pipeline`, and under ADR-058 that moves the run ids of stages 3 to 6b. This wave moves 6a and 6b only.

**It absorbs the two private copies in `synthesis`**, `Deliverable.ClusterKey` and `SynthesisDocs.ClusterKey`, and every place in `synthesis` that pairs a seed with an ordinal by hand. Beside its existing `of(RecordedCluster)` it gains `of(RecordedSynthesisDoc)`, `of(RecordedClusterFault)` and `of(ListedSurvivor)`, so every wrapper's pairing is read in the key's own class.

**It absorbs none of the three `Recorded*` types.** `RecordedCluster` is not "(seed, ordinal) plus X": it is an `ArrangedCluster` and a `ClusterLabel`. `DeliverableTest`, which this wave keeps unedited, constructs `RecordedCluster` and `RecordedSynthesisDoc` and passes lists of them to `writeTo`. `GenerationTasklet` reads `winningSeed()` and `clusterOrdinal()` off `RecordedClusterFault` and `RecordedSynthesisDoc` (`:543`, `:547`). Recomposing any of the three around `ClusterSlot` would edit `pipeline` and that test, which is out of this wave.

**Left standing, and named here so it is not mistaken for an oversight:** `GenerationTasklet.ClusterKey`, a private record of the same shape in `pipeline`. Removing it is a `pipeline` change and moves stages 3 to 6b.

### 4. The citation pattern lives in `Citation`, and the filename stem in `FilenameStem`

**`Citation.AS_WRITTEN`**, a package-private `Pattern` in a package-private class, is a citation as the model writes it (ADR-109). Two rules read it: the range check in `ClusterSynthesis` and the link rewrite in `ClusterPage`. Neither owns the other, so the pattern is put in neither of them. The class takes the vocabulary's own term (CONTEXT.md, **Citation**).

**`FilenameStem.of(String path)`** is a path's last `/`-separated segment without its last extension. A name that is only an extension yields an empty stem, which ADR-096's reasoning and `ClusterLabel`'s third tier depend on. `ClusterLabel` (the label's second tier) and the deliverable (the partition directory and the picture directory) both call it. It takes a `String` so it serves an `OccurrencePath`'s value and a page's file name alike.

### 5. One SHA-256 per picture per pass

In the second pass, the furniture check and the file name share one digest. The first pass's digests are not carried into the second.

ADR-149 asks the source about each survivor twice and holds no pixels between the passes. The second answer is a new answer, and ADR-149 §3 names a picture's file from its own bytes, so the bytes digested must be the bytes written. Carrying the first pass's digests by position would name a file from another answer's bytes whenever a source answered differently. What it would save is one SHA-256 of a picture of a few kilobytes.

No test holds this. Nothing the tree contains shows how many digests were taken.

### 6. What does not move

- **The deliverable is byte-identical**: every page, the index, the manifest, and every picture's path and bytes. `DeliverableTest`, `ClusterFileTest` and `DeliverablePicturesTest` pass unedited. The new unit tests take their expected values from what `Deliverable` wrote at `4b99a03`.
- **No cache key, schema, or persisted name moves.**
- **Run ids move for 6a and 6b only**, because the change is confined to `synthesis`. Stage 6a's moves because `ClusterLabel` calls `FilenameStem`, and both stages name `synthesis` in their identity anyway.
- **`synthesis` still reads neither `profile` nor `pipeline`** (ADR-110).

### 7. The measured size

Before, at `4b99a03`: `Deliverable.java` 1,271 lines, and `synthesis` 42 main files.

After, measured with `wc -l` over `src/main/java/io/algernon/vespera/synthesis` once the build was green: `Deliverable.java` 270 lines (from 1,271), and `synthesis` 50 main files. The collaborators:

| File | Lines |
| --- | --- |
| `EntryPictures.java` | 323 |
| `ClusterPage.java` | 237 |
| `IndexPage.java` | 186 |
| `ArchiveLink.java` | 99 |
| `MarkdownSurroundings.java` | 91 |
| `ManifestCsv.java` | 88 |
| `FilenameStem.java` | 27 |
| `Citation.java` | 17 |

`ClusterSlot.java` grew to 32 lines with its three overloads. All of `synthesis` main went from 3,815 lines to 3,875, a net of 60 more: the split is close to neutral in lines, as the plan's estimate said, and the largest class is gone, `EntryPictures` at 323 lines being the largest now.

## Tests

| Class | What it holds |
| --- | --- |
| `synthesis.MarkdownSurroundingsTest` | the three constants and no fourth; each constant's fold and escape set, the backslash first; one hostile value through each constant; the backslash beside a pipe in a cell (ADR-134 §4); ASCII-only folding (ADR-134 §3); the heading and the membership entry kept apart |
| `synthesis.ArchiveLinkTest` | relative destinations and their percent-encoding (`%20`, `%26`, `%28`, `%29`, `%5B`, `%5D`, `%3C`, `%60`, UTF-8); no destination for a relative page, a relative root, or a root that is not a path |
| `synthesis.ManifestCsvTest` | the whole file for a two-cluster arrangement; RFC 4180 quoting and what it leaves alone; a survivor whose cluster the arrangement does not carry stops the writer |
| `synthesis.ClusterPageTest` | numbering from what the call sent (ADR-133), score order where nothing was sent, a sent document the cluster no longer holds; citation links; a written page and an unwritten page, whole; one progress tick per entry that carries a document |
| `synthesis.IndexPageTest` | the whole index for a two-cluster partition; padding of the page's name at ten clusters |
| `synthesis.EntryPicturesTest` | the first pass asks each survivor once and reports it; recurring and furniture-layer pictures are furniture and a unique one is not; a picture's file and line; furniture left out; the caption as alt text; the budget, singular and plural; an unwritten media type counted; the indent under a two-digit entry |
| `synthesis.CitationTest` | the pattern, what it matches and what it leaves alone |
| `synthesis.FilenameStemTest` | the stem, and that it is the one `ClusterLabel`'s second tier gives |
| `synthesis.ClusterSlotTest` | the four `of` overloads agree for one cluster and tell two apart |
| `DeliverableRulesHaveOneHomeTest` | ADR-134's trigger; the destination's and the CSV's escapes in their one class each; one citation pattern; one filename stem; one record of the cluster key's shape |

## What the commit that builds `src/main` owes

The package-private signatures these tests compile against, all in `io.algernon.vespera.synthesis`:

```java
enum MarkdownSurroundings {
    TABLE_CELL, ATX_HEADING, MEMBERSHIP_ENTRY;
    String escape(String value);        // fold where folds(), then escape escaped() in order
    boolean folds();
    String escaped();                   // the characters escaped, backslash first
    static String onOneLine(String value);
}

final class ArchiveLink {
    static Optional<String> from(Path pageDirectory, String corpusRoot, String relativePath);
}

final class ManifestCsv {
    static String contents(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors);
    static String quoted(String field);
}

final class ClusterPage {
    static List<Optional<ListedSurvivor>> numbered(SynthesisDoc doc, List<ListedSurvivor> members);
    static String withCitationLinks(String prose);
    static void write(Path file, RecordedCluster recorded, SynthesisDoc doc, Unwritten why,
            List<ListedSurvivor> members, String corpusRoot, EntryPictures pictures,
            DeliverableProgress progress) throws IOException;
}

final class IndexPage {
    static String contents(DeliverableProvenance provenance, List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written, List<ListedSurvivor> survivors);
}

final class EntryPictures {
    static EntryPictures among(List<ListedSurvivor> survivors, SurvivorPictures source,
            DeliverableProgress progress);
    Set<String> furniture();
    void appendUnder(StringBuilder page, ListedSurvivor member, int ordinal, Path pageDirectory,
            String pictureDirectoryName) throws IOException;
}

final class Citation { static final Pattern AS_WRITTEN; }

final class FilenameStem { static String of(String path); }

// public, existing, gains three overloads
public record ClusterSlot(OccurrenceId winningSeed, int clusterOrdinal) {
    public static ClusterSlot of(RecordedSynthesisDoc written);
    public static ClusterSlot of(RecordedClusterFault fault);
    public static ClusterSlot of(ListedSurvivor survivor);
}
```

It also owes §7's figures, and the removal of `Deliverable.ClusterKey` and `SynthesisDocs.ClusterKey`. No `pipeline` source moves.

## What this does not decide

- Whether the `Recorded*` wrappers are recomposed around `ClusterSlot`, and whether `pipeline`'s own `ClusterKey` goes. Both edit `pipeline`, so both move stages 3 to 6b.
- The duplicated SHA-256 helpers across modules (plan §6).
- Anything the deliverable says or looks like.
