# ADR-444 — The release version is computed from git and the records merged since the previous tag, and every push to `main` is released

- **Date**: 2026-11-23
- **Status**: accepted on 2026-11-23.
- **Release**: minor
- **Decided**: by the operator: that no version is typed by hand, neither in the pom nor as a tag, and that it is inferred from git and from what each change implements (§1); that the bump a change makes is named by its decision record, on a line of the record's head, `- **Release**: major|minor|patch` (§2); that every push to `main` is released (§4); and that a build that is no release is named by git through a Maven core extension, with no command-line property (§5). This record's own: the extension and its configuration (§5), the form of a build's version that is no release (§5), the computation's rules where there is no previous tag or no new record (§3), the refusal of a new record that names no bump (§2), and the workflow's order, the tag made before the build and pushed after it (§4).
- **Amends**: [ADR-431](0431-the-release-version-is-the-v-tags-semver-0-snapshot-in-any-other-build-and-enters-no-run-id.md) §2, *"The pom's `<version>` is `${revision}`"* and *"A release build is given `-Drevision=<the tag without its v>`"*: the pom's `<version>` is a plain `0-SNAPSHOT` that no build but one with the extension turned off reads, and the version is the extension's (§5). §6, *"A release is cut on demand: the operator pushes a `vMAJOR.MINOR.PATCH` tag"*: the workflow runs on a push to `main`, computes the version and pushes the tag itself (§4). Its Alternatives refused, *"A version computed from commits, by a plugin such as jgitver or git-commit-id"* and *"A release at each merged change"*: both are now what is done. It carries a note at its head.
- **Keeps**: ADR-431 §1, the table of what raises MAJOR, MINOR and PATCH, by which a record's `Release` line is chosen (§2); §3, the release version entering no run id; §4 and §5, how the jar reads it and where it shows; §7, the notes; §8, every refusal; §9, `v1.2` the baseline and 2.0.0 the first release; §11, `run_release`.
- **Amends**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): the build gains one core extension, `me.qoomon:maven-git-versioning-extension`, which this record requires (§5).
- **Read with**: [ADR-272](0272-a-module-whose-answer-is-the-boundary-of-a-shallow-clone-is-written-as-unknown.md): a shallow clone already writes `unknown` for a module and mints no run; under the extension it is refused outright (Known limits).

## Context

ADR-431 made the `v` tag the release version's source and every other build `0-SNAPSHOT`, a release being cut by the operator pushing a tag whose number they chose. The operator found two things wanting: `0` is no version, so every jar not built from a tag, `target/vespera-0-SNAPSHOT.jar`, says nothing of which Vespera it is; and choosing the number by hand is a step that can be forgotten or misjudged, where the change itself already says what kind of change it is. Every decision this project makes is a record, and ADR-431 §1 already says which kinds of decision raise MAJOR, MINOR or PATCH.

**Measured**, with Maven 3.9.16, the wrapper's, and `me.qoomon:maven-git-versioning-extension` 9.12.3 configured as §5 says, on a clone of the whole history whose nearest tag on the first-parent line was `v1.2`, 101 commits back:

| Build | `project.version` | `project.build.finalName` |
| --- | --- | --- |
| on a branch, no tag at `HEAD` | `1.2-101-g3c7a254-SNAPSHOT` | `vespera-1.2-101-g3c7a254-SNAPSHOT` |
| a tag `v9.9.9` made at `HEAD`, still on the branch | `9.9.9` | `vespera-9.9.9` |
| given `-Dversioning.disable` | `0-SNAPSHOT` | `vespera-0-SNAPSHOT` |
| sources with no `.git` at all | `0-SNAPSHOT` | `vespera-0-SNAPSHOT` |
| a clone of depth 1 | refused: *couldn't find matching tag in shallow git repository* | — |

The extension writes the version into the model Maven builds, so the manifest's `Implementation-Version`, which Spring Boot's parent writes from `project.version`, and the jar's name follow it with no configuration of their own, and it writes `.git-versioned-pom.xml` at the root in each build.

## Decision

### 1. No version is typed

**No person writes a version, in the pom, on the command line or as a tag.** The release version is computed from git: the previous release tag, and the decision records merged since it (§3). ADR-431 §1's meaning of MAJOR, MINOR and PATCH is unchanged; what changes is who applies it, the record's author, once, for the change the record makes.

### 2. A record names its bump

**From this record on, every decision record's head carries the line `- **Release**: major`, `minor` or `patch`**, after its Status, chosen by ADR-431 §1's table for what the record decides: a record that changes nothing an operator installs, a test's or an agent's rule among them, is `patch`. A record numbered 444 or above whose head has no such line, or another word on it, is refused: `src/test/docs/release.test.mjs` refuses it before it is merged, and `node docs/release.mjs next` refuses to compute a version over it. The records below 444 carry none and are never read for one (§3).

### 3. The next version

**`node docs/release.mjs next` prints the version the commit at `HEAD` is released as**, or nothing where `HEAD` already carries a `vMAJOR.MINOR.PATCH` tag:

- **The previous tag** is the greatest tag reachable from `HEAD` reading `v` and two or three numbers, as ADR-431 §7 reads it.
- **Where there is none, or it lies below 2.0.0, the version is 2.0.0**, ADR-431 §9's first release, whatever the records say.
- **Otherwise the records merged since it** are the rows of `docs/adr/README.md` at `HEAD` that its copy at the previous tag does not hold, as ADR-431 §7's notes list them; each one's file at `HEAD` is read for its `Release` line.
- **The greatest bump among them raises the previous tag's version**: `major` to `MAJOR+1.0.0`, `minor` to `MAJOR.MINOR+1.0`, `patch` to `MAJOR.MINOR.PATCH+1`. **Where no record was merged, it is a patch.**

