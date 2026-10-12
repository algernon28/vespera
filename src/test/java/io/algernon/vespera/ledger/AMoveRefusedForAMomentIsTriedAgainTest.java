package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A page moved over its place is tried again where the move is refused as access denied, a few times with a
 * short wait between, and put in place once the move goes through (ADR-242). On Windows a program that has just
 * opened the page or its place, a virus scanner, an indexer or a folder kept in step with a cloud, without
 * letting it be deleted, makes the move fail that way for as long as it holds the file, and 6b's whole
 * invocation is rolled back on a failed move of its index.
 *
 * <p>No Windows here: the move is stood in for by one that is refused as Windows refuses it, a given number of
 * times, and then made as the platform makes it, and the waits are counted and not slept.
 */
@Epic("Ledger")
@Feature("Writing pages")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-242", url = Adr.A_MOVE_REFUSED_AS_ACCESS_DENIED_IS_ATTEMPTED_AGAIN, type = "adr")
class AMoveRefusedForAMomentIsTriedAgainTest {

    private static final String THE_OLD_PAGE = "the page written before";

    private static final String THE_NEW_PAGE = "the page written now";

    /** The reason Java gives for {@code ERROR_SHARING_VIOLATION} on an English Windows. */
    private static final String WINDOWS_SHARING_VIOLATION =
            "The process cannot access the file because it is being used by another process";

    /** How many times the stand-in refuses the move before it lets it through, fewer than the attempts made. */
    private static final int REFUSED_TWICE = 2;

    /** Whether the move stands in for one made on Windows, where a sharing violation has no type of its own. */
    private static final boolean ON_WINDOWS = true;

    /** How many attempts the move makes in all, the first and four more. */
    private static final int FIVE_ATTEMPTS = 5;

    /** The longest one wait may be, in milliseconds: a second, so that a page refused for a moment costs a moment. */
    private static final long A_SECOND = 1_000;

    /** The longest all the waits of one move may be together, in milliseconds. */
    private static final long TWO_SECONDS = 2_000;

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("A page whose move is refused twice as access denied is moved on the third attempt, after two short waits")
    void aMoveRefusedTwiceIsMadeOnTheThirdAttempt(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};

        Throwable thrown = catchThrowable(
                () -> new ReplacingMove(refusedAsAccessDenied(REFUSED_TWICE, attempts), waited::add).intoPlace(part, target));

