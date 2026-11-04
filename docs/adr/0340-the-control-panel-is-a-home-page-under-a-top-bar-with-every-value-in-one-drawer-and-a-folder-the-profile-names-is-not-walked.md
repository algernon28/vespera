# ADR-340 — The control panel is a home page under a top bar, with every value in one drawer, and a folder the profile names is not walked

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: by the operator, that the control panel takes the layout of one home page under a top bar, with no page menu, every value it edits in one drawer of foldable sections sliding in from the right over it (§1 to §3); that Run, Label and Check stand in that top bar on every page and while it scrolls, with a box *Autolabel* beside them in place of Have the local model answer, Label with it ticked starting `vespera label --auto` (§1); that the page opened at the start shows the progress page's charts, the four tiles, the preflight as a checklist and a foldable box of the reports on its left, and the next step in a box of its own (§2, §4); that what this machine runs with is a foldable box on its right alone (§2); that the list of folders not relevant stays, as a section of the drawer (§3); that every field empty shows the value in use where one exists, the work folder's being the jar's default, and keeps its Browse button and an example where none does (§6); that every field and section is described in plain words, its key beside it (§7); that every folder of the corpus is read unless the operator unticks it in a tree of the corpus's folders, an unticked folder never being read, and that code sets are named in the same tree, a set given no name taking its folder's (§5, §8); and that the profile stays one form with Save and Reset, inside the drawer (§3). The names of the elements, the words beside what the operator gave, whether Autolabel is kept, how the drawer opens without a script, which pages it absorbs and where their addresses land, how the tree is drawn and saved, how the work folder's default is read, and how a folder not walked is recorded and counted are Claude's, under the operator's delegation of the same date.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 and §4: the control panel names `java.base` and `jdk.httpserver` alone, links no class of the jar, and `ControlPanelPage` owns every page's skeleton, style and escaping.
- **Keeps**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §4 and [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §4: every page holds the one script, `/control-panel.js`, which writes nothing into a page; no event attribute and no second script is written. The drawer opens and its sections fold by `:target`, `:has()` and `<details>`, which the browser draws (§3).
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §1, §3, §5 to §7 and §11: no `GET` writes; the reports are re-served as written; the sidecars are asked by `/` alone; the invocation page, `/stop` and its costs stand.
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1 and §2: the launch values are the seven, Autolabel not among them, and their file is written only as a child starts (§1).
- **Keeps**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) and [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §5 and §6: every save is one of ADR-295's writes, under its checks, a changed value needing a provenance, a stale page refused; a file not in the jar's form is shown and repaired, with no form `profile`.
- **Keeps**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6, the list of folders not relevant and the answers it gives, its form, its file and its writes, which move into the drawer unchanged (§3), and its §7, the profile as one form with one Save and one Reset; [ADR-335](0335-the-control-panel-shows-the-progress-page-on-a-page-of-its-own-and-its-pages-are-drawn-to-one-compact-scale.md) §2, §3 and §5: the frame, its policy and the compact scale; [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §2: every argument of the commands is accounted for, `ChildCommand.ARGUMENTS`' reasons changing their words alone.
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): every screen is tested through the pages served, read by names, ids and attributes, and no test renders a page.
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): a page's words are the operator's prose and free of the _Avoid_ lists; the names of ours beneath them are not, so no `id`, class or name of this record is "settings", "dashboard" or "panel" alone.
- **Amends**: ADR-302 §2, whose launch page is §2's home page, and whose form `launch` is split: the top bar's form `launch` holds Run, Label, Check and the box Autolabel and no field, and a post of it naming no launch value keeps the values held; the drawer's form `launch-values` holds the seven fields (§1, §3). Its §4, whose form `seed-folder` goes, the seed folder being a key of the form `profile` (§3). Its §8, whose `/next` is §4's box, whose `/labels` is §3's section, whose `/code-sets` and its form `code-sets` go, the names being marked in §5's tree, and whose `/profile` is §3's section. Its §9, whose table is this record's (§10). It carries a note at its head.
- **Amends**: ADR-292 §5, whose *"Run needs the working directory"* and *"Label needs the working directory"* are no longer said: an empty work folder is the jar's default, which Run, Label and Publish pass (§6). It carries a note at its head.
- **Amends**: ADR-331 §3, whose `#sidebar`, its `nav` and `#top-row` give way to §1's top bar, and whose cards give way to §2's three columns; §5, whose closed list is §3's; §6, whose working directory left empty is §6's default and not `not-chosen`; §8, whose labels and hints give way to §7's and whose examples to §6's rule. It carries a note at its head.
- **Amends**: ADR-334 §1 and §2, whose `in use: ` placeholder holds of every field the page knows a value in use for (§6); §3, whose words name no Launch page (§4); §5.2, whose form `code-set-folders` stands in §5's section, Browse returning there; §5.5's `new-folder`, which goes with the form `code-sets`, a set below a code set folder being named in §5's tree; §6, whose `/labels` is a section of the drawer, its form, file and writes unchanged (§3); and §7, whose form stands in §3's section, every key's section shown. It carries a note at its head.
- **Amends**: ADR-335 §1, whose `/progress` is no page: the home page holds `#progress-view`, never asking to be loaded again (§2); §4, whose `#progress` links `/#progress-view`; and §5, whose sidebar's rule, `#shell`'s columns, goes with the sidebar, the scale's seventeen steps, `--sidebar-width` among them, staying as they are, and whose `main.two-columns` gives way to the home page's columns (§2). It carries a note at its head.
- **Amends**: ADR-330 §2's *"The `placeholder` stays ADR-331 §8's example"* (§6), §4's *"its words are no decision's"*, each key's line being §7's, and §12, whose `machine-values` stands in §2's right column. It carries a note at its head.
- **Amends**: ADR-336 §1: the button `label-auto`, Have the local model answer, gives way to the top bar's box `label-auto`, *Autolabel*, Label with it ticked starting `label --auto` (§1), and the next step's form `label-auto` goes (§4). It carries a note at its head.
- **Amends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md)'s *"A corpus is identified by its root path alone"*, as ADR-334 §5.3 amended it, and [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md)'s *"The walk is accountable for every entry beneath the corpus root it was given"*: a folder the profile's `foldersNotWalked` names is an entry the walk counts and does not enter (§8). Each carries a note at its head.
- **Amends**: [ADR-056](0056-excludes-nothing-is-checked-by-reconciliation-at-finish.md)'s identity, `entriesSeen == occurrences + anomalies + directoriesEntered - 1`, which gains the folders not walked (§8.3); [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md), an unfinished walk being continued with the folders not walked it recorded (§8.3); and [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), a walk given another list being no repetition (§8.3). Each carries a note at its head.

