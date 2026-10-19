# ADR-284 — Code is kept in code sets, each below the nearest folder under the corpus root holding a build file

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-277](0277-code-is-recognised-by-a-name-ending-the-profile-lists-or-by-a-documentation-generators-mark-and-stage-1-takes-it-out-of-the-survivors.md) §1, §3 and §8. Code is also a set's build file and a file of a generated tree (§4, §5), a build file is told by its name alone, whatever its bytes, where it marks a set's folder (§1), and stage 1 writes `code` in a second pass too (§6). ADR-277's two rules, its marks, its verdict and its order in the first pass stand.
- **Answers**: ADR-277, the first item of its *What this does not decide*: how code is grouped by its path, and what a generated tree's pictures and companion files become.
- **Keeps**: [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md). No kind is added.
- **Keeps**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md). Every member's detected format is the one its bytes gave. The verdict is about where a file lies, as `superseded-by` is about another file, and not about what its bytes are.
- **Keeps**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) and ADR-277 §8. The discard before an unfinished stage 1 works again already holds `CODE`, and takes the two tables of §7 with it.
- **Rests on**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §3, by which a new table no existing row depends on moves no schema version; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §3, [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md) and [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), for the tables (§7); [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md), for what the set pass holds (§6); [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, by which `deliverable` keys no run (§9); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), for the run ids (§11).

## Context

ADR-277 takes code out of the survivors file by file. A project's code is read as a tree, and a file of it alone means little, so the code is kept by the folder it lies below. Four facts shape how.

- **A build tool marks where a project begins.** The folder holding a `pom.xml`, a `CMakeLists.txt` or a `*.sln` is the folder its tool builds from, and a multi-module build puts one in each module's folder as well as one above them.
- **Archives hold code with no build file at all**: a folder of scripts, a copy of one source file, a generated reference tree saved on its own.
- **A generated reference tree is more than its pages.** Javadoc writes `element-list`, a stylesheet, scripts and pictures beside its pages; Doxygen writes `doxygen.css`, and graphs as `.png`, `.map` and `.md5`. ADR-277 keeps the pages as code and leaves the rest where it was: pictures among the documents, and text of no use to anyone among the survivors. `docs/research/documentation-generator-markers.md` §5 lists the companion files each generator writes.
- **Stage 1's first pass judges one file at a time.** Whether a file lies in a project needs every `code` verdict of the run, which exists only once the first pass ends, and whatever is added must come before the hashing, so that it is never hashed.

## Decision

### 1. A build file

**A build file is a file occurrence of the walk whose name, the last element of its path, folded to lower case with `Locale.ROOT`, is one of the names below, or ends with one of the endings below after at least one character.** No byte is read, and the occurrence's detected format and verdicts do not matter: an empty `Makefile` or a `.sln` in UTF-16 marks its folder as any other does. A walk anomaly is no occurrence and marks nothing.

| Matched | v1 |
| --- | --- |
| The whole name | `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`, `cmakelists.txt`, `makefile`, `gnumakefile`, `configure.ac`, `package.json`, `cargo.toml`, `go.mod`, `pyproject.toml`, `setup.py`, `build.bazel`, `module.bazel`, `build.xml`, `build.sbt`, `meson.build`, `gemfile`, `composer.json`, `mix.exs`, `package.swift`, `pubspec.yaml`, `project.clj`, `deps.edn`, `stack.yaml`, `dune-project`, `rebar.config`, `sconstruct` |
| The end of the name | `.sln`, `.csproj`, `.vbproj`, `.fsproj`, `.vcxproj`, `.vcproj`, `.gemspec`, `.cabal` |

**Left out**: `.pro`, a bare `BUILD` and a bare `WORKSPACE`, which name documents as often as builds; `requirements.txt` and `setup.cfg`, which list or configure and build nothing; `Dockerfile`, which builds an image and not the code beside it; and `Rakefile`, which runs tasks of any kind. A name that is only an ending, `.sln`, is none.

**The list is built into `corpus`, as ADR-277 §4's marks are, and is changed by a change that carries a record.** Its version is `v1` (§10). It is a fact about build tools and not about an archive, so the profile does not hold it, for the reason ADR-277 gives for the marks.

