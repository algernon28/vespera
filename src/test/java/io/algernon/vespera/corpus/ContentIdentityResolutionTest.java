package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
 * Stage 1's second pass, moved from {@code pipeline} into {@code corpus} unchanged (ADR-188): the
 * survivors are grouped by size, hashed only within a group of two or more (ADR-067), each content
 * identity resolves to one representative occurrence (ADR-069), and every other member is recorded as
 * superseded by it and verdicted {@code superseded-by}.
 *
 * <p>Every expectation here was measured first, on 2026-10-04, by running the same files with the same
 * creation times through the stage-1 step as it stood before the move. That step hashed every member of a
 * size group and nothing of a size of its own, resolved each content identity to its earliest-created
 * member with the lowest path breaking a tie, and named the representative's path in each reason.
 *
 * <p>The file occurrences are written into the ledger by hand, so their creation times can be chosen;
 * a real walk takes them from the filesystem, where a test cannot set them precisely.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Content identity")
@Issue("406")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
@Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
class ContentIdentityResolutionTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-01T00:00:00Z");

    /** Written to three files, so they share a size and their bytes. */
    private static final String THREE_TIMES = "the same bytes, three times";

    /** How many files in the mixed corpus below share a size with another: three copies, then x and y. */
    private static final int IN_A_SIZE_GROUP = 5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Which file occurrence is left standing")
    @DisplayName("Of three identical files, the earliest created is left standing, the lower path breaking a tie, and the others name it")
    void resolvesToTheEarliestCreatedThenTheLowestPath(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        // c.txt is recorded before b.txt, so the order the survivors are read in and the order of their
        // paths disagree: only the path can be what breaks the tie.
        OccurrenceId a = corpus.occurrence("a.txt", THREE_TIMES, LATER);
        OccurrenceId c = corpus.occurrence("c.txt", THREE_TIMES, EARLIER);
        OccurrenceId b = corpus.occurrence("b.txt", THREE_TIMES, EARLIER);
        RunId run = corpus.run();

        corpus.resolution().resolve(run, root, new RecordedHashing());

        claim(
                "b.txt is left standing: it was created earlier than a.txt, and at the same time as c.txt, whose"
                        + " path sorts after it; it carries no verdict",
                () -> assertThat(verdictsOf(b)).isEmpty());
        claim(
                "a.txt and c.txt are each verdicted superseded-by, with a reason naming b.txt's path",
                () -> {
                    assertThat(verdictsOf(a)).containsExactly("SUPERSEDED_BY: superseded by the representative at b.txt");
                    assertThat(verdictsOf(c)).containsExactly("SUPERSEDED_BY: superseded by the representative at b.txt");
                });
        claim(
                "and each is recorded as superseded by b.txt, while b.txt is recorded as superseded by nothing",
                () -> {
                    ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
                    assertThat(contentIdentity.representativeFor(a, run)).contains(b);
                    assertThat(contentIdentity.representativeFor(c, run)).contains(b);
                    assertThat(contentIdentity.representativeFor(b, run)).isEmpty();
                });
    }

    @Test
    @Story("Which file occurrences are hashed")
    @DisplayName("A file sharing a size with another is hashed; a file of a size of its own is never hashed")
    void hashesOnlyWithinAGroupSharingASize(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        OccurrenceId x = corpus.occurrence("x.txt", "one size, content x", EARLIER);
        OccurrenceId y = corpus.occurrence("y.txt", "one size, content y", EARLIER);
        OccurrenceId lonely = corpus.occurrence("lonely.txt", "a size no other file has, at all", EARLIER);
        RunId run = corpus.run();

        corpus.resolution().resolve(run, root, new RecordedHashing());

        ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
        claim(
                "the two files of one size are each hashed, and the hash recorded is the file's SHA-256",
                () -> {
                    assertThat(contentIdentity.hashFor(x, run)).contains(ContentHash.sha256(root.resolve("x.txt")));
                    assertThat(contentIdentity.hashFor(y, run)).contains(ContentHash.sha256(root.resolve("y.txt")));
                });
        claim(
                "but sharing a size is not sharing content, so neither carries a verdict",
                () -> {
                    assertThat(verdictsOf(x)).isEmpty();
                    assertThat(verdictsOf(y)).isEmpty();
                });
        claim(
                "the file whose size no other file has is never hashed, and carries no verdict",
                () -> {
                    assertThat(contentIdentity.hashFor(lonely, run)).isEmpty();
                    assertThat(verdictsOf(lonely)).isEmpty();
                });
    }

    @Test
    @Story("Which file occurrence is left standing")
    @DisplayName("Two contents that share a size each leave their own file standing")
    void twoContentsOfOneSizeResolveApart(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        OccurrenceId p1 = corpus.occurrence("p1.txt", "AAAAAAAAAAAAAAAA", LATER);
        OccurrenceId p2 = corpus.occurrence("p2.txt", "AAAAAAAAAAAAAAAA", EARLIER);
        OccurrenceId q1 = corpus.occurrence("q1.txt", "BBBBBBBBBBBBBBBB", EARLIER);
        OccurrenceId q2 = corpus.occurrence("q2.txt", "BBBBBBBBBBBBBBBB", LATER);
        RunId run = corpus.run();

        corpus.resolution().resolve(run, root, new RecordedHashing());

        claim(
                "the earlier of each pair is left standing: p2.txt and q1.txt carry no verdict",
                () -> {
                    assertThat(verdictsOf(p2)).isEmpty();
                    assertThat(verdictsOf(q1)).isEmpty();
                });
        claim(
                "and each later one names its own pair's file, not the other pair's",
                () -> {
                    assertThat(verdictsOf(p1))
                            .containsExactly("SUPERSEDED_BY: superseded by the representative at p2.txt");
                    assertThat(verdictsOf(q2))
                            .containsExactly("SUPERSEDED_BY: superseded by the representative at q1.txt");
                });
    }

    @Test
    @Story("Which file occurrences are hashed")
    @DisplayName("A file already removed under the run takes no part, so its identical copy is left alone")
    void aRemovedFileTakesNoPart(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        OccurrenceId removed = corpus.occurrence("copy-1.txt", "identical copies", EARLIER);
        OccurrenceId copy = corpus.occurrence("copy-2.txt", "identical copies", LATER);
        RunId run = corpus.run();
        corpus.ledger.verdict(removed, run, VerdictKind.BROKEN, "removed earlier in the same stage, by this test");

        corpus.resolution().resolve(run, root, new RecordedHashing());

        ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
        claim(
                "the removed file is not hashed: only survivors are read",
                () -> assertThat(contentIdentity.hashFor(removed, run)).isEmpty());
        claim(
                "so its copy is the only survivor of its size, is never hashed, and carries no verdict",
                () -> {
                    assertThat(contentIdentity.hashFor(copy, run)).isEmpty();
                    assertThat(verdictsOf(copy)).isEmpty();
                });
    }

    @Test
    @Story("What the second pass reports as it goes")
    @DisplayName("The count to hash is given once, before any hash, and leaves out files of a size of their own")
    void reportsHowManyItWillHashThenEachHash(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("a.txt", THREE_TIMES, LATER);
        corpus.occurrence("b.txt", THREE_TIMES, EARLIER);
        corpus.occurrence("c.txt", THREE_TIMES, EARLIER);
        corpus.occurrence("x.txt", "one size, content x", EARLIER);
        corpus.occurrence("y.txt", "one size, content y", EARLIER);
        corpus.occurrence("lonely.txt", "a size no other file has, at all", EARLIER);
        RunId run = corpus.run();
        RecordedHashing progress = new RecordedHashing();

        corpus.resolution().resolve(run, root, progress);

        ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
        claim(
                "the first thing reported is the count: " + IN_A_SIZE_GROUP + ", the three copies and the two files"
                        + " of one size, and not the sixth file, whose size is its own",
                () -> assertThat(progress.events.getFirst()).isEqualTo("to hash " + IN_A_SIZE_GROUP));
        claim(
                "it is reported once only",
                () -> assertThat(progress.events).filteredOn(event -> event.startsWith("to hash")).hasSize(1));
        claim(
                "then each of the " + IN_A_SIZE_GROUP + " is reported once, with the hash recorded for it",
                () -> assertThat(progress.hashed)
                        .hasSize(IN_A_SIZE_GROUP)
                        .allSatisfy(hashed -> assertThat(contentIdentity.hashFor(hashed.occurrence(), run))
                                .contains(hashed.sha256())));
    }

    @Test
    @Story("What the second pass reports as it goes")
    @DisplayName("With no two files of one size, a count of zero is reported and nothing is hashed")
    void reportsZeroWhenNothingSharesASize(@TempDir Path root) throws Exception {
        Corpus corpus = new Corpus(root);
        corpus.occurrence("short.txt", "short", EARLIER);
        corpus.occurrence("longer.txt", "a little longer", EARLIER);
        RecordedHashing progress = new RecordedHashing();

        corpus.resolution().resolve(corpus.run(), root, progress);

        claim(
                "the one thing reported is a count of zero",
                () -> assertThat(progress.events).containsExactly("to hash 0"));
        claim(
                "and nothing is recorded as hashed",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class))
                        .isZero());
    }

    /** The verdicts on {@code occurrence}, as kind and reason. */
    private List<String> verdictsOf(OccurrenceId occurrence) {
        return jdbcTemplate.query(
                "SELECT kind, reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind") + ": " + resultSet.getString("reason"),
                occurrence.value());
    }

    /** A walk recorded by hand, each file written under {@code root} with the creation time chosen. */
    private final class Corpus {
        private final Path root;
        final Ledger ledger = new Ledger(jdbcTemplate);
        private final WalkId walk;

        Corpus(Path root) {
            this.root = root;
            this.walk = ledger.startWalk(root);
        }

        OccurrenceId occurrence(String name, String content, Instant created) throws IOException {
            Path file = root.resolve(name);
            Files.writeString(file, content);
            ledger.fileOccurrence(walk, new OccurrencePath(name), Files.size(file), created, created);
            return ledger.occurrenceId(walk, new OccurrencePath(name)).orElseThrow();
        }

        RunId run() {
            return ledger.startRun("byte-level-reduction", "corpus-under-test", "{}", walk, List.of());
        }

        ContentIdentityResolution resolution() {
            return new ContentIdentityResolution(ledger, new ContentIdentity(jdbcTemplate));
        }
    }

    /** One hash reported. */
    private record Hashed(OccurrenceId occurrence, String sha256) {}

    /** Every call the pass made, in order. */
    private static final class RecordedHashing implements HashingProgress {
        final List<String> events = new ArrayList<>();
        final List<Hashed> hashed = new ArrayList<>();

        @Override
        public void toHash(long occurrences) {
            events.add("to hash " + occurrences);
        }

        @Override
        public void hashed(OccurrenceId occurrence, String sha256) {
            events.add("hashed " + occurrence.value());
            hashed.add(new Hashed(occurrence, sha256));
        }
    }
}
