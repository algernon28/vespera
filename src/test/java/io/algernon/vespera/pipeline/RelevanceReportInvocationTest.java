package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
/**
 * Stage 5's last step, driven through a whole invocation (ADR-088, #110): the page and the label
 * file have to exist where the operator will look for them, which is the one claim the rendering
 * tests beside this class cannot make.
 *
 * <p>A sibling of {@code RelevanceScoringInvocationTest}, on the same scripted extraction and
 * embedding beans, so the whole pipeline runs here without a Docker daemon. What the real sidecars
 * add is checked separately by {@code RelevanceReportIT}.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceReportInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /**
     * The logger a failed step's exception is written through: Spring Batch logs it, with its cause
     * chain, when a step ends in error, and the application does not log it a second time.
     */
    private static final String BATCH_LOGGER = "org.springframework.batch";

    /** A floor of 1.0 opens stage 4's gate, the way the sibling invocation tests do. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The corpus document the tests below keep from being opened, or change underneath the run. */
    private static final String HELD_DOCUMENT = "held-document.txt";

    /** The corpus document left alone, so the page has one preview that should still be shown. */
    private static final String OPEN_DOCUMENT = "open-document.txt";

    /**
     * How every line the page shows in place of a document's opening ends, whatever its reason: the one
     * ADR-206 section 5 keeps, and the two of ADR-152 it withdrew or reworded. A page holding none of
     * these words replaced no opening.
     */
    private static final String AN_OPENING_NOT_SHOWN = "its opening is not shown";

    /**
     * What the page shows in place of a document's opening when no conversion is on record under the key
     * recorded for it (ADR-206 section 5), word for word: the page is what a person reads, so the wording
     * is the contract.
     */
    private static final String NO_CONVERSION_ON_RECORD =
            "(no conversion is on record for this document, so its opening is not shown)";

    /** One of the two documents: the one whose conversion a test takes away, or the one left. */
    private static final int ONE_DOCUMENT = 1;

    /** The corpus {@link #aCorpusOfTwoDocuments} writes, so the page has this many openings to show. */
    private static final int TWO_DOCUMENTS = 2;

    /**
     * The steps between conversion and the arrangement page that record their completion and so keep
     * what they finished: each read every document's file again until ADR-206.
     */
    private static final List<String> THE_STEPS_AFTER_CONVERSION = List.of(
            StepNames.EMBEDDING_SCORING, StepNames.RELEVANCE_SCORING, StepNames.CLUSTERING, StepNames.ARRANGEMENT);

    /**
     * The words every scripted conversion carries after its title, and nothing else on the page does,
     * so finding them means a document's opening was shown.
     */
    private static final String A_SHOWN_OPENING = "stubbed but real content";

    /** One walk of one corpus folder: the archive looked the same to the second invocation as to the first. */
    private static final int ONE_WALK = 1;

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A shut seed gate gates this step, as it gates every other step in stage 5")
    @DisplayName("Stage 4's gate open and a model named, with no seed folder, still reports success")
    void aModelNamedWithNoSeedFolderIsGatedRatherThanFatal(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        theFloorAndTheModelButNoSeedFolder();
        whateverAnotherTestLeftHere();

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: nothing here is a failure -- the operator has answered"
                        + " two of the three values a run wants and not the third, which is the state"
                        + " every other step in stage 5 reports as gated and exits 0 on",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and this step says it was gated, in the same sentence its siblings use, rather than"
                        + " resolving a scoring run that cannot exist while the seed gate is shut",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains("relevance-report step is gated")
                                && line.contains("no seed folder is named")));
        claim(
                "nothing was put to a person, so no page and no label file were written -- there is no"
                        + " seed set for a question to be about",
                () -> assertThat(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME))
                        .doesNotExist());
    }

    @Test
    @Story("A seed folder naming nothing is census's finding, not this step's failure")
    @DisplayName("A seed folder that is not there is gated rather than fatal")
    void aSeedFolderThatIsNotThereIsGatedRatherThanFatal(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        profile(seeds.resolve("not-here"));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, because census already recorded why it could not walk"
                        + " the folder and carried on (ADR-064) -- turning that into a failed invocation"
                        + " two steps later reports one typo as two different kinds of problem",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the step is gated, reached by the same route: a seed folder that resolves to"
                        + " nothing leaves the seed gate shut, which is what SeedGate already decided",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains("relevance-report step is gated")));
    }

    @Test
    @Story("The two files land where the operator will look for them")
    @DisplayName("A whole invocation leaves the page and the label file beside the database, and inside no corpus")
    void leavesBothFilesBesideTheDatabase(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page a person reads before choosing a cut is written where the database and the"
                        + " profile already live, which is the one place an operator has been told to look",
                () -> assertThat(workingDirectory.resolve("relevance-labelling.html")).exists());
        claim(
                "and so is the file they write their answers into, beside the page that poses the"
                        + " questions rather than somewhere they have to be told about separately",
                () -> assertThat(workingDirectory.resolve("relevance-labels.yaml")).exists());
        claim(
                "and neither is written into the corpus or the seed folder: both hold exactly the files"
                        + " this test put in them, because a curation tool that leaves its own paperwork"
                        + " among the documents has changed the thing it was asked to describe",
                () -> assertThat(filesUnder(root, seeds))
                        .containsExactlyInAnyOrder(root.resolve("corpus.txt"), seeds.resolve("seed.txt")));
    }

    @Test
    @Story("The two files land where the operator will look for them")
    @DisplayName("The page reports the spread and the label file names the run, so the two can be matched later")
    void thePageAndTheLabelFileAgreeOnWhatTheyWereGeneratedFrom(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String page = Files.readString(workingDirectory.resolve("relevance-labelling.html"));
        String labels = Files.readString(workingDirectory.resolve("relevance-labels.yaml"));
        claim(
                "the page shows the bands it cut, so a reader can see the shape rather than be handed a"
                        + " verdict about it",
                () -> assertThat(page).contains("Band 1"));
        claim(
                "the label file names the run it was generated under, which is what lets a completed file"
                        + " offered against a different sample be refused rather than partially matched",
                () -> assertThat(labels).contains("generatedUnderRun"));
        claim(
                "and it asks about the document that was scored, with the answer left blank for a person",
                () -> assertThat(labels).contains("corpus.txt").contains("relevant:"));
    }

    @Test
    @Story("The threshold is pointed at, never answered")
    @DisplayName("The profile gains a pointer to the page and no threshold value")
    void pointsTheThresholdKeyAtThePageWithoutAnsweringIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        Profile profile = profileStore.load();
        claim(
                "the threshold key names the page its value is meant to be read off, so an operator"
                        + " opening the profile is told where the number comes from",
                () -> assertThat(profile.relevanceScoreFloor().measurement().source())
                        .isEqualTo("relevance-labelling.html"));
        claim(
                "and the value itself is still unanswered: a threshold removes documents, and nothing"
                        + " here may choose one on a person's behalf",
                () -> assertThat(profile.relevanceScoreFloor().isSet()).isFalse());
    }

    /**
     * One sampled document's file will not open on the invocation after the one that scored it
     * (ADR-206 section 5, amending ADR-152 section 1; #289, #349).
     *
     * <p>The walk lists files without opening them, so a held file is the same observation as before:
     * the earlier walk is kept, every step that recorded its completion is walked past, and the page
     * -- which records none (ADR-118) -- is written again. Until ADR-206 the page read the file again to
     * find its conversion, and showed a stated fallback where it could not. It now reads the key stage 2
     * recorded, so it shows the opening and has nothing to warn about. The claim about one walk says the
     * state was reproduced rather than assuming it.
     *
     * <p><b>Guarded, and it says so.</b> On Windows the file is held by a lock over all of it, which
     * Windows enforces against every other handle, this process's included. Elsewhere every
     * permission is taken off it, which a superuser reads through, so where the file can still be
     * read the test aborts by assumption rather than claiming something it did not show.
     *
     * <p>Moved by ADR-206 from {@code aSampledDocumentWhoseFileWillNotOpenIsStillAskedAbout}: the
     * document is still asked about, and what changed is that its opening is shown too.
     */
    @Test
    @Issue("289")
    @Issue("349")
    @Story("A document is shown as it was converted, whatever has become of its file")
    @DisplayName("A sampled document whose file cannot be opened is still asked about, with its opening shown")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aSampledDocumentWhoseFileWillNotOpenIsStillShown(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        claim(
                "before anything is held, the first run succeeds and asks about both documents, so what"
                        + " follows is about the second run alone",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(labelFile()).contains(HELD_DOCUMENT).contains(OPEN_DOCUMENT);
                });
        whateverAnotherTestLeftHere();
        logged.list.clear();

        try (FileThatWillNotOpen held = FileThatWillNotOpen.hold(root.resolve(HELD_DOCUMENT))) {
            assumeTrue(
                    held.refusesToOpen(),
                    "this environment reads a file with every permission taken off it, as a superuser"
                            + " does, so no file can be kept from opening here");
            cli.run("run", root.toString());
        }

        claim(
                "the archive looked the same to the second run as to the first: a file that will not open"
                        + " is still listed with the same size and time, so nothing was measured again",
                () -> assertThat(walksOf(root)).isEqualTo(ONE_WALK));
        claim(
                "the run reports success: the page is written from what was converted, and a file that"
                        + " cannot be opened today takes nothing from that",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page and the answer file were both written by this run, not left over from the last",
                () -> {
                    assertThat(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME)).exists();
                    assertThat(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME)).exists();
                });
        claim(
                "the document is still asked about: its score came from the text read when it was first"
                        + " converted, and a person's answer is about the document, not about today's file",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
        claim(
                "the openings of both of the " + TWO_DOCUMENTS + " documents are shown, the held one's"
                        + " among them: the page finds each conversion by the key recorded when the"
                        + " document was converted, and does not open the file to find it",
                () -> assertThat(openingsShownOn(page())).isEqualTo(TWO_DOCUMENTS));
        claim(
                "and no document's opening is replaced by a line saying it is not shown",
                () -> assertThat(page()).doesNotContain(AN_OPENING_NOT_SHOWN));
        claim(
                "no warning names the held file: nothing went missing from the page, so there is nothing"
                        + " for the operator to release or look at again",
                () -> assertThat(logged.list)
                        .noneMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains(HELD_DOCUMENT)));
        claim(
                "and the run went on past the page to the arrangement step, rather than ending there",
                () -> assertThat(operatorLines()).anyMatch(line -> line.contains("the arrangement step")));
    }

    /**
     * A sampled document's bytes change after it was converted, and nothing a directory listing shows
     * changes with them (ADR-206 section 5, amending ADR-152 section 3).
     *
     * <p>The page reads the conversions already on record and makes none: a conversion here would be a
     * call to the document converter to fill a preview, of bytes no score was computed from. Until
     * ADR-206 it looked the conversion up under the hash of the bytes as they are now, found nothing and
     * said so. It now looks it up under the key stage 2 recorded, so it shows what the score was computed
     * from. Unlike the test above this needs nothing the environment can refuse, so it runs everywhere.
     *
     * <p>Moved by ADR-206 from {@code aDocumentWhoseBytesChangedIsNotConvertedAgainForThePage}: no
     * conversion is added, as before, and the page now shows the opening where it showed a fallback.
     */
    @Test
    @Issue("289")
    @Issue("349")
    @Story("A document is shown as it was converted, whatever has become of its file")
    @DisplayName("A document whose bytes changed since it was converted is shown as converted, and not converted again")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aDocumentWhoseBytesChangedIsShownAsItWasConverted(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        int conversionsBefore = conversionsOnRecord();
        whateverAnotherTestLeftHere();
        logged.list.clear();

        theBytesChangeAndTheListingDoesNot(root.resolve(HELD_DOCUMENT));
        cli.run("run", root.toString());

        claim(
                "the archive looked the same to the second run as to the first: the file kept its size and"
                        + " its time, so nothing was measured again",
                () -> assertThat(walksOf(root)).isEqualTo(ONE_WALK));
        claim("the run reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "no conversion was added: the page shows what was converted, and a changed file is not"
                        + " sent to the converter to fill a preview",
                () -> assertThat(conversionsOnRecord()).isEqualTo(conversionsBefore));
        claim(
                "and the document is still asked about, because the sample is the same whatever the file"
                        + " holds today",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
        claim(
                "the openings of both of the " + TWO_DOCUMENTS + " documents are shown: the changed"
                        + " document's is the opening of the text its score was computed from, found by"
                        + " the key recorded when it was converted and not by what the file holds now",
                () -> assertThat(openingsShownOn(page())).isEqualTo(TWO_DOCUMENTS));
        claim(
                "and no document's opening is replaced by a line saying it is not shown",
                () -> assertThat(page()).doesNotContain(AN_OPENING_NOT_SHOWN));
        claim(
                "no warning names the changed file: the page had its opening to show",
                () -> assertThat(logged.list)
                        .noneMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains(HELD_DOCUMENT)));
    }

    /**
     * Scoring, clustering and the arrangement finish over a document whose file will not open, because
     * none of them opens it (ADR-206 sections 4 and 5, amending ADR-152 section 4).
     *
     * <p>The first invocation names no embedding model, so stage 2 converts both documents and records
     * their keys, and every step of stage 5 is gated and still unfinished. The second names one with the
     * file held. Until ADR-206 each of those steps hashed the file again, so the first of them, the
     * embedding step, failed the run on it. Guarded as the first test here is, and for the same reason.
     *
     * <p>Moved by ADR-206 from {@code aStepThatRecordsItsCompletionStopsOnAFileThatWillNotOpen}, whose
     * claims are inverted: the run that failed now finishes, so no third run is needed to finish it.
     */
    @Test
    @Issue("289")
    @Issue("349")
    @Story("A step after conversion works from what was converted and never opens the file")
    @DisplayName("Scoring, grouping and the arrangement finish over a document whose file cannot be opened")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void everyStepAfterConversionFinishesOverAFileThatWillNotOpen(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        theFloorAndTheSeedFolderButNoModel(seeds);
        cli.run("run", root.toString());
        claim(
                "the precondition: with no model named, no step that scores, groups or arranges has"
                        + " finished, so each of them still has all its work to do when the file is held",
                () -> assertThat(stepsFinishedOver(root)).doesNotContainAnyElementsOf(THE_STEPS_AFTER_CONVERSION));
        whateverAnotherTestLeftHere();
        logged.list.clear();

        int heldExitCode;
        ListAppender<ILoggingEvent> stepFailures = new ListAppender<>();
        ch.qos.logback.classic.Logger batchLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BATCH_LOGGER);
        stepFailures.start();
        batchLogger.addAppender(stepFailures);
        try (FileThatWillNotOpen held = FileThatWillNotOpen.hold(root.resolve(HELD_DOCUMENT))) {
            assumeTrue(
                    held.refusesToOpen(),
                    "this environment reads a file with every permission taken off it, as a superuser"
                            + " does, so no file can be kept from opening here");
            profile(seeds);
            cli.run("run", root.toString());
            heldExitCode = cli.getExitCode();
        } finally {
            batchLogger.detachAppender(stepFailures);
            stepFailures.stop();
        }

        claim(
                "the archive looked the same to the run that met the held file as to the one before it",
                () -> assertThat(walksOf(root)).isEqualTo(ONE_WALK));
        claim(
                "while the file was held the run succeeded: every step after conversion reads what was"
                        + " converted, so none of them has a file to fail on",
                () -> assertThat(heldExitCode).isZero());
        claim(
                "no step failed: nothing was logged as a step's error",
                () -> assertThat(stepFailures.list).noneMatch(event -> event.getLevel() == Level.ERROR));
        claim(
                "each of the " + THE_STEPS_AFTER_CONVERSION.size() + " steps that keep their finished work"
                        + " -- embedding, scoring, grouping and the arrangement -- recorded that it"
                        + " finished, in that run, over both documents",
                () -> assertThat(stepsFinishedOver(root)).containsAll(THE_STEPS_AFTER_CONVERSION));
        claim(
                "the page asks about the held document and shows its opening with the other's, "
                        + TWO_DOCUMENTS + " in all",
                () -> {
                    assertThat(labelFile()).contains(HELD_DOCUMENT);
                    assertThat(openingsShownOn(page())).isEqualTo(TWO_DOCUMENTS);
                });
        claim(
                "and no warning names the held file",
                () -> assertThat(logged.list)
                        .noneMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains(HELD_DOCUMENT)));
    }

    /**
     * A sampled document whose conversion is no longer on record under the key recorded for it
     * (ADR-206 section 5, amending ADR-152 section 3).
     *
     * <p>No run leaves a database in this state: a conversion is always kept, and under one run the key
     * and the cache agree. It is reached here by taking the document's rows out of the caches between
     * two invocations. What it pins is the one line the page still shows in place of an opening, word for
     * word, and the warning beside it. The page is what a person reads, so the wording is the contract.
     */
    @Test
    @Issue("349")
    @Story("A document is shown as it was converted, whatever has become of its file")
    @DisplayName("A sampled document with no conversion on record is still asked about, and the page says why its opening is missing")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aSampledDocumentWithNoConversionOnRecordSaysSo(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        whateverAnotherTestLeftHere();
        logged.list.clear();

        ConversionOffTheRecordFixture.Held offTheRecord =
                ConversionOffTheRecordFixture.takenOffTheRecord(jdbcTemplate, root.resolve(HELD_DOCUMENT));
        cli.run("run", root.toString());

        claim(
                "the run reports success: a page for a person to read is written from what is on record,"
                        + " and one document with nothing on record costs its own preview",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the document is still asked about, since the sample is the same whatever is on record",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
        claim(
                "the page says, in these words, that no conversion is on record for the document, which"
                        + " is a different reason from a document that had no text",
                () -> assertThat(page()).contains(NO_CONVERSION_ON_RECORD));
        claim(
                "the other document's opening is still shown, " + ONE_DOCUMENT + " in all",
                () -> assertThat(openingsShownOn(page())).isEqualTo(ONE_DOCUMENT));
        claim(
                "a warning says the document carries no conversion under the key recorded for it, and"
                        + " gives that key, the SHA-256 of the document's bytes, so the operator can find"
                        + " which document the page could not show",
                () -> assertThat(logged.list)
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains("carries no cached conversion under the key")
                                && event.getFormattedMessage().contains(offTheRecord.contentHash())));
        claim(
                "and no conversion was made to fill the preview",
                () -> assertThat(conversionsOnRecordUnder(offTheRecord.contentHash())).isZero());
    }

    /**
     * The embedding step meets a document whose conversion is not on record under its recorded key, and
     * stops (ADR-206 section 4).
     *
     * <p>The first invocation names no embedding model, so stage 2 finishes and the embedding step is
     * gated. The document's conversion is then taken out of the cache, a state no run leaves, and the
     * second invocation names a model. The step converts nothing: converting would need the file, and
     * would keep whatever the file holds now under the hash of what it held when it was converted.
     */
    @Test
    @Issue("349")
    @Story("A step after conversion works from what was converted and never opens the file")
    @DisplayName("The embedding step stops on a document with no conversion on record, naming the document, its key and the converter")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    void theEmbeddingStepStopsOnADocumentWithNoConversionOnRecord(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        theFloorAndTheSeedFolderButNoModel(seeds);
        cli.run("run", root.toString());
        String theConverter = jdbcTemplate.queryForObject(
                "SELECT DISTINCT extractor_identity FROM extraction_cache", String.class);
        long theDocument = jdbcTemplate.queryForObject(
                "SELECT fo.id FROM file_occurrence fo JOIN walk w ON w.id = fo.walk_id WHERE w.root = ? AND fo.path = ?",
                Long.class,
                Walk.canonicalRoot(root).toString(),
                HELD_DOCUMENT);
        int conversionsBefore = conversionsOnRecord();
        ConversionOffTheRecordFixture.Held offTheRecord =
                ConversionOffTheRecordFixture.takenOffTheRecord(jdbcTemplate, root.resolve(HELD_DOCUMENT));

        ListAppender<ILoggingEvent> stepFailures = new ListAppender<>();
        ch.qos.logback.classic.Logger batchLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BATCH_LOGGER);
        stepFailures.start();
        batchLogger.addAppender(stepFailures);
        try {
            profile(seeds);
            cli.run("run", root.toString());
        } finally {
            batchLogger.detachAppender(stepFailures);
            stepFailures.stop();
        }

        claim(
                "the run fails: the embedding step keeps what it finishes, so going on without the"
                        + " document would leave it unembedded under work recorded as complete",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the error names the document by its number in the record, the key recorded for it and"
                        + " the converter the conversion was looked for under, which is everything needed"
                        + " to see what is missing",
                () -> assertThat(stepFailures.list)
                        .anyMatch(event -> event.getLevel() == Level.ERROR
                                && anyCauseHolds(
                                        event.getThrowableProxy(),
                                        "occurrence " + theDocument,
                                        offTheRecord.contentHash(),
                                        theConverter)));
        claim(
                "and nothing was converted to get past it: one conversion fewer is on record than before"
                        + " the document's was taken away, and none was added",
                () -> assertThat(conversionsOnRecord()).isEqualTo(conversionsBefore - ONE_DOCUMENT));
    }

    /** Whether {@code thrown}, or a cause beneath it, has a message holding every one of {@code parts}. */
    private static boolean anyCauseHolds(ch.qos.logback.classic.spi.IThrowableProxy thrown, String... parts) {
        for (ch.qos.logback.classic.spi.IThrowableProxy cause = thrown; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && java.util.Arrays.stream(parts).allMatch(message::contains)) {
                return true;
            }
        }
        return false;
    }

    /** How many conversions are on record under {@code contentHash}, under any converter. */
    private int conversionsOnRecordUnder(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_cache WHERE content_hash = ?", Integer.class, contentHash);
    }

    /** How many times the words every scripted conversion carries appear on {@code page}. */
    private static int openingsShownOn(String page) {
        int shown = 0;
        for (int at = page.indexOf(A_SHOWN_OPENING); at >= 0; at = page.indexOf(A_SHOWN_OPENING, at + 1)) {
            shown++;
        }
        return shown;
    }

    /** The name of every step recorded as finished under a run over {@code root}. */
    private List<String> stepsFinishedOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT f.step FROM finished_step f JOIN run r ON r.id = f.run_id"
                        + " JOIN walk w ON w.id = r.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /** Two corpus documents that convert alike and a seed, so the page has two previews to show. */
    private static void aCorpusOfTwoDocuments(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(HELD_DOCUMENT), "a corpus document");
        Files.writeString(root.resolve(OPEN_DOCUMENT), "a second corpus document, worded differently");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /**
     * Rewrites a file's bytes in place with the same length, and puts its modified time back, so a
     * directory listing shows exactly what it showed before. Its creation time is untouched by a
     * rewrite in place.
     */
    private static void theBytesChangeAndTheListingDoesNot(Path file) throws IOException {
        FileTime modified = Files.getLastModifiedTime(file);
        String before = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, before.toUpperCase(Locale.ROOT), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, modified);
    }

    /** How many walks of {@code root} are on record. */
    private int walksOf(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
    }

    /** How many conversions the extraction cache holds, under any instrument. */
    private int conversionsOnRecord() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM extraction_cache", Integer.class);
    }

    /** The labelling page as this run wrote it, or nothing where it wrote none. */
    private String page() throws IOException {
        Path page = workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME);
        return Files.exists(page) ? Files.readString(page) : "";
    }

    /** The answer file as this run wrote it, or nothing where it wrote none. */
    private String labelFile() throws IOException {
        Path labels = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        return Files.exists(labels) ? Files.readString(labels) : "";
    }

    /** The seed folder named and stage 4's gate open, with no embedding model, so stage 5 is gated. */
    private void theFloorAndTheSeedFolderButNoModel(Path seeds) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(loaded.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    /**
     * Keeps one file from being opened for as long as it is held, the way a program the archive's owner
     * has open can.
     *
     * <p>On Windows, an exclusive lock over the whole file through a handle of its own: Windows refuses
     * a read through any other handle, this process's included. Elsewhere, every permission is taken
     * off the file and put back on release, which refuses everyone but a superuser. {@link
     * #refusesToOpen} is what the caller checks before claiming anything.
     */
    private static final class FileThatWillNotOpen implements AutoCloseable {

        private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

        private final Path file;
        private final FileChannel lockingChannel;
        private final Set<PosixFilePermission> permissionsBefore;

        private FileThatWillNotOpen(
                Path file, FileChannel lockingChannel, Set<PosixFilePermission> permissionsBefore) {
            this.file = file;
            this.lockingChannel = lockingChannel;
            this.permissionsBefore = permissionsBefore;
        }

        static FileThatWillNotOpen hold(Path file) throws IOException {
            if (WINDOWS) {
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                channel.lock();
                return new FileThatWillNotOpen(file, channel, null);
            }
            Set<PosixFilePermission> before = Files.getPosixFilePermissions(file);
            Files.setPosixFilePermissions(file, EnumSet.noneOf(PosixFilePermission.class));
            return new FileThatWillNotOpen(file, null, before);
        }

        /** Whether reading the file actually fails here, which is the premise every claim rests on. */
        boolean refusesToOpen() {
            try (InputStream in = Files.newInputStream(file)) {
                in.read();
                return false;
            } catch (IOException refused) {
                return true;
            }
        }

        /** Releases the lock, or puts the permissions back, so the file can be read and cleaned up. */
        @Override
        public void close() throws IOException {
            if (lockingChannel != null) {
                lockingChannel.close();
            } else {
                Files.setPosixFilePermissions(file, permissionsBefore);
            }
        }
    }

    /** Every file under either folder, so the claim about leaving nothing behind can be made. */
    private static java.util.List<Path> filesUnder(Path... folders) throws IOException {
        java.util.List<Path> found = new java.util.ArrayList<>();
        for (Path folder : folders) {
            try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
                walk.filter(Files::isRegularFile).forEach(found::add);
            }
        }
        return found;
    }

    /**
     * Stage 4's gate open and a model named, with no seed folder -- ADR-098's invocation 2 for an
     * operator who never took step zero.
     */
    private void theFloorAndTheModelButNoSeedFolder() {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(loaded.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the model gate is open")
                .build());
    }

    /**
     * Clears the two files this step writes, so a claim about them is about this invocation.
     *
     * <p>The working directory is static, so it outlives each test method -- a page left by the test
     * that asserts it gets written would otherwise satisfy a claim that this invocation wrote none.
     */
    private void whateverAnotherTestLeftHere() throws IOException {
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }
}
