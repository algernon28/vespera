package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * A key of four numbers, {@code P.S.C.K}, page K of a split cluster of a crowded topic, is a key of the tree (ADR-415
 * §7): a publish finds the page holding it among the tree's pages, and moves it to the trash where the rendering no
 * longer has it (ADR-328 §5). A key of five numbers is no key of the tree, and its page is left alone.
 *
 * <p>The rendering lists its root page alone, its body's digest the one the site holds, so the publish sends nothing
 * and goes to its removals. The site, answered in memory, holds that root page and two more labelled as the tree's: one
 * keyed {@code en:1.2.3.2}, one {@code en:1.2.3.2.1}; nothing stands under either.
 */
@Epic("The deliverable")
@Feature("The publish")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
class APageKeyedByFourNumbersIsOfTheTreeTest {

    /** The generation run the rendering is of, and its marker. */
    private static final String RUN = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static final String MARKER = "abcdef012345";

    private static final String ROOT_PAGE_ID = "9001";

    /** Page 2 of the third group of the second subtopic of the first topic, which the rendering no longer has. */
    private static final String FOUR_NUMBERS_ID = "9002";

    private static final String FOUR_NUMBERS_TITLE = "Pump seals (1.2.3 page 2 · en)";

    /** A page carrying the tree's label whose key has one number too many for a place. */
    private static final String FIVE_NUMBERS_ID = "9003";

    private static final String ROOT_BODY = "<h1>Deliverable</h1>\n";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("A publish updates one tree in place, and trashes what it no longer has")
    @DisplayName("A page keyed by four numbers is found as a page of the tree and moved to the trash where the rendering no longer has it, and one keyed by five is left alone")
    void aPageKeyedByFourNumbersIsMovedToTheTrashAndOneOfFiveIsLeftAlone(@TempDir Path base) throws IOException {
        Path confluence = aRenderingOfTheRootPageAlone(base.resolve("tree"));
        SiteInMemory site = new SiteInMemory(sha256(ROOT_BODY));
        Map<String, String> environment = Map.of(
                StubConfluence.EMAIL_VARIABLE, StubConfluence.USER, ConfluenceSite.TOKEN_VARIABLE, StubConfluence.TOKEN);
        Publisher publisher = new Publisher(environment::get, site, duration -> { }, Clock.systemUTC(),
                StubConfluence.BASE_URL, StubConfluence.SPACE_KEY, StubConfluence.PARENT_ID, "",
                directory -> ConfluenceValues.NONE);
        List<String> lines = new ArrayList<>();

        Publisher.Outcome outcome = publisher.publish(
                confluence, RUN, Files.createDirectories(base.resolve("work")), lines::add);

        claim("the publish goes to its end", () -> assertThat(outcome.published()).as(lines.toString()).isTrue());
        claim("the page keyed by four numbers is the tree's, one the rendering no longer has, and is moved to the trash",
                () -> assertThat(lines).contains(
                        "1 page of the tree the rendering no longer has is moved to the trash",
                        "removal 1 of 1: " + FOUR_NUMBERS_TITLE + ", moved to the trash"));
        claim("the one page moved to the trash is it, and not the page keyed by five numbers, which is no page of the"
                        + " tree",
                () -> assertThat(site.trashed()).containsExactly(FOUR_NUMBERS_ID));
    }

