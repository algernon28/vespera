package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.Verdicts;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
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

    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    /** The reason a build file kept with its set carries, with the set's folder to fill in. */
    private static final String BUILD_REASON = "a build file of the code set at %s";

    /** How that reason names the root's set. */
    private static final String THE_CORPUS_ROOT = "the corpus root";

    /** The reason a file of a generated tree carries, with the generator to fill in. */
    private static final String TREE_REASON = "a file of the tree %s generated";

    /** The file the operator renames sets in, beside the database. */
    private static final String CODE_SET_NAMES = "code-set-names.yaml";

    /** The format-mix page's count of the files the first pass kept as code. */
    private static final String KEPT_AS_CODE = "Files kept as code, and not read any further";

    private static final String KEPT_AS_CODE_HEADING = "<h2>Kept as code</h2>";

    /** A log floor of 90%, so that a file of timestamped lines is a log. */
    private static final String A_LOG_FLOOR = "0.9";

    /** More timestamped lines than the ten the log rule needs. */
    private static final int LOG_LINES = 12;

    /** The eight bytes every PNG begins with, then enough to look like one; each picture adds its path. */
    private static final byte[] PNG_HEAD = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };

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
    @Story("Code is kept together below the folder its build begins in")
    @DisplayName("Each module of a build of several modules is a code set of its own, and the build file above the modules is in none and is kept as it was")
    void eachModuleOfAMultiModuleBuildIsASetOfItsOwn(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/pom.xml", "<project><artifactId>shop</artifactId><modules><module>core</module>"
                + "<module>api</module></modules></project>\n");
        write(root, "shop/core/pom.xml", "<project><artifactId>core</artifactId></project>\n");
        write(root, "shop/core/src/main/java/Core.java", "class Core {}\n");
        write(root, "shop/api/pom.xml", "<project><artifactId>api</artifactId></project>\n");
        write(root, "shop/api/src/main/java/Api.java", "class Api {}\n");

        stageOne(root, workingDirectory);

        claim(
                "each module is a code set named for its folder, and the folder of the build above them, which holds no"
                        + " code of its own, is none",
                () -> assertThat(sets()).isEqualTo(Map.of("shop/core", "core", "shop/api", "api")));
        claim(
                "each module's own build file is in its module's set",
                () -> {
                    assertThat(membershipOf("shop/core/pom.xml")).containsExactly("build in 'shop/core'");
                    assertThat(membershipOf("shop/api/pom.xml")).containsExactly("build in 'shop/api'");
                });
        claim(
                "the build file above the modules is in no set and carries no verdict",
                () -> {
                    assertThat(membershipOf("shop/pom.xml")).isEmpty();
                    assertThat(verdictKindsFor("shop/pom.xml")).isEmpty();
                });
    }

    @Test
    @Story("Code is kept together below the folder its build begins in")
    @DisplayName("Code lying in a folder of the build above the modules, where no build file of its own lies, makes that folder a code set, and the build file above the modules is its")
    void aBuildFileAboveModulesIsASetsWhereCodeLiesInItsOwnExtent(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/pom.xml", "<project><artifactId>shop</artifactId></project>\n");
        write(root, "shop/core/pom.xml", "<project><artifactId>core</artifactId></project>\n");
        write(root, "shop/core/Core.java", "class Core {}\n");
        write(root, "shop/tools/genera.py", "print('genera')\n");

        stageOne(root, workingDirectory);

        claim(
                "the module is a set, and so is the folder above it, for the script lying in it below no nearer build file",
                () -> assertThat(sets()).isEqualTo(Map.of("shop", "shop", "shop/core", "core")));
        claim(
                "the build file above the module is the outer set's, kept as code with the reason naming that set",
                () -> {
                    assertThat(membershipOf("shop/pom.xml")).containsExactly("build in 'shop'");
                    assertThat(verdictReasonsFor("shop/pom.xml")).containsExactly(BUILD_REASON.formatted("shop"));
                    assertThat(membershipOf("shop/tools/genera.py")).containsExactly("code in 'shop'");
                });
    }

    @Test
    @Story("Code is kept together below the folder its build begins in")
    @DisplayName("A code set below another's folder takes its own build file and code, and a build file in a folder holding no code is the outer set's")
    void aSetsExtentStopsAtTheFolderOfASetNestedInIt(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "Work/Makefile", "all:\n\tcc -o work main.c\n");
        write(root, "Work/main.c", "int main(void) { return 0; }\n");
        write(root, "Work/shop/pom.xml", "<project><artifactId>shop</artifactId></project>\n");
        write(root, "Work/shop/Shop.java", "class Shop {}\n");
        write(root, "Work/scripts/Makefile", "clean:\n\trm -f *.o\n");
        write(root, "Work/scripts/leggimi.txt", text("Work/scripts/leggimi.txt"));

        stageOne(root, workingDirectory);

        claim(
                "there are two code sets, the inner one below the folder of its own build file",
                () -> assertThat(sets()).isEqualTo(Map.of("Work", "Work", "Work/shop", "shop")));
        claim(
                "the inner set holds its own code and build file",
                () -> {
                    assertThat(membershipOf("Work/shop/Shop.java")).containsExactly("code in 'Work/shop'");
                    assertThat(membershipOf("Work/shop/pom.xml")).containsExactly("build in 'Work/shop'");
                });
        claim(
                "the outer set holds its code, its build file, and the build file of a folder below it holding no code",
                () -> {
                    assertThat(membershipOf("Work/main.c")).containsExactly("code in 'Work'");
                    assertThat(membershipOf("Work/Makefile")).containsExactly("build in 'Work'");
                    assertThat(membershipOf("Work/scripts/Makefile")).containsExactly("build in 'Work'");
                });
    }

    @Test
    @Story("Code lying at the root of the archive")
    @DisplayName("A build file at the archive's root marks no code set: the files lying directly at the root are the root's set, named for the root's own folder, and each folder of code below is a set of its own")
    void theCorpusRootAnchorsNoSetAndItsSetHoldsWhatLiesDirectlyThere(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "pom.xml", "<project><artifactId>archivio</artifactId></project>\n");
        write(root, "main.c", "int main(void) { return 0; }\n");
        write(root, "foto/vacanza.png", png("foto/vacanza.png"));
        write(root, "lib/util.c", "int util(void) { return 1; }\n");
        write(root, "lib/deep/x.c", "int x(void) { return 2; }\n");

        stageOne(root, workingDirectory);

        claim(
                "there are two code sets: the root's, written as an empty folder and named for the root's own folder,"
                        + " and the first folder's below it",
                () -> assertThat(sets()).isEqualTo(Map.of("", ROOT_NAME, "lib", "lib")));
        claim(
                "the root's set holds the code lying directly at the root, and the build file beside it as the root's",
                () -> {
                    assertThat(membershipOf("main.c")).containsExactly("code in ''");
                    assertThat(membershipOf("pom.xml")).containsExactly("build in ''");
                    assertThat(verdictReasonsFor("pom.xml")).containsExactly(BUILD_REASON.formatted(THE_CORPUS_ROOT));
                });
        claim(
                "the code of the folder below is in that folder's set, and a picture one folder down is in none",
                () -> {
                    assertThat(membershipOf("lib/util.c")).containsExactly("code in 'lib'");
                    assertThat(membershipOf("lib/deep/x.c")).containsExactly("code in 'lib'");
                    assertThat(membershipOf("foto/vacanza.png")).isEmpty();
                    assertThat(verdictKindsFor("foto/vacanza.png")).isEmpty();
                });
    }

    @Test
    @Story("A code set's build files stay with it")
    @DisplayName("A build file is told by its name alone, in any case and whatever its bytes; a bare ending and the names left off the list mark no folder")
    void aBuildFileIsToldByItsNameAlone(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        byte[] binary = new byte[64];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i;
        }
        Map<String, byte[]> marking = new TreeMap<>(Map.of(
                "upper/m/POM.XML", "<project/>\n".getBytes(StandardCharsets.UTF_8),
                "lower/m/makefile", "all:\n".getBytes(StandardCharsets.UTF_8),
                "ending/m/Rete.csproj", "<Project/>\n".getBytes(StandardCharsets.UTF_8),
                "empty/m/Makefile", new byte[0],
                "binary/m/Makefile", binary));
        Map<String, String> notMarking = new TreeMap<>(Map.of(
                "bare/m/.sln", text("bare/m/.sln"),
                "qmake/m/app.pro", text("qmake/m/app.pro"),
                "bazel/m/BUILD", text("bazel/m/BUILD"),
                "docker/m/Dockerfile", text("docker/m/Dockerfile"),
                "config/m/setup.cfg", text("config/m/setup.cfg"),
                "rake/m/Rakefile", text("rake/m/Rakefile")));
        for (Map.Entry<String, byte[]> file : marking.entrySet()) {
            write(root, file.getKey(), file.getValue());
            write(root, file.getKey().substring(0, file.getKey().lastIndexOf('/')) + "/code.c", "int f(void) { return 0; }\n");
        }
        for (Map.Entry<String, String> file : notMarking.entrySet()) {
            write(root, file.getKey(), file.getValue());
            write(root, file.getKey().substring(0, file.getKey().lastIndexOf('/')) + "/code.c", "int f(void) { return 0; }\n");
        }

        stageOne(root, workingDirectory);

        Map<String, String> expected = new TreeMap<>();
        marking.keySet().forEach(file -> expected.put(file.substring(0, file.lastIndexOf('/')), "m"));
        notMarking.keySet().forEach(file -> expected.put(file.substring(0, file.indexOf('/')), file.substring(0, file.indexOf('/'))));
        claim(
                "a listed name in capitals or in lower case, a listed ending, and an empty and a binary file of a listed"
                        + " name each make their own folder a set's; an ending alone, .pro, a bare BUILD, a Dockerfile,"
                        + " setup.cfg and a Rakefile do not, and their code is in the first folder's set",
                () -> assertThat(sets()).isEqualTo(expected));
    }

    @Test
    @Story("A code set's build files stay with it")
    @DisplayName("A build file of a code set whose bytes read as text is kept as code with it and never hashed; one kept as code for its own name is a member as code; one in UTF-16, an empty one and a binary one are kept as they were")
    void aBuildFileInASetIsKeptAsCodeWithIt(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        String packageJson = "{\"name\": \"app\"}\n";
        write(root, "net/Rete.SLN", "Microsoft Visual Studio Solution File, Format Version 12.00\n");
        write(root, "net/Rete.csproj", "<Project Sdk=\"Microsoft.NET.Sdk\"></Project>\n");
        write(root, "net/Program.java", "class Program {}\n");
        write(root, "cli/MAKEFILE", "all:\n\tcc -o cli main.c\n");
        write(root, "cli/package.json", packageJson);
        write(root, "cli/main.c", "int main(void) { return 0; }\n");
        write(root, "ui/package.json", packageJson);
        write(root, "ui/ui.py", "print('ui')\n");
        write(root, "tool/setup.py", "from setuptools import setup\nsetup(name='tool')\n");
        write(root, "utf/Rete.sln", utf16("Microsoft Visual Studio Solution File, Format Version 12.00\n"));
        write(root, "utf/a.c", "int a(void) { return 0; }\n");
        write(root, "vuoto/Makefile", new byte[0]);
        write(root, "vuoto/e.c", "int e(void) { return 0; }\n");
        byte[] binary = new byte[64];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i;
        }
        write(root, "bin/Makefile", binary);
        write(root, "bin/b.c", "int b(void) { return 0; }\n");

        List<String> logged = stageOneLogging(root, workingDirectory);

        Map<String, String> buildFiles = new TreeMap<>(Map.of(
                "net/Rete.SLN", "net",
                "net/Rete.csproj", "net",
                "cli/MAKEFILE", "cli",
                "cli/package.json", "cli",
                "ui/package.json", "ui"));
        buildFiles.forEach((file, set) -> claim(
                file + " is kept as code, the reason naming its set's folder, and is a member of that set as a build file",
                () -> {
                    assertThat(verdictReasonsFor(file)).containsExactly(BUILD_REASON.formatted(set));
                    assertThat(membershipOf(file)).containsExactly("build in '" + set + "'");
                }));
        claim(
                "the two identical build files are both kept, never hashed, neither superseding the other",
                () -> {
                    assertThat(contentHashRowsFor("cli/package.json") + contentHashRowsFor("ui/package.json")).isZero();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM superseded_by", Integer.class)).isZero();
                });
        claim(
                "stage 1's log says of a build file, in the whole of its line, that it was kept with its code set as code, and why",
                () -> assertThat(logged).anyMatch(line -> line.matches(
                        Pattern.quote("[byte-level-reduction] kept " + occurrenceIdOf("cli/MAKEFILE")
                                + " with its code set -> code: " + BUILD_REASON.formatted("cli")))));
        claim(
                "every line the set pass logs for a file it verdicts has that shape, the occurrence named by its id",
                () -> assertThat(logged.stream().filter(line -> line.contains(" with its code set ")))
                        .isNotEmpty()
                        .allMatch(line -> line.matches(
                                "\\[byte-level-reduction\\] kept \\d+ with its code set -> code: "
                                        + "a build file of the code set at [a-z]+")));
        claim(
                "a build file kept as code for the ending of its own name is a member as code, and makes its folder a set",
                () -> {
                    assertThat(membershipOf("tool/setup.py")).containsExactly("code in 'tool'");
                    assertThat(sets()).containsEntry("tool", "tool");
                });
        claim(
                "a build file in UTF-16 and a binary one mark their folder, but are no members and carry no verdict; an"
                        + " empty one marks its folder and stays broken, and only broken",
                () -> {
                    assertThat(sets()).containsEntry("utf", "utf").containsEntry("bin", "bin").containsEntry("vuoto", "vuoto");
                    assertThat(List.of(membershipOf("utf/Rete.sln"), membershipOf("bin/Makefile"), membershipOf("vuoto/Makefile")))
                            .containsOnly(List.of());
                    assertThat(List.of(verdictKindsFor("utf/Rete.sln"), verdictKindsFor("bin/Makefile")))
                            .containsOnly(List.of());
                    assertThat(verdictKindsFor("vuoto/Makefile")).containsExactly(BROKEN);
                });
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("The pictures and companion files of a reference tree a generator wrote are kept as code with their set; a read-me, an unmarked page, a PDF and a picture outside the tree stay documents")
    void theFilesOfAGeneratedTreeAreKeptAsCodeAndItsDocumentsStayDocuments(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "proj/pom.xml", "<project><artifactId>proj</artifactId></project>\n");
        write(root, "proj/src/A.java", "class A {}\n");
        String apidocs = "proj/target/apidocs/";
        write(root, apidocs + "index.html", javadocPage("Overview"));
        write(root, apidocs + "element-list", "pkg\n");
        write(root, apidocs + "pkg/Api.html", javadocPage("Api"));
        write(root, apidocs + "resources/glass.png", png(apidocs + "resources/glass.png"));
        write(root, apidocs + "stylesheet.css", "body { margin: 0; }\n");
        write(root, apidocs + "script.js", "function show(id) { return id; }\n");
        write(root, apidocs + "member-search-index.js", "memberSearchIndex = [];\n");
        write(root, apidocs + "README.md", "# Le API\n\nCome leggerle.\n");
        write(root, apidocs + "guida.html", page("<title>Guida</title>", "<h1>Come si usa</h1><p>Una guida.</p>", ""));
        write(root, apidocs + "manuale.pdf", pdf(apidocs + "manuale.pdf"));
        write(root, "proj/target/foto.png", png("proj/target/foto.png"));
        String doxygen = "eng/doc/html/";
        write(root, "eng/CMakeLists.txt", "project(eng C)\n");
        write(root, "eng/m.c", "int m(void) { return 0; }\n");
        write(root, doxygen + "classA.html", page(
                "<meta name=\"generator\" content=\"Doxygen 1.9.8\"/>",
                "<div class=\"memitem\"><div class=\"memproto\">void run()</div></div>", ""));
        write(root, doxygen + "doxygen.css", "div.memitem { padding: 0; }\n");
        write(root, doxygen + "graph_legend.png", png(doxygen + "graph_legend.png"));
        write(root, doxygen + "classA__coll__graph.map", "<map id=\"A\" name=\"A\"></map>\n");
        write(root, doxygen + "classA__coll__graph.md5", "0f3c2a9d1e7b5c4f6a8b9c0d1e2f3a4b\n");

        stageOne(root, workingDirectory);

        Map<String, String> companions = new TreeMap<>(Map.of(
                apidocs + "element-list", "Javadoc proj",
                apidocs + "resources/glass.png", "Javadoc proj",
                apidocs + "stylesheet.css", "Javadoc proj",
                apidocs + "script.js", "Javadoc proj",
                apidocs + "member-search-index.js", "Javadoc proj",
                doxygen + "doxygen.css", "Doxygen eng",
                doxygen + "graph_legend.png", "Doxygen eng",
                doxygen + "classA__coll__graph.map", "Doxygen eng",
                doxygen + "classA__coll__graph.md5", "Doxygen eng"));
        companions.forEach((file, generatorAndSet) -> {
            String generator = generatorAndSet.substring(0, generatorAndSet.indexOf(' '));
            String set = generatorAndSet.substring(generatorAndSet.indexOf(' ') + 1);
            claim(
                    file + " is kept as code, the reason naming " + generator + ", and is a companion member of the"
                            + " set at " + set,
                    () -> {
                        assertThat(verdictReasonsFor(file)).containsExactly(TREE_REASON.formatted(generator));
                        assertThat(membershipOf(file)).containsExactly("companion in '" + set + "' by " + generator);
                    });
        });
        claim(
                "a picture kept with its tree is still a picture: what its bytes are is unchanged",
                () -> assertThat(storedFormatOf(apidocs + "resources/glass.png")).isEqualTo("IMAGE"));
        claim(
                "a reference page of the tree is a member as code, recorded with its generator",
                () -> assertThat(membershipOf(apidocs + "pkg/Api.html")).containsExactly("code in 'proj' by Javadoc"));
        claim(
                "a read-me, a guide with no generator's mark and a PDF inside the tree, and a picture outside it, carry no"
                        + " verdict and are no members",
                () -> {
                    for (String document : List.of(
                            apidocs + "README.md", apidocs + "guida.html", apidocs + "manuale.pdf", "proj/target/foto.png")) {
                        assertThat(verdictKindsFor(document)).as(document).isEmpty();
                        assertThat(membershipOf(document)).as(document).isEmpty();
                    }
                });
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("One saved reference page with none of its generator's companion files beside it makes no tree, and the notes and photos beside it stay documents")
    void aLoneMarkedPageRootsNoTree(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "Downloads/Api.html", javadocPage("Api"));
        write(root, "Downloads/notes.md", "# Notes\n\nThings to remember.\n");
        write(root, "Downloads/lista.txt", text("Downloads/lista.txt"));
        write(root, "Downloads/photo.png", png("Downloads/photo.png"));

        stageOne(root, workingDirectory);

        claim(
                "the page is code, in the set of its first folder",
                () -> assertThat(membershipOf("Downloads/Api.html")).containsExactly("code in 'Downloads' by Javadoc"));
        claim(
                "the notes, the list and the photo beside it carry no verdict and are no members",
                () -> {
                    for (String document : List.of("Downloads/notes.md", "Downloads/lista.txt", "Downloads/photo.png")) {
                        assertThat(verdictKindsFor(document)).as(document).isEmpty();
                        assertThat(membershipOf(document)).as(document).isEmpty();
                    }
                });
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("The same reference tree under two folders is two code sets, each keeping its own pictures")
    void twoIdenticalTreesAreTwoSets(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        byte[] logo = png("logo.png");
        for (String copy : List.of("a", "b")) {
            write(root, copy + "/doc/index.html", javadocPage("Overview"));
            write(root, copy + "/doc/element-list", "pkg\n");
            write(root, copy + "/doc/logo.png", logo);
        }

        stageOne(root, workingDirectory);

        claim(
                "each copy of the tree is a set of its own",
                () -> assertThat(sets()).isEqualTo(Map.of("a", "a", "b", "b")));
        claim(
                "each identical picture is kept as code with its own set, and neither supersedes the other",
                () -> {
                    assertThat(membershipOf("a/doc/logo.png")).containsExactly("companion in 'a' by Javadoc");
                    assertThat(membershipOf("b/doc/logo.png")).containsExactly("companion in 'b' by Javadoc");
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM superseded_by", Integer.class)).isZero();
                });
    }

    @Test
    @Story("Documents inside a code set stay documents")
    @DisplayName("A read-me, a PDF, a picture and a note lying in a code set carry no verdict and are no members of it")
    void documentsInsideASetStayDocuments(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "proj/pom.xml", "<project><artifactId>proj</artifactId></project>\n");
        write(root, "proj/Main.java", "class Main {}\n");
        write(root, "proj/README.md", "# Proj\n\nWhat this project does, in prose.\n");
        write(root, "proj/docs/manuale.pdf", pdf("proj/docs/manuale.pdf"));
        write(root, "proj/docs/schema.png", png("proj/docs/schema.png"));
        write(root, "proj/docs/note.txt", text("proj/docs/note.txt"));

        stageOne(root, workingDirectory);

        for (String document : List.of("proj/README.md", "proj/docs/manuale.pdf", "proj/docs/schema.png", "proj/docs/note.txt")) {
            claim(
                    document + " carries no verdict and is no member: relevance decides it as any document",
                    () -> {
                        assertThat(verdictKindsFor(document)).isEmpty();
                        assertThat(membershipOf(document)).isEmpty();
                    });
        }
    }

    @Test
    @Story("What stage 1 already removed inside a code set stays removed")
    @DisplayName("A log, an empty file and a file left out of scope inside a code set keep their one verdict and are no members; a build file left out of scope still marks its folder")
    void aLogAnEmptyFileAndAFileOutOfScopeInsideASetKeepTheirVerdicts(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile()
                .logTimestampShareFloor(A_LOG_FLOOR, SET_BY_THIS_TEST)
                .outOfScopeNameEndings(".bak .json", SET_BY_THIS_TEST)
                .build());
        CodeNameEndingsInProfile.write(profileStore, CODE_ENDINGS, SET_BY_THIS_TEST);
        write(root, "srv/Makefile", "all:\n\tcc -o srv s.c\n");
        write(root, "srv/s.c", "int s(void) { return 0; }\n");
        write(root, "srv/logs/server.out", timestampedLines());
        write(root, "srv/vuoto.c", new byte[0]);
        write(root, "srv/vecchio.bak", text("srv/vecchio.bak"));
        write(root, "srv/sub/package.json", "{\"name\": \"sub\"}\n");
        write(root, "srv/sub/x.c", "int x(void) { return 0; }\n");

        stageOneWithItsProfile(root, workingDirectory);

        claim(
                "the log keeps its one out-of-scope verdict, the empty file its one broken verdict, and the two files of"
                        + " a listed ending their one out-of-scope verdict each",
                () -> assertThat(List.of(
                                verdictKindsFor("srv/logs/server.out"),
                                verdictKindsFor("srv/vuoto.c"),
                                verdictKindsFor("srv/vecchio.bak"),
                                verdictKindsFor("srv/sub/package.json")))
                        .containsExactly(List.of(OUT_OF_SCOPE), List.of(BROKEN), List.of(OUT_OF_SCOPE), List.of(OUT_OF_SCOPE)));
        claim(
                "none of them is a member",
                () -> assertThat(List.of(
                                membershipOf("srv/logs/server.out"),
                                membershipOf("srv/vuoto.c"),
                                membershipOf("srv/vecchio.bak"),
                                membershipOf("srv/sub/package.json")))
                        .containsOnly(List.of()));
        claim(
                "the build file left out of scope still marks its folder, so the code beside it is a set of its own",
                () -> {
                    assertThat(sets()).isEqualTo(Map.of("srv", "srv", "srv/sub", "sub"));
                    assertThat(membershipOf("srv/sub/x.c")).containsExactly("code in 'srv/sub'");
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
    @Story("What the format-mix page says of code sets")
    @DisplayName("The format-mix page counts the code sets and their members by kind, tables the build files and the files of each generator's tree, and names no folder")
    void theFormatMixPageCountsTheSetsAndTheirMembersAndNamesNone(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "Main.java", "class Main {}\n");
        write(root, "proj/pom.xml", "<project><artifactId>proj</artifactId></project>\n");
        write(root, "proj/A.java", "class A {}\n");
        write(root, "proj/api/index.html", javadocPage("Overview"));
        write(root, "proj/api/element-list", "pkg\n");
        write(root, "lib/v.c", "int v(void) { return 0; }\n");

        stageOne(root, workingDirectory);

        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page counts three sets, whose members are four files of code, one build file and one file of a"
                        + " generated tree",
                () -> assertThat(html).contains("Code sets: 3, whose members are files of code: 4, build files: 1, and"
                        + " files of generated reference trees: 1."));
        claim(
                "the count of what was kept as code is still of the four files kept as code for themselves",
                () -> assertThat(countIn(html, KEPT_AS_CODE)).isEqualTo(4));
        String keptAsCode = section(html, KEPT_AS_CODE_HEADING);
        claim(
                "under its heading for code it tables the build files and the files of the tree each generator wrote",
                () -> assertThat(keptAsCode)
                        .contains("<td>build file</td><td class=\"count\">1</td>")
                        .contains("<td>file of a tree Javadoc generated</td><td class=\"count\">1</td>"));
        claim(
                "and it names no folder of a set",
                () -> assertThat(html).doesNotContain("proj/api").doesNotContain("proj/"));
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

    @Test
    @Story("Renaming a code set moves no piece of work")
    @DisplayName("A rename written beside the database is not read by stage 1: the code set keeps its default name and the settings stage 1 records hold no name")
    void aRenameIsNotReadByStageOne(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "m/Makefile", "all:\n\tcc a.c\n");
        write(root, "m/a.c", "int a(void) { return 0; }\n");
        Files.createDirectories(workingDirectory);
        Files.writeString(
                workingDirectory.resolve(CODE_SET_NAMES), "- folder: m\n  name: motore\n", StandardCharsets.UTF_8);

        stageOne(root, workingDirectory);

        claim(
                "the set is recorded under its default name",
                () -> assertThat(sets()).isEqualTo(Map.of("m", "m")));
        claim(
                "the settings stage 1 records name neither the file nor the name it gives",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .doesNotContain("motore")
                        .doesNotContain(CODE_SET_NAMES));
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("Nested reference trees are rooted at the highest folder; a file in two generators' trees goes to the deeper root, and where both roots are one folder, to the generator listed first")
    void nestedTreesAndTreesOfTwoGeneratorsAreToldApartByTheirRoots(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        // Javadoc's marked page and companion in p/doc, and again two folders below, in p/doc/mid/deep; Doxygen's
        // between them, in p/doc/mid.
        write(root, "p/doc/index.html", javadocPage("Overview"));
        write(root, "p/doc/element-list", "pkg\n");
        write(root, "p/doc/top.png", png("p/doc/top.png"));
        write(root, "p/doc/mid/classA.html", doxygenPage());
        write(root, "p/doc/mid/doxygen.css", "div.memitem { padding: 0; }\n");
        write(root, "p/doc/mid/graph.png", png("p/doc/mid/graph.png"));
        write(root, "p/doc/mid/deep/Api.html", javadocPage("Api"));
        write(root, "p/doc/mid/deep/element-list", "pkg.deep\n");
        write(root, "p/doc/mid/deep/glass.png", png("p/doc/mid/deep/glass.png"));
        // Javadoc's and Doxygen's marked pages and companions in one folder, q/html.
        write(root, "q/html/index.html", javadocPage("Overview"));
        write(root, "q/html/element-list", "qpkg\n");
        write(root, "q/html/classB.html", doxygenPage());
        write(root, "q/html/doxygen.css", "div.memitem { margin: 0; }\n");
        write(root, "q/html/shared.png", png("q/html/shared.png"));

        stageOne(root, workingDirectory);

        claim(
                "a picture in the highest of Javadoc's nested folders is a file of Javadoc's tree",
                () -> assertThat(membershipOf("p/doc/top.png")).containsExactly("companion in 'p' by Javadoc"));
        claim(
                "a picture in Doxygen's folder, inside Javadoc's tree, is a file of Doxygen's, whose root is deeper",
                () -> assertThat(membershipOf("p/doc/mid/graph.png")).containsExactly("companion in 'p' by Doxygen"));
        claim(
                "a picture beside Javadoc's lower marked page and companion is still a file of Doxygen's tree: Javadoc's"
                        + " tree is rooted at the highest of its folders, p/doc, so the deeper root above it is"
                        + " Doxygen's, p/doc/mid",
                () -> {
                    assertThat(membershipOf("p/doc/mid/deep/glass.png")).containsExactly("companion in 'p' by Doxygen");
                    assertThat(verdictReasonsFor("p/doc/mid/deep/glass.png"))
                            .containsExactly(TREE_REASON.formatted("Doxygen"));
                });
        claim(
                "where the two generators' trees are rooted at one folder, its picture is a file of Javadoc's tree,"
                        + " the generator ADR-277 lists first",
                () -> {
                    assertThat(membershipOf("q/html/shared.png")).containsExactly("companion in 'q' by Javadoc");
                    assertThat(verdictReasonsFor("q/html/shared.png")).containsExactly(TREE_REASON.formatted("Javadoc"));
                });
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("A page kept as code for the ending of its name is recorded with no generator and roots no tree, though it carries a generator's mark")
    void aPageKeptForTheEndingOfItsNameRootsNoTreeAndHasNoGenerator(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS + " .html", SET_BY_THIS_TEST);
        write(root, "saved/Api.html", javadocPage("Api"));
        write(root, "saved/element-list", "pkg\n");
        write(root, "saved/logo.png", png("saved/logo.png"));

        stageOneWithItsProfile(root, workingDirectory);

        claim(
                "the page is a member as code, with no generator: the first pass kept it for its ending, before any mark",
                () -> assertThat(membershipOf("saved/Api.html")).containsExactly("code in 'saved'"));
        claim(
                "so it roots no tree: the companion file and the picture beside it carry no verdict and are no members",
                () -> {
                    for (String file : List.of("saved/element-list", "saved/logo.png")) {
                        assertThat(verdictKindsFor(file)).as(file).isEmpty();
                        assertThat(membershipOf(file)).as(file).isEmpty();
                    }
                });
    }

    @Test
    @Story("A code set's build files stay with it")
    @DisplayName("A build file or a tree's companion the set pass cannot read is left as it was and logged, still marks its folder or roots its tree, and the pass carries on")
    void aFileTheSetPassCannotReadIsLeftAsItWasAndThePassCarriesOn(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "gone/Makefile", "all:\n\tcc g.c\n");
        write(root, "gone/sub/g.c", "int g(void) { return 0; }\n");
        write(root, "t/doc/index.html", javadocPage("Overview"));
        write(root, "t/doc/element-list", "pkg\n");
        write(root, "t/doc/logo.png", png("t/doc/logo.png"));
        write(root, "ok/Makefile", "all:\n\tcc o.c\n");
        write(root, "ok/o.c", "int o(void) { return 0; }\n");
        // Each is removed once its kind is recorded: after detection has read it, before the set pass does.
        Map<String, Path> removedOnceDetected = Map.of(
                "gone/Makefile", root.resolve("gone/Makefile"), "t/doc/element-list", root.resolve("t/doc/element-list"));
        DetectedFormats removing = new DetectedFormats(jdbcTemplate) {
            @Override
            public void record(OccurrenceId occurrenceId, RunId runId, DetectedFormat format, DetectedSubtype subtype) {
                super.record(occurrenceId, runId, format, subtype);
                Path file = removedOnceDetected.get(pathOf(occurrenceId));
                if (file != null) {
                    deleteQuietly(file);
                }
            }
        };
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        List<String> logged = logging(() -> stageOneWithItsProfile(root, workingDirectory, new Ledger(jdbcTemplate), removing));

        claim(
                "neither file the set pass could not read carries a verdict, and neither is a member",
                () -> {
                    for (String file : removedOnceDetected.keySet()) {
                        assertThat(verdictKindsFor(file)).as(file).isEmpty();
                        assertThat(membershipOf(file)).as(file).isEmpty();
                    }
                });
        claim(
                "the build file still marks its folder, which goes by its name alone, so the code below it is that set's",
                () -> assertThat(membershipOf("gone/sub/g.c")).containsExactly("code in 'gone'"));
        claim(
                "the companion file still roots its tree, so the picture beside it is a file of Javadoc's tree",
                () -> assertThat(membershipOf("t/doc/logo.png")).containsExactly("companion in 't' by Javadoc"));
        claim(
                "each is logged once, in the whole of its line, as left as it was",
                () -> {
                    for (String file : removedOnceDetected.keySet()) {
                        String said = "[byte-level-reduction] could not read " + occurrenceIdOf(file)
                                + " to see whether it reads as text, so it is left as it was: ";
                        assertThat(logged.stream().filter(line -> line.startsWith(said) && line.length() > said.length()))
                                .as(file)
                                .hasSize(1);
                    }
                });
        claim(
                "and the pass carries on: the next set's build file is kept as code with it",
                () -> assertThat(membershipOf("ok/Makefile")).containsExactly("build in 'ok'"));
    }

    @Test
    @Story("A generated reference tree is kept whole")
    @DisplayName("A marked page the set pass cannot read again is a member as code with no generator and logged, and its tree is still found from the first pass's folders")
    void aMarkedPageTheSetPassCannotReadAgainIsKeptWithNoGenerator(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "r/doc/index.html", javadocPage("Overview"));
        write(root, "r/doc/element-list", "pkg\n");
        write(root, "r/doc/logo.png", png("r/doc/logo.png"));
        Path page = root.resolve("r/doc/index.html");
        // The page is removed once the first pass has kept it as code for its mark, before the set pass reads it.
        Ledger removing = new Ledger(jdbcTemplate) {
            private final Verdicts verdicts = new Verdicts(jdbcTemplate) {
                @Override
                public void verdict(OccurrenceId occurrenceId, RunId runId, VerdictKind kind, String reason) {
                    super.verdict(occurrenceId, runId, kind, reason);
                    if (kind == VerdictKind.CODE && "r/doc/index.html".equals(pathOf(occurrenceId))) {
                        deleteQuietly(page);
                    }
                }
            };

            @Override
            public Verdicts verdicts() {
                return verdicts;
            }
        };
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        List<String> logged = logging(() ->
                stageOneWithItsProfile(root, workingDirectory, removing, new DetectedFormats(jdbcTemplate)));

        claim(
                "the page keeps the first pass's one code verdict and is a member as code, with no generator",
                () -> {
                    assertThat(verdictKindsFor("r/doc/index.html")).containsExactly(CODE);
                    assertThat(membershipOf("r/doc/index.html")).containsExactly("code in 'r'");
                });
        claim(
                "its tree is still found, from the folders the first pass handed over, so the picture is a file of it",
                () -> assertThat(membershipOf("r/doc/logo.png")).containsExactly("companion in 'r' by Javadoc"));
        claim(
                "the page is logged once, in the whole of its line, as kept with no generator",
                () -> {
                    String said = "[byte-level-reduction] could not read " + occurrenceIdOf("r/doc/index.html")
                            + " to see which generator's mark it carries, so it is kept with none: ";
                    assertThat(logged.stream().filter(line -> line.startsWith(said) && line.length() > said.length()))
                            .hasSize(1);
                });
    }

    /** {@code text} in UTF-16, little-endian, behind its byte-order mark: text that holds bytes of nought. */
    private static byte[] utf16(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);
        return bytes;
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

    /** Stage 1, with every line it logs kept, in order. */
    private List<String> stageOneLogging(Path root, Path workingDirectory) throws Exception {
        return logging(() -> stageOne(root, workingDirectory));
    }

    /** What stage 1 does. */
    @FunctionalInterface
    private interface StageOne {
        void run() throws Exception;
    }

    /** Every line stage 1 logs while {@code stageOne} runs, in order. */
    private static List<String> logging(StageOne stageOne) throws Exception {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger stageLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ByteLevelReductionTasklet.class);
        stageLogger.addAppender(logged);
        try {
            stageOne.run();
        } finally {
            stageLogger.detachAppender(logged);
            logged.stop();
        }
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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

    /** The id of the occurrence at {@code path}, as stage 1's log names it. */
    private long occurrenceIdOf(String path) {
        return jdbcTemplate.queryForObject("SELECT id FROM file_occurrence WHERE path = ?", Long.class, path);
    }

    /** The path of {@code occurrence}. */
    private String pathOf(OccurrenceId occurrence) {
        return jdbcTemplate.queryForObject(
                "SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence.value());
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> verdictKindsFor(String path) {
        return jdbcTemplate.query(
                "SELECT v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("kind"),
                path);
    }

    private List<String> verdictReasonsFor(String path) {
        return jdbcTemplate.query(
                "SELECT v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("reason"),
                path);
    }

    private String storedFormatOf(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT d.format FROM detected_format d JOIN file_occurrence o ON o.id = d.occurrence_id WHERE o.path = ?",
                String.class,
                path);
    }

    private int contentHashRowsFor(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_hash h JOIN file_occurrence o ON o.id = h.occurrence_id WHERE o.path = ?",
                Integer.class,
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

    /** A PNG's leading bytes and {@code path}, so that no two pictures share their bytes. */
    private static byte[] png(String path) {
        byte[] tail = path.getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[PNG_HEAD.length + tail.length];
        System.arraycopy(PNG_HEAD, 0, bytes, 0, PNG_HEAD.length);
        System.arraycopy(tail, 0, bytes, PNG_HEAD.length, tail.length);
        return bytes;
    }

    /** A small PDF naming {@code path}. */
    private static String pdf(String path) {
        return "%PDF-1.4\n% " + path + "\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF\n";
    }

    /** A page Javadoc wrote, carrying its mark. */
    private static String javadocPage(String title) {
        return page(
                "<!-- Generated by javadoc (17) -->\n<title>" + title + "</title>\n"
                        + "<meta name=\"generator\" content=\"javadoc/ClassWriterImpl\">",
                "<body class=\"class-declaration-page\"><h1>" + title + "</h1>",
                "");
    }

    /** A class's page Doxygen wrote, carrying its mark and a reference page's markup. */
    private static String doxygenPage() {
        return page(
                "<meta name=\"generator\" content=\"Doxygen 1.9.8\"/>",
                "<div class=\"memitem\"><div class=\"memproto\">void run()</div></div>",
                "");
    }

    /** A small HTML page: {@code head} inside its head, {@code body} and {@code footer} inside its body. */
    private static String page(String head, String body, String footer) {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n" + head + "\n</head>\n<body>\n"
                + body + "\n" + footer + "\n</body>\n</html>\n";
    }

    /** {@link #LOG_LINES} lines, each beginning with a timestamp of its own. */
    private static String timestampedLines() {
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < LOG_LINES; line++) {
            log.append("[2023-02-08 12:00:").append(String.format(Locale.ROOT, "%02d", line))
                    .append(",096] INFO -- a request was served\n");
        }
        return log.toString();
    }

    /** The page from {@code heading} to the next heading of the same level, or nothing where it has no such heading. */
    private static String section(String html, String heading) {
        int from = html.indexOf(heading);
        if (from < 0) {
            return "";
        }
        int to = html.indexOf("<h2>", from + heading.length());
        return to < 0 ? html.substring(from) : html.substring(from, to);
    }

    /** The count the page prints on the line for {@code label}. */
    private static int countIn(String html, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[^0-9]{0,80}?([0-9]+)").matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("the page has no line for " + label + ": " + html);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
