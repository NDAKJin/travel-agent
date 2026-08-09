package com.travelagent.travelagent.admin.service;

import com.travelagent.travelagent.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.mapper.AgentConversationSessionMapper;
import com.travelagent.travelagent.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.admin.model.AdminConversationSessionView;
import com.travelagent.travelagent.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.admin.dto.AdminConversationUserResponse;
import com.travelagent.travelagent.admin.dto.AdminScenicDocumentCreateRequest;
import com.travelagent.travelagent.admin.dto.AdminScenicDocumentResponse;
import com.travelagent.travelagent.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.common.dto.PageResponse;
import com.travelagent.travelagent.auth.mapper.WxUserMapper;
import com.travelagent.travelagent.auth.model.WxUser;
import com.travelagent.travelagent.rag.service.ScenicKnowledgeIngestionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[^a-z0-9._-]+");

    private final WxUserMapper wxUserMapper;
    private final AgentConversationSessionMapper conversationSessionMapper;
    private final ScenicKnowledgeIngestionService scenicKnowledgeIngestionService;

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

    public AgentSessionDetailResponse getSessionDetail(long conversationId) {
        var session = conversationSessionMapper.findForAdminById(conversationId);
        if (session == null) {
            throw new IllegalArgumentException("Conversation session not found");
        }
        return toDetail(session.getSessionId(), conversationSessionMapper.findMessagesBySessionId(session.getId()),
                session.getCreatedAt(), session.getUpdatedAt());
    }

    public AdminScenicDocumentResponse addScenicDocument(AdminScenicDocumentCreateRequest request) {
        String fileName = buildFileName(request.title());
        Path directory = scenicKnowledgeIngestionService.knowledgeDirectory();
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid scenic document file name");
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(target, buildMarkdown(request.title(), request.content()), StandardCharsets.UTF_8);
            scenicKnowledgeIngestionService.ingestDocument(target);
            return new AdminScenicDocumentResponse(fileName, target.toString(), Instant.now());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to save scenic document", exception);
        }
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

    private String buildFileName(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}", "");
        String slug = SAFE_FILE_NAME.matcher(normalized).replaceAll("-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (!StringUtils.hasText(slug)) {
            slug = "scenic-document";
        }
        return slug + "-" + Instant.now().toEpochMilli() + ".md";
    }

    private String buildMarkdown(String title, String content) {
        return "# " + title.trim() + "\n\n" + content.trim() + "\n";
    }

    private AgentSessionDetailResponse toDetail(String sessionId, List<AgentConversationMessage> messages,
                                                Instant createdAt, Instant updatedAt) {
        return new AgentSessionDetailResponse(
                sessionId,
                buildTitle(messages),
                messages.stream()
                        .map(message -> new AgentConversationMessageResponse(message.getRole(), message.getContent()))
                        .toList(),
                createdAt,
                updatedAt);
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
