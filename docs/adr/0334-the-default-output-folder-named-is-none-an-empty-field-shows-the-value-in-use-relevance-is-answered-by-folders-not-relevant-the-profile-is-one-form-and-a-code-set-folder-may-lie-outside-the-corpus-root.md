# ADR-334 — The default output folder named is none named, an empty field shows the value in use, relevance is answered by folders not relevant, the profile is one form, and a code set's folder may lie outside the corpus root

> **Partly amended — see [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md).** Of §7: the provenance field's hint asking for a reason beside each value changed goes; each value has a note filled in for the operator. The rest of this record stands.

> **Partly amended — see [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md).** Of §2: each field of the services starts from the launch values, the environment, `.env`, the GPU preselection or `application.yaml`, and passes nothing where it holds, unchosen, what the child would read anyway; `in use` stays beside a field no source names. Of §11: an image the environment or `.env` names comes before the GPU preselection, which comes before `application.yaml`'s. The rest of this record stands.

> **Partly amended — see [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md).** Of §1 and §2: `in use: ` holds of every field whose value in use the page knows, the working directory's default among them. Of §3: the words name no Launch page. Of §5.2: `code-set-folders` stands in the drawer's section of folders to process. Of §5.5: `new-folder` goes with the form `code-sets`. Of §6: `/labels` is the drawer's section `values-relevance`, its form, file and writes unchanged. Of §7: the form `profile` stands in the drawer, every key's section shown. The rest of this record stands.

- **Date**: 2026-11-03
- **Status**: accepted on 2026-11-03.
- **Decided**: by the operator, that naming the folder the default draws in is read as naming none (§1); that an empty Output folder field and an empty field of the services card show the value that will be used (§1, §2); that the next page says in plain words why it suggests nothing (§3); that a code set's folder may be any folder, read with the corpus (§5); that the relevance questions are answered by a list of folders not relevant, empty at first, every other folder being relevant (§6); and that the profile page is one form of every value with one Save and one Reset, Reset putting the form back to `profile.yaml` as last saved and keeping no copy (§7); that a converter running another image than Run would use is said before Run, naming both, and its image offered (§2); that the invocation page's Output box is a fixed share of the window, showing the newest line after every load unless the operator has scrolled up (§10); and that the control panel preselects the GPU image, listing it first, the jar's own default unchanged (§11). The words, the names of the controls and files, how a folder outside the corpus root is stored, walked and spelt, and how the list of folders maps onto the label file are decided here under the same delegation.
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s *"The archive is never written to"*, and [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md): a code set's folder outside the corpus root is archive, read by the walk and by stage 1 as the corpus is, and written by nothing (§5).
- **Keeps**: [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md): an unfinished walk is continued under its own id, with the folders it recorded when it started (§5.3).
- **Keeps**: [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md): the seed folder is walked under its own walk id, as before; a code set's folder is not, being corpus and not seeds (§5.3).
- **Keeps**: [ADR-169](0169-the-label-file-shows-the-answers-already-recorded-and-a-changed-answer-replaces-the-old-one-and-is-reported.md) and [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md): `vespera label` records the answers the file holds as a person's, a person's answer is never replaced by a model's, and the label file's form is unchanged (§6).
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): each screen is tested through the pages it serves. §3's words are read by its test, the words being what §3 decides.
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md) and `CONTEXT.md`'s _Renders as_: a page's words are free of the _Avoid_ lists, and the deliverable root is "Output folder" on a page.
- **Amends**: [ADR-276](0276-the-deliverable-is-drawn-at-a-root-the-operator-may-name-outside-the-corpus-root-and-the-seed-folder.md) §1's *"Named, the root is the tree itself"* and §3's refusal of *"a folder that is not empty and holds no marker"*, neither of which holds where the folder named is `<working directory>/deliverable` itself, which is read as no root named (§1). It carries a note at its head. [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) amends ADR-276 §1 to §3 too, in other clauses; read the two together.
- **Amends**: [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md)'s *"An occurrence's path is stored relative to the corpus root"* and *"Relative paths survive re-siting"*: an occurrence found below a code set's folder outside the corpus root is spelt by that folder's real path, absolute (§5.4). It carries a note at its head.
- **Amends**: ADR-054's *"A corpus is identified by its root path alone"*: a walk of the corpus root also reads the code set folders the profile names, recorded with the walk, and the corpus root stays the path a walk is found by (§5.3). It carries a note at its head.
- **Amends**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §2, §7, §8 and §9: a code set's folder outside the corpus root anchors a set as the first folder under the corpus root does, and is spelt absolute in `code_set`, `code_set_member` and `code-set-names.yaml` (§5.5). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §2's *"three files of a working directory and no other"*, which gains `not-relevant-folders.yaml`, and *"A write is one action"*, one action writing that list and the answers it gives (§6); §5's table, which gains `codeSetFolders` (§5.1); §6, whose answers the control panel gives by folder (§6); and §7, whose folder may be spelt absolute (§5.5). It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §2, whose `tail` keeps a fixed height and shows its newest line (§10); §5, whose preflight row `docling` is `bad` where the converter runs another image than Run would use, beside a button offering the one it runs (§2); and §8: `next-invocation` says why it suggests nothing (§3); `/labels` is §6's list in place of a select for each question; `/code-sets` gains §5.2's form; and `/profile` is §7's one form, every key in it. It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose file gains `vespera.label.model` (§2); §11, whose image check compares with the image the jar ships where neither a launch value nor the file names one, and offers the image the converter runs (§2); and §5's *"`/profile` keeps ADR-331 §3's list and its one form for each value"* and *"a key the next invocation wants stands on `/next` and not here"*, `/profile` being one form of every key with one Save and one Reset (§7). Its §2's rule that a value the jar ships stays out of a field stands on every page, the launch page's services card among them (§2). It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §3, whose style gains `#tail`'s height (§10); §5, whose closed list gains two fields, whose `profile-seedFolder` row names the form `profile` and the field `seedFolder`, and whose code set's folder may also be absolute at or below a folder of `codeSetFolders` (§5.2, §6, §7); §7, whose `relevant-<i>` row goes, whose language select gains an empty choice on `/profile`, and whose `docling-images` lists the GPU image first, preselected where no image is named (§6, §7, §11); and §8, whose placeholder is `in use: ` and the value where the page knows the value in use, whose Output folder row loses its example, and whose `relevant-<i>` row goes (§1, §2, §6). It carries a note at its head.

