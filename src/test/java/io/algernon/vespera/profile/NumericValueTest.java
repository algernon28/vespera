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

    /** The four characters a quoted null arrives as, which is the risk ADR-061 was written against. */
    private static final String A_QUOTED_NULL = "null";

    @Test
    @Story("A key nobody has answered is distinguishable from one answered wrongly")
    @DisplayName("A key nobody has answered reads as unanswered")
    void anUnansweredKeyReadsAsUnset() {
        NumericValue unanswered = NumericValue.unset();

        claim(
                "a key census drafted and nobody filled in reads as unanswered, which is the state the"
                        + " whole pipeline is already built around -- no threshold, nothing removed",
                () -> assertThat(unanswered.reading()).isInstanceOf(NumericValue.Unset.class));
    }

    @Test
    @Story("A key nobody has answered is distinguishable from one answered wrongly")
    @DisplayName("A key holding only spaces reads as unanswered, not as a wrong answer")
    void aBlankKeyReadsAsUnset() {
        NumericValue blank = new NumericValue("   ", "typed and then thought better of", null);

        claim(
                "whitespace is nobody having answered rather than somebody answering badly: there is no"
                        + " mistake to report back, and reporting one would send a person looking for a"
                        + " value they never wrote",
                () -> assertThat(blank.reading()).isInstanceOf(NumericValue.Unset.class));
    }

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
    @Story("A number that was written readably is read")
    @DisplayName("Spaces around a number do not stop it being read")
    void surroundingSpaceIsNotAMistake() {
        NumericValue padded = new NumericValue("  " + A_READABLE_FLOOR + "  ", "pasted in", null);

        claim(
                "a value pasted in with spaces around it is the number the person meant: two of the five"
                        + " readers this replaces trimmed and three did not, so the same file was readable"
                        + " to one stage and fatal to another",
                () -> assertThat(padded.reading())
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
                        + " because only one of the two is worth telling them about",
                () -> assertThat(commaed.reading()).isInstanceOf(NumericValue.Unreadable.class));
        claim(
                "and it carries back exactly what they wrote, so the closing line can quote it at them"
                        + " rather than describing it",
                () -> assertThat(commaed.reading())
                        .isEqualTo(new NumericValue.Unreadable(A_DECIMAL_COMMA)));
    }

    @Test
    @Story("A value nobody can read a number from is its own answer")
    @DisplayName("The word null in quotes is a wrong answer, never a missing one")
    void aQuotedNullIsUnreadableRatherThanUnset() {
        NumericValue quotedNull = new NumericValue(A_QUOTED_NULL, "meant to clear the key", null);

        claim(
                "the four characters that arrive when somebody quotes the word null are treated as the"
                        + " wrong answer they are: this is the exact mistake the profile's format was"
                        + " chosen to catch, and for seven keys it was reaching the stages as ordinary"
                        + " text nobody had checked",
                () -> assertThat(quotedNull.reading())
                        .isEqualTo(new NumericValue.Unreadable(A_QUOTED_NULL)));
        claim(
                "and it is emphatically not read as nobody having answered, which is what would let a"
                        + " person believe they had cleared a threshold when they had set a broken one",
                () -> assertThat(quotedNull.reading()).isNotInstanceOf(NumericValue.Unset.class));
    }

    @Test
    @Story("A value nobody can read a number from is its own answer")
    @DisplayName("A value that was written is reported as set even when no number can be read from it")
    void anUnreadableValueIsStillSet() {
        NumericValue commaed = new NumericValue(A_DECIMAL_COMMA, "read off the report", null);

        claim(
                "the key counts as answered, because somebody did answer it -- what is asked separately"
                        + " is whether the answer can be acted on, and collapsing the two questions is how"
                        + " a mistyped value would go unmentioned",
                () -> assertThat(commaed.isSet()).isTrue());
    }
}
