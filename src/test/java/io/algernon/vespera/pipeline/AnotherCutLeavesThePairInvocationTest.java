package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The near-duplicate cut named by configuration is the cut that judges (ADR-330 §9): {@code
 * ThePairIsRedundantAtTheShippedCutInvocationTest} finds this pair at the shipped number, and at this one it stands.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TwoDocumentsNearlyAlike.class})
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the similarity cuts in the step that judges")
class AnotherCutLeavesThePairInvocationTest {

    /** Above the pair's Jaccard of about 0.81, where the shipped 0.80 is below it. */
    private static final String A_HIGHER_CUT = "0.9";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.redundancy.near-duplicate-jaccard", () -> A_HIGHER_CUT);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("Two documents of about 0.81 are not judged redundant at a cut of 0.9")
    void aPairAboveTheShippedCutAndBelowTheConfiguredOneIsNotJudged() throws IOException {
        TwoDocumentsNearlyAlike.aCorpusIn(root, profileStore);

        cli.run("run", root.toString());

        claim("stage 4 signed the three documents, so it ran",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM minhash_signature", Integer.class))
                        .isEqualTo(3));
        claim("and its run names the cut of " + A_HIGHER_CUT + ", so the value stopped nothing and is the one it judged by",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT config_consumed FROM run WHERE stage = 'content-redundancy'", String.class))
                        .singleElement().asString().contains("near-duplicate:" + A_HIGHER_CUT));
        claim("neither of the pair is written redundant, the cut of " + A_HIGHER_CUT + " being above what they share",
                () -> assertThat(jdbcTemplate.queryForList("SELECT relation FROM redundant_with", String.class)).isEmpty());
    }
}
