package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.MisshapenProfileException;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * The start-up half of #321: a {@code profile.yaml} that does not load is caught before the context
 * exists, and said in place of Spring's account of the failure — and nothing else is.
 *
 * <p>{@code ProfileThatDoesNotLoadIT} launches the jar and sees the whole invocation. What it cannot
 * see is the two edges this pins: that the reporter claims no other failure, so a start-up that fails
 * for any other reason still shows its stack, and that the profile's fault is still recognised when it
 * arrives wrapped in a bean's creation failure, as it would if the file changed after the check.
 */
@Epic("Census")
@Feature("Profile")
@Issue("321")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class ProfileThatDoesNotLoadAtStartTest {

    /** What the README's Step 0 used to lead an operator to write. */
    private static final String A_KEY_WRITTEN_FLAT = "seedFolder: /seeds\n";

    /** A name Spring would give the first bean to read the profile, which the operator never wrote. */
    private static final String A_BEAN_THAT_ASKED_FIRST = "degenerateOutputConfidenceFloor";

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A profile that does not load stops the start while the environment is prepared")
    void aProfileThatDoesNotLoadStopsTheStartBeforeAnyBean(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), A_KEY_WRITTEN_FLAT);

        Throwable stopped = catchThrowable(() -> new ProfileShapeCheck().onApplicationEvent(prepared(workingDirectory)));

        claim("the check refuses the file with the profile's own fault, before any bean could ask for it",
                () -> assertThat(stopped).isInstanceOf(MisshapenProfileException.class));
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A working directory with no profile yet starts as it always did")
    void noProfileYetStartsAsBefore(@TempDir Path workingDirectory) {
        Throwable stopped = catchThrowable(() -> new ProfileShapeCheck().onApplicationEvent(prepared(workingDirectory)));

        claim("nothing is refused, since a corpus with no profile loads as every key unset",
                () -> assertThat(stopped).isNull());
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A profile that does not load is recognised even inside a bean's creation failure")
    void theFaultIsRecognisedInsideABeansFailure(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), A_KEY_WRITTEN_FLAT);
        Throwable misshapen = catchThrowable(() -> new ProfileStore(workingDirectory).load());
        BeanCreationException wrapped = new BeanCreationException(A_BEAN_THAT_ASKED_FIRST, "failed", misshapen);

        claim("the refusal claims it, so Spring prints no stack for it, and the exit code is the refusal's",
                () -> assertThat(new MisshapenProfileRefusal().reportException(wrapped)).isTrue());
        claim("and the entry point sees it for the same fault",
                () -> assertThat(MisshapenProfileRefusal.refuses(wrapped)).isTrue());
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("Any other failed start is left for Spring to report in full")
    void anyOtherFailedStartIsLeftAlone() {
        BeanCreationException unrelated = new BeanCreationException(
                A_BEAN_THAT_ASKED_FIRST, "failed", new IllegalStateException("something else entirely"));

        claim("the refusal does not claim it, so the stack of a failure it knows nothing about is still shown",
                () -> assertThat(new MisshapenProfileRefusal().reportException(unrelated)).isFalse());
        claim("and the entry point lets it through rather than exiting as though the profile were at fault",
                () -> assertThat(MisshapenProfileRefusal.refuses(unrelated)).isFalse());
    }

    private static ApplicationEnvironmentPreparedEvent prepared(Path workingDirectory) {
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[0],
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString()));
    }
}
