package com.travelagent.travelagent.application.rag.admin;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.rag.service.RagAdminService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagAdminApplicationTest {
    @Test void delegatesRagOperations() {
        RagAdminService service = mock(RagAdminService.class);
        RagAdminApplication app = new RagAdminApplication(service);
        app.documents("k"); app.chunks(1L, "k", 1); app.toggleDocument(1L, true); app.toggleChunk(2L, false);
        app.updateChunkContent(2L, "content"); app.batchToggleChunks(List.of(1L, 2L), true);
        verify(service).documents("k"); verify(service).chunks(1L, "k", 1); verify(service).toggleDocument(1L, true);
        verify(service).toggleChunk(2L, false); verify(service).updateChunkContent(2L, "content"); verify(service).batchToggleChunks(List.of(1L, 2L), true);
    }
}
