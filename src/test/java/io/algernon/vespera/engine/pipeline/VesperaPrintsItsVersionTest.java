package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * {@code vespera --version} prints one line, {@code Vespera} and the release version (ADR-431 §5), through a version
 * provider {@link VesperaCommand} names. The classes under test lie in no jar, so no manifest names a version and the
 * line reads {@code unknown} (ADR-431 §4).
 *
 * <p>The provider is read off the command's annotation and asked directly, so that no Spring context and no
 * subcommand is built.
 */
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-431", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
class VesperaPrintsItsVersionTest {

    /** The one line printed for classes that lie in no jar: the name, a space and the word for no version named. */
    private static final String THE_LINE_OUTSIDE_A_JAR = "Vespera unknown";

    /** The package every class of Vespera's own lies under. */
    private static final String THE_APPLICATION = "io.algernon.vespera.";

    @Test
    @Story("Vespera says which version it is")
    @DisplayName("Asked for its version, Vespera prints one line naming itself and its version")
    void theVersionLineNamesVesperaAndItsReleaseVersion() throws Exception {
        Class<? extends CommandLine.IVersionProvider> named =
                VesperaCommand.class.getAnnotation(Command.class).versionProvider();

        claim("the command names a version provider of Vespera's own, where picocli's default prints nothing",
                () -> assertThat(named.getName()).startsWith(THE_APPLICATION));
        claim("its version is the one line Vespera and the version, unknown for classes that lie in no jar",
                () -> assertThat(versionOf(named)).containsExactly(THE_LINE_OUTSIDE_A_JAR));
    }

    private static String[] versionOf(Class<? extends CommandLine.IVersionProvider> named) throws Exception {
        Constructor<? extends CommandLine.IVersionProvider> made = named.getDeclaredConstructor();
        made.setAccessible(true);
        return made.newInstance().getVersion();
    }
}
