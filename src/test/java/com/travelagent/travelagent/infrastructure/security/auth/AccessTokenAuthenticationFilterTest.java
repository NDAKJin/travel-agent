package com.travelagent.travelagent.infrastructure.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.travelagent.travelagent.application.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenAuthenticationFilterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    private AccessTokenAuthenticationFilter filter;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setIssuer("travel-agent");
        properties.setJwtSecret("test-secret-key-for-hs256-signing-123456");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(1));

        SecretKeySpec secretKey = new SecretKeySpec(properties.getJwtSecret().getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());

        jwtTokenService = new JwtTokenService(properties, FIXED_CLOCK, jwtEncoder, jwtDecoder);
        filter = new AccessTokenAuthenticationFilter(jwtTokenService);
    }

    @Test
    void rejectsRequestWithoutAccessToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing access token");
        verify(filterChain, times(0)).doFilter(request, response);
    }

    @Test
    void rejectsRefreshTokenOnProtectedEndpoint() throws Exception {
        String refreshToken = jwtTokenService.issueTokenPair(1L, "admin", "admin", "ops-admin").refreshToken();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token type must be access");
        verify(filterChain, times(0)).doFilter(request, response);
    }

    @Test
    void acceptsValidAccessToken() throws Exception {
        String accessToken = jwtTokenService.issueTokenPair(1L, "admin", "admin", "ops-admin").accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void onlyFiltersProtectedApiPaths() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/doc.html"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/nextdoc/jse/index.js"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", ""))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/init"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/stream/travel-agent"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/agent/chat"))).isFalse();
    }
}
