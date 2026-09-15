# ADR-119 — `Profile`'s constructor overloads are deleted, and the convenience they bought moves to a test fixture

- **Date**: 2026-09-15
- **Status**: accepted
- **Amends**: nothing. Neither of the two records this touches decided anything about constructors — see "What the overloads were not" below, which corrects a claim the code's own javadoc made about [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) rather than a claim ADR-062 made.
- **Rests on**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) (the record *is* the schema, which is why a key is a record component and not a map entry, and why the shape of this type is load-bearing), [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) (a key the file predates arrives unset — the promise the overloads were mistaken for), [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) and [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (each invocation the operator's path gained brought a key, which is why the overload count tracks the ticket history).

## Context

### Measured: seven keys, six constructors, and no production caller

`Profile` is a record of seven components, every one of them a `ProfileValue`. Beside its canonical constructor it carried five more, of arity two through six — one per key added since #58, each written so that the call sites of the previous key would still compile.

Counted in the tree at `d30a90f`:

| | Count |
|---|---|
| Constructors on `Profile` | 6 (arities 2, 3, 4, 5, 6, 7) |
| Call sites of any of them in `src/test` | 45, across 25 files |
| Call sites of a non-canonical one in `src/main` | **0** |
| Call sites of the canonical one in `src/main` | 4, all inside `Profile` itself |

Those four are `skeleton()` and the three `with*Measurement` methods, which `CensusTasklet`, `ContentCensusTasklet` and `RelevanceReportTasklet` do call. Nothing else in `src/main` constructs a `Profile` at all — `ProfileStore` deserialises one.

So this is a production type whose public surface was shaped entirely by what test code found convenient, and the growth rate is one method per profile key. An eighth key is already implied by ADR-108's `num_ctx`.

### Measured: the Jackson creator hazard does not reproduce

The ticket's most serious charge is that six constructors give Jackson more than one candidate creator, that it happens to pick the right one, and that nothing pins it. The first half was checked rather than argued.

A probe against the tree's own `ProfileStore` — `tools.jackson` (Jackson 3), `FAIL_ON_UNKNOWN_PROPERTIES` enabled — loaded a `profile.yaml` naming all seven keys and got all seven answered, which only the seven-parameter constructor can produce; and a file naming one key, which loaded with the other six unset. Two further probes, on throwaway records declared in the test tree, added to a two-component record (a) an extra one-argument constructor and (b) an extra two-argument constructor of a different shape. Neither was selected: both records deserialised through their canonical constructor, and the partial input produced a null rather than the overload's substituted value.

**Jackson 3 resolves a record's properties-based creator from its record components, not by ranking its constructors.** An unannotated extra constructor is invisible to it. So the hazard is latent rather than live: it needs a `@JsonCreator` somewhere else, or a Jackson whose record handling differs, to become real.

That finding removes the only problem the cheapest candidate direction would have fixed.

### What the overloads were not

Each overload's javadoc says it is kept "so that adding a third key mints no compile break at every existing caller — the third key arrives unset, the same 'a key the file predates is added unset' merge the canonical constructor already gives a key missing from the file."

The second half of that sentence is true and the first half is about test code. ADR-062's merge is a rule about `profile.yaml`: a key absent from the file arrives as null and the canonical constructor turns it into `ProfileValue.unset()`. That mechanism lives in the compact constructor and is untouched here. The overloads never carried it; they carried source compatibility for callers, and every one of those callers is a test.

The distinction matters because it is what made five additions look like five applications of a recorded decision instead of five instances of the same unrecorded one.

## Decision

### The five non-canonical constructors are deleted

`Profile` keeps exactly one constructor: the canonical seven-component one, with its compact body's null-to-unset normalisation unchanged. `skeleton()` and the three `with*Measurement` methods stay exactly as they are — they have production callers, and they name the key they touch.

### The convenience moves to `ProfileFixture`, in the test tree

`src/test/java/io/algernon/vespera/profile/ProfileFixture.java` builds a `Profile` one named key at a time, starting from every key unset, and offers `profileFrom(existing)` for the "load it, change one key, save it" shape that made the longest positional call sites. All 45 call sites are migrated.

This gives back, in test code, exactly what the overloads bought in production code: **a new key changes no existing call site**, because an unnamed key defaults to unset. It adds two things the overloads could not:

- **A call site names the keys it sets.** `new Profile(set("/corpus/exemplars"), null, set("0.4"), set("embeddinggemma"), null)` named none of them. Its replacement names four.
- **A wrong subset is no longer expressible by accident.** The positional form carried the key's meaning entirely by position, so inserting a key in the middle would silently re-point every call site of that arity, and two callers wanting different subsets of one arity were indistinguishable to the compiler.

### Three tests pin what the deletion is worth

- **`Profile` has exactly one constructor.** Asserted reflectively, so that re-adding an overload — the move this record exists to stop — is a red build rather than a review comment.
- **A file naming all seven keys loads with all seven answered.** The only creator that can produce that is the seven-component one, so this is the behavioural pin on which constructor Jackson deserialises through. It survives the eighth key by gaining a line, which is the right cost.
- **A file naming one key loads with the rest unset.** ADR-062's merge, asserted where it actually lives — through the reader, over YAML — rather than at a constructor that was only ever standing in for it.

### The alternatives, and why each loses

**Withers on the canonical record (`Profile.skeleton().withSeedFolder(...)`), usable from production.** Rejected: it is the same growth, renamed. Seven withers today, eight with `num_ctx`, one per key forever — and six of the seven would ship with no production caller, which is the exact charge this record is answering, committed a second time in nicer syntax. The three `with*Measurement` methods are not a counter-example: they exist because three tasklets call them.

**Keep the constructors and pin Jackson with an explicit `@JsonCreator`.** Rejected twice over. It leaves the readability and wrong-subset problems standing, which the ticket already concedes; and the hazard it addresses did not reproduce, so it would add an annotation to production code to defend against a failure the measurement says is not there. Worse, it would bless the constructor list as intentional, which is the opposite of what is wanted.

**A `Map<ProfileKey, ProfileValue>` behind a key enumeration.** Rejected on ADR-061's ground, which is explicit: the record *is* the schema, so that a key that does not exist is a compile error. A keyed map is the map ADR-061 refused, and it would cost the compile-time key check to buy the same call-site readability a fixture gives for nothing.

## Consequences

**A new profile key costs one record component, one field and two methods in the fixture, and no call-site edits.** Previously it cost a record component and a constructor, in production, forever. The eighth key is the first to be added under this rule.

**`Profile`'s public surface stops tracking the ticket history.** Its size is now a function of how many keys the profile has, which is the only thing it should be a function of.

**Four tests are deleted, and they were the ones defending the overloads.** `ProfileTest`'s `theTwoKeyConstructorDefaultsTheThirdKeyUnset` and its three successors asserted that a call site untouched since an earlier ticket still gets the later key unset. What they claimed is still true and still tested — by the file-level merge test named above — but the constructors they claimed it *through* no longer exist. Deleting a test whose subject a decision removes is the decision landing, not the number being managed; the replacement claims are stated above so the arithmetic can be checked.

**The test tree gains a public class in an existing module package, not a new one.** `ProfileFixture` sits in `io.algernon.vespera.profile` under `src/test`, beside the two test classes already there. A support class in a package of its own would read as a further module to `ApplicationModules` and fail `ModuleBoundariesTest`, which is why `TestSteps` lives in the root package; this one needs no such exception.

**`ProfileStore` and `profile.yaml` are untouched.** No file written by any prior version reads differently, because nothing about the record's components, their names, or their order changes.

## What this does not decide

**Whether `ProfileValue` should carry a typed value rather than a `String`.** ADR-061 requires strict typing on load and the current record types every value as a string with the parse deferred; that is a live question and a different one.

**Whether `num_ctx` is a profile key at all.** ADR-108 implies one and #196 owns it. This record only guarantees that adding it costs no constructor.

**Whether the fixture should be able to express an invalid profile.** It can today — any string is accepted as any value — and nothing here says it should validate. A fixture that refused what an operator can type would stop tests from covering what an operator can type.

**Whether `skeleton()` should be public.** It is package-private and has one production caller in its own package. The fixture does not need it widened, and widening it on the fixture's behalf would be this record's own mistake repeated.
