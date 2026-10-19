package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile's {@code degenerateOutputWordFloor} key (ADR-282 section 3): a number, declared after the confidence
 * floor, which census drafts unset, which keeps an operator's answer as written, and whose pointer stage 3 brings up
 * to date without touching the answer.
 */
@Epic("Census")
@Feature("Profile")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
class DegenerateOutputWordFloorInTheProfileTest {

    /** The key, as the profile names it. */
    private static final String KEY = "degenerateOutputWordFloor";

    /** A floor as an operator writes it, and why. */
    private static final String TWO_WORDS = "2";

    private static final String WHY = "read off word-count-distribution.html: a floor of 2 removes 40 images";

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The word floor is a key of the profile, declared after the confidence floor")
    void theKeySitsAfterTheConfidenceFloor() {
        List<String> keys = ProfileKeys.everyKey();

        claim(
                "the key comes right after degenerateOutputConfidenceFloor, the other floor of the same verdict, so"
                        + " the operator reads the two together in profile.yaml and on the deliverable's index",
                () -> assertThat(keys.get(keys.indexOf("degenerateOutputConfidenceFloor") + 1)).isEqualTo(KEY));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A fresh profile carries the word floor, unanswered, which removes nothing")
    void aFreshProfileCarriesTheKeyUnset(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the key reads as unset: observe before enforce, so nothing is removed for too few words until the"
                        + " operator writes a number",
                () -> assertThat(loaded.degenerateOutputWordFloor().reading()).isEqualTo(new NumericValue.Unset()));
        claim(
                "and the file census writes carries it, so an operator has it to fill in",
                () -> assertThat(Files.readString(store.file())).contains(KEY + ":"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The word floor an operator writes is read and written back as written")
    void anAnswerRoundTrips(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(
                store.file(),
                KEY + ":\n  value: \"" + TWO_WORDS + "\"\n  provenance: \"" + WHY + "\"\n");

        store.save(store.load());
        Profile reloaded = new ProfileStore(workingDirectory).load();

        claim(
                "the value and its provenance are the operator's, as written, after a load and a save",
                () -> assertThat(List.of(
                                reloaded.degenerateOutputWordFloor().value(),
                                reloaded.degenerateOutputWordFloor().provenance()))
                        .containsExactly(TWO_WORDS, WHY));
        claim(
                "and every other key is left unanswered: the answer landed on its own key",
                () -> assertThat(reloaded.degenerateOutputConfidenceFloor().isSet()).isFalse());
    }

    @Test
    @Story("The profile points at the page the value is read off")
    @DisplayName("Pointing the word floor at its page keeps the operator's answer and touches no other key's pointer")
    void thePointerKeepsTheAnswer() {
        Measurement page = new Measurement("word-count-distribution.html", Instant.parse("2026-10-19T12:00:00Z"));
        Profile answered = ProfileFixture.profile().degenerateOutputWordFloor(TWO_WORDS, WHY).build();

        Profile pointed = answered.withDegenerateOutputWordFloorMeasurement(page);

        claim(
                "the word floor points at the page, with its value and provenance as the operator wrote them",
                () -> assertThat(pointed.degenerateOutputWordFloor()).isEqualTo(new NumericValue(TWO_WORDS, WHY, page)));
        claim(
                "and the confidence floor's pointer is not touched, so each floor points at its own page",
                () -> assertThat(pointed.degenerateOutputConfidenceFloor().measurement()).isNull());
    }
}
