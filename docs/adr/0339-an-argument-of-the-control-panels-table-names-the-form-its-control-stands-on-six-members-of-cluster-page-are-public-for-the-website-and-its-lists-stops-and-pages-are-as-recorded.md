# ADR-339 — An argument of the control panel's table names the form its control stands on, six members of `ClusterPage` are public for the website rendering, and the website's lists, stops and pages are as recorded here

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: here. Each choice is one that building ADR-333's second step needed, which ADR-333's Decided line leaves to its building; none is a ruling of the operator's. §1 settles a conflict between ADR-336 §2 and ADR-333 §6 by the reading that keeps both rulings and ADR-292 §1 whole.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §2, whose `Argument` gains a component `form`, whose `control` is a control of the form `form` names, and whose table gains `publish`'s `to` (§1); and its Tests, whose two rows on the table hold what §1 says, under the names §1 gives them (§1, Tests).
- **Amends**: [ADR-338](0338-the-collaborators-of-the-deliverable-a-plugin-reads-are-public-and-three-members-beyond-adr-333s-table-are-public-with-them.md) §1, whose "only the members ADR-333 §2's table lists, along with those that §2 to §4 here add" gains six members of `ClusterPage` (§2).
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §1: the launch values stay seven, and a destination is none of them. [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §6: Publish's checkboxes `to` stand on the invocation page. ADR-336 §3: Publish is not added to the launch page. [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the table names no class of the jar. ADR-333 §14: no run id moves, since `StageModules` lists no `deliverable`, `confluence` or `website` for any stage, and nothing here touches `synthesis`, `extraction` or `embedding`.

## Context

**Two rulings on one option.** ADR-336 §2 defines an entry's `control` as a control or button of the launch page's form `launch`, and holds that the three entries with no control are `vespera help`, `vespera version` and `label <file>`. ADR-333 §6 gives `publish` an option, `--to`, set by the checkboxes `to` of the invocation page's form `publish`. ADR-292 §1 keeps the launch values at seven, so `to` cannot be put on the launch form; and `--to` is passed, so it cannot be listed with no control. Read as ADR-336 wrote it, the table cannot hold `publish to` at all.

**What the website rendering reads of `ClusterPage`.** The website rendering's pages follow the HTML tree's cluster pages (ADR-333 §3): the same first page, the same list, the same split, the same links. ADR-213 §1 gives each of those rules one class, and ADR-338 §1 lets a plugin read a collaborator through the members ADR-333 §2 and ADR-338 list. The website rendering needs six more, and copying their rules into `website` would give each rule two classes.

**What building the website settled.** ADR-333 §3, §6 and §7 name the website rendering's list and the website's, the stops of a publish to the website, the rendering's pages and the Publish form's checkboxes, and leave their exact shape to the build. By `docs/adr/README.md`, what a build settles beyond a record is stated by a later one.

## Decision

### 1. An entry of the table names the form its control stands on

**`ChildCommand.Argument` gains a component `form`**, between `name` and `control`:

```java
/** One option or positional argument of picocli's model of the jar's commands, and how the control panel sets it. */
public record Argument(String command, String name, String form, String control, String reason) {
}
```

- **`form`** is the name of the form the control stands on: `launch`, the launch page's form, or `publish`, the invocation page's form that starts a publish; and **the empty text exactly where `control` is empty**.
- **`control`** is the name of the control or submit button of that form whose value or press sets the argument; empty where the control panel never passes it. ADR-336 §2's "of the launch page's form `launch`" reads "of the form `form` names".

Every entry ADR-336 §2's table gives a control names `launch`. The table gains one row, after `publish`'s `db-dir`:

| `command` | `name` | `form` | `control` | `reason` |
| --- | --- | --- | --- | --- |
| `publish` | `to` | `publish` | `to` | The checkboxes of the invocation page's Publish, one for each destination drawn; never a control of the launch form. |

The words of `reason` are the implementation's. The three entries with no control stay ADR-336's three, and `to` is never a control of the launch form, which ADR-292 §1 keeps to seven values.

**The two tests**, ADR-336 §2's, hold this reading, and take names that say it:

- `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy`, named `eachArgumentIsSetByALaunchControlOrNeverPassedAndSaysWhy` before this record, holds `publish to` by `to` on `publish`, the five other controlled entries on `launch`, and a form named exactly where a control is, `launch` or `publish`.
- `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.everyControlTheTableNamesIsOnTheFormItsEntryNames`, named `everyControlTheTableNamesIsOnTheLaunchForm` before this record, draws a tree holding both renderings' lists so that the form `publish` stands, holds each control on the form its entry names, and holds that no control of `launch` is named `to`.

ADR-336's Tests rows name the two by these names, corrected in place as `docs/adr/README.md` has it for a renamed test.

### 2. Six members of `ClusterPage` are public, for the website rendering

Beside ADR-333 §2's `ANCHOR_PREFIX` and `PageBreaks.pages()`, and ADR-338 §2's `PageBreaks.firstEntryOf`, these are public:

| Member | What the website rendering draws through it |
| --- | --- |
| `ClusterPage.firstPageBody` | a cluster's first page: its opening, its writing and the disclosure, or the sentence saying why nothing was written |
| `ClusterPage.listOpening` | the membership list's heading, and its opening numbered on from a page's first entry (ADR-303 §5) |
| `ClusterPage.nameOfPage` | `index.html`, then `index-<k>.html` (ADR-303 §4) |
| `ClusterPage.navigation` | the line naming a page of a split cluster and linking the others (ADR-303 §5) |
| `ClusterPage.percentEncoded` | a copy's name in a link (ADR-280 §5) |
| `ClusterPage.PageBreaks.nameOfPageHolding` | the page a citation links its entry on (ADR-303 §6) |

No other member of `ClusterPage` or of its breaks is public, and the breaks' constructor stays package-private, so only `deliverable` splits a page (ADR-338 §2). Each rule keeps its one class (ADR-213 §1).

### 3. The website rendering, as built

- **`files.json`** is `{"format": 1, "files": [{"path", "source", "size", "sha256", "kind"}]}`, each entry those five fields and no other, `kind` one of `page`, `asset`, `listing` or `copy` (ADR-333 §3). **`vespera-website.json`** is `{"format": 1, "files": [{"path", "sha256"}]}`, each entry those two fields and no other (ADR-333 §7).
- **A stopped delivery to the website** ends ADR-333 §7's line with a reason of ours: where the stop is a failure of the machine, reading or writing a file or reading the rendering's list, the reason ends with the thrown type's simple name in parentheses, as `a file could not be read or written (IOException)`, and never its message (ADR-274 §8).
- **A plugin that throws out of `deliver`** is a stop of that destination, on standard error: `vespera publish stopped <name>: it threw <Type>, whose message is not written. Publish again once that is dealt with.`, `<Type>` the simple name. The next destination is still delivered (ADR-333 §6).
- **A source is checked as it is copied.** Each file is digested while it is copied into its `.part`; where its size or digest is not what `files.json` names, the `.part` is deleted, nothing of it is moved into place, and the delivery stops as ADR-333 §7 has it. A source is read once.
- **A page of the website rendering follows the ceiling by entries, not to the byte.** It splits at the entries the HTML pages split at (ADR-303), and is the HTML page with the rendering's head, navigation and search around it, a few hundred bytes longer, so it can exceed `clusterPageByteCeiling` by that much.
- **Publish offers the destinations whose list is found**, `pages.json` for Confluence and `files.json` for the website rendering, in the order of `ChildCommand.DESTINATIONS`, `confluence` then `website`, Confluence's box checked and the website's not; a destination whose list is not found has no box (ADR-333 §6).
- **`FixedWords` gains three words**, in English and Italian: `search`, `Search` and `Cerca`; `searchFoundNothing`, `Nothing found` and `Nessun risultato`; and `contents`, `Contents` and `Indice` (ADR-321, ADR-333 §3).

## Known limits

- The two test methods of §1 are renamed, so a reader holding the old names finds them under the new ones only through ADR-336's corrected rows and this record.
- A page of the website rendering may be larger than `clusterPageByteCeiling` by its head and navigation, and no test holds a size of one (§3).
- The words of a stop's reason are held by the tests where they are written whole, the thrown type's line of §3 among them, and not every reason is.

## Alternatives refused

- **`control` widened to mean a control of any page of the control panel.** The table would no longer say where a control stands, and the screen test could not look for it in one form.
- **`to` on the launch form.** An eighth launch value, which ADR-292 §1 refuses, and a destination remembered from one publish to the next.
- **`publish to` listed with no control.** It is passed, so the table would say the wrong thing of it.
- **The six rules copied into `website`.** Two classes for each, which ADR-213 §1 refuses, and a page of the website rendering that could drift from the HTML page it follows.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy` | `publish to` by `to` on `publish`; the other five controlled entries on `launch`; a form exactly where a control is, `launch` or `publish` (§1) |
| `controlpanel.TheLaunchPageStartsLabelAutoScreenTest.everyControlTheTableNamesIsOnTheFormItsEntryNames` | each control on the form its entry names, `to` alone on `publish`, and no control of `launch` named `to` (§1) |
| `DeliverableRulesHaveOneHomeTest.theClusterPageOpensWhatTheRenderingsReadAndNothingMore` | `ClusterPage`'s public members and its breaks' exactly those §2 and ADR-333 §2 and ADR-338 §2 list, and no public constructor of the breaks (§2) |
| `website.TheWebsitesListsHoldTheirFormatAndFieldsTest` | both lists at format 1, each entry its fields and no other, the website's own list naming each path the rendering's lists (§3) |
| `pipeline.PublishToEachDestinationInTurnInvocationTest.aThrowIsAStopNamedByItsType` | the whole line of a plugin that throws, and the next destination still delivered (§3) |
| `website.PublishRefreshesTheWebsiteByDigestTest.aStopMidwayResumes` | a source unlike its list stops the delivery, its page not written, and the next publish leaves no file but the rendering's, the marker and the list (§3) |
| `controlpanel.InvocationScreenTest.publishIsOfferedOnceARenderingIsDrawnAndStartsThePublish` | a box per destination drawn, in `DESTINATIONS` order, Confluence's checked and the website's not (§3) |
| `website.TheWebsiteIsInTheDeliverableLanguageTest` | every word of `FixedWords`, the three added here among them, Italian on an Italian tree (§3) |
