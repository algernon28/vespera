package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.ProfileValue;
import io.algernon.vespera.engine.synthesis.Clusters;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6a, end to end (ADR-105, ADR-106, ADR-110, ADR-112, #175): once every survivor carries a
 * score, a winning seed and a cluster, the clusters themselves are given a name, a size and a place.
 *
 * <p>A sibling of {@link ClusteringInvocationTest} for the reason that class is a sibling of the
 * scoring one: this step reads what the step before it wrote rather than re-deriving it.
 *
 * <p><b>What this step must be shown not to do matters as much as what it does.</b> It restates no
 * membership — which documents are in a cluster stays exactly where the previous step put it — and it
 * writes no judgement against any document, because it takes nothing out of the archive.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("175")
@Link(name = "ADR-105", url = Adr.STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
class ArrangementInvocationTest {

    /** How many corpus documents this fixture walks, all of which survive to be arranged. */
    private static final int CORPUS_DOCUMENTS = 3;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clusters clusters;

    @Test
    @Story("Every group of documents is given a name and a place")
    @DisplayName("Every group the previous step formed is recorded with a name, a size and a place")
    void recordsEveryClusterWithANameASizeAndAPlace(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        List<RecordedCluster> arranged = WholeRun.clusters(jdbcTemplate,theArrangementRun());
        claim(
                "every group the previous step formed has a row of its own, which is the level that did"
                        + " not exist before it, and each arrives named, counted and placed, so whatever"
                        + " renders the arrangement never works any of that out a second time -- the"
                        + " arrangement a person approves and"
                        + " the arrangement a reader receives have to be the same one",
                () -> assertThat(arranged).allSatisfy(recorded -> {
                    assertThat(recorded.label().value()).isNotBlank();
                    assertThat(recorded.cluster().documentCount()).isPositive();
                    assertThat(recorded.cluster().partitionOrder()).isPositive();
                    assertThat(recorded.cluster().clusterOrder()).isPositive();
                }));
        claim(
                "and the documents across those groups add up to the " + CORPUS_DOCUMENTS + " that"
                        + " survived, so the arrangement covers the archive rather than part of it",
                () -> assertThat(arranged.stream()
                                .mapToInt(recorded -> recorded.cluster().documentCount())
                                .sum())
                        .isEqualTo(CORPUS_DOCUMENTS));
    }

    @Test
    @Story("Every group of documents is given a name and a place")
    @DisplayName("A group is named after its leading document's own title, not its filename")
    void namesAClusterAfterItsLeadingDocumentsTitle(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the group is named after what its leading document calls itself, which is a name a"
                        + " reviewer can check by opening that document -- the filename is the fallback for"
                        + " documents that have no title of their own, not the first choice",
                () -> assertThat(WholeRun.clusters(jdbcTemplate,theArrangementRun()))
                        .singleElement()
                        .satisfies(recorded ->
                                assertThat(recorded.label().value())
                                        .isEqualTo(SeedScriptedExtractionBeans.STUBBED_TITLE)));
    }

    @Test
    @Story("Arranging documents takes nothing out of the archive")
    @DisplayName("Arranging the documents records no judgement against any of them")
    void writesNoVerdict(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "no judgement was recorded because the documents were arranged: every kind of judgement"
                        + " this system records exists to take a document out of what gets published, and"
                        + " arranging takes nothing out of anything",
                () -> assertThat(verdictCountUnder(theArrangementRun())).isZero());
    }

    @Test
    @Story("Every group of documents is given a name and a place")
    @DisplayName("Arranging the documents restates none of what the previous step recorded")
    void restatesNoMembership(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "which documents are in which group is left exactly where the previous step put it, and"
                        + " not one row of it is written again: two places holding one truth is two places"
                        + " for it to drift apart",
                () -> assertThat(membershipRowsUnder(theArrangementRun())).isZero());
    }

    @Test
    @Story("The person who has to approve it is shown what they are approving")
    @DisplayName("The page a person reads is written beside the database, naming every group")
    void writesTheArrangementPage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        Path page = workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME);
        claim(
                "the page was written to the working directory rather than into the archive, beside the"
                        + " database and the other pages the operator was already told to look at",
                () -> assertThat(Files.exists(page)).isTrue());
        claim(
                "it names the exemplar whose documents were arranged, so what is on the page belongs to"
                        + " something a reader can find",
                () -> assertThat(Files.readString(page)).contains("seed.txt"));
        claim(
                "and it carries the short name of this arrangement, which is the value the operator"
                        + " copies: what they approve has to be the arrangement they read, and a page that"
                        + " did not say which one it was could only be approved in general",
                () -> assertThat(Files.readString(page))
                        .contains(theArrangementRun().value().substring(0, ArrangementGate.APPROVAL_LENGTH)));
        List<RecordedCluster> arranged = WholeRun.clusters(jdbcTemplate, theArrangementRun());
        claim(
                "and every group on it arrives with the documents it holds, in its row's count cell, and the "
                        + CORPUS_DOCUMENTS + " documents in all are said above the table, because a name with no"
                        + " size beside it gives a reviewer nothing to weigh",
                () -> {
                    String written = Files.readString(page);
                    assertThat(arranged).isNotEmpty().allSatisfy(recorded -> assertThat(written)
                            .contains("<td class=\"count\">" + recorded.cluster().documentCount() + "</td>"));
                    assertThat(written).contains(CORPUS_DOCUMENTS + " document(s) in " + arranged.size() + " group(s).");
                });
    }

    @Test
    @Story("The person who has to approve it is shown what they are approving")
    @DisplayName("The document a group is named after is a link the reader can open")
    void linksEachClusterToTheDocumentItWasNamedAfter(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String page = Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME));
        claim(
                "the document each name was taken from is a link into the archive where the document already"
                        + " is, so a reviewer opens it and disagrees with the name rather than taking the name on"
                        + " trust -- which is the only thing this page can be checked against -- and nothing"
                        + " copies or moves a document to be linked to",
                () -> assertThat(page)
                        .contains("<a href=\"" + Walk.canonicalRoot(root).resolve("corpus.txt").toUri() + "\""));
    }

    @Test
    @Story("The person who has to approve it is shown what they are approving")
    @DisplayName("Nothing on that page was written by a model")
    void writesNoGeneratedTextOntoThatPage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String page = Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME));
        claim(
                "the group on the page is named by its leading document's own title, \""
                        + SeedScriptedExtractionBeans.STUBBED_TITLE + "\", not by a written-up name: what is being"
                        + " approved is a derivation, and a reviewer checking written-up text would be reviewing"
                        + " the very thing this page exists to authorise",
                () -> assertThat(page).contains("<td>" + SeedScriptedExtractionBeans.STUBBED_TITLE + "</td>"));
    }

    /** The arrangement run this invocation minted — the one this test's claims are about. */
    private RunId theArrangementRun() {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid DESC LIMIT 1",
                String.class,
                "arrangement"));
    }

    private int verdictCountUnder(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ?", Integer.class, runId.value());
    }

    private int membershipRowsUnder(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id = ?", Integer.class, runId.value());
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }
}
