# ADR-398 — The Confluence root page's title is a profile value, `Vespera_{language}` by default, and a changed title retitles the tree in place

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, on 2026-11-15: that the root page's title is a value of the profile, edited in the control panel's Profile window beside the other Confluence values, and read on the command line from `VESPERA_CONFLUENCE_ROOT_TITLE` as the others are read from theirs (§1, §2); that each language's tree carries the language's code in its root page's title, `{language}` standing for the code where the title holds it and `_<code>` appended where it does not (§3); that the default is `Vespera_{language}`, which gives the titles a publish sends today (§3); and that a changed title retitles the tree an earlier publish made in place and makes no new tree (§6). Under the operator's delegation: the key's and the variable's names (§1, §2), a blank value being the default, the title folded as a title's text is and refused past Confluence's limit rather than cut (§3, §4), where the title is applied (§5), the Profile window's words (§7), the templates (§8), and the tests.
- **Amends**: [ADR-378](0378-each-deliverable-language-is-a-tree-of-its-own-its-titles-carrying-its-code-and-an-earlier-tree-taken-by-its-heading.md) §1, whose root page is titled `Vespera_<code>`: the page list still names it so, and a publish sends it under the title §3 gives, `Vespera_<code>` where nothing names another (§3, §5); and §7, whose lines name the tree `Vespera_<code>`: they name it by that title (§5). Its §5's root page that lost its label is looked up by that title too (§5). It carries a note at its head.
- **Amends**: [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) §1, whose five keys are the last components of `Profile`: a sixth, `confluenceRootTitle`, follows `confluenceEmail` (§1); §2, which orders five values: the root page's title is read in the same order, the shell's before the profile's (§2); §5, whose `ConfluenceValues` holds five values: it holds six, `rootTitle` the last (§1); and §7, whose index states none of the five: it states none of the six (§1). It carries a note at its head.
- **Keeps**: [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md), the one tree under the parent, its pages matched by their key and updated in place, and its §7, every page whose title is not that of the page standing for its key looked up by its title before any write; ADR-378 §2, a page found by its label `vespera-<code>` and its key `<code>:root`, §3, the root page standing directly under the parent, and §4, the page list's format 3; [ADR-310](0310-a-confluence-pages-title-is-its-text-followed-by-its-place-and-its-generation-runs-marker-cut-to-255-characters-with-the-marker-kept-whole.md) §4's limit of 255 UTF-16 code units, and ADR-378 §1's root title never cut; [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md)'s names of the variables it names; ADR-359 §3, the control panel's publish inheriting no `VESPERA_CONFLUENCE_*` variable, and §4's refusals of a missing value; [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, `effective-values.properties` naming no Confluence value, and §2, a value the jar ships kept out of the field; [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §2, a value the Profile window saves saved with its provenance filled in; [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4, a key's words in `profile-texts.json`; [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md), the Profile window; [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no run id moving (§9).
- **Settles**: [#837](https://github.com/algernon28/vespera/issues/837).

## Context

**The root page's title is fixed.** `ConfluenceTitles.root` gives `Vespera_` and the deliverable language's code, never cut (ADR-378 §1). Stage 6b writes it as the root page's title in the page list, `confluence/pages.json` (ADR-378 §4), and a publish creates the root page under it, compares it with the title of the page standing for the key `<code>:root`, looks it up where the root page lost its label (ADR-378 §5), and names the tree by it in its first line and its closing line (ADR-378 §7). The site, the space and the parent are the operator's to name, in the profile or in the shell (ADR-359); the title is not.

**How a publish finds the tree.** By its label and its key: every page labelled `vespera-<code>` whose property holds a key `<code>:<place>`, the root page keyed `<code>:root` (ADR-378 §2, `Publisher.Run.ofTheTree`). Only where no labelled page is keyed `<code>:root` is the root page looked up by its title (ADR-378 §5). A page standing for its key whose title differs from the list's is looked up by the list's title and, where no other page of the space holds it, sent with that title, its version plus one (ADR-328 §7, `Publisher.Run.titled` and `sendBody`): that is how a cluster's page takes a new label's title in place.

**No page links to the root page by its title.** The root page links down to the pages under it, by their titles; no body of the rendering names the root page's (`ConfluencePages.link`'s callers). And the root page's body opens with the deliverable's heading, never with its title (ADR-378 §5).

**The values a publish reads.** The site's address, the space's key and the parent are properties that Spring sets from `VESPERA_CONFLUENCE_BASE_URL`, `VESPERA_CONFLUENCE_SPACE_KEY`, `VESPERA_CONFLUENCE_FOLDER_ID` and `VESPERA_CONFLUENCE_FOLDER_TITLE`; the email and the token are read from the environment alone, by `VESPERA_CONFLUENCE_EMAIL` and `VESPERA_CONFLUENCE_TOKEN` (ADR-308 §3, ADR-318). The first four and the email are also keys of the profile, the shell's value coming first (ADR-359 §1, §2), handed from `pipeline` to `confluence` as `ConfluenceValues` (ADR-359 §5). A publish the control panel starts inherits no `VESPERA_CONFLUENCE_*` variable, so the profile is its one source (ADR-359 §3).

## Decision

### 1. A key of the profile

`Profile` gains a sixth Confluence component, **`confluenceRootTitle`**, a `TextValue`, after `confluenceEmail` and so the last of the record. It is named as ADR-359 §1 names the other five, the variable's name read in camel case after `confluence`.

- **It ships unset**, enters no run's identity and is no gate: no stage reads it (§5). It has no `Measurement` pointer.
- **The deliverable's index does not state it**: `Profile.keysTheIndexStates()` leaves out the six Confluence keys, as ADR-359 §7 left out five.
- **`ConfluenceValues` gains a sixth component, `rootTitle`**, the last, `null` where the profile names none or names it blank; `NONE` is six `null`s. `ConfluenceValuesOfTheProfile` reads it as it reads the other five, and still holds no rule.
- **Its provenance is the operator's**, written beside its value in `profile.yaml` as every key's is (ADR-061, ADR-343 §2); a load and a save keep both as written.

### 2. A variable of the shell

On the command line the title is read from **`VESPERA_CONFLUENCE_ROOT_TITLE`**, from the environment alone, as the email is (ADR-318 §1). It is no Spring property, so the application's `Publisher` keeps the four properties it reads.

- **The shell's value comes first**, as ADR-359 §2 orders the others: set and not blank, it is the title stated; else the profile's `confluenceRootTitle`, not blank; else none.
- **A publish the control panel starts** inherits no `VESPERA_CONFLUENCE_*` variable (ADR-359 §3), so there the profile's key is the one source.

### 3. The title

The title a publish sends the root page under is worked out from the title stated and the language the page list names (ADR-378 §4):

1. **The title stated is folded** as ADR-310 §4 folds a title's text: each control character becomes a space, each run of white space one space, and the whole is stripped.
2. **Folded to nothing, or stated nowhere, the title stated is `Vespera_{language}`.**
3. **Where it holds `{language}`**, spelt so, every `{language}` in it is replaced by the language's code. Any other brace is taken as written.
4. **Where it does not**, `_` and the language's code are appended.

So the default gives `Vespera_en` and `Vespera_it`, the titles ADR-378 §1 gave, and a tree published before keeps its title. Each language's root page's title always carries its code, so the English and the Italian trees under one parent never share a root page's title (ADR-378 §3, §6).

| Stated | English | Italian |
| --- | --- | --- |
| nothing, or blank | `Vespera_en` | `Vespera_it` |
| `Hand-overs {language}` | `Hand-overs en` | `Hand-overs it` |
| `{language} notes, {language}` | `en notes, en` | `it notes, it` |
| `Hand-overs` | `Hand-overs_en` | `Hand-overs_it` |
| `  Hand-overs` and a tab and `for the team  ` | `Hand-overs for the team_en` | `Hand-overs for the team_it` |

`ConfluenceTitles` holds this rule, beside the other titles' (ADR-378 §1).

### 4. A title past Confluence's limit is refused, never cut

**The root page's title is never cut** (ADR-378 §1). Where the title §3 gives is longer than 255 UTF-16 code units, Confluence's own limit (ADR-310 §4), the publish sends nothing: once the values, the page list and every file it names are checked (ADR-378 §7's first row), and before any request, it ends unpublished with one line naming where the title was stated and how long it came to. It writes no template: no value is missing.

| Started | Stated by | The line |
| --- | --- | --- |
| on the command line | the shell | `vespera publish sent nothing: VESPERA_CONFLUENCE_ROOT_TITLE gives the root page a title of <n> characters, and Confluence takes at most 255. Shorten it, and publish again.` |
| on the command line | the profile | `vespera publish sent nothing: confluenceRootTitle gives the root page a title of <n> characters, and Confluence takes at most 255. Shorten it, and publish again.` |
| by the control panel | the profile | `vespera publish sent nothing: confluenceRootTitle gives the root page a title of <n> characters, and Confluence takes at most 255. Shorten it in the control panel's Profile window, and publish again.` |

`<n>` is the title's length in UTF-16 code units, the language's code counted in.

### 5. Where the title is applied

**The drawing is unchanged.** Stage 6b reads no profile value for the title: the page list's root page is still titled `Vespera_<code>`, in format 3 (ADR-378 §1, §4).

**A publish sends the root page under the title §3 gives**, in place of the one the page list names, wherever the root page's title is used: it creates the root page under it; compares it with the title of the page standing for `<code>:root`, and looks it up by it where they differ (ADR-328 §7); looks the root page up by it where no labelled page is keyed `<code>:root` (ADR-378 §5); and names the tree by it in ADR-378 §7's lines, *"…, tree <title>: …"*, among them *"the tree <its root page's title> an earlier publish made is taken as the tree <title>: …"*. Every other page is sent under the title the page list names, as before. No body names the root page's title, so no body changes with it.

### 6. A changed title retitles the tree in place, and makes no new tree

The tree is found by its label and its key, not by its title (ADR-378 §2). So where the title §3 gives is not the one the root page holds, the publish finds the root page as it always does, keyed `<code>:root`; looks the new title up, by reads alone (ADR-328 §7); and, where no other page of the space holds it, sends the root page's body with the new title and its version plus one, to the same page. **No page is created**: every page keeps its id, its address, its label and its key; the pages under the root page stay under it; the root page counts as updated in the closing line, and the others as their digests and titles have them. Going back to the default, or to any earlier title, is the same.

Where another page of the space, one no tree owns, holds the new title, the publish stops before any write, naming the title, as ADR-320 §3 and ADR-378 §6 stop it; the tree stands as it stood.

### 7. The Profile window

**`ProfileKey` gains `CONFLUENCE_ROOT_TITLE`**, the last constant, `confluenceRootTitle`, of the kind `TEXT`, with no earliest run: a change of it costs no run, closes no approval, refuses no labels and draws no sample, as ADR-359 §1 has it of the other five. **It has no value beneath the profile** (`hasAValueBeneath` false): the default is the publish's rule, not configuration, and `effective-values.properties` names no Confluence value (ADR-330 §1), so it is unchanged. Its row stands in the Profile window after the email's, a text field, and a save writes it with its provenance as every row's (ADR-343 §2).

Its words, for a reader outside this project:

| Where | Words |
| --- | --- |
| hint, `ProfileKey.hint()`, the README's row | `the title of the top page the others go under, {language} standing for the language's code; empty is Vespera_{language}` |
| example, the field's placeholder | `Vespera_{language}` |
| title, `profile-texts.json` | `Confluence top page title` |
| short, `profile-texts.json` | `The title of the top page Publish puts all your other pages under; {language} stands for the language's code.` |
| tooltip, `profile-texts.json` | `Text, empty by default, which gives the title Vespera_{language}: Vespera_en for pages in English, Vespera_it for pages in Italian. Write {language} where the language's code should go, for example Hand-overs {language}; without it, an underscore and the code are added at the end, so Hand-overs becomes Hand-overs_en. Each language has its own top page, so its title always carries the code. The title can be at most 255 characters once the code is in it; a longer one is refused before anything is sent. After a change, the next Publish renames the top page you published before, keeping its address and the pages under it, and makes no new one. Publishing only to the website copy does not use it.` |

### 8. The templates and the README

`confluence-env.sh` and `confluence-env.ps1` each gain a line setting `VESPERA_CONFLUENCE_ROOT_TITLE` to `''`, after the parent's, its comment saying that empty is `Vespera_{language}`. The README's table of profile keys gains `confluenceRootTitle` with §7's hint, its table of the variables `VESPERA_CONFLUENCE_ROOT_TITLE`, and its paragraph *In the profile* the key.

### 9. The modules, and run ids

`confluence` (`ConfluenceTitles`, `ConfluenceValues`, `Publisher` or `ConfluenceSite`, the templates), `pipeline` (`ConfluenceValuesOfTheProfile`, which holds no rule), `profile` (`Profile`) and the control panel (`ProfileKey`, `profile-texts.json`) change. No stage reads the key, so none enters a run's `config_consumed`, and no stage's version names `confluence`, `pipeline`, `profile` or the control panel (`StageModules`, ADR-274 §5, ADR-222): **no run id moves** and no stage is redone. The page list is drawn as before.

## Alternatives refused

- **The title applied by stage 6b**, written into the page list. The variable of §2 could not then change it, as the operator decided it should, and the drawing would read a value of the profile no stage reads; a changed title would want the deliverable drawn again before a publish.
- **A Spring property `vespera.confluence.root-title`** set by the variable, as the site, the space and the parent are. The application's `Publisher` would read a fifth property; read from the environment alone, as the email is, the title needs none, and a test sets it in the environment it hands the publish.
- **The title cut to fit**, its code kept whole, as ADR-310 §4 cuts a cluster's text. ADR-378 §1 never cuts the root page's title, and a title the operator typed and Vespera shortened is a title nobody chose: refusing it before any request says so.
- **The title taken as stated, with no code**, where it holds no `{language}`. The English and the Italian trees stand under one parent, and a title is unique within a space (ADR-378 §6): the second language's root page would be refused.
- **A changed title making a new tree beside the old one.** The operator decided that it retitles the one tree; and the tree is found by its label and its key (ADR-378 §2), so retitling in place is what a publish already does for any page whose title changes (ADR-328 §7).
- **The default shown as a value beneath the profile** in `effective-values.properties`. That file names no Confluence value (ADR-330 §1); the placeholder and the words say what an empty row gives.

## Consequences

- A publish whose shell and profile name no root page's title sends the titles it sent before, `Vespera_en` and `Vespera_it`, and says the same lines.
- An operator names the root page's title once, in the Profile window or in the shell; the next publish renames the tree's root page in place.
- The profile has six Confluence keys, the last six of its record, none of them on the deliverable's index.
- No run id moves.

## Tests

| Method | What it holds |
| --- | --- |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.whereNothingNamesATitleTheRootPageIsVesperaAndTheCodeAsBefore` | no title stated, or a blank one: the English tree is `Vespera_en`, the Italian `Vespera_it`, in the publish's first line (§3) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.theLanguagePlaceholderTakesTheCodeWhereverItStands` | `Hand-overs {language}` is `Hand-overs en`, `{language} notes, {language}` is `en notes, en` (§3) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.withNoPlaceholderAnUnderscoreAndTheCodeAreAppendedToTheFoldedTitle` | `Hand-overs` is `Hand-overs_en` and, in Italian, `Hand-overs_it`; a title with blanks, a tab and a line break around and in it is folded first (§3) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.theShellsTitleComesBeforeTheProfilesAndABlankOneIsNone` | the profile's alone gives the title; the shell's beside it gives the shell's; a blank shell value gives the profile's (§2) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.aTitlePastConfluencesLimitIsRefusedBeforeAnythingIsSentAndOneAtItIsNot` | a title of 256 characters is refused with §4's line, on the command line from the shell and from the control panel from the profile, nothing sent and nothing written; one of 255 is not refused, the publish going on to its first request (§4) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.theRootPageReachesTheSiteUnderTheStatedTitle` | `Hand-overs {language}` stated, against a site the test answers in memory with no connection opened: where no tree stands, the root page is created under `Hand-overs en`; where the root page an earlier publish made stands, titled `Vespera_en` and keyed `en:root`, its body is sent to that page under `Hand-overs en` and no page is created. Untagged, so CI runs it, which it does not run the integration test below (ADR-320 §6) (§5, §6) |
| `confluence.TheRootPagesTitleIsTheOperatorsTest.eachTemplateSetsTheRootTitlesVariableToNothing` | both templates set `VESPERA_CONFLUENCE_ROOT_TITLE` to `''` (§8) |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aChangedRootTitleRetitlesTheTreeInPlaceAndMakesNoNewTree` | a tree published as `Vespera_en`, published again titled `Hand-overs {language}`, holds the same three pages, ids kept, the root page `Hand-overs en`, its label and key kept, one body sent and no page created or trashed; published again with a blank title it is `Vespera_en` again, the same pages (§5, §6) |
| `confluence.ThePublishReadsItsPlainValuesFromTheProfileTest.theTokenComesFromTheEnvironmentAlone` | what a publish reads of the profile is the site, the space, the parent by its id and its title, the email and the root page's title, and no token (§1) |
| `pipeline.ThePublishReadsTheProfilesConfluenceValuesTest.theRootPagesTitleIsReadOffTheProfileWithTheOtherValues` | a profile naming `confluenceRootTitle` beside the other values loads, and its title is read as written; a blank one is none (§1) |
| `profile.TheRootPagesTitleIsAProfileKeyTest.itIsTheLastKeyOfTheProfileATextShippedUnset` | `confluenceRootTitle` is the record's last component, a `TextValue`, unset in a profile drafted afresh (§1) |
| `profile.TheRootPagesTitleIsAProfileKeyTest.itsValueAndItsProvenanceAreKeptAsWritten` | loaded and saved, `profile.yaml` keeps its value and its provenance (§1) |
| `profile.TheRootPagesTitleIsAProfileKeyTest.theDeliverablesIndexDoesNotStateIt` | `keysAsWritten` names it and `keysTheIndexStates` does not (§1) |
| `ui.ProfileKeyTest.aChangeCostsItsRunAndEveryLaterOne` | its change costs no run, closes no approval, refuses no labels and draws no sample (§7) |
| `ui.TheRootPagesTitleIsARowOfTheProfileWindowTest.itsRowIsATextEnteringNoRunWithItsWords` | `ProfileKey` names it last, `TEXT`, no earliest run, no value beneath, with §7's hint, example, title, short and tooltip (§7) |
| `ui.TheRootPagesTitleIsARowOfTheProfileWindowTest.itsFieldFollowsTheEmailsAndASaveWritesItWithItsProvenance` | the Profile window's Confluence fields are the site, the space, the parent two ways, the email and the root page's title, in that order, and a title typed and saved is in `profile.yaml` set in the control panel (§7) |
| `confluence.TheConfluenceRenderingTest.everyTitleCarriesTheLanguagesCodeAndTheRootPageIsVesperaAndTheCode`, unchanged | the page list's root page is still `Vespera_en` (§5) |

## What the implementation owes

- `ProfileFixture`, `ProfileStoreTest`'s every key answered, and each test that builds `ConfluenceValues` from five values, given the sixth.
- `ProfileKey` and `Profile` gaining the key together, as `TheJarReadsWhatTheControlPanelWritesTest` holds the one to the other; `ProfileKeys.CONFLUENCE_KEYS`, the tests' one list of the Confluence keys, names the six.

## What this does not decide

- **A root page that lost its label in the same publish as its title changed.** It is found neither by its label nor by the new title (ADR-378 §5), and a page keyed `<code>:root` under the parent is no earlier tree (ADR-378 §5), so the publish would make a root page under the new title beside it. Putting the label back, or publishing once under the old title, finds it again. Looking such a page up among the parent's children by its key is left to a record of its own.
- **A root page's title that is another page's title of the same rendering.** The site refuses the second page so titled; whether the publish should refuse it before any request is not decided.
- **Whether the website rendering's or the HTML tree's title follows the value.** Neither has a root page titled so (ADR-378 §8).
