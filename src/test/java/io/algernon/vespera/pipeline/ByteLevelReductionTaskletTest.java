package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1's wiring and ordering: that it mints its own run over census's survivors, verdicts what
 * {@link io.algernon.vespera.corpus.BrokenCheck} catches, then resolves duplicates among what
 * remains. {@code BrokenCheckTest} and {@code DuplicateResolutionTest} already pin every per-format
 * and per-comparison branch, so what is worth proving here is that the step reaches the ledger
 * correctly and in order, not either capability's own logic again.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("36")
@Issue("37")
@Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
@Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
class ByteLevelReductionTaskletTest {

    /** Written to two occurrences, so they share both size and content. */
    private static final String DUPLICATE_CONTENT = "duplicate-content-x";

    /** Same length as {@link #DUPLICATE_CONTENT}, differing only in its last character. */
    private static final String DIFFERENT_CONTENT_SAME_SIZE = "duplicate-content-y";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A broken file is verdicted broken; a valid file survives, unverdicted")
    void verdictsOnlyTheBrokenSurvivor(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.pdf"), "not a pdf at all");
        Files.writeString(root.resolve("fine.txt"), "plain text content");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(ledger, contentIdentity(), new ImplementationVersions(), root).execute(null, null);

        claim(
                "the broken pdf is verdicted broken",
                () -> assertThat(verdictKindsFor(ledger, walkId, "broken.pdf")).containsExactly("BROKEN"));
        claim(
                "the valid text file carries no verdict at all -- survival is the absence of one, never a"
                        + " PASSED row",
                () -> assertThat(verdictKindsFor(ledger, walkId, "fine.txt")).isEmpty());
    }

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("Stage 1 mints its own run, named for its stage, over the walk census recorded")
    void mintsARunOverTheWalkCensusRecorded(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(ledger, contentIdentity(), new ImplementationVersions(), root).execute(null, null);

        claim(
                "one run was minted, for the walk census produced",
                () -> assertThat(runWalkId()).isEqualTo(walkId.value()));
        claim(
                "the run is named for the stage it belongs to",
                () -> assertThat(runStage()).isEqualTo("byte-level-reduction"));
    }

    @Test
    @Story("Content-identity duplicate resolution")
    @DisplayName(
            "Byte-identical survivors resolve to one representative and one superseded; a same-size,"
                    + " different-content file is untouched")
    void resolvesByteIdenticalFilesToOneRepresentative(@TempDir Path root) throws Exception {
        // Same length as DUPLICATE_CONTENT, differing only in the last character -- same size, so it
        // enters the same size-group, but must not be swept up by content identity alone.
        Files.writeString(root.resolve("copy-a.txt"), DUPLICATE_CONTENT);
        Files.writeString(root.resolve("copy-b.txt"), DUPLICATE_CONTENT);
        Files.writeString(root.resolve("same-size-different.txt"), DIFFERENT_CONTENT_SAME_SIZE);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(ledger, contentIdentity(), new ImplementationVersions(), root).execute(null, null);

        List<String> aVerdicts = verdictKindsFor(ledger, walkId, "copy-a.txt");
        List<String> bVerdicts = verdictKindsFor(ledger, walkId, "copy-b.txt");
        claim(
                "exactly one of the two byte-identical copies is superseded -- the other is the"
                        + " representative, carrying no verdict",
                () -> assertThat(aVerdicts.isEmpty()).isNotEqualTo(bVerdicts.isEmpty()));
        claim(
                "the superseded one is verdicted superseded-by, naming the survivor's path",
                () -> {
                    List<String> supersededVerdicts = aVerdicts.isEmpty() ? bVerdicts : aVerdicts;
                    String survivorPath = aVerdicts.isEmpty() ? "copy-a.txt" : "copy-b.txt";
                    assertThat(supersededVerdicts).containsExactly("SUPERSEDED_BY");
                    assertThat(verdictReasonsFor(ledger, walkId, aVerdicts.isEmpty() ? "copy-b.txt" : "copy-a.txt"))
                            .anySatisfy(reason -> assertThat(reason).contains(survivorPath));
                });
        claim(
                "the same-size but different-content file carries no verdict: sharing a size is not"
                        + " sharing content",
                () -> assertThat(verdictKindsFor(ledger, walkId, "same-size-different.txt")).isEmpty());
    }

    private ContentIdentity contentIdentity() {
        return new ContentIdentity(jdbcTemplate);
    }

    private WalkRecorder walkRecorder(Ledger ledger) {
        return new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    private List<String> verdictReasonsFor(Ledger ledger, WalkId walkId, String fileName) {
        long occurrenceId =
                ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow().value();
        return jdbcTemplate.query(
                "SELECT reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("reason"),
                occurrenceId);
    }

    private List<String> verdictKindsFor(Ledger ledger, WalkId walkId, String fileName) {
        long occurrenceId =
                ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow().value();
        return jdbcTemplate.query(
                "SELECT kind FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind"),
                occurrenceId);
    }

    private long runWalkId() {
        return jdbcTemplate.queryForObject("SELECT walk_id FROM run", Long.class);
    }

    private String runStage() {
        return jdbcTemplate.queryForObject("SELECT stage FROM run", String.class);
    }
}
