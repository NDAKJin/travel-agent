package com.travelagent.travelagent.application.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.service.RagTextChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagIngestionServiceTest {

    @Test
    void chunksKeepIncreasingOffsetsWithOverlap() throws Exception {
        List<?> chunks = new RagTextChunker(800, 100, List.of("\n\n", "\n", "。", "！", "？", "；", "，"))
                .split("a".repeat(805), new DocumentMetadata("", "", List.of(), "", List.of()));

        assertThat(chunks).hasSize(2);
        assertThat(read(chunks.get(0), "startOffset")).isEqualTo(0);
        assertThat(read(chunks.get(0), "endOffset")).isEqualTo(800);
        assertThat(read(chunks.get(1), "startOffset")).isEqualTo(700);
        assertThat(read(chunks.get(1), "endOffset")).isEqualTo(805);
    }

    private int read(Object chunk, String accessor) {
        return switch (accessor) {
            case "startOffset" -> ((com.travelagent.travelagent.domain.rag.model.EmbeddingChunk) chunk).startOffset();
            case "endOffset" -> ((com.travelagent.travelagent.domain.rag.model.EmbeddingChunk) chunk).endOffset();
            default -> throw new IllegalArgumentException(accessor);
        };
    }
}