## Context

**The pages as they stand.** Seven pages behind a sidebar (ADR-335 §1): the launch page, with the launch values, the seed folder, the tiles, a preflight, the reports and what this machine runs with as cards; the invocation page; the progress page in a frame; the next page; the relevance page; the code-sets page; and the profile page, one form of every key. A value is edited on the page of its kind, the seed folder on three, and `seedFolder`'s form on the launch page beside the launch values.

**What the operator chose.** Variant B of a throwaway prototype of three: one home page, a bar along its top holding the four starts and a way to the values, the reports in a box on the left, the charts and tiles in the middle, the next step, a checklist of what Run needs and what this machine runs with on the right; and every value in one drawer sliding in from the right over it, its sections folding.

**What a page without a script can do.** A `<details>` element folds and unfolds by the browser alone, and the attribute `open` says how it is drawn. `:target` selects the element the address's fragment names, and `:has(:target)` an element holding it; both are CSS the browser applies, Chromium from version 105 for the second. A form cannot hold another, and a control stands in the form it lies in: ADR-331 refused the `form` attribute, which ADR-297's browser does not read. So the starts, which stand in the top bar on every page, cannot share a form with fields lying in a drawer on one page.

**What the walk does with a folder.** The census walks every entry beneath the corpus root and accounts for each as a file occurrence, a walk anomaly or a directory entered (ADR-056); a code set folder outside the corpus root is walked after it under its walk id, and recorded with it (ADR-334 §5.3). No profile value keeps the walk out of a folder: a folder of what is not documentation is read, converted and judged, and the relevance questions about it are answered by a person through the list of folders not relevant (ADR-334 §6), which removes nothing before stage 5.

**What a corpus's folders are.** Their number is not bounded, and the archive may be on a network share, where listing a folder is a round trip. The folder page lists one folder's subfolders at a time and reads no file (ADR-331 §5).

## Decision

### 1. The top bar, on every page

**Every page of the control panel's own** (ADR-325 §4.1) opens on `#top-bar`, a `header` whose style holds `position: sticky` and `top: 0`, so that it stays at the top of the window as the page scrolls. It holds, in this order:

- the program's name and the page's one `h1`, which the `<title>` follows (ADR-331 §3);
- **the form `launch`**, `POST` to `/launch`, holding **no text field**, three submit buttons, `run`, `label` and `check`, in that order, disabled as ADR-302 §2 has them, and after `label` **a checkbox `label-auto`, *Autolabel***, carrying no `value`, disabled where `label` is;
- `#values-held`, saying the corpus root and the working directory Run would start on, carrying `data-corpus-root` and `data-working-directory`, each as the control panel holds it, empty where none is named;
- three links: `#to-home`, `/`, *Home*; `#to-invocation`, `/invocation`, *Invocation*; `#open-values`, `/#values`, *Settings*. The page's own carries `aria-current="page"`.

No page holds `#sidebar`, `#top-row` or a `nav`. `#outcome` stands right after the top bar, shown once as before.

**A post of `launch` that names none of the seven launch values keeps the values held.** ADR-302 §2's *"Every post of `launch`, whichever button, replaces the values held by the seven posted"* holds of a post naming them, which only the drawer's form `launch-values` sends (§3). So Run and Label start on the values held, by the same checks and lines; `check` asks the sidecars again for them; a refusal answers `303` to `/`, and a start to `/invocation`, as before.

**Label with Autolabel ticked starts `vespera label --auto`**, by ADR-336 §1's checks and lines, `Label` naming it in them; unticked, `vespera label`, as ADR-302 §2 has it. The box is read only by Label: Run and Check ignore it. **It is no launch value and is kept nowhere**: it is drawn unticked on every page, after every post, a start among them, and neither the launch values' file nor memory holds it. A local model's answers then come only from a press that asked for them, which a box left ticked from an earlier start would make without the operator saying so, and `vespera label` records a person's answers beside them otherwise (ADR-197). `ChildCommand.ARGUMENTS`' row of `label`'s `auto` names the control `label-auto` still, its reason saying the box.

### 2. The home page

**`/` is the home page**, its `h1` *Home* and its `<title>` `Home · Vespera`. Under the top bar it holds three columns, each block of it carrying `data-column`, in this order:

| `id` | `data-column` | What it is |
| --- | --- | --- |
| `reports-box` | `left` | a `<details>` drawn `open`, its `summary` *Reports*, holding `#reports` as ADR-302 §3 has it |
| `tiles` | `centre` | ADR-331 §3's four tiles |
| `progress-view` | `centre` | ADR-335 §1's view, its frame and its link, with its states and words |
| `next-step` | `right` | §4's box |
| `ready-to-run` | `right` | a `section`, its `h2` *Ready to run?*, holding `#preflight` (ADR-331 §3), `#warnings`, `#holder-working-directory`, `#holder-corpus-root`, `#free-space` and `#free-space-rules` |
| `machine-values-box` | `right` | a `<details>` drawn closed, its `summary` *What this machine runs with*, holding `#machine-values` as ADR-330 §12 has it |

then the drawer `#values` (§3). What this machine runs with stands in the right column alone: the drawer holds no list of it. A narrow window draws the columns one under another, as ADR-331 §3 has every grid.

**The home page never asks to be loaded again**, whatever `#progress-view` says: it carries no `data-refresh-seconds` and no refresh in `<noscript>`, since it holds every form a reload would lose. Where the working directory holds no progress page yet, `not-written` keeps ADR-335 §1's words, and the frame is shown at the page's next load. The page in the frame reloads itself, as before. **`no-working-directory` is said only of a working directory the system cannot hold**, an empty one being the default (§6), and its words are `No working directory this system can hold is chosen, so there is no progress page to show. Choose one under Settings, and the progress page of what runs there is shown here.`

**`/progress` is no page.** A `GET` or `HEAD` of it answers `303` to `/#progress-view`, and `#progress` on the invocation page links `/#progress-view`, in the same window.

### 3. The drawer of values

**`#values` is an `aside` of the home page alone**, after its columns, holding every value the control panel edits. Its head holds the `h2` *Settings* and `#close-values`, a link to `#top-bar`, *Close*. **It is shown where the address's fragment names it or an element inside it**, by the style's rules `#values:target` and `#values:has(:target)`, and hidden otherwise; how it slides in from the right, over the columns and under the top bar, is the style's. `#open-values` (§1) opens it from every page, landing on the home page.

**Its five sections are `<details>`, in this order:**

