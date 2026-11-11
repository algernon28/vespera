# ADR-359 — The publish reads its Confluence values from the profile, and the control panel passes the token to its child alone

> **Partly amended — see [ADR-372](0372-a-publish-the-control-panel-started-refuses-a-parent-named-twice-or-a-wrong-address-naming-the-profile-keys.md).** Of §4: started by the control panel, a parent named twice is refused naming `confluenceFolderId` and `confluenceFolderTitle`, and an address that is not a Cloud site's naming `confluenceBaseUrl`, each line saying to put it right in the control panel's Profile tab and publish again, and naming no `VESPERA_CONFLUENCE_*` variable. On the command line both lines are unchanged. The rest stands.

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Decided**: by the operator on 2026-11-06: that the site's address, the space's key, the parent by its id or its title, and the account's email are values of the profile, which the control panel's Profile tab edits as it edits every other value and which `vespera publish` reads (§1, §2); that the API token is never written down: the control panel asks for it in a masked field when a publish to Confluence is started, and passes it to the `publish` child as `VESPERA_CONFLUENCE_TOKEN` in that child's environment alone, never in the profile, the control panel's state or any file, and never in a log (§3); and that `confluence-env.sh` and `confluence-env.ps1` stay for a publish typed on the command line, while a publish the control panel starts writes neither, its refusal for a missing value naming the control panel's field instead (§4); that a publish the control panel starts inherits no `VESPERA_CONFLUENCE_*` variable from the window, the token typed being the one such variable it is given, so that the Profile tab is the one source of the four plain values there (§3); and that the deliverable's index lists none of the five keys, which say where the deliverable is sent and not how it was made (§7). Under the operator's delegation of the same date, Claude decided: that on the command line a `VESPERA_CONFLUENCE_*` variable or property the shell sets comes before the profile's value, and the token comes from the environment alone (§2); that a command-line publish whose profile names none of the values behaves as before (§2, §4); the names of the five keys (§1); and that the masked field is no authentication (§6). What building it needs beyond those is decided here: the keys' place and kind, the seam between `confluence` and `pipeline`, how the child knows the control panel started it, the parent read as one value, a profile that does not load, and what Publish does with no token typed (§1 to §5).
- **Supersedes**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §3's table, in the rows of the site, the space, the parent and the email alone: each is read from the shell, as that table has it, or, where the shell sets none, from the profile, and *the profile* leaves their column *Never*; and its refused alternative *The site and parent as profile keys*, which this record takes (§1, §2). Its token's row stands, its column *Never* gaining the control panel's state and every file the control panel writes (§3).
- **Amends**: ADR-308 §6, whose control panel "neither asks for nor holds any of them": it reads the four plain values as profile values, asks for the token as a publish to Confluence starts and passes it to that child alone (§3). It carries a note at its head.
- **Amends**: [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) §1, whose email is read from `VESPERA_CONFLUENCE_EMAIL` "alone": from it, or, where it is not set, from the profile's `confluenceEmail` (§2). Its names, its §2 and its §3 stand. It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §1, whose child inherits the window's environment "with ADR-292 §4's added": a publish's child is also given `VESPERA_STARTED_BY` and, where Confluence is ticked, the token typed (§3); and §4, whose `ChildCommand` gains `withConfluenceToken`, and whose Tests row `ChildCommandTest.publishIsTheJarsPublishCommandAndAddsNoConfluenceValue` holds of a publish given no token (§3, Tests). It carries a note at its head.
- **Amends**: [ADR-340](0340-the-control-panel-is-a-home-page-under-a-top-bar-with-every-value-in-one-drawer-and-a-folder-the-profile-names-is-not-walked.md) §6, as ADR-345 §1 keeps it, whose child inherits the control panel's environment "less eight variables": a publish's child inherits it less those eight and less every variable whose name, in any case, begins with `VESPERA_CONFLUENCE_` (§3). It carries a note at its head.
- **Amends**: [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) §1, whose index states "one line for every component of `Profile`": it states one for every component but the five keys of §1, read off the record by `Profile.keysTheIndexStates` (§7); and its §4, whose `DeliverableInvocationTest` claims the index's lines are exactly those keys. It carries a note at its head.
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1 and §2: the launch values stay seven, the token is none of them, and the launch values' file never holds it. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel names no class of the jar and no type of `java.net.http`, and opens no connection to Confluence. ADR-308 §2: a publish opens connections to the site's one `https` host. [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §6: `--to website` reads none of the five and misses none. ADR-340 §6's eight: every child is still given the window's environment less the eight variables naming the working directory. [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §2: a value the Profile tab saves is saved with its note filled in. ADR-186 §2 and §3: each key the index states is stated as written, read off the record in `profile`. [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and ADR-222: no run id moves (§8).

## Context

**Where a publish's values come from today.** `ConfluenceSite` reads the token and the email from the environment alone, by `VESPERA_CONFLUENCE_TOKEN` and `VESPERA_CONFLUENCE_EMAIL`, and `Publisher` reads the site's address, the space's key and the parent from the properties `vespera.confluence.base-url`, `vespera.confluence.space-key`, `vespera.confluence.folder-id` and `vespera.confluence.folder-title`, which Spring sets from the variables of the same names (ADR-308 §3, ADR-318 §1). A publish missing any of the five refuses before anything is sent, names the variable it lacks, and writes the two templates into the working directory where they are not there already (ADR-308 §3).

**What the control panel does with a publish.** Publish starts `publish --to <destinations> --db-dir=<working directory>` as a child, whose environment is the window's less eight variables, with `CI=1` and the launch values' five added (ADR-340 §6, ADR-345 §1, §4). The window has no field for any of the five Confluence values, so a publish started from it reads only what the shell that started the window exported, and a window started from a desktop shortcut exports none: the publish refuses, and writes templates meant for a shell the operator never opened.

**What the profile is to the jar.** `Profile` is one record whose components are the keys of `profile.yaml` (ADR-061); the control panel's `ProfileKey` lists the same keys, and the Profile tab edits each (ADR-330, ADR-344). Only `pipeline` names `profile` (ADR-040). Since ADR-274 §5 no stage's version names `profile`, and a value of it reaches a run only through `config_consumed`, where a stage reads it. ADR-308 refused the site and the parent as profile keys because the profile describes the corpus, and because a key would move the generation run; no stage reads any of these values, so the second reason does not hold.

## Decision

### 1. Five keys of the profile

`Profile` gains five components, after `foldersNotWalked` and in this order, each a `TextValue`:

| Key | What it holds | Set in the shell by |
| --- | --- | --- |
| `confluenceBaseUrl` | the site's address | `vespera.confluence.base-url`, `VESPERA_CONFLUENCE_BASE_URL` |
| `confluenceSpaceKey` | the space's key | `vespera.confluence.space-key`, `VESPERA_CONFLUENCE_SPACE_KEY` |
| `confluenceFolderId` | the parent, by its id | `vespera.confluence.folder-id`, `VESPERA_CONFLUENCE_FOLDER_ID` |
| `confluenceFolderTitle` | the parent, by its title | `vespera.confluence.folder-title`, `VESPERA_CONFLUENCE_FOLDER_TITLE` |
| `confluenceEmail` | the account's email | `VESPERA_CONFLUENCE_EMAIL` |

- **Named as the profile names its keys**: a camel-cased name beginning with what it is about, as `deliverableLanguage` and `clusterPageByteCeiling` are, the rest of each the property's or the variable's name read in camel case, so that a key and the variable that comes before it are found by the same words.
- **Each ships unset**, enters no run's identity and is no gate: no stage reads one. `ProfileKey` gains five constants of the kind `TEXT` with no earliest run, so a change of one costs no run, closes no approval, refuses no labels and draws no sample, as `clusterPageByteCeiling`'s does (ADR-303 §9). None has a value beneath the profile, so `effective-values.properties` names none (ADR-330 §1).
- **No key holds the token**, and no component of `Profile` names a token, a secret or a password.
- **The parent is one value**, named by its id or by its title, one and not both, as ADR-308 §3 has it: a profile naming both is a parent named twice, and the publish refuses it as it refuses a shell naming both.

### 2. What a publish reads, and in which order

**On the command line, a value the shell sets comes before the profile's.** A publish the control panel starts is given no such value (§3), so there the profile is the one source of the four plain values. For the site, the space and the parent, the shell's value is the property, set by its variable or on the command line; for the email it is `VESPERA_CONFLUENCE_EMAIL`. A value set and not blank is the shell's; a blank one is none, as ADR-308 §3 reads it. Where the shell sets none, the publish reads the profile's key, a blank one being none.

- **The parent is taken whole from one side.** Where the shell names the parent, by its id or by its title, the profile's parent is not read: a shell naming a title beside a profile naming an id is a parent named by its title. Where the shell names neither, the profile's id or title is read.
- **The token is read from `VESPERA_CONFLUENCE_TOKEN` alone**, as ADR-308 §3 has it, whoever set it: the operator's shell, or the control panel for the one child it starts (§3).
- **The profile read is `profile.yaml` in the working directory the publish holds the lock on.** Where the file is not there, or does not load, no value is read from it, and the publish reads the shell alone, as it did before this record.
- **A publish whose profile names none of the five behaves as before**: the same values read, the same refusals, the same lines and the same templates.

### 3. The token, as the control panel starts a publish

- **Publish asks for the token where Confluence's box is ticked**, each time it is pressed, in a masked field: what is typed is not shown, in the field or anywhere in the window. Where the website's box alone is ticked, nothing is asked.
- **Cancelled, or with nothing typed but blanks, Publish starts nothing**, and `outcome` says so in one line.
- **A publish's child inherits no `VESPERA_CONFLUENCE_*` variable.** Its environment is the window's less ADR-340 §6's eight and less every variable whose name, compared without regard to case, begins with `VESPERA_CONFLUENCE_`, so that neither a value the shell that started the window exported nor a token it holds reaches the child; `environment()` is then put over it. Run's and Label's children are unchanged.
- **What is typed is passed to that child alone**, as `VESPERA_CONFLUENCE_TOKEN` in its environment, the one `VESPERA_CONFLUENCE_*` variable the child is given. It is not on the child's command line, in `command-line-publish`, in the launch values, in `profile.yaml`, in the control panel's state once the child is started, or in any file the control panel writes, and no line the control panel writes holds it. The next Publish asks again.
- **Every child Publish starts is given `VESPERA_STARTED_BY=control-panel`** in its environment, by which the jar knows a publish the control panel started (§4). Run's and Label's children are not given it.
- **The four plain values are the profile's**, which the Profile tab shows and saves as it does every key (ADR-330, ADR-343), each with its name in plain words and a line saying what it is (ADR-340 §7, which ADR-345 §1 keeps for the Profile tab).

### 4. The refusals

- **On the command line, a publish missing a value refuses as before**: it names each variable missing, never a value, says to set the site, the space and the parent in a template and run it in the shell, and writes the templates where they are not there already (ADR-308 §3, ADR-318 §3). A value the profile names is not missing.
- **A publish the control panel started**, its environment holding `VESPERA_STARTED_BY` set to `control-panel`, writes no template, and its refusal for a missing value names each value missing by its key as `profile.yaml` spells it and says to set it in the control panel's Profile tab and publish again; a missing token is said as the API token Publish asks for. It names no `VESPERA_CONFLUENCE_*` variable and no template. Its other refusals, a parent named twice and an address that is not a Cloud site's, are said as on the command line.

### 5. Where each part lives

- **`confluence`** holds `ConfluenceValues`, a public record of the five plain values, with `NONE`, each `null`, and its nested interface `ConfluenceValues.InTheProfile`, which gives the values a working directory's profile names. `ConfluenceSite.of` gains the profile's values as an argument, and `Publisher` a collaborator of that interface, asked for the working directory it publishes from. `confluence` still names no class of `profile`, and its declaration is unchanged.
- **`pipeline`**, the one module naming `profile`, holds `ConfluenceValuesOfTheProfile`, the bean implementing that interface by `ProfileStore`. It holds no rule: it reads five values and hands them on.
- **`profile`** holds which keys the deliverable's index states, `Profile.keysTheIndexStates`, beside `keysAsWritten`, whose rule ADR-226 moved there (§7).
- **`io.algernon.controlpanel`** holds the masked field, behind `Dialogs` so that a test answers it, `ChildCommand.withConfluenceToken`, and the withholding of the inherited `VESPERA_CONFLUENCE_*` variables in `ChildCommand.environmentFrom`; it names `VESPERA_STARTED_BY`, `VESPERA_CONFLUENCE_` and `VESPERA_CONFLUENCE_TOKEN` as text, linking no class of the jar (ADR-285 §3).

### 6. The masked field is no authentication

Nothing here authenticates anyone. The control panel admits no operator by the field, compares what is typed with nothing, checks it against nothing and keeps it nowhere: it carries the text into the environment of the one child it starts, as a shell carries a variable into the command it runs. The account is checked by the Confluence site alone, as ADR-308 §3 has it, and the control panel and the jar hold no login, no account and no password of their own.

### 7. The deliverable's index lists none of the five

The index's provenance block, which ADR-186 §1 has state every key of the profile, states every key but the five of §1. They say where the deliverable is sent, not how it was made, and a reader of the tree needs none of them to read it; the email is the account's, and the tree is carried where the operator carries it.

- **`Profile.keysTheIndexStates()`** gives `keysAsWritten()` less the five, in the record's order, each value as written, so that a key the record gains later is on the index unless it is one of these. `GenerationTasklet.profileValues` builds the index's values from it in place of `keysAsWritten()`.
- **`keysAsWritten()` is unchanged**: every key, the five among them, for every reader that is not the index.
- **No line of the index names one of the five, and none holds a value of one.** Neither the Confluence rendering nor the website rendering states the profile, so nothing else of the tree is changed.

### 8. Run ids

No stage reads any of the five keys, so none enters a run's `config_consumed`, and no stage's version names `profile`, `confluence`, `pipeline` or the control panel (`StageModules`, ADR-274 §5), so no run id moves. The index is drawing, which keys no run (ADR-274 §5): the next invocation that reaches the tree draws it without the five lines.

## Known limits

- **An operator who set the values in the shell, and starts the window from it, publishes from the window with none of them** until they are set in the Profile tab: the child inherits no `VESPERA_CONFLUENCE_*` variable, and its refusal names the keys to set (§3, §4).
- **A `profile.yaml` that does not load gives the publish no value**, so a publish the control panel starts over one refuses naming its fields, set as they may be in the file (§2).
- **A reader of the tree cannot tell from its index where it was published**: the five are not on it (§7).

## Alternatives refused

- **The token in the profile, the launch values or a file of the control panel's**: a file holding a token is a file that leaks it (ADR-308's refused alternative, which stands).
- **The control panel remembering the token between publishes**: it would hold the token in a process with no end in view, where asking each time holds it for one start.
- **A refusal of the command line naming the control panel's fields too**: two places to set a value in one line, where the command line's operator set it in the shell.
- **The parent read key by key, the shell's title beside the profile's id**: every such mix would be a parent named twice, refused, where the operator named one in each place.
- **A publish's child inheriting the window's `VESPERA_CONFLUENCE_*` variables**, the operator's refusal: a value the shell exported would come before the one the Profile tab shows, unseen, and a token the shell held would reach the child beside the one typed (§3).
- **The index listing the five as it lists every key**, the operator's refusal: they say where the tree was sent, not how it was made, and the email would travel with every copy of the tree (§7).
- **The five left out of `keysAsWritten` itself**: every reader of it would lose them, the tests that read a profile back through it among them, for a rule that is the index's alone.
- **A publish refusing over a `profile.yaml` that does not load**: a command-line publish naming every value in the shell would refuse over a file it did not need.
- **`confluence` naming `profile`**: a fourth exception to the module rule, for five values `pipeline` can hand it.

## Consequences

- **`profile.yaml` gains five keys**, written unset by the census as every key the record gains is (ADR-062), and the deliverable's index gains no line for them (§7).
- **The README's table of values** gains a row for each key, and its section on the publish says that the four plain values are read from the profile where the shell sets none, and that the control panel asks for the token.
- **`CONTEXT.md`'s Profile and Control panel entries** say what the profile holds of a publish and what the control panel passes.
- **ADR-186, ADR-308, ADR-318, ADR-340 and ADR-345 carry a note** at their heads pointing here.

## Tests

| Method | What it holds |
| --- | --- |
| `profile.TheConfluenceValuesAreProfileKeysTest.theLastFiveKeysAreTheConfluenceValuesEachText` | the record's last five components are the five keys of §1, in its order, each a `TextValue` (§1) |
| `profile.TheConfluenceValuesAreProfileKeysTest.noKeyHoldsAToken` | no component names a token, a secret or a password (§1, §3) |
| `profile.TheConfluenceValuesAreProfileKeysTest.aFileAnsweringThemLoadsEachOnItsKey` | a `profile.yaml` answering the five loads each on its key (§1) |
| `pipeline.ThePublishReadsTheProfilesConfluenceValuesTest.theFiveKeysAreReadOffTheWorkingDirectorysProfile` | `ConfluenceValuesOfTheProfile` gives the five as the file names them, an unset one `null` (§2, §5) |
| `pipeline.ThePublishReadsTheProfilesConfluenceValuesTest.noProfileAProfileNamingNoneAndOneThatDoesNotLoadGiveNone` | no file, a file naming none of the five and one that does not load each give `ConfluenceValues.NONE` (§2) |
| `confluence.ThePublishReadsItsPlainValuesFromTheProfileTest.theProfileAloneNamesTheSiteTheSpaceTheParentAndTheEmail` | the shell setting the token alone, the site, the space, the parent and the email are the profile's (§2) |
| `confluence.ThePublishReadsItsPlainValuesFromTheProfileTest.aValueTheShellSetsComesBeforeTheProfiles` | the shell's site, space, parent title and email come before the profile's, the profile's id not read beside the shell's title (§2) |
| `confluence.ThePublishReadsItsPlainValuesFromTheProfileTest.aBlankValueOfTheShellIsNoneAndTheProfilesIsRead` | blank values of the shell give way to the profile's (§2) |
| `confluence.ThePublishReadsItsPlainValuesFromTheProfileTest.theTokenComesFromTheEnvironmentAlone` | `ConfluenceValues` holds the five and no token, and a profile naming every plain value with no token in the environment is refused naming the token's variable alone (§1, §2) |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.fromTheCommandLineAMissingValueIsRefusedAsBeforeAndTheTemplatesAreWritten` | with the profile naming nothing, the refusal's whole line is the one ADR-308 §3 and ADR-318 §3 give, and both templates are written (§2, §4) |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.onTheCommandLineAValueTheProfileNamesIsNotMissing` | a space key the profile names is not named missing, the token is, and the templates are written (§2, §4) |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.fromTheControlPanelTheRefusalNamesTheProfileTabsKeysAndWritesNoTemplate` | started by the control panel, the refusal names `confluenceSpaceKey` and `confluenceEmail` and the Profile tab, no variable and no template, a missing token is said as the one Publish asks for, and the working directory holds no file (§4) |
| `controlpanel.ChildCommandTest.aPublishToConfluenceCarriesTheTokenTypedInItsEnvironmentAlone` | `withConfluenceToken` puts the token in a Confluence publish's environment, over an inherited one, and in no argument of its command line or its shown line; a run, a label and a publish to the website alone are unchanged by it (§3) |
| `controlpanel.ChildCommandTest.aPublishsChildInheritsNoConfluenceVariableButTheTokenTyped` | over a window's environment holding `VESPERA_CONFLUENCE_SPACE_KEY=OLDSPACE`, `VESPERA_CONFLUENCE_TOKEN=stale` and other `VESPERA_CONFLUENCE_*` variables in either case, a publish's child holds none of them, the token typed alone where one is typed, and every other variable inherited; its process builder's the same (§3) |
| `profile.TheConfluenceValuesAreProfileKeysTest.theIndexStatesEveryKeyButTheFive` | `Profile.keysTheIndexStates()` of a profile setting all five is `keysAsWritten()` less the five, in order, and holds none of their values (§7) |
| `pipeline.DeliverableInvocationTest.theIndexNamesNoneOfTheConfluenceValues` | a run whose profile sets all five draws an index naming none of the five keys and holding none of their values (§7) |
| `pipeline.DeliverableInvocationTest.opensTheIndexWithWhatProducedTheTree` | as ADR-186 has it, the index's lines exactly the keys the record declares less the five (§7) |
| `controlpanel.ChildCommandTest.aPublishsChildIsToldTheControlPanelStartedIt` | a publish's environment holds `VESPERA_STARTED_BY=control-panel`, with or without a token (§3) |
| `controlpanel.ChildCommandTest.theChildInheritsNoVariableNamingTheWorkingDirectory` | as ADR-345 has it, a publish's environment also holding `VESPERA_STARTED_BY` (§3) |
| `controlpanel.ChildCommandTest.publishIsTheJarsPublishCommandAndAddsNoConfluenceValue` | as ADR-345 has it, of a publish given no token (§3) |
| `controlpanel.PublishToConfluenceAsksForTheTokenTest.theTokenTypedReachesTheChildAndIsWrittenNowhere` | Publish with Confluence ticked asks once in the masked field; the child it starts sees the token typed and `VESPERA_STARTED_BY`, and no argument holding it; and after it ends no file under the test's folder, the launch values and `profile.yaml` among them, no text of the window and nothing the state holds names the token (§3) |
| `controlpanel.PublishToConfluenceAsksForTheTokenTest.cancelledOrBlankPublishStartsNothing` | the field cancelled, then answered with blanks: no child started, one line in `outcome` each time (§3) |
| `controlpanel.PublishToConfluenceAsksForTheTokenTest.aPublishToTheWebsiteAloneAsksForNoToken` | with the website's box alone ticked, Publish asks nothing (§3) |
| `controlpanel.ProfileKeyTest.eachKeyHasItsTypeAndEarliestRun`, `aChangeCostsItsRunAndEveryLaterOne` | the five keys last, `TEXT`, entering no run and costing nothing (§1) |
| `profile.ProfileTest.theRecordDeclaresASingleConstructor`, `profile.ProfileStoreTest.everyAnswerLandsOnTheKeyThatCarriedIt` | twenty-six keys, every one answered on its key (§1) |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `ConfluenceValuesOfTheProfile` holds no rule (§5) |
| `confluence.PublishReadsItsCredentialFromTheEnvironmentIT.noPropertyNamesTheTokenOrTheEmail` | the public constructor reads the same four properties, and takes the profile's values beside them (§5) |

## What the implementation owes

Beside turning the tests above green: **in `profile`**, the five components of §1 after `foldersNotWalked`, each `with…` method carrying them, and `keysTheIndexStates()` (§7); **in `pipeline`**, `GenerationTasklet.profileValues` reading `keysTheIndexStates()`; **in `confluence`**, `ConfluenceValues` and its `InTheProfile`, `ConfluenceSite.of(environment, baseUrl, spaceKey, parentId, parentTitle, profile)` with the five-argument form reading `ConfluenceValues.NONE`, §4's lines, and `Publisher`'s public constructor taking an `InTheProfile` after its four properties, its package-private test constructor taking one last, a constructor of the four strings alone reading none, and `publish` writing no template for a refusal the control panel's child is given; **in `pipeline`**, `ConfluenceValuesOfTheProfile`; **in `io.algernon.controlpanel`**, `ProfileKey`'s five constants, `Dialogs.masked`, `ChildCommand.withConfluenceToken`, `VESPERA_STARTED_BY` on every publish and the inherited `VESPERA_CONFLUENCE_*` variables withheld from it, the token held only for the one start; **documents**, the README's five rows and its publish section.

## What this does not decide

- **Whether the Profile tab says that the window inherited a `VESPERA_CONFLUENCE_*` variable** it withholds from its child.
- **A scoped token through `api.atlassian.com`**, which ADR-308 §3 leaves undecided.
