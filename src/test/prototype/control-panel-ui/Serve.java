import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROTOTYPE, throw me away. Not compiled by Maven, not part of either jar.
 *
 * Question: what should the control panel look like, with pickers and example hints, opening a window of its own?
 *
 * Run from the repository's root:   java src/test/prototype/control-panel-ui/Serve.java
 * Then flip with the bottom bar or ?variant=A|B|C. Add --no-open to open no window.
 *
 * Real: the app window, the folder listing (folder names only, no file is read), the Windows folder dialog, and
 * Ollama's model list where Ollama answers. Everything else, a run above all, is simulated in the page's memory.
 */
public class Serve {

    static final int PORT = 24292;
    static final Path PAGE = Path.of("src/test/prototype/control-panel-ui/index.html");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/", exchange -> guarded(exchange, Serve::route));
        server.start();
        String address = "http://127.0.0.1:" + PORT + "/";
        System.out.println("PROTOTYPE at " + address + "  (Ctrl+C ends it)");
        if (!List.of(args).contains("--no-open")) {
            System.out.println("opened with: " + String.join(" ", openWindow(address)));
        }
    }

    interface Route {
        void handle(HttpExchange exchange) throws Exception;
    }

    static void guarded(HttpExchange exchange, Route route) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
            boolean ownHost = ("127.0.0.1:" + PORT).equals(host) || ("localhost:" + PORT).equals(host);
            boolean otherSite = "cross-site".equalsIgnoreCase(site) || "same-site".equalsIgnoreCase(site);
            if (!ownHost || otherSite) {
                send(exchange, 403, "text/plain", "refused");
                return;
            }
            route.handle(exchange);
        } catch (Exception e) {
            send(exchange, 500, "application/json", "{\"error\":" + quote(e.getClass().getName()) + "}");
        }
    }

    static void route(HttpExchange exchange) throws Exception {
        switch (exchange.getRequestURI().getPath()) {
            case "/" -> send(exchange, 200, "text/html; charset=utf-8", Files.readString(PAGE));
            case "/api/roots" -> send(exchange, 200, "application/json", roots());
            case "/api/list" -> send(exchange, 200, "application/json", list(query(exchange).getOrDefault("path", "")));
            case "/api/pick-folder" -> send(exchange, 200, "application/json", pickFolder());
            case "/api/models" -> send(exchange, 200, "application/json", models());
            case "/alive" -> send(exchange, 200, "text/plain", "prototype");
            default -> send(exchange, 404, "text/plain", "not found");
        }
    }

    // ---- the window

    /** An app window of an installed Chromium browser where one is found, else the system's default browser. */
    static List<String> openWindow(String address) throws IOException {
        String system = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>();
        String app = "--app=" + address;
        if (system.startsWith("windows")) {
            List<Path> candidates = new ArrayList<>();
            for (String base : new String[] {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")}) {
                if (base != null) {
                    candidates.add(Path.of(base, "Microsoft/Edge/Application/msedge.exe"));
                    candidates.add(Path.of(base, "Google/Chrome/Application/chrome.exe"));
                }
            }
            if (System.getenv("LOCALAPPDATA") != null) {
                candidates.add(Path.of(System.getenv("LOCALAPPDATA"), "Google/Chrome/Application/chrome.exe"));
            }
            Path browser = candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
            command = browser != null
                    ? List.of(browser.toString(), app, "--window-size=1280,880")
                    : List.of("rundll32", "url.dll,FileProtocolHandler", address);
        } else if (system.startsWith("mac")) {
            command = List.of("open", "-na", "Google Chrome", "--args", app);
        } else {
            command = List.of("sh", "-c", "chromium --app=" + address + " || google-chrome --app=" + address
                    + " || xdg-open " + address);
        }
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        return command;
    }

    // ---- the pickers

    static String roots() {
        List<String> roots = new ArrayList<>();
        for (Path root : java.nio.file.FileSystems.getDefault().getRootDirectories()) {
            roots.add(quote(root.toString()));
        }
        return "{\"roots\":" + roots + ",\"home\":" + quote(System.getProperty("user.home")) + ",\"here\":"
                + quote(Path.of("").toAbsolutePath().toString()) + "}";
    }

    /** The folder's subfolders by name; no file is opened and no file's name is sent. */
    static String list(String path) {
        Path folder = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            return "{\"exists\":false,\"path\":" + quote(folder.toString()) + "}";
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder, Files::isDirectory)) {
            for (Path entry : entries) {
                names.add(entry.getFileName().toString());
            }
        } catch (IOException | RuntimeException unreadable) {
            return "{\"exists\":true,\"unreadable\":true,\"path\":" + quote(folder.toString()) + ",\"dirs\":[]}";
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Path parent = folder.getParent();
        return "{\"exists\":true,\"path\":" + quote(folder.toString()) + ",\"parent\":"
                + (parent == null ? "null" : quote(parent.toString())) + ",\"dirs\":"
                + names.stream().map(Serve::quote).toList() + ",\"free\":" + free(folder) + "}";
    }

    static long free(Path folder) {
        try {
            return Files.getFileStore(folder).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    /** The system's own folder dialog, on Windows; waits for the operator. */
    static String pickFolder() throws Exception {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return "{\"unsupported\":true}";
        }
        String script = "Add-Type -AssemblyName System.Windows.Forms;"
                + "$o = New-Object System.Windows.Forms.Form; $o.TopMost = $true;"
                + "$d = New-Object System.Windows.Forms.FolderBrowserDialog;"
                + "$d.Description = 'Vespera: choose a folder';"
                + "if ($d.ShowDialog($o) -eq 'OK') { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $d.SelectedPath }";
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", script)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process.getOutputStream().close();
        String chosen = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return chosen.isEmpty() ? "{\"cancelled\":true}" : "{\"path\":" + quote(chosen) + "}";
    }

    /** The models Ollama serves where it answers, else an example list marked as such. */
    static String models() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(2)).build();
            String tags = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            Matcher names = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(tags);
            List<String> found = new ArrayList<>();
            while (names.find()) {
                found.add(quote(names.group(1)));
            }
            return "{\"source\":\"ollama\",\"models\":" + found + "}";
        } catch (Exception notAnswering) {
            return "{\"source\":\"example\",\"models\":[\"qwen3:8b\",\"bge-m3:latest\"]}";
        }
    }

    // ---- plumbing

    static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> fields = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int at = pair.indexOf('=');
                if (at > 0) {
                    fields.put(pair.substring(0, at), URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return fields;
    }

    static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> quoted.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return quoted.append('"').toString();
    }
}