| `id` | `summary` | Holds |
| --- | --- | --- |
| `values-folders` | *Folders* | the three folder fields of the form `launch-values` |
| `values-services` | *Services* | the four service fields of `launch-values`, and `use-running-image` where ADR-334 §2 draws it |
| `values-corpus` | *Folders to process and code sets* | the form `folders-to-process` (§5), then the form `code-set-folders` (ADR-334 §5.2) |
| `values-relevance` | *Relevance* | `#questions` and the form `not-relevant`, POST `/labels`, as ADR-334 §6 has them |
| `values-profile` | *Run settings* | the form `profile` (ADR-334 §7), or the repair of a file not in the jar's form (ADR-330 §6) |

Each section's `about-<id>` says in one line what it holds (§7).

**Which section is drawn open.** A `GET` of `/` whose query's `open` names one of the five draws that one `open` and no other; with no `open`, or one naming none of them, `values-folders` alone is drawn open. The operator folds and unfolds the rest by the browser. So **every form of the drawer answers `303` to `/?open=<its section>#<where it lands>`**:

| Form, path | Lands at |
| --- | --- |
| `launch-values`, `/launch`, `check` | `/?open=values-folders#values`; a Browse button lands on the folder page as ADR-331 §5 has it |
| `launch-values`, `/launch`, `use-running-image` | `/?open=values-services#values` |
| `folders-to-process`, `/folders-to-process` | `/?open=values-corpus&tree=<the folder shown>#values-corpus` |
| `code-set-folders`, `/code-set-folders` | `/?open=values-corpus#values-corpus` |
| `not-relevant`, `/labels`, `save`, and a refusal of `save-then-label` | `/?open=values-relevance#values-relevance`; a start of `save-then-label` lands on `/invocation` as before |
| `profile`, `/profile` | `/?open=values-profile#key-<key>` as ADR-334 §7 names the key, `#values-profile` where it names none |
| `/profile-repair` | `/?open=values-profile#values-profile` |

**The form `launch-values`**, `POST` to `/launch`, wraps the sections `values-folders` and `values-services`, and holds the seven controls of ADR-302 §2 by their names and in their order, the three Browse buttons of ADR-331 §5, `use-running-image` where it is drawn, and, after the two sections, one submit button, `check`, *Use these and check*. A post of it holds the values posted and asks the sidecars again, as `check` did (ADR-302 §2), and starts nothing; `use-running-image` is ADR-334 §2's.

**No `id` stands twice on the home page.** Each `id` the forms `next` (§4) and `code-set-folders` draw that the form `profile` draws too is written with the form's name and a hyphen before it: `next-cost-<key>`, and `code-set-folders-cost-codeSetFolders` in place of ADR-334 §5.2's `cost-codeSetFolders`.

**The form `profile`** keeps ADR-334 §7's controls, Save and Reset, and `#profile-list` as an index of its keys at its head, each linking `#key-<key>`. **Every key's section is shown**: ADR-331 §3's rule that the style shows the section `:target` names and the first otherwise goes, the drawer being opened by the same fragment. `seedFolder` is edited here and in §4's box alone: **the form `seed-folder` goes** with its path.

**The pages the drawer and the home page take in answer at their old addresses.** None is a page any more:

| Path | A `GET` or `HEAD` answers `303` to | Any other method |
| --- | --- | --- |
| `/progress` | `/#progress-view` | `405`, `Allow: GET, HEAD` |
| `/next` | `/#next-step` | `POST` stays the path of the form `next` (§4) |
| `/labels` | `/?open=values-relevance#values-relevance` | `POST` stays the path of the form `not-relevant` |
| `/profile` | `/?open=values-profile#values-profile` | `POST` stays the path of the form `profile` |
| `/code-sets` | `/?open=values-corpus#values-corpus` | `405`, `Allow: GET, HEAD` |
| `/seed-folder` | `/?open=values-profile#key-seedFolder` | `405`, `Allow: GET, HEAD` |
| `/label-auto` | `/#next-step` | `405`, `Allow: GET, HEAD` |

so that a tab left on one, coming back to the next start (ADR-325 §4), lands where its content now stands. Each writes nothing. The only pages beside the home page are `/invocation`, `/stop` and `/folders`.

**The folder page's closed list** (ADR-331 §5, ADR-334 §5.2 and §6) is now:

| `for` | The form, its path | Its field | The page returned to |
| --- | --- | --- | --- |
| `vespera.corpus-root` | `launch-values`, `/launch` | `vespera.corpus-root` | `/?open=values-folders#values` |
| `vespera.working-dir` | `launch-values`, `/launch` | `vespera.working-dir` | `/?open=values-folders#values` |
| `vespera.deliverable-root` | `launch-values`, `/launch` | `vespera.deliverable-root` | `/?open=values-folders#values` |
| `next-seedFolder` | `next`, `/next` | `seedFolder` | `/#next-step` |
| `profile-seedFolder` | `profile`, `/profile` | `seedFolder` | `/?open=values-profile#key-seedFolder` |
| `code-set-folder` | `code-set-folders`, `/code-set-folders` | `new-code-set-folder` | `/?open=values-corpus#values-corpus` |
| `not-relevant-folder` | `not-relevant`, `/labels` | `new-not-relevant-folder` | `/?open=values-relevance#values-relevance` |

`seedFolder` and `new-folder` leave it with their forms, a `GET` naming either answering 404 as any `for` off the list.

### 4. The next step is a box of the home page

**`#next-step`**, a `section` of the home page's right column, its `h2` *Next step*, holds what `/next` held (ADR-302 §8, ADR-330 §5, ADR-334 §3, ADR-336 §1): `next-invocation` with `data-invocation` and `data-why`; the form `next`, `POST` to `/next`, with its controls, `save` and `save-then-run`; `arrangement-file`; and, where answers are wanted, `labels-link` to `/?open=values-relevance#values-relevance`, *Answer by folder*, and the words `Or tick Autolabel and press Label to have the local model answer.` **The form `label-auto` goes**: the top bar's box does what it did (§1). **Each `id` the form `next` draws that the form `profile` draws too is written with `next-` before it**, `next-cost-<key>`, `next-report-<key>`, `next-hint-<key>` and the id of each control and its status, so that no `id` stands twice on the home page; `#models` and `#docling-images` stand once.

`save` answers `303` to `/#next-step`, `save-then-run` to `/invocation`, and a refusal of `save-then-run` to `/` with its line, as ADR-331 §6 had it land on `/next`. **The words of `next-invocation`**, whole:

| `data-why` | Words |
| --- | --- |
| `none-ended` | `No run has finished yet, so there is no next step to suggest. Press Run; once a run finishes, this box shows what it needs next.` |
| `ended-badly` | `The last run did not finish, so there is no next step to suggest. The Invocation page says how it ended; press Run again once that is put right.` |
| `said-nothing` | ADR-334 §3's, unchanged |

