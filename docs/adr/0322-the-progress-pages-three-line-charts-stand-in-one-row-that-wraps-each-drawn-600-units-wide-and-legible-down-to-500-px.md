# ADR-322 — The progress page's three line charts stand in one row that wraps, each drawn 600 units wide and legible down to 500 px

- **Date**: 2026-10-23
- **Status**: accepted on 2026-10-23.
- **Decided**: by the operator, who asked for the three charts at the foot of the `Overview` tab to be resized and set side by side on one row, and chose that a narrower window wraps them onto further rows rather than keep them on one row and scroll each; the viewBox, the floor, the grid, the widths and the end labels are decided under delegation, on ADR-269 §7's principle that a chart's text is never drawn under 10 px high.
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md), in these places and no others: §7's "where two end labels would lie within 14 units of each other, one label holds both, `<name> <value>, <name> <value>`", which §4 below replaces with one label a series, set at least 14 units apart; and §7's "Each `<svg>` is never drawn narrower than its viewBox", already replaced by ADR-269 §7, which §2 below narrows further for the line charts. [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md), in these places and no others: §7's "no rule of it gives anything a `min-width` but the charts' below", and its third bullet, "Every `<svg>` has `min-width: 917px` … that is a window 983 px wide", which §2 below keeps for the timeline alone and replaces for the line charts; its Consequence "a window narrower than about 1,000 px … shows a scroll bar under each chart", which now holds of the timeline alone; the last clause of its alternative refused "A second, narrower drawing of each chart for narrow windows, chosen by a media query", "the scroll bar under a chart below 917 px is what the operator asked for", which now holds of the timeline alone, the refusal itself standing (§6); and what its test `ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls` holds of `min-width`.
- **Keeps**: the rest of ADR-268 and ADR-269, above all ADR-268 §7's three charts, their order, titles, captions, axes and anatomy but for the end labels, its eight units of right margin for each character of the longest end label, and §9's palette and type; ADR-269 §4's hover and §3's one fixed script, whose text does not change; ADR-269 §7's viewport tag, the body as wide as the window with a margin of 16 px, every `<svg>` `width: 100%; height: auto`, only a chart's or a table's card scrolling sideways and never the page, and its 10 px floor; [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §4, the SVG written in Java with no library, no JSON and no template engine; [ADR-275](0275-every-div-of-every-page-vespera-writes-says-on-hover-what-it-shows-and-every-step-of-the-timeline-what-it-does.md) §1, every `<div>` a page carries says on hover what it shows, the row of §1 below among them; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

The `Overview` tab, `progress.html`, draws, after its tiles, ADR-267 §1's timeline of the steps and then ADR-268 §7's three line charts, the database's size, the running step's counts and the verdicts of the outcome step, each a `<figure class="chart-card">` the width of the page, one below the other. Every drawing is 1,100 units wide; each line chart is 260 high. ADR-269 §7 draws every `<svg>` as wide as its card and no narrower than 917 px, `1100 × 10 / 12` rounded up, the width at which its 12-unit text is 10 px high, and its card scrolls sideways below that, which is a window 983 px wide. So the three charts take three rows of the page whatever the window, and a wide window draws each larger rather than beside the others.

The operator asked for the three resized and set on one row. Offered two ways for a window too narrow for three, each card keeping one row and scrolling, or the row wrapping, the operator chose to wrap: three across in a wide window, two and the third below in a narrower one, one a row in a narrow one, and no sideways scroll bar on the three where wrapping can avoid one.

A line chart drawn narrower than 917 px keeps its text 10 px high only if its viewBox is narrower too: drawn `<w>` px wide, a viewBox `<V>` units wide draws its 12-unit text `12 × <w> / <V>` px high.

**What a narrower viewBox does to the end labels.** ADR-268 §7 leaves to the right of the plot eight units for each character of the longest end label and joins two labels within 14 units of each other into one, `<name> <value>, <name> <value>`. In 1,100 units that costs little. In 600 it costs the plot: extraction's two kinds joined, `extraction-failed 12,345, degenerate-output 12,345`, are 50 characters, 400 units, and leave a plot of 132 units of 600; byte-level reduction's five kinds at zero, joined as they are while that step starts, `broken 0, duplicate-of 0, superseded-by 0, out-of-scope 0, code 0`, are 65 characters, 520 units, more than the chart has right of its value axis. One label a series is at most a kind's name of 17 characters, a space and a count: `degenerate-output 12,345`, 24 characters, 192 units, which leaves 340.

## Decision

