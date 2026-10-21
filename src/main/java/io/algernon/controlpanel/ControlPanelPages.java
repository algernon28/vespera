package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpHandler;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The control panel's pages by path, for {@code ControlPanelServer.start} (ADR-302 §10): the launch page, the
 * invocation page and Stop's confirmation, the sixteen reports, and the next act, the questions, the code sets and the
 * profile, with the paths their forms post to. The pages start the child, write files and read the sidecars; the
 * server's classes do none of these (ADR-291 §8). They link no class of the jar (ADR-285 §3).
 */
public final class ControlPanelPages {

    /** The sixteen file names of ADR-302 §3 and ADR-306 §2, in that table's order. */
    public static final List<String> REPORTS = List.of("progress.html", "progress-steps.html",
            "progress-samples.html", "progress-runs.html", "format-mix.html", "extraction-failures.html",
            "extraction-survivors.html", "confidence-distribution.html", "document-frequency.html",
            "word-count-distribution.html", "seed-corpus-comparison.html", "translations.html", "cluster-sizes.html",
            "relevance-labelling.html", "arrangement.html", "cluster-page-sizes.html");

    private ControlPanelPages() {
    }

    /**
     * The pages of §2, §3, §7 and §8, by path, for ControlPanelServer.start.
     *
     * @param runtime the java and the jar the child is started with
     * @param launchValuesFile ADR-292's file, loaded now and stored as an invocation starts
     * @param corpusRootLockFolder the folder the corpus-root locks are read in
     * @param userName the user whose own folders the control panel writes in
     */
    public static Map<String, HttpHandler> pages(ChildRuntime runtime, Path launchValuesFile,
            Path corpusRootLockFolder, String userName) {
        return pages(new ControlPanelState(runtime, launchValuesFile, corpusRootLockFolder, userName));
    }

    /** The same pages, reading and changing {@code state}, which {@code ControlPanel.serve} ends its child through. */
    static Map<String, HttpHandler> pages(ControlPanelState state) {
        Map<String, HttpHandler> pages = new HashMap<>();
        NextPages next = new NextPages(state);
        new LaunchPages(state).register(pages);
        new InvocationPages(state).register(pages);
        new ReportPages(state).register(pages);
        next.register(pages);
        new ValuePages(state, next).register(pages);
        return pages;
    }
}
