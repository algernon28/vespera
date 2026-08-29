# ADR-054 — A corpus is its root path; the database lives in a configured working directory

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-009 gives one SQLite database per corpus but says nothing about how a corpus is named, where that database sits relative to the archive it describes, or how the CLI finds it. ADR-047 sizes the CLI to two commands, which rules out an interactive prompt to resolve any of this at run time. Nothing in `CONTEXT.md` gives a corpus an identity independent of the archive itself — no name, no id — so the gap was purely: where does the database and the profile file live, and how does an invocation locate them?

The census archive is not necessarily writable. "Hundreds of gigabytes of already-existing files" ([`AGENTS.md`](../../AGENTS.md)) plausibly means a read-only mount, which rules out writing the database inside the corpus root.

## Decision

**A corpus is identified by its root path alone.** No operator-supplied name, no generated id. Nothing in the domain gives a corpus an identity that survives the archive moving, and a generated id would need its own lookup (id → path) that this CLI has nowhere clean to keep.

**The database and the profile file live together in a working directory that is explicit operator configuration, never derived from the root.** Configured as a Spring Boot property, `vespera.working-dir`, defaulted in `application.yaml` and overridable per invocation on the command line. Inside it, two fixed filenames: the SQLite database and `profile.yaml`. Nothing about the root path is used to locate either — the working directory is supplied, not computed.

**CLI shape** (ADR-047's two commands):

| Command | Arguments | Working directory |
| --- | --- | --- |
| `vespera run <root>` | `root`: corpus root, positional | `vespera.working-dir` (config or override) |
| `vespera publish` | none | `vespera.working-dir` (config or override) |

`publish` takes no root: publication reads from the ledger, never the corpus.

## Consequences

**No guard against a working directory pointed at the wrong root, or a root reopened under a different working directory.** Both the root and the working directory are explicit arguments the operator supplies on every invocation, exactly like a filename typed into any other CLI. No root is stamped into the database at creation, and none is checked at open. This is a documented assumption, not a decision the design routes around — the failure mode is operator error, not a design gap, and it is revisited only if it causes an actual incident.

**The archive is never written to.** Because the working directory is independent of the root, census can run against a read-only mount without needing a fallback location.

**The profile and the database share a lifecycle.** Both are per-corpus operational data the pipeline reads and writes on the operator's behalf; keeping them in one directory means one path to manage, back up, or delete, not two.

## Amends

None. ADR-009 established one database per corpus; this decision only supplies what ADR-009 left open — where that database sits and how an invocation finds it.