### 2. A set's folder

**A file's set folder is the nearest folder holding a build file, from the file's own folder up to the first folder under the corpus root, both included; else the first folder under the corpus root on the file's path.** A folder holds a build file where an occurrence lying directly in it is one (§1).

**The corpus root anchors no set.** A build file lying at the root is passed over, and the files lying directly at the root have the root as their set folder, written `''`, whatever lies beside them.

**A code set exists for each folder that is the set folder of at least one file carrying `code` for itself**: for the ending of its name or a generator's mark (ADR-277), or as a file of a generated tree (§5). A build file kept with a set (§4) never makes one.

**Each module of a multi-module build is its own set**, since its own build file is nearer to its code than the parent's. `shop/pom.xml` above `shop/core/pom.xml` and `shop/api/pom.xml`, with the code in the modules, gives two sets, `shop/core` and `shop/api`, and `shop/pom.xml` lies in no set. Where code lies in `shop`'s own extent, in a folder of it holding no build file, `shop` is a set too, and its `pom.xml` is that set's build file. A build file outside every set is #575's to decide; until then it goes on as the first pass left it.

### 3. A set's extent

**An occurrence lies in the set whose folder is the deepest set folder at or above its own folder**, and in no set where there is none. The root's set covers only the files lying directly at the root. So a set's extent stops at the folder of a set nested in it, and an occurrence lies in one set at most. Seeds are no occurrences of the walk and lie in none.

### 4. Its members

Every member is an occurrence of the set's extent, a member of that one set, with one of three roles. `configuration` is kept for #575.

- **`code`**: each occurrence carrying `code` for itself, by ADR-277's rules. It keeps that verdict and reason: a `setup.py` kept for its ending is a member as code, and makes its set.
- **`companion`**: each file of a generated tree (§5).
- **`build`**: each build file of the extent that is a survivor after the first pass, detected `PLAIN_TEXT` or `UNRECOGNISED`, and whose bytes read as text by ADR-277 §3's test. The set pass verdicts it `code` with the reason *"a build file of the code set at shop/core"*, the folder as `code_set` holds it, and *"a build file of the code set at the corpus root"* for the root's set. A build file that is empty is `broken` and stays so; one in UTF-16 or of bytes that do not read as text marks its folder and is left as it was.

**Only a survivor of the first pass is touched by the set pass.** A log inside a set keeps its `out-of-scope` verdict, an empty file stays `broken`, and a file out of scope stays out of scope. None of them is a member.

**A document inside a set is no member and carries no verdict of this record**: a `README.md`, a `docs/` folder of Word files, a PDF, a picture outside a generated tree, a generator's guide. Relevance decides it as it decides any document. The operator's prose about their code is what the knowledge base is for, and a set found by the first folder under the root can cover a folder of documents.

**Nothing is removed for being in a set.** A tree present twice is two sets, both kept, each with its members. No rule of redundancy is drawn between sets.

### 5. A generated tree

**A generated tree of generator G is rooted at a folder that directly holds both a page carrying `code` for G's mark (ADR-277 §4) and one of G's companion files below.** The tree covers that folder and every folder below it. Where such folders nest, the highest is the tree's root. A marked page with no companion of its generator beside it roots no tree, so one saved reference page in a folder of photos and notes leaves them as they were.

| Generator | Companion files, v1, matched by name folded to lower case, whole or, for a leading `*`, at its end after at least one character |
| --- | --- |
| Javadoc | `element-list`, `package-list`, `*search-index.js` |
| Doxygen | `doxygen.css`, `dynsections.js`, `*.map`, `*.md5` |
| RDoc | `created.rid` |
| pdoc | `search.js` |
| mkdocstrings | `objects.inv` |

**Inside a tree, each survivor of the first pass is a `companion` member where it is detected `IMAGE`, or detected `PLAIN_TEXT` with no subtype, or `UNRECOGNISED`, and its bytes read as text by ADR-277 §3's test.** The set pass verdicts it `code` with the reason *"a file of the tree Javadoc generated"*, G named as ADR-277 §4 names it, and its member row records G. A file inside two generators' trees belongs to the tree whose root is deeper, and where the roots are one folder, to the generator ADR-277 §4's tables list first.

