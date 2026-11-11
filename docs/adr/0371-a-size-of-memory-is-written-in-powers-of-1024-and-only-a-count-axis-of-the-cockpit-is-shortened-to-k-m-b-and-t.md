# ADR-371 — A size of memory is written in powers of 1,024, and only a count axis of the cockpit is shortened to K, M, B and T

- **Date**: 2026-11-11
- **Status**: accepted on 2026-11-11.
- **Decided**: by the operator, of the shortened ticks #789 drew: the cockpit's value-axis ticks are shortened to `K`, `M`, `B` and `T`, but not where the unit is memory; there a value is written in bytes, `KB` once it reaches 1,024, then `MB`, `GB` and `TB`; and powers of 1,024 are used only where the unit is memory; and where the unit is time, a tick is written in seconds, minutes and hours. The examples, the abbreviations and decimals of a time tick, the rounding at a unit's edge, the decimals of a memory axis's ticks and the tests are decided under delegation.
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §6, its sizes "in powers of 1,000 as ADR-265 counts megabytes" and the examples `2.05 KB`, `1.00 MB` never `1,000.00 KB`, which §1 below writes in powers of 1,024; and its value axis of a size, "the largest of the five in which the axis's span, margins included, is at least 1", which §1 counts in powers of 1,024. It carries a note at its head.
- **Keeps**: ADR-268 §6's other rules, bytes whole and every other unit to two decimal places, grouped as a count is, the same writing on the `database` tile, its growth, every tooltip and end label of the database chart and every value of an analysis report in `bytes`; the axis titled with its unit and its ticks plain numbers in it; every count, written as ADR-268 §6 writes it, in powers of 1,000 and never 1,024; [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, the database chart's values in the unit its axis title names; [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md), each sample's tooltip as it is; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar.

## Context

ADR-268 §6 wrote a size in the largest of `B`, `KB`, `MB`, `GB` and `TB` whose value is at least 1, in powers of 1,000: 2,050 bytes were `2.05 KB`. The jar's sampler titles the database chart's axis in that unit and writes its values divided by its power of 1,000, and the report's page draws them back to bytes the same way.

#789 shortened the cockpit's value-axis ticks, 1,500 reading `1.5K` and 2,000,000 `2M`, on every chart. On the database chart that would print a size of 51,700,000,000 bytes, whose axis is in `B` where it is constant, as `51.7B`, `B` there read as bytes and meant as billions. The operator asked for no such shortening where the unit is memory, and for memory's own units counted in 1,024.

## Decision

### 1. A size of memory is counted in powers of 1,024

A size is written in the largest of `B`, `KB`, `MB`, `GB` and `TB`, **each 1,024 of the one before**, whose value is at least 1: bytes as a whole number, every other unit to two decimal places, grouped by thousands. A value that rounds to 1,024.00 of a unit is written in the next. So 0, 1,023, 1,024, 2,050 and 1,234,567 bytes are `0 B`, `1,023 B`, `1.00 KB`, `2.00 KB` and `1.18 MB`; 1,048,575 bytes, 1,023.999 KB, is `1.00 MB`; and 5 × 1,024⁴ bytes is `5.00 TB`. Between 1,000 and 1,024 of a unit a size stays in it, grouped: 1,000,000 bytes are `976.56 KB`, and 1,030,000 bytes are `1,005.86 KB`.

The value axis of a size is written in the largest of the five in which its span, margins included, is at least 1, counted the same way: a span of 1,023 bytes is an axis in `B`, of 1,024 bytes in `KB`, of three times 1,024² in `MB`. The sampler titles the axis in that unit and writes the values divided by its power of 1,024, and the page multiplies them back by it. One rule writes them all in the jar, `Sizes` in `engine.pipeline`, which the sampler and the page both call. The control panel's tab of an analysis report writes a value in `bytes` by the same rule, held in `ui`, since it links no class of the jar (ADR-285 §3), so that the tab shows what the report's page shows (ADR-362 §10): 2,050 bytes are `2.00 KB` on both.

### 2. Only a size is counted in 1,024

A count, a share, seconds and a number are written as before, in powers of 1,000 where they are grouped. Of the values the report and the control panel's tabs write, only those whose unit is memory, the database's size, its growth and an analysis report's value in `bytes`, wherever each is drawn, are counted in 1,024.

### 3. The cockpit shortens the ticks of an axis that is not a size

Where the cockpit draws a chart whose value axis is not a size, each tick reads as #789 drew it: below 1,000 as it is, to two decimals at most, trailing zeros dropped; from 1,000 in `K`, `M`, `B` or `T` of 1,000 each, to one decimal at most, a tick that rounds to 1,000 of a unit written in the next. So 1,500 reads `1.5K`, 2,000,000 `2M` and 999,950 `1M`.

**A value axis whose title ends with a size's unit in parentheses**, `(B)`, `(KB)`, `(MB)`, `(GB)` or `(TB)`, is a size, and its ticks are never shortened: each is a plain number in that unit, grouped by thousands, to two decimals at most, trailing zeros dropped. So a constant database of 51,700,000,000 bytes on an axis titled `Database size (B)` reads `51,700,000,000`, and an axis titled `Database size (MB)` reads `51,700`, never `51.7K`. The cockpit reads the title the report gives the chart at each reading, so the rule follows the unit the jar chose.

### 4. A tick of time is written in seconds, minutes and hours

