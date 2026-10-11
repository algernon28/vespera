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
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
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

    /** How many times the stand-in refuses the move before it lets it through, fewer than the attempts made. */
    private static final int REFUSED_TWICE = 2;

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
    @DisplayName("A move that fails for any other reason than access denied is not attempted again")
    void anotherFailureIsNotTriedAgain(@TempDir Path directory) throws IOException {
        Path part = page(directory.resolve("index.md.part"), THE_NEW_PAGE);
        Path target = page(directory.resolve("index.md"), THE_OLD_PAGE);
        List<Long> waited = new ArrayList<>();
        int[] attempts = {0};
        FileSystemException unmovable = new FileSystemException(part.toString(), target.toString(), "the disk is full");

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
