package com.travelagent.travelagent.admin.dto;

import java.time.Instant;

public record AdminConversationSummaryResponse(
        long id,
        String sessionId,
        String title,
        String preview,
        int messageCount,
        Instant updatedAt,
        AdminConversationUserResponse user) {
}
