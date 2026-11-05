# ADR-348 — The private-paths guard reads a variable with a plus sign as its word or as nothing, and never as the name's value

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Amends**: [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md) §5, in these places and no others:
  - its fourth item, "`${NAME-word}`, `${NAME=word}`, `${NAME+word}` and `${NAME?word}`, each with or without a colon before the sign, is a variable with a word for when it has no value", and its two readings, "with the word in the variable's place, and, when the name has a value, with that value in its place": `${NAME+word}` and `${NAME:+word}` are taken out of that item and read as §1 below has it; the item stands of the other six forms;
  - its fifth item's "A word that holds a variable is read by these same rules", which takes §1's rules for a `+` or `:+` inside a word.
- **Amends**: [ADR-233](0233-the-private-paths-guard-reads-what-stands-beside-the-name-claude-a-leading-tilde-in-a-field-and-a-link-out-of-its-own-folder.md), in these places and no others:
  - its "Read, and not executed", "`${NAME:-word}`, `${NAME-word}`, `${NAME=word}` and `${NAME+word}` substitute `word` when the name is unset (or, with the colon, unset or empty)": of `${NAME+word}` and `${NAME:+word}` the reverse holds (Context below);
  - §1's second point, "the start of a variable with a word for when it has no value", which names `${NAME+`: its rule stands, and its description is §2's below.
- **Keeps**: ADR-201 §5's two readings of `${NAME-word}`, `${NAME:-word}`, `${NAME=word}`, `${NAME:=word}`, `${NAME?word}` and `${NAME:?word}`; its word that runs to the brace that closes the variable; its three limits; and that these forms are read in a Bash command only (its Decided here 4). ADR-233 §1's rule, what may stand before the name `.claude`. [ADR-347](0347-in-a-git-command-the-private-paths-guard-reads-gits-index-form-as-the-path-it-names-and-keeps-the-drive-of-one-letter-and-the-name-of-spaces.md) whole: of a rest that holds `${NAME+word}` or `${NAME:+word}`, the two readings its §1 asks each of are §1's below (§3 below).

## Context

ADR-201 §5 reads `${NAME+word}` and `${NAME:+word}` as it reads `${NAME-word}`: with the word in the variable's place, as though the word were given where the name has no value, and with the name's value in its place where it has one. ADR-233's "Read, and not executed" says the same of `${NAME+word}`. Bash does the reverse. It gives the word where the name is set, and with the colon where it is set and not empty, and the empty string otherwise; it never gives the name's value.

So the guard read the word where Bash gives nothing, and the value where Bash gives the word. Where the word takes back a climb, the reading lets the climb through: `${N+a/}../../outside/doc.txt` is read as `a/../../outside/doc.txt`, a name one folder below where it starts, and where `N` is not set Bash gives `../../outside/doc.txt`, two folders up.

### Measured

**GNU bash 5.3.15, the Git Bash of this checkout, on Windows 11**: `echo "[${N+a/}x] [${N:+a/}x]"` printed `[x] [x]` with `N` not set, `[a/x] [x]` with `N` set to the empty string, and `[a/x] [a/x]` with `N` set to `v`.

**The draft guard at commit 4c16e3ae, on Windows, by `src/test/hooks/private-paths-guard.test.mjs`** with §5's cases added:

- it lets through G801, G802, G811 and G812, `cat ${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt` and the same with `:+`, from the checkout's `src` folder, with the name not set and set to the empty string. Bash gives `cat` `../../outside/doc.txt` in all four, a file outside the allow list;
- it lets through G821, G822, G831 and G832, `git show :${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt` and the same with `:+`, in the same four states. Bash gives git `:../../outside/doc.txt` in all four, whose rest lies outside the allow list;
- it refuses G844 and G854, `cat ${VESPERA_GUARD_PLUS+README.md}` and the same with `:+`, run from the checkout with the name set to the folder outside the allow list. Bash gives `cat` `README.md`, a file inside the checkout, or nothing;
- the other 1,135 cases pass, G803, G813, G823 and G833 among them, the same commands with the name set to `x`. They are refused by a cut and not by a reading of `+`: the value's reading put the variable alone, `${VESPERA_GUARD_PLUS}`, before `../../outside/doc.txt` with no separator between, so it was not replaced by its value, and the token was cut at its braces into a piece `../../outside/doc.txt`, which refuses.

### Read, and not executed

