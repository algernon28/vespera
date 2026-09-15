# ADR-118 — `Profile`'s canonical constructor is its only public one, and test convenience lives in the tests

- **Date**: 2026-09-14
- **Status**: accepted
- **Rests on**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) (the record *is* the schema, and strict deserialisation enforces it), [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) (a key the file predates arrives unset, and `value` and `provenance` are a person's to write), [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) (where test support may live, and why).
- **Settles** [#197](https://github.com/algernon28/vespera/issues/197).

## Context

`Profile` is a seven-component record (ADR-061), and its canonical constructor is where ADR-062's merge lives: a null component becomes `ProfileValue.unset()`, so a key the file predates loads as the same gated state as a key nobody has answered. That constructor is the whole of what production needs — `ProfileStore` deserialises into it, `skeleton()` calls it, and the three `with*Measurement` methods re-call it with one pointer moved.

Beside it stood **five more public constructors**, arities two to six, each added with a key. (The ticket says six, counting the canonical one; there were six public constructors in total and five of them overloads.) #58 kept the two-key one so that a third key "mints no compile break at every existing caller", and every ticket since — #107, #110, #175, #179 — kept the previous arity for the same stated reason. Every parameter is a `ProfileValue`, so at a call site the arity is the only thing the compiler sees:

```java
new Profile(set("/corpus/exemplars"), null, set("0.4"), set("embeddinggemma"), null)
```

Nothing there says which keys those are. Two callers wanting different subsets of the same arity are indistinguishable; a key inserted in the middle re-points every call of that arity without a compile error; and four of the five carried a test in `ProfileTest` whose only job was to pin that overload's own existence.

**All forty-three call sites of the five overloads are in `src/test`, across twenty-four files.** `Profile.java` is their only caller in `src/main`, and only to delegate to the canonical one. So the overloads are a production API shaped entirely by what tests found convenient — and the convenience was never great: `NextActionTest` had already grown six private helpers, `nothingSet()` and `everythingSet()` among them, to give the positional calls names.

The ticket also named a Jackson creator-selection hazard. It is smaller than stated. Since databind 2.15 a record's canonical constructor is its implicit primary creator, and other constructors are considered only when one is annotated `@JsonCreator` — a rule the 2.18 creator-detection rewrite hardened and the Jackson 3 this project uses inherits. `ProfileStoreTest` round-trips the seven-key record past the five extra constructors today and passes, by rule rather than by luck. What was true is that nothing here pinned the rule.

## Decision

**A production type's constructor surface is shaped by what production calls, never by what tests find convenient. Test convenience lives in `src/test`.**

Applied to `Profile`:

- **The canonical constructor is the only public constructor.** The five overloads are deleted. Adding the eighth key — [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s `num_ctx`, overridable in the profile, already implies one — adds a component to the record and nothing beside it.
- **`ProfileValue.unset()` becomes public.** It was package-private, and tests outside the `profile` package had written `new ProfileValue(null, null, null)` to get around that — an unset key spelled as three nulls rather than as the thing it is. `Profile.skeleton()` stays package-private: the fixture below starts from it, and the fixture is in that package.
- **A `ProfileFixture` in `src/test` is how a test writes a profile.** A builder over the skeleton — `aProfile().seedFolder("/corpus/exemplars").embeddingModel("embeddinggemma").build()` — with one method per key. A `String` argument sets the value and stamps the provenance `"set by this test"`, because ADR-062 has the two fields written together and a fixture should produce a profile a person could have written; a `ProfileValue` argument is taken whole, for the tests that assert on provenance or on a measurement; `seedFolder` also takes the `Path` its callers already hold. `from(profile)` starts from a profile that exists, for a test that changes one key and means every other to carry — which is the case the deleted overloads served worst, since a six-argument call over a seven-key record carried six keys and silently unset the seventh. A key not named stays unset, exactly as the canonical constructor leaves a key the file does not mention.
- **The standard provenance is the fixture's, and a bespoke one stays the test's.** The `new ProfileValue(value, "set by this test", null)` idiom repeated beside the constructor calls is absorbed by the `String` form. What is deliberately not absorbed is a provenance that says something — `"the handover set"`, `"read off the labels"`, `"chosen by the archivist from the 2019 handover"`: those tests pass a whole `ProfileValue`, because the sentence is the thing the test is about. So raw `ProfileValue` construction survives in `src/test` where it carries meaning, and nowhere that it does not.
- **Nothing pins Jackson's creator choice, because with one constructor there is no choice to pin.** `@JsonCreator` has no job here and is not added. What is pinned is this decision: one test asserts that `Profile` declares exactly one public constructor, and says why in its claim, so a seventh overload goes red before anyone reviews it.

### Why not withers on the record

The second candidate — `Profile.skeleton().withSeedFolder(...)` on the record itself, usable from production — also deletes the overloads, and reads as well at the call site. It is refused because it hands `src/main` a way to answer a key's value, which is the one thing the domain says code must never do: the profile is *"authored by a person, never guessed at"* (`CONTEXT.md`), and ADR-062 makes `value` and `provenance` census's to read and never to write. The three withers that do exist move `measurement`, which is census's own field and the one field ADR-062 allows a re-run to overwrite. A production method whose only legitimate caller is a test is the overload problem again in a different shape.

### Why not keep them and pin the creator

The third candidate — leave the overloads, add `@JsonCreator` to the canonical constructor, and add a test that Jackson chose it — was the cheapest by some distance. It is refused because it fixes the part that was not broken: the creator choice already follows a documented rule, while the blind call sites and the silently-re-pointed subsets are what the next key will actually cost.

## Consequences

**Four tests in `ProfileTest` go, and nothing replaces them.** Each pinned that "the N-key constructor defaults the N+1th key unset", which is a fact about a constructor that no longer exists. The property they were reaching for — a key the file predates arrives unset — is pinned where it matters, at the YAML boundary, by `ProfileStoreTest.addsAKeyTheFileDoesNotYetMention`. The skeleton tests stay.

**Twenty-four test files change, and the `Adr` link map gains an entry; no behaviour changes.** Every positional call becomes a named one. `NextActionTest`'s scenario helpers stay, because naming the scenario is a different job from naming the keys, and they now build on the fixture.

**`ProfileFixture` goes in the `profile` package under `src/test`, not in a test-support package of its own.** ADR-052 records the constraint: `ApplicationModules` reads a new top-level package as a further capability module, and `ModuleBoundariesTest` — which exists to defend ADR-040 — fails on it. An existing module's package costs nothing, and is where this belongs anyway: it is that module's test vocabulary.

**The rule reaches past `Profile`.** Any record here that grows a second public constructor so that callers keep compiling is this decision being taken again in the other direction. What that convenience defers is a mechanical pass over the tests — the one this record pays for — and deferring it is what turned one overload into five.

**`AGENTS.md`'s decision count moves, and `docs/check-claims.mjs` fails the build if it does not.**
