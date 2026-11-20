package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A retrieval record's id is its content key (ADR-434 §6): the lowercase hexadecimal SHA-256 of the UTF-8 bytes of
 * its kind and its key's parts, joined by U+001F, an absent subtopic written {@code -1}. A synthesis doc is keyed by
 * its cluster, an overview by its topic, and a passage by its cluster, its member's content hash and its ordinal. The
 * id takes no generation run and no deliverable language, so the same place has the same id in every drawing.
 */
@Epic("The deliverable")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class ARetrievalRecordIsKeyedByItsPlaceTest {

    /** What the key's parts are joined by: the unit separator, which no seed path or content hash holds. */
    private static final String SEPARATOR = "\u001F";

    /** A topic's key: a seed's path relative to the seed folder, and the index of its first passage's chunk. */
    private static final TopicKey TOPIC = new TopicKey("pumps/manual.pdf", 3);

    /** The cluster numbered 4 within the topic's subtopic numbered 1. */
    private static final ClusterSlot IN_A_SUBTOPIC = new ClusterSlot(TOPIC, OptionalInt.of(1), 4);

    /** The cluster numbered 4 within the same topic, which has no subtopic. */
    private static final ClusterSlot IN_NO_SUBTOPIC = new ClusterSlot(TOPIC, OptionalInt.empty(), 4);

    /** A member's content hash, as stage 2 keys its text. */
    private static final String MEMBER = "4f1c2a7b9e0d3c6f8a5b2e1d0c9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a19";

    /** The passage's ordinal among the member's chunks. */
    private static final int PASSAGE = 7;

    @Test
    @Story("A record's id")
    @DisplayName("Each kind of record is identified by the digest of its kind and of the place it stands in")
    void eachKindsIdIsTheDigestOfItsKey() {
        claim("a group's writing in a subtopic: the digest of synthesis-doc, the topic's path and index, the"
                        + " subtopic 1 and the group 4",
                () -> assertThat(RetrievalRecordId.ofSynthesisDoc(IN_A_SUBTOPIC))
                        .isEqualTo(sha256("synthesis-doc", "pumps/manual.pdf", "3", "1", "4")));
        claim("a group's writing in a topic with no subtopic: the subtopic written -1",
                () -> assertThat(RetrievalRecordId.ofSynthesisDoc(IN_NO_SUBTOPIC))
                        .isEqualTo(sha256("synthesis-doc", "pumps/manual.pdf", "3", "-1", "4")));
        claim("a topic's overview: the digest of topic-overview and the topic's path and index",
                () -> assertThat(RetrievalRecordId.ofTopicOverview(TOPIC))
                        .isEqualTo(sha256("topic-overview", "pumps/manual.pdf", "3")));
        claim("a passage: the digest of member-passage, the group's place, the member's content hash and the"
                        + " passage's ordinal 7",
                () -> assertThat(RetrievalRecordId.ofMemberPassage(IN_A_SUBTOPIC, MEMBER, PASSAGE))
                        .isEqualTo(sha256("member-passage", "pumps/manual.pdf", "3", "1", "4", MEMBER, "7")));
    }

    @Test
    @Story("A record's id")
    @DisplayName("The same place gives the same id each time it is asked, and every other place another")
    void theSameKeyGivesTheSameIdAndAnotherKeyAnother() {
        claim("a passage's id asked twice is the same",
                () -> assertThat(RetrievalRecordId.ofMemberPassage(IN_A_SUBTOPIC, MEMBER, PASSAGE))
                        .isEqualTo(RetrievalRecordId.ofMemberPassage(
                                new ClusterSlot(new TopicKey("pumps/manual.pdf", 3), OptionalInt.of(1), 4),
                                MEMBER,
                                PASSAGE)));
        List<String> ids = List.of(
                RetrievalRecordId.ofMemberPassage(IN_A_SUBTOPIC, MEMBER, PASSAGE),
                RetrievalRecordId.ofMemberPassage(IN_NO_SUBTOPIC, MEMBER, PASSAGE),
                RetrievalRecordId.ofMemberPassage(new ClusterSlot(TOPIC, OptionalInt.of(1), 5), MEMBER, PASSAGE),
                RetrievalRecordId.ofMemberPassage(IN_A_SUBTOPIC, MEMBER.replace('4', '5'), PASSAGE),
                RetrievalRecordId.ofMemberPassage(IN_A_SUBTOPIC, MEMBER, PASSAGE + 1),
                RetrievalRecordId.ofSynthesisDoc(IN_A_SUBTOPIC),
                RetrievalRecordId.ofTopicOverview(TOPIC));
        claim("another subtopic, another group, another member and another passage each give an id of its own,"
                        + " and none is the writing's or the overview's",
                () -> assertThat(ids).doesNotHaveDuplicates());
    }

    /** The lowercase hexadecimal SHA-256 of the UTF-8 bytes of {@code parts} joined by {@link #SEPARATOR}. */
    private static String sha256(String... parts) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join(SEPARATOR, parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }
}
