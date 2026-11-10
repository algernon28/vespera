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
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * A code set's folder the profile's {@code codeSetFolders} names outside the corpus root is read with the corpus
 * (ADR-334 §5.3 to §5.5): the census walks it after the corpus root under the corpus root's one walk id, each
 * occurrence below it spelt by the folder's real path, absolute, with {@code /} between its elements; and stage 1 makes
 * the folder a set's folder, as the first folder under the corpus root would be, named for its last element, with its
 * build file and its code as members.
 *
 * <p>Assembled by hand as {@code CensusTaskletTest} and {@code CodeSetTaskletTest} are. The profile is written as text
 * and the tables read by their names, so this compiles before {@code Profile} has the key; until it does, the census
 * fails on the first load of a profile naming it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Code sets")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-284", url = Adr.CODE_IS_KEPT_IN_CODE_SETS_NAMED_FOR_THEIR_FOLDER, type = "adr")
class ACodeSetFolderOutsideTheCorpusRootIsWalkedWithTheCorpusTest {

    private static final Instant CENSUS_RAN_AT = Instant.parse("2026-11-03T09:00:00Z");

    /** The one walk the census records of the corpus root and the folder outside it together. */
    private static final long ONE_WALK = 1;

    /** The note lying in the corpus, by its path below the corpus root. */
    private static final String THE_NOTE = "notes/appunti.txt";

    /** The build file and the code lying in the folder outside the corpus root, by their paths below it. */
    private static final String THE_BUILD_FILE = "pom.xml";

    private static final String THE_CODE = "src/main/java/Billing.java";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("Code kept beside the archive is read with it")
    @DisplayName("A folder of code outside the corpus root is walked with it, in one walk, each of its files by the folder's whole path")
    void theFolderIsWalkedWithTheCorpusUnderOneWalk(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = Files.createDirectory(temp.resolve("archivio"));
        Path billing = theBillingEngine(temp);
        write(root, THE_NOTE, "a note about the archive, which is a document and no code\n");
        theProfileNames(workingDirectory, billing);
        String spelt = speltAsOccurrences(billing);

        census(root, workingDirectory);

        claim("the census records one walk, of the corpus root, and none of its own for the folder outside it",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class)).isEqualTo(ONE_WALK));
        claim("the walk holds the note by its path below the corpus root, and the folder's two files by the folder's"
                        + " whole path, with / between its elements",
                () -> assertThat(jdbcTemplate.queryForList("SELECT path FROM file_occurrence ORDER BY path", String.class))
                        .containsExactlyInAnyOrder(THE_NOTE, spelt + "/" + THE_BUILD_FILE, spelt + "/" + THE_CODE));
    }

    @Test
    @Story("Code kept beside the archive is read with it")
    @DisplayName("Stage 1 keeps the code of a folder outside the corpus root as a set at that folder, named for it, with its build file")
    void stageOneMakesTheFolderASetOfItsOwn(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = Files.createDirectory(temp.resolve("archivio"));
        Path billing = theBillingEngine(temp);
        write(root, THE_NOTE, "a note about the archive, which is a document and no code\n");
        theProfileNames(workingDirectory, billing);
        String spelt = speltAsOccurrences(billing);

        census(root, workingDirectory);
        stageOne(root, workingDirectory);

        claim("there is one set, at the folder outside the corpus root spelt as its files' paths spell it, named for its"
                        + " last element",
                () -> assertThat(sets()).isEqualTo(Map.of(spelt, billing.getFileName().toString())));
        claim("its code and its build file are its members",
                () -> assertThat(List.of(membershipOf(spelt + "/" + THE_CODE), membershipOf(spelt + "/" + THE_BUILD_FILE)))
                        .containsExactly(List.of("code in '" + spelt + "'"), List.of("build in '" + spelt + "'")));
        claim("and the note of the corpus lies in no set",
                () -> assertThat(membershipOf(THE_NOTE)).isEmpty());
    }

    /** A folder outside the corpus root holding a Maven build and one class of it. */
    private static Path theBillingEngine(Path temp) throws IOException {
        Path billing = Files.createDirectories(temp.resolve("elsewhere").resolve("billing"));
        write(billing, THE_BUILD_FILE, "<project><artifactId>billing</artifactId></project>\n");
        write(billing, THE_CODE, "public class Billing { }\n");
        return billing;
    }

    /** The folder's real path with {@code /} between its elements, as ADR-334 §5.4 spells its occurrences. */
    private static String speltAsOccurrences(Path folder) throws IOException {
        return folder.toRealPath().toString().replace(File.separatorChar, '/');
    }

    /** A profile listing {@code .java} as code and naming {@code folder} as a code set's folder outside the corpus root. */
    private static void theProfileNames(Path workingDirectory, Path folder) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), """
                ---
                codeNameEndings:
                  value: ".java"
                  provenance: "set by this test"
                  measurement: null
                codeSetFolders:
                  value: '%s'
                  provenance: "set by this test"
                  measurement: null
                """.formatted(folder.toAbsolutePath()), StandardCharsets.UTF_8);
    }

    private void census(Path root, Path workingDirectory) throws Exception {
        WalkRecorder walkRecorder = new WalkRecorder(
                new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
        new CensusTasklet(walkRecorder, new ProfileStore(workingDirectory), Clock.fixed(CENSUS_RAN_AT, ZoneOffset.UTC), root)
                .execute(null, null);
    }

    private void stageOne(Path root, Path workingDirectory) throws Exception {
        new ByteLevelReductionTasklet(
                        new Ledger(jdbcTemplate),
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        new ImplementationVersions(),
                        new ProfileStore(workingDirectory),
                        root,
                        workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());
    }

    /** Each set of the one run, by folder, with its default name. */
    private Map<String, String> sets() {
        Map<String, String> sets = new TreeMap<>();
        jdbcTemplate.query("SELECT folder, default_name FROM code_set",
                row -> {
                    sets.put(row.getString("folder"), row.getString("default_name"));
                });
        return sets;
    }

    /** What the occurrence at {@code path} is a member of, as {@code <role> in '<folder>'}. */
    private List<String> membershipOf(String path) {
        return jdbcTemplate.query(
                "SELECT m.role, m.folder FROM code_set_member m JOIN file_occurrence o ON o.id = m.occurrence_id"
                        + " WHERE o.path = ?",
                (row, n) -> row.getString("role") + " in '" + row.getString("folder") + "'",
                path);
    }

    private static void write(Path folder, String path, String text) throws IOException {
        Path file = folder.resolve(path);
        Files.createDirectories(file.getParent() == null ? folder : file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
