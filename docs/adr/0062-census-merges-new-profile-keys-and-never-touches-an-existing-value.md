# ADR-062 — Census merges new profile keys and never touches an existing value

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-043 says the profile file is input, that history never overrides it, and that an anti-regeneration rule protects human edits — but not the mechanism. Census both drafts the profile on a corpus's first run and re-runs on every subsequent invocation (ADR-047's "campaign"), so the mechanism has to answer what a second census does when `profile.yaml` already exists, without ever silently reverting a calibrated value to null — the one failure `CONTEXT.md`'s framing exists to prevent.

ADR-061 (ticket #12) already fixed the profile's shape: closed, code-defined Java records deserialized strictly, one object per value carrying `value`, `provenance`, and `measurement` together. That fact does most of the work here: a key a human deletes from the YAML and a key that is merely `null` are behaviourally identical (Jackson deserializes a missing field as null, and `CONTEXT.md`'s Gate treats "unset" as "the run ends there" regardless of why), so no mechanism is needed to distinguish "genuinely new key" from "a human deleted this one."

## Decision

**Census merges in place; it never regenerates the file from scratch, writes a sibling draft, or refuses to write.** On finding an existing `profile.yaml`, it reads the file first, adds any key the current code's schema defines that the file does not yet have (as `value: null`, `provenance: null`), and leaves every existing key exactly as found. A wholesale regenerate is exactly the mechanism that could silently revert a calibrated value; an outright refusal to write means an operator who upgrades vespera never gets a newly-added threshold key without manual file surgery. Merge-in-place is the only option where "new keys arrive automatically" and "an existing decision is never touched" aren't in tension, because they are two rules applied to two disjoint sets of keys — absent keys get added, present keys are never rewritten.

**The merge rule is asymmetric per field within a value object.** `value` and `provenance` are written by census only at the moment a key is created; thereafter they are census's to read, never to write — only a human, or a future run snapshot's carry-over, changes them. `measurement` — census's own free-text pointer to where the informing data lives (ADR-061) — is refreshed by census on every run, since it is census's metadata about itself, not a judgement being protected. This is what "a key the human edited that census now measures differently" resolves to: census updates where it points the human to look, without touching what the human decided.

**A deleted key needs no special handling.** Given the schema is closed and code-defined, a key absent from the file and a key present with `value: null` are the same state to the pipeline — both gate identically (ADR-047). The merge rule already reproduces the right behaviour as a side effect: a deleted key simply reappears as `null` on the next census run, exactly as gated as it was the moment before deletion. Building a change-log or per-key origin stamp to preserve a "was this deleted on purpose" distinction would protect a distinction with no behavioural consequence.

**No dedicated conflict-signal mechanism.** A newly merged-in null key is an ordinary unset gate; the operator finds out the way they find out about any other missing gate — the pipeline reaches the stage that needs it and terminates there. A separate "profile was updated: N keys added" feature would duplicate a job the gate mechanism already does. Census logging the keys it added to its own invocation output, for a human doing the campaign's between-runs editing pass, is enough.

## Consequences

**Census's write path needs read-modify-write, not write-only.** Drafting a brand-new profile and merging into an existing one are the same code path with an empty starting file, not two separate cases to implement.

**No profile-diffing or versioning feature is built.** An operator wanting to know what census added on a given run reads that run's own log output, not a stored history of profile changes.

## Amends

None. This supplies the mechanism ADR-043 stated the rule for but did not specify.
