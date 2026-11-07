# ADR-363 — The ADR index and Adr.java keep their rows in number order, and one script resolves a merge's conflicts in both

- **Date**: 2026-11-07
- **Status**: accepted on 2026-11-07.
- **Settles**: the order of the rows of `docs/adr/README.md` and of the constants of `src/test/java/io/algernon/vespera/Adr.java`, the claim that holds it, and how a merge whose conflicts lie in either file is resolved.
- **Keeps**: [ADR-259](0259-no-document-keeps-a-count-of-the-decision-records-and-agents-md-says-how-a-command-is-written-under-the-guard.md) §1, no count of the records written anywhere; and [ADR-346](0346-agents-md-is-measured-as-git-stores-it-and-a-new-decision-record-takes-a-number-no-other-holds.md) §2 to §4, the number a new record takes, and what the claims check computes without reading a file being in `docs/check-claims-lib.mjs`.

## Context

A record adds one row at the end of the index and, where a test links it, one constant at the end of Adr.java. Two branches that each add a record edit the same place in both files, so the second to merge main conflicts in both. git leaves outside a conflict the lines its two sides share at its edges, so that a conflict between two of Adr.java's constants holds each without the `/**` line that opens its javadoc, which stands above the conflict, and without the blank line after it, which stands below. A row of the index runs to 3,057 characters, and the private-paths guard refuses a Bash command whose text holds a conflict marker, so the conflict is resolved by Edits that copy whole rows.

The index holds its rows in two tables: the reconstituted records', ADR-001 to ADR-049, and, below the heading "Decisions written after the reconstitution", a paragraph and a table head of their own, every later record's. Its rows run in ascending order of number across both, and a record adds its row at the end of the second.

Adr.java's 305 constants stand next to each other, a blank line between two, and are not in order of number. 22 of them stand directly after the constant of a higher record, and 36 below the constant of a higher record somewhere above them; the constant of ADR-225 is the last of both, and from the one after it on, the constants run in order. The longest sequence of them already in order holds 273 constants.

## Decision

### 1. Both files keep their rows and constants in order of number

A row of the index is a line beginning `| [ADR-NNN]`, and its number is NNN. Each record has one row at most, and the rows run in strictly ascending order of number.

A constant of Adr.java is a `public static final String` whose value names a record's file, `NNNN-<slug>.md`, and its number is that file's. The constants run in ascending order of number, two constants of one record standing next to each other. Adr.java's constants are put in that order once, in the change that brings §2's claim, each with its javadoc and its text as they are.

### 2. The claim "the order of the decision records in the index and in Adr.java"

`node docs/check-claims.mjs` reads both files on every run, with or without `--with-network`, and the claim:

- fails where a row's number is below the number of the row before it, naming the file and both, as `docs/adr/README.md: ADR-360 after ADR-361`, and where it is the same, as `docs/adr/README.md: ADR-360 twice`;
- fails where a constant's number is below the number of the constant before it, as `src/test/java/io/algernon/vespera/Adr.java: ADR-015 after ADR-053`; two constants of one number pass;
- fails where a file holds a line beginning with seven `<`, seven `|` or seven `>`, the three markers of a conflict git writes, naming the file and the line of the first, as `docs/adr/README.md: a conflict marker on line 9`. A line of seven `=` alone is not read, since Markdown underlines a heading with it;
- names every fault, the index's first and then Adr.java's, each in the order of the file, separated by `; `, a number written `ADR-` and three digits at least;
- passes otherwise, naming how many rows and constants it read: `N rows of docs/adr/README.md and M constants of src/test/java/io/algernon/vespera/Adr.java, each in ascending order of number`, so that a claim that read none cannot pass as one that read them all.

The index's rows are read as one sequence, the first table's and then the second's. `adrOrderClaim({ index, adrJava })` in `docs/check-claims-lib.mjs`, given the two files' text, gives `{ state, detail }`; the claims check reads the files and passes them. The line of the claims check's list of what it does not check that names the index's rows says instead that a row's title and date, and whether every record has a row, are not checked.

### 3. `node docs/merge-adr-index.mjs` resolves a merge's conflicts in both files

It is run from the repository's root, with no argument, while a merge, a rebase or a cherry-pick stands with its conflicts. It reads `docs/adr/README.md` and `src/test/java/io/algernon/vespera/Adr.java` itself, so that no conflict marker appears in a command's text.

- A file holding no conflict marker is left as it is, byte for byte.
- A conflict opens at a line beginning with seven `<`, may hold a line beginning with seven `|` before its `=======`, and closes at a line beginning with seven `>` after it. A file holding a line beginning with seven `<`, seven `|` or seven `>` anywhere else, such as a `>>>>>>>` with no conflict open, which a merge resolved by hand halfway leaves, is refused, as is a file whose last conflict is not closed. A line of seven `=` outside a conflict is the file's own text.
- A file holding a conflict is read as its two sides: the text with each conflict replaced by the lines before its `=======`, and the text with each replaced by the lines after it, the lines between `|||||||` and `=======`, the base's, read as none. Where the two sides differ in anything but their rows, in the index, or their constants, in Adr.java, the file is refused; so is a record whose row or constant the two sides hold with different text.
- A stretch is rows of the index, or constants of Adr.java, standing next to each other, nothing between two of them; the index has two, one to a table, and Adr.java has one. Each side's stretches are read with the place each stands in the side's text without its rows or constants, and the number of its first row or constant. Where the two sides' stretches do not stand in the same places, as where one side holds a row where the other holds none, the file is refused.
- Otherwise the file is written as its first side with its rows or constants taken out, and the rows or constants of both sides, each once, given to the stretches: a record goes to the last stretch whose first number, the lower of the two sides' first numbers for that stretch, is not above the record's, or to the first stretch where the record's number is below every stretch's first number. Each stretch's share is written in ascending order of number at the place that stretch stood, laid out as the file lays them out: a row to a line; a constant with its javadoc above it and a blank line after it.
- A file is written with the line ending it has: a file of CRLF lines is written with CRLF lines.
- It runs no git command and stages nothing. Adding the two files and committing the merge are left to whoever merges.

