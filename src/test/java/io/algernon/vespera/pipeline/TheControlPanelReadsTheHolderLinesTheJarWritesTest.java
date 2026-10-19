package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.HolderLine;
import io.algernon.controlpanel.HolderReading;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel parses the holder line itself, linking no class of the jar (ADR-285 §3); this is the contract
 * that holds its parse to what the jar's two writers, {@link WorkingDirectoryLock} and {@link CorpusRootLock}, write,
 * on a test class path that sees both. Each lock is held by a second JVM, as an invocation the control panel did not
 * start would hold it (ADR-278 §4).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
class TheControlPanelReadsTheHolderLinesTheJarWritesTest {

    /** The first two fields of every holder line, as the jar writes them. */
    private static final Pattern PID_AND_STARTED = Pattern.compile("pid=(\\d+) started=(\\S+) .*");

    private static final long HOLDER_END_BOUND_SECONDS = 30;

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("The working-directory lock's holder line is read whole, with its holder's pid, and held while that holder lives")
    void theWorkingDirectoryLocksHolderLineIsReadAsHeldWhileItsHolderLives(@TempDir Path workingDirectory)
            throws Exception {
        Path lockFile = workingDirectory.resolve(WorkingDirectoryLock.FILE_NAME);
        Process holder = WorkingDirectoryLockTest.startHolder(workingDirectory);
        try {
            readsWhatWasWritten(lockFile, holder);
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
        claim("once its holder is killed, the line it left is read as not held",
                () -> assertThat(HolderReading.read(lockFile).held()).isFalse());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("The corpus-root lock's holder line is read whole, with its holder's pid, and held while that holder lives")
    void theCorpusRootLocksHolderLineIsReadAsHeldWhileItsHolderLives(@TempDir Path temporary) throws Exception {
        Path folder = Files.createDirectory(temporary.resolve("root-locks"));
        Path corpusRoot = Files.createDirectory(temporary.resolve("corpus"));
        Path workingDirectory = Files.createDirectory(temporary.resolve("work"));
        Path lockFile = folder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(corpusRoot)));
        Process holder = HoldsTheCorpusRoot.start(folder, corpusRoot, workingDirectory);
        try {
            readsWhatWasWritten(lockFile, holder);
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
        claim("once its holder is killed, the line it left is read as not held",
                () -> assertThat(HolderReading.read(lockFile).held()).isFalse());
    }

    private static void readsWhatWasWritten(Path lockFile, Process holder) throws Exception {
        String written = Files.readString(lockFile, StandardCharsets.UTF_8).strip();
        Matcher fields = PID_AND_STARTED.matcher(written);
        claim("the jar wrote a holder line that opens with its pid and started fields",
                () -> assertThat(fields.matches()).isTrue());
        HolderReading read = HolderReading.read(lockFile);

        claim("the control panel reads that line whole, with the holder's pid and the instant its started field names",
                () -> assertThat(read.line()).contains(new HolderLine(
                        holder.pid(), OffsetDateTime.parse(fields.group(2)).toInstant(), written)));
        claim("and reads it as held while its holder lives",
                () -> assertThat(read.held()).isTrue());
    }
}
