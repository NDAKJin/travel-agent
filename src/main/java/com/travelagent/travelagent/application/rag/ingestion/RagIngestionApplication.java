package com.travelagent.travelagent.application.rag.ingestion;

import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskResponse;
import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagIngestionApplication {
    private final RagIngestionTaskService service;

    public List<RagIngestionTaskResponse> submit(List<org.springframework.web.multipart.MultipartFile> files) {
        return service.submit(files);
    }
    public List<RagIngestionTaskResponse> list() { return service.list(); }
    public void cancel(long taskId) { service.cancel(taskId); }
    public void retry(long taskId) { service.retry(taskId); }
}
