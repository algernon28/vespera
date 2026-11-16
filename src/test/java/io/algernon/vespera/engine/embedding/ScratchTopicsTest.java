package io.algernon.vespera.engine.embedding;

import java.util.*;
import org.junit.jupiter.api.Test;

class ScratchTopicsTest {
    @Test
    void probe() {
        Map<String, SeedVectors> m = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) m.put("on-sluices-" + i + ".txt", new SeedVectors("s" + i, List.of(new float[] {1,0,0,0,0,0,0,0})));
        for (int i = 1; i <= 2; i++) m.put("on-pumps-" + i + ".txt", new SeedVectors("p" + i, List.of(new float[] {0.1f,1,0,0,0,0,0,0})));
        System.out.println("TOPICS42 " + TopicInference.over(m, 10, 1.0).size());
        Map<String, SeedVectors> n = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) n.put("on-sluices-" + i + ".txt", new SeedVectors("s" + i, List.of(new float[] {1,0,0,0,0,0,0,0})));
        for (int i = 1; i <= 3; i++) n.put("on-pumps-" + i + ".txt", new SeedVectors("p" + i, List.of(new float[] {0.1f,1,0,0,0,0,0,0})));
        System.out.println("TOPICS33 " + TopicInference.over(n, 10, 1.0).size());
    }
}
