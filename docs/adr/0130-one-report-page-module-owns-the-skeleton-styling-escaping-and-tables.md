# ADR-130 — One report-page module owns the document skeleton, styling, escaping and table assembly

- **Date**: 2026-09-19
- **Status**: accepted
- **Rests on**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) (the pages stay hand-assembled with no templating library and no new dependency — this record adds neither), [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) and [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md) (the prose each report supplies is rendered for that outside reader and is untouched here).

## Context

Six classes in `pipeline` each render a complete HTML page: `ArrangementReport`, `ClusterSizeReport`, `ConfidenceDistributionReport`, `FormatMixReport`, `RelevanceLabellingReport` and `SeedCorpusComparisonReport`. Each one assembled the document itself — the doctype, the character set, the title, a `<style>` block, the body and the closing tags — and each carried a private `escape` method:

```java
private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
}
```

That method was duplicated character-for-character in all six files. The stylesheet was duplicated with small variations: the `body`, `table` and `td, th` rules were common to all six, `td.count` to five, and `code.name`, `.bar`, `blockquote` and `li` each belonged to one or two. So the same document skeleton existed six times, the same escape table six times, and the same styling six times with four local additions.

**The duplication is a locality defect, not a length one.** A change to the skeleton — a viewport, a language, a different title tag — would have had to be made in six places, and five of them would be wrong until someone remembered. A change to the escape table is worse: escaping is the one rule on these pages whose failure is a rendering defect rather than a missing feature, and six copies are six chances for one to drift away from the others. ADR-129 exists because a document describing the tree drifted from the tree while nothing checked it; the same shape applies here, with nothing checking that the six escape methods still agreed.

**ADR-046 is not in the way.** It says the pom carries what a recorded decision requires, and the pages it governs are hand-assembled HTML with no templating library. It forbids a template engine; it does not forbid one module in which the hand-assembly happens. Consolidating six string-building methods into one class adds no dependency and leaves the HTML exactly as hand-written as before.

## Decision

**Introduce `ReportPage` in `pipeline` as the one module that assembles a report page.** It owns the document skeleton, the shared stylesheet, HTML escaping and the table/row scaffolding. Each report supplies only its title, its prose and its rows, and calls `ReportPage.render(title, body)` to get the page.

**The interface is deliberately small, and it is the seam.** A caller learns `render`, `escape`, `heading`, `paragraph`, `table` and `bulletList`, plus the cell constructors a row is built from — `headerRow`, `row`, `textCell`, `numberCell`, `htmlCell`, `linkCell`, `codeCell`. Everything the six reports used to repeat sits behind those names. The module's depth is the point: one interface entry buys a whole page, and the six call sites — and the tests that cross the same seam through each report's `render` — get the leverage of every rule behind it.

**Escaping moves inside the cell constructors.** `textCell`, `linkCell` and `codeCell` escape what they are given, so a caller cannot pass them unescaped text; a caller that means raw markup has to reach for `htmlCell` and say so. The escaping rule now lives in one place and is applied by construction rather than by convention.

**One stylesheet, the union of what the six pages used.** The shared block carries every rule any page relied on, so no page lost one. This is a visible change to the rendered `<style>` of a page that had a narrower block, and it is accepted: the styling is presentation, the tests assert content, and the alternative — a per-report style parameter — keeps six copies of the skeleton's half of the problem.

**The seam is inside `pipeline`, package-private.** No module boundary moves, no `allowedDependencies` changes (ADR-040), and no dependency is added (ADR-046). The six reports and the module are in the same package by the same composition-root reasoning that already put the reports there (ADR-040).

**The deletion test decides it was worth doing.** Delete `ReportPage` and the skeleton, the escape table, the stylesheet and the row scaffolding reappear in six files, exactly as before. The complexity does not vanish; it returns to every caller. That is a module earning its keep rather than a pass-through.

**What was refused.** Each report keeps its own private `escape` and the shared module offers only the skeleton is refused because the escape table is the rule whose drift is hardest to see; the cell constructors are where escaping is applied, and they belong in the module that owns the document. Templating the prose — a DSL or a template file — is refused by ADR-046: the prose stays in Java, and only the frame around it is shared.

## Consequences

**The six reports now read as what they are: a title, prose and rows.** Their class javadoc points at `ReportPage` (ADR-130) for the shared shape, and the local `escape` methods are gone.

**A future change to the skeleton, the styling or the escape table happens once.** The next report written beside the database uses `ReportPage` rather than copying a seventh skeleton, and adds no seventh `escape`.

**The interface is the test surface, and it is already crossed.** `ReportPage` has no test of its own because every test that renders a report crosses its interface through that report's `render`. The guards on the observable output — the words on the arrangement page (ADR-122), the numbers on the cluster page (ADR-087), the key the confidence page names (ADR-098), the refusals the labelling page makes (ADR-088), the statements the comparison page carries (ADR-086) — now defend the shared module as well as the report above it. A change to `ReportPage` that altered a page's content would turn one of those tests red.

**The rendered body is unchanged; the stylesheet is unified.** Every report's observable content — its headings, prose, tables and cells — is preserved, and the full unit suite passes unmodified. The one intended difference is the shared stylesheet, which is a superset of each page's former block.

**The reopen triggers are specific.** This record describes the tree no longer if a report needs a document shape the interface cannot express and reaches around `render` to write its own skeleton, if the styling a page needs stops being expressible as one shared block, or if ADR-046 is ever amended to admit a templating library — at which point the hand-assembly this record consolidated is the thing that would change, not the consolidation.