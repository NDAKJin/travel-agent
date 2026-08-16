package com.travelagent.travelagent.agent.mapper;

import com.travelagent.travelagent.agent.model.AgentConversationSession;
import com.travelagent.travelagent.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.admin.model.AdminConversationSessionView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentConversationSessionMapper {

    AgentConversationSession findByUserIdAndSessionId(long userId, String sessionId);

    AgentConversationSession findByUserIdAndSessionIdForUpdate(@Param("userId") long userId,
                                                               @Param("sessionId") String sessionId);

    List<AgentConversationSession> findAllByUserIdOrderByUpdatedAtDesc(long userId);

    List<AdminConversationSessionView> findAllForAdminPage(@Param("offset") int offset,
                                                           @Param("size") int size);

    List<AdminConversationSessionView> findByUserIdForAdminPage(@Param("userId") long userId,
                                                                @Param("offset") int offset,
                                                                @Param("size") int size);

    long countAllForAdmin();

    long countByUserIdForAdmin(long userId);

    AdminConversationSessionView findForAdminById(long id);

    List<AgentConversationMessage> findMessagesBySessionId(long sessionId);

    int deleteMessagesBySessionId(long sessionId);

    int insertMessages(@Param("messages") List<AgentConversationMessage> messages);

    int nextMessageSequenceNo(long sessionId);

    int insertMessage(AgentConversationMessage message);

    int insert(AgentConversationSession session);

    int update(AgentConversationSession session);

    int deleteByUserIdAndSessionId(@Param("userId") long userId, @Param("sessionId") String sessionId);
}
