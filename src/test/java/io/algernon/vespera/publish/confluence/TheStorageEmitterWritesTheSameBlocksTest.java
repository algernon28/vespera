package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.ProseHtml;
import io.algernon.vespera.engine.deliverable.TheWritingIsRenderedFromASubsetOfMarkdownTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Confluence rendering's writing, emitted in storage format from the subset of Markdown the HTML renderer reads
 * (ADR-274 §1.3, ADR-309), held to every input {@link TheWritingIsRenderedFromASubsetOfMarkdownTest} holds the HTML
 * renderer to. Its two claims stood in that class until the emitter moved into {@code confluence} with the rendering
 * (ADR-333 §1, §2), and stand here as they were; {@link ProseHtml}'s output is read through the seam {@code
 * deliverable} makes public.
 *
 * <p>Untagged: CI's {@code -DexcludedGroups=confluence} is for the publish's integration tests alone (ADR-320 §6).
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Issue("620")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-309", url = Adr.A_CITATION_ON_A_CONFLUENCE_PAGE_IS_AN_ANCHOR_LINK, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheStorageEmitterWritesTheSameBlocksTest {

    /** Every input the HTML renderer is held to. */
    private static final List<String> EVERY_INPUT = TheWritingIsRenderedFromASubsetOfMarkdownTest.EVERY_INPUT;

    /** The tags the storage emitter writes: the HTML renderer's but its link, and ADR-309's citation. */
    private static final Set<String> THE_STORAGE_TAGS = Set.of(
            "p", "br", "h3", "h4", "h5", "ul", "ol", "li", "strong", "em", "code", "ac:link", "ri:page",
            "ac:plain-text-link-body");

    /** A tag in storage format, its name possibly prefixed. */
    private static final Pattern A_STORAGE_TAG = Pattern.compile("<\\s*/?\\s*([A-Za-z][A-Za-z0-9:-]*)([^>]*)>");

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("No input renders, in storage format, a tag the emitter does not write, and every output is well-formed XML")
    void theStorageEmitterWritesNoTagOfItsInputAndWellFormedXml() throws Exception {
        List<String> foreign = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        for (String input : EVERY_INPUT) {
            String storage = ProseStorage.of(input, number -> "A <title> & \"more\"");
            Matcher tag = A_STORAGE_TAG.matcher(storage);
            while (tag.find()) {
                if (!THE_STORAGE_TAGS.contains(tag.group(1))) {
                    foreign.add(tag.group());
                }
            }
            try {
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                        ("<body xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</body>")
                                .getBytes(StandardCharsets.UTF_8)));
            } catch (Exception refused) {
                malformed.add(input);
            }
        }

        claim(
                "over all " + EVERY_INPUT.size() + " inputs, every tag is one the storage emitter writes",
                () -> assertThat(foreign).isEmpty());
        claim(
                "and every output parses as XML once ac and ri are declared",
                () -> assertThat(malformed).isEmpty());
    }
}
