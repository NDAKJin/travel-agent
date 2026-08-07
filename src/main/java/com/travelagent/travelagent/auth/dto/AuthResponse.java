package com.travelagent.travelagent.auth.dto;

public record AuthResponse(AuthUserResponse user,
                           AuthTokenResponse token) {
}
