# ADR-295 — The control panel writes profile values, label answers and code-set names holding the working-directory lock, one operator action at a time

> **Partly amended — see [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md).** Of §3: `seedFolder`'s value loses its trailing white space alone, every other key's both ends. Of §7: before these refusals, a Save at `/code-sets` refuses a `new-folder` that is absolute, holds a `..` element or holds a backslash, with one line, and nothing is written. `CodeSetRenames.render`'s refusals stand as the jar's `CodeSetNames` gives them, and a saved row's folder is written back as the file holds it. The rest of this record stands.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md), [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md) and [ADR-120](0120-a-profile-values-type-is-its-keys-and-an-unreadable-value-is-a-third-state-beside-unset.md): a profile value is `value`, `provenance` and `measurement` under its key; a key the file does not hold is unset at the next load; a value the key cannot use is unreadable, a state beside unset. The control panel writes no unreadable value (§4) and keeps `measurement` as it is (§3).
- **Keeps**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md), with ADR-169 and [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md): `relevance-labels.yaml` is written by the jar, stamped with the run, the embedder and the seed set it was generated under, and a file stamped for another sample is refused by `vespera label`; `label --auto` writes `relevanceScoreFloor` only when it is unset or its provenance begins with the rule's own words (§6, §4).
- **Keeps**: [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md): `arrangementApproved` opens generation only over the arrangement the invocation arrived at, whose run id it begins (§4, §5).
- **Keeps**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md) §6's reason, that the jar reads the profile as it starts and writes it back read-modify-write during an invocation, so a write made while one runs can be lost or change what a later step reads (§1).
- **Keeps**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §9: `code-set-names.yaml` is a list of `folder` and `name`, read by `CodeSetNames` alone in the jar, written by the control panel as a `.part` file moved into place or by the operator by hand, and never by the jar (§7).
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, which lets the control panel read no file format of the jar's beyond those it names: it reads and writes three more, `profile.yaml`, `relevance-labels.yaml` and `code-set-names.yaml`, each rule written again in `java.base`, linking no class of the jar, and held to agree with the jar by contract tests whose class path holds both (Tests). It carries a note at its head.
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §4: finding whether a working directory is in use takes no lock; writing takes one (§1).
- **Keeps**: [ADR-291](0291-the-control-panel-listens-on-127-0-0-1-alone-at-a-port-the-system-picks-and-answers-only-a-browser-holding-its-starts-token-on-its-own-address.md) §8: the control panel writes no file in a folder other local users can write, checks the ownership of a folder it reuses, and writes no token and no text of a document to a file (§1, §8).
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §2, §3 and §6: a file is written whole to a temporary file in its folder and moved over the target with `ATOMIC_MOVE` and `REPLACE_EXISTING`; a folder is checked as §3 checks the launch values' folder; the last seed folder is offered for the operator to confirm, and writing it into `profile.yaml` is decided here (§3).
- **Amends**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) §1, whose holder of `vespera.lock` is an invocation: the control panel holds it too, for the length of one write, with a holder line of its own (§1). It carries a note at its head.
- **Settles**: [#566](https://github.com/algernon28/vespera/issues/566). That every profile key can be set on the control panel, that no value is saved unless the operator typed or confirmed it, and that code sets can be renamed there, are the operator's; §1 to §8 are decided under the operator's delegation of 2026-10-19.

## Context

`profile.yaml` holds sixteen keys, the components of `Profile`, each typed by `TextValue`, `NumericValue`, `NameEndingsValue` or `LanguagesValue`. `ProfileStore` reads it with Jackson's `YAMLMapper`, failing on an unknown key, and writes it whole. `ProfileShapeCheck` loads it while the environment is prepared, so a file not in a profile's shape ends `vespera run` and `vespera label` with one line and exit 1. Census and stages 1 to 5 write `profile.yaml` back during an invocation, each to refresh a `measurement`, and `label --auto` writes `relevanceScoreFloor`.

`ProfileStore` writes a document start `---`, each key on a line of its own at the margin, and beneath it, indented two spaces, `value:`, `provenance:` and `measurement:`, in that order. A text is written in double quotes, with YAML's escapes, `\"`, `\\`, `\n`, `\t`, `\x01`, `\N`, `\_` among them, and characters outside ASCII as themselves; a line past 80 columns is broken where it holds a space, with `\` at its end and the next line indented four spaces further. An absent text is `null`. `measurement` is `null` or a mapping of `source` and `refreshedAt` indented four spaces. The README's examples write `value:` and `provenance:` alone, in double quotes, with no `---`.

What the jar does with each value:

| Key | Type | What the jar reads | Where |
| --- | --- | --- | --- |
| `seedFolder` | text | a path walked from the jar's current directory; a path that does not resolve to a folder is not walked, and the measurement says why | `CensusTasklet`, `SeedGate`, `Walk.canonicalRoot` |
| `degenerateOutputConfidenceFloor` | number | `Double.parseDouble` of the trimmed text; anything else unreadable | `NumericValue.reading` |
| `degenerateOutputWordFloor` | number | a whole number from 0 to 2,147,483,647; anything else ignored | `DegenerateOutputWordFloor.of` |
| `boilerplateDocumentFrequencyFloor` | number | a number; NaN and a floor above 1 make nothing boilerplate | `BoilerplateShingles` |
| `embeddingModel` | text | a model's name, asked of Ollama | `EmbeddingModelGate` |
| `relevanceScoreFloor` | number | a number | `RelevanceScoreFloorValue` |
| `arrangementApproved` | text | trimmed, compared as a prefix of the arrangement's run id, 64 lowercase hexadecimal digits; twelve is the length the page and the closing line show | `ArrangementGate` |
| `generationModel` | text | a model's name, asked of Ollama | `GenerationModel` |
| `generationContextWindow` | number | a whole number, at most 2,147,483,647, leaving room for a document after the reply's 1,024 tokens and the instructions' 256, at two tokens a word, so from 1,282; anything else stops the invocation | `GenerationContextWindow`, `ClusterSynthesis.roomForDocumentsIn` |
| `logTimestampShareFloor` | number | a number | `ByteLevelReductionTasklet` |
| `extractionAttempt` | number | a whole number from 1 to 2,147,483,647; anything else ignored | `ExtractionAttempt.of` |
| `codeNameEndings`, `outOfScopeNameEndings` | name endings | pieces split on spaces and commas, each beginning with a dot, not dots alone, holding none of `/ \ * ?`; one piece that is not makes the whole unreadable | `NameEndingsValue.reading` |
| `translationSimilarityFloor` | number | a number; NaN or an infinity throws where it is compared as a `BigDecimal` | `Translations` |
| `preferredLanguages` | languages | pieces split on spaces and commas, each two ASCII letters | `LanguagesValue.reading` |
| `configurationSettingShareFloor` | number | a number | `ByteLevelReductionTasklet` |

`Double.parseDouble` also reads `NaN`, `Infinity`, `0x1p-1` and `2d`. No floor or share applies a range of its own: a value past its scale is read, and applies to every document or to none.

The earliest run each key enters, by the configuration each run is minted under (`StageRuns`, `ByteLevelReductionTasklet`): the four keys of stage 1's rules enter stage 1's run; the confidence floor, the word floor and the attempt stage 2's; the boilerplate floor stage 4's; the seed folder stage 5's measurement run; the embedding model, the relevance floor and the two keys of translations stage 5's scoring run; the approval, the generation model and the window stage 6b's. A run names the run before it, so a key entering stage N's run changes the run of every stage after N that the next invocation arrives at, and an arrangement re-minted is one no approval names yet.

`relevance-labels.yaml` is written by `RelevanceLabelFile` through a `YAMLMapper`: `---`, the three stamps, then `documents:`, one entry per sampled document opened by `- path:` and holding `score`, `band`, `closestSeed`, `relevant` (`true`, `false` or `null`) and, where a model set the answer, `labelledBy`. `LabelFileReader` reads `relevant` and ignores the rest but `path` and `score`.

`WorkingDirectoryLock` opens `vespera.lock` for writing, creates it where needed, and calls `tryLock(Long.MAX_VALUE - 1, 1, false)`; a second process's `tryLock` on the same byte returns `null`, and a second channel of the same JVM throws `OverlappingFileLockException` (ADR-177's measurements). A process of the jar refused it says one line naming the holder line it read and exits 1.

