# ADR-372 — The control panel's window has a light and a dark theme, switched in its header and kept beside the launch values

> **Partly amended — see [ADR-373](0373-every-html-page-vespera-writes-is-drawn-dark-whatever-the-systems-setting-and-the-control-panels-theme.md).** Of §5: `report.html`, each analysis report's page and the deliverable's pages keep a look of their own whatever the window's theme, and that look is dark, drawn from §3's dark tokens. Of What this does not decide: a page drawn outside the window does not take the theme, every page Vespera writes being dark. The rest of this record stands.

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: under the operator's delegation on [#780](https://github.com/algernon28/vespera/issues/780) of 2026-11-08, to take its decisions and build the window's dark theme: a Light and Dark switch in the window, light by default, the operating system's setting not read (§1); the choice kept per user beside the launch values (§2); the colours a set of tokens defined once and redefined for dark, at WCAG AA (§3); the window and its dialogs covered (§4); the files the jar writes left as they are (§5). The switch's words and id, the tokens' values, the rule for a folder that is missing or not the user's own, and the tests are decided under the same delegation. That the theme is not read from a folder that is not the user's own (§2) is the operator's, on [#794](https://github.com/algernon28/vespera/issues/794) of 2026-11-12.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, the header's row, `where` and `child`: the header carries the theme switch, `theme-toggle`, at its right (§1); and its What this does not decide, *"A dark theme"*: decided here. It carries a note at its head.
- **Amends**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §3, the folder of the user's own, whose section covers one file: the folder holds a second, `theme`, written as the switch changes and only where the folder exists and is the user's own, never making the folder (§2). It carries a note at its head.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, the one stylesheet that owns every colour: it holds each colour token twice, the light set under `.root` and the dark under `.root.dark` and `.dark-dialog`, chosen by a style class on the window's root or a dialog's pane, and a dialog's pane is given the window's stylesheet (§3, §4). It carries a note at its head.
- **Keeps**: ADR-342 §3, no class sets a style of its own: the switch and `Dialogs` add a style class and a stylesheet, never a style. ADR-292 §1 and §2: the launch values are the eight keys of one file, written as an invocation starts; the theme is no launch value and is not in that file. [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md) §4: a bar's percent in the theme's ink with a halo of its surface, in either theme.
- **Settles**: [#794](https://github.com/algernon28/vespera/issues/794), its Part D, which folds [#785](https://github.com/algernon28/vespera/issues/785).

## Context

**What the window looked like.** The window is drawn over JavaFX's Modena theme by the one stylesheet of `io.algernon.vespera.ui`, `control-panel.css`, which owns every colour, size and space (ADR-342 §3). Its colours were light alone, and ADR-344 left a dark theme undecided.

**The word.** *Theme* here names the window's set of colours, light or dark. It is not a cluster, the report template, or the website rendering's theme (ADR-333), the three things `CONTEXT.md` keeps the word from.

**What was measured.** WCAG 2's contrast ratio of each text colour of the stylesheet against each background it names, worked out from the hexadecimal values the stylesheet sets, by WCAG's relative luminance; AA asks 4.5 : 1 of text of a normal size.

| Dark theme | on the surface, `#25232f` | on the ground, `#1a1922` | on a control's inside, `#211f2a` |
| --- | --- | --- | --- |
| text, `-vespera-ink`, `#ecebf3` | 13.05 : 1 | 14.71 : 1 | 13.71 : 1 |
| dim text, `-vespera-dim`, `#aeaac2` | 6.86 : 1 | 7.74 : 1 | 7.21 : 1 |
| accent, `-vespera-accent`, `#b0a6ff` | 7.16 : 1 | 8.08 : 1 | 7.53 : 1 |
| error, `-vespera-bad`, `#ff8f94` | 7.07 : 1 | 7.97 : 1 | 7.43 : 1 |
| faint text, `-vespera-faint`, `#8e8aa6` | **4.66 : 1** | 5.25 : 1 | 4.90 : 1 |

The text on the accent, `-vespera-accent-ink`, `#16132b`, stands at 8.39 : 1 against the dark accent. The weakest pair is the faint text on the surface, at 4.66 : 1.

| Light theme | on the surface, `#ffffff` | on the ground, `#f5f4f1` |
| --- | --- | --- |
| faint text, `-vespera-faint`, `#6f6a85` | 5.15 : 1 | 4.68 : 1 |
| the faint text it replaced, `#a29db8` | 2.61 : 1 | 2.37 : 1 |

The light theme's other text colours stand at 16.78 and 15.26 : 1 (text, `#1e1b2e`), 5.77 and 5.24 (dim, `#67637a`), 7.92 and 7.20 (accent, `#4c3fb1`) and 6.45 and 5.86 (error, `#b3282d`) on the surface and the ground, and its white text on the accent at 7.92 : 1.

## Decision

### 1. The switch

**The header carries one switch at its right, `theme-toggle`, a `ToggleButton` drawn as a pill whose knob stands right where dark is chosen.** It stands in a box that is the header's last part, after the corpus root and the working directory (`where`, `child`), and its right edge is the header's:

| Part | Id | What it holds |
| --- | --- | --- |
| The theme switch | `theme-toggle` | on where dark is chosen, off where light is; named *Dark mode* to a screen reader |

Above the switch stands the word **Theme**; to its left the word **Light** and to its right the word **Dark**, each choosing its theme where it is clicked. Pressing the switch moves the window to the other theme at once.

**A first start opens light**: the window opens dark only where §2's file says `dark`. **The operating system's setting is not read**: whether the system is set light or dark plays no part.

### 2. Where the choice is kept

**The choice is kept per user in a file named `theme` in the folder of the launch values** (ADR-292 §3), beside `launch-values.properties`: `dark` or `light`, in UTF-8, written each time the switch changes. A window opens dark where that file, its leading and trailing whitespace stripped, reads `dark`, and light where it reads anything else, is missing or cannot be read.

**The file is written only where the folder exists and is the user's own, and read only where the folder is the user's own**, by ADR-292 §3's test: a folder, and where POSIX permissions apply, owned by the user and not writable by its group or by others. Where the folder does not exist the switch never makes it, and where it is not the user's own nothing is written there, a `theme` file found there is not read and the window opens light, and nothing is said. In either case the window keeps the theme chosen until it closes. A write that fails is silent: the theme is a convenience, and no start or write of the control panel's waits on it.

The theme is no launch value: it is not in `launch-values.properties`, is not passed to any child, and is not written as an invocation starts.

### 3. The colours

**The stylesheet defines each colour token once for the light theme, under `.root`, and redefines every one of them, by the same names, for the dark, under `.root.dark` and `.dark-dialog`**: the text, the dim text, the surface, the ground, the rule, the accent, the text on the accent, the error and the faint text, with Modena's `-fx-base` and, for the dark, its control backgrounds, text fills, prompt text and focus colour. Every rule of the window draws through the tokens, so the window's root carrying the style class `dark` moves every part at once.

**In the dark theme the text, dim text, accent, error and faint text colours each stand at 4.5 : 1 or more against the surface, the ground and a control's inside, and the text on the accent against the accent**; the measured ratios are the Context's, the weakest the faint text on the surface at 4.66 : 1. **In the light theme the faint text stands at 4.5 : 1 or more against the surface and the ground**: 5.15 and 4.68 : 1, where the colour it replaced stood at 2.61 : 1 on white.

### 4. The dialogs

**A dialog the window asks through `Dialogs`, a question with two answers or a masked field, is given the window's stylesheet on its pane, and, where the window's root carries `dark`, the style class `dark-dialog`**, which the stylesheet gives the dark tokens and draws on the dark surface. A dialog is a scene of its own and would otherwise be drawn in Modena's colours alone. The folder and file dialogs are the system's own and are drawn as the system draws them.

### 5. What the theme does not reach

The files the jar writes are drawn as they were: `report.html`, each analysis report's page and the deliverable keep their own look, whatever the window's theme. The window's charts, the analysis reports drawn natively in its tabs and the steps' bars are drawn as their records have them; no colour of theirs is chosen here for the dark theme (What this does not decide).

### 6. The modules, and run ids

Only `io.algernon.vespera.ui` and its stylesheet are concerned. No class of the jar's other packages, no file the jar writes and no property changes; no stage's version names the control panel. **No run id moves.**

## Alternatives refused

- **The operating system's setting, alone or as the default the switch overrides.** The window opens light whatever the system says; one control decides it, and a test on the Headless platform cannot set the system's choice.
- **The theme as a launch value in `launch-values.properties`.** That file holds what an invocation starts with and is written as one starts (ADR-292 §2); the theme is chosen and kept as it changes, and passes to no child.
- **Making the folder to keep the theme.** ADR-292 §3 makes it as an invocation first starts, with its permissions; a switch pressed before that keeps its choice until the window closes.
- **A second stylesheet for the dark theme.** One stylesheet owns every colour (ADR-342 §3); the dark set redefines the same tokens in it.

## Consequences

- The operator switches the window between light and dark in its header, and the next window opens in the theme last chosen, once an invocation has made the launch values' folder.
- Every part of the window that draws through the stylesheet's tokens follows the theme, and the dialogs it asks with too.
- No schema, cache key, verdict kind, property, run id, file the jar writes or class outside `io.algernon.vespera.ui` changes.

## Known limits

- **The ratios are measured against the backgrounds the stylesheet names**: the surface, the ground and, in the dark theme, a control's inside. A text drawn on a control's own face, `-fx-base`, is not measured: the faint text there would stand at 4.24 : 1 in the dark theme and 4.44 : 1 in the light. A titled pane's content is drawn on the ground, its top 5 px a little darker.
- **The file is written in place**, not through a temporary file moved over it as ADR-292 §2 writes the launch values: a read that meets a write half done reads light.
- **The tests read the colours the stylesheet sets**, not the pixels drawn: a platform that drew a colour otherwise would not be seen.

## What this does not decide

- **The charts' colours, the analysis reports' native views, a tooltip, a context menu and the steps' bars in the dark theme**, and whether a report drawn outside the window takes the theme: [#794](https://github.com/algernon28/vespera/issues/794)'s Part C, whose record amends this one.
- **The stylesheet's colours beyond §3's ratios**: the implementation's, under ADR-342 §3.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheControlPanelsThemeTest.theSwitchStandsAtTheHeadersRightBetweenLightAndDarkAndTheWindowOpensLight` | §1: `theme-toggle`'s box is the last part of the header holding `where`, its right edge at the header's within a pixel; it reads Theme above Light, the switch and Dark in that order, the switch named Dark mode; a first start opens light, the switch off and the root carrying no `dark`; the switch makes the root dark, Light clicked light and Dark clicked dark again |
| `ui.TheControlPanelsThemeTest.theThemeChosenIsKeptBesideTheLaunchValuesAndTheNextWindowOpensInIt` | §2: in a launch values' folder of the user's own, dark chosen writes `dark` in `theme` and the next window opens dark, its switch on; light chosen there writes `light` and the window after it opens light |
| `ui.TheControlPanelsThemeTest.theThemeIsKeptNowhereAndReadFromNowhereWhereTheFolderIsAbsentOrNotTheUsersOwn` | §2: dark chosen where the folder does not exist makes no folder and the next start reads light; where a file stands in the folder's place it is left as it was with nothing beside it; and, where POSIX permissions apply, a folder others can write in is left empty and a `theme` reading `dark` found there is not read |
| `ui.TheControlPanelsThemeTest.everyDarkTokenAndTheLightFaintTextStandAtWcagAaAgainstTheirBackgrounds` | §3, parsed from the stylesheet: the dark rule redefines exactly the light rule's tokens, in its order; each dark text token stands at 4.5 : 1 or more against the surface, the ground and a control's inside, and the text on the accent against the accent; the light faint text at 4.5 : 1 or more against the surface and the ground |
| `ui.TheControlPanelsThemeTest.aDialogOverTheWindowTakesItsStylesheetAndOverADarkWindowTheDarkTokens` | §4: a question asked through `Dialogs.of` over the light window and over the dark one shows a pane carrying exactly the window's stylesheet; over the dark window it carries `dark-dialog` and is drawn on the dark surface, and over the light one neither |

That the operating system's setting is not read is held only as far as a first start opens light on the machine the test runs on; the Headless platform reports no system choice to set. §5 has no test of its own: nothing the jar writes names the theme.

## What the implementation owes

Nothing: the window as it stands builds §1 to §5, and the tests above pass against it.