### 1. The three line charts stand in one row, below the timeline

**The `Overview` tab draws, after its tiles and the bar of a running step's count, the timeline's card as ADR-268 §8 draws it, then one `<div class="chart-row" title="Charts over time: the database's size, the running step's counts and the verdicts of the step removing occurrences.">` holding the line charts' cards, in ADR-268 §7's order, the database, the counts and the verdicts**, whichever of them the write draws, and nothing else. Where ADR-268 §7 draws no chart of counts and says `<step> reports no count to draw.` in its place, that paragraph follows the row's close, so the row holds only cards and the paragraph keeps the page's width. Every write of `vespera run` draws the database chart, so the row is on every `Overview` tab; the label command's page draws no chart and has no row.

The row is a `<div>` so that it carries a hover text, as ADR-275 §1 requires of every `<div>`; its text says what the row shows, and no other `<div>` of the page shares it.

### 2. Each line chart is 600 units wide and never drawn under 500 px; the timeline stays as it is

**Each line chart's `<svg class="chart">` has the viewBox `0 0 600 260`**: 600 units wide, its height and everything ADR-268 §7 places by height unchanged. **Its `min-width` is `500px`**, `600 × 10 / 12`, the width at which its 12-unit text is 10 px high.

**The timeline's `<svg class="timeline">` keeps its viewBox 1,100 units wide and its `min-width: 917px`**, and its card, the width of the page, scrolls below a window 983 px wide as ADR-269 §7 has it. Its rows of step names, states and bars are not narrowed: its name column and state column take 320 of the 1,100 units before the bars start.

The stylesheet ADR-267 §4 gives the progress pages carries these rules for the drawings, and gives a `min-width` to nothing else:

```css
svg { display: block; width: 100%; height: auto; }
svg.timeline { min-width: 917px; }
svg.chart { min-width: 500px; }
```

### 3. The row is a grid that wraps

```css
.chart-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(max(min(534px, 100%), (100% - 48px) / 3), 1fr)); gap: 16px; margin: 16px 0; }
.chart-row .chart-card { margin: 0; }
```

- **534 px is a card at the floor**: the chart's 500 px, the card's padding of 16 px on each side and its border of 1 px on each side, `500 + 2 × 16 + 2 × 1`. No column is narrower while one fits.
- **`min(534px, 100%)`**: in a row narrower than one card, the one column is as wide as the row and no wider, so the page itself never scrolls (ADR-269 §7).
- **`(100% - 48px) / 3`, three gaps less than the row, a third**: the row never holds more than three columns. Three always fit, `3 × (R − 48) / 3 + 2 × 16 = R − 16`, and a fourth never does, `4 × (R − 48) / 3 + 3 × 16 = (4R − 48) / 3`, which is more than `R` for any row wider than 48 px. In a window wide enough for three, each column is a third of the row.
- **`auto-fill`, not `auto-fit`**: the columns are laid out whether the write draws three charts or fewer, so a write that draws two, while the census runs or a step reports no count, draws them a column each at the width three would have, and a chart does not widen across the page when its neighbour is not drawn and shrink again when it is.
- **16 px between cards and none around each**: the row takes the vertical margin the cards had, and a card in it has none of its own, so a card is 16 px from its neighbours in both directions, as the cards of the page are 16 px apart. A row's cards share the height of the tallest, the grid's default.

### 4. One end label for each series, and none on another

**Each series of a line chart has one end label, `<text class="end-label">` holding `<name> <value>`, or the value alone for a chart of one series**, as ADR-268 §7 writes it, at `x` 10 units right of the line's end. Two labels are never joined. **Their heights**: taken from the top of the chart down in the order of their lines' ends, ties in the order the series are drawn, each label's baseline is 4 units below its line's end, and moved down to at least 14 units below the label above it; then, where the lowest lies more than 4 units below the plot's bottom, it is moved up to 4 units below it, and each label above it moved up to at least 14 units above the one below. No two labels of a chart are then within 14 units of each other, and each lies between the plot's top and 4 units below its bottom: a chart draws at most five series, whose labels take 56 units of the plot's 168.

**The plot still leaves eight units to its right for each character of the longest end label, and ends there** (ADR-268 §7), so a label ends inside the viewBox. With one label a series the longest is a kind's name and its count, and the plot keeps more than half the chart.

A label moved off its line's end is read by its name, which every label of a chart of two series or more carries, and by the legend beside it; it is moved only as far as keeps it from the next.

### 5. What the narrower drawing leaves as it was

