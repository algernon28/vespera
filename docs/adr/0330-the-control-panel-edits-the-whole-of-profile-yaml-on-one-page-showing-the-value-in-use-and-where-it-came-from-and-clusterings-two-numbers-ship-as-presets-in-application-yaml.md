# ADR-330 — The control panel edits the whole of `profile.yaml` on one page, showing the value in use and where it came from, and clustering's two numbers ship as presets in `application.yaml`

- **Date**: 2026-10-31
- **Status**: accepted on 2026-10-31.
- **Decided**: by the operator, that one record covers both parts; that `profile.yaml` stays the store, the jar reading it, every run recording it and an agent reading and writing it; that the pattern to extend is [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md)'s; that the page creates the file if it is missing and edits it if it is present; and that more technical choices ship as presets and are in time edited on the control panel. Every other choice here, each with the alternative it refused, is decided under the operator's delegation of 2026-10-31, and any of them is reopened by a later record.
- **Keeps**: [ADR-043](0043-the-profile-is-authored-as-a-file-and-recorded-in-the-ledger.md), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md): the file is input, a value is `value`, `provenance` and `measurement` under its key, and a save holds only what was saved, the jar adding the rest as unset at its next load (§7).
- **Keeps**: [ADR-006](0006-census-measure-before-judging.md): a measured floor ships unset, so no floor is a preset (§8).
- **Keeps**: [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md): an unreadable value is a third state, shown as one, and no repair touches it (§2, §6).
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md): every write holds the working-directory lock; a save that writes nothing takes none (§5).
- **Keeps**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md): what an agent may read in a working directory is unchanged, so neither `effective-values.properties` nor a repair's copy is on its list (What this does not decide).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §1, §4 and §5 as [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §2 leaves them, §7, and §8: every path of §6 passes the checks of `Host`, `Sec-Fetch-Site` and `Origin`, the server's classes write nothing, no file holds a token or text of a document, and a folder made or reused is one only this user can write in (§7, §13).
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md): the Docling image stays a launch value, stored as an invocation starts and passed as `VESPERA_DOCLING_IMAGE`; a key of `application.yaml` the control panel comes to edit is a launch value too (§11, §12).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): each screen is tested through the pages it serves, reading names, ids and attributes and never words. §3's control types rest on what a browser does with them, which no test sees, and this says so as that record's Consequences ask.
- **Keeps**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §4: one script, which writes nothing into a page; no control here needs it (§3).
- **Keeps**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): a stage's version is the last commit touching a module it names, and no stage names `pipeline` or `profile`; §10 follows from them and asks neither to change.
- **Keeps**: [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md), [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands.md), [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md), [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md), [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) and [ADR-020](0020-relevance-scoring-function.md): each of their numbers stays in code (§8).
- **Keeps**: [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md), [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) and [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §2: the processor build stays the default everywhere, and a conversion is refused under an image the sidecar does not run (§11).
- **Keeps**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §9 and [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md) §5: those keys are the application's and no profile values (§12).
- **Extends**: [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md) §1, §3 and §4: the resolution ships in `application.yaml`, the profile overrides it, nothing is written into the profile when the shipped value applies, and a run is identified by the value resolved and not by where it was named (§8, §9).
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the files that are the control panel's contract with the jar gain `effective-values.properties`, parsed in `java.base` (§1). It carries a note at its head.
- **Amends**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s table and its sentence on resolution: `k` and the resolution are presets at the numbers it chose, and the resolution becomes the profile key that sentence foresaw, shipping with a value where it refused one shipping unset (§8). It carries a note at its head.
- **Amends**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §2: the row of a key holding something nothing can be read from gains `clusterResolution`, after `extractionAttempt` (§9). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §1, *"The control panel creates no working directory"*: a save of `profile.yaml` makes one that does not exist (§7); §2, *"three files of a working directory and no other"*: a repair also writes a copy of the file it repairs, and the file not in the form read is shown and may be repaired where it was refused alone (§6); §3, *"never offers a provenance"*: it offers back the provenance the file states, and still none of its own (§2), and *"kept unread"*: a measurement's two lines are read to be shown, and kept byte for byte (§4); §4, whose checks gain the two of §5 and the row of `clusterResolution`, and whose *"one line"* is asked of a list's pieces once its lines are joined (§3, §9); and §5, whose table gains that key (§9). It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5, *"the jar's own defaults are not the control panel's to know"*, whose `docling-check` compares with the image the jar last said where no launch value names one, and which is also asked as `/profile` is answered (§3, §11); §8, *"a form named by the key"* for *"every `ProfileKey` that the form `next` does not hold"*: one form of every key (§5), with its values filled (§2); *"`save` writes the keys in the form's order … and stops at the first refused"*: nothing is written where one is refused (§5); and *"A key's control is empty"*: it holds what the file states (§2); and §9, whose table of names gains those of §2 to §7 and §11 to §12. It carries a note at its head.

## Context

**What the pages hold today.** `/profile` holds a form for every `ProfileKey` the form `next` does not hold, each saving its one key. `current-<key>` shows what `profile.yaml` states, so a key the file leaves unset reads `not set`, though `bge-m3:latest` is in use: the control panel links no class of the jar (ADR-285 §3) and reads no configuration of its. Every control is `input type="text"`, empty, checked only once saved. `ProfileEdit.Stated` holds a value and a provenance, and a key's measurement is kept unread. A file not in the form ADR-295 §3 reads is refused whole with one line. A first save writes the file where the working directory exists, and a working directory that does not exist is refused.

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

**What was measured.** Over the graph `NearestNeighbourGraph.build` makes of identical vectors, the shape `EmbeddingScriptedBeans` gives every test, `Communities.of` finds 1 cluster at resolution 1.0 and one for each document at 2.5, 50 and 1000, for 3, 6, 8 and 20 documents and for `k` of 15, 2 and 1. So on that fixture the count of clusters tells the resolution used, and tells nothing of `k`. A whole-job invocation over 3 documents and a seed writes 3 rows of `document_cluster` and 1 ordinal.

**The image.** `vespera.docling.image` ships the processor build, which `compose.yaml` runs and `TestcontainersConfiguration` tests against, and `DoclingSidecarImageTest` holds the three as one; `compose.gpu.yaml` builds another image under another name, and an operator on it sets `VESPERA_DOCLING_IMAGE`. A device request a machine cannot satisfy stops `up` (ADR-165). The launch page's check says `not-checked` where the launch value is empty, so an operator who leaves it empty with the card's sidecar running learns of the mismatch when stage 2 stops.

## Decision

### Part A: the control panel is the complete editor of `profile.yaml`

### 1. The jar says what lies beneath the profile

**`effective-values.properties`**, in the working directory beside `vespera.db`, says what the jar would use where the profile names nothing. It is the jar's to write and the control panel's to read, as `next-invocation.properties` is, in `java.util.Properties`, UTF-8, read with `java.base` alone.

**When.** `vespera run` and `vespera label`, with or without `--auto`, write it once they hold the working-directory lock and before their first step. `vespera publish` writes none: it reads no profile value, and its environment holds the one credential a process of the jar holds. An invocation refused writes nothing, so the file stands as the last one left it. It is written whole to a `.part` file and moved over the target; one that cannot be written is logged at WARN and fails nothing. **It is never deleted.** Unlike `next-invocation.properties`, what it says stays true of the jar that wrote it whatever became of the invocation, and how old it is is shown (below).

**What it holds, and nothing else.** A closed list, each key with its `<key>.source` beside it; a key nothing names is left out:

| Key | Value |
| --- | --- |
| `written-at` | the moment it was written, ISO-8601 with an offset, and no source |
| `embeddingModel`, `generationModel`, `generationContextWindow`, `deliverableLanguage`, `clusterResolution` | what applies where the profile names none, never what the profile names: so a value cleared on the page can be shown as what it falls back to |
| `vespera.docling.image` | an image's name |
| `vespera.sidecars.start`, `vespera.sidecars.wait-at-most`, `vespera.progress-interval`, `vespera.open-browser`, `vespera.clustering.neighbours` | as the application resolves each |
| `spring.ai.ollama.base-url`, `vespera.docling.base-url` | its scheme, host and port alone |
| `docling.images` | the names of the two images the compose files in the jar build, separated by one space, and no source; left out where they cannot be read |

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
| a value ADR-295 §4 would save | any | that value | `profile` |
| a value ADR-295 §4 would refuse, a decimal comma among them | any | that value, as written | `unreadable` |
| none, or blank | names the key | the file's | the file's `.source` |
| none, or blank | present, and does not name it | empty | `unset` |
| none, or blank | absent, for a key that has a value beneath it, the five of §1's second row | empty | `not-known` |
| none, or blank | absent, for any other key | empty | `unset` |

**A field holds what the file states**, on `/profile` and `/next`: the key's control its value, `<key>.provenance` its provenance. These are the operator's own words offered back, and the control panel still offers no provenance of its own. **A value the jar ships stays out of the field**, shown as the control's `placeholder`: pressing Save must not write into the profile a value nobody chose (ADR-298 §4). On `/next`, `arrangementApproved` holds the file's `arrangement` whatever the profile states, and `seedFolder`, where the profile states none, the last seed folder, as ADR-302 §8 has them.

### 3. A control of the key's type

| Key | Control |
| --- | --- |
| a floor or a share, and `clusterResolution` | `input type="number" step="any"`, no `min` or `max`: a floor past its scale is the jar's to read (ADR-295 §4) |
| `degenerateOutputWordFloor` | `input type="number" step="1" min="0"` |
| `extractionAttempt`, `clusterPageByteCeiling` | `input type="number" step="1" min="1"` |
| `generationContextWindow` | `input type="number" step="1" min="1282"` |
| `codeNameEndings`, `outOfScopeNameEndings`, `preferredLanguages` | `textarea`, one piece to a line |
| `embeddingModel`, `generationModel` | `input type="text" list="models-served"`, so the list offers and anything may be typed |
| `deliverableLanguage` | `select` of the empty value, `en` and `it` |
| `seedFolder`, `arrangementApproved` | `input type="text"` |

**A value a typed control could not hold stands in one that can.** A browser blanks a `number` control whose value is not a number, and a save would then unset the key, losing a value the operator wrote. So a stated value that is no valid floating-point number of HTML is drawn in `input type="text"`, as written, and a `select` gains an option holding a stated value it does not list, selected. The controls' attributes help a browser; the server's checks are what decide, and a browser's own handling of them is on no test (ADR-297's Consequences).

