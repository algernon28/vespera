# ADR-397 — The YAML configuration lies beside the jar, and the jar writes its shipped copy where a file is missing

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, in [#836](https://github.com/algernon28/vespera/issues/836): that `application.yaml`, `application-local.yaml`, `compose.yaml` and `compose.gpu.yaml`, with the `docker/` build context `compose.yaml` names relative to itself, lie outside the jar, by default in the folder the jar is in (§1); that the jar carries no configuration Spring reads, the file beside the jar being the whole configuration, and keeps only a shipped copy, on a resource path Spring's configuration loading never reads (§2); that a file missing beside the jar is written there from the shipped copy as the jar starts, and `application-local.yaml`, which is optional, never (§3); that `--spring.config.location` and `SPRING_CONFIG_LOCATION` still name another location, the jar's folder being only the default (§4); and that on an upgrade the operator's file is never written over, a newer jar saying which keys or images differ from its shipped copy, in the log and in the control panel, and writing its new default beside the file as `<name>.new` (§5). Under the operator's delegation: which folder a run from compiled classes takes (§1); the resource folder, and where the repository keeps `application.yaml`'s source (§2); when the folder is prepared, and the line of a folder that cannot be written (§3); how Spring is pointed at the folder (§4); how a difference is found and said (§5); how the starter reads the compose files (§6); what the effective values call a value of the file beside the jar (§7); how a test context reads the shipped copy (§8); the IDE's run configurations (§9); and the line of a compose file beside the jar that cannot be read (§12).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §2 and §4: the starter reads both images from the compose files beside the jar, and `up` names those files where they lie; the jar no longer carries the files under `sidecars/`, writes them to no temporary folder and deletes none (§6). And §8: `ComposeFilesUnwritableException`'s line goes with the class, and a compose file beside the jar that cannot be read for its image refuses the start in a line of its own, `ComposeFileUnreadableException`'s (§12). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1: `shipped` is a value read from a file named `application.yaml` at the value the jar's shipped copy holds for its key, and `local-file` a value of that file the operator changed or added, and any other file of application configuration (§7). §8's presets still ship in `application.yaml`: in its shipped copy, which the jar writes beside itself. It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §11: the jar's own default image is the shipped copy's `vespera.docling.image`, still the processor build; the file beside the jar may name another, and `vespera` started by hand then runs it (§10). It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §3: the fifth source a field starts from is `application.yaml` in the folder beside the jar, read from the file, and no longer the window's class path (§10). It carries a note at its head.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md) as ADR-323 leaves it: the jar needs nothing from the checkout, carrying a shipped copy of every file it starts the sidecars from.
- **Keeps**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md): the Docling image is built from `docker/docling-serve`, never pulled. The build context's files are not changed or moved in the repository, so the image's contents do not change.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 as ADR-390 §4 leaves it: the control panel links no new class of the jar. The entry point, which names both (ADR-342 §2), prepares the folder and hands the window its path and the lines of §5.
- **Keeps**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8: `vespera serve` is handed on before `SpringApplication` is built, reads no YAML file, and prepares nothing beside the jar.
- **Keeps**: [ADR-395](0395-one-vespera-runs-on-a-machine-at-a-time-and-the-control-panels-own-child-is-let-through.md) §4: the machine lock is taken where it was, before the folder is prepared.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): a start refused for a folder that cannot be written exits 1.
- **Settles**: [#836](https://github.com/algernon28/vespera/issues/836).
- **Rests on**: Spring Boot's reference documentation on external configuration, read and not measured here, and the tests below.

## Context

`application.yaml` is a resource at the root of the jar's class path, so a value the operator wants changed for good means a jar built again or a flag passed at every start. `compose.yaml`, `compose.gpu.yaml` and `docker/` are carried under `sidecars/`, and `ComposeFiles.fromTheClasspath` writes them to a temporary folder at each start of the sidecars. `application-local.yaml` is read from the class path through `-Dspring.config.import`, which only the IDE's run configurations pass.

### Read, and not measured

Spring Boot's reference documentation, *External Application Properties*:

| What it says | What follows here |
| --- | --- |
| Config data files are considered in the order: inside the jar, profile-specific inside the jar, outside the jar, profile-specific outside the jar, later overriding earlier | a file Spring finds outside the jar stands over every file of the class path, `application-test.yaml` included |
| The default locations are the class path's root and `config/`, then the current directory, its `config/` and the folders below that | `./application.yaml`, in the current directory of a start, is read with no flag |
| Locations named by `spring.config.location` replace the default locations; it is read early, from an environment property, a system property or the command line | a location the operator names reads in place of the folder beside the jar |
| An imported file stands over the file that imports it, inserted just below it | a test context can read the shipped copy through an import of the test tree's own file |
| A location not found refuses the start unless it carries `optional:` | `application.yaml` beside the jar is named without it, `application-local.yaml` with it |

**Not read or measured**: that `spring.config.location` given among `SpringApplication`'s default properties is read as one given as a system property, which the tests of §4 hold; how Windows access control answers a write in a folder such as `Program Files`, which §3's refusal names whatever the reason.

## Decision

### 1. The folder beside the jar

The folder beside the jar is found from the code source the application class was loaded from:

- **A jar** (`java -jar`, the control panel's child, Spring Boot's nested jar read as its outer file): the folder the jar lies in.
- **A folder of compiled classes** (an IDE's run, a test): the folder above it, `target/` for `target/classes`, where the build writes the jar. Never the classes folder itself, which is on the class path.
- **Neither, or a code source that is not there**: the current directory. So is a code source whose URL names no file, a `jrt:` URL or an opaque one among them: it is not taken for a file, and so never for a folder whose parent would be the folder beside the jar.

**Why not the current directory for a run from classes.** The IDE's run configurations start in the repository's root, which is also every test's current directory. Spring reads `./application.yaml` there with no flag, above `application-test.yaml`, so a file written there by one IDE run would stand over the test profile in every test context on that machine. A jar started from another folder would also find, and write, its files there.

**Why not Spring Boot's `ApplicationHome`.** It answers the current directory under a test and the classes folder for an IDE run, the two places the bullets above refuse.

### 2. What the jar carries

- **Nothing Spring reads.** No `application.yaml`, `application.yml` or `application.properties` lies at the root of the jar's classes or under `config/`, and no `application-local.yaml` anywhere in them.
- **A shipped copy of each file it keeps beside itself**, under `beside-the-jar/`, which no default location of Spring's reads: `application.yaml`, `compose.yaml`, `compose.gpu.yaml` and every file under `docker/`, byte for byte, unfiltered. None of `application-local.yaml`, which is the operator's alone. `sidecars/` goes.
- **The sources.** `application.yaml` moves from `src/main/resources/` to `src/main/beside-the-jar/application.yaml`. `compose.yaml`, `compose.gpu.yaml` and `docker/` stay at the repository's root, unchanged, so the README's lines and the Docling image built from `docker/docling-serve` are what they were. `pom.xml` copies both into `beside-the-jar/`.
- **The repository's root holds no `application.yaml`, `application.yml` or `application.properties`**: it is every test's current directory, a default location Spring reads above `application-test.yaml` (§1).
- **`.gitignore`** holds out `/application-local.yaml` at the root, in place of `/src/main/resources/application-local.yaml` (§9).

### 3. Preparing the folder

**When.** `VesperaApplication.main` registers `BesideTheJar` as the first of its listeners; it hears that a start has begun, before Spring prepares its environment and reads any configuration. The entry point prepares the folder for the control panel once the machine lock is held (ADR-395 §4) and before the window opens. `vespera serve` prepares nothing, and neither does a context Spring's test support builds.

**What.** Each file of §2 that is not beside the jar is written there from its shipped copy, its folders made, in the order `application.yaml`, `compose.yaml`, `compose.gpu.yaml`, then the build context's files by name; each is written to a file of its own beside it, its name ending `.part` and different at each start, and moved into place, so a start ended midway leaves no half file under the file's name. **A file already there is never written over**, whatever it holds. **Two starts preparing one folder at once**, as two jars in one folder or the control panel and a hand-started `vespera` may, both go on: each writes its own `.part`, the first moved into place stands, the other start's is deleted, and no `.part` is left behind. **`application-local.yaml` is never written.** A file the operator deletes is missing, and so is written again at the next start: deleting a file is how the operator takes the shipped one. Then §5.

**A folder that cannot be written refuses the start**, before a working directory is made or any lock but the machine's taken, in one line on standard error, exit 1:

*"Vespera cannot write &lt;name&gt; in the folder beside the jar, &lt;folder&gt; (&lt;reason&gt;); make that folder one this user can write, and start Vespera again."*

The control panel's start is refused in the same line, and no window opens.

**Standard error, not the log.** The command line's refusal is thrown from the listener as Spring starts, before its logging is configured, so a log line would reach the console through whatever logging stands before it, on standard output, with a logger's prefix. The line goes to standard error as it stands, once, and the exit is 1, whichever of `VesperaApplication.main` and the entry point says it.

**The folder is prepared even where §4's location names another.** The compose files are read beside the jar whatever Spring reads (§6).

### 4. Where Spring reads

`VesperaApplication.main` gives Spring, among its default properties, the lowest of its sources:

```
spring.config.location=file:<folder>/application.yaml,optional:file:<folder>/application-local.yaml
```

`application-local.yaml` comes last, and so stands over `application.yaml`, as it stood over the class path's file. Spring cuts `spring.config.location` at its commas, so a comma in the folder's path is written so that the cut does not fall inside it: whatever the folder is named, the property names the two files and Spring reads both. A `spring.config.location` given on the command line, as a system property or as `SPRING_CONFIG_LOCATION` stands in its place, by Spring's own precedence, and neither file beside the jar is read; `spring.config.additional-location` adds to whichever is in force. The command line, the environment and the system properties stand over both files, as before.

### 5. A file that differs from its shipped copy

After §3's writes, each file of §2 beside the jar is compared with its shipped copy:

- **`application.yaml`, `compose.yaml` and `compose.gpu.yaml`**, by their keys, flattened as Spring flattens a YAML file, each key by its dotted name and each item of a list by its index, and their values as Spring's YAML reading gives them. A key added, a key removed, or a key whose value is another differs. A comment, the order of the keys, a blank line or a quoting does not. An image is a key, `services.docling-serve.image` among them, so a compose file is named by the images that differ, and by any other key.
- **A file of the build context**, by its bytes.
- **A YAML file that does not load** differs, at no key: its `.new` is written and the line that names no key is said. It cannot be the shipped copy, which loads, and the `.new` is what the operator has to put it right from.

For each file that differs, in §3's order, the shipped copy is written beside it as `<name>.new`, over a `.new` already there, and **the file is left as it is**. One line says so, naming the keys in the order of their names, and no value, since the operator's file may hold a path or a credential:

*"&lt;name&gt; beside the jar differs from this jar's own at &lt;key&gt;, &lt;key&gt;; this jar's is written beside it as &lt;name&gt;.new"*

and where no key differs, *"&lt;name&gt; beside the jar differs from this jar's own; this jar's is written beside it as &lt;name&gt;.new"*.

**Where it is said.** The command line logs each line at WARN, so it is in its log and on its console. The entry point hands the lines to the control panel, whose window says each at WARNING in its log as it opens, as it says the lines of the list of working directories; its child prepares the folder again and says the same lines in its own output.

A `.new` that cannot be written is said in place of that file's line, among the same lines and where they are said, and the start goes on: the operator's file is the one in use either way. `<reason>` is the message of what refused the write, on one line:

*"&lt;name&gt; beside the jar differs from this jar's own at &lt;key&gt;, &lt;key&gt;; this jar's cannot be written beside it as &lt;name&gt;.new (&lt;reason&gt;)"*

and where no key differs, the same line without *"at &lt;key&gt;, &lt;key&gt;"*. It is not logged as the folder is prepared, before the logging is up, where the command line's log would not hold it. No `.new` is read or deleted; one beside a file that no longer differs is an earlier jar's.

### 6. The compose files

The starter reads `ComposeFiles.in(<the folder beside the jar>)`: ADR-323 §2's two images are read from those files, and §4's `up` names them where they lie, its build context resolving beside them. Nothing is written to a temporary folder and nothing is deleted. `ComposeFiles.fromTheClasspath` and `ComposeFilesUnwritableException` go. An image the operator writes in `compose.yaml` is the one `vespera.docling.image` is compared with. `-p vespera` names one project from any folder (ADR-323, probe G), so the containers started from the checkout, from an earlier jar's temporary folder and from beside the jar are one set.

### 7. Where a value of the effective values came from

ADR-330 §1's table of `<key>.source`:

| `<key>.source` | Where the value came from |
| --- | --- |
| `shipped` | a file named `application.yaml`, its name compared without regard to case, at the value the jar's shipped copy holds for the key, compared as the property's text |
| `local-file` | a file named `application.yaml` at another value, or for a key the shipped copy does not hold; and any other file of application configuration, `application-local.yaml` among them |
| `environment` | an environment variable or a system property |
| `command-line` | the command line |
| `code` | no configuration: a value in the code |

So a value the operator changed beside the jar is shown as the local settings', and one left as shipped as Vespera's own.

### 8. The tests' configuration

`src/test/resources/application.properties` imports `classpath:/beside-the-jar/application.yaml`, so a context Spring's test support builds starts from what the jar ships, and `application-test.yaml` stands over it as it stood over the class path's file. The tests of this record write the folder beside the jar in temporary folders of their own, and start Spring with system properties and an environment of their own; the test of §3's registration abandons its start before any listener is told anything, so nothing is written beside any jar. No test reads a folder beside a jar of the machine's, or expects one to hold a file.

### 9. The IDE's run configurations

*Local SpringApp*, *Local Vespera Label* and *Local Vespera Publish* drop `-Dspring.config.import=optional:classpath:/application-local.yaml` and pass `-Dspring.config.additional-location=optional:file:./application-local.yaml`, the repository root's, which `.gitignore` holds out. The folder beside the jar of an IDE run is `target/` (§1), which a clean build empties, so the operator's local file is kept at the root and read over `target/`'s files.

### 10. The control panel's starting values and the GPU image

ADR-344 §3's fifth source is `application.yaml` in the folder beside the jar, read from the file with Jackson's YAML reader as before; a file that cannot be read says nothing. `application-local.yaml` stays outside §3's sources. ADR-334 §11's *"the jar's own default does not change"* holds of the shipped copy, whose `vespera.docling.image` stays the processor build; a file beside the jar naming another is the operator's, and `vespera` started by hand runs it.

### 11. No run id moves

A run's identity takes values, never the file they came from, and the shipped copy holds the bytes `application.yaml` held.

### 12. A compose file beside the jar that cannot be read refuses the start in one line

`compose.yaml` and `compose.gpu.yaml` are the operator's to edit once they lie beside the jar, so the starter can meet one that does not load, one whose `docling-serve` service names no image, or one deleted after the folder was prepared. Each would reach the operator as a stack trace from the middle of a start, which ADR-323 §8's lines exist to prevent; so each is a refusal like the rest, `ComposeFileUnreadableException`, extending `SidecarStartRefusedException`, one line on standard error and exit 1:

*"the sidecars are not answering and Vespera cannot start them: &lt;file&gt; cannot be read for the image its docling-serve service builds (&lt;reason&gt;). Put that file right, or delete it so that Vespera writes its own there, and run the same command again."*

`<file>` is the file's absolute path. `<reason>` is one of, on one line:

- *it is not there*;
- *it names no image for its docling-serve service*, where the file loads and `services.docling-serve.image` is missing or not text;
- *it does not load as YAML: &lt;the parser's message&gt;*;
- the message of what refused the read, or its exception's simple name where it has none, for any other failure to read it.

**When.** As ADR-323 §3 has it: only once the sidecars are found not answering, so a broken file stops nothing while they answer. Both files are read before the configured image is compared with theirs, `compose.yaml` first, and the first that cannot be read is the one named, whichever image is configured. Nothing is run before the refusal.

Deleting the file is the remedy the line offers because §3 writes a missing file from the shipped copy at the next start, and the same command run again is that start.

## Alternatives refused

- **The current directory as the folder.** A jar started from another folder would write its files there, and the repository's root, where the IDE starts, is the current directory of every test, where Spring would read the file above `application-test.yaml` (§1).
- **`spring.config.additional-location` naming the folder, the class path's file kept.** Two files would define values, against the operator's decision that the file beside the jar is the whole configuration.
- **Merging the new keys into the operator's file.** It writes the operator's file, which the operator decided is never written over, and a YAML writer loses its comments and order.
- **An `EnvironmentPostProcessor` listed in `spring.factories` for §3.** Every context Spring's test support builds would run it, writing into the build's `target/` during the tests.
- **Comparing a compose file by its images alone.** A changed port or volume would go unsaid; comparing every key names the images among them.
- **`application.yaml`'s source at the repository's root, beside the compose files.** Spring reads it there in every test context, above `application-test.yaml` (§2).

## Consequences

- **A checkout built before this record needs one clean build.** The build copies resources and deletes none, so `target/classes/application.yaml` and `target/classes/sidecars/` stay until `target/` is emptied, and the test that the jar carries nothing Spring reads fails until then.
- **A jar in a folder the user cannot write refuses to start**, naming the folder (§3).
- **A key a newer jar needs and the operator's file lacks** is named in §5's line as one that differs, and `<name>.new` holds it; the code's own default applies where it has one, and a start that needs it otherwise fails as a missing value fails.
- **Two jars in one folder share its files**, and each says how its own shipped copy differs.
- **The control panel and its child read one folder**, being one jar.

## What this does not decide

- Profile-specific files beside the jar other than `application-local.yaml`.
- A shipped copy, or a `.new`, of `application-local.yaml`.
- A control of the window that writes or merges a `.new` into the file.
- What the README and `docs/architecture.md` say of the folder: the implementation owes them.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.BesideTheJarTest.aMissingFileIsWrittenFromTheJarsCopyAndTheLocalFileNever` | §2, §3: every file of the shipped copy written, in order, byte for byte, the resource folder `beside-the-jar/`; `application-local.yaml` not written; a second start writes nothing, finds nothing that differs and writes no `.new` |
| `pipeline.BesideTheJarTest.aFileAlreadyBesideTheJarIsNeverWrittenOver` | §3: `application.yaml`, `compose.yaml` and a build-context file the operator wrote left untouched, only the missing ones written |
| `pipeline.BesideTheJarTest.twoStartsPreparingOneFolderAtOnceLeaveEveryFileTheShippedCopyAndNoPartFile` | §3: in 50 fresh folders, two `prepare()` calls started together from two threads; neither throws or says a line, every file of the shipped copy is its bytes, and no file ending `.part` is left |
| `pipeline.BesideTheJarTest.aFileThatDiffersIsNamedByItsKeysAndTheJarsCopyIsWrittenBesideIt` | §5: a key added, removed and changed in `application.yaml`, an image in `compose.yaml` and a build-context file's bytes named, in order, with §5's lines; a comment in `compose.gpu.yaml` not; `<name>.new` byte for byte the shipped copy beside each; every file as the operator wrote it |
| `pipeline.BesideTheJarTest.aNewFileThatCannotBeWrittenIsSaidAmongTheLinesNamingItAndWhy` | §5: a `.new` stood in the way of by a folder of that name gives, among `prepare()`'s lines, the line naming the key, the `.new` and the reason, on one line; nothing thrown; the operator's file and folder untouched |
| `pipeline.BesideTheJarTest.aYamlFileThatDoesNotLoadDiffersAtNoKey` | §5: an `application.yaml` that does not load is named with no key, in the line naming none, its `.new` the shipped copy byte for byte, the file untouched |
| `pipeline.BesideTheJarTest.aFolderThatCannotBeWrittenRefusesTheStartNamingIt` | §3: `BesideTheJarUnwritableException`, its message §3's line, one line |
| `pipeline.BesideTheJarTest.theFolderIsTheJarsTheOneAboveTheClassesOrTheCurrentDirectory` | §1: a jar's folder; the folder above compiled classes; the current directory for a code source not there |
| `pipeline.BesideTheJarTest.aCodeSourceUrlThatNamesNoFileIsTheCurrentDirectory` | §1: `folderOf` a code source's URL: a `file:` URL of a jar its folder; a `jrt:` URL and an opaque URL the current directory |
| `pipeline.BesideTheJarTest.springReadsTheConfigurationAndTheLocalFileBesideTheJar` | §4: Spring, pointed by `pointSpringAt`, reads `application.yaml` beside the jar and `application-local.yaml` over it |
| `pipeline.BesideTheJarTest.aFolderWhoseNameHoldsACommaIsReadAsOneFolder` | §4: the same, for a folder named `a,b` |
| `pipeline.BesideTheJarTest.aLocationNamedElsewhereIsReadInPlaceOfTheFolderBesideTheJar` | §4: `spring.config.location` on the command line, as a system property and as `SPRING_CONFIG_LOCATION` read in place of both files |
| `pipeline.BesideTheJarTest.theComposeFilesAreReadBesideTheJar` | §6: the compose files read in the folder beside the jar, an image the operator wrote there the one read, neither deleted |
| `pipeline.TheEffectiveValuesSayWhereAValueBesideTheJarCameFromTest.aValueBesideTheJarIsShippedOnlyAtTheShippedValue` | §7: a preset left as shipped `shipped`; one changed in `application.yaml` and one set in `application-local.yaml` `local-file` |
| `pipeline.TheEffectiveValuesSayWhereAValueBesideTheJarCameFromTest.anApplicationYamlSpeltInAnotherCaseIsShippedAtTheShippedValue` | §7: read from `Application.yaml` named by `spring.config.location`, a preset left as shipped `shipped` and one changed `local-file` |
| `pipeline.AComposeFileThatCannotBeReadRefusesTheStartTest.aComposeFileNamingNoImageRefusesTheStartInOneLine` | §12: `ComposeFileUnreadableException`, a `SidecarStartRefusedException`, in §12's line for a `compose.yaml` naming no image; no command run |
| `pipeline.AComposeFileThatCannotBeReadRefusesTheStartTest.aComposeFileThatDoesNotLoadRefusesTheStartInOneLine` | §12: the line for a `compose.yaml` that does not load, one line; no command run; nothing refused while both sidecars answer |
| `pipeline.AComposeFileThatCannotBeReadRefusesTheStartTest.aGpuFileThatIsNotThereRefusesTheStartInOneLine` | §12: the line naming `compose.gpu.yaml`, not there, though `compose.yaml` builds the image configured |
| `EntryPointTest.aFolderBesideTheJarThatCannotBeWrittenIsSaidOnStandardErrorAndExitsOne` | §3: the command line refused with `BesideTheJarUnwritableException` gives exactly its one line on standard error and exit 1, nothing escaping the start |
| `TheJarCarriesNoConfigurationSpringReadsTest.theJarCarriesNoFileSpringsConfigurationLoadingReads` | §2: no name Spring reads at the root of the jar's classes or under `config/`, and no `application-local.yaml` at any depth |
| `TheJarCarriesNoConfigurationSpringReadsTest.theRepositorysRootHoldsNoFileSpringReads` | §2: none at the repository's root either; `application.yaml`'s source below `src/main` |
| `TheJarCarriesNoConfigurationSpringReadsTest.theJarCarriesTheRepositorysFilesAsItsShippedCopies` | §2: each file under `beside-the-jar/` byte for byte its source, nothing else there, no `application-local.yaml`, no `sidecars/` |
| `TheFolderBesideTheJarIsPreparedBeforeSpringReadsItTest.mainRegistersTheFolderBesideTheJarFirst` | §3: `VesperaApplication.main` registers `BesideTheJar` ahead of `WorkingDirectoryPreparer` |

Changed beside them, each now reading the shipped copy under `beside-the-jar/` through `TheShippedCopy`, in place of the class path's `application.yaml`: `ShippedConfigurationTest`, `TheShippedPresetsTest`, `NoPoolSetsSqlitesTemporaryDirectoryTest`, `pipeline.PresetsTest` and `pipeline.GroupingStepsPerRowTest`. `PipelineHoldsOnlyTheRulesOnRecordTest` puts `BesideTheJar`, `BesideTheJarUnwritableException` and `ComposeFileUnreadableException` on record as holding no rule, and `ComposeFilesUnwritableException` no longer.

Not held by a test: §5's lines in the control panel's window and in the command line's log, the entry point's preparation before the window, and §9's run configurations.

## What the implementation owes

- `src/main/java/io/algernon/vespera/engine/pipeline/BesideTheJar.java` and `BesideTheJarUnwritableException.java`, with the members the tests name, and the deletion of the two stand-ins of those names under `src/test/java/io/algernon/vespera/engine/pipeline/`, in the same commit.
- `VesperaApplication.main`: the listener first, the default property of §4, and §3's refusal said in its line with exit 1. `EntryPoint`: the preparation before the window, the folder and the lines handed to `ControlPanel.open`, which reads `application.yaml` from the folder (§10) and says the lines (§5).
- `SidecarStarter.forTheApplication` reading `ComposeFiles.in` the folder beside the jar; `ComposeFiles.fromTheClasspath` and `ComposeFilesUnwritableException` removed (§6).
- `BesideTheJar`: its record of a file that differs named `Difference`, and the method that writes the `.new` files `writeNewWhereDifferent`, in the words of `CONTEXT.md`, which keeps the word the earlier names used out of identifiers; `folderOf(URL, Path)` for a code source's URL (§1); `pointSpringAt` for a folder whose path holds a comma (§4); a YAML file that does not load differing at no key, and a `.new` that cannot be written said among the lines (§5).
- §3's refusal on standard error, exit 1, through `EntryPoint.start`'s `err` and `exit` where the command line throws `BesideTheJarUnwritableException`, in place of `VesperaApplication.main`'s log line and its own exit.
- `BesideTheJar`'s writes through a `.part` of each start's own, in place of the one `<name>.part` two starts share, so that two starts preparing one folder at once both go on and leave no `.part` (§3).
- `EffectiveValuesFile`'s comparison of the file's name without regard to case (§7).
- `ComposeFileUnreadableException` and `ComposeFiles`' reading of the two images as §12 has it.
- `EffectiveValuesFile.sourceOf` as §7 has it.
- `application.yaml` moved to `src/main/beside-the-jar/`; `pom.xml`'s resources as §2 has them; `.gitignore` as §2 and §9 have it; the three run configurations of §9.
- The README, `docs/architecture.md`, and `AGENTS.md`'s line on the test configuration, which says it layers over `src/main/resources/application.yaml`.