## Decision

### 1. Writing holds the working directory

The control panel writes into a working directory only while no invocation it started is running, and only holding the working-directory lock, taken as the jar takes it: `vespera.lock` in the working directory, opened for writing and created where needed, and `tryLock(Long.MAX_VALUE - 1, 1, false)`, so the jar and the control panel exclude each other.

- **Before the lock**, the working directory is checked as ADR-292 §3 checks the launch values' folder, by its real path, or as given where it has none: one that does not exist, or is not a folder, is refused; on a file system with POSIX permissions, so is one whose owner's name is not `user.name` or that its group or others may write in. On Windows the owner is not compared. The control panel creates no working directory.
- **Lock got:** it truncates the file and writes one holder line from byte 0, in ADR-177 §1's form, ended by the platform's line separator, which `HolderLine.parse` reads:

  ```
  pid=<the control panel's pid> started=<ISO-8601 offset date and time, to the second> command=control-panel write <file name>
  ```

  It reads the file to be written, makes the new text (§2), writes it, and releases the lock and closes the channel, the write done or refused. The line stays in the file, as a holder's does.
- **Lock not got**, `tryLock` returning `null` or throwing `OverlappingFileLockException`: nothing is written, and one line names the holder line as read, or `its details could not be read` where it is blank or cannot be read. The control panel does not wait, and does not try again on its own; the operator saves again.
- **The control panel makes its writes one at a time**, so no two of its own channels hold or ask for the lock at once, and closing one never releases another's (ADR-177's note on `FileLock`).
- **The control panel starts no invocation while it holds the lock.** An invocation started by hand while it does is refused by ADR-177 §1's own line, which names the control panel's holder line, and exits 1. The corpus-root lock (ADR-279) is not taken: no file under a corpus root is written.