## Context

**The default location named.** ADR-276 §1 draws an unnamed tree at `<working directory>/deliverable/<generation run>/`, and a named root is the tree itself. `<working directory>/deliverable` holds a folder for each generation run, each with its marker, and no marker at its own top. So naming it in `vespera.deliverable-root`, which is where the trees are when nothing is named, makes `DeliverableRoot.refusalAtTheStart` refuse it as *"not empty and holds no tree of this working directory"*. The Output folder field's example is `D:\vespera\deliverable` and its hint *"Leave it empty to keep them in the working directory"*, both of which read as a parent of trees.

**What the services card shows.** The four fields, the Docling image, the two addresses and the label model, show an example when empty. `LaunchPages.namedOrSaid` already reads what `effective-values.properties` says of the image and the two addresses, to ask the sidecars, and the page shows none of it beside the fields. The file says nothing of the label model, which `OllamaRelevanceLabeller` reads as `vespera.label.model`, else `spring.ai.ollama.chat.options.model`, else `qwen3:8b`.

**What the next page says.** It reads `next-invocation.properties` only once the control panel's own child has ended with 0 (ADR-294 §4). Before that, after a child that did not, and where the file is not there, it says *"The next act is not known: no invocation of the control panel's has ended with 0"*.

**Where a code set may lie.** ADR-284 finds sets among the walk's occurrences, every one below the corpus root, a set's folder being at most the first folder under it. The code-sets page names a set by its folder from the corpus root, and the folder page and Save refuse any other (ADR-331 §5). Code an operator keeps beside the archive, a checkout of a program the documents describe, cannot be held in a set.

**How the questions are answered.** `/labels` draws a select of three answers for each document of the sample in `relevance-labels.yaml`, sixty as Vespera ships. A sample is drawn again by every scoring run (ADR-330 §9), and its documents lie in the corpus's folders, most of which hold documentation and some of which hold what is not.

**What the profile page is.** A form for every key the next page does not hold, each with its own Save (ADR-330 §5). A refused save holds what was typed until it is written or typed back (ADR-331 §5), and nothing on the page lets go of it otherwise.

## Decision

### 1. The output folder: the default shown, and the default named read as none named

**`DeliverableRoot` reads a named root whose real path is the real path of `<working directory>/deliverable` as `UNNAMED`.** Real paths and names are compared as ADR-276 §2 compares them: a path that exists by its real path, one that does not by the real path of the nearest folder above it with the names below appended, name by name, without regard to case on Windows. So `treeOf(workingDirectory, run)` is `<working directory>/deliverable/<run>`, `refusalAtTheStart` asks of it only ADR-276 §2's comparisons with the corpus root and the seed folder, as of the unnamed root, and `refusalAtTheDrawing` never refuses it. The rule is in `treeOf`, `refusalAtTheStart` and `refusalAtTheDrawing`, each of which is given the working directory, so `DeliverableRoot.of(String)` and `named(Path)` stay as they are, and the closing line and the drawing ask `treeOf` as before. A folder below it, `<working directory>/deliverable/mine`, is a named root as before.

**The Output folder field shows the default in use.** It carries no example. Its `placeholder` is `in use: ` and the folder each run's tree is drawn in where it is left empty: `<working directory>`, absolute and normalised, then `deliverable`, then `<run id>`, joined by the system's separator, and `<working directory>` written as those words where none is named. Beside it, `in-use-vespera.deliverable-root` carries `data-value` and `data-source`:

| The field holds | `data-value` | `data-source` |
| --- | --- | --- |
| nothing, or the working directory's `deliverable` folder as the rule above reads it | the working directory's `deliverable` folder, absolute and normalised, empty where no working directory is named | `default` |
| any other folder | that folder, as the field holds it | `launch-value` |

Its words say where each run's tree goes, and its hint is *"Leave it empty to put each run's pages in a folder of their own under the working directory, as shown; a folder named here holds one run's pages, so name an empty one."*

**The control panel reads the default folder named as none named too.** Whether a Confluence rendering is drawn (ADR-308 §6) is asked, where the Output folder names the working directory's `deliverable` folder, of each run's folder under it, as where it names none, and not of the folder itself.

### 2. An empty field of the services card shows the value that will be used

**Each of the four fields of the services card, `vespera.docling.image`, `vespera.docling.base-url`, `spring.ai.ollama.base-url` and `vespera.label.model`, where it is empty and `effective-values.properties` names its key, carries the `placeholder` `in use: <value>`**, the file's value; where the file is absent or does not name it, ADR-331 §8's example as before. The field stays empty: the value is the jar's, and an empty field passes nothing, as ADR-330 §2 has it of a value the jar ships. The Docling image is the one exception, never empty on the page, since §11 preselects it, so of it this paragraph and the table below hold only of a value typed.

Beside each, `in-use-<name>` carries `data-value`, `data-source` and, where the value is the file's, `data-fresh`, as ADR-330 §2 has them on `/profile`:

| The field holds | The file | `data-value` | `data-source` |
| --- | --- | --- | --- |
| a value | any | that value | `launch-value` |
| nothing | names the key | the file's | the file's `.source` |
| nothing | absent, or does not name it | empty | `not-known` |

Its words are *"In use: <value>, as typed here."*, *"In use: <value>, from <source>."*, with ADR-330 §2's sentence on a stale file, or *"The jar has not said yet what it would use; run an invocation to see it."*

