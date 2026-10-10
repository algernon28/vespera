package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.ModuleNamedVersionsBeans;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What each kind of run records it was derived from, pinned as text (ADR-153, ADR-058, ADR-048).
 *
 * <p>A run's id is derived from its {@code implementation_version}, its {@code config_consumed}, its
 * walk and its upstream runs (its stage is recorded beside it, not hashed into it), and a re-invocation recognises finished work only
 * because the same inputs derive the same id (ADR-115). So a refactor that changes one character of
 * what a stage writes into {@code config_consumed} — a field renamed, two fields reordered, a record
 * swapped for a map, a value formatted differently — or the order it names its modules in, re-mints
 * every run of that stage for work already done, and nothing else fails: every behaviour test still
 * passes, because the work is still done correctly, just again. These tests are what fails instead.
 *
 * <p><b>The expected text is written out literally, on purpose.</b> Reading the stage's own
 * constants or calling its own {@code configConsumed} would move with the code and pin nothing. The
 * only parts not written literally are the ones that cannot be: the corpus root and seed folder,
 * which are temporary directories, and the upstream run ids, which are themselves derived from
 * the directories — each read back from the row it names, so the claim is still that this field names
 * that run. The stage names in the queries are literal for the same reason: they are persisted too.
 *
 * <p>The module list is read through {@link ModuleNamedVersionsBeans}, which records each module's
 * name as its version: several modules share one commit at any given time, so the build's real SHAs
 * cannot show an order. The joining is still the real one.
 *
 * <p><b>A change that is meant to move one of these edits the literal here</b>, in the same change,
 * which is what makes a re-mint something a reviewer sees rather than something an operator
 * discovers. Bumping the Docling image in {@code application.yaml} is one such change: stage 2's
 * identity carries it (ADR-147), so the extraction text below names it.
 *
 * <p>The whole job runs once for the class, the way an operator reaches generation: one invocation
 * that stops at the arrangement gate, the arrangement approved and a generation model named, and a
 * second invocation. Each test then reads the one row it is about.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, ModuleNamedVersionsBeans.class})
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Issue("294")
@Link(name = "ADR-153", url = Adr.THE_WHOLE_JOB_TESTS_SHARE_ONE_SLICE, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
class RunIdentityGoldenTest {

    /** A floor of 1.0 opens stage 4's gate the way every other whole-job test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so the embedding-model gate and everything behind it open. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The generation model this test names, so the generation text rests on a name this test chose. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    /** Where the corpus and the seed folder are written: one pair for the class, like the job run. */
    @TempDir
    static Path scratch;

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Path root;

    private static Path seeds;

    /**
     * One corpus document and one seed, every gate opened, and the job run through to generation --
     * once for the class, by whichever test comes first, since every test reads the same rows.
     */
    @BeforeEach
    void theWholeJobHasRunThroughToGeneration() throws IOException {
        if (root != null) {
            return;
        }
        root = Files.createDirectory(scratch.resolve("corpus"));
        seeds = Files.createDirectory(scratch.resolve("seeds"));
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", root.toString());
        String arrangement = ArrangementGate.shortNameOf(new RunId(
                theRunOf("arrangement").get("id").toString()));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(arrangement, "set by this test")
                .generationModel(GENERATION_MODEL, "set by this test")
                .build());
        cli.run("run", root.toString());
    }

    /**
     * Stage 1 read no settings until ADR-171, and recorded {@code {}}. It now records the text size
     * ceiling, a constant in {@code extraction} that its {@code corpus}-only implementation version cannot
     * see (ADR-058), and the log floor this profile leaves unset. Red until the change lands.
     */
    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Byte-level reduction is identified by the text size ceiling, the rule for text converted in parts and the log floor, under the corpus code alone")
    @Issue("370")
    @Issue("371")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void byteLevelReduction() {
        Map<String, Object> run = theRunOf("byte-level-reduction");

        claim(
                "the settings it records are the size over which a text file is not sent whole, 16,000,000"
                        + " bytes, then the rule for converting text in parts -- its version, that ceiling, the"
                        + " largest text cut, 64,000,000 bytes, and the largest part, 8,000,000 bytes -- then the"
                        + " share of timestamped lines that makes a text file a log, recorded as null because"
                        + " this profile sets none -- so a change to any of them is a new piece of work, and the"
                        + " same walk under the same three still names the same one",
                () -> assertThat(run.get("config_consumed"))
                        .isEqualTo("{\"textSizeCeilingBytes\":16000000,"
                                + "\"textParts\":\"v1,over=16000000,upto=64000000,part=8000000\","
                                + "\"logTimestampShareFloor\":null}"));
        claim(
                "and its code version is the corpus module's alone",
                () -> assertThat(run.get("implementation_version")).isEqualTo("corpus"));
    }

    /**
     * The image is the revision-2 build, which reports its own name in {@code /version} (ADR-179), so
     * the name appears twice: once as configured, once among the versions the sidecar reported.
     */
    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Extraction is identified by the converter it used and the confidence floor, in that order")
    @Issue("373")
    @Issue("371")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void extraction() {
        Map<String, Object> run = theRunOf("extraction");

        claim(
                "the settings it records are the converter's full identity -- its image, its reported"
                        + " versions sorted by name, which include the image it says it was built as, and the"
                        + " options sent with every conversion, the rule for cutting a large text into parts"
                        + " among them -- then the confidence floor, recorded as null"
                        + " because this profile sets none",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        "{\"extractorIdentity\":\"docling-serve;"
                                + "image=vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2;"
                                + "docling=2.124.0;docling-serve=1.32.0;"
                                + "vespera-image=vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2;"
                                + "to_formats=json;ocr_preset=rapidocr;"
                                + "image_export_mode=embedded;naming=1;"
                                + "text_parts=v1,over=16000000,upto=64000000,part=8000000\","
                                + "\"degenerateOutputConfidenceFloor\":null}"));
        claim(
                "and its code version is the extraction module's, then the similarity module's",
                () -> assertThat(run.get("implementation_version")).isEqualTo("extraction+similarity"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Content census is identified by the corpus root and the extraction it read")
    @Issue("353")
    @Link(name = "ADR-222", url = Adr.A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT, type = "adr")
    void contentCensus() {
        Map<String, Object> run = theRunOf("content-census");

        claim(
                "the settings it records are the corpus root, then the id of the extraction it read",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        "{\"root\":\"%s\",\"extractionRunId\":\"%s\"}"
                                .formatted(inJson(Walk.canonicalRoot(root)), theIdOf("extraction"))));
        claim(
                "and its code version is the similarity module's, then extraction's, and not the version of"
                        + " the code that runs the stages, which holds nothing that decides what this one"
                        + " measures",
                () -> assertThat(run.get("implementation_version")).isEqualTo("similarity+extraction"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Content redundancy is identified by the corpus root, the content census it read and the boilerplate floor")
    @Issue("353")
    @Link(name = "ADR-222", url = Adr.A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT, type = "adr")
    void contentRedundancy() {
        Map<String, Object> run = theRunOf("content-redundancy");

        claim(
                "the settings it records are the corpus root, the id of the content census it read, and the"
                        + " boilerplate floor of " + BOILERPLATE_FLOOR + " this profile sets, written as a number",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        "{\"root\":\"%s\",\"stage3RunId\":\"%s\",\"boilerplateDocumentFrequencyFloor\":1.0}"
                                .formatted(inJson(Walk.canonicalRoot(root)), theIdOf("content-census"))));
        claim(
                "and its code version is the similarity module's, then extraction's, and not the version of"
                        + " the code that runs the stages, which holds nothing that decides what this one"
                        + " removes",
                () -> assertThat(run.get("implementation_version")).isEqualTo("similarity+extraction"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Seed measurement is identified by the corpus root, the seed folder and the redundancy check it read")
    @Issue("479")
    @Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
    void seedMeasurement() {
        Map<String, Object> run = theRunOf("seed-measurement");

        claim(
                "the settings it records are the corpus root, the seed folder, then the id of the content"
                        + " redundancy check it read",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        "{\"root\":\"%s\",\"seedFolder\":\"%s\",\"redundancyRunId\":\"%s\"}"
                                .formatted(
                                        inJson(Walk.canonicalRoot(root)),
                                        inJson(Walk.canonicalRoot(seeds)),
                                        theIdOf("content-redundancy"))));
        claim(
                "and its code version is the embedding module's, then extraction's, and not the version of the"
                        + " code that runs the stages, which since the rule for a seed with nothing to read went"
                        + " to extraction holds nothing that decides what this one measures",
                () -> assertThat(run.get("implementation_version")).isEqualTo("embedding+extraction"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Embedding and scoring is identified by the corpus root, the embedding model, the seed measurement and the score floor")
    @Issue("479")
    @Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
    void embeddingScoring() {
        Map<String, Object> run = theRunOf("embedding-scoring");

        claim(
                "the settings it records are the corpus root, the embedding model this profile names, the id"
                        + " of the seed measurement it read, and the relevance floor, recorded as null because"
                        + " this profile sets none",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        ("{\"root\":\"%s\",\"embeddingModel\":\"" + EMBEDDING_MODEL + "\","
                                        + "\"measurementRunId\":\"%s\",\"relevanceScoreFloor\":null}")
                                .formatted(inJson(Walk.canonicalRoot(root)), theIdOf("seed-measurement"))));
        claim(
                "and its code version is the embedding module's, then extraction's, and not the version of the"
                        + " code that runs the stages, which since the relevance floor's two rules went to"
                        + " embedding holds nothing that decides what this one removes",
                () -> assertThat(run.get("implementation_version")).isEqualTo("embedding+extraction"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Arrangement is identified by the corpus root and the scoring it arranged")
    @Issue("353")
    @Link(name = "ADR-222", url = Adr.A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT, type = "adr")
    void arrangement() {
        Map<String, Object> run = theRunOf("arrangement");

        claim(
                "the settings it records are the corpus root, then the id of the scoring it arranged",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        "{\"corpusRoot\":\"%s\",\"scoringRunId\":\"%s\"}"
                                .formatted(inJson(Walk.canonicalRoot(root)), theIdOf("embedding-scoring"))));
        claim(
                "and its code version is the synthesis module's, then extraction's, then embedding's, and"
                        + " not the version of the code that runs the stages, which holds nothing that"
                        + " decides how the documents are arranged",
                () -> assertThat(run.get("implementation_version")).isEqualTo("synthesis+extraction+embedding"));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Generation is identified by the corpus root, the approved arrangement and the writing model's identity")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    @Issue("479")
    @Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
    void generation() {
        Map<String, Object> run = theRunOf("generation");

        claim(
                "the settings it records are the corpus root, the id of the approved arrangement, the"
                        + " writing model this profile names, the weights the serving runtime reported"
                        + " under that name, the 8192-token window it reads each group through by default,"
                        + " the 1024 tokens it leaves the reply, and -1 for how much of a question too long"
                        + " for the window the engine is asked to keep: all of it that fits, so a cut shows"
                        + " in the count",
                () -> assertThat(run.get("config_consumed")).isEqualTo(
                        ("{\"corpusRoot\":\"%s\",\"arrangementRunId\":\"%s\","
                                        + "\"generationModel\":\"" + GENERATION_MODEL + "\","
                                        + "\"weightsDigest\":\"" + EmbeddingScriptedBeans.DIGEST + "\","
                                        + "\"contextWindow\":8192,\"replyAllowance\":1024,\"numKeep\":-1}")
                                .formatted(inJson(Walk.canonicalRoot(root)), theIdOf("arrangement"))));
        claim(
                "and its code version is the synthesis module's, then extraction's, then embedding's, then the"
                        + " profile module's, which reads every key the index states off the profile, and not"
                        + " the version of the code that runs the stages, which holds nothing that decides"
                        + " what this one writes",
                () -> assertThat(run.get("implementation_version"))
                        .isEqualTo("synthesis+extraction+embedding+profile"));
    }

    /**
     * The one row {@code stage} recorded over this test's corpus.
     *
     * <p>The latest, should there be more than one: an invocation that derives the same inputs derives
     * the same id and adds no row, so two rows here would mean the second invocation derived something
     * else — which the text claims above would then be about.
     */
    private Map<String, Object> theRunOf(String stage) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT r.id, r.config_consumed, r.implementation_version FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC",
                stage,
                Walk.canonicalRoot(root).toString());
        return runs.isEmpty() ? Map.of() : runs.getFirst();
    }

    private String theIdOf(String stage) {
        return String.valueOf(theRunOf(stage).get("id"));
    }

    /** {@code path} as a JSON string's contents: a Windows root carries backslashes, which JSON escapes. */
    private static String inJson(Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
