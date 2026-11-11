package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormat;
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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Stage 1 tells a configuration file by its bytes (ADR-287 §3): by its first XML element, where that is one of a
 * program's configuration, or by the share of its lines that are settings, under the profile's
 * {@code configurationSettingShareFloor}; an interface definition is never one. A third pass, after the set pass
 * and before the hashing, keeps one lying in a code set as code, a member of the set as {@code configuration}, and
 * leaves one lying in no set out of scope (§4); a build file outside every set is judged so too, by its content (§6).
 * With the floor unset nothing is a configuration file, and the page still shows what was measured (§7, §8).
 *
 * <p>The floor is written through {@link ConfigurationSettingShareFloorInProfile}, the tables are read by their names
 * and the verdicts by their stored names, so this compiles before the key, the rule and the pass exist; until they do,
 * a test writing the key fails where the profile is loaded, and the others on what stage 1 has not written. Every
 * file is built here, each with contents of its own, so that nothing a test means to judge is superseded by another.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Configuration files")
@Issue("575")
@Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
class ConfigurationFilesTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** The floor most tests run under: a file of ten counted lines or more, 80% of them settings, is one. */
    private static final String FLOOR = "0.8";

    /** The corpus root's own folder name. */
    private static final String ROOT_NAME = "archivio";

    /** Lines in a file of settings: more than the ten a share is taken over. */
    private static final int SETTINGS = 12;

    /** The fewest counted lines a share is taken over, less one. */
    private static final int NINE = 9;

    /** The verdicts' stored names. */
    private static final String CODE = "CODE";

    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    private static final String SUPERSEDED_BY = "SUPERSEDED_BY";

    /** The reason a configuration file lying in no code set is left out with, by its share. */
    private static final String BY_ITS_SHARE = "a configuration file, and configuration files outside every code set are"
            + " out of scope: %d%% of the lines read from its start and end are settings";

    /** The reason, by a first element in no namespace. */
    private static final String BY_ITS_ROOT = "a configuration file, and configuration files outside every code set are"
            + " out of scope: its first element is %s, in no namespace";

    /** How long the listener a DTD would be fetched from waits for a connection before it looks again. */
    private static final int LISTENER_WAIT_MILLIS = 100;

    /** A single-byte encoding in which {@code è} is one byte that does not decode as UTF-8 here. */
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A configuration file is told from its content")
    @DisplayName("A file whose lines are mostly settings, lying in no code set, is left out, whatever its name; a guide, a short list, Markdown, comma-separated values and lines with no space after the colon are kept")
    void aFileMostlyOfSettingsOutsideEveryCodeSetIsOutOfScope(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "impostazioni/server.properties", "# the server's settings\n! a comment\n; another\n"
                + "// and another\n   # an indented comment\n\n" + settings("server") + "\n");
        write(root, "impostazioni/app.ini", "[server]\n" + keyed("app", SETTINGS - 1, " = "));
        write(root, "impostazioni/app.yaml", "server:\n" + indented(keyed("yaml", SETTINGS - 1, ": ")));
        write(root, "impostazioni/settings.json", json("settings", SETTINGS - 2));
        write(root, "impostazioni/senza-nome", settings("senza-nome"));
        write(root, "impostazioni/vecchio.cfg", settings("vecchio").replace("valore", "caffè").getBytes(WINDOWS_1252));
        write(root, "note/guida.txt", guide());
        write(root, "note/elenco.txt", keyed("elenco", NINE, "="));
        write(root, "note/leggimi.md", settings("leggimi"));
        write(root, "note/tabella.csv", settings("tabella"));
        write(root, "note/due-punti.txt", keyed("due", SETTINGS, ":"));

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "a Properties file whose comments of every kind are not counted, an INI file, a YAML file and a file"
                        + " with no ending are each wholly settings, and left out with the share in the reason",
                () -> {
                    for (String path : List.of(
                            "impostazioni/server.properties", "impostazioni/app.ini", "impostazioni/app.yaml",
                            "impostazioni/senza-nome")) {
                        assertThat(verdictReasonsFor(path)).as(path).containsExactly(BY_ITS_SHARE.formatted(100));
                    }
                });
        claim(
                "a JSON file of ten keys between two braces is 10 of its 12 counted lines, 83% rounded down, and is left out",
                () -> assertThat(verdictReasonsFor("impostazioni/settings.json")).containsExactly(BY_ITS_SHARE.formatted(83)));
        claim(
                "a file of settings in a single-byte encoding is of no known kind, reads as text, and is left out too,"
                        + " its detected format unchanged",
                () -> {
                    assertThat(storedFormatOf("impostazioni/vecchio.cfg")).isEqualTo(DetectedFormat.UNRECOGNISED.name());
                    assertThat(verdictReasonsFor("impostazioni/vecchio.cfg")).containsExactly(BY_ITS_SHARE.formatted(100));
                });
        claim(
                "a guide quoting three settings among its prose, a list of only nine settings, a Markdown file and a"
                        + " comma-separated file of settings, and a file whose colons no space follows are all kept",
                () -> {
                    for (String path : List.of(
                            "note/guida.txt", "note/elenco.txt", "note/leggimi.md", "note/tabella.csv",
                            "note/due-punti.txt")) {
                        assertThat(verdictKindsFor(path)).as(path).isEmpty();
                    }
                });
        claim(
                "stage 1 records the floor it ran under among what it consumed",
                () -> assertThat(configurationConsumed()).contains("\"configurationSettingShareFloor\":" + FLOOR));
    }

    @Test
    @Story("A configuration file is told from its content")
    @DisplayName("Reading a file's first element fetches no document type and resolves no entity it declares")
    void noDtdIsFetchedAndNoEntityResolved(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(LISTENER_WAIT_MILLIS);
            Thread listening = Thread.ofVirtual().start(() -> {
                while (!listener.isClosed()) {
                    try (Socket ignored = listener.accept()) {
                        connections.incrementAndGet();
                    } catch (SocketTimeoutException stillWaiting) {
                        // nothing connected yet; look again until the listener is closed
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
            String where = "http://127.0.0.1:" + listener.getLocalPort();
            write(root, "xml/con-dtd.xml", "<?xml version=\"1.0\"?>\n"
                    + "<!DOCTYPE configuration SYSTEM \"" + where + "/configuration.dtd\" [\n"
                    + "  <!ENTITY outside SYSTEM \"" + where + "/entity\">\n"
                    + "]>\n<configuration>&outside;</configuration>\n");

            stageOne(root, workingDirectory, FLOOR);

            listener.close();
            listening.join();
        }

        claim(
                "the file's first element is read, so it is left out as logback's configuration",
                () -> assertThat(verdictReasonsFor("xml/con-dtd.xml")).containsExactly(BY_ITS_ROOT.formatted("configuration")));
        claim(
                "and nothing connected to the address its document type and its entity name: neither was fetched",
                () -> assertThat(connections.get()).isZero());
    }

    /** Stage 1 over {@code root}, under the floor {@code floor} written into the profile beside what it holds. */
    private void stageOne(Path root, Path workingDirectory, String floor) throws Exception {
        ConfigurationSettingShareFloorInProfile.write(new ProfileStore(workingDirectory), floor, SET_BY_THIS_TEST);
        stageOne(root, workingDirectory);
    }

    /** Stage 1 over {@code root}, with the profile as it stands. */
    private void stageOne(Path root, Path workingDirectory) throws Exception {
        stageOneWithItsProfile(root, workingDirectory, new DetectedFormats(jdbcTemplate));
    }

    /** Stage 1 over {@code root}, walked first, recording detected formats through {@code detectedFormats}. */
    private void stageOneWithItsProfile(Path root, Path workingDirectory, DetectedFormats detectedFormats) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
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

    /** {@link #SETTINGS} settings written {@code key=value}, each key and value naming {@code name}. */
    private static String settings(String name) {
        return keyed(name, SETTINGS, "=");
    }

    /** {@code lines} lines, each a key naming {@code name} and its number, then {@code separator}, then a value. */
    private static String keyed(String name, int lines, String separator) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            text.append(name).append(".chiave").append(line).append(separator).append("valore ").append(line).append('\n');
        }
        return text.toString();
    }

    /** {@code text} with each line indented by two spaces. */
    private static String indented(String text) {
        return text.lines().map(line -> "  " + line + "\n").reduce("", String::concat);
    }

    /** A JSON object of {@code keys} keys naming {@code name}, one to a line, between a line of each brace. */
    private static String json(String name, int keys) {
        return "{\n" + jsonBody(name, keys);
    }

    /** {@code keys} keys naming {@code name}, one to a line, then the closing brace. */
    private static String jsonBody(String name, int keys) {
        StringBuilder text = new StringBuilder();
        for (int key = 0; key < keys; key++) {
            text.append("  \"").append(name).append(key).append("\": ").append(key).append(key < keys - 1 ? ",\n" : "\n");
        }
        return text.append("}\n").toString();
    }

    /** A guide about configuration: seventeen lines of prose and three settings quoted among them, twenty lines. */
    private static String guide() {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < 17; line++) {
            text.append("Il server legge le impostazioni all'avvio e le applica in ordine, paragrafo ")
                    .append(line).append(".\n");
            if (line == 5 || line == 10 || line == 15) {
                text.append("port = 80").append(line).append('\n');
            }
        }
        return text.toString();
    }

    /** The configuration stage 1's run consumed, as stored. */
    private String configurationConsumed() {
        return jdbcTemplate.queryForObject(
                "SELECT config_consumed FROM run WHERE stage = 'byte-level-reduction'", String.class);
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
}
