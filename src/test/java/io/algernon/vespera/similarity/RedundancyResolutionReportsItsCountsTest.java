package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The contract of {@code ResolutionProgress} (ADR-192 section 5, #412), through which {@code
 * RedundancyResolution.resolve} tells its caller about its five counted loops and its one running count: the
 * signed occurrences whose candidate pairs have been scored, the occurrence profiles read, the components
 * resolved, the near-duplicate verdicts written (summed across the components, announced once), the signed
 * occurrences checked for containment, and each containment candidate gone through, which has no total.
 *
 * <p>Since ADR-214 the first loop counts signed occurrences and not pairs: the pairs are found a page of signed
 * occurrences at a time, so how many there are is known only once the last page is read, and an item is a
 * signed occurrence whose pairs, as the lesser member, have all been scored.
 *
 * <p>Whole-job tests see only the lines {@code pipeline} writes, and a loop of zero items writes none, so
 * "once, before the first item, zero included" and "summed and announced once" can be seen only here.
 *
 * <p><b>Part (b) of ADR-192.</b> Does not compile until {@code ResolutionProgress} and the five-argument {@code
 * resolve} exist; part (b) moves it into {@code src/test}. The documents are explicit shingle sets, as in
 * {@code RedundancyResolutionTest}, and every total is read off the database the resolution reads.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class RedundancyResolutionReportsItsCountsTest {

    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    private static final String SCORE = "to-score ";
    private static final String PROFILES = "to-read-profiles ";
    private static final String COMPONENTS = "to-resolve ";
    private static final String VERDICTS = "to-write-verdicts ";
    private static final String CONTAINMENT = "to-check ";

    /** A signed document whose candidate pairs have all been scored (ADR-214). */
    private static final String CANDIDATES = "candidates";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("Each loop is announced once with its total, in the order resolution goes through them, and each item is reported")
    void announcesEachLoopOnceAndReportsEachItem() {
        Fixture fixture = fixture();
        fixture.document("a.pdf", shingles(0, 100));
        fixture.document("b.pdf", shingles(1, 100));
        fixture.document("c.pdf", shingles(10_000, 100));
        Recording progress = new Recording();

        fixture.resolve(progress, true);

        long pairs = fixture.pairsSharingABand();
        claim("the signatures put at least one pair forward", () -> assertThat(pairs).isPositive());
        claim(
                "each counted loop is announced exactly once, with its total: the three signed documents whose"
                        + " candidates are scored, the two members of the one component, the one component, its one"
                        + " member that is not the survivor, and the three signed documents again, checked for a"
                        + " container",
                () -> assertThat(progress.announcements())
                        .containsExactly(SCORE + 3, PROFILES + 2, COMPONENTS + 1, VERDICTS + 1, CONTAINMENT + 3));
        claim(
                "and each item is reported as many times as its loop's total",
                () -> {
                    assertThat(progress.count(CANDIDATES)).isEqualTo(3);
                    assertThat(progress.count("profile")).isEqualTo(2);
                    assertThat(progress.count("component")).isEqualTo(1);
                    assertThat(progress.count("verdict")).isEqualTo(1);
                    assertThat(progress.count("checked")).isEqualTo(3);
                });
        claim(
                "every item comes after its loop's announcement and before the next loop's, and the verdict comes"
                        + " before the component it belongs to is reported resolved",
                () -> assertThat(progress.events)
                        .containsSubsequence(SCORE + 3, CANDIDATES, PROFILES + 2, "profile", COMPONENTS + 1,
                                VERDICTS + 1, "verdict", "component", CONTAINMENT + 3, "checked"));
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("The near-duplicate verdicts of two components are announced once, as their sum")
    void theVerdictCounterIsSummedAcrossComponents() {
        Fixture fixture = fixture();
        fixture.document("a.pdf", shingles(0, 100));
        fixture.document("b.pdf", shingles(1, 100));
        fixture.document("d.pdf", shingles(20_000, 100));
        fixture.document("e.pdf", shingles(20_001, 100));
        Recording progress = new Recording();

        fixture.resolve(progress, true);

        claim(
                "two components of two members each: four profiles, two components, and the verdict loop is"
                        + " announced once with two, the sum, and not once per component",
                () -> {
                    assertThat(progress.announcements())
                            .contains(PROFILES + 4, COMPONENTS + 2, VERDICTS + 2)
                            .filteredOn(announcement -> announcement.startsWith(VERDICTS))
                            .hasSize(1);
                    assertThat(progress.count("verdict")).isEqualTo(2);
                    assertThat(progress.count("component")).isEqualTo(2);
                });
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("Containment candidates are reported inside the containment loop, with no total")
    void containmentCandidatesAreReportedInsideTheContainmentLoop() {
        Fixture fixture = fixture();
        fixture.document("a.pdf", shingles(0, 100));
        fixture.document("b.pdf", shingles(1, 100));
        fixture.document("c.pdf", shingles(10_000, 100));
        Recording progress = new Recording();

        fixture.resolve(progress, true);

        int toCheck = progress.events.indexOf(CONTAINMENT + 3);
        claim(
                "the survivor of the near-identical pair has rare shingles its partner shares, so at least one"
                        + " candidate is reported",
                () -> assertThat(progress.count("candidate")).isPositive());
        claim(
                "every candidate is reported after the containment loop is announced, and none has a total of its"
                        + " own announced",
                () -> {
                    assertThat(progress.events.subList(0, toCheck)).doesNotContain("candidate");
                    assertThat(progress.announcements()).noneMatch(announcement -> announcement.contains("candidate"));
                });
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("With no pair, every loop before containment is announced with zero and reports nothing")
    void aLoopOfZeroItemsIsAnnouncedWithZero() {
        Fixture fixture = fixture();
        fixture.document("a.pdf", shingles(0, 100));
        fixture.document("c.pdf", shingles(10_000, 100));
        Recording progress = new Recording();

        fixture.resolve(progress, true);

        claim(
                "no two documents share a band, so the profile, component and verdict loops are each announced with"
                        + " zero, once, and report nothing; the candidates loop is announced with the two signed"
                        + " documents and reports both, each found to have no candidate, and the containment loop is"
                        + " announced with two and reports both",
                () -> {
                    assertThat(progress.announcements())
                            .containsExactly(SCORE + 2, PROFILES + 0, COMPONENTS + 0, VERDICTS + 0, CONTAINMENT + 2);
                    assertThat(progress.count(CANDIDATES)).isEqualTo(2);
                    assertThat(progress.count("checked")).isEqualTo(2);
                    assertThat(progress.count("pair") + progress.count("profile") + progress.count("component")
                                    + progress.count("verdict"))
                            .isZero();
                });
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("A document with no rare shingle to look for is reported when it is skipped, and finds no candidate")
    void aDocumentWithNoRareShingleIsStillReported() {
        Fixture fixture = fixture();
        fixture.document("a.pdf", shingles(0, 100));
        fixture.document("c.pdf", shingles(10_000, 100));
        Recording progress = new Recording();

        fixture.resolve(progress, false);

        claim(
                "stage 3's document frequency was not measured, so no document has a rare shingle to look for: both"
                        + " are reported checked all the same, and no candidate is reported",
                () -> {
                    assertThat(progress.count("checked")).isEqualTo(2);
                    assertThat(progress.count("candidate")).isZero();
                });
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("With no signed document resolution returns before any loop, and its caller is told nothing")
    void withNothingSignedNothingIsAnnounced() {
        Fixture fixture = fixture();
        Recording progress = new Recording();

        fixture.resolve(progress, true);

        claim(
                "no signature exists, so resolution returns before any total is known: no loop is announced, not"
                        + " even with zero",
                () -> assertThat(progress.events).isEmpty());
    }

    @Test
    @Story("Resolution tells its caller how many items each loop will go through")
    @DisplayName("Reporting does not change what is resolved: a near-identical pair still leaves one pointer")
    void reportingChangesNothingAboutWhatIsResolved() {
        Fixture fixture = fixture();
        OccurrenceId first = fixture.document("a.pdf", shingles(0, 100));
        OccurrenceId second = fixture.document("b.pdf", shingles(1, 100));

        fixture.resolve(new Recording(), true);

        claim(
                "exactly one of the two near-identical documents points at the other",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM redundant_with WHERE occurrence_id IN (?, ?)",
                                Long.class,
                                first.value(),
                                second.value()))
                        .isEqualTo(1L));
    }

    private static Set<Long> shingles(long from, int count) {
        Set<Long> hashes = new LinkedHashSet<>();
        for (long i = 0; i < count; i++) {
            hashes.add(from + i);
        }
        return hashes;
    }

    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        String suffix = Long.toString(System.nanoTime());
        RunId stage2RunId = ledger.runs().startRun("extraction", "x" + suffix, "{}", walkId, List.of());
        RunId stage3RunId = ledger.runs().startRun("content-census", "y" + suffix, "{}", walkId, List.of(stage2RunId));
        RunId stage4RunId = ledger.runs().startRun("content-redundancy", "z" + suffix, "{}", walkId, List.of(stage3RunId));
        return new Fixture(ledger, walkId, stage2RunId, stage3RunId, stage4RunId);
    }

    /** What the callback was told, in order, as text. */
    private static final class Recording implements ResolutionProgress {

        final List<String> events = new ArrayList<>();

        List<String> announcements() {
            return events.stream().filter(event -> event.startsWith("to-")).toList();
        }

        long count(String item) {
            return events.stream().filter(item::equals).count();
        }

        @Override
        public void toScorePairs(long pairs) {
            events.add(SCORE + pairs);
        }

        /** Written without {@code @Override}: it is gone once ADR-214 is built. */
        public void pairScored() {
            events.add("pair");
        }

        /** ADR-214's callback, written without {@code @Override} so the class compiles before it exists. */
        public void candidatesScored() {
            events.add(CANDIDATES);
        }

        @Override
        public void toReadProfiles(long occurrences) {
            events.add(PROFILES + occurrences);
        }

        @Override
        public void profileRead() {
            events.add("profile");
        }

        @Override
        public void toResolveComponents(long components) {
            events.add(COMPONENTS + components);
        }

        @Override
        public void componentResolved() {
            events.add("component");
        }

        @Override
        public void toWriteNearDuplicateVerdicts(long members) {
            events.add(VERDICTS + members);
        }

        @Override
        public void nearDuplicateVerdictWritten() {
            events.add("verdict");
        }

        @Override
        public void toCheckForContainment(long occurrences) {
            events.add(CONTAINMENT + occurrences);
        }

        @Override
        public void checkedForContainment() {
            events.add("checked");
        }

        @Override
        public void containmentCandidateGoneThrough() {
            events.add("candidate");
        }
    }

    private final class Fixture {
        private final Ledger ledger;
        private final WalkId walkId;
        private final RunId stage2RunId;
        private final RunId stage3RunId;
        private final RunId stage4RunId;

        Fixture(Ledger ledger, WalkId walkId, RunId stage2RunId, RunId stage3RunId, RunId stage4RunId) {
            this.ledger = ledger;
            this.walkId = walkId;
            this.stage2RunId = stage2RunId;
            this.stage3RunId = stage3RunId;
            this.stage4RunId = stage4RunId;
        }

        OccurrenceId document(String path, Set<Long> shingleHashes) {
            ledger.occurrences().fileOccurrence(
                    walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"),
                    Instant.parse("2021-01-01T00:00:00Z"));
            OccurrenceId occurrenceId = ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
            for (long hash : shingleHashes) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        stage2RunId.value(),
                        ShingleParameters.DEFAULT.identity(),
                        hash);
            }
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(), stage2RunId.value(), "success", 0.5, 10_000L, 10_000L, 1, 1, 0, 0);
            return occurrenceId;
        }

        /** Signs every document, optionally measures stage 3's document frequency, and resolves. */
        void resolve(ResolutionProgress progress, boolean measureDocumentFrequency) {
            if (measureDocumentFrequency) {
                new DocumentFrequency(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
            }
            RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
            for (Long occurrenceId : jdbcTemplate.queryForList(
                    "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value())) {
                signatures.write(new OccurrenceId(occurrenceId), stage4RunId, stage2RunId, Set.of(), NO_BOILERPLATE_FLOOR);
            }
            new RedundancyResolution(jdbcTemplate, ledger)
                    .resolve(
                            stage4RunId,
                            stage3RunId,
                            stage2RunId,
                            Set.of(),
                            RecordedAlphanumericCounts.over(jdbcTemplate),
                            progress);
        }

        long pairsSharingABand() {
            Long pairs = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT DISTINCT a.occurrence_id AS a, b.occurrence_id AS b"
                            + " FROM signature_band a JOIN signature_band b ON a.run_id = b.run_id"
                            + " AND a.band_ordinal = b.band_ordinal AND a.band_hash = b.band_hash"
                            + " AND a.occurrence_id < b.occurrence_id WHERE a.run_id = ?)",
                    Long.class,
                    stage4RunId.value());
            return pairs == null ? 0 : pairs;
        }
    }
}
