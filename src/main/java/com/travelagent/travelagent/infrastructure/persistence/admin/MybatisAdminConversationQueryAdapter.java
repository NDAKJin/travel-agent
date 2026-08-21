package com.travelagent.travelagent.infrastructure.persistence.admin;

import com.travelagent.travelagent.application.admin.model.AdminConversationSessionView;
import com.travelagent.travelagent.application.admin.port.out.AdminConversationQueryPort;
import com.travelagent.travelagent.domain.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.infrastructure.persistence.agent.AgentConversationSessionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisAdminConversationQueryAdapter implements AdminConversationQueryPort {
    private final AgentConversationSessionMapper mapper;
    public List<AdminConversationSessionView> findAllForAdminPage(int offset, int size) { return mapper.findAllForAdminPage(offset, size); }
    public List<AdminConversationSessionView> findByUserIdForAdminPage(long userId, int offset, int size) { return mapper.findByUserIdForAdminPage(userId, offset, size); }
    public long countAllForAdmin() { return mapper.countAllForAdmin(); }
    public long countByUserIdForAdmin(long userId) { return mapper.countByUserIdForAdmin(userId); }
    public AdminConversationSessionView findForAdminById(long id) { return mapper.findForAdminById(id); }
    public List<AgentConversationMessage> findMessagesBySessionId(long sessionId) { return mapper.findMessagesBySessionId(sessionId); }
}