It exits 0 where neither file holds a conflict marker once it is done, printing a line for each file that names its path. It exits 1 where it refuses a file, writing neither, and prints on standard error the file's path and why, naming the record where the two sides hold one with different text. It exits 2 where either file is not under the working directory, writing nothing, and prints on standard error the path of each that is not.

### 4. Where it is said

The paragraph of `docs/adr/README.md` that says which number a new record takes names `node docs/merge-adr-index.mjs` as the way a merge whose conflicts lie in the index or in Adr.java is resolved. `.github/workflows/claims.yml` runs `node --test src/test/docs/merge-adr-index.test.mjs` as a step of its own, as it runs the claims check's own tests.

### 5. Run ids

No class under `src/main` changes, so no stage's version moves.

## Consequences

- **A branch adding a record, merged with a main that gained another, resolves both files with one command**, whichever of the two numbers is lower, both rows written at the end of the index's second table, where the conflict stood, and the first table and the heading, paragraph and table head above the second left where they stand.
- **A record that edits another record's row or constant, merged with a main that added one beside it, is refused.** Under git's default conflict style the hunk holds both texts and not the base's, so the script cannot tell which side changed the row, and that conflict is resolved by hand.
- **A merge resolved by hand halfway, a marker left outside any conflict, is refused by the script** rather than written with the marker in it and reported resolved.
- **A merge resolved by hand that leaves a marker, a row twice or a row out of place fails the claims job** of the pull request it is pushed to.
- **Putting Adr.java's constants in order once moves 32 of them**, 305 less the 273 of the longest sequence already in order, the fewest that can. A branch open at the time conflicts with that move only where it edits one of those 32.

## Alternatives refused

- **`merge=union` on the index.** A union merge never stops on a conflict: it keeps both sides' lines, the branch's before main's, so the rows come out of order wherever main's number is lower, and a row both sides edited is kept twice. Adr.java would still need a script, since a union of a conflict between two constants leaves the second's javadoc with no `/**` above it.
- **A merge driver named in `.gitattributes`.** The driver's command is set in each clone's own git configuration, which no committed file sets, and a merge GitHub makes runs no driver.
- **A file of its own for each record's row, the index generated from them.** The index is read as a page on github.com, and generating it adds a renderer and a staleness check for one table.
- **Holding the order only after the constant of ADR-225, leaving the constants up to it as they stand.** The script orders the constants it merges by number, and the file around them would then be ordered by something else.
- **Keeping main's side first and the branch's after.** A branch whose record is numbered below one main gained would then fail §2.
- **Writing every merged row where the first row stood.** The index's second table would be left with its heading and head and no row, and the claim of §2, reading the rows as one sequence, would still pass.

## Tests

- `src/test/docs/merge-adr-index.test.mjs` holds §3, on an index of two tables, as the index has, the records' rows in the second. In a git repository it builds, with no system or global configuration, a branch adding ADR-361 merged with a main that gained ADR-360, under each of git's conflict styles `merge`, `diff3` and `zdiff3`, is resolved to the whole of both files with ADR-360 before ADR-361 in the second table, exiting 0 and naming both paths; and a branch adding ADR-359 merged with a main that gained ADR-360 and ADR-361 is resolved with ADR-359 first. Outside any git repository, an index of CRLF lines is resolved with CRLF lines while an Adr.java with no conflict is left byte for byte; two files with no conflict are left byte for byte, exiting 0; rows merged at the end of the second table are written there, the first table and the second's heading, paragraph and head where they stood; a merged row numbered below the first row of both tables is written at the head of the first table, the other side's row in the second. A conflict outside the rows, a row the two sides word differently, a row one side holds where the other holds none, and a `>>>>>>>` with no conflict open, are refused with exit 1, naming the index and, for the second, the record, neither file written; and a folder holding no Adr.java exits 2 naming its path, nothing written.
- `src/test/docs/check-claims.test.mjs` holds §2. `adrOrderClaim` passes on rows and constants in order, the index's rows read across its two tables and two constants of one record among them, naming how many of each it read; it fails on a row after a higher one, on a record's row twice, on a constant after a higher one, on faults in both files, named the index's first, and on each of the three markers in the index and on one in Adr.java, naming the line. And `docs/check-claims.mjs`, run on this tree, reports the claim `PASS` with the numbers of rows and constants the test counts itself.
- Not held by a test: §4's sentence and the workflow's step.

## What this does not decide

- How a conflict the script refuses is resolved: by hand, as before.
- Whether every record has a row in the index, or a row's title and date agree with its record.
- Which of the index's two tables a row stands in. §2's claim reads the rows of both as one sequence, so that ADR-050's row written at the end of the first table, the second beginning at ADR-051, passes it; §3 gives a merged row to a table by its number alone.
- Whether a file other than these two, that every record edits, is ever given the same.