**`effective-values.properties` gains `vespera.label.model`**, resolved as `OllamaRelevanceLabeller` resolves it: `vespera.label.model` where configuration names it, else `spring.ai.ollama.chat.options.model`, each with the source of the layer that named it, else `qwen3:8b` with the source `code`. It is always written, as the two addresses are. A model's name is no secret and no path, so ADR-330 §1's closed list stays closed to anything else.

**An image the converter runs that is not the one Run would use is said before Run, and offered.** The image Run would use is the field's: the launch value where one is named, else the GPU image §11 preselects, which is what the child is then given. So `docling-check` compares the image the sidecar says it runs with that one in every case, and is `not-checked` only where the sidecar says nothing of its image; ADR-330 §11's comparison with what `effective-values.properties` says of the image, and its `not-checked` where neither names one, no longer hold, the file's image being what the jar would use with no launch value, which the control panel no longer leaves it. Where the two differ:

- **the preflight's row `docling` is `bad`**, not `warn`, and says *"Docling: the converter runs <running>, and Run would use <in use>; extraction refuses a converter running another image."*, naming both whole; the warning beside Run that names both stays;
- **the launch form holds a submit button `use-running-image`**, *"Use the image the converter runs"*, which holds the launch values with `vespera.docling.image` set to the image the sidecar runs, starts nothing, writes no file, and answers `303` to `/`, where the field then holds it and the check is `matches`. Run then passes it, as any launch value (ADR-292).

Where they match, or the sidecar says nothing of its image, the row stays as ADR-302 §5 has it and the button is not drawn. Nothing changes the image by itself: a launch value is what the operator typed or confirmed, and the button is the confirmation.

### 3. The next page says why it suggests nothing

`next-invocation` keeps `data-invocation`, `unknown` in each case below, and gains `data-why`. Its words are these, whole:

| When | `data-why` | Words |
| --- | --- | --- |
| no child the control panel started has ended since it started | `none-ended` | `No run has finished yet, so there is no next step to suggest. Start a run from the Launch page; once it finishes, this page shows what it needs next.` |
| the last child ended other than with 0, or was stopped | `ended-badly` | `The last run did not finish, so there is no next step to suggest. The Invocation page says how it ended; start it again from the Launch page once that is put right.` |
| the last child ended with 0 and `next-invocation.properties` is not there | `said-nothing` | `The last run finished without saying what it needs next, so there is no next step to suggest.` |

A file not in ADR-294 §1's shape is said as before, by its reason. ADR-294 §4's rule, that the file is read only after the control panel's own child ended with 0, stands.

### 4. Where a profile value, a launch value and a file of the control panel's lie

| What | Where it is kept | Who reads it |
| --- | --- | --- |
| the code set folders outside the corpus root (§5) | the profile's `codeSetFolders`, with its provenance | the census, stage 1, `vespera run`'s start, the control panel |
| a rename of a set, outside the corpus root or inside it (§5.5) | `code-set-names.yaml`, as ADR-284 §9 | the drawing and the control panel |
| the folders not relevant (§6) | `not-relevant-folders.yaml`, new, in the working directory | the control panel alone |

A code set folder is a profile value, as `seedFolder` is, because it changes what a corpus is, so it travels with the corpus's working directory and every run records the profile it read. It is no launch value: a launch value names where things are on one machine and judges nothing about a corpus (ADR-292).

### 5. A code set's folder may lie outside the corpus root

#### 5.1 The profile's `codeSetFolders`

**A new profile key, `codeSetFolders`**, after `clusterResolution` in `Profile`'s components and `ProfileKey`'s order, of a new kind, folders: one string under `value`, as every key's (ADR-061), holding one or more absolute folders separated by the system's path separator, `;` on Windows and `:` elsewhere, as a `PATH` holds them. Each piece is stripped, and an empty piece is passed over. A value holding a piece that is not an absolute path, or one the system cannot hold, is unreadable (ADR-120), and none of it is read. Unset, no folder outside the corpus root is read, as before. Its earliest run is byte-level reduction, every later run following, since a walk that reads other folders is another walk. Its hint, *"which folders outside the corpus root hold code that is read with the corpus"*, its example, `D:\code\billing`, and its line of what it is are the implementer's; its README row, whose second cell is that hint and whose third names no report, is written with this record's tests, as ADR-331 §8 asks of every key.

**`vespera run` checks each folder at its start**, in the profile's order, after the corpus root is known and before the deliverable root's own checks, comparing real paths name by name as ADR-276 §2 does. Each folder must be a folder that is there, and may not be equal to, inside or above the corpus root, the working directory, the seed folder where the profile names one, the deliverable root's folder, named or `<working directory>/deliverable`, or a folder listed before it. The first refusal is one line on standard error, exit 1, and nothing is walked:

- `vespera run refused the code set folder <folder>: it is no folder that is there. Name a folder that is there in codeSetFolders.`
- `vespera run refused the code set folder <folder>: it is <relation> <other>. Name a folder outside the corpus root, the working directory, the seed folder, the deliverable root and the other code set folders in codeSetFolders.`

`<relation>` is, for each of `the corpus root`, `the working directory`, `the seed folder`, `the deliverable root` and `the code set folder`, the thing itself, `inside` it or `above` it, compared in that order and, for each, equal, then inside, then above, as ADR-276 §2 orders its own. `<folder>` and `<other>` are the paths as compared. A folder inside the corpus root is refused so: the corpus root's walk reads it already, and a set below it is named from the corpus root (§5.5). `vespera label` and `vespera publish` walk nothing and check nothing of it.

#### 5.2 The code-sets page

`/code-sets` keeps the form `code-sets` as it is and gains, below it, **the form `code-set-folders`**, `POST` to `/code-set-folders`:

- its heading, *Folders outside the corpus root*;
- `code-set-folders`, a list holding, for each folder the profile's `codeSetFolders` states, in its order, an item carrying `data-folder`, the folder as stated, a hidden `folder-<i>` and a submit button `remove-<i>`;
- `new-code-set-folder`, labelled *Folder outside the corpus root*, example `D:\code\billing`, hint *"A folder of code anywhere on this machine, read with the corpus and kept as code sets of its own."*, with the Browse button `browse-code-set-folder`;
- `codeSetFolders.provenance`, ADR-330 §2's provenance of the key, holding what the file states;
- `cost-codeSetFolders`, what a change of the key costs, as `/profile` draws it for every key (ADR-295 §5, ADR-330 §9);
- the submit button `save`, which adds the new folder.

