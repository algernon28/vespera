# ADR-371 — A size of memory is written in powers of 1,024, and only a count axis of the cockpit is shortened to K, M, B and T

- **Date**: 2026-11-11
- **Status**: accepted on 2026-11-11.
- **Decided**: by the operator, of the shortened ticks #789 drew: the cockpit's value-axis ticks are shortened to `K`, `M`, `B` and `T`, but not where the unit is memory; there a value is written in bytes, `KB` once it reaches 1,024, then `MB`, `GB` and `TB`; and powers of 1,024 are used only where the unit is memory. The examples, the rounding at a unit's edge, the decimals of a memory axis's ticks and the tests are decided under delegation.
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §6, its sizes "in powers of 1,000 as ADR-265 counts megabytes" and the examples `2.05 KB`, `1.00 MB` never `1,000.00 KB`, which §1 below writes in powers of 1,024; and its value axis of a size, "the largest of the five in which the axis's span, margins included, is at least 1", which §1 counts in powers of 1,024. It carries a note at its head.
- **Keeps**: ADR-268 §6's other rules, bytes whole and every other unit to two decimal places, grouped as a count is, the same writing on the `database` tile, its growth, every tooltip and end label of the database chart and every value of an analysis report in `bytes`; the axis titled with its unit and its ticks plain numbers in it; every count, written as ADR-268 §6 writes it, in powers of 1,000 and never 1,024; [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, the database chart's values in the unit its axis title names; [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md), each sample's tooltip as it is; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar.

## Context

ADR-268 §6 wrote a size in the largest of `B`, `KB`, `MB`, `GB` and `TB` whose value is at least 1, in powers of 1,000: 2,050 bytes were `2.05 KB`. The jar's sampler titles the database chart's axis in that unit and writes its values divided by its power of 1,000, and the report's page draws them back to bytes the same way.

#789 shortened the cockpit's value-axis ticks, 1,500 reading `1.5K` and 2,000,000 `2M`, on every chart. On the database chart that would print a size of 51,700,000,000 bytes, whose axis is in `B` where it is constant, as `51.7B`, `B` there read as bytes and meant as billions. The operator asked for no such shortening where the unit is memory, and for memory's own units counted in 1,024.

## Decision

### 1. A size of memory is counted in powers of 1,024

A size is written in the largest of `B`, `KB`, `MB`, `GB` and `TB`, **each 1,024 of the one before**, whose value is at least 1: bytes as a whole number, every other unit to two decimal places, grouped by thousands. A value that rounds to 1,024.00 of a unit is written in the next. So 0, 1,023, 1,024, 2,050 and 1,234,567 bytes are `0 B`, `1,023 B`, `1.00 KB`, `2.00 KB` and `1.18 MB`; 1,048,575 bytes, 1,023.999 KB, is `1.00 MB`; and 5 × 1,024⁴ bytes is `5.00 TB`. Between 1,000 and 1,024 of a unit a size stays in it, grouped: 1,000,000 bytes are `976.56 KB`, and 1,030,000 bytes are `1,005.86 KB`.

The value axis of a size is written in the largest of the five in which its span, margins included, is at least 1, counted the same way: a span of 1,023 bytes is an axis in `B`, of 1,024 bytes in `KB`, of three times 1,024² in `MB`. The sampler titles the axis in that unit and writes the values divided by its power of 1,024, and the page multiplies them back by it. One rule writes them all, `Sizes` in `engine.pipeline`, which the sampler and the page both call.

### 2. Only a size is counted in 1,024

A count, a share, seconds and a number are written as before, in powers of 1,000 where they are grouped. Only a value whose unit is memory, the database's size, its growth and an analysis report's value in `bytes`, is counted in 1,024.

### 3. The cockpit shortens the ticks of an axis that is not a size

Where the cockpit draws a chart whose value axis is not a size, each tick reads as #789 drew it: below 1,000 as it is, to two decimals at most, trailing zeros dropped; from 1,000 in `K`, `M`, `B` or `T` of 1,000 each, to one decimal at most, a tick that rounds to 1,000 of a unit written in the next. So 1,500 reads `1.5K`, 2,000,000 `2M` and 999,950 `1M`.

**A value axis whose title ends with a size's unit in parentheses**, `(B)`, `(KB)`, `(MB)`, `(GB)` or `(TB)`, is a size, and its ticks are never shortened: each is a plain number in that unit, grouped by thousands, to two decimals at most, trailing zeros dropped. So a constant database of 51,700,000,000 bytes on an axis titled `Database size (B)` reads `51,700,000,000`, and an axis titled `Database size (MB)` reads `51,700`, never `51.7K`. The cockpit reads the title the report gives the chart at each reading, so the rule follows the unit the jar chose.

## Alternatives refused

- **`KiB`, `MiB`, `GiB` and `TiB`.** The operator named `KB`, `MB`, `GB` and `TB`.
- **Counting every grouped number in 1,024.** A count of 1,500 rows is not memory; the operator asked for 1,024 only where the unit is memory.
- **Shortening a memory axis's ticks as a size, `51.7 GB` on each tick.** The axis title already names the unit (ADR-268 §6), and each tick holding its own unit would repeat it.
- **Telling a memory axis by the chart's id, `database`.** A later chart of a size would have to be named in the cockpit too; the title's unit is what the jar decides.

## Consequences

- Every size the report draws moves by up to 2.4% per unit from what it read before: 2,050 bytes read `2.00 KB`, not `2.05 KB`.
- A `logs/report-*.json` written before this record keeps a database chart divided by powers of 1,000 under the same unit's title; a page drawn from it now multiplies by 1,024, and reads its sizes up to 2.4% per unit high. No copy is drawn again unless a template is run on it by hand.
- The cockpit's count and verdict axes read `1.5K`, `2M` and so on; its database axis reads plain numbers in its unit.

## What this does not decide

- The decimals the report's page gives a memory axis's ticks, which ADR-268 §6 sets.
- Any other chart of the cockpit; [#794](https://github.com/algernon28/vespera/issues/794) asks what each stage's chart plots.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ReportHtmlTest.aSizeOfMemoryIsWrittenInPowersOf1024` | §1, §2: 0, 1,023, 1,024, 2,050, 1,000,000, 1,234,567, 1,048,575 and 5 × 1,024⁴ bytes are `0 B`, `1,023 B`, `1.00 KB`, `2.00 KB`, `976.56 KB`, `1.18 MB`, `1.00 MB` and `5.00 TB`; a span of 1,023, 1,024 and three times 1,024² bytes is an axis in `B`, `KB` and `MB`; a job whose database grew from 1,000 to 2,050 bytes on an axis in `KB` draws the tile `2.00 KB` and its two points' titles `1,000 B` and `2.00 KB`; and a count of 51,732,300 verdicts is still grouped by thousands, `51,732,300` |
| `ui.TheCockpitsValueTicksTest.aCountTickIsShortenedAndASizeTickIsNot` | §3: on an axis titled `Done (count)`, 2.5, 999, 1,500, 2,000,000, 999,950 and 3,000,000,000,000 read `2.5`, `999`, `1.5K`, `2M`, `1M` and `3T`; on axes titled `Database size (B)`, `Database size (MB)` and `Database size (KB)`, 51,700,000,000, 51,700 and 1.5 read `51,700,000,000`, `51,700` and `1.5` |

## What the implementation owes

Beside the tests above, which it turns green, the change adds `Sizes` to `engine.pipeline` and has `ReportHtml` and `JobSampler` call it for every size, unit and divisor; gives `ProgressCharts` a tick formatter that reads its axis's title at each tick; and puts a note at ADR-268's head.