**A list has no rows of its own.** A row added in place needs a script writing into the page, which ADR-325 §4.2 refuses. Lines of a `textarea` give the operator one piece to a line with none. The server splits what is posted on line ends, spaces and commas and joins the pieces with one space, which the jar reads (`NameEndingsValue`, `LanguagesValue`), before ADR-295 §4's checks.

**The picker asks Ollama as the page is answered.** `models-served`, a `datalist`, holds an `option` for each `name` of `/api/tags`, in the order answered, asked through `SidecarChecks`'s two timeouts of 2 seconds, of the launch value's address, or, where it is empty, `spring.ai.ollama.base-url` from §1's file. It carries `data-check`: `answered`; `not-answering` where no answer came in time, the list empty; `not-checked` where no address is known, the list empty. The control stays free text in all three, so the picker never stands between the operator and a save: a page is answered within the two timeouts, and a model typed by hand is saved as ADR-295 §4 has it, any text. A model not served is the step's to pull and, where that fails, to refuse, at stage 5 (ADR-202, ADR-329).

### 4. What a key is, and its measurement

`about-<key>` holds one line saying what the key is, each its own; its words are no decision's. `measurement-<key>`, where the file holds a measurement under the key, carries `data-source` and `data-refreshed-at`, read from its `source` and `refreshedAt` lines, as the file holds them. A key whose measurement is `null`, or whose lines beneath `measurement:` are not those two, shows none, and the bytes are kept as ADR-295 §3 keeps them either way. `ProfileEdit.Stated` keeps its two components, which the contract tests name, and the measurement is read beside it.

