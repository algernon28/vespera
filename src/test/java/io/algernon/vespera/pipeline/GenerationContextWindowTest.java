package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * How much one call may read, and where that number comes from (ADR-108, #182).
 *
 * <p>{@link GenerationModelTest}'s sibling, and the same shape of key: unanswered means a default
 * applies rather than a stop. Being asked for this one before a first run would be worse than being
 * asked for a model — most people running this could not say what number belongs here, and the answer
 * that matters is whichever one the machine in front of them can actually serve.
 *
 * <p><b>An answer that is not a number is refused rather than ignored.</b> Unset and wrong are not the
 * same state: somebody who wrote something here meant to change what happens, and quietly reading past
 * it would run their archive under a window they did not choose and never be mentioned again.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({GenerationContextWindow.class, ProfileStore.class})
@Epic("Synthesis")
@Feature("Generation")
@Issue("182")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
class GenerationContextWindowTest {

    /** What an operator wrote, deliberately not the shipped number so a resolver ignoring them fails. */
    private static final int AN_OPERATOR_CHOICE = 32768;

    /** A value written and left empty, which {@code ProfileValue.isSet()} already reads as unset. */
    private static final String BLANK = "";

    /** The key's own name, as any refusal has to name it so the operator knows where to look. */
    private static final String KEY_IN_THE_PROFILE = "generationContextWindow";

    /** What somebody might write meaning to be helpful, which is not a window. */
    private static final String NOT_A_NUMBER = "as much as it can take";

    /** {@link #AN_OPERATOR_CHOICE} written with a decimal point: the same count of tokens (ADR-120). */
    private static final String THE_SAME_NUMBER_WITH_A_POINT = AN_OPERATOR_CHOICE + ".0";

    /** Half a token, which is no more usable than none. */
    private static final String HALF_A_TOKEN = "4096.5";

    /**
     * A number too large for any window, and the one that reads as whole and positive.
     *
     * <p>It is here because it nearly shipped. The parse behind this key became a {@code double} under
     * ADR-120, and {@code (int)} of anything past {@code Integer.MAX_VALUE} saturates rather than
     * failing — so this value passed both of the obvious checks and silently became a window of two
     * billion tokens.
     */
    private static final String LARGER_THAN_ANY_WINDOW = "1e18";

    /** The same hole at its most extreme: whole and positive by every test that is not a range check. */
    private static final String NOT_A_FINITE_NUMBER = "Infinity";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private GenerationContextWindow contextWindow;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("A number most people cannot reason about has an answer already")
    @DisplayName("With nothing written, the window is the one the code ships with")
    void fallsBackToTheShippedWindow() {
        write(null);

        claim(
                "an unanswered key resolves to the shipped number rather than ending the invocation:"
                        + " being stopped on the way to a first run, over a number whose right value"
                        + " depends on the machine doing the serving, would teach nobody anything",
                () -> assertThat(contextWindow.size()).isEqualTo(ClusterSynthesis.CONTEXT_WINDOW));
    }

    @Test
    @Story("A number most people cannot reason about has an answer already")
    @DisplayName("A key left empty is the same as one never written")
    void treatsABlankAnswerAsNoAnswer() {
        write(BLANK);

        claim(
                "a key someone opened and left empty is a key nobody answered, which is what every other"
                        + " value in this file already means by it",
                () -> assertThat(contextWindow.size()).isEqualTo(ClusterSynthesis.CONTEXT_WINDOW));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("A window written in the profile is the one used")
    void usesTheWindowTheOperatorWrote() {
        write(String.valueOf(AN_OPERATOR_CHOICE));

        claim(
                "what the operator wrote wins over what the code ships with: a bigger machine reads more"
                        + " of each group in one go, and that is a fact about their machine that nothing"
                        + " here could have worked out for them",
                () -> assertThat(contextWindow.size()).isEqualTo(AN_OPERATOR_CHOICE));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("An answer that is not a number stops rather than being read past")
    void refusesAnAnswerThatIsNotANumber() {
        write(NOT_A_NUMBER);

        claim(
                "it stops, naming the key: somebody who wrote this meant to change how much gets read, and"
                        + " carrying on under the shipped number would run their archive under a window"
                        + " they did not choose without ever mentioning it",
                () -> assertThatThrownBy(() -> contextWindow.size())
                        .hasMessageContaining("generationContextWindow")
                        .hasMessageContaining(NOT_A_NUMBER));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("The same number written with a decimal point is the same number of tokens")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void acceptsAWholeNumberWrittenWithAPoint() {
        write(THE_SAME_NUMBER_WITH_A_POINT);

        claim(
                "it is read as " + AN_OPERATOR_CHOICE + " tokens: the rule this key refuses on is a whole"
                        + " positive number of tokens, and a decimal point is how that number was typed"
                        + " rather than a different number -- stopping here would refuse a notation nobody"
                        + " decided against",
                () -> assertThat(contextWindow.size()).isEqualTo(AN_OPERATOR_CHOICE));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("Part of a token stops, because a window is counted in whole ones")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void refusesPartOfAToken() {
        write(HALF_A_TOKEN);

        claim(
                "it stops and quotes back exactly what was typed, rather than rounding to a window the"
                        + " person did not ask for",
                () -> assertThatThrownBy(() -> contextWindow.size())
                        .hasMessageContaining(KEY_IN_THE_PROFILE)
                        .hasMessageContaining(HALF_A_TOKEN));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("A number too large for any window stops instead of quietly becoming the largest one")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void refusesANumberTooLargeForAnyWindow() {
        write(LARGER_THAN_ANY_WINDOW);

        claim(
                "it stops rather than saturating: this number is whole and positive, so every check but a"
                        + " range check lets it through, and what it would have become is the largest"
                        + " window the machine can name -- which is the archive generated under a number"
                        + " nobody chose, the one outcome this key exists to prevent",
                () -> assertThatThrownBy(() -> contextWindow.size())
                        .hasMessageContaining(KEY_IN_THE_PROFILE)
                        .hasMessageContaining(LARGER_THAN_ANY_WINDOW));
    }

    @Test
    @Story("Whoever runs this can say how much their own machine will read")
    @DisplayName("A value that is not a finite number stops as well")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void refusesAValueThatIsNotFinite() {
        write(NOT_A_FINITE_NUMBER);

        claim(
                "the same hole at its widest is shut too, and for the same reason: there is no count of"
                        + " tokens here to run anything under",
                () -> assertThatThrownBy(() -> contextWindow.size())
                        .hasMessageContaining(KEY_IN_THE_PROFILE));
    }

    /** Writes the key, leaving every other one as it was. */
    private void write(String value) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(value, value == null ? null : "set by this test")
                .build());
    }
}