The lines, one each, on the screen that asked for the write:

| Case | Line |
| --- | --- |
| an invocation the control panel started runs | `the control panel writes nothing into a working directory while the invocation it started runs, so <file> was not written` |
| the folder is missing, not a folder, or not the user's own | `the control panel writes nothing in <folder>, which is not a folder only this user can write in, so <file> was not written` |
| the lock is held | `another Vespera invocation is using the working directory <folder> (<holder line>), so <file> was not written; save again once it has ended` |
| the file cannot be written | `the control panel could not write <file>: <reason>` |

### 2. Three files, each written whole

The control panel writes three files of a working directory and no other: `profile.yaml`, `relevance-labels.yaml` and `code-set-names.yaml`. Each is written whole to `<file>.part` in the working directory, which is then moved over the file with `ATOMIC_MOVE` and `REPLACE_EXISTING`, so a reader finds the old file or the new one; a `.part` left by a write that failed is written over by the next.

It rewrites a file only where it reads it in the form the jar writes it, or the control panel itself, as §3, §6 and §7 state for each. A file in any other form is refused whole, and nothing is written: the control panel does not guess at YAML it does not read. The line is

```
<file> is not in the form the jar writes it, so the control panel does not change it
```

Where it rewrites, every byte outside what it changes is kept, line endings included.

A write is one action of the operator's: one profile key (§3), the answers given on one screen of label questions (§6), or the list of code-set names (§7).

### 3. A profile value

One key is written at a time. Its value and its provenance are both the operator's, typed or confirmed: the control panel offers a value it holds, such as the last seed folder (ADR-292 §6), for the operator to confirm, and never offers a provenance. The provenance is required, and is not blank. The value is written with its leading and trailing white space removed; a value left empty unsets the key, written as `value: null` with the operator's provenance. `measurement` is kept as the file holds it.

**The form read.** After an optional line `---`, the file is a sequence of key blocks, with empty lines allowed between lines. A block opens with a line `<key>:` at the margin, `<key>` one of `Profile`'s sixteen components, each at most once in the file. Beneath it, indented two spaces, come entries among `value:`, `provenance:` and `measurement:`, each at most once, in any order. `value` and `provenance` hold a scalar of §3a, or `null`; `measurement` holds `null`, or nothing on its line and lines indented four spaces or more beneath it, which are kept unread. A comment, a flow mapping, a block scalar, a single-quoted or plain scalar running onto a second line, a tab in an indent, a key not of `Profile`, or a key twice, is another form, and §2 refuses it. A file holding nothing but white space is read as holding no key.

**3a. The scalars read.** The README teaches `seedFolder` single-quoted and ADR-284 §9's example is plain, so three kinds of scalar are read, in `profile.yaml` and `code-set-names.yaml` alike, each to the text the jar's Jackson reads from it:

