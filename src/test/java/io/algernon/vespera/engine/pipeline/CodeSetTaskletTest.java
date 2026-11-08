package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1 keeps code in code sets (ADR-284): a set pass, after the first pass's verdicts and before the hashing,
 * finds each set's folder, the nearest folder under the corpus root holding a build file or else the first folder
 * under the root, keeps the set's build files and the pictures and companion files of a generated tree in it as
 * code, and records the sets and their members in {@code code_set} and {@code code_set_member}.
 *
 * <p>The tables are read by their names and the verdicts by their stored names, so this compiles before anything
 * it holds is built; until the tables exist, each test fails on the first statement naming one, or on a verdict
 * the set pass has not written. Every file is built here, and every one that is not code has contents of its own,
 * so nothing a test means to keep is superseded by another. The corpus root is a folder of a name the test
 * chooses, {@link #ROOT_NAME}, since the root's set is named for it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Code sets")
@Issue("572")
@Link(name = "ADR-284", url = Adr.CODE_IS_KEPT_IN_CODE_SETS_NAMED_FOR_THEIR_FOLDER, type = "adr")
class CodeSetTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** The name endings every test lists as code, so that its code is found by name. */
    private static final String CODE_ENDINGS = ".java .c .py";

    /** The corpus root's own folder name, which the root's set is named for. */
    private static final String ROOT_NAME = "archivio";

    /** The verdicts' stored names. */
    private static final String CODE = "CODE";

    private static final String BROKEN = "BROKEN";

    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("Code is kept together below the folder its build begins in")
    @DisplayName("Code is kept with the nearest folder above it holding a build file, else with the first folder under the archive's root; a folder of documents is no code set")
    void eachSetLiesBelowTheNearestFolderHoldingABuildFileElseTheFirstFolder(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "engine/CMakeLists.txt", "project(motore C)\n");
        write(root, "engine/src/core/motore.c", "int motore(void) { return 1; }\n");
        write(root, "tools/scripts/pulisci.py", "print('pulisci')\n");
        write(root, "notes/appunti.txt", text("notes/appunti.txt"));

        stageOne(root, workingDirectory);

        claim(
                "there are two code sets: one below the folder holding the build file, two folders above its code, and"
                        + " one below the first folder of the script that has no build file above it; each is named"
                        + " for its folder, and the folder of notes is none",
                () -> assertThat(sets()).isEqualTo(Map.of("engine", "engine", "tools", "tools")));
        claim(
                "each file of code is a member of its set, as code",
                () -> {
                    assertThat(membershipOf("engine/src/core/motore.c")).containsExactly("code in 'engine'");
                    assertThat(membershipOf("tools/scripts/pulisci.py")).containsExactly("code in 'tools'");
                });
    }

    @Test
    @Story("What stage 1 records of each code set")
    @DisplayName("Stage 1 records a row for each code set and one for each of its members, under its own piece of work")
    void theTablesHoldARowForEachSetAndEachMemberUnderStageOnesRun(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "m/Makefile", "all:\n\tcc a.c b.c\n");
        write(root, "m/a.c", "int a(void) { return 0; }\n");
        write(root, "m/b.c", "int b(void) { return 1; }\n");
        write(root, "m/api/index.html", javadocPage("Overview"));
        write(root, "m/api/element-list", "pkg\n");

        stageOne(root, workingDirectory);

        String run = jdbcTemplate.queryForObject("SELECT id FROM run WHERE stage = 'byte-level-reduction'", String.class);
        claim(
                "one row of the set: its folder and its name, under stage 1's piece of work",
                () -> assertThat(jdbcTemplate.query(
                                "SELECT run_id, folder, default_name FROM code_set",
                                (row, n) -> row.getString("run_id") + " " + row.getString("folder") + " "
                                        + row.getString("default_name")))
                        .containsExactly(run + " m m"));
        claim(
                "five rows of its members, one for each, under the same piece of work: the build file, the two files of"
                        + " code, the reference page with its generator, and the tree's companion file with its generator",
                () -> assertThat(jdbcTemplate.query(
                                "SELECT m.run_id, o.path, m.folder, m.role, m.generator FROM code_set_member m"
                                        + " JOIN file_occurrence o ON o.id = m.occurrence_id ORDER BY o.path",
                                (row, n) -> row.getString("run_id") + " " + row.getString("path") + " "
                                        + row.getString("folder") + " " + row.getString("role") + " "
                                        + row.getString("generator")))
                        .containsExactly(
                                run + " m/Makefile m build null",
                                run + " m/a.c m code null",
                                run + " m/api/element-list m companion Javadoc",
                                run + " m/api/index.html m code Javadoc",
                                run + " m/b.c m code null"));
    }

    @Test
    @Story("Code sets are written once")
    @DisplayName("Stage 1 redone after it stopped partway leaves one row of each code set and of each member, and one verdict on each build file")
    void aStageOneLeftUnfinishedIsRedoneWithOneRowOfEachSetAndMember(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "m/Makefile", "all:\n\tcc a.c\n");
        write(root, "m/a.c", "int a(void) { return 0; }\n");

        stageOne(root, workingDirectory);
        // as if the invocation had stopped before stage 1 was recorded as finished
        jdbcTemplate.update("DELETE FROM finished_step WHERE step = ?", StepNames.BYTE_LEVEL_REDUCTION);
        stageOne(root, workingDirectory);

        claim(
                "one row of the set and two of its members: the step's unfinished rows are discarded before it works"
                        + " again",
                () -> assertThat(List.of(
                                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM code_set", Integer.class),
                                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM code_set_member", Integer.class)))
                        .containsExactly(1, 2));
        claim(
                "and the build file carries one code verdict",
                () -> assertThat(verdictKindsFor("m/Makefile")).containsExactly(CODE));
    }

    /** Stage 1 over {@code root}, with {@link #CODE_ENDINGS} listed as code in a profile written for it. */
    private void stageOne(Path root, Path workingDirectory) throws Exception {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        if (!Files.exists(profileStore.file())) {
            CodeNameEndingsInProfile.write(profileStore, CODE_ENDINGS, SET_BY_THIS_TEST);
        }
        stageOneWithItsProfile(root, workingDirectory);
    }

    /** Stage 1 over {@code root}, walked first if it has not been, with the profile already written. */
    private void stageOneWithItsProfile(Path root, Path workingDirectory) throws Exception {
        stageOneWithItsProfile(root, workingDirectory, new Ledger(jdbcTemplate), new DetectedFormats(jdbcTemplate));
    }

    /** As above, stage 1 writing through {@code ledger} and {@code detectedFormats}. */
    private void stageOneWithItsProfile(Path root, Path workingDirectory, Ledger ledger, DetectedFormats detectedFormats)
            throws Exception {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Integer.class) == 0) {
            new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
        }
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        detectedFormats,
                        new ImplementationVersions(),
                        new ProfileStore(workingDirectory),
                        root,
                        workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());
    }

    /** Each set of the one run, by folder, with its default name. */
    private Map<String, String> sets() {
        Map<String, String> sets = new TreeMap<>();
        jdbcTemplate.query(
                "SELECT folder, default_name FROM code_set",
                row -> {
                    sets.put(row.getString("folder"), row.getString("default_name"));
                });
        return sets;
    }

    /** What the occurrence at {@code path} is a member of, as {@code <role> in '<folder>'} and its generator, if any. */
    private List<String> membershipOf(String path) {
        return jdbcTemplate.query(
                "SELECT m.role, m.folder, m.generator FROM code_set_member m"
                        + " JOIN file_occurrence o ON o.id = m.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("role") + " in '" + row.getString("folder") + "'"
                        + (row.getString("generator") == null ? "" : " by " + row.getString("generator")),
                path);
    }

    private List<String> verdictKindsFor(String path) {
        return jdbcTemplate.query(
                "SELECT v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("kind"),
                path);
    }

    /** A folder named {@link #ROOT_NAME} inside {@code temp}, to walk as the corpus root. */
    private static Path namedRoot(Path temp) throws IOException {
        return Files.createDirectory(temp.resolve(ROOT_NAME));
    }

    private static void write(Path root, String path, String text) throws IOException {
        write(root, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path root, String path, byte[] bytes) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** A line of prose naming {@code path}, so that no two such files share their bytes. */
    private static String text(String path) {
        return "a note about " + path + ", which is a document and no code\n";
    }

    /** A page Javadoc wrote, carrying its mark. */
    private static String javadocPage(String title) {
        return page(
                "<!-- Generated by javadoc (17) -->\n<title>" + title + "</title>\n"
                        + "<meta name=\"generator\" content=\"javadoc/ClassWriterImpl\">",
                "<body class=\"class-declaration-page\"><h1>" + title + "</h1>",
                "");
    }

    /** A small HTML page: {@code head} inside its head, {@code body} and {@code footer} inside its body. */
    private static String page(String head, String body, String footer) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n" + head + "\n</head>\n<body>\n"
                + body + "\n" + footer + "\n</body>\n</html>\n";
    }
}
