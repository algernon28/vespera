package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree stage 6b writes one seed partition at a time (ADR-223 sections 6 and 7) is byte for byte the tree
 * it wrote from lists of every cluster, every synthesis doc and every survivor: the index, the manifest and
 * every cluster file.
 *
 * <p><b>The expected files are captured, not composed here.</b> {@link TheTreeWrittenFromLists} holds every
 * file {@code Deliverable.writeTo} wrote from {@link ThreePartitions}'s lists at {@code 5b0cc20}, the commit
 * before ADR-223 was built. The forms of {@code writeTo} that took those lists are gone with this record, so
 * nothing in the tree can write it that way any more.
 *
 * <p><b>What the three partitions put to the test.</b> Occurrence order cuts across the partitions, which the
 * manifest keeps and a partition's loop does not have. Two partitions hold as many survivors as one another.
 * Two survivors of a cluster score alike, in three clusters, where the page keeps the order they arrive in.
 * One cluster was written from two of its three survivors, neither the highest-scoring and not in occurrence
 * order, so its page is numbered from what its call was recorded as sending. And of the four clusters with no
 * writing, one carries a fault, nothing could be sent for one, one was not reached and one has no reason
 * given, each saying so in its own sentence.
 *
 * <p><b>The directories are laid out as they were captured</b>: the working directory is {@code work} under
 * the temporary directory and the corpus root {@code archive} beside it, so every link from a page to an
 * original is the same relative path. The corpus root's own text, which {@code index.md} states, is this
 * machine's, and is put where the captured files carry a token for it.
 *
 * <p>Pure: no database. The survivors are handed over two at a time, so a page of them never falls on a
 * partition's edge by design.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class TreeIsTheSameWrittenAPartitionAtATimeTest {

    /** How many survivors the source hands over at once: fewer than any partition here holds. */
    private static final int TWO_SURVIVORS_IN_A_PAGE = 2;

    @Test
    @Story("The tree is the same tree, written one exemplar at a time")
    @DisplayName("Every file of a tree of three exemplars is byte for byte the file written when everything was held at once")
    void everyFileIsTheSame(@TempDir Path base) throws IOException {
        String corpusRoot = base.resolve("archive").toString();

        Path tree = Deliverable.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(corpusRoot),
                ListedArrangement.of(
                        ThreePartitions.arrangement(),
                        ThreePartitions.written(),
                        ThreePartitions.survivors(),
                        ThreePartitions.unwritten(),
                        TWO_SURVIVORS_IN_A_PAGE),
                SurvivorPictures.none(),
                DeliverableProgress.NONE);

        Map<String, String> written = filesOf(tree);
        Map<String, String> expected = new TreeMap<>();
        TheTreeWrittenFromLists.files()
                .forEach((path, content) ->
                        expected.put(path, content.replace(ThreePartitions.CORPUS_ROOT_TOKEN, corpusRoot)));

        claim(
                "the tree holds the same eight files and nothing else: the index, the listing and a page for each"
                        + " of the six groups under its exemplar's directory, with no file left half written"
                        + " beside any of them",
                () -> assertThat(written.keySet()).containsExactlyElementsOf(expected.keySet()));
        for (Map.Entry<String, String> file : expected.entrySet()) {
            claim(
                    file.getKey() + " is byte for byte what it was",
                    () -> assertThat(written.get(file.getKey()))
                            .as("the contents of %s", file.getKey())
                            .isEqualTo(file.getValue()));
        }
    }

    @Test
    @Story("The tree is the same tree, written one exemplar at a time")
    @DisplayName("The listing keeps the documents in the order they were first recorded, across exemplars")
    void theManifestKeepsOccurrenceOrderAcrossPartitions(@TempDir Path base) throws IOException {
        String corpusRoot = base.resolve("archive").toString();

        Path tree = ListedArrangement.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(corpusRoot),
                ThreePartitions.arrangement(),
                ThreePartitions.written(),
                ThreePartitions.survivors(),
                SurvivorPictures.none(),
                ThreePartitions.unwritten());

        List<String> firstCells = Files.readAllLines(tree.resolve(Deliverable.MANIFEST_FILE_NAME), StandardCharsets.UTF_8)
                .stream()
                .skip(1)
                .map(row -> row.substring(0, row.indexOf(',')))
                .toList();
        claim(
                "the eleven rows run 10 to 20, a document of the first exemplar followed by one of the second and"
                        + " one of the third: the listing is not written in the order the pages are",
                () -> assertThat(firstCells)
                        .containsExactly("10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20"));
    }

    /**
     * The writer reads every survivor twice, a page at a time, and each read is asked for by what it is for
     * (ADR-223 section 6). Whoever answers the source writes a line for each, and tells the two apart by
     * which was asked for, not by which came first.
     */
    @Test
    @Story("The tree is the same tree, written one exemplar at a time")
    @DisplayName("Every document is asked for once for the pictures and once for the listing, each by its own name, the pictures first")
    void eachReadOfEverySurvivorIsAskedForByName(@TempDir Path base) {
        ArrangedSurvivors whole = ListedArrangement.of(
                ThreePartitions.arrangement(), ThreePartitions.written(), ThreePartitions.survivors(), ThreePartitions.unwritten());
        List<String> asked = new ArrayList<>();

        Deliverable.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(base.resolve("archive").toString()),
                ArrangedSurvivors.reading(
                        whole::partitions,
                        whole::survivorCount,
                        whole::clustersOf,
                        whole::survivorsOf,
                        whole::writtenOver,
                        whole::whyUnwritten,
                        whole::placeOf,
                        page -> {
                            asked.add("for their pictures");
                            whole.eachPageOfSurvivorsForTheirPictures(page);
                        },
                        page -> {
                            asked.add("for the listing");
                            whole.eachPageOfSurvivorsForTheManifest(page);
                        }),
                SurvivorPictures.none(),
                DeliverableProgress.NONE);

        claim(
                "the pass that finds which pictures recur asks for every document through the read named for"
                        + " the pictures, and the listing through the read named for it, once each and in that"
                        + " order: neither is asked for through the other's name",
                () -> assertThat(asked).containsExactly("for their pictures", "for the listing"));
    }

    /** Every file beneath {@code tree}, by its path relative to it with {@code /} between segments. */
    private static Map<String, String> filesOf(Path tree) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(tree)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(
                        tree.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
