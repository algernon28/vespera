# ADR-275 — Every `<div>` of every page Vespera writes says on hover what it shows, and every step of the timeline what it does

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md), in these places and no others: §5's form of a tile, `<div class="tile" data-tile="<key>">`, which §2 below gives a `title` attribute, its three spans and their text unchanged; and §8's `<g>` of a step, which §4 below gives a `<title>` of its own as its first child. [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md), in these places and no others: §4's "The timeline has no hover beyond the `<title>` of each bar", which §4 below extends by the `<title>` of each step; and §7's `<div class="table-card">`, which §2 gives a `title` attribute. [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md), in one place: §1.3's "Its output stands in one `<div class="writing">`", which §2 gives a `title` attribute; what the renderer `ProseHtml` writes inside it, its twelve tags and its one attribute, is unchanged. [ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md), in the bar each grade's row draws, which §2 gives a `title` attribute.
- **Keeps**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §1, the progress page names no path, no title or text of a document, no exception message and no run id, and §3, it makes no query; [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §4, no library, no JSON and no template engine; ADR-269 §3, the one script, unchanged, which this record adds nothing to; ADR-274 §1.1, no script in the tree, §5, the tree keys no run, and §8, nothing outside the tree names a document; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), `ReportPage` owns the skeleton and escaping of the pages beside the database; [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §7, `StepNames` holds the step names; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), the pages are prose for the operator, a cluster rendered as *group*; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) and [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), no dependency.

## Context

The operator asked that every `<div>` of every HTML page Vespera writes carry a description shown when the pointer rests on it, saying what that part of the page shows, and that each step of the progress page's timeline say, the same way, what that step does.

What the pages write today, read off the shipped classes:

| Page | Written by | Its `<div>`s |
| --- | --- | --- |
| `progress.html`, the `Overview` tab | `ProgressPage` | `<div class="tiles">`, around one `<div class="tile">` a figure (ADR-268 §5) |
| `progress-steps.html`, `progress-samples.html`, `progress-runs.html` | `ProgressPage` through `ReportPage.tableCard` | `<div class="table-card">` around each table (ADR-269 §7) |
| the label command's `progress.html` | `ProgressPage` | `<div class="tiles">` and its tiles, and the table card of its answers |
| `confidence-distribution.html` | `ConfidenceDistributionReport` | `<div class="bar">` in each grade's row |
| a cluster's page in the deliverable | `deliverable.ClusterPage` | `<div class="writing">` around what the model wrote (ADR-274 §1.3) |

The other pages beside the database, the index of the deliverable and the progress page's charts write no `<div>`. The script of ADR-269 §3 adds `<div>`s of its own, the tooltip of a chart and its lines, only while the pointer is over the chart, and they are themselves the hover.

The timeline's steps are `<g class="step">` in an `<svg>`. An element of SVG takes no `title` attribute: its tooltip is a `<title>` child, as each chart's points and each bar already carry (ADR-267 §1, ADR-268 §7). A bar's `<title>` says the step's name, state and duration, and a step not started has no bar.

`ReportPage.escape` writes `&`, `<` and `>` as entities and leaves the quotes, "because nothing here writes text into an attribute unquoted"; a `"` in a double-quoted attribute ends it all the same. The deliverable's `Html.escape` writes all five (ADR-274 §1.3).

## Decision

### 1. Which elements

**Every `<div>` element of every page Vespera writes to disk carries a `title` attribute whose text is not blank**: the four tabs of `vespera run` and the label command's page (ADR-269), every page `ReportPage` writes beside the database, and every page of the deliverable (ADR-274). A `<div>` added to any of them later carries one from the change that adds it.

**The `<div>`s ADR-269 §3's script builds while the pointer is over a chart are not among them**: they are the hover, they stand on no page as written, and the script stays as ADR-269 fixes it.

### 2. What the text is

**A hover text is fixed words that say what the element shows**, written by the class that writes the element: a constant, or one text for each value of a closed set the element is keyed by, a tile's key, a verdict kind, a table of the progress page. **It holds no value of the invocation, the corpus or the run**: no count, size, time, rate, path, title, text of a document, message or run id. So a tile's hover is the same at every write and in every invocation while its value changes, and ADR-265 §1 and ADR-274 §8 hold of it by construction.

