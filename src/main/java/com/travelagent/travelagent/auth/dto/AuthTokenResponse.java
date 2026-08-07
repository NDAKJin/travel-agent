package com.travelagent.travelagent.auth.dto;

import java.time.Instant;

public record AuthTokenResponse(String accessToken,
                                Instant accessTokenExpiresAt,
                                String refreshToken,
                                Instant refreshTokenExpiresAt) {
}
