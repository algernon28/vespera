# ADR-214 — Chroma is removed, and vectors live in SQLite alone

- **Date**: 2026-10-09
- **Status**: accepted. The three points first left to the operator were answered on 2026-10-09 (§9): this record amends ADR-001; the run ids of stages 3 to 6b move once, with [#456](https://github.com/algernon28/vespera/issues/456)'s; and the README says nothing of the leftover container.
- **Supersedes**: [ADR-142](0142-the-vector-store-connects-to-chroma-when-it-is-first-used.md), whole. There is no vector store left to connect, at start-up or at first use, and its requirement that the store stay configured for a reader still to come is withdrawn (§1).
- **Amends**: [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md), in its Chroma half. *"Chroma populated from that cache, droppable/rebuildable at any time"* has no object. *"Vectors written to SQLite when computed"*, and SQLite being authoritative for them, stand, and are now the whole of it (§2).
- **Amends**: [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md), in *"ANN index is rebuildable"*. There is no approximate-nearest-neighbour index to rebuild. *"Vectors cached (chunk hash + model identity)"* stands.
- **Amends**: [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md), in the section *"Chroma is retained on speculation, and stage 5 does not use it"*, its sentence *"It is nevertheless kept"* and the obligation after it, and the Consequence *"Chroma is carried without a caller, on the record"*. The obligation it put on the next map, to justify Chroma or remove it with its sidecar, is discharged here by removal. Its measurement, and its paragraph on what would bring a vector database back, stand: they are the conditions a later record would answer (§1).
- **Read with**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md), [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md), [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md) and [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md), where each names the Chroma sidecar. Each decision stands; the sentences that count three services or speak of Chroma's container describe a service that is gone (§6).
- **Amends**: [ADR-001](0001-tech-stack-is-a-fixed-constraint.md), by the operator's answer of 2026-10-09 (§9, question 1). *"a vector database"* leaves the fixed stack, which is Java, Spring Boot and Spring AI, with every vector in SQLite.
- **Rests on**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), the principle the removal applies; [ADR-020](0020-relevance-scoring-function.md), [ADR-045](0045-clustering-runs-within-each-seed-partition.md) and ADR-085, by which scoring and clustering read SQLite and need no vector database; [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) and [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md), by which stage 6b sends a cluster's documents themselves to the serving engine and retrieves nothing; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and `StageModules` for the run ids (§8). A reading of the tree at `4b99a03`, and `mvn dependency:tree` over the pom as it is and over a copy without the two artifacts §3 removes (§5). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Keeps**: [ADR-009](0009-one-storage-technology-single-database.md) as clarified: a vector database beside SQLite is not ruled out, and a later record may add one (§1). [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) and [ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md): a vector and its embedder identity are what they were. [ADR-013](0013-ollama-is-the-default-engine.md) and [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md): Spring AI's Ollama starter and the models it serves. ADR-158 and ADR-179 §1: the operator starts the sidecars, and no entry point starts or stops one.
- **Settles**: the Chroma point of [#352](https://github.com/algernon28/vespera/issues/352) (its §3), by the operator's decision of 2026-10-09 to remove Chroma. The rest of #352 is not this record's.

## Context

**What Chroma was.** ADR-039 made SQLite authoritative for vectors and Chroma a projection populated from that cache, droppable at any time. ADR-085 then measured that an exact scan over every chunk vector of a 200,000-chunk corpus takes 0.30 s on one thread, so an approximate-nearest-neighbour index trades correctness for nothing at this pipeline's scale; it found stage 5 neither reads nor writes Chroma, and kept it anyway, *"on speculation"*, because stage 6 was not designed yet. It obliged the next map to justify Chroma rather than inherit it, and said that if 6a and 6b turned out to iterate over clusters already computed, Chroma should go, *"along with the compose service, the Testcontainers image and the integration test's Chroma half"*.

That map was [#151](https://github.com/algernon28/vespera/issues/151). It closed on 2026-09-20 with 6a and 6b built, and both do iterate over the clusters stage 5 computed: 6a names and orders them, and 6b sends each cluster's documents to the serving engine in one call (ADR-108, ADR-110, ADR-166). Neither retrieves anything. The obligation was not taken up, and Chroma stayed.

**What ADR-142 added.** Spring AI's Chroma store fetches its collection as its bean is built, so on 2026-09-22 every command, `vespera --version` included, failed to start without a reachable Chroma. ADR-142 made the store bean lazy, through `pipeline`'s `VectorStoreConfiguration`, and kept it configured on purpose: *"whatever later reads the vector store gets the configured Chroma store"*. It told the first reader to ask for the store where it is used or through an `ObjectProvider`.

**What reads it, read at `4b99a03`.** Nothing.

- The one class under `src/main` that names a Spring AI vector-store type is `VectorStoreConfiguration`, and it names it only to defer it. No class writes to the store or reads from it; ADR-039's *"populated from that cache"* was never built.
- Scoring and clustering read the `vector` table, through `embedding`'s `VectorCache` and `RelevanceDistribution`. The Spring AI types `src/main` names are `ChatModel`, `EmbeddingModel` and Ollama's options and connection details, and the one vector-store type above.
- `schema.sql` holds no table for Chroma, and nothing written into a working directory names it.

**What it cost to carry.** The starter, and through it four more artifacts, `spring-ai-autoconfigure-vector-store-chroma`, `spring-ai-autoconfigure-vector-store-observation`, `spring-ai-chroma-store` and `spring-ai-vector-store` (§5). A configuration class whose only job was to keep that starter from stopping every command. A property. A sidecar the operator starts and leaves running, on port 8000, that nothing reaches. A test container started beside the integration tests that need Docker, and two tests whose only subject is the deferral. Three integration tests that point the packaged jar at a Chroma on a closed port so that they hold on a machine running one.

**What #352 asks.** That Chroma, its configuration class and its test container go only under a record that weighs ADR-039 and ADR-142, and that the record retire the requirement and not only the dependency, because *"re-add it when the first reader is built"* is the reasoning ADR-046 rejects: the pom carries what a recorded decision requires, not what a future one might.

## Decision

### 1. Chroma is removed, and the requirement for it is withdrawn

No recorded decision requires a vector database, and none is kept for a reader nobody has decided to build. ADR-142's requirement that the store stay configured for its first reader is withdrawn with the store, and ADR-085's choice to keep Chroma on speculation is reversed.

**A reader that wants one is a decision of its own.** ADR-085 named what would bring a vector database back: a query surface over the finished archive, which no recorded decision asks for, or a corpus large enough that the exact scan stops being instant. Whichever comes first is recorded in its own ADR, which chooses the store, adds its dependency, its sidecar and its tests, and says where the first read is made. Nothing here is left in place for it.

### 2. Vectors live in SQLite alone

The `vector` table ADR-085 created is where every vector is, and nothing is projected from it anywhere. Its key, its content addressing, its embedder identity (ADR-084, ADR-091) and every reader of it are unchanged. No table is added or dropped, and no module's schema version moves.

### 3. What goes

Every item below was found by `grep -ril chroma` over the tree outside `target/` and `.git/`, and by the same search, case-insensitive, for `VectorStore`, `vectorstore`, `vector-store`, `vector store`, `8000` and a Chroma volume. No Chroma volume exists anywhere: Chroma never had one (ADR-179 §5). `docs/decision-ledger.md` names Chroma in ADR-039's row and is not edited, being closed to edits. `handouts/` is not this record's.

**Production, the build and configuration** (`spec-implementer`, step B of §10):

| File | What goes |
| --- | --- |
| `src/main/java/io/algernon/vespera/pipeline/VectorStoreConfiguration.java` | The file. |
| `pom.xml` | The dependency `org.springframework.ai:spring-ai-starter-vector-store-chroma`; the test dependency `org.testcontainers:testcontainers-chromadb`; in the comment above `maven-failsafe-plugin`, *"VesperaApplicationIT starts Chroma and Ollama via Testcontainers"* becomes *"VesperaApplicationIT starts Ollama and docling-serve via Testcontainers"*. `spring-ai-spring-boot-testcontainers` stays: Ollama's container still reaches the application through its `@ServiceConnection`. |
| `src/main/resources/application.yaml` | The block `spring.ai.vectorstore` with `chroma.initialize-schema: true` and its two comment lines. In the comment above `vespera.docling.base-url`, *"same as Chroma and Ollama, but unlike them"* becomes *"same as Ollama, but unlike it"*, and *"the way spring-ai's starters do for Chroma/Ollama"* becomes *"the way Spring AI's starter does for Ollama"*. |
| `compose.yaml` | The service `chroma` (image `chromadb/chroma:1.5.9`, `restart: unless-stopped`, port `8000:8000`). Its two comment lines on the restart policy (*"Each service here is restarted by Docker … (ADR-164)"*) move to the first service that remains, `ollama`, above its `restart:` key. In the header, *"the defaults in application.yaml and Spring AI (8000, 11434) are all an invocation has"* becomes *"the defaults in application.yaml and Spring AI (11434) are all an invocation has"*. No top-level volume goes, since none was Chroma's. |
| `.github/workflows/build.yml` | In the header comment, *"The integration test starts Chroma and Ollama through Testcontainers"* becomes *"The integration tests start Ollama and docling-serve through Testcontainers"*. No step changes: no step names Chroma. |

**Tests** (`analyst`, step A of §10): the table in §10.

**Documentation** (`analyst`, step C of §10):

| File | What changes |
| --- | --- |
| `README.md` | "Start the sidecars": *"three services"* becomes two, and the bullet *"Chroma, the vector store;"* goes; the ports sentence names `11434` and `5001`. `docs/check-claims.mjs` holds both against `compose.yaml`. |
| `docs/check-claims.mjs` | The entry `chroma: /\bChroma\b/` in the map `said`, which otherwise fails the README for naming a service `compose.yaml` no longer runs, and fails it again once the README stops naming it. |
| `docs/architecture.md`, and `docs/architecture.html` regenerated | §1.3's clustering bullet loses *"and bounds Chroma's working set to one partition at a time"*. §1.4's module table and its diagram drop *"Chroma projection"* and *"Chroma"* from `embedding`. §1.5's bullet *"SQLite is authoritative for vectors; Chroma is a derived, disposable projection"* says vectors live in SQLite alone and Chroma was removed by this record. §2's opening sentence notes that ADR-001 no longer fixes a vector database, and its row *Vector index* reads *"None: an exact scan over the vectors SQLite holds (ADR-085); Chroma removed"*, decided by ADR-085 and ADR-214. The list *"Explicitly removed from the pom"* gains `spring-ai-starter-vector-store-chroma` and `testcontainers-chromadb` (ADR-214). |
| `AGENTS.md` | The tech-stack sentence loses *"Chroma as a disposable vector projection (…ADR-142)"* and says SQLite holds every vector, with no vector database (ADR-214). The ADR count is this record's own commit (§10). |
| `.claude/agents/tester.md`, `.claude/agents/spec-implementer.md` | *"starts Chroma and Ollama through Testcontainers"* becomes *"starts Ollama and docling-serve through Testcontainers"*, and nothing else in either file. These files configure agents, so the edit is made with the operator's approval. |
| `docs/adr/0179-…md` | The note on `anIdeRunStartsNoContainer` in its "What pins it", which names *"its Chroma sibling"*, is corrected in place when that test's list drops the class (§10, step A), as `docs/adr/README.md` asks of a note on which tests hold a record, citing #352. |

### 4. What stays

- **SQLite's vectors and the caches beside them**: the `vector` table, the run-scoped `relevance_score`, the clusters, all as they are.
- **Spring AI's Ollama starter**, `spring-ai-starter-model-ollama`, with its chat and embedding clients and the settings under `spring.ai.model` and `spring.ai.ollama`.
- **The other sidecars**: `ollama` and `docling-serve` in `compose.yaml`, their images, ports, volume and restart policy, and `compose.gpu.yaml` unchanged.
- **The test containers for Ollama and docling-serve** in `TestcontainersConfiguration`, and `spring-ai-spring-boot-testcontainers` with `spring-boot-testcontainers`.
- **`spring.docker.compose.enabled=false`** where three tests set it. It is ADR-179's, not Chroma's.

### 5. Spring AI's auto-configuration after the starter goes

`mvn dependency:tree` over the pom at `4b99a03`, and over a copy of it with the two artifacts of §3 removed and nothing else changed: exactly six artifacts leave, the two named and the four the starter brought, `spring-ai-autoconfigure-vector-store-chroma`, `spring-ai-autoconfigure-vector-store-observation`, `spring-ai-chroma-store` and `spring-ai-vector-store`. Every other line of the tree is the same; `spring-web`, which the Chroma store also brought, stays through another path. `spring-ai-spring-boot-testcontainers` lists Chroma's auto-configuration and store among its dependencies only as optional ones, so it brings neither back.

So **no vector-store auto-configuration is left on the classpath**, Chroma's or its observation, and none needs excluding. The project has no `spring.autoconfigure.exclude` and no `@EnableAutoConfiguration(exclude …)`, so no exclusion exists for Chroma to take away. The one Chroma key, `spring.ai.vectorstore.chroma.initialize-schema`, goes with the block (§3).

**`spring.ai.vectorstore.type: none` is not set either.** `application.yaml` names the model kinds no starter serves, set to `none`, so that a starter added later is not switched on by default. A vector store is not that case: none can arrive except by a record that adds its starter on purpose, and a key for a store that is not there would tell a reader one exists. `ChromaIsRemovedTest` holds the configuration to no `spring.ai.vectorstore` key at all.

### 6. The records that name Chroma

Earlier records are not edited in their text. Where this record amends one, that record carries a block at its top pointing here.

| Record | What it says of Chroma | Here |
| --- | --- | --- |
| ADR-142 | The whole record: the store connects on first use, and stays configured for its first reader. | **Superseded.** Block at its top. |
| ADR-039 | Chroma is derived and disposable; SQLite is authoritative. | **Amended** in its Chroma half. Block at its top. |
| ADR-032 | The ANN index is rebuildable. | **Amended**: there is no index. Block at its top. |
| ADR-085 | Chroma is kept on speculation, and the next map must justify it or remove it. | **Amended**: removed, obligation discharged. Block at its top. |
| ADR-158 | The operator's `up` starts Chroma, Ollama and docling-serve; Chroma's contents go with its container. | **Read with** this record: the operator starts two services. Block at its top. |
| ADR-164 | All three services restart unless stopped; Chroma's own bullet in §1 and in Consequences. | **Read with** this record: the rule holds for the two that remain, and `SidecarRestartPolicyTest` reads it off whatever services `compose.yaml` runs. Block at its top. |
| ADR-165 | Its header's *"starts all three"*; §2's bullet that Chroma does no inference. Its measurements ran with Chroma in the file. | **Read with** this record: `compose.yaml` alone starts two services, and §2's bullet has no object. Its measurements are history. Block at its top. |
| ADR-179 | §5: *"Chroma stays without a volume"*; Context names `VectorStoreIsReachedOnFirstUseTest`; a test note names the Chroma connection-details class. | **Read with** this record: there is no Chroma to keep a volume for. Its Context is what was measured then. Its test note is corrected in place in step A (§3). Block at its top. |
| ADR-001 | *"Java + Spring Boot + Spring AI + a vector database, given as input"*. | **Amended**, by the operator's answer of 2026-10-09 (§9, question 1): the vector database leaves the fixed stack. Block at its top. |
| ADR-020 | *"Needs no vector DB at scoring time."* | **No block.** True as it was; this record rests on it. |
| ADR-045 | Resolves ADR-039's open sizing condition. | **No block.** The clustering decision stands, and its sizing argument stands without the projection. |
| ADR-046 | The pom carries what a recorded decision requires. | **No block.** Applied, not amended. |
| ADR-009 | Not a ban on a separate vector store. | **No block.** Kept: a later record may add one. |
| ADR-071 | `compose.yaml` and `TestcontainersConfiguration` *"currently declare only Chroma and Ollama"*; Docling's lifecycle *"mirrors Chroma and Ollama"*. | **No block.** A description of the files as they stood on 2026-09-03, and a lifecycle ADR-158 and ADR-179 have since restated without Chroma. |
| ADR-084 | Whether Chroma is populated at all is deferred to #81. | **No block.** A deferral, answered by ADR-085 and now by this record. |
| ADR-110 | Its answer table is *"ADR-039's shape one instrument along"*. | **No block.** An analogy to SQLite being authoritative, which stands. |
| ADR-153 | The whole-job slice needs neither Chroma nor a live Ollama. | **No block.** A description of the tests as they were measured. |

### 7. What the operator does

**A working directory needs nothing.** Nothing in it was ever for Chroma: `schema.sql` declares no table for it, no run's identity carries a Chroma setting, and the store was never written to. The database, the caches and the labels are kept as they are. What moves is the run ids of six stages (§8), which is not a step the operator takes.

**A machine that ran the sidecars before this change still holds a Chroma container.** Worked out from how Docker Compose behaves, not run, since no Docker daemon was available to this record:

- After `git pull`, the README's `docker compose -p vespera up -d --build` (with `-f compose.yaml -f compose.gpu.yaml` on a GPU machine) leaves the container `vespera-chroma-1` running, because Compose does not remove a service that has gone from the file. It warns that it found an orphan container and names `--remove-orphans`. The container keeps its `restart: unless-stopped`, so it comes back after a reboot and keeps port 8000.
- **Ollama and docling-serve are not replaced** by that `up`: their part of `compose.yaml` does not change, only a comment moves onto `ollama`, and a comment is not part of a service's configuration. So Ollama keeps its models (they are in a volume since ADR-179 in any case).
- **The operator may leave the Chroma container or remove it.** Nothing reads it and it holds nothing that was ever written, so removing it loses nothing. To remove it, add `--remove-orphans` to the next `up`, once, or run `docker rm -f vespera-chroma-1`. Its image, `chromadb/chroma:1.5.9`, may then be removed with `docker image rm chromadb/chroma:1.5.9`.
- **There is no Chroma volume to remove.** `compose.yaml` never declared one, and Chroma's Dockerfiles at tag 1.5.9 declare no `VOLUME`, so the image creates no anonymous one. This was read from Chroma's source at that tag; the image itself could not be inspected from here (the registry refused an unauthenticated request).
- A Chroma setting the operator added outside the repository, in `application-local.yaml` or as `SPRING_AI_VECTORSTORE_CHROMA_…` in `.env` or the shell, configures nothing once the starter is gone and is not an error. It may be removed.

### 8. The run ids

**The mechanism** (ADR-058, `.mvn/scripts/implementation-versions.groovy`): a module's version is the last commit that touches `src/main/java/io/algernon/vespera/<module>`, and a stage's run id carries the versions of the modules `StageModules` lists for it.

**Read from `StageModules` at `4b99a03`**, six stages name `pipeline`: content census (3, `similarity`, `extraction`, `pipeline`), content redundancy (4, the same), seed measurement and embedding scoring (both runs of 5, `embedding`, `extraction`, `pipeline`), arrangement (6a) and generation (6b) (both `synthesis`, `extraction`, `embedding`, `pipeline`). Byte-level reduction (1) names `corpus` alone and extraction (2) names `extraction` and `similarity`; the census records a walk, which has no implementation version.

**Removing `VectorStoreConfiguration` moves `pipeline`'s version, so the run ids of stages 3 to 6b move.** At the first `vespera run` of a build that carries it, over an existing working directory, each of those stages is minted anew and runs again: stages 3 and 4 over the conversions stage 2 already cached, stage 5 over vectors the cache already holds (a vector has no run id, ADR-085), so it re-scores and re-clusters without embedding again; 6a names the arrangement anew, so `arrangementApproved` is written again; and 6b generates every cluster again under its new run. The labels survive, being keyed by path and seed set (ADR-097). Stages 1 and 2 do not move.

**No shape of the change avoids it.** The class names `VectorStore`, which leaves the classpath with `spring-ai-vector-store` (§5), so it cannot stay once the starter goes, and keeping the starter to keep the class is what this record removes. The removal is a commit under `pipeline`'s path, whatever else it carries. Only leaving everything as it was moves nothing. `application.yaml`, `compose.yaml`, the pom and the workflow are outside every module's path, and so are this record's own commit and every test and documentation edit: none of them moves a run id.

**The operator accepted that cost on 2026-10-09, and it is paid once** (§9, question 2).

### 9. Put to the operator, and answered on 2026-10-09

This record was first committed with these three points open. The operator answered all three on 2026-10-09, each as recommended.

1. **ADR-001.** It fixes the stack as *"Java + Spring Boot + Spring AI + a vector database, given as input, not derived"*, and `docs/architecture.md` §2 opens *"Fixed as an input constraint (ADR-001)"*. Removing Chroma leaves no vector database in the stack, so this record either amends ADR-001 or contradicts it. The other reading, that SQLite's `vector` table is the vector database ADR-001 meant, was not recommended: ADR-009's own clarification says a relational store and a vector store serve different purposes, and ADR-039 named Chroma as the latter. **Answered: this record amends ADR-001.** The fixed stack is Java, Spring Boot and Spring AI, every vector lives in SQLite, and a vector database comes back only through a record of its own (§1; ADR-009 as clarified). ADR-001 carries a block at its top that points here.
2. **When the run ids move.** The implementation moves the run ids of stages 3 to 6b (§8): one more pass of stages 3 to 6b over an existing working directory, 6b's generation calls included, and one more approval of the arrangement. [#456](https://github.com/algernon28/vespera/issues/456)'s implementation moves the same stages. **Answered: the cost is accepted, and it is paid once.** #456's branches are not on the remote, so this record's implementation is built on its own branch, not on top of #456's. Instead, the pull request that carries it and #456's are merged back to back, and the operator makes no `vespera run` with a build that carries one of the two changes without the other. So the run ids of stages 3 to 6b move once for both.
3. **Whether the README tells the operator about the leftover container.** The README describes how the tool is driven and carries no history (ADR-098); §7 is a one-time step. **Answered: the README does not.** §7 is the only place the step is written. Compose's own warning names `--remove-orphans` at the first `up`.

### 10. The order of the work, and who changes each test

**This record's commit**: this file, the blocks of §6 on ADR-142, ADR-039, ADR-032, ADR-085, ADR-158, ADR-164, ADR-165 and ADR-179, its row in `docs/adr/README.md` and the range line there, the ADR count in `AGENTS.md`, `Adr.CHROMA_IS_REMOVED_AND_VECTORS_LIVE_IN_SQLITE_ALONE`, and the three test classes of "What pins it", which fail against the tree as it is. Nothing under `src/main`, no pom, no compose file and no workflow. The operator's answers of §9, and the block on ADR-001 they called for, were added in the implementation commit, before its step A.

**The implementation commit**, one commit made in three steps, so that the build is never blocked:

- **A, `analyst`, first.** The test-side edits in the table below. Test sources still compile, because the starter and the test container are still in the pom, and the suite stays as green as it was: the store is still lazy, so nothing that dropped its closed Chroma port reaches a Chroma.
- **B, `spec-implementer`.** §3's production and build table, and the suite run green. `compose.yaml` and `build.yml` are neither `src/main` nor a test nor Markdown; they are `spec-implementer`'s, under this record, as the pom is. B cannot come before A: once the two artifacts leave the pom, `TestcontainersConfiguration`, `VesperaApplicationIT` and `VectorStoreIsReachedOnFirstUseTest` no longer compile, and `spec-implementer` does not edit tests.
- **C, `analyst`, last.** §3's documentation table, `node docs/render-docs.mjs`, and both docs gates. C cannot come before B: `docs/check-claims.mjs` checks the README's services and ports against `compose.yaml`, so the README can describe two services only once `compose.yaml` runs two. Between B and C that check fails, which is why the three steps land as one commit.

| Test file | What changes in step A |
| --- | --- |
| `VectorStoreIsReachedOnFirstUseTest` | **Deleted.** Its second and third claims, a refused connection and the datasource not being lazy, have no object. Its first, that the whole application starts under `./mvnw test` with no sidecar, is held by `TheApplicationStartsWithNoVectorStoreTest.theApplicationStarts`. |
| `VesperaApplicationIT` | The test `askedForTheVectorStoreConnectsToTheRunningChroma` and the `VectorStore` import go. `contextLoads`'s display name becomes *"The application starts with Ollama and the document converter running beside it"*. The javadoc drops the sentences on the vector store and on `VectorStoreIsReachedOnFirstUseTest`. The links to ADR-039 and ADR-142 go; those to ADR-011, ADR-012 and ADR-013 stay. |
| `TestcontainersConfiguration` | The import of `ChromaDBContainer`, `CHROMA_IMAGE` and the bean `chromaContainer` go. The javadoc's Chroma item goes, *"Two roles"* and *"Unlike the two above"* are reworded for the two containers that remain, and *"both are pinned"* stays true of them. |
| `TestVesperaApplication` | Javadoc only: *"throwaway Chroma, Ollama and docling-serve containers"* becomes *"throwaway Ollama and docling-serve containers"*. |
| `SidecarsAreStartedByTheOperatorTest` | `ChromaDockerComposeConnectionDetailsFactory` leaves the list of classes that read containers back, its javadoc says *"the class that reads Ollama back"*, and the second claim of `anIdeRunStartsNoContainer` reads *"and neither is Spring AI's support for reading the model server back from containers that support started"*. ADR-179's note on the test is corrected with it (§3). |
| `CliExitIT` | The argument `-Dspring.ai.vectorstore.chroma.client.port=…`, the `closedPort()` helper and the imports only it used go. The javadoc's paragraph on pointing the jar at a closed Chroma port goes. The link to ADR-142 goes; the link to ADR-141 stays. |
| `ProfileThatDoesNotLoadIT` | The same argument, helper and imports go, and the javadoc's *"The jar is pointed at a Chroma on a port nothing listens on and starts no compose sidecar"* becomes *"The jar starts no sidecar"*. |
| `pipeline/WorkingDirectoryInUseIT` | The same argument, helper and imports go, and the javadoc's *"The jar is pointed at a Chroma on a port nothing listens on and starts no sidecar"* becomes *"The jar starts no sidecar"*. |
| `pipeline/CascadeSliceTest` | Javadoc only: *"neither Chroma nor a live Ollama nor a Docling sidecar"* becomes *"neither a live Ollama nor a Docling sidecar"*, and *"the whole application starts Chroma and Ollama"* becomes *"the whole application builds Ollama's clients"*. |
| `pipeline/CensusInvocationTest` | Javadoc only: *"the whole application starts Chroma and Ollama and this question does not involve either"* becomes *"the whole application builds Ollama's clients and this question does not involve them"*. |
| `src/test/resources/application-test.yaml` | The comment's *"which is how the Chroma setting below went missing once already"* becomes *"and every shipped setting would silently stop applying"*; there is no Chroma setting below. |
| `Adr.java` | `CHROMA_IS_DERIVED` and `THE_VECTOR_STORE_CONNECTS_WHEN_FIRST_USED` go, since no test links either once the edits above are made. |

**Recounting the records.** ADR-211, ADR-212 and ADR-213 are taken by changes still in review ([#456](https://github.com/algernon28/vespera/issues/456), [#196](https://github.com/algernon28/vespera/issues/196), [#351](https://github.com/algernon28/vespera/issues/351)). On this record's branch `docs/adr/` holds ADR-001 to ADR-210 and ADR-214, 211 files, and `AGENTS.md` and `docs/adr/README.md` say so. Whichever of the four lands later recounts both lines and adds its row to the index in order.

## Alternatives refused

- **Keep Chroma lazy, as it is.** It is the status quo, kept for a reader that does not exist. ADR-085 recorded it as speculation with an obligation to settle it, and ADR-046 rules it out.
- **Remove the starter and keep `VectorStoreConfiguration`, or the property, for when a store returns.** The class does not compile without `spring-ai-vector-store` (§5), and a property for an absent dependency configures nothing and says a store exists.
- **Keep the starter and switch the store off with `spring.ai.vectorstore.type=none`.** That is the workaround ADR-142 itself refused, and it leaves the dependency this record removes.
- **Drop the starter and keep the compose service.** The operator would start and keep running a sidecar nothing reaches.
- **Set `spring.ai.vectorstore.type: none` as a guard, as the model kinds are.** §5.
- **Leave the removal of the configuration class for later, to move no run id now.** The class cannot outlive the starter (§8), and leaving the starter is leaving everything.

## Consequences

- **The operator starts two sidecars**, Ollama and the document converter, and port 8000 is no longer Vespera's.
- **A command never had to reach Chroma and now cannot**: there is nothing to reach.
- **The integration tests that need Docker start two containers**, not three.
- **Six stages' run ids move with the implementation** (§8), once, and an existing working directory is kept (§7).
- **A vector database comes back only through a record of its own** (§1).
- **`CONTEXT.md` is not edited.** No entry names Chroma or a vector store.
- **`docs/decision-ledger.md` is not edited.** It is closed to edits, and its rows for ADR-039 and ADR-046 are witnesses of what was decided then.

**What the implementation owes** (`spec-implementer`, step B):

- delete `src/main/java/io/algernon/vespera/pipeline/VectorStoreConfiguration.java`;
- `pom.xml`: remove the dependency `org.springframework.ai:spring-ai-starter-vector-store-chroma` and the test dependency `org.testcontainers:testcontainers-chromadb`, and reword the comment above `maven-failsafe-plugin` as §3 says; add, change or remove nothing else;
- `src/main/resources/application.yaml`: remove `spring.ai.vectorstore` and everything under it with its comment lines, and reword the comment above `vespera.docling.base-url` as §3 says;
- `compose.yaml`: remove the service `chroma`; move its restart-policy comment onto `ollama`; reword the header's port list as §3 says; leave `ollama`, `docling-serve` and the top-level `volumes` as they are;
- `.github/workflows/build.yml`: reword the header comment as §3 says;
- run the suite green, steps A's edits being already in the tree; then every test of "What pins it" passes, and no other test changes outcome;
- not the README, `docs/`, `AGENTS.md` or `.claude/`: those are step C's.

**What pins it.** Each class is in `src/test/java/io/algernon/vespera/`, was written with this record, and was run against `4b99a03` with it. None names a type that does not exist, so the test sources compile; the red is at run time.

- `ChromaIsRemovedTest`, six tests, all **red** at `4b99a03`:
  - `thePomDeclaresNoChromaArtifact`: the pom names neither artifact. *Red: it names both.*
  - `noClassThatCameWithChromaIsOnTheClasspath`: Spring AI's `VectorStore`, `ChromaVectorStore`, `ChromaVectorStoreAutoConfiguration`, `VectorStoreObservationAutoConfiguration` and Testcontainers' `ChromaDBContainer` are not on the test classpath, which contains the runtime one. *Red: all five are.*
  - `noShippedClassNamesAVectorStore`: no class compiled from `src/main` names a type under `org/springframework/ai/vectorstore/` or anything named for Chroma, and `pipeline.VectorStoreConfiguration` is not among them. *Red: that class.*
  - `nothingInTheProductionTreeMentionsChroma`: no file under `src/main`, in any case and in comments too. *Red: `VectorStoreConfiguration.java` and `application.yaml`.*
  - `theShippedConfigurationSetsNoVectorStoreProperty`: `application.yaml`, flattened as Spring reads it, has no key under `spring.ai.vectorstore`. *Red: `spring.ai.vectorstore.chroma.initialize-schema`.*
  - `theBuildFilesNoLongerMentionChroma`: `pom.xml`, `compose.yaml`, `compose.gpu.yaml` and `.github/workflows/build.yml`, in any case. *Red: the pom, `compose.yaml` and the workflow.*
- `ChromaIsNotASidecarTest`, two tests:
  - `noComposeFileRunsChroma`: neither compose file declares a service named for Chroma or running its image. *Red: the service `chroma`.*
  - `noComposeFileKeepsAVolumeForChroma`: neither declares or mounts a volume named for Chroma. **Green at `4b99a03` and after**: Chroma never had one; it is there so that one does not come back with a service.
- `TheApplicationStartsWithNoVectorStoreTest`, two tests, starting the whole application in-process under the `test` profile with no Docker:
  - `theApplicationStarts`: the context is active. **Green at `4b99a03` and after**: it takes over the one claim of `VectorStoreIsReachedOnFirstUseTest` that outlives it.
  - `theApplicationDeclaresNoVectorStore`: no bean definition is of a type under `org.springframework.ai.vectorstore.` or `org.springframework.ai.chroma.`, or is named for a vector store or for Chroma; types are read from the definitions without building a bean. *Red: the Chroma store, its client, their auto-configuration and `VectorStoreConfiguration`.*

**Not pinned, and why:**

- what an operator's machine holds (§7): no test sees Docker's state;
- the run ids moving (§8): no test can see a commit;
- that `docs/` describe two sidecars: `docs/check-claims.mjs` holds the README to `compose.yaml` once step C is made, and the architecture document has no guard of its own.
