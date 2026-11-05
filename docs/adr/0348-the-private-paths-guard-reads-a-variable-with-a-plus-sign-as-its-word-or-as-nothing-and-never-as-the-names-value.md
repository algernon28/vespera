# ADR-348 — The private-paths guard reads a variable with a plus sign as its word or as nothing, and never as the name's value

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Amends**: [ADR-201](0201-the-private-paths-guard-reads-a-climb-out-of-a-link-both-ways-and-refuses-a-name-it-cannot-follow.md) §5, in these places and no others:
  - its fourth item, "`${NAME-word}`, `${NAME=word}`, `${NAME+word}` and `${NAME?word}`, each with or without a colon before the sign, is a variable with a word for when it has no value", and its two readings, "with the word in the variable's place, and, when the name has a value, with that value in its place": `${NAME+word}` and `${NAME:+word}` are taken out of that item and read as §1 below has it; the item stands of the other six forms;
  - its fifth item's "A word that holds a variable is read by these same rules", which does not hold of a token that holds a `+` or `:+` and another variable with a word: that token is refused (§1).
- **Amends**: [ADR-233](0233-the-private-paths-guard-reads-what-stands-beside-the-name-claude-a-leading-tilde-in-a-field-and-a-link-out-of-its-own-folder.md), in these places and no others:
  - its "Read, and not executed", "`${NAME:-word}`, `${NAME-word}`, `${NAME=word}` and `${NAME+word}` substitute `word` when the name is unset (or, with the colon, unset or empty)": of `${NAME+word}` and `${NAME:+word}` the reverse holds (Context below);
  - §1's second point, "the start of a variable with a word for when it has no value", which names `${NAME+`: its rule stands, and its description is §2's below.
- **Keeps**: ADR-201 §5's two readings of `${NAME-word}`, `${NAME:-word}`, `${NAME=word}`, `${NAME:=word}`, `${NAME?word}` and `${NAME:?word}`, of a token that holds no `+` or `:+`; its word that runs to the brace that closes the variable; its three limits; and that these forms are read in a Bash command only (its Decided here 4). ADR-233 §1's rule, what may stand before the name `.claude`. [ADR-347](0347-in-a-git-command-the-private-paths-guard-reads-gits-index-form-as-the-path-it-names-and-keeps-the-drive-of-one-letter-and-the-name-of-spaces.md) whole: of a rest that holds `${NAME+word}` or `${NAME:+word}`, the two readings its §1 asks each of are §1's below, and a rest that holds one beside or around another variable with a word is refused with its token (§3 below).

## Context

ADR-201 §5 reads `${NAME+word}` and `${NAME:+word}` as it reads `${NAME-word}`: with the word in the variable's place, as though the word were given where the name has no value, and with the name's value in its place where it has one. ADR-233's "Read, and not executed" says the same of `${NAME+word}`. Bash does the reverse. It gives the word where the name is set, and with the colon where it is set and not empty, and the empty string otherwise; it never gives the name's value.

So the guard read the word where Bash gives nothing, and the value where Bash gives the word. Where the word takes back a climb, the reading lets the climb through: `${N+a/}../../outside/doc.txt` is read as `a/../../outside/doc.txt`, a name one folder below where it starts, and where `N` is not set Bash gives `../../outside/doc.txt`, two folders up.

Two readings of a token are the forms Bash gives it only where it holds one variable with a word. Where it holds a `+` and another variable with a word, Bash gives one form for each combination of their states, set or not, and a `+` whose word holds a `-` gives the `-` variable's value where both names are set: `${A+${B-}}/doc.txt` is `B`'s value and `/doc.txt` where `A` is set. Where `+` was read as `-`, the value's reading caught some of those forms, the inner value or a cut of the outer name: the draft of commit 840baa5e, which took that reading away and read the rest of ADR-201 §5 unchanged, lets them through.

### Measured

