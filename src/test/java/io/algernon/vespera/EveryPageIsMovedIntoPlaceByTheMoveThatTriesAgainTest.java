package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every page this tool writes beside its place and moves over it, {@code arrangement.html}, the review list,
 * {@code index.html}, {@code documents.csv} and each copy of a survivor (ADR-223 sections 3 and 6, ADR-274 §2.2),
 * is moved by {@code ledger.ReplacingMove}, which tries the move again a few times where Windows refuses it for a
 * moment (ADR-242), and by no move of a class's own.
 *
 * <p>Read off the compiled classes, as {@link EachTableIsNamedOnlyByItsOwnerTest} reads them: a class that moves a
 * file in one atomic move names {@code ATOMIC_MOVE}, and only the class that tries again may. A move written
 * another way, or a copy and a delete, is not seen.
 */
@Epic("Architecture")
@Feature("Writing pages")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-242", url = Adr.A_MOVE_REFUSED_AS_ACCESS_DENIED_IS_ATTEMPTED_AGAIN, type = "adr")
class EveryPageIsMovedIntoPlaceByTheMoveThatTriesAgainTest {

    /** The one shipped class that may move a file in one atomic move. */
    private static final String THE_MOVE_THAT_TRIES_AGAIN = "io.algernon.vespera.ledger.ReplacingMove";

    /** What a class that moves a file in one atomic move names. */
    private static final String AN_ATOMIC_MOVE = "ATOMIC_MOVE";

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("Every page written beside its place is moved over it by the one move that tries again when the system refuses it for a moment")
    void everyPageIsMovedByTheMoveThatTriesAgain() throws Exception {
        List<String> movingOnTheirOwn = ShippedClasses.namesByClass().entrySet().stream()
                .filter(shipped -> shipped.getValue().contains(AN_ATOMIC_MOVE))
                .map(Map.Entry::getKey)
                .filter(name -> !name.equals(THE_MOVE_THAT_TRIES_AGAIN))
                .toList();

        claim(
                "no class but the one that tries again moves a page over its place itself, so a page refused for a"
                        + " moment by a program holding it open is tried again wherever it is written",
                () -> assertThat(movingOnTheirOwn).isEmpty());
    }
}