- **Left as they were**: an HTML page, which ADR-277's mark rule alone judges, so an unmarked page or a guide in a tree stays a document; text with the `MARKDOWN`, `CSV` or `ASCIIDOC` subtype, which is where documents are; a file whose bytes do not read as text, an `objects.inv` among them; and every class a signature named but `IMAGE`, a PDF or a Word file among them. A font is out of scope already (ADR-252).
- **The companions' list is built into `corpus`** beside the marks, versioned `v1` (§10), and is changed by a change that carries a record.
- **A tree is found from the first pass's own findings and never from a reason.** The first pass hands the set pass, for each generator, the folders directly holding a page it kept for that generator's mark: folders, never pages (§6). The wording of a reason may change without changing a tree.
- **A marked page's generator is read again from its bytes.** For each member as code lying in a folder the first pass handed over for some generator, and whose name ends with no ending `codeNameEndings` lists, the set pass applies ADR-277 §4's test to the page's bytes, as the first pass applied it, and the generator that test names is the one its member row records (§7). A page that test names none for, its bytes having changed since the first pass, is a member as code with no generator. A page the set pass cannot read is a member as code with no generator too, and is logged (§6). Neither changes a tree, which the first pass's folders found.

### 6. Stage 1 in three passes

**Stage 1 is the first pass of ADR-277 §6, then the set pass, `CodeSets`, then the format-mix page and the measurement it points to, then the hashing.** What the set pass verdicts is never hashed, neither superseding nor superseded.

- **What it holds.** The folders holding a build file, the folders holding a marked page or a companion file of each generator, and the sets: folders, never occurrences. It reads the walk's occurrences, the survivors and the `code` verdicts a page at a time (ADR-220). The first pass's hand-over is folders too (§5), so neither pass holds an occurrence for the other, and no exception to ADR-220 §9 is made: a page's generator is read again from its bytes when its member row is written, and not kept from the first pass.
- **A file it cannot read**, to see whether its bytes read as text (§4, §5), is left as it was, as the first pass leaves a file it cannot read for the code rules (ADR-277): no verdict, no member, and it still marks its folder or roots its tree, which go by names alone. The pass carries on, and logs `[byte-level-reduction] could not read <occurrence> to see whether it reads as text, so it is left as it was: <cause>`. A marked page it cannot read again (§5) is logged `[byte-level-reduction] could not read <occurrence> to see which generator's mark it carries, so it is kept with none: <cause>`.
- **Its log line**, for each file it verdicts, the whole line: `[byte-level-reduction] kept <occurrence> with its code set -> code: <reason>`.
- **An unfinished stage 1 redone** discards the run's rows of `code_set` and `code_set_member` with its verdicts and detected formats, so each set and each member has one row.
- **For later stages**, `CodeSets.setOf(run, path)` answers which set's folder an occurrence's path lies in under a stage-1 run, the run a later stage names upstream, or none.

### 7. The two tables

Both are owned by `corpus`, additive, and built empty at start-up on a database written before them; `CorpusSchema`'s version does not move (ADR-197 §3).

- `code_set (run_id TEXT NOT NULL REFERENCES run (id), folder TEXT NOT NULL, default_name TEXT NOT NULL, PRIMARY KEY (run_id, folder))`. `folder` is the set's folder relative to the corpus root, spelt as the occurrences' paths spell it, `/`-separated, `''` for the root's set.
- `code_set_member (occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id), run_id TEXT NOT NULL REFERENCES run (id), folder TEXT NOT NULL, role TEXT NOT NULL, generator TEXT, PRIMARY KEY (occurrence_id, run_id))`. `role` is `code`, `companion` or `build`. `generator` is a companion's generator and the generator of a page kept for its mark, as the set pass reads it again (§5), and null for every other member.
- **One index**, `code_set_member_by_run_id`, on `code_set_member (run_id)` (ADR-173); `code_set`'s primary key leads with `run_id`, and `code_set_member`'s with `occurrence_id`. It is built with the table, over no row, and sorts nothing.
- **Both grow by a copy for each stage-1 run**, as `detected_format` does, and nothing removes another run's rows (ADR-229).

### 8. A set's name

