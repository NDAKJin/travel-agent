package com.travelagent.travelagent.domain.admin.service;

import com.travelagent.travelagent.infrastructure.persistence.admin.MybatisAdminObservationQueryAdapter;
import com.travelagent.travelagent.domain.agent.model.AgentObservationLog;
import com.travelagent.travelagent.infrastructure.persistence.admin.MybatisAdminConversationQueryAdapter;
import com.travelagent.travelagent.domain.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.domain.admin.dto.AdminAgentObservationResponse;
import com.travelagent.travelagent.domain.admin.dto.AdminConversationDetailResponse;
import com.travelagent.travelagent.domain.admin.dto.AdminConversationMessageResponse;
import com.travelagent.travelagent.domain.admin.model.AdminConversationSessionView;
import com.travelagent.travelagent.domain.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.domain.admin.dto.AdminConversationUserResponse;
import com.travelagent.travelagent.domain.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.domain.common.dto.PageResponse;
import com.travelagent.travelagent.infrastructure.persistence.auth.WxUserMapper;
import com.travelagent.travelagent.domain.auth.model.WxUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final WxUserMapper wxUserMapper;
    private final MybatisAdminConversationQueryAdapter conversationSessionMapper;
    private final MybatisAdminObservationQueryAdapter observationLogMapper;

    public PageResponse<AdminWxUserResponse> searchWxUsers(String keyword, int page, int size) {
        String normalized = normalizeKeyword(keyword);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        List<AdminWxUserResponse> content = wxUserMapper.searchByKeywordPage(normalized, offset, safeSize).stream()
                .map(this::toWxUserResponse)
                .toList();
        long total = wxUserMapper.countByKeyword(normalized);
        return new PageResponse<>(content, total, safePage, safeSize);
    }

    public PageResponse<AdminConversationSummaryResponse> listSessions(Long wxUserId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        List<AdminConversationSummaryResponse> content;
        long total;
        if (wxUserId == null) {
            content = conversationSessionMapper.findAllForAdminPage(offset, safeSize).stream()
                    .map(this::toConversationSummary)
                    .toList();
            total = conversationSessionMapper.countAllForAdmin();
        } else {
            WxUser user = requireWxUser(wxUserId);
            content = conversationSessionMapper.findByUserIdForAdminPage(user.getId(), offset, safeSize).stream()
                    .map(session -> toConversationSummary(session, user))
                    .toList();
            total = conversationSessionMapper.countByUserIdForAdmin(user.getId());
        }
        return new PageResponse<>(content, total, safePage, safeSize);
    }

    public AdminConversationDetailResponse getSessionDetail(long conversationId) {
        var session = conversationSessionMapper.findForAdminById(conversationId);
        if (session == null) {
            throw new IllegalArgumentException("Conversation session not found");
        }
        return toDetail(session.getSessionId(), conversationSessionMapper.findMessagesBySessionId(session.getId()),
                observationLogMapper.findBySessionId(session.getId()),
                session.getCreatedAt(), session.getUpdatedAt());
    }

    private AdminWxUserResponse toWxUserResponse(WxUser user) {
        return new AdminWxUserResponse(user.getId(), user.getOpenId(), user.getNickname(), user.isEnabled(), user.getUpdatedAt());
    }

    private AdminConversationSummaryResponse toConversationSummary(AdminConversationSessionView session) {
        return toConversationSummary(session, null);
    }

    private AdminConversationSummaryResponse toConversationSummary(AdminConversationSessionView session, WxUser user) {
        AdminConversationUserResponse conversationUser = user != null
                ? new AdminConversationUserResponse(user.getId(), "wx", user.getOpenId(), user.getNickname())
                : new AdminConversationUserResponse(
                        session.getUserId(),
                        session.getUserType(),
                        session.getUserSubject(),
                        session.getUserDisplayName());
        return new AdminConversationSummaryResponse(
                session.getId(),
                session.getSessionId(),
                session.getTitle(),
                session.getPreview(),
                session.getMessageCount(),
                session.getUpdatedAt(),
                conversationUser);
    }

    private WxUser requireWxUser(long wxUserId) {
        WxUser user = wxUserMapper.findById(wxUserId);
        if (user == null) {
            throw new IllegalArgumentException("Wx user not found");
        }
        return user;
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : "";
    }

    private AdminConversationDetailResponse toDetail(String sessionId, List<AgentConversationMessage> messages,
                                                      List<AgentObservationLog> observations,
                                                      Instant createdAt, Instant updatedAt) {
        Map<Long, List<AdminAgentObservationResponse>> byMessageId = observations.stream()
                .collect(Collectors.groupingBy(AgentObservationLog::getMessageId,
                        Collectors.mapping(this::toObservation, Collectors.toList())));
        return new AdminConversationDetailResponse(
                sessionId,
                buildTitle(messages),
                messages.stream()
                        .map(message -> new AdminConversationMessageResponse(message.getId(), message.getRole(), message.getContent(),
                                byMessageId.getOrDefault(message.getId(), List.of())))
                        .toList(),
                createdAt,
                updatedAt);
    }

    private AdminAgentObservationResponse toObservation(AgentObservationLog log) {
        return new AdminAgentObservationResponse(log.getAgentName(), log.getLlmInput(), log.getLlmOutput(),
                log.getPromptTokens(), log.getCompletionTokens(), log.getTotalTokens(), log.getNextDecision(),
                log.getCreatedAt());
    }

    private String buildTitle(List<AgentConversationMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.getRole()))
                .map(AgentConversationMessage::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(text -> text.length() <= 60 ? text : text.substring(0, 57) + "...")
                .orElse("new-chat");
    }
}
