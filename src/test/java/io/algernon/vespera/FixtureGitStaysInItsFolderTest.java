package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fixture repository is built in its own folder even when the process that runs the tests carries git's location
 * variables (ADR-290).
 *
 * <p>The outer repository is a throwaway one under a temporary folder, never this checkout. The variables are
 * {@code GIT_DIR} and {@code GIT_INDEX_FILE}, as the children of a git hook inherit them.
 */
@Epic("Build")
@Feature("Test fixtures")
@Issue("591")
@Link(name = "ADR-290", url = Adr.A_FIXTURE_REPOSITORY_IS_RUN_WITHOUT_GITS_LOCATION_VARIABLES, type = "adr")
class FixtureGitStaysInItsFolderTest {

    @TempDir
    Path folder;

    @Test
    @Story("A fixture repository is built where the test says, whatever the process inherited")
    @DisplayName("A fixture committed under an inherited repository location leaves that repository as it was")
    void aFixtureDoesNotCommitIntoTheRepositoryTheEnvironmentNames() throws IOException {
        assumeTrue(FixtureGit.runs(), "git is not on the path");
        Path outer = Files.createDirectories(folder.resolve("outer"));
        FixtureGit.git(outer, "init", "--quiet");
        Files.writeString(outer.resolve("outer.txt"), "outer");
        FixtureGit.git(outer, "add", "--all");
        FixtureGit.git(outer, "commit", "--quiet", "--message", "outer");
        String outerHead = FixtureGit.git(outer, "rev-parse", "HEAD");
        String outerStatus = FixtureGit.git(outer, "status", "--porcelain");

        Map<String, String> inherited = new HashMap<>(System.getenv());
        inherited.put("GIT_DIR", outer.resolve(".git").toString());
        inherited.put("GIT_INDEX_FILE", outer.resolve(".git").resolve("index").toString());

        Path fixture = Files.createDirectories(folder.resolve("fixture"));
        FixtureGit.git(fixture, inherited, "init", "--quiet");
        Files.writeString(fixture.resolve("fixture.txt"), "fixture");
        FixtureGit.git(fixture, inherited, "add", "--all");
        FixtureGit.git(fixture, inherited, "commit", "--quiet", "--message", "fixture");

        claim(
                "the outer repository the inherited variables name still has the commit it had, and no change"
                        + " waiting to be committed",
                () -> {
                    assertThat(FixtureGit.git(outer, "rev-parse", "HEAD")).isEqualTo(outerHead);
                    assertThat(FixtureGit.git(outer, "status", "--porcelain")).isEqualTo(outerStatus);
                });
        claim(
                "and the fixture's own folder is the repository that holds the fixture's commit",
                () -> assertThat(FixtureGit.git(fixture, "log", "--format=%s")).isEqualTo("fixture"));
    }
}
