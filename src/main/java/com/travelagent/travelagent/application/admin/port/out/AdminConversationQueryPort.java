package com.travelagent.travelagent.application.admin.port.out;

import com.travelagent.travelagent.application.admin.model.AdminConversationSessionView;
import com.travelagent.travelagent.domain.agent.model.AgentConversationMessage;
import java.util.List;

public interface AdminConversationQueryPort {
    List<AdminConversationSessionView> findAllForAdminPage(int offset, int size);
    List<AdminConversationSessionView> findByUserIdForAdminPage(long userId, int offset, int size);
    long countAllForAdmin();
    long countByUserIdForAdmin(long userId);
    AdminConversationSessionView findForAdminById(long id);
    List<AgentConversationMessage> findMessagesBySessionId(long sessionId);
}
