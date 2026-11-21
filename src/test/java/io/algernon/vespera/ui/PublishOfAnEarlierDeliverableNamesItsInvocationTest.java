package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command line of a publish the window starts with an earlier deliverable chosen (ADR-439 §7): {@code publish
 * --invocation <id> --to <destinations>}, {@code --to} the last of the command's own arguments, before {@code
 * --db-dir}, so that a publish to Confluence is still given the token typed; and an invocation not of the form of an
 * invocation's id refused, as a destination outside the closed list is.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("923")
@Link(name = "ADR-439", url = Adr.A_PUBLISH_MAY_NAME_AN_EARLIER_RUN_BY_ITS_INVOCATION, type = "adr")
class PublishOfAnEarlierDeliverableNamesItsInvocationTest {

    private static final Path JAVA = Path.of(System.getProperty("java.home"), "bin", "java");

    /** Relative on purpose, so that each must be made absolute. */
    private static final Path JAR = Path.of("vespera.jar");

    private static final Path WORKING_DIRECTORY = Path.of("work");

    private static final LaunchValues THE_TWO = new LaunchValues("corpus", WORKING_DIRECTORY.toString(), "", "", "",
            "", "", "");

    /** An earlier invocation's id, the second of its second. */
    private static final String INVOCATION = "20261112T233000Z-2";

    /** A token as the operator types it, synthetic. */
    private static final String TOKEN = "TOKEN-typed-0f1e2d3c-not-a-real-one";

    @Test
    @Story("Publish delivers the earlier deliverable chosen")
    @DisplayName("A publish of an earlier deliverable names its run before the destinations, the destinations last before the working folder, and a publish to Confluence still carries the token")
    void theChildIsGivenTheInvocationBeforeItsDestinations() {
        ChildCommand publish = ChildCommand.publish(JAVA, JAR, THE_TWO, List.of("confluence", "website"), INVOCATION);
        List<String> command = publish.command();
        List<String> after = command.subList(command.indexOf("-jar") + 2, command.size());

        claim("after the jar come publish, --invocation and its id, --to and the destinations joined by commas, then"
                        + " --db-dir and the working folder, absolute",
                () -> assertThat(after).containsExactly("publish", "--invocation", INVOCATION, "--to",
                        "confluence,website", "--db-dir=" + WORKING_DIRECTORY.toAbsolutePath()));
        claim("and given a token, it carries it in its environment, Confluence being among its destinations",
                () -> assertThat(publish.withConfluenceToken(TOKEN).environment())
                        .containsEntry("VESPERA_CONFLUENCE_TOKEN", TOKEN));
    }

    @Test
    @Story("Publish delivers the earlier deliverable chosen")
    @DisplayName("A run named by anything but a run's dated id is refused before a command line is built")
    void anInvocationNotOfAnIdsFormIsRefused() {
        claim("an id with a space and a word in it is refused, in the window's own words",
                () -> assertThatThrownBy(() -> ChildCommand.publish(JAVA, JAR, THE_TWO, List.of("website"),
                        "20261112T233000Z --to=elsewhere"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("an invocation outside the form of an invocation's id was named"));
    }
}
