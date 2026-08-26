# ADR-051 — A file occurrence is identified by its path relative to the corpus root

- **Date**: 2026-08-26
- **Status**: accepted

## Context

ADR-015 fixes the *fields* of occurrence identity — a surrogate key, with path, size and mtime, and a nullable hash — but not their semantics. The corpus lives on NTFS, and on Windows every plausible reading of "the same file" behaves differently. Research against the JDK and Microsoft documentation, confirmed by execution on Windows 11 with OpenJDK 26 ([#2](https://github.com/algernon28/vespera/issues/2)), established three facts that constrain the choice:

- **`Path` cannot be the key.** `Path.equals` folds case with `Character.toUpperCase`, and that table disagrees with NTFS: `ı`/`I`, `µ`/`μ` and `ſ`/`s` compare equal as `Path`s and are distinct files on disk. `Files.isSameFile` cannot arbitrate, because it returns early on `equals` — it returned `true` for two files with different contents.
- **There is no inode.** `fileKey()` is `null` on Windows, so identity has to be path-based whether or not that is desirable, and one physical file reachable by two paths cannot be recognised as one thing at walk time.
- **Filenames are opaque UTF-16.** NTFS normalises nothing and the JDK round-trips bit-exact, including unpaired surrogates — which are valid on NTFS and have no UTF-8 encoding at all.

## Decision

An occurrence's path is stored **relative to the corpus root**, which is recorded once on the walk row as `toRealPath()` returns it for whatever the operator supplied. Entry paths are stored **exactly as directory traversal spells them**, with Windows separators rewritten as `/`, and compared by **exact string equality**. The column is `TEXT` with no length limit, under `UNIQUE(walk_id, path)`.

No case folding, no Unicode normalisation, and no per-file `toRealPath()`: for an entry found by traversal the directory entry already *is* the authoritative on-disk spelling, so canonicalising each one buys nothing and costs a filesystem call per file. The root is the exception because it is the one component a human typed.

A filename that does not survive a UTF-8 round trip yields **no occurrence**. It is recorded as a walk anomaly carrying the lossy rendering, so it stays in the walk's denominator and remains findable, without ever becoming a row whose stored path cannot reopen its file.

**Links.** Hard links are not detected and are treated as ordinary identical copies: two paths become two occurrences, hash identically, and are related as one content identity by stage 1, which runs before extraction. Detection is not merely expensive but unavailable — `fileKey()` is `null` and the Win32 link count is not exposed by `java.nio`. Soft links — symlinks, junctions and volume mount points — are not followed; they are skipped and recorded as walk anomalies.

## Consequences

**Relative paths survive re-siting.** A drive-letter change, a remount, or moving the corpus does not invalidate the ledger; only the walk row's root changes. Reading a file always means joining root and path.

**Byte-exact comparison is the only safe comparison.** Any case folding we add is a collision generator, given that the JDK's table disagrees with the filesystem's. Two entries differing only in case cannot arise within one walk anyway, because NTFS forbids them in one directory.

**Census is Windows-only**, and this is accepted rather than worked around: on Linux the JDK decodes filenames through `sun.jnu.encoding` with U+FFFD replacement, so the round trip is lossy and stored paths could not reopen their files.

**`duplicate-of` must be a blocking verdict** for exactly one occurrence of an identical pair. Because hard links are not detected, the collapse to a single representative occurrence happens entirely through that verdict; if it did not block, publication would emit the same document twice.

**No schema cap on path length.** The filesystem already caps at 255 characters per component and roughly 32,767 for an absolute path, and stored paths are relative and therefore shorter still. One known hole: between about 32,000 and 32,767 characters lies a band Windows permits and the JDK cannot address, where `readAttributes` throws but `Files.exists` silently returns `false`. The walk cannot close it without native calls, so it is a recorded limit on "excludes nothing".

**Two anomaly kinds follow from this decision**: a path that cannot be encoded, and a collision under the unique constraint. Skipped soft links add a third.

## Amends

Sharpens ADR-015 by giving its `path` field defined semantics. Rests on [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md) for the absence of concurrency defenses, but not for the link decisions, which follow from what Windows exposes rather than from who has access.
