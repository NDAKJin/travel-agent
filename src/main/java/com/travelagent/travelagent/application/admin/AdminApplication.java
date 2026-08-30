package com.travelagent.travelagent.application.admin;

import com.travelagent.travelagent.domain.admin.dto.*;
import com.travelagent.travelagent.domain.admin.service.AdminManagementService;
import com.travelagent.travelagent.domain.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminApplication {
    private final AdminManagementService service;

    public PageResponse<AdminWxUserResponse> searchWxUsers(String k, int p, int s) {
        return service.searchWxUsers(k, p, s);
    }

    public PageResponse<AdminConversationSummaryResponse> listSessions(Long id, int p, int s) {
        return service.listSessions(id, p, s);
    }

    public AdminConversationDetailResponse getSessionDetail(long id) {
        return service.getSessionDetail(id);
    }
}
