package com.travelagent.travelagent.application.admin;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.admin.service.AdminManagementService;
import org.junit.jupiter.api.Test;

class AdminApplicationTest {
    @Test void delegatesAdminOperations() {
        AdminManagementService service = mock(AdminManagementService.class);
        AdminApplication app = new AdminApplication(service);
        app.searchWxUsers("k", 1, 10); app.listSessions(1L, 1, 10); app.getSessionDetail(2L);
        verify(service).searchWxUsers("k", 1, 10); verify(service).listSessions(1L, 1, 10); verify(service).getSessionDetail(2L);
    }
}