**GNU bash 5.3.15, the Git Bash of this checkout, on Windows 11**: `echo "[${N+a/}x] [${N:+a/}x]"` printed `[x] [x]` with `N` not set, `[a/x] [x]` with `N` set to the empty string, and `[a/x] [a/x]` with `N` set to `v`. With `B` set to `/o`, `${A+${B-}}/doc.txt` printed `/doc.txt` with `A` not set and `/o/doc.txt` with `A` set to the empty string or to `x`; `${A:+${B-}}/doc.txt` printed `/o/doc.txt` with `A` set to `x` only. `${A+..}${B+..}/../outside/doc.txt`, and the same with `:+`, printed `../../outside/doc.txt` with one of `A` and `B` set to `x` and the other not set, `..../../outside/doc.txt` with both set, and `/../outside/doc.txt` with neither.

**The draft guard at commit 4c16e3ae, on Windows, by `src/test/hooks/private-paths-guard.test.mjs`** with §5's cases added:

- it lets through G801, G802, G811 and G812, `cat ${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt` and the same with `:+`, from the checkout's `src` folder, with the name not set and set to the empty string. Bash gives `cat` `../../outside/doc.txt` in all four, a file outside the allow list;
- it lets through G821, G822, G831 and G832, `git show :${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt` and the same with `:+`, in the same four states. Bash gives git `:../../outside/doc.txt` in all four, whose rest lies outside the allow list;
- it refuses G844 and G854, `cat ${VESPERA_GUARD_PLUS+README.md}` and the same with `:+`, run from the checkout with the name set to the folder outside the allow list. Bash gives `cat` `README.md`, a file inside the checkout, or nothing;
- it lets through G903, `cat ${VESPERA_GUARD_PLUS+${VESPERA_GUARD_NAME-}}/doc.txt` with `VESPERA_GUARD_PLUS` set to `x` and `VESPERA_GUARD_NAME` to the folder outside the allow list. It read the token as `/doc.txt`, a rooted path it does not read, and as `${VESPERA_GUARD_PLUS}/doc.txt`, a file inside the checkout; Bash gives `cat` the outside folder's `doc.txt`. It refuses G901 and G902, the same with `VESPERA_GUARD_PLUS` not set and set to the empty string, by the reading that put the inner value in the place of the `+`;
- the other 1,144 cases pass, G803, G813, G823 and G833 among them, the same commands as G801 to G832 with the name set to `x`, and G921 to G932. They are refused by a cut and not by a reading of `+`: the value's reading put the variable alone, `${VESPERA_GUARD_PLUS}`, before what follows it with no separator between, so it was not replaced by its value, and the token was cut at its braces into a piece that begins `..`, which refuses.

**The draft guard at commit 840baa5e, on Windows, by the same file**: it lets through G901 to G903, G921, G922, G931 and G932, and the other 1,148 cases pass. It reads G921's token as `..../../outside/doc.txt`, a name inside the checkout, and as `/../outside/doc.txt`, a rooted path; Bash gives `cat` `../../outside/doc.txt`. It refuses G911 to G913, whose rest it reads as `/doc.txt` in both readings, which records no path, so that the token is read as written too and refused there as one that begins with a colon (ADR-347 §1).

### Read, and not executed

- The Bash reference manual, "Shell Parameter Expansion": "`${parameter:+word}` If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted"; and "Omitting the colon results in a test only for a parameter that is unset."
- The draft guard at commit 4c16e3ae off Windows: it reads a git segment's index token as written as well as by its rest (ADR-347 §1), and as written `:${N+a/}../../outside/doc.txt` is read by the same two readings, each a name `:a` that the two `..` after it take back, so G821, G822, G831 and G832 are let through there too.
- The draft guard at commit 840baa5e off Windows: it reads G911's token as written, `:/doc.txt` in both readings, a name `:` inside the checkout, and its rest `/doc.txt` is a rooted path it does not read, so G911 to G913 are let through there.

## Decision

### 1. `${NAME+word}` and `${NAME:+word}` are read with the word and with nothing, and a token with another variable with a word is refused

**In a Bash command, a token that holds `${NAME+word}` or `${NAME:+word}`, and no other variable with a word, is read twice, wherever in it the variable stands: with the word in the variable's place, and with nothing there. Either refuses.** The name's value is never put in its place, since Bash never gives it.

