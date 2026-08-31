package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The walk's own logic, against a corpus built in memory (ADR-065).
 *
 * <p>What separates these from {@link WalkTest} is what the test is evidence for. These ask whether
 * the walk counts, skips and resumes correctly over a tree of a given shape, and the shape is all
 * that matters — so the filesystem is a fixture. {@link WalkTest} asks how the walk behaves against
 * NTFS, where the platform is the subject and only the platform is evidence.
 *
 * <p>Two things become possible here and nowhere else. A directory the walk may not read is a
 * {@code chmod} rather than an ACL that half the environments running this suite will refuse to
 * apply, so the unprocessable anomaly is tested rather than skipped. And the checkpoint arithmetic
 * gets a tree large enough to break it: ordinals go wrong on depth and width, and a fixture of four
 * entries exercises neither.
 */
@Epic("Census")
@Feature("Walk")
@Link(name = "ADR-065", url = Adr.WALK_ALGORITHM_ON_AN_IN_MEMORY_FILESYSTEM, type = "adr")
class WalkAlgorithmTest {

    /** Directories at the top of the resume fixture, each holding files and a subdirectory. */
    private static final int BRANCHES = 6;

    /** Files directly inside each branch. */
    private static final int FILES_PER_BRANCH = 4;

    /** Files inside each branch's subdirectory. */
    private static final int FILES_PER_SUBDIRECTORY = 3;

    /** Files at the root of the resume fixture. */
    private static final int FILES_AT_THE_ROOT = 3;

    /** Every file the resume fixture holds: 6 branches of 4 + 3, plus 3 at the root. */
    private static final int FILES_IN_THE_FIXTURE =
            BRANCHES * (FILES_PER_BRANCH + FILES_PER_SUBDIRECTORY) + FILES_AT_THE_ROOT;

    /** Directories the fixture holds, all entered: the root, each branch, and each subdirectory. */
    private static final int DIRECTORIES_IN_THE_FIXTURE = 1 + BRANCHES + BRANCHES;

    /** A flat tree big enough that walking it is a measurement rather than a formality. */
    private static final int DIRECTORIES_AT_SCALE = 40;

    private static final int FILES_PER_DIRECTORY_AT_SCALE = 25;

    /** Collects what the walk emits, and what it had counted at each point it offered to stop. */
    private static final class Recorder implements Walk.Observer {

        final List<String> occurrences = new ArrayList<>();
        final List<WalkAnomalyKind> anomalyKinds = new ArrayList<>();
        final List<String> anomalies = new ArrayList<>();
        final List<Offer> offers = new ArrayList<>();

        @Override
        public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {
            occurrences.add(path.value());
        }

        @Override
        public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
            anomalyKinds.add(kind);
            anomalies.add(pathRendering + " :: " + detail);
        }

