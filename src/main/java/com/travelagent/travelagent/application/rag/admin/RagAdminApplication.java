package com.travelagent.travelagent.application.rag.admin;

import com.travelagent.travelagent.domain.rag.dto.RagChunkResponse;
import com.travelagent.travelagent.domain.rag.dto.RagDocumentResponse;
import com.travelagent.travelagent.domain.rag.service.RagAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagAdminApplication {
    private final RagAdminService service;

    public List<RagDocumentResponse> documents(String keyword) { return service.documents(keyword); }
    public List<RagChunkResponse> chunks(Long documentId, String keyword, Integer enabled) {
        return service.chunks(documentId, keyword, enabled);
    }
    public void toggleDocument(long documentId, boolean enabled) { service.toggleDocument(documentId, enabled); }
    public void toggleChunk(long chunkId, boolean enabled) { service.toggleChunk(chunkId, enabled); }
    public void updateChunkContent(long chunkId, String content) { service.updateChunkContent(chunkId, content); }
    public void batchToggleChunks(List<Long> chunkIds, boolean enabled) { service.batchToggleChunks(chunkIds, enabled); }
}