**A save or a remove is one write of `profile.yaml`**, of the key `codeSetFolders`, by `ProfileEdit` as any value: the folders stated with the new one appended, or without the one removed, joined by the system's path separator, with the provenance posted, and ADR-330 §5's checks, a value changed under an unchanged provenance among them. The folder added is the field's value, its trailing white space lost (ADR-331 §6), and is checked first, in the order of §5.1's start, against the corpus root, the working directory, the seed folder, the output folder and the folders already stated. A refusal answers one line in `outcome`, writes nothing and keeps what was typed:

- `no folder is added: none is typed in the field`, where `save` is pressed with the field empty;
- `the folder <folder> is not added: it is not the whole path of a folder`, where it is not absolute;
- `the folder <folder> is not added: there is no such folder`;
- `the folder <folder> is not added: it is <relation> <other>`, `<relation>` as §5.1 has it, `the output folder` in place of `the deliverable root`.

**The folder page for `code-set-folder`** lists the system's roots and its folders as for `vespera.corpus-root`, from any folder, and its `use-folder` puts the absolute path in `new-code-set-folder` and returns to `/code-sets`. ADR-331 §5's closed list gains this row:

| `for` | The form, its path | Its field | The page returned to |
| --- | --- | --- | --- |
| `code-set-folder` | `code-set-folders`, `/code-set-folders` | `new-code-set-folder` | `/code-sets` |

#### 5.3 The walk

**The census walks the corpus root, then each code set folder in the profile's order, under the one walk id of the corpus root.** The walk row's `root` stays the corpus root's real path, by which every later stage finds the walk (ADR-051, ADR-115). The folders a walk reads are recorded with it, in `walk_folder (walk_id INTEGER NOT NULL REFERENCES walk (id), ordinal INTEGER NOT NULL, folder TEXT NOT NULL, PRIMARY KEY (walk_id, ordinal))`, owned by `ledger`, additive, built empty at start-up on a database written before it, with no schema version moving (ADR-197 §3); its primary key leads with `walk_id`, so ADR-173 asks no other index. `folder` is the folder's real path, as `Walk.canonicalRoot` reads a root.

- **An unfinished walk is continued with the folders it recorded**, whatever the profile now names (ADR-055); a list changed since applies from the next walk. A checkpoint names the folder it lies in by its ordinal, `0` for the corpus root.
- **Every entry is accounted for**, the folders' entries among the walk's counts, and ADR-056's reconciliation is of the whole.
- **ADR-115's comparison of a walk with the one before it** compares their occurrences, the folders' among them, so a walk reading another list of folders is never taken for a repetition.
- **The seed folder is walked under its own walk id**, as ADR-064 has it.

#### 5.4 How an occurrence below a code set folder is spelt

**Its path is the code set folder's real path, its separators written `/`, then `/` and the path below it as traversal spells it**, ADR-051's rule for the rest: `D:/code/billing/src/Billing.java` on Windows, `/srv/code/billing/src/Billing.java` elsewhere, `//server/share/billing/src/Billing.java` for a share. Such a path is absolute, and a path below the corpus root never is: on Windows no name traversal finds holds `:`, and no relative path begins with `/`. So `UNIQUE (walk_id, path)` holds whatever the corpus holds, and **a file is read at its path where its path is absolute, and at the corpus root joined to its path otherwise**. A walk anomaly found there is spelt the same way. Re-siting such a folder makes its occurrences others, as a walk of another list does.

#### 5.5 Stage 1 and the drawing

**A code set folder anchors a set as the first folder under the corpus root does** (ADR-284 §2): for an occurrence below it, the set folder is the nearest folder holding a build file from the occurrence's own folder up to the code set folder, both included, else the code set folder. A file lying directly in it lies in its set, and it is never the root's set. Its extent, its members and their roles are ADR-284 §3 to §5's; a document in it is no member and is judged as any document is; a log, a broken file or a file out of scope keeps its verdict. Where it holds no file that is code, no set is found in it and its documents are the corpus's like any other.

- **`code_set.folder` and `code_set_member.folder`** hold such a set's folder as its occurrences' paths spell it, absolute, `/`-separated (§5.4). ADR-284 §6's reason reads *"a build file of the code set at D:/code/billing"*.
- **`code_set.default_name`** is the last element of that folder, `billing`, as ADR-284 §8 names any set's.
- **`code-set-names.yaml`'s `folder`** is relative to the corpus root as before, or, for a set below a code set folder, absolute as `code_set` spells it: beginning with `/`, with a drive letter, a colon and `/`, or with `//`, holding no `\`, no empty element but the leading `//`, and not ending with `/`. `CodeSetNames` and `CodeSetRenames` read and refuse both by that shape, and an entry naming a folder no set has stays dormant (ADR-284 §9). **The form `code-sets`' `new-folder` takes an absolute folder spelt so where it lies at or below a folder `codeSetFolders` states**, and refuses it by ADR-331 §5's line as climbing out otherwise.
- **The drawing copies such a set whole under `code/`**, its paths below its set's folder kept, as ADR-296 copies any set, each file read at its path (§5.4).

### 6. Relevance is answered by a list of folders not relevant

**`/labels` holds the form `not-relevant`**, `POST` to `/labels`, in place of a select for each question:

- `not-relevant-folders`, a list holding, for each folder of `not-relevant-folders.yaml` in its order, an item carrying `data-folder`, a hidden `folder-<i>` and a submit button `remove-<i>`; **empty at first**, where the file is not there;
- `new-not-relevant-folder`, labelled *Folder, from the corpus root*, example `archive/old-flows`, hint *"A folder of what you are not keeping: every question about a file below it is answered not relevant."*, with the Browse button `browse-not-relevant-folder`;
- the submit buttons `save` and `save-then-label`.

