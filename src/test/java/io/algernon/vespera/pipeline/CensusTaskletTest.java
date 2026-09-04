package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.CheckpointMismatch;
import io.algernon.vespera.corpus.CorpusFailures;
import io.algernon.vespera.corpus.ExcludesNothingViolation;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
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

    /** Files written under the seed folder. */
    private static final int SEED_FILES = 1;

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
                () -> assertThat(ledger.occurrenceCount(onlyWalk())).isEqualTo(CORPUS_FILES));
        claim(
                "the profile now exists, carrying the seed-folder key nobody has answered",
                () -> assertThat(profileStore.load().seedFolder().isSet()).isFalse());
        claim(
                "and census said so in the measurement, rather than leaving the key looking unmeasured",
                () -> assertThat(profileStore.load().seedFolder().measurement())
                        .isEqualTo(new Measurement("no seed folder is set in the profile", CENSUS_RAN_AT)));
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("A seed folder named in the profile is walked by the same instrument, under its own id")
    void walksTheSeedFolderWhenTheProfileNamesOne(@TempDir Path root, @TempDir Path seeds, @TempDir Path workingDirectory)
            throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        Files.writeString(seeds.resolve("exemplar.txt"), "a known-relevant document");
        Ledger ledger = new Ledger(jdbcTemplate);
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(new Profile(new ProfileValue(seeds.toString(), "the handover set", null), null));

        tasklet(root, profileStore).execute(null, null);

        claim(
                "two walks were recorded, the corpus and the seed folder, neither one part of the other",
                () -> assertThat(walkCount()).isEqualTo(2));
        claim(
                "the seed folder's " + SEED_FILES + " file was recorded as an occurrence of its own walk",
                () -> assertThat(occurrenceCountForRoot(ledger, seeds)).isEqualTo(SEED_FILES));
        claim(
                "the profile's measurement now points at the walk census made of the seed folder",
                () -> assertThat(profileStore.load().seedFolder().measurement().source())
                        .isEqualTo("walk " + walkIdForRoot(seeds)));
        claim(
                "the operator's value survived the run, as an answer census never rewrites",
                () -> assertThat(profileStore.load().seedFolder().value()).isEqualTo(seeds.toString()));
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
        profileStore.save(new Profile(new ProfileValue(seeds.toString(), "the handover set", null), null));
        CensusTasklet census = tasklet(root, profileStore, failingOn(seeds, CorpusFailures.excludesNothing()));

        claim(
                "the invocation fails, rather than exiting nought with the loss recorded as a measurement",
                () -> assertThatThrownBy(() -> census.execute(null, null))
                        .isInstanceOf(ExcludesNothingViolation.class));
        claim(
                "the corpus walk still ran to completion first, neither walk costing the other its chance",
                () -> assertThat(walkCount()).isEqualTo(1));
        claim(
                "and the profile says what went wrong, so the failure is legible after the exit code",
                () -> assertThat(profileStore.load().seedFolder().measurement().source())
                        .contains("the seed folder could not be walked"));
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("A seed folder that is merely missing is recorded, not fatal")
    void aSeedFolderThatIsNotThereIsRecordedRatherThanFatal(
            @TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Path missing = root.resolve("nowhere");
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(new Profile(new ProfileValue(missing.toString(), "the handover set", null), null));
        CensusTasklet census = tasklet(root, profileStore);

        claim(
                "a mistyped seed path does not cost the operator the corpus census that ran beside it",
                () -> assertThatCode(() -> census.execute(null, null)).doesNotThrowAnyException());
        claim(
                "the corpus was recorded",
                () -> assertThat(new Ledger(jdbcTemplate).occurrenceCount(onlyWalk())).isEqualTo(1));
        claim(
                "and the profile carries why no seed walk happened",
                () -> assertThat(profileStore.load().seedFolder().measurement().source())
                        .contains("the seed folder could not be walked"));
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("A seed walk resumed onto a corpus that moved under it aborts the invocation")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void aSeedWalkResumedOntoAMovedTreeAbortsTheInvocation(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(seeds.resolve("exemplar.txt"), "a known-relevant document");
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(new Profile(new ProfileValue(seeds.toString(), "the handover set", null), null));
        CensusTasklet census = tasklet(root, profileStore, failingOn(seeds, CorpusFailures.checkpointMismatch()));

        claim(
                "a resumed seed walk that would skip past a subtree it can no longer trust fails the"
                        + " invocation, the same as a walk that lost rows — both mean the ledger may hold"
                        + " less than the archive",
                () -> assertThatThrownBy(() -> census.execute(null, null))
                        .isInstanceOf(CheckpointMismatch.class));
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

    private long walkIdForRoot(Path root) throws IOException {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM walk WHERE root = ?", Long.class, root.toRealPath().toString());
    }

    private long occurrenceCountForRoot(Ledger ledger, Path root) throws IOException {
        return ledger.occurrenceCount(new WalkId(walkIdForRoot(root)));
    }
}