### 5. Folders to process, and code sets, in one tree

**The form `folders-to-process`**, `POST` to `/folders-to-process`, in the section `values-corpus`, shows **one folder of the corpus at a time**: the folder the home page's query names in `tree`, relative to the corpus root with `/` between its elements, or the corpus root where it names none. Its `#folder-tree` carries `data-path`, that folder as `tree` names it, empty for the corpus root, and `data-tree`:

| `data-tree` | When | Rows |
| --- | --- | --- |
| `listed` | the folder could be listed | one per subfolder |
| `no-corpus-root` | no corpus root is named, or one the system cannot hold | none |
| `no-such-folder` | the folder is not there, or is no folder | none |
| `cannot-be-listed` | it is a folder that cannot be listed | none |
| `outside` | `tree` is absolute but for a folder at or below one `codeSetFolders` states, or holds a `..` element or a `\` | none |

`#tree-path` holds a submit button `up-<j>` for each folder above the one shown, `j` from `0` for the corpus root, its words the folder's name. `#tree-rows` holds, for each subfolder, in name order without regard to case, and, at the corpus root, then for each folder `codeSetFolders` states, in its order, an item `li` carrying:

| Attribute | Value |
| --- | --- |
| `data-folder` | the folder relative to the corpus root, `/` between its elements; a code set folder outside it, and any folder below one, spelt absolute as ADR-334 §5.4 spells it |
| `data-walked` | `walked`; `not-walked`, the folder being one `foldersNotWalked` lists; `not-walked-above`, a folder above it being one; `outside`, a code set folder outside the corpus root or a folder below one |
| `data-code-set` | `marked` where `code-set-names.yaml` names the folder, `not-marked` otherwise |

and its controls, `i` from 1 in the rows' order:

| Control | Name | Holds |
| --- | --- | --- |
| hidden | `folder-<i>` | the folder as `data-folder` spells it |
| checkbox, *Read* | `walk-<i>` | ticked where `data-walked` is `walked`; drawn `disabled` where it is `not-walked-above`; not drawn where it is `outside` |
| checkbox, *Code* | `code-set-<i>` | ticked where `data-code-set` is `marked`; `disabled` where the folder is not walked |
| text, *Name shown in the pages* | `code-set-name-<i>` | the name `code-set-names.yaml` gives the folder, empty where it gives none |
| submit, *Open* | `open-<i>` | |

A checkbox carries no `value`, so a browser sends `on` for one ticked and nothing for one unticked or disabled. Then `foldersNotWalked.provenance`, the key's provenance as the file states it, with the hidden `foldersNotWalked.drawn` of ADR-330 §5, and the submit buttons `save` and `reset`. **No file's name is listed and no file is read**: the folders are listed as the folder page lists them (ADR-331 §5).

**Moving through the tree holds what was ticked.** A press of `open-<i>` or `up-<j>` writes nothing: it holds, in memory beside what earlier presses held, the state of each row posted, by its folder, as ADR-331 §5 holds a form's fields, and answers `303` to the folder chosen. A row drawn for a folder whose state is held shows the state held. A row drawn `disabled`, or a row posted whose state is what the files state, holds nothing.

**Save is one action**, under one hold of the working-directory lock, writing two files all or none, as ADR-334 §6 writes two:

- **`profile.yaml`'s `foldersNotWalked`**: the list the file states as read then, each folder it lists whose row is posted or held ticked taken out, and each folder whose row is posted or held unticked, and not below a folder listed, added after the rest in the order of their paths; written by ADR-330 §5's checks, so a list changed under the provenance the file states, or a file changed since the page was drawn, is refused. A list taken out entirely leaves the key unset.
- **`code-set-names.yaml`**: for each folder posted or held marked, its entry's name set to the name typed, or, where none is typed, to its default name, the folder's last element, as ADR-284 §8 gives `code_set.default_name`, its entry added after the file's, in the order of their paths, where it has none; for each folder posted or held unmarked, its entry removed; every other entry kept; written by ADR-295 §7's checks.

A file that would not change is not written, so a save marking a code set and leaving the list as it stands writes `code-set-names.yaml` alone and asks no provenance.

The lines of `outcome`:

- `nothing was written: every folder is as the files state it`, where neither file would change; no lock is taken;
- `the folders to process are saved: <n> not read, <m> named as code sets`, `<n>` the folders `foldersNotWalked` then lists and `<m>` the entries of `code-set-names.yaml`;
- `foldersNotWalked is not saved: <reason>`, by ADR-330 §5's rules, and ADR-295 §7's line for the names; and ADR-334 §6's lines for a write that fails part way.

A refusal writes neither file and keeps what was held. A save written lets go of it. **Reset** writes nothing, takes no lock, lets go of everything held and answers `303` to the folder shown.

**A code set is marked, not made.** A mark is an entry of `code-set-names.yaml`, ADR-284 §9's rename, read by the drawing alone: a set exists where stage 1 finds one, and an entry naming a folder no set has stays dormant (ADR-284 §9). ADR-334 §5.5's absolute spelling of a set below a code set folder outside the corpus root is the spelling of its rows here.

### 6. An empty field shows the value in use, where one exists

**ADR-334 §1 and §2's rule holds of every field**: an empty field whose value in use the page knows carries the `placeholder` `in use: ` and that value; a field whose value has no default keeps ADR-331 §8's example, and its Browse button where it has one. The field stays empty, so nothing the operator did not type is passed or saved (ADR-330 §2).

| Field | Empty, its `placeholder` |
| --- | --- |
| `vespera.corpus-root`, the seed folder on `profile` and `next`, `new-code-set-folder`, `new-not-relevant-folder` | the example: no default exists |
| `vespera.working-dir` | `in use: <the default>`, below |
| `vespera.deliverable-root` | ADR-334 §1's |
| the four service fields | ADR-334 §2's, the image ADR-334 §11's |
| a key's control on `profile` and `next` | `in use: <value>` where `in-use-<key>`'s `data-source` is one of `effective-values.properties`' sources (ADR-330 §1), the example otherwise |
| `code-set-name-<i>` | `in use: <the folder's last element>`, the set's default name |
| a provenance | the example |

**The work folder left empty is the jar's default.** `application.yaml` names the working directory `${db-dir:${user.dir}/.vespera}`: for a command given no `--db-dir`, `.vespera` under the directory it is started in. **The control panel reads its own current directory once, as it starts, and where `vespera.working-dir` is empty, `<that directory>/.vespera`, absolute and normalised, is the working directory** for every page, check, save and start, as one typed would be:

