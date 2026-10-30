package io.algernon.vespera.confluence;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The calls a publish makes to a Confluence Cloud site (ADR-308 §9): v2 for spaces, pages, content properties and
 * reading files, v1 for search, labels and sending files, which v2 does not do.
 *
 * <p><b>One origin.</b> Every request is built from the site's {@code https} origin and a path of its own, and a
 * {@code next} link a site answers with is followed only where it names that origin (ADR-308 §2); one naming another is
 * refused before anything is sent to it.
 *
 * <p><b>Pacing.</b> A {@code 429}, or a {@code 503} the site paces with, is waited out and the request sent again, up
 * to {@value #MOST_TRIES} times: for {@code Retry-After}'s seconds where it is given, else until {@code
 * X-RateLimit-Reset}'s instant, else for a wait that doubles from one second. No wait passes {@link #LONGEST_WAIT}.
 */
final class ConfluenceApi {

    /** The content property the publish keeps on each page it made (ADR-316 §4). */
    static final String PROPERTY_KEY = "vespera";

    /** The most times one request is sent while the site paces it. */
    static final int MOST_TRIES = 6;

    /** The longest one wait the publish holds for a paced request. */
    static final Duration LONGEST_WAIT = Duration.ofMinutes(5);

    /** How many results a listing asks for in one answer. */
    private static final int PAGE_OF_RESULTS = 100;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** How the publish waits out a paced request; the tests wait for nothing. */
    @FunctionalInterface
    interface Pause {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** A site's answer: its status, its headers, and its body read as JSON where it is JSON. */
    record Reply(int status, HttpHeaders headers, JsonNode body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        /** What the site said, for a line: its status and, where it gave one, its message. */
        String said() {
            String message = body == null ? "" : firstText(body, "message", "errorMessage", "title");
            if (message.isEmpty() && body != null && body.path("errors").isArray() && !body.path("errors").isEmpty()) {
                message = firstText(body.path("errors").get(0), "title", "message", "detail");
            }
            return "HTTP " + status + (message.isEmpty() ? "" : " (" + message + ")");
        }

        private static String firstText(JsonNode node, String... names) {
            for (String name : names) {
                String text = node.path(name).asString("");
                if (!text.isBlank()) {
                    return text.strip();
                }
            }
            return "";
        }
    }

    /** A page as the site holds it: its id, title, version and parent. */
    record SitePage(String id, String title, int version, String parentId, String spaceId) {}

    /** The content property {@value #PROPERTY_KEY} on a page: its id, version, and the key and digest it holds. */
    record Property(String id, int version, String key, String bodySha256) {}

    /** A file on a page: its id, its name, its size and its comment. */
    record SiteFile(String id, String name, long size, String comment) {}

    private final ConfluenceSite site;
    private final RestClient rest;
    private final Pause pause;
    private final Clock clock;

