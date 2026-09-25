package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * No whole-job test names, in its own {@code @Import}, a class {@link CascadeSliceTest} already
 * imports (ADR-153).
 *
 * <p>This has to be a test because Spring will not say so. It was expected to: bean overriding is
 * off, so a class registered twice looked as though it would fail the context. It does not — the
 * {@code @Import} on a test class and the one on its meta-annotation are gathered into one set before
 * anything is registered, so a class named in both is quietly registered once. Measured on
 * 2026-09-25 by adding {@code Ledger} and {@code CensusTasklet} to {@code CensusInvocationTest}'s own
 * list: all seven of its tests still passed.
 *
 * <p>Harmless to the context, then, but not to the point of the annotation: a class left in a test's
 * own list is a second place that test's wiring is written, and the next change to the job edits one
 * place and leaves the other stale. A test's own list is meant to say what makes it that test — its
 * extraction double and its probes — and nothing the whole job needs anyway.
 */
@Epic("Pipeline")
@Feature("How the whole-job tests are wired")
@Issue("294")
@Link(name = "ADR-153", url = Adr.THE_WHOLE_JOB_TESTS_SHARE_ONE_SLICE, type = "adr")
class CascadeSliceImportsTest {

    @Test
    @Story("A whole-job test names only what makes it that test")
    @DisplayName("No whole-job test repeats a class the shared slice already brings in")
    void noTestRepeatsWhatTheSliceImports() {
        Set<Class<?>> shared = Set.of(CascadeSliceTest.class.getAnnotation(Import.class).value());
        List<Class<?>> wholeJobTests = testsOnTheSlice();
        Map<String, List<String>> repeated = new TreeMap<>();
        for (Class<?> test : wholeJobTests) {
            Import own = test.getDeclaredAnnotation(Import.class);
            if (own == null) {
                continue;
            }
            List<String> both = Arrays.stream(own.value())
                    .filter(shared::contains)
                    .map(Class::getSimpleName)
                    .toList();
            if (!both.isEmpty()) {
                repeated.put(test.getSimpleName(), both);
            }
        }

        claim(
                "the whole-job tests were found at all, so the claim below is about something",
                () -> assertThat(wholeJobTests).isNotEmpty());
        claim(
                "no whole-job test names, in its own list, a class the shared slice already brings in -- a"
                        + " test listed here carries a second copy of wiring that lives in one place, which"
                        + " Spring merges without a word and the next change to the job leaves stale",
                () -> assertThat(repeated).isEmpty());
    }

    /** Every class in this package that carries {@link CascadeSliceTest}, found rather than listed. */
    private static List<Class<?>> testsOnTheSlice() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent() && !definition.getMetadata().isAnnotation();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(CascadeSliceTest.class, false));
        return scanner.findCandidateComponents(CascadeSliceTest.class.getPackageName()).stream()
                .map(definition -> ClassUtils.resolveClassName(
                        definition.getBeanClassName(), CascadeSliceImportsTest.class.getClassLoader()))
                .collect(Collectors.toList());
    }
}
