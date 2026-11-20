# ADR-389 — The profile is edited in a window of its own, opened from the first section of the sidebar

> **Partly amended — see [ADR-413](0413-save-my-labels-and-auto-label-are-a-labels-section-of-the-sidebar-and-the-window-calls-no-label-an-answer.md).** Of §1: the sidebar is nine sections, `labels`, titled Labels and holding the two label commands, standing after `seed-folder`. The rest of this record stands.

> **Partly amended — see [ADR-414](0414-the-analysis-reports-open-in-a-reports-window-of-their-own-a-tab-each-and-the-cockpit-keeps-its-list-and-two-tabs.md).** Of §1: the tabs pane holds `live-report` and `inclusions` alone, no report's tab opening there. Of §2: a report link on a row opens the report's tab in the Reports window, a window of its own. The rest of this record stands.

> **Partly amended — see [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md).** Of §1: the cockpit holds no tabs pane, and the link from `foldersNotWalked` opens the sidebar's Folders section, as those from `seedFolder` and `codeSetFolders` open theirs. The rest of this record stands.

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Decided**: by the operator, on [#799](https://github.com/algernon28/vespera/issues/799): that Profile is a section of its own, the first of the sidebar, holding an **Open profile** button (§1); that there is one profile window, moved and resized apart from the cockpit, brought to the front where it is chosen again, its size and place kept until the control panel exits and saved nowhere, and its first size showing every row's title whole (§2, §3); that closing it with an edit unsaved asks **Save**, **Discard** or **Cancel**, and that closing the cockpit closes it, asking the same first where an edit is unsaved, and that the cockpit loading the working directory's files again while the window holds an edit unsaved asks the same first, **Save** writing and then loading them, **Discard** loading them, and **Cancel**, as a Save refused, keeping the edit and loading them all but the form, but that a change of the working directory with an edit unsaved asks **Save** or **Discard** alone, with no **Cancel**, **Save** writing the edit into the old working directory's `profile.yaml` and **Discard** dropping it, the dialog closed with no answer counting as **Save**, a Save refused there dropping the edit, the cockpit's `outcome` holding the refusal whole, and the new working directory's form loading after each (§2, §4); that the badge marks the section, and the window's title while it is open (§5); that the refusals naming the control panel's Profile tab name its Profile window, and that where the window shows one it offers a button opening the profile window (§6); and that Inclusions stays a tab, the tabs pane holding the Report tab and Inclusions (§1). The ids, the question's words, how the window knows a refusal names the profile window, the profile window's theme, the reading of *another folder* in §4 and the tests are this record's, consistent with ADR-344, ADR-381 and ADR-383.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose tabs pane holds *"**Profile** and **Inclusions**"*, whose sidebar is *"seven sections in this order"*, and whose badge is on *"the Profile tab while the next invocation wants a value"*: the form is the Profile window's, the sidebar eight sections, Profile first, and the badge the section's and the window's title's (§1, §5). It carries a note at its head.
- **Amends**: [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) §4, whose refusal for a missing value *"says to set it in the control panel's Profile tab"*: it says the Profile window (§6). It carries a note at its head.
- **Amends**: [ADR-372](0372-a-publish-the-control-panel-started-refuses-a-parent-named-twice-or-a-wrong-address-naming-the-profile-keys.md) §1, whose two lines end *"in the control panel's Profile tab, and publish again."*: they end *"in the control panel's Profile window, and publish again."* (§6). It carries a note at its head.
- **Amends**: [ADR-383](0383-a-parents-title-naming-no-page-or-several-stops-a-control-panel-publish-with-its-own-code-and-a-warning-asks-for-another.md) §1, whose two lines name *"the control panel's Profile tab"*; §3, whose two sentences say the title *"is saved as confluenceFolderTitle in the Profile tab"*; and §5, whose line says to name the parent *"in the Profile tab"*: each says the Profile window (§6). It carries a note at its head.
- **Keeps**: ADR-344 §1's form, every key a row, one **Save** and **Reset** (as [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) amends it), its rows' ids, `profile-`, and the links from `seedFolder`, `codeSetFolders` and `foldersNotWalked` to where each is edited; ADR-344 §7, closing the window while its child runs; [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) and [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §5, what a Save writes and refuses; [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md), the Inclusions tab; ADR-383 §3 to §5, the warning and what its answers do, but for their words; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar.
- **Read with**: [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §1, [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) and [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8, whose rules for the views of the control panel's window hold of the Profile window (§7).
- **Settles**: [#799](https://github.com/algernon28/vespera/issues/799).

## Context

**Where the profile is edited.** The form of every key of `profile.yaml` is the Profile tab, in the tabs pane beneath the charts, beside the Report tab and Inclusions (ADR-344 §1, ADR-362 §10). Selecting it hides the Report tab, and the form shares the window's one scroll with the steps, the output and the charts, so the profile cannot be kept in view while a run is watched. Between Run and Publish the operator goes back and forth to it, for the Confluence keys and `deliverableLanguage` among others.

**What names it.** A publish the control panel started, refused for a value of the profile, says where to set it: *"the control panel's Profile tab"* (ADR-359 §4, ADR-372 §1, ADR-383 §1); the window's own warning and line after a parent's title naming no page or several say *"in the Profile tab"* (ADR-383 §3, §5). The window shows the child's last line in `child`, and its own lines in `outcome` (ADR-344 §1).

**What a Stage is.** A JavaFX `Stage` is a window of the system's: moved, resized, minimized and closed by the operator apart from any other, an owner keeping it above its owner on some systems, and a modality blocking the windows beneath it. Its close button sends a close request, which a handler may consume to keep it open (ADR-344 §7 consumes the cockpit's).

## Decision

### 1. The sidebar and the tabs

**The sidebar is eight sections, in this order**: `profile`, `preflight`, `folders`, `services`, `seed-folder`, `code-sets`, `command-options`, `reports`, the seven of ADR-344 §1 after the first. **Profile**, `profile`, titled `Profile`, holds one button, `open-profile`, reading **Open profile**. Folders is still the section open as the window opens.

**The tabs pane holds `live-report` and `inclusions`**, in that order, and after them a tab for each report opened (ADR-362 §10). No tab is the profile's, and the cockpit's own scene holds no part of the form: no `profile-form`, `profile-save`, `profile-reset`, `profile-outcome` or `profile-row-<key>`. The link from `foldersNotWalked` still selects Inclusions, and those from `seedFolder` and `codeSetFolders` still open their sections of the sidebar, in the cockpit.

### 2. The Profile window

**Open profile opens the Profile window**: a `Stage` of its own, titled `Profile`, whose scene's root is the form ADR-344 §1 and ADR-377 draw, every id within it unchanged. It has **no owner and no modality**, so the cockpit and it are each moved, resized, minimized and brought in front of the other by the operator, and neither blocks the other; and it is resizable.

**There is one at a time.** Open profile pressed while the Profile window is open opens no other: it shows the one open again where it is minimized, brings it to the front and gives it the focus.

**The form is the one the cockpit loads.** It is loaded from the working directory's files whenever the cockpit loads them (ADR-344 §1), open or not, and a Save in it loads them again; but where the Profile window holds an edit unsaved, the cockpit asks first, and the form is loaded again only as §4 says. A report link on a row opens the report's tab in the cockpit.

### 3. Its size and place

**The Profile window's size and place are kept by the cockpit that opened it**, from its closing to its next opening, so it opens again where and as large as it was left; and **they are written nowhere**: no file, no preference of the user's and no launch value holds them, and a control panel started again opens it as at its first opening.

**At its first size every row's title is shown whole.** No title of the form's rows is cut: under [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §3's layout, unchanged, the titles' column is as wide as the widest title and at most 45 % of a row's width, so the window opens wide enough that 45 % of a row holds the widest title, `confluenceFolderId`'s among them, and no title's `Line` shows the "…" of ADR-377 §6. The size itself, and the first place, are the implementation's.

### 4. Closing it, and closing the cockpit

**An edit is unsaved** where a Save of the form would write a key: some row's value differs from what `profile.yaml` stated as the form was last loaded, by ADR-343 §2's table.

**Closing the Profile window with an edit unsaved asks**, before it closes, in a dialog over it titled `The profile`:

`The profile has changes not saved in profile.yaml.`

with three answers, in this order: **Save**, **Discard** and **Cancel**.

- **Save** saves the form as its Save does. Where it writes, the window closes; where it is refused, the window stays open, `profile-outcome` holding the refusal as a Save pressed would.
- **Discard** puts the form back to the file as last loaded, as Reset does, writes nothing, and the window closes.
- **Cancel**, or the dialog closed with no answer, writes nothing, changes nothing of the form, and the window stays open.

With no edit unsaved it closes at once, asking nothing.

**Closing the cockpit closes the Profile window.** Where the Profile window is open with an edit unsaved, the cockpit asks the same question, over the cockpit, before anything else: Save that writes, or Discard, goes on; Cancel, the dialog closed, or a Save refused keeps both windows open, asking nothing more. Then ADR-344 §7's question, where the child runs, is asked as before; and once the cockpit closes, the Profile window closes with it, asking nothing.

**Loading the files again with an edit unsaved asks too.** Whenever the cockpit is to load the working directory's files again while the Profile window holds an edit unsaved, for whatever reason but a change of the working directory (a Save of Seed folder, of the code sets, of Inclusions or of a value the next invocation wants, the parent's title saved from ADR-383 §3's warning, or any other), the cockpit asks the same question first:

- **Save** saves the form as its Save does. Where it writes, the files are loaded, the form with them.
- **Discard** puts the form back as Reset does, writes nothing, and the files are loaded, the form with them.
- **Cancel**, the dialog closed with no answer, or a Save refused, `profile-outcome` holding the refusal as a Save pressed would, keeps the form as it is, the edit in it, and changes nothing of it: the files are loaded for all but the form, the Seed folder's row, the code sets, the values the next invocation wants, Inclusions and the reports drawn from them as before.

With no edit unsaved the files are loaded asking nothing, the form with them. A Save of the form that writes leaves no edit unsaved, so the load it runs asks nothing.

**A change of the working directory with an edit unsaved asks Save or Discard alone.** Where the working directory's field names another folder while the Profile window holds an edit unsaved, the cockpit asks, over the cockpit, with the same title and the same sentence, two answers, in this order: **Save** and **Discard**. No **Cancel** is offered: the working directory is changed whatever the answer, and the form never shows one working directory's file while the cockpit names another.

- **Save**, or **the dialog closed with no answer**, saves the form as its Save does, but into the `profile.yaml` of the working directory the form was last loaded from, the old one, and writes nothing into the new one's.
- **A Save refused** there, by ADR-330 §5's checks or any other (the old `profile.yaml` changed since the form was loaded, gone with its folder among them, or no working directory named), writes nothing into either, drops the edit, and the cockpit's `outcome` holds the refusal, whole, the line the Save gave.
- **Discard** puts the form back as Reset does and writes nothing.

After each, the new working directory's files are loaded, the form with them: the working directory is changed whatever the answer and whatever the Save did. With no edit unsaved the new working directory's files are loaded asking nothing, as before. Every other load of the files keeps the three answers above.

**Another folder is another folder, not another spelling of the same one.** This record reads the operator's *another folder* so: the folder the field's new value names, an empty field naming the folder it stands for ([ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §1), and the folder the form was last loaded from are compared as absolute, normalised paths, a trailing separator, a `.` and a `..` among the names changing neither, and on Windows ignoring case, as its file system does. Where they are the same folder, nothing is settled at the change: no Save or Discard is asked, and the load of the files that follows is any other load, asking Save, Discard or Cancel where the Profile window holds an edit unsaved.

**`Dialogs.choose(title, sentence, answers)`** asks the question: it gives the words of the answer pressed, empty where the dialog is closed with none, by its close button or by Escape, whatever its last answer, and `Dialogs.NONE` answers empty. It takes the window's stylesheet and theme as every dialog of the window does (ADR-381 §1).

### 5. The badge

`Profile (needs a value)` is the section's title while the next invocation wants a value (ADR-294), and `Profile` otherwise, as every other section carries its badge (ADR-344 §1); and **the Profile window's title** is the same words, following as the section's does while the window is open.

### 6. The refusals that name it

**Each line of the jar that named *"the control panel's Profile tab"* names *"the control panel's Profile window"***, the rest of each line unchanged. Started by the control panel, `ConfluenceSite.of` refuses, whole:

| What is refused | The line |
| --- | --- |
| a value missing | `vespera publish sent nothing: <keys> is not set. Set it in the control panel's Profile window, and publish again.`, and with several keys `<keys> are not set. Set each key in the control panel's Profile window, and publish again.`, as ADR-359 §4 words the rest |
| the parent named both by its id and by its title | `vespera publish sent nothing: the parent is named both by confluenceFolderId and by confluenceFolderTitle. Clear one in the control panel's Profile window, and publish again.` |
| an address that is not a Cloud site's | `vespera publish sent nothing: confluenceBaseUrl is not the https address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user, query or other path. Correct it in the control panel's Profile window, and publish again.` |

and `Publisher` stops, whole, with code 3 and 4 as ADR-383 §1 has it:

| The parent's title names | The line |
| --- | --- |
| no page or folder of the space | `vespera publish stopped: no page or folder of the space <space> is titled <title>, and the parent must be exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in the control panel's Profile window. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.` |
| several pages or folders | `vespera publish stopped: <n> pages or folders of the space <space> are titled <title>, and the parent must be exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in the control panel's Profile window. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.` |

**The window's own words** say the Profile window: the warning's two sentences of ADR-383 §3 end `it is saved as confluenceFolderTitle in the Profile window, and the publish starts again.`, and its line of ADR-383 §5 is, whole:

`the publish to Confluence sent nothing: confluenceFolderTitle is left as profile.yaml states it; name the parent by confluenceFolderTitle or confluenceFolderId in the Profile window, and publish again`

On the command line nothing changes: no line names the control panel (ADR-372 §2, ADR-383 §2).

**Where the window shows such a line it offers the Profile window.** A button, `outcome-open-profile`, reading **Open profile**, stands beside `outcome`, shown and laid out only while the text of `child` or of `outcome` holds the words `Profile window`, and hidden otherwise; pressed, it does what `open-profile` does. The window knows such a line by those words alone: the jar's refusals reach it as text, the control panel naming no class of the jar (ADR-285 §3), and a code would not tell a value missing from any other stop, every one exiting 1 (ADR-333 §6). The control panel holds the words as text of its own.

### 7. Its theme, its texts and its contrast

The Profile window is a view of the control panel's, and ADR-381 §1, ADR-374 and ADR-364 §8 hold of it as of the cockpit: **its root carries the cockpit's stylesheet and takes the `dark` style class at once whenever the cockpit's root does, and loses it with it**, nothing reopened, a popup showing over it following as one over the cockpit does; every text of it, in either theme, stands at 4.5 : 1 or more against what is drawn behind it; and every text it shows outside a control is a `SelectableText`, a wheel over one scrolling the form.

### 8. The modules, and run ids

`ui` gains `ProfileWindow`, which opens, brings to the front, keeps the size and place of and closes the one Profile window, and `Dialogs.choose`; `Cockpit` gains the section, the button beside `outcome` and the badge of the window's title, and its tabs pane loses the Profile tab; `ControlPanelWindow`'s handler of the cockpit's close request asks §4's question first, and the cockpit's load of the working directory's files asks it first where the Profile window holds an edit unsaved, with Save and Discard alone where the working directory names another folder, the same folder spelled another way asking as any load, a Save or no answer then writing into the old one and a Save refused said in `outcome`; and `ProfileWindow`'s first size shows every row's title whole. `confluence` changes the words of the lines §6 gives, `ConfluenceSite.of`'s and `Publisher`'s. No stage's version names `confluence` or the control panel (ADR-383 §7), so **no run id moves**.

## Consequences

- The profile stays in view while a run is watched, the cockpit's Report tab selected beside it.
- A publish refused for a value of the profile is put right from the line that says so, without looking for where the form is.
- `profile.yaml` is written only by a Save, of the form or of §4's question; an edit is not lost by closing either window, or by the cockpit loading the files again, without being asked.
- After Cancel at a load, the form shows the file as it was last loaded while the rest of the cockpit shows it as it is now; a later Save of the form is checked against the file as ADR-330 §5 has it, and refuses a value the file has changed since the form was loaded. Cancel is never offered at a change of the working directory, so the form and the cockpit always name the same working directory.
- An edit kept by Save at a change of the working directory is in the old working directory's `profile.yaml`, which the form no longer shows; it is seen again by naming the old working directory.
- At a change of the working directory an edit is dropped only by Discard or by a Save refused: the dialog closed with no answer keeps it, by a Save. An edit whose Save is refused there is lost, not kept for later, and `outcome` says why; it is made again by naming the old working directory and editing it there.
- The Profile window opens wider than 780 pixels, at which 45 % of a row cuts `confluenceFolderId`'s title, and so takes more of a small screen at its first opening; made narrower by the operator, its titles are cut with "…" as ADR-377 §6 has it.
- ADR-344, ADR-359, ADR-372 and ADR-383 carry a note at their heads pointing here.

## Alternatives refused

- **A button inside an existing section, Preflight or Folders.** The operator chose a section of its own, first.
- **Several Profile windows, or one opened afresh each time.** Two forms over one file would each refuse the other's Save (ADR-330 §5).
- **The cockpit as its owner.** An owned window is held above its owner on some systems, so the cockpit could not be brought in front of it, and is minimized with it; the window is to be moved apart from the cockpit.
- **A modal window.** It would block the cockpit while the profile is open, which is what the change is for.
- **Its size and place kept in the launch values file or the user's preferences.** The operator chose them kept until the control panel exits and saved nowhere.
- **Closing with an edit unsaved and keeping the edit in memory for the next opening.** A form holding values the file does not state would be saved later by a Save meant for another key.
- **Knowing a refusal by the publish's exit code.** Every refusal of a value exits 1, as every other stop does (ADR-333 §6); ADR-383 gave codes of their own to the two stops the window acts on, and the button acts on nothing but opening a window.
- **Moving Inclusions too.** The operator kept it a tab.

## What this does not decide

- **The Profile window's first size and place**, beyond its showing every row's title whole (§3), and its stylesheet's sizes and spaces, the implementation's under ADR-342 §3.
- **Whether a link of a row to the cockpit brings the cockpit to the front.**
- **A button opening the Profile window from the output panel, a report's view or the Report tab's closing line**, which show the child's lines too.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheProfileWindowTest.theProfileIsTheSidebarsFirstSectionAndOpensOneWindowOfItsOwn` | §1, §2: the sidebar's eight sections in order, `profile` first, titled `Profile` and holding `open-profile`; the tabs `live-report` and `inclusions` alone and no `profile-form` in the cockpit; Open profile opens one more window, with no owner and no modality, resizable, titled `Profile` and holding `profile-form`; pressed again with that window minimized, it opens no other, and the one is shown and no longer minimized |
| `ui.TheProfileWindowTest.itsPlaceAndSizeAreKeptUntilTheControlPanelExitsAndWrittenNowhere` | §3: moved and resized, closed and opened again, the window is where and as large as it was left; and a second control panel over the same launch values file opens it elsewhere or at another size |
| `ui.TheProfileWindowTest.atItsFirstSizeEveryRowsTitleIsShownWhole` | §3: opened and not resized, every key's row is there and no row's `<key>.title` shows its "…"; made 500 pixels wide, some title shows it, so that the reading can see a title cut |
| `ui.TheProfileWindowTest.closingTheProfileWindowWithAnEditUnsavedAsksSaveDiscardOrCancel` | §4: Cancel keeps it open, the edit and the file as they were; Save writes the value with `set in the control panel` and closes it; Discard writes nothing, closes it and the form opens again as the file states; Save refused, the file changed since the form was loaded, keeps it open with the refusal and the file as changed; with no edit it closes asking nothing; each question with §4's title, whole sentence and three answers in order |
| `ui.TheProfileWindowTest.closingTheCockpitClosesTheProfileWindowAskingFirstWhereAnEditIsUnsaved` | §4: with an edit unsaved, Cancel keeps both windows open and Save writes it and closes both; Discard closes both, writing nothing; with none, closing the cockpit closes both, asking nothing |
| `ui.TheProfileWindowTest.loadingTheFilesAgainWithAnEditUnsavedAsksSaveDiscardOrCancelFirst` | §2, §4: an edit unsaved in the Profile window and `profile.yaml` changed meanwhile by another program, Seed folder's Save, which loads the files again, pressed in the cockpit: Cancel asks once and keeps the edit and the form as loaded, the file as the other program wrote it, while Seed folder's row shows the seed folder the file now states; Save writes the edit with `set in the control panel` and the form shows the file after it; a Save refused keeps the edit, `profile-outcome` holding the refusal, and the form as loaded; Discard writes nothing and the form shows the file; each asked once with §4's title, sentence and three answers in order; and with no edit the form shows the file, asking nothing |
| `ui.TheProfileWindowTest.changingTheWorkingDirectoryWithAnEditUnsavedAsksSaveOrDiscardAndLoadsTheNewOne` | §4: an edit unsaved and another working directory named in its field: Save writes the edit with `set in the control panel` into the old working directory's `profile.yaml`, nothing into the new one's, and the form shows the new one's value; a second edit and a third working directory named: Discard writes nothing into either, and the form shows the third's value; each asked once with §4's title and sentence and the two answers Save and Discard, in that order; and with no edit, the first working directory named again, nothing is asked and the form shows the value Save wrote there |
| `ui.TheProfileWindowTest.changingTheWorkingDirectoryWithTheQuestionClosedWithNoAnswerSavesTheEdit` | §4: an edit unsaved, another working directory named, and the dialog closed with no answer: asked once with Save and Discard alone, it writes the edit with `set in the control panel` into the old working directory's `profile.yaml`, nothing into the new one's, and the form shows the new one's value |
| `ui.TheProfileWindowTest.aSaveRefusedAtAChangeOfTheWorkingDirectoryDropsTheEditAndSaysTheRefusalInTheOutcome` | §4: an edit unsaved, the old `profile.yaml` changed meanwhile by another program, another working directory named, and Save: asked once with Save and Discard alone, it writes nothing into the old one, as the other program left it, or the new one, the form shows the new one's value, and `outcome` holds the refusal whole |
| `ui.TheProfileWindowTest.namingTheSameWorkingDirectoryAnotherWayAsksSaveDiscardOrCancelAsAnyLoad` | §4: an edit unsaved, and the working directory the form was loaded from named again with a trailing separator, then through a sibling's `..` and a `.`, each answered Cancel: each asked once with §4's title, sentence and three answers in order, never Save and Discard alone; the file as the test wrote it and the edit kept in the form |
| `ui.TheProfileWindowTest.emptyingTheFieldWhereItStandsForTheSameFolderAsksSaveDiscardOrCancelAsAnyLoad` | §4: an edit unsaved, the folder an empty field stands for being the one the form was loaded from, and the field emptied, answered Cancel: asked once with §4's title, sentence and three answers in order, never Save and Discard alone; the file as the test wrote it and the edit kept in the form |
| `ui.TheProfileWindowTest.onWindowsTheWorkingDirectoryNamedInOtherCaseAsksSaveDiscardOrCancelAsAnyLoad` | §4, on Windows alone, where the temporary folder's file system finds the folder by its name in capitals: the same as above for the working directory's name in capitals, asked once with three answers |
| `ui.TheProfileWindowTest.theQuestionGivesTheAnswerPressedAndNoneWhereItIsClosedWithNoButtonPressed` | §4: `Dialogs.of`'s `choose` on the Headless platform, over a stage of the test's, with Save and Discard and with Save, Discard and Cancel: pressing each answer's button closes the dialog and gives its words, in order; a close request, then Escape, with no button pressed, closes it and gives empty, not the last answer |
| `ui.TheProfileWindowTest.theSectionAndTheWindowsTitleCarryTheBadge` | §5: before the next invocation wants a value the section and the open window read `Profile`; once it wants one, both read `Profile (needs a value)` |
| `ui.TheProfileWindowTest.aLineNamingTheProfileWindowOffersAButtonThatOpensIt` | §6: no `outcome-open-profile` shown before; a child ending with ConfluenceSite's whole line for a parent named twice, shown in `child`, shows it, and pressing it opens the Profile window; and `outcome` holding §6's line of the window's own shows it too |
| `ui.TheProfileWindowTest.theProfileWindowFollowsTheThemeSwitch` | §7: the window's root carries the cockpit's stylesheet, and takes and loses `dark` as the switch moves the cockpit, while it is open |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.everyTextOfTheWindowAndEveryMarkOfItsChartsStandsAtAaInBothThemes` | §7: amended so that the Profile window, open, is measured in both themes beside each section and tab |
| `ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | §1, §7: amended so that the walk reaches the Profile window's rows and its problem line, the tabs read Report and Inclusions, the sections Profile first, and a wheel over a row's description scrolls the Profile window's form |
| `ui.WhereTheModelsRunIsShownAndNotEditedTest.theRowShowsItsValueGreyedOutAndASaveWritesNothingOfIt` | §2: amended to read the row and press Save in the Profile window |
| `ui.TheProfileRowIsOneLineTest.everyControlOfTheProfileWindowStartsAtOneEdge` | §3: amended to measure ADR-377 §3's one edge in the Profile window at its first size, not made 1500 by 960 first; that no title is cut at that size is `atItsFirstSizeEveryRowsTitleIsShownWhole`'s claim, and leaves this test |
| `ui.TheLiveReportTest.theReportTabShowsTheInvocationNatively` (retired by ADR-432, #914), `ui.TheAnalysisReportsOpenNativelyTest.anAnalysisReportsLinkOpensItsNativeViewInATabOfTheWindow` and `.aJsonThatCannotBeReadOrIsOfAnotherFormatOpensNothing` | §1: amended to the tabs `live-report` and `inclusions`, a report's tab third |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.fromTheControlPanelTheRefusalNamesTheProfileWindowsKeysAndWritesNoTemplate` and `.fromTheControlPanelAParentNamedTwiceAndAnAddressNotASitesNameTheProfilesKeys` | §6: the refusals name the Profile window, the two of ADR-372 whole |
| `confluence.PublishUpdatesOneTreeInPlaceIT.fromTheControlPanelATitleNamingNoPageOrTwoStopsWithItsOwnCodeNamingTheProfilesKeys` | §6: the two stops' whole lines name the Profile window |
| `ui.PublishToConfluenceAsksForTheTokenTest.aTitleNamingNoPageAsksForAnotherSavesItAndPublishesAgain` and `.aTitleNamingSeveralAsksTooAndCancelOrBlanksSaveAndStartNothing` | §6: the warning's sentences and the Cancel line, whole, name the Profile window |

A popup over the Profile window following the switch is held by no test of its own: ADR-381's test holds the rule over the windows `ThemeSwitch` restyles, and the Profile window is restyled by the same rule. That Open profile brings the window to the front and gives it the focus is held by no test: the Headless platform reports every window focused and keeps no order of windows a test can read; that it shows the window again where it is minimized is held.

## What the implementation owes

Beside turning the tests above green: **in `ui`**, `ProfileWindow`, `Dialogs.choose` in `Dialogs.NONE` and in `Dialogs.of`, drawn as a dialog of three buttons in §4's order over the window it is asked from, a close request or Escape with no button pressed giving empty, the section, `outcome-open-profile`, the badge of the window's title, the cockpit's close request of §4, the question of §4 asked before the cockpit loads the files again, with Save and Discard alone where the working directory names another folder, compared with the folder the form was last loaded from as §4 says, a Save there, or the dialog closed with no answer, writing into the old working directory, and a Save refused there dropping the edit, the new working directory's files loaded all the same and `outcome` set to the refusal whole, the Profile window's first size showing every row's title whole, and the window's own words of §6; **in `confluence`**, the five lines of §6 and the javadoc of `ConfluenceSite.of` saying so; **documents**, the README's paragraphs on the Profile tab, on the Reports section and on publishing from the control panel, which say *Profile tab*, and `docs/architecture.md`'s paragraph on the control panel, saying the profile is edited in the Profile window, opened from the first section of the sidebar.
