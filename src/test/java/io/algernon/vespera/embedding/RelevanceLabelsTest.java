package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A person's recorded answer about one document (ADR-088): relevant to this seed set, or not.
 *
 * <p>This is the first table in the system whose rows a re-run never rewrites, and the claims below
 * are written to make that hard to undo by accident. Everywhere else, running a stage again produces
 * a fresh row set under a new run id, because a second computation is a second observation. A second
 * copy of a person's answer is not a second observation — it is a duplicate, and the two hours that
 * produced the first one cannot be produced again by a machine.
 *
 * <p><b>Keyed by the path and the seed set</b> (ADR-097), because an occurrence id is per-walk and
 * census re-walks every invocation. A label keyed by the occurrence would join to nothing the next
 * run scores, so the answers would sit here and stop being findable -- worse than losing them,
 * because nothing reports their absence. Nothing below builds a walk to record an answer.
 *
 * <p>What a label is keyed by follows from what it means. "Is this document relevant to this seed
 * set" stays true however the document was scored, so the run, the score on screen and the embedder
 * are recorded beside the answer as the context it was given in, and never as part of its identity.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Labelling")
@Issue("111")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
@Link(name = "ADR-097", url = Adr.A_LABEL_IS_KEYED_BY_PATH_AND_SEED_SET, type = "adr")
class RelevanceLabelsTest {

    /** The folder the exemplars live in, which is what an operator means by "the seed set". */
    private static final String SEED_SET = "C:/archive/seeds";

    /**
     * The document judged, named the way ADR-051 names one and the way the label file already does.
     * No walk is built to arrive at it: that is what re-keying by the path bought (ADR-097).
     */
    private static final OccurrencePath DOCUMENT = new OccurrencePath("report.pdf");

    /** A document a person would say no about, so the no can be shown to be an answer. */
    private static final OccurrencePath AN_UNRELATED_DOCUMENT = new OccurrencePath("holiday-snap.jpg");

    /** The score the person was shown when they answered, kept beside the answer as context. */
    private static final double SCORE_ON_SCREEN = 0.83;

    /** The score the same document got when a different model scored it later. */
    private static final double SCORE_UNDER_THE_NEW_MODEL = 0.41;

    /** One answer given about one document leaves one row, however many times it is offered. */
    private static final int ONE_ROW = 1;

    private static final String AN_EMBEDDER = "model=a-small-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";

    private static final String A_LATER_EMBEDDER =
            "model=a-comparing-model;digest=def;dtype=F16;dimension=1024;instruction=none";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("An answer survives everything that produced it")
    @DisplayName("An answer given under one model is still there after a re-score under another")
    void anAnswerSurvivesAReScoreUnderANewModel() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId firstRun = aRun();
        RunId laterRun = aRun();

        labels.record(DOCUMENT, SEED_SET, true, firstRun, SCORE_ON_SCREEN, AN_EMBEDDER);
        labels.record(DOCUMENT, SEED_SET, true, laterRun, SCORE_UNDER_THE_NEW_MODEL, A_LATER_EMBEDDER);

        claim(
                "the answer is still there and still " + ONE_ROW + " row: a new model is a new set of"
                        + " numbers, not a new opinion, so re-scoring costs the operator no second sitting."
                        + " Every other table in this system answers a repeat run with a fresh row set,"
                        + " because a second computation is a second observation -- a second copy of a"
                        + " person's answer is only a duplicate, and this table must never be tidied into"
                        + " consistency with the others",
                () -> assertThat(rowsAbout(labels, DOCUMENT)).hasSize(ONE_ROW));
        claim(
                "and the answer itself is unchanged, because nothing about a different model makes a"
                        + " person's judgement about a document different",
                () -> assertThat(labels.answerFor(DOCUMENT, SEED_SET)).contains(true));
        claim(
                "while what it is seen against is the re-score's: the row now carries the later run, the"
                        + " score under the new model and the later embedder, so the answer is read against"
                        + " the numbers it now sits beside",
                () -> assertThat(rowsAbout(labels, DOCUMENT).getFirst())
                        .extracting(RelevanceLabel::runId, RelevanceLabel::scoreShown, RelevanceLabel::embedderIdentity)
                        .containsExactly(laterRun.value(), SCORE_UNDER_THE_NEW_MODEL, A_LATER_EMBEDDER));
    }

    @Test
    @Story("An answer is about a document and a seed set, never about a run")
    @DisplayName("An answer of not-relevant is recorded as an answer, not as the absence of one")
    void recordsNotRelevantAsAnAnswer() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);

        labels.record(AN_UNRELATED_DOCUMENT, SEED_SET, false, aRun(), SCORE_ON_SCREEN, AN_EMBEDDER);

        claim(
                "a person who says no has answered, and the row says so: a hard negative is exactly this"
                        + " -- a no carrying a high score -- and it is a query over these rows rather than"
                        + " a thing anything here has to build",
                () -> assertThat(labels.answerFor(AN_UNRELATED_DOCUMENT, SEED_SET)).contains(false));
    }

    /**
     * The rows standing about one document, so a count says what it is counting.
     *
     * <p>Scoped to the document rather than taken as the size of the whole pass: this database
     * outlives each test method, and every method here answers about the same seed set.
     */
    private List<RelevanceLabel> rowsAbout(RelevanceLabels labels, OccurrencePath document) {
        return labels.forSeedSet(SEED_SET).stream()
                .filter(label -> label.path().equals(document))
                .toList();
    }

    /**
     * A run for the answer to be recorded beside, which needs a walk only because a run is recorded
     * against one. Nothing about the label wants either.
     */
    private RunId aRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        return ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
