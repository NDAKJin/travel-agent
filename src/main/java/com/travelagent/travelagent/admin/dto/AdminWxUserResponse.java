package com.travelagent.travelagent.admin.dto;

import java.time.Instant;

public record AdminWxUserResponse(
        long id,
        String openId,
        String nickname,
        boolean enabled,
        Instant updatedAt) {
}
