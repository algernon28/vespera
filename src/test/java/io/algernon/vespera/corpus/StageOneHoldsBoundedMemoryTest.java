package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.SizedOccurrence;
import io.algernon.vespera.ledger.WalkId;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1 holds no more of a run's survivors than one size at a time (ADR-200, settling #405), and what
 * it records is the same set of rows. The order in which rows of different sizes are inserted did change,
 * from the order each size was first met to size order; nothing reads that order, the change was accepted
 * as it is, and no claim here holds the rows to it (ADR-200 section 6). What is held is the set, read back
 * by path, and the order the pass works in, through the calls it makes.
 *
 * <p>The fixture has four sizes, recorded interleaved so that id order and size order disagree: size 11
 * (two files, different bytes), size 20 (three identical files), size 30 (two identical files and a third
 * of other bytes) and size 5 (one file, alone). Every expectation below is written out, not computed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Bounded memory")
@Issue("405")
@Link(name = "ADR-200", url = Adr.STAGE_1_HOLDS_ONE_SIZE_AT_A_TIME, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class StageOneHoldsBoundedMemoryTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-01T00:00:00Z");

    /** Nine survivors: the three of size 20, the three of size 30, the two of size 11 and the one of size 5. */
    private static final int SURVIVORS = 9;

    /** The lone file of size 5, the two files of size 11, and the one look-ahead read to learn a size has ended. */
    private static final int FIRST_SIZE_AND_ONE_LOOK_AHEAD = 4;

    /**
     * The three files of size 20, or the three of size 30, which are the most that share one size, and the
     * one look-ahead read to learn that size has ended.
     */
    private static final int LARGEST_SIZE_AND_ONE_LOOK_AHEAD = 4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Content identity resolves one size at a time")
    @DisplayName("Over four sizes, each duplicate names its representative and only the files that share a size are hashed")
    void resolvesEverySizeAsItAlwaysDid(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("c1.txt", "c".repeat(30), LATER);
        corpus.occurrence("a1.txt", "a".repeat(20), LATER);
        corpus.occurrence("b1.txt", "b".repeat(11), EARLIER);
        corpus.occurrence("d.txt", "z".repeat(5), EARLIER);
        corpus.occurrence("a2.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c2.txt", "c".repeat(30), EARLIER);
        corpus.occurrence("b2.txt", "e".repeat(11), EARLIER);
        corpus.occurrence("a3.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c3.txt", "d".repeat(30), EARLIER);
        RunId run = corpus.run();

        corpus.resolution(corpus.ledger).resolve(run, root, new Recording(null));

        claim(
                "a1 and a3 name a2, which is as early as a3 and sorts first; c1 names c2, which is earlier; the"
                        + " files of size 11 and the third of size 30 share a size and not their bytes, so they"
                        + " are superseded by nothing; read back by name, these three verdicts are all there are",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT o.path || ' <- ' || v.reason FROM verdict v"
                                        + " JOIN file_occurrence o ON o.id = v.occurrence_id"
                                        + " WHERE v.kind = 'SUPERSEDED_BY' ORDER BY o.path",
                                String.class))
                        .containsExactly(
                                "a1.txt <- superseded by the representative at a2.txt",
                                "a3.txt <- superseded by the representative at a2.txt",
                                "c1.txt <- superseded by the representative at c2.txt"));
        claim(
                "the eight files that share a size are each hashed once, and the one of size 5, whose size is"
                        + " its own, is never hashed; read back by name, these eight hashes are all there are",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT o.path FROM content_hash h JOIN file_occurrence o ON o.id = h.occurrence_id"
                                        + " ORDER BY o.path",
                                String.class))
                        .containsExactly(
                                "a1.txt", "a2.txt", "a3.txt", "b1.txt", "b2.txt", "c1.txt", "c2.txt", "c3.txt"));
        claim(
                "each superseded file is recorded as superseded by its representative, and no other file is",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT o.path || ' -> ' || r.path FROM superseded_by s"
                                        + " JOIN file_occurrence o ON o.id = s.occurrence_id"
                                        + " JOIN file_occurrence r ON r.id = s.representative_occurrence_id"
                                        + " ORDER BY o.path",
                                String.class))
                        .containsExactly("a1.txt -> a2.txt", "a3.txt -> a2.txt", "c1.txt -> c2.txt"));
    }

    @Test
    @Story("Content identity resolves one size at a time")
    @DisplayName("Every size is announced and read before the first hash, and the hashing then goes size by size")
    void reportsInTheOrderTheContractPins(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("c1.txt", "c".repeat(30), LATER);
        corpus.occurrence("a1.txt", "a".repeat(20), LATER);
        corpus.occurrence("b1.txt", "b".repeat(11), EARLIER);
        corpus.occurrence("d.txt", "z".repeat(5), EARLIER);
        corpus.occurrence("a2.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c2.txt", "c".repeat(30), EARLIER);
        corpus.occurrence("b2.txt", "e".repeat(11), EARLIER);
        corpus.occurrence("a3.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c3.txt", "d".repeat(30), EARLIER);
        Recording progress = new Recording(null);

        corpus.resolution(corpus.ledger).resolve(corpus.run(), root, progress);

        List<String> expected = new ArrayList<>();
        expected.add("to-size " + SURVIVORS);
        for (int i = 0; i < SURVIVORS; i++) {
            expected.add("sized");
        }
        expected.add("hash-total 8");
        expected.addAll(List.of("hashed", "hashed"));
        expected.addAll(List.of("hashed", "hashed", "hashed", "superseded", "superseded"));
        expected.addAll(List.of("hashed", "hashed", "hashed", "superseded"));
        claim(
                "the survivor total comes once, then one report for each of the nine, then the hash total, 8 being"
                        + " the files that share a size, then the sizes in turn: 11 hashes two and supersedes none,"
                        + " 20 hashes three and supersedes two, 30 hashes three and supersedes one",
                () -> assertThat(progress.events).containsExactlyElementsOf(expected));
    }

    @Test
    @Story("Content identity resolves one size at a time")
    @DisplayName("When the first file is hashed, no more than its size and one look-ahead have been read")
    void holdsOneSizeWhileHashing(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("c1.txt", "c".repeat(30), LATER);
        corpus.occurrence("a1.txt", "a".repeat(20), LATER);
        corpus.occurrence("b1.txt", "b".repeat(11), EARLIER);
        corpus.occurrence("d.txt", "z".repeat(5), EARLIER);
        corpus.occurrence("a2.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c2.txt", "c".repeat(30), EARLIER);
        corpus.occurrence("b2.txt", "e".repeat(11), EARLIER);
        corpus.occurrence("a3.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c3.txt", "d".repeat(30), EARLIER);
        SizeOrderLedger counting = new SizeOrderLedger(jdbcTemplate);
        Recording progress = new Recording(counting);

        corpus.resolution(counting).resolve(corpus.run(), root, progress);

        claim(
                "a read of the survivors by size is open while the first file is hashed",
                () -> assertThat(progress.openAtFirstHash).isTrue());
        claim(
                "and it has handed out the lone file of size 5, the two of size 11 and the one that showed that size had ended,"
                        + " " + FIRST_SIZE_AND_ONE_LOOK_AHEAD + " of the " + SURVIVORS + " survivors, not the whole set",
                () -> assertThat(progress.handedOutAtFirstHash).isEqualTo(FIRST_SIZE_AND_ONE_LOOK_AHEAD));
    }

    /**
     * The read that sizes, which comes before the one that hashes and which {@link #holdsOneSizeWhileHashing}
     * cannot see: were it to read every survivor before reporting the first, eight would be waiting at the
     * first report, and this fails.
     */
    @Test
    @Story("Content identity resolves one size at a time")
    @DisplayName("Each time a file's size is reported, no more files are waiting to be reported than the most that share one size, and one")
    void holdsOneSizeWhileSizing(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("c1.txt", "c".repeat(30), LATER);
        corpus.occurrence("a1.txt", "a".repeat(20), LATER);
        corpus.occurrence("b1.txt", "b".repeat(11), EARLIER);
        corpus.occurrence("d.txt", "z".repeat(5), EARLIER);
        corpus.occurrence("a2.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c2.txt", "c".repeat(30), EARLIER);
        corpus.occurrence("b2.txt", "e".repeat(11), EARLIER);
        corpus.occurrence("a3.txt", "a".repeat(20), EARLIER);
        corpus.occurrence("c3.txt", "d".repeat(30), EARLIER);
        SizeOrderLedger counting = new SizeOrderLedger(jdbcTemplate);
        Recording progress = new Recording(counting);

        corpus.resolution(counting).resolve(corpus.run(), root, progress);

        claim(
                "each of the " + SURVIVORS + " survivors had its size reported, so the read went over all of them",
                () -> assertThat(progress.handedOutAheadOfSizing).hasSize(SURVIVORS));
        claim(
                "a read of the survivors by size was open every time a size was reported",
                () -> assertThat(progress.openAtEverySizing).isTrue());
        claim(
                "and every time, the survivors handed out and not yet reported were no more than "
                        + LARGEST_SIZE_AND_ONE_LOOK_AHEAD
                        + ": the three that share the commonest size and the one that showed that size had ended,"
                        + " never the " + (SURVIVORS - 1) + " that would be waiting had the whole set been read first",
                () -> assertThat(progress.handedOutAheadOfSizing)
                        .allSatisfy(ahead -> assertThat(ahead).isLessThanOrEqualTo(LARGEST_SIZE_AND_ONE_LOOK_AHEAD)));
    }

    @Test
    @Story("The first pass holds no survivor set")
    @DisplayName("The first pass has been handed no more survivors than it has checked, plus the one in hand")
    void theFirstPassStreams(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        for (int i = 0; i < SURVIVORS; i++) {
            corpus.occurrence("f" + i + ".txt", "text number " + i, EARLIER);
        }
        SurvivorsCountingLedger counting = new SurvivorsCountingLedger(jdbcTemplate);
        List<Integer> heldAheadOfChecking = new ArrayList<>();
        int[] checked = {0};
        CheckingProgress progress = new CheckingProgress() {
            @Override
            public void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome) {
                checked[0]++;
                heldAheadOfChecking.add(counting.handedOut - checked[0]);
            }

            @Override
            public void timestampsUnreadable(OccurrenceId occurrence, Exception cause) {}
        };
        TextSizeLimits unlimited = new TextSizeLimits(Integer.MAX_VALUE, Long.MAX_VALUE, (file, subtype, size) -> true);

        new BrokenOrOutOfScope(counting, new DetectedFormats(jdbcTemplate), unlimited)
                .verdictSurvivors(corpus.run(), root, null, progress);

        claim(
                "each of the " + SURVIVORS + " survivors was checked, so the pass ran over all of them",
                () -> assertThat(heldAheadOfChecking).hasSize(SURVIVORS));
        claim(
                "and every time one was checked, the reader had handed out no survivor the pass was not yet at, so"
                        + " the set was never drained into a list first",
                () -> assertThat(heldAheadOfChecking).allMatch(ahead -> ahead <= 0));
    }

    /** The size-ordered reader, counting what it has handed out since it was opened. */
    private static final class SizeOrderLedger extends Ledger {
        boolean open;
        int handedOut;

        SizeOrderLedger(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public ItemStreamReader<SizedOccurrence> survivorsBySize(RunId runId) {
            ItemStreamReader<SizedOccurrence> real = super.survivorsBySize(runId);
            return new ItemStreamReader<>() {
                @Override
                public void open(ExecutionContext executionContext) {
                    real.open(executionContext);
                    open = true;
                    handedOut = 0;
                }

                @Override
                public SizedOccurrence read() throws Exception {
                    SizedOccurrence next = real.read();
                    if (next != null) {
                        handedOut++;
                    }
                    return next;
                }

                @Override
                public void close() {
                    open = false;
                    real.close();
                }
            };
        }
    }

    /** The id-ordered reader, counting what it has handed out since it was opened. */
    private static final class SurvivorsCountingLedger extends Ledger {
        int handedOut;

        SurvivorsCountingLedger(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public ItemStreamReader<OccurrenceId> survivors(RunId runId) {
            ItemStreamReader<OccurrenceId> real = super.survivors(runId);
            return new ItemStreamReader<>() {
                @Override
                public void open(ExecutionContext executionContext) {
                    real.open(executionContext);
                    handedOut = 0;
                }

                @Override
                public OccurrenceId read() throws Exception {
                    OccurrenceId next = real.read();
                    if (next != null) {
                        handedOut++;
                    }
                    return next;
                }

                @Override
                public void close() {
                    real.close();
                }
            };
        }
    }

    /**
     * Every call the pass made, in order, as text; and what a size-ordered reader had done at the first hash,
     * and at each size reported before it.
     */
    private static final class Recording implements HashingProgress {
        final List<String> events = new ArrayList<>();
        private final SizeOrderLedger counting;
        boolean openAtFirstHash;
        int handedOutAtFirstHash = -1;

        /** At each {@code sized()}, the survivors the reader had handed out less those reported, this one included. */
        final List<Integer> handedOutAheadOfSizing = new ArrayList<>();

        boolean openAtEverySizing = true;
        private int sized;

        Recording(SizeOrderLedger counting) {
            this.counting = counting;
        }

        @Override
        public void toSize(long survivors) {
            events.add("to-size " + survivors);
        }

        @Override
        public void sized() {
            sized++;
            if (counting != null) {
                openAtEverySizing &= counting.open;
                handedOutAheadOfSizing.add(counting.handedOut - sized);
            }
            events.add("sized");
        }

        @Override
        public void toHash(long occurrences) {
            events.add("hash-total " + occurrences);
        }

        @Override
        public void hashed(OccurrenceId occurrence, String sha256) {
            if (counting != null && handedOutAtFirstHash < 0) {
                openAtFirstHash = counting.open;
                handedOutAtFirstHash = counting.handedOut;
            }
            events.add("hashed");
        }

        @Override
        public void supersededRecorded() {
            events.add("superseded");
        }
    }

    /** A walk recorded by hand, each file written under {@code root} with the creation time chosen. */
    private final class Corpus {
        private final Path root;
        private final Ledger ledger = new Ledger(jdbcTemplate);
        private final WalkId walk;

        Corpus(Path root) {
            this.root = root;
            this.walk = ledger.startWalk(root);
        }

        void occurrence(String name, String content, Instant created) throws IOException {
            Path file = root.resolve(name);
            Files.writeString(file, content);
            ledger.fileOccurrence(walk, new OccurrencePath(name), Files.size(file), created, created);
        }

        RunId run() {
            return ledger.startRun("byte-level-reduction", "corpus-under-test", "{}", walk, List.of());
        }

        ContentIdentityResolution resolution(Ledger through) {
            return new ContentIdentityResolution(through, new ContentIdentity(jdbcTemplate));
        }
    }
}
