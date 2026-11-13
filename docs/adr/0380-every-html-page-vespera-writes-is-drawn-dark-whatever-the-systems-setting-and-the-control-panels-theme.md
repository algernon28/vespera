# ADR-380 — Every HTML page Vespera writes is drawn dark, whatever the system's setting and the control panel's theme

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator on 2026-11-12, on [#794](https://github.com/algernon28/vespera/issues/794)'s Part C: every HTML page Vespera writes is drawn dark, whatever the operating system's setting and whatever the control panel's Light and Dark switch says; the analysis reports and `report.html` as much as the deliverable's pages (§1). Refused by the operator: following the system's setting by `prefers-color-scheme`, and drawing a page in the theme the window had when the page was written. The colours, the rule that every colour is a property of the stylesheet's root, the selectors that carry them and the tests are decided here, from ADR-379's dark tokens.
- **Amends**: [ADR-379](0379-the-control-panels-window-has-a-light-and-a-dark-theme-switched-in-its-header-and-kept-beside-the-launch-values.md) §5, *"`report.html`, each analysis report's page and the deliverable keep their own look"*: their own look is dark, drawn from §3's dark tokens, whatever the window's theme (§1, §2); and its What this does not decide, *"whether a report drawn outside the window takes the theme"*: it does not, every page being dark (§1). It carries a note at its head.
- **Amends**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), its one stylesheet: `ReportPage`'s shared rules set `color-scheme: dark` and draw every colour through a property of `:root` (§2, §3). It carries a note at its head.
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §1.1, *"The stylesheet is `ReportPage`'s shared rules (font, margins, table and cell borders)"*: it carries the same dark colours as `ReportPage`'s, as properties of `:root` (§3). It carries a note at its head.
- **Amends**: [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §4, *"one set for a light scheme and one under `@media (prefers-color-scheme: dark)`"*; [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §9, its table's Light column, its dark page, surface, grid and axis, and its *"in both schemes"*; and [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §6, *"The palette, its dark scheme and the contrast of ADR-268 §9 and ADR-267 §7 are the shipped template's stylesheet"*: the shipped template's stylesheet holds one set of colours, dark, under `:root`, with no `@media` of the scheme (§3). Each carries a note at its head.
- **Amends**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §3, *"Light and dark by `prefers-color-scheme`"*: the website rendering's theme is dark alone (§3). It carries a note at its head.
- **Amends**: [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) §4, *"No HTML file the jar writes changes"*: every one is drawn dark, its stylesheet as §3 sets it (§1 to §3); that none follows the window's switch stands. And its What this does not decide, *"Whether `report.html`'s dark scheme stays"*: it stays, the only scheme (§1). It carries a note at its head.
- **Keeps**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3 and ADR-379 §1 to §4: the window's stylesheet, its switch and its two themes are untouched. ADR-362 §5: a template `vespera.report.template` names draws what it draws. ADR-268 §9's five series colours, its three state colours and the running colour, at their dark values, and its relief channel, a legend, end labels and a table of values beneath each chart. ADR-274 §1.1 and ADR-333 §3's self-contained pages: no script, nothing fetched, the stylesheet inline or the website's own. [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md) and ADR-333 §14: no stage's version names `pipeline`, `deliverable` or `website`.
- **Settles**: [#794](https://github.com/algernon28/vespera/issues/794)'s Part C, for the HTML pages Vespera writes; what it asks of the window's own views stays open (What this does not decide).

## Context

**The pages Vespera writes.** Four kinds of HTML page are written by the jar, each with a stylesheet of its own:

| Page | Written through | Its stylesheet before this record |
| --- | --- | --- |
| eleven analysis reports, `format-mix.html` to `arrangement.html` | `pipeline.ReportPage`'s skeleton (ADR-130) | `ReportPage.STYLES`, inline: light alone, colours written as hexadecimal values in its rules (`#ccc`, `#eee`, `#4a90d9`, `#444`), the page's ground and text left to the browser |
| the HTML tree's index and cluster pages, and the analysis report `cluster-page-sizes.html` | `deliverable.Html`'s skeleton (ADR-274 §1.1) | `Html.STYLES`, inline: `ReportPage`'s rules written again, light alone |
| `report.html` | the shipped template, `report.html.mustache` (ADR-362 §5) | its one `<style>`: ADR-268 §9's palette, `color-scheme: light dark`, a light set under `:root` and a dark set under `@media (prefers-color-scheme: dark)` |
| the website rendering's pages | `website.WebsitePage`, linking `website.css` (ADR-333 §3) | `website.css`: `color-scheme: light dark`, a light set under `:root` and a dark set under `@media (prefers-color-scheme: dark)` |

**What is no page of Vespera's.** The Confluence rendering is storage format, fragments of XHTML Confluence Cloud draws in its own look; Vespera writes no stylesheet into it, so it is outside this record. A copy in the tree or the website is the archive's file byte for byte (ADR-274 §2), and is not drawn by Vespera even where it is HTML. A template `vespera.report.template` names is the operator's (ADR-362 §5). `vespera serve` answers a refusal as plain text. `docs/*.html`, the project's documentation site, and the test report the build leaves under `reports/` are written by this repository's tools, never by the jar.

**The dark tokens.** ADR-379 §3 gives the control panel's dark theme its colours: the text `#ecebf3`, the dim text `#aeaac2`, the surface `#25232f`, the ground `#1a1922`, the rule `#3b3949` and the accent `#b0a6ff`. Drawing the pages from the same six makes the window and the pages it links read as one.

**What was measured.** WCAG 2's contrast ratio of each colour against each background, from the hexadecimal values, by WCAG's relative luminance (a channel at or below 0.03928 divided by 12.92), rounded to two places. AA asks 4.5 : 1 of text of a normal size, and WCAG 2.1's 1.4.11 asks 3 : 1 of a graphical object needed to read a chart.

| Text | on the ground, `#1a1922` | on the surface, `#25232f` |
| --- | --- | --- |
| text, `#ecebf3` | 14.71 : 1 | 13.05 : 1 |
| dim text, `#aeaac2` | 7.73 : 1 | 6.86 : 1 |
| accent, `#b0a6ff` | 8.08 : 1 | 7.16 : 1 |

ADR-379 gives 7.74 : 1 for the dim text on the ground; the pair is 7.7347 : 1, and the difference is rounding.

| A mark of `report.html`'s charts and timeline | on the page, `#1a1922` | on the surface, `#25232f` |
| --- | --- | --- |
| `--series-1` and `--running`, `#3987e5` | 4.78 : 1 | 4.24 : 1 |
| `--series-2`, `#d95926` | 4.48 : 1 | 3.98 : 1 |
| `--series-3`, `#199e70` | 5.11 : 1 | 4.53 : 1 |
| `--series-4`, `#c98500` | 5.67 : 1 | 5.03 : 1 |
| `--series-5`, `#d55181` | 4.41 : 1 | 3.91 : 1 |
| `--finished`, `#0ca30c` | 5.19 : 1 | 4.60 : 1 |
| `--gate`, `#fab219` | 9.49 : 1 | 8.42 : 1 |
| `--failed`, `#d03b3b` | **3.62 : 1** | **3.21 : 1** |

The weakest text pair is the dim text on the surface, at 6.86 : 1; the weakest mark, the failed state's bar on the surface, at 3.21 : 1. The lines that only divide, the rule `#3b3949` and `report.html`'s grid `#34323f`, stand at 1.55 and 1.39 : 1 on the ground and 1.37 and 1.23 : 1 on the surface, as ADR-268's dark grid and axis stood at 1.24 and 1.48 : 1 on theirs.

## Decision

### 1. Every page is dark, and only dark

**Every HTML page Vespera writes is drawn dark**: the eleven analysis reports through `ReportPage`, the HTML tree's pages and `cluster-page-sizes.html` through `Html`, `report.html` through the shipped template, and the website rendering's pages through `website.css`. **Its stylesheet holds one set of colours**: no `@media (prefers-color-scheme: …)`, no `light-dark()`, and no second set chosen by a class. **Its root declares `color-scheme: dark`**, so that what the browser draws of its own, a scroll bar, a field, a link's default, is dark too.

**Neither the system's setting nor the window's theme is read.** A page is the same bytes whatever either says, and whenever it is written.

### 2. Every colour is a property of the stylesheet's root

**Each stylesheet has one `:root` rule, which declares `color-scheme: dark` and every colour the stylesheet draws, each as a custom property whose value is six hexadecimal digits.** No other rule writes a colour of its own, as a hexadecimal value, `rgb()` or `hsl()`: each draws through `var(--…)`. Every `color` declared outside `:root` draws through a property this record names as text.

### 3. The colours, page by page

**`ReportPage.STYLES` and `Html.STYLES`** carry the same root, and draw through it:

| Selector | Declares |
| --- | --- |
| `:root` | `color-scheme: dark; --ground: #1a1922; --surface: #25232f; --ink: #ecebf3; --dim: #aeaac2; --rule: #3b3949; --accent: #b0a6ff;` |
| `body` | `background: var(--ground); color: var(--ink);`, its font, margin and width as before |
| `a` | `color: var(--accent);` |
| `td, th` | `border: 1px solid var(--rule);`, its padding and alignment as before |
| `code.name` | `background: var(--surface);`, its size and padding as before |
| `blockquote` | `color: var(--dim);`, its style and margin as before |
| `.bar`, `ReportPage`'s alone | `background: var(--accent);`, its height as before |

Text: `--ink`, `--dim`, `--accent`. Backgrounds: `--ground`, `--surface`. Not measured: `--rule`. The accent drawn as `.bar` stands at 8.08 : 1 on the ground, a mark at 3 : 1 or more.

**`report.html.mustache`**: the two blocks of its `<style>` that set colours become one, and every other rule stands as it is:

| Selector | Declares |
| --- | --- |
| `:root` | `color-scheme: dark; --page: #1a1922; --surface: #25232f; --text: #ecebf3; --muted: #aeaac2; --grid: #34323f; --axis: #3b3949; --series-1: #3987e5; --series-2: #d95926; --series-3: #199e70; --series-4: #c98500; --series-5: #d55181; --finished: #0ca30c; --gate: #fab219; --failed: #d03b3b; --running: #3987e5;` |
| `@media (prefers-color-scheme: dark)` | gone |

Text: `--text`, `--muted`. Backgrounds: `--page`, `--surface`. Marks: `--series-1` to `--series-5`, `--finished`, `--gate`, `--failed`, `--running`. Not measured: `--grid`, `--axis`. The series and state colours are ADR-268 §9's dark values, unchanged, so its check of their separation stands.

**`website.css`**:

| Selector | Declares |
| --- | --- |
| `:root` | `color-scheme: dark; --background: #1a1922; --text: #ecebf3; --muted: #aeaac2; --line: #3b3949; --accent: #b0a6ff; --panel: #25232f;` |
| `@media (prefers-color-scheme: dark)` | gone |
| `#search::placeholder`, added | `color: var(--muted);` |

Text: `--text`, `--muted`, `--accent`. Backgrounds: `--background`, `--panel`. Not measured: `--line`. The comment at its head says the theme is dark alone. Every other rule, the narrow window's `@media (max-width: 40em)` among them, stands.

### 4. What the colours are held to

**Every text property of a page stands at 4.5 : 1 or more against every background property of that page, and every mark at 3 : 1 or more against every background**: the ratios are the Context's, the weakest text the dim text on the surface at 6.86 : 1 and the weakest mark the failed state on the surface at 3.21 : 1. **Every text property is lighter than every background**, so a page is light text on a dark ground. A property that only divides, a rule, a line, a grid or an axis, is named as not measured and held to nothing.

### 5. The modules, and run ids

`pipeline` changes in `ReportPage` and the shipped template, `deliverable` in `Html`, and `website` in `website.css`. **No run id moves**: no stage's version names `pipeline` (ADR-222, ADR-226), `deliverable` (ADR-274 §5) or `website` (ADR-333 §14), and no profile key changes, so no `config_consumed` does. The next invocation that reaches the tree redraws it, the website rendering with it, in place and at no cost in writing (ADR-274 §6); `report.html` is dark from the next write of the invocation report; an analysis report is dark from the next time its step writes it. A page an earlier version wrote stays as it was written until it is written again.

## Alternatives refused

- **The system's setting, by `prefers-color-scheme`.** Refused by the operator. It was what `report.html` and the website did, and it left the analysis reports and the tree light whatever the system said.
- **The window's theme, read as a page is written.** Refused by the operator. A page would differ by who wrote it and when, and a page written by `vespera run` from a shell has no window to read.
- **A light and a dark set on the page, switched by a class.** It needs a script to switch, and the tree's pages and `report.html` hold none (ADR-274 §1.1, ADR-362 §5).
- **ADR-268's dark page and surface, `#0d0d0d` and `#1a1a19`.** They would keep `report.html`'s charts on the ground they were checked on, and leave it apart from the window and the other pages; every series and state colour stands at 3 : 1 or more on the window's ground and surface (Context).

## Consequences

- An operator opening any page Vespera wrote, in a browser or from the window, sees it dark, on a light system as on a dark one, and with the window in either theme.
- The analysis reports, the tree's pages, `report.html` and the website rendering share one ground, surface, text, dim text and accent, the window's dark theme's.
- No schema, cache key, verdict kind, property, profile key or run id changes.

## Known limits

- **The ratios are those of the properties**, measured against the backgrounds the record names; the pixels a browser draws are not read. A text a rule places on a background the record does not pair it with, or a picture of a document on the dark ground, is not measured.
- **The lines that only divide are below 3 : 1** (Context): a table's borders, the website's lines, `report.html`'s grid and axis. None carries a value a reader needs; the values are in the text and the tables beside each chart.
- **What the browser draws of its own** under `color-scheme: dark`, a scroll bar, the date of a field, a focus ring, is its own and is not measured.
- **A page printed** is printed as the browser prints a dark page.

## What this does not decide

- **The window's own views in the dark theme**: its charts, the analysis reports drawn natively in its tabs, a tooltip, a context menu and the steps' bars, which ADR-379's What this does not decide leaves to [#794](https://github.com/algernon28/vespera/issues/794)'s Part C, and which no HTML page reaches.
- **[#794](https://github.com/algernon28/vespera/issues/794)'s Part B**, a chart for each stage.
- **What a template `vespera.report.template` names draws** (ADR-362 §5).

## Tests

| Method | What it holds |
| --- | --- |
| `EveryHtmlPageVesperaWritesIsDrawnDarkTest.theStylesheetIsDarkAloneAndEveryColourStandsAtWcagAa`, for each of four pages: an analysis report's page as `ReportPage.render` writes it, a page of the HTML tree as `Html.head` and `Html.tail` write it, `report.html` as the shipped template draws it, and the stylesheet a website rendering's page links from `WebsitePage.opening` | §1: the stylesheet names no `prefers-color-scheme` and no `light-dark(`, and has one `:root`, declaring `color-scheme: dark` and no other scheme; §2: no colour is written outside `:root`, `:root`'s colour properties are exactly the ones §3 names for the page, and every `color` declared draws through a text property; §4: every text property is lighter than every background, at 4.5 : 1 or more against each, and every mark at 3 : 1 or more |
| `website.TheWebsiteIsSelfContainedTest.nothingIsFetchedAndNothingNamesAHost`, amended | its claim that the theme is light and dark by the reader's preferred colour scheme goes; the rest stands |

`#search::placeholder` has no test of its own: it draws through `--muted`, which the test above holds as text, but the test does not require the rule to be there.

## What the implementation owes

- `ReportPage.STYLES` and `Html.STYLES` as §3 sets them, and their javadoc citing this record where it describes the stylesheet.
- `report.html.mustache`'s `:root` as §3 sets it, its `@media (prefers-color-scheme: dark)` block removed.
- `website.css`'s `:root` as §3 sets it, its `@media (prefers-color-scheme: dark)` block removed, `#search::placeholder` added and its head comment saying the theme is dark alone.
- `README.md`'s two lines that say the website's theme is light and dark and follows the system's setting, under the files of the deliverable and under what the website holds: dark, whatever the system's setting.
- The test above passing.