    /** A rendering in {@code tree}'s {@code confluence/} listing the root page alone, carrying no file. */
    private static Path aRenderingOfTheRootPageAlone(Path tree) throws IOException {
        Path confluence = Files.createDirectories(tree.resolve("confluence"));
        Files.writeString(confluence.resolve("page.xml"), ROOT_BODY, StandardCharsets.UTF_8);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("key", "en:root");
        root.put("parent", null);
        root.put("title", "Vespera_en");
        root.put("body", "page.xml");
        root.put("bodySha256", sha256(ROOT_BODY));
        root.put("files", List.of());
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("format", 3);
        list.put("generationRun", RUN);
        list.put("marker", MARKER);
        list.put("language", "en");
        list.put("pages", List.of(root));
        Files.write(confluence.resolve("pages.json"), JSON.writeValueAsBytes(list));
        return confluence;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /**
     * A site answered in memory: the space {@code KT}; the folder {@code 7001} the publish names as its parent; and three
     * pages labelled {@code vespera-en} under it, the tree's root page, whose body's digest is {@code rootDigest}, and
     * the two of four and five numbers, under nothing of their own. A page's {@code DELETE} is taken and recorded; any
     * other write, and any request not named, is refused, so a publish that sent one would not go to its end.
     */
    private static final class SiteInMemory implements ClientHttpRequestFactory {

        private static final String PAGES = "/wiki/api/v2/pages/";

        private final String rootDigest;

        private final List<String> trashed = new ArrayList<>();

        SiteInMemory(String rootDigest) {
            this.rootDigest = rootDigest;
        }

        List<String> trashed() {
            return List.copyOf(trashed);
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            return new MockClientHttpRequest(method, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    return answer(method.name() + " " + uri.getRawPath());
                }
            };
        }

        private ClientHttpResponse answer(String request) {
            if (request.equals("GET /wiki/api/v2/spaces")) {
                return json(200, results(Map.of("id", StubConfluence.SPACE_ID, "key", StubConfluence.SPACE_KEY)));
            }
            if (request.equals("GET /wiki/api/v2/folders/" + StubConfluence.PARENT_ID)) {
                return json(200, Map.of("id", StubConfluence.PARENT_ID, "title", StubConfluence.PARENT_TITLE,
                        "spaceId", StubConfluence.SPACE_ID));
            }
            if (request.equals("GET /wiki/rest/api/content/search")) {
                return json(200, results(page(ROOT_PAGE_ID, "Vespera_en"), page(FOUR_NUMBERS_ID, FOUR_NUMBERS_TITLE),
                        page(FIVE_NUMBERS_ID, "Pump seals (1.2.3.2.1 · en)")));
            }
            for (String id : List.of(ROOT_PAGE_ID, FOUR_NUMBERS_ID, FIVE_NUMBERS_ID)) {
                if (request.equals("GET " + PAGES + id)) {
                    return json(200, Map.of("id", id, "title", titleOf(id), "spaceId", StubConfluence.SPACE_ID,
                            "parentId", id.equals(ROOT_PAGE_ID) ? StubConfluence.PARENT_ID : ROOT_PAGE_ID,
                            "version", Map.of("number", 1)));
                }
                if (request.equals("GET " + PAGES + id + "/properties")) {
                    return json(200, results(Map.of("id", "p" + id, "key", ConfluenceApi.PROPERTY_KEY,
                            "version", Map.of("number", 1),
                            "value", Map.of("key", keyOf(id), "bodySha256", id.equals(ROOT_PAGE_ID) ? rootDigest
                                    : "0".repeat(64)))));
                }
                if (request.equals("GET " + PAGES + id + "/direct-children")) {
                    return json(200, results());
                }
                if (request.equals("DELETE " + PAGES + id)) {
                    trashed.add(id);
                    return new MockClientHttpResponse(new byte[0], 204);
                }
            }
            return json(404, Map.of("message", "the test's site does not answer " + request));
        }

        private static String titleOf(String id) {
            return switch (id) {
                case ROOT_PAGE_ID -> "Vespera_en";
                case FOUR_NUMBERS_ID -> FOUR_NUMBERS_TITLE;
                default -> "Pump seals (1.2.3.2.1 · en)";
            };
        }

        private static String keyOf(String id) {
            return switch (id) {
                case ROOT_PAGE_ID -> "en:root";
                case FOUR_NUMBERS_ID -> "en:1.2.3.2";
                default -> "en:1.2.3.2.1";
            };
        }

        private static Map<String, Object> page(String id, String title) {
            return Map.of("id", id, "type", "page", "title", title);
        }

        private static Map<String, Object> results(Object... results) {
            return Map.of("results", List.of(results));
        }

        private static ClientHttpResponse json(int status, Object body) {
            MockClientHttpResponse response = new MockClientHttpResponse(JSON.writeValueAsBytes(body), status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        }
    }
}
