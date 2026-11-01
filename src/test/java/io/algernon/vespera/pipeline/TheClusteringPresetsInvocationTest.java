package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * How finely documents are clustered is a preset the profile may override, and it enters the scoring run's identity
 * only where it differs from the value every run was minted under before it was a preset (ADR-330 §8, §9): so stating
 * the shipped value arrives at the run nothing stated, and another value is a run of its own, with clusters of its own.
 *
 * <p>Every vector of the fixture is the same, and on such a partition of {@value #DOCUMENTS} documents a resolution of
 * 1.0 finds one cluster and one of {@value #A_FINER_RESOLUTION} finds one for each document, measured through {@code
 * Communities.of}; so the count of clusters says which value reached the graph.
 *
 * <p>{@code RunIdentityGoldenTest} holds the text of every run's identity with the presets at their shipped values, and
 * passes unchanged once they are presets: that is the claim that no recorded configuration moves.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits clusterResolution in the profile and in the scoring run's identity")
class TheClusteringPresetsInvocationTest {

    private static final int DOCUMENTS = 3;

    private static final double A_FINER_RESOLUTION = 2.5;

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The tail of a scoring run's recorded configuration where the profile names a finer resolution. */
    private static final String THE_MEMBER = ",\"clusterResolution\":" + A_FINER_RESOLUTION + "}";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

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

    @BeforeAll
    static void aCorpusOfThreeDocumentsAndOneSeed() throws IOException {
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    @BeforeEach
    void everyGateOpenAndNoResolutionNamed() {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("Naming the shipped resolution in the profile arrives at the run naming none, and another resolution is a run of its own with groups of its own")
    void aProfileValueOtherThanTheShippedOneNamesAnotherScoringRun() throws IOException {
        cli.run("run", root.toString());
        List<String> namingNone = theScoringRuns();

        theProfileAlsoNamesTheResolution("1.0");
        cli.run("run", root.toString());
        claim("the invocation under the shipped value ended well",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("and arrived at the scoring run it arrived at naming none, so stating the shipped value mints nothing",
                () -> assertThat(theScoringRuns()).containsExactlyInAnyOrderElementsOf(namingNone));

        theProfileAlsoNamesTheResolution(String.valueOf(A_FINER_RESOLUTION));
        cli.run("run", root.toString());
        claim("another value ends well too",
                () -> assertThat(cli.getExitCode()).isZero());
        List<Map<String, Object>> named = jdbcTemplate.queryForList(
                "SELECT id, config_consumed FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ? AND config_consumed LIKE ?",
                "%\"clusterResolution\"%", "%" + root.toString().replace("\\", "\\\\") + "%");
        claim("it is one more scoring run, whose recorded configuration ends with the resolution",
                () -> assertThat(named).singleElement().satisfies(run -> assertThat(run.get("config_consumed").toString())
                        .endsWith(THE_MEMBER)));
        claim("and the value reached the graph: one group for each of the documents, where the shipped value finds one",
                () -> assertThat(List.of(clustersUnder(named.getFirst().get("id").toString()),
                                clustersUnder(namingNone.getFirst())))
                        .containsExactly(DOCUMENTS, 1));
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("A resolution the jar cannot read is named as the next value wanted, and the shipped one applies in its place")
    void anUnreadableResolutionIsNamedAndTheShippedValueApplies() throws IOException {
        cli.run("run", root.toString());
        List<String> namingNone = theScoringRuns();

        theProfileAlsoNamesTheResolution("0,5");
        cli.run("run", root.toString());

        claim("the invocation ends well, on the scoring run it arrived at naming none",
                () -> assertThat(List.of(cli.getExitCode(), theScoringRuns()))
                        .containsExactly(0, namingNone));
        claim("and the next invocation is told to want it, as any unreadable value is",
                () -> assertThat(theNextInvocationFile().getProperty("wants")).isEqualTo("clusterResolution"));
    }

    /**
     * Zero, a negative number and the two values {@code Double.parseDouble} reads that are no numbers anyone counts
     * with: {@code NumericValue} calls each answered, and no partition can be divided by one, so each is ignored as
     * {@code ExtractionAttempt} ignores a number no attempt is numbered by.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "-1", "NaN", "Infinity"})
    @Story("How finely documents are clustered is a preset")
    @DisplayName("A number written by hand that no partition can be divided by is ignored as an unreadable one is: named as the next value wanted, and the shipped one applies in its place")
    void aResolutionThatIsNoFiniteNumberAboveZeroIsIgnoredAndNamed(String written) throws IOException {
        cli.run("run", root.toString());
        List<String> namingNone = theScoringRuns();

        theProfileAlsoNamesTheResolution(written);
        cli.run("run", root.toString());

        claim("the invocation ends well rather than stopping on it, on the scoring run it arrived at naming none",
                () -> assertThat(List.of(cli.getExitCode(), theScoringRuns())).containsExactly(0, namingNone));
        claim("and the next invocation is told to want it, as any unreadable value is",
                () -> assertThat(theNextInvocationFile().getProperty("wants")).isEqualTo("clusterResolution"));
    }

    /**
     * What any change of the scoring run's identity does to a sample being answered (ADR-330 §10). That a new run's id
     * draws other documents is {@code RelevanceDistributionTest}'s: this corpus is smaller than a band's sample, so
     * each run asks about all of it.
     */
    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("Another resolution is a new sample: the file is written anew naming its run, a copy of the earlier one is refused, and the answers recorded are kept and shown")
    void anotherResolutionWritesANewSampleRefusesTheEarlierFileAndKeepsTheAnswers(@TempDir Path aCorpusOfItsOwn, @TempDir Path elsewhere)
            throws IOException {
        // A root of its own, in the runs' configuration, so both runs here are minted here and each writes its file.
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(aCorpusOfItsOwn.resolve("own-" + document + ".txt"), "another corpus document, the " + document + "th");
        }
        cli.run("run", aCorpusOfItsOwn.toString());
        Path labelFile = workingDirectory.resolve("relevance-labels.yaml");
        String runOfTheEarlierFile = theRunNamedBy(labelFile);
        Files.writeString(labelFile, Files.readString(labelFile, StandardCharsets.UTF_8)
                .replace("relevant: null", "relevant: true"), StandardCharsets.UTF_8);
        Path earlierFile = Files.copy(labelFile, elsewhere.resolve("the-earlier-sample.yaml"));
        cli.run("label");
        int answersRecorded = answersRecorded();
        claim("the answers of the earlier sample are recorded, the precondition of what follows",
                () -> assertThat(answersRecorded).isGreaterThan(0));

        theProfileAlsoNamesTheResolution(String.valueOf(A_FINER_RESOLUTION));
        cli.run("run", aCorpusOfItsOwn.toString());

        claim("the file in the working folder is written anew, naming the run of the new resolution",
                () -> assertThat(theRunNamedBy(labelFile)).isNotEqualTo(runOfTheEarlierFile));

        cli.run("label", earlierFile.toString());

        claim("a copy of the earlier file is refused whole, so nothing is recorded from it",
                () -> assertThat(List.of(cli.getExitCode() != 0, answersRecorded() == answersRecorded))
                        .containsExactly(true, true));
        claim("the answers recorded are kept, and the new file shows each where it asks about the same document",
                () -> assertThat(Files.readString(labelFile, StandardCharsets.UTF_8)).doesNotContain("relevant: null"));
    }

    /** Appends the key as the operator writes it, since the fixture names only the keys the jar knows. */
    private void theProfileAlsoNamesTheResolution(String value) throws IOException {
        everyGateOpenAndNoResolutionNamed();
        Path profile = profileStore.file();
        Files.writeString(profile, Files.readString(profile, StandardCharsets.UTF_8)
                .replaceAll("(?m)^clusterResolution:\\R(  .*\\R)*", "")
                + "clusterResolution:\n  value: \"" + value + "\"\n  provenance: \"set by this test\"\n",
                StandardCharsets.UTF_8);
    }

    /** The scoring runs of the class's shared corpus root, which is in each one's configuration. */
    private List<String> theScoringRuns() {
        return jdbcTemplate.queryForList("SELECT id FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ? ORDER BY id",
                String.class, "%" + root.toString().replace("\\", "\\\\") + "%");
    }

    private int answersRecorded() {
        Integer answers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM relevance_label", Integer.class);
        return answers == null ? 0 : answers;
    }

    private static String theRunNamedBy(Path labelFile) throws IOException {
        Map<String, Object> file = new Yaml().load(Files.readString(labelFile, StandardCharsets.UTF_8));
        return String.valueOf(file.get("generatedUnderRun"));
    }

    private int clustersUnder(String scoringRun) {
        Integer clusters = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT cluster_ordinal) FROM document_cluster WHERE run_id = ?", Integer.class, scoringRun);
        return clusters == null ? 0 : clusters;
    }

    private static Properties theNextInvocationFile() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve("next-invocation.properties"),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