- **A tile**: `<div class="tile" data-tile="<key>" title="<text>">`, its spans as ADR-268 §5 has them. **No two tiles of a page share a text**: one for each key of ADR-268 §5 and of the label command's page, and one for each verdict kind, saying what the kind removes.
- **The row of tiles**, `<div class="tiles" title="<text>">`.
- **A table card**, `<div class="table-card" title="<text>">`: one text for each table, the steps, the samples, the runs and the label command's answers, saying what a row of it is.
- **A grade's bar** on `confidence-distribution.html`, `<div class="bar" style="…" title="<text>">`: one text, that the bar's length is the grade's share of the documents counted.
- **The writing** on a cluster's page, `<div class="writing" title="<text>">`: one text, that it is what the local model wrote over the group from the documents listed below it, each number in brackets a link to the document it cites. ProseHtml's output inside it is unchanged.

The words are prose for the operator (ADR-122): a cluster is a *group* in them.

### 3. How it is escaped

**A hover text is written through an escape for a double-quoted attribute, which writes `&`, `<`, `>`, `"` and `'` as `&amp;`, `&lt;`, `&gt;`, `&quot;` and `&#39;`.** In `pipeline` it is a method of `ReportPage` (ADR-130) beside `escape`, which keeps its three for text, so no page's text changes; in `deliverable` it is `Html.escape`, which writes the five already. A step's `<title>` is text and goes through `ReportPage.escape`.

### 4. Every step says what it does, from one place

**Each of the sixteen steps has one description, held in `StepNames` beside its name and read through `StepNames.description(String step)`**, which returns it for each name `StepNames` holds and throws `IllegalArgumentException` for any other. No other class holds a step's description. No two steps share one.

**Each step's `<g class="step">` in the timeline has as its first child `<title>` holding its step's description**, whatever its state, so a step not started says what it will do. The bar's own `<title>` stays as ADR-267 has it, so the pointer on a bar shows its state and duration and on the step's name or state its description.

What each says, in the words of the operator's pages:

| Step | What it does |
| --- | --- |
| `census` | walks the archive and records every file it finds, with its size and times |
| `byte-level-reduction` | removes files that are broken, exact copies, superseded or out of scope, judging their bytes alone |
| `extraction` | converts each file to text, removing those that cannot be converted or whose text is too poor to use |
| `content-census` | measures the converted text and writes the pages the confidence and boilerplate floors are read off |
| `redundancy-signature` | computes a signature of each document's text, leaving out boilerplate, so that near-copies can be found |
| `content-redundancy` | finds documents that repeat or contain another and keeps the fuller of each set |
| `seed-extraction` | converts the seed set, the documents known to be relevant, to text |
| `seed-corpus-comparison` | compares the seed set with the archive's documents and writes how far they differ |
| `embedding-scoring` | turns each document's text into vectors with the embedding model |
| `translation` | finds documents that are translations of one another and keeps one, in the language preferred first |
| `relevance-scoring` | scores each document by how close it lies to the seed set |
| `relevance-floor` | removes the documents scoring below the relevance threshold |
| `clustering` | gathers the documents under each seed into groups of documents alike |
| `relevance-report` | writes the page of documents to judge that the relevance threshold is chosen from |
| `arrangement` | names and orders the groups and writes the arrangement page to approve |
| `generation` | has the local model write over each group and writes the deliverable, with a copy of each document it lists |

### 5. The modules, and run ids

`ProgressPage`, `ReportPage`, `ConfidenceDistributionReport` and `StepNames` of `pipeline` change, and `ClusterPage` of `deliverable`. **No run id moves**: no stage's version names `pipeline` (ADR-226) or `deliverable` (ADR-274 §5), and a hover text decides no verdict, cache key, cluster or row. A deliverable already written is drawn again with its hover at the next invocation that reaches it, with no question put (ADR-274 §6). No setting is added.

## Alternatives refused

