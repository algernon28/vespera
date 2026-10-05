package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Three of the capability modules, {@code similarity}, {@code embedding} and {@code synthesis}, declare no
 * SLF4J logger (ADR-192 section 5, #412).
 *
 * <p>ADR-192 has the loops of these three modules tell {@code pipeline} each total and each completion
 * through interfaces they own, and has {@code pipeline} write every counter's line, so that no stage's name
 * and no wording of its progress enters them. This pins the narrow thing that can be pinned about that: no
 * class in the three packages has a field of type {@code org.slf4j.Logger}. It does not pin that no line is
 * written by them: a logger obtained inside a method, or a line written some other way, would not be seen.
 * What sees a counter line written from the wrong place is the whole-job tests, each of which asserts that
 * every line of every counter it names was written by {@code StageProgress}'s logger.
 *
 * <p>It does not look at {@code corpus} or {@code extraction}. Both declare loggers, and {@code corpus}'s
 * walk writes the census's running count (ADR-093, ADR-192 section 6).
 *
 * <p>The scan takes every class the packages hold, interfaces, abstract classes and nested classes included,
 * so a logger constant on one of the interfaces ADR-192 adds would be seen. It passes on main and has to go on
 * passing through every part of ADR-192.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class SimilarityEmbeddingAndSynthesisDeclareNoLoggerTest {

    private static final List<String> MODULES = List.of("similarity", "embedding", "synthesis");

    @Test
    @Story("A capability module hands its counts to the stage that reports them")
    @DisplayName("No class, interface or nested class in the similarity, embedding or synthesis module declares a logger")
    void noTypeOfTheThreeModulesDeclaresALogger() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<String> typesScanned = new ArrayList<>();
        List<String> withALogger = new ArrayList<>();
        for (String module : MODULES) {
            for (BeanDefinition candidate : scanner.findCandidateComponents("io.algernon.vespera." + module)) {
                typesScanned.add(candidate.getBeanClassName());
                Class<?> type = Class.forName(candidate.getBeanClassName());
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType().getName().equals("org.slf4j.Logger")) {
                        withALogger.add(type.getName() + "." + field.getName());
                    }
                }
            }
        }

        claim(
                "the scan found types in all three modules, so an empty answer below is not an empty scan",
                () -> assertThat(typesScanned)
                        .anyMatch(name -> name.startsWith("io.algernon.vespera.similarity."))
                        .anyMatch(name -> name.startsWith("io.algernon.vespera.embedding."))
                        .anyMatch(name -> name.startsWith("io.algernon.vespera.synthesis.")));
        claim(
                "and none of them declares a field of the logger type",
                () -> assertThat(withALogger).isEmpty());
    }
}
