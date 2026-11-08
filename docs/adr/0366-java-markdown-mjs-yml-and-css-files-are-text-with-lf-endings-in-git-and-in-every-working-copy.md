# ADR-366 — Java, Markdown, .mjs, .yml and .css files are text with LF endings, in git and in every working copy

- **Date**: 2026-11-07
- **Status**: accepted on 2026-11-07.
- **Settles**: [#767](https://github.com/algernon28/vespera/issues/767), the line endings of the five kinds of file this repository's work writes most.
- **Keeps**: [ADR-346](0346-agents-md-is-measured-as-git-stores-it-and-a-new-decision-record-takes-a-number-no-other-holds.md) §1, AGENTS.md measured as git stores it, and the line `/AGENTS.md text` its test reads; `/mvnw`'s, the Containerfiles' and the hooks' `*.sh` rules, `*.cmd text eol=crlf` and `*.pdf binary`.

## Context

`.gitattributes` named `/mvnw`, `/AGENTS.md`, `*.cmd`, the Containerfiles, `*.pdf` and the hooks' `*.sh`, and no `* text=auto`. A `.java`, `.md`, `.mjs`, `.yml` or `.css` file carried no `text` attribute, so its line endings followed each clone's `core.autocrlf` and whichever tool wrote the file.

Measured on a clone with `core.autocrlf=true`, git's default on Windows: `git ls-files --eol` reads every stored file of the five kinds `i/lf`; the files git checked out are `w/crlf` in the working copy, and a file a tool writes with LF is `w/lf`. Staging such a file prints `warning: in the working copy of '<path>', LF will be replaced by CRLF the next time Git touches it`, once for every new file.

Measured in a scratch repository with `core.autocrlf=true`, staging a new LF `.java` file:

| `.gitattributes` | Warning |
| --- | --- |
| nothing | `LF will be replaced by CRLF` |
| `*.java text` | `LF will be replaced by CRLF` |
| `*.java text eol=lf` | none |

`text` alone fixes what git stores, which is LF already, and leaves the working copy to `core.autocrlf`, which on Windows is CRLF: an LF file is still one git will rewrite, and still warned of. Only `eol=lf` makes LF the working copy's ending as well.

## Decision

### 1. The rules

`.gitattributes` sets `text eol=lf` on `*.java`, `*.md`, `*.mjs`, `*.yml` and `*.css`. git stores each with LF whatever its writer wrote, and writes each to the working copy with LF whatever `core.autocrlf` says, so a file a tool writes with LF is staged with no warning on any machine.

The existing rules stay as they stand. `/AGENTS.md text` stays beside `*.md text eol=lf`, which sets the same `text` on it and LF in the working copy; ADR-346 §1 reads the line, and its measure counts a CRLF as the one LF git stores either way.

### 2. What changes in a clone

No stored file changes: every one of the five kinds is stored `i/lf`, so `git add --renormalize .` changes no blob. A clone's existing CRLF working copies are left as they are until git next writes them, by a checkout, a merge or a reset, which writes them with LF; a file written with CRLF in the meantime is staged as LF, with git warning `CRLF will be replaced by LF`, the one line-ending warning these rules leave: on an existing file written with CRLF, not on a new one.

### 3. Run ids

No run id moves: no stage's code changes.

## Alternatives refused

- **`text` without `eol`.** It leaves the warning on every new file on Windows, the cost #767 names, as the table above shows.
- **`* text=auto eol=lf`.** It would bind every kind of file the repository holds, among them the `*.cmd` files, which `eol=crlf` keeps CRLF, and the generated `.html` pages, on a guess of git's at each file; the five kinds named are the ones this work writes.

## Consequences

- A new file of the five kinds staged in any clone draws no line-ending warning.
- A clone's working copy of these files becomes LF as git rewrites it, on Windows as elsewhere.

## What this does not decide

- The line endings of the kinds not named: `.yaml`, `.xml`, `.json`, `.html`, `.properties`, `.sql` and the rest keep following `core.autocrlf`. The tree holds 1,616 files of the five kinds named and 42 of all the others; a kind is added by a record of its own when its files draw the warning.

## Tests

- `src/test/docs/check-claims.test.mjs` holds §1 and the first sentence of §2. `git check-attr` reads `text: set` and `eol: lf` for a new file of each of the five kinds; in a repository of its own set to `core.autocrlf=true` and holding this tree's `.gitattributes`, a new LF file of each kind is staged with no `will be replaced by` warning; and every tracked file of the five kinds is stored `i/lf` or `i/none`, so renormalising changes no blob.
- Not held by a test: §2's account of a clone's existing CRLF working copies, which is git's behaviour under the rules.
