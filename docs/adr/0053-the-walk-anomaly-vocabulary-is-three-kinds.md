# ADR-053 — The walk anomaly vocabulary is three kinds

- **Date**: 2026-08-28
- **Status**: accepted

## Context

`CONTEXT.md` defines a walk anomaly as "an entry a walk encountered and did not record as a file occurrence, carrying the reason", and insists the kinds are observations rather than policy — but never lists them. `Walk.java` reflected that: it emitted a free-text reason and said so in the javadoc on `Observer.anomaly`.

The list cannot stay open. The anomaly kinds are half of the denominator that makes census's "excludes nothing" a claim anyone can check rather than assert ([#6](https://github.com/algernon28/vespera/issues/6)), and a check has to enumerate the categories it is checking.

ADR-051 already handed over three findings: an unencodable filename becomes an anomaly rather than an occurrence, soft links are skipped and recorded, and a path collision under `UNIQUE(walk_id, path)` would be a new kind. ADR-050 removed two candidates from the design's centre — a file vanishing mid-walk and a file locked by another process — by giving the pipeline exclusive access to the corpus.

Three facts were measured on this machine (Windows 11, NTFS, OpenJDK 26) rather than argued:

| Case | Observed |
| --- | --- |
| Directory denied by ACL | `AccessDeniedException` arrives at `visitFileFailed`, not `preVisitDirectory` — the entry is seen, and not descended into |
| **File** denied by ACL | The walk records an ordinary occurrence with real `size` and `mtime`. Windows directory enumeration returns a child's attributes without consulting that child's ACL, so the walk never learns it cannot read the file. `Files.isReadable` returns `false`; opening it throws |
| Path past ~32,000 characters | A bare `java.io.IOException`, message `Cannot access file with path exceeding 32000 characters` — not a `FileSystemException`, so no `getReason()`, and only the message distinguishes it |

## Decision

The walk anomaly vocabulary is a **closed enum of three kinds, owned by `corpus`**, each row carrying the kind plus a nullable free-text `detail`.

| Kind | Raised when | `detail` carries |
| --- | --- | --- |
| `unprocessable` | the walk saw the entry and could not read or record it — permission denied, I/O error, path too long, not a regular file, a directory listing that did not complete | the exception class and message |
| `soft-link-not-followed` | a symlink, junction or volume mount point, skipped per ADR-051 | the target, where readable |
| `unencodable-path` | the filename has no UTF-8 encoding, per ADR-051 | the lossy rendering, e.g. `orphan-?.txt` |

**`detail` is never parsed and never required for a decision.** It exists for the operator reading a list of several thousand anomalies and asking which of them are the same problem. Code that branches on `detail` has invented a kind that was not declared.

**Owned by `corpus`, not `ledger`.** ADR-042 put the *verdict* vocabulary in `ledger` because a verdict is a ledger concept and every stage writes one. An anomaly kind is a fact about filesystem traversal, only `corpus` ever produces one, and putting it in `ledger` would mean `ledger` knows what a reparse point is.

**A path collision is not a kind.** ADR-051 established that it effectively cannot occur: NTFS forbids two case-only siblings in one directory, and a filename cannot contain `/`. A collision would mean an assumption in ADR-051 is false, so the unique constraint throws rather than the walk absorbing it into an anomaly count nobody reads.

**Coverage stays where it is.** Whether a walk completed is carried by `Outcome.finished`, not by an anomaly. A truncated directory listing raises `unprocessable` against that directory, which is an entry the walk saw.

## Consequences

**The vocabulary is blunt on purpose.** `unprocessable` merges permission denied, I/O error, path too long and special files into one kind. Under ADR-050 the permission cases are theoretical, and the measured behaviour makes finer distinctions unavailable anyway: path-too-long can only be recognised by string-matching a JDK message, which is a kind that would silently stop being populated. A distinction the operator cannot act on is not worth carrying, and the entry lands in the denominator either way — so "excludes nothing" holds regardless.

**Symlinks, junctions and volume mount points share one kind, and that is a known loss.** A skipped volume mount point can mean an entire second disk was never walked, which is a different fact from a skipped symlink. `java.nio` does not expose the reparse tag, so telling them apart needs a native call or `fsutil`. Recorded as a limitation on [#6](https://github.com/algernon28/vespera/issues/6) rather than paid for here.

**Census does not detect a file it can stat but cannot open.** Measured above: it becomes an ordinary occurrence and the failure surfaces at stage 2, extraction — the most expensive stage in the cascade, and the one ADR-017's ordering exists to protect. Accepted on the definition rather than the cost: the file *was* recorded as an occurrence, so by `CONTEXT.md` it is categorically not an anomaly. It is an occurrence heading for an `extraction-failed` verdict. Making census guess at readability would cost an `AccessCheck` per entry across hundreds of thousands of files and would put policy in the one stage that exists to be pure observation. A second hole in "excludes nothing", for the record on [#6](https://github.com/algernon28/vespera/issues/6).

**`Observer.anomaly(String, String)` must become kind-typed.** The signature in `Walk.java` takes a free-text reason and its javadoc points at [#3](https://github.com/algernon28/vespera/issues/3) as the open question. The change belongs to the hand-off spec ([#18](https://github.com/algernon28/vespera/issues/18)), not here — this is a planning map.

## Amends

Narrows **ADR-050**, which said a file vanishing mid-walk and a file locked by another process "remain recordable walk anomaly kinds". They are not separate kinds; both land in `unprocessable` if they ever occur. ADR-050's substance is unchanged — they were already described there as near-impossible and not design-shaping.

Neither **ADR-017** nor **ADR-048** is amended. `docs/architecture.md` never mentions walk anomalies, so this is new ground rather than a change to either.
