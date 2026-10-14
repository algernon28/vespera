# Reviewing a change

A reviewer reads this before the diff. `AGENTS.md` is what an author reads, and it points here for tests.

A change to a decision record, to a stage's rule or to a table is judged against the record that decides it. A change to a test is judged against the seven standards below first, because a test written to prove a fix is the thing most often found wanting by the next pass over it: the same seven faults, in different tests.

## What a test is held to

### Can fail

Every claim has a change to the code it holds that turns it red. Ask what that change is. A claim that holds whatever the code does is not a claim: a `noneMatch` after an `anyMatch` of the same line, a state built so that no statement can use the thing it is meant to differ by, an assertion that a seed has no vectors when the seed has no chunks and so cannot have any. Show the red: take the line the claim holds out of the code, or write it the way the fault wrote it, and run the test.

### Says what it runs

The name, the `@DisplayName`, the javadoc and the claim say what the test runs, and every number in them is the constant the test uses: four thousand copies where the constant is 4,000, thirteen steps where the name says twelve. A test whose words are not its body is read as proof of something it does not show. The word is the one in `CONTEXT.md` (a cluster is not a group).

### Matches the whole thing

A claim about a plan, a statement or a name matches what the code is meant to produce, not a part of it that other things also contain: not a substring that any plan naming the column holds, not an index name SQLite generates and changes with a constraint, not a filter that two statements pass. Where a statement is meant to use a primary key on both columns, the claim says both columns.

### One claim, one place

A claim sits in the test of the decision it holds, and a test holds one decision. Two reads' plans in one test, or a decision's claim in a neighbour's test, leave the decision's own test passing when the decision is undone.

### Holds its decision

A decision a change records is held by a test that names it with `@Link` to its constant in `Adr`, and that test fails against the code before the change. A behaviour the change adds, down to the wait that is interrupted or the failure that is thrown, has a test, or the change says why it has none.

### Order and identity

Where the code pairs by position, by order or by identity, the claim holds that pairing and not the presence of the names: the labels of a count in the order of its columns, a row written over another run's row and not only a row deleted, an approval that holds for the identity it was given and not for any.

### Shares its setup

What is costly and the same for every claim of a class is built once for the class. A fixture resolved again for each of three tests to read one statement log is the same work three times.

## What this is not

It is not a list of style. A name, a comment's wording or a line's length is a nit, and says so.
