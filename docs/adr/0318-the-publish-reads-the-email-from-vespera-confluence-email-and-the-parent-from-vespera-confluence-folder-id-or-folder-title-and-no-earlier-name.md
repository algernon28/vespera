# ADR-318 — The publish reads the account's email from `VESPERA_CONFLUENCE_EMAIL` and the parent from `VESPERA_CONFLUENCE_FOLDER_ID` or `VESPERA_CONFLUENCE_FOLDER_TITLE`, and the names ADR-308 gave them from nowhere

- **Date**: 2026-10-22
- **Status**: accepted on 2026-10-22.
- **Supersedes**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §3, in the names of three of its five values alone: the account's email is read from `VESPERA_CONFLUENCE_EMAIL`, not `VESPERA_CONFLUENCE_USER`; the parent by its id from the property `vespera.confluence.folder-id`, set by `VESPERA_CONFLUENCE_FOLDER_ID`, not `vespera.confluence.parent-id` and `VESPERA_CONFLUENCE_PARENT_ID`; and the parent by its title from the property `vespera.confluence.folder-title`, set by `VESPERA_CONFLUENCE_FOLDER_TITLE`, not `vespera.confluence.parent-title` and `VESPERA_CONFLUENCE_PARENT_TITLE` (§1, §2).
- **Keeps**: ADR-308 §3 in everything but those names: where each value is read from and never read from, the token's variable `VESPERA_CONFLUENCE_TOKEN`, the site's and the space's variables and properties, the parent being a page or a folder named by its id or its title and not both, the templates naming the five variables and no value, and a publish missing any of the five refusing before anything is sent and naming the variable it lacks, never its value (§3).
- **Keeps**: ADR-308 §1, §2 and §4 to §10 whole.
- **Decided**: by the operator on 2026-10-22: the three variables are renamed, the earlier names are no longer read and nothing falls back to them, the parent is still a page or a folder, and the refusals name the new variables.

## Context

ADR-308 §3 named the account's email `VESPERA_CONFLUENCE_USER` and the parent `VESPERA_CONFLUENCE_PARENT_ID` or `VESPERA_CONFLUENCE_PARENT_TITLE`. What the operator types there is an email address, and what they name is, on their site, a folder of a space they can write to. The operator asked that the variables say so.

Spring sets each `vespera.confluence.*` property of the publish from the environment variable of the same name upper-cased with its dots and hyphens made underscores, so a property's name and its variable's name move together: `vespera.confluence.folder-id` is read from `VESPERA_CONFLUENCE_FOLDER_ID`, and nothing asks for `vespera.confluence.parent-id` once the publisher's constructor names the new property.

## Decision

### 1. The three names

| Value | Read from | Earlier name, read nowhere |
| --- | --- | --- |
| the account's email | the environment variable `VESPERA_CONFLUENCE_EMAIL` alone | `VESPERA_CONFLUENCE_USER` |
| the parent by its id | the property `vespera.confluence.folder-id`, set by `VESPERA_CONFLUENCE_FOLDER_ID` | `vespera.confluence.parent-id`, `VESPERA_CONFLUENCE_PARENT_ID` |
| the parent by its title | the property `vespera.confluence.folder-title`, set by `VESPERA_CONFLUENCE_FOLDER_TITLE` | `vespera.confluence.parent-title`, `VESPERA_CONFLUENCE_PARENT_TITLE` |

The token, `VESPERA_CONFLUENCE_TOKEN`, the site's address, `vespera.confluence.base-url` set by `VESPERA_CONFLUENCE_BASE_URL`, and the space's key, `vespera.confluence.space-key` set by `VESPERA_CONFLUENCE_SPACE_KEY`, keep their names.

### 2. The earlier names are not read

No class of the jar reads `VESPERA_CONFLUENCE_USER`, `VESPERA_CONFLUENCE_PARENT_ID` or `VESPERA_CONFLUENCE_PARENT_TITLE`, and no constructor or `@Value` names `vespera.confluence.parent-id` or `vespera.confluence.parent-title`. Nothing falls back to them, warns of them or names them: an environment holding only an earlier name is an environment missing that value, and the publish refuses before anything is sent as §3 of ADR-308 has it. A parent set by an earlier name beside one set by a new name is not a parent named twice: the earlier one is not read, and the publish goes under the one the new name names.

### 3. What is unchanged

