package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What an operator is told when no model is named to generate under (ADR-114, #179).
 *
 * <p><b>This is about the closing line surviving, not about the model.</b> A blanked default is a
 * misconfiguration that has stopped nothing yet: the invocation did all its work, and its last words
 * must not be a stack trace. The line drops the clause naming the model and keeps everything else.
 *
 * <p>Its own class rather than a case inside the closing line's other tests, for {@link
 * ConfiguredRootTest}'s reason — the condition is a property, and a property that differs needs a
 * context that differs. It wires only what {@link NextAction} takes, which is four beans, not the
 * whole cascade.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({NextAction.class, GenerationModel.class, ProfileStore.class, Ledger.class, RelevanceLabels.class})
@Epic("Operator path")
@Feature("The closing line")
@Issue("179")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class ClosingLineWithoutAGenerationModelTest {

    /** The first key the path asks for, so the line has something to say and a shape to keep. */
    private static final String THE_FIRST_KEY = "seedFolder";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void nothingNamesAModel(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("spring.ai.ollama.chat.options.model", () -> "");
    }

    @Autowired
    private NextAction nextAction;

    @Test
    @Story("A misconfiguration that has stopped nothing does not end the invocation in red")
    @DisplayName("With no model named anywhere, the invocation still ends on its closing line")
    void stillEndsOnTheClosingLine() {
        claim(
                "the line was written rather than thrown: everything this invocation was asked to do, it"
                        + " did, and a value that only matters to a later invocation must not take the"
                        + " operator's last words away from them",
                () -> assertThat(nextAction.line()).contains(THE_FIRST_KEY));
    }
}
