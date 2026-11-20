package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The topics the profile lists enter the identity of the embedding-scoring run, under which placement is written, as
 * its last member, {@code topics}, the names in the list's order (ADR-429 §11): so a list edited, a reordering
 * included, is another scoring run, and the runs after it follow.
 *
 * <p>The list is appended to the profile as text, as the operator would write it, so that this compiles whatever
 * {@code Profile}'s components are. {@code RunIdentityGoldenTest} holds, unchanged, that with no list no member is
 * written.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("898")
@Link(name = "ADR-429", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class TheListedTopicsEnterTheScoringRunInvocationTest {

    /** The names, in the order the profile first lists them. */
    private static final List<String> AS_LISTED = List.of("Pagamenti", "Incassi");

    /** The same names, the other way round. */
    private static final List<String> REORDERED = List.of("Incassi", "Pagamenti");

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void theWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void aCorpusOfOneDocumentAndOneSeed() throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("A profile listing two topics gets a scoring run whose settings end with the two names, in the order listed")
    void theListInItsOrderIsTheLastMember() throws IOException {
        theProfileLists(AS_LISTED);

        cli.run("run", root.toString());

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("its one scoring run of that list has settings ending with the names, Pagamenti then Incassi, as the last"
                        + " member",
                () -> assertThat(scoringRunsListing(AS_LISTED)).singleElement().asString()
                        .endsWith(member(AS_LISTED) + "}"));
    }

    @Test
    @Story("Editing the list of topics starts new runs from the topics on")
    @DisplayName("The same two topics listed the other way round are another scoring run, the first kept")
    void aReorderingIsAnotherScoringRun() throws IOException {
        theProfileLists(AS_LISTED);
        cli.run("run", root.toString());
        theProfileLists(REORDERED);
        cli.run("run", root.toString());

        claim("both invocations ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("one scoring run names the names in the first order, and another, beside it, names them reordered",
                () -> {
                    assertThat(scoringRunsListing(AS_LISTED)).hasSize(1);
                    assertThat(scoringRunsListing(REORDERED)).hasSize(1);
                });
    }

    /**
     * The whole profile, written as text as the operator would write it: the seed folder, stage 4's floor and the
     * embedding model, so that every gate to stage 5 is open, and {@code names} as its topics, one a line. Every other key
     * arrives unset.
     */
    private void theProfileLists(List<String> names) throws IOException {
        String text = "---\n"
                + key("seedFolder", "'" + seeds.toAbsolutePath() + "'")
                + key("boilerplateDocumentFrequencyFloor", "'" + EveryGateOpen.BOILERPLATE_FLOOR + "'")
                + key("embeddingModel", "'" + EveryGateOpen.EMBEDDING_MODEL + "'")
                + key("topics", "\"" + String.join("\\n", names) + "\"");
        Files.createDirectories(profileStore.file().getParent());
        Files.writeString(profileStore.file(), text, StandardCharsets.UTF_8);
    }

    /** One key of the profile as text, its value the scalar {@code value} as written. */
    private static String key(String name, String value) {
        return name + ":\n  value: " + value + "\n  provenance: \"set by this test\"\n  measurement: null\n";
    }

    /** The settings of every scoring run over this class's corpus root whose topics are {@code names}, in that order. */
    private List<String> scoringRunsListing(List<String> names) {
        return jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring' AND config_consumed LIKE ?"
                        + " AND config_consumed LIKE ?",
                String.class,
                "%" + Walk.canonicalRoot(root).toString().replace("\\", "\\\\") + "%",
                "%" + member(names) + "%");
    }

    /** The member, as the scoring run's recorded settings write it. */
    private static String member(List<String> names) {
        return "\"topics\":[\"" + String.join("\",\"", names) + "\"]";
    }
}