- **The value ticks**: at most five over the plot's 168 units of height, which does not change.
- **The time ticks**: at most five, each centred under its time across the plot. A tick is `<H:MM:SS>`, 8 characters from ten hours on, 64 units at eight a character, so two neighbours do not touch while the plot is at least 256 units wide, a quarter of it between them. The plot is `600 − 12 − 8 × <longest end label> − <left>`, `<left>` eight units a character of the longest value tick and eight more, at least 56: at least 256 while the longest end label and the longest value tick are 40 characters together, which a kind's name of at most 17 characters and counts below a billion, of 11 characters each, keep them to (`17 + 1 + 11 + 11`). Beyond it ticks may touch; the table of samples gives every sample's time.
- **The hover**: ADR-269 §4's script, unchanged, reads each chart's scale off its own viewBox, `svg.viewBox.baseVal.width / box.width`, and holds no width of its own, so it places the crosshair on a 600-unit chart as on an 1,100-unit one. Its tooltip, placed on the side of the pointer away from the card's nearer edge, holds a time and at most five lines of `<name>: <value>`, about 210 px wide at the stylesheet's tooltip text of 0.85 em, inside the half of a card that is at least 250 px wide.

### 6. The widths of window at which the row goes three, two and one across

`W` is the width the page is laid out at: the browser window's inner width, less the vertical scroll bar where the browser draws one beside the page. The row is `W − 32` px wide, the body's margin being 16 px on each side. `n` cards fit a row where `n × 534 + (n − 1) × 16 ≤ W − 32`:

| Cards across | Window | Arithmetic | Each chart's text |
| --- | --- | --- | --- |
| three | `W ≥ 1,666 px` | `3 × 534 + 2 × 16 + 2 × 16` | 10 px at 1,666 px, 11.6 px at 1,903 px |
| two, the third below at one column's width | `1,116 ≤ W < 1,666 px` | `2 × 534 + 16 + 2 × 16` | 10 px at 1,116 px, up to 15.5 px |
| one a row | `566 ≤ W < 1,116 px` | `534 + 2 × 16` | 10 px at 566 px, up to 21 px |
| one a row, its card scrolling | `W < 566 px` | | 10 px, the card narrower than the chart |

