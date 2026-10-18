# How a log or a configuration file can be told by its content, locally

Research record for [#555](https://github.com/algernon28/vespera/issues/555), a ticket of map
[#551](https://github.com/algernon28/vespera/issues/551). Facts carry their source. Measurements were made on public
samples only, never on the operator's documents, and are labelled **Measured here**. Anything not measured or published
is labelled **Estimate**. Sources were read on 2026-10-18.

## Recommendation

**Add no detector library. Extend stage 1's reading of the bytes with the JDK and the Jackson YAML parser the pom
already carries, and keep the local generation model for what exists only as converted text.**

1. **Logs.** Keep ADR-171's timestamp share, but widen what it reads. On 16 public sample logs (Loghub), the pattern as
   built scores **0% on 9 of the 16 formats**, because their timestamp is not at the line start or is a form it lacks.
   Searching the first 64 characters of each line for the same forms, plus an hour without its leading zero, reaches 90%
   on 12 of the 16. Over 630 of this repository's own documentation, code and configuration files, neither pattern
   reached 90% on any file. The other misses need forms the pattern lacks: seconds since 1970, `YYMMDD HHMMSS`, a dash
   between date and time, and minutes or seconds written without a leading zero. Logs whose entries run over several lines (ADR-171's 50 files at
   50% to 89%) need continuation lines left out of the count.
2. **Configuration files.** No surveyed detector labels a file by what it is *for*. Magika labels syntax (INI, YAML,
   TOML, JSON, XML), and Tika and Linguist decide these kinds from the file name. Two cheap checks over the bytes do
   more:
   - a **setting-line share**, the share of lines shaped like `key = value`, `key: value` or `[section]`;
   - a **parse of the first elements** with StAX (JDK) or Jackson streaming. It reads an XML root's name and namespace,
     or a JSON or YAML document's root keys. The same parse tells schema and interface files (XSD, WSDL, OpenAPI, JSON
     Schema) and data records apart from settings.
3. **Generated code documentation.** Javadoc and Doxygen write fixed generator markers into their HTML. Stage 1 can
   read them from the bytes.
4. **Logs and configuration files inside a PDF, DOCX or RTF** exist only as converted text. Docling joins a PDF
   paragraph's lines with spaces, so no line-based rule carries over. Of the surveyed techniques, only the local
   generation model (`qwen3:8b` through Ollama, already a dependency) reads converted text for what a file is for. No
   precision is published for that task. Measure it on public samples before it removes anything.

**Precision.** Only Magika publishes precision, and only for syntax. Its paper reports these F1 scores: INI 98%,
YAML 99%, JSON 99%, XML 99%, CSV 99% and plain text 84%. Every rule proposed above has to be measured, first on public
samples and then by a brief the operator runs (map #551, standing rules). This record gives first public-sample numbers.
They come from small samples.

## What the repository already does

- **Stage 1 reads text as UTF-8 only.** `BrokenCheck.decodesAsText` reads a 512-byte prefix. It accepts a UTF-16 or
  UTF-32 byte-order mark, or a prefix with no `0x00` that decodes as UTF-8. Anything else with no signature is
  `UNRECOGNISED` (`src/main/java/io/algernon/vespera/corpus/BrokenCheck.java`, `decodesAsText`; ADR-256, Context).
- **XML, JSON and YAML have no subtype.** They are `PLAIN_TEXT`, and a subtype comes from the name only for HTML,
  Markdown, CSV, AsciiDoc and `.eml` (`BrokenCheck.narrowedPlainText`; `DetectedSubtype`).
- **The log rule** (`TimestampedLines`, ADR-171 §2) applies only to `PLAIN_TEXT`. It reads the whole file up to 128 KB,
  or else the first and last 64 KB. Of those lines, it counts the non-blank ones that *begin* with one of five
  timestamp forms. A file is a log at ten or more non-blank lines and a share at or over `logTimestampShareFloor`
  (0.9 on the measured archive).
- **What that rule cannot reach, by construction:**
  - a log in a single-byte encoding with a byte over `0x7F` in its first 512 bytes, which is `UNRECOGNISED` (ADR-256
    Context describes such records);
  - a log or a configuration file inside a PDF, DOCX or another container;
  - a log of fewer than ten lines.
- **What ADR-171 measured that it misses.** There were 50 files at 50% to 89%: logs whose entries run over several
  lines, and package catalogs. Three small `.log` files use timestamp forms the pattern does not read. Of 27 files named
  `.log`, 25 fall below the floor.

## 1. Which logs the timestamp share misses

### 1.1 Published sample lines

**Measured here.** `TimestampedLines`' pattern, copied verbatim, was applied with JDK 26 to the sample lines each
format's own documentation prints.

| Format (source) | Line begins | Matched at line start |
|---|---|---|
| Apache httpd error log ([httpd docs](https://httpd.apache.org/docs/2.4/logs.html)) | `[Fri Sep 09 10:42:29.902022 2011]` | **no**: the weekday comes first |
| Apache httpd Common Log Format (same) | `127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700]` | **no**: the client address comes first |
| RFC 5424 syslog, Example 1 ([RFC 5424 §6.5](https://www.rfc-editor.org/rfc/rfc5424.html)) | `<34>1 2003-10-11T22:14:15.003Z` | **no**: `PRI` and `VERSION` come first |
| RFC 3164 syslog, first example ([RFC 3164 §5.4](https://www.rfc-editor.org/rfc/rfc3164.html)) | `<34>Oct 11 22:14:15` | **no** as sent. Yes once `<34>` is removed |
| `java.util.logging.SimpleFormatter`, two-line example ([Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.logging/java/util/logging/SimpleFormatter.html)) | `Mar 22, 2011 1:11:31 PM` / `SEVERE: …` | **no** on either line: comma, year and an unpadded hour |
| Python `logging` default ([HOWTO](https://docs.python.org/3/howto/logging.html)) | `WARNING:root:Watch out!` | **no**: no timestamp at all |
| Python `logging` with `%(asctime)s` (same) | `2010-12-12 11:41:42,612` | yes |
| Logback `BasicConfigurator` ([manual](https://logback.qos.ch/manual/configuration.html)) | `16:06:09.031 [main] INFO` | yes |
| A Java stack frame (a continuation line under a logged exception) | `\tat java.base/…` | no, and it counts against the share |

### 1.2 Loghub's public sample logs

**Measured here.** Loghub ([logpai/loghub](https://github.com/logpai/loghub) at `dd61d09`) publishes a 2,000-line
sample from each of 16 systems. Each sample was read the way `TimestampedLines.count` reads a file. All 16 are over
128 KB, so the two 64 KB windows apply. Two patterns were applied:

- **anchored**: the pattern as built;
- **first 64 chars**: the same five forms, or `\d{1,2}:\d{2}:\d{2}`, found anywhere in the line's first 64 characters.

| Sample | Lines read | Anchored | First 64 chars | How the line begins |
|---|---|---|---|---|
| Hadoop, Linux, Mac, OpenSSH, Spark, Windows, Zookeeper | 695–1,365 | **100%** | 100% | a form the pattern lists |
| Android | 961 | **0%** | 100% | `03-17 16:13:38.811`: month and day, no year |
| Apache | 1,530 | **0%** | 100% | `[Sun Dec 04 04:47:44 2005]`: weekday first |
| OpenStack | 442 | **0%** | 100% | the source file's name, then the timestamp. **Estimate**: the prefix was probably added when the collection was assembled |
| Proxifier | 1,090 | **0%** | 100% | `[10.30 16:49:06]`: no year |
| Thunderbird | 846 | **0%** | 100% | `- 1131566461 2005.11.09 …`: an alert field and seconds since 1970 come first |
| HealthApp | 1,406 | **0%** | 81% | `20171223-22:15:29:606`, and 25% of lines write minutes or seconds unpadded (`22:5:3`) |
| BGL | 758 | **0%** | **0%** | `- 1117838570 2005.06.03 … 2005-06-03-15.42.50`: seconds since 1970 first, then a date with no time, and a full timestamp with a dash between date and time, which no form allows |
| HDFS | 896 | **0%** | **0%** | `081109 203615`: `YYMMDD HHMMSS`, six digits and six |
| HPC | 1,622 | **0%** | **0%** | a record number and a node name. Seconds since 1970 sit in the middle of the line |

At a floor of 0.9, the anchored pattern finds **7 of 16** and the first-64-characters pattern finds **12 of 16**.

**What is not a log was measured too, with the same code.** The run covered 651 files in this repository: everything
under `docs/` and `src/main/`, plus `pom.xml`, `compose.yaml`, `CONTEXT.md` and `AGENTS.md`. Of the 630 with ten or
more non-blank lines, **none reached 90% under either pattern**. That is English technical prose, code and
configuration files. ADR-171 §3 already warns that timestamped minutes or changelogs could score high.

### 1.3 Which technique finds what the definition misses

| Missed log | What finds it | Evidence |
|---|---|---|
| Timestamp after a prefix (address, weekday, level, PRI, file name) | the timestamp share, searched in the first 64 characters instead of at the line start | **Measured here**: 5 of the 9 missed Loghub formats reach 100% |
| Forms the pattern lacks (unpadded parts, seconds since 1970, `YYMMDD HHMMSS`) | more forms in the pattern. Seconds since 1970 (ten digits) need care: any ten-digit number matches | **Measured here**: HealthApp 81%, BGL, HDFS and HPC 0%. **Estimate**: the risk from ten-digit forms is unmeasured |
| Entries over several lines (stack frames, wrapped messages) | leave continuation lines out of the count: indented lines, `at …`, `Caused by:`, `...` | ADR-171: 50 files at 50% to 89%. **Estimate**: not measured here |
| A log saved in a single-byte encoding | nothing, while stage 1 recognises text as UTF-8 only | `BrokenCheck.decodesAsText`; ADR-256 Context |
| A log inside a PDF, DOCX or RTF | the converted text only: timestamps per word over the whole text (§4), or the generation model | **Estimate**: neither is measured |
| A log of fewer than ten lines | nothing: ADR-171 rules that one line is not a log | ADR-171 §2 and *Context* |

## 2. Technique by technique

### 2.1 Google Magika

| | |
|---|---|
| **What it is** | A neural classifier of content type from bytes. CLI [`cli/v1.1.0`](https://github.com/google/magika/releases/tag/cli/v1.1.0) (2026-04-24), model [`standard_v3_3`](https://github.com/google/magika/blob/main/assets/models/standard_v3_3/README.md) (2025-04-11). The CLI's model link points at `standard_v3_3` ([`rust/gen/model` at `cli/v1.1.0`](https://github.com/google/magika/tree/cli/v1.1.0/rust/gen)) |
| **What it reads** | After leading and trailing white space is removed, the first 1,024 and last 1,024 bytes, padded with token 256. Content under 8 bytes after that gets a heuristic `txt` or `unknown` ([`config.min.json`](https://github.com/google/magika/blob/main/assets/models/standard_v3_3/config.min.json); [`input.rs`](https://github.com/google/magika/blob/main/rust/lib/src/input.rs)). The name is never read. The [paper](https://arxiv.org/abs/2409.13768)'s v1 model read three 512-byte windows instead |
| **Labels relevant here** | `ini`, `yaml`, `toml`, `json`, `jsonl`, `xml`, `csv`, `tsv`, `html`, `markdown`, `hcl`, `htaccess`, `winregistry`, `sql`, `txt`, among 216 outputs |
| **Labels it lacks** | **No `log`, `properties`, `xsd`, `wsdl` or `openapi`** (model card, checked line by line) |
| **Output** | A label is returned only when the score passes that label's threshold. The default threshold is 0.5. Markdown's is 0.75, and some labels' are 0.9 or 0.95. Otherwise it returns `txt` for text and `unknown` for binary (`config.min.json`; [How Magika works](https://securityresearch.google/magika/core-concepts/how-magika-works/)) |
| **Published accuracy** | About 99% average over 216 outputs for `standard_v3_3` ([model CHANGELOG](https://github.com/google/magika/blob/main/assets/models/CHANGELOG.md)). Per-type F1, from the paper's Table V (v1 model, about 100 types): INI 98%, YAML 99%, JSON 99%, XML 99%, CSV 99%, HTML 94%, Markdown 92%, plain text 84%. TOML is not reported. The paper names plain text as its weakest type, and files under about 100 bytes as where generic labels cost accuracy ([HTML v1](https://arxiv.org/html/2409.13768v1)) |
| **Training data** | GitHub for text types and VirusTotal for binaries. Labels came from extensions (paper) |
| **Bindings** | Rust CLI, Python, Rust, JavaScript/TypeScript, and Go (work in progress). **No Java binding** ([README](https://github.com/google/magika/blob/main/README.md)). From Java there are two routes: run the CLI as a child process (Tika's `MagikaDetector` does this, §2.2), or load `model.onnx` (3,163,737 bytes) in ONNX Runtime and reimplement the input and threshold logic above |
| **Dependency** | CLI: a native binary, `magika-cli-x86_64-pc-windows-msvc.zip` at 10,076,032 bytes. ONNX route: [`com.microsoft.onnxruntime:onnxruntime` 1.31.0](https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime), MIT, with the jar at **50,942,147 bytes** ([Maven Central](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.31.0/)). Magika is Apache-2.0 |
| **Network** | None once installed. The installers fetch from the network (README) |
| **For this ticket** | It tells INI, TOML and YAML from prose, which a name or a line shape partly does too. It cannot tell a log: there is no label for one. It cannot tell settings from schemas, records or OpenAPI, which are all `yaml`, `json` or `xml` to it. Javadoc is `html`. **Not run here**: installing it needs a binary download nobody approved |

### 2.2 Apache Tika

| | |
|---|---|
| **Version** | [4.1.0](https://github.com/apache/tika/tree/4.1.0). `tika-core` is 772,797 bytes, Apache-2.0 and Java 17+. It depends on `slf4j-api`, `commons-io` and `commonmark` ([pom](https://github.com/apache/tika/blob/4.1.0/tika-core/pom.xml)) |
| **Log, INI, properties, YAML, `.conf`/`.cfg`/`.config`, WSDL** | Told **by the name only**: `text/x-log` is `*.log`, `text/x-ini` is `*.ini`, `text/x-java-properties` is `*.properties`, `text/x-yaml` is `*.yaml`/`*.yml`, `text/x-config` is `*.config`/`*.conf`/`*.cfg`/`*.xconf`, and `application/wsdl+xml` is `*.wsdl`. None has a content pattern ([`tika-mimetypes.xml`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml)) |
| **XML** | Recognised by `<?xml` at offset 0. 72 `root-XML` rules, each a root element name and namespace, tell types apart: Atom, RSS, SVG, XSLT, XHTML and others. **XSD, WSDL, Maven POM and logging configurations are not among them** |
| **Extending it** | A `custom-mimetypes.xml` can add `root-XML` rules (`tika.custom-mimetypes`, [`MimeTypesFactory`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/java/org/apache/tika/mime/MimeTypesFactory.java)) |
| **Text versus binary** | `TextDetector` reads 512 bytes and looks for control bytes. That is what stage 1 already decides ([`TextDetector`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/java/org/apache/tika/detect/TextDetector.java)) |
| **Magika inside Tika** | `tika-detector-magika` runs the `magika` CLI as a child process with `--json` and a 60 s timeout ([`MagikaDetector`](https://github.com/apache/tika/blob/4.1.0/tika-detectors/tika-detector-magika/src/main/java/org/apache/tika/detect/magika/MagikaDetector.java)). It adds Magika's limits to Tika's |
| **Network** | None |
| **For this ticket** | Name rules are what ADR-094 and ADR-171 rule out: a name rule missed 68 of the 70 logs. `root-XML` does what ten lines of StAX do (§2.4), and Tika's table holds none of the roots needed here |

### 2.3 GitHub Linguist

| | |
|---|---|
| **Version** | [v9.7.0](https://github.com/github-linguist/linguist/releases/tag/v9.7.0) (2026-08-26). Ruby, MIT. Its native gems are `charlock_holmes` (ICU) and `rugged` (libgit2) ([gemspec](https://github.com/github-linguist/linguist/blob/main/github-linguist.gemspec)) |
| **How it decides** | Strategies run in order: modeline, file name, shebang, extension, XML, manpage, heuristics, classifier ([`linguist.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist.rb)). The heuristics choose among the languages an **extension** already allows ([`heuristics.yml`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/heuristics.yml)). The Bayesian classifier ranks only candidates from earlier strategies, and returns nothing when there are none ([`classifier.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/classifier.rb)) |
| **With no usable name** | Only a modeline, a shebang, a manpage, or `<?xml version=` in the first two lines (giving `XML`) decides anything ([`strategy/xml.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/strategy/xml.rb)) |
| **Log** | No such language. `INI` is a data language reached by extensions (`.ini`, `.cfg`, `.properties`, …) ([`languages.yml`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/languages.yml)) |
| **Generated documentation** | `generated_html?` recognises Doxygen by `<!-- Generated by Doxygen x.y.z -->` in the first 31 lines. It also recognises pkgdown, mandoc and a `<meta name="generator">` for org-mode, LaTeX2HTML, groff, makeinfo, texi2html and ronn. **Javadoc is not in the list**, and the check runs only on `.html`, `.htm` and `.xhtml` names ([`generated.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/generated.rb)) |
| **Network** | None |
| **For this ticket** | Not usable from Java, and its decisions are name-led. **Worth copying**: the generator markers, read from the content without the extension gate |

### 2.4 Structural rules over the bytes

None needs a new dependency. StAX is in the JDK. `tools.jackson.dataformat:jackson-dataformat-yaml` is already in
`pom.xml` for the profile (ADR-061), and with it Jackson's JSON streaming parser.

**Setting-line share.** A line counts as a setting if it is `key = value`, `key: value` (YAML needs the space) or
`[section]`. Lines that are blank or begin with `#`, `;`, `!` or `//` are left out. Java's own format
([`Properties.load`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Properties.html)) takes
`=`, `:` or white space as the separator, `#` or `!` for comments, and a trailing `\` for a continued line.

**Measured here**, at ten or more counted lines:

| Sample | Files | Setting-line share |
|---|---|---|
| Configuration files: Magika's public test files (`toml`, `hcl`, 2 × `yaml`); this repository's `application.yaml`, two compose files and three workflows; the JDK 26 `conf/` files | 15 | 100% on 6 files; 97%, 85%, 81%, 78%, 66%, 61% and 54% on one each; **5%** on `management.properties`; **0%** on `default_local.policy`. The JDK's `conf/README.txt` is prose, scored 15%, and is not counted |
| Loghub's 16 sample logs | 16 | 0% on all 16 |
| This repository's `docs/`, `CONTEXT.md`, `AGENTS.md` and `README.md` | 301 | under 10% on 298; 10% to 39% on 3 |

Both misses are understood. `management.properties` is mostly one value continued over 20 lines with `\`, which the
Properties format defines as one entry. `default_local.policy` uses the policy grammar (`grant { permission …; };`).
Directive files such as httpd's `Listen 80` would also be missed, because white space is their separator. **Estimate**:
joining `\` continuations recovers the first kind. A grammar or the generation model is needed for the others. Fifteen
files cannot set a floor. They show the share separates these kinds, not where to cut.

**Parsing the first elements.** Both parsers read only until the answer is known. Like `TimestampedLines`, the cost does
not grow with the file.

| Kind | Content signal | Source |
|---|---|---|
| XSD | root `schema` in `http://www.w3.org/2001/XMLSchema` | [XSD 1.1 Part 1](https://www.w3.org/TR/xmlschema11-1/) |
| WSDL 1.1 / 2.0 | root `definitions` in `http://schemas.xmlsoap.org/wsdl/` / root `description` in `http://www.w3.org/ns/wsdl` | [WSDL 1.1](https://www.w3.org/TR/2001/NOTE-wsdl-20010315), [WSDL 2.0](https://www.w3.org/TR/wsdl20/) |
| OpenAPI / Swagger (JSON or YAML) | root key `openapi` (REQUIRED, the version) / `swagger: "2.0"` | [OAS 3.1.1](https://spec.openapis.org/oas/v3.1.1.html), [Swagger 2.0](https://spec.openapis.org/oas/v2.0.html) |
| JSON Schema | `$schema` in the root object | [JSON Schema core §8.1.1](https://json-schema.org/draft/2020-12/json-schema-core) |
| Maven POM (a build configuration) | root `project` in `http://maven.apache.org/POM/4.0.0` | [POM reference](https://maven.apache.org/pom.html) |
| XML configuration in general | a root not listed above whose children are few and differently named. Example roots: logback `configuration`, log4j2 `Configuration`, .NET `configuration` | **Estimate**: a rule of thumb, unmeasured |
| Data records (XML) | a root whose children are mostly one element name repeated many times | **Estimate**: unmeasured |
| Data records (JSON, JSON Lines, YAML) | a top-level array, or one object per line, of objects sharing one set of keys | **Estimate**: unmeasured |
| Fixed-width or delimited records | the same line length, or the same count of a delimiter, on most lines | **Estimate**: unmeasured. Single-byte records are `UNRECOGNISED` and covered by ADR-256's name endings |

**Estimate**: when a file parses, root-name and root-key checks are close to exact, because the namespaces and keys are
fixed by the specifications. They fail on files that do not parse, and on XML whose namespace prefix is undeclared.

**Generated code documentation.** These markers are read from the bytes:

- **Javadoc**: `<!-- Generated by javadoc (NN) … -->`. The JDK source calls it a *"marker string, deliberately not
  localized"*. There is also `<meta name="generator" content="javadoc/ClassName">` on every page
  ([`Head.java`](https://github.com/openjdk/jdk/blob/master/src/jdk.javadoc/share/classes/jdk/javadoc/internal/doclets/formats/html/markup/Head.java),
  [`HtmlDocletWriter.java`](https://github.com/openjdk/jdk/blob/master/src/jdk.javadoc/share/classes/jdk/javadoc/internal/doclets/formats/html/HtmlDocletWriter.java)).
- **Doxygen**: `<!-- Generated by Doxygen x.y.z -->` (Linguist, §2.3).

Section headings such as "Method Summary" are localized (`doclets.properties` and its `_de`, `_ja` variants), so they
are weaker evidence. A converted page loses the HTML comment and the `<meta>`: **Estimate** from Docling's
HTML-to-document model, not tested.

**Security.** Corpus files are untrusted input. A StAX reader must have DTDs and external entities switched off
(`XMLInputFactory.SUPPORT_DTD` and `IS_SUPPORTING_EXTERNAL_ENTITIES` set to false).

### 2.5 The local generation model: `qwen3:8b` through Ollama

| | |
|---|---|
| **What it is** | The generation model ADR-197 uses to label relevance. Q4_K_M, 5.2 GB, Apache-2.0 ([Ollama library](https://ollama.com/library/qwen3:8b)). Reached through `spring-ai-starter-model-ollama`, which is already in `pom.xml` |
| **What it reads** | Converted text, so it is the only surveyed technique that sees a log or a configuration file kept in a PDF or DOCX. Through `num_ctx`, only part of a long text fits ([`ollama-generation-surface.md`](ollama-generation-surface.md) §2) |
| **What it can tell** | Any role it is asked about: log, configuration, schema or interface, data records, generated code documentation, documentation. `format` takes a JSON Schema and constrains decoding to it ([structured outputs](https://docs.ollama.com/capabilities/structured-outputs); this repository's [research record](ollama-generation-surface.md) §3) |
| **Published precision for this task** | **None found.** Must be measured |
| **Determinism** | Structured output is reported not to repeat across runs with the same seed ([ollama#12559](https://github.com/ollama/ollama/issues/12559), via the research record §3). An answer is a model's answer under its weights' digest (ADR-114, ADR-197 §3) |
| **Cost** | **Estimate**: one call of a few seconds per file on a GPU. Tens of thousands of files would take hours. The bytes rules cost two reads |
| **Network** | None for a local model. A cloud model served through the same daemon is not local (ADR-197, Context) |

## 3. Comparison

| | Magika | Tika | Linguist | Line shares (timestamp, setting) | First-element parse (XML root, JSON/YAML keys, generator markers) | `qwen3:8b` |
|---|---|---|---|---|---|---|
| **Reads** | bytes | bytes plus name | name, then bytes | bytes. On converted text only after Docling's line joins (§4) | bytes | converted text |
| **Logs** | no label for one | `*.log` name only | no | **yes**: 7 of 16 public formats as built, 12 of 16 widened | no | **Estimate**: probably, unmeasured |
| **Configuration files** | syntax only (`ini`, `yaml`, `toml`, …) | name only | name only | line formats: 13 of 15 public samples at 54% or more | XML and JSON/YAML roots | **Estimate**: probably, unmeasured |
| **Schema and interface files** (XSD, WSDL, OpenAPI) | no: `xml`, `yaml` or `json` | WSDL by name only | no | no: an OpenAPI YAML scores like settings | **yes**, by fixed root names, namespaces and keys | **Estimate**: probably, unmeasured |
| **Data records** | no: `csv`, `json`, `jsonl`, `xml` | no | no | timestamped rows count as a log (ADR-171 tests a CSV-named one) | **Estimate**: repeated uniform children or objects | **Estimate**: probably, unmeasured |
| **Generated code documentation** | no: `html` | no | Doxygen and some generators, `.html` names only | no | **yes**: Javadoc and Doxygen markers | **Estimate**: from headings, unmeasured |
| **New dependency (ADR-046)** | a native CLI (10 MB zip) or ONNX Runtime (51 MB jar, MIT), plus the 3 MB model (Apache-2.0) | `tika-core` (0.8 MB, Apache-2.0) | Ruby with native gems (MIT): not usable from Java | none (JDK) | none (JDK, Jackson already present) | none (Spring AI Ollama already present) |
| **Offline** | yes, once installed | yes | yes | yes | yes | yes, with a local model |
| **Published precision** | per-syntax F1 98–99% (plain text 84%) | none: the name's accuracy | none | none. Public samples here | none. **Estimate**: near exact once parsed | none |

## 4. What converted text keeps of a file's lines

- **PDF.** Docling's page assembly joins a text cluster's lines with a single space, except across a word split by a
  hyphen. This applies to every text label, `code` among them
  ([`page_assemble_model.py`](https://github.com/docling-project/docling/blob/main/docling/models/stages/page_assemble/page_assemble_model.py)
  `sanitize_text`; [`base_layout_model.py`](https://github.com/docling-project/docling/blob/main/docling/models/base_layout_model.py)
  `TEXT_ELEM_LABELS`; labels in
  [`labels.py`](https://github.com/docling-project/docling-core/blob/main/docling_core/types/doc/labels.py)). This was
  read at Docling `main`, not at the version in the pinned image. So the unit is a paragraph, not a line. Any share over
  converted text needs a unit that survives the join, such as timestamps per hundred words. **Estimate**: unmeasured.
- **A text file posted as Markdown** with no blank line is one paragraph to Docling (ADR-171, Context). For text, stage
  1 has the bytes anyway.
- **DOCX.** **Estimate**: it depends on whether the author pasted one Word paragraph per line. Not checked.

## 5. Left for the decision

- Which widening of the timestamp forms, and whether continuation lines leave the count. Extending the pattern re-mints
  stage 1 by itself (ADR-058, ADR-171 §2).
- Whether configuration files, schema and interface files, and generated code documentation are out of scope, removed
  under another verdict, or kept as code sets. Map #551, items 3 and 4.
- Where a rule over converted text runs: after stage 2, since stage 1 has no converted text. Which run ids move.
- The floors. Each is a profile value with its evidence beside it (map #551 standing rules; ADR-171 §3). The numbers
  above come from public samples and set no floor.
- The operator brief that measures these rules on the archive.

## Sources

Read 2026-10-18.

- **Magika**:
  - [README](https://github.com/google/magika/blob/main/README.md)
  - [model card `standard_v3_3`](https://github.com/google/magika/blob/main/assets/models/standard_v3_3/README.md)
  - [`config.min.json`](https://github.com/google/magika/blob/main/assets/models/standard_v3_3/config.min.json)
  - [models CHANGELOG](https://github.com/google/magika/blob/main/assets/models/CHANGELOG.md)
  - [`rust/lib/src/input.rs`](https://github.com/google/magika/blob/main/rust/lib/src/input.rs)
  - [`rust/lib/src/model.rs` at `cli/v1.1.0`](https://github.com/google/magika/blob/cli/v1.1.0/rust/lib/src/model.rs)
  - [release `cli/v1.1.0`](https://github.com/google/magika/releases/tag/cli/v1.1.0)
  - [How Magika works](https://securityresearch.google/magika/core-concepts/how-magika-works/)
  - Fratantonio et al., *Magika: AI-Powered Content-Type Detection*, ICSE 2025, [arXiv:2409.13768](https://arxiv.org/abs/2409.13768) ([HTML v1](https://arxiv.org/html/2409.13768v1))
- **ONNX Runtime Java**:
  - [Maven Central listing](https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime)
  - [1.31.0 files](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.31.0/)
- **Apache Tika 4.1.0**:
  - [`tika-mimetypes.xml`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml)
  - [`MagikaDetector`](https://github.com/apache/tika/blob/4.1.0/tika-detectors/tika-detector-magika/src/main/java/org/apache/tika/detect/magika/MagikaDetector.java)
  - [`TextDetector`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/java/org/apache/tika/detect/TextDetector.java)
  - [`MimeTypesFactory`](https://github.com/apache/tika/blob/4.1.0/tika-core/src/main/java/org/apache/tika/mime/MimeTypesFactory.java)
  - [`tika-core` pom](https://github.com/apache/tika/blob/4.1.0/tika-core/pom.xml)
  - [`tika-core` 4.1.0 files](https://repo1.maven.org/maven2/org/apache/tika/tika-core/4.1.0/)
- **GitHub Linguist**, read on `main` (latest release v9.7.0):
  - [`linguist.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist.rb)
  - [`strategy/xml.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/strategy/xml.rb)
  - [`classifier.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/classifier.rb)
  - [`heuristics.yml`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/heuristics.yml)
  - [`generated.rb`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/generated.rb)
  - [`languages.yml`](https://github.com/github-linguist/linguist/blob/main/lib/linguist/languages.yml)
  - [gemspec](https://github.com/github-linguist/linguist/blob/main/github-linguist.gemspec)
- **Ollama**:
  - [`qwen3:8b`](https://ollama.com/library/qwen3:8b)
  - [structured outputs](https://docs.ollama.com/capabilities/structured-outputs)
  - [ollama#12559](https://github.com/ollama/ollama/issues/12559)
  - this repository's [`ollama-generation-surface.md`](ollama-generation-surface.md)
- **Docling**:
  - [`page_assemble_model.py`](https://github.com/docling-project/docling/blob/main/docling/models/stages/page_assemble/page_assemble_model.py)
  - [`base_layout_model.py`](https://github.com/docling-project/docling/blob/main/docling/models/base_layout_model.py)
  - [`labels.py`](https://github.com/docling-project/docling-core/blob/main/docling_core/types/doc/labels.py)
- **JDK**:
  - [`Head.java`](https://github.com/openjdk/jdk/blob/master/src/jdk.javadoc/share/classes/jdk/javadoc/internal/doclets/formats/html/markup/Head.java)
  - [`HtmlDocletWriter.java`](https://github.com/openjdk/jdk/blob/master/src/jdk.javadoc/share/classes/jdk/javadoc/internal/doclets/formats/html/HtmlDocletWriter.java)
  - [`doclets.properties`](https://github.com/openjdk/jdk/blob/master/src/jdk.javadoc/share/classes/jdk/javadoc/internal/doclets/toolkit/resources/doclets.properties)
  - [`Properties`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Properties.html)
  - [`SimpleFormatter`](https://docs.oracle.com/en/java/javase/25/docs/api/java.logging/java/util/logging/SimpleFormatter.html)
- **Specifications**:
  - [XSD 1.1 Part 1](https://www.w3.org/TR/xmlschema11-1/)
  - [WSDL 1.1](https://www.w3.org/TR/2001/NOTE-wsdl-20010315)
  - [WSDL 2.0](https://www.w3.org/TR/wsdl20/)
  - [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html)
  - [Swagger 2.0](https://spec.openapis.org/oas/v2.0.html)
  - [JSON Schema core 2020-12](https://json-schema.org/draft/2020-12/json-schema-core)
  - [Maven POM reference](https://maven.apache.org/pom.html)
  - [RFC 5424](https://www.rfc-editor.org/rfc/rfc5424.html)
  - [RFC 3164](https://www.rfc-editor.org/rfc/rfc3164.html)
- **Log format documentation**:
  - [Apache httpd log files](https://httpd.apache.org/docs/2.4/logs.html)
  - [Python logging HOWTO](https://docs.python.org/3/howto/logging.html)
  - [Logback configuration](https://logback.qos.ch/manual/configuration.html)
- **Public samples**:
  - [Loghub](https://github.com/logpai/loghub) at commit `dd61d0952749ee7963bde24220d1be5ede023033`, `*_2k.log`
  - [Magika `tests_data/basic`](https://github.com/google/magika/tree/main/tests_data/basic)
  - the JDK 26.0.2 `conf/` directory
- **This repository**:
  - `src/main/java/io/algernon/vespera/corpus/TimestampedLines.java`
  - `src/main/java/io/algernon/vespera/corpus/BrokenCheck.java`
  - `CONTEXT.md` (*Log*, *Out of scope*)
  - ADR-046, ADR-094, ADR-095, ADR-171, ADR-195, ADR-197 and ADR-256 under `docs/adr/`
