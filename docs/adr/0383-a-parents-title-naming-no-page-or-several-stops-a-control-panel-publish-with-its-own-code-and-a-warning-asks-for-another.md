# ADR-383 — A parent's title naming no page or several stops a control panel publish with its own code, and a warning asks for another

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator, on 2026-11-12: that where a publish the control panel started stops because the parent's title names no page or folder of the space, or several, the window opens a warning asking for a different title. Under the operator's delegation of the same date, Claude decided the rest, choosing in each the simplest design consistent with ADR-359 and ADR-372: how the window knows the stop, by an exit code of its own and not by the words of a line, and the child's line, naming the profile's keys and the Profile tab (§1); the warning's words and its field (§3); what a title entered does, the token asked for again (§4); what Cancel does (§5); and where each part lives (§6).
- **Amends**: [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) §3, whose *"a title naming no page or folder, or more than one, says to name the parent by `VESPERA_CONFLUENCE_FOLDER_ID`"*: it does so on the command line; started by the control panel it names `confluenceFolderTitle`, `confluenceFolderId` and the Profile tab (§1). It carries a note at its head.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §6, whose invocation *"exits 1 if any stopped"*: it exits with the code a destination's stop carries where one carries a code of its own, 3 or 4, and 1 otherwise; and its `Plugin.Delivery`, which gains that code (§1, §6). It carries a note at its head.
- **Amends**: [ADR-372](0372-a-publish-the-control-panel-started-refuses-a-parent-named-twice-or-a-wrong-address-naming-the-profile-keys.md)'s *What this does not decide*, whose refusal of a parent's title naming no page or folder, or more than one, is decided here. It carries a note at its head.
- **Keeps**: [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) §2 and §3: a publish the control panel starts inherits no `VESPERA_CONFLUENCE_*` variable and reads the parent from the profile; Publish asks for the token each time, and the token is held for the one start and in no state of the control panel once the child is started; ADR-359 §6: the masked field is no authentication. ADR-372 §1 and §2. [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §2, as [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) amends it: a value the window changes is saved with `set in the control panel`, one left as the file states it is not written. [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md): the window writes `profile.yaml` holding the working directory's lock. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names no class of the jar and opens no connection to Confluence; and its §5, as [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §5 has it: an exit code other than 0 and 2 of a child not stopped by Stop is shown as failed or refused, with its code. [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the process exits with the command's code. [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §1: a dialog of the window takes its theme. [ADR-378](0378-each-deliverable-language-is-a-tree-of-its-own-its-titles-carrying-its-code-and-an-earlier-tree-taken-by-its-heading.md): one parent named for a publish, whichever language it sends, and looked up before any write.

## Context

**Where the parent is looked up.** A publish names its parent by its id or by its title, one and not both (ADR-308 §3, ADR-359 §1). Named by its title, the parent is looked up once the publish has reached the site and read the space: `Publisher`'s `parent` asks the site for the pages and folders of the space so titled, and where there is not exactly one it stops the publish before anything is written, in one line, whoever started it:

- `vespera publish stopped: no page or folder of the space <space> is titled <title>, and the parent must be exactly one; name it by VESPERA_CONFLUENCE_FOLDER_ID. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.`
- `vespera publish stopped: <n> pages or folders of the space <space> is titled <title>, and the parent must be exactly one; name it by VESPERA_CONFLUENCE_FOLDER_ID. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.`

The invocation exits 1, as for any destination that stopped (ADR-333 §6).

**What a publish the control panel starts can read.** Its child inherits no `VESPERA_CONFLUENCE_*` variable but the token typed, so the parent is the profile's `confluenceFolderTitle` or `confluenceFolderId`, which the Profile tab sets (ADR-359 §3). The line names a variable that child cannot have read, says nothing of where the title it read is set, and the window shows it as the child's ending, failed or refused, exit 1, like every other stop (ADR-285 §5). ADR-372 named the profile's keys in the two refusals `ConfluenceSite.of` makes before anything is sent, and left this one, made at the site, undecided.

**How the window reads its child.** It reads the child's output to its end and its exit code (ADR-278); its last line that is not blank and not the report line is the command's closing line (ADR-281), which with several destinations is the last destination's (ADR-333 §6). It reads `report.json` for the steps and the charts on a clock of its own (ADR-362 §8). It names no class of the jar (ADR-285 §3).

## Decision

### 1. Started by the control panel, the stop names the profile's keys and the publish exits 3 or 4

Where the publish's environment holds `VESPERA_STARTED_BY` set to `control-panel`, and the parent's title names no page or folder of the space, or several, Confluence's destination stops with these lines, each whole, and the publish exits with the code beside it, in place of 1:

| The parent's title names | The line | Exit code |
| --- | --- | --- |
| no page or folder of the space | `vespera publish stopped: no page or folder of the space <space> is titled <title>, and the parent must be exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in the control panel's Profile tab. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.` | 3 |
| several pages or folders | `vespera publish stopped: <n> pages or folders of the space <space> are titled <title>, and the parent must be exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in the control panel's Profile tab. It had created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.` | 4 |

`<space>` is the space's key, `<title>` the title as read and `<n>` how many pages and folders hold it; the sentence after the first `.` is what the publish had done, as ADR-320 §4 has every stop say it, and the parent is looked up before anything is written, so it says nothing was. Neither line names a `VESPERA_CONFLUENCE_*` variable.

- **The code, and not the line, is what the window reads** (§3). 0, 1 and 2 are taken: a publish delivered, a stop, and a usage refusal, which the window shows as a defect of its own (ADR-285 §5). 3 and 4 are the next. A code is read whole, where the window already reads how its child ended; a line is prose, whose words a later record may change, and with several destinations the closing line is the last destination's.
- **The code is the publish's whatever the other destinations did.** Where Confluence's destination so stops, the publish exits 3 or 4 though the website was delivered after it, or stopped too: each destination still says how it ended in its own line (ADR-333 §6). Only Confluence's destination stops with a code of its own.
- **`report.json` records the code exited**, its publish `stopped`, as it records every exit (ADR-362).

### 2. On the command line, nothing changes

Where `VESPERA_STARTED_BY` is not `control-panel`, the two lines are the Context's, word for word, and the publish exits 1, as ADR-372 §2 keeps the command line's other lines and for its reasons: a command-line operator sees no change.

### 3. The window warns, asking for a different title

**Where.** Where the child Publish started ends by itself, not by Stop, with exit code 3 or 4, the window shows the ending as it shows any other (ADR-285 §5), and then opens one warning over itself, once for that ending. A child of Run or Label, an ending by Stop, and another invocation holding the working directory open none.

**What it says.** It reads `confluenceSpaceKey` and `confluenceFolderTitle` from the working directory's `profile.yaml` as it opens, as the Profile tab reads them, which are what the child read (ADR-359 §2). Where the file cannot be read then, or states no title, no warning opens, and the ending stands as shown. The warning's title is `The parent's title`, and its sentence, whole:

| Exit code | The sentence |
| --- | --- |
| 3 | `No page or folder of the space <space> is titled <title>, so the publish to Confluence sent nothing. Enter a different title for the parent the pages are published under: it is saved as confluenceFolderTitle in the Profile tab, and the publish starts again.` |
| 4 | `Several pages or folders of the space <space> are titled <title>, so the publish to Confluence sent nothing. Enter a different title for the parent the pages are published under: it is saved as confluenceFolderTitle in the Profile tab, and the publish starts again.` |

Beneath it stands one field, holding the title refused, so that a title mistyped is put right in place, and one the operator has since given a page on the site is published to as it stands; and two buttons, **Publish** and **Cancel**. It carries JavaFX's sign of a warning, and takes the window's light or dark theme as every dialog of the window does, following the switch while it shows (ADR-381 §1). It shows nothing of the token.

### 4. A title entered is saved, and the publish starts again

What Publish answers, its white space around removed, is the title entered; a title of blanks alone is no title, and is Cancel (§5).

- **It is saved as `confluenceFolderTitle`** in `profile.yaml`, as the Profile tab saves a key: holding the working directory's lock (ADR-295), refused as the tab's Save refuses a value, and with the provenance ADR-343 §2 gives as ADR-377 amends it, `set in the control panel` for a title changed; a title as the file states it is not written. No other key is written: a profile naming the parent by its id never reaches this stop, and one naming it by both is refused before the site is reached (ADR-372 §1). The Profile tab is loaded again from the file, so it shows the title saved.
- **A save refused starts nothing**: the window's `outcome` line holds the refusal.
- **Then the publish starts again, as Publish pressed starts it**: the window's values and the destinations ticked as they stand, and **the token asked for again** in the masked field. The token is not kept for the second start: ADR-359 §3 holds it for the one start and in no state of the control panel once the child is started, and refuses the control panel remembering it between publishes; held across the warning, it would be held for as long as the warning stood open. The masked field cancelled, or answered with blanks, starts nothing and `outcome` says so as ADR-359 §3 has it; the title stays saved.
- **The publish started again is a publish like any other**: where its parent's title names no page or folder, or several, the window warns again.

### 5. Cancel saves nothing and starts nothing

Cancel, or a title of blanks alone, writes nothing and starts nothing; the child's ending stays shown, and the window's `outcome` line is, whole:

`the publish to Confluence sent nothing: confluenceFolderTitle is left as profile.yaml states it; name the parent by confluenceFolderTitle or confluenceFolderId in the Profile tab, and publish again`

### 6. Where each part lives

- **`confluence`**: `Publisher`'s lookup of the parent tells a title naming none from one naming several, and `publish` gives §1's line and code where the control panel started it and the Context's line and 1 otherwise, branching on `VESPERA_STARTED_BY` as `ConfluenceSite.of` does (ADR-372 §1). `Publisher.Outcome` carries the code.
- **`deliverable`**: `Plugin.Delivery` gains a third component, `int exitCode`, the code the publish exits with for this destination; its two-component constructor stays, giving 0 to a delivery that went to its end and 1 to one that stopped, so the website's plugin is unchanged.
- **`pipeline`**: `VesperaCommand.Publish` exits with the code of the first destination, in the order delivered, whose stop carries a code other than 0 and 1; else 1 where any stopped; else 0.
- **`ui`**: `Dialogs.warnAndAsk(title, sentence, initial)`, giving what is typed, or empty where the warning is cancelled, `Dialogs.NONE` answering empty; the window's opening of it after its own Publish child's ending, outside the clock's pulse, since JavaFX refuses a dialog that waits during an animation; its save of the title and its start of the publish again through Publish's own path. The control panel holds 3 and 4 as text of its own, naming no class of the jar (ADR-285 §3).

### 7. Run ids

No stage reads the parent, the exit code of a publish or anything the window writes, and no stage's version names `confluence`, `deliverable`'s plugin seam, `pipeline` or the control panel for it (ADR-359 §8, ADR-274 §5), so **no run id moves**.

## Known limits

- **A parent's title set as the property `vespera.confluence.folder-title` in a file of configuration the child reads** comes before the profile's (ADR-359 §2): the title saved is not the one read, and the warning opens again for the same title until it is cancelled.
- **The warning names the space and the title `profile.yaml` states as it opens**: a file changed while the publish ran is named as it then stands.
- **A child ended with exit code 3 or 4 by another program** is taken for this stop.

## Alternatives refused

- **A fixed line the child prints, read by the window**: a decision taken on the words of a line, which a later change of its words would undo unseen; and with several destinations the closing line is the last destination's.
- **A field of `report.json` read by the window**: the report is read for the progress view on a clock of its own (ADR-362 §8), and a field would grow its schema for one stop, where the exit code is read with the ending.
- **One code for both stops**: the warning could not say whether no page holds the title or several do.
- **The codes on the command line too**: a command-line operator sees no change, as ADR-372 §2 keeps the command line's lines.
- **The token kept for the second start**: ADR-359 §3 and its refused alternative *The control panel remembering the token between publishes*.
- **An empty field**: a title wrong by one letter would be typed again whole, and one the operator has since given a page could not be published to as it stands.
- **The warning offering the parent's id as well**: the operator's decision asks for a title; the line and the Cancel line name `confluenceFolderId`, set in the Profile tab.
- **The window listing the pages of the space to choose from**: it opens no connection to Confluence (ADR-285 §3).

## Consequences

- A publish started from the control panel names no `VESPERA_CONFLUENCE_*` variable in any stop of its parent's lookup by title, and exits 3 or 4 for it.
- The README's section on publishing from the control panel says that a parent's title naming no page, or several, opens a warning asking for another, which is saved in the Profile tab and published to.
- ADR-318, ADR-333 and ADR-372 carry a note at their heads pointing here.

## What this does not decide

- **A warning for a parent named by an id the site holds no page or folder for, or one not in the space**, which the publish stops for naming no variable.

## Tests

| Method | What it holds |
| --- | --- |
| `confluence.PublishUpdatesOneTreeInPlaceIT.fromTheControlPanelATitleNamingNoPageOrTwoStopsWithItsOwnCodeNamingTheProfilesKeys` | §1: started by the control panel, the profile naming the parent by a title no page holds, and by one a folder and a page both hold, Confluence's delivery stops with §1's whole line and code 3, and 4, sending nothing but reads |
| `confluence.PublishUpdatesOneTreeInPlaceIT.fromTheCommandLineATitleNamingNoPageOrTwoStopsAsBefore` | §2: typed on the command line, the same two stop with the Context's whole lines and code 1 |
| `pipeline.PublishToEachDestinationInTurnInvocationTest.aStopWithAnExitCodeOfItsOwnEndsThePublishWithIt` | §1, §6: Confluence's stop carrying 3, the website is still delivered after it and the publish exits 3 |
| `ui.PublishToConfluenceAsksForTheTokenTest.aTitleNamingNoPageAsksForAnotherSavesItAndPublishesAgain` | §3, §4: a Publish child ending 3 opens one warning, §3's title and whole sentence naming the space and the title, its field holding the title; the title entered is saved as `confluenceFolderTitle` with `set in the control panel`, the space as it was; the token is asked for again; and the publish started again reads the title entered and is given the token |
| `ui.PublishToConfluenceAsksForTheTokenTest.aTitleNamingSeveralAsksTooAndCancelOrBlanksSaveAndStartNothing` | §3, §5: a Publish child ending 4 warns with §3's second sentence; cancelled, then answered with blanks, `outcome` holds §5's whole line each time, no publish is started again, and `profile.yaml` is byte for byte as it was |

The first two publish to the in-process stub, tagged `confluence`, which CI skips (ADR-320 §6). The window's tests start a jar of the tests' own, `ATitleCheckingChildForTests`, which reads the title in `profile.yaml` as the jar does and exits 3, 4 or 0 as the jar would.

## What the implementation owes

Beside turning the tests above green: **in `confluence`**, `Publisher`'s lookup and `publish` giving §1's lines and codes and the Context's lines and 1, and `Publisher.Outcome`'s code, its javadoc saying so; **in `deliverable`**, `Plugin.Delivery`'s `exitCode` and its two-component constructor; **in `pipeline`**, `VesperaCommand.Publish`'s exit of §6; **in `ui`**, `Dialogs.warnAndAsk`, in `Dialogs.NONE` and in `Dialogs.of` drawn as a warning with the field and the two buttons of §3, the window's warning, save and start again of §3 to §5, and the two codes as its own text; **documents**, the README's sentence of the Consequences.
