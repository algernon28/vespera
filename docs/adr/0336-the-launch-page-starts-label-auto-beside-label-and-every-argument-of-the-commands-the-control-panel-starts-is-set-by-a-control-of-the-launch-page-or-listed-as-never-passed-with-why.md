# ADR-336 — The launch page starts `vespera label --auto` beside Label, and every argument of the commands the control panel starts is set by a control of the launch page or listed as never passed, with why

> **Partly amended — see [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md).** Of §1: the button is the window's `label-auto`, named Let the local model answer. Of §2: `control` names a control of the window by its id, `label <file>` is set by `label-file`, and the options of ADR-344 §4 are passed beside the table. Of §3: `label`'s `<file>` is offered. The rest of this record stands.

> **Partly amended — see [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md).** Of §1: the button `label-auto`, Have the local model answer, gives way to the top bar's box `label-auto`, Autolabel, Label with it ticked starting `label --auto` by the same checks; it is kept nowhere, and the next step's form `label-auto` goes. The rest of this record stands.

> **Partly amended — see [ADR-366](0366-the-window-shows-a-provenance-on-one-line-behind-edit-reason-a-list-as-tags-a-code-set-by-its-folder-and-no-label-file.md).** Of §2 and §3: `label <file>` is never passed, Command options offering no label file; the entries never passed are four, `vespera help`, `vespera version`, `serve db-dir` and `label <file>`. The rest of this record stands.

