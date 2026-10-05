package io.algernon.vespera.corpus;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The two loops of stage 1's second pass that ADR-192 counts, as {@code ContentIdentityResolution} reports
 * them through {@code HashingProgress}'s three new methods, each with a default body that does nothing:
 * {@code toSize(long)} once with the survivors drained and {@code sized()} after each size read, and {@code
 * supersededRecorded()}, with no total, after each duplicate is recorded and verdicted.
 *
 * <p><b>Part (a) of ADR-192.</b> Does not compile until those methods exist; part (a) moves it into {@code
 * src/test}. {@code ContentIdentityResolutionTest}'s own recording class implements only the two methods
 * ADR-188 gave the interface, and compiles unchanged because the new ones have defaults.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
class ContentIdentityResolutionReportsItsLoopsTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-01T00:00:00Z");
    private static final String THREE_TIMES = "the same bytes, three times";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Stage 1 tells its caller how many sizes it reads and each duplicate it records")
    @DisplayName("The sizes read are announced once before any is read, and each duplicate recorded is reported")
    void reportsSizesReadAndDuplicatesRecorded(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("a.txt", THREE_TIMES, LATER);
        corpus.occurrence("b.txt", THREE_TIMES, EARLIER);
        corpus.occurrence("c.txt", THREE_TIMES, EARLIER);
        corpus.occurrence("x.txt", "one size, content x", EARLIER);
        corpus.occurrence("y.txt", "one size, content y", EARLIER);
        corpus.occurrence("lonely.txt", "a size no other file has, at all", EARLIER);
        Recording progress = new Recording();

        corpus.resolution().resolve(corpus.run(), root, progress);

        claim(
                "the size loop is announced once, with all six survivors, before its first item, and each of the"
                        + " six is reported, the one whose size is its own included",
                () -> assertThat(progress.events.subList(0, 7))
                        .containsExactly("to-size 6", "sized", "sized", "sized", "sized", "sized", "sized"));
        claim(
                "the two files the representative supersedes are each reported once, after the hashing began,"
                        + " and nothing else is: the two files of one size and different content supersede nothing",
                () -> assertThat(progress.events.subList(7, progress.events.size()))
                        .filteredOn(event -> !event.startsWith("hash"))
                        .containsExactly("superseded", "superseded"));
    }

    @Test
    @Story("Stage 1 tells its caller how many sizes it reads and each duplicate it records")
    @DisplayName("With no survivor the size loop is announced with zero and nothing is reported")
    void reportsZeroSizesWhenThereIsNoSurvivor(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        Recording progress = new Recording();

        corpus.resolution().resolve(corpus.run(), root, progress);

        claim(
                "the size loop is reached with no item, so it is announced with zero, once; the hash loop is"
                        + " announced with zero, as it always has been; nothing else is reported",
                () -> assertThat(progress.events).containsExactly("to-size 0", "hash-total 0"));
    }

    /** Every call the pass made, in order, as text. */
    private static final class Recording implements HashingProgress {
        final List<String> events = new ArrayList<>();

        @Override
        public void toHash(long occurrences) {
            events.add("hash-total " + occurrences);
        }

        @Override
        public void hashed(OccurrenceId occurrence, String sha256) {
            events.add("hashed");
        }

        @Override
        public void toSize(long survivors) {
            events.add("to-size " + survivors);
        }

        @Override
        public void sized() {
            events.add("sized");
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

        ContentIdentityResolution resolution() {
            return new ContentIdentityResolution(ledger, new ContentIdentity(jdbcTemplate));
        }
    }
}
