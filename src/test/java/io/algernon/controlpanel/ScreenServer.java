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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * The control panel's server started with its screens' pages, as {@code ControlPanel.main} starts it (ADR-302 §11),
 * for the screen tests: on a launch values file of the test's own, under a temporary folder in place of the user's
 * temp directory, and with a jar whose main class is {@link JarForScreenTests}, started with the java
 * running the tests, so that Run and Label start a child that does what the test says and never Vespera.
 *
 * <p>Closing it closes the server and kills the processes the test's JVM started since this server was started and
 * left running, and no other, and waits for each to end: on Windows a folder a live process uses cannot be deleted.
 * A process is told from those running at the start by its pid and its start instant together, since Windows soon
 * gives the pid of a process that ended to a new one (#661).
 */
final class ScreenServer implements AutoCloseable {

    /** What the name of a Browse button begins with, before the field it chooses a folder for (ADR-331 §5). */
    static final String BROWSE = "browse-";

    /** How long a test waits for a child to end before it fails. */
    private static final Duration ENDING_BOUND = Duration.ofSeconds(30);

    /** How long a test waits between two looks at the invocation page. */
    private static final long POLL_MILLIS = 100;

    /** The jar for the screen tests, once written. */
    private static Path jar;

    private final ControlPanelServer server;

    /** The processes the test's JVM had started, and still had, when this server was started. */
    private final Set<Started> runningBefore;

    /** The folder the corpus-root locks are read in, under the test's own folder in place of the user's home. */
    private final Path corpusRootLockFolder;

    private ScreenServer(ControlPanelServer server, Set<Started> runningBefore, Path corpusRootLockFolder) {
        this.server = server;
        this.runningBefore = runningBefore;
        this.corpusRootLockFolder = corpusRootLockFolder;
    }

    /**
     * Writes {@code values} as the last launch values, writes the jar for the screen tests, and starts the server
     * with the screens' pages, reading corpus-root locks in a folder of the test's own, everything under
     * {@code folder}.
     */
    static ScreenServer start(Path folder, LaunchValues values) throws IOException {
        String userName = System.getProperty("user.name");
        Path launchValuesFile = storedIn(folder, values, userName);
        ChildRuntime runtime = runtime();
        Set<Started> runningBefore = snapshot(ProcessHandle.current().descendants());
        Path corpusRootLockFolder = Files.createDirectories(folder.resolve("root-locks"));
        return new ScreenServer(ControlPanelServer.start(pages(runtime, launchValuesFile, corpusRootLockFolder,
                userName)), runningBefore, corpusRootLockFolder);
    }

    /** Writes {@code values} as the last launch values of {@code userName}, under {@code folder}; returns their file. */
    static Path storedIn(Path folder, LaunchValues values, String userName) throws IOException {
        Path launchValuesFile = LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), userName);
        values.store(launchValuesFile, userName, line -> {
            throw new AssertionError("the launch values were not stored: " + line);
        });
        return launchValuesFile;
    }

    /**
     * The jar for the screen tests with the java running the tests. The jar is written once for the test JVM, in a
     * folder of its own outside every test's folder: on Windows a jar a child has just run can stay in use for a moment
     * after the child ends, and a test's folder holding it could not then be deleted.
     */
    static ChildRuntime runtime() throws IOException {
        return new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"), theJar());
    }

    private static synchronized Path theJar() throws IOException {
        if (jar == null) {
            Path folder = Files.createTempDirectory("vespera-screen-tests");
            folder.toFile().deleteOnExit();
            jar = writeTheJar(folder.resolve("vespera-for-screen-tests.jar"));
            jar.toFile().deleteOnExit();
        }
        return jar;
    }

    /** The folder the control panel reads corpus-root locks in, as the jar's {@code CorpusRootLock} would take them. */
    Path corpusRootLockFolder() {
        return corpusRootLockFolder;
    }

    /** The screens' pages, by path, as {@code ControlPanel.main} starts the server with them (ADR-302 §10). */
    private static Map<String, HttpHandler> pages(ChildRuntime runtime, Path launchValuesFile,
            Path corpusRootLockFolder, String userName) {
        return ControlPanelPages.pages(runtime, launchValuesFile, corpusRootLockFolder, userName);
    }

    /**
     * The paths of the control panel's own pages, as {@code ControlPanel.main} starts the server with them, sorted:
     * every path but a report's, which is the jar's page (ADR-302 §3), and the script's and {@code /alive}, which are
     * no pages (ADR-325 §4.2, §4.3). The pages are built for their paths alone, on files under {@code folder}.
     */
    static List<String> pathsOfItsOwnPages(Path folder) throws IOException {
        Path elsewhere = Files.createDirectories(folder.resolve("only-for-the-paths"));
        return ControlPanelPages.pages(new ChildRuntime(elsewhere.resolve("java"), elsewhere.resolve("vespera.jar")),
                        elsewhere.resolve("launch-values.properties"), elsewhere.resolve("root-locks"),
                        System.getProperty("user.name"))
                .keySet().stream()
                .filter(path -> !path.startsWith("/report/") && !path.equals(TabPaths.SCRIPT)
                        && !path.equals(TabPaths.ALIVE))
                .sorted()
                .toList();
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

    /**
     * The names of a form's submit buttons that act on what it holds, in the page's order: every one but a Browse
     * button, which only chooses a folder for a field (ADR-331 §5) and which {@link #browseNames} reads.
     */
    static List<String> buttonNames(Element form) {
        return form.select("button:not([type=button]):not([type=reset]), input[type=submit]").eachAttr("name").stream()
                .filter(name -> !name.startsWith(BROWSE))
                .toList();
    }

    /** The names of a form's Browse buttons, in the page's order: {@link #BROWSE} and the field each chooses for. */
    static List<String> browseNames(Element form) {
        return form.select("button[name^=" + BROWSE + "]").eachAttr("name");
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
        List<ProcessHandle> started = startedSince(runningBefore, ProcessHandle.current().descendants());
        started.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle process : started) {
            try {
                process.onExit().get(ENDING_BOUND.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("process " + process.pid() + " did not end once killed", e);
            }
        }
    }

    /**
     * A process as one of those running when a server was started: its pid and the instant it started. A process given
     * that pid since then started at another instant, so it is not this one.
     */
    record Started(long pid, Instant start) {

        /** {@code process} by its pid and start instant; empty where the system gives no start instant of it. */
        static Optional<Started> of(ProcessHandle process) {
            return process.info().startInstant().map(start -> new Started(process.pid(), start));
        }
    }

    /** The processes of {@code processes} by their pids and start instants, leaving out each that gives no start. */
    static Set<Started> snapshot(Stream<ProcessHandle> processes) {
        return processes.map(Started::of).flatMap(Optional::stream).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The processes of {@code descendants} that are not in {@code runningBefore}: one whose pid is there under another
     * start instant was started since, and so is one that gives no start instant, which cannot be told from it.
     */
    static List<ProcessHandle> startedSince(Set<Started> runningBefore, Stream<ProcessHandle> descendants) {
        return descendants.filter(process -> Started.of(process).map(started -> !runningBefore.contains(started))
                .orElse(true)).toList();
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
