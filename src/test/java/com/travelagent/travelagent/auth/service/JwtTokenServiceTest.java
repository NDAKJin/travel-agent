package com.travelagent.travelagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelagent.travelagent.config.AuthProperties;
import com.travelagent.travelagent.auth.model.DecodedToken;
import com.travelagent.travelagent.auth.model.TokenPair;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-hs256-signing-123456");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(1));
        SecretKeySpec secretKey = new SecretKeySpec(properties.getJwtSecret().getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        jwtTokenService = new JwtTokenService(
                properties,
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC),
                jwtEncoder,
                jwtDecoder);
    }

    @Test
    void issueTokenPairCreatesValidJwtPair() {
        TokenPair tokenPair = jwtTokenService.issueTokenPair(123L, "wx", "wx-open-id-demo", "wx-user");

        assertThat(tokenPair.accessToken()).isNotBlank();
        assertThat(tokenPair.refreshToken()).isNotBlank();
        assertThat(tokenPair.refreshTokenId()).isNotBlank();

        DecodedToken accessToken = jwtTokenService.decodeAndVerify(tokenPair.accessToken());
        assertThat(accessToken.stringClaim("token_type")).isEqualTo("access");
        assertThat(accessToken.longClaim("uid")).isEqualTo(123L);

        DecodedToken refreshToken = jwtTokenService.decodeAndVerify(tokenPair.refreshToken());
        assertThat(refreshToken.stringClaim("token_type")).isEqualTo("refresh");
        assertThat(refreshToken.stringClaim("jti")).isEqualTo(tokenPair.refreshTokenId());
    }
}
