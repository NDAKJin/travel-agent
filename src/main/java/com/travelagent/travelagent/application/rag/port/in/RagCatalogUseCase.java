package com.travelagent.travelagent.application.rag.port.in;

import com.travelagent.travelagent.application.rag.admin.RagChunkResponse;
import com.travelagent.travelagent.application.rag.admin.RagDocumentResponse;
import java.util.List;

public interface RagCatalogUseCase {

    List<RagDocumentResponse> documents(String keyword);

    List<RagChunkResponse> chunks(Long documentId, String keyword, Integer enabled);

    void toggleDocument(long documentId, boolean enabled);

    void toggleChunk(long chunkId, boolean enabled);

    void batchToggleChunks(List<Long> chunkIds, boolean enabled);
}
