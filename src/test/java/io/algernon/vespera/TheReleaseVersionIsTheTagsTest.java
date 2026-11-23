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
 * The release version is git's, and the build keeps it apart from the stages' implementation versions (ADR-444 §5,
 * ADR-431 §3): a Maven core extension names every build from git, a commit carrying a {@code vMAJOR.MINOR.PATCH} tag
 * building those numbers, and the pom's own {@code 0-SNAPSHOT} is read only with the extension off; and
 * {@code implementation-versions.properties}, which a run id is hashed from (ADR-058), names each module by a commit
 * and holds nothing else, so no release version can enter it.
 *
 * <p>The pom and the extension's files are read as written, not as Maven resolves them; the implementation versions
 * are read as this build wrote them.
 */
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-444", url = Adr.THE_RELEASE_VERSION_IS_COMPUTED_FROM_GIT_AND_THE_RECORDS, type = "adr")
@Link(name = "ADR-431", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class TheReleaseVersionIsTheTagsTest {

    private static final Path POM = Path.of("pom.xml");

    /** The core extensions Maven loads before it reads the pom. */
    private static final Path EXTENSIONS = Path.of(".mvn", "extensions.xml");

    /** The git-versioning extension's configuration. */
    private static final Path VERSIONING = Path.of(".mvn", "maven-git-versioning-extension.xml");

    /** The extension that names every build from git. */
    private static final String THE_EXTENSION = "maven-git-versioning-extension";

    /** A release tag: v and three numbers, the numbers captured as the version. */
    private static final String A_RELEASE_TAG = "v(?<version>\\d+\\.\\d+\\.\\d+)";

    /** What a release tag builds: its numbers alone. */
    private static final String THE_TAGS_NUMBERS = "${ref.version}";

    /** The pom's own version, which only a build with the extension turned off reads. */
    private static final String THE_POMS_OWN = "0-SNAPSHOT";

    /** The resource the build writes the stages' implementation versions into, as the jar reads it. */
    private static final String IMPLEMENTATION_VERSIONS = "/implementation-versions.properties";

    /** The two folders whose sub-folders are the modules (ADR-367). */
    private static final List<Path> MODULE_FOLDERS = List.of(
            Path.of("src", "main", "java", "io", "algernon", "vespera", "engine"),
            Path.of("src", "main", "java", "io", "algernon", "vespera", "publish"));

    /** A commit, as git names it in full, or the word the build writes where git could not answer. */
    private static final Pattern A_COMMIT_OR_UNKNOWN = Pattern.compile("[0-9a-f]{40}|unknown");

    @Test
    @Story("The version is git's, and no one types it")
    @DisplayName("Every build is named by git through the versioning extension, a release tag building its own numbers")
    void theVersionIsGitsThroughTheExtension() throws Exception {
        Element project = xml(POM).getDocumentElement();
        Element extensions = xml(EXTENSIONS).getDocumentElement();
        Element configuration = xml(VERSIONING).getDocumentElement();
        Element tagRef = child(child(configuration, "refs"), "ref");

        claim("the build loads the git-versioning extension, which writes the version into every build",
                () -> assertThat(childText(child(extensions, "extension"), "artifactId")).isEqualTo(THE_EXTENSION));
        claim("a commit carrying a tag of v and three numbers builds those numbers, so a release is its tag's",
                () -> assertThat(List.of(tagRef.getAttribute("type"), childText(tagRef, "pattern"),
                                childText(tagRef, "version")))
                        .containsExactly("tag", A_RELEASE_TAG, THE_TAGS_NUMBERS));
        claim("the pom's version is 0-SNAPSHOT, read only where the extension is turned off, and no property moves it",
                () -> assertThat(List.of(String.valueOf(childText(project, "version")),
                                String.valueOf(childText(child(project, "properties"), "revision"))))
                        .containsExactly(THE_POMS_OWN, "null"));
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

    private static Document xml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file.toFile());
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
