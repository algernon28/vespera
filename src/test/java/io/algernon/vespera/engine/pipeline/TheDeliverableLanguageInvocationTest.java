package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

/**
 * The deliverable's language end to end (ADR-321 §1, §2, §4, §6): the profile's {@code deliverableLanguage} reaches
 * the question every cluster is asked, the generation run's identity, and every page the run draws; changing it is a
 * generation run of its own, over an arrangement approved again (ADR-407 §3); the language the invocation kept is the
 * one the generation run records and writes in, whatever is saved part-way through (ADR-407 §3); and a language the
 * deliverable cannot be drawn in stops where the arrangement run would be minted, before any topic is named (ADR-407
 * §3).
 *
 * <p>The scripted model answers whatever it is asked in the words it was scripted with, so what is claimed of the
 * writing is what it was asked for, never what it wrote. The report says <em>group</em> where the code says cluster
 * (ADR-122), as {@link GenerationInvocationTest} explains.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
class TheDeliverableLanguageInvocationTest {

    /** What the profile says for an Italian deliverable. */
    private static final String ITALIAN = "it";

    /** The language an empty key means, as the arrangement and generation runs record it. */
    private static final String ENGLISH = "en";

    /** A language code the deliverable has no fixed words for. */
    private static final String A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN = "fr";

    private static final String WRITE_BOTH_IN_ENGLISH = "Write both in English.";

    /** What two languages of the same arrangement are worth: two generation runs. */
    private static final int TWO_GENERATION_RUNS = 2;

    /**
     * The model the groups are written under, named in the profile so that the one step that checks it is the writing
     * step's: the topics' naming model is the shipped default, another name, checked at the arrangement step.
     */
    private static final String THE_WRITING_MODEL = "a-named-writing-model:8b";

    /** A run's language member, read out of its recorded settings. */
    private static final Pattern THE_RECORDED_LANGUAGE = Pattern.compile("\"deliverableLanguage\":\"([^\"]*)\"");

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

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With the language emptied again, the groups are written again under work of their own, asked for in English")
    void aChangedLanguageIsWorkOfItsOwn(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusUpToTheApprovedArrangement(root, seeds);
        setTheLanguageTo(ITALIAN);
        runApprovingTheArrangementIn(root, ITALIAN);
        setTheLanguageTo(null);
        GenerationScriptedBeans.forgetScriptedAnswers();

        runApprovingTheArrangementIn(root, ENGLISH);
        List<String> runs = generationRuns(root);

        claim("the invocation after the change reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the work is recorded " + TWO_GENERATION_RUNS + " times, under two names: writing over the same groups"
                        + " in English is not the Italian work over again, and finding the Italian record would leave"
                        + " English readers with Italian pages",
                () -> assertThat(runs).hasSize(TWO_GENERATION_RUNS).doesNotHaveDuplicates());
        claim(
                "the second records English, the language an empty key means, and then the overview's temperature"
                        + " and seed, which close the settings",
                () -> assertThat(settingsOf(runs.getLast())).endsWith(
                        ",\"deliverableLanguage\":\"en\",\"overviewTemperature\":0.0,\"overviewSeed\":1}"));
        claim(
                "and every group was asked for again, in English",
                () -> assertThat(GenerationScriptedBeans.promptsSent()).isNotEmpty().allSatisfy(question ->
                        assertThat(question).contains(WRITE_BOTH_IN_ENGLISH).doesNotContain("Italian")));
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With a language the pages cannot be drawn in, the invocation stops before the arrangement is recorded and before any topic is named")
    @Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
    void aLanguageItCannotBeDrawnInStopsWhereTheArrangementIsMinted(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        setTheLanguageTo(A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN);
        GenerationScriptedBeans.forgetScriptedAnswers();
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim(
                "the invocation does not report success: the operator wrote a language, and naming the topics in"
                        + " another would hand the readers one nobody chose",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "it says the key, the value as written, the two languages there are, and that an empty key means"
                        + " English",
                () -> assertThat(said).contains("deliverableLanguage is \"" + A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN
                        + "\", which is not a language the deliverable can be drawn in; write en or it, or leave the"
                        + " key empty to mean en"));
        claim(
                "the scoring before the arrangement was done in this invocation, so it stopped at the arrangement",
                () -> assertThat(runsOf(root, "embedding-scoring")).hasSize(1));
        claim(
                "no topic was asked for its name and no group was asked about",
                () -> assertThat(List.of(GenerationScriptedBeans.namingCallsMade(), GenerationScriptedBeans.callsMade()))
                        .containsExactly(0, 0));
        claim(
                "and no arrangement was recorded, since there is no language to name its topics in",
                () -> assertThat(runsOf(root, "arrangement")).isEmpty());
    }

    /**
     * The language read once in an invocation and kept feeds 6b's writing as it feeds the arrangement (ADR-407 §3,
     * ADR-321 §1): with {@code arrangementApproved} {@value ArrangementGate#AUTOMATIC}, a language saved after the
     * arrangement run is minted and before any cluster is asked about does not part the language the generation run
     * records from the one every cluster is asked for in and every page is drawn in.
     */
    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("A language saved while the writing model is checked does not part the language the groups are asked for and drawn in from the one the writing records")
    @Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
    void theClustersAreWrittenInTheLanguageTheGenerationRecords(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        EveryGateOpen.approve(profileStore, ArrangementGate.AUTOMATIC, THE_WRITING_MODEL);
        // saved as the writing step checks its model: after the arrangement was recorded and its topics named, before
        // the writing is recorded and before any group is asked about, as an operator saving the profile part-way
        // through the invocation
        EmbeddingScriptedBeans.whenAskedAbout(THE_WRITING_MODEL, () -> setTheLanguageTo(ITALIAN));

        cli.run("run", root.toString());
        List<String> runs = generationRuns(root);

        claim("the invocation reports success, approving the arrangement unread and writing the groups",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("the writing was recorded once", () -> assertThat(runs).hasSize(1));
        DeliverableLanguage recorded = DeliverableLanguage.ofCode(recordedLanguageOf(runs.getFirst())).orElseThrow();
        claim(
                "every group was asked for in the language that writing records, " + recorded.englishName()
                        + ": the pages it stands behind are in it",
                () -> assertThat(GenerationScriptedBeans.promptsSent()).isNotEmpty().allSatisfy(question ->
                        assertThat(question).contains("Write both in " + recorded.englishName() + ".")));
        claim(
                "and every page the writing drew names that language, " + recorded.code(),
                () -> assertThat(pagesOf(workingDirectory.resolve("deliverable").resolve(runs.getFirst())))
                        .isNotEmpty()
                        .allSatisfy(page -> assertThat(Files.readString(page))
                                .startsWith("<!DOCTYPE html>\n<html lang=\"" + recorded.code() + "\">\n")));
    }

    @BeforeEach
    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    /** Every HTML page under {@code tree}. */
    private static List<Path> pagesOf(Path tree) throws IOException {
        try (Stream<Path> files = Files.walk(tree)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".html")).sorted().toList();
        }
    }

    /** The language code {@code run}'s recorded settings name as its {@code deliverableLanguage} member. */
    private String recordedLanguageOf(String run) {
        Matcher member = THE_RECORDED_LANGUAGE.matcher(settingsOf(run));
        assertThat(member.find()).as("the writing's settings name a deliverableLanguage").isTrue();
        return member.group(1);
    }

    /**
     * Two corpus documents and one exemplar with every gate before 6b open, taken through the invocation that stops at
     * the arrangement gate, and that arrangement approved.
     */
    private void aCorpusUpToTheApprovedArrangement(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
    }

    /**
     * One invocation over {@code root} after the language changed to {@code language}, which stops at the arrangement
     * gate, the arrangement in that language approved, and the invocation that writes the groups.
     *
     * <p>Two invocations and not one because the language is a member of the arrangement run's identity (ADR-407 §3):
     * a change of language mints another arrangement, which the approval given for the first does not name. Back to
     * English, the arrangement is the first one again, found rather than minted, so it is picked out by the language
     * it records and not as the latest; the approval names the Italian one by then, so it too is approved again.
     */
    private void runApprovingTheArrangementIn(Path root, String language) {
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theArrangementIn(root, language)), "read by this test")
                .build());
        cli.run("run", root.toString());
    }

    /** The one arrangement over {@code root} whose recorded settings name {@code language}. */
    private RunId theArrangementIn(Path root, String language) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.config_consumed LIKE ?",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                "%,\"deliverableLanguage\":\"" + language + "\"}"));
    }

    /** Writes {@code deliverableLanguage}, leaving every other key as it was. */
    private void setTheLanguageTo(String language) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .deliverableLanguage(language, language == null ? null : "set by this test")
                .build());
    }

    /** The settings the run is identified by, as recorded. */
    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunsOver(jdbcTemplate, root).latest("arrangement");
    }

    /** Every run of {@code stage} over {@code root}, oldest first. */
    private List<String> runsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }

    /** Every generation run over {@code root}, oldest first, scoped to its walk as {@link GenerationInvocationTest} scopes it. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