Above it, `questions` carries `data-questions`, the number of entries `relevance-labels.yaml` holds, `0` where it is not there, and `data-not-relevant`, how many of their paths lie at or below a folder listed. Its words are `<n> questions, <k> of them in a folder not relevant.`, with `question` where `<n>` is 1. The page title and the sidebar's name of the page are *Relevance*.

**`not-relevant-folders.yaml`**, in the working directory, UTF-8, written whole by ADR-295 §2's `.part` and move:

```
---
- folder: "archive/old-flows"
- folder: "D:/code/billing/docs"
```

and `--- []` for none, each scalar double-quoted as ADR-295 §3 writes one, and read back only in that shape, plain or single-quoted by hand as well. A folder is written as occurrences' paths spell a folder: relative to the corpus root, `/` between its elements, none empty, no `\`, no `..` element, neither beginning nor ending with `/`; or absolute at or below a folder `codeSetFolders` states, spelt as §5.4 spells one. The corpus root itself is not one, however it is spelt, `.`, `./`, `.\` or its whole path, with either separator and with one after it: it holds every question. A folder is given once, compared with those listed as a question's path is compared with it, so on Windows a folder listed in another case is listed already. No stage reads the file and the jar never writes it; it keys no run.

**A save, a remove and a save-then-label are one action**, under one hold of the working-directory lock (ADR-295 §1): both files are read and checked first, then `not-relevant-folders.yaml` is written with the folder added to, or the one removed from, the list the file holds as read then, and not the list the page drew, so a page drawn before another tab saved loses nothing that tab added, then, where `relevance-labels.yaml` is there, **each of its questions is answered: `false` where its path lies at or below a folder listed, compared element by element, without regard to case on Windows, and `true` otherwise.** Only a `relevant` line whose answer changes is rewritten, as ADR-295 §6 has it, so a file whose answers already agree is written byte for byte as it stood. A refusal of either file, a folder the rules above refuse among them, writes neither, with one line in `outcome`, and keeps what was typed. **So does a write that fails part way**: the two files are one write, all or none, so where the second cannot be written once the first is moved over its target, the first is put back as it was read, or removed where it was not there, before the line is said:

- `the folder <folder> is not added: it is not a folder below the corpus root, written with / between its elements`;
- `the folder <folder> is not added: the corpus root holds every question; name a folder below it`;
- `the folder <folder> is not added: it is listed already`;
- ADR-295 §2's and §6's lines for a file in another form;
- `the control panel could not write <file>: <reason>, so neither file is changed`, `<file>` being the one whose write failed, ADR-295 §2's line with the words after the reason added; where the file moved first cannot be put back either, `the control panel could not write <file>: <reason>, and could not put <other> back as it stood: <reason>`.

The outcome of a save is `the folders not relevant are saved in <file>, and the <n> questions answered: <k> not relevant, <n - k> relevant`, or, where the label file is not there, `the folders not relevant are saved in <file>; there are no questions to answer yet`. `save-then-label` then starts `vespera label` as ADR-302 §8 has it.

**The folder page for `not-relevant-folder`** lists folders from the corpus root down, relative, as for `new-folder` (ADR-331 §5), and its `use-folder` puts the folder in `new-not-relevant-folder` and returns to `/labels`:

| `for` | The form, its path | Its field | The page returned to |
| --- | --- | --- | --- |
| `not-relevant-folder` | `not-relevant`, `/labels` | `new-not-relevant-folder` | `/labels` |

**What it answers, and what it does not.** Each answer is a person's, given through the page, and `vespera label` records it so, over a model's (ADR-197). The answers are re-given from the list at every save, so a sample a later scoring run draws is answered by pressing Save again. **Relevant means documentation**: an operational record (`CONTEXT.md`) lying in a folder not listed is answered relevant, so its folder belongs on the list, or its name's ending in `outOfScopeNameEndings` (ADR-293). A document of a folder listed is answered not relevant whatever it says. `vespera label --auto`, which answers document by document, stays as it is, and its answers are written over by the next save of this page, as a person's answer is over a model's.

### 7. The profile page is one form

**`/profile`, where the file is in the form ADR-295 §3 reads, holds `#profile-list` and one form, `profile`, `POST` to `/profile`, holding every `ProfileKey` in its order**, a key the next page wants among them. A key's section `key-<key>` holds, as ADR-330 §2 to §4 have them, its heading, `about-<key>`, `in-use-<key>`, `current-<key>`, `measurement-<key>`, its cost and `report-<key>`, and its controls:

| Control | Name | Holds |
| --- | --- | --- |
| hidden | `<key>.drawn` | ADR-330 §5's digest of what the page drew |
| the key's own, ADR-331 §7's | `<key>` | what the file states |
| text | `<key>.provenance` | the provenance the file states, not `required` |

`seedFolder`'s section holds the Browse button `browse-profile-seedFolder`. The form's last two buttons are **`save` and `reset`**. **The language's select on `/profile` gains a first choice, the empty value, `not set`, where the file states no language**, selected, so that a save of the form leaves the key unset. ADR-331 §5's row for `profile-seedFolder` names the form `profile` and the field `seedFolder`.

**Save** compares each key's posted value and provenance with its `<key>.drawn`, as ADR-330 §5 compares one:

- **No key changed**: nothing is written, no lock taken, the held form let go, and `outcome` says `nothing was written: every value is as profile.yaml states it`. It answers `303` to `/profile`.
- **Keys changed**: each is checked by ADR-330 §5's three rules, in `ProfileKey`'s order, before anything is written. **The first refused refuses the whole save**: nothing is written, `outcome` holds its `<key> is not saved: <reason>`, the whole form is held as posted, and it answers `303` to `/profile#key-<key>`. Otherwise **every change is written in one write of the file**, under one hold of the lock, each key as ADR-295 §3 rewrites one, every other byte kept; the form is let go, `outcome` is `<key>, <key> saved in <file>`, the keys in their order, and it answers `303` to `/profile#key-<the first key written>`.