    ConfluenceApi(ConfluenceSite site, ClientHttpRequestFactory requests, Pause pause, Clock clock) {
        this.site = site;
        this.pause = pause;
        this.clock = clock;
        this.rest = RestClient.builder()
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.AUTHORIZATION, site.authorization())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** The id of the space whose key the site was given, if the site holds one. */
    Optional<String> spaceId(String key) throws InterruptedException {
        Reply reply = expect(get(uri("/wiki/api/v2/spaces", "keys", key)), "reading the space " + key);
        JsonNode results = reply.body().path("results");
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0).path("id").asString());
    }

    /** The space a page or a folder of this id lies in, if the site holds one. */
    Optional<String> spaceOfPageOrFolder(String id) throws InterruptedException {
        for (String kind : List.of("pages", "folders")) {
            Reply reply = get(uri("/wiki/api/v2/" + kind + "/" + segment(id)));
            if (reply.ok() && reply.body() != null) {
                return Optional.of(reply.body().path("spaceId").asString(""));
            }
            if (reply.status() != 404) {
                throw refused("reading the parent " + id, reply);
            }
        }
        return Optional.empty();
    }

    /** The ids of the pages and folders of the space titled exactly {@code title}. */
    List<String> pagesOrFoldersTitled(String spaceKey, String title) throws InterruptedException {
        String cql = "space = " + quoted(spaceKey) + " and type in (page, folder) and title = " + quoted(title);
        List<String> ids = new ArrayList<>();
        for (JsonNode result : listing(uri("/wiki/rest/api/search", "cql", cql, "limit", "" + PAGE_OF_RESULTS),
                "looking up the parent " + title)) {
            JsonNode content = result.path("content");
            if (title.equals(content.path("title").asString(result.path("title").asString("")))) {
                ids.add(content.path("id").asString());
            }
        }
        return ids;
    }

    /** The ids of the pages of the space carrying {@code label}. */
    List<String> pagesLabelled(String spaceKey, String label) throws InterruptedException {
        String cql = "space = " + quoted(spaceKey) + " and type = page and label = " + quoted(label);
        List<String> ids = new ArrayList<>();
        for (JsonNode result : listing(uri("/wiki/rest/api/content/search", "cql", cql, "limit", "" + PAGE_OF_RESULTS),
                "looking up the pages labelled " + label)) {
            ids.add(result.path("id").asString());
        }
        return ids;
    }

    /** The page of this id, if the site holds it. */
    Optional<SitePage> page(String id) throws InterruptedException {
        Reply reply = get(uri("/wiki/api/v2/pages/" + segment(id)));
        if (reply.status() == 404) {
            return Optional.empty();
        }
        return Optional.of(sitePage(expect(reply, "reading the page " + id).body()));
    }

    /** The page of the space titled exactly {@code title}, if one stands. */
    Optional<SitePage> pageTitled(String spaceId, String title) throws InterruptedException {
        Reply reply = expect(get(uri("/wiki/api/v2/pages", "space-id", spaceId, "title", title, "status", "current")),
                "looking up the page " + title);
        for (JsonNode page : reply.body().path("results")) {
            if (title.equals(page.path("title").asString())) {
                return Optional.of(sitePage(page));
            }
        }
        return Optional.empty();
    }

    /** The content property {@value #PROPERTY_KEY} on the page, if it carries one. */
    Optional<Property> property(String pageId) throws InterruptedException {
        Reply reply = expect(get(uri("/wiki/api/v2/pages/" + segment(pageId) + "/properties", "key", PROPERTY_KEY)),
                "reading the property of the page " + pageId);
        JsonNode results = reply.body().path("results");
        if (results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode property = results.get(0);
        JsonNode value = property.path("value");
        return Optional.of(new Property(property.path("id").asString(), property.path("version").path("number").asInt(1),
                value.path("key").asString(""), value.path("bodySha256").asString("")));
    }

    /** Creates an empty page titled {@code title} under {@code parentId} in the space, and gives it. */
    SitePage createPage(String spaceId, String parentId, String title) throws InterruptedException {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("spaceId", spaceId);
        page.put("status", "current");
        page.put("title", title);
        page.put("parentId", parentId);
        page.put("body", Map.of("representation", "storage", "value", ""));
        return sitePage(expect(send(HttpMethod.POST, uri("/wiki/api/v2/pages"), page), "creating the page " + title)
                .body());
    }

    /** Labels the page {@code label}; a label it carries already is kept as it is. */
    void label(String pageId, String label) throws InterruptedException {
        expect(send(HttpMethod.POST, uri("/wiki/rest/api/content/" + segment(pageId) + "/label"),
                List.of(Map.of("prefix", "global", "name", label))), "labelling the page " + pageId);
    }

    /** Takes {@code label} off the page; the reply is the caller's to read (a label not on it answers 404). */
    Reply removeLabel(String pageId, String label) throws InterruptedException {
        URI uri = uri("/wiki/rest/api/content/" + segment(pageId) + "/label", "name", label);
        return paced(uri, () -> rest.delete().uri(uri).exchange((request, response) ->
                reply(response.getStatusCode().value(), response.getHeaders(), response.getBody())));
    }

    /** A child of a page or a folder: its id, type and title. */
    record Child(String id, String type, String title) {}

    /** The direct children of the page or folder {@code id}, cursor-paged (ADR-328 §5, §6). */
    Optional<List<Child>> children(String id, boolean folder) throws InterruptedException {
        List<Child> children = new ArrayList<>();
        URI first = uri("/wiki/api/v2/" + (folder ? "folders/" : "pages/") + segment(id) + "/direct-children",
                "limit", "" + PAGE_OF_RESULTS);
        Reply opening = get(first);
        if (opening.status() == 404) {
            return Optional.empty();
        }
        for (JsonNode child : listing(opening, "listing the children of " + id)) {
            children.add(new Child(child.path("id").asString(), child.path("type").asString(""),
                    child.path("title").asString("")));
        }
        return Optional.of(children);
    }

    /**
     * Whether {@code id} is a folder of the site rather than a page: {@code 2xx} is a folder and {@code 404} a page
     * (ADR-328 §6). Any other answer is a refusal, the parent being taken for neither.
     */
    boolean isFolder(String id) throws InterruptedException {
        Reply reply = get(uri("/wiki/api/v2/folders/" + segment(id)));
        if (reply.ok()) {
            return true;
        }
        if (reply.status() == 404) {
            return false;
        }
        throw refused("reading the parent " + id, reply);
    }

    /** Moves the page to the space's trash, never purging it (ADR-328 §5); the reply is the caller's to read. */
    Reply trashPage(String pageId) throws InterruptedException {
        URI uri = uri("/wiki/api/v2/pages/" + segment(pageId));
        return paced(uri, () -> rest.delete().uri(uri).exchange((request, response) ->
                reply(response.getStatusCode().value(), response.getHeaders(), response.getBody())));
    }

    /** Sets the property on a page that carries none. */
    void createProperty(String pageId, String key, String bodySha256) throws InterruptedException {
        expect(send(HttpMethod.POST, uri("/wiki/api/v2/pages/" + segment(pageId) + "/properties"),
                Map.of("key", PROPERTY_KEY, "value", propertyValue(key, bodySha256))),
                "setting the property of the page " + pageId);
    }

    /** Sets the property standing on a page, with its version plus one. */
    void updateProperty(String pageId, Property property, String key, String bodySha256) throws InterruptedException {
        expect(send(HttpMethod.PUT, uri("/wiki/api/v2/pages/" + segment(pageId) + "/properties/"
                        + segment(property.id())),
                Map.of("key", PROPERTY_KEY, "value", propertyValue(key, bodySha256),
                        "version", Map.of("number", property.version() + 1))),
                "setting the property of the page " + pageId);
    }

    /** Sends the page's title and body as version {@code version}; the reply is the caller's to read. */
    Reply updatePage(String pageId, String title, String body, int version) throws InterruptedException {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("id", pageId);
        page.put("status", "current");
        page.put("title", title);
        page.put("body", Map.of("representation", "storage", "value", body));
        page.put("version", Map.of("number", version));
        return send(HttpMethod.PUT, uri("/wiki/api/v2/pages/" + segment(pageId)), page);
    }

    /** The files on the page, by name. */
    Map<String, SiteFile> files(String pageId) throws InterruptedException {
        Map<String, SiteFile> files = new LinkedHashMap<>();
        URI next = uri("/wiki/api/v2/pages/" + segment(pageId) + "/attachments", "limit", "" + PAGE_OF_RESULTS);
        for (JsonNode file : listing(next, "reading the files of the page " + pageId)) {
            String name = file.path("title").asString();
            files.put(name, new SiteFile(file.path("id").asString(), name, file.path("fileSize").asLong(-1),
                    file.path("comment").asString("")));
        }
        return files;
    }

    /**
     * Sends the file at {@code path} to the page under {@code name}, with {@code comment}: new, or over the file of
     * that name where {@code standing} is its id. The reply is the caller's to read.
     */
    Reply sendFile(String pageId, Optional<String> standing, Path path, String name, String comment)
            throws InterruptedException {
        String where = "/wiki/rest/api/content/" + segment(pageId) + "/child/attachment"
                + standing.map(id -> "/" + segment(id) + "/data").orElse("");
        URI uri = uri(where);
        return paced(uri, () -> {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(path) {
                @Override
                public String getFilename() {
                    return name;
                }
            });
            parts.add("comment", comment);
            parts.add("minorEdit", "true");
            return rest.post().uri(uri)
                    .header("X-Atlassian-Token", "no-check")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .exchange((request, response) -> reply(response.getStatusCode().value(), response.getHeaders(),
                            response.getBody()));
        });
    }

    /** The refusal a reply that is not a success makes, naming what was being done. */
    static TheSiteRefusedThePublishException refused(String doing, Reply reply) {
        return new TheSiteRefusedThePublishException("the site answered " + reply.said() + " while " + doing);
    }

    private static Map<String, String> propertyValue(String key, String bodySha256) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("key", key);
        value.put("bodySha256", bodySha256);
        return value;
    }

    private static SitePage sitePage(JsonNode page) {
        return new SitePage(page.path("id").asString(), page.path("title").asString(""),
                page.path("version").path("number").asInt(1), page.path("parentId").asString(null),
                page.path("spaceId").asString(null));
    }

    private Reply expect(Reply reply, String doing) {
        if (!reply.ok() || reply.body() == null) {
            throw refused(doing, reply);
        }
        return reply;
    }

    /** Every result of a listing, its {@code next} links followed while they name the site. */
    private List<JsonNode> listing(URI first, String doing) throws InterruptedException {
        return listing(get(first), doing);
    }

    /** Every result of a listing whose first answer is {@code opening}, its {@code next} links followed. */
    private List<JsonNode> listing(Reply opening, String doing) throws InterruptedException {
        List<JsonNode> results = new ArrayList<>();
        Reply reply = expect(opening, doing);
        while (true) {
            reply.body().path("results").forEach(results::add);
            String link = reply.body().path("_links").path("next").asString("");
            if (link.isBlank()) {
                return results;
            }
            reply = expect(get(followed(link)), doing);
        }
    }

    /**
     * A {@code next} link as a request of this site: v2 gives a path from the host, v1 one from {@code /wiki}; an
     * absolute link is followed only where it names the site's origin (ADR-308 §2).
     */
    private URI followed(String link) {
        if (link.startsWith("/wiki/")) {
            return URI.create(site.site() + link);
        }
        if (link.startsWith("/")) {
            return URI.create(site.site() + "/wiki" + link);
        }
        URI absolute = URI.create(link);
        if (!sameOrigin(absolute)) {
            throw new TheSiteRefusedThePublishException("the site answered with a link to another host, which the"
                    + " publish does not follow");
        }
        return absolute;
    }

    private boolean sameOrigin(URI uri) {
        URI origin = site.site();
        return "https".equalsIgnoreCase(uri.getScheme())
                && origin.getHost().equalsIgnoreCase(uri.getHost())
                && effectivePort(origin) == effectivePort(uri);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private Reply get(URI uri) throws InterruptedException {
        return paced(uri, () -> rest.get().uri(uri).exchange((request, response) ->
                reply(response.getStatusCode().value(), response.getHeaders(), response.getBody())));
    }

    private Reply send(HttpMethod method, URI uri, Object body) throws InterruptedException {
        return paced(uri, () -> rest.method(method).uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> reply(response.getStatusCode().value(), response.getHeaders(),
                        response.getBody())));
    }

    /** Sends the request, waiting out each answer that paces it, up to {@value #MOST_TRIES} sends. */
    private Reply paced(URI uri, java.util.function.Supplier<Reply> request) throws InterruptedException {
        if (!sameOrigin(uri)) {
            throw new TheSiteRefusedThePublishException("a request was built for another host than the site's, and"
                    + " was not sent");
        }
        Reply reply = request.get();
        for (int tries = 1; tries < MOST_TRIES && paces(reply); tries++) {
            pause.sleep(waitFor(reply, tries));
            reply = request.get();
        }
        return reply;
    }

    private static boolean paces(Reply reply) {
        return reply.status() == 429
                || (reply.status() == 503 && (reply.headers().getFirst(HttpHeaders.RETRY_AFTER) != null
                        || reply.headers().getFirst("X-RateLimit-Reset") != null));
    }

    /** How long a paced request waits before its next send (ADR-308; #633). */
    Duration waitFor(Reply reply, int tries) {
        Duration wait = Duration.ofSeconds(1L << Math.min(tries - 1, 8));
        String retryAfter = reply.headers().getFirst(HttpHeaders.RETRY_AFTER);
        String reset = reply.headers().getFirst("X-RateLimit-Reset");
        if (retryAfter != null && retryAfter.strip().matches("\\d{1,9}")) {
            wait = Duration.ofSeconds(Long.parseLong(retryAfter.strip()));
        } else if (reset != null) {
            try {
                Instant at = OffsetDateTime.parse(reset.strip()).toInstant();
                Duration until = Duration.between(clock.instant(), at);
                wait = until.isNegative() ? Duration.ZERO : until;
            } catch (DateTimeParseException notAnInstant) {
                // The doubling wait stands.
            }
        }
        return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    }

    private static Reply reply(int status, HttpHeaders headers, InputStream body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.readAllBytes();
        JsonNode json = null;
        if (bytes.length > 0) {
            try {
                json = JSON.readTree(bytes);
            } catch (RuntimeException notJson) {
                json = null;
            }
        }
        HttpHeaders kept = new HttpHeaders();
        kept.putAll(headers);
        return new Reply(status, kept, json);
    }

    /** {@code path} on the site, with its query's pairs encoded. */
    private URI uri(String path, String... query) {
        StringBuilder uri = new StringBuilder(site.site().toString()).append(path);
        for (int at = 0; at + 1 < query.length; at += 2) {
            uri.append(at == 0 ? '?' : '&').append(query[at]).append('=').append(encoded(query[at + 1]));
        }
        return URI.create(uri.toString());
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String segment(String id) {
        return encoded(id);
    }

    /** {@code text} as a CQL string: in double quotes, each backslash and double quote escaped. */
    private static String quoted(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
