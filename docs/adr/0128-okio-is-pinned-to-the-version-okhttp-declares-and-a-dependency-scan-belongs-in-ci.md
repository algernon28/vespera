# ADR-128 — okio is pinned to the version okhttp declares, and a dependency scan belongs in CI

- **Date**: 2026-09-19
- **Status**: accepted
- **Adds a dependency under**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) — the pom carries what a recorded decision requires, so a transitive version pinned in `dependencyManagement` wants a record behind it. This is that record.
- **Rests on**: [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) (Lingua is the language detector, and this record keeps it), [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) (what `AGENTS.md` and `README.md` may claim — neither says anything about a dependency scan, and neither gains a claim here).
- **Settled as [#221](https://github.com/algernon28/vespera/issues/221)** under the stage 6a/6b map [#151](https://github.com/algernon28/vespera/issues/151).

## Context

### The finding, and what kind of question it is

A Qodana run on 2026-09-19 flagged `com.squareup.okio:okio:2.10.0` under `VulnerableLibrariesLocal`, carrying **CVE-2023-3635**, CVSS 5.9, reported by Mend.io. The vulnerable code is okio's `GzipSource`: a malformed gzip buffer raises an exception the source does not handle, which is a denial of service for a caller decompressing untrusted input. The fix landed in **okio 3.4.0**, and there is no 2.x release carrying it — 2.10.0 is the last of that line. So the finding cannot be answered by a patch on the 2.x branch; it is either accepted, or the graph is moved to 3.x, or the consumer that pulled 2.x is dropped.

Nothing in `pom.xml` names okio. A scanner's verdict is where the investigation starts, not where it ends: **ADR-046 makes the pom carry what a recorded decision requires, and what a decision requires depends on whether the vulnerable code is reachable**, not on whether the artifact is present. The ticket that raised this asked for reachability first, for exactly that reason.

### The dependency path, as evidence

`./mvnw dependency:tree` shows two independent consumers of okio, and the ticket named only one:

```
com.github.pemistahl:lingua:jar:1.2.2:compile
|  +- com.squareup.moshi:moshi:jar:1.13.0:runtime
|  |  \- com.squareup.okio:okio:jar:2.10.0:compile
|  \- com.squareup.moshi:moshi-kotlin:jar:1.13.0:runtime
```

The second is only visible with `-Dverbose`, because a normal tree hides a losing conflict:

```
org.springframework.ai:spring-ai-starter-model-openai:2.0.0
\- com.openai:openai-java-core:4.39.1
   \- com.squareup.okhttp3:okhttp:jar:4.12.0:compile
      \- (com.squareup.okio:okio:jar:3.6.0:compile - omitted for conflict with 2.10.0)
```

**Maven's nearest-wins selected 2.10.0** (reached at depth three, through Lingua) over **3.6.0** (reached at depth four, through okhttp). That single fact is the whole of the vulnerability's presence: the vulnerable artifact is not here because anything wanted it, but because it won a proximity tie against the version the consumer that actually decompresses gzip declared.

### Reachability, established from the code rather than the scanner

Four readings, each of which can be redone in minutes.

- **Lingua does not touch gzip and never sees corpus bytes.** Its language models ship in the jar as **plain `.json`** — 459 entries, none of them `.gz`. `com.github.pemistahl.lingua.internal.TrainingDataLanguageModel$Companion.fromJson(InputStream)` reads one with `Okio.source(InputStream)`, `Okio.buffer(Source)` and `moshi.JsonReader.of(BufferedSource)`. No lingua class references `GzipSource`; a scan of the extracted jar finds the string nowhere. The detector is asked for a language through `detectLanguageOf(String)`, and Vespera calls it with a `String` (`ExtractionMetrics` passes normalised text), so an archive's bytes reach the detector as decoded text and never as a stream.
- **Moshi does not decompress.** No class in `moshi-1.13.0.jar` references `okio/GzipSource`; it uses okio for buffered characters, not for compression.
- **okhttp does decompress, and that is the arm the ticket missed.** `okhttp3.internal.http.BridgeInterceptor` wraps a `Content-Encoding: gzip` response body in a `GzipSource`, and `okhttp3.internal.publicsuffix.PublicSuffixDatabase` reads a bundled gzip resource. The sentence the ticket offers — *"Nothing in this tree hands okio a gzip stream"* — is true of the corpus and false of the tree: the OpenAI starter's HTTP client does exactly that, against an okio it was not built for.
- **The corpus never reaches okhttp either.** `DoclingClient` is pinned to the JDK `HttpClient` through `RestClient` (ADR-010's client), and no code in `src/main` invokes the OpenAI model at all — the starter is declared ahead of the code that will use it, the same ADR-046 deferral the pom's own comment records. So an archive's `.docx`, `.pdf` and `.txt` bytes reach okio on no path, before or after this decision.

### What the reading changes

The finding is not the corpus-facing exposure the scanner's line implies, and it is not the purely crystallised case the ticket assumed. It is a **graph defect with a latent reachable path**: the vulnerable `GzipSource` is on the classpath, the one consumer that actually calls it (okhttp, for the reference model) declared 3.6.0 and was silently downgraded, and any future code that names the OpenAI model puts network gzip through it. Accepting the CVE as "unreachable" would be true today and would leave both the vulnerability and the version mismatch in place for a change that is already anticipated by the pom.

## Decision

### okio is pinned forward, in `dependencyManagement`

`pom.xml` gains `<okio.version>3.6.0</okio.version>` and a managed dependency on `com.squareup.okio:okio` at `${okio.version}`. The pin is the ADR-046 requirement this record exists to satisfy.

**Why 3.6.0 rather than 3.4.0 or the newest 3.x.** 3.4.0 is the floor the fix landed at, and 3.6.0 is the version **okhttp 4.12.0 itself declares**. The pin therefore does not invent a third version to replace a disputed one; it restores the graph to what the only consumer that decompresses gzip was compiled against. A newer 3.x would satisfy the CVE and put okhttp back in the same "running against a version it was not built for" position the pin exists to end.

### The risk the ticket named was measured, not assumed

The ticket warned that `moshi:1.13.0` was built against okio 2.x, and that a forward pin would be running it against a version it never saw. That is the measurement to make before pinning, and it was made. With okio pinned to 3.6.0:

- `./mvnw dependency:tree -Dincludes=com.squareup.okio:okio` resolves to `com.squareup.okio:okio:jar:3.6.0:compile`.
- `./mvnw test` reports **Tests run: 571, Failures: 0, Errors: 0, Skipped: 0**, against a baseline of the same 571 on the unpinned tree.
- The path that would break is exercised rather than merely compiled: `LanguageDetectionTest` constructs a `LanguageDetection`, which builds a `LanguageDetector` over all languages and loads **every bundled model** through moshi on the pinned okio, then asserts the detected labels.

The observation is left as a test rather than as this paragraph, so a future pin that reintroduces a vulnerable version goes red.

### A dependency scan belongs in CI, as an advisory and never as a gate

Qodana is an IDE-local run today: nothing under `.github/workflows` executes it, so no build goes red on a finding and nobody is told when the next one appears. That gap is why a finding reached a ticket instead of a check. **A dependency scan belongs in CI.** It reports; it does not gate, because a scanner's verdict is not evidence of reachability, and a build made red by an unreachable transitive CVE is the same category error this record refuses. What a finding earns is a look at its path — the work above — and, where a real path exists, a record.

**The mechanism is GitHub's dependency graph with Dependabot security alerts enabled in repository settings.** That adds no build dependency and no workflow, and it surfaces a finding as an advisory rather than a gate — which is the position above. Enabling it is a repository setting rather than a diff, so this record names it instead of shipping it in a workflow.

### What this records, and what it does not

It records the reachability reading, the pin, the version choice, the measurement, and the CI position. It does **not** decide to drop or replace Lingua: ADR-073 chose it deliberately, and the okio conflict would vanish with it — but that is a language-detection decision, not a dependency-hygiene one, and it is out of scope for this ticket exactly as the ticket says.

## Consequences

**`pom.xml` gains one property and one managed dependency**, both carrying the ADR-128 reference, so the reason travels with the pin.

**A pin test guards it.** `DependencyPolicyTest` reads `pom.xml` and fails if the managed okio version is absent or below the 3.4.0 the fix landed at, so the property the record defends cannot silently disappear. It does not demand the exact 3.6.0: a later pin above the floor is a compatible change, and a test that forbade one would be pinning a number rather than a decision. It follows ADR-052's conventions, links ADR-128 and #221, and says nothing about third-party internals.

**The next graph change is the reopen trigger, and there are three of them.** This record stops describing the tree if any of these happens, and the reading above is redone rather than assumed:

1. `lingua`, `moshi` or `okhttp` is upgraded, or anything else begins to depend on okio, so the conflict that selected 2.10.0 can re-resolve;
2. Lingua gains a file or network surface beyond the resources in its own jar, so its input is no longer data this build shipped;
3. any code hands okio a stream from the corpus, from an operator file, or from a network endpoint the pipeline does not itself ship — the reference model's gzip decompression included.

**The scanner's line was answered with a path, and that is the precedent.** The finding was true as a fact about an artifact and false as a description of the threat; the record says both.

**`AGENTS.md` and `README.md` carry no claim about dependency scanning**, and none is added: nothing in them says a scan is in place, and no sentence is made inaccurate by this record. ADR-098's division is untouched — what changed is the ADR count line in `AGENTS.md`, and this record does not describe project state anywhere else.

### The mechanism, and why it is an operator step rather than a diff

The scan is GitHub's dependency graph with **Dependabot security alerts**, switched on in repository settings; it leaves nothing in the tree. A pull-request-time check was refused for now: `actions/dependency-review-action` sees only the change as it is proposed, so it cannot find a CVE published after the last commit that touched a dependency — the case a scan exists for — and a PR check is the shape that drifts toward gating, which this record's Decision refuses. Moving to one later reopens this record, because it changes both the scan's scope and its failure posture.