- **double-quoted**, with YAML's escapes, continued on lines indented four spaces or more where it is broken as `ProfileStore` breaks it;
- **single-quoted on one line**, `''` read as `'` and every other character, a backslash included, as itself;
- **plain on one line**, its leading and trailing spaces not part of it, where it does not begin with one of `, [ ] { } # & * ! | > ' " % @` or a backquote, nor with `-`, `?` or `:` followed by a space or ending it, holds neither `: ` nor ` #`, and does not end with `:`.

In `profile.yaml` a plain `null` is null, and any other plain scalar, `~`, `Null`, `true` and `0.35` among them, is read as its text, as Jackson reads it into the profile's text components. In `code-set-names.yaml` a plain scalar Jackson's JSON schema reads as no text, `null`, `true`, `false` or a number such as `0.35` or `-1e3`, is one `CodeSetNames` refuses, and the control panel refuses it as another form (§2). Anything else, a comment after a scalar among them, is another form. The control panel writes every scalar double-quoted.

**The rewrite.** The `value` and `provenance` entries of the key, the line and its continuation lines, are replaced by one line each, `  value: <scalar>` and `  provenance: <scalar>`, where they stand; an entry the block does not hold is added after the key's line, `value` before `provenance`. A block the file does not hold is added at its end, with `  measurement: null`. A file that does not exist is written as `---` and that one block; ADR-062's merge adds the other keys at the next load. A scalar is written on one line in double quotes, `\` and `"` escaped with a backslash, a line feed as `\n`, a carriage return as `\r`, a tab as `\t`, any other character below U+0020, U+007F, U+0085, U+2028 and U+2029 as `\u` and four hexadecimal digits, and every other character as itself.

### 4. Checked before writing, as the jar reads it

A value is checked by the key's type and the rule the jar applies to it, in `java.base`, before anything is written. A value the jar would read as unreadable, ignore or stop on is refused with one line, `<key> is not saved: <reason>`, and nothing is written:

| Key | Saved when the value is | Reason where it is not |
| --- | --- | --- |
| every key | one line, with no character below U+0020 | `a value is one line` |
| every key | given a provenance not blank | `write how you arrived at it in provenance` |
| `seedFolder` | an absolute path naming a folder that exists | `"<value>" is not the absolute path of a folder that exists` |
| `embeddingModel`, `generationModel` | any text | (none) |
| `arrangementApproved` | twelve characters, each `0` to `9` or `a` to `f` | `"<value>" is not the twelve characters naming an arrangement, each 0 to 9 or a to f` |
| `degenerateOutputConfidenceFloor`, `boilerplateDocumentFrequencyFloor`, `relevanceScoreFloor`, `logTimestampShareFloor`, `translationSimilarityFloor`, `configurationSettingShareFloor` | read by `Double.parseDouble` as a finite number | `"<value>" is not a number` |
| `degenerateOutputWordFloor` | a finite number, whole, from 0 to 2147483647 | `"<value>" is not a whole number from 0 to 2147483647` |
| `extractionAttempt` | a finite number, whole, from 1 to 2147483647 | `"<value>" is not a whole number from 1 to 2147483647` |
| `generationContextWindow` | a finite number, whole, from 1282 to 2147483647 | `"<value>" is not a whole number of tokens from 1282 to 2147483647` |
| `codeNameEndings`, `outOfScopeNameEndings` | `NameEndingsValue`'s rule: at least one piece, split on spaces and commas, each beginning with a dot, not dots alone, holding none of `/ \ * ?` | `"<value>" is not a list of name endings, each beginning with a dot` |
| `preferredLanguages` | `LanguagesValue`'s rule: at least one piece, split on spaces and commas, each two ASCII letters | `"<value>" is not a list of two-letter language codes` |
| `relevanceScoreFloor` | given a provenance that does not begin `set by the rule that loses no documentation` | `a provenance beginning "set by the rule that loses no documentation" is the one vespera label --auto writes, and would let it write over this value` |

The checks are on the value as written, white space removed; an empty value, which unsets the key, is checked for its provenance alone. A floor or share past its scale is saved: the jar reads it, and applies it to every document or to none. `NaN` and the infinities are refused for every number, since no floor is meant by one, and `translationSimilarityFloor` stops on one.

