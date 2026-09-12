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
 * What stage 4 removes, and what it keeps (ADR-079, ADR-081, ADR-082).
 *
 * <p>Every document here is built from an explicit shingle set, so the similarity between any two of
 * them is an exact fraction this file states rather than a number that emerges from text. The
 * near-duplicate pairs sit far above the 0.80 cut on purpose: retrieval is probabilistic, and a pair
 * placed at the cut itself would be retrieved about 19 times in 20 (ADR-081), which is a flaky test
 * rather than a demanding one. Where the cut sits is ADR-081's to state; that documents on either
 * side of it are treated differently is what these tests pin.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Resolution")
@Issue("75")
@Link(name = "ADR-079", url = Adr.REDUNDANT_WITH_COVERS_NEAR_DUPLICATION_AND_CONTAINMENT, type = "adr")
@Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
@Link(name = "ADR-082", url = Adr.STAGE_4_JUDGES_ON_ITS_FIRST_RUN, type = "adr")
class RedundancyResolutionTest {

    /** Shingles two near-duplicate documents share: 99 of each document's 100, a Jaccard of 99/101. */
    private static final int SHARED_SHINGLES = 99;

    private static final double NEAR_DUPLICATE_JACCARD = 99.0 / 101.0;

    /** The contained document's whole shingle set, every one of which the container also holds. */
    private static final int CONTAINED_SHINGLES = 40;

    /** What the container holds on top of them — enough that the pair's Jaccard is nowhere near the cut. */
    private static final int CONTAINER_EXTRA_SHINGLES = 1_200;

    /** The fuller rendering, in alphanumeric characters of extracted text (ADR-079's survivor rule). */
    private static final long FULLER_TEXT = 50_000L;

    private static final long THINNER_TEXT = 10_000L;

