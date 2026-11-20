package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The jar reads the two settings by their new names, {@code vespera.containers.wsl-distribution} and {@code
 * vespera.containers.wait-at-most}, and by their earlier names where the new one is not set, a blank value setting
 * nothing; and each earlier name set is said in one line, whether or not the new one is set too (ADR-433 §5).
 *
 * <p>Each test builds an environment of its own, holding only what it names: the environment variables in a {@link
 * SystemEnvironmentPropertySource}, which reads a property from its variable as Spring's relaxed binding does, above an
 * {@code application.yaml}'s keys. So nothing of the machine the test runs on is read. The lines are those {@link
 * EarlierSettingNames#linesFor} gives, which the jar logs at WARN as it starts; that it logs them once an invocation is
 * not shown here.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("891")
@Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
class TheSettingsEarlierNamesAreStillReadTest {

    private static final String DISTRIBUTION = "vespera.containers.wsl-distribution";

    private static final String WAIT = "vespera.containers.wait-at-most";

    private static final String EARLIER_DISTRIBUTION = "vespera.sidecars.wsl-distribution";

    private static final String EARLIER_WAIT = "vespera.sidecars.wait-at-most";

    /** The earlier distribution's environment variable, as an operator set it for an earlier jar. */
    private static final String EARLIER_DISTRIBUTION_VARIABLE = "VESPERA_SIDECARS_WSL_DISTRIBUTION";

    private static final String NEW_DISTRIBUTION_VARIABLE = "VESPERA_CONTAINERS_WSL_DISTRIBUTION";

    /** The wait where nothing names one: the code's own 180 seconds (ADR-323 §7). */
    private static final Duration THE_DEFAULT_WAIT = Duration.ofSeconds(180);

    @Test
    @Story("The settings' earlier names are still read")
    @DisplayName("Where only the earlier names are set, the distribution from the environment and the wait from application.yaml, each is read as its new name and said in one line")
    void anEarlierNameAloneIsReadAsTheNewOneAndSaidInOneLine() {
        ConfigurableEnvironment environment = environmentOf(
                Map.of(EARLIER_DISTRIBUTION_VARIABLE, "Ubuntu-24.04"),
                Map.of(EARLIER_WAIT, "5m"));

        ContainerStartSettings read = ContainerStartSettings.from(environment);

        claim("the distribution is the earlier variable's, Ubuntu-24.04, and the wait the earlier key's, five minutes",
                () -> assertThat(List.of(read.wslDistribution(), read.waitAtMost()))
                        .containsExactly("Ubuntu-24.04", Duration.ofMinutes(5)));
        claim("each earlier name is said in one line, by its property's name, the distribution's first, each line whole",
                () -> assertThat(EarlierSettingNames.linesFor(environment))
                        .containsExactly(lineFor(EARLIER_DISTRIBUTION, DISTRIBUTION), lineFor(EARLIER_WAIT, WAIT)));
    }

    @Test
    @Story("The settings' earlier names are still read")
    @DisplayName("Where both names are set, the new one wins whichever source gives each, and the earlier one is still said")
    void theNewNameWinsWhereBothAreSetAndTheEarlierIsStillSaid() {
        // The earlier names in the environment, which comes before application.yaml; the new names in application.yaml.
        ConfigurableEnvironment environment = environmentOf(
                Map.of(EARLIER_DISTRIBUTION_VARIABLE, "Ubuntu-24.04", "VESPERA_SIDECARS_WAIT_AT_MOST", "5m"),
                Map.of(DISTRIBUTION, "Debian", WAIT, "2m"));

        ContainerStartSettings read = ContainerStartSettings.from(environment);

        claim("the new names are read, Debian and two minutes, though the earlier ones come from a source before them",
                () -> assertThat(List.of(read.wslDistribution(), read.waitAtMost()))
                        .containsExactly("Debian", Duration.ofMinutes(2)));
        claim("and each earlier name set is still said, in the same two lines",
                () -> assertThat(EarlierSettingNames.linesFor(environment))
                        .containsExactly(lineFor(EARLIER_DISTRIBUTION, DISTRIBUTION), lineFor(EARLIER_WAIT, WAIT)));

        ConfigurableEnvironment newOnly = environmentOf(Map.of(NEW_DISTRIBUTION_VARIABLE, "Debian"), Map.of(WAIT, "2m"));
        claim("where only the new names are set, the new distribution's variable among them, they are read and nothing"
                        + " is said",
                () -> assertThat(List.of(ContainerStartSettings.from(newOnly).wslDistribution(),
                                ContainerStartSettings.from(newOnly).waitAtMost(),
                                EarlierSettingNames.linesFor(newOnly)))
                        .containsExactly("Debian", Duration.ofMinutes(2), List.of()));
    }

    @Test
    @Story("The settings' earlier names are still read")
    @DisplayName("The blank distribution an earlier jar wrote in application.yaml sets nothing and is not said, and the wait is the code's own")
    void aBlankEarlierNameSetsNothingAndSaysNothing() {
        // What every application.yaml an earlier jar wrote beside itself held, but its wait, which is left out here.
        ConfigurableEnvironment environment = environmentOf(Map.of(), Map.of(EARLIER_DISTRIBUTION, ""));

        ContainerStartSettings read = ContainerStartSettings.from(environment);

        claim("the distribution is blank, WSL's default, and the wait the code's 180 seconds",
                () -> assertThat(List.of(read.wslDistribution(), read.waitAtMost()))
                        .containsExactly("", THE_DEFAULT_WAIT));
        claim("and no line is said", () -> assertThat(EarlierSettingNames.linesFor(environment)).isEmpty());
    }

    /** ADR-433 §5's line for an earlier name set. */
    static String lineFor(String earlier, String now) {
        return earlier + " is set: it is the earlier name of " + now + ", and is read only where " + now + " is not set."
                + " Rename it: a later major version reads only " + now + ".";
    }

    /** An environment of the variables given, above an {@code application.yaml} of the keys given, and nothing else. */
    private static ConfigurableEnvironment environmentOf(Map<String, Object> variables, Map<String, Object> yaml) {
        ConfigurableEnvironment environment = new AbstractEnvironment() {
        };
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("systemEnvironment", variables));
        environment.getPropertySources().addLast(new MapPropertySource("application.yaml", yaml));
        return environment;
    }
}