- **The parent is a page or a folder** of the named space, by its id or by its title, one and not both, as ADR-308 §3 has it. *Folder* in the variables' names does not narrow it: an id or a title naming a page is taken as before.
- **Every refusal naming one of the three names the new variable**: a missing email names `VESPERA_CONFLUENCE_EMAIL`; a parent named by neither names `VESPERA_CONFLUENCE_FOLDER_ID or VESPERA_CONFLUENCE_FOLDER_TITLE`; a parent named by both names `VESPERA_CONFLUENCE_FOLDER_ID` and `VESPERA_CONFLUENCE_FOLDER_TITLE`; and a title naming no page or folder, or more than one, says to name the parent by `VESPERA_CONFLUENCE_FOLDER_ID`. No refusal names an earlier name, and none names a value.
- **The templates**, `confluence-env.sh` and `confluence-env.ps1`, set the five variables by these names, and name no earlier one and no value.

### 4. Run ids

No stage reads a value of the publish, and no stage's version names `confluence` or `pipeline`, so no run id moves (ADR-308 §10).

## Alternatives refused

- **Reading the earlier names as a fallback**, or beside the new ones: two names for one value is a second thing to explain on every refusal, and the operator decided against it.
- **Renaming `VESPERA_CONFLUENCE_PARENT_ID` and keeping `VESPERA_CONFLUENCE_PARENT_TITLE`**: the two name one value two ways, and are read as a pair.

## Consequences

- **An operator who set the earlier names sets the new ones**: a publish with only the earlier ones set refuses, naming the new ones. A `confluence-env.sh` or `confluence-env.ps1` already in the working directory still sets the earlier names, and the publish does not write over it, since it writes the templates only where they are not there already; it is edited, or deleted so that the next refused publish writes it afresh.
- **`README.md`**'s table of the five values names the new variables.
- **ADR-308 carries a banner** pointing here; its body is unchanged.

## What this does not decide

- **The Java names of the constants and accessors** holding these values (`ConfluenceSite`'s, the publisher's): they are not report-visible and not a value the operator sets.
- **Whether a publish writes the templates over a copy that still names an earlier variable**: it does not today, and a change to that is a decision of its own.

## Tests

| Method | What it holds |
| --- | --- |
| `confluence.PublishReadsItsValuesByTheirNamesTest.anEnvironmentHoldingOnlyTheEarlierEmailVariableIsRefusedNamingTheNewOne` | the email is read from `VESPERA_CONFLUENCE_EMAIL` and not from `VESPERA_CONFLUENCE_USER`; the refusal names the new variable and no earlier one, and nothing is sent (§1, §2, §3) |
| `confluence.PublishReadsItsValuesByTheirNamesTest.theParentIsReadFromTheFolderVariablesAndNotFromTheEarlierOnes` | through Spring's reading of the environment, the publisher's properties take the parent from `VESPERA_CONFLUENCE_FOLDER_ID` and `VESPERA_CONFLUENCE_FOLDER_TITLE`, and nothing from `VESPERA_CONFLUENCE_PARENT_ID` or `VESPERA_CONFLUENCE_PARENT_TITLE` (§1, §2) |
| `confluence.PublishReadsItsValuesByTheirNamesTest.aParentNamedByNeitherIsRefusedNamingTheFolderVariables` | a parent named by neither is refused naming both new variables and no earlier one (§3) |
| `confluence.PublishReadsItsValuesByTheirNamesTest.aParentNamedByBothIsRefusedNamingTheFolderVariables` | a parent named by both is refused naming both new variables and no earlier one (§3) |
| `confluence.PublishReadsItsValuesByTheirNamesTest.aTitleNamingNoPageOrFolderIsRefusedPointingAtTheFolderId` | a title naming no page or folder stops the publish pointing at `VESPERA_CONFLUENCE_FOLDER_ID` and no earlier name (§3) |
| `confluence.PublishReadsItsCredentialFromTheEnvironmentTest.noPropertyNamesTheTokenOrTheEmail` | the publisher reads `vespera.confluence.folder-id` and `vespera.confluence.folder-title` beside the site's and the space's properties, and no `parent` property (§1, §2) |
| `confluence.PublishReadsItsCredentialFromTheEnvironmentTest.aMissingValueIsNamedAndRefusedBeforeSending` | a missing email is named `VESPERA_CONFLUENCE_EMAIL`, and the two templates name the five variables by their new names and no earlier one (§1, §3) |
