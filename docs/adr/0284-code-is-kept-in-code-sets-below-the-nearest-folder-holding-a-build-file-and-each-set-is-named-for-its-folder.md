# ADR-284 — Code is kept in code sets, below the nearest folder holding a build file, and each set is named for its folder

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Decided**: under the operator's delegation of 2026-10-19, on the ground the operator settled on 2026-10-18: a set's folder is the nearest folder holding a build file, else the first folder under the corpus root; the operator renames a set; copies keep their paths below the set's folder; a set's build files stay with it and its logs are still removed; generated code documentation is code. One consequence of that ground is confirmed under the delegation: **each module of a multi-module build becomes its own set** (§2).
- **Extends**: [ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md). Stage 1 gains a third pass, the set pass, between its first pass and its hashing (§6). It writes ADR-277's verdict, `code`, on two more kinds of file, a set's build files and the files of a reference tree a generator wrote in it, each with a reason of its own (§4). §7's format-mix page gains a sentence and two rows of its *Kept as code* table (§9), and §9's configuration consumed gains `buildFiles` (§10).
- **Answers**: ADR-277, the first item of its *What this does not decide*: how code is grouped by its path, and what a generated tree's pictures and companion files become.
- **Keeps**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md). No kind is added: the vocabulary stays at eleven values, ten of them blocking.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). The detected format of every file the set pass touches is the one its bytes gave. Nothing is narrowed: the verdict is about where a file lies, as `superseded-by` is about another file, and not about what its bytes are.
- **Keeps**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) and ADR-277 §8. The discard before an unfinished stage 1 works again already holds `CODE`, and takes the two tables of §5 with it.
- **Rests on**: ADR-277 §3, for which files are read and what reads as text; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §3, by which `corpus`'s tables are named in SQL by `corpus` alone; [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md), [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) and [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), for the tables and their index (§5); [ADR-235](0235-a-path-in-the-manifest-a-spreadsheet-would-run-as-a-formula-is-written-behind-a-single-quote.md), for a folder a spreadsheet would run (§7); [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, by which `deliverable` keys no run (§8); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), for the run ids (§11).

## Context