- the field stays empty and carries `placeholder` `in use: <the default>`; `in-use-vespera.working-dir` carries `data-value`, the default, and `data-source` `default`, or the folder typed and `launch-value`; its status, its preflight row, the reports, the profile and the holder line are read of the default;
- **Run, Label and Publish pass it**, `--db-dir=<the default>`, and start the child in it, as for a folder typed, by the same checks: where it is not there, ADR-331 §6's line refuses the start, `Run is not started: the working directory <the default> is no folder that is there`, and a save of `profile.yaml` makes it (ADR-330 §7);
- ADR-292 §5's `Run needs the working directory`, `Run needs the corpus root and the working directory` and `Label needs the working directory` are no longer said; `Run needs the corpus root` stays;
- the launch values' file stores the field as it is, empty, so the next start reads the default again.

It is not read from `effective-values.properties`: that file lies in the working directory it would have to name, and names no path (ADR-330 §1).

### 7. Every field and section is named in plain words, its key beside it

Every field's `label` holds its name in plain words and, after it, `<small class="key">` holding the key or control name, as secondary text. A checkbox, Autolabel, Read or Code, carries its label and no example, ADR-331 §8's example being for a field a value is typed into. Its hint or, for a key, its `about-<key>`, says what it is in plain words. A key's `hint-<key>` stays ADR-331 §8's, the README's row. The words, whole:

| Field | Name | What it is |
| --- | --- | --- |
| `vespera.corpus-root` | Documents folder | The folder with the documents to process. Vespera only reads it and never changes anything in it. |
| `vespera.working-dir` | Work folder | Where Vespera keeps its database, its reports and profile.yaml. Left empty, it is the .vespera folder of the folder the control panel was started from, as shown. If it is not there yet, the first value you save makes it. |
| `vespera.deliverable-root` | Output folder | ADR-334 §1's hint, its words *the working directory* being *the work folder* |
| `vespera.docling.image` | Converter build | Which build of the document converter to use: GPU where this machine has a graphics card, CPU otherwise. |
| `vespera.docling.base-url` | Converter address | Where the document converter answers. Change it only if you moved it. |
| `spring.ai.ollama.base-url` | Local models address | Where the local AI models answer. |
| `vespera.label.model` | Answering model | The local model that answers the relevance questions when you press Label with Autolabel ticked. |

| Key | Name | What it is |
| --- | --- | --- |
| `seedFolder` | Example documents | Documents you already know belong in what you keep; everything else is measured against them. |
| `degenerateOutputConfidenceFloor` | Lowest text quality | A file whose extracted text the converter rates below this, from 0 to 1, is set aside: its text is too poor to use. |
| `degenerateOutputWordFloor` | Fewest words in a file | A file holding fewer words than this once its text is extracted is set aside. |
| `boilerplateDocumentFrequencyFloor` | Repeated text | A passage found in at least this share of the documents, from 0 to 1, is taken for template text and not compared. |
| `embeddingModel` | Comparing model | The local model that turns each text into numbers, so that documents can be compared. |
| `relevanceScoreFloor` | Relevance cut-off | A document less like your example documents than this is left out. |
| `arrangementApproved` | Approved grouping | The grouping you approve for writing, by its name, or auto to approve every grouping proposed. |
| `generationModel` | Writing model | The local model that writes the text connecting the documents of each page. |
| `generationContextWindow` | Writing model's reading size | How many tokens of a group the writing model reads in one go. |
| `clusterPageByteCeiling` | Largest page | A group whose page would be larger than this many bytes is split over several pages. |
| `logTimestampShareFloor` | Log detection | A text file in which at least this share of lines begin with a date or a time, from 0 to 1, is taken for a log and left out. |
| `extractionAttempt` | Ask again about failed files | Raise it by one to ask the converter again about the files it did not answer about. |
| `codeNameEndings` | Code file endings | File name endings that mark a file as code, kept apart from the documents. |
| `outOfScopeNameEndings` | Endings left out | File name endings that mark a file as no document at all, so that it is left out. |
| `translationSimilarityFloor` | Translation match | How alike, from 0 to 1, two documents in different languages must be to be taken for one. |
| `preferredLanguages` | Preferred languages | Which language's copy to keep, first choice first, of a document held in several. |
| `configurationSettingShareFloor` | Configuration file detection | A text file in which at least this share of lines are a program's settings, from 0 to 1, is taken for a configuration file and left out, unless it lies in a set of code. |
| `deliverableLanguage` | Language of the pages | The language the finished pages are written in, English or Italian. |
| `clusterResolution` | Grouping detail | How finely the documents under each example divide into groups: higher makes more, smaller groups. |
| `codeSetFolders` | Code folders elsewhere | Folders of code elsewhere on this machine, read with the documents and kept as code sets of their own. |
| `foldersNotWalked` | Folders not read | Folders of the documents folder that Vespera never reads. Untick them under Folders to process. |

| Section | `about-<id>` |
| --- | --- |
| `values-folders` | Where the documents are, where Vespera keeps its work, and where the finished pages go. |
| `values-services` | The document converter and the local models Vespera asks, and where they answer. |
| `values-corpus` | Every folder of the documents folder is read. Untick a folder to leave it unread; tick Code on a folder of code to name its code set. |
| `values-relevance` | Folders of what you are not keeping: every relevance question about a file below one is answered not relevant, and every other relevant. |
| `values-profile` | The values of profile.yaml in the work folder. An empty field uses the value shown in it; changing a value may make the next run redo some steps. |

`ProfileKey`'s `about()` is the second table's third column, and it gains a name of its own, its second column. `ChildCommand.ARGUMENTS`' reasons name the fields by these names.

### 8. A folder the profile names is not walked

#### 8.1 The profile's `foldersNotWalked`

**A new profile key, `foldersNotWalked`**, after `codeSetFolders` in `Profile`'s components and `ProfileKey`'s order, of a new kind, folders below the corpus root, `CORPUS_FOLDERS` in `ProfileKey.Kind`, its value type in `profile` `CorpusFoldersValue`, which reads, as `FoldersValue` does, to `Answered` or `Unreadable`: one string under `value` (ADR-061), holding one or more folders separated by the system's path separator, as `codeSetFolders` holds its own. Each piece is stripped and an empty piece passed over. **A piece is a folder relative to the corpus root**: elements separated by `/`, none empty, none `.` or `..`, no `\`, neither beginning nor ending with `/`, and not absolute. A value holding any other piece is unreadable (ADR-120), and none of it is read: the walk then reads every folder. Unset, every folder is read, as before. A piece naming no folder that is there keeps nothing out. Its earliest run is byte-level reduction, every later run following, since a walk that leaves a folder out is another walk. Its hint, which the README's table of values gains as its row's second cell, is *"which folders of the corpus hold nothing you want read"*, its example `archive/old-flows`, and its plain words §7's.

