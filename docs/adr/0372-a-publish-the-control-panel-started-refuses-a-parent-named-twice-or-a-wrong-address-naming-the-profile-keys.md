# ADR-372 — A publish the control panel started refuses a parent named twice, or an address that is not a Cloud site's, naming the profile's keys and the Profile tab

> **Partly amended — see [ADR-383](0383-a-parents-title-naming-no-page-or-several-stops-a-control-panel-publish-with-its-own-code-and-a-warning-asks-for-another.md).** Of *What this does not decide*: started by the control panel, a parent's title naming no page or folder, or several, stops the publish naming `confluenceFolderTitle`, `confluenceFolderId` and the Profile tab, exit 3 or 4, and the window warns, asking for a different title, which it saves and publishes to. The rest stands.

> **Partly amended — see [ADR-388](0388-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md).** Of §1: the two lines end *"in the control panel's Profile window, and publish again."*, not its Profile tab. The rest of this record stands.

- **Date**: 2026-11-11
- **Status**: accepted on 2026-11-11.
- **Decided**: by the operator, in [#797](https://github.com/algernon28/vespera/issues/797): that where `VESPERA_STARTED_BY=control-panel`, the refusal of a parent named both by its id and by its title, and the refusal of an address that is not the `https` address of a Confluence Cloud site, name the profile's keys and the control panel's Profile tab, as the refusal of a missing value does, and name no `VESPERA_CONFLUENCE_*` variable; and that a publish typed on the command line keeps both lines as they are. The words of the two lines, which #797 gives as its examples, are taken as they stand under delegation.
- **Amends**: [ADR-359](0359-the-publish-reads-its-confluence-values-from-the-profile-and-the-control-panel-passes-the-token-to-its-child-alone.md) §4, whose publish started by the control panel says *"Its other refusals, a parent named twice and an address that is not a Cloud site's, ... as on the command line"*: it says them as §1 has it. The rest of §4 stands. It carries a note at its head.
- **Amends**: [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) §3, whose *"a parent named by both names `VESPERA_CONFLUENCE_FOLDER_ID` and `VESPERA_CONFLUENCE_FOLDER_TITLE`"*: it does so on the command line; started by the control panel it names `confluenceFolderId` and `confluenceFolderTitle` (§1). It carries a note at its head.
- **Keeps**: ADR-359 §1 to §3 and §5 to §8: where each value is read from, the parent taken whole from one side, the child inheriting no `VESPERA_CONFLUENCE_*` variable, and its refusal of a missing value; ADR-308 §2 and §3: a parent is named by its id or its title and not both, and an address that is not `https`, or names a user, a query, a fragment or a path beyond `/wiki`, is refused before anything is sent; ADR-318 §3's other refusals.
- **Settles**: [#797](https://github.com/algernon28/vespera/issues/797).

## Context

**What a publish the control panel starts can read.** Its child inherits no `VESPERA_CONFLUENCE_*` variable but the token typed, so the site's address, the space's key, the parent and the email are the profile's, which the Profile tab sets (ADR-359 §3). The child is told who started it by `VESPERA_STARTED_BY=control-panel`.

**What it says when it refuses.** `ConfluenceSite.of` refuses before anything is sent in three ways. A value missing is named, started by the control panel, by its key as `profile.yaml` spells it, with the Profile tab to set it in (ADR-359 §4). The two others, a parent named both by its id and by its title, and an address that is not a Cloud site's, are said in the one line whoever started the publish:

- `vespera publish sent nothing: the parent is named both by VESPERA_CONFLUENCE_FOLDER_ID and by VESPERA_CONFLUENCE_FOLDER_TITLE. Name it by one.`
- `vespera publish sent nothing: VESPERA_CONFLUENCE_BASE_URL is not the https address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user, query or other path.`

Started by the control panel, each names variables the child cannot have read, and none says where the value that was read is set. An operator who sets both `confluenceFolderId` and `confluenceFolderTitle` on the Profile tab is told of two variables they never set.

## Decision

### 1. Started by the control panel, the two refusals name the profile's keys and the Profile tab

Where the publish's environment holds `VESPERA_STARTED_BY` set to `control-panel`, `ConfluenceSite.of` refuses with these lines, each whole:

| What is refused | The line |
| --- | --- |
| the parent named both by its id and by its title | `vespera publish sent nothing: the parent is named both by confluenceFolderId and by confluenceFolderTitle. Clear one in the control panel's Profile tab, and publish again.` |
| an address that is not a Cloud site's | `vespera publish sent nothing: confluenceBaseUrl is not the https address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user, query or other path. Correct it in the control panel's Profile tab, and publish again.` |

Neither names a `VESPERA_CONFLUENCE_*` variable, a template or a value. What is refused, and in which order, is unchanged: a value missing first, then a parent named twice, then the address; and the child writes no template for any of them (ADR-359 §4).

### 2. On the command line, both lines are unchanged

Where `VESPERA_STARTED_BY` is not `control-panel`, the two refusals are the lines of the Context, word for word, and the templates are written as before (ADR-308 §3). This holds whether the shell or the profile named the parent twice or the address: ADR-359's refused alternative *A refusal of the command line naming the control panel's fields too* stands.

### 3. Run ids

Only `confluence`'s `ConfluenceSite` changes. No stage reads a value of the publish, and no stage's version names `confluence` (ADR-359 §8), so **no run id moves**.

## Alternatives refused

- **One line naming both the variable and the key**, whoever started the publish: two places to set a value in one line, which ADR-359 refused for the missing values.
- **A line naming the key by what was read**, the shell's variable or the profile's key, on the command line too: a command-line publish's lines stay as they are, by the operator's decision.

## Consequences

- A publish started from the control panel names no `VESPERA_CONFLUENCE_*` variable in any refusal `ConfluenceSite.of` makes.
- A command-line operator sees no change.

## What this does not decide

- **The publish's refusal of a parent title naming no page or folder, or more than one**, which `Publisher` makes once it has reached the site and which says to name the parent by `VESPERA_CONFLUENCE_FOLDER_ID` (ADR-318 §3), whoever started the publish.

## Tests

| Method | What it holds |
| --- | --- |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.fromTheControlPanelAParentNamedTwiceAndAnAddressNotASitesNameTheProfilesKeys` | §1: started by the control panel, a profile naming the parent by both its id and its title, and a profile naming an `http` address, are each refused with §1's whole line |
| `confluence.ARefusedPublishSaysWhereToSetWhatIsMissingTest.fromTheCommandLineAParentNamedTwiceAndAnAddressNotASitesAreRefusedAsBefore` | §2: typed on the command line, a shell naming the parent by both, and a shell naming an `http` address, are each refused with the Context's whole line |

Both read the refusal through `ConfluenceSite.of`, which sends nothing.

## What the implementation owes

Beside turning the first test green, `ConfluenceSite.of` branches the two refusals on `fromTheControlPanel`, as the refusal of a missing value above them does, and its javadoc says so.
