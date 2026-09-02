# ADR-068 — `broken` is a cross-format floor plus per-format structural checks, no new dependency

- **Date**: 2026-09-02
- **Status**: accepted
- **Amends**: none

## Context

Stage 1's `broken` verdict exists to catch mechanically-corrupt occurrences before extraction (stage 2, Docling) pays to attempt them. ADR-017's ordering principle — cheapest filter first — governs what "cheap enough" means here: any check heavier than what extraction itself would do defeats the point of running the filter first at all.

The corpus carries `.txt`, `.docx`, `.pdf` and images (`AGENTS.md`). Each format fails mechanically in a different, format-specific way — a truncated zip is not the same failure as a malformed PDF trailer — so a single cross-format probe cannot catch what matters; per-format logic is the feature this ticket specifies, not scope creep.

Three tool options were weighed for `.docx` specifically (a zip container holding OOXML parts):

- **`java.util.zip.ZipFile`** (JDK): opens the zip and validates its central directory without decompressing any entry. Catches truncation and container corruption; blind to a valid zip holding a malformed `document.xml`.
- **Apache POI** (`OPCPackage.open`, new dependency): validates OOXML package relationships on top of the zip check, catching the gap above — at the cost of a new pom entry (ADR-046: a dependency wants a decision behind it) and real parse work approaching what stage 2 already does.
- **Docling**: ruled out entirely, not merely as the more expensive option. Docling runs out-of-process (ADR-010, ADR-011), so invoking it at stage 1 is not a cheaper check — it *is* extraction. Using it here either pays extraction's cost twice (once to "check," once for real at stage 2) or collapses the two stages into one, which contradicts the cascade's ordering principle outright.

The choice between `ZipFile` and POI came down to whether `broken` should absorb the "valid container, corrupt content" case or leave it to stage 2's `extraction-failed`, which already exists for exactly that failure shape.

## Decision

**`broken` is a cross-format floor plus per-format structural checks, entirely within the JDK — no new dependency.**

- **Cross-format floor**: an occurrence that cannot be opened (`IOException`) or has `size_bytes == 0` (already known from census) is `broken`, regardless of format.
- **`.docx`**: `java.util.zip.ZipFile` opens the file and validates the central directory. A `ZipException`/`IOException` here is `broken`. A valid zip with malformed content inside (e.g. corrupt `document.xml`) is **not** caught at this stage — that failure surfaces as stage 2's `extraction-failed`, which already owns it.
- **`.pdf`**: presence of the `%PDF-` header and an `%%EOF` trailer. Catches truncation cheaply; a PDF with intact headers but malformed internal structure is left to stage 2, same reasoning as `.docx`.
- **Images**: a magic-byte/signature check (or `javax.imageio.ImageIO.getImageReaders()` against the header), JDK-only.
- **`.txt`**: no check beyond the cross-format floor — any byte sequence is valid text.

**Apache POI is explicitly rejected for this ticket.** `OPCPackage.open` would catch more (OOXML relationship corruption), but that gap is stage 2's `extraction-failed` territory, and taking it on here would mean stage 1 doing real parse work and carrying a new dependency for a case that already has a home.

**Docling is not an option for `broken` detection at all** — it is stage 2's extraction engine, not a cheaper alternative; running it at stage 1 deletes the stage boundary rather than filtering ahead of it.

**Ordering: `broken` runs before hashing.** An occurrence verdicted `broken` never enters ADR-067's size-then-hash grouping — this is the same boundary ADR-067 already fixed from the hashing side, restated here from `broken`'s side now that what `broken` means is precise.

## Consequences

**No pom change.** `ZipFile`, header/trailer byte checks, and `ImageIO` are all JDK-standard, keeping ADR-046 clean for this ticket.

**A known, accepted gap**: `.docx` and `.pdf` files with valid containers but corrupt internal content pass stage 1 and are only caught at stage 2 as `extraction-failed`. This is not a hole to close later — it is where that failure category already belongs, and closing it here would duplicate stage 2's job one stage early.

**Per-format check implementations are an implementation detail** for the hand-off spec ([issue #32](https://github.com/algernon28/vespera/issues/32)); this ADR fixes which tool and which tier per format, not the exact Java call sites.
