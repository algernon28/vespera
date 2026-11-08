package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a numeric profile key says, as one of three answers (ADR-120).
 *
 * <p>The middle answer is the one this class exists for. Before it, a value no number could be read
 * from was parsed at five separate call sites that disagreed about what it meant: two read it as
 * unanswered, one reported it, and two let the failure out — one ending the invocation, one stopping
 * the application from starting at all. Which of those an operator met was decided by which key they
 * mistyped.
 *
 * <p>Tested here rather than only through the file because this is where the judgement now lives, and
 * a judgement made in one place is worth asserting in one place. {@link ProfileStoreTest} covers the
 * other half — that the answer survives being written down and read back.
 */
@Epic("Census")
@Feature("Profile")
@Issue("203")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
@Link(name = "ADR-061", url = Adr.PROFILE_IS_YAML_TYPED_RECORDS, type = "adr")
@Link(name = "ADR-062", url = Adr.CENSUS_MERGES_AND_NEVER_OVERWRITES, type = "adr")
class NumericValueTest {

    /** A floor an operator might plausibly have read off a report. */
    private static final String A_READABLE_FLOOR = "0.42";

    /** The same number, as the parse should hand it back. */
    private static final double A_READABLE_FLOOR_AS_A_NUMBER = 0.42;

    /** A decimal comma: the likeliest real mistake, and the one that used to be silently ignored. */
    private static final String A_DECIMAL_COMMA = "0,42";

    @Test
    @Story("A number that was written readably is read")
    @DisplayName("A number reads back as that number")
    void aNumberReadsAsAnswered() {
        NumericValue floor = new NumericValue(A_READABLE_FLOOR, "read off the labelling report", null);

        claim(
                "the value reads back as the number " + A_READABLE_FLOOR + " that was written, which is"
                        + " the ordinary case every stage that applies a threshold depends on",
                () -> assertThat(floor.reading())
                        .isEqualTo(new NumericValue.Answered(A_READABLE_FLOOR_AS_A_NUMBER)));
    }

    @Test
    @Story("A value nobody can read a number from is its own answer")
    @DisplayName("A decimal comma is reported as unreadable rather than passed off as unanswered")
    void aDecimalCommaIsUnreadable() {
        NumericValue commaed = new NumericValue(A_DECIMAL_COMMA, "read off the report", null);

        claim(
                "a comma written for a decimal point is somebody having answered, and having answered in"
                        + " a way nothing can act on -- which is a different thing from never answering,"
                        + " because only one of the two is worth telling them about -- and it carries back"
                        + " exactly what they wrote, so the closing line can quote it at them rather than"
                        + " describing it",
                () -> assertThat(commaed.reading())
                        .isEqualTo(new NumericValue.Unreadable(A_DECIMAL_COMMA)));
    }
}