Checking a value reads no document: the seed folder's check asks whether the path is a folder, and nothing in it is opened.

### 5. What a change costs, said before it is saved

The control panel holds a table of every profile key, exactly `Profile`'s components in their order, each with its type and the earliest run it enters:

| Key | Type | Earliest run |
| --- | --- | --- |
| `seedFolder` | text | seed measurement (stage 5) |
| `degenerateOutputConfidenceFloor` | number | extraction (stage 2) |
| `degenerateOutputWordFloor` | number | extraction (stage 2) |
| `boilerplateDocumentFrequencyFloor` | number | content redundancy (stage 4) |
| `embeddingModel` | text | embedding scoring (stage 5) |
| `relevanceScoreFloor` | number | embedding scoring (stage 5) |
| `arrangementApproved` | text | generation (stage 6b) |
| `generationModel` | text | generation (stage 6b) |
| `generationContextWindow` | number | generation (stage 6b) |
| `logTimestampShareFloor` | number | byte-level reduction (stage 1) |
| `extractionAttempt` | number | extraction (stage 2) |
| `codeNameEndings` | name endings | byte-level reduction (stage 1) |
| `outOfScopeNameEndings` | name endings | byte-level reduction (stage 1) |
| `translationSimilarityFloor` | number | embedding scoring (stage 5) |
| `preferredLanguages` | languages | embedding scoring (stage 5) |
| `configurationSettingShareFloor` | number | byte-level reduction (stage 1) |

The runs are those of the jar's `StageModules`, in its order: byte-level reduction, extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement, generation.

Before saving, the control panel says what the change costs, and the operator confirms it:

- the earliest run of the key, and every run after it, are the next invocation's to mint again, or to arrive at again where a value is put back (ADR-156);
- where that includes the arrangement, the approval written now names an arrangement the next invocation does not arrive at, so generation stays shut until the new arrangement is approved: `arrangementApproved` closes by itself, and the control panel does not clear it;
- for `seedFolder`, also, `relevance-labels.yaml` names the old seed set, so `vespera label` refuses it until the next `vespera run` writes a new one, whose questions show no answer given under the old seed set.

The control panel changes nothing on its own: no key is cleared, and no file but the one saved is written.

### 6. Label answers

The control panel writes only the `relevant` answer of entries `relevance-labels.yaml` already holds: `true`, `false` or `null`, blank. It writes no stamp, no path, no score, no band, no seed, no `labelledBy` and no entry of its own, and an entry is found by its `path`. A file that is not there is refused, `relevance-labels.yaml is not there, so there is no question to answer; vespera run writes it`.

**The form read.** An optional `---`; the lines `generatedUnderRun:`, `generatedUnderEmbedder:` and `generatedUnderSeedSet:` at the margin, each holding `null` or a double-quoted scalar; `documents:`; then entries, each opened by `- path: <double-quoted scalar>` and holding lines indented two spaces among `score:` and `band:` with a number, `closestSeed:` and `labelledBy:` with `null` or a double-quoted scalar, and `relevant:` with `true`, `false` or `null`, each at most once. A file with two entries of one path is in another form (§2); an answer for a path the file does not hold is refused, `relevance-labels.yaml holds no question about <path>`, and nothing is written.

**The rewrite** replaces the `relevant:` line of each answered entry, and nothing else. Whether the file is for the sample the database holds, its stamps, stays `vespera label`'s to check (ADR-088, ADR-169 §4); `label --auto` stays the jar's, started as the control panel's child.

### 7. Code-set names

The control panel writes `code-set-names.yaml` whole, as the list of entries the operator left, in ADR-284 §9's shape and the order shown:

```
---
- folder: "<folder>"
  name: "<name>"
```

each scalar written as §3 writes one, and `--- []` for a list of none. A folder is `""` for the root's set, or elements joined by `/`, none empty, with no `\`, neither beginning nor ending with `/`; a folder is given once; a name is not blank; clearing a name removes its entry. A list breaking one of these is refused, and nothing is written, with one line giving `CodeSetNames`'s reason in its words: `code-set-names.yaml is not saved: <reason>`, the reason `the folder "<folder>" is given twice`, `the name for the folder "<folder>" is blank`, or `the folder "<folder>" is not written relative to the corpus root, with / between its elements, none empty, and neither beginning nor ending with /`.

