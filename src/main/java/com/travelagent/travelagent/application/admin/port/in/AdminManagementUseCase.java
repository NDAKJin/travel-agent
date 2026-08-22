package com.travelagent.travelagent.application.admin.port.in;

import com.travelagent.travelagent.application.admin.dto.AdminConversationDetailResponse;
import com.travelagent.travelagent.application.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.application.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.application.common.dto.PageResponse;

public interface AdminManagementUseCase {

    PageResponse<AdminWxUserResponse> searchWxUsers(String keyword, int page, int size);

    PageResponse<AdminConversationSummaryResponse> listSessions(Long wxUserId, int page, int size);

    AdminConversationDetailResponse getSessionDetail(long conversationId);
}
