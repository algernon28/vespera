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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What makes {@code AnotherCutLeavesThePairInvocationTest} able to fail: at the shipped numbers its pair is judged
 * (ADR-330 §9). It passes today and carries no {@code @Disabled}.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TwoDocumentsNearlyAlike.class})
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ThePairIsRedundantAtTheShippedCutInvocationTest {

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("At the shipped cut two documents of about 0.81 are judged redundant, one of them with the other")
    void aPairAboveTheShippedCutIsJudged() throws IOException {
        TwoDocumentsNearlyAlike.aCorpusIn(root, profileStore);

        cli.run("run", root.toString());

        claim("one of the pair is written redundant with the other, as a near duplicate",
                () -> assertThat(jdbcTemplate.queryForList("SELECT relation FROM redundant_with", String.class))
                        .containsExactly("near-duplicate"));
    }
}