        claim("the move went through, and nothing was thrown", () -> assertThat(thrown).isNull());
        claim(
                "the page in its place is the new one, and the page beside it is gone",
                () -> {
                    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(THE_NEW_PAGE);
                    assertThat(part).doesNotExist();
                });
        claim(
                "it was attempted three times, the first two refused, with a wait after each refusal, the second"
                        + " wait longer than the first and neither longer than a second",
                () -> {
                    assertThat(attempts[0]).isEqualTo(REFUSED_TWICE + 1);
                    assertThat(waited).hasSize(REFUSED_TWICE);
                    assertThat(waited.get(0)).isPositive().isLessThan(waited.get(1));
                    assertThat(waited.get(1)).isLessThanOrEqualTo(A_SECOND);
                });
    }

    /**
     * On Windows a program holding the page open without letting others share it makes the move fail with
     * {@code ERROR_SHARING_VIOLATION}, which Java gives as a plain {@link FileSystemException} carrying no code
     * and a reason in the machine's language (ADR-244, amending ADR-242 section 3).
     */
    @Test
    @Story("A page is put in its place whole")
    @DisplayName("On Windows, a page whose move is refused twice because another program is using the file is moved on the third attempt, after two short waits")
    @Link(name = "ADR-244", url = Adr.THE_FLOORS_ANSWERS_ARE_UNDER_THE_RUNS_IDENTITY_AND_A_SHARING_VIOLATION_IS_TRIED_AGAIN, type = "adr")
    void aMoveRefusedTwiceAsASharingViolationIsMadeOnTheThirdAttempt(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};

        Throwable thrown = catchThrowable(() -> new ReplacingMove(
                        (from, to) -> {
                            attempts[0]++;
                            if (attempts[0] <= REFUSED_TWICE) {
                                throw new FileSystemException(from.toString(), to.toString(), WINDOWS_SHARING_VIOLATION);
                            }
                            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        },
                        waited::add,
                        ON_WINDOWS)
                .intoPlace(part, target));

        claim("the move went through, and nothing was thrown", () -> assertThat(thrown).isNull());
        claim(
                "the page in its place is the new one, and the page beside it is gone",
                () -> {
                    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(THE_NEW_PAGE);
                    assertThat(part).doesNotExist();
                });
        claim(
                "it was attempted three times, the first two refused, with a wait after each refusal",
                () -> {
                    assertThat(attempts[0]).isEqualTo(REFUSED_TWICE + 1);
                    assertThat(waited).hasSize(REFUSED_TWICE);
                });
    }

    /**
     * Away from Windows no program holding a file makes its move fail with an error Java has no more specific
     * type for: such a failure is a disk that is read-only or full, a directory in the page's place or a fault of
     * the device, which no wait lifts (ADR-245, amending ADR-244).
     */
    @Test
    @Story("A page is put in its place whole")
    @DisplayName("Away from Windows, a move that fails with an error of no more specific type is not attempted again")
    @Link(name = "ADR-245", url = Adr.A_PAGE_IS_ASKED_WHETHER_IT_IS_KEPT_AND_A_SHARING_VIOLATION_IS_TRIED_AGAIN_ONLY_ON_WINDOWS, type = "adr")
    void awayFromWindowsAnUntypedFailureIsNotTriedAgain(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};
        FileSystemException readOnly =
                new FileSystemException(part.toString(), target.toString(), "Read-only file system");

        Throwable thrown = catchThrowable(() -> new ReplacingMove(
                        (from, to) -> {
                            attempts[0]++;
                            throw readOnly;
                        },
                        waited::add,
                        !ON_WINDOWS)
                .intoPlace(part, target));

        claim(
                "the failure is thrown at once, after one attempt and no wait",
                () -> {
                    assertThat(thrown).isSameAs(readOnly);
                    assertThat(attempts[0]).isOne();
                    assertThat(waited).isEmpty();
                });
        claim(
                "the page in its place is the old one, and the new one is still beside it",
                () -> {
                    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(THE_OLD_PAGE);
                    assertThat(Files.readString(part, StandardCharsets.UTF_8)).isEqualTo(THE_NEW_PAGE);
                });
    }

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("A page whose move is refused at every attempt is left beside its place after five attempts, and the refusal is thrown")
    void aMoveRefusedEveryTimeGivesUpAfterTheLastAttempt(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};

        Throwable thrown = catchThrowable(() ->
                new ReplacingMove(refusedAsAccessDenied(Integer.MAX_VALUE, attempts), waited::add).intoPlace(part, target));

        claim(
                "the refusal is thrown, as Windows gave it, once the attempts are spent",
                () -> assertThat(thrown).isInstanceOf(AccessDeniedException.class));
        claim(
                "it was attempted " + FIVE_ATTEMPTS + " times, the first and four more, with a wait between each two"
                        + " and none after the last, the waits together no longer than two seconds",
                () -> {
                    assertThat(attempts[0]).isEqualTo(FIVE_ATTEMPTS);
                    assertThat(waited).hasSize(FIVE_ATTEMPTS - 1);
                    assertThat(waited.stream().mapToLong(Long::longValue).sum()).isLessThanOrEqualTo(TWO_SECONDS);
                });
        claim(
                "the page in its place is the old one, and the new one is still beside it",
                () -> {
                    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(THE_OLD_PAGE);
                    assertThat(Files.readString(part, StandardCharsets.UTF_8)).isEqualTo(THE_NEW_PAGE);
                });
    }

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("A move that fails for a reason Java names, other than access denied, is not attempted again")
    void anotherFailureIsNotTriedAgain(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};
        FileSystemException unmovable =
                new NoSuchFileException(part.toString(), target.toString(), "the folder the page goes in is gone");

        Throwable thrown = catchThrowable(() -> new ReplacingMove(
                        (from, to) -> {
                            attempts[0]++;
                            throw unmovable;
                        },
                        waited::add)
                .intoPlace(part, target));

        claim(
                "the failure is thrown at once, after one attempt and no wait: only a refusal a program holding the"
                        + " file can lift is worth a second attempt",
                () -> {
                    assertThat(thrown).isSameAs(unmovable);
                    assertThat(attempts[0]).isOne();
                    assertThat(waited).isEmpty();
                });
    }

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("A move whose wait between attempts is interrupted stops there, says it was interrupted and keeps the interruption")
    void aMoveInterruptedWhileItWaitsStopsAndKeepsTheInterruption(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        int[] attempts = {0};

        Throwable thrown;
        boolean interruptedAfter;
        try {
            thrown = catchThrowable(() -> new ReplacingMove(
                            refusedAsAccessDenied(Integer.MAX_VALUE, attempts),
                            millis -> {
                                throw new InterruptedException("stopped while waiting");
                            })
                    .intoPlace(part, target));
        } finally {
            // Read and cleared here, so that no later test runs on a thread left interrupted.
            interruptedAfter = Thread.interrupted();
        }

        claim(
                "an interrupted wait is thrown as an interrupted read or write, carrying the refusal it was waiting"
                        + " to try again",
                () -> {
                    assertThat(thrown).isInstanceOf(InterruptedIOException.class);
                    assertThat(thrown.getSuppressed()).singleElement().isInstanceOf(AccessDeniedException.class);
                });
        claim(
                "the move is not attempted again after the interruption, and the thread is still marked interrupted",
                () -> {
                    assertThat(attempts[0]).isOne();
                    assertThat(interruptedAfter).isTrue();
                });
        claim(
                "the page in its place is the old one, and the new one is still beside it",
                () -> {
                    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(THE_OLD_PAGE);
                    assertThat(Files.readString(part, StandardCharsets.UTF_8)).isEqualTo(THE_NEW_PAGE);
                });
    }

    /** A move refused as Windows refuses one on a file held open, {@code refusals} times, then made. */
    private static ReplacingMove.Move refusedAsAccessDenied(int refusals, int[] attempts) {
        return (part, target) -> {
            attempts[0]++;
            if (attempts[0] <= refusals) {
                throw new AccessDeniedException(part.toString(), target.toString(), null);
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        };
    }

    private static Path page(Path file, String text) throws IOException {
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
