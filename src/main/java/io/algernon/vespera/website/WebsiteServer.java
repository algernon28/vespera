package io.algernon.vespera.website;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableRoot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The server of {@code vespera serve} (ADR-333 §8): it serves a published website, {@code <working directory>/website/},
 * bound to {@code 127.0.0.1} alone, with no login, on the JDK's own HTTP server.
 *
 * <p><b>What it answers.</b> {@code GET} and {@code HEAD} alone, and nothing else (405). {@code Host} has to be {@code
 * 127.0.0.1:<port>} or {@code localhost:<port>}, and a {@code Sec-Fetch-Site} of {@code cross-site} or {@code same-site}
 * is refused (403), as ADR-291 §4 and ADR-302 §12 refuse them, since the folder holds the operator's documents and
 * another website in the same browser must not read them. A path is resolved beneath the folder and answered only where
 * its real path lies beneath the folder's real path. A folder answers its {@code index.html}, or 404: no listing. The
 * marker, the website's list and any {@code .part} are 404. Every answer carries {@code X-Content-Type-Options:
 * nosniff}, and every file but a page, an asset or the listing of the website carries {@code Content-Security-Policy:
 * sandbox}, so a document that is HTML runs no script in the website's origin.
 *
 * <p><b>A page, an asset, a listing.</b> The website's list names a path and a digest and no kind (ADR-339 §3), so a file
 * is one of the three where the list names it <em>and</em> its name is one the website draws: {@code index.html} or {@code
 * index-<k>.html}, which a copy's name never is, the page's names being reserved in a cluster folder (ADR-280), or, at the
 * folder's top, the script, the theme, the search index and the listing. Every other file, each copy and whatever the
 * operator put there, is sandboxed. The list is read again when it changes, since a publish can refresh the website while
 * it is served.
 *
 * <p>No request is logged, a request's path naming a document (ADR-274 §8).
 */
public final class WebsiteServer implements AutoCloseable {

    private static final String LOOPBACK = "127.0.0.1";

    private static final int OK = 200;
    private static final int MOVED = 301;
    private static final int REFUSED = 403;
    private static final int NOT_FOUND = 404;
    private static final int NO_SUCH_METHOD = 405;
    private static final int FAILED = 500;

    /** A page of a cluster beyond its first, {@code index-<k>.html} (ADR-303). */
    private static final Pattern FURTHER_PAGE = Pattern.compile("index-\\d+\\.html");

    /** What the website draws at its top beside its index (ADR-333 §3). */
    private static final Set<String> AT_THE_TOP = Set.of(
            WebsiteDrawing.SEARCH_INDEX_FILE_NAME,
            WebsiteDrawing.SCRIPT_FILE_NAME,
            WebsiteDrawing.THEME_FILE_NAME,
            Deliverable.MANIFEST_FILE_NAME);

    /** What no request is answered with, though it is there: the marker and the website's own list, at the top. */
    private static final Set<String> HELD_BACK = Set.of(
            DeliverableRoot.MARKER_FILE_NAME.toLowerCase(Locale.ROOT),
            WebsiteDelivery.HELD_FILE_NAME.toLowerCase(Locale.ROOT));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path folder;
    private final Path realFolder;
    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    private Set<String> listedPaths = Set.of();
    private long listedStamp = Long.MIN_VALUE;
    private long listedSize = -1;

    private WebsiteServer(Path folder, Path realFolder, HttpServer server, ExecutorService executor) {
        this.folder = folder;
        this.realFolder = realFolder;
        this.server = server;
        this.executor = executor;
        this.port = server.getAddress().getPort();
    }

