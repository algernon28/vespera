package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * Which runs {@code vespera run} can never arrive at again, and what still reads them (ADR-229, #481).
 *
 * <p><b>A run over a walk that is no longer its corpus root's latest finished walk is not arrived at
 * again.</b> Every run is minted over {@code Walks.finishedWalkFor}, the finished walk of the root with the
 * highest id, and the walk's id is hashed into the run's. A later walk is discarded only where it observed
 * what the walk immediately before it recorded (ADR-115), so an archive changed and changed back is a third
 * walk, compared with the second and kept, and the first is not the latest again. The first test drives
 * that sequence, which is the one by which the first walk's runs might have come back, and counts the rows
 * kept under them in every table keyed by a run.
 *
 * <p><b>Not arrived at is not the same as not read.</b> {@code vespera label} mints no run and walks
 * nothing: it reads the run the label file names. Where the archive has changed since that file was
 * written and no later invocation has reached stage 5, that run is over an earlier walk, and the command
 * records answers under it and reads the conversions its stage-2 run recorded keys for. The second test
 * holds that, and what the command does once those keys are removed, which nothing shipped does.
 *
 * <p>Each invocation reads the profile as a new process would, as {@code UpstreamRunOverAReusedWalkTest}
 * has it.
 */
@CascadeSliceTest
@Import({
    SeedScriptedExtractionBeans.class,
    AutoLabelling.class,
    ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest.Beans.class
})
@Epic("Pipeline")
@Feature("Repeated invocation")
@Issue("481")
@Link(name = "ADR-229", url = Adr.EVERY_RUNS_ROWS_ARE_KEPT_AND_THE_FILE_IS_NOT_MADE_SMALLER, type = "adr")
@Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class ARunOverAnEarlierWalkIsNotArrivedAtAgainInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String LABEL_FILE = "relevance-labels.yaml";

    /** How many texts the fixture's archive holds before one is added. */
    private static final int CORPUS_DOCUMENTS = 2;

    /** The text added to the archive and taken out of it again. */
    private static final String THE_ADDED_TEXT = "corpus-added.txt";

    /** The bean holding stage 2's confidence floor, read once when a process's context starts. */
    private static final String THE_CONFIDENCE_FLOOR_BEAN = "degenerateOutputConfidenceFloor";

    /** The words every scripted conversion carries, so finding them means a conversion on record was read. */
    private static final String WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH = "stubbed but real content";

    /**
     * The stages an invocation reaches with every gate open but the arrangement's approval: all but
     * generation, in cascade order.
     */
    private static final List<String> THE_STAGES_REACHED = Arrays.stream(StageModules.values())
            .filter(stage -> stage != StageModules.GENERATION)
            .map(StageModules::stage)
            .toList();

    /**
     * Which stages' runs hold rows in each table keyed by a run, as the first invocation of this fixture
     * leaves them: fourteen of the twenty-six. The other twelve hold no row in this fixture: the three of
     * generation, which is not reached, and those a corpus of two texts that share every shingle, one usable
     * seed and no answer recorded gives nothing to write to, {@code verdict} among them.
     */
    private static final Map<String, List<String>> THE_STAGES_WHOSE_RUNS_HOLD_ROWS = new TreeMap<>(Map.ofEntries(
            Map.entry("finished_step", THE_STAGES_REACHED),
            Map.entry("run_upstream", THE_STAGES_REACHED.subList(1, THE_STAGES_REACHED.size())),
            Map.entry("content_hash", List.of("byte-level-reduction")),
            Map.entry("detected_format", List.of("byte-level-reduction")),
            Map.entry("extraction_cache_key", List.of("extraction", "seed-measurement")),
            Map.entry("extraction_metric", List.of("extraction", "seed-measurement")),
            Map.entry("shingle", List.of("extraction")),
            Map.entry("confidence_distribution", List.of("content-census")),
            Map.entry("shingle_corpus_size", List.of("content-census")),
            Map.entry("shingle_document_frequency", List.of("content-census")),
            Map.entry("seed_corpus_comparison", List.of("seed-measurement")),
            Map.entry("document_cluster", List.of("embedding-scoring")),
            Map.entry("relevance_score", List.of("embedding-scoring")),
            Map.entry("cluster", List.of("arrangement"))));

    private static final Pattern THE_RUN_THE_LABEL_FILE_NAMES = Pattern.compile("generatedUnderRun: \"?([0-9a-f]{64})");

    /** The opening each question put to the scripted labeller, empty where none could be read. */
    static final List<Optional<String>> OPENINGS_PUT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller labellerThatKeepsWhatItWasPut() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "scripted-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    OPENINGS_PUT.add(question.opening());
                    return Optional.of(true);
                }
            };
        }
    }

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
    private ConfigurableApplicationContext context;

    /** The labeller's record and the scripted extractor's armed move are static and shared. */
    @BeforeEach
    void startClean() {
        OPENINGS_PUT.clear();
        SeedScriptedExtractionBeans.stopMovingAway();
    }

    @AfterEach
    void anEmptyProfileAgain() {
        profileStore.save(ProfileFixture.profile().build());
        aNewProcessReadsTheProfile();
    }

    @Test
    @Story("What a changed folder leaves of the work done before the change")
    @DisplayName("A folder changed and changed back is read as a new observation, every stage does its work again under it, and the work done over the first observation is neither continued nor touched")
    void anArchiveChangedAndChangedBackIsANewWalkAndTheFirstWalksRunsAreNotArrivedAtAgain(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds);
        invoke(root);
        long firstWalk = theOnlyWalkOf(root);
        List<String> firstRuns = runsOver(firstWalk);
        Map<String, Long> keptUnderTheFirstRuns = rowsUnderTheRunsOver(firstWalk);

        claim("the first invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and reaches every stage but the last, which waits for the arrangement to be approved, so the"
                        + " claims below are about the work of each of those stages",
                () -> assertThat(stagesOf(firstRuns)).containsExactlyElementsOf(THE_STAGES_REACHED));
        claim(
                "the tables that keep rows by piece of work hold rows under the first invocation's work, each"
                        + " under the stages named here",
                () -> assertThat(stagesHoldingRowsOver(firstWalk))
                        .isEqualTo(THE_STAGES_WHOSE_RUNS_HOLD_ROWS));

        Files.writeString(root.resolve(THE_ADDED_TEXT), "a corpus document that was not there before");
        invoke(root);
        List<Long> afterTheChange = walksOf(root);

        claim(
                "one text added to the folder is a second observation of it, beside the first",
                () -> assertThat(afterTheChange).hasSize(2).startsWith(firstWalk));
        claim(
                "every stage does its work again over the second observation, under work of its own and none"
                        + " of the first invocation's",
                () -> assertThat(runsOver(afterTheChange.getLast()))
                        .hasSameSizeAs(firstRuns)
                        .doesNotContainAnyElementsOf(firstRuns));

        Files.delete(root.resolve(THE_ADDED_TEXT));
        invoke(root);
        List<Long> afterChangingBack = walksOf(root);
        long thirdWalk = afterChangingBack.getLast();

        claim("the invocation after the text is taken out again reports success", () -> assertThat(
                        cli.getExitCode())
                .isZero());
        claim(
                "the folder, holding again the " + CORPUS_DOCUMENTS + " texts it held at first, is a third"
                        + " observation and not the first one again: an observation is compared with the one"
                        + " just before it, and that one held the added text",
                () -> assertThat(afterChangingBack).hasSize(3).startsWith(firstWalk));
        claim(
                "though the third observation recorded the same paths and sizes as the first",
                () -> assertThat(pathsAndSizesOf(thirdWalk)).isEqualTo(pathsAndSizesOf(firstWalk)));
        claim(
                "every stage does its work a third time, under work of its own: none of the first"
                        + " invocation's is continued",
                () -> assertThat(runsOver(thirdWalk))
                        .hasSameSizeAs(firstRuns)
                        .doesNotContainAnyElementsOf(firstRuns));
        claim(
                "no piece of work was added over the first observation since it stopped being the latest",
                () -> assertThat(runsOver(firstWalk)).containsExactlyElementsOf(firstRuns));
        claim(
                "and every row the first invocation's work left is still there, table by table, with none"
                        + " added and none removed",
                () -> assertThat(rowsUnderTheRunsOver(firstWalk)).isEqualTo(keptUnderTheFirstRuns));

        List<String> thirdRuns = runsOver(thirdWalk);
        invoke(root);

        claim(
                "invoked once more with nothing changed, the folder is not observed a fourth time",
                () -> assertThat(walksOf(root)).containsExactlyElementsOf(afterChangingBack));
        claim(
                "and the invocation arrives at the work done over the latest observation and adds none, which"
                        + " is what the work over an earlier observation can no longer have",
                () -> assertThat(runsOver(thirdWalk)).containsExactlyElementsOf(thirdRuns));
    }

    @Test
    @Story("What a changed folder leaves of the work done before the change")
    @DisplayName("Labelling by the model reads the scoring the label file names, though the folder has been observed again since, and with the conversion keys of that scoring's extraction removed it fails before asking anything")
    void labellingReadsTheRunTheLabelFileNamesThoughItsWalkIsNoLongerTheLatest(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds);
        invoke(root);
        long firstWalk = theOnlyWalkOf(root);
        String scoring = theRunTheLabelFileNames();

        claim(
                "the label file names the scoring made over the first observation of the folder",
                () -> assertThat(walkOf(scoring)).isEqualTo(firstWalk));

        // The archive changes, and the next invocation stops at stage 4's gate: a new walk, no stage 5.
        Files.writeString(root.resolve(THE_ADDED_TEXT), "a corpus document that was not there before");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .build());
        invoke(root);
        long latestWalk = walksOf(root).getLast();

        claim(
                "after one text is added and an invocation stops before the scoring, the folder has a later"
                        + " observation than the one the label file's scoring was made over",
                () -> assertThat(latestWalk).isGreaterThan(firstWalk));
        claim(
                "no scoring has been made over that later observation",
                () -> assertThat(stagesOf(runsOver(latestWalk)))
                        .doesNotContain(StageModules.EMBEDDING_SCORING.stage()));
        claim(
                "and the label file still names the first scoring",
                () -> assertThat(theRunTheLabelFileNames()).isEqualTo(scoring));

        cli.run("label", "--auto");

        claim("labelling by the model reports success", () -> assertThat(cli.getExitCode())
                .isEqualTo(CommandLine.ExitCode.OK));
        claim(
                "the model was put questions, each with the opening of its document, read from the"
                        + " conversion the first extraction recorded a key for",
                () -> assertThat(OPENINGS_PUT).isNotEmpty().allSatisfy(opening -> assertThat(opening)
                        .hasValueSatisfying(text -> assertThat(text).contains(WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH))));
        int questions = OPENINGS_PUT.size();
        claim(
                "and every answer recorded for this seed folder is recorded under the first scoring, the work"
                        + " over the earlier observation",
                () -> assertThat(runsTheAnswersAreRecordedUnder(seeds)).containsExactly(scoring));

        // What removing an earlier walk's rows would do, done here by the test: nothing that ships does it.
        String firstExtraction = theRunOf(firstWalk, StageModules.EXTRACTION);
        int removed = jdbcTemplate.update("DELETE FROM extraction_cache_key WHERE run_id = ?", firstExtraction);
        OPENINGS_PUT.clear();
        cli.run("label", "--auto");

        claim(
                "the first extraction had recorded conversion keys, so removing them removed something",
                () -> assertThat(removed).isPositive());
        claim(
                "with those keys removed, labelling by the model fails, where it had answered " + questions
                        + " question(s) a moment before: it reports an error of the program's and not success",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("and it puts no question to the model", () -> assertThat(OPENINGS_PUT).isEmpty());
    }

    private void invoke(Path root) {
        aNewProcessReadsTheProfile();
        cli.run("run", root.toString());
    }

    private void aNewProcessReadsTheProfile() {
        ((DefaultListableBeanFactory) context.getBeanFactory()).destroySingleton(THE_CONFIDENCE_FLOOR_BEAN);
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open and the embedding gate open. */
    private void everyGateOpen(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the embedding gate is open")
                .build());
    }

    /** The finished walks of {@code root}, in the order they were made. */
    private List<Long> walksOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM walk WHERE root = ? AND finished = 1 ORDER BY id",
                Long.class,
                Walk.canonicalRoot(root).toString());
    }

    private long theOnlyWalkOf(Path root) {
        List<Long> walks = walksOf(root);
        claim(
                "the folder has been observed once so far, so the claims below are about that observation",
                () -> assertThat(walks).hasSize(1));
        return walks.getFirst();
    }

    /** Every run over {@code walk}, in the order they were minted. */
    private List<String> runsOver(long walk) {
        return jdbcTemplate.queryForList("SELECT id FROM run WHERE walk_id = ? ORDER BY rowid", String.class, walk);
    }

    private String theRunOf(long walk, StageModules stage) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE walk_id = ? AND stage = ?", String.class, walk, stage.stage());
    }

    private long walkOf(String run) {
        Long walk = jdbcTemplate.queryForObject("SELECT walk_id FROM run WHERE id = ?", Long.class, run);
        return walk == null ? -1 : walk;
    }

    private List<String> stagesOf(List<String> runs) {
        return runs.stream()
                .map(run -> jdbcTemplate.queryForObject("SELECT stage FROM run WHERE id = ?", String.class, run))
                .toList();
    }

    /** Every table with a {@code run_id} column, which is every table keyed by a run. */
    private List<String> tablesKeyedByARun() {
        return jdbcTemplate.queryForList(
                "SELECT m.name FROM sqlite_master m JOIN pragma_table_info(m.name) c"
                        + " WHERE m.type = 'table' AND c.name = 'run_id' ORDER BY m.name",
                String.class);
    }

    /** The rows each table keyed by a run holds under the runs over {@code walk}, by table. */
    private Map<String, Long> rowsUnderTheRunsOver(long walk) {
        Map<String, Long> rows = new TreeMap<>();
        for (String table : tablesKeyedByARun()) {
            rows.put(
                    table,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + table + " WHERE run_id IN (SELECT id FROM run WHERE walk_id = ?)",
                            Long.class,
                            walk));
        }
        return rows;
    }

    /** For each table keyed by a run that holds a row under a run over {@code walk}, those runs' stages. */
    private Map<String, List<String>> stagesHoldingRowsOver(long walk) {
        Map<String, List<String>> stages = new TreeMap<>();
        for (String table : tablesKeyedByARun()) {
            List<String> holding = jdbcTemplate.queryForList(
                    "SELECT r.stage FROM run r WHERE r.walk_id = ? AND EXISTS"
                            + " (SELECT 1 FROM " + table + " t WHERE t.run_id = r.id) ORDER BY r.rowid",
                    String.class,
                    walk);
            if (!holding.isEmpty()) {
                stages.put(table, holding);
            }
        }
        return stages;
    }

    private List<String> pathsAndSizesOf(long walk) {
        return jdbcTemplate.queryForList(
                "SELECT path || ' ' || size_bytes FROM file_occurrence WHERE walk_id = ? ORDER BY path",
                String.class,
                walk);
    }

    private String theRunTheLabelFileNames() throws IOException {
        Matcher named = THE_RUN_THE_LABEL_FILE_NAMES.matcher(Files.readString(workingDirectory.resolve(LABEL_FILE)));
        claim("the label file names the scoring it was written under", () -> assertThat(named.find())
                .isTrue());
        return named.group(1);
    }

    private List<String> runsTheAnswersAreRecordedUnder(Path seeds) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT run_id FROM relevance_label WHERE seed_set = ?",
                String.class,
                Walk.canonicalRoot(seeds).toString());
    }
}