- **A hover that restates the value**, such as `51,732 survivors of 60,000`. The value is on the tile; a text built from it is a second place the page writes it, and one more string to keep off the pages ADR-265 §1 keeps clean.
- **One text for every tile**, or for every `<div>`. A hover that says the same thing everywhere tells a reader nothing the label does not.
- **A tooltip drawn by the script**, as ADR-269 §4 draws a chart's. A native `title` needs no script, works with none, and the deliverable may carry none (ADR-274 §1.1).
- **A `title` on the script's tooltip and its lines.** They are the hover; a hover over a hover is noise.
- **The steps' descriptions in `ProgressPage`.** The step names have one home (ADR-157 §7), and a step added there without a description would be a step the timeline cannot describe.
- **Widening `ReportPage.escape` to the quotes.** It would change the text of every page beside the database for no reader; an attribute's escape is its own method.

## Consequences

- A pointer resting on any figure, table, bar or block of writing says what it shows, and on any step of the timeline what that step does, in a browser with or without scripts.
- Each write carries a few hundred bytes more of fixed text; nothing more is read or computed.
- Tests that match a tile, a table card or the writing's `<div>` by its whole opening tag read the `title` too.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.EveryDivOfTheProgressPagesSaysWhatItShowsTest.everyDivOfTheFourTabsCarriesAHoverTextAtTheFirstWriteAndTheLast` | §1: with each of the five steps that remove occurrences in turn the outcome step, and a reading of one run, every `<div>` of the four tabs carries a `title` that is not blank, at the first write and the last, tiles and table cards among them |
| `pipeline.EveryDivOfTheProgressPagesSaysWhatItShowsTest.everyTileSaysWhatItShowsInWordsOfItsOwn` | §2: no two tiles of a page share a hover text; a tile's text is the same at every write while its value changes; every verdict kind's tile has one |
| `pipeline.EveryDivOfTheProgressPagesSaysWhatItShowsTest.everyDivOfTheLabelPageCarriesAHoverText` | §1: the label command's tiles, their row and the table card of its answers each carry a `title` that is not blank, at both writes |
| `pipeline.EveryDivOfTheProgressPagesSaysWhatItShowsTest.everyHoverTextIsEscapedForTheAttributeItStandsIn` | §3: every `<div>` of every page the tests write opens with attributes each a name and a double-quoted value holding no `<`, `>` or `"`, and every `&` in a value opens an entity |
| `pipeline.EveryStepSaysWhatItDoesTest.everyStepHasOneDescriptionOfItsOwn` | §4: each of the sixteen names `StepNames` holds has a description that is not blank, no two the same, and any other name is refused |
| `pipeline.EveryStepSaysWhatItDoesTest.everyStepOfTheTimelineSaysWhatTheStepDoes` | §4: each of the timeline's sixteen steps, started or not, has as its first child a `<title>` holding its description from `StepNames`, escaped |
| `pipeline.EveryDivOfAReportPageSaysWhatItShowsTest.everyBarOfTheConfidenceDistributionCarriesAHoverText` | §1, §2: every `<div>` of `confidence-distribution.html`, one bar a grade, carries a `title` that is not blank, the same for every grade |
| `deliverable.EveryDivOfTheTreeSaysWhatItShowsTest.everyDivOfEveryPageOfTheTreeCarriesAHoverText` | §1, §3: every `<div>` of every page of a tree of two groups written over and one not carries a `title` that is not blank, escaped for its attribute |
| `deliverable.EveryDivOfTheTreeSaysWhatItShowsTest.theWritingsHoverHoldsNothingOfTheCluster` | §2: the writing's hover text is one text on the pages of two clusters of different labels, titles and writing, and holds none of them |

Restated with the `title` of §2: `ProgressPageChartsTest`, `ProgressPageReadabilityTest` and `ProgressPageOutcomesInvocationTest`, whose pattern of a tile reads its opening tag whole, and `ProgressPageChartsTest.aCountOfATotalOfNoneDrawsNoBar`'s and `ProgressPageTabsTest.theLastWriteKeepsTheChartsAndTheHoverAndCarriesNoReload`'s opening tag of the `steps` tile; `ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls`, which counts `<div class="table-card">` before each `<table`; `ClusterPageTest`'s and `DeliverablePicturesTest`'s whole pages, and `TheTreeIsHtmlTest.rendersTheWritingOnItsPageWithEachCitationALink`, which finds `<div class="writing">`.

## What this does not decide

- **The words of each hover text but a step's**, which are the renderer's within §2.
- **A hover on elements other than `<div>`s and the timeline's steps**: a heading, a cell, a link or a chart, whose own `<title>` stands.
- **A keyboard's way to a hover text**, which a native `title` does not give.