**An axis whose unit is time**, the cockpit's elapsed-time axis under each chart and any value axis whose title ends with a space and `(s)`, is never shortened to `K`, `M`, `B` or `T`. **Its unit is chosen for the axis as a whole, from the spacing of its ticks**: the largest of `s`, `min` and `h` in which that spacing is at least 1, so that two ticks side by side never read alike. Each tick is written in that unit, with a space before it, to one decimal at most, trailing zeros dropped, grouped by thousands. So ticks every 15 s read `0 s`, `15 s`, `30 s`, `45 s`; every 300 s, `0 min`, `5 min`, `10 min`; every 3,600 s, `2 h`, `3 h`; every 36,000 s, `100 h`, hours being the largest unit; and every 50 s, `7,250 s`. A step that started 2 h into the job and has run 5 minutes, its axis from 7,200 to 7,500 s ticked every 100 s, reads `120 min`, `121.7 min`, `123.3 min`, `125 min`, where a unit chosen tick by tick would read `2 h` twice. A title such as `Cluster(s)`, whose `(s)` is a plural with no space before it, is a count. The elapsed-time axis is titled `Elapsed`, since each tick now names its own unit.

## Alternatives refused

- **`KiB`, `MiB`, `GiB` and `TiB`.** The operator named `KB`, `MB`, `GB` and `TB`.
- **Counting every grouped number in 1,024.** A count of 1,500 rows is not memory; the operator asked for 1,024 only where the unit is memory.
- **Shortening a memory axis's ticks as a size, `51.7 GB` on each tick.** The axis title already names the unit (ADR-268 §6), and each tick holding its own unit would repeat it.
- **A time tick as a clock, `1:30:00`.** The operator named seconds, minutes and hours; the report's page keeps its clock (ADR-268 §7), drawn by the jar's template, not by the cockpit.
- **A unit chosen tick by tick, from each tick's own value.** On an axis that starts hours into the job and spans minutes, every tick would read the same number of hours.
- **Days past 24 hours.** The operator named hours as the largest unit, and no job's chart runs for days.
- **Telling a memory axis by the chart's id, `database`.** A later chart of a size would have to be named in the cockpit too; the title's unit is what the jar decides.

## Consequences

- Every size the report draws moves by up to 2.4% per unit from what it read before: 2,050 bytes read `2.00 KB`, not `2.05 KB`.
- A `logs/report-*.json` written before this record keeps a database chart divided by powers of 1,000 under the same unit's title; a page drawn from it now multiplies by 1,024, and reads its sizes up to 2.4% per unit high. No copy is drawn again unless a template is run on it by hand.
- The cockpit's count and verdict axes read `1.5K`, `2M` and so on; its database axis reads plain numbers in its unit; and every chart's elapsed-time axis, titled `Elapsed`, reads `45 s`, `10 min`, `2 h`, in the unit its ticks' spacing fits, where it read raw seconds.

## What this does not decide

- The decimals the report's page gives a memory axis's ticks, which ADR-268 §6 sets, and its clock-time axis, which ADR-268 §7 sets.
- A value in `seconds` on the control panel's tab of an analysis report, which is a value and not a tick, and reads as before.
- Any other chart of the cockpit; [#794](https://github.com/algernon28/vespera/issues/794) asks what each stage's chart plots.
- Two other values of memory, which a later record may bring under §1: the counter line of a model's pull, `megabytes pulled`, in millions of bytes (ADR-329 §5), and the control panel's free space, in `GiB` (ADR-331 §6).

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ReportHtmlTest.aSizeOfMemoryIsWrittenInPowersOf1024` | §1, §2: 0, 1,023, 1,024, 2,050, 1,000,000, 1,234,567, 1,048,575 and 5 × 1,024⁴ bytes are `0 B`, `1,023 B`, `1.00 KB`, `2.00 KB`, `976.56 KB`, `1.18 MB`, `1.00 MB` and `5.00 TB`; a span of 1,023, 1,024 and three times 1,024² bytes is an axis in `B`, `KB` and `MB`; a job whose database grew from 1,000 to 2,050 bytes on an axis in `KB` draws the tile `2.00 KB` and its two points' titles `1,000 B` and `2.00 KB`; and a count of 51,732,300 verdicts is still grouped by thousands, `51,732,300` |
| `ui.TheCockpitsValueTicksTest.aCountTickIsShortenedAndASizeTickIsNot` | §3: on an axis titled `Done (count)`, 2.5, 999, 1,500, 2,000,000, 999,950 and 3,000,000,000,000 read `2.5`, `999`, `1.5K`, `2M`, `1M` and `3T`; on axes titled `Database size (B)`, `Database size (MB)` and `Database size (KB)`, 51,700,000,000, 51,700 and 1.5 read `51,700,000,000`, `51,700` and `1.5`; and §1: on the control panel's tab of an analysis report, 512, 2,050, 1,030,000, 1,048,575 and -2,050 bytes read `512 B`, `2.00 KB`, `1,005.86 KB`, `1.00 MB` and `-2.00 KB`; and §4: on the elapsed-time axis, ticks every 15, 300, 3,600, 36,000 and 50 s read `0 s` to `45 s`, `0 min` to `15 min`, `2 h` and `3 h`, `100 h` and `7,250 s`; an axis from 7,200 to 7,500 s ticked every 100 s reads `120 min`, `121.7 min`, `123.3 min` and `125 min`, no two alike; and 5,400 ticked every 1,800 s reads `90 min` on an axis titled `Duration (s)` and `5.4K` on one titled `Cluster(s)` |

## What the implementation owes

Beside the tests above, which it turns green, the change adds `Sizes` to `engine.pipeline`, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as deciding no verdict, cache key, cluster or text of the deliverable, and has `ReportHtml` and `JobSampler` call it for every size, unit and divisor; writes the same rule in `AnalysisReportView` of `ui` for a value in `bytes`; gives `ProgressCharts` a tick formatter that reads its axis's title at each tick, and its elapsed-time axis, titled `Elapsed`, one that writes seconds, minutes and hours; and puts a note at ADR-268's head.