#### 8.2 The walk does not enter a folder not walked

**A directory beneath the corpus root whose path from it, compared element by element and without regard to case on Windows, is a folder `foldersNotWalked` lists is a folder not walked**: the walk counts it as an entry and does not enter it, so nothing below it is a file occurrence, a walk anomaly or an entry the walk counts. A folder below one listed is never met. A code set folder outside the corpus root and the seed folder are walked as before, a relative piece naming nothing in them.

#### 8.3 The walk records it, and accounts for it

- **The list a walk is given is recorded with it**, in `walk_folder_not_walked (walk_id INTEGER NOT NULL REFERENCES walk (id), ordinal INTEGER NOT NULL, folder TEXT NOT NULL, PRIMARY KEY (walk_id, ordinal))`, owned by `ledger`, additive and built empty on a database written before it, with no schema version moving, as ADR-334 §5.3 adds `walk_folder`; `ordinal` from 1 in the profile's order, `folder` the piece as stated.
- **An unfinished walk is continued with the list it recorded**, whatever the profile now states (ADR-055); a list changed since applies from the next walk.
- **The walk counts each folder not walked it meets**, in a column `folders_not_walked INTEGER NOT NULL DEFAULT 0` of `walk`, added as `entries_seen` and `directories_entered` are added at each checkpoint (ADR-056), and **ADR-056's identity is `entriesSeen == occurrences + anomalies + directoriesEntered + foldersNotWalked - 1`**, `Walk.Progress.unaccountedFor` being the one place it is written.
- **A walk given another list than the walk before it is no repetition** (ADR-115), whatever occurrences it holds: the two lists, compared piece by piece in their order, differ.

### 9. Which run ids move

| Change | Folder | Moves |
| --- | --- | --- |
| §8.1's key, in `Profile` | `profile` | none by itself (ADR-274 §5); a profile naming a folder walks another walk from the census on, so every stage's run over it is new |
| §8.2 and §8.3, in `Walk`, `WalkRecorder` and the census | `corpus`, `ledger`, `pipeline` | stage 1, its version naming `corpus`, and every later stage following it (ADR-089) |
| §1 to §7 | `io.algernon.controlpanel` | none: it is no module |

### 10. What a test holds, and what the implementation owes

A test reads, beside ADR-302 §9's and ADR-331 §9's names: on every page `top-bar`, the box `label-auto` of `launch`, `values-held` (`data-corpus-root`, `data-working-directory`), `to-home`, `to-invocation`, `open-values`; on `/`, `reports-box`, `progress-view`, `next-step`, `ready-to-run`, `machine-values-box` (`data-column`), `values`, `close-values`, the five sections (`open`), `about-<section>`, `in-use-vespera.working-dir`, the forms `launch-values`, `folders-to-process` (`folder-<i>`, `walk-<i>`, `code-set-<i>`, `code-set-name-<i>`, `open-<i>`, `up-<j>`, `foldersNotWalked.provenance`; buttons `save`, `reset`), `folder-tree` (`data-tree`, `data-path`), `tree-rows` (`data-folder`, `data-walked`, `data-code-set`). **`PanelBrowser` ticks a checkbox** named among the fields given where the value given is the checkbox's value, `on` where it carries none, and unticks it otherwise, as a browser sends one ticked and none unticked.

The implementation owes, **in `io.algernon.controlpanel`**: `ControlPanelPage.render`'s top bar in place of the sidebar and the top row, and the style's rules of §1 to §3; `LaunchPages` as the home page, its form `launch` with the box Autolabel and the form `launch-values`, a post naming no value keeping those held; `ControlPanel` reading its current directory once and `ControlPanelState` reading the working directory's default from it (§6); `LaunchValues`' refusals without the working directory's lines; `ChildCommand` given the default where the field is empty, and `ARGUMENTS`' reasons in the words of §1 and §7; `ValuePages` for the drawer's sections, the relevance section, the tree's form and the retired paths; `NextPages` for §4's box without its form `label-auto`; `InvocationPages` for `#progress` and `/progress`'s answer; `FolderPages`' closed list; `ControlPanelState` holding the tree's rows; `WorkingDirectoryWrite` writing `profile.yaml` and `code-set-names.yaml` in one action; `ProfileKey.FOLDERS_NOT_WALKED`, its kind, name, hint and example, and a name for each key (§7); `ProfileEdit`'s check of the new kind. **In `profile`**: `foldersNotWalked` and its value type. **In `corpus`**: §8.2 in `Walk` and §8.3 in `WalkRecorder`. **In `ledger`**: `walk_folder_not_walked` and the column in `schema.sql`, and `Walks`. **In `pipeline`**: `CensusTasklet` handing the walk the list. **Documents**: the README's row of `foldersNotWalked` and its section on the control panel; `CONTEXT.md`'s Folder not walked; `docs/architecture.md`'s census.

## Known limits

- **The starts in the top bar take the values held, not the values typed.** A value typed in the drawer and not yet posted by *Use these and check* is not what Run starts on; `#values-held` says what it starts on.
- **A folder of the tree is shown one at a time**, so ticks across the corpus are reached by Open and the path, each a request; a value typed elsewhere on the home page and not posted is lost by them, as by Browse (ADR-331).
- **The home page does not reload**, so its tiles, its next step and a progress page first written while it is open are shown at its next load.
- **A browser with no `:has()`** opens the drawer only where the address names `#values` itself, and a save landing on a key leaves it closed.
- **A code set marked where stage 1 finds no set is dormant** and the tree still shows it marked: the control panel reads no `vespera.db`, so it cannot say which sets exist.
- **A folder not walked is matched by its path**: a folder moved or renamed in the archive is walked again until the list names it there.
- **Two forms edit one key**: `foldersNotWalked` in the tree and in `profile`, `codeSetFolders` in `code-set-folders` and in `profile`, each by the same checks, as ADR-334 §7 has `/next` and `/profile` share a key.
- **Autolabel is ticked again for each start of the model**, since it is kept nowhere (§1).
- **The work folder's default follows the control panel's own current directory**, not a machine's own configuration of `vespera.working-dir`, in `application-local.yaml`, the environment or `db-dir`, which the jar would read when started by hand: the control panel passes `--db-dir` itself, so what the field shows is what a start passes (§6).
- **A folder not relevant is answered so only where it is walked**: a question lies below no folder not walked, since nothing there is read.

## Alternatives refused

