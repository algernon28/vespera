# ADR-381 — Every view inside the control panel's window follows its Light and Dark switch at once, its popups included

> **Partly amended — see [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md).** Of §2, §4 and §5: each theme rule gains a sixth series colour, `-vespera-series-6`, `#8f5bd6` light and `#9b74e6` dark, at 3 : 1 or more against the surface and the ground, and sets `CHART_COLOR_6` from it; of §3, the latest-sample rules name `-vespera-series-1` to `-6`. Of Known limits: a chart of seven lines or more. Of What this does not decide: a chart for each stage is decided there. The rest of this record stands.

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator on 2026-11-12, on [#794](https://github.com/algernon28/vespera/issues/794)'s Part C, with no prototype: every view inside the control panel's window follows its Light and Dark switch, and the switch repaints it at once, with nothing reopened: the progress page's three charts, the steps' bars, their percent and the legs, the analysis reports' native views, the Report tab, a tooltip, a context menu, and any other part that drew a colour the stylesheet's tokens did not give it (§1); every colour drawn through the stylesheet's tokens, its text at WCAG AA, 4.5 : 1, and a chart's marks and a bar's fill at 3 : 1 against their background, in both themes, and the percent on a bar at AA against the bar's filled and unfilled part, or a rule that guarantees it (§2, §5, §6); `report.html`'s dark series colours reused for the dark theme where they pass, and light ones picked that pass (§4). The selectors, the tokens' values, which of Modena's colours are taken over, the rule for a popup showing as the switch flips, and the tests are decided here.
- **Amends**: [ADR-379](0379-the-control-panels-window-has-a-light-and-a-dark-theme-switched-in-its-header-and-kept-beside-the-launch-values.md) §3, the tokens each theme rule defines: both gain five series colours, and the light rule sets the Modena colours the dark one sets, with the accent, the unfocused selection and five chart colours set from tokens (§2); its §5, *"The window's charts, the analysis reports drawn natively in its tabs and the steps' bars are drawn as their records have them"*, and its What this does not decide, *"The charts' colours, the analysis reports' native views, a tooltip, a context menu and the steps' bars in the dark theme"*: they follow the theme (§1, §3). It carries a note at its head.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md) §2, *"Eight such colours are named"*: five are, `-vespera-series-1` to `-vespera-series-5`, one for each line a chart of the window draws at most (§4); its What this does not decide, *"the colours, modena's eight or the stylesheet's own, … the tooltip's … style, and a dark theme"*: decided here (§3, §4); and its refused alternative *"A copy of the progress page's palette in the window's stylesheet"*: the window's dark series take the five values `report.html`'s do, as tokens of its own stylesheet that nothing ties to the page's (§4). It carries a note at its head.
- **Amends**: [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md) §4 and its What this does not decide, *"the bar's colours"*: a bar's fill is the accent and its track's inside the surface, which is the colour of the percent's halo, so that the percent's ink stands on its halo against the fill and on the track's own colour against the rest (§3, §6). It carries a note at its head.
- **Keeps**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, one stylesheet owns every colour and no class sets a style of its own: the switch takes a popup's style again by a style class, never a style. ADR-379 §1, §2 and §4: the switch, where the choice is kept, the dialogs. ADR-370 §4's rule, the percent in the theme's ink with a halo of its surface. ADR-365 §2's rule that a line, its swatch and its samples' nodes take one colour by one style class, and §3's markers. [ADR-380](0380-every-html-page-vespera-writes-is-drawn-dark-whatever-the-systems-setting-and-the-control-panels-theme.md): every page Vespera writes is dark, whatever the switch says. [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1 and §2: the parts of the window and what each bar shows.
- **Settles**: [#794](https://github.com/algernon28/vespera/issues/794)'s Part C, for the window's own views; ADR-380 settled it for the pages.

## Context

**What the stylesheet drew, and what Modena drew.** `control-panel.css` defines ADR-379's tokens under `.root` and redefines them under `.root.dark, .dark-dialog`; every rule of its own draws through them. A part no rule of it names is drawn by Modena, JavaFX's theme, from Modena's own colours. No class of `io.algernon.vespera.ui` sets a colour, a style, an effect, a background or a border in code: none imports `javafx.scene.paint` or `javafx.scene.effect`, none calls `setStyle`, and no string of it holds `-fx-` or a hexadecimal colour. What Modena drew, read from the window's nodes on the Headless platform:

| Part | Light theme | Dark theme |
| --- | --- | --- |
| a line of a chart, its swatch and its latest sample | Modena's `CHART_COLOR_1` to `_5`, `#f3622d`, `#fba71b`, `#57b757`, `#41a9c9`, `#4258c9`, the same in both themes; the stylesheet's latest-sample rules name all eight of Modena's | the same |
| a bar of an analysis report's chart | a gradient of `CHART_COLOR_1` and `_2` | the same |
| a chart's plot, title, axis title, ticks and legend | on the ground; text `#333333`, ticks `#585858`; the legend on a gradient of the ground | on the ground; text `-vespera-ink`, ticks `#f5f5f9`; the legend nearly the ground |
| a bar of the steps, its fill | a gradient of Modena's accent, `#0096c9` | the same |
| a bar of the steps, its track | a gradient from `#ededed` to `#ffffff` | a gradient around `#211f2a` |
| a leg's summary, its face | a gradient from `#fefefe` to `#dcdae0` | a gradient from `#474557` to `#2a2835` |
| a tooltip | `#1e1e1e`, its text `#ffffff` | the same |
| a context menu | `#ffffff`, its text `#333333` | `#211f2a`, its text `#ffffff` |
| a selected row of a list or a table, its view focused | `#0096c9`, its text `#ffffff` | `#0096c9`, its text `-vespera-ink` |
| a selected row, its view not focused | `#d3d3d3`, its text `#333333` | `#d3d3d3`, its text `-vespera-ink` |

**What fails.** WCAG 2's contrast ratio, worked out from the hexadecimal values by WCAG's relative luminance, rounded to two places; AA asks 4.5 : 1 of text of a normal size, and WCAG 2.1's 1.4.11 asks 3 : 1 of a graphical object needed to read a chart. In the light theme Modena's first four chart colours stand at 2.89, 1.79, 2.30 and 2.47 : 1 on the ground; a selected row's white text on `#0096c9` at 3.38 : 1. In the dark theme the selected row's text stands at 2.86 : 1 on `#0096c9` and at 1.26 : 1 on `#d3d3d3`. The dim text of a leg's summary stands at 4.16 : 1 on the light face's darkest stop and 4.13 : 1 on the dark face's lightest. The tooltip and the light context menu never change with the theme.

**A popup.** A tooltip and a context menu are windows of their own, each with a scene whose root is not under the window's. JavaFX styles each from the stylesheets and the style classes of the node it is shown over, a tooltip's the node it is installed on and a context menu's the control it is asked of, so a popup shown after the switch has flipped takes the theme; one showing as it flips keeps the colours it was shown with until it is shown again, since nothing tells its scene the window's root has changed. Adding a style class to the popup's root and removing it again has its scene take its style afresh, from the window's root as it now is.

**The bar's percent.** ADR-370 §4 draws it in the ink with a halo of the surface. The ink cannot stand at AA against any one fill that stands at 3 : 1 on the dark track: a fill 3 : 1 above the dark surface has a relative luminance of 0.154 or more, and the dark ink stands at 4.5 : 1 only on a luminance of 0.147 or less.

## Decision

### 1. Every view follows the switch, at once

**Every part of the window takes its colours from the theme the window's root carries, and moves with it as the switch flips, with nothing reopened**: the progress page's three charts, `ProgressCharts`, their plots, axes, ticks, grids, titles, legends and lines; the steps, `StepsChart`, every bar, its percent and each leg's summary and rows; the analysis reports' native views, `AnalysisReportView`, their charts, tables and texts, and the list of them, `ReportsPane`; the Report tab, `LiveReport`; and every other part of the window, the header, the forms, the sidebar, the output panel and the dialogs as ADR-379 §4 has them.

**A popup takes the theme too**: a tooltip, a context menu, and any other popup window shown over a node of the window, a list of a choice among them. One shown after the switch flips takes the theme as it is shown; **one showing as the switch flips takes the new theme at once**: whenever the switch moves the window's root between the themes, every popup window showing whose owner is the window, or a popup of it, takes its style afresh.

### 2. Every colour is a token of the stylesheet's two theme rules

**A colour is written only in the stylesheet's two theme rules**, `.root` for the light theme and `.root.dark, .dark-dialog` for the dark, as ADR-379 §3 has them: a token's value, six hexadecimal digits, or a colour of Modena's set there from a token or a value of its own. **No other rule writes a colour of its own**: each colour it declares, of a background, a border, a fill, a stroke, a text or an effect, alone or inside a gradient or an effect, names a colour the two theme rules set, a `-vespera-` token or one of Modena's colours set there, or is `transparent`. **No class of `io.algernon.vespera.ui` sets a colour**: none imports `javafx.scene.paint` or `javafx.scene.effect`, none calls `setStyle`, and no string of it holds `-fx-` or a hexadecimal colour. There is no exception.

**Each theme rule gains the five series colours**, `-vespera-series-1` to `-vespera-series-5`, in that order after `-vespera-faint`, at §4's values.

**The light rule sets the Modena colours the dark one sets**, so that neither theme leaves one to Modena: `-fx-control-inner-background: -vespera-surface`, `-fx-text-background-color`, `-fx-text-inner-color` and `-fx-prompt-text-fill` as the dark rule sets them, `-vespera-ink`, `-vespera-ink` and `-vespera-dim`, and `-fx-focus-color: -vespera-accent` and `-fx-faint-focus-color: transparent`. **And it sets, for both themes**, since the dark rule redefines the tokens they name: `-fx-accent: -vespera-accent`, `-fx-selection-bar-non-focused: -vespera-rule`, and `CHART_COLOR_1` to `CHART_COLOR_5` as `-vespera-series-1` to `-vespera-series-5`. The dark rule keeps its `-fx-base`, `-fx-control-inner-background` and the rest as ADR-379 §3 has them.

### 3. The colours, part by part

| Part | Drawn in |
| --- | --- |
| a chart's plot | `-vespera-surface` |
| a chart's title, its axes' titles, its legend's names | `-vespera-ink` |
| a chart's ticks | `-vespera-dim` |
| a chart's grid, its zero lines, its axes' lines and tick marks | `-vespera-rule` |
| a chart's legend | `-vespera-surface`, inside a border of `-vespera-rule` |
| the n-th line of a chart, its swatch and its latest sample's marker | `-vespera-series-<n>`, through `CHART_COLOR_<n>`; the latest-sample rules name `-vespera-series-1` to `-5`, and the three for a sixth to an eighth line go |
| the n-th series of an analysis report's bar chart, each bar and its swatch | `-vespera-series-<n>`, one fill and no gradient |
| a bar of the steps or of a leg's summary, its fill | `-vespera-accent`, one fill and no gradient; a bar moving with no fraction a gradient from `transparent` to `-vespera-accent`, as Modena draws it |
| a bar's track | `-vespera-surface`, inside a border of `-vespera-rule` |
| a bar's percent | `-vespera-ink`, with a halo of `-vespera-surface` (ADR-370 §4) |
| a leg's summary, its face | `-vespera-surface`, inside a border of `-vespera-rule`; its arrow `-vespera-dim`; its name and state in `-vespera-ink` |
| a tooltip | `-vespera-surface`, inside a border of `-vespera-rule`; its text `-vespera-ink` |
| a context menu | `-vespera-surface`, inside a border of `-vespera-rule`; an item's text `-vespera-ink`; the item under the pointer or the keys `-vespera-accent`, its text `-vespera-accent-ink` |
| a table's or a list's row | `-fx-control-inner-background`; its text `-vespera-ink` |
| a selected row, its view focused | `-vespera-accent`; its text `-vespera-accent-ink` |
| a selected row, its view not focused | `-vespera-rule`; its text `-vespera-ink` |
| text selected in a focused field | `-vespera-accent`; its text `-vespera-accent-ink` |

A part this table does not name draws as ADR-379 §3 has it: a control's face from `-fx-base`, its text from Modena's ladder of it, and everything else through the tokens.

### 4. The series colours

| Token | Light | Dark |
| --- | --- | --- |
| `-vespera-series-1` | `#2a78d6` | `#3987e5` |
| `-vespera-series-2` | `#c8541f` | `#d95926` |
| `-vespera-series-3` | `#15895f` | `#199e70` |
| `-vespera-series-4` | `#a86f00` | `#c98500` |
| `-vespera-series-5` | `#c94c80` | `#d55181` |

**The dark values are `report.html`'s `--series-1` to `--series-5`** (ADR-380 §3), all five standing at 3 : 1 or more on the dark surface and ground. **The light ones keep the same order of hues**, blue, orange, green, amber and pink, ADR-268 §9's light blue and the four others darkened until each stands at about 4 : 1 on the light ground. The two sets are the window's own tokens: no rule and no test ties them to the page's, and either may change without the other.

### 5. What the colours are held to

**In each theme, every text token, `-vespera-ink`, `-vespera-dim`, `-vespera-accent`, `-vespera-bad` and `-vespera-faint`, stands at 4.5 : 1 or more against the surface, the ground and a control's inside; `-vespera-accent-ink` against the accent; and `-vespera-ink` against the rule, an unfocused selection. Every series token and the accent, a chart's mark and a bar's fill, stand at 3 : 1 or more against the surface and the ground.** The lines that only divide, the rule as a grid, an axis or a border, are held to nothing, as ADR-380 §4 holds its own.

| Light theme | on the surface and a control's inside, `#ffffff` | on the ground, `#f5f4f1` |
| --- | --- | --- |
| text, `-vespera-ink`, `#1e1b2e` | 16.78 : 1 | 15.26 : 1 |
| dim text, `-vespera-dim`, `#67637a` | 5.77 : 1 | 5.24 : 1 |
| accent, `-vespera-accent`, `#4c3fb1` | 7.92 : 1 | 7.20 : 1 |
| error, `-vespera-bad`, `#b3282d` | 6.45 : 1 | 5.86 : 1 |
| faint text, `-vespera-faint`, `#6f6a85` | 5.15 : 1 | **4.68 : 1** |
| series 1, `#2a78d6` | 4.42 : 1 | 4.01 : 1 |
| series 2, `#c8541f` | 4.43 : 1 | 4.02 : 1 |
| series 3, `#15895f` | 4.40 : 1 | 4.00 : 1 |
| series 4, `#a86f00` | 4.25 : 1 | **3.87 : 1** |
| series 5, `#c94c80` | 4.35 : 1 | 3.95 : 1 |

The light text on the accent, `#ffffff`, stands at 7.92 : 1 against it, and the ink at 12.40 : 1 against the rule, `#dfdce6`.

| Dark theme | on the surface, `#25232f` | on the ground, `#1a1922` | on a control's inside, `#211f2a` |
| --- | --- | --- | --- |
| series 1, `#3987e5` | 4.24 : 1 | 4.78 : 1 | 4.46 : 1 |
| series 2, `#d95926` | 3.98 : 1 | 4.48 : 1 | 4.18 : 1 |
| series 3, `#199e70` | 4.53 : 1 | 5.11 : 1 | 4.76 : 1 |
| series 4, `#c98500` | 5.03 : 1 | 5.67 : 1 | 5.28 : 1 |
| series 5, `#d55181` | **3.91 : 1** | 4.41 : 1 | 4.11 : 1 |
| accent, `#b0a6ff`, as a bar's fill | 7.16 : 1 | 8.08 : 1 | 7.53 : 1 |

The dark text tokens stand as ADR-379's Context measures them, the weakest the faint text on the surface at 4.66 : 1. The dark text on the accent, `#16132b`, stands at 8.38 : 1 against it, the pair ADR-379 gives as 8.39 : 1, 8.385 rounded the other way; the ink at 9.50 : 1 against the rule, `#3b3949`. The weakest text pair of either theme is the light faint text on the ground, at 4.68 : 1; the weakest mark, the light fourth series on the ground, at 3.87 : 1.

### 6. The bar's percent

**The percent's halo is the colour of the bar's track inside**, `-vespera-surface`, in both themes. Where the percent lies on the unfilled part, its ink stands on the track's own colour, at 16.78 : 1 in the light theme and 13.05 : 1 in the dark; where it lies on the fill, it stands on its halo, at the same ratios, as ADR-370 §4 has it. Against the fill alone the ink would stand at 2.12 : 1 in the light theme and 1.82 : 1 in the dark (Context: no fill both at 3 : 1 on the dark track and under 4.5 : 1 of the dark ink exists); the halo is what holds it at AA, and the fill stands at 3 : 1 or more against the track, 7.92 : 1 and 7.16 : 1, and against the track's border at 5.85 and 5.22 : 1.

### 7. The modules, and run ids

Only `io.algernon.vespera.ui` and its stylesheet are concerned: `control-panel.css`, and the class that moves the window's root between the themes. No class of the jar's other packages, no file the jar writes and no property changes. The control panel lies in no module, and `implementation-versions.properties` lists the eleven modules alone, `ui/` among none of them, so no stage's version names it. **No run id moves.**

## Alternatives refused

- **Drawing the bar's fill in a colour the ink stands at AA against with no halo.** None exists in the dark theme that also stands at 3 : 1 on the dark track (Context); in the light theme one would make the two themes' bars follow different rules.
- **A popup that keeps the theme it was shown with until it is shown again.** The operator asked that the switch repaint at once; a tooltip held open under the pointer, or a context menu open as the switch is pressed by its keys, would show the other theme beside the window.
- **ADR-268 §9's light series.** Three of them, `#1baf7a`, `#eda100` and `#e87ba4`, stand under 3 : 1 on white.
- **Keeping Modena's eight chart colours.** Four of the five a chart draws stand under 3 : 1 on the light ground, and none changes with the theme.
- **A second set of selectors per theme, `.root.dark .chart-plot-background` and its kind.** The tokens are redefined once, and every part draws through them in both themes by one rule.

## Consequences

- The operator who flips the switch sees every part of the window, its charts, bars, native views, an open tooltip and an open context menu among them, in the theme chosen, at once.
- Every colour the window draws is one of the tokens its stylesheet defines twice, and a part added later draws through them or fails the test that reads the stylesheet.
- The light theme's text is the ink, `#1e1b2e`, where Modena drew `#333333`; a selected row is the accent, not Modena's blue.
- No schema, cache key, verdict kind, property, run id or file the jar writes changes.

## Known limits

- **A chart of six lines or more** would draw its sixth to eighth in Modena's `CHART_COLOR_6` to `_8`, which no token sets. No chart of the window draws more than five: the progress page's at most five (ADR-365 §2), an analysis report's at most two.
- **The light series stand at nearly one lightness**, so that each stands at about 4 : 1 on the ground; lines are told apart by their hue, the legend's names and each sample's value (ADR-365 §2, §3), not by lightness.
- **A control's face is still Modena's ladder of `-fx-base`**, as ADR-379's Known limits says, except a leg's summary, which this record draws on the surface. A dim or faint text placed on a control's face is not measured.
- **The ratios are those of the tokens**, and the test reads the colours the window's nodes are given, not the pixels drawn: the halo's blur and a gradient Modena still draws, the indeterminate bar's, are not measured.
- **Text selected in a focused field** is drawn as §3 says, but no test reads it: the skin holds those two colours and no public property gives them.
- **A popup shown over no node**, with a window alone for its owner, is styled from neither the window's stylesheet nor its rules, only from the tokens Modena's own rules look up; nothing of the window shows one so.

## What this does not decide

- **A chart for each stage**, [#794](https://github.com/algernon28/vespera/issues/794)'s Part B.
- **The window's sizes, spaces and fonts**, the stylesheet's under ADR-342 §3.
- **The folder and file dialogs**, the system's own (ADR-379 §4).

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheControlPanelsThemeTest.everyColourOfTheWindowIsAThemeTokenAndStandsAtItsContrastInBothThemes` | §2: no source of `io.algernon.vespera.ui` imports `javafx.scene.paint` or `javafx.scene.effect`, calls `setStyle`, or holds `-fx-` or a hexadecimal colour in a string; every colour a rule other than the two theme rules declares names a colour those two set or is `transparent`; §4, §5, parsed from the stylesheet with each token's reference followed to its value: in each theme the five series tokens are five colours, each with the accent at 3 : 1 or more against the surface and the ground; each text token at 4.5 : 1 or more against the surface, the ground and a control's inside; the text on the accent against the accent and the ink against the rule |
| `ui.TheControlPanelsThemeTest.everyViewOfTheWindowAndAPopupShowingOverItTakeTheThemeAsTheSwitchFlips` | §1, §3, §6: the window drawn with a chart of five lines, the steps at extraction running 375 of 1,000, an analysis report's native view of a bar chart of two series and a table, a row of it selected in its focused table, a sample's tooltip and the table's context menu showing, shown over the table as a control shows it, its item under the keys; read in the light theme, again after the switch is pressed and nothing reopened, and again after it is pressed back: the plot, the chart's title, its time axis's title and ticks, its legend and the legend's first name, its five lines, the two series' bars, the step's bar's fill and track, its percent's halo, the leg's summary's face and name, the selected row and its text, an unselected row and its text, the tooltip and its text, and the context menu, its item and the item's text are each its token's value in the theme the window carries |

The first holds what the stylesheet and the classes write; the second what the window's nodes are given, through `WindowForTests` on the Headless platform. `ui.EveryBarOfTheStepsCarriesItsPercentTest.eachPercentStandsAtWcagAaAgainstItsHaloInBothThemes` keeps holding ADR-370 §4's halo.

## What the implementation owes

- `control-panel.css`: the five series tokens in each theme rule (§2, §4); the Modena colours §2 names in `.root`; the latest-sample rules naming `-vespera-series-1` to `-5`, and the three for `default-color5` to `default-color7` removed; a rule for each part of §3's table, the tooltip's setting Modena's `-fx-background` to the surface too, since Modena sets it on a tooltip, and a focused row's text set through `-fx-text-background-color`, which both theme rules set to the ink.
- `ThemeSwitch`: whenever it moves the root between the themes, each popup window showing whose owner is the window, or a popup of it, takes its style afresh, through a style class added to its root and removed, never a style (§1).
- `ThemeSwitch`'s javadoc citing this record where it says what the switch moves.
- The tests above passing.
