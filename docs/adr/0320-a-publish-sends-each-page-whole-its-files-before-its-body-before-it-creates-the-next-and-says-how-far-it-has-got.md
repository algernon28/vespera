# ADR-320 — A publish sends each page whole, its files before its body, before it creates the next, and says how far it has got

- **Date**: 2026-10-22
- **Status**: accepted on 2026-10-22.
- **Supersedes**: [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §8's *"Every page is created before any body is written, so a link between pages, which Confluence resolves by title, finds its page"*, and [ADR-316](0316-the-page-list-is-pages-json-in-confluence-each-page-keyed-by-its-run-and-place-with-its-parent-title-body-files-sizes-and-digests.md) §4's **Order**: a publish goes through the page list a page at a time, and finds or creates a page, sends its files and then its body before it creates the next (§1, §2).
- **Amends**: ADR-308 §5's *"sends, prints what it sent and what it skipped, and ends"*: a publish also prints its totals before it sends, a line as each page starts and a line for each file, and on a stop the page it stopped at (§4, §5).
- **Keeps**: ADR-308 §8 in everything but that order, a page found by its key and its local body's digest, a file by its name, size and digest, nothing deleted, a page of the run the list no longer holds counted, a page titled as one of the rendering's that no publish made stopping the publish; ADR-316 §4 in everything but its order, the content property `vespera`, the label `vespera-<marker>`, one `409` read again and sent again and a second stopping the publish, a file's digest in its comment, `X-Atlassian-Token: no-check`; [ADR-313](0313-a-clusters-confluence-page-carries-its-copies-as-files-and-a-copy-the-site-refuses-is-counted-and-listed-by-the-publish.md) §3, a copy or a picture's file refused as too large skipped, counted and listed in `confluence-not-carried.csv`, any other refusal stopping the publish; [ADR-318](0318-the-publish-reads-the-email-from-vespera-confluence-email-and-the-parent-from-vespera-confluence-folder-id-or-folder-title-and-no-earlier-name.md) whole; ADR-308 §6, the control panel showing the child's output as it shows `run`'s.
- **Decided**: by the operator on 2026-10-22: each page is sent whole before the next is created, its files before its body, and the publish says how far it has got (§1, §4); and the publish's tests become integration tests that CI does not run (§6). The wording of §4, §2's reliance on a measured fact and §3's check before the first write are settled with that decision, under it.

## Context

ADR-308 §8 and ADR-316 §4 had a publish create every page of the list empty, then send every body, then send every page's files, so that a link between pages, written by title (`ri:page ri:content-title`, ADR-309, ADR-310), would find its page. The publisher printed one line for a page, after that page's files, in the third pass.

On the operator's first publish of a large rendering, of 74 pages and 3,509 files holding about 989 MiB, three things followed. Every page stood on the site empty for as long as the bodies took. Nothing was printed until the first page's files were sent, and then nothing for minutes while a large page's were. And each body was sent while the files it names (`ri:attachment`, ADR-313 §1, ADR-314) were not yet on its page, so a reader opening a page saw its copies and pictures as files Confluence could not load, until the third pass reached that page.

The reason given for creating every page first does not hold on Cloud. Measured on the operator's site (`docs/research/confluence-loading.md` §5, row 2): a title link to a page not yet created renders as a link to create it, and once the page exists the same stored link renders as a link to it, with and without `ri:space-key`. A link is resolved when a page is shown, not when its body is saved.

Bodies do link to pages later in the list: the root page to each seed partition's page and each group's first page, a seed partition's page to its groups', a split group's page to its next and last pages, and a citation to the page its entry is on (`ConfluencePages`, `ConfluencePage`, `ProseStorage`).

The publisher's lines go to the command's standard output, which the control panel reads into the last 200 lines it shows of its child (`Child.TAIL_LINES`, ADR-278 §3). Every other line the jar prints about bytes gives them as a whole number of bytes.

## Decision

### 1. A page at a time, its files before its body

Before the first page, as before: the space, the parent, and the run's pages found by their label and keyed by their property, all reads; and §3's check. Then, for each page in the page list's order, parents first:

1. **The page** is found by its key, or by its title holding its key, as before, and labelled where it was found by title; or it is **created**, empty, under its parent's page, labelled `vespera-<marker>`, its property `vespera` set to its key and an empty digest.
2. **Its files**, in the list's order: each skipped where one of its name stands on the page at its size with its digest in its comment, sent new where none of its name stands, and sent through the file's update endpoint where one does; one refused as too large skipped and listed where it is a copy or a picture's file (ADR-313 §3); any other refusal stops the publish.
3. **Its body**, where its local digest is not the one the property holds or its title is not the page's, sent as the page's version plus one, a `409` answered as before; then the property set to the body's digest.
4. **Its line** (§4), and the next page.

So no body is sent while a file it names is missing from its page, save a file the site refused as too large in this publish, which its entry still names (ADR-313 §3); and **no page is created while a page before it in the list lacks its body or a file**, save that same refused file. The property's digest is set only after the body, so a page whose body did not reach the site is sent its body by the next publish.

### 2. A link to a page later in the list

A body is sent as drawn, with its title links, though a page it links to may not stand yet. No page is created ahead of its turn, and no body is sent a second time to make a link resolve: by §5 row 2 of the research, the stored link resolves once its page exists. While the publish runs, such a link shows as a link to create the page.

### 3. A title the publish may not take is found before anything is written

Before the first page, every page of the list not found by its label is looked for by its title, by reads alone. A page so titled whose property does not hold that page's key stops the publish before any request that writes, as ADR-308 §8 has it, and is left as it stands; one whose property holds the key is taken, and labelled when its turn comes. A page so titled that appears after this check, between it and that page's turn, is refused by the site when the page is created, and the publish stops there as at any other refusal.

### 4. What a publish says

On the `out` the publish is given, which `vespera publish` prints to standard output and the control panel shows:

| When | Line |
| --- | --- |
| Once the values, the page list and every file it names are checked, before any request | `vespera publish is sending the rendering of generation run <marker> to <host>, space <key>: <n> pages and <f> files holding <b> bytes` |
| As page *i* starts, before any request about it | `page <i> of <n>: <title>, <f> files holding <b> bytes`, or `page <i> of <n>: <title>, no files` |
| After each of its files is sent, kept or refused | `file <j> of <F>: <name>, <size> bytes, sent`, `…, unchanged` or `…, refused by the site as too large` |
| After its body, and its property | the page's line as before: `<title>: created`, `updated` or `unchanged`, then `; files: <s> sent, <k> unchanged` where it has files, then `, <r> refused by the site as too large` where any were |
| At the end | the closing line as before |

- **`<n>` and `<F>` are the list's pages and files**, `<b>` the sum of the list's `size` of every file, a page's `<f>` and `<b>` its own; `<j>` counts files across the whole publish, so a file's line says how far the publish has got when the totals have left the control panel's last 200 lines. A file is *sent* whether it was added or replaced.
- **A count of one is singular**: `1 page`, `1 file`, `1 byte`. Every number is in digits, bytes whole with no separator, as the jar's other lines give them.
- **A line names nothing but** the marker, the site's host, the space's key, a page's title and a file's name, each of which the page list or the five values already hold, and counts. No line names a path, a key of the list or a document's text.
- **A publish refused before it sends**, for a missing value, a page list it cannot read or a file not at its size, prints no totals and no page's line: only its refusal, as before.

### 5. A publish stopped part way

A stop leaves on the site every page before the one it stopped at whole: its files, less any refused as too large, its body and its property's digest. The page it stopped at stands created, if it was new, with its label, its property and the files sent before the stop, and its earlier body or none. No page after it was created; one an earlier publish made stands as that publish left it. `confluence-not-carried.csv` is written only by a publish that went to its end, as before.

The stop's line says where it stopped:

`vespera publish stopped: <reason>. It stopped at page <i> of <n>, <title>. It had created <c> pages, updated <u> and sent <s> files. Publish again to go on from there.`

The sentence `It stopped at page …` is left out where the publish stopped before its first page, at the space, the parent or §3's check; an interrupted publish says `it was interrupted` as its reason, as before. The next publish finds the pages by their label, skips every file and body that stands at its digest, sends the rest of that page's files and its body, and goes on through the list.

### 6. The publish's tests are integration tests CI does not run

Every test class that publishes to the stub site is named `*IT` and tagged `@Tag("confluence")`: `confluence.PublishOrderAndRefusalsIT`, `PublishReadsItsCredentialFromTheEnvironmentIT`, `PublishReadsItsValuesByTheirNamesIT`, `PublishSendsToOneHostIT`, `RepublishUpdatesInPlaceIT`, `PublishSendsEachPageWholeIT`, and `pipeline.PublishFindsTheLastDrawnRenderingIT`. Surefire's exclusion of `**/*IT.java` keeps them out of the `test` phase, and failsafe runs them in the `verify` phase. CI's Linux job runs `verify` with `-DexcludedGroups=confluence`, a property failsafe reads, so CI runs none of them; its NTFS job runs the `test` phase, which the name already keeps them out of. The tests of the rendering in `deliverable` stay unit tests.

**CI no longer guards the publish.** What ADR-308, ADR-313 §3's publish half, ADR-316 §4, ADR-318 and this record decide of a publish is held only by a `verify` run locally, whole or naming them by `-Dit.test`. A change to `confluence` or to the publish command is verified that way before it is merged.

### 7. Run ids

No stage's version names `confluence` (`StageModules`), and the publish reads no stage's rows but the ledger's runs; no run id moves.

## Alternatives refused

- **Every page created first, as before**: its only reason, a title link finding its page, does not need it (§2), and it leaves every page empty for the length of the publish.
- **A page created empty just before the first body that links to it, or a second pass over the bodies that link to a later page**: each sends or creates something only to make a link resolve, which it does without either; the second also gives those pages a version more.
- **A page's body before its files**: what the operator saw, a page naming files it cannot show.
- **A line per page alone**: a group's page of hundreds of files and megabytes stays silent for minutes. A line per file is one line for each request that sends a file's bytes or decides not to.
- **Bytes in MiB**: a unit needs a rounding and a decimal separator, and every other line of the jar gives whole bytes.
- **The title check left to each page's turn**: a page no publish made, titled as the list's last page, would stop a publish after every page before it was sent whole, where it stopped before with nothing but empty pages; checking first keeps that stop before any write.

## Consequences

- **`Publisher`** goes through the list a page at a time, prints the lines of §4 and the stop of §5, and checks titles before the first write; its javadoc says so. That is `src/main`'s change.
- **`.github/workflows/build.yml`**'s Linux job runs `verify` with `-DexcludedGroups=confluence`, and the failsafe comment in `pom.xml` names the tag and that CI leaves it out.
- **`AGENTS.md`** names the seven among the integration tests and says CI leaves them out; `docs/check-claims.mjs` reads that count in digits.
- **`README.md`**'s *Publishing to Confluence* says what a publish does and tells as this record has it.
- **ADR-308 and ADR-316 carry a banner** pointing here; their bodies are unchanged, but for the test names in their Tests tables, which are corrected in place to the names §6 gives.
- **A reader who follows a link to create a page during a publish**, before that page's turn, makes a page of its title no publish made: the publish stops at that page, which is left for the operator to remove.

## What this does not decide

- **A page created whose label or property the site did not take** before a stop: the next publish finds it by its title holding no key and stops, as before this record.
- **Whether the editor, rather than the `view` form the research read**, shows a stored title link to a later page as a link once that page exists.
- **Sending files in parallel**, or a file's bytes in parts with a line for each.

## Tests

`PublishSendsEachPageWholeIT`, against `StubConfluence`, which records each write it takes and each body with the files its page held when it took it; the root page's and the group's bodies name their files, and the root page's and the seed partition's link to the page after them, so an order that sends a body early has something to show.

| Method | What it holds |
| --- | --- |
| `confluence.PublishSendsEachPageWholeIT.aPageIsFilledBeforeTheNextIsCreated` | the site takes each page's create, its files in the list's order and its body, a page at a time; no page is created while one before it lacks its body or a file; parents first, each labelled and keyed (§1) |
| `confluence.PublishSendsEachPageWholeIT.noBodyIsSentNamingAFileItsPageDoesNotHold` | every file a body names stood on its page when the site took the body (§1) |
| `confluence.PublishSendsEachPageWholeIT.aCopyRefusedAsTooLargeIsSaidSoAndItsBodyStillGoes` | a copy refused as too large is said so on its line, and its page's body is still sent naming it (§1, §4; ADR-313 §3) |
| `confluence.PublishSendsEachPageWholeIT.theTotalsComeBeforeAnyRequest` | the first line is the totals, printed before the site is sent any request (§4) |
| `confluence.PublishSendsEachPageWholeIT.eachPageIsAnnouncedThenItsFilesThenItsClosingLine` | the whole of a first publish's lines, word for word (§4) |
| `confluence.PublishSendsEachPageWholeIT.everythingAboutAPageIsSentBetweenItsTwoLines` | every request that creates a page or names its id falls after the line that starts it and before the line that closes it (§1, §4) |
| `confluence.PublishSendsEachPageWholeIT.aSecondPublishSaysEachPageAndFileUnchanged` | a second publish of the same rendering prints each page and file unchanged, and the site takes no write (§4; ADR-308 §8) |
| `confluence.PublishSendsEachPageWholeIT.aPublishStoppedHalfwayIsFinishedByTheNext` | a stop at the second page leaves the first whole, the second without its body and no third, says where it stopped, and the next publish finishes all three (§5) |
| `confluence.PublishSendsEachPageWholeIT.aPageOfALaterTitleNoPublishMadeStopsThePublishBeforeAnyWrite` | a page no publish made, titled as the list's last, stops the publish with only reads sent and only the totals printed (§3, §5) |
| `confluence.PublishSendsEachPageWholeIT.aPublishRefusedBeforeSendingPrintsNoTotals` | a publish refused for a missing value prints nothing but its refusal (§4) |
| `confluence.PublishOrderAndRefusalsIT.aSecond409StopsThePublish` | a second `409` stops the publish, and no line closes that page (§1; ADR-316 §4) |
| `confluence.RepublishUpdatesInPlaceIT` | a republish updates in place, unchanged by this order (ADR-308 §8) |
