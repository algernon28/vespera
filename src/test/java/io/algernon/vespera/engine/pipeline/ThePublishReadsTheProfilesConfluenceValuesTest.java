package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.publish.confluence.ConfluenceValues;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
 * What a publish reads of the profile (ADR-359 §2, §5): {@code pipeline}, the one module naming {@code profile}, reads
 * the five Confluence values off {@code profile.yaml} in the working directory and hands them to {@code confluence} as
 * plain values. Where the file is not there, names none of them or does not load, no value is read, so the publish
 * reads the shell alone, as before.
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
class ThePublishReadsTheProfilesConfluenceValuesTest {

    /** A profile naming the site, the space, the folder by its id and the email, and the folder by no title. */
    private static final String FOUR_OF_THE_FIVE = """
            generationModel:
              value: "a-generation-model:8b"
              provenance: "overriding what is configured"
            confluenceBaseUrl:
              value: "https://wiki.example.com"
              provenance: "the team's site"
            confluenceSpaceKey:
              value: "DOCS"
              provenance: "the space the team writes in"
            confluenceFolderId:
              value: "123456789"
              provenance: "the folder the team keeps hand-overs in"
            confluenceEmail:
              value: "operator@example.org"
              provenance: "the account the pages are published as"
            """;

    /** A profile naming none of the five. */
    private static final String NONE_OF_THE_FIVE = """
            generationModel:
              value: "a-generation-model:8b"
              provenance: "overriding what is configured"
            """;

    /** A file that is no profile: a list where a key's value and provenance go (ADR-061). */
    private static final String NOT_IN_A_PROFILES_SHAPE = """
            confluenceBaseUrl:
              - "https://wiki.example.com"
            """;

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("The site's address, the space's key, the folder and the email are read off the working directory's profile, a value it does not name read as none")
    void theFiveKeysAreReadOffTheWorkingDirectorysProfile(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), FOUR_OF_THE_FIVE);

        ConfluenceValues read = new ConfluenceValuesOfTheProfile().of(workingDirectory);

        claim("the four the file names are read as it wrote them, and the folder's title, which it does not name, is none",
                () -> assertThat(read).isEqualTo(new ConfluenceValues("https://wiki.example.com", "DOCS", "123456789",
                        null, "operator@example.org")));
    }

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("No profile, a profile naming none of the five and one that does not load each give the publish no value")
    void noProfileAProfileNamingNoneAndOneThatDoesNotLoadGiveNone(@TempDir Path folder) throws IOException {
        Path noFile = Files.createDirectories(folder.resolve("no-profile"));
        Path namingNone = Files.createDirectories(folder.resolve("naming-none"));
        Files.writeString(namingNone.resolve("profile.yaml"), NONE_OF_THE_FIVE);
        Path notLoading = Files.createDirectories(folder.resolve("not-loading"));
        Files.writeString(notLoading.resolve("profile.yaml"), NOT_IN_A_PROFILES_SHAPE);

        ConfluenceValuesOfTheProfile reader = new ConfluenceValuesOfTheProfile();
        List<ConfluenceValues> read = List.of(reader.of(noFile), reader.of(namingNone), reader.of(notLoading));

        claim("each of the three gives none of the five values, so the publish reads the shell alone, as before",
                () -> assertThat(read).containsExactly(ConfluenceValues.NONE, ConfluenceValues.NONE,
                        ConfluenceValues.NONE));
        claim("and none is a value of every component unset",
                () -> assertThat(ConfluenceValues.NONE).isEqualTo(new ConfluenceValues(null, null, null, null, null)));
    }
}
