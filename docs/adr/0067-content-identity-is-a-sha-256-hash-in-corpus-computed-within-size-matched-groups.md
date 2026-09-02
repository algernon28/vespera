# ADR-067 — Content identity is a SHA-256 hash in `corpus`, computed within size-matched groups

- **Date**: 2026-09-02
- **Status**: accepted
- **Amends**: none

## Context

Stage 1's `duplicate-of` verdict needs a way to group file occurrences that share one content identity — a distinct byte sequence (`CONTEXT.md`). `docs/architecture.md` §1.3 already assigns `corpus` the module scope "byte-level facts," which settles that this data does not need a new module. It does not settle three narrower questions: which module's *table* actually holds it, what algorithm computes it, and whether every survivor of census pays that cost.

The first question has a live discrepancy to resolve. `schema.sql`'s comment on the `verdict` table reads: "the occurrence reference a duplicate-of verdict needs... belong[s] to similarity's and embedding's own tables (ADR-041)" — naming `similarity`. But `similarity` is scoped, per the architecture table, to stage 4's lexical redundancy (shingles, MinHash/LSH over near-duplicates) — a different comparison entirely from stage 1's byte-exact match, and a module that does not yet exist in `src/main`. The comment predates the module split being pinned down this precisely and is superseded by this decision.

ADR-041 already set the generic shape this decision applies: `ledger`'s `verdict` row stays generic (kind + free-text reason); a capability module's own table carries the richer payload, joined back by `occurrence_id` and `run_id`.

## Decision

**The content-identity table is owned by `corpus`, not `similarity`.** Byte-exact hashing is a byte-level fact, `corpus`'s stated scope; `similarity` names and exists for stage 4's near-duplicate detection and has nothing to do with exact-match comparison. Read `schema.sql`'s existing `verdict`-table comment naming `similarity` for `duplicate-of` as superseded by this ADR.

**The digest is SHA-256, unconditionally, with no faster-hash fallback.** At this corpus's scale (hundreds of gigabytes, not billions of files) collision probability is not a practical concern, `MessageDigest` ships in the JDK so this adds no new dependency (ADR-046 makes that weight real), and stage 1 is I/O-bound reading the file regardless of digest choice — a faster non-cryptographic hash would buy speed nothing here needs at the cost of a same-hash-different-bytes edge case nobody wants to carry.

**Hashing is scoped to survivors of `broken`, not every occurrence census produced.** An occurrence already verdicted `broken` is out of stage 1's own survivor set; grouping it into a duplicate set serves nothing downstream. The exact ordering of `broken` detection versus hashing inside stage 1's run is [issue #30](https://github.com/algernon28/vespera/issues/30)'s to settle — this decision only fixes that hashing's *input* is `broken`'s survivors.

**Hashing groups by `size_bytes` first, and only hashes within a same-size group.** `size_bytes` already exists on `file_occurrence` at no extra cost; two files of different sizes can never be byte-identical, so grouping by size before hashing skips every occurrence with no same-size peer at all, at zero accuracy cost. This is the concrete algorithm, not merely a lazy-vs-eager preference: (1) group survivors by `size_bytes`; (2) within any group of size ≥ 2, compute the SHA-256 of each member; (3) occurrences sharing a hash share content identity.

## Consequences

**A new `corpus`-owned table, shape left to the hand-off spec.** This decision fixes ownership, algorithm and input scope; the exact column layout (e.g. hash stored per occurrence vs. a content-identity grouping row) is an implementation detail for [issue #32](https://github.com/algernon28/vespera/issues/32).

**`schema.sql`'s `verdict`-table comment needs a follow-up edit** pointing at `corpus` instead of `similarity` for `duplicate-of`'s payload — deferred to the hand-off spec so this ADR does not itself carry code changes.

**Nothing here decides which occurrence within a content-identity group becomes the representative, or how `superseded-by` is assigned.** That is [issue #31](https://github.com/algernon28/vespera/issues/31)'s question; this ADR only fixes how the groups themselves are formed.
