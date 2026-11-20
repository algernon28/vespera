package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The retrieval records are composed as the tree's topics are gone through and handed to each plugin reading them, in
 * the order of their names, each record to each before the next is composed (ADR-434 §4, §5): a topic's overview where
 * one is written, then each group in the topic's order, its synthesis doc where one is written and then its members'
 * passages in membership order, an unwritten group's passages too. A plugin that answers no to {@code
 * takesMemberPassages} is handed none.
 *
 * <p>Two scripted plugins read the records of {@link ThreeTopics}, whose topic {@code alpha} has an overview written:
 * {@code aaa} takes passages, {@code zzz} does not.
 */
@Epic("The deliverable")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class RetrievalRecordsAreHandedInTheTreesOrderTest {

    /** The records of {@link ThreeTopics}, in the tree's order, as {@link RetrievalReadings#labelOf} writes them. */
    private static final List<String> EVERY_RECORD = everyRecord();

    private static List<String> everyRecord() {
        List<String> all = new ArrayList<>();
        all.add("synthesis-doc Fire doors & their inspection");
        for (String path : List.of("reports/doors a.docx", "reports/doors [final].docx", "reports/doors b.docx")) {
            passages(all, path);
        }
        for (String path : List.of("reports/dampers.pdf", "reports/dampers \"old\".pdf")) {
            passages(all, path);
        }
        all.add("topic-overview alpha");
        for (String path : List.of("reports/stairs, north.pdf", "reports/stairs, south.pdf")) {
            passages(all, path);
        }
        all.add("synthesis-doc Risers");
        passages(all, "notes/risers.txt");
        for (String path : List.of("reports/sprinklers-1.pdf", "reports/sprinklers-2.pdf", "notes/hose reels.txt")) {
            passages(all, path);
        }
        return List.copyOf(all);
    }

    private static void passages(List<String> into, String path) {
        into.add("member-passage " + path + "#0");
        into.add("member-passage " + path + "#1");
    }

    @Test
    @Story("The records are handed in the tree's order")
    @DisplayName("Each record goes to every plugin before the next is composed, in the order the tree is drawn, and a plugin not taking passages is handed none")
    void eachRecordIsHandedToEachPluginInTheTreesOrder(@TempDir Path base) {
        List<String> heard = new ArrayList<>();
        List<String> asked = new ArrayList<>();
        RetrievalReadings.Recording takes = new RetrievalReadings.Recording("aaa", true, heard);
        RetrievalReadings.Recording leaves = new RetrievalReadings.Recording("zzz", false, heard);

        Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ThreeTopics.provenance(base.resolve("archive").toString()),
                RetrievalReadings.source(),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                List.of(takes, leaves),
                Outputs.read("tree, aaa, zzz"),
                RetrievalReadings.inputs(asked),
                DeliverableProgress.NONE);

        List<String> toTakes = heard.stream().filter(line -> line.startsWith("aaa:")).map(line -> line.substring(4)).toList();
        List<String> toLeaves = heard.stream().filter(line -> line.startsWith("zzz:")).map(line -> line.substring(4)).toList();

        claim("the plugin taking passages is handed every record in the tree's order: the first topic's written group"
                        + " and the passages of its three members, then those of the unwritten group's two, the second"
                        + " topic's overview, its unwritten group's passages, its written group and its member, and the"
                        + " third topic's passages; no synthesis doc of an unwritten group and none of an unwritten"
                        + " overview",
                () -> assertThat(toTakes).containsExactlyElementsOf(EVERY_RECORD));
        claim("the plugin that takes none is handed the two synthesis docs and the one overview, in the same order, and"
                        + " no passage",
                () -> assertThat(toLeaves).containsExactly(
                        "synthesis-doc Fire doors & their inspection", "topic-overview alpha", "synthesis-doc Risers"));
        claim("each record that goes to both goes to the first by name and then to the second, before the next is handed",
                () -> {
                    for (int at = 0; at < heard.size(); at++) {
                        if (heard.get(at).startsWith("zzz:")) {
                            assertThat(heard.get(at - 1)).isEqualTo("aaa:" + heard.get(at).substring(4));
                        }
                    }
                });
        claim("no writing was embedded twice, and each member's passages were asked of the inputs once",
                () -> {
                    assertThat(asked).filteredOn(line -> line.startsWith("passagesOf")).hasSize(11).doesNotHaveDuplicates();
                    assertThat(asked).filteredOn(line -> line.startsWith("vectorOf")).hasSize(3).doesNotHaveDuplicates();
                });
    }

    @Test
    @Story("A record is placed")
    @DisplayName("A record carries its text, its vector, the identity, and where it stands: topic, group, citations, language and run")
    void aRecordCarriesItsPlace(@TempDir Path base) {
        List<String> heard = new ArrayList<>();
        RetrievalReadings.Recording takes = new RetrievalReadings.Recording("aaa", true, heard);

        Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ThreeTopics.provenance(base.resolve("archive").toString()),
                RetrievalReadings.source(),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                List.of(takes),
                Outputs.read("tree, aaa"),
                RetrievalReadings.inputs(new ArrayList<>()),
                DeliverableProgress.NONE);

        RetrievalRecord doc = takes.taken().get(0);
        RetrievalRecord citedPassage = takes.taken().get(1);
        RetrievalRecord uncitedPassage = takes.taken().get(3);
        RetrievalRecord overview = takes.taken().stream()
                .filter(record -> record.kind() == RetrievalRecord.Kind.TOPIC_OVERVIEW)
                .findFirst()
                .orElseThrow();

        claim("the synthesis doc holds its prose as stored, its vector, the identity, its group's title and number and"
                        + " the ordinals it cites once each in ascending order",
                () -> {
                    assertThat(doc.kind()).isEqualTo(RetrievalRecord.Kind.SYNTHESIS_DOC);
                    assertThat(doc.text()).isEqualTo(
                            "The doors were inspected twice [1]. The second inspection found a fault [2][1].");
                    assertThat(doc.vector()).containsExactly(doc.text().length(), 1f);
                    assertThat(doc.embedderIdentity()).isEqualTo(RetrievalReadings.EMBEDDER);
                    assertThat(doc.id()).isEqualTo(RetrievalRecordId.ofSynthesisDoc(ListedTree.slotOf(ThreeTopics.BETA, 0)));
                    RetrievalRecord.Placement place = doc.placement();
                    assertThat(place.generationRun()).isEqualTo(ThreeTopics.provenance("x").runId());
                    assertThat(place.language()).isEqualTo(DeliverableLanguage.ENGLISH);
                    assertThat(place.topicOrder()).isEqualTo(1);
                    assertThat(place.subtopic()).isEmpty();
                    assertThat(place.cluster()).contains("Fire doors & their inspection");
                    assertThat(place.clusterNumber()).isEqualTo(OptionalInt.of(1));
                    assertThat(place.citations()).containsExactly(1, 2);
                    assertThat(place.document()).isEmpty();
                });
        claim("a passage of a member the group's call sent carries that member's ordinal in the call, and a member it"
                        + " did not send none; the first of the group's three is occurrence 10, the second of the call",
                () -> {
                    assertThat(citedPassage.placement().citations()).containsExactly(2);
                    assertThat(citedPassage.placement().document()).contains("reports/doors a.docx");
                    assertThat(citedPassage.placement().passage()).isEqualTo(OptionalInt.of(0));
                    assertThat(citedPassage.text()).isEqualTo("passage 0 of key10");
                    assertThat(uncitedPassage.placement().citations()).isEmpty();
                    assertThat(uncitedPassage.placement().document()).contains("reports/doors [final].docx");
                });
        claim("the overview holds no group and cites the ordinals its writing cites once each",
                () -> {
                    assertThat(overview.text()).isEqualTo(RetrievalReadings.ALPHA_OVERVIEW);
                    assertThat(overview.placement().cluster()).isEmpty();
                    assertThat(overview.placement().citations()).containsExactly(1, 2);
                    assertThat(overview.placement().topic()).isEqualTo("alpha");
                    assertThat(overview.placement().topicOrder()).isEqualTo(2);
                });
    }

    @Test
    @Story("Nothing is composed unless it is read")
    @DisplayName("Where no plugin reads the records, none is composed and the inputs are asked nothing")
    void nothingIsComposedWhereNoneReadsRecords(@TempDir Path base) throws IOException {
        List<String> asked = new ArrayList<>();
        Plugin none = new ScriptedPlugin("alpha", new ArrayList<>());

        Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ThreeTopics.provenance(base.resolve("archive").toString()),
                RetrievalReadings.source(),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                List.of(none),
                Outputs.read("tree, alpha"),
                RetrievalReadings.inputs(asked),
                DeliverableProgress.NONE);

        claim("the inputs were asked for no identity, no vector and no passage", () -> assertThat(asked).isEmpty());
    }
}
