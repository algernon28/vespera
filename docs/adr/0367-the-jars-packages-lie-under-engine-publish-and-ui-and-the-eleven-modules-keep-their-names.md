# ADR-367 — The jar's packages lie under engine, publish and ui, and the eleven modules keep their names

- **Date**: 2026-11-08
- **Status**: accepted on 2026-11-08.
- **Amends**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) where it, and the documents it points at, place the modules as packages directly under `io.algernon.vespera` (§1); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)'s Decision, on the path the last commit is asked of (§3); [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §1 and §3 and [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §2 and §3, for the names of the control panel's package and of the entry point's (§4).
- **Keeps**: ADR-040's capability-shaped modules and its rule, with the exceptions ADR-100, ADR-226, ADR-274 §4, ADR-282 and [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §1 record, each declared in its `@ApplicationModule`; the names of the eleven modules, which are the names of the stages' versions in `StageModules` and of the tables' owners in `schema.sql` ([ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md)); ADR-285 §3's and ADR-342 §3's rule that the control panel and the jar link no class of each other, with the control panel's package renamed.

## Context

The jar's code lies in one flat package: `io.algernon.vespera` holds the eleven capability modules as packages directly beneath it, the control panel is `io.algernon.controlpanel` beside it, and the entry point is `io.algernon.EntryPoint` beside both. Three kinds of code lie at one level: the cascade (nine modules, `pipeline` their composition root), the plugins that draw and deliver a rendering (two modules, [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §1), and the window.

Spring Modulith 2.1.0 finds a module as a direct sub-package of the package of the application class. Moving the modules one level down, with nothing else changed, leaves it two modules, `engine` and `publish`, and every `allowedDependencies` declared on the eleven stops applying with nothing failing. Its other detection strategy, `explicitly-annotated`, takes every package carrying `@ApplicationModule` at any depth as a module, and names it by its trailing name under the application package, `engine.ledger`, unless the annotation's `id` gives it another. A module's name is what a declaration names a module by (`allowedDependencies = "ledger"`), what `ModuleBoundariesTest` lists, and what `StageModules`, the build's `implementation-versions.properties` and `schema.sql` name it by.

A stage's implementation version is the last commit touching its module's folder (ADR-058), so a move of the folders is a commit touching every one of them.

## Decision

### 1. The layout

The packages of the jar lie under `io.algernon.vespera` as:

