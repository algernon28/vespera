package io.algernon.vespera.publish.confluence;

import io.algernon.vespera.publish.confluence.ConfluenceApi.Property;
import io.algernon.vespera.publish.confluence.ConfluenceApi.Reply;
import io.algernon.vespera.publish.confluence.ConfluenceApi.SiteFile;
import io.algernon.vespera.publish.confluence.ConfluenceApi.SitePage;
import io.algernon.vespera.publish.confluence.PageList.Page;
import io.algernon.vespera.publish.confluence.PageList.PageFile;
import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

/**
 * Publishes the Confluence rendering of one generation run to the operator's site (ADR-308, ADR-316 §4).
 *
 * <p><b>Before anything is sent</b> the five values are read (ADR-308 §3), the page list is read and every body is
 * checked against its digest, and every file it lists is checked to stand at its size. A refusal there sends nothing.
 *
 * <p><b>Then</b> the totals are printed, the space and the parent are found, and the pages of the tree are found by
 * their label and their key. Where none is keyed {@code root}, the parent is asked whether it is a folder or a page and
 * its children are listed, any answer but a success stopping the publish, and the one tree an earlier publish made
 * under it is found by its root page's property (ADR-328 §6); the tree's root page standing under another parent
 * stops the publish (ADR-328 §4). Every page of the list whose title is not that of the page standing for its key, a
 * page of the earlier tree to be taken included, is looked up by its title, by reads alone: another page so titled stops the
 * publish before anything is written (ADR-320 §3, ADR-328 §7). <b>Then</b> the earlier tree, if any, is taken: its
 * pages are labelled, keyed in this tree's form and freed of the earlier label, the root page last. <b>Then, a page at
 * a time</b>, in the list's order, parents first (ADR-320 §1): the page is found or created empty; its files are sent,
 * a file standing at its size with its digest in its comment skipped; its body is sent where its digest or title
 * changed, with the page's version plus one; its closing line is printed; the next page is taken. A line says as each
 * page starts and after each file (ADR-320 §4). A {@code 409} on a body is answered by reading the page's version again
 * and sending once more; a second stops the publish, saying which page it stopped at (ADR-320 §5). A copy or a
 * picture's file the site refuses as too large ({@code 413}) is skipped, said and counted, and listed in no file; any
 * other refusal stops the publish (ADR-378 §10). A page of the tree the list no
 * longer holds is moved to the trash after every page is sent, deepest first, and only where nothing stands under it
 * (ADR-328 §5).
 */
@Component
public class Publisher {

    /** The list of refused files an earlier version wrote, which a publish removes and none writes (ADR-378 §10). */
    private static final String THE_EARLIER_LIST = "confluence-not-carried.csv";

    /** The templates a publish refused for a missing value writes into the working directory (ADR-308 §3). */
    static final List<String> TEMPLATES = List.of("confluence-env.sh", "confluence-env.ps1");

    /** The page list's folder at the tree's root (ADR-312). */
    public static final String RENDERING_FOLDER = "confluence";

    /** The page list's name in the rendering's folder (ADR-316 §1). */
    public static final String PAGE_LIST_FILE_NAME = PageList.FILE_NAME;

    /** What every page of ADR-328's tree is labelled, an earlier tree's label a publish takes off (ADR-378 §2, §5). */
    static final String LABEL = "vespera";

    /**
     * What a page of a language's tree is labelled begins with, {@code vespera-<code>} (ADR-378 §2); what an earlier
     * publish labelled a run's pages with, before the run's marker (ADR-316 §4).
     */
    static final String LABEL_PREFIX = "vespera-";

    /** How many characters of a run's id an earlier publish's label carries as its marker (ADR-328 §6). */
    private static final int EARLIER_MARKER_LENGTH = 12;

    /** A place as ADR-328 §3 writes it. */
    private static final String PLACE = "[1-9][0-9]*(\\.[1-9][0-9]*){0,2}";

    /** A key of ADR-328's tree (ADR-328 §3): its place alone. */
    private static final java.util.regex.Pattern ADR_328_KEY = java.util.regex.Pattern.compile("root|" + PLACE);

    /**
     * The key an ADR-310 tree's publish gave a page: its run, sixty-four hexadecimal characters exactly, and its place
     * (ADR-316 §3, ADR-378 §2).
     */
    private static final java.util.regex.Pattern EARLIER_KEY =
            java.util.regex.Pattern.compile("([0-9a-f]{64}):(root|[0-9]+(\\.[0-9]+){0,2})");

    /** The first {@code h1} element of a body in storage format (ADR-378 §5). */
    private static final java.util.regex.Pattern FIRST_HEADING =
            java.util.regex.Pattern.compile("(?is)<h1(?:\\s[^>]*)?>(.*?)</h1\\s*>");

    /** The status a site refuses a file with as too large. */
    private static final int TOO_LARGE = 413;

    /** The status a site answers a body sent over a newer version with. */
    private static final int CONFLICT = 409;

    private final Function<String, String> environment;
    private final ClientHttpRequestFactory requests;
    private final ConfluenceApi.Pause pause;
    private final Clock clock;
    private final String baseUrl;
    private final String spaceKey;
    private final String parentId;
    private final String parentTitle;
    private final ConfluenceValues.InTheProfile profile;