- The Bash reference manual, "Shell Parameter Expansion": "`${parameter:+word}` If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted"; and "Omitting the colon results in a test only for a parameter that is unset."
- The draft guard at commit 4c16e3ae off Windows: it reads a git segment's index token as written as well as by its rest (ADR-347 §1), and as written `:${N+a/}../../outside/doc.txt` is read by the same two readings, each a name `:a` that the two `..` after it take back, so G821, G822, G831 and G832 are let through there too.

## Decision

### 1. `${NAME+word}` and `${NAME:+word}` are read with the word and with nothing

**In a Bash command, a token that holds `${NAME+word}` or `${NAME:+word}` is read twice, wherever in it the variable stands: with the word in the variable's place, and with nothing there. Either refuses.** The name's value is never put in its place, since Bash never gives it.

**Both readings are made whatever value the name has in the hook's environment, and whether it has one.** The guard does not know whether the name is set when the command runs: the command can set it or unset it before the token, as `unset N;` or `N=;` does, and the shell's environment need not be the hook's, which is ADR-201 §5's "What it lets through". The colon changes when Bash gives the word, and so changes nothing the guard reads: `${NAME:+word}` is read as `${NAME+word}` is.

Where nothing stands in the variable's place, what stands before it and what follows its closing brace are joined, and read as one token. So from the checkout's `src` folder `cat ${N+a/}../../outside/doc.txt` is read as `a/../../outside/doc.txt` and as `../../outside/doc.txt`, and the second lies outside the allow list and refuses, whether `N` is not set, set to the empty string or set to a value. `cat ${N+README.md}` is read as `README.md` and as an empty token, which is no path; it is let through wherever `README.md` is, whatever `N`'s value names. A reading that begins with `/` once the variable is gone is read as `$NO_SUCH_VARIABLE/x` is, ADR-196 §5's rooted path.

What ADR-201 §5 says of the word stands for these two forms: it runs to the brace that closes the variable, counting the braces between, and a word that holds an expansion is read by these same rules, so that a `+` inside the word of a `-` gives that word its word and nothing as its two readings, and a `-` inside the word of a `+` its word and its value. A token that holds more than one variable with a word is still read twice, as ADR-201 §5 has it: once with each variable's word in its place, and once with each `-`, `=` or `?` variable's value where it has one, its word where it has none, and nothing in the place of each `+` or `:+` variable.

### 2. ADR-233 §1's rule is kept

`${NAME+` and `${NAME:+`, with the colon or without it, stay among what may stand before the name `.claude` in a token's text. Bash gives the word where the name is set, so `${X+.claude}/settings.json` names the folder then, as `${X-.claude}/settings.json` does where `X` is not set. Of `+`, §1's second point is "the start of a variable with a word", the word given where the name is set; of the other signs it is as ADR-233 wrote it.

### 3. ADR-347 is kept, and its Consequences' line stands of these forms

ADR-347 §1 asks of each of "a `${NAME-word}`'s two readings" of a rest whether it records a path, and counts the rest as a path only where each does. Of a rest that holds `${NAME+word}` or `${NAME:+word}`, those two readings are §1's: with the word and with nothing. So `git show :${N+a/}../../outside/doc.txt` is read by its rest as `a/../../outside/doc.txt` and as `../../outside/doc.txt`, and the second refuses, on every platform.

ADR-347's Consequences say "A rest outside the allow list is refused on every platform", and name no exception for a variable with a word. Of `+` and `:+` the line was not true of the draft at commit 4c16e3ae (Measured; and Read, and not executed, off Windows). This record makes it true of them, and ADR-347 is not edited: its line stands as written, unqualified.

### 4. Elsewhere nothing changes

In a PowerShell command `${N+a/}` is a variable whose name is `N+a/`, and it is read as before, as ADR-201 §5 has `${Q:-x}`. ADR-201 §5's third limit, the word after an unbraced variable that is not read, holds of `+` and `:+` too, and stays on ADR-201 §8's list. ADR-196 §5's list of what the hook does not cover gains nothing and loses nothing.

### 5. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 36 cases, G801 to G894, each of the first three in a group started with `VESPERA_GUARD_PLUS` not set, set to the empty string and set to a value, through the case's environment, so that none depends on the machine's:

- **G801 to G803 and G811 to G813**, refused on every platform: `cat ${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt`, and the same with `:+`, from the checkout's `src` folder, the value being `x`, a name inside the checkout, so that no reading of the value is what refuses;
- **G821 to G823 and G831 to G833**, refused on every platform: `git show :${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt`, and the same with `:+`, from `src`, git's index form (§3);
- **G841 to G843 and G851 to G853**, let through on every platform: `cat ${VESPERA_GUARD_PLUS+a/}README.md`, and the same with `:+`, from the checkout, each of whose two readings names a file inside it, the value being the folder outside the allow list, so that a reading of the value would refuse;
- **G844 and G854**, let through on every platform: `cat ${VESPERA_GUARD_PLUS+README.md}`, and the same with `:+`, the name set to the folder outside the allow list;
- **G861 to G894**, refused on every platform, ADR-201 §5's reading of the other signs kept: for each of `-`, `:-`, `=` and `?`, `cat ${VESPERA_GUARD_PLUS<sign>..}/../outside/doc.txt` from `src` in the three states, the value being `x`, whose word climbs out of the allow list (G861 to G863, G871 to G873, G881 to G883, G891 to G893); and `cat ${VESPERA_GUARD_PLUS<sign>x}/doc.txt` from the checkout, the name set to the folder outside the allow list, whose value's reading refuses (G864, G874, G884, G894).

Against the draft at commit 4c16e3ae, on Windows, G801, G802, G811, G812, G821, G822, G831 and G832 are let through, G844 and G854 refused, and the rest pass (Measured).

## Decided here

1. **§1: `+` and `:+` are read with the word and with nothing, whatever the hook's environment holds.** The value in the hook's environment does not say the shell's, and a command can change it before the token; reading only the form Bash would give with the hook's value would let through `unset N; cat ${N+a/}../../outside/doc.txt` where the hook's `N` is set.
2. **§1: the name's value is not read in the place of `+` or `:+`.** Bash never puts it there, so no program is given a path that reading makes. G844 and G854 are the one kind of call the draft at commit 4c16e3ae refused and this record lets through: the variable alone, or with what follows it, where only its value lies outside the allow list.
3. **§3: ADR-347's Consequences' line is left as written** and made true of these forms by this record, as a later record and not an edit of the earlier one settles it.

## Consequences

- **`cat ${N+a/}../../outside/doc.txt`, `cat ${N:+a/}../../outside/doc.txt` and `git show :${N+a/}../../outside/doc.txt` are refused on every platform**, from a folder one below an allowed root, whatever `N`'s value is in the hook's environment and whether it has one.
- **A rest outside the allow list is refused on every platform**, ADR-347's line, of a rest that holds `${NAME+word}` or `${NAME:+word}` too.
- **`cat ${N+README.md}` is let through where `N`'s value names a folder outside the allow list**, as Bash gives `cat` `README.md` or nothing.
- **`${NAME-word}`, `${NAME:-word}`, `${NAME=word}` and `${NAME?word}`, and each with the colon, are read as ADR-201 has them.**
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Keeping the value's reading as a third reading of `+`**, beside the word and nothing. It refuses more, and only where the value lies outside the allow list. Rejected: Bash never gives the value in that place, so the reading names no path any program is given, and it costs a refusal of `cat ${N+README.md}` wherever `N` names a folder outside the allow list.
- **Reading `+` by the hook's environment alone**, the word where the hook's `N` is set and nothing where it is not. Rejected: Decided here 1.
- **Reading `:+` apart from `+`**, the word where the hook's value is not empty. Rejected with the one before: the colon changes when Bash gives the word, which the guard does not know at the hook's time.

## What this does not decide

- Whether a token that holds more than one variable with a word is read in every combination of their readings, and not in the two ADR-201 §5 makes.
- ADR-201 §5's two other limits, which this record keeps.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): `PARAMETER_WITH_WORD` and `variantsOf`, so that a `+` or `:+` variable gives the word to the first reading and nothing to the second, and never its value, and their comments, which say the word is "for when the variable has no value" and that the second reading holds the value where the name has one; the header comment of `gitIndexForms`, which names "a `${NAME-word}`'s two readings". No reading of any other form changes, and no call refused at commit 4c16e3ae is let through but G844's and G854's kind (Decided here 2).
2. `src/test/hooks/private-paths-guard.test.mjs`: §5's cases and its header comment, written before the guard.
3. ADR-201 and ADR-233 take a pointer to this record at their top. Their decisions are not edited.
4. `docs/agents/private-paths-guard.md`: the sentence on what the hook reads in a shell command, and the list of the records that amend ADR-196.
