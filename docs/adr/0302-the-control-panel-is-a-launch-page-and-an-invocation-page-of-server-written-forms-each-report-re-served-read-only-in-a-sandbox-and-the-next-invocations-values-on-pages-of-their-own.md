# ADR-302 — The control panel is a launch page and an invocation page of server-written forms, each report re-served read-only in a sandbox, and the next invocation's values on pages of their own

> **Partly amended — see [ADR-306](0306-the-control-panel-reads-a-holder-line-of-its-own-process-as-held-by-nobody-and-serves-the-cluster-page-sizes-as-a-sixteenth-report.md).** Of §2: a holder line of `vespera.lock` whose pid is the control panel's own process is held by nobody, on its pages and in its start checks, since it holds the working-directory lock only inside a write of its own that ends before the request is answered; the corpus-root lock is read as before. Of §3 and §10: the reports are sixteen, `cluster-page-sizes.html` last. Of §8 and §9: the table of `report-<key>` links gains `clusterPageByteCeiling`, linked to `/report/cluster-page-sizes.html`, and the form of each key on `/profile` holds `report-<key>` from the same table. Of its Tests: a held working directory is faked with another live process than the control panel's. The rest of this record stands.

> **Partly amended — see [ADR-307](0307-the-child-the-control-panel-started-ends-with-it-wherever-its-end-runs-the-jvms-shutdown-hooks.md).** Of §11: ending the control panel by `Ctrl+C`, by closing its terminal or console window, or by any end that runs the JVM's shutdown hooks ends the child it started, its descendants first, the hook waiting up to 2 seconds to see them end; only a forced end of its process, `SIGKILL` or Windows's `TerminateProcess`, leaves the child running, shown held at the next start. The rest of this record stands.

