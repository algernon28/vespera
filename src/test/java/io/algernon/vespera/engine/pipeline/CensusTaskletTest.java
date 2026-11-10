package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.CorpusFailures;
import io.algernon.vespera.engine.corpus.ExcludesNothingViolationException;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.profile.Measurement;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * Stage 0 as one act: walk the corpus, walk the seed folder if the operator named one, and leave the
 * profile pointing at what was walked (ADR-064).
 *
 * <p>Assembled by hand rather than through the job, because what is worth pinning here is what
 * census does, not that Spring Batch can call a tasklet.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Census")
@Issue("16")
@Link(name = "ADR-064", url = Adr.THE_WALK_INSTRUMENT_GENERALIZES, type = "adr")
class CensusTaskletTest {

    private static final Instant CENSUS_RAN_AT = Instant.parse("2026-08-31T09:00:00Z");

    /** Files written under the corpus root, so a claim can say where its number comes from. */
    private static final int CORPUS_FILES = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("What census does in one invocation")
    @DisplayName("Census records the corpus and points the profile at what it found")
    void recordsTheCorpusAndMergesTheProfile(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        Ledger ledger = new Ledger(jdbcTemplate);
        ProfileStore profileStore = new ProfileStore(workingDirectory);

        tasklet(root, profileStore).execute(null, null);

        claim(
                "the " + CORPUS_FILES + " files under the corpus root were recorded as occurrences",
                () -> assertThat(ledger.occurrences().occurrenceCount(onlyWalk())).isEqualTo(CORPUS_FILES));
        claim(
                "the profile now exists as a file, carrying the seed-folder key nobody has answered",
                () -> {
                    assertThat(profileStore.file()).isRegularFile();
                    assertThat(profileStore.load().seedFolder().isSet()).isFalse();
                });
        claim(
                "and census said so in the measurement, rather than leaving the key looking unmeasured",
                () -> assertThat(profileStore.load().seedFolder().measurement())
                        .isEqualTo(new Measurement("no seed folder is set in the profile", CENSUS_RAN_AT)));
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("A seed walk that cannot account for every entry aborts the invocation")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void aSeedWalkThatLostRowsAbortsTheInvocation(@TempDir Path root, @TempDir Path seeds, @TempDir Path workingDirectory)
            throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(seeds.resolve("exemplar.txt"), "a known-relevant document");
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile().seedFolder(seeds.toString(), "the handover set").build());
        CensusTasklet census = tasklet(root, profileStore, failingOn(seeds, CorpusFailures.excludesNothing()));

        claim(
                "the invocation fails, rather than exiting nought with the loss recorded as a measurement",
                () -> assertThatThrownBy(() -> census.execute(null, null))
                        .isInstanceOf(ExcludesNothingViolationException.class));
        claim(
                "the corpus walk still ran to completion first, neither walk costing the other its chance",
                () -> assertThat(walkCount()).isEqualTo(1));
        claim(
                "and the profile says what went wrong, so the failure is legible after the exit code",
                () -> assertThat(profileStore.load().seedFolder().measurement().source())
                        .contains("the seed folder could not be walked"));
    }

    /** A recorder that walks everything for real, except {@code seeds}, where it raises {@code failure}. */
    private WalkRecorder failingOn(Path seeds, RuntimeException failure) {
        return new WalkRecorder(new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)) {
            @Override
            public WalkId walk(Path walkRoot) throws IOException {
                if (walkRoot.equals(seeds)) {
                    throw failure;
                }
                return super.walk(walkRoot);
            }
        };
    }

    private CensusTasklet tasklet(Path root, ProfileStore profileStore) {
        WalkRecorder walkRecorder = new WalkRecorder(
                new Ledger(jdbcTemplate),
                new AnomalyLog(jdbcTemplate),
                new JdbcTransactionManager(dataSource));
        return tasklet(root, profileStore, walkRecorder);
    }

    private CensusTasklet tasklet(Path root, ProfileStore profileStore, WalkRecorder walkRecorder) {
        return new CensusTasklet(
                walkRecorder, profileStore, Clock.fixed(CENSUS_RAN_AT, ZoneOffset.UTC), root);
    }

    private WalkId onlyWalk() {
        return new WalkId(
                jdbcTemplate.queryForObject("SELECT id FROM walk", Long.class));
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }
}