- **Date**: 2026-11-03
- **Status**: accepted on 2026-11-03.
- **Decided**: by the operator, that the control panel sets the options of the jar's commands too, `--auto` named among them; that `--auto` is started by a button of its own and not by a choice beside Label (§1); that the button is drawn where the working directory holds no label file (§1); that Publish is not added to the launch page (§3); and that the table names an option without its leading `--`, so that ADR-278 §1's scan of the control panel's texts stands as it is (§2). The rest, the button's name, its words and where it stands, the table that accounts for every option and positional argument, where it lives and the two tests that hold it, and which arguments stay unpassed, was decided by Claude for the operator, who asked for the feature without naming them.
- **Keeps**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6: `--auto` is an option of `label`, the deliberate act, and every refusal of it, no label file, answers not yet recorded, a labeller that is not local, is the jar's own, in its own line. [ADR-047](0047-the-pipeline-never-blocks.md): `vespera run` stays unattended and reaches no model through this record.
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1: the command lines are `run <corpus root> --db-dir=<working directory>`, `label [--auto] --db-dir=<working directory>` and, by [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §6, `publish --db-dir=<working directory>`, never `--help` or `--version`; one child at a time; and the control panel adds no command and no option to the jar. ADR-308 §6: Publish stays on the invocation page, and the five values a publish reads stay the operator's environment's.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the table of §2 is text in a class of the control panel's own, and names no class of the jar. [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md): the seven launch values stay seven, stored as an invocation starts. [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §4: `/next` reads the next act as before. [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): the screens are tested through the pages served, by names, ids and attributes.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §2, whose form `launch` has four submit buttons, `run`, `label`, `label-auto` and `check`, `label-auto` starting `label --auto` as `label` starts `label`, disabled when they are (§1); §8, whose form `label-auto` on `/next` stands, and is no longer the one place `label --auto` starts from (§1); and §9, whose row of `/` names the fourth button. It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §3, whose `#top-row` on the launch page holds Run, Label, Have the local model answer and Check (§1); and §6, whose refusals of `label --auto` land on `/next` where it is pressed there and on `/` where it is pressed on the launch page (§1). It carries a note at its head.

## Context

**What the jar's commands take.** picocli's model of `vespera` (`VesperaCommand`) holds, beside the three commands, these options and positional arguments, by an option's longest name and a positional's label:

| Command | Arguments |
| --- | --- |
| `vespera` | `--help`, `--version` (`mixinStandardHelpOptions`) |
| `run` | `<root>`, `--db-dir` (`WorkingDirectoryOption`) |
| `label` | `--auto`, `<file>`, `--db-dir` |
| `publish` | `--db-dir` |

**What the control panel passes.** `ChildCommand` builds `run` with the corpus root made absolute and `--db-dir` from the working directory, `label` with or without `--auto`, and `publish`, each from the launch values held (ADR-278 §1, ADR-292). The launch page's form `launch` starts `run` and `label` (ADR-302 §2). `label --auto` is started only from `/next`'s form `label-auto`, which stands only where the control panel's own child ended with 0 and `next-invocation.properties` says answers are wanted (ADR-294 §4, ADR-302 §8). So a control panel just started, one whose last child ended other than with 0 or was stopped, and one after a scoring run started from a terminal, offer no way to have the local model answer; the operator has to leave the control panel for a terminal to type `--auto`.

**What holds the arguments today.** `NothingShippedReachesConfluenceTest.theCommandHasRunLabelAndPublish` pins the options of the tree, for its own reason, that no option names a Confluence site or a credential (ADR-308). An option added to a command fails it, and nothing then says whether the control panel is to set the new option, nor how. Nothing ties the control panel's pages to the model at all.

## Decision

### 1. The launch page starts `label --auto`

**The form `launch` gains a fourth submit button, `label-auto`**, after `label` and before `check`, in `#top-row` with the three (ADR-331 §3), its words `Have the local model answer`, the words of `/next`'s button. The form's buttons are then, in order, `run`, `label`, `label-auto`, `check`.

**Pressed, it starts `label --auto` as `label` starts `label`** (ADR-302 §2): the post holds the seven values posted, as every post of `launch` does; then `ControlPanelState.start` is asked for `Start.LABEL_AUTO`, by the same checks in the same order as Label's, the launch values' own refusal (`Label needs the working directory`), a folder the system cannot hold and a working directory that is no folder (ADR-331 §6), the control panel's own child, `vespera.lock`, the corpus-root lock, each line beginning `Label is not started` as ADR-331 §6 already has it for `label --auto`. A refusal answers `303` to `/`, its line in `outcome`, starting nothing and storing nothing. A start stores the launch values (ADR-292 §2) and answers `303` to `/invocation`, whose `#child` names `label --auto`. The child's command line is ADR-278 §1's, `label --auto --db-dir=<working directory>`, with the environment of every child (ADR-292 §4).

**It is disabled exactly where `run` and `label` are**: while a holder line is held or the control panel's own child runs (ADR-302 §2, ADR-278 §4). `check` stays enabled.

**It is drawn whatever the working directory holds**, a working directory with no label file among them. The launch page reads no label file to offer Label either. Where there is no label file, or it holds answers not yet recorded, or the labeller is not local, the jar refuses `--auto` in its own line and exit 1 (ADR-197 §6), which the invocation page shows as `failed-or-refused` with that line.

**`/next`'s form `label-auto` stays**, `POST` to `/label-auto`, offered where answers are wanted, its refusals landing on `/next` (ADR-302 §8, ADR-331 §6). The two buttons start the same child by the same checks; they differ only in the page each stands on and the page a refusal lands on.

### 2. Every argument of the commands the control panel starts is accounted for

**`ChildCommand` holds the table**, in the control panel's own package, linking no class of the jar:

```java
/** One option or positional argument of picocli's model of the jar's commands, and how the control panel sets it. */
public record Argument(String command, String name, String control, String reason) {
}

/** Every option and positional argument of {@code vespera} and of each of its commands, each once. */
public static final List<Argument> ARGUMENTS;
```

- **`command`** is the name of the command the argument belongs to as picocli's model names it: `vespera` for the top command, `run`, `label` or `publish`.
- **`name`** is an option's longest name, `OptionSpec.longestName()`, **without its leading `--`**, as `help` for `--help` and `db-dir` for `--db-dir`; or a positional argument's label, `PositionalParamSpec.paramLabel()`, as the model gives it, as `<root>`. Every option of the tree has a long name.
- **`control`** is the name of the control or submit button of the launch page's form `launch` whose value or press sets the argument; **empty where the control panel never passes it**.
- **`reason`** is one sentence, never blank, saying how the control sets it or why it is never passed.

The table, in this order:

| `command` | `name` | `control` | `reason` |
| --- | --- | --- | --- |
| `vespera` | `help` | | Never passed (ADR-278 §1): it boots the context and takes the working directory's lock, and prints nothing the control panel reads. |
| `vespera` | `version` | | Never passed (ADR-278 §1): it boots the context, takes the lock and prints no version. |
| `run` | `<root>` | `vespera.corpus-root` | The corpus root field, made absolute. |
| `run` | `db-dir` | `vespera.working-dir` | The working directory field, made absolute; also the child's current directory. |
| `label` | `auto` | `label-auto` | Passed by Have the local model answer, here and on the next page; Label passes none. |
| `label` | `<file>` | | Never passed: label reads the label file the last run wrote in the working directory, which the Relevance page answers. |
| `label` | `db-dir` | `vespera.working-dir` | As for run. |
| `publish` | `db-dir` | `vespera.working-dir` | As for run; Publish stands on the invocation page. |

The words of `reason` are the implementation's, and say what the table says.

**Why `name` drops the `--`.** ADR-278 §1's scan, `ChildCommandTest.noClassOfTheControlPanelSpellsVersionOrHelp`, holds that no string text compiled under `io.algernon.controlpanel` contains `--help` or `--version`, so that no change can pass either to the child by accident. The table names both, to say they are never passed. Named without the prefix, `help` and `version`, the table holds neither text and the scan stands exactly as it is; the jar's-side test strips the prefix from `longestName()` before it compares. The prefix is dropped from every option's name, not from the two alone, so that one rule reads the whole table. The `--auto` and `--db-dir` the command line passes stay where they are, in the code that builds it (ADR-278 §1).

**Two tests hold it.**

- **The jar's side**, `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest`, reads picocli's model of `VesperaCommand`, built without its collaborators as `NothingShippedReachesConfluenceTest` builds it, every command of the tree and every option and positional argument of each, the standard help options included, each option by its `longestName()` with the leading `--` stripped, and holds the set of `command` and `name` equal to the table's, each entry once, every `reason` not blank, and the three entries with no control the three above.
- **The control panel's side**, `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.everyControlTheTableNamesIsOnTheLaunchForm`, holds that each `control` the table names is a control or a submit button of the form `launch` on `/`.

**So no argument is forgotten.** An option or a positional argument added to a command of the jar, or a command added to the tree with arguments of its own, fails the first test until the table holds it, with a control the launch page then draws, which the second test holds, or with none and the reason it is never passed. One removed or renamed fails it too. A control renamed or taken off the launch form fails the second. Either way the decision is written beside the code that builds the command line, in the change that adds the argument, and not left to a later reading of the command's help.

**What the table does not cover.** A property given as `--<key>=<value>` is configuration Spring reads, no option or positional argument of picocli's model, and the control panel passes none: the launch values reach the child through its environment (ADR-292 §4). The five values a publish reads are the operator's environment's (ADR-308 §6). Which commands the control panel starts is `ChildCommandTest`'s (ADR-308 §6).

### 3. No other argument is offered today

- **`run`**: `<root>` and `--db-dir`, each set by a launch field already. Nothing is added.
- **`publish`**: `--db-dir` alone, set by the working directory field. Nothing is added, and **Publish is not added to the launch page**: it stays on the invocation page once a rendering is drawn (ADR-308 §6).
- **`label`'s `<file>`**: not offered. The file `label` reads where none is given is the one the last run wrote in the working directory, which `/labels` answers by folder (ADR-334 §6) and ADR-295 §6 writes; a file elsewhere is one the operator wrote, given to `vespera label <file>` from a terminal. `--auto` with a file is refused as a usage error (ADR-197 §6), so the one option the launch page adds takes none.
- **`vespera`'s `--help` and `--version`**: never passed (ADR-278 §1).

### 4. The classes, and run ids

In `io.algernon.controlpanel`, linking no class of the jar:

- **`LaunchPages`**: the button `label-auto` in `#top-row`, disabled as `run` and `label` are, and its post mapped to `ControlPanelState.Start.LABEL_AUTO`, a refusal landing on `/`.
- **`ChildCommand`**: `Argument` and `ARGUMENTS`, as §2 gives them.

`ControlPanelState`, `InvocationPages` and `NextPages` do not change: `Start.LABEL_AUTO` and its checks are there already. No file under `src/main/java/io/algernon/vespera` changes, so no run id moves, and no dependency or plugin is added. The README's section on starting the control panel says what Have the local model answer does, written with this record's tests.

## Known limits

- **A `reason` is held to be there, not to be right**: a sentence that says the wrong thing passes both tests.
- **A control named is held to be drawn, not to set its argument.** That `label-auto` passes `--auto` is held by the screen test reading the child's command line; that the fields set `<root>` and `--db-dir` is `ChildCommandTest`'s.
- **`label-auto` is enabled where the working directory holds no label file**, and its press then starts a child the jar refuses, as Label's press does.

## Alternatives refused

- **A choice beside Label, `auto`, sent with Label's press.** It would be a value of the launch form beside the seven, which ADR-292 keeps seven, or one forgotten at each drawing of the page, so that Label from a page drawn with the box ticked would start `--auto` while the page shown later had it clear. One button starts one command line, as each of the form's buttons does.
- **`label-auto` on the launch page only where the next act asks for answers.** That is `/next`'s condition, read after the control panel's own child ended with 0 (ADR-294 §4); the launch page is where any command is started, whatever ended before.
- **Removing `/next`'s `label-auto`.** The next act leads there to the questions and to the model's answers (ADR-302 §8); both stay.
- **The table read off the jar as the control panel runs**, by `vespera --help` or by picocli's model. ADR-278 §1 never calls `--help`, and ADR-285 §3 links no class of the jar.
- **The table in a test alone.** The control panel's code would hold no record of how it sets an argument, and the one place an option is answered for would be a test's list.
- **The rule left to `NothingShippedReachesConfluenceTest`'s list of options.** It holds that no option names a site, and fails without saying what the control panel owes.
- **`label`'s `<file>` offered as a field.** It names a file outside what the control panel writes and reads, and `--auto` refuses one (§3).
- **`name` as picocli gives it, `--help` and `--version` among them.** The table would hold the two texts ADR-278 §1's scan forbids every class of the control panel. Relaxing the scan for `ChildCommand` would leave the class that builds the command line the one class free to spell them; building the two names at run time, from `-` repeated and a word, would keep the scan's letter and defeat its point, as a text could then reach the command line by a change no test reads. Dropping the prefix costs one strip in the jar's-side test.

## Consequences

- The operator has the local model answer from the launch page whenever Label could be pressed, without a terminal and whatever the last child did.
- An option or positional argument added to the jar's commands fails the build's tests until the control panel says how it sets it, or that it never does and why.
- The launch page's top row holds four buttons.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.everyOptionAndPositionalArgumentOfTheCommandTreeIsInTheControlPanelsTableOnce` | picocli's model of `vespera`, every command, option and positional argument, the help options included, each option's longest name without its leading `--`: the same set of command and name as `ChildCommand.ARGUMENTS`, each entry once (§2) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.theLaunchFormHoldsLabelAutoAfterLabelAndBeforeCheck` (removed with the control panel's server and pages, ADR-342 §6, #700) | the four buttons in order, in `#top-row`, none disabled with nothing held (§1) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.labelAutoStartsLabelWithAutoOnTheWorkingDirectory` (removed with the control panel's server and pages, ADR-342 §6, #700) | the press lands on `/invocation`; the child's arguments are `label --auto --db-dir=<working directory>`, absolute (§1) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.labelAutoIsDisabledWhileTheControlPanelsChildRunsAndRefusedFromAPageDrawnBefore` (removed with the control panel's server and pages, ADR-342 §6, #700) | a child running: disabled on a page drawn then; pressed on a page drawn before, back on `/` with ADR-302 §2's line, and no second child (§1) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.labelAutoIsRefusedWithTheLineOfTheValueItNeeds` (removed with the control panel's server and pages, ADR-342 §6, #700) | no working directory: back on `/` with `Label needs the working directory`, no child (§1) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.theNextPagesLabelAutoStaysAndStartsTheSameCommand` (removed with the control panel's server and pages, ADR-342 §6, #700) | after a run asking for answers, `/next`'s `label-auto` starts the same command line (§1) |
| `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` ([#700](https://github.com/algernon28/vespera/issues/700)) | each `control` of `ChildCommand.ARGUMENTS` a control of the window, as ADR-344 §4 leaves §2 |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy`, named `eachArgumentIsSetByALaunchControlOrNeverPassedAndSaysWhy` until ADR-339 | every `reason` not blank; `run <root>` by `vespera.corpus-root`, each `db-dir` by `vespera.working-dir`, `label auto` by `label-auto`; `vespera help`, `vespera version` and `label <file>` with no control, and no other (§2, §3); and, by ADR-339 §1, `publish to` by `to` on the form `publish`; and, by ADR-341 §2, `serve db-dir` a fourth with no control; and, by ADR-345 §4, `publish to` by `publish-to`, and `vespera help`, `vespera version` and `serve db-dir` the three never passed |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.everyControlTheTableNamesIsOnTheFormItsEntryNames` (removed with the pages, ADR-342 §6; held now by `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow`, ADR-345, #704), named `everyControlTheTableNamesIsOnTheLaunchForm` until ADR-339 | each `control` of `ChildCommand.ARGUMENTS` a control or a button of the form its entry names, `launch` or, by ADR-339 §1, `publish`; by ADR-340 §1 and §3, `launch-values` for the folder fields (§2) |

These change with this record, holding what they held otherwise: `controlpanel.LaunchScreenTest.theLaunchFormHoldsTheSevenLaunchValuesByTheirKeysAsLastStarted` (removed with the control panel's server and pages, ADR-342 §6, #700) reads the four buttons; its `runAndLabelAreDisabledWhileAnotherInvocationHoldsTheWorkingDirectory`, `runAndLabelAreDisabledWhileAnotherInvocationHoldsTheCorpusRoot` and `aSaveOfTheControlPanelsOwnLeavesTheWorkingDirectoryHeldByNobody` read `label-auto` disabled and enabled with `run` and `label`; `controlpanel.TheControlPanelsLayoutScreenTest.everyPageHasATopRowHoldingItsTitleAndTheLaunchPageItsThreeButtons` is `everyPageHasATopRowHoldingItsTitleAndTheLaunchPageItsFourButtons`, reading the four in `#top-row` and none of them in another page's; and `controlpanel.JarForScreenTests` gains `say-the-arguments `, before what the child does, printing its arguments as a line of its own. `NothingShippedReachesConfluenceTest`, `ChildCommandTest`, `NextScreenTest` and `FolderFieldStatusScreenTest.labelAutoRefusesACorpusRootTheSystemCannotHold` (removed with the control panel's server and pages, ADR-342 §6, #700) stand unchanged, `ChildCommandTest.noClassOfTheControlPanelSpellsVersionOrHelp` among them: its scan reads `ChildCommand`'s table and finds neither `--help` nor `--version`, since the table names no option with its `--` (§2).

## What this does not decide

- **A rule of the same kind for the child's environment**, the launch values and the variables a publish reads (ADR-292, ADR-308 §3).
- **The words of each `reason`**, beyond what §2's table says.
