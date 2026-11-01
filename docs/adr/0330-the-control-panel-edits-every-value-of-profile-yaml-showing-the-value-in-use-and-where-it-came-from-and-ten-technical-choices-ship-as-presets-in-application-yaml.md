# ADR-330 — The control panel edits every value of `profile.yaml`, showing the value in use and where it came from, and ten technical choices ship as presets in `application.yaml`

- **Date**: 2026-10-31
- **Status**: accepted on 2026-10-31.
- **Decided**: by the operator, that one record covers both parts; that `profile.yaml` stays the store, the jar reading it, every run recording it and an agent reading and writing it; that the pattern to extend is [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md)'s; that the page creates the file if it is missing and edits it if it is present, a save of `profile.yaml` making a working directory that is missing; that more technical choices ship as presets and are in time edited on the control panel; that every candidate of §8's table is one: `RelevanceScorer.TOP_K`, `RelevanceDistribution.BANDS` and `SAMPLED_PER_BAND`, `DoclingClient.PINNED_OCR_PRESET`, `FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT`, and `similarity`'s `MinHashParameters.DEFAULT`, `ShingleParameters.DEFAULT` and `RedundancyThresholds.DEFAULT`, beside clustering's two numbers; that the number of similarities joins a relevance floor's scale as `;top:<n>`; that the sample's two numbers stay in the scoring run's identity; that a preset no run can be made under is refused at the start of `vespera run`; that the timeouts in a row are held to 2 to 4; that `clusterResolution` is wholly Part B; that a working directory a save makes on Windows is owner-only by a DACL; and that `/profile` keeps [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md)'s list and its one form for each value. How each preset enters a run and what becomes of the floors and answers its earlier value was read under is decided here under the same delegation. Every other choice here, each with the alternative it refused, is decided under the operator's delegation of 2026-10-31, and any of them is reopened by a later record.
- **Keeps**: [ADR-043](0043-the-profile-is-authored-as-a-file-and-recorded-in-the-ledger.md), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md): the file is input, a value is `value`, `provenance` and `measurement` under its key, and a save holds only what was saved, the jar adding the rest as unset at its next load (§7).
- **Keeps**: [ADR-006](0006-census-measure-before-judging.md): a measured floor ships unset, so no floor is a preset (§8).
- **Keeps**: [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md): an unreadable value is a third state, shown as one, and no repair touches it (§2, §6).
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): every write holds the working-directory lock; a save that writes nothing takes none (§5).
- **Keeps**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md): what an agent may read in a working directory is unchanged, so neither `effective-values.properties` nor a repair's copy is on its list (What this does not decide).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §1, §4 and §5 as [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §2 leaves them, §7, and §8: every path of §6 passes the checks of `Host`, `Sec-Fetch-Site` and `Origin`, the server's classes write nothing, no file holds a token or text of a document, and a folder a save makes is one only this user can write in, on NTFS by a DACL. Of a folder reused §8 keeps what can be told: on POSIX permissions its owner and who may write, on NTFS its owner alone, who else may write there not being compared (§7, §13, Known limits).
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md): the Docling image stays a launch value, stored as an invocation starts and passed as `VESPERA_DOCLING_IMAGE`; a key of `application.yaml` that enters no run, which the control panel comes to edit, is a launch value too (§11, §12); and §3's folder of the launch values, whose owner is not compared on Windows, for a reason that is of `%LOCALAPPDATA%\Temp` and does not reach a working directory on any drive (§7).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): each screen is tested through the pages it serves, reading names, ids and attributes and never words. §3's control types rest on what a browser does with them, which no test sees, and this says so as that record's Consequences ask.
- **Keeps**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §4: one script, which writes nothing into a page; no control here needs it (§3).
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): a stage's version is the last commit touching a module it names, and no stage names `pipeline` or `profile`; §10 follows from them and asks neither to change.
- **Keeps**: [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands.md): its numbers are the shipped values, byte for byte, and its *"recorded here as values, not as profile keys"* stands: none of them has a profile key (§8, §9).
- **Keeps**: [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md)'s *"Why not a profile key"* and its first run judging: a similarity cut is still no judgement about one corpus, so it is no profile value (§8, §12).
- **Keeps**: [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md), [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) and [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §2: the processor build stays the default everywhere, and a conversion is refused under an image the sidecar does not run (§11).
- **Keeps**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §9 and [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md) §5: those keys are the application's and no profile values (§12).
- **Keeps**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §3's `#profile-list` and a section and form for each value, each save answering to its section; §4's rule that every picker is a server-written page or a control a browser draws; §5's folder page; §6's folder field, which keeps the white space before its name; §7's controls, a number with no `min` and no `max`, a list a text field, `models` and `docling-images`, each as it has them; and §8's placeholder and hints (§2, §3, §5, §11).
- **Extends**: [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md) §1, §3 and §4: the resolution ships in `application.yaml`, the profile overrides it, nothing is written into the profile when the preset applies as configuration resolves it, and a run is identified by the value resolved and not by where it was named (§8, §9).
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the files that are the control panel's contract with the jar gain `effective-values.properties`, parsed in `java.base` (§1). It carries a note at its head.
- **Amends**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s table and its sentence on resolution: `k` and the resolution are presets at the numbers it chose, and the resolution becomes the profile key that sentence foresaw, shipping with a value where it refused one shipping unset (§8). It carries a note at its head.
- **Amends**: [ADR-020](0020-relevance-scoring-function.md)'s *"mean top-3 chunk similarity"*: the mean is of the top `vespera.relevance.top-similarities`, shipping 3, and a score under another number is on another scale (§8, §9). It carries a note at its head.
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md)'s *"The timeout budget and both consecutive-streak counts are fixed defaults in code"*: the consecutive-timeout count is the preset `vespera.docling.timeouts-in-a-row`, shipping 3, refused outside 2 to 4, and entering stage 2's run where it is not 3; the budget and the count of 5 stay in code (§8, §9). It carries a note at its head.
- **Amends**: [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md)'s *"Granularity is a code default, not a profile key"*: it is the preset `vespera.redundancy.shingle-words`, shipping 5, and still no profile key; its identity stays in the row's key, and enters stage 2's run where it is not `word:5` (§8, §9). It carries a note at its head.
- **Amends**: [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md)'s *"The thresholds are code defaults"*, *"0.80 and 0.95 live in code … and are changed by a new ADR"* and *"No new identity column is needed for them"*: the two cuts and the containment path's two counts ship as presets, what ships is changed by a record, and a value a machine names in their place enters stage 4's run, so that a run says which cuts judged it (§8, §9). Its *"no threshold an operator can move"* no longer holds; whether stage 4 ships a report is left open (What this does not decide). It carries a note at its head.
- **Amends**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md)'s table, whose five bands and twelve from each are the presets `vespera.relevance.sample.bands` and `vespera.relevance.sample.per-band`, the sixty being the shipped sample, and each entering the scoring run where it differs; and its section 3, *"A threshold is a number on a scale, and changing the model changes the scale"*: the scale is the embedder identity and, where it is not 3, the number of similarities a score is the mean of (§8, §9). It carries a note at its head.
- **Amends**: [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md)'s *"The OCR engine is pinned by the client"*: the client still names a preset on every conversion, never `auto`, and the name is the preset `vespera.docling.ocr-preset`, shipping `rapidocr`, which `sentOptions()` and so the extractor identity go on naming (§8, §9). It carries a note at its head.
- **Amends**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §2: the row of a key holding something nothing can be read from gains `clusterResolution`, after `extractionAttempt` (§9). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §1, *"The control panel creates no working directory"*: a save of `profile.yaml` makes one that does not exist, and on NTFS a folder's owner is compared (§7); §2, *"three files of a working directory and no other"*: a repair also writes a copy of the file it repairs, and the file not in the form read is shown and may be repaired where it was refused alone (§6); §3, *"never offers a provenance"*: it offers back the provenance the file states, and still none of its own (§2), and *"kept unread"*: a measurement's two lines are read to be shown, and kept byte for byte (§4); §4, whose checks gain the two of §5 and the row of `clusterResolution` (§9); and §5, whose table gains that key (§9). It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5, *"the jar's own defaults are not the control panel's to know"*, whose `docling-check` compares with the image the jar last said where no launch value names one, and whose two sidecars are asked at the address the jar last said where no launch value names one (§3, §11); §8, *"A key's control is empty"*: it holds what the file states (§2), and a key's form gains what §2, §4 and §5 add and a save its three checks, the form `next` the one that a value changed under the provenance of the old is refused, stopping there as §8 has it (§5); and §9, whose table of names gains those of §2, §4 to §7 and §11 to §12, and whose *"a `303` to `/` where it has none"* is not said of `/profile-repair`, which answers `303` to `/profile` (§6). It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §5's *"let go only once it is written"*: what a form held is let go where a save finds it no change, the file stating it already (§5); §6's *"nothing creates it"* and its Consequences' *"made outside the control panel before its first command"*: a save of `profile.yaml` makes a working directory that is not there, every start still refusing one and the preflight staying `bad` until it exists (§7); §7's *"Ollama is asked wherever its address is named"*, which gains the address the jar last said (§3); and §8's hint of `vespera.working-dir`, whose *"Create it if it is not there yet"* becomes *"The first value saved makes it if it is not there yet."* (§7). It carries a note at its head.

## Context

**What the pages hold today.** `/profile` holds the list and the sections ADR-331 §3 gives, a form for every `ProfileKey` the form `next` does not hold, each saving its one key. `current-<key>` shows what `profile.yaml` states, so a key the file leaves unset reads `not set`, though `bge-m3:latest` is in use: the control panel links no class of the jar (ADR-285 §3) and reads no configuration of its. Each control is ADR-331 §7's, empty, checked only once saved. `ProfileEdit.Stated` holds a value and a provenance, and a key's measurement is kept unread. A file not in the form ADR-295 §3 reads is refused whole with one line. A first save writes the file where the working directory exists, and a working directory that does not exist is refused.

**What a folder on NTFS holds.** Measured on Windows 11 and OpenJDK 26.0.2.1, by a user not elevated, with `java.base` alone. A folder `Files.createDirectories` makes on a drive of ordinary folders carries what its parent hands down, there `Authenticated Users: Modify`, so every other local user may write in it, each working directory `vespera run` makes by the same call being no different; and `FolderOwnership` asks nothing of a folder on Windows (ADR-292 §3). The same call given the `acl:acl` file attribute of one `AclEntry`, `ALLOW`, the user `user.name` names, every `AclEntryPermission`, `FILE_INHERIT` and `DIRECTORY_INHERIT`, under a parent that also hands down `Everyone: Full`, makes each folder holding that entry alone: the first it makes protected, `D:P(A;OICI;FA;;;<the user>)`, and those below inheriting from it. `setAcl` on a folder made gives the same protected entry, with a moment between the two. There the user creates a file, writes over it and moves another atomically over it; a file or a folder made below holds the entry, inherited; and an inheritable entry `icacls` then grants on the parent stops at the protected folder. A file moved in from outside keeps the entries it held, `Everyone` among them. `FileOwnerAttributeView` names the user as the owner of a folder the user made; `Files.setOwner` to `Administrators` or to `Everyone` is refused to a user not elevated, so one user's machine can make no folder another owns. `Everyone`, `SYSTEM` and `Administrators` read as `GroupPrincipal`, and a lookup of the SID `S-1-5-32-544` finds nothing. A FAT32 volume answers `false` to `FileStore.supportsFileAttributeView("acl")` and to `"owner"`. What a second account is refused, a process run as administrator and `vespera.db` under the entry are not measured.

**What moves a run.** A run's id is the hash of its implementation version, the configuration it consumed, its walk and its upstream runs (ADR-048). The implementation version is the last commit touching the folder of each module the stage names (ADR-058, `.mvn/scripts/implementation-versions.groovy`), whatever the commit changed, a comment included, and a later stage moves with an earlier one by naming its run. `StageModules` holds, today:

| Stage | Names |
| --- | --- |
| byte-level-reduction | `corpus` |
| extraction | `extraction`, `similarity` |
| content-census, content-redundancy | `similarity`, `extraction` |
| seed-measurement, embedding-scoring | `embedding`, `extraction` |
| arrangement, generation | `synthesis`, `extraction`, `embedding` |