It reads the entries of a file in that form, `---` optional, each scalar of a kind §3a reads, with `\r\n` or `\n` line ends, and keeps their order; a file in another form, among them one `CodeSetNames` would refuse, is refused, and nothing is written. An absent file, or one holding nothing but white space, is a list of none.

### 8. What is written

Nothing written by these three files holds a token or a text read from a document (ADR-291 §8): a profile value and its provenance are what the operator typed, a label answer is one of three words, a code-set name is typed. A path in `relevance-labels.yaml` is kept as the jar wrote it, and none is added.

### 9. The classes

In `io.algernon.controlpanel`, linking no class of the jar (ADR-285 §3):

```java
public final class WorkingDirectoryWrite {
    public static final String LOCK_FILE_NAME = "vespera.lock";
    public static final Set<String> FILES = Set.of("profile.yaml", "relevance-labels.yaml", "code-set-names.yaml");
    public WorkingDirectoryWrite(BooleanSupplier anInvocationItStartedRuns, String userName);
    // §1, §2: empty when written; otherwise the one line. change is given the file's text, empty where it is absent,
    // and returns the new text or throws IllegalArgumentException whose message is the line. A name outside FILES throws.
    public Optional<String> write(Path workingDirectory, String fileName, Function<Optional<String>, String> change);
    public boolean holdsTheLock();                                        // true only while a write holds it
    public static Optional<String> refusalOfFolder(Path workingDirectory, String userName, String fileName);  // §1
    public static String holderLine(long pid, OffsetDateTime started, String fileName);                       // §1
}

public enum ProfileKey {                    // §5: one constant per key of Profile, in its order, each named
    SEED_FOLDER, DEGENERATE_OUTPUT_CONFIDENCE_FLOOR /* and the other fourteen */;  // as its key in capitals, _ between words
    public String key();                    // the name profile.yaml spells
    public Kind kind();                     // TEXT, NUMBER, NAME_ENDINGS, LANGUAGES
    public Run earliestRun();
    public Cost cost();                     // §5
    public enum Kind { TEXT, NUMBER, NAME_ENDINGS, LANGUAGES }
    public enum Run { BYTE_LEVEL_REDUCTION, EXTRACTION, CONTENT_CENSUS, CONTENT_REDUNDANCY, SEED_MEASUREMENT,
            EMBEDDING_SCORING, ARRANGEMENT, GENERATION; public String stageName(); }  // StageModules' names
    public record Cost(List<Run> runs, boolean approvalCloses, boolean labelsRefused) {}
}

public final class ProfileEdit {
    public static final String FILE_NAME = "profile.yaml";
    public record Stated(String value, String provenance) {}              // null where the file says null
    public static Optional<String> refusalOf(ProfileKey key, String value, String provenance);   // §4
    public static String rewrite(Optional<String> current, ProfileKey key, String value, String provenance);  // §3
    public static Map<ProfileKey, Stated> read(String text);              // §3's form, decoded
}

public final class LabelAnswers {
    public static final String FILE_NAME = "relevance-labels.yaml";
    public record Question(String path, Optional<Boolean> relevant) {}
    public static List<Question> read(String text);                       // §6's form, in the file's order
    public static String rewrite(Optional<String> current, Map<String, Optional<Boolean>> answersByPath);  // §6
}

public final class CodeSetRenames {
    public static final String FILE_NAME = "code-set-names.yaml";
    public record Rename(String folder, String name) {}
    public static List<Rename> read(Optional<String> text);               // §7
    public static String render(List<Rename> renames);                    // §7
}
```

`read`, `rewrite` and `render` throw `IllegalArgumentException` with the one line where they refuse. The check of a folder in §1 is the one ADR-292 §3 makes of its folder, shared with `LaunchValues` and not written twice, with its own line. Which screen calls these, and where a prefill of `arrangementApproved` comes from, are not decided here.

### 10. What changes

No file under `src/main/java/io/algernon/vespera` changes, so no run id moves.

## Alternatives refused