### 5. One form, one save

**`/profile` holds one form, `profile`**, `POST` to `/profile`, with one button `save`, and for each `ProfileKey` in its order a `fieldset` `key-<key>`, `data-wanted` true where the next invocation wants the key. The key's value is the control `<key>`, its provenance `<key>.provenance`, not `required`, since a key left alone asks for none, and a hidden `<key>.drawn`, the hexadecimal SHA-256 of the value and the provenance as drawn, a NUL between them. A key the next invocation wants stands here as well as on `/next`; two ways to one write do no harm, and a page of all the keys has no key missing from it.

**A save, in order:**

1. **Each key is compared.** It is changed where what is posted, its value stripped and a list's pieces joined by §3's rule, differs from `drawn`. An unchanged key is not written, not locked for and not checked, whatever the file holds now: so a provenance is not rewritten, and a floor `vespera label --auto` wrote behind the page is not written over by the value the form still holds.
2. **Each changed key is checked**, before anything is written, by three rules, each giving `<key> is not saved: <reason>` in `refused-<key>`:
   - the file now states other than `drawn`: `profile.yaml has changed this value since the page was drawn; look at it and save again`;
   - the value changed and the provenance is what the file states: `its value changed and its provenance did not; write how you arrived at the new value`, because a field filled by §2 would otherwise leave the old reason under the new number;
   - ADR-295 §4's checks, the blank provenance among them.
3. **Any key refused writes no key of the save.** The page answers with what was typed in the fields, once, each refused key saying why, and `outcome` saying nothing was saved. The whole form is a value the operator judged whole, and every key the panel can see is bad it sees before it writes.
4. **Nothing changed writes nothing**: no lock, no holder line, and `outcome` saying so.
5. **The changed keys are written in the form's order**, each one of ADR-295's writes, under its lock, with its checks of the folder and of a child. **A write refused here**, the lock held, the folder or the file's form changed under it, or the disk, stops the save: the keys written stay written, each a whole file moved into place, `outcome` names those and gives the refusal's line, and what was typed for the rest stands in the fields. No key is put back: a write to undo a write can fail too, and leaves the file further from what the operator saw.

**`/next`** saves by the same rules, one to three and five, comparing with the file where it holds no `drawn`, so its controls are named as ADR-302 §8 has them. `save-then-run` starts `run` only once every key is written. A provenance is `required` there, where every key is meant to be written.

The cost beside each key (ADR-302 §8) is what pressing Save confirms, as before.

### 6. A file not in the jar's form

The form read, ADR-295 §3's, does not change, and `ProfileEdit.rewrite` goes on refusing any other. **What changes is what the page does.** Where the file is in another form, `/profile` holds no form `profile`, and shows:

- `form-problem`, carrying `data-line`, the first line not in the form, from 1, and `data-repairable`, `true` or `false`, with a line saying why;
- `profile-text`, the file's text, as it stands;
- where it is repairable, `repair-changes`, an item for each line the repair changes, carrying `data-line` and saying what it does, and **the form `repair`**, `POST` to `/profile-repair`, a hidden `drawn` holding the hexadecimal SHA-256 of the text and one button `repair`.

**The repair may do three things, and is offered only where they are all that is wrong:**

| Where the file has | The repair |
| --- | --- |
| a whole line that is a comment | removes the line with its end |
| a comment after a scalar on its line | removes the comment, and writes the scalar as ADR-295 §3 writes one |
| a plain or single-quoted scalar of `value` or `provenance` over several lines, no line of it blank | joins them with one space, as YAML folds them, into one line written as ADR-295 §3 writes a scalar |

