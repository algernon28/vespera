package io.algernon.vespera.engine.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.BrokenOrOutOfScope.Outcome;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1's first pass, moved from {@code pipeline} into {@code corpus} unchanged (ADR-188): every
 * survivor is checked by {@link BrokenCheck}, its detected format recorded before any verdict (ADR-095),
 * and it is verdicted {@code broken} (ADR-068) or {@code out-of-scope} (ADR-146, ADR-171, ADR-178), or
 * kept, while the format mix is counted.
 *
 * <p>The file occurrences are written into the ledger by hand, with no walk, so this exercises the pass
 * alone. {@link OutOfScopeTest} pins each reason's wording; this pins that the pass applies the rules in
 * order, writes what they decide, counts what it saw, and says so through {@link CheckingProgress}. The
 * size limits handed in are small, so a few hundred bytes stand in for sixteen million.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Broken and out-of-scope check")
@Issue("406")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
class BrokenOrOutOfScopeTest {

    /** A pdf that opens correctly and stops before its end marker: damaged, so broken. */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** A ceiling far below the real one, so a small file stands in for a large one. */
    private static final long SMALL_CEILING = 100L;

    /** A largest size converted in parts, far below the real one, for the same reason. */
    private static final long SMALL_LARGEST = 400L;

    /** A file whose name begins with this is answered "not converted in parts" by the limits below. */
    private static final String NOT_CUT_PREFIX = "wide";

    /** Limits that cut every text file but the ones named {@link #NOT_CUT_PREFIX}. */
    private static final TextSizeLimits SMALL_LIMITS = new TextSizeLimits(
            SMALL_CEILING,
            SMALL_LARGEST,
            (file, subtype, size) -> !file.getFileName().toString().startsWith(NOT_CUT_PREFIX));

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the first pass decides about each survivor")
    @DisplayName("A damaged file is removed as broken, with the checker's reason; an intact text file is kept")
    @Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
    void verdictsTheBrokenAndKeepsTheRest(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Files.writeString(root.resolve("fine.txt"), "plain text content");
        Corpus corpus = new Corpus(root);
        OccurrenceId broken = corpus.occurrence("broken.pdf");
        OccurrenceId fine = corpus.occurrence("fine.txt");
        RunId run = corpus.run();
        String checkersReason = BrokenCheck.check(root.resolve("broken.pdf")).reason();
        RecordedChecks progress = new RecordedChecks();

        corpus.pass().verdictSurvivors(run, root, null, List.of(), List.of(), progress);

        claim(
                "the damaged pdf is verdicted broken, with the reason the checker gave",
                () -> assertThat(verdictsOf(broken)).containsExactly("BROKEN: " + checkersReason));
        claim(
                "the intact text file carries no verdict at all",
                () -> assertThat(verdictsOf(fine)).isEmpty());
        claim(
                "and each is reported as what it came to, in the order the survivors were read",
                () -> assertThat(progress.checked)
                        .containsExactly(
                                Map.entry(broken, new Outcome.Broken(checkersReason)),
                                Map.entry(fine, new Outcome.Kept())));
        claim(
                "what each was found to be is recorded, the removed one included",
                () -> {
                    assertThat(new DetectedFormats(jdbcTemplate).formatFor(broken, run)).contains(DetectedFormat.PDF);
                    assertThat(new DetectedFormats(jdbcTemplate).formatFor(fine, run))
                            .contains(DetectedFormat.PLAIN_TEXT);
                });
    }

    @Test
    @Story("What the first pass counts")
    @DisplayName("The tally counts every file checked by what it was found to be, removed ones and unread ones included")
    @Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
    void talliesWhatEveryFileWasFoundToBe(@TempDir Path root) throws Exception {
        Files.write(root.resolve("Report - Shortcut.lnk"), WINDOWS_SHORTCUT_HEADER);
        Files.createFile(root.resolve("empty.txt"));
        Files.writeString(root.resolve("notes.md"), "# Notes\n\n- first\n");
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Corpus corpus = new Corpus(root);
        corpus.occurrence("Report - Shortcut.lnk");
        corpus.occurrence("empty.txt");
        corpus.occurrence("notes.md");
        corpus.occurrence("broken.pdf");

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, null, List.of(), List.of(), new RecordedChecks());

        claim(
                "the shortcut matched nothing, and its first four bytes are counted, so a kind arriving in bulk shows",
                () -> {
                    assertThat(mix.byFormat()).containsEntry(DetectedFormat.UNRECOGNISED, 1);
                    assertThat(mix.unrecognisedLeadingBytes()).containsExactly(Map.entry("4C 00 00 00", 1));
                });
        claim(
                "the empty file is counted as one nothing was read from, apart from the one that matched nothing",
                () -> assertThat(mix.byFormat()).containsEntry(DetectedFormat.FLOOR_STOPPED, 1));
        claim(
                "the damaged pdf is counted as a pdf although it was removed",
                () -> assertThat(mix.byFormat()).containsEntry(DetectedFormat.PDF, 1));
        claim(
                "and the Markdown file is counted under text, with the finer label beside it",
                () -> assertThat(mix.bySubtype())
                        .containsEntry(DetectedFormat.PLAIN_TEXT, Map.of(DetectedSubtype.MARKDOWN, 1)));
    }

    /** The verdicts on {@code occurrence}, as kind and reason. */
    private List<String> verdictsOf(OccurrenceId occurrence) {
        return jdbcTemplate.query(
                "SELECT kind, reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind") + ": " + resultSet.getString("reason"),
                occurrence.value());
    }

    /** A walk recorded by hand over files already written under {@code root}, and the pass over it. */
    private final class Corpus {
        private final Path root;
        private final Ledger ledger = new Ledger(jdbcTemplate);
        private final WalkId walk;

        Corpus(Path root) {
            this.root = root;
            this.walk = ledger.walks().startWalk(root);
        }

        OccurrenceId occurrence(String name) throws IOException {
            ledger.occurrences().fileOccurrence(walk, new OccurrencePath(name), Files.size(root.resolve(name)), CREATED, CREATED);
            return ledger.occurrences().occurrenceId(walk, new OccurrencePath(name)).orElseThrow();
        }

        RunId run() {
            return ledger.runs().startRun("byte-level-reduction", "corpus-under-test", "{}", walk, List.of());
        }

        BrokenOrOutOfScope pass() {
            return new BrokenOrOutOfScope(ledger, new DetectedFormats(jdbcTemplate), SMALL_LIMITS);
        }
    }

    /** Every call the pass made, in order. */
    private static final class RecordedChecks implements CheckingProgress {
        final List<Map.Entry<OccurrenceId, Outcome>> checked = new ArrayList<>();
        final List<OccurrenceId> unreadable = new ArrayList<>();
        final List<OccurrenceId> codeUnreadable = new ArrayList<>();

        @Override
        public void checked(OccurrenceId occurrence, Outcome outcome) {
            checked.add(Map.entry(occurrence, outcome));
        }

        @Override
        public void timestampsUnreadable(OccurrenceId occurrence, Exception cause) {
            unreadable.add(occurrence);
        }

        @Override
        public void codeUnreadable(OccurrenceId occurrence, Exception cause) {
            codeUnreadable.add(occurrence);
        }
    }

    /** The first four bytes of a Windows shortcut, which no detection rule recognises. */
    private static final byte[] WINDOWS_SHORTCUT_HEADER = {0x4C, 0x00, 0x00, 0x00};
}
