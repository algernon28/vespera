package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingCallRejectedException;
import io.algernon.vespera.extraction.DoclingConnectionLostException;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * A call dispatched ahead that failed is handed to the step as the failure it was (ADR-175, ADR-140).
 * The step tells a rejection and a lost connection apart by type, so a conversion placed on a worker has
 * to fail on the step's thread exactly as one placed there would have.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("326")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
class PendingConversionsTest {

    /** The document the dispatched call was for; nothing here turns on which. */
    private static final OccurrenceId OCCURRENCE = new OccurrenceId(7);

    /** The file that document was read from; it is never opened here. */
    private static final Path FILE = Path.of("archive", "document.pdf");

    /** An error status the converter answered with. */
    private static final int NOT_FOUND = 404;

    @Test
    @Story("A conversion placed ahead of its turn fails as it would have in its turn")
    @DisplayName("A conversion placed ahead that the converter rejected, or lost the connection under, reaches the step as that same failure, and nothing is stored for it")
    void aFailedDispatchedCallIsRethrownAsItself() {
        DoclingCallRejectedException rejected = new DoclingCallRejectedException(FILE, NOT_FOUND, "not found");
        DoclingConnectionLostException lost =
                new DoclingConnectionLostException(FILE, new ResourceAccessException("connection reset"));
        List<DoclingResponse> stored = new ArrayList<>();

        PendingConversions afterARejection = new PendingConversions();
        afterARejection.dispatch(OCCURRENCE, CompletableFuture.failedFuture(rejected), stored::add);
        PendingConversions afterALostConnection = new PendingConversions();
        afterALostConnection.dispatch(OCCURRENCE, CompletableFuture.failedFuture(lost), stored::add);

        claim(
                "a rejection is thrown to the step as the very rejection the worker met, not wrapped in"
                        + " anything the step would have to look inside",
                () -> assertThatThrownBy(() -> afterARejection.take(OCCURRENCE)).isSameAs(rejected));
        claim(
                "a lost connection is thrown to the step as the very failure the worker met",
                () -> assertThatThrownBy(() -> afterALostConnection.take(OCCURRENCE)).isSameAs(lost));
        claim(
                "neither failure was handed on to be stored, so the converter is asked again next time",
                () -> assertThat(stored).isEmpty());
    }

    @Test
    @Issue("369")
    @Link(name = "ADR-176", url = Adr.STAGE_2_READS_AHEAD_ACROSS_CHUNKS, type = "adr")
    @Story("A conversion placed ahead that the step ends without reaching")
    @DisplayName("A conversion placed ahead and given up before it answered is cancelled, nothing waits for it afterwards, and nothing is stored for it")
    void anAbandonedDispatchedCallIsCancelledAndForgotten() {
        CompletableFuture<DoclingResponse> stillConverting = new CompletableFuture<>();
        List<DoclingResponse> stored = new ArrayList<>();
        PendingConversions pending = new PendingConversions();
        pending.dispatch(OCCURRENCE, stillConverting, stored::add);

        pending.abandon(OCCURRENCE);

        claim(
                "the conversion was cancelled, so whatever was working on it is told to stop",
                () -> assertThat(stillConverting).isCancelled());
        claim(
                "asking for that document's conversion afterwards finds nothing placed ahead, and returns at"
                        + " once without waiting for an answer that will never come",
                () -> assertThat(pending.take(OCCURRENCE)).isEmpty());
        claim(
                "and nothing was handed on to be stored for it",
                () -> assertThat(stored).isEmpty());
    }
}
