package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Deliverable;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

/**
 * What an invocation of stage 6b actually leaves on disk (ADR-103, ADR-104, ADR-111, ADR-112, #186):
 * a tree named for the run that wrote it, the index that opens it, the listing beside it, and the
 * line that tells the operator both are there.
 *
 * <p>Driven end to end rather than against the writer, because every claim here is about something
 * the writer is handed rather than something it decides: which run the tree is named for, which
 * values the index states, which documents the listing carries, and whether a second invocation
 * writes beside the first or over it. {@code DeliverableTest} holds the claims about the rendering
 * itself, including the two that no invocation can reach.
 *
 * <p><b>Every claim here is scoped to the run this method minted.</b> One static working directory
 * serves the whole class, so a tree written by an earlier method is still on disk when the next one
 * runs — and because the tree is named for the run, "this invocation wrote no tree" is a claim that
 * passes on somebody else's. Two devices keep that honest: the trees this corpus produced are found
 * through the run ids recorded against this corpus's own walk, and the one claim that is genuinely
 * about absence compares the whole directory before and after rather than looking for nothing.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The tree is
 * one of that record's outside-facing surfaces, and so is every word of this report.
 */
@CascadeSliceTest
@Import({ClusterFaults.class, SeedScriptedExtractionBeans.class})
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("186")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class DeliverableInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /**
     * A relevance floor of zero, which removes nothing and leaves the corpus as it was.
     *
     * <p>Answered by the fixture because the closing line names the value an operator still owes, and
     * a threshold nobody answered is owed before anything about a tree is: without it the last line
     * of every invocation here would be about labelling, and two tests would claim against a sentence
     * the operator never reaches on the path this stage sits at the end of.
     */
    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    /** How much every call in this class may read, stated so the profile cannot carry another's. */
    private static final String THE_READING_WINDOW = "4096";

    /** A wider window: a different reading of the same archive, and so a different piece of work. */
    private static final String A_WIDER_WINDOW = "16384";

    /** What one archive, read one way, is worth: one piece of work, and one tree named after it. */
    private static final int ONE_RUN = 1;

    /** What the same archive read two ways is worth: two pieces of work, and two trees side by side. */
    private static final int TWO_RUNS = 2;

    /** The two documents this fixture's corpus holds, which are the two the listing has to carry. */
    private static final int TWO_DOCUMENTS = 2;

    /** How many clusters the one seed of this fixture breaks into once a second is put beside it. */
    private static final int TWO_CLUSTERS = 2;

    /** And how many this corpus arranges on its own, which is how many pages its tree holds. */
    private static final int ONE_CLUSTER = 1;

    /** What a cluster left unwritten costs the tree: nothing but its own page. */
    private static final int ONE_CLUSTER_LEFT_UNWRITTEN = 1;

    /** What an invocation that believed every answer leaves unwritten. */
    private static final int NO_CLUSTER_LEFT_UNWRITTEN = 0;

    /** How long a run's name is, in characters, which is what the tree's directory is named with. */
    private static final int THE_WHOLE_RUN_NAME = 64;

    /**
     * The label given to the second cluster, so the claims below can name it plainly.
     *
     * <p>The value says <em>group</em> and the constant naming it says cluster, which is the rule
     * rather than an oversight (ADR-122): this stands in for a label an operator reads, so the fixture
     * reads as the thing it fakes, while the name is one of ours.
     */
    private static final String THE_CLUSTER_PLACED_FIRST = "A group placed ahead of the other";

    /** An answer nothing can read back into a heading and its writing, so its cluster is left unwritten. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /** What the corpus documents of this fixture hold, so a copy of one is recognisable as a copy. */
    private static final String WHAT_A_CORPUS_DOCUMENT_SAYS = "a corpus document";

    /** And what the second holds. */
    private static final String WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS = "a second corpus document";

    /**
     * A bracketed ordinal not immediately followed by {@code (}, which is a citation the page failed to
     * rewrite (ADR-109): a link's own {@code [n]} is followed by its destination and so is not one.
     */
    private static final Pattern A_RAW_CITATION = Pattern.compile("\\[\\d+\\](?!\\()");

    /**
     * The destination of one membership entry, captured the way a renderer reads one: everything
     * between the parenthesis that opens the destination and the one that closes it. The link text is
     * skipped over rather than matched loosely, because a document's own name may carry an escaped
     * bracket and a pattern stopping at the first {@code ]} would read half a name as a destination.
     */
    private static final Pattern A_MEMBERSHIP_DESTINATION =
            Pattern.compile("\\d+\\. <a id=\"document-\\d+\"></a>\\[(?:\\\\.|[^\\]])*\\]\\(([^)]*)\\)");

    /** Every key the profile carries, each of which the index states beside the value it held. */
    private static final List<String> EVERY_PROFILE_KEY = List.of(
            "seedFolder",
            "degenerateOutputConfidenceFloor",
            "boilerplateDocumentFrequencyFloor",
            "embeddingModel",
            "relevanceScoreFloor",
            "arrangementApproved",
            "generationModel",
            "generationContextWindow");

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /**
     * Drops what an earlier class scripted and what it counted, before each test as well as after it.
     *
     * <p>The count and the script are static on a fixture eighteen classes in this package share, so
     * clearing only afterwards leaves this class trusting whichever class ran before it to have done
     * the same — and class order is not a thing any test here decides.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

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
    @Story("The operator is handed a tree named after the work that produced it")
    @DisplayName("An invocation that writes over the groups leaves a tree beside the database, named for itself")
    void writesATreeNamedForTheRunItJustMinted(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "exactly " + ONE_RUN + " piece of work stands behind this tree, which is what the name"
                        + " below is taken from -- asserted first, so an invocation that wrote nothing"
                        + " fails saying so rather than while reaching for a name that is not there",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN));
        claim(
                "the tree is named with the whole name of the work that produced it, all "
                        + THE_WHOLE_RUN_NAME + " characters: the name is worked out from what that work"
                        + " read, so it carries its own provenance and no two pieces of work can land on"
                        + " one path by accident",
                () -> {
                    assertThat(generationRuns(root).getFirst()).hasSize(THE_WHOLE_RUN_NAME);
                    assertThat(theTreeOf(root)).isDirectory();
                });
        claim(
                "and it sits inside the directory the database is in, which the operator set nothing new"
                        + " to choose: one setting moves everything this tool writes, and a second place"
                        + " to put things would be another value to supply on a path kept deliberately"
                        + " short",
                () -> assertThat(theTreeOf(root)).startsWithRaw(workingDirectory));
        claim(
                "the index and the listing are both at its root, so what a person opens and what a"
                        + " program loads are where the tree says they are",
                () -> {
                    assertThat(theTreeOf(root).resolve(Deliverable.INDEX_FILE_NAME)).isRegularFile();
                    assertThat(theTreeOf(root).resolve(Deliverable.MANIFEST_FILE_NAME)).isRegularFile();
                });
    }

    @Test
    @Story("The operator is handed a tree named after the work that produced it")
    @DisplayName("With nothing approved, the invocation leaves every tree on disk exactly as it found it")
    void writesNoTreeWhenNothingIsApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(null);
        List<String> before = everyTreeOnDisk();

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded: a person who has not yet read the arrangement has done nothing"
                        + " wrong",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the trees on disk are exactly the ones that were there before it -- compared before"
                        + " and after rather than counted, because one working directory serves every test"
                        + " in this class and a claim that simply looked for nothing would be satisfied by"
                        + " another test's tree just as well as by this one having written none",
                () -> assertThat(everyTreeOnDisk()).isEqualTo(before));
    }

    @Test
    @Story("Running again changes nothing, and changing something writes beside what is there")
    @DisplayName("An invocation that changed nothing writes the same tree again rather than a second beside it")
    void writesTheSamePathWhenNothingChanged(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        cli.run("run", root.toString());
        Path first = theTreeOf(root);

        cli.run("run", root.toString());

        claim(
                "the second invocation reports success, having met its own earlier work rather than"
                        + " fallen over it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "both invocations worked under " + ONE_RUN + " name, because a name is worked out from"
                        + " what the work read and nothing either of them read had changed: this is a"
                        + " claim about what the work is called, and the path follows from it",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN));
        claim(
                "so the second wrote the very path the first did, rather than a near-copy beside it:"
                        + " running again is meant to leave the operator with what they already had, not"
                        + " with two trees they have to tell apart by timestamp",
                () -> assertThat(theTreeOf(root)).isEqualTo(first));
        claim(
                "and this corpus has " + ONE_RUN + " tree in all",
                () -> assertThat(treesOf(root)).hasSize(ONE_RUN));
    }

    @Test
    @Story("Running again changes nothing, and changing something writes beside what is there")
    @DisplayName("Reading more of each group writes a new tree beside the old, and leaves the old one whole")
    void writesANewTreeBesideTheOldWhenTheReadingWindowChanges(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        cli.run("run", root.toString());
        Path first = theTreeOf(root);

        setTheReadingWindowTo(A_WIDER_WINDOW);
        cli.run("run", root.toString());

        claim(
                "the two readings are " + TWO_RUNS + " pieces of work rather than one, because how much"
                        + " of a group may be read decides what the writing rests on -- which is the claim"
                        + " the two paths below follow from",
                () -> assertThat(generationRuns(root)).hasSize(TWO_RUNS));
        claim(
                "so there are " + TWO_RUNS + " trees, one named for each, and each named for its own"
                        + " piece of work: what was already handed to somebody is untouched, and the two"
                        + " are told apart by what they are rather than by when they were written",
                () -> {
                    assertThat(treesOf(root)).hasSize(TWO_RUNS);
                    assertThat(treeNamesOf(root)).containsExactlyElementsOf(generationRuns(root));
                });
        claim(
                "and the first tree still has its index, rather than having been written over by the"
                        + " second: the whole point of naming a tree after its own work is that a later"
                        + " run cannot quietly replace what an operator has already sent on",
                () -> assertThat(first.resolve(Deliverable.INDEX_FILE_NAME)).isRegularFile());
    }

    @Test
    @Story("The tree can be read years later with nothing beside it")
    @DisplayName("The index opens with the work, the reading of the archive, the archive itself and every value that was set")
    void opensTheIndexWithWhatProducedTheTree(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "it names the work whole, all " + THE_WHOLE_RUN_NAME + " characters of the name -- the"
                        + " directory carries it too, but a tree that has been moved or renamed is exactly"
                        + " the tree somebody needs it from",
                () -> assertThat(theIndexOf(root)).contains(generationRuns(root).getFirst()));
        claim(
                "it names the reading of the archive the links were made from, which is what says when"
                        + " they were true: the archive is assumed to stand still, and this is the date"
                        + " stamp on that assumption",
                () -> assertThat(theIndexOf(root)).contains(String.valueOf(theWalkBehind(root))));
        claim(
                "it names the archive's own root once, which is what makes a moved archive a matter of"
                        + " replacing one line rather than every link in the tree",
                () -> assertThat(theIndexOf(root)).contains(Walk.canonicalRoot(root).toString()));
        claim(
                "and it states every one of the " + EVERY_PROFILE_KEY.size() + " values the operator's"
                        + " file carries, by name: what was cut, what was kept and how much of each group"
                        + " was read are the questions a reader of this tree asks first, and the database"
                        + " that could answer them is not going with it",
                () -> assertThat(theIndexOf(root)).contains(EVERY_PROFILE_KEY.toArray(new CharSequence[0])));
        claim(
                "with the values themselves beside them, so the file says what was set rather than only"
                        + " what could have been",
                () -> assertThat(theIndexOf(root))
                        .contains(seeds.toString(), EMBEDDING_MODEL_NAME, THE_READING_WINDOW));
    }

    @Test
    @Story("Every group is listed where the arrangement put it")
    @DisplayName("Every group appears in the order it was stored in, with its document count, its name and its heading")
    void listsEveryClusterInStoredOrderWithItsCountLabelAndTitle(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));

        cli.run("run", root.toString());

        claim(
                "the arrangement really does hold both of the " + TWO_CLUSTERS + " groups, which is what"
                        + " the order claim below is about -- asserted first, so a fixture that failed to"
                        + " produce two fails here rather than while reading a page about one",
                () -> assertThat(clusters.forRun(theApprovedArrangement(root))).hasSize(TWO_CLUSTERS));
        claim(
                "the group the arrangement placed first is listed first, though it was formed after the"
                        + " other and carries the higher number: the sequence a person approved and the"
                        + " sequence a reader receives are one sequence, and nothing here sorts anything"
                        + " a second time",
                () -> assertThat(theIndexOf(root).indexOf(THE_CLUSTER_PLACED_FIRST))
                        .isNotNegative()
                        .isLessThan(theIndexOf(root).indexOf(SeedScriptedExtractionBeans.STUBBED_TITLE)));
        claim(
                "each group carries the name stage 6a derived for it and the heading the model wrote for"
                        + " it, both: the name is what keeps the tree navigable where no writing exists,"
                        + " and both together are what let a reader see what the derivation said before"
                        + " the heading was written over it",
                () -> assertThat(theIndexOf(root))
                        .contains(SeedScriptedExtractionBeans.STUBBED_TITLE)
                        .contains(GenerationScriptedBeans.GENERATED_TITLE));
        claim(
                "and it carries how many documents each group holds, the " + TWO_DOCUMENTS + " of this"
                        + " corpus among them, so a reader can see the weight of a group before opening"
                        + " it",
                () -> assertThat(theIndexOf(root)).contains(String.valueOf(TWO_DOCUMENTS)));
    }

    @Test
    @Story("A group nothing was written over is a hole a reader can see")
    @DisplayName("A group whose answer was turned down is listed under its own name with nothing to open")
    @Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
    void leavesAnUnwrittenClusterInPlaceWithNoLink(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_PLACED_FIRST, GenerationScriptedBeans.ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "exactly " + ONE_CLUSTER_LEFT_UNWRITTEN + " group was left unwritten, which is what the"
                        + " entry below is the hole for",
                () -> assertThat(clustersLeftUnwritten(root)).isEqualTo(ONE_CLUSTER_LEFT_UNWRITTEN));
        claim(
                "it is in the index under the name stage 6a derived for it, so the gap in the archive is"
                        + " legible as a gap: nothing else anywhere reports this, the tree being the"
                        + " report",
                () -> assertThat(theIndexLineNaming(root, THE_CLUSTER_PLACED_FIRST))
                        .contains(THE_CLUSTER_PLACED_FIRST));
        claim(
                "and there is nothing to open from it -- no link, because there is no page for one to"
                        + " lead to. A link to writing that was never produced is worse than the hole it"
                        + " would cover",
                () -> assertThat(theIndexLineNaming(root, THE_CLUSTER_PLACED_FIRST)).doesNotContain("]("));
        claim(
                "while the group after it was still written over and still has its page, so one answer"
                        + " nobody believed cost its own group and nothing else",
                () -> assertThat(theIndexLineNaming(root, SeedScriptedExtractionBeans.STUBBED_TITLE))
                        .contains("]("));
    }

    @Test
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("The listing beside the index carries every surviving document of the archive")
    void carriesEverySurvivorIntoTheListing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "all " + TWO_DOCUMENTS + " surviving documents are listed, which is every document the"
                        + " arrangement arranged rather than the ones any piece of writing mentioned",
                () -> assertThat(rowsOfTheListing(root)).hasSize(TWO_DOCUMENTS));
        claim(
                "each is listed by the path it has beneath the archive's own root rather than by a whole"
                        + " path from a drive letter down, so an archive that has moved is re-pointed from"
                        + " the one root the index records",
                () -> assertThat(rowsOfTheListing(root))
                        .allSatisfy(row -> assertThat(row).doesNotContain(Walk.canonicalRoot(root).toString())));
        claim(
                "and both documents of this corpus are there by name, so the listing is of these"
                        + " documents and not of a count that happens to match",
                () -> assertThat(String.join("\n", rowsOfTheListing(root)))
                        .contains("corpus.txt", "another-corpus-document.txt"));
    }

    @Test
    @Issue("287")
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("The listing gives every document its content hash, including one nothing else was the size of")
    @Link(name = "ADR-151", url = Adr.THE_MANIFESTS_CONTENT_HASH_IS_EVERY_SURVIVORS_SHA_256, type = "adr")
    @Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
    void fillsTheContentHashOfADocumentWithNoPeerOfItsSize(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);

        claim(
                "the precondition: the two documents of this corpus differ in size, so the first stage"
                        + " hashed neither of them. It hashes only among files that share a size, since no"
                        + " other file can be a copy of one. Without this, a filled column below could have"
                        + " come from that stage's record, and would say nothing about the gap",
                () -> assertThat(hashesTheFirstStageRecordedFor(root)).isZero());

        cli.run("run", root.toString());

        claim(
                "every one of the " + TWO_DOCUMENTS + " documents is listed with the SHA-256 of its own"
                        + " bytes in the content_hash column. The expected value is computed here from"
                        + " the file on disk, not read from any code under test. A blank cell is the"
                        + " failure: on the first real archive, 47 rows of 65 were blank",
                () -> assertThat(contentHashesOfTheListing(root))
                        .hasSize(TWO_DOCUMENTS)
                        .containsEntry("corpus.txt", sha256Of(root.resolve("corpus.txt")))
                        .containsEntry(
                                "another-corpus-document.txt", sha256Of(root.resolve("another-corpus-document.txt"))));
    }

    @Test
    @Issue("287")
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("A document gone from the archive before the tree is written is still listed, with a blank content hash and a warning")
    @Link(name = "ADR-151", url = Adr.THE_MANIFESTS_CONTENT_HASH_IS_EVERY_SURVIVORS_SHA_256, type = "adr")
    void leavesTheContentHashBlankForADocumentGoneBeforeTheTreeIsWritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        Path gone = root.resolve("corpus.txt");
        GenerationScriptedBeans.duringEachCall(() -> {
            try {
                Files.deleteIfExists(gone);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });

        cli.run("run", root.toString());

        claim(
                "the precondition: the document really is gone by the time the tree is written. It is"
                        + " deleted while the model is being asked, which is after the call's documents"
                        + " were read and before documents.csv is written -- the same window a file that"
                        + " goes away during 6b's model calls falls into",
                () -> assertThat(gone).doesNotExist());
        claim(
                "both of the " + TWO_DOCUMENTS + " survivors are still listed: a document the archive no"
                        + " longer opens is a fact about that document, and the tree is written anyway",
                () -> assertThat(rowsOfTheListing(root)).hasSize(TWO_DOCUMENTS));
        claim(
                "the gone document's content_hash cell is blank rather than a value from anywhere else,"
                        + " and the other document's cell is still the SHA-256 of its own bytes, computed"
                        + " here with the JDK",
                () -> assertThat(contentHashesOfTheListing(root))
                        .hasSize(TWO_DOCUMENTS)
                        .containsEntry("corpus.txt", "")
                        .containsEntry(
                                "another-corpus-document.txt", sha256Of(root.resolve("another-corpus-document.txt"))));
        claim(
                "and one warning names the file and the content_hash cell it left blank, so the gap in the"
                        + " listing is explained where the operator reads the run",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                        .map(ILoggingEvent::getFormattedMessage)
                        .filteredOn(line -> line.contains("content_hash"))
                        .singleElement()
                        .asString()
                        .contains(gone.getFileName().toString()));
    }

    @Test
    @Story("Whatever comes after the hand-off can be built without reading the prose")
    @DisplayName("A group's own page carries the writing with its citation resolved and the whole group beneath it")
    @Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    void writesAPageWhoseCitationLeadsToADocumentOfTheArchive(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        Path pageFile = theClusterPageFileOf(root);
        String page = Files.readString(pageFile);

        claim(
                "the citation the model wrote is a link to an entry of the list below rather than the"
                        + " notation it arrived as, on a page a whole invocation produced: every other"
                        + " claim about this rewriting is made against the writer holding values by hand,"
                        + " and none of them says a real run reaches it",
                () -> assertThat(page).contains("[1](#document-1)"));
        claim(
                "and no raw marker is left anywhere on it, so what a reader meets is the link and never"
                        + " the model's own numbering",
                () -> assertThat(A_RAW_CITATION.matcher(page).find()).isFalse());
        claim(
                "the entry that citation points at is the first of the list, and both of this corpus's "
                        + TWO_DOCUMENTS + " documents are on it -- the group entire, not the one document"
                        + " the writing happened to cite",
                () -> assertThat(page).contains("1. <a id=\"document-1\"></a>[", "2. <a id=\"document-2\"></a>["));
        claim(
                "no destination on the page names a scheme: the reader's renderer decides what a link is,"
                        + " and the two that were measured as a person's way into this tree make no link at"
                        + " all of one that begins \"file:\" -- one printing the entry's source at the"
                        + " reader, the other keeping the text and dropping the destination",
                () -> assertThat(page).doesNotContain("file:"));
        claim(
                "and each entry leads on to the document where it already sits: both destinations are"
                        + " followed from the directory the page is in, and both land on a file that is"
                        + " really there -- the second click of the chain, and the whole of what this tool"
                        + " owes a reader who doubts a sentence. This is the one claim in the project that"
                        + " exercises a link rather than reading its text, because only a whole invocation"
                        + " leaves an archive on disk for one to land in",
                () -> assertThat(whereTheEntriesLeadFrom(pageFile))
                        .hasSize(TWO_DOCUMENTS)
                        .allSatisfy(document -> assertThat(document).exists()));
    }

    @Test
    @Story("The archive is referenced and never copied")
    @DisplayName("Nothing in the tree is a copy of a document, and the archive is left exactly as it was")
    void copiesNoOriginalIntoTheTree(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "nothing in the tree carries the text of a document from the archive -- not the documents"
                        + " themselves and not the text pulled out of them, which would be a third version"
                        + " of every document and an invitation to read the wrong one",
                () -> assertThat(everyFileIn(theTreeOf(root)))
                        .allSatisfy(file -> assertThat(Files.readString(file))
                                .doesNotContain(WHAT_A_CORPUS_DOCUMENT_SAYS)
                                .doesNotContain(WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS)));
        claim(
                "and the archive holds exactly the " + TWO_DOCUMENTS + " documents it held before,"
                        + " unmoved and unaugmented: curating hundreds of gigabytes must never duplicate"
                        + " them, and nothing here writes inside the archive at all",
                () -> assertThat(everyFileIn(root)).hasSize(TWO_DOCUMENTS));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("The closing line names the tree that was written and how many groups were left unwritten")
    @Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
    void closesOnTheLineNamingTheTreeAndWhatIsMissingFromIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_PLACED_FIRST, GenerationScriptedBeans.ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the last thing the invocation says names the tree it just wrote: a name of "
                        + THE_WHOLE_RUN_NAME + " characters is neither readable nor in any order, so the"
                        + " one place an operator can be handed the path is here",
                () -> assertThat(operatorLines().getLast()).contains(theTreeOf(root).toString()));
        claim(
                "and it counts the " + ONE_CLUSTER_LEFT_UNWRITTEN + " group left unwritten beside it, so"
                        + " the operator knows what they have before they open it rather than after"
                        + " reading to the hole",
                () -> assertThat(whatTheClosingLineSaysBesideThePath(root))
                        .containsPattern("\\b" + ONE_CLUSTER_LEFT_UNWRITTEN + "\\b"));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("With every answer believed, the closing line still names the tree and says nothing is missing")
    @Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
    void closesOnTheSameLineWhenNothingWasLeftUnwritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation left " + NO_CLUSTER_LEFT_UNWRITTEN + " groups unwritten, which is what the"
                        + " line below has to say",
                () -> assertThat(clustersLeftUnwritten(root)).isEqualTo(NO_CLUSTER_LEFT_UNWRITTEN));
        claim(
                "and the closing line still names the path and still states the count, rather than"
                        + " leaving the count off when it is nothing: a line that says how much is missing"
                        + " only when something is teaches an operator to read silence as success",
                () -> {
                    assertThat(operatorLines().getLast()).contains(theTreeOf(root).toString());
                    assertThat(whatTheClosingLineSaysBesideThePath(root))
                            .containsPattern("\\b" + NO_CLUSTER_LEFT_UNWRITTEN + "\\b");
                });
    }

    /** The tree this corpus's one piece of work wrote, found by that work's own name. */
    private Path theTreeOf(Path root) {
        return workingDirectory
                .resolve(Deliverable.DIRECTORY_NAME)
                .resolve(generationRuns(root).getLast());
    }

    /**
     * Every tree this corpus produced, in the order the work behind them was recorded.
     *
     * <p>Found through the runs recorded against this corpus's own walk rather than by listing the
     * directory, because the directory is shared by every test in this class and holds whatever they
     * left behind.
     */
    private List<Path> treesOf(Path root) {
        return generationRuns(root).stream()
                .map(run -> workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(run))
                .filter(Files::isDirectory)
                .toList();
    }

    /** What those trees are called, which is the claim that ties a path to a piece of work. */
    private List<String> treeNamesOf(Path root) {
        return treesOf(root).stream()
                .map(tree -> tree.getFileName().toString())
                .toList();
    }

    /**
     * The names of every tree on disk, whichever test wrote it, sorted so two readings compare.
     *
     * <p>The one claim about absence is made by comparing this before and after, because a claim
     * scoped to a run that was never minted is a claim about nothing and passes on an empty database
     * exactly as it passes on a correct one.
     */
    private List<String> everyTreeOnDisk() throws IOException {
        Path deliverables = workingDirectory.resolve(Deliverable.DIRECTORY_NAME);
        if (!Files.isDirectory(deliverables)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(deliverables)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    /** Every file anywhere beneath {@code directory}, which is what a claim about copying counts. */
    private static List<Path> everyFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile).toList();
        }
    }

    /** The rows of the listing, without the header line that names their columns. */
    private List<String> rowsOfTheListing(Path root) throws IOException {
        return Files.readAllLines(theTreeOf(root).resolve(Deliverable.MANIFEST_FILE_NAME)).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .toList();
    }

    /**
     * Each listed document's content_hash cell, keyed by the path the listing gives it.
     *
     * <p>The columns are found by name in the header rather than by position, so this claim is about
     * the column's content and not about where the column sits. A row is split on commas outside
     * double quotes, the way a program loading the file splits it.
     */
    private Map<String, String> contentHashesOfTheListing(Path root) throws IOException {
        List<String> lines = Files.readAllLines(theTreeOf(root).resolve(Deliverable.MANIFEST_FILE_NAME)).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cellsOf(lines.getFirst());
        int path = header.indexOf("path");
        int contentHash = header.indexOf("content_hash");
        Map<String, String> byPath = new LinkedHashMap<>();
        for (String row : lines.subList(1, lines.size())) {
            List<String> cells = cellsOf(row);
            byPath.put(cells.get(path), cells.get(contentHash));
        }
        return byPath;
    }

    /** One CSV line's cells, with quotes stripped and a doubled quote read as one. */
    private static List<String> cellsOf(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '"' && quoted && at + 1 < line.length() && line.charAt(at + 1) == '"') {
                cell.append('"');
                at++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /**
     * The SHA-256 of {@code file}'s bytes as lowercase hex, computed here with the JDK. Neither of the
     * two hashers under test is used, so the expected value is not read back out of the code it checks.
     */
    private static String sha256Of(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /** How many content hashes the first stage recorded for any occurrence of this corpus's walks. */
    private int hashesTheFirstStageRecordedFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_hash ch JOIN file_occurrence fo ON fo.id = ch.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id WHERE w.root = ?",
                Integer.class,
                Walk.canonicalRoot(root).toString());
    }

    /**
     * The one cluster's page beneath the tree this corpus's work wrote.
     *
     * <p>Found as the one Markdown file that is not the index, rather than by composing the name from
     * a label and an ordinal: a claim about what a page carries should not also depend on this test
     * having predicted what the page would be called.
     */
    private String theClusterPageOf(Path root) throws IOException {
        return Files.readString(theClusterPageFileOf(root));
    }

    /**
     * Where that page sits, which a claim about a destination relative to it has to follow it from
     * (ADR-135).
     */
    private Path theClusterPageFileOf(Path root) throws IOException {
        List<Path> pages = everyFileIn(theTreeOf(root)).stream()
                .filter(file -> file.getFileName().toString().endsWith(".md"))
                .filter(file -> !file.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME))
                .toList();
        if (pages.size() != ONE_CLUSTER) {
            throw new IllegalStateException(
                    "this corpus arranges one group and so writes one page, and the tree holds " + pages.size());
        }
        return pages.getFirst();
    }

    /**
     * Where every membership entry on {@code page} leads, each destination decoded as a renderer
     * decodes it and followed from the directory the page is in.
     *
     * <p>Decoded rather than read as characters of a filename: the percent-escapes are the renderer's
     * to undo, and a claim that treated them as part of the name would be checking the encoding against
     * itself instead of following the link.
     */
    private static List<Path> whereTheEntriesLeadFrom(Path page) throws IOException {
        List<Path> documents = new ArrayList<>();
        Matcher entry = A_MEMBERSHIP_DESTINATION.matcher(Files.readString(page));
        while (entry.find()) {
            documents.add(page.getParent()
                    .resolve(URI.create(entry.group(1)).getPath())
                    .normalize());
        }
        return List.copyOf(documents);
    }

    /** The index of the tree this corpus's work wrote, read at the moment a claim asks for it. */
    private String theIndexOf(Path root) throws IOException {
        return Files.readString(theTreeOf(root).resolve(Deliverable.INDEX_FILE_NAME));
    }

    /** The one line of the index mentioning {@code name}, so a claim is about an entry and not a page. */
    private String theIndexLineNaming(Path root, String name) throws IOException {
        return Files.readAllLines(theTreeOf(root).resolve(Deliverable.INDEX_FILE_NAME)).stream()
                .filter(line -> line.contains(name))
                .findFirst()
                .orElse("");
    }

    /** How many clusters of this corpus were left unwritten under the work that just ran. */
    private int clustersLeftUnwritten(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster_fault WHERE run_id = ?",
                Integer.class,
                generationRuns(root).getLast());
    }

    /** The reading of the archive the work behind this tree was made over. */
    private long theWalkBehind(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT walk_id FROM run WHERE id = ?", Long.class, generationRuns(root).getLast());
    }

    /**
     * The closing line with the path it names cut out of it.
     *
     * <p>A count is claimed against this rather than against the whole line, because the path carries
     * sixty-four hexadecimal characters and a temporary directory of its own: a digit found anywhere
     * in it would satisfy a claim about the number of clusters left unwritten without the line ever
     * having stated one.
     */
    private String whatTheClosingLineSaysBesideThePath(Path root) {
        return operatorLines().getLast().replace(theTreeOf(root).toString(), "");
    }

    /** Every line this application wrote for an operator, in the order they were written. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * A corpus of two documents, walked once, with the arrangement it produced approved — the state
     * every test here starts from, none of them being about the gate in front of it.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
    }

    /** Two documents, an exemplar, and every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), WHAT_A_CORPUS_DOCUMENT_SAYS);
        Files.writeString(root.resolve("another-corpus-document.txt"), WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS);
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
    }

    /** Writes the reading window into the profile, leaving every other key as it was. */
    private void setTheReadingWindowTo(String window) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(window, "set by this test")
                .build());
    }

    /**
     * Puts a second cluster into the approved arrangement, ahead of the one already there.
     *
     * <p>Written straight into the table, for the reason this package's other invocation tests do it:
     * a fixture whose documents all convert alike cannot be steered into producing two clusters. It is
     * placed <em>ahead</em> of the existing one on purpose — a tree that listed the two in the order
     * they were formed rather than the order they were arranged in would pass with them the other way
     * round.
     *
     * <p><b>It copies document_count unchanged</b>, so the injected row claims more documents than it
     * holds, which no arrangement run would write. Harmless here, because a call is sized from
     * membership and never from that column.
     */
    private void aSecondClusterAheadOfTheFirst(RunId arrangement) {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " document_count, partition_order, cluster_order - 1"
                        + " FROM cluster WHERE run_id = ?",
                THE_CLUSTER_PLACED_FIRST,
                arrangement.value());
    }

    /**
     * Moves one of the corpus documents into that second cluster, so it has something to send and is
     * therefore asked about at all. Which document moves does not matter, so the query takes the last.
     */
    private void thatClusterIsGivenADocumentItCanSend(RunId arrangement) {
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE rowid ="
                        + " (SELECT rowid FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1)",
                arrangement.value());
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names, whichever invocation recorded it. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method here ever walked.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
