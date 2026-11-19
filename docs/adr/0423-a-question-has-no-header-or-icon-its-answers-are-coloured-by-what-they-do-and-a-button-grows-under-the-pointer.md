# ADR-423 — A question has no header or icon, its answers are coloured by what they do, and a button grows under the pointer

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator on 2026-11-18: the control panel's questions of two answers lose the header and the icon JavaFX's confirmation draws, and the title it gives them; of the two answers, the one that goes on and stops something is drawn in a muted red and the one that keeps it in a muted green, neither bright, in both themes, every text on them at WCAG AA; and every button reacts to the pointer at once, with no animation and no fade, drawn in a lighter fill and slightly larger, the red and the green answers in hover shades of their own, a disabled button not reacting. Under the operator's delegation: each question's title (§1); that a choice of `Dialogs.choose` follows, its first answer green and its second red (§2); the tokens and their values (§3); the size under the pointer, 1.04, and which controls are buttons (§4); and the tests.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §7, the question asked on closing the window while its child runs, which is asked under the title *Close the control panel* (§1); and its Tests' *"its questions answered by the test through `Dialogs.confirm(String, String, String)`"*: through `Dialogs.confirm(String, String, String, String)`, its title first (§1). It carries a note at its head.
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §5: *Shut down Docker*'s warning is asked under the title *Shut down Docker*, and *"The confirmation of closing is unchanged"* holds of its sentence and its answers, the question gaining ADR-344 §7's title above (§1). It carries a note at its head.
- **Keeps**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, every colour, size and space the window draws in its one stylesheet; [ADR-379](0379-the-control-panels-window-has-a-light-and-a-dark-theme-switched-in-its-header-and-kept-beside-the-launch-values.md) §3, each colour token defined under `.root` and again under `.root.dark, .dark-dialog`, and §4, a dialog's pane given the window's stylesheet and, over a dark window, `dark-dialog`; [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §2, every colour a token of the two theme rules; [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) §1, every text at 4.5 : 1; [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §4, `Dialogs.choose`'s title, sentence, answers in their order and what it gives, its answers now coloured (§2); the sentences and the answers' words of the three questions, ADR-344 §7's, ADR-390 §5's and Stop's.

## Context

**The questions.** `Dialogs.confirm(sentence, yes, no)` asks a question with an answer that goes on, `yes`, and one that does not, `no`, and gives true where `yes` is pressed. Three callers ask one, each of whose answers that goes on stops something: the cockpit's Stop, with an invocation running, *Stop* or *Keep it running*; closing the window while its child runs, *Stop and close* or *Keep the window* (ADR-344 §7); and *Shut down Docker*, *Shut down Docker* or *Keep Docker running* (ADR-390 §5). `Dialogs.of` builds each as JavaFX's `Alert` of type `CONFIRMATION`, which draws, above the sentence, a header holding the type's own word in the machine's language, *Conferma* in Italian, an icon of a question mark beside it, and the same word as the window's title: none of the three says what the question is about.

**The choices.** `Dialogs.choose(title, sentence, answers)` asks under its caller's title, with one button for each answer, as a `Dialog` of no type, which draws no header and no icon (ADR-389 §4). Its callers ask whether to save an edit of the profile not saved: *Save*, *Discard* and *Cancel*, or *Save* and *Discard*, in that order.

**The buttons.** The stylesheet draws every button on the accent with the accent's text colour, a disabled one at opacity 0.45, and nothing under the pointer: its `.button` rule, an author's, overrides Modena's `:hover` colours, so a button drawn under the pointer is drawn as away from it. A tag's ×, a `Button` of the class `remove`, and a profile row's toggle, a `ToggleButton` of the class `more`, are drawn flat, with no fill, under the pointer too. JavaFX 26's stylesheets can declare a transition, which draws a property's change over time.

## Decision

### 1. A question is a plain dialog under its own title

**`Dialogs.confirm(title, sentence, yes, no)` takes a title first**, and asks a dialog of no type: **titled `title`, with no header, no header text and no icon**, showing its sentence alone, wrapped, above its two answers. It gives true where `yes` is pressed, as before, and `Dialogs.NONE` answers false. The three questions are asked under these titles, their sentences and answers unchanged:

| Asked by | Title | Goes on | Keeps |
| --- | --- | --- | --- |
| Stop, with an invocation running | *Stop the invocation* | *Stop* | *Keep it running* |
| Closing the window while its child runs | *Close the control panel* | *Stop and close* | *Keep the window* |
| *Shut down Docker* | *Shut down Docker* | *Shut down Docker* | *Keep Docker running* |

Which answer Enter and Escape press is as before.

### 2. An answer is coloured by what it does

**The answer that ends something is drawn on `-vespera-ending`, a muted red, and the one that keeps it on `-vespera-keeping`, a muted green**, each with a button's text colour, `-vespera-accent-ink`. Their buttons carry the style classes `answer-that-ends` and `answer-that-keeps`:

- **`Dialogs.confirm`**: `yes` ends and `no` keeps; each of the three ends what it stops, the invocation, the window with its child, or Docker.
- **`Dialogs.choose`**: its first answer keeps and its second ends, any after them drawn as every button. Every caller asks *Save* first, which keeps the edit, and *Discard* second, which ends it, *Cancel* staying on the accent.

The warning of [ADR-383](0383-a-parents-title-naming-no-page-or-several-stops-a-control-panel-publish-with-its-own-code-and-a-warning-asks-for-another.md) §3, *Publish* or *Cancel*, the masked field of [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) §3 and New…'s field of [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §6 keep their buttons on the accent.

### 3. The tokens

**Five tokens join each theme rule, after `-vespera-faint` and in the same order in both**: `-vespera-accent-hover`, a button's fill under the pointer; `-vespera-ending` and `-vespera-ending-hover`; `-vespera-keeping` and `-vespera-keeping-hover`. Each is muted, its hover shade lighter than it, and each carries `-vespera-accent-ink` at 4.5 : 1 or more, worked out from the tokens as WCAG's relative luminance has it:

| Token | Light | `#ffffff` on it | Dark | `#16132b` on it |
| --- | --- | --- | --- | --- |
| `-vespera-accent`, unchanged | `#4c3fb1` | 7.92 | `#b0a6ff` | 8.38 |
| `-vespera-accent-hover` | `#5c4fc2` | 6.26 | `#c4bcff` | 10.31 |
| `-vespera-ending` | `#9a4146` | 6.52 | `#dc9fa2` | 8.20 |
| `-vespera-ending-hover` | `#a84f54` | 5.37 | `#e8b7b9` | 10.24 |
| `-vespera-keeping` | `#356d4d` | 6.09 | `#98c7aa` | 9.54 |
| `-vespera-keeping-hover` | `#3e7a58` | 5.09 | `#b0d6be` | 11.37 |

In the light theme a lighter fill lowers the white text's contrast, the green's hover shade the least of them at 5.09 : 1; `#468563`, a step lighter, stands at 4.38 : 1 and is refused.

### 4. A button under the pointer

**Every button, a node of JavaFX's style class `button`, is drawn under the pointer on a lighter fill and at 1.04 times its size, across and down, at once**: the stylesheet's `:hover` rule sets `-fx-scale-x` and `-fx-scale-y` to 1.04 and the fill to `-vespera-accent-hover`, an answer that ends to `-vespera-ending-hover` and one that keeps to `-vespera-keeping-hover`; and **the stylesheet declares no transition**, so the button is drawn so as soon as its look is worked out, and drawn back as the pointer leaves it. The size is drawn and not laid out: no other control moves. A dialog's answers are buttons of a scene given the window's stylesheet (ADR-379 §4), and the rule reaches them.

**A disabled button does not react**: under the pointer it is drawn as away from it, on its own fill, at its own size and at opacity 0.45.

**A flat button keeps its look**: a tag's × stays with no fill under the pointer, its own rule's, and grows as every button. A `ToggleButton`, the theme switch and a profile row's toggle among them, a hyperlink, and the arrows of a scroll bar, a spinner or a list box are no node of the class `button`, and are drawn as before.

### 5. The modules, and run ids

**In `ui` alone**: `Dialogs`, `Cockpit`, `ControlPanelWindow` and `control-panel.css`. **No run id moves.**

## Alternatives refused

- **An `Alert` of type `CONFIRMATION` with its header and icon set to nothing.** Modena sets the type's icon from its stylesheet, and the type's word remains the title wherever one is not set; a dialog of no type, as `choose` builds, has neither to undo.
- **One title for every question, *Vespera*.** It says no more than the window it is asked over.
- **The answers coloured by their words**, *Save* green wherever it stands. The callers name the answers; the dialog colours them by their place, which `confirm`'s two arguments and `choose`'s order already give.
- **The choices left on the accent.** *Discard* loses an edit as *Stop* loses work; the operator's reason for the colours holds of it.
- **Bright reds and greens.** The operator asked for muted ones; each fill here is a muted shade that keeps its text at AA.
- **A transition, a fade or a growing animation.** The operator asked for none.
- **A larger size, 1.1.** The operator asked for a slight one; at 1.1 a button 120 px wide grows by 6 px each side, three quarters of the 8 px between the buttons of a row, where at 1.04 it grows by 2.4 px.

## Consequences

- Each question says in its title what it is about, and shows its sentence and its two answers alone.
- An answer that ends something reads as such before its words are read, in both themes.
- Every enabled button shows at once that the pointer is over it.

## Known limits

- **A button drawn 1.04 times its size inside a container that clips what it holds** loses the part past the container's edge, 2 % of its width each side at most. Which of the window's containers clip is not measured.

## What this does not decide

- **The warning, the masked field and New…'s field**, which keep their look (§2).
- **Which answer is the default**, pressed by Enter: as before, the answer that goes on.
- **A button's look when focused or pressed**, Modena's under the stylesheet's, unchanged.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheWindowsQuestionsAndButtonsTest.aQuestionShowsItsTitleAndSentenceAloneAndColoursItsAnswersByWhatTheyDo` | §1 to §4: a question asked through `Dialogs.of`'s `confirm` over the window, light and then dark, read while it shows: its window titled as the caller names it; no header, no header text, no icon, and its sentence the only words beside its answers; the answer that goes on on `-vespera-ending` and the one that keeps on `-vespera-keeping`, each in `-vespera-accent-ink`; under the pointer, at once, the first on `-vespera-ending-hover` and the second on `-vespera-keeping-hover`, each 1.04 times its size across and down |
| `ui.TheWindowsQuestionsAndButtonsTest.stopAsksItsQuestionUnderItsTitle` | §1: Stop, with the sleeping child running, asks one question, *Stop the invocation*, Stop's sentence, *Stop* and *Keep it running* |
| `ui.TheWindowsQuestionsAndButtonsTest.aChoiceDrawsItsFirstAnswerKeepingAndItsSecondEnding` | §2: `choose` with *Save*, *Discard* and *Cancel*, light and then dark: *Save* on `-vespera-keeping`, *Discard* on `-vespera-ending`, *Cancel* on the accent, each in `-vespera-accent-ink` |
| `ui.TheWindowsQuestionsAndButtonsTest.aButtonUnderThePointerIsDrawnAtOnceLighterAndLargerAndADisabledOneIsNot` | §4: in the window, light and then dark, *Shut down Docker*, enabled, on the accent at its own size away from the pointer and, given the pointer, in the same turn of the toolkit on `-vespera-accent-hover` at 1.04 across and down; Stop, disabled with no invocation running, on the accent at its own size under the pointer as away from it; the stylesheet declares no transition |
| `ui.TheShutDownDockerButtonTest.theButtonWarnsFirstAndRunsNothingWhenKept`, `withAChildRunningItIsStoppedFirstThenTheShutDownsLineIsShown` and `closingTheWindowStopsTheSidecarsAndNotTheEngine` | §1: the warning asked under *Shut down Docker* and the question of closing under *Close the control panel*, each with its sentence and answers as ADR-390 §5 and ADR-344 §7 have them |
| `ui.TheControlPanelsThemeTest.everyColourOfTheWindowIsAThemeTokenAndStandsAtItsContrastInBothThemes` | §3: in both themes `-vespera-accent-ink` at 4.5 : 1 or more on each of the five tokens; with every colour of the stylesheet a token of the two theme rules (ADR-381 §2) |

`ui.TheControlPanelsThemeTest.everyDarkTokenAndTheLightFaintTextStandAtWcagAaAgainstTheirBackgrounds` holds that the dark rule redefines each of the light rule's tokens, the five among them, in the same order, and `aDialogOverTheWindowTakesItsStylesheetAndOverADarkWindowTheDarkTokens` asks its question through the four-argument `confirm`. `AnsweringDialogs.Asked` holds a question's title first. **What a test cannot hold** (ADR-342 §6): the system's pointer, which the Headless platform does not move, so the tests give a button the `hover` state the stylesheet reads; and a look drawn in code on a change of a button's `hover` property, which giving the state does not change. ADR-381 §2's test holds that no class of the window sets a style of its own.

## What the implementation owes

- `Dialogs`: `confirm(String title, String sentence, String yes, String no)` in the interface, in `NONE`, which answers false, and in `of`, which builds a `Dialog<ButtonType>` as `choose` does: titled `title`, its content a wrapping `Label` of `sentence`, no header text and no graphic, `yes` of `ButtonData.OK_DONE` carrying `answer-that-ends` and `no` of `CANCEL_CLOSE` carrying `answer-that-keeps`, owned by and themed over the window as now, true only where `yes` is pressed. `choose`: its first answer's button carries `answer-that-keeps` and its second's `answer-that-ends`.
- `Cockpit`: Stop asks `confirm("Stop the invocation", Child.STOP_CONFIRMATION, "Stop", "Keep it running")`, and *Shut down Docker* `confirm("Shut down Docker", …)` with its sentence and answers as now. `ControlPanelWindow`: closing asks `confirm("Close the control panel", Child.STOP_CONFIRMATION, "Stop and close", "Keep the window")`.
- `control-panel.css`: §3's five tokens in `.root` and in `.root.dark, .dark-dialog`, after `-vespera-faint`, in that order; `.button:hover` on `-vespera-accent-hover` at `-fx-scale-x` and `-fx-scale-y` 1.04; `.button.answer-that-ends` and `.button.answer-that-keeps` on their tokens, and under the pointer on their hover shades; a disabled button under the pointer drawn as away from it, its fill its own and its scale 1; no transition. The header comment names the answers' tokens beside the others.
- The tests above passing.