A provenance left empty beside a value changed is refused by ADR-295 §4's rule, so the browser need not stop the form for one: the provenance is not `required`, a key left alone needing none. Its hint reads *"Needed for each value you change: why you chose it, in your words. It is written beside it in profile.yaml."*

**Reset** writes nothing, takes no lock, lets go of everything the form held, typed, refused or chosen by Browse, and answers `303` to `/profile`, which draws every control from `profile.yaml` as it stands, which is the file as last saved. `outcome` says `the form is back to profile.yaml as last saved`. No copy of the file is kept for it, and nothing is undone that a save wrote.

**A file not in the form** is shown and repaired as ADR-330 §6 has it, with no form `profile`. `/next` keeps its own form as ADR-302 §8 and ADR-330 §5 have it, so a key it asks for is on both pages, each saving it by the same rules.

### 8. Which run ids move

| Change | Folder | Moves |
| --- | --- | --- |
| §1, in `DeliverableRoot` | `deliverable` | none: no stage names it (ADR-274 §5) |
| §2's line of `effective-values.properties`, §5.1's check at the start | `pipeline` | none (ADR-222) |
| §5.1's key, in `Profile` | `profile` | none by itself (ADR-274 §5); a profile naming a folder walks another walk, so every stage's run over it is new |
| §5.3's walk and `walk_folder`, §5.4's spelling | `corpus`, `ledger` | stage 1, its version naming `corpus`, and every later stage following it, each naming the run upstream of it (ADR-089) |
| §5.5's set pass and `CodeSetNames` | `corpus`, `deliverable` | stage 1, in the same change as the row above |
| §3, §5.2, §6, §7 | `io.algernon.controlpanel` | none: it is no module |

### 9. What the implementation owes

- **In `deliverable`**: §1 in `DeliverableRoot`'s three methods, and its javadoc; §5.5's shape in `CodeSetNames`.
- **In `pipeline`**: §2's line in `EffectiveValuesFile`; §5.1's start check, beside `DeliverableRoot.refusalAtTheStart`'s call in `VesperaCommand.Run`; `CensusTasklet` handing the walk the folders, and `ByteLevelReductionTasklet` handing the set pass the folders of its walk.
- **In `corpus`**: §5.3 in `WalkRecorder` and `Walk`, the folders' entries walked after the corpus root's and the checkpoint naming its folder; §5.4's spelling; §5.5 in `CodeSets`, reading a file at its path where it is absolute.
- **In `ledger`**: `walk_folder` in `schema.sql`, under its owner's line, and `Walks` writing and reading it.
- **In `profile`**: `codeSetFolders` and its value type; `ProfileFixture`'s builder method for it.
- **In `io.algernon.controlpanel`**: §1 and §2 in `LaunchPages`, and §1 in `ControlPanelState.aRenderingIsDrawn`; §11's GPU image posted back named as none in `LaunchPages` and `ControlPanelState`; §6's write of two files, all or none, in `WorkingDirectoryWrite`; §3 in `NextPages`; §5.2 in `ValuePages` and `FolderPages`, the `Target` `CODE_SET_FOLDER`; `ProfileKey.CODE_SET_FOLDERS` and its kind; §5.5's `new-folder` rule; §6 in `ValuePages`, `FolderPages`' `Target` `NOT_RELEVANT_FOLDER` and a class of its own for `not-relevant-folders.yaml`, `WorkingDirectoryWrite` admitting it; §7 in `ValuePages`, `ControlPanelPage.keyControl`'s empty choice on `/profile` and `FolderPages.Target.PROFILE_SEED_FOLDER`; the sidebar's *Relevance*; §2's image row and `use-running-image` in `LaunchPages` and `SidecarChecks`; §10's rule in `ControlPanelPage`'s style and its script.
- **Documents**, written with this record's tests rather than by the implementation (§5.1): README's table of values gains `codeSetFolders`'s row and "Where things live" names `not-relevant-folders.yaml`; `docs/architecture.md` §1's census and stage 1 say a code set folder outside the corpus root is walked with it.

### 10. The invocation page's Output box keeps its height and shows the newest line

**`#tail` on `/invocation` is a fixed share of the window**: the page's style holds the rule `#tail { height: 45vh; overflow-y: auto; }`, after ADR-331 §3's rule for `pre`, so the box neither grows with the child's output nor pushes the rest of the page down, and its lines scroll inside it. 45 of each 100 of the window's height leaves the page's head, the Stop button and the last line in view on a window of 700 pixels.

**After every load it shows the newest line at the bottom**, `scrollTop` set to `scrollHeight`, unless the operator has scrolled up since. This is in the control panel's one script, `/control-panel.js` (ADR-325 §4), which no page gains a second of and which writes nothing into a page but the box's scroll:

- On a `scroll` of `#tail`, the script keeps in `sessionStorage`, under `vespera-tail-scroll`, the box's `scrollTop` where the operator has left the bottom, `scrollTop + clientHeight` short of `scrollHeight` by more than 2 pixels, and removes the key where they have come back to it.
- On every load of a page holding `#tail`, where the key is there the box is scrolled to the `scrollTop` it holds, so the operator keeps their place across ADR-302 §2's reloads every `REFRESH_SECONDS`; where it is not, to `scrollHeight`.
- `sessionStorage` is the tab's own and outlives a reload and no tab, so a place kept is of that tab alone and is gone with it, and scrolling back to the bottom follows the newest line again. Nothing of it reaches the server, a file or a cookie (ADR-325 §2).

The box's words are the child's output as before (ADR-302 §2), and no `id` or name changes.

### 11. The control panel preselects the GPU image

