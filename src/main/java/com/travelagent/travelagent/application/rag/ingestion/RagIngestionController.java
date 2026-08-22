package com.travelagent.travelagent.application.rag.ingestion;

import com.travelagent.travelagent.application.rag.port.in.RagIngestionUseCase;
import java.util.List;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/rag/documents")
@RequiredArgsConstructor
public class RagIngestionController {

    private final RagIngestionUseCase taskService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<RagIngestionTaskResponse> importDocuments(@RequestParam("files") MultipartFile[] files) {
        return taskService.submit(Arrays.asList(files));
    }

    @GetMapping("/import/tasks")
    public List<RagIngestionTaskResponse> importTasks() {
        return taskService.list();
    }
}