No stage names `pipeline`, `profile`, `deliverable` or `confluence`, and `io.algernon.controlpanel` is no module. `application.yaml` lies outside every module's folder. So a value read by a class of `pipeline` can leave code with no version moving, and a constant of a module cannot leave its class without an edit of that module's folder.

**Where a configuration can stay silent.** A member of a run's `ConfigConsumed` can be written only where it differs from what every earlier run was minted under: `extractionAttempt` and `degenerateOutputWordFloor` (ADR-185, ADR-282) and `standingRemovals` (ADR-230) do so by `@JsonInclude(NON_NULL)` on the component, and an earlier run's text stays as it was.

**Where a cluster lies.** `document_cluster` is keyed by the scoring run, so a cluster's identity is the scoring run's, as the relevance floor's verdicts are.

**Where the other candidates are named today.** Each is a constant, and none is a member of a run's `ConfigConsumed`, so each reaches a run's id through the implementation version alone:

| Constant | Used by | Named besides |
| --- | --- | --- |
| `DoclingClient.PINNED_OCR_PRESET`, `rapidocr` | every conversion, as `ocr_preset` | `sentOptions()`, so the extractor identity, which keys `extraction_cache` and is `ExtractionConfigConsumed`'s first member, says `ocr_preset=rapidocr` (ADR-090) |
| `FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT`, 3 | `OccurrenceJudge`: below it a timeout is `extraction-failed`, at it the occurrence is set aside (ADR-071) | nothing |
| `ShingleParameters.DEFAULT`, 5 words | stage 2's shingler, stage 3's document frequency and boilerplate, stage 4's signatures and resolution | `identity()`, `word:5`, in `shingle.shingle_parameter_identity` and in a signature's identity (ADR-073, ADR-080) |
| `MinHashParameters.DEFAULT`, 128, 16, 8 and a seed | stage 4's signatures and bands | `identity()`, `perms:128;bands:16;rows:8;seed:6840335614489015382`, in a signature's identity (ADR-081) |
| `RedundancyThresholds.DEFAULT`, 0.80, 0.95, 32 and 24 | stage 4's resolution | nothing (ADR-082) |
| `RelevanceScorer.TOP_K`, 3 | every relevance score (ADR-020) | nothing |
| `RelevanceDistribution.BANDS` and `SAMPLED_PER_BAND`, 5 and 12 | the relevance report's draw, from the scoring run's id (ADR-088, ADR-220) | nothing |

**What a floor is compared on.** ADR-088 §3 makes the embedder identity a relevance floor's scale: `FloorReach`, `RelevanceLabels.forSeedSet` and `LoseNoDocumentationFloor` compare the identity each recorded answer was given under, which the label file names as `generatedUnderEmbedder`, with the run's, and a floor whose answers stand under another removes nothing. A floor stated with no answers at all applies (ADR-088). The other two floors a candidate bears on, `degenerateOutputConfidenceFloor`, read off the confidence Docling reports, and `boilerplateDocumentFrequencyFloor`, read off the document frequency of shingles, carry no scale.

**What was measured.** Over the graph `NearestNeighbourGraph.build` makes of identical vectors, the shape `EmbeddingScriptedBeans` gives every test, `Communities.of` finds 1 cluster at resolution 1.0 and one for each document at 2.5, 50 and 1000, for 3, 6, 8 and 20 documents and for `k` of 15, 2 and 1. So on that fixture the count of clusters tells the resolution used, and tells nothing of `k`. A whole-job invocation over 3 documents and a seed writes 3 rows of `document_cluster` and 1 ordinal. Over four documents in two close pairs, mean vectors `(1, 0)`, `(1, 0.05)`, `(1, 0.3)` and `(1, 0.35)`, it finds 1 cluster at resolution 1.0 and `k` of 15, 3 or 2, and 2 at `k` of 1, so on that fixture the count tells `k`.

**The image.** `vespera.docling.image` ships the processor build, which `compose.yaml` runs and `TestcontainersConfiguration` tests against, and `DoclingSidecarImageTest` holds the three as one; `compose.gpu.yaml` builds another image under another name, and an operator on it sets `VESPERA_DOCLING_IMAGE`. A device request a machine cannot satisfy stops `up` (ADR-165). The launch page's check says `not-checked` where the launch value is empty, so an operator who leaves it empty with the card's sidecar running learns of the mismatch when stage 2 stops.

## Decision

### Part A: the control panel is the complete editor of `profile.yaml`

### 1. The jar says what lies beneath the profile

**`effective-values.properties`**, in the working directory beside `vespera.db`, says what the jar would use where the profile names nothing. It is the jar's to write and the control panel's to read, as `next-invocation.properties` is, in `java.util.Properties`, UTF-8, read with `java.base` alone.

**When.** `vespera run` and `vespera label`, with or without `--auto`, write it once they hold the working-directory lock, past the last refusal of their start and before their first step: `vespera run` past a misnamed `--db-dir`, a root nothing names, a misplaced deliverable root, a preset §9 refuses, a corpus-root lock it cannot take and sidecars it cannot start, as the job is started; `vespera label` past a misnamed `--db-dir` and `--auto` given a file, as the progress page is written. `vespera publish` writes none: it reads no profile value, and its environment holds the one credential a process of the jar holds. An invocation refused at its start writes nothing, so the file stands as the last one left it. One that has started has written it, whatever then stops it: a `vespera label` that finds no answers, or sidecars `--auto` cannot start. It is written whole to a `.part` file and moved over the target; one that cannot be written is logged at WARN and fails nothing. **It is never deleted.** Unlike `next-invocation.properties`, what it says stays true of the jar that wrote it whatever became of the invocation, and how old it is is shown (below).

**What it holds, and nothing else.** A closed list, each key with its `<key>.source` beside it; a key nothing names, or one bound blank, is left out, its source with it, so the file never says an empty value:

| Key | Value |
| --- | --- |
| `written-at` | the moment it was written, ISO-8601 with an offset, and no source |
| `embeddingModel`, `generationModel`, `generationContextWindow`, `deliverableLanguage`, `clusterResolution` | what applies where the profile names none, never what the profile names: so a value cleared on the page can be shown as what it falls back to |
| `vespera.docling.image` | an image's name |
| `vespera.sidecars.start`, `vespera.sidecars.wait-at-most`, `vespera.progress-interval`, `vespera.open-browser`, `vespera.clustering.neighbours`, and the fourteen keys of §9's table from `vespera.docling.ocr-preset` to `vespera.relevance.sample.per-band`, in its order | as the application resolves each |
| `spring.ai.ollama.base-url`, `vespera.docling.base-url` | its scheme, host and port alone; where no configuration names it, as `application.yaml` names no `spring.ai.ollama.base-url`, the address the jar then uses, `SidecarStartSettings.DEFAULT_OLLAMA` or `DEFAULT_DOCLING`, with the source `code` |

| `<key>.source` | Where the value came from |
| --- | --- |
| `shipped` | the jar's own `application.yaml` |
| `local-file` | another file of application configuration, such as `application-local.yaml` |
| `environment` | an environment variable or a system property |
| `command-line` | the command line |
| `code` | no configuration: a value in the code |

**No secret enters it.** The list is closed and names keys, and is never a filter over what a process holds. Its values are model names, image names, numbers, durations, booleans and addresses cut to scheme, host and port, so a user and a password an address may carry do not reach it. It names no path, no corpus root, no working directory, no Confluence value and no text of a document.

**What the control panel reads.** The file, at every request that shows it, and at any time: it is moved into place whole, and what it says needs no ended child, unlike ADR-294 §4's. A file it cannot read is treated as absent. **A file older than the jar beside the control panel** (`ChildRuntime.jar()`, by modification time) **is stale**, shown with its value and marked: it may speak of a jar since replaced. **An absent file** says nothing, and the page says the jar has not said, which is not the same as unset.

### 2. What the page shows of a value, and what a field holds

Beside each key on `/profile`, `in-use-<key>` carries `data-value`, `data-source` and, where the value is the file's, `data-fresh`, `fresh` or `stale`:

| The profile states | The file | `data-value` | `data-source` |
| --- | --- | --- | --- |
| a value §4's checks of the value itself pass, whatever its provenance | any | that value | `profile` |
| a value §4's checks of the value itself refuse, whatever its provenance, a decimal comma among them | any | that value, as written | `unreadable` |
| none, or blank | names the key | the file's | the file's `.source` |
| none, or blank | present, and does not name it | empty | `unset` |
| none, or blank | absent, for a key that has a value beneath it, those of §1's second row the page holds | empty | `not-known` |
| none, or blank | absent, for any other key | empty | `unset` |

So a floor `vespera label --auto` set, whose provenance a save would refuse, and a value written by hand with no provenance, read `profile`.

**A field holds what the file states**, on `/profile` and `/next`: the key's control its value, and its provenance's control its provenance, each named as ADR-331 §7 has it. These are the operator's own words offered back, and the control panel still offers no provenance of its own. What a Browse or a refused save holds stands in place of the file's until its form is saved (ADR-331 §5), and a `seedFolder` keeps the white space before its name in the field and in `data-value` (ADR-331 §6). **A value the jar ships stays out of the field**, shown by `in-use-<key>` alone: pressing Save must not write into the profile a value nobody chose (ADR-298 §4). The `placeholder` stays ADR-331 §8's example. On `/next`, `arrangementApproved` holds the file's `arrangement` whatever the profile states, and `seedFolder`, where the profile states none, the last seed folder, as ADR-302 §8 has them.

### 3. A control of the key's type

ADR-331 §7's controls stand, each in its key's form: a number `type=number step=any` with no `min` and no `max`, a list a text field, a model's `list="models"`, the language's `select` of `en` and `it`, and any other a text field. Its reasons stand with them: a bound drawn is a value nobody decided, and a second copy of a rule `ProfileEdit` holds.

**A value a typed control could not hold stands in one that can.** A field is now filled from the file, and a browser blanks a `number` control whose value is not a number, so a save would unset the key, losing a value the operator wrote. So a stated value that is no valid floating-point number of HTML is drawn in `input type="text"`, as written, and the `select` gains an option holding a stated value it does not list, selected. With no `min` and `step=any` there is no other number a browser refuses. The controls' attributes help a browser; the server's checks are what decide, and a browser's own handling of them is on no test (ADR-297's Consequences).

**A list's pieces.** `it, en` in the file and `it en` in the field are one value, no change (§5): the server compares a list's pieces, split on white space and commas as the jar reads them (`NameEndingsValue`, `LanguagesValue`) and joined by one space. A list changed is written as it was typed, as before.

**The models offered.** ADR-331 §7's `models`, the names `/` was last told: `/profile` asks no sidecar, its one `GET` staying quick. **`/` asks at the launch value's address, or, where that is empty, at `spring.ai.ollama.base-url` from §1's file**, so a default install is asked at the address the jar falls back to (§1). A model typed by hand is saved as ADR-295 §4 has it, any text. A model not served is the step's to pull and, where that fails, to refuse, at stage 5 (ADR-202, ADR-329).

### 4. What a key is, and its measurement

`about-<key>` holds one line saying what the key is, each its own, beside ADR-331 §8's `#hint-<key>`, which says what the value is read from; its words are no decision's. `measurement-<key>`, where the file holds a measurement under the key, carries `data-source` and `data-refreshed-at`, read from its `source` and `refreshedAt` lines, as the file holds them. A key whose measurement is `null`, or whose lines beneath `measurement:` are not those two, shows none, and the bytes are kept as ADR-295 §3 keeps them either way. `ProfileEdit.Stated` keeps its two components, which the contract tests name, and the measurement is read beside it.

### 5. A save of one value

**`/profile` keeps ADR-331 §3's list and its one form for each value**, each key's form named by the key and saving that key; a key the next invocation wants stands on `/next` and not here, as ADR-302 §8 has it. A field being filled now, each form gains a hidden `drawn`: the hexadecimal SHA-256 of the value as read from what is posted, its white space lost as ADR-295 §3 and ADR-331 §6 lose it and a list's pieces joined by one space, and of the provenance, each with any line feed or carriage return removed as a `text` control removes it, a NUL between them. So `it, en` in the file and `it en` in the field are no change, and nor is a provenance the file holds over two lines, which a text control cannot hold.

**A save, in order:**