        @Override
        public void checkpoint(Checkpoint at, Walk.Progress progress) {
            offers.add(new Offer(at, progress));
        }
    }

    /** One point the walk offered to be resumed from, and what it had counted by then. */
    private record Offer(Checkpoint at, Walk.Progress progress) {}

    private static InMemoryCorpus resumeFixture(String name) throws IOException {
        InMemoryCorpus corpus = InMemoryCorpus.open(name);
        for (int branch = 0; branch < BRANCHES; branch++) {
            for (int file = 0; file < FILES_PER_BRANCH; file++) {
                corpus.file("branch%02d/file%02d.txt".formatted(branch, file), "x");
            }
            for (int file = 0; file < FILES_PER_SUBDIRECTORY; file++) {
                corpus.file("branch%02d/deeper/file%02d.txt".formatted(branch, file), "x");
            }
        }
        for (int file = 0; file < FILES_AT_THE_ROOT; file++) {
            corpus.file("root%02d.txt".formatted(file), "x");
        }
        return corpus;
    }

    @Test
    @Story("Entries that become walk anomalies")
    @DisplayName("A directory that may not be read is recorded, and the walk carries on past it")
    @Issue("6")
    @Link(name = "ADR-053", url = Adr.WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS, type = "adr")
    void recordsADirectoryItMayNotReadAndCarriesOn() throws IOException {
        try (InMemoryCorpus corpus = InMemoryCorpus.open("denied")) {
            corpus.file("readable.txt", "reached after the denied directory");
            corpus.file("denied/unreachable.txt", "behind the denial");
            corpus.denyAccessTo("denied");

            Recorder recorder = new Recorder();
            Walk.Outcome outcome = Walk.walk(corpus.root(), recorder);

            claim(
                    "the file beside the denied directory was still recorded, so one unreadable entry did"
                            + " not end the traversal",
                    () -> assertThat(recorder.occurrences).containsExactly("readable.txt"));
            claim(
                    "the denied directory was recorded rather than dropped: one anomaly, kinded as"
                            + " unprocessable",
                    () -> assertThat(recorder.anomalyKinds).containsExactly(WalkAnomalyKind.UNPROCESSABLE));
            claim(
                    "the file behind the denied directory was never reached",
                    () -> assertThat(recorder.occurrences).doesNotContain("denied/unreachable.txt"));
            claim(
                    "every entry met is still accounted for, the denied directory counting as an anomaly"
                            + " rather than as a directory entered",
                    () -> assertThat(outcome.progress().accountsForEveryEntry()).isTrue());
        }
    }

    /**
     * The strongest claim available about resuming, and the reason this tier exists.
     *
     * <p>The walk offers a checkpoint at every completed directory. Resuming from any one of them
     * must record exactly the occurrences the first session had not yet reported at that point —
     * none of them twice, none of them lost — and the two sessions' counts must add up to what one
     * uninterrupted walk counted, because that sum is what the excludes-nothing reconciliation
     * checks. Asserting it at every offer walks the whole done/ancestor/pending classification
     * across every position in the tree, which is where ordinal arithmetic goes wrong.
     */
    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("Resuming from any point records exactly what was left, and the counts still add up")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void resumingFromAnyCheckpointRecordsExactlyWhatWasLeft() throws IOException {
        try (InMemoryCorpus corpus = resumeFixture("every-checkpoint")) {
            Recorder uninterrupted = new Recorder();
            Walk.Outcome whole = Walk.walk(corpus.root(), uninterrupted);

            claim(
                    "the fixture holds " + FILES_IN_THE_FIXTURE + " files, and one uninterrupted walk"
                            + " recorded them all",
                    () -> assertThat(uninterrupted.occurrences).hasSize(FILES_IN_THE_FIXTURE));
            claim(
                    "it entered " + DIRECTORIES_IN_THE_FIXTURE + " directories: the root, "
                            + BRANCHES + " branches, and a subdirectory in each",
                    () -> assertThat(whole.progress().directoriesEntered()).isEqualTo(DIRECTORIES_IN_THE_FIXTURE));
            claim(
                    "it offered a checkpoint at every directory it completed except the root",
                    () -> assertThat(uninterrupted.offers).hasSize(DIRECTORIES_IN_THE_FIXTURE - 1));

            for (Offer offer : uninterrupted.offers) {
                Recorder resumed = new Recorder();
                Walk.Outcome after = Walk.walk(corpus.root(), resumed, Optional.of(offer.at()));

                List<String> notYetReported = uninterrupted.occurrences.subList(
                        (int) offer.progress().occurrences(), FILES_IN_THE_FIXTURE);

                claim(
                        "resuming from " + offer.at() + " records exactly the "
                                + notYetReported.size() + " occurrences the stopped session had not"
                                + " reported: none repeated, none lost, and in the same order",
                        () -> assertThat(resumed.occurrences).containsExactlyElementsOf(notYetReported));
                claim(
                        "the entries the two sessions saw add up to what one uninterrupted walk saw,"
                                + " which is the sum the reconciliation checks after resuming from " + offer.at(),
                        () -> assertThat(offer.progress().entriesSeen() + after.progress().entriesSeen())
                                .isEqualTo(whole.progress().entriesSeen()));
                claim(
                        "so do the directories entered, the root counted once across both sessions rather"
                                + " than once each, resuming from " + offer.at(),
                        () -> assertThat(offer.progress().directoriesEntered()
                                        + after.progress().directoriesEntered())
                                .isEqualTo(whole.progress().directoriesEntered()));
            }
        }
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("Resuming holds over a thousand entries, not only over a handful")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void resumingHoldsAtScale() throws IOException {
        int files = DIRECTORIES_AT_SCALE * FILES_PER_DIRECTORY_AT_SCALE;
        try (InMemoryCorpus corpus = InMemoryCorpus.open("at-scale")) {
            for (int directory = 0; directory < DIRECTORIES_AT_SCALE; directory++) {
                for (int file = 0; file < FILES_PER_DIRECTORY_AT_SCALE; file++) {
                    corpus.file("dir%02d/file%02d.txt".formatted(directory, file), "x");
                }
            }

            Recorder uninterrupted = new Recorder();
            Walk.Outcome whole = Walk.walk(corpus.root(), uninterrupted);
            Offer halfway = uninterrupted.offers.get(uninterrupted.offers.size() / 2);

            Recorder resumed = new Recorder();
            Walk.Outcome after = Walk.walk(corpus.root(), resumed, Optional.of(halfway.at()));

            claim(
                    files + " files across " + DIRECTORIES_AT_SCALE + " directories were all recorded by"
                            + " one uninterrupted walk",
                    () -> assertThat(uninterrupted.occurrences).hasSize(files));
            claim(
                    "resuming halfway recorded exactly the occurrences that remained, in order",
                    () -> assertThat(resumed.occurrences)
                            .containsExactlyElementsOf(uninterrupted.occurrences.subList(
                                    (int) halfway.progress().occurrences(), files)));
            claim(
                    "and the two sessions still account for every entry the whole walk met",
                    () -> assertThat(halfway.progress().entriesSeen() + after.progress().entriesSeen())
                            .isEqualTo(whole.progress().entriesSeen()));
        }
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A checkpoint that no longer matches the tree stops the walk instead of skipping on")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void refusesToResumeAgainstATreeThatChanged() throws IOException {
        try (InMemoryCorpus corpus = InMemoryCorpus.open("changed-tree")) {
            corpus.file("one/a.txt", "a");
            Checkpoint pointingElsewhere = new Checkpoint(List.of(0), "somewhere-else");

            claim(
                    "a checkpoint naming a directory the tree does not hold at that position stops the"
                            + " walk, saying the tree changed",
                    () -> assertThatThrownBy(
                                    () -> Walk.walk(corpus.root(), new Recorder(), Optional.of(pointingElsewhere)))
                            .isInstanceOf(CheckpointMismatch.class)
                            .hasMessageContaining("changed"));
        }
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A checkpoint pointing at what is now a file stops the walk rather than skipping on")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void refusesToResumeWhenTheCheckpointedDirectoryIsNowAFile() throws IOException {
        try (InMemoryCorpus corpus = InMemoryCorpus.open("now-a-file")) {
            corpus.file("one", "what was a directory when the checkpoint was written");
            Checkpoint pointingAtWhatIsNowAFile = new Checkpoint(List.of(0), "one");

            claim(
                    "the walk stops rather than carrying on past a position it never found, so a tree that"
                            + " changed cannot quietly cost the corpus a subtree",
                    () -> assertThatThrownBy(() -> Walk.walk(
                                    corpus.root(), new Recorder(), Optional.of(pointingAtWhatIsNowAFile)))
                            .isInstanceOf(CheckpointMismatch.class)
                            .hasMessageContaining("no longer holds"));
        }
    }
}
