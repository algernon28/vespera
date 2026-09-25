package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.ClusteringBeans;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.DocumentTitles;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Deliverable;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A survivor's pictures, carried by a whole invocation from the extraction cache to its cluster file
 * (ADR-149, #285).
 *
 * <p>{@code DeliverablePicturesTest} holds every claim about what the writer does with pictures it is
 * handed. What only an invocation can show is that it is handed them at all: that stage 2's cached
 * response for a document is found again at stage 6b through that document's own file, and that what
 * comes out of it lands on the right page, under the right entry, as a file the page links to.
 *
 * <p><b>Why a sibling class with its own converter double.</b> The double the rest of this package
 * shares answers every document with one response, so every document would carry the same pictures,
 * and the furniture rule would rightly remove all of them: a test built on it would pass on a writer
 * that never shows a picture. {@link PictureScriptedExtractionBeans} answers by file name instead, so
 * exactly one corpus document carries a picture the other does not, and both carry one picture in
 * common. Changing the shared double's default answer would have put pictures into every tree this
 * package writes.
 *
 * <p><b>One static working directory serves the whole class</b>, as in every invocation test here, so
 * the tree is found through the run recorded against this test's own walk, never by listing the
 * directory.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    GenerationJobConfiguration.class,
    GenerationTasklet.class,
    GenerationRun.class,
    GenerationModel.class,
    GenerationContextWindow.class,
    CensusTasklet.class,
    ByteLevelReductionJobConfiguration.class,
    ByteLevelReductionTasklet.class,
    ExtractionJobConfiguration.class,
    ExtractionItemProcessor.class,
    ExtractionItemWriter.class,
    ExtractionRun.class,
    ExtractionTimeoutStreak.class,
    ExtractionCircuitBreaker.class,
    ExtractionHealthCheckListener.class,
    ContentCensusJobConfiguration.class,
    ContentCensusTasklet.class,
    ContentCensusRun.class,
    RedundancyJobConfiguration.class,
    RedundancyRun.class,
    RedundancyGate.class,
    RedundancyBoilerplate.class,
    RedundancySignatureItemWriter.class,
    RedundancyResolutionTasklet.class,
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonJobConfiguration.class,
    SeedCorpusComparisonTasklet.class,
    EmbeddingModelJobConfiguration.class,
    EmbeddingScoringTasklet.class,
    RelevanceScoringJobConfiguration.class,
    RelevanceScoringTasklet.class,
    RelevanceFloorJobConfiguration.class,
    RelevanceFloorTasklet.class,
    RelevanceFloor.class,
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    ArrangementJobConfiguration.class,
    ArrangementTasklet.class,
    DocumentTitles.class,
    ArrangementRun.class,
    ArrangementGate.class,
    Clusters.class,
    SynthesisDocs.class,
    ClusterFaults.class,
    ClusterSynthesis.class,
    LeadingChunks.class,
    GenerationScriptedBeans.class,
    RelevanceDistribution.class,
    EmbeddingModelGate.class,
    SeedMeasurementRun.class,
    ScoringRun.class,
    SeedGate.class,
    UsableSeedGate.class,
    ChunkEmbedderBeans.class,
    RelevanceScoringBeans.class,
    ClusteringBeans.class,
    DocumentClusters.class,
    EmbeddingScriptedBeans.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    SeedCorpusComparison.class,
    UnusableSeeds.class,
    Shingler.class,
    HybridChunkerBeans.class,
    PictureScriptedExtractionBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    DetectedFormats.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    NextAction.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    LabelIngestion.class,
    RelevanceLabels.class,
    VesperaCli.class
})
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
class DeliverablePicturesInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** A relevance floor of zero, which removes nothing, so both corpus documents reach the tree. */
    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    /** How much every call in this class may read. */
    private static final String THE_READING_WINDOW = "4096";

    /** What one archive, read one way, is worth: one piece of work, and one tree named after it. */
    private static final int ONE_RUN = 1;

    /** The two corpus documents, both of which survive into the one group this corpus arranges. */
    private static final int TWO_DOCUMENTS = 2;

    /** How many pages the tree holds: this corpus arranges one group. */
    private static final int ONE_PAGE = 1;

    /** How many hexadecimal characters of a picture's digest name its file. */
    private static final int NAME_LENGTH = 16;

    /** An image on a page, as its alt text and its destination. */
    private static final Pattern AN_IMAGE = Pattern.compile("!\\[((?:\\\\.|[^\\]\\\\])*)\\]\\(([^)]*)\\)");

    /** Where one membership entry begins: its number and its anchor. */
    private static final Pattern AN_ENTRY = Pattern.compile("(?m)^\\d+\\. <a id=\"document-\\d+\"></a>");

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

    /**
     * Drops what an earlier class scripted, before each test as well as after it: the script is static
     * on a fixture this whole package shares, and class order is not a thing any test here decides.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("A picture one document's conversion carried reaches that document's entry as a file beside the page, and one both carried is left out")
    void carriesAPictureFromTheConversionToItsDocumentsEntryAndLeavesOutTheOneEveryDocumentCarries(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, and exactly " + ONE_RUN + " piece of work stands behind the"
                        + " tree the claims below read",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(generationRuns(root)).hasSize(ONE_RUN);
                });
        Path page = theOnePageOf(root);
        String text = Files.readString(page);
        String stem = page.getFileName().toString().replaceFirst("\\.md$", "");
        String diagramName = nameOf(PictureScriptedExtractionBeans.THE_DIAGRAM);
        String letterheadName = nameOf(PictureScriptedExtractionBeans.THE_LETTERHEAD);
        Path diagramFile = page.resolveSibling(stem).resolve(diagramName);

        claim(
                "both corpus documents reached the group's page, which the claims below depend on: a"
                        + " picture can only be left out as shared if both documents that share it are there",
                () -> assertThat(text)
                        .contains(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM)
                        .contains(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD));
        claim(
                "the diagram only one document's conversion carried is written into the tree, in a"
                        + " directory beside the group's page named after it, holding exactly the pixels the"
                        + " converter returned: read out of what stage 2 stored, with no conversion asked"
                        + " for again",
                () -> assertThat(diagramFile).isRegularFile().hasBinaryContent(PictureScriptedExtractionBeans.THE_DIAGRAM));
        claim(
                "and the page shows it inside the entry of the document it came from, and not inside the"
                        + " other document's entry",
                () -> assertThat(theEntryNaming(text, PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM))
                        .contains("](" + stem + "/" + diagramName + ")"));
        claim(
                "and the only image on the page leads, by a relative path followed from the page's own"
                        + " directory, to that file: nothing outside the tree and nothing on a network is"
                        + " needed to see it",
                () -> assertThat(whereEveryImageLeads(page)).containsExactly(diagramFile));
        claim(
                "the letterhead both documents' conversions carried is written nowhere in the tree and"
                        + " linked from nowhere on the page: a picture every document carries is furniture,"
                        + " not information",
                () -> {
                    assertThat(everyFileIn(theTreeOf(root)))
                            .noneMatch(file -> file.getFileName().toString().equals(letterheadName));
                    assertThat(text).doesNotContain(letterheadName);
                });
    }

    /** The one membership entry of {@code page} naming {@code document}, from its number to the next. */
    private static String theEntryNaming(String page, String document) {
        Matcher entry = AN_ENTRY.matcher(page);
        List<Integer> starts = new ArrayList<>();
        while (entry.find()) {
            starts.add(entry.start());
        }
        starts.add(page.length());
        for (int at = 0; at < starts.size() - 1; at++) {
            String body = page.substring(starts.get(at), starts.get(at + 1));
            if (body.contains(document)) {
                return body;
            }
        }
        throw new IllegalStateException("no entry on the page names " + document);
    }

    /**
     * Where every image on {@code page} leads, each destination refused if it carries a scheme or is
     * rooted, then decoded and followed from the page's own directory, as a renderer follows it.
     */
    private static List<Path> whereEveryImageLeads(Path page) throws IOException {
        Matcher image = AN_IMAGE.matcher(Files.readString(page));
        List<Path> targets = new ArrayList<>();
        while (image.find()) {
            URI destination = URI.create(image.group(2));
            if (destination.isAbsolute() || destination.getPath().startsWith("/")) {
                throw new IllegalStateException("an image on " + page + " is not relative: " + destination);
            }
            targets.add(page.getParent().resolve(destination.getPath()).normalize());
        }
        return List.copyOf(targets);
    }

    /** The file name ADR-149 gives a picture: sixteen hex characters of its digest, then {@code .png}. */
    private static String nameOf(byte[] pixels) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pixels);
            return HexFormat.of().formatHex(digest).substring(0, NAME_LENGTH) + ".png";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /** The tree this corpus's one piece of work wrote, found by that work's own name. */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRuns(root).getLast());
    }

    /** The one cluster page of that tree: the one Markdown file that is not the index. */
    private Path theOnePageOf(Path root) throws IOException {
        List<Path> pages = everyFileIn(theTreeOf(root)).stream()
                .filter(file -> file.getFileName().toString().endsWith(".md"))
                .filter(file -> !file.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME))
                .toList();
        if (pages.size() != ONE_PAGE) {
            throw new IllegalStateException(
                    "this corpus arranges one group and so writes one page, and the tree holds " + pages.size());
        }
        return pages.getFirst();
    }

    private static List<Path> everyFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile).toList();
        }
    }

    /**
     * The two corpus documents, a seed, every gate before 6b open, walked once, and the arrangement it
     * produced approved: the state the one test here starts from.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM), "a document with a diagram");
        Files.writeString(
                root.resolve(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD),
                "a document with only the letterhead");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(root));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
        claim(
                "the first invocation arranged both of the " + TWO_DOCUMENTS + " corpus documents, so the"
                        + " approved arrangement holds a document with a picture of its own and one without",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM document_cluster WHERE run_id ="
                                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                                Integer.class,
                                theLatestArrangement(root).value()))
                        .isEqualTo(TWO_DOCUMENTS));
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString()));
    }

    /** Every record of stage 6b having run over {@code root}, oldest first, scoped to this walk. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }
}
