package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pin ADR-128 puts on okio, kept where the decision put it.
 *
 * <p>Qodana flagged {@code com.squareup.okio:okio:2.10.0} under Lingua for CVE-2023-3635, and
 * Maven's nearest-wins was selecting that version over the okio okhttp declares. The record pins
 * okio forward in {@code dependencyManagement}; this test is what notices if the pin is removed or
 * lowered, because nothing else in the build would — the suite compiles and passes against the
 * vulnerable version too.
 *
 * <p>It reads {@code pom.xml} rather than reflection on purpose: the resolved version of a
 * transitive dependency is a property of the build file, and the artifact carries no reliable
 * version string on the classpath. It does not demand the exact pinned number; a later pin at or
 * above the fix is the same decision, and a test that forbade a bump would pin a number rather
 * than defend a decision.
 *
 * <p>Report text follows ADR-052: no ADR id and no phrase that needs {@code CONTEXT.md}.
 */
@Epic("Architecture")
@Feature("Dependency policy")
@Issue("221")
@Link(name = "ADR-128", url = Adr.OKIO_IS_PINNED_AND_A_DEPENDENCY_SCAN_BELONGS_IN_CI, type = "adr")
class DependencyPolicyTest {

    /** The build file the managed version lives in and nowhere else. */
    private static final Path POM = Path.of("pom.xml");

    /**
     * CVE-2023-3635 is fixed in okio 3.4.0, and no 2.x release carries the fix — 2.10.0 is the last
     * of that line. A managed version below this puts the vulnerable gzip reader back on the
     * classpath.
     */
    private static final String FIXED_IN = "3.4.0";

    /** The managed declaration, read only from between the dependencyManagement tags. */
    private static final Pattern MANAGED_VERSION =
            Pattern.compile("<artifactId>okio</artifactId>\\s*<version>([^<]+)</version>");

    /** A version written as a property reference, which the pin uses. */
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{(\\S+)}");

    @Test
    @Story("The vulnerable transitive dependency cannot return")
    @DisplayName("okio is managed at or above the release the gzip denial of service was fixed in")
    void okioIsManagedAtOrAboveTheFix() {
        Optional<String> managed = managedOkioVersion();

        claim(
                "the pom still manages com.squareup.okio:okio, so Maven cannot resolve its version"
                        + " by proximity alone — removing the pin returns the graph to whichever"
                        + " consumer is nearest, which was Lingua's 2.10.0 and not okhttp's 3.6.0,"
                        + " and 2.10.0 is the version that carries the vulnerable gzip reader",
                () -> assertThat(managed).isPresent());

        claim(
                "and the version it manages is at least " + FIXED_IN + ", the release CVE-2023-3635"
                        + " was fixed in, so the gzip reader on the classpath is the repaired one; a"
                        + " version below it is the vulnerability this decision exists to keep out",
                () -> assertThat(compare(managed.orElseThrow(), FIXED_IN)).isGreaterThanOrEqualTo(0));
    }

    /**
     * The okio version the pom's dependencyManagement states, with a property reference resolved
     * against the same file.
     */
    private static Optional<String> managedOkioVersion() {
        String pom = read(POM);
        int start = pom.indexOf("<dependencyManagement>");
        int end = pom.indexOf("</dependencyManagement>", start);
        if (start < 0 || end < 0) {
            return Optional.empty();
        }
        Matcher declared = MANAGED_VERSION.matcher(pom.substring(start, end));
        if (!declared.find()) {
            return Optional.empty();
        }
        String value = declared.group(1).strip();
        Matcher reference = PROPERTY_REFERENCE.matcher(value);
        if (reference.matches()) {
            Matcher property = Pattern.compile(
                            "<" + Pattern.quote(reference.group(1)) + ">([^<]+)</"
                                    + Pattern.quote(reference.group(1)) + ">")
                    .matcher(pom);
            return property.find() ? Optional.of(property.group(1).strip()) : Optional.empty();
        }
        return Optional.of(value);
    }

    /** Semantic-order comparison over the dot-separated numbers a version is written with. */
    private static int compare(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int part = 0; part < Math.max(leftParts.length, rightParts.length); part++) {
            int leftNumber = part < leftParts.length ? Integer.parseInt(leftParts[part]) : 0;
            int rightNumber = part < rightParts.length ? Integer.parseInt(rightParts[part]) : 0;
            if (leftNumber != rightNumber) {
                return Integer.compare(leftNumber, rightNumber);
            }
        }
        return 0;
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException couldNotRead) {
            throw new UncheckedIOException("could not read " + source, couldNotRead);
        }
    }
}