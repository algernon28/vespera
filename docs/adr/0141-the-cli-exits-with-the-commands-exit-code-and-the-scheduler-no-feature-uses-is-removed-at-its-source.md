# ADR-141 — The CLI exits with the command's exit code, and the scheduler no feature uses is removed at its source

- **Date**: 2026-09-22
- **Status**: accepted
- **Amends**: [ADR-037](0037-spring-modulith-event-publication-registry-dropped.md) — its "`starter-core` retained for boundary checks" is narrowed to the one artifact the boundary check actually reads.
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s environment listener, which fires before the context exists and is therefore untouched by an exit that closes it.
- **Settles** [#269](https://github.com/algernon28/vespera/issues/269).

## Context

`vespera` finishes its command and then never exits. Measured on 2026-09-22: the real-corpus verification of a stage-2 run left a JVM alive for hours after the step reported `COMPLETED`, and a `vespera --bogus-option` invocation — which picocli rejects before any stage runs — stayed alive too. A JFR thread-start recording, then `jstack` on a leftover process, named the holder: a non-daemon thread parked on a `ScheduledThreadPoolExecutor` delay queue. It belongs to `spring-modulith-moments`: `MomentsAutoConfiguration` registers a `Moments` bean carrying `@Scheduled` hourly and daily tasks, no `TaskScheduler` bean is declared anywhere, so Spring falls back to `Executors.newSingleThreadScheduledExecutor()`, whose thread is non-daemon, and whose shutdown only runs once the JVM has already begun exiting.

Two things were wrong at once, and each is the reason the other was never noticed. `VesperaApplication.main` called `application.run(args)` and discarded the result, so `VesperaCli`'s `ExitCodeGenerator` — which the class exists to expose, because ADR-047's "did it work has to be answerable without reading the log" is exactly that code — was never consulted, and the context was never closed. And a scheduler this application never asked for existed at all: `spring-modulith-moments` ships in the jar only because `spring-modulith-starter-core` pulls it in transitively, while `AGENTS.md` says Spring Modulith is here "for boundary verification only" and nothing in `src/main` uses a moment event.

[#264](https://github.com/algernon28/vespera/issues/264) makes a full run on the 42,851-file folder this tool exists for a roughly three-hour job, so a run that finishes and then never returns cannot be scripted, chained, or told apart from one still working. The operator can only tell it is done by reading the log and killing the process by hand; the leftover JVM also holds the jar and the database file open.

## Decision

### 1. The process exits with the command's exit code

`VesperaApplication.main` wraps the run in `System.exit(SpringApplication.exit(application.run(args)))`. `SpringApplication.exit` closes the context and reads every `ExitCodeGenerator`; `VesperaCli` is one, and its `getExitCode()` is the value picocli returned. `System.exit` then ends the JVM whatever non-daemon thread a dependency has started and left parked.

This is the canonical Spring Boot CLI shape, and it is the half of the repair that closes the class rather than this instance: the next non-daemon thread any future dependency starts will not hang the CLI.

### 2. The scheduler goes away at its source

`spring-modulith-starter-core` is replaced in the pom by `spring-modulith-api`, the artifact that carries the `@ApplicationModule` annotation the boundary test reads. `spring-modulith-core` — the `ApplicationModules` verifier the test invokes — is still reached through `spring-modulith-starter-test` in the test scope. Nothing in `src/main` uses Modulith's runtime, so the starter, and the `spring-modulith-moments` auto-configuration it drags in, is dropped entirely.

This is the half that removes the instance. It is the more specific of the two and the less general: it says nothing about the next unwanted thread, only that this one — a scheduler publishing moment events nothing consumes — is not here, because nothing here asked for it.

## Consequences

- A command that picocli rejects returns picocli's usage code to the shell, non-zero, exactly as a failed command should; previously that code was computed and then dropped. A command that succeeds returns zero, and its last stage having finished is now the moment the process ends, not an event to infer from a log.
- `WorkingDirectoryPreparer` (ADR-054) is unaffected: it runs during environment preparation, before the context exists to be closed. Nothing here assumes the context stays open past the command, because the command is the whole of the program (ADR-047, ADR-101).
- `TestVesperaApplication`, the dev entry point that delegates through `SpringApplication.from(VesperaApplication::main)`, now exits at the end of its command too; that is the behaviour it is a thin wrapper for.
- The exit is pinned by `CliExitIT`, an integration test that launches the packaged jar and asserts it exits within a bound carrying the command's code, so neither half of this decision can regress silently.