- **One form for the top bar and the drawer's fields.** Forms cannot nest, so it would hold every form of the drawer as one, every press posting every value, or need the `form` attribute ADR-297's browser does not read.
- **The top bar holding the seven values as hidden fields.** They are the values held, which a post of none keeps already.
- **The drawer opened by a script, or by a query alone.** A script is ADR-325's one; a query would show the drawer without the fragment the browser scrolls to.
- **The whole tree of the corpus drawn at once.** Its folders are not bounded, every load of the home page would list them all, on a share each a round trip, and without a script nothing could fold it lazily.
- **A folder not walked recorded as a walk anomaly.** An anomaly is an observation of the archive (ADR-053); a folder not walked is the operator's choice.
- **The folders not walked kept by the control panel alone, as the folders not relevant are.** The jar must read them to walk, and a value that changes what a corpus is travels with the profile, as `codeSetFolders` does (ADR-334 §4).
- **A tick of Code making the folder a code set's folder.** Stage 1 finds sets by build files (ADR-284 §2); a set made by a tick would be a profile value read by stage 1, moving its runs, where a name moves none.
- **The next page kept as a page.** With no page menu it would be reached only by its address, and what it suggests is what the home page is opened to see.
- **Autolabel kept as a launch value across starts.** A launch value names where things are on one machine (ADR-292); a box left ticked would have every later Label give the model's answers, which no press asked for.
- **Have the local model answer kept as a button beside Autolabel.** Two controls for one start.
- **The work folder's default read from `effective-values.properties`.** The file lies in the working directory, so it cannot say where that is, and it names no path (ADR-330 §1).
- **The empty work folder passed as none, the child left to resolve its own default.** Its current directory would have to be the control panel's, every page reading a folder no field names; `--db-dir` passed makes the folder shown the folder used.
- **What this machine runs with listed twice**, in the right column and again in the drawer.

## Consequences

