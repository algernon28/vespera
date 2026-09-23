# ADR-142 — The vector store connects to Chroma when it is first used

- **Date**: 2026-09-23
- **Status**: accepted
- **Keeps**: [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md) — Chroma stays configured as the derived projection; only the moment it is first reached moves.
- **Keeps**: [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md) — the compose lifecycle still starts the sidecars; this changes what a command needs when they are not running, not who runs them.
- **Builds on**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md), whose exit code a command could not return while the context failed to start.

## Context

`spring-ai-starter-vector-store-chroma` auto-configures a `vectorStore` bean whose initialisation fetches its collection from Chroma. Built eagerly, it made the whole application context depend on a reachable Chroma: with the compose lifecycle off and nothing on `localhost:8000`, every command — `vespera --version` and a rejected `vespera --bogus-option` included — failed to start and exited 1 before picocli read the command line. Measured on 2026-09-22 by running the packaged jar in a Linux container, where the stack trace named `vectorStore` and a refused connection.

It surfaced as CI failing `CliExitIT` on the pull request that shipped ADR-141: the runner has no Chroma on 8000, while a developer machine running the sidecar hides the dependency entirely. That pull request unblocked CI by passing `spring.ai.vectorstore.type=none` to the launched jar, which removes the bean rather than deferring it — a test-only workaround that left the shipped behaviour as it was.

Nothing in `src/main` reads the vector store today. Ollama, the other sidecar with a Spring AI starter, reaches nothing while the context starts; the vector store was the only bean that did.

## Decision

The vector store bean is lazy. `pipeline`'s `VectorStoreConfiguration` declares a `BeanFactoryPostProcessor` that marks every `VectorStore` bean definition lazy, found by type without eager initialisation, so the bean is built — and Chroma first contacted — only when something asks for it. With no reader today, a command never contacts Chroma at all.

It lives in `pipeline` rather than `embedding`, although `docs/architecture.md` §1.4 gives the Chroma projection to `embedding`: what it decides is when the application's start-up builds a bean, which is wiring for every command and so belongs to the composition root, not to the projection itself. It names no Vespera module and changes no module's declared dependencies.

Only that bean is deferred. Spring Boot's global lazy initialisation would also move every other bean's configuration fault from start-up to first use, which is the wrong trade for a CLI whose start-up is where such faults are found.

The bean is kept rather than switched off: whatever later reads the vector store gets the configured Chroma store, and meets the connection refusal then, at the point that actually needs Chroma.

## Consequences

- A command that does not use the vector store starts and returns its own exit code whether or not Chroma is reachable.
- The first reader of the vector store, when one is written, is where a missing Chroma will surface. If that reader is built at start-up by constructor injection, it reintroduces the start-up dependency for every command; it should ask for the store where it is used, or through an `ObjectProvider`.
- `CliExitIT` drops the `type=none` workaround and points the jar at a Chroma on a closed port, so it holds whether or not the machine running it has a Chroma sidecar up. `VectorStoreIsReachedOnFirstUseTest` starts the whole application in-process the same way and pins three things: the context starts; asking for the vector store is what tries to connect — a refused connection rather than a missing bean, which is what separates deferring the store from removing it; and the datasource is still not lazy, which is what separates deferring this one bean from global lazy initialisation.
- Starting the application no longer builds the Chroma store, so starting it is no longer a check that Spring AI's store works against the pinned Chroma image. `VesperaApplicationIT` keeps that check by asking for the vector store explicitly against the Testcontainers Chroma: the success side of the refusal above, and the one place a Spring AI or Chroma upgrade that breaks the store is still caught before a reader exists.