Every other byte is kept, line ends included. **Everything else is the operator's**, shown with its line and the file's text: a key `Profile` does not hold, whose value and provenance no repair may drop or guess a home for; a key twice, of which the repair cannot say which stands; a flow mapping, a block scalar, a tab in an indent, a key written flat. A file the jar reads, in whatever form, the next invocation's census writes back in the jar's own (ADR-295's Consequences), and the page may say so.

**A value is never the repair's to change.** A decimal comma in a double-quoted value is in the form, and is no problem of this section: the page shows it `unreadable` (§2) and the operator fixes it.

**What is removed is kept.** Under the lock, before anything else, the file is copied whole to `profile.yaml.before-repair-<yyyyMMdd-HHmmss>` beside it, never over an earlier one; a copy that cannot be made leaves the file as it was. Comments are neither values nor provenances, the operator may have meant them, and the jar's next write would drop them with no copy.

**It is confirmed by what was shown.** Under the lock the repair is worked out again from the file. Where its digest is not `drawn`, nothing is changed and no copy made, with the line `profile.yaml has changed since this page was drawn, so it was not repaired`. A `GET` of `/profile-repair` answers `303` to `/profile` and changes nothing.

### 7. Create if missing, edit if present

**A working directory that does not exist is made by a save of `profile.yaml`**, as `LaunchValues` makes its folder: `Files.createDirectories`, on a file system with POSIX permissions `rwx------` for each folder it makes, on Windows with what the parent gives. It is checked again as ADR-295 §1 checks a folder, by its real path, once made, so a folder another user made first in that moment is refused. A path where a file lies is refused as before, and so is a folder that exists and is not this user's alone. The other two files go on refusing a folder that does not exist: no question is answered and no code set named before a run has made it.

**It is made only by a `POST`, past §5's second step**, so a value refused makes nothing, and a `GET` writes nothing (ADR-302 §1). The file holds only what was saved, and ADR-062's merge adds the rest as unset at the jar's next load.

`profile-file` says where things stand, carrying `data-file` and `data-folder`, each `present` or `absent`, and `data-path`, the absolute path of the file; its words say that the first save creates the file there, and the folder with it where that is absent.

### Part B: presets

### 8. What a preset is, and which choices are presets

**A preset** is the value of a technical choice shipped in `src/main/resources/application.yaml`, where it is read, overridden and listed, in place of a constant of the code. Spring's own layers override it for a machine. Where a choice is also a judgement about one corpus, the record that makes it a preset may give it a profile key, with its provenance, as ADR-298 has it of the embedding model. A measured floor is no preset: it ships unset and points at its measurement (ADR-006).

**A candidate becomes a preset where all three hold:** an operator could want another value, on evidence they can see; another value is comparable with this one, leaving what a recorded threshold was calibrated against in place; and the cost of §10, once, is one that buys that. The rest stay in code, where a changed value is a changed record and a changed version (ADR-082).

| Value | Where | Decision | Control panel | What it does to run identity |
| --- | --- | --- | --- | --- |
| resolution, 1.0 | `Communities.DEFAULT_RESOLUTION` | **Preset** `vespera.clustering.resolution`, overridden by the profile's new `clusterResolution`. How finely a partition divides is what `cluster-sizes.html` shows and what ADR-087 foresaw a key for; its objection, a key shipping unset and unanswerable, does not meet a key shipping a value. | **Now**: a key of `/profile`, with `cost-` and `report-` linking `cluster-sizes.html` | Enters the scoring run where it is not 1.0 (§9) |
| `k`, 15 | `Clustering.NEIGHBOURS` | **Preset** `vespera.clustering.neighbours`. An operational number, no judgement about a corpus (ADR-087), so no profile key; it stands beside the resolution in the graph it shapes, and costs nothing further. | **Later**: shown read-only now (§12); edited once a record gives it a profile key on a measurement of what it moves | Enters the scoring run where it is not 15 (§9) |
| top 3, the mean's K | `RelevanceScorer.TOP_K` | **Stays in code.** It defines ADR-020's score, the scale `relevanceScoreFloor` and every label's score are read on, so another value carries a floor across scales with no error; and no page informs it. | **Never** | None: unchanged |
| five bands | `RelevanceDistribution.BANDS` | **Stays in code.** ADR-088 fixes it so that one archive is divided as another is. | **Never** | None: unchanged |
| twelve from each | `RelevanceDistribution.SAMPLED_PER_BAND` | **Stays in code.** ADR-088's sixty is a ceiling on a person's sitting, and the README, the report and `vespera label --auto` all rest on it. A local model answering may yet free it; that is a record of its own. | **Never**, until such a record | None: unchanged |
| `ocr_preset`, `rapidocr` | `DoclingClient.PINNED_OCR_PRESET` | **Stays in code.** ADR-090 pinned it so that the one choice whose drift is invisible and total is not left to anyone, and its models ship in the image. | **Never** | None: unchanged. It stays in `sentOptions()`, so the extractor identity holds it |
| three timeouts in a row | `FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT` | **Stays in code.** ADR-071's mechanism number, which decides whether a timeout is the document's or the converter's. It is no value an operator has evidence for, `extractionAttempt` answers a busy converter, and moving it would reach every stage from extraction on. | **Never** | None: unchanged |
| 128 permutations, 16 bands, 8 rows, its seed | `similarity.MinHashParameters.DEFAULT` | **Stays in code.** ADR-081's measured choice, and a mechanism parameter as ADR-082 says; `identity()` already keys a signature. Moving it would reach every stage from extraction on. | **Never** | None: unchanged |
| five-word shingles | `similarity.ShingleParameters.DEFAULT` | **Stays in code.** ADR-073 parks its real value on data no page shows, and a change is another measurement of every document. | **Never** | None: unchanged |
| 0.80, 0.95, 32 and 24 | `similarity.RedundancyThresholds.DEFAULT` | **Stays in code.** ADR-082: no measurement of a corpus tells whether two documents at 0.78 are one, and the record moves, not a value. | **Never** | None: unchanged |
| `vespera.sidecars.start`, `vespera.sidecars.wait-at-most` | `application.yaml` already | **Stays a preset.** | **Later**, as a launch value (§12); shown read-only now | None: in no run's configuration (ADR-323 §9) |
| `vespera.progress-interval` | `application.yaml` already | **Stays a preset.** | **Later**, as a launch value (§12); shown read-only now | None: in no run's configuration (ADR-265) |
| `vespera.open-browser` | `application.yaml` already | **Stays a preset.** | **Never**: under the control panel the child runs with `CI=1` and opens nothing, so there is nothing to set (ADR-325 §8, ADR-327 §5); shown read-only | None: in no run's configuration (ADR-327 §6) |
| `vespera.docling.image` | `application.yaml` already | **Stays the processor build** (§11). | **Now**, on the launch page, as the launch value it is (§11) | Unchanged at the shipped value; another image is another extractor (ADR-147) |

### 9. The presets' identity

**`vespera.clustering.resolution: 1.0` and `vespera.clustering.neighbours: 15` ship in `application.yaml`**, held exactly by a test, as ADR-298 §7 holds the embedding model: another shipped value re-clusters every corpus whose profile names none, and is changed by a record.

**The profile's `clusterResolution` overrides the first.** It is `Profile`'s last component and `ProfileKey`'s last constant: `NumericValue`, earliest run `EMBEDDING_SCORING`, unset by default with no provenance written when the shipped value applies (ADR-298 §4). It is saved where it is a finite number above 0: `"<value>" is not a number above 0`. Unset or blank, the shipped value applies. **Unreadable, the shipped value applies**, as `translationSimilarityFloor` applies nothing, and `NextAction` names it after `extractionAttempt`, in ADR-294 §2's row. It is no gate. A class of `pipeline` resolves it, as `EmbeddingModelGate` resolves the model, and is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule: what it resolves names the run.

**Each enters `EmbeddingScoringConfigConsumed`, the record `document_cluster` is keyed under**, as two components after `preferredLanguages`, in this order, each `@JsonInclude(NON_NULL)`:

| Component | Written |
| --- | --- |
| `clusterResolution`, a number | where the value resolved is not **1.0** |
| `clusterNeighbours`, a whole number | where the value resolved is not **15** |

The two numbers stand in `StageRuns` as what every run before the presets was minted under, and they do not move if a later record changes what ships: a preset shipped at another value is written, so it cannot be taken for a run of 1.0. So **a stated 1.0 and an unset key arrive at one scoring run** (ADR-298 §3), and every run's configuration text at the shipped values is what it was, as `RunIdentityGoldenTest` holds, unedited. The scoring run reads the two as it is minted and hands them to the clustering step, so the number that names the run is the number that clusters (ADR-117).

A value no graph can be built from, named by configuration, a whole number below 1 or a resolution not above 0, stops the invocation where the scoring run is minted, with one line naming the key.

### 10. Which run ids move

**Part A moves none.** It changes `io.algernon.controlpanel`, outside every module, and `pipeline`, which no stage names; the file of §1 is in no run's configuration.

**Part B moves four stages, once, because it edits `embedding`, and not because of any value:**

| Stage | When the change that edits `embedding` lands |
| --- | --- |
| byte-level-reduction, extraction, content-census, content-redundancy | **no move** |
| seed-measurement, embedding-scoring, arrangement, generation | **move**: each names `embedding`, so its implementation version is the new commit |

Its configuration text stays byte for byte at the shipped values, so this is the whole of the move, and it cannot be less. The constants lie in classes of `embedding`, and ADR-058's version is the last commit touching that folder, whatever it did. `application.yaml`, `Profile` and `StageRuns` alone would leave a preset that reaches no graph. **Part B therefore lands as a commit of its own, after Part A**, so that Part A asks no pass of anyone.

That the other candidates stay in code is what keeps it to four: a constant of `similarity` or `extraction` moved out would move extraction and every stage after it.

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
- The control carries `list="docling-images"`, a `datalist` of the sidecar's image first, then `docling.images`, each once, so the image running is one pick away and anything may still be typed. The image picked is a launch value, stored as an invocation starts. **No file of the working directory holds it.**

The check stays advice: Run stays enabled, and stage 2 stops before a conversion is recorded under another image (ADR-179).

### 12. A key that is not the profile's

**Such a key is the application's, of one machine, and its store is not `profile.yaml`**: a value there would be read with the corpus's judgements, travel with the corpus and enter no run honestly. Nor may the control panel write `application.yaml`, which lies inside the jar, or `application-local.yaml`, which is the operator's by hand and outside ADR-295 §2's files, in a form it does not read.

**So what the control panel shows of one comes from §1's file, and what it edits of one is a launch value** (ADR-292): held per user in the launch values' own file, keyed by the jar's own property as text, and passed in the child's environment by its name in capitals. That links no class of the jar (ADR-285 §3), leaves `profile.yaml` the profile's store, and a contract test holds the name. `machine-values` on the launch page lists `vespera.sidecars.start`, `vespera.sidecars.wait-at-most`, `vespera.progress-interval`, `vespera.open-browser` and `vespera.clustering.neighbours`, in that order, each an item with `data-key`, `data-value` and `data-source`, and no control. A key becomes editable by the record that adds it to `LaunchValues` and to ADR-292 §4's table.

**A preset that enters a run's identity is not a launch value**: it decides what a run produces, and needs the provenance only a profile key carries. So `vespera.clustering.neighbours` is edited, once it is, through a profile key.

### 13. The rules a new write path obeys

- `/profile-repair` is a path of the pages, so `Host`, `Sec-Fetch-Site` and `Origin` stand before it as before every page (ADR-325 §2), and a form from another site is refused before any handler. No new path answers a `GET` with an effect (ADR-302 §1).
- The server's classes still write nothing; the pages' classes write (ADR-291 §8). The picker's question and `/version` are read-only, through `HttpURLConnection` and `JsonReader` (ADR-285 §3, ADR-302 §5). Nothing is started, stopped or pulled (ADR-179).
- `WorkingDirectoryWrite` makes every write of §5, §6 and §7, while no child runs, holding the lock, one at a time (ADR-295 §1). A repair's copy holds what the profile holds, and no text of a document and no token (ADR-291 §8).
- No page gains a script or an event attribute (ADR-325 §4.1): `datalist`, `textarea` and `select` need none.

### 14. What the implementation owes

**In `io.algernon.controlpanel`**: a reader of §1's file beside `NextInvocation`; `ValuePages` and `NextPages` as §2 to §6 say; `ProfileKey` gaining `CLUSTER_RESOLUTION` and which keys have a value beneath them; `ProfileEdit` reading a measurement and a repair, its `rewrite` and its form unchanged; `WorkingDirectoryWrite` making a folder and a repair's copy; `SidecarChecks` as §11 says; `NextPages.REPORTS_OF_A_KEY` naming `cluster-sizes.html`; and every name of ADR-302 §9's table these add. All of it in `java.base` and `jdk.httpserver`.

**In `pipeline`**: the writer of §1's file, and `StageRuns`, the resolving class and `NextAction` as §9 says. **In `profile`**: `clusterResolution`. **In `embedding`**: `Clustering` taking the two numbers in place of its constants, and `Communities`' constant gone. **In `application.yaml`**: the two presets.

**In the documents, in the change that builds each**: the README's table of values and section of what the working directory holds, which `docs/check-claims.mjs` reads from code; `CONTEXT.md`'s Working directory and Control panel entries; `docs/architecture.md`'s sentence on the control panel's server.

**Of the tests.** Every class and method named in the first table below carries `@Disabled` naming this record until it is built; **removing those lines is the one edit owed to them.** Beside it, tests that hold what §5, §7 and §9 change are to be edited as this record amends them, as named below the tables.

## Known limits

- A launch value emptied since the last invocation leaves the file naming the image that invocation was passed, as `environment`, until the next one writes it (§1, §11).
- Stale is told by the jar's modification time alone: a jar copied into place with an old time reads as older than it is.
- With no invocation yet, the shipped values are not known to the page: the control panel reads no configuration of the jar's, so a working directory's first page says so.
- A save that stops at §5's fifth step leaves part of the form saved, which the outcome says. It can be reached only by something outside the form changing under it, in the moments between two writes.
- A `drawn` is of what the page showed: a second tab's save is seen by the first as the file having changed, and refused where it touches that key.
- `/profile` waits up to the two timeouts for an Ollama that does not answer, on each `GET`.

## Alternatives refused

- **A fourth command, or `--print-values`, for the control panel to ask.** It would start a JVM and a Spring context for each page, and ADR-278 keeps the child to the commands that do work.
- **The control panel reading `application.yaml` out of the jar.** It would need a YAML reader and would know the shipped file and not the machine's layers, where `VESPERA_DOCLING_IMAGE` lives; `source` would be a guess.
- **The file deleted at each start, as `next-invocation.properties` is.** A failed invocation would leave the page saying nothing, though what the jar would use has not changed; what stops a next act being offered does not bear on a default.
- **Writing the file at the end of an invocation.** The longest invocations are the ones that end last, and a page open during one has nothing to say.
- **Filling a field with the value in use.** Save would write the default into the profile, the one thing ADR-298 §4 refuses.
- **Rows of `input` for a list, with a button adding one.** It needs a script writing into the page (ADR-325 §4.2) or a round trip per row, and a list of a hundred endings becomes a hundred controls.
- **Stopping at the first refused key, as `/next` did.** It writes part of what the operator judged whole on a mistake the control panel could see, each key costing its runs.
- **Going on past a refused key, writing the rest.** The same, and a file the operator did not see as one.
- **All or nothing past the checks, putting written keys back.** A write that undoes a write can fail; one file that moves key by key, each move whole, is what ADR-295 chose.
- **A key with one digest for the whole file, in place of one for each key.** A measurement refreshed by the jar would refuse every save on a page left open, and what was typed could not be told from what was drawn.
- **Reading any YAML in the control panel, so that no file needs repairing.** ADR-295 refused it, and this keeps that: three narrow repairs each have one reading, and the jar's own reader is held to them by a test.
- **Repairing what the reader can only guess at**, a key `Profile` does not hold moved to the nearest name or dropped, the second of a key twice kept. That silently loses a value the operator wrote.
- **A repair with no copy.** A comment is the operator's writing, and the file moves over its past.
- **A repair the jar performs.** `ProfileStore` drops every comment with no copy, and needs a child and a fourth command to be asked.
- **Making the working directory on any file's write.** `relevance-labels.yaml` is refused where it is absent, so its save would leave an empty folder for nothing.
- **Every candidate a preset, since the operator's direction runs that way.** Three cost every stage from extraction on, and each stays where its record put it for a reason a yaml value does not answer.
- **`TOP_K` a preset, since `embedding` moves anyway.** A floor read on one scale, applied on another with no error, is the failure it would add.
- **A shipped default of the card's image.** Every machine with no card stops, CI tests another image than it ships, and every run on the default moves.
- **Finding the card at run time and naming the image from it.** What the identity says would turn on the machine, the coin-flip ADR-090 pinned the client's options to end.
- **A `vespera.clustering.*` key alone for the resolution, with no profile key.** A corpus's own preference would have no provenance, and would apply to whatever corpus that machine runs next.
- **Presets in the identity at every value.** Every scoring run's text would change at the shipped values, where the member's absence changes none.
- **Amending ADR-058 so that a commit may declare it changes no behaviour.** A version is mechanical because a person's claim is what it replaced.
- **Launch values for an identity-bearing preset.** It would judge a corpus from a file named for a machine, with no provenance.

## Consequences

- An operator sees, for every key, what is in use and from where; edits all of them on one page in controls of their type; and a save writes what changed, or says why it wrote nothing.
- The profile stays authored: the file holds only what was saved, as the operator wrote it.
- A file a person or an agent wrote in another form no longer locks the page: it is shown, and repaired where repair can lose nothing.
- The working directory holds one more file the jar writes, and may hold a repair's copy.
- `clusterResolution` joins the profile, so ADR-062's merge adds it to every file at the next load, and the deliverable's index shows it (ADR-186).
- Changing `clusterResolution` mints a scoring run and everything after it, and closes an approval by name, as its cost says; the vectors stay where they are.
- The operator on the card sees the mismatch on the launch page before pressing Run.

## Tests

These hold the record through what it builds. Each is `@Disabled` until then, but for the two named as guards below.

| Method | What it holds |
| --- | --- |
| `controlpanel.ProfileEditorScreenTest.theValueInUseAndWhereItCameFromAreShown` | the profile's value, the jar's, the code's and none, each with its source (§2) |
| `controlpanel.ProfileEditorScreenTest.anAbsentFileAndAStaleFileAreSaidForWhatTheyAre` | no file: a key with a default is `not-known`, another `unset`; a fresh file; one older than the jar marked `stale`, its value kept (§1, §2) |
| `controlpanel.ProfileEditorScreenTest.theFieldsHoldTheFilesOwnWordsAndADefaultStaysOutOfThem` | a field holds what the file states; a default is only a placeholder (§2) |
| `controlpanel.ProfileEditorScreenTest.theNextActsFieldsHoldTheFilesOwnWordsAndTheArrangementToApproveWins` | on `/next`: the arrangement filled, the provenance the file's, and saved so refused (§2, §5) |
| `controlpanel.ProfileEditorScreenTest.eachKeysControlIsTypedAsTheJarReadsIt` | §3's table, each key by name (§3) |
| `controlpanel.ProfileEditorScreenTest.aStatedValueATypedControlCouldNotHoldStaysInAControlThatHoldsIt` | a decimal comma in a text control, a language outside the choices kept, both `unreadable` or selected, and a save leaving the file (§2, §3) |
| `controlpanel.ProfileEditorScreenTest.aListIsEditedOnePieceToALineAndWrittenAsOneLine` | one piece to a line whichever way separated, the same pieces no change, a piece added written on one line (§3, §5) |
| `controlpanel.ProfileEditorScreenTest.theModelPickerOffersWhatOllamaServes` | the models answered, at the launch address or the one the jar last used (§3) |
| `controlpanel.ProfileEditorScreenTest.withOllamaSilentThePickerIsEmptyAndAModelIsStillSaved` | an empty picker saying so, and a model typed saved (§3) |
| `controlpanel.ProfileEditorScreenTest.everyKeyHasAOneLineDescriptionOfItsOwn` | one line for each, no two alike (§4) |
| `controlpanel.ProfileEditorScreenTest.theMeasurementPointerIsShown` | its source and time; none for a null one (§4) |
| `controlpanel.ProfileEditorScreenTest.everyKeyStandsInOneFormWithOneSave` | every key in order in one form, one button, the wanted one marked (§5) |
| `controlpanel.ProfileEditorScreenTest.onlyAChangedKeyIsWrittenAndNothingChangedWritesNothing` | nothing changed: no lock and the same bytes; one changed: every other byte kept (§5) |
| `controlpanel.ProfileEditorScreenTest.aRefusedKeyWritesNoKeyOfTheSaveAndKeepsWhatWasTyped` | no key written, the refused one named, what was typed shown once (§5) |
| `controlpanel.ProfileEditorScreenTest.aValueChangedUnderTheSameProvenanceIsRefused` | a value under the old provenance, and one with none (§5) |
| `controlpanel.ProfileEditorScreenTest.aKeyTheFileChangedSinceThePageWasDrawnIsNotWrittenOver` | left alone it is kept, typed over it is refused (§5) |
| `controlpanel.ProfileEditorScreenTest.aHeldLockWritesNothingAndKeepsWhatWasTyped` | another holder: nothing written, the outcome said, what was typed kept (§5) |
| `controlpanel.ProfileEditorScreenTest.aRefusedKeyOnTheNextActWritesNoKeyOfIt` | on `/next`, the good key before the refused one is not written (§5) |
| `controlpanel.ProfileEditorScreenTest.aFileNotInFormShowsItsLineAndARepairThatLosesNothing` | the line, the changes, no values form; repaired, the exact text, the jar's `ProfileStore` reading equal profiles before and after, a copy whole (§6) |
| `controlpanel.ProfileEditorScreenTest.whatTheRepairCannotReadIsLeftToTheOperator` | an unknown key, a key twice and a flow mapping: the line, no repair, the text, the file as it was (§6) |
| `controlpanel.ProfileEditorScreenTest.aBadValueIsTheOperatorsAndNoRepairTouchesIt` | a decimal comma kept through a repair, shown `unreadable` (§2, §6) |
| `controlpanel.ProfileEditorScreenTest.theRepairIsRefusedWhereTheFileChangedSinceThePageWasDrawn` | no change, no copy, the outcome said (§6) |
| `controlpanel.ProfileEditorScreenTest.theRepairsAddressWritesNothingOnAGetAndRefusesAnotherSite` | `GET`: to `/profile`, nothing written; `POST` of another `Origin`: 403 (§6, §13) |
| `controlpanel.ProfileEditorScreenTest.theFirstSaveCreatesAMissingWorkingDirectoryOwnerOnly` | the exact file; `rwx------` for each folder made, where permissions apply (§7) |
| `controlpanel.ProfileEditorScreenTest.aRefusedValueOrAPageOpenedCreatesNoFolder` | neither a `GET` nor a refused value makes one (§7) |
| `controlpanel.ProfileEditorScreenTest.thePageSaysWhereTheFirstSaveCreatesTheFile` | `profile-file` in each of three states (§7) |
| `controlpanel.ProfileEditorScreenTest.theGroupingResolutionIsEditedLikeAnyValue` | shown as shipped, its cost and report, 0 refused, 2.5 written (§8, §9) |
| `controlpanel.LaunchImageScreenTest.anEmptyImageIsCheckedAgainstTheImageTheJarLastUsed` | `another-image`, the sidecar's image shown, a warning naming both (§11) |
| `controlpanel.LaunchImageScreenTest.theImagesAreOfferedAndTheOnePickedIsHeldAsALaunchValue` | the list, the field tied to it, `matches` once picked, no file written (§11) |
| `controlpanel.LaunchImageScreenTest.withNoFileTheSidecarsImageIsStillShown` | the image shown, `not-checked` with nothing to compare (§11) |
| `controlpanel.LaunchImageScreenTest.whatThisMachineRunsWithIsShownAndNotEdited` | the five keys with value and source, and no control for any (§12) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.anInvocationWritesTheValuesBeneathTheProfile` | values and sources, a blanked model left out, and every key on the closed list (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aValueTheProfileNamesStaysOutOfTheFile` | the file says what lies beneath a value the profile names (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.noCredentialOrPathEntersTheFile` | a user, a password, a path and a query off an address, and no working directory (§1) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.aRefusedInvocationLeavesTheFileAsItWas` | §1 |
| `pipeline.TheClusteringPresetsInvocationTest.aProfileValueOtherThanTheShippedOneNamesAnotherScoringRun` | 1.0 stated arrives at the run stating none; 2.5 is one more run, its text ending in the member, with a cluster for each document where the other has one (§9) |
| `pipeline.TheClusteringPresetsInvocationTest.anUnreadableResolutionIsNamedAndTheShippedValueApplies` | the same run, and `wants=clusterResolution` (§9) |
| `pipeline.TheClusteringPresetsFromConfigurationInvocationTest.configurationNamesBothAndTheProfileOverridesIt` | both members in order; the profile's 1.0 leaves the resolution's out (§9) |
| `TheShippedPresetsTest.theShippedFileNamesBothAtTheirEarlierValues` | 15 and 1.0, exactly (§8, §9) |

**Guards, passing before this is built and carrying no `@Disabled`**, because each holds what §10 rests on:

| Method | What it holds |
| --- | --- |
| `pipeline.ThePresetsMoveOnlyTheStagesNamingEmbeddingTest.anEditOfEmbeddingMovesTheLastFourStagesAndLeavesTheFirstFour` | `StageModules`' stages naming `embedding` are those §10 says |
| `pipeline.ThePresetsMoveOnlyTheStagesNamingEmbeddingTest.anEditOfSimilarityWouldReachEveryStageFromExtractionOn` | those naming `similarity` are those §8's reasons say |

`pipeline.RunIdentityGoldenTest`, **unedited**, holds that no configuration text moves at the shipped values (§9, §10), as `DoclingSidecarImageTest` and its literal hold §11's image. `controlpanel.TheControlPanelLinksNoClassOfTheJarTest` holds the new classes to ADR-285 §3.

**Tests the implementation adds, since each names what does not yet exist**: `pipeline.TheControlPanelReadsTheEffectiveValuesTheJarWritesTest`, each shape the jar writes read by the control panel's reader, as ADR-294's contract test holds its file; `embedding.ClusteringTest`, that the neighbours handed in reach the graph, which the fixture's identical vectors cannot show by a count; and `pipeline.TheJarReadsWhatTheControlPanelWritesTest`'s samples of `clusterResolution`.

**Tests that hold what this amends, edited in the change that builds it:**

- `controlpanel.ProfileScreenTest.everyKeyHasAFormOfItsOwnWithItsCostAndOneIsSaved` and `aKeyTheNextInvocationWantsHasNoFormOfItsOwn`, of a form for each key, whose claims §5 replaces; its third test holds on `report-<key>` and stands.
- `controlpanel.WorkingDirectoryWriteTest.aFolderOthersCanWriteInOrAnotherUserOwnsIsNotWrittenIn`, whose claim that a working directory that does not exist is refused holds for the other two files alone (§7).
- `controlpanel.ProfileKeyTest` and `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theKeyTableIsTheProfilesComponentsAndTheRunsAreTheStagesModules`, of the key added (§9).
- `embedding.ClusteringTest` and `embedding.ClusteringProgressTest`, which name the constant `NEIGHBOURS` and `clusterAndRecord` as they stand (§14).

## What this does not decide

- **Whether an agent may read `effective-values.properties` or a repair's copy**: ADR-261's list is unchanged.
- **Who removes a repair's copy.** Nothing does; it is the operator's.
- **The words of `about-<key>`, of any line said here without a table, and how a page lays them out.**
- **Whether `ollama-check` on the launch page also asks after the models §1's file names**, as it asks after the profile's.
- **Whether the launch page's label model gets the picker.**
- **Whether `generationContextWindow`'s number, or `deliverableLanguage`'s, moves from the code into `application.yaml`**: they enter as `code`, and no candidate here named them.
- **Editing the keys §8 marks later**, each by a record of its own.
- **Whether `vespera.models.pull` and `vespera.models.pull-stalled-at-most` (ADR-329) join §1's list and `machine-values`.** They are the application's and no profile values, as §12's keys are; until a record adds them the page does not show them.