    /**
     * The application's: the process's environment, the JDK's HTTP client, waits that wait, the four properties and
     * the values the working directory's profile names (ADR-359 §5).
     */
    @Autowired
    public Publisher(
            @Value("${" + ConfluenceSite.BASE_URL_PROPERTY + ":}") String baseUrl,
            @Value("${" + ConfluenceSite.SPACE_KEY_PROPERTY + ":}") String spaceKey,
            @Value("${" + ConfluenceSite.FOLDER_ID_PROPERTY + ":}") String parentId,
            @Value("${" + ConfluenceSite.FOLDER_TITLE_PROPERTY + ":}") String parentTitle,
            ConfluenceValues.InTheProfile profile) {
        this(System::getenv, new JdkClientHttpRequestFactory(), Thread::sleep, Clock.systemUTC(), baseUrl, spaceKey,
                parentId, parentTitle, profile);
    }

    /**
     * The four properties alone, a profile naming none of the five values: for a publisher that is another's subclass,
     * as a test's, which sends nothing.
     */
    protected Publisher(String baseUrl, String spaceKey, String parentId, String parentTitle) {
        this(baseUrl, spaceKey, parentId, parentTitle, workingDirectory -> ConfluenceValues.NONE);
    }

    /** The seam the tests need: an environment, a request factory pointed at a stub, and a pause. */
    Publisher(
            Function<String, String> environment,
            ClientHttpRequestFactory requests,
            ConfluenceApi.Pause pause,
            Clock clock,
            String baseUrl,
            String spaceKey,
            String parentId,
            String parentTitle) {
        this(environment, requests, pause, clock, baseUrl, spaceKey, parentId, parentTitle,
                workingDirectory -> ConfluenceValues.NONE);
    }

    /** The seam the tests need, with the values the working directory's profile names. */
    Publisher(
            Function<String, String> environment,
            ClientHttpRequestFactory requests,
            ConfluenceApi.Pause pause,
            Clock clock,
            String baseUrl,
            String spaceKey,
            String parentId,
            String parentTitle,
            ConfluenceValues.InTheProfile profile) {
        this.profile = profile;
        this.environment = environment;
        this.requests = requests;
        this.pause = pause;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.spaceKey = spaceKey;
        this.parentId = parentId;
        this.parentTitle = parentTitle;
    }

    /** How a publish ended: whether it went to its end, and its closing line. */
    public record Outcome(boolean published, String closingLine) {}