- **Waiting for the lock, or trying again on a timer.** An invocation holds it for hours; a write the operator asked for would land at a moment they did not choose, over a file the invocation has since rewritten.
- **Writing while the invocation the control panel started runs, between its writes.** Census and stages 1 to 5 write `profile.yaml` back read-modify-write, so a value saved between would be lost, or read by one step and not the one before.
- **Writing without the lock, while no child runs.** A `vespera run` started by hand in the same moment would read a part-written file, or write over the value.
- **Rewriting the profile by a YAML library.** None is in `java.base` (ADR-285 §3), and one written out whole from a parse loses the operator's line breaks, order and spacing.
- **Accepting any YAML the jar accepts.** The control panel would need a full YAML reader in `java.base` to keep every byte; a form it does not read is refused instead, and the jar's next write of `profile.yaml` puts it back into the form read.
- **Refusing a floor or share outside 0 to 1.** The jar reads such a value and defines what it does; refusing it would make the control panel stricter than the jar for no value that the jar cannot act on.
- **Clearing `arrangementApproved` when a change costs the arrangement.** The approval of an old arrangement is how a value put back finds its old run again (ADR-154, ADR-156).
- **Answering a label by its position.** Positions shift between the file the control panel read and the one the jar rewrites; a path names the document.

## Consequences