ADR-277 takes code out of the survivors file by file. What the operator is handed of it is decided elsewhere (#573), but it is handed by its paths: a project's code is read as a tree, and a file of it alone means little. Four facts shape how files are put together.

- **A build tool marks where a project begins.** The folder holding a `pom.xml`, a `CMakeLists.txt` or a `*.sln` is the folder its tool builds from, and a multi-module build puts one in each module's folder as well as one above them.
- **Archives hold code with no build file at all**: a folder of scripts, a copy of one source file, a generated reference tree saved on its own.
- **A generated reference tree is more than its pages.** Javadoc writes `element-list`, a stylesheet, scripts and pictures beside its pages; Doxygen writes graphs as `.png`, `.map` and `.md5`. ADR-277 keeps the pages as code and leaves those files where they were: pictures among the documents, and text of no use to anyone among the survivors.
- **Stage 1's first pass judges one file at a time.** Whether a file lies in a project needs every `code` verdict of the run, which exists only once the first pass ends; and whatever is added must come before the hashing, so that it is never hashed.

## Decision

### 1. A build file

**A build file is a file whose name is on the list below, detected `PLAIN_TEXT` or `UNRECOGNISED`, whose bytes read as text by ADR-277 §3's test.** That is ADR-277 §3's reach: a PDF named `package.json` is a PDF, and a binary file named `Makefile` is not a build file.

**The name is matched as the last element of the file's path, folded to lower case with `Locale.ROOT`**, whole for the names and at its end for the endings.

| Matched | v1 |
| --- | --- |
| The whole name | `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`, `build.xml`, `ivy.xml`, `build.sbt`, `cmakelists.txt`, `makefile`, `gnumakefile`, `meson.build`, `configure.ac`, `sconstruct`, `build`, `build.bazel`, `workspace`, `module.bazel`, `package.json`, `cargo.toml`, `go.mod`, `pyproject.toml`, `setup.py`, `setup.cfg`, `pipfile`, `gemfile`, `rakefile`, `composer.json`, `mix.exs`, `rebar.config`, `package.swift`, `pubspec.yaml`, `stack.yaml`, `dune-project`, `project.clj`, `deps.edn` |
| The end of the name | `.sln`, `.csproj`, `.vbproj`, `.fsproj`, `.vcxproj`, `.vcproj`, `.gemspec`, `.cabal`, `.nimble` |

**Left out**: `DESCRIPTION` and `.pro`, which name documents as often as builds; `Doxyfile`, which configures a generator and builds nothing; and `.xcodeproj`, which is a folder and never a file.

**The list is built into `corpus`, as ADR-277 §4's marks are, and is changed by a change that carries a record.** Its version is `v1`, and a change to it is a new version (§10). It is a fact about build tools and not about an archive, so the profile does not hold it, for the reason ADR-277 gives for the marks.

### 2. A set and its folder

**A code set exists for each folder that is the set folder of at least one file carrying a `code` verdict of the first pass.** A file's set folder is:

1. **the nearest folder holding a build file**, from the file's own folder up to the corpus root and including both. A folder holds a build file where a file lying directly in it is one (§1) and carries, after the first pass, no verdict or `code`. A build file that is `broken`, out of scope or a log marks no folder;
2. **else the first folder under the corpus root** on the file's path;
3. **else, for a file lying directly at a corpus root that holds no build file, the corpus root** (§3).

**Each module of a multi-module build is its own set**, since its own build file is nearer to its code than the parent's. A folder whose build file sits above modules, and that is the set folder of no code file, is no set: the parent's build file lies in no set, carries no verdict of this record, and goes on as a survivor. What becomes of a build file outside every set is #575's.

### 3. A set's extent

**A set's extent is every file whose path lies below its folder and not below the folder of another set nested deeper.** A file lies in one extent at most.

**The corpus root's set, where the root holds no build file, covers only the files lying directly at the root.** Every folder under the root is then either a set of its own, by rule 1 or 2, or no set.

### 4. Its members

Every member is a file of the set's extent, and is a member of that one set, with one role.

- **`code`**: each file carrying a `code` verdict of the first pass whose set folder is the set's. It keeps that verdict and its reason, whatever else it is: a `setup.py` kept for its ending is a member as code.
- **`build-file`**: each build file of the extent carrying no blocking verdict after the first pass. The set pass verdicts it `code`, with the reason *"a build file, kept with its code set"*. A build file whose folder is a set's folder is in that set, the nearest winning; one in a folder below a set that holds no code of its own is a build file of the set around it.
- **`generated-tree`**: the files of a reference tree a generator wrote (below).

**A generated tree.** For each set, and for each generator named by a `code` verdict of the first pass on a page of the set's extent, the tree's folder is the deepest folder holding every such page of that generator. Inside the tree's folder and within the set's extent, each file carrying no blocking verdict after the first pass is a member, `generated-tree`, where it is detected `IMAGE`, or detected `PLAIN_TEXT` or `UNRECOGNISED` and is not an HTML page. The set pass verdicts it `code`, with the reason *"a file inside a reference tree generated by Javadoc"*, the generator named as ADR-277 §4 names it, and its member row records the generator.

- **An HTML page** is one detected with the `HTML` subtype, or of no known kind with a name ending `.html`, `.htm` or `.xhtml`, the names stage 1 reads as HTML. It is judged by ADR-277's mark rule alone, so a guide in a generated tree stays a document.
- **A PDF, a Word file or any other class a signature named** stays a document, wherever it lies.
- **A file inside two trees of one set** belongs to the deeper tree's generator, and where the two folders are one, to the generator ADR-277 §4's tables list first.
- So Javadoc's `.gif` and `.png`, `element-list`, `package-list`, stylesheets, scripts and search indexes, and Doxygen's graphs, `.map` and `.md5` files, are code with their pages.

**A member row of a page kept for its mark carries that page's generator, the one its reason names (ADR-277 §5).** A member row of any other `code` file, and of a build file, carries none.

**Only a file carrying no blocking verdict after the first pass is touched by the set pass.** A log inside a set keeps its `out-of-scope` verdict, an empty file stays `broken`, and a file out of scope stays out of scope. None of them is a member.

**Everything else in a set's extent is left as it was.** A document inside a set, a `README.md`, a `docs/` folder of Word files or a generator's guide, is no member and carries no verdict of this record: relevance decides it as it decides any document. The operator's prose about their code is what the knowledge base is for, and a set found by the first folder under the root can cover a folder of documents. A picture outside a generated tree stays a document, and a compiled binary is already out of scope (ADR-252).

**Nothing is removed for being in a set.** A tree present twice is two sets, both kept, named apart by §5's rule. No rule of redundancy is drawn between sets.

### 5. The default name, and the two tables

**A set's default name is its folder's name**, the last element of its relative path as the path spells it. The corpus root's set is named for the last element of the root's real path, and `root` where that path has none, as a drive root has not.

**Two names collide where they are equal folded to lower case with `Locale.ROOT`.** While two or more sets' names collide, each colliding name is prefixed with the name of the next folder above those already in it, and `-`: its folder's parent first (`projA-core`, `projB-core`), then the next ancestor, up to the corpus root, whose name is the last one taken. A set whose names have reached the corpus root, and the corpus root's set, take no more. Names still colliding then take ` (2)`, ` (3)` and on, in the canonical order of their folders, the first keeping its name. **The canonical order** is the ordinal order of the relative paths folded to lower case with `Locale.ROOT`, and, where two fold alike, of the paths as spelt.

**Two tables, owned by `corpus`, record each run's sets.** Both are additive: a database written before this record gains them empty at start-up, and `CorpusSchema`'s version does not move, as [ADR-271](0271-one-copy-of-a-translation-set-survives-in-the-language-the-profile-prefers-first.md) has it for `translation_of`.

- `code_set (run_id TEXT NOT NULL REFERENCES run (id), folder TEXT NOT NULL, default_name TEXT NOT NULL, PRIMARY KEY (run_id, folder))`. `folder` is the set's folder relative to the corpus root, spelt as the occurrences' paths spell it, `/`-separated, and `''` for the corpus root; `default_name` is the name after the collision rule.
- `code_set_member (run_id TEXT NOT NULL REFERENCES run (id), occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id), folder TEXT NOT NULL, role TEXT NOT NULL, generator TEXT, PRIMARY KEY (run_id, occurrence_id))`. `role` is `code`, `build-file` or `generated-tree`; #575 may add `configuration` through the same table. `generator` is set as §4 says, and null otherwise.
- **One index**, `code_set_member_by_occurrence_id`, on `occurrence_id` (ADR-173). Each `run_id` leads its table's primary key, which is the index it needs. The index is built with the table, over no row, and sorts nothing.
- **Both tables grow by a copy for each stage-1 run**, as `detected_format` does, and nothing removes another run's rows (ADR-229).

### 6. Where the set pass runs

**Stage 1 is three passes**: the first pass of ADR-277 §6, then the set pass, then the hashing. The set pass needs every `code` verdict of the first pass, and what it verdicts is never hashed, neither superseding nor superseded.

- **It holds the sets, the folders holding a build file and the generated trees**, and no collection of the run's occurrences. It reads the survivors and the `code` verdicts a page at a time ([ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md)).
- **Its log line**, for each file it verdicts: `[byte-level-reduction] kept <occurrence> in its code set -> code: <reason>`.
- **An unfinished stage 1 redone** discards the run's rows of `code_set` and `code_set_member` with its verdicts and detected formats, so each set and each member has one row.

### 7. `code-sets.csv`

**At the end of its set pass, stage 1 writes `code-sets.csv` beside the database**, UTF-8, each record ended by a line feed, as the manifest `documents.csv` is:

`folder,default_name,code,build_files,generated_tree`

- **One row per set of the run, in the canonical order of its folder.** `folder` as `code_set` holds it, empty for the corpus root; `default_name`; and the number of members of each role.
- **Each text field is quoted by RFC 4180 where it holds a comma, a quote, a carriage return or a line feed**, a quote doubled, and is written behind a single quote where a spreadsheet would run it, as ADR-235 has it for the manifest's paths.
- It is the operator's list to rename from, and holds folder paths as stage 2's survivor list holds paths. It is written again by every stage 1 that works, and is not written by one already recorded.

### 8. Renaming a set: `code-set-names.csv`

**A rename is stored in `code-set-names.csv`, beside `vespera.db` and `profile.yaml`**, UTF-8, RFC 4180, header `folder,name`. It is written only from what the operator typed, in the control panel (#566), or by the operator's hand.

**It keys no run and moves no run id.** No stage reads it. It is read where a set's name is rendered: the deliverable's drawing (#573), which keys no run (ADR-274 §5), and the control panel.

**`CodeSetNames`, in `deliverable`, reads it**, against the sets of a run and their default names handed to it in canonical order, and gives each set its effective name and the rows it could not apply.

- **The file.** A leading byte-order mark is skipped, and a line ends with a line feed or a carriage return and a line feed. A first record that reads `folder,name` is the header; any other is a row. A blank line is nothing. An absent file renames nothing.
- **`folder`** is spelt as `code-sets.csv` spells it: a field written behind ADR-235's single quote is read without it.
- **A name is accepted** where it is not blank; holds none of `<`, `>`, `:`, `"`, `/`, `\`, `|`, `?`, `*`, nor a character below U+0020; does not end in a space or a dot; and is not a Windows device name, `CON`, `PRN`, `AUX`, `NUL`, `COM1` to `COM9` or `LPT1` to `LPT9`, compared folded with `Locale.ROOT`, alone or before a dot. **There is no limit on its length**; how long a path a copy may take is #573's.
- **The rows are applied in the file's order.** A row is ignored, and the set keeps the name it had, where it does not hold two fields, where its folder is no set's of the run, where its name is not accepted, or where its name collides, folded, with the name another set carries at that row. A folder given in more than one row is read in its first row only, whatever came of it, and its later rows are ignored. So a swap of two names takes two drawings.
- **The invocation that renders names closes**, where nothing earlier is wanted, with one line: *"Every run value is set, but code-set-names.csv renames N code sets in a way this run could not apply, so their default names were kept. Next: correct each row of code-set-names.csv that names a folder no code set has, or a name that is blank, holds a character a folder name cannot, or is already another code set's name, and run again."*, N counting the rows ignored. #573 writes it.

**Rejected for a rename's store**: a profile value, since every profile value is one line of text (ADR-061, ADR-120), and pairs of folders and names, whose folder names may hold any character, fit none; it would also invite a stage to read it. The control panel's own temporary file, which is lost, unseen by an operator who uses only the command line, and not beside the working directory it belongs to.

### 9. What the operator sees

- **The verdicts and their reasons** (§4), and the log line (§6).
- **The format-mix page.** After ADR-277 §7's paragraph counting code, which still counts the files the first pass kept as code, a sentence counts the sets and their members by role: *"Code sets: N, holding C code files, B build files and G files of generated reference trees."* Its *Kept as code* table gains a row `build file` and a row `file of a reference tree generated by <generator>` for each generator, each with its count. The page names no folder and no set.
- **`code-sets.csv`** (§7).
- **The progress page, the tab of runs and the invocation account** count the set pass's verdicts under `code`, as they count every `code` verdict (ADR-277 §7).

### 10. Stage 1's configuration consumed records the list

`ConfigConsumed` gains `buildFiles`, the version of §1's list, `"v1"`, after `generatorMarks`. Where the profile sets none of the keys stage 1 reads, it reads:

`{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null,"outOfScopeNameEndings":[],"codeNameEndings":[],"generatorMarks":"v1","buildFiles":"v1"}`

### 11. Run ids, and the ADR-216 check

- **`corpus`**: the build-file list and its version, the set pass, the naming rule, the two tables and their reads and writes.
- **`pipeline`**: `ByteLevelReductionTasklet` (the set pass's call, its log line, its discard, `code-sets.csv`, and the page written after the set pass), and `FormatMixReport`. No stage's version names `pipeline` (ADR-222), and a class added there for `code-sets.csv` holds no rule.
- **`deliverable`**: `CodeSetNames`. No stage's version names `deliverable` (ADR-274 §5).
- **`ledger`**: none. No kind is added, and the discard already names `CODE`.

**Stage 1 moves**: its version names `corpus`, and its configuration consumed gains `"buildFiles":"v1"`. **Every later stage follows**, each naming the run upstream of it (ADR-089). **A rename moves none.**

## Alternatives rejected

- **A set for every folder holding a build file, code or none.** A multi-module parent would be a set of no code, and a folder of documents holding a `Makefile` would be one.
- **The outermost folder holding a build file.** A multi-module build would be one set, and a module could not be named or copied apart.
- **Membership by location alone, every file below a set's folder.** A set found by the first folder under the root can cover a folder of documents, and every one of them would leave the knowledge base.
- **Every file of a generated tree, PDFs and guides with them.** ADR-277 decided that a generator's guide is a document; a PDF is judged by its bytes.
- **The build-file list in the profile.** It is a fact about build tools, as ADR-277's marks are about generators.
- **Sets computed in a later stage.** A build file or a tree's picture would by then have been hashed, converted and scored.
- **A table of sets in the ledger, or a verdict of its own for a build file.** Membership is a relation `corpus` alone writes, as content identity is; a build file is code, kept apart for the reason ADR-277 keeps code apart.
- **A name compared as spelt.** Two sets whose names differ only in case would be copied, on Windows, into one folder.
- **A cap on a name's length.** No length is measured to rest one on, and the path a copy takes is #573's to bound.

## Consequences

- With code kept, the operator's code is listed by the folder it lies below, and each module of a build is a set of its own.
- **A build file of a set never reaches the documents.** A build file in no set, a multi-module parent's among them, still does, until #575 decides it.
- **A lone saved reference page in a folder of photos makes that folder a generated tree**, and its pictures and its text files that are no HTML page are kept as code. The format-mix page counts them, and their reason names the generator.
- **A tree whose pages lie in two far folders makes the folder above both its tree**, and every picture and text file between them inside the set's extent is kept as code.
- **Code at the root of an archive with no build file at its root is one set**, named for the archive's folder, holding only what lies directly there.
- **A rename the operator writes is read only where names are rendered**, so a name refused is reported there and nowhere earlier.
- `CONTEXT.md` defines **Code set** and **Build file**, and its entries for **Verdict** and **Code** are widened.
- `EveryTableKeyedByARunIsOnRecordTest` holds twenty-nine tables, and `EveryStatementThatSortsIsRecordedTest` thirty-five indexes. The README's *Where things live* names `code-sets.csv` and `code-set-names.csv`, and `AGENTS.md` counts the tables keyed by a run as twenty-nine, in the change that builds them.

## Tests

Every file is built in the test, and every profile is YAML the test writes. The tables are read by their names and the verdicts by their stored names, and `CodeSetNames` is reached by its name, so every test compiles before what it holds is built.

| Class | What it holds |
| --- | --- |
| `pipeline.CodeSetTaskletTest.eachSetLiesBelowTheNearestFolderHoldingABuildFileElseTheFirstFolder` | code two folders below a `CMakeLists.txt` is in that folder's set; a script with no build file above it is in the set of the first folder under the root; a folder of documents is no set |
| `…eachModuleOfAMultiModuleBuildIsASetOfItsOwn` | two modules under a parent `pom.xml` are two sets named for their folders; the parent's `pom.xml` carries no verdict and is no member |
| `…aSetsExtentStopsAtTheFolderOfASetNestedInIt` | a nested set takes its own build file and code; a build file in a folder below a set holding no code is the outer set's |
| `…aCorpusRootHoldingABuildFileIsTheSetOfEverythingBelowIt` | a root `Makefile` makes one set of the root, named for the root's folder, holding the code of every folder below |
| `…codeLyingAtACorpusRootWithNoBuildFileIsASetOfWhatLiesDirectlyThere` | the root's set holds the code and a generated tree's picture lying at the root, and not a picture one folder down; a folder of code below is a set of its own |
| `…collidingNamesTakeTheNamesOfTheFoldersAboveThem` | `projA-core` and `projB-core`; a second round reaching the root's name; two names differing in case collide; a name colliding with none is untouched |
| `…aSetAtTheRootKeepsTheRootsNameAndASetOfTheSameNameTakesItsParents` | the root's set is named for the root, and a set below it of that name is prefixed with it |
| `…namesThatDifferOnlyInTheCaseOfTheirPathTakeANumber` | on a file system that holds both, `a/Lib` keeps its name and `a/lib` takes ` (2)` |
| `…aBuildFileInASetIsKeptAsCodeWithIt` | a name of the list in another case, two endings and two identical `setup.cfg` are code with §4's reason, members as build files, never hashed; the log line says so; `DESCRIPTION`, `Doxyfile`, `.pro` and `Makefile.am` are not build files |
| `…aBuildFileIsOneOnlyWhereItsBytesReadAsText` | a binary `Makefile`, a PDF named `package.json` and an empty `CMakeLists.txt` mark no folder and are kept as they were; a `Makefile` in a single-byte encoding marks its folder and is a member |
| `…theFilesOfAGeneratedTreeAreKeptAsCodeAndItsGuidesAndPdfsStayDocuments` | Javadoc's picture, `element-list`, script and stylesheet and Doxygen's graph, `.map` and `.md5` are code, reasoned and recorded with their generator, their formats unchanged; a guide, a PDF, a page named `.html` of no known kind and a picture outside the tree carry no verdict |
| `…documentsInsideASetStayDocuments` | a `README.md`, a PDF, a picture and a note inside a set carry no verdict and are no members |
| `…aLogAnEmptyFileAndAFileOutOfScopeInsideASetKeepTheirVerdicts` | each keeps its one verdict and is no member; a build file left out of scope marks no folder |
| `…theTablesHoldARowForEachSetAndEachMemberUnderStageOnesRun` | one `code_set` row and five `code_set_member` rows, roles and generators as §4 says, under stage 1's run |
| `…codeSetsCsvListsEverySetInTheCanonicalOrder` | the file, word for word: the root's set with an empty folder, a folder behind a single quote, a folder quoted for its comma, and the counts |
| `…theFormatMixPageCountsTheSetsAndTheirMembersAndNamesNone` | §9's sentence and two rows; the paragraph of code still counts the first pass's; no folder is named |
| `…aStageOneLeftUnfinishedIsRedoneWithOneRowOfEachSetAndMember` | stage 1 redone leaves one row of each and one `CODE` verdict on a build file |
| `…aRenameIsNotReadByStageOne` | with `code-set-names.csv` renaming a set, stage 1 records the default name and its configuration consumed holds no name |
| `deliverable.CodeSetNamesTest.aRowRenamesTheSetWhoseFolderItNames` | the root's set, a quoted folder, a folder behind the single quote, a byte-order mark and carriage returns |
| `…aNameIsAcceptedOnlyWhereAFolderCouldBeNamedSo` | each refused form of §8, and accepted names close to them, one of 300 characters among them |
| `…aRowThatCannotBeAppliedIsIgnoredAndTheNameKept` | a folder of no set, a refused name, a name another set carries, folded, a folder given twice and a row of three fields are ignored, in the file's order, and the rest applied |
| `…withNoFileEverySetKeepsItsDefaultName` | an absent file renames nothing |
| `pipeline.RunIdentityGoldenTest.byteLevelReduction` | §10's configuration consumed, word for word |
| `EveryTableKeyedByARunIsOnRecordTest`, `EveryStatementThatSortsIsRecordedTest` | the two tables and the index of §5 |

A corpus root with no name, a drive root, cannot be walked by a test, so the name `root` has none.

## What this does not decide

- **Code in the deliverable** (#573): what is copied of each set, how a copy is checked, where on the tree a set's folder lies, the rendering of names and the closing line of §8.
- **The control panel's screen for a rename** and its write of `code-set-names.csv` (#566).
- **Configuration files, and a build file outside every set** (#575), which may add a role through `code_set_member`.
- **A tree's empty files**, which stay `broken`, so a set lacks them (ADR-277).