> **Partly amended — see [ADR-323](0323-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md).** Of §1: every page of the control panel's own carries one script, `/control-panel.js`, by its address, and the invocation page is refreshed by it while its child runs, the `<meta http-equiv="refresh">` kept inside `<noscript>`; a request holding the token during a start's wait tells it a tab came back, the one effect a `GET` has beyond showing a page. Of §9: a test also reads `data-start`, `data-tab-key` and `data-refresh-seconds`. Of §11: a start that kept a token first says it waits up to 5 seconds for a tab, and where one comes back says so and prints no address. Of §12: its fifth reason no longer holds, and the other four carry `SameSite=Lax`. Of its Tests: the test that no page holds a script holds one script by its address instead. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names its own package, `java.base` and `jdk.httpserver` alone; it asks the sidecars through `java.net.HttpURLConnection`, of `java.base`, and reads their JSON with a reader of its own (§5). Its §4: one class of its own owns the skeleton, styling and escaping of its pages (§10).
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, §3 to §6: the two command lines, one child at a time, the tail in memory, no connection to `vespera.db` (§6), the holder lines read as there, Stop for its own child alone with its one sentence, and §5's four endings (§2). Its refusal of a cost of stopping read from Spring Batch's `Executing step` line: the table of §7 names no running step and reads no line of output.
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §1, §2, §7 and §8, and §4 and §5 but for what §12 adds: every page, report included, is answered only past the checks of `Host`, the cookie and `Origin`, with the four headers of §7; the port and the token are drawn again at each start.
- **Keeps**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md) §5: the private-paths guard sees an agent's `java -jar` of the control panel and nothing of what the control panel then starts, reads, writes or serves, so an agent driving it is held by the written rule alone.
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md): its file is loaded as the control panel starts and stored as Run or Label starts, and its refusals and warning are shown beside Run (§2, §4).
- **Keeps**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §4 and [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md): the next act is read from `next-invocation.properties` after an exit code of 0 and nowhere else, and every save is one of ADR-295's writes, its cost said beside its field before it is saved (§4, §8).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md) §1, §3 to §5: each screen is tested through `PanelBrowser`, finding forms and controls by `name`, pages by path, text by `id` (§9, Tests).
- **Keeps**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §1 to §3: the control panel starts, stops and pulls nothing; it asks Docling which image it runs and Ollama which models it serves, and only reads the answers (§5).
- **Keeps**: [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md) and [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md): no file of a deliverable tree is served (§3); the code sets the control panel lists are those `code-set-names.yaml` holds (§8).
- **Amends**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §6, whose "no request path is ever mapped to a file" gains one exception: each of the fifteen report paths of §3 is mapped to one fixed file name in the working directory, and no part of a request names a file; §3, whose cookie is `SameSite=Strict`: it is `SameSite=Lax`, the rest of its `Set-Cookie` unchanged (§12); §4, whose checks gain one after `Host`: a `GET` or `HEAD` sent `Sec-Fetch-Site: cross-site` or `same-site` is refused, but under `/report/` (§12); §5, whose second line of defence is `Lax`, which a `POST` another site starts does not carry; and its What this does not decide, whose pages, start from `ControlPanel.main` and arrangement are decided here (§1, §3, §11). It carries a note at its head.
- **Amends**: [ADR-288](0288-the-operator-starts-the-control-panel-with-java-jar-and-it-starts-the-jar-beside-its-own-with-the-java-running-it.md) §4, whose `ControlPanel.main` writes one line and exits 0 until a first screen exists: it writes that line and the address of ADR-291 §2, and serves until its process ends (§11); and its What this does not decide, whose README text arrives with the screens. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §2 and its What this does not decide, where the pages sit on the screen (§2, §3); and the second reason of its refusal of a cost of stopping per step, a second copy of the stage-by-stage guide, which §7's table is, condensed from that guide's Resume paragraphs. It carries a note at its head.
- **Amends**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md) §2, which submits a form with every control as the page wrote it: a form is submitted by pressing one of its submit buttons by name, only that button is sent, and a disabled one cannot be pressed (§9). It carries a note at its head.
- **Settles**: [#561](https://github.com/algernon28/vespera/issues/561), and what ADR-278 §4, ADR-285, ADR-292, ADR-294 and ADR-295 leave to it: Run and Label disabled while a holder line is held, where the launch values, their refusals and warning sit, how the next act is shown, and the screens that collect each key, the answers and the approval.

## Context

The **control panel** is a second jar of this build that starts the jar as its child (ADR-278, ADR-285, ADR-288), on a server bound to `127.0.0.1` that answers only a browser holding its start's token (ADR-291). It remembers seven **launch values** and the last seed folder (ADR-292), learns the next act from `next-invocation.properties` (ADR-294), and writes `profile.yaml`, `relevance-labels.yaml` and `code-set-names.yaml` holding the working-directory lock (ADR-295). Its screens are tested through the pages it serves (ADR-297). It has served no page.

**What the jar writes beside the database.** An invocation rewrites `progress.html` and its three companions, `progress-steps.html`, `progress-samples.html` and `progress-runs.html`, linked to each other by their bare file names (`ProgressPage`), and each stage writes its reports in the working directory: `format-mix.html` (stage 1), `extraction-failures.html` and `extraction-survivors.html` (stage 2), `confidence-distribution.html`, `document-frequency.html` and `word-count-distribution.html` (stage 3), `seed-corpus-comparison.html`, `translations.html`, `cluster-sizes.html` and `relevance-labelling.html` (stage 5), and `arrangement.html` (6a). Every one carries the progress page's script or none, and none fetches anything; `arrangement.html` links into the archive with `file:` links. The deliverable is a tree under the working directory or at the deliverable root, its `index.html` linking its cluster pages and copies by relative paths (ADR-274, ADR-276), and the closing line names where it is.

**A browser and a page of another origin.** A browser does not follow a `file:` link from a page served over `http:`. A page served with `Content-Security-Policy: sandbox allow-scripts` runs its own script in an opaque origin: it reads no cookie of the server's and its requests carry `Origin: null`, which ADR-291 §5 refuses on every `POST`.

**What a sandboxed page sends, measured.** In Chromium 141.0.7390.37, driven by Playwright against a server of the same checks outside this build, a page served with `Content-Security-Policy: sandbox allow-scripts` after a `SameSite=Strict` cookie was set on its origin: its `<meta http-equiv="refresh">`, a link it holds to a page of the same origin, and the operator's reload of it, are each sent with `Sec-Fetch-Site: cross-site` and without the cookie, so ADR-291 §4 answers each 403. With the cookie set `SameSite=Lax`, the refresh, the link and a script's `location.reload()` each carry it. Firefox and Edge were not measured.

**The sidecars.** Docling's sidecar answers `GET /version` with a JSON object whose entry `vespera-image` names the image it runs, which stage 2 compares with `vespera.docling.image` before it records a conversion (ADR-179). Ollama answers `GET /api/tags` with a JSON object whose `models` array holds an object per model served, its `name` among its entries.

**Free space.** Stage 4b's build of `shingle_by_hash` needs 58 bytes free on the working directory's drive for each shingle row of the stage-2 run it reads (ADR-225); stage 3's grouping writes about 22 to 23 bytes, and the 4b build about 91, of temporary files for each row they sort (ADR-211); the deliverable's copies need the survivors' bytes plus 1 GiB free on the tree's drive (ADR-274 §2.5, ADR-276 §4). How many rows and bytes these are is in `vespera.db`.

## Decision

### 1. The tool

The control panel's screens are pages of plain HTML forms written by its own server, `ControlPanelServer` on the JDK's `com.sun.net.httpserver`, served to the operator's own browser on `127.0.0.1`. They carry no script of the control panel's own, and htmx is not vendored: no screen replaces a part of a page in place. Every form posts to a path of the control panel and is answered by a `303` to the page that shows the outcome, which a `GET` asks for.

The invocation page (§2) refreshes itself while the child it shows runs, by

```html
<meta http-equiv="refresh" content="2">
```

every 2 seconds, and carries no such element once the child has ended or where none was started.

**No `GET` of the control panel changes anything.** A `GET` or `HEAD` of any path, whatever its query holds, writes no file, starts and stops no child, and changes no value the control panel holds; it only shows a page, or answers `303` to one. Every change is a `POST`. `GET /stop` shows Stop's confirmation and stops nothing (§2, §7).

### 2. The launch page and the invocation page

**`/`, the launch page.** It holds:

- **The form `launch`**, `POST` to `/launch`, with one text control per launch value, each named by its key, in ADR-292 §1's order: `vespera.corpus-root`, `vespera.working-dir`, `vespera.deliverable-root`, `vespera.docling.image`, `vespera.docling.base-url`, `spring.ai.ollama.base-url`, `vespera.label.model`. Each holds the value the control panel holds (below). Its three submit buttons are `run`, `label` and `check`.
- **The holder lines**: `holder-working-directory`, the holder line of `vespera.lock` in the working directory, and `holder-corpus-root`, that of the corpus root's corpus-root lock, its file named by ADR-279 §3's rule in the folder ADR-279 names, the one `VESPERA_CORPUS_ROOT_LOCK_FOLDER` in the control panel's environment, which its child inherits, names where it is not blank, and `<user home>/.vespera-root-locks` otherwise (a folder named for the jar only by a system property, a configuration file or an argument is not seen), each as ADR-278 §4 and ADR-285 §5 read it, and empty where it is held by nobody. While either is held, or the control panel's own child runs, `run` and `label` carry `disabled`, and the page links the progress page (§3).
- **The warnings**, `warnings`, a list beside Run: ADR-292 §5's warning of an empty Docling image, and §5's warnings of the sidecars. Run stays enabled whatever they say.
- **The outcome**, `outcome`: the one line the last form's post gave, shown on the page its `303` lands on, once.
- **The seed folder's form**, §4; **the sidecars**, §5; **the free space**, §6; **the reports**, `reports`, a link per report of §3 the working directory holds.
- Links to `/invocation`, `/next`, `/labels`, `/code-sets` and `/profile`.

**The launch values the control panel holds.** As it starts, the control panel loads ADR-292's file and holds its values. Every post of `launch`, whichever button, replaces the values held by the seven posted, and the holder lines, the sidecars and the free space are read again for them. The file is written only as Run or Label starts an invocation, with the values it starts with (ADR-292 §2). Every other page reads the working directory and the corpus root from the values held.

- **`run`**: refused with ADR-292 §5's line where it names one; otherwise, read again as the post arrives, a holder line held, or the control panel's own child running, refuses it with one line of its own (below); otherwise the file is stored and `run` started as ADR-278 §1's child, and the post answers `303` to `/invocation`. A refused post answers `303` to `/` with its line in `outcome`, and starts nothing.
- **`label`**: the same, with ADR-292 §5's line for Label, starting `label` without `--auto`.

The lines a held lock or a running child gives, `<Run>` being `Run` or `Label` as pressed, and `<holder line>` the line as read:

| Case | Line |
| --- | --- |
| `vespera.lock` held | `<Run> is not started: the working directory <folder> is held by another Vespera invocation (<holder line>)` |
| the corpus-root lock held | `<Run> is not started: the corpus root <folder> is held by another Vespera invocation (<holder line>)` |
| the control panel's own child runs | `<Run> is not started while the invocation the control panel started runs` |

The control panel's own child is asked about first, then `vespera.lock`, then the corpus-root lock, and the first case that holds gives the line. `label-auto`, `save-then-run` and `save-then-label` (§8) start their child by the same checks and lines.
- **`check`**: holds the values and answers `303` to `/`.

**`/invocation`, the invocation page.** It shows the control panel's own child, the last one it started:

| `id` | What it holds |
| --- | --- |
| `child` | the command, `run` or `label` or `label --auto`, and how the child is, with the attribute `data-ending` one of `running`, `ended`, `failed-or-refused`, `defect`, `stopped`, by ADR-278 §5 and ADR-285 §5, or `none` where no child was started since the control panel started; an exit code beside the words where it is not 0 |
| `tail` | the in-memory tail, oldest line first |
| `last-line` | the last line of output that is not blank, empty where there is none |
| `progress` | the link to the progress page (§3), present while a working directory is held |
| `reports` | as on `/` |

While the child runs, the page holds the form `stop`, `GET` to `/stop`, with its one submit button `stop`. `/stop` is §7's confirmation; its form `stop-confirmed`, `POST` to `/stop`, with its one submit button `stop`, stops the child as ADR-278 §6 does and answers `303` to `/invocation`, which then shows `stopped` and does not refresh. Where no child of the control panel's runs, a `GET` of `/stop` answers `303` to `/invocation`.

For a holder the control panel did not start, `/` shows the holder line and the link to that working directory's progress page, and no `stop` form exists (ADR-278 §6).

### 3. The reports, re-served read-only in a sandbox

Each report the jar writes in the working directory is answered at one fixed path, `/report/` and its file name, `.html` kept:

| Path | File in the working directory |
| --- | --- |
| `/report/progress.html` | `progress.html` |
| `/report/progress-steps.html` | `progress-steps.html` |
| `/report/progress-samples.html` | `progress-samples.html` |
| `/report/progress-runs.html` | `progress-runs.html` |
| `/report/format-mix.html` | `format-mix.html` |
| `/report/extraction-failures.html` | `extraction-failures.html` |
| `/report/extraction-survivors.html` | `extraction-survivors.html` |
| `/report/confidence-distribution.html` | `confidence-distribution.html` |
| `/report/document-frequency.html` | `document-frequency.html` |
| `/report/word-count-distribution.html` | `word-count-distribution.html` |
| `/report/seed-corpus-comparison.html` | `seed-corpus-comparison.html` |
| `/report/translations.html` | `translations.html` |
| `/report/cluster-sizes.html` | `cluster-sizes.html` |
| `/report/relevance-labelling.html` | `relevance-labelling.html` |
| `/report/arrangement.html` | `arrangement.html` |

The file name is kept so that the progress page's links to its three companions, by bare name, land on their own paths.

- **Each is a path the server is started with**, as every page is (ADR-291 §6). The file is the one of that name in the working directory the control panel holds (§2); no part of the request, path or query, names a file. Any other path under `/report/` is answered 404 by the server, and no handler runs.
- **The answer** is `200`, `Content-Type: text/html; charset=utf-8`, the file's bytes as they are, and, beside ADR-291 §7's four headers, a second header
  ```
  Content-Security-Policy: sandbox allow-scripts
  ```
  so the report's own script runs, in an opaque origin, and can neither read the control panel's cookie nor post to it. A browser enforces both policies.
- **A file not there**, or no working directory held, is answered `404`, `Content-Type: text/plain; charset=utf-8`, with one line:
  ```
  the working directory holds no <file name>
  ```
- **Every link to a report** carries `target="_blank"` and `rel="noopener"`, so it opens in a tab of its own, beside the control panel.
- **`arrangement.html`'s `file:` links** are served as the jar wrote them, and the browser does not follow them from the served page. Where the next act is the approval (§8), the control panel shows, as text, `arrangement-file`, the absolute path of `arrangement.html`, which the operator opens from their file manager for its links.
- **The deliverable is not served.** Its tree lies at the deliverable root as well as in the working directory, its pages link their copies by relative paths, and serving them would map request paths to files of a tree ADR-283 keeps outside the allow list. The control panel shows where it is through the closing line, which names it, in `last-line`.

### 4. The seed folder on the launch page

The seed folder is a profile value, not a launch value. On `/`, beneath `launch`, the form `seed-folder`, `POST` to `/seed-folder`, labelled as a value of the profile in the working directory it names in `seed-folder-working-directory`, holds `seedFolder`, filled with the last seed folder (ADR-292 §6), `provenance`, empty, its cost in `cost-seedFolder` (ADR-295 §5), and one submit button, `save`.

`save` writes `seedFolder` by ADR-295 §1 to §5, into the working directory held, and answers `303` to `/` with the outcome. A seed folder saved becomes the last seed folder the control panel holds, written into ADR-292's file with the next start of an invocation. Run neither writes it nor reads it from this form.

### 5. Docling's image and Ollama's models, read

When `/` is answered, the control panel asks, each at most once, through `java.net.HttpURLConnection` with a connect and a read timeout of 2 seconds each, the two asked at once:

- **Docling**: `GET <vespera.docling.base-url>/version`, reading the string entry `vespera-image` of the object answered;
- **Ollama**: `GET <spring.ai.ollama.base-url>/api/tags`, reading the string `name` of each object of the array `models`.

Each JSON answer is read by a reader of the control panel's own, in `java.base`, of the JSON grammar whole.

| `id` | `data-check` | When |
| --- | --- | --- |
| `docling-check` | `not-checked` | its address, or `vespera.docling.image`, is empty: the jar's own defaults are not the control panel's to know |
| | `matches` | `vespera-image` equals `vespera.docling.image`, character for character |
| | `another-image` | it differs, or the answer has no `vespera-image`; a warning in `warnings` names both |
| | `not-answering` | no answer in time, a status other than 200, or an answer the reader refuses |
| `ollama-check` | `not-checked` | its address is empty, or no model is named |
| | `serves-all` | each model named is served |
| | `missing` | a model named is not served; a warning in `warnings` names each one |
| | `not-answering` | as for Docling |

The models named are `vespera.label.model` where it is not empty, and `embeddingModel` and `generationModel` where `profile.yaml`, read in ADR-295 §3's form, holds a value for them. A model is served where it equals a `name` answered, or, naming no tag, equals one with `:latest` after it.

Nothing is started, stopped or pulled, and Run stays enabled whatever the checks say: ADR-179's check in stage 2 stops before a conversion is recorded under another image.

### 6. Free space

`free-space` lists the usable bytes, `FileStore.getUsableSpace`, of the drive of the working directory, and of the deliverable root's where it is named, each an item with `data-folder` `working-directory` or `deliverable-root` and `data-usable-bytes` the number. A folder that does not exist is measured at its nearest ancestor that does. Beside them, as text, `free-space-rules` gives the rules of the Context: 58 bytes for each shingle row of stage 4b's build, 22 to 23 and 91 bytes of temporary files for each row stage 3 and stage 4b sort, and the survivors' bytes plus 1 GiB for the copies. The control panel computes no need: the rows and bytes are in `vespera.db`, to which it opens no connection (ADR-278 §3).

### 7. What a stop costs at each step

`/stop` holds, in `stop-sentence`, ADR-285 §5's sentence, and, for a `run` child, in `stop-costs`, a table of a row per step of `vespera run`, in the order they run, each row with `data-step` the step's name, saying what a stop in that step costs. The rows are condensed from `docs/running-stage-by-stage.md`'s Resume paragraphs and change with them:

| `data-step` | A stop there costs |
| --- | --- |
| `census` | the entries walked since the last commit, about every thousand entries, which are walked again |
| `byte-level-reduction` | the step, done again from the start; quick next to extraction |
| `extraction` | at most the batch open when it stopped; no conversion stored is made again |
| `content-census` | the pass, done again |
| `redundancy-signature` | its own work, done again from the stored text; the other step's finished work stays |
| `content-redundancy` | its own work, done again; the signatures stay |
| `seed-extraction` | its own work, done again, the conversions read back from the extraction cache |
| `seed-corpus-comparison` | its own work, done again |
| `embedding-scoring` | nothing computed: every vector stored is kept |
| `translation` | its decisions, made again |
| `relevance-scoring` | the scores, computed again from the stored vectors |
| `relevance-floor` | nothing: it decides again on every invocation |
| `clustering` | the clusters, formed again from the stored vectors |
| `relevance-report` | nothing: its two files are written again on every invocation that reaches it |
| `arrangement` | the arrangement, made again |
| `generation` | the cluster being written, asked about again; every cluster written is kept |

The page renders a cluster as a group (ADR-122). For a `label` child, `stop-costs` is not on the page. The table names no running step: the control panel does not know which step runs.

### 8. The next act, the label answers, the code-set names and the profile

**`/next`.** It shows the next act ADR-294 §4 reads after the control panel's own child ended with 0:

- `next-invocation`, with `data-invocation` one of `run`, `label`, `none`, or `unknown` where no child of the control panel's has ended with 0 since it started, or the file is not there or not in ADR-294 §1's shape.
- Where `wants` is not empty, **the form `next`**, `POST` to `/next`, holding for each key it names, in its order, a control named by the key, its provenance's control named `<key>.provenance`, empty and required, the key's cost in `cost-<key>` (ADR-295 §5), and, beside it, `report-<key>`, a list holding a link to each report the README's table of values names for the key, in that table's order, each opening in a tab of its own (§3):

  | Key | `report-<key>` links |
  | --- | --- |
  | `boilerplateDocumentFrequencyFloor` | `/report/document-frequency.html` |
  | `relevanceScoreFloor` | `/report/relevance-labelling.html`, `/report/cluster-sizes.html`, `/report/seed-corpus-comparison.html` |
  | `degenerateOutputConfidenceFloor` | `/report/confidence-distribution.html` |
  | `degenerateOutputWordFloor` | `/report/word-count-distribution.html` |
  | `extractionAttempt` | `/report/extraction-failures.html` |
  | `arrangementApproved` | `/report/arrangement.html` |
  | `logTimestampShareFloor`, `codeNameEndings`, `outOfScopeNameEndings`, `configurationSettingShareFloor` | `/report/format-mix.html` |
  | `preferredLanguages`, `translationSimilarityFloor` | `/report/translations.html` |

  `seedFolder`, `embeddingModel`, `generationModel` and `generationContextWindow`, which no report informs, and any key the README's table does not name, have no `report-<key>`. A key the README's table gains a report for gains it here in the same change. Its submit buttons are `save` and `save-then-run`. A key's control is empty, but for `seedFolder`, filled with the last seed folder, and `arrangementApproved`, filled with the file's `arrangement`, the arrangement to approve, which is how it is approved.
- `save` writes the keys in the form's order, each one of ADR-295's writes, and stops at the first refused, its line the outcome. `save-then-run` does the same and, every key written, starts `run` as `launch`'s `run` does, answering `303` to `/invocation`.
- Where `answer=true`: `labels-link`, a link to `/labels`, and **the form `label-auto`**, `POST` to `/label-auto`, with one submit button `label-auto`, which starts `label --auto` (ADR-278 §1) as `launch`'s `label` starts `label`.

**`/labels`.** Where `relevance-labels.yaml` is in the working directory, **the form `labels`**, `POST` to `/labels`, holds for each question `i`, from 1 in the file's order, a hidden control `path-<i>` holding its path and a select `relevant-<i>` of three options, the empty value for no answer, `true` and `false`, the file's answer selected. Its submit buttons are `save` and `save-then-label`. `save` writes the answers by path by ADR-295 §6; `save-then-label` then starts `label`. Where the file is not there, the page shows ADR-295 §6's line in `outcome`.

**`/code-sets`.** **The form `code-sets`**, `POST` to `/code-sets`, holds for each entry `i` of `code-set-names.yaml`, from 1 in its order, a hidden `folder-<i>` and a text `name-<i>`, then one empty row, `new-folder` and `new-name`, and one submit button `save`, which writes the list by ADR-295 §7: a name cleared removes its entry, and a new row filled adds one. The list shown is the file's entries alone. The default names, and which sets exist, are in the deliverable index's Code section (ADR-296), which the control panel neither reads nor serves; it reads no `vespera.db` to list them.

**`/profile`.** For every `ProfileKey` that the form `next` does not hold, in `ProfileKey`'s order, **a form named by the key**, `POST` to `/profile`, holding a hidden `key`, `value`, `provenance`, required, its cost in `cost-<key>` and its current value and provenance as `profile.yaml` holds them in `current-<key>`, and one submit button `save`, which writes that key by ADR-295.

Every save answers `303` to its page, its outcome in `outcome`, and is refused with ADR-295 §1's line while the control panel's child runs.

**The cost is confirmed by pressing Save.** Each key's cost is shown beside its field, in `cost-<key>`, on the page that holds the field, before anything is posted; pressing `save` or `save-then-run` with the cost in view is ADR-295 §5's confirmation, and no second page asks again.

### 9. What a test holds

A test finds a form and a control by `name`, a page by its path and a text by `id`, and reads the attributes `data-ending`, `data-check`, `data-step`, `data-folder`, `data-usable-bytes` and `data-invocation`, never words (ADR-297 §5):

| Page | Forms, controls and buttons (`name`) | Texts (`id`) |
| --- | --- | --- |
| `/` | `launch`: `vespera.corpus-root`, `vespera.working-dir`, `vespera.deliverable-root`, `vespera.docling.image`, `vespera.docling.base-url`, `spring.ai.ollama.base-url`, `vespera.label.model`; buttons `run`, `label`, `check`. `seed-folder`: `seedFolder`, `provenance`; button `save` | `holder-working-directory`, `holder-corpus-root`, `warnings`, `outcome`, `docling-check`, `ollama-check`, `free-space`, `free-space-rules`, `reports`, `seed-folder-working-directory`, `cost-seedFolder` |
| `/invocation` | `stop`: button `stop`, while the child runs | `child`, `tail`, `last-line`, `progress`, `reports` |
| `/stop` | `stop-confirmed`: button `stop` | `stop-sentence`, `stop-costs` |
| `/report/<file name>` | none | none |
| `/next` | `next`: `<key>`, `<key>.provenance` per key of `wants`; buttons `save`, `save-then-run`. `label-auto`: button `label-auto` | `next-invocation`, `cost-<key>`, `report-<key>` (a list of links), `labels-link`, `arrangement-file`, `outcome` |
| `/labels` | `labels`: `path-<i>`, `relevant-<i>`; buttons `save`, `save-then-label` | `outcome` |
| `/code-sets` | `code-sets`: `folder-<i>`, `name-<i>`, `new-folder`, `new-name`; button `save` | `outcome` |
| `/profile` | one form per key, named by it: `key`, `value`, `provenance`; button `save` | `cost-<key>`, `current-<key>`, `outcome` |

The paths posted to are `/launch`, `/seed-folder`, `/stop`, `/next`, `/label-auto`, `/labels`, `/code-sets` and `/profile`, each answering a `GET` as its page where it has one and a `303` to `/` where it has none.

**A form is submitted by pressing a button.** A browser sends, of a form's submit buttons, only the one pressed, and none that is disabled. `PanelBrowser` gains `press(String formName, String button, Map<String, String> fields)`: it submits the form as `submit` does, sending of its submit buttons only the one named, and fails the test, sending nothing, where the form holds no such button or it is disabled. `submit` sends none of a form's submit buttons.

### 10. The classes

In `io.algernon.controlpanel`, linking no class of the jar:

```java
public final class ControlPanelPages {
    /** The pages of §2, §3, §7 and §8, by path, for ControlPanelServer.start. */
    public static Map<String, HttpHandler> pages(ChildRuntime runtime, Path launchValuesFile,
            Path corpusRootLockFolder, String userName);
    /** §3's fifteen file names, in its table's order. */
    public static final List<String> REPORTS;
}

final class ControlPanelPage { /* the one owner of the pages' skeleton, styling and escaping (ADR-285 §4) */ }
```

The pages start the child, write files and read the sidecars; the server's classes still do none of these (ADR-291 §8).

### 11. The start

`ControlPanel.main` finds the child's runtime (ADR-288 §3, §4), writes ADR-288 §4's line, loads ADR-292's file from `LaunchValues.fileIn` under `java.io.tmpdir` for `user.name`, starts `ControlPanelServer` with `ControlPanelPages.pages`, given §2's corpus-root lock folder, and writes one more line on standard output:

```
open http://127.0.0.1:<port>/?token=<token> in your browser
```

It then serves until its process ends. Ending it, by closing the terminal it was started in or by `Ctrl+C`, is a hard close: the child is left running (ADR-278 §7).

### 12. The cookie is `SameSite=Lax`

The token is exchanged for

```
Set-Cookie: vespera-control-panel=<token>; Path=/; HttpOnly; SameSite=Lax
```

and everything else of ADR-291 §3 stands. Under `SameSite=Strict` a report's own refresh, its links to its companions and the operator's reload of it are sent without the cookie and refused (Context), so a report could be opened once and not followed or refreshed.

What `Lax` costs: a top-level `GET` that another site starts, a link the operator follows from another site's page or a form of method `get` it submits, now carries the cookie. That is accepted because:

- the other site's page cannot read the answer: the browser's same-origin policy keeps the response from its script, and ADR-291 §7's `frame-ancestors 'none'` keeps it from framing one;
- no `GET` of the control panel changes anything (§1), so the request it can make does nothing it could use;
- every `POST` still needs the control panel's own `Origin` (ADR-291 §5), and a `POST` another site starts carries no `Lax` cookie in any case;
- `Host` is still checked first (ADR-291 §4), so a name rebound to `127.0.0.1` is refused whatever cookie it carries;
- the port and the token are drawn again at every start, so an address another site could aim at is good for one start only.

**A page opened by another site is refused, but for a report.** Under `Lax`, another site can open `/` or `/stop` in a window of the operator's, the page served with its forms live; a click the other site tricks the operator into, through a window it opens over the control panel's or a double click whose second click lands on a control panel's button (clickjacking), would then post from the control panel's own page, with its own `Origin`, past ADR-291 §5. `frame-ancestors 'none'` keeps the page from a frame, and not from a window. So the server refuses a `GET` or `HEAD` whose `Sec-Fetch-Site` header is `cross-site` or `same-site`, on every path but one beginning `/report/`:

- **Where it is checked.** After `Host` (ADR-291 §4, first), and before the token's exchange (ADR-291 §3) and the cookie: a `GET` another site starts is refused though it holds the cookie or the token, and the token is not exchanged on it. Then the cookie, the method (ADR-291 §5), the `Origin` of a `POST`, and the path (ADR-291 §6), as before.
- **What it refuses.** `cross-site`, and `same-site`, which is what a browser sends from another port of `127.0.0.1` or `localhost`: another local user's server there is the site ADR-291's known limits name, which receives the control panel's cookie and could open its pages. The value is compared without ASCII case.
- **What it admits.** `same-origin`, the control panel's own pages and their redirects; `none`, the printed address pasted into the address bar, opened from a bookmark or clicked in a terminal; and no `Sec-Fetch-Site` at all, as `PanelBrowser`, `RawHttp` and a browser that sends none send it. A `POST` is not read for it: its `Origin` is checked (ADR-291 §5).
- **The refusal** is ADR-291 §4's: 403, `Content-Type: text/plain; charset=utf-8`, no `Set-Cookie`, the one line `open the address the control panel printed when it started`, and no page's handler runs.
- **The reports are not refused by it.** A report's own refresh, its links to its companions and a reload of it are sent `cross-site`, the sandbox giving it an opaque origin (Context); a report holds no form, and its answer is the file read, which changes nothing (§1).

### 13. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves, and no dependency or plugin is added.

## Alternatives refused

- **A desktop window, JavaFX or Swing.** Neither is in ADR-285 §3's two modules, and a window cannot show the reports, which are HTML pages with a script.
- **A page built by script, in TypeScript or JavaScript.** It needs Node and a second build, and a script of the control panel's own, where server-written forms show every screen.
- **htmx.** No screen replaces a part of a page in place; the invocation page refreshes whole.
- **A report shown in an `iframe` of a control-panel page.** ADR-291 §7's `frame-ancestors 'none'` would have to be loosened for it.
- **A report linked by its path on disk.** A browser does not follow a `file:` link from an `http:` page.
- **A report at a path without `.html`.** The progress page's links to its companions, by bare file name, would land on paths the server does not serve.
- **A report path naming the file from the request.** It maps request paths to files, which ADR-291 §6 refuses; the fixed list maps none.
- **The deliverable served.** Its pages link copies by relative paths under a tree ADR-283 keeps outside the allow list, and serving them maps request paths to files.
- **The seed folder among the launch values, saved on Run.** It is a profile value, written by ADR-295 with its provenance and its cost said first; a Run would write it with neither.
- **The seed folder left off the launch page.** It is the one profile value the first `vespera run` stops for, and ADR-292 §6 offers it as the working directory is named.
- **The sidecars' answers checked by `java.net.http`'s `HttpClient`.** It is a module beyond ADR-285 §3's two, for two `GET`s `HttpURLConnection` makes.
- **Run disabled on a sidecar's warning.** The jar's own check stops before it costs a conversion, and the control panel cannot know every default the jar falls back on.
- **A need for free space computed.** It needs counts from `vespera.db`, to which the control panel opens no connection.
- **Keeping `SameSite=Strict`.** A report could be opened once and neither refreshed nor followed to its companions: the measured Chromium sends each without the cookie, and the control panel refuses it.
- **`Lax` with no check of `Sec-Fetch-Site`.** A page another site opens would hold live forms, and a click tricked onto one posts with the control panel's own `Origin`.
- **Refusing a request with no `Sec-Fetch-Site`.** `PanelBrowser`, `RawHttp` and a browser that sends none would be refused; such a request is no page of another site's that a browser marked as one.
- **`sandbox allow-scripts allow-same-origin` on the reports.** The report's script would run in the control panel's own origin, able to post a form with its `Origin` and to read every page of the control panel by script; a report is a page the control panel did not write.
- **The reports served without the cookie.** Any page of another site, or another local user, could read a report, and the reports name paths of the archive and the relevance labelling page carries text of documents (ADR-261).
- **A cost of stopping read for the running step.** The control panel would read Spring Batch's lines to know it (ADR-278).

## Consequences

- The operator opens one address and works every invocation, value, answer and approval from the browser; each report opens in a tab of its own, sandboxed.
- A report a later stage adds is not served until it is added to §3's list.
- A page another site opens at the control panel's address is answered with the cookie, and shows the operator a page of the control panel that site cannot read; a page of the control panel that would change something on a `GET` breaks §1.
- A change to a Resume paragraph of `docs/running-stage-by-stage.md` changes §7's table with it.
- A `ProfileKey` added gets a form on `/profile`.
- `TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava` no longer sees the process exit 0: it reads the two lines and ends it.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.PanelBrowserTest.aFormIsSentWithOnlyTheButtonPressedAndADisabledOneCannotBePressed` | `press` sends the button pressed, a `button` of type submit, one of no type and a submit input alike, and no other; `submit` sends none; a disabled or absent button fails the test and sends nothing (§9) |
| `controlpanel.ControlPanelServerTest.theRightTokenIsExchangedForACookieTheBrowserDropsWhenItCloses` | the token is exchanged for `vespera-control-panel=<token>; Path=/; HttpOnly; SameSite=Lax`, on a first exchange and on one by a browser already holding the cookie (§12) |
| `controlpanel.ControlPanelServerTest.aPageOpenedFromAnotherSiteIsRefusedButAReportIsNot` | a `GET` sent `Sec-Fetch-Site: cross-site`, `same-site` or `Cross-Site`, holding the cookie, and one holding the token, are answered 403 with ADR-291 §4's line and no `Set-Cookie`, a `HEAD` sent `cross-site` 403, no page's handler run; a `GET` sent `cross-site` to a page at `/report/progress.html` reaches it (§12) |
| `controlpanel.ControlPanelServerTest.aPageAskedForFromItsOwnPagesTheAddressBarOrWithNoMarkIsServed` | `same-origin`, `none` and no `Sec-Fetch-Site` reach their pages in order, and the token sent `none` is exchanged (§12) |

**The build of the screens owes** these, each written first in the build's change, failing with 404 against a server started with no page, and passing once the screens are built:

| Method | What it holds |
| --- | --- |
| `controlpanel.LaunchScreenTest.theLaunchFormHoldsTheSevenLaunchValuesByTheirKeysAsLastStarted` | `/` holds `launch`, posted to `/launch`, with the seven controls in §2's order, filled from ADR-292's file, and the buttons `run`, `label` and `check` (§2) |
| `controlpanel.LaunchScreenTest.theFreeSpaceOfTheWorkingDirectorysAndTheDeliverableRootsDrivesIsShown` | `free-space` names the working directory's drive and the deliverable root's, each with its usable bytes, and `free-space-rules` is there (§6) |
| `controlpanel.LaunchScreenTest.runIsRefusedWithTheLineOfTheValueItNeeds` | pressing `run` with no corpus root lands on `/` with ADR-292 §5's line in `outcome`, and starts no child (§2) |
| `controlpanel.LaunchScreenTest.runAndLabelAreDisabledWhileAnotherInvocationHoldsTheWorkingDirectory` | a held `vespera.lock`: its line in `holder-working-directory`, `run` and `label` disabled, `check` not (§2) |
| `controlpanel.LaunchScreenTest.runAndLabelAreDisabledWhileAnotherInvocationHoldsTheCorpusRoot` | a held corpus-root lock, its file named as the jar's `CorpusRootLock` names it: its line in `holder-corpus-root`, `holder-working-directory` empty, `run` and `label` disabled, `check` not (§2) |
| `controlpanel.LaunchScreenTest.runPressedOnceTheWorkingDirectoryIsHeldIsRefusedWithALineOfItsOwn` | `vespera.lock` taken after the page was shown: `run` lands on `/` with §2's line naming the working directory and the holder line, and starts no child (§2) |
| `controlpanel.LaunchScreenTest.theSeedFolderIsAFormOfItsOwnFilledFromTheLastSeedFolderAndSavedIntoTheProfile` | `seed-folder` filled from the last seed folder; `save` writes `seedFolder` and its provenance into `profile.yaml` (§4) |
| `controlpanel.LaunchScreenTest.aSidecarRunningAnotherImageOrServingNoNamedModelIsWarnedOfAndRunStaysEnabled` | stub sidecars: `another-image` and `missing`, two warnings, `run` enabled; addresses nobody answers: `not-answering` (§5) |
| `controlpanel.LaunchScreenTest.noGetOfAnyPageWritesAFileOrStartsAChild` | a `GET` of every page and of every path a form posts to, with a form's fields in its query, is answered with a page, leaves every file of the working directory as it was, and starts no child (§1) |
| `controlpanel.InvocationScreenTest.runStartsTheChildAndItsPageRefreshesEveryTwoSecondsWhileItRuns` | `run` lands on `/invocation`, `running`, a refresh of 2 seconds, `progress` opening `/report/progress.html` in a tab of its own, and `stop` (§1, §2, §3) |
| `controlpanel.InvocationScreenTest.stopIsConfirmedWithItsSentenceAndWhatAStopCostsAtEachStep` | `stop` leads to `/stop`, ADR-285 §5's sentence and the sixteen steps in order; `stop-confirmed` lands on `stopped`, with no refresh and no `stop` (§2, §7) |
| `controlpanel.InvocationScreenTest.anEndedChildIsShownWithItsLastLineAndThePageNoLongerRefreshes` | a child ending with 0: `ended`, its last line that is not blank, no refresh, no `stop` (§1, §2) |
| `controlpanel.ReportScreenTest.eachReportIsServedAsWrittenInASandboxAtItsOwnPath` | each of the fifteen: 200, the bytes written, `text/html; charset=utf-8`, ADR-291 §7's policy then `sandbox allow-scripts`, `no-store` (§3) |
| `controlpanel.ReportScreenTest.aReportNotWrittenIsNotFoundWithOneLine` | 404, plain text, §3's line (§3) |
| `controlpanel.ReportScreenTest.noOtherFileOfTheWorkingDirectoryIsServed` | `index.html`, `vespera.db`, `profile.yaml`, `vespera.lock`, a name without `.html` and paths climbing out, under `/report/`, all present: 404 (§3) |
| `controlpanel.NextScreenTest.eachWantedValueIsAskedWithItsProvenanceItsCostAndItsReport` | after a child ends with 0 beside a file wanting two keys: `next` holds each key and its provenance in order, `save` and `save-then-run`, each cost, `report-boilerplateDocumentFrequencyFloor` holding one link, to `/report/document-frequency.html`, and no `report-seedFolder` (§8) |
| `controlpanel.NextScreenTest.aValueReadOffSeveralReportsIsLinkedToEachInTheGuidesOrder` | `relevanceScoreFloor` wanted: `report-relevanceScoreFloor` holds the labelling report, the cluster sizes and the seed comparison, in that order, each in a tab of its own (§8) |
| `controlpanel.NextScreenTest.theWantedValuesAreSavedIntoTheProfile` | `save` writes both keys with their provenance (§8) |
| `controlpanel.NextScreenTest.answersWantedLeadToTheQuestionsAndToLabelAuto` | `answer=true`: `labels-link` to `/labels`, `label-auto`, and no `next` (§8) |
| `controlpanel.NextScreenTest.theArrangementToApproveIsOfferedByItsName` | `arrangementApproved` filled with the file's `arrangement`, its report linked and `arrangement-file` shown (§3, §8) |
| `controlpanel.LabelsScreenTest.eachQuestionIsAnsweredByItsPathAndTheAnswersAreWritten` | `labels` with a path and a select per question, the file's answers selected; `save` writes the answers given (§8) |
| `controlpanel.CodeSetsScreenTest.aCodeSetIsRenamedAndOneAddedAndTheListWritten` | `code-sets` lists the file's entries; one renamed and one added are written in order (§8) |
| `controlpanel.ProfileScreenTest.everyKeyHasAFormOfItsOwnWithItsCostAndOneIsSaved` | with no next act read, a form per `ProfileKey`, iterated, in its order, each with its cost; `save` writes one key (§8) |
| `controlpanel.ProfileScreenTest.aKeyTheNextInvocationWantsHasNoFormOfItsOwn` | once the next run wants two keys, a form for every other `ProfileKey` and none for the two (§8) |

They start the server through `ScreenServer`, a class of the tests that writes the launch values and a jar beside the control panel whose main class, `JarForScreenTests`, sleeps or exits as the test says, and that closes by killing only the processes started since it started. The build also owes:

- `pipeline.TheControlPanelServesEveryReportTheJarWritesTest`, holding §3's list equal to the report file names of the jar's classes;
- `pipeline.TheControlPanelsStopCostsNameTheJarsStepsTest`, holding §7's steps equal to the jar's sixteen, in order;
- a contract test holding the control panel's corpus-root lock file, for a root, the one the jar's `CorpusRootLock` takes in the same folder (§2);
- a test holding §8's `report-<key>` table equal to the README's table of values, key by key and in order;
- a test that no page the control panel writes holds a `<script>` element or an attribute whose name begins with `on` (§1);
- a test of the JSON reader of §5;
- a test of `ControlPanelServerTest`'s kind holding that the page classes are outside the server's (ADR-291 §8);
- `TheControlPanelsChildIT.theControlPanelStartedByJavaJarNamesTheJarBesideItAndItsOwnJava` reading §11's two lines.

## What this does not decide

- **An orderly exit of the control panel**, which asks whether to stop the child (ADR-278 §7).
- **The README's text for starting the control panel and the screens**, written with them.
- **The words, layout and styling of each page**, which no test holds.
- **Whether the pages are checked for accessibility.**
- **A path the ANSI code page cannot hold** (ADR-278).