    /**
     * Starts serving {@code folder} on {@code 127.0.0.1}.
     *
     * @param port the port to listen at; {@code 0} lets the system pick, which a test asks and the command never does
     * @throws IOException where the port cannot be bound (a {@link java.net.BindException} where it is in use)
     */
    public static WebsiteServer start(Path folder, int port) throws IOException {
        Path realFolder = folder.toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(LOOPBACK), port), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        WebsiteServer website = new WebsiteServer(folder, realFolder, server, executor);
        server.createContext("/", website::handle);
        server.setExecutor(executor);
        server.start();
        return website;
    }

    /** The address it is bound to. */
    public InetSocketAddress address() {
        return server.getAddress();
    }

    /** Stops serving. It throws nothing. */
    @Override
    public void close() {
        try {
            server.stop(0);
        } finally {
            executor.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            boolean head = "HEAD".equals(exchange.getRequestMethod());
            try {
                if (!ownHost(exchange.getRequestHeaders().getFirst("Host")) || fromAnotherSite(exchange)) {
                    answer(exchange, REFUSED, "Refused", head);
                } else if (!head && !"GET".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    answer(exchange, NO_SUCH_METHOD, "Method not allowed", false);
                } else {
                    serve(exchange, head);
                }
            } catch (IOException | RuntimeException failure) {
                if (exchange.getResponseCode() == -1) {
                    // No status line sent yet: any Content-Type already set is replaced by the answer's own.
                    answer(exchange, FAILED, "Failed", head);
                }
            }
        }
    }

    private boolean ownHost(String host) {
        return host != null
                && (host.equalsIgnoreCase(LOOPBACK + ":" + port) || host.equalsIgnoreCase("localhost:" + port));
    }

    private static boolean fromAnotherSite(HttpExchange exchange) {
        String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        return site != null && (site.strip().equalsIgnoreCase("cross-site") || site.strip().equalsIgnoreCase("same-site"));
    }

    private void serve(HttpExchange exchange, boolean head) throws IOException {
        URI target = exchange.getRequestURI();
        Path file = fileOf(target.getPath());
        if (file == null) {
            answer(exchange, NOT_FOUND, "Not found", head);
            return;
        }
        if (Files.isDirectory(file)) {
            String raw = target.getRawPath();
            if (!raw.endsWith("/")) {
                exchange.getResponseHeaders().set("Location", raw + "/");
                answer(exchange, MOVED, "Moved", head);
                return;
            }
            file = within(file.resolve(Deliverable.INDEX_FILE_NAME));
            if (file == null) {
                answer(exchange, NOT_FOUND, "Not found", head);
                return;
            }
        }
        if (!Files.isRegularFile(file)) {
            answer(exchange, NOT_FOUND, "Not found", head);
            return;
        }
        String relative = relativeName(file);
        if (heldBack(relative)) {
            answer(exchange, NOT_FOUND, "Not found", head);
            return;
        }
        if (!isOneOfTheWebsites(relative)) {
            exchange.getResponseHeaders().set("Content-Security-Policy", "sandbox");
        }
        exchange.getResponseHeaders().set("Content-Type", contentTypeOf(file.getFileName().toString()));
        long size = Files.size(file);
        if (head) {
            exchange.sendResponseHeaders(OK, -1);
            return;
        }
        exchange.sendResponseHeaders(OK, size == 0 ? -1 : size);
        if (size > 0) {
            try (InputStream in = Files.newInputStream(file); OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        }
    }

    /** The file {@code decodedPath} names beneath the folder, or {@code null} where it names none that lies beneath it. */
    private Path fileOf(String decodedPath) {
        if (decodedPath == null || !decodedPath.startsWith("/") || decodedPath.indexOf('\\') >= 0
                || decodedPath.indexOf('\0') >= 0) {
            return null;
        }
        Path resolved = folder;
        for (String name : decodedPath.split("/", -1)) {
            if (name.isEmpty()) {
                continue;
            }
            if (name.equals(".") || name.equals("..") || name.indexOf(':') >= 0) {
                return null;
            }
            try {
                resolved = resolved.resolve(name);
            } catch (InvalidPathException unspellable) {
                return null;
            }
        }
        return within(resolved);
    }

    /** {@code path}'s real path where it lies beneath the folder's real path, else {@code null}. */
    private Path within(Path path) {
        try {
            Path real = path.toRealPath();
            return real.startsWith(realFolder) ? real : null;
        } catch (IOException | InvalidPathException notThere) {
            return null;
        }
    }

    private String relativeName(Path realFile) {
        return realFolder.relativize(realFile).toString().replace('\\', '/');
    }

    private static boolean heldBack(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.endsWith(Deliverable.PART_SUFFIX) || HELD_BACK.contains(lower);
    }

    /** Whether the website's list names {@code relative} and it is a name the website draws as a page, asset or listing. */
    private boolean isOneOfTheWebsites(String relative) {
        int slash = relative.lastIndexOf('/');
        String name = relative.substring(slash + 1);
        boolean page = name.equals(Deliverable.INDEX_FILE_NAME) || FURTHER_PAGE.matcher(name).matches();
        boolean atTheTop = slash < 0 && AT_THE_TOP.contains(name);
        return (page || atTheTop) && listed(relative);
    }

    private synchronized boolean listed(String relative) {
        Path list = realFolder.resolve(WebsiteDelivery.HELD_FILE_NAME);
        try {
            long stamp = Files.getLastModifiedTime(list).toMillis();
            long size = Files.size(list);
            if (stamp != listedStamp || size != listedSize) {
                Set<String> paths = new HashSet<>();
                for (JsonNode node : JSON.readTree(list.toFile()).path("files").valueStream().toList()) {
                    paths.add(node.path("path").asString(""));
                }
                listedPaths = paths;
                listedStamp = stamp;
                listedSize = size;
            }
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
        return listedPaths.contains(relative);
    }

    private static String contentTypeOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return switch (dot < 0 ? "" : lower.substring(dot + 1)) {
            case "html", "htm" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "csv" -> "text/csv; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "txt" -> "text/plain; charset=utf-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    /** Answers {@code status} in a line of ours, which names no path, and nothing at all to a {@code HEAD}. */
    private static void answer(HttpExchange exchange, int status, String line, boolean head) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        byte[] body = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (head) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
