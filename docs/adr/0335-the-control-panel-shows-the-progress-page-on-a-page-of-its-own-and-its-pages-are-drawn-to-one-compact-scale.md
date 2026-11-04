# ADR-335 — The control panel shows the progress page on a page of its own, in a frame, and its pages are drawn to one compact scale of space, type, corners and controls

> **Partly amended — see [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md).** Of §1: `/progress` is no page; the home page holds `#progress-view`, its frame and its states, and never asks to be loaded again, and `/progress` answers `303` to `/#progress-view`. Of §4: `#progress` links `/#progress-view`. The rest of this record stands.

- **Date**: 2026-11-03
- **Status**: accepted on 2026-11-03.
- **Decided**: by the operator, that the progress page is shown inside the control panel's window, and that the window's pages are more compact and modern; and, on 2026-11-03, that the four tabs of the progress page may be framed by the control panel's own pages, reversing ADR-302's refusal of a report in an `iframe` for them (§1, §3). The rest, where the page stands, what stands in its place before a progress page exists, its states, the scale and its values, and the words, was decided by Claude for the operator, who asked for the feature without naming them.
- **Keeps**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §1, [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md), [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md), [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md), [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) and [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md): the progress page is the jar's, written as those records have it, naming no path, title or text of a document, so that it can be sent to anyone; its four tabs, its palette, its widths, its script and its reload at `vespera.progress-interval`, while the invocation runs and after it has ended, are unchanged. No file under `src/main/java/io/algernon/vespera` changes.
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1 to §3: the child runs with `CI=1` and opens no browser, and the control panel shows `progress.html` itself, reading no `vespera.db`; this record is where on its screen. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 and §4: no class of the jar is linked, and `ControlPanelPage` owns every page's skeleton and style.
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §1, §3 and §12: no `GET` changes anything; every report is re-served as written under `sandbox allow-scripts`, its link opening in a tab of its own; and a `GET` under `/report/` sent `cross-site` is admitted. Its refusal of `sandbox allow-scripts allow-same-origin` stands.
- **Keeps**: [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §4: every page of the control panel's own holds one script, `/control-panel.js`, by its address. [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §1, its What this does not decide on a window's size, and §3's colours: the window is opened as it is, its size stays the browser's, and the colours keep their names and values. [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6's *Relevance* in the sidebar and §10's rule for `#tail`, whole.
- **Keeps**: [ADR-297](0297-the-control-panels-screens-are-tested-failing-first-through-the-pages-it-serves-read-as-a-browser-reads-them.md): every rule here is held through the pages served, their markup and their style read as text; no test renders a page or frames one. [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md): no dependency, font or plugin is added. [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): no name of ours is "dashboard", "GUI" or "panel" alone.
- **Amends**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §7, whose `Content-Security-Policy` of every response is, for the four tabs of the progress page under `/report/`, `frame-ancestors 'self'` in place of `frame-ancestors 'none'`, the rest of it and the other three headers unchanged (§3). It carries a note at its head.
- **Amends**: ADR-302 §2, whose `progress` on the invocation page links `/progress`, in the same window (§4); §3, whose four tabs of the progress page carry the policy of §3; and its alternative refused *"A report shown in an `iframe` of a control-panel page"*, which is reversed for those four, shown on `/progress` alone (§1, §3). It carries a note at its head.
- **Amends**: ADR-331 §3, whose sidebar leads to seven pages, `/progress` the third (§1); whose *"a page holds no `link`, `img` or other element that fetches anything, its one script apart"* gains `/progress`'s one frame (§1); and whose *"The words of a page and its styling, its `<title>` apart, stay no decision's"*, and the first item of its What this does not decide, which give way to §5's scale and to §1's words. It carries a note at its head.

## Context

**What the operator watches today.** `vespera run` and `vespera label` write the progress page, four tabs in the working directory (ADR-269), each reloading itself at `vespera.progress-interval`, while the invocation runs and after it has ended (ADR-327). The control panel's child runs with `CI=1`, so it opens none (ADR-278 §2), and the control panel re-serves each tab as written at `/report/<file name>` under `sandbox allow-scripts` (ADR-302 §3). The invocation page links `/report/progress.html` with `target="_blank"`, so the page opens in a tab of the default browser, or a window of the browser's own, beside the control panel's window (ADR-331 §1): the operator has two windows to watch one invocation.

**Why a frame needs a header changed.** Every response carries `Content-Security-Policy: frame-ancestors 'none'; form-action 'self'; base-uri 'none'` (ADR-291 §7), set first by the server for every path, and a report a second policy, `sandbox allow-scripts` (`ReportPages`). A browser enforces every policy a response carries, so a frame of a report is refused by the first whatever the second says, and ADR-302 refused a report in an `iframe` for that reason. `frame-ancestors 'self'` admits as an ancestor only the origin of the framed response's own address, which is the control panel's, so another site still frames nothing. A tab of the progress page holds no form, no control and no link but the bar of tabs (ADR-265 §1, ADR-269 §1), so a frame of it gives a page that frames it nothing to press. Its reloads and the links of its bar are sent `cross-site` by the sandbox, and a `GET` under `/report/` is admitted so (ADR-302 §12).

**What the control panel could draw itself.** It reads no `vespera.db` (ADR-278 §3) and links no class of the jar (ADR-285 §3). What it holds of an invocation is the child's output, the two holder lines and the file `progress.html`; the charts, tiles and samples exist only in the markup `ProgressPage` writes.

**What reloads what.** The invocation page asks to be loaded again every `InvocationPages.REFRESH_SECONDS`, 2 seconds, while its child runs (ADR-302 §1, ADR-325 §5). A frame inside a page that reloads is loaded again from its `src` with it, so a tab chosen in the frame, a leg folded in its address's fragment (ADR-324 §5) and a hover would go back to the first tab every 2 seconds. A frame whose own page reloads itself, as each tab of the progress page does, keeps the address it reached.

**How dense the pages are, as written.** The style sets `font: 15px/1.5`, a sidebar of `13rem`, 208 px at the default size, cards with `padding: 1rem` and corners of `.8rem`, and fields and buttons with `padding: .45rem` around a line of 22.5 px, about 38 px high. Its lengths are written in `rem`, `em` and `px`, each where it stands, and no two rules name a shared step. Worked out from those rules, one field of the launch page, its label, its control, its hint and the space under it, is about 97 px high.

## Decision

### 1. The progress page has a page of the control panel's own

**`/progress` is a page of the control panel's own**, a path the server is started with, past ADR-325 §2's checks, written through `ControlPanelPage.render`. A `GET` or `HEAD` shows it and changes nothing (ADR-302 §1); any other method is answered `405` with `Allow: GET, HEAD`.

**The sidebar leads to seven pages**, in this order: `/` Launch, `/invocation` Invocation, `/progress` Progress, `/next` Next, `/labels` Relevance, `/code-sets` Code sets, `/profile` Profile. `/progress`'s `h1` is `Progress` and its `<title>` `Progress · Vespera`, as ADR-331 §3 has every page's.

**`#progress-view`** carries `data-progress`, read as the page is asked for, whether or not the control panel's own child runs:

| `data-progress` | When | What the page holds | It asks to be loaded again |
| --- | --- | --- | --- |
| `no-working-directory` | no working directory is named, or one the system cannot hold (ADR-331 §6) | the words below, no frame, no `#progress-own-window` | never |
| `not-written` | the working directory named holds no file `progress.html`: nothing of that name, a folder of that name, or no such folder | the words below, no frame, no `#progress-own-window` | every `InvocationPages.REFRESH_SECONDS`, by `data-refresh-seconds` and the refresh inside `<noscript>`, as the invocation page asks (ADR-325 §5) |
| `shown` | the working directory holds the file `progress.html` | `#progress-frame` and `#progress-own-window` | never: the page in the frame reloads itself (§2) |

The words of `#progress-view`, whole:

| `data-progress` | Words |
| --- | --- |
| `no-working-directory` | `No working directory is chosen, so there is no progress page to show. Choose one on the Launch page, and the progress page of what runs there is shown here.` |
| `not-written` | `This working directory holds no progress page yet. It is shown here once Run or Label starts and the page is first written.` |
| `shown` | none |

**The frame** is the one element of the page that fetches anything beside its script:

```html
<iframe id="progress-frame" src="/report/progress.html" title="Progress page" sandbox="allow-scripts"></iframe>
```

its `sandbox` the same as the header the framed page is answered with (ADR-302 §3). **`#progress-own-window`** is a link to the same report, `href="/report/progress.html"`, `target="_blank"` and `rel="noopener"`, as every link to a report is (ADR-302 §3), with the words `Open in a window of its own`, so that the page is still opened, kept and sent on its own as before.

### 2. What the frame shows, and how it goes on

**The frame shows the progress page as the jar wrote it**, re-served by `/report/progress.html` from the working directory the control panel holds at each request (ADR-302 §3). Its bar of tabs links its companions by their bare names, so `Steps`, `Samples` and `Runs` open in the frame at their own report paths. Its script reloads it every `vespera.progress-interval` while its invocation runs and after it has ended (ADR-327 §1, §3), so the frame follows the invocation running and the next one in the same working directory, whether the control panel started it or not, and keeps the tab chosen and the legs folded across its reloads. `/progress` itself does not reload while the frame shows, so nothing reloads the frame from its `src`.

**The page in the frame keeps its own look**: its palette (ADR-268 §9), its widths and the scroll bar under a chart too narrow to read (ADR-269 §7, ADR-322), laid out in the frame's width. Nothing of §5 reaches it. Both follow the browser's scheme by `prefers-color-scheme`, so they are light or dark together.

**Nothing is added to the progress page.** The control panel frames the file as written and writes nothing into it, so the page names no path, title or text of a document (ADR-265 §1), and may be sent to anyone as before.

### 3. The four tabs may be framed by the control panel alone

**`/report/progress.html`, `/report/progress-steps.html`, `/report/progress-samples.html` and `/report/progress-runs.html` are answered with**

```
Content-Security-Policy: frame-ancestors 'self'; form-action 'self'; base-uri 'none'
Content-Security-Policy: sandbox allow-scripts
```

the first in place of ADR-291 §7's, so that the control panel's own pages may frame them and no other site's may. A tab not there is answered `404` with its one line, as before (ADR-302 §3). **Every other response keeps ADR-291 §7's `frame-ancestors 'none'`**: the twelve other reports, which still open in a tab of their own, and every page of the control panel's own, `/progress` among them. `Cache-Control`, `Referrer-Policy` and `X-Content-Type-Options` are unchanged on every response. The server's classes do not change: `ReportPages` sets the first policy of the four tabs in place of the one the server set, as it adds the second.

### 4. The invocation page leads to the progress page in the same window

**`#progress` on `/invocation` links `/progress`**, with no `target`, so it opens in the control panel's window; it stands where a working directory is named, as before (ADR-302 §2). The launch page's card of reports keeps its links as ADR-302 §3 has them, the progress page's among them, each opening in a tab of its own.

### 5. One compact scale

**The style names one scale of lengths**, as custom properties of `:root`, outside `@media (prefers-color-scheme: dark)`, so that both schemes share it:

| Property | Value | Property | Value |
| --- | --- | --- | --- |
| `--space-1` | `2px` | `--text-xs` | `11px` |
| `--space-2` | `4px` | `--text-sm` | `12px` |
| `--space-3` | `8px` | `--text-md` | `13px` |
| `--space-4` | `12px` | `--text-lg` | `15px` |
| `--space-5` | `16px` | `--text-xl` | `18px` |
| `--space-6` | `24px` | `--control-height` | `28px` |
| `--radius-sm` | `4px` | `--sidebar-width` | `176px` |
| `--radius` | `6px` | `--radius-lg` | `8px` |
| `--radius-pill` | `999px` | | |

No other property of the style is named `--space-`, `--text-` or `--radius` and a suffix, and the dark scheme's block names none of the scale. The same `:root` sets `color-scheme: light dark` and `accent-color: var(--accent)`, so that the browser's own parts of a page, a scroll bar, a list a `<datalist>` opens, the arrow of a `<select>`, follow the scheme and the accent of ADR-331 §3's colours.

**Every length of these kinds is a step of the scale**, in every rule of the style, a narrow window's included:

| Declarations | Each value is made of |
| --- | --- |
| `margin`, `padding` and each of their sides, `gap`, `row-gap`, `column-gap` | `0`, `auto` and `var(--space-1)` to `var(--space-6)`, separated by spaces |
| `font-size` | one of `var(--text-xs)` to `var(--text-xl)` |
| `font` | `inherit`, or a value naming one of `var(--text-xs)` to `var(--text-xl)` and no length of its own |
| `border-radius` | `0`, `var(--radius-sm)`, `var(--radius)`, `var(--radius-lg)` and `var(--radius-pill)` |

**What the scale is set to**:

- **Type.** `body`'s `font` is `var(--text-md)/1.4` and a family of the system's, `system-ui` first, no font fetched (ADR-331 §3); `h1`'s `font-size` is `var(--text-xl)` and `h2`'s `var(--text-lg)`.
- **Controls.** One rule, whose selectors are `input[type=text]`, `input[type=number]`, `select` and `button` among others, sets `height: var(--control-height)`, `border-radius: var(--radius)` and `font: inherit`, so that a field, a choice and a button standing in one row are one height and one shape.
- **The sidebar.** `#shell`'s `grid-template-columns` is `var(--sidebar-width) minmax(0, 1fr)`.
- **Density.** The last rule outside a `@media` that names `.card` sets its `padding` to `var(--space-4)`; `main.two-columns`' `gap` is `var(--space-4)`; and `#tiles`' `gap` is `var(--space-3)`.
- **The frame.** `#progress-frame` is `width: 100%` and `height: 80vh`, the frame as wide as the page's column and four fifths of the window high, so that the top row, the frame and the link under it stand in a window of 700 pixels.

So one field of the launch page, its label, its control, its hint and the space under it, is about 81 px high, worked out from these rules as Context works out 97 for those it replaces. **Every page carries the one style**, written by `ControlPanelPage`, so that the seven pages and `/folders` look alike.

**What stays as it is**: the colours, their names and their values in both schemes (ADR-331 §3); `#top-row`'s `position: sticky` and the narrow window's rule setting each grid to one column (ADR-331 §3); `#tail`'s rule (ADR-334 §10); and nothing fetched, no `url(`, `@import` or `@font-face` (ADR-331 §3). **The window's size stays the browser's** (ADR-331): nothing of how the control panel opens its window changes, and the pages are made compact by the style alone.

### 6. The classes, and run ids

In `io.algernon.controlpanel`, linking no class of the jar:

- **`ControlPanelPage`**: the seventh entry of the sidebar, and the style of §5.
- **`InvocationPages`**: `/progress`, registered beside `/invocation`, and `#progress`'s link (§1, §4).
- **`ReportPages`**: the first policy of the four tabs (§3).

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves; no dependency, plugin or font is added. The README's section on the control panel says that the progress page is shown on its Progress page, and that `Open in a window of its own` opens it alone.

## Known limits

- **What a browser does with the frame is not measured.** That a response carrying `frame-ancestors 'self'` and `sandbox allow-scripts` is framed by a page of its own origin, runs its script and reloads in the frame, rests on Content Security Policy Level 3 and the HTML standard, and no test frames a page (ADR-297). A browser that refuses the frame shows its own refusal inside it; `Open in a window of its own` opens the page as before.
- **A working directory changed or removed while the frame shows**: the frame's next reload reads the working directory then held, and where it holds no `progress.html` lands on the `404` line, which carries no reload, and stays there until `/progress` is asked for again.
- **After the control panel ends**, the frame reloads onto the browser's page for an address that does not answer, as a report's tab does (ADR-327 §4); the window comes back with the next start (ADR-325 §4) and asks for `/progress` again.
- **A window too short for the frame** scrolls twice, the page and the frame; a hover's tooltip near the frame's edge is cut by it.
- **`not-written` asks for the page every 2 seconds** for as long as it is open, as the invocation page does while its child runs.
- **The scale is in pixels.** A browser's zoom grows it whole; a default font size set larger in the browser's settings is not followed.

## Alternatives refused

- **The progress page drawn by the control panel itself.** It would read `vespera.db`, which ADR-278 §3 keeps it from, or the markup `ProgressPage` writes, a second drawing of the same charts that drifts from the first; the frame shows the page as the jar draws it, and the page stays one file the operator can send.
- **The frame on the invocation page.** That page reloads every 2 seconds while its child runs, which reloads the frame from its `src` and drops the tab chosen, a leg folded and a hover; and the Output box holds 45 of each 100 of the window's height (ADR-334 §10).
- **The frame shown while the working directory holds no progress page.** The `404` line carries no reload, so the frame would stay on it after the first write; `/progress` reloading itself until the file is there shows the frame once it is.
- **`/progress` reloading while the frame shows.** The frame would go back to the first tab at every reload; the page in it reloads itself.
- **`frame-ancestors 'self'` on every response, or on every report.** Only the four tabs are framed; every other page and report keeps a policy that frames nothing.
- **`allow-same-origin` in the frame's sandbox.** ADR-302's refusal stands: the page's script would run in the control panel's origin.
- **The scale in `rem`.** A browser's default size set larger would grow the type and not the borders and the frame drawn in pixels, and a control of `--control-height` would cut its own text.
- **A window size passed to the browser.** ADR-331 leaves the size to the browser; one size chosen here would be too large on a small screen and wasted on a large one.
- **A font fetched or carried**, a variable font as many modern pages draw. ADR-331 §3 fetches nothing, and the system's own is the one the operator's other programs use.
- **A choice of density, or a setting for the scale.** One scale serves every page; a second is a second layout to hold.

## Consequences

- The operator watches an invocation in the control panel's window: Progress in the sidebar shows the four tabs of the progress page, following each invocation in the working directory, the tab chosen kept as it reloads; the page still opens alone, and is still sent on its own.
- Before any invocation writes it, Progress says why there is nothing to show, and shows the page once there is.
- Every page is drawn smaller and closer: 13 px type, controls 28 px high, a sidebar of 176 px, cards and tiles one step apart, corners of 4 to 8 px, and the browser's own parts in the scheme and the accent.
- A rule added to the style takes its spaces, sizes and corners from the scale, or fails its test.
- The four tabs of the progress page may be framed by the control panel's own pages; nothing else of the control panel may be framed by anything.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheProgressPageInTheControlPanelScreenTest.withNoWorkingDirectoryThePageSaysSoAndShowsNoFrame` | no working directory: `no-working-directory`, its words, no frame, no link, no reload (§1) |
| `controlpanel.TheProgressPageInTheControlPanelScreenTest.aWorkingDirectoryWithNoProgressPageSaysSoAndReloadsUntilOneIsWritten` | a working directory holding none: `not-written`, its words, no frame, a reload every 2 seconds; the file written: `shown`, no reload (§1) |
| `controlpanel.TheProgressPageInTheControlPanelScreenTest.aProgressPageWrittenIsShownInAFrameOfItsReportPath` | the frame's four attributes, the link to a window of its own, one script, and no reload of the page before a child runs and while one runs (§1, §2) |
| `controlpanel.TheProgressPageInTheControlPanelScreenTest.theProgressPageChangesNothingAndAnswersOnlyAGet` | a `POST` from the control panel's origin: `405`, `Allow: GET, HEAD`, every file under the test's folder unchanged (§1) |
| `controlpanel.TheCompactScaleScreenTest.everyPageCarriesTheOneStyle` | the seven pages and `/folders`: one style, the same text (§5) |
| `controlpanel.TheCompactScaleScreenTest.theStyleNamesOneScaleTheSameInBothSchemes` | the scale's seventeen properties and values, no other of their families, none in the dark block (§5) |
| `controlpanel.TheCompactScaleScreenTest.everySpaceIsAStepOfTheScale` | every margin, padding and gap of every rule (§5) |
| `controlpanel.TheCompactScaleScreenTest.everyTypeSizeIsAStepOfTheScale` | every `font-size` and `font`; `body`, `h1` and `h2` (§5) |
| `controlpanel.TheCompactScaleScreenTest.everyCornerIsARadiusOfTheScale` | every `border-radius` (§5) |
| `controlpanel.TheCompactScaleScreenTest.everyFieldChoiceAndButtonIsOneHeightAndShape` | the one rule of the four controls (§5) |
| `controlpanel.TheCompactScaleScreenTest.theSidebarIsAsWideAsTheScaleSays` | `#shell`'s columns (§5) |
| `controlpanel.TheCompactScaleScreenTest.cardsAndTilesStandOneStepApart` | `.card`'s padding, the two columns' gap and the tiles' (§5) |
| `controlpanel.TheCompactScaleScreenTest.theBrowsersOwnPartsFollowTheSchemeAndTheAccent` | `color-scheme` and `accent-color` on `:root` (§5) |
| `controlpanel.TheCompactScaleScreenTest.theProgressFrameIsAsWideAsTheColumnAndMostOfTheWindowHigh` | `#progress-frame`'s width and height (§5) |

These change with this record, holding what they held otherwise: `controlpanel.TheControlPanelsLayoutScreenTest.everyPageHasTheSixPagesInASidebarWithItsOwnMarked` is `everyPageHasTheSevenPagesInASidebarWithItsOwnMarked`, and it, `everyPageHasATopRowHoldingItsTitleAndTheLaunchPageItsThreeButtons`, `everyPageIsTitledWithItsNameThenTheProgramsName` and `noPageFetchesAFontAStyleOrAPicture` read seven pages, `/progress` the third, the last admitting `/progress`'s one frame (§1); `controlpanel.ReportScreenTest.eachReportIsServedAsWrittenInASandboxAtItsOwnPath` holds `frame-ancestors 'self'` on the four tabs and `'none'` on the twelve others (§3); and `controlpanel.InvocationScreenTest.runStartsTheChildAndItsPageRefreshesEveryTwoSecondsWhileItRuns` holds `#progress` linking `/progress` in the same window (§4). `TheControlPanelsTabTest.everyPageOfItsOwnAllowsScriptsAndConnectionsOnlyFromItsOrigin` and `EveryPageHoldsOnlyTheControlPanelsScriptTest` read `/progress` among the pages of the control panel's own, unchanged; `TheOutputBoxKeepsItsHeightScreenTest` and `TheControlPanelsLayoutScreenTest.everyColourOfTheLightIsNamedAgainForTheDark` stand unchanged.

## What this does not decide

- **The colours' values**, which stay ADR-331 §3's, and the words of the other pages.
- **Whether the pages are checked for accessibility**, beyond ADR-331's.
- **How Firefox and Safari draw the frame**, and how a browser other than Chromium's family opens the window (ADR-331).
- **Whether the other reports are shown in the control panel's window**: they open in a tab of their own.