**Where no launch value names a Docling image, the control panel reads the image as `ControlPanelPage.DOCLING_IMAGE_GPU`**, `compose.gpu.yaml`'s, everywhere it reads the launch values: the launch form's `vespera.docling.image` holds it as its value, the check of §2 and its warnings compare with it, and every start, `run`, `label`, `save-then-run`, `label --auto` and `publish`, passes it as `VESPERA_DOCLING_IMAGE` (ADR-292 §5). **The GPU image posted back names no image.** The launch form posts what its field holds whichever of its buttons is pressed, Run, Label, Check, a Browse or `use-running-image`, so where no launch value names an image and the field posts the GPU image, the control panel holds the launch values, and stores them as a child starts, with no image named, as they were before the post: the field, the checks and every start still read the GPU image, and the child is given it. **`docling-images` lists the GPU image first**, `<option label="GPU">`, then the CPU image, `<option label="CPU">`, which stays a choice the operator may pick or type, and is stored and passed as typed. `in-use-vespera.docling.image` then carries the GPU image and `data-source` `preselected`, its words *"In use: <image>, chosen by the control panel; pick the other if this machine has no graphics card."*, and `launch-value` once the operator has named one: an image other than the GPU image, or the GPU image posted where a launch value names an image already.

**The jar's own default does not change**: `application.yaml`'s `vespera.docling.image` stays the processor build, which `compose.yaml` runs, CI tests against and a machine with no card needs (ADR-330 §11, ADR-147). Only a child the control panel starts is given the GPU image, through its environment; `vespera` started by hand runs the processor build unless told otherwise.

**§2's check still stands over it**: where the converter runs another image than the field's, the processor build among them, the preflight's row is `bad` naming both, and `use-running-image` offers the one it runs. So on a machine running the processor build the first page says so before Run, and one press puts that image in the field.

`LaunchValues.warningsToRun`'s line that no Docling image is named is not said by a page, the page's values holding the GPU image in its place; the type keeps it for a value read with no page.

## Known limits

- An operational record in a folder not listed is answered relevant (§6): the list answers by place, and the control panel reads no file.
- A sample's questions are answered from the list only when the page is saved: a label file a later run writes keeps the answers it carried over (ADR-169) until then.
- A code set folder re-sited, or reached through another drive letter, is another folder: its occurrences are spelt by its real path (§5.4).
- A code set folder holding the working directory, the seed folder or the deliverable root is refused at the start of `vespera run` and on the code-sets page; a folder of the archive outside every folder Vespera is told of is not seen, as ADR-276 §2 says of the deliverable root.
- `in use: ` on the services card is the jar's last word, as ADR-330 §1 has it: a machine's configuration changed since says otherwise only once an invocation has written the file again, the file being marked stale only against the jar.
- Reset discards what the form held and no more: a value a save wrote stands, and the page keeps no earlier file to put back.
- The GPU image picked from the list where no image is named names none (§11): it is the preselection, read as such until another image is named.

## Alternatives refused

- **Refusing the default folder named, with a line saying to leave the field empty.** The operator named where the trees already are; refusing it sends them to find out why a folder that holds every tree holds none.
- **Filling an empty service field with the value in use.** A field filled is a launch value posted, passed to the child and remembered (ADR-292), so a value the operator never typed would come to override the jar's.
- **A code set folder walked under a walk of its own, as the seed folder is.** Every stage reads one walk's occurrences and every run names one walk, so a second walk would be seen by no stage until each learned to join two.
- **A code set folder as a launch value.** It would be lost with the user's launch values and passed to no `vespera run` started by hand, and a corpus's code would differ by who started it.
- **An occurrence below a code set folder spelt relative under a made-up first element.** No printable name is ruled out of a POSIX folder's, so it could meet a real folder of the corpus.
- **The folders not relevant written into the label file.** Its form is the jar's (ADR-295 §6), and a new sample would lose them.
- **The relevant answer left blank below no folder listed.** The operator's rule is that every folder is relevant until listed, and a blank takes nothing back (ADR-169), so a sample answered so would never set a floor.
- **Reset as an HTML reset button.** It puts a form back to what the page drew, which holds what a refused save kept, and not to the file.

## Consequences

- The default output folder can be named, typed or chosen by Browse, and the trees go where they went.
- An operator sees beside each launch field what an empty one stands for, before the first invocation says otherwise and after.
- The next page says what to do when it has nothing to suggest.
- Code kept beside the archive is read with it, kept apart as code sets and copied whole into the deliverable, and its documents are judged as the corpus's are.
- Sixty questions are answered by naming the folders that hold what is not documentation, and answered again by one Save for every sample a later run draws.
- The profile is edited as one form, and a slip is undone by Reset.

## Tests

