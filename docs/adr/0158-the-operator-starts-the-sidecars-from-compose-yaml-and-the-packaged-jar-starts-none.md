# ADR-158 — The operator starts the sidecars from compose.yaml, and the packaged jar starts none

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md), for the packaged jar. The repository still owns the sidecars: `compose.yaml` declares them and pins their images and their ports. The jar does not start or stop them. The operator starts them once, from `compose.yaml`, and leaves them up across the invocations. ADR-011's single-command start still holds where Spring Boot's compose support is on the classpath, which is the development entry points only.
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md). `spring-boot-docker-compose` and `spring-ai-spring-boot-docker-compose` stay `<optional>` in `pom.xml`, and this record is now the decision behind that. Nothing in the pom changes.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md). The operator's shell receives the command's own exit code, because the operator runs the jar directly and no launcher sits between them.
- **Keeps**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md). The Docling image is built from `docker/docling-serve`, and the operator's start command builds it.
- **Answers** [#304](https://github.com/algernon28/vespera/issues/304).

## Context

The README said: "Vespera runs its document converter and its embedding model as sidecars and manages them itself." That was not true of the packaged jar, and nothing said so.

- **What starts `compose.yaml`.** Spring Boot's Docker Compose support is what brings `compose.yaml` up. It lives in `spring-boot-docker-compose`, with Spring AI's connection details for Chroma and Ollama in `spring-ai-spring-boot-docker-compose`. Both are `<optional>true</optional>` in `pom.xml`, and the Boot plugin leaves optional dependencies out of the repackaged jar. Measured on 2026-09-26: `target/vespera-0.0.1-SNAPSHOT.jar` nests 143 dependency jars under `BOOT-INF/lib/`, and none of them is either compose artifact.
- **Where it runs.** The compose support runs under `./mvnw spring-boot:run`, under an IDE launching `VesperaApplication`, and under `TestVesperaApplication`. It does not run under `java -jar`.
- **What `compose.yaml` already assumed.** Its header comment already reasoned from this: "from `java -jar` nothing reads it back". That is why every host port is pinned to the default that `application.yaml` and Spring AI assume. So the file was written for a jar that does not start its own sidecars, and the README described one that did.
- **What operators actually do.** The one operator run on this machine runs the jar with `java -jar`, and starts the sidecars itself from `compose.yaml`.
- **No launcher exists.** The README's commands are written as `vespera run <root>`, and the tree holds no launcher of that name. There is no script and no wrapper, and the build produces nothing named `vespera` on a path. picocli's command is named `vespera`. The operator gets there by `java -jar` on the Boot jar.

#304 set out three ways to make the README true. They are weighed below.

## Decision

**The operator starts the sidecars, and the operator runs the jar.** Before the first invocation, the operator runs `docker compose up -d --build` from the repository root. That starts Chroma, Ollama and docling-serve, and builds the Docling image from `docker/docling-serve`. The operator then runs Vespera as `java -jar target/vespera-0.0.1-SNAPSHOT.jar`, followed by the subcommand. The sidecars stay up across all five invocations, and the operator stops them when the curation is done.

**The packaged jar carries no compose support, deliberately.** The two compose dependencies stay `<optional>`, so the jar starts no container and stops none. Before this record, `<optional>` was Spring Initializr's default. From this record on, it is the decision.

### Why not the other two

**The jar manages the sidecars (drop `<optional>`).** Rejected for three reasons:

- **It ties the jar to the checkout.** Spring Boot looks for `compose.yaml` in the working directory, and the file's build context is `docker/docling-serve`, relative to the file. So the jar could only be launched from the repository root, or with `spring.docker.compose.file` pointed back at the checkout. Under this record the jar needs nothing from the checkout, and runs from wherever it is copied to.
- **It puts Docker in front of every command.** `vespera label`, `vespera --version` and a rejected option each need no sidecar. Each would have to start or confirm the whole stack before picocli read the command line. That is the start-up dependency ADR-142 removed for Chroma.
- **It gains nothing for the sidecars that need it most.** Dropping `<optional>` would let Chroma's and Ollama's ports float again, but docling-serve has no connection-details factory at all (ADR-071). Its port stays pinned whatever the classpath holds, so `compose.yaml`'s pinning argument would be rewritten for two of three services and still stand for the third.

**Maven is the launcher (`./mvnw spring-boot:run`).** Rejected because it breaks two things an operator is promised:

- **The exit code.** `spring-boot:run` reports a non-zero exit as a failure of the build, so the shell receives Maven's exit code and not the command's. ADR-141 exists so that the command's code reaches the shell.
- **The last line.** The README tells the operator that the last line of the output names the value to set next. Under Maven, the last lines are Maven's build summary.

Maven launching also recompiles on every invocation, and it passes the archive root through `-Dspring-boot.run.arguments`. That quoting is fragile for the Windows paths with spaces this tool exists for, and it resolves relative paths against the repository rather than against where the operator stands.

### What ADR-011 keeps, and what it loses

- **It loses the single command, for the jar.** The operator runs one more command, once per curation rather than once per invocation.
- **It keeps what the single command was for:**
  - the operator never chooses an image, a version or a port;
  - `compose.yaml` is still the one file that pins them, in step with `TestcontainersConfiguration` and `vespera.docling.image` (ADR-147).
- **The development entry points still start and stop the sidecars themselves.** These are `spring-boot:run`, an IDE run of `VesperaApplication`, and `TestVesperaApplication`. ADR-142's "the compose lifecycle still starts the sidecars" stays true of those entry points. It is not true of the jar.

## Consequences

- **The README is rewritten to say this.** Its "Running it" section names:
  - the build (`./mvnw package`);
  - the start command, and that it builds the Docling image rather than pulling it;
  - the pulls Ollama needs;
  - the command `vespera` stands for everywhere else in the file.

  `docs/check-claims.mjs` checks each of these against the tree:
  - the jar path against the pom's artifact and version;
  - the services, the build context and the ports against `compose.yaml`;
  - the generation model against `application.yaml`;
  - the Java version against the pom.
- **`PackagedJarIT` pins the jar's half.** It opens the packaged jar and asserts that no compose artifact is under `BOOT-INF/lib/`. If someone drops `<optional>`, the jar starts containers from wherever it is launched, which silently reverses this record, and this test is what fails.
- **`compose.yaml` does not change.** Its header comment was written on the premise this record adopts, and it stays true word for word.
- **Operator-facing costs:**
  - **The embedding model and the generation model live inside the Ollama container.** `compose.yaml` declares no volume, so `docker compose down` discards them with the container, and `docker compose stop` keeps them. The README says which command to use.
  - **Chroma's contents go with its container too.** That is harmless, because Chroma is a disposable projection of what SQLite holds (ADR-039).
  - **The operator must pull the embedding model and the generation model before each is needed.** No configuration asks Spring AI to pull either, and Ollama serves only what it has been given. So the embedding model is needed before invocation 2, and the generation model before invocation 5.

## What this does not decide

- **Unverified: whether Spring Boot's compose support builds the Docling image on first use.** This applies to the development entry points, where Spring Boot runs `docker compose up` itself. It is not verified here, because measuring it means starting `compose.yaml`, and the sidecars on this machine belong to a live run. The operator's path does not depend on the answer, because `--build` makes the build explicit.
- **Unverified: that following the README gets an invocation past stage 2.** #304 asks for this to be checked on a machine with only Java 26 and a Docker daemon, and recorded in the pull request. That check has not been run under this record.
- **No distribution.** No launcher script, installer or image of Vespera itself is shipped. `vespera` in the README is shorthand for the `java -jar` command it names. A launcher, if one is ever wanted, is its own decision.
