package com.travelagent.travelagent.application.rag.admin;

import com.travelagent.travelagent.application.rag.port.in.RagCatalogUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagCatalogUseCase ragAdminService;

    @GetMapping("/documents")
    public List<RagDocumentResponse> documents(@RequestParam(required = false) String keyword) {
        return ragAdminService.documents(keyword);
    }

    @GetMapping("/chunks")
    public List<RagChunkResponse> chunks(@RequestParam(required = false) Long documentId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer enabled) {
        return ragAdminService.chunks(documentId, keyword, enabled);
    }

    @PatchMapping("/documents/{documentId}/enable")
    public ResponseEntity<Void> toggleDocument(@PathVariable long documentId, @RequestParam boolean enabled) {
        ragAdminService.toggleDocument(documentId, enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chunks/{chunkId}/enable")
    public ResponseEntity<Void> toggleChunk(@PathVariable long chunkId, @RequestParam boolean enabled) {
        ragAdminService.toggleChunk(chunkId, enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chunks/batch-enable")
    public ResponseEntity<Void> batchToggleChunks(@RequestParam boolean enabled, @RequestBody List<Long> chunkIds) {
        ragAdminService.batchToggleChunks(chunkIds, enabled);
        return ResponseEntity.noContent().build();
    }
}
