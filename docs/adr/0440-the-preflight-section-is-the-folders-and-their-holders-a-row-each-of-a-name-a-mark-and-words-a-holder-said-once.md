# ADR-440 — The Preflight section is the folders and their holders, a row each of a name, a mark and words, and a holder is said once, in words

> **Partly amended — see [ADR-444](0444-the-gpu-image-starts-only-where-docker-has-the-nvidia-runtime-and-the-control-panel-offers-the-toolkit-or-the-cpu-image.md).** Of §1: after `docling-image`, a row `graphics-card`, *Graphics card*, and a row `graphics-card-warning-<n>`, *Warning*, for each warning of the driver or the card, each drawn only where the converter's image field names the GPU image and the window keeps an answer of the card whose runtime is known. Of §2: `graphics-card` is *OK* where Docker has the nvidia runtime and *KO* where it has none, each with its words, and a warning's row *KO*, the warning its words and its caption. Of §8: the window asks `DockerControl.graphicsCard` once when a check finding the Docker daemon *OK* comes back and none is kept, dropping the answer where a check finds the daemon other than *OK* and where a press of *Start Docker* returns. The rest of this record stands.

- **Date**: 2026-11-21
- **Status**: accepted on 2026-11-21.
- **Decided**: under the operator's delegation, on [#924](https://github.com/algernon28/vespera/issues/924): that the Preflight section holds the two folders and their holders and no line of Docling's or Ollama's answers, and loses its Check button, the header's check being the one place the containers are said (§1); that each row is a name, a mark and words in three columns, the mark *OK* or *KO* as the header's list draws its marks (§2); that a holder is said in words, *vespera run (pid 44072) since 23:19*, its line whole the caption (§3); that one process holding both folders is said once (§4); that free space is said once, in each folder's words (§5); that an image Docling runs other than the one Run would use is still said, in a row of its own, from the header's check (§6); and that the models the Profile window offers are the names the header's check found Ollama listing (§7).
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1's Preflight: *"the sidecars' last answers (ADR-302 §5), the two locks' holders (ADR-278 §4), free space (ADR-302 §6)"*, and the Check beneath them: the section is §1's rows, the holders in §3's words, free space in each folder's words alone, and no Check. It carries a note at its head.
- **Amends**: [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §3: where Docling is *OK* its `GET /version` is asked once and the image it names kept, and the names Ollama's `GET /api/tags` lists are kept in its order (§6, §7); §8's *"Not explanations, and kept as text: … the Preflight section's lines"*: the rows' words stay text, and a holder's row and the image's row carry a caption (§3, §6); §12: `DockerCheck` gains two components (§8). Its §2 list, its marks and the rest of §3 stand. It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §2's *"An image the converter runs that is not the one Run would use is said before Run"*: it is said in §6's row, from the header's check, and not on a press of Check. The image Run would use is still the field's. It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §7's *"`SidecarChecks.ollama` asks `/api/tags` and returns the names"*: the names offered are the last header check's (§7), and `ServiceChecks` goes. It carries a note at its head.
- **Keeps**: [ADR-306](0306-the-control-panel-reads-a-holder-line-of-its-own-process-as-held-by-nobody-and-serves-the-cluster-page-sizes-as-a-sixteenth-report.md) §1: a working directory's holder line naming the control panel's own process is held by nobody, and draws no row. [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §4 as ADR-285 §5 amends it: what is held, read from the lock file's first line without a lock, `HolderReading`'s rule unchanged. [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8: every text of the section can be selected and copied. [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §6: a folder's words, *Not chosen*, *No such folder*, *Found, ‹n› GiB free*. [ADR-436](0436-the-jar-and-the-control-panel-use-no-working-directory-nothing-names-and-browse-chooses-one.md): with no working directory chosen, no folder is read for its row, which says *Not chosen*. [ADR-395](0395-one-vespera-runs-on-a-machine-at-a-time-and-the-control-panels-own-child-is-let-through.md): the machine lock is not shown in the section; a control panel open holds it. The header's list, its rows, marks and captions (ADR-430 §2, §8, ADR-433).
- **Settles**: [#924](https://github.com/algernon28/vespera/issues/924).

## Context

**What the section draws.** `PreflightPane.draw` writes seven lines of `Label: text` into a column: *Corpus root* and *Working directory*, each with `FolderStatus`'s words; *Converter* and *Ollama*, each with the answer the last press of the section's Check gave; *Working directory held by* and *Corpus root held by*, each the lock file's first line whole or *nobody*; and *Free space*, the working directory's drive's. Docling's warning of another image follows them. The clock draws it each second.

**The holder lines.** `vespera run` writes its working-directory lock's line as `pid=<pid> started=<ISO-8601 with offset, to the second> command=<the arguments joined by spaces>` (ADR-177 §1) and its corpus-root lock's with ` root=<the root's key> working-directory=<the folder>` after it (ADR-279). A control panel writing into a working directory writes `command=control-panel write <file>` (ADR-295). Drawn whole, a line is wider than the sidebar and cut off, repeats the two folders the rows above name, and, one `vespera run` holding both locks, says the same process twice.

**Free space** is said twice: the working directory's words already end *‹n› GiB free*.

**Converter and Ollama** read *not checked* until Check is pressed, while the header's list (ADR-430 §2) has checked Docling and Ollama every 5 s since the window opened, so the two disagree. The press is also the only thing that reads Docling's `/version`, from which the image Docling runs is compared with the one Run would use (ADR-334 §2), and the only thing that keeps the names Ollama lists, which the Profile window offers for the two model keys (ADR-331 §7). The header's check asks Ollama's `/api/tags` already, and keeps nothing of it but the two models' marks.

**The header's list** draws a row as a name and a mark, *OK* in the keeping green, class `ok`, and *KO* in the ending red, class `ko` (ADR-430 §2, ADR-423).

## Decision

### 1. The section holds the folders and their holders

The section `preflight` holds one node, `preflight-list`, and nothing else: no Check, no line of Docling's or Ollama's answers and no line of free space. Its rows, in this order, each present only where its condition holds:

| Row | Name | Present where |
|---|---|---|
| `corpus-root` | Corpus root | always |
| `corpus-root-holder` | Held by | the corpus root is held, and not by the process that holds the working directory (§4) |
| `working-directory` | Working directory | always |
| `working-directory-holder` | Held by | the working directory is held, and not by the process that holds the corpus root (§4) |
| `holder` | Both held by | one process holds both (§4) |
| `docling-image` | Docling's image | §6's condition |

Held is as the window reads it today: the working directory's `vespera.lock` and the corpus root's lock file in the folder of corpus-root locks, each its first line, by `HolderReading`, a line naming the control panel's own process held by nobody for the working directory (ADR-306 §1), for the folders the Folders section names.

### 2. A row is a name, a mark and words

`preflight-list` is a grid of three columns: the name in the first, as wide as the widest name shown; the mark in the second, as wide as its widest mark; the words in the third, which takes the width left and cuts its text with an ellipsis where it is wider. A row's three are on one line of the grid, the rows in §1's order, top to bottom. Each is a selectable text on one line (ADR-364 §8): the name of id `preflight-<row>`, the mark `preflight-<row>-mark`, the words `preflight-<row>-words`.

**The marks** are the header's two: *OK*, style class `ok`, drawn in the keeping green, and *KO*, style class `ko`, drawn in the ending red, each also of class `preflight-mark`.

| Row | *OK* where | *KO* where | Words |
|---|---|---|---|
| `corpus-root`, `working-directory` | the folder is found | it is not chosen or there is no such folder | `FolderStatus`'s, as ADR-331 §6 has them: *Found, ‹n› GiB free*, *Found* where the free space cannot be read, *Not chosen*, *No such folder* |
| `corpus-root-holder`, `working-directory-holder`, `holder` | never | always | §3's |
| `docling-image` | never | always | §6's |

A folder found and held reads *OK* on its own row and *KO* on its holder's: the folder is there, and Run is held by another invocation (ADR-344 §3).

### 3. A holder in words

A holder's words are `<who> (pid <pid>) since <time>`, from its line read by `HolderLine`:

- **who**: from the line's `command=` field, its first word that does not begin with `-`: *vespera run*, *vespera label* or *vespera publish* where it is `run`, `label` or `publish`; *another control panel* where it is `control-panel`; *another Vespera* where it is any other, or where the line has no `command=` field or the field is empty;
- **pid**: the line's;
- **time**: the line's `started`, at the system's time zone, as `HH:mm` where its date there is the date it is there now, and as `yyyy-MM-dd HH:mm` where it is another.

So a `vespera run` started at 23:19 that day holds as *vespera run (pid 44072) since 23:19*. A first line that is not blank and does not have the form, which `HolderReading` reads as held, is said *a process whose line cannot be read*.

**Its caption**, on the words, is the line whole, as the lock file's first line holds it: the pid, the start, the command and, for the corpus root's, the root and the working directory, none of which the row repeats. The words are selectable text, so the operator can copy the pid; the line itself is read in the caption.

### 4. One process holding both is said once

Where the corpus root and the working directory are both held, and both lines have the form and name the same pid, the two are one process: a line is held only while its pid is a live process started no later than a second after the line's start (ADR-278 §4), so two held lines of one pid name the one process that has it now. One row, `holder`, *Both held by*, then stands after the working directory's, its words §3's for the working directory's line and its caption the corpus root's line, a line break, then the working directory's. Neither `corpus-root-holder` nor `working-directory-holder` is drawn. The pid alone is compared: `vespera run` writes each line with a start of its own, read from the clock as it takes that lock, and the two can be a second apart. Two lines naming different pids, or either not having the form, are two holders, each said beneath its own folder.

### 5. Free space is said once

Free space is said in each folder's words alone, *Found, ‹n› GiB free*, each of its own drive. No row says it apart.

### 6. Docling's image, from the header's check

**The check reads the image.** Each check of ADR-430 §3, where Docling is *OK*, asks Docling's `GET /version` once, at the same address, waiting at most 2 s as for `/health`, and keeps the string member `vespera-image` of the JSON object it answers with (ADR-179 §2); where Docling is not *OK*, the question is not asked, and where it gets no 2xx in time, no JSON object or no such string, the image kept is empty. The header's Docling row reads what `/health` answered, as before, whatever `/version` answers.

**The row.** `docling-image` is drawn where the last check that came back kept an image that is not empty, the converter's image the Services section's field holds now, white space taken off, is not blank, and the two differ, compared exactly, as Docling's own name and the field's are compared at extraction's start (ADR-179 §2). Its mark is *KO*, its words `runs <the image kept>; Run would use <the field's image>`, both whole, and its caption *Extraction refuses a Docling that runs another image than the one Run would use.* Where they are the same, or either is empty, no row is drawn.

So the section says nothing of Docling that the header's list does not rest on: it shows the image the same check read.

### 7. The models the Profile window offers

Each check of ADR-430 §3, where Ollama is *OK*, keeps the names its one `GET /api/tags` lists, in the answer's order; where Ollama is not *OK*, none. The Profile window's and the values wanted's choices for `embeddingModel` and `generationModel` offer the names of the last check that came back as they are drawn, none before the first has. No other question is asked for them.

### 8. The seams

- **`DockerCheck`** gains, after `generativeModel`, `String doclingImage`, §6's image, empty where none was kept, and `List<String> ollamaModels`, §7's names, in Ollama's order; its five-component constructor stays, giving `""` and an empty list. `DockerControl.inProcess`'s `check` fills both; `DockerControl.NONE`'s answers both empty.
- **`ControlPanelState.keepCheck(DockerCheck)`** keeps each check that comes back, and **`ControlPanelState.lastCheck()`** answers it, `Optional<DockerCheck>`, empty before the first. The cockpit keeps each check it shows in the header's list. `ProfileForm` and `WantedValues` read §7's names from it.
- **`HolderWords.of(String line, ZonedDateTime now)`**, in `ui`, answers §3's words for a holder's first line: `now` gives the time zone and the date the time is read at.
- **`PreflightPane`** draws §1's grid; its Check and `askTheServices` go. **`ServiceChecks`**, `ControlPanelState.lastChecks` and `ControlPanelState.keepChecks` go.

### 9. No run id moves

Every change is the control panel's, which no stage's version names; it decides no verdict, cache key, cluster or text of the deliverable.

## Alternatives rejected

- **Keeping Converter and Ollama rows in the section, read from the header's check.** They would say again what the header's list says beside them; the section is for what Run passes and what holds it.
- **Keeping Check.** Every 5 s the header's check asks the same containers at the same addresses (ADR-430 §4); a press would ask what was asked a moment before.
- **Dropping the image comparison with Check.** ADR-334 §2 has the image said before Run, so that a Run refused at extraction's start is foreseen; only where it is read from moves.
- **The image as a sixth row of the header's list, or Docling's mark *KO* for it.** The header's rows say what answers, and its columns are fixed (ADR-430 §2); a Docling that answers is *OK*, and Run waits for nothing more.
- **The holder line whole in the section, wrapping.** It repeats both folders' paths and the arguments, and takes several lines of a narrow sidebar to say who and since when.
- **The holder in the folder's own row.** The folder's words and the holder's together are wider than the sidebar, and a folder found and held would need two marks in one row.
- **The whole line as a selectable text beneath the words, or a menu to copy it.** It would put the line back in the section; the caption shows it, and the pid, the one part an operator types elsewhere, is in the words.
- **Fields of the line, one a line, in the caption.** `command=` holds the arguments as written, spaces and `=` among them, so ` root=` cannot be told from an argument; the line whole is what the holder wrote.
- **The two lines' paths, or their starts, compared to tell one process.** The paths are written differently in the two lines, and the starts are each lock's own; the held pid is what names the process.
- **The header's dash for a folder not chosen.** The dash says a row was not asked; a folder not chosen holds Run, as one not there does.

## Consequences

- **The section is two rows where nothing holds the folders**, three or four where something does, and one more where Docling runs another image.
- **Who holds a folder is read at a glance**, by the command and when it started; the line is a mouse over away.
- **The section never disagrees with the header**: it says nothing of Docling or Ollama but the image the header's check read.
- **The Profile window offers Ollama's models without a press**, from the window's opening once a check has come back.
- **A check where Docling answers asks one more question**, `/version`, within the 2 s Docling already has.
- **The README's** Preflight clause says the folders, their holders and Docling's image where it differs.
- ADR-344, ADR-430, ADR-334 and ADR-331 carry a note at their heads pointing here.

## What this does not decide

- **The machine lock's holder in the window.** A control panel open holds it; another Vespera holding it refuses the window's own start (ADR-395 §4).
- **A holder's line written by a process this record does not name**, whose who is *another Vespera* until a record names it.
- **The Profile window drawn before the first check**, which offers no names until it is drawn again, as before.

## Tests

| Method | What it holds |
|---|---|
| `ui.ThePreflightSectionTest.eachFolderIsARowOfANameAMarkAndWordsAndFreeSpaceIsSaidOnce` | §1, §2, §5: both folders found, the grid's rows by its row index exactly the corpus root's and the working directory's, each name in column 0, mark in 1 and words in 2, *OK* of class `ok`, words *Found, ‹n› GiB free*; no other text of the section says free space, Docling, Ollama or the converter, and the window holds no `check`; a corpus root that is not there reads *KO*, class `ko`, *No such folder* |
| `ui.ThePreflightSectionTest.oneProcessHoldingBothFoldersIsSaidOnceInWordsItsTwoLinesTheCaption` | §3, §4: one live process's lines in both lock files, same pid, starts a second apart: rows corpus root, working directory and `holder`, *Both held by*, *KO*, *vespera run (pid ‹n›) since ‹HH:mm›*, its caption the corpus root's line, a line break and the working directory's |
| `ui.ThePreflightSectionTest.twoHoldersAreEachSaidBeneathTheirFolderEachLineItsCaption` | §1, §3: two live processes, a `run` holding the corpus root and another control panel's write the working directory: each holder's row beneath its folder's, *Held by*, *KO*, its words and its line whole as the caption; no `holder` row |
| `ui.ThePreflightSectionTest.aHolderLineThatCannotBeReadIsSaidHeldWithTheLineAsItsCaption` | §3: a first line not of the form in the working directory's lock: its holder's row reads *a process whose line cannot be read*, its caption the line |
| `ui.ThePreflightSectionTest.doclingsImageIsARowOnlyWhereTheCheckFoundItRunningAnotherImage` | §6: a check keeping an image other than the field's: the row, *KO*, its words and caption; a check keeping the field's image, and one keeping none, no row |
| `ui.ThePreflightSectionTest.theProfileWindowOffersTheModelsTheLastCheckFoundOllamaListing` | §7, §8: a check keeping two names, the Profile window's `generationModel` and `embeddingModel` choices offer both, in that order |
| `ui.HolderWordsTest.whoIsTheCommandsFirstWordThatIsNoOption` | §3: `run`, `--headless label`, `publish`, `control-panel write`, another command, an empty field and no field, each its who, with the pid |
| `ui.HolderWordsTest.theTimeIsTheStartAtTheSystemsZoneWithTheDateWhereItIsAnotherDay` | §3: a start the same day at another offset, its time at the zone given; a start the day before, its date and time |
| `ui.HolderWordsTest.aLineNotOfTheFormIsAProcessWhoseLineCannotBeRead` | §3 |
| `ui.TheCheckKeepsDoclingsImageAndOllamasNamesTest.whereDoclingIsOkItsVersionIsAskedOnceAndTheImageItNamesIsKept` | §6: Docling *OK*, `/version` asked once with GET, its `vespera-image` kept; an answer without it, or not 2xx, keeps none; Docling *KO*, `/version` not asked |
| `ui.TheCheckKeepsDoclingsImageAndOllamasNamesTest.whereOllamaIsOkTheNamesItListsAreKeptInItsOrder` | §7: Ollama *OK*, its names in the answer's order, `/api/tags` asked once; Ollama *KO*, none |

Amended:

- **`ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText`**'s walk reaches a preflight row's name *Corpus root*, the section's texts being a row's three and not a line of `Label: text`.

## What the implementation owes

Beside turning the tests above green: `PreflightPane`'s grid and rows, the Check and its thread gone (§1 to §6); `HolderWords` (§3); `DockerCheck`'s two components and constructor, `DockerControl`'s `/version` question and kept names (§6 to §8); `ControlPanelState.keepCheck` and `lastCheck`, `ServiceChecks`, `lastChecks` and `keepChecks` gone, `ProfileForm` and `WantedValues` reading the names from the last check, the cockpit keeping each check (§7, §8); the stylesheet's rules for `preflight-list`'s columns and `preflight-mark`'s `ok` and `ko`, in the header's two colours, in both themes (§2); `PreflightPane`'s and `DockerCheck`'s javadoc.
