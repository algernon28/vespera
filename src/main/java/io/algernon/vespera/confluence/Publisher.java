package io.algernon.vespera.confluence;

import io.algernon.vespera.confluence.ConfluenceApi.Property;
import io.algernon.vespera.confluence.ConfluenceApi.Reply;
import io.algernon.vespera.confluence.ConfluenceApi.SiteFile;
import io.algernon.vespera.confluence.ConfluenceApi.SitePage;
import io.algernon.vespera.confluence.PageList.Page;
import io.algernon.vespera.confluence.PageList.PageFile;
import io.algernon.vespera.ledger.ReplacingMove;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/**
 * Publishes the Confluence rendering of one generation run to the operator's site (ADR-308, ADR-316 §4).
 *
 * <p><b>Before anything is sent</b> the five values are read (ADR-308 §3), the page list is read and every body is
 * checked against its digest, and every file it lists is checked to stand at its size. A refusal there sends nothing.
 *
 * <p><b>Then, in order:</b> the space and the parent are found; the pages of the run are found by their label and
 * their key; every page the list lists that does not stand is created, empty, parents first; then each body whose
 * digest the page's property does not already hold is sent, with the page's version plus one; then each page's files
 * are sent, a file standing at its size with its digest in its comment skipped. A {@code 409} on a body is answered by
 * reading the page's version again and sending once more; a second stops the publish. A copy or a picture's file the
 * site refuses as too large ({@code 413}) is skipped, counted and listed in {@value #NOT_CARRIED_FILE_NAME}; any other
 * refusal stops the publish (ADR-313 §3). Nothing is deleted, and a page of the run whose key the list no longer holds
 * is counted on the closing line (ADR-308 §8).
 */
@Component
public class Publisher {

    /** What a publish writes into the working directory when the site refused a file as too large (ADR-313 §3). */
    public static final String NOT_CARRIED_FILE_NAME = "confluence-not-carried.csv";

    /** The templates a publish refused for a missing value writes into the working directory (ADR-308 §3). */
    static final List<String> TEMPLATES = List.of("confluence-env.sh", "confluence-env.ps1");

    /** The page list's folder at the tree's root (ADR-312). */
    public static final String RENDERING_FOLDER = "confluence";

    /** The page list's name in the rendering's folder (ADR-316 §1). */
    public static final String PAGE_LIST_FILE_NAME = PageList.FILE_NAME;

    /** What every page of a run is labelled, before the run's marker (ADR-316 §4). */
    static final String LABEL_PREFIX = "vespera-";

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

