package com.travelagent.travelagent.auth.dto;

public record AuthUserResponse(Long id,
                               String userType,
                               String subject,
                               String displayName) {
}
