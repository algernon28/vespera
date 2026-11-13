# ADR-374 — Every text, line and marker of the window, its charts and each analysis report's native view stands at WCAG AA in both themes

> **Partly amended — see [ADR-380](0380-every-html-page-vespera-writes-is-drawn-dark-whatever-the-systems-setting-and-the-control-panels-theme.md).** Of §4: every HTML file the jar writes is drawn dark, whatever the system's setting and the window's theme; that none follows the window's switch stands. Of What this does not decide: `report.html`'s dark scheme stays, its only one. The rest of this record stands.

> **Partly amended — see [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md).** Of §2: the eight colours are the stylesheet's series tokens, `-vespera-series-1` to `-vespera-series-8`, set as `CHART_COLOR_1` to `CHART_COLOR_8` in both theme rules, and a bar of an analysis report's chart is one fill of its colour, with no gradient. Of What this does not decide: the colours' values are that record's. The rest of this record stands.

> **Partly amended — see [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md).** Of §1: the cockpit's charts are eight, one for each stage, and a frozen chart's lines and swatches, dimmed, are measured at the opacity they are drawn at. Of What this does not decide: #794's Part B is decided there. Of Tests: the first method draws the stages' charts from their files, stage 1's running and then frozen, in place of the three charts. The rest of this record stands.

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator, in [#786](https://github.com/algernon28/vespera/issues/786): that the charts' line colours, legend swatches and sample markers are checked against the dark background and given dark values where a pair stands under WCAG AA, 3 : 1 for a graphic and 4.5 : 1 for a text, the stylesheet owning them as ADR-342 §3 says; that the native views of the analysis reports draw from the stylesheet's tokens, so that they follow the window's theme with no code of their own; that the HTML files the jar writes follow nothing of the window; that the operating system's setting is not read, the switch deciding; and that every text, line and marker of the cockpit, its charts and each native view reads at AA in both themes, held by a test that computes the ratios. How a ratio is measured, what lies behind each thing measured, and the tests are decided under delegation.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md), *What this does not decide*, *"the colours, modena's eight or the stylesheet's own"*: they are the stylesheet's own, in each theme, at 3 : 1 against the plot and the legend (§2); and its Context's *"the window's stylesheet sets no colour of a chart, and modena's eight chart colours apply"*, which no longer holds once §2 is built. It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §10, the native view's parts: their code names no colour and sets no style, each colour being the window's stylesheet's in the theme the window wears, and what the view draws stands at AA in both themes (§1, §3). It carries a note at its head.
- **Keeps**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, one stylesheet owns every colour, size and space the window draws and no class sets a style of its own; ADR-365 §2, a line, its swatch and its samples coloured by one style class, eight colours named and each line of a chart its own, and its refusal of a copy of the page's palette in the window's stylesheet; [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md) §4, the percent's halo, and its test; ADR-362 §6, the shipped template's palette, and §9, the analysis reports' pages as their writers draw them; [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §9's palette, the template's.
- **Read with**: [#794](https://github.com/algernon28/vespera/issues/794), whose Part D is the record of the switch and the file that keeps the theme, and whose Part C asks how every panel follows the theme: this record decides the contrast of what the window draws in either theme, and neither of those.
- **Settles**: [#786](https://github.com/algernon28/vespera/issues/786).

## Context

**The two themes.** The control panel's one stylesheet, `control-panel.css`, names its colours as tokens on `.root`, `-vespera-ink`, `-vespera-dim`, `-vespera-surface`, `-vespera-ground`, `-vespera-rule`, `-vespera-accent`, `-vespera-accent-ink`, `-vespera-bad` and `-vespera-faint`, and redefines them on `.root.dark` with Modena's `-fx-base`, `-fx-background`, `-fx-control-inner-background`, the text colours and `-fx-prompt-text-fill`. A switch in the header, `theme-toggle`, puts the style class `dark` on the window's root or takes it off, and the choice is kept in a file `theme` beside the launch values; nothing reads the system's setting. Dialogs take the window's stylesheet and, where the window is dark, the dark tokens. The stylesheet's head says that every pair of its dark tokens stands at AA.

**What the stylesheet does not name.** It sets no colour of a chart but the latest sample's marker, which it draws in `CHART_COLOR_1` to `CHART_COLOR_8` by the line's `default-color<N>`: those eight are Modena's, `#f3622d`, `#fba71b`, `#57b757`, `#41a9c9`, `#4258c9`, `#9a42c8`, `#c84164` and `#888888`, the same in both themes. A chart's plot is drawn in `-fx-background`, the theme's ground, and its legend in Modena's gradient derived from it; a bar in a gradient derived from its colour. A text field's prompt is drawn in `-fx-prompt-text-fill`, which Modena sets on `.text-input` itself, so the value `.root.dark` gives it is not the one a field takes. An analysis report's native view, `AnalysisReportView`, names no colour: its tiles, sections, charts and tables take Modena's look and the stylesheet's tokens through the scene they are drawn in.

**What was measured**, on JavaFX's Headless platform with OpenJFX 26.0.2, the window drawn at 1500 by 960 from a first start's state, the cockpit's three charts drawing one, two and five lines, and a native view of a bars chart of eight series and a lines chart of three, each colour read as JavaFX resolves the stylesheet over Modena and measured as §1 says. The pairs under AA:

| Theme | What | Drawn | Against | Ratio |
| --- | --- | --- | --- | --- |
| light | a line and its marker, colour 1 | `#f3622d` | the plot, `#f5f4f1` | 2.89 : 1 |
| light | a line and its marker, colour 2 | `#fba71b` | the plot | 1.79 : 1 |
| light | a line and its marker, colour 3 | `#57b757` | the plot | 2.30 : 1 |
| light | a line and its marker, colour 4 | `#41a9c9` | the plot | 2.47 : 1 |
| light | a legend's swatch, colours 1 to 4 | as their lines | the legend's darker stop, `#ebeae7` | 2.65, 1.64, 2.10 and 2.26 : 1 |
| light | a bars chart's swatch, colour 8 | `#888888` | the legend | 2.95 : 1 |
| light | a bar, colours 1 to 4 | its gradient's lightest stop | the plot | 2.90, 1.82, 2.30 and 2.46 : 1 |
| light | a text field's prompt | `#b2b2b2` | the field's top stop, `#e8e8e8` | 1.72 : 1 |
| light | faint text, `grey`, in a section of the sidebar | `#6f6a85` | the section's content, `#e6e5e3` | 4.10 : 1 |
| dark | a line and its marker, colour 5 | `#4258c9` | the plot, `#1a1922` | 2.88 : 1 |
| dark | a legend's swatch, colour 5 | `#4258c9` | the legend's lighter stop, `#25242f` | 2.54 : 1 |
| dark | a bar, colours 5 and 6 | `#3e53bd`, `#903ebc` | the plot | 2.64 and 2.99 : 1 |
| dark | a bars chart's swatch, colours 5 to 7 | `#3e53bd`, `#903ebc`, `#bc3d5f` | the legend | 2.33, 2.64 and 2.91 : 1 |
| dark | a text field's prompt | `#1b1922` | the field, `#1f1d28` | 1.05 : 1 |

Every other text of the window and of the view, and every other mark, stands at AA in both themes. Five of the eight chart colours fall short in the light theme and three in the dark, so no one set of eight serves both.

## Decision

### 1. What stands at AA, and how it is measured

**In the light theme and in the dark, every text of the window stands at 4.5 : 1 or more against what is drawn behind it, and every line, legend swatch, marked sample and bar of a chart of the window at 3 : 1 or more against the chart's plot, a swatch against its legend**: the cockpit's header, actions, steps, output, sidebar and tabs, the cockpit's three charts, and each analysis report's native view. A text is held to 4.5 : 1 whatever its size.

**A ratio is WCAG 2's**, `(L1 + 0.05) / (L2 + 0.05)` of the two colours' relative luminances, measured on the colours JavaFX resolves from the stylesheet over Modena:

- **What is drawn**: a text's fill; a line's stroke; a swatch's, a marker's or a bar's outermost fill, the first of its background. A gradient counts each of its stops, and the least ratio stands.
- **What lies behind it**: for a text, the topmost fill of the nearest region it stands in, itself included, whose background holds a fill not wholly transparent; for a line, a marker or a bar, the chart's plot, read the same way from the plot's own region; for a swatch, its legend. A fill not wholly opaque is laid over what lies under it, down to the scene's white, and a gradient's stops each count.
- **What is measured**: every text shown, its node and every node it stands in visible, whose words are not blank; a text in a disabled control is not, as WCAG asks nothing of an inactive component, nor a text drawn wholly transparent, as a focused field's prompt is. A sample's hit area drawn transparent is no mark (ADR-365 §3). The window is read with each section of the sidebar open in turn and each tab selected in turn.

### 2. The stylesheet names the charts' colours of each theme

**The stylesheet names the eight colours a chart draws by `default-color0` to `default-color7` on `.root` and again on `.root.dark`**, each at 3 : 1 or more against that theme's plot and against its legend, and each bar's gradient with it; Modena's eight apply in neither theme. A line, its swatch and its samples keep one rule of colour (ADR-365 §2), and **each line of a chart keeps a colour no other line of it has**, eight colours named in each theme. The values are the stylesheet's (ADR-342 §3); they are not the shipped template's palette (ADR-365's refused copy).

**Every other text the stylesheet leaves to Modena stands at AA as well**: where §1 finds one under it, as a field's prompt in both themes and the faint text on a section of the sidebar in the light are, the stylesheet sets the colour, or the colour behind it, in that theme, by a token or by a rule on the control. No class does.

### 3. A native view draws from the stylesheet's tokens

**`AnalysisReportView` and its nested classes name no colour, paint, background, border or effect, and set no style**: no type of `javafx.scene.paint` or `javafx.scene.effect`, no `Background`, `BackgroundFill`, `Border` or `BorderStroke`, and no call of `setStyle`, `getStylesheets`, `setBackground`, `setBorder`, `setTextFill`, `setFill`, `setStroke`, `setEffect` or `setBlendMode`. Its parts carry style classes and Modena's own, and take their colours from the window's stylesheet in the scene they are drawn in. Its tab lies under the window's root, so **it follows the switch at once, with no code of its own reading the theme**, and what it draws stands at AA in both themes as §1 measures it. Its charts take §2's colours.

### 4. The jar's HTML files

**No HTML file the jar writes changes, and none follows the window's switch**: `report.html`, the analysis reports' pages and the deliverable are read in a browser, outside the window, and are drawn as their records draw them (ADR-362 §6 and §9, ADR-274).

### 5. The switch decides

**The window wears the theme its switch chose, and the system's setting is not read**: no class of the control panel asks JavaFX's platform preferences or the system for a colour scheme.

### 6. The modules, and run ids

Only `io.algernon.vespera.ui`'s stylesheet changes. No class of the jar, no file the jar writes and no property changes; no stage's version names the control panel. **No run id moves.**

### 7. What the operator is told

Nothing new: the window reads as it did, in colours that stand at AA in either theme.

## Alternatives refused

- **The charts' colours given dark values alone, the light theme's left at Modena's.** Five of them stand under 3 : 1 against the light plot, and the operator asked for AA in both themes.
- **The ratios computed by reading `control-panel.css` alone.** A text field's prompt takes Modena's rule on `.text-input` over the token the dark theme sets, which no reading of the stylesheet's own lines sees; the window is measured as JavaFX resolves it.
- **3 : 1 for large text.** No text of the window reaches WCAG's large size but a chart's title, at about 18 px and not bold; one threshold for every text is simpler to hold.
- **The template's palette copied into the stylesheet.** ADR-365 refused it: a second table free to drift from the template's, which the operator may replace.
- **A native view setting its colours in code for each theme.** It would be a second place that knows the theme, and ADR-342 §3 gives every colour to the stylesheet.
- **The system's dark setting as the default.** The operator chose the switch alone.

## Consequences

- An operator reads every text, line, swatch and marker of the window in either theme, charts and native views included.
- A colour the stylesheet or Modena changes that leaves a pair under AA fails the build.
- The colours of a chart in the window and in `report.html` differ, as ADR-365 already accepted.
- No schema, cache key, verdict kind, property, run id, file the jar writes or class outside the control panel's stylesheet changes.

## Known limits

- **The test reads colours as the stylesheet resolves, not the pixels drawn**: a line drawn thinner than a pixel, smoothed into its plot, is measured at its stroke's colour.
- **A gradient is measured at its worst stop**, which can fail a text that reads at AA over most of its field.
- **Only the window's own scene is measured**: a dialog, a tooltip and a context menu are scenes of their own, drawn from the same tokens and not measured; nor are a row selected, a control hovered or focused, a section of the sidebar other than the one open, or a step's row inside a folded leg.
- **One native view is drawn**, of each kind of part the twelve hold; the twelve share `AnalysisReportView`.

## What this does not decide

- **Whether `report.html`'s dark scheme stays.** The shipped template follows the browser's `prefers-color-scheme` (ADR-362 §6); #786 says the jar's HTML files stay light, which read so would remove it. §4 changes no file, and that question is the operator's.
- **The contrast of what is not text, line or marker**: a control's border, a focus ring, a progress bar's fill against its track, a chart's grid, axis and tick marks, the switch.
- **The values of the colours**, the stylesheet's within §2.
- **The switch, its place and words, and the file that keeps the theme**: #794's Part D.
- **#794's Part B**, a chart per stage; a chart it adds is a chart of the window, and §1 holds it.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.everyTextOfTheWindowAndEveryMarkOfItsChartsStandsAtAaInBothThemes` | §1, §2: the window from a first start's state, extraction running at 375 of 1,000, its database chart drawing one line, its counts chart two and its verdicts chart five; in the light theme and in the dark, set by the switch, with each of the sidebar's sections open in turn and each tab selected in turn, every text measured stands at 4.5 : 1 or more and every line, swatch and marked sample at 3 : 1 or more, as §1 measures them, a pair said once for each theme with the first text it was found on |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.aNativeViewNamesNoColourOfItsOwnAndStandsAtAaInBothThemes` | §3, §2: `AnalysisReportView` and its nested classes, read from their compiled classes, name nothing §3 lists; and the view of `format-mix`, opened from the Reports section, of two headline numbers, a paragraph, a bars chart of eight series, a lines chart of three and two tables of text, counts, a file and another analysis report, draws, in the light theme and in the dark, every text at 4.5 : 1 or more and every bar, line, marker and swatch at 3 : 1 or more |

Each method also claims that it found texts and chart marks to measure, so that a window drawn empty fails it. No test holds §4 and §5: neither changes anything, no file the jar writes reads the window, and no class of the control panel reads a system's colour scheme.

## What the implementation owes

Beside the tests above, which it turns green, the change gives the stylesheet §2's eight colours in each theme, and the colour of a field's prompt, and of the faint text or the section behind it, where §1 finds them short; it keeps the stylesheet's head saying what holds of its pairs. No class changes.
