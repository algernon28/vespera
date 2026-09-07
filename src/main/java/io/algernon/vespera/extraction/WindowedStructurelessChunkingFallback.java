package io.algernon.vespera.extraction;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The active {@link StructurelessChunkingFallback} while the LLM-based one ships disabled
 * (ADR-029): a plain whitespace-word window within the rule's budget over whatever text there is, with no
 * structure to respect because none was reported. Not itself the fallback the design eventually
 * wants — it is what runs in the meantime, so the seam has a real (if unmeasured) behavior rather
 * than silently dropping structureless documents on the floor.
 */
@Component
class WindowedStructurelessChunkingFallback implements StructurelessChunkingFallback {

    @Override
    public List<String> chunk(String text, ChunkingRule rule) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && rule.size(candidate) > rule.maxWords()) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
