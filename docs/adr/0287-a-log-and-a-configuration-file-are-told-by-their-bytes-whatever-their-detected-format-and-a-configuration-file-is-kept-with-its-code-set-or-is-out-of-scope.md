# ADR-287 — A log and a configuration file are told by their bytes, whatever their detected format, and a configuration file is kept with its code set or is out of scope

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Decided**: under the operator's delegation, on the six questions of [#575](https://github.com/algernon28/vespera/issues/575), with the technique taken from the research of [#555](https://github.com/algernon28/vespera/issues/555).
- **Amends**: [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) §1 and §2. The log rule also reads a file of no known kind whose bytes read as text (§2), and a timestamp is looked for in a line's first 64 characters, with an hour of one digit, and a line continuing the entry above it is not counted (§1). ADR-171's floor, its minimum of ten lines, its windows, its decoding, its reason and its page stand.
- **Amends**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §4 and §6. A code set gains the role `configuration`, written by a third pass of stage 1 after the set pass (§4), and answers its §2's *"A build file outside every set is #575's to decide"* (§6).
- **Amends**: [ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md) and [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md), in one place each: stage 1 still parses no container's internals, and reading the first XML element, or the root keys of a JSON or YAML document, of a text file is reading its bytes (§3.5).
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as ADR-171 and [ADR-256](0256-a-file-whose-name-ends-with-an-ending-the-profile-lists-is-out-of-scope-where-its-bytes-matched-no-signature.md) did. A configuration file outside every code set is `out-of-scope`, a kind of file this tool leaves out, under the verdict that exists.
- **Keeps**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md). No verdict kind and no detected format or subtype is added.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) and [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). Both rules read the bytes and never the name; a subtype, the name's word, only narrows what the configuration rule reads (§3.1).
- **Keeps**: [ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md) §3 and §6. The text test is its §3's, and the first pass keeps its order: the log rule runs where it ran.
- **Keeps**: [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) and ADR-256 §8. Seeds never pass stage 1, so neither rule reaches a seed (§9).
- **Rests on**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md), [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md), for a floor that ships unset (§7); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and ADR-171 §4, for stage 1's configuration consumed (§10); [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), by which every later stage follows stage 1's run (§11); [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md), for what the third pass holds (§4).
- **Replaces**: the format-mix page's sentence above its timestamp table, as ADR-277 §7 quotes it (§2), and the example in its paragraph under *What this page cannot see* (§8). Those records are left as written.
- **Settles** [#575](https://github.com/algernon28/vespera/issues/575).

## Context

Two kinds of text file are operational records and not documentation: a log, which records what a system did, and a configuration file, which holds the settings a program reads. Neither is what this knowledge base is for, and both are often on the very subject of the seeds, so relevance (stage 5) keeps them. ADR-171 removes a log in stage 1 by the share of its lines that begin with a timestamp. Nothing removes a configuration file: ADR-095 recorded that `desktop.ini`, an `.ini` or a `.bak` holding text is counted as text with no subtype, indistinguishable there from a note.

Four facts from the code and the records shape the rule.

- **ADR-171's rule reads `PLAIN_TEXT` only.** Text is recognised as UTF-8 from a 512-byte prefix, so a log written in a single-byte encoding, with a byte over `7F` in its prefix, is `UNRECOGNISED` and never read for timestamps. ADR-277 §3 already reads such a file as text where its first and last 64 KB hold no control byte but tab, line feed, form feed and carriage return.
- **Stage 1 judges a file in its first pass and a code set after it** (ADR-284 §6). Whether a file lies in a code set is known only after the set pass, and whatever stage 1 removes must be removed before the hashing, so that it is never hashed.
- **A name never decides what a file is** (ADR-094, ADR-171's survey: 2 of the 70 logs were named `.log`). ADR-284 §1 reads a build file's name only to mark a folder, and decides nothing about the file's content by it.
- **A log or a configuration file inside a PDF, a Word document or an RTF exists only as converted text**, after stage 2. Docling joins a PDF paragraph's lines with spaces, so no line-based rule carries over, and the one technique that reads converted text for what a file is for, the local generation model, has no published precision for this task (#555, §4).

### What #555 measured

On public samples only.

- **Logs.** Loghub publishes a 2,000-line sample from each of 16 systems. ADR-171's pattern, anchored at the line start, finds 7 of the 16 at a share of 90%. The same forms searched in each line's first 64 characters, with an hour written without its leading zero, find 12 of the 16: a timestamp after an address, a weekday, a level, a syslog priority or a file name is read. The four still missed write seconds since 1970, `YYMMDD HHMMSS`, a dash between date and time, or minutes and seconds unpadded. Over 630 of this repository's documentation, code and configuration files of ten lines or more, neither pattern reached 90% on any.
- **Configuration files.** The share of lines shaped `key = value`, `key: value` or `[section]`, blank lines and comments not counted, over 15 public configuration files: 100% on 6, then 97%, 85%, 81%, 78%, 66%, 61% and 54%, and 5% on a Properties file holding one value continued over 20 lines with `\`, and 0% on a policy file in a grammar of its own. Over the 16 Loghub samples, 0% on all. Over 301 documents of this repository, under 10% on 298 and 10% to 39% on 3. Fifteen files set no floor; they show the share separates these kinds, not where to cut.
- **Roots.** An XML file's first element and its namespace, and a JSON or YAML document's root keys, are fixed by the specifications of the formats that matter here: an XML Schema, a WSDL, an OpenAPI or Swagger description, a JSON Schema, a Maven project. No detector surveyed (Magika, Apache Tika, GitHub Linguist) labels a file by what it is for: Magika labels syntax only, and Tika and Linguist decide these kinds by the name.
- **Security.** A corpus file is untrusted. An XML reader fetching a DTD or resolving an external entity would read the network or the disk on a file's say-so.

## Decision

### 1. What a log is, widened

ADR-171 §2 stands, with three changes to how a line is read. `TimestampedLines` keeps its windows (the whole file up to 131,072 bytes, else its first and last 65,536 bytes, the two cut lines dropped) and its decoding (a UTF-16 or UTF-32 byte-order mark names the charset, otherwise ISO-8859-1).

1. **A continuation line leaves the count.** A line whose first character is white space (a space, a tab or a form feed), or that begins with `at ` (the two letters and a space), `Caused by:` or `...`, continues the entry above it: a stack frame, a wrapped message, a chained exception. It is counted neither among the non-blank lines nor among the timestamped ones. A blank line is not counted, as before.
2. **A timestamp is looked for in the line's first 64 characters**, wherever it lies in them, and no longer at the line's start only. A form must lie wholly within those 64 characters.
3. **The time alone may open with an hour of one digit**: `\d{1,2}:\d{2}:\d{2}` in place of `\d{2}:\d{2}:\d{2}`. The other four forms of ADR-171 §2 are unchanged.

A file is a log where at least **ten** lines are counted and the timestamped ones divided by the counted ones is at least `logTimestampShareFloor`, as before.

**Not added**: ten digits as seconds since 1970, and `YYMMDD HHMMSS`. Any ten-digit or six-and-six-digit number would match, and how many documents that would take is unmeasured (§13).

**The reason, the page's heading and its table keep ADR-171 §5's words**, *"…% of the lines read from its start and end begin with a timestamp"*: a line's first 64 characters are where it begins.

### 2. Which files the log rule reads

**An occurrence detected `PLAIN_TEXT`, with any subtype or none, as ADR-171 §1 reads; and an occurrence detected `UNRECOGNISED` whose bytes read as text by ADR-277 §3's test.** An `UNRECOGNISED` file's lines are decoded as ISO-8859-1, one character per byte: every character the forms look for is ASCII. A file of no known kind that does not read as text is not read for timestamps.

- **The first pass keeps ADR-277 §6's order**: `broken`, the kinds `OutOfScope` names, `outOfScopeNameEndings`, code, then the log rule, then the size rule. The size rule still reads `PLAIN_TEXT` only, since it is about what the converter is sent as text.
- **The detected format is unchanged.** A log of no known kind left out stays `UNRECOGNISED`, and is counted on the format-mix page under *Of no known kind*, as ADR-095 counts every removed file.
- **The timestamp table counts every file the log rule read**, of either class. The sentence above it reads: *"Each text file, and each file of no known kind whose start and end read as text, not left out for its kind or for the ending of its name, and not kept as code, was read from its start and its end, and the lines that begin with a date or a time were counted against the lines read that are neither blank nor the continuation of the line above. The share is that count in whole percent, rounded down."* It replaces ADR-277 §7's.

### 3. What a configuration file is

**A configuration file is a file whose content is settings for a program**, told from its bytes alone, never from its name, by the rule below. It is decided in stage 1, in the third pass of §4.

#### 3.1 Which files are read

**An occurrence detected `PLAIN_TEXT` with no subtype, and an occurrence detected `UNRECOGNISED` whose bytes read as text by ADR-277 §3's test.** A text file with a subtype, `HTML`, `MARKDOWN`, `CSV`, `ASCIIDOC` or `RFC822_MESSAGE`, is a page, a document or a record, and is not read: a subtype is the name's word, so it may only narrow what is read (ADR-094), never widen it. Every other detected format is judged by its bytes as before.

#### 3.2 An interface definition is never one

Read first. **A file whose first XML element, or whose JSON or YAML root keys, are those of an interface definition is kept, as a document, whatever its share**:

| Interface definition | Told by (v1) |
| --- | --- |
| XML Schema | first element `schema` in `http://www.w3.org/2001/XMLSchema` |
| WSDL 1.1 | first element `definitions` in `http://schemas.xmlsoap.org/wsdl/` |
| WSDL 2.0 | first element `description` in `http://www.w3.org/ns/wsdl` |
| OpenAPI | root key `openapi`, JSON or YAML |
| Swagger | root key `swagger`, JSON or YAML |
| JSON Schema | root key `$schema`, JSON or YAML |

Of the three root keys, the first one met among the root keys names it. An interface definition describes what a program offers, and is read by people who build against it, so it is a document. It becomes code only where the operator lists its ending in `codeNameEndings` (ADR-277 §2), which keeps it as code in the first pass, before this rule is reached.

#### 3.3 A configuration root

**A file whose first XML element is one of `configurationRoots` is a configuration file, whatever its share:**

| First element | Namespace (v1) | Written by |
| --- | --- | --- |
| `project` | `http://maven.apache.org/POM/4.0.0` | Maven |
| `configuration` | none | logback, and .NET |
| `Configuration` | none | log4j 2 |
| `beans` | `http://www.springframework.org/schema/beans` | Spring |

The local name is matched exactly, case included, and the namespace exactly, a prefix playing no part. **Both lists, §3.2's and this one, are built into `corpus`**, as ADR-284 §1's build files are, versioned `v1` (§10), and are changed by a change that carries a record. They are facts about formats, not about an archive, so the profile does not hold them.

#### 3.4 The setting-line share

Read for a file that is neither an interface definition nor of a configuration root. **`SettingLines` counts, from the bytes alone, the counted lines and the setting lines among them.**

1. **What is read** is what `TimestampedLines` reads: the same windows, decoding and cut lines (§1).
2. **A line is not counted** where it is blank, or where its first character that is not white space begins `#`, `;`, `!` or `//`: a comment, in the formats that write one so.
3. **A continued line is joined.** A counted line ending with an odd number of `\` is joined to the next line read: the last `\` is dropped, and so is the next line's leading white space, and the joined line is one counted line. This is the Properties format's continuation (`java.util.Properties.load`). A comment is never joined, and nothing is joined across the stretch between two windows.
4. **A counted line is a setting line** where, its leading white space dropped, the whole of it matches one of these (Java regular expressions):
   - a section heading: `\[{1,2}[^\[\]]+\]{1,2}\s*`, as in `[server]` or `[[bin]]`;
   - a keyed value: `(?:[A-Za-z0-9_][A-Za-z0-9_.\-]*|"[^"]+")\s*(?:=.*|:(?:\s.*)?)`. The key is a name of ASCII letters, digits, `_`, `.` and `-`, or a quoted JSON key; then `=`, or `:` followed by white space or by the end of the line, as YAML needs. So `port = 8080`, `server.port=8080`, `port: 8080`, `server:` and `"port": 8080,` are settings, and `port:8080`, `http://host`, `12:00`, `- name: x` and `Listen 80` are not.
5. **The count** is two numbers, the counted lines and the setting lines (ADR-073), and the share is setting lines × 100 ÷ counted lines in whole percent, rounded down.

A file is a configuration file by its share where **at least ten** lines are counted and the setting lines divided by the counted ones is at least `configurationSettingShareFloor` (§7). Ten is ADR-171's minimum, for ADR-171's reason. The windows, the minimum and the shape of a setting line define what is measured and live in `corpus`; the floor is the profile's.

**A guide about configuration, and a document quoting a configuration excerpt, are kept**: their lines of prose are counted and are not settings, so they dilute the share below any floor a configuration file meets, and the root rule needs the document's own first element.

#### 3.5 How the first element and the root keys are read

- **Only the first 65,536 bytes are read**, or the whole file where it is smaller, after a byte-order mark. The first character that is not white space chooses the reader: `<` the JDK's StAX reader, `{` Jackson's JSON streaming parser, anything else Jackson's YAML streaming parser. Both Jackson parsers are in the pom already, for the profile (ADR-061); no dependency is added (ADR-046).
- **StAX reads the bytes as given**, so an XML declaration names their encoding, and stops at the first start element: its local name and its namespace URI, none where it has no namespace, are the root. **`XMLInputFactory.SUPPORT_DTD` and `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES` are both false**: no DTD is read, fetched or resolved, and no entity is, whatever the file declares. A parse error before the first element, or no element in the bytes read, means no root.
- **Jackson reads the bytes decoded as §1 decodes them.** Where the first token opens an object or a mapping, the names of its root-level fields are read, each value skipped, until the object closes, the bytes read end, or a parse error; the root keys read before an error count. Of a YAML stream, the first document alone is read. A document whose first token is not an object has no root keys.
- **Reading the first element of a text file, or the root keys of a JSON or YAML document, is reading its bytes.** ADR-068 kept parse work out of stage 1 to keep a container's internals, Apache POI's work, for stage 2; ADR-167 cited it for an image's headers. That holds. Reading a text file's first element or root keys is bounded by 64 KB as every other read of stage 1 is, and stops when the answer is known.

#### 3.6 The order

1. An interface definition (§3.2) is kept, and counted as one.
2. Else a configuration root (§3.3) makes the file a configuration file, and it is counted under its root.
3. Else the share (§3.4) is counted into the page's table, and makes the file a configuration file where it reaches the floor.

**With the floor unset, nothing is a configuration file**, a configuration root included: the whole rule is switched on by the one value (§7). The share, the roots and the interface definitions are still measured and counted, for the page.

### 4. Stage 1's configuration pass

**Stage 1 is the first pass, then the set pass (ADR-284 §6), then the configuration pass, then the format-mix page and the measurements it points to, then the hashing.** What the configuration pass verdicts is never hashed.

- **It touches only a survivor of the set pass** that §3.1 reads. A log inside a set keeps its `out-of-scope` verdict, a set's build file keeps its `code` verdict and its `build` role, a file of a generated tree keeps its `companion` role, and a file left out for the ending of its name stays so. None of them is read again.
- **Inside a code set.** A configuration file lying in a set's extent, by ADR-284 §3's rule over the sets the set pass recorded under this run, is verdicted `code` with the reason *"a configuration file of the code set at shop/core"*, the folder as `code_set` holds it, or *"a configuration file of the code set at the corpus root"* for the root's set; and a `code_set_member` row records it with role `configuration` and no generator. Its log line is ADR-284 §6's: `[byte-level-reduction] kept <occurrence> with its code set -> code: <reason>`.
- **Outside every code set.** It is verdicted `out-of-scope` with the reason of §5. Its log line is `[byte-level-reduction] checked <occurrence> for configuration -> out of scope: <reason>`.
- **A configuration file never makes a set.** A set exists where a file carries `code` for itself (ADR-284 §2), and this pass runs after the sets are found.
- **A file it cannot read** is left as it was: no verdict, no member, and the pass carries on, logging `[byte-level-reduction] could not read <occurrence> to see whether it is a configuration file, so it is left as it was: <cause>`.
- **What it holds** is the set folders of the run, read from `code_set` by the run's key: folders, never occurrences. It reads the survivors and their detected formats a page at a time (ADR-220), and no exception to ADR-220 §9 is made.
- **An unfinished stage 1 redone** discards its rows as ADR-284 §6 says: the `out-of-scope` and `code` verdicts and the `code_set_member` rows, the `configuration` role among them.

**Why a third pass of stage 1, and not a rule of stage 2's.** It is a rule over bytes, as stage 1's others are. Stage 1 owns the sets (ADR-284), and the hashing must not see a file it removes. A rule of stage 2's, after ADR-277's skip of what stage 1 kept as code, would run after extraction and send the converter files about to be removed, and would split the set logic between two modules.

### 5. The verdict and its reason

**`out-of-scope`**, the verdict ADR-146 made for a kind of file this tool leaves out, whatever it holds. A configuration file is such a kind: a log and a configuration file are both operational records. The vocabulary stays as it is.

The reason, word for word:

- by its share: *"a configuration file, and configuration files outside every code set are out of scope: 96% of the lines read from its start and end are settings"*, the share as §3.4 states it;
- by its root: *"a configuration file, and configuration files outside every code set are out of scope: its first element is project, in the namespace http://maven.apache.org/POM/4.0.0"*, or, where the root has no namespace, *"… its first element is configuration, in no namespace"*.

### 6. A build file is a configuration file of a build

**A build file configures a build.** Inside a code set it stays ADR-284 §4's `build` member, kept by the set pass before this one. **Outside every code set** it is read by the configuration rule like any other file, by its content and never by its name: a Maven `pom.xml` in the POM namespace is a configuration file by its root, a `package.json` or a `Cargo.toml` mostly of keyed values is one by its share, and a `Makefile` or a `CMakeLists.txt` of rules and calls is not one and stays as the first pass left it. ADR-284 §1's list of names keeps one job: marking a set's folder.

### 7. The floor ships unset, and is not a gate

`configurationSettingShareFloor`, a numeric key on a 0-to-1 scale, typed `NumericValue` (ADR-120), with `value` and `provenance`. It is the last component of `Profile`, and its measurement points at `format-mix.html`.

- **Unset**, as it ships and as census writes it: no configuration rule. Stage 1 measures and reports (§8), and removes nothing and keeps nothing with a code set as a configuration file. This is observe before enforce (ADR-098): where a corpus's configuration files part from its documents is not known until measured, and #555's fifteen samples set no floor.
- **Answered**: the rule applies at that floor.
- **Unreadable** (ADR-120): treated as unset, recorded as `null` (§10), and named in the closing line after the line for shared name endings: *"Every run value is set, but configurationSettingShareFloor reads "…", which is not a number, so this run ignored it and found no configuration file. Next: write a share between 0 and 1 into configurationSettingShareFloor in profile.yaml, and run again."*
- **Not range-checked**, as no floor is (ADR-171 §3): above 1 finds nothing by its share, 0 or below makes every file read with ten counted lines a configuration file.
- **Not a gate.** An unset or unreadable floor does not end a run, as an unset `logTimestampShareFloor` and an unset `outOfScopeNameEndings` do not: stage 1 runs to its end and every later stage follows.

### 8. What the operator sees

- **The verdicts, their reasons and the log lines** (§4, §5).
- **The format-mix page.**
  - The out-of-scope paragraph adds, after the sentence ending with the log: *"So is a configuration file that lies in no code set, once configurationSettingShareFloor is set in profile.yaml."* Its count of files left out counts them, and its next sentence ends *"…, files left out for the ending of their name: N, and configuration files: C."*
  - ADR-284 §10's sentence reads *"Code sets: N, whose members are files of code: C, build files: B, configuration files: F, and files of generated reference trees: T."*, and the *Kept as code* table gains a row `configuration file` after `build file`.
  - A section headed ***How much of each text file is settings*** comes after the timestamp section:
    - *"Each text file of no finer kind, and each file of no known kind whose start and end read as text, still kept after what was kept as code and with the code sets, was read from its start and its end, and its lines shaped as a setting, a name followed by = or by a colon and a space, or a [section] heading, were counted against the lines read that are neither blank nor a comment. A line ending with a backslash is read with the line after it. The share is that count in whole percent, rounded down."*
    - Unset: *"No floor is set, so no file was found to be a configuration file. Setting configurationSettingShareFloor in profile.yaml turns the rule on: a configuration file is then kept with its code set where it lies in one, and left out otherwise."* Answered: *"The floor is 80%: a file of ten lines or more at or above it, or whose first element is one of those below, is a configuration file. Configuration files: N, of which kept with their code set: K, and left out: L."*, the floor written as ADR-171 §5 writes the log floor.
    - A table, *"Share of lines that are settings"* by *"Files"*, of the files §3.6 counted by their share, in ADR-171 §5's bands, *"fewer than ten lines"* first.
    - *"Files whose first element is a program's configuration, which makes each one a configuration file whatever its share once the floor is set: R."*, and where R is not 0 a table, *"First element"* by *"Files"*, one row for each root found, written *"project, in the namespace http://maven.apache.org/POM/4.0.0"* or *"configuration, in no namespace"*.
    - *"Interface definitions, kept as documents whatever their share: I."*, and where I is not 0 a table, *"Interface definition"* by *"Files"*, one row for each named in §3.2 that was found, by its name there.
  - The paragraph under *What this page cannot see* names *"an editor's leftover backup"* as its example, and no longer *"a folder settings file"*.
  - The page names no file and no folder.
- **The profile**: after writing the page, stage 1 points `configurationSettingShareFloor`'s measurement at `format-mix.html`.
- **The closing line**, for an unreadable floor (§7).
- **The progress page, the tab of runs and the invocation account** count these verdicts under `out-of-scope` and `code`, as they count every such verdict.

### 9. Seeds, and converted text, are not reached

**Seeds never pass stage 1** (ADR-256 §8), so neither rule reaches a seed, and ADR-083's bar is unchanged.

**A log or a configuration file inside a PDF, a Word document, an RTF or another container is not decided here.** It exists only as converted text after stage 2, no measured technique reads it (§13), and nothing in stage 2 changes.

### 10. Stage 1's configuration consumed

`ConfigConsumed` gains five components after `generatorCompanions`:

- `timestampedLines`, the version of §1's reading of a line, `"v2"`; ADR-171 §2's, never recorded, is `v1`;
- `configurationSettingShareFloor`, the floor, `null` where unset or unreadable;
- `settingLines`, the version of §3.4's reading, `"v1"`;
- `configurationRoots`, the version of §3.3's list, `"v1"`;
- `interfaceDefinitions`, the version of §3.2's list, `"v1"`.

A rule or list built into `corpus` is recorded by its version, as ADR-277 §9 and ADR-284 §10 record theirs. Where the profile sets none of the keys stage 1 reads, it reads:

`{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null,"outOfScopeNameEndings":[],"codeNameEndings":[],"generatorMarks":"v1","buildFiles":"v1","generatorCompanions":"v1","timestampedLines":"v2","configurationSettingShareFloor":null,"settingLines":"v1","configurationRoots":"v1","interfaceDefinitions":"v1"}`

### 11. Run ids, and the ADR-216 check

- **`corpus`**: `TimestampedLines` (§1), the log rule's reach in `BrokenOrOutOfScope` (§2), `SettingLines`, the two lists, the reading of a first element and of root keys, the configuration pass and its `configuration` role (§3, §4), and `FormatMix` or the pass's own tally.
- **`profile`**: `Profile` gains the key.
- **`pipeline`**: `ByteLevelReductionTasklet` (the pass's call, its log lines, the order, the measurement and the configuration consumed), `FormatMixReport` and `NextAction`. No stage's version names `pipeline` (ADR-222).
- **`ledger`**: none. No table and no column is added: `code_set_member.role` is text, and gains a value.

**Stage 1 moves, whatever the profile holds**: its version names `corpus`, and its configuration consumed gains five fields. **Every later stage follows**, each naming the run upstream of it (ADR-089). Setting the floor, or changing it, moves stage 1 again, and every later stage with it.

**What that costs in kind.** Stage 1 checks every file occurrence again, and reads up to 128 KB of each file of either class twice. Stage 2 asks the converter nothing about content it already holds an answer for: neither the content hash nor the extractor identity changes. Every later stage recomputes what it keys by run.

### 12. `CONTEXT.md`

- **Log** widens from a text file to a file whose bytes read as text, detected as text or of no known kind, read as §1 reads; still not a container's converted text.
- **Configuration file** and **Interface definition** are added.
- **Out of scope**, **Code set** and **Build file** name the configuration file.

## Alternatives rejected

- **Reopening ADR-277's skip so that stage 2 judges configuration.** The bytes rule would run after extraction, sending the converter files about to be removed, and the set logic would be split between `corpus` and `extraction`.
- **A rule over converted text, or the local generation model.** It would reach a log inside a PDF, but its precision on this task is unpublished, Docling joins a paragraph's lines, and a call per file would take hours over an archive. Left open (§13).
- **Magika, Apache Tika or GitHub Linguist.** Magika labels syntax and not purpose, and needs a native binary or a 51 MB runtime; Tika and Linguist decide these kinds by the name. Each would be a dependency for less than two reads of the bytes do.
- **The name.** `.ini`, `.conf`, `.properties`, `.yaml` name as many notes as settings, and most configuration files on a corpus need not be named so; ADR-094 and ADR-171 refuse it.
- **A verdict kind or a subtype of its own.** Out of scope already means a kind of file left out whatever it holds, and a subtype is the name's word (ADR-095).
- **A gate.** The floor needs a measurement first, and a run held for it would hold every later stage for a rule that removes nothing until set.
- **Configuration rooted XML matched by structure alone** (#555's *a root whose children are few and differently named*). Unmeasured; a fixed list is exact where it reaches.
- **XSD, WSDL and OpenAPI as configuration, or as code by their content.** They describe an interface for the people who build against it. The operator makes them code by listing their endings.
- **Seconds since 1970 and `YYMMDD HHMMSS` among the timestamp forms.** Any ten-digit, or six-and-six-digit, number would match, and the cost is unmeasured.
- **A YAML list entry, `- key: value`, as a setting line.** A bulleted note writes the same, and a document wrongly removed costs more than a configuration file wrongly kept.

## Consequences

- **With the floor set**, a configuration file outside every code set leaves the survivors in stage 1, and one inside a set is kept with it, as code. A guide about configuration stays.
- **With it unset**, nothing changes for configuration, and the page shows the distribution an operator sets it from.
- **Logs are found where ADR-171 missed them**: a timestamp after an address, a weekday or a priority, an hour of one digit, entries over several lines, and a log in a single-byte encoding. A document whose lines carry a date near their start, minutes or a changelog, scores higher than before, as ADR-171 §3 warned.
- **A JSON or YAML file of records keyed line by line can read as settings.** Data records are #574's, and the floor ships unset.
- **A build file outside every set is judged by its content**, so a Maven project outside every set is removed and a `Makefile` outside every set stays a document.
- Census writes `configurationSettingShareFloor`, unset, into every profile it saves, and the deliverable's index states it as written. The README's table of values names it, beside `format-mix.html`, and `docs/architecture.md`'s stage table names the rule.

## Tests

Every file is built in the test, and every profile is YAML the test writes, the new key through a helper that edits the file's YAML tree, so every test compiles before the key, the rule and the pass exist. Tables are read by their names and verdicts by their stored names.

| Class | What it holds |
| --- | --- |
| `corpus.TimestampedLinesTest.findsATimestampAfterAPrefixWithinTheFirst64Characters` | an Apache error line, a Common Log Format line, an RFC 5424 line and an Android line each count as timestamped |
| `…findsAnHourOfOneDigit` | `Mar 22, 2011 1:11:31 PM …` counts |
| `…aTimestampPastTheFirst64CharactersIsNotRead` | a time ending on a line's 64th character counts, and one ending on its 65th does not |
| `…continuationLinesLeaveTheCount` | indented lines, `at `, `Caused by:` and `...` lines are counted neither way, so ten entries over sixty lines are ten of ten |
| `…secondsSince1970AndSixAndSixDigitsAreNotTimestamps` | `1131566461 …` and `081109 203615 …` count as lines and not as timestamped |
| `pipeline.LogsOfNoKnownKindTaskletTest.aLogInASingleByteEncodingIsALog` | a log with a cp1252 byte in its prefix is `UNRECOGNISED`, left out as a log at 100%, still `UNRECOGNISED`; one with a byte of nought carries no verdict; the page counts one log, one in the top band, and carries §2's sentence |
| `pipeline.ConfigurationFilesTaskletTest.withNoFloorSetNothingIsAConfigurationFileAndTheShareIsMeasured` | unset, settings and a logback file carry no verdict and are no members, the stage reaches its hashing, the page says no floor is set and counts the share and the root, the configuration consumed records `null`, the measurement points at the page |
| `…aFileMostlyOfSettingsOutsideEveryCodeSetIsOutOfScope` | Properties with each comment kind, INI, YAML, JSON and a file with no ending are left out with the share's reason; a guide, nine settings, Markdown, CSV and `key:value` lines are kept |
| `…aContinuedLineIsReadAsOneSetting` | ten settings continued over thirty lines are 100% |
| `…aFileWhoseFirstElementIsAProgramsConfigurationIsOneWhateverItsShare` | logback, log4j 2, a Maven project in its namespace, Spring beans under a prefix and a `.txt` holding logback's root are left out with the root's reason; a `project` in no namespace and another root are kept |
| `…noDtdIsFetchedAndNoEntityResolved` | a file declaring a DTD and an external entity on a local port is left out by its root, and the port is never connected to |
| `…anInterfaceDefinitionIsKeptWhateverItsShare` | at a floor of 0, an XSD, both WSDLs, OpenAPI in YAML with another key first, Swagger in JSON and a JSON Schema are kept; a YAML whose `openapi` key is not at its root is left out; the page counts six |
| `…insideACodeSetAConfigurationFileIsKeptWithItAsCode` | settings and a logback file in a set are code with §4's reason, members as `configuration`, never hashed, logged with ADR-284's line; one at the root's set names the corpus root; the build file stays `build`; a guide stays a document |
| `…aConfigurationFileNeverMakesASet` | settings in a folder of no code make no set and are left out |
| `…aBuildFileOutsideEveryCodeSetIsJudgedByItsContent` | a multi-module parent `pom.xml` and a `package.json` outside every set are left out; a `CMakeLists.txt` outside every set is kept |
| `…aFileAnEarlierPassDecidedIsNotReadAgain` | a log and a file left out for its ending inside a set keep their verdicts and are no members |
| `…aFileThePassCannotReadIsLeftAsItWas` | a file gone after detection carries no verdict, is logged once with §4's line, and the next is left out |
| `…theFormatMixPageCountsWhatThePassFound` | §8's counts, sentences and rows |
| `…anUnreadableFloorFindsNothingAndIsRecordedAsNull` | `0,8` records `null` and removes nothing |
| `profile.ConfigurationSettingShareFloorInTheProfileTest` | census writes the key unset as the last key; a value is kept as written; `0,8` loads |
| `pipeline.NextActionTest.anUnreadableConfigurationFloorIsReported` | the closing line names the key, says nothing was found, and quotes the value, in one line |
| `pipeline.RunIdentityGoldenTest.byteLevelReduction` | §10's configuration consumed, word for word |
| `pipeline.ByteLevelReductionTaskletTest.removesCompiledCodeFontsCertificatesDatabasesAndEmailsAsOutOfScope`, `pipeline.OutOfScopeNameEndingsTaskletTest.aListedEndingNeverLeavesOutAFileWhoseBytesNameItsKind` | the page's sentence above the timestamp table is §2's |

No test holds that the pass holds folders and no occurrence (§4): what a class holds does not show in what it writes.

## What this does not decide

- **A log or a configuration file inside a container**, as converted text. A later decision needs a measurement brief the operator runs: the precision of the local generation model on public samples, or a unit that survives Docling's joined lines, such as timestamps per hundred words.
- **More timestamp forms**: seconds since 1970, `YYMMDD HHMMSS`, a dash between date and time, and unpadded minutes and seconds. Each needs its false positives measured.
- **Directive files and other grammars**: `Listen 80`, a Java policy file, HCL blocks. A root this record's list does not hold, a log4j 2 configuration written in its namespace among them, is judged by its share.
- **Data records** (#574), and operational records that are neither logs nor configuration files.
- **A log or a configuration file of fewer than ten counted lines.**