| Package | Holds |
| --- | --- |
| `io.algernon.vespera` | `VesperaApplication` and `EntryPoint`, and nothing else |
| `io.algernon.vespera.engine` | the nine modules of the cascade: `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `deliverable`, `profile`, `pipeline` |
| `io.algernon.vespera.publish` | the two plugins' modules: `confluence`, `website` |
| `io.algernon.vespera.ui` | the control panel, which is no module |

Each module is the package directly beneath `engine` or `publish`, and the tests mirror it under the same names. `engine` names the cascade's folder and nothing else; it is no instrument in CONTEXT.md's sense and names none. No class, resource or test moves between modules, and none changes but in its package and its imports: the resource folders of `confluence`, `website` and `pipeline`, of the control panel and of the extraction tests' fixtures move with their packages.

### 2. How a module is found

The module rule is held as it was, for the same eleven names, by Spring Modulith's `explicitly-annotated` strategy:

- each module's `package-info.java` carries `@ApplicationModule(id = "<name>", allowedDependencies = ...)`, the `id` being the module's short name, so a module is `ledger` and not `engine.ledger` and every declaration, constant and test that names one is unchanged; and
- `src/test/resources/application.properties` sets `spring.modulith.detection-strategy=explicitly-annotated`, the one place the property is read, by `ApplicationModules` in the tests alone. Nothing the jar ships reads it: the jar carries `spring-modulith-api` for the annotation and no part of Modulith's runtime (ADR-141).

`ui` carries no `@ApplicationModule`, so it is no module and `ApplicationModules` does not see it; `engine` and `publish` carry none either, so each is a folder and not a module. A package under `engine` or `publish` that carries none would equally not be seen, so a test holds that every package directly beneath either is a module found, and that each of the eleven is found in the folder of its kind (Tests).

### 3. Implementation versions

The build's `implementation-versions.properties` keeps one line for each module and keeps the names it was keyed by. The script asks git for the last commit touching `src/main/java/io/algernon/vespera/engine/<module>` or `src/main/java/io/algernon/vespera/publish/<module>`, for every directory beneath those two, and no directory of `ui`, which holds no stage. `StageModules`, `RunMint` and the tests of run identity name the same strings as before.

### 4. The control panel and the entry point

The control panel's code is `io.algernon.vespera.ui`, its tests `src/test/java/io/algernon/vespera/ui` and its stylesheet `io/algernon/vespera/ui/control-panel.css`, which `ControlPanelWindow` asks for beside its own class. `EntryPoint` is `io.algernon.vespera.EntryPoint`, beside `VesperaApplication`; the Spring Boot plugin's `mainClass`, the jar's `Start-Class`, names it so.

ADR-285 §3 and ADR-342 §3 hold with the package renamed. The control panel names only types of `io.algernon.vespera.ui`, of `java.base`, of a `javafx.` package and of `tools.jackson`, and `ClassPathLine` also `java.logging`; so no other class of `io.algernon.vespera` is among them, `engine`, `publish`, the application class and the entry point included. No class of the jar names the control panel or a `javafx.` package. The jar's classes are every class of `io.algernon.vespera` but the control panel's and the entry point's, which names both and is neither. `ui` and `EntryPoint` carry no Spring stereotype, so the application class's component scan of `io.algernon.vespera` makes no bean of either.

### 5. The direction between the folders

A plugin names `engine`: it implements the seam `deliverable` makes public and is handed the records of `synthesis` and the ledger's move (ADR-333 §1). `engine` names `publish` in `pipeline` alone, the composition root that hands each plugin's beans to `deliverable`, which names no plugin. Each module's own allowed dependencies are unchanged and are `ModuleBoundariesTest`'s.

### 6. Run ids

Every run-minting stage's version moves once, with the commit that moves the folders: all eight of them, byte-level reduction, extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement and generation, because each names at least one of `corpus`, `extraction`, `similarity`, `embedding` and `synthesis` in `StageModules`, and the commit touches the folder of every one. A run minted before it is arrived at again by no invocation after it, whatever the profile says, and stays recorded, removing nothing from a run that does not name it upstream (ADR-156). Whether that costs the operator a pass over a corpus is asked of the operator when the change is merged.

No stage's version names `ledger`, `profile`, `deliverable`, `pipeline`, `confluence`, `website` or the control panel, so a later change to any of them moves none, as before; this record moves none beyond the one above.

## Alternatives refused

- **The default detection with `engine` and `publish` as the modules.** It replaces eleven modules and their rules with two, and every declared exception with nothing.
- **Nested modules, `engine` and `publish` each a module holding the eleven.** The eleven are then named `engine.ledger` and the like, in every declaration and test and the stage names, and the two parents' own rules apply beside theirs.
- **Detecting the folders with a strategy class of our own.** It would be code in the build to do what the annotation already says.
- **Naming each module by its folder, `engine.ledger`, `publish.website`.** The names are in `StageModules`, whose strings `RunIdentityGoldenTest` pins, and in `schema.sql`, whose owner lines ADR-209's test reads; a rename touches every one of those and every declaration and test that names a module.

## Consequences

- The module rule, ADR-209's, ADR-222's and ADR-226's rules and every test of them bind the same eleven names as before; a forbidden dependency between two modules still fails `ModuleBoundariesTest`.
- A new module is a package directly beneath `engine` or `publish` carrying `@ApplicationModule(id = ...)`, and one that does not fails a test.
- A reader of the tree sees the cascade, what delivers its output and the window in three folders.
- Every run-minting stage's version moves once (§6).
- A settled record that names a package by its old name, `io.algernon.controlpanel`, `io.algernon.EntryPoint` or `io.algernon.vespera.<module>`, names it by where it lay when it was written; `docs/check-claims.mjs` reads a test a record's Tests section names at the package the record names now.

## What this does not decide

- Any change of what a module holds or may depend on.
- Whether a record of the packages' names is written into `package-info.java` files for `engine`, `publish` or `ui`: none exists, and none is needed for a folder.

## Tests

| Method | What it holds |
| --- | --- |
| `TheJarsCodeLiesUnderEngineAndPublishAndUiTest.theApplicationPackageHoldsTheThreeFoldersAndItsTwoClasses` | the application package holds the folders `engine`, `publish` and `ui` and the two classes `VesperaApplication` and `EntryPoint`, and nothing else (§1) |
| `TheJarsCodeLiesUnderEngineAndPublishAndUiTest.everyModuleLiesInTheFolderOfItsKindAndEveryFolderIsAModule` | each of the eleven is found under its short name in the folder of its kind, and every package directly beneath `engine` or `publish` is a module found (§1, §2) |
| `TheJarsCodeLiesUnderEngineAndPublishAndUiTest.aPluginNamesTheEngineAndOnlyThePipelineNamesAPlugin` | no class of `engine` but `pipeline`'s names a class of `publish`, `pipeline` names both plugins' modules and each plugin names `deliverable` (§5) |
| `ModuleBoundariesTest.moduleDependenciesAreAllowed` | no module depends on one it has not declared, under the new detection (§2) |
| `ModuleBoundariesTest.everyModuleDeclaresWhatItMayDependOn` | no module leaves `allowedDependencies` open (§2) |
| `ModuleBoundariesTest.everyCapabilityModuleDeclaresLedgerAlone` | a capability module declares `ledger` or the exception recorded for it (§2) |
| `ModuleBoundariesTest.everyModuleIsOneOfTheRecordedModules` | the modules found are the eleven (§2) |
| `ModuleBoundariesTest.aPluginsModuleDeclaresTheSeamAndNothingElseHorizontal` | a plugin declares `ledger`, `deliverable` and `synthesis`, and `deliverable` names no plugin (§2, §5) |
| `TheBuildRecordsNoVersionAShallowCloneCannotGiveTest.aModuleUnderPublishIsRecordedAndTheControlPanelsFolderIsNot` | a module of `publish` is recorded by its folder's name beside one of `engine`, and `ui` is recorded for none (§3) |
| `TheBuildRecordsNoVersionAShallowCloneCannotGiveTest.aFullCloneRecordsEachModulesLastCommit` | the script keeps asking git for the last commit of each module's folder, for the modules under `engine` (§3) |
| `engine.pipeline.RunIdentityGoldenTest` | each stage's name and the modules its version spans are the strings they were (§3, §6) |
| `ui.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson` | the control panel names only types of `io.algernon.vespera.ui`, of `java.base`, of JavaFX and of Jackson (§4) |
| `ui.TheControlPanelLinksNoClassOfTheJarTest.onlyTheClassPathLineNamesTheJdksLoggingModule` | one class of `ui` names `java.logging` too (§4) |
| `ui.TheControlPanelLinksNoClassOfTheJarTest.noClassOfTheJarNamesTheControlPanel` | no class of the jar names a class of `ui` (§4) |
| `ui.TheControlPanelLinksNoClassOfTheJarTest.noClassOfTheJarNamesJavaFx` | no class of the jar names JavaFX (§4) |
| `ui.TheStylesheetLiesBesideTheControlPanelsClassesTest.theWindowsClassFindsTheStylesheetBesideItself` | the window's class finds `control-panel.css` in `io/algernon/vespera/ui` (§4) |
| `EntryPointTest.theEntryPointIsNoJavaFxApplicationAndNamesBothItStarts` | the entry point lies in `io.algernon.vespera` and names the control panel's main class and the command line's (§4) |
| `PackagedJarIT.theJarCarriesTheControlPanelAndJavaFxAndStartsAtTheEntryPoint` | the packaged jar's `Start-Class` is `io.algernon.vespera.EntryPoint` and its classes include the control panel's (§4) |
