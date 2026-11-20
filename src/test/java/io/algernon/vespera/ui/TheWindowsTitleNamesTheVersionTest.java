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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's title names the version of Vespera it is (ADR-430 §5), read from its own jar's manifest with its
 * own code, since it links no class of the jar (ADR-430 §4, ADR-285 §3). The classes under test lie in no jar, so no
 * manifest names a version and the title reads {@code unknown}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("904")
@Link(name = "ADR-430", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
class TheWindowsTitleNamesTheVersionTest {

    /** The title for classes that lie in no jar: the window's name, a space and the word for no version named. */
    private static final String THE_TITLE_OUTSIDE_A_JAR = "Vespera control panel unknown";

    @Test
    @Story("Vespera says which version it is")
    @DisplayName("The window's title names the control panel and Vespera's version")
    void theTitleIsTheControlPanelAndTheReleaseVersion(@TempDir Path folder) throws Exception {
        String title;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            title = window.read(root -> window.stage.getTitle());
        }

        claim("the title is Vespera control panel and the version, unknown for classes that lie in no jar",
                () -> assertThat(title).isEqualTo(THE_TITLE_OUTSIDE_A_JAR));
    }
}
