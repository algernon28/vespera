# ADR-259 — No document keeps a count of the decision records, and AGENTS.md says how a command is written under the guard

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Settles**: where the number of decision records is written, and that the habits which spare an agent a refusal by the guard are written in AGENTS.md.
- **Applies, and does not amend**: [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) and [ADR-215](0215-no-agent-writes-into-a-claude-folder-and-the-private-paths-guard-closes-each-one-but-for-what-it-names.md): what the guard refuses is as they decide it.

## Context

AGENTS.md wrote how many records there are and the highest number, and the index wrote the highest number. Each was kept current by hand and checked against the directory, so a record added edited the same two lines as every other record added, and two records added at once conflicted on both.

The guard refuses a command by what its text names. A command that spells a relative path with `..`, or whose text names a closed folder, is refused though it does nothing the guard forbids, and nothing in AGENTS.md said how to write one that is not.

## Decision

### 1. The count and the highest number are written nowhere

AGENTS.md and `docs/adr/README.md` do not state how many decision records there are or the number of the highest. The directory holds both: one file per record, the highest number last. The claims job fails a sentence in either document that brings a count or the highest number back.

### 2. AGENTS.md says three habits

A shell command names a file by its absolute path and never by `..`; a script too long for one line is written with the Write tool and run by its path; a transcript is read with Read or Grep. The claims job fails when AGENTS.md lacks one of the three.

### 3. Run ids

The change is to documents and the claims job. No class under `src/main` changes, so no stage's version moves.

## What this does not decide

- What the guard refuses: that is ADR-196, ADR-201, ADR-215, ADR-217 and ADR-233.
- Whether any other sentence of AGENTS.md or of the index is kept current by hand.
