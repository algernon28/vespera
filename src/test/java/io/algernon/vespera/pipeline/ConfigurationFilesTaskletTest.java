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
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** The name endings listed as code where a test needs a code set. */
    private static final String CODE_ENDINGS = ".java .c .py";

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

    /** The reason, by a first element in a namespace. */
    private static final String BY_ITS_ROOT_IN = "a configuration file, and configuration files outside every code set"
            + " are out of scope: its first element is %s, in the namespace %s";

    /** The reason, by a first element in no namespace. */
    private static final String BY_ITS_ROOT = "a configuration file, and configuration files outside every code set are"
            + " out of scope: its first element is %s, in no namespace";

    /** The reason a configuration file lying in a code set is kept with it as code. */
    private static final String IN_ITS_SET = "a configuration file of the code set at %s";

    /** The reason a build file of a set is kept as code, which the set pass writes before this one. */
    private static final String BUILD_REASON = "a build file of the code set at %s";

    private static final String MAVEN = "http://maven.apache.org/POM/4.0.0";

    private static final String SPRING_BEANS = "http://www.springframework.org/schema/beans";

    /** The page's section on the setting-line share. */
    private static final String SETTINGS_SECTION = "<h2>How much of each text file is settings</h2>";

    private static final String KEPT_AS_CODE_SECTION = "<h2>Kept as code</h2>";

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
    @DisplayName("With no floor set, no file is a configuration file, the run goes on to the end of its first stage, and the page shows what was measured")
    void withNoFloorSetNothingIsAConfigurationFileAndTheShareIsMeasured(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/Main.java", "class Main {}\n");
        write(root, "shop/app.properties", settings("shop"));
        write(root, "shop/logback.xml", logback("shop"));
        write(root, "impostazioni/server.properties", settings("server"));
        write(root, "impostazioni/logback.xml", logback("impostazioni"));
        write(root, "note/a.txt", "the same note, twice\n");
        write(root, "note/b.txt", "the same note, twice\n");
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        stageOne(root, workingDirectory);

        claim(
                "no file of settings and no file whose first element is a program's configuration carries a verdict,"
                        + " inside the code set or outside it, and none is a member of the set",
                () -> {
                    for (String path : List.of(
                            "shop/app.properties", "shop/logback.xml", "impostazioni/server.properties",
                            "impostazioni/logback.xml")) {
                        assertThat(verdictKindsFor(path)).as(path).isEmpty();
                        assertThat(membershipOf(path)).as(path).isEmpty();
                    }
                });
        claim(
                "stage 1 goes on to its hashing, so of two identical notes one is superseded: no floor stops it",
                () -> assertThat(List.of(verdictKindsFor("note/a.txt"), verdictKindsFor("note/b.txt")))
                        .containsExactlyInAnyOrder(List.of(), List.of(SUPERSEDED_BY)));
        String settings = section(formatMix(workingDirectory), SETTINGS_SECTION);
        claim(
                "the page says no floor is set, counts the two files of settings in the band from 90% to 100%, and"
                        + " counts the two whose first element is a program's configuration",
                () -> {
                    assertThat(settings).contains("No floor is set, so no file was found to be a configuration file.");
                    assertThat(countIn(settings, "<td>90% to 100%</td>")).isEqualTo(2);
                    assertThat(countIn(settings, "whatever its share once the floor is set")).isEqualTo(2);
                });
        claim(
                "stage 1 records the floor as null, unset, among what it consumed",
                () -> assertThat(configurationConsumed()).contains("\"configurationSettingShareFloor\":null"));
        claim(
                "and the profile's floor now points at the page it is read off",
                () -> assertThat(ConfigurationSettingShareFloorInProfile.measurementSource(new ProfileStore(workingDirectory)))
                        .isEqualTo(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME).toString()));
    }

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
    @DisplayName("A setting continued over several lines with a backslash is read as one setting, and a line ending with two backslashes is not continued")
    void aContinuedLineIsReadAsOneSetting(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        StringBuilder continued = new StringBuilder();
        StringBuilder escaped = new StringBuilder();
        for (int entry = 0; entry < SETTINGS; entry++) {
            continued.append("elenco.").append(entry).append("=primo,\\\n    secondo,\\\n    terzo,\\\n    quarto\n");
            escaped.append("percorso.").append(entry).append("=C:\\\\dati\\\\\n");
        }
        write(root, "impostazioni/lunghe.properties", continued.toString());
        write(root, "impostazioni/barre.properties", escaped.toString());

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "each setting and the three lines continuing it are one counted line, so the file is wholly settings"
                        + " and left out; read line by line it would be a quarter settings and kept",
                () -> assertThat(verdictReasonsFor("impostazioni/lunghe.properties"))
                        .containsExactly(BY_ITS_SHARE.formatted(100)));
        claim(
                "a line ending with two backslashes ends with an escaped backslash and continues nothing, so each of"
                        + " its " + SETTINGS + " lines is a setting of its own and the file is left out; joined in"
                        + " pairs it would be six lines, too few for a share",
                () -> assertThat(verdictReasonsFor("impostazioni/barre.properties"))
                        .containsExactly(BY_ITS_SHARE.formatted(100)));
    }

    @Test
    @Story("A configuration file is told from its content")
    @DisplayName("A file whose first element is that of a program's configuration is one, whatever its name and its share; another first element is not")
    void aFileWhoseFirstElementIsAProgramsConfigurationIsOneWhateverItsShare(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "xml/logback.xml", logback("xml"));
        write(root, "xml/log4j2.xml", "<?xml version=\"1.0\"?>\n<Configuration status=\"warn\">\n"
                + "  <Appenders><Console name=\"c\"/></Appenders>\n</Configuration>\n");
        write(root, "xml/pom.xml", "<project xmlns=\"" + MAVEN + "\">\n  <artifactId>xml</artifactId>\n</project>\n");
        write(root, "xml/beans.xml", "<b:beans xmlns:b=\"" + SPRING_BEANS + "\">\n  <b:bean id=\"x\"/>\n</b:beans>\n");
        write(root, "xml/registrazione.txt", logback("registrazione"));
        write(root, "xml/utf16.xml", utf16WithItsMark(logback("utf16").replace("UTF-8", "UTF-16")));
        write(root, "xml/progetto.xml", "<project>\n  <name>progetto</name>\n</project>\n");
        write(root, "xml/note.xml", "<note>\n  <body>a note in XML</body>\n</note>\n");
        write(root, "xml/maiuscole.xml", "<CONFIGURATION>\n  <appender name=\"a\"/>\n</CONFIGURATION>\n");

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "logback's, log4j 2's, a Maven project's in its namespace and Spring's, under a prefix, are each left out"
                        + " with their first element in the reason, though none of their lines is a setting",
                () -> {
                    assertThat(verdictReasonsFor("xml/logback.xml")).containsExactly(BY_ITS_ROOT.formatted("configuration"));
                    assertThat(verdictReasonsFor("xml/log4j2.xml")).containsExactly(BY_ITS_ROOT.formatted("Configuration"));
                    assertThat(verdictReasonsFor("xml/pom.xml")).containsExactly(BY_ITS_ROOT_IN.formatted("project", MAVEN));
                    assertThat(verdictReasonsFor("xml/beans.xml"))
                            .containsExactly(BY_ITS_ROOT_IN.formatted("beans", SPRING_BEANS));
                });
        claim(
                "a file named as text holding logback's configuration is one all the same: the name is not read",
                () -> assertThat(verdictReasonsFor("xml/registrazione.txt")).containsExactly(BY_ITS_ROOT.formatted("configuration")));
        claim(
                "logback's configuration in UTF-16, behind its byte-order mark, is read for its first element as any"
                        + " other is, and left out by it",
                () -> assertThat(verdictReasonsFor("xml/utf16.xml")).containsExactly(BY_ITS_ROOT.formatted("configuration")));
        claim(
                "a project in no namespace, another first element, and configuration spelt in capitals are kept",
                () -> {
                    for (String path : List.of("xml/progetto.xml", "xml/note.xml", "xml/maiuscole.xml")) {
                        assertThat(verdictKindsFor(path)).as(path).isEmpty();
                    }
                });
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

    @Test
    @Story("An interface definition is kept as a document")
    @DisplayName("An XML Schema, both WSDLs, OpenAPI, Swagger and a JSON Schema are kept whatever their share, and a key named openapi below the root is no mark")
    void anInterfaceDefinitionIsKeptWhateverItsShare(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "interfacce/tipi.xsd", xmlOfTenLines(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">", "  <xs:element name=\"e%d\"/>", "</xs:schema>"));
        write(root, "interfacce/servizio.wsdl", xmlOfTenLines(
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" name=\"S\">", "  <message name=\"m%d\"/>",
                "</definitions>"));
        write(root, "interfacce/servizio2.wsdl", xmlOfTenLines(
                "<description xmlns=\"http://www.w3.org/ns/wsdl\">", "  <interface name=\"i%d\"/>", "</description>"));
        write(root, "interfacce/openapi.yaml", "info:\n  title: Pagamenti\n  version: 1.0.0\nopenapi: 3.1.0\n"
                + "paths:\n" + indented(keyed("percorso", SETTINGS, ": ")));
        write(root, "interfacce/swagger.json", "{\n  \"swagger\": \"2.0\",\n" + jsonBody("swagger", SETTINGS));
        write(root, "interfacce/schema.json", "{\n  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n"
                + jsonBody("schema", SETTINGS));
        write(root, "interfacce/non-radice.yaml", "x:\n  openapi: 3.1.0\n" + indented(keyed("sotto", SETTINGS, ": ")));

        stageOne(root, workingDirectory, "0");

        claim(
                "at a floor of nought, where every other file of ten counted lines is a configuration file, the six"
                        + " interface definitions carry no verdict, an OpenAPI description whose first root key is"
                        + " another among them",
                () -> {
                    for (String path : List.of(
                            "interfacce/tipi.xsd", "interfacce/servizio.wsdl", "interfacce/servizio2.wsdl",
                            "interfacce/openapi.yaml", "interfacce/swagger.json", "interfacce/schema.json")) {
                        assertThat(verdictKindsFor(path)).as(path).isEmpty();
                    }
                });
        claim(
                "a YAML file whose key openapi lies below its root is not one, and is left out by its share",
                () -> assertThat(verdictReasonsFor("interfacce/non-radice.yaml")).containsExactly(BY_ITS_SHARE.formatted(100)));
        claim(
                "the page counts the six interface definitions",
                () -> assertThat(countIn(
                                section(formatMix(workingDirectory), SETTINGS_SECTION),
                                "Interface definitions, kept as documents whatever their share"))
                        .isEqualTo(6));
    }

    @Test
    @Story("A configuration file inside a code set is kept with it")
    @DisplayName("A configuration file lying in a code set is kept with it as code, a member of the set, and the set's build file and its documents are as they were")
    void insideACodeSetAConfigurationFileIsKeptWithItAsCode(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/pom.xml", "<project xmlns=\"" + MAVEN + "\">\n  <artifactId>shop</artifactId>\n</project>\n");
        write(root, "shop/src/Main.java", "class Main {}\n");
        write(root, "shop/src/main/resources/application.properties", settings("shop"));
        write(root, "shop/conf/logback.xml", logback("shop"));
        write(root, "shop/docs/guida.txt", guide());
        write(root, "Avvio.java", "class Avvio {}\n");
        write(root, "application.yaml", keyed("radice", SETTINGS, ": "));
        // Byte for byte the same, so the hashing, which reads only files sharing a size, would read both if either
        // reached it: one lies in the set, the other in a folder of no code.
        write(root, "shop/conf/copia.properties", settings("copia"));
        write(root, "impostazioni/copia.properties", settings("copia"));
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        List<String> logged = logging(() -> stageOne(root, workingDirectory, FLOOR));

        claim(
                "the file of settings and logback's configuration in the set are code, with the reason naming the set,"
                        + " and members of it as configuration",
                () -> {
                    for (String path : List.of("shop/src/main/resources/application.properties", "shop/conf/logback.xml")) {
                        assertThat(verdictKindsFor(path)).as(path).containsExactly(CODE);
                        assertThat(verdictReasonsFor(path)).as(path).containsExactly(IN_ITS_SET.formatted("shop"));
                        assertThat(membershipOf(path)).as(path).containsExactly("configuration in 'shop'");
                    }
                });
        claim(
                "a file of settings lying directly at the root, beside code, is the root's set's, named as the corpus root",
                () -> {
                    assertThat(verdictReasonsFor("application.yaml")).containsExactly(IN_ITS_SET.formatted("the corpus root"));
                    assertThat(membershipOf("application.yaml")).containsExactly("configuration in ''");
                });
        claim(
                "two identical configuration files, one kept with the set as code and one left out as lying in no set,"
                        + " carry those verdicts and have no content hash: the third pass took both before the hashing,"
                        + " which would otherwise have read both, their size being shared",
                () -> {
                    assertThat(verdictKindsFor("shop/conf/copia.properties")).containsExactly(CODE);
                    assertThat(verdictKindsFor("impostazioni/copia.properties")).containsExactly(OUT_OF_SCOPE);
                    assertThat(contentHashRowsFor("shop/conf/copia.properties")).isZero();
                    assertThat(contentHashRowsFor("impostazioni/copia.properties")).isZero();
                });
        claim(
                "the set's build file, whose first element is Maven's, stays the build file the set pass kept",
                () -> {
                    assertThat(verdictReasonsFor("shop/pom.xml")).containsExactly(BUILD_REASON.formatted("shop"));
                    assertThat(membershipOf("shop/pom.xml")).containsExactly("build in 'shop'");
                });
        claim(
                "and a guide in the set stays a document",
                () -> {
                    assertThat(verdictKindsFor("shop/docs/guida.txt")).isEmpty();
                    assertThat(membershipOf("shop/docs/guida.txt")).isEmpty();
                });
        claim(
                "stage 1 logs the file of settings in the set once, in the whole of the line it logs for what it keeps"
                        + " with a code set",
                () -> assertThat(logged).containsOnlyOnce("[byte-level-reduction] kept "
                        + occurrenceIdOf("shop/src/main/resources/application.properties")
                        + " with its code set -> code: " + IN_ITS_SET.formatted("shop")));
    }

    @Test
    @Story("A configuration file inside a code set is kept with it")
    @DisplayName("A folder holding configuration files and no code is no code set, and its configuration files are left out")
    void aConfigurationFileNeverMakesASet(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/Main.java", "class Main {}\n");
        write(root, "solo/app.properties", settings("solo"));
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "the one code set is the folder of code; the folder of settings is none",
                () -> assertThat(sets()).isEqualTo(Map.of("shop", "shop")));
        claim(
                "so its file of settings lies in no set, and is left out",
                () -> assertThat(verdictReasonsFor("solo/app.properties")).containsExactly(BY_ITS_SHARE.formatted(100)));
    }

    @Test
    @Story("A build file outside every code set is judged by its content")
    @DisplayName("A build file lying in no code set is left out where its content is configuration, and kept where it is not")
    void aBuildFileOutsideEveryCodeSetIsJudgedByItsContent(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/pom.xml", "<project xmlns=\"" + MAVEN + "\">\n  <modules><module>core</module>"
                + "<module>api</module></modules>\n</project>\n");
        write(root, "shop/core/pom.xml", "<project xmlns=\"" + MAVEN + "\">\n  <artifactId>core</artifactId>\n</project>\n");
        write(root, "shop/core/Core.java", "class Core {}\n");
        write(root, "shop/api/pom.xml", "<project xmlns=\"" + MAVEN + "\">\n  <artifactId>api</artifactId>\n</project>\n");
        write(root, "shop/api/Api.java", "class Api {}\n");
        write(root, "strumenti/package.json", json("strumenti", SETTINGS - 2));
        StringBuilder cmake = new StringBuilder("cmake_minimum_required(VERSION 3.20)\nproject(motore C)\n");
        for (int line = 0; line < SETTINGS; line++) {
            cmake.append("add_executable(passo").append(line).append(" passo").append(line).append(".c)\n");
        }
        write(root, "motore/CMakeLists.txt", cmake.toString());
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "the build file above the modules, in no set, is left out by its first element, Maven's project",
                () -> assertThat(verdictReasonsFor("shop/pom.xml")).containsExactly(BY_ITS_ROOT_IN.formatted("project", MAVEN)));
        claim(
                "each module's build file stays its set's",
                () -> {
                    assertThat(membershipOf("shop/core/pom.xml")).containsExactly("build in 'shop/core'");
                    assertThat(membershipOf("shop/api/pom.xml")).containsExactly("build in 'shop/api'");
                });
        claim(
                "a package.json in a folder of no code is left out by its share, ten keys of twelve counted lines",
                () -> assertThat(verdictReasonsFor("strumenti/package.json")).containsExactly(BY_ITS_SHARE.formatted(83)));
        claim(
                "and a CMakeLists.txt of calls, in a folder of no code, is no configuration file and carries no verdict:"
                        + " its name marks a folder and decides nothing about it",
                () -> assertThat(verdictKindsFor("motore/CMakeLists.txt")).isEmpty());
    }

    @Test
    @Story("A configuration file inside a code set is kept with it")
    @DisplayName("A log and a file left out for its name inside a code set keep their verdicts, however much of them reads as settings")
    void aFileAnEarlierPassDecidedIsNotReadAgain(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile()
                .logTimestampShareFloor("0.9", SET_BY_THIS_TEST)
                .outOfScopeNameEndings(".cfg", SET_BY_THIS_TEST)
                .build());
        CodeNameEndingsInProfile.write(profileStore, CODE_ENDINGS, SET_BY_THIS_TEST);
        write(root, "srv/Main.java", "class Main {}\n");
        StringBuilder logfmt = new StringBuilder();
        for (int line = 0; line < SETTINGS; line++) {
            logfmt.append(String.format(Locale.ROOT, "ts=2023-02-08T12:00:%02d level=INFO msg=served\n", line));
        }
        write(root, "srv/logs/server.log", logfmt.toString());
        write(root, "srv/vecchio.cfg", settings("vecchio"));

        stageOne(root, workingDirectory, FLOOR);

        claim(
                "a log whose every line is also a setting keeps the one verdict the first pass gave it, and is no member",
                () -> {
                    assertThat(verdictReasonsFor("srv/logs/server.log")).containsExactly(
                            "a log, and logs are out of scope: 100% of the lines read from its start and end begin with a"
                                    + " timestamp");
                    assertThat(membershipOf("srv/logs/server.log")).isEmpty();
                });
        claim(
                "and a file of settings left out for the ending of its name keeps that one verdict, and is no member",
                () -> {
                    assertThat(verdictKindsFor("srv/vecchio.cfg")).containsExactly(OUT_OF_SCOPE);
                    assertThat(verdictReasonsFor("srv/vecchio.cfg").getFirst()).startsWith("a file whose name ends with .cfg");
                    assertThat(membershipOf("srv/vecchio.cfg")).isEmpty();
                });
    }

    @Test
    @Story("A configuration file is told from its content")
    @DisplayName("A file the configuration pass cannot read is left as it was, said once in the log, and the pass carries on")
    void aFileThePassCannotReadIsLeftAsItWas(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "impostazioni/sparito.properties", settings("sparito"));
        write(root, "impostazioni/presente.properties", settings("presente"));
        Path gone = root.resolve("impostazioni/sparito.properties");
        // Removed once its kind is recorded: after detection has read it, before the configuration pass does.
        DetectedFormats removing = new DetectedFormats(jdbcTemplate) {
            @Override
            public void record(OccurrenceId occurrenceId, RunId runId, DetectedFormat format, DetectedSubtype subtype) {
                super.record(occurrenceId, runId, format, subtype);
                if (pathOf(occurrenceId).equals("impostazioni/sparito.properties")) {
                    deleteQuietly(gone);
                }
            }
        };
        ConfigurationSettingShareFloorInProfile.write(new ProfileStore(workingDirectory), FLOOR, SET_BY_THIS_TEST);

        List<String> logged = logging(() -> stageOneWithItsProfile(root, workingDirectory, removing));

        claim(
                "the file gone carries no verdict",
                () -> assertThat(verdictKindsFor("impostazioni/sparito.properties")).isEmpty());
        claim(
                "it is logged once, in the whole of the line that says it is left as it was",
                () -> {
                    String said = "[byte-level-reduction] could not read " + occurrenceIdOf("impostazioni/sparito.properties")
                            + " to see whether it is a configuration file, so it is left as it was: ";
                    assertThat(logged.stream().filter(line -> line.startsWith(said) && line.length() > said.length()))
                            .hasSize(1);
                });
        claim(
                "and the pass carries on: the file beside it is left out",
                () -> assertThat(verdictReasonsFor("impostazioni/presente.properties")).containsExactly(BY_ITS_SHARE.formatted(100)));
    }

    @Test
    @Story("What the format-mix page shows of configuration files")
    @DisplayName("The page counts the configuration files kept with a code set and left out, the share of each file read, the first elements found and the interface definitions kept")
    void theFormatMixPageCountsWhatThePassFound(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = namedRoot(temp);
        write(root, "shop/Main.java", "class Main {}\n");
        write(root, "shop/app.properties", settings("shop"));
        write(root, "impostazioni/a.properties", settings("a"));
        write(root, "impostazioni/logback.xml", logback("impostazioni"));
        write(root, "interfacce/tipi.xsd", xmlOfTenLines(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">", "  <xs:element name=\"e%d\"/>", "</xs:schema>"));
        write(root, "note/guida.txt", guide());
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), CODE_ENDINGS, SET_BY_THIS_TEST);

        stageOne(root, workingDirectory, FLOOR);

        String html = formatMix(workingDirectory);
        String settings = section(html, SETTINGS_SECTION);
        claim(
                "the section says what the floor is and counts three configuration files: one kept with its code set, and"
                        + " two left out, one by its share and one by its first element",
                () -> {
                    assertThat(settings).contains("The floor is 80%");
                    assertThat(countIn(settings, "Configuration files")).isEqualTo(3);
                    assertThat(countIn(settings, "kept with their code set")).isEqualTo(1);
                    assertThat(countIn(settings, "and left out")).isEqualTo(2);
                });
        claim(
                "its table of shares counts the two files of settings from 90% to 100% and the guide, three settings in"
                        + " twenty lines, from 10% to 19%; logback's file and the schema are not in it",
                () -> {
                    assertThat(countIn(settings, "<td>90% to 100%</td>")).isEqualTo(2);
                    assertThat(countIn(settings, "<td>10% to 19%</td>")).isEqualTo(1);
                });
        claim(
                "it counts the one first element found, by its name and its namespace, and the one interface definition"
                        + " kept, by its kind",
                () -> {
                    assertThat(countIn(settings, "whatever its share once the floor is set")).isEqualTo(1);
                    assertThat(countIn(settings, "<td>configuration, in no namespace</td>")).isEqualTo(1);
                    assertThat(countIn(settings, "Interface definitions, kept as documents whatever their share"))
                            .isEqualTo(1);
                    assertThat(countIn(settings, "<td>XML Schema</td>")).isEqualTo(1);
                });
        claim(
                "the files left out as out of scope count the two, and say they were configuration files",
                () -> {
                    assertThat(countIn(html, "Files left out as out of scope, and not read any further")).isEqualTo(2);
                    assertThat(countIn(html, "and configuration files")).isEqualTo(2);
                });
        claim(
                "the code sets' members count the one configuration file, and so does the table of what was kept as code",
                () -> {
                    assertThat(countIn(paragraphFrom(html, "Code sets:"), "configuration files")).isEqualTo(1);
                    assertThat(countIn(section(html, KEPT_AS_CODE_SECTION), "<td>configuration file</td>")).isEqualTo(1);
                });
    }

    @Test
    @Story("A configuration file is told from its content")
    @DisplayName("A floor that is not a number finds no configuration file and is recorded as unset")
    void anUnreadableFloorFindsNothingAndIsRecordedAsNull(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = namedRoot(temp);
        write(root, "impostazioni/a.properties", settings("a"));
        write(root, "impostazioni/logback.xml", logback("impostazioni"));

        stageOne(root, workingDirectory, "0,8");

        claim(
                "neither the file of settings nor logback's configuration carries a verdict",
                () -> {
                    assertThat(verdictKindsFor("impostazioni/a.properties")).isEmpty();
                    assertThat(verdictKindsFor("impostazioni/logback.xml")).isEmpty();
                });
        claim(
                "and stage 1 records the floor as null, as it records an unset one",
                () -> assertThat(configurationConsumed()).contains("\"configurationSettingShareFloor\":null"));
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

    /** logback's configuration, an appender naming {@code name}. */
    private static String logback(String name) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration>\n  <appender name=\"" + name
                + "\" class=\"ch.qos.logback.core.ConsoleAppender\"/>\n</configuration>\n";
    }

    /** An XML document of ten lines or more: {@code open}, ten numbered lines of {@code line}, and {@code close}. */
    private static String xmlOfTenLines(String open, String line, String close) {
        StringBuilder text = new StringBuilder("<?xml version=\"1.0\"?>\n").append(open).append('\n');
        for (int n = 0; n < SETTINGS; n++) {
            text.append(String.format(Locale.ROOT, line, n)).append('\n');
        }
        return text.append(close).append('\n').toString();
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

    /** What the occurrence at {@code path} is a member of, as {@code <role> in '<folder>'}. */
    private List<String> membershipOf(String path) {
        return jdbcTemplate.query(
                "SELECT m.role, m.folder FROM code_set_member m JOIN file_occurrence o ON o.id = m.occurrence_id"
                        + " WHERE o.path = ?",
                (row, n) -> row.getString("role") + " in '" + row.getString("folder") + "'",
                path);
    }

    /** The configuration stage 1's run consumed, as stored. */
    private String configurationConsumed() {
        return jdbcTemplate.queryForObject(
                "SELECT config_consumed FROM run WHERE stage = 'byte-level-reduction'", String.class);
    }

    private long occurrenceIdOf(String path) {
        return jdbcTemplate.queryForObject("SELECT id FROM file_occurrence WHERE path = ?", Long.class, path);
    }

    private String pathOf(OccurrenceId occurrence) {
        return jdbcTemplate.queryForObject(
                "SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence.value());
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

    /** {@code text} in UTF-16, little-endian, behind its byte-order mark. */
    private static byte[] utf16WithItsMark(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);
        return bytes;
    }

    private static String formatMix(Path workingDirectory) throws IOException {
        return Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    /** The page from {@code heading} to the next heading of the same level, or nothing where it has no such heading. */
    private static String section(String html, String heading) {
        int from = html.indexOf(heading);
        if (from < 0) {
            return "";
        }
        int to = html.indexOf("<h2>", from + heading.length());
        return to < 0 ? html.substring(from) : html.substring(from, to);
    }

    /** The paragraph of the page that holds {@code opening}, from it to the paragraph's end, or nothing. */
    private static String paragraphFrom(String html, String opening) {
        int from = html.indexOf(opening);
        if (from < 0) {
            return "";
        }
        int to = html.indexOf("</p>", from);
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