### 4. Every push to `main` is released

**`.github/workflows/release.yml` runs on every push to `main`**, one at a time in the order `main` moved, a later push waiting rather than cancelling one being published. It:

1. checks out the whole history, `fetch-depth: 0`, as ADR-431 §6 does;
2. computes the version by §3, and stops, publishing nothing, where `HEAD` already carries a release tag;
3. makes the tag `v<version>` at `HEAD`, locally, so that the extension builds that version (§5);
4. refuses by ADR-431 §8's first and third rules;
5. builds as the Linux job of `build.yml` builds, with no property naming the version;
6. refuses by ADR-431 §8's second and fourth rules, reading the jar;
7. pushes the tag and creates the GitHub release, `vespera-<version>.jar` attached, its notes ADR-431 §7's.

**A build or a refusal that fails leaves no tag and no release**, the tag being pushed last; the next push to `main` computes again from the same previous tag, so its version covers both pushes' records. A tag the workflow pushes with the workflow's own token starts no workflow, so the release builds once.

### 5. Every build is named by git

**`.mvn/extensions.xml` loads `me.qoomon:maven-git-versioning-extension` 9.12.3**, configured in `.mvn/maven-git-versioning-extension.xml`:

- **a commit carrying a tag `v` and three numbers**, whether checked out by the tag or on a branch, builds the tag's numbers: `v2.1.0` builds `vespera-2.1.0.jar`, `Implementation-Version: 2.1.0`;
- **any other commit builds `<previous tag's numbers>-<commits since it>-g<the commit, seven characters>-SNAPSHOT`**, the tag found along the first-parent line, so a developer's jar says which release it grew from, how far, and at which commit; `0.0.0` stands for the numbers where no tag is found.

**The pom's `<version>` is a plain `0-SNAPSHOT`**, and its property `revision` is removed: it is read only where the extension is off, `-Dversioning.disable`, or where there is no `.git`. `.git-versioned-pom.xml`, which the extension writes, is ignored by git. **Failsafe hands the tests that launch the jar its path**, as the system property `vespera.jar`, `${project.build.directory}/${project.build.finalName}.jar`, since its name now moves with every commit.

## Alternatives refused

- **The bump named by a pull request's label.** It lives outside the repository, is lost from a clone, and is a second place to judge what the record already judges.
- **Conventional Commits in the squash titles.** This project's titles are sentences of what changed; a prefix on each would be a second rule of writing, and a title is easily mistyped, where a record is read line by line before it is merged.
- **The next version computed in a developer's build too**, `2.1.0-SNAPSHOT` from the records on the branch. The extension reads git and not the records, and computing it in the build would need a plugin or extension of this project's own; the distance and the commit say where a jar came from without guessing which release it will be part of.
- **A release cut on demand, by a dispatched workflow that computes the version.** The operator chose each push to `main`.
- **`-Drevision` computed by a script and passed in.** A plain package build would still be `0-SNAPSHOT`.

## Consequences

- No pom edit, no tag and no number is ever written by hand; every push to `main` that builds and passes ADR-431 §8 becomes a GitHub release with its jar.
- The first push to `main` under this record is released as 2.0.0, `v1.2` being the previous tag.
- A developer's jar is `target/vespera-<previous release>-<commits>-g<commit>-SNAPSHOT.jar`; the README's command lines say `target/vespera-*.jar`.
- A record's author chooses its bump when writing it, and a release raises by the greatest one merged since the previous tag.
- `ADR-431` carries a note at its head.

## Known limits

- **A clone of shallow history fails every Maven build**, the extension finding no tag: `git fetch --unshallow` first, or build with `-Dversioning.disable`, whose jar is `0-SNAPSHOT` and, by ADR-272, mints no run anyway.
- **An IDE that imports the pom without the extension** shows `0-SNAPSHOT`; one that loads `.mvn/extensions.xml` shows git's version.
- **A push to `main` that only touches documents is released** as a patch, its jar the same as the previous one's but for its version.
- **A push to `main` while a release is being built waits** for it; where several wait, GitHub keeps the latest, whose release covers all of them.

## Tests

| Method | What it holds |
| --- | --- |
| `TheReleaseVersionIsTheTagsTest.theVersionIsGitsThroughTheExtension` | §5: `.mvn/extensions.xml` loads the extension; its configuration builds a `v` and three numbers as those numbers; the pom's version is `0-SNAPSHOT` and it has no `revision` |
| `release.test.mjs`, *a record's head names its bump on its Release line* and *a record naming no bump, or another word, is refused* | §2 |
| `release.test.mjs`, *every record from ADR-444 on names its bump* | §2: every file in `docs/adr` numbered 444 or above has a `Release` line |
| `release.test.mjs`, *the next version raises the previous tag's by the greatest bump merged since it*, *a release merging no record is a patch*, *with no previous tag, or one below 2.0.0, the next release is 2.0.0*, *the records merged since the previous tag are the index's new rows, ascending* | §3 |
| `PackagedJarIT`, `CliExitIT` | §5: the jar is found at the path failsafe names |