A chart's text is `<card's column> − 34` px over 50: a third of a row 1,871 px wide, a window of 1,920 px less a vertical scroll bar of 17 px, is 613 px, a chart of 579 px, text 11.6 px high.

**Below 566 px, a phone's width, the one column is narrower than a chart at its floor, and that card, and only it, scrolls sideways**: ADR-269 §7's 10 px floor is the principle this record keeps, and in a column narrower than 534 px no layout of a 600-unit chart keeps its text at 10 px without scrolling. Wrapping takes the scroll bar off the three charts at every width from 566 px up; ADR-269 §7 put one under every chart below 983 px.

**A second, narrower drawing of each chart chosen by a media query, refused by ADR-269, stays refused**: each line chart is drawn once, 600 units wide, at every width, and the row wraps by the grid alone, with no media query. The clause of that refusal that keeps a scroll bar under a chart below 917 px holds of the timeline alone.

### 7. The other tabs and pages

`Steps`, `Samples` and `Runs` draw no chart and are unchanged; the label command's page draws its tiles and its table of answers and no chart, and is unchanged. No other page Vespera writes draws a chart.

### 8. The modules, and run ids

`ProgressPage` and `ReportPage` of `pipeline` change: the line charts' width, the end labels, the row and the stylesheet's rules. No class is added. **No run id moves**: no stage's version names `pipeline` (ADR-226, `StageModules`), and the page decides no verdict, cache key, cluster or text of the deliverable. No property is added.

### 9. What the operator is told

The README's sentence on how the pages fit the window says that the charts of the database's size, the running step's counts and the verdicts stand three to a row in a window at least 1,666 px wide, two from 1,116 px and one below, and that a chart or a table scrolls sideways only where the window is too narrow to read it.

## Alternatives refused

- **Each card keeps the one row and scrolls in a narrow window.** Offered, and not chosen: the operator chose wrapping.
- **`repeat(auto-fit, minmax(534px, 1fr))`.** A write that draws two charts, or one, would stretch them across the page and shrink them when the third is drawn; with no cap, a window of 2,216 px or more fits four columns, and auto-fill would leave the fourth empty while auto-fit widens the three. And a row narrower than 534 px would hold a column wider than itself, scrolling the page.
- **Columns set by media queries at 1,116 and 1,666 px.** A media query reads the window's width with its scroll bar, so a window just past 1,666 px with a scroll bar of 17 px would draw three charts at 494 px, under the floor; the grid reads the row it lays out.
- **A narrower viewBox, 540 units, for three across from about 1,520 px.** It leaves the plot 60 units less beside the end labels and the value ticks, under half the chart for a label of 24 characters and ticks of 9.
- **The end labels kept joined, or set in a smaller type.** Joined, the Context: no plot at all for byte-level reduction's five kinds at zero. Smaller, under the 10 px floor at the chart's narrowest.
- **Dropping the end labels where a chart has two series or more.** ADR-268 §9 rests the palette's three light series under 3:1 on the legend, the end labels and the table of samples together.
- **The timeline narrowed or put in the row.** Its sixteen rows of step names and states need its width, and it is one drawing the operator did not ask to move.
- **A setting for any of it.** The operator asked for none.

## Consequences

- In a window at least 1,666 px wide, the `Overview` tab draws the database, the counts and the verdicts side by side under the timeline; narrower, two and the third below, and from under 1,116 px one a row. None of the three scrolls sideways from 566 px up.
- Each line chart's text is drawn at least 10 px high at every width from 566 px up, and a wide window no longer draws it 20 px high or more across the whole page.
- A chart of several series draws a label for each, one a line of text, none on another; a reader no longer finds two series in one label.
- The timeline still scrolls in its own card below a window of 983 px.
- No schema, cache key, verdict kind, property or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageChartsOnOneRowTest.theThreeLineChartsStandInOneRowBelowTheTimeline` | §1: with byte-level reduction running, its read and write counts moved and what entered it held, the overview holds one row with its hover text, after the timeline's card, holding the database's, the counts' and the verdicts' cards in that order and nothing else, and no card of the page follows it |
| `pipeline.ProgressPageChartsOnOneRowTest.eachLineChartIsDrawnSixHundredUnitsWideAndTheTimelineElevenHundred` | §2: each of the three line charts has the viewBox `0 0 600 260`, and the timeline's viewBox is 1,100 units wide |
| `pipeline.ProgressPageChartsOnOneRowTest.theRowWrapsThreeTwoAndOneAcrossAtTheWidthsOnRecord` | §3, §6: the row's rule is §3's, its 534 px is the line chart's `min-width` and the card's padding and border on each side, its cards have no margin, and with the body's margin and the gap, three fit from 1,666 px, two from 1,116 px and one from 566 px, and never four, at every row width from 300 to 4,000 px |
| `pipeline.ProgressPageChartsOnOneRowTest.eachSeriesHasAnEndLabelOfItsOwnSetApartAndInsideTheChart` | §4: byte-level reduction's five kinds at 3, 2, 1, 0 and 0 have five end labels, top to bottom in that order, the two at zero 14 units apart; read and written both 4 have one label each; every label of the three charts lies at least 14 units from the next, between the plot's top and 4 units below its bottom, and ends inside the viewBox at eight units a character |
| `pipeline.ProgressPageChartsOnOneRowTest.aStepThatCountsNothingIsSaidBelowTheRow` | §1: a running step that counts nothing leaves the row holding the database's card alone, and its sentence follows the row's close |
| `pipeline.ProgressPageChartsOnOneRowTest.theHoverReadsEachChartsScaleOffItsOwnViewBox` | §5: the script reads the scale off the chart's viewBox and holds neither width |
| `pipeline.ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls` | amended, §2: the stylesheet gives a `min-width` to `svg.timeline`, `917px`, and to `svg.chart`, `500px`, and to nothing else; the rest of what ADR-269 has it hold is unchanged |
| `pipeline.EveryDivOfTheProgressPagesSaysWhatItShowsTest.everyDivOfTheFourTabsCarriesAHoverTextAtTheFirstWriteAndTheLast` | amended, §1: the boxes the tabs draw include the row of charts, so its hover text is among those held |

ADR-268's tests that read end labels, `ProgressPageReadabilityTest.twoEqualSeriesAreBothDrawnLegendedAndLabelled`, `ProgressPageOutcomesInvocationTest.theLastWriteShowsWhatExtractionRemovedByKind` and `ProgressPageShowsCodeTest`'s, join every label of a chart before they look for a series' text, and are unchanged.

## What this does not decide

- **The look beyond what §1 to §4 fix**: where a chart sits in a card the row has made taller than it is the renderer's.
- **A chart of more than five series**, which none draws (ADR-268 §9).
- **The timeline in a window under 983 px**, which keeps ADR-269 §7's scroll bar.
