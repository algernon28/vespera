package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-076 as a test: every type the application can throw is named so that a reader can tell it is
 * throwable without looking it up.
 *
 * <p>Half of ADR-076 is mechanisable and half is not. That a name ends in {@code Exception} is
 * checked here; that it names the fault rather than the mechanism that noticed it — the difference
 * between {@code ExtractorStoppedAnsweringException} and the {@code ServiceScopeCircuitBreakerTripped}
 * it replaced — and that its message states the event rather than the rule, are matters for review by
 * eye. Recording that split is the point of this javadoc: a reader who finds only this test should not
 * conclude the suffix was the whole decision.
 *
 * <p>Compiled classes, not source, and only the application's own output directory. Reading bytecode
 * catches a throwable that arrives without the word {@code extends} visible anywhere — a subclass of a
 * subclass — and keeps test scaffolding, which may reasonably throw a locally-named control-flow
 * signal, out of scope.
 */
@Epic("Architecture")
@Feature("Naming conventions")
@Link(name = "ADR-076", url = Adr.EXCEPTION_TYPES_ARE_NAMED_FOR_THE_FAULT, type = "adr")
class ExceptionNamingTest {

    /** The suffix ADR-076 requires, spelled once so the claim below cannot disagree with the check. */
    private static final String REQUIRED_SUFFIX = "Exception";

    private static final String CLASS_FILE = ".class";

    /**
     * Compiled artefacts that carry no class of their own to load: a {@code package-info} declaration
     * exists as a class file but its name is not a legal type name.
     */
    private static final String PACKAGE_INFO = ".package-info";

    /**
     * One throwable the scan must find. Named rather than counted, so the guard does not have to be
     * edited every time a type is added: a scan that has quietly stopped finding anything — a changed
     * output layout, a packaging change — would pass the emptiness claim below while proving nothing,
     * and this is what fails instead.
     */
    private static final String A_THROWABLE_THE_APPLICATION_DEFINES =
            "io.algernon.vespera.pipeline.ExtractorStoppedAnsweringException";

    @Test
    @Story("A throwable type is recognisable as one from its name")
    @DisplayName("Every type the application can throw is named ...Exception")
    void everyThrowableTypeIsNamedException() throws IOException, URISyntaxException {
        List<String> found =
                applicationThrowables().stream().map(Class::getName).sorted().toList();
        List<String> misnamed =
                found.stream().filter(name -> !name.endsWith(REQUIRED_SUFFIX)).toList();

        claim(
                "the search found the application's own throwable types, checked by naming one it must contain;"
                        + " without this, a search that had stopped finding anything at all would satisfy the"
                        + " claim below while checking nothing",
                () -> assertThat(found).contains(A_THROWABLE_THE_APPLICATION_DEFINES));
        claim(
                "every type the application can throw ends its name in Exception, so a reader meeting it in a"
                        + " catch or a throws clause knows what it is; a type named here would be one that reads"
                        + " as an ordinary value until someone looks it up",
                () -> assertThat(misnamed).isEmpty());
    }

    /** Every {@link Throwable} subtype compiled from {@code src/main}, anonymous classes aside. */
    private static List<Class<?>> applicationThrowables() throws IOException, URISyntaxException {
        Path compiled = Path.of(VesperaApplication.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        try (Stream<Path> files = Files.walk(compiled)) {
            return files.filter(file -> file.toString().endsWith(CLASS_FILE))
                    .map(file -> typeNameOf(compiled, file))
                    .filter(name -> !name.endsWith(PACKAGE_INFO))
                    .<Class<?>>map(ExceptionNamingTest::load)
                    .filter(Throwable.class::isAssignableFrom)
                    .filter(type -> !type.isAnonymousClass() && !type.isSynthetic())
                    .toList();
        }
    }

    private static String typeNameOf(Path compiled, Path classFile) {
        String relative = compiled.relativize(classFile).toString();
        return relative.substring(0, relative.length() - CLASS_FILE.length())
                .replace(java.io.File.separatorChar, '.')
                .replace('/', '.');
    }

    /**
     * Loaded without initialising: this only reads the type hierarchy, and running a static
     * initialiser to find out what a class extends would be a side effect the check does not need.
     */
    private static Class<?> load(String typeName) {
        try {
            return Class.forName(typeName, false, ExceptionNamingTest.class.getClassLoader());
        } catch (ClassNotFoundException cannotLoad) {
            throw new IllegalStateException(
                    "a class file under the application's own output directory did not load: " + typeName,
                    cannotLoad);
        }
    }
}