| Method | What it holds |
| --- | --- |
| `deliverable.NamingTheWorkingDirectorysDeliverableFolderNamesNoRootTest.theDefaultFolderNamedDrawsWhereNoneNamedDraws` | named, `<working directory>/deliverable` holding two runs' trees: not refused at the start or at the drawing, and each run's tree in its own folder, as `UNNAMED`'s (§1) |
| `deliverable.NamingTheWorkingDirectorysDeliverableFolderNamesNoRootTest.theDefaultFolderSpeltAnotherWayIsTheSame` | with a separator after it, by a `..`, and not made yet (§1) |
| `deliverable.NamingTheWorkingDirectorysDeliverableFolderNamesNoRootTest.onWindowsTheDefaultFolderInAnotherCaseIsTheSame` | the same in capitals, on Windows (§1) |
| `deliverable.NamingTheWorkingDirectorysDeliverableFolderNamesNoRootTest.aFolderBelowTheDefaultFolderIsANamedRoot` | the bound of the rule: a folder below it is the tree itself (§1) |
| `controlpanel.TheLaunchPageShowsTheValuesInUseScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | §1's placeholder and `in-use-vespera.deliverable-root`; §2's placeholders and `in-use-<name>` for the four, typed, said by the file and not said (§1, §2) |
| `controlpanel.TheImageTheConverterRunsIsOfferedScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | no image named and no file, the converter running the processor build: `another-image` against the GPU image preselected, the row `docling` `bad` naming both, `use-running-image` drawn, and pressed, the field holding the running image, `matches`, nothing started or written; the GPU image running: no button, row not `bad` (§2, §11) |
| `controlpanel.LaunchImageScreenTest.withNoFileTheSidecarsImageIsStillShown` (removed with the control panel's server and pages, ADR-342 §6, #700) | with no file and no image named, the sidecar's image is checked against the GPU image the field holds (§2, §11) |
| `pipeline.TheControlPanelReadsTheEffectiveValuesTheJarWritesTest` | the file's `vespera.label.model` in each shape, read as the jar writes it (§2) |
| `controlpanel.TheNextPageSaysWhyItSuggestsNothingScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | `data-why` and the words, in each of the three cases (§3) |
| `controlpanel.CodeSetFoldersOutsideTheCorpusRootScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | the form `code-set-folders`; a folder added and removed in `profile.yaml`; the refusals; the folder page from any folder; an outside set renamed (§5.2, §5.5) |
| `pipeline.ACodeSetFolderOutsideTheCorpusRootIsWalkedWithTheCorpusTest` | one walk holding the corpus's occurrences and the folder's, spelt absolute; stage 1's set at the folder, named for it, with its build file and its code (§5.3 to §5.5) |
| `pipeline.ACodeSetFolderOverlappingIsRefusedInvocationTest` | §5.1's lines, exit 1, nothing walked: the working directory, above the corpus root, inside it, the seed folder, inside a folder listed before it, a folder listed twice, the working directory's `deliverable` folder refused as inside the working directory, which is compared first, and no folder there (§5.1) |
| `pipeline.ACodeSetFolderOverlappingTheDeliverableRootIsRefusedInvocationTest` | the deliverable root named, a folder above it, and the code set folders checked before the deliverable root's own checks, its line alone said where both would refuse (§5.1) |
| `corpus.TheCodeSetFoldersAreWalkedUnderTheCorpusRootsWalkTest` | an unfinished walk continued under its id with the folders it recorded, not those it is given now; a checkpoint `@<n>:` stored and read back through the ledger; a walk reading another list of folders, an empty one added included, never a repetition; a walk anomaly below a code set folder spelt absolute (§5.3, §5.4) |
| `deliverable.CodeSetsInTheTreeTest.aSetOutsideTheCorpusRootIsCopiedWholeUnderCode` | a set at a code set folder copied whole under `code/<last element>/`, each file read at its absolute path, its paths below the folder kept (§5.5) |
| `controlpanel.CodeSetRenamesTest.aFolderSpeltAbsoluteIsWrittenAndReadBack`, `pipeline.TheJarReadsWhatTheControlPanelWritesTest.codeSetNamesReadsTheListTheControlPanelWrites` | a set's folder spelt absolute, `/shop`, a drive letter's and `//`'s among them, written by the control panel and read by `CodeSetNames`, and one with a `\`, an empty element or a trailing `/` refused by both (§5.5) |
| `controlpanel.ProfileKeyTest`, `pipeline.TheJarReadsWhatTheControlPanelWritesTest`, `profile.ProfileStoreTest` | `codeSetFolders` after `clusterResolution`, of the kind folders, entering byte-level reduction and drawing the sample again; saved values read by the jar as answered, a relative piece unreadable (§5.1) |
| `controlpanel.WorkingDirectoryWriteTest.onlyTheFourFilesAreWritten` | `not-relevant-folders.yaml` the fourth file the control panel writes, and no other (§6) |
| `controlpanel.TheNotRelevantFoldersScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | the list empty at first and every question answered relevant; a folder added answering below it not relevant; a folder removed; Browse from the corpus root; a folder climbing out refused; no label file; a page drawn before another tab saved adding to and removing from the file's list, losing nothing; the label file's write failing after the list's, neither file changed and the line said; the corpus root refused by every spelling; on Windows a folder in another case listed already (§6) |
| `controlpanel.TheProfileIsOneFormScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | one form of every key, a wanted key among them, with Save and Reset; a save of two keys; a save refused whole and held; Reset back to the file, with no lock; an unset language left unset, and a stated one offered without the choice of none (§7) |
| `controlpanel.TheGpuImageIsPreselectedScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | an empty image launch value: the field holds the GPU image, `docling-images` lists GPU then CPU, and a Run gives the child `VESPERA_DOCLING_IMAGE` as the GPU image, storing no image named; the GPU image posted back by Check or a Browse still `preselected`, and `launch-value` once another image was named; the jar's `application.yaml` still names the processor build (§11) |
| `controlpanel.NativePickersScreenTest.theDoclingImageOffersTheJarsDefaultAndTheGpuComposeFilesImage`, `controlpanel.LaunchImageScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | the list's order, and the checks against the field's image (§2, §11) |
| `controlpanel.TheOutputBoxKeepsItsHeightScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | the invocation page's style holds `#tail`'s rule whole, the page still one script, the control panel's, and that script, as served, scrolls `#tail` to its bottom and keeps a place left in `sessionStorage` under its key (§10); what a browser then does with them is on no test (ADR-297) |
| `controlpanel.ProfileEditorScreenTest`, `ProfileScreenTest`, `TheClusterResolutionEditorScreenTest`, `NativePickersScreenTest`, `TheFolderPageScreenTest`, `FolderFieldStatusScreenTest`, `FolderFieldsKeepALeadingSpaceScreenTest`, `TheControlPanelsLayoutScreenTest`, `FieldHintsScreenTest` (removed with the control panel's server and pages, ADR-342 §6, #700) | what they held of ADR-330 and ADR-331, through the one form `profile`; the closed list of nine; *Relevance*; and `in use: ` beside `e.g. ` (§5.2, §6, §7) |

## What this does not decide

- **Whether the not-relevant list may name a single file.** It names folders; a file is answered by its folder.
- **Whether `vespera label` reads the list itself**, so that a sample is answered without a save of the page.
- **Whether a code set folder may be named by a path relative to the working directory.** It is absolute.
- **Whether the walk of a code set folder can be resumed past a folder the profile no longer lists** other than as §5.3 has it.
