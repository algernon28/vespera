# ADR-293 — An operational record that is not a log or a configuration file is left to relevance, and its name ending is the operator's to list

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Decided**: under the operator's delegation, on the question of [#574](https://github.com/algernon28/vespera/issues/574), with the record shapes of the research of [#555](https://github.com/algernon28/vespera/issues/555).
- **Keeps**: [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) §7. An operational record that is not a log, a flow file or a lookup-table export among them, remains relevance's to remove, unless a rule of [ADR-287](0287-a-log-and-a-configuration-file-are-told-by-their-bytes-whatever-their-detected-format-and-a-configuration-file-is-kept-with-its-code-set-or-is-out-of-scope.md) reaches it (§2) or the operator lists the ending of its name (§3).
- **Keeps**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §2 and §4. The labeller is told, as before, that operational records are not relevant, and a label still only sets the floor.
- **Keeps**: ADR-287 §3, §5 and §7. The configuration rule, its reason and its floor are unchanged, and a JSON or YAML export it reads as settings is left as it leaves a configuration file (§2). This answers its Consequence *"A JSON or YAML file of records keyed line by line can read as settings. Data records are #574's, and the floor ships unset"*, and the item *Data records* of its *What this does not decide*. That record is left as written.
- **Keeps**: [ADR-256](0256-a-file-whose-name-ends-with-an-ending-the-profile-lists-is-out-of-scope-where-its-bytes-matched-no-signature.md) and [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md). A flow file and a lookup-table export are left out by the operator's list of name endings, or as a spreadsheet where one is a workbook (§3).
- **Keeps**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md), [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) and [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). No verdict kind, no detected format and no subtype is added.
- **Rests on**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md), observe before enforce, for a rule no measurement stands behind (§1).
- **Settles** [#574](https://github.com/algernon28/vespera/issues/574).

## Context

The operator's rule for this archive is that only documentation is relevant and the operational records are kept out. An operational record is what a program or a system writes or reads in its running: a log, a configuration file, a data export (CSV, JSON or XML holding records rather than settings), a flow file such as a `.OK` marker or a file of merchant rows, a lookup-table export. Stage 1 removes two of these kinds by their bytes, a log (ADR-171, widened by ADR-287 §1) and a configuration file (ADR-287 §3). Nothing in stage 1 is written for the others.

Facts from the code and the records:

- **The configuration rule reads a text file of no subtype, and a file of no known kind whose bytes read as text** (ADR-287 §3.1). A file named `.csv` is text with the subtype `CSV` and is never read by it. A file named `.tsv`, `.dat`, `.ok` or with no ending is text of no subtype, and is read.
- **A setting line is a whole line matching `SettingLines`' keyed value or section heading**, its leading white space dropped (ADR-287 §3.4). A pretty-printed JSON record, one key to a line, writes `"id": 1,`, which is a setting; its braces and the array's brackets are not. A YAML list of records writes `- id: 1`, which is not a setting, and then `  name: x`, which is. A JSON record written on one line, opening with `{`, is not a setting, nor is an XML line such as `<id>1</id>`, nor a delimited row such as `ORD0001;ESERCENTE;10,50`.
- **So the share a JSON export pretty-printed one key to a line reaches** is close to n ÷ (n + 2) for records of n keys: 50% for two keys, 60% for three, 80% for eight. A YAML list of records of n keys reaches (n − 1) ÷ n: 66% for three, 80% for five. Whether one is a configuration file depends on the floor the operator sets.
- **An XML export's first element is its own**, not one of `configurationRoots` (ADR-287 §3.3), and none of its lines is setting-shaped, so it scores near 0% and is not a configuration file.
- **The log rule reads every text file, a CSV among them, and every file of no known kind that reads as text** (ADR-287 §2), and counts a line whose first 64 characters hold one of its forms anywhere. One form is a run of fourteen digits, `\d{8}[ T_]?\d{6}`. A record export whose rows carry a date with a time, or fourteen digits together, in their first 64 characters is a log once `logTimestampShareFloor` is set; ADR-171's tests already remove a CSV-named file of timestamped rows as one.
- **An empty file is `broken`**, stopped by the cross-format floor (ADR-068) before its name is read, so a flow file of no bytes, as a `.OK` marker often is, is removed already.
- **The research of #555 names three record shapes**: repeated XML children of one name, a top-level array of objects of the same keys, and rows of a fixed delimiter. **Each is marked *Estimate: unmeasured***: no precision, no recall, and no count of the tables in documentation that a rule of that shape would take.
- **Relevance's floor is the lowest score among the labels that say relevant** (ADR-197 §4). An operational record labelled not relevant moves no floor of its own: it is removed where it scores below the floor, and kept where it scores above it.

## Decision

### 1. No rule of stage 1's for records, now

**Stage 1 gains no rule for data exports, flow files or lookup-table exports.** Every record shape the research names is unmeasured, and a rule of rows over a fixed delimiter, or of uniform children or objects, would also reach a table in a document: a removed document costs more than a record kept (ADR-287's last rejected alternative gives the same reason for `- key: value`). A record that no rule of stage 1's reaches is relevance's to remove, as ADR-171 §7 recorded, and the labeller's rule (ADR-197 §2) is unchanged.

### 2. Some records are told from settings by ADR-287's rule, and not all, and that is accepted

- **A CSV export**, and any text file whose name gives it a subtype, is never read by the configuration rule (ADR-287 §3.1).
- **An XML export** whose first element is not a configuration root, and whose lines are elements, scores low and is not a configuration file.
- **A JSON or YAML export written one key to a line reads as settings** (Context). Once `configurationSettingShareFloor` is set and its share reaches the floor, it is a configuration file: outside every code set it is `out-of-scope`, with ADR-287 §5's reason; inside one it is kept as `code`, a member of the set with the role `configuration`.

**This is accepted, not corrected.** It removes an operational record, which is the operator's rule; inside a code set, data written beside code, a fixture or a seed file of a test, belongs with it; and outside every set the verdict, `out-of-scope`, is the one a rule of records would write. **The reason calls it *a configuration file*, which it is not: that is the known imprecision**, and the reason's share says what was measured.

### 3. A flow file and a lookup-table export are the operator's to list by the ending of their name

**No rule is added for them.** Where an archive names its flow files and exports by endings of their own, `.ok`, `.dat`, `.csv` or another, the operator lists those endings in `outOfScopeNameEndings` (ADR-256), and stage 1 leaves the files out in its first pass. The list is read for a file detected `UNRECOGNISED` or `PLAIN_TEXT`, **a text file with a subtype included**: `OutOfScope.nameReason` reads the detected format and never the subtype, so a `.csv` is left out once `.csv` is listed. A lookup-table export saved as a workbook is already out of scope as a spreadsheet (ADR-146), and needs no listing.

### 4. No vocabulary, no profile key, no run id

No verdict kind, no detected format, no subtype and no profile key is added, and no code changes: **no stage's version moves, and no run id moves.** `CONTEXT.md` gains the entry **Operational record**.

## Alternatives rejected

- **A rule for rows of a fixed delimiter.** Unmeasured, and a table in a text document writes the same rows.
- **A rule for an XML document of repeated children of one name, or a JSON array of objects of the same keys.** Unmeasured; a document's table exported as XML or JSON is the same shape.
- **Keeping a JSON or YAML export out of the configuration rule**, by its top-level array or its list. It would keep a record the operator wants out, to be removed only by relevance, and would change ADR-287's rule, which would move every stage, for a reason text alone.
- **A reason, a verdict kind or a subtype for records.** A reason would need a rule that tells a record, which §1 declines; `out-of-scope` already means a kind of file left out whatever it holds, and a subtype is the name's word (ADR-095).
- **A built-in list of endings for flow files and exports.** The endings are the archive's, and differ between archives; ADR-256 made the list the operator's for that reason.
- **Telling the labeller more about records.** Its rule is the operator's, word for word (ADR-197 §2), and already names operational records.

## Consequences

- **A record that no rule of stage 1's reaches is scored by relevance**, and is removed only where it scores below the lowest relevant label. A record on the subject of the seeds can reach the deliverable; listing its ending is what removes it before.
- **With `configurationSettingShareFloor` set**, a JSON or YAML export written one key to a line whose share reaches it leaves the survivors in stage 1, called a configuration file, or is kept with its code set. One whose records have few keys may stay below the floor, and is relevance's.
- **With `logTimestampShareFloor` set**, a record export whose rows carry a date with a time, or fourteen digits together, in their first 64 characters leaves the survivors as a log.
- **An operator who lists `.csv` leaves out every file whose name ends so**, a CSV of documentation among them: the list reads names, not content.

## Tests

Every file is synthetic text the test writes, and the profile is written through `ProfileFixture`. The decision changes no code, so each test holds what stage 1 already does, and turns red where a rule for records is added, where ADR-287's rule stops reading such an export as settings, or where the list of endings stops reading a text file with a subtype.

| Class | What it holds |
| --- | --- |
| `pipeline.OperationalRecordsTaskletTest.anExportOfRecordsKeyedLineByLineIsLeftAsAConfigurationFileIs` | at a floor of 0.5, a pretty-printed JSON array of six records of three keys is 56% settings and left out with ADR-287's reason, a YAML list of the same is 66% and left out, and the same JSON inside a code set is `code`, a member as `configuration` |
| `…anExportNoRuleReachesCarriesNoVerdict` | under a log floor of 0.9 and a settings floor of 0.5, an XML export, a `;`-delimited `.dat` flow file, a `.tsv` and a `.csv` export carry no verdict |
| `…aFlowFileAndAnExportAreLeftOutOnceTheirEndingsAreListed` | with `.ok .csv` listed, a `.ok` flow file and a `.csv` export, stored as text with the subtype `CSV`, are each `out-of-scope` with ADR-256's reason naming the ending |

## What this does not decide

- **A rule for record shapes**, once measured. A later decision needs a measurement brief the operator runs over their archive: how many data exports, flow files and lookup-table exports each shape of the research takes, and how many tables in documents.
- **Records inside a container**, as converted text, as ADR-287 leaves logs and configuration files there.