    /** The application's: the process's environment, the JDK's HTTP client, and waits that wait. */
    @Autowired
    public Publisher(
            @Value("${" + ConfluenceSite.BASE_URL_PROPERTY + ":}") String baseUrl,
            @Value("${" + ConfluenceSite.SPACE_KEY_PROPERTY + ":}") String spaceKey,
            @Value("${" + ConfluenceSite.PARENT_ID_PROPERTY + ":}") String parentId,
            @Value("${" + ConfluenceSite.PARENT_TITLE_PROPERTY + ":}") String parentTitle) {
        this(System::getenv, new JdkClientHttpRequestFactory(), Thread::sleep, Clock.systemUTC(), baseUrl, spaceKey,
                parentId, parentTitle);
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
     * Publishes the rendering in {@code confluence}, drawn for {@code generationRun}, telling {@code out} a line per
     * page it went through; {@value #NOT_CARRIED_FILE_NAME} and any template are written in {@code workingDirectory}.
     */
    public Outcome publish(Path confluence, String generationRun, Path workingDirectory, Consumer<String> out) {
        ConfluenceSite.Read read = ConfluenceSite.of(environment, baseUrl, spaceKey, parentId, parentTitle);
        if (read instanceof ConfluenceSite.Read.Refused(String line)) {
            return new Outcome(false, line + templatesWrittenIn(workingDirectory));
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
        Run run = new Run(site, list, confluence, new ConfluenceApi(site, requests, pause, clock), out);
        try {
            run.publish();
        } catch (TheSiteRefusedThePublishException | RestClientException | UncheckedIOException stopped) {
            return new Outcome(false, "vespera publish stopped: " + reasonOf(stopped) + ". " + run.doneSoFar()
                    + " Publish again to go on from there.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(false, "vespera publish stopped: it was interrupted. " + run.doneSoFar());
        }
        try {
            run.writeNotCarried(workingDirectory);
        } catch (IOException unwritten) {
            return new Outcome(false, run.closingLine() + "; but " + NOT_CARRIED_FILE_NAME + " could not be written in"
                    + " " + workingDirectory + ": " + unwritten.getClass().getSimpleName());
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
    private record NotCarried(String key, String name, long size, String kind) {}

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

        Run(ConfluenceSite site, PageList list, Path confluence, ConfluenceApi api, Consumer<String> out) {
            this.site = site;
            this.list = list;
            this.confluence = confluence;
            this.api = api;
            this.out = out;
        }

        void publish() throws InterruptedException {
            String spaceId = api.spaceId(site.spaceKey()).orElseThrow(() -> new TheSiteRefusedThePublishException(
                    "the site holds no space with the key " + site.spaceKey() + " that this account can read"));
            String parent = parent(spaceId);
            String label = LABEL_PREFIX + list.marker();
            Map<String, Standing> found = ofTheRun(label);
            Set<String> listed = new HashSet<>();
            for (Page page : list.pages()) {
                listed.add(page.key());
                Standing at = found.get(page.key());
                if (at == null) {
                    at = titled(spaceId, page, label);
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
            }
            for (Page page : list.pages()) {
                sendBody(page);
            }
            for (Page page : list.pages()) {
                sendFiles(page);
            }
            leftAsTheyStand = (int) found.keySet().stream().filter(key -> !listed.contains(key)).count();
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
                        + " VESPERA_CONFLUENCE_PARENT_ID");
            }
            return titled.getFirst();
        }

        /** The pages of the run that stand, found by their label and keyed by their property. */
        private Map<String, Standing> ofTheRun(String label) throws InterruptedException {
            Map<String, Standing> found = new LinkedHashMap<>();
            for (String id : api.pagesLabelled(site.spaceKey(), label)) {
                Optional<Property> property = api.property(id);
                if (property.isEmpty() || property.get().key().isEmpty() || found.containsKey(property.get().key())) {
                    continue;
                }
                Optional<SitePage> page = api.page(id);
                page.ifPresent(standing -> found.put(property.get().key(), new Standing(standing, property.get())));
            }
            return found;
        }

        /**
         * The page titled as {@code page} is, where one stands and its property holds the page's key: a page the
         * label's search did not yet find. One titled so that holds another key, or none, stops the publish, as a
         * title is unique within the space and the page is not this publish's to take.
         */
        private Standing titled(String spaceId, Page page, String label) throws InterruptedException {
            Optional<SitePage> titled = api.pageTitled(spaceId, page.title());
            if (titled.isEmpty()) {
                return null;
            }
            Optional<Property> property = api.property(titled.get().id());
            if (property.isEmpty() || !property.get().key().equals(page.key())) {
                throw new TheSiteRefusedThePublishException("a page titled " + page.title() + " stands in the space"
                        + " and is not the page " + page.key() + " of this rendering, so it was left as it stands");
            }
            api.label(titled.get().id(), label);
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

        private void sendFiles(Page page) throws InterruptedException {
            List<PageFile> files = PageList.filesOf(page);
            int sent = 0;
            int kept = 0;
            int refused = 0;
            if (!files.isEmpty()) {
                String id = standing.get(page.key()).page().id();
                Map<String, SiteFile> onThePage = api.files(id);
                for (PageFile file : files) {
                    SiteFile there = onThePage.get(file.name());
                    if (there != null && there.size() == file.size() && there.comment().contains(file.sha256())) {
                        filesUnchanged++;
                        kept++;
                        continue;
                    }
                    Reply reply = api.sendFile(id, Optional.ofNullable(there).map(SiteFile::id),
                            confluence.resolve(file.path()), file.name(), file.sha256());
                    if (reply.status() == TOO_LARGE && file.skippedWhenTooLarge()) {
                        notCarried.add(new NotCarried(page.key(), file.name(), file.size(), file.kind()));
                        refused++;
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
                }
            }
            out.accept(page.title() + ": " + done.get(page.key()) + (files.isEmpty() ? "" : "; files: " + sent
                    + " sent, " + kept + " unchanged" + (refused == 0 ? "" : ", " + refused
                    + " refused by the site as too large")));
        }

        void writeNotCarried(Path workingDirectory) throws IOException {
            if (notCarried.isEmpty()) {
                return;
            }
            StringBuilder csv = new StringBuilder("key,name,size\r\n");
            for (NotCarried file : notCarried) {
                csv.append(field(file.key())).append(',').append(field(file.name())).append(',').append(file.size())
                        .append("\r\n");
            }
            Path target = workingDirectory.resolve(NOT_CARRIED_FILE_NAME);
            Path part = workingDirectory.resolve(NOT_CARRIED_FILE_NAME + ".part");
            Files.writeString(part, csv, StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, target);
        }

        private static String field(String value) {
            String quote = "\"";
            return quote + value.replace(quote, quote + quote) + quote;
        }

        String doneSoFar() {
            return "It had created " + created + (created == 1 ? " page" : " pages") + ", updated " + updated
                    + " and sent " + (filesAdded + filesReplaced) + (filesAdded + filesReplaced == 1 ? " file" : " files")
                    + ".";
        }

        String closingLine() {
            int pages = created + updated + unchanged;
            int files = filesAdded + filesReplaced + filesUnchanged;
            StringBuilder line = new StringBuilder("vespera publish sent the rendering of generation run ")
                    .append(list.marker()).append(" to ").append(site.site().getHost()).append(", space ")
                    .append(site.spaceKey()).append(": ").append(pages).append(pages == 1 ? " page (" : " pages (")
                    .append(created).append(" created, ").append(updated).append(" updated, ").append(unchanged)
                    .append(" unchanged) and ").append(files).append(files == 1 ? " file (" : " files (")
                    .append(filesAdded).append(" added, ").append(filesReplaced).append(" replaced, ")
                    .append(filesUnchanged).append(" unchanged)");
            if (leftAsTheyStand > 0) {
                line.append("; ").append(leftAsTheyStand).append(leftAsTheyStand == 1
                        ? " page of this run the page list no longer holds was left as it stands"
                        : " pages of this run the page list no longer holds were left as they stand");
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
                    .append(bytes).append(bytes == 1 ? " byte" : " bytes").append(", listed in ")
                    .append(NOT_CARRIED_FILE_NAME);
        }
    }
}