    /**
     * Publishes the rendering in {@code confluence}, drawn for {@code generationRun}, telling {@code out} the totals,
     * a line as each page starts, a line per file and the page's closing line (ADR-320 §4). A template is written in
     * {@code workingDirectory}, and the list an earlier version wrote there is removed (ADR-378 §10); no file of
     * refused files is written.
     */
    public Outcome publish(Path confluence, String generationRun, Path workingDirectory, Consumer<String> out) {
        ConfluenceSite.Read read = ConfluenceSite.of(environment, baseUrl, spaceKey, parentId, parentTitle,
                profile.of(workingDirectory));
        if (read instanceof ConfluenceSite.Read.Refused(String line)) {
            boolean fromTheControlPanel =
                    ConfluenceSite.THE_CONTROL_PANEL.equals(environment.apply(ConfluenceSite.STARTED_BY_VARIABLE));
            return new Outcome(false, fromTheControlPanel ? line : line + templatesWrittenIn(workingDirectory));
        }
        ConfluenceSite site = ((ConfluenceSite.Read.Given) read).site();
        PageList.Read listed = PageList.in(confluence, generationRun);
        if (listed instanceof PageList.Read.Refused(String line)) {
            return new Outcome(false, line);
        }
        PageList list = ((PageList.Read.Listed) listed).list();
        Optional<String> unlike = unlikeItsList(confluence, list);
        if (unlike.isPresent()) {
            return new Outcome(false, "vespera publish sent nothing: " + unlike.get() + ". Run vespera run to draw the"
                    + " deliverable again, and publish again.");
        }
        Run run = new Run(site, list, confluence, workingDirectory, new ConfluenceApi(site, requests, pause, clock), out);
        try {
            run.publish();
        } catch (TheSiteRefusedThePublishException | RestClientException | UncheckedIOException stopped) {
            return new Outcome(false, "vespera publish stopped: " + reasonOf(stopped) + ". " + run.doneSoFar()
                    + " Publish again to go on from there.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(false, "vespera publish stopped: it was interrupted. " + run.doneSoFar());
        }
        return new Outcome(true, run.closingLine());
    }

    private static String reasonOf(RuntimeException stopped) {
        if (stopped instanceof TheSiteRefusedThePublishException) {
            return stopped.getMessage();
        }
        if (stopped instanceof UncheckedIOException unread) {
            return "a file of the rendering could not be read (" + unread.getCause().getClass().getSimpleName() + ")";
        }
        return "the site could not be reached (" + stopped.getClass().getSimpleName() + ")";
    }

    /** Why the rendering's files are not the ones its list names, if they are not. */
    private static Optional<String> unlikeItsList(Path confluence, PageList list) {
        try {
            for (Page page : list.pages()) {
                Path body = confluence.resolve(page.body());
                if (!Files.isRegularFile(body) || !sha256(body).equals(page.bodySha256())) {
                    return Optional.of("the body of the page " + page.key() + " is not the one the page list names");
                }
                for (PageFile file : PageList.filesOf(page)) {
                    Path path = confluence.resolve(file.path());
                    if (!Files.isRegularFile(path) || Files.size(path) != file.size()) {
                        return Optional.of("the file " + file.name() + " of the page " + page.key() + " does not stand"
                                + " at the size the page list names");
                    }
                }
            }
        } catch (IOException unread) {
            return Optional.of("a file of the rendering could not be read (" + unread.getClass().getSimpleName() + ")");
        }
        return Optional.empty();
    }

    /** Writes each template the working directory lacks, and says which stand there. */
    private static String templatesWrittenIn(Path workingDirectory) {
        List<String> written = new ArrayList<>();
        for (String template : TEMPLATES) {
            Path target = workingDirectory.resolve(template);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream shipped = Publisher.class.getResourceAsStream(template)) {
                if (shipped != null) {
                    Files.write(target, shipped.readAllBytes());
                    written.add(template);
                }
            } catch (IOException unwritten) {
                // The line names the templates that were written; one that was not is not named.
            }
        }
        return written.isEmpty() ? "" : " " + String.join(" and ", written) + (written.size() == 1 ? " was" : " were")
                + " written in " + workingDirectory + ", naming each value and holding none.";
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("this JDK offers no SHA-256", absent);
        }
    }

    /** A page the publish found or made: as the site holds it, and its property. */
    private record Standing(SitePage page, Property property) {}

    /** A file the site refused as too large: its page's key, its name and its size (ADR-313 §3). */
    private record NotCarried(long size, String kind) {}

    /** One publish, from the space to the closing line. */
    private static final class Run {

        private final ConfluenceSite site;
        private final PageList list;
        private final Path confluence;
        private final ConfluenceApi api;
        private final Consumer<String> out;
        private final Map<String, Standing> standing = new LinkedHashMap<>();
        private final Map<String, String> done = new HashMap<>();
        private final List<NotCarried> notCarried = new ArrayList<>();
        private int created;
        private int updated;
        private int unchanged;
        private int filesAdded;
        private int filesReplaced;
        private int filesUnchanged;
        private int leftAsTheyStand;
        private int removed;
        private int removalNumber;
        private int removalTotal;
        private int fileNumber;
        private int fileTotal;
        private int pageNumber;
        private String pageTitle;

        private final DeliverableLanguage language;
        private final String treeRootTitle;
        private final String rootKey;
        private final String label;
        private final java.util.regex.Pattern treeKey;

        private final Path workingDirectory;

        Run(ConfluenceSite site, PageList list, Path confluence, Path workingDirectory, ConfluenceApi api,
                Consumer<String> out) {
            this.workingDirectory = workingDirectory;
            this.language = list.drawnIn();
            this.treeRootTitle = ConfluenceTitles.root(language);
            this.rootKey = language.code() + ":root";
            this.label = LABEL_PREFIX + language.code();
            this.treeKey = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(language.code() + ":")
                    + "(root|" + PLACE + ")");
            this.site = site;
            this.list = list;
            this.confluence = confluence;
            this.api = api;
            this.out = out;
        }

        void publish() throws InterruptedException {
            List<Page> pages = list.pages();
            long bytes = 0;
            int fileCount = 0;
            for (Page page : pages) {
                for (PageFile file : PageList.filesOf(page)) {
                    fileCount++;
                    bytes += file.size();
                }
            }
            this.fileTotal = fileCount;
            out.accept("vespera publish is sending the rendering of generation run " + list.marker() + " to "
                    + site.site().getHost() + ", space " + site.spaceKey() + ", tree " + treeRootTitle + ": " + pages.size()
                    + (pages.size() == 1 ? " page" : " pages") + " and " + fileCount
                    + (fileCount == 1 ? " file" : " files") + " holding " + bytes + (bytes == 1 ? " byte" : " bytes"));
            removeTheListAnEarlierVersionWrote();
            String spaceId = api.spaceId(site.spaceKey()).orElseThrow(() -> new TheSiteRefusedThePublishException(
                    "the site holds no space with the key " + site.spaceKey() + " that this account can read"));
            String parent = parent(spaceId);
            Map<String, Standing> found = ofTheTree();
            Standing root = found.get(rootKey);
            Earlier toTake = Earlier.NONE;
            if (root == null) {
                // The tree's own root page may have lost its label: found by its title, it is the root page, and no
                // earlier tree is looked for; a page so titled that is not is a stop (ADR-378 §5, ADR-320 §3).
                Optional<SitePage> own = api.pageTitled(spaceId, treeRootTitle);
                if (own.isPresent()) {
                    Optional<Property> property = api.property(own.get().id());
                    if (property.isEmpty() || !property.get().key().equals(rootKey)) {
                        throw new TheSiteRefusedThePublishException("a page titled " + treeRootTitle + " stands in the"
                                + " space and is not the page " + rootKey + " of this rendering, so it was left as it"
                                + " stands");
                    }
                    root = new Standing(own.get(), property.get());
                } else {
                    toTake = earlierTree(parent, found);
                }
            }
            if (root != null && !parent.equals(root.page().parentId())) {
                throw new TheSiteRefusedThePublishException("the tree " + treeRootTitle + " it updates stands under "
                        + root.page().parentId() + ", not under the parent named. Name that parent, or move the page "
                        + treeRootTitle + " under the one named");
            }
            Set<String> listed = new HashSet<>();
            Map<String, Standing> byTitle = new HashMap<>();
            for (Page page : pages) {
                listed.add(page.key());
                SitePage standingFor = found.containsKey(page.key()) ? found.get(page.key()).page() : null;
                for (Taken one : toTake.taken()) {
                    if (one.newKey().equals(page.key())) {
                        standingFor = one.at().page();
                    }
                }
                if (standingFor == null || !standingFor.title().equals(page.title())) {
                    Standing at = titled(spaceId, page, standingFor);
                    if (at != null) {
                        byTitle.put(page.key(), at);
                    }
                }
            }
            for (String left : toTake.left()) {
                out.accept(left);
            }
            take(toTake, found);
            for (Page page : pages) {
                pageNumber++;
                pageTitle = page.title();
                List<PageFile> files = PageList.filesOf(page);
                long pageBytes = files.stream().mapToLong(PageFile::size).sum();
                out.accept("page " + pageNumber + " of " + pages.size() + ": " + page.title() + ", "
                        + (files.isEmpty() ? "no files" : files.size() + (files.size() == 1 ? " file" : " files")
                        + " holding " + pageBytes + (pageBytes == 1 ? " byte" : " bytes")));
                Standing at = found.get(page.key());
                if (at == null) {
                    at = byTitle.get(page.key());
                    if (at != null) {
                        api.label(at.page().id(), label);
                    }
                }
                if (at == null) {
                    String under = page.parent() == null ? parent : standing.get(page.parent()).page().id();
                    SitePage made = api.createPage(spaceId, under, page.title());
                    api.label(made.id(), label);
                    api.createProperty(made.id(), page.key(), "");
                    Property property = api.property(made.id()).orElseThrow(() -> new TheSiteRefusedThePublishException(
                            "the site kept no property on the page " + page.key() + " it created"));
                    at = new Standing(made, property);
                    created++;
                    done.put(page.key(), "created");
                }
                standing.put(page.key(), at);
                found.put(page.key(), at);
                String filesSaid = sendFiles(page, files);
                sendBody(page);
                out.accept(page.title() + ": " + done.get(page.key()) + filesSaid);
            }
            removeWhatTheListNoLongerHolds(found, listed);
        }

        /** An earlier publish's page to be taken: as the site holds it, its new key and the label it carries. */
        private record Taken(Standing at, String newKey, String earlierLabel) {}

        /**
         * The pages of the language's tree that stand: labelled {@code vespera-<code>}, keyed {@code <code>:<place>}; of
         * two alike, the lower id (ADR-378 §2).
         */
        private Map<String, Standing> ofTheTree() throws InterruptedException {
            Map<String, Standing> found = new LinkedHashMap<>();
            for (String id : api.pagesLabelled(site.spaceKey(), label)) {
                Optional<Property> property = api.property(id);
                if (property.isEmpty() || !treeKey.matcher(property.get().key()).matches()) {
                    continue;
                }
                Optional<SitePage> page = api.page(id);
                if (page.isEmpty()) {
                    continue;
                }
                Standing candidate = new Standing(page.get(), property.get());
                Standing held = found.get(property.get().key());
                if (held == null || lower(candidate.page().id(), held.page().id())) {
                    found.put(property.get().key(), candidate);
                }
            }
            return found;
        }

        private static boolean lower(String id, String than) {
            try {
                return Long.parseLong(id) < Long.parseLong(than);
            } catch (NumberFormatException notNumbers) {
                return id.compareTo(than) < 0;
            }
        }

        /**
         * Where no page is keyed {@code root}: the root page of a tree an earlier publish made under the parent, if
         * there is one, found by its property whatever labels it carries, and its pages, root last, and those a
         * stopped taking half took (ADR-328 §6). Two such trees stop the publish.
         */
        private Earlier earlierTree(String parent, Map<String, Standing> found) throws InterruptedException {
            List<ConfluenceApi.Child> children = api.children(parent, api.isFolder(parent)).orElseThrow(() ->
                    new TheSiteRefusedThePublishException("the site answered HTTP 404 while listing the children of the"
                            + " parent " + parent + ", which it holds"));
            List<EarlierRoot> roots = new ArrayList<>();
            for (ConfluenceApi.Child child : children) {
                if (!child.type().isEmpty() && !child.type().equals("page")) {
                    continue;
                }
                Optional<Property> property = api.property(child.id());
                if (property.isPresent() && (property.get().key().equals("root")
                        || EARLIER_KEY.matcher(property.get().key()).matches()
                        && property.get().key().endsWith(":root"))) {
                    Optional<SitePage> page = api.page(child.id());
                    if (page.isPresent()) {
                        roots.add(new EarlierRoot(new Standing(page.get(), property.get()),
                                headingsLanguage(page.get())));
                    }
                }
            }
            if (roots.isEmpty()) {
                return Earlier.NONE;
            }
            for (EarlierRoot root : roots) {
                if (root.drawnIn().isEmpty()) {
                    String title = root.at().page().title();
                    throw new TheSiteRefusedThePublishException("the parent holds the tree " + title + " an earlier"
                            + " publish made, and its root page's heading is neither "
                            + FixedWords.theDeliverable(DeliverableLanguage.ENGLISH).strip() + " nor "
                            + FixedWords.theDeliverable(DeliverableLanguage.ITALIAN).strip() + ", so the language it was"
                            + " drawn in cannot be told. Put the heading back, or move the page " + title
                            + " from under the parent named");
                }
            }
            List<EarlierRoot> ofThisLanguage = roots.stream().filter(root -> root.drawnIn().get() == language).toList();
            List<String> left = new ArrayList<>();
            for (EarlierRoot root : roots) {
                if (root.drawnIn().get() != language) {
                    left.add("the tree " + root.at().page().title() + " an earlier publish made was drawn in "
                            + root.drawnIn().get().englishName() + ", and is left as it stands for a publish in "
                            + root.drawnIn().get().englishName() + " to take");
                }
            }
            if (ofThisLanguage.size() > 1) {
                throw new TheSiteRefusedThePublishException("the parent holds " + ofThisLanguage.size()
                        + " trees an earlier publish made in " + language.englishName() + ", "
                        + String.join(", ", ofThisLanguage.stream().map(r -> r.at().page().title()).toList())
                        + ", and one can be taken as the tree " + treeRootTitle + ". Remove all but the one to keep");
            }
            if (ofThisLanguage.isEmpty()) {
                return new Earlier(List.of(), List.of(), "", left);
            }
            Standing earlierRoot = ofThisLanguage.getFirst().at();
            String earlierKey = earlierRoot.property().key();
            String run = earlierKey.equals("root") ? "" : earlierKey.substring(0, earlierKey.indexOf(':'));
            String earlierLabel = run.isEmpty()
                    ? LABEL
                    : LABEL_PREFIX + run.substring(0, Math.min(EARLIER_MARKER_LENGTH, run.length()));
            // The candidates by the key they would hold, each the page of the lower id of its key (ADR-378 §5).
            Map<String, Taken> candidates = new LinkedHashMap<>();
            Map<String, Boolean> halfTaken = new HashMap<>();
            Map<String, Boolean> underTheRoot = new HashMap<>();
            for (String id : api.pagesLabelled(site.spaceKey(), earlierLabel)) {
                Optional<Property> property = api.property(id);
                if (property.isEmpty() || id.equals(earlierRoot.page().id())) {
                    continue;
                }
                // A page a stopped taking set already: the tree's page of its key, whose earlier label goes.
                boolean set = treeKey.matcher(property.get().key()).matches();
                String place;
                if (set) {
                    place = property.get().key().substring(language.code().length() + 1);
                    if (place.equals("root")) {
                        continue;
                    }
                } else if (run.isEmpty()) {
                    if (!ADR_328_KEY.matcher(property.get().key()).matches() || property.get().key().equals("root")) {
                        continue;
                    }
                    place = property.get().key();
                } else {
                    java.util.regex.Matcher key = EARLIER_KEY.matcher(property.get().key());
                    if (!key.matches() || !key.group(1).equals(run) || key.group(2).equals("root")) {
                        continue;
                    }
                    place = unpadded(key.group(2));
                }
                Optional<SitePage> page = api.page(id);
                if (page.isEmpty() || !standsUnder(page.get(), earlierRoot.page().id(), parent, underTheRoot)) {
                    continue;
                }
                String newKey = language.code() + ":" + place;
                Taken held = candidates.get(newKey);
                if (held == null || lower(id, held.at().page().id())) {
                    candidates.put(newKey, new Taken(new Standing(page.get(), property.get()), newKey, earlierLabel));
                    halfTaken.put(newKey, set);
                }
            }
            List<Taken> taken = new ArrayList<>();
            List<String> unlabelled = new ArrayList<>();
            for (Map.Entry<String, Taken> candidate : candidates.entrySet()) {
                if (halfTaken.get(candidate.getKey())) {
                    unlabelled.add(candidate.getValue().at().page().id());
                } else if (!found.containsKey(candidate.getKey())) {
                    taken.add(candidate.getValue());
                }
            }
            taken.add(new Taken(earlierRoot, rootKey, earlierLabel));
            return new Earlier(taken, unlabelled, earlierLabel, left);
        }

        /**
         * Whether the page stands under the earlier tree's root page at any depth: its {@code parentId}, then that
         * page's, read by {@code GET pages/{id}}, reaches the root page. Reaching the parent named, a {@code 404} or a
         * folder (which that read answers {@code 404}) ends the walk with no (ADR-378 §5). {@code known} remembers
         * each id's answer.
         */
        private boolean standsUnder(SitePage page, String rootId, String named, Map<String, Boolean> known)
                throws InterruptedException {
            List<String> walked = new ArrayList<>();
            String above = page.parentId();
            boolean under = false;
            while (above != null && !walked.contains(above)) {
                if (above.equals(rootId)) {
                    under = true;
                    break;
                }
                if (above.equals(named)) {
                    break;
                }
                Boolean remembered = known.get(above);
                if (remembered != null) {
                    under = remembered;
                    break;
                }
                walked.add(above);
                Optional<SitePage> next = api.page(above);
                if (next.isEmpty()) {
                    break;
                }
                above = next.get().parentId();
            }
            for (String id : walked) {
                known.put(id, under);
            }
            return under;
        }

        /** An earlier tree's root page as the site holds it, and the language its heading names, if it names one. */
        private record EarlierRoot(Standing at, Optional<DeliverableLanguage> drawnIn) {}

        /**
         * The language the root page's heading names: the text of the body's first {@code h1}, its markup taken out,
         * its character references read and its whitespace folded, being a language's heading words or beginning with
         * them and a space (ADR-378 §5). The body is read by the page's id, the site's answer standing for nothing but
         * {@code 2xx}.
         */
        private Optional<DeliverableLanguage> headingsLanguage(SitePage root) throws InterruptedException {
            java.util.regex.Matcher heading = FIRST_HEADING.matcher(api.storageBody(root.id()));
            if (!heading.find()) {
                return Optional.empty();
            }
            String text = HtmlUtils.htmlUnescape(heading.group(1).replaceAll("<[^>]*>", ""))
                    .replaceAll("[\\s\\u00a0]+", " ").strip();
            for (DeliverableLanguage candidate : DeliverableLanguage.values()) {
                String words = FixedWords.theDeliverable(candidate).strip();
                if (text.equals(words) || text.startsWith(words + " ")) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }

        /**
         * What an earlier publish's tree needs: its pages to take, root last, those a stopped taking half took, to
         * unlabel, and the lines saying which earlier trees of another language are left.
         */
        private record Earlier(List<Taken> taken, List<String> unlabelled, String label, List<String> left) {
            static final Earlier NONE = new Earlier(List.of(), List.of(), "", List.of());
        }

        /** A place read without its padding: {@code 01.10} is {@code 1.10}. */
        private static String unpadded(String place) {
            if (place.equals("root")) {
                return place;
            }
            List<String> numbers = new ArrayList<>();
            for (String number : place.split("\\.")) {
                numbers.add(Integer.toString(Integer.parseInt(number)));
            }
            return String.join(".", numbers);
        }

        /** Takes the earlier tree's pages as this tree's, the root page last, and says so (ADR-328 §6). */
        private void take(Earlier earlier, Map<String, Standing> found) throws InterruptedException {
            List<Taken> toTake = earlier.taken();
            if (toTake.isEmpty()) {
                return;
            }
            String rootTitle = toTake.getLast().at().page().title();
            out.accept("the tree " + rootTitle + " an earlier publish made is taken as the tree " + treeRootTitle
                    + ": its " + (toTake.size() == 1 ? "1 page is" : toTake.size() + " pages are")
                    + " retitled and updated in place");
            for (String id : earlier.unlabelled()) {
                takeLabelOff(id, earlier.label(), id);
            }
            for (Taken one : toTake) {
                String id = one.at().page().id();
                api.label(id, label);
                boolean root = one.newKey().equals(rootKey);
                if (root) {
                    takeLabelOff(id, one.earlierLabel(), one.at().page().title());
                }
                api.updateProperty(id, one.at().property(), one.newKey(), one.at().property().bodySha256());
                if (!root) {
                    takeLabelOff(id, one.earlierLabel(), one.at().page().title());
                }
                Property now = new Property(one.at().property().id(), one.at().property().version() + 1,
                        one.newKey(), one.at().property().bodySha256());
                found.put(one.newKey(), new Standing(one.at().page(), now));
            }
        }

        /** Takes the earlier label off the page; one gone already is no stop, any other refusal is (ADR-328 §6). */
        private void takeLabelOff(String id, String earlierLabel, String named) throws InterruptedException {
            Reply off = api.removeLabel(id, earlierLabel);
            if (!off.ok() && off.status() != 404) {
                throw ConfluenceApi.refused("taking the label " + earlierLabel + " off the page " + named, off);
            }
        }

        /** Moves to the trash, deepest first, the tree's pages the list no longer holds (ADR-328 §5). */
        private void removeWhatTheListNoLongerHolds(Map<String, Standing> found, Set<String> listed)
                throws InterruptedException {
            List<String> keys = new ArrayList<>(found.keySet().stream()
                    .filter(key -> !listed.contains(key) && !key.equals(rootKey)).toList());
            if (keys.isEmpty()) {
                return;
            }
            int codeLength = language.code().length() + 1;
            keys.sort((one, other) -> {
                String[] a = one.substring(codeLength).split("\\.");
                String[] b = other.substring(codeLength).split("\\.");
                if (a.length != b.length) {
                    return Integer.compare(b.length, a.length);
                }
                for (int at = 0; at < a.length; at++) {
                    int c = Integer.compare(Integer.parseInt(a[at]), Integer.parseInt(b[at]));
                    if (c != 0) {
                        return c;
                    }
                }
                return 0;
            });
            removalTotal = keys.size();
            out.accept(removalTotal == 1
                    ? "1 page of the tree the rendering no longer has is moved to the trash"
                    : removalTotal + " pages of the tree the rendering no longer has are moved to the trash");
            for (String key : keys) {
                removalNumber++;
                SitePage page = found.get(key).page();
                pageTitle = page.title();
                String said = "removal " + removalNumber + " of " + removalTotal + ": " + page.title() + ", ";
                Optional<List<ConfluenceApi.Child>> children = api.children(page.id(), false);
                if (children.isEmpty()) {
                    removed++;
                    out.accept(said + "already gone");
                    continue;
                }
                if (!children.get().isEmpty()) {
                    leftAsTheyStand++;
                    out.accept(said + "left as it stands: something stands under it, which its removal could take"
                            + " with it");
                    continue;
                }
                Reply reply = api.trashPage(page.id());
                if (reply.ok()) {
                    removed++;
                    out.accept(said + "moved to the trash");
                } else if (reply.status() == 404) {
                    removed++;
                    out.accept(said + "already gone");
                } else {
                    leftAsTheyStand++;
                    out.accept(said + "left as it stands: the site answered " + reply.said());
                }
            }
        }

        /** The parent's id: the one named, checked to lie in the space, or the one page or folder titled so. */
        private String parent(String spaceId) throws InterruptedException {
            if (site.parentId() != null) {
                String space = api.spaceOfPageOrFolder(site.parentId()).orElseThrow(() ->
                        new TheSiteRefusedThePublishException("the site holds no page or folder " + site.parentId()
                                + " that this account can read, so there is no parent to publish under"));
                if (!space.equals(spaceId)) {
                    throw new TheSiteRefusedThePublishException("the parent " + site.parentId() + " is not in the space "
                            + site.spaceKey());
                }
                return site.parentId();
            }
            List<String> titled = api.pagesOrFoldersTitled(site.spaceKey(), site.parentTitle());
            if (titled.size() != 1) {
                throw new TheSiteRefusedThePublishException((titled.isEmpty() ? "no page or folder" : titled.size()
                        + " pages or folders") + " of the space " + site.spaceKey() + " is titled "
                        + site.parentTitle() + ", and the parent must be exactly one; name it by"
                        + " VESPERA_CONFLUENCE_FOLDER_ID");
            }
            return titled.getFirst();
        }

        /**
         * The page titled as {@code page} is, looked up for a page whose title differs from the title of the page
         * that stands for its key, {@code standingFor}, or for which none stands (ADR-328 §7). For a page to be
         * created, one so titled whose property holds the page's key is a page the label's search did not yet find,
         * labelled when its turn comes (ADR-320 §3). Any other page so titled, whose property holds another key or
         * none, or which is not the page standing for the key, stops the publish, as a title is unique within the
         * space and the page is not this publish's to take.
         */
        private Standing titled(String spaceId, Page page, SitePage standingFor) throws InterruptedException {
            Optional<SitePage> titled = api.pageTitled(spaceId, page.title());
            if (titled.isEmpty() || (standingFor != null && titled.get().id().equals(standingFor.id()))) {
                return null;
            }
            Optional<Property> property = standingFor != null ? Optional.<Property>empty()
                    : api.property(titled.get().id());
            if (property.isEmpty() || !property.get().key().equals(page.key())) {
                throw new TheSiteRefusedThePublishException("a page titled " + page.title() + " stands in the space"
                        + " and is not the page " + page.key() + " of this rendering, so it was left as it stands");
            }
            return new Standing(titled.get(), property.get());
        }

        private void sendBody(Page page) throws InterruptedException {
            Standing at = standing.get(page.key());
            if (page.bodySha256().equals(at.property().bodySha256()) && page.title().equals(at.page().title())) {
                if (!done.containsKey(page.key())) {
                    unchanged++;
                    done.put(page.key(), "unchanged");
                }
                return;
            }
            String body;
            try {
                body = Files.readString(confluence.resolve(page.body()), StandardCharsets.UTF_8);
            } catch (IOException unread) {
                throw new UncheckedIOException(unread);
            }
            String id = at.page().id();
            Reply reply = api.updatePage(id, page.title(), body, at.page().version() + 1);
            if (reply.status() == CONFLICT) {
                SitePage now = api.page(id).orElseThrow(() -> new TheSiteRefusedThePublishException(
                        "the page " + page.key() + " was deleted while it was sent"));
                reply = api.updatePage(id, page.title(), body, now.version() + 1);
                if (reply.status() == CONFLICT) {
                    throw new TheSiteRefusedThePublishException("the page " + page.key() + " was changed on the site"
                            + " while it was sent, twice");
                }
            }
            if (!reply.ok()) {
                throw ConfluenceApi.refused("sending the body of the page " + page.key(), reply);
            }
            api.updateProperty(id, at.property(), page.key(), page.bodySha256());
            if (!done.containsKey(page.key())) {
                updated++;
                done.put(page.key(), "updated");
            }
        }

        /** Sends the page's files, a line after each; the clause of the page's closing line that tells of them. */
        private String sendFiles(Page page, List<PageFile> files) throws InterruptedException {
            int sent = 0;
            int kept = 0;
            int refused = 0;
            if (!files.isEmpty()) {
                String id = standing.get(page.key()).page().id();
                Map<String, SiteFile> onThePage = api.files(id);
                for (PageFile file : files) {
                    fileNumber++;
                    String said = "file " + fileNumber + " of " + fileTotal + ": " + file.name() + ", " + file.size()
                            + (file.size() == 1 ? " byte, " : " bytes, ");
                    SiteFile there = onThePage.get(file.name());
                    if (there != null && there.size() == file.size() && there.comment().contains(file.sha256())) {
                        filesUnchanged++;
                        kept++;
                        out.accept(said + "unchanged");
                        continue;
                    }
                    Reply reply = api.sendFile(id, Optional.ofNullable(there).map(SiteFile::id),
                            confluence.resolve(file.path()), file.name(), file.sha256());
                    if (reply.status() == TOO_LARGE && file.skippedWhenTooLarge()) {
                        notCarried.add(new NotCarried(file.size(), file.kind()));
                        refused++;
                        out.accept(said + "refused by the site as too large");
                        continue;
                    }
                    if (!reply.ok()) {
                        throw ConfluenceApi.refused("sending the file " + file.name() + " of the page " + page.key(),
                                reply);
                    }
                    if (there == null) {
                        filesAdded++;
                    } else {
                        filesReplaced++;
                    }
                    sent++;
                    out.accept(said + "sent");
                }
            }
            return files.isEmpty() ? "" : "; files: " + sent + " sent, " + kept + " unchanged"
                    + (refused == 0 ? "" : ", " + refused + " refused by the site as too large");
        }

        /**
         * Removes the list an earlier version wrote in the working directory, deleting what stands at its name and
         * never reading it, and says so; a deletion that fails is said and stops nothing (ADR-378 §10).
         */
        private void removeTheListAnEarlierVersionWrote() {
            String said = THE_EARLIER_LIST + ", which an earlier version of vespera publish wrote, ";
            try {
                Files.delete(workingDirectory.resolve(THE_EARLIER_LIST));
                out.accept(said + "is removed from the working directory");
            } catch (NoSuchFileException nothingThere) {
                // Where nothing stands there, nothing is said.
            } catch (IOException | RuntimeException unremoved) {
                out.accept(said + "could not be removed from the working directory ("
                        + unremoved.getClass().getSimpleName() + "), and is left as it stands");
            }
        }

        String doneSoFar() {
            if (removalNumber > 0) {
                return "It stopped at removal " + removalNumber + " of " + removalTotal + ", " + pageTitle
                        + ". It had created " + created + (created == 1 ? " page" : " pages") + ", updated " + updated
                        + " and sent " + (filesAdded + filesReplaced)
                        + (filesAdded + filesReplaced == 1 ? " file" : " files") + ".";
            }
            return (pageNumber == 0 ? "" : "It stopped at page " + pageNumber + " of " + list.pages().size() + ", "
                    + pageTitle + ". ") + "It had created " + created + (created == 1 ? " page" : " pages")
                    + ", updated " + updated + " and sent " + (filesAdded + filesReplaced)
                    + (filesAdded + filesReplaced == 1 ? " file" : " files")
                    + ".";
        }

        String closingLine() {
            int pages = created + updated + unchanged;
            int files = filesAdded + filesReplaced + filesUnchanged;
            StringBuilder line = new StringBuilder("vespera publish sent the rendering of generation run ")
                    .append(list.marker()).append(" to ").append(site.site().getHost()).append(", space ")
                    .append(site.spaceKey()).append(", tree ").append(treeRootTitle).append(": ").append(pages)
                    .append(pages == 1 ? " page (" : " pages (")
                    .append(created).append(" created, ").append(updated).append(" updated, ").append(unchanged)
                    .append(" unchanged) and ").append(files).append(files == 1 ? " file (" : " files (")
                    .append(filesAdded).append(" added, ").append(filesReplaced).append(" replaced, ")
                    .append(filesUnchanged).append(" unchanged)");
            if (removed > 0) {
                line.append("; ").append(removed).append(removed == 1
                        ? " page the rendering no longer has was removed"
                        : " pages the rendering no longer has were removed");
            }
            if (leftAsTheyStand > 0) {
                line.append("; ").append(leftAsTheyStand).append(leftAsTheyStand == 1
                        ? " page the rendering no longer has was left as it stands"
                        : " pages the rendering no longer has were left as they stand");
            }
            clause(line, PageFile.COPY, "copy was", "copies were");
            clause(line, PageFile.PICTURE, "picture's file was", "pictures' files were");
            return line.toString();
        }

        private void clause(StringBuilder line, String kind, String one, String many) {
            List<NotCarried> refused = notCarried.stream().filter(file -> file.kind().equals(kind)).toList();
            if (refused.isEmpty()) {
                return;
            }
            long bytes = refused.stream().mapToLong(NotCarried::size).sum();
            line.append("; ").append(refused.size()).append(' ').append(refused.size() == 1 ? one : many)
                    .append(" refused by the site as too large and ").append(refused.size() == 1 ? "is" : "are")
                    .append(" not on ").append(refused.size() == 1 ? "its page" : "their pages").append(", ")
                    .append(bytes).append(bytes == 1 ? " byte" : " bytes");
        }
    }
}
