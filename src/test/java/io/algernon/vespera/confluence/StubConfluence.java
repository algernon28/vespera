package io.algernon.vespera.confluence;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Confluence Cloud site in memory, served on the loopback address by the JDK's own HTTP server, answering the calls a
 * publish makes as {@code docs/research/confluence-loading.md} §5 measured them: a title is unique within a space, a
 * second {@code POST} of a file name is refused with {@code 400}, and a body is taken as sent. No test reaches a real
 * site.
 *
 * <p>The publish is pointed at {@code https://} {@value #SITE_HOST}: {@link #requests()} sends each request it builds
 * to this server instead, recording the address it was built for, so a test can tell where a publish would have sent.
 */
final class StubConfluence implements AutoCloseable {

    /** The host the publish is configured with; nothing resolves it. */
    static final String SITE_HOST = "site.example";

    /** The site's address as the operator sets it. */
    static final String BASE_URL = "https://" + SITE_HOST;

    /** The space's key and id. */
    static final String SPACE_KEY = "KT";

    static final String SPACE_ID = "65537";

    /** The folder the pages are published under, by id and by title. */
    static final String PARENT_ID = "7001";

    static final String PARENT_TITLE = "Handed over";

    /** The account and its token, synthetic. */
    static final String USER = "operator@example.org";

    static final String TOKEN = "TOKEN-a1b2c3d4e5-not-a-real-one";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A request as the stub was sent it: its method, the address the publish built, its headers and its body. */
    record Request(String method, URI addressed, Map<String, List<String>> headers, byte[] body) {
        String path() {
            return addressed.getRawPath();
        }

        String query(String name) {
            return queryOf(addressed).get(name);
        }

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }

    /** A page as the stub holds it. */
    static final class Page {
        final String id;
        String title;
        final String parentId;
        int version = 1;
        String body = "";
        final Set<String> labels = new HashSet<>();
        String propertyId;
        int propertyVersion;
        Map<String, Object> property;
        final Map<String, File> files = new LinkedHashMap<>();

        Page(String id, String title, String parentId) {
            this.id = id;
            this.title = title;
            this.parentId = parentId;
        }
    }

    /** A file on a page. */
    static final class File {
        final String id;
        final String name;
        byte[] bytes;
        String comment;

        File(String id, String name, byte[] bytes, String comment) {
            this.id = id;
            this.name = name;
            this.bytes = bytes;
            this.comment = comment;
        }
    }

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Integer> conflictsLeft = new HashMap<>();
    private final Set<String> tooLarge = new HashSet<>();
    private int pacesLeft;
    private String pacedWith = "7";
    private String nextLinkOfTheLabelSearch;
    private int ids = 10_000;

    private StubConfluence() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    static StubConfluence start() throws IOException {
        return new StubConfluence();
    }

    /** A request factory sending every request to this server, recording the address it was built for. */
    ClientHttpRequestFactory requests() {
        JdkClientHttpRequestFactory jdk = new JdkClientHttpRequestFactory();
        return (uri, method) -> {
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return jdk.createRequest(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + uri.getRawPath() + query + (query.isEmpty() ? "?" : "&") + "addressed="
                    + URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8)), method);
        };
    }

    /** A publisher of this site, the given environment and the configured values, its waits recorded in {@code waits}. */
    Publisher publisher(Function<String, String> environment, String baseUrl, String parentId, String parentTitle,
            List<Duration> waits) {
        return new Publisher(environment, requests(), waits::add, Clock.systemUTC(), baseUrl, SPACE_KEY, parentId,
                parentTitle);
    }

    /** A publisher of this site with the account's two values set and the parent named by id. */
    Publisher publisher(List<Duration> waits) {
        return publisher(theAccount(), BASE_URL, PARENT_ID, "", waits);
    }

    /** The environment holding the account's email and token, and nothing else. */
    static Function<String, String> theAccount() {
        return Map.of(ConfluenceSite.USER_VARIABLE, USER, ConfluenceSite.TOKEN_VARIABLE, TOKEN)::get;
    }

    /** Every request the stub was sent, in order. */
    List<Request> sent() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /** Forgets the requests it was sent; the pages stay. */
    void forgetTheRequests() {
        requests.clear();
    }

    /** The pages the site holds, in the order they were made. */
    synchronized List<Page> pages() {
        return List.copyOf(pages.values());
    }

    /** Answers the next {@code times} body updates of the page titled {@code title} with {@code 409}. */
    synchronized void conflictOn(String title, int times) {
        conflictsLeft.put(title, times);
    }

    /** Refuses the file named {@code name} as too large, with {@code 413}. */
    synchronized void refuseAsTooLarge(String name) {
        tooLarge.add(name);
    }

    /** Answers the next {@code times} requests with {@code 429} and {@code Retry-After: seconds}. */
    synchronized void pace(int times, String seconds) {
        pacesLeft = times;
        pacedWith = seconds;
    }

    /** Gives the label's search a {@code next} link to {@code link}. */
    synchronized void nextLinkOfTheLabelSearch(String link) {
        nextLinkOfTheLabelSearch = link;
    }

    /** Puts a page titled {@code title} in the space that no publish made. */
    synchronized void aPageNoPublishMade(String title) {
        pages.put("" + ids, new Page("" + ids++, title, PARENT_ID));
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            URI arrived = exchange.getRequestURI();
            URI addressed = URI.create(queryOf(arrived).get("addressed"));
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            Request request = new Request(exchange.getRequestMethod(), addressed, headers, body);
            requests.add(request);
            Answer answer;
            synchronized (this) {
                answer = answerTo(request);
            }
            answer.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] bytes = answer.body() == null ? new byte[0] : JSON.writeValueAsBytes(answer.body());
            if (bytes.length > 0) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        } catch (RuntimeException defect) {
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private record Answer(int status, Object body, Map<String, String> headers) {
        static Answer of(int status, Object body) {
            return new Answer(status, body, Map.of());
        }
    }

    private static final Pattern PAGE = Pattern.compile("/wiki/api/v2/pages/([^/]+)");
    private static final Pattern FOLDER = Pattern.compile("/wiki/api/v2/folders/([^/]+)");
    private static final Pattern PROPERTIES = Pattern.compile("/wiki/api/v2/pages/([^/]+)/properties");
    private static final Pattern PROPERTY = Pattern.compile("/wiki/api/v2/pages/([^/]+)/properties/([^/]+)");
    private static final Pattern ATTACHMENTS = Pattern.compile("/wiki/api/v2/pages/([^/]+)/attachments");
    private static final Pattern LABEL = Pattern.compile("/wiki/rest/api/content/([^/]+)/label");
    private static final Pattern ATTACH = Pattern.compile("/wiki/rest/api/content/([^/]+)/child/attachment");
    private static final Pattern ATTACH_DATA =
            Pattern.compile("/wiki/rest/api/content/([^/]+)/child/attachment/([^/]+)/data");
    private static final Pattern CQL_TITLE = Pattern.compile("title = \"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern CQL_LABEL = Pattern.compile("label = \"((?:[^\"\\\\]|\\\\.)*)\"");

    private Answer answerTo(Request request) {
        if (pacesLeft > 0) {
            pacesLeft--;
            return new Answer(429, Map.of("message", "Rate limited"), Map.of("Retry-After", pacedWith));
        }
        String path = request.path();
        String method = request.method();
        Matcher m;
        if (method.equals("GET") && path.equals("/wiki/api/v2/spaces")) {
            return Answer.of(200, results(SPACE_KEY.equals(request.query("keys"))
                    ? List.of(Map.of("id", SPACE_ID, "key", SPACE_KEY)) : List.of()));
        }
        if (method.equals("GET") && (m = FOLDER.matcher(path)).matches()) {
            return m.group(1).equals(PARENT_ID)
                    ? Answer.of(200, Map.of("id", PARENT_ID, "title", PARENT_TITLE, "spaceId", SPACE_ID))
                    : Answer.of(404, Map.of("message", "Not found"));
        }
        if (method.equals("GET") && path.equals("/wiki/rest/api/search")) {
            String title = unquoted(CQL_TITLE, request.query("cql"));
            List<Object> found = new ArrayList<>();
            if (PARENT_TITLE.equals(title)) {
                found.add(Map.of("content", Map.of("id", PARENT_ID, "type", "folder", "title", PARENT_TITLE)));
            }
            pages.values().stream().filter(page -> page.title.equals(title)).forEach(page ->
                    found.add(Map.of("content", Map.of("id", page.id, "type", "page", "title", page.title))));
            return Answer.of(200, results(found));
        }
        if (method.equals("GET") && path.equals("/wiki/rest/api/content/search")) {
            String label = unquoted(CQL_LABEL, request.query("cql"));
            List<Object> found = new ArrayList<>();
            pages.values().stream().filter(page -> page.labels.contains(label))
                    .forEach(page -> found.add(Map.of("id", page.id, "type", "page", "title", page.title)));
            Map<String, Object> answer = new LinkedHashMap<>(results(found));
            if (nextLinkOfTheLabelSearch != null && request.query("cursor") == null) {
                answer.put("_links", Map.of("next", nextLinkOfTheLabelSearch));
            }
            return Answer.of(200, answer);
        }
        if (method.equals("GET") && path.equals("/wiki/api/v2/pages")) {
            String title = request.query("title");
            return Answer.of(200, results(pages.values().stream().filter(page -> page.title.equals(title))
                    .map(StubConfluence::pageJson).toList()));
        }
        if (method.equals("POST") && path.equals("/wiki/api/v2/pages")) {
            JsonNode sent = JSON.readTree(request.body());
            String title = sent.path("title").asString();
            if (pages.values().stream().anyMatch(page -> page.title.equals(title))) {
                return Answer.of(400, Map.of("message", "A page already exists with the same TITLE in this space"));
            }
            Page page = new Page("" + ids++, title, sent.path("parentId").asString());
            page.body = sent.path("body").path("value").asString("");
            pages.put(page.id, page);
            return Answer.of(200, pageJson(page));
        }
        if ((m = PROPERTY.matcher(path)).matches() && method.equals("PUT")) {
            Page page = pages.get(m.group(1));
            JsonNode sent = JSON.readTree(request.body());
            if (page == null || !m.group(2).equals(page.propertyId)) {
                return Answer.of(404, Map.of("message", "Not found"));
            }
            if (sent.path("version").path("number").asInt() != page.propertyVersion + 1) {
                return Answer.of(409, Map.of("message", "Version must be incremented"));
            }
            page.propertyVersion++;
            page.property = JSON.convertValue(sent.path("value"), Map.class);
            return Answer.of(200, propertyJson(page));
        }
        if ((m = PROPERTIES.matcher(path)).matches()) {
            Page page = pages.get(m.group(1));
            if (page == null) {
                return Answer.of(404, Map.of("message", "Not found"));
            }
            if (method.equals("GET")) {
                return Answer.of(200, results(page.property == null ? List.of() : List.of(propertyJson(page))));
            }
            JsonNode sent = JSON.readTree(request.body());
            page.propertyId = "p" + ids++;
            page.propertyVersion = 1;
            page.property = JSON.convertValue(sent.path("value"), Map.class);
            return Answer.of(200, propertyJson(page));
        }
        if ((m = ATTACHMENTS.matcher(path)).matches() && method.equals("GET")) {
            Page page = pages.get(m.group(1));
            return Answer.of(200, results(page.files.values().stream().map(file -> (Object) Map.of("id", file.id,
                    "title", file.name, "fileSize", file.bytes.length, "comment", file.comment)).toList()));
        }
        if ((m = PAGE.matcher(path)).matches()) {
            Page page = pages.get(m.group(1));
            if (page == null) {
                return Answer.of(404, Map.of("message", "Not found"));
            }
            if (method.equals("GET")) {
                return Answer.of(200, pageJson(page));
            }
            if (method.equals("PUT")) {
                JsonNode sent = JSON.readTree(request.body());
                int left = conflictsLeft.getOrDefault(page.title, 0);
                if (left > 0) {
                    conflictsLeft.put(page.title, left - 1);
                    page.version++;
                    return Answer.of(409, Map.of("message", "Version conflict"));
                }
                if (sent.path("version").path("number").asInt() != page.version + 1) {
                    return Answer.of(409, Map.of("message", "Version conflict"));
                }
                page.version++;
                page.title = sent.path("title").asString();
                page.body = sent.path("body").path("value").asString();
                return Answer.of(200, pageJson(page));
            }
        }
        if ((m = LABEL.matcher(path)).matches() && method.equals("POST")) {
            Page page = pages.get(m.group(1));
            JSON.readTree(request.body()).forEach(label -> page.labels.add(label.path("name").asString()));
            return Answer.of(200, results(List.of()));
        }
        if ((m = ATTACH_DATA.matcher(path)).matches() && method.equals("POST")) {
            Page page = pages.get(m.group(1));
            Map<String, Part> parts = parts(request);
            Part file = parts.get("file");
            if (tooLarge.contains(file.filename())) {
                return Answer.of(413, Map.of("message", "Request entity too large"));
            }
            String attachmentId = m.group(2);
            File standing = page.files.values().stream().filter(f -> f.id.equals(attachmentId)).findFirst().orElseThrow();
            standing.bytes = file.bytes();
            standing.comment = new String(parts.get("comment").bytes(), StandardCharsets.UTF_8);
            return Answer.of(200, Map.of("id", standing.id, "title", standing.name));
        }
        if ((m = ATTACH.matcher(path)).matches() && method.equals("POST")) {
            Page page = pages.get(m.group(1));
            Map<String, Part> parts = parts(request);
            Part file = parts.get("file");
            if (tooLarge.contains(file.filename())) {
                return Answer.of(413, Map.of("message", "Request entity too large"));
            }
            if (page.files.containsKey(file.filename())) {
                return Answer.of(400, Map.of("message",
                        "Cannot add a new attachment with same file name as an existing attachment"));
            }
            File added = new File("att" + ids++, file.filename(), file.bytes(),
                    new String(parts.get("comment").bytes(), StandardCharsets.UTF_8));
            page.files.put(added.name, added);
            return Answer.of(200, results(List.of(Map.of("id", added.id, "title", added.name))));
        }
        return Answer.of(404, Map.of("message", "No such endpoint in the stub: " + method + " " + path));
    }

    private static Map<String, Object> results(List<?> results) {
        return Map.of("results", results);
    }

    private static Map<String, Object> pageJson(Page page) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", page.id);
        json.put("title", page.title);
        json.put("spaceId", SPACE_ID);
        json.put("parentId", page.parentId);
        json.put("version", Map.of("number", page.version));
        return json;
    }

    private static Map<String, Object> propertyJson(Page page) {
        return Map.of("id", page.propertyId, "key", ConfluenceApi.PROPERTY_KEY, "value", page.property,
                "version", Map.of("number", page.propertyVersion));
    }

    private static String unquoted(Pattern pattern, String cql) {
        Matcher matcher = pattern.matcher(cql == null ? "" : cql);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    static Map<String, String> queryOf(URI uri) {
        Map<String, String> query = new LinkedHashMap<>();
        if (uri.getRawQuery() == null) {
            return query;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            int at = pair.indexOf('=');
            if (at > 0) {
                query.put(URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
        }
        return query;
    }

    /** One part of a multipart body: its file name, where it has one, and its bytes. */
    record Part(String filename, byte[] bytes) {}

    /** The parts of a multipart request, by name. */
    static Map<String, Part> parts(Request request) {
        String type = request.header("Content-Type");
        String boundary = "--" + type.substring(type.indexOf("boundary=") + "boundary=".length()).replace("\"", "");
        byte[] body = request.body();
        byte[] delimiter = boundary.getBytes(StandardCharsets.ISO_8859_1);
        Map<String, Part> parts = new LinkedHashMap<>();
        List<Integer> starts = new ArrayList<>();
        for (int at = indexOf(body, delimiter, 0); at >= 0; at = indexOf(body, delimiter, at + delimiter.length)) {
            starts.add(at);
        }
        for (int i = 0; i + 1 < starts.size(); i++) {
            int from = starts.get(i) + delimiter.length + 2;
            int to = starts.get(i + 1) - 2;
            byte[] part = Arrays.copyOfRange(body, from, to);
            int split = indexOf(part, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
            String head = new String(part, 0, split, StandardCharsets.UTF_8);
            Matcher name = Pattern.compile("name=\"([^\"]*)\"").matcher(head);
            Matcher filename = Pattern.compile("filename=\"([^\"]*)\"").matcher(head);
            parts.put(name.find() ? name.group(1) : "", new Part(filename.find() ? filename.group(1) : null,
                    Arrays.copyOfRange(part, split + 4, part.length)));
        }
        return parts;
    }

    private static int indexOf(byte[] in, byte[] what, int from) {
        outer:
        for (int at = from; at <= in.length - what.length; at++) {
            for (int j = 0; j < what.length; j++) {
                if (in[at + j] != what[j]) {
                    continue outer;
                }
            }
            return at;
        }
        return -1;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