- A save is refused while any invocation holds the working directory, and the operator saves again once it has ended; a `vespera run` started by hand during a save is refused, with a line naming the control panel's write, and is started again.
- A `profile.yaml` or `code-set-names.yaml` written by hand as the README and ADR-284 §9 show is read and rewritten. One holding a comment, a flow mapping or a value over several lines outside double quotes is refused by the control panel until it is written back in a form §3a reads, or, for `profile.yaml`, until the next invocation's census writes it back in the jar's form.
- After a save that costs the arrangement, generation stays shut until the new arrangement is approved, as it would after the same edit by hand.
- The control panel holds no value the operator did not type or confirm, and the jar reads every value it writes as answered.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.WorkingDirectoryWriteTest.aWriteHoldsTheLockAndLeavesItsHolderLine` | during the change, `holdsTheLock` is true and `vespera.lock` holds §1's line, which `HolderLine.parse` reads with this pid; after, the lock is free and the line stays (§1) |
| `controlpanel.WorkingDirectoryWriteTest.nothingIsWrittenWhileTheInvocationItStartedRuns` | with a child running, the line of §1, the change never called, the file as it was (§1) |
| `controlpanel.WorkingDirectoryWriteTest.aHeldLockIsSaidAndNothingIsWritten` | with the lock held by another channel, the line naming the holder line, or `its details could not be read` for a blank one, and the file as it was (§1) |
| `controlpanel.WorkingDirectoryWriteTest.aFolderOthersCanWriteInOrAnotherUserOwnsIsNotWrittenIn` | on POSIX, a working directory `rwxrwxrwx`, one refused for another user's name, a missing one and a file in its place, each with §1's line and nothing written (§1) |
| `controlpanel.WorkingDirectoryWriteTest.theFileIsWrittenWholeByAMoveAndNothingIsLeftBesideIt` | the target holds the new text, no `.part` stays, and a stale `.part` is written over (§2) |
| `controlpanel.WorkingDirectoryWriteTest.aRefusedChangeWritesNothing` | a file that is not there is given to the change as absent; the change's refusal is the line returned, and no file but `vespera.lock` is written (§2) |
| `controlpanel.WorkingDirectoryWriteTest.onlyTheThreeFilesAreWritten` | a name outside the three throws, and nothing is written (§2) |
| `controlpanel.ProfileKeyTest.eachKeyHasItsTypeAndEarliestRun` | the sixteen keys in order, each with §5's type and run (§5) |
| `controlpanel.ProfileKeyTest.aChangeCostsItsRunAndEveryLaterOne` | the runs of the cost are the earliest and every later one in order; the approval closes for every key whose runs hold the arrangement, and for no other; the labels are refused for `seedFolder` alone (§5) |
| `controlpanel.ProfileEditTest.oneKeyIsRewrittenAndEveryOtherByteKept` | the file outside the key's `value` and `provenance` lines, `measurement` and line ends included, is byte for byte as it was (§2, §3) |
| `controlpanel.ProfileEditTest.anAbsentEntryBlockOrFileIsAdded` | entries added after the key's line, a block at the end, a new file of `---` and one block (§3) |
| `controlpanel.ProfileEditTest.anEmptyValueUnsetsTheKeyWithTheProvenance` | `value: null` and the provenance (§3) |
| `controlpanel.ProfileEditTest.aFileInAnotherFormIsRefusedWhole` | a comment, a flow mapping, a block scalar, a single-quoted or plain scalar over two lines, a plain scalar holding `: ` or ` #` or beginning with an indicator, an unknown key, a key written flat and a key twice are each refused naming the file (§2, §3a) |
| `controlpanel.ProfileEditTest.singleQuotedAndPlainScalarsAreReadAsTheJarReadsThem` | the README's single-quoted Windows path, `''`, a plain number, a plain `-0.2` and a plain `null` are read to the text, or null, §3a gives, and a single-quoted value is rewritten double-quoted (§3a) |
| `controlpanel.ProfileEditTest.eachKeysValueIsCheckedAsTheJarReadsIt` | §4's table, each refused sample with its exact line (§4) |
| `controlpanel.ProfileEditTest.theProvenanceIsRequiredAndNeverTheAutoRules` | a blank provenance is refused for every key, and the rule's words for `relevanceScoreFloor` (§3, §4) |
| `controlpanel.LabelAnswersTest.onlyTheAnswersOfThePathsGivenChange` | each answered entry's `relevant` line changes, every other byte stays (§6) |
| `controlpanel.LabelAnswersTest.aMissingFileAnUnknownPathOrAnotherFormIsRefused` | each refused, nothing returned (§6) |
| `controlpanel.CodeSetRenamesTest.theListIsWrittenInTheFormReadAndReadBackInOrder` | `render` then `read` gives the list in its order, `--- []` for none (§7) |
| `controlpanel.CodeSetRenamesTest.aListCodeSetNamesWouldRefuseIsNotWritten` | a folder twice, a blank name, a folder with `\`, a leading or trailing `/` or an empty element, and a file in another form, each refused (§7) |
| `controlpanel.CodeSetRenamesTest.adr284sExampleIsReadAsWritten` | ADR-284 §9's example, plain, and a single-quoted file are read to their entries; a plain `null`, `true` or number is refused (§3a, §7) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theKeyTableIsTheProfilesComponentsAndTheRunsAreTheStagesModules` | `ProfileKey`'s keys and types are `Profile`'s components and their types, in order; `Run`'s names are `StageModules`' in order (§5) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.everyValueTheControlPanelSavesIsReadAsAnsweredAndEqual` | for each key, accepted samples written by `rewrite` load through `ProfileStore` with the value as written, the provenance, and an answered reading by the jar's own rule (§3, §4) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.everySampleTheControlPanelRefusesTheJarCannotActOn` | each refused sample, written as the jar would read it, is unreadable, ignored or stopped on by the jar's rule for its key (§4) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.aProfileTheJarWroteIsReadAndRewrittenWithItsOtherValuesKept` | a profile `ProfileStore` saved, provenance with escapes and a broken line among them, is read by `ProfileEdit.read` equal, and after one key's rewrite loads equal but for that key (§3) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theLabelFileTheJarWroteIsAnsweredAndReadWithItsStamps` | `RelevanceLabelFile.render`'s text, answered by `LabelAnswers`, is read by `LabelFileReader` with those answers, and its stamps as they were (§6) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theJarReadsEachScalarTheControlPanelReadsToTheSameText` | for single-quoted and plain samples, the README's example among them, `ProfileStore` loads, and `CodeSetNames.parse` reads, the text or null the control panel reads, ADR-284 §9's example verbatim included; each plain scalar `CodeSetNames` refuses the control panel refuses (§3a, §7) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.codeSetNamesReadsTheListTheControlPanelWrites` | `CodeSetNames.parse(render(list))` equals the list; each list `render` refuses `CodeSetNames.parse` refuses too (§7) |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theJarAndTheControlPanelExcludeEachOther` | with a second JVM holding the working directory as the jar does, a write is refused naming its holder line; while a write holds it, a second JVM's `WorkingDirectoryLock` is refused naming the control panel's holder line (§1) |

## What this does not decide

- **The screens**, where each field, cost and line is shown (#561, #568).
- **Where a prefill of `arrangementApproved` comes from** (#565).
- **A save queued while an invocation runs.**
- **Writing `relevance-labels.yaml`'s questions, stamps or `labelledBy`**, which stay the jar's.
