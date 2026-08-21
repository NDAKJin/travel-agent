package com.travelagent.travelagent.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

class RagIngestionServiceTest {

    @Test
    void chunksKeepIncreasingOffsetsWithOverlap() throws Exception {
        RagIngestionService service = new RagIngestionService(
                mock(VectorStore.class), mock(ChatClient.class), mock(PromptResourceLoader.class));
        Method split = RagIngestionService.class.getDeclaredMethod("split", String.class, Class.forName(
                "com.travelagent.travelagent.rag.ingestion.RagIngestionService$DocumentMetadata"));
        split.setAccessible(true);

        List<?> chunks = (List<?>) split.invoke(service, "a".repeat(805), null);

        assertThat(chunks).hasSize(2);
        assertThat(read(chunks.get(0), "startOffset")).isEqualTo(0);
        assertThat(read(chunks.get(0), "endOffset")).isEqualTo(800);
        assertThat(read(chunks.get(1), "startOffset")).isEqualTo(700);
        assertThat(read(chunks.get(1), "endOffset")).isEqualTo(805);
    }

    private int read(Object chunk, String accessor) throws Exception {
        Method method = chunk.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (int) method.invoke(chunk);
    }
}
