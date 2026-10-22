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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile's {@code clusterPageByteCeiling} key (ADR-303 §2): a number, declared after {@code
 * generationContextWindow}, which census writes unset into a fresh profile, which keeps an operator's answer as written,
 * and which the deliverable's index states with every other key (ADR-186), with no code of its own.
 */
@Epic("Census")
@Feature("Profile")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class ClusterPageByteCeilingInTheProfileTest {

    /** The key, as the profile names it. */
    private static final String KEY = "clusterPageByteCeiling";

    /** A ceiling as an operator may write it, with a trailing zero a value normalised on its way would lose. */
    private static final String A_CEILING = "300000.0";

    private static final String WHY = "read off cluster-page-sizes.html";

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The page ceiling is a key of the profile, declared after the generation context window")
    void theKeySitsAfterTheGenerationContextWindow() {
        List<String> keys = ProfileKeys.everyKey();

        claim(
                "the key comes right after generationContextWindow, the other number the drawing step reads, so the"
                        + " operator reads the two together in profile.yaml and on the deliverable's index",
                () -> assertThat(keys.get(keys.indexOf("generationContextWindow") + 1)).isEqualTo(KEY));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A fresh profile carries the page ceiling, unanswered, which splits no page")
    void aFreshProfileCarriesTheKeyUnset(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the key reads as unset, so no page is split until the operator writes a number",
                () -> assertThat(loaded.clusterPageByteCeiling().reading()).isEqualTo(new NumericValue.Unset()));
        claim(
                "the file census writes carries it, so an operator has it to fill in",
                () -> assertThat(Files.readString(store.file())).contains(KEY + ":"));
        claim(
                "and it is among the keys the deliverable's index states, empty",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, ""));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The page ceiling an operator writes is read and written back as written")
    void anAnswerRoundTrips(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), KEY + ":\n  value: \"" + A_CEILING + "\"\n  provenance: \"" + WHY + "\"\n");

        store.save(store.load());
        Profile reloaded = new ProfileStore(workingDirectory).load();

        claim(
                "the value and its provenance are the operator's, as written, after a load and a save",
                () -> assertThat(List.of(
                                reloaded.clusterPageByteCeiling().value(), reloaded.clusterPageByteCeiling().provenance()))
                        .containsExactly(A_CEILING, WHY));
        claim(
                "the index states it as written",
                () -> assertThat(reloaded.keysAsWritten()).containsEntry(KEY, A_CEILING));
        claim(
                "and the key beside it is left unanswered: the answer landed on its own key",
                () -> assertThat(reloaded.generationContextWindow().isSet()).isFalse());
    }
}
