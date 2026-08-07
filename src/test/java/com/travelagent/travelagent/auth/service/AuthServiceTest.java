package com.travelagent.travelagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.travelagent.travelagent.config.AuthProperties;
import com.travelagent.travelagent.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.auth.dto.AuthResponse;
import com.travelagent.travelagent.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.auth.mapper.AdminUserMapper;
import com.travelagent.travelagent.auth.mapper.WxUserMapper;
import com.travelagent.travelagent.auth.model.AdminUser;
import com.travelagent.travelagent.auth.model.TokenPair;
import com.travelagent.travelagent.auth.model.WxUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Mock
    private WxUserMapper wxUserMapper;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private WxMiniProgramIdentityResolver wxMiniProgramIdentityResolver;

    private AuthService authService;
    private JwtTokenService jwtTokenService;
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-hs256-signing-123456");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(1));
        jwtTokenService = new JwtTokenService();
        SecretKeySpec secretKey = new SecretKeySpec(properties.getJwtSecret().getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        ReflectionTestUtils.setField(jwtTokenService, "properties", properties);
        ReflectionTestUtils.setField(jwtTokenService, "clock", clock);
        ReflectionTestUtils.setField(jwtTokenService, "jwtEncoder", jwtEncoder);
        ReflectionTestUtils.setField(jwtTokenService, "jwtDecoder", jwtDecoder);
        passwordHasher = new BCryptPasswordHasher();
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "wxUserMapper", wxUserMapper);
        ReflectionTestUtils.setField(authService, "adminUserMapper", adminUserMapper);
        ReflectionTestUtils.setField(authService, "jwtTokenService", jwtTokenService);
        ReflectionTestUtils.setField(authService, "refreshTokenStore", refreshTokenStore);
        ReflectionTestUtils.setField(authService, "passwordHasher", passwordHasher);
        ReflectionTestUtils.setField(authService, "wxMiniProgramIdentityResolver", wxMiniProgramIdentityResolver);
        ReflectionTestUtils.setField(authService, "clock", clock);
    }

    @Test
    void loginWxCreatesUserAndStoresRefreshToken() {
        when(wxMiniProgramIdentityResolver.resolve("test-code")).thenReturn(new WxSessionIdentity("wx-open-id-demo", null));
        when(wxUserMapper.findByOpenId("wx-open-id-demo")).thenReturn(null);
        when(wxUserMapper.insert(any())).thenAnswer(invocation -> {
            WxUser user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        AuthResponse response = authService.loginWx(new WxLoginRequest("test-code", null, null));

        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.token().accessToken()).isNotBlank();
        ArgumentCaptor<WxUser> userCaptor = ArgumentCaptor.forClass(WxUser.class);
        verify(wxUserMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(userCaptor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
        verify(refreshTokenStore).storeToken(anyLong(), any(), any());
    }

    @Test
    void refreshRevokesOldTokenAndStoresNewToken() {
        WxUser user = new WxUser();
        user.setId(10L);
        user.setOpenId("wx-open-id-demo");
        user.setNickname("wx-user");
        when(wxUserMapper.findById(10L)).thenReturn(user);

        TokenPair firstTokenPair = jwtTokenService.issueTokenPair(10L, "wx", "wx-open-id-demo", "wx-user");
        when(refreshTokenStore.isTokenValid(10L, firstTokenPair.refreshTokenId())).thenReturn(true);

        AuthResponse refreshed = authService.refresh(new RefreshTokenRequest(firstTokenPair.refreshToken()));

        assertThat(refreshed.token().refreshToken()).isNotEqualTo(firstTokenPair.refreshToken());
        ArgumentCaptor<String> tokenIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(refreshTokenStore).revokeToken(10L, firstTokenPair.refreshTokenId());
        verify(refreshTokenStore).storeToken(eq(10L), tokenIdCaptor.capture(), ttlCaptor.capture());
        assertThat(tokenIdCaptor.getValue()).isNotBlank();
        assertThat(ttlCaptor.getValue()).isPositive();
    }

    @Test
    void refreshRejectsInactiveTokenWhenWhitelistValidationFails() {
        TokenPair firstTokenPair = jwtTokenService.issueTokenPair(10L, "wx", "wx-open-id-demo", "wx-user");
        when(refreshTokenStore.isTokenValid(10L, firstTokenPair.refreshTokenId())).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(firstTokenPair.refreshToken())))
                .isInstanceOf(AuthException.class)
                .hasMessage("Refresh token is not active");
    }

    @Test
    void adminLoginAcceptsSeededPassword() {
        AdminUser adminUser = new AdminUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setDisplayName("ops-admin");
        adminUser.setPasswordHash(passwordHasher.hash("admin123"));
        adminUser.setEnabled(true);
        when(adminUserMapper.findByUsername("admin")).thenReturn(adminUser);

        AuthResponse response = authService.loginAdmin(new AdminLoginRequest("admin", "admin123"));

        assertThat(response.user().userType()).isEqualTo("admin");
        assertThat(response.token().refreshToken()).isNotBlank();
    }

    @Test
    void adminLoginRejectsInvalidPassword() {
        AdminUser adminUser = new AdminUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setDisplayName("ops-admin");
        adminUser.setPasswordHash(passwordHasher.hash("admin123"));
        adminUser.setEnabled(true);
        when(adminUserMapper.findByUsername("admin")).thenReturn(adminUser);

        assertThatThrownBy(() -> authService.loginAdmin(new AdminLoginRequest("admin", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid admin credentials");
    }
}
