# ADR-058 — A stage's implementation version is the last commit touching its module

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-048 defines `run_id = hash(stage's implementation version, config consumed, walk id, upstream run ids)`, chained so an upstream implementation change forces downstream re-runs. Nothing records how "implementation version" is obtained, and the hash is only as good as that input.

Four options were on the table, each with a named failure mode:

- **Repo-wide git commit SHA** — honest, but invalidates every run on every commit, including a README typo. Re-running extraction over hundreds of GB because a comment changed is exactly the cost ADR-017's cheapest-filter-first ordering exists to avoid.
- **Maven project version** — stable, but only changes when a human remembers to bump it, so it can silently lie.
- **A per-stage hand-maintained constant** — precise in principle, most likely to be forgotten in practice, which silently serves stale verdicts as fresh.
- **A hash of the stage's full compiled bytecode (whole classpath)** — mechanical, nothing to forget, but sensitive to compiler/toolchain noise and to transitive dependency bumps three levels down, reintroducing a milder version of the repo-wide problem.

The asymmetry: too-eager invalidation costs compute; too-lazy invalidation costs correctness (a stale verdict served as fresh, with nothing to say so). Every hand-maintained option fails toward too-lazy; every whole-scope mechanical option fails toward too-eager.

## Decision

**A stage's implementation version is the SHA of the last git commit that touched its owning capability module's path** — `git log -1 --format=%H -- src/main/java/io/algernon/vespera/<module>` (plus the specific stage-orchestration class in `pipeline` that drives it, if that file lives outside the module's own path), computed at build time and baked into a generated resource read at runtime when `pipeline` computes `run_id`.

This is scoped, not repo-wide: a commit touching an unrelated module or top-level documentation does not change it. It is mechanical, not hand-maintained: any real commit to the module's own files changes it automatically, with nothing for a person to remember to bump. It is human-traceable: the version is an actual commit SHA, `git show`-able, rather than an opaque hash of class files — matching how this project already cites decisions by commit and PR elsewhere.

**Dependency versions are explicitly excluded from this hash**, confirmed as a boundary rather than an oversight: a bump to a third-party library (the embedding model, Docling itself) is a fact for the profile/config to record explicitly, not something the implementation-version hash should notice by accident. `run_id`'s separate "config consumed" input is where a dependency change that actually matters must be captured.

**Computed at build time, not runtime.** A build step resolves the path-scoped commit SHA per module and writes it into a generated resource shipped in the jar, in the same phase module boundaries are already Modulith-verified — avoiding any runtime dependency on `git` being present or the `.git` directory being available where the jar eventually runs.

## Consequences

**The accepted failure mode is: an in-module comment or formatting-only edit still bumps the version.** Git's path filter operates on files, not on compiled output, so it cannot distinguish a behavior change from a no-op edit the way a bytecode hash could. This is accepted as strictly milder than the options it replaces — the cost lands on one module, once, as one avoidable re-run, not on the whole pipeline (repo-wide SHA) and not as silent staleness (every hand-maintained option).

**A module boundary now doubles as a build-provenance boundary.** If a capability module's package structure is ever reorganised across the path used for the `git log` filter, its implementation-version history effectively resets — a consequence of tying the mechanism to filesystem paths rather than to a logical module identifier `git` doesn't have a notion of.

**No custom hashing tooling is needed.** A shell/`exec-maven-plugin` call to `git log` at build time is the entire mechanism — no bytecode reader, no reflection over loaded classes, no toolchain-sensitivity to reason about beyond git itself being present at build time (never at runtime).

## Amends

None. This supplies the "implementation version" input ADR-048 named but left unspecified.