1. **It is compared.** Where the digest of what is posted, worked out as `drawn` was, is `drawn`, nothing is written, no lock is taken and no holder line written, and `outcome` says so, and what the form held is let go, the file stating it already: so pressing Save on a key left alone rewrites nothing, and does not meet ADR-295 §4's refusal of the provenance `vespera label --auto` wrote.
2. **It is checked**, before anything is written, by three rules, each giving `<key> is not saved: <reason>` in `outcome`:
   - the digest of what the file now states, worked out as `drawn` was, is not `drawn`: `profile.yaml has changed this value since the page was drawn; look at it and save again`, since a field filled from a file that has moved would otherwise write the old value over a floor `vespera label --auto` wrote behind the page;
   - the value changed and the provenance is what the file states: `its value changed and its provenance did not; write how you arrived at the new value`, because a field filled by §2 would otherwise leave the old reason under the new number;
   - ADR-295 §4's checks, the blank provenance among them.
3. **It is written**, one of ADR-295's writes, under its lock, with its checks of the folder and of a child.

**A save refused keeps what was typed**: its form's fields are held, and shown again until it is written, or a save finds it no change, which stands written already, as ADR-331 §5 has it but for that, so nothing typed is lost.

**`/next`** saves as ADR-302 §8 has it, in the form's order, the first refused stopping it and what was written staying written. Its controls are filled by §2, so it gains the second rule, comparing with the file where it holds no `drawn`. `save-then-run` starts `run` only once every key is written.

The cost beside each key (ADR-302 §8) is what pressing Save confirms, as before.

### 6. A file not in the jar's form

The form read, ADR-295 §3's, does not change, and `ProfileEdit.rewrite` goes on refusing any other. **What changes is what the page does.** Where the file is in another form, `/profile` holds no form of a key and no `#profile-list`, and shows:

- `form-problem`, carrying `data-line`, the first line not in the form, from 1, and `data-repairable`, `true` or `false`, with a line saying why;
- `profile-text`, the file's text, as it stands;
- where it is repairable, `repair-changes`, an item for each change, carrying `data-line`, the line it starts on, and saying what it does, and **the form `repair`**, `POST` to `/profile-repair`, a hidden `drawn` holding the hexadecimal SHA-256 of the text and one button `repair`.

**The repair may do three things, and is offered only where they are all that is wrong:**

| Where the file has | The repair |
| --- | --- |
| a whole line that is a comment | removes the line with its end |
| a comment after a key, or after a scalar on its line, a measurement's lines among them | removes the comment, and writes a scalar as ADR-295 §3 writes one |
| a plain or single-quoted scalar of `value` or `provenance` over several lines, no line of it blank | joins them with one space, as YAML folds them, into one line written as ADR-295 §3 writes a scalar |

