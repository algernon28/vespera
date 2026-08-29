# ADR-061 — The profile is YAML, typed Java records, one object per value

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-043 makes the profile a file — authored by a person, mutable, per-corpus, drafted by census with thresholds unset, snapshotted into the ledger per run with provenance — but never says what kind of file. ADR-054 (ticket #11) already named it `profile.yaml`, committing to YAML and settling its location; this decision fills in what ADR-054 left open: the per-value structure and how safely it can be authored and read.

Requirements, from `CONTEXT.md` and ADR-006 ("observe before enforce"): each value carries the value itself, its provenance (human-calibrated / carried-over / auto-derived), and a pointer to the measurement that should inform it. A null threshold must be distinguishable from one deliberately set to zero, and — the ticket's own risk — a null must not be typo-able into a string that silently reads as "set."

## Decision

**YAML**, confirmed. It is the only one of the three candidates (YAML, TOML, JSON) that both supports comments — needed because a human authoring "how this was arrived at" is writing prose, not just a value — and has a native, idiomatic null (`null`, `~`, or blank). JSON is ruled out solely for lacking comments; TOML's null support is weaker and less idiomatic than YAML's.

**The null-vs-typo risk is caught by strict typing on load, not by convention.** Every value's field is typed in the Java record it deserializes into (`Double`, `Integer`, never `Object` or `String`), and the Jackson YAML mapper is configured to fail on unknown properties and fail on type mismatch. A stray `value: "null"` (a quoted string) fails to parse into a `Double` field and throws at load — the format doesn't have to be trusted to get this right, the deserializer does.

**The schema lives entirely as Java records, not a checked-in schema file.** A separate schema artifact (JSON Schema or similar) would be a second thing to keep in sync with the code that actually reads it — exactly the parallel-tooling this project has avoided elsewhere (`schema.sql` + a version check rather than Flyway, ADR-049; a closed Java enum rather than a registry, ADR-057). The record is the schema; strict Jackson deserialization enforces it at load. Each capability module's own profile record is added alongside its own `schema.sql` as that module is built, mirroring `schema_version`'s per-module-additive ownership (ADR-059) — this decision does not need to invent every future stage's threshold names now.

**Every value is one object, not a bare scalar with provenance tracked elsewhere**:

```yaml
below_threshold_score:
  value: null
  provenance: null
  measurement: "content_census.relevance_score_distribution"
```

Value, provenance, and measurement pointer travel together per key. A parallel top-level `provenance:` section keyed the same way as the values would let the two drift apart — add a key to one section, forget the other — the same drift shape ADR-056's reconciliation check exists to catch elsewhere, avoided here by construction instead of by a second check.

**The measurement pointer is free text, never parsed** — the same pattern already used for walk anomaly detail and verdict reason (ADR-053, ADR-057): there for the operator to read and go look at, not a resolvable URI a program follows.

**Census's draft writes a complete skeleton, always.** Every key the current build's profile records define is present, with `value: null` and `provenance: null`, and `measurement` filled in wherever census itself can name the relevant measurement. No key is omitted because census has no opinion on it yet — omission would mean "key absent" and "key present but null" both have to mean something, the same two-states-for-one-concept hazard already named for null-vs-zero.

## Consequences

**`profile` needs its own Jackson YAML reader/writer, separate from Spring Boot's `application.yaml` binding.** `vespera.working-dir` (ADR-054) is Spring configuration; `profile.yaml` is per-corpus domain data authored by a human and re-read across runs — `@ConfigurationProperties` does not fit a file with this lifecycle.

**The skeleton's key set grows only as new capability modules are built.** This slice's `profile` module ships whatever keys `ledger`/`corpus` need (if any); stage 1 through 6b's thresholds appear only when those modules exist, each contributing its own record.

## Amends

None. This supplies the format and structure ADR-043 left as "a file" without saying what kind.
