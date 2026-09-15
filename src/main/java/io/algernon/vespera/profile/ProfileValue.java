package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One value in the profile, with everything about it in one object (ADR-061), typed by the key it
 * answers (ADR-120).
 *
 * <p>The three components travel together rather than in parallel sections, because a value whose
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
 *
 * <p><b>Sealed over two records, and that is ADR-120.</b> Both implementations carry those same
 * three components in the same order, so the file on disk is byte-for-byte what it was; what the
 * split buys is that <em>which keys hold a number</em> is a fact the compiler holds rather than a
 * convention each reader re-derives. Before it, three numeric keys were parsed at five call sites
 * that disagreed three ways about a value no number could be read from — one ignored it, one ended
 * the invocation, one stopped the context from starting.
 */
public sealed interface ProfileValue permits NumericValue, TextValue {

    /** What the profile says, exactly as the operator wrote it. */
    String value();

    /** How that value came to be what it is, in the operator's words. */
    String provenance();

    /** Where the data informing this key lives, or {@code null} where census names nothing. */
    Measurement measurement();

    /**
     * Whether an operator has supplied this value.
     *
     * <p>Not a component, and marked as such: the record <em>is</em> the file's schema (ADR-061), so
     * anything Jackson would take for a property ends up written into the profile. This is a question
     * about the three components, not a fourth one — and the annotation works from here, on the
     * interface, which ADR-120's probe established rather than assumed.
     *
     * <p><b>Set is not the same as usable.</b> A numeric key the operator answered with something no
     * number can be read from is set and unreadable at once; {@link NumericValue#reading()} is what
     * separates them, and no caller should infer a usable number from this answer alone.
     */
    @JsonIgnore
    default boolean isSet() {
        return value() != null && !value().isBlank();
    }
}
