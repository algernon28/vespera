package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.stream.Collectors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * The control panel's server started with its screens' pages, as {@code ControlPanel.main} starts it (ADR-302 §11),
 * for the screen tests: on a launch values file of the test's own, under a temporary folder in place of the user's
 * temp directory, and with a jar beside it whose main class is {@link JarForScreenTests}, started with the java
 * running the tests, so that Run and Label start a child that does what the test says and never Vespera.
 *
 * <p>Closing it closes the server and kills the processes the test's JVM started since this server was started and
 * left running, and no other.
 */
final class ScreenServer implements AutoCloseable {

    /** How long a test waits for a child to end before it fails. */
    private static final Duration ENDING_BOUND = Duration.ofSeconds(30);

    /** How long a test waits between two looks at the invocation page. */
    private static final long POLL_MILLIS = 100;

    private final ControlPanelServer server;

    /** The processes the test's JVM had started, and still had, when this server was started. */
    private final Set<Long> runningBefore;

    private ScreenServer(ControlPanelServer server, Set<Long> runningBefore) {
        this.server = server;
        this.runningBefore = runningBefore;
    }

    /**
     * Writes {@code values} as the last launch values, writes the jar for the screen tests, and starts the server
     * with the screens' pages, everything under {@code folder}.
     */
    static ScreenServer start(Path folder, LaunchValues values) throws IOException {
        String userName = System.getProperty("user.name");
        Path launchValuesFile = LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), userName);
        values.store(launchValuesFile, userName, line -> {
            throw new AssertionError("the launch values were not stored: " + line);
        });
        Path jar = writeTheJar(Files.createDirectories(folder.resolve("bin")).resolve("vespera-for-screen-tests.jar"));
        ChildRuntime runtime = new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"), jar);
        Set<Long> runningBefore = ProcessHandle.current().descendants().map(ProcessHandle::pid)
                .collect(Collectors.toUnmodifiableSet());
        return new ScreenServer(ControlPanelServer.start(pages(runtime, launchValuesFile, userName)), runningBefore);
    }

    /**
     * The screens' pages, by path.
     *
     * <p>Failing first (ADR-297 §5): until the screens of ADR-302 are built the server is started with no screen, the
     * root alone registered and answering 404, so that the token is exchanged for the cookie at the root, as the
     * server exchanges it only on a path it is started with, and every path then answers 404. The build replaces this
     * body with {@code ControlPanelPages.pages(runtime, launchValuesFile, userName)} (ADR-302 §10).
     */
    private static Map<String, HttpHandler> pages(ChildRuntime runtime, Path launchValuesFile, String userName) {
        return Map.of("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
    }

    /** Launch values naming only the corpus root and the working directory, every other value empty. */
    static LaunchValues values(Path corpusRoot, Path workingDirectory) {
        return new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", "");
    }

    /** Has the next child started in {@code workingDirectory} do {@code what}, as {@link JarForScreenTests} reads it. */
    static void childWill(Path workingDirectory, String what) {
        try {
            Files.writeString(workingDirectory.resolve(JarForScreenTests.WHAT_TO_DO), what, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The element of {@code page} whose {@code id} is given; a test fails naming the page where there is none. */
    static Element byId(PanelBrowser.Page page, String id) {
        Element element = page.document().getElementById(id);
        if (element == null) {
            throw new AssertionError("the page at " + page.address().getPath() + ", answered " + page.status()
                    + ", holds no element with the id '" + id + "'");
        }
        return element;
    }

    /** The one form of {@code page} named {@code name}; a test fails naming the page where there is not one. */
    static Element form(PanelBrowser.Page page, String name) {
        Elements forms = page.document().select("form[name='" + name + "']");
        if (forms.size() != 1) {
            throw new AssertionError("the page at " + page.address().getPath() + ", answered " + page.status()
                    + ", holds " + forms.size() + " forms named '" + name + "'");
        }
        return forms.getFirst();
    }

    /** The names of a form's controls, buttons excluded, in the page's order. */
    static List<String> controlNames(Element form) {
        return form.select("input:not([type=submit]), select, textarea").eachAttr("name");
    }

    /** The names of a form's submit buttons, in the page's order. */
    static List<String> buttonNames(Element form) {
        return form.select("button:not([type=button]):not([type=reset]), input[type=submit]").eachAttr("name");
    }

    /** Whether the page refreshes itself, and after how many seconds, as {@code content} says; empty where it does not. */
    static String refresh(PanelBrowser.Page page) {
        Elements refresh = page.document().select("meta[http-equiv=refresh]");
        return refresh.isEmpty() ? "" : refresh.attr("content");
    }

    /**
     * Goes to the invocation page until it no longer shows the child running, and returns that page; a test fails
     * once {@link #ENDING_BOUND} has passed.
     */
    static PanelBrowser.Page whenTheChildHasEnded(PanelBrowser browser) throws InterruptedException {
        Instant giveUp = Instant.now().plus(ENDING_BOUND);
        PanelBrowser.Page page = browser.go("/invocation").page();
        while (byId(page, "child").attr("data-ending").equals("running")) {
            if (Instant.now().isAfter(giveUp)) {
                throw new AssertionError("the child still runs after " + ENDING_BOUND.toSeconds() + " seconds");
            }
            Thread.sleep(POLL_MILLIS);
            page = browser.go("/invocation").page();
        }
        return page;
    }

    /** A browser that has opened the printed address, pressed Run, and seen the child end on the invocation page. */
    PanelBrowser afterARunHasEnded() throws InterruptedException {
        PanelBrowser browser = browser().press("launch", "run", Map.of());
        whenTheChildHasEnded(browser);
        return browser;
    }

    /** A browser that has opened the address the control panel printed. */
    PanelBrowser browser() {
        return PanelBrowser.opening(server.firstAddress());
    }

    /** The server, for a request a test writes by hand. */
    ControlPanelServer server() {
        return server;
    }

    @Override
    public void close() {
        server.close();
        ProcessHandle.current().descendants()
                .filter(process -> !runningBefore.contains(process.pid()))
                .forEach(ProcessHandle::destroyForcibly);
    }

    private static Path writeTheJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, JarForScreenTests.class.getName());
        String entry = JarForScreenTests.class.getName().replace('.', '/') + ".class";
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file, manifest);
                InputStream bytes = JarForScreenTests.class.getClassLoader().getResourceAsStream(entry)) {
            if (bytes == null) {
                throw new IOException("no compiled " + entry + " on the test class path");
            }
            out.putNextEntry(new JarEntry(entry));
            bytes.transferTo(out);
            out.closeEntry();
        }
        return jar;
    }
}
