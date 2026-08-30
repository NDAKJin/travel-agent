package com.travelagent.travelagent.application.rag.ingestion;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class RagIngestionApplicationTest {
    @Test void delegatesIngestionOperations() {
        RagIngestionTaskService service = mock(RagIngestionTaskService.class);
        RagIngestionApplication app = new RagIngestionApplication(service);
        List<MultipartFile> files = List.of(mock(MultipartFile.class));
        app.submit(files); app.list(); app.cancel(3L);
        verify(service).submit(files); verify(service).list(); verify(service).cancel(3L);
    }
}
