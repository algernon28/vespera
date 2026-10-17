package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An operator whose configuration still sets {@code vespera.account-dir}, as one machine's
 * {@code application-local.yaml} does, gets the invocation account in the working directory's {@code logs}
 * folder all the same, and one warning saying so; the run is not refused for it (ADR-270 section 3).
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The invocation account")
@Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
class TheAccountFolderSettingIsIgnoredInvocationTest {

    /** How an account's file name starts. */
    private static final String ACCOUNT_PREFIX = "invocation-account-";

    /** How the one line for a configured {@code vespera.account-dir} begins; the folder written to follows. */
    private static final String THE_SETTING_IS_NO_LONGER_READ =
            "vespera.account-dir is no longer read, and the invocation account is written to ";

    @TempDir
    static Path workingDirectory;

    /** The folder the old setting names, outside the working directory, as an operator's machine had it. */
    @TempDir
    static Path accountDirectory;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.account-dir", accountDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Test
    @Story("The account is written in the working directory's logs folder")
    @DisplayName("A run with vespera.account-dir still configured writes its account in the working directory's logs folder, and one line says so")
    void aConfiguredAccountFolderIsIgnoredWithOneLine(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");

        List<String> warned = warningsOfTheAccountWhile(() -> cli.run("run", root.toString()));

        claim(
                "the folder vespera.account-dir names gained nothing",
                () -> assertThat(namesIn(accountDirectory)).isEmpty());
        Path logs = workingDirectory.resolve("logs");
        claim("the working directory has a logs folder", () -> assertThat(logs).isDirectory());
        claim(
                "and the one account of the invocation is in it",
                () -> assertThat(namesIn(logs)).filteredOn(name -> name.startsWith(ACCOUNT_PREFIX)).hasSize(1));
        claim(
                "the account's one warning says the setting is no longer read and names the logs folder, so the"
                        + " operator's configuration does not stop the run",
                () -> assertThat(warned)
                        .containsExactly(THE_SETTING_IS_NO_LONGER_READ + workingDirectory.toAbsolutePath().resolve("logs")));
    }

    /** The warnings {@link InvocationAccount}'s logger wrote while {@code invocation} ran, as the operator reads them. */
    private static List<String> warningsOfTheAccountWhile(Runnable invocation) {
        Logger logger = (Logger) LoggerFactory.getLogger(InvocationAccount.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            invocation.run();
        } finally {
            logger.detachAppender(logged);
        }
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
