package com.travelagent.travelagent.auth.service;

import java.time.Duration;

public interface RefreshTokenStore {

    void storeToken(long userId, String tokenId, Duration ttl);

    boolean isTokenValid(long userId, String tokenId);

    /** Atomically validates and consumes a refresh token. */
    boolean consumeToken(long userId, String tokenId);

    void revokeToken(long userId, String tokenId);

    void revokeAll(long userId);
}