**Both readings are made whatever value the name has in the hook's environment, and whether it has one.** The guard does not know whether the name is set when the command runs: the command can set it or unset it before the token, as `unset N;` or `N=;` does, and the shell's environment need not be the hook's, which is ADR-201 §5's "What it lets through". The colon changes when Bash gives the word, and so changes nothing the guard reads: `${NAME:+word}` is read as `${NAME+word}` is.

Where nothing stands in the variable's place, what stands before it and what follows its closing brace are joined, and read as one token. So from the checkout's `src` folder `cat ${N+a/}../../outside/doc.txt` is read as `a/../../outside/doc.txt` and as `../../outside/doc.txt`, and the second lies outside the allow list and refuses, whether `N` is not set, set to the empty string or set to a value. `cat ${N+README.md}` is read as `README.md` and as an empty token, which is no path; it is let through wherever `README.md` is, whatever `N`'s value names. A reading that begins with `/` once the variable is gone is read as `$NO_SUCH_VARIABLE/x` is, ADR-196 §5's rooted path.

**A Bash token that holds a `${NAME+word}` or `${NAME:+word}` and any other variable with a word is refused, and is not read.** The other variable may stand in the `+` variable's word, as in `${A+${B-}}/doc.txt`; around it, as in `${B-${A+..}}`; or beside it, before or after and with or without text between, as in `${A+..}${B+..}/../outside/doc.txt` or `${A+..}/x/${B-y}`. A variable with a word is ADR-201 §5's, any of the eight forms with its closing brace, at any depth; a variable with no word, `$NAME` or `${NAME}`, is not one. Each variable's state is the shell's, which the guard does not know (above), so such a token has a form for each combination of their states, four for two `+` variables side by side, and a `+` whose word holds a `-` gives the inner value where both names are set; two readings give two of those forms. Refused is what the guard does of it whatever the hook's environment holds and whatever each form would name. A token that holds no `+` or `:+` is read as ADR-201 §5 has it, with every variable's word in one reading and every value, where the name has one, in the other.

The word of a `+` or `:+` runs to the brace that closes the variable, counting the braces between, as ADR-201 §5 has it. A word that holds a variable with no word is read by these same rules: `${A+$HOME/x}` is read as `$HOME/x` and as nothing.

This applies to every text the guard reads by these readings in a Bash command: a token, a quoted string, a piece of a token, and the rest of git's index form (§3).

### 2. ADR-233 §1's rule is kept

`${NAME+` and `${NAME:+`, with the colon or without it, stay among what may stand before the name `.claude` in a token's text. Bash gives the word where the name is set, so `${X+.claude}/settings.json` names the folder then, as `${X-.claude}/settings.json` does where `X` is not set. Of `+`, §1's second point is "the start of a variable with a word", the word given where the name is set; of the other signs it is as ADR-233 wrote it.

### 3. ADR-347 is kept, and its Consequences' line stands of these forms

ADR-347 §1 asks of each of "a `${NAME-word}`'s two readings" of a rest whether it records a path, and counts the rest as a path only where each does. Of a rest that holds `${NAME+word}` or `${NAME:+word}` and no other variable with a word, those two readings are §1's: with the word and with nothing. So `git show :${N+a/}../../outside/doc.txt` is read by its rest as `a/../../outside/doc.txt` and as `../../outside/doc.txt`, and the second refuses, on every platform. A token whose rest holds a `+` or `:+` and another variable with a word is refused by §1, on every platform, whatever ADR-347 §1 would ask of it: `git show :${A+..}${B+..}/../outside/doc.txt` and `git show :${A+${B-}}/doc.txt` among them.

ADR-347's Consequences say "A rest outside the allow list is refused on every platform", and name no exception for a variable with a word. Of `+` and `:+` the line was not true of the draft at commit 4c16e3ae (Measured; and Read, and not executed, off Windows), nor of the draft at commit 840baa5e, which let through `git show :${A+..}${B+..}/../outside/doc.txt` where Bash gives git `:../../outside/doc.txt`. This record makes it true of them, and ADR-347 is not edited: its line stands as written, unqualified.

### 4. Elsewhere nothing changes

