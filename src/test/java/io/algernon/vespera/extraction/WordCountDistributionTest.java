package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3's count of the words of stage 2's survivors, by detected format (ADR-282 section 4): each survivor
 * carrying a metrics row under stage 2's run is counted once, in the band its stored {@code word_count} falls in,
 * under the detected format {@code pipeline} hands over for it, and what each candidate word floor would remove is
 * the sum of the bands below it.
 *
 * <p>One walk and one stage-2 run, with hand-written {@code extraction_metric} rows, the shape {@link
 * ConfidenceDistributionTest} uses. The formats are handed over as {@code pipeline} hands them, a page of
 * occurrences at a time, from a map this test holds in place of {@code corpus}'s table.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
class WordCountDistributionTest {

    /** The fourteen bands of ADR-282 section 4, in order; the last has no most. */
    private static final List<WordCountDistribution.Band> THE_RECORDED_BANDS = List.of(
            new WordCountDistribution.Band(0, 1),
            new WordCountDistribution.Band(2, 2),
            new WordCountDistribution.Band(3, 3),
            new WordCountDistribution.Band(4, 4),
            new WordCountDistribution.Band(5, 5),
            new WordCountDistribution.Band(6, 6),
            new WordCountDistribution.Band(7, 7),
            new WordCountDistribution.Band(8, 8),
            new WordCountDistribution.Band(9, 9),
            new WordCountDistribution.Band(10, 10),
            new WordCountDistribution.Band(11, 19),
            new WordCountDistribution.Band(20, 49),
            new WordCountDistribution.Band(50, 99),
            new WordCountDistribution.Band(100, Long.MAX_VALUE));

