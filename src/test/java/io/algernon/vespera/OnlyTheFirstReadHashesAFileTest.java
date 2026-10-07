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
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Which shipped classes hash a file to find its extraction cache key, and which convert a file as a seed
 * is converted (ADR-206 section 4): the three that read a file for the first time, and no step after
 * them.
 *
 * <p>Read off the compiled classes, not off a list of the steps: a step added later that hashes a file
 * is found without anybody naming it here. Every class under {@code io.algernon.vespera} that the build
 * compiled from {@code src/main} is parsed, and every call in it is looked at, including the ones inside
 * a lambda, which the compiler keeps in the class that wrote it. Test classes are left out by where they
 * were compiled to, since the doubles in the test tree hash files on purpose.
 *
 * <p>Before ADR-206 was built this failed by assertion, naming the six classes that read a file again:
 * the embedding, relevance and clustering steps, the reader of a document's opening, the arrangement step
 * and the generation step.
 */
@Epic("Pipeline")
@Feature("How a stage reaches its run")
@Issue("349")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class OnlyTheFirstReadHashesAFileTest {

    private static final String SHIPPED_PACKAGE = "io.algernon.vespera";

    /** The class whose method hashes a file for its cache key, as the compiler names it. */
    private static final String THE_EXTRACTOR = "io/algernon/vespera/extraction/DoclingExtractor";

    private static final String HASHES_A_FILE = "contentHashFor";

    /** The class whose method detects a file's format from its bytes and converts it. */
    private static final String SEED_CONVERSIONS = "io/algernon/vespera/pipeline/SeedConversions";

    private static final String CONVERTS_A_FILE = "convert";

    /** Stage 2's reader, which hashes a file before it dispatches the call. */
    private static final String STAGE_TWO_READER = "io.algernon.vespera.pipeline.ConversionDispatch";

    /** Stage 2's per-document step, which hashes a file where it places the call itself. */
    private static final String STAGE_TWO_STEP = "io.algernon.vespera.pipeline.ExtractionItemProcessor";

    /** Seed extraction's per-seed step, the first read of a seed. */
    private static final String SEED_EXTRACTION_STEP = "io.algernon.vespera.pipeline.SeedExtractionItemProcessor";

    @Test
    @Story("A stage reads what an earlier stage recorded instead of the archive")
    @DisplayName("Only stage 2 and seed extraction hash a file to find its conversion, and no step after them does")
    void onlyTheFirstReadOfAFileHashesIt() throws Exception {
        Set<String> callers = shippedClassesCalling(THE_EXTRACTOR, HASHES_A_FILE);

        claim(
                "the classes that hash a file to find its conversion are stage 2's reader, stage 2's"
                        + " per-document step and seed extraction's per-seed step, and no other: those"
                        + " three read a file for the first time, and every step after them reads the"
                        + " key they recorded. A step that hashed the file again would read the whole"
                        + " archive once more, and would have to decide on its own what a file that"
                        + " no longer opens means",
                () -> assertThat(callers)
                        .containsExactlyInAnyOrder(STAGE_TWO_READER, STAGE_TWO_STEP, SEED_EXTRACTION_STEP));
    }

    @Test
    @Story("A stage reads what an earlier stage recorded instead of the archive")
    @DisplayName("Only seed extraction converts a file by reading its bytes for their format")
    void onlySeedExtractionConvertsAFileFromItsBytes() throws Exception {
        Set<String> callers = shippedClassesCalling(SEED_CONVERSIONS, CONVERTS_A_FILE);

        claim(
                "the one class that detects a file's format from its bytes and converts it is seed"
                        + " extraction's per-seed step: a seed has no earlier stage to have recorded what"
                        + " it is. The embedding step did the same for every surviving document and every"
                        + " seed, which read each file again even where its conversion was on record",
                () -> assertThat(callers).containsExactly(SEED_EXTRACTION_STEP));
    }

    /** The shipped classes holding a call to {@code method} of {@code owner}, by name, in order. */
    private static Set<String> shippedClassesCalling(String owner, String method)
            throws ClassNotFoundException, IOException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        Set<String> callers = new TreeSet<>();
        int shippedClasses = 0;
        for (BeanDefinition candidate : scanner.findCandidateComponents(SHIPPED_PACKAGE)) {
            Class<?> type = Class.forName(candidate.getBeanClassName(), false, Thread.currentThread().getContextClassLoader());
            if (compiledFromTheTestTree(type)) {
                continue;
            }
            shippedClasses++;
            if (calls(type, owner, method)) {
                callers.add(outermost(type).getName());
            }
        }
        if (shippedClasses == 0) {
            throw new IllegalStateException(
                    "the scan found no shipped class, so an answer naming few callers would be an empty scan");
        }
        return callers;
    }

    private static boolean compiledFromTheTestTree(Class<?> type) {
        return type.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString()
                .contains("test-classes");
    }

    private static Class<?> outermost(Class<?> type) {
        Class<?> outer = type;
        while (outer.getEnclosingClass() != null) {
            outer = outer.getEnclosingClass();
        }
        return outer;
    }

    private static boolean calls(Class<?> type, String owner, String method) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the compiled class of " + type.getName() + " could not be read");
            }
            ClassModel compiled = ClassFile.of().parse(in.readAllBytes());
            for (MethodModel declared : compiled.methods()) {
                if (declared.code().isEmpty()) {
                    continue;
                }
                for (CodeElement element : declared.code().get()) {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().equalsString(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
