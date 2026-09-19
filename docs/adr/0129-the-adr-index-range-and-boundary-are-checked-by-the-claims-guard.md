# ADR-129 — The decision record's index states its range and its boundary, and the claims guard checks both

- **Date**: 2026-09-19
- **Status**: accepted

## Context

`docs/adr/README.md` is the index a reader lands on when they open the decision record. It made two claims about that record, and both were false.

**The range was behind the files.** The line read *"One file per architecture decision, ADR-001 through ADR-121."* The directory held 122 records, ADR-001 to ADR-122, when this was measured; the ticket that surfaced the defect measured 124 on `main`, which is the same defect at a different moment rather than a different defect. This record is ADR-129 — ADR-127 and ADR-128 are being written on parallel branches, so the range this branch states runs through ADR-129 with a temporary gap beneath it.

**The full-text boundary named the wrong number entirely.** The line read *"New decisions continue from ADR-065 and carry their own full text."* The boundary is ADR-050. Measured: 49 files carry no `## Context` section — ADR-001 to ADR-049 — and ADR-050 is the first that does. `AGENTS.md` states the boundary correctly —

> **ADR-001 to ADR-049 are reconstituted records.** … **ADR-050 onward carry their own full text**

— and `docs/check-claims.mjs` verifies that sentence against the tree, printing `the full-text boundary: reconstituted through ADR-049, full text from ADR-050`. So the two documents disagreed by fifteen records, the checked one was right, and the unchecked one is what a reader opening `docs/adr/` finds.

**Why it rotted, and why correcting the text is not the fix.** `check-claims.mjs` named two inputs, `AGENTS.md` and `README.md`, and opened no third document. `AGENTS.md`'s count line is correct after every ADR precisely because a check fails when it is not. The index's equivalent line had drifted because nothing did. Correcting the line by hand leaves the next ADR free to break it again. This is ADR-098's concern — two documents describing the same thing is drift that has already cost two pull requests — reaching a document ADR-098's own remedy does not cover.

**The two claims are not the same kind of claim.** The range changes with every ADR and is stale the moment one lands. The boundary changes never: the reconstitution stopped at ADR-049, and no future decision can move it. They want the same guard for different reasons, and are checked for different things.

## Decision

**`docs/adr/README.md` becomes a third input to `docs/check-claims.mjs`.** The checker already reads a claim out of a document and asks whether the tree agrees; the index is a third document making such claims, and its claims are derivable from the directory the boundary check already reads.

**The range line is checked for currency.** The last id it names must equal the highest ADR id on disk. This is the claim that goes stale on every ADR, and it fails the way `AGENTS.md`'s count fails — a new record with the line untouched.

**The boundary sentence is checked for agreement.** It must name the same number as `AGENTS.md`'s own `ADR-050 onward` sentence, which is itself checked against the `## Context` marker. This is the one check in the file that reads two documents against each other rather than one against the tree, and it is the specific failure that motivated this record: `docs/adr/README.md` said ADR-065 while `AGENTS.md` said ADR-050, and neither disagreement reached a red build.

**The index table stays hand-maintained.** Each ADR adds its own row, as before; nothing here generates the file. The checker reads the range line and the boundary sentence and leaves the table alone, and the checker's `UNCHECKED` list says so.

**The two other shapes are refused, for different reasons.**

- **Derive the lines instead of stating them** is refused because nothing regenerates this file. `render-docs.mjs` renders `docs/architecture.md` and `docs/decision-ledger.md`; `docs/adr/README.md` is authored, and its table already is. Deriving two lines inside a hand-authored file means either a generator no commit runs, or a hybrid in which part of a hand-maintained page is overwritten. `AGENTS.md`'s count line is stated and checked; this is the same remedy in the same checker.
- **Strike the lines and point at `AGENTS.md`** is refused because it trades a false claim for no orientation, and the guard has removed the drift the striking would prevent. The reader who opens `docs/adr/` directly — the reader this file exists for — loses the count, and loses the sentence that says why 49 records look unlike the rest. ADR-098's division is between the operator's `README.md` and the agent's `AGENTS.md`; a third document that indexes the decision record and states its own scope does not fall under it, and once checked it carries no drift risk that removing it would avoid.

## Consequences

**`check-claims.mjs` checks three documents, and its header now says so.** The count it prints at the end grows by two.

**A branch that adds an ADR without touching the index now fails the claims job** — the behaviour `AGENTS.md`'s count has had since the checker was written.

**The boundary can no longer be wrong in one document and right in the other.** A disagreement fails the new check; a bad number in `AGENTS.md` fails the existing one; a tree that matches neither fails both.

**The index table's rows remain unchecked.** A row pointing at a renamed file, or missing for a record that exists, passes. That is named in the checker's `UNCHECKED` list; it is the precedent this record keeps, not a defect it introduces.

**The range and count lines are shared with every other ADR branch.** Two lines in `docs/adr/README.md` and one in `AGENTS.md` change whenever a record lands, so parallel branches conflict on them by construction. The edits are mechanical — the id and the count — and a merge keeps the highest.