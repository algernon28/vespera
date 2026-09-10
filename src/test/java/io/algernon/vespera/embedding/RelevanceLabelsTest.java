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

    /** A different folder, and therefore a different question about the same document. */
    private static final String ANOTHER_SEED_SET = "C:/archive/other-seeds";

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

    private static final String AN_EMBEDDER = "model=all-minilm;digest=abc;dtype=F16;dimension=384;instruction=none";

    private static final String A_LATER_EMBEDDER =
            "model=qwen3-embedding;digest=def;dtype=F16;dimension=1024;instruction=none";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("An answer is about a document and a seed set, never about a run")
    @DisplayName("A recorded answer is read back by the document and the seed set it was given about")
    void recordsAnAnswerAgainstADocumentAndASeedSet() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();

        labels.record(DOCUMENT, SEED_SET, true, run, SCORE_ON_SCREEN, AN_EMBEDDER);

        claim(
                "the answer is found by asking about the document and the seed set it was given about,"
                        + " which is the question it answers -- no run is named in the asking, and no walk"
                        + " either",
                () -> assertThat(labels.answerFor(DOCUMENT, SEED_SET)).contains(true));
        claim(
                "the same document against a different seed set is a different question, and nobody has"
                        + " answered that one",
                () -> assertThat(labels.answerFor(DOCUMENT, ANOTHER_SEED_SET)).isEmpty());
    }

    @Test
    @Story("An answer survives everything that produced it")
    @DisplayName("Re-ingesting the same answer leaves one row, not two")
    void reIngestingTheSameAnswerDoesNotDuplicateIt() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId firstRun = aRun();

        labels.record(DOCUMENT, SEED_SET, true, firstRun, SCORE_ON_SCREEN, AN_EMBEDDER);
        labels.record(DOCUMENT, SEED_SET, true, firstRun, SCORE_ON_SCREEN, AN_EMBEDDER);

        claim(
                "one answer was given about that document, so " + ONE_ROW + " row stands: every other"
                        + " table in this system answers a repeat run with a fresh row set, because a"
                        + " second computation is a second observation -- a second copy of a person's"
                        + " answer is only a duplicate, and this table must never be tidied into"
                        + " consistency with the others",
                () -> assertThat(rowsAbout(labels, DOCUMENT)).hasSize(ONE_ROW));
    }

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
                        + " numbers, not a new opinion, so re-scoring costs the operator no second sitting",
                () -> assertThat(rowsAbout(labels, DOCUMENT)).hasSize(ONE_ROW));
        claim(
                "and the answer itself is unchanged, because nothing about a different model makes a"
                        + " person's judgement about a document different",
                () -> assertThat(labels.answerFor(DOCUMENT, SEED_SET)).contains(true));
    }

    @Test
    @Story("The context an answer was given in is kept beside it")
    @DisplayName("The run, the score shown and the embedder are recorded beside the answer")
    void keepsTheContextTheAnswerWasGivenIn() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();

        labels.record(DOCUMENT, SEED_SET, false, run, SCORE_ON_SCREEN, AN_EMBEDDER);

        RelevanceLabel recorded = rowsAbout(labels, DOCUMENT).getFirst();
        claim(
                "the score the person had in front of them is kept, so a later reader can tell what the"
                        + " judgement was made against rather than guessing",
                () -> assertThat(recorded.scoreShown()).isEqualTo(SCORE_ON_SCREEN));
        claim(
                "so is the embedder that produced that score, which is what says whether two answers"
                        + " were given against numbers on the same scale",
                () -> assertThat(recorded.embedderIdentity()).isEqualTo(AN_EMBEDDER));
        claim(
                "and the run that put the question, recorded beside the answer and not inside its"
                        + " identity -- an answer belongs to the document, not to the pass that showed it",
                () -> assertThat(recorded.runId()).isEqualTo(run.value()));
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
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        return ledger.startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