**`code_set.default_name` is the last element of the set's folder, as its path spells it.** The root's set is named for the corpus root's last element: a folder's name, a UNC root's share name, or a drive root's letter.

**Names are told apart and made safe where the deliverable is drawn (#573), and nothing of that is stored.** The rule #573 applies:

- **Two names are one where they are equal after Unicode NFC and upper case with `Locale.ROOT`.**
- **A name the operator typed (§9) outranks a default name.** Among names alike of the same kind, the set whose lowest member occurrence id is lower comes first.
- **The first keeps the name; each other takes `~` and its own lowest member occurrence id** (`core~4127`).
- #573 then applies the safe-name rule #570 settles.

**What #573 owes**: a drawing never puts one set's copies into another set's folder, whatever the names are and however they are typed.

### 9. Renaming a set: `code-set-names.yaml`

**A rename is stored in `<working directory>/code-set-names.yaml`**, UTF-8, a YAML list of entries, each a mapping of exactly two strings, `folder` and `name`:

```
- folder: shop/core
  name: Shop core
- folder: ""
  name: Archive
```

- **`folder`** is the set's folder relative to the corpus root, `/`-separated, compared exactly, `""` for the root's set. It holds no `\`, and neither begins nor ends with `/` nor holds an empty element.
- **`name`** is the name the operator typed, not blank. Only a typed name is written; clearing a name removes its entry.
- **A file that is not such a list**, a folder given twice among them, is refused whole, naming the file; no name of it is applied. An absent file, or one holding nothing, renames nothing.
- **Who writes it**: the control panel (#566), as a `.part` file moved into place, or the operator by hand. An agent writes it only with a name the operator stated. **Vespera's jar never writes it.**
- **It keys no run and moves no run id.** No stage reads it. The drawing (#573) reads it once, which keys no run (ADR-274 §5), and the control panel reads it.
- **An entry naming a folder no set of the run has is dormant**: kept, unapplied, and applied again by a run whose sets include its folder. The drawing's closing line counts the dormant entries, in words #573 writes.
- **`CodeSetNames`, in `deliverable`**, reads the file and checks its shape, and is the one reader. Applying the names is #573's.

### 10. What the operator sees, and stage 1's configuration consumed

- **The verdicts and their reasons** (§4, §5), and the log lines (§6).
- **The format-mix page.** After ADR-277 §7's paragraph counting code, which still counts the files the first pass kept as code, one sentence counts the sets and their members by role: *"Code sets: N, whose members are files of code: C, build files: B, and files of generated reference trees: T."* Its *Kept as code* table gains a row `build file` and a row `file of a tree <generator> generated` for each generator, each with its count. The page names no folder and no set.
- **The progress page, the tab of runs and the invocation account** count the set pass's verdicts under `code`, as they count every `code` verdict.
- **`ConfigConsumed`** gains, after `generatorMarks`, `buildFiles` and `generatorCompanions`, the versions of §1's and §5's lists. Where the profile sets none of the keys stage 1 reads, it reads:

`{"textSizeCeilingBytes":16000000,"textParts":"v1,over=16000000,upto=64000000,part=8000000","logTimestampShareFloor":null,"outOfScopeNameEndings":[],"codeNameEndings":[],"generatorMarks":"v1","buildFiles":"v1","generatorCompanions":"v1"}`

### 11. Run ids, and the ADR-216 check

- **`corpus`**: the two lists and their versions, `CodeSets` and its reads and writes, the first pass's hand-over of the folders holding each generator's marked pages, the set pass's reading of a page's mark again, and the two tables.
- **`pipeline`**: `ByteLevelReductionTasklet` (the set pass's call, its log line, its discard, the order of §6 and the configuration consumed) and `FormatMixReport`. No stage's version names `pipeline` (ADR-222).
- **`deliverable`**: `CodeSetNames`. No stage's version names `deliverable` (ADR-274 §5).
- **`ledger`**: none.

**Stage 1 moves**: its version names `corpus`, and its configuration consumed gains two fields. **Every later stage follows**, each naming the run upstream of it (ADR-089). **A rename moves none.**

## Alternatives rejected

- **Widening a set to the highest folder holding a build file of the same tool.** It joins the modules the operator's rule keeps apart; the nearest folder is what was settled.
- **The corpus root as an anchor.** One build file at the root would make the whole archive one set, every project below it among its members' folders.
- **A set for every folder holding a build file, code or none.** A multi-module parent would be a set of no code, and a folder of documents holding a `Makefile` would be one.
- **Membership by location alone, every file below a set's folder.** A set found by the first folder under the root can cover a folder of documents.
- **A tree as the deepest folder above its generator's pages, with no companion asked for.** One saved reference page would make a folder of photos code.
- **Reading a build file's bytes to tell where a set begins.** An empty or a UTF-16 build file says where its tool builds from as well as any.
- **The lists in the profile.** They are facts about build tools and generators, as ADR-277's marks are.
- **Sets computed in a later stage.** A build file or a tree's picture would by then have been hashed, converted and scored.
- **Names told apart in stage 1 and stored.** A rename would then move stage 1's run, and the safe-name rule is the drawing's (#570, #573).
- **A rename as a profile value.** Every profile value is one line of text (ADR-061, ADR-120), and pairs of folders and names fit none; it would also invite a stage to read it.
- **A file of the sets beside the database for the operator to rename from.** The control panel shows the sets, and a second list of them would be one more file to keep true.

## Consequences

- With code kept, the operator's code is held by the folder it lies below, each module of a build a set of its own.
- **A set's build file never reaches the documents**; a build file in no set, a multi-module parent's among them, still does, until #575 decides it.
- **Code lying directly at the corpus root is one set**, named for the root, holding only what lies there, whatever build file lies beside it.
- **A tree whose companion files a generator did not write beside a marked page is not found**, and its pictures stay documents.
- **A rename is applied only where names are rendered**, so a dormant entry is reported there and nowhere earlier.
- `CONTEXT.md` defines **Code set**, **Build file** and **Generated tree**, and widens **Verdict**, **Code**, **Survivor list**, **Arrangement**, **Picture** and **Publication-ready artifact**.
- In the change that builds this, `EveryTableKeyedByARunIsOnRecordTest` holds twenty-nine tables, `EveryStatementThatSortsIsRecordedTest` thirty-five indexes, `AGENTS.md` counts twenty-nine tables keyed by a run, and the README's *Where things live* names `code-set-names.yaml`.

## Tests

Every file is built in the test, and every profile is YAML the test writes. The tables are read by their names, the verdicts by their stored names, and `CodeSetNames` by its name, so every test compiles before what it holds is built.

| Class | What it holds |
| --- | --- |
| `pipeline.CodeSetTaskletTest.eachSetLiesBelowTheNearestFolderHoldingABuildFileElseTheFirstFolder` | code two folders below a `CMakeLists.txt` is in that folder's set; a script with no build file above it is in the first folder's; a folder of documents is no set |
| `…eachModuleOfAMultiModuleBuildIsASetOfItsOwn` | `shop/core` and `shop/api` are two sets, and `shop/pom.xml` is in neither and carries no verdict |
| `…aBuildFileAboveModulesIsASetsWhereCodeLiesInItsOwnExtent` | code in a folder of `shop` holding no build file makes `shop` a set, whose build file `shop/pom.xml` is |
| `…aSetsExtentStopsAtTheFolderOfASetNestedInIt` | `Work/Makefile` above `Work/shop/pom.xml` gives two sets; a build file in a folder holding no code is the outer set's |
| `…theCorpusRootAnchorsNoSetAndItsSetHoldsWhatLiesDirectlyThere` | a root `pom.xml` is passed over; the root's set, named for the root, holds the root's files only; the folders below are sets of their own |
| `…aBuildFileIsToldByItsNameAlone` | `POM.XML`, `makefile`, a `.csproj`, an empty `Makefile` and a binary one mark their folder; `.sln` alone, `.pro`, `BUILD`, `Dockerfile`, `setup.cfg` and `Rakefile` do not |
| `…aBuildFileInASetIsKeptAsCodeWithIt` | build files of a set are code with §4's reason, members as `build`, never hashed, the log line saying so, matched whole; a `setup.py` kept for its ending is a member as code and makes its set; one in UTF-16, an empty one and a binary one are left as they were |
| `…theFilesOfAGeneratedTreeAreKeptAsCodeAndItsDocumentsStayDocuments` | Javadoc's picture, stylesheet, script and `element-list` and Doxygen's graph, `.map`, `.md5` and `doxygen.css` are code with their generator, formats unchanged; a `README.md`, an unmarked page, a PDF and a picture outside the tree stay as they were |
| `…aLoneMarkedPageRootsNoTree` | a saved Javadoc page beside notes and photos, with no companion, leaves them as they were |
| `…nestedTreesAndTreesOfTwoGeneratorsAreToldApartByTheirRoots` | Javadoc's nested folders root its tree at the highest, so a picture below Doxygen's root inside it is Doxygen's, the deeper root; where Javadoc's and Doxygen's roots are one folder, its picture is Javadoc's |
| `…aPageKeptForTheEndingOfItsNameRootsNoTreeAndHasNoGenerator` | with `.html` listed, a marked page is a member as code with no generator, and the companion and picture beside it stay as they were |
| `…aFileTheSetPassCannotReadIsLeftAsItWasAndThePassCarriesOn` | a build file and a companion gone before the set pass reads them carry no verdict and are no members, still mark their folder and root their tree, are each logged once with §6's line, and the next set's build file is kept |
| `…aMarkedPageTheSetPassCannotReadAgainIsKeptWithNoGenerator` | a marked page gone after the first pass keeps its verdict and is a member as code with no generator, its tree still found, logged once with §6's line |
| `…twoIdenticalTreesAreTwoSets` | the same tree under two folders is two sets, each with its companions |
| `…documentsInsideASetStayDocuments` | a `README.md`, a PDF, a picture and a note inside a set carry no verdict and are no members |
| `…aLogAnEmptyFileAndAFileOutOfScopeInsideASetKeepTheirVerdicts` | each keeps its one verdict and is no member; a build file left out of scope still marks its folder and is no member |
| `…theTablesHoldARowForEachSetAndEachMemberUnderStageOnesRun` | one `code_set` row and five `code_set_member` rows, roles and generators as §4 and §5 say, under stage 1's run |
| `…theFormatMixPageCountsTheSetsAndTheirMembersAndNamesNone` | §10's sentence and two rows; the paragraph of code still counts the first pass's; no folder is named |
| `…aStageOneLeftUnfinishedIsRedoneWithOneRowOfEachSetAndMember` | one row of each and one `CODE` verdict on a build file |
| `…aRenameIsNotReadByStageOne` | with `code-set-names.yaml` renaming a set, stage 1 records the default name and its configuration consumed holds no name |
| `deliverable.CodeSetNamesTest.theEntriesAreReadAsWritten` | the file's name; entries in order, the root's set by `""`; an absent or empty file has none |
| `…aFileThatIsNotAListOfFoldersAndNamesIsRefusedNamingIt` | a mapping, an entry of three keys, a number for a name, a blank name, a `\`, a leading or trailing `/`, an empty element, a folder given twice and YAML that does not parse are each refused, the message naming the file |
| `pipeline.RunIdentityGoldenTest.byteLevelReduction` | §10's configuration consumed, word for word |
| `EveryTableKeyedByARunIsOnRecordTest`, `EveryStatementThatSortsIsRecordedTest` | the two tables and the index of §7 |

No test holds that the set pass and the first pass's hand-over hold folders and no occurrence (§5, §6): what a class holds does not show in what it writes. No test of this record holds `CodeSets.setOf` (§6) or §8's naming rule: neither has a reader before #573, whose tests hold both with the first caller. A UNC root and a drive root cannot be walked by a test, so their names have none.

## What this does not decide

- **Code in the deliverable** (#573): what is copied of each set and how a copy is checked, §8's naming at the drawing and the safe-name rule (#570), the closing line for dormant entries, and the **Copy** entry of `CONTEXT.md`.
- **The control panel's screen for a rename** and its write of `code-set-names.yaml` (#566).
- **Configuration files, and a build file outside every set** (#575), which may add the role `configuration`.
- **The README's and the architecture's text for code sets** (#577).
- **Text with no recognisable text** (#576). The set pass touches only what it names here.
