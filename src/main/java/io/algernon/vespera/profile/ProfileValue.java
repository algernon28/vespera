package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One value in the profile, with everything about it in one object (ADR-061).
 *
 * <p>The three fields travel together rather than in parallel sections, because a value whose
 * provenance sits elsewhere in the file is a value whose provenance can be edited away without
 * anyone noticing. They are written by different hands and on different schedules, which is ADR-062:
 *
 * <ul>
 *   <li>{@code value} — what the profile says. Census writes it once, when it creates the key, and
 *       never again; after that it is the operator's.
 *   <li>{@code provenance} — how that value came to be what it is, in the operator's words. Written
 *       once alongside the value, never rewritten by census.
 *   <li>{@code measurement} — where the data informing this key lives, refreshed by census on every
 *       run. It is census's own pointer, not a judgement, which is why it is the one field a re-run
 *       may overwrite.
 * </ul>
 *
 * <p>An unset key is a present key with nulls, never an absent one: absent and null are the same
 * gated state, so a key nobody has answered yet reads the same either way (ADR-062).
 */
public record ProfileValue(String value, String provenance, Measurement measurement) {

    /** A key census has created and nobody has answered. */
    public static ProfileValue unset() {
        return new ProfileValue(null, null, null);
    }

    /**
     * Whether an operator has supplied this value.
     *
     * <p>Not a field, and marked as such: the record <em>is</em> the file's schema (ADR-061), so
     * anything Jackson would take for a property ends up written into the profile. This is a question
     * about the three fields, not a fourth one.
     */
    @JsonIgnore
    public boolean isSet() {
        return value != null && !value.isBlank();
    }

    /** The same value and provenance, pointed at where census last found the informing data. */
    public ProfileValue measuredBy(Measurement measurement) {
        return new ProfileValue(value, provenance, measurement);
    }
}
