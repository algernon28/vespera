package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel reads its Docker settings, the distribution and the wait, by their new names from the sources its
 * fields start from, and by their earlier names where no source gives the new one, the new name winning whichever source
 * gives each; and it says each earlier name set in the jar's line (ADR-433 §5, amending ADR-390 §1 and ADR-323 §7).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("891")
@Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
class TheControlPanelReadsTheSettingsEarlierNamesTest {

    private static final String DISTRIBUTION = "vespera.containers.wsl-distribution";

    private static final String WAIT = "vespera.containers.wait-at-most";

    private static final String EARLIER_DISTRIBUTION = "vespera.sidecars.wsl-distribution";

    private static final String EARLIER_WAIT = "vespera.sidecars.wait-at-most";

    /** The compose folder the window's settings name: none, which stands for the folder beside the jar (ADR-430 §12). */
    private static final Path BESIDE_THE_JAR = null;

    @TempDir
    Path folder;

    @Test
    @Story("The settings' earlier names are still read")
    @DisplayName("The control panel reads the earlier distribution from the environment where nothing names the new one, the new wait from application.yaml over the earlier one in .env, and says both earlier names")
    void theControlPanelReadsTheEarlierNamesFromItsSourcesAndSaysThem() {
        Path lockFolder = folder.resolve("root-locks");
        Path profileFolder = folder.resolve("profile");
        StartingValues.Sources sources = new StartingValues.Sources(Map.of(),
                Map.of(StartingValues.variableOf(EARLIER_DISTRIBUTION), "Ubuntu-24.04"),
                Map.of(StartingValues.variableOf(EARLIER_WAIT), "5m"),
                Map.of(WAIT, "2m", EARLIER_DISTRIBUTION, ""));

        DockerControl.Settings read = ControlPanel.dockerSettings(sources, lockFolder, profileFolder);

        claim("the distribution is the earlier variable's, Ubuntu-24.04, no source naming the new one; the wait is"
                        + " application.yaml's two minutes under the new name, over .env's five under the earlier one",
                () -> assertThat(read).isEqualTo(new DockerControl.Settings(
                        "Ubuntu-24.04", Duration.ofMinutes(2), lockFolder, profileFolder, BESIDE_THE_JAR, sources)));
        claim("each earlier name set is said in the jar's line, the distribution's first; the blank one in"
                        + " application.yaml sets nothing and is said only for the environment's",
                () -> assertThat(ControlPanel.earlierSettingNames(sources)).containsExactly(
                        lineFor(EARLIER_DISTRIBUTION, DISTRIBUTION), lineFor(EARLIER_WAIT, WAIT)));

        StartingValues.Sources newOnly = new StartingValues.Sources(Map.of(),
                Map.of(StartingValues.variableOf(DISTRIBUTION), "Debian"), Map.of(), Map.of(WAIT, "2m"));
        claim("where only the new names are set, nothing is said",
                () -> assertThat(ControlPanel.earlierSettingNames(newOnly)).isEmpty());
    }

    /** ADR-433 §5's line for an earlier name set, the jar's words. */
    private static String lineFor(String earlier, String now) {
        return earlier + " is set: it is the earlier name of " + now + ", and is read only where " + now + " is not set."
                + " Rename it: a later major version reads only " + now + ".";
    }
}