In a PowerShell command `${N+a/}` is a variable whose name is `N+a/`, and it is read as before, as ADR-201 §5 has `${Q:-x}`; §1's refusal is of a Bash command only. ADR-201 §5's third limit, the word after an unbraced variable that is not read, holds of `+` and `:+` too, and stays on ADR-201 §8's list. ADR-196 §5's list of what the hook does not cover gains nothing and loses nothing.

### 5. The test

`src/test/hooks/private-paths-guard.test.mjs` gains 46 cases, G801 to G932, each of the first three in a group of G8 and of G90 and G91 started with `VESPERA_GUARD_PLUS` not set, set to the empty string and set to a value, through the case's environment, so that none depends on the machine's:

- **G801 to G803 and G811 to G813**, refused on every platform: `cat ${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt`, and the same with `:+`, from the checkout's `src` folder, the value being `x`, a name inside the checkout, so that no reading of the value is what refuses;
- **G821 to G823 and G831 to G833**, refused on every platform: `git show :${VESPERA_GUARD_PLUS+a/}../../outside/doc.txt`, and the same with `:+`, from `src`, git's index form (§3);
- **G841 to G843 and G851 to G853**, let through on every platform: `cat ${VESPERA_GUARD_PLUS+a/}README.md`, and the same with `:+`, from the checkout, each of whose two readings names a file inside it, the value being the folder outside the allow list, so that a reading of the value would refuse;
- **G844 and G854**, let through on every platform: `cat ${VESPERA_GUARD_PLUS+README.md}`, and the same with `:+`, the name set to the folder outside the allow list;
- **G861 to G894**, refused on every platform, ADR-201 §5's reading of the other signs kept: for each of `-`, `:-`, `=` and `?`, `cat ${VESPERA_GUARD_PLUS<sign>..}/../outside/doc.txt` from `src` in the three states, the value being `x`, whose word climbs out of the allow list (G861 to G863, G871 to G873, G881 to G883, G891 to G893); and `cat ${VESPERA_GUARD_PLUS<sign>x}/doc.txt` from the checkout, the name set to the folder outside the allow list, whose value's reading refuses (G864, G874, G884, G894);
- **G901 to G903 and G911 to G913**, refused on every platform: `cat ${VESPERA_GUARD_PLUS+${VESPERA_GUARD_NAME-}}/doc.txt` and `git show :${VESPERA_GUARD_PLUS+${VESPERA_GUARD_NAME-}}/doc.txt`, from the checkout, `VESPERA_GUARD_NAME` set to the folder outside the allow list, a `-` in the word of a `+` (§1);
- **G921, G922, G931 and G932**, refused on every platform: `cat ${VESPERA_GUARD_PLUS+..}${VESPERA_GUARD_NAME+..}/../outside/doc.txt`, the same with `:+` in both places, and each under `git show :`, from `src`, `VESPERA_GUARD_PLUS` set to `x` and `VESPERA_GUARD_NAME` not set, two `+` side by side (§1).

Against the draft at commit 4c16e3ae, on Windows, G801, G802, G811, G812, G821, G822, G831, G832 and G903 are let through, G844 and G854 refused, and the rest pass. Against the draft at commit 840baa5e, on Windows, G901 to G903, G921, G922, G931 and G932 are let through, and the rest pass (Measured).

## Decided here

1. **§1: `+` and `:+` are read with the word and with nothing, whatever the hook's environment holds.** The value in the hook's environment does not say the shell's, and a command can change it before the token; reading only the form Bash would give with the hook's value would let through `unset N; cat ${N+a/}../../outside/doc.txt` where the hook's `N` is set.
2. **§1: the name's value is not read in the place of `+` or `:+`.** Bash never puts it there, so no program is given a path that reading makes. The one kind of call the draft at commit 4c16e3ae refused and this record lets through is a token whose one variable with a word is a `+` or `:+`, refused there only by the reading that put the name's value, or the name alone in braces, in its place, or cut its braces apart: G844 and G854 are of it, the variable alone, or with what follows it, where only its value lies outside the allow list. Every token that holds a `+` or `:+` and another variable with a word is refused, and so is not of that kind.
3. **§1: a token that holds a `+` or `:+` and another variable with a word is refused, and not read in every combination of their states.** The combinations are 2 to the power of the number of `+` variables, more where a word nests a `-`, and reading them would take a bound under ADR-196 §6 and a refusal past it; a third reading, the `+` word with its nested values, would answer the nesting and not two `+` side by side. Such a token is rare in a command an agent writes, and a refusal of it costs a rewrite of one token.
4. **§3: ADR-347's Consequences' line is left as written** and made true of these forms by this record, as a later record and not an edit of the earlier one settles it.

