package io.wulfcodes.messaging.chat.util;

import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistentHashRingTest {

    private static final int USERS = 30_000;
    private final List<String> users = IntStream.range(0, USERS)
            .mapToObj(i -> new UlidGenerator().nextString()).toList();

    @Test
    void loadIsSpreadEvenlyAcrossNodes() {
        ConsistentHashRing ring = new ConsistentHashRing(List.of("chat-1", "chat-2", "chat-3"), 128);

        Map<String, Integer> counts = new HashMap<>();
        users.forEach(user -> counts.merge(ring.ownerOf(user), 1, Integer::sum));

        // perfect = 1/3 each; with 128 virtual nodes every node stays within +-20% of that
        counts.values().forEach(count -> assertThat(count).isBetween((int) (USERS / 3 * 0.8), (int) (USERS / 3 * 1.2)));
    }

    @Test
    void addingANodeMovesOnlyAboutOneQuarterOfUsers() {
        ConsistentHashRing three = new ConsistentHashRing(List.of("chat-1", "chat-2", "chat-3"), 128);
        ConsistentHashRing four = new ConsistentHashRing(List.of("chat-1", "chat-2", "chat-3", "chat-4"), 128);

        long moved = users.stream().filter(u -> !three.ownerOf(u).equals(four.ownerOf(u))).count();
        long movedToOtherThanNewNode = users.stream()
                .filter(u -> !three.ownerOf(u).equals(four.ownerOf(u)))
                .filter(u -> !four.ownerOf(u).equals("chat-4"))
                .count();

        // ideal is 1/4 of users (only those the new node takes over); hash % N would move ~3/4
        assertThat((double) moved / USERS).isBetween(0.18, 0.32);
        assertThat(movedToOtherThanNewNode).isZero();   // nobody shuffles between the old nodes
    }

    @Test
    void ringIsDeterministicAndIndependentOfNodeOrder() {
        ConsistentHashRing a = new ConsistentHashRing(List.of("chat-1", "chat-2", "chat-3"), 128);
        ConsistentHashRing b = new ConsistentHashRing(List.of("chat-3", "chat-1", "chat-2"), 128);

        users.stream().limit(1_000).forEach(user -> assertThat(a.ownerOf(user)).isEqualTo(b.ownerOf(user)));
    }

    @Test
    void emptyRingHasNoOwner() {
        assertThat(new ConsistentHashRing(List.of(), 128).ownerOf("anyone")).isNull();
    }
}
