package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPERS;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS;
import static io.algernon.vespera.publish.website.WebsiteFixture.SEARCH_INDEX;
import static io.algernon.vespera.publish.website.WebsiteFixture.WEBSITE;
import static io.algernon.vespera.publish.website.WebsiteFixture.WRITING;
import static io.algernon.vespera.publish.website.WebsiteFixture.filesUnder;
import static io.algernon.vespera.publish.website.WebsiteFixture.listedIn;
import static io.algernon.vespera.publish.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.publish.website.WebsiteFixture.survivors;
import static io.algernon.vespera.publish.website.WebsiteFixture.textOf;
import static io.algernon.vespera.publish.website.WebsiteFixture.written;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.ListedCodeFile;
import io.algernon.vespera.engine.deliverable.ListedCodeSet;
import io.algernon.vespera.engine.deliverable.ListedCodeSets;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The code sets are not on the website (ADR-333 §10): no {@code code/}, no {@code code.csv}, no row of a code set on
 * its index and nothing of a code set's in {@code search-index.js}, because a configuration file can hold a secret and
 * the operator's sync would carry it. They stay in the HTML tree, which is drawn here with one set, so that what the
 * website leaves out is there to be left out.
 */
@Epic("The deliverable")
@Feature("The website rendering")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class TheWebsiteCarriesNoCodeTest {

    /** The code set's folder in the archive and its name, which nothing of the website may carry. */
    private static final String THE_SETS_FOLDER = "billing-tool-4e91";

    /** A configuration file's secret, standing for what ADR-333 §10 keeps from the sync. */
    private static final String THE_SECRET = "CODE-SECRET-5d2b";

    private static final String THE_CONFIGURATION_FILE = THE_SETS_FOLDER + "/app.properties";

    private static final String THE_SOURCE_FILE = THE_SETS_FOLDER + "/src/Ledger.java";

    private static final Instant THE_WALKS_TIME = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    @Story("The website carries no code")
    @DisplayName("The website rendering and the website a publish refreshed hold no code/, no code.csv and nothing of a code set's, which the HTML tree holds")
    void theWebsiteHoldsNothingOfACodeSet(@TempDir Path base) throws IOException {
        Path drawnIn = base.resolve("drawn");
        Path archive = drawnIn.resolve("archive");
        ListedCodeSets code = ListedCodeSets.reading(
                () -> List.of(new ListedCodeSet(THE_SETS_FOLDER, THE_SETS_FOLDER, new OccurrenceId(300))),
                set -> List.of(
                        member(archive, 300, THE_CONFIGURATION_FILE, "configuration", "secret=" + THE_SECRET + "\n"),
                        member(archive, 301, THE_SOURCE_FILE, "code", "class Ledger {}\n")));
        Path tree = WebsiteFixture.drawn(drawnIn, List.of(DOORS, DAMPERS), written(WRITING), survivors(), code);
        Path rendering = renderingOf(tree);
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Plugin.Delivery delivery = WebsiteFixture.published(tree, workingDirectory);
        Path website = workingDirectory.resolve(WEBSITE);
        Map<String, WebsiteFixture.Listed> listed = listedIn(rendering);

        claim(
                "the HTML tree holds the code set, under code/ and in code.csv, so there is code to leave out",
                () -> {
                    assertThat(tree.resolve("code").resolve(THE_SETS_FOLDER).resolve("app.properties")).isRegularFile();
                    assertThat(tree.resolve("code.csv")).isRegularFile();
                    assertThat(textOf(tree.resolve("index.html"))).contains(THE_SETS_FOLDER);
                });
        claim(
                "the rendering holds no code/ and no code.csv",
                () -> assertThat(filesUnder(rendering).keySet())
                        .noneMatch(name -> name.startsWith("code/") || name.equals("code.csv")));
        claim(
                "files.json lists nothing of code/ and not code.csv, by its place in the website or by its source",
                () -> assertThat(listed.values()).allSatisfy(entry -> {
                    assertThat(entry.path()).doesNotStartWith("code/").isNotEqualTo("code.csv");
                    assertThat(entry.source()).doesNotContain("code/", "code.csv");
                }));
        claim(
                "no file of the rendering, its index and search-index.js among them, names the set, its files or its"
                        + " secret",
                () -> assertThat(filesUnder(rendering).values()).allSatisfy(file -> assertThat(textOf(file))
                        .as(file.toString())
                        .doesNotContain(THE_SETS_FOLDER, "app.properties", "Ledger.java", THE_SECRET)));
        claim(
                "search-index.js was among them",
                () -> assertThat(filesUnder(rendering)).containsKey(SEARCH_INDEX));
        claim(
                "the publish delivered, and the website it refreshed holds no code/ and no code.csv",
                () -> {
                    assertThat(delivery.delivered()).as(delivery.closingLine()).isTrue();
                    assertThat(website.resolve("code")).doesNotExist();
                    assertThat(website.resolve("code.csv")).doesNotExist();
                });
        claim(
                "and no file the publish wrote carries the secret or the set's name",
                () -> assertThat(filesUnder(website).values()).allSatisfy(file -> assertThat(
                                new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                        .as(file.toString())
                        .doesNotContain(THE_SECRET, THE_SETS_FOLDER)));
    }

    /**
     * A member of the set written into {@code archive} at {@code path} holding {@code text}, its time set to the
     * walk's, and as the walk recorded it (ADR-296 §2).
     */
    private static ListedCodeFile member(Path archive, long occurrence, String path, String role, String text) {
        try {
            Path original = archive.resolve(path);
            Files.createDirectories(original.getParent());
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (!Files.exists(original)) {
                Files.write(original, bytes);
                Files.setLastModifiedTime(original, FileTime.from(THE_WALKS_TIME));
            }
            return new ListedCodeFile(new OccurrenceId(occurrence), new OccurrencePath(path), THE_SETS_FOLDER, role,
                    null, bytes.length, THE_WALKS_TIME);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