    /** The candidate floors of ADR-282 section 4, each on a band's lower edge. */
    private static final List<Long> THE_RECORDED_CANDIDATE_FLOORS =
            List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 20L, 50L, 100L);

    /** The words of a one-line note, a stray OCR token, a button's label. */
    private static final long ONE_WORD = 1;

    /** The words of a {@code .url} shortcut's two lines. */
    private static final long TWO_WORDS = 2;

    /** A poster's words, in the band from 11 to 19. */
    private static final long TWELVE_WORDS = 12;

    /** A short scan's words, in the band from 20 to 49. */
    private static final long THIRTY_FIVE_WORDS = 35;

    /** A letter's words, in the band of 100 or more. */
    private static final long A_HUNDRED_AND_TWENTY_WORDS = 120;

    /** Two full pages of the ledger's 1,000 survivors and a half page. */
    private static final int TWO_AND_A_HALF_PAGES = 2_500;

    /** The most occurrences a page of the ledger holds. */
    private static final int A_PAGE = 1_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The words of the files text extraction kept")
    @DisplayName("The bands of words and the candidate floors are the recorded ones")
    void theBandsAndTheCandidateFloorsAreTheRecordedOnes() {
        claim(
                "fourteen bands: one or fewer, then each count from 2 to 10, then 11 to 19, 20 to 49, 50 to 99 and 100"
                        + " or more",
                () -> assertThat(WordCountDistribution.BANDS).containsExactlyElementsOf(THE_RECORDED_BANDS));
        claim(
                "and the floors whose cost is shown are 2 to 10, 20, 50 and 100, each the lower edge of a band, so"
                        + " what a floor removes is the sum of the bands below it",
                () -> assertThat(WordCountDistribution.CANDIDATE_FLOORS)
                        .containsExactlyElementsOf(THE_RECORDED_CANDIDATE_FLOORS));
    }

    @Test
    @Story("The words of the files text extraction kept")
    @DisplayName("Each surviving file is counted once, in the band of its words, under its detected format")
    void eachSurvivorIsCountedInItsBandUnderItsDetectedFormat() {
        Fixture fixture = aMixOfFormats();

        WordCountDistribution.Distribution distribution = fixture.measure();

        claim(
                "the formats are those with a survivor counted, in the order the detected formats are declared:"
                        + " PDF, then image, then text",
                () -> assertThat(distribution.formats())
                        .containsExactly(DetectedFormat.PDF, DetectedFormat.IMAGE, DetectedFormat.PLAIN_TEXT));
        claim(
                "the scan of " + THIRTY_FIVE_WORDS + " words is in the band from 20 to 49; the two images of "
                        + ONE_WORD + " word in the first band and the poster of " + TWELVE_WORDS + " in the band from"
                        + " 11 to 19; the note of " + ONE_WORD + " word in the first band, the shortcut of "
                        + TWO_WORDS + " in the band of 2 and the letter of " + A_HUNDRED_AND_TWENTY_WORDS
                        + " in the band of 100 or more",
                () -> assertThat(distribution.survivorsByFormat())
                        .isEqualTo(Map.of(
                                DetectedFormat.PDF, inBands(Map.of(11, 1L)),
                                DetectedFormat.IMAGE, inBands(Map.of(0, 2L, 10, 1L)),
                                DetectedFormat.PLAIN_TEXT, inBands(Map.of(0, 1L, 1, 1L, 13, 1L)))));
        claim(
                "and a band's survivors over every format are the sum of the formats': three in the first band",
                () -> assertThat(distribution.survivors(THE_RECORDED_BANDS.getFirst())).isEqualTo(3L));
    }

    @Test
    @Story("What a word floor would remove")
    @DisplayName("A candidate floor removes, in each detected format, the surviving files with fewer words than it")
    void aFloorRemovesTheSurvivorsWithFewerWordsThanItInEachFormat() {
        WordCountDistribution.Distribution distribution = aMixOfFormats().measure();

        Map<Long, List<Long>> removed = new LinkedHashMap<>();
        for (long floor : THE_RECORDED_CANDIDATE_FLOORS) {
            removed.put(floor, List.of(
                    distribution.removedBy(floor, DetectedFormat.PDF),
                    distribution.removedBy(floor, DetectedFormat.IMAGE),
                    distribution.removedBy(floor, DetectedFormat.PLAIN_TEXT),
                    distribution.removedBy(floor)));
        }

        Map<Long, List<Long>> expected = new LinkedHashMap<>();
        expected.put(2L, List.of(0L, 2L, 1L, 3L));
        for (long floor = 3; floor <= 10; floor++) {
            expected.put(floor, List.of(0L, 2L, 2L, 4L));
        }
        expected.put(20L, List.of(0L, 3L, 2L, 5L));
        expected.put(50L, List.of(1L, 3L, 2L, 6L));
        expected.put(100L, List.of(1L, 3L, 2L, 6L));
        claim(
                "for each floor, as PDF, image, text and every format: a floor of 2 removes the two one-word images"
                        + " and the one-word note; from 3 the two-word shortcut too; at 20 the poster of "
                        + TWELVE_WORDS + "; at 50 the scan of " + THIRTY_FIVE_WORDS + "; and no floor up to 100"
                        + " removes the letter of " + A_HUNDRED_AND_TWENTY_WORDS,
                () -> assertThat(removed).containsExactlyEntriesOf(expected));
    }

    @Test
    @Story("The words of the files text extraction kept")
    @DisplayName("A file text extraction removed, and a file measured under another run only, are counted nowhere")
    void anOccurrenceStageTwoRemovedIsNotCounted() {
        Fixture fixture = fixture("C:/corpus-of-words-removed");
        fixture.survivor("kept.txt", DetectedFormat.PLAIN_TEXT, TWO_WORDS);
        fixture.removed("removed.png", DetectedFormat.IMAGE, ONE_WORD);
        fixture.measuredUnderAnotherRunOnly("measured-elsewhere.pdf", DetectedFormat.PDF, ONE_WORD);

        WordCountDistribution.Distribution distribution = fixture.measure();

        claim(
                "only the one survivor with a metrics row under the run is counted: the removed image and the PDF"
                        + " measured under another run add no format and no count",
                () -> assertThat(distribution.survivorsByFormat())
                        .isEqualTo(Map.of(DetectedFormat.PLAIN_TEXT, inBands(Map.of(1, 1L)))));
    }

    @Test
    @Story("The words of the files text extraction kept")
    @DisplayName("The detected formats are asked for a page of surviving files at a time")
    void theFormatsAreAskedForAPageOfSurvivorsAtATime() {
        Fixture fixture = fixture("C:/corpus-of-words-in-pages");
        fixture.survivorsInBulk(TWO_AND_A_HALF_PAGES, DetectedFormat.PLAIN_TEXT, TWO_WORDS);
        List<Integer> pagesAsked = new ArrayList<>();

        WordCountDistribution.Distribution distribution = fixture.measure(page -> {
            pagesAsked.add(page.size());
            return fixture.formatsOf(page);
        });

        claim(
                "the " + TWO_AND_A_HALF_PAGES + " survivors are handed over in the ledger's pages of " + A_PAGE
                        + ", in turn, and never all at once",
                () -> assertThat(pagesAsked).containsExactly(A_PAGE, A_PAGE, TWO_AND_A_HALF_PAGES - 2 * A_PAGE));
        claim(
                "and every one of them is counted, in the band of " + TWO_WORDS,
                () -> assertThat(distribution.survivors(DetectedFormat.PLAIN_TEXT, THE_RECORDED_BANDS.get(1)))
                        .isEqualTo((long) TWO_AND_A_HALF_PAGES));
    }

    @Test
    @Story("The words of the files text extraction kept")
    @DisplayName("A surviving file measured with no detected format handed over for it is refused, by its id")
    void aCountedSurvivorWithNoDetectedFormatIsRefused() {
        Fixture fixture = fixture("C:/corpus-of-words-no-format");
        OccurrenceId withoutFormat = fixture.survivor("no-format.txt", null, TWO_WORDS);

        claim(
                "a survivor of text extraction has a detected format by construction, so one handed over without is"
                        + " a fault of the caller, and the measurement stops naming the occurrence",
                () -> assertThatThrownBy(fixture::measure)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(String.valueOf(withoutFormat.value())));
    }

    @Test
    @Story("What a word floor would remove")
    @DisplayName("A floor that is not among the candidates, and a count of bands of another length, are refused")
    void aFloorNotAmongTheCandidatesIsRefused() {
        WordCountDistribution.Distribution distribution = aMixOfFormats().measure();

        claim(
                "15 is no band's lower edge, so what it removes cannot be read off the bands, and it is refused",
                () -> assertThatThrownBy(() -> distribution.removedBy(15, DetectedFormat.PLAIN_TEXT))
                        .isInstanceOf(IllegalArgumentException.class));
        claim(
                "and a format's counts that are not one for each of the fourteen bands are refused when the value is"
                        + " made",
                () -> assertThatThrownBy(() -> new WordCountDistribution.Distribution(
                                Map.of(DetectedFormat.PDF, List.of(1L, 2L))))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    /**
     * A PDF scan of {@link #THIRTY_FIVE_WORDS}; two images of {@link #ONE_WORD} and a poster of {@link
     * #TWELVE_WORDS}; a note of {@link #ONE_WORD}, a shortcut of {@link #TWO_WORDS} and a letter of {@link
     * #A_HUNDRED_AND_TWENTY_WORDS}, as text. Every one a survivor.
     */
    private Fixture aMixOfFormats() {
        Fixture fixture = fixture("C:/corpus-of-words-mixed");
        fixture.survivor("scan.pdf", DetectedFormat.PDF, THIRTY_FIVE_WORDS);
        fixture.survivor("stray-token.png", DetectedFormat.IMAGE, ONE_WORD);
        fixture.survivor("button.png", DetectedFormat.IMAGE, ONE_WORD);
        fixture.survivor("poster.png", DetectedFormat.IMAGE, TWELVE_WORDS);
        fixture.survivor("note.txt", DetectedFormat.PLAIN_TEXT, ONE_WORD);
        fixture.survivor("shortcut.url", DetectedFormat.PLAIN_TEXT, TWO_WORDS);
        fixture.survivor("letter.txt", DetectedFormat.PLAIN_TEXT, A_HUNDRED_AND_TWENTY_WORDS);
        return fixture;
    }

    /** Fourteen counts, one a band, zero but where {@code counts} names a band's index. */
    private static List<Long> inBands(Map<Integer, Long> counts) {
        List<Long> bands = new ArrayList<>(Collections.nCopies(THE_RECORDED_BANDS.size(), 0L));
        counts.forEach(bands::set);
        return bands;
    }

    private Fixture fixture(String root) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of(root));
        RunId stage2RunId = ledger.runs().startRun("extraction", "words1", "{}", walkId, List.of());
        RunId anotherStage2RunId = ledger.runs().startRun("extraction", "words2", "{\"another\":1}", walkId, List.of());
        return new Fixture(ledger, walkId, stage2RunId, anotherStage2RunId);
    }

    private class Fixture {
        private final Ledger ledger;
        private final WalkId walkId;
        private final RunId stage2RunId;
        private final RunId anotherStage2RunId;

        /** What {@code pipeline} would read off {@code corpus}'s table for each occurrence. */
        private final Map<OccurrenceId, DetectedFormat> formats = new HashMap<>();

        Fixture(Ledger ledger, WalkId walkId, RunId stage2RunId, RunId anotherStage2RunId) {
            this.ledger = ledger;
            this.walkId = walkId;
            this.stage2RunId = stage2RunId;
            this.anotherStage2RunId = anotherStage2RunId;
        }

        OccurrenceId survivor(String path, DetectedFormat format, long words) {
            OccurrenceId occurrence = occurrence(path, format);
            insertMetric(occurrence, stage2RunId, words);
            return occurrence;
        }

        void removed(String path, DetectedFormat format, long words) {
            OccurrenceId occurrence = survivor(path, format, words);
            ledger.verdicts().verdict(occurrence, stage2RunId, VerdictKind.DEGENERATE_OUTPUT, "removed by this test");
        }

        void measuredUnderAnotherRunOnly(String path, DetectedFormat format, long words) {
            insertMetric(occurrence(path, format), anotherStage2RunId, words);
        }

        void survivorsInBulk(int count, DetectedFormat format, long words) {
            List<Object[]> occurrences = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                occurrences.add(new Object[] {walkId.value(), "bulk-" + i + ".txt"});
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
                    occurrences);
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walkId.value());
            List<Object[]> metrics = new ArrayList<>();
            for (long id : ids) {
                formats.put(new OccurrenceId(id), format);
                metrics.add(new Object[] {id, stage2RunId.value(), words});
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count)"
                            + " VALUES (?, ?, 'success', 0.5, 10, 9, ?, 9, 0, 0)",
                    metrics);
        }

        /** The formats of {@code page} this fixture knows, as {@code pipeline} hands them over. */
        Map<OccurrenceId, DetectedFormat> formatsOf(List<OccurrenceId> page) {
            return page.stream()
                    .filter(formats::containsKey)
                    .collect(Collectors.toMap(Function.identity(), formats::get));
        }

        WordCountDistribution.Distribution measure() {
            return measure(this::formatsOf);
        }

        WordCountDistribution.Distribution measure(
                Function<List<OccurrenceId>, Map<OccurrenceId, DetectedFormat>> formatsOf) {
            return new WordCountDistribution(jdbcTemplate, ledger)
                    .measure(stage2RunId, formatsOf, ExtractionStatementProgress.NONE);
        }

        private OccurrenceId occurrence(String path, DetectedFormat format) {
            ledger.occurrences().fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1,
                    Instant.parse("2026-10-19T10:15:30Z"),
                    Instant.parse("2026-10-19T08:00:00Z"));
            OccurrenceId occurrence = ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
            if (format != null) {
                formats.put(occurrence, format);
            }
            return occurrence;
        }

        private void insertMetric(OccurrenceId occurrence, RunId run, long words) {
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrence.value(),
                    run.value(),
                    "success",
                    0.5,
                    words * 6,
                    words * 5,
                    words,
                    words * 5,
                    0,
                    0);
        }
    }
}