- The control panel opens on one page that shows how the corpus stands, what Run needs and what comes next, with Run, Label, Autolabel and Check always in reach and every value one press away.
- The seed folder, the code-set names, the code set folders and the folders not relevant are edited in one place each kind; the launch page's seed folder form, the code-sets page, the relevance page and the next and profile pages are gone as pages, their old addresses landing where their content stands.
- Run starts with the work folder left empty, on the folder the field shows.
- An operator leaves a folder of what is not documentation unread by unticking it: it costs no conversion and asks no question, and the next walk is another walk, every run over it new.
- A field left empty says what it stands for wherever that is known.
- The screen tests of ADR-302, ADR-331, ADR-334, ADR-335 and ADR-336 that read the old pages read the home page and the drawer.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheTopBarScreenTest.everyPageOfItsOwnOpensOnTheTopBarHoldingRunLabelCheckAndAutolabel` | on `/`, `/invocation` and `/folders`: `#top-bar` holding `launch`, its buttons the three in order, the box Autolabel and no other control, no `#sidebar`, no `#top-row`, no `nav` (§1) |
| `controlpanel.TheTopBarScreenTest.labelWithAutolabelTickedStartsLabelAutoAndUntickedStartsLabel` | each child's command line (§1) |
| `controlpanel.TheTopBarScreenTest.autolabelIsUntickedOnEveryPageAndKeptNowhere` | after a start with it ticked: unticked on the pages drawn, the launch values' file unchanged (§1) |
| `controlpanel.TheTopBarScreenTest.theTopBarStaysAtTheTopOfTheWindowAsThePageScrolls` | the style's rule of `#top-bar`, `position: sticky` and `top: 0` (§1) |
| `controlpanel.TheTopBarScreenTest.theTopBarLeadsHomeToTheInvocationPageAndToTheSettings` | the three links, their addresses, the page's own marked (§1) |
| `controlpanel.TheTopBarScreenTest.runInTheTopBarStartsOnTheValuesHeldAndSaysWhichTheyAre` | values posted by `launch-values`, `#values-held` saying them, Run pressed from the invocation page starting on them (§1, §3) |
| `controlpanel.TheTopBarScreenTest.aPostOfTheTopBarNamesNoValueAndKeepsTheValuesHeld` | Check pressed from the invocation page: the values held unchanged, landing on `/` (§1) |
| `controlpanel.TheHomePageScreenTest.theHomePageHoldsItsBlocksInThreeColumns` | the six blocks in order with their columns, and its title (§2) |
| `controlpanel.TheHomePageScreenTest.theReportsAreAFoldableBoxDrawnOpenOnTheLeft` | `#reports-box` a `<details>`, `open`, holding `#reports` and a report's link (§2) |
| `controlpanel.TheHomePageScreenTest.whatThisMachineRunsWithIsAFoldableBoxDrawnClosedOnTheRightAlone` | `#machine-values-box` closed, holding `#machine-values`; no list of them in the drawer (§2, §3) |
| `controlpanel.TheHomePageScreenTest.theProgressPageIsShownOnTheHomePageWhichNeverAsksToBeLoadedAgain` | `not-written` and `shown`, no refresh either way (§2) |
| `controlpanel.TheHomePageScreenTest.theReadyToRunChecklistIsThePreflight` | `#ready-to-run` holding the five rows (§2) |
| `controlpanel.TheHomePageScreenTest.eachPageTakenInAnswersWithWhereItsContentNowStands` | the six paths' `303` and `Location`, and `405` for three (§2, §3) |
| `controlpanel.TheValuesDrawerScreenTest.theDrawerHoldsFiveFoldableSectionsInOrder` | the five ids and summaries (§3) |
| `controlpanel.TheValuesDrawerScreenTest.theDrawerOpensOnItsOwnAddressOrOneInsideItByTheStyleAlone` | the two rules, the close link, one script (§3) |
| `controlpanel.TheValuesDrawerScreenTest.theSectionTheAddressNamesIsDrawnOpenAndTheFoldersOtherwise` | `open` on one section in each case (§3) |
| `controlpanel.TheValuesDrawerScreenTest.theLaunchValuesAreOneFormOverTheFoldersAndTheServices` | `launch-values`: its two sections, seven controls, three Browse buttons, `check` (§3) |
| `controlpanel.TheValuesDrawerScreenTest.useTheseAndCheckHoldsTheValuesAndLandsInTheDrawer` | the value held, the file unwritten, landing at `/?open=values-folders#values` (§3) |
| `controlpanel.TheValuesDrawerScreenTest.aSaveOfTheProfileLandsAtItsKeyInTheDrawer` | `/?open=values-profile#key-<key>` (§3) |
| `controlpanel.TheValuesDrawerScreenTest.theSeedFolderIsAValueOfTheProfileFormAlone` | no form `seed-folder`; `seedFolder` in `profile` with its Browse (§3) |
| `controlpanel.TheValuesDrawerScreenTest.theFoldersNotRelevantAreASectionOfTheDrawer` | `not-relevant` and `#questions` in `values-relevance`, a save landing back there, the list written (§3) |
| `controlpanel.TheNextStepBoxScreenTest.theNextStepIsABoxOfTheHomePagesRightColumn` | `#next-step`, its column, `next-invocation` in it (§4) |
| `controlpanel.TheNextStepBoxScreenTest.beforeAnyRunHasFinishedTheBoxSaysToPressRun` | `none-ended`'s words (§4) |
| `controlpanel.TheNextStepBoxScreenTest.afterARunThatDidNotFinishTheBoxSaysSo` | `ended-badly`'s words (§4) |
| `controlpanel.TheNextStepBoxScreenTest.theValuesTheNextRunWantsAreSavedFromTheBoxAndLandOnIt` | the form `next` in the box, its ids `next-`, no id twice, the save landing at `/#next-step`, the profile written (§4) |
| `controlpanel.TheNextStepBoxScreenTest.whereAnswersAreWantedTheBoxLeadsToTheRelevanceSectionAndToAutolabel` | `labels-link` to the section, the words, no form `label-auto` (§4) |
| `controlpanel.TheFoldersToProcessScreenTest.everyFolderOfTheCorpusIsReadAtFirst` | rows by name, each `walked` and ticked, no file listed (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.aFolderUntickedAndSavedIsNotRead` | `foldersNotWalked` written with its provenance; the row `not-walked` (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.aFolderBelowOneNotReadCannotBeTicked` | `not-walked-above`, disabled (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.aFolderTickedAgainIsReadAgain` | the key left unset (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.aCodeSetIsMarkedInTheSameTreeWithItsName` | an entry with the name typed, one with the folder's own, one removed (§5, §6) |
| `controlpanel.TheFoldersToProcessScreenTest.openingAFolderHoldsWhatWasTickedAndWritesNothing` | the tick held across Open and the path, written by Save (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.aChangedListWithNoProvenanceIsRefusedAndNothingIsWritten` | the refusal, neither file written (§5) |
| `controlpanel.TheFoldersToProcessScreenTest.withNoCorpusRootTheTreeListsNothing` | `no-corpus-root` (§5) |
| `controlpanel.EveryEmptyFieldShowsTheValueInUseScreenTest.anEmptyValueOfTheProfileShowsTheValueTheJarSaidItUses` | `in use: ` from the file; the example where nothing is beneath (§6) |
| `controlpanel.EveryEmptyFieldShowsTheValueInUseScreenTest.aFolderWithNoDefaultKeepsItsExampleAndItsBrowseButton` | the documents folder, the seed folder, a code set folder (§6) |
| `controlpanel.EveryEmptyFieldShowsTheValueInUseScreenTest.anEmptyWorkFolderIsTheJarsDefaultWhichRunPasses` | `in use: <current directory>/.vespera`, `default`; Run refused by that folder's line where it is not there, nothing made (§6) |
| `controlpanel.EveryFieldIsNamedInPlainWordsScreenTest.eachLaunchValueIsNamedInPlainWordsWithItsKeyBeside` | the seven names, keys and hints (§7) |
| `controlpanel.EveryFieldIsNamedInPlainWordsScreenTest.eachValueOfTheProfileIsNamedInPlainWordsWithItsKeyBeside` | the twenty-one names, keys and lines (§7) |
| `controlpanel.EveryFieldIsNamedInPlainWordsScreenTest.eachSectionOfTheDrawerSaysWhatItHoldsInPlainWords` | the five lines (§7) |
| `controlpanel.ProfileKeyTest` | `foldersNotWalked` after `codeSetFolders`, of the kind `CORPUS_FOLDERS`, entering byte-level reduction (§8.1) |
| `controlpanel.PanelBrowserTest.aCheckboxNamedIsTickedByItsValueAndUntickedByAnyOther` | the browser's checkbox (§10) |
| `pipeline.AFolderNotWalkedIsNeverReadTest.theWalkNeverEntersAFolderTheProfileSaysIsNotWalked` | no occurrence below it, the walk finished and reconciled, the folder counted and the list recorded (§8.2, §8.3) |
| `pipeline.AFolderNotWalkedIsNeverReadTest.aFolderNotWalkedIsComparedElementByElement` | a folder whose name begins with a listed piece is walked (§8.2) |
| `pipeline.AFolderNotWalkedIsNeverReadTest.aPieceThatIsNoFolderBelowTheCorpusRootReadsNothingOfTheValue` | an unreadable value: every folder walked, nothing recorded (§8.1) |
| `pipeline.AFolderNotWalkedIsNeverReadTest.aWalkGivenAnotherListIsAnotherWalk` | two walks of the same files, the lists differing (§8.3) |

These change with this record, holding what they held through the home page and the drawer: `controlpanel.TheControlPanelsLayoutScreenTest`, `TheCompactScaleScreenTest`, `FieldHintsScreenTest`, `NativePickersScreenTest`, `TheFolderPageScreenTest`, `FolderFieldStatusScreenTest`, `FolderFieldsKeepALeadingSpaceScreenTest`, `LaunchScreenTest`, `LaunchImageScreenTest`, `TheGpuImageIsPreselectedScreenTest`, `TheImageTheConverterRunsIsOfferedScreenTest`, `TheLaunchPageShowsTheValuesInUseScreenTest`, `TheLaunchPageStartsLabelAutoScreenTest`, `InvocationScreenTest`, `NextScreenTest`, `TheNextPageSaysWhyItSuggestsNothingScreenTest`, `ProfileScreenTest`, `ProfileEditorScreenTest`, `ProfileEditorGuardsScreenTest`, `TheProfileIsOneFormScreenTest`, `TheClusterResolutionEditorScreenTest`, `CodeSetsScreenTest`, `CodeSetFoldersOutsideTheCorpusRootScreenTest`, `TheProgressPageInTheControlPanelScreenTest`, `TheNotRelevantFoldersScreenTest`, `EveryPageHoldsOnlyTheControlPanelsScriptTest`, `TheControlPanelsTabTest`, `ChildCommandTest`, `LaunchValuesTest` and `ScreenServer`, each where it presses `launch` with fields or the button `label-auto`, reads a page's sidebar or top row, goes to a page taken in, reads a working directory left empty as none, or reads the words §4 and §7 change; and `pipeline.TheJarReadsWhatTheControlPanelWritesTest` and `profile.ProfileStoreTest`, which gain `foldersNotWalked` as they gained `codeSetFolders`.

## What this does not decide

- **Whether `foldersNotWalked` may name a folder below a code set folder outside the corpus root.**
- **Whether the home page reloads while a child runs**, which would need what is typed kept across a reload.
