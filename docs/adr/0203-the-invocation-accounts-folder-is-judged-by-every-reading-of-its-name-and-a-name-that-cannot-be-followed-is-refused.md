# ADR-203 — The invocation account's folder is judged by every reading of its name, and a name that cannot be followed is refused

- **Date**: 2026-10-06
- **Status**: accepted
- **Amends**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md), in these places and no others: §1's sentence on when the account is written ("is not at or below the working directory this invocation uses, and has no folder at or above it that holds `vespera.db` or `vespera.lock`"), which is now a judgement of every reading of the name and not of its text; §1's two warnings, to which a third is added; §6's "with the file it tried to create", made exact; and §7's "The only lines added are the warnings of §1 and §6", which the third line made false. ADR-198's allow-list (§2), its counts (§3), its progress lines (§4), its exception types (§5) and its timing (§8) stand as written.
- **Rests on**: [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md), whose §1 reads a name as text and as the file system walks it and refuses on either, and whose §2 refuses a name that is there and cannot be followed. [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), whose hook is what the account's folder has to be readable through.
- **Settles**: [#436](https://github.com/algernon28/vespera/issues/436), which ADR-201 left open: the account's own refusal to write, which compared paths as text, "raised as its own ticket".

## Context

ADR-198 §1 writes the account only in a folder that is outside every working directory, because ADR-196's hook refuses every path at or below a folder holding `vespera.db` or `vespera.lock`, so an account written there cannot be read by the agent it is for. The code held that by comparing the text of the configured name with the text of the working directory, and by asking, for each folder above the name, whether it holds either file. A name is not only its text. A link in it leads somewhere else, and `Files.createDirectories` and the open that follow go where the link leads, so an account could be written inside a working directory under a name that does not look like one.

**The hook judges a name both ways, and refuses on either** (ADR-201 §1, and in the guard: `inside: workingDirectoryAbove(absolute) ?? workingDirectoryAbove(found.real)`). The first form of the fix for #436 followed the links and judged only where the name leads. It over-corrected: a name written inside a working directory, through a link that leads out of it, was then accepted, and the account was written outside. That account cannot be read under the configured name, because the hook reads that name by its text too and refuses it as a path inside a working directory. The operator is not told. That is the harm #436 describes, from the other side.

Three other facts decide this record, each executed on 2026-10-06 on Windows 11 with JDK 26.0.2.1, in a folder of the scratch area and not in the repository, with a junction `links/a-link` leading to `deep/beside`:

- **The system folds a parent step as text before it follows a link.** `Files.isDirectory(a-link/../sub)` is false, `toRealPath(a-link/..)` is `links`, and the path `links/sub` is what a program reaches. The hook refuses the same configured name by its walked reading (ADR-201 §1), which goes through the link and reaches `deep/sub`.
- **`java.nio` can make the walked reading by hand.** Taking the path's names one at a time with `getName(i)`, and at each `..` the parent of the real path reached so far, gives `deep/sub` on Windows. `Path.relativize` cannot be used for it: it folds the `..` away, and a walk built on it gave `links/sub`. A `\\?\` prefix did not change what `Files.isDirectory` answers.
- **A junction can be made to a name that is not there.** `mklink /J` to a name that does not exist succeeded. `Files.exists` of that junction without following links is true, `Files.isSymbolicLink` is false, `toRealPath` throws `NoSuchFileException`, and `createDirectories` beneath it throws `FileAlreadyExistsException`. `TestLinks` said otherwise, and is corrected.

## Decision

### 1. The folder is read every way its name can be read, and any one of them inside a working directory refuses

The configured name is made absolute and not folded. It is then read four ways, each as a path with no `..` and no link left in it, except the first, which is the text:

1. **As text**: each `..` folded against the name before it. Its own text is judged, and so is where it leads (next).
2. **Where the text leads**: the deepest name of it that is there, asked without following links (so a link to nothing is there), is resolved with `toRealPath`, and the names after it, which are not there, are put back. When those names held a `..`, the folded result is resolved again, so that a link the fold brings up to the part that is there is followed too (`missing/../a-link`).
3. **As the file system walks it** (ADR-201 §1): from the root, one name at a time; at each `..`, the parent of the real path reached so far; a name that is not there is put back as text. §2 says how.
4. **Where this machine's own file calls lead**, which is where `createDirectories` and the open will go (§4). On Windows the system folds a `..` as text before it follows a link, so this is reading 2; elsewhere the system walks it, so this is reading 3. It is judged as well, so that what is written to is always what was judged and no claim about a platform holds that up.

**Inside** is: at or below the working directory this invocation uses, taken as given and taken as resolved, or at or below a folder holding `vespera.db` or `vespera.lock`. **Any reading that is inside refuses**, with the line ADR-198 already has: `no invocation account was written: vespera.account-dir lies inside a working directory`. **A name that any reading cannot follow is refused first, with §3's line, before any reading is judged**, because no reading is complete until every name on it has been followed: a name whose text lies inside a working directory, but which meets a link to nothing, is refused as a name that cannot be followed and not as one that lies inside. A name judged inside by one reading and outside by another is refused: the agent reads the configured name, and the hook reads it by each of them.

- **What it lets through that was refused before**: nothing. A name refused by its text, such as a link in a working directory that leads out of it, is refused still.
- **What it newly refuses**: a name whose text is outside every working directory and which leads into one, or is read as inside one by the walk; and a name that cannot be followed (§3).

### 2. The walked reading is made by hand, on every system, and ADR-198 takes it on Windows too

The open question was whether ADR-198 should adopt ADR-201 §1's walked reading of a parent step on Windows, so that the two tests of the first form (one for a file system that walks a parent through a link, one for a file system that folds it) become one refusal. **It does.** The reason is §1's: the hook reads the configured name by its walked reading on every system, so an account written where only the text lands is unreadable under the name the operator set, however the system that wrote it folds a `..`.

It is reachable on Windows without the system's help, by the measurement above: the code takes the names of the path one at a time and takes the parent of the real path reached so far at each `..`. It does not hand a path that still holds a `..` to `relativize`, `resolve` or `normalize` before the walk has finished, because on Windows each of them folds it as text.

- **What it does not know**: on a system that is not Windows the walked reading is what the system itself does, and was not run here. CI runs it on Linux.
- **The platform pair is deleted.** `noAccountWhereAParentOfTheFolderLeadsIntoAWorkingDirectory` holds the refusal without a platform assumption, and `anAccountIsWrittenWhereAParentOfTheFolderIsFoldedAsText` is removed, since its claim, that the account is written where the text lands, is false once the walked reading lands inside.

### 3. A name that is there and cannot be followed is refused, with a line of its own

A name for which `Files.exists(name, NOFOLLOW_LINKS)` is true and `toRealPath` throws, as a link to nothing or a link in a loop does, is a name the account cannot place. **No account is written**, nothing is created at the name it leads to, and the invocation writes one warning: `no invocation account was written: vespera.account-dir cannot be followed to where it leads`.

- **It holds for every name the readings meet**, and it is decided before any reading is judged (§1): the configured name, a name above it, and a name the walk of §1 reaches, including one a following `..` would climb out of. That is stricter than ADR-201 §2, which lets `links/nowhere/..` through because the hook only reads and nothing is read through a broken link. Here the account is a file written once, and the cost of a refusal is one warning.
- **The working directory is a name too.** One that is there and cannot be followed gives the same line. It is practically unreachable: `vespera.db` is open in the working directory before `beforeJob` runs, so the directory has been followed. It is not judged unreachable by assumption, and the line is there for it.
- **`Files.exists(..., NOFOLLOW_LINKS)` answers false when existence cannot be determined**, access denied among the causes (the JDK's documentation; not executed here). Such a name is then answered from its parent as a name that is not there. It is expected to surface as §5's "could not be written" warning, from the JDK's documentation; that was not executed here, and Windows permissions may allow a folder to be created beneath a name whose attributes cannot be read.

### 4. The account is written only where it was judged

The place judged (reading 4 of §1) is the place the folder and the file are created, and nowhere else. The configured name is not used again after the judgement.

- **The gap between judging and creating is accepted.** The part of the name that exists is resolved, with no link in it, when it is judged, and it is written by that resolved path. What remains is an operator who swaps a folder in that path for a link while the invocation starts, on their own machine. ADR-196's threat is an agent that reads what it may not; an operator racing their own command line is not in it, and the hook reads the folder again at each read.

### 5. The warning for a failed write names the place that was tried

ADR-198 §6 says the line names the file it tried to create. Now that the account is created only where it was judged, that is a resolved path: the folder when the folder could not be made, the file when it could not be opened. It is never the configured name, which a link in it would make say something other than where the write went.

### 6. ADR-198 §7 is corrected

§7 says the only lines added are the warnings of §1 and §6. They are four: §1's `vespera.account-dir is not set`, §1's `lies inside a working directory`, this record's `cannot be followed to where it leads`, and §6's. Each appears only when no account was written. No existing log call is edited.

## Decided here, open to the operator's overruling

1. §1 refuses when any reading is inside, where it could have refused only when every reading is.
2. §2 adopts the walked reading on Windows, where it could have stated the limit and kept a platform pair.
3. §3 refuses a broken link a `..` would climb out of, where it could have followed ADR-201 §2's rule for `links/nowhere/..` (case L307 of the guard's table).
4. §4 writes where this machine's own calls lead when the readings differ and are all outside, and does not refuse for the difference. A name such as `link/../accounts`, where the text lands in one place and the walk in another, both outside, is written in one of them, and the agent may not find it in the other. It is a name the operator would not write on purpose.
5. §4 accepts the race.

## Consequences

- **An account is never written inside a working directory, and never under a name the hook refuses.** Both of #436's directions are closed: a link into a working directory, and a link in one that leads out.
- **A name with a `..` after a link may be refused on Windows where it was written before**, because the walked reading is now judged there. The operator writes the name without it.
- **One more warning in `vespera.log`**, only when no account was written.
- **Run ids move for stages 3 to 6b**, as they did for ADR-198: `InvocationAccount` is in `pipeline`, which their implementation versions span. An archive that has not yet replayed them for ADR-198 pays once for both.
- **Nothing in `ledger`, `corpus`, `extraction`, `similarity`, `embedding` or `synthesis` changes.**

## Alternatives weighed

- **Judge only where the name leads**, the first form of the fix. Rejected in Context: it writes an account the agent cannot read under the configured name, silently.
- **Judge only the text**, the old code. Rejected in Context.
- **State the Windows limit and keep the platform pair** (§2). Weighed since `java.nio` hands `link/..` back folded. Rejected because a hand walk reaches the walked reading, shown above.
- **Refuse any name that holds a `..`.** Weighed, and the simplest rule. Rejected: it refuses `a/../b` where no link is on the way, which every reading agrees on.
- **Refuse when the readings lead to different places** (§4). Weighed, and it would remove the one place the agent may not find the account. Rejected as a fourth line for a name nobody writes on purpose.
- **Keep an unresolvable working directory as "not there"** (§3). Rejected for ADR-196 §6's reason: the account has not reached a decision about where it is, and the cost of refusing is one warning.

## Tests

`InvocationAccountTest` holds the writer on its own; each case builds its links with `TestLinks`, which makes a symbolic link and, on Windows where that is refused, a junction.

Every test of this change carries `@Issue("436")`; there are seventeen, and each is named here once.

- A link in a working directory that leads out of it, written under: `noAccountWhereTheFolderIsALinkInAWorkingDirectoryThatLeadsOutOfIt` (§1; it held the opposite in the first form of #436's fix).
- A parent step after a link into a working directory: `noAccountWhereAParentOfTheFolderLeadsIntoAWorkingDirectory` (§2), and the text of a parent step inside while the walk leads out: `noAccountWhereOnlyTheTextOfAParentStepLiesInsideAWorkingDirectory` (§1).
- The folder is a link into a working directory (`noAccountWhereTheFolderIsALinkIntoAWorkingDirectory`), and one that does not exist yet lies beneath such a link (`noAccountWhereTheFolderDoesNotExistYetBeneathALinkIntoAWorkingDirectory`): nothing is written or created (§1).
- The two positive cases, where an account is written and nothing is logged: through a link to a folder outside every working directory, `anAccountIsWrittenThroughALinkToAFolderOutsideEveryWorkingDirectory`, and in a folder that does not exist yet beneath one that does and is outside every working directory, `anAccountIsWrittenInAFolderThatDoesNotExistYet` (§1, §4).
- A folder that is a link to nothing (`noAccountWhereTheFolderIsALinkToNothing`), one beneath a link to nothing (`noAccountWhereAFolderAboveItIsALinkToNothing`) and two links in a loop (`noAccountWhereTheFolderIsALinkInALoop`): the line is "cannot be followed" (§3).
- A parent step that climbs out of a link to nothing: `noAccountWhereAParentStepClimbsOutOfALinkToNothing` holds that nothing is written or created and that the one line is "cannot be followed", which is the stricter rule of §3 and the third decision left open to the operator below.
- A working directory that cannot be followed: `noAccountWhereTheWorkingDirectoryCannotBeFollowed` (§3).
- A link to nothing, made inside a working directory and named as the folder, so that its text lies inside: `noAccountWhereTheFolderIsALinkToNothingInAWorkingDirectory` holds that the line is "cannot be followed" and not "lies inside" (§1, §3).
- A name made of the folder a link is made in, then `missing`, a parent step and the link's name (`<the folder the link is made in>/missing/../a-link`), where a parent step follows a folder that is not there. `noAccountWhereAParentStepAfterAMissingFolderLeadsOntoALinkIntoAWorkingDirectory` holds the refusal when the link leads into a working directory, and `anAccountIsWrittenWhereAParentStepAfterAMissingFolderLeadsOntoALinkOutsideEveryWorkingDirectory` holds that the account is written where a link to a folder outside leads and that the folder the link was made in gains no `missing` (§4). Neither observes the second resolution of §1's reading 2: both are green with or without it, on Windows and elsewhere. Only `theWarningForAFailedWriteAfterAParentStepNamesTheFileBeyondTheLink` observes it, by the warning of §5 naming a place with no link in it, and only on a system that walks a parent step through a link, which Windows does not (§5).
- The warning for a failed write names where the link leads: `theWarningForAFailedWriteNamesTheFileBeyondTheLink` (§5).

## What this does not decide

- **Whether the agent finds the account** when the readings of a name lead to different places and every one is outside. §4 writes where this machine's own calls lead and says no more.
- **A retention rule** for old accounts, as in ADR-198.
- **Whether the walked reading and the file system's own agree on a system that is not Windows**, by execution (§2).
- **A denied name, which the JDK cannot tell from a missing one.** ADR-201 §2 refuses it in the hook. Here it is answered from its parent and is expected to end as §5's warning; that was not executed (§3). That is weaker and leaks nothing.
