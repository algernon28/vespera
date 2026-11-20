package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The release version's source is the tag, and the build keeps it apart from the stages' implementation versions
 * (ADR-430 §2, §3): the pom's version is Maven's {@code ${revision}}, whose default, {@code 0-SNAPSHOT}, never changes,
 * a release build being given the tag's; and {@code implementation-versions.properties}, which a run id is hashed from
 * (ADR-058), names each module by a commit and holds nothing else, so no release version can enter it.
 *
 * <p>The pom is read as written, not as Maven resolves it, so the claim holds whatever {@code -Drevision} a build was
 * given; the implementation versions are read as this build wrote them.
 */
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-430", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class TheReleaseVersionIsTheTagsTest {

    private static final Path POM = Path.of("pom.xml");

    /** What the pom's version names: the property Maven reads a release's version into from the command line. */
    private static final String THE_REVISION = "${revision}";

    /** The version of every build no tag made, fixed for good. */
    private static final String THE_DEFAULT = "0-SNAPSHOT";

    /** The resource the build writes the stages' implementation versions into, as the jar reads it. */
    private static final String IMPLEMENTATION_VERSIONS = "/implementation-versions.properties";

    /** The two folders whose sub-folders are the modules (ADR-367). */
    private static final List<Path> MODULE_FOLDERS = List.of(
            Path.of("src", "main", "java", "io", "algernon", "vespera", "engine"),
            Path.of("src", "main", "java", "io", "algernon", "vespera", "publish"));

    /** A commit, as git names it in full, or the word the build writes where git could not answer. */
    private static final Pattern A_COMMIT_OR_UNKNOWN = Pattern.compile("[0-9a-f]{40}|unknown");

    @Test
    @Story("The version is the tag's, and every other build is 0-SNAPSHOT")
    @DisplayName("The pom's version is the revision given on the command line, and 0-SNAPSHOT where none is")
    void thePomsVersionIsTheRevisionWhoseDefaultIsZeroSnapshot() throws Exception {
        Element project = pom().getDocumentElement();

        claim("the pom's version is ${revision}, which a release build sets from the tag, so no release edits the pom",
                () -> assertThat(childText(project, "version")).isEqualTo(THE_REVISION));
        claim("the pom's properties set revision to 0-SNAPSHOT, the version of every build no tag made",
                () -> assertThat(childText(child(project, "properties"), "revision")).isEqualTo(THE_DEFAULT));
    }

    @Test
    @Story("Vespera's version never decides which work is done again")
    @DisplayName("The versions a piece of work is identified by name each module by a commit, and nothing else")
    void theImplementationVersionsNameEachModuleByACommitAndNothingElse() throws IOException {
        Map<String, String> written = implementationVersions();
        TreeSet<String> modules = moduleFolders();

        claim("the source tree has modules to compare against",
                () -> assertThat(modules).isNotEmpty());
        claim("the file the build writes names each module folder under engine and publish, and no other key",
                () -> assertThat(written.keySet()).containsExactlyElementsOf(modules));
        claim("each module's value is the full name of a commit, or unknown where git could not answer, so no"
                        + " version of Vespera is among them",
                () -> assertThat(written.values()).allMatch(value -> A_COMMIT_OR_UNKNOWN.matcher(value).matches()));
    }

    private static Document pom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(POM.toFile());
    }

    /** The one direct child of {@code parent} named {@code name}, or {@code null}. */
    private static Element child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) {
                return element;
            }
        }
        return null;
    }

    /** The trimmed text of {@code parent}'s direct child {@code name}, or {@code null} where it has none. */
    private static String childText(Element parent, String name) {
        Element child = parent == null ? null : child(parent, name);
        return child == null ? null : child.getTextContent().strip();
    }

    private static Map<String, String> implementationVersions() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = TheReleaseVersionIsTheTagsTest.class.getResourceAsStream(IMPLEMENTATION_VERSIONS)) {
            if (stream == null) {
                throw new IllegalStateException("the build wrote no " + IMPLEMENTATION_VERSIONS);
            }
            properties.load(stream);
        }
        Map<String, String> written = new TreeMap<>();
        properties.stringPropertyNames().forEach(key -> written.put(key, properties.getProperty(key)));
        return written;
    }

    private static TreeSet<String> moduleFolders() throws IOException {
        TreeSet<String> modules = new TreeSet<>();
        for (Path folder : MODULE_FOLDERS) {
            try (Stream<Path> children = Files.list(folder)) {
                children.filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).forEach(modules::add);
            }
        }
        return modules;
    }
}
