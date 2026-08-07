package com.travelagent.travelagent.auth.service;

import com.travelagent.travelagent.config.AuthProperties;
import com.travelagent.travelagent.auth.model.DecodedToken;
import com.travelagent.travelagent.auth.model.TokenPair;
import com.travelagent.travelagent.auth.exception.AuthException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@Slf4j
public class JwtTokenService {
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USER_TYPE = "userType";
    private static final String CLAIM_DISPLAY_NAME = "displayName";
    private static final String CLAIM_TOKEN_TYPE = "token_type";

    @Autowired
    private AuthProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    public TokenPair issueTokenPair(long userId, String userType, String subject, String displayName) {
        Instant issuedAt = clock.instant();
        String refreshTokenId = UUID.randomUUID().toString();
        String accessToken = encode(buildClaims(userId, userType, subject, displayName, "access", UUID.randomUUID().toString(), issuedAt));
        String refreshToken = encode(buildClaims(userId, userType, subject, displayName, "refresh", refreshTokenId, issuedAt));
        log.debug("Issued JWT token pair: userId={}, userType={}, issuedAt={}", userId, userType, issuedAt);
        return new TokenPair(
                accessToken,
                issuedAt.plus(properties.getAccessTokenTtl()),
                refreshToken,
                issuedAt.plus(properties.getRefreshTokenTtl()),
                refreshTokenId);
    }

    public DecodedToken decodeAndVerify(String token) {
        Assert.hasText(token, "token must not be blank");
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Map<String, Object> claims = new LinkedHashMap<>(jwt.getClaims());
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            Instant now = clock.instant();
            if (issuedAt == null || expiresAt == null || now.isBefore(issuedAt) || !now.isBefore(expiresAt)) {
                log.warn("Token verification failed: token expired or not yet valid, subject={}, tokenType={}",
                        claims.get("sub"),
                        claimValue(claims, CLAIM_TOKEN_TYPE, "tokenType"));
                throw new AuthException("Token has expired");
            }
            log.debug("Token verified: subject={}, tokenType={}", claims.get("sub"), claimValue(claims, CLAIM_TOKEN_TYPE, "tokenType"));
            return new DecodedToken(claims, issuedAt, expiresAt);
        } catch (JwtException ex) {
            log.warn("Token verification failed: token expired or not yet valid, subject={}, tokenType={}",
                    null,
                    null);
            throw new AuthException("Invalid token");
        }
    }

    private JwtClaimsSet buildClaims(long userId,
                                     String userType,
                                     String subject,
                                     String displayName,
                                     String tokenType,
                                     String tokenId,
                                     Instant issuedAt) {
        Instant expiresAt = issuedAt.plus("access".equals(tokenType) ? properties.getAccessTokenTtl() : properties.getRefreshTokenTtl());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USER_TYPE, userType)
                .claim(CLAIM_DISPLAY_NAME, displayName)
                .claim(CLAIM_TOKEN_TYPE, tokenType);
        if (tokenId != null) {
            builder.id(tokenId);
        }
        return builder.build();
    }

    private String encode(JwtClaimsSet claims) {
        try {
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                    .type("JWT")
                    .build();
            return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        } catch (JwtException ex) {
            throw new IllegalStateException("Unable to encode token", ex);
        }
    }

    private Object claimValue(Map<String, Object> claims, String primaryKey, String legacyKey) {
        return claims.containsKey(primaryKey) ? claims.get(primaryKey) : claims.get(legacyKey);
    }
}
