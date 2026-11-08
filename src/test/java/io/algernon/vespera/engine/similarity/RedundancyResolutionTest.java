package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
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

    /** The window the application ships: five words (vespera.redundancy.shingle-words). */
    private static final ShingleParameters FIVE_WORD_SHINGLES = new ShingleParameters(5);

    /** The signature the application ships: 128 permutations in 16 bands of 8 rows, and its seed. */
    private static final MinHashParameters THE_SHIPPED_MINHASH =
            new MinHashParameters(128, 16, 8, 0x5EED_C0FF_EE12_3456L);

    /** The cuts the application ships: 0.80 and 0.95, over a sample of 32 shingles asking 24 hits. */
    private static final RedundancyThresholds THE_SHIPPED_THRESHOLDS = new RedundancyThresholds(0.80, 0.95, 32, 24);

    private static final double NEAR_DUPLICATE_JACCARD = 99.0 / 101.0;

    /** The contained document's whole shingle set, every one of which the container also holds. */
    private static final int CONTAINED_SHINGLES = 40;

    /** What the container holds on top of them — enough that the pair's Jaccard is nowhere near the cut. */
    private static final int CONTAINER_EXTRA_SHINGLES = 1_200;

    /** What a document in the middle of a chain holds on top of the one inside it, and inside the one around it. */
    private static final int MIDDLE_EXTRA_SHINGLES = 400;

    /** The fuller rendering, in alphanumeric characters of extracted text (ADR-079's survivor rule). */
    private static final long FULLER_TEXT = 50_000L;

    private static final long THINNER_TEXT = 10_000L;

    /** Not a real floor: no test here strips anything, so every shingle stays distinctive. */
    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    /** Shingles a pair at the near-duplicate cut shares: 80 of the 100 between them, each holding 10 of its own. */
    private static final int SHARED_AT_THE_CUT = 80;

    private static final int OWN_AT_THE_CUT = 10;

    /** The Jaccard of that pair, 80/100: the shipped near-duplicate cut exactly. */
    private static final double JACCARD_AT_THE_CUT = 0.80;

    /** Shingles a pair just below the cut shares: 79 of the 100 between them, one more of its own on one side. */
    private static final int SHARED_JUST_BELOW = 79;

    /** A document measured against the containment cut: 100 shingles, some of them in a far larger one. */
    private static final int SMALL_DOCUMENT_SHINGLES = 100;

    /** How many of its 100 the larger one holds at the containment cut, 95/100 = 0.95, and just below it. */
    private static final int HELD_AT_THE_CUT = 95;

    private static final int HELD_JUST_BELOW = 94;

    /** The containment of that document, 95/100: the shipped containment cut exactly. */
    private static final double CONTAINMENT_AT_THE_CUT = 0.95;

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
        assumeTrue(
                creationTimeOf(scan).compareTo(creationTimeOf(digital)) < 0,
                "the thinner rendering is the older file");

        fixture.resolve();

        claim(
                "the fuller rendering keeps no verdict: it is the copy a reader gets, holding 50000"
                        + " alphanumeric characters against the other's 10000",
                () -> assertThat(redundantWith(digital)).isNull());
        claim(
                "and the thinner one is redundant with it, pointing at the survivor, even though the thinner one is"
                        + " six years older -- stage 1 keeps the earliest copy because byte-identical copies leave"
                        + " nothing else to choose on, and stage 4 keeps the fullest because here content can choose"
                        + " (ADR-079)",
                () -> assertThat(redundantWith(scan)).isEqualTo(digital.value()));
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

    /**
     * The near-duplicate cut is 0.80, judged at or above (ADR-079, ADR-081): a pair scoring exactly 80 shared of 100
     * is removed, one scoring 79 of 100 is not. Raising the cut, judging strictly above it, or lowering it by a
     * hundredth each turns this red. Both pairs are retrieved by the shipped signature and seed; a pair at the cut is
     * retrieved about 19 times in 20 over all seeds (ADR-081), so a change of seed that loses one is seen here as a
     * failure of the first claim, never as a silent pass.
     */
    @Test
    @Story("A pair is near-identical from the cut up, and not below it")
    @DisplayName("Two documents sharing 80 of the 100 shingles between them are judged near-identical, and two sharing 79 are not")
    void aPairAtTheNearDuplicateCutIsJudgedAndAPairJustBelowItIsNot() {
        Fixture fixture = fixture();
        OccurrenceId atTheCut = fixture.document(
                "at-the-cut.pdf", union(shingles(0, SHARED_AT_THE_CUT), shingles(1_000, OWN_AT_THE_CUT)), THINNER_TEXT,
                Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId atTheCutKept = fixture.document(
                "at-the-cut-kept.pdf", union(shingles(0, SHARED_AT_THE_CUT), shingles(2_000, OWN_AT_THE_CUT)), FULLER_TEXT,
                Instant.parse("2022-01-01T00:00:00Z"));
        OccurrenceId justBelow = fixture.document(
                "just-below.pdf",
                union(shingles(50_000, SHARED_JUST_BELOW), shingles(51_000, OWN_AT_THE_CUT + 1)),
                THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId justBelowOther = fixture.document(
                "just-below-other.pdf", union(shingles(50_000, SHARED_JUST_BELOW), shingles(52_000, OWN_AT_THE_CUT)),
                FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the pair sharing 80 of the 100 shingles between them scores exactly 0.80, the cut itself, and the"
                        + " thinner of the two is removed in favour of the fuller: the cut is met from 0.80 up",
                () -> assertThat(redundantWith(atTheCut)).isEqualTo(atTheCutKept.value()));
        claim(
                "the stored relation names near-duplication",
                () -> assertThat(relationOf(atTheCut)).isEqualTo("near-duplicate"));
        claim(
                "and the stored score is that exact 0.80",
                () -> assertThat(scoreOf(atTheCut)).isEqualTo(JACCARD_AT_THE_CUT));
        claim(
                "the pair sharing 79 of the 100 shingles between them scores 0.79, a hundredth below the cut, and"
                        + " neither of the two is removed",
                () -> assertThat(List.of(redundantWith(justBelow) == null, redundantWith(justBelowOther) == null))
                        .containsExactly(true, true));
    }

    /**
     * The containment cut is 0.95, judged at or above (ADR-079, ADR-081): a 100-shingle document holding 95 of its
     * shingles in a far larger one is removed as inside it, one holding 94 is not. Raising the cut, judging strictly
     * above it, or lowering it by a hundredth each turns this red. Each small document's 32 rarest shingles are its
     * own few plus shared ones enough that its container holds at least the 24 retrieval asks for, so both pairs are
     * measured and the cut alone decides them.
     */
    @Test
    @Story("A document is inside another from the cut up, and not below it")
    @DisplayName("A document with 95 of its 100 shingles in a far larger one is judged inside it, and one with 94 is not")
    @Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
    void aDocumentAtTheContainmentCutIsJudgedAndOneJustBelowItIsNot() {
        Fixture fixture = fixture();
        OccurrenceId atTheCut = fixture.document(
                "at-the-cut.pdf",
                union(shingles(0, HELD_AT_THE_CUT), shingles(1_000, SMALL_DOCUMENT_SHINGLES - HELD_AT_THE_CUT)),
                THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId atTheCutContainer = fixture.document(
                "at-the-cut-container.pdf",
                union(shingles(0, HELD_AT_THE_CUT), shingles(10_000, CONTAINER_EXTRA_SHINGLES)),
                FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));
        OccurrenceId justBelow = fixture.document(
                "just-below.pdf",
                union(shingles(50_000, HELD_JUST_BELOW), shingles(51_000, SMALL_DOCUMENT_SHINGLES - HELD_JUST_BELOW)),
                THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        OccurrenceId justBelowContainer = fixture.document(
                "just-below-container.pdf",
                union(shingles(50_000, HELD_JUST_BELOW), shingles(60_000, CONTAINER_EXTRA_SHINGLES)),
                FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the document with 95 of its 100 shingles in the larger one is inside it at exactly 0.95, the cut"
                        + " itself, and is removed as inside it: the cut is met from 0.95 up",
                () -> assertThat(redundantWith(atTheCut)).isEqualTo(atTheCutContainer.value()));
        claim(
                "the stored relation names containment",
                () -> assertThat(relationOf(atTheCut)).isEqualTo("contained-in"));
        claim(
                "and the stored score is that exact 0.95",
                () -> assertThat(scoreOf(atTheCut)).isEqualTo(CONTAINMENT_AT_THE_CUT));
        claim(
                "the document with 94 of its 100 shingles in the larger one is inside it at 0.94, a hundredth below"
                        + " the cut, and neither it nor the larger one is removed",
                () -> assertThat(List.of(redundantWith(justBelow) == null, redundantWith(justBelowContainer) == null))
                        .containsExactly(true, true));
    }

    @Test
    @Story("No pointer names a document the same pass removed")
    @DisplayName("Of a document inside a second that is inside a third, no document is left pointing at one that was itself removed")
    @Link(name = "ADR-234", url = Adr.NO_REDUNDANCY_POINTER_NAMES_A_REMOVED_DOCUMENT, type = "adr")
    void aChainOfContainmentsLeavesNoPointerAtARemovedDocument() {
        Fixture fixture = fixture();
        Set<Long> inner = shingles(0, CONTAINED_SHINGLES);
        Set<Long> middle = new LinkedHashSet<>(inner);
        middle.addAll(shingles(10_000, MIDDLE_EXTRA_SHINGLES));
        Set<Long> outer = new LinkedHashSet<>(middle);
        outer.addAll(shingles(20_000, CONTAINER_EXTRA_SHINGLES));
        OccurrenceId chapter = fixture.document("chapter.pdf", inner, THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        fixture.document("part.pdf", middle, THINNER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));
        fixture.document("volume.pdf", outer, FULLER_TEXT, Instant.parse("2023-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the chapter, the smallest of the three, is removed: it is wholly inside both others",
                () -> assertThat(redundantWith(chapter)).isNotNull());
        claim(
                "and every document the pass points a removed one at is a document the pass kept -- a reader"
                        + " following the pointer finds the text, not another pointer",
                () -> assertThat(pointersAtRemovedDocuments()).isEmpty());
    }

    @Test
    @Story("No pointer names a document the same pass removed")
    @DisplayName("The copy kept of two near-identical documents is not removed afterwards for lying inside a third, leaving the other copy pointing at nothing kept")
    @Link(name = "ADR-234", url = Adr.NO_REDUNDANCY_POINTER_NAMES_A_REMOVED_DOCUMENT, type = "adr")
    void aNearDuplicateSurvivorInsideAnotherDocumentLeavesNoPointerAtARemovedDocument() {
        Fixture fixture = fixture();
        Set<Long> digitalShingles = shingles(1, 100);
        OccurrenceId scan = fixture.document("scan.pdf", shingles(0, 100), THINNER_TEXT, Instant.parse("2021-01-01T00:00:00Z"));
        fixture.document("digital.pdf", digitalShingles, FULLER_TEXT, Instant.parse("2022-01-01T00:00:00Z"));
        Set<Long> anthology = new LinkedHashSet<>(digitalShingles);
        anthology.addAll(shingles(10_000, CONTAINER_EXTRA_SHINGLES));
        fixture.document("anthology.pdf", anthology, FULLER_TEXT, Instant.parse("2023-01-01T00:00:00Z"));

        fixture.resolve();

        claim(
                "the scan is removed as a near-duplicate of the digital copy",
                () -> assertThat(relationOf(scan)).isEqualTo("near-duplicate"));
        claim(
                "and every document the pass points a removed one at is a document the pass kept",
                () -> assertThat(pointersAtRemovedDocuments()).isEmpty());
    }

    /** Every pointer whose target is itself removed under the same run, as "removed -> target". */
    private List<String> pointersAtRemovedDocuments() {
        return jdbcTemplate.query(
                "SELECT r.occurrence_id, r.redundant_with_occurrence_id FROM redundant_with r"
                        + " JOIN redundant_with t ON t.occurrence_id = r.redundant_with_occurrence_id"
                        + " AND t.run_id = r.run_id",
                (resultSet, rowNumber) -> resultSet.getLong(1) + " -> " + resultSet.getLong(2));
    }

    /** {@code count} consecutive shingle hashes from {@code from} — an explicit set, so overlaps are exact. */
    @SafeVarargs
    private static Set<Long> union(Set<Long>... parts) {
        Set<Long> hashes = new LinkedHashSet<>();
        for (Set<Long> part : parts) {
            hashes.addAll(part);
        }
        return hashes;
    }

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
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());
        RunId stage3RunId = ledger.runs().startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));
        RunId stage4RunId = ledger.runs().startRun("content-redundancy", "ghi789", "{}", walkId, List.of(stage3RunId));
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
            ledger.occurrences().fileOccurrence(
                    walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"), createdAt);
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
            for (long hash : shingleHashes) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        stage2RunId.value(),
                        FIVE_WORD_SHINGLES.identity(),
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
                        new OccurrenceId(occurrenceId), stage4RunId, stage2RunId, Set.of(), NO_BOILERPLATE_FLOOR, FIVE_WORD_SHINGLES, THE_SHIPPED_MINHASH);
            }
            new RedundancyResolution(jdbcTemplate, ledger)
                    .resolve(stage4RunId, stage3RunId, stage2RunId, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), FIVE_WORD_SHINGLES, THE_SHIPPED_THRESHOLDS);
        }

        /** The same pass, telling {@code progress} about its loops. */
        void resolve(ResolutionProgress progress) {
            signAndMeasure();
            new RedundancyResolution(jdbcTemplate, ledger)
                    .resolve(
                            stage4RunId,
                            stage3RunId,
                            stage2RunId,
                            Set.of(),
                            RecordedAlphanumericCounts.over(jdbcTemplate),
                            FIVE_WORD_SHINGLES, THE_SHIPPED_THRESHOLDS,
                            progress);
        }

        /** Stage 3's document frequency and every survivor's signature: the pass before resolution. */
        void signAndMeasure() {
            new DocumentFrequency(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
            RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
            for (Long occurrenceId : jdbcTemplate.queryForList(
                    "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value())) {
                signatures.write(
                        new OccurrenceId(occurrenceId), stage4RunId, stage2RunId, Set.of(), NO_BOILERPLATE_FLOOR, FIVE_WORD_SHINGLES, THE_SHIPPED_MINHASH);
            }
        }

        /** Resolution alone, over what {@link #signAndMeasure} wrote. */
        void resolveSigned() {
            new RedundancyResolution(jdbcTemplate, ledger)
                    .resolve(stage4RunId, stage3RunId, stage2RunId, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), FIVE_WORD_SHINGLES, THE_SHIPPED_THRESHOLDS);
        }

        /** The same pass, with resolution reading the store through {@code resolutionTemplate}. */
        void resolveWith(JdbcTemplate resolutionTemplate) {
            resolveWith(jdbcTemplate, resolutionTemplate);
        }

        /** The same pass, with signatures and resolution each reading the store through its own template. */
        void resolveWith(JdbcTemplate signatureTemplate, JdbcTemplate resolutionTemplate) {
            new DocumentFrequency(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
            RedundancySignatures signatures = new RedundancySignatures(signatureTemplate);
            for (Long occurrenceId : jdbcTemplate.queryForList(
                    "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value())) {
                signatures.write(
                        new OccurrenceId(occurrenceId), stage4RunId, stage2RunId, Set.of(), NO_BOILERPLATE_FLOOR, FIVE_WORD_SHINGLES, THE_SHIPPED_MINHASH);
            }
            new RedundancyResolution(resolutionTemplate, ledger)
                    .resolve(stage4RunId, stage3RunId, stage2RunId, Set.of(), RecordedAlphanumericCounts.over(resolutionTemplate), FIVE_WORD_SHINGLES, THE_SHIPPED_THRESHOLDS);
        }
    }
}
