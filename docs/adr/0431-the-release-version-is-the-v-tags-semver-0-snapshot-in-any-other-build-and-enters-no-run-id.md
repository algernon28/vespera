# ADR-431 — The release version is the `v` tag's SemVer, `0-SNAPSHOT` in any other build, and enters no run id

- **Date**: 2026-11-20
- **Status**: accepted on 2026-11-20.
- **Decided**: by the operator, on [#904](https://github.com/algernon28/vespera/issues/904): that Vespera's version is SemVer, read by the operator, and what makes a major, a minor and a patch release (§1); that the tag is its source, the pom carrying Maven's `${revision}` at a fixed default of `0-SNAPSHOT` and a release build being given the tag's version (§2); that it never enters a run id, which stays keyed by the modules' commits alone (§3); where it shows (§5); that a release is cut on demand by pushing a `vMAJOR.MINOR.PATCH` tag, and a workflow on that push builds it and publishes a GitHub release with the jar attached and notes listing the records merged since the previous tag (§6, §7); what the workflow refuses (§8); that `v1.2` is the baseline and the first release is 2.0.0, `pre-220` is no release, and there is one branch, `main` (§9); and that the ledger keeps the release version in a new table, `run_release`, a row for each release that minted or continued a run, raising no schema version (§11). This record's own: the measurement of §2 and what follows from it, how the jar reads the version and what it says where none is named (§4), the words each place in §5 shows it in, the release notes' form and the previous tag's choice (§7), the refusal of a jar that could not mint a run (§8), the full clone (§6), the script holding the release's rules (§8), and `run_release`'s columns and key (§11).
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, its row `vespera`, *"the jar's version, as its manifest names it"*: the release version as §4 reads it, `unknown` where no manifest names one, so never `null`; `format` stays `2` (§5). It carries a note at its head.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §3, the marker's *"two lines"*: three, the third `vespera=<release version>`; a marker of two lines, as a tree drawn before this record carries, is read as before (§5). It carries a note at its head.
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), its Context's refusal of *"Maven project version"* as a run's input among them, and [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §7: a stage's implementation version is the last commit touching its modules, `implementation-versions.properties` holds a line for each module and nothing else, and no run id moves (§3, §10).
- **Amends**: [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) §11, *"The tables keyed by a run are thirty-eight"*, as [ADR-429](0429-the-profile-may-list-the-topics-by-name-a-survivor-going-to-the-name-its-chunks-are-nearest-and-none-listed-infers-them.md) leaves it at forty: forty-one, `run_release` the forty-first (§11). It carries a note at its head.
- **Amends**: [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), whose tables keyed by a run, and what each keeps of every run, gain `run_release`, of a row for each release that minted or continued a run and from which nothing deletes (§11). It carries a note at its head.
- **Keeps**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): the pom gains no plugin and no dependency; `${revision}` is Maven's own (§2).
- **Keeps**: [ADR-049](0049-verdict-rows-and-schema-versioning-without-a-migration-tool.md), [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) and [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md): `schema.sql` gains one table, `run_release`, on which no existing table depends, so no module's schema version moves and a database written before it opens as it did (§11).
- **Read with**: [ADR-272](0272-a-module-whose-answer-is-the-boundary-of-a-shallow-clone-is-written-as-unknown.md), by which a jar built from a shallow clone writes `unknown` for a module and refuses to mint its runs: the release is built from a full clone, and a jar with an `unknown` is refused (§6, §8). [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, as ADR-342 §3 leaves it: the control panel reads the version with its own code (§4).
- **Settles**: [#904](https://github.com/algernon28/vespera/issues/904).

## Context

**Two numbers have been called Vespera's version, and neither says which Vespera an operator has.** The pom's `<version>` is `0.0.1-SNAPSHOT` and has never moved; the jar is named after it, and its manifest's `Implementation-Version` carries it. The repository holds two tags, `v1.2`, whose number appears nowhere in the pom, and `pre-220`, a marker; it holds no GitHub release. Each stage's implementation version (ADR-058) is a separate thing: the last commit touching that stage's modules, written by the build into `implementation-versions.properties` and hashed into each of its runs' ids. Squash merges land on `main` by hand and nothing is tagged then.

**What reads a version today.** `report.json`'s `vespera` is `InvocationReport`'s package's `Implementation-Version`, `null` where the classes lie in no jar (ADR-362 §3). `vespera --version` exists, through picocli's `mixinStandardHelpOptions`, and prints nothing, `VesperaCommand` naming no version. The control panel's title is *Vespera control panel*; it has no About. The deliverable's marker holds two lines, the working directory and the generation run (ADR-276 §3).

**What the build does with `${revision}`, measured** with Maven 3.9.16, the wrapper's, on a clone whose pom read `<version>${revision}</version>` with the property `revision` at `0-SNAPSHOT`, the tests skipped:

| Build | Jar | Manifest's `Implementation-Version` | `implementation-versions.properties` | Pom installed in the local repository |
| --- | --- | --- | --- | --- |
| given `-Drevision=2.0.0` | `target/vespera-2.0.0.jar` | `2.0.0` | one line a module, as before | `<version>${revision}</version>`, the property at `0-SNAPSHOT` |
| given none | `target/vespera-0-SNAPSHOT.jar` | `0-SNAPSHOT` | the same | not installed |

Spring Boot's parent writes `Implementation-Version` with no configuration of this pom's. Inside the jar so built, `VesperaCommand`'s package answered `getImplementationVersion()` with `2.0.0`, and Spring Boot's start line named `v2.0.0`. The pom installed keeps `${revision}` unresolved, so a build depending on that pom would read `0-SNAPSHOT`: the flatten plugin exists to resolve it. Nothing depends on Vespera's pom, nothing installs or deploys it to a repository another build reads, and a release here publishes a jar.

## Decision

### 1. The release version

**Vespera's release version is SemVer, `MAJOR.MINOR.PATCH`, for the operator to read.** It is provenance: it says which Vespera did something, and decides nothing.

| Part | Raised by |
| --- | --- |
| MAJOR | an existing working directory, profile or deliverable no longer working as before: a profile key removed or renamed, a schema change by which an older database cannot be opened, a deliverable layout that breaks links, a raised JDK requirement |
| MINOR | a new feature, profile key or stage behaviour; and, at least, any bump of a pinned image, `vespera.docling.image` among them, or of a major dependency, Spring Boot or the JDK |
| PATCH | fixes |

**A run id moving is no signal of its own**: which stages re-run is ADR-058's, and a release says nothing of it.

### 2. The tag is its source, and the pom reads it

- **The pom's `<version>` is `${revision}`**, and its `<properties>` hold `<revision>0-SNAPSHOT</revision>`, a default that never changes. No release edits the pom.
- **A release build is given `-Drevision=<the tag without its v>`**: the tag `v2.0.0` builds `vespera-2.0.0.jar`, whose manifest's `Implementation-Version` is `2.0.0`. Every other build, a developer's, CI's and an IDE's, is `0-SNAPSHOT` and its jar `vespera-0-SNAPSHOT.jar`.
- **No flatten plugin.** The pom installed keeps `${revision}`, which matters only to a build that depends on it, and none does: a release publishes the jar alone (§6). A record that publishes the pom to a repository decides the plugin then.

### 3. It enters no run id

**The release version never enters a run id, a run's `config_consumed`, a stage's implementation version, or any instrument's identity or cache key.** A run id stays the hash of ADR-048's four things, its implementation version being the modules' commits alone (ADR-058, ADR-216). `implementation-versions.properties` keeps a line for each module, named for its folder, holding a commit or `unknown`, and nothing else. **No class of a module any stage's version names, `corpus`, `extraction`, `similarity`, `embedding` or `synthesis`, reads the release version**, and nor do `RunId`, `ImplementationVersions` and `StageRuns`, through which a run's id is derived. `Runs` takes it as a text and writes it beside the run once the id is derived, and `RunMint` hands it over (§11).

So two jars of one commit, `0-SNAPSHOT` and `2.0.0`, mint and continue the same runs, and a release that touches no stage's module re-runs nothing.

### 4. How the jar reads it

**`ledger.ReleaseVersion` reads it from the manifest of the jar it runs from**, `Implementation-Version`, through its own package's `getImplementationVersion()`. **Where none is named**, as for classes run outside a jar by a test or an IDE, or a value that is blank, **the release version is `unknown`**. Nothing refuses for it, being provenance: `unknown` is shown where a version would be. It lives in `ledger` because every module that shows it may name `ledger` and no other (ADR-040), and `ledger` is a module no stage's version names (ADR-216 §7).

**The control panel reads the same manifest with its own code**, its own package's `getImplementationVersion()`, with the same `unknown`: it links no class of the jar (ADR-285 §3, as ADR-342 §3 leaves it), and it runs from the same jar.

### 5. Where it shows

| Where | What it shows, for `2.0.0` |
| --- | --- |
| `vespera --version` | one line, `Vespera 2.0.0`, through a version provider on `VesperaCommand`; `--version` still takes no lock and touches no folder (ADR-342 §2, ADR-395) |
| the jar's manifest | `Implementation-Version: 2.0.0` (§2) |
| the control panel's title | `Vespera control panel 2.0.0` |
| the control panel's About | the line `Vespera 2.0.0`, the About's form being undecided (What this does not decide) |
| `report.json` | `vespera`, `"2.0.0"`; `"unknown"` where §4 reads none, never `null`. No field is removed, renamed or retyped, so `format` stays `2` (ADR-362 §3) |
| `vespera-deliverable.txt` | a third line, `vespera=2.0.0`, after the working directory's and the generation run's, ending in `\n`. A marker is read by its two keys, as before, a line of another key being passed over, so a marker of two lines and one of three are read alike (ADR-276 §3) |
| the deliverable's `index.html` | one line at its foot, *Made with Vespera 2.0.0.* |
| the website rendering and the Confluence rendering | the same line at the foot of the top page of each, the website's `index.html` and the Confluence rendering's top page, and of no other page, so a new release changes one page of each tree a publish updates |
| the ledger | `run_release`, beside the run whose `implementation_version` the stage's is: a row for each release that minted or continued the run (§11) |

A tree drawn again under the same generation run by a later release carries the later release's version. The deliverable's line, and every place but `report.json` and the marker, are for a reader outside this project, so they say *version*.

### 6. A release

**A release is cut on demand: the operator pushes a `vMAJOR.MINOR.PATCH` tag on a commit of `main`.** Nothing is tagged at a merge.

**A workflow, `.github/workflows/release.yml`, runs on the push of a tag matching `v*`**, and nothing else starts it. It:

1. checks out the tag **with its whole history**, `fetch-depth: 0`, so that no module's implementation version is `unknown` (ADR-272);
2. refuses by §8's first and third rules;
3. builds as the Linux job of `build.yml` builds, given `-Drevision=<version>`, so a released jar is one the suite passed;
4. refuses by §8's second and fourth rules, reading the jar's manifest and its `BOOT-INF/classes/implementation-versions.properties`;
5. creates the GitHub release named for the tag, `vespera-<version>.jar` attached, its notes §7's.

It needs `contents: write` and no secret of the operator's.

### 7. The release notes

**The notes list the decision records merged since the previous tag**, read off the index of records, `docs/adr/README.md`, the condensed `docs/decision-ledger.md` being closed at ADR-049: a row of the index at the tag that the index at the previous tag does not hold, by its number, in ascending order. Each is one line, `- [ADR-NNN](<the record's file at the tag on GitHub>): <title>`, its title the row's third cell without the italic summary in brackets that follows it. They begin `Decision records merged since <previous tag>:` and a blank line; where there is no previous tag, `Decision records in this release:`, every row of the index following; where no row is new, the notes are the one line `No decision record was merged since <previous tag>.`

**The previous tag** is the greatest tag below this one among those reading `v` and two or three numbers separated by full stops, `v1.2` read as `1.2.0`, so that `v1.2` is the baseline of 2.0.0. A tag of another shape is no release and is passed over.

### 8. What the workflow refuses

The rules are held in one script, `docs/release.mjs`, which the workflow calls and `src/test/docs/release.test.mjs` tests, so that the workflow's text carries no rule of its own. Each refusal fails the workflow with one line naming the tag, the version or the module it refuses, and publishes nothing.

1. **A `v` tag that is not `v` and three numbers, `vMAJOR.MINOR.PATCH`**, each `0` or a number not beginning with `0`, with nothing after: `v1.2`, `v2.0.0-rc1`, `v02.0.0` and `v2.0.0.1` are refused.
2. **A version that resolves to anything but `MAJOR.MINOR.PATCH`**, `-SNAPSHOT` among them: the jar's manifest must name the tag's version.
3. **A tag on a commit not reachable from `main`**, as `git merge-base --is-ancestor <commit> origin/main` answers.
4. **A jar any of whose implementation versions is `unknown`**, which could mint no run (ADR-058, ADR-272).

### 9. The tags there are, and the branches

**`v1.2` stays as history and is the baseline**; the first release under this record is **2.0.0**. **`pre-220` stays a marker**: it has no `v`, so the workflow never starts for it. The workflow starts only for a tag pushed, so `v1.2`, already pushed, is not built again. **There is one branch, `main`**, and no maintenance branch; a release's patch is a later release.

### 10. The modules, and run ids

The pom, as §2 says; a new class of `ledger`, `ReleaseVersion`; in `pipeline`, `VesperaCommand`'s version provider and `InvocationReport`; in `deliverable`, `DeliverableRoot`'s marker and the index's line; in `confluence` and `website`, the top page's line; in `ui`, `ControlPanelWindow`'s title. **No stage's version names `ledger`, `pipeline`, `deliverable`, `confluence`, `website` or `ui`** (ADR-216 §7, ADR-222, ADR-274 §5, ADR-333 §1), **so no run id moves.** `schema.sql` gains `run_release` and no schema version moves (§11), and `report.json` stays of format 2.

### 11. The ledger keeps every release that minted or continued a run

**A table of `ledger`'s, `run_release`**, beside `run`, whose `implementation_version` is the stage's:

```
-- owner: ledger
CREATE TABLE IF NOT EXISTS run_release (
    run_id TEXT NOT NULL REFERENCES run (id),
    release_version TEXT NOT NULL,
    PRIMARY KEY (run_id, release_version)
);
```

- **Written whenever a run is minted or continued**: `RunMint.mint`, once `Runs.startRun` has returned the run's id, calls `Runs.recordRelease(RunId, String)` with `ReleaseVersion.ofThisJar().value()`, an `INSERT OR IGNORE`. So a run keeps each release that worked under it once, `unknown` and `0-SNAPSHOT` among them, and none twice; `vespera label` and `vespera publish`, which mint no run, write none.
- **It is written after the id and never read to derive one**: the id is ADR-048's hash of the four things `startRun` is given, and continuing a run under a second release finds the same row of `run` (§3).
- **Nothing deletes from it**, a step discarding its own work (ADR-116, ADR-118, ADR-230) deleting the rows of its step's tables and not its run's record, as of `finished_step`; it grows by a row of two short texts for each run and each release that worked under it.
- **No schema version moves**: a new table on which no existing table depends (ADR-197), which `CREATE TABLE IF NOT EXISTS` adds to a database written before it; a run minted before it has no row, and keeps none until an invocation continues it.
- **Its primary key leads with `run_id`**, so the reference to `run` is indexed as ADR-173 has every reference indexed, with no `CREATE INDEX` of its own, and `EveryStatementThatSortsIsRecordedTest`'s list of the schema's indexes is unchanged. No shipped class reads it: an operator or a support question reads it with SQLite.
- **The tables keyed by a run are forty-one**: ADR-409 §11's thirty-eight, ADR-429's `listed_topic` and `winning_topic_score`, and this one.

## Alternatives refused

- **The pom's version edited and committed at each release, then tagged.** A tag and a pom could disagree, and a commit would exist only to move a number; with the tag the source, they cannot.
- **A version computed from commits, by a plugin such as jgitver or git-commit-id.** A new plugin under ADR-046 for what one property does, and a version no person chose, where the operator reads it as a statement of what changed.
- **The release version in a run's id, or in its implementation version.** ADR-058 refused the Maven version there: a hand-moved number can lie, and a release that touches no stage would re-run every stage.
- **The flatten plugin.** It resolves `${revision}` in a pom nothing reads (§2).
- **A filtered resource holding the version beside the manifest**, so that classes run outside a jar would read `0-SNAPSHOT`. Two places the build writes one value into, and a test of the classes would read a version no jar carries; `unknown` says what is true of them.
- **A release at each merged change, or at each closed map.** A version would move for changes no operator wants to install; on demand is the operator's.
- **The version on every page of the deliverable and the renderings.** Every page of every published tree would change with each release, for a line a reader needs once.

## Consequences

- An operator can say which Vespera wrote a working directory's `report.json`, drew a tree or built the jar in hand, and read from the number whether a working directory, profile or deliverable still works as before.
- A developer's jar is `target/vespera-0-SNAPSHOT.jar`; the README's command lines and `ui.ChildRuntimeTest`'s jar name say so.
- The release workflow's build takes as long as CI's Linux job, Docker's integration tests included.
- ADR-362, ADR-276, ADR-409 and ADR-229 carry a note at their heads.

## Known limits

- **Classes run outside a jar show `unknown`**, in a test, from an IDE and in `report.json` of such a run.
- **A tree drawn by an earlier release carries no version** until it is drawn again.
- **A tag deleted and pushed again** starts the workflow again, and the release it would create already exists; the workflow fails there, and nothing is published twice.
- **The release's build depends on GitHub's runners**, Docker among them, as CI's does.

## What this does not decide

- **The control panel's About**: where it opens from and what else it says. Its version line is §5's.
- **Whether a release's notes say more than the records**, the pull requests' titles for example.
- **Whether the pom is ever published to a repository**, and with it the flatten plugin (§2).

## Tests

| Method | What it holds |
| --- | --- |
| `TheReleaseVersionIsTheTagsTest.thePomsVersionIsTheRevisionWhoseDefaultIsZeroSnapshot` | §2: the pom's `<version>` is `${revision}` and its property `revision` is `0-SNAPSHOT` |
| `TheReleaseVersionIsTheTagsTest.theImplementationVersionsNameEachModuleByACommitAndNothingElse` | §3: the built `implementation-versions.properties` holds one key for each module folder under `engine` and `publish` and no other, each a commit or `unknown` |
| `engine.ledger.ReleaseVersionTest.theManifestsVersionIsTheReleaseVersion` | §4: a manifest naming `2.0.0` reads `2.0.0`, and `0-SNAPSHOT` reads `0-SNAPSHOT` |
| `engine.ledger.ReleaseVersionTest.noVersionNamedIsUnknown` | §4: none named, and a blank one, read `unknown`; and the classes under test, which lie in no jar, read `unknown` |
| `engine.pipeline.NoRunIsMintedFromTheReleaseVersionTest.noClassARunIsMintedThroughReadsTheReleaseVersion` | §3: `ReleaseVersion` exists; no class of a module `StageModules` names, nor `RunId`, `Runs`, `ImplementationVersions` or `StageRuns`, depends on it or calls `Package.getImplementationVersion` |
| `engine.pipeline.VesperaPrintsItsVersionTest.theVersionLineNamesVesperaAndItsReleaseVersion` | §5: `VesperaCommand` names a version provider, whose one line is `Vespera unknown` for the classes under test |
| `engine.deliverable.TheMarkerNamesTheVersionThatDrewTheTreeTest.theMarkersThirdLineIsTheReleaseVersion` | §5: a tree's marker is the working directory's line, the generation run's and `vespera=unknown`, each ending in a line feed |
| `engine.deliverable.TheMarkerNamesTheVersionThatDrewTheTreeTest.aMarkerOfTwoLinesIsStillThisWorkingDirectorysTree` | §5: a named root whose marker has two lines naming this working directory, or three, is drawn in, and one naming another working directory is refused |
| `ui.TheWindowsTitleNamesTheVersionTest.theTitleIsTheControlPanelAndTheReleaseVersion` | §5: the window's title is `Vespera control panel unknown` for the classes under test |
| `engine.ledger.ARunKeepsEveryReleaseThatMintedOrContinuedItTest.aRunKeepsEachReleaseOnceAndItsIdDoesNotMove` | §3, §11: a run begun under `0-SNAPSHOT` and continued under `2.0.0`, twice, keeps one id and the two rows `0-SNAPSHOT` and `2.0.0` in `run_release` |
| `EveryTableKeyedByARunIsOnRecordTest.everyShippedDeleteIsOfOneRunsRows` | §11: `run_release`, `ledger`'s, is a table keyed by a run, and one of the eleven no shipped statement deletes from |
| `engine.pipeline.ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest.anArchiveChangedAndChangedBackIsANewWalkAndTheFirstWalksRunsAreNotArrivedAtAgain` | §11: after the first invocation `run_release` holds rows under the run of every stage reached, and none of them moves when later invocations work over later walks |
| `src/test/docs/release.test.mjs` | §6 to §8: the tag's version and the tags refused; a version not `MAJOR.MINOR.PATCH` refused; a jar whose manifest names another version, or with an `unknown` implementation version, refused; the previous tag, `v1.2` the baseline of `v2.0.0`; the notes in their three forms; a commit reachable from `main` and one that is not |

## What the implementation owes

- **The pom**: `<version>${revision}</version>` and `<revision>0-SNAPSHOT</revision>` in `<properties>`; nothing else of it changes.
- **`ledger.ReleaseVersion`**, public and final: `public static final String UNKNOWN = "unknown"`; `public static ReleaseVersion of(String named)`, `UNKNOWN` for `null` or blank, the value stripped otherwise; `public static ReleaseVersion ofThisJar()`, `of(ReleaseVersion.class.getPackage().getImplementationVersion())`; `public String value()`. No Spring bean is needed.
- **`schema.sql`**: §11's table, after `finished_step`. **`Runs.recordRelease(RunId runId, String releaseVersion)`**, public, the `INSERT OR IGNORE`; **`RunMint.mint`** calling it after `startRun` with `ReleaseVersion.ofThisJar().value()`. No schema version moves.
- **`AGENTS.md`**'s *"the 38 tables keyed by a run"*: forty-one once this table and ADR-429's two are in `schema.sql`, written with the code, since the file states what ships; `docs/check-claims.mjs` does not read that count.
- **`VesperaCommand`**: `versionProvider` naming a provider whose `getVersion()` is `{"Vespera " + ReleaseVersion.ofThisJar().value()}`.
- **`InvocationReport`**: `vespera` from `ReleaseVersion.ofThisJar().value()`. `docs/report/report.schema.json` types it a string or `null`, which it keeps, a reader of format 2 accepting both; the report template is unchanged.
- **`DeliverableRoot.markTree`**, its signature unchanged, writing the third line from `ReleaseVersion.ofThisJar()`; its reading of a marker, by its two keys, is unchanged. `DeliverableRootTest`, `TreeIsTheSameWrittenATopicAtATimeTest` and `TheDeliverableRootInvocationTest`, which state the marker whole, gain the third line.
- **The line at the foot of the deliverable's `index.html`, the website's `index.html` and the Confluence rendering's top page**, its words §5's, each drawn by the class that draws the page; any test stating one of those pages whole gains it.
- **`ControlPanelWindow.shownOn`**: the title `"Vespera control panel " +` the version its own package's `getImplementationVersion()` names, or `unknown`.
- **`docs/release.mjs`**, exporting `versionOfTag(tag)`, `checkPublishable(version)`, `checkBuilt(version, manifestText, implementationVersionsText)`, each throwing an `Error` whose message names what it refuses; `previousTag(tag, tags)`, a tag or `null`; `releaseNotes({ tag, previous, currentIndex, previousIndex })`, the notes' text, ending in a line feed; and `reachableFromMain(commit, { cwd, main })`, `main` defaulting to `origin/main`. Run as `node docs/release.mjs check <tag>`, `built <tag> <manifest> <implementation-versions>` and `notes <tag>`, exit `1` with the refusal's line on standard error.
- **`.github/workflows/release.yml`**, as §6 lists it, and a step of `build.yml`'s two Node jobs running `node --test src/test/docs/release.test.mjs`.
- **`README.md`**: the jar's name, `vespera-0-SNAPSHOT.jar`, in its command lines, and where a release's jar is found; `ui.ChildRuntimeTest`'s `JAR_NAME`. `src/test/hooks/private-paths-guard.test.mjs` names the jar only as a `vespera-*.jar` the guard matches, and may keep its names.
- `CONTEXT.md`'s **Release version** entry.
- The tests above passing.