## Consequences

- **`cat ${N+a/}../../outside/doc.txt`, `cat ${N:+a/}../../outside/doc.txt` and `git show :${N+a/}../../outside/doc.txt` are refused on every platform**, from a folder one below an allowed root, whatever `N`'s value is in the hook's environment and whether it has one.
- **`cat ${A+${B-}}/doc.txt`, `cat ${A+..}${B+..}/../outside/doc.txt` and each under `git show :` are refused on every platform**, whatever the hook's environment holds, and so is every Bash token that holds a `+` or `:+` and another variable with a word, `cat ${A+x}${B-y}` among them, where every form Bash can give lies inside the allow list.
- **A rest outside the allow list is refused on every platform**, ADR-347's line, of a rest that holds `${NAME+word}` or `${NAME:+word}` too.
- **`cat ${N+README.md}` is let through where `N`'s value names a folder outside the allow list**, as Bash gives `cat` `README.md` or nothing.
- **`${NAME-word}`, `${NAME:-word}`, `${NAME=word}` and `${NAME?word}`, and each with the colon, are read as ADR-201 has them**, in a token that holds no `+` or `:+`.
- **Nothing under `src/main` changes, and no run id moves.**

## Alternatives weighed

- **Keeping the value's reading as a third reading of `+`**, beside the word and nothing. It refuses more, and only where the value lies outside the allow list. Rejected: Bash never gives the value in that place, so the reading names no path any program is given, and it costs a refusal of `cat ${N+README.md}` wherever `N` names a folder outside the allow list.
- **Reading `+` by the hook's environment alone**, the word where the hook's `N` is set and nothing where it is not. Rejected: Decided here 1.
- **Reading `:+` apart from `+`**, the word where the hook's value is not empty. Rejected with the one before: the colon changes when Bash gives the word, which the guard does not know at the hook's time.
- **Reading a token that holds a `+` and another variable with a word in every combination of their states**, bounded under ADR-196 §6 and refused past the bound. Rejected: Decided here 3.
- **A third reading for a word nested in a `+`**, the word with its nested values, Bash's form where the name is set. Not the value reading the first alternative rejects. Rejected: Decided here 3; it leaves two `+` side by side read in two of their four forms.

## What this does not decide

- Whether a token that holds more than one variable with a word, none of them a `+` or `:+`, is read in every combination of their readings, and not in the two ADR-201 §5 makes.
- ADR-201 §5's two other limits, which this record keeps.

## What the implementation changes

1. `.claude/hooks/private-paths-guard.mjs`, drafted as `docs/agents/guard-draft/hooks/private-paths-guard.mjs` and put in place by the operator (ADR-215 §7): `PARAMETER_WITH_WORD` and `variantsOf`, so that a `+` or `:+` variable gives the word to the first reading and nothing to the second, and never its value, and so that a text that holds a `+` or `:+` and another variable with a word, at any depth, is refused wherever the guard reads a Bash text by these readings, the rest of git's index form among them; and their comments, which say the word is "for when the variable has no value" and that the second reading holds the value where the name has one; the header comment of `gitIndexForms`, which names "a `${NAME-word}`'s two readings". No reading of any other form changes, and no call refused at commit 4c16e3ae is let through but Decided here 2's kind.
2. `src/test/hooks/private-paths-guard.test.mjs`: §5's cases and its header comment, written before the guard.
3. ADR-201 and ADR-233 take a pointer to this record at their top. Their decisions are not edited.
4. `docs/agents/private-paths-guard.md`: the sentence on what the hook reads in a shell command, and the list of the records that amend ADR-196.
