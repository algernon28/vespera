package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The timeouts in a row named by configuration are the count the judge flips at (ADR-330 §9, ADR-071). A corpus of two
 * files, both of which the converter reports running out of time on, so whichever the walk reaches first the two are in
 * a row: at the shipped 3 neither reaches the count, and at 2 the second does, and is set aside.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheTimeoutsInARowConfiguredSetAnOccurrenceAsideInvocationTest.Beans.class})
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the timeouts in a row in the step that judges them")
class TheTimeoutsInARowConfiguredSetAnOccurrenceAsideInvocationTest {

    /** Named by configuration, in place of the shipped 3. */
    private static final int TWO_IN_A_ROW = 2;

    /** Both files time out. */
    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        DoclingExtractor everyFileTimesOut(JdbcTemplate jdbcTemplate) {
            return new PathScriptedExtractor().cachingInto(jdbcTemplate).otherwiseAnswering(TextsOfTheirOwn.timedOut());
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.docling.timeouts-in-a-row", () -> TWO_IN_A_ROW);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How documents are converted is a preset")
    @DisplayName("The second of two timeouts in a row is set aside at a count of two, where the shipped count of three judges both")
    void twoTimeoutsInARowSetTheSecondAside() throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "the bytes of the first");
        Files.writeString(root.resolve("corpus-1.txt"), "the bytes of the second");
        profileStore.save(ProfileFixture.profile().build());

        cli.run("run", root.toString());

        claim("one of the two is set aside as the converter's fault, the row reaching " + TWO_IN_A_ROW + " at it",
                () -> assertThat(jdbcTemplate.queryForList("SELECT category FROM extraction_fault", String.class))
                        .containsExactly("timeout"));
    }
}
