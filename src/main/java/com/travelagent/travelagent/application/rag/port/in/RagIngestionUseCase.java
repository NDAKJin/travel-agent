package com.travelagent.travelagent.application.rag.port.in;

import com.travelagent.travelagent.application.rag.ingestion.RagIngestionTaskResponse;
import com.travelagent.travelagent.application.rag.model.RagIngestionMessage;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface RagIngestionUseCase {

    List<RagIngestionTaskResponse> submit(List<MultipartFile> files);

    List<RagIngestionTaskResponse> list();

    void cancel(long taskId);

    void process(RagIngestionMessage message);
}