    /** Not a real floor: no test here strips anything, so every shingle stays distinctive. */
    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The fuller rendering survives a near-duplicate set")
    @DisplayName("Of two near-identical documents the one with more extracted text survives, even when it arrived later")
    void keepsTheFullerRenderingRatherThanTheEarlierFile() {
        Fixture fixture = fixture();
        OccurrenceId scan = fixture.document(
                "scan.pdf", shingles(0, 100), THINNER_TEXT, Instant.parse("2020-01-01T00:00:00Z"));
        OccurrenceId digital = fixture.document(
                "digital.pdf", shingles(1, 100), FULLER_TEXT, Instant.parse("2026-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the fuller rendering keeps no verdict: it is the copy a reader gets, holding 50000"
                        + " alphanumeric characters against the other's 10000",
                () -> assertThat(redundantWith(digital)).isNull());
        claim(
                "and the thinner one is redundant with it, pointing at the survivor",
                () -> assertThat(redundantWith(scan)).isEqualTo(digital.value()));
        claim(
                "even though the thinner one is six years older -- stage 1 keeps the earliest copy"
                        + " because byte-identical copies leave nothing else to choose on, and stage 4"
                        + " keeps the fullest because here content can choose (ADR-079)",
                () -> assertThat(creationTimeOf(scan)).isLessThan(creationTimeOf(digital)));
        claim(
                "the stored relation says which of the two rules removed it",
                () -> assertThat(relationOf(scan)).isEqualTo("near-duplicate"));
        claim(
                "and the stored score is the exact Jaccard of the two shingle sets, 99 shared of 101"
                        + " between them, not an estimate read off the signatures",
                () -> assertThat(scoreOf(scan)).isCloseTo(NEAR_DUPLICATE_JACCARD, org.assertj.core.data.Offset.offset(1e-9)));
    }

    @Test
    @Story("A redundancy set resolves as a whole, not pair by pair")
    @DisplayName("Three mutually near-identical documents leave one survivor and two pointers to it")
    void resolvesAWholeComponentToOneSurvivor() {
        Fixture fixture = fixture();
        OccurrenceId first =
                fixture.document("first.pdf", shingles(0, 100), THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId second =
                fixture.document("second.pdf", shingles(1, 100), THINNER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));
        OccurrenceId survivor =
                fixture.document("third.pdf", shingles(2, 100), FULLER_TEXT, Instant.parse("2023-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "one member of the set survives, the one with the most extracted text",
                () -> assertThat(redundantWith(survivor)).isNull());
        claim(
                "and both others point at that same survivor rather than at each other -- resolving per"
                        + " pair could leave a pointer naming a document this pass had itself removed, which"
                        + " a reader following it would find gone",
                () -> assertThat(List.of(redundantWith(first), redundantWith(second)))
                        .containsExactly(survivor.value(), survivor.value()));
    }

    @Test
    @Story("A document wholly inside another is redundant with it")
    @DisplayName("A 40-shingle document inside a 1240-shingle one is removed, and the container is not")
    void removesTheContainedDocumentAndKeepsTheContainer() {
        Fixture fixture = fixture();
        Set<Long> contained = shingles(0, CONTAINED_SHINGLES);
        Set<Long> container = new LinkedHashSet<>(contained);
        container.addAll(shingles(10_000, CONTAINER_EXTRA_SHINGLES));
        OccurrenceId chapter =
                fixture.document("chapter.pdf", contained, THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId volume =
                fixture.document("volume.pdf", container, FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the chapter is redundant with the volume that prints it whole, though the two share only"
                        + " 40 shingles of the 1240 in their union -- a Jaccard of about 0.03, far below the"
                        + " 0.80 cut and invisible to banding, which is why containment has its own"
                        + " retrieval path (ADR-081)",
                () -> assertThat(redundantWith(chapter)).isEqualTo(volume.value()));
        claim(
                "the direction is fixed and asymmetric: the volume keeps no verdict, because removing it"
                        + " to keep the chapter would discard the other 1200 shingles (ADR-079)",
                () -> assertThat(redundantWith(volume)).isNull());
        claim(
                "the relation names containment rather than near-duplication",
                () -> assertThat(relationOf(chapter)).isEqualTo("contained-in"));
        claim(
                "and the score is the containment itself -- every one of the chapter's shingles is in the"
                        + " volume, so 1.0 -- rather than the Jaccard the near-duplicate path would store",
                () -> assertThat(scoreOf(chapter)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9)));
    }

    @Test
    @Story("Documents that share nothing are left alone")
    @DisplayName("Two unrelated documents earn no verdict and no pointer between them")
    void leavesUnrelatedDocumentsAlone() {
        Fixture fixture = fixture();
        OccurrenceId first =
                fixture.document("one.pdf", shingles(0, 100), FULLER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId second =
                fixture.document("two.pdf", shingles(5_000, 100), FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "neither document is redundant with the other: they share no shingle at all, so nothing"
                        + " retrieval offered survived exact scoring",
                () -> assertThat(List.of(redundantWith(first) == null, redundantWith(second) == null))
                        .containsExactly(true, true));
        claim(
                "and stage 4 wrote no verdict of any kind over them",
                () -> assertThat(redundantWithVerdictCount()).isZero());
    }

    /** {@code count} consecutive shingle hashes from {@code from} — an explicit set, so overlaps are exact. */
    private static Set<Long> shingles(long from, int count) {
        Set<Long> hashes = new LinkedHashSet<>();
        for (long i = 0; i < count; i++) {
            hashes.add(from + i);
        }
        return hashes;
    }

    private Long redundantWith(OccurrenceId occurrenceId) {
        return jdbcTemplate
                .query(
                        "SELECT redundant_with_occurrence_id FROM redundant_with WHERE occurrence_id = ?",
                        (resultSet, rowNumber) -> resultSet.getLong("redundant_with_occurrence_id"),
                        occurrenceId.value())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String relationOf(OccurrenceId occurrenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT relation FROM redundant_with WHERE occurrence_id = ?", String.class, occurrenceId.value());
    }

    private double scoreOf(OccurrenceId occurrenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT score FROM redundant_with WHERE occurrence_id = ?", Double.class, occurrenceId.value());
    }

    private long redundantWithVerdictCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE kind = 'REDUNDANT_WITH'", Long.class);
    }

    private String creationTimeOf(OccurrenceId occurrenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT creation_time FROM file_occurrence WHERE id = ?", String.class, occurrenceId.value());
    }

    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.startRun("extraction", "abc123", "{}", walkId, List.of());
        RunId stage3RunId = ledger.startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));
        RunId stage4RunId = ledger.startRun("content-redundancy", "ghi789", "{}", walkId, List.of(stage3RunId));
        return new Fixture(ledger, walkId, stage2RunId, stage3RunId, stage4RunId);
    }

    /** One walk and the three runs stage 4 reads across, with the documents each test populates. */
    private class Fixture {
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

        /** A stage-2 survivor with an explicit shingle set, a measured text size, and a creation time. */
        OccurrenceId document(String path, Set<Long> shingleHashes, long alphanumericCharCount, Instant createdAt) {
            ledger.fileOccurrence(
                    walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"), createdAt);
            OccurrenceId occurrenceId =
                    ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
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
                    occurrenceId.value(),
                    stage2RunId.value(),
                    "success",
                    0.5,
                    alphanumericCharCount,
                    alphanumericCharCount,
                    1,
                    1,
                    0,
                    0);
            return occurrenceId;
        }

        /**
         * The whole stage-4 pass: stage 3's document frequency (which containment retrieval reads to
         * find a document's rarest shingles), then every survivor's signature, then resolution.
         */
        void resolve() {
            new DocumentFrequency(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
            RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
            for (Long occurrenceId : jdbcTemplate.queryForList(
                    "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value())) {
                signatures.write(
                        new OccurrenceId(occurrenceId), stage4RunId, stage2RunId, Set.of(), NO_BOILERPLATE_FLOOR);
            }
            new RedundancyResolution(jdbcTemplate, ledger).resolve(stage4RunId, stage3RunId, stage2RunId, Set.of());
        }
    }
}
