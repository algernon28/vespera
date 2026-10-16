package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * No test opens a browser on the machine it runs on (ADR-266 section 2). There is no setting that turns opening
 * off, so the whole application context a test builds holds the system's opener, and the recording one, which the
 * test tree declares as a primary component under the application's own package, so that every full context finds
 * it and every invocation in one asks it instead.
 *
 * <p>Configured as {@code TheApplicationStartsWithNoVectorStoreTest} is, so the two share one context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
class NoTestOpensABrowserTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @Story("No test opens a browser")
    @DisplayName("In the whole application a test builds, the page is handed to an opener that records it and opens nothing, although the system's opener is there too")
    void theWholeApplicationsOpenerIsTheRecordingOne() {
        Map<String, PageOpener> openers = context.getBeansOfType(PageOpener.class);
        claim(
                "the application holds the system's opener, as an operator's invocation does, so what is asked"
                        + " below is which of two openers wins",
                () -> assertThat(openers.values()).hasAtLeastOneElementOfType(SystemPageOpener.class));
        claim(
                "and the one an invocation is handed is the recording one, which opens nothing",
                () -> assertThat(context.getBean(PageOpener.class)).isInstanceOf(RecordingPageOpener.class));
    }
}
