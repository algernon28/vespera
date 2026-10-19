package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * An operational record that is neither a log nor a configuration file is left to relevance: stage 1 has no rule of
 * its own for one (ADR-293 §1). A JSON or YAML export keyed line by line is read as settings by ADR-287's rule, and
 * is left out or kept with its code set as that rule leaves a configuration file (§2); an XML export and a delimited
 * flow file carry no verdict of stage 1's (§1); and a flow file or a lookup-table export is left out by the ending
 * of its name once the operator lists it in {@code outOfScopeNameEndings}, a text file with a subtype included (§3).
 *
 * <p>No code was changed by the decision, so every test here holds what stage 1 already does, and fails where a
 * rule for records is added, or where the ending list stops reading a text file with a subtype. Every file is
 * written here, synthetic, each with contents of its own so that nothing a test judges is superseded by another.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Operational records")
@Issue("574")
@Link(name = "ADR-293", url = Adr.AN_OPERATIONAL_RECORD_THAT_IS_NOT_A_LOG_OR_A_CONFIGURATION_FILE_IS_LEFT_TO_RELEVANCE, type = "adr")
class OperationalRecordsTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** A setting-line floor of 50%, low enough for an export of records keyed line by line to reach. */
    private static final String HALF = "0.5";

    /** A log floor of 90%, so that a file whose lines begin with a timestamp would be left out as a log. */
    private static final String A_LOG_FLOOR = "0.9";

    /** The name endings listed as code where a test needs a code set. */
    private static final String CODE_ENDINGS = ".java";

    /** The corpus root's own folder name. */
    private static final String ROOT_NAME = "archivio";

    /** Records in each export: enough lines for a share to be taken. */
    private static final int RECORDS = 12;

    /** Records in a JSON or YAML export, each of three keys, {@code id}, {@code nome} and {@code stato}. */
    private static final int KEYED_RECORDS = 6;

    /**
     * The JSON export's share: six records of three keys, each record between a line of each brace, inside the two
     * lines of the array's brackets, are 18 keyed lines of 32 counted, 56% rounded down.
     */
    private static final int JSON_SHARE = 56;

    /**
     * The YAML export's share: six records of three keys, the first of each opening with the list's dash and so no
     * setting, are 12 keyed lines of 18 counted, 66% rounded down.
     */
    private static final int YAML_SHARE = 66;

    /** The reason a configuration file lying in no code set is left out with, by its share. */
    private static final String BY_ITS_SHARE = "a configuration file, and configuration files outside every code set are"
            + " out of scope: %d%% of the lines read from its start and end are settings";

    /** The reason a configuration file lying in a code set is kept with it as code. */
    private static final String IN_ITS_SET = "a configuration file of the code set at %s";

    /** The reason a file left out for the ending of its name carries, with the ending to fill in. */
    private static final String BY_ITS_NAME =
            "a file whose name ends with %s, an ending outOfScopeNameEndings in profile.yaml lists as out of scope";

    private static final String CODE = "CODE";

    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("An export of records keyed line by line reads as settings")
    @DisplayName("A JSON or YAML export of records written one key to a line reads as settings: lying in no code set it is left out as a configuration file, and lying in one it is kept with it")
    void anExportOfRecordsKeyedLineByLineIsLeftAsAConfigurationFileIs(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "esportazioni/ordini.json", jsonRecords("ordine"));
        write(root, "esportazioni/clienti.yaml", yamlRecords("cliente"));
        write(root, "shop/Main.java", "class Main {}\n");
        write(root, "shop/fixtures/ordini.json", jsonRecords("prova"));
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .codeNameEndings(CODE_ENDINGS, SET_BY_THIS_TEST)
                .configurationSettingShareFloor(HALF, SET_BY_THIS_TEST)
                .build());

        stageOne(root, workingDirectory);

        claim(
                "a JSON array of six records of three keys, one key to a line, is " + JSON_SHARE + "% settings, 18 of"
                        + " its 32 counted lines, and lying in no code set is left out as a configuration file",
                () -> assertThat(verdictReasonsFor("esportazioni/ordini.json"))
                        .containsExactly(BY_ITS_SHARE.formatted(JSON_SHARE)));
        claim(
                "a YAML list of six records of three keys is " + YAML_SHARE + "% settings, the twelve keys after each"
                        + " record's dash of its 18 lines, and is left out as a configuration file too",
                () -> assertThat(verdictReasonsFor("esportazioni/clienti.yaml"))
                        .containsExactly(BY_ITS_SHARE.formatted(YAML_SHARE)));
        claim(
                "the same shape of export lying in a code set is kept with it as code, a member of the set as"
                        + " configuration",
                () -> {
                    assertThat(verdictKindsFor("shop/fixtures/ordini.json")).containsExactly(CODE);
                    assertThat(verdictReasonsFor("shop/fixtures/ordini.json")).containsExactly(IN_ITS_SET.formatted("shop"));
                    assertThat(membershipOf("shop/fixtures/ordini.json")).containsExactly("configuration in 'shop'");
                });
    }

    @Test
    @Story("An operational record no rule reaches is left to relevance")
    @DisplayName("An XML export of records, a delimited flow file, a tab-separated export and a comma-separated export carry no verdict from the first stage, with both floors set")
    void anExportNoRuleReachesCarriesNoVerdict(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "esportazioni/ordini.xml", xmlRecords());
        write(root, "flussi/FLUSSO0001.dat", delimited(";", "ORD%04d", "ESERCENTE %d", "10,%02d", "EUR", "APPROVATO"));
        write(root, "flussi/esercenti.tsv", delimited("\t", "E%04d", "Bar Centrale %d", "Via Roma %d", "Milano"));
        write(root, "esportazioni/listino.csv",
                "codice,descrizione,prezzo\n" + delimited(",", "P%04d", "articolo %d", "%d.50"));
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .logTimestampShareFloor(A_LOG_FLOOR, SET_BY_THIS_TEST)
                .configurationSettingShareFloor(HALF, SET_BY_THIS_TEST)
                .build());

        stageOne(root, workingDirectory);

        claim(
                "an XML export of " + RECORDS + " records under a first element of its own, a flow file of "
                        + RECORDS + " rows separated by semicolons, a tab-separated export and a comma-separated one"
                        + " are neither logs nor configuration files under a log floor of 90% and a settings floor of"
                        + " 50%, and none carries a verdict: what becomes of each is relevance's",
                () -> {
                    for (String path : List.of(
                            "esportazioni/ordini.xml", "flussi/FLUSSO0001.dat", "flussi/esercenti.tsv",
                            "esportazioni/listino.csv")) {
                        assertThat(verdictKindsFor(path)).as(path).isEmpty();
                    }
                });
    }

    @Test
    @Story("The operator lists the endings of flow files and exports")
    @DisplayName("A flow file and a comma-separated export are left out once the endings of their names are listed, the export though its name gives it a finer kind")
    void aFlowFileAndAnExportAreLeftOutOnceTheirEndingsAreListed(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "flussi/FLUSSO0001.ok", "OK\n");
        write(root, "esportazioni/listino.csv",
                "codice,descrizione,prezzo\n" + delimited(",", "P%04d", "articolo %d", "%d.50"));
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .outOfScopeNameEndings(".ok .csv", SET_BY_THIS_TEST)
                .build());

        stageOne(root, workingDirectory);

        claim(
                "the flow file is left out for the ending of its name, .ok",
                () -> assertThat(verdictReasonsFor("flussi/FLUSSO0001.ok")).containsExactly(BY_ITS_NAME.formatted(".ok")));
        claim(
                "the export is text whose name gives it the finer kind of comma-separated values",
                () -> assertThat(storedFormatOf("esportazioni/listino.csv")).isEqualTo("PLAIN_TEXT CSV"));
        claim(
                "and it is left out for the ending of its name all the same, .csv, as out of scope",
                () -> {
                    assertThat(verdictKindsFor("esportazioni/listino.csv")).containsExactly(OUT_OF_SCOPE);
                    assertThat(verdictReasonsFor("esportazioni/listino.csv")).containsExactly(BY_ITS_NAME.formatted(".csv"));
                });
    }

    /** Stage 1 over {@code root}, walked first, with the profile as it stands. */
    private void stageOne(Path root, Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        new ImplementationVersions(),
                        new ProfileStore(workingDirectory),
                        root,
                        workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());
    }

    /** A JSON array of {@link #KEYED_RECORDS} records of three keys, pretty-printed one key to a line. */
    private static String jsonRecords(String name) {
        StringBuilder text = new StringBuilder("[\n");
        for (int record = 0; record < KEYED_RECORDS; record++) {
            text.append("  {\n")
                    .append("    \"id\": ").append(record).append(",\n")
                    .append("    \"nome\": \"").append(name).append(' ').append(record).append("\",\n")
                    .append("    \"stato\": \"attivo\"\n")
                    .append(record < KEYED_RECORDS - 1 ? "  },\n" : "  }\n");
        }
        return text.append("]\n").toString();
    }

    /** A YAML list of {@link #KEYED_RECORDS} records of three keys, the first key of each after its dash. */
    private static String yamlRecords(String name) {
        StringBuilder text = new StringBuilder();
        for (int record = 0; record < KEYED_RECORDS; record++) {
            text.append("- id: ").append(record).append('\n')
                    .append("  nome: ").append(name).append(' ').append(record).append('\n')
                    .append("  stato: attivo\n");
        }
        return text.toString();
    }

    /** An XML export of {@link #RECORDS} records, pretty-printed, each field an element on a line of its own. */
    private static String xmlRecords() {
        StringBuilder text = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ordini>\n");
        for (int record = 0; record < RECORDS; record++) {
            text.append("  <ordine>\n")
                    .append("    <id>").append(record).append("</id>\n")
                    .append("    <cliente>cliente ").append(record).append("</cliente>\n")
                    .append("    <importo>").append(record).append(".50</importo>\n")
                    .append("  </ordine>\n");
        }
        return text.append("</ordini>\n").toString();
    }

    /** {@link #RECORDS} rows of {@code fields}, each a format given the row's number, joined by {@code separator}. */
    private static String delimited(String separator, String... fields) {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < RECORDS; row++) {
            int number = row;
            text.append(String.join(separator, Arrays.stream(fields)
                            .map(field -> String.format(Locale.ROOT, field, number))
                            .toList()))
                    .append('\n');
        }
        return text.toString();
    }

    /** What the occurrence at {@code path} is a member of, as {@code <role> in '<folder>'}. */
    private List<String> membershipOf(String path) {
        return jdbcTemplate.query(
                "SELECT m.role, m.folder FROM code_set_member m JOIN file_occurrence o ON o.id = m.occurrence_id"
                        + " WHERE o.path = ?",
                (row, n) -> row.getString("role") + " in '" + row.getString("folder") + "'",
                path);
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

    /** The detected format stored for {@code path}, then its subtype where it has one, a space between. */
    private String storedFormatOf(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT d.format || COALESCE(' ' || d.subtype, '') FROM detected_format d"
                        + " JOIN file_occurrence o ON o.id = d.occurrence_id WHERE o.path = ?",
                String.class,
                path);
    }

    /** A folder named {@link #ROOT_NAME} inside {@code temp}, to walk as the corpus root. */
    private static Path namedRoot(Path temp) throws IOException {
        return Files.createDirectory(temp.resolve(ROOT_NAME));
    }

    private static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
