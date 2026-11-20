package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.embedding.StoredVectors;
import io.algernon.vespera.engine.embedding.WritingEmbedder;
import io.algernon.vespera.engine.extraction.StoredChunks;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.publish.retrieval.RetrievalPlugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A drawing with {@code retrieval} named hands the plugin vectors under the scoring run's embedder identity (ADR-434
 * §7): each passage's is the one {@code vector} holds for its chunk under that identity, each writing's is kept in
 * {@code writing_vector} by its digest and is not embedded at the next drawing, and an artefact other than the run's,
 * reported when the first writing is to be embedded, stops the plugin alone and is named on the closing line, the
 * tree drawn whole.
 *
 * <p>Driven end to end over {@link DeliverableInvocationTest}'s corpus, the scripted embedding runtime counting each
 * call it answers, so that "not embedded again" is a count and not a belief.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, RetrievalPlugin.class, RecordInputsOf.class, StoredChunks.class,
        StoredVectors.class, WritingEmbedder.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Synthesis")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class TheRecordsCarryTheScoringRunsVectorsInvocationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @BeforeEach
    @AfterEach
    void forgetWhatWasScripted() {
        EmbeddingScriptedBeans.servesEveryModel();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("The vectors are the scoring run's")
    @DisplayName("Each passage carries the vector stored for its chunk, each writing's vector is kept by its digest, and a second drawing embeds nothing")
    void thePassagesVectorsAreTheStoredOnesAndTheWritingsAreKeptOnce(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        statingOutputs("tree, retrieval");

        cli.run("run", root.toString());
        List<JsonNode> records = recordsOf(root);
        List<JsonNode> passages = records.stream().filter(record -> kind(record).equals("member-passage")).toList();
        List<JsonNode> writings = records.stream().filter(record -> !kind(record).equals("member-passage")).toList();
        String identity = records.getFirst().get("embedder").asString();
        int callsAfterTheFirst = EmbeddingScriptedBeans.embeddingCallsMade();

        cli.run("run", root.toString());

        claim("the invocation ended 0 and the plugin drew records: passages for the two documents, a synthesis doc and an overview",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(passages).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(writings).extracting(JsonRecords::kindOf)
                            .contains("synthesis-doc", "topic-overview");
                });
        claim("every record carries the one identity the stored vectors are under",
                () -> {
                    assertThat(records).extracting(record -> record.get("embedder").asString()).containsOnly(identity);
                    assertThat(jdbcTemplate.queryForList(
                            "SELECT DISTINCT embedder_identity FROM vector", String.class)).contains(identity);
                });
        claim("each passage's vector is, component for component, the one vector holds for the chunk of its text under that identity",
                () -> {
                    for (JsonNode passage : passages) {
                        List<float[]> stored = jdbcTemplate.query(
                                "SELECT v.embedding FROM vector v JOIN chunk_cache c"
                                        + " ON c.content_hash = v.content_hash AND c.chunker_identity = v.chunker_identity"
                                        + " AND c.chunking_rule_identity = v.chunking_rule_identity AND c.ordinal = v.ordinal"
                                        + " WHERE c.chunk_text = ? AND v.embedder_identity = ?",
                                (resultSet, row) -> floatsOf(resultSet.getBytes(1)),
                                passage.get("text").asString(),
                                identity);
                        assertThat(stored).as("the vectors stored for %s", passage.get("document")).isNotEmpty();
                        assertThat(floatsOf(passage.get("vector"))).isEqualTo(stored.getFirst());
                    }
                });
        claim("each writing's vector is kept in writing_vector by the digest of its text, and is the vector the record carries",
                () -> {
                    for (JsonNode writing : writings) {
                        List<float[]> kept = jdbcTemplate.query(
                                "SELECT embedding FROM writing_vector WHERE writing_digest = ? AND embedder_identity = ?",
                                (resultSet, row) -> floatsOf(resultSet.getBytes(1)),
                                WritingEmbedder.digestOf(writing.get("text").asString()),
                                identity);
                        assertThat(kept).hasSize(1);
                        assertThat(floatsOf(writing.get("vector"))).isEqualTo(kept.getFirst());
                    }
                });
        claim("the second drawing, whose writings are all kept, asked the embedding runtime nothing",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isEqualTo(callsAfterTheFirst));
    }

    @Test
    @Story("The vectors are the scoring run's")
    @DisplayName("An artefact other than the run's, reported where the first writing is to be embedded, stops the retrieval plugin alone and is named on the closing line")
    void anotherArtefactStopsThePluginAlone(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        anApprovedCorpus(root, seeds);
        statingOutputs("tree, retrieval");
        // An overview no earlier drawing kept the vector of, so that a writing is to be embedded at all.
        GenerationScriptedBeans.writesEveryOverviewAs("An overview no other drawing wrote, number " + System.nanoTime() + " [1].");
        // Ollama reports another digest once the generation model has answered, before the first writing is embedded.
        GenerationScriptedBeans.duringEachCall(() -> EmbeddingScriptedBeans.hasPulledAgainAs("a-digest-pulled-since"));
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);
        Path tree = theTreeOf(root);

        claim("the invocation went to its end, the tree drawn: its index stands",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).isRegularFile();
                });
        claim("the plugin was stopped alone: its drawing.txt says failed and no records file stands",
                () -> {
                    assertThat(Files.readString(tree.resolve("retrieval").resolve("drawing.txt"))).isEqualTo("failed\n");
                    assertThat(tree.resolve("retrieval").resolve("records.jsonl")).doesNotExist();
                });
        claim("and the closing line names the plugin with the type it threw",
                () -> assertThat(said).contains("retrieval rendering could not be drawn (IllegalStateException)"));
    }

    private static String kind(JsonNode record) {
        return JsonRecords.kindOf(record);
    }

    /** The lines of the retrieval plugin's file in the tree this corpus's work wrote, each read as JSON. */
    private List<JsonNode> recordsOf(Path root) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        for (String line : Files.readAllLines(theTreeOf(root).resolve("retrieval").resolve("records.jsonl"))) {
            records.add(JSON.readTree(line));
        }
        return records;
    }

    private static float[] floatsOf(JsonNode array) {
        float[] vector = new float[array.size()];
        for (int at = 0; at < vector.length; at++) {
            vector[at] = (float) array.get(at).asDouble();
        }
        return vector;
    }

    private static float[] floatsOf(byte[] blob) {
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[blob.length / Float.BYTES];
        for (int at = 0; at < vector.length; at++) {
            vector[at] = buffer.getFloat();
        }
        return vector;
    }

    /** The reading of one line's fields, named so the claims above read as sentences. */
    private static final class JsonRecords {

        static String kindOf(JsonNode record) {
            return record.get("kind").asString();
        }
    }

    private Path theTreeOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(runs.getLast());
    }

    /** States {@code outputs} after whatever the fixtures saved, as the file states a key. */
    private void statingOutputs(String value) throws IOException {
        Files.writeString(
                profileStore.file(),
                "\noutputs:\n  value: \"" + value + "\"\n  provenance: set by this test\n",
                StandardOpenOption.APPEND);
    }

    /** A corpus of two documents walked once, its arrangement approved: the state every claim here starts from. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor("0.0", "set by this test, so nothing earlier is asked for")
                .generationContextWindow("4096", "set by this test, so every test here reads alike")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(
                        ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")),
                        "read by this test")
                .build());
    }
}
