# ADR-340 — The note saved with a profile value is filled in for the operator, `set in the control panel` or the provenance already saved, and a value changed under its old note is saved with the filled-in one

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: by the operator, that the profile form asks no "Why this value?"; that each value has a note saved as its `provenance`, filled in for them, `set in the control panel` or the note already saved, which they may replace; and that a value changed while its note is left as it was is saved with the filled-in note (§1, §2). Where the rule lives, its names, what becomes of the two refusals it makes unreachable and the tests, by Claude for the operator.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §3, whose control panel never offers a provenance: it offers the filled-in note (§1); and §4, whose refusal of a blank provenance the window never meets, since it saves none (§3). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §5, whose second rule refused a value changed under the provenance the file states: that value is saved with the filled-in note (§2, §3). Its first rule, a save that changes nothing writing nothing, stands. It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §7, whose provenance field's hint asked for one beside each value changed (§1). It carries a note at its head.
- **Keeps**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md): each value is `value`, `provenance` and `measurement` under its key, the provenance free text. [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md): the jar's merge never touches a value or its provenance.
- **Keeps**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §4: `vespera label --auto` writes the floor only where the key is unset or its provenance begins with the rule's words; ADR-295 §4's refusal of those words from the control panel stands (§4).
- **Keeps**: [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md): an agent writes no provenance but the operator's own words or words they approve. The filled-in note is the window's, saved by the operator's Save, and never an agent's.
- **Read with**: [ADR-339](0339-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md), whose window holds the form, and [ADR-341](0341-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, where the notes stand.
- **Settles**: [#700](https://github.com/algernon28/vespera/issues/700), with ADR-339 and ADR-341.

## Context

**What a provenance is.** ADR-061 makes every value of `profile.yaml` one object, its value, its provenance and a pointer to the measurement that informs it; the provenance is free text, saying how the value was arrived at. The README tells the operator so: *Every value you set carries a `provenance` field. Write down how you arrived at the number.* `CONTEXT.md`'s Profile: *each value carries how it was arrived at*.

**What the control panel asks of it today.**

- ADR-295 §3: the provenance is the operator's, typed; the control panel never offers one, and a blank one is refused by §4's line `<key> is not saved: write how you arrived at it in provenance`.
- ADR-330 §5: a value changed while its provenance is what the file states is refused, `its value changed and its provenance did not; write how you arrived at the new value`, so that the old reason is not left under a new number.
- ADR-302 §4 and §8, ADR-331 §8 and ADR-334 §7: each value's form holds a field `<key>.provenance`, labelled *Why this value?*, empty or filled from the file, with a hint asking for a reason in the operator's own phrasing.

So each value the operator changes costs a sentence before it is saved, and a Save of the one form refuses every value changed under its old words.

**What else writes a provenance.** `vespera label --auto` writes the relevance floor's, beginning `set by the rule that loses no documentation`, and writes over the floor only while its provenance still begins so (ADR-197 §4). The control panel refuses those words for that key (ADR-295 §4).

## Decision

### 1. Each value has a note, filled in

**The form asks no "Why this value?"** Beside each value of the profile, on the Profile tab and where a value the next invocation wants is edited in place (ADR-341 §1), stands one field, **the note saved with it**, which is saved as the key's `provenance`. It is filled in:

- with **the provenance the file states for the key**, where it states one that is not blank;
- otherwise with **`set in the control panel`**.

The operator may replace it with words of their own, or leave it. Its hint says it is saved beside the value in `profile.yaml` so that whoever reads it later knows where the value came from, and that they may write how they chose the value instead. Nothing marks it required.

### 2. What is saved

When the form is saved, each key is written or not, and with which provenance, by the first row of this table that matches it:

| The value, against the file | The note, against the one shown (§1) | What is written |
| --- | --- | --- |
| as the file states | left as shown | nothing: the key is not written |
| any | blank | the value, with **`set in the control panel`** |
| changed | left as shown | the value, with **`set in the control panel`** |
| any | changed | the value, with the note, its white space around removed |

The value is compared as ADR-330 §5 compares it: read as the jar reads it, its white space around lost and a list's pieces joined by one space. The note is compared with the one §1 filled the field with, each with any line feed or carriage return removed and its white space around ignored. A value emptied, which unsets the key, is a value changed. So a key the file states nothing for, left alone, is not written, though its field shows `set in the control panel`, and pressing Save on a form left alone writes nothing, as ADR-330 §5's first rule has it.

So **the old words never stay under a new number**: a value changed while its note is left as shown gets the filled-in words, which say where it was set and claim no reason. And a note the operator wrote is saved as they wrote it, beside a value changed or not.

### 3. Where the rule lives, and the refusals it makes unreachable

`ProfileEdit`, beside the checks it holds for ADR-295 §4, gains:

```java
/** The words a note is filled in with where the file states no provenance, and saved with where §2 says. */
public static final String FILLED_IN_NOTE = "set in the control panel";

/** The note a form shows beside a key: the provenance the file states, where not blank, else FILLED_IN_NOTE (§1). */
public static String noteShown(Optional<Stated> inFile);

/** The provenance a save writes for the key by §2's table; empty where the key is not written (§2's first row). */
public static Optional<String> noteToSave(ProfileKey key, String value, String note, Optional<Stated> inFile);
```

`value` and `note` are what the form holds; `inFile` what the file states for the key, empty where it states none. The window writes each key `noteToSave` answers for, with that answer as its provenance, through `refusalOfSave` and `rewrite` as before. So:

- **ADR-330 §5's second rule goes** from `refusalOfSave`: a value changed under the provenance the file states is no refusal, since the window never sends one (§2) and the rule would otherwise refuse the filled-in words where a file already held them.
- **ADR-295 §4's refusal of a blank provenance stays** in `refusalOf`, which every write passes: no blank provenance is ever written, and the window, saving none, never meets it.
- **ADR-295 §4's refusal of the auto rule's words for `relevanceScoreFloor` stays**, and is met only where the operator types them.

### 4. The floor the local model set

A floor `vespera label --auto` set carries the rule's words. Left as it is, it is not written. Its value changed under those words, it is saved with `set in the control panel`, which does not begin with the rule's words, so `vespera label --auto` no longer writes over it (ADR-197 §4): the floor is the operator's from that Save on. Its note changed by the operator, to anything but the rule's words, the same.

### 5. What changes beside the window

- **The README's paragraph on provenance** says the note is filled in and may be replaced, in place of asking the operator to write one; it changes with the window, in the change that builds it.
- **`CONTEXT.md`'s Profile entry** says each value carries its provenance, a reason the operator wrote or the control panel's filled-in note, in the same change.
- **A profile written by hand is unchanged**: the README's form, a `provenance` under each `value`, stays what the jar reads, and a bare value is refused as before.

No run id moves: the profile's provenance enters no run's identity, and no file under `src/main/java/io/algernon/vespera` changes.

## Consequences

- The operator saves a value with one press, and the profile still says beside each value where it came from.
- A note of `set in the control panel` says where a value was set and not how it was chosen; a reader of the profile finds the operator's reasons only where they wrote them.
- A note the file held is kept until the value under it changes, so a reason written once is not lost to a later Save of other keys.
- A floor the local model set becomes the operator's when its value is changed from the window.

## Known limits

- **A value changed and changed back** before Save is compared with the file and is no change, as ADR-330 §5 compares.
- **A note the operator retypes as it was shown**, beside a value changed, is read as left as shown and saved as `set in the control panel`.

## Alternatives refused

- **"Why this value?" kept, required.** It costs a sentence for every value changed, and the operator chose the filled-in note.
- **No provenance written for a value set in the window.** ADR-061's object holds one beside every value, and the jar refuses a value without it.
- **The filled-in note dated, or naming the user.** The file's own history is the operator's; a note that changes on every save would make every save a change of each note.
- **The old note kept beside a value changed.** It describes the old value, and would sit under the new one as its reason.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheNoteSavedWithAValueTest.theNoteShownIsTheProvenanceTheFileStatesOrTheFilledInOne` | a provenance stated, none, `null` and blank: the stated one, else `set in the control panel` (§1) |
| `controlpanel.TheNoteSavedWithAValueTest.eachValueAndNoteIsWrittenAsTheTableSays` | §2's table, row by row, a key the file states nothing for among them, left alone and not written (§2) |
| `controlpanel.TheNoteSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused` | `refusalOfSave` lets a value changed under the stated provenance through (§3) |
| `controlpanel.TheNoteSavedWithAValueTest.aFloorTheLocalModelSetChangedUnderItsNoteBecomesTheOperators` | the floor changed under the rule's words is saved with the filled-in note, which `refusalOf` accepts for the floor (§4) |

`controlpanel.ProfileEditTest.theProvenanceIsRequiredAndNeverTheAutoRules` stands, holding that every write still refuses a blank provenance and the rule's words for the floor (§3). The two screen tests that read the refusal of a value changed under its old provenance go with the pages (ADR-339 §6), and the refusal with them (§3).

## What this does not decide

- **Where on the window each note stands**: ADR-341 §1.
- **The words of the note's hint**, beyond what §1 says.
