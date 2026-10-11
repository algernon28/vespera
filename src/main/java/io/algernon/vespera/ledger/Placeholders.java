package io.algernon.vespera.ledger;

import java.util.Collections;

/**
 * The placeholders of a list a statement is completed with at run time: {@code count} question marks
 * joined by a comma and a space, for the brackets after {@code IN} that the statement itself writes.
 *
 * <p>One home for what four modules each spelled for themselves (ADR-220 section 4's lookups by key,
 * ADR-225's pages of a hash's occurrences). It lives in {@code ledger}, the one module every capability
 * may name (ADR-040), and holds no rule: what is asked, and how many at a time, stays the caller's.
 */
public final class Placeholders {

    private Placeholders() {}

    /** {@code ?, ?, ?} for three; an empty text for none, which no caller sends, an empty list being asked nothing. */
    public static String of(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
