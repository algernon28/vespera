package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
/**
 * Stage 5's last step, driven through a whole invocation (ADR-088, #110): the page and the label
 * file have to exist where the operator will look for them, which is the one claim the rendering
 * tests beside this class cannot make.
 *
 * <p>A sibling of {@code RelevanceScoringInvocationTest}, on the same scripted extraction and
 * embedding beans, so the whole pipeline runs here without a Docker daemon.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
@Link(name = "ADR-337", url = Adr.EVERY_CLAIM_CAN_FAIL_AND_A_DECISION_IS_HELD_ONCE, type = "adr")
class RelevanceReportInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    /** The corpus root of the one invocation the three tests about a whole run read, built once for the class. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that invocation. */
    @TempDir
    static Path theSeeds;

    /**
     * What that invocation left in the working directory, read as soon as it ended: the working directory
     * outlives each test, and a later test writes the page and the label file again (#678).
     */
    private record Labelled(int exitCode, List<String> besideTheDatabase, String page, String labels, Profile profile) {}

    private static final Once<Labelled> labelled = new Once<>();

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    /**
     * One corpus document, one seed, and every gate up to the approval open: a whole invocation, run the
     * first time one of the three tests below asks, after clearing whatever an earlier test left here.
     */
    private Labelled labelled() {
        return labelled.get(() -> {
            Files.writeString(theRoot.resolve("corpus.txt"), "a corpus document");
            Files.writeString(theSeeds.resolve("seed.txt"), "a seed document");
            profile(theSeeds);
            whateverAnotherTestLeftHere();

            cli.run("run", theRoot.toString());

            List<String> besideTheDatabase;
            try (java.util.stream.Stream<Path> listed = Files.list(workingDirectory)) {
                besideTheDatabase = listed.filter(Files::isRegularFile)
                        .map(file -> file.getFileName().toString())
                        .toList();
            }
            return new Labelled(cli.getExitCode(), besideTheDatabase, page(), labelFile(), profileStore.load());
        });
    }

    @Test
    @Story("The threshold is pointed at, never answered")
    @DisplayName("The profile gains a pointer to the page and no threshold value")
    void pointsTheThresholdKeyAtThePageWithoutAnsweringIt() {
        Profile profile = labelled().profile();

        claim(
                "the threshold key names the page its value is meant to be read off, so an operator"
                        + " opening the profile is told where the number comes from",
                () -> assertThat(profile.relevanceScoreFloor().measurement().source())
                        .isEqualTo("relevance-labelling.html"));
        claim(
                "and the value itself is still unanswered: a threshold removes documents, and nothing"
                        + " here may choose one on a person's behalf",
                () -> assertThat(profile.relevanceScoreFloor().isSet()).isFalse());
    }

    /** The labelling page as this run wrote it, or nothing where it wrote none. */
    private String page() throws IOException {
        Path page = workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME);
        return Files.exists(page) ? Files.readString(page) : "";
    }

    /** The answer file as this run wrote it, or nothing where it wrote none. */
    private String labelFile() throws IOException {
        Path labels = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        return Files.exists(labels) ? Files.readString(labels) : "";
    }

    /**
     * Keeps one file from being opened for as long as it is held, the way a program the archive's owner
     * has open can.
     *
     * <p>On Windows, an exclusive lock over the whole file through a handle of its own: Windows refuses
     * a read through any other handle, this process's included. Elsewhere, every permission is taken
     * off the file and put back on release, which refuses everyone but a superuser. {@link
     * #refusesToOpen} is what the caller checks before claiming anything.
     */
    private static final class FileThatWillNotOpen implements AutoCloseable {

        private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

        private final Path file;
        private final FileChannel lockingChannel;
        private final Set<PosixFilePermission> permissionsBefore;

        private FileThatWillNotOpen(
                Path file, FileChannel lockingChannel, Set<PosixFilePermission> permissionsBefore) {
            this.file = file;
            this.lockingChannel = lockingChannel;
            this.permissionsBefore = permissionsBefore;
        }

        static FileThatWillNotOpen hold(Path file) throws IOException {
            if (WINDOWS) {
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                channel.lock();
                return new FileThatWillNotOpen(file, channel, null);
            }
            Set<PosixFilePermission> before = Files.getPosixFilePermissions(file);
            Files.setPosixFilePermissions(file, EnumSet.noneOf(PosixFilePermission.class));
            return new FileThatWillNotOpen(file, null, before);
        }

        /** Whether reading the file actually fails here, which is the premise every claim rests on. */
        boolean refusesToOpen() {
            try (InputStream in = Files.newInputStream(file)) {
                in.read();
                return false;
            } catch (IOException refused) {
                return true;
            }
        }

        /** Releases the lock, or puts the permissions back, so the file can be read and cleaned up. */
        @Override
        public void close() throws IOException {
            if (lockingChannel != null) {
                lockingChannel.close();
            } else {
                Files.setPosixFilePermissions(file, permissionsBefore);
            }
        }
    }

    /**
     * Clears the two files this step writes, so a claim about them is about this invocation.
     *
     * <p>The working directory is static, so it outlives each test method -- a page left by the test
     * that asserts it gets written would otherwise satisfy a claim that this invocation wrote none.
     */
    private void whateverAnotherTestLeftHere() throws IOException {
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }
}
