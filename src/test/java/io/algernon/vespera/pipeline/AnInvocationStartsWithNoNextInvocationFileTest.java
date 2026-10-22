package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * An invocation deletes the file an earlier one left for the control panel as it takes the working-directory lock,
 * so an invocation that is refused or fails afterwards leaves none, and only the holder of the lock deletes it
 * (ADR-294 §3).
 *
 * <p>The lock is taken as {@code VesperaApplication.main} has it taken, by the listener hearing the environment
 * prepared; the listener keeps what it holds for the life of the JVM, so each test hands it back.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class AnInvocationStartsWithNoNextInvocationFileTest {

    /** The file an earlier invocation left, beside the database file. */
    private static final String FILE_NAME = "next-invocation.properties";

    /** What an earlier invocation that ended on its closing line left in it. */
    private static final String AN_EARLIER_INVOCATIONS_FILE = "invocation=label\nwants=\nanswer=true\n";

    private static final String[] ARGUMENTS = {"run", "archive-root"};

    @BeforeEach
    void startWithNothingHeld() {
        WorkingDirectoryLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        WorkingDirectoryLock.release();
    }

    @Test
    @Story("An invocation starts with no file from the one before")
    @DisplayName("Taking the working directory deletes the file an earlier invocation left")
    void takingTheWorkingDirectoryDeletesAnEarlierFile(@TempDir Path workingDirectory) throws IOException {
        Path earlier = Files.writeString(workingDirectory.resolve(FILE_NAME), AN_EARLIER_INVOCATIONS_FILE,
                StandardCharsets.UTF_8);

        new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory));

        claim("once the invocation holds the working directory, the earlier invocation's file is gone",
                () -> assertThat(earlier).doesNotExist());
    }

    @Test
    @Story("An invocation starts with no file from the one before")
    @DisplayName("A file that cannot be deleted stops the invocation from starting")
    void aFileThatCannotBeDeletedFailsTheStart(@TempDir Path workingDirectory) throws IOException {
        Path inItsPlace = Files.createDirectory(workingDirectory.resolve(FILE_NAME));
        Files.writeString(inItsPlace.resolve("kept"), AN_EARLIER_INVOCATIONS_FILE, StandardCharsets.UTF_8);

        Throwable failed = catchThrowable(() -> new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory)));

        claim("taking the working directory fails, a folder holding a file standing where the earlier file was",
                () -> assertThat(failed).isInstanceOf(UncheckedIOException.class));
        claim("and what stands there is left as it was",
                () -> assertThat(inItsPlace.resolve("kept")).hasContent(AN_EARLIER_INVOCATIONS_FILE));
    }

    @Test
    @Story("An invocation starts with no file from the one before")
    @DisplayName("An invocation refused the working directory leaves the holder's file where it is")
    void aRefusedTakeLeavesTheHoldersFile(@TempDir Path workingDirectory) throws IOException {
        new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory));
        Path holders = Files.writeString(workingDirectory.resolve(FILE_NAME), AN_EARLIER_INVOCATIONS_FILE,
                StandardCharsets.UTF_8);

        Throwable refused = catchThrowable(() -> new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory)));

        claim("the second take is refused, the directory being held",
                () -> assertThat(refused).isInstanceOf(WorkingDirectoryInUseException.class));
        claim("and the file the holder wrote is still there, as it wrote it",
                () -> assertThat(holders).hasContent(AN_EARLIER_INVOCATIONS_FILE));
    }

    private static ApplicationEnvironmentPreparedEvent prepared(Path workingDirectory) {
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                ARGUMENTS,
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString()));
    }
}
