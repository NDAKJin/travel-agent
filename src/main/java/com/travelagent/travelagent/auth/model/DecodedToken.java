package com.travelagent.travelagent.auth.model;

import java.time.Instant;
import java.util.Map;

public record DecodedToken(Map<String, Object> claims,
                           Instant issuedAt,
                           Instant expiresAt) {

    public String stringClaim(String name) {
        Object value = claims.get(name);
        if (value == null && "token_type".equals(name)) {
            value = claims.get("tokenType");
        }
        return value == null ? null : value.toString();
    }

    public long longClaim(String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