Every other byte is kept, line ends included. **Everything else is the operator's**, shown with its line and the file's text: a key `Profile` does not hold, whose value and provenance no repair may drop or guess a home for; a key twice, of which the repair cannot say which stands; a flow mapping, a block scalar, a tab in an indent, a key written flat. A file the jar reads, in whatever form, the next invocation's census writes back in the jar's own (ADR-295's Consequences), and the page may say so.

**A value is never the repair's to change.** A decimal comma in a double-quoted value is in the form, and is no problem of this section: the page shows it `unreadable` (§2) and the operator fixes it.

**What is removed is kept.** Under the lock, before anything else, the file is copied whole to `profile.yaml.before-repair-<yyyyMMdd-HHmmss>` beside it, never over an earlier one; a copy that cannot be made leaves the file as it was. Comments are neither values nor provenances, the operator may have meant them, and the jar's next write would drop them with no copy.

**It is confirmed by what was shown.** Under the lock the repair is worked out again from the file. Where its digest is not `drawn`, nothing is changed and no copy made, with the line `profile.yaml has changed since this page was drawn, so it was not repaired`. A repair and a `GET` of `/profile-repair` each answer `303` to `/profile`, the `GET` changing nothing.

### 7. Create if missing, edit if present

**A working directory that does not exist is made by a save of `profile.yaml`**, by `Files.createDirectories` given the rule as a file attribute, so that each folder it makes is born with it and no moment lies between: on a file system with POSIX permissions `rwx------`; on a volume with access control lists, NTFS among them, the `acl:acl` attribute of one `AclEntry`, `ALLOW`, the user `user.name` names, every `AclEntryPermission`, `FILE_INHERIT` and `DIRECTORY_INHERIT`, in `java.base` as ADR-285 §3 asks. The user is named and the owner is not, since a process run as administrator owns what it makes as the Administrators group and the operator must go on writing there. **On a volume with neither**, such as FAT32, which `FileStore.supportsFileAttributeView` tells of the nearest folder that exists, the folder is made as the volume makes it and the save goes on, there being nothing to set and nobody to keep out by (Known limits). Once made it is checked again by its real path, as ADR-295 §1 checks a folder, and on NTFS its entry is read back and must be that one alone, so a folder another user made first in that moment is refused by its owner. A path where a file lies is refused as before. The other two files go on refusing a folder that does not exist: no question is answered and no code set named before a run has made it.

**A folder that exists is checked by what can be told of it.** On POSIX permissions as before: its owner and that no group or other may write. **On NTFS its owner alone**: it is this user's, or a group's, as a process run as administrator leaves it (ADR-292 §3), and any other owner is refused with ADR-295 §1's line. Who else may write in it is not compared: every folder of an ordinary drive hands that right down, and so does each working directory `vespera run` has made, so comparing it would refuse every one of them (Known limits). On a volume with no owner to read, nothing but its being a folder. The folder of the launch values stays as ADR-292 §3 has it.

**Only this save makes one.** Every start still refuses a working directory that is not there (ADR-331 §6): its status stays `no-such-folder` and the preflight's row `bad` until it exists, the folder page making none. Nor does a value refused make one.

**It is made only by a `POST`, past §5's second step**, so a value refused makes nothing, and a `GET` writes nothing (ADR-302 §1). The file holds only what was saved, and ADR-062's merge adds the rest as unset at the jar's next load.

`profile-file` says where things stand, carrying `data-file` and `data-folder`, each `present` or `absent`, and `data-path`, the absolute path of the file; its words say that the first save creates the file there, and the folder with it where that is absent.

### Part B: presets

### 8. What a preset is, and which choices are presets

**A preset** is the value of a technical choice shipped in `src/main/resources/application.yaml`, where it is read, overridden and listed, in place of a constant of the code. Spring's own layers override it for a machine. Where a choice is also a judgement about one corpus, the record that makes it a preset may give it a profile key, with its provenance, as ADR-298 has it of the embedding model. A measured floor is no preset: it ships unset and points at its measurement (ADR-006).

**Every candidate is a preset.** What it decides is not settled by its being one. Each enters the identity of the run it shapes where it differs from the value every earlier run was minted under (§9), so that a machine naming another value gets runs of its own beside those of the shipped one, and no finished run is read as if made under a value it was not. What another value does to a floor or an answer read under the shipped one is said of each below and in §9. None of the eight below `k` gets a profile key: each is a mechanism's number and no judgement about one corpus (ADR-071, ADR-073, ADR-082), so none is edited on the control panel until a record gives it one on a measurement of what it moves (§12).

| Value | Where | Decision | Control panel | What it does to run identity |
| --- | --- | --- | --- | --- |
| resolution, 1.0 | `Communities.DEFAULT_RESOLUTION` | **Preset** `vespera.clustering.resolution`, overridden by the profile's new `clusterResolution`. How finely a partition divides is what `cluster-sizes.html` shows and what ADR-087 foresaw a key for; its objection, a key shipping unset and unanswerable, does not meet a key shipping a value. | **Now**: a key of `/profile`, with `cost-` and `report-` linking `cluster-sizes.html` | Enters the scoring run where it is not 1.0 (§9) |
| `k`, 15 | `Clustering.NEIGHBOURS` | **Preset** `vespera.clustering.neighbours`. An operational number, no judgement about a corpus (ADR-087), so no profile key; it stands beside the resolution in the graph it shapes, and costs nothing further. | **Later**: shown read-only now (§12); edited once a record gives it a profile key on a measurement of what it moves | Enters the scoring run where it is not 15 (§9) |
| ocr preset, `rapidocr` | `DoclingClient.PINNED_OCR_PRESET` | **Preset** `vespera.docling.ocr-preset`. ADR-090's pin was against `auto`, an engine the sidecar picks and nothing records; a preset named by configuration and written into the extractor identity is recorded, so its drift is neither invisible nor silent. `auto` stays refused (§9). | **Later**: shown read-only now (§12) | None at `rapidocr`: `sentOptions()` names it as now. Another is another extractor: a stage-2 run and every later one, and every conversion asked again (§9) |
| three timeouts in a row | `FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT` | **Preset** `vespera.docling.timeouts-in-a-row`. It decides whether a timeout is the document's verdict or the converter's fault (ADR-071), so it shapes stage 2's verdicts, and is held to 2 to 4 by ADR-071's own reasons (§9). The count of 5 and the budget stay in code. | **Later**: shown read-only now (§12) | Enters the extraction run where it is not 3 (§9) |
| five-word shingles | `similarity.ShingleParameters.DEFAULT` | **Preset** `vespera.redundancy.shingle-words`. ADR-073 parked its real value on data stage 3 shows; its identity already keys every shingle row, so another window is a measurement of its own, not a correction of this one. | **Later**: shown read-only now (§12) | Enters the extraction run, as `word:<n>`, where it is not 5 (§9) |
| 128 permutations, 16 bands, 8 rows, its seed | `similarity.MinHashParameters.DEFAULT` | **Preset** `vespera.redundancy.minhash.permutations`, `.bands`, `.rows-per-band` and `.seed`. ADR-081's measured choice stays what ships; `identity()` already keys a signature. | **Later**: shown read-only now (§12) | Enters the content-redundancy run, as its `identity()`, where that is not the shipped one (§9) |
| 0.80, 0.95, 32 and 24 | `similarity.RedundancyThresholds.DEFAULT` | **Preset** `vespera.redundancy.near-duplicate-jaccard`, `vespera.redundancy.containment-index`, `vespera.redundancy.rare-shingle-sample` and `vespera.redundancy.rare-shingle-hits`. ADR-082's objection to a profile key stands, and a run now says which cuts judged it, where a changed constant said so only through the version. | **Later**: shown read-only now (§12) | Enters the content-redundancy run where any of the four differs (§9) |
| top 3, the mean's K | `RelevanceScorer.TOP_K` | **Preset** `vespera.relevance.top-similarities`. It defines ADR-020's score, so another number is another scale: it joins the scale a floor and an answer are compared on (§9), and a floor read at 3 removes nothing at another number where answers stand under 3. | **Later**: shown read-only now (§12) | Enters the scoring run where it is not 3 (§9) |
| five bands | `RelevanceDistribution.BANDS` | **Preset** `vespera.relevance.sample.bands`. ADR-088 fixed it so that one archive is divided as another is; a machine naming another divides its archives alike, and the run says so. | **Later**: shown read-only now (§12) | Enters the scoring run where it is not 5 (§9) |
| twelve from each | `RelevanceDistribution.SAMPLED_PER_BAND` | **Preset** `vespera.relevance.sample.per-band`. Five bands of twelve is ADR-088's sixty, the ceiling on one sitting, and is what ships; a machine whose answers a local model gives (ADR-197) may ask more. The README's sixty is the shipped sample's. | **Later**: shown read-only now (§12) | Enters the scoring run where it is not 12 (§9) |
| `vespera.sidecars.start`, `vespera.sidecars.wait-at-most` | `application.yaml` already | **Stays a preset.** | **Later**, as a launch value (§12); shown read-only now | None: in no run's configuration (ADR-323 §9) |
| `vespera.progress-interval` | `application.yaml` already | **Stays a preset.** | **Later**, as a launch value (§12); shown read-only now | None: in no run's configuration (ADR-265) |
| `vespera.open-browser` | `application.yaml` already | **Stays a preset.** | **Never**: under the control panel the child runs with `CI=1` and opens nothing, so there is nothing to set (ADR-325 §8, ADR-327 §5); shown read-only | None: in no run's configuration (ADR-327 §6) |
| `vespera.docling.image` | `application.yaml` already | **Stays the processor build** (§11). | **Now**, on the launch page, as the launch value it is (§11) | Unchanged at the shipped value; another image is another extractor (ADR-147) |

### 9. The presets' identity

**Every preset of §8 ships in `application.yaml` at the value its constant held**, held exactly by a test, as ADR-298 §7 holds the embedding model: another shipped value re-clusters, re-judges or asks about other documents in every corpus whose machine names none, and is changed by a record.

| Key | Ships |
| --- | --- |
| `vespera.clustering.resolution` | `1.0` |
| `vespera.clustering.neighbours` | `15` |
| `vespera.docling.ocr-preset` | `rapidocr` |
| `vespera.docling.timeouts-in-a-row` | `3` |
| `vespera.redundancy.shingle-words` | `5` |
| `vespera.redundancy.minhash.permutations` | `128` |
| `vespera.redundancy.minhash.bands` | `16` |
| `vespera.redundancy.minhash.rows-per-band` | `8` |
| `vespera.redundancy.minhash.seed` | `6840335614489015382`: `0x5EED_C0FF_EE12_3456L` in decimal, as `identity()` writes it |
| `vespera.redundancy.near-duplicate-jaccard` | `0.80` |
| `vespera.redundancy.containment-index` | `0.95` |
| `vespera.redundancy.rare-shingle-sample` | `32` |
| `vespera.redundancy.rare-shingle-hits` | `24` |
| `vespera.relevance.top-similarities` | `3` |
| `vespera.relevance.sample.bands` | `5` |
| `vespera.relevance.sample.per-band` | `12` |

**The profile's `clusterResolution` overrides the first.** It is `Profile`'s last component and `ProfileKey`'s last constant: `NumericValue`, earliest run `EMBEDDING_SCORING`, unset by default with no provenance written when the preset applies (ADR-298 §4). It is saved where it is a finite number above 0: `"<value>" is not a number above 0`. Unset or blank, **the preset applies, as configuration resolves it**. **Unreadable, the same**, as `translationSimilarityFloor` applies nothing, and `NextAction` names it after `extractionAttempt`, in ADR-294 §2's row, whose keys are named one at a time, the first that holds something nothing can be read from. **A number written by hand that is no finite number above 0**, `0`, `-1`, `NaN` or `Infinity`, which `NumericValue` reads as answered, **is ignored in the same way**, as `ExtractionAttempt` ignores a number no attempt is numbered by (ADR-185): the preset applies, the scoring run is the one that value's absence arrives at, `NextAction` names the key, and §2's page shows it `unreadable`, since ADR-295 §4 would refuse it. A profile value is an answer that did not arrive, which the closing line can go back and ask for; what stops an invocation is below, a machine's value nothing can ask after. It is no gate. A class of `pipeline` resolves it, as `EmbeddingModelGate` resolves the model, and is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule: what it resolves names the run.

**Each enters the run whose output it shapes**, as a component appended to that run's `ConfigConsumed`, in this order, each `@JsonInclude(NON_NULL)`, written where the value resolved is not the one in bold:

| Run, and the component it follows | Component | Written where the value resolved is not |
| --- | --- | --- |
| embedding-scoring, after `preferredLanguages`; the record `document_cluster` is keyed under | `clusterResolution`, a number | **1.0** |
| | `clusterNeighbours`, a whole number | **15** |
| | `topSimilarities`, a whole number | **3** |
| | `sampleBands`, a whole number | **5** |
| | `sampledPerBand`, a whole number | **12** |
| extraction, after `degenerateOutputWordFloor` | `timeoutsInARow`, a whole number | **3** |
| | `shingleIdentity`, the text of `ShingleParameters.identity()` | **`word:5`** |
| content-redundancy, after `boilerplateDocumentFrequencyFloor` | `minHash`, the text of `MinHashParameters.identity()` | **`perms:128;bands:16;rows:8;seed:6840335614489015382`** |
| | `redundancyThresholds`, the text of `RedundancyThresholds.identity()`, which this adds: `near-duplicate:<jaccard>;containment:<index>;sample:<n>;hits:<n>`, each number as Java's `toString` writes it | **`near-duplicate:0.8;containment:0.95;sample:32;hits:24`** |

**The OCR preset needs no component.** `sentOptions()` names it today, as `ocr_preset=rapidocr`, and goes on naming the preset the client sends, so the extractor identity holds it at every value, and at `rapidocr` its text is what it was. Another preset is another extractor (ADR-090, ADR-147): another stage-2 run, and, `extraction_cache` being keyed by the extractor identity, every conversion asked of the sidecar again.

The values in bold stand in `StageRuns` as what every run before the presets was minted under, and they do not move if a later record changes what ships: a preset shipped at another value is written, so it cannot be taken for a run of the earlier one. So, where configuration names no other, **a stated 1.0 and an unset key arrive at one scoring run** (ADR-298 §3), a machine naming a preset's shipped value arrives at the run that names none, and every run's configuration text at the shipped values is what it was, as `RunIdentityGoldenTest` holds, unedited. **Each run reads its presets as it is minted and hands them to the steps that use them**, so the number that names the run is the number that works (ADR-117): the scoring run's to scoring, clustering and the relevance report's draw; stage 2's to its judge of a timeout and its shingler, and the window, through the extraction run, to stage 3's document frequency and boilerplate and to stage 4's signatures and resolution, which read the shingles of the extraction run they name; stage 4's to its signatures, bands and resolution.

**The scale a relevance score is on gains the number of similarities.** `topSimilarities` changes what a score is (ADR-020), so it joins the scale ADR-088 §3 compares on: **the scale is the embedder identity, followed, where the number resolved is not 3, by `;top:<n>`**. `FloorReach`, `RelevanceLabels.forSeedSet`, `LoseNoDocumentationFloor`, the identity an answer is recorded under and the label file's `generatedUnderEmbedder` all take the scale where they take the embedder identity now. It is composed in `embedding`, because it decides whether a floor removes (ADR-222). The vector's own identity, which keys `vector`, is untouched, so nothing is embedded again; and at 3 the scale is the embedder identity byte for byte, so no answer recorded moves. Under another number:

- **the answers stay**, facts about documents (ADR-088), and stand under the scale they were given on;
- **the floor stated removes nothing while no answer stands under the new scale**, those answers standing under another, and the report says why, as it does for another model (ADR-226). **Once one answer stands under the new scale it removes**: `RelevanceLabels.forSeedSet(seedSet, identity)` (ADR-244) reads the answers of that scale alone where any stands, so the earlier ones no longer keep the floor from applying, and a floor read at 3 then removes at the new number though nobody read it there, until the operator states another. The next bullet is the ordinary road to that, a single answer enough (Known limits);
- **the run draws its own sample**, whose answers, a person's or `vespera label --auto`'s (ADR-197), are recorded under the new scale, and a floor is read off them;
- **a floor stated with no answers at all applies**, as ADR-088 has it of every floor nothing was labelled for (Known limits).

**The sample is the scoring run's.** It is drawn from that run's id (ADR-088, ADR-220), so its two numbers enter the run, and the same run always asks about the same documents. A label file names its run, so a file drawn under another shape is refused as ADR-088 refuses another sample's. The relevance report says the number each band was asked for where it says *twelve* now, and the README's sixty is said as the shipped sample's.

**The cost says what a key does to a sample being answered.** Any change of the scoring run's identity does what §10 says: another draw, `relevance-labels.yaml` written over, a copy of the earlier file refused (#111), and the answers recorded kept by path and seed set (ADR-097). This is true of every key whose runs include embedding scoring, and not of `clusterResolution` alone, so `ProfileKey.Cost` gains `sampleDrawnAgain`, true of each such key and false of `arrangementApproved`, `generationModel`, `generationContextWindow`, `clusterPageByteCeiling` and `deliverableLanguage`. `cost-<key>` carries `data-sample-drawn-again`, `true` or `false`, on `/profile` and `/next` alike, and where it is `true` its words say so.

**A value no run can be made under**, named by configuration, **is refused at the start of `vespera run`**: in `VesperaCommand.Run.call`, past a misnamed `--db-dir`, a root nothing names and a misplaced deliverable root, and before the corpus-root lock is taken, the sidecars started and the job started, with one line on standard error naming the key and no stack trace, and a non-zero exit. So the corpus-root lock is not taken, no walk made, no run minted and §1's file not written, the working-directory lock being the invocation's already, and an operator learns of it in the first second and not at the stage that would use the value. Every preset of the table is checked, whichever stage this invocation would reach. `vespera label` and `vespera publish`, which mint no run and read no preset, are not stopped by one, and nor is a run in which the profile's `clusterResolution` stands in place of a configured resolution, which then names no run; the start reads the profile as its refusal of a deliverable root does. Each is refused by the type of the module that uses it, and the line is `pipeline`'s:

| Key | Refused where it is | The line |
| --- | --- | --- |
| `vespera.clustering.resolution` | not a finite number above 0 | `vespera.clustering.resolution is <value>: a number above 0` |
| `vespera.clustering.neighbours`, `vespera.redundancy.shingle-words`, `vespera.redundancy.rare-shingle-sample`, `vespera.relevance.top-similarities`, `vespera.relevance.sample.bands`, `vespera.relevance.sample.per-band` | not a whole number from 1 to 2147483647 | `<key> is <value>: a whole number from 1 to 2147483647` |
| `vespera.docling.ocr-preset` | blank, `auto` in any case, or holding a character other than a letter, a digit, `-`, `_` or `.` | `vespera.docling.ocr-preset is "<value>": one preset's name, of letters, digits, '-', '_' and '.', and never auto, which the converter resolves where nothing records it` |
| `vespera.docling.timeouts-in-a-row` | not a whole number from 2 to 4 | `vespera.docling.timeouts-in-a-row is <value>: a whole number from 2 to 4, so that one slow document can still earn its verdict and the count stays below the five failures in a row that stop the step` |
| `vespera.redundancy.minhash.permutations`, `.bands`, `.rows-per-band` | not a whole number from 1 to 2147483647 | `<key> is <value>: a whole number from 1 to 2147483647` |
| the three together | the bands times their rows not the permutations | `vespera.redundancy.minhash is <p> permutations in <b> bands of <r> rows: the bands times their rows must be the permutations` |
| `vespera.redundancy.minhash.seed` | not a whole number a Java `long` holds | `vespera.redundancy.minhash.seed is <value>: a whole number from -9223372036854775808 to 9223372036854775807` |
| `vespera.redundancy.near-duplicate-jaccard`, `vespera.redundancy.containment-index` | not a number above 0 and at most 1 | `<key> is <value>: a number above 0 and at most 1` |
| `vespera.redundancy.rare-shingle-hits` | not a whole number from 1 to the sample | `vespera.redundancy.rare-shingle-hits is <value>: a whole number from 1 to vespera.redundancy.rare-shingle-sample, <sample>` |

Why each bound: a resolution not above 0, or a count of neighbours, words, permutations, bands, rows, similarities or documents below 1, builds nothing, and a count past 2147483647 is one each type holds in an `int` and so cannot hold; a cut above 1 judges no pair and one of 0 judges every pair redundant; more hits than the sample retrieves no container. `auto` is what ADR-090 ended, and the extractor identity is text of `key=value` pairs joined by `;`, so a name holding `;` or `=` would write an identity that reads as other options. A count of 1 would set aside every timeout, so that one slow document could never earn `extraction-failed`, which ADR-071 keeps it able to; and ADR-071 sets the breaker of five *"Deliberately higher than the timeout count"*.

### 10. Which run ids move

**Part A moves none.** It changes `io.algernon.controlpanel`, outside every module, and `pipeline`, which no stage names; the file of §1 is in no run's configuration. **`clusterResolution` is wholly Part B**: `Profile`, `ProfileKey`, §1's row, its control following §3's rule for a number, `NextAction`, `REPORTS_OF_A_KEY`, `StageRuns`, `embedding`. So are the presets in `application.yaml`, and so §1's lines of them are written only once Part B ships them, a key nothing names being left out. Part A touches neither `profile` nor `embedding`, and its page holds the eighteen keys.

**Part B moves seven stages, once, because it edits `extraction`, `similarity` and `embedding`, and not because of any value:**

| Stage | Names | When the change that edits the three lands |
| --- | --- | --- |
| byte-level-reduction | `corpus` | **no move** |
| extraction | `extraction`, `similarity` | **move** |
| content-census, content-redundancy | `similarity`, `extraction` | **move** |
| seed-measurement, embedding-scoring | `embedding`, `extraction` | **move** |
| arrangement, generation | `synthesis`, `extraction`, `embedding` | **move** |

Each of the seven names a module whose folder the change touches, and each after extraction names the run before it too. Its configuration text stays byte for byte at the shipped values, so this is the whole of the move, and it cannot be less: the constants lie in classes of those three modules, and ADR-058's version is the last commit touching a folder, whatever it did. `application.yaml`, `Profile` and `StageRuns` alone would leave presets that reach no conversion, no shingle and no score. **Part B therefore lands as one change of its own, after Part A**, so that Part A asks no pass of anyone and the seven move once.

**What the move does again, and what it reads back.** At the shipped values the extractor identity and the embedder identity do not move, so the new stage-2 run reads every conversion from `extraction_cache`, every vector is read from `vector`, and no document is converted or embedded again. Every stage from extraction on does its own work again under its new run; the arrangement is a new run, so an approval naming the earlier one opens nothing (ADR-107); and generation writes every cluster again, `synthesis_doc` being keyed by its run.

**What it does to a sample being answered, which any change of the scoring run's identity does.** This move does it, and so does every value that enters that run, the profile's `clusterResolution` and the four presets beside it. A new scoring run draws its own sample from its own id (ADR-088, ADR-220), sixty at the shipped numbers, so other documents are asked about, and its report step writes `relevance-labels.yaml` over the last one. A copy of the earlier file offered to `vespera label` is refused whole, naming both runs (#111), since the file a sample is checked against is the one the last scoring run wrote; until that step has written the new one, `vespera label` goes on taking the earlier. The answers recorded stay, keyed by path and seed set (ADR-097), count in the new run's spread, and show in the new file wherever the new draw asks about the same document (ADR-169). An answer typed into the file and not yet recorded goes with the file written over. So Part B's landing reaches every corpus part way through its sample, once, at the first `vespera run` after it: what `vespera label` recorded is kept, and the sitting goes on over the new draw.

Clustering's two numbers alone, in `embedding`, would have moved the last four. A constant of `extraction` or `similarity` reaches extraction and every stage after it, and the operator's direction moves five such constants; no split of the change keeps a stage, since each module's folder is touched either way and an extraction move reaches every later stage through the runs they name.

### 11. The Docling image stays the processor build

**`vespera.docling.image` ships `vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2`, unchanged.** No run id moves: `RunIdentityGoldenTest`'s literal stands, and an operator on the card goes on naming its image.

| Where | What this does |
| --- | --- |
| a machine with no card | Nothing changes: `compose.yaml`'s `up` succeeds, and the image it runs is the one named (ADR-165). Shipping the card's image would have every such machine stop at `up` on a device request, or at stage 2 |
| CI | Nothing changes: `TestcontainersConfiguration` runs the processor build, `DoclingSidecarImageTest` holds the three places as one, and no job has a card |
| the operator's machine | The card's image is that machine's to name: as the launch value the control panel remembers, as `VESPERA_DOCLING_IMAGE`, or in `application-local.yaml`. A value that is one machine's stays out of what ships |

**What the control panel adds closes the trap on the launch page.** The image is the launch value it was, `vespera.docling.image` in the form `launch`, passed to the child as before:

- `/version` is asked wherever an address is known, the launch value's, or else `vespera.docling.base-url` from §1's file; `docling-running`, carrying `data-image`, shows what the sidecar says it runs.
- `docling-check`, where the launch value is empty, compares with `vespera.docling.image` from §1's file, and says `another-image` with the warning naming both, as it does for a launch value; with no file to compare with it stays `not-checked`.
- The control's `list="docling-images"` stays ADR-331 §7's, the jar's default and `compose.gpu.yaml`'s, read by no file of the jar's: `docling-running` shows the image the sidecar runs, which may be typed. The image picked is a launch value, stored as an invocation starts. **The control panel writes it into no file of the working directory; the jar's §1 file says it as `environment`, and the extractor identity as ever.**

The check stays advice: Run stays enabled, and stage 2 stops before a conversion is recorded under another image (ADR-179).

### 12. A key that is not the profile's

**Such a key is the application's, of one machine, and its store is not `profile.yaml`**: a value there would be read with the corpus's judgements, travel with the corpus and enter no run honestly. Nor may the control panel write `application.yaml`, which lies inside the jar, or `application-local.yaml`, which is the operator's by hand and outside ADR-295 §2's files, in a form it does not read.

**So what the control panel shows of one comes from §1's file, and what it edits of one is a launch value** (ADR-292): held per user in the launch values' own file, keyed by the jar's own property as text, and passed in the child's environment by its name in capitals, each dot and hyphen an underscore (`VESPERA_SIDECARS_WAIT_AT_MOST`), as ADR-292 §4 spells `VESPERA_DOCLING_BASE_URL`. That links no class of the jar (ADR-285 §3), leaves `profile.yaml` the profile's store, and a contract test holds the name. `machine-values` on the launch page, placed by the implementation among ADR-331 §3's cards, lists `vespera.sidecars.start`, `vespera.sidecars.wait-at-most`, `vespera.progress-interval`, `vespera.open-browser`, `vespera.clustering.neighbours` and then the fourteen keys of §9's table from `vespera.docling.ocr-preset` to `vespera.relevance.sample.per-band`, in that order, each an item with `data-key`, `data-value` and `data-source`, and no control; a key the file does not name is left out. A key that enters no run becomes editable by the record that adds it to `LaunchValues` and to ADR-292 §4's table.

**A preset that enters a run's identity is not a launch value**: it decides what a run produces, and a launch value is passed to whatever corpus is started next, a choice the control panel would be making on every start with nobody having said why. **A machine may still name one in its own configuration, by hand** (§8): the operator's own act, outside the control panel, which the run's configuration records by value and nothing records by reason (Known limits). The control panel adds no second way to that: what it edits it edits with a provenance, so `vespera.clustering.neighbours` and the fourteen after it are edited there, once they are, each through a profile key, and until then are shown and not edited.

### 13. The rules a new write path obeys

- `/profile-repair` is a path of the pages, so `Host`, `Sec-Fetch-Site` and `Origin` stand before it as before every page (ADR-325 §2), and a form from another site is refused before any handler. No new path answers a `GET` with an effect (ADR-302 §1).
- The server's classes still write nothing; the pages' classes write (ADR-291 §8). The picker's question and `/version` are read-only, through `HttpURLConnection` and `JsonReader` (ADR-285 §3, ADR-302 §5). Nothing is started, stopped or pulled (ADR-179).
- `WorkingDirectoryWrite` makes every write of §5, §6 and §7, while no child runs, holding the lock, one at a time (ADR-295 §1). A repair's copy holds what the profile holds, and no text of a document and no token (ADR-291 §8).
- No page gains a script or an event attribute (ADR-325 §4.1, ADR-331 §4): every control here is one a browser draws.

### 14. What the implementation owes

**Part A, in `io.algernon.controlpanel`**: a reader of §1's file beside `NextInvocation`; `ValuePages` and `NextPages` as §2 and §4 to §6 say, and `ControlPanelPage.keyControl` as §3 says; `ProfileEdit` reading a measurement and a repair and able to check a value apart from its provenance (§2), its `rewrite` and its form unchanged; `WorkingDirectoryWrite` making a folder and a repair's copy, and `FolderOwnership` comparing an owner on NTFS for the working directory's folder and not the launch values' (§7); `SidecarChecks` and `LaunchPages` as §3 and §11 say, and the hint of §7; `ProfileKey.Cost` gaining `sampleDrawnAgain` (§9); `ProfileKey` saying which keys have a value beneath them, for the four it holds; and every name of ADR-302 §9's table these add. All of it in `java.base` and `jdk.httpserver`.

**Part A, in `pipeline`**: the writer of §1's file, at the place §1 says, with no preset to write yet.

**Part B, in `io.algernon.controlpanel`**: `ProfileKey` gaining `CLUSTER_RESOLUTION` with its hint and example (ADR-331 §8), and the new key among those with a value beneath them; §1's row; `NextPages.REPORTS_OF_A_KEY` naming `cluster-sizes.html`. **In `pipeline`**: `StageRuns`, the resolving class, its lines of refusal, the check of §9 in `VesperaCommand.Run.call` and `NextAction`, the resolving class reading every preset of §9's table and listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, and §1's writer naming the presets; `ClusteringTasklet`, `RelevanceScoringTasklet`, `RelevanceReportTasklet`, `RelevanceFloor`, `RelevanceFloorTasklet`, `RedundancyResolutionTasklet` and the tasklets of stages 2 to 4a handed what the run was minted under. **In `profile`**: `clusterResolution`. **In `embedding`**: `Clustering` taking the two numbers in place of its constants, and `Communities`' constant gone; `RelevanceScorer` taking the number of similarities in place of `TOP_K`; `RelevanceDistribution` taking the bands and the number from each in place of `BANDS` and `SAMPLED_PER_BAND`; the scale of §9, composed here, taken by `FloorReach`, `RelevanceLabels` and `LoseNoDocumentationFloor`; and the relevance report saying the number each band was asked for. **In `extraction`**: `DoclingClient` sending the preset it is given in place of `PINNED_OCR_PRESET`, and `sentOptions()` naming it, for every conversion and the control conversion alike; `FailuresInARow` or `OccurrenceJudge` taking the count in place of `CONSECUTIVE_TIMEOUT_COUNT`. **In `similarity`**: `Shingler`, `DocumentFrequency`, `BoilerplateShingles`, `RedundancySignatures` and `RedundancyResolution` taking the parameters they are given in place of the three `DEFAULT`s, which go; `RedundancyThresholds.identity()`; each record type refusing what §9 refuses. **In `application.yaml`**: the sixteen presets of §9's table, each with a comment saying what it decides and which run it enters.

**In the documents, in the change that builds each**: Part A, the README's section of what the working directory holds, which `docs/check-claims.mjs` reads from code, and `CONTEXT.md`'s Working directory and Control panel entries; Part B, the README's table of values, its row of `clusterResolution` being what ADR-331 §8's hint is held to, and its sixty said as the shipped sample's, `docs/architecture.md`'s sentence on the control panel's server, and its *"mean of top-3 chunk similarities"* said as the shipped number's, and the javadoc of each class whose constant leaves, which says today that the number is a code default (ADR-216 §7).

**Of the tests.** Every class and method named in the first table below carries `@Disabled` until it is built, its Part saying when; **removing those lines is the one edit owed to them.** A class of Part A with a method of Part B carries both, so Part A lifts the class's alone. Beside it, tests that hold what §5, §7 and §9 change are to be edited as this record amends them, as named below the tables.

## Known limits

- A launch value emptied since the last invocation leaves the file naming the image that invocation was passed, as `environment`, until the next one writes it (§1, §11).
- A preset refused at the start leaves §1's file as it was, so the launch page goes on showing what the last invocation that started said, and the value that stopped this one is on its line alone (§1, §9).
- Stale is told by the jar's modification time alone: a jar copied into place with an old time reads as older than it is.
- With no invocation yet, the shipped values are not known to the page: the control panel reads no configuration of the jar's, so a working directory's first page says so.
- A `drawn` is of what the page showed: a second tab's save of a key is seen by the first as the file having changed, and refused where it saves that key. A held value is redrawn with a fresh `drawn`, so a refused 0.5, then `vespera label --auto` writing 0.6, then Save on the reloaded page writes 0.5 under the old reason, neither of §5's checks firing; `in-use-<key>` shows 0.6.
- **Two floors carry no scale, and are carried across a preset that moves what they were read off.** `degenerateOutputConfidenceFloor`, read off the confidence Docling reports, is applied as stated under another OCR preset, which moves every confidence score (ADR-090); `boilerplateDocumentFrequencyFloor`, a share of documents read off stage 3's document frequency, is applied as stated under another shingle window, at which the shares are other. Neither is an error, and the confidence report and the document-frequency page of the new runs show the distribution to read each again against.
- A relevance floor stated with no answers recorded applies whatever the number of similarities, as it applies whatever the model (ADR-088). **A floor read at one number also removes at another once a single answer stands under the new scale** (§9): nothing says it was read elsewhere but the answers' identities, and the operator's reading of the new sample is what is expected to replace it.
- **On NTFS a folder reused is checked for its owner alone.** A working directory `vespera run` made, or one made by hand, goes on letting whoever its drive lets write there; only a folder a save of `profile.yaml` makes is owner-only (§7). That no second account can write in one is not shown by any test here, which has one user, and no test can make a folder another user owns, so the comparison is shown by naming another user.
- A file moved into an owner-only folder keeps the entries it held; only what is made there inherits (Context).
- A user who may delete from the parent can move a folder a save made away and put another in its place, on NTFS as on POSIX permissions: a folder's own entries do not bear on its parent's. A folder so replaced is checked by its owner at the next write.
- **On a volume with neither POSIX permissions nor access control lists, such as FAT32, a save makes the folder as the volume makes it and goes on**: nobody is kept out and no owner is compared, and the record cannot do better there. What the attribute does on such a volume is not measured, so the volume is asked first (§7).
- A process run as administrator owns what it makes as a group, which §7 lets stand, and any group-owned folder with it.
- The entry §7 writes names the user as `user.name` resolves. Where a local and a domain account share that name it may name the other, the read-back passing, so the operator would be locked out of the folder made; nothing here tells the two apart.
- `/next` holds no `drawn`, so a value and a provenance both changed behind the page are written over by its Save (§5).
- A near-duplicate cut below what the bands retrieve, about (1/bands)^(1/rows), 0.71 at the shipped 16 and 8, is applied only to the pairs banding retrieves, so most pairs between the cut and that point are never scored; ADR-081's measurement is of the shipped numbers alone.
- An OCR preset whose models the image does not carry is fetched by the sidecar at its first use, and fails where it cannot be fetched; the conversions then fail as the sidecar answers. A preset is a name the sidecar resolves, so one redefined there is still unseen here (ADR-090).
- A value a machine names is in no file the corpus carries: the run's configuration says it, and the effective values say it for the machine that wrote them (§1). Nothing says how it was arrived at; a provenance is a profile key's alone (§12).

## Alternatives refused

- **A fourth command, or `--print-values`, for the control panel to ask.** It would start a JVM and a Spring context for each page, and ADR-278 keeps the child to the commands that do work.
- **The control panel reading `application.yaml` out of the jar.** It would need a YAML reader and would know the shipped file and not the machine's layers, where `VESPERA_DOCLING_IMAGE` lives; `source` would be a guess.
- **The file deleted at each start, as `next-invocation.properties` is.** A failed invocation would leave the page saying nothing, though what the jar would use has not changed; what stops a next act being offered does not bear on a default.
- **Writing the file at the end of an invocation.** The longest invocations are the ones that end last, and a page open during one has nothing to say.
- **Filling a field with the value in use.** Save would write the default into the profile, the one thing ADR-298 §4 refuses.
- **Rows of `input` for a list, with a button adding one.** It needs a script writing into the page (ADR-325 §4.2) or a round trip per row, and a list of a hundred endings becomes a hundred controls.
- **One form of every value, with one Save.** ADR-331 §3 gives each value a section and a form, and a page of all of them saved whole would ask a rule for a key refused and for a write refused part way, for what one form saves by itself; so all-or-nothing and its fifth step are not built.
- **One digest of the whole file, in place of one of the key.** A measurement refreshed by the jar would refuse a save on a page left open.
- **Keeping no `drawn`, since a form saves one key.** A field filled from the file would then write its old value over a floor `vespera label --auto` wrote behind the page, whoever pressed Save on it.
- **A `textarea` for a list, and `min` and `step=1` on a whole number.** ADR-331 §7 draws a list as a text field and a number with no bound, for reasons its Alternatives give, and a contract test holds them.
- **`/profile` asking Ollama as it is answered.** Two seconds on every `GET` where nobody answers, which ADR-331's Alternatives refuse of every page but `/`.
- **The shipped value as a placeholder.** ADR-331 §8 gives the placeholder a real example, which a test holds of every field; `in-use-<key>` says what the jar ships.
- **A list of images from the file the jar writes.** ADR-331 §7's two come from what this build knows, a contract test holding them, so a second list would say the same with an invocation needed first.
- **A DACL only where a parent is seen to be wide.** A parent's entries can change later, and what is handed down is not the save's to judge.
- **`setAcl` on the folder once made.** A moment in which another user may enter it, where the attribute leaves none (Context).
- **Keeping ADR-292's reason on Windows, what the parent gives, for a working directory.** `%LOCALAPPDATA%\Temp` is the user's already, and a working directory lies on any drive.
- **Comparing who else may write in a folder reused on NTFS.** Every working directory `vespera run` has made on an ordinary drive would be refused (Context), and the operator's save with it.
- **Cost saying this of `clusterResolution` alone.** Each key whose runs include scoring does it to a sample being answered, so one key's saying so would read as the others not doing it.
- **Reading any YAML in the control panel, so that no file needs repairing.** ADR-295 refused it, and this keeps that: three narrow repairs each have one reading, and the jar's own reader is held to them by a test.
- **Repairing what the reader can only guess at**, a key `Profile` does not hold moved to the nearest name or dropped, the second of a key twice kept. That silently loses a value the operator wrote.
- **A repair with no copy.** A comment is the operator's writing, and the file moves over its past.
- **A repair the jar performs.** `ProfileStore` drops every comment with no copy, and needs a child and a fourth command to be asked.
- **Making the working directory on any file's write.** `relevance-labels.yaml` is refused where it is absent, so its save would leave an empty folder for nothing.
- **Only clustering's two numbers as presets, the rest staying in code.** Refused by the operator's direction, which makes every candidate one; the cost, seven stages where four would have moved, is §10's.
- **A preset outside the run's identity, as a constant was, reaching a run only through the version.** A value of one machine's configuration is in no commit, so another value would reuse a finished run made under the earlier one, and verdicts of two cuts would stand under one run id.
- **The number of similarities changed and the floor applied as stated, from the first.** A floor read on one scale, applied on another with no error, writes removals against a distribution it was not read off; the scale is the mechanism ADR-088 already has for that, and holds it until an answer stands under the new number (§9, Known limits).
- **The number of similarities in the vector's identity.** Every vector would be computed again for a number that changes no vector.
- **The sample's two numbers in the label file alone, outside the scoring run.** The same run would ask about other documents as configuration changed between two invocations, which ADR-088's draw from the run id exists to refuse.
- **A list of the OCR presets Vespera accepts.** A preset is the sidecar's to define (ADR-090), so a list here would guess at another program's; what Vespera refuses is what it can see is wrong, `auto` and a name that would misread in the identity.
- **Every preset checked as the application starts.** `vespera label` and `vespera publish` read none of them, and would be stopped by a value they do not use; §9 refuses at the start of `vespera run`.
- **A preset refused where the run it enters is minted.** Hours of stages before it would be done for a value configuration names wrongly from the first second, with the corpus-root lock held and sidecars started, and the same value would pass an invocation that stops at an earlier gate.
- **Profile keys for the eight.** None is a judgement about one corpus (ADR-071, ADR-073, ADR-082), which is what a profile key holds, with the provenance of how it was arrived at; a record may give one a key on a measurement of what it moves.
- **A shipped default of the card's image.** Every machine with no card stops, CI tests another image than it ships, and every run on the default moves.
- **Finding the card at run time and naming the image from it.** What the identity says would turn on the machine, the coin-flip ADR-090 pinned the client's options to end.
- **A `vespera.clustering.*` key alone for the resolution, with no profile key.** A corpus's own preference would have no provenance, and would apply to whatever corpus that machine runs next.
- **Presets in the identity at every value.** Every run's text would change at the shipped values, where the member's absence changes none.
- **Amending ADR-058 so that a commit may declare it changes no behaviour.** A version is mechanical because a person's claim is what it replaced.
- **Launch values for an identity-bearing preset.** The control panel would pass a judgement about a corpus on every start, from a file named for a machine, with no provenance. A machine's configuration has no provenance either, but is the operator's own hand, and the record of ADR-298's layers; the control panel asks for one where it offers an edit (§12).
- **A `clusterResolution` of 0 by hand stopping the invocation, as a configured 0 does.** A closing line can ask after a profile value, as it asks after `extractionAttempt`, and every stage before it would have been done for nothing; a value in configuration has no line to ask through and no provenance to show, and §9 refuses it.

## Consequences

- An operator sees, for every value, what is in use and from where; edits each in its own form, filled from the file; and a save writes that value, or says why it wrote nothing.
- The profile stays authored: the file holds only what was saved, as the operator wrote it.
- A file a person or an agent wrote in another form no longer locks the page: it is shown, and repaired where repair can lose nothing.
- The working directory holds one more file the jar writes, and may hold a repair's copy. A working directory a save makes is the operator's alone on NTFS too.
- `clusterResolution` joins the profile, so ADR-062's merge adds it to every file at the next load, and the deliverable's index shows it (ADR-186).
- Changing `clusterResolution` mints a scoring run and everything after it, and closes an approval by name, as its cost says; the vectors stay where they are. What its cost says of a sample being answered is §9's.
- Part B's landing does that once to every corpus part way through its sample, at its first `vespera run` after it (§10), whatever its values.
- A machine may name another value of any preset. Each run made under one says it in its configuration, beside the runs of the shipped value, and putting the shipped value back arrives at those again (ADR-154, ADR-156).
- Another OCR preset converts every document again and makes every stage from extraction on again; another timeout count or shingle window makes every stage from extraction on again from the cached conversions; another MinHash or cut makes stage 4 and every later stage again; another number of similarities or sample makes the scoring run and every later one again, and keeps a floor read at the shipped number from removing until an answer stands under the new one.
- A preset no run can be made under stops `vespera run` before it has taken the corpus-root lock or walked anything.
- What ADR-082 fixed in code, what ADR-071 and ADR-073 called code defaults, and what ADR-088 and ADR-090 fixed, ship at the same numbers in `application.yaml`, changed in what ships only by a record.
- The operator on the card sees the mismatch on the launch page before pressing Run.

## Tests

These hold the record through what it builds. Each is `@Disabled` until then, its Part saying when, but for the guards named below.

| Method | Part | What it holds |
| --- | --- | --- |
| `controlpanel.ProfileEditorScreenTest.theValueInUseAndWhereItCameFromAreShown` | A | the profile's value, the jar's, the code's and none, each with its source (§2) |
| `controlpanel.ProfileEditorScreenTest.aValueIsReadOnItsOwnWhateverItsProvenance` | A | a floor under the auto rule's provenance and a value with none read `profile`, and a save with both left alone writes nothing (§2, §5) |
| `controlpanel.ProfileEditorScreenTest.anAbsentFileAndAStaleFileAreSaidForWhatTheyAre` | A | no file: a key with a default is `not-known`, another `unset`; a fresh file; one older than the jar marked `stale`, its value kept (§1, §2) |
| `controlpanel.ProfileEditorScreenTest.theFieldsHoldTheFilesOwnWordsAndADefaultStaysOutOfThem` | A | a field holds what the file states; a default stays out of it, the placeholder the example (§2) |
| `controlpanel.ProfileEditorScreenTest.theNextActsFieldsHoldTheFilesOwnWordsAndTheArrangementToApproveWins` | A | on `/next`: the arrangement filled, the provenance the file's (§2) |
| `controlpanel.ProfileEditorScreenTest.theArrangementToApproveUnderAnEarlierOnesProvenanceIsRefused` | A | saved so: the refusal in `outcome`, and nothing written (§5) |
| `controlpanel.ProfileEditorScreenTest.aStatedValueATypedControlCouldNotHoldStaysInAControlThatHoldsIt` | A | a decimal comma in a text control, a language outside the choices kept, both `unreadable` or selected, and a save leaving the file (§2, §3) |
| `controlpanel.ProfileEditorScreenTest.theSamePiecesHoweverSeparatedAreNoChange` | A | a list with a comma posted with a space writes nothing; a piece added is written (§3, §5) |
| `controlpanel.ProfileEditorScreenTest.withNoLaunchAddressOllamaIsAskedWhereTheJarLastAskedIt` | A | `/` asks at the address of the file, and `/profile` offers what it was told (§3) |
| `controlpanel.ProfileEditorScreenTest.everyKeyHasAOneLineDescriptionOfItsOwn` | A | one line for each, no two alike (§4) |
| `controlpanel.ProfileEditorScreenTest.theMeasurementPointerIsShown` | A | its source and time; none for a null one (§4) |
| `controlpanel.ProfileEditorScreenTest.theCostSaysWhichValuesDrawTheSampleAgain` | A | `data-sample-drawn-again` of each key, by name (§9) |
| `controlpanel.ProfileEditorScreenTest.aValueLeftAloneIsNotWritten` | A | no lock and the same bytes; a value changed: every other byte kept (§5) |
| `controlpanel.ProfileEditorScreenTest.aValueChangedUnderTheSameProvenanceIsRefused` | A | a value under the old provenance, and one with none, what was typed shown again until it is written, and let go where typed back (§5) |
| `controlpanel.ProfileEditorScreenTest.aValueTheFileChangedSinceThePageWasDrawnIsNotWrittenOver` | A | left alone it is kept, a save over it refused (§5) |
| `controlpanel.ProfileEditorScreenTest.aProvenanceOverTwoLinesLeftAloneIsNoChange` | A | the file's two lines, posted as a text control sends them, write nothing (§5) |
| `controlpanel.ProfileEditorScreenTest.aSeedFolderLeftAloneKeepsTheSpaceBeforeItsName` | A | a name with a space before it stands in the field and is no change (§2, §5) |
| `controlpanel.ProfileEditorScreenTest.aHeldLockWritesNothingAndKeepsWhatWasTyped` | A | another holder: nothing written, the outcome said, what was typed kept (§5) |
| `controlpanel.ProfileEditorScreenTest.aFileNotInFormShowsItsLineAndARepairThatLosesNothing` | A | the line, each change's first line, no form of a value and no list; repaired, the exact text, the jar's `ProfileStore` reading equal profiles before and after, a copy whole (§6) |
| `controlpanel.ProfileEditorScreenTest.aCommentOnAKeysLineOrInAMeasurementIsRemovedToo` | A | the two comments gone, the rest kept, the jar's reading equal (§6) |
| `controlpanel.ProfileEditorScreenTest.whatTheRepairCannotReadIsLeftToTheOperator` | A | an unknown key, a key twice and a flow mapping: the line, no repair, the text, the file as it was (§6) |
| `controlpanel.ProfileEditorScreenTest.anUnreadableValueIsTheOperatorsAndNoRepairTouchesIt` | A | a decimal comma kept through a repair, shown `unreadable` (§2, §6) |
| `controlpanel.ProfileEditorScreenTest.theRepairIsRefusedWhereTheFileChangedSinceThePageWasDrawn` | A | no change, no copy, the outcome said (§6) |
| `controlpanel.ProfileEditorScreenTest.aCopyThatCannotBeMadeLeavesTheFileAsItWas` | A | a folder taking no new file: the file as it was, no copy, the outcome said (§6) |
| `controlpanel.ProfileEditorScreenTest.theRepairsAddressWritesNothingOnAGet` | A | `GET`: to `/profile`, nothing written (§6, §13) |
| `controlpanel.ProfileEditorScreenTest.theFirstSaveCreatesAMissingWorkingDirectoryOwnerOnly` | A | the exact file, saved twice; `rwx------` for each folder made on POSIX permissions, one entry for this user on each folder and the file on NTFS (§7) |
| `controlpanel.ProfileEditorScreenTest.aFolderOwnedByAnotherIsRefusedWhereAnOwnerCanBeRead` | A | named for another user than its owner it is refused, for its owner not; aborted for a group-owned one and on a volume naming no owner (§7) |
| `controlpanel.ProfileEditorScreenTest.aRefusedValueOrAPageOpenedCreatesNoFolder` | A | neither a `GET` nor a refused value makes one (§7) |
| `controlpanel.ProfileEditorScreenTest.thePageSaysWhereTheFirstSaveCreatesTheFile` | A | `profile-file` in each of three states (§7) |
| `controlpanel.ProfileEditorScreenTest.aStartStillRefusesTheFolderASaveWouldMake` | A | the launch page's status and preflight, and Run's line, with nothing made (§7) |
| `controlpanel.TheClusterResolutionEditorScreenTest.theClusterResolutionsControlIsANumberOfAnyStep` | B | a number of any step with no bound (§3, §9) |
| `controlpanel.TheClusterResolutionEditorScreenTest.theClusterResolutionIsEditedLikeAnyValue` | B | shown as shipped, its cost and report, 0 refused, 2.5 written (§8, §9) |
| `controlpanel.TheClusterResolutionEditorScreenTest.aResolutionOfZeroWrittenByHandIsShownUnreadable` | B | `unreadable`, held in its field as written (§2, §9) |
| `controlpanel.LaunchImageScreenTest.anEmptyImageIsCheckedAgainstTheImageTheJarLastUsed` | A | `another-image`, the sidecar's image shown, a warning naming both (§11) |
| `controlpanel.LaunchImageScreenTest.theImagePickedMatchesTheOneTheSidecarRuns` | A | the image the sidecar runs, typed, is `matches` (§11) |
| `controlpanel.LaunchImageScreenTest.withNoAddressTheSidecarIsAskedWhereTheJarLastAskedIt` | A | no launch address: `/version` asked at the file's `vespera.docling.base-url` (§11) |
| `controlpanel.LaunchImageScreenTest.withNoFileTheSidecarsImageIsStillShown` | A | the image shown, `not-checked` with nothing to compare (§11) |
| `controlpanel.LaunchImageScreenTest.whatThisMachineRunsWithIsShownAndNotEdited` | A | the nineteen keys, the five and the fourteen presets, in order, with value and source, and no control for any (§12) |
| `controlpanel.LaunchImageScreenTest.aValueTheFileDoesNotNameIsLeftOutOfWhatThisMachineRunsWith` | A | the one the file names is the one listed (§12) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.anInvocationWritesTheValuesBeneathTheProfile` | A | values and sources, the image, whether the sidecars start and the language among them, a blanked model left out with its source, and every key on the closed list (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.theMomentIsSaid` | A | `written-at`, with no source (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.thePresetsAreWrittenAsTheShippedFileHoldsThem` | B | the neighbours, the resolution, and the fourteen presets, each at its exact value from the shipped file (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aValueTheProfileNamesStaysOutOfTheFile` | A | the file says what lies beneath a value the profile names (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.noCredentialOrPathEntersTheFile` | A | a user, a password, a path and a query off an address, and no working directory (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aLabelWritesTheFile` | A | `vespera label` recording answers, the file having been removed (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aLabelByTheLocalModelWritesTheFile` | A | `vespera label --auto` likewise (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aLabelThatFindsNoAnswersHasWrittenTheFile` | A | a label that started and recorded nothing (§1) |
| `pipeline.TheJarSaysWhereEachValueCameFromInvocationTest.aValueOnTheCommandLineSaysSo` | A | the source `command-line` (§1) |
| `pipeline.TheJarSaysWhereEachValueCameFromInvocationTest.aValueInTheEnvironmentSaysSo` | A | the source `environment`, by a system property (§1) |
| `pipeline.TheJarSaysWhereEachValueCameFromInvocationTest.aValueInAnotherFileSaysSo` | A | the source `local-file` (§1) |
| `pipeline.TheJarSaysWhereEachValueCameFromInvocationTest.anAddressNothingNamesIsTheOneTheCodeFallsBackTo` | A | Ollama's address with no configuration: `http://localhost:11434`, the source `code` (§1) |
| `pipeline.TheClusteringPresetsInvocationTest.aProfileValueOtherThanTheShippedOneNamesAnotherScoringRun` | B | 1.0 stated arrives at the run stating none; 2.5 is one more run, its text ending in the member, with a cluster for each document where the other has one (§9) |
| `pipeline.TheClusteringPresetsInvocationTest.anUnreadableResolutionIsNamedAndTheShippedValueApplies` | B | the same run, and `wants=clusterResolution` (§9) |
| `pipeline.TheClusteringPresetsInvocationTest.anUnreadableResolutionWaitsBehindAnUnreadableExtractionAttempt` | B | both unreadable: `wants=extractionAttempt` alone; the attempt readable: `wants=clusterResolution` (§9) |
| `pipeline.TheClusteringPresetsInvocationTest.aResolutionThatIsNoFiniteNumberAboveZeroIsIgnoredAndNamed` | B | `0`, `-1`, `NaN` and `Infinity` by hand: the same run, and `wants=clusterResolution` (§9) |
| `pipeline.TheClusteringPresetsInvocationTest.anotherResolutionWritesANewSampleRefusesTheEarlierFileAndKeepsTheAnswers` | B | the file written anew naming the new run, a copy of the earlier refused, the answers kept and shown (§10) |
| `pipeline.TheClusteringPresetsFromConfigurationInvocationTest.configurationNamesBothInTheScoringRun` | B | both members in order (§9) |
| `pipeline.TheClusteringPresetsFromConfigurationInvocationTest.theProfilesResolutionOverridesTheConfiguredOneAndTheNeighboursStay` | B | the profile's 1.0 leaves the resolution's member out, and the neighbours' stays (§9) |
| `pipeline.TheClusteringPresetsFromConfigurationInvocationTest.anUnreadableResolutionFallsToTheConfiguredOne` | B | the preset as configuration resolves it applies, not the shipped one (§9) |
| `pipeline.TheNeighboursReachTheGraphInvocationTest.oneNeighbourSplitsWhatTheShippedNumberKeepsTogether` | B | two close pairs: 2 clusters at 1 neighbour, where 15 finds 1 (§9) |
| `pipeline.TheShingleWindowAndMinHashReachTheirRowsInvocationTest.theShingleRowsAreKeyedByTheWindowConfigured` | B | every shingle row under `word:6` (§9) |
| `pipeline.TheShingleWindowAndMinHashReachTheirRowsInvocationTest.theWindowConfiguredCutsTheText` | B | the rows the window makes of the words, at 6 and not 5 (§9) |
| `pipeline.TheShingleWindowAndMinHashReachTheirRowsInvocationTest.eachSignatureIsUnderTheIdentityConfigured` | B | window, MinHash and floor in each signature's identity (§9) |
| `pipeline.TheShingleWindowAndMinHashReachTheirRowsInvocationTest.eachSignatureIsOfTheNumbersConfigured` | B | 64 minima and 8 bands to a signature, where 128 and 16 ship (§9) |
| `pipeline.AnotherCutLeavesThePairInvocationTest.aPairAboveTheShippedCutAndBelowTheConfiguredOneIsNotJudged` | B | stage 4 ran, three signatures and a run naming 0.9, and no `redundant_with` row, for the pair the guard below finds at 0.80 (§9) |
| `pipeline.TheSampleBandsReachTheDrawInvocationTest.twoBandsAskAboutTheBandsThatHoldADocument` | B | three scores, one from a band: 2 questions at 2 bands, where the shipped 5 ask 3 (§9) |
| `pipeline.AScoreIsTheMeanOfTheNumberConfiguredInvocationTest.aScoreOfOneSimilarityIsTheHighest` | B | 1.0, where the shipped three give the mean of two, about 0.854 (§9) |
| `pipeline.TheTimeoutsInARowConfiguredSetAnOccurrenceAsideInvocationTest.twoTimeoutsInARowSetTheSecondAside` | B | one `extraction_fault` row at 2, where the shipped 3 writes none (§9) |
| `TheShippedPresetsTest.theShippedFileNamesBothAtTheirEarlierValues` | B | 15 and 1.0, exactly (§8, §9) |
| `TheShippedPresetsTest.theConvertersTwoPresetsShipAtTheirEarlierValues` | B | `rapidocr` and 3, exactly (§8, §9) |
| `TheShippedPresetsTest.theShingleWindowAndMinHashShipAtTheirEarlierValues` | B | 5; 128, 16, 8 and the seed the code held, exactly (§8, §9) |
| `TheShippedPresetsTest.theRedundancyCutsShipAtTheirEarlierValues` | B | 0.80 and 0.95; 32 and 24, exactly (§8, §9) |
| `TheShippedPresetsTest.theRelevanceScoreAndItsSampleShipAtTheirEarlierValues` | B | 3; 5 and 12, exactly (§8, §9) |
| `pipeline.TheTechnicalPresetsFromConfigurationInvocationTest.theConvertersPresetsNameTheExtractionRun` | B | another OCR preset in the extractor identity in place of `rapidocr`; the two components ending stage 2's configuration, in order (§9) |
| `pipeline.TheTechnicalPresetsFromConfigurationInvocationTest.theRedundancyPresetsNameTheContentRedundancyRun` | B | the two components ending stage 4's configuration, in order, each as its identity writes it (§9) |
| `pipeline.TheTechnicalPresetsFromConfigurationInvocationTest.theRelevancePresetsNameTheScoringRunAndReachItsSample` | B | the three components ending the scoring run's configuration, in order; the label file's scale ending `;top:1`; one question where the shipped sample asks three (§9) |
| `pipeline.AnOcrPresetTheConverterWouldResolveIsRefusedInvocationTest.anOcrPresetOfAutoIsRefusedAtTheStart` | B | `auto`: non-zero, §9's line once and no stack trace, and no run at all (§9) |
| `pipeline.MinHashBandsThatDoNotMakeItsPermutationsAreRefusedInvocationTest.bandsTimesRowsOtherThanThePermutationsAreRefusedAtTheStart` | B | 16 bands of 7 rows: the same (§9) |
| `pipeline.ARelevanceScoreOfNoSimilaritiesIsRefusedInvocationTest.aScoreOfNoSimilaritiesIsRefusedAtTheStart` | B | 0: the same (§9) |
| `pipeline.APresetNoRunCanBeMadeUnderIsRefusedInvocationTest`'s nested classes `.stopsInOneLineAtTheStart` | B | the same, for resolution 0, `NaN` and `fine`, neighbours 0, 1.5 and 3000000000, sample bands 0, per-band 0; OCR preset blank and `a;b`, timeouts 1 and 5, shingle words 0; permutations, bands and rows 0, seed one past a `long`, either cut 0 and 1.5, rare-shingle sample 0, hits 0 and 33 (§9) |
| `pipeline.APresetNoRunCanBeMadeUnderIsRefusedInvocationTest.AResolutionOfZeroThatTheProfileStandsInPlaceOf.stopsNothing` | B | a configured 0 under the profile's 2.5 stops nothing, and 2.5 names the run (§9) |
| `pipeline.APresetNoRunCanBeMadeUnderIsRefusedInvocationTest.AValueRefusedAtTheStartWritesNoFile.leavesTheFileAsItWas` | B | a marker put in §1's file stays (§1, §9) |

**Guards, passing before this is built and carrying no `@Disabled`**, because each holds what §1, §5, §7, §9, §10, §11 or §13 rests on or leaves as it is:

| Method | What it holds |
| --- | --- |
| `pipeline.TheStagesNamingThePresetsModulesTest.theStagesNamingEmbeddingAreTheLastFour` | `StageModules`' stages naming `embedding` are the last four of §10's table |
| `pipeline.TheStagesNamingThePresetsModulesTest.theStagesNamingSimilarityAreExtractionAndTheTwoAfterIt` | those naming `similarity` are extraction, content census and content redundancy |
| `pipeline.TheStagesNamingThePresetsModulesTest.theStagesNamingExtractionAreTheSevenFromExtractionOn` | those naming `extraction` are the seven §10 moves, byte-level reduction alone left |
| `pipeline.VesperaPublishWritesNoEffectiveValuesInvocationTest.aPublishWritesNoFile` | a publish, refused here with nothing drawn, writes no §1 file: a writer ahead of its first refusal is seen, one after it is not (§1) |
| `pipeline.ARefusedInvocationWritesNoEffectiveValuesInvocationTest.aRefusedInvocationLeavesTheFileAsItWas` | `vespera run` with no root: a marker put in the file stays, so a writer ahead of the refusal is seen (§1) |
| `pipeline.ARefusedInvocationWritesNoEffectiveValuesInvocationTest.aLabelRefusedAtItsStartLeavesTheFileAsItWas` | `vespera label --auto` given a file: the same (§1) |
| `controlpanel.ProfileEditorGuardsScreenTest.saveThenRunStartsTheRunOnceEveryKeyIsWritten` | the child, as it starts, finds every key in the file (§5) |
| `controlpanel.ProfileEditorGuardsScreenTest.theRepairsAddressRefusesAFormFromAnotherSite` | `POST` of another `Origin`: 403 (§13) |
| `controlpanel.ProfileEditorGuardsScreenTest.aWorkingFolderOfThisUsersThatOthersMayWriteInIsStillWrittenIn` | where folders carry access control lists, one with `Everyone` writing is written in; aborted on POSIX permissions and where that group goes by another language's name (§7) |
| `controlpanel.ProfileEditorGuardsScreenTest.theImagePickedIsHeldAsALaunchValueAndTheControlPanelWritesItInNoFileOfTheWorkingDirectory` | the image picked is in the launch values' file once an invocation starts, and the control panel writes it into no file of the working directory (§11) |
| `pipeline.ThePairIsRedundantAtTheShippedCutInvocationTest.aPairAboveTheShippedCutIsJudged` | the pair of `AnotherCutLeavesThePairInvocationTest` is one `redundant_with` row, `near-duplicate`, at the shipped numbers (§9) |
| `pipeline.ABadPresetStopsNeitherALabelNorAPublishInvocationTest.aLabelIsNotStoppedByIt` | `vespera label` under a preset §9 refuses says no line of it; that a label with answers to record goes on is not shown, since no run can score under the value (§9) |
| `pipeline.ABadPresetStopsNeitherALabelNorAPublishInvocationTest.aPublishIsNotStoppedByIt` | `vespera publish` likewise (§9) |
| `embedding.AFloorReadAtAnotherNumberOfSimilaritiesTest.itRemovesNothingWhileNoAnswerStandsUnderTheNewScale` | answers under the number it was read at alone: no removal (§9) |
| `embedding.AFloorReadAtAnotherNumberOfSimilaritiesTest.itRemovesOnceOneAnswerStandsUnderTheNewScale` | one answer under `;top:1` among those at 3: the one read, and the floor removes (§9) |

`pipeline.RunIdentityGoldenTest`, **unedited**, holds that no configuration text moves at the shipped values (§9, §10), as `DoclingSidecarImageTest` and its literal hold §11's image. `controlpanel.TheControlPanelLinksNoClassOfTheJarTest` holds the new classes to ADR-285 §3.

**Tests the implementation adds, since each names what does not yet exist**: `pipeline.TheControlPanelReadsTheEffectiveValuesTheJarWritesTest`, each shape the jar writes read by the control panel's reader, as ADR-294's contract test holds its file; and `pipeline.TheJarReadsWhatTheControlPanelWritesTest`'s samples of `clusterResolution`; a test of the resolving class, each value of §9's table of refusals refused with its line, as the invocation tests above have it of the whole job, and each bound's nearest value allowed, which they do not; `embedding`'s test of the scale, the embedder identity at 3 and `;top:<n>` after it at any other, `FloorReach` removing nothing with answers under the one and a run under the other, and removing once one answer stands under the run's, as `AFloorReadAtAnotherNumberOfSimilaritiesTest` holds of the text today; `extraction.DoclingClientTest`'s request naming the preset the client is given, as `sendsThePinnedOcrPresetAndExportFormat` names the constant now; `extraction.OccurrenceJudgeTest`'s timeout set aside at the count it is given; `similarity.RedundancyThresholdsTest`'s identity; and `controlpanel.ProfileKeyTest`'s `sampleDrawnAgain`.

**Tests that hold what this amends, edited in the change that builds it:**

- `controlpanel.ProfileScreenTest`, whose reads hold of a key's form, each control to be read filled by §2 where it read one empty; `controlpanel.NextScreenTest` likewise of the form `next`.
- `controlpanel.WorkingDirectoryWriteTest.aFolderOthersCanWriteInOrAnotherUserOwnsIsNotWrittenIn`, whose claim that a working directory that does not exist is refused holds for the other two files alone (§7).
- `controlpanel.FolderFieldStatusScreenTest.theWorkingDirectorysHintAsksForItToBeCreatedFirst`, renamed, the hint no longer asking that, and `controlpanel.FieldHintsScreenTest`, of the hint of `vespera.working-dir`, and `FieldHintsScreenTest` and `NativePickersScreenTest` of a decimal comma's control where they read a stated one (§3, §7).
- `controlpanel.TheFolderPageScreenTest.aRefusedSaveOfTheProfilePagesSeedFolderKeepsWhatItHeld`, which opens `/profile` on a file not in the form and presses `seedFolder`'s form, which §6 no longer draws there: it is to be refused instead by another holder of `vespera.lock`, as `ProfileEditorScreenTest.aHeldLockWritesNothingAndKeepsWhatWasTyped` is, so ADR-331's row *"a profile `ProfileEdit` refuses"* no longer describes it. Its siblings on `/` and `/next` are untouched by §6.
- `controlpanel.ProfileKeyTest`, of `Cost`'s new component in Part A and the key added in Part B, and `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theKeyTableIsTheProfilesComponentsAndTheRunsAreTheStagesModules`, of the key added (§9).
- `embedding.ClusteringTest` and `embedding.ClusteringProgressTest`, which name the constant `NEIGHBOURS` and `clusterAndRecord` as they stand (§14).
- The tests naming a constant §14 removes, each to name the value in a constant of its own or to be handed it: of `PINNED_OCR_PRESET` and `sentOptions()`, `extraction.DoclingClientTest`, `extraction.ExtractorIdentityCompositionRuleTest`, `extraction.TextInPartsTest` and `pipeline.ExtractorIdentityCompositionTest`; of `CONSECUTIVE_TIMEOUT_COUNT`, `extraction.FailuresInARowTest` and `extraction.OccurrenceJudgeTest`; of `TOP_K`, `BANDS` and `SAMPLED_PER_BAND`, `embedding.LabelledSpreadTest`, `embedding.RelevanceDistributionTest` and `embedding.RelevanceDistributionDrawsByLeastKeyAPageAtATimeTest`; of the three `DEFAULT`s, `pipeline.StageThreeWritesTheDocumentFrequencyPageTest` and every class of `similarity`'s tests that names one, `ShinglerTest`, `MinHashSignatureTest`, `RedundancySignaturesTest`, `RedundancyResolutionTest`, `BoilerplateShinglesTest`, `DocumentFrequencySharesTest`, `DocumentFrequencyIsCountedInTheDatabaseTest`, `ShingleIndexesInTheSchemaTest`, `ShinglesReachTheFloorExactlyTest`, `FrequencyProgressTest`, `SimilarityStatementProgressOrderTest`, `TheHashIndexIsBuiltForOneRunTest`, `AFailedReadOfOneHashsOccurrencesIsThrownWithItsCauseTest`, `RedundancyResolutionBoundsItsCandidatesTest`, `RedundancyResolutionReadsOnlyTheBandRowsThatCanPairTest`, `RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest` and `RedundancyResolutionReportsItsCountsTest`. A test whose claim is the shipped value goes on claiming it; none is weakened to a claim any value passes.
- `pipeline.SeedScriptedExtractionBeans`, `pipeline.StubbedExtractionBeans` and every other double of `DoclingClient`, should its constructor gain the preset.

## What this does not decide

- **Whether an agent may read `effective-values.properties` or a repair's copy**: ADR-261's list is unchanged.
- **Who removes a repair's copy.** Nothing does; it is the operator's.
- **The words of `about-<key>`, of any line said here without a table, and how a page lays them out**, within ADR-331 §3.
- **Whether `ollama-check` on the launch page also asks after the models §1's file names**, as it asks after the profile's.
- **Whether the launch page's label model gets the picker.**
- **Whether `generationContextWindow`'s number, or `deliverableLanguage`'s, moves from the code into `application.yaml`**: they enter as `code`, and no candidate here named them.
- **Editing the keys §8 marks later**, each by a record of its own.
- **Whether stage 4 ships a report of its pairs' similarities**, now that its cuts can be moved. ADR-082 refused one with nothing to move; a record that adds one says what it shows.
- **Whether ADR-071's count of five and its timeout budget become presets.** No candidate here named them.
- **Whether `vespera.models.pull` and `vespera.models.pull-stalled-at-most` (ADR-329) join §1's list and `machine-values`.** They are the application's and no profile values, as §12's keys are; until a record adds them the page does not show them.
