package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera run} takes the corpus-root lock it is handed before it walks anything, and a root another invocation
 * walks from another working directory stops it in one line, exit 1 (ADR-279 §4, §7).
 *
 * <p>{@code VesperaApplication.main} is what registers the lock, so a context built by Spring's test support has none;
 * this one is handed a lock on a folder of its own, as main would hand it, and the root is taken first by another
 * {@link CorpusRootLock} on that folder, naming another working directory. {@link CorpusRootLockTest} holds the lock
 * itself, a second process and a killed holder among it; this holds that {@code run} asks it, and stops on its refusal.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, ACorpusRootAnotherInvocationWalksIsRefusedInvocationTest.TheLockMainHandsOn.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Architecture")
@Feature("One invocation per corpus root")
@Issue("559")
@Link(name = "ADR-279", url = Adr.ONE_VESPERA_RUN_PER_CORPUS_ROOT, type = "adr")
class ACorpusRootAnotherInvocationWalksIsRefusedInvocationTest {

    /** picocli's exit code for a refusal of the start, which the jar's process ends with. */
    private static final int REFUSED = 1;

    /** The arguments the other invocation was started with, as its holder line records them. */
    private static final String[] THE_OTHER_INVOCATIONS_ARGUMENTS = {"run", "the-same-root"};

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path lockFolder;

    @TempDir
    static Path theOtherWorkingDirectory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    /** The bean {@code VesperaApplication.main} registers, on this class's folder in place of the user's own. */
    @TestConfiguration
    static class TheLockMainHandsOn {

        @Bean
        CorpusRootLock corpusRootLock() {
            return new CorpusRootLock(lockFolder);
        }
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void startWithNothingHeld() {
        CorpusRootLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        CorpusRootLock.release();
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("A run on a corpus root another invocation walks is refused in one line, exit 1, before anything is walked")
    void aRunOnACorpusRootAnotherInvocationWalksIsRefused(CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        new CorpusRootLock(lockFolder).take(root, theOtherWorkingDirectory, THE_OTHER_INVOCATIONS_ARGUMENTS);
        String holder = Files.readString(lockFolder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(root))),
                        StandardCharsets.UTF_8)
                .strip();
        String refusal = new CorpusRootInUseException(root, holder).getMessage();
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends with exit code " + REFUSED + ", a refusal of its start",
                () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("exactly one line is the refusal, naming the root and quoting the other invocation's holder line",
                () -> assertThat(said.lines().filter(refusal::equals).toList()).hasSize(1));
        claim("no walk was recorded and no run minted, so the root was not walked",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT (SELECT COUNT(*) FROM walk) + (SELECT COUNT(*) FROM run)", Integer.class))
                        .containsExactly(0));
        claim("and no progress page was written in the working directory",
                () -> assertThat(ProgressPage.pathIn(workingDirectory)).doesNotExist());
    }
}
