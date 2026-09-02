# ADR-066 — The command line names the root; configuration is the fallback

- **Date**: 2026-09-02
- **Status**: accepted
- **Amends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)

## Context

[ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md) settled the two paths an invocation needs and gave them two different mechanisms. The working directory — where the database and `profile.yaml` live — is a Spring Boot property, `vespera.working-dir`, defaulted in `application.yaml` and overridden per invocation. The corpus root is a required positional argument, supplied on every invocation and configurable nowhere.

That asymmetry was not itself decided; it fell out of the working directory needing to be a property for a mechanical reason (the datasource URL is built from it long before the command line is parsed, which is why `WorkingDirectoryPreparer` is an environment listener rather than a bean). The root has no such constraint, so nothing forced it either way.

What the asymmetry costs shows up in how the tool is actually invoked. A corpus is hundreds of gigabytes at a path that does not move ([`AGENTS.md`](../../AGENTS.md)), censused and then re-censused as stages are added, and the pipeline never blocks (ADR-047) precisely so that an invocation can be unattended. An unattended invocation of a required positional means the archive's path lives in a scheduled-task definition or a shell script — outside the working directory, unversioned, and in a different place from the other path the same invocation needs. Retyping it is the smaller cost; the real one is that the corpus root is the one operator-supplied path with nowhere to be written down.

Making it configuration *instead* is the opposite error. A root that is only ever configured means a one-off census of a second archive is an edit to `application.yaml`, and it makes every invocation's subject invisible at the point of invocation.

## Decision

**The root is an optional positional argument, and `vespera.corpus-root` is consulted only when the command names none.** The command line wins whenever it says anything; configuration answers only silence.

| Command | Root |
| --- | --- |
| `vespera run <root>` | the argument, always |
| `vespera run` | `vespera.corpus-root`, or a refusal if that is unset |
| `vespera publish` | none — publication reads the ledger, never the corpus (ADR-054) |

**`vespera.corpus-root` ships unset.** An invocation that names no root and finds none configured refuses, with `USAGE` as its exit code and a message naming both ways to supply one. There is no derived default — not the working directory, not the process's current directory. A walk records what it finds beneath the root it was given (ADR-050), so a guessed root produces a census of the wrong tree that reports success, which is the one failure this project treats as unacceptable everywhere else it appears (ADR-055's unfinished walk, ADR-056's reconciliation).

**The resolved root and where it came from are logged before the job starts.** A configured root is a path nobody typed this invocation, so which archive is about to be walked, and on whose word, is not left to be inferred from the ledger afterwards.

**`--db-dir` is unchanged.** It stays a property with a command-line override because it has to be — the datasource is built from it before parsing. The two paths now read the same way from the operator's side, by coincidence of shape rather than by one mechanism: both are configurable, both are overridable per invocation. They are not, and should not be made, the same code path.

**The root does not move into `profile.yaml`.** The profile is the per-corpus record of judgements the engine cannot make for itself (ADR-043, ADR-061), and it already lives in a working directory that belongs to one corpus. A root key there would be the corpus naming itself, in a file located by the very configuration that would then contain it. `application.yaml` is where the operator's *machine* is described; the profile is where a *corpus* is described.

## Consequences

**ADR-054's documented gap widens, and stays documented.** ADR-054 accepted that nothing guards against a working directory pointed at the wrong root, on the grounds that both paths are typed on every invocation like a filename in any other CLI. Half of that reasoning is now gone: a configured root is typed once and used indefinitely, so a stale value is walked silently against whatever database the configured working directory holds. The log line above is the whole mitigation. No root is stamped into the database at creation and none is checked at open — still the same decision, revisited only on an actual incident rather than pre-emptively.

**A convenience for the common case, and nothing more.** One long-lived archive is configured once; a second archive is a positional argument, exactly as before. Nothing about the two-command surface (ADR-047), the non-interactive invocation, or the working directory changes.

**Every test either supplies a root or asserts the refusal.** The refusal is now a reachable path with its own exit code, so it is a case to pin rather than something picocli rejects before the command runs.

## Amends

**ADR-054**, in one respect: its CLI-shape table gave `root` as a required positional, and it is now optional with a configured fallback. Everything else ADR-054 decided stands unchanged — a corpus is still identified by its root path alone, the working directory is still explicit operator configuration never derived from the root, and `publish` still takes no root